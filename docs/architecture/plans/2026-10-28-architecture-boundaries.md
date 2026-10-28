# Architecture boundary remediation

The user requested remediation of the October 28 architecture review and delivery
to `develop`. The integration base is
`2f9ca84b18c8bae45a298348676e28d0a91a0798`. The task branch is
`bugfix/ai-architecture-boundaries`, in `.worktrees/ai-architecture-boundaries`.
Existing main-workspace changes and optional submodules remain outside this task.

## Ownership decisions

- Bonus-stage object services forward failures to the existing creator callback
  and engine frame boundaries. Removing blanket catches avoids a second recovery
  policy in adapters whose provider methods have no recoverable checked errors.
  A separate typed catch would preserve the logging fallback but leave ordinary
  provider failures silently accepted.
- Object ROM access resolves the owning world's `GameDataSource`. Its existing
  legacy source retains late process-ROM registration. Explicit pinned and
  ROM-free sources retain their declared capabilities. Donor ROM access remains
  explicit through its existing service.
- Rewind restore admission uses the existing callback-bearing fade classification.
  Unsafe seeks report failure, backward steps refuse, and audits skip unsafe
  endpoints. This does not make replayed callbacks transactional or capture
  closures. A new public snapshot-eligibility interface would expand the API for
  a single known owner and is unnecessary for this correction.
- Retiring a pending preparation joins its running work before resources can
  close. The pending future is the resource-lifetime handle. An immutable detached
  ROM/loader rewrite would preserve nonblocking retirement but require a larger
  change to captured loader inputs and caches. Retirement may wait for an active
  decode; it cannot safely release that decode's resources first.
- World metadata setters retain their signatures and enforce the existing
  engine-caller boundary. Removing the setters would require a candidate API
  migration without improving this bounded ownership correction. Creator code
  reads world metadata and requests changes through level services.

## Throughput and validation

Two standard-speed delegates author independent service and rewind regressions.
The lead owns preparation/world-boundary tests, implementation composition,
all Maven runs, review, integration and cleanup. Workers do not launch broad
checks. One shared worktree has one validation observer.

The critical path is a combined failing regression run, bounded runtime fixes,
focused green verification, independent review, then one change-based smoke run
with two workers and separate guards. Queue waiting remains pending work and is
excluded from execution estimates. The repository's qualified broad recipe
targets 30 minutes of execution; actual cost and admission delay are unknown
until the run completes. New failures block delivery until fixed or attributed
by exact matched baseline/current checks. Unchanged coverage is not repeated
as a baseline/development/integration ceremony.

Preflight passed with `JAVA_HOME=/usr/lib/jvm/java-25-openjdk` and
`LUA_BIN=/usr/bin/lua5.4`. The ambient `lua` executable was not Lua 5.4.

The initial focused request completed against unchanged production source with
33 tests, 17 failures, zero errors and zero skips. Admission waited 374 seconds;
Maven reported 1 minute 24 seconds for build and execution. XML reports were
inspected by exact class and method. Commit `07b5b7b17` preserves these regressions
before runtime fixes.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestObjectServicesModAbortBoundary,TestObjectServicesExpansion,TestRewindFadeRestoreSafety,TestLevelLoadPreparer,TestWorldSessionMutationAuthority' test
```

The observed failures were:

- `TestObjectServicesModAbortBoundary`: all three parameterized bonus actions
  completed the failed frame and omitted discard/title recovery.
- `TestObjectServicesExpansion`: both pinned-source accessor cases read 34
  instead of 17; `defaultObjectServices_noRomWorldCannotBorrowTheProcessRom`
  returned the process ROM instead of throwing.
- `TestRewindFadeRestoreSafety`: all eight refusal/audit cases failed. The safe
  crossing control passed. Unsafe operations changed snapshots, callbacks,
  cursor or history rather than refusing them.
- `TestLevelLoadPreparer`: `retirementWaitsUntilRunningBuildReleasesItsResources`
  and `interruptedRetirementStillWaitsAndPreservesInterruption` returned before
  the blocked decoder finished.
- `TestWorldSessionMutationAuthority`:
  `creatorCannotBypassTheLevelLifecycleThroughWorldSetters` admitted the creator
  write instead of raising `SecurityException`.

## Checkpoint

The service, preparation and world-metadata patches are applied. The rewind patch
is applied and independent review found no blockers. A further preparation regression covers an
interrupted `take` falling back synchronously only after its old task finishes.
The expanded twelve-class focused request ended with signal 143 before admission,
after its last reported wait of 331 seconds. The cause was not recorded and no
tests ran. That ended request was resubmitted with live queue/summary output.
The replacement passed 90 tests across eleven executed classes, with zero failures,
errors or skips. It waited 140 seconds for admission; Maven reported 1 minute
19 seconds for build and execution. The selected `TestHeldRewindAudioStepCost`
measurement harness remained excluded by its `performance-measurement` tag.
`TestRewindController` executed the warm-cache capture/floor-lookup behavioral
control. No performance measurement is claimed.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestObjectServicesModAbortBoundary,TestObjectServicesExpansion,TestEngineModCallbackAbortBoundary,TestRewindFadeRestoreSafety,TestRewindController,TestRewindDeterminismAuditor,TestRewindControllerAudioSuppression,TestHeldRewindAudioRestoreDeferral,TestHeldRewindAudioStepCost,TestLevelLoadPreparer,TestWorldSessionMutationAuthority,TestGameDataSourceSessionOwnership' test
```

