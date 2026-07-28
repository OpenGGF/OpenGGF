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

## Deferred

`S3kKosModuleQueue` remains unchanged by design: composing module parents
over direct children is Task 3.
