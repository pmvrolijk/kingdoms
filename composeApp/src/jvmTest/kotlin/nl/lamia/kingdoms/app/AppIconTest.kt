package nl.lamia.kingdoms.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders the app icon for every platform into `build/icons/` and checks it (Design §8.1). With
 * `ICONS=1` it also writes the files the platforms use:
 *
 *     ICONS=1 ./gradlew :composeApp:jvmTest --tests '*AppIconTest*'
 */
class AppIconTest {
    private val out = File("build/icons").apply { mkdirs() }
    private val write = System.getenv("ICONS") == "1"
    private val root = File("..")

    /** Zoom for icons that show the whole square: the 66-unit circle fills ~80% of it. */
    private val fullBleed = 1.3f

    @Test
    fun iosIconIsOpaqueAndFilled() {
        val png = render(1024) { drawAppIcon(zoom = fullBleed) }
        val pixels = Image.makeFromEncoded(png).toComposeImageBitmap().toPixelMap()
        for (y in 0 until 1024 step 8) for (x in 0 until 1024 step 8) assertEquals(1f, pixels[x, y].alpha, "transparent at $x,$y")
        assertTrue(hasKingdomBlue(pixels), "no pennant or roof in the player's colour")
        save("ios-1024.png", png)
        if (write) {
            val set = File(root, "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset").apply { mkdirs() }
            File(set, "AppIcon-1024.png").writeBytes(png)
        }
    }

    @Test
    fun androidLayersKeepToTheSafeCircle() {
        val densities = mapOf("mdpi" to 1f, "hdpi" to 1.5f, "xhdpi" to 2f, "xxhdpi" to 3f, "xxxhdpi" to 4f)
        for ((name, factor) in densities) {
            val px = (GRID * factor).toInt()
            val front = render(px) { drawAppIcon(IconLayer.Foreground) }
            val back = render(px) { drawAppIcon(IconLayer.Background) }
            val pixels = Image.makeFromEncoded(front).toComposeImageBitmap().toPixelMap()
            val unit = px / GRID
            for (y in 0 until px) for (x in 0 until px) {
                val r = hypot(x + 0.5f - px / 2f, y + 0.5f - px / 2f) / unit
                if (r > SAFE / 2 + 0.5f) assertTrue(pixels[x, y].alpha < 0.02f, "$name: foreground at $x,$y, ${r} units out")
            }
            save("android-$name-foreground.png", front)
            save("android-$name-background.png", back)
            if (write) {
                val dir = File(root, "androidApp/src/main/res/mipmap-$name").apply { mkdirs() }
                File(dir, "ic_launcher_foreground.png").writeBytes(front)
                File(dir, "ic_launcher_background.png").writeBytes(back)
            }
        }
    }

    /** macOS (.icns via `iconutil`) and Linux (.png): a rounded tile with margins, as macOS icons have. */
    @Test
    fun desktopIcons() {
        val sizes = listOf(16, 32, 64, 128, 256, 512, 1024)
        val set = File(out, "kingdoms.iconset").apply { mkdirs() }
        for (s in sizes) {
            val png = render(s) { macTile() }
            if (s <= 512) File(set, "icon_${s}x$s.png").writeBytes(png)
            if (s >= 32) File(set, "icon_${s / 2}x${s / 2}@2x.png").writeBytes(png)
            if (s == 512) save("desktop-512.png", png)
        }
        if (write) {
            val icons = File(root, "composeApp/icons").apply { mkdirs() }
            File(icons, "kingdoms.png").writeBytes(render(512) { macTile() })
            if (System.getProperty("os.name").startsWith("Mac")) {
                val result = ProcessBuilder("iconutil", "-c", "icns", set.path, "-o", File(icons, "kingdoms.icns").path).inheritIO().start().waitFor()
                assertEquals(0, result, "iconutil failed")
            }
        }
    }

