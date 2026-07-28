# S3K direct Kosinski queue Task 7 validation

Date: 2026-07-28

## Outcome

Task 7 completed the required `LevelManager` extraction, repaired three stale
verification harnesses, and ran the focused Java and native matrices. Focused
queue/timing tests and both native `--no-gates` modes pass. End-to-end
publication and integration are not green:

- the committed schema-1 ICZ complete-run fixture admits
  `KOS_MODULE_QUEUE#158` before the engine's parent is prepared, which is the
  expected schema-2 publication gate;
- the full Java suite contains unresolved queue-integration and architecture
  failures in addition to known baseline failures; and
- both full-suite attempts produced all 1,724 Surefire reports and then left a
  stale execution handle after the Maven/Java processes had disappeared.

No trace fixture or disassembly content was changed.

## Delivered Task 7 commits

- `7cca4692d refactor: extract level pattern locator`
- `6c7d20200 test: align S3K queue verification harnesses`

`LevelPatternLocator` now owns the former pattern search implementation.
`LevelManager.findPatternOffset(...)` remains a thin public delegate with
unchanged search and coordinate semantics. The size guard no longer reports
`LevelManager`; its frozen limit remains 2,500 effective lines.

The repaired harnesses:

- prepare admission-order KosM work through the runtime direct/module queues,
  instead of submitting a fabricated raw timing job;
- assert the seamless-transition handoff rewind key and the resulting ten-key
  gameplay registry; and
- service only AIZ fire-transition queue tests in production order: timing
  service, direct FIFO retirement, then KosM parent coordination.

The ordinary AIZ intro/save helper remains unchanged.

## Environment and ROM identity

```text
Apache Maven 3.9.16
Java version: 21.0.11
```

| Game | File | CRC32 | SHA-1 |
|---|---|---|---|
| Sonic 1 World REV01 | `Sonic The Hedgehog (W) (REV01) [!].gen` | `AFE05EEE` | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| Sonic 2 World REV01 | `Sonic The Hedgehog 2 (W) (REV01) [!].gen` | `7B905383` | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| Sonic 3&K locked-on | `Sonic and Knuckles & Sonic 3 (W) [!].gen` | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |

BizHawk 2.11 was supplied from
`docs/BizHawk-2.11-linux-x64`.

## TDD and focused Java verification

The locator test was first observed RED at test compilation because
`LevelPatternLocator` did not exist. After extraction:

```text
mvn -Dmse=off "-Dtest=TestLevelPatternLocator" test
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

The focused source guard then reported only the existing `GameLoop`
3,014 > 3,005 and `AbstractPlayableSprite` 3,164 > 3,159 overages.
`LevelManager` was absent from the failure.

The admission-order harness was observed RED with one error in three tests:
the raw fake parent had an unexpected preparation owner. The gameplay rewind
registry was observed RED because its expected nine keys became ten. After
the runtime-owned harness repairs:

```text
mvn -Dmse=off \
  "-Dtest=TestLevelIterationHardwareTimingAdmissionOrder,TestGameplayModeContextRewindRegistry" \
  test
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
```

The focused timing/authority matrix passed:

```text
mvn -Dmse=off "-Ds3k.rom.path=<locked-on-rom>" \
  "-Dtest=TestHardwareTimingService,TestHardwareTimingRewind,TestHardwareTimingStreamLoader,TestHardwareTimingReplayPort,TestHardwareTimingAuthorityGuard,TestTraceDataHardwareTiming,TestCommittedHardwareTimingFixtures,TestLevelFrameHardwareTimingBoundaries,TestLevelIterationHardwareTimingAdmissionOrder,TestRecordingFrameDriverHardwareTiming" \
  test
Tests run: 99, Failures: 0, Errors: 0, Skipped: 0
```

The direct/module structural matrix passed 18/18. The queue/rewind matrix
passed 22 of 23 tests; its sole failure was the known unrelated
`Flybot767BadnikInstance#finalScalar#layoutWaitUsesRetainedRenderFlag`
rewind-coverage gap.

The exact AIZ/ICZ/rewind-registry matrix was:

```text
mvn -Dmse=off "-Ds3k.rom.path=<locked-on-rom>" \
  "-Dtest=TestSonic3kAIZEvents,TestSonic3kIczRewindRoundTrip,TestSonic3kIczSlideTerrain,TestSonic3kLevelEventRewindSnapshot,TestGameplayModeContextRewindRegistry" \
  test
Tests run: 112, Failures: 6, Errors: 0, Skipped: 0
```

Both ICZ classes passed 10/10 and the registry passed 19/19. The six remaining
failures match the known branch baseline:

