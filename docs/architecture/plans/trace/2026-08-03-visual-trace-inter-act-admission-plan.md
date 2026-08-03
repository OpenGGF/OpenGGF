# Visual Trace Inter-Act Admission Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent a visual whole-run destination level from opening comparison, input, timing, or dynamic-art ownership until its production initial title card has released.

**Architecture:** Extend the shared `RunPlaybackObservation` with the live, value-free initial-title-card barrier and make `TraceRunPlaybackCoordinator` treat it as part of level destination readiness. Populate the field in both visual and headless adapters, keeping the existing load receipt remembered across the title-card gap and admitting at the existing pre-production callback without another level load.

**Tech Stack:** Java 21, JUnit 5, Maven Surefire, existing visual/headless trace replay coordinator and production lifecycle services.

## Global Constraints

- Preserve the engine-created level load and production title card; never request a second load.
- Trace physics, aux, and manifest data remain comparison-only and cannot drive the readiness barrier.
- Add no game, zone, route, trace-name, or frame-number carve-out.
- Keep the source-ownership failure invariant unchanged.
- Preserve immediate admission for presentation-suppressed and seamless/no-card destinations.
- Update `docs/status/trace-frontier-log.md` with exact commands and results.
- Use JUnit Jupiter only and build on JDK 21.

---

### Task 1: Add the structural title-card barrier contract

**Files:**
- Modify: `src/main/java/com/openggf/trace/replay/runs/RunPlaybackObservation.java`
- Modify: `src/test/java/com/openggf/tests/trace/runs/TestTraceRunPlaybackCoordinator.java`
- Modify: all existing `new RunPlaybackObservation(...)` call sites under `src/main/java` and `src/test/java`

**Interfaces:**
- Produces: `RunPlaybackObservation.initialTitleCardPending(): boolean`.
- Consumes: no trace data; synthetic observations explicitly choose `true` or `false`.

- [x] **Step 1: Write the observation-contract RED test**

Add a reflection assertion to `TestTraceRunPlaybackCoordinator` before changing the record:

```java
@Test
void observationCarriesInitialTitleCardProductionBarrier() {
    assertTrue(Arrays.stream(RunPlaybackObservation.class.getRecordComponents())
            .anyMatch(component -> component.getName()
                    .equals("initialTitleCardPending")));
}
```

- [x] **Step 2: Run the contract test and verify RED**

Run:

```bash
mvn -q -Dmse=off \
  -Dtest='com.openggf.tests.trace.runs.TestTraceRunPlaybackCoordinator#observationCarriesInitialTitleCardProductionBarrier' test
```

Expected: assertion failure because the record has no `initialTitleCardPending` component.

- [x] **Step 3: Add the record component and update constructors mechanically**

Insert `boolean initialTitleCardPending` immediately after `LevelIdentity level` so it remains adjacent to the lifecycle it qualifies:

```java
public record RunPlaybackObservation(
        GameMode mode,
        int sharedBk2Cursor,
        long admittedStepOrdinal,
        LevelIdentity level,
        boolean initialTitleCardPending,
        BonusIdentity bonus,
        Integer specialStageIndex,
        boolean productionOpen,
        boolean currentSegmentExhausted,
        int destinationRowsConsumed,
        boolean lagOnlySameLevelContinuation,
        long timingScheduleGeneration,
        long dynamicArtGeneration) {
```

Update every constructor call explicitly. Use `false` for existing synthetic bonus/special/ordinary observations; do not add a compatibility constructor that silently defaults the new ownership state.

- [x] **Step 4: Run the coordinator class and verify GREEN**

Run:

```bash
mvn -q -Dmse=off \
  -Dtest='com.openggf.tests.trace.runs.TestTraceRunPlaybackCoordinator' test
```

Expected: all coordinator tests pass, proving the contract change is complete before behavior changes.

### Task 2: Gate level admission through the shared coordinator

**Files:**
- Modify: `src/main/java/com/openggf/trace/replay/runs/TraceRunPlaybackCoordinator.java`
- Modify: `src/test/java/com/openggf/tests/trace/runs/TestTraceRunPlaybackCoordinator.java`

**Interfaces:**
- Consumes: `RunPlaybackObservation.initialTitleCardPending()`.
- Produces: no action while a matching loaded level still has an initial title-card owner; the remembered load remains eligible after the barrier clears.

- [x] **Step 1: Write the exact source-tail ordering RED test**

Extend the existing remembered-load scenario or add a focused method that performs this order:

```java
coordinator.activateInitialLevel(levelObservation(10, 0, 0, 0, false, 0, false));
RunBoundarySignal.LevelLoaded loaded = new RunBoundarySignal.LevelLoaded(
        125, RunLevelLoadCause.LEVEL_ADVANCE,
        new RunPlaybackObservation.LevelIdentity(11, 1, 1, 0));
assertTrue(coordinator.beforeLoadedLevelActivation(
        loaded, levelObservation(11, 1, 0, 1, false, 0, true)).isEmpty());
assertEquals(List.of(new CloseSegment(0), new EnterTransitionGap(0, 1)),
        coordinator.afterProduction(
                levelObservation(10, 0, 0, 2, true, 0, true)));
assertTrue(coordinator.beforeAdmission(
        levelObservation(11, 1, 0, 3, false, 0, true)).isEmpty());
assertEquals(TraceRunPlaybackCoordinator.Phase.TRANSITION_GAP,
        coordinator.phase());
assertInstanceOf(AdmitDestination.class,
        coordinator.beforeAdmission(
                levelObservation(11, 1, 0, 4, false, 0, false)).getFirst());
```

Add the final boolean to the level-observation helper as the pending-card value.

- [x] **Step 2: Run the new ordering test and verify RED**

Run:

```bash
mvn -q -Dmse=off \
  -Dtest='com.openggf.tests.trace.runs.TestTraceRunPlaybackCoordinator#matchingLoadDuringSourceTailWaitsForInitialTitleCardRelease' test
```

Expected: failure because `beforeLoadedLevelActivation` or `beforeAdmission` emits `AdmitDestination` while the barrier is true.

- [x] **Step 3: Add the minimal generic readiness condition**

At the start of the level branch in `destinationReady`, reject the destination while its live presentation owner is pending:

```java
if ("level".equals(destination.kind())) {
    if (observation.mode() != GameMode.LEVEL
            || observation.initialTitleCardPending()) {
        return false;
    }
    // existing lag-only and remembered-load checks remain unchanged
}
```

Do not alter `ownsCurrentSegment`, `failSourceOwnership`, load-cause matching, or boundary windows.

- [x] **Step 4: Run coordinator tests and verify GREEN**

Run:

```bash
mvn -q -Dmse=off \
  -Dtest='com.openggf.tests.trace.runs.TestTraceRunPlaybackCoordinator,com.openggf.tests.trace.runs.TestTraceRunPlaybackTranscriptParity' test
```

Expected: all tests pass, including immediate no-card and lag-only continuation cases.

### Task 3: Populate the barrier in visual and headless adapters

**Files:**
- Modify: `src/main/java/com/openggf/TraceSessionLauncher.java`
- Modify: `src/test/java/com/openggf/TestTraceSessionLauncherRunBranch.java`
- Modify: `src/test/java/com/openggf/tests/trace/runs/AbstractRunChainTest.java`

**Interfaces:**
- Consumes: `LevelManager.isTitleCardRequested()`.
- Produces: identical live barrier state for visual `captureRunObservation` and headless `HeadlessRunCoordinatorAdapter.observation`.
- Preserves: `withProductionOwner` pins source mode/identity but carries the current destination barrier.

- [x] **Step 1: Write launcher observation and owner-isolation RED tests**

Add focused assertions that:

1. a live level manager with `requestTitleCard(...)` produces an observation whose `initialTitleCardPending()` is true; and
2. `withProductionOwner(current, owner)` preserves `current.initialTitleCardPending()` while replacing only source ownership identity/mode.

Invoke private helpers through the existing reflection utilities in `TestTraceSessionLauncherRunBranch`.

Also create a two-level synthetic visual run, install the production-shaped
`runBoundaryProbe` as the playback frame observer with `sourceComparator` as
its delegate, activate segment 0, observe the segment-1 load during source
production, and apply source close/gap actions. Ask the launcher to admit while
the live `LevelManager` still reports a pending initial title card. Assert that
no destination owner replaces the source/gap state:

```java
assertSame(sourceBoundaryProbe,
        getField(GameServices.playbackDebug(), "frameObserver"));
assertNull(getField(sourceBoundaryProbe, "delegate"));
assertSame(sourceComparator, getField(session, "comparator"));
assertTrue(fixture.handoffs.isEmpty());
assertFalse(context.dynamicArtLifecycle().isComparisonSegmentOpen());
assertEquals(sourceCursor, GameServices.playbackDebug().getCursorFrame());
assertEquals(List.of(0), session.runCoordinatorTranscript().stream()
        .filter(TraceRunPlaybackCoordinator.AdmitDestination.class::isInstance)
        .map(TraceRunPlaybackCoordinator.AdmitDestination.class::cast)
        .map(action -> action.receipt().segmentIndex())
        .toList());
```

The stable frame observer must remain the boundary probe. Closing the source
intentionally detaches its delegate during the transition gap, while the
launcher's comparator field retains the closed source comparator until a real
destination admission replaces it. Directly expecting the comparator as
`frameObserver`, or expecting the source comparator to remain attached as the
probe delegate, would not model the production launcher.

- [x] **Step 2: Run all launcher RED tests**

Run:

```bash
mvn -q -Dmse=off \
  -Dtest='TestTraceSessionLauncherRunBranch#captureObservationReportsPendingInitialTitleCard,TestTraceSessionLauncherRunBranch#productionOwnerPinKeepsDestinationTitleCardBarrier,TestTraceSessionLauncherRunBranch#pendingInitialTitleCardKeepsVisualDestinationOwnersClosed' test
```

