package nl.lamia.kingdoms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding

val Label = TextStyle(color = Palette.Ink, fontSize = 14.sp)

fun Modifier.panel() = background(Palette.Parchment.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
    .padding(horizontal = 10.dp, vertical = 6.dp)

@Composable
fun Chip(text: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val style = Label.copy(
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (enabled) Palette.Ink else Palette.Ink.copy(alpha = 0.35f),
    )
    BasicText(text, style = style, modifier = Modifier.panel().clickable(enabled = enabled, onClick = onClick))
}