- three AIZ intro assertions;
- `eventsFg5TransitionWritesProgressionSaveForActiveSlot`, whose harness
  clears the live level;
- `roundTripFixedAirCountdownSidecarRam`; and
- `legacyFixedAirSnapshotClearsUnrepresentedOwner`.

The scoped AIZ repair reduced this matrix from its initial 13 failures to
those six without changing the unrelated helpers.

The committed-fixture inventory passes independently:

```text
mvn -Dmse=off "-Dtest=TestCommittedHardwareTimingFixtures" test
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

The complete ICZ replay reaches the required publication gate:

```text
mvn -Dmse=off "-Ds3k.rom.path=<locked-on-rom>" \
  "-Dtest=TestS3kIczCompleteRunTraceReplay" test
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
```

The error is:

```text
expected completion: KOS_MODULE_QUEUE#158
sha256:9d76abb5369bb27fca1574b28bf37d429af1904dbd45edabef04fd0ab1e0f594
engine job is not prepared
```

The committed fixture is schema-1 load-only compatibility and lacks the
schema-2 direct FIFO authority needed to admit that parent edge. This is a
publication blocker, not permission to edit or replace the fixture.

## Full Java suite

The full suite was run twice with all three verified ROM properties. Before
the second run, the first run's Surefire reports were moved intact to
`/tmp/openggf-task7-surefire-reports-pty-20260728-2010` and Maven generated a
fresh report directory.

Both attempts generated 1,724 `TEST-*.xml` reports and last wrote
`TestRewindTorture`. In each attempt Maven, Java, Surefire, Mono, and
PulseAudio processes had exited, but the execution handle did not close and
had to be interrupted. The fresh report totals are:

```text
Tests represented: 13,484
Failures: 71
Errors: 30
Skipped: 31
Execution result: infrastructure-incomplete; stale handle terminated
```

The first attempt represented the same 13,484 tests with 72 failures,
30 errors, and 31 skipped. The one-failure variation is consistent with the
existing broad-suite instability and does not turn either attempt into a
completed Maven result.

Known baseline signatures include the two large-class source-guard failures,
the four AIZ intro/save failures, the two FixedAir snapshot failures, the
Flybot rewind gap, and the stale AIZ mutation-pipeline source assertion.

The fresh run reports failures or errors in 48 classes. Several are directly
related to unresolved queue integration and must remain attributable until
fixed or baseline-proven:

- `TestArchUnitRules`, `TestZoneEventRuntimeAccessGuard`, and
  `TestPlayableRuntimeAccessGuard` report new concrete/runtime dependency
  edges;
- `TestS3kObjectKosOwnerRewind` and `TestS3kResultsKosQueueRewind` report
  queue-owner rewind harness failures;
- results-screen and boss harnesses report unavailable KosM coordination;
- AIZ/HCZ headless harnesses report full module FIFOs or unpublished queue
  work; and
- title-card/headless progression harnesses fail to release while their
  initial Kos work remains pending.

Other broad-suite failures span pre-existing movement, rewind, lifecycle,
object-registration, and architecture baselines. No green baseline full-suite
run was produced during Task 7, so failures outside the explicitly known
baseline list are unresolved rather than claimed pre-existing. The full suite
therefore does not support an integration-complete claim.

## Native recorder verification

Focused event-engine verification:

```text
BIZHAWK_HOME=<bizhawk> ./test.sh \
  --filter HardwareTimingEventEngine --jobs 1
15 passed; unrelated GpgxHost test skipped because S1_ROM_PATH was absent
```

Without ROM environment variables:

```text
env -u S1_ROM_PATH -u S2_ROM_PATH -u S3K_ROM_PATH \
  BIZHAWK_HOME=<bizhawk> ./test.sh --no-gates
408 total: 378 passed, 0 failed, 30 skipped
```

With all three verified ROM environment variables:

```text
BIZHAWK_HOME=<bizhawk> \
S1_ROM_PATH=<rev01-s1> \
S2_ROM_PATH=<rev01-s2> \
S3K_ROM_PATH=<locked-on-s3k> \
  ./test.sh --no-gates
410 total: 410 passed, 0 failed, 0 skipped
```

The no-gates native implementation is green in both required modes. The
schema-1 fixture publication boundary remains intentionally visible in the
Java complete-run replay and was not bypassed.

## Repository integrity

`git diff --name-only -- src/test/resources/traces` produced no output.
`git diff --check` passed before the harness commit. No ROM, trace fixture, or
disassembly bytes were renamed, copied, deleted, linked, or modified.

The full suite regenerated `docs/status/rewind-round-trip-gaps.md`; that
test-generated report is not a Task 7 deliverable and is not staged with this
validation report.

