package nl.lamia.kingdoms.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import nl.lamia.kingdoms.core.map.GameMap
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.NewGame
import nl.lamia.kingdoms.core.rules.TurnOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Plays several rounds through the real UI on a virtual clock: click a move each turn, press End
 * turn, let the paced AI run. Catches anything that leaves the player unable to act.
 */
private const val TURNS = 4

@OptIn(ExperimentalTestApi::class)
class PlayTest {
    @Test
    fun thePlayerCanMoveEveryTurn() = play(TurnOrder.Fixed)

    @Test
    fun throughTheNewGameScreenWithFixedTurns() = playApp(order = TurnOrder.Fixed, restarts = 0, playing = false) {
        onNodeWithText("Fixed").performClick()
        onNodeWithText("Start").performClick()
    }

    @Test
    fun throughTheAppAfterRestart() = playApp(order = TurnOrder.Shuffled, restarts = 1, playing = true) {
        onNodeWithText("Menu").performClick()
        onNodeWithText("Restart").performClick()
    }

    /**
     * Play two turns, close the app, open it again and Continue: the game on screen must be the
     * saved one — a new game from the same setup has the same seed and map, so check the round.
     */
    @Test
    fun continueResumesTheSavedGame() = runComposeUiTest {
        val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = 1))
        val store = MemorySaveStore()
        var launch by mutableStateOf(0)
        var state: GameState? = null
        setContent { key(launch) { App(Setup(), store = store, onGameChange = { state = it }) } }
        onNodeWithText("Start").performClick()
        waitForIdle()
        val expected = NewGame.create(map, kingdoms = 4, seed = 1, turnOrder = TurnOrder.Shuffled)
        playTurns(map, expected, turns = 2) { state ?: expected }
        val saved = assertNotNull(store.load(), "nothing saved")
        assertEquals(state, saved.state, "the save holds the latest state")
        assertTrue(saved.state.round >= 2, "round ${saved.state.round}")

        launch++
        state = null
        waitForIdle()
        onNodeWithText("Continue · round ${saved.state.round}").performClick()
        waitForIdle()
        playTurns(map, saved.state, turns = 1) { state ?: saved.state }
        val resumed = assertNotNull(state, "no move after Continue")
        assertTrue(resumed.round >= saved.state.round, "round ${resumed.round}: a new game, not the saved one")
    }

    /** The full App from a known setup, after [setup] clicks; the start mirrors what App creates. */
    private fun playApp(order: TurnOrder, restarts: Int, playing: Boolean, setup: ComposeUiTest.() -> Unit) = runComposeUiTest {
        val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = 1))
        var state = NewGame.create(map, kingdoms = 4, seed = 1L + restarts, turnOrder = order)
        setContent { App(Setup(), playing = playing, onGameChange = { state = it }) }
        setup()
        waitForIdle()
        playTurns(map, expected = NewGame.create(map, kingdoms = 4, seed = 1L + restarts, turnOrder = order)) { state }
    }

    private fun play(order: TurnOrder) = runComposeUiTest {
        val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = 1))
        val start = NewGame.create(map, kingdoms = 4, seed = 1, turnOrder = order)
        var state: GameState = start
        setContent { GameScreen(start, onNewGame = {}, Modifier.fillMaxSize(), onChange = { state = it }) }
        playTurns(map, expected = start) { state }
    }

    /**
     * Moves must land in the game on screen, [expected]: a stale handler would apply them to the
     * previous game, which has the same map and reports through the same hook — so check identity.
     */
    private fun ComposeUiTest.playTurns(map: GameMap, expected: GameState, turns: Int = TURNS, current: () -> GameState) {
        val camera = Camera.fit(map, onRoot().fetchSemanticsNode().size)

        repeat(turns) { turn ->
            var state = current()
            // Wait (virtual time) for the AI to hand the turn back.
            var waited = 0
            while (!state.isHumanTurn && !state.isOver) {
                mainClock.advanceTimeBy(500)
                state = current()
                if (++waited > 2_000) fail("turn $turn: still ${state.kingdoms[state.current].name}'s turn in round ${state.round}")
            }
            if (state.isOver) return
            waitForIdle()

            val source = state.provincesOf(0).firstOrNull { state.movable(it) > 0 }
            if (source != null) {
                val target = map.provinces[source].neighbours.first()
                val before = state.movable(source)
                onRoot().performMouseInput { click(camera.toScreen(map.provinces[source].site)) }
                mainClock.advanceTimeBy(400)
                onRoot().performMouseInput { click(camera.toScreen(map.provinces[target].site)) }
                mainClock.advanceTimeBy(400)
                waitForIdle()
                state = current()
                assertEquals(expected.seed to expected.turnOrder, state.seed to state.turnOrder, "the move landed in another game")
                assertTrue(
                    state.movable(source) < before,
                    "turn $turn, round ${state.round}: clicking #$source then #$target did not move (movable $before)",
                )
            }
            onNodeWithText("End turn").performClick()
            waitForIdle()
        }
    }
}
