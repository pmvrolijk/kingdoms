package nl.lamia.kingdoms.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import nl.lamia.kingdoms.core.map.Terrain
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Which part of the app icon: Android's adaptive icon takes sea and land as separate layers. */
enum class IconLayer { Full, Background, Foreground }

/**
 * The app icon (Design §8.1): the player's capital — a forest province, roughly hexagonal, on its
 * earth slab in the sea, with the tower and a blue pennant — drawn with the map's own pieces.
 *
 * Laid out on Android's adaptive-icon grid: 108 units square, with everything that matters inside
 * the central circle of 66 units that every launcher mask keeps. [zoom] enlarges the land for
 * icons that show the whole square (iOS, desktop).
 */
fun DrawScope.drawAppIcon(layer: IconLayer = IconLayer.Full, zoom: Float = 1f) {
    val unit = size.minDimension / GRID
    withTransform({
        translate((size.width - GRID * unit) / 2, (size.height - GRID * unit) / 2)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        if (layer != IconLayer.Foreground) sea()
        if (layer != IconLayer.Background) {
            withTransform({ scale(zoom, zoom, pivot = Offset(GRID / 2, GRID / 2)) }) { capital() }
        }
    }
}

/** The icon as a [Painter], for the desktop window. */
class AppIconPainter(private val zoom: Float = 1.3f) : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() = drawAppIcon(zoom = zoom)
}

/** The adaptive-icon grid and the circle inside it that every mask keeps. */
const val GRID = 108f
const val SAFE = 66f

private const val R = 23f // hexagon radius
private const val WALL = 6f // slab thickness, as Board.WALL is to a province
private const val LINE = 0.8f // as the map's line is to a province
private val CENTRE = Offset(GRID / 2, GRID / 2 + 5f) // a little low: the tower and pennant rise above it

private fun DrawScope.sea() {
    drawRect(Palette.Sea, Offset.Zero, Size(GRID, GRID))
    val wave = Stroke(width = 1.1f, cap = StrokeCap.Round)
    val spacing = 21f
    for (row in -1..8) for (col in -1..6) {
        val x = col * spacing + (row.mod(2)) * spacing / 2 + 4f
        val y = row * spacing * 0.65f + 3f
        drawArc(Palette.Wave.copy(alpha = 0.75f), 200f, 140f, false, Offset(x - 4f, y - 2f), Size(8f, 4f), style = wave)
    }
}

/** The province's rim: a flat-topped hexagon seen at the board's tilt, each side with a slight wobble. */
private val rim: List<Offset> = run {
    val random = Random(7)
    val corners = List(6) { k ->
        val a = PI / 3 * k
        Offset(CENTRE.x + R * cos(a).toFloat(), CENTRE.y + R * sin(a).toFloat() * Board.TILT)
    }
    corners.indices.flatMap { k ->
        val a = corners[k]
        val b = corners[(k + 1) % 6]
        val normal = Offset(-(b - a).y, (b - a).x) / (b - a).getDistance()
        List(4) { i ->
            val t = i / 4f
            a + (b - a) * t + normal * if (i == 0) 0f else (random.nextFloat() - 0.5f) * 2.2f
        }
    }
}

private fun DrawScope.capital() {
    val outline = rim.toPath(close = true)
    val forest = Palette.terrain(Terrain.Forest)
    val you = Palette.kingdom(0)

    // As on the map: foam belongs to the water, under the land; then the slab's shadow and earth wall.
    val foam = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(outline, Palette.Foam.copy(alpha = 0.6f), style = foam)
    translate(0f, WALL) { drawPath(outline, Palette.Foam.copy(alpha = 0.8f), style = foam) }
    translate(1.5f, WALL + 1.5f) { drawPath(outline, Palette.BoardShadow) }
    drawPath(wall(0f, 0.55f), Palette.Earth)
    drawPath(wall(0.55f, 1f), Palette.EarthDark)

    // The land, owned: the forest's green, the kingdom's tint and inner edge, the coast line.
    drawPath(outline, forest)
    drawPath(outline, you.copy(alpha = 0.16f))
    clipPath(outline) { drawPath(outline, you.copy(alpha = 0.8f), style = Stroke(width = 3.2f, join = StrokeJoin.Round)) }
    drawPath(outline, forest.darker(0.5f), style = Stroke(LINE * 1.6f, join = StrokeJoin.Round, cap = StrokeCap.Round))

    // Trees and the capital, back to front.
    val dp = 0.56f
    val things = trees.map { tree -> tree.y to { drawProp(tree, LINE) } } +
        (CENTRE.y to { drawPennant(drawTower(CENTRE, you, dp), you, dp) })
    things.sortedBy { it.first }.forEach { it.second() }
}

/** The earth wall under the front edges: each rim segment swept down by [WALL], [from] to [to]. */
private fun wall(from: Float, to: Float) = Path().apply {
    rim.zipWithNext().plus(rim.last() to rim.first()).forEach { (a, b) ->
        moveTo(a.x, a.y + WALL * from)
        lineTo(b.x, b.y + WALL * from)
        lineTo(b.x, b.y + WALL * to)
        lineTo(a.x, a.y + WALL * to)
        close()
    }
}

/** A dozen trees inside the rim, clear of its edge and of the tower, larger than on the map so they read small. */
private val trees: List<Prop.Tree> = run {
    val random = Random(11)
    val found = mutableListOf<Prop.Tree>()
    var tries = 0
    while (found.size < 12 && tries++ < 2000) {
        val x = CENTRE.x + (random.nextFloat() * 2 - 1) * R * 0.86f
        val y = CENTRE.y + (random.nextFloat() * 2 - 1) * R * Board.TILT * 0.8f
        val inside = Offset(x - CENTRE.x, (y - CENTRE.y) / Board.TILT).getDistance() < R * 0.8f
        val clearOfTower = kotlin.math.abs(x - CENTRE.x) > 7.5f || y > CENTRE.y + 9f || y < CENTRE.y - 16f
        val apart = found.all { Offset(it.x - x, (it.y - y) * 1.6f).getDistance() > 6.5f }
        if (inside && clearOfTower && apart) found += Prop.Tree(x, y, 3.3f + random.nextFloat() * 0.9f, random.nextInt(3))
    }
    found
}
