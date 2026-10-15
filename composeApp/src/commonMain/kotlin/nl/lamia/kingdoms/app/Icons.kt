package nl.lamia.kingdoms.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.lamia.kingdoms.core.map.Feature
import nl.lamia.kingdoms.core.map.Resource
import nl.lamia.kingdoms.core.map.Terrain

/*
 * HUD icons in the map's cel style. Each draws into a 24 × 24 unit box; [Icon] scales it.
 * Terrain icons reuse the map's own props, so the card and the board look alike.
 */

@Composable
fun Icon(size: Dp = 20.dp, draw: DrawScope.() -> Unit) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        withTransform({ scale(s, s, pivot = Offset.Zero) }) { draw() }
    }
}

private const val LINE = 1.4f

fun DrawScope.terrainIcon(terrain: Terrain) = when (terrain) {
    Terrain.Plains -> {
        drawDecal(Decal.Field(Offset(12f, 13f), 20f, 12f, -8f), LINE)
        drawProp(Prop.Tuft(5f, 22f, 4f), LINE)
    }
    Terrain.Hills -> {
        drawProp(Prop.Hill(8f, 20f, 13f, 8f), LINE)
        drawProp(Prop.Hill(15f, 22f, 16f, 11f), LINE)
    }
    Terrain.Forest -> {
        drawProp(Prop.Tree(8f, 18f, 4.5f, 0), LINE)
        drawProp(Prop.Tree(16f, 21f, 5f, 1), LINE)
    }
    Terrain.Mountains -> drawProp(Prop.Mountain(12f, 22f, 20f, 19f, 1f), LINE)
    Terrain.Marsh -> {
        drawDecal(Decal.Pool(Offset(13f, 18f), 9f, 4f, 0f), LINE)
        drawProp(Prop.Reeds(7f, 18f, 7f), LINE)
        drawProp(Prop.Reeds(18f, 15f, 6f), LINE)
    }
}

fun DrawScope.resourceIcon(resource: Resource) = when (resource) {
    Resource.Grain -> {
        val gold = Color(0xFFE2B84B)
        drawLine(gold.darker(0.35f), Offset(12f, 22f), Offset(12f, 6f), LINE, cap = StrokeCap.Round)
        for (k in 0..2) {
            val y = 8f + k * 4.5f
            drawOval(gold, Offset(6.5f, y), Size(5f, 3.2f))
            drawOval(gold, Offset(12.5f, y), Size(5f, 3.2f))
            drawOval(gold.darker(0.35f), Offset(6.5f, y), Size(5f, 3.2f), style = Stroke(1f))
            drawOval(gold.darker(0.35f), Offset(12.5f, y), Size(5f, 3.2f), style = Stroke(1f))
        }
        drawOval(gold, Offset(10f, 3f), Size(4f, 5f))
    }
    Resource.Wool -> {
        val wool = Color(0xFFF7F3EA)
        val outline = wool.darker(0.45f)
        drawLine(outline, Offset(8f, 16f), Offset(8f, 21f), 1.6f, cap = StrokeCap.Round)
        drawLine(outline, Offset(15f, 16f), Offset(15f, 21f), 1.6f, cap = StrokeCap.Round)
        for (c in listOf(Offset(8f, 12f), Offset(12f, 9f), Offset(16f, 12f), Offset(12f, 14f))) drawCircle(wool, 4.5f, c)
        for (c in listOf(Offset(8f, 12f), Offset(12f, 9f), Offset(16f, 12f), Offset(12f, 14f))) drawCircle(outline, 4.5f, c, style = Stroke(1f))
        drawOval(Color(0xFF5B4632), Offset(17f, 9f), Size(5f, 4f))
    }
    Resource.Timber -> {
        val bark = Color(0xFF9C6F45)
        drawRoundRect(bark, Offset(3f, 9f), Size(16f, 9f), CornerRadius(3f))
        drawRoundRect(bark.darker(0.4f), Offset(3f, 9f), Size(16f, 9f), CornerRadius(3f), style = Stroke(LINE))
        drawOval(Color(0xFFE6C48E), Offset(15f, 9f), Size(7f, 9f))
        drawOval(bark.darker(0.4f), Offset(15f, 9f), Size(7f, 9f), style = Stroke(LINE))
        drawOval(bark, Offset(17f, 12f), Size(3f, 3f), style = Stroke(1f))
    }
    Resource.Ore -> {
        val rock = Path().apply {
            moveTo(3f, 19f); lineTo(6f, 9f); lineTo(13f, 5f); lineTo(20f, 9f); lineTo(21f, 19f); close()
        }
        drawPath(rock, Palette.RockDark)
        drawPath(Path().apply { moveTo(6f, 9f); lineTo(13f, 5f); lineTo(12f, 12f); lineTo(4.5f, 14f); close() }, Palette.RockLight)
        drawCircle(Color(0xFFE2B84B), 2f, Offset(15f, 13f))
        drawCircle(Color(0xFFE2B84B), 1.4f, Offset(9f, 16f))
        drawPath(rock, Palette.RockOutline, style = Stroke(LINE, join = StrokeJoin.Round))
    }
    Resource.Peat -> {
        val peat = Color(0xFF6E4E35)
        drawRect(peat, Offset(4f, 10f), Size(16f, 9f))
        drawPath(Path().apply { moveTo(4f, 10f); lineTo(8f, 6f); lineTo(22f, 6f); lineTo(20f, 10f); close() }, peat.lighter(0.25f))
        drawPath(Path().apply { moveTo(20f, 10f); lineTo(22f, 6f); lineTo(22f, 15f); lineTo(20f, 19f); close() }, peat.darker(0.2f))
        drawPath(Path().apply { moveTo(4f, 10f); lineTo(8f, 6f); lineTo(22f, 6f); lineTo(22f, 15f); lineTo(20f, 19f); lineTo(4f, 19f); close() }, peat.darker(0.45f), style = Stroke(LINE, join = StrokeJoin.Round))
    }
    Resource.Fish -> {
        val fish = Color(0xFF6DB3DC)
        val tail = Path().apply { moveTo(17f, 12f); lineTo(22f, 7f); lineTo(22f, 17f); close() }
        drawPath(tail, fish.darker(0.15f))
        drawPath(tail, fish.darker(0.45f), style = Stroke(LINE, join = StrokeJoin.Round))
        drawOval(fish, Offset(3f, 7f), Size(16f, 10f))
        drawOval(fish.lighter(0.4f), Offset(5f, 8f), Size(9f, 4f))
        drawOval(fish.darker(0.45f), Offset(3f, 7f), Size(16f, 10f), style = Stroke(LINE))
        drawCircle(Color(0xFF2E3A44), 1.2f, Offset(7f, 11f))
    }
}

