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

`GameState` (package `core.rules`) is an immutable value: the map, the kingdoms, one `Holding`
(owner, armies, fresh armies) per province, round, current kingdom, winner, and a `draws`
counter. `Rules.apply(state, action)` is the only way state changes; it returns
`Outcome.Applied(state, events)` or `Outcome.Rejected(reason)`. Human input and AI produce the
same `Action`s (`Move(from, to, armies?)`, `EndTurn`). Events (moves, battles, eliminations,
round ends, turn starts, victory) come back with the new state so the UI can describe and
animate them.

Each battle draws from `Random(mix(seed, draws))` and increments `draws`, so a state plus an
action always yields the same next state, and the state stays a plain serializable value.

## 3. The map

### 3.1 Generation pipeline

1. **Points.** Best-candidate (Mitchell) sampling inside the map rectangle — blue noise with an
   exact point count — then 2 rounds of Lloyd relaxation so cells are similar in size.
2. **Voronoi.** Each cell is the rectangle clipped by the perpendicular bisector to every other
   site, nearest first, stopping once no bisector can reach it. Corners computed by neighbouring
   cells are merged into one shared vertex table by union-find over corners closer than a
   millionth of the cell spacing — the same vertex up to rounding, nothing more; every interior
   edge must then appear in both cells. (A 1% snap distance crashed on 2 of 3000 Medium seeds
   and, made transitive, mis-tiled ~2% of maps; `ManySeedsTest` checks 9000 maps.) If a layout
   were ever inconsistent the generator retries with a salted site stream, deterministically.
3. **Land and sea.** Cells touching the map edge are always sea. Land grows from the
   best-scoring interior cell, each step adding the best-scoring interior cell adjacent to the
   land so far, until it has exactly `landProvinces` cells. Score is low-frequency value noise
   minus a radial falloff, giving one irregular island. Land is connected by construction.
4. **Irregular borders.** Every edge touching land becomes a noisy polyline, generated **once
   per edge** from a seed mixed from the map seed and the two cell ids, and used by both sides —
   so neighbours always share the exact same border. The path subdivides recursively inside a
   convex quad whose controls stay within the fan triangles (edge + site) of the two convex cells,
   so borders never cross each other or self-intersect, and each site stays inside its province.
5. **Terrain** assigned per province (§3.3), **features** per border (§3.4).
6. **Names.** Each province gets a generated place name, unique within the map: a
   countryside prefix (Ash, Thorn, Willow…) and a suffix that fits the terrain (-ford for
   plains, -wood for forest, -crag for mountains, -fen for marsh, -down for hills; a coastal
   province often gets -haven, -mouth, -port).

Starting positions are a rule, not geometry (§4.1).

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

**Coastal** is an attribute, not a terrain: a province bordering the sea (any sea, including an
enclosed inland sea) keeps its own terrain, also produces Fish, and gains **+1 yield**. Coastal
plains grow 3 per round, other coastal provinces 2, inland non-plains 1 — harbour provinces are
the rich ones, as most large cities are on the coast.

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
| `seaFraction` | Share of the interior cells (not touching the map edge) that stay sea (default 0.35) |
| `featureFraction` | Share of land borders that carry a feature (default ≈0.33) |

The map rectangle is derived, not configured: the generator picks enough cells for the land, the
interior sea and an edge ring of sea, at a constant 10 000 units² per cell and a 3:2 aspect, so
province size stays constant and the map grows with the count.

Terrain is assigned by quota over two noise fields. Elevation is noise plus distance from the
sea, so the top 12% become mountains and the next 18% hills. Of the rest, the wettest 12% become
marsh and the next 22% forest. The remainder is plains.

Player-facing presets (for 4 kingdoms): **Small** 18, **Medium** 28 (default), **Large** 40 land
provinces, plus a custom count. The generator rejects a config with fewer than 3 land provinces per
kingdom. Generation hits `landProvinces` exactly: the land mask threshold is adjusted until the
count matches.

## 4. Rules

### 4.1 Setup

