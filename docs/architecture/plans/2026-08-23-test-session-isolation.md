# Test Session Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every supported OpenGGF Maven test/build run a named, source-valid session whose temporary files, build outputs, reports, and generated diagnostics cannot be confused with another run.

**Architecture:** A standalone JDK 21 coordinator under `tools/testing/` owns a per-worktree regular-file `FileLock`, creates a timestamp/PID/nonce session root, launches Maven with all temporary-directory variables redirected, and writes a JSON manifest plus start/end markers. Maven lifecycle guards validate the active session manifest and capability for `pre-clean` and `validate`; test/report writers resolve all generated paths from session properties, and CI/release consume manifest paths rather than fixed `target` globs.

**Tech Stack:** Java 21 standard library for the coordinator, POSIX shell and PowerShell thin wrappers, Maven/Surefire 3.2.5, JUnit 5 for project-side tests, GitHub Actions job outputs, and disposable-process integration tests. The standalone coordinator and its self-tests use no third-party classes.

**Spec:** `docs/architecture/designs/2026-08-23-test-session-isolation-design.md`

## Global Constraints

- Build and test with JDK 21; verify Maven's JVM with `mvn -v`.
- The supported isolation guarantee covers coordinator-launched `clean`, `compile`, `test`, `test-compile`, `verify`, and `package` lifecycles; raw output-producing direct Maven goals are unsupported and non-certifying.
- The coordinator holds `openggf-test-session.lock/lease.lock` with Java `FileChannel`/`FileLock` for the entire child-process lifetime; `owner.json` is metadata, not the lock primitive.
- The namespace creates `lease.lock` with `CREATE_NEW` before opening and locking it; `owner.json` is metadata, not the lock primitive.
- There is no deletable in-worktree lease fallback. If Git metadata is not writable, use an explicitly provisioned external lock root or writable managed scratch outside the worktree; otherwise fail before Maven.
- The session capability guards stale/misrouted invocations but is not a hostile same-user security boundary.
- Parent Maven and child processes receive the session temp root through `MAVEN_OPTS`, `JAVA_TOOL_OPTIONS`, `TMPDIR`, `TMP`, `TEMP`, and Surefire `argLine`; existing values are preserved and the session setting is appended.
- The source digest covers tracked and non-ignored untracked files; `runtime_inputs_digest` covers supplied ROMs, local config, mods, and other ignored inputs named by the run.
- Guard identity is carried by the exact properties `openggf.session.manifest`, `openggf.session.capability`, `openggf.session.run-id`, `openggf.session.command-hash`, `openggf.session.worktree`, `openggf.session.lease-path`, and `openggf.session.allowed-phases`. The coordinator writes/populates all seven; Maven passes all seven to the pre-clean/validate guard; raw lifecycle invocations have none and reject before mutation.
- Generated reports use `CREATE_NEW`, adjacent owner sidecars, and post-run manifest inventory; no writer mutates the manifest concurrently.
- Do not use `--no-verify`, destructive broad cleanup, trace hydration, zone/route/frame exceptions, or hard-coded test-route behavior.
- `AGENTS.md` and `CLAUDE.md` must remain synchronized; all required commit trailers must be present on non-merge commits.
- Commit examples provide each required trailer exactly once; the repository's prepare hook appends only missing keys, so the implementation must not add a second trailer block.

---

## File map

The implementation is split by ownership rather than by one large launcher file:

- `tools/testing/TestSessionCoordinator.java` — standalone JDK 21 coordinator, session paths, manifest, lease, capability, source/input digests, child process, export file, and cleanup/reclaim commands.
- `tools/testing/test-session.sh` and `tools/testing/test-session.ps1` — thin platform entrypoints that locate the coordinator and forward arguments without changing semantics.
- `.gitignore` — tracked exceptions for `tools/testing/**` and ignored `.openggf/test-runs/` session output.
- `pom.xml` — configurable build/report/temp/artifact properties, shared Surefire configuration, lifecycle guards, and no Git-config mutation.
- `src/test/java/com/openggf/tests/TestSessionOutputPaths.java` — session-aware test diagnostic/report path resolver and report-owner metadata API.
- `src/test/java/com/openggf/tests/SessionInvocationExtension.java` — JUnit 5 invocation scope exposing the hashed `ExtensionContext.getUniqueId()`.
- `src/test/java/com/openggf/tests/TestSessionOutputPathsTest.java` and `src/test/java/com/openggf/tests/TestSessionInvocationExtensionTest.java` — unit coverage for defaults, session overrides, invocation identity, collision handling, and sidecars.
- `src/test/java/com/openggf/tests/trace/TraceReportWriter.java` plus the trace base classes — migrated report output and owner metadata.
- `src/main/java/com/openggf/tools/TraceTriageTool.java` — manifest/session-aware report default while retaining explicit `--report` input behavior.
- `src/main/java/com/openggf/tools/timing/S3kLoadTimeProfileGenerator.java` — session-aware default for generated timing publication paths while preserving explicit output arguments.
- `.github/workflows/ci.yml` and `.github/workflows/release.yml` — coordinator invocations, manifest export, and manifest-based report/artifact checks.
- `src/packaging/assemble-macos-app.sh` and native POM profile — session artifact/distribution roots.
- `tools/testing/TestSessionProcessHarness.java` — disposable external process harness for concurrency, interruption, read-only temp, source mutation, raw lifecycle rejection, and stale lease cases.
- `tools/testing/install-hooks.sh` and `tools/testing/install-hooks.ps1` — explicit hook bootstrap, no longer run from Maven.
- `AGENTS.md`, `CLAUDE.md`, `README.md`, `docs/guide/contributing/dev-setup.md`, `docs/guide/contributing/testing.md`, `docs/guide/contributing/trace-replay.md`, `docs/guide/contributing/trace-framework-reference.md`, `docs/guide/playing/getting-started.md`, `docs/guide/contributing/tutorial-implement-object.md`, and `docs/guide/PLAN.md` — coordinator-first workflow documentation. Historical evidence under `docs/status/**` and `docs/changelog/**` is excluded from command rewriting.

