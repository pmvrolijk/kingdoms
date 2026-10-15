# Status

The session entry point: where things stand, what to do next, and the handover from the last
session. Keep it short — it earns its value by being short.

## 1. Where things stand

- **Phase:** M3 AI done, pending one decision (§2). Next is M4 (look). Branch `master`.
- **Modules:** `core`, `composeApp`, `androidApp`, `iosApp` (Design §2.1). `core.map` generates
  the map (Design §3); `core.rules` is setup, moves, fatigue, battle, growth, elimination
  (Design §4); `core.ai` has `HeuristicAi` with three personalities, `Simulation`, `PassiveAi`. The app plays a game: owner
  bands, army badges with yield, capital rings, movable dots, dashed targets with win chances,
  long-press amount picker, turn bar with per-kingdom totals, event log, victory/defeat panel,
  debug buttons (map seed/size, kingdom count 2–6, turn order, restart). AI turns play step by
  step with an arrow for the latest move and a Skip button. 54 core tests + 3 snapshot tests.
- **M1 verified:** 14 property tests over 34 maps (10 seeds × 3 presets, plus 6, 120, no-sea and
  70%-sea/all-features) pass on JVM, Android host and iOS simulator: exact province count,
  connected land, symmetric neighbours, shared border paths identical in both outlines, cells
  tile the rectangle to 1e-6, no self-intersecting outline, no crossing borders, site inside its
  province, determinism. A deliberate 5× wobble made 3 of them fail, so they have teeth.
  Generation ≈20 ms for Medium on desktop. The map was seen rendered via the snapshot PNG;
  pan/zoom/tap have not been tried by hand on any platform.
- **Verified:** core tests pass on JVM, Android host and iOS simulator; desktop app launches
  (process up, no exceptions; window not screenshotted); APK installs and renders on the
  Pixel_8 emulator (API 37); `iosApp` builds with `xcodebuild` and renders on the iPhone 16
  simulator (iOS 18.2). Not yet run on a physical device on either platform.
- **Known warning:** the iOS link warns that Compose's bundled ICU data was built for iOS 18.5
  while the deployment target is 16.0. Harmless on the 18.2 simulator; check on an older iOS
  before release, or raise the target.
- **M2 verified:** rules tests cover every move rule, battle maths (exact win chance agrees with
  20 000 sampled battles within 1.5%), setup on 30 maps, and 15 full games (greedy test player
  vs passive kingdoms, all sizes) with invariants checked after every action — all won, in 11–18
  rounds. A headless test taps the capital, then a neutral neighbour, and the snapshots show
  targets, 100% labels, and the conquest in the log. Not tried by hand: long-press picker, End
  turn button, a game to the finish in the real window.
- **M3 measured** (tuning report, Shuffled order, 100–300 games per line): 0 stalemates and 0
  rejected AI actions in every configuration. Medium, 4 kingdoms: median 15 rounds, p90 20–21.
  Small 10–11, Large 18–19. Seats fair (4 kingdoms 21–29% each, 6 kingdoms 9–21%). The richest
  start wins 22–36% of 4-kingdom games (fair is 25%) but 56–61% of 2-kingdom games (fair is 50%):
  the remaining two-player edge comes from more than starting growth. Under Fixed order the
  human's seat wins only 8–14% — hence §2.
- **Toolchain:** Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.4.1, Gradle 9.8.1, JDK 21,
  Xcode 26.3, Android SDK at `~/Library/Android/sdk` (AGP installed platform 37 and build-tools
  36.0.0 itself; `ANDROID_HOME` is not set — `local.properties` points at the SDK).

## 2. Open decisions (need the user)

- **Design §11 #1 — turn order.** Proposed: Shuffled (fair), at the cost of the player not
  always moving first. Default is Fixed until decided; the debug bar has all three.

## 3. To do next

In order; each milestone ends with something runnable on desktop.

- [x] **M0 Scaffold.** KMP + Compose Multiplatform, four modules, runs on desktop, Android
      emulator and iOS simulator.
- [x] **M1 Map.** Generator, property tests, flat render with pan/zoom/select and debug
      regenerate. Rivers are per-border, not connected
      chains — fine for rules, revisit in M4 for looks.
- [x] **M2 Rules.** Playable against passive kingdoms on desktop.
- [x] **M3 AI.** Heuristic AI, simulation, fair setup, paced AI turns. Turn-order default
      awaits Design §11 #1. Not played by hand against the AI yet.
