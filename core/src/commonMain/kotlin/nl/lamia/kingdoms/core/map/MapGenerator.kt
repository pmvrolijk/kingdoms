package nl.lamia.kingdoms.core.map

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** Generates a [GameMap] from a [MapConfig] (Design §3.1). Same config, same map, on every platform. */
object MapGenerator {
    /** Average area of one cell in map units: a province is roughly 100 × 100. */
    const val CELL_AREA = 10_000.0

    /** Map width / height. */
    const val ASPECT = 1.5

    private const val LLOYD_ROUNDS = 2
    private const val CANDIDATES = 10
    private val SPACING = sqrt(CELL_AREA)

    /** Voronoi vertices closer than this are one vertex; it also collapses near-zero edges. */
    private val SNAP = SPACING * 0.01

    /** Border segments shorter than this are not subdivided further. */
    private val MIN_SEGMENT = SPACING * 0.08

    // Stream ids, so each stage draws from its own random sequence.
    private const val SITES = 1L
    private const val LAND = 2L
    private const val EDGES = 3L
    private const val ELEVATION = 4L
    private const val MOISTURE = 5L
    private const val FEATURES = 6L

    fun generate(config: MapConfig): GameMap {
        // Land is taken only from interior cells (not touching the map edge), so it never gets
        // cut off by the rectangle. seaFraction is the share of interior cells that stay sea.
        val interiorNeeded = ceil(config.landProvinces / (1.0 - config.seaFraction)).toInt()
        var cellCount = interiorNeeded + (4 * sqrt(interiorNeeded.toDouble())).toInt() + 4
        while (true) {
            val layout = Layout.build(config.seed, cellCount)
            val shortfall = interiorNeeded - layout.interior.count { it }
            if (shortfall <= 0) {
                val land = selectLand(config, layout)
                if (land != null) return assemble(config, layout, land)
            }
            cellCount += maxOf(shortfall, 0) + 2
        }
    }

    /** Sites, cells and the shared vertex table: the geometry before land, terrain and borders. */
    private class Layout(
        val width: Double,
        val height: Double,
        val sites: List<Point>,
        val vertices: List<Point>,
        /** Per cell, its vertex indices in ring order. */
        val rings: List<IntArray>,
        /** Per cell, what lies across edge k (ring[k] → ring[k + 1]): a cell index or BOUNDARY. */
        val labels: List<IntArray>,
    ) {
        val interior = BooleanArray(sites.size) { i -> labels[i].none { it == Voronoi.BOUNDARY } }
        val neighbours: List<List<Int>> = labels.map { l -> l.filter { it >= 0 } }

        companion object {
            fun build(seed: Long, cellCount: Int): Layout {
                val area = cellCount * CELL_AREA
                val width = sqrt(area * ASPECT)
                val height = area / width
                val random = Random(mix(seed, SITES, cellCount.toLong()))
                var sites = bestCandidates(cellCount, width, height, random)
                repeat(LLOYD_ROUNDS) {
                    sites = Voronoi.cells(sites, width, height).map { Polygons.centroid(it.vertices) }
                }
                return shared(width, height, sites, Voronoi.cells(sites, width, height))
            }

            /** Mitchell's best-candidate sampling: blue noise with an exact point count. */
            private fun bestCandidates(n: Int, width: Double, height: Double, random: Random): List<Point> {
                val points = ArrayList<Point>(n)
                repeat(n) {
                    var best = Point(0.0, 0.0)
                    var bestDistance = -1.0
                    repeat(CANDIDATES) {
                        val candidate = Point(random.nextDouble() * width, random.nextDouble() * height)
                        val distance = points.minOfOrNull { it.distanceSquaredTo(candidate) } ?: Double.MAX_VALUE
                        if (distance > bestDistance) {
                            best = candidate
                            bestDistance = distance
                        }
                    }
                    points += best
                }
                return points
            }

            /** Snaps the cells' independently computed corners into one shared vertex table. */
            private fun shared(width: Double, height: Double, sites: List<Point>, cells: List<Cell>): Layout {
                val vertices = ArrayList<Point>()
                val grid = HashMap<Long, MutableList<Int>>()
                fun gridKey(gx: Long, gy: Long) = gx shl 32 xor (gy and 0xFFFFFFFFL)
                fun vertexOf(p: Point): Int {
                    val gx = floor(p.x / SNAP).toLong()
                    val gy = floor(p.y / SNAP).toLong()
                    for (dx in -1L..1L) for (dy in -1L..1L) {
                        grid[gridKey(gx + dx, gy + dy)]?.forEach { if (vertices[it].distanceTo(p) < SNAP) return it }
                    }
                    vertices += p
                    grid.getOrPut(gridKey(gx, gy)) { mutableListOf() } += vertices.lastIndex
                    return vertices.lastIndex
                }

                val rings = ArrayList<IntArray>(cells.size)
                val labels = ArrayList<IntArray>(cells.size)
                for (cell in cells) {
                    val ring = cell.vertices.map(::vertexOf).toMutableList()
                    val label = cell.labels.toMutableList()
                    // Drop zero-length edges left by snapping; the following edge keeps its label.
                    var k = 0
                    while (k < ring.size && ring.size > 2) {
                        if (ring[k] == ring[(k + 1) % ring.size]) {
                            ring.removeAt(k)
                            label.removeAt(k)
                        } else {
                            k++
                        }
                    }
                    rings += ring.toIntArray()
                    labels += label.toIntArray()
                }
                checkConsistent(rings, labels)
                return Layout(width, height, sites, vertices, rings, labels)
            }

            /** Every interior edge must appear in both cells, reversed, each labelled with the other. */
            private fun checkConsistent(rings: List<IntArray>, labels: List<IntArray>) {
                val owner = HashMap<Long, Int>()
                for (i in rings.indices) forEachEdge(rings[i]) { k, u, v -> if (labels[i][k] >= 0) owner[edgeKey(u, v)] = i }
                for (i in rings.indices) forEachEdge(rings[i]) { k, u, v ->
                    val j = labels[i][k]
                    check(j < 0 || owner[edgeKey(v, u)] == j) { "Voronoi edge $u→$v of cell $i has no twin in cell $j" }
                }
            }
        }
    }

