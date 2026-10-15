package nl.lamia.kingdoms.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import nl.lamia.kingdoms.core.map.Feature
import nl.lamia.kingdoms.core.map.GameMap
import nl.lamia.kingdoms.core.map.Point
import nl.lamia.kingdoms.core.map.Polygons
import nl.lamia.kingdoms.core.map.Terrain
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Board space (Design §8): the ground plane seen at a slight angle. `x` is the map's, `y` is the
 * map's times [TILT]; heights are drawn straight up in board units, so props stay upright.
 */
object Board {
    const val TILT = 0.8f

    /** Thickness of the land slab: the earth wall along the coast. */
    const val WALL = 14f

    fun of(p: Point) = Offset(p.x.toFloat(), p.y.toFloat() * TILT)

    fun toWorld(board: Offset) = Point(board.x.toDouble(), (board.y / TILT).toDouble())
}

/** Something drawn standing on the board at ([x], [y]), painted back to front by [y]. */
sealed interface Prop {
    val x: Float
    val y: Float

    data class Tree(override val x: Float, override val y: Float, val r: Float, val shade: Int) : Prop
    data class Hill(override val x: Float, override val y: Float, val w: Float, val h: Float) : Prop
    data class Mountain(override val x: Float, override val y: Float, val w: Float, val h: Float, val lean: Float) : Prop
    data class Reeds(override val x: Float, override val y: Float, val s: Float) : Prop
    data class Tuft(override val x: Float, override val y: Float, val s: Float) : Prop
}

/** Flat things on the ground: drawn before every [Prop]. */
sealed interface Decal {
    data class Pool(val centre: Offset, val rx: Float, val ry: Float, val degrees: Float) : Decal
    data class Field(val centre: Offset, val w: Float, val h: Float, val degrees: Float) : Decal
}

/** Everything about a map that is drawn and never changes during a game, in board space. */
class BoardGeometry(val map: GameMap) {
    val width = map.width.toFloat()
    val height = map.height.toFloat() * Board.TILT + Board.WALL

    val outlines: List<List<Offset>> = map.provinces.map { p -> p.outline.map(Board::of) }
    val provinces: List<Path> = outlines.map { it.toPath(close = true) }
    val borders: List<Path> = map.borders.map { it.path.map(Board::of).toPath(close = false) }
    val shores: List<Path> = map.shores.map { it.path.map(Board::of).toPath(close = false) }
    val sites: List<Offset> = map.provinces.map { Board.of(it.site) }

    /** The coast's earth wall: each shore segment swept down by [Board.WALL]; [from] to [to] as fractions. */
    fun wall(from: Float, to: Float) = Path().apply {
        for (shore in map.shores) {
            shore.path.map(Board::of).zipWithNext { a, b ->
                moveTo(a.x, a.y + Board.WALL * from)
                lineTo(b.x, b.y + Board.WALL * from)
                lineTo(b.x, b.y + Board.WALL * to)
                lineTo(a.x, a.y + Board.WALL * to)
                close()
            }
        }
    }

    val wallTop = wall(0f, 0.55f)
    val wallBottom = wall(0.55f, 1f)

    val rivers: List<Path> = map.borders.filter { it.feature == Feature.River }.map { it.path.map(Board::of).toPath(close = false) }

    val decals: List<Decal>
    val props: List<Prop>

    init {
        val decals = mutableListOf<Decal>()
        val props = mutableListOf<Prop>()
        for (p in map.provinces) scatter(p.id, p.terrain, Random(map.config.seed * 31 + p.id), props, decals)
        for ((n, border) in map.borders.withIndex()) {
            val path = border.path.map(Board::of)
            val random = Random(map.config.seed * 97 + n)
            when (border.feature) {
                Feature.MountainRange -> along(path, 15f) { o, _ ->
                    props += Prop.Mountain(o.x, o.y, random.nextFloat(20f, 27f), random.nextFloat(22f, 31f), random.nextFloat(-3f, 3f))
                }
                Feature.Ridge -> along(path, 13f) { o, _ ->
                    props += Prop.Hill(o.x, o.y, random.nextFloat(17f, 22f), random.nextFloat(8f, 11f))
                }
                Feature.Marsh -> along(path, 8f) { o, i ->
                    if (i % 4 == 2) decals += Decal.Pool(o, random.nextFloat(6f, 9f), random.nextFloat(3.5f, 5f), random.nextFloat(-20f, 20f))
                    else props += Prop.Reeds(o.x + random.nextFloat(-3f, 3f), o.y + random.nextFloat(-2f, 2f), random.nextFloat(5f, 7f))
                }
                Feature.Lake -> lake(path)?.let { decals += it }
                Feature.River, null -> Unit
            }
        }
        this.decals = decals
        this.props = props.sortedBy { it.y }
    }