    /** Web: the tab icon (the desktop tile) and Safari's home-screen icon (opaque; iOS rounds it itself). */
    @Test
    fun webIcons() {
        val favicon = render(192) { macTile() }
        val touch = render(180) { drawAppIcon(zoom = fullBleed) }
        val pixels = Image.makeFromEncoded(touch).toComposeImageBitmap().toPixelMap()
        for (y in 0 until 180 step 4) for (x in 0 until 180 step 4) assertEquals(1f, pixels[x, y].alpha, "transparent at $x,$y")
        save("web-192.png", favicon)
        save("web-touch-180.png", touch)
        if (write) {
            val dir = File(root, "composeApp/src/wasmJsMain/resources").apply { mkdirs() }
            File(dir, "icon-192.png").writeBytes(favicon)
            File(dir, "apple-touch-icon.png").writeBytes(touch)
        }
    }

    /** How the icon looks under the usual masks and at launcher sizes: look at `build/icons/preview.png`. */
    @Test
    fun preview() {
        val sizes = listOf(192, 96, 48)
        val png = render(3 * 220 + 20, 4 * 220 + 20, background = true) {
            sizes.forEachIndexed { row, s ->
                fun at(col: Int, draw: DrawScope.() -> Unit) = translateTo(Offset(20f + col * 220f, 20f + row * 220f), s, draw)
                // A launcher shows the middle 72 of the 108 units, masked.
                at(0) { clipPath(circle(s)) { zoomed(GRID / 72f) { drawAppIcon() } } }
                at(1) { clipPath(squircle(s)) { drawAppIcon(zoom = fullBleed) } }
                at(2) { macTile() }
            }
            translateTo(Offset(20f, 20f + 3 * 220f), 192) { drawAppIcon(IconLayer.Foreground) }
        }
        save("preview.png", png)
    }

    private fun DrawScope.translateTo(at: Offset, s: Int, draw: DrawScope.() -> Unit) {
        drawContext.canvas.save()
        drawContext.canvas.translate(at.x, at.y)
        val old = drawContext.size
        drawContext.size = Size(s.toFloat(), s.toFloat())
        draw()
        drawContext.size = old
        drawContext.canvas.restore()
    }

    private fun DrawScope.zoomed(factor: Float, draw: DrawScope.() -> Unit) =
        withTransform({ scale(factor, factor, pivot = center) }) { draw() }

    private fun circle(s: Int) = Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, s.toFloat(), s.toFloat())) }

    private fun squircle(s: Int) = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, s.toFloat(), s.toFloat(), CornerRadius(s * 0.225f)))
    }

    /** macOS: the tile is 824 of 1024 with rounded corners, on transparency. */
    private fun DrawScope.macTile() {
        val s = size.minDimension
        val inset = s * 100f / 1024f
        val tile = s - 2 * inset
        drawContext.canvas.save()
        drawContext.canvas.translate(inset, inset)
        val old = drawContext.size
        drawContext.size = Size(tile, tile)
        clipPath(squircle(tile.toInt())) { drawAppIcon(zoom = fullBleed) }
        drawContext.size = old
        drawContext.canvas.restore()
    }

    private fun hasKingdomBlue(pixels: androidx.compose.ui.graphics.PixelMap): Boolean {
        val blue = Palette.kingdom(0)
        for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) {
            val c = pixels[x, y]
            if (kotlin.math.abs(c.red - blue.red) + kotlin.math.abs(c.green - blue.green) + kotlin.math.abs(c.blue - blue.blue) < 0.06f) return true
        }
        return false
    }

    private fun render(w: Int, h: Int = w, background: Boolean = false, draw: DrawScope.() -> Unit): ByteArray =
        ImageComposeScene(w, h, density = Density(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                if (background) drawRect(androidx.compose.ui.graphics.Color(0xFFE9E4DA))
                draw()
            }
        }.use { it.render().encodeToData(EncodedImageFormat.PNG)!!.bytes }

    private fun save(name: String, png: ByteArray) = File(out, name).writeBytes(png)
}
