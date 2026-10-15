package nl.lamia.kingdoms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.lamia.kingdoms.core.ai.HeuristicAi
import nl.lamia.kingdoms.core.ai.Personality
import nl.lamia.kingdoms.core.ai.playUntilHumanTurn
import nl.lamia.kingdoms.core.ai.step
import nl.lamia.kingdoms.core.rules.Action
import nl.lamia.kingdoms.core.rules.Event
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.Outcome
import nl.lamia.kingdoms.core.rules.Rules
import kotlin.math.roundToInt

private val Ai = HeuristicAi()

/** Pause before each AI move, so the player can follow it; ending a turn is quicker. */
private const val AI_MOVE_MILLIS = 450L
private const val AI_END_TURN_MILLIS = 200L

/**
 * One game: the map with the HUD around it — kingdom strength (top left), turn and log (top
 * right), the selected province's card (bottom, on the side away from it), the amount picker next
 * to its target, and the end-of-game panel.
 */
@Composable
fun GameScreen(
    start: GameState,
    /** Back to the new-game screen. */
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
    /** The same setup again. */
    onPlayAgain: () -> Unit = onNewGame,
    /** Every new state, after each applied action. */
    onChange: (GameState) -> Unit = {},
    /** Opens the game menu; no menu button when null. */
    onMenu: (() -> Unit)? = null,
) {
    var game by remember(start) { mutableStateOf(start) }
    var selected by remember(start) { mutableStateOf<Int?>(null) }
    var pick by remember(start) { mutableStateOf<Pick?>(null) }
    var log by remember(start) { mutableStateOf(listOf<LogEntry>()) }
    var clashes by remember(start) { mutableStateOf(listOf<Clash>()) }
    var now by remember { mutableLongStateOf(0L) }
    /** The AI move just played, drawn as an arrow; ending a turn clears it, so none is left on the player's turn. */
    var lastMove by remember(start) { mutableStateOf<Arrow?>(null) }
    val cameraState = remember(start.map) { CameraState() }
    val camera = cameraState.camera

    fun record(next: GameState, events: List<Event>) {
        game = next
        onChange(next)
        log = (log + events.mapNotNull { describe(next, it) }).takeLast(5)
        clashes = clashes + events.filterIsInstance<Event.Fought>().map { Clash(it.to, it.kingdom, it.defender, it.result.attackerWon) }
    }

    // Drive the clash animations frame by frame while any are playing.
    LaunchedEffect(clashes.isNotEmpty()) {
        while (clashes.isNotEmpty()) {
            withFrameNanos { t ->
                now = t
                clashes = clashes.map { if (it.start == 0L) it.copy(start = t) else it }.filter { t - it.start < CLASH_NANOS }
            }
        }
    }

    fun submit(action: Action) {
        lastMove = null
        when (val outcome = Rules.apply(game, action)) {
            is Outcome.Applied -> record(outcome.state, outcome.events)
            is Outcome.Rejected -> log = (log + LogEntry(null, AnnotatedString("Can't do that: ${outcome.reason}"))).takeLast(5)
        }
    }

    // AI turns play one action at a time; every new state relaunches this effect.
    LaunchedEffect(game) {
        if (game.isOver || game.isHumanTurn) return@LaunchedEffect
        val action = Ai.decide(game)
        // Let a battle's animation play out before the next move.
        val settle = if (clashes.isNotEmpty()) 350L else 0L
        delay(settle + if (action is Action.Move) AI_MOVE_MILLIS else AI_END_TURN_MILLIS)
        val played = step(game, Ai)
        lastMove = played.events.firstNotNullOfOrNull { it.arrow() }
        record(played.state, played.events)
    }

    fun canMove(from: Int?, to: Int?) = from != null && to != null && game.isHumanTurn &&
        game.movable(from) > 0 && to in game.map.provinces[from].neighbours

    BoxWithConstraints(modifier) {
        MapView(
            game,
            selected,
            pick?.let { Arrow(it.from, it.to, game.current) } ?: lastMove,
            cameraState = cameraState,
            clashes = clashes,
            now = now,
            onTap = { p ->
                val from = selected
                pick = null
                if (canMove(from, p)) {
                    submit(Action.Move(from!!, p!!))
                    selected = from.takeIf { game.movable(it) > 0 }
                } else {
                    selected = p.takeIf { it != from } // tapping the selection again clears it
                }
            },
            onLongPress = { p ->
                val from = selected
                if (canMove(from, p)) pick = Pick(from!!, p!!, game.movable(from))
            },
            modifier = Modifier.fillMaxSize(),
        )

        val edge = Modifier.safeDrawingPadding().padding(10.dp)
        StrengthPanel(game, edge.align(Alignment.TopStart))
        Column(edge.align(Alignment.TopEnd), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            TurnPanel(
                game,
                onEndTurn = {
                    selected = null
                    pick = null
                    submit(Action.EndTurn)
                },
                onSkip = {
                    val played = playUntilHumanTurn(game, Ai)
                    lastMove = null
                    record(played.state, played.events)
                },
                onMenu = onMenu,
            )
            LogPanel(log)
        }

        selected?.let { id ->
            val site = camera?.toScreen(game.map.provinces[id].site)
            val onLeft = site == null || site.x < constraints.maxWidth / 2
            ProvinceCard(game, id, edge.align(if (onLeft) Alignment.BottomEnd else Alignment.BottomStart))
        }

        pick?.let { current ->
            val cam = camera
            if (cam != null) {
                val anchor = cam.toScreen(game.map.provinces[current.to].site)
                MovePicker(
                    game,
                    current,
                    onChange = { pick = it },
                    onCancel = { pick = null },
                    onGo = {
                        pick = null
                        submit(Action.Move(current.from, current.to, current.armies))
                        selected = current.from.takeIf { game.movable(it) > 0 }
                    },
                    modifier = Modifier.anchoredNear(anchor, gap = with(LocalDensity.current) { 56.dp.toPx() }),
                )
            }
        }

        if (game.isOver) GameOver(game, onPlayAgain, onNewGame, Modifier.align(Alignment.Center))
    }
}