- 2–6 kingdoms; default 4 (player + 3 AI), set up as **seats** on the new-game screen: each has
  a name, a colour (from eight) and either a person or an AI difficulty (§5). The first seat is
  the player.
- Each kingdom starts with a **capital** and two neighbouring provinces. The setup tries 80
  candidate layouts — farthest-point capitals over graph distance from a random first pick, one
  refinement pass, then starting neighbours claimed in passes where the poorest kingdom picks
  first and takes the richest province next to its capital — and keeps the one with the smallest
  spread in starting growth among those at most one step less spread out than the best.
- Territories are dealt to seats at random, so turn order says nothing about map position.
- Each owned province starts with 2 armies, the capital with 5. A kingdom whose starting growth
  is below the best gets **3 rounds of its shortfall** as extra capital armies.
- **Head-to-head bonus:** in a two-kingdom game that alternates (Fixed or Shuffled order), the
  first mover gets **2** extra capital armies — the second mover always acts just before growth.
  Measured (200–400 games per size): first mover 35–42% without, Medium 50–53% and Large 47–50%
  with; Small stays a little short at 41–46% (3 overshoots to 54%).
- Remaining provinces are **neutral** with 1–3 armies. Neutral provinces do not grow.

### 4.2 Round and turn

A **round** is every living kingdom taking one turn. In round 1 people move first, then the AIs
— in random order under Shuffled, by seat otherwise. From round 2 the order follows the game's
`TurnOrder`, **Shuffled** by default:

| TurnOrder | Order each round |
| --- | --- |
| Fixed | By kingdom id, every round |
| Rotating | The starting kingdom moves one seat each round |
| Shuffled | A fresh random order each round, from the game seed. With two kingdoms left they alternate: the one that did not just move goes first, so neither gets a double turn |

At the end of the round, provinces grow (§4.5).

During its turn a kingdom performs any number of **moves**, then ends the turn.

### 4.3 Moves

The player taps a source province they own, then an adjacent target.

- **Amount.** By default all *surplus* armies move — everything except 1, which always stays
  behind. Long-press (touch or mouse) or right-click on the target opens an amount picker
  instead; an arrow shows the pending move, and the selected province's panel says how.
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

Armies are home-grown. At the end of each round every owned province gains its yield
(§3.3: terrain yield, +1 if coastal) in armies, in place. There is no reinforcement pool and no placement step.

- Every terrain yields at least 1, so a living kingdom always gains at least one army per round.
- There is no per-province cap.

Consequences, intended: rich provinces are well defended by default, the hinterland is never
empty, and getting armies to the front is logistics — moving them province by province, one step
per turn (§4.3). A kingdom stretched into a long column cannot abandon its tail.

### 4.6 Elimination and victory

A kingdom with no provinces is eliminated. The last kingdom with provinces wins. If the player is
eliminated the game ends in defeat.

## 5. AI

Heuristic, cheap, deterministic (same state, same action), and runs in `core.ai`. Each call
returns one action; the AI is asked again until it ends its turn.

1. **Attack.** Over every own province with movable armies and every non-own neighbour whose win
   chance with all movable armies is at least the personality's *boldness*, pick the highest
   `chance × value / (1 + 0.1 × defenders)`. Value is the target's yield, plus 2 for a capital,
   plus 3 if its owner has two provinces or fewer, plus the personality's *neutral bias* for a
   neutral. **Commit** the larger of: the fewest armies reaching the personality's *confidence*,
   and everything except a guard as large as the biggest other enemy stack next to the source.
2. **Logistics.** Move fresh armies from an interior province one step towards the frontier
   province with the best prospect — the best `value / (defenders × border defence + 1)` of its
   targets — divided by `1 + distance`. Supply goes where an attack can succeed, not merely to the
   nearest front: nearest-front supply deadlocked equal stacks across mountain borders.
3. **End turn.**

**Difficulty** (chosen per AI on the new-game screen) changes skill, not personality:

| Difficulty | Plays | Measured: one vs three Normal (fair 25%) |
| --- | --- | --- |
| Easy | Attacks only at ≥ 90%, commits for 80%, supplies the nearest front every other round | 14% |
| Normal | As above | 23% |
| Hard | Normal, plus: fronts an enemy stack could take next turn get supply first; +2 value on the strongest rival's provinces | 34% |

| Personality | Boldness | Confidence | Neutral bias | Plays seats |
| --- | --- | --- | --- | --- |
| Balanced | 0.70 | 0.95 | 1.0 | 1, 4 |
| Aggressive | 0.55 | 0.85 | 0.0 | 2, 5 |
| Cautious | 0.85 | 0.99 | 2.0 | 3 |

The UI plays AI turns one action at a time (≈450 ms per move, 200 ms per end of turn), draws the
latest move as an arrow in the mover's colour (gone once that kingdom ends its turn), and offers **Skip** to resolve the remaining AI
turns at once.

## 6. Screens and interaction

Landscape only: phones are locked to landscape (Android `sensorLandscape`, iOS landscape
orientations); desktop windows are landscape by default. Android runs full screen (system bars
hidden, a swipe from the edge shows them for a moment): a landscape phone is only ~360 dp tall.
The smallest screen checked is the Huawei P30, 780 × 360 dp (snapshots `*-p30.png`).

### 6.1 In game

The map fills the screen; the HUD sits in the corners on parchment panels.

- **Top left — kingdom strength.** Three 100% stacked bars, one segment per living kingdom in
  its colour: *production* (growth per round), *territory* (provinces), *armies* (total). The
  player's segment is outlined and the player's own value is printed at the end of each bar.
- **Top right — turn.** Round number; this round's turn order as coloured dots, the current
  kingdom enlarged; whose move it is (an AI shows its personality); **End turn** on the player's
  turn, **Skip** during AI turns; a Menu button. Under it, a short **chronicle** of the last
  three events, told with names: "Aldmere takes Thornwick from Brightvale", "Brightvale is
  defeated at Millbrook", "Dunmoor falls to Aldmere" — kingdom names in their colours, the
  numbers dimmed, older lines faded.
- **Selected province — card.** Bottom of the screen, on the side away from the province so it
  never covers it: terrain icon, province name, terrain, owner, capital; *Grows* as one resource icon per army grown
  (coastal Plains: grain, grain, fish); armies with a shield icon and — for the current kingdom's
  provinces — a stacked bar of *can move* / *stays* (the one army that must remain) / *fatigued*
  (moved this turn), with a legend; defended borders with feature icons and multipliers; and a
  one-line how-to while a move is possible.
- **Moves.** Click a province, then a neighbour: all movable armies go. Right-click or long-press
  the neighbour instead to open the **amount picker** next to the target: a slider from 1 to all
  movable, the win chance for an attack and about how many would survive a win, Cancel and
  Move/Attack. An arrow shows the pending move.
- **Selection.** The selected province gets a bright outline; rivers along its edge are drawn
  over it, since they are what defends it.
- **Map labels.** Dashed outlines on valid targets with win chances. In the fitted view, banners
  shrink to 60% when provinces get small on screen; zooming scales them half as much as the map.
  Zoomed in (a province over ~120 dp wide on screen), each province's name fades in under its
  banner.
- **Touch.** Drag to pan, pinch to zoom; zoom stays within 0.8×–6× of the fitted view and the
  middle of the screen stays over the board, so the map cannot be lost. A finger tap that misses
  the land (a coast, a cliff, a thin province) takes the province most of a ring of points 12 dp
  around it lands on; a mouse click does not. A touch with two fingers down is never a tap or
  long-press. Chips are ~34 dp tall (small ~28 dp), buttons ~40 dp. Under 480 dp of height the
  province card leaves out its how-to line.
- **Game over** — a centred panel: victory or defeat, rounds played, New game.

### 6.2 New game, menu, result

