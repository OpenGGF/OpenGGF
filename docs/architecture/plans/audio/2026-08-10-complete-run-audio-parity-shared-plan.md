# Complete-run audio parity shared infrastructure implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the strict, bounded, cross-game capture envelope, storage, comparator, observer plumbing, and complete-run replay boundary required by S1, S2, and S3K audio parity.

**Architecture:** Natural BizHawk and OpenGGF producers emit the same canonical record model into deterministic 4,096-row chunks. Game profiles own identities and driver-specific state; shared code owns validation, publication, comparison, observer propagation, and exact BK2-row cadence.

**Tech Stack:** Java 21, JUnit Jupiter, Jackson streaming JSON, deterministic GZIP, SHA-256, Bash, .NET/C# BizHawk 2.11 GPGX host.

## Global Constraints

- Follow `docs/architecture/designs/audio/2026-08-10-cross-game-complete-run-audio-parity-design.md` exactly.
- Work only in the isolated `bugfix/ai-s1-audio-parity-frontier` worktree; do not merge or push.
- Test behavior changes with RED/GREEN TDD and commit each task with required policy trailers.
- Detailed captures remain ignored under `target/audio-parity/`; tracked docs are compact and non-reconstructive.
- Production packages must not import `com.openggf.tools.audio.completerun`.
- The OpenGGF producer must never read a reference capture or recorded audio-event sidecar.
- Existing GHZ sound-test and GHZ1 timeline tools remain green.

---

### Task 1: Canonical record model and strict profile registry

**Files:**
- Create: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioTrace.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioProfile.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioProfiles.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioRecordSink.java`
- Create: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioTrace.java`

**Interfaces:**
- Produces: sealed `Record`, `Metadata`, `Baseline`, `Frame`, `DriverService`, `Lifecycle`, `Terminal`, `Request`, `Decision`, `OwnerRef`, `NormalizedState`, and `ChipEvent` types.
- Produces: `CompleteRunAudioProfile#validateState(NormalizedState)`, `CompleteRunAudioProfiles#require(String)`, and append-only `CompleteRunAudioRecordSink`.
- Consumes: no runtime owners and no old S1 schema classes.

- [ ] **Step 1: Write the failing model tests**

```java
@Test
void oneFrameCanContainZeroOrMultipleOrderedServices() {
    var empty = fixture.frame(860, List.of(), List.of());
    var busy = fixture.frame(861, List.of(fixture.request(1)), List.of(
            fixture.service(0), fixture.service(1)));
    assertEquals(List.of(), empty.services());
    assertEquals(List.of(0L, 1L),
            busy.services().stream().map(DriverService::ordinal).toList());
}

@Test
void sameIdOwnersRemainDistinctByRequestOrdinal() {
    assertNotEquals(new OwnerRef(OwnerClass.SFX, "sfx.explosion", 0xC0, 7),
            new OwnerRef(OwnerClass.SFX, "sfx.explosion", 0xC0, 8));
}
```

Also reject signed/out-of-range bytes, unordered/duplicate roles, duplicate state
fields, empty content keys, unknown profiles, inactive roles with stale fields,
and a terminal whose counts or exclusive end do not match metadata.

- [ ] **Step 2: Run the test and observe RED**

Run:

```bash
mvn -Dmse=off -Dtest=com.openggf.tools.audio.completerun.TestCompleteRunAudioTrace test
```

Expected: test compilation fails because the complete-run types do not exist.

- [ ] **Step 3: Implement immutable records and profile validation**

Use a sealed hierarchy with defensive `List.copyOf`/`Map.copyOf` constructors.
Represent unsigned bytes as validated `int`; do not use signed JSON bytes.
`NormalizedState` stores an ordered list of `StateField` objects so canonical
field order is explicit and profile-validated.

```java
public sealed interface Record permits Baseline, Frame, Lifecycle, Terminal { }

public sealed interface ChipEvent permits YmWrite, PsgWrite {
    long ordinal();
}

public record YmWrite(long ordinal, int port, int register, int value)
        implements ChipEvent { }
```

- [ ] **Step 4: Run the focused tests and observe GREEN**

