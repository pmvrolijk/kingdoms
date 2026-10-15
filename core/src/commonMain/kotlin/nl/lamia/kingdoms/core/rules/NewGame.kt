package nl.lamia.kingdoms.core.rules

import nl.lamia.kingdoms.core.map.GameMap
import nl.lamia.kingdoms.core.map.mix
import kotlin.random.Random

/** Game setup (Design §4.1). */
object NewGame {
    const val MIN_KINGDOMS = 2
    const val MAX_KINGDOMS = 6
    const val CAPITAL_ARMIES = 5
    const val START_ARMIES = 2

    /** Capital plus this many neighbours. */
    const val START_NEIGHBOURS = 2

    /** Candidate capital layouts tried; the fairest of the (nearly) most spread-out wins. */
    private const val LAYOUTS = 80

    /** A poorer start gets this many rounds of its growth shortfall as extra capital armies. */
    const val COMPENSATION = 3

    val NAMES = listOf("Aldmere", "Brightvale", "Corrindor", "Dunmoor", "Eastwatch", "Fernholt")

    private const val SETUP = 100L

    fun create(
        map: GameMap,
        kingdoms: Int,
        seed: Long,
        humans: Set<Int> = setOf(0),
        turnOrder: TurnOrder = TurnOrder.Fixed,
    ): GameState {
        require(kingdoms in MIN_KINGDOMS..MAX_KINGDOMS) { "kingdoms must be $MIN_KINGDOMS..$MAX_KINGDOMS, was $kingdoms" }
        require(map.config.fits(kingdoms)) { "${map.provinces.size} provinces are too few for $kingdoms kingdoms" }
        val random = Random(mix(seed, SETUP))
        val distances = map.provinces.indices.map { distancesFrom(map, it) }
        // Fairness first, among layouts at most one step less spread out than the best.
        val layouts = (1..LAYOUTS).map { layout(map, kingdoms, distances, random) }
        val spreadOut = layouts.maxOf { it.minDistance } - 1
        val best = layouts.filter { it.minDistance >= spreadOut }
            .minWith(compareBy<Layout> { it.growthSpread }.thenBy { -it.minDistance })
        // Seats are shuffled over the territories, so turn order says nothing about map position.
        val seat = (0 until kingdoms).shuffled(random)
        val capitals = (0 until kingdoms).map { k -> best.capitals[seat.indexOf(k)] }
        val holdings = map.provinces.indices.map { p ->
            when (val t = best.owner[p]) {
                null -> Holding(owner = null, armies = random.nextInt(1, 4))
                else -> {
                    val k = seat[t]
                    val armies = if (p == capitals[k]) CAPITAL_ARMIES else START_ARMIES
                    Holding(owner = k, armies = armies, fresh = if (k == 0) armies else 0)
                }
            }
        }
        // Compensation: a kingdom that starts poorer gets its shortfall, several rounds over, at its capital.
        val growth = (0 until kingdoms).map { k -> holdings.indices.filter { holdings[it].owner == k }.sumOf { map.provinces[it].yield } }
        val compensated = holdings.mapIndexed { p, h ->
            val k = h.owner
            if (k != null && p == capitals[k]) {
                val extra = (growth.max() - growth[k]) * COMPENSATION
                h.copy(armies = h.armies + extra, fresh = if (h.fresh > 0) h.fresh + extra else 0)
            } else {
                h
            }
        }
        return GameState(
            map = map,
            seed = seed,
            kingdoms = (0 until kingdoms).map { Kingdom(it, NAMES[it], human = it in humans, capital = capitals[it]) },
            holdings = compensated,
            round = 1,
            current = 0,
            turnOrder = turnOrder,
        )
    }

    /** A candidate start: territory index per province, capital per territory, and its quality. */
    private class Layout(val owner: Array<Int?>, val capitals: List<Int>, val minDistance: Int, val growthSpread: Int)

    /**
     * One candidate: farthest-point capitals from a random first pick (with one refinement pass),
     * then starting neighbours claimed in passes where the poorest kingdom picks first and takes the
     * richest province next to its capital.
     */
    private fun layout(map: GameMap, count: Int, distances: List<IntArray>, random: Random): Layout {
        val order = map.provinces.indices.shuffled(random)
        val capitals = mutableListOf(order.first())
        while (capitals.size < count) {
            capitals += order.filter { it !in capitals }.maxBy { p -> capitals.minOf { distances[it][p] } }
        }
        for (k in capitals.indices) {
            val others = capitals.filterIndexed { i, _ -> i != k }
            capitals[k] = order.filter { it !in others }.maxBy { p -> others.minOf { distances[it][p] } }
        }
        val owner = arrayOfNulls<Int>(map.provinces.size)
        capitals.forEachIndexed { k, p -> owner[p] = k }
        fun growth(k: Int) = owner.indices.filter { owner[it] == k }.sumOf { map.provinces[it].yield }
        repeat(START_NEIGHBOURS) {
            for (k in (0 until count).sortedBy(::growth)) {
                val territory = owner.indices.filter { owner[it] == k }
                val options = territory.flatMap { map.provinces[it].neighbours }.distinct().filter { owner[it] == null }
                val nextToCapital = options.filter { it in map.provinces[capitals[k]].neighbours }
                (nextToCapital.ifEmpty { options }).maxByOrNull { map.provinces[it].yield }?.let { owner[it] = k }
            }
        }
        val growths = (0 until count).map(::growth)
        val minDistance = capitals.indices.minOf { i -> capitals.indices.filter { it != i }.minOf { distances[capitals[i]][capitals[it]] } }
        return Layout(owner, capitals, minDistance, growths.max() - growths.min())
    }

    private fun distancesFrom(map: GameMap, start: Int): IntArray {
        val distance = IntArray(map.provinces.size) { Int.MAX_VALUE }
        distance[start] = 0
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            for (n in map.provinces[p].neighbours) if (distance[n] == Int.MAX_VALUE) {
                distance[n] = distance[p] + 1
                queue += n
            }
        }
        return distance
    }
}
