package nl.lamia.kingdoms.core.rules

import nl.lamia.kingdoms.core.ai.HeuristicAi
import nl.lamia.kingdoms.core.ai.step
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveTest {
    private val ai = HeuristicAi()

    private fun GameState.playOn(actions: Int): GameState {
        var s = this
        repeat(actions) { if (!s.isOver) s = step(s, ai).state }
        return s
    }

    @Test
    fun aSavedGameLoadsAsTheSameState() {
        for (size in MapSize.entries) for (seed in 1L..3L) {
            val start = NewGame.create(MapGenerator.generate(MapConfig.of(size, seed)), 4, seed, humans = emptySet())
            for (state in listOf(start, start.playOn(60))) {
                assertEquals(state, Save.decode(Save.encode(state)), "$size seed $seed round ${state.round}")
            }
        }
    }

    /** Battles draw from the state's seed and counter, so a loaded game plays on exactly as the original would have. */
    @Test
    fun aLoadedGamePlaysOnLikeTheOriginal() {
        for (size in MapSize.entries) for (seed in 1L..3L) {
            val midGame = NewGame.create(MapGenerator.generate(MapConfig.of(size, seed)), 4, seed, humans = emptySet()).playOn(40)
            assertEquals(midGame.playOn(400), Save.decode(Save.encode(midGame)).playOn(400), "$size seed $seed")
        }
    }

    @Test
    fun aSaveWithAnUnknownFieldStillLoads() {
        val state = Fixtures.game
        val text = Save.encode(state).replaceFirst("{", """{"addedLater":1,""")
        assertEquals(state, Save.decode(text))
    }
}
