# Status

The session entry point: where things stand, what to do next, and the handover from the last
session. Keep it short — it earns its value by being short.

## 1. Where things stand

- **Phase:** M6 (platforms and polish) nearly done: autosave/resume and touch tuning (1i); help
  screen, sound effects and app icons (1j) — all checked by the user on a Huawei P30, the icon
  also in the macOS Dock. Left: performance measurement, the iOS ICU warning, desktop packaging.
  Branch `master`; session 1j is uncommitted at close.
- **Modules:** `core`, `composeApp`, `androidApp`, `iosApp` (Design §2.1). `core.map` generates
  the map (Design §3); `core.rules` is setup, moves, fatigue, battle, growth, elimination
  (Design §4); `core.ai` has `HeuristicAi` with three personalities and three difficulties,
  `Simulation`, `PassiveAi`. The app opens on the new-game screen (map, seed, turn order, seats
  with name, colour, You/Easy/Normal/Hard, live preview), then plays: HUD (strength bars, turn
  panel, chronicle, menu), province card, slider picker, banners and resource icons that scale at
  half the zoom, province names when zoomed in, clash animation, result panel. The game in play
  is saved after every action and offered as "Continue · round N" on launch (Design §7). How to
  play: seven tabs with looping scenes on real grid maps (§6.3). Sounds generated in code with
  volume and mute (§6.4). App icon drawn by the game for every platform (§8.1). 66 core tests
  (64 common, plus JVM-only `ManySeedsTest` over 9000 maps and `TuningReport`); composeApp has
  32: 9 snapshot, 4 play, 4 help, 5 sound, 4 icon, 2 picker, 2 touch, 2 save-store (~3 min).
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
  simulator (iOS 18.2). **Physical device:** the user played several games on a Huawei P30
  (Android, 2340×1080): readable, pan/pinch fine, kill-and-Continue works, 70-province and small
  maps; in 1j also the help screens, the sounds and the launcher icon. No physical iOS device
  yet; the iOS save path and iOS sound have only been compiled (the app builds with `xcodebuild`
  and carries its icon), not run.
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
  kotlinx-serialization 1.11.0, kotlinx-io 0.9.1. **IntelliJ 2026.1 (and reportedly 2026.2)
  cannot import AGP 9.4.1** (supports up to 9.0 / 9.1); the user chose to keep AGP and build from
  the CLI until JetBrains catches up. Android Studio would work.

## 2. Open decisions (need the user)

- **Unexplained (watch for it):** once, right after starting the app and switching Turns to
  Shuffled, the user could not move. Not reproducible since, by the user or by tests (App-level
  play tests click Shuffled/Restart then play 4 turns and check each move lands in the game on
  screen). Compose 1.12 restarts `pointerInput` when its lambda changes, so stale tap handlers are
  ruled out. If it recurs: note the turn bar text, whether selection/targets showed, any log line,
  and the terminal output.
- **Desktop packaging and the JDK:** `createDistributable`/`packageDmg` refuse the Homebrew JDK
  (Compose checks the vendor). Either another JDK (e.g. Corretto) or
  `compose.desktop.packaging.checkJdkVendor=false` in `gradle.properties` — the user's call. 1j
  checked the bundle once with `-P…checkJdkVendor=false` on the command line; nothing changed.
- Design §11 is empty.

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
- [ ] **M6 Platforms and polish.**
  - [x] Autosave and resume (1i).
  - [x] Touch tuning on Android — checked on the P30 by the user. iOS touch not tried on a device.
  - [x] Help screen (1j), checked on the P30.
  - [x] Sound effects (1j), checked on the P30.
  - [x] App icons (1j): Android, iOS, macOS, Linux, window — seen on the P30 and macOS Dock.
  - [ ] Real-device performance (the headless emulator renders in software; the P30 felt fine but
        nothing was measured), the iOS ICU deployment warning, desktop packaging (§2).
  - [ ] Optional: Android 13 monochrome (themed) icon layer; a defeat sound (not asked for).

## 4. Handover

Latest first. Keep the last three sessions; condense older ones to a line under "Earlier".

### 2026-10-16/17 — Session 1j: help screen, sound effects, app icons

