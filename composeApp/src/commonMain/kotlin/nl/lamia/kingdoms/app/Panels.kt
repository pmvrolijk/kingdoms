package nl.lamia.kingdoms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
fun Chip(text: String, active: Boolean = false, enabled: Boolean = true, small: Boolean = false, onClick: () -> Unit) {
    val style = Label.copy(
        fontSize = if (small) 12.sp else 14.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (enabled) Palette.Ink else Palette.Ink.copy(alpha = 0.35f),
    )
    val shape = RoundedCornerShape(7.dp)
    BasicText(
        text,
        style = style,
        softWrap = false,
        maxLines = 1,
        modifier = Modifier
            .background(if (active) Palette.Selected else Palette.Selected.copy(alpha = 0.7f), shape)
            .border(if (active) 1.5.dp else 1.dp, Palette.Ink.copy(alpha = if (active) 0.7f else 0.25f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            // About 28 and 34 dp tall: a fingertip, without crowding a phone-height panel.
            .padding(horizontal = if (small) 8.dp else 11.dp, vertical = if (small) 5.dp else 7.dp),
    )
}