Independent review found no blockers. The conservative backward clamp now stops
at frame 35 in the existing callback test: reconstructing frame 34 would first
restore unsafe keyframe 30. Seek refusal also restores logical audio and retains
future command/keyframe history. Public API signatures are unchanged.

The change-based smoke plan selects 4,241 candidate classes and separate guards,
using two ordinary workers, explicit surfaceless EGL and verified ROM discovery
from the main workspace. This remains bounded routine coverage. Combined smoke,
integration, push and cleanup remain.

## Interrupted combined run and upstream reconciliation

Run `20261028T093137Z-8fad3d06` tested `c9d9243c8` with the complete
4,241-candidate selection. Preparation passed in 30.79 seconds. The first test
admission waited 533 seconds, with later reported waits of 31, 41, 93 and 37
seconds. The process ended with interruption/exit 130 while the sixtieth batch
was reporting. Its retained summary records 59 of 85 completed batches, 24,975
tests, one failure, zero errors, 39 skips and 649.56 seconds of test execution.
Guards did not run. The sender/cause of the interruption is unknown; this is
incomplete coverage, not a category pass.

The failure is
`TestCnzMinibossRegistered.numericSlotIsImplementedInBothPointerTables`, line 36,
`expected: <true> but was: <false>`, asserting membership in
`Sonic3kObjectProfile.SHARED_IMPLEMENTED_IDS`. Both source files are unchanged
from the original base. Exact matched attribution is pending. All 39 skip
records were inspected; reasons were explicit capture, measurement, soak,
isolated OpenAL or external sample-mod prerequisites, rather than missing stock
ROMs. No failure/skip records were omitted. Diagnostics were acknowledged and
removed after inspection.

During this run, `develop` advanced to `13b908d2d` through the separate scoped
native-salvage delivery. Its codec snapshot equality/hash, attached-manager
registry cleanup and camera changes are retained. Merging it into this task
branch was automatic, including the changelog; no source conflict was resolved
by dropping either change. Combined verification must use the reconciled tree.

## Completed verification of the reconciled candidate

Run `20261028T100429Z-046d1c88` completed against the clean reconciled source
`efdb90f5dd11b416c9ee1c0e97a39794137a3dd6`. The complete change-based selection
contained 4,242 candidate classes. All 85 ordinary batches and the separate fresh
guard JVM completed:

| Lane | Reported tests | Failures | Errors | Skips | Maven execution |
| --- | ---: | ---: | ---: | ---: | ---: |
| Ordinary smoke | 35,131 | 1 inherited | 0 | 49 | 1,465.76 seconds |
| Guards | 808 | 3 inherited | 0 | 0 | 160.24 seconds |

Preparation took 29.57 seconds. Execution including preparation totaled
1,655.57 seconds. The existing queue telemetry contains all 86 batch/guard leases,
with 827.1 seconds of admission waiting. The timestamped run interval was about
2,493.1 seconds, or 41 minutes 33 seconds. The remaining interval outside reported
Maven/preparation execution and admission is unattributed. Parallel JUnit class
times are not added to these elapsed durations.

