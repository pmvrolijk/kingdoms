package nl.lamia.kingdoms.core.map

import kotlin.random.Random

/**
 * A small hand-laid map: [cols] × [rows] provinces on a jittered grid, all land, with sea around
 * it — the help screen's scenes (Design §6.3). Borders and coasts are the generator's noisy edges,
 * so it looks like any other map. Province `x + y × cols` sits in column x, row y.
 * It has no [GameMap.seas]: nothing is under the sea. Like any map it needs at least six provinces.
 */
object GridMap {
    private const val CELL = 100.0
    private const val MARGIN = 50.0
    private const val JITTER = 14.0

    fun create(
        cols: Int,
        rows: Int,
        terrain: List<Terrain>,
        features: Map<Pair<Int, Int>, Feature> = emptyMap(),
        seed: Long = 1,
    ): GameMap {
        val count = cols * rows
        require(terrain.size == count) { "need $count terrains, got ${terrain.size}" }
        val random = Random(seed)
        fun jitter() = random.nextDouble(-JITTER, JITTER)
        val vertices = List((cols + 1) * (rows + 1)) { v ->
            Point(MARGIN + (v % (cols + 1)) * CELL + jitter(), MARGIN + (v / (cols + 1)) * CELL + jitter())
        }
        fun vertex(x: Int, y: Int) = x + y * (cols + 1)
        /** Corners of cell (x, y), clockwise on screen (y grows down). */
        fun corners(x: Int, y: Int) = listOf(vertex(x, y), vertex(x + 1, y), vertex(x + 1, y + 1), vertex(x, y + 1))
        val sites = List(count) { id ->
            val ring = corners(id % cols, id / cols).map(vertices::get)
            Point(ring.sumOf { it.x } / 4, ring.sumOf { it.y } / 4)
        }

        // One path per edge, stored from the lower vertex index to the higher; the cell on the
        // other side is the neighbour, or a mirror image of this site out at sea.
        val edges = HashMap<Pair<Int, Int>, List<Point>>()
        fun edge(u: Int, v: Int, site: Point, other: Point?): List<Point> {
            val key = minOf(u, v) to maxOf(u, v)
            val path = edges.getOrPut(key) {
                val a = vertices[key.first]
                val c = vertices[key.second]
                val far = other ?: (a + c - site)
                NoisyEdges.path(a, c, site, far, CELL * 0.08, random)
            }
            return if (u < v) path else path.asReversed()
        }
        fun cellAt(x: Int, y: Int) = if (x in 0 until cols && y in 0 until rows) x + y * cols else null

        val outlines = ArrayList<List<Point>>()
        val shores = ArrayList<Shore>()
        val neighbours = List(count) { mutableListOf<Int>() }
        val borderPaths = LinkedHashMap<Pair<Int, Int>, List<Point>>()
        for (id in 0 until count) {
            val x = id % cols
            val y = id / cols
            val ring = corners(x, y)
            val across = listOf(cellAt(x, y - 1), cellAt(x + 1, y), cellAt(x, y + 1), cellAt(x - 1, y))
            val outline = ArrayList<Point>()
            for (k in 0 until 4) {
                val other = across[k]
                val path = edge(ring[k], ring[(k + 1) % 4], sites[id], other?.let(sites::get))
                outline += path.dropLast(1)
                when {
                    other == null -> shores += Shore(id, path)
                    else -> {
                        neighbours[id] += other
                        if (other > id) borderPaths[id to other] = path
                    }
                }
            }
            outlines += outline
        }

        val borders = borderPaths.map { (pair, path) -> Border(pair.first, pair.second, path, features[pair] ?: features[pair.second to pair.first]) }
        val coastal = shores.map { it.province }.toSet()
        val names = ProvinceNames.generate(terrain.mapIndexed { id, t -> t to (id in coastal) }, Random(seed))
        val provinces = (0 until count).map { id ->
            Province(id, names[id], sites[id], terrain[id], id in coastal, outlines[id], neighbours[id].sorted())
        }
        return GameMap(
            config = MapConfig(seed = seed, landProvinces = count, seaFraction = 0.0),
            width = cols * CELL + 2 * MARGIN,
            height = rows * CELL + 2 * MARGIN,
            provinces = provinces,
            borders = borders,
            shores = shores,
            seas = emptyList(),
        )
    }
}
