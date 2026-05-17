---
name: trace-replay-bug-fixing
description: Use when investigating or fixing any *TraceReplay test failure across the engine. Covers the comparison-only invariant (trace data is read-only diagnostic input, never written back into engine state), the recorder/parser/comparator pipeline, the diagnose-fix-regen-loop workflow, cross-game parity rules, and how to extend the recorder when more diagnostic data is needed.
---

# Trace Replay Bug Fixing

> Mirrored at `.claude/skills/trace-replay-bug-fixing/skill.md` (Claude Code, lowercase) and `.agents/skills/trace-replay-bug-fixing/SKILL.md` (other agent harnesses, uppercase). When editing, update both files.

Recorded BizHawk traces verify that the engine plays back ROM behaviour pixel-for-pixel given the same controller input. When a `*TraceReplay` test diverges, this skill describes how to diagnose, fix, and (when needed) regenerate traces — without taking shortcuts that mask engine bugs.

## Core Mission Rules (apply to all trace work)

1. **No hacks or dirty fixes.** Every behaviour change must be backed by the disassembly for the relevant game. Cite ROM file and line numbers in commits and code comments.
2. **You may regenerate a trace** when the recorded data is genuinely insufficient for diagnosis (missing per-frame data, broken setup, recorder schema changed). Use the `tools/bizhawk/record_*_trace.bat` launcher with the matching `.lua` recorder. Regeneration is part of the loop — don't avoid it. **But** do not regenerate just to "make the test match"; regenerate to gain visibility.
3. **If the engine architecture is missing or fundamentally broken**, or game objects/functionality aren't yet implemented, **plan and delegate**. Use review agents and parallel subagent execution for large-scope work. Don't try to land everything in one pass.
4. **Cross-game parity is non-negotiable.** The engine supports three games (Sonic 1, Sonic 2, Sonic 3 & Knuckles). Before changing any shared/root code (physics, collision, sidekick AI, oscillation, rendering, audio, shared object base classes, shared object helpers, etc.), check the disassemblies for **all three games** to confirm whether the change is a universal correction or a per-game divergence. Universal corrections must cite the matching ROM pattern for each affected game and keep all games' traces green. Per-game divergences must be gated behind a `PhysicsFeatureSet` flag (see CLAUDE.md "Per-Game Physics Framework") or an equivalent explicit behaviour flag at the owning abstraction. Prefer the smallest accurate scope: use a per-object or per-class hook when the divergence belongs to one object family, and reserve per-game flags for true game-wide ROM/system differences. **Never** branch on `if (gameId == GameId.S3K) ...`.

## The Core Invariant — Comparison Only, Never Sync

**The engine must be able to play back any BK2 movie and produce ROM-correct behaviour natively, with no trace data on its inputs.**

The trace replay tests prove this. They are not state-syncing harnesses.

- The **only** input the engine receives during a trace replay test is the BK2 controller stream (Player 1 buttons each frame). Everything else — sidekick AI, oscillator phase, object state, audio, RNG, sub-pixel position — must evolve natively from the same starting conditions ROM had at frame 0.
- `physics.csv` and `aux_state.jsonl` are **read-only diagnostic data**. They feed the divergence comparator and the divergence report. They are **not** allowed to write back into engine state in committed test code.
- **Pre-trace bootstrap is fine.** Setting starting position, RNG seed, oscillation pre-advance, and frame counter once at frame 0 is "load a save state at the BK2 starting point". The prohibition is on per-frame write-back during the comparison loop.
- **Diagnostic re-seeding is acceptable, but only as uncommitted exploratory work.** ("Does this divergence cascade from a single bad frame? Re-seed sidekick state at frame K and see what happens" — fine to try, not fine to land.)

If a trace replay test passes only because engine state is snapped back to ROM-correct values each frame, the engine has not been verified — it has been masked. That defeats the purpose.

### Concrete prohibitions

- No `applyRecordedFirstSidekickState`-style methods that copy CSV columns into engine sprites/managers/controllers as part of the per-frame test loop.
- No `hydrateFromRom*` calls in the per-frame loop. Such helpers may exist in engine code, but the test loop must not invoke them.
- New aux event types are **diagnostic only**. They feed the divergence report and per-frame comparator; they do not feed engine state.
- No "elastic windows" or "tolerance bands" introduced to suppress divergence around suspected engine bugs. If a comparison threshold is non-zero, it must reflect a known ROM/engine semantic difference declared as a tolerance because it is not a bug. Otherwise the bug is fixed in the engine and the threshold stays zero.