    /** Terrain props scattered inside province [id], clear of its edges and of its banner at the site. */
    private fun scatter(id: Int, terrain: Terrain, random: Random, props: MutableList<Prop>, decals: MutableList<Decal>) {
        val ring = outlines[id]
        val site = sites[id]
        val area = Polygons.area(map.provinces[id].outline).toFloat() * Board.TILT
        fun spot(margin: Float): Offset? {
            val b = bounds(ring)
            repeat(40) {
                val o = Offset(random.nextFloat(b.first.x, b.second.x), random.nextFloat(b.first.y, b.second.y))
                val dy = o.y - site.y
                val clearOfBanner = kotlin.math.abs(o.x - site.x) > 20f || dy < -40f || dy > 16f
                if (clearOfBanner && contains(ring, o) && distanceToRing(ring, o) > margin) return o
            }
            return null
        }
        fun count(perArea: Float, lo: Int, hi: Int) = (area / perArea).roundToInt().coerceIn(lo, hi)
        when (terrain) {
            Terrain.Forest -> {
                val clusters = List(3) { spot(14f) }.filterNotNull()
                repeat(count(230f, 8, 40)) {
                    val c = clusters.randomOrNull(random) ?: return@repeat
                    val o = Offset(c.x + random.nextFloat(-18f, 18f), c.y + random.nextFloat(-12f, 12f))
                    val dy = o.y - site.y
                    if (contains(ring, o) && distanceToRing(ring, o) > 6f && (kotlin.math.abs(o.x - site.x) > 18f || dy < -40f || dy > 14f)) {
                        props += Prop.Tree(o.x, o.y, random.nextFloat(4.5f, 6.5f), random.nextInt(3))
                    }
                }
            }
            Terrain.Hills -> repeat(count(1400f, 3, 8)) {
                spot(12f)?.let { props += Prop.Hill(it.x, it.y, random.nextFloat(22f, 32f), random.nextFloat(10f, 15f)) }
            }
            Terrain.Mountains -> repeat(count(2000f, 2, 6)) {
                spot(14f)?.let { props += Prop.Mountain(it.x, it.y, random.nextFloat(26f, 36f), random.nextFloat(28f, 40f), random.nextFloat(-4f, 4f)) }
            }
            Terrain.Marsh -> {
                repeat(count(2200f, 2, 5)) {
                    spot(12f)?.let { decals += Decal.Pool(it, random.nextFloat(8f, 13f), random.nextFloat(4f, 6.5f), random.nextFloat(-25f, 25f)) }
                }
                repeat(count(500f, 6, 20)) { spot(6f)?.let { props += Prop.Reeds(it.x, it.y, random.nextFloat(5f, 7.5f)) } }
            }
            Terrain.Plains -> {
                repeat(count(3400f, 1, 3)) {
                    spot(18f)?.let { decals += Decal.Field(it, random.nextFloat(26f, 34f), random.nextFloat(14f, 19f), random.nextFloat(-14f, 14f)) }
                }
                repeat(count(900f, 3, 10)) { spot(6f)?.let { props += Prop.Tuft(it.x, it.y, random.nextFloat(3.5f, 5f)) } }
            }
        }
    }

    /** Calls [action] at points every [spacing] along [path], skipping the ends where borders meet. */
    private fun along(path: List<Offset>, spacing: Float, action: (Offset, Int) -> Unit) {
        val length = path.zipWithNext { a, b -> (b - a).getDistance() }.sum()
        if (length < spacing) return
        var next = spacing * 0.75f
        var walked = 0f
        var index = 0
        for ((a, b) in path.zipWithNext()) {
            val segment = (b - a).getDistance()
            while (next <= walked + segment && next <= length - spacing * 0.6f) {
                val t = (next - walked) / segment
                action(a + (b - a) * t, index++)
                next += spacing
            }
            walked += segment
        }
    }

    /** A lake straddling the border: an oval at the path's middle, along its direction. */
    private fun lake(path: List<Offset>): Decal? {
        val length = path.zipWithNext { a, b -> (b - a).getDistance() }.sum()
        if (length < 20f) return null
        var walked = 0f
        for ((a, b) in path.zipWithNext()) {
            val segment = (b - a).getDistance()
            if (walked + segment >= length / 2) {
                val centre = a + (b - a) * ((length / 2 - walked) / segment)
                val degrees = atan2(b.y - a.y, b.x - a.x) * 180f / kotlin.math.PI.toFloat()
                return Decal.Pool(centre, min(length * 0.33f, 24f), 9f, degrees)
            }
            walked += segment
        }
        return null
    }
}

fun List<Offset>.toPath(close: Boolean) = Path().apply {
    moveTo(first().x, first().y)
    for (k in 1 until size) lineTo(this@toPath[k].x, this@toPath[k].y)
    if (close) close()
}

private fun bounds(ring: List<Offset>) =
    Offset(ring.minOf { it.x }, ring.minOf { it.y }) to Offset(ring.maxOf { it.x }, ring.maxOf { it.y })

private fun contains(ring: List<Offset>, p: Offset): Boolean {
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

private fun distanceToRing(ring: List<Offset>, p: Offset): Float {
    var best = Float.MAX_VALUE
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        val ab = b - a
        val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / max(ab.x * ab.x + ab.y * ab.y, 1e-6f)).coerceIn(0f, 1f)
        best = min(best, hypot(p.x - (a.x + ab.x * t), p.y - (a.y + ab.y * t)))
    }
    return best
}

private fun Random.nextFloat(from: Float, until: Float) = from + nextFloat() * (until - from)