- [ ] **M4 Look.** The 2.5D cel-shaded style (Design §8): board edge, props, banners, water,
      cached static layer.
- [ ] **M5 Platforms and polish.** Touch input on Android/iOS, title/result screens, autosave
      and resume.

## 4. Handover

Latest first. Keep the last three sessions; condense older ones to a line under "Earlier".

### 2026-10-15 — Session 1e: M3 AI

- Tap the selected province again to deselect (user request after playing M2 to a win in 19
  rounds).
- `HeuristicAi` + `Personality` (Design §5), `Simulation` (AI-vs-AI with stall flag, rejection
  count), opt-in `TuningReport` in `core/src/jvmTest`. `GameState` gained `turnOrder` and
  `order`; `NewGame` gained balanced layouts, random seats and compensation.
- How the tuning went, so it is not repeated: (1) first run showed the last seat winning 4× the
  first; (2) shuffling seats over territories didn't help, so it was turn order, not geography;
  (3) growth on each kingdom's own turn flipped the bias instead; (4) Shuffled order fixed it;
  (5) balanced layouts cut starting spread 3.1 → ~1.3; (6) compensation ×3 brought the richest
  start to ≈ fair at 4 kingdoms (×2 and ×4 measured, ×3 kept); (7) a 2-kingdom stalemate (two
  300-stacks across a ×1.5 mountain border) was fixed by prospect-based logistics and guarded
  commits, not by rule changes.
- **Picking this up:** get the user's call on Design §11 #1 (one default in `App.kt` and
  `NewGame.create`), play a game against the AI by hand, then M4.

### 2026-10-15 — Session 1d: M2 rules

- `core.rules`: `GameState`/`Holding`/`Kingdom`, `Action`/`Event`/`Outcome`/`Rejection`,
  `Rules.apply`, `Battle` (resolve + exact `winChance` via piecewise-linear integral), `NewGame`.
  `core.ai`: `Ai`, `PassiveAi`, `playUntilHumanTurn` (ends an AI's turn if it submits a rejected
  action, so a buggy AI cannot hang the game).
- App rebuilt around `GameScreen`; `MapView` draws ownership and badges in screen space so they
  stay readable at any zoom. `SnapshotTest` now also taps through a move headlessly — the way to
  check UI interaction from the terminal.
- Choices made without the user: exactly two starting neighbours (design said 1–2); round-robin
  claiming; kingdom 0 is human; game seed = map seed + restarts; names Aldmere, Brightvale,
  Corrindor, Dunmoor, Eastwatch, Fernholt; kingdom colours blue/red/gold/purple/teal/orange.
- **Picking this up:** play a game by hand on desktop (picker, End turn, finish), then M3: promote
  the greedy policy into a real AI, simulate AI-vs-AI for game length and fairness.

### 2026-10-15 — Session 1c: M1 map

- Built `core.map`: `MapGenerator`, `Voronoi` (half-plane clipping), `NoisyEdges`, `ValueNoise`,
  `GameMap`/`Province`/`Border`/`Shore`. Swapped the planned Poisson-disc + Bowyer–Watson + noise
  threshold for best-candidate sampling, bisector clipping and grow-from-centre land: exact
  counts, connected by construction, far less code. Design §3.1 updated to match.
- Border wobble is bounded geometrically (quad inside the two cells' fan triangles), so "borders
  never cross" is a property of the construction, and the tests confirm it.
- Added `SnapshotTest` (composeApp jvmTest): renders `App()` headless to
  `composeApp/build/snapshots/app.png`. Use it for visual checks; the terminal cannot screenshot
  the desktop window.
- Coast as a terrain crowded out Plains (0–5%, avg yield ≈1.0). User decided: coastal is a
  province attribute, +1 yield and Fish. Measured after (50 seeds/size): Plains 35–39%, yields
  1/2/3 split roughly in thirds, average ≈2.0 per province per round. That is double the rate
  the first design assumed — the thing to watch in M3 game-length tuning.
- **Picking this up:** try pan/zoom/tap by hand on desktop, then start M2 with `GameState`,
  `Action` and `apply`.

### Earlier

- **Session 1b (M0):** hand-built KMP scaffold (no wizard); Compose 1.12 needs compileSdk 37;
  Xcode 26.3 needs the iOS 26.2 platform even for simulator builds; hand-written pbxproj worked.
- **Session 1 (setup):** working method, first design; user decided home-grown armies, fatigue,
  no sea movement, no terrain defence, Forest, no time limit, `nl.lamia.kingdoms`, `MapConfig`.
