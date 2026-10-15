# Kingdoms — Design

The single design document. **Normative**: what is written here is what the game does. Edit it in
the same change as the code when behaviour changes. Present tense, no history — reversals are
struck through, not deleted. Open questions live in §11 until decided.

## 1. Premise

A short turn-based strategy game in the spirit of Risk. A small map of provinces,
a handful of kingdoms, one of them the player's. Move armies, take provinces, collect resources,
grow armies at home and march them to the front. The last kingdom standing wins.

Tone: **cozy**. A tabletop diorama, not a war game. Simple mechanics, readable at a glance, no
menus deeper than one level.

Design constraints that every rule is measured against:

- A typical game for a human against 3 AI kingdoms on the default map lasts 15–30 minutes. This
  is a tuning guideline, not a rule: there is no time or round limit.
- A rule the player cannot predict from what is on screen is a bad rule.
- One input gesture per action: tap source, tap target.

## 2. Platforms and technology

| | |
| --- | --- |
| Language | Kotlin Multiplatform |
| UI | Compose Multiplatform |
| Targets | Android, iOS, Desktop JVM (macOS, Linux) |
| Rendering | Compose `Canvas`, 2.5D projection (§8) — no 3D engine |
| Serialization | kotlinx.serialization (save/resume) |
| Package root | `nl.lamia.kingdoms` (also the Android application id and iOS bundle id) |

### 2.1 Modules

- **`core`** (`nl.lamia.kingdoms.core`, sub-packages `map`, `rules`, `ai`) — pure Kotlin, `commonMain` only, no UI or platform dependencies. Map generation,
  game state, rules, battle resolution, AI. Everything here is deterministic given a seed and is
  unit-tested in `commonTest`.
- **`composeApp`** (`nl.lamia.kingdoms.app`) — rendering, input, HUD, screens; desktop `main` in `jvmMain`.
- **`androidApp`** (`nl.lamia.kingdoms`) — the Android application module: one `MainActivity`
  that hosts `App()`. AGP 9 does not allow the Android application plugin in a KMP module, so
  `core` and `composeApp` are KMP Android *libraries* and this module is the app.
- **`iosApp`** — the Xcode project: a SwiftUI shell hosting `MainViewController()` from the static
  `ComposeApp` framework, which an Xcode build phase builds through Gradle.

Minimum platforms: Android 8.0 (API 26), iOS 16, desktop JVM 21.

`core` never depends on `composeApp`. The UI reads `GameState` and submits `Action`s; it never
mutates state.

### 2.2 Determinism

- All randomness flows from one seeded `Random` owned by the game. Map seed + action log
  reproduces a game exactly — this is what makes bugs reproducible and AI-vs-AI simulation useful.
- No platform-specific geometry or math libraries in `core`: Voronoi/Delaunay is implemented in
  common code.

### 2.3 State model

`GameState` (package `core.rules`) is an immutable value: the map, the kingdoms, one `Holding`
(owner, armies, fresh armies) per province, round, current kingdom, winner, and a `draws`
counter. `Rules.apply(state, action)` is the only way state changes; it returns
`Outcome.Applied(state, events)` or `Outcome.Rejected(reason)`. Human input and AI produce the
same `Action`s (`Move(from, to, armies?)`, `EndTurn`). Events (moves, battles, eliminations,
round ends, turn starts, victory) come back with the new state so the UI can describe and
animate them.

Each battle draws from `Random(mix(seed, draws))` and increments `draws`, so a state plus an
action always yields the same next state, and the state stays a plain serializable value.

## 3. The map

### 3.1 Generation pipeline

1. **Points.** Best-candidate (Mitchell) sampling inside the map rectangle — blue noise with an
   exact point count — then 2 rounds of Lloyd relaxation so cells are similar in size.
2. **Voronoi.** Each cell is the rectangle clipped by the perpendicular bisector to every other
   site, nearest first, stopping once no bisector can reach it. Corners computed by neighbouring
   cells are snapped into one shared vertex table (snap distance 1% of cell spacing, which also
   collapses near-zero edges); every interior edge must then appear in both cells.
3. **Land and sea.** Cells touching the map edge are always sea. Land grows from the
   best-scoring interior cell, each step adding the best-scoring interior cell adjacent to the
   land so far, until it has exactly `landProvinces` cells. Score is low-frequency value noise
   minus a radial falloff, giving one irregular island. Land is connected by construction.
