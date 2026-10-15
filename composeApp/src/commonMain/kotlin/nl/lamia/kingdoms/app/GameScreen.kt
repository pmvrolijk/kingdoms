package nl.lamia.kingdoms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

/** A pending long-press move: how many armies to send from [from] to [to]. */
private data class Picker(val from: Int, val to: Int, val armies: Int)

private val Ai = HeuristicAi()

/** Pause before each AI move, so the player can follow it; ending a turn is quicker. */
private const val AI_MOVE_MILLIS = 450L
private const val AI_END_TURN_MILLIS = 200L

/** One game: the map, the turn HUD, the move picker and the end-of-game panel. */
@Composable
fun GameScreen(
    start: GameState,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
    /** Every new state, after each applied action. */
    onChange: (GameState) -> Unit = {},
) {
    var game by remember(start) { mutableStateOf(start) }
    var selected by remember(start) { mutableStateOf<Int?>(null) }
    var picker by remember(start) { mutableStateOf<Picker?>(null) }
    var log by remember(start) { mutableStateOf(listOf<String>()) }
    /** The last AI move, drawn as an arrow until the player acts. */
    var lastMove by remember(start) { mutableStateOf<Arrow?>(null) }

    fun record(next: GameState, events: List<Event>) {
        game = next
        onChange(next)
        log = (log + events.mapNotNull { describe(next, it) }).takeLast(5)
    }

    fun submit(action: Action) {
        lastMove = null
        when (val outcome = Rules.apply(game, action)) {
            is Outcome.Applied -> record(outcome.state, outcome.events)
            is Outcome.Rejected -> log = (log + "Can't do that: ${outcome.reason}").takeLast(5)
        }
    }

    // AI turns play one action at a time; every new state relaunches this effect.
    LaunchedEffect(game) {
        if (game.isOver || game.isHumanTurn) return@LaunchedEffect
        val action = Ai.decide(game)
        delay(if (action is Action.Move) AI_MOVE_MILLIS else AI_END_TURN_MILLIS)
        val played = step(game, Ai)
        played.events.firstNotNullOfOrNull { it.arrow() }?.let { lastMove = it }
        record(played.state, played.events)
    }

    fun canMove(from: Int?, to: Int?) = from != null && to != null && game.isHumanTurn &&
        game.movable(from) > 0 && to in game.map.provinces[from].neighbours

    Box(modifier) {
        MapView(
            game,
            selected,
            picker?.let { Arrow(it.from, it.to, game.current) } ?: lastMove,
            onTap = { p ->
                val from = selected
                picker = null
                if (canMove(from, p)) {
                    submit(Action.Move(from!!, p!!))
                    selected = from.takeIf { game.movable(it) > 0 }
                } else {
                    selected = p.takeIf { it != from } // tapping the selection again clears it
                }
            },
            onLongPress = { p ->
                val from = selected
                if (canMove(from, p)) picker = Picker(from!!, p!!, game.movable(from))
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selected?.let { BasicText(describeProvince(game, it), style = Label, modifier = Modifier.panel()) }
            picker?.let { pick ->
                MovePicker(game, pick, onChange = { picker = it }, onGo = {
                    picker = null
                    submit(Action.Move(pick.from, pick.to, pick.armies))
                    selected = pick.from.takeIf { game.movable(it) > 0 }
                })
            }
            if (log.isNotEmpty()) BasicText(log.joinToString("\n"), style = Label.copy(fontSize = 12.sp), modifier = Modifier.panel())
            TurnBar(
                game,
                onEndTurn = {
                    selected = null
                    picker = null
                    submit(Action.EndTurn)
                },
                onSkip = {
                    val played = playUntilHumanTurn(game, Ai)
                    lastMove = null
                    record(played.state, played.events)
                },
            )
        }

        if (game.isOver) GameOver(game, onNewGame, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun TurnBar(game: GameState, onEndTurn: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.panel(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val k = game.kingdoms[game.current]
            Swatch(k.id)
            BasicText("Round ${game.round} · ${if (k.human) "your turn" else "${k.name}'s turn"}", style = Label.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.size(8.dp))
            if (game.isHumanTurn || game.isOver) {
                Chip("End turn", enabled = game.isHumanTurn, onClick = onEndTurn)
            } else {
                Chip("Skip", onClick = onSkip)
            }
        }
        for (k in game.kingdoms) {
            val provinces = game.provincesOf(k.id)
            val status = if (provinces.isEmpty()) {
                "fallen"
            } else {
                "${provinces.size} provinces · ${provinces.sumOf { game.holdings[it].armies }} armies · +${game.growthOf(k.id)}/round"
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Swatch(k.id)
                val who = if (k.human) "you" else Personality.forSeat(k.id).name
                BasicText("${k.name} ($who): $status", style = Label.copy(fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun MovePicker(game: GameState, pick: Picker, onChange: (Picker) -> Unit, onGo: () -> Unit) {
    val max = game.movable(pick.from)
    Row(Modifier.panel(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val attack = game.owner(pick.to) != game.current
        val chance = if (attack) " · ${(game.winChance(pick.from, pick.to, pick.armies) * 100).roundToInt()}%" else ""
        BasicText("${if (attack) "Attack" else "Move to"} #${pick.to} with ${pick.armies} of $max$chance", style = Label)
        Chip("−", enabled = pick.armies > 1) { onChange(pick.copy(armies = pick.armies - 1)) }
        Chip("+", enabled = pick.armies < max) { onChange(pick.copy(armies = pick.armies + 1)) }
        Chip("Go", onClick = onGo)
    }
}

@Composable
private fun GameOver(game: GameState, onNewGame: () -> Unit, modifier: Modifier) {
    val winner = game.winner
    val human = game.kingdoms.firstOrNull { it.human }
    val title = when {
        winner != null && winner == human?.id -> "Victory!"
        winner != null -> "${game.kingdoms[winner].name} wins"
        else -> "Defeat"
    }
    Column(modifier.panel().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(title, style = Label.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold))
        BasicText("after ${game.round} rounds", style = Label)
        Chip("New game", onClick = onNewGame)
    }
}

@Composable
private fun Swatch(kingdom: Int) {
    Box(Modifier.size(12.dp).background(Palette.kingdom(kingdom), CircleShape))
}

private fun describeProvince(game: GameState, id: Int): String {
    val p = game.map.provinces[id]
    val h = game.holdings[id]
    val owner = h.owner?.let { game.kingdoms[it].name } ?: "Neutral"
    val movable = game.movable(id).takeIf { it > 0 }?.let { ", $it can move" }.orEmpty()
    val coast = if (p.coastal) ", coastal" else ""
    val borders = p.neighbours.mapNotNull { n -> game.map.border(id, n)?.feature?.let { "#$n ${it.name} ×${it.defence}" } }
    val featureLine = if (borders.isEmpty()) "" else "\nborders: ${borders.joinToString(", ")}"
    val hint = if (game.isHumanTurn && game.movable(id) > 0) {
        "\nClick a neighbour to send ${game.movable(id)} · long-press or right-click it to choose how many"
    } else {
        ""
    }
    return "#$id ${p.terrain.name}$coast · ${p.resources.joinToString(" + ")} · +${p.yield}/round\n" +
        "$owner · ${h.armies} armies$movable$featureLine$hint"
}

private fun Event.arrow(): Arrow? = when (this) {
    is Event.Moved -> Arrow(from, to, kingdom)
    is Event.Fought -> Arrow(from, to, kingdom)
    else -> null
}

private fun describe(game: GameState, event: Event): String? {
    fun name(k: Int?) = k?.let { game.kingdoms[it].name } ?: "neutral"
    return when (event) {
        is Event.Fought -> {
            val r = event.result
            val bonus = if (event.defence > 1.0) " ×${event.defence}" else ""
            if (r.attackerWon) {
                "${name(event.kingdom)} took #${event.to} from ${name(event.defender)}: ${event.attackers} vs ${event.defenders}$bonus, ${r.attackerSurvivors} survive"
            } else {
                "${name(event.kingdom)} failed at #${event.to} (${name(event.defender)}): ${event.attackers} vs ${event.defenders}$bonus, ${r.defenderSurvivors} hold"
            }
        }
        is Event.Eliminated -> "${name(event.kingdom)} has fallen to ${name(event.by)}"
        is Event.Won -> "${name(event.kingdom)} rules the land"
        is Event.RoundEnded -> "Round ${event.round} ended"
        is Event.Moved, is Event.TurnStarted -> null
    }
}
