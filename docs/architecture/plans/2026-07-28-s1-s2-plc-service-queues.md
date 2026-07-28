# Sonic 1 and Sonic 2 PLC Service Queues Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove whether structural interrupt/lag state can reproduce S1/S2 PLC readiness, then—only if that evidence gate passes—add ROM-derived logical queues so gameplay consumers observe the original FIFO timing without trace hydration.

**Architecture:** Diagnostic ROM captures and a standalone structural predictor first test the provisional native-service classification, including lag, handler selection, HBlank deferral, preparation bubbles, and the retail interrupt race. If it passes, a shared `NemesisPlcServiceQueue` owns game-neutral FIFO/progress mechanics while game façades own parsing, lifecycle call sites, and three/six/nine-pattern budgets; if it fails, implementation stops for an amended authority design.

**Tech Stack:** Java 21, JUnit 5, Maven, existing `PlcParser`, ROM loaders, game module service graph, level lifecycle, trace replay, and rewind framework.

## Global Constraints

- Implement after the S3K direct Kos decompression queue worktree is integrated, or rebase the implementation worktree onto its integrated commit.
- Task 1 is a hard evidence gate. Tasks 2-9 must not begin until its independent review concludes `NATIVE_MODEL_APPROVED`.
- If Task 1 concludes `NATIVE_MODEL_REJECTED`, amend both design and plan, repeat their review loops, and do not infer permission to add trace authority.
- Do not change S3K Kos module or direct Kos decompression semantics in this scope.
- Runtime PLC data and pattern counts come only from the user-supplied ROM.
- Trace physics and auxiliary data remain comparison-only; do not add `PLC_QUEUE` to `HardwareWorkKind` or `hardware_timing.jsonl`.
- Shared runtime code contains no game-name, game-ID, zone, route, fixture, or frame carve-out.
- Objects use injected services and never call `getInstance()`.
- Preserve ROM call order: VBlank service, admitted loop, queue preparation, then consumer observation at its actual owner.
- Keep rendering eager; renderer/cache readiness is never gameplay readiness.
- Every queue and façade state field is rewind-covered.
- Use JDK 21 as reported by `mvn -v`.
- Tests are JUnit 5 only.
- Do not use `--no-verify`.

---

### Task 1: Prove or reject native deterministic PLC service

**Files:**
- Modify: `docs/architecture/audits/2026-07-27-s1-hardware-timing-inventory.md`
- Modify: `docs/architecture/audits/2026-07-27-s2-hardware-timing-inventory.md`
- Create: `docs/architecture/research/trace/2026-07-28-s1-s2-plc-readiness-evidence.md`
- Create: `tools/bizhawk/diagnostics/s1_plc_timing_probe.lua`
- Create: `tools/bizhawk/diagnostics/s2_plc_timing_probe.lua`
- Create: `tools/bizhawk/diagnostics/s1_plc_timing_probe.env.sh`
- Create: `tools/bizhawk/diagnostics/s2_plc_timing_probe.env.sh`
- Create: `tools/bizhawk/diagnostics/plc_timing_probe_contract_test.lua`
- Create: `src/main/java/com/openggf/tools/PlcTimingEvidenceTool.java`
- Create: `src/test/java/com/openggf/tools/TestPlcTimingEvidenceTool.java`
- Create: `docs/architecture/research/trace/assets/s1-s2-plc-evidence-vectors.json.gz`
- Reference: `docs/s1disasm/sonic.asm`
- Reference: `docs/s2disasm/s2.asm`
- Reference: `docs/architecture/designs/2026-07-28-s1-s2-plc-service-queues.md`

**Interfaces:**
- Consumes: S1/S2 ROM PLC RAM, selected interrupt handler, raw lag/admission state, and diagnostic-only capture events.
- Produces: rerunnable compact evidence plus one reviewed disposition:
  `NATIVE_MODEL_APPROVED`, `NATIVE_MODEL_REJECTED`, or `EVIDENCE_INCOMPLETE`.

- [ ] **Step 1: Verify the integration baseline**

Run:

```bash
git status --short --branch
git log -1 --oneline
git log --all --oneline --grep='Kos decompression queue' -5
mvn -v
```

Expected: the implementation worktree is based on the integrated direct Kos
decompression commit, the main workspace branch is unchanged, and Maven reports
Java 21.

- [ ] **Step 2: Pin RAM fields and structural boundaries**

Run:

```bash
rg -n "NewPLC|AddPLC|ClearPLC|RunPLC|ProcessPLC_[39]Tiles|QuickPLC" \
  docs/s1disasm/sonic.asm docs/s1disasm/_incObj
```

For every production caller, record PLC ID, append/replace/clear operation,
containing lifecycle, `RunPLC` point, selected interrupt handler, service
budget, HBlank deferral, and consumer. Pin addresses for queue head,
destination, patterns left, per-frame patterns left, VInt routine, HInt
deferral flag, game mode, and gameplay-frame counter. Resolve queue capacity
and retail overflow behavior.

- [ ] **Step 3: Add isolated diagnostic probes**

Run:

```bash
rg -n "LoadPLC|LoadPLC2|ClearPLC|RunPLC_RAM|ProcessDPLC2?|Plc_Buffer" \
  docs/s2disasm/s2.asm docs/s2disasm/*.asm
```

The native recorder host does not expose execute hooks, so do not modify the
canonical native recorder or its schema. Create isolated Lua diagnostic probes
using `event.onmemoryexecute` at the verified PLC submission, preparation,
service, pop, and consumer-poll addresses. Use `event.onframeend` to attach raw
frame, RAM state, selected handler, lag classification, and HBlank deferral.

Emit compact scratch-only events:

```text
plc_submission
plc_prepare_begin
plc_prepare_end
plc_service
plc_pop
plc_empty
plc_consumer_observation
plc_frame_state
plc_vint_state
plc_hblank_state
```

Each record contains raw frame, game mode, selected interrupt handler, lag
classification, HBlank deferral flag, queue head source/destination, patterns
left before/after, and queue-slot count. It contains no engine mutation
payload. The probes write to an explicitly supplied temporary output path and
refuse to overwrite it. They are diagnostic tools, not fixture-publication
authorities.

- [ ] **Step 4: Smoke-test probe derivation**

Run short captures and require preparation begin/end around one `RunPLC`,
service events only at the hooked service routine, a pop at the queue-shift
routine, and consumer observations only at their verified hook addresses.

Run:

```bash
plc_probe_dir=$(mktemp -d)
export OGGF_PLC_PROBE_OUTPUT="$plc_probe_dir/s1.jsonl"
source tools/bizhawk/diagnostics/s1_plc_timing_probe.env.sh
tools/bizhawk/run_bizhawk_lua.sh \
  tools/bizhawk/diagnostics/s1_plc_timing_probe.lua \
  src/test/resources/traces/s1/_movies/s1-complete-run.bk2 s1.gen
export OGGF_PLC_PROBE_OUTPUT="$plc_probe_dir/s2.jsonl"
source tools/bizhawk/diagnostics/s2_plc_timing_probe.env.sh
tools/bizhawk/run_bizhawk_lua.sh \
  tools/bizhawk/diagnostics/s2_plc_timing_probe.lua \
  src/test/resources/traces/s2/arz2/s2-lvl-select-ARZ.bk2 s2.gen
```

Expected: each output is nonempty, ordered by raw frame and within-frame hook
sequence, and contains no event inferred solely from a frame-to-frame RAM
delta.

- [ ] **Step 5: Capture varied-history ROM evidence**

Discover ROMs and existing movies. Capture at least:

```text
S1: level title card, Final Zone submission through boss release,
    varied level results, special-stage results; Game Over if an authentic
    corpus route exists
S2: level title card, ARZ submission through boss initialization,
    varied level results, special-stage results; Game Over if an authentic
    corpus route exists
```

Run one identical-input pair as a recorder-stability smoke test. For the model
gate, capture materially distinct execution instances that reach equivalent
consumers after different queue histories. Individual-level movies and
different lifecycle instances inside a multi-level/complete-run movie both
qualify when their submission, service, lag, or HBlank histories differ.
Keep raw outputs under a temporary directory; do not install or commit them as
fixtures.

If a lifecycle is absent from existing movies, inspect the complete-run movie
first and extract the relevant playback interval without changing its input.
If no existing movie reaches it, record a diagnostic BK2/save-state route using
the repository's BizHawk recording runbook and keep it as scratch. Do not
manufacture duplicate inputs solely to satisfy a per-consumer count. Record
consumer coverage by instance, movie, submission history, lag/HBlank exposure,
and readiness latency. A unique consumer may be single-instance covered; a
common consumer family without any varied-history comparison leaves the result
`EVIDENCE_INCOMPLETE` and Tasks 2-9 blocked.

- [ ] **Step 6: Write the standalone structural predictor and analyzer**

The test-local predictor consumes ROM-derived entries plus only:

```java
record StructuralRow(
        int rawFrame,
        int gameMode,
        int interruptHandler,
        boolean lag,
        boolean hblankDeferred,
        List<Submission> submissions,
        boolean runPlcCalled,
        List<ConsumerPoll> consumerPolls,
        int withinFrameOrder,
        StructuralPhase phase) {}

record ConsumerPoll(
        String consumerId,
        int withinFrameOrder) {}
```

It must not consume captured `patterns_left`, service, pop, empty, or consumer
events as inputs. Its output is:

```java
record PredictedEdge(
        int rawFrame,
        EdgeKind kind,
        int sourceAddress,
        int remainingPatterns) {}
```

`ConsumerPoll` supplies only structural execution identity/order; the captured
busy/empty result remains oracle-only. Compare predicted preparation, service,
pop, empty, and poll results against diagnostic output.

Build an ordered structural/action timeline from `plc_frame_state`,
`plc_vint_state`, and `plc_hblank_state`, keyed by
`(raw_frame, within_frame_order)`. A passive frame-end row never creates a
service opportunity. A VInt row creates the selected-handler segment; a
reviewed HBlank row may defer and complete that same open segment. Service,
pop, empty, and consumer oracle events must never create, select, or
reclassify structural segments.

Implement and run:

```bash
mvn exec:java \
  "-Dexec.mainClass=com.openggf.tools.PlcTimingEvidenceTool" \
  "-Dexec.args=--game s1 --rom s1.gen --probe /tmp/s1-plc.jsonl --out /tmp/s1-vector.json"
```

The tool validates raw probe ordering, derives structural rows and observed
edge oracles, runs the predictor, prints the first mismatch, and writes a
compact vector. Run it for every distinct capture. Require deterministic
serialization for the identical-input smoke pair, but compare distinct runs by
predictor match and diversity rather than byte equality. Merge the reviewed
vectors, gzip them reproducibly, and stage
`docs/architecture/research/trace/assets/s1-s2-plc-evidence-vectors.json.gz`.

Unit tests load that committed vector and mutate one handler, lag row, HBlank
deferral, consumer order, preparation call, or budget. Each mutation must
produce a mismatch, making the evidence gate rerunnable without committing raw
capture payloads.

- [ ] **Step 7: Resolve the retail preparation race**

Use preparation begin/end events to determine whether any covered route accepts
an interrupt while `PatternsLeft` is published but decoder state is incomplete.
Record one of:

```text
NOT_OBSERVED_ON_COVERED_ROUTES
OBSERVED_AND_CYCLE_MODEL_REQUIRED
OBSERVED_WITH_UNPREDICTABLE_HEALTHY_COMPLETION
```

Do not silently model the retail ROM as the disassembly's `FixBugs` variant.

- [ ] **Step 8: Decide the gate**

Write the evidence report with exact commands, ROM hashes, recorder commit,
routes, repeated-output hashes, first mismatch if any, race disposition, and
one conclusion:

```text
NATIVE_MODEL_APPROVED
NATIVE_MODEL_REJECTED
EVIDENCE_INCOMPLETE
```

Approval requires zero unexplained predicted-edge mismatch across all captured
lifecycles, varied-history coverage for each available common consumer family,
and authentic coverage for available unique consumers. An unavailable unique
consumer does not block approval when disassembly review proves it introduces
no queue mechanism outside the covered submission, service, and readiness
contracts. Delegate independent review. If
rejected, amend and re-review the design and plan before continuing. If
incomplete, record the missing acquisition dependency and keep Tasks 2-9
blocked. If approved, Tasks 2-9 may proceed.

- [ ] **Step 9: Commit diagnostic tooling and evidence**

Stage only Task 1 files. Commit:

```text
test(plc): validate S1 and S2 service determinism
```

Use `Changelog: n/a: diagnostic recorder and architecture evidence only`.

---

### Task 2: Implement the shared logical queue kernel

**Files:**
- Create: `src/main/java/com/openggf/level/resources/NemesisPlcPatternCounts.java`
- Create: `src/main/java/com/openggf/level/resources/NemesisPlcServiceQueue.java`
- Create: `src/main/java/com/openggf/game/rewind/snapshot/NemesisPlcQueueSnapshot.java`
- Test: `src/test/java/com/openggf/level/resources/TestNemesisPlcPatternCounts.java`
- Test: `src/test/java/com/openggf/level/resources/TestNemesisPlcServiceQueue.java`
- Create: `src/test/java/com/openggf/level/resources/TestNemesisPlcRomVectors.java`

**Interfaces:**
- Consumes: `Rom`, `PlcParser.PlcDefinition`, `PlcParser.PlcEntry`, `Pattern.PATTERN_SIZE_IN_ROM`.
- Produces:

```java
public final class NemesisPlcPatternCounts {
    public static List<Integer> derive(Rom rom, PlcDefinition definition)
            throws IOException;
}

public final class NemesisPlcServiceQueue {
    public void replaceQueued(
            PlcDefinition definition, List<Integer> patternCounts);
    public void append(PlcDefinition definition, List<Integer> patternCounts);
    public void clearQueued();
    public void prepareHead();
    public void servicePatterns(int patternBudget);
    public boolean isBusy();
    public int queuedEntryCount();
    public NemesisPlcQueueSnapshot capture();
    public void restore(NemesisPlcQueueSnapshot snapshot);
}

public record NemesisPlcQueueSnapshot(
        Entry activeEntry,
        List<Entry> queuedEntries) {
    public record Entry(
            int sourceAddress,
            int destinationTile,
            int totalPatterns,
            int remainingPatterns) {}
}
```

- [ ] **Step 1: Write queue RED tests**

Proceed only when the Task 1 evidence report says
`NATIVE_MODEL_APPROVED` and its independent review has no blocking findings.

Add named tests:

```java
@Test void appendPreservesFifoAndDuplicateEntries()
@Test void replaceQueuedRequiresIdleDecoder()
@Test void clearQueuedRequiresIdleDecoder()
@Test void prepareHeadConsumesNoPatterns()
@Test void serviceRequiresPreparedHead()
@Test void serviceHonorsThreeSixAndNinePatternBudgets()
@Test void completingEntryLeavesNextHeadUnprepared()
@Test void busyCoversPreparedAndUnpreparedEntries()
@Test void invalidCountsDoNotMutateQueue()
@Test void snapshotRoundTripsPartialHeadAndPendingTail()
@Test void restoreRejectsImpossibleProgress()
```

