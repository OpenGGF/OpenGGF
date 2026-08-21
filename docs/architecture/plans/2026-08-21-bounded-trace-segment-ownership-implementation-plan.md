# Bounded Trace Segment Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace whole-run retention of every segment's parsed physics and auxiliary payload with immutable run descriptors plus one deterministically closed, streaming active-segment cursor.

**Architecture:** Planning scans one segment at a time, preserves only compact metadata, timing, lag, bootstrap, opening, and terminal summaries, and drops each eager validation object before scanning the next segment. Replay opens a `TraceSegmentCursor` whose physics reader keeps previous/current/lookahead rows and whose auxiliary reader materialises only pre-trace and current-raw-frame events; run drivers close that cursor on every boundary, abort, and failure path. Existing single-trace replay continues using `TraceData`; shared comparator/bootstrap code consumes a narrow `TraceReplayData` contract implemented by both representations.

**Tech Stack:** Java 21, JUnit 5/Jupiter, Jackson streaming JSON parsing, Maven Surefire, gzip/plain trace files.

**Spec:** `docs/architecture/designs/2026-08-21-bounded-trace-run-segment-ownership.md`

## Global Constraints

- Trace payloads remain comparison-only and cannot hydrate or choose gameplay state.
- V5 remains the sole live trace contract; do not change fixture formats, schema fields, or hardware-timing authority.
- Preserve raw-frame identity, previous/current/lookahead semantics, row order, typed auxiliary lookups, dynamic-art ledgers, and bootstrap policy.
- Keep hardware-timing schedules and compact per-row lag mappings run-scoped.
- Close physics and auxiliary readers on successful boundary, comparison failure, cursor-construction failure, and launcher abort.
- Runtime assets remain ROM-only; this feature reads only comparison fixtures.
- Build and test with JDK 21 and JUnit Jupiter.
- Use test-first RED/GREEN cycles for every production behavior.

---

### Task 1: Extract a payload-independent replay contract and immutable descriptor

**Files:**
- Create: `src/main/java/com/openggf/trace/TraceReplayData.java`
- Create: `src/main/java/com/openggf/trace/replay/runs/TraceRunSegmentDescriptor.java`
- Create: `src/main/java/com/openggf/trace/replay/runs/TraceReplayBootstrapSummary.java`
- Modify: `src/main/java/com/openggf/trace/TraceData.java`
- Modify: `src/main/java/com/openggf/trace/replay/runs/TraceRunReplayWalker.java`
- Test: `src/test/java/com/openggf/tests/trace/runs/TestTraceRunSegmentDescriptor.java`
- Test: `src/test/java/com/openggf/tests/trace/runs/TestTraceRunReplayWalkerControlFlow.java`

**Interfaces:**
- Produces: `TraceReplayData`, the read-only comparison/bootstrap surface implemented by eager `TraceData` and the streaming cursor in Task 3.
- Produces: `TraceRunSegmentDescriptor`, the whole-run-safe segment summary and cursor factory input.
- Produces: `TraceReplayBootstrapSummary.from(TraceData)`, which evaluates whole-segment bootstrap scans while the planning payload is still available.
- Produces: `SegmentPlan.descriptor()`; removes payload ownership from `SegmentPlan`.

- [ ] **Step 1: Write the failing descriptor ownership tests**

Add tests proving that a planned segment exposes metadata, row count, timing/raw-frame mapping, compact lag outcomes, opening frame, terminal dynamic-art ledger, and source directory without exposing `TraceData`. The structural mutation the test catches is reintroducing an eager payload field into the run plan:

```java
@Test
void plannedRunRetainsDescriptorsInsteadOfTraceData(@TempDir Path root) throws Exception {
    Path runDir = TraceV5RunFixture.writeS3kBonusRun(root);
    TraceRunManifest run = TraceRunManifest.load(runDir.resolve("run_manifest.json"));

    var plans = TraceRunReplayWalker.plan(run, runDir);

    assertEquals(run.segments().size(), plans.size());
    assertEquals(2, plans.getFirst().descriptor().rowCount());
    assertEquals("s3k", plans.getFirst().descriptor().metadata().game());
    assertEquals(runDir.resolve(run.segments().getFirst().dir()),
            plans.getFirst().descriptor().segmentDirectory());
    assertTrue(Arrays.stream(TraceRunReplayWalker.SegmentPlan.class.getRecordComponents())
            .noneMatch(component -> component.getType() == TraceData.class));
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
mvn -Dmse=off "-Dtest=com.openggf.tests.trace.runs.TestTraceRunSegmentDescriptor,com.openggf.tests.trace.runs.TestTraceRunReplayWalkerControlFlow" test
```

Expected: compilation failure because `descriptor()` and `TraceRunSegmentDescriptor` do not exist.

- [ ] **Step 3: Introduce the contract and descriptor**

Define the shared read-only interface around existing consumer-visible behavior:

```java
public interface TraceReplayData {
    TraceMetadata metadata();
    HardwareTimingSchedule hardwareTimingSchedule();
    int frameCount();
    TraceFrame getFrame(int traceIndex);
    List<TraceEvent> getEventsForFrame(int rawFrame);
    List<TraceEvent.LoadQueueState> loadQueueStatesForComparisonFrame(int rawFrame);
    TraceEvent.DynamicArtTransferState dynamicArtTransferStateForFrame(int rawFrame);
    HardwareCompletionEdge unobservedDirectChildForComparisonFrame(int rawFrame);
    List<TraceEvent.ObjectStateSnapshot> preTraceObjectSnapshots();
    TraceEvent.PlayerHistorySnapshot preTracePlayerHistorySnapshot();
    TraceEvent.CpuStateSnapshot preTraceCpuStateSnapshot(String characterCode);
    TraceEvent.CpuState cpuStateForFrame(int rawFrame, String characterCode);
}
```

Make `TraceData implements TraceReplayData`. Define `TraceRunSegmentDescriptor` as an immutable record containing `Path segmentDirectory`, `TraceMetadata metadata`, `int rowCount`, `TraceFrame openingFrame`, `List<Integer> rawFrames`, `BitSet laggedRows`, `HardwareTimingSchedule hardwareTimingSchedule`, `List<DynamicArtTransfer.Descriptor> terminalDynamicArtLedger`, `TraceReplayBootstrapSummary bootstrapSummary`, and the advertised auxiliary event-type set. Copy every mutable collection/bit set in the compact constructor.

Define `TraceReplayBootstrapSummary.from(TraceData)` in this task with the exact scalar/pre-trace values currently discovered by whole-segment scans: recording start, pre-level row count/presence, replay seed index, initial VBlank/V-int phase, level-loop row count, prior-input policy, complete-run/handoff predicates, release blockers, pre-trace object/player/CPU snapshots, and the first full-level/opening frames. Task 3 changes bootstrap consumers to read these stored values; the descriptor must not need to reopen a payload to answer them.

- [ ] **Step 4: Change `SegmentPlan` to own only the descriptor**

Replace its `TraceData trace` component with `TraceRunSegmentDescriptor descriptor`; calculate execution policy during planning and update hardware-timing helpers to read descriptor fields. Do not add a compatibility `trace()` accessor.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Task 1 command and require zero failures/errors.

- [ ] **Step 6: Commit Task 1**

```bash
git add src/main/java/com/openggf/trace/TraceReplayData.java \
  src/main/java/com/openggf/trace/TraceData.java \
  src/main/java/com/openggf/trace/replay/runs/TraceReplayBootstrapSummary.java \
  src/main/java/com/openggf/trace/replay/runs/TraceRunSegmentDescriptor.java \
  src/main/java/com/openggf/trace/replay/runs/TraceRunReplayWalker.java \
  src/test/java/com/openggf/tests/trace/runs/TestTraceRunSegmentDescriptor.java \
  src/test/java/com/openggf/tests/trace/runs/TestTraceRunReplayWalkerControlFlow.java
git commit -m "refactor(traces): describe run segments without payload ownership"
```

