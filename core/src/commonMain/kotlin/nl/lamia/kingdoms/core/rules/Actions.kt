package nl.lamia.kingdoms.core.rules

/** What a kingdom can do on its turn. Humans and AI submit the same actions. */
sealed interface Action {
    /** Move [armies] (default: all movable) from [from] to the adjacent [to]: transfer or attack. */
    data class Move(val from: Int, val to: Int, val armies: Int? = null) : Action

    data object EndTurn : Action
}

/** What happened, for the UI to show and animate. */
sealed interface Event {
    data class Moved(val kingdom: Int, val from: Int, val to: Int, val armies: Int) : Event

    data class Fought(
        val kingdom: Int,
        val from: Int,
        val to: Int,
        /** The defending kingdom, or null for a neutral province. */
        val defender: Int?,
        val attackers: Int,
        val defenders: Int,
        val defence: Double,
        val result: BattleResult,
    ) : Event

    data class Eliminated(val kingdom: Int, val by: Int) : Event

    data class Won(val kingdom: Int) : Event

    /** Growth per living kingdom at the end of [round]. */
    data class RoundEnded(val round: Int, val growth: Map<Int, Int>) : Event

    data class TurnStarted(val kingdom: Int, val round: Int) : Event

    /** [members] ally against [against], who holds too large a share of all armies (Design §4.7). */
    data class PactFormed(val against: Int, val members: List<Int>) : Event

    /** The pact against [against] ends: it fell back, fell, or too few kingdoms are left. */
    data class PactEnded(val against: Int) : Event

    /** [kingdom] attacked a fellow member and is out of the pact against [against]. */
    data class PactLeft(val kingdom: Int, val against: Int) : Event
}

enum class Rejection { GameOver, NoSuchProvince, NotYourProvince, NotAdjacent, NothingToMove, InvalidAmount }

sealed interface Outcome {
    data class Applied(val state: GameState, val events: List<Event>) : Outcome

    data class Rejected(val reason: Rejection) : Outcome
}
