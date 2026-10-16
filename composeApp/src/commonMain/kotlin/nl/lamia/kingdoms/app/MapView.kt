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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
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
import nl.lamia.kingdoms.core.map.Feature
import nl.lamia.kingdoms.core.map.GameMap
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.Point
import nl.lamia.kingdoms.core.rules.GameState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

/** A move to show: from province, to province, by kingdom. */
data class Arrow(val from: Int, val to: Int, val kingdom: Int)

/**
 * The camera, held where both the map and the HUD can read it. Gestures read and write [camera]
 * directly, so several pointer events in one frame each see the previous one's result.
 */
class CameraState {
    var camera by mutableStateOf<Camera?>(null)
}

/** What the gesture handlers learn about the touch in progress; not state, nothing draws from it. */
private class Touch {
    /** The pointer that pressed last: a finger gets a tap tolerance, a mouse does not. */
    var type = PointerType.Mouse

    /** More than one finger has been down since the first one pressed: a pinch, never a tap. */
    var multi = false
}

/** How far around a fingertip a tap that misses the land still finds a province (a coast, a small province). */
private val TOUCH_SLACK = 12.dp

/** Screen = board × scale + offset; board space is the tilted ground plane (see [Board]). */
data class Camera(val offset: Offset, val scale: Float) {
    fun toWorld(screen: Offset) = Board.toWorld((screen - offset) / scale)

    fun toScreen(world: Point) = Board.of(world) * scale + offset

    fun boardToScreen(board: Offset) = board * scale + offset

    /** Zooms by [factor] about [focus], within [MIN_ZOOM]..[MAX_ZOOM] of the [fitted] scale. */
    fun zoom(factor: Float, focus: Offset, fitted: Float): Camera {
        val newScale = (scale * factor).coerceIn(fitted * MIN_ZOOM, fitted * MAX_ZOOM)
        val applied = newScale / scale
        return Camera(focus - (focus - offset) * applied, newScale)
    }

    /** Keeps the middle of the [view] over the board, so the map cannot be pushed off screen. */
    fun keepOnBoard(map: GameMap, view: IntSize): Camera {
        val board = boardSize(map)
        val cx = view.width / 2f
        val cy = view.height / 2f
        return copy(offset = Offset(offset.x.coerceIn(cx - board.width * scale, cx), offset.y.coerceIn(cy - board.height * scale, cy)))
    }

