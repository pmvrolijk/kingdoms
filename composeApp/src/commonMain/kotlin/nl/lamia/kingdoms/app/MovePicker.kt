package nl.lamia.kingdoms.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lamia.kingdoms.core.rules.Battle
import nl.lamia.kingdoms.core.rules.GameState
import kotlin.math.roundToInt

/** A pending chosen-amount move: how many armies to send from [from] to [to]. */
data class Pick(val from: Int, val to: Int, val armies: Int)

/** The amount picker: a slider from 1 to all movable, with the odds for an attack. */
@Composable
fun MovePicker(game: GameState, pick: Pick, onChange: (Pick) -> Unit, onCancel: () -> Unit, onGo: () -> Unit, modifier: Modifier = Modifier) {
    val max = game.movable(pick.from)
    val colour = game.colourOf(game.current)
    val target = game.holdings[pick.to]
    val attack = target.owner != game.current
    Column(modifier.panel().width(250.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val whom = target.owner?.let { game.kingdoms[it].name } ?: "neutral"
        BasicText(
            if (attack) "Attack ${game.map.provinces[pick.to].name} ($whom, ${target.armies})" else "Move to ${game.map.provinces[pick.to].name}",
            style = Label.copy(fontWeight = FontWeight.Bold),
        )
        AmountSlider(pick.armies, max, colour, onChange = { onChange(pick.copy(armies = it)) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Send ${pick.armies} of $max", style = Small)
            Spacer(Modifier.weight(1f))
            if (attack) {
                val chance = game.winChance(pick.from, pick.to, pick.armies)
                BasicText("${(chance * 100).roundToInt()}% to win", style = Small.copy(fontWeight = FontWeight.Bold))
            }
        }
        if (attack) {
            val defence = target.armies * game.defence(pick.from, pick.to)
            val survivors = if (pick.armies > defence) Battle.survivors(pick.armies, pick.armies.toDouble(), defence) else null
            BasicText(
                survivors?.let { "about $it would survive a win" } ?: "outnumbered: a win needs luck",
                style = Small.copy(color = Palette.Ink.copy(alpha = 0.7f)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Cancel", Palette.Ink.lighter(0.3f), modifier = Modifier.weight(1f), onClick = onCancel)
            ActionButton(if (attack) "Attack" else "Move", colour, modifier = Modifier.weight(1f).testTag("confirm-move"), onClick = onGo)
        }
    }
}

/** A whole-number slider over 1..[max]; drag or tap. Exposes progress semantics for tests and accessibility. */
@Composable
fun AmountSlider(value: Int, max: Int, colour: Color, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(onChange)
    val text = rememberTextMeasurer()
    val pad = 14.dp
    Canvas(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .testTag("amount")
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 1f..maxOf(max, 1).toFloat(), steps = maxOf(0, max - 2))
                setProgress { v -> current(v.roundToInt().coerceIn(1, maxOf(max, 1))); true }
            }
            .pointerInput(max) {
                fun valueAt(x: Float): Int {
                    val p = pad.toPx()
                    val t = ((x - p) / (size.width - 2 * p)).coerceIn(0f, 1f)
                    return (1 + t * (max - 1)).roundToInt()
                }
                detectTapGestures { current(valueAt(it.x)) }
            }
            .pointerInput(max) {
                detectHorizontalDragGestures { change, _ ->
                    val p = pad.toPx()
                    val t = ((change.position.x - p) / (size.width - 2 * p)).coerceIn(0f, 1f)
                    current((1 + t * (max - 1)).roundToInt())
                    change.consume()
                }
            },
    ) {
        val p = pad.toPx()
        val y = size.height / 2
        val t = if (max <= 1) 1f else (value - 1f) / (max - 1f)
        val x = p + t * (size.width - 2 * p)
        val track = 6.dp.toPx()
        drawRoundRect(Palette.Neutral.darker(0.1f), Offset(p, y - track / 2), Size(size.width - 2 * p, track), CornerRadius(track / 2))
        drawRoundRect(colour, Offset(p, y - track / 2), Size(x - p, track), CornerRadius(track / 2))
        val r = 12.dp.toPx()
        drawCircle(colour, r, Offset(x, y))
        drawCircle(colour.darker(0.45f), r, Offset(x, y), style = Stroke(1.5.dp.toPx()))
        val label = text.measure(value.toString(), Label.copy(color = Palette.Parchment, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        drawText(label, topLeft = Offset(x - label.size.width / 2f, y - label.size.height / 2f))
    }
}
