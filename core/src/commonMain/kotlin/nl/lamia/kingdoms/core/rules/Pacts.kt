package nl.lamia.kingdoms.core.rules

import kotlinx.serialization.Serializable

/**
 * Kingdoms allied against a runaway leader (Design §4.7). Members do not attack each other; the AI
 * members go after [against]. A person who attacks a member while in the pact leaves it.
 */
@Serializable
data class Pact(val against: Int, val members: List<Int>)

/**
 * Pact settings, fixed for a game (Design §4.7). The defaults are the game's rules; other
 * values exist so the tuning report can compare them.
 */
@Serializable
data class RuleOptions(
    val pacts: Boolean = true,
    /** A pact forms when one kingdom holds at least this share of all armies… */
    val pactForms: Double = 0.35,
    /** …and ends when it falls below this share. */
    val pactEnds: Double = 0.28,
    /** A kingdom that breaks a pact sits out new pacts for this many rounds. */
    val breakerSitsOut: Int = 3,
)

internal object Pacts {
    /** The pact after a round ends: formed, kept, or ended, with the events that say so. */
    fun update(state: GameState): Pair<Pact?, List<Event>> {
        val options = state.options
        val alive = state.alive
        val armies = alive.associateWith { k -> state.provincesOf(k).sumOf { state.holdings[it].armies } }
        val total = armies.values.sum().toDouble()
        fun share(k: Int) = (armies[k] ?: 0) / total
        val events = mutableListOf<Event>()
        var pact = state.pact?.let { p -> p.copy(members = p.members.filter { it in alive }) }
        if (pact != null && (!holds(state, pact) || share(pact.against) < options.pactEnds)) {
            events += Event.PactEnded(pact.against)
            pact = null
        }
        if (pact == null && options.pacts && alive.size >= 3) {
            val leader = alive.maxBy { armies.getValue(it) }
            val tied = alive.count { armies.getValue(it) == armies.getValue(leader) } > 1
            // Easy AIs are too disorganised to join; a recent breaker is not trusted (Design §4.7).
            val members = alive.filter {
                it != leader && (state.kingdoms[it].human || state.kingdoms[it].difficulty != Difficulty.Easy) &&
                    state.round - (state.pactBreakers[it] ?: Int.MIN_VALUE / 2) >= options.breakerSitsOut
            }
            if (!tied && share(leader) >= options.pactForms && members.size >= 2) {
                pact = Pact(leader, members)
                events += Event.PactFormed(leader, members)
            }
        }
        return pact to events
    }

    /**
     * Whether [pact] can stand at all, whatever the shares: pacts on, three kingdoms alive, its
     * target alive and at least two living members.
     */
    fun holds(state: GameState, pact: Pact): Boolean {
        val alive = state.alive
        return state.options.pacts && alive.size >= 3 && pact.against in alive && pact.members.count { it in alive } >= 2
    }

    /** [kingdom], a member, attacked [defender], another member: it leaves the pact. */
    fun breaks(state: GameState, kingdom: Int, defender: Int?): Boolean {
        val pact = state.pact ?: return false
        return defender != null && kingdom in pact.members && defender in pact.members
    }
}
