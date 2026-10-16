package nl.lamia.kingdoms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import nl.lamia.kingdoms.app.App
import nl.lamia.kingdoms.app.FileSaveStore

class MainActivity : ComponentActivity() {
    private var speaker: AndroidSpeaker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Full screen: a landscape phone is only ~360 dp tall. A swipe from the edge shows the bars for a moment.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val store = FileSaveStore(filesDir.path)
        val settings = FileSaveStore(filesDir.path, "settings.json")
        val speaker = AndroidSpeaker(this).also { speaker = it }
        setContent { App(store = store, speaker = speaker, settings = settings) }
    }

    override fun onDestroy() {
        speaker?.release()
        super.onDestroy()
    }
}
