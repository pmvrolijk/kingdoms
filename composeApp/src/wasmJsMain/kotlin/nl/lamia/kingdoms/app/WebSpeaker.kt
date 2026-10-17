package nl.lamia.kingdoms.app

/**
 * Browser sound through the Web Audio API: one buffer per sound variant, played through a gain node
 * so repeats overlap. Browsers only allow audio after the player's first tap or key, so the
 * `AudioContext` is made on the first sound (always a click), and each sound is rendered the first
 * time it plays. Without Web Audio it goes quiet after one message.
 */
class WebSpeaker : Speaker {
    private var context: JsAny? = null
    private val buffers = HashMap<Pair<Sound, Int>, JsAny>()
    private var broken = false

    override fun play(sound: Sound, variant: Int, volume: Float) {
        if (broken) return
        try {
            val ctx = context ?: newContext().also { context = it }
            resume(ctx)
            val buffer = buffers.getOrPut(sound to variant) { toBuffer(ctx, Synth.render(sound, variant)) }
            start(ctx, buffer, volume.toDouble())
        } catch (e: Throwable) {
            broken = true
            println("Kingdoms: no sound: ${e.message}")
        }
    }

    private fun toBuffer(ctx: JsAny, samples: FloatArray): JsAny {
        val buffer = newBuffer(ctx, samples.size, Synth.RATE)
        val data = channel(buffer)
        for (i in samples.indices) setSample(data, i, samples[i].toDouble())
        return buffer
    }
}

private fun newContext(): JsAny = js("new (window.AudioContext || window.webkitAudioContext)()")

private fun resume(ctx: JsAny): Unit = js("{ if (ctx.state === 'suspended') ctx.resume(); }")

private fun newBuffer(ctx: JsAny, length: Int, rate: Int): JsAny = js("ctx.createBuffer(1, length, rate)")

private fun channel(buffer: JsAny): JsAny = js("buffer.getChannelData(0)")

private fun setSample(data: JsAny, i: Int, value: Double): Unit = js("{ data[i] = value; }")

private fun start(ctx: JsAny, buffer: JsAny, volume: Double): Unit = js(
    """{
        const source = ctx.createBufferSource();
        source.buffer = buffer;
        const gain = ctx.createGain();
        gain.gain.value = volume;
        source.connect(gain).connect(ctx.destination);
        source.start();
    }""",
)
