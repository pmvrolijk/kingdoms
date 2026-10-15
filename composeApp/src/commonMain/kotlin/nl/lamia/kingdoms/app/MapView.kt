package nl.lamia.kingdoms.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lamia.kingdoms.core.map.GameMap
import nl.lamia.kingdoms.core.map.Point
import nl.lamia.kingdoms.core.rules.GameState
import kotlin.math.min
import kotlin.math.roundToInt

/** A move to show: from province, to province, by kingdom. */
data class Arrow(val from: Int, val to: Int, val kingdom: Int)

/** Screen = world × scale + offset. */
data class Camera(val offset: Offset, val scale: Float) {
    fun toWorld(screen: Offset) = Point(((screen.x - offset.x) / scale).toDouble(), ((screen.y - offset.y) / scale).toDouble())

    fun toScreen(world: Point) = Offset(world.x.toFloat() * scale + offset.x, world.y.toFloat() * scale + offset.y)

    fun zoom(factor: Float, focus: Offset): Camera {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val applied = newScale / scale
        return Camera(focus - (focus - offset) * applied, newScale)
    }

    companion object {
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 8f

        fun fit(map: GameMap, size: IntSize): Camera {
            val scale = 0.95f * min(size.width / map.width.toFloat(), size.height / map.height.toFloat())
            return Camera(
                Offset((size.width - map.width.toFloat() * scale) / 2, (size.height - map.height.toFloat() * scale) / 2),
                scale,
            )
        }
    }
}

private class MapPaths(map: GameMap) {
    val provinces = map.provinces.map { it.outline.toPath(close = true) }
    val borders = map.borders.map { it.path.toPath(close = false) }
    val shores = map.shores.map { it.path.toPath(close = false) }
}

private fun List<Point>.toPath(close: Boolean) = Path().apply {
    moveTo(first().x.toFloat(), first().y.toFloat())
    for (k in 1 until size) lineTo(this@toPath[k].x.toFloat(), this@toPath[k].y.toFloat())
    if (close) close()
}

/**
 * Flat top-down map with ownership, army badges and move targets. Pan by dragging, zoom by pinch
 * or wheel; taps and long-presses report the province under the pointer (null for sea).
 */
@Composable
fun MapView(
    game: GameState,
    selected: Int?,
    arrow: Arrow?,
    onTap: (Int?) -> Unit,
    onLongPress: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val map = game.map
    val paths = remember(map) { MapPaths(map) }
    // The gesture detectors below restart only when the map changes, so they must read the
    // callbacks through state: a new game on the same map brings new callbacks.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val text = rememberTextMeasurer()
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var camera by remember(map) { mutableStateOf<Camera?>(null) }

    fun provinceAt(screen: Offset) = camera?.let { map.provinceAt(it.toWorld(screen))?.id }

    val source = selected?.takeIf { game.isHumanTurn && game.movable(it) > 0 }
    val targets = source?.let { map.provinces[it].neighbours }.orEmpty()

    Canvas(
        modifier
            .onSizeChanged {
                if (camera == null || viewSize == IntSize.Zero) camera = Camera.fit(map, it)
                viewSize = it
            }
            .pointerInput(map) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camera = camera?.let { Camera(it.offset + pan, it.scale).zoom(zoom, centroid) }
                }
            }
            .pointerInput(map) {
                // Initial pass, so a right-click is consumed before the tap detector sees it.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.first()
                        when {
                            event.type == PointerEventType.Scroll -> {
                                camera = camera?.zoom(if (change.scrollDelta.y > 0) 1 / 1.1f else 1.1f, change.position)
                                change.consume()
                            }
                            event.type == PointerEventType.Press && event.buttons.isSecondaryPressed -> {
                                currentOnLongPress(provinceAt(change.position))
                                change.consume()
                            }
                        }
                    }
                }
            }
            .pointerInput(map) {
                val at = ::provinceAt
                detectTapGestures(onTap = { currentOnTap(at(it)) }, onLongPress = { currentOnLongPress(at(it)) })
            },
    ) {
        drawRect(Palette.DeepSea)
        val cam = camera ?: return@Canvas
        val px = 1f / cam.scale
        withTransform({
            translate(cam.offset.x, cam.offset.y)
            scale(cam.scale, cam.scale, pivot = Offset.Zero)
        }) {
            drawRect(Palette.Sea, size = Size(map.width.toFloat(), map.height.toFloat()))
            for (p in map.provinces) {
                val path = paths.provinces[p.id]
                drawPath(path, Palette.terrain(p.terrain))
                game.owner(p.id)?.let { owner ->
                    val colour = Palette.kingdom(owner)
                    drawPath(path, colour.copy(alpha = 0.18f))
                    clipPath(path) { drawPath(path, colour.copy(alpha = 0.85f), style = Stroke(width = 8f * px, join = StrokeJoin.Round)) }
                }
            }
            val thin = Stroke(width = 1.5f * px, join = StrokeJoin.Round, cap = StrokeCap.Round)
            map.borders.forEachIndexed { n, border ->
                val feature = border.feature
                if (feature == null) {
                    drawPath(paths.borders[n], Palette.Border, style = thin)
                } else {
                    val stroke = Stroke(Palette.featureWidth(feature) * px, join = StrokeJoin.Round, cap = StrokeCap.Round)
                    drawPath(paths.borders[n], Palette.feature(feature), style = stroke)
                }
            }
            val shore = Stroke(width = 2.5f * px, join = StrokeJoin.Round, cap = StrokeCap.Round)
            paths.shores.forEach { drawPath(it, Palette.Shore, style = shore) }

            val dashed = Stroke(width = 2.5f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * px, 6f * px)))
            targets.forEach { drawPath(paths.provinces[it], Palette.Selected, style = dashed) }
            if (selected != null) {
                drawPath(paths.provinces[selected], Palette.Selected, style = Stroke(width = 3.5f * px, join = StrokeJoin.Round))
            }
        }
        arrow?.let { drawArrow(cam.toScreen(map.provinces[it.from].site), cam.toScreen(map.provinces[it.to].site), Palette.kingdom(it.kingdom)) }
        for (p in map.provinces) drawBadge(game, p.id, cam.toScreen(p.site), text)
        if (source != null) {
            for (t in targets) if (game.owner(t) != game.current) {
                val chance = (game.winChance(source, t) * 100).roundToInt()
                drawLabel("$chance%", cam.toScreen(map.provinces[t].site) - Offset(0f, 30.dp.toPx()), text)
            }
        }
    }
}

