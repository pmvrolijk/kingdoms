package nl.lamia.kingdoms.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lamia.kingdoms.core.map.Feature
import nl.lamia.kingdoms.core.map.GameMap
import nl.lamia.kingdoms.core.map.GridMap
import nl.lamia.kingdoms.core.map.Terrain
import nl.lamia.kingdoms.core.rules.Battle
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.Holding
import nl.lamia.kingdoms.core.rules.Kingdom
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The help screen's animated scenes (Design §6.3). Each is a small hand-laid map with a few
 * game states over time, drawn by the game's own MapView: plates, pennants, towers, targets,
 * win-chance labels (from core), arrows and the clash animation are the real thing. A thin
 * overlay adds what a still state cannot show: taps, armies on the march, and growth.
 */

/** One moment of a scene, shown from [at] seconds until the next beat. */
class Beat(
    val at: Float,
    val game: GameState,
    val caption: String,
    val selected: Int? = null,
    val arrow: Arrow? = null,
    /** A strength bar under the map, e.g. attacker against defender. */
    val bar: List<Segment>? = null,
)

/** A tap on [province] at [at] seconds, drawn as a ripple. */
class Tap(val at: Float, val province: Int)

/** [armies] shields walking from [from] to [to] between [start] and [end] seconds. */
class March(val start: Float, val end: Float, val from: Int, val to: Int, val armies: Int, val kingdom: Int)

/** A battle at [province] starting at [at] seconds, played by the game's clash animation. */
class Fight(val at: Float, val province: Int, val attacker: Int, val defender: Int?, val attackerWon: Boolean)

/** A looping scene of [length] seconds. [growth] holds the times at which a round ends and provinces grow. */
class Scene(
    val length: Float,
    val beats: List<Beat>,
    val taps: List<Tap> = emptyList(),
    val marches: List<March> = emptyList(),
    val fights: List<Fight> = emptyList(),
    val growth: List<Float> = emptyList(),
)

private const val SECOND = 1_000_000_000L
private const val GROW_SECONDS = 1.6f

/**
 * Plays [scene] in a loop: the map, then the strength bar (if the beat has one) and a caption.
 * The clock starts at the first frame, so a test can render any moment.
 */
