# Status

The session entry point: where things stand, what to do next, and the handover from the last
session. Keep it short — it earns its value by being short.

## 1. Where things stand

- **Phase:** M4 look done (first pass). Next is M5 (UI). Branch `master`.
- **Modules:** `core`, `composeApp`, `androidApp`, `iosApp` (Design §2.1). `core.map` generates
  the map (Design §3); `core.rules` is setup, moves, fatigue, battle, growth, elimination
  (Design §4); `core.ai` has `HeuristicAi` with three personalities, `Simulation`, `PassiveAi`. The app plays a game: owner
  bands, army badges with yield, capital rings, movable dots, dashed targets with win chances,
  long-press amount picker, turn bar with per-kingdom totals, event log, victory/defeat panel,
  debug buttons (map seed/size, kingdom count 2–6, turn order, restart). AI turns play step by
  step with an arrow for the latest move and a Skip button. Amount picker by long-press or
  right-click on the target. 55 core tests; composeApp has 4 snapshot tests, 3 UI play tests and
  2 picker tests (Compose ui-test, virtual clock, ~30 s).
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

- **Unexplained (watch for it):** once, right after starting the app and switching Turns to
  Shuffled, the user could not move. Not reproducible since, by the user or by tests (App-level
  play tests click Shuffled/Restart then play 4 turns and check each move lands in the game on
  screen). Compose 1.12 restarts `pointerInput` when its lambda changes, so stale tap handlers are
  ruled out. If it recurs: note the turn bar text, whether selection/targets showed, any log line,
  and the terminal output.
- None. Design §11 is empty.

## 3. To do next

In order; each milestone ends with something runnable on desktop.

- [x] **M0 Scaffold.** KMP + Compose Multiplatform, four modules, runs on desktop, Android
      emulator and iOS simulator.
- [x] **M1 Map.** Generator, property tests, flat render with pan/zoom/select and debug
      regenerate. Rivers are per-border, not connected
      chains — fine for rules, revisit in M4 for looks.
- [x] **M2 Rules.** Playable against passive kingdoms on desktop.
- [x] **M3 AI.** Heuristic AI, simulation, fair setup, paced AI turns, Shuffled order.
- [x] **M4 Look.** Board space with tilt, earth slab, cel props, animated water, banners and
      towers, layered canvases. First pass — expect tweaks from play.
- [ ] **M5 UI.** The HUD and screens: compact HUD that keeps clear of the map (kingdom list and
      log collapsible), camera fit that leaves room for it, phone portrait and landscape layouts
      (on a phone the map is tiny and screen-sized banners crowd it — scale them with zoom
      below some size), the debug bar replaced by a new-game screen (map size, kingdoms, seed,
      turn order), title and result screens.
- [ ] **M6 Platforms and polish.** Touch tuning on Android/iOS devices, autosave and resume
      (the `onChange` hook is there), real-device performance (the headless emulator renders in
      software, so its frame times say nothing), app icons, the iOS ICU deployment warning.

## 4. Handover

Latest first. Keep the last three sessions; condense older ones to a line under "Earlier".

### 2026-10-15 — Session 1f: M4 look

- `Board` (board space, tilt, wall), `BoardGeometry` (paths, walls, rivers, decals and props
  placed once per map), `Props.kt` (cel-shaded tree, hill, mountain, reeds, tuft, pool, field),
  `MapView` rebuilt as three layered canvases; `Palette` reworked with `darker`/`lighter`.
- Iterated on snapshots: second pass thickened outlines, added a selection glow, made fields
  rounded farmland, thinned mountain provinces, gave capitals a tower with a coloured roof.
- Found by counting draw calls, not by looking: sibling canvases share a layer, so the animated
  water re-ran the land's draw every frame. Fixed with a `graphicsLayer` per canvas (150 → 1 land
  draws per 150 frames). The headless emulator's frame times (~1 s, software GPU) are not a
  measure of anything; real-device numbers belong to M6.
- Runs on desktop, Android emulator and iOS simulator. On a phone in portrait the map is tiny
  and banners crowd it — the user's next ask is the UI, now M5.
- **Picking this up:** M5 UI. Ask the user what the HUD and screens should feel like before
  building (layout on phone vs desktop, what must always be visible).

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
- Bug hunt after the user could not move once (see §1 "Unexplained"): added `PlayTest` — plays
  rounds through `GameScreen`/`App` with real clicks on a virtual clock — and a human-seat vs
  `HeuristicAi` stress test in `FullGameTest`; `GameScreen`/`App` gained `onChange` hooks. Two
  hypotheses (stale tap handlers; AI exception mid-turn) tested and ruled out.
- User chose Shuffled turn order. The long-press picker "didn't work on desktop": a test showed
  mouse long-press does open it — it must be on the target after selecting a source, and the
  picker appears in a corner. Added right-click, a pending-move arrow and a how-to hint.
- User asked for alternation once two kingdoms remain (no double turns head to head). Done,
  with a test. Side effect measured and written up as Design §11 #1; own-turn growth as a fix
  was measured and rejected (78–83% for the first mover).
- User chose the first-mover bonus; swept 0–12, then 1 and 2 over 400 games: 2 kept
  (Design §4.1 has the numbers; Small maps stay slightly short).
- **Picking this up:** M4.

### Earlier

- **Session 1d (M2):** `core.rules` (GameState, Rules.apply, Battle with exact win chance,
  NewGame); app rebuilt around `GameScreen`; headless tap-through snapshot tests.
- **Session 1b (M0):** hand-built KMP scaffold (no wizard); Compose 1.12 needs compileSdk 37;
  Xcode 26.3 needs the iOS 26.2 platform even for simulator builds; hand-written pbxproj worked.
- **Session 1 (setup):** working method, first design; user decided home-grown armies, fatigue,
  no sea movement, no terrain defence, Forest, no time limit, `nl.lamia.kingdoms`, `MapConfig`.
