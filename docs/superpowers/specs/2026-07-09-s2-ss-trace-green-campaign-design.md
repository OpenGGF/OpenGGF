# S2 Special Stage Trace-Green Campaign — Design

Date: 2026-07-09
Status: Approved for planning
Predecessor: `2026-07-09-s2-special-stage-trace-design.md` (pipeline, merged to develop `72794884b`)

## Goal

Drive the S2 special-stage trace (`TestS2SpecialStageTraceReplay`, trace
`src/test/resources/traces/s2/special_stage/`) to **fully green with the ratchet
on**, thereby fixing the four reported issue areas:

1. Control locked/unlocked at the wrong times.
2. Tails CPU sidekick behavior (team now spawns — post two-key fix — but CPU
   input semantics are unverified against ROM).
3. Playback speed driven by an imprecise flat-0.35 lag compensator (normal play).
4. Timing of text/graphics (start banner, "GET n RINGS", rings-to-go HUD) and
   ring/object appearance.

Success criteria:

- Zero Tier-1 errors on the trace; `assertNoReleaseBlockingDivergences()` flipped
  on in `AbstractS2SpecialStageTraceReplayTest` so it stays green.
- Each Tier-2 warning group ratcheted to error as its area is fixed; by campaign
  end all Tier-2 groups are errors and green (requires the engine/snapshot
  additions below).
- Normal-play lag compensator replaced by a trace-derived deterministic lag
  model (statistical validation, defined below).
- `docs/TRACE_FRONTIER_LOG.md` updated at every frontier move per repo policy.

## Ground truth: the divergence chain (report of 2026-07-09)

`target/trace-reports/s2_special_stage_0_report.json` — 15,313 errors / 1,877
warnings over 3,249 stepped frames — reduces to an ordered root chain:

| First frame | Field | Root reading |
|---|---|---|
| f0–f22 | `*_present`, all player fields | ROM leaves player object slots empty for the first 23 frames of `GameMode_SpecialStage`; engine spawns both players immediately |
| f0 | `speed_factor` exp 0 act 12 | ROM sets `SS_New_Speed_Factor=$C0000` later in init; engine initializes 12 at construction |
| f4 | `track_anim_frame` +1 | track animation starts on the wrong phase relative to mode entry |
| f195 | `current_segment` 0 vs 1 | engine track playback runs ahead (accumulated phase shift) |
| f791 | `combined_rings` 1 vs 0 | first ring-collection timing (downstream of the shift) |
| f2683 | `sonic_hurt` | bomb-hit response mismatch (downstream) |
| f5073 | `finished` (146 frames early) | accumulated time shift reaches course end |

The intro/init misalignment is the dominant root; most of the error mass is its
cascade. Issues 1 and 4 are largely THIS; issues 2 and 3 sit behind it.

## Method (binding rules)

- **Fix loop:** per the `trace-replay-bug-fixing` skill — take the FIRST
  divergence, root-cause against the disassembly, fix the engine, rerun the
  trace, bank the win (commit + frontier-log entry), re-triage. Never chase a
  divergence downstream of an unfixed earlier root.
- **ROM-modeled fixes only:** every retiming fix must model the ROM mechanism
  (init routine order, object routine counters, `SpecialStage_Started`,
  banner/flyout object timers — cite `s2.asm` lines), never a magic constant
  sniffed from the trace. The trace is evidence, not source. No zone/route/
  frame carve-outs; the comparison-only invariant continues to hold.
- **No compensating errors:** a fix that improves the error count but models no
  ROM mechanism is rejected even if it "works".
- **Regeneration:** if a root needs trace data the recorder doesn't capture,
  extend the lua (bump `LUA_SCRIPT_VERSION` — it owes a bump already), re-record
  via the ps1 route, and re-commit artifacts; parser/columns changes follow the
  pipeline spec's contracts (48-column header is versioned via `ss_csv_version`).

## Campaign stages

### Stage 1 — Init/intro sequence alignment (the dominant root)

Port the ROM's special-stage startup order faithfully: what runs on each of the
first ~70 frames of `GameMode_SpecialStage` (PLC/art loads, palette, banner
object creation, player object creation at ~f23, `SS_New_Speed_Factor` set,
track animation start, `SS_player_anim_frame_timer` seeding, input enable).
Rework `Sonic2SpecialStageIntro`'s phase timings (DROP/WAIT/MESSAGE_FLYOUT/
GAMEPLAY) and `Sonic2SpecialStageManager.initialize()`/`update()` ordering to
match — driven by the disasm's `SpecialStage` game-mode routine, not by trace
frame numbers. The comparison snapshot's `present` semantics (player null until
spawned) must be honored by deferring player construction or gating their
visibility/participation exactly as ROM does.