- **Help** (user: tabs, a scene each for Goal, Moving, Attack, Strength, Borders, Growth, plus
  Tactics as text): scenes are real maps — `core.map.GridMap` builds small grids with the
  generator's edges, tested with the other maps — drawn by `MapView` with gestures off, with
  taps, marching shields and growth overlaid; numbers come from `Battle`. Found on the way: a
  river under the selection outline was hidden (fixed for the real game too); the AI kept
  playing behind the menu (now waits while menu or help is open).
- **Sound** (user: generated in code with variation; volume and mute; "be nice"): `Synth`
  renders Click, Clash, March, Victory; `Speaker` per platform (`JvmSpeaker`, `AndroidSpeaker`
  with SoundPool, `IosSpeaker` with the Ambient session). Iterated by ear with the user: the
  clash rang like a bell, then was rebuilt from a sword recording the user gave; the march went
  from a white-noise slide to two gravelly grey-noise steps at ~0.42 s, ~5 dB under the rest, and
  a march never overlaps another. Recordings were only measured (one is CC BY): nothing ships.
  I cannot hear; levels were set by measurement, the judging was the user's.
- **Icons** (user: the player's forest capital, roughly hexagonal): `AppIcon.kt` with the map's
  pieces; `AppIconTest` checks the Android safe circle and iOS opacity, and with `ICONS=1`
  writes all platform files (CLAUDE.md §Commands). Seen on the P30 launcher and the macOS Dock.
- Choices without the user: no army count on the icon; help scenes silent; AI sounds at 60%;
  sound settings in `settings.json`; volume default 6/10 with amplitude (v/10)².
- No tuning re-run needed (no rule, setup or AI change).
- **Picking this up:** ask the user what follows M6. Open M6 items: measure performance on the
  P30, the iOS ICU warning (raise the target or check an older iOS), the desktop packaging JDK
  (§2), and a first run on a physical iOS device if one is available.

### 2026-10-16 — Session 1i: autosave/resume, touch tuning, P30 fixes

- User decided: on launch with a save, a **Continue** button (not straight into the game); the
  save is a **GameState snapshot** (not an action log). Design §6.2, §7.
- `core`: `@Serializable` on the map and state types, `Save.json`; `SaveTest` round-trips every
  preset and plays on identically. Save 150 / 225 / 301 KB (Small/Medium/Large), encode < 1 ms,
  decode 2–3.5 ms on JVM (not measured on a phone). App: `Saves.kt` (`SaveStore`,
  `FileSaveStore` writes a temp file then moves it atomically), saves after every action, a
  finished game clears it; Continue restores setup and restart count. Checked on the emulator
  (kill, relaunch, same board) and by the user on the P30.
- Hard chip clipped on phones: the panel width was set outside `safeDrawingPadding`, so the
  cutout squeezed it. Fixed; chips no longer wrap.
- Touch: zoom limits are relative to the fitted view (0.8×–6×; the old absolute limits allowed
  only ~3× on a phone), panning keeps the view centre over the board, a finger that just misses
  the coast takes the majority province within 12 dp, a two-finger touch is never a tap. Bigger
  chips/buttons; the province card drops its how-to line under 480 dp; Android hides the system
  bars. `TouchTest`; P30 snapshots (`new-game-p30.png`, `app-p30.png`).
- User on the P30: province names "dark brown, no halo, not readable". Android's text paint kept
  the halo's stroke style for the fill pass; `drawStyle = Fill` fixed it (CLAUDE.md rule).
  Desktop snapshots never showed it. User confirmed the fix on the P30.
- Not checked: iOS save at runtime; the desktop app by hand; an Android configuration change
  (no `configChanges`, so the Activity is recreated and returns to the new-game screen with
  Continue — no loss, but a step). No tuning re-run needed (no rule/setup/AI change).
- **Picking this up:** the help screen — ask how long it may be and whether it is one page or
  tabs (rules / movement and attack / defence lines / tactics), then sound effects (§2 questions
  first). Optional, not asked for: a leader-focus for Normal AI.

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

### Earlier

- **Session 1g (M5 HUD):** strength bars, turn panel, chronicle, province card, slider picker,
  generated province names, clash animation; camera in `CameraState` (taps were lost through
  `rememberUpdatedState`).
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
