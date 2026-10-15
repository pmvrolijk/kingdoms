package nl.lamia.kingdoms.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

/** A move to show: from province, to province, by kingdom. */
data class Arrow(val from: Int, val to: Int, val kingdom: Int)

/** Screen = board × scale + offset; board space is the tilted ground plane (see [Board]). */
data class Camera(val offset: Offset, val scale: Float) {
    fun toWorld(screen: Offset) = Board.toWorld((screen - offset) / scale)

    fun toScreen(world: Point) = Board.of(world) * scale + offset

    fun boardToScreen(board: Offset) = board * scale + offset

    fun zoom(factor: Float, focus: Offset): Camera {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val applied = newScale / scale
        return Camera(focus - (focus - offset) * applied, newScale)
    }

    companion object {
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 8f

        fun fit(map: GameMap, size: IntSize): Camera {
            val width = map.width.toFloat()
            val height = map.height.toFloat() * Board.TILT + Board.WALL
            val scale = 0.95f * min(size.width / width, size.height / height)
            return Camera(Offset((size.width - width * scale) / 2, (size.height - height * scale) / 2), scale)
        }
    }
}

private fun DrawScope.inBoard(cam: Camera, block: DrawScope.() -> Unit) = withTransform({
    translate(cam.offset.x, cam.offset.y)
    scale(cam.scale, cam.scale, pivot = Offset.Zero)
}, block)

/**
 * The map as a tilted, cel-shaded diorama (Design §8), in three layers so only what changes
 * redraws: animated water; the land (walls, terrain, ownership, borders, props), which redraws
 * on camera or ownership changes; and an overlay with selection, arrows and army banners.
 * Pan by dragging, zoom by pinch or wheel; taps, long-presses and right-clicks report the
 * province under the pointer (null for sea).
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
    val board = remember(map) { BoardGeometry(map) }
    // The gesture detectors below restart only when the map changes, so they must read the
    // callbacks through state: a new game on the same map brings new callbacks.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val text = rememberTextMeasurer()
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var camera by remember(map) { mutableStateOf<Camera?>(null) }
    val waves by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing)))

    fun provinceAt(screen: Offset) = camera?.let { map.provinceAt(it.toWorld(screen))?.id }

    val source = selected?.takeIf { game.isHumanTurn && game.movable(it) > 0 }
    val targets = source?.let { map.provinces[it].neighbours }.orEmpty()

    Box(
        modifier
            .background(Palette.Sea)
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
        // Each layer has its own graphicsLayer: without it, the water's per-frame invalidation
        // re-records the shared parent layer and re-runs the land's draw with every prop.
        // Water: the only layer that reads the animation clock.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val cam = camera ?: return@Canvas
            drawWater(board, cam, waves)
        }
        // Land: static except for camera and ownership.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val cam = camera ?: return@Canvas
            inBoard(cam) { drawLand(board, game, line = max(1.7f, 1.2f / cam.scale)) }
        }
        // Overlay: selection, targets, arrows, banners — screen-sized where it carries text.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val cam = camera ?: return@Canvas
            val px = 1f / cam.scale
            inBoard(cam) {
                val dashed = Stroke(width = 2.5f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * px, 6f * px)))
                targets.forEach { drawPath(board.provinces[it], Palette.Selected, style = dashed) }
                if (selected != null) {
                    val outline = board.provinces[selected]
                    drawPath(outline, Palette.Selected.copy(alpha = 0.35f), style = Stroke(width = 11f * px, join = StrokeJoin.Round))
                    drawPath(outline, Palette.Selected, style = Stroke(width = 4f * px, join = StrokeJoin.Round))
                }
            }
            arrow?.let { drawArrow(cam.boardToScreen(board.sites[it.from]), cam.boardToScreen(board.sites[it.to]), Palette.kingdom(it.kingdom)) }
            map.provinces.indices.sortedBy { board.sites[it].y }.forEach { drawBanner(game, it, cam.boardToScreen(board.sites[it]), text) }
            if (source != null) {
                for (t in targets) if (game.owner(t) != game.current) {
                    val chance = (game.winChance(source, t) * 100).roundToInt()
                    drawLabel("$chance%", cam.boardToScreen(board.sites[t]) - Offset(0f, 44.dp.toPx()), text)
                }
            }
        }
    }
}

/** Sea with drifting wave marks, and foam where the water meets the coast and the wall foot. */
private fun DrawScope.drawWater(board: BoardGeometry, cam: Camera, phase: Float) {
    val px = 1f / cam.scale
    val topLeft = (Offset.Zero - cam.offset) / cam.scale
    val bottomRight = (Offset(size.width, size.height) - cam.offset) / cam.scale
    val spacing = 46f
    val wave = Stroke(width = 1.6f * px, cap = StrokeCap.Round)
    inBoard(cam) {
        var row = floor(topLeft.y / (spacing * 0.65f)).toInt() - 1
        while (row * spacing * 0.65f < bottomRight.y + spacing) {
            var col = floor(topLeft.x / spacing).toInt() - 1
            while (col * spacing < bottomRight.x + spacing) {
                val local = phase * 2 * PI.toFloat() + col * 0.9f + row * 1.7f
                val x = col * spacing + (row.mod(2)) * spacing / 2 + sin(local) * 4f
                val y = row * spacing * 0.65f
                drawArc(Palette.Wave.copy(alpha = 0.55f + 0.35f * sin(local + 1f)), 200f, 140f, false, Offset(x - 7f, y - 3f), Size(14f, 7f), style = wave)
                col++
            }
            row++
        }
        val swell = 0.5f + 0.5f * sin(phase * 2 * PI.toFloat())
        val foam = Stroke(width = 5f + 2.5f * swell, cap = StrokeCap.Round, join = StrokeJoin.Round)
        board.shores.forEach { drawPath(it, Palette.Foam.copy(alpha = 0.75f), style = foam) }
        translate(0f, Board.WALL) { board.shores.forEach { drawPath(it, Palette.Foam.copy(alpha = 0.6f + 0.3f * swell), style = foam) } }
    }
}

