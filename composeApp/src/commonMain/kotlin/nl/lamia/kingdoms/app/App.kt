package nl.lamia.kingdoms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.NewGame

/**
 * The app: the new-game screen, then the game. [initial] and [playing] let tests start from a
 * known setup, straight into play.
 */
@Composable
fun App(
    initial: Setup = remember { Setup(seed = Setup.randomSeed()) },
    playing: Boolean = false,
    onGameChange: (GameState) -> Unit = {},
) {
    var setup by remember { mutableStateOf(initial) }
    var inGame by remember { mutableStateOf(playing) }
    var restarts by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Palette.SeaDeep)) {
        if (!inGame) {
            NewGameScreen(setup, onChange = { setup = it }, onStart = {
                restarts = 0
                inGame = true
            }, Modifier.fillMaxSize())
        } else {
            val map = remember(setup.config) { MapGenerator.generate(setup.config) }
            val game = remember(map, setup.seats, setup.turnOrder, restarts) {
                NewGame.create(map, setup.seats, seed = setup.seed + restarts, turnOrder = setup.turnOrder)
            }
            fun newGame() {
                menuOpen = false
                setup = setup.copy(seed = Setup.randomSeed())
                inGame = false
            }
            fun restart() {
                menuOpen = false
                restarts++
            }
            GameScreen(
                game,
                onNewGame = ::newGame,
                onPlayAgain = ::restart,
                modifier = Modifier.fillMaxSize(),
                onChange = onGameChange,
                onMenu = { menuOpen = !menuOpen },
            )
            if (menuOpen) {
                Column(
                    Modifier.align(Alignment.Center).width(240.dp).panel().padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText("Menu", style = Label.copy(fontWeight = FontWeight.Bold))
                    ActionButton("Resume", Palette.kingdom(setup.seats.first().colour)) { menuOpen = false }
                    ActionButton("Restart", Palette.Ink.lighter(0.3f), onClick = ::restart)
                    ActionButton("New game…", Palette.Ink.lighter(0.3f), onClick = ::newGame)
                    BasicText(
                        "seed ${setup.seed} · ${map.provinces.size} provinces",
                        style = Small.copy(color = Palette.Ink.copy(alpha = 0.6f)),
                    )
                }
            }
        }
    }
}
