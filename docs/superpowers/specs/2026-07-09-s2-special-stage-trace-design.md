# S2 Special Stage Trace Capture & Replay — Design

Date: 2026-07-09
Status: Approved for planning

## Goal & Scope

Add a new trace type for Sonic 2 special stages: a BizHawk lua recorder that captures
per-frame ROM truth inside `GameMode_SpecialStage`, plus engine replay support
(headless JUnit + visual test mode) that steps the engine's S2 special stage
deterministically against the recorded trace.

Motivating bug areas (to be *exposed* by this work, *fixed* by a separate follow-up plan):

1. Control locked/unlocked at the wrong times.
2. Tails CPU sidekick behavior (partial team support exists in
   `Sonic2SpecialStageManager.setupPlayers()` with a 16-frame `tailsCtrlRecordBuf`;
   the ROM uses the larger `SS_Ctrl_Record_Buf` plus a 180-frame `Tails_control_counter`
   P2-override window — `s2.asm:70420-70449`).
3. Playback speed approximated by a flat-0.35 lag compensator
   (`Sonic2SpecialStageManager.java:985-991`) instead of the ROM's real
   speed-factor/lag behavior (`SSRun_Animation_Timers`, `s2.asm:960-982`).
4. Timing of text/graphics (start banner, "GET n RINGS" flyout, rings-to-go HUD)
   and ring/bomb appearance.

**MVP scope:** special-stage interior only — trace starts at special-stage game-mode
entry (launched from level select via the existing
`docs/BizHawk-2.11-win-x64/Movies/s2-lvl-select-special-stage.bk2`) and ends at
results/exit. Sonic + Tails team.

**Definition of done:** pipeline proven end-to-end — recorder produces a valid trace;
the headless test loads it, steps the engine, and emits a faithful divergence report
(first-error frame/field); visual test mode can play it. The test may be red;
divergences become the follow-up fix plan's worklist and enter
`docs/TRACE_FRONTIER_LOG.md`.

**Approach decision:** parallel SS trace profile reusing the proven S2 level-select
pipeline shape, discriminated by `trace_profile: "s2_special_stage"` end-to-end
(recorder → metadata → parser → fixture → catalog). No unification with level
`TraceFrame` (semantics differ) and no premature generic stage-mode framework —
the profile discriminator and the `TraceReplayFixture` interface are the seams a
future S1-special-stage trace can grow through.

## Frame Pacing Decision (load-bearing)

The S2 special stage lags heavily and irregularly on real hardware; BizHawk records
lag frames exactly. **Replay is trace-paced at logic-frame granularity:** the engine
steps one special-stage update per recorded non-lag frame; recorded lag frames consume
the corresponding BK2 input row without stepping (same precedent as
`skipFrameFromRecording()` for VBLANK-only frames in level traces). The flat lag
compensator is disabled during replay (`setLagCompensation(0)`). Comparison is
logic-frame to logic-frame, so it is exact.

Pacing and pad inputs are the only trace-derived data fed to the engine; the
comparison-only invariant (no state hydration from trace) holds unchanged.

Replacing the lag compensator for *normal* (non-replay) play is out of scope here;
the recorded lag schedule is the data source for a future state-derived lag model.

## Component 1 — Recorder: `tools/bizhawk/s2_ss_trace_recorder.lua`

Derived from `s2_trace_recorder.lua` (v9.10). Behavior:

- Idle until `Game_Mode` (`$FFFFF600`) `== 0x10` (`GameModeID_SpecialStage`); record
  every emulated frame (including lag frames, flagged) until the mode exits.
- Assert SS mode is reached within a bounded frame count of movie start (guards
  against bk2 drift); record `bk2_frame_offset` at SS entry.

### Output trio (standard layout)

**`physics.csv`** — new csv profile, all values hex. Global columns:

| Column | Source |
|--------|--------|
| `frame`, `input`, `input_p2`, `lag` | movie inputs P1/P2, `emu.islagged()` |
| `speed_factor` | `SS_Cur_Speed_Factor` |
| `track_anim`, `track_anim_frame`, `track_drawing_index`, `track_orientation`, `track_duration_timer` | `SSTrack_*` block |
| `current_segment` | `SpecialStage_CurrentSegment` |
| `player_anim_frame_timer` | `SS_player_anim_frame_timer` |
| `rings_togo_bcd`, `check_rings_flag` | `SS_RingsToGoBCD`, `SS_Check_Rings_flag` |
| `tails_control_counter` | `Tails_control_counter` |
| `swap_positions_flag` | `SS_Swap_Positions_Flag` |

Per character (`sonic_*` block then `tails_*` block), read from the standard object
slots (MainCharacter `$FFFFB000`, Sidekick `$FFFFB040`) using the SS field offsets
(`s2.constants.asm:138-153`):

`present, ss_x (+2A), ss_x_sub (+2C), ss_y (+2E), ss_y_sub (+30), ss_z (+34),
angle, routine, routine_secondary, status, anim, anim_frame,
rings_bcd (+3C..3E), hurt_timer (+36), slide_timer (+37), flip_timer (+33)`

