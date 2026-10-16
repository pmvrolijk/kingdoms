package nl.lamia.kingdoms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lamia.kingdoms.core.map.Feature
import nl.lamia.kingdoms.core.map.Resource
import nl.lamia.kingdoms.core.map.Terrain
import nl.lamia.kingdoms.core.rules.Battle
import kotlin.math.roundToInt

/** The help screen's tabs (Design §6.3). Every one but Tactics has an animated scene. */
enum class HelpTopic(val title: String) {
    Goal("Goal"),
    Moving("Moving"),
    Attack("Attack"),
    Strength("Strength"),
    Borders("Borders"),
    Growth("Growth"),
    Tactics("Tactics");

    fun scene(): Scene? = when (this) {
        Goal -> goalScene()
        Moving -> moveScene()
        Attack -> attackScene()
        Strength -> strengthScene()
        Borders -> defenceScene()
        Growth -> growthScene()
        Tactics -> null
    }
}

/** A line of help text, with an icon in front when it helps. */
class Tip(val text: String, val icon: (DrawScope.() -> Unit)? = null)

/**
 * How to play: tabs along the top; each shows a looping scene on the left and a few lines on the
 * right. Reachable from the new-game screen and the in-game menu. Taps do not reach what is under it.
 */
@Composable
fun HelpScreen(onClose: () -> Unit, modifier: Modifier = Modifier, initial: HelpTopic = HelpTopic.Goal) {
    var topic by remember { mutableStateOf(initial) }
    Box(
        modifier
            .background(Palette.Ink.copy(alpha = 0.4f))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.safeDrawingPadding().padding(10.dp).widthIn(max = 980.dp).heightIn(max = 600.dp).fillMaxSize().panel(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText("How to play", style = Label.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (t in HelpTopic.entries) Chip(t.title, active = t == topic, small = true) { topic = t }
                }
                Chip("Close", small = true, onClick = onClose)
            }
            key(topic) {
                val scene = remember { topic.scene() }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (scene != null) HelpScene(scene, Modifier.weight(1.15f).fillMaxHeight())
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (tip in tips(topic)) TipRow(tip)
                        HelpTopic.entries.getOrNull(topic.ordinal + 1)?.let { next ->
                            Row {
                                Spacer(Modifier.weight(1f))
                                Chip("Next: ${next.title}", small = true) { topic = next }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TipRow(tip: Tip) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        tip.icon?.let { Icon(20.dp, it) } ?: Spacer(Modifier.size(20.dp))
        BasicText(tip.text, style = Label)
    }
}

private fun percent(attackers: Int, defenders: Int) = (Battle.winChance(attackers, defenders, 1.0) * 100).roundToInt()

private fun kept(attackers: Int, defenders: Int) = Battle.survivors(attackers, attackers.toDouble(), defenders.toDouble())

/** The words follow Design §4; the numbers come from core. */
fun tips(topic: HelpTopic): List<Tip> {
    val you = Palette.kingdom(0)
    val rival = Palette.kingdom(1)
    return when (topic) {
        HelpTopic.Goal -> listOf(
            Tip("Kingdoms take turns. On yours, make as many moves as you like, then tap End turn."),
            Tip("Each kingdom starts with a capital, the tower, and the two provinces beside it.") { armyIcon(you) },
            Tip("Grey provinces are neutral. They never grow: easy land early on.") { armyIcon(Palette.Neutral) },
            Tip("The last kingdom standing wins. Lose every province and your game is over.") { armyIcon(rival) },
            Tip("The bars at the top left compare everyone's growth, land and armies; yours is outlined."),
        )
        HelpTopic.Moving -> listOf(
            Tip("Tap one of your provinces, then a neighbour: all its armies march there but one, which always stays behind."),
            Tip("To send fewer, right-click or long-press the neighbour and pick how many."),
            Tip("Armies that moved are fatigued until your next turn, so an army goes one step per turn. A white dot marks a province whose armies can still move."),
            Tip("There is no sea travel: armies walk from province to province."),
        )
        HelpTopic.Attack -> listOf(
            Tip("Tap a neutral or enemy neighbour the same way to attack it. The label above it is your chance to win.") { armyIcon(you) },
            Tip("Win, and the province is yours: the survivors move in, fatigued."),
            Tip("Lose, and every attacker is lost. The defender keeps its survivors.") { armyIcon(rival) },
            Tip("Long-press the target first to see how many would survive a win."),
        )
        HelpTopic.Strength -> listOf(
            Tip("Each side's strength gets a little luck, up to 10% either way. The stronger side wins; a tie goes to the defender."),
            Tip("6 against 6 wins ${percent(6, 6)}% of the time, 7 against 6 ${percent(7, 6)}%, 8 against 6 ${percent(8, 6)}%."),
            Tip("Close fights are costly: 7 that beat 6 keep about ${kept(7, 6)}, while 12 keep about ${kept(12, 6)}."),
            Tip("So gather first, then strike once with everything.") { armyIcon(you) },
        )
        HelpTopic.Borders -> listOf(Tip("A feature on a border makes the defender stronger, whichever side attacks across it:")) +
            Feature.entries.map { f -> Tip("${f.title}: defenders count ×${decimal(f.defence, 2)}") { featureIcon(f) } } +
            listOf(
                Tip("The land itself gives no defence: only the border counts."),
                Tip("Hold a line behind rivers and mountains; attack where the border is open."),
            )
        HelpTopic.Growth -> listOf(
            Tip("At the end of every round, each owned province grows armies where it stands: one per icon under its count."),
            Tip("Plains grow 2.") { resourceIcon(Resource.Grain) },
            Tip("Hills, forest, mountains and marsh grow 1.") { resourceIcon(Terrain.Hills.resource) },
            Tip("On the coast, 1 more.") { resourceIcon(Resource.Fish) },
            Tip("Neutral provinces never grow. Your armies don't walk to the front by themselves: bring them, one step per turn."),
        )
        HelpTopic.Tactics -> listOf(
            Tip("Concentrate. One big attack wins cheaply; two small ones can both be lost.") { armyIcon(you) },
            Tip("Grab rich land early: plains and coasts grow the most, and neutrals are cheap.") { resourceIcon(Resource.Grain) },
            Tip("Make your border behind rivers and mountains, and attack where it is open.") { featureIcon(Feature.River) },
            Tip("A province just taken holds only the survivors: strike back while it is weak.") { armyIcon(rival) },
            Tip("Watch the strength bars. When one kingdom pulls ahead, help bring it down."),
        )
    }
}
