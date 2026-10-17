package nl.lamia.kingdoms.core.ai

import nl.lamia.kingdoms.core.map.MapConfig
import nl.lamia.kingdoms.core.map.MapGenerator
import nl.lamia.kingdoms.core.map.MapSize
import nl.lamia.kingdoms.core.rules.Difficulty
import nl.lamia.kingdoms.core.rules.NewGame
import nl.lamia.kingdoms.core.rules.RuleOptions
import nl.lamia.kingdoms.core.rules.TurnOrder
import kotlin.test.Test

/**
 * The Design §9 numbers from AI-vs-AI simulation. Opt-in, about a minute:
 * `TUNING=1 ./gradlew :core:jvmTest --tests '*TuningReport*' -i | grep REPORT`
 * Optional: TUNING_GAMES (default 300 per line), TUNING_ORDER (Fixed, Rotating, Shuffled),
 * TUNING_QUICK=1 (only the mixed Medium 4-kingdom and Large 6-kingdom lines, for tuning loops),
 * TUNING_VARIANTS=default,none,pact40-33,… (rule variants to compare, see [variant]).
 */
class TuningReport {
    @Test
    fun report() {
        if (System.getenv("TUNING") == null) return
        val games = System.getenv("TUNING_GAMES")?.toLong() ?: 300L
        val order = System.getenv("TUNING_ORDER")?.let(TurnOrder::valueOf) ?: TurnOrder.Shuffled
        val quick = System.getenv("TUNING_QUICK") == "1"
        val variants = System.getenv("TUNING_VARIANTS")?.split(",") ?: listOf("default")
        // Difficulty, both ways (Medium, 4 kingdoms, rotating seat; fair is 25%): one kingdom at each
        // level against three Normal, and one Normal — standing in for the player — against three at
        // each level, which says how hard that level is to beat.
        for (v in variants) for (d in Difficulty.entries) for (alone in listOf(true, false)) {
            if (quick) break
            if (d == Difficulty.Normal && !alone) continue // the same line as Normal vs 3 Normal
            val (options, _) = variant(v)
            var wins = 0
            for (seed in 1L..games) {
                val seat = (seed % 4).toInt()
                val map = MapGenerator.generate(MapConfig.of(MapSize.Medium, seed))
                var s = NewGame.create(map, 4, seed, humans = emptySet(), turnOrder = order, options = options)
                s = s.copy(kingdoms = s.kingdoms.map { if ((it.id == seat) == alone) it.copy(difficulty = d) else it })
                while (!s.isOver && s.round <= 200) s = step(s, HeuristicAi()).state
                if (s.winner == seat) wins++
            }
            val line = if (alone) "$d vs 3 Normal" else "Normal vs 3 $d"
            println("REPORT $order $v difficulty $line (Medium): wins ${100 * wins / games}%")
        }
        val ais = listOf("balanced" to HeuristicAi({ Personality.Balanced }), "mixed" to HeuristicAi())
        for (v in variants) for ((aiLabel, ai) in ais) for (size in MapSize.entries) for (kingdoms in listOf(2, 4, 6)) {
            val label = aiLabel
            val (options, pactBonus) = variant(v)
            val ai = if (pactBonus == 3.0) ai else
                HeuristicAi(if (aiLabel == "mixed") Personality::forSeat else { _ -> Personality.Balanced }, pactBonus)
            if (!MapConfig.of(size, 1).fits(kingdoms)) continue
            if (quick && (label != "mixed" || (size to kingdoms) !in listOf(MapSize.Medium to 4, MapSize.Large to 6))) continue
            val results = (1L..games).map { seed ->
                Simulation.run(MapGenerator.generate(MapConfig.of(size, seed)), kingdoms, seed, ai, order, options = options)
            }
            val rounds = results.filter { !it.stalled }.map { it.rounds }.sorted()
            val seatWins = (0 until kingdoms).map { k -> 100 * results.count { it.winner == k } / results.size }
            val richest = 100 * results.count { r -> r.winner == r.startingGrowth.indexOf(r.startingGrowth.max()) } / results.size
            val spread = results.map { it.startingGrowth.max() - it.startingGrowth.min() }.average()
            // Runaway (Design §9): how often the army leader after a few rounds goes on to win, and how
            // often the kingdom that starts farthest from the others wins (fair: 1 / kingdoms).
            fun leaderWins(round: Int) = results.filter { it.leaders.size >= round }
                .let { r -> if (r.isEmpty()) 0 else 100 * r.count { it.winner == it.leaders[round - 1] } / r.size }
            val far = results.filter { it.farthest != null }
            val farWins = if (far.isEmpty()) 0 else 100 * far.count { it.winner == it.farthest } / far.size
            val remote = results.filter { it.remotest != null }
            val remoteWins = if (remote.isEmpty()) 0 else 100 * remote.count { it.winner == it.remotest } / remote.size
            val quiet = results.filter { it.quietest != null }
            val quietWins = if (quiet.isEmpty()) 0 else 100 * quiet.count { it.winner == it.quietest } / quiet.size
            println(
                "REPORT $order $v $label $size k=$kingdoms: rounds median=${rounds[rounds.size / 2]} " +
                    "p10=${rounds[rounds.size / 10]} p90=${rounds[rounds.size * 9 / 10]} " +
                    "stalled=${results.count { it.stalled }} rejected=${results.sumOf { it.rejections }} " +
                    "seatWins%=$seatWins richestStartWins%=$richest startSpread=${(spread * 100).toInt() / 100.0} " +
                    "leaderR5Wins%=${leaderWins(5)} leaderR8Wins%=${leaderWins(8)} farthestWins%=$farWins (${far.size}) remotestWins%=$remoteWins (${remote.size}) quietestWins%=$quietWins (${quiet.size})",
            )
        }
    }

    /**
     * A named rule variant: `default`, `none` (no pacts), `pact<form>-<end>` (thresholds in percent),
     * `bonus<n>` (AI pact bonus); joined with `+`, e.g. `pact40-33+bonus5`.
     */
    private fun variant(name: String): Pair<RuleOptions, Double> {
        var options = RuleOptions()
        var bonus = 3.0
        for (part in name.split("+")) {
            val n = part.filter { it.isDigit() || it == '-' }
            options = when {
                part == "default" -> options
                part == "none" -> options.copy(pacts = false)
                part.startsWith("pact") -> n.split("-").let { (f, e) -> options.copy(pactForms = f.toInt() / 100.0, pactEnds = e.toInt() / 100.0) }
                part.startsWith("bonus") -> options.also { bonus = n.toDouble() }
                else -> error("unknown variant $part")
            }
        }
        return options to bonus
    }
}