Expected: the live capture assertion fails because the launcher currently
supplies `false`, and the owner-isolation test observes premature segment-1
admission/replacement.

- [x] **Step 3: Populate the live field at both adapters**

In `TraceSessionLauncher.captureRunObservation` pass:

```java
levelManager.isTitleCardRequested()
```

In `withProductionOwner`, pass:

```java
current.initialTitleCardPending()
```

In `AbstractRunChainTest.HeadlessRunCoordinatorAdapter.observation`, pass:

```java
levelManager.isTitleCardRequested()
```

- [x] **Step 4: Run launcher/headless parity tests and verify GREEN**

Run:

```bash
mvn -q -Dmse=off \
  -Dtest='TestTraceSessionLauncherRunBranch,com.openggf.tests.trace.runs.TestTraceRunPlaybackCoordinator,com.openggf.tests.trace.runs.TestTraceRunPlaybackTranscriptParity' test
```

Expected: all tests pass; no destination owner opens while the card is pending.

### Task 4: Verify real S1 segment and run behavior

**Files:**
- Modify: `docs/status/trace-frontier-log.md`
- Modify: `CHANGELOG.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: committed S1 complete-run fixtures and ROM-backed replay.
- Produces: recorded evidence for GHZ1/GHZ2 and cross-game whole-run policy stability.

- [x] **Step 1: Run the S1 boundary segment replays**

Run:

```bash
mvn -q -Dmse=off \
  -Dsonic1.rom.path=s1.gen \
  -Dtest='TestS1Ghz1CompleteRunTraceReplay,TestS1Ghz2CompleteRunTraceReplay' test
```

Expected: both segment replays pass.

- [x] **Step 2: Run the available S1 whole-run chain**

Run:

```bash
mvn -q -Dmse=off \
  -Dsonic1.rom.path=s1.gen \
  -Dtest='com.openggf.tests.trace.runs.TestS1GhzMazeRoundTripChain' test
```

Expected: the chain reaches its terminal action without an ownership failure.

- [x] **Step 3: Run all trace replays across all ROMs**

Run:

```bash
mvn -q -Dmse=off \
  -Dsonic1.rom.path=s1.gen \
  -Dsonic2.rom.path=s2.gen \
  -Ds3k.rom.path=s3k.gen \
  -Dtest='*TraceReplay' -DfailIfNoTests=false test
```

Expected: no previously passing trace regresses. Record any pre-existing red frontier by exact test, error count, first frame, and field.

- [x] **Step 4: Update user-facing and frontier documentation**

Add concise entries explaining that visual whole-run level destinations now wait for their production initial title card before opening replay ownership. Record the exact commands, branch/worktree context, pass/fail counts, and any unchanged baseline failures in `docs/status/trace-frontier-log.md`.

- [x] **Step 5: Run focused verification again after documentation edits**

Run:

```bash
mvn -q -Dmse=off \
  -Dsonic1.rom.path=s1.gen \
  -Dtest='TestTraceSessionLauncherRunBranch,com.openggf.tests.trace.runs.TestTraceRunPlaybackCoordinator,com.openggf.tests.trace.runs.TestTraceRunPlaybackTranscriptParity,TestS1Ghz1CompleteRunTraceReplay,TestS1Ghz2CompleteRunTraceReplay' test
```

Expected: all focused tests pass.

### Task 5: Review, integrate, and deliver

**Files:**
- Review every staged file from Tasks 1-4.

**Interfaces:**
- Produces: one reviewed commit on `bugfix/ai-visual-trace-level-handoff`, fast-forwarded into `develop`, verified and pushed.

- [x] **Step 1: Run diff and policy checks**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors and only intended files changed.

- [x] **Step 2: Request independent code review**

Give the reviewer the design, plan, base SHA, final diff, red/green evidence, and exact verification results. Resolve every Critical or Important issue and repeat review until none remain.

- [ ] **Step 3: Commit with required documentation trailers**

Use subject:

```text
fix: defer visual run admission through title cards
```

Set `Changelog: updated`; set the remaining trailers accurately. Never use `--no-verify`.

- [ ] **Step 4: Refresh, reconcile, and compare the integration baseline**

Fetch and fast-forward the main `develop` workspace without disturbing its
existing user changes. Run the full all-ROM suite on that updated baseline. If
`develop` advanced beyond the worktree base, merge the refreshed `develop`
into `bugfix/ai-visual-trace-level-handoff`, reconcile conflicts, and rerun the
full and focused suites in the worktree. Compare exact failures before merging
the reconciled branch into the main workspace; do not assume a fast-forward is
available until ancestry is verified.

- [ ] **Step 5: Verify and push merged develop**

Run the full all-ROM suite and the Task 4 focused command on merged `develop`. Confirm no baseline-passing test becomes red, push only `develop`, then remove the clean worktree and fully merged local branch.