### Task 1: Add the standalone coordinator and tracked entrypoints

**Files:**

- Create: `tools/testing/TestSessionCoordinator.java`
- Create: `tools/testing/test-session.sh`
- Create: `tools/testing/test-session.ps1`
- Modify: `.gitignore`
- Create: `tools/testing/TestSessionCoordinatorSelfTest.java`

**Interfaces:**

- `TestSessionCoordinator` accepts `--export-file <path>`, `--lock-root <path>`, `--allow-system-tmp`, `--reclaim <lease-path>`, `--guard <phase>`, and a child command after `--`.
- Run mode emits `OPENGGF_TEST_RUN_START` and `OPENGGF_TEST_RUN_END` lines and exits with the child exit code, except that invalid identity/startup states return nonzero even if the child returned zero.
- `manifest.json` contains `run_id`, `state`, `manifest`, `worktree`, `lease_path`, `source_digest`, `runtime_inputs_digest`, `build_root`, `surefire_reports`, `trace_reports`, `artifact_root`, `distribution_root`, `reports`, and `artifacts`.
- The lease path is `<git-dir>/openggf-test-session.lock/lease.lock` or an explicitly supplied external lock root keyed by the canonical worktree hash. The coordinator creates the namespace directory, creates `lease.lock` with `CREATE_NEW`, acquires an exclusive `FileLock`, writes `owner.json` atomically, and holds the channel until Maven exits and the final manifest is written.
- Run IDs use UTC timestamp + coordinator PID + cryptographic random suffix, for example `20260823T101530Z-p4812-a7f93c`.

- [ ] **Step 1: Write the standalone coordinator contract test.**

  In `TestSessionCoordinatorSelfTest.java`, create a temporary external lock/output root and assert that the coordinator produces the required manifest keys, start/end markers, unique run IDs, and an owner namespace containing a regular `lease.lock`. Add deterministic checks for staged namespace publication, initialization metadata, post-lock owner publication, the second `reclaiming.json` check, and retained recovery markers after a simulated interrupted startup/reclaim.

  ```bash
  test_root="$(mktemp -d)"
  javac --release 21 -d "$test_root/classes" \
    tools/testing/TestSessionCoordinator.java \
    tools/testing/TestSessionCoordinatorSelfTest.java
  java -ea -cp "$test_root/classes" TestSessionCoordinatorSelfTest "$test_root"
  ```

  Expected before implementation: compilation fails because the coordinator and contract types do not exist.

- [ ] **Step 2: Implement root resolution and run identity.**

  Implement `resolveOutputRoot`, `createRunId`, and the writable probe. Honor `OPENGGF_TEST_ROOT` as a hard override; otherwise call `agent-scratch new` when available, then use `.openggf/test-runs`, and use system temp only with `--allow-system-tmp`. Reject non-absolute, unwritable, newline-containing, or ownership-invalid paths.

- [ ] **Step 3: Implement the lease and manifest lifecycle.**

  Add `LeaseHandle implements AutoCloseable` and `ManifestWriter`. Create a uniquely named sibling staging directory, write `initializing.json` with owner metadata, and publish the complete directory with `StandardCopyOption.ATOMIC_MOVE` required; `AtomicMoveNotSupportedException` is a fail-closed error with the staging path retained. Then create `lease.lock` with `CREATE_NEW`, open it with `FileChannel.open(..., WRITE)`, and acquire `tryLock`; handle `FileAlreadyExistsException` as the concurrent-owner path. Publish `owner.json` with an atomic `owner.json.tmp` → `owner.json` move, and retain `initializing.json` until the owner is visible. Contenders that see initialization metadata without an owner never create a lock in that state; attempt 1 is immediate, then up to three retries sleep 50 ms, 100 ms, and 200 ms before retrying, and a fourth failure returns exit code 75 (`EX_TEMPFAIL`) with no Maven manifest. Explicit reclaim attempt 1 atomically claims `reclaiming.json` with `CREATE_NEW`; if it exists, a dead recorded reclaimer may be resumed and a live one is retryable contention. The marker remains across retries. Explicit reclaim uses exactly the same four-attempt policy: attempt 1 is immediate, retries occur after 50/100/200 ms, every attempt revalidates the target owner and lease and reacquires/rechecks the lock marker when `lease.lock` exists, then closes the channel before each Windows-safe `ATOMIC_MOVE` rename; exhaustion returns exit 75 and retains all markers/namespaces. A normal coordinator that sees `reclaiming.json` before or immediately after lock acquisition releases any lock, does not launch Maven, and follows the same initial-plus-three-retry policy. Failed namespaces and markers are retained rather than deleted.

- [ ] **Step 4: Implement the child process and platform wrappers.**

  Preserve the caller's environment, append the session JVM temp option to `MAVEN_OPTS` and `JAVA_TOOL_OPTIONS`, set `TMPDIR`, `TMP`, and `TEMP`, launch the command after `--`, stream output to `maven.log` and stdout, trap process termination in the Java shutdown hook, and write the final manifest/end marker. The shell and PowerShell wrappers only locate the source file and invoke `java --source 21`.

- [ ] **Step 5: Add the tracked-tool ignore rules and run the standalone test.**

  Add these `.gitignore` rules after the existing `tools/*` rule:

  ```gitignore
  !tools/testing/
  !tools/testing/**
  /.openggf/test-runs/
  ```

  Run the compile/self-test command from Step 1 and `git diff --check`.

- [ ] **Step 6: Commit the coordinator foundation.**

  ```bash
  git add .gitignore tools/testing/TestSessionCoordinator.java tools/testing/test-session.sh tools/testing/test-session.ps1 tools/testing/TestSessionCoordinatorSelfTest.java
  git commit -m "feat: add isolated test session coordinator" \
    -m "Changelog: updated" \
    -m "Guide: n/a: documentation follows in a later task" \
    -m "Known-Discrepancies: n/a" \
    -m "S3K-Known-Discrepancies: n/a" \
    -m "Agent-Docs: n/a: mirrored workflow docs follow in a later task" \
    -m "Configuration-Docs: n/a" \
    -m "Skills: n/a"
  ```

