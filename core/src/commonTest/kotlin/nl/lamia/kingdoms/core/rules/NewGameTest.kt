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
    fun theFirstMoverInAHeadToHeadGetsTheBonus() {
        val map = Fixtures.map
        for (order in TurnOrder.entries) {
            val game = NewGame.create(map, kingdoms = 2, seed = 4, turnOrder = order)
            val growth = game.kingdoms.map { game.growthOf(it.id) }
            val bonus = if (order == TurnOrder.Rotating) 0 else NewGame.HEAD_TO_HEAD_BONUS
            for (k in game.kingdoms) {
                val expected = NewGame.CAPITAL_ARMIES + (growth.max() - growth[k.id]) * NewGame.COMPENSATION +
                    if (k.id == 0) bonus else 0
                assertEquals(expected, game.holdings[k.capital].armies, "$order kingdom ${k.id}")
            }
        }
    }

    @Test
    fun startsAreNearlyEven() = games.forEach { game ->
        val growth = game.kingdoms.map { game.growthOf(it.id) }
        // Hinterland comes first, so growth may differ more; compensation makes up for it (Design §4.1).
        assertTrue(growth.max() - growth.min() <= 6, "growth $growth, seed ${game.seed}")
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
    fun seatsCarryNameColourAndDifficulty() {
        val seats = listOf(
            NewGame.Seat("Ours", colour = 6, human = true),
            NewGame.Seat("Theirs", colour = 2, human = false, difficulty = Difficulty.Hard),
            NewGame.Seat("Others", colour = 7, human = false, difficulty = Difficulty.Easy),
        )
        val game = NewGame.create(Fixtures.map, seats, seed = 5)
        assertEquals(listOf("Ours", "Theirs", "Others"), game.kingdoms.map { it.name })
        assertEquals(listOf(6, 2, 7), game.kingdoms.map { it.colour })
        assertEquals(listOf(Difficulty.Normal, Difficulty.Hard, Difficulty.Easy), game.kingdoms.map { it.difficulty })
        assertEquals(listOf(true, false, false), game.kingdoms.map { it.human })
    }

    @Test
    fun peopleMoveFirstInRoundOneWhereverTheySit() {
        val seats = NewGame.seats(4, humans = setOf(2))
        val game = NewGame.create(Fixtures.map, seats, seed = 5)
        assertEquals(2, game.current)
        assertEquals(2, game.order.first())
        assertEquals(listOf(0, 1, 2, 3), game.order.sorted())
        game.provincesOf(2).forEach { assertEquals(game.holdings[it].armies, game.holdings[it].fresh) }
        game.provincesOf(0).forEach { assertEquals(0, game.holdings[it].fresh) }
    }

    @Test
    fun hotSeatDrawsWhichPersonGoesFirstThenTheOthersBySeatThenTheAis() {
        val seats = NewGame.seats(5, humans = setOf(0, 2, 3))
        val firsts = (1L..40L).map { seed ->
            val game = NewGame.create(Fixtures.map, seats, seed = seed)
            val people = game.order.take(3)
            assertEquals(listOf(0, 2, 3), people.sorted(), "seed $seed: ${game.order}")
            // The others follow the first in seat order, wrapping round.
            assertTrue(people in listOf(listOf(0, 2, 3), listOf(2, 3, 0), listOf(3, 0, 2)), "seed $seed: $people")
            assertEquals(game.order.first(), game.current)
            game.provincesOf(game.current).forEach { assertEquals(game.holdings[it].armies, game.holdings[it].fresh) }
            game.order.first()
        }
        assertEquals(setOf(0, 2, 3), firsts.toSet(), "every person goes first for some seed")
    }

    @Test
    fun underFixedOrderRoundOnesOrderHoldsAllGame() {
        val game = NewGame.create(Fixtures.map, NewGame.seats(4, humans = setOf(1, 3)), seed = 7, turnOrder = TurnOrder.Fixed)
        var state = game
        while (state.round < 3) state = (Rules.apply(state, Action.EndTurn) as Outcome.Applied).state
        assertEquals(game.order, state.order)
        assertEquals(game.order.first(), state.current)
    }

    @Test
    fun onePersonDrawsNothingSoSeedsSetUpAsBefore() {
        // Pinned before hot-seat (session 1m): a draw for one person would shift every later random
        // number, and old seeds would start differently.
        val game = NewGame.create(MapGenerator.generate(MapConfig.of(MapSize.Medium, seed = 5)), kingdoms = 4, seed = 5)
        assertEquals(listOf(0, 1, 2, 3), game.order)
        assertEquals(listOf(22, 20, 11, 6), game.kingdoms.map { it.capital })
        assertEquals(listOf(3, 2, 2, 3, 3, 1, 3, 3, 2, 1, 2, 3, 3, 1, 3, 3), game.holdings.filter { it.owner == null }.map { it.armies })
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
