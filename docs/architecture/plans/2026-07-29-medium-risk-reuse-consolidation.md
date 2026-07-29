# Medium-Risk Reuse Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove three bounded duplicate paths in ROM detection, trace-tool CLI
parsing, and test-only BK2 input feeding without changing runtime behavior.

**Architecture:** Introduce small, ownership-specific helpers rather than a
cross-cutting framework: a fresh-instance built-in detector catalog, a
package-private CLI parsing kernel, and stateless test-only BK2 row access.
Public compatibility facades and caller-owned state remain in place.

**Tech Stack:** Java 21, Maven, JUnit Jupiter.

## Global Constraints

- Runtime asset bytes remain ROM-only.
- Do not introduce game-name or zone carve-outs in shared runtime code.
- Trace comparison data must not hydrate gameplay state.
- Preserve public ROM detector extension APIs and Sonic 2 fallback behavior.
- Preserve CLI syntax, exception types, messages, defaults, and validation.
- Keep all BK2 segment/lag advancement state at existing call sites.
- Use strict red-green-refactor TDD for every behavioral extraction.
- Run Maven with JDK 21.

---

### Task 1: Single-own built-in ROM detector orchestration

**Files:**

- Create: `src/main/java/com/openggf/game/BuiltInRomDetectors.java`
- Modify: `src/main/java/com/openggf/game/RomDetectionService.java`
- Modify: `src/main/java/com/openggf/game/GameModuleRegistry.java`
- Modify: `src/test/java/com/openggf/tests/rules/RomCache.java`
- Create: `src/test/java/com/openggf/game/TestBuiltInRomDetectors.java`
- Create: `src/test/java/com/openggf/game/TestRomDetectionService.java`
- Modify: `src/test/java/com/openggf/game/TestHeaderNameRomDetectors.java`

**Interfaces:**

- Produces public: `BuiltInRomDetectors.all(): List<RomDetector>`
- Produces public: `BuiltInRomDetectors.forGame(GameId): RomDetector`
- Retains: `RomDetectionService.detectAndSetModule(Rom): boolean` as a
  deprecated adapter to registry-owned result application
- Produces package-private:
  `GameModuleRegistry.applyDetectedModule(Optional<GameModule>): boolean`
- Retains fresh detector instances and the existing concrete detector classes.

- [ ] **Step 1: Add failing catalog contract tests**

Create table-driven tests asserting literal class order:

```java
assertEquals(
        List.of(Sonic3kRomDetector.class, Sonic1RomDetector.class,
                Sonic2RomDetector.class),
        BuiltInRomDetectors.all().stream().map(Object::getClass).toList());
assertInstanceOf(Sonic1RomDetector.class,
        BuiltInRomDetectors.forGame(GameId.S1));
assertNotSame(BuiltInRomDetectors.forGame(GameId.S1),
        BuiltInRomDetectors.forGame(GameId.S1));
```

The mutation caught is returning shared/stateful instances or changing the
declared built-in coverage.

- [ ] **Step 2: Run the catalog test and verify RED**

Run:

```bash
mvn -Dmse=off -Dtest=com.openggf.game.TestBuiltInRomDetectors test
```

Expected: test compilation fails because `BuiltInRomDetectors` does not exist.

- [ ] **Step 3: Implement the minimal fresh-instance catalog**

Use a public uninstantiable final composition-root class with public static methods. `all()`
constructs the three detectors and returns `List.of(...)`; `forGame` uses an
exhaustive `GameId` switch. Do not cache detector objects.

- [ ] **Step 4: Run the catalog test and verify GREEN**

Run the Step 2 command. Expected: all catalog tests pass.

- [ ] **Step 5: Add failing service behavior tests**

Construct an isolated service through a package-private constructor accepting a
detector list, leaving the singleton constructor private. Test with real
lightweight `RomDetector` fakes and a temporary open `Rom`:

- lower numeric priority runs first;
- equal priorities retain registration order;
- first matching detector wins;
- a throwing detector is skipped;
- unregister removes a detector;
- `getRegisteredDetectors()` cannot be mutated;
- null and closed ROMs return empty.

Each fake returns a distinct minimal `GameModule`; assertions inspect the
returned instance rather than fake invocation counts.

- [ ] **Step 6: Run the service test and verify RED**

Run:

```bash
mvn -Dmse=off -Dtest=com.openggf.game.TestRomDetectionService test
```