### What to do when the engine diverges from ROM

- Find the engine code path that should have produced the ROM-correct value but didn't. Fix the engine.
- If the engine has no equivalent path, port the ROM logic with disassembly citations and a `PhysicsFeatureSet` flag if it's game-divergent.
- If the trace lacks the diagnostic data needed to pinpoint the bug, extend the recorder. New fields are comparison context, not write-back targets.

### Frame-0 bootstrap comparator (post-2026-05-15)

For traces recorded at `lua_script_version >= 9.2-s2`, `TraceBinder.compareBootstrapFrame0(trace, EngineSnapshot)` runs once at the start of each test and asserts engine state at frame 0 against the recorder's `player_history_snapshot`, `cpu_state_snapshot`, and `object_state_snapshot` events. Mismatches become `BootstrapDivergence` entries rendered ahead of per-frame divergences in `target/trace-reports/<game>_<zone>_report.json`. Legacy traces (older recorder versions) are skipped — their frame-0 state was captured under the pre-ADR-1 freeze-during-title-card engine and is no longer valid for comparison; re-record at v9.2-s2 to opt in.

### Engine title-card behaviour (post-2026-05-15)

`GameLoop` ticks `ObjectManager` + player physics every frame during the title-card phase, matching ROM `TitleCard_Main` for S1/S2/S3K. Player input is locked via the same path the ROM uses (`Sonic_ControlsLock` / `Ctrl_locked`). This means `Sonic_Pos_Record_Buf` fills naturally during the prelude — previously the engine froze object updates during the card, leaving the position-history ring empty at frame 0 and triggering sidekick AI divergences in the first ~300 frames of every level-select trace.

### Diagnostic reseeding policy

Do not commit trace-to-engine hydration switches or writeback binders. If a bootstrap divergence needs A/B isolation, use a local throwaway patch or a debugger to reseed state, then remove it before committing. The committed replay path must remain comparison-only: it may report pre-trace snapshots and compare bootstrap frame 0, but it must not copy recorded `player_history_snapshot`, `cpu_state_snapshot`, or `object_state_snapshot` data into engine runtime state.

## Pipeline Overview

```
+------------------------+     +------------------------+     +------------------------+
| BK2 movie (Bizhawk)   | --> | Lua recorder (.lua)    | --> | Trace files            |
| - P1 controller frames |     | - reads RAM each frame |     | - metadata.json        |
|                        |     | - writes physics.csv   |     | - physics.csv          |
|                        |     | - writes aux_state.jsonl|    | - aux_state.jsonl      |
+------------------------+     +------------------------+     +------------------------+
                                                                       |
                                                                       v
+------------------------+     +------------------------+     +------------------------+
| Engine simulation      | <-- | AbstractTraceReplayTest| <-- | TraceData parser       |
| - reads BK2 input only |     | - drives engine        |     | - reads metadata       |
| - native simulation    |     | - compares each frame  |     | - reads physics rows   |
+------------------------+     +------------------------+     | - reads aux events     |
        |                              |                      +------------------------+
        v                              v
+------------------------+     +------------------------+
| Engine state per frame | --> | TraceBinder.compareFrame| --> DivergenceReport
+------------------------+     +------------------------+
```

The arrow from `TraceData` into the engine simulation goes ONLY into `compareFrame`. There is no arrow from `TraceData` into engine state.

## File Layout

### Recorder (`tools/bizhawk/`)

- `<game>_trace_recorder.lua` — per-game lua scripts launched inside Bizhawk-2.11 with `--lua <recorder>` and `--movie <bk2>`. Each frame the script reads RAM, classifies the frame phase, and emits one CSV row plus zero-or-more aux JSONL events.
- `record_<game>_trace.bat` — Windows launcher wrapping Bizhawk's headless mode.
- `trace_output/` — scratch directory the recorder writes to. Outputs are *manually copied* into the test resources tree.

