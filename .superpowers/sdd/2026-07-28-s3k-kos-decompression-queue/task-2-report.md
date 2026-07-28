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
