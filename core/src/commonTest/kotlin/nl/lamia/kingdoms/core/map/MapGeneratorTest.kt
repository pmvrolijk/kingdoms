package nl.lamia.kingdoms.core.map

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MapGeneratorTest {
    private val configs: List<MapConfig> =
        MapSize.entries.flatMap { size -> (1L..10L).map { MapConfig.of(size, seed = it) } } +
            listOf(
                MapConfig(seed = 11, landProvinces = 6),
                MapConfig(seed = 12, landProvinces = 120),
                MapConfig(seed = 13, landProvinces = 28, seaFraction = 0.0),
                MapConfig(seed = 14, landProvinces = 28, seaFraction = 0.7, featureFraction = 1.0),
            )

    private val maps: List<GameMap> = configs.map(MapGenerator::generate)

    @Test
    fun hitsTheLandProvinceCountExactly() = forEachMap { map ->
        assertEquals(map.config.landProvinces, map.provinces.size)
        map.provinces.forEachIndexed { index, province -> assertEquals(index, province.id) }
    }

    @Test
    fun landIsConnected() = forEachMap { map ->
        val seen = mutableSetOf(0)
        val queue = ArrayDeque(listOf(0))
        while (queue.isNotEmpty()) map.provinces[queue.removeFirst()].neighbours.forEach { if (seen.add(it)) queue += it }
        assertEquals(map.provinces.size, seen.size)
    }

    @Test
    fun neighboursAreSymmetricAndEachPairHasOneBorder() = forEachMap { map ->
        val pairs = map.borders.map { it.a to it.b }
        assertEquals(pairs.size, pairs.toSet().size, "duplicate border")
        map.borders.forEach { assertTrue(it.a < it.b) }
        for (p in map.provinces) for (n in p.neighbours) {
            assertTrue(p.id in map.provinces[n].neighbours, "${p.id} → $n not symmetric")
            assertTrue(map.border(p.id, n) != null, "no border for ${p.id}–$n")
        }
        assertEquals(map.provinces.sumOf { it.neighbours.size }, 2 * map.borders.size)
    }

    @Test
    fun bothProvincesContainTheSharedBorderPathExactly() = forEachMap { map ->
        for (border in map.borders) {
            for (id in listOf(border.a, border.b)) {
                val outline = map.provinces[id].outline.toSet()
                assertTrue(border.path.all { it in outline }, "border ${border.a}–${border.b} not in outline of $id")
            }
        }
    }

    @Test
    fun cellsTileTheMapExactly() = forEachMap { map ->
        val total = map.provinces.sumOf { Polygons.area(it.outline) } + map.seas.sumOf { Polygons.area(it) }
        assertTrue(abs(total - map.width * map.height) < 1e-6 * map.width * map.height, "tiling covers $total of ${map.width * map.height}")
    }

    @Test
    fun noOutlineSelfIntersects() = forEachMap { map ->
        for (p in map.provinces) {
            val ring = p.outline
            val n = ring.size
            for (i in 0 until n) for (j in i + 2 until n) {
                if (i == 0 && j == n - 1) continue
                if (properlyCross(ring[i], ring[(i + 1) % n], ring[j], ring[(j + 1) % n])) {
                    fail("province ${p.id} outline crosses itself (seed ${map.config.seed})")
                }
            }
        }
    }

    @Test
    fun bordersAndShoresNeverCross() = forEachMap { map ->
        val segments = (map.borders.map { it.path } + map.shores.map { it.path })
            .flatMap { path -> path.zipWithNext() }
        val boxes = segments.map { (a, b) -> Bounds(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)) }
        for (i in segments.indices) for (j in i + 1 until segments.size) {
            val bi = boxes[i]
            val bj = boxes[j]
            if (bi.maxX < bj.minX || bj.maxX < bi.minX || bi.maxY < bj.minY || bj.maxY < bi.minY) continue
            val (a, b) = segments[i]
            val (c, d) = segments[j]
            if (properlyCross(a, b, c, d)) fail("segments cross at $a–$b / $c–$d (seed ${map.config.seed})")
        }
    }

    @Test
    fun siteLiesInsideItsProvince() = forEachMap { map ->
        for (p in map.provinces) assertEquals(p.id, map.provinceAt(p.site)?.id, "site of ${p.id}")
    }

    @Test
    fun landNeverTouchesTheMapEdge() = forEachMap { map ->
        for (p in map.provinces) for (pt in p.outline) {
            assertTrue(pt.x > 0 && pt.y > 0 && pt.x < map.width && pt.y < map.height, "province ${p.id} touches the edge")
        }
    }

    @Test
    fun featureShareMatchesConfig() = forEachMap { map ->
        val expected = (map.borders.size * map.config.featureFraction).roundToInt()
        assertEquals(expected, map.borders.count { it.feature != null })
    }

    @Test
    fun coastalMeansItHasAShore() = forEachMap { map ->
        val withShore = map.shores.map { it.province }.toSet()
        map.provinces.forEach { assertEquals(it.id in withShore, it.coastal, "province ${it.id}") }
    }

    @Test
    fun coastAddsYieldAndFish() = forEachMap { map ->
        map.provinces.forEach {
            assertEquals(it.terrain.yield + if (it.coastal) COASTAL_YIELD else 0, it.yield)
            assertEquals(it.coastal, Resource.Fish in it.resources)
        }
    }

    @Test
    fun sameConfigSameMap() {
        configs.take(3).forEach { assertEquals(MapGenerator.generate(it), MapGenerator.generate(it)) }
    }

    @Test
    fun differentSeedsDifferentMaps() {
        assertTrue(MapGenerator.generate(MapConfig(seed = 1)) != MapGenerator.generate(MapConfig(seed = 2)))
    }

    private fun forEachMap(check: (GameMap) -> Unit) = maps.forEach(check)

    /** True when the segments cross at a point interior to both (touching endpoints don't count). */
    private fun properlyCross(a: Point, b: Point, c: Point, d: Point): Boolean {
        val d1 = (b - a) cross (c - a)
        val d2 = (b - a) cross (d - a)
        val d3 = (d - c) cross (a - c)
        val d4 = (d - c) cross (b - c)
        return d1 * d2 < 0 && d3 * d4 < 0
    }
}
