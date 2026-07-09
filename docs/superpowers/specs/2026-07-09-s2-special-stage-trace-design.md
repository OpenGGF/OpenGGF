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
2. Tails CPU sidekick behavior. Team support already exists in
   `Sonic2SpecialStageManager.setupPlayers()`, and the engine's `tailsCtrlRecordBuf`
   (`int[16]`) matches the ROM's `SS_Ctrl_Record_Buf` size (16 words,
   `s2.constants.asm:2039-2041`); the 180-frame (`$B4`) `Tails_control_counter`
   P2-override window is also already implemented
   (`Sonic2SpecialStageManager.java:1422-1429` vs `s2.asm:70437`). The remaining gap
   is *semantic* parity — buffer shift order / delayed-tap index
   (`s2.asm:70426-70436`) and the P2-vs-solo branch conditions
   (`s2.asm:70411-70417`) — which the trace's divergence report will adjudicate.
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

**Press-edge rule across lag rows:** the ROM polls controllers every V-int,
*including lag frames* (`Vint_S2SS` → `ReadJoypads`, `s2.asm:837-840`), and
`Joypad_Read` computes press bits as `newHeld & ~prevHeld` between consecutive
polls (`s2.asm:1361-1387`). The fixture therefore computes press-edges against the
previous **physical** BK2 row (not the last *stepped* row) — matching the existing
`RecordingFrameDriver` behavior. A press that appears only on a lag row and is gone
by the next stepped row is legitimately invisible to game logic, in both ROM and
replay. With SS's high lag density this rule is load-bearing, so it is pinned here
rather than inherited by assumption.

Pacing and pad inputs are the only trace-derived data fed to the engine; the
comparison-only invariant (no state hydration from trace) holds unchanged.

Replacing the lag compensator for *normal* (non-replay) play is out of scope here;
the recorded lag schedule is the data source for a future state-derived lag model.

## Component 1 — Recorder: `tools/bizhawk/s2_ss_trace_recorder.lua`

Derived from `s2_trace_recorder.lua` (v9.10). Behavior:

- Idle until `Game_Mode` (`$FFFFF600`) `== 0x10` (`GameModeID_SpecialStage`); record
  every emulated frame (including lag frames, flagged) until `Game_Mode` changes.
  **Note:** the ROM runs the entire results/ring-bonus tally (Obj6F) while still
  under `Game_Mode == 0x10` — the mode only flips back to Level *after* the tally
  and PLC wait complete (`s2.asm:6794-6813`). The recorded trace therefore includes
  a results tail (~50-200+ frames) that the MVP fixture does **not** compare; it is
  retained as data for future results-screen parity work.
- Emit an aux `stage_finished` event at the frame the final checkpoint resolves
  (the ROM-side equivalent of the engine's `isFinished()`, before
  `Pal_FadeToWhite`/Obj6F). This marks the end of the compared range.
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
| `swap_positions_flag` | `SS_Swap_Positions_Flag` (see comparison caveats) |

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
  `physics.csv.gz` + `aux_state.jsonl.gz` + metadata + bk2 copy), same normalization.
  The script's validation helpers (`Assert-Metadata`, `Assert-ZoneActCoverage`)
  hard-require level/route concepts (`zone`, `zone_id`, `rom_zone_id`, `act`,
  `gameplay_segment`, per-act `zone_act_state` coverage) that have no SS analog —
  they gain an SS-profile validation branch (profile/stage-index/frame-count/
  bk2-offset invariants) instead of reusing the level checks verbatim. The
  frame-count invariant checks *total* recorded frames and must tolerate the
  uncompared results tail (compared range ends at the `stage_finished` aux event).
  The input-column normalizer learns the `input_p2` column (zero for this bk2;
  needed for future human-P2-override traces).

## Component 3 — Headless replay

New classes (test tree unless noted):

- `SpecialStageTraceFrame` / profile-aware parsing in the trace loader
  (`com.openggf.trace`) keyed off `trace_profile` — existing level parsing untouched.
- `SpecialStageTraceReplayFixture` — a new purpose-built replay driver *modeled on*
  (not delegating to) `SpecialStageStepper` (`game/rewind/SpecialStageStepper.java:26`).
  The stepper cannot be reused directly: it is package-private, purpose-built for
  held-rewind re-simulation, and deliberately suppresses the finish→results
  transition (`TestSpecialStageStepperReplay.finishingDuringReplayDoesNotDispatchResults`),
  while this fixture must drive the same
  `handleInput` → `handlePlayer2Input` → `update()` sequence *and* own the finish
  boundary: replay ends on the frame `SpecialStageProvider.isFinished()` fires,
  which corresponds to the trace's `stage_finished` aux event (the recorded results
  tail beyond it is not compared; the finish frame itself is, including that the
  finish fires on the *matching* frame).
- `AbstractS2SpecialStageTraceReplayTest` + concrete
  `TestS2SpecialStageTraceReplay` in `src/test/java/com/openggf/tests/trace/s2/`.

**Bootstrap contract (comparison-only invariant holds):** the engine boots the special
stage natively — team config from metadata, then
`Sonic2SpecialStageProvider.initializeStage(specialStageIndex)`; lag compensation set
to 0. No recorded player/track/object state is copied into the engine. Per frame:
non-lag row → step fixture with that row's pad input; lag row → consume input without
stepping. Input validation per frame (BK2 vs CSV `input`), failing fast with
`bk2_frame_offset` guidance.

Frame 0 of the compared range is the first recorded frame with
`Game_Mode == 0x10`; the fixture anchors it to a documented engine state
immediately after `initializeStage()` (intro `DROP` phase start — the exact anchor
frame is pinned during implementation, analogous to level traces'
`alignFrameCountersForReplayStart`). `routine` values are compared through an
explicit ROM↔engine mapping table (the ROM encodes hurt as a routine value; the
engine models it as `routineSecondary == 2`), defined in the fixture, not by raw
value equality.

