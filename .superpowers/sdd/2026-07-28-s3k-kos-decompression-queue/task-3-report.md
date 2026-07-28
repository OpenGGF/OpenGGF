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

Legacy schema-1 replay audit:

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
