package nl.lamia.kingdoms.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.NewGame
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.captureToImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/** Choosing how many armies to send: select the source, then long-press the target. */
@OptIn(ExperimentalTestApi::class)
class PickerTest {
    @Test
    fun longPressWithTheMouseOpensThePicker() = runUiTest {
        val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = 1))
        val start = NewGame.create(map, kingdoms = 4, seed = 1)
        var state: GameState = start
        setContent { GameScreen(start, onNewGame = {}, Modifier.fillMaxSize(), onChange = { state = it }) }
        val camera = Camera.fit(map, onRoot().fetchSemanticsNode().size)
        val source = start.provincesOf(0).maxBy { start.movable(it) }
        val target = map.provinces[source].neighbours.first()
        val movable = start.movable(source)

        onRoot().performMouseInput { click(camera.toScreen(map.provinces[source].site)) }
        mainClock.advanceTimeBy(400)
        onRoot().performMouseInput { longClick(camera.toScreen(map.provinces[target].site)) }
        waitForIdle()
        onNodeWithTag("amount").performSemanticsAction(SemanticsActions.SetProgress) { it(movable - 1f) }
        onNodeWithTag("confirm-move").performClick()
        waitForIdle()
        assertEquals(1, state.movable(source), "picked ${movable - 1} of $movable, so one movable army stays")
    }

    @Test
    fun rightClickOpensThePickerToo() = runUiTest {
        val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = 1))
        val start = NewGame.create(map, kingdoms = 4, seed = 1)
        var state: GameState = start
        setContent { GameScreen(start, onNewGame = {}, Modifier.fillMaxSize(), onChange = { state = it }) }
        val camera = Camera.fit(map, onRoot().fetchSemanticsNode().size)
        val source = start.provincesOf(0).maxBy { start.movable(it) }
        val target = map.provinces[source].neighbours.first()
        val movable = start.movable(source)

        onRoot().performMouseInput { click(camera.toScreen(map.provinces[source].site)) }
        mainClock.advanceTimeBy(400)
        onRoot().performMouseInput { rightClick(camera.toScreen(map.provinces[target].site)) }
        waitForIdle()
        val shot = File("build/snapshots/picker.png").apply { parentFile.mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", shot)
        onNodeWithTag("amount").performSemanticsAction(SemanticsActions.SetProgress) { it(movable - 1f) }
        onNodeWithTag("confirm-move").performClick()
        waitForIdle()
        assertEquals(1, state.movable(source))
        assertEquals(start.seed, state.seed)
    }
}
