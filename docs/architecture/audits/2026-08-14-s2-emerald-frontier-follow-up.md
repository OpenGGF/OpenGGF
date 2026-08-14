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
   and stat-history prefill. This is shared shipped-ROM behaviour, not an S2
   exception: S2 `Obj01_Init_Continued` offsets Player 1 by `(-$20,+4)` and
   fills/clears the rings (`s2.asm:36201-36217`), while S3K
   `Sonic_Init_Continued` calls `Reset_Player_Position_Array` under the same
   offset (`sonic3k.asm:21931-21940,22166-22178`). No typed per-game rule is
   therefore appropriate.
3. The ownership signal will be granted only to a controller whose
   `getLeader()` is the exact main-player instance whose ring `LevelManager`
   populated. Multi-sidekick teams chain later controllers to the preceding
   sidekick, whose ring was not populated by this operation; those controllers
   must retain their existing initialization path. A production-level
   multi-sidekick test will pin that distinction.
4. For the directly-following controller, `LevelManager` will explicitly say
   that the already-established prefill is authoritative. This is a distinct
   production ownership state, not an alias for the existing
   `bootstrapPreludePlacementApplied` state. On the first INIT tick it changes
   only the history operation: the controller still performs the ordinary
   captured-level-start-anchor placement and transient CPU reset, still
   preserves the air state applied after spawn by S3K's MGZ1/HCZ1/LRZ1 intro
   owner, and skips only the destructive leader-ring rewrite. The existing
   bootstrap skip helper is not reused because it reanchors from the live
   leader and forces `air=false`. The new internal state remains
   rewind-captured.
5. The existing bootstrap helper remains valid for standalone trace setup. The
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

Add a focused production-level test that calls `LevelManager.spawnSidekicks`,
runs the directly-following controller's first INIT tick, and proves the
main-player ring retains the hand-derived `(-$20,+4)` values. Extend the
multi-sidekick integration coverage to prove only the controller whose leader is
that main player preserves this prefill; chained followers continue to initialize
their own leader history through the existing path. Add a characterization for
an S3K falling-intro sidekick whose zone-event owner sets `air=true` after spawn:
the first CPU INIT tick must retain that state while using the captured spawn
anchor and preserving the main leader's prefilled ring.

### Non-goals

- Do not alter the five axes in the original chain manifest.
- Do not address special-stage frame 136 in the same change.
- Do not extend the interior-return census walk.
- Do not relax dynamic-art or history comparison.
- Do not change the hardware-timing trace contract.

## Expected verification

Focused verification will include the new visual canary, the S2 complete-emerald
chain, S2 frame-0/bootstrap tests, direct- and multi-sidekick level-start tests,
and the existing S1 production visual canary. Because the changed owners are
shared with S3K, verification will also include affected S3K sidekick tests, the
mandatory keep-green set (`TestS3kAiz1SkipHeadless`,
`TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`, and
`TestSonic3kDecodingUtils`), plus a trace-profile `*TraceReplay` sweep with all
three ROMs. The default complete Maven suite and the trace-profile sweep will be
compared against updated `develop` baselines before integration and again after
merge. Any newly exposed visual frontier will also be recorded in
`docs/status/trace-frontier-log.md` with its exact command and first error.

## Progress log

- **2026-08-14 — REVIEW:** Original five axes reproduced exactly.
- **2026-08-14 — REVIEW:** Production visual path exposed 91 earlier frame-0
  history errors.
- **2026-08-14 — PROBE:** Two ownership changes cleared those 91 errors and
  moved the production visual frontier to special-stage frame 136.
- **2026-08-14 — DESIGN:** Scope bounded to the visual-session adoption and
  level-start prefill ownership fixes plus one permanent visual canary.
- **2026-08-14 — DESIGN REVIEW:** Independent review identified shared S3K
  coverage and chained-leader ownership as blockers. The design now cites the
  equivalent S3K reset, scopes authority to the actually populated main-player
  ring, and requires explicit S3K, multi-sidekick, and trace-profile coverage.
- **2026-08-14 — DESIGN RE-REVIEW:** Review found that the existing bootstrap
  skip helper also reanchors from the live leader and clears S3K intro air state.
  Production ownership is now a separate rewind-captured predicate that skips
  only the ring rewrite and retains the ordinary captured-anchor/air semantics.