- **New game** (also the title screen): on the left the settings — map size (presets and ±2
  provinces), seed (editable, Shuffle for a new one; random on launch), turn order, and the
  kingdoms: colour swatch (tap for the next unused colour), editable name, *You* or an AI
  difficulty, remove; Add a kingdom up to six; **Start** stays pinned at the bottom. On the right a
  live preview of the actual map with each kingdom's starting territory in its colour. Start is
  disabled, with a reason, when the map is too small for the kingdoms. With a saved game,
  **Continue · round N** (in the player's colour) sits beside Start, which then reads *Start
  new*; starting one replaces the save.
- **Menu** (in game): Resume, How to play, Restart (same setup, new game seed), New game…, and
  the sound control (§6.4). AI turns wait while the menu or help is open.
- **Result**: Victory / Defeat / "X wins" in the winner's colour, rounds played, Play again,
  New game….

### 6.3 Help

**How to play**, from a chip beside the title on the new-game screen and from the in-game menu:
a panel over everything with seven tabs — Goal, Moving, Attack, Strength, Borders, Growth,
Tactics — and Close; each tab ends with *Next*. Decided 2026-10-16 (user): tabs rather than one
scrolling page, each with a looping animation drawn with the game's own pieces.

- **Layout.** The scene on the left with a caption under it (and, where it helps, a strength bar
  in the HUD's style); a few short lines on the right, with icons from the HUD. Tactics has no
  scene. Fits the P30 (780 × 360 dp) without scrolling the scene.
- **Scenes are real maps.** `core.map.GridMap` lays out a 3 × 2 or 3 × 3 grid of provinces with
  the generator's noisy borders and coasts; it holds to the same invariants as generated maps
  (tested alongside them). Each scene is a few hand-set `GameState`s over time, drawn by
  `MapView` with gestures off — plates, pennants, towers, targets, arrows, the clash and the
  win-chance labels are the game's own, and every chance or survivor count comes from `Battle`
  in core. An overlay adds only taps (a ripple), armies on the march (shields along the arrow)
  and growth (each province's resource icons rise into its count, then "+n").
- **Scenes:** Goal — a capital and two provinces each, then conquest until one kingdom is left,
  with the territory bar. Moving — all but one march, they arrive fatigued, only the fresh army
  goes on. Attack — a sure win against a neutral, then an even fight that is lost. Strength — 4 to
  12 attackers against 6 defenders. Borders — the same attack across an open border, then each
  feature in turn. Growth — two round ends on a 3 × 3 map; the inland and neutral provinces show
  the coast bonus and that neutrals do not grow.
- Wording follows §4; numbers in the text (win chances, survivors, multipliers) are computed, not
  typed.

### 6.4 Sound

Decided 2026-10-16 (user): the effects are **generated in code**, not files, with variation;
there is a **volume and mute** setting; "be nice to people". The user liked the victory and the
click; the first clash rang like a bell and the first march was a white-noise slide; both were redone
from reference recordings the user chose (only measured, nothing from them ships). Four sounds (`Synth`, pure common Kotlin, deterministic):

| Sound | When | What it is |
| --- | --- | --- |
| Click | any button or chip | a 60 ms sine blip falling from 1150 Hz |
| Clash | a battle (with the clash animation) | swords meeting, modelled on a recorded clash: a short bright scrape, then a ring at ×1, 1.5, 1.96, 2.35 of ~2 kHz with nothing low (a low fundamental made the first version a bell), each partial with a twin 30–90 Hz off for shimmer; the upper partials lead and die first; −11 dB in 0.1 s, 0.85 s in all; 4 variants, each with its own seeded pitch, twins and decays |
| March | a move into one's own province | two footfalls of grey noise, modelled on a recording of soldiers marching: pink noise without the sub-bass (< 100 Hz), extra body at 150–500 Hz, gently rolled off above ~3 kHz; each step rises in 45 ms and falls ~17 dB before the next, its onset a little brighter (the crunch) with gravel — tiny random ticks of grit around 2 kHz, up to ~900 a second as the foot lands — over a low rumble; about 30% of its energy above 2 kHz, like the recording; steps ~0.42 s apart, near the recording's 0.46 s (the user found three steps at 0.19 s, then two at 0.32 s, too fast), 0.95 s in all; 3 variants with seeded spacing and step levels |
| Victory | a person wins | ta-da: G+B, then a long C major chord with soft harmonics and vibrato |

- **Being nice.** Every sound fades in and out over 4 ms (no clicks) and is scaled to its own
  level, so none is louder than intended: Click and Clash are about equally loud (−21 to −19 dBFS
  RMS over their first 0.2 s), March ~5 dB softer (−26; the user found it too loud beside
  them; its variants are levelled by that loudness, since its peak is a random stone of gravel),
  Victory louder (−14.5). Peaks are Click 0.3, March 0.23–0.38 (at most 0.45),
  Clash 0.7 (it falls fast, so its peak sits high for its loudness), Victory 0.6. The AI's
  moves play at 60% of the player's; the same sound is not repeated within 60 ms, a march not
  until the last one has finished (the user: quick moves, the AI's or the player's, piled up), and an action
  makes at most one battle-or-march sound however many events it has (Skip plays a whole round
  at once). The help scenes are silent. No defeat sound. iOS uses the Ambient session: the
  silent switch mutes the game and the player's music keeps playing. Android plays as game
  audio on the media volume.
- **Setting.** Volume 0–10 (default 6) and mute, in the in-game menu (Sound on/off and a slider
  that clicks at the new level); the new-game screen has the Sound on/off chip. The amplitude is
  (volume / 10)², so the steps sound even. Kept in `settings.json` beside the save (§7); a file
  that does not parse gives the defaults.
- **Playback** is per platform behind `Speaker`, passed into `App` like the save store: desktop
  `javax.sound` clips opened on a background thread (no device: silent after one message),
  Android `SoundPool` from WAVs written to the cache at launch, iOS `AVAudioPlayer` (two per
  sound, so a quick repeat overlaps).

## 7. Save and resume

One save slot. The app saves after every applied action — a phone can kill the app in the
middle of the player's turn — and once when a game starts; a finished game clears the save.
Decided 2026-10-16 (user): a **`GameState` snapshot**, not seed + action log, and a Continue
button on the new-game screen rather than launching straight into the game.

- **Format:** JSON via kotlinx.serialization (`core.rules.Save`): the whole `GameState`, map
  included, so a save does not depend on the generator staying the same; defaults are written
  out; unknown keys are ignored. The app wraps it as `SavedGame(setup, restarts, state)`, so
  Restart after a resume gives the same next seed. Battles draw from the state's seed and
  `draws` counter (§2.3), so a loaded game plays on exactly as it would have.
- **Size:** 150 KB (Small) to 300 KB (Large); encoding under 1 ms, decoding ~3 ms on desktop.
- **Where:** `save.json` — desktop `~/Library/Application Support/Kingdoms` (macOS) or
  `$XDG_DATA_HOME/kingdoms` (Linux, default `~/.local/share`); Android the app's `filesDir`; iOS
  Application Support. Written to `save.json.tmp` and renamed, so a crash mid-write keeps the old
  save. A save that does not parse or fails validation is treated as no save; a failed write
  loses the save but does not stop play.
- Not saved: the chronicle, the last AI arrow, the camera, the selection.

## 8. Visual style

Cozy, cartoony, cel-shaded — a painted tabletop diorama seen from above at a slight angle.

- **Board space.** The ground plane is drawn with `y × 0.8` (`Board.TILT`); heights go straight
  up in board units, so props stay upright and are painted back to front by their base `y`. The
  camera maps board space to the screen; taps map back exactly, so hit-testing is unchanged.
- **Land as a board.** The island is a slab `Board.WALL` (14) units thick: every coast segment is
  swept downward into an earth wall in two tones, with a soft drop shadow on the water and foam
  along the coast and the wall foot.
- **Cel shading.** Flat fills, one hard-edged light band on the lit (upper-left) side, outlines in
  a darker shade of the fill (`Color.darker`), never black, never gradients. Outlines scale with
  the board but never drop below ~1 px.
- **Kingdom colours.** Ownership is a light tint plus a coloured band inside the province
  outline, so the terrain stays readable; banners and towers carry the full colour.
- **Props.** Placed once per map (same map, same props), clear of each province's edges and of
  its banner: tree clusters (forest), rounded hills, snow-capped two-tone mountains, reeds and
  pools (marsh), rounded crop fields and grass tufts (plains). Border features are drawn on the
  border: rivers as ribbons, lakes as ovals at the border's middle, ridges as hills, mountain
  ranges as peaks, marsh as reeds and pools along the line.
- **Water.** Sea-coloured background with drifting wave marks and pulsing foam.
- **Armies.** On each site, a count plate in the owner's colour (neutral: parchment) with what
  the province grows under it as resource icons (as on the card), a white dot while armies can still move, a pennant on a pole for owned
  provinces, and a small tower with a roof in the kingdom's colour under the pennant for a
  capital, then the province's name when zoomed in (§6.1). They scale with the map at half its
  rate, so they read zoomed out and grow, less than the land, zoomed in.
- **Battles.** Under a second, cartoonish and bloodless: two swords swing in and cross above the
  defending plate, a small spark where they meet, dust puffs at ground level around the plate,
  then a ring in the winner's colour spreads from it. AI moves wait for it to finish.
- **Performance.** Four stacked canvases: water (the only one reading the animation clock),
  land (walls, terrain, ownership, borders, props — redrawn only on camera or ownership change),
  overlay (selection, targets, arrows, banners), and effects (battle animations, ticking only
  while one plays). Each has its own `graphicsLayer`; without it the
  water's per-frame invalidation re-runs the land's draw every frame (measured: 150 land draws in
  150 frames without, 1 with).

### 8.1 App icon

Decided 2026-10-17 (user): the player's capital — a forest province, roughly hexagonal — as the
icon. `AppIcon.kt` draws it with the map's own pieces: the earth slab with its shadow and foam,
the forest green with the kingdom's tint and inner edge, `Prop.Tree`s, the capital's tower and
pennant in the first kingdom colour (blue, the player's default). No army count: a number is
noise at launcher size. The hexagon's sides have a slight seeded wobble, like the map's borders.

- **Grid.** Drawn on Android's adaptive-icon grid, 108 units, with everything inside the 66-unit
  circle every launcher mask keeps; icons that show the whole square (iOS, desktop) draw the land
  1.3× larger.
- **Files**, rendered by `AppIconTest` and written with `ICONS=1`: Android adaptive icon (sea
  background and land foreground as PNGs per density, `mipmap-anydpi-v26/ic_launcher.xml`;
  minSdk 26, so no legacy icon); iOS one opaque 1024 px image in `Assets.xcassets`; macOS
  `composeApp/icons/kingdoms.icns` (a rounded tile, 824 of 1024, via `iconutil`) and Linux
  `kingdoms.png`. The desktop window draws the icon live (`AppIconPainter`).
- The test checks that the iOS icon is opaque and shows the player's colour, and that no pixel of
  the Android foreground lies outside the safe circle; `build/icons/preview.png` shows it under
  the masks at 192, 96 and 48 px. No Android 13 monochrome layer yet.

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

Decided 2026-10-15, head to head: with two kingdoms left, Shuffled order alternates (no double
turns); a two-kingdom game gives the first mover a 2-army bonus (§4.1). Own-turn growth was
measured as the alternative and rejected (first mover 78–83%).

Decided 2026-10-15, turn order: **Shuffled** by default. Simulation (300 games per line, all AI)
showed Fixed order letting the first seat — the human — win only 8–14% of 4-kingdom games and the
last seat 37–48%; Rotating still up to 35% for seat 0; Shuffled 21–29% for every seat. Fixed and
Rotating stay available.

Decided 2026-10-15: home-grown armies (§4.5); fatigue, one move per army per turn (§4.3); no sea
movement (§3.2); no terrain defence (§3.3); Forest stays; no turn or time limit (§1, §9);
configurable map size (§3.5). Coastal is an attribute giving +1 yield, not a terrain (§3.3).