Use synthetic definitions and exact remaining-count assertions. In
`invalidCountsDoNotMutateQueue`, cover cardinality mismatch, zero/negative
counts, and a remaining count greater than total during restore. Task 1 must
prove the idle precondition; if it does not, stop and amend the design instead
of implementing this kernel.

- [ ] **Step 2: Write pattern-count RED tests**

Use a test ROM containing one known Nemesis stream and assert:

```java
assertEquals(
        raw.length / Pattern.PATTERN_SIZE_IN_ROM,
        NemesisPlcPatternCounts.derive(rom, definition).getFirst());
```

Also assert that a decompressed byte length not divisible by
`Pattern.PATTERN_SIZE_IN_ROM` fails before returning counts.

- [ ] **Step 3: Run RED**

Run:

```bash
mvn -Dmse=off \
  "-Dtest=TestNemesisPlcPatternCounts,TestNemesisPlcServiceQueue" test
```

Expected: compilation failure for the missing production types.

- [ ] **Step 4: Implement count derivation**

For every entry, call `PlcParser.decompressEntryRaw(rom, entry)`, validate the
length, and return an immutable count list. Synchronize ROM channel access
through the existing parser/decompressor boundary; do not introduce a second
decoder.

- [ ] **Step 5: Implement queue mutation and service**

Use a separate private active progress entry plus an `ArrayDeque` of queued
descriptors. Validate an entire submission before mutating either.
`servicePatterns` rejects nonpositive budgets and returns without effect when
there is no active entry. When the active entry reaches zero, clear it; do not
spend leftover budget on or implicitly prepare the next descriptor.

- [ ] **Step 6: Implement immutable rewind snapshots**

Capture the optional active entry separately from immutable queued descriptor
records in FIFO order. Restore validates all entries before replacing live
state.

- [ ] **Step 7: Run GREEN**

Run:

```bash
mvn -Dmse=off \
  "-Dsonic1.rom.path=s1.gen" \
  "-Dsonic2.rom.path=s2.gen" \
  "-Dtest=TestNemesisPlcPatternCounts,TestNemesisPlcServiceQueue,TestNemesisPlcRomVectors" test
```

Expected: all tests pass.

- [ ] **Step 8: Commit the kernel**

Commit:

```text
feat(plc): add deterministic Nemesis service queue
```

Stage `CHANGELOG.md` and add a concise entry describing logical S1/S2 PLC
readiness; use `Changelog: updated`.

---

### Task 3: Add game-owned S1 and S2 PLC services

**Files:**
- Create: `src/main/java/com/openggf/game/sonic1/resources/Sonic1PlcService.java`
- Create: `src/main/java/com/openggf/game/sonic2/resources/Sonic2PlcService.java`
- Create: `src/main/java/com/openggf/game/resources/PlcVBlankService.java`
- Modify: S1 game module/service registration file found by `rg -n "registerGameService|GameService" src/main/java/com/openggf/game/sonic1`
- Modify: S2 game module/service registration file found by `rg -n "registerGameService|GameService" src/main/java/com/openggf/game/sonic2`
- Test: `src/test/java/com/openggf/game/sonic1/resources/TestSonic1PlcService.java`
- Test: `src/test/java/com/openggf/game/sonic2/resources/TestSonic2PlcService.java`

**Interfaces:**
- Consumes: `NemesisPlcServiceQueue`, each game's ROM and PLC table address.
- Produces:

```java
public interface PlcVBlankService {
    void serviceLevelVBlank();
}

public final class Sonic1PlcService implements PlcVBlankService {
    public void replaceQueued(int plcId);
    public void append(int plcId);
    public void clearQueued();
    public void prepare();
    public void serviceLevelVBlank();       // three patterns
    public void serviceFastVBlank();        // nine patterns
    public boolean isBusy();
}

public final class Sonic2PlcService implements PlcVBlankService {
    public void replaceQueued(int plcId);
    public void append(int plcId);
    public void clearQueued();
    public void prepare();
    public void serviceLevelVBlank();       // three patterns
    public void serviceNormalVBlank();      // six patterns
    public boolean isBusy();
}
```

Methods that parse/decompress ROM data either declare `IOException` or translate
it once at the existing game resource boundary with game/PLC context.

- [ ] **Step 1: Write S1 façade RED tests**

Test that S1 parses from `Sonic1Constants.ART_LOAD_CUES_ADDR`, maps append,
queued replacement, and queued clear distinctly, exposes whole-buffer busy,
and advances exactly three or nine patterns. Assert `prepare()` is required
between entries and clear/replace rejects a non-idle decoder.

- [ ] **Step 2: Write S2 façade RED tests**

Test the same behavior against `Sonic2Constants.ART_LOAD_CUES_ADDR`, using
three- and six-pattern budgets. Add a capacity test using the exact behavior
pinned in Task 1.

- [ ] **Step 3: Run RED**

Run:

```bash
mvn -Dmse=off \
  "-Dsonic1.rom.path=s1.gen" \
  "-Dsonic2.rom.path=s2.gen" \
  "-Dtest=TestSonic1PlcService,TestSonic2PlcService" test
```

