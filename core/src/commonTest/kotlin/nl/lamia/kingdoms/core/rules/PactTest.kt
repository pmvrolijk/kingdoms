package nl.lamia.kingdoms.core.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pacts against a runaway leader (Design §4.7). Fixed order: kingdom 3 ends the round. */
class PactTest {
    private val game = Fixtures.game
    private val map = Fixtures.map

    // One province per kingdom; the leader's attacker [p] borders a member's [q]. Attacks on [q]
    // below are against 30 armies and fail, so nobody falls and the pact is judged on membership alone.
    private val p = map.provinces.first { it.neighbours.size >= 3 }.id
    private val q = map.provinces[p].neighbours[0]
    private val provinces = (listOf(p, q) + map.provinces.indices.filter { it != p && it != q }).take(4)

    /** Kingdom k holds provinces[k] with armies[k]; kingdom 3 is about to end the round. */
    private fun armies(vararg armies: Int, pact: Pact? = null): GameState =
        game.only(*armies.indices.map { k -> provinces[k] to Holding(k, armies[k], fresh = armies[k]) }.toTypedArray())
            .copy(current = 3, pact = pact)

    private fun endRound(state: GameState) = state.play(Action.EndTurn)

    @Test
    fun aPactFormsAgainstALeaderAtTheEndOfTheRound() {
        val after = endRound(armies(40, 10, 10, 10))
        assertEquals(Pact(0, listOf(1, 2, 3)), after.state.pact)
        assertTrue(Event.PactFormed(0, listOf(1, 2, 3)) in after.events)
    }

    @Test
    fun noPactBelowTheThreshold() {
        assertNull(endRound(armies(12, 10, 10, 10)).state.pact)
    }

    @Test
    fun easyAisStayOutOfPacts() {
        val s = armies(40, 10, 10, 10).let { s -> s.copy(kingdoms = s.kingdoms.map { if (it.id == 2) it.copy(difficulty = Difficulty.Easy) else it }) }
        assertEquals(Pact(0, listOf(1, 3)), endRound(s).state.pact)
    }

    @Test
    fun aPactNeedsTwoMembersAndThreeKingdoms() {
        // Only kingdoms 0 and 1 left.
        val two = game.only(provinces[0] to Holding(0, 40), provinces[1] to Holding(1, 5)).copy(current = 1)
        assertNull(two.play(Action.EndTurn).state.pact)
        // Three left, but one of the two others is Easy.
        val three = game.only(provinces[0] to Holding(0, 40), provinces[1] to Holding(1, 5), provinces[3] to Holding(3, 5))
            .let { s -> s.copy(current = 3, kingdoms = s.kingdoms.map { if (it.id == 3) it.copy(difficulty = Difficulty.Easy) else it }) }
        assertNull(three.play(Action.EndTurn).state.pact)
    }

    @Test
    fun aPactHoldsUntilTheLeaderFallsBelowTheLowerThreshold() {
        val pact = Pact(0, listOf(1, 2, 3))
        // About 31%: under the forming threshold but above the ending one, so it holds.
        assertEquals(pact, endRound(armies(20, 15, 15, 15, pact = pact)).state.pact)
        val ended = endRound(armies(10, 15, 15, 15, pact = pact))
        assertNull(ended.state.pact)
        assertTrue(Event.PactEnded(0) in ended.events)
    }

    @Test
    fun aMemberWhoAttacksAFellowMemberLeavesThePact() {
        val s = armies(10, 30, 30, 10, pact = Pact(2, listOf(0, 1, 3))).copy(current = 0)
        val after = s.play(Action.Move(p, q))
        assertEquals(listOf(1, 3), after.state.pact?.members)
        assertTrue(Event.PactLeft(0, 2) in after.events)
        assertEquals(mapOf(0 to s.round), after.state.pactBreakers)
    }

    @Test
    fun aPactLeftWithOneMemberEndsAtOnce() {
        val s = armies(10, 30, 30, 10, pact = Pact(2, listOf(0, 1))).copy(current = 0)
        val after = s.play(Action.Move(p, q))
        assertNull(after.state.pact)
        assertEquals(listOf(Event.PactLeft(0, 2), Event.PactEnded(2)), after.events.filter { it is Event.PactLeft || it is Event.PactEnded })
    }

    @Test
    fun aBreakerSitsOutNewPactsForThreeRounds() {
        val s = armies(10, 40, 10, 10)
        // Broke in this round or the two before: left out. Three rounds ago: back in.
        for (ago in 0..2) assertEquals(Pact(1, listOf(2, 3)), endRound(s.copy(pactBreakers = mapOf(0 to s.round - ago))).state.pact, "$ago")
        assertEquals(Pact(1, listOf(0, 2, 3)), endRound(s.copy(pactBreakers = mapOf(0 to s.round - 3))).state.pact)
        // With only one trusted kingdom left, no pact forms.
        assertNull(endRound(s.copy(pactBreakers = mapOf(0 to s.round, 2 to s.round))).state.pact)
    }

    @Test
    fun attackingTheLeaderKeepsTheMembership() {
        val s = armies(10, 30, 30, 10, pact = Pact(1, listOf(0, 2, 3))).copy(current = 0)
        val after = s.play(Action.Move(p, q))
        assertEquals(listOf(0, 2, 3), after.state.pact?.members)
    }

    @Test
    fun thePactIsSaved() {
        val s = armies(40, 10, 10, 10, pact = Pact(0, listOf(1, 2, 3))).copy(pactBreakers = mapOf(2 to 4))
        val decoded = Save.decode(Save.encode(s))
        assertEquals(s.pact, decoded.pact)
        assertEquals(s.pactBreakers, decoded.pactBreakers)
    }

    @Test
    fun withoutPactsNoneForms() {
        val s = armies(40, 10, 10, 10).copy(options = RuleOptions(pacts = false))
        assertNull(endRound(s).state.pact)
    }
}