**`aux_state.jsonl`** — per-frame events: checkpoint/emerald routine changes,
intro/message lifecycle transitions (start banner, "GET n RINGS" flyout via the
owning object slots plus `SS_NoRingsTogoLifetime` / `SS_TriggerRingsToGo` /
`SS_HideRingsToGo`), raw `off_00..off_3F` slot dumps for active rings/bombs near the
player plane (Java composes fields later, as level traces do), and a frame -1
`state_snapshot` (stage layout pointer `SS_CurrentLevelLayout`, ring requirement
`SS_Ring_Requirement`, initial speed factor).

**`metadata.json`** — existing fields plus `trace_profile: "s2_special_stage"`,
`special_stage_index`, `characters`, `bk2_frame_offset`, `source_bk2`.

Capture is deliberately generous (cheap at record time); the comparator binds fields
incrementally.

## Component 2 — Recording workflow

Extend `tools/bizhawk/record_s2_level_select_traces.ps1`:

- Routes table gains an optional `Profile` column (default: level recorder lua;
  `s2_special_stage` dispatches the new lua).
- New route `special_stage` → `s2-lvl-select-special-stage.bk2`.
- Outputs land in `src/test/resources/traces/s2/special_stage/` (gzipped
  `physics.csv.gz` + `aux_state.jsonl.gz` + metadata + bk2 copy), same normalization
  and metadata-invariant checks. The input-column normalizer learns the `input_p2`
  column (zero for this bk2; needed for future human-P2-override traces).

## Component 3 — Headless replay

New classes (test tree unless noted):

- `SpecialStageTraceFrame` / profile-aware parsing in the trace loader
  (`com.openggf.trace`) keyed off `trace_profile` — existing level parsing untouched.
- `SpecialStageTraceReplayFixture` built on the existing deterministic harness
  `SpecialStageStepper` (`game/rewind/SpecialStageStepper.java:26`), which already
  drives `handleInput` → `handlePlayer2Input` → `update()` from `Bk2FrameInput`.
- `AbstractS2SpecialStageTraceReplayTest` + concrete
  `TestS2SpecialStageTraceReplay` in `src/test/java/com/openggf/tests/trace/s2/`.

**Bootstrap contract (comparison-only invariant holds):** the engine boots the special
stage natively — team config from metadata, then
`Sonic2SpecialStageProvider.initializeStage(specialStageIndex)`; lag compensation set
to 0. No recorded player/track/object state is copied into the engine. Per frame:
non-lag row → step fixture with that row's pad input; lag row → consume input without
stepping. Input validation per frame (BK2 vs CSV `input`), failing fast with
`bk2_frame_offset` guidance.

**Comparator tiers:**

- Tier 1 (errors): per-player `ss_x`, `ss_y`, `ss_z`, `angle`, `routine`, rings;
  global `speed_factor`, `current_segment`, `track_anim_frame`.
- Tier 2 (warnings initially, ratcheted to errors as fixes land): intro/message
  timing, `rings_togo_bcd`, checkpoint flags, `tails_control_counter`,
  `track_drawing_index`, hurt/slide timers.

Report JSON to `target/trace-reports/s2_special_stage_report.json` in the existing
format (first-divergence brief compatible with `TraceTriageTool`).

**Engine-side additions (src/main):** read-only accessors on
`Sonic2SpecialStageManager` (and/or a comparison snapshot reusing
`Sonic2SpecialStageSnapshot` fields) for track animator state, checkpoint/intro
phase, and per-player state — no behavior change.

## Component 4 — Visual test mode

- `TraceCatalog` validation gains a profile switch so SS trace dirs validate
  against the SS artifact set.
- `TraceSessionLauncher` gains a special-stage branch: instead of zone/act load it
  drives `GameLoop.doEnterSpecialStage()` and feeds BK2 inputs through
  `SpecialStageInputMapper` with the same trace-paced skips.
- Side benefit: paced skips reproduce the ROM's authentic perceived speed on screen,
  making side-by-side visual comparison with a BizHawk capture meaningful.

## Invariants & Error Handling

- Comparison-only invariant per the `trace-replay-bug-fixing` skill: committed code
  never hydrates engine state from trace fields.
- Per-frame input validation as in level traces.
- The new test is **not** added to must-keep-green; on first run its first-error
  frame/field is logged in `docs/TRACE_FRONTIER_LOG.md`.
- Recorder hard-fails if SS mode never appears (bk2 drift guard).

## Testing the Infrastructure

- Parser unit test for the new CSV profile (round-trip a hand-built row).
- Fixture determinism test: two replays of the same trace produce identical reports.
- `TraceCatalog` scan test covering a profile-tagged trace dir.
- Existing level-trace suites untouched (new paths gated by the profile
  discriminator); full trace sweep run before merge to confirm.

## Out of Scope (follow-ups)

- Full round trip (level → SS → return transition validation).
- All-7-stage complete-run traces; solo-Sonic / solo-Tails / human-P2 traces.
- S1 special stage generalization (seams intentionally left).
- All discrepancy fixes, including replacing the flat lag compensator for normal
  play with a state-derived lag model built from the recorded lag schedule —
  that is the follow-up plan, driven by this trace's divergence report.
