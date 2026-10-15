package nl.lamia.kingdoms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapSize

private val Parchment = Color(0xFFF4E9D2)
private val Ink = Color(0xFF5B4632)

@Composable
fun App() {
    val config = MapConfig.of(MapSize.Medium, seed = 1)
    Column(
        modifier = Modifier.fillMaxSize().background(Parchment),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText("Kingdoms", style = TextStyle(color = Ink, fontSize = 48.sp, fontWeight = FontWeight.Bold))
        BasicText(
            "${MapSize.Medium} map · ${config.landProvinces} provinces",
            style = TextStyle(color = Ink, fontSize = 18.sp),
        )
    }
}
