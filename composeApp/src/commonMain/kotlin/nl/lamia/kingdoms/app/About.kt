package nl.lamia.kingdoms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val COPYRIGHT = "© 2026 Marcel Vrolijk"
const val LICENSE_URL = "https://www.gnu.org/licenses/agpl-3.0.html"
const val SOURCE_URL = "https://github.com/pmvrolijk/kingdoms"

/**
 * Copyright, licence and where the source is (Design §6.5). Reachable from the new-game screen
 * and the in-game menu. Taps do not reach what is under it.
 */
@Composable
fun AboutScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val uris = LocalUriHandler.current
    // No browser to open (or a platform that refuses) must not crash the game.
    fun open(url: String) = runCatching { uris.openUri(url) }
    Box(
        modifier
            .background(Palette.Ink.copy(alpha = 0.4f))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.safeDrawingPadding().padding(10.dp).width(340.dp).panel().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText("Kingdoms", style = Label.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
            BasicText(COPYRIGHT, style = Label)
            BasicText(
                "Free software: you may share and change it under the GNU Affero General Public " +
                    "License, version 3 or later. It comes with no warranty.",
                style = Small,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("Licence", small = true) { open(LICENSE_URL) }
                Chip("Source code", small = true) { open(SOURCE_URL) }
                Spacer(Modifier.weight(1f))
                Chip("Close", small = true, onClick = onClose)
            }
        }
    }
}
