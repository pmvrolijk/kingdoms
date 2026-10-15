package nl.lamia.kingdoms.core.map

/**
 * A Voronoi cell clipped to the map rectangle. `labels[k]` names what lies across the edge from
 * `vertices[k]` to `vertices[k + 1]`: the neighbouring site index, or [Voronoi.BOUNDARY].
 */
internal class Cell(val vertices: List<Point>, val labels: List<Int>)

/**
 * Voronoi cells by half-plane clipping: each cell starts as the map rectangle and is cut by the
 * perpendicular bisector to every other site, nearest first, until no bisector can reach it.
 * O(n² log n), which is nothing at a few hundred sites, and robust without a triangulation.
 */
internal object Voronoi {
    const val BOUNDARY = -1

    fun cells(sites: List<Point>, width: Double, height: Double): List<Cell> =
        sites.indices.map { cell(it, sites, width, height) }

    private fun cell(i: Int, sites: List<Point>, width: Double, height: Double): Cell {
        val p = sites[i]
        var vertices = listOf(Point(0.0, 0.0), Point(width, 0.0), Point(width, height), Point(0.0, height))
        var labels = List(4) { BOUNDARY }
        val others = sites.indices.filter { it != i }.sortedBy { sites[it].distanceSquaredTo(p) }
        for (j in others) {
            val reach = vertices.maxOf { it.distanceSquaredTo(p) }
            if (sites[j].distanceSquaredTo(p) / 4 > reach) break
            val clipped = clip(vertices, labels, p, sites[j], j)
            vertices = clipped.first
            labels = clipped.second
        }
        return Cell(vertices, labels)
    }

    /** Keeps the part of a convex polygon closer to [p] than to [q]; the new edge is labelled [j]. */
    private fun clip(vertices: List<Point>, labels: List<Int>, p: Point, q: Point, j: Int): Pair<List<Point>, List<Int>> {
        val mid = lerp(p, q, 0.5)
        val dir = q - p
        val outVertices = ArrayList<Point>(vertices.size + 1)
        val outLabels = ArrayList<Int>(vertices.size + 1)
        for (k in vertices.indices) {
            val cur = vertices[k]
            val next = vertices[(k + 1) % vertices.size]
            val sCur = (cur - mid) dot dir
            val sNext = (next - mid) dot dir
            val curIn = sCur <= 0
            val nextIn = sNext <= 0
            if (curIn) {
                outVertices += cur
                outLabels += labels[k]
            }
            if (curIn != nextIn) {
                outVertices += lerp(cur, next, sCur / (sCur - sNext))
                outLabels += if (curIn) j else labels[k]
            }
        }
        return outVertices to outLabels
    }
}
