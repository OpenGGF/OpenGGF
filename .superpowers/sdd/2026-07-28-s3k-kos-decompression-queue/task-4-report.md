# Task 4 report: AIZ and ICZ gameplay consumers

## Status

Implemented the AIZ and ICZ consumers against the shared direct Kosinski FIFO
and KosM parent queue. No trace fixture was added or changed.

## Implementation

- AIZ now submits the ROM-owned main-level block stream as ordinary Kosinski
  work and the main-level pattern archive as KosM work exactly once.
- AIZ publishes the block overlay on the first event scan observing the direct
  FIFO empty after PRE retirement, and publishes patterns only on a later scan
  after KosM parent retirement at POST.
- `AizIntroTerrainSwap` has separate block and pattern publication methods.
- ICZ now submits the two ROM-owned ordinary chunk/block streams plus the KosM
  pattern archive at camera X `$6900`; the synthetic 41-frame timer is removed.
- ICZ transitions on physical direct-FIFO emptiness, transfers the exact
  session-owned handles to the new ICZ2 event owner during the in-frame
  seamless reload, and publishes through the layout mutation pipeline.
- ICZ's KosM parent is exportable only for this structural handoff.
- AIZ and ICZ capture exact kind/ordinal identities and lazily rebind queue
  facades after rewind. Schema guards and manager sidecar coverage were
  updated.

## RED/GREEN evidence

- RED: the new AIZ test expected one direct submission but observed zero; the
  ICZ headless test expected two direct submissions but observed zero.
- RED: the ICZ handoff test expected its module parent to be exportable but it
  was not.
- GREEN: the focused Task 4 boundary, handoff, schema, and rewind set completed
  with 30 tests and no failures before the additional after-boundary rewind
  case; `TestS3kZoneKosRewind` then completed with 5 tests and no failures.

## Verification

- `mvn -q -Dmse=off -Ds3k.rom.path=... -Dtest=TestS3kZoneKosRewind test`
  — pass, 5 tests.
- Focused AIZ/ICZ boundary, headless handoff, schema, and sidecar command
  — pass, 30 tests.
- Terrain/module expansion command — 47 relevant tests passed; the command was
  red only in the pre-existing source-text assertion
  `TestS3kAizMutationPipeline.seamlessMutationExecutorShouldRouteAizImmediateMutationsThroughPipeline`.
- The requested six-class command remains red on baseline failures outside
  this task: two fixed-air sidecar assertions and existing AIZ intro/fire test
  helper assumptions. Focused Task 4 tests in that run passed.

## Concerns

- The complete requested class sweep is not green on assigned HEAD. Task 4
  does not alter fixed-air ownership or the unrelated AIZ intro/fire sequence.
- Maven validate logs a read-only `.git/config` warning while attempting to
  install hooks; builds and tests continue normally.
