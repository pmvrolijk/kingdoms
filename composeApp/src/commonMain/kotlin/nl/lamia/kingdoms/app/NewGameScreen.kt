package nl.lamia.kingdoms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.rules.Difficulty
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.NewGame
import nl.lamia.kingdoms.core.rules.TurnOrder
import kotlin.random.Random

/** Everything a new game is made from (Design §6.2). The first seat is the player. */
@Serializable
data class Setup(
    val landProvinces: Int = MapSize.Medium.landProvinces,
    val seed: Long = 1,
    val turnOrder: TurnOrder = TurnOrder.Shuffled,
    val seats: List<NewGame.Seat> = NewGame.seats(4),
) {
    val config get() = MapConfig(seed = seed, landProvinces = landProvinces)
    val fits get() = config.fits(seats.size)

    companion object {
        fun randomSeed() = Random.nextLong(1, 1_000_000)
    }
}

/**
 * Title and new-game screen: settings on the left, a live preview of the map with everyone's
 * starting territory on the right. With a saved game, [resume], Continue sits above Start.
 */
@Composable
fun NewGameScreen(
    setup: Setup,
    onChange: (Setup) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    resume: GameState? = null,
    onContinue: () -> Unit = {},
    onHelp: () -> Unit = {},
    onAbout: () -> Unit = {},
) {
    val map = remember(setup.config) { MapGenerator.generate(setup.config) }
    val preview = remember(map, setup.seats, setup.turnOrder) {
        if (setup.fits) NewGame.create(map, setup.seats, setup.seed, setup.turnOrder) else null
    }
    val camera = remember(map) { CameraState() }

    Row(modifier.background(Palette.Sea)) {
        // Settings scroll; Start stays pinned under them, so it is always on screen (phones).
        // The insets go outside the width: a camera cutout must not narrow the panel.
        Column(Modifier.safeDrawingPadding().padding(12.dp).width(356.dp).panel()) {
          Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("Kingdoms", style = Label.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold))
                Spacer(Modifier.weight(1f))
                val sounds = LocalSounds.current
                Chip(if (sounds.settings.muted) "Sound off" else "Sound on", active = !sounds.settings.muted, small = true) {
                    sounds.change(sounds.settings.copy(muted = !sounds.settings.muted))
                }
                Spacer(Modifier.width(5.dp))
                Chip("How to play", small = true, onClick = onHelp)
            }

            Section("Map") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (size in MapSize.entries) {
                        Chip(size.name, active = setup.landProvinces == size.landProvinces) { onChange(setup.copy(landProvinces = size.landProvinces)) }
                    }
                    Chip("−", enabled = setup.landProvinces - 2 >= setup.seats.size * MapConfig.MIN_PROVINCES_PER_KINGDOM) {
                        onChange(setup.copy(landProvinces = setup.landProvinces - 2))
                    }
                    Chip("+") { onChange(setup.copy(landProvinces = setup.landProvinces + 2)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("${setup.landProvinces} provinces · seed", style = Small)
                    BasicTextField(
                        setup.seed.toString(),
                        onValueChange = { text -> text.filter(Char::isDigit).take(9).toLongOrNull()?.let { onChange(setup.copy(seed = it)) } },
                        singleLine = true,
                        textStyle = Label,
                        cursorBrush = SolidColor(Palette.Ink),
                        modifier = Modifier.width(90.dp).background(Palette.Selected, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                    Chip("Shuffle") { onChange(setup.copy(seed = Setup.randomSeed())) }
                }
            }

            Section("Turns") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (order in listOf(TurnOrder.Shuffled, TurnOrder.Rotating, TurnOrder.Fixed)) {
                        Chip(order.name, active = order == setup.turnOrder) { onChange(setup.copy(turnOrder = order)) }
                    }
                }
            }

            Section("Kingdoms") {
                setup.seats.forEachIndexed { i, seat ->
                    SeatRow(
                        seat,
                        canRemove = i > 0 && setup.seats.size > NewGame.MIN_KINGDOMS,
                        onChange = { changed -> onChange(setup.copy(seats = setup.seats.toMutableList().also { it[i] = changed })) },
                        onNextColour = {
                            val taken = setup.seats.map { it.colour }.toSet()
                            val next = (1..Palette.kingdoms.size).map { (seat.colour + it) % Palette.kingdoms.size }.first { it !in taken || it == seat.colour }
                            onChange(setup.copy(seats = setup.seats.toMutableList().also { it[i] = seat.copy(colour = next) }))
                        },
                        onRemove = { onChange(setup.copy(seats = setup.seats.filterIndexed { j, _ -> j != i })) },
                    )
                }
                if (setup.seats.size < NewGame.MAX_KINGDOMS) {
                    Chip("Add a kingdom") {
                        val colour = Palette.kingdoms.indices.first { c -> setup.seats.none { it.colour == c } }
                        val name = NewGame.NAMES.firstOrNull { n -> setup.seats.none { it.name == n } } ?: "Kingdom ${setup.seats.size + 1}"
                        onChange(setup.copy(seats = setup.seats + NewGame.Seat(name, colour, human = false)))
                    }
                }
            }

            if (!setup.fits) {
                BasicText(
                    "${setup.seats.size} kingdoms need at least ${setup.seats.size * MapConfig.MIN_PROVINCES_PER_KINGDOM} provinces",
                    style = Small.copy(color = Palette.kingdom(1).darker(0.2f)),
                )
            }

            // At the end of the list: the header has no room for it on a phone (Design §6.5).
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(COPYRIGHT, style = Small.copy(color = Palette.Ink.copy(alpha = 0.6f)), modifier = Modifier.weight(1f))
                Chip("About", small = true, onClick = onAbout)
            }
          }
          Spacer(Modifier.height(10.dp))
          if (resume != null) {
              // Side by side: on a landscape phone a second row of buttons hides the kingdoms.
              val you = resume.kingdoms.firstOrNull { it.human }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  ActionButton(
                      "Continue · round ${resume.round}",
                      you?.let { resume.colourOf(it.id) } ?: Palette.Ink,
                      modifier = Modifier.weight(1.4f),
                      onClick = onContinue,
                  )
                  ActionButton("Start new", Palette.Ink.lighter(0.3f), enabled = setup.fits, modifier = Modifier.weight(1f), onClick = onStart)
              }
          } else {
              ActionButton("Start", Palette.kingdom(setup.seats.first().colour), enabled = setup.fits, onClick = onStart)
          }
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (preview != null) {
                MapView(
                    preview,
                    selected = null,
                    arrow = null,
                    cameraState = camera,
                    clashes = emptyList(),
                    now = 0L,
                    onTap = {},
                    onLongPress = {},
                    modifier = Modifier.fillMaxSize(),
                    interactive = false,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(title, style = Label.copy(fontWeight = FontWeight.Bold))
        content()
    }
}