Expected: compilation failure for the services and lifecycle interface.

- [ ] **Step 4: Implement the façades**

Parse with `PlcParser.parse`, derive counts with
`NemesisPlcPatternCounts.derive`, validate queue capacity according to Task 1,
then delegate to the kernel. Do not register render art from these low-level
methods; Task 5 composes logical submission with the existing art provider.

- [ ] **Step 5: Register session-owned services**

Use each game module's existing service graph. Do not add static mutable
singletons. Add reset/close behavior through the established session lifecycle.

- [ ] **Step 6: Run GREEN and ownership guards**

Run:

```bash
mvn -Dmse=off \
  "-Dsonic1.rom.path=s1.gen" \
  "-Dsonic2.rom.path=s2.gen" \
  "-Dtest=TestSonic1PlcService,TestSonic2PlcService,TestStaticStateRewindCoverageGuard" test
```

Expected: all tests pass.

- [ ] **Step 7: Commit the game services**

Commit:

```text
feat(plc): add S1 and S2 queue services
```

Use `Changelog: updated` and stage the Task 2 changelog entry if the kernel and
façades land in separate commits with one shared player-facing entry.

---

### Task 4: Integrate exact VBlank preparation and service ordering

**Files:**
- Modify: `src/main/java/com/openggf/LevelFrameStep.java`
- Modify: S1 title/fade/results/special-stage lifecycle owners identified in Task 1
- Modify: S2 title/fade/results/special-stage lifecycle owners identified in Task 1
- Modify: game module lifecycle/provider interfaces as required by the reviewed call-site table
- Test: `src/test/java/com/openggf/TestPlcVBlankOrdering.java`
- Test: `src/test/java/com/openggf/game/sonic1/resources/TestSonic1PlcLifecycle.java`
- Test: `src/test/java/com/openggf/game/sonic2/resources/TestSonic2PlcLifecycle.java`

**Interfaces:**
- Consumes: `PlcVBlankService`, `Sonic1PlcService.prepare()`, `Sonic2PlcService.prepare()`.
- Produces: exact lifecycle calls for three/six/nine-pattern service and head preparation.

- [ ] **Step 1: Write ordering RED tests**

Use a recording service to assert:

```java
assertEquals(
        List.of("vblank-service", "events", "objects", "prepare"),
        observedCalls);
```

This example is the ordinary level-loop case only; title, fade, title-card,
results, and special-stage tests use their Task 1-pinned call order.

Add separate cases for a lag/VBlank-only row, ordinary level frame, title-card
frame, fade frame, special-stage results frame, and a completed-entry boundary.
The completed-entry case must prove the next entry is not serviced until a
later prepare/service pair.

Add an object-scan ordering test in which an earlier-slot producer submits a
PLC and a later-slot consumer observes busy in the same scan. Reverse the slots
and prove the earlier consumer's observation is unchanged. Repeat for the
reviewed clear/replace semantics.

- [ ] **Step 2: Run RED**

Run:

```bash
mvn -Dmse=off \
  "-Dtest=TestPlcVBlankOrdering,TestSonic1PlcLifecycle,TestSonic2PlcLifecycle" test
```

Expected: failures because the lifecycle does not call the services.

- [ ] **Step 3: Add the semantic VBlank hook**

Expose an optional `PlcVBlankService` through the game lifecycle/provider used
by `LevelFrameStep`. Invoke it at the selected VBlank boundary before ordinary
events/objects. Do not test game identity in `LevelFrameStep`.

- [ ] **Step 4: Add phase-owned fast service and preparation**

Connect title-card, fade, results, credits, and special-stage owners according
to the Task 1 table. Call `prepare()` only where the ROM calls
`RunPLC`/`RunPLC_RAM`; do not globally prepare every frame.

- [ ] **Step 5: Run GREEN**

Run:

```bash
mvn -Dmse=off \
  "-Dtest=TestPlcVBlankOrdering,TestSonic1PlcLifecycle,TestSonic2PlcLifecycle,TestLevelFrameHardwareTimingBoundaries" test
```

Expected: all tests pass, including the existing S3K hardware timing boundary
test.

- [ ] **Step 6: Commit lifecycle ordering**

Commit:

```text
feat(plc): service queues at ROM lifecycle boundaries
```

Use `Changelog: updated`.

---