Lua-side schema versioning:
- `local TRACE_SCHEMA_VERSION = 5` — bumped only when CSV columns change.
- `local LUA_SCRIPT_VERSION = "<version>"` — bumped on any recorder change (CSV, aux, or behaviour).
- `aux_schema_extras` — list of optional aux event types this trace contains. Parsers opt in by checking `TraceMetadata.has<Feature>()`.

### Trace files (`src/test/resources/traces/<game>/<zone>/`)

- `metadata.json` — game, zone, act, BK2 frame offset, trace frame count, oscillation pre-advance, character set, lua/recorder version, ROM checksum, profile, `aux_schema_extras`.
- `physics.csv` — one row per recorded frame. Frame numbers are hex; fields documented in the recorder's CSV header function.
- `aux_state.jsonl` — one JSON object per line. Standard event types: `zone_act_state`, `checkpoint`, `state_snapshot`, `mode_change`, `slot_dump`, `object_appeared`, `object_near`, `object_removed`. Plus opt-in events declared in `aux_schema_extras` (e.g. `cpu_state` per-frame for sidekick CPU state).
- `*.bk2` — the BK2 movie. Bizhawk replays this against the ROM to drive the recording. `bk2_frame_offset` in metadata is where recording starts inside the BK2.

Pre-trace setup events (frame `-1`) capture starting state for one-time bootstrap (player position history, RNG seed, oscillator phase, object snapshots).

### Parser (`src/main/java/com/openggf/trace/`)

- `TraceData` — top-level loader. `load(Path)` reads metadata + physics + aux. Random-access by frame.
- `TraceFrame` — one CSV row.
- `TraceMetadata` — parsed metadata.json. `hasPerFrameXxx()` accessors return true when the corresponding `aux_schema_extras` key is present.
- `TraceEvent` — sealed type hierarchy of aux event records.
- `TraceBinder` — frame-by-frame comparator. `compareFrame(expected, actual...)` records divergences into a `DivergenceReport`.
- `DivergenceReport` — summarises errors and warnings. `getContextWindow(frame, radius)` produces a human-readable side-by-side dump.
- `<engine>.hydrateFromRomXxx` helpers — engine helpers may exist for one-off probes, but **are not invoked from the test per-frame loop** (per the core invariant).

### Test framework (`src/test/java/com/openggf/tests/trace/`)

- `AbstractTraceReplayTest` — abstract base. Subclasses provide game/zone/act/path; the base class loads metadata, validates configuration, drives BK2 playback via `HeadlessTestFixture`, runs the per-frame comparator, and writes the divergence report.
- Concrete subclasses: one per recorded zone.

## Workflow — Diagnose, Fix, Regen, Loop

```
1. Run the failing trace test:
     mvn test -Dtest=<Test*TraceReplay> -DfailIfNoTests=false

2. Read target/trace-reports/<game>_<zone>_report.json (errors[0])
   and target/trace-reports/<game>_<zone>_context.txt
   (divergence window: ROM vs engine side-by-side).

3. Locate the diverging field at the first error frame K:
     - Player physics (x, y, x_speed, ...): inspect physics CSV row K.
     - Aux state (objects, checkpoints, CPU state): inspect aux_state.jsonl events at frame K.

4. Find the matching ROM routine in the relevant disassembly:
     - docs/s1disasm/, docs/s2disasm/, docs/skdisasm/
     - Use the s1disasm-guide / s2disasm-guide / s3k-disasm-guide skills
       to navigate, plus the RomOffsetFinder tool for offset lookups.
   Read the ROM logic completely. Compare with the engine path.

5. Identify the divergence. Choose the fix:
     - Engine code path missing a step that ROM does:    add it.
     - Engine code path doing a step ROM doesn't:        remove it (carefully).
     - Engine code path with wrong constant/threshold:   fix the value.
     - Per-game divergence:                              add a PhysicsFeatureSet flag.
     - Test infrastructure asserting wrong behaviour:    fix the test (with disasm citation).
     - Wall/floor probe X/Y offset mismatch: the player path uses
       fixed-pixel offsets in places (e.g. S3K
       `CheckRightWallDist` does `addi.w #$A,d3`, sonic3k.asm:20195,
       NOT `x_radius`). Engine probes that use `centreX + xRadius`
       silently miss walls when `x_radius` shrinks (rolling=7 vs
       default=9). When a trace shows a "speed cap held for several
       frames then a 5-frame `0/0x18/0x30/0x48/0x60/0` cycle on
       `x_speed` with a sub-x snap pushback once per cycle", that's
       the canonical "rolling-air sliding into a flush wall" pattern
       from `SonicKnux_DoLevelCollision` and the probe-offset is
       the prime suspect.

