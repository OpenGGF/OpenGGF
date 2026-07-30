# Task 8 fix round 1 implementer report

## Outcome

All blocking findings from the independent Task 8 review are implemented.
This report is separate from `task-8-report.md`; the reviewer report remains
unchanged.

## Implemented boundaries

- `LiveTraceComparator` keeps ordinary gameplay comparison at its original
  frame timing but defers the final advertised DPLC envelope. A structural
  production owner closes the comparison segment first, after which the
  comparator captures one immutable terminal snapshot and merges the DPLC
  fields exactly once.
- Ordinary headless replay closes the production dynamic-art segment before
  comparing the final advertised row. Early-frontier exits compare no terminal
  row that production did not reach.
- Live single-trace and named-run level owners close their structural window
  before invoking the read-only terminal comparison seam.
- `GameplayModeContext` owns the value-free segment-close operation. The trace
  fixture can request that structural boundary but cannot reach
  `DynamicArtLifecycleService` or any trace-derived mutation API.
- Named-run special stages now use an ordered DPLC-only accumulator alongside
  the existing gameplay-uncompared policy. Every represented row, including
  lag rows and the drained tail after the simplified engine has left special
  stage mode, is stepped through the production coordinator before its
  immutable snapshot is compared.
- The final represented special-stage row closes the production window before
  capture, so terminal-forwarded edges are visible on that row.
- Advertised named-run special-stage rows must be complete and ordered.
  Missing, duplicate, extra, or out-of-order rows fail instead of silently
  disappearing.
- Advertised special-stage segments write a dedicated dynamic-art report and
  fail on any divergent DPLC field. Legacy segments without the capability
  preserve their prior gameplay-uncompared behavior and emit no empty report.

No expected edge, ledger, transfer ID, lifecycle cursor, lag value, or gameplay
value crosses a production-owner seam. Comparison remains read-only and
hardware timing remains isolated.

## TDD evidence

- The new live terminal test first failed to compile because no deferred
  terminal comparison API existed.
- The named-run production-row tests first failed to compile because no
  ordered DPLC segment accumulator existed.
- The first real S2 chain exposed an invalid unconditional prepare on a row
  owned by palette-fade production; preparation is now performed only after
  the structural row successfully claims its production phase.
- Both real chains then reached the boundary step cap because tail draining
  accounted for represented rows but did not continue the engine's
  unrepresented return choreography. The driver now resumes ordinary engine
  stepping after the represented row budget is exhausted.
- The first authority-matrix run rejected a direct lifecycle mutation call
  from `TraceReplayFixture`. Moving the value-free close into
  `GameplayModeContext` restored the trace comparison-only boundary.

## Positive advertised production-path coverage

Two focused positive tests advertise
`dynamic_art_transfer_state_per_frame_v1` and exercise real production
lifecycle/coordinator publication:

1. `finalDynamicArtWaitsForProductionCloseAndComparesExactlyOnce` covers the
   live/level terminal path. It submits a real ROM DPLC decision on a lag row,
   proves no comparison occurs before close, and verifies the terminal-forwarded
   field is compared once after close.
2. `advertisedRunSpecialStageRowsUseProductionCoordinatorSnapshots` covers the
   named-run special-stage row path. It drives first, lag, and terminal
   publication through `PlcFrameLifecycleCoordinator`, compares ordered
   snapshots, and verifies the terminal-forwarded envelope.

`advertisedRunSpecialStageCannotSilentlyOmitARow` is the complementary negative
test for complete row accounting.

## Verification

JDK 21 focused comparator, lifecycle, launcher/walker, producer, comparison-only,
and hardware-authority matrix:

```text
Tests run: 125, Failures: 0, Errors: 0, Skipped: 0
```

Representative ordinary legacy level replays with SHA-1-compatible S1/S2
REV01 ROMs:

```text
TestS1Ghz1TraceReplay,TestS2Ehz1TraceReplay
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

Real legacy named-run chains:

```text
TestS1GhzMazeRoundTripChain,TestS2EhzHalfpipeRoundTripChain
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

The real chains completed with no DPLC mismatch or remaining frontier. Their
checked-in metadata is legacy and does not advertise the new envelope; they
validate backward-compatible production choreography separately from the two
positive advertised synthetic production-path tests above.

`git diff --check` completed with no output.

The Maven validate phase continued to print the known non-fatal read-only
`.git/config` hook-install warning in this linked worktree.

## Delivery scope

The fix commit contains only Task 8 production code, tests, and this implementer
report. The independent reviewer report, BizHawk work, scratch files, generated
trace payloads, and other shared-worktree changes remain unstaged.

## Fix round 2

- `TraceSessionLauncher` now arms an ordered DPLC accumulator on visual
  special-stage entry. `runSpecialTimingRow` latches the represented row
  before production; the all-mode hook captures the immutable snapshot after
  publication, closes before the terminal capture, records divergent fields,
  and rejects missing advertised rows.
- Because the first special-stage admission precedes the run advancer's
  post-production handoff, that admission now anticipates and opens the
  structural production window. The later handoff preserves rather than
  closes/reopens it, keeping row zero and its lifecycle cursor intact.
- Completed `AbstractTraceReplayTest` runs always compare the final advertised
  envelope after structural close. A stale/wrong actual frame is an exact
  `dynamic_art.frame` error; only an explicit frontier stop suppresses an
  unproduced terminal expectation.
- Owner-level tests execute the actual launcher timing choreography and an
  `AbstractTraceReplayTest` subclass backed by a real
  `GameplayModeContext`/`PlcFrameLifecycleCoordinator`.

TDD exposed an initially misbound bonus-stage synthetic segment; rebinding the
test to `run_ehz_ss_3seg` then exposed and fixed the zero-row omission arm.
The completed-replay test first failed because the base exposed no
completed/frontier terminal owner seam.

Fresh JDK 21 verification:

```text
Focused comparator/lifecycle/producer/launcher/walker/authority matrix:
Tests run: 133, Failures: 0, Errors: 0, Skipped: 0

Representative ordinary replays and real named-run chains:
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

## Fix round 3

- `LiveTraceComparator.ingestExternalComparison` is a read-only result sink
  for gameplay-uncompared structural rows. It publishes through the normal
  per-frame observer and updates the same zero-tolerance error/warning counts,
  first-error callback, desync flag, and HUD mismatch ring as an ordinary live
  frame.
- Visual named-run special-stage DPLC rows now use that sink. The temporary
  logger-only/result-list path is removed; a missing live report sink for an
  advertised row fails closed.
- The launcher owner test drives the actual pre-admission -> production ->
  all-mode sequence with a stale snapshot and proves
  `dynamic_art.frame` reaches the normal comparator frontier, observer,
  counter, callback, and mismatch ring.
- The headless tests no longer call a protected finalizer. They clone the real
  5,852-row S2 EHZ fixture, advertise a DPLC envelope for every stored row,
  and invoke the inherited `replayMatchesTrace()` end to end. The completed
  run's structured report carries the `dynamic_art.frame` range through
  terminal frame 5851. With `trace.frontierOnly=true` and radius zero, the
  replay actually stops at frame zero and the report contains no invented
  terminal range. The finalizer is private again.

Fresh JDK 21 verification:

```text
Focused owner/comparator/lifecycle/producer/walker/authority matrix:
Tests run: 136, Failures: 0, Errors: 0, Skipped: 0

Representative ordinary replays and real named-run chains:
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

## Fix round 4

- Visual named-run row zero is now captured from the immutable production
  snapshot before segment advancement, but it is not published into a live
  report until after `RunSegmentAdvancer` has emitted its `AdvanceAction` and
  `applyRunSegmentAdvance` has rebound the comparator, HUD, and playback frame
  observer to the special-stage segment.