Expected: compilation fails because the isolated constructor does not exist.

- [ ] **Step 7: Implement isolated construction and catalog adoption**

Add a package-private constructor:

```java
RomDetectionService(List<? extends RomDetector> initialDetectors)
```

Register each supplied detector through `registerDetector`. The private
singleton constructor delegates to `BuiltInRomDetectors.all()`.

Change `RomCache.detectorFor` to map its `SonicGame` to `GameId` and call
`BuiltInRomDetectors.forGame`. Do not alter ROM caching or close ownership.

- [ ] **Step 8: Add failing registry-owned result application tests**

Test the new package-private
`GameModuleRegistry.applyDetectedModule(Optional<GameModule>)` directly:
present modules become the bootstrap default and return `true`; empty results
install a fresh Sonic 2 module and return `false`. Also characterize that the
deprecated service method and the registry's public detection method produce
the same success/fallback result for matching, unmatched, null, and closed
ROMs.

The mutation caught is reintroducing two implementations with divergent
fallback behavior.

- [ ] **Step 9: Run parity tests and verify RED**

Run:

```bash
mvn -Dmse=off \
  -Dtest=com.openggf.game.TestRomDetectionService,com.openggf.game.TestHeaderNameRomDetectors \
  test
```

Expected: compilation fails because `applyDetectedModule` does not exist.

- [ ] **Step 10: Replace duplicate mutation with the compatibility forwarder**

Move Optional-to-bootstrap success/fallback mutation into
`GameModuleRegistry.applyDetectedModule`. Make
`GameModuleRegistry.detectAndSetModule` detect and pass its result to that
operation. Annotate `RomDetectionService.detectAndSetModule` with `@Deprecated`;
it detects through `this` and passes its result to the same registry operation.
Add Javadoc naming the registry as mutation owner. Preserve both public method
signatures and avoid a service-to-registry-to-service recursion.

- [ ] **Step 11: Run focused and architecture verification**

Run:

```bash
mvn -Dmse=off \
  -Dtest=com.openggf.game.TestBuiltInRomDetectors,com.openggf.game.TestRomDetectionService,com.openggf.game.TestHeaderNameRomDetectors,com.openggf.tests.rules.TestRomCacheAvailability,com.openggf.tests.TestArchUnitTestRules,com.openggf.tests.TestArchUnitRules \
  test
```

Expected: all tests pass.

- [ ] **Step 12: Commit Task 1**

Stage only Task 1 files and commit:

```text
refactor: single-own ROM detector orchestration
```

Use the repository trailer block; justify `Changelog: n/a` as behavior-neutral
internal consolidation.

---

### Task 2: Share the exact trace-tool CLI parsing kernel

**Files:**

- Create: `src/main/java/com/openggf/tools/CliArguments.java`
- Modify: `src/main/java/com/openggf/tools/TraceCaptureTool.java`
- Modify: `src/main/java/com/openggf/tools/TraceBenchmarkTool.java`
- Create: `src/test/java/com/openggf/tools/TestCliArguments.java`
- Test: `src/test/java/com/openggf/tools/TraceCaptureToolArgsTest.java`
- Test: `src/test/java/com/openggf/tools/TestTraceBenchmarkToolArgs.java`

**Interfaces:**

- Produces: `CliArguments.requireValue(String[] argv, int index, String flag)`
- Produces: `CliArguments.parseInt(String raw)`
- Leaves benchmark minimum checks in `TraceBenchmarkTool.Args`.

- [ ] **Step 1: Add failing helper tests**

Test real results and exception behavior:

```java
assertEquals("value",
        CliArguments.requireValue(new String[] {"--flag", "value"}, 1, "--flag"));
assertEquals(-3, CliArguments.parseInt("-3"));
assertThrows(NumberFormatException.class,
        () -> CliArguments.parseInt("three"));
assertEquals("Missing value for --flag",
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.requireValue(
                        new String[] {"--flag"}, 1, "--flag")).getMessage());
```

The mutation caught is accepting a missing value or adding validation not owned
by the helper.

- [ ] **Step 2: Run helper tests and verify RED**

Run:

```bash
mvn -Dmse=off -Dtest=com.openggf.tools.TestCliArguments test
```

Expected: compilation fails because `CliArguments` does not exist.

- [ ] **Step 3: Implement the package-private helper**

