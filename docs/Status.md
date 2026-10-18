# Status

The session entry point: where things stand, what to do next, and the handover from the last
session. Keep it short — it earns its value by being short.

## 1. Where things stand

- **Phase:** M6 (platforms and polish) nearly done: autosave/resume and touch tuning (1i); help
  screen, sound effects and app icons (1j) — all checked by the user on a Huawei P30, the icon
  also in the macOS Dock. 1k added a **web target** (Kotlin/Wasm) and **GitHub Actions** for
  tests and releases (Design §2.4), and **pacts** against a runaway leader (§4.7), played by the
  user on desktop. Left: performance measurement, the iOS ICU warning. Branch `master` on GitHub
  (`origin`), **public** since 1m. **First release: v1.0.2** (1l), the dmg and APK installed
  and played by the user. 1m: CI badge and screenshots in the README, the web build deployed to
  GitHub Pages on each release tag (Design §2.4), and **hot-seat** — several people at one screen
  (§4.1, §4.2), played by the user on desktop with only people.
- **Modules:** `core`, `composeApp`, `androidApp`, `iosApp` (Design §2.1); `core` and `composeApp`
  also target `wasmJs` (browser app in `wasmJsMain`: `WebSaveStore`, `WebSpeaker`, `index.html`). `core.map` generates
  the map (Design §3); `core.rules` is setup, moves, fatigue, battle, growth, elimination
  (Design §4); `core.ai` has `HeuristicAi` with three personalities and three difficulties,
  `Simulation`, `PassiveAi`. The app opens on the new-game screen (map, seed, turn order, seats
  with name, colour, You/Easy/Normal/Hard, live preview), then plays: HUD (strength bars, turn
  panel, chronicle, menu), province card, slider picker, banners and resource icons that scale at
  half the zoom, province names when zoomed in, clash animation, result panel. The game in play
  is saved after every action and offered as "Continue · round N" on launch (Design §7). How to
  play: seven tabs with looping scenes on real grid maps (§6.3). Sounds generated in code with
  volume and mute (§6.4). App icon drawn by the game for every platform (§8.1). Pacts against a
  runaway leader and a hinterland-first setup (§4.1, §4.7). Any seat can be a person (hot-seat).
  82 core tests (80 common — on JVM and Wasm/Node in 1m; Android host and iOS simulator not re-run
  since 1l — plus JVM-only `ManySeedsTest` over 9000 maps and `TuningReport`); composeApp has 42:
  10 snapshot, 4 play, 4 help, 5 sound, 5 icon, 3 about, 3 hot-seat, 2 screenshot, 2 picker,
  2 touch, 2 save-store (~3 min).
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
  and carries its icon), not run. **Web:** the production build served locally, in Chrome on
  macOS (by me, through the browser extension): renders, a move and an AI turn play, the save
  lands in `localStorage` and reload offers Continue on the same board, a Clash buffer starts
  with the `AudioContext` running (not heard), 120 fps. The built site in
  `composeApp/build/dist` predates pacts until rebuilt. Not tried: Safari, Firefox, a phone
  browser, the help screen on the web.
