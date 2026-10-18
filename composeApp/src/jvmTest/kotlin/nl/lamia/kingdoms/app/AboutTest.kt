package nl.lamia.kingdoms.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

private const val LICENCE_LINE = "GNU Affero General Public License, version 3 or later"

/** The About panel (Design §6.5): copyright, licence and source links, from both places. */
@OptIn(ExperimentalTestApi::class)
class AboutTest {
    private class Recorder : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) {
            opened += uri
        }
    }

    @Test
    fun opensFromTheNewGameScreenWithTheNoticeAndLinks() = runUiTest {
        val uris = Recorder()
        setContent { CompositionLocalProvider(LocalUriHandler provides uris) { App(Setup()) } }
        // The settings end with the copyright line; the panel repeats it with the licence.
        onNodeWithText(COPYRIGHT).assertExists()
        onNodeWithText("About").performClick()
        onNodeWithText(LICENCE_LINE, substring = true).assertExists()
        onNodeWithText("Licence").performClick()
        onNodeWithText("Source code").performClick()
        assertEquals(listOf(LICENSE_URL, SOURCE_URL), uris.opened)
        onNodeWithText("Close").performClick()
        onAllNodesWithText(LICENCE_LINE, substring = true).assertCountEquals(0)
    }

    @Test
    fun opensFromTheGameMenu() = runUiTest {
        setContent { App(Setup(), playing = true) }
        onNodeWithText("Menu").performClick()
        onNodeWithText("About").performClick()
        onNodeWithText(LICENCE_LINE, substring = true).assertExists()
    }

    @Test
    fun snapshot() {
        ImageComposeScene(1280, 800) { AboutScreen(onClose = {}, Modifier.fillMaxSize()) }.use { scene ->
            val file = File("build/snapshots/about.png").apply { parentFile.mkdirs() }
            file.writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
