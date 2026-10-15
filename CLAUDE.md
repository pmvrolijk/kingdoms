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
./gradlew :core:allTests                        # core tests on JVM, Android host and iOS simulator
./gradlew :composeApp:run                       # desktop app
./gradlew :androidApp:assembleDebug             # APK → androidApp/build/outputs/apk/debug/
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # iOS framework without Xcode
open iosApp/iosApp.xcodeproj                    # iOS app; the build phase calls Gradle
```

- Versions live in `gradle/libs.versions.toml` only. Compose Multiplatform 1.12 requires
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