- **CI:** `.github/workflows/ci.yml` and `release.yml`, both green on GitHub. Tag `v1.0.2`
  published a release with all four files; the user installed the signed APK (P30) and the
  .dmg (macOS) and both work. **Not tried:** the `.deb` on a Linux machine, the web zip served
  from the release. The v1.0.1 tag/release failed to publish (fixed in v1.0.2); tags `v0.9.0`
  and `v1.0.0` also exist on GitHub, from before signing. **Pages:** the `pages` job in
  `release.yml` has not run yet; the user set up Pages and the `github-pages` environment and will
  check the deploy with the next release. Until then the README's "Play in your browser" link 404s.
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
  Xcode 26.3, the Wasm build fetches its own Node and Yarn through Gradle, Android SDK at `~/Library/Android/sdk` (AGP installed platform 37 and build-tools
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
- **Hot-seat hand-over:** there is no "Pass to X" pause between people; the turn panel's name is the
  cue (the board holds no secrets). Add one only if play shows it is needed. Optionally tidy the
  failed v1.0.1 release and the early `v0.9.0`/`v1.0.0` tags.
- **Pact strength:** in play the user won a game the pact stood against (round 15) and lost one
  where the pact failed against red; fine so far. If people keep beating pacts easily, the
  levers are the AI's +3 on the target and the 35% trigger (both measurable in the report).
  Game length (Medium median 20) the user found good in play.
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
        nothing was measured), the iOS ICU deployment warning.
  - [x] Web target (1k): Kotlin/Wasm, localStorage saves, Web Audio, favicon. Seen in Chrome.
  - [x] Distributions (1k, 1l): CI on push, release on a `v*` tag (.dmg, .deb, APK, web zip).
        First release v1.0.2; dmg and signed APK checked by the user. .deb and web zip untried.
  - [x] Licence (AGPL-3.0-or-later), README, About panel (1l).
  - [x] Host the web build (1m): GitHub Pages on each release tag. First deploy pending (§1).
  - [ ] Optional: Safari/Firefox/phone-browser check, an Intel macOS build.
  - [x] Hot-seat (1m): several people at one screen; played by the user on desktop, people only.
  - [ ] Optional: Android 13 monochrome (themed) icon layer; a defeat sound (not asked for).
  - [x] Balance against runaway leaders (1k): pacts on Normal and Hard (35%/28%), hinterland-first
        setup; a pact ends below two members, a breaker sits out 3 rounds. Growth caps and upkeep
        measured and dropped. Played by the user in four desktop games: every pact case seen.
  - [ ] Watch: the remotest kingdom still wins ~2× its share in AI-only games (Design §4.7, §9);
        the pact should bite harder against a person who turtles. Seen in play (1k): a pact
        formed against the user, who still won in round 15; keep an eye on whether it is too soft.

## 4. Handover

Latest first. Keep the last three sessions; condense older ones to a line under "Earlier".

### 2026-10-18 — Session 1m: public repo, Pages, screenshots, hot-seat

- The user made the repo public. **README:** CI badge, a "Play in your browser" link, three
  screenshots. **Pages** (user: on each release tag): the web job also uploads the site as a Pages
  artifact, a `pages` job deploys it (`deploy-pages@v5`; also on a manual run). I served the build
  under `/kingdoms/` locally and loaded it in Chrome: it renders, every request 200. The user set
  up Pages (source "GitHub Actions") and the environment; not yet deployed.
- **Screenshots** from `ScreenshotTest` (CLAUDE.md command): seed 51, played by the AI to round 6
  so the chronicle fills; the attack target skips pact allies (seed 1's only targets were allies,
  so the chronicle said "breaks the pact" — 60 seeds searched). The clash frame needs a frame at
  +150 ms before it, or the animation starts late. Re-renders differ by a few bytes: the water runs
  on real time.