4. **Irregular borders.** Every edge touching land becomes a noisy polyline, generated **once
   per edge** from a seed mixed from the map seed and the two cell ids, and used by both sides —
   so neighbours always share the exact same border. The path subdivides recursively inside a
   convex quad whose controls stay within the fan triangles (edge + site) of the two convex cells,
   so borders never cross each other or self-intersect, and each site stays inside its province.
5. **Terrain** assigned per province (§3.3), **features** per border (§3.4).

Starting positions are a rule, not geometry (§4.1).

Map size and province count come from `MapConfig` (§3.5).

### 3.2 Adjacency

Two land provinces are adjacent when they share a border. Armies only cross land borders into an
adjacent province; there is no sea movement.

### 3.3 Province terrain

Terrain sets the province's look, resource and yield. All resources count the same; the type is
flavour and readability. Yield is how many armies the province grows per round (§4.5). Terrain
gives no defence bonus — only border features do (§3.4).

| Terrain | Resource | Yield | Notes |
| --- | --- | --- | --- |
| Plains | Grain | 2 | Most common |
| Hills | Wool | 1 | |
| Forest | Timber | 1 | |
| Mountains | Ore | 1 | |
| Marsh | Peat | 1 | |

**Coastal** is an attribute, not a terrain: a province bordering the sea (any sea, including an
enclosed inland sea) keeps its own terrain, also produces Fish, and gains **+1 yield**. Coastal
plains grow 3 per round, other coastal provinces 2, inland non-plains 1 — harbour provinces are
the rich ones, as most large cities are on the coast.

### 3.4 Border features

A geographic feature straddles the border between two neighbouring provinces and multiplies the
**defender's** strength when that border is attacked across — in either direction.

| Feature | Defence multiplier |
| --- | --- |
| None | ×1.00 |
| River | ×1.15 |
| Hills / ridge | ×1.20 |
| Marsh | ×1.25 |
| Lake (partly blocks the border) | ×1.30 |
| Mountain range | ×1.50 |

Roughly a third of land borders carry a feature (configurable, §3.5). Features are drawn on the border itself, so the
player sees the bonus where it applies.

### 3.5 Map configuration

Map size is a game setting, not a constant — used by tests to generate small and large maps, and
offered to the player on the new-game screen.

`MapConfig` holds:

| Field | Meaning |
| --- | --- |
| `seed` | Map seed |
| `landProvinces` | Target number of land provinces |
| `seaFraction` | Share of the interior cells (not touching the map edge) that stay sea (default 0.35) |
| `featureFraction` | Share of land borders that carry a feature (default ≈0.33) |

The map rectangle is derived, not configured: the generator picks enough cells for the land, the
interior sea and an edge ring of sea, at a constant 10 000 units² per cell and a 3:2 aspect, so
province size stays constant and the map grows with the count.

Terrain is assigned by quota over two noise fields. Elevation is noise plus distance from the
sea, so the top 12% become mountains and the next 18% hills. Of the rest, the wettest 12% become
marsh and the next 22% forest. The remainder is plains.

Player-facing presets (for 4 kingdoms): **Small** 18, **Medium** 28 (default), **Large** 40 land
provinces, plus a custom count. The generator rejects a config with fewer than 3 land provinces per
kingdom. Generation hits `landProvinces` exactly: the land mask threshold is adjusted until the
count matches.

## 4. Rules

### 4.1 Setup

- 2–6 kingdoms; default 4 (player + 3 AI). Kingdom 0 is the human player.
- Each kingdom starts with a **capital** and two neighbouring provinces. The setup tries 80
  candidate layouts — farthest-point capitals over graph distance from a random first pick, one
  refinement pass, then starting neighbours claimed in passes where the poorest kingdom picks
  first and takes the richest province next to its capital — and keeps the one with the smallest
  spread in starting growth among those at most one step less spread out than the best.
- Territories are dealt to seats at random, so turn order says nothing about map position.
- Each owned province starts with 2 armies, the capital with 5. A kingdom whose starting growth
  is below the best gets **3 rounds of its shortfall** as extra capital armies.
- Remaining provinces are **neutral** with 1–3 armies. Neutral provinces do not grow.

