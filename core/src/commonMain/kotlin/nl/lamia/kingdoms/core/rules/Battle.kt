package nl.lamia.kingdoms.core.rules

import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

data class BattleResult(val attackerWon: Boolean, val attackerSurvivors: Int, val defenderSurvivors: Int)

/** Battle resolution (Design §4.4). [winChance] is exact for the same model [resolve] samples. */
object Battle {
    /** Each side's strength is scaled by uniform(1 − NOISE, 1 + NOISE). */
    const val NOISE = 0.1

    fun resolve(attackers: Int, defenders: Int, defence: Double, random: Random): BattleResult {
        require(attackers > 0 && defenders > 0) { "both sides need armies: $attackers vs $defenders" }
        val a = attackers * random.nextDouble(1 - NOISE, 1 + NOISE)
        val d = defenders * defence * random.nextDouble(1 - NOISE, 1 + NOISE)
        return if (a > d) {
            BattleResult(attackerWon = true, attackerSurvivors = survivors(attackers, a, d), defenderSurvivors = 0)
        } else {
            BattleResult(attackerWon = false, attackerSurvivors = 0, defenderSurvivors = survivors(defenders, d, a))
        }
    }

    /** Lanchester's square law, at least one survivor. */
    fun survivors(count: Int, winnerStrength: Double, loserStrength: Double): Int {
        val ratio = loserStrength / winnerStrength
        return (count * sqrt(1 - ratio * ratio)).roundToInt().coerceIn(1, count)
    }

    /**
     * P(A·u₁ > D·m·u₂) for independent u₁, u₂ ~ uniform(lo, hi). For a fixed u₁ the inner
     * probability is linear in u₁ between two breakpoints, so the trapezoid rule over those
     * breakpoints is exact.
     */
    fun winChance(attackers: Int, defenders: Int, defence: Double): Double {
        if (attackers <= 0) return 0.0
        val r = attackers / (defenders * defence)
        val lo = 1 - NOISE
        val hi = 1 + NOISE
        fun inner(u: Double) = ((r * u - lo) / (hi - lo)).coerceIn(0.0, 1.0)
        val points = listOf(lo, hi, lo / r, hi / r).filter { it in lo..hi }.sorted()
        var sum = 0.0
        for (k in 0 until points.size - 1) sum += (inner(points[k]) + inner(points[k + 1])) / 2 * (points[k + 1] - points[k])
        return sum / (hi - lo)
    }
}