- The capture still closes the structural production window before a terminal
  snapshot and verifies complete ordered row accounting. The existing
  terminal-forwarding and omitted-row failure coverage remains green.
- The launcher regression now starts with the real level comparator, performs
  special-stage admission and a real `PlcFrameLifecycleCoordinator` production
  iteration, then drives the all-mode advancer through the real
  `Engine`-owned rebind. The resulting row-zero mismatch contributes exactly
  three errors and three HUD-ring entries to the new special-stage comparator;
  the playback observer is the new comparator and its first-error callback
  pauses the loop. The old level comparator receives no error, ring entry,
  per-frame observation, or callback.

TDD red evidence:

```text
TestTraceSessionLauncherRunBranch#visualRunSpecialStageRowZeroRebindsBeforeMismatchPublication
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```

The production row-zero comparison contained the expected
`dynamic_art.edges`, `dynamic_art.outstanding_transfer_ids`, and
`dynamic_art.edge[0].present` errors, but the old level comparator logged the
first error before the `AdvanceAction`; the rebound special-stage comparator
still had `errorCount == 0`.

Fresh JDK 21 verification with SHA-1-compatible S1/S2 REV01 ROMs:

```text
Focused comparator/lifecycle/producer/launcher/walker/authority matrix:
Tests run: 136, Failures: 0, Errors: 0, Skipped: 0

Representative ordinary replays and real named-run chains:
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

## Fix round 5

- The round-4 launcher regression used the wrong lifecycle order. Production
  calls `runAdvanceTickIfActive` from `GameLoop.stepInternalBody`, while
  `PlcLifecycleFrame.finish` publishes the diagnostics row only afterward in
  `runLogicalIteration`'s outer `finally`.
- `DynamicArtDiagnosticsProvider` now exposes one identity-scoped,
  session-owned publication subscription. The callback receives only an
  immutable `DynamicArtDiagnosticsSnapshot`; the service receives no expected
  row, comparator, report sink, trace event, or lifecycle mutator reference.
- The launcher latches the represented row before production, lets the
  all-mode advancer rebind the special-stage comparator inside the iteration,
  and compares/ingests only when production publishes after the iteration
  body. Row zero therefore belongs exclusively to the rebound comparator.
- A final lag row closes its structural comparison window from the
  post-publication callback, then reads the immutable terminal-forwarded
  snapshot exactly once. The close path emits no recursive notification.
- An admitted row cannot be overwritten by a later admission. Leaving the
  special stage with an unpublished pending row fails as an omission; a
  published row clears its latch before comparison and cannot be ingested
  twice.
- The subscription rejects a second owner, closes by observer identity, is
  not captured as rewind state, remains attached across restore without
  emitting a restore notification, and is removed by launcher
  failure/teardown and production-run teardown.
- The comparison-only guard pins the diagnostics surface to immutable
  snapshots and prevents expected trace or production-lifecycle inputs.

TDD red evidence used the real coordinator ordering:

```text
TestTraceSessionLauncherRunBranch
Tests run: 13, Failures: 3, Errors: 0, Skipped: 0
```

The three failures independently showed the stale `frame=-1` row-zero
comparison inside the iteration body, a broken first/lag/terminal sequence,
and a falsely consumed omitted row. The new subscription contract separately
failed to compile before the read-only seam existed, and its production-run
cleanup test failed until teardown removed the observer.

Fresh JDK 21 verification:

```text
Focused comparator/lifecycle/producer/launcher/walker/authority matrix:
Tests run: 139, Failures: 0, Errors: 0, Skipped: 0

Rewind, production-owner, special-stage, and session matrix:
Tests run: 78, Failures: 0, Errors: 0, Skipped: 0

Representative ordinary replays and real named-run chains with
SHA-1-compatible S1/S2 REV01 ROMs:
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

`git diff --check` completed with no output. Maven validate continued to print
the known non-fatal read-only linked-worktree `.git/config` hook-install
warning.
