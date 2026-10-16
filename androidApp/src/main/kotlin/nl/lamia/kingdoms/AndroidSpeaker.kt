package nl.lamia.kingdoms

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import nl.lamia.kingdoms.app.Sound
import nl.lamia.kingdoms.app.Speaker
import nl.lamia.kingdoms.app.Synth
import java.io.File

/**
 * Android sound through `SoundPool`, which wants files: the sounds are written as WAVs to the
 * cache once per launch. Game audio follows the media volume; a sound not loaded yet is skipped.
 */
class AndroidSpeaker(context: Context) : Speaker {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids: Map<Pair<Sound, Int>, Int>

    init {
        val dir = File(context.cacheDir, "sounds").apply { mkdirs() }
        ids = Sound.entries.flatMap { s -> List(s.variants) { s to it } }.associateWith { (s, v) ->
            val file = File(dir, "${s.name.lowercase()}-$v.wav")
            file.writeBytes(Synth.wav(Synth.render(s, v)))
            pool.load(file.path, 1)
        }
    }

    override fun play(sound: Sound, variant: Int, volume: Float) {
        ids[sound to variant]?.let { pool.play(it, volume, volume, 1, 0, 1f) }
    }

    fun release() = pool.release()
}
