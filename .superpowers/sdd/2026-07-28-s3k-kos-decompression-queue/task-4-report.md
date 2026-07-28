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

## Review follow-up

- Replaced the ICZ event-object transfer with a gameplay-session-owned,
  rewind-snapshottable handoff registry. The transition request carries only
  an opaque ID; the executor claims it before mutation, verifies exact
  per-load descriptor consumption, and transfers handles after target event
  initialization.
- Added an immutable S3K LevelLoadBlock resource profile for AIZ intro entry
  26 and ICZ2 entry 11. This omits only the exact secondary ROM
  source/compression/destination triples whose production jobs already own
  publication. Ordinary LevelLoadBlock entry 0 remains unchanged.
- ICZ2 now claims both direct jobs and the KosM parent in one consumer scan,
  then publishes all three prepared payloads atomically. The public boolean
  queue policy was replaced with the ICZ-specific intent method
  `queueForIczSeamlessHandoff`.
- Added negative exact-once/isolation tests for manifests and trackers,
  registry claim/failure/rewind tests, scalar handoff-ID rewind coverage, exact ICZ
  post-publication bytes, and a whole-payload AIZ direct publication check.
- ICZ2's two direct payloads and KosM payload remain byte-hidden while their
  exact jobs are unclaimed. All three are asserted byte-exact after the
  atomic publication scan.

### Review verification

- Isolated review-focused set: pass, 31 tests.
- `TestS3kIczAct1TransitionHeadless`: pass.
- Prescribed six-class comparison: 112 tests, 99 passed, 13 failed, 0 errors.
  Exact `d230501d4` baseline was 108 tests, 94 passed, 14 failed, 0 errors.
  The baseline's AIZ main-level queue assertion is now green; the remaining
  11 AIZ failures and two fixed-air failures are the same pre-existing
  failures. No new regression was introduced.
