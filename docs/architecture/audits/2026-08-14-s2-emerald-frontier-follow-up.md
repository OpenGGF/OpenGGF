# S2 complete-emerald frontier — production visual follow-up

**Date:** 2026-08-14  
**Base:** `develop` at `3f0dde97b`  
**Related manifest:**
[`2026-08-14-s2-emerald-frontier-manifest.md`](2026-08-14-s2-emerald-frontier-manifest.md)  
**Status:** design approved in conversation; implementation not started

## Purpose

This is a relay log for the agent investigating the five axes in the related
manifest. It records an additional production-path failure found while reviewing
that work, the experiments that discriminate it, and the bounded implementation
that will be attempted. Measurements are labelled separately from hypotheses.

## Reproduced chain baseline

**MEASURED.** On JDK 21.0.11, the committed chain still reports exactly the five
axes in the related manifest:

```text
walk-failure: seg7_ehz2 cursor 3977 / 3997
segment-physics: seg11, 236 errors, first at f3525 queue.s2_nemesis_plc.busy
dynamic-art-gap: seg4_ehz1 -> seg5_ehz2, four movie rows at -1
dynamic-art-gap: ss_4 -> seg6_ehz2, two submission rows at +1
dynamic-art-gap: ss_5 -> seg7_ehz2, 16 expected edges / 18 actual
```

Command:

```bash
mvn -Ptrace-replay -Dmse=off -Dsurefire.forkCount=1 \
  -Dsurefire.runOrder=alphabetical \
  -Dtest=TestS2CompleteEmeraldRunChain \
  -Dsonic2.rom.path=s2.gen test
```

This confirms the manifest is current for `AbstractRunChainTest`.

## Newly exposed production-path axis

**MEASURED.** A temporary S2 analogue of `TestS1CompleteEmeraldVisualRun`, using
`VisualRunReplayHarness`, pauses at segment 0 frame 0 with 91 comparison errors.
The first fields are `player_history.y[63]` through adjacent ring entries:

```text
ROM    0x0293
engine 0x0294
```

The committed pre-trace snapshot is structurally consistent with the ROM:

- `StartLocations/EHZ_1` is `(0x0060, 0x028F)`;
- `Obj01_Init_Continued` prefills the 64-entry position ring at
  `(Sonic.x - 0x20, Sonic.y + 4)`, hence Y `0x0293`;
- the 26 title-card player passes overwrite 26 entries with Sonic's settled
  Y `0x0290` before frame 0.

The chain fixture does not reveal this because its synthetic bootstrap explicitly
prepares the position ring. The production visual path runs the real title card.

## Discriminating experiments

All changes below were temporary and reverted after measurement.

### Experiment 1: preserve only the prepared visual session

**MEASURED.** Skipping `applyStartPositionAndGroundSnap` when
`TraceReplayDriver.startPlayback(..., preparedLevel=true)` reduced the initial
failure from 91 to 76 errors. The remaining prefill entries became `0x0290`, not
the ROM's `0x0293`.

**DERIVED.** The prepared replay path was incorrectly rerunning standalone
metadata-start setup, but removing that duplicate setup alone exposes a second
owner overwriting the ring.

### Experiment 2: preserve the level-load prefill through Tails CPU init

**MEASURED.** Combining experiment 1 with production level-load registration of
the already-established ROM prefill removed all 91 frame-0 errors. The visual
replay then consumed all of EHZ1 and entered special stage 1 before pausing at
special-stage frame 136:

```text
field: dynamic_art.edges
ROM:   [] with outstanding transfer ids [1, 2, 3]
engine:[4, 5, 6] with no outstanding transfers
```

This later dynamic-art readiness mismatch is independent and outside the first
implementation scope.

### Negative controls

**MEASURED.** Neither of these changed any of the original five chain axes:

- replacing the special-stage-return Tails top-left setters with centre setters;
- deleting the stale duplicate Tails reset in `enterTitleCardFromResults`.

The stale code remains cleanup debt, but it is not a cause of this frontier.

## Approved implementation design

### Production ownership

1. A prepared visual replay adopts the state already produced by the real title
   card. `TraceReplayDriver` must not reapply the standalone metadata position,
   ground snap, sidekick reposition, or position-ring prefill on that path.
   Standalone replay keeps the existing setup unchanged.
2. `LevelManager.spawnSidekicks` already performs the ROM's accurate position-
   and stat-history prefill. It will explicitly tell `SidekickCpuController`
   that this prefill is authoritative, so the controller's first INIT tick uses
   the existing skip-prefill path instead of replacing the ring with Sonic's
   live position.
3. The existing bootstrap helper remains valid for standalone trace setup. The
   production API will describe ownership of an already-populated prefill rather
   than call a method named `ForBootstrap` from ordinary level loading.

No trace row or auxiliary value will be copied into gameplay state. Every value
continues to come from the ROM start location and ordinary production execution.

### Regression test

Add `TestS2CompleteEmeraldVisualRun` beside the S1 visual test. Its first canary
will replay through the end of segment 0 via `VisualRunReplayHarness`, proving:

- the real title-card path reaches comparison without pausing at frame 0;
- the entire first EHZ1 body remains strict;
- the test stops before the newly exposed special-stage frame-136 mismatch.

The test must be observed red on the current implementation and green only after
both ownership corrections.

### Non-goals

- Do not alter the five axes in the original chain manifest.
- Do not address special-stage frame 136 in the same change.
- Do not extend the interior-return census walk.
- Do not relax dynamic-art or history comparison.
- Do not change the hardware-timing trace contract.

## Expected verification

Focused verification will include the new visual canary, the S2 complete-emerald
chain, S2 frame-0/bootstrap tests, sidekick level-start tests, and the existing S1
production visual canary. The complete Maven suite will be compared against an
updated `develop` baseline before integration and again after merge.

## Progress log

- **2026-08-14 — REVIEW:** Original five axes reproduced exactly.
- **2026-08-14 — REVIEW:** Production visual path exposed 91 earlier frame-0
  history errors.
- **2026-08-14 — PROBE:** Two ownership changes cleared those 91 errors and
  moved the production visual frontier to special-stage frame 136.
- **2026-08-14 — DESIGN:** Scope bounded to the visual-session adoption and
  level-start prefill ownership fixes plus one permanent visual canary.

