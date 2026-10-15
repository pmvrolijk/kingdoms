package nl.lamia.kingdoms.core.ai

import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.rules.Difficulty
import nl.lamia.kingdoms.core.rules.NewGame
import nl.lamia.kingdoms.core.rules.TurnOrder
import kotlin.test.Test

/**
 * The Design §9 numbers from AI-vs-AI simulation. Opt-in, about a minute:
 * `TUNING=1 ./gradlew :core:jvmTest --tests '*TuningReport*' -i | grep REPORT`
 * Optional: TUNING_GAMES (default 300 per line), TUNING_ORDER (Fixed, Rotating, Shuffled).
 */
class TuningReport {
    @Test
    fun report() {
        if (System.getenv("TUNING") == null) return
        val games = System.getenv("TUNING_GAMES")?.toLong() ?: 300L
        val order = System.getenv("TUNING_ORDER")?.let(TurnOrder::valueOf) ?: TurnOrder.Shuffled
        // Difficulty: one kingdom at each level (rotating seat) against three Normal; fair is 25%.
        for (d in Difficulty.entries) {
            var wins = 0
            for (seed in 1L..games) {
                val seat = (seed % 4).toInt()
                val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed))
                var s = NewGame.create(map, 4, seed, humans = emptySet(), turnOrder = order)
                s = s.copy(kingdoms = s.kingdoms.map { if (it.id == seat) it.copy(difficulty = d) else it })
                while (!s.isOver && s.round <= 200) s = step(s, HeuristicAi()).state
                if (s.winner == seat) wins++
            }
            println("REPORT $order difficulty $d vs 3 Normal (Medium): wins ${100 * wins / games}%")
        }
        val ais = listOf("balanced" to HeuristicAi { Personality.Balanced }, "mixed" to HeuristicAi())
        for ((label, ai) in ais) for (size in MapSize.entries) for (kingdoms in listOf(2, 4, 6)) {
            if (!MapConfig.of(size, 1).fits(kingdoms)) continue
            val results = (1L..games).map { seed ->
                Simulation.run(MapGenerator.generate(MapConfig.of(size, seed)), kingdoms, seed, ai, order)
            }
            val rounds = results.filter { !it.stalled }.map { it.rounds }.sorted()
            val seatWins = (0 until kingdoms).map { k -> 100 * results.count { it.winner == k } / results.size }
            val richest = 100 * results.count { r -> r.winner == r.startingGrowth.indexOf(r.startingGrowth.max()) } / results.size
            val spread = results.map { it.startingGrowth.max() - it.startingGrowth.min() }.average()
            println(
                "REPORT $order $label $size k=$kingdoms: rounds median=${rounds[rounds.size / 2]} " +
                    "p10=${rounds[rounds.size / 10]} p90=${rounds[rounds.size * 9 / 10]} " +
                    "stalled=${results.count { it.stalled }} rejected=${results.sumOf { it.rejections }} " +
                    "seatWins%=$seatWins richestStartWins%=$richest startSpread=${(spread * 100).toInt() / 100.0}",
            )
        }
    }
}
