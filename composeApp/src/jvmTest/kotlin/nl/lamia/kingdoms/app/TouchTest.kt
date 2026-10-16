package nl.lamia.kingdoms.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.use
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.rules.NewGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Touch handling on the map (Design §6.1): zoom limits, tap tolerance, pinches. */
class TouchTest {
    private val view = IntSize(1280, 800)

    @Test
    fun zoomAndPanStayWithinLimits() {
        for (size in MapSize.entries) {
            val map = MapGenerator.generate(MapConfig.of(size, seed = 1))
            val fit = Camera.fit(map, view)
            // The fitted board is centred, so its extent follows from the fit.
            val board = Offset(view.width - 2 * fit.offset.x, view.height - 2 * fit.offset.y) / fit.scale
            var cam = fit
            repeat(60) { cam = cam.zoom(0.8f, Offset(100f, 100f), fit.scale).keepOnBoard(map, view) }
            assertEquals(fit.scale * Camera.MIN_ZOOM, cam.scale, 1e-4f, "$size zoomed out")
            repeat(60) { cam = cam.zoom(1.25f, Offset(100f, 700f), fit.scale).keepOnBoard(map, view) }
            assertEquals(fit.scale * Camera.MAX_ZOOM, cam.scale, 1e-3f, "$size zoomed in")
            for (pan in listOf(Offset(1e5f, 0f), Offset(-1e5f, 0f), Offset(0f, 1e5f), Offset(0f, -1e5f))) {
                val moved = Camera(cam.offset + pan, cam.scale).keepOnBoard(map, view)
                val centre = (Offset(view.width / 2f, view.height / 2f) - moved.offset) / moved.scale
                assertTrue(centre.x in -0.01f..board.x + 0.01f && centre.y in -0.01f..board.y + 0.01f, "$size panned by $pan: centre $centre off the board")
            }
        }
    }

    @OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun aFingerJustOffTheCoastFindsTheProvinceButAPinchIsNoTap() {
        val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = 1))
        val game = NewGame.create(map, kingdoms = 4, seed = 1)
        val cam = Camera.fit(map, view)
        // A sea point a few pixels off the middle of a stretch of coast, away from province corners.
        val (sea, province) = map.shores.filter { it.path.size >= 5 }.firstNotNullOf { shore ->
            val coast = cam.toScreen(shore.path[shore.path.size / 2])
            val away = coast - cam.toScreen(map.provinces[shore.province].site)
            val step = away / away.getDistance()
            (2..5).map { coast + step * it.toFloat() }.firstOrNull { map.provinceAt(cam.toWorld(it)) == null }?.let { it to shore.province }
        }
        val none = -1
        var tapped: Int? = none
        ImageComposeScene(view.width, view.height) {
            MapView(game, null, null, remember { CameraState() }, emptyList(), 0L, onTap = { tapped = it }, onLongPress = {}, Modifier.fillMaxSize())
        }.use { scene ->
            scene.render()
            var t = 1_000L
            fun tap(type: PointerType) {
                scene.sendPointerEvent(PointerEventType.Press, sea, timeMillis = t, type = type)
                scene.sendPointerEvent(PointerEventType.Release, sea, timeMillis = t + 50, type = type)
                scene.render()
                t += 1_000
            }

            tap(PointerType.Touch)
            assertEquals(province, tapped, "a finger ${sea} just off the coast")
            tapped = none
            tap(PointerType.Mouse)
            assertNull(tapped, "a mouse click on the sea is the sea")

            // Two fingers down and up without moving: a pinch that went nowhere, not a tap.
            tapped = none
            val a = cam.toScreen(map.provinces[0].site)
            val b = a + Offset(60f, 0f)
            fun pointers(pressed: Boolean, second: Boolean = true) = listOfNotNull(
                ComposeScenePointer(PointerId(1), a, pressed, PointerType.Touch),
                ComposeScenePointer(PointerId(2), b, pressed, PointerType.Touch).takeIf { second },
            )
            scene.sendPointerEvent(PointerEventType.Press, pointers(true, second = false), timeMillis = t)
            scene.sendPointerEvent(PointerEventType.Press, pointers(true), timeMillis = t + 20)
            scene.sendPointerEvent(PointerEventType.Release, pointers(false), timeMillis = t + 120)
            scene.render()
            assertEquals(none, tapped, "a two-finger touch tapped")
        }
    }
}
