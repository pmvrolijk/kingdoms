# Status

The session entry point: where things stand, what to do next, and the handover from the last
session. Keep it short — it earns its value by being short.

## 1. Where things stand

- **Phase:** M0 scaffold done, except running the app on iOS (below). Branch `master`.
- **Modules:** `core`, `composeApp`, `androidApp`, `iosApp` (Design §2.1). `core` holds only
  `MapConfig`/`MapSize`; the app shows a placeholder screen reading them. 4 tests.
- **Verified:** core tests pass on JVM, Android host and iOS simulator; desktop app launches
  (process up, no exceptions; window not screenshotted); APK installs and renders on the
  Pixel_8 emulator (API 37); iOS frameworks link for device and simulator.
- **Not verified:** the Xcode app build. Xcode 26.3 refuses every iOS destination until the
  iOS 26.2 platform is installed (Xcode → Settings → Components, or
  `xcodebuild -downloadPlatform iOS`). The hand-written `project.pbxproj` is therefore untested
  beyond `xcodebuild -list`.
- **Toolchain:** Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.4.1, Gradle 9.8.1, JDK 21,
  Xcode 26.3, Android SDK at `~/Library/Android/sdk` (AGP installed platform 37 and build-tools
  36.0.0 itself; `ANDROID_HOME` is not set — `local.properties` points at the SDK).

## 2. Open decisions (need the user)

None. Design §11 is empty — all first-round questions decided 2026-10-15.

## 3. To do next

In order; each milestone ends with something runnable on desktop.

- [ ] **M0 Scaffold.** Done except one check: build and launch `iosApp` in the simulator once
      the iOS 26.2 platform is installed; fix `project.pbxproj` if it fails.
- [ ] **M1 Map.** Poisson-disc → Delaunay/Voronoi → land mask → irregular shared borders →
      terrain and features (Design §3), all driven by `MapConfig` (§3.5). Property tests across
      seeds and size presets. Flat-colour render on desktop with pan/zoom, province hit-testing,
      and a debug way to regenerate with another seed/size.
- [ ] **M2 Rules.** `GameState`, `Action`, `apply`; moves, fatigue, battle, home-grown armies,
      elimination (Design §4). Player can play a full game against passive kingdoms.
- [ ] **M3 AI.** Heuristic AI (Design §5); AI-vs-AI simulation tests and first tuning pass
      against Design §9.
- [ ] **M4 Look.** The 2.5D cel-shaded style (Design §8): board edge, props, banners, water,
      cached static layer.
- [ ] **M5 Platforms and polish.** Touch input on Android/iOS, title/result screens, autosave
      and resume.

## 4. Handover

Latest first. Keep the last three sessions; condense older ones to a line under "Earlier".

### 2026-10-15 — Session 1b: M0 scaffold

- Hand-built the scaffold instead of using the web wizard: version catalog with current stables,
  `core` / `composeApp` as KMP Android libraries plus a separate `androidApp` (AGP 9 rule),
  hand-written Xcode project (objectVersion 77, folder-synced sources, Gradle build phase).
- Surprises: Compose 1.12 needs `compileSdk` 37; Xcode 26.3 needs the iOS 26.2 platform
  component even for simulator builds (only 18.x simulator runtimes are installed).
- No Material 3 dependency yet — the UI will be custom drawn; add it if the HUD wants it.
- `.idea/go.imports.xml` is staged in git (by the IDE, not by this session); `.idea/` is now in
  `.gitignore`, so it probably wants unstaging.
- **Picking this up:** install the iOS platform and run `iosApp` in the simulator to close M0;
  then M1 (map generation), starting with Poisson-disc + Delaunay/Voronoi in `core` with
  property tests.

### 2026-10-15 — Session 1: project setup

- Set up the working method: `CLAUDE.md`, `docs/Design.md`, `docs/Status.md`, and the
  `session-open` / `session-close` skills in `.claude/skills/`.
- Wrote the first full design from the brief: Voronoi map with shared irregular borders, terrain →
  resource yield, border features as defender multipliers, fatigue rule for moves,
  Lanchester-square battle with ±10% noise, reinforcements `max(1, yield/2)`, heuristic AI,
  Compose Canvas 2.5D cel-shaded rendering.
- User review decided all of Design §11: armies are **home-grown** (each province grows its yield
  in place each round, no placement step — logistics matter, no "snake" play); fatigue, one move
  per army per turn; no sea movement; no terrain defence; Forest stays; no turn or time limit
  (15–30 min is a guideline). Added: package `nl.lamia.kingdoms`, configurable map size via
  `MapConfig` (Design §3.5) with Small/Medium/Large presets plus custom.
- Still first guesses, for tuning in M3: yields, border multipliers, battle noise, preset sizes.
  Home-grown growth roughly doubles the army rate of the earlier `yield / 2` pool — watch game
  length in simulation.
- **Picking this up:** start M0 (scaffold).