    companion object {
        /** Zoom limits, relative to the fitted view: the same on a dense phone screen as on a desktop. */
        const val MIN_ZOOM = 0.8f
        const val MAX_ZOOM = 6f

        private fun boardSize(map: GameMap) = Size(map.width.toFloat(), map.height.toFloat() * Board.TILT + Board.WALL)

        fun fit(map: GameMap, size: IntSize): Camera {
            val board = boardSize(map)
            val scale = 0.95f * min(size.width / board.width, size.height / board.height)
            return Camera(Offset((size.width - board.width * scale) / 2, (size.height - board.height * scale) / 2), scale)
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
    cameraState: CameraState,
    clashes: List<Clash>,
    /** Frame time for [clashes]. */
    now: Long,
    onTap: (Int?) -> Unit,
    onLongPress: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    /** False for a preview: no "can move" dots. */
    interactive: Boolean = true,
    /** False for the help screen's scenes: no pan, zoom or taps. */
    gestures: Boolean = true,
) {
    val map = game.map
    val board = remember(map) { BoardGeometry(map) }
    // The gesture detectors below restart only when the map changes, so they must read the
    // callbacks through state: a new game on the same map brings new callbacks.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val text = rememberTextMeasurer()
    val waves by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing)))

    val touch = remember { Touch() }

    /**
     * The province under [screen]. A finger that just misses the land takes the province most of
     * a ring of points [TOUCH_SLACK] around it lands on: fingertips are wide, coasts are thin.
     */
    fun PointerInputScope.provinceAt(screen: Offset): Int? {
        val cam = cameraState.camera ?: return null
        map.provinceAt(cam.toWorld(screen))?.let { return it.id }
        if (touch.type != PointerType.Touch) return null
        val r = TOUCH_SLACK.toPx()
        return (0 until 8)
            .mapNotNull { i -> map.provinceAt(cam.toWorld(screen + Offset(cos(i * PI / 4).toFloat() * r, sin(i * PI / 4).toFloat() * r)))?.id }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    /** Applies a camera change within the zoom limits, keeping the board on screen. */
    fun PointerInputScope.moveCamera(change: (camera: Camera, fitted: Float) -> Camera) {
        val cam = cameraState.camera ?: return
        cameraState.camera = change(cam, Camera.fit(map, size).scale).keepOnBoard(map, size)
    }

    val source = selected?.takeIf { game.isHumanTurn && game.movable(it) > 0 }
    val targets = source?.let { map.provinces[it].neighbours }.orEmpty()

    Box(
        modifier
            // The layers draw wherever the camera puts the board; keep that inside the view
            // (the new-game preview sits beside the settings panel).
            .clipToBounds()
            .background(Palette.Sea)
            .onSizeChanged { if (cameraState.camera == null && it != IntSize.Zero) cameraState.camera = Camera.fit(map, it) }
            .then(if (!gestures) Modifier else Modifier.pointerInput(map) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    moveCamera { cam, fitted -> Camera(cam.offset + pan, cam.scale).zoom(zoom, centroid, fitted) }
                }
            }
            .pointerInput(map) {
                // Initial pass, so a right-click is consumed before the tap detector sees it.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.first()
                        val down = event.changes.count { it.pressed }
                        if (event.type == PointerEventType.Press) {
                            touch.type = change.type
                            if (down == 1) touch.multi = false
                        }
                        if (down > 1) touch.multi = true
                        when {
                            event.type == PointerEventType.Scroll -> {
                                moveCamera { cam, fitted -> cam.zoom(if (change.scrollDelta.y > 0) 1 / 1.1f else 1.1f, change.position, fitted) }
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
                detectTapGestures(
                    onTap = { if (!touch.multi) currentOnTap(provinceAt(it)) },
                    onLongPress = { if (!touch.multi) currentOnLongPress(provinceAt(it)) },
                )
            }),
    ) {
        // Each layer has its own graphicsLayer: without it, the water's per-frame invalidation
        // re-records the shared parent layer and re-runs the land's draw with every prop.
        // Water: the only layer that reads the animation clock.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val cam = cameraState.camera ?: return@Canvas
            drawWater(board, cam, waves)
        }
        // Land: static except for camera and ownership.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val cam = cameraState.camera ?: return@Canvas
            inBoard(cam) { drawLand(board, game, line = max(1.7f, 1.2f / cam.scale)) }
        }
        // Overlay: selection, targets, arrows, banners — screen-sized where it carries text.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val cam = cameraState.camera ?: return@Canvas
            val px = 1f / cam.scale
            inBoard(cam) {
                val dashed = Stroke(width = 2.5f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * px, 6f * px)))
                targets.forEach { drawPath(board.provinces[it], Palette.Selected, style = dashed) }
                if (selected != null) {
                    val outline = board.provinces[selected]
                    drawPath(outline, Palette.Selected.copy(alpha = 0.35f), style = Stroke(width = 11f * px, join = StrokeJoin.Round))
                    drawPath(outline, Palette.Selected, style = Stroke(width = 4f * px, join = StrokeJoin.Round))
                    // Rivers on its edge stay visible over the outline: they are what defends it.
                    map.borders.forEachIndexed { n, b ->
                        if (b.feature == Feature.River && (b.a == selected || b.b == selected)) drawRiver(board.borders[n])
                    }
                }
            }
            arrow?.let { drawArrow(cam.boardToScreen(board.sites[it.from]), cam.boardToScreen(board.sites[it.to]), game.colourOf(it.kingdom)) }
            val bannerScale = markerScale(map, cam)
            // Names fade in once a province is wide enough on screen to hold one.
            val nameAlpha = ((PROVINCE * cam.scale / 1.dp.toPx() - 120f) / 40f).coerceIn(0f, 1f)
            map.provinces.indices.sortedBy { board.sites[it].y }.forEach {
                drawBanner(game, it, cam.boardToScreen(board.sites[it]), text, bannerScale, interactive, nameAlpha)
            }
            if (source != null) {
                for (t in targets) if (game.owner(t) != game.current) {
                    val chance = (game.winChance(source, t) * 100).roundToInt()
                    drawLabel("$chance%", cam.boardToScreen(board.sites[t]) - Offset(0f, 44.dp.toPx()), text)
                }
            }
        }
        // Effects: battle animations, on top of everything; only this layer ticks while they play.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val cam = cameraState.camera ?: return@Canvas
            val scale = markerScale(map, cam)
            for (c in clashes) {
                if (c.start == 0L) continue
                val winner = if (c.attackerWon) game.colourOf(c.attacker) else c.defender?.let(game::colourOf) ?: Palette.Neutral.darker(0.3f)
                val progress = ((now - c.start).toFloat() / CLASH_NANOS).coerceIn(0f, 1f)
                drawClash(cam.boardToScreen(board.sites[c.province]), progress, scale, winner)
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
        val colour = game.colourOf(owner)
        val path = board.provinces[p.id]
        drawPath(path, colour.copy(alpha = 0.16f))
        clipPath(path) { drawPath(path, colour.copy(alpha = 0.8f), style = Stroke(width = 7f, join = StrokeJoin.Round)) }
    }
    map.borders.forEachIndexed { n, border ->
        val a = Palette.terrain(map.provinces[border.a].terrain)
        val b = Palette.terrain(map.provinces[border.b].terrain)
        drawPath(board.borders[n], lerp(a, b, 0.5f).darker(0.4f), style = Stroke(line, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
    board.rivers.forEach { drawRiver(it) }
    board.decals.forEach { drawDecal(it, line) }
    map.shores.forEachIndexed { n, shore ->
        drawPath(board.shores[n], Palette.terrain(map.provinces[shore.province].terrain).darker(0.5f), style = Stroke(line * 1.6f, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
    board.props.forEach { drawProp(it, line) }
}

private fun DrawScope.drawRiver(path: Path) {
    drawPath(path, Palette.River.darker(0.25f), style = Stroke(7f, join = StrokeJoin.Round, cap = StrokeCap.Round))
    drawPath(path, Palette.River, style = Stroke(5f, join = StrokeJoin.Round, cap = StrokeCap.Round))
    drawPath(path, Palette.RiverLight, style = Stroke(1.6f, join = StrokeJoin.Round, cap = StrokeCap.Round))
}

/** A province's width in board units, about. */
private val PROVINCE = sqrt(MapGenerator.CELL_AREA).toFloat()

/**
 * How big banners, towers and resource icons draw (Design §8). In the fitted view: full size,
 * down to 60% when provinces are small on screen (a phone). Zooming scales them half as much as
 * the map, so they grow when zoomed in but leave room for the land.
 */
fun DrawScope.markerScale(map: GameMap, cam: Camera): Float {
    val fit = Camera.fit(map, IntSize(size.width.toInt(), size.height.toInt())).scale
    val atFit = (fit * PROVINCE / 110.dp.toPx()).coerceIn(0.6f, 1f)
    val zoom = cam.scale / fit
    return (atFit * (1f + 0.5f * (zoom - 1f))).coerceIn(0.4f, 2.5f)
}

private val NameText = TextStyle(color = Palette.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)

private val BadgeText = TextStyle(color = Palette.Parchment, fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val LabelText = TextStyle(color = Palette.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)

/**
 * A province's army: a count plate in the owner's colour on the site, a pennant on a pole for an
 * owned province, a small keep under it for a capital, a dot while armies can still move, and
 * the yield underneath, and the province's name below that when [nameAlpha] > 0. Drawn at
 * [scale] (see [markerScale]).
 */
private fun DrawScope.drawBanner(game: GameState, province: Int, site: Offset, text: TextMeasurer, scale: Float, interactive: Boolean, nameAlpha: Float) {
    val holding = game.holdings[province]
    val owner = holding.owner
    val colour = owner?.let(game::colourOf) ?: Palette.Neutral
    val dp = 1.dp.toPx() * scale
    if (owner != null) {
        val capital = game.kingdoms[owner].capital == province
        drawPennant(if (capital) drawTower(site, colour, dp) else site - Offset(0f, 8 * dp), colour, dp)
    }
    val style = (if (owner == null) BadgeText.copy(color = Palette.Ink) else BadgeText).let { it.copy(fontSize = it.fontSize * scale) }
    val count = text.measure(holding.armies.toString(), style)
    val plate = Size(maxOf(count.size.width + 12 * dp, 24 * dp), 19 * dp)
    val topLeft = site - Offset(plate.width / 2, plate.height / 2)
    drawRoundRect(Palette.BoardShadow, topLeft + Offset(1.5f * dp, 2 * dp), plate, CornerRadius(6 * dp))
    drawRoundRect(colour, topLeft, plate, CornerRadius(6 * dp))
    drawRoundRect(colour.darker(0.45f), topLeft, plate, CornerRadius(6 * dp), style = Stroke(1.5f * dp))
    drawText(count, topLeft = site - Offset(count.size.width / 2f, count.size.height / 2f))
    if (interactive && game.isHumanTurn && game.movable(province) > 0) {
        val dot = topLeft + Offset(plate.width - 1 * dp, 1 * dp)
        drawCircle(Palette.Selected, 4 * dp, dot)
        drawCircle(Palette.Ink, 4 * dp, dot, style = Stroke(1 * dp))
    }
    // What the province grows, as resource icons — the same row as on the province card.
    val p = game.map.provinces[province]
    val icons = p.grows
    val icon = 12.5f * dp
    var x = site.x - (icons.size * icon + (icons.size - 1) * dp) / 2
    val y = site.y + plate.height / 2 + 1.5f * dp
    for (r in icons) {
        withTransform({
            translate(x, y)
            scale(icon / 24f, icon / 24f, pivot = Offset.Zero)
        }) { resourceIcon(r) }
        x += icon + dp
    }
    if (nameAlpha > 0f) {
        val name = text.measure(p.name, NameText.copy(fontSize = NameText.fontSize * scale))
        val at = Offset(site.x - name.size.width / 2f, y + icon + 1.5f * dp)
        // A parchment halo keeps the name readable on any terrain. The name must say Fill: on
        // Android the layout's paint keeps the halo's Stroke otherwise (null means "unchanged"),
        // and the name draws as a thick ink outline over its halo.
        drawText(name, color = Palette.Parchment, topLeft = at, alpha = nameAlpha * 0.85f, drawStyle = Stroke(3 * dp, join = StrokeJoin.Round))
        drawText(name, topLeft = at, alpha = nameAlpha, drawStyle = Fill)
    }
}

/** A pole from [foot] with a triangular flag in the kingdom's colour. */
internal fun DrawScope.drawPennant(foot: Offset, colour: Color, dp: Float) {
    val top = foot - Offset(0f, 26 * dp)
    drawLine(Palette.Ink, foot, top, 2 * dp, cap = StrokeCap.Round)
    val flag = Path().apply {
        moveTo(top.x, top.y)
        lineTo(top.x + 16 * dp, top.y + 5 * dp)
        lineTo(top.x, top.y + 10 * dp)
        close()
    }
    drawPath(flag, colour)
    drawPath(flag, colour.darker(0.45f), style = Stroke(1.2f * dp, join = StrokeJoin.Round))
}

/** A little stone tower with a cone roof in the kingdom's colour; returns the roof's tip. */
internal fun DrawScope.drawTower(site: Offset, colour: Color, dp: Float): Offset {
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
