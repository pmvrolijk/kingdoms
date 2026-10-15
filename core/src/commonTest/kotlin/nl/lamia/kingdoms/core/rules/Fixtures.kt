package nl.lamia.kingdoms.core.rules

import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import kotlin.test.assertIs

internal object Fixtures {
    val map = MapGenerator.generate(MapConfig(seed = 3))
    val game = NewGame.create(map, kingdoms = 4, seed = 3)
}

internal fun GameState.with(vararg changes: Pair<Int, Holding>): GameState {
    val holdings = holdings.toMutableList()
    for ((p, h) in changes) holdings[p] = h
    return copy(holdings = holdings)
}

/** Every province neutral with one army, then [changes]. */
internal fun GameState.only(vararg changes: Pair<Int, Holding>): GameState =
    copy(holdings = holdings.map { Holding(null, 1) }).with(*changes)

internal fun GameState.play(action: Action): Outcome.Applied = assertIs<Outcome.Applied>(Rules.apply(this, action))

internal fun GameState.reject(action: Action): Rejection = assertIs<Outcome.Rejected>(Rules.apply(this, action)).reason
