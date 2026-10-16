package nl.lamia.kingdoms.app

import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

/**
 * Desktop sound through `javax.sound`: a fresh clip per sound, opened on a background thread so the
 * UI never waits for the audio device. Without a usable device it goes quiet after one message.
 */
class JvmSpeaker : Speaker {
    private val format = AudioFormat(Synth.RATE.toFloat(), 16, 1, true, false)
    private val samples = Sound.entries.associateWith { s -> List(s.variants) { Synth.render(s, it) } }
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "kingdoms-sound").apply { isDaemon = true } }

    @Volatile
    private var broken = false

    override fun play(sound: Sound, variant: Int, volume: Float) {
        if (broken) return
        worker.execute {
            try {
                val data = Synth.pcm(samples.getValue(sound)[variant], volume)
                val clip = AudioSystem.getClip()
                clip.open(format, data, 0, data.size)
                // Close on the worker, not in the listener: closing from the line's own event thread can hang.
                clip.addLineListener { if (it.type == LineEvent.Type.STOP) worker.execute { clip.close() } }
                clip.start()
            } catch (e: Exception) {
                broken = true
                println("Kingdoms: no sound: ${e.message}")
            }
        }
    }
}