### Task 2: Add Maven session guards and move all Maven paths behind properties

**Files:**

- Modify: `pom.xml:properties`, `<build>`, `install-git-hooks`, default Surefire, `guards`, `trace-replay`, `trace-segments`, `trace-replay-r7`, `trace-diagnostics`, `benchmarks`, and native profiles.
- Modify: `tools/testing/TestSessionCoordinator.java` guard mode.
- Create: `tools/testing/TestSessionGuardSelfTest.java`
- Modify: `src/test/java/com/openggf/tests/TestBuildToolingGuard.java`

**Interfaces:**

- Maven properties: `openggf.session.manifest`, `openggf.session.capability`, `openggf.build.directory`, `openggf.test.tmpdir`, `openggf.surefire.reports`, `openggf.trace.reports`, `openggf.test.diagnostics`, `openggf.artifact.root`, and `openggf.distribution.root`.
- Defaults resolve to the current `target` layout; coordinator runs override every property with absolute session paths.
- `TestSessionCoordinator --guard pre-clean` and `--guard validate` read the manifest/capability, verify canonical worktree, lease path, run ID, command hash, `RUNNING` state, and allowed phase, then exit zero or print an actionable rejection.
- The guard properties are passed as Maven/system properties with these exact names: `openggf.session.manifest`, `openggf.session.capability`, `openggf.session.run-id`, `openggf.session.command-hash`, `openggf.session.worktree`, `openggf.session.lease-path`, and `openggf.session.allowed-phases`. The coordinator sets them from the active manifest before child launch; the POM's guard reads them from the same property names. `allowed-phases` is a comma-separated list such as `pre-clean,validate` for `mvn clean test`; `command-hash` is the SHA-256 of the canonical child argv. The guard rejects if any property is missing, mismatches the manifest/capability, or names a lifecycle phase outside the supported list.

- [ ] **Step 1: Add failing guard contract tests.**

  In `TestSessionGuardSelfTest.java`, create a disposable manifest with each of: missing capability, wrong worktree, wrong command hash, non-running state, and valid state. Assert the first four reject and the valid case accepts both `pre-clean` and `validate` for one `mvn clean test` command.

  Verify the standalone contract independently of Maven:

  ```bash
  guard_test_root="$(mktemp -d)"
  javac --release 21 -d "$guard_test_root/classes" \
    tools/testing/TestSessionCoordinator.java \
    tools/testing/TestSessionGuardSelfTest.java
  java -ea -cp "$guard_test_root/classes" TestSessionGuardSelfTest "$guard_test_root"
  ```

- [ ] **Step 2: Add the shared POM properties and build directory.**

  Set the default properties before profiles and use `<directory>${openggf.build.directory}</directory>`. Configure every Surefire profile to use the shared temp/report properties rather than literal `${project.build.directory}/test-tmp` or default report paths. Pass session properties through `systemPropertyVariables`.

- [ ] **Step 3: Bind the lifecycle guards.**

  Add a `pre-clean` execution before Maven clean and a `validate` execution before compilation. The guard rejects an unwrapped `mvn clean`, `compile`, `test`, `test-compile`, `verify`, or `package`; `mvn clean test` from the coordinator validates both phases using the same still-running manifest/capability.

- [ ] **Step 4: Remove Maven's shared Git-config mutation.**

  Delete the `install-git-hooks` antrun execution that runs `git config core.hooksPath`. Retain only session-safe directory creation/configuration in Maven. Add a guard assertion that `pom.xml` has no lifecycle command that writes `.git/config`.

- [ ] **Step 5: Run focused guard/build checks.**

  ```bash
  lock_root="$(agent-scratch path tasks)/test-session-locks"
  tools/testing/test-session.sh --lock-root "$lock_root" -- \
    mvn -Dmse=off -Pguards -Dsurefire.forkCount=1 \
    '-Dtest=com.openggf.tests.TestBuildToolingGuard' test
  tools/testing/test-session.sh --lock-root "$lock_root" -- \
    mvn -Dmse=off -DskipTests validate
  ```

  Expected: the guarded coordinator run succeeds without attempting `/tmp` or `.git/config`; raw lifecycle invocation rejects before compilation/clean.

- [ ] **Step 6: Commit the Maven boundary.**

  ```bash
  Review `git diff --name-only`, then stage only `pom.xml`, `tools/testing/TestSessionCoordinator.java`, `tools/testing/TestSessionGuardSelfTest.java`, and `src/test/java/com/openggf/tests/TestBuildToolingGuard.java` if changed; do not stage unrelated user changes.
  git commit -m "feat: guard Maven lifecycle with test sessions" \
    -m "Changelog: updated" -m "Guide: n/a" -m "Known-Discrepancies: n/a" \
    -m "S3K-Known-Discrepancies: n/a" -m "Agent-Docs: n/a" \
    -m "Configuration-Docs: n/a" -m "Skills: n/a"
  ```

### Task 3: Add session-aware test output and invocation ownership

**Files:**

- Create: `src/test/java/com/openggf/tests/TestSessionOutputPaths.java`
- Create: `src/test/java/com/openggf/tests/SessionInvocationExtension.java`
- Create: `src/test/java/com/openggf/tests/TestSessionOutputPathsTest.java`
- Create: `src/test/java/com/openggf/tests/TestSessionInvocationExtensionTest.java`
- Modify: `src/test/java/com/openggf/tests/trace/TraceReportWriter.java`
- Modify: `src/test/java/com/openggf/tests/trace/AbstractTraceReplayTest.java`
- Modify: `src/test/java/com/openggf/tests/trace/AbstractCreditsDemoTraceReplayTest.java`
- Modify: `src/test/java/com/openggf/tests/trace/runs/AbstractRunChainTest.java`
- Modify: `src/test/java/com/openggf/tests/trace/s1/AbstractS1SpecialStageTraceReplayTest.java`
- Modify: `src/test/java/com/openggf/tests/trace/s2/AbstractS2SpecialStageTraceReplayTest.java`
- Modify: `src/test/java/com/openggf/tests/trace/s3k/AbstractS3kSpecialStageTraceReplayTest.java`
- Modify: `src/main/java/com/openggf/tools/TraceTriageTool.java`