6. If you can't pinpoint the bug because the trace lacks the right data:
     - Extend the recorder lua with a new aux event type.
     - Bump LUA_SCRIPT_VERSION; add an opt-in key to aux_schema_extras.
     - Add a matching TraceEvent record + parser handler.
     - Wire the new data into DivergenceReport rendering or a probe class.
     - Regenerate the affected trace(s).
     - DO NOT wire the new data into engine-state mutation in the test loop.

7. Implement the fix:
     - Disassembly-cited (file + line numbers).
     - Cross-check the other two games' disassemblies for shared code.
     - Gate divergences at the narrowest owning abstraction:
       per-object/per-class hook for object-family quirks, or
       `PhysicsFeatureSet`/equivalent per-game flag only for game-wide
       ROM/system behaviour.

8. Run the trace test plus cross-game traces:
     mvn test -Dtest='Test<Game1>Ghz1TraceReplay,Test<Game1>Mz1TraceReplay,Test<Game2>Ehz1TraceReplay,Test<Game3><Zone>TraceReplay' -DfailIfNoTests=false
   All previously-green traces must stay green; the targeted trace
   should advance its first error frame (or, ideally, become green).

9. Update `docs/TRACE_FRONTIER_LOG.md` whenever a trace frontier moves,
   a trace fix is committed, a previously passing trace regresses, or a full
   trace sweep is used to select the next target. Record the exact command,
   commit/worktree context, pass/fail status, error count, and first-error
   frame/field. If a result was measured with local uncommitted investigation
   edits, say so explicitly so the snapshot is not mistaken for clean branch
   state.

10. Commit with proper trailers (see Branch Documentation Policy in
   CLAUDE.md/AGENTS.md). No --no-verify.

11. **Skill catalogue update (object/badnik fixes only).** If the fix
    touched code under `src/main/java/com/openggf/game/sonic{1,2,3k}/`
    (objects, badniks, lifts, springs, monitors, etc.), evaluate
    whether the root cause is a class of bug that could recur in any
    not-yet-implemented object of the same game:
      - Read the existing `.agents/skills/s{1,2,3k}-implement-object/rom-pitfalls.md`
        for that game. Does the fix match an existing pattern? If yes,
        consider adding the fresh commit hash + a one-line example to
        the existing entry's "Originating commit" list.
      - If the bug is a NEW pattern not yet catalogued, append a new
        entry following the format in the pitfalls file. Mirror to
        `.claude/skills/.../rom-pitfalls.md` in the same logical change.
        Use the `Skills: updated` commit trailer.
      - Cross-apply: if the pattern is plausibly cross-game (S2 and S3K
        share the same ROM convention), copy the entry to the other
        game's pitfalls file with that game's disasm citation.
      - If the bug is a one-off (specific to this object's quirks, not
        a generalisable rule), skip the catalogue and just commit.
    Goal: each accumulated pitfall entry prevents that pattern from
    recurring in future object implementations.

