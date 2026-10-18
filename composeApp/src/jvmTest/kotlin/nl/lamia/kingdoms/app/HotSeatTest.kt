package nl.lamia.kingdoms.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
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

/** Several people at one screen (Design §4.2, §6.2): seats, turns, and whose numbers the HUD shows. */
@OptIn(ExperimentalTestApi::class)
class HotSeatTest {
    private val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = 1))

    @Test
    fun aSeatBecomesAPersonOnTheNewGameScreen() = runUiTest {
        var state: GameState? = null
        setContent { App(Setup(), onGameChange = { state = it }) }
        // Aldmere is the only person: its AI chips are off, so the table never empties.
        onAllNodesWithText("Hard")[0].assertIsNotEnabled()
        onAllNodesWithText("Human")[1].performClick() // Brightvale
        onNodeWithText("Start").performClick()
        waitForIdle()
        val turn = onAllNodesWithText(", your turn", substring = true).fetchSemanticsNodes()
        assertEquals(1, turn.size, "the turn panel does not name whose turn it is")
        onNodeWithText("End turn").performClick()
        waitForIdle()
        val game = assertNotNull(state, "End turn did not reach the game")
        assertEquals(listOf(true, true, false, false), game.kingdoms.map { it.human })
        assertEquals(Setup().seed, game.seed)
    }

    @Test
    fun twoPeopleTakeTheirTurnsAtOneScreenThenTheAisPlay() = runUiTest {
        val start = NewGame.create(map, NewGame.seats(4, humans = setOf(0, 2)), seed = 1, turnOrder = TurnOrder.Fixed)
        var state = start
        setContent { GameScreen(start, onNewGame = {}, Modifier.fillMaxSize(), onChange = { state = it }) }
        val (first, second) = start.order.take(2)
        assertEquals(setOf(0, 2), setOf(first, second))

        for (round in 1..2) {
            waitForPerson { state }
            assertEquals(round, state.round)
            takeTurn(first) { state }
            // Straight to the second person: no AI moves in between.
            assertEquals(second, state.current, "round $round: ${state.kingdoms[state.current].name} moves after the first person")
            assertEquals(round, state.round)
            takeTurn(second) { state }
        }
        assertEquals(start.seed, state.seed)
    }

    @Test
    fun theStrengthBarsShowThePersonToMoveOrTheNextOne() {
        val game = NewGame.create(map, NewGame.seats(4, humans = setOf(0, 2)), seed = 1, turnOrder = TurnOrder.Fixed)
        val (first, second, ai) = game.order
        assertEquals(first, game.viewer())
        assertEquals(second, game.copy(current = second).viewer())
        // During the AIs' turns: the person who moves next, round the order.
        assertEquals(first, game.copy(current = ai).viewer())
        // One person: always them.
        val alone = NewGame.create(map, kingdoms = 4, seed = 1)
        assertEquals(0, alone.copy(current = alone.order.last()).viewer())
    }

    /** Lets the AIs play (virtual time) until a person is to move. */
    private fun ComposeUiTest.waitForPerson(current: () -> GameState) {
        var waited = 0
        while (!current().isHumanTurn) {
            assertTrue(!current().isOver, "the game ended")
            mainClock.advanceTimeBy(500)
            if (++waited > 2_000) fail("still ${current().kingdoms[current().current].name}'s turn")
        }
        waitForIdle()
    }

    /** [person]'s turn: the panel names them, one move by clicking lands in this game, End turn. */
    private fun ComposeUiTest.takeTurn(person: Int, current: () -> GameState) {
        val state = current()
        assertEquals(person, state.current)
        onNodeWithText("${state.kingdoms[person].name}, your turn").assertExists()
        val camera = Camera.fit(map, onRoot().fetchSemanticsNode().size)
        val source = state.provincesOf(person).first { state.movable(it) > 0 }
        val target = map.provinces[source].neighbours.first()
        onRoot().performMouseInput { click(camera.toScreen(map.provinces[source].site)) }
        mainClock.advanceTimeBy(400)
        onRoot().performMouseInput { click(camera.toScreen(map.provinces[target].site)) }
        mainClock.advanceTimeBy(400)
        waitForIdle()
        assertTrue(current().movable(source) < state.movable(source), "${state.kingdoms[person].name}: the move from #$source did not land")
        onNodeWithText("End turn").performClick()
        waitForIdle()
    }
}
