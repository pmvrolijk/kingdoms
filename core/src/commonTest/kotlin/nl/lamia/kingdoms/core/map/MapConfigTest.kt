package nl.lamia.kingdoms.core.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapConfigTest {
    @Test
    fun presetsCarryTheirProvinceCount() {
        assertEquals(28, MapConfig.of(MapSize.Medium, seed = 1).landProvinces)
    }

    @Test
    fun everyPresetFitsFourKingdoms() {
        MapSize.entries.forEach { assertTrue(MapConfig.of(it, seed = 1).fits(4), "$it") }
    }

    @Test
    fun aMapNeedsThreeProvincesPerKingdom() {
        val config = MapConfig(seed = 1, landProvinces = 12)
        assertTrue(config.fits(4))
        assertFalse(config.fits(5))
    }

    @Test
    fun rejectsAMapTooSmallForTwoKingdoms() {
        assertFailsWith<IllegalArgumentException> { MapConfig(seed = 1, landProvinces = 5) }
    }
}