Run the command from Step 2. Expected: all tests pass.

- [ ] **Step 5: Commit the model**

```bash
git add src/main/java/com/openggf/tools/audio/completerun \
        src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioTrace.java
git commit -m "feat(tools): define complete-run audio trace schema"
```

### Task 2: Deterministic chunk store and atomic publication

**Files:**
- Create: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioJson.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioCaptureStore.java`
- Create: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioCaptureStore.java`

**Interfaces:**
- Consumes: Task 1 record/profile types.
- Produces: `CompleteRunAudioCaptureStore.Writer`, `Reader`, `writeNew(Path, Metadata, Iterator<Record>)`, and `read(Path)`.

- [ ] **Step 1: Write failing strictness and publication tests**

Test deterministic byte identity, exactly 4,096 frame rows per non-final chunk,
gzip header timestamp zero, duplicate/unknown JSON rejection at every nesting
level, trailing root rejection, digest validation, missing terminal rejection,
iterator failure cleanup, unsupported atomic move failure, and preservation of
an existing destination.

```java
@Test
void failedPublicationNeverReplacesExistingCapture() throws Exception {
    Path output = temp.resolve("capture");
    Files.createDirectory(output);
    Files.writeString(output.resolve("sentinel"), "keep");
    assertThrows(FileAlreadyExistsException.class,
            () -> store.writeNew(output, metadata(), records().iterator()));
    assertEquals("keep", Files.readString(output.resolve("sentinel")));
}
```

- [ ] **Step 2: Run the test and observe RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.tools.audio.completerun.TestCompleteRunAudioCaptureStore test
```

Expected: compilation fails for the missing store.

- [ ] **Step 3: Implement streaming JSON, deterministic gzip, and staging**

Write canonical JSON with a `JsonGenerator`; parse with a streaming
`JsonParser` and strict exact-field sets. Create a sibling staging directory
with `Files.createTempDirectory(output.getParent(), ".audio-staging-")`. After
full re-read validation, publish only through:

```java
Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
```

Do not fall back to a non-atomic move. The capture manifest lists per-chunk
compressed/uncompressed hashes, record counts, frame bounds, and the root hash.

- [ ] **Step 4: Prove bounded reads and deterministic output**

Add a subprocess test that reads a synthetic 20,000-frame capture using
`java -Xmx16m`, then run the Step 2 command. Expected: all pass and duplicate
capture directories compare byte-for-byte.

- [ ] **Step 5: Commit the store**

```bash
git add src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioJson.java \
        src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioCaptureStore.java \
        src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioCaptureStore.java
git commit -m "feat(tools): store complete audio traces in deterministic chunks"
```

### Task 3: No-realignment comparator and reports

**Files:**
- Create: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioComparator.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioReport.java`
- Create: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioComparator.java`

**Interfaces:**
- Consumes: Task 2 validated capture readers.
- Produces: `CompleteRunAudioComparator.compare(Path reference, Path engine)` and deterministic text/JSON reports.

- [ ] **Step 1: Write failing mismatch-classification tests**

Cover metadata identity, missing/extra frame, request, service, decision and chip
event, ordering, state-field name/value, owner, priority, lifecycle, terminal
count, source replacement between passes, and exact match. Assert first mismatch
only and at most eight before/eight after records from each side.

```java
@Test
void doesNotRealignOneFrameAdmissionDelay() throws Exception {
    Report report = compare(referenceWithAdmissionAt(959),
            engineWithAdmissionAt(958));
    assertEquals(Kind.DECISION_EXTRA, report.kind());
    assertEquals(958, report.frame());
}
```

- [ ] **Step 2: Run RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.tools.audio.completerun.TestCompleteRunAudioComparator test
```

- [ ] **Step 3: Implement validation-first two-pass comparison**

Pass one fully validates each capture and records its root digest. Pass two
reopens both, rechecks metadata/ordinals/EOF while comparing, and verifies the
same digest at completion. Do not classify errors by message substrings; use a
typed `ValidationException.Kind`.

- [ ] **Step 4: Run GREEN and bounded-memory comparison**