### Task 2: Stream bounded physics and auxiliary row windows

**Files:**
- Create: `src/main/java/com/openggf/trace/TracePhysicsRowCursor.java`
- Create: `src/main/java/com/openggf/trace/TraceAuxRowCursor.java`
- Test: `src/test/java/com/openggf/trace/TestTracePhysicsRowCursor.java`
- Test: `src/test/java/com/openggf/trace/TestTraceAuxRowCursor.java`

**Interfaces:**
- Consumes: `TraceFiles.openReader(Path)`, `TraceFrame.parseCsvRow(String)`, and `TraceEvent.parseJsonLine(String, ObjectMapper, boolean)`.
- Produces: closeable monotonic cursors used by `TraceSegmentCursor` in Task 3.

- [ ] **Step 1: Write failing physics-window tests**

Cover plain and gzip inputs, optional headers/comments, monotonic advance, previous/current/lookahead identity, bounds failures, and close idempotence. Assert `retainedFrameCount() <= 3` after every advance so a future list-backed implementation fails.

- [ ] **Step 2: Run physics cursor tests and verify RED**

```bash
mvn -Dmse=off "-Dtest=com.openggf.trace.TestTracePhysicsRowCursor" test
```

Expected: compilation failure because `TracePhysicsRowCursor` is absent.

- [ ] **Step 3: Implement the physics cursor**

Use one `BufferedReader`, parse the header once, preload current/lookahead, and reject backward or skipped indices:

```java
public final class TracePhysicsRowCursor implements AutoCloseable {
    public static TracePhysicsRowCursor open(Path physicsPath) throws IOException;
    public int index();
    public TraceFrame previous();
    public TraceFrame current();
    public TraceFrame lookahead();
    public void advance() throws IOException;
    int retainedFrameCount();
    @Override public void close() throws IOException;
}
```

- [ ] **Step 4: Run physics cursor tests and verify GREEN**

Run the Step 2 command and require zero failures/errors.

- [ ] **Step 5: Write failing auxiliary-window tests**

Use ordered literal JSONL rows with pre-trace frame `-1`, multiple events on one frame, gaps, and the next frame. Assert only pre-trace plus the selected raw frame is exposed and `retainedEventFrameCount() <= 2`. Cover plain/gzip, malformed line propagation, monotonic advance, constructor failure closing its stream, and idempotent close.

- [ ] **Step 6: Run auxiliary cursor tests and verify RED**

```bash
mvn -Dmse=off "-Dtest=com.openggf.trace.TestTraceAuxRowCursor" test
```

Expected: compilation failure because `TraceAuxRowCursor` is absent.

- [ ] **Step 7: Implement the auxiliary cursor**

Use one lookahead event and group only the requested raw frame:

```java
public final class TraceAuxRowCursor implements AutoCloseable {
    public static TraceAuxRowCursor open(Path auxPath, TraceMetadata metadata)
            throws IOException;
    public List<TraceEvent> preTraceEvents();
    public List<TraceEvent> eventsForRawFrame(int rawFrame) throws IOException;
    int retainedEventFrameCount();
    @Override public void close() throws IOException;
}
```

Reject decreasing raw-frame requests and input whose event frames decrease; return `List.of()` across gaps.

- [ ] **Step 8: Run both cursor suites and verify GREEN**

```bash
mvn -Dmse=off "-Dtest=com.openggf.trace.TestTracePhysicsRowCursor,com.openggf.trace.TestTraceAuxRowCursor" test
```

- [ ] **Step 9: Commit Task 2**

```bash
git add src/main/java/com/openggf/trace/TracePhysicsRowCursor.java \
  src/main/java/com/openggf/trace/TraceAuxRowCursor.java \
  src/test/java/com/openggf/trace/TestTracePhysicsRowCursor.java \
  src/test/java/com/openggf/trace/TestTraceAuxRowCursor.java
git commit -m "feat(traces): stream bounded segment row windows"
```

### Task 3: Build the active segment cursor and preserve comparison behavior

