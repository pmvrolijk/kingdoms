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

`GameState` is an immutable value. `apply(state, action) -> Result<GameState, Rejection>` is the
only way state changes. Human input and AI produce the same `Action` types. Events (battle
results, conquests, eliminations) are returned alongside the new state so the UI can animate them.

## 3. The map

### 3.1 Generation pipeline

1. **Points.** Poisson-disc sampling inside the map rectangle, then 1–2 rounds of Lloyd
   relaxation so provinces are similar in size.
2. **Voronoi.** Delaunay triangulation (Bowyer–Watson) → Voronoi cells, clipped to the rectangle.
3. **Land and sea.** A land mask from low-frequency noise with a radial falloff gives an island or
   small continent. Cells under the mask threshold are sea. The land graph must be connected; if
   it is not, the generator retries with the next seed.
4. **Irregular borders.** Each shared Voronoi edge is replaced by a noisy polyline (recursive
   midpoint displacement). The polyline is generated **once per edge**, seeded from the two
   province ids, and used by both provinces — so neighbours always share the exact same border.
   Displacement is bounded to the quad formed by the edge endpoints and the two province sites, so
   borders never cross each other or self-intersect.
5. **Terrain** assigned per province (§3.3), **features** per border (§3.4).
6. **Starting positions** (§4.1).

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
| Coast | Fish | 1 | Any land province bordering sea |

Coast is a terrain (not a modifier) only when the province would otherwise be plains; otherwise
the province keeps its terrain and is drawn with a shoreline.

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
| `width`, `height` | Map rectangle; derived from `landProvinces` when not given, so province size stays constant and the map grows |
| `seaFraction` | Share of cells that become sea (default ≈0.35) |
| `featureFraction` | Share of land borders that carry a feature (default ≈0.33) |

Player-facing presets (for 4 kingdoms): **Small** 18, **Medium** 28 (default), **Large** 40 land
provinces, plus a custom count. The generator rejects a config with fewer than 3 land provinces per
kingdom. Generation hits `landProvinces` exactly: the land mask threshold is adjusted until the
count matches.

## 4. Rules

### 4.1 Setup

- 2–6 kingdoms; default 4 (player + 3 AI).
- Each kingdom starts with one **capital** province and its nearest 1–2 neighbours, spread so
  capitals are far apart (farthest-point placement over graph distance). Remaining provinces are
  **neutral** with 1–3 armies. Neutral provinces do not grow.
- Each owned province starts with 2 armies; the capital with 5.

### 4.2 Round and turn

A **round** is every living kingdom taking one turn, the player first, then AI in fixed order.
At the end of the round, provinces grow (§4.5).

During its turn a kingdom performs any number of **moves**, then ends the turn.

### 4.3 Moves

The player taps a source province they own, then an adjacent target.

- **Amount.** By default all *surplus* armies move — everything except 1, which always stays
  behind. Long-press / secondary click on the target opens an amount picker instead.
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

Armies are home-grown. At the end of each round every owned province gains its terrain yield
(§3.3) in armies, in place. There is no reinforcement pool and no placement step.

- Every terrain yields at least 1, so a living kingdom always gains at least one army per round.
- There is no per-province cap.

Consequences, intended: rich provinces are well defended by default, the hinterland is never
empty, and getting armies to the front is logistics — moving them province by province, one step
per turn (§4.3). A kingdom stretched into a long column cannot abandon its tail.

### 4.6 Elimination and victory

A kingdom with no provinces is eliminated. The last kingdom with provinces wins. If the player is
eliminated the game ends in defeat.

## 5. AI

Heuristic, cheap, deterministic, and runs in `core`.

Each AI turn:

1. **Attack** every adjacent target where the estimated win chance exceeds the kingdom's
   *boldness* threshold, best odds first, preferring targets that are weakly held, high-yield, or
   the capital of a weakened rival.
2. **Logistics**: move fresh surplus from interior provinces one step towards the nearest
   frontier (BFS distance over own provinces), keeping a garrison that grows with the province's
   yield.
3. End turn.

Personalities are parameter sets (boldness, preference for neutrals vs kingdoms, how much is kept
on the frontier). The UI plays AI moves back with short animations; the AI itself is instant.

## 6. Screens and interaction

- **Title** — new game (kingdom count, map size preset or custom province count, map seed
  optional), resume.
- **Game** — the map with pan/zoom, a small HUD: current kingdom, round, armies growing next round,
  End Turn button. Each province shows its yield (+1/+2) next to its army count.
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
  province's visual centre (pole of inaccessibility, not the Voronoi site).
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

None open.

Decided 2026-10-15: home-grown armies (§4.5); fatigue, one move per army per turn (§4.3); no sea
movement (§3.2); no terrain defence (§3.3); Forest stays; no turn or time limit (§1, §9);
configurable map size (§3.5).
