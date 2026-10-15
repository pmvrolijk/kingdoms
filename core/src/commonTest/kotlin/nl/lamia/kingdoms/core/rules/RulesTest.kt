package nl.lamia.kingdoms.core.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {
    private val game = Fixtures.game
    private val map = Fixtures.map

    // A province, a neighbour of it, and a neighbour of that neighbour other than the first.
    private val p = map.provinces.first { it.neighbours.size >= 3 }.id
    private val q = map.provinces[p].neighbours.first { q -> map.provinces[q].neighbours.size >= 2 }
    private val r = map.provinces[q].neighbours.first { it != p }
    private val far = map.provinces.indices.first { it != p && it !in map.provinces[p].neighbours }

    @Test
    fun aDefaultMoveTakesEverythingButOne() {
        val s = game.only(p to Holding(0, 6, fresh = 6), q to Holding(0, 2))
        val after = s.play(Action.Move(p, q)).state
        assertEquals(Holding(0, 1, fresh = 1), after.holdings[p])
        assertEquals(Holding(0, 7, fresh = 0), after.holdings[q])
    }

    @Test
    fun aChosenAmountMovesExactlyThat() {
        val s = game.only(p to Holding(0, 6, fresh = 6), q to Holding(0, 2))
        val after = s.play(Action.Move(p, q, armies = 3)).state
        assertEquals(3, after.holdings[p].armies)
        assertEquals(2, after.movable(p))
    }

    @Test
    fun armiesThatMovedCannotMoveAgainThisTurn() {
        val s = game.only(p to Holding(0, 6, fresh = 6), q to Holding(0, 3, fresh = 0), r to Holding(0, 1))
        val after = s.play(Action.Move(p, q)).state
        assertEquals(0, after.movable(q))
        assertEquals(Rejection.NothingToMove, after.reject(Action.Move(q, r)))
    }

    @Test
    fun freshArmiesCanStillMoveAfterOthersArrived() {
        val s = game.only(p to Holding(0, 6, fresh = 6), q to Holding(0, 3, fresh = 3), r to Holding(0, 1))
        val after = s.play(Action.Move(p, q)).state
        assertEquals(3, after.movable(q)) // 3 fresh of 8, not 7
    }

    @Test
    fun invalidMovesAreRejected() {
        val s = game.only(p to Holding(0, 6, fresh = 6), q to Holding(1, 2), far to Holding(0, 1))
        assertEquals(Rejection.NotAdjacent, s.reject(Action.Move(p, far)))
        assertEquals(Rejection.NotYourProvince, s.reject(Action.Move(q, p)))
        assertEquals(Rejection.NothingToMove, s.reject(Action.Move(far, map.provinces[far].neighbours.first())))
        assertEquals(Rejection.InvalidAmount, s.reject(Action.Move(p, q, armies = 0)))
        assertEquals(Rejection.InvalidAmount, s.reject(Action.Move(p, q, armies = 6)))
        assertEquals(Rejection.NoSuchProvince, s.reject(Action.Move(p, 10_000)))
    }

    @Test
    fun aWinningAttackTakesTheProvinceWithFatiguedSurvivors() {
        val s = game.only(p to Holding(0, 40, fresh = 40), q to Holding(null, 1), far to Holding(1, 1))
        val outcome = s.play(Action.Move(p, q))
        val fought = assertIs<Event.Fought>(outcome.events.single())
        assertTrue(fought.result.attackerWon)
        assertEquals(Holding(0, fought.result.attackerSurvivors, fresh = 0), outcome.state.holdings[q])
        assertEquals(1, outcome.state.holdings[p].armies)
        assertEquals(s.draws + 1, outcome.state.draws)
    }

    @Test
    fun aLosingAttackLosesEveryAttacker() {
        val s = game.only(p to Holding(0, 3, fresh = 3), q to Holding(1, 20), r to Holding(1, 1))
        val outcome = s.play(Action.Move(p, q))
        val fought = assertIs<Event.Fought>(outcome.events.single())
        assertTrue(!fought.result.attackerWon)
        assertEquals(1, outcome.state.holdings[p].armies)
        assertEquals(Holding(1, fought.result.defenderSurvivors), outcome.state.holdings[q])
    }

    @Test
    fun theAttackUsesTheBorderDefence() {
        val s = game.only(p to Holding(0, 12, fresh = 12), q to Holding(1, 10))
        assertEquals(Battle.winChance(11, 10, map.border(p, q)?.defence ?: 1.0), s.winChance(p, q))
    }

    @Test
    fun turnsPassInOrderAndRefreshTheNextKingdom() {
        val s = game.only(p to Holding(0, 4), q to Holding(1, 5, fresh = 0), r to Holding(2, 2))
        val after = s.play(Action.EndTurn)
        assertEquals(1, after.state.current)
        assertEquals(5, after.state.holdings[q].fresh)
        assertEquals(listOf<Event>(Event.TurnStarted(1, 1)), after.events)
    }

    @Test
    fun aRotatingOrderStartsOneSeatLaterEachRound() {
        val s = rotatingFour().copy(current = 3)
        val after = s.play(Action.EndTurn).state
        assertEquals(listOf(1, 2, 3, 0), after.order)
        assertEquals(1, after.current)
    }

    @Test
    fun aShuffledOrderIsAReproduciblePermutationEachRound() {
        val s = rotatingFour().copy(turnOrder = TurnOrder.Shuffled, current = 3)
        val after = s.play(Action.EndTurn).state
        assertEquals(listOf(0, 1, 2, 3), after.order.sorted())
        assertEquals(after.order.first(), after.current)
        assertEquals(after, s.play(Action.EndTurn).state)
        assertEquals(s.draws + 1, after.draws)
    }

    @Test
    fun withTwoKingdomsLeftAShuffledOrderAlternates() {
        var s = game.only(p to Holding(0, 2), q to Holding(2, 2)).copy(turnOrder = TurnOrder.Shuffled)
        val movers = mutableListOf(s.current)
        repeat(40) {
            s = s.play(Action.EndTurn).state
            movers += s.current
        }
        assertTrue(movers.zipWithNext().all { (a, b) -> a != b }, "a kingdom moved twice in a row: $movers")
        assertTrue(s.round > 15)
    }

    private fun rotatingFour(): GameState {
        val four = map.provinces.indices.filter { it != p && it != q }.take(2)
        return game.only(p to Holding(0, 2), q to Holding(1, 2), four[0] to Holding(2, 2), four[1] to Holding(3, 2))
            .copy(turnOrder = TurnOrder.Rotating)
    }

    @Test
    fun deadKingdomsAreSkipped() {
        val s = game.only(p to Holding(0, 4), r to Holding(2, 2))
        assertEquals(2, s.play(Action.EndTurn).state.current)
    }

    @Test
    fun ownedProvincesGrowTheirYieldAtTheEndOfTheRound() {
        val s = game.only(p to Holding(0, 4), q to Holding(3, 5)).copy(current = 3)
        val after = s.play(Action.EndTurn)
        assertEquals(4 + map.provinces[p].yield, after.state.holdings[p].armies)
        assertEquals(5 + map.provinces[q].yield, after.state.holdings[q].armies)
        assertEquals(1, after.state.holdings[r].armies, "neutral provinces do not grow")
        assertEquals(2, after.state.round)
        assertEquals(0, after.state.current)
        assertEquals(after.state.holdings[p].armies, after.state.holdings[p].fresh)
        val ended = assertIs<Event.RoundEnded>(after.events.first())
        assertEquals(mapOf(0 to map.provinces[p].yield, 3 to map.provinces[q].yield), ended.growth)
    }

    @Test
    fun takingTheLastProvinceEliminatesAndTheLastKingdomWins() {
        val s = game.only(p to Holding(0, 40, fresh = 40), q to Holding(1, 1))
        val outcome = s.play(Action.Move(p, q))
        assertEquals(listOf(Event.Eliminated(1, by = 0), Event.Won(0)), outcome.events.drop(1))
        assertEquals(0, outcome.state.winner)
        assertTrue(outcome.state.isOver)
        assertEquals(Rejection.GameOver, outcome.state.reject(Action.EndTurn))
    }

    @Test
    fun losingEveryHumanProvinceEndsTheGame() {
        val s = game.only(q to Holding(1, 5), r to Holding(2, 5))
        assertTrue(s.isOver)
        assertNull(s.winner)
    }
}