**Files:**
- Create: `src/main/java/com/openggf/trace/replay/runs/TraceSegmentCursor.java`
- Modify: `src/main/java/com/openggf/trace/replay/runs/TraceReplayBootstrapSummary.java`
- Modify: `src/main/java/com/openggf/trace/TraceReplayBootstrap.java`
- Modify: `src/main/java/com/openggf/trace/TraceBinder.java`
- Modify: `src/main/java/com/openggf/trace/LoadQueueComparisonProjection.java`
- Modify: `src/main/java/com/openggf/trace/live/LiveTraceComparator.java`
- Modify: `src/main/java/com/openggf/trace/replay/TraceReplayRowPolicy.java`
- Modify: `src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java`
- Test: `src/test/java/com/openggf/tests/trace/runs/TestTraceSegmentCursor.java`
- Test: `src/test/java/com/openggf/trace/live/TestLiveTraceComparator.java`

**Interfaces:**
- Consumes: Task 1 descriptor/contract and Task 2 bounded readers.
- Produces: `TraceSegmentCursor.open(TraceRunSegmentDescriptor)` implementing `TraceReplayData` and `AutoCloseable`.

- [ ] **Step 1: Write failing cursor parity tests**

Load the same synthetic segment through eager `TraceData` and `TraceSegmentCursor`; for each row assert equal previous/current/lookahead physics, row policy, events, load-queue projection, dynamic-art state, CPU state, and raw-frame identity. Assert backward/out-of-window access fails and all cursor methods fail after close.

- [ ] **Step 2: Run cursor tests and verify RED**

```bash
mvn -Dmse=off "-Dtest=com.openggf.tests.trace.runs.TestTraceSegmentCursor" test
```

- [ ] **Step 3: Implement `TraceSegmentCursor`**

Open both readers failure-atomically, seed pre-trace events from the aux cursor, maintain latest checkpoint/zone-act summaries while advancing, and implement typed lookups by filtering only the current raw-frame event list. The cursor advances through one method:

```java
public final class TraceSegmentCursor implements TraceReplayData, AutoCloseable {
    public static TraceSegmentCursor open(TraceRunSegmentDescriptor descriptor)
            throws IOException;
    public int index();
    public TraceFrame previousFrame();
    public TraceFrame currentFrame();
    public TraceFrame lookaheadFrame();
    public void advance() throws IOException;
    public boolean isClosed();
    @Override public void close() throws IOException;
}
```

- [ ] **Step 4: Migrate shared comparator and policy APIs**

Change only run-compatible read paths from `TraceData` to `TraceReplayData`. Keep single-trace APIs source-compatible. Replace all per-row random lookups with the cursor's current window; derive deferred-VBlank and phase decisions from explicit previous/current arguments. Move whole-segment bootstrap scans into `TraceReplayBootstrapSummary.from(TraceData)` during planning and make run bootstrap consume the summary.

- [ ] **Step 5: Run comparator/bootstrap/policy suites and verify GREEN**

```bash
mvn -Dmse=off "-Dtest=com.openggf.tests.trace.runs.TestTraceSegmentCursor,com.openggf.trace.live.*,com.openggf.trace.replay.*,com.openggf.tests.trace.TestTraceReplayBootstrap" test
```

- [ ] **Step 6: Commit Task 3**

```bash
git add src/main/java/com/openggf/trace/replay/runs/TraceSegmentCursor.java \
  src/main/java/com/openggf/trace/replay/runs/TraceReplayBootstrapSummary.java \
  src/main/java/com/openggf/trace/TraceReplayBootstrap.java \
  src/main/java/com/openggf/trace/TraceBinder.java \
  src/main/java/com/openggf/trace/LoadQueueComparisonProjection.java \
  src/main/java/com/openggf/trace/live/LiveTraceComparator.java \
  src/main/java/com/openggf/trace/replay/TraceReplayRowPolicy.java \
  src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java \
  src/test/java/com/openggf/tests/trace/runs/TestTraceSegmentCursor.java \
  src/test/java/com/openggf/trace/live/TestLiveTraceComparator.java
git commit -m "feat(traces): compare through an active segment cursor"
```