Expected outcome: the f0–f22 cluster, `speed_factor` f0, `track_anim_frame` f4
and most position/segment/ring/finish cascade collapse. Rerun, re-triage,
record the new first root.

### Stage 2 — Iterative frontier fixes to Tier-1 green

Repeat the fix loop on whatever the report surfaces next. Anticipated (from the
chain + known code): track phase residuals, ring-collection windows
(`SS_Perfect_rings_left` / object touch), control-lock edges (jump/hurt input
windows; `intro.isInputEnabled()` gating vs ROM's control flags), Tails CPU
`SS_Ctrl_Record_Buf` semantics (shift order, delayed-tap index, P2-override
branch conditions, `s2.asm:70411-70449`), bomb/hurt response, and the finish
frame. Tier-1 green = flip the ratchet on (releases-blocking divergences fail
the test from then on).

### Stage 3 — Tier-2 ratchet enablers (engine/snapshot additions)

- **Per-player rings:** add per-player ring tracking to the engine mirroring the
  ROM's per-object `ss_rings_*` fields (`s2.asm:70771-70789`, summed for HUD
  `s2.asm:9938-9943`); route collection through the touching player; keep the
  combined total consistent. Then compare per-player rings as errors.
- **Snapshot extension:** add rings-to-go state, swap-positions flag (reconciled
  to a single ROM-faithful global instead of per-player copies), and hurt/slide/
  flip timers to `Sonic2SpecialStageComparisonState`; wire each into the
  comparator and ratchet to error when green.
- **`getPlayerAnimFrameTimer()`:** replace the constant with a real decrementing
  counter (ROM `SSRun_Animation_Timers`, `s2.asm:960-982`) and START comparing
  the recorded `player_anim_frame_timer` column (currently recorded-not-compared).
- Intro/message timing fields (`rings_togo_bcd`, message lifecycle aux events)
  ratchet to errors once Stage 1's retiming lands.

### Stage 4 — Normal-play lag model (issue 3, non-replay half)

Replace the flat `lagCompensation = 0.35` skip with a deterministic,
trace-derived state-keyed model:

- **Derivation (offline, one-off tool or test-tree analysis):** bucket the
  recorded per-frame lag flags by observable engine state (track segment type,
  `speed_factor`, drawing index phase, live object count) and extract per-bucket
  lag ratios and burst-length patterns from the 5,299-frame schedule.
- **Runtime:** a small `Sonic2SpecialStageLagModel` consulted once per frame in
  the same place the accumulator lives today; deterministic (seeded by frame
  counter/state, no RNG) so rewind/replay stay reproducible. Trace replay
  continues to force it off (trace pacing governs there).
- **Validation (defined):** for every (segment-type × speed-factor) bucket
  present in the trace, the model's lag ratio must be within ±5 percentage
  points of the recorded ratio, and overall ratio within ±2 points of 1971/5299
  (~37.2%); asserted by a JUnit test against the committed trace artifacts.
  Perceived-speed eyeball via the visual SS session (jar test mode) at the end.
- Keep the F1/F6/F7 lag overlay as a debug/tuning view over the new model.

### Stage 5 — Green gate & closeout

Ratchet fully on (Tier-1 + all ratcheted Tier-2), full-suite regression sweep,
frontier log final entry, CHANGELOG/README per policy on merge. Add the SS
trace test to the tracked keep-green set used by trace sweeps.

## Out of scope (follow-ups)

- Results-screen replay/timing (the recorded post-`stage_finished` tail stays
  uncompared).
- Additional traces (solo Sonic, solo Tails, human-P2 override, failed-stage,
  stages 2–7) — recommended immediately after green to lock the fixes broadly,
  using the existing recorder unchanged.
- S1 special-stage trace generalization.

## Risks

- **Cascade churn:** most measured error mass may collapse after Stage 1 — or
  reveal deeper roots currently masked. Mitigation: strict first-divergence
  ordering; no parallel fix streams on this single trace.
- **Init sequence depth:** ROM SS init interleaves PLC loading across frames;
  the engine loads synchronously. The fix models the *observable* sequencing
  (object creation, flags, timers), not the PLC scheduler itself — if a
  divergence turns out to require PLC-cycle emulation, stop and re-scope with
  the user rather than emulating the loader.
- **Lag-model overfit:** one trace = one route. The ±tolerance validation and
  bucket granularity guard against memorizing the schedule; broader-corpus
  recalibration is listed as follow-up work.

## Testing

- Oracle: the SS trace test after every fix (focused run) + determinism test.
- Each engine fix carries its own focused unit test where the mechanism is
  isolable (intro phase machine, ctrl-record buffer, lag-model buckets).
- Existing level-trace suites and SS package tests stay green throughout.
- Final: full-suite sweep + visual session eyeball.