@Composable
fun HelpScene(scene: Scene, modifier: Modifier = Modifier) {
    val camera = remember(scene) { CameraState() }
    val text = rememberTextMeasurer()
    var elapsed by remember(scene) { mutableLongStateOf(0L) }
    LaunchedEffect(scene) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { elapsed = it - start }
    }
    val now = elapsed % (scene.length * SECOND).toLong()
    val t = now.toFloat() / SECOND
    val beat = scene.beats.last { it.at <= t }
    val clashes = scene.fights
        .filter { t >= it.at && now < (it.at * SECOND).toLong() + CLASH_NANOS }
        .map { Clash(it.province, it.attacker, it.defender, it.attackerWon, start = (it.at * SECOND).toLong() + 1) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
            MapView(
                beat.game,
                beat.selected,
                beat.arrow,
                cameraState = camera,
                clashes = clashes,
                now = now,
                onTap = {},
                onLongPress = {},
                modifier = Modifier.fillMaxSize(),
                gestures = false,
            )
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                val cam = camera.camera ?: return@Canvas
                drawMoments(scene, t, beat.game, cam, text)
            }
        }
        beat.bar?.let { StackedBar(it, Modifier.fillMaxWidth().height(12.dp)) }
        BasicText(
            beat.caption,
            style = Small.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            minLines = 2,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().background(Palette.Selected, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private val PlusText = TextStyle(color = Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)

/** Taps, marching shields and growth at time [t]. */
private fun DrawScope.drawMoments(scene: Scene, t: Float, game: GameState, cam: Camera, text: TextMeasurer) {
    val map = game.map
    val scale = markerScale(map, cam)
    val dp = 1.dp.toPx() * scale
    fun site(p: Int) = cam.toScreen(map.provinces[p].site)

    for (tap in scene.taps) {
        val k = (t - tap.at) / 0.45f
        if (k !in 0f..1f) continue
        val r = (6 + 18 * k) * dp
        drawCircle(Palette.Selected.copy(alpha = 0.55f * (1 - k)), r, site(tap.province))
        drawCircle(Palette.Ink.copy(alpha = 0.8f * (1 - k)), r, site(tap.province), style = Stroke(2 * dp))
    }

    for (m in scene.marches) {
        if (t !in m.start..m.end) continue
        val span = m.end - m.start
        val walk = span * 0.6f
        val gap = if (m.armies > 1) (span - walk) / (m.armies - 1) else 0f
        val from = site(m.from)
        val to = site(m.to)
        for (k in 0 until m.armies) {
            val f = (t - m.start - k * gap) / walk
            if (f !in 0f..1f) continue
            val eased = f * f * (3 - 2 * f)
            val at = from + (to - from) * eased - Offset(0f, (10 + 7 * sin(f * PI.toFloat())) * dp)
            icon(at, 15 * dp) { armyIcon(game.colourOf(m.kingdom)) }
        }
    }

    for (at in scene.growth) {
        val k = (t - at) / GROW_SECONDS
        if (k !in 0f..1f) continue
        for (p in map.provinces) {
            if (game.owner(p.id) == null) continue
            val grows = p.grows
            val icon = 12.5f * dp
            val row = site(p.id) + Offset(0f, 9.5f * dp + 1.5f * dp + icon / 2)
            grows.forEachIndexed { i, r ->
                val f = ((k - i * 0.06f) / 0.55f).coerceIn(0f, 1f)
                if (f <= 0f || f >= 1f) return@forEachIndexed
                val x = row.x + (i - (grows.size - 1) / 2f) * (icon + dp)
                val start = Offset(x, row.y)
                val pop = 1f + 0.5f * sin(f * PI.toFloat())
                val pos = start + (site(p.id) - start) * (f * f) - Offset(0f, 14 * dp * sin(f * PI.toFloat()))
                icon(pos, icon * pop, alpha = 1f - f * f) { resourceIcon(r) }
            }
            val plus = ((k - 0.5f) / 0.5f).coerceIn(0f, 1f)
            if (plus in 0.001f..0.999f) {
                val layout = text.measure("+${grows.size}", PlusText.copy(fontSize = PlusText.fontSize * scale))
                val topLeft = site(p.id) + Offset(15 * dp, -layout.size.height / 2f - 16 * dp * plus)
                val alpha = 1f - plus * plus
                drawText(layout, color = Palette.Parchment, topLeft = topLeft, alpha = alpha, drawStyle = Stroke(3 * dp))
                drawText(layout, topLeft = topLeft, alpha = alpha, drawStyle = Fill)
            }
        }
    }
}

/** A 24-unit icon centred on [centre], [size] px wide. */
private fun DrawScope.icon(centre: Offset, size: Float, alpha: Float = 1f, draw: DrawScope.() -> Unit) {
    if (alpha <= 0f) return
    withTransform({
        translate(centre.x - size / 2, centre.y - size / 2)
        scale(size / 24f, size / 24f, pivot = Offset.Zero)
    }) {
        if (alpha >= 1f) draw() else drawContext.canvas.run {
            saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, 24f, 24f), androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha })
            draw()
            restore()
        }
    }
}

// ---- The scenes -------------------------------------------------------------------------------

private const val YOU = 0
private const val RED = 1

private fun kingdoms(you: Int, red: Int) = listOf(
    Kingdom(YOU, "You", human = true, capital = you, colour = 0),
    Kingdom(RED, "Redmarch", human = false, capital = red, colour = 1),
)

private fun you(armies: Int, fresh: Int = armies) = Holding(YOU, armies, fresh)
private fun red(armies: Int) = Holding(RED, armies, armies)
private fun neutral(armies: Int) = Holding(null, armies)

private fun GameMap.state(kingdoms: List<Kingdom>, vararg holdings: Holding) =
    GameState(this, seed = 1, kingdoms = kingdoms, holdings = holdings.toList(), round = 1, current = YOU)