### Task 4: Make planning one-segment-at-a-time and preserve run validation

**Files:**
- Modify: `src/main/java/com/openggf/trace/TraceRunManifest.java`
- Modify: `src/main/java/com/openggf/trace/replay/runs/TraceRunReplayWalker.java`
- Modify: `src/main/java/com/openggf/trace/catalog/TraceCatalog.java`
- Test: `src/test/java/com/openggf/tests/trace/runs/TestTraceRunPlanningOwnership.java`
- Test: `src/test/java/com/openggf/trace/catalog/TestTraceRunLaunchValidation.java`

**Interfaces:**
- Consumes: eager `TraceData` only inside one planning-loop iteration.
- Produces: `TraceRunManifest.DynamicArtRunValidator`, an incremental run-wide lifecycle validator.

- [ ] **Step 1: Write failing planning-lifetime tests**

Inject a package-visible planning observer that receives `segmentOpened(index)` and `segmentReleased(index)`. Assert the sequence is `open 0, release 0, open 1, release 1...`, that a parser failure releases the active segment, and that the returned plan opens no cursor. Assert catalog validation uses descriptor metadata/row count.

- [ ] **Step 2: Run planning tests and verify RED**

```bash
mvn -Dmse=off "-Dtest=com.openggf.tests.trace.runs.TestTraceRunPlanningOwnership,com.openggf.trace.catalog.TestTraceRunLaunchValidation" test
```

- [ ] **Step 3: Implement incremental dynamic-art validation**

Extract the existing `validateDynamicArtRun(List<TraceData>)` loop into:

```java
public final class DynamicArtRunValidator {
    public void accept(int segmentIndex, TraceData trace);
    public void finish();
}
```

The validator owns the single `LifecycleIdentity`, gap index, and opening ledger. `accept` performs the same capability, fingerprint, declared-ledger, segment lifecycle, and adjacent-gap checks before the caller drops the eager trace. `finish` enforces no remaining gap transition.

- [ ] **Step 4: Rewrite planning as a scan/extract/release loop**

For each segment: load and validate exactly as today, feed the incremental validator, build the immutable descriptor and special-stage summary, notify release in `finally`, and retain only the descriptor. Finish the dynamic-art validator before returning plans. `TraceCatalog` validates profiles and row counts from descriptors.

- [ ] **Step 5: Run planning/catalog tests and verify GREEN**

Run the Step 2 command and require zero failures/errors.

- [ ] **Step 6: Commit Task 4**

```bash
git add src/main/java/com/openggf/trace/TraceRunManifest.java \
  src/main/java/com/openggf/trace/replay/runs/TraceRunReplayWalker.java \
  src/main/java/com/openggf/trace/catalog/TraceCatalog.java \
  src/test/java/com/openggf/tests/trace/runs/TestTraceRunPlanningOwnership.java \
  src/test/java/com/openggf/trace/catalog/TestTraceRunLaunchValidation.java
git commit -m "perf(traces): release segment payloads during run planning"
```

### Task 5: Own one cursor across every run driver and failure path

**Files:**
- Modify: `src/main/java/com/openggf/TraceSessionLauncher.java`
- Modify: `src/main/java/com/openggf/trace/replay/runs/TraceRunFrameDriver.java`
- Modify: `src/main/java/com/openggf/trace/replay/runs/TraceRunPlaybackCoordinator.java`
- Modify: `src/test/java/com/openggf/tests/trace/runs/AbstractRunChainTest.java`
- Modify: `src/test/java/com/openggf/tests/trace/runs/VisualRunReplayHarness.java`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioTrace.java`
- Test: `src/test/java/com/openggf/tests/trace/runs/TestTraceRunCursorLifecycle.java`
- Test: `src/test/java/com/openggf/TestTraceSessionLauncherFailureCleanup.java`
- Test: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioTrace.java`

