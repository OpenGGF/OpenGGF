# Visual Trace and Headless Replay Parity Validation

## Outcome

The master-title visual trace path now uses the same structural replay-row
contract as the headless driver. Expected physics, queue, PLC, Kosinski, and
dynamic-art values remain comparison-only. Recorded hardware timing still has
only its documented authority to delay readiness of matching, prepared,
production-submitted ROM work.

## Delivered contracts

- One immutable `TraceReplayRowPolicy` selects validation/applied BK2 rows,
  execution phase, publication ownership, suppressed closure, sidekick hold,
  and playable-prefix action from existing structural replay predicates.
- `PlaybackDebugManager` prepares that policy before ROM Start/pause admission,
  applies held/action/Start history from the selected row, and keeps validation
  on the represented current row.
- `TraceSuppressedRowClosure` is shared by headless playback, live forward
  playback, and visual rewind. One stored row owns zero or one closure; held
  S3K title-card rows run the hardware-timed object scan and all other
  represented suppressed rows use `LAG` VBlank service.
- Physical S1/S2 PLC and S3K Kosinski queue comparison remains inside the row
  after service/preparation. Player-DPLC comparison is queued until the outer
  `PlcFrameLifecycleCoordinator.finish()` publishes the immutable diagnostic
  snapshot. Run segment close/open and comparator rebind are deferred until the
  old row drains.
- Headless and live bootstrap comparison share one read-only engine snapshot.
  Live input-alignment and bootstrap differences feed the HUD mismatch ring,
  counters, and first-error pause callback without changing gameplay.
- An incomplete bootstrap, callback, Esc exit, production exception, or fatal
  error uses the fixture abort seam: timing observers, rewind registrations,
  and close hooks detach without strict schedule verification; configuration
  is restored, the failed context is destroyed, and the master title regains
  control. Successful completion retains strict verification and fade-out.

## Focused verification

All commands ran under Maven's JDK 21 JVM with the three locally discovered
ROMs verified against the project SHA-1 table.

The final pre-integration trace/PLC/rewind/launcher batch passed 251 tests:

```text
mvn -q -Dmse=off -Dtest=TestPlaybackAdvanceOnlyInputBridge,
TestGameLoopTraceRunPostIteration,TestTraceSessionLauncherAdvanceOnlyRewind,
TestTraceSessionLauncherRewindPresentation,TestRecordingFrameDriverDynamicArt,
TestRecordingFrameDriverHardwareTiming,TestLoadQueueTraceComparison,
TestPlcLifecycleDriverParity,TestSkippedPresentationPlcTraceIsolationGuard,
TestTraceRunHardwareTimingCoordinator,TestTraceRunReplayWalkerControlFlow,
TestSpecialStageHardwareTimingLifecycle,TestTraceSessionLauncherRunBranch,
LiveTraceComparatorTest,TestLiveTraceComparatorObserver,
TestHardwareTimingAuthorityGuard,TestS1S2PlcComparisonOnlyGuard,
TestTraceReplayRowPolicy,TestTraceSuppressedRowClosure,
TestPlaybackDebugManagerPreparedInput,TestTraceSessionLauncherFailureCleanup,
TestTraceSessionLauncherProductionFailureCleanup,TestGameLoop,
TestBootstrapComparator,TestTraceSessionLauncherAdvanceOnlyRewind test

Tests run: 251, failures: 0, errors: 0, skipped: 0
```

Additional focused coverage passed for live bootstrap ingestion, callback
cleanup after engine teardown, production `RuntimeException` containment,
fatal `Error` cleanup/rethrow with suppressed cleanup failure, special-stage
partial install abort, and the new preparation-before-admission source
contract.

The first feature full-suite pass on the original branch base executed 13,994 tests and reported 19
failures plus 7 errors. Two task-attributable guard failures were resolved:
the old playback-admission source assertion now pins the intended early
preparation order, and title-card ownership was extracted so `GameLoop`
remains below its release-critical size budget. The remaining failures are in
the repository's existing hardware-boundary, rewind-torture, object-policy,
fixture-publication, and unrelated Sonic 2 areas. Final baseline comparison,
post-merge command results, commit identities, and review disposition are
recorded below during integration.

The refreshed `develop` baseline at `fa3900494` executed 13,980 tests and
reported 20 failures plus 7 errors. The exact failing methods were archived
before worktree reconciliation; this red baseline is the comparison set for
the updated feature and post-merge runs.

## Integration report

Pending refreshed `develop` baseline comparison and post-merge verification.

## End-to-end review

An independent implementation review found and drove fixes for lifecycle-marker
deferral, playable-prefix comparison order, held Start edge detection, fatal
partial-launch cleanup, bonus timer suppression, rewind restore reconstruction,
and pause-versus-setup closure ownership. The final pre-integration review
reported no blocking issues. A short confirmation review remains after upstream
reconciliation.
