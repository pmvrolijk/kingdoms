package nl.lamia.kingdoms.app

import androidx.compose.ui.graphics.Color
import nl.lamia.kingdoms.core.map.Feature
import nl.lamia.kingdoms.core.map.Terrain

/** Flat M1 colours; the cel-shaded style replaces these in M4. */
object Palette {
    val Parchment = Color(0xFFF4E9D2)
    val Ink = Color(0xFF5B4632)
    val Sea = Color(0xFF8EC9D9)
    val DeepSea = Color(0xFF6FAFC4)
    val Shore = Color(0xFF4F7F8C)
    val Border = Color(0x805B4632)
    val Selected = Color(0xFFFFF8E7)
    val Neighbour = Color(0x40FFF8E7)
    val Neutral = Color(0xFFD8D0C0)

    private val kingdoms = listOf(
        Color(0xFF4A7BD0), // blue
        Color(0xFFD0524A), // red
        Color(0xFFD9A21B), // gold
        Color(0xFF8C5BC4), // purple
        Color(0xFF2A9D8F), // teal
        Color(0xFFE07A2E), // orange
    )

    fun kingdom(id: Int) = kingdoms[id % kingdoms.size]

    fun terrain(t: Terrain) = when (t) {
        Terrain.Plains -> Color(0xFFC9DE8A)
        Terrain.Hills -> Color(0xFFA9C77A)
        Terrain.Forest -> Color(0xFF6FA463)
        Terrain.Mountains -> Color(0xFFA79C8E)
        Terrain.Marsh -> Color(0xFF8FA88A)
    }

    fun feature(f: Feature) = when (f) {
        Feature.River -> Color(0xFF4E9AC7)
        Feature.Ridge -> Color(0xFF8B6B4A)
        Feature.Marsh -> Color(0xFF5E7348)
        Feature.Lake -> Color(0xFF7DC3E0)
        Feature.MountainRange -> Color(0xFF6E6259)
    }

    /** Stroke width of a feature in screen pixels. */
    fun featureWidth(f: Feature) = when (f) {
        Feature.River -> 4f
        Feature.Ridge, Feature.Marsh -> 5f
        Feature.Lake -> 9f
        Feature.MountainRange -> 7f
    }
}