**Interfaces:**
- Consumes: `SegmentPlan.descriptor()` and `TraceSegmentCursor.open`.
- Produces: one active cursor owned by each run drive, closed before the next segment opens.

- [ ] **Step 1: Write failing lifecycle tests**

Inject a `SegmentCursorFactory` into run control. Cover normal handoff (`open 0, close 0, open 1, close 1`), comparator assertion, constructor failure after physics opens, launcher abort, visual gap/handoff lookup without an active cursor, and complete-audio termination. Assert at most one cursor is open.

- [ ] **Step 2: Run lifecycle tests and verify RED**

```bash
mvn -Dmse=off "-Dtest=com.openggf.tests.trace.runs.TestTraceRunCursorLifecycle,com.openggf.TestTraceSessionLauncherFailureCleanup,com.openggf.tools.audio.completerun.TestCompleteRunAudioTrace" test
```

- [ ] **Step 3: Migrate the production launcher and run driver**

Introduce:

```java
@FunctionalInterface
public interface SegmentCursorFactory {
    TraceSegmentCursor open(TraceRunSegmentDescriptor descriptor) throws IOException;
}
```

Open segment 0 immediately before bootstrap/comparator attachment. At every boundary, detach the comparator, close the source cursor in `finally`, perform immutable opening-summary comparison, then open the destination cursor. Session teardown closes the active cursor before restoring configuration and timing authority.

- [ ] **Step 4: Migrate headless, visual, and audio harnesses**

Use descriptor row counts and compact lag mappings for arbitrary BK2 `frameView` calls during gaps. Never open a payload cursor for catalog listing or gap-only presentation. Wrap each harness run in try-with-resources or an equivalent `finally` that closes the active cursor.

- [ ] **Step 5: Run lifecycle and run-control suites and verify GREEN**

```bash
mvn -Dmse=off "-Dtest=com.openggf.tests.trace.runs.*,com.openggf.TestTraceSessionLauncherRunBranch,com.openggf.TestTraceSessionLauncherFailureCleanup,com.openggf.trace.catalog.*,com.openggf.tools.audio.completerun.*" test
```

- [ ] **Step 6: Commit Task 5**

```bash
git add src/main/java/com/openggf/TraceSessionLauncher.java \
  src/main/java/com/openggf/trace/replay/runs/TraceRunFrameDriver.java \
  src/main/java/com/openggf/trace/replay/runs/TraceRunPlaybackCoordinator.java \
  src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioTrace.java \
  src/test/java/com/openggf/tests/trace/runs/AbstractRunChainTest.java \
  src/test/java/com/openggf/tests/trace/runs/VisualRunReplayHarness.java \
  src/test/java/com/openggf/tests/trace/runs/TestTraceRunCursorLifecycle.java \
  src/test/java/com/openggf/TestTraceSessionLauncherFailureCleanup.java \
  src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioTrace.java
git commit -m "perf(traces): bound run payload ownership to one segment"
```

### Task 6: Prove memory, accuracy, and resource acceptance gates

**Files:**
- Create: `src/test/java/com/openggf/tests/trace/runs/TestTraceRunSegmentOwnershipPerformance.java`
- Modify: `docs/architecture/audits/performance/2026-08-21-performance-investigation-report-audit.md`
- Modify: `docs/architecture/designs/2026-08-21-bounded-trace-run-segment-ownership.md`
- Modify: `docs/architecture/plans/2026-08-21-bounded-trace-segment-ownership-implementation-plan.md`
- Modify: `docs/status/trace-frontier-log.md` if a complete-run frontier changes.
- Modify: `CHANGELOG.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: the completed descriptor/cursor/run lifecycle.
- Produces: repeatable retained-heap and descriptor/cursor ownership evidence plus release documentation.

- [ ] **Step 1: Write the opt-in performance/resource test**

The test plans the 67-segment fixture, forces GC, records retained heap, opens/closes every cursor, samples `/proc/self/fd` when available, and asserts structural bounds independent of host noise: no plan component is `TraceData`, every physics cursor retains at most three frames, every aux cursor retains at most two frame buckets, open descriptors return to baseline after every close, and exactly one cursor is active.

- [ ] **Step 2: Run focused structural and resource verification**

```bash
mvn -Dmse=off "-Dopenggf.trace.segmentOwnershipBenchmark=true" \
  "-Dtest=com.openggf.tests.trace.runs.TestTraceRunSegmentOwnershipPerformance" test
