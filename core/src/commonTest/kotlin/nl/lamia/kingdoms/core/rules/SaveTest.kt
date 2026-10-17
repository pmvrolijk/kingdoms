package nl.lamia.kingdoms.core.rules

import nl.lamia.kingdoms.core.ai.HeuristicAi
import nl.lamia.kingdoms.core.ai.step
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.GameMap
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.map.Point
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveTest {
    private val ai = HeuristicAi()

    private fun GameState.playOn(actions: Int): GameState {
        var s = this
        repeat(actions) { if (!s.isOver) s = step(s, ai).state }
        return s
    }

    /**
     * [actual] is [expected] after a save and load. Everything must match exactly except map coordinates:
     * Kotlin/Wasm's `String.toDouble` puts about 1 in 8000 numbers one bit off (Design §7). Rules and AI
     * never read coordinates, so play is unaffected.
     */
    private fun assertSameGame(expected: GameState, actual: GameState, message: String? = null) =
        assertEquals(expected.copy(map = expected.map.snapTo(actual.map)), actual, message)

    private fun close(a: Double, b: Double) = abs(a - b) <= 1e-12 * max(1.0, abs(a))

    private fun Double.snapTo(o: Double) = if (close(this, o)) o else this

    private fun Point.snapTo(o: Point) = if (close(x, o.x) && close(y, o.y)) o else this

    private fun List<Point>.snapTo(o: List<Point>) = if (size == o.size) zip(o) { a, b -> a.snapTo(b) } else this

    private fun GameMap.snapTo(o: GameMap) = copy(
        width = width.snapTo(o.width),
        height = height.snapTo(o.height),
        provinces = provinces.zip(o.provinces) { a, b -> a.copy(site = a.site.snapTo(b.site), outline = a.outline.snapTo(b.outline)) },
        borders = borders.zip(o.borders) { a, b -> a.copy(path = a.path.snapTo(b.path)) },
        shores = shores.zip(o.shores) { a, b -> a.copy(path = a.path.snapTo(b.path)) },
        seas = seas.zip(o.seas) { a, b -> a.snapTo(b) },
    )

    @Test
    fun aSavedGameLoadsAsTheSameState() {
        for (size in MapSize.entries) for (seed in 1L..3L) {
            val start = NewGame.create(MapGenerator.generate(MapConfig.of(size, seed)), 4, seed, humans = emptySet())
            for (state in listOf(start, start.playOn(60))) {
                assertSameGame(state, Save.decode(Save.encode(state)), "$size seed $seed round ${state.round}")
            }
        }
    }

    /** Battles draw from the state's seed and counter, so a loaded game plays on exactly as the original would have. */
    @Test
    fun aLoadedGamePlaysOnLikeTheOriginal() {
        for (size in MapSize.entries) for (seed in 1L..3L) {
            val midGame = NewGame.create(MapGenerator.generate(MapConfig.of(size, seed)), 4, seed, humans = emptySet()).playOn(40)
            assertSameGame(midGame.playOn(400), Save.decode(Save.encode(midGame)).playOn(400), "$size seed $seed")
        }
    }

    @Test
    fun aSaveWithAnUnknownFieldStillLoads() {
        val state = Fixtures.game
        val text = Save.encode(state).replaceFirst("{", """{"addedLater":1,""")
        assertSameGame(state, Save.decode(text))
    }
}
