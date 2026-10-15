package nl.lamia.kingdoms.core.rules

import nl.lamia.kingdoms.core.map.mix
import kotlin.random.Random

/** The only way game state changes (Design §2.3, §4). */
object Rules {
    fun apply(state: GameState, action: Action): Outcome {
        if (state.isOver) return Outcome.Rejected(Rejection.GameOver)
        return when (action) {
            is Action.Move -> move(state, action)
            Action.EndTurn -> endTurn(state)
        }
    }

    private fun move(state: GameState, action: Action.Move): Outcome {
        val (from, to) = action
        val provinces = state.map.provinces.indices
        if (from !in provinces || to !in provinces) return Outcome.Rejected(Rejection.NoSuchProvince)
        val source = state.holdings[from]
        if (source.owner != state.current) return Outcome.Rejected(Rejection.NotYourProvince)
        if (to !in state.map.provinces[from].neighbours) return Outcome.Rejected(Rejection.NotAdjacent)
        val movable = state.movable(from)
        if (movable < 1) return Outcome.Rejected(Rejection.NothingToMove)
        val armies = action.armies ?: movable
        if (armies !in 1..movable) return Outcome.Rejected(Rejection.InvalidAmount)

        val holdings = state.holdings.toMutableList()
        holdings[from] = source.copy(armies = source.armies - armies, fresh = source.fresh - armies)
        val target = holdings[to]

        if (target.owner == state.current) {
            holdings[to] = target.copy(armies = target.armies + armies) // arrivals are not fresh
            return Outcome.Applied(state.copy(holdings = holdings), listOf(Event.Moved(state.current, from, to, armies)))
        }

        val defence = state.defence(from, to)
        val result = Battle.resolve(armies, target.armies, defence, Random(mix(state.seed, BATTLES, state.draws)))
        val events = mutableListOf<Event>(
            Event.Fought(state.current, from, to, target.owner, armies, target.armies, defence, result),
        )
        holdings[to] = if (result.attackerWon) {
            Holding(owner = state.current, armies = result.attackerSurvivors, fresh = 0)
        } else {
            target.copy(armies = result.defenderSurvivors, fresh = minOf(target.fresh, result.defenderSurvivors))
        }
        val loser = target.owner
        if (result.attackerWon && loser != null && holdings.none { it.owner == loser }) {
            events += Event.Eliminated(loser, by = state.current)
        }
        var next = state.copy(holdings = holdings, draws = state.draws + 1)
        val alive = next.alive
        if (alive.size == 1) {
            next = next.copy(winner = alive.single())
            events += Event.Won(alive.single())
        }
        return Outcome.Applied(next, events)
    }

    private fun endTurn(state: GameState): Outcome {
        val alive = state.alive
        val events = mutableListOf<Event>()
        var holdings = state.holdings
        var round = state.round
        var order = state.order
        var draws = state.draws
        var next = order.drop(order.indexOf(state.current) + 1).firstOrNull { it in alive }
        if (next == null) {
            val growth = alive.associateWith { state.growthOf(it) }
            holdings = holdings.mapIndexed { p, h ->
                if (h.owner != null) h.copy(armies = h.armies + state.map.provinces[p].yield) else h
            }
            events += Event.RoundEnded(round, growth)
            round++
            order = when (state.turnOrder) {
                TurnOrder.Fixed -> state.order
                TurnOrder.Rotating -> state.kingdoms.indices.map { (it + round - 1) % state.kingdoms.size }
                TurnOrder.Shuffled -> state.kingdoms.indices.shuffled(Random(mix(state.seed, ORDERS, draws++)))
            }
            next = order.first { it in alive }
        }
        holdings = holdings.map { if (it.owner == next) it.copy(fresh = it.armies) else it }
        events += Event.TurnStarted(next, round)
        return Outcome.Applied(state.copy(holdings = holdings, round = round, current = next, order = order, draws = draws), events)
    }

    private const val BATTLES = 101L
    private const val ORDERS = 102L
}