**Comparator tiers:**

- Tier 1 (errors): per-player `ss_x`, `ss_y`, `ss_z`, `angle`, `routine`;
  **combined** ring total (sum of the two players' BCD fields, decoded to binary);
  global `speed_factor`, `current_segment`, `track_anim_frame`.
- Tier 2 (warnings initially, ratcheted to errors as fixes land): per-player rings,
  intro/message timing, `rings_togo_bcd`, checkpoint flags, `tails_control_counter`,
  `track_drawing_index`, `track_duration_timer` (via mapping), `swap_positions_flag`,
  hurt/slide timers.
- Recorded but not compared (engine counterpart missing/constant — see caveats):
  `player_anim_frame_timer`.

**Per-player rings caveat:** the ROM tracks each player's ring pickups separately
on their object slots (`ss_rings_*` at `objoff_3C..3E`; incremented per touching
object `s2.asm:70771-70789`, summed for the HUD `s2.asm:9938-9943`). The engine has
only a shared pool (`objectManager.collectRing()` regardless of which player
touched; `Sonic2SpecialStagePlayer` has no rings field). Per-player rings are
therefore *recorded* in the CSV but compared only as Tier-2 diagnostics until the
follow-up fix plan adds per-player ring tracking to the engine (a real ROM-parity
gap — the results screen tallies each player's rings separately). BCD fields are
decoded to binary before comparison.

**Further field-comparability caveats** (same class as rings — recorded now,
faithfully comparable only after small engine parity fixes; all are follow-up-plan
worklist items, not MVP blockers):

- `swap_positions_flag`: ROM has one global cell toggled by whichever player jumps
  (`s2.asm:69058`, `s2.asm:69253`); the engine keeps a *per-player copy* on each
  `Sonic2SpecialStagePlayer` (`swapPositionsFlag`, line 142) with no cross-instance
  sync, so the copies diverge from a ROM-faithful single flag whenever only one
  player acts. Tier-2 diagnostic only until reconciled.
- `player_anim_frame_timer`: the engine's only counterpart,
  `Sonic2TrackAnimator.getPlayerAnimFrameTimer()`, returns a constant
  (`getFrameDuration() - 1`) by its own admission, not a live decrementing counter.
  Recorded, **not compared**, until a real counter exists — wiring it in now would
  be permanently-noisy diagnostics.
- `track_duration_timer`: no engine accessor exists, and the closest analog
  (`frameDelayCounter`) counts *up* 0→duration while the ROM counts *down* to 0
  (`s2.asm:967`). Needs a new getter plus the same explicit ROM↔engine mapping
  treatment as `routine` — never raw value equality.

Report JSON to
`target/trace-reports/s2_special_stage_<special_stage_index>_report.json` —
per-trace prefix derived from metadata, matching the existing dynamic-naming
convention (`AbstractTraceReplayTest.java:1249-1250`) so future multi-stage traces
don't overwrite each other. Existing format (first-divergence brief compatible
with `TraceTriageTool`).

**Engine-side additions (src/main), enumerated:** `Sonic2SpecialStageSnapshot` is
package-private, so the fixture cannot read it from the test tree. Add a *public*
read-only comparison accessor on `Sonic2SpecialStageManager` (a small comparison
snapshot record or getters) exposing track animator state, checkpoint/intro phase,
and per-player state — no behavior change, no new mutators.

## Component 4 — Visual test mode

Sized honestly: this is a **parallel live SS trace driver** modeled on the level
`TraceSessionLauncher` stack, not a small in-place branch — the existing launcher
is level-shaped end-to-end (`TraceReplayDriver.start(zone, act)`, level camera
focus, `LevelFrameStep`-based stepping).

- `TraceCatalog` validation gains a profile switch so SS trace dirs validate
  against the SS artifact set. SS metadata carries no `zone_id`/`act` (they default
  to 0 in `TraceCatalog.tryLoad`); catalog labeling/sorting for SS entries derives
  from `trace_profile` + `special_stage_index` instead — a `TraceEntry` display
  change, called out so multiple SS traces don't all render as "zone 0 act 0".
- **`GameLoop.updateSpecialStageMode()` needs a new skip-gate**: unlike LEVEL mode
  (which has `shouldSkipCurrentGameplayTick()` at `GameLoop.java:1346` to suppress
  a gameplay tick on a lag row while advancing the BK2 cursor/VBlank counter),
  the SS path unconditionally calls `ssProvider.update()` every engine frame
  (`GameLoop.java:1049-1114`). A mirroring gate is a prerequisite for live
  trace-paced skips: on a skipped row the BK2 cursor and frame bookkeeping advance
  but `ssProvider.update()` does not run.
- The SS live driver drives special-stage entry and feeds BK2 inputs through
  `SpecialStageInputMapper` with the same trace-paced skips as headless replay.
  Note: `GameLoop.doEnterSpecialStage(...)` is `private` (`GameLoop.java:1995`; the
  one existing test caller uses reflection). Loosen it to package-private —
  `TraceSessionLauncher` lives in the same `com.openggf` package — rather than
  adding reflection in production code.
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
- Results/ring-bonus tally replay (recorded in the trace tail, uncompared for MVP).
- Per-player ring tracking in the engine (follow-up fix; see comparator caveat).
- All-7-stage complete-run traces; solo-Sonic / solo-Tails / human-P2 traces.
- S1 special stage generalization (seams intentionally left).
- All discrepancy fixes, including replacing the flat lag compensator for normal
  play with a state-derived lag model built from the recorded lag schedule —
  that is the follow-up plan, driven by this trace's divergence report.
