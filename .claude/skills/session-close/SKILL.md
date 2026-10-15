---
name: session-close
description: Use when a piece of work on kingdoms is finished and verified, or when the user signals the session is ending ("let's wrap up", "update the status", "write the handover", "nice work"). Writes the state back to docs/Status.md (and docs/Design.md if behaviour changed) so the next session can resume from prose. Never commits.
---

# Closing a session

Every session leaves a written state behind. This is the step that pays the next session.

## Never commit

Do not run `git add`, `git commit` or `git push` unless the user explicitly asks in that turn.
Instead:

- Summarise what changed, by file.
- Propose a commit message: what was built and how it was verified (e.g. "Map generator: shared
  irregular borders; property tests over 500 seeds pass"), not just "add map".
- Then stop.

## What to write

### 1. `docs/Status.md` — always

- **§1 Where things stand** — phase, what runs on which platform, test count if it moved.
- **§2 Open decisions** — remove what the user decided; add what came up.
- **§3 To do next** — tick finished items `[x]`; a half-done item stays unticked with a note on
  what is still open. Reorder if priorities changed and say why.
- **§4 Handover** — a new entry at the top: date, session number, a short title, then bullets:
  what was done, choices made without the user, anything surprising, and a final
  **Picking this up:** line naming the next concrete step. Condense entries older than the last
  three to one line each under "Earlier".

Keep it short. Status.md is read in full at every session start.

### 2. `docs/Design.md` — if behaviour changed

Normative, present tense, no history. Strike through superseded rules rather than deleting them;
move decided §11 questions into the section they belong to. Tuning numbers that changed after
simulation are updated here, with the measured result in the handover.

### 3. `CLAUDE.md` — only for traps

Add a rule only if a future session that did not know it would write a defect. One or two
sentences, imperative, the rule and the failure it prevents. Fill in §Commands when build or run
commands change.

## Honesty rules

- "Compiles" is not "runs"; "runs on desktop" is not "runs on iOS". Write what was actually checked.
- An unverified claim is written as unverified.
- Write the awkward open item down rather than leaving it out of a tidy status.
