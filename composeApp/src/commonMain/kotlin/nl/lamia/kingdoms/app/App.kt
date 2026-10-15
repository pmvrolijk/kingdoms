package nl.lamia.kingdoms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.rules.GameState
import nl.lamia.kingdoms.core.rules.NewGame
import nl.lamia.kingdoms.core.rules.TurnOrder
import kotlin.random.Random
import kotlin.time.measureTimedValue

@Composable
fun App(onGameChange: (GameState) -> Unit = {}) {
    var config by remember { mutableStateOf(MapConfig.of(MapSize.Medium, seed = 1)) }
    var kingdoms by remember { mutableStateOf(4) }
    var restarts by remember { mutableStateOf(0) }
    var turnOrder by remember { mutableStateOf(TurnOrder.Shuffled) }
    val (map, took) = remember(config) { measureTimedValue { MapGenerator.generate(config) } }
    val game = remember(map, kingdoms, restarts, turnOrder) {
        NewGame.create(map, kingdoms, seed = config.seed + restarts, turnOrder = turnOrder)
    }

    Box(Modifier.fillMaxSize().background(Palette.DeepSea)) {
        GameScreen(
            game,
            onNewGame = { config = config.copy(seed = Random.nextLong(1, 1_000_000)) },
            Modifier.fillMaxSize(),
            onChange = onGameChange,
        )
        Column(
            Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DebugBar(
                config,
                kingdoms,
                turnOrder,
                onConfig = { config = it },
                onKingdoms = { kingdoms = it },
                onTurnOrder = { turnOrder = it },
                onRestart = { restarts++ },
            )
            BasicText(
                "seed ${config.seed} · ${map.provinces.size} provinces · ${map.borders.size} borders · ${took.inWholeMilliseconds} ms",
                style = Label,
                modifier = Modifier.panel(),
            )
        }
    }
}

/** Regenerate with another seed, size or kingdom count; restart on the same map. */
@Composable
private fun DebugBar(
    config: MapConfig,
    kingdoms: Int,
    turnOrder: TurnOrder,
    onConfig: (MapConfig) -> Unit,
    onKingdoms: (Int) -> Unit,
    onTurnOrder: (TurnOrder) -> Unit,
    onRestart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("New map") { onConfig(config.copy(seed = Random.nextLong(1, 1_000_000))) }
            Chip("Restart", onClick = onRestart)
            MapSize.entries.forEach { size ->
                Chip(size.name, active = config.landProvinces == size.landProvinces, enabled = config.copy(landProvinces = size.landProvinces).fits(kingdoms)) {
                    onConfig(config.copy(landProvinces = size.landProvinces))
                }
            }
            Chip("−5", enabled = config.landProvinces - 5 >= kingdoms * MapConfig.MIN_PROVINCES_PER_KINGDOM) {
                onConfig(config.copy(landProvinces = config.landProvinces - 5))
            }
            Chip("+5") { onConfig(config.copy(landProvinces = config.landProvinces + 5)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Kingdoms", style = Label, modifier = Modifier.panel())
            for (k in NewGame.MIN_KINGDOMS..NewGame.MAX_KINGDOMS) {
                Chip("$k", active = k == kingdoms, enabled = config.fits(k)) { onKingdoms(k) }
            }
            BasicText("Turns", style = Label, modifier = Modifier.panel())
            for (order in TurnOrder.entries) Chip(order.name, active = order == turnOrder) { onTurnOrder(order) }
        }
    }
}
