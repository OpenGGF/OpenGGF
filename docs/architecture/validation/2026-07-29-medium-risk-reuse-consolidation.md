# Medium-Risk Reuse Consolidation Validation

**Date:** 2026-07-29
**Branch under validation:** `feature/ai-reuse-consolidation-next`
**Base:** `8c9b7378b3bf255bd979292c61a4b8584272c12c`

## Environment

Validation used Maven 3.9.16 on OpenJDK 21.0.11 (`mvn -v`) and the discovered
ROM links:

- Sonic 1: `/home/farrell/code/projects/OpenGGF/s1.gen`
- Sonic 2: `/home/farrell/code/projects/OpenGGF/s2.gen`
- Sonic 3 & Knuckles: `/home/farrell/code/projects/OpenGGF/s3k.gen`

Each command supplied the links through `sonic1.rom.path`, `sonic2.rom.path`,
and `s3k.rom.path` respectively.

## Focused tranche suite

The branch ran the prescribed command:

```bash
mvn -Dmse=off \
  -Dsonic1.rom.path="$S1_ROM" \
  -Dsonic2.rom.path="$S2_ROM" \
  -Ds3k.rom.path="$S3K_ROM" \
  -Dtest=com.openggf.game.TestBuiltInRomDetectors,com.openggf.game.TestRomDetectionService,com.openggf.game.TestHeaderNameRomDetectors,com.openggf.tests.rules.TestRomCacheAvailability,com.openggf.tools.TestCliArguments,com.openggf.tools.TraceCaptureToolArgsTest,com.openggf.tools.TestTraceBenchmarkToolArgs,com.openggf.tests.trace.TestRecordedInputRows,com.openggf.game.TestSpecialStageInputMapper,com.openggf.game.rewind.TestLiveRewindLogicalInput,com.openggf.tests.trace.s1.TestS1SpecialStageTraceReplay,com.openggf.tests.trace.s2.TestS2SpecialStageTraceReplay,com.openggf.tests.trace.s2.S2SpecialStageReplayDeterminismTest,com.openggf.tests.trace.s3k.TestS3kSpecialStageTraceReplay,com.openggf.tests.trace.runs.TestS1GhzMazeRoundTripChain,com.openggf.tests.trace.runs.TestS2EhzHalfpipeRoundTripChain,com.openggf.tests.TestArchUnitTestRules,com.openggf.tests.TestArchUnitRules \
  test
```

| Tests | Failures | Errors | Skipped | Maven exit |
|---:|---:|---:|---:|---:|
| 106 | 0 | 0 | 0 | 0 |

This validates the detector delegation, shared CLI parsing, reusable recorded
input rows, special-stage/round-trip paths, logical input, and architecture
guards selected for this tranche.

## Clean full-suite comparison

Both revisions ran the same clean command with the three ROM properties:

```bash
mvn -Dmse=off \
  -Dsonic1.rom.path="$S1_ROM" \
  -Dsonic2.rom.path="$S2_ROM" \
  -Ds3k.rom.path="$S3K_ROM" \
  clean test
```

| Revision | XML suites | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Base `8c9b7378b` | 1,728 | 13,522 | 13 | 1 | 31 |
| Branch `ab177212a` before Task 4 documentation | 1,731 | 13,548 | 4 | 1 | 31 |

Each Maven invocation reached its known idle post-suite shutdown tail after
Surefire had written the complete XML set. After confirming the report count
and stable final report timestamps, only that idle Maven tail was interrupted.
The table is aggregated from `target/surefire-reports/TEST-*.xml`; the shell
process therefore exits 130 after interruption, not as a statement about the
test execution itself.

The shared base/branch failure set is one `TestGameLoop` assertion
(`traceRealtimeRewindRunsBeforePlaybackInputBridge`, expected `true`, got
`false`) plus one `TestGameLoop` `StringIndexOutOfBoundsException` in
`setupAdmissionPrecedesSeamlessBoundaryAndTraceCameraMutations` (`Range
[34669, -1) out of bounds for length 191573`).

The base additionally reports 12 `TestS3kSnaleBlasterBadnik` reflection and
registration failures. Those failures are absent on the branch. The branch
instead reports three suite-only failure cases absent from the base:

- `TestPlayableSpriteRollSpeed.s3kTailsStopsRollingBelowMinimumRollSpeedThreshold`
  (expected ground velocity `65409`, actual `0`);
- `TestLiveTraceComparatorObserver.existingFiveArgConstructorDelegatesWithNullObserver`
  (expected `0`, actual `2`); and
- `TestLiveTraceComparatorObserver.nullObserverIsHonoured` (expected `0`,
  actual `2`).

## Failure investigation and deferred scope

The branch-only cases were rerun together, without `clean`, using:

```bash
mvn -Dmse=off \
  -Dsonic1.rom.path="$S1_ROM" \
  -Dsonic2.rom.path="$S2_ROM" \
  -Ds3k.rom.path="$S3K_ROM" \
  -Dtest=com.openggf.tests.TestPlayableSpriteRollSpeed,com.openggf.trace.live.TestLiveTraceComparatorObserver \
  test
```

That isolated run completed with 9 tests, 0 failures, 0 errors, and 0 skips.
The affected test sources are not directly modified by the tranche. The
evidence indicates an unresolved suite-order or shared-state interaction, not
a standalone failure of either class. It is intentionally deferred for final
branch-review triage; this validation record does not claim that the branch is
free of full-suite regressions.

The test-generated `docs/status/rewind-round-trip-gaps.md` was restored to its
tracked content after branch validation. The pre-existing dirty copy in the
main workspace was backed up before its baseline run and restored with the
same SHA-256 (`dec9526b5a99360ab99b477d8af3fe3bb3e2e8ee39e7bfdf88dc599fd29c085f`).
The Task 1 frozen-ArchUnit-baseline minor remains untouched for final-review
triage.

## Review finding

The focused tranche is green, but the clean comparison has three branch-only,
full-suite-only failures. Final review must trace the order/state interaction
and either eliminate it or establish an evidence-backed pre-existing cause
before treating this consolidation as behavior-neutral for release purposes.

## Diff and staging checks

Before staging, `git diff --check`, `git diff --cached --check`, and
`git status --short` found no whitespace defect in the tracked branch changes;
the only pre-existing worktree entries were local disassembly directories.
After staging the README and this record, both diff checks passed and the index
contained only those two Task 4 files. `git diff --check HEAD^..HEAD` also
passes for the documentation commit itself.

The required wider `git diff --check 8c9b7378b..HEAD` still reports trailing
whitespace in the earlier Task 1 design record
`docs/architecture/designs/2026-07-29-medium-risk-reuse-consolidation.md`.
That pre-existing range defect is deliberately left for the reserved final
review triage; Task 4 does not modify it or the logged frozen-ArchUnit-baseline
minor.
