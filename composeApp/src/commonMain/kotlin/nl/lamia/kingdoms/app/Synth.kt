package nl.lamia.kingdoms.app

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The game's sound effects (Design §6.4), [seconds] long. Each has a few variants, so a repeated
 * sound is not identical. One that does not [overlap] is skipped while it is still playing.
 */
enum class Sound(val variants: Int, val seconds: Double, val overlaps: Boolean = true) {
    /** A soft blip: buttons and chips. */
    Click(1, 0.06),

    /** Swords meeting: a battle. */
    Clash(4, 0.85),

    /** Footsteps of grey noise: armies marching. Quick moves would pile up into a crowd. */
    March(3, 0.95, overlaps = false),

    /** Ta-da: the player has won. */
    Victory(1, 1.25),
}

/**
 * Makes every sound in code: mono samples in -1..1 at [RATE], and the same as a WAV file for the
 * platforms that play files. Pure and deterministic: the noise comes from a fixed seed. Every sound
 * fades in and out (no click at the ends) and is scaled to its own peak, so loudness is set here,
 * not by chance.
 */
object Synth {
    const val RATE = 22_050

    fun render(sound: Sound, variant: Int = 0): FloatArray = when (sound) {
        Sound.Click -> click()
        Sound.Clash -> clash(variant)
        Sound.March -> march(variant)
        Sound.Victory -> victory()
    }.also {
        finish(it, PEAK.getValue(sound))
        // The march's peak is a random stone of gravel, so its variants are levelled by loudness.
        if (sound == Sound.March) level(it, rms = 0.05f, ceiling = 0.45f)
    }

    /** How loud each sound peaks, so none of them startles: a click is quiet, the fanfare is the loudest. */
    private val PEAK = mapOf(Sound.Click to 0.3f, Sound.Clash to 0.7f, Sound.March to 0.3f, Sound.Victory to 0.6f)

    private fun samples(sound: Sound) = FloatArray((sound.seconds * RATE).toInt())

