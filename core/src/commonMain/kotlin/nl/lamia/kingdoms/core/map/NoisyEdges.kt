package nl.lamia.kingdoms.core.map

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Irregular borders by recursive subdivision inside a convex quad (Design §3.1 step 4).
 *
 * For an edge A–C between sites P and Q, the controls B and D sit on the perpendicular through the
 * edge's midpoint, at a height that keeps B inside triangle A-C-P and D inside triangle A-C-Q. Those
 * triangles are fan triangles of convex Voronoi cells, so the quads of different edges never overlap.
 * Each step picks a point H on the diagonal B–D and recurses into two sub-quads that lie in the
 * disjoint triangles A-B-D and C-B-D. The path therefore stays inside its own quad: borders never
 * cross each other and never self-intersect.
 */
internal object NoisyEdges {
    /** How far the controls reach into the safe triangles, as a share of the safe height. */
    private const val AMPLITUDE = 0.6

    /** How far H may stray from the middle of the diagonal B–D. */
    private const val WOBBLE = 0.25

    fun path(a: Point, c: Point, sideP: Point, sideQ: Point, minLength: Double, random: Random): List<Point> {
        val ac = c - a
        val length = ac.length
        if (length < minLength) return listOf(a, c)
        var normal = Point(-ac.y / length, ac.x / length)
        val mid = lerp(a, c, 0.5)
        if ((sideP - mid) dot normal < 0) normal = -normal
        val height = AMPLITUDE * min(safeHeight(a, c, sideP), safeHeight(a, c, sideQ))
        val out = mutableListOf(a)
        subdivide(a, mid + normal * height, c, mid - normal * height, minLength, random, out)
        out += c
        return out
    }

    /** Height of triangle A-C-P above the midpoint of A–C. */
    private fun safeHeight(a: Point, c: Point, p: Point): Double {
        val ac = c - a
        val lengthSquared = ac dot ac
        val s = ((p - a) dot ac) / lengthSquared
        val distance = abs(ac cross (p - a)) / sqrt(lengthSquared)
        return distance * 0.5 / max(s, 1 - s)
    }

    private fun subdivide(a: Point, b: Point, c: Point, d: Point, minLength: Double, random: Random, out: MutableList<Point>) {
        if (a.distanceTo(c) < minLength) return
        val h = lerp(d, b, random.nextDouble(0.5 - WOBBLE, 0.5 + WOBBLE))
        subdivide(a, lerp(a, b, 0.5), h, lerp(a, d, 0.5), minLength, random, out)
        out += h
        subdivide(h, lerp(c, b, 0.5), c, lerp(c, d, 0.5), minLength, random, out)
    }
}
