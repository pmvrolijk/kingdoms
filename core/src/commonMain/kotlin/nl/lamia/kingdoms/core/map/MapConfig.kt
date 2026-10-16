package nl.lamia.kingdoms.core.map

import kotlinx.serialization.Serializable

/** Player-facing map size presets (Design §3.5), sized for 4 kingdoms. */
enum class MapSize(val landProvinces: Int) {
    Small(18),
    Medium(28),
    Large(40),
}

/** Everything the map generator needs; map size is never a constant (Design §3.5). */
@Serializable
data class MapConfig(
    val seed: Long,
    val landProvinces: Int = MapSize.Medium.landProvinces,
    val seaFraction: Double = 0.35,
    val featureFraction: Double = 0.33,
) {
    init {
        require(landProvinces >= MIN_PROVINCES_PER_KINGDOM * MIN_KINGDOMS) {
            "landProvinces must be at least ${MIN_PROVINCES_PER_KINGDOM * MIN_KINGDOMS}, was $landProvinces"
        }
        require(seaFraction >= 0.0 && seaFraction < 1.0) { "seaFraction must be in [0, 1), was $seaFraction" }
        require(featureFraction in 0.0..1.0) { "featureFraction must be in [0, 1], was $featureFraction" }
    }

    /** Whether this map is large enough for [kingdoms] kingdoms. */
    fun fits(kingdoms: Int): Boolean = landProvinces >= kingdoms * MIN_PROVINCES_PER_KINGDOM

    companion object {
        const val MIN_KINGDOMS = 2
        const val MIN_PROVINCES_PER_KINGDOM = 3

        fun of(size: MapSize, seed: Long) = MapConfig(seed = seed, landProvinces = size.landProvinces)
    }
}