/** Slab shadow and earth walls, terrain tops, ownership, borders, rivers, decals, coast line, props. */
private fun DrawScope.drawLand(board: BoardGeometry, game: GameState, line: Float) {
    val map = board.map
    translate(5f, Board.WALL + 5f) { board.provinces.forEach { drawPath(it, Palette.BoardShadow) } }
    drawPath(board.wallTop, Palette.Earth)
    drawPath(board.wallBottom, Palette.EarthDark)
    for (p in map.provinces) drawPath(board.provinces[p.id], Palette.terrain(p.terrain))
    for (p in map.provinces) {
        val owner = game.owner(p.id) ?: continue
        val colour = Palette.kingdom(owner)
        val path = board.provinces[p.id]
        drawPath(path, colour.copy(alpha = 0.16f))
        clipPath(path) { drawPath(path, colour.copy(alpha = 0.8f), style = Stroke(width = 7f, join = StrokeJoin.Round)) }
    }
    map.borders.forEachIndexed { n, border ->
        val a = Palette.terrain(map.provinces[border.a].terrain)
        val b = Palette.terrain(map.provinces[border.b].terrain)
        drawPath(board.borders[n], lerp(a, b, 0.5f).darker(0.4f), style = Stroke(line, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
    board.rivers.forEach {
        drawPath(it, Palette.River.darker(0.25f), style = Stroke(7f, join = StrokeJoin.Round, cap = StrokeCap.Round))
        drawPath(it, Palette.River, style = Stroke(5f, join = StrokeJoin.Round, cap = StrokeCap.Round))
        drawPath(it, Palette.RiverLight, style = Stroke(1.6f, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
    board.decals.forEach { drawDecal(it, line) }
    map.shores.forEachIndexed { n, shore ->
        drawPath(board.shores[n], Palette.terrain(map.provinces[shore.province].terrain).darker(0.5f), style = Stroke(line * 1.6f, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
    board.props.forEach { drawProp(it, line) }
}

private val BadgeText = TextStyle(color = Palette.Parchment, fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val SmallText = TextStyle(color = Palette.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
private val LabelText = TextStyle(color = Palette.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)

/**
 * A province's army: a count plate in the owner's colour on the site, a pennant on a pole for an
 * owned province, a small keep under it for a capital, a dot while armies can still move, and
 * the yield underneath. Screen-sized, so it reads at any zoom.
 */
private fun DrawScope.drawBanner(game: GameState, province: Int, site: Offset, text: TextMeasurer) {
    val holding = game.holdings[province]
    val owner = holding.owner
    val colour = owner?.let(Palette::kingdom) ?: Palette.Neutral
    val dp = 1.dp.toPx()
    if (owner != null) {
        val capital = game.kingdoms[owner].capital == province
        val poleFoot = if (capital) drawTower(site, colour, dp) else site - Offset(0f, 8 * dp)
        val poleTop = poleFoot - Offset(0f, 26 * dp)
        drawLine(Palette.Ink, poleFoot, poleTop, 2 * dp, cap = StrokeCap.Round)
        val flag = Path().apply {
            moveTo(poleTop.x, poleTop.y)
            lineTo(poleTop.x + 16 * dp, poleTop.y + 5 * dp)
            lineTo(poleTop.x, poleTop.y + 10 * dp)
            close()
        }
        drawPath(flag, colour)
        drawPath(flag, colour.darker(0.45f), style = Stroke(1.2f * dp, join = StrokeJoin.Round))
    }
    val style = if (owner == null) BadgeText.copy(color = Palette.Ink) else BadgeText
    val count = text.measure(holding.armies.toString(), style)
    val plate = Size(maxOf(count.size.width + 12 * dp, 24 * dp), 19 * dp)
    val topLeft = site - Offset(plate.width / 2, plate.height / 2)
    drawRoundRect(Palette.BoardShadow, topLeft + Offset(1.5f * dp, 2 * dp), plate, CornerRadius(6 * dp))
    drawRoundRect(colour, topLeft, plate, CornerRadius(6 * dp))
    drawRoundRect(colour.darker(0.45f), topLeft, plate, CornerRadius(6 * dp), style = Stroke(1.5f * dp))
    drawText(count, topLeft = site - Offset(count.size.width / 2f, count.size.height / 2f))
    if (game.isHumanTurn && game.movable(province) > 0) {
        val dot = topLeft + Offset(plate.width - 1 * dp, 1 * dp)
        drawCircle(Palette.Selected, 4 * dp, dot)
        drawCircle(Palette.Ink, 4 * dp, dot, style = Stroke(1 * dp))
    }
    val yieldText = text.measure("+${game.map.provinces[province].yield}", SmallText)
    drawText(yieldText, topLeft = site + Offset(-yieldText.size.width / 2f, plate.height / 2 + 1 * dp))
}

/** A little stone tower with a cone roof in the kingdom's colour; returns the roof's tip. */
private fun DrawScope.drawTower(site: Offset, colour: Color, dp: Float): Offset {
    val stone = Color(0xFFEDE4D3)
    val w = 16 * dp
    val h = 18 * dp
    val left = site.x - w / 2
    val bottom = site.y - 7 * dp
    drawRect(stone, Offset(left, bottom - h), Size(w, h))
    drawRect(stone.darker(0.14f), Offset(left + w * 0.6f, bottom - h), Size(w * 0.4f, h))
    drawRect(stone.darker(0.5f), Offset(left, bottom - h), Size(w, h), style = Stroke(1.3f * dp))
    drawRoundRect(stone.darker(0.55f), Offset(site.x - 2.5f * dp, bottom - 7 * dp), Size(5 * dp, 7 * dp), CornerRadius(2.5f * dp))
    val tip = Offset(site.x, bottom - h - 14 * dp)
    val roof = Path().apply {
        moveTo(left - 3 * dp, bottom - h)
        lineTo(tip.x, tip.y)
        lineTo(left + w + 3 * dp, bottom - h)
        close()
    }
    drawPath(roof, colour)
    drawPath(Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left + w + 3 * dp, bottom - h)
        lineTo(tip.x + 2 * dp, bottom - h)
        close()
    }, colour.darker(0.2f))
    drawPath(roof, colour.darker(0.45f), style = Stroke(1.3f * dp, join = StrokeJoin.Round))
    return tip
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
