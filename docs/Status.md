# Status

The session entry point: where things stand, what to do next, and the handover from the last
session. Keep it short — it earns its value by being short.

## 1. Where things stand

- **Phase:** M5 UI done; the user has looked and three screen fixes followed (1h). Next is M6
  (platforms and polish). Branch `master`; everything through session 1h is in commit 558cf59.
- **Modules:** `core`, `composeApp`, `androidApp`, `iosApp` (Design §2.1). `core.map` generates
  the map (Design §3); `core.rules` is setup, moves, fatigue, battle, growth, elimination
  (Design §4); `core.ai` has `HeuristicAi` with three personalities and three difficulties,
  `Simulation`, `PassiveAi`. The app opens on the new-game screen (map, seed, turn order, seats
  with name, colour, You/Easy/Normal/Hard, live preview), then plays: HUD (strength bars, turn
  panel, chronicle, menu), province card, slider picker, banners and resource icons that scale at
  half the zoom, province names when zoomed in, clash animation, result panel. 62 core tests (60
  common, plus JVM-only `ManySeedsTest` over 9000 maps and `TuningReport`); composeApp has 9
  snapshot tests, 3 UI play tests and 2 picker tests (~2 min).
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
- [x] **M5 UI.** Landscape lock, HUD (strength bars, turn panel, chronicle, province card
      with icons and army split bar, slider picker anchored at the target), province names,
      resource icons on the map, clash animation, banner scaling, new-game screen with seats,
      colours and AI difficulty, menu, result panel (Design §6).
- [ ] **M6 Platforms and polish.** Touch tuning on Android/iOS devices, autosave and resume
      (the `onChange` hook is there), real-device performance (the headless emulator renders in
      software, so its frame times say nothing), app icons, the iOS ICU deployment warning.

## 4. Handover

Latest first. Keep the last three sessions; condense older ones to a line under "Earlier".

### 2026-10-15 — Session 1h: new-game screen, difficulty, a generator bug

- New-game screen (`NewGameScreen.kt`, `Setup`) with seats: name, colour (8), You / AI
  Easy-Normal-Hard; live map preview; random seed on launch. Menu (Resume, Restart, New game…)
  and result panel (Play again, New game…). `NewGame.Seat`, `Kingdom.colour/difficulty`,
  `Difficulty` in core; the app colours kingdoms through `GameState.colourOf`.
- Difficulty in `HeuristicAi` measured: Easy 14%, Normal 23%, Hard 34% (one vs three Normal,
  400 games); Normal unchanged. Now in the tuning report.
- "Red usually wins": not the personality (rotations even) and, over 1000 games, not the seat
  either (32/32/31%). The real effect is geography — the AI farthest from a player who fights
  their neighbours wins ~48%. The user's games all started on seed 1 (fixed launch seed), where
  red sits far away; the launch seed is now random. A leader-focused Normal AI would damp the
  far-kingdom effect generally — not done; Hard has it.
- Round 1 now puts people first, then the AIs in random order (harmless; it did not explain red).
- Found while measuring: map generation crashed on 2 of 3000 Medium seeds (greedy vertex snap);
  union-find made it transitive but mis-tiled ~2% of maps; a rounding-level snap fixed both
  (0 failures in 9000 maps, no crossings in 1500). `ManySeedsTest` (core jvmTest, ~10 s) guards it.
- User: "the menu is behind the map" on the new-game screen — dragging the preview drew the board
  over the settings. `MapView` now clips to its bounds; `previewMapStaysBehindTheSettings` guards it.
- User: banners, resources and castles stayed the same size when zooming — "scale with the map,
  maybe at 50%"; and names when zoomed in. `markerScale`: the fitted view as before, then half the
  map's zoom (clamped 0.4–2.5); names fade in from 120 to 160 dp province width, with a parchment
  halo. Snapshots `zoom-*.png`.
- User: "the last arrow of the enemy moves stays visible when it is the players turn". It was
  cleared only when the player moved; now an AI's end of turn clears it.
  `noEnemyArrowOnThePlayersTurn` compares the screen with a fresh render of the same state.
- Not checked: pinch-zoom and the scaled markers/names on a real device; the leftover arrow was
  reproduced only headless (a test), not in the user's window after the fix.
- **Picking this up:** M6 — start with autosave/resume (the `onChange` hook is there), then
  device touch tuning. Optional, not asked for: a leader-focus for Normal AI against the
  far-kingdom effect.

### 2026-10-15 — Session 1g: M5 HUD

- User's direction: phones landscape; strength bars for production, territory, armies; a slider
  on right-click/long-press; the province card as an infographic with resource and army icons;
  the can-move / stays / fatigued split as a stacked bar. All built (Design §6.1).
- New: `Icons.kt` (terrain icons reuse the map props), `Hud.kt` (`StackedBar`, `StrengthPanel`,
  `TurnPanel`, `LogPanel`, `ActionButton`, `anchoredNear`), `ProvinceCard.kt` (`ArmySplit`),
  `MovePicker.kt` (`AmountSlider` with progress semantics, so tests set it). Camera moved into a
  `CameraState` that gestures read and write directly — reading it through
  `rememberUpdatedState` lost taps in the first frame and would drop pan deltas when several
  pointer events arrive in one frame.
- Phone landscape snapshot (2556 × 1179 at 3×) looks right; not yet seen on a device.
- Tweaks after the user's look: resource icons under the army plates instead of "+n";
  generated province names (`core.map.ProvinceNames`, Design §3.1 step 6) used on the card, in
  the picker and in a narrative chronicle; a bloodless clash animation (`Clash.kt`, Design §8)
  in its own canvas, checked frame by frame in snapshots (`clash-early/clash/clash-late.png`).
- **Picking this up:** new-game screen (done in 1h).

### Earlier

- **Session 1f (M4):** board space, cel props, layered canvases (a `graphicsLayer` each: land
  draws 150 → 1 per 150 frames).
- **Session 1e (M3):** `HeuristicAi`, `Simulation`, `TuningReport`; fairness work — Shuffled
  order, balanced layouts, compensation ×3, head-to-head bonus 2; a stall fixed via logistics.
- **Session 1d (M2):** `core.rules` (GameState, Rules.apply, Battle with exact win chance,
  NewGame); app rebuilt around `GameScreen`; headless tap-through snapshot tests.
- **Session 1b (M0):** hand-built KMP scaffold (no wizard); Compose 1.12 needs compileSdk 37;
  Xcode 26.3 needs the iOS 26.2 platform even for simulator builds; hand-written pbxproj worked.
- **Session 1 (setup):** working method, first design; user decided home-grown armies, fatigue,
  no sea movement, no terrain defence, Forest, no time limit, `nl.lamia.kingdoms`, `MapConfig`.
