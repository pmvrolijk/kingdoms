package nl.lamia.kingdoms.app

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kingdoms",
        icon = remember { AppIconPainter() },
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        App(
            store = remember { FileSaveStore(saveDirectory()) },
            speaker = remember { JvmSpeaker() },
            settings = remember { FileSaveStore(saveDirectory(), "settings.json") },
        )
    }
}

/** macOS: Application Support; Linux: the XDG data directory. */
private fun saveDirectory(): String {
    val home = System.getProperty("user.home")
    if (System.getProperty("os.name").startsWith("Mac")) return "$home/Library/Application Support/Kingdoms"
    val data = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotEmpty() } ?: "$home/.local/share"
    return "$data/kingdoms"
}
