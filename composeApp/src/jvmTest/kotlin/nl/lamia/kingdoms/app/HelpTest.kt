package nl.lamia.kingdoms.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import nl.lamia.kingdoms.core.rules.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Renders every help tab at each beat of its scene, on the smallest phone: look at the PNGs. */
@OptIn(ExperimentalTestApi::class)
class HelpTest {
    @Test
    fun opensFromTheNewGameScreenAndCloses() = runUiTest {
        // The scenes animate forever, so the clock never idles by itself.
        mainClock.autoAdvance = false
        setContent { App(Setup()) }
        mainClock.advanceTimeBy(100)
        onNodeWithText("How to play").performClick()
        mainClock.advanceTimeBy(100)
        onNodeWithText("Strength").performClick()
        mainClock.advanceTimeBy(100)
        onNodeWithText("7 against 6", substring = true).assertExists()
        onNodeWithText("Close").performClick()
        mainClock.advanceTimeBy(100)
        onAllNodesWithText("Strength").assertCountEquals(0)
        onNodeWithText("Start").assertExists()
    }

    /** With help open from the in-game menu, the AI waits; closing both lets it play on. */
    @Test
    fun theAiWaitsWhileHelpIsOpen() = runUiTest {
        var state: GameState? = null
        mainClock.autoAdvance = false
        setContent { App(Setup(), playing = true, onGameChange = { state = it }) }
        mainClock.advanceTimeBy(500)
        onNodeWithText("End turn").performClick()
        mainClock.advanceTimeBy(100)
        val waiting = assertNotNull(state)
        assertTrue(!waiting.isHumanTurn, "an AI is to move")
        onNodeWithText("Menu").performClick()
        mainClock.advanceTimeBy(100)
        onNodeWithText("How to play").performClick()
        repeat(20) { mainClock.advanceTimeBy(500) }
        assertSame(waiting, state, "the AI moved while help was open")
        onNodeWithText("Close").performClick()
        mainClock.advanceTimeBy(100)
        onNodeWithText("Resume").performClick()
        repeat(20) { mainClock.advanceTimeBy(500) }
        assertTrue(state !== waiting, "the AI did not play on after closing")
        assertEquals(waiting.seed, state!!.seed, "same game")
    }

    @Test
    fun everySceneRendersOnThePhone() {
        for (topic in HelpTopic.entries) {
            val moments = topic.scene()?.let { scene -> scene.beats.map { it.at + 0.35f } + scene.fights.map { it.at + 0.4f } }?.sorted() ?: listOf(0f)
            // Huawei P30: 780 × 360 dp.
            ImageComposeScene(2340, 1080, density = Density(3f)) { HelpScreen({}, Modifier.fillMaxSize(), initial = topic) }.use { scene ->
                scene.render(0)
                moments.forEachIndexed { i, s ->
                    val file = File("build/snapshots/help/${topic.name.lowercase()}-$i.png").apply { parentFile.mkdirs() }
                    file.writeBytes(scene.render((s * 1e9).toLong()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
            }
        }
    }

    @Test
    fun desktopWindow() {
        ImageComposeScene(1280, 800) { HelpScreen({}, Modifier.fillMaxSize(), initial = HelpTopic.Borders) }.use { scene ->
            scene.render(0)
            File("build/snapshots/help/desktop-borders.png").writeBytes(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
