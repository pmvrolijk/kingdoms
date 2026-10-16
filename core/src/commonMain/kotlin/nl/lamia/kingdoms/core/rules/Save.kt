package nl.lamia.kingdoms.core.rules

import kotlinx.serialization.json.Json

/**
 * The save format (Design §7): a [GameState] snapshot as JSON, map included, so a save does not
 * depend on the generator staying the same. Defaults are written out, so changing a default
 * later does not change what an old save means.
 */
object Save {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    fun decode(text: String): GameState = json.decodeFromString(GameState.serializer(), text)
}
