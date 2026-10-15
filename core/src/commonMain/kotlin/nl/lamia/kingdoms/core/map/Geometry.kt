package nl.lamia.kingdoms.core.map

import kotlin.math.abs
import kotlin.math.sqrt

/** A point in map units. A province is roughly 100 × 100 units (see [MapGenerator.CELL_AREA]). */
data class Point(val x: Double, val y: Double) {
    operator fun plus(o: Point) = Point(x + o.x, y + o.y)
    operator fun minus(o: Point) = Point(x - o.x, y - o.y)
    operator fun times(s: Double) = Point(x * s, y * s)
    operator fun unaryMinus() = Point(-x, -y)
    infix fun dot(o: Point) = x * o.x + y * o.y
    infix fun cross(o: Point) = x * o.y - y * o.x
    val length: Double get() = sqrt(x * x + y * y)
    fun distanceTo(o: Point) = (this - o).length
    fun distanceSquaredTo(o: Point): Double {
        val dx = x - o.x
        val dy = y - o.y
        return dx * dx + dy * dy
    }
}

fun lerp(a: Point, b: Point, t: Double) = Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** Operations on closed rings: the last point connects back to the first. */
object Polygons {
    fun signedArea(ring: List<Point>): Double {
        var sum = 0.0
        for (k in ring.indices) sum += ring[k] cross ring[(k + 1) % ring.size]
        return sum / 2
    }

    fun area(ring: List<Point>) = abs(signedArea(ring))

    fun centroid(ring: List<Point>): Point {
        var cx = 0.0
        var cy = 0.0
        var twiceArea = 0.0
        for (k in ring.indices) {
            val a = ring[k]
            val b = ring[(k + 1) % ring.size]
            val c = a cross b
            twiceArea += c
            cx += (a.x + b.x) * c
            cy += (a.y + b.y) * c
        }
        return Point(cx / (3 * twiceArea), cy / (3 * twiceArea))
    }

    /** Even-odd point-in-polygon test. */
    fun contains(ring: List<Point>, p: Point): Boolean {
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
}

/** Axis-aligned bounding box. */
data class Bounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    operator fun contains(p: Point) = p.x in minX..maxX && p.y in minY..maxY

    companion object {
        fun of(points: List<Point>) = Bounds(
            points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y },
        )
    }
}
