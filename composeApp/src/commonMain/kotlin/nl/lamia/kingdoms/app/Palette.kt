package nl.lamia.kingdoms.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import nl.lamia.kingdoms.core.map.Terrain
import nl.lamia.kingdoms.core.rules.GameState

/** Cozy cel-shaded palette (Design §8): flat fills, darker same-hue outlines, never black. */
object Palette {
    val Parchment = Color(0xFFF4E9D2)
    val Ink = Color(0xFF5B4632)
    val Selected = Color(0xFFFFF8E7)
    val Neutral = Color(0xFFE6DCC8)

    val Sea = Color(0xFF8ECFD9)
    val SeaDeep = Color(0xFF7CC2CF)
    val Wave = Color(0xFFC4ECF0)
    val Foam = Color(0xFFF4FBFA)
    val BoardShadow = Color(0x33285A6A)
    val Earth = Color(0xFFB08860)
    val EarthDark = Color(0xFF8A6744)

    val River = Color(0xFF6DB3DC)
    val RiverLight = Color(0xFFA6DBF0)
    val Pool = Color(0xFF8FD0DC)
    val PoolRim = Color(0xFFD4F1F4)

    val TreeDark = Color(0xFF4E9457)
    val TreeLight = Color(0xFF72B567)
    val TreeOutline = Color(0xFF2F6A3B)
    val Trunk = Color(0xFF8A6744)
    val HillBase = Color(0xFF9DC271)
    val HillLight = Color(0xFFBFD98F)
    val HillOutline = Color(0xFF6A8C47)
    val RockLight = Color(0xFFBCB2A6)
    val RockDark = Color(0xFF928679)
    val RockOutline = Color(0xFF5E544C)
    val Snow = Color(0xFFF8F6F1)
    val SnowShade = Color(0xFFDCE3EA)
    val Reed = Color(0xFF6F8C4C)
    val Cattail = Color(0xFF8A5F3C)
    val Field = Color(0xFFE8D58C)
    val FieldStripe = Color(0xFFCDB869)
    val FieldOutline = Color(0xFFA8955A)

    fun terrain(t: Terrain) = when (t) {
        Terrain.Plains -> Color(0xFFD5E28E)
        Terrain.Hills -> Color(0xFFB7D38A)
        Terrain.Forest -> Color(0xFF9CC77E)
        Terrain.Mountains -> Color(0xFFCFC5AF)
        Terrain.Marsh -> Color(0xFFAAC6A2)
    }

    /** The colours a kingdom can wear; [nl.lamia.kingdoms.core.rules.Kingdom.colour] indexes this. */
    val kingdoms = listOf(
        Color(0xFF4A7BD0), // blue
        Color(0xFFD0524A), // red
        Color(0xFFD9A21B), // gold
        Color(0xFF8C5BC4), // purple
        Color(0xFF2A9D8F), // teal
        Color(0xFFE07A2E), // orange
        Color(0xFFD6649A), // rose
        Color(0xFF5B6B8C), // slate
    )

    fun kingdom(colour: Int) = kingdoms[colour.mod(kingdoms.size)]
}

/** Kingdom [k]'s chosen colour. */
fun GameState.colourOf(k: Int) = Palette.kingdom(kingdoms[k].colour)

/** The same hue, darker: cel outlines and shade bands. */
fun Color.darker(amount: Float = 0.3f) = lerp(this, Color(0xFF2A2420), amount)

fun Color.lighter(amount: Float = 0.3f) = lerp(this, Color.White, amount)
