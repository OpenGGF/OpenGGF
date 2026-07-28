# Task 2 report — shared direct Kosinski queue

## Delivered

- Added the session-owned four-entry `S3kKosDecompressionQueue`, direct stream
  descriptor/preparation/snapshots, and exact signed 68000 RAM destination
  constants.
- Standard-Kos inspection begins at the descriptor and derives exact consumed
  and output spans using the existing resumable decoder.
- Direct decoder work is PRE-only; physical FIFO retirement happens after
  timing readiness admission, leaving ready-unclaimed payload ownership in the
  timing ledger rather than consuming a direct slot.
- Registered the queue with gameplay-session rewind lifecycle and exposed its
  single owner through frame context and `GameServices`.
- Added scanner, malformed backreference, FIFO capacity, identity, recorded
  PRE-edge, destination fingerprint, and rewind round-trip coverage.

## Verification

`mvn "-Dtest=TestS3kKosDecompressionQueue,TestResumableKosinskiDecoder,TestHardwareTimingRewind,TestRewindCoverageGuard,TestStaticStateRewindCoverageGuard" test`

The new direct-queue suite (6 tests) and decoder suite (4 tests) passed. The
overall invocation remains red on existing unrelated AIZ tests and the
pre-existing rewind guard gap for
`Flybot767BadnikInstance#layoutWaitUsesRetainedRenderFlag`.

## Review follow-up

- Direct preparation now advances one descriptor command per PRE boundary, so
  active decoder state is a real rewind state rather than a completion loop.
- Scanner coverage now includes descriptor refill, short/long matches,
  no-output commands, terminators, malformed backreferences, and bounds.
- Claimed handles are removed from the descriptor facade before queue capture;
  a capture/restore after claim is covered explicitly. The descriptor contract
  therefore lasts through claim only.

Focused evidence: `TestS3kKosDecompressionQueue` has 7 passing tests and
`TestResumableKosinskiDecoder` has 6 passing tests.

## Deferred

`S3kKosModuleQueue` remains unchanged by design: composing module parents
over direct children is Task 3.

Production lifecycle coverage for the session facade, rewind registration
ordering, and teardown reset remains an outstanding follow-up.

## Fix round 4 — production lifecycle proof

### Delivered

- Added `TestS3kKosDecompressionQueueLifecycle`, using the real
  `SessionManager`, `GameplaySessionFactory`, `GameServices`, `RewindRegistry`,
  and `LevelFrameStep` production paths.
- Proved one direct queue is owned per `GameplayModeContext` and both strict
  and nullable `GameServices` facades return that identical owner.
- Proved the production rewind layout registers `HardwareTimingService` before
  `S3kKosDecompressionQueue`. The round trip captures after the first direct
  decoder command, advances and claims the job, then restores through the
  actual registry and verifies both physical FIFO membership and the exact
  resumable decoder cursor/output before completing on the remaining three PRE
  boundaries.
- Proved `SessionManager.closeGameplaySession()` withdraws strict and nullable
  facade access, resets the retired timing/physical owners, and a newly opened
  context owns a distinct empty queue with no timing-ledger state.
- Corrected the existing rewind-registry test name and class comment from eight
  to nine always-present adapters.
- Production wiring required no changes; the lifecycle tests exposed no wiring
  defect.

### Test mutation check

Temporarily reversed the two production registrations, then ran:

`mvn -Dmse=off "-Dtest=TestS3kKosDecompressionQueueLifecycle#rewindRegistryRestoresTimingBeforePhysicalQueueAndResumesExactDecoderState" test`

Expected result: 1 test, 1 failure. The new test rejected the reversed layout
with `timing must restore before its dependent physical queue`. The production
order was restored immediately afterward, leaving no production diff.

### Verification

`mvn -Dmse=off "-Dtest=TestS3kKosDecompressionQueueLifecycle" test`

Result: 3 tests, 3 passed.

`mvn -Dmse=off "-Dtest=TestS3kKosDecompressionQueueLifecycle,TestS3kKosDecompressionQueue,TestRewindCoverageGuard,TestStaticStateRewindCoverageGuard" test`

Result: 12 tests, 11 passed, 1 failed. The lifecycle suite passed 3/3, direct
queue suite passed 7/7, and static-state guard passed 1/1. The sole failure is
the pre-existing unrelated rewind-coverage gap:
`Flybot767BadnikInstance#finalScalar#layoutWaitUsesRetainedRenderFlag`.

`mvn -Dmse=off "-Dtest=TestS3kKosDecompressionQueueLifecycle,TestS3kKosDecompressionQueue,TestResumableKosinskiDecoder,TestHardwareTimingRewind,TestRewindCoverageGuard,TestStaticStateRewindCoverageGuard" test`

Result: 26 tests, 24 passed, 1 failed, 1 errored. In addition to the same
unrelated Flybot coverage failure,
`TestResumableKosinskiDecoder#scannerHandlesDescriptorRefillAndAllMatchForms`
errors with `Unexpected end of Kosinski module`. The lifecycle suite passed
3/3, direct queue suite passed 7/7, hardware timing rewind passed 8/8,
static-state guard passed 1/1, and the other five resumable-decoder tests
passed. This decoder-fixture error is outside the remaining lifecycle scope
and predates this fix round.

Final green focused verification:

`mvn -Dmse=off "-Dtest=TestS3kKosDecompressionQueueLifecycle,TestS3kKosDecompressionQueue,TestGameplayModeContextRewindRegistry,TestStaticStateRewindCoverageGuard" test`

Result: 30 tests, 30 passed.
