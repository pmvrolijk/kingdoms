package nl.lamia.kingdoms.core.map

import kotlin.random.Random

/**
 * Cozy English-countryside province names: a prefix and a suffix that fits the terrain (a
 * forest ends in -wood or -holt, a coast often in -haven or -mouth). Unique within a map and
 * the same for the same seed.
 */
internal object ProvinceNames {
    private val prefixes = listOf(
        "Ash", "Amber", "Barley", "Bell", "Bram", "Briar", "Clover", "Cold", "Copper", "Dew", "Dun",
        "Elder", "Elm", "Ember", "Fair", "Fern", "Fox", "Glen", "Gold", "Hart", "Hazel", "Heather",
        "Holly", "Honey", "Iron", "Ivy", "Kestrel", "Kings", "Lark", "Linden", "Maple", "Marl", "Mill",
        "Moss", "Nettle", "North", "Oak", "Otter", "Pine", "Poppy", "Raven", "Red", "Rose", "Rowan",
        "Rush", "Salt", "Silver", "Sorrel", "Stone", "Swan", "Tansy", "Thorn", "Wil", "Willow", "Wren", "Yew",
    )

    private val suffixes = mapOf(
        Terrain.Plains to listOf("ford", "field", "ham", "stead", "ton", "wick", "by", "worth"),
        Terrain.Hills to listOf("down", "ridge", "brae", "knoll", "hill", "cote", "ley"),
        Terrain.Forest to listOf("wood", "holt", "hurst", "glade", "grove", "shaw"),
        Terrain.Mountains to listOf("crag", "fell", "tor", "peak", "scar", "cairn"),
        Terrain.Marsh to listOf("fen", "mere", "moss", "carr", "wash", "slade"),
    )

    private val coastal = listOf("haven", "port", "strand", "mouth", "bay", "wick")

    fun generate(provinces: List<Pair<Terrain, Boolean>>, random: Random): List<String> {
        val used = mutableSetOf<String>()
        return provinces.map { (terrain, onCoast) ->
            var name: String
            var tries = 0
            do {
                val prefix = prefixes.random(random)
                val endings = if (onCoast && random.nextInt(3) == 0) coastal else suffixes.getValue(terrain)
                val suffix = endings.random(random)
                name = prefix + suffix
                tries++
                // "Willowwood" and "Mossmoss" read badly; retry those and repeats.
                val clash = prefix.last().lowercaseChar() == suffix.first() || prefix.lowercase() == suffix
            } while ((name in used || clash) && tries < 200)
            if (name in used) name += " ${used.count { it.startsWith(name) } + 1}"
            used += name
            name
        }
    }
}
