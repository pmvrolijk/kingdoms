# CLAUDE.md

Guidance for Claude Code working in this repository. **Rules only** — reasoning belongs in
`docs/Design.md`, history in the handover in `docs/Status.md`.

| | |
| --- | --- |
| **Where things stand, to do, handover** | [`docs/Status.md`](docs/Status.md) — the session entry point |
| **What the game does** | [`docs/Design.md`](docs/Design.md) — normative. §11 is the open list |

Kingdoms is a short, cozy, Risk-like turn-based strategy game in Kotlin Multiplatform with Compose
Multiplatform, targeting Android, iOS and desktop (macOS, Linux).

## Working method

- **Open a session with the `session-open` skill; close it with `session-close`.** State lives in
  `docs/Status.md`, not in memory.
- **`docs/Design.md` is the only design document.** Do not create other design, rationale or
  notes files; add a section to Design.md instead. Update it in the same change as the code when
  behaviour changes.
- **Never commit, push or `git add` unless the user asks in that turn.** Propose a commit message
  instead.
- An open question in Design §11 is the user's to decide. Implement the proposed default only when
  told to, and say so.

## Commands

```sh
./gradlew :core:jvmTest                         # fast loop: core tests on the JVM
./gradlew :core:allTests                        # core tests on JVM, Android host, iOS simulator and Wasm (Node)
./gradlew :core:wasmJsNodeTest                  # core tests on Wasm only
./gradlew :composeApp:run                       # desktop app
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web app on http://localhost:8080
./gradlew :composeApp:wasmJsBrowserDistribution     # static site → composeApp/build/dist/wasmJs/productionExecutable
./gradlew :composeApp:jvmTest                   # snapshots → composeApp/build/snapshots/*.png, UI play tests
TUNING=1 ./gradlew :core:jvmTest --tests '*TuningReport*' --rerun -i | grep REPORT   # Design §9 numbers, ~5 min
TUNING=1 TUNING_QUICK=1 TUNING_VARIANTS=default,none,pact40-33 ./gradlew :core:jvmTest --tests '*TuningReport*' --rerun -i | grep REPORT   # compare rule variants
ICONS=1 ./gradlew :composeApp:jvmTest --tests '*AppIconTest*'   # re-render the app icons into the platforms (Design §8.1)
./gradlew :androidApp:assembleDebug             # APK → androidApp/build/outputs/apk/debug/
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # iOS framework without Xcode
open iosApp/iosApp.xcodeproj                    # iOS app; the build phase calls Gradle
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.2' build   # iOS app from the CLI
```

- Versions live in `gradle/libs.versions.toml` only, the app's own (`kingdoms`) included; a release
  tag overrides it with `-Pkingdoms.version` (Design §2.4). Compose Multiplatform 1.12 requires
  `compileSdk` 37; do not lower it.
- `iosApp` uses a folder-synced group: new Swift files in `iosApp/iosApp/` are picked up without
  editing `project.pbxproj`. Keep `Info.plist` outside that folder, or it is copied as a resource
  and the build fails.

## Architecture rules

- **Package root is `nl.lamia.kingdoms`** — also the Android application id and iOS bundle id.
  `core` lives under `nl.lamia.kingdoms.core`, the app under `nl.lamia.kingdoms.app`.
- **Map size is never a constant.** Everything that depends on it reads `MapConfig` (Design §3.5);
  tests run across size presets, and the player chooses it.

- **`core` is pure common Kotlin.** No Compose, no platform APIs, no `expect/actual` for game logic.
  If game logic needs it, it is in the wrong module.
- **State changes only through `apply(state, action)`.** The UI and the AI both submit `Action`s;
  nothing mutates `GameState` directly.
- **All randomness comes from the game's seeded `Random`.** Never `Random.Default`, never wall-clock
  time in `core` — it breaks reproducibility of maps, battles and simulation tests.
- **A border polyline is generated once and shared by both provinces.** Generating it per province
  produces gaps and overlaps between neighbours.
- **The UI's win-chance preview calls the battle code in `core`.** Never a separate estimate.
- **A UI test must check that the state it observes belongs to the game on screen** (seed, turn
  order), not just that some state changed: a new game on the same map has the same layout.
- **Text drawn after a stroked `drawText` passes `drawStyle = Fill`.** On Android the layout's
  paint keeps the stroke otherwise; desktop snapshots do not show it.
- **Every map layer `Canvas` keeps its own `graphicsLayer()`.** Without it, the animated water
  re-runs the land's draw (all props) every frame (Design §8).
- **A UI test that shows the help screen sets `mainClock.autoAdvance = false`** and advances the
  clock by hand: the scenes animate forever, so `waitForIdle` and auto-advance hang the test.
- **App UI tests start with `runUiTest`, not `runComposeUiTest`.** The latter gives up after 60 s
  of real time, which the play tests exceed on GitHub's Linux runner.
- **Web code calls JavaScript only from `wasmJsMain`** (`js("…")` in top-level functions), behind
  the same `SaveStore`/`Speaker` interfaces as the other platforms.
- **Release files come from `.github/workflows/release.yml`.** Change packaging there and in the
  Gradle builds together; CI never publishes from a branch, only from a `v*` tag.
- **Run the tuning report with `--rerun`.** Its `TUNING*` variables are not Gradle inputs, so
  without it an up-to-date test task prints nothing and an old result looks current.
- **Re-run the tuning report after changing rules, setup or AI.** Unit tests do not catch a
  fairness or game-length regression; the report does (Design §9).