    private fun click() = samples(Sound.Click).also { out ->
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            phase += 2 * PI * (1150 - 2500 * t) / RATE // a short drop in pitch makes it a blip, not a beep
            out[i] = (sin(phase) * exp(-t * 60)).toFloat()
        }
    }

    /**
     * Two blades meeting, modelled on a recorded sword clash: a bright scrape at the strike, then a
     * high ring (partials near 1, 1.5, 1.96 and 2.35 × ~2 kHz, nothing low — a low fundamental is
     * what makes a bell). Each partial has a twin 30–90 Hz off, so the ring shimmers instead of
     * chiming; the upper ones lead and die first, and the whole falls fast, then lingers briefly. Each variant
     * draws its pitch, twins and decays from its own seed.
     */
    private fun clash(variant: Int) = samples(Sound.Clash).also { out ->
        val random = Random(101 + variant)
        val base = random.nextDouble(1750.0, 2250.0)
        val ratios = listOf(1.0, 1.5, 1.96, 2.35).map { it * random.nextDouble(0.98, 1.02) }
        // The upper partials lead at the strike; the lowest outlasts them.
        val levels = listOf(0.45, 1.0, 0.9, 0.4)
        val decays = listOf(4.0, 9.0, 14.0, 20.0).map { it * random.nextDouble(0.85, 1.2) }
        val twins = ratios.map { random.nextDouble(30.0, 90.0) * (if (random.nextBoolean()) 1 else -1) }
        val twinLevels = ratios.map { random.nextDouble(0.3, 0.6) }
        val phases = DoubleArray(ratios.size * 2)
        var previous = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            var ring = 0.0
            for (k in ratios.indices) {
                val f = base * ratios[k]
                phases[2 * k] += 2 * PI * f / RATE
                phases[2 * k + 1] += 2 * PI * (f + twins[k]) / RATE
                ring += (sin(phases[2 * k]) + twinLevels[k] * sin(phases[2 * k + 1])) * levels[k] * exp(-t * decays[k])
            }
            // The first tenth of a second is louder: the blades still touching.
            ring *= 0.45 + 0.55 * exp(-t * 22)
            // The scrape: noise with its lows taken off (a difference of samples), gone in ~10 ms.
            val n = random.nextDouble(-1.0, 1.0)
            val scrape = (n - previous) * 1.2 * exp(-t * 220)
            previous = n
            // A soft release over the last fifth, so the tail ends without a cut.
            val release = min(1.0, (out.size - i).toDouble() / (0.2 * out.size))
            out[i] = ((ring + scrape) * release).toFloat()
        }
    }

    /**
     * Two footfalls, modelled on a recording of soldiers marching: each a swell of grey noise —
     * pink, with extra body at 150–500 Hz and little hiss — that rises in ~45 ms, holds briefly
     * and falls ~20 dB by the next step, over a low rumble that never quite stops. The onset of
     * each step is a little brighter (the crunch), with gravel: tiny random ticks of grit. Steps ~0.42 s apart, about the recording's
     * 0.46. Each variant draws its spacing and step levels from
     * its seed.
     */
    private fun march(variant: Int) = samples(Sound.March).also { out ->
        val random = Random(201 + variant)
        val spacing = random.nextDouble(0.38, 0.47)
        val steps = List(2) { k -> k * spacing + random.nextDouble(0.0, 0.03) }
        val levels = List(2) { k -> (1.0 - 0.12 * k) * random.nextDouble(0.85, 1.0) }
        val body = Filter(300.0, 0.7)
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        var dark = 0.0
        var sub = 0.0
        val grit = Filter(2000.0, 1.5)
        var grain = 0.0
        val grainFade = exp(-1 / (RATE * 0.0015))
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            // Pink noise (Kellet's three-pole approximation) without what is under ~100 Hz, which a
            // phone cannot play; then a gentle low-pass (~3 kHz) for the hiss.
            val white = random.nextDouble(-1.0, 1.0)
            b0 = 0.99765 * b0 + white * 0.0990460
            b1 = 0.96300 * b1 + white * 0.2965164
            b2 = 0.57000 * b2 + white * 1.0526913
            val raw = (b0 + b1 + b2 + white * 0.1848) * 0.25
            sub += 0.028 * (raw - sub)
            val pink = raw - sub
            dark += 0.6 * (pink - dark)
            val grey = dark + 1.2 * body.band(pink)
            val bright = pink - dark
            var step = 0.0
            var crunch = 0.0
            var gravel = 0.0
            for ((k, at) in steps.withIndex()) {
                val since = t - at
                if (since < 0) continue
                val rise = min(1.0, since / 0.044)
                step += levels[k] * rise * rise * exp(-maxOf(0.0, since - 0.065) * 9.2)
                crunch += 1.6 * levels[k] * rise * exp(-since * 16)
                gravel += levels[k] * rise * exp(-since * 10.8)
            }
            // Gravel: tiny ticks of gritty noise at random, densest as the foot lands (up to ~900 a
            // second), each gone in a couple of milliseconds; mostly small ones, now and then a stone.
            grain *= grainFade
            if (random.nextDouble() < 900.0 * gravel / RATE) grain += random.nextDouble().let { it * it }
            val crackle = 1.0 * grain * grit.band(white)
            val rumble = 0.1 * min(1.0, t / 0.065)
            val release = min(1.0, (out.size - i).toDouble() / (0.15 * out.size))
            out[i] = ((grey * (step + rumble) + bright * crunch + crackle) * release).toFloat()
        }
    }

    /** A short note, then a long major chord a fourth above, with a soft brass-like tone and vibrato. */
    private fun victory() = samples(Sound.Victory).also { out ->
        fun note(start: Double, length: Double, frequencies: List<Double>) {
            val phases = DoubleArray(frequencies.size)
            val from = (start * RATE).toInt()
            val to = min(out.size, ((start + length) * RATE).toInt())
            for (i in from until to) {
                val t = (i - from).toDouble() / RATE
                val envelope = min(1.0, t / 0.015) * exp(-t * 2.2) * min(1.0, (to - i).toDouble() / (0.08 * RATE))
                val vibrato = 1 + 0.004 * sin(2 * PI * 5.5 * t) * min(1.0, t / 0.3)
                var s = 0.0
                for ((k, f) in frequencies.withIndex()) {
                    phases[k] += 2 * PI * f * vibrato / RATE
                    // A few harmonics, falling off: round, not buzzy.
                    s += sin(phases[k]) + 0.4 * sin(2 * phases[k]) + 0.18 * sin(3 * phases[k]) + 0.08 * sin(4 * phases[k])
                }
                out[i] += (s * envelope / frequencies.size).toFloat()
            }
        }
        note(0.0, 0.16, listOf(392.0, 493.9)) // ta: G and B
        note(0.17, 1.08, listOf(523.3, 659.3, 784.0)) // da: C major
    }

    /** Short fades at both ends, then scaled so the loudest sample is [peak]. */
    private fun finish(out: FloatArray, peak: Float) {
        val fade = (0.004 * RATE).toInt()
        for (i in 0 until min(fade, out.size)) {
            val g = i.toFloat() / fade
            out[i] *= g
            out[out.size - 1 - i] *= g
        }
        val max = out.maxOf { abs(it) }
        if (max > 0) for (i in out.indices) out[i] *= peak / max
    }

    /** Scales [out] so its first 0.2 s has this [rms], but never peaks above [ceiling]. */
    private fun level(out: FloatArray, rms: Float, ceiling: Float) {
        val head = out.take((0.2 * RATE).toInt())
        val now = kotlin.math.sqrt(head.sumOf { (it * it).toDouble() } / head.size).toFloat()
        val peak = out.maxOf { abs(it) }
        if (now <= 0f || peak <= 0f) return
        val gain = min(rms / now, ceiling / peak)
        for (i in out.indices) out[i] *= gain
    }

    /** [samples] as a 16-bit mono WAV file. */
    fun wav(samples: FloatArray): ByteArray {
        val data = samples.size * 2
        val out = ByteArray(44 + data)
        var at = 0
        fun text(s: String) = s.forEach { out[at++] = it.code.toByte() }
        fun int(v: Int, bytes: Int) = repeat(bytes) { out[at++] = (v ushr (8 * it)).toByte() }
        text("RIFF"); int(36 + data, 4); text("WAVE")
        text("fmt "); int(16, 4); int(1, 2); int(1, 2); int(RATE, 4); int(RATE * 2, 4); int(2, 2); int(16, 2)
        text("data"); int(data, 4)
        for (s in samples) int((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt(), 2)
        return out
    }

    /** [samples] as signed 16-bit little-endian PCM, scaled by [volume]. */
    fun pcm(samples: FloatArray, volume: Float = 1f): ByteArray {
        val out = ByteArray(samples.size * 2)
        for ((i, s) in samples.withIndex()) {
            val v = ((s * volume).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
            out[2 * i] = v.toByte()
            out[2 * i + 1] = (v shr 8).toByte()
        }
        return out
    }
}

/** A state-variable band-pass filter, retunable per sample for a slide. */
private class Filter(centre: Double, private val q: Double) {
    private var f = 0.0
    private var low = 0.0
    private var band = 0.0

    init {
        tune(centre)
    }

    fun tune(centre: Double) {
        f = 2 * sin(PI * min(centre, Synth.RATE / 6.0) / Synth.RATE)
    }

    fun band(input: Double): Double {
        low += f * band
        val high = input - low - band / q
        band += f * high
        return band
    }
}
