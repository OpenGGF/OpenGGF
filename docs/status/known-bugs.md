# Known Bugs and Unfinished Work (Engine-Wide)

This document tracks **bugs**, incomplete implementations, and known parity gaps that we intend to fix but haven't addressed yet. Entries here are *not* intentional — they're acknowledged problems with a plan (or hope) of eventual resolution.

For **intentional** deviations from the original ROMs (architectural choices, feature extensions, deliberate bug-fixes of ROM data), see [known-discrepancies.md](known-discrepancies.md).

Entries should include:
- **Location** — the file(s) where the bug lives, if known
- **Symptom** — what goes wrong and where you can observe it (test name, trace frame, manual repro)
- **Suspected cause** — best current theory, with ROM/disasm references when relevant
- **Removal condition** — what needs to be true for this entry to be deleted

---

## Table of Contents

1. [Game Over and Continue Flow Missing](#game-over-and-continue-flow-missing)
2. [Persisted Editor Saves Disabled for S3K Gameplay Loads](#persisted-editor-saves-disabled-for-s3k-gameplay-loads)
3. [Trace Replay Recorder Coverage Follow-Up](#trace-replay-recorder-coverage-follow-up)

---

## Game Over and Continue Flow Missing

**Location:** `src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java`,
`src/main/java/com/openggf/game/GameStateManager.java`, `src/main/java/com/openggf/GameLoop.java`

### Symptom

A death that produces a game over (the life subtraction reaches zero) or that follows a time over now stops the
level, matching the ROM's `restartime = 0`, but nothing takes over from there. The ROMs load the GAME OVER /
TIME OVER card object, play the game over music, queue the game over PLC, and then — after that object's own
12-second wait or an A/B/C press — restart the level on a time over or enter the continue screen on a game over
(`docs/s1disasm/_incObj/01 Sonic.asm:2019-2049`, `docs/s1disasm/_incObj/39 Game Over.asm:57-88`). OpenGGF has
none of that, so the corpse is held off-screen indefinitely.

This affects Sonic 1, Sonic 2, and Sonic 3&K; all three ROMs share the structure
(`docs/s2disasm/s2.asm:38279-38316`, `docs/skdisasm/sonic3k.asm:24581-24616`). Continues are tracked in
`GameStateManager`, but no gameplay flow consumes them.

### Current State

The crossing-frame half is modelled: the life comes off on the frame the corpse falls past the death row, and the
restart delay is armed with 60 frames only when the ROM would restart the level. What is missing is everything
downstream of that decision — the card object, the music/PLC pair, the wait, the time-over restart, and the
continue screen.

Zero-life gameplay remains pausable, a deliberate release compromise made while the Game Over state was absent.

### Removal Condition

Remove this entry once a game over or time over branches into a ROM-appropriate GAME OVER / TIME OVER card and
Continue flow for each supported game, a time over restarts the level from that flow rather than from
`restartime`, and continues are consumed where applicable.

---

## Persisted Editor Saves Disabled for S3K Gameplay Loads

**Location:** `src/main/java/com/openggf/level/LevelManager.java`,
`src/main/java/com/openggf/level/MutableLevel.java`,
`src/main/java/com/openggf/game/sonic3k/events/*`

### Symptom

Sonic 3&K levels currently skip automatic persisted editor-save application during normal gameplay level loads.
S1/S2 editor saves still apply normally. S3K editor sessions can still mutate the live editor/playtest level, but
those persisted edits are not re-applied the next time the S3K level is loaded from disk.

### Current State

This is a release safety guard. Several S3K runtime event paths still require the concrete `Sonic3kLevel` overlay
surface for PLC, pattern, chunk, and battleship/AIZ terrain swaps. Applying a persisted editor save wraps the loaded
level in `MutableLevel`; until `MutableLevel` can execute those S3K runtime overlays directly, that wrapper can disable
route-critical S3K event logic.

### Removal Condition

Remove this entry once S3K runtime overlay operations are expressed through a `MutableLevel`-safe capability or mutation
surface, and persisted S3K editor saves can be applied without disabling AIZ/CNZ/MGZ event handlers or terrain swaps.

---

## Trace Replay Recorder Coverage Follow-Up

**Location:** `src/test/java/com/openggf/tests/trace/*`, `tools/bizhawk/*`

### Symptom

BK2-derived fixture coverage now exists across Sonic 1, Sonic 2, and Sonic 3&K, but the suite is
mixed between green guard traces, known-red frontier traces, synthetic fixtures, and historical
pre-v3 trace directories. Older or pre-v3 traces can therefore still reach the legacy heuristic
path when they are loaded, and the full trace suite still depends on careful documentation of
known red frontiers.

### Current State

The shared replay harness understands schema v3 execution counters and uses
`gameplay_frame_counter` plus `vblank_counter` when those columns are present. The Sonic 1,
Sonic 2, and Sonic 3&K BizHawk recorders all emit schema v3. S3K also has a committed
complete-run per-zone trace suite from a Sonic+Tails AIZ-to-Doomsday route, with current
frontiers tracked in `docs/status/trace-frontier-log.md`.

`TraceData` now logs a one-shot notice when a pre-v3 trace directory is loaded so the fallback is visible during test runs.

### Removal Condition

Remove this entry once the remaining pre-v3 trace fallback path in `TraceExecutionModel` /
`TraceData` is deleted or intentionally retained with a documented compatibility reason, and the
release trace gate distinguishes green guard traces from known-red frontier traces without hidden
warning-only failures.
