package nl.lamia.kingdoms.app

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate

/*
 * Cel-shaded props (Design §8), drawn in board space. Each has a flat base tone, one hard-edged
 * light band on the lit (upper-left) side, and an outline in a darker shade of its own colour.
 * [line] is the outline width in board units.
 */

fun DrawScope.drawProp(prop: Prop, line: Float) = when (prop) {
    is Prop.Tree -> tree(prop, line)
    is Prop.Hill -> hill(prop.x, prop.y, prop.w, prop.h, line)
    is Prop.Mountain -> mountain(prop, line)
    is Prop.Reeds -> reeds(prop, line)
    is Prop.Tuft -> tuft(prop, line)
}

fun DrawScope.drawDecal(decal: Decal, line: Float) = when (decal) {
    is Decal.Pool -> rotate(decal.degrees, decal.centre) {
        val rect = Rect(decal.centre - Offset(decal.rx, decal.ry), Size(decal.rx * 2, decal.ry * 2))
        drawOval(Palette.Pool, rect.topLeft, rect.size)
        drawArc(Palette.PoolRim, 200f, 110f, false, rect.deflate(decal.ry * 0.25f).topLeft, rect.deflate(decal.ry * 0.25f).size, style = Stroke(line * 1.3f, cap = StrokeCap.Round))
        drawOval(Palette.Pool.darker(0.35f), rect.topLeft, rect.size, style = Stroke(line))
    }
    is Decal.Field -> rotate(decal.degrees, decal.centre) {
        val topLeft = decal.centre - Offset(decal.w / 2, decal.h / 2)
        val size = Size(decal.w, decal.h)
        val corner = CornerRadius(decal.h * 0.3f)
        drawRoundRect(Palette.Field, topLeft, size, corner)
        for (k in 1..4) {
            val y = topLeft.y + decal.h * k / 5
            drawLine(Palette.FieldStripe, Offset(topLeft.x + decal.h * 0.25f, y), Offset(topLeft.x + decal.w - decal.h * 0.25f, y), line, cap = StrokeCap.Round)
        }
        drawRoundRect(Palette.FieldOutline, topLeft, size, corner, style = Stroke(line))
    }
}

private fun DrawScope.groundShadow(x: Float, y: Float, w: Float) {
    drawOval(Palette.BoardShadow, Offset(x - w / 2, y - w * 0.16f), Size(w, w * 0.32f))
}

private fun DrawScope.tree(t: Prop.Tree, line: Float) {
    val r = t.r
    groundShadow(t.x + r * 0.3f, t.y, r * 1.9f)
    drawRect(Palette.Trunk, Offset(t.x - r * 0.18f, t.y - r * 0.9f), Size(r * 0.36f, r * 0.9f))
    val centre = Offset(t.x, t.y - r * 1.55f)
    val base = when (t.shade) {
        0 -> Palette.TreeDark
        1 -> Palette.TreeDark.lighter(0.08f)
        else -> Palette.TreeDark.darker(0.08f)
    }
    drawCircle(base, r, centre)
    drawCircle(Palette.TreeLight, r * 0.68f, centre + Offset(-r * 0.24f, -r * 0.24f))
    drawCircle(Palette.TreeOutline, r, centre, style = Stroke(line))
}

private fun DrawScope.hill(x: Float, y: Float, w: Float, h: Float, line: Float) {
    groundShadow(x + w * 0.1f, y, w * 1.05f)
    val dome = Path().apply {
        moveTo(x - w / 2, y)
        arcTo(Rect(x - w / 2, y - h, x + w / 2, y + h), 180f, 180f, false)
        close()
    }
    drawPath(dome, Palette.HillBase)
    clipPath(dome) { drawOval(Palette.HillLight, Offset(x - w * 0.55f, y - h * 1.05f), Size(w * 0.75f, h * 1.6f)) }
    drawPath(dome, Palette.HillOutline, style = Stroke(line, join = StrokeJoin.Round))
}

private fun DrawScope.mountain(m: Prop.Mountain, line: Float) {
    val left = Offset(m.x - m.w / 2, m.y)
    val right = Offset(m.x + m.w / 2, m.y)
    val peak = Offset(m.x + m.lean, m.y - m.h)
    val foot = Offset(m.x + m.lean * 0.4f + m.w * 0.08f, m.y)
    groundShadow(m.x + m.w * 0.15f, m.y, m.w * 1.1f)
    drawPath(polygon(listOf(left, peak, foot)), Palette.RockLight)
    drawPath(polygon(listOf(peak, right, foot)), Palette.RockDark)
    // Snow cap: the top third, with a zigzag hem; lit and shaded halves like the rock.
    val t = 0.34f
    val capLeft = lerp(peak, left, t)
    val capRight = lerp(peak, right, t)
    val capMid = lerp(peak, foot, t * 0.8f)
    val hemL = lerp(capLeft, capMid, 0.5f) + Offset(0f, m.h * 0.06f)
    val hemR = lerp(capMid, capRight, 0.5f) + Offset(0f, m.h * 0.06f)
    drawPath(polygon(listOf(peak, capLeft, hemL, capMid)), Palette.Snow)
    drawPath(polygon(listOf(peak, capMid, hemR, capRight)), Palette.SnowShade)
    val outline = polygon(listOf(left, peak, right))
    drawPath(outline, Palette.RockOutline, style = Stroke(line, join = StrokeJoin.Round))
    drawLine(Palette.RockOutline.copy(alpha = 0.5f), lerp(peak, foot, 0.35f), foot, line * 0.7f)
}

private fun DrawScope.reeds(r: Prop.Reeds, line: Float) {
    for (k in -1..1) {
        val top = Offset(r.x + k * r.s * 0.35f, r.y - r.s * (1.4f - kotlin.math.abs(k) * 0.3f))
        drawLine(Palette.Reed, Offset(r.x + k * r.s * 0.15f, r.y), top, line, cap = StrokeCap.Round)
        if (k != 0) drawOval(Palette.Cattail, top - Offset(r.s * 0.12f, r.s * 0.05f), Size(r.s * 0.24f, r.s * 0.45f))
    }
}

private fun DrawScope.tuft(t: Prop.Tuft, line: Float) {
    val colour = Palette.terrain(nl.lamia.kingdoms.core.map.Terrain.Plains).darker(0.25f)
    for (k in -1..1) drawLine(colour, Offset(t.x, t.y), Offset(t.x + k * t.s * 0.45f, t.y - t.s), line * 0.8f, cap = StrokeCap.Round)
}

private fun polygon(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (k in 1 until points.size) lineTo(points[k].x, points[k].y)
    close()
}

private fun lerp(a: Offset, b: Offset, t: Float) = a + (b - a) * t