Run the Step 2 command and a `-Xmx32m` synthetic 50,000-frame comparison.
Expected: all pass with bounded context.

- [ ] **Step 5: Commit the comparator**

```bash
git add src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioComparator.java \
        src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioReport.java \
        src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioComparator.java
git commit -m "feat(tools): compare complete-run audio captures"
```

### Task 4: Pre-construction observer propagation and authority guard

**Files:**
- Create: `src/main/java/com/openggf/audio/AudioAdmissionObserver.java`
- Create: `src/main/java/com/openggf/audio/driver/SmpsDriverServiceObserver.java`
- Create: `src/main/java/com/openggf/audio/driver/SmpsRequestAdmissionPolicy.java`
- Modify: `src/main/java/com/openggf/audio/AudioManager.java`
- Modify: `src/main/java/com/openggf/audio/AbstractSmpsAudioBackend.java`
- Modify: `src/main/java/com/openggf/audio/GameAudioProfile.java`
- Modify: `src/main/java/com/openggf/audio/presentation/AudioPresentationSourceFactory.java`
- Modify: `src/main/java/com/openggf/audio/driver/SmpsDriver.java`
- Test: `src/test/java/com/openggf/audio/TestAudioDiagnosticObservers.java`
- Test: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioAuthorityGuard.java`

**Interfaces:**
- Produces: disabled-by-default admission and service/lifecycle observers plus a default permissive request policy selected through `GameAudioProfile`.
- Produces: `AudioManager#setAdmissionObserver`, `setDriverServiceObserver`, and existing chip/SFX observer propagation to every subsequently constructed driver.
- Consumes: no tooling types.

- [ ] **Step 1: Write failing lifecycle tests**

Construct the audio manager, install observers before music creation, then
assert observers see the first driver service, every new override driver, the
restored driver, accepted and blocked submissions, and ordered chip writes.
Assert `NONE` preserves snapshot bytes and write order.

- [ ] **Step 2: Run RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.audio.TestAudioDiagnosticObservers,com.openggf.tools.audio.completerun.TestCompleteRunAudioAuthorityGuard test
```

- [ ] **Step 3: Add minimal observer interfaces and propagation**

```java
public interface SmpsDriverServiceObserver {
    SmpsDriverServiceObserver NONE = new SmpsDriverServiceObserver() { };
    default void onServiceBegin(long ordinal) { }
    default void onServiceEnd(long ordinal, SmpsDriverSnapshot snapshot) { }
    default void onLifecycle(LifecycleKind kind) { }
}
```

Callbacks run after the observed mutation, propagate exceptions so a tooling
capture fails loudly, and are absent from snapshots. Production defaults remain
no-op. Add no game-name checks.

The shared request policy is exact and game-neutral:

```java
public interface SmpsRequestAdmissionPolicy {
    AdmissionResult evaluate(SmpsAdmissionContext context);

    record AdmissionResult(boolean accepted, RejectionReason reason,
            int priorityBefore, int priorityAfter, int resolvedSoundId) { }
}
```

`GameAudioProfile#getSfxAdmissionPolicy()` returns the permissive implementation
by default. S2 and S3K replace it in their plans; S1 retains its driver-owned
priority semantics until the S1 mailbox task selects its source-accurate path.

- [ ] **Step 4: Add static authority assertions**

Scan `src/main/java/com/openggf` excluding `tools/` and fail on imports or fully
qualified references to `com.openggf.tools.audio.completerun`. Scan complete-run
producer constructors and fail if they accept reference capture paths/readers.

- [ ] **Step 5: Run GREEN and regression tests**

```bash
mvn -Dmse=off -Dtest=com.openggf.audio.TestAudioDiagnosticObservers,com.openggf.audio.synth.TestChipWriteObserver,com.openggf.audio.driver.TestSmpsDriverSnapshot,com.openggf.tools.audio.completerun.TestCompleteRunAudioAuthorityGuard test
```

- [ ] **Step 6: Commit the observer boundary**

```bash
git add src/main/java/com/openggf/audio src/test/java/com/openggf/audio \
        src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioAuthorityGuard.java
git commit -m "feat(audio): expose inert complete-run diagnostic observers"
```

