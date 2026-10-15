package nl.lamia.kingdoms.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.use
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.rules.NewGame
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the app headless to `composeApp/build/snapshots/` — a visual check that needs no window
 * or screen-recording permission. It asserts only that rendering succeeds; look at the PNGs.
 * The second test taps through a move the way a player would.
 */
class SnapshotTest {
    private val width = 1280
    private val height = 800

    @Test
    fun rendersTheMap() {
        ImageComposeScene(width, height) { App() }.use { scene ->
            scene.render()
            scene.save("app.png", frame = 1)
        }
    }

    @Test
    fun selectingAndAttackingByTapping() {
        // The same game App() starts with, to find one of the player's provinces next to a neutral one.
        val config = MapConfig.of(MapSize.Medium, seed = 1)
        val map = MapGenerator.generate(config)
        val game = NewGame.create(map, kingdoms = 4, seed = config.seed)
        val (source, target) = game.provincesOf(0).filter { game.movable(it) > 0 }.firstNotNullOf { p ->
            map.provinces[p].neighbours.firstOrNull { game.owner(it) == null }?.let { p to it }
        }
        val camera = Camera.fit(map, IntSize(width, height))

        ImageComposeScene(width, height) { App() }.use { scene ->
            scene.render()
            scene.tap(camera.toScreen(map.provinces[source].site), frame = 1)
            scene.save("selected.png", frame = 2)
            scene.tap(camera.toScreen(map.provinces[target].site), frame = 3)
            scene.save("attacked.png", frame = 4)
        }
    }

    @Test
    fun aiTurnsPlayOutStepByStep() {
        val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = 1))
        val start = NewGame.create(map, kingdoms = 4, seed = 1).let { game ->
            game.copy(current = 1, holdings = game.holdings.map { if (it.owner == 1) it.copy(fresh = it.armies) else it })
        }
        ImageComposeScene(width, height) { GameScreen(start, onNewGame = {}, Modifier.fillMaxSize()) }.use { scene ->
            scene.render()
            Thread.sleep(1_600) // real time: the AI pauses between moves
            scene.save("ai-turn.png", frame = 1)
        }
    }

    private fun ImageComposeScene.tap(at: Offset, frame: Int) {
        sendPointerEvent(PointerEventType.Press, at, timeMillis = frame * 1000L)
        sendPointerEvent(PointerEventType.Release, at, timeMillis = frame * 1000L + 50)
        render(nanoTime = frame * 1_000_000_000L + 100_000_000L)
    }

    private fun ImageComposeScene.save(name: String, frame: Int) {
        val file = File("build/snapshots/$name").apply { parentFile.mkdirs() }
        val image = render(nanoTime = frame * 1_000_000_000L + 500_000_000L)
        file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        assertTrue(file.length() > 0)
    }
}
