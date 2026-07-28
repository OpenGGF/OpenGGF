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
- Added immutable S3K LevelLoadBlock resource profiles for the active AIZ
  intro entry 0 and ICZ2 entry 11. AIZ entry 0 loads its own resources
  immediately while declaring entry 26's secondary pattern/chunk resources as
  deferred. Active entry 26 has no deferred profile, so skip/post-intro loads
  publish its resources immediately.
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

## Round-two review follow-up

- Corrected the AIZ profile ownership inversion: the active intro
  LevelLoadBlock is entry 0, with deferred declarations sourced from entry 26.
  Entry 0's own resources remain immediate, while active entry 26 is ordinary
  immediate loading for intro-skip and post-intro paths.
- The loader now derives a mandatory manifest from the target profile and
  requires exact requested-set equality before consuming any deferred
  descriptor. Missing, extra, nonmatching, and duplicate declarations fail;
  the existing per-load tracker still rejects repeat consumption.
- Added ROM-backed loading acceptance tests for byte-exact AIZ entry 0
  immediacy plus entry 26 hiding, entry 26 immediate skip loading, and an ICZ
  load rejected before mutation when its mandatory terrain declarations are
  incomplete.
- Strengthened AIZ and ICZ acceptance to compare complete affected pattern,
  chunk, and block ranges before publication, during intermediate scans, and
  after publication. ICZ also proves a later POST scan does not rewrite the
  published bytes and that a later ordinary load is isolated from the prior
  tracker.
- Added gameplay-composite rewind coverage before and after a handoff-registry
  claim across an in-frame reload. Queued target reload, same-level reload,
  failed consumption/no-retry, and exact-once ownership on the replayed
  timeline are covered independently.

### Round-two RED/GREEN evidence

- RED: the profile tests did not compile because the model lacked a distinct
  deferred source entry, and the tracker lacked mandatory exact-request
  validation.
- GREEN: the focused round-two set completed with 48 tests, 48 passed, no
  failures or errors.
- Prescribed six-class comparison: 112 tests, 99 passed, 13 failed, 0 errors.
  Exact `d230501d4` baseline: 108 tests, 94 passed, 14 failed, 0 errors. The
  remaining 11 AIZ and two fixed-air failures match the baseline categories;
  no new regression was introduced.

## Round-three review follow-up

- `DeferredLevelResourceTracker` now records whether it represents an
  explicitly supplied policy. `none()` means an ordinary load with no policy;
  every manifest-created tracker is explicit, including an empty manifest.
- ICZ2 exact-profile validation is therefore applied to every supplied
  handoff. An explicitly empty ICZ2 handoff fails with all three mandatory
  descriptors missing, while an ordinary ICZ2 load still publishes all
  secondary resources synchronously.
- Removed the AIZ and ICZ pre-publication `payloadVisible` predicates. AIZ
  snapshots its complete affected chunk and pattern ranges before the initial
  queue-submission scan and proves byte-for-byte stability after every
  intermediate consumer, PRE, and POST boundary up to each owning fence.
- ICZ constructs an independently deferred ICZ2 level as the expected
  post-reload baseline. The in-frame reload and every later PRE, POST, and
  consumer scan must match the complete block, chunk, and pattern snapshots;
  absent pre-publication table entries are represented explicitly so any
  prefix capacity growth or write also fails.
- Publication checks compare every ROM payload word/pixel against the
  decompressed expected bytes, then snapshot the published ranges to prove
  later scans leave them unchanged. Sanity assertions establish that each
  complete pre-publication range differs from its expected published state.

### Round-three RED/GREEN evidence

- RED: the policy-presence tests failed compilation because the tracker had no
  explicit-policy contract. Before the fix, the new ROM-backed empty-handoff
  assertion would also have observed a successful ICZ2 load.
- GREEN: the policy/loading pair completed with 10 tests, all passed.
- GREEN: the complete round-three focused set completed with 51 tests, all
  passed, with no failures or errors in the requested Surefire reports.
- Prescribed six-class comparison remains 112 tests, 99 passed, 13 failed,
  0 errors. The 11 AIZ and two fixed-air failures are unchanged from the
  round-two comparison and introduce no new regression against the exact
  `d230501d4` baseline.
