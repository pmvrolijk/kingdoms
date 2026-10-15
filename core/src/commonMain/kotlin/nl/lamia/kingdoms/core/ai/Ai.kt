package nl.lamia.kingdoms.core.ai

import nl.lamia.kingdoms.core.rules.Action
import nl.lamia.kingdoms.core.rules.Event
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.Outcome
import nl.lamia.kingdoms.core.rules.Rules

/** Chooses the next action for the kingdom whose turn it is. */
fun interface Ai {
    fun decide(state: GameState): Action
}

/** Never acts: ends every turn. */
val PassiveAi = Ai { Action.EndTurn }

/** A state and the events that led to it; [rejected] when the AI's own action was refused. */
data class Played(val state: GameState, val events: List<Event>, val rejected: Boolean = false)

/**
 * Applies one AI action. An AI that submits a rejected action has its turn ended for it, so a
 * buggy AI cannot hang the game.
 */
fun step(state: GameState, ai: Ai): Played {
    val first = Rules.apply(state, ai.decide(state))
    val outcome = if (first is Outcome.Rejected) Rules.apply(state, Action.EndTurn) else first
    check(outcome is Outcome.Applied) { "even EndTurn was rejected: $outcome" }
    return Played(outcome.state, outcome.events, rejected = first is Outcome.Rejected)
}

/** Plays AI turns until a human kingdom is to move or the game is over. */
fun playUntilHumanTurn(state: GameState, ai: Ai, maxActions: Int = 100_000): Played {
    var current = state
    val events = mutableListOf<Event>()
    repeat(maxActions) {
        if (current.isOver || current.kingdoms[current.current].human) return Played(current, events)
        val played = step(current, ai)
        current = played.state
        events += played.events
    }
    error("AI did not finish within $maxActions actions")
}
