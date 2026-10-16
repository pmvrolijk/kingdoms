package nl.lamia.kingdoms.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@Suppress("FunctionName", "unused") // called from Swift
fun MainViewController() = ComposeUIViewController {
    App(
        store = remember { FileSaveStore(saveDirectory()) },
        speaker = remember { IosSpeaker() },
        settings = remember { FileSaveStore(saveDirectory(), "settings.json") },
    )
}

/** The app's Application Support directory: kept, backed up, not shown to the user. */
private fun saveDirectory(): String =
    NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