private fun GameState.with(vararg changes: Pair<Int, Holding>) =
    copy(holdings = holdings.toMutableList().apply { for ((p, h) in changes) this[p] = h })

private val blue get() = Palette.kingdom(0)
private val redColour get() = Palette.kingdom(1)

/*
 * The grids are 3 × 2 (provinces 0 1 2 over 3 4 5) or 3 × 3; the seeds pick a pleasant shape.
 */

/** Goal: a capital and two provinces each; take the rest. */
fun goalScene(): Scene {
    val map = GridMap.create(3, 2, listOf(Terrain.Hills, Terrain.Forest, Terrain.Plains, Terrain.Plains, Terrain.Marsh, Terrain.Mountains), seed = 13)
    val k = kingdoms(you = 3, red = 2)
    fun tired(owner: Int?, armies: Int) = Holding(owner, armies, 0)
    val s0 = map.state(k, tired(YOU, 3), tired(RED, 2), tired(RED, 5), tired(YOU, 6), tired(YOU, 7), tired(RED, 3))
    val s1 = s0.with(4 to tired(YOU, 1), 1 to tired(YOU, Battle.survivors(6, 6.0, 2.0)))
    val s2 = s1.with(0 to tired(YOU, 5), 1 to tired(YOU, 8), 2 to tired(RED, 6), 3 to tired(YOU, 8), 4 to tired(YOU, 4), 5 to tired(RED, 5))
    val s3 = s2.with(1 to tired(YOU, 1), 2 to tired(YOU, Battle.survivors(7, 7.0, 6.0)))
    val s4 = s3.with(0 to tired(YOU, 6), 1 to tired(YOU, 3), 2 to tired(YOU, 6), 3 to tired(YOU, 10), 4 to tired(YOU, 9), 5 to tired(RED, 6))
    val s5 = s4.with(4 to tired(YOU, 1), 5 to tired(YOU, Battle.survivors(8, 8.0, 6.0)))
    fun territory(g: GameState) = listOf(
        Segment(g.provincesOf(YOU).size.toFloat(), blue, outlined = true),
        Segment(g.provincesOf(RED).size.toFloat(), redColour),
    )
    val start = "Each kingdom starts with a capital (the tower) and two provinces beside it"
    return Scene(
        length = 9f,
        beats = listOf(
            Beat(0f, s0, start, bar = territory(s0)),
            Beat(1.4f, s0, "Take land from your rivals…", arrow = Arrow(4, 1, YOU), bar = territory(s0)),
            Beat(1.9f, s1, "Take land from your rivals…", arrow = Arrow(4, 1, YOU), bar = territory(s1)),
            Beat(3.0f, s2, "…round after round", bar = territory(s2)),
            Beat(3.6f, s2, "…round after round", arrow = Arrow(1, 2, YOU), bar = territory(s2)),
            Beat(4.1f, s3, "…round after round", arrow = Arrow(1, 2, YOU), bar = territory(s3)),
            Beat(5.2f, s4, "…round after round", bar = territory(s4)),
            Beat(5.8f, s4, "…round after round", arrow = Arrow(4, 5, YOU), bar = territory(s4)),
            Beat(6.3f, s5, "The last kingdom standing wins", arrow = Arrow(4, 5, YOU), bar = territory(s5)),
        ),
        fights = listOf(Fight(1.4f, 1, YOU, RED, true), Fight(3.6f, 2, YOU, RED, true), Fight(5.8f, 5, YOU, RED, true)),
    )
}