Implement only the two methods. `parseInt` delegates directly to
`Integer.parseInt`; it must not trim or enforce bounds.

- [ ] **Step 4: Run helper tests and verify GREEN**

Run the Step 2 command. Expected: all helper tests pass.

- [ ] **Step 5: Characterize both existing parsers before migration**

Extend existing parser tests only where coverage is absent:

- capture accepts its currently accepted negative integer values;
- benchmark rejects values below its current minima with the exact current
  message;
- both retain missing-value messages and `NumberFormatException` for malformed
  integers.

Run these tests before changing production call sites; they must pass and form
the behavior baseline.

- [ ] **Step 6: Replace only duplicated require/parse calls**

Delete the two local `requireValue` implementations. Delegate raw integer
conversion to `CliArguments.parseInt`, while leaving benchmark's current
minimum comparison and exception construction in its parser.

- [ ] **Step 7: Run trace-tool parser verification**

Run:

```bash
mvn -Dmse=off \
  -Dtest=com.openggf.tools.TestCliArguments,com.openggf.tools.TraceCaptureToolArgsTest,com.openggf.tools.TestTraceBenchmarkToolArgs \
  test
```

Expected: all tests pass with unchanged parser behavior.

- [ ] **Step 8: Commit Task 2**

Commit the three production files and relevant tests:

```text
refactor: share trace tool CLI argument parsing
```

Use the repository trailer block and a behavior-neutral Changelog
justification.

---

### Task 3: Centralize test-only BK2 row input plumbing

**Files:**

- Create: `src/test/java/com/openggf/tests/trace/RecordedInputRows.java`
- Create: `src/test/java/com/openggf/tests/trace/TestRecordedInputRows.java`
- Modify: `src/test/java/com/openggf/tests/trace/s1/S1SpecialStageReplayHarness.java`
- Modify: `src/test/java/com/openggf/tests/trace/s2/S2SpecialStageReplayHarness.java`
- Modify: `src/test/java/com/openggf/tests/trace/s3k/S3kSpecialStageReplayHarness.java`
- Modify: `src/test/java/com/openggf/tests/trace/runs/TestS1GhzMazeRoundTripChain.java`
- Modify: `src/test/java/com/openggf/tests/trace/runs/TestS2EhzHalfpipeRoundTripChain.java`

**Interfaces:**

- Produces:
  public `RecordedInputRows(Bk2Movie movie, int absoluteBaseOffset)`
- Produces:
  public `LogicalInputSnapshot snapshotAt(int localRow)`
- Produces:
  public `void withLogicalOverride(int localRow, InputHandler input, Runnable action)`
- Owns no mutable cursor and accepts no `TraceData`.

- [ ] **Step 1: Add failing row-mapping tests**

Create a literal `Bk2Movie` whose physical rows distinguish P1/P2 direction,
action, and Start state. Assert:

- base offset plus local row selects the expected physical row;
- the predecessor is physical row `N - 1`, even if the caller skipped a local
  row;
- physical row zero uses a null predecessor and therefore literal just-pressed
  masks;
- negative or exhausted absolute rows throw before input mutation.

The production mutation caught is using the last stepped row instead of the
physical predecessor or applying the offset twice.

- [ ] **Step 2: Run row tests and verify RED**

Run:

```bash
mvn -Dmse=off -Dtest=com.openggf.tests.trace.TestRecordedInputRows test
```

Expected: compilation fails because `RecordedInputRows` does not exist.

- [ ] **Step 3: Implement snapshot lookup**

Use a public final class with a public constructor and public consumed methods.
Store final movie and base offset. `snapshotAt(localRow)` computes one absolute
index, validates it, reads the current row and physical predecessor, then calls
`RecordedInputSnapshots.fromBk2(current, previous)`.

- [ ] **Step 4: Run row-mapping tests and verify GREEN**

Run the Step 2 command. Expected: row-mapping tests pass.

- [ ] **Step 5: Add failing scoped-override tests**

Using a real `InputHandler`, assert:

- the callback observes the expected logical input;
- `hasLogicalOverride()` is false after normal return;
- it is false after a callback throws;
- an already-installed override causes `IllegalStateException` and remains
  installed;
- an invalid row fails before any override is installed.

- [ ] **Step 6: Run scoped tests and verify RED**

Run the Step 2 command. Expected: compilation fails because
`withLogicalOverride` does not exist.