### Task 5: Compose every implemented producer with eager rendering

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic1/Sonic1.java`
- Modify: `src/main/java/com/openggf/game/sonic1/Sonic1ObjectArtProvider.java`
- Modify: S1 producer owners from the Task 1 table
- Modify: `src/main/java/com/openggf/game/sonic2/Sonic2ObjectArtProvider.java`
- Modify: `src/main/java/com/openggf/game/sonic2/events/Sonic2ZoneEvents.java`
- Modify: S2 event/boss/result producer owners from the Task 1 table
- Test: `src/test/java/com/openggf/game/sonic1/resources/TestSonic1PlcProducerCoverage.java`
- Test: `src/test/java/com/openggf/game/sonic2/resources/TestSonic2PlcProducerCoverage.java`
- Test: `src/test/java/com/openggf/game/sonic2/TestSonic2RuntimePlcRendererRefresh.java`

**Interfaces:**
- Consumes: each game PLC service plus existing eager art loaders/providers.
- Produces: one game-owned request path per ROM PLC operation that performs validated logical submission and eager rendering without conflating readiness.

- [ ] **Step 1: Write producer-coverage RED tests**

Create table-driven tests from the Task 1 audit. For each implemented producer,
invoke its threshold or lifecycle action and assert:

```java
assertEquals(expectedPlcId, recordingPlcService.lastSubmission().plcId());
assertEquals(expectedOperation, recordingPlcService.lastSubmission().operation());
assertTrue(rendererOrSheetIsAvailable());
```

Include repeated submission after the sheet is cached and assert that the
logical submission count increments while renderer allocation remains stable.

- [ ] **Step 2: Run RED**

Run:

```bash
mvn -Dmse=off \
  "-Dtest=TestSonic1PlcProducerCoverage,TestSonic2PlcProducerCoverage,TestSonic2RuntimePlcRendererRefresh" test
```

Expected: failures for omitted logical submissions, including implemented S2
events whose comments currently say art is handled elsewhere.

- [ ] **Step 3: Add transactional game-owned request methods**

For each game, introduce an internal request method that first parses and
derives all logical work and materializes the eager render payload without
publishing it. Then commit queue mutation plus a non-throwing prepared renderer
registration; if publication can fail, roll back before exposing either
effect. Preserve existing renderer deduplication, but never use it to skip
logical submission. Invoke this method synchronously at the exact producer
call site inside an object/event scan.

- [ ] **Step 4: Route all implemented producers**

Replace comments or direct art-only calls with the reviewed operation and PLC
ID. Do not add zone checks to shared code. Leave nonexistent engine lifecycle
owners explicitly documented in the audit rather than inventing submissions.

- [ ] **Step 5: Run GREEN**

Run:

```bash
mvn -Dmse=off \
  "-Dsonic1.rom.path=s1.gen" \
  "-Dsonic2.rom.path=s2.gen" \
  "-Dtest=TestSonic1PlcProducerCoverage,TestSonic2PlcProducerCoverage,TestSonic2RuntimePlcRendererRefresh,TestSonic2PlcParser" test
```

Expected: all tests pass.

- [ ] **Step 6: Commit producer routing**

Commit:

```text
fix(plc): route runtime S1 and S2 PLC requests
```

Use `Changelog: updated`.

---

### Task 6: Migrate gameplay consumers and retire the FZ counter

**Files:**
- Delete: `src/main/java/com/openggf/game/sonic1/events/Sonic1FzPlcTimingQueue.java`
- Delete: `src/test/java/com/openggf/game/sonic1/events/TestSonic1FzPlcTimingQueue.java`
- Modify: `src/main/java/com/openggf/game/sonic1/events/Sonic1SBZEvents.java`
- Modify: `src/main/java/com/openggf/game/sonic1/objects/bosses/Sonic1FZBossInstance.java`
- Modify: implemented S1 result/Game Over/special-stage consumer owners
- Modify: `src/main/java/com/openggf/game/sonic2/events/Sonic2ARZEvents.java`
- Modify: `src/main/java/com/openggf/game/sonic2/objects/bosses/Sonic2ARZBossInstance.java`
- Modify: implemented S2 result/Game Over/special-stage/2P consumer owners
- Test: `src/test/java/com/openggf/game/sonic1/events/TestSonic1FinalZonePlcIntegration.java`
- Test: `src/test/java/com/openggf/game/sonic2/objects/bosses/TestSonic2ArzBossPlcReadiness.java`
- Test: per-consumer lifecycle tests identified by `rg -n "Results|GameOver|SpecialStageResults" src/test/java/com/openggf/game/sonic1 src/test/java/com/openggf/game/sonic2`

**Interfaces:**
- Consumes: injected `Sonic1PlcService.isBusy()` and `Sonic2PlcService.isBusy()`.
- Produces: ROM consumers that advance only from the shared queue's natural readiness.

- [ ] **Step 1: Write S1 Final Zone RED tests**

Assert:

```java
@Test void bossWaitsForWholeQueueAndAdvancesRngEveryBusyFrame()
@Test void queueCompletionAndCameraThresholdReleaseOnTheSameRomVisibleFrame()
@Test void unrelatedEarlierEntryExtendsTheBossWait()
@Test void existingFzRomVectorMatchesGeneralQueueDrain()
```

Use a recording/in-memory PLC service, not a hard-coded frame counter.

- [ ] **Step 2: Write S2 ARZ RED tests**

Assert:

```java
@Test void arzBossInitializationReturnsWhileAnyPlcEntryIsPending()
@Test void unrelatedEarlierEntryBlocksArzBossArtReadiness()
@Test void completionBeforeObjectScanAllowsInitializationThatFrame()
@Test void playerAndSidekickPositionBranchRunsOnlyAfterReadiness()
```

- [ ] **Step 3: Write remaining consumer RED tests**

For every implemented consumer in the Task 1 audit, assert its initial routine
does not advance while busy and advances at the first ROM-visible empty frame.

- [ ] **Step 4: Run RED**

Run:

```bash
mvn -Dmse=off \
  "-Dtest=TestSonic1FinalZonePlcIntegration,TestSonic2ArzBossPlcReadiness,*Results*,*GameOver*" test