### 4.2 Round and turn

A **round** is every living kingdom taking one turn. In round 1 the order is by kingdom id, so
the player moves first. From round 2 the order follows the game's `TurnOrder`, **Shuffled** by
default:

| TurnOrder | Order each round |
| --- | --- |
| Fixed | By kingdom id, every round |
| Rotating | The starting kingdom moves one seat each round |
| Shuffled | A fresh random order each round, from the game seed. With two kingdoms left they alternate: the one that did not just move goes first, so neither gets a double turn |

At the end of the round, provinces grow (§4.5).

During its turn a kingdom performs any number of **moves**, then ends the turn.

### 4.3 Moves

The player taps a source province they own, then an adjacent target.

- **Amount.** By default all *surplus* armies move — everything except 1, which always stays
  behind. Long-press (touch or mouse) or right-click on the target opens an amount picker
  instead; an arrow shows the pending move, and the selected province's panel says how.
- **Fatigue.** Each army moves at most once per turn. A province tracks how many of its armies
  are fresh; armies that arrive (by transfer or conquest) are fatigued until the kingdom's next
  turn. This prevents one stack crossing the whole map in a turn.
- **Friendly target** — armies transfer.
- **Neutral or enemy target** — battle (§4.4).

A province with only 1 army, or no fresh surplus, cannot be a source.

### 4.4 Battle

Inputs: attacking armies `A`, defending armies `D`, border multiplier `m` (§3.4).

```
a = A × uniform(0.9, 1.1)
d = D × m × uniform(0.9, 1.1)
```

The larger of `a` and `d` wins (ties go to the defender). Survivors follow Lanchester's square law,
which rewards concentration and makes close fights costly:

```
winnerSurvivors = max(1, round(W × sqrt(1 − (l / w)²)))
```

where `W` is the winner's army count and `w`, `l` the winner's and loser's strengths.

- **Attacker wins:** the province changes owner; all surviving attackers cross into it and are
  fatigued.
- **Defender wins:** all committed attackers are lost; the defender keeps its survivors.

Before confirming an attack the UI shows the estimated win chance (computed from the same formula
in `core`, not a separate approximation).

### 4.5 Growth

Armies are home-grown. At the end of each round every owned province gains its yield
(§3.3: terrain yield, +1 if coastal) in armies, in place. There is no reinforcement pool and no placement step.

- Every terrain yields at least 1, so a living kingdom always gains at least one army per round.
- There is no per-province cap.

Consequences, intended: rich provinces are well defended by default, the hinterland is never
empty, and getting armies to the front is logistics — moving them province by province, one step
per turn (§4.3). A kingdom stretched into a long column cannot abandon its tail.

### 4.6 Elimination and victory

A kingdom with no provinces is eliminated. The last kingdom with provinces wins. If the player is
eliminated the game ends in defeat.

## 5. AI

Heuristic, cheap, deterministic (same state, same action), and runs in `core.ai`. Each call
returns one action; the AI is asked again until it ends its turn.

1. **Attack.** Over every own province with movable armies and every non-own neighbour whose win
   chance with all movable armies is at least the personality's *boldness*, pick the highest
   `chance × value / (1 + 0.1 × defenders)`. Value is the target's yield, plus 2 for a capital,
   plus 3 if its owner has two provinces or fewer, plus the personality's *neutral bias* for a
   neutral. **Commit** the larger of: the fewest armies reaching the personality's *confidence*,
   and everything except a guard as large as the biggest other enemy stack next to the source.
2. **Logistics.** Move fresh armies from an interior province one step towards the frontier
   province with the best prospect — the best `value / (defenders × border defence + 1)` of its
   targets — divided by `1 + distance`. Supply goes where an attack can succeed, not merely to the
   nearest front: nearest-front supply deadlocked equal stacks across mountain borders.
3. **End turn.**

| Personality | Boldness | Confidence | Neutral bias | Plays seats |
| --- | --- | --- | --- | --- |
| Balanced | 0.70 | 0.95 | 1.0 | 1, 4 |
| Aggressive | 0.55 | 0.85 | 0.0 | 2, 5 |
| Cautious | 0.85 | 0.99 | 2.0 | 3 |