/** Moving: all but one march, arrive fatigued, and only fresh armies go on. */
fun moveScene(): Scene {
    val map = GridMap.create(3, 2, listOf(Terrain.Forest, Terrain.Hills, Terrain.Mountains, Terrain.Plains, Terrain.Plains, Terrain.Marsh), seed = 3)
    val s0 = map.state(kingdoms(you = 3, red = 2), neutral(2), neutral(3), red(5), you(6), you(1), you(1))
    val s1 = s0.with(3 to you(1))
    val s2 = s1.with(4 to you(6, fresh = 1))
    val s3 = s2.with(4 to you(5, fresh = 0))
    val s4 = s3.with(5 to you(2, fresh = 1))
    val stays = "Only the army that was already there can go on"
    return Scene(
        length = 8.5f,
        beats = listOf(
            Beat(0f, s0, "Your turn. A white dot: these armies can move"),
            Beat(0.9f, s0, "Tap one of your provinces…", selected = 3),
            Beat(1.8f, s1, "…then a neighbour: all armies but one march", arrow = Arrow(3, 4, YOU)),
            Beat(2.8f, s2, "They arrive fatigued: no more moves for them this turn", arrow = Arrow(3, 4, YOU)),
            Beat(4.2f, s2, stays, selected = 4),
            Beat(5.2f, s3, stays, arrow = Arrow(4, 5, YOU)),
            Beat(5.9f, s4, "One step per turn: send armies to the front early", arrow = Arrow(4, 5, YOU)),
        ),
        taps = listOf(Tap(0.8f, 3), Tap(1.7f, 4), Tap(4.1f, 4), Tap(5.1f, 5)),
        marches = listOf(March(1.8f, 2.8f, 3, 4, 5, YOU), March(5.2f, 5.9f, 4, 5, 1, YOU)),
    )
}

/** Attacking: a sure win against a neutral, then a coin flip against an enemy that goes wrong. */
fun attackScene(): Scene {
    val map = GridMap.create(3, 2, listOf(Terrain.Hills, Terrain.Forest, Terrain.Plains, Terrain.Plains, Terrain.Hills, Terrain.Mountains), seed = 5)
    val s0 = map.state(kingdoms(you = 3, red = 2), neutral(3), neutral(2), red(6), you(8), you(8), red(7))
    val s1 = s0.with(3 to you(1))
    val s2 = s1.with(0 to you(Battle.survivors(7, 7.0, 3.0), fresh = 0))
    val s3 = s2.with(4 to you(1))
    val s4 = s3.with(5 to red(Battle.survivors(7, 7.0, 7.0)))
    return Scene(
        length = 8f,
        beats = listOf(
            Beat(0f, s0, "Attack a neutral or enemy neighbour the same way"),
            Beat(0.8f, s0, "The label shows your chance to win", selected = 3),
            Beat(1.9f, s1, "The label shows your chance to win", arrow = Arrow(3, 0, YOU)),
            Beat(2.4f, s2, "Won: the survivors move in", arrow = Arrow(3, 0, YOU)),
            Beat(3.8f, s2, "Equal armies: a coin flip", selected = 4),
            Beat(4.9f, s3, "Equal armies: a coin flip", arrow = Arrow(4, 5, YOU)),
            Beat(5.4f, s4, "Lost: every attacker falls. The defender keeps its survivors", arrow = Arrow(4, 5, YOU)),
        ),
        taps = listOf(Tap(0.7f, 3), Tap(1.8f, 0), Tap(3.7f, 4), Tap(4.8f, 5)),
        fights = listOf(Fight(1.9f, 0, YOU, null, true), Fight(4.9f, 5, YOU, RED, false)),
    )
}

/** Relative strength: the same defender against ever larger attacks, with the game's own odds. */
fun strengthScene(): Scene {
    val map = GridMap.create(3, 2, listOf(Terrain.Plains, Terrain.Hills, Terrain.Plains, Terrain.Forest, Terrain.Plains, Terrain.Hills), seed = 7)
    val defenders = 6
    val k = kingdoms(you = 3, red = 2)
    val step = 1.4f
    val beats = listOf(4, 5, 6, 7, 8, 10, 12).mapIndexed { i, attackers ->
        val game = map.state(k, you(3), red(defenders), red(5), you(4), you(attackers + 1), you(3))
        Beat(i * step, game, odds(attackers, defenders, 1.0), selected = 4, bar = balance(attackers, defenders * 1.0))
    }
    return Scene(length = beats.size * step, beats = beats)
}

