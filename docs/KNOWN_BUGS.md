# Known Bugs and Unfinished Work (Engine-Wide)

This document tracks **bugs**, incomplete implementations, and known parity gaps that we intend to fix but haven't addressed yet. Entries here are *not* intentional — they're acknowledged problems with a plan (or hope) of eventual resolution.

For **intentional** deviations from the original ROMs (architectural choices, feature extensions, deliberate bug-fixes of ROM data), see [KNOWN_DISCREPANCIES.md](KNOWN_DISCREPANCIES.md).

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

When the main player loses the last life, OpenGGF currently clamps the life count at zero and runs the normal
respawn flow. The ROMs instead leave normal gameplay for a Game Over / Continue sequence.

This affects Sonic 1, Sonic 2, and Sonic 3&K. Continues are tracked in `GameStateManager`, but no current gameplay
flow consumes them.

### Current State

Until the Game Over / Continue state exists, zero-life gameplay remains pausable. This is a deliberate release
compromise to avoid the previous broken hybrid state where the player could respawn forever at zero lives but could
no longer pause.

### Removal Condition

Remove this entry once last-life death branches into a ROM-appropriate Game Over / Continue flow for each supported
game, continues are consumed where applicable, and zero-life normal gameplay no longer persists after the death
sequence.

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
frontiers tracked in `docs/TRACE_FRONTIER_LOG.md`.

`TraceData` now logs a one-shot notice when a pre-v3 trace directory is loaded so the fallback is visible during test runs.

### Removal Condition

Remove this entry once the remaining pre-v3 trace fallback path in `TraceExecutionModel` /
`TraceData` is deleted or intentionally retained with a documented compatibility reason, and the
release trace gate distinguishes green guard traces from known-red frontier traces without hidden
warning-only failures.
