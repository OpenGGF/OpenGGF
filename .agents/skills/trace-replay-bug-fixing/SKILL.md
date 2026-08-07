---
name: trace-replay-bug-fixing
description: Use when investigating or fixing any *TraceReplay test failure across the engine.
---

# Trace Replay Bug Fixing

> Mirrored in both skill trees (Claude Code and other agent harnesses); when editing, update both copies.

Recorded BizHawk traces verify that the engine plays back ROM behaviour pixel-for-pixel given the same controller input. When a `*TraceReplay` test diverges, this skill describes how to diagnose, fix, and (when needed) regenerate traces — without taking shortcuts that mask engine bugs.

## Agent Workflow Tooling

Use these to get oriented on a divergence before you start editing engine code. The tool is comparison-only — it reads the report, it never hydrates engine state.

- **TraceTriageTool** — reads `target/trace-reports/<game>_<zone>_report.json` and prints a first-divergence brief (frame/field, ROM vs engine value, likely owning subsystem, disasm search terms). Run after a failing `*TraceReplay` test produces a report:
  `mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceTriageTool" "-Dexec.args=s2 mtz1"`
- **`docs/agent-workflow/runbooks/runbook-trace-divergence.md`** — step-by-step divergence runbook.
- **`docs/agent-workflow/documentation-obligation-checklist.md`** — commit trailers, changelog justification, and the `docs/status/trace-frontier-log.md` update obligation when a trace frontier moves.

## Core Mission Rules (apply to all trace work)