**Interfaces:**

- `TestSessionOutputPaths.traceReports()`, `diagnostics(String namespace)`, and `artifactRoot()` return the session property path or the current legacy default when no session is active.
- `SessionInvocationExtension` exposes `SessionInvocation.current()` with `className`, `methodName`, `parameterIndex`, `invocationId`, and `displayName`; `invocationId` is the first 16 lowercase hex characters of SHA-256(`ExtensionContext.getUniqueId()`).
- `TestSessionOutputPaths.allocateReport(profile, className, methodName, parameterIndex, invocationId, laneId, logicalKey, suffix)` returns a `ReportAllocation` containing `logicalKey`, `ownerKey`, `physicalPath`, and `metadataPath`; both report and metadata use `CREATE_NEW`/atomic publication.
- `TraceReportWriter` registers only the sidecar; the coordinator builds the manifest `reports` array after Maven exits.

- [ ] **Step 1: Write failing resolver and invocation tests.**

  Assert that no-session defaults remain `target/trace-reports`, a session property resolves to the supplied absolute path, two distinct JUnit unique IDs produce distinct invocation IDs, repeated/template/dynamic IDs remain distinct, and a second allocation for the same owner key fails rather than overwriting.

- [ ] **Step 2: Implement the resolver and extension.**

  Use `System.getProperty("openggf.trace.reports")`, `openggf.test.diagnostics`, and `openggf.artifact.root` with the existing defaults. Use JUnit `BeforeEachCallback`/`AfterEachCallback` and an `ExtensionContext.Namespace` store to install the current invocation scope. Because test code calls `SessionInvocation.current()` without an `ExtensionContext`, the extension uses a private `ThreadLocal<Deque<SessionInvocation>>` bridge scoped to the current invocation: `BeforeEach` pushes the value and records the owner thread, while `AfterEach` pops and restores the previous value in a `finally` block on every success/failure path. Nested/repeated/parameterized invocations therefore cannot leak scope, and Surefire forks and parallel test threads cannot share it; calls outside an extension-owned invocation fail with an actionable exception.

- [ ] **Step 3: Migrate trace report bases.**

  Add the extension to the abstract trace bases so inherited tests receive it. Replace each `Path.of("target", "trace-reports")` with the resolver. Pass an explicit lane ID for run-chain segment reports and special-stage indexes; do not use frame numbers, route names, or game-name branches to decide gameplay behavior. Update report existence assertions to use the allocated physical path.

- [ ] **Step 4: Migrate `TraceReportWriter` and triage defaults.**

  Write JSON/context reports to allocated paths, publish adjacent owner metadata, and make `TraceTriageTool` use `System.getProperty("openggf.trace.reports", DEFAULT_REPORT_DIR)` when no explicit `--report` argument is supplied. Add tests for both explicit and default triage paths.

- [ ] **Step 5: Run focused trace/output tests.**

  ```bash
  tools/testing/test-session.sh -- \
    mvn -Dmse=off -Dsurefire.forkCount=1 \
    '-Dtest=com.openggf.tests.TestSessionOutputPathsTest,com.openggf.tests.TestSessionInvocationExtensionTest,com.openggf.tests.trace.TestS2SpecialStageTraceReplay' test
  ```

  Verify that every report path printed by the test is under the session root and that no fixed `target/trace-reports` file is created.

- [ ] **Step 6: Commit trace output ownership.**

  ```bash
  Review `git diff --name-only`, then stage `src/main/java/com/openggf/tools/TraceTriageTool.java` and only the changed paths from this task's explicit Files list; do not stage the whole test tree.
  git commit -m "feat: isolate trace report outputs by session" \
    -m "Changelog: updated" -m "Guide: n/a" -m "Known-Discrepancies: n/a" \
    -m "S3K-Known-Discrepancies: n/a" -m "Agent-Docs: n/a" \
    -m "Configuration-Docs: n/a" -m "Skills: n/a"
  ```

### Task 4: Migrate remaining generated diagnostics and package outputs

**Files:**