The UI plays AI turns one action at a time (≈450 ms per move, 200 ms per end of turn), draws the
latest move as an arrow in the mover's colour, and offers **Skip** to resolve the remaining AI
turns at once.

## 6. Screens and interaction

- **Title** — new game (kingdom count, map size preset or custom province count, map seed
  optional), resume.
- **Game** — the map with pan/zoom, a small HUD: current kingdom, round, armies growing next round,
  End Turn button. Each province shows its yield (+1 to +3) next to its army count.
- **Result** — victory/defeat, play again.

Tap targets: a selected source highlights valid targets; tapping elsewhere deselects. Hit-testing
inverts the 2.5D projection (§8) and does point-in-polygon against the province outline.

## 7. Save and resume

The game autosaves at the end of every turn: seed + settings + action log, or a serialized
`GameState` snapshot (both are deterministic; the snapshot is simpler to load).

## 8. Visual style

Cozy, cartoony, cel-shaded — a painted tabletop diorama seen from above at a slight angle.

- **Projection.** Orthographic top-down with a tilt: ground-plane `y` is scaled by ≈0.8 and
  elevation is drawn as a vertical offset upward. Objects are painted back-to-front by `y`.
- **Land as a board.** The landmass has a visible side face (darker earth band) along the coast,
  so the map reads as a thick tile resting on water.
- **Cel shading.** Flat fills with 2–3 hard-edged tone bands, no gradients. Outlines are thick and
  a darker shade of the fill colour, never pure black.
- **Kingdom colours.** Warm pastel palette; ownership shown as a tinted border band inside the
  province outline plus banner colour, so terrain colour stays readable.
- **Props.** Vector-drawn, chunky and rounded: tree clusters, round hills, mountains with snow
  caps, reeds and pools for marsh, lakes with a light rim, gentle animated waves along coasts.
  Border features (§3.4) are props placed along the border polyline.
- **Armies.** A small banner/figure in kingdom colour with a count badge, standing on the
  province's site, which the generator guarantees lies inside the outline.
- **Performance.** Static layers (terrain, borders, props) are rendered once to an `ImageBitmap`
  and redrawn only when ownership changes; armies, selection and water animate on top.

## 9. Tuning targets

Measured by AI-vs-AI simulation in `core` tests, not by feel alone:

Guidelines, not rules — the game itself has no round or time limit.

- Median game length for 4 kingdoms on the Medium map: 12–20 rounds.
- No single starting position wins more than 40% of simulated 4-kingdom games.
- Simulated games end. The simulation harness has a round cap; hitting it flags a stalemate as a
  defect to tune away. The cap exists only in tests.

## 10. Testing

- `core`: unit tests for battle math, rules, growth; property tests for map generation across
  many seeds and every size preset — exact land province count, every land province reachable, every border shared by exactly two provinces with identical
  polylines, no self-intersecting outlines.
- Simulation tests for §9.
- UI: manual run on desktop each session; Android and iOS checked at each milestone.

## 11. Open questions

1. **Second-mover edge in two-kingdom games.** Alternation makes a game that *starts* with two
   kingdoms a fixed order, and the kingdom moving second always acts just before growth: it wins
   57–65% (Small 65%, Medium/Large 57%; 200 games each). Seat 0 — the human — moves first.
   Endgames of 4- and 6-kingdom games are unaffected (seats 22–28%). Growing on each kingdom's
   own turn while two are left was measured and is much worse (first mover 78–83%).
   *Options:* accept it; give the first mover in a two-kingdom game extra starting armies
   (tunable, like compensation); or keep shuffling while both kingdoms are still at their
   starting size. *Proposed:* extra starting armies for the first mover, tuned by simulation.

Decided 2026-10-15, turn order: **Shuffled** by default. Simulation (300 games per line, all AI)
showed Fixed order letting the first seat — the human — win only 8–14% of 4-kingdom games and the
last seat 37–48%; Rotating still up to 35% for seat 0; Shuffled 21–29% for every seat. Fixed and
Rotating stay available.

Decided 2026-10-15: home-grown armies (§4.5); fatigue, one move per army per turn (§4.3); no sea
movement (§3.2); no terrain defence (§3.3); Forest stays; no turn or time limit (§1, §9);
configurable map size (§3.5). Coastal is an attribute giving +1 yield, not a terrain (§3.3).
