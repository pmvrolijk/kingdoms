package nl.lamia.kingdoms.core.ai

import nl.lamia.kingdoms.core.rules.Action
import nl.lamia.kingdoms.core.rules.GameState

/** How an AI kingdom plays (Design §5). */
data class Personality(
    val name: String,
    /** Attack only at or above this win chance. */
    val boldness: Double,
    /** Commit just enough armies to reach this win chance (or all, if it can't be reached); the rest stays. */
    val confidence: Double,
    /** Extra value of a neutral province over a kingdom's: expanding into empty land vs fighting. */
    val neutralBias: Double,
) {
    companion object {
        val Balanced = Personality("balanced", boldness = 0.70, confidence = 0.95, neutralBias = 1.0)
        val Aggressive = Personality("aggressive", boldness = 0.55, confidence = 0.85, neutralBias = 0.0)
        val Cautious = Personality("cautious", boldness = 0.85, confidence = 0.99, neutralBias = 2.0)

        /** Seat k (k ≥ 1) plays `cycle[(k - 1) % size]`. */
        val cycle = listOf(Balanced, Aggressive, Cautious)

        fun forSeat(kingdom: Int) = cycle[(kingdom - 1).mod(cycle.size)]
    }
}

/**
 * Attack the best target it can win; otherwise move interior armies one step towards the front;
 * otherwise end the turn. Deterministic: the same state gives the same action.
 */
class HeuristicAi(private val personality: (kingdom: Int) -> Personality = Personality::forSeat) : Ai {
    override fun decide(state: GameState): Action = attack(state) ?: logistics(state) ?: Action.EndTurn

    private fun attack(state: GameState): Action? {
        val me = state.current
        val p = personality(me)
        var best: Action.Move? = null
        var bestScore = 0.0
        for (from in state.provincesOf(me)) {
            val movable = state.movable(from)
            if (movable == 0) continue
            for (to in state.map.provinces[from].neighbours) {
                if (state.owner(to) == me) continue
                val chance = state.winChance(from, to, movable)
                if (chance < p.boldness) continue
                val score = chance * value(state, to, p) / (1 + 0.1 * state.holdings[to].armies)
                if (score > bestScore) {
                    bestScore = score
                    best = Action.Move(from, to, commit(state, from, to, movable, p.confidence))
                }
            }
        }
        return best
    }

    /** What taking [province] is worth: its yield, more for a capital or a rival about to fall. */
    private fun value(state: GameState, province: Int, p: Personality): Double {
        var value = state.map.provinces[province].yield.toDouble()
        val owner = state.owner(province)
        if (owner == null) {
            value += p.neutralBias
        } else {
            if (state.kingdoms[owner].capital == province) value += 2
            if (state.provincesOf(owner).size <= 2) value += 3
        }
        return value
    }

    /**
     * At least enough to reach [confidence]; beyond that, everything except a guard the size of the
     * largest other enemy stack next to [from], so the source is not left open.
     */
    private fun commit(state: GameState, from: Int, to: Int, movable: Int, confidence: Double): Int {
        val needed = (1..movable).firstOrNull { state.winChance(from, to, it) >= confidence } ?: movable
        val guard = state.map.provinces[from].neighbours
            .filter { it != to && state.owner(it).let { o -> o != null && o != state.current } }
            .maxOfOrNull { state.holdings[it].armies } ?: 0
        return maxOf(needed, movable - guard).coerceAtMost(movable)
    }

    /**
     * Moves fresh armies from interior provinces one step towards the frontier province with the
     * best prospect, discounted by distance: supply goes where an attack can succeed, not merely
     * to the nearest front (a mountain front can deadlock two equal stacks).
     */
    private fun logistics(state: GameState): Action? {
        val me = state.current
        val p = personality(me)
        val provinces = state.map.provinces
        val mine = state.provincesOf(me)
        val frontier = mine.filter { f -> provinces[f].neighbours.any { state.owner(it) != me } }
        if (frontier.isEmpty()) return null
        val prospect = frontier.associateWith { f ->
            provinces[f].neighbours.filter { state.owner(it) != me }.maxOf { e ->
                value(state, e, p) / (state.holdings[e].armies * state.defence(f, e) + 1)
            }
        }
        val distances = frontier.associateWith { f -> distancesWithin(state, f, me) }
        for (from in mine) {
            if (from in prospect || state.movable(from) == 0) continue
            val target = frontier.filter { from in distances.getValue(it) }
                .maxByOrNull { f -> prospect.getValue(f) / (1 + distances.getValue(f).getValue(from)) } ?: continue
            val d = distances.getValue(target)
            val next = provinces[from].neighbours.first { d[it] == d.getValue(from) - 1 }
            return Action.Move(from, next)
        }
        return null
    }

    /** Graph distance from [start] to each province of [kingdom], moving only through its own land. */
    private fun distancesWithin(state: GameState, start: Int, kingdom: Int): Map<Int, Int> {
        val distance = hashMapOf(start to 0)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val q = queue.removeFirst()
            for (n in state.map.provinces[q].neighbours) if (state.owner(n) == kingdom && n !in distance) {
                distance[n] = distance.getValue(q) + 1
                queue += n
            }
        }
        return distance
    }
}
