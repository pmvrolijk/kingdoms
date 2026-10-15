package nl.lamia.kingdoms.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lamia.kingdoms.core.ai.Personality
import nl.lamia.kingdoms.core.map.Resource
import nl.lamia.kingdoms.core.rules.GameState
import kotlin.math.max
import kotlin.math.roundToInt

/** One part of a [StackedBar]. [outlined] marks the player's own share. */
data class Segment(val value: Float, val colour: Color, val outlined: Boolean = false)

/** A rounded bar split into parts proportional to their values. */
@Composable
fun StackedBar(segments: List<Segment>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        val radius = CornerRadius(size.height / 2)
        val shape = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, radius)) }
        drawPath(shape, Palette.Neutral.darker(0.1f))
        if (total > 0f) {
            clipPath(shape) {
                var x = 0f
                for (s in segments) {
                    val w = size.width * s.value / total
                    drawRect(s.colour, Offset(x, 0f), Size(w, size.height))
                    drawRect(s.colour.lighter(0.25f), Offset(x, 0f), Size(w, size.height * 0.35f))
                    if (s.outlined && w > 2f) {
                        drawRect(Palette.Selected, Offset(x + 1f, 1f), Size(w - 2f, size.height - 2f), style = Stroke(2.dp.toPx()))
                    }
                    x += w
                    if (x < size.width - 1f) drawLine(Palette.Parchment, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                }
            }
        }
        drawPath(shape, Palette.Ink.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
    }
}

/** Kingdoms' shares of production, territory and armies (Status M5: relative strength bars). */
@Composable
fun StrengthPanel(game: GameState, modifier: Modifier = Modifier) {
    val human = game.kingdoms.firstOrNull { it.human }?.id
    val alive = game.alive
    Column(modifier.panel().width(250.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        MetricRow("Production", { Icon(16.dp) { resourceIcon(Resource.Grain) } }, game, alive, human, { game.growthOf(it) }) { "+$it" }
        MetricRow("Territory", { Icon(16.dp) { terrainIcon(nl.lamia.kingdoms.core.map.Terrain.Plains) } }, game, alive, human, { game.provincesOf(it).size }) { "$it" }
        MetricRow("Armies", { Icon(16.dp) { armyIcon(human?.let(game::colourOf) ?: Palette.Neutral) } }, game, alive, human, { k ->
            game.provincesOf(k).sumOf { game.holdings[it].armies }
        }) { "$it" }
    }
}

@Composable
private fun MetricRow(
    label: String,
    icon: @Composable () -> Unit,
    game: GameState,
    alive: List<Int>,
    human: Int?,
    value: (Int) -> Int,
    show: (Int) -> String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon()
        BasicText(label, style = Small, modifier = Modifier.width(62.dp))
        StackedBar(
            alive.map { Segment(value(it).toFloat(), game.colourOf(it), outlined = it == human) },
            Modifier.weight(1f).height(12.dp),
        )
        BasicText(
            human?.takeIf { it in alive }?.let { show(value(it)) } ?: "–",
            style = Small.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
            modifier = Modifier.width(28.dp),
        )
    }
}

/** Round, this round's turn order (current kingdom enlarged), whose move it is, End turn / Skip. */
@Composable
fun TurnPanel(game: GameState, onEndTurn: () -> Unit, onSkip: () -> Unit, onMenu: (() -> Unit)?, modifier: Modifier = Modifier) {
    val k = game.kingdoms[game.current]
    Column(modifier.panel().width(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText("Round ${game.round}", style = Label.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            for (id in game.order.filter { game.isAlive(it) }) {
                val current = id == game.current
                Box(
                    Modifier.size(if (current) 16.dp else 11.dp)
                        .background(game.colourOf(id), CircleShape)
                        .border(if (current) 2.dp else 1.dp, if (current) Palette.Ink else Palette.Ink.copy(alpha = 0.4f), CircleShape),
                )
            }
            onMenu?.let { BasicText("Menu", style = Small, modifier = Modifier.padding(start = 4.dp).clickable(onClick = it)) }
        }
        val who = when {
            game.isOver -> "Game over"
            k.human -> "Your turn"
            else -> "${k.name} (${k.difficulty.name.lowercase()}, ${Personality.forSeat(k.id).name}) moves…"
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).background(game.colourOf(k.id), CircleShape))
            BasicText(who, style = Small)
        }
        if (game.isHumanTurn || game.isOver) {
            ActionButton("End turn", game.colourOf(k.id), enabled = game.isHumanTurn, onClick = onEndTurn)
        } else {
            ActionButton("Skip", Palette.Neutral.darker(0.15f), onClick = onSkip)
        }
    }
}

/** One line of the chronicle: the acting kingdom's colour (for its shield) and what happened. */
data class LogEntry(val colour: Color?, val text: AnnotatedString)

/** The last few events as a short chronicle, newest last and older ones faded. */
@Composable
fun LogPanel(entries: List<LogEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    val shown = entries.takeLast(3)
    Column(modifier.panel().width(270.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEachIndexed { n, entry ->
            val fade = if (n == shown.lastIndex) 1f else 0.6f
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(14.dp) { armyIcon((entry.colour ?: Palette.Neutral).copy(alpha = fade)) }
                BasicText(entry.text, style = Small.copy(color = Palette.Ink.copy(alpha = fade)))
            }
        }
    }
}

@Composable
fun ActionButton(text: String, colour: Color, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fill = if (enabled) colour else Palette.Neutral
    BasicText(
        text,
        style = Label.copy(
            color = if (enabled) Palette.Parchment else Palette.Ink.copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        modifier = modifier
            .fillMaxWidth()
            .background(fill, RoundedCornerShape(8.dp))
            .border(1.5.dp, fill.darker(0.4f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 7.dp),
    )
}

/**
 * Places the content centred above [anchor] (screen px) — or below it when there is no room
 * above — clamped inside the parent. Use on a child of a full-screen Box.
 */
fun Modifier.anchoredNear(anchor: Offset, gap: Float) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        val x = (anchor.x - placeable.width / 2).roundToInt().coerceIn(0, max(0, constraints.maxWidth - placeable.width))
        var y = (anchor.y - gap - placeable.height).roundToInt()
        if (y < 0) y = (anchor.y + gap).roundToInt()
        placeable.place(x, y.coerceIn(0, max(0, constraints.maxHeight - placeable.height)))
    }
}

val Small = Label.copy(fontSize = 12.sp)
