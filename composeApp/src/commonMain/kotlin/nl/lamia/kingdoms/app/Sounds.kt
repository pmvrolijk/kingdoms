package nl.lamia.kingdoms.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import nl.lamia.kingdoms.core.rules.Event
import nl.lamia.kingdoms.core.rules.GameState
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Plays a sound on this platform; [volume] is the amplitude, 0..1. Must not block or throw. */
fun interface Speaker {
    fun play(sound: Sound, variant: Int, volume: Float)

    companion object {
        /** Silent: tests, previews, and platforms without audio. */
        val None = Speaker { _, _, _ -> }
    }
}

/** The player's sound settings, kept between launches (Design §6.4). The slider runs 0..10. */
@Serializable
data class SoundSettings(val volume: Int = 6, val muted: Boolean = false) {
    companion object {
        const val STEPS = 10
    }
}

/**
 * The app's sounds: what is playing goes through here, so mute and volume apply everywhere. The
 * same sound is not repeated within [GAP_MILLIS], so a burst of moves makes one sound, not a roar;
 * a sound that does not overlap waits until it has finished.
 */
class Sounds(
    private val speaker: Speaker = Speaker.None,
    settings: SoundSettings = SoundSettings(),
    private val onChange: (SoundSettings) -> Unit = {},
    private val time: TimeSource = TimeSource.Monotonic,
) {
    var settings by mutableStateOf(settings)
        private set

    private val last = HashMap<Sound, TimeMark>()
    private val turns = HashMap<Sound, Int>()

    fun change(next: SoundSettings) {
        settings = next
        onChange(next)
    }

    /** Plays [sound] at [loudness] of the set volume; variants take turns. */
    fun play(sound: Sound, loudness: Float = 1f) {
        if (settings.muted || settings.volume <= 0) return
        val previous = last[sound]
        val gap = if (sound.overlaps) GAP_MILLIS else (sound.seconds * 1000).toLong()
        if (previous != null && previous.elapsedNow().inWholeMilliseconds < gap) return
        last[sound] = time.markNow()
        val turn = turns.getOrElse(sound) { 0 }
        turns[sound] = turn + 1
        // Loudness grows with the square of amplitude's fraction, so the slider's steps sound even.
        val level = settings.volume.toFloat() / SoundSettings.STEPS
        speaker.play(sound, turn % sound.variants, (level * level * loudness).coerceIn(0f, 1f))
    }

    companion object {
        const val GAP_MILLIS = 60L
    }
}

/** The sounds for the composition: Chip and ActionButton click through it. */
val LocalSounds = staticCompositionLocalOf { Sounds() }

/** A move's own sounds stay full; the AI's are softer, so its turn stays in the background. */
const val AI_LOUDNESS = 0.6f

/**
 * What to play after an action: a clash for a battle, else a march for a move, and the fanfare when
 * a person has won. One sound of each kind, however many events (Skip plays many at once).
 */
fun cues(state: GameState, events: List<Event>): List<Sound> = buildList {
    when {
        events.any { it is Event.Fought } -> add(Sound.Clash)
        events.any { it is Event.Moved } -> add(Sound.March)
    }
    val won = events.filterIsInstance<Event.Won>().firstOrNull()
    if (won != null && state.kingdoms[won.kingdom].human) add(Sound.Victory)
}

/** Sound on/off and a volume slider; moving it plays a click at the new level. */
@Composable
fun SoundControl(sounds: Sounds, modifier: Modifier = Modifier) {
    val settings = sounds.settings
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(if (settings.muted) "Sound off" else "Sound on", active = !settings.muted, small = true) {
            sounds.change(settings.copy(muted = !settings.muted))
        }
        AmountSlider(
            value = settings.volume.coerceAtLeast(1),
            max = SoundSettings.STEPS,
            colour = if (settings.muted) Palette.Neutral else Palette.Ink.lighter(0.3f),
            onChange = { v ->
                val now = sounds.settings
                if (v != now.volume || now.muted) {
                    sounds.change(now.copy(volume = v, muted = false))
                    sounds.play(Sound.Click)
                }
            },
            modifier = Modifier.weight(1f),
            tag = "volume",
        )
    }
}
