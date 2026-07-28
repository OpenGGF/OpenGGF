# Task 3 report — KosM parents over shared direct children

## Delivered

- Refactored `S3kKosModuleQueue` into a POST-owned parent coordinator with no
  embedded standard-Kosinski decoder.
- Each exact, 16-byte-aligned KosM child stream is submitted to the one
  session-owned `S3kKosDecompressionQueue` at the canonical ROM source and
  `Kos_decomp_buffer` destination.
- Parent service performs one transition per POST call: defer, submit one
  child, claim one ready child, or prepare the final archive payload.
- Parent output is assembled only from claimed direct payloads. Shared direct
  capacity and the complete physical-FIFO empty predicate gate submission and
  advancement.
- Module snapshots now preserve active child handle and compressed span beside
  aggregate output, allowing exact restore after physical retirement and
  before the parent claim.
- Gameplay mode/frame wiring owns one direct queue and one central module
  coordinator. Production and object callers resolve that exact coordinator;
  no timing-only facade can pair a parent ledger with another session's direct
  owner. PRE ordering admits recorded direct edges before physical
  retirement; POST ordering prepares the parent before recorded module
  admission.
- Corrected resumable Kosinski descriptor refill to match the synchronous ROM
  decoder: the next descriptor word is fetched immediately after consuming
  bit 16, before that command's data bytes.
- AIZ and ICZ publication/transition consumers were not migrated in this task.

## RED/GREEN evidence

- RED: the corrected descriptor-refill fixture failed with
  `Unexpected end of Kosinski module`; after the production refill fix, the
  decoder suite and real title-card archive passed.
- RED: the first composition test failed to compile because the module queue
  had no shared direct-owner constructor or active-child snapshot field.
- GREEN: frame-by-frame tests cover child submission, PRE preparation,
  one-child POST claim, following-POST next-child submission, full-capacity
  deferral, ordinary tail blocking, schema-1 live children, and rewind from a
  ready-unclaimed direct child.

## Verification

Required command, with the verified S3K ROM property:

`mvn -Dmse=off "-Ds3k.rom.path=/home/farrell/code/projects/OpenGGF/s3k.gen" "-Dtest=TestS3kKosModuleQueue,TestS3kKosModuleReadiness,TestS3kKosStructuralSequence,TestS3kKosTimingRewindIntegration" test`

Result: 15 tests, 15 passed.

Expanded regression/guard command:

`mvn -Dmse=off "-Ds3k.rom.path=/home/farrell/code/projects/OpenGGF/s3k.gen" "-Dtest=TestS3kKosModuleQueue,TestS3kKosModuleReadiness,TestS3kKosStructuralSequence,TestS3kKosTimingRewindIntegration,TestS3kKosDecompressionQueue,TestS3kKosDecompressionQueueLifecycle,TestResumableKosinskiDecoder,TestLevelFrameHardwareTimingBoundaries,TestGameplayModeContextRewindRegistry,TestSonic3kTitleCardKosQueue,TestHardwareTimingAuthorityGuard,TestHardwareTimingRewind,TestStaticStateRewindCoverageGuard" test`

Result: 85 tests, 85 passed.

Initial legacy schema-1 replay audit, before the review-fix round below:

`mvn -Dmse=off "-Ds3k.rom.path=/home/farrell/code/projects/OpenGGF/s3k.gen" "-Dtest=TestS3kHardwareTimingReplay" test`

Result: 4 tests, 3 failures and 1 error. The three isolated tests still drive
only generic timing boundaries and therefore do not invoke the new production
module coordinator. The production frame-driver case reaches its recorded
schema-1 module edge before the live direct children have prepared the parent
and correctly rejects it as `engine job is not prepared`. The approved design
classifies direct-sensitive schema-1 fixtures as loader-compatible but not
valid synchronization gates until schema-2 regeneration; Task 8 owns that
fixture/publication approval gate. Production cadence was not tuned to the old
recorded frame, and recorded authority was not allowed to prepare work.

## Review checklist

- No private module decoder remains.
- Direct physical capacity and empty state have one owner.
- Generic budget scheduling cannot advance a module parent; coordinator state
  advances only from the S3K POST owner.
- Generic timing authority remains limited to preparation capture and
  readiness admission; it has no S3K parent/child knowledge.

Independent review initially found an unsafe timing-only constructor and the
legacy schema-1 replay incompatibility. The constructor was removed and all
production callers now resolve the matched session coordinator. The schema-1
incompatibility is retained and reported for Task 8, as required by the
approved compatibility design.

## Review-fix round

The follow-up review identified four additional ownership gaps. Each was
covered test-first and resolved:

- A zero-module KosM parent no longer starts prepared in its constructor.
  PRE and VINT leave it untouched; the POST coordinator owns its sole
  preparation transition.
- `S3kKosModuleQueue` now rejects a direct queue backed by a different
  `HardwareTimingService`, preventing split parent/child ledgers.
- A stronger full-FIFO test proves a child submitted behind ordinary work
  cannot advance its parent until the complete physical direct FIFO drains,
  followed by the correct later POST.
- The three isolated schema-1 replay helpers now drive the production
  boundary choreography: POST parent coordination and PRE direct admission
  and physical retirement. Their readiness assertions remain unchanged.

Focused RED result: three new tests ran; the zero-module and mismatched-ledger
tests failed, while the stronger ordinary-ahead case already passed. Focused
GREEN result: all three passed.

Required suite after the review fixes: 18 tests, 18 passed.

Schema-1 replay after the review fixes: 4 tests, 3 passed and 1 errored. The
sole remaining error is
`standaloneAizCompleteRunConsumesFirstEdgeThroughProductionFrameDriver`, where
the old raw-frame-73 module edge attempts admission before the production
child chain has prepared `KOS_MODULE_QUEUE#2`. This is the previously approved
Task 8 fixture-regeneration concern; neither production cadence nor recorded
authority was changed to accommodate it.
