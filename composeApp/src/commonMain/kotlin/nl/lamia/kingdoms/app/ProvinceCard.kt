package nl.lamia.kingdoms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.lamia.kingdoms.core.map.COASTAL_YIELD
import nl.lamia.kingdoms.core.map.Resource
import nl.lamia.kingdoms.core.rules.GameState

/** How a province's armies split this turn; the parts always add up to the armies. */
data class ArmySplit(val canMove: Int, val stays: Int, val fatigued: Int)

/** For the current kingdom's provinces: fresh-and-movable, the one that must stay, already moved. */
fun GameState.split(province: Int): ArmySplit? {
    val h = holdings[province]
    if (h.owner != current) return null
    val canMove = movable(province)
    val fatigued = h.armies - h.fresh
    return ArmySplit(canMove, h.armies - canMove - fatigued, fatigued)
}

/**
 * The selected province as an infographic: terrain, owner, production as one resource icon per
 * army grown, armies with their can-move / stays / fatigued split, and defended borders.
 * [compact] (a landscape phone, ~360 dp tall) leaves out the how-to line.
 */
@Composable
fun ProvinceCard(game: GameState, id: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    val p = game.map.provinces[id]
    val h = game.holdings[id]
    val owner = h.owner
    val colour = owner?.let(game::colourOf) ?: Palette.Neutral.darker(0.25f)
    Column(modifier.panel().width(290.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(30.dp) { terrainIcon(p.terrain) }
            Column {
                BasicText(p.name, style = Label.copy(fontWeight = FontWeight.Bold))
                BasicText(
                    "${p.terrain.name}${if (p.coastal) " · coast" else ""} · ${owner?.let { game.kingdoms[it].name } ?: "neutral"}" +
                        if (owner != null && game.kingdoms[owner].capital == id) " · capital" else "",
                    style = Small,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(14.dp).background(colour, RoundedCornerShape(3.dp)))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText("Grows", style = Small, modifier = Modifier.width(52.dp))
            repeat(p.terrain.yield) { Icon(18.dp) { resourceIcon(p.terrain.resource) } }
            if (p.coastal) repeat(COASTAL_YIELD) { Icon(18.dp) { resourceIcon(Resource.Fish) } }
            Spacer(Modifier.weight(1f))
            BasicText(if (owner == null) "+${p.yield} if held" else "+${p.yield} / round", style = Small.copy(fontWeight = FontWeight.Bold))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(18.dp) { armyIcon(colour) }
            BasicText("${h.armies} armies", style = Label.copy(fontWeight = FontWeight.Bold))
        }
        game.split(id)?.let { split ->
            val parts = listOf(
                Triple(split.canMove, colour, "can move"),
                Triple(split.stays, colour.darker(0.35f), "stays"),
                Triple(split.fatigued, Color(0xFFBDB4A3), "fatigued"),
            )
            StackedBar(parts.map { Segment(it.first.toFloat(), it.second) }, Modifier.fillMaxWidth().height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((n, c, label) in parts) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(10.dp).background(c, RoundedCornerShape(2.dp)))
                        BasicText("$n $label", style = Small)
                    }
                }
            }
        }

        val defended = p.neighbours.mapNotNull { n -> game.map.border(id, n)?.feature?.let { n to it } }
        if (defended.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText("Defends", style = Small, modifier = Modifier.width(52.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for ((n, feature) in defended) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(16.dp) { featureIcon(feature) }
                            BasicText("${game.map.provinces[n].name} ×${feature.defence}", style = Small, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (!compact && game.isHumanTurn && game.movable(id) > 0) {
            BasicText(
                "Pick a neighbour to send ${game.movable(id)} · long-press or right-click it to choose how many",
                style = Small.copy(color = Palette.Ink.copy(alpha = 0.7f)),
            )
        }
    }
}
