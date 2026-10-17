package nl.lamia.kingdoms.core.ai

import nl.lamia.kingdoms.core.map.GameMap
import nl.lamia.kingdoms.core.rules.Event
import nl.lamia.kingdoms.core.rules.NewGame
import nl.lamia.kingdoms.core.rules.RuleOptions
import nl.lamia.kingdoms.core.rules.TurnOrder

data class SimulationResult(
    val winner: Int?,
    /** Rounds played: the round in which the game ended, or the cap. */
    val rounds: Int,
    /** True when the round cap was hit — a stalemate to tune away (Design §9). */
    val stalled: Boolean,
    /** Kingdoms in the order they fell. */
    val fallen: List<Int>,
    /** Each kingdom's growth per round at the start. */
    val startingGrowth: List<Int>,
    /** AI actions the rules refused; anything above zero is an AI bug. */
    val rejections: Int,
    /** The kingdom with the most armies at the start of each round (index 0 = round 1). */
    val leaders: List<Int> = emptyList(),
    /** The kingdom whose capital is farthest from every other capital, or null on a tie (Design §9). */
    val farthest: Int? = null,
    /** The kingdom whose capital has the largest summed distance to all rival capitals, or null on a tie. */
    val remotest: Int? = null,
    /** The kingdom in the fewest battles with other kingdoms in rounds 1–6, or null on a tie (Design §9). */
    val quietest: Int? = null,
)

/** AI-vs-AI games for tuning (Design §9). Every seat is played by [ai]; no seat is human. */
object Simulation {
    fun run(
        map: GameMap,
        kingdoms: Int,
        seed: Long,
        ai: Ai,
        turnOrder: TurnOrder = TurnOrder.Shuffled,
        maxRounds: Int = 200,
        options: RuleOptions = RuleOptions(),
    ): SimulationResult {
        var state = NewGame.create(map, kingdoms, seed, humans = emptySet(), turnOrder = turnOrder, options = options)
        val startingGrowth = state.kingdoms.map { state.growthOf(it.id) }
        val fallen = mutableListOf<Int>()
        var rejections = 0
        val leaders = mutableListOf<Int>()
        val battles = IntArray(kingdoms)
        while (!state.isOver && state.round <= maxRounds) {
            if (state.round > leaders.size) leaders += state.alive.maxBy { k -> state.provincesOf(k).sumOf { state.holdings[it].armies } }
            val played = step(state, ai)
            if (played.rejected) rejections++
            played.events.filterIsInstance<Event.Eliminated>().forEach { fallen += it.kingdom }
            if (state.round <= QUIET_ROUNDS) played.events.filterIsInstance<Event.Fought>().forEach { f ->
                if (f.defender != null) { battles[f.kingdom]++; battles[f.defender]++ }
            }
            state = played.state
        }
        return SimulationResult(
            state.winner, minOf(state.round, maxRounds), stalled = !state.isOver, fallen, startingGrowth, rejections,
            leaders, farthest(map, state.kingdoms.map { it.capital }), remotest(map, state.kingdoms.map { it.capital }),
            battles.indices.minBy { battles[it] }.takeIf { k -> battles.count { it == battles[k] } == 1 },
        )
    }

    private const val QUIET_ROUNDS = 6

    /** The capital with the largest graph distance to its nearest rival capital; null when two share it. */
    fun farthest(map: GameMap, capitals: List<Int>): Int? = uniqueMax(capitals.map { c -> rivals(map, capitals, c).min() })

    /** The capital with the largest summed graph distance to all rival capitals; null when two share it. */
    fun remotest(map: GameMap, capitals: List<Int>): Int? = uniqueMax(capitals.map { c -> rivals(map, capitals, c).sum() })

    private fun rivals(map: GameMap, capitals: List<Int>, c: Int): List<Int> {
        val d = IntArray(map.provinces.size) { -1 }.also { it[c] = 0 }
        val queue = ArrayDeque(listOf(c))
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            for (n in map.provinces[p].neighbours) if (d[n] < 0) { d[n] = d[p] + 1; queue += n }
        }
        return capitals.filter { it != c }.map { d[it] }
    }

    private fun uniqueMax(values: List<Int>): Int? = values.indexOf(values.max()).takeIf { values.count { it == values.max() } == 1 }
}
