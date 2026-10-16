package nl.lamia.kingdoms.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.rules.Action
import nl.lamia.kingdoms.core.rules.Event
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.NewGame
import nl.lamia.kingdoms.core.rules.Outcome
import nl.lamia.kingdoms.core.rules.Rules
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

@OptIn(ExperimentalTestApi::class)
class SoundTest {
    /** Every sound is clean: right length, its own peak, silent at both ends, the same every time. */
    @Test
    fun everySoundIsMadeTheSameWayEveryTime() {
        val seconds = mapOf(Sound.Click to 0.03..0.1, Sound.Clash to 0.4..1.0, Sound.March to 0.7..1.1, Sound.Victory to 1.0..1.6)
        for (sound in Sound.entries) {
            val variants = List(sound.variants) { Synth.render(sound, it) }
            for ((v, samples) in variants.withIndex()) {
                val name = "$sound/$v"
                assertTrue(samples.size.toDouble() / Synth.RATE in seconds.getValue(sound), "$name: ${samples.size} samples")
                assertTrue(samples.none { it.isNaN() }, "$name: NaN")
                val peak = samples.maxOf { abs(it) }
                assertTrue(peak in 0.2f..0.75f, "$name: peak $peak")
                assertTrue(abs(samples.first()) < 0.01f && abs(samples.last()) < 0.01f, "$name: clicks at the ends")
                assertContentEquals(samples, Synth.render(sound, v), "$name: not deterministic")
                // Listen: build/sounds/*.wav
                File("build/sounds/${sound.name.lowercase()}-$v.wav").apply { parentFile.mkdirs() }.writeBytes(Synth.wav(samples))
            }
            if (variants.size > 1) assertFalse(variants[0].contentEquals(variants[1]), "$sound: variants are the same")
        }
    }

    /** A march waits until the last one has finished; other sounds only skip an echo within 60 ms. */
    @Test
    fun aMarchDoesNotPlayOverAnother() {
        val played = mutableListOf<Sound>()
        val time = TestTimeSource()
        val sounds = Sounds(Speaker { sound, _, _ -> played += sound }, time = time)
        fun after(millis: Long, sound: Sound) {
            time += millis.milliseconds
            sounds.play(sound)
        }
        after(0, Sound.March)
        after(300, Sound.March) // skipped: still marching
        after(0, Sound.Clash) // other sounds still play
        after(100, Sound.Clash)
        after(30, Sound.Clash) // skipped: an echo
        after((Sound.March.seconds * 1000).toLong() - 400, Sound.March) // 0.98 s after the first: done
        after(1000, Sound.March)
        assertEquals(listOf(Sound.March, Sound.Clash, Sound.Clash, Sound.March, Sound.March), played)
    }

    @Test
    fun aWavFileHasTheRightHeader() {
        val samples = Synth.render(Sound.Click)
        val wav = Synth.wav(samples)
        assertEquals(44 + 2 * samples.size, wav.size)
        assertEquals("RIFF", wav.decodeToString(0, 4))
        assertEquals("WAVE", wav.decodeToString(8, 12))
        assertEquals("data", wav.decodeToString(36, 40))
        val rate = (0 until 4).sumOf { (wav[24 + it].toInt() and 0xff) shl (8 * it) }
        assertEquals(Synth.RATE, rate)
    }

    /** A move marches, an attack clashes, and only a person's win gets the fanfare. */
    @Test
    fun cuesFollowTheEvents() {
        val game = NewGame.create(MapGenerator.generate(MapConfig.of(MapSize.Small, seed = 3)), kingdoms = 4, seed = 3)
        val me = game.current
        assertTrue(game.kingdoms[me].human)
        fun move(own: Boolean): Pair<GameState, List<Event>> {
            val (from, to) = game.provincesOf(me).filter { game.movable(it) > 0 }.firstNotNullOf { p ->
                game.map.provinces[p].neighbours.firstOrNull { (game.owner(it) == me) == own }?.let { p to it }
            }
            val applied = Rules.apply(game, Action.Move(from, to)) as Outcome.Applied
            return applied.state to applied.events
        }
        val (walked, walking) = move(own = true)
        assertEquals(listOf(Sound.March), cues(walked, walking))
        val (fought, fighting) = move(own = false)
        assertEquals(listOf(Sound.Clash), cues(fought, fighting))
        val ai = game.kingdoms.first { !it.human }.id
        assertEquals(listOf(Sound.Victory), cues(game, listOf(Event.Won(me))))
        assertEquals(listOf(), cues(game, listOf(Event.Won(ai))))
        assertEquals(listOf(), cues(game, listOf(Event.TurnStarted(me, 2))))
    }

    /**
     * Through the app: buttons click, the AI's moves sound softer, the menu sets the volume and
     * mutes, muted means silent, and the setting is there after a relaunch.
     */
    @Test
    fun theMenuSetsVolumeAndMuteAndTheyAreKept() = runComposeUiTest {
        val played = mutableListOf<Pair<Sound, Float>>()
        val speaker = Speaker { sound, _, volume -> played += sound to volume }
        val settings = MemorySaveStore()
        var launch by mutableStateOf(0)
        mainClock.autoAdvance = false
        setContent { key(launch) { App(Setup(), playing = launch == 0, speaker = speaker, settings = settings) } }
        mainClock.advanceTimeBy(500)

        val level = 0.6f * 0.6f // the default, 6 of 10
        onNodeWithText("End turn").performClick()
        mainClock.advanceTimeBy(100)
        assertEquals(Sound.Click to level, played.first())
        repeat(20) { mainClock.advanceTimeBy(500) }
        val ai = played.filter { it.first == Sound.March || it.first == Sound.Clash }
        assertTrue(ai.isNotEmpty(), "the AI's moves made no sound: $played")
        assertTrue(ai.all { abs(it.second - level * AI_LOUDNESS) < 1e-4 }, "AI loudness: $ai")

        onNodeWithText("Menu").performClick()
        mainClock.advanceTimeBy(100)
        File("build/snapshots/menu.png").writeBytes(
            Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes,
        )
        onNodeWithTag("volume").performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        mainClock.advanceTimeBy(100)
        assertEquals(SoundSettings(volume = 10), settings.loadSettings())
        onNodeWithText("Sound on").performClick()
        mainClock.advanceTimeBy(100)
        assertEquals(SoundSettings(volume = 10, muted = true), settings.loadSettings())

        val before = played.size
        onNodeWithText("Resume").performClick()
        repeat(20) { mainClock.advanceTimeBy(500) }
        assertEquals(before, played.size, "muted, yet played ${played.drop(before)}")

        launch++
        mainClock.advanceTimeBy(500)
        onNodeWithText("Sound off").performClick() // the new-game screen's chip
        mainClock.advanceTimeBy(100)
        assertEquals(SoundSettings(volume = 10, muted = false), settings.loadSettings())
        assertEquals(Sound.Click to 1f, played.last(), "turning sound on clicks at the kept volume")
    }
}
