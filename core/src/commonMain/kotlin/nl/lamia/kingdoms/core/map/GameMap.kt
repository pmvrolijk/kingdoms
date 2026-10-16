package nl.lamia.kingdoms.core.map

import kotlinx.serialization.Serializable

/** A land province. [outline] is a closed ring; [site] always lies inside it. */
@Serializable
data class Province(
    val id: Int,
    /** A generated place name, unique within the map. */
    val name: String,
    val site: Point,
    val terrain: Terrain,
    /** Borders the sea (any sea, including inland seas). */
    val coastal: Boolean,
    val outline: List<Point>,
    val neighbours: List<Int>,
) {
    /** Armies grown per round (Design §4.5). */
    val yield: Int get() = terrain.yield + if (coastal) COASTAL_YIELD else 0

    val resources: List<Resource> get() = if (coastal) listOf(terrain.resource, Resource.Fish) else listOf(terrain.resource)
}

/** The shared border between land provinces [a] < [b]; both outlines contain [path] exactly. */
@Serializable
data class Border(val a: Int, val b: Int, val path: List<Point>, val feature: Feature?) {
    val defence: Double get() = feature?.defence ?: 1.0
}

/** A stretch of coastline: a land province's edge against sea. */
@Serializable
data class Shore(val province: Int, val path: List<Point>)

/** The generated map. Provinces are numbered 0 until `provinces.size`. */
@Serializable
data class GameMap(
    val config: MapConfig,
    val width: Double,
    val height: Double,
    val provinces: List<Province>,
    val borders: List<Border>,
    val shores: List<Shore>,
    /** Outlines of the sea cells, including inland seas; together with the provinces they tile the map. */
    val seas: List<List<Point>>,
) {
    private val bounds by lazy { provinces.map { Bounds.of(it.outline) } }
    private val bordersByPair by lazy { borders.associateBy { pairKey(it.a, it.b) } }

    fun border(a: Int, b: Int): Border? = bordersByPair[pairKey(minOf(a, b), maxOf(a, b))]

    /** The province containing [p], or null for sea and off-map points. */
    fun provinceAt(p: Point): Province? =
        provinces.firstOrNull { p in bounds[it.id] && Polygons.contains(it.outline, p) }

    private fun pairKey(a: Int, b: Int) = a.toLong() shl 32 or b.toLong()
}