    /**
     * Grows the land from the best-scoring interior cell, always adding the best-scoring interior
     * cell next to the land so far. Land is connected by construction and has exactly the requested
     * size. Score is noise minus a radial falloff, so the result is one irregular island.
     */
    private fun selectLand(config: MapConfig, layout: Layout): BooleanArray? {
        val noise = ValueNoise(mix(config.seed, LAND))
        val cx = layout.width / 2
        val cy = layout.height / 2
        val score = DoubleArray(layout.sites.size) { i ->
            val s = layout.sites[i]
            val dx = (s.x - cx) / cx
            val dy = (s.y - cy) / cy
            noise.fractal(s.x / (2.5 * SPACING), s.y / (2.5 * SPACING)) - 0.8 * (dx * dx + dy * dy)
        }
        val land = BooleanArray(layout.sites.size)
        val start = layout.sites.indices.filter { layout.interior[it] }.maxByOrNull { score[it] } ?: return null
        land[start] = true
        repeat(config.landProvinces - 1) {
            val next = layout.sites.indices
                .filter { !land[it] && layout.interior[it] && layout.neighbours[it].any { n -> land[n] } }
                .maxByOrNull { score[it] } ?: return null
            land[next] = true
        }
        return land
    }

    private fun assemble(config: MapConfig, layout: Layout, land: BooleanArray): GameMap {
        val cellCount = layout.sites.size
        val provinceOf = IntArray(cellCount) { -1 }
        var next = 0
        for (i in 0 until cellCount) if (land[i]) provinceOf[i] = next++

        // One noisy path per edge that touches land, generated once and shared by both cells.
        val paths = HashMap<Long, List<Point>>()
        for (i in 0 until cellCount) forEachEdge(layout.rings[i]) { k, u, v ->
            val j = layout.labels[i][k]
            if (j > i && (land[i] || land[j])) {
                val from = minOf(u, v)
                val to = maxOf(u, v)
                paths[pairKey(i, j)] = NoisyEdges.path(
                    layout.vertices[from], layout.vertices[to], layout.sites[i], layout.sites[j],
                    MIN_SEGMENT, Random(mix(config.seed, EDGES, i.toLong(), j.toLong())),
                )
            }
        }

        /** The path of cell [i]'s edge u→v, oriented u→v. */
        fun edgePath(i: Int, j: Int, u: Int, v: Int): List<Point> {
            val path = (if (j >= 0) paths[pairKey(minOf(i, j), maxOf(i, j))] else null)
                ?: return listOf(layout.vertices[u], layout.vertices[v])
            return if (u < v) path else path.asReversed()
        }

        fun outline(i: Int): List<Point> {
            val out = ArrayList<Point>()
            forEachEdge(layout.rings[i]) { k, u, v -> out += edgePath(i, layout.labels[i][k], u, v).dropLast(1) }
            return out
        }

        val landCells = (0 until cellCount).filter { land[it] }
        val coastal = landCells.filter { i -> layout.neighbours[i].any { !land[it] } }.toSet()
        val terrain = assignTerrain(config, layout, land, landCells, coastal)

        val provinces = landCells.map { i ->
            Province(
                id = provinceOf[i],
                site = layout.sites[i],
                terrain = terrain.getValue(i),
                coastal = i in coastal,
                outline = outline(i),
                neighbours = layout.neighbours[i].filter { land[it] }.map { provinceOf[it] },
            )
        }

        val landPairs = ArrayList<Pair<Int, Int>>()
        val shores = ArrayList<Shore>()
        for (i in landCells) forEachEdge(layout.rings[i]) { k, u, v ->
            val j = layout.labels[i][k]
            when {
                j > i && land[j] -> landPairs += i to j
                j < 0 || !land[j] -> shores +=Shore(provinceOf[i], edgePath(i, j, u, v))
            }
        }
        val features = assignFeatures(config, landPairs.map { (i, j) -> terrain.getValue(i) to terrain.getValue(j) })
        val borders = landPairs.mapIndexed { n, (i, j) ->
            Border(provinceOf[i], provinceOf[j], paths.getValue(pairKey(i, j)), features[n])
        }

        return GameMap(
            config = config,
            width = layout.width,
            height = layout.height,
            provinces = provinces,
            borders = borders,
            shores = shores,
            seas = (0 until cellCount).filter { !land[it] }.map(::outline),
        )
    }