### Task 5: Complete-run outer-frame replay cadence

**Files:**
- Modify: `src/test/java/com/openggf/tests/trace/runs/VisualRunReplayHarness.java`
- Create: `src/test/java/com/openggf/tests/trace/runs/TestCompleteRunAudioReplayCadence.java`

**Interfaces:**
- Produces: `VisualRunReplayHarness.replayCompleteAudio(Path, CompleteAudioStop, FrameObserver)`.
- Produces: frame views for every row in the comparison interval, with nullable segment coordinates.

- [ ] **Step 1: Write failing cadence tests**

Use a compact synthetic run with a segment, a three-row transition gap, a
second segment, a lag row, and a terminal tail. Assert one callback and one
presentation per absolute BK2 row, contiguous cursors, gap rows retained with
`segmentIndex == -1`, and exact terminal cursor. Assert fast-forward, pause,
trace abort, or premature movie end fails.

- [ ] **Step 2: Run RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.tests.trace.runs.TestCompleteRunAudioReplayCadence test
```

- [ ] **Step 3: Implement explicit epoch/end cadence**

Do not change ordinary replay behavior. Add a mode whose budget is exactly the
validated interval length plus bounded bootstrap allowance. For every consumed
row call `loop.step()`, `loop.presentOuterFrame(false, false)`, and
`GameServices.audio().update()` exactly once before notifying the observer.

- [ ] **Step 4: Run GREEN and existing visual-run regressions**

```bash
mvn -Dmse=off -Dtest=com.openggf.tests.trace.runs.TestCompleteRunAudioReplayCadence,com.openggf.tests.trace.runs.TestS1CompleteEmeraldVisualRun test
```

- [ ] **Step 5: Commit the cadence seam**

```bash
git add src/test/java/com/openggf/tests/trace/runs/VisualRunReplayHarness.java \
        src/test/java/com/openggf/tests/trace/runs/TestCompleteRunAudioReplayCadence.java
git commit -m "test(audio): drive every complete-run movie row"
```

### Task 6: Verified Z80 domains and callback capability probe

**Files:**
- Modify: `tools/bizhawk-headless/src/Core/IGpgxHost.cs`
- Modify: `tools/bizhawk-headless/src/Core/GpgxHost.cs`
- Create: `tools/bizhawk-headless/src/Core/GpgxMemoryScope.cs`
- Create: `tools/bizhawk-headless/src/Audio/CompleteRunAudioObserver.cs`
- Modify: `tools/bizhawk-headless/tests/GpgxHostTests.cs`
- Create: `tools/bizhawk-headless/tests/CompleteRunAudioObserverTests.cs`
- Create: `tools/bizhawk-headless/tests/GpgxZ80AudioCapabilityTests.cs`

**Interfaces:**
- Produces: scoped execute/write callbacks, strict reads from `68K RAM`, `Z80 RAM`, `M68K BUS`, and `Z80 BUS`, and a game-profile-neutral ordered callback collector.
- Consumes: pinned BizHawk 2.11 GPGX services only.

- [ ] **Step 1: Write failing scope/domain unit tests**

Assert missing or incorrectly sized `Z80 RAM`, absent `Z80 BUS`, unknown scope,
out-of-range read, callback exception, and disposed registration all fail
closed. Assert the old M68K overload delegates to `M68K BUS` unchanged.

- [ ] **Step 2: Run RED**

```bash
tools/bizhawk-headless/test.sh --filter GpgxHostTests
```

- [ ] **Step 3: Implement declarative domains and callbacks**

```csharp
public enum GpgxMemoryScope { M68kBus, Z80Bus }

IDisposable RegisterExecuteCallback(
    GpgxMemoryScope scope, uint address, Action callback);
IDisposable RegisterWriteCallback(
    GpgxMemoryScope scope, uint address, Action<uint, byte> callback);
