package nl.lamia.kingdoms.app

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    hideLoading()
    ComposeViewport {
        App(
            store = remember { WebSaveStore("kingdoms.save") },
            speaker = remember { WebSpeaker() },
            settings = remember { WebSaveStore("kingdoms.settings") },
        )
    }
}

/** index.html shows "Loading…" while the Wasm module downloads. */
private fun hideLoading(): Unit = js("document.getElementById('loading')?.remove()")
