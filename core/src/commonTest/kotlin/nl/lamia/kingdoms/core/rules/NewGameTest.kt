package nl.lamia.kingdoms.core.rules

import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NewGameTest {
    private val games = MapSize.entries.flatMap { size ->
        (1L..10L).map { seed -> NewGame.create(MapGenerator.generate(MapConfig.of(size, seed)), kingdoms = 4, seed = seed) }
    }

    @Test
    fun eachKingdomStartsWithItsCapitalAndTwoNeighbours() = games.forEach { game ->
        for (k in game.kingdoms) {
            val owned = game.provincesOf(k.id)
            assertEquals(1 + NewGame.START_NEIGHBOURS, owned.size, "kingdom ${k.id}, seed ${game.seed}")
            assertTrue(k.capital in owned)
        }
    }

    @Test
    fun startingArmiesWithCompensationForAPoorerStart() = games.forEach { game ->
        val growth = game.kingdoms.map { game.growthOf(it.id) }
        game.holdings.forEachIndexed { p, h ->
            when {
                h.owner == null -> assertTrue(h.armies in 1..3)
                p == game.kingdoms[h.owner].capital -> assertEquals(
                    NewGame.CAPITAL_ARMIES + (growth.max() - growth[h.owner]) * NewGame.COMPENSATION,
                    h.armies,
                )
                else -> assertEquals(NewGame.START_ARMIES, h.armies)
            }
        }
    }

    @Test
    fun startsAreNearlyEven() = games.forEach { game ->
        val growth = game.kingdoms.map { game.growthOf(it.id) }
        assertTrue(growth.max() - growth.min() <= 3, "growth $growth, seed ${game.seed}")
    }

    @Test
    fun capitalsAreNeverNeighbours() = games.forEach { game ->
        val capitals = game.kingdoms.map { it.capital }
        assertEquals(capitals.size, capitals.toSet().size)
        for (c in capitals) assertTrue(capitals.none { it in game.map.provinces[c].neighbours }, "seed ${game.seed}")
    }

    @Test
    fun theFirstKingdomIsHumanAndMovesFirst() = games.forEach { game ->
        assertEquals(listOf(true, false, false, false), game.kingdoms.map { it.human })
        assertEquals(0, game.current)
        assertEquals(1, game.round)
        game.provincesOf(0).forEach { assertEquals(game.holdings[it].armies, game.holdings[it].fresh) }
    }

    @Test
    fun sameSeedSameSetup() {
        val map = Fixtures.map
        assertEquals(NewGame.create(map, 4, seed = 9), NewGame.create(map, 4, seed = 9))
    }

    @Test
    fun rejectsMoreKingdomsThanTheMapHolds() {
        val small = MapGenerator.generate(MapConfig(seed = 1, landProvinces = 9))
        NewGame.create(small, kingdoms = 3, seed = 1)
        assertFailsWith<IllegalArgumentException> { NewGame.create(small, kingdoms = 4, seed = 1) }
    }
}