/** Defence lines: one attack across each kind of border, with the game's own odds. */
fun defenceScene(): Scene {
    val terrain = listOf(Terrain.Forest, Terrain.Hills, Terrain.Plains, Terrain.Plains, Terrain.Marsh, Terrain.Hills)
    val k = kingdoms(you = 3, red = 2)
    val attackers = 5
    val defenders = 4
    val step = 1.6f
    val beats = listOf(null, Feature.River, Feature.Ridge, Feature.Marsh, Feature.Lake, Feature.MountainRange).mapIndexed { i, feature ->
        val features = if (feature == null) emptyMap() else mapOf((1 to 4) to feature)
        val map = GridMap.create(3, 2, terrain, features, seed = 9)
        val game = map.state(k, you(2), red(defenders), red(5), you(4), you(attackers + 1), you(2))
        val m = feature?.defence ?: 1.0
        val where = feature?.let { "${it.title} ×${decimal(m, 2)}" } ?: "Open border"
        Beat(i * step, game, "$where: $defenders defend as ${decimal(defenders * m, 1)} against $attackers", selected = 4, bar = balance(attackers, defenders * m))
    }
    return Scene(length = beats.size * step, beats = beats)
}

/** Growth: at the end of each round every owned province grows in place; neutrals do not. */
fun growthScene(): Scene {
    val map = GridMap.create(
        3, 3,
        listOf(Terrain.Forest, Terrain.Plains, Terrain.Hills, Terrain.Marsh, Terrain.Plains, Terrain.Mountains, Terrain.Hills, Terrain.Plains, Terrain.Forest),
        seed = 11,
    )
    fun tired(owner: Int?, armies: Int) = Holding(owner, armies, 0)
    val s0 = map.state(
        kingdoms(you = 4, red = 5),
        tired(null, 2), tired(null, 3), tired(RED, 3), tired(YOU, 2), tired(YOU, 5), tired(RED, 4), tired(YOU, 2), tired(YOU, 3), tired(null, 1),
    )
    fun GameState.grown() = copy(holdings = holdings.mapIndexed { p, h -> if (h.owner == null) h else h.copy(armies = h.armies + map.provinces[p].yield) })
    val s1 = s0.grown()
    val s2 = s1.grown()
    val first = 1.0f
    val second = 4.2f
    return Scene(
        length = 7.5f,
        beats = listOf(
            Beat(0f, s0, "At the end of every round, every province grows armies"),
            Beat(first + GROW_SECONDS * 0.55f, s1, "Plains grow 2, other land 1, and the coast 1 more"),
            Beat(second, s1, "Neutral provinces never grow"),
            Beat(second + GROW_SECONDS * 0.55f, s2, "Neutral provinces never grow"),
        ),
        growth = listOf(first, second),
    )
}

private fun balance(attackers: Int, defence: Double) = listOf(
    Segment(attackers.toFloat(), blue, outlined = true),
    Segment(defence.toFloat(), redColour),
)

/** "7 against 6 · about 4 survive a win" — the move picker's wording, from core's battle maths. */
fun odds(attackers: Int, defenders: Int, defence: Double): String {
    val strength = defenders * defence
    val how = when {
        attackers > strength -> "about ${Battle.survivors(attackers, attackers.toDouble(), strength)} survive a win"
        attackers.toDouble() == strength -> "a tie goes to the defender"
        else -> "outnumbered"
    }
    return "$attackers against $defenders · $how"
}

/** [value] with up to [places] decimals, trailing zeros dropped: 4.6, 5, 1.15. */
fun decimal(value: Double, places: Int): String {
    var factor = 1
    repeat(places) { factor *= 10 }
    val n = (value * factor).roundToInt()
    val whole = n / factor
    val fraction = (n % factor).toString().padStart(places, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$whole" else "$whole.$fraction"
}

val Feature.title: String
    get() = when (this) {
        Feature.River -> "River"
        Feature.Ridge -> "Ridge"
        Feature.Marsh -> "Marsh"
        Feature.Lake -> "Lake"
        Feature.MountainRange -> "Mountain range"
    }