1. **No hacks or dirty fixes.** Every behaviour change must be backed by the disassembly for the relevant game. Cite ROM file and line numbers in commits and code comments.
2. **You may regenerate a trace** when the recorded data is genuinely insufficient for diagnosis (missing per-frame data, broken setup, recorder schema changed). Use the native harness (`tools/bizhawk-headless/run.sh`) for S1, S2, and S3K; use Lua only for hook-driven diagnostics the native recorder deliberately defers — see [Trace Regeneration](#trace-regeneration). The native harness is also the canonical fixture-publication authority, subject to the independent publication contract below. Regeneration is part of the loop — don't avoid it. **But** do not regenerate just to "make the test match"; regenerate to gain visibility.
3. **If the engine architecture is missing or fundamentally broken**, or game objects/functionality aren't yet implemented, **plan and delegate**. Use review agents and parallel subagent execution for large-scope work. Don't try to land everything in one pass.
4. **Cross-game parity is non-negotiable.** The engine supports three games (Sonic 1, Sonic 2, Sonic 3 & Knuckles). Before changing any shared/root code (physics, collision, sidekick AI, oscillation, rendering, audio, shared object base classes, shared object helpers, etc.), check the disassemblies for **all three games** to confirm whether the change is a universal correction or a per-game divergence. Per-game divergences must use the smallest accurate owner from `docs/architecture/per-game-rule-placement.md`: a typed `GameRules` record for game-wide shared runtime gates, or an existing provider/profile/registry/object hook for narrower behavior. **Never** branch on `if (gameId == GameId.S3K) ...`.
5. **No zone/route/frame carve-outs for trace fixes.** A trace failure in AIZ, CNZ, MGZ, or any other zone must be fixed by modelling the ROM state, object routine, physics profile, event flag, or data-driven object/profile condition that caused it. Do not add behaviour branches whose only predicate is a zone id/name, trace route, frame number, or "known failing trace" exception. "Use ROM-default behaviour except in AIZ" is still a zone-specific carve-out and is not acceptable. Zone/event/object providers may expose ROM state at the owning boundary, but shared physics/sidekick/object behaviour must consume semantic predicates such as object id/routine/control bits/status bits/frame-counter visibility, not "this zone".

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

## The Other Core Invariant — Any BK2, Not This BK2

**A fix must hold for a movie nobody has recorded yet. A green fixture proves the fixture.**

This is hard rule 3. It is the twin of the comparison-only invariant above: that one stops
the harness from feeding the engine answers, this one stops *you* from feeding the engine
answers. Both failure modes produce a green test over an engine that is still wrong.

A constant derived by **measuring the fixture's own rows**, rather than read out of the
disassembly, is a fitted model *even when every test passes*, and it will desync the first
different recording. Roughly twenty such fixes were reverted in one recent stretch; two of
them cost whole days before anyone noticed the test was green for the wrong reason.

### The test to apply before landing anything

Ask: *if someone recorded a new BK2 of this same route tomorrow, with different lag, a
different entry frame, or the player standing somewhere else — would this change still be
correct?* If the honest answer is "probably, because it happens to line up", it is fitted.

### Red flags

- A number whose provenance is "I measured frames N..M of this fixture and it was 26".
- A value that is **close to the ROM's but not equal**. That almost always means it is
  absorbing an error somewhere else. Chase the other error; do not keep the constant.
- A predicate that mentions a frame index, stage index, zone, route, fixture name, or
  "the known failing trace". *"ROM-default behaviour except in AIZ"* is still a carve-out.
- A tolerance, epsilon, or comparison exclusion added so a field stops failing.
- A local timer standing in for a global ROM clock. If the ROM gates on
  `Level_frame_counter` or `V_int_run_count`, a per-object counter that "also ticks every
  other frame" is right only for the recording that entered on the same parity.
- Reaching for the recorded row to decide *when* something happens. See the
  hardware-timing exception below for the one narrow, documented case.

### The procedure

1. **Measure the fixture to locate the problem.** This is legitimate and expected — it is
   how you find which frame and field to study. It is a starting point, never a landing
   place.
2. **Find the ROM routine that owns the behaviour**, and read what it actually does.
3. **Land a structural rule, not a number, wherever possible.** A rule read out of the
   disassembly generalises; a measured quantity does not.
4. **Cite the routine** — file and line — in the code comment and the commit message.
5. **Audit the blast radius.** Re-measure across the whole corpus, not just the target: how
   many other rows/objects/traces does the new rule change? If a classifier or predicate
   changed, count exactly which rows now fall on each side.
6. **State the audit in your report**, including "no constant was introduced" when that is
   the truth. A reviewer must be able to check step 3 without re-deriving your work.

### Worked examples — rules that generalise

Each of these closed real traces, and each is a *structural* fact about the ROM, which is
why it holds for any movie:

- *"`Bri_Main` allocates `subtype - 1` display-only child logs"* — from the `dbf` loop
  structure, not from counting slots in a fixture.
- *"`SpinC_Main_Spawner` re-uses the spawner's own slot as the group's first platform and
  calls `FindFreeObj` only for entries 2..N"* — from `movea.l a0,a1 / bra.s .makePlatform`.
- *"ROM `Obj08` never calls `MarkObjGone`"*, and *"`Pyl_Display` has no out-of-range test,
  `MarkObjGone`, or `DeleteObject`"* — so neither object can ever unload, whatever the
  camera does.
- *"`v_ssangle` is the last unconditional write in the special-stage tick"* — which makes
  "angle unchanged while velocities advanced" a sound torn-sample witness at every tear
  point, where the previous witness only caught tears before one particular store.
- *"`React_CollisionDetected`'s ring branch has no once-only latch; it re-tests
  `cmpi.w #90,flashtime(a0)` every overlapping frame"* — so the response must be
  continuous, not edge-triggered.

### Worked examples — fits that were caught

- **CNZ Point Pokey** spawned prizes on a local `prizeSpawnTimer++ >= 2`. The ROM gates on
  `btst #0,(Level_frame_counter+1).w`. The timer was green for that recording and wrong for
  any movie entering the cage on the other parity — a textbook this-BK2 construct.
- **S1 special-stage `ss_angle`** failed on one frame in two stages. Widening the torn-row
  classifier until they passed would have looked identical to a fix. What made the real fix
  legitimate: the new witness came from the ROM's write ordering, *and* the blast radius was
  audited across ~25k rows to confirm exactly two rows changed classification.
- **A press-edge baseline** was "fitted exactly over rows 1714/1715 and 1731-1736" of one
  fixture. The ROM answer was `Joypad_Read`'s `pressed = new & ~stored_held` plus the fact
  that `Vint_Lag` never reaches it — a rule, not a window.

### When it genuinely is not derivable

Sometimes the discriminator is sub-frame 68000 cycle position, or a clock the engine does
not model at all (for example a free-running `v_vblank_count` that never resets, where the
engine's counter is level-local). A native model **cannot** predict that from
frame-granularity state, and no amount of measuring will make it generalise.

In that case the answer is **not** a tuned number. Say so plainly, name the missing piece,
and either leave the trace red with a written diagnosis or use the documented per-movie
hardware-timing sidecar under hard rule 4. A `no-improvement` result with a real root cause
is worth more than a fitted green, and is treated as success.

### What to do when the engine diverges from ROM

- If the first divergent field starts with `queue.`, stop downstream triage.
  Compare physical membership/order, active preparation, remaining work, and
  queue-local lifecycle state against the disassembly. Version 1 reserves
  `service_observations` as an empty array because sub-frame service is not
  cross-observable at the shared end-frame boundary. The empty array is
  mandatory schema padding, not evidence that service did not run; infer
  service and retirement from membership, prepared, and remaining-work
  transitions. Queue timing can
  make later music, event, object, and physics symptoms appear early or late;
  do not “fix” those symptoms while the queue frontier is red.
- Per-frame queue events are enabled only by
  `aux_schema_extras: ["load_queue_state_per_frame"]`. They are exact,
  comparison-only evidence sampled at `END_OF_LOGICAL_FRAME`; legacy traces
  without the capability remain unchanged. Never use a recorded queue event to
  submit, complete, retire, or otherwise mutate an engine load job.
- Find the engine code path that should have produced the ROM-correct value but didn't. Fix the engine.
- If the engine has no equivalent path, port the ROM logic with disassembly citations and route any game-divergent behavior through the smallest accurate owner from `docs/architecture/per-game-rule-placement.md`.
- If the trace lacks the diagnostic data needed to pinpoint the bug, extend the recorder. New fields are comparison context, not write-back targets.

### Frame-0 bootstrap comparator

For v5 traces advertising `native_prelude_bootstrap`,
`TraceBinder.compareBootstrapFrame0(trace, EngineSnapshot)` runs once at the
start of each test and asserts engine state at frame 0 against the recorder's
`player_history_snapshot`, `cpu_state_snapshot`, and `object_state_snapshot`
events. Mismatches become `BootstrapDivergence` entries rendered ahead of
per-frame divergences. Eligibility is capability-based; recorder provenance
never selects behaviour.

### Engine title-card behaviour

`GameLoop` ticks `ObjectManager` + player physics every frame during the title-card phase, matching ROM `TitleCard_Main` for S1/S2/S3K. Player input is locked via the same path the ROM uses (`Sonic_ControlsLock` / `Ctrl_locked`). This means `Sonic_Pos_Record_Buf` fills naturally during the prelude — previously the engine froze object updates during the card, leaving the position-history ring empty at frame 0 and triggering sidekick AI divergences in the first ~300 frames of every level-select trace.

### Diagnostic reseeding policy

Do not commit trace-to-engine hydration switches or writeback binders. If a bootstrap divergence needs A/B isolation, use a local throwaway patch or a debugger to reseed state, then remove it before committing. The committed replay path must remain comparison-only: it may report pre-trace snapshots and compare bootstrap frame 0, but it must not copy recorded `player_history_snapshot`, `cpu_state_snapshot`, or `object_state_snapshot` data into engine runtime state.

#### The hardware-timing exception — what it does and does not cover

There is one narrow, sanctioned exception to comparison-only, and it is easy to
apply too broadly *and* easy to refuse too broadly. Both mistakes cost a round.

**Permitted.** Recorded hardware timing may drive a **delay** in the art-loading
pipelines of all three games:

| Game | Pipeline whose delay hardware timing may drive |
|---|---|
| S1 | PLC |
| S2 | DPLC |
| S3K | Kosinski module and direct-decompression queues |

"Drive a delay" means exactly that: defer or release *when* already-submitted,
production-created work becomes ready. A V-blank-count-derived latch that defers
one publication boundary is inside the exception, even though its input is a
recorded counter and its effect is on engine behaviour.

**Not permitted — and this is the whole of the rest.** The exception may not:

- carry gameplay values of any kind across the boundary,
- create work the engine did not submit, or fabricate readiness for work that
  does not exist,
- use physics or aux **comparison** data as the signal,
- seed, reseed or sync engine state (see the reseeding policy above),
- key on a frame index, zone, route, game name, or a known-failing trace.

The distinguishing question is *"does this only change WHEN real, engine-created
work becomes ready?"* If yes, it is in scope. If it supplies a value, conjures a
job, or decides *what* happens rather than *when*, it is out of scope regardless
of how well the ROM behaviour is cited.

`TestHardwareTimingAuthorityGuard` enforces parser/authority isolation and
forbids physics/aux/gameplay and reflective mutation paths — keep the exception
confined to the timing port and keep that guard green.

### Diagnostic hooks — investigation only, never sync drivers

The S3K Lua recorders carry ~61 `event.onmemoryexecute` / `onmemorywrite` registrations
each, gated behind `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS`. They were decisive for deep
frontier work. They are also **off in every committed fixture**, disabled during the Linux
move because the per-write cost was severe and because their enriched fields were not being
used by the sync checks.

**A hook must never be the thing that decides when a trace lines up.** Hook-derived
per-level sync points were used for AIZ and CNZ (S3) and were rejected as hydration in
another guise: the trace ends up telling the engine when to start, instead of the engine
reaching that state natively. **The sync point is the beginning of the level load.** If a
trace only lines up because a per-level hook says so, that is the same defect class as
copying a CSV column into a sprite — fix the engine's level-load path instead. Minimise
one-off per-level hooks used as drivers for making syncing work; they are the mechanism by
which "it passes" quietly stops meaning "it is correct".

For investigation, prefer a **one-off throwaway Lua script** over extending a production
recorder. The two S3K recorders are already 4,957 and 5,918 lines and sit against Lua 5.4's
200-locals-per-chunk cap; every permanent addition also widens the env-var surface the
native CLI must refuse. Same rule as the reseeding policy above: write it, learn from it,
remove it before committing.

**Re-enabling hooks in a fixture capture is a fixture-invalidating change.** The gates pin
hooks-off two ways — `S3KHookAbsenceTests` asserts zero aux lines for the deferred families,
*and* asserts that hook-enriched records keep their unpopulated shape (the 9 AIZ
`aiz_handoff_terrain_state` records must keep `sonic_floor_seen:false` /
`solid_vertical_seen:false`). A hooks-on regeneration fails those gates by design. Treat it
like any other fixture regeneration: user approval, categorise every delta, full frontier
re-measurement. Note also that hook output is exactly what has previously breached git's
file-size limits.

**Do not confuse a hook family with hook output.** Several families emit frame-polled and
appear in fixtures regardless — the AIZ fixture carries 401 `aiz_fire_transition`, 12
`terrain_wall_sensor` and 9 `aiz_handoff_terrain_state` records with hooks off. Those are
validation data; hooks only *enrich* them. Do not remove a family because it is
"hook-driven".

The catalogue of what each hook watched already exists — do not re-derive it.
`tools/bizhawk-headless/docs/s3k-profiles-and-hooks.md` §2 tabulates every family with its
hooked ROM addresses, the routine and disassembly line it sits on, its gating conditions and
frame windows, and the aux family it feeds; §2.4 records the native port's deferral verdict
and the exact trigger for revisiting it.

**The AIZ/CNZ precedent, and where its record lives.** This rule exists because it was
litigated once already, and the outcome is written down — read it before re-opening the
argument:

- `d5eb5d2ef` (2026-06-09, *"replace legacy AIZ trace bootstrap policy"*) retired
  seed-at-`gameplay_start` in favour of the frame-0 comparison-only policy, and removed the
  S3K/AIZ/act/checkpoint identity predicate in favour of generic capability metadata. The
  recorded `gameplay_start` checkpoint survived **only** to terminate the prefix phase
  classifier — never to seed state.
- `2b4b075be` (2026-07-02) realigned the AIZ probe to that policy: it deleted seven tests
  that existed solely to pin the superseded seed-at-anchor and hydrated-anchor semantics,
  retired the legacy diagnostic fixture with its 289 suppressed oscillator frames, and
  documented the two-row intro-handoff skew as parity-neutral (rows 1384-1427 are recorded
  fully idle).
- The 2026-07-23 structural work went further still: S3K stopped consuming the metadata
  capability at all and now recognises a pre-level prefix from the recorded `zone_act_state`
  mode transition, so it infers phase from no fixture name, start position, velocity,
  animation or oscillator value.

`docs/status/known-discrepancies.md` → *Legacy Pre-Level Intro Prefix Trace Bootstrap Contract*
holds the current boundary (S1/S2 fixture compatibility only), its rationale — *"the engine
must execute its own production lifecycle; trace rows and auxiliary events are
comparison-only evidence"* — and its removal condition. The direction of travel across all
three steps is the same: each one removed a trace-derived driver rather than adding one.

## Pipeline Overview

```
+------------------------+     +------------------------+     +------------------------+
| BK2 movie (Bizhawk)   | --> | Recorder (native C#    | --> | Trace files            |
| - P1 controller frames |     |   or Lua)              |     | - metadata.json        |
|                        |     | - reads RAM each frame |     | - physics.csv          |
|                        |     | - writes physics.csv   |     | - aux_state.jsonl      |
|                        |     | - writes aux_state.jsonl|    |                        |
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

### Recorders — two implementations, one output contract

A **native C#/Mono harness is replacing the Lua recorders**, gated by byte-identical
differential comparison against the committed fixtures. Both emit the same
`metadata.json` / `physics.csv` / `aux_state.jsonl` contract, so everything downstream of
the recorder is unchanged. Pick by game:

| Game | Current recorder | Notes |
|---|---|---|
| S1 standard + complete-run | **native** (`tools/bizhawk-headless/`) | Migrated and gated |
| S2 all modes + complete-run | **native** | Migrated and gated |
| S3K standard | **native** | Migrated and gated (AIZ end-to-end, CNZ, MGZ). Hook-driven aux families are deferred, and the CLI refuses every unmodeled `OGGF_*` recorder variable rather than diverging silently |
| S3K complete-run | **native** | `--trace-profile complete_run` / `--run-id`; emits the same strict v5 envelope as every other mode |

The entire Lua recorder fleet (S1, S2, S3K standard, S3K complete-run) now has a
byte-parity-gated native port. `s3k_trace_recorder.lua` /
`s3k_complete_run_recorder.lua` remain useful for the 14 hook-driven aux
families the native port defers (`OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1`) and as
optional cross-implementation evidence. They are not fixture-publication
authorities — see "Porting a recorder to the native harness" below.

**Native harness (`tools/bizhawk-headless/`)** — Linux/Mono, no display required, ~1240-2790
fps vs ~840 fps for Lua-on-Linux.

- `run.sh` — wrapper that builds if needed and execs the harness under Mono with
  `DISPLAY` unset. Args: `--rom`, `--movie`, `--output` (must **not** already exist),
  `--mode smoke|trace`, plus `--trace-profile`, `--gameplay-segment` (S2 only), `--run-id`
  (run mode; mutually exclusive with the other two), `--effective-movie-length` (run mode
  only). `--bk2-frame-offset` / `--max-frames` are smoke-mode only.
- `test.sh [--filter <substr>]` — the differential suite (~4 min; the complete-emeralds
  gate is most of it, so `--filter` when iterating). Skips cleanly when the BizHawk
  distribution or a ROM env var is absent.
- `common-env.sh` — resolves `BIZHAWK_HOME` (defaults to
  `docs/BizHawk-2.11-linux-x64`) and validates the required BizHawk DLLs. Needs Mono 6.12
  + xbuild. **C# 7.x only**, non-SDK csproj: every new `.cs` file must be hand-added to
  **both** `BizHawk.Headless.Gpgx.csproj` and `...Tests.csproj`. Tests are a
  dependency-free console runner (`tests/TestMain.cs` registry + `AssertEx`), not NUnit.
- Behaviour specs live in `tools/bizhawk-headless/docs/` (`s1-trace-recorder-behavior.md`,
  `s1-complete-run-behavior.md`, `s1-run-mode-behavior.md`, `s2-trace-recorder-behavior.md`,
  `s2-run-mode-behavior.md`). Read the spec for the mode you're touching.

**Lua recorders (`tools/bizhawk/`)** — optional corroboration for native recorder
changes, and still the only way to capture the 14 hook-driven aux families
(`OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1`) that the native S3K ports (standard and
complete-run) defer. Lua parity is not required to publish a canonical fixture.

- `<game>_trace_recorder.lua` — launched inside BizHawk-2.11 with `--lua <recorder>` and
  `--movie <bk2>`. Each frame reads RAM, classifies the frame phase, and emits one CSV row
  plus zero-or-more aux JSONL events.
- `run_bizhawk_lua.sh <lua> <bk2> <rom>` — **the Linux launcher; use this one here.** Needs
  the repo-local BizHawk build, hardware GL, and `DISPLAY=:0`, with `OGGF_TRACE_RUN_ID` /
  `OGGF_BK2_FRAME_COUNT` / `OGGF_BK2_BASENAME` in the environment. Lua `print()` never
  reaches stdout — judge success by output files. Run one EmuHawk at a time.
- `run_bizhawk_lua.bat`, `record_<game>_trace.bat` — Windows equivalents. Ignore them on
  Linux.
- `trace_output/` — scratch directory the recorder writes to (CWD-relative to the script).
  Treat all Lua output as scratch-only diagnostic/corroborative evidence. Never install
  Lua-produced bytes as a canonical fixture.

**Fixtures under `src/test/resources/traces/` are read-only ground truth during ordinary
differential work.** On an unexplained recorder mismatch, fix the recorder — never the
fixture or normalization. A deliberately reviewed recorder/schema change may replace a
fixture only through the native publication contract below, including frozen literal
evidence and explicit approval for the exact bytes.
`physics.csv` / `aux_state.jsonl` / `run_manifest.json` must be byte-identical with zero
normalization; only `metadata.json`'s `recording_date` and an exactly-pinned version-line
delta may differ. Run-mode published files are CRLF; plain mode and S1 complete-run are LF
(Lua-on-Linux writes LF — environmental).

#### Porting a recorder to the native harness

Treat ROM/disassembly semantics as the behavioral source of truth. Establish a
native recorder change with named semantic invariants, behavioral and unit tests,
and independent review. Existing committed fixture vectors and Lua parity are
useful cross-implementation evidence when available, but neither overrides the
ROM nor gates canonical publication.

Keep Lua runnable for optional corroboration and ad-hoc hook-driven debugging.
Frozen and unmaintained is fine; do not invest in Lua-side parity for its own
sake. The native harness is the preferred capture path and canonical
fixture-publication authority.

Lessons already paid for on the S1 and S2 ports:

- **Evaluate stop conditions POST-advance, in the Lua's `on_frame_end` source order.** This
  exact bug was found independently in both ports. Don't reintroduce it.
- **Pin version drift empirically.** When fixture and HEAD recorder versions differ,
  derive the permitted metadata delta from recorder history plus a real capture diff,
  and assert it exactly in the gate. Never widen normalization to make a diff pass.
- **Hook-driven aux families are the hard part.** Families like `rng_call_per_frame` and
  `sonic_record_pos_per_frame` use `event.onmemoryexecute`, which the native `GpgxHost` does
  not support yet. Determine which families actually appear in the gated fixtures; port
  those, and document deferral for env-gated-off ones rather than dropping them silently.
- **Never commit ROMs, BizHawk binaries, or capture outputs.**

Current schema ownership:
- `trace_schema: 5` owns metadata, row shapes, timing, and run manifests.
- `recorder` identifies the producer and `recorder_version` identifies its
  implementation. Both are opaque provenance and never select parsing/replay.
- `lua_script_version` is removed, not renamed. Diagnostic Lua emits
  `recorder: lua-bizhawk-diagnostic` and the same v5 data contract.
- `aux_schema_extras` lists optional semantic capabilities.

### Trace files (`src/test/resources/traces/<game>/<zone>/`)

- `metadata.json` — game, zone, act, BK2 frame offset, trace frame count,
  oscillation pre-advance, character set, opaque recorder provenance, ROM
  checksum, profile, `trace_schema: 5`, and `aux_schema_extras`.
- `physics.csv` — one row per recorded frame. **Every numeric column is hex-rendered**, not just the frame number: `rings`, `camera_x/y`, the frame/VBlank counters, positions, sub-pixels, speeds (two's complement), routine, status, animation and mapping ids. Only the boolean/enum columns (`player_present`, `player_air`, `player_rolling`, `player_ground_mode`) are plain decimal. The writer is `S1TraceCsvWriter.FormatRow` (`Hex4`/`Hex2` per field) and the reader parses radix 16 (`TraceFrame.java`:161). Reading `rings=0037` as 37 instead of 55 put a special-stage results card on the wrong branch of `SSR_RingBonus.finished` and invalidated a whole timing analysis — convert before comparing a column against a disassembly threshold.
- `aux_state.jsonl` — one JSON object per line. Standard event types: `zone_act_state`, `checkpoint`, `state_snapshot`, `mode_change`, `slot_dump`, `object_appeared`, `object_near`, `object_removed`. Plus opt-in events declared in `aux_schema_extras` (e.g. `cpu_state` per-frame for sidekick CPU state). The S1 complete-run recorder (`s1_complete_run_recorder.lua`) is at **v3.13**: beyond the standard set it carries `objoff_32/34/36/38` (maker/collapse/approach timers), `v_oscillate` (the osc array @`0xFFFE5E`), `lag_state` (`emu.islagged`/`lagcount`), and conveyor-specific `s1_obj64_state`/`tracked_obj`. Before claiming a counter/osc/lag/maker-timer frontier is gated, check whether the field is ALREADY captured here; if a needed field is missing, extend the recorder and regen (it is byte-identical physics + new aux — verify, then swap aux+metadata only).
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

#### Multi-segment run tests: two drivers, only one of them is the visual path

- `tests/trace/runs/VisualRunReplayHarness` drives a whole multi-segment run
  through the **production** visual-session owners — `TraceSessionLauncher`'s run
  branch, its `TraceRunFrameDriver` hooks and its structural row comparator —
  headlessly, entering at `finishRunLaunch` exactly as a windowed launch does
  once its master-title fade completes. Anything it reproduces is reproduced in
  production code. Call `VisualRunReplayHarness.replay(runDir, stopAfterSegment(n))`
  to pin a lane at the frontier it has actually cleared instead of driving into
  the next unexplored boundary, and `tearDown()` from `@AfterEach`. It converts
  two production behaviours back into test failures: the launcher's deliberate
  failure *containment* (a windowed session logs and returns the user to the
  picker rather than propagating) and its first-error *self-pause* (headlessly a
  stall). Its failure text carries a 12-step context window, a change timeline
  (mode / coordinator phase / segment / load generation), and — on the
  self-pause path — the dynamic-art gap ledger.
- `tests/trace/runs/AbstractRunChainTest` is **not** the visual path. It builds
  its own coordinator/driver loop beside the launcher and calls a different
  `completePostProduction` overload, so a visual-only defect (an unpublished
  dynamic-art row, a mode change on the wrong physical row, a transition that
  softlocks) can stay green there while a real session aborts on it. Reproduce
  suspected visual/boundary defects in the harness before believing a green
  chain test.

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

3a. For object, boss, badnik, hazard, platform, and sidekick-contact
    frontiers, mine existing aux data BEFORE writing new Lua:
      - `object_near`, `slot_dump`, `state_snapshot`, and per-frame object
        extras often already contain ROM slot, routine, position, collision,
        and object-off bytes.
      - Build a small timeline table over the suspected window:
        `frame | slot | id | routine | x/y | collision | key objoffs`.
      - Diff that table against engine diagnostics for the owning object and
        every relevant child. If the owner is globally one frame ahead/behind,
        fix cadence before local object math.

3b. Audit compensations before adding another one. Local offsets such as
    `bossY - 2`, manual angle nudges, child-index predicates, and per-object
    stale-input windows are suspect until the upstream ROM cadence is proven.
    Repeated same-length exceptions (especially 2-3 frame input windows) are a
    signal to hunt the shared ROM mechanic, not to add one more carve-out.

4. Find the matching ROM routine in the relevant disassembly:
     - docs/s1disasm/, docs/s2disasm/, docs/skdisasm/
     - Use the s1disasm-guide / s2disasm-guide / s3k-disasm-guide skills
       to navigate, plus the RomOffsetFinder tool for offset lookups.
   Read the ROM logic completely. Compare with the engine path.

5. Identify the divergence. Choose the fix:
     - Engine code path missing a step that ROM does:    add it.
     - Engine code path doing a step ROM doesn't:        remove it (carefully).
     - Engine code path with wrong constant/threshold:   fix the value.
     - Per-game divergence:                              choose the smallest accurate owner from `docs/architecture/per-game-rule-placement.md`.
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
     - Extend the native recorder with a new aux event type and behavioral/unit coverage.
     - Bump the recorder version; add an opt-in key to aux_schema_extras.
     - Extend Lua only when the field requires a deferred hook-driven family or useful
       corroboration.
     - Add a matching TraceEvent record + parser handler.
     - Wire the new data into DivergenceReport rendering or a probe class.
     - Regenerate with the native recorder. Publish fixture bytes only through the
       canonical publication contract and exact-byte approval.
     - DO NOT wire the new data into engine-state mutation in the test loop.

7. Implement the fix:
     - Disassembly-cited (file + line numbers).
     - Cross-check the other two games' disassemblies for shared code.
     - Gate divergences at the narrowest owning abstraction:
       per-object/per-class hook for object-family quirks, or
      a typed `GameRules` record,
      only for game-wide ROM/system behaviour.

8. Run the trace test plus cross-game traces:
     mvn test -Dtest='Test<Game1>Ghz1TraceReplay,Test<Game1>Mz1TraceReplay,Test<Game2>Ehz1TraceReplay,Test<Game3><Zone>TraceReplay' -DfailIfNoTests=false
   All previously-green traces must stay green; the targeted trace
   should advance its first error frame (or, ideally, become green).

9. Update `docs/status/trace-frontier-log.md` whenever a trace frontier moves,
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
      - Read the existing mirrored
        `.agents/skills/s{1,2,3k}-implement-object/rom-pitfalls.md` and
        `.claude/skills/s{1,2,3k}-implement-object/rom-pitfalls.md`
        for that game. Does the fix match an existing pattern? If yes,
        consider adding the fresh commit hash + a one-line example to
        the existing entry's "Originating commit" list.
      - If the bug is a NEW pattern not yet catalogued, append a new
        entry following the format in the pitfalls file. Update both
        mirrored copies in the same logical change.
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

For boss/object fights, make an aux-backed object timeline before new Lua or code
changes. Compare the owner and all child objects frame-by-frame against ROM:
slot, id, routine/subroutine, center position, collision byte/category, render
eligibility, key counters/objoffs, and parent-visible state. `object_near`
events often carry enough per-frame ROM object positions to prove whether the
engine is globally one update ahead/behind. If the whole object graph is phase
shifted, fix init/update cadence first; local hitboxes, sine math, or per-child
offsets are usually downstream symptoms.

Object cadence checklist:
- Does ROM routine 0/init consume the object's first execution frame and `rts`
  before real logic starts?
- Does child 0 reuse the maker/spawner's own slot (`movea.l a0,a1`) while later
  children are allocated separately, causing child 0 to start later or in a
  different slot order?
- Does the parent update before children in engine while ROM children execute in
  their SST slots, or vice versa?
- Does a touch pass merely set a flag/counter, while the object's own later
  animation or routine tail applies movement, velocity, collision, or defeat
  changes?
- Does `AnimateSprite` gate motion/collision on the displayed animation frame?
- Does ROM touch handling mutate the object's RAM (`collision_flags`,
  `collision_property`, routine, status bits) immediately, making same-frame
  later scans see a changed object?

Compensation red flags: manual phase nudges, `-2/+2` position sources, child
index predicates, route/frame gates, and one-off stale-input windows. If two or
more compensations interact, stop and prove the earliest owner cadence against
aux/disassembly before trying another fix. A compensation that prevents one
window from regressing can still be masking a missing ROM tail elsewhere.

For object-contact divergences, add or inspect fields that explain the contact edge: object id/subtype/routine, object centre position, piece index, player and sidekick standing bits, collision side, carried delta, release reason, and whether contact was persisted from the previous frame or freshly detected. These fields are diagnostic context only; they must not drive engine state.

Compare ROM positions to engine centre coordinates (`getCentreX()` / `getCentreY()`), not debug HUD `getX()` / `getY()` top-left values. A one-radius offset in object contact, kill-plane, or boss-trigger traces is usually a coordinate-semantics bug before it is a physics bug.

When the branch exposes standard object contracts, use them as diagnostic vocabulary rather than inventing new local flags. Map raw `obj_control` evidence to `ObjectControlState`; map "which player did this object inspect?" to `ObjectPlayerQuery` and `ObjectPlayerParticipationPolicy`; map delete/despawn/remembered-object behavior to `ObjectLifetimeOps`; and map ordinary solid/touch/lifecycle behavior to canonical `SolidRoutineProfile`, `TouchResponseProfile`, and `ObjectLifecycleProfile` compatibility wrappers. A trace fix should not change behavior just to adopt a profile: first prove the wrapper preserves existing decisions, then migrate the smallest object family. If a guard blocks the fix, ratchet its baseline with an explicit historical allowlist instead of weakening the rule.

Separate moving-platform timing from generic collision timing. If a trace first diverges on a rideable solid, compare the object routine transition, timer pre-decrement, platform motion, `SolidObject` call frame, standing-bit refresh, player carry, and walk-off/release helper in that order. Do not collapse them into one "platform collision" fix.

When a trace mismatch lands on trigonometric object physics, do not replace ROM math with host floating-point approximations. Check whether the ROM routine calls `CalcAngle`, `CalcSine`, or a game-specific lookup table, then use the engine's integer lookup helpers (for example `TrigLookupTable`) or add an equivalent integer path with disassembly cites. S2 CNZ map bumpers are a concrete case: `CNZBumpersReact_Angle` reflects the incoming `CalcAngle` result and multiplies the `CalcSine` components by `-$A00` (`docs/s2disasm/s2.asm:32334-32677`); a one-angle rounding difference changed the bounce velocity and moved the CNZ frontier.

For power-up timer divergences, identify both the ROM counter value and the phase where the ROM decrements it. S1/S2 speed shoes use a word `$4B0` timer decremented from display after movement, while S3K uses a byte `(20*60)/8` timer decremented only every eighth frame. If the engine timer runs in a different phase, gate the compensation through the smallest accurate owner from `docs/architecture/per-game-rule-placement.md` instead of changing a shared timer constant globally.

When comparing sidekick CPU gates, distinguish ROM's raw `object_control` byte tests from the engine's split flags (`objectControlled`, `objectControlAllowsCpu`, `objectControlSuppressesMovement`). S2 `TailsCPU_Spawning` uses `tst.b obj_control(a1)` and must block respawn for any nonzero object-control byte; S3K catch-up code has narrower bit-7-style gates in other paths. Keep S2's Tails respawn/flying timeout separate from normal despawn: `TailsCPU_CheckDespawn` writes the `$4000,0` marker, but `TailsCPU_Flying`'s 300-frame offscreen timeout writes `x_pos=0,y_pos=0`, `Tails_CPU_routine=2`, `obj_control=$81`, and `Status_InAir` (`docs/s2disasm/s2.asm:38795-38806,39043-39052`). However, S2 uses the same `Tails_respawn_counter` word across `TailsCPU_Flying` and `TailsCPU_CheckDespawn`; if Tails is offscreen during fly-in and lands before the 300-frame flying timeout, the accumulated count must carry into the NORMAL despawn check rather than restarting at zero.

Route-start traces need their native preludes accounted for: title-card delays, route-start bootstrap, object spawning windows, scroll/parallax pre-advance, oscillation phase, and any zone intro skips. Prefer recording or replaying the real prelude when possible; use frame-0 bootstrap only for state ROM would already have at the BK2 start.

S2 level-select route BK2s can contain multiple controllable gameplay segments: an EHZ debug/menu bootstrap, the selected zone act 1, and for long routes the selected zone act 2 after the inter-act transition. Do not splice those segments into one physics trace because BK2 input alignment would skip non-gameplay frames. Use `OGGF_TRACE_GAMEPLAY_SEGMENT` / `record_s2_level_select_traces.ps1` segment routes (for example `cnz2`) to record later acts as separate fixtures with their own `bk2_frame_offset`; `metadata.act` and `metadata.gameplay_segment` must describe that fixture's starting segment.

S2 Metropolis Act 3 is a ROM-zone special case: the raw zone id is `0x05` with act byte `0`, while the engine progression zone remains MTZ and trace metadata must report act 3. Keep raw ROM zone/act diagnostics intact; only the fixture metadata/route identity should normalize it to `mtz3`.

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

When you need new diagnostic data, regenerate the trace.

**S1 / S2 / S3K (every recorder, incl. S3K complete-run) — native harness (preferred).**
No display, no EmuHawk process to babysit, and it fails loudly instead of silently
writing nothing:

```bash
tools/bizhawk-headless/run.sh \
    --rom "$S1_ROM_PATH" \
    --movie src/test/resources/traces/<game>/<zone>/<movie>.bk2 \
    --output /tmp/regen-<zone> \
    --mode trace \
    --trace-profile <profile>
```

`--output` must not already exist. Use `--run-id <id>` instead of `--trace-profile` for
run-mode/complete-run captures, and add `--gameplay-segment <n>` for S2 segment captures.
ROM paths come from `S1_ROM_PATH` / `S2_ROM_PATH` / `S3K_ROM_PATH`, following the
SKIP-when-absent convention.

For S3K STANDARD the profile is `aiz_end_to_end`, `level_gated_reset_aware`, or
`gameplay_unlock` (passed as `--trace-profile`, not the Lua's `OGGF_S3K_TRACE_PROFILE`
env var). For S3K COMPLETE-RUN, pass `--trace-profile complete_run` for a per-zone-segment
pass with no run_id, or `--run-id <id>` for run mode (bonus/special-stage detour segments
plus `run_manifest.json`) — these are a separate recorder/CLI branch from the STANDARD
profiles above, selected the same way as S1's complete-run recorder. Either S3K branch's
CLI **refuses** to run if any unmodeled `OGGF_*` recorder variable is set — hook-arming,
`*_RANGE` window overrides, or `OGGF_TRACE_STOP_FRAME` / `OGGF_BK2_FRAME_COUNT` — so clear
them from your shell rather than working around the error, since honoring them silently
would produce non-canonical output. The two S3K branches read different `OGGF_*` surfaces
(the STANDARD recorder honors `OGGF_S3K_TRACE_PROFILE`; the complete-run recorder never
reads it because it hard-pins its own profile), so a variable refused on one branch is not
necessarily refused on the other — check `tools/bizhawk/README.md`'s per-branch table
before assuming a refusal carries over.

**S3K diagnostics via Lua** — use scratch-only for the 14 hook-driven aux families both
native S3K ports defer (`OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1`). No longer needed for
`runs/s3-knux-multibonus-ss/`: that set was a 2026-07-19 Windows capture from a Lua build
three versions behind and could not be reproduced by any current recorder, so it was
regenerated at 6.32 and the native gate now asserts it byte-for-byte. Lua captures do work
on Linux; the old "Windows only" README note
is stale. Clear the scratch dir first, since the recorder appends into it (swap in
`s3k_complete_run_recorder.lua` for complete-run captures):

```bash
rm -rf tools/bizhawk/trace_output
OGGF_S3K_TRACE_PROFILE=<profile> DISPLAY=:0 \
    tools/bizhawk/run_bizhawk_lua.sh \
        tools/bizhawk/s3k_trace_recorder.lua \
        src/test/resources/traces/s3k/<zone>/<movie>.bk2 \
        "$S3K_ROM_PATH"
```

Output lands in `tools/bizhawk/trace_output/` and stays there (or in another scratch
directory) as diagnostic/corroborative evidence. Never copy Lua output into
`src/test/resources/traces/`. If a canonical fixture needs a hook-driven or legacy
capability the native harness lacks, implement and independently review that native
capability first, or obtain an explicit policy redesign before publication. Do not
substitute Lua-produced bytes.

**Before regenerating, confirm the capture mode** from semantic metadata such
as `game`, `trace_profile`, `trace_type`, and `run_id`. `recorder` and
`recorder_version` document provenance only; do not infer a mode from them.

Profiles are declared inside the lua via `is_*_profile()` predicates — check the recorder for the available list. Common ones: gameplay-unlock starts at controls-active, level-gated-reset-aware starts at gameplay and discards on soft-reset, end-to-end starts at BK2 frame 0.

### Canonical fixture publication contract

Use the native harness for canonical fixture publication. Keep correctness
independent of the bytes being proposed:

1. Establish recorder correctness from named ROM/disassembly semantics,
   behavioral and unit tests, and independent review. Use existing fixture
   vectors or Lua parity as optional corroboration.
2. Capture into scratch with the unchanged reviewed native implementation.
3. Freeze literal SHA-256 digests, byte lengths, metadata versions, segment
   inventories, row/event counts, ordering, ranges, and a named cause for every
   byte-level delta. Never derive passing expectations dynamically from the same
   capture invocation.
4. Obtain explicit user approval for those exact bytes and reported deltas.
   Install the native output byte-for-byte with no hand edits.
5. Re-run native gates plus fixture-load, schema, compression, and reference
   guards, then measure and record replay-frontier movement.

Until Step 4, committed fixtures remain read-only ground truth. A pre-publication
gate failure means the recorder or its proposed contract is wrong; it never
authorizes weakening a comparison or silently replacing a fixture.

## Recorder Extension Recipe

When a divergence can't be pinpointed without more ROM-side state:

1. **Recorder side.** Emit a JSONL line with a new `event` type from the per-frame entry,
   reading the RAM block of interest. Bump the recorder version and add an opt-in key to
   `aux_schema_extras` (e.g. `"<feature>_per_frame"`). Every recorder now has a **native**
   port (S1, S2, S3K standard, S3K complete-run) — extend the relevant `*AuxEventEngine`
   and its writer, with a test in `tools/bizhawk-headless/tests/`. On **Lua**, add a helper
   function (e.g. `write_<feature>_per_frame()`) and bump `LUA_SCRIPT_VERSION` only for
   scratch diagnostics or optional corroboration. A canonical fixture field requires a
   native implementation backed by ROM/disassembly semantics, native behavioral/unit
   coverage, and independent review.
   If a focused frontier only needs a few extra fields on an existing generic diagnostic such as `state_snapshot`, add the fields there and force snapshots for a narrow frame window instead of creating a new event type. Typical S1/S2 movement-input questions need both BK2/CSV input and ROM-side `Ctrl_1_Held_Logical` plus `move_lock`, because `Sonic_Move` consumes the logical RAM byte after `ReadJoypads` runs from V-int (`docs/s2disasm/s2.asm:701,1361-1387,36253-36260`).
2. **Java parser.** Add a new sealed-record type to `TraceEvent` (e.g. `TraceEvent.<Feature>State`). Parse the new JSON event in `TraceEvent.parseJsonLine`. Add `TraceMetadata.hasPerFrame<Feature>()` and `TraceData.<feature>StateForFrame(frame)`. V5 is strict; an optional event remains absent unless its semantic capability is advertised.
3. **Diagnostic use.** Wire the new data into `DivergenceReport.getContextWindow` rendering, or into a dedicated probe class for targeted bug investigation. **Do not** wire it into engine state mutation in the per-frame test loop.
4. **Regenerate with the native recorder.** If the new field belongs in a canonical
   fixture, complete the publication contract and exact-byte approval, then commit the
   fixture separately from the recorder/schema change.

## Recorder → Regen → Decode Loop — the primary engine for deep frontiers

The single highest-leverage method for frontiers labelled "RAM-gated" / "BizHawk-gated": **extend the recorder to log the exact gated value, regen headless locally, decode against ground truth.** This loop cracked the deepest frontiers in practice (the camera-pipeline reorder, the S3K AIZ fire-transition fix, GHZ3 red→green). It is RUNNABLE in this environment — you do not have to defer it to the user.

- **Run the recorder headless yourself.** For S1/S2 and S3K (all four recorder
  branches, including S3K complete-run), use the native harness with `--run-id` or
  `--trace-profile complete_run` — it needs no display and self-terminates
  (measured: ~6 min / ~235 MB peak RSS / 2.84 GB scratch for one untruncated S3K
  complete-run pass over the 466,334-row canonical movie; ~3.5 min for a full S1 run;
  the S2 complete-emeralds run is 259,590 frames ≈ 3.5 min native, ~1.5 GB peak RSS,
  375 MB output). Use Lua + EmuHawk only for scratch-only hook diagnostics or optional
  legacy/non-Linux corroboration. Lua output cannot become a canonical fixture. If native
  lacks a capability required by a canonical fixture, implement/review it natively or
  explicitly redesign the policy before publication. The Windows diagnostic equivalent is:
  ```
  EmuHawk.exe --chromeless --lua=tools/bizhawk/<game>_complete_run_recorder.lua \
      --movie=<the complete-run bk2> "Sonic The Hedgehog (W) (REV01) [!].gen"
  ```
  Output lands per-zone in `tools/bizhawk/trace_output/<zone>/` (uncompressed) and remains
  scratch-only. The full ROM name (spaces/parens/`[!]`) works as the trailing positional
  ROM arg to the recorder — the "spaces break it" trap is specific to the ad-hoc
  diag-capture path below, not the recorder. **Check which recorder made the target trace**
  via semantic profile/type metadata when comparing predecessor evidence.
- **The regen CORRECTS wrong "gated" labels — distrust them.** Real ground truth disproved root after root: "needs BizHawk v_objstate" (LZ2) was actually a ring/object placement-pass separation; "needs BizHawk x_sub" (SBZ2) was a no-hardware conveyor subpixel-discard → a WIN; "boss 1px behind" (GHZ3) was a byte-identical boss with a 1-frame defeat-routine slip; a guessed `v_limitbtm2 ~0x2E8` (MZ1) was 0x02EA with a different (camera-ORDER) root. **Before accepting a "RAM-gated" verdict, regen the data that would prove it.** Equally, re-attack any frontier decoded BEFORE a pattern you have since learned (PlatformObject landing-flags, object-push/self-motion subpixel, the bclr-release pattern) — the old decode was blind to it.
- **Validate recorder lua with a real compile, not balance-checking.** `pip install lupa`, then:
  ```
  python -c "import lupa; lupa.LuaRuntime().compile(open('tools/bizhawk/<recorder>.lua',encoding='utf-8',errors='replace').read())"
  ```
  Brace/paren/quote balance MISSES real errors — notably Lua's **200-locals-per-main-chunk limit** (top-level `local`s past 200 fail to load; EmuHawk runs, writes no `trace_output`, and looks like a silent core-init failure). Fix by making new constants global or keeping the main chunk ≤200 locals. Always lupa-compile before launching a regen.
- **Map scratch output by `bk2_frame_offset`, NOT by directory name.** Recorder output dirs
  use RAM-detected zone/act names that can differ from repo fixture names (for example,
  `sbz3` offset 189578 maps to `fz_completerun`; `lz4` offset 181004 maps to
  `sbz3_completerun`). Build an offset map before local comparison. Never stage a scratch
  overlay. A canonical installation uses only approved native output through the
  publication contract.
- **Verify the frontier reproduces, and do NOT commit aux bloat.** For a temporary local
  native diagnostic overlay, the first-error frame must remain unchanged when only aux
  context changed. Revert the overlay after decoding. Commit regenerated fixture bytes only
  through the native publication contract; otherwise prefer an engine-only fix against the
  lean committed trace.
- **Parallel regen/decode agents need worktree isolation.** Two non-isolated agents collided in a shared worktree (one branch overwrote the other). Use `isolation: worktree` (or run serially).

## Shared-resolver ordering — check the engine's pipeline ORDER against ROM

Camera/boundary/event-timing frontiers are often not a value bug but an **ordering** bug. ROM `DeformLayers` runs `ScrollHoriz`/`ScrollVertical` (camera move + clamp) BEFORE `DynamicLevelEvents` (zone event handler + boundary easing); the engine had it inverted (events+easing before the camera move), applying the airborne +8 boundary accel one frame early and feeding zone handlers a stale pre-scroll camera X. Reordering to ROM order fixed three S1 frontiers at once. Two lessons: (1) when a divergence is camera_x/camera_y/boundary/event-driven and the per-value math checks out, diff the engine's per-frame *pipeline order* against the ROM main loop; (2) a structural reorder can EXPOSE a pre-existing bug the wrong order was accidentally masking ("two wrongs made a right" — e.g. the S3K AIZ fire-transition `0x140` reset only "worked" because the inverted order applied a maxX release same-frame). Budget for the exposed bug, and gate the fix on the full cross-game sweep since the pipeline is shared.

## BizHawk Live Diagnostic Capture (ad-hoc register/RAM dumps)

Separate from the recorder (which produces full trace files), you often need a **one-off lua** that dumps ROM registers/RAM at a few specific frames to compare against the engine — e.g. the ROM value of a player/object field at the exact divergence frame. Three hard-won rules make this fast and non-destructive.

**Use the canonical probe contract — do NOT hand-roll lifecycle.** For new
diagnostics, copy `tools/bizhawk/probes/example_stage_probe.lua` and provide
only its semantic stage predicate and declarative hook table. The older
`tools/bizhawk/diag_template_fast.lua` remains the reference for grandfathered
diagnostics outside the guarded probe directory. Run probes through the
reusable launcher:

New ad-hoc probes belong under `tools/bizhawk/probes/` and must use
`probe_runtime.lua`'s declarative stage-and-hooks contract. The shared runtime
owns fast-headless setup, stage-before-hook registration, output teardown, hook
removal, and self-exit. Existing diagnostics outside that directory and the
production recorder/library fleet are intentionally grandfathered.
The launcher supplies the canonical runtime by absolute
`OGGF_BIZHAWK_PROBE_RUNTIME` path, including for probes in nested directories.
Probe callbacks are strictly read/log-only: a `kind = "write"` hook
observes a write and never authorizes emulated-memory, input, register, or
savestate mutation.

```bash
OGGF_START=<firstFrame> OGGF_STOP=<lastFrame> OGGF_OUT=/tmp/<name>.txt DISPLAY=:0 \
    tools/bizhawk/run_bizhawk_lua.sh tools/bizhawk/<your_copy>.lua <bk2> "$ROM_PATH"
```

On Windows the equivalent is `run_bizhawk_lua.bat` with `set OGGF_*` variables.

BizHawk frame for trace frame `F` = `bk2_frame_offset` (from `metadata.json`) + `F`.

**Before launching any ad-hoc Lua, perform this mandatory probe review:**

- The executable fast-headless block from `diag_template_fast.lua` appears
  before the main loop: unlimited framerate, 6400% speed, invisible emulation,
  and sound disabled.
- The script flushes/closes output and calls `client.exit()` on both success and
  movie-finished paths.
- Expensive execution/write hooks are **not registered at script load** when
  the target is a later stage. Poll only the minimum cheap stage state
  (`Game_Mode` plus zone/act or the equivalent semantic selector), register
  hooks when the target stage/window is entered, and unregister them as soon
  as capture completes. Filtering inside an always-registered callback does
  not avoid BizHawk's Lua/C# callback cost.
- The hook gate identifies ROM state, never a trace name alone. Frame windows
  may further narrow an already stage-gated diagnostic capture.

Review agents must reject a probe or oracle capture whose script or capture
report does not show these four properties.

**ROM arg: discover the actual root-level `.gen` file.** Search the repository root, select the appropriate ROM using its filename and hash, and pass its quoted absolute path to EmuHawk. Do not assume an alias or rename, copy, delete, or symlink a ROM to fit an example command. Filenames containing spaces, parentheses, or `[!]` must be quoted correctly; otherwise EmuHawk can launch with **no ROM** and hang (~316 MB resident, never writing output while `emu.framecount()` stays 0). This looks like the timeout case below but is distinct: here EmuHawk never advances a frame; there it advances but is killed mid-seek. (The trace-replay Maven tests are unaffected — this only bites the EmuHawk invocation.)

**1. Fast headless is the reusable launcher plus Lua toggles, not the `--chromeless` flag.** Run `tools/bizhawk/run_bizhawk_lua.sh` (`.bat` on Windows) so EmuHawk starts with the generated no-audio diagnostic config and a generated wrapper that runs the fast-headless calls before your diagnostic. The launcher also verifies the copied diagnostic still has executable fast-headless calls before its main loop, so commented-out template text does not pass the guard. Keep these Lua toggles at the top, before the loop:

```lua
emu.limitframerate(false)        -- remove the 60fps cap
client.speedmode(6400)           -- 6400% speed
client.invisibleemulation(true)  -- SKIP rendering: ~100x faster AND bounds memory
if client.SetSoundOn then pcall(client.SetSoundOn, false) end
```

`--chromeless` only hides window chrome. Without these calls a long seek (e.g. to BizHawk frame ~190000) runs at real-time (~50 min) while EmuHawk renders, piling up to multiple GB. `invisibleemulation(true)` is the memory fix (captures drop from ~3.4 GB to ~475 MB). The generated config and `SetSoundOn(false)` keep probes silent even when BizHawk's remembered config has audio enabled. Set `BIZHAWK_ALLOW_SLOW_LUA=1` only when deliberately running a visible/interactive diagnostic.

**2. The script MUST self-exit, or EmuHawk lingers as a multi-GB zombie.** End the capture window with `client.exit()` (flush/close the outfile first). Both of these LEAK the process: a `while true do emu.frameadvance() end` loop with no exit, and a `...; client.pause()` tail. The robust pattern (what the production recorder uses) is: detect `movie.mode() == "FINISHED"` (or `emu.framecount() > STOP`) → flush → `client.exit()`. Always check for a stray EmuHawk before each run (`pgrep -fi emuhawk` on Linux, `tasklist | grep -i emuhawk` on Windows) and kill any leftover after — `client.exit()` is not 100% reliable in every BizHawk build. The native harness has no equivalent failure mode; prefer it where the game is migrated.

**3. NLua silently dies on heavy per-frame reads at turbo.** A diag doing more than ~12–16 `mainmemory.read_*` calls/frame while `speedmode(6400)` is active can make EmuHawk exit at "start" (core loads, no frames, exit code 0, no lua error). Workarounds, in order: (a) keep 6400% for the long SEEK, then `client.speedmode(100)` for ONLY the small capture window (a few frames at normal speed costs nothing); (b) buffer into an in-memory table and write once at exit, never `io.open` per frame; (c) split a multi-slot object scan across runs (e.g. slots 0x11–0x16, then 0x17–0x1C). **Prefer the recorder** for full-movie / object-position needs — regenerating with the recorder lua (which already emits `object_appeared`/`object_near` aux and exits robustly) beats a hand-rolled multi-object live capture.

**The "writes 'start' then exits code 0, no frames" trap is almost always a SHORT TIMEOUT, not a crash.** Seeking the BK2 from frame 0 to a far capture frame (e.g. ~190000) takes SEVERAL MINUTES even at `speedmode(6400)` + `invisibleemulation`, so a bash `timeout 180-200` SIGTERMs EmuHawk mid-seek before it reaches your window, leaving only the file's initial "start" write — which looks exactly like a core-init crash but isn't. Give EmuHawk a GENEROUS timeout (600s+ for windows far into the movie), or none. Only if EmuHawk exits in *seconds* (gpgx core loads, then quits immediately) is it a genuine core-init abort, which usually means the `docs/BizHawk-2.11-win-x64/` install isn't present in that worktree (some worktrees symlink only `docs/*disasm`) — run from one that has it.

**Engine-side companion:** when adding `System.err` debug to the *engine* during trace work, note MSE-relaxed SWALLOWS `System.err` — write to a file (`Files.writeString` to a relative/Windows path; `/tmp` throws on the Windows JVM and the catch hides it) to see your output.

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
   modern recorders read `movie.getinput()` directly via the
   `bk2_input_mask` helper, so the CSV column matches BK2 by construction.
   The version floor at which each game's recorder made this switch evolves
   over time — check the recorder script's own header comment for the
   current per-game floor rather than trusting a hardcoded version here.

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

### Engine-side standing/ride diagnostic

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

### Slot-occupancy divergence (`obj_sNN_slot` / `eng-expected-onObj sNN missing`)

When the first error is a slot field (`obj_sNN_slot exp 0xNN act 0xMM`) or the
context shows `eng-expected-onObj sNN missing` (the player rides/expects an
object in a slot the engine filled with something else), the engine's dynamic
object RAM (SST) occupancy has drifted from ROM. Diagnose, don't assume RAM:

- **Slot_dump-comparison method.** Newer-format traces (post-old-lua-3.2) carry
  a per-frame `slot_dump` aux event (ROM slot -> obID map; e.g. LZ2 has ~469).
  Diff the engine's **live** slot map (`ObjectManager` slot occupancy) against the
  aux `slot_dump` frame-by-frame to pin the divergence to an exact slot / frame /
  obID, then trace WHY that slot diverged (which object spawned/unloaded
  differently freed or claimed it). **Use the PRODUCTION bootstrap** (the real
  trace-replay path / `applyBootstrap`, correct `zone()`/`act()` overrides) for
  this probe — a bare `SharedLevel` + `HeadlessTestFixture` that skips
  `applyBootstrap` produces a wrong-harness artifact (frame-0 slot mismatch that
  isn't real). **Old lua-3.2 traces have no `slot_dump` aux** (only
  `physics.csv` + `metadata.json`) — they must be regenerated before this method
  applies; without the aux you cannot diff ROM-side occupancy.

- **Full-OST first-divergence hunt (the structural lever for deep slot-cadence
  clusters).** When several reds share "the engine ends up with object X in the
  wrong slot N frames later" (S1 MZ2/MZ3/SBZ2 were all one root), do NOT keep
  decoding the *symptom* frame. Instead diff the **entire** OST occupancy
  engine-vs-ROM from level start and find the FIRST frame `F0` any slot's
  occupant diverges — that is the real bug; everything after is permutation.
  - **Diff the authoritative `SlotAllocator` bitset (active + reserved), NOT
    `getActiveObjects()`.** Bare slot *reservations* (e.g. ChainedStomper /
    multi-piece children allocated but not yet constructed) are invisible to
    `getActiveObjects()` but DO occupy the allocator — diffing the visible-object
    list produces false "missing object" pins (this mis-led two MZ3 decodes into
    chasing already-implemented objects). Instrument `ObjectManager`/`SlotAllocator`
    for a per-frame `slot -> objId` dump of all 96 slots (temporary; revert).
  - **Lag-frame-filter the ROM `slot_dump`.** A `slot_dump` whose `vfc` equals the
    NEXT frame's is a mid-`ObjPosLoad` VBlank sample (the load is still running) —
    skip it or it shows false transient divergences.
  - **`FindFreeObj` is lowest-free, so identical occupancy ⇒ identical slot pick.**
    Therefore a slot divergence is ALWAYS upstream spawn/free **timing**, never the
    allocator itself: find the object that spawned or freed one frame off, not "why
    did FindFreeObj pick differently". The engine packing slots *contiguously*
    while ROM carries *gaps from freed objects* is the classic tell — ROM freed a
    slot (a placed object scrolled off, an object self-deleted) that the engine
    still holds, or vice-versa.

- **Triage reframe: "slot-cadence" is OFTEN a single tractable object bug, not
  genuine RAM.** Before declaring a slot divergence RAM-gated, trace the
  responsible object-lifetime event. Classify:
  - **TRACTABLE** (fixable object-local, disasm-cited): an object deleted on the
    wrong frame (delete-check run every frame vs only its ROM routines — see
    s1-implement-object P9/S2 P50), a wrong child COUNT or array-vs-real-slot
    spawn (`dbf`+1 / FindFreeObj per piece — P8/P21/S2 P46), a wrong off-screen
    delete bound (raw `isOnScreenX` vs ROM render box — P10/S2 P52), an
    incomplete collapse/release drop, a dormant/consumed object still collidable
    (col_none — P7/P11/S2 P51/S3K P23), or a wrong allocation function
    (`FindFreeObj` lowest-free per ring vs `FindNextFreeObj`/`allocateSlotAfter`
    chaining — the S1 lost-ring scatter; `25, 37 Rings.asm:251` uses `FindFreeObj`
    each iteration). Newer tractable patterns proven this way:
    - **Maker reuses its OWN slot for child #0** (`movea.l a0,a1` keeps the maker's
      routine 0 so the first child moves NEXT frame): LZ Conveyor `LCon_Main`
      loc_12460, Lava Geyser maker. Engine spawning all children as fresh
      `FindFreeObj` made the ridden platform out-rank the maker → moved 1f early.
      Fix: `detachSlotForTransfer` + force child #0 into the maker slot.
    - **In-place object→object transfer** (`move.b #id_X,obID(a0)` mutates the
      object in its slot — e.g. Walking Bomb → Explosion): use
      `detachSlotForTransfer` + `addReplacementAtTransferredSlot`, NOT a lowest-free
      spawn of the replacement.
    - **`remember` destruction only for respawn-tracked entries.** ROM persists an
      object's destruction across re-entry ONLY via the respawn table (`obRespawnNo
      != 0`, set by `OPL_MakeItem` when the layout id-byte remember bit is set);
      `RememberState` deletes a non-tracked object with NO bit set, so the
      ObjPosLoad cursor RE-CREATES a fresh copy on the next crossing (incl. during
      a leftward `OPL_MovedLeft` backtrack). Engine `markRemembered` must be a
      no-op for non-respawn-tracked spawns or below-screen objects never reappear
      during backtrack (S1 MZ3 SmashBlocks; `sub RememberState.asm:16-21`).
    - **Off-screen despawn via the ROM `out_of_range` macro, not `isOnScreenX`.**
      Objects whose routine tail is `bra RememberState` despawn on the chunk-aligned
      X-only `out_of_range` (Macros.asm:273-289 → engine `isInRange()`), not a
      pixel-margin on-screen test (SpinPlatform, Walking Bomb).
    - **Multi-piece assemblies delete EN MASSE keyed on the parent**, not per-piece:
      Swinging-Platform chain links run `Swing_Display` (no `out_of_range`) and are
      all deleted by the parent's `Swing_ChkDel out_of_range ...,swing_origX`. A
      link self-despawning on its own swung-out X frees a slot early (S1 MZ3 f9917;
      fix: child `getOutOfRangeReferenceX()` returns the parent pivot).
  - **RAM-gated** (bounce with the exact first-divergent slot/frame/obID + the
    `v_objstate`/remember-bit index): subpixel/position-accumulation, the
    `v_objstate` remember-bit byte-array, or coupled multi-object spawn cadence
    with no single tractable lifetime event.
  Trace the responsible lifetime event (the upstream object whose spawn/unload
  frame differs) before concluding gated — the SLOT being wrong does not mean the
  ridden object's own position/motion is wrong (verify the solid is faithful and
  only its SLOT differs).

- **Re-verify on CLEAN develop, especially `TestRewindCoverageGuard`.** Local
  worktree baselines (`coverage-baseline.txt`, frontier counts) go stale and can
  MASK a new coverage gap a fix introduces. After any object-presence change,
  `git reset --hard origin/develop`-equivalent re-verify and run
  `TestRewindCoverageGuard`; a newly spawned real child (P8/P21) needs a
  baseline entry or the guard fails.

- **Rewind baseline-entry convention for parent-recreated render-only children.**
  When a multi-piece fix spawns render-only children whose recreate path is
  parent-driven (the parent re-creates them on rewind), baseline-entry the
  child's `#recreate` + `#finalScalar` keys in `coverage-baseline.txt` (precedent:
  `SpikedBallChain$ChainChild`, `CollapsingLedge$Fragment`), rather than adding a
  bespoke per-child recreate path.

### Touch / hurt timing is slot-order- and render-flag-gated

When the first error is a hurt/bounce velocity (`x_speed`/`y_speed` jump to a
knockback value) one frame early or late, the cause is usually WHEN the damaging
object becomes touch-eligible, not its position:

- **ROM `ReactToItem` skips any object whose `obRender` bit 7 is clear**
  (`tst.b obRender(a1) / bpl .next`, `_incObj/Sonic ReactToItem.asm:50-51`). Bit 7 is set by
  `DisplaySprite` during the object's OWN `ExecuteObjects` pass. So a child spawned
  mid-loop into a slot at or BELOW the spawner's slot does not run/display until
  the next frame → is touch-INELIGIBLE for one extra frame (S1 MZ3 f14132: a lava
  ball dropped into a lower slot hurt the player 1f early). The engine must defer
  touch eligibility of a lower-or-equal-slot mid-loop child until after its first
  `update()`.
- **Tall objects gate touch on their own `obHeight`, not a fixed band.** ROM
  `BuildSprites` computes the off-screen render-flag bit from `obHeight` when
  `obRender` bit 4 is set; `ReactToItem` honors it. A fixed ~32px Y touch band
  misses a tall hazard (256px lavafall column, `obHeight=$80`) whose anchor is
  above the camera top — model the object's real render height (S1 MZ2 column).
- **Hit detection and hit reaction may be different phases.** Some ROM touch
  paths set an invulnerability/collision/defeat flag, but the boss/object applies
  the reaction from its own later routine or animation tail after the current
  movement copy. Do not apply velocity writes, break flags, routine changes, or
  defeat flow directly in the engine touch callback until a PC probe or aux
  timeline proves ROM mutates that state in the touch pass. MTZ3 Obj54/Obj53 is
  the cautionary pattern: early touch-pass reaction masked a missing same-pass
  break tail and created several false phase "fixes."

### Repeated input-staleness windows are a shared-mechanic alarm

If several unrelated objects need identical stale logical-input windows, stop
adding per-object exceptions. Search the player disassembly for a shared gate
that skips or suppresses grounded movement/input consumption (wait animation,
blink/impatient-idle, move lock, control lock, object control, etc.). For S2,
the impatient-wait blink path in `Obj01_MdNormal_Checks` can ignore held input
for a short grounded window without using a conventional control lock; per-ride
three-frame stale-input patches should be treated as temporary evidence until
the common player mechanic and ride-time wait-animation cadence are modeled.

### ROM-revision (REV01 vs REV00) divergences are real — model, don't carve

The recorded S1 traces are REV01. REV01's `FixBugs=0` makes `FindFreeObj`/object
scans cover a REDUCED slot range (object slots 1..63) in some paths — e.g. the SLZ
boss spikeball duplicate-check (`BSLZ_MakeBall .checkForBall`) only sees balls in
slots <64, reproducing ROM's two-balls-on-one-seesaw cadence. When a fix depends
on REV01 behavior, gate it on the ROM revision (a `FixBugs`/revision predicate),
never on zone/route/frame.

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

If a fix is genuinely game-divergent (different games' ROMs really do behave differently), choose the smallest accurate owner from `docs/architecture/per-game-rule-placement.md`, set all Sonic 1/Sonic 2/Sonic 3&K values explicitly, and branch on that semantic rule/profile/provider value at the call site.

## Do NOT bounce a frontier as "RAM-gated" without a PC-execute probe first

**Hard rule (hard-won, repeatedly violated):** a bounce that concludes a frontier is "BizHawk/RAM-gated", "needs hardware RAM", or that the deciding value is a "mid-frame register / sub-frame spawn order / slot-occupancy cadence / sub-pixel that no per-VBlank trace can capture" is **NOT valid** until you have built a targeted **PC-execute probe** (see *BizHawk Live Diagnostic Capture* above — `diag_template_fast.lua` + `event.onmemoryexecute`) and captured the exact mid-frame 68k state at the divergence instruction.

**Why:** the recorder samples RAM once per VBlank, but the recorder is *Lua inside BizHawk* — `event.onmemoryexecute(cb, addr, "system")` fires at ANY 68k instruction and `emu.getregister("M68K <reg>")` / `mainmemory` read state **mid-frame, at that exact PC**. "The per-VBlank trace can't see it" is a recorder limitation, **never** a BizHawk one. Mid-frame registers, the slot a `FindFreeObj` returns, the `x_sub` at a `SpeedToPos`, a counter at its update — all directly capturable.

**Proof this matters:** SYZ1 f4430 was bounced THREE times as "RAM-gated on ROM mid-frame obVelY". A PC-probe at the `Sonic_FloorUp` branch PCs (0x13DF0/0x13DF2/0x13E02), gated to `a0==v_player` in the divergence window, captured `obRoutine=0x04` (Sonic_Hurt) + which branch fired — revealing the engine missed `Sonic_HurtStop`'s velocity-zeroing on the *angled-ceiling* hurt-land. Object-local fix, universal S1/S2/S3K, commit `42bb2fe22`, 554→406 errors. The "uncapturable" wall was an error, not a fact.

**Recipe by gate type** (build a standalone probe lua per *BizHawk Live Diagnostic Capture*; gate callbacks to `a0==0xFFD000` for the player or the target object slot + a `bk2_offset+frame` window; read-only):
- **mid-frame register** → hook the computing instruction (the `move`/`btst`/branch); dump the register/field + which branch fired. Either find the engine's divergent branch/value (fix) or prove engine==ROM at every step (real bounce).
- **slot-cadence / slot-interleave** → hook `FindFreeObj`/`FindNextFreeObj` (slot returned in an address reg → `slot=(areg-0xFFD000)/0x40`) + `DeleteObject` + the spawn site; capture the per-frame spawn/free→slot timeline; diff vs the engine to find the FIRST object that takes a wrong slot and WHY (spawned/freed at the wrong frame). Turns "irreducible slot-occupancy" into a decodable spawn/free-cadence diff — sometimes a fixable object-lifetime bug.
- **sub-pixel** → hook `SpeedToPos` / the position-update instruction; capture `x_sub`/`y_sub` mid-frame.
- **counter-phase** → hook the counter's update instruction.

**Probe output path:** `tools/bizhawk/` is per-worktree (not shared), so write probe
output via an env var (`OGGF_DIAG_OUT` for `diag_template_fast.lua`, or
`OGGF_OUT`) to a stable absolute path rather than a relative one — EmuHawk's CWD
becomes the lua dir, so relative paths land unpredictably. All probe paths
(`--lua`, `--movie`, the ROM) must be ABSOLUTE.

**A legitimate bounce category the probe sometimes reveals: chaotic feedback
loops.** A few frontiers are a sub-frame perturbation amplified over thousands of
frames — the per-frame logic is byte-faithful, but a tiny initial difference (e.g.
the engine reads a `playerStanding`/status bit during the player solid pass while
ROM's object reads the bit set by ANOTHER object the *previous* frame, a
slot-order read-ordering) tips a boss/object into a different attractor and the
divergence only becomes visible at one downstream event (S1 SLZ3 f11325: the boss
phase-lagged ~one seesaw over a ~3000-frame fight; the trace compares only the
player, so the drift was invisible until a missed launch). This is a HONEST bounce
ONLY after the probe proves every per-frame step faithful AND a speculative
read-ordering reorder is shown unsafe (it touches shared code holding other greens
and the outcome is chaotic/uncertain). Quote the captured boss/object timeline.

Only after the probe shows the engine's mid-frame value matches ROM at every inspectable step is a "gated" bounce honest — and then quote the captured values, not "ROM unknown". See memory `bizhawk-pc-execute-hook-lever`.

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
- **Trace recording (game-specific):** `bizhawk-headless-trace`, `s1-trace-replay`.
- **S3K specific:** `s3k-plc-system`, `s3k-zone-events`, `s3k-zone-analysis`, `s3k-zone-bring-up`, `s3k-palette-cycling`, `s3k-parallax`, `s3k-animated-tiles`.
- **Generic engineering process:** `superpowers:systematic-debugging`, `superpowers:dispatching-parallel-agents`, `superpowers:writing-plans`, `superpowers:test-driven-development`, `superpowers:verification-before-completion`, `superpowers:requesting-code-review`.

## Queue and Dynamic-Art Frontier Triage

First verify the fixture's declared evidence. Audited native captures use
`--load-queue-state` and advertise `load_queue_state_per_frame`;
DPLC/player-art auditing additionally advertises
`dynamic_art_transfer_state_per_frame`.

Interpret report families separately:

- `queue.s1_nemesis_plc.*` and `queue.s2_nemesis_plc.*` compare physical
  Nemesis PLC state.
- `queue.s3k_kos_direct.*` compares physical direct Kosinski jobs.
- `queue.s3k_kos_module.*` compares physical KosM parents.
- `dynamic_art.frame`, `dynamic_art.edges`,
  `dynamic_art.edge[N].request[N].*`, terminal forwarding, and
  `dynamic_art.outstanding_transfer_ids` compare player-art lifecycle and
  ordered ledger state, including schema-2 run-gap carry.

All are zero-tolerance, comparison-only fields. Fix the earliest queue or
dynamic-art cause before downstream symptoms. For S3K, distinguish an ordinary
comparator mismatch from a hardware-timing admission error: v5 timing can only
release a matching, prepared, production-submitted ROM job after kind,
ordinal, fingerprint, and service-boundary checks; it cannot create work.

Record the first frame, exact field/admission reason, and total error count in
`docs/status/trace-frontier-log.md`. Never add missing capability names to
legacy metadata or infer audited evidence from an old trace.

### Transition-gap (`run_gap.*`) triage traps

Field-level contracts for `movie_logical_frame` and `gap_edge_index`, and the
S1 `segment_start - 26` load-pair invariant, live in the `plc-system` skill's
"Dynamic-Art Reports and Routing". The mechanical traps that cost the most time:

- **`art=serial` in the harness step window cannot tell you whether a gap edge
  was emitted.** `deliverySerial` advances only inside
  `DynamicArtLifecycleService.publishRow`/`publishBuffered`
  (`.../DynamicArtLifecycleService.java`:776-797, :823-842), both of which
  require an open comparison segment — and the window is closed for the whole
  gap. Gap edges live in a separate ledger,
  `DynamicArtLifecycleService.gapTransitions`, compared by
  `TraceRunDynamicArtGapJournal`. Instrument that ledger, not the publication
  serial.
- **Transition-gap rows never service a production V-blank.** Measured directly:
  removing the S1 preparation's boundary flush makes the return pair vanish from
  the gap ledger entirely.
- **The gap ledger is compared at destination admission, BEFORE the admitted
  row's body runs.** `TraceRunDynamicArtGapJournal.destinationOpened` is called
  from `TraceSessionLauncher.applyRunDestinationAdmission`
  (`src/main/java/com/openggf/TraceSessionLauncher.java`:1501-1505), after
  `settlePreMainLoopPlayerTransferAtAdmission` (:1444-1467). Anything that must
  be visible to the comparison has to settle at or before that point — but
  settling *unconditionally* at admission invents a spurious pair at the
  special-stage gap, so settle only an explicitly held tail.
- **The shared movie clock still reads the gap's LAST row at admission.** The
  level's first main-loop row is the next one, so a transfer derived back from
  it is at `movieLogicalFrame + 1 - tailRows`
  (`DynamicArtLifecycleService.settlePendingPlayerPreparationBeforeLevelMainLoop`,
  :661-678, and `flushS1PreparationIfPending`, :695-709). A run that ends before
  any level reaches its main loop can never measure the tail back from it and
  takes the earliest legal row instead
  (`releaseUnclaimedPreMainLoopPlayerTransfer`, :680-693).

### Prefer derivation over "it needs recorded timing"

Closing all 210 frames of the S1 emerald route's special-stage-return divergence
took only frame-counted ROM loops (`Level_Delay`, `PalFadeIn_Alt`,
`PaletteFadeOut`, `SSR_*`) plus fixture invariants — no new recorded stream, and
no fitted constant. A residual initially attributed to un-modelable hardware load
cost was refuted by a one-line manifest query showing the value was
zone-invariant. Before concluding a divergence needs hardware timing: count the
ROM's actual wait loops in the listing, and check whether the fixture already
pins the quantity as an invariant across zones with very different workloads. An
elapsed hardware cost varies with payload; a counted loop does not.

## Why This Matters

The mission is faithful pixel-for-pixel reimplementation. Trace replay tests are the proof. If they're allowed to lean on synced trace data each frame, the proof is hollow — bugs hide behind the synchronisation and the test green-lights anyway. Honest tests force honest engine fixes. That's how progress compounds: every fix makes the next divergence visible instead of building on top of a masked one.