/** The result: who won, after how many rounds, the final strength, and what next. */
@Composable
private fun GameOver(game: GameState, onPlayAgain: () -> Unit, onNewGame: () -> Unit, modifier: Modifier) {
    val winner = game.winner
    val human = game.kingdoms.firstOrNull { it.human }
    val title = when {
        winner != null && winner == human?.id -> "Victory!"
        winner != null -> "${game.kingdoms[winner].name} wins"
        else -> "Defeat"
    }
    Column(
        modifier.width(300.dp).panel().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(title, style = Label.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = winner?.let(game::colourOf)?.darker(0.2f) ?: Palette.Ink))
        BasicText("after ${game.round} rounds", style = Label)
        ActionButton("Play again", human?.let { game.colourOf(it.id) } ?: Palette.Ink, onClick = onPlayAgain)
        ActionButton("New game…", Palette.Ink.lighter(0.3f), onClick = onNewGame)
    }
}

private fun Event.arrow(): Arrow? = when (this) {
    is Event.Moved -> Arrow(from, to, kingdom)
    is Event.Fought -> Arrow(from, to, kingdom)
    else -> null
}

/** An event as a line of the chronicle, with kingdom names in their colours and the numbers dimmed. */
private fun describe(game: GameState, event: Event): LogEntry? {
    fun AnnotatedString.Builder.kingdom(k: Int) =
        withStyle(SpanStyle(color = game.colourOf(k).darker(0.25f), fontWeight = FontWeight.Bold)) { append(game.kingdoms[k].name) }
    fun AnnotatedString.Builder.place(p: Int) =
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(game.map.provinces[p].name) }
    fun AnnotatedString.Builder.dim(text: String) = withStyle(SpanStyle(color = Palette.Ink.copy(alpha = 0.55f))) { append(text) }
    return when (event) {
        is Event.Fought -> LogEntry(game.colourOf(event.kingdom), buildAnnotatedString {
            kingdom(event.kingdom)
            if (event.result.attackerWon) {
                append(" takes ")
                place(event.to)
                event.defender?.let { append(" from "); kingdom(it) }
            } else {
                append(" is defeated at ")
                place(event.to)
            }
            dim("  ${event.attackers} vs ${event.defenders}")
        })
        is Event.Eliminated -> LogEntry(game.colourOf(event.by), buildAnnotatedString {
            kingdom(event.kingdom)
            append(" falls to ")
            kingdom(event.by)
        })
        is Event.Won -> LogEntry(game.colourOf(event.kingdom), buildAnnotatedString {
            kingdom(event.kingdom)
            append(" rules the land")
        })
        is Event.Moved, is Event.TurnStarted, is Event.RoundEnded -> null
    }
}
