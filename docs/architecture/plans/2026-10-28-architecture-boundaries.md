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