```

Record forced-GC retained bytes and peak heap in managed task scratch storage; compare with the 1,094,956,904-byte retained baseline.

- [ ] **Step 3: Run the recorded 67-segment acceptance fixture**

Use the same fixture and JVM envelope recorded by the audit. Require segment 0 to consume all 1,653 rows, first mismatch `camera_x` expected `0x1300` actual `0x1308`, the same terminal `giant_ring` boundary failure, identical unmatched hardware completions, and identical dynamic-art result.

- [ ] **Step 4: Run focused trace ownership and authority suites**

```bash
mvn -Dmse=off "-Dtest=com.openggf.tests.trace.runs.*,com.openggf.trace.catalog.*,com.openggf.trace.timing.TestHardwareTimingAuthorityGuard,com.openggf.tools.audio.completerun.*" test
```

- [ ] **Step 5: Run the complete JDK 21 three-ROM suite**

```bash
mvn -Dmse=off \
  "-Dsonic1.rom.path=${PROJECT_ROOT}/s1.gen" \
  "-Dsonic2.rom.path=${PROJECT_ROOT}/s2.gen" \
  "-Ds3k.rom.path=${PROJECT_ROOT}/s3k.gen" test
```

Compare normalized failing `Class.method` identities with the freshly updated integration baseline. Existing baseline failures may remain; no new or worsened failure attributable to this branch is allowed.

- [ ] **Step 6: Update evidence and release documentation**

Record measured before/after heap, cursor/resource results, exact trace frontier, test commands, and limitations. Mark the design status implemented only if every accuracy and ownership gate passes. Add the required `CHANGELOG.md` and README release summary.

- [ ] **Step 7: Commit Task 6**

```bash
git add src/test/java/com/openggf/tests/trace/runs/TestTraceRunSegmentOwnershipPerformance.java \
  docs/architecture/audits/performance/2026-08-21-performance-investigation-report-audit.md \
  docs/architecture/designs/2026-08-21-bounded-trace-run-segment-ownership.md \
  docs/architecture/plans/2026-08-21-bounded-trace-segment-ownership-implementation-plan.md \
  docs/status/trace-frontier-log.md CHANGELOG.md README.md
git commit -m "perf(traces): validate bounded run segment ownership"
```

### Task 7: Review, reconcile, integrate, and clean up

**Files:**
- Review every file changed by Tasks 1-6.

**Interfaces:**
- Consumes: completed implementation and recorded verification evidence.
- Produces: pushed `develop` with no new regression and no leftover task worktree/branch.

- [ ] **Step 1: Request independent code review**

Review specifically for trace-to-gameplay authority expansion, payload reachability from `SegmentPlan`, cursor cleanup holes, row-window off-by-one errors, aux ordering assumptions, and visual/audio gap behavior. Correct every material finding test-first.

- [ ] **Step 2: Fetch and establish the updated integration baseline**

Fast-forward/reconcile `origin/develop` without switching the main workspace branch. Run the complete suite on the updated baseline and preserve exact failing-method identities.

- [ ] **Step 3: Merge the feature branch into main `develop`**

Resolve conflicts while preserving concurrent upstream behavior and stage the required README summary.

- [ ] **Step 4: Run post-merge verification and compare both directions**

Repeat the complete suite and focused ownership/trace commands. Require zero new normalized failing methods and no worsened baseline failure attributable to the branch.

- [ ] **Step 5: Push and clean up**

Push only `develop`. Confirm the feature commit is merged, inspect the worktree for generated versus unknown changes, remove the clean task worktree, delete the merged local feature branch, and prune worktree metadata.
