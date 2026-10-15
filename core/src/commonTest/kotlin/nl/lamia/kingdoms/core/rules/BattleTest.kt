package nl.lamia.kingdoms.core.rules

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BattleTest {
    @Test
    fun equalForcesOnOpenGroundAreACoinFlip() {
        assertEquals(0.5, Battle.winChance(10, 10, 1.0), 1e-12)
    }

    @Test
    fun beyondTheNoiseTheOutcomeIsCertain() {
        // Noise spans 0.9..1.1, so a ratio above 1.1 / 0.9 cannot lose and below 0.9 / 1.1 cannot win.
        assertEquals(1.0, Battle.winChance(13, 10, 1.0))
        assertEquals(0.0, Battle.winChance(8, 10, 1.0))
        assertEquals(0.0, Battle.winChance(0, 10, 1.0))
    }

    @Test
    fun borderFeaturesFavourTheDefender() {
        assertTrue(Battle.winChance(12, 10, 1.5) < Battle.winChance(12, 10, 1.15))
        assertTrue(Battle.winChance(12, 10, 1.15) < Battle.winChance(12, 10, 1.0))
    }

    @Test
    fun winChanceMatchesTheBattleItPredicts() {
        val random = Random(42)
        for ((a, d, m) in listOf(Triple(10, 10, 1.0), Triple(11, 10, 1.0), Triple(12, 10, 1.15), Triple(7, 5, 1.3), Triple(3, 2, 1.5))) {
            val trials = 20_000
            val wins = (1..trials).count { Battle.resolve(a, d, m, random).attackerWon }
            val expected = Battle.winChance(a, d, m)
            assertTrue(abs(wins.toDouble() / trials - expected) < 0.015, "$a vs $d ×$m: ${wins.toDouble() / trials} vs $expected")
        }
    }

    @Test
    fun survivorsFollowTheSquareLaw() {
        assertEquals(10, Battle.survivors(10, 10.0, 0.0))
        assertEquals(8, Battle.survivors(10, 10.0, 6.0)) // sqrt(1 - 0.36) = 0.8
        assertEquals(1, Battle.survivors(10, 10.0, 10.0)) // a draw-like win still leaves one
    }

    @Test
    fun theWinnerAlwaysKeepsAtLeastOneAndTheLoserNone() {
        val random = Random(7)
        repeat(1_000) {
            val a = random.nextInt(1, 30)
            val d = random.nextInt(1, 30)
            val r = Battle.resolve(a, d, 1.2, random)
            if (r.attackerWon) {
                assertTrue(r.attackerSurvivors in 1..a)
                assertEquals(0, r.defenderSurvivors)
            } else {
                assertTrue(r.defenderSurvivors in 1..d)
                assertEquals(0, r.attackerSurvivors)
            }
        }
    }
}