12. Loop: read the new first-error frame, repeat from step 3.
```

## Trace Triage Notes

Classify sidekick failures by phase before changing AI: native route-start/title-card/object/scroll preludes, spawn/approach setup, normal following history, object contact/riding, and release/respawn recovery produce different signatures. A Tails mismatch at the first controllable frame usually points to bootstrap/prelude state; a later mismatch after object contact usually points to standing-bit or carry timing.

For object-contact divergences, add or inspect fields that explain the contact edge: object id/subtype/routine, object centre position, piece index, player and sidekick standing bits, collision side, carried delta, release reason, and whether contact was persisted from the previous frame or freshly detected. These fields are diagnostic context only; they must not drive engine state.

Separate moving-platform timing from generic collision timing. If a trace first diverges on a rideable solid, compare the object routine transition, timer pre-decrement, platform motion, `SolidObject` call frame, standing-bit refresh, player carry, and walk-off/release helper in that order. Do not collapse them into one "platform collision" fix.

When a trace mismatch lands on trigonometric object physics, do not replace ROM math with host floating-point approximations. Check whether the ROM routine calls `CalcAngle`, `CalcSine`, or a game-specific lookup table, then use the engine's integer lookup helpers (for example `TrigLookupTable`) or add an equivalent integer path with disassembly cites. S2 CNZ map bumpers are a concrete case: `CNZBumpersReact_Angle` reflects the incoming `CalcAngle` result and multiplies the `CalcSine` components by `-$A00` (`docs/s2disasm/s2.asm:32334-32677`); a one-angle rounding difference changed the bounce velocity and moved the CNZ frontier.

For power-up timer divergences, identify both the ROM counter value and the phase where the ROM decrements it. S1/S2 speed shoes use a word `$4B0` timer decremented from display after movement, while S3K uses a byte `(20*60)/8` timer decremented only every eighth frame. If the engine timer runs in a different phase, gate the compensation in `PhysicsFeatureSet` instead of changing a shared timer constant globally.

When comparing sidekick CPU gates, distinguish ROM's raw `object_control` byte tests from the engine's split flags (`objectControlled`, `objectControlAllowsCpu`, `objectControlSuppressesMovement`). S2 `TailsCPU_Spawning` uses `tst.b obj_control(a1)` and must block respawn for any nonzero object-control byte; S3K catch-up code has narrower bit-7-style gates in other paths. Keep S2's Tails respawn/flying timeout separate from normal despawn: `TailsCPU_CheckDespawn` writes the `$4000,0` marker, but `TailsCPU_Flying`'s 300-frame offscreen timeout writes `x_pos=0,y_pos=0`, `Tails_CPU_routine=2`, `obj_control=$81`, and `Status_InAir` (`docs/s2disasm/s2.asm:38795-38806,39043-39052`).

Route-start traces need their native preludes accounted for: title-card delays, route-start bootstrap, object spawning windows, scroll/parallax pre-advance, oscillation phase, and any zone intro skips. Prefer recording or replaying the real prelude when possible; use frame-0 bootstrap only for state ROM would already have at the BK2 start.

Do not leave gameplay-affecting scroll logic hidden in render-only parallax updates. If a ROM scroll routine owns camera words, velocity globals, or route object inputs (for example S2 `SwScrl_SCZ` driving `Camera_X_pos` and `Tornado_Velocity_X/Y`), expose that as a logic-frame hook used by headless replay and rendering. The render pass should consume the resulting scroll state, not be the only place that mutates it.

Embedded `SolidObject` calls belong where the ROM calls them inside the object's routine, not automatically at the end of every engine object update. For objects that move, branch, then call solid handling mid-routine, preserve that placement so player/sidekick carry and release observe the same pre- or post-motion coordinates as the ROM.

For death and dead-fall divergences, verify the exact ROM motion helper and
velocity ordering before changing generic death code. S2 `Obj01_Dead` /
`Obj02_Dead` call `ObjectMoveAndFall`, which loads old `y_vel` for the 16:16
position add, then stores `y_vel += $38` for the next frame
(`docs/s2disasm/s2.asm:37901-37911,40736-40738,29967-29981`). S1 hurt/death
and S3K Tails death paths follow the same old-velocity-for-position pattern
via `SpeedToPos` / `MoveSprite_TestGravity`
(`docs/s1disasm/_incObj/01 Sonic.asm:1792-1795`,
`docs/skdisasm/sonic3k.asm:29280-29285,36068-36083`). If the engine moves with
post-gravity velocity, subpixel carry will be off by one or more pixels. For
S2 sidekick generic-dead frames, remember that `Obj02_Dead` can branch to
`TailsCPU_Despawn` when `y_pos > Tails_Max_Y_pos + $100` and then still run the
same frame's `ObjectMoveAndFall` from the marker (`docs/s2disasm/s2.asm:40736-40759,39043-39052`).

## Trace Regeneration

When you need new diagnostic data, regenerate the trace. The proven Windows PowerShell pattern:

```powershell
$env:OGGF_<GAME>_TRACE_PROFILE = "<profile>"
Set-Location <repo or worktree root>
if (Test-Path "tools\bizhawk\trace_output") { Remove-Item -Recurse -Force "tools\bizhawk\trace_output" }
& "tools\bizhawk\record_<game>_trace.bat" `
    "C:\path\to\<rom file>.gen" `
    "src\test\resources\traces\<game>\<zone>\<bk2 file>.bk2" `
    "<profile>"
```