byte ReadMemoryByte(string domain, int offset);
```

Require exact BizHawk names and sizes. Root delegates until disposal and surface
callback exceptions after `FrameAdvance` with the selected scope in the error.

Implement `CompleteRunAudioObserver` as a bounded-current-frame collector whose
game profile supplies hook manifests/state readers. It assigns service and chip
ordinals but performs no game-specific classification.

- [ ] **Step 4: Run the real capability proof**

With the verified S2 and S3K ROM properties, register known Z80 service PCs and
YM/PSG write ranges. Cross-check positive PC counts with observed register/data
writes and emit a compact non-reconstructive test report. A zero count or
uncorrelated callback is RED; do not choose fallback semantics yet.

```bash
tools/bizhawk-headless/test.sh --filter GpgxZ80AudioCapabilityTests
```

- [ ] **Step 5: Commit the GPGX boundary**

```bash
git add tools/bizhawk-headless/src/Core tools/bizhawk-headless/src/Audio/CompleteRunAudioObserver.cs \
        tools/bizhawk-headless/tests/GpgxHostTests.cs \
        tools/bizhawk-headless/tests/CompleteRunAudioObserverTests.cs \
        tools/bizhawk-headless/tests/GpgxZ80AudioCapabilityTests.cs
git commit -m "feat(tools): expose verified GPGX Z80 audio callbacks"
```

### Task 7: Shared CLI orchestration and final shared verification

**Files:**
- Create: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioTool.java`
- Create: `tools/audio/run_complete_audio_parity.sh`
- Create: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioCli.java`
- Modify: `tools/audio/README.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Produces: `validate`, `publish`, and `compare` subcommands and exit codes 0/2/3/4.
- Consumes: game-specific producer commands supplied by the later plans.

- [ ] **Step 1: Write failing CLI/security tests**

Test canonical target confinement, control/newline rejection, create-new run
roots, no overwrite, fixed trusted Java class, sanitized child environment,
producer nonzero discard, duplicate byte gate, report pair atomicity, and exact
exit classification. Reject `JAVA_TOOL_OPTIONS`, `MAVEN_OPTS`, `BASH_ENV`,
`ENV`, `LD_*`, and producer/tool replacement variables.

- [ ] **Step 2: Run RED**

```bash
mvn -Dmse=off -Dtest=com.openggf.tools.audio.completerun.TestCompleteRunAudioCli test
bash -n tools/audio/run_complete_audio_parity.sh
```

- [ ] **Step 3: Implement the safe generic orchestration**

The shell validates identities through the fixed Java tool, creates one fresh
run directory, invokes reference twice and engine twice into direct children,
uses byte comparison for duplicate gates, then invokes the comparator. It never
deletes published captures and never accepts a replacement executable seam.

- [ ] **Step 4: Run all shared verification**

```bash
mvn -Dmse=off -Dtest='com.openggf.tools.audio.completerun.TestCompleteRunAudioTrace,com.openggf.tools.audio.completerun.TestCompleteRunAudioCaptureStore,com.openggf.tools.audio.completerun.TestCompleteRunAudioComparator,com.openggf.tools.audio.completerun.TestCompleteRunAudioAuthorityGuard,com.openggf.tools.audio.completerun.TestCompleteRunAudioCli,com.openggf.audio.TestAudioDiagnosticObservers,com.openggf.audio.synth.TestChipWriteObserver,com.openggf.audio.driver.TestSmpsDriverSnapshot,com.openggf.tests.trace.runs.TestCompleteRunAudioReplayCadence' test
tools/bizhawk-headless/test.sh
bash -n tools/audio/run_complete_audio_parity.sh
git diff --check
```

Verify the Surefire XML rather than trusting wildcard console summaries.

- [ ] **Step 5: Commit shared orchestration and docs**

```bash
git add src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioTool.java \
        src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioCli.java \
        tools/audio/run_complete_audio_parity.sh tools/audio/README.md CHANGELOG.md
git commit -m "feat(tools): orchestrate complete-run audio parity"
```

### Task 8: Cross-game integration report and end-to-end review

