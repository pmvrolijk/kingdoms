package nl.lamia.kingdoms.app

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A battle to animate at [province]; [start] is the frame time it began, 0 until its first frame. */
data class Clash(val province: Int, val attacker: Int, val defender: Int?, val attackerWon: Boolean, val start: Long = 0L)

const val CLASH_NANOS = 900_000_000L

/**
 * A tasteful skirmish (Design §8): two swords swing in and cross, a small spark and puffs of
 * dust where they meet, then a ring in the winner's colour spreads from the army plate.
 * [plate] is the army plate's centre on screen; [progress] runs 0..1; [scale] is the banner scale.
 */
fun DrawScope.drawClash(plate: Offset, progress: Float, scale: Float, winner: Color) {
    val dp = 1.dp.toPx() * scale
    val cross = plate - Offset(0f, 30 * dp)

    // Swords: swing in over the first 35%, hold, fade out from 65%.
    val swing = easeOut((progress / 0.35f).coerceIn(0f, 1f))
    val swordAlpha = 1f - ((progress - 0.65f) / 0.3f).coerceIn(0f, 1f)
    if (swordAlpha > 0f) {
        val angle = -70f + 102f * swing
        sword(cross + Offset(-11 * dp, 15 * dp), angle, dp, swordAlpha)
        sword(cross + Offset(11 * dp, 15 * dp), -angle, dp, swordAlpha)
    }

    // Spark where the blades meet.
    val spark = ((progress - 0.3f) / 0.4f).coerceIn(0f, 1f)
    if (spark in 0.001f..0.999f) {
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4 + 0.3f
            val dir = Offset(cos(a), sin(a))
            val inner = cross + dir * ((3 + 6 * spark) * dp)
            val outer = cross + dir * ((6 + 12 * spark) * dp)
            drawLine(Color(0xFFFFE6A0).copy(alpha = 1f - spark), inner, outer, 2.5f * dp, cap = StrokeCap.Round)
        }
    }

    // Dust puffs at ground level beside and below the plate, drifting outward — never over the count.
    val dust = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)
    if (dust in 0.001f..0.999f) {
        for (k in 0 until 6) {
            val a = PI.toFloat() * (-0.1f + 0.24f * k) // from just right of the plate, under it, to its left
            val centre = plate + Offset(cos(a) * (20 + 12 * dust), sin(a) * (11 + 6 * dust)) * dp
            val r = (2.5f + 3f * dust) * dp
            val alpha = 0.85f * (1f - dust)
            drawCircle(Palette.Parchment.copy(alpha = alpha), r, centre)
            drawCircle(Palette.Ink.copy(alpha = alpha * 0.35f), r, centre, style = Stroke(1 * dp))
        }
    }

    // The winner's colour spreads from the plate.
    val ring = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f)
    if (ring in 0.001f..0.999f) {
        drawCircle(winner.copy(alpha = 0.85f * (1f - ring)), (12 + 22 * ring) * dp, plate, style = Stroke(3 * dp))
    }
}

/** A cartoon sword with its grip at [hilt], blade up, turned by [degrees]. */
private fun DrawScope.sword(hilt: Offset, degrees: Float, dp: Float, alpha: Float) = rotate(degrees, hilt) {
    val w = 4.5f * dp
    val length = 24 * dp
    val guardY = hilt.y - 3 * dp
    val tipY = guardY - length
    val steel = Color(0xFFDCE3EA).copy(alpha = alpha)
    val edge = Color(0xFF6B7480).copy(alpha = alpha)
    val blade = Path().apply {
        moveTo(hilt.x - w / 2, guardY)
        lineTo(hilt.x - w / 2, tipY + w)
        lineTo(hilt.x, tipY)
        lineTo(hilt.x + w / 2, tipY + w)
        lineTo(hilt.x + w / 2, guardY)
        close()
    }
    drawPath(blade, steel)
    drawRect(Color.White.copy(alpha = 0.7f * alpha), Offset(hilt.x - w / 2 + 0.8f * dp, tipY + w), Size(w * 0.3f, length - w - 1 * dp))
    drawPath(blade, edge, style = Stroke(1.2f * dp, join = StrokeJoin.Round))
    val gold = Color(0xFFD9A84B).copy(alpha = alpha)
    drawRect(Color(0xFF8A5F3C).copy(alpha = alpha), Offset(hilt.x - w * 0.35f, guardY), Size(w * 0.7f, 7 * dp))
    drawRoundRect(gold, Offset(hilt.x - w * 1.6f, guardY - 1.5f * dp), Size(w * 3.2f, 3 * dp), CornerRadius(1.5f * dp))
    drawRoundRect(gold.darker(0.4f), Offset(hilt.x - w * 1.6f, guardY - 1.5f * dp), Size(w * 3.2f, 3 * dp), CornerRadius(1.5f * dp), style = Stroke(1 * dp))
    drawCircle(gold, 2.2f * dp, Offset(hilt.x, guardY + 8 * dp))
}

private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
