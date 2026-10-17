package nl.lamia.kingdoms.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.use
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.rules.Event
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.Holding
import nl.lamia.kingdoms.core.rules.NewGame
import nl.lamia.kingdoms.core.rules.Pact
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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
        ImageComposeScene(width, height) { App(Setup(), playing = true) }.use { scene ->
            scene.render()
            scene.save("app.png", frame = 1)
        }
    }

    @Test
    fun newGameScreen() {
        ImageComposeScene(width, height) { App(Setup()) }.use { scene ->
            scene.render()
            scene.save("new-game.png", frame = 1)
        }
        ImageComposeScene(2556, 1179, density = Density(3f)) { App(Setup()) }.use { scene ->
            scene.render()
            scene.save("new-game-phone.png", frame = 1)
        }
        // Huawei P30: 780 × 360 dp, the smallest phone we check.
        ImageComposeScene(2340, 1080, density = Density(3f)) { App(Setup()) }.use { scene ->
            scene.render()
            scene.save("new-game-p30.png", frame = 1)
        }
        ImageComposeScene(2340, 1080, density = Density(3f)) { App(Setup(), playing = true) }.use { scene ->
            scene.render()
            scene.save("app-p30.png", frame = 1)
        }
    }

    @Test
    fun zoomingScalesMarkersAndShowsNames() {
        ImageComposeScene(width, height) { App(Setup(), playing = true) }.use { scene ->
            scene.render()
            val at = Offset(640f, 400f)
            // 1.1× per wheel notch: about 1.6×, then 2.6× (names fade in), then out to the limit (0.8×).
            for ((notches, name) in listOf(5 to "zoom-1.6.png", 5 to "zoom-2.6.png")) {
                repeat(notches) { scene.sendPointerEvent(PointerEventType.Scroll, at, scrollDelta = Offset(0f, -1f)) }
                scene.save(name, frame = 1)
            }
            repeat(17) { scene.sendPointerEvent(PointerEventType.Scroll, at, scrollDelta = Offset(0f, 1f)) }
            scene.save("zoom-out.png", frame = 1)
        }
        ImageComposeScene(2556, 1179, density = Density(3f)) { App(Setup(), playing = true) }.use { scene ->
            scene.render()
            repeat(10) { scene.sendPointerEvent(PointerEventType.Scroll, Offset(1278f, 590f), scrollDelta = Offset(0f, -1f)) }
            scene.save("zoom-phone-2.6.png", frame = 1)
        }
    }

    @Test
    fun previewMapStaysBehindTheSettings() {
        ImageComposeScene(width, height) { App(Setup()) }.use { scene ->
            // Points across the settings panel, down to the Start button.
            val panel = listOf(300 to 160, 100 to 330, 200 to 400, 60 to 300)
            fun pixel() = scene.render(nanoTime = 3_000_000_000L).toComposeImageBitmap().toPixelMap().let { m -> panel.map { (x, y) -> m[x, y] } }
            val before = pixel()
            // Drag the preview map left, over where the settings are.
            val from = Offset(800f, 400f)
            scene.sendPointerEvent(PointerEventType.Press, from, timeMillis = 1000)
            for (i in 1..10) scene.sendPointerEvent(PointerEventType.Move, from - Offset(50f * i, 0f), timeMillis = 1000L + 20 * i)
            scene.sendPointerEvent(PointerEventType.Release, from - Offset(500f, 0f), timeMillis = 1300)
            scene.save("new-game-dragged.png", frame = 2)
            assertEquals(before, pixel())
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

        ImageComposeScene(width, height) { App(Setup(), playing = true) }.use { scene ->
            scene.render()
            scene.tap(camera.toScreen(map.provinces[source].site), frame = 1)
            scene.save("selected.png", frame = 2)
            scene.tap(camera.toScreen(map.provinces[target].site), frame = 3)
            scene.saveAt("clash-early.png", 3_250_000_000L)
            scene.saveAt("clash.png", 3_500_000_000L)
            scene.saveAt("clash-late.png", 3_750_000_000L)
            scene.save("attacked.png", frame = 4)
        }
    }

    @Test
    fun phoneLandscape() {
        // A 6.1" phone in landscape: 2556 × 1179 px at 3×.
        ImageComposeScene(2556, 1179, density = Density(3f)) { App(Setup(), playing = true) }.use { scene ->
            scene.render()
            scene.save("phone.png", frame = 1)
        }
    }

    /** Regression: a new game on the same map (Restart, turn order, kingdoms) must still take taps. */
    @Test
    fun tapsReachANewGameOnTheSameMap() {
        val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = 1))
        val first = NewGame.create(map, kingdoms = 4, seed = 1)
        val second = NewGame.create(map, kingdoms = 4, seed = 2)
        val (source, target) = second.provincesOf(0).filter { second.movable(it) > 0 }.firstNotNullOf { p ->
            map.provinces[p].neighbours.firstOrNull { second.owner(it) == null }?.let { p to it }
        }
        val camera = Camera.fit(map, IntSize(width, height))
        var start by mutableStateOf(first)
        var latest: GameState? = null
        ImageComposeScene(width, height) {
            GameScreen(start, onNewGame = {}, Modifier.fillMaxSize(), onChange = { latest = it })
        }.use { scene ->
            scene.render()
            start = second
            scene.render(nanoTime = 500_000_000L)
            scene.tap(camera.toScreen(map.provinces[source].site), frame = 1)
            scene.tap(camera.toScreen(map.provinces[target].site), frame = 2)
            scene.save("new-game-same-map.png", frame = 3)
        }
        assertEquals(second.seed, latest?.seed, "the tap went to the previous game")
        assertEquals(0, latest?.movable(source), "the tap-move from #$source did not reach the new game")
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

    @Test
    fun noEnemyArrowOnThePlayersTurn() {
        val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = 1))
        val start = NewGame.create(map, kingdoms = 4, seed = 1).let { game ->
            game.copy(current = 1, holdings = game.holdings.map { if (it.owner == 1) it.copy(fresh = it.armies) else it })
        }
        var latest = start
        var moves = 0
        ImageComposeScene(width, height) {
            GameScreen(start, onNewGame = {}, Modifier.fillMaxSize(), onChange = { latest = it; moves++ })
        }.use { scene ->
            // The waves' phase depends on the first frame times; the fresh render below repeats them.
            val frames = mutableListOf<Long>()
            val deadline = System.currentTimeMillis() + 60_000
            while (!latest.isHumanTurn && System.currentTimeMillis() < deadline) {
                frames += System.nanoTime()
                scene.render(nanoTime = frames.last())
                Thread.sleep(50)
            }
            assertTrue(latest.isHumanTurn && moves > 3, "the AIs played into the player's turn")
            assertEquals(start.map.config.seed, latest.map.config.seed)
            Thread.sleep(1_200) // let the last battle animation finish
            scene.render(nanoTime = System.nanoTime())
            val end = System.nanoTime() + 5_000_000_000L
            val played = scene.render(nanoTime = end)
            File("build/snapshots/players-turn.png").writeBytes(played.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            val fresh = ImageComposeScene(width, height) { GameScreen(latest, onNewGame = {}, Modifier.fillMaxSize()) }.use {
                frames.take(3).forEach { t -> it.render(nanoTime = t) }
                it.render(nanoTime = end)
            }
            // The same state without history: only the chronicle (top right) may differ.
            val a = played.toComposeImageBitmap().toPixelMap()
            val b = fresh.toComposeImageBitmap().toPixelMap()
            val differing = (0 until width).sumOf { x -> (0 until height).count { y -> !(x > 900 && y < 330) && a[x, y] != b[x, y] } }
            assertEquals(0, differing, "pixels differ outside the chronicle: an arrow left over from the AI?")
        }
    }

    private fun ImageComposeScene.tap(at: Offset, frame: Int) {
        sendPointerEvent(PointerEventType.Press, at, timeMillis = frame * 1000L)
        sendPointerEvent(PointerEventType.Release, at, timeMillis = frame * 1000L + 50)
        render(nanoTime = frame * 1_000_000_000L + 100_000_000L)
    }

    /** A pact against Brightvale with the player in it: a line in the turn panel, the warning on the card (Design §4.7). */
    @Test
    fun aPactShowsInTheTurnPanelAndWarnsBeforeAttackingAnAlly() {
        val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = 1))
        val game = NewGame.create(map, kingdoms = 4, seed = 1)
        val (source, ally) = game.provincesOf(0).filter { game.movable(it) > 0 }.firstNotNullOf { p ->
            map.provinces[p].neighbours.firstOrNull { game.owner(it) == null }?.let { p to it }
        }
        val holdings = game.holdings.toMutableList().apply { this[ally] = Holding(2, 3) }
        val start = game.copy(holdings = holdings, pact = Pact(against = 1, members = listOf(0, 2, 3)))
        val camera = Camera.fit(map, IntSize(width, height))
        ImageComposeScene(width, height) { GameScreen(start, onNewGame = {}, modifier = Modifier.fillMaxSize()) }.use { scene ->
            scene.render()
            scene.tap(camera.toScreen(map.provinces[source].site), frame = 1)
            scene.save("pact.png", frame = 2)
        }
        val formed = describe(start, Event.PactFormed(1, listOf(0, 2, 3)))!!.text.text
        assertEquals("${start.kingdoms[0].name}, ${start.kingdoms[2].name} and ${start.kingdoms[3].name} ally against ${start.kingdoms[1].name}", formed)
        assertEquals("${start.kingdoms[0].name} breaks the pact against ${start.kingdoms[1].name}", describe(start, Event.PactLeft(0, 1))!!.text.text)
        assertEquals("The pact against ${start.kingdoms[1].name} ends", describe(start, Event.PactEnded(1))!!.text.text)
        assertEquals("Pact: ${start.kingdoms[0].name}, ${start.kingdoms[2].name} and ${start.kingdoms[3].name} against ${start.kingdoms[1].name}", pactLine(start)!!.text)
    }

    private fun ImageComposeScene.saveAt(name: String, nanoTime: Long) {
        val file = File("build/snapshots/$name").apply { parentFile.mkdirs() }
        file.writeBytes(render(nanoTime).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    private fun ImageComposeScene.save(name: String, frame: Int) {
        val file = File("build/snapshots/$name").apply { parentFile.mkdirs() }
        val image = render(nanoTime = frame * 1_000_000_000L + 500_000_000L)
        file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        assertTrue(file.length() > 0)
    }
}
