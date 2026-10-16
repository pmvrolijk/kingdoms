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
import nl.lamia.kingdoms.core.map.GameMap
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.NewGame

/**
 * The app: the new-game screen, then the game. [initial] and [playing] let tests start from a
 * known setup, straight into play. Every new state is saved to [store]; a finished game clears it
 * (Design §7).
 */
@Composable
fun App(
    initial: Setup = remember { Setup(seed = Setup.randomSeed()) },
    playing: Boolean = false,
    store: SaveStore = SaveStore.None,
    onGameChange: (GameState) -> Unit = {},
) {
    var setup by remember { mutableStateOf(initial) }
    var restarts by remember { mutableStateOf(0) }
    /** The game in play as it started (GameScreen plays on from it); null on the new-game screen. */
    var start by remember { mutableStateOf(if (playing) create(initial, restarts = 0) else null) }
    /** The game Continue resumes: the save, kept current while playing. */
    var saved by remember { mutableStateOf(store.load()) }
    var menuOpen by remember { mutableStateOf(false) }

    fun keep(state: GameState) {
        if (state.isOver) {
            saved = null
            store.clear()
        } else {
            saved = SavedGame(setup, restarts, state).also(store::save)
        }
    }
    fun begin(state: GameState) {
        menuOpen = false
        start = state
        keep(state)
    }

    Box(Modifier.fillMaxSize().background(Palette.SeaDeep)) {
        val game = start
        if (game == null) {
            NewGameScreen(
                setup,
                onChange = { setup = it },
                onStart = {
                    restarts = 0
                    begin(create(setup, restarts))
                },
                Modifier.fillMaxSize(),
                resume = saved?.state,
                onContinue = {
                    saved?.let {
                        setup = it.setup
                        restarts = it.restarts
                        begin(it.state)
                    }
                },
            )
        } else {
            fun newGame() {
                menuOpen = false
                setup = setup.copy(seed = Setup.randomSeed())
                start = null
            }
            fun restart() {
                restarts++
                begin(create(setup, restarts, game.map))
            }
            GameScreen(
                game,
                onNewGame = ::newGame,
                onPlayAgain = ::restart,
                modifier = Modifier.fillMaxSize(),
                onChange = {
                    keep(it)
                    onGameChange(it)
                },
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
                        "seed ${setup.seed} · ${game.map.provinces.size} provinces",
                        style = Small.copy(color = Palette.Ink.copy(alpha = 0.6f)),
                    )
                }
            }
        }
    }
}

/** A new game from [setup]; each restart gets the next game seed on the same map. */
private fun create(setup: Setup, restarts: Int, map: GameMap = MapGenerator.generate(setup.config)): GameState =
    NewGame.create(map, setup.seats, seed = setup.seed + restarts, turnOrder = setup.turnOrder)