```

Expected: the general queue is not yet consumed and ARZ initializes early.

- [ ] **Step 5: Inject queue readiness into consumers**

Use constructor/service injection consistent with surrounding objects.
Consumers only poll `isBusy()`; they do not service, clear queued descriptors,
prepare, or inspect queue entries.

- [ ] **Step 6: Remove the narrow FZ model**

Delete `Sonic1FzPlcTimingQueue`, its snapshot fields, and hard-coded tile
counts. Preserve the boss's per-wait-frame RNG increment and camera condition.
Update any stale comment claiming eager art makes the PLC always empty.

- [ ] **Step 7: Run GREEN**

Run:

```bash
mvn -Dmse=off \
  "-Dsonic1.rom.path=s1.gen" \
  "-Dsonic2.rom.path=s2.gen" \
  "-Dtest=TestSonic1FinalZonePlcIntegration,TestSonic2ArzBossPlcReadiness,*Results*,*GameOver*,TestSonic1SBZEvents" test
```

Expected: all migrated consumers pass.

- [ ] **Step 8: Commit consumer migration**

Commit:

```text
fix(plc): gate S1 and S2 consumers on queue readiness
```

Use `Changelog: updated`.

---

### Task 7: Register complete rewind ownership

**Files:**
- Modify: rewind registration/adapter files located with `rg -n "PlcProgressSnapshot|RewindSnapshottable|register.*rewind" src/main/java`
- Modify: game service snapshots if Task 3's module graph requires them
- Test: `src/test/java/com/openggf/game/rewind/TestNemesisPlcQueueRewind.java`
- Test: `src/test/java/com/openggf/game/rewind/TestS1S2PlcSessionOwnership.java`
- Test: `src/test/java/com/openggf/tests/TestRewindCoverageGuard.java`
- Test: `src/test/java/com/openggf/tests/TestStaticStateRewindCoverageGuard.java`

**Interfaces:**
- Consumes: `NemesisPlcQueueSnapshot`, game service capture/restore.
- Produces: session-scoped rewind adapters restoring queue state without resubmission.

- [ ] **Step 1: Write rewind RED tests**

Cover rewind at:

```text
empty
unprepared head
partially serviced head
entry completion before next preparation
prepared second entry
after append
after successful queued replacement while idle
after successful queued clear while idle
after rejected queued replacement while active, with state unchanged
after rejected queued clear while active, with state unchanged
```

For each, capture, advance across readiness, restore, replay, and assert the
same busy sequence and consumer release frame.

- [ ] **Step 2: Write session-isolation RED test**

Create sequential S1 and S2 sessions, submit work in the first, close it, and
assert the second begins empty with no shared cache or ordinal state.

- [ ] **Step 3: Run RED**

Run:

```bash
mvn -Dmse=off \
  "-Dtest=TestNemesisPlcQueueRewind,TestS1S2PlcSessionOwnership,TestRewindCoverageGuard,TestStaticStateRewindCoverageGuard" test
```

Expected: missing registration or coverage failure.

- [ ] **Step 4: Register capture and restore**

Restore the queue in place through the façade as the sole rewind registration
owner. Do not call parse, append, queued replacement, eager art registration,
or any trace API during restoration.

- [ ] **Step 5: Run GREEN**

Run the command from Step 3. Expected: all tests pass.

- [ ] **Step 6: Commit rewind support**

Commit:

```text
feat(rewind): capture S1 and S2 PLC progress
```

Use `Changelog: n/a: rewind coverage for the PLC runtime behavior already documented`.

---

### Task 8: Add trace-policy guards and route validation

**Files:**
- Modify: `src/test/java/com/openggf/trace/timing/TestHardwareTimingAuthorityGuard.java`
- Create: `src/test/java/com/openggf/trace/TestS1S2PlcComparisonOnlyGuard.java`
- Modify: relevant S1/S2 trace replay tests discovered with `rg -l "TraceReplay" src/test/java/com/openggf/tests/trace`
- Modify: `docs/status/trace-frontier-log.md` only if a measured frontier changes
- Modify: `docs/status/known-discrepancies.md` if an existing PLC discrepancy is resolved

**Interfaces:**
- Consumes: production PLC services and existing comparison-only trace policy.
- Produces: source guard preventing trace authority/hydration and measured trace evidence.

- [ ] **Step 1: Write the source guard**

Assert:

```text
Sonic1PlcService and Sonic2PlcService do not import com.openggf.trace.*
TraceFrame, TraceEvent, physics CSV, and aux parsers do not import either PLC service.
HardwareWorkKind does not contain PLC_QUEUE.
No replay/bootstrap class calls append, replaceQueued, clearQueued, prepare, servicePatterns,
serviceLevelVBlank, serviceFastVBlank, or serviceNormalVBlank on an S1/S2 PLC service.
```

- [ ] **Step 2: Run policy tests**

Run:

```bash
mvn -Dmse=off \
  "-Dtest=TestHardwareTimingAuthorityGuard,TestS1S2PlcComparisonOnlyGuard,TestHardwareTimingStreamLoader" test