```bash
OPENGGF_MAIN_ROOT="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py \
  --base 2f9ca84b18c8bae45a298348676e28d0a91a0798 --workers 2 \
  --headless-gl surfaceless-egl \
  --rom-directory "$OPENGGF_MAIN_ROOT" --run
```

All four failure identity, kind, exception-type and message tuples matched
independently executed original-base XML exactly:

- `TestCnzMinibossRegistered.numericSlotIsImplementedInBothPointerTables`:
  the unchanged shared-set assertion, expected true but false.
- `TestRewindCoverageGuard.noNewCoverageGapsBeyondBaseline`:
  `MhzEndBossInstance#objectRef#hitProxy`.
- `TestBuildToolingGuard.s3kStaticSessionStateDebtShouldNotGrow`:
  `HCZWaterSkimHandler.slotRetired` and
  `Aiz2BossEndSequenceState.cutsceneKnucklesSlot`.
- `TestNoAssertionFreeDiagnostics.noAssertionFreeTestMethodsUnderTestsTree`:
  the existing `TestTestReelExtension#bodyPasses` fixture.

The original-base CNZ control ran two tests with one failure, zero errors/skips;
Maven took 118 seconds with immediate admission. The reconciled nine-class cohort
ran 71 tests with only that same failure and zero errors/skips; Maven took
97 seconds with immediate admission. Its other 70 cases passed, including the
incoming codec, registry-cleanup and camera controls and the affected remediation
consumers. The baseline three-method guard control failed those exact three
methods with zero errors/skips; Maven took 30.913 seconds after 21 seconds of
admission waiting. No registry, coverage, static-debt or assertion allowance was
changed to hide inherited failures.

The bounded selectors, using the Java/Lua environment above and
`-Dopenggf.headless.gl=surfaceless-egl`, were:

```bash
# Original-base control, with --lean.
python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestCnzMinibossRegistered' test
# Reconciled focused cohort, with --lean.
python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestCnzMinibossRegistered,TestCodecFieldSnapshotEquality,TestLevelManagerZoneScopedRegistryReset,TestSonic3kLevelStartCamera,TestRewindController,TestRewindFadeRestoreSafety,TestLevelLoadPreparer,TestObjectServicesExpansion,TestWorldSessionMutationAuthority' test
# Original-base structural failure controls in a fresh guard JVM.
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Pguards '-Dtest=TestRewindCoverageGuard#noNewCoverageGapsBeyondBaseline,TestBuildToolingGuard#s3kStaticSessionStateDebtShouldNotGrow,TestNoAssertionFreeDiagnostics#noAssertionFreeTestMethodsUnderTestsTree' test -B
```

All 49 ordinary skip records were inspected, with no omitted skip/failure records
in either lane. Forty-eight skips require explicit diagnostics, captures,
measurements, soak/reference setup, isolated OpenAL or external sample-mod inputs.
The remaining skip is the existing CPZ traversal fixture
`TestCPZObjectBugs.testSpinTubeForcesRolling`: the tube at `(1920,896)` did not
capture/release Sonic. Exact base/current checks each ran one test with one skip,
zero failures/errors, and the same causal line. They used the original absolute
S2 REV01 ROM path and this selector:

```bash
OPENGGF_S2_ROM="$OPENGGF_MAIN_ROOT/"'Sonic The Hedgehog 2 (W) (REV01) [!].gen'
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dsonic2.rom.path=$OPENGGF_S2_ROM" '-Dtest=TestCPZObjectBugs#testSpinTubeForcesRolling' test
```

That skip remains an inherited coverage gap. It is not counted as a passing
interaction. Bounded controls are separate invocations and overlap smoke coverage;
their counts are not added to the smoke total. Release-only, performance and
exhaustive trace campaigns remain outside this delivery.

Model the Domain retained the existing world-source and fade-snapshot types.
Boundary Discipline kept callback recovery with the frame owner. Laziness
Protocol removed fallback catches without adding public APIs. Test Behavior,
Not Implementation used real callback execution and observable ROM/state/history
effects. Sequence Work into Verifiable Units preserved the failing regressions
before their fixes; Prove It Works required executed checks and exact baseline
comparisons.

The final documentation update records evidence only. Integration preserves the
validated executable tree and the existing main-workspace changes. The delivery
reply records the actual pushed commit and cleanup outcome after those operations.
