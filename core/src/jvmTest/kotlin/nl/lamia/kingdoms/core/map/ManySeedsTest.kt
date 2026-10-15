package nl.lamia.kingdoms.core.map

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Generation must never fail and cells must tile the map, for every seed — not just the 34 maps
 * in MapGeneratorTest. A greedy 1%-of-spacing vertex snap failed 2 Medium seeds in 3000 (crash)
 * and, made transitive, mis-tiled ~2% of maps; this is the check that found both. JVM only, ~10 s.
 */
class ManySeedsTest {
    @Test
    fun everySeedGeneratesAndTiles() {
        for (size in MapSize.entries) {
            val bad = (1L..3000L).filter { seed ->
                val map = runCatching { MapGenerator.generate(MapConfig.of(size, seed)) }.getOrNull() ?: return@filter true
                val total = map.provinces.sumOf { Polygons.area(it.outline) } + map.seas.sumOf { Polygons.area(it) }
                abs(total - map.width * map.height) > 1e-6 * map.width * map.height
            }
            assertTrue(bad.isEmpty(), "$size: ${bad.size} seeds fail, e.g. ${bad.take(5)}")
        }
    }
}
