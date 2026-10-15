package nl.lamia.kingdoms.core.ai

import nl.lamia.kingdoms.core.map.GameMap
import nl.lamia.kingdoms.core.rules.Event
import nl.lamia.kingdoms.core.rules.NewGame
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
    ): SimulationResult {
        var state = NewGame.create(map, kingdoms, seed, humans = emptySet(), turnOrder = turnOrder)
        val startingGrowth = state.kingdoms.map { state.growthOf(it.id) }
        val fallen = mutableListOf<Int>()
        var rejections = 0
        while (!state.isOver && state.round <= maxRounds) {
            val played = step(state, ai)
            if (played.rejected) rejections++
            played.events.filterIsInstance<Event.Eliminated>().forEach { fallen += it.kingdom }
            state = played.state
        }
        return SimulationResult(state.winner, minOf(state.round, maxRounds), stalled = !state.isOver, fallen, startingGrowth, rejections)
    }
}
