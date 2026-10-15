package nl.lamia.kingdoms.core.rules

import nl.lamia.kingdoms.core.ai.Ai
import nl.lamia.kingdoms.core.ai.PassiveAi
import nl.lamia.kingdoms.core.ai.playUntilHumanTurn
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Whole games through the public rules, checking invariants after every action (Status M2). */
class FullGameTest {
    /** Attacks only near-certain wins; otherwise moves interior armies one step towards the front. */
    private val greedy = Ai { s ->
        val me = s.current
        val mine = s.provincesOf(me)
        val provinces = s.map.provinces
        for (p in mine) {
            if (s.movable(p) == 0) continue
            val target = provinces[p].neighbours.filter { s.owner(it) != me }.maxByOrNull { s.winChance(p, it) }
            if (target != null && s.winChance(p, target) >= 0.75) return@Ai Action.Move(p, target)
        }
        val toFront = HashMap<Int, Int>()
        val queue = ArrayDeque(mine.filter { p -> provinces[p].neighbours.any { s.owner(it) != me } })
        queue.forEach { toFront[it] = 0 }
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            for (n in provinces[p].neighbours) if (s.owner(n) == me && n !in toFront) {
                toFront[n] = toFront.getValue(p) + 1
                queue += n
            }
        }
        for (p in mine) {
            val d = toFront[p] ?: continue
            if (d > 0 && s.movable(p) > 0) {
                return@Ai Action.Move(p, provinces[p].neighbours.first { s.owner(it) == me && toFront[it] == d - 1 })
            }
        }
        Action.EndTurn
    }

    @Test
    fun aGreedyPlayerBeatsPassiveKingdomsOnEveryMapSize() {
        for (size in MapSize.entries) for (seed in 1L..5L) {
            val map = MapGenerator.generate(MapConfig.of(size, seed))
            var s = NewGame.create(map, kingdoms = 4, seed = seed)
            while (!s.isOver && s.round <= 300) {
                s = if (s.isHumanTurn) {
                    val outcome = Rules.apply(s, greedy.decide(s))
                    check(outcome is Outcome.Applied) { "greedy move rejected: $outcome" }
                    outcome.state
                } else {
                    playUntilHumanTurn(s, PassiveAi).state
                }
                checkInvariants(s)
            }
            assertEquals(0, s.winner, "$size seed $seed after ${s.round} rounds")
        }
    }

    private fun checkInvariants(s: GameState) {
        s.holdings.forEachIndexed { p, h ->
            assertTrue(h.armies >= 1, "province $p has ${h.armies} armies")
            assertTrue(h.fresh in 0..h.armies, "province $p fresh ${h.fresh} of ${h.armies}")
            assertTrue(h.owner == null || h.owner in s.kingdoms.indices)
        }
        assertTrue(s.isOver || s.isAlive(s.current), "the current kingdom is dead")
    }
}