**Files:**
- Create: `docs/architecture/validation/audio/2026-08-10-cross-game-complete-run-audio-parity-integration.md`
- Create: `docs/architecture/validation/audio/2026-08-10-cross-game-complete-run-audio-parity-end-to-end-review.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: completed S1, S2, and S3K plans with real `MATCH` artifacts.
- Produces: requirements traceability, exact verification evidence, residual-risk assessment, and a human listening checklist. It performs no merge or push.

- [ ] **Step 1: Audit every design acceptance criterion**

Build a table mapping each numbered goal and acceptance criterion to the exact
capture manifest, comparator result, test XML, source file, or command log that
proves it. Mark missing or indirect evidence RED and return to the owning game
plan; do not describe an unproved item as complete.

- [ ] **Step 2: Run all three real commands from fresh output roots**

```bash
tools/audio/run_s1_complete_audio_parity.sh --rom "$S1_ROM" --bizhawk-home docs/BizHawk-2.11-linux-x64
tools/audio/run_s2_complete_audio_parity.sh --rom "$S2_ROM" --bizhawk-home docs/BizHawk-2.11-linux-x64
tools/audio/run_s3k_complete_audio_parity.sh --rom "$S3K_ROM" --bizhawk-home docs/BizHawk-2.11-linux-x64
```

Expected: all exit 0; each has two byte-identical reference captures, two
byte-identical OpenGGF captures, and a cross-producer `MATCH` report.

- [ ] **Step 3: Run final focused and full JDK21 verification**

```bash
mvn -Dmse=off -Dsonic1.rom.path="$S1_ROM" -Dsonic2.rom.path="$S2_ROM" \
  -Ds3k.rom.path="$S3K_ROM" \
  -Dtest='com.openggf.tools.audio.completerun.TestCompleteRunAudioTrace,com.openggf.tools.audio.completerun.TestCompleteRunAudioCaptureStore,com.openggf.tools.audio.completerun.TestCompleteRunAudioComparator,com.openggf.tools.audio.completerun.TestCompleteRunAudioAuthorityGuard,com.openggf.tools.audio.completerun.TestCompleteRunAudioCli,com.openggf.tools.audio.completerun.s1.TestS1CompleteRunOpenGgfCapture,com.openggf.tools.audio.completerun.s2.TestS2CompleteRunOpenGgfCapture,com.openggf.tools.audio.completerun.s3k.TestS3kCompleteRunOpenGgfCapture,com.openggf.audio.TestAudioDiagnosticObservers,com.openggf.audio.driver.TestSmpsDriverSnapshot,com.openggf.audio.synth.TestChipWriteObserver,com.openggf.game.sonic1.audio.TestSonic1ExtraLifePriority,com.openggf.game.sonic2.audio.TestSonic2ExtraLifeRestore,com.openggf.game.sonic3k.audio.TestSonic3kExtraLifeRestore' test
tools/bizhawk-headless/test.sh
mvn -Pci -Dsonic1.rom.path="$S1_ROM" -Dsonic2.rom.path="$S2_ROM" \
  -Ds3k.rom.path="$S3K_ROM" test
```

Compare exact failure/error IDs against the pre-feature baseline. Any new red
test returns to its owner.

- [ ] **Step 4: Perform independent source and policy review**

Review for trace authority, game-name checks in shared runtime code, unpinned
identities, fallback without callback proof, skipped rows/services/writes,
non-atomic output, stale snapshot fields, `FixBugs` branch errors, and changes
to operator/chip-port order. Record every finding and its fix in the end-to-end
review document.

- [ ] **Step 5: Write the human listening checklist**

For each game list concrete all-emeralds scenes covering normal music, FM SFX,
PSG SFX, overlapping SFX, 1-up takeover and restore, speed changes, DAC/PCM,
special-stage/bonus music, transitions, and ending/credits. State explicitly
that automated byte parity does not authorize merging without human approval.

- [ ] **Step 6: Commit the reports without integrating**

```bash
git add CHANGELOG.md \
  docs/architecture/validation/audio/2026-08-10-cross-game-complete-run-audio-parity-integration.md \
  docs/architecture/validation/audio/2026-08-10-cross-game-complete-run-audio-parity-end-to-end-review.md
git commit -m "docs(audio): validate complete-run parity across games"
```

Stop after this commit and present the branch/commits/artifacts to the humans.
Do not fetch/merge/push/clean up the worktree until they explicitly authorize
integration.