Output lands in `tools/bizhawk/trace_output/`. Copy `metadata.json`, `physics.csv`, `aux_state.jsonl` into the test resources tree (the `.bk2` is unchanged) and commit the regen as a separate logical change from any recorder schema change.

Profiles are declared inside the lua via `is_*_profile()` predicates — check the recorder for the available list. Common ones: gameplay-unlock starts at controls-active, level-gated-reset-aware starts at gameplay and discards on soft-reset, end-to-end starts at BK2 frame 0.

## Recorder Extension Recipe

When a divergence can't be pinpointed without more ROM-side state:

1. **Lua side.** Add a helper function (e.g. `write_<feature>_per_frame()`) that reads the RAM block of interest and emits a JSONL line with a new `event` type. Call it from the per-frame entry. Bump `LUA_SCRIPT_VERSION`. Add an opt-in key to `aux_schema_extras` (e.g. `"<feature>_per_frame"`).
   If a focused frontier only needs a few extra fields on an existing generic diagnostic such as `state_snapshot`, add the fields there and force snapshots for a narrow frame window instead of creating a new event type. Typical S1/S2 movement-input questions need both BK2/CSV input and ROM-side `Ctrl_1_Held_Logical` plus `move_lock`, because `Sonic_Move` consumes the logical RAM byte after `ReadJoypads` runs from V-int (`docs/s2disasm/s2.asm:701,1361-1387,36253-36260`).
2. **Java parser.** Add a new sealed-record type to `TraceEvent` (e.g. `TraceEvent.<Feature>State`). Parse the new JSON event in `TraceEvent.parseJsonLine`. Add `TraceMetadata.hasPerFrame<Feature>()` and `TraceData.<feature>StateForFrame(frame)`. Keep parsers tolerant — old traces without the new key must still load.
3. **Diagnostic use.** Wire the new data into `DivergenceReport.getContextWindow` rendering, or into a dedicated probe class for targeted bug investigation. **Do not** wire it into engine state mutation in the per-frame test loop.
4. **Regenerate the affected trace(s).** Commit the regen separately from the recorder schema change so reviewers can see the data churn distinctly.

## Recorder Limitations and Existing Tooling

### "Input alignment error at trace frame N" failure mode

```
org.opentest4j.AssertionFailedError: Input alignment error at trace frame N:
BK2 input=0xXXXX, trace input=0xYYYY. Check bk2_frame_offset in metadata.json.
```

This is **not** a parity bug in the engine — it means the trace's CSV `input`
column does not match what BK2 plays for that frame. Two common root causes:

1. **Stale `$FFF604` (ROM-side Ctrl_1_Held) reads.** ROM `Read_Joypads` runs
   only from specific V-int subroutines. On lag frames and long V-int paths
   (notably SCZ Tornado handoffs, OOZ tunnel exits, ARZ end-of-act
   transitions), `$FFF604` can lag the BK2 logical input by one game frame.
   The Lua recorder used to read `$FFF604` for the CSV `input` column;
   modern recorders (S2 v9.3-s2+, S1 v3.1+, S3K v6.19-s3k+) read
   `movie.getinput()` directly via the `bk2_input_mask` helper, so the CSV
   column matches BK2 by construction.

2. **`bk2_frame_offset` actually wrong** in `metadata.json`. Rare — happens
   when the recorder armed at an unexpected `emu.framecount()` boundary or
   the BK2 was edited after recording.

**Existing repair tools** (use these BEFORE re-recording, which is slow):

- `tools/bizhawk/normalize_s2_traces_input.ps1 -Routes <list>` — rewrites the
  CSV `input` column on existing S2 traces by reading the BK2 movie's Input
  Log directly. Resolves stale-`$FFF604` cases without re-recording.
- `tools/bizhawk/record_s2_level_select_traces.ps1` — bundles the normalize
  step at the end of every record, so freshly recorded S2 traces are already
  BK2-aligned.