private val BadgeText = TextStyle(color = Palette.Parchment, fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val SmallText = TextStyle(color = Palette.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
private val LabelText = TextStyle(color = Palette.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)

/** Army count in the owner's colour; a ring for a capital, a dot while armies can still move. */
private fun DrawScope.drawBadge(game: GameState, province: Int, centre: Offset, text: TextMeasurer) {
    val holding = game.holdings[province]
    val owner = holding.owner
    val radius = 12.dp.toPx()
    drawCircle(owner?.let(Palette::kingdom) ?: Palette.Neutral, radius, centre)
    drawCircle(Palette.Ink, radius, centre, style = Stroke(1.5.dp.toPx()))
    if (owner != null && game.kingdoms[owner].capital == province) {
        drawCircle(Palette.Ink, radius + 3.dp.toPx(), centre, style = Stroke(1.5.dp.toPx()))
    }
    if (game.isHumanTurn && game.movable(province) > 0) {
        val dot = centre + Offset(radius * 0.75f, -radius * 0.75f)
        drawCircle(Palette.Selected, 4.dp.toPx(), dot)
        drawCircle(Palette.Ink, 4.dp.toPx(), dot, style = Stroke(1.dp.toPx()))
    }
    val style = if (owner == null) BadgeText.copy(color = Palette.Ink) else BadgeText
    val count = text.measure(holding.armies.toString(), style)
    drawText(count, topLeft = centre - Offset(count.size.width / 2f, count.size.height / 2f))
    val yieldText = text.measure("+${game.map.provinces[province].yield}", SmallText)
    drawText(yieldText, topLeft = centre + Offset(-yieldText.size.width / 2f, radius + 2.dp.toPx()))
}

/** A thick arrow between two badges, outlined so it reads on any terrain. */
private fun DrawScope.drawArrow(from: Offset, to: Offset, colour: Color) {
    val delta = to - from
    val length = delta.getDistance()
    if (length < 1f) return
    val dir = delta / length
    val start = from + dir * 16.dp.toPx()
    val tip = to - dir * 16.dp.toPx()
    val head = 10.dp.toPx()
    val side = Offset(-dir.y, dir.x) * (head * 0.6f)
    val base = tip - dir * head
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + side.x, base.y + side.y)
        lineTo(base.x - side.x, base.y - side.y)
        close()
    }
    for ((c, extra) in listOf(Palette.Ink to 2.dp.toPx(), colour to 0f)) {
        drawLine(c, start, base, strokeWidth = 4.dp.toPx() + extra, cap = StrokeCap.Round)
        drawPath(path, c, style = Stroke(width = extra.coerceAtLeast(0.1f), join = StrokeJoin.Round))
        drawPath(path, c)
    }
}

private fun DrawScope.drawLabel(label: String, centre: Offset, text: TextMeasurer) {
    val layout = text.measure(label, LabelText)
    val pad = Offset(6.dp.toPx(), 3.dp.toPx())
    val size = Size(layout.size.width + 2 * pad.x, layout.size.height + 2 * pad.y)
    val topLeft = centre - Offset(size.width / 2, size.height / 2)
    drawRoundRect(Palette.Selected, topLeft, size, CornerRadius(6.dp.toPx()))
    drawRoundRect(Palette.Ink, topLeft, size, CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
    drawText(layout, topLeft = topLeft + pad)
}