- [ ] **Step 7: Implement scoped override**

Validate the row first, reject `input.hasLogicalOverride()`, install the
snapshot, invoke the `Runnable`, and unconditionally clear in `finally`.
Never catch or wrap the callback exception.

- [ ] **Step 8: Run all helper tests and verify GREEN**

Run the Step 2 command. Expected: all helper tests pass.

- [ ] **Step 9: Migrate the three special-stage harness feeders**

Create one `RecordedInputRows` beside each harness's movie/base offset. Replace
only the repeated current/previous row lookup plus set/try/finally/clear block.
Keep each harness's trace-frame counters and gameplay stepping exactly where
they are.

- [ ] **Step 10: Run special-stage harness verification**

Run the non-ROM helper/unit owners plus discovered ROM-backed abstract-harness
subclasses. At minimum:

```bash
mvn -Dmse=off \
  -Dtest=com.openggf.tests.trace.TestRecordedInputRows,com.openggf.game.TestSpecialStageInputMapper \
  test
```

Run the concrete harness owners using the ROM filenames discovered at the
project root:

```bash
mvn -Dmse=off \
  -Dsonic1.rom.path="$S1_ROM" \
  -Dsonic2.rom.path="$S2_ROM" \
  -Ds3k.rom.path="$S3K_ROM" \
  -Dtest=com.openggf.tests.trace.TestRecordedInputRows,com.openggf.game.TestSpecialStageInputMapper,com.openggf.tests.trace.s1.TestS1SpecialStageTraceReplay,com.openggf.tests.trace.s2.TestS2SpecialStageTraceReplay,com.openggf.tests.trace.s2.S2SpecialStageReplayDeterminismTest,com.openggf.tests.trace.s3k.TestS3kSpecialStageTraceReplay \
  test
```

Set `S1_ROM`, `S2_ROM`, and `S3K_ROM` to the actual discovered paths; record
skips separately from failures.

- [ ] **Step 11: Migrate the run-chain feeders**

Adopt `RecordedInputRows` only in local feeders declared by
`TestS1GhzMazeRoundTripChain` and `TestS2EhzHalfpipeRoundTripChain`. Leave
`AbstractRunChainTest` unchanged because its boundary-await paths rely on
`Bk2Movie.getFrame` clamping beyond the recorded frame count. Leave all segment
counters, lag branches, boundary tests, and cursor advancement statements
unchanged. Do not migrate the S2 completed-pass binder path.

- [ ] **Step 12: Run chain and logical-input verification**

Run:

```bash
mvn -Dmse=off \
  -Dsonic1.rom.path="$S1_ROM" \
  -Dsonic2.rom.path="$S2_ROM" \
  -Dtest=com.openggf.tests.trace.TestRecordedInputRows,com.openggf.game.TestSpecialStageInputMapper,com.openggf.game.rewind.TestLiveRewindLogicalInput,com.openggf.tests.trace.runs.TestS1GhzMazeRoundTripChain,com.openggf.tests.trace.runs.TestS2EhzHalfpipeRoundTripChain \
  test
```

Supply the discovered S1/S2 ROM properties. Expected: helper tests pass and
chain outcomes match the pre-change baseline.

- [ ] **Step 13: Commit Task 3**

Commit only the test-support helper and migrated test files:

```text
refactor: share recorded BK2 input rows in tests
```

All documentation trailers may be `n/a`; this task does not touch
`src/main`.

---

### Task 4: Tranche validation and documentation

**Files:**

- Create:
  `docs/architecture/validation/2026-07-29-medium-risk-reuse-consolidation.md`
- Modify: `README.md`

- [ ] **Step 1: Run the combined focused suite**

With `S1_ROM`, `S2_ROM`, and `S3K_ROM` set to discovered paths, run:

```bash
mvn -Dmse=off \
  -Dsonic1.rom.path="$S1_ROM" \
  -Dsonic2.rom.path="$S2_ROM" \
  -Ds3k.rom.path="$S3K_ROM" \
  -Dtest=com.openggf.game.TestBuiltInRomDetectors,com.openggf.game.TestRomDetectionService,com.openggf.game.TestHeaderNameRomDetectors,com.openggf.tests.rules.TestRomCacheAvailability,com.openggf.tools.TestCliArguments,com.openggf.tools.TraceCaptureToolArgsTest,com.openggf.tools.TestTraceBenchmarkToolArgs,com.openggf.tests.trace.TestRecordedInputRows,com.openggf.game.TestSpecialStageInputMapper,com.openggf.game.rewind.TestLiveRewindLogicalInput,com.openggf.tests.trace.s1.TestS1SpecialStageTraceReplay,com.openggf.tests.trace.s2.TestS2SpecialStageTraceReplay,com.openggf.tests.trace.s2.S2SpecialStageReplayDeterminismTest,com.openggf.tests.trace.s3k.TestS3kSpecialStageTraceReplay,com.openggf.tests.trace.runs.TestS1GhzMazeRoundTripChain,com.openggf.tests.trace.runs.TestS2EhzHalfpipeRoundTripChain,com.openggf.tests.TestArchUnitTestRules,com.openggf.tests.TestArchUnitRules \
  test
```

Record exact counts, failures, errors, skips, JDK, ROM paths, and commit.

- [ ] **Step 2: Run a clean full suite**

Run:

```bash
mvn -Dmse=off \
  -Dsonic1.rom.path="$S1_ROM" \
  -Dsonic2.rom.path="$S2_ROM" \
  -Ds3k.rom.path="$S3K_ROM" \
  clean test
```

If the known post-suite JVM shutdown hang occurs after all Surefire XML is
written, terminate only the idle tail and aggregate the XML. Compare the exact
failure/error set with a clean run at base `8c9b7378b` using the same three ROM
properties.

- [ ] **Step 3: Write validation evidence**

Document every command, result, baseline comparison, intentional deferral, and
review finding in the validation artifact. Do not claim a red baseline is green;
state whether the branch adds any regression.

- [ ] **Step 4: Add the required release summary**

Add one concise bullet to the current prerelease section of `README.md`
describing the behavior-neutral ownership consolidation.

- [ ] **Step 5: Run diff and policy checks**

Run:

```bash
git diff --check
git diff --cached --check
git status --short
```

Confirm no generated report, ROM link, disassembly link, or unrelated file is
staged.

- [ ] **Step 6: Commit validation documentation**

Commit the validation artifact and README with the repository trailer block.

- [ ] **Step 7: Verify the committed range and request final branch review**

Run:

```bash
git diff --check 8c9b7378b..HEAD
```

Provide the reviewer the design, plan, base SHA, head SHA, exact focused/full
test evidence, and explicit deferred scope. Fix every Critical or Important
issue and repeat review until green.

- [ ] **Step 8: Integrate under project workflow**

Fetch and fast-forward `develop` without overwriting user changes; compare the
updated baseline if it moved; merge into the main workspace; run the full
post-merge suite and compare failures; push only `develop`; then verify the
worktree is free of unknown changes, remove it, delete the fully merged local
feature branch, and prune metadata.

---

### Task 5: Resolve validation-discovered suite interaction

**Files:**

- Modify only after root-cause evidence identifies the owning production or
  test-reset boundary.
- Modify: `docs/architecture/archunit-exceptions.md`
- Modify: `src/test/java/com/openggf/tests/TestArchUnitRules.java`
- Modify: `src/test/resources/archunit/frozen/stored.rules`
- Modify:
  `docs/architecture/validation/2026-07-29-medium-risk-reuse-consolidation.md`
- Test: `src/test/java/com/openggf/tests/TestPlayableSpriteRollSpeed.java`
- Test:
  `src/test/java/com/openggf/trace/live/TestLiveTraceComparatorObserver.java`

**Interfaces:**

- Produces an automated reproduction of each branch-only failure or repeatable
  evidence identifying a non-order suite-context cause.
- Preserves production behavior and fixes only the owner proven by evidence.
- Aligns the shared-layer frozen baseline metadata to 14 without changing the
  frozen rule ID.

- [ ] **Step 1: Re-run design and plan review loops**

Delegate the amended design and plan for review. Fix all blocking/Important
issues and repeat until both are green before beginning investigation.
Then create a disposable clean detached worktree at exact base `8c9b7378b`,
link the same three discovered ROMs through the repository hook, and retain it
through Step 9. Keep its `target/surefire-reports` separate from the feature
worktree reports. Do not switch the main workspace or feature worktree.

- [ ] **Step 2: Capture exact failure evidence and suite predecessors**