/** A kingdom: colour (tap to change), name (editable), and who plays it. */
@Composable
private fun SeatRow(seat: NewGame.Seat, canRemove: Boolean, onChange: (NewGame.Seat) -> Unit, onNextColour: () -> Unit, onRemove: () -> Unit) {
    val colour = Palette.kingdom(seat.colour)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).background(colour, RoundedCornerShape(5.dp))
                .border(1.5.dp, colour.darker(0.45f), RoundedCornerShape(5.dp))
                .clickable(onClick = onNextColour)
                .testTag("colour-${seat.name}"),
        )
        BasicTextField(
            seat.name,
            onValueChange = { onChange(seat.copy(name = it.take(16))) },
            singleLine = true,
            textStyle = Label.copy(fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Palette.Ink),
            // The name gives way when the row is tight, so the difficulty chips never wrap.
            modifier = Modifier.weight(1f, fill = false).width(110.dp)
                .background(Palette.Selected, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
        )
        if (seat.human) {
            BasicText("You", style = Small.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 6.dp))
        } else {
            for (d in Difficulty.entries) {
                Chip(d.name, active = d == seat.difficulty, small = true) { onChange(seat.copy(difficulty = d)) }
            }
        }
        if (canRemove) BasicText("×", style = Label.copy(fontSize = 18.sp), modifier = Modifier.clickable(onClick = onRemove).padding(4.dp))
    }
}
