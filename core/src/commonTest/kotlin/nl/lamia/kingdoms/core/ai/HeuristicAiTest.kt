package nl.lamia.kingdoms.core.ai

import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.rules.Action
import nl.lamia.kingdoms.core.rules.Difficulty
import nl.lamia.kingdoms.core.rules.Fixtures
import nl.lamia.kingdoms.core.rules.Holding
import nl.lamia.kingdoms.core.rules.Pact
import nl.lamia.kingdoms.core.rules.TurnOrder
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.with
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HeuristicAiTest {
    private val ai = HeuristicAi({ Personality.Balanced })
    private val map = Fixtures.map
    private val game = Fixtures.game

    @Test
    fun aiGamesEndWithoutStalemateOrRejectedActions() {
        for (order in listOf(TurnOrder.Fixed, TurnOrder.Shuffled)) for (size in MapSize.entries) for (seed in 1L..6L) {
            val result = Simulation.run(MapGenerator.generate(MapConfig.of(size, seed)), 4, seed, HeuristicAi(), order)
            assertTrue(!result.stalled, "$order $size seed $seed stalled")
            assertEquals(0, result.rejections, "$order $size seed $seed")
            assertNotNull(result.winner)
            assertEquals(3, result.fallen.size)
        }
    }

    @Test
    fun simulationIsReproducible() {
        val map = MapGenerator.generate(MapConfig.of(MapSize.Small, 5))
        assertEquals(Simulation.run(map, 4, 5, HeuristicAi()), Simulation.run(map, 4, 5, HeuristicAi()))
    }

    // A province with at least two neighbours: the attacker's home.
    private val home = map.provinces.first { it.neighbours.size >= 3 }.id
    private val target = map.provinces[home].neighbours[0]
    private val other = map.provinces[home].neighbours[1]

    /** Every province ours with one (unmovable) army, then [changes]: no distracting neutral targets. */
    private fun mine(vararg changes: Pair<Int, Holding>): GameState =
        game.copy(holdings = game.holdings.map { Holding(0, 1) }).with(*changes)

    @Test
    fun attacksAWinnableNeighbour() {
        val s = mine(home to Holding(0, 20, fresh = 20), target to Holding(1, 2))
        val move = assertIs<Action.Move>(ai.decide(s))
        assertEquals(home to target, move.from to move.to)
        assertTrue(s.winChance(home, target, move.armies!!) >= Personality.Balanced.confidence)
    }

    @Test
    fun holdsBackBelowItsBoldness() {
        val s = mine(home to Holding(0, 10, fresh = 10), target to Holding(1, 9))
        assertTrue(s.winChance(home, target) < Personality.Balanced.boldness)
        assertEquals(Action.EndTurn, ai.decide(s))
    }

    @Test
    fun anEasyAiHoldsBackWhereANormalOneAttacks() {
        // Between 70% and 90%: Normal (boldness 0.70) attacks, Easy (needs 0.90) waits.
        val defence = map.border(home, target)?.defence ?: 1.0
        val attackers = (1..300).first { nl.lamia.kingdoms.core.rules.Battle.winChance(it, 30, defence) in 0.75..0.88 }
        val s = mine(home to Holding(0, attackers + 1, fresh = attackers + 1), target to Holding(1, 30))
        val chance = s.winChance(home, target)
        assertTrue(chance in 0.7..0.9, "chance $chance")
        assertIs<Action.Move>(ai.decide(s))
        val easy = s.copy(kingdoms = s.kingdoms.map { if (it.id == 0) it.copy(difficulty = Difficulty.Easy) else it })
        assertEquals(Action.EndTurn, ai.decide(easy))
    }

    @Test
    fun keepsAGuardAgainstTheOtherEnemyNextDoor() {
        val s = mine(home to Holding(0, 30, fresh = 30), target to Holding(1, 2), other to Holding(1, 10))
        val move = assertIs<Action.Move>(ai.decide(s))
        assertEquals(target, move.to)
        assertEquals(29 - 10, move.armies)
    }

    @Test
    fun interiorArmiesMarchTowardsTheFront() {
        // home and all its neighbours are ours; one province beyond a neighbour is an enemy.
        val ring = map.provinces[home].neighbours
        val beyond = ring.flatMap { map.provinces[it].neighbours }.first { it != home && it !in ring }
        val changes = (ring.map { it to Holding(0, 1) } + listOf(home to Holding(0, 9, fresh = 9), beyond to Holding(1, 50)))
        val s = mine(*changes.toTypedArray())
        val move = assertIs<Action.Move>(ai.decide(s))
        assertEquals(home, move.from)
        assertTrue(move.to in ring)
        val toBeyond = s.map.provinces.indices.associateWith { p -> if (p == beyond) 0 else if (beyond in map.provinces[p].neighbours) 1 else 2 }
        assertEquals(1, toBeyond.getValue(move.to), "moved to ${move.to}, not next to the front")
    }

    @Test
    fun aPactMemberLeavesFellowMembersAloneAndGoesForTheLeader() {
        val s = mine(home to Holding(0, 20, fresh = 20), target to Holding(1, 2), other to Holding(2, 5))
        // Without a pact the weak neighbour is the better target.
        assertEquals(target, assertIs<Action.Move>(ai.decide(s)).to)
        val allied = s.copy(pact = Pact(against = 2, members = listOf(0, 1)))
        assertEquals(other, assertIs<Action.Move>(ai.decide(allied)).to)
    }

    @Test
    fun theLeaderItselfStillAttacksMembers() {
        val s = mine(home to Holding(0, 20, fresh = 20), target to Holding(1, 2), other to Holding(2, 5))
            .copy(pact = Pact(against = 0, members = listOf(1, 2)))
        assertEquals(target, assertIs<Action.Move>(ai.decide(s)).to)
    }
}