    /**
     * Terrain by quota over two noise fields (Design §3.3). Elevation rises with distance from the
     * sea, so mountains sit inland; the wettest of the rest become marsh, then forest; the remainder
     * is plains. Being coastal is not a terrain; it only seeds the distance from the sea.
     */
    private fun assignTerrain(
        config: MapConfig,
        layout: Layout,
        land: BooleanArray,
        landCells: List<Int>,
        coastal: Set<Int>,
    ): Map<Int, Terrain> {
        val distance = HashMap<Int, Int>()
        val queue = ArrayDeque(coastal.sorted())
        coastal.forEach { distance[it] = 0 }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            for (n in layout.neighbours[i]) if (land[n] && n !in distance) {
                distance[n] = distance.getValue(i) + 1
                queue += n
            }
        }
        val maxDistance = maxOf(1, distance.values.maxOrNull() ?: 1).toDouble()
        val elevationNoise = ValueNoise(mix(config.seed, ELEVATION))
        val moistureNoise = ValueNoise(mix(config.seed, MOISTURE))
        val scale = 3 * SPACING
        fun elevation(i: Int): Double {
            val s = layout.sites[i]
            return 0.6 * elevationNoise.fractal(s.x / scale, s.y / scale) + 0.4 * distance.getValue(i) / maxDistance
        }
        fun moisture(i: Int): Double {
            val s = layout.sites[i]
            return moistureNoise.fractal(s.x / scale, s.y / scale)
        }

        val n = landCells.size
        val result = HashMap<Int, Terrain>()
        val byElevation = landCells.sortedByDescending(::elevation)
        val mountains = (n * 0.12).roundToInt()
        val hills = (n * 0.18).roundToInt()
        byElevation.take(mountains).forEach { result[it] = Terrain.Mountains }
        byElevation.drop(mountains).take(hills).forEach { result[it] = Terrain.Hills }
        val lowlands = byElevation.drop(mountains + hills).sortedByDescending(::moisture)
        val marsh = (n * 0.12).roundToInt()
        val forest = (n * 0.22).roundToInt()
        lowlands.take(marsh).forEach { result[it] = Terrain.Marsh }
        lowlands.drop(marsh).take(forest).forEach { result[it] = Terrain.Forest }
        lowlands.drop(marsh + forest).forEach { result[it] = Terrain.Plains }
        return result
    }

    /** Picks `featureFraction` of the land borders at random; the type follows the terrain on either side. */
    private fun assignFeatures(config: MapConfig, sides: List<Pair<Terrain, Terrain>>): List<Feature?> {
        val random = Random(mix(config.seed, FEATURES))
        val count = (sides.size * config.featureFraction).roundToInt()
        val chosen = sides.indices.shuffled(random).take(count).toSet()
        return sides.mapIndexed { n, (a, b) ->
            if (n !in chosen) {
                null
            } else {
                when {
                    a == Terrain.Mountains || b == Terrain.Mountains -> Feature.MountainRange
                    a == Terrain.Marsh || b == Terrain.Marsh -> Feature.Marsh
                    a == Terrain.Hills || b == Terrain.Hills -> Feature.Ridge
                    random.nextInt(4) == 0 -> Feature.Lake
                    else -> Feature.River
                }
            }
        }
    }

    private inline fun forEachEdge(ring: IntArray, action: (k: Int, u: Int, v: Int) -> Unit) {
        for (k in ring.indices) action(k, ring[k], ring[(k + 1) % ring.size])
    }

    private fun edgeKey(u: Int, v: Int) = u.toLong() shl 32 or v.toLong()

    private fun pairKey(i: Int, j: Int) = i.toLong() shl 32 or j.toLong()
}