```

Expected: all guards pass without adding a new timing kind or fixture stream.

- [ ] **Step 3: Run focused trace replays**

Discover actual class names and ROM files:

```bash
find . -maxdepth 1 -type f -name '*.gen' -print
rg -l "Final Zone|FZ|Aquatic Ruin|ARZ" src/test/java/com/openggf/tests/trace
```

Run every class printed by the discovery command individually with the correct
ROM property:

```bash
mvn -Dmse=off \
  "-Dsonic1.rom.path=s1.gen" \
  "-Dsonic2.rom.path=s2.gen" \
  "-Dtest=FullyQualifiedClassNamePrintedByDiscovery" test
```

Expected: zero new divergences. If a frontier moves or a previously passing
trace changes, update `docs/status/trace-frontier-log.md` with command, commit,
pass/fail, error count, and first-error frame/field.

- [ ] **Step 4: Run S1/S2 trace sweeps**

List the repository's actual S1 and S2 `*TraceReplay` class set:

```bash
rg -l "class .*TraceReplay" \
  src/test/java/com/openggf/tests/trace/s1 \
  src/test/java/com/openggf/tests/trace/s2
```

Run each concrete non-abstract class printed by the command using:

```bash
mvn -Dmse=off \
  "-Dsonic1.rom.path=s1.gen" \
  "-Dsonic2.rom.path=s2.gen" \
  "-Dtest=FullyQualifiedClassNamePrintedByDiscovery" test
```

Expected: no new failure relative to the integration baseline. Do not exclude
a class merely because it is slow.

- [ ] **Step 5: Commit guards and trace evidence**

Commit:

```text
test(trace): guard native S1 and S2 PLC timing
```

Use truthful documentation trailers based on whether the frontier or known
discrepancy files changed.

---

### Task 9: Full regression, documentation closure, and delivery

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `README.md` release/change-log section for merge into `develop`
- Modify: `docs/status/known-discrepancies.md`
- Modify: `docs/status/trace-frontier-log.md` when required by Task 8 results
- Reference: `docs/architecture/designs/2026-07-28-s1-s2-plc-service-queues.md`
- Reference: this plan

**Interfaces:**
- Consumes: all prior tasks.
- Produces: reviewed documentation, baseline comparison, merged `develop`, pushed integration commit, and cleaned implementation worktree.

- [ ] **Step 1: Close documentation**

Update the changelog and known-discrepancy ledger to state:

- S1/S2 PLC readiness is ROM-derived production state;
- rendering remains eager;
- S1 Final Zone uses the general S1 queue;
- S2 ARZ and implemented lifecycle consumers poll the general S2 queue; and
- traces provide no PLC completion authority.

Update `README.md`'s release/change-log section as required for the feature
branch merge into `develop`.

- [ ] **Step 2: Run focused verification**

Run all focused tests from Tasks 2-8 in one Maven invocation with both
discovered ROM paths. Expected: all pass.

- [ ] **Step 3: Record the updated integration baseline**

In the main workspace, preserve uncommitted user changes, then:

```bash
git fetch origin
git pull --ff-only
mvn -Dmse=off \
  "-Dsonic1.rom.path=s1.gen" \
  "-Dsonic2.rom.path=s2.gen" \
  "-Ds3k.rom.path=s3k.gen" test
```

Record the exact baseline failures, errors, and command. A red baseline is
acceptable; new failures are not.

- [ ] **Step 4: Run the full suite in the implementation worktree**

Run the same full-suite command with all three ROM properties. Compare every
failure/error with Step 3, then rerun all focused tests. Expected: no new or
worsened failure attributable to this branch.

- [ ] **Step 5: Review and commit documentation**

Delegate design/spec compliance and code-quality review. Resolve every valid
issue and repeat review until no blocking issue remains. Run:

```bash
git diff --check
git status --short
git diff --cached --name-only
```

Commit:

```text
docs(plc): record S1 and S2 queue parity
```

Use truthful trailers for every staged mapped document.

- [ ] **Step 6: Merge into the main workspace**

Merge the completed implementation branch directly into the branch checked
out in the main workspace without switching that workspace. Reconcile upstream
changes and preserve all unrelated user modifications.

- [ ] **Step 7: Run post-merge regression comparison**

Run the same full suite and focused suite on merged `develop`. Confirm no test
that passed on the baseline now fails and no baseline failure worsened due to
the delivered work.

- [ ] **Step 8: Push and clean up**

Push only the main-workspace branch. Verify the implementation worktree has no
unknown, user-authored, uncommitted, or unmerged changes; discard only
classified generated outputs. Remove the worktree, verify the implementation
branch is fully merged, delete that local branch, and prune worktree metadata.

- [ ] **Step 9: Report exact delivery state**

Report the design/implementation result, significant challenges, upstream
conflicts, every test command/outcome, pushed branch/commits, and completed
worktree/branch cleanup. Do not claim completion if fetch, pull, comparison,
merge, push, or cleanup failed.
