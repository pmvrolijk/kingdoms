package nl.lamia.kingdoms.app

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.use
import nl.lamia.kingdoms.core.ai.HeuristicAi
import nl.lamia.kingdoms.core.ai.step
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.NewGame
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The README's screenshots: the new-game screen, a game in round [ROUND] with a province selected,
 * and a battle up close. Written to `build/screenshots/`; with `SCREENSHOTS=1` to `docs/screenshots/`:
 *
 *     SCREENSHOTS=1 ./gradlew :composeApp:jvmTest --tests '*ScreenshotTest*'
 */
class ScreenshotTest {
    private val out = File(if (System.getenv("SCREENSHOTS") == "1") "../docs/screenshots" else "build/screenshots").apply { mkdirs() }
    private val density = 2f
    private val width = (1280 * density).toInt()
    private val height = (800 * density).toInt()

    @Test
    fun newGame() {
        ImageComposeScene(width, height, Density(density)) { App(Setup(seed = SEED)) }.use { scene ->
            scene.render(nanoTime = 0)
            write("new-game.png", scene.render(nanoTime = 1_500_000_000L))
        }
    }

    @Test
    fun gameAndBattle() {
        val start = midGame()
        val map = start.map
        var latest = start
        ImageComposeScene(width, height, Density(density)) {
            GameScreen(start, onNewGame = {}, Modifier.fillMaxSize(), onChange = { latest = it }, onMenu = {})
        }.use { scene ->
            // The AIs before the player move in real time, filling the chronicle.
            val deadline = System.currentTimeMillis() + 60_000
            while (!latest.isHumanTurn && System.currentTimeMillis() < deadline) {
                scene.render(nanoTime = System.nanoTime())
                Thread.sleep(50)
            }
            assertTrue(latest.isHumanTurn, "the AIs did not finish their turns")
            assertEquals(start.seed, latest.seed)
            Thread.sleep(1_200) // the last battle animation
            var t = System.nanoTime()

            // The player's strongest attack on a rival that is not a pact ally (that would break the pact),
            // between provinces away from the panels.
            val camera = Camera.fit(map, IntSize(width, height))
            val allies = latest.pact?.members?.takeIf { 0 in it }.orEmpty()
            val (source, target) = latest.provincesOf(0).filter { latest.movable(it) > 0 }.flatMap { p ->
                map.provinces[p].neighbours.filter { latest.owner(it).let { o -> o != null && o != 0 && o !in allies } }.map { p to it }
            }.filter { (a, b) ->
                val mid = (camera.toScreen(map.provinces[a].site) + camera.toScreen(map.provinces[b].site)) / 2f
                mid.x in width * 0.25f..width * 0.65f && mid.y in height * 0.25f..height * 0.7f
            }.maxBy { (a, b) -> latest.movable(a) - latest.holdings[b].armies }

            scene.tap(camera.toScreen(map.provinces[source].site), t)
            t += 1_000_000_000L
            write("game.png", scene.render(nanoTime = t))
            scene.tap(camera.toScreen(map.provinces[source].site), t) // deselect

            // Zoom about the middle of the two, which stays put; every other point moves away from it.
            val a = camera.toScreen(map.provinces[source].site)
            val b = camera.toScreen(map.provinces[target].site)
            val mid = (a + b) / 2f
            repeat(NOTCHES) { scene.sendPointerEvent(PointerEventType.Scroll, mid, scrollDelta = Offset(0f, -1f)) }
            val zoom = 1.1f.pow(NOTCHES)
            t += 1_000_000_000L
            scene.render(nanoTime = t)
            scene.tap(mid + (a - mid) * zoom, t)
            t += 600_000_000L
            scene.tap(mid + (b - mid) * zoom, t)
            assertEquals(0, latest.movable(source), "the attack from #$source did not land")
            assertTrue(latest.pact?.members?.contains(0) ?: true, "the attack broke the player's pact")
            scene.render(nanoTime = t + 150_000_000L) // the clash animation starts on this frame
            write("battle.png", scene.render(nanoTime = t + 450_000_000L)) // swords crossed, a spark, dust
        }
    }

    /** Seed [SEED] played by the AI on every seat until the player has just ended a turn in round [ROUND]. */
    private fun midGame(): GameState {
        val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = SEED))
        var state = NewGame.create(map, kingdoms = 4, seed = SEED)
        val ai = HeuristicAi()
        while (true) {
            val before = state
            state = step(state, ai).state
            check(!state.isOver) { "the game ended before round $ROUND" }
            if (before.current == 0 && state.current != 0 && state.round >= ROUND) return state
        }
    }

    private fun ImageComposeScene.tap(at: Offset, nanoTime: Long) {
        sendPointerEvent(PointerEventType.Press, at, timeMillis = nanoTime / 1_000_000)
        sendPointerEvent(PointerEventType.Release, at, timeMillis = nanoTime / 1_000_000 + 50)
        render(nanoTime = nanoTime + 100_000_000L)
    }

    private fun write(name: String, image: org.jetbrains.skia.Image) {
        File(out, name).writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    private companion object {
        const val SEED = 51L
        const val ROUND = 5
        const val NOTCHES = 14
    }
}