- **Hot-seat** (user's idea and turn rule): any seat can be Human; round 1 draws which person goes
  first, the others follow by seat, then the AIs. One person draws nothing — a test pins seed 5's
  setup, matched against the old code — so old seeds, saves and the tuning numbers are unchanged
  (no tuning re-run). App: Human/Easy/Normal/Hard chips (the only person's AI chips greyed out),
  "Aldmere, your turn", strength bars follow the person to move, "Victory for X!".
  `HotSeatTest` (3). The user played a people-only game on desktop to a win and liked the
  coloured turn dots.
- Choices without the user: no hand-over screen (§2); the settings panel 356 → 396 dp, since the
  extra chip cut the names ("Bright…"), checked in the P30 snapshot; the other people follow the
  drawn one in seat order (not shuffled).
- Not checked: hot-seat on Android/iOS/web (compiled for web and Android, not run; iOS not
  compiled), the core tests on the Android host and iOS simulator.
- **Picking this up:** the user tags a release; check that the `pages` job deployed and the game
  plays at https://pmvrolijk.github.io/kingdoms/ (also Safari/Firefox/phone if at hand). Then the
  open M6 items: performance on the P30, the iOS ICU warning.

### 2026-10-18 — Session 1l: first CI run, licence, first release

- The user pushed to GitHub (`pmvrolijk/kingdoms`, private). First CI run: 5 app UI tests
  failed with `UncompletedCoroutinesError` — the four `PlayTest`s and
  `HelpTest.theAiWaitsWhileHelpIsOpen`, the five slowest (22–27 s locally). `runComposeUiTest`
  (v2) defaults to a 60 s real-time limit; the Linux runner draws in software and is slower.
  Fix: `runUiTest` (`UiTests.kt`) with 5 minutes, used by all 8 UI tests; CLAUDE.md rule.
  CI green since. `gh` is not installed here, so I read no CI logs (the user pasted them).
- **Licence** (user: AGPL since the web version can run as a service; chose 3.0-or-later and
  "Marcel Vrolijk"): `LICENSE` is the FSF text, downloaded from gnu.org with the user's OK;
  `README.md` (what, downloads per platform, build, layout, licence). About panel (`About.kt`,
  Design §6.5) from the end of the new-game settings and the in-game menu, with Licence and
  Source code links; `AboutTest` (3) with a fake `UriHandler`, snapshot `about.png`. The macOS
  app carries the copyright line; `licenseFile` was tried and dropped — it made the .dmg show an
  "Agree" dialog. Compiles for web, Android and iOS; the links were only tested with the fake
  handler, not opened on a device.
- **Android signing:** the user made a self-signed keystore (kept in the git-ignored `local/`)
  and added the four secrets to the GitHub environment `prod`; the android job now names
  `environment: prod` (environment secrets are invisible otherwise). `*.jks`/`*.keystore`
  git-ignored. The user restricted `prod` to `v*` tags, so a manual release run's android job fails.
- **First release, v1.0.1:** CI green; all four files built (signed `Kingdoms-1.0.1.apk`, 7 MB;
  .dmg 69 MB; `kingdoms_1.0.1_amd64.deb` 53 MB — no `-1`, Design fixed; web zip 4.5 MB). The
  publish step failed: the release already existed (made on GitHub's release page, which also
  makes the tag). It now uploads to an existing release, or creates one. The user then tagged
  **v1.0.2**: published, and the user installed and ran the APK and the .dmg — "Working".
- Choices without the user: About in the settings footer, not the header (it was clipped on a
  phone); British "Licence" on the chip, matching the game's English; no screenshot in the
  README; the Source code link points at the private repo, so it 404s for others until the repo
  is public.
- No tuning re-run needed (no rule, setup or AI change).
- **Picking this up:** the user makes the repo public (§2). Then the open M6 items: measure
  performance on the P30, the iOS ICU warning; optionally try the .deb on Linux and the web zip
  from the release, and host the web build (GitHub Pages works once the repo is public).

### 2026-10-17/18 — Session 1k: web target, CI and releases, pacts

- User chose: web first, then CI; a new private GitHub repo; .dmg + .deb + APK + web zip; tests
  on push, release on a tag. Design §2.4 (new), §6.4, §7, §8.1, §10; CLAUDE.md commands + 2 rules.
- **Web:** `wasmJs` in `core` (tests in Node) and `composeApp` (`Main.kt` with `ComposeViewport`,
  `WebSaveStore` on `localStorage`, `WebSpeaker` on Web Audio, `index.html` with a loading line).
  No code in `commonMain` had to change: the save/speaker interfaces carried it. Favicon and
  apple-touch icon from `AppIconTest.webIcons` (ran with `ICONS=1` for that test only; the other
  platforms' icon files were not rewritten).
- **Surprise:** 3 `SaveTest`s failed on Wasm — Kotlin/Wasm's `String.toDouble` is 1 bit off for
  ~1 in 8000 numbers (no YouTrack issue found). Harmless for play (Design §7); the tests now
  compare coordinates to 1e-12 and everything else exactly. Worth reporting to JetBrains.
- **CI:** `ci.yml` (tests, snapshots kept 7 days) and `release.yml` (version from the tag; macOS
  on `macos-latest`, so Apple silicon only; signed APK only with secrets). One app version in
  `libs.versions.toml` (1.0.0 — Android was 0.1.0, but macOS needs major ≥ 1).
- **Local packaging** (user: use the JetBrains Temurin): with `JAVA_HOME` on Temurin 26
  `packageDmg` passes the vendor check (67 MB .dmg); the packaged app starts. JDK 26 warned that
  Skia loads native code, so the app now runs with `--enable-native-access=ALL-UNNAMED`; the
  warning is gone. The Homebrew-JDK question is closed.
- **Two games in Chrome by me** (user asked), before pacts: lost seed 549751 in round 12 to the
  kingdom far from the early fighting; won seed 1463 as the far kingdom in round 12 (37 armies
  by round 3, half the board by round 5). Both decided by distance, not play — which led to the
  pacts. The web build ran both games with no errors.
- **Runaway balance** (user: a pact against a common threat on Normal and Hard; maybe cap
  growth by upkeep or a province cap). User decided via questions: people in pacts both ways;
  chronicle + turn-panel mark; measure caps before choosing; trigger tuned by simulation; then
  35%/28%, caps dropped, hinterland-first setup without a growth guard, try exposure
  compensation. New report metrics: leader after round 5/8 wins, farthest/remotest/quietest
  kingdom wins; `TUNING_QUICK`, `TUNING_VARIANTS`. Findings (Design §4.7, §9): the pact cuts
  the round-5 leader's wins 69–75% → 49–55% but games run ~5 rounds longer (Medium median 20);
  caps, upkeep, growth-share trigger, exposure compensation (1–5/step) and neutrals-first moved
  nothing beyond noise; geography is the cause — the remotest kingdom won 55%, now 47–49%.
  I first reported hinterland balance as 55→45% without saying it loosened growth fairness;
  with a guard it was 51%; corrected and the user chose the unguarded version (growth spread
  ≤ 6, `startsAreNearlyEven` bound 3 → 6, compensation covers it).
- **Difficulty moved:** Hard alone vs Normal 36% → 26–27%, Easy 17% → 11–12%, mostly from the
  new setup, not the pact. From the player's seat it still scales: one Normal beats three Easy
  33%, Normal 24%, Hard 18% (report now prints both directions).
- Code: `Pacts.kt` (`Pact`, `RuleOptions`, update at round end, `holds`),
  `Event.PactFormed/Ended/Left`, `GameState.pact/pactBreakers/options`, AI ally filter and +3
  bonus, `NewGame` hinterland; app chronicle lines, a "Pact: … against …" line in the turn panel
  (`pactLine`), province-card warning; `PactTest` (11), 2 AI tests, snapshot `pact.png`.
- Played by the user: seed 1463 (not the remotest seat): red grew fastest, the others allied
  against it, later against purple; "more interesting, with the tide turning", length good. The
  swords mark was unclear and the pact's chronicle line scrolled away, so the turn panel now shows
  "Pact: A, B and C against D" while it lasts. A second game where the user swept the board with
  no pact turned out to be the stale 11:49 web build (before pacts); rebuild the web app before
  playing it. Then a game where the user broke a pact left "Pact: Corrindor against Dunmoor", a
  one-member pact, so the user decided: a pact ends at once below two members (or when its
  target falls), and a breaker sits out new pacts for 3 rounds (`RuleOptions.breakerSitsOut`).
  Tuning re-run: unchanged within noise (AIs never break pacts; Medium k=4 leader 51–55%,
  median 20, remotest 48–49%; player seat vs Easy/Normal/Hard 34/26/19%).
  Replayed by the user after the change: red ran away, the pact formed, the user (in it) lost
  anyway — "works". Then a random game: the pact formed against the user, ended when Brightvale
  fell (two kingdoms left), and the user won in round 15 — "fun game".
  Seeds from before 1k start differently now (hinterland setup).
- Choices without the user: Wasm only (no JS fallback for older browsers); macOS arm64 only;
  debug APK when there are no secrets; snapshots uploaded from CI; a manual run of the release
  workflow builds but never publishes. Breaking a pact needs no AI rule: AIs never attack members.
- **Picking this up:** when the user has pushed to GitHub, watch the first CI run and a test tag
  (§2); otherwise the open M6 items: P30 performance, the iOS ICU warning. Rebuild the web app
  before anyone plays it in a browser.

### Earlier

- **Session 1j:** help screen (seven tabs, scenes on real grid maps), sound effects generated in
  code (`Synth`, per-platform `Speaker`, tuned by ear with the user), app icons drawn by the game.
- **Session 1i:** autosave/resume (GameState snapshot, Continue button), touch tuning on the P30
  (relative zoom limits, near-miss taps), Android text-halo fix (`drawStyle = Fill`).
- **Session 1h:** new-game screen with seats, colours and difficulty (Easy 14 / Normal 23 / Hard
  34%); "red usually wins" was geography plus a fixed launch seed (now random); a map-generation
  crash on 2 of 3000 seeds fixed (`ManySeedsTest`); markers scale at half the zoom; names when
  zoomed in.

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