/** A shield in the kingdom's colour (or parchment for neutral). */
fun DrawScope.armyIcon(colour: Color) {
    val shield = Path().apply {
        moveTo(5f, 4f); lineTo(19f, 4f); lineTo(19f, 12f)
        quadraticTo(19f, 19f, 12f, 22f); quadraticTo(5f, 19f, 5f, 12f); close()
    }
    drawPath(shield, colour)
    drawPath(Path().apply { moveTo(5f, 4f); lineTo(12f, 4f); lineTo(12f, 22f); quadraticTo(5f, 19f, 5f, 12f); close() }, colour.lighter(0.25f))
    drawLine(colour.darker(0.45f), Offset(9f, 9f), Offset(15f, 15f), 1.6f, cap = StrokeCap.Round)
    drawLine(colour.darker(0.45f), Offset(15f, 9f), Offset(9f, 15f), 1.6f, cap = StrokeCap.Round)
    drawPath(shield, colour.darker(0.45f), style = Stroke(LINE, join = StrokeJoin.Round))
}

fun DrawScope.featureIcon(feature: Feature) = when (feature) {
    Feature.River -> {
        val river = Path().apply { moveTo(2f, 8f); cubicTo(8f, 2f, 12f, 18f, 22f, 12f) }
        drawPath(river, Palette.River.darker(0.25f), style = Stroke(6f, cap = StrokeCap.Round))
        drawPath(river, Palette.River, style = Stroke(4f, cap = StrokeCap.Round))
        drawPath(river, Palette.RiverLight, style = Stroke(1.2f, cap = StrokeCap.Round))
    }
    Feature.Ridge -> drawProp(Prop.Hill(12f, 19f, 20f, 11f), LINE)
    Feature.Marsh -> {
        drawDecal(Decal.Pool(Offset(14f, 17f), 7f, 3.5f, 0f), LINE)
        drawProp(Prop.Reeds(8f, 18f, 7f), LINE)
    }
    Feature.Lake -> drawDecal(Decal.Pool(Offset(12f, 13f), 10f, 6f, 0f), LINE)
    Feature.MountainRange -> {
        drawProp(Prop.Mountain(8f, 21f, 12f, 13f, 0f), LINE)
        drawProp(Prop.Mountain(15f, 22f, 15f, 17f, 1f), LINE)
    }
}