- Modify: `src/test/java/com/openggf/audio/TestLiveCaptureSurvivesBackendSwap.java`
- Modify: `src/test/java/com/openggf/audio/TestSmpsRepeatedPlaybackBenchmark.java`
- Modify: `src/test/java/com/openggf/audio/TestSmpsRepeatedPlaybackBenchmarkComparator.java`
- Modify: `src/test/java/com/openggf/audio/SmpsRepeatedPlaybackBenchmarkComparator.java`
- Modify: `src/test/java/com/openggf/audio/synth/TestYm2612ChipGpgxParity.java`
- Modify: `src/test/java/com/openggf/capture/CaptureRecorderTest.java`
- Modify: `src/test/java/com/openggf/capture/LiveCaptureControllerTest.java`
- Modify: `src/test/java/com/openggf/capture/LiveCaptureRecorderFactoryTest.java`
- Modify: `src/test/java/com/openggf/game/TestInstaShieldVisual.java`
- Modify: `src/test/java/com/openggf/game/rewind/RewindBenchmark.java`
- Modify: `src/test/java/com/openggf/game/rewind/RewindRoundTripHarness.java`
- Modify: `src/test/java/com/openggf/game/rewind/TestRewindManySidekickPerformanceTrace.java`
- Modify: `src/test/java/com/openggf/game/rewind/schema/TestRewindFieldDispositionGuard.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/TestS3kCnzVisualCapture.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/dataselect/S3kDataSelectVisualCapture.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/dataselect/TestS3kDataSelectPresentation.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/specialstage/TestS3kSpecialStageResultsVisual.java`
- Modify: `src/test/java/com/openggf/graphics/VisualRegressionTest.java`
- Modify: `src/test/java/com/openggf/level/TestLevelRendererBackgroundSamplingPerformance.java`
- Modify: `src/test/java/com/openggf/tests/TestAizFireCurtainGpuDiag.java`
- Modify: `src/test/java/com/openggf/tests/trace/SlotOccupancyProbe.java`
- Modify: `src/test/java/com/openggf/graphics/shaderlib/TestDisplayShaderPackDiagnostics.java`
- Modify: `src/test/java/com/openggf/tools/TestTraceCaptureUnifiedAudio.java`
- Modify: `src/test/java/com/openggf/tools/TraceCaptureSessionTest.java`
- Modify: `src/test/java/com/openggf/tools/audio/parity/TestS1AudioParityCli.java`
- Modify: `src/test/java/com/openggf/tools/audio/parity/TestS1OpenGgfAudioCapture.java`
- Modify: `src/test/java/com/openggf/tools/audio/timeline/TestS1GameplayAudioTimelineCli.java`
- Modify: `src/main/java/com/openggf/configuration/SonicConfigurationService.java`
- Modify: `src/main/java/com/openggf/tools/BenchmarkCompareTool.java`
- Modify: `src/main/java/com/openggf/tools/TraceBenchmarkTool.java`
- Modify: `src/main/java/com/openggf/tools/TraceCaptureTool.java`
- Modify: `src/main/java/com/openggf/tools/audio/parity/S1AudioParityTool.java`
- Modify: `src/main/java/com/openggf/tools/audio/timeline/S1GameplayAudioTimelineTool.java`
- Modify: `src/main/java/com/openggf/tools/timing/S3kLoadTimeProfileGenerator.java`
- Modify: `src/main/resources/config.yaml`
- Modify: `tools/audio/run_complete_audio_parity.sh`
- Modify: `tools/audio/run_s1_audio_parity.sh`
- Modify: `tools/audio/run_s1_ghz1_gameplay_audio_timeline.sh`
- Modify: `tools/audio/README.md`
- Modify: `src/test/java/com/openggf/tests/trace/runs/AbstractRunChainTest.java`
- Modify: `src/test/java/com/openggf/game/sonic1/objects/TestRewindFixS1Batch8Codecs.java`
- Modify: `src/test/java/com/openggf/game/sonic1/objects/TestRewindFixS1Batch9Codecs.java`
- Modify: `src/test/java/com/openggf/game/sonic1/objects/TestRewindFixS1Batch10Codecs.java`
- Modify: `src/test/java/com/openggf/game/rewind/TestScalarOnlyCodecDeletion.java`
- Modify: `src/test/java/com/openggf/game/rewind/TestS3kAizEndBossGraphRewind.java`
- Modify: `src/test/java/com/openggf/game/rewind/TestS3kHczEndBossGraphRewind.java`
- Modify: `src/packaging/assemble-macos-app.sh`
- Modify: `pom.xml` native profile and artifact configuration

**Interfaces:**

- Every generated-output writer calls `TestSessionOutputPaths.diagnostics(namespace)` or `allocateReport(...)`.
- Fixture inputs, expected default strings, and explicit user-supplied paths remain unchanged; tests that assert the legacy default do so only when no session properties are set.
- Native packaging consumes `${project.build.directory}`, `${openggf.artifact.root}`, and `${openggf.distribution.root}` instead of `${basedir}/target` or fixed `dist` paths.
- Deliberate inventory exclusions are `src/test/java/com/openggf/configuration/CaptureConfigDefaultsTest.java` (asserts the no-session `target/trace-videos` default but does not write), `src/test/java/com/openggf/tests/TestTempFiles.java` and `src/test/java/com/openggf/tests/TestNoLeakedTemporaryFiles.java` (exercise/inspect temporary-file cleanup rather than named reports), and trace fixture classes whose only output call is inherited from the migrated abstract trace bases. The guard must assert these exclusions contain no generated writer; it must fail if a future edit turns one into a writer.

- [ ] **Step 1: Add a fixed-output inventory guard.**

  First run a repository-wide inventory over `src/main`, `src/test`, `src/main/resources`, `src/packaging`, and `tools/audio` for `Path.of`, `Paths.get`, `Files.write*`, `newBufferedWriter`, `OutputStream`, shell redirection, `target/`, and `dist/`; classify every hit as a migrated writer, explicit user output, fixture/default assertion, or archival text. Extend `TestBuildToolingGuard` to scan the exact migrated and exclusion files listed in this task for generated writes to literal `target`, `target/trace-reports`, `target/audio-parity`, `target/trace-videos`, `Path.of("target", ...)`, and `Paths.get("target", ...)`. The guard allowlist contains only fixture inputs and no-session default assertions with an explanatory source comment; generated writers fail the guard. Explicit `--out`, `--output-root`, and `--report` arguments remain supported and are tested for ownership validation rather than rewritten. The inventory test also asserts that every supported output-producing entrypoint in the list resolves its default beneath `openggf.test.diagnostics`, `openggf.trace.reports`, `openggf.artifact.root`, or `openggf.distribution.root` when those properties are present.

- [ ] **Step 2: Migrate capture, audio, visual, performance, and rewind writers.**

  Replace each generated path with a named diagnostic namespace. Preserve the existing filenames inside that namespace so human triage remains readable. Route `TestRewindFieldDispositionGuard`'s generated disposition report through the session diagnostic root and keep the committed baseline comparison independent of the output location.

- [ ] **Step 3: Migrate native/package roots.**

  Change the macOS assembly invocation to receive the session artifact/distribution arguments. Ensure native extraction, executable output, config copy, and native libraries all use `${project.build.directory}` or the explicit package root. Make the manifest `artifacts` array list the produced JAR/native files.