Read the failing Surefire XML and dump files from the branch clean sweep,
including stack traces, test method names, timestamps, fork configuration,
environment, and resource diagnostics. Inventory every observable branch/base
suite-context difference. Use report order only as one candidate signal. Do not
edit code.

- [ ] **Step 3: Minimize ordered reproduction**

Based on Step 2 evidence, test one hypothesis at a time. If same-JVM order is
implicated, use one reused fork:

```bash
mvn -Dmse=off \
  -Dsonic1.rom.path="$S1_ROM" \
  -Dsonic2.rom.path="$S2_ROM" \
  -Ds3k.rom.path="$S3K_ROM" \
  -Dsurefire.forkCount=1 \
  -Dsurefire.reuseForks=true \
  -Dsurefire.runOrder=alphabetical \
  -Dtest=<fixed-alphabetical-class-set> \
  test
```

Choose a fixed class set whose alphabetical names place each candidate writer
before the consumer, and verify from the reports/process evidence that both ran
in the same fork. Reduce that set while retaining the failure. Do not treat the
order of a comma-separated `-Dtest` value as an execution guarantee. If the
failure requires the normal four-fork configuration, make assignment and
repetition deterministic and record the exact mechanism before inferring
causality. If resource or environment evidence is implicated, vary only that
one factor. Run the identical reduced command in a clean base worktree at
`8c9b7378b` with the same ROM properties and environment.

Expected: a repeatable branch/base distinction, or repeatable evidence that
the failure is environmental rather than branch-caused.

- [ ] **Step 4: Trace contaminated state to its owner**

Trace the inputs or state implicated by the confirmed hypothesis back through
their writers and readers. Name the observed difference, owner, expected
lifecycle, and evidence before proposing a fix. Do not assume the cause is a
singleton, static field, configuration service, or reset boundary.

- [ ] **Step 5: Add a failing ordered regression test**

Encode the smallest behavior-level reproduction in the relevant existing test
or reset-contract suite. Run it against the current branch and confirm the
expected failure. Do not add a source-text or test-order-only assertion.

- [ ] **Step 6: Implement the single root-cause fix**

Change only the owner identified in Step 4. Do not weaken the affected
assertions, add retries, skip tests, reorder the suite, or special-case the
failing test classes.

- [ ] **Step 7: Verify red-green and affected suites**

Run the regression test, minimized ordered reproducer, both originally failing
classes, and the exact 106-test focused command from Task 4. Record exact
counts and results.

- [ ] **Step 8: Correct architecture baseline metadata**

Update the published shared-layer count, rule metadata, and stored-rule mapping
from 20 to 14 while preserving the frozen rule ID and current 14-entry
violation file. Run both ArchUnit suites.

- [ ] **Step 9: Rerun clean same-ROM full comparison**

Use the disposable base worktree retained from Step 1; do not switch either the
main workspace or feature worktree. Run the branch and base worktrees with the
same normal four-fork configuration:

```bash
mvn -Dmse=off \
  -Dsonic1.rom.path="$S1_ROM" \
  -Dsonic2.rom.path="$S2_ROM" \
  -Ds3k.rom.path="$S3K_ROM" \
  clean test
```

Aggregate complete Surefire XML after the documented idle-tail hang. Integration
is allowed only if no test passing on base fails on branch. Preserve reports
separately, classify any generated files, then remove the disposable base
worktree after comparison.

- [ ] **Step 10: Amend validation evidence and commit**

Enumerate every base-only SnaleBlaster failing testcase, record the debugging
reproducer and root cause, update final full-suite counts, run both worktree and
range `git diff --check`, and commit the fix plus documentation with required
trailers.

- [ ] **Step 11: Resume final review and integration**

Run a whole-branch review and fix every Critical/Important finding to green.
Fetch and fast-forward the main-workspace `develop` branch without overwriting
user changes. If `develop` moved, create a clean worktree at the updated
integration baseline, rerun the same-ROM full suite there, and rerun focused
and full verification on the rebased/merged feature state.

Merge the reviewed feature branch into the main-workspace `develop` without
switching that workspace. Preserve its pre-existing dirty rewind report
byte-for-byte across the post-merge same-ROM full suite. Compare the merged
failure/error set with the updated clean baseline; push only `develop` when no
new regression exists. Then verify the feature worktree contains no unknown
changes, remove it, delete its fully merged local branch, and prune worktree
metadata.