For S1 / S3K, the same logic exists in their respective Lua recorders
(`bk2_input_mask` helper) but no PowerShell normalize wrapper has been built
yet. If an S1/S3K trace fails alignment, port the S2 normalize script.

### Engine-side standing/ride diagnostic (post-2026-05-16)

`EngineDiagnostics` exposes the engine's tri-state truth for:

- `ride=N` — `ObjectManager.isRidingObject(player)` (1 = riding, 0 = not, -1 = not captured).
- `standsnap=N` — `ObjectManager.latestStandingSnapshot(player)` (1 = standing, 0 = not).

These render in the `ENG:` line of `<game>_<zone>_context.txt`. They diverge
from the live `statusByte` bit 0x08 (on-object) during platform-release and
walk-off transitions — the typical divergence class for "engine drops Sonic
to airborne one frame before/after ROM" frontiers. Read these alongside
`onSlot=N(0xTT)` (which slot Sonic is currently riding, if any) and the
`sub=(xsub,ysub)` block (engine sub-pixel coordinates).

### Sub-pixel diagnostic (P9 / 1-pixel-Y frontiers)

The CSV columns `sonic_x_sub` / `sonic_y_sub` / `tails_x_sub` / `tails_y_sub`
record ROM-side sub-pixel coordinates. The engine's matching values appear
in the `ENG:` line as `sub=(XXXX,YYYY)`. For P9-pattern 1-pixel Y frontiers
(MCZ f1085, S1 LZ3 f221, MGZ f1538), compare the ROM and engine `sub=`
blocks across the frames around the divergence to identify which routine
dropped the sub-pixel carry.

## Cross-Game Sanity Checks

Always run all green trace tests every iteration when touching shared code:

```
mvn test -Dtest='*TraceReplay' -DfailIfNoTests=false
```

For S3K work specifically, also keep the S3K must-keep-green tests green:
- `TestS3kAiz1SkipHeadless`
- `TestSonic3kLevelLoading`
- `TestSonic3kBootstrapResolver`
- `TestSonic3kDecodingUtils`

If a fix is genuinely game-divergent (different games' ROMs really do behave differently), add a flag to `PhysicsFeatureSet`, set the right value on each game's `SONIC_1`/`SONIC_2`/`SONIC_3K` constant, and branch on the flag at the call site.

## When to Stop and Plan

Per mission rule 3, hand work off when scope expands beyond a clean fix:

- Multiple objects/badniks need to be implemented (use the `<game>-implement-object` and `<game>-implement-boss` skills).
- A whole zone needs bringing up (use `s3k-zone-bring-up` for S3K; pattern transfers).
- A subsystem (audio driver, collision framework, animation pipeline) needs significant rework.
- A trace bug requires recorder schema changes + parser updates + multiple engine fixes — split into commits/agents per concern.

Plan first, dispatch parallel subagents per independent concern (use the `superpowers:dispatching-parallel-agents` skill), then integrate.

## Related Skills

When working through a trace bug you'll often pull these in:

- **Disassembly navigation:** `s1disasm-guide`, `s2disasm-guide`, `s3k-disasm-guide` — label conventions, file structure, RomOffsetFinder commands.
- **Object/badnik implementation:** `s1-implement-object`, `s2-implement-object`, `s3k-implement-object`, `s1-implement-boss`, `s2-implement-boss`, `s3k-implement-boss`.
- **Trace recording (game-specific):** `s1-trace-replay`, `s1-retro-trace`.
- **S3K specific:** `s3k-plc-system`, `s3k-zone-events`, `s3k-zone-validate`, `s3k-zone-analysis`, `s3k-zone-bring-up`, `s3k-palette-cycling`, `s3k-parallax`, `s3k-animated-tiles`.
- **Generic engineering process:** `superpowers:systematic-debugging`, `superpowers:dispatching-parallel-agents`, `superpowers:writing-plans`, `superpowers:test-driven-development`, `superpowers:verification-before-completion`, `superpowers:requesting-code-review`.

## Why This Matters

The mission is faithful pixel-for-pixel reimplementation. Trace replay tests are the proof. If they're allowed to lean on synced trace data each frame, the proof is hollow — bugs hide behind the synchronisation and the test green-lights anyway. Honest tests force honest engine fixes. That's how progress compounds: every fix makes the next divergence visible instead of building on top of a masked one.