- [ ] **Step 4: Run focused output and package checks.**

  ```bash
  tools/testing/test-session.sh -- \
    mvn -Dmse=off -DskipTests package
  tools/testing/test-session.sh -- \
    mvn -Dmse=off -Dtest=com.openggf.tests.TestBuildToolingGuard test
  ```

  Expected: the package manifest points outside legacy `target`, the diagnostic inventory guard passes, and no generated output is written to the shared legacy root during the coordinator run.

- [ ] **Step 5: Commit generated-output migration.**

  ```bash
  Review `git diff --name-only`, then stage only the changed paths from this task's explicit Files list; do not stage whole source directories or unrelated user changes.
  git commit -m "feat: route generated test outputs through sessions" \
    -m "Changelog: updated" -m "Guide: n/a" -m "Known-Discrepancies: n/a" \
    -m "S3K-Known-Discrepancies: n/a" -m "Agent-Docs: n/a" \
    -m "Configuration-Docs: n/a" -m "Skills: n/a"
  ```

### Task 5: Migrate CI/release consumers and workflow invocations

**Files:**

- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `src/test/java/com/openggf/tests/TestBuildToolingGuard.java`
- Modify: `tools/testing/TestSessionCoordinator.java` export-file handling and export-file contract tests
- Modify: `README.md` release/build command examples where fixed reports are named

**Interfaces:**

- POSIX invocation: `tools/testing/test-session.sh --export-file "$GITHUB_OUTPUT" -- mvn ...`.
- PowerShell invocation: `tools/testing/test-session.ps1 -ExportFile $env:GITHUB_OUTPUT -- mvn ...`.
- Workflow outputs: `manifest`, `run_id`; manifest paths: `surefire_reports`, `trace_reports`, `artifact_root`, `distribution_root`, `reports`, `artifacts`.

- [ ] **Step 1: Write failing workflow-contract assertions.**

  Add `TestBuildToolingGuard` assertions that every test/trace/release Maven invocation uses the correct `tools/testing` wrapper, passes an export file in CI, and that no coverage/report step globs `target/surefire-reports` or `target/trace-reports`.

- [ ] **Step 2: Convert CI guard/default/trace jobs.**

  Wrap `-Pguards`, default `test`, and trace replay commands. Change Python assertions to read the manifest path from the step output and use `surefire_reports`, `trace_reports`, and logical report entries. Keep existing minimum executed counts, skipped-test policy, trace warning policy, and required S2 report semantics.

- [ ] **Step 3: Convert release test/package/smoke jobs.**

  Wrap release tests and native/JVM package commands. Use `artifacts` and `distribution_root` from the manifest for upload and smoke validation. Ensure Windows PowerShell and POSIX matrix jobs use the same manifest field names.

- [ ] **Step 4: Run workflow source guards and local manifest simulation.**

  ```bash
  workflow_lock_root="$(agent-scratch path tasks)/test-session-locks"
  tools/testing/test-session.sh --lock-root "$workflow_lock_root" -- \
    mvn -Dmse=off -Pguards -Dsurefire.forkCount=1 \
    '-Dtest=com.openggf.tests.TestBuildToolingGuard' test
  ```

  Also run the coordinator with a temporary export file and verify it contains exactly `manifest=<absolute path>` and `run_id=<id>` and that the manifest JSON contains every required handoff key.

