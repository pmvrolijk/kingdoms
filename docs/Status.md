# Status

The session entry point: where things stand, what to do next, and the handover from the last
session. Keep it short — it earns its value by being short.

## 1. Where things stand

- **Phase:** design only. No code yet. Branch `master`, no commits.
- **Docs:** `docs/Design.md` (normative design, §11 open questions), this file, `CLAUDE.md`.
- **Toolchain on this machine:** JDK 21 (Homebrew, JBR, Temurin), Xcode 26.3, Android SDK at
  `~/Library/Android/sdk` (`ANDROID_HOME` is not set in the shell).

## 2. Open decisions (need the user)

Design §11 — proposed defaults exist for each; none confirmed yet. #1 (reinforcement placement)
blocks the rules milestone, the rest can wait.

## 3. To do next

In order; each milestone ends with something runnable on desktop.

- [ ] **M0 Scaffold.** KMP + Compose Multiplatform project (Android, iOS, desktop JVM) via the
      JetBrains wizard; add the `core` module; Gradle wrapper; `.gitignore`. Desktop window opens.
      Android and iOS build once to prove the targets.
- [ ] **M1 Map.** Poisson-disc → Delaunay/Voronoi → land mask → irregular shared borders →
      terrain and features (Design §3). Property tests. Flat-colour render on desktop with
      pan/zoom and province hit-testing.
- [ ] **M2 Rules.** `GameState`, `Action`, `apply`; moves, fatigue, battle, reinforcements,
      elimination (Design §4). Player can play a full game against passive kingdoms.
- [ ] **M3 AI.** Heuristic AI (Design §5); AI-vs-AI simulation tests and first tuning pass
      against Design §9.
- [ ] **M4 Look.** The 2.5D cel-shaded style (Design §8): board edge, props, banners, water,
      cached static layer.
- [ ] **M5 Platforms and polish.** Touch input on Android/iOS, title/result screens, autosave
      and resume.

## 4. Handover

Latest first. Keep the last three sessions; condense older ones to a line under "Earlier".

### 2026-10-15 — Session 1: project setup

- Set up the working method: `CLAUDE.md`, `docs/Design.md`, `docs/Status.md`, and the
  `session-open` / `session-close` skills in `.claude/skills/`.
- Wrote the first full design from the brief: Voronoi map with shared irregular borders, terrain →
  resource yield, border features as defender multipliers, fatigue rule for moves,
  Lanchester-square battle with ±10% noise, reinforcements `max(1, yield/2)`, heuristic AI,
  Compose Canvas 2.5D cel-shaded rendering.
- Choices made without the user that deserve a look: Forest terrain added; fatigue rule added
  (needed so a stack cannot cross the map in one turn); the specific multipliers and yields in
  Design §3 are first guesses for tuning in M3.
- **Picking this up:** confirm or change Design §11, then start M0.
