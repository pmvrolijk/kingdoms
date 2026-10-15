package nl.lamia.kingdoms.core.rules

import nl.lamia.kingdoms.core.map.GameMap

data class Kingdom(val id: Int, val name: String, val human: Boolean, val capital: Int)

/** How the turn order is set each round (Design §4.2). */
enum class TurnOrder {
    /** Kingdom 0 first, then the rest by id, every round. */
    Fixed,

    /** The starting kingdom moves one seat each round. */
    Rotating,

    /** A fresh random order every round. */
    Shuffled,
}

/** Who holds a province and with what. [owner] null is neutral; [fresh] armies may still move this turn. */
data class Holding(val owner: Int?, val armies: Int, val fresh: Int = 0)

/**
 * The whole game, immutable. Change it only through [Rules.apply]. Randomness comes from [seed]
 * and the [draws] counter, so a state plus an action always produces the same next state.
 */
data class GameState(
    val map: GameMap,
    val seed: Long,
    val kingdoms: List<Kingdom>,
    val holdings: List<Holding>,
    val round: Int,
    /** The kingdom whose turn it is. */
    val current: Int,
    val turnOrder: TurnOrder = TurnOrder.Fixed,
    /** This round's turn order (kingdom ids); fallen kingdoms are skipped. */
    val order: List<Int> = kingdoms.map { it.id },
    /** The last kingdom standing, once there is one. */
    val winner: Int? = null,
    val draws: Long = 0,
) {
    fun owner(province: Int): Int? = holdings[province].owner

    fun provincesOf(kingdom: Int): List<Int> = holdings.indices.filter { holdings[it].owner == kingdom }

    fun isAlive(kingdom: Int): Boolean = holdings.any { it.owner == kingdom }

    val alive: List<Int> get() = kingdoms.map { it.id }.filter(::isAlive)

    /** Game over: a winner, or every human kingdom is gone. AI-only games end only with a winner. */
    val isOver: Boolean
        get() = winner != null || (kingdoms.any { it.human } && kingdoms.none { it.human && isAlive(it.id) })

    val isHumanTurn: Boolean get() = !isOver && kingdoms[current].human

    /** Armies the current kingdom can still move out of [province]: fresh ones, leaving one behind. */
    fun movable(province: Int): Int {
        val h = holdings[province]
        return if (h.owner == current) minOf(h.fresh, h.armies - 1).coerceAtLeast(0) else 0
    }

    /** Armies [kingdom] grows at the end of this round (Design §4.5). */
    fun growthOf(kingdom: Int): Int = provincesOf(kingdom).sumOf { map.provinces[it].yield }

    fun defence(from: Int, to: Int): Double = map.border(from, to)?.defence ?: 1.0

    /** The chance an attack from [from] on [to] with [armies] wins — the same model the battle uses. */
    fun winChance(from: Int, to: Int, armies: Int = movable(from)): Double =
        Battle.winChance(armies, holdings[to].armies, defence(from, to))
}