- [ ] **Step 5: Commit CI/release migration.**

  ```bash
  Review `git diff --name-only`, then stage only `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `README.md`, `src/test/java/com/openggf/tests/TestBuildToolingGuard.java`, and the changed coordinator/tool paths named in this task.
  git commit -m "ci: consume test session manifests" \
    -m "Changelog: updated" -m "Guide: updated" -m "Known-Discrepancies: n/a" \
    -m "S3K-Known-Discrepancies: n/a" -m "Agent-Docs: n/a" \
    -m "Configuration-Docs: n/a" -m "Skills: n/a"
  ```

### Task 6: Make hook installation explicit and synchronize workflow documentation

**Files:**

- Create: `tools/testing/install-hooks.sh`
- Create: `tools/testing/install-hooks.ps1`
- Modify: `AGENTS.md`
- Modify: `CLAUDE.md`
- Modify: `README.md`
- Modify: `docs/guide/contributing/dev-setup.md`
- Modify: `docs/guide/contributing/testing.md`
- Modify: `docs/guide/contributing/trace-replay.md`
- Modify: `docs/guide/contributing/trace-framework-reference.md`
- Modify: `docs/guide/playing/getting-started.md`
- Modify: `docs/guide/contributing/tutorial-implement-object.md`
- Modify: `docs/guide/PLAN.md`
- Historical evidence in `docs/status/**`, `docs/changelog/**`, and other archival sections is explicitly excluded; it retains original command/report paths and is labeled as historical evidence where needed.

**Interfaces:**

- POSIX hook bootstrap: `tools/testing/install-hooks.sh` runs `git config core.hooksPath .githooks` from the worktree and reports read-only failure without being part of Maven.
- PowerShell hook bootstrap: `tools/testing/install-hooks.ps1` performs the same operation and returns a nonzero code on failure.
- Supported build/test examples call the coordinator wrappers; raw Maven is explicitly non-certifying.

- [ ] **Step 1: Write the documentation consistency guard.**

  Add a source guard that compares the mirrored workflow paragraphs in `AGENTS.md` and `CLAUDE.md`, rejects raw supported commands in those sections, requires the explicit hook-bootstrap command, and allows historical command examples only where the document labels them archival evidence.

- [ ] **Step 2: Implement explicit hook bootstrap.**

  Use the existing `.githooks` path, make the scripts resolve the current worktree, and never call them from Maven. Test success in a writable disposable Git repository and clear failure when `.git/config` is read-only.

- [ ] **Step 3: Rewrite contributor/release guidance.**

  Convert the documented commands to the wrapper syntax while preserving all Maven profiles, ROM properties, test selectors, and quoting examples. Add a short explanation that `MAVEN_OPTS`/`TMPDIR` are owned by the coordinator and that the manifest is the source of report paths.

- [ ] **Step 4: Verify mirrored docs and command inventory.**

  ```bash
  cmp -s AGENTS.md CLAUDE.md
  rg -n 'mvn (test|package|clean|verify)' AGENTS.md CLAUDE.md docs/guide README.md
  docs_lock_root="$(agent-scratch path tasks)/test-session-locks"
  tools/testing/test-session.sh --lock-root "$docs_lock_root" -- \
    mvn -Dmse=off -Pguards -Dsurefire.forkCount=1 \
    '-Dtest=com.openggf.tests.TestBuildToolingGuard' test
  ```

  Expected: mirrored files remain synchronized, supported examples point to the coordinator, and the guard passes.

- [ ] **Step 5: Commit hook/docs policy.**

  ```bash
  Review `git diff --name-only`, then stage only the changed paths from this task's explicit Files list; do not stage whole documentation or tooling directories.
  git commit -m "docs: make test sessions and hook setup explicit" \
    -m "Changelog: n/a: workflow documentation" \
    -m "Guide: updated" -m "Known-Discrepancies: n/a" \
    -m "S3K-Known-Discrepancies: n/a" -m "Agent-Docs: updated" \
    -m "Configuration-Docs: n/a" -m "Skills: n/a"
  ```

### Task 7: Add the external process isolation harness

**Files:**

- Create: `tools/testing/TestSessionProcessHarness.java`
- Modify: `tools/testing/TestSessionCoordinatorSelfTest.java`
- Create: `tools/testing/run-session-process-harness.sh`
- Create: `tools/testing/run-session-process-harness.ps1`
- Create: `tools/testing/fixtures/session-guard/pom.xml`
- Modify: `tools/testing/README.md`

**Interfaces:**

- The harness creates a disposable repository outside the OpenGGF checkout, initializes a minimal Git worktree, copies only the coordinator/wrappers, and uses a controllable fake Maven command that writes markers, sleeps, creates a report, or exits with a selected code. For lifecycle-guard cases it also copies `tools/testing/fixtures/session-guard/pom.xml` and runs a real Maven invocation against that minimal POM; fake-Maven cases are reserved for process/lease/output behavior.
- POSIX fake Maven is an executable `.sh`; Windows fake Maven is a `.cmd`; the Java harness chooses the native script and never assumes `/bin/sh` on Windows.
- Each case returns nonzero on failure and prints the retained session manifest path.

- [ ] **Step 1: Write failing process cases.**

  Implement cases for: same-worktree concurrent sessions, separate linked worktrees, read-only system temp with writable session root, rejection of an in-worktree external lock root, report isolation, branch mutation, `HEAD` mutation, tracked-file mutation, existing untracked-file mutation, staged-state mutation, declared ignored-runtime-input mutation, interruption/orphan detection, raw `clean`/`compile`/`test`/`test-compile`/`verify`/`package` rejection through the fixture POM before its marker file changes, stale lease reclaim, and active-session protection from legacy `target` cleanup. Each source/runtime mutation case must end with a retained manifest marked `INVALID_IDENTITY_CHANGED`, with the relevant before/after digest and status field showing which identity changed.

- [ ] **Step 2: Implement the disposable fake-Maven runner.**

  Make the fake command honor a control file for sleep/release, write `BUILD SUCCESS`, and create a deliberately colliding report name. The harness starts the coordinator, waits for `OPENGGF_TEST_RUN_START`, then performs the competing operation and finally checks the manifest state, exit code, and output ownership.

- [ ] **Step 3: Add Linux and Windows wrapper runners.**

  The shell runner compiles/runs `TestSessionProcessHarness`; the PowerShell runner performs the same command with Windows paths. Both use an external lock root when the disposable Git metadata is deliberately made read-only.

- [ ] **Step 4: Run the full harness.**

  ```bash
  tools/testing/run-session-process-harness.sh
  ```

  Expected: all cases pass; the concurrent loser never starts fake Maven; mutated-input runs are `INVALID_IDENTITY_CHANGED`; interrupted runs are retained as `ABORTED`; no report is accepted from another run.

- [ ] **Step 5: Commit the process harness.**

  ```bash
  Review `git diff --name-only`, then stage only the changed paths from this task's explicit Files list; do not stage unrelated tooling changes.
  git commit -m "test: exercise isolated session processes" \
    -m "Changelog: n/a: test infrastructure" \
    -m "Guide: n/a" -m "Known-Discrepancies: n/a" \
    -m "S3K-Known-Discrepancies: n/a" -m "Agent-Docs: n/a" \
    -m "Configuration-Docs: n/a" -m "Skills: n/a"
  ```

### Task 8: Complete validation and release handoff

**Files:**

- Modify: `docs/architecture/designs/2026-08-23-test-session-isolation-design.md` only if implementation evidence changes a contract.
- Modify: `docs/architecture/validation/` with the session isolation validation report.
- Modify: `docs/status/known-discrepancies.md` only if a genuine remaining limitation is discovered; do not hide an implementation failure as a discrepancy.

**Interfaces:**

- Every reported suite result names its coordinator manifest, source digest, runtime-input digest, branch, commit, state, exit code, and exact report roots.
- Release readiness remains separate from human end-to-end QA; this work removes test-run attribution/sandbox ambiguity but does not satisfy the required human engine QA gate.

- [ ] **Step 1: Run standalone and focused verification.**

  ```bash
  tools/testing/run-session-process-harness.sh
  tools/testing/test-session.sh -- mvn -Dmse=off -Pguards -Dsurefire.forkCount=1 test
  tools/testing/test-session.sh -- mvn -Dmse=off '-Dtest=com.openggf.tests.TestBuildToolingGuard' test
  ```

- [ ] **Step 2: Run the default suite through the coordinator.**

  Discover the three `.gen` files with `rg --files -g '*.gen'`, select the files whose names identify the Sonic 1 World REV01, Sonic 2 World REV01, and Sonic 3&K locked-on images, and verify their SHA-1 values against `AGENTS.md`. With variables `s1_rom`, `s2_rom`, and `s3k_rom` set to those canonical absolute paths, run exactly:

  ```bash
  export_file="$(mktemp)"
  tools/testing/test-session.sh --export-file "$export_file" -- \
    mvn -Dmse=off "-Dsonic1.rom.path=$s1_rom" "-Dsonic2.rom.path=$s2_rom" \
    "-Ds3k.rom.path=$s3k_rom" test
  ```

  Read only the exported `manifest` path, then record its terminal state, source/runtime-input digests, and report roots; do not report counts from stale `target` files.

- [ ] **Step 3: Run the trace profile through the coordinator.**

  With the verified `s1_rom`, `s2_rom`, and `s3k_rom` variables from Step 2, run the focused keep-green profile exactly:

  ```bash
  focused_export="$(mktemp)"
  tools/testing/test-session.sh --export-file "$focused_export" -- \
    mvn -Dmse=off -B '-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
    "-Ds3k.rom.path=$s3k_rom" test
  ```

  Run the release trace sweep exactly:

  ```bash
  trace_export="$(mktemp)"
  tools/testing/test-session.sh --export-file "$trace_export" -- \
    mvn -Dmse=off -B -Ptrace-replay \
    "-Dsonic1.rom.path=$s1_rom" "-Dsonic2.rom.path=$s2_rom" "-Ds3k.rom.path=$s3k_rom" test
  ```

  Compare the manifest report inventories and first-error fields with the pre-change baseline; known-red trace frontiers remain known-red unless a real regression appears. Never read a report by a fixed `target` basename.

- [ ] **Step 4: Run package smoke tests.**

  Run the Linux package smoke exactly:

  ```bash
  package_export="$(mktemp)"
  tools/testing/test-session.sh --export-file "$package_export" -- \
    mvn -Dmse=off -B -Pnative -DskipTests package
  ```

  On a platform that provides the native profile, run its corresponding CI matrix command through the platform wrapper; also run `-Puniversal-jar -DskipTests package` when that release job is available. Parse the exported manifest JSON and assert every path in `artifacts` is a regular file below `artifact_root` or `distribution_root`, the packaged JAR/native smoke checks pass, and no package output exists under the legacy shared root.

- [ ] **Step 5: Write validation evidence and review the final diff.**

  Record commands, manifest paths, terminal states, test totals, exact failures, platform results, and any baseline comparison in `docs/architecture/validation/`. Run `git diff --check`, the command-inventory guard, all focused coordinator tests, and `mvn -v` verification.

- [ ] **Step 6: Commit validation evidence.**

  ```bash
  git add docs/architecture/validation/2026-08-23-test-session-isolation.md
  git commit -m "test: validate isolated session release workflow" \
    -m "Changelog: n/a: validation evidence" \
    -m "Guide: n/a" -m "Known-Discrepancies: n/a" \
    -m "S3K-Known-Discrepancies: n/a" -m "Agent-Docs: n/a" \
    -m "Configuration-Docs: n/a" -m "Skills: n/a"
  ```

## Final integration checks

Before claiming completion, follow the repository workflow exactly:

1. From the main workspace, run `git fetch origin` and `git pull --ff-only origin develop` on the already checked-out `develop` branch without discarding user changes. Record `git rev-parse HEAD` and `git status --short` before and after.
2. Discover and SHA-1-verify `s1_rom`, `s2_rom`, and `s3k_rom` as in Task 8 Step 2. Because the pre-change `develop` commit does not yet contain `tools/testing/test-session.sh`, capture its baseline with this one-off direct Maven command, explicitly label it pre-change/non-certifying, and record the exact output and failures from the command's legacy reports:

   ```bash
   set -o pipefail
   baseline_log="$(mktemp)"
   mvn -Dmse=off -B "-Dsonic1.rom.path=$s1_rom" "-Dsonic2.rom.path=$s2_rom" \
     "-Ds3k.rom.path=$s3k_rom" test 2>&1 | tee "$baseline_log"
   ```

   After the implementation branch has the coordinator, run the complete development-worktree suite with one coordinator manifest:

   ```bash
   development_export="$(mktemp)"
   tools/testing/test-session.sh --export-file "$development_export" -- \
     mvn -Dmse=off -B "-Dsonic1.rom.path=$s1_rom" "-Dsonic2.rom.path=$s2_rom" \
     "-Ds3k.rom.path=$s3k_rom" test
   ```

   Save the exported absolute `development_manifest` path and record exact failures from its own Surefire/report roots, never from shared `target` files.
3. In this development worktree, run the focused coordinator tests, the external process harness, the full relevant default/trace/package commands from Task 8, and record their manifest paths and exact outcomes.
4. After the development worktree is clean and its branch contains the reviewed commits, merge `feature/ai-test-session-isolation-design` directly into the main-workspace `develop` branch without switching the main workspace.
5. On merged `develop`, run the same coordinator command with a fresh `merged_export` and all three verified ROM properties. Compare `development_manifest` and `merged_manifest` by loading only their declared report roots: every test case that passed on the development branch must still pass, no development-branch failure may become a new failure/error attributable to this change, and all report/artifact paths must remain inside their respective session roots. Record the comparison result and exact changed failure set in the validation report; the direct pre-change log is retained separately as historical baseline evidence.
6. Push only the integrated main-workspace `develop` branch with `git push origin develop` after the comparison is green; do not push the local worktree branch.
7. Verify the development worktree has no unknown or unmerged changes, remove it only after merge, post-merge verification, and push succeed, then delete `feature/ai-test-session-isolation-design` only after `git branch --merged develop` confirms it and prune stale worktree metadata.

No human end-to-end engine QA result may be inferred from these automated checks; the 0.6 release remains blocked on that human gate until separately completed.
