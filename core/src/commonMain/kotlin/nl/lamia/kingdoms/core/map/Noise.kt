package nl.lamia.kingdoms.core.map

import kotlin.math.floor

/** Mixes values into one well-distributed 64-bit seed (SplitMix64 finaliser per value). */
internal fun mix(vararg values: Long): Long {
    var h = -7046029254386353131L // 0x9E3779B97F4A7C15
    for (v in values) h = splitMix(h xor v)
    return h
}

private fun splitMix(x: Long): Long {
    var z = x + -7046029254386353131L
    z = (z xor (z ushr 30)) * -4658895280553007687L // 0xBF58476D1CE4E5B9
    z = (z xor (z ushr 27)) * -7723592293110705685L // 0x94D049BB133111EB
    return z xor (z ushr 31)
}

/** Smooth 2D value noise on a unit lattice, deterministic for a seed on every platform. */
internal class ValueNoise(private val seed: Long) {
    /** Noise in [0, 1]. */
    fun at(x: Double, y: Double): Double {
        val x0 = floor(x)
        val y0 = floor(y)
        val ix = x0.toLong()
        val iy = y0.toLong()
        val sx = smooth(x - x0)
        val sy = smooth(y - y0)
        val top = lerp(lattice(ix, iy), lattice(ix + 1, iy), sx)
        val bottom = lerp(lattice(ix, iy + 1), lattice(ix + 1, iy + 1), sx)
        return lerp(top, bottom, sy)
    }

    /** Sum of [octaves] layers, each at double frequency and half amplitude; in [0, 1]. */
    fun fractal(x: Double, y: Double, octaves: Int = 3): Double {
        var sum = 0.0
        var amplitude = 1.0
        var frequency = 1.0
        var total = 0.0
        repeat(octaves) {
            sum += at(x * frequency, y * frequency) * amplitude
            total += amplitude
            amplitude /= 2
            frequency *= 2
        }
        return sum / total
    }

    private fun lattice(ix: Long, iy: Long): Double = (mix(seed, ix, iy) ushr 11).toDouble() / (1L shl 53)

    private fun smooth(t: Double) = t * t * (3 - 2 * t)

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
}
