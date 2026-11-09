# Testing

This page covers the engine's test infrastructure and how to write tests for new features.

All new or updated tests must use JUnit 5 / Jupiter. Do not add JUnit 4 tests, rules, runners, or `org.junit.*` imports.

Pick the check from what you changed:

| Change | Run |
|---|---|
| One class or a bounded fix | The focused test: `python3 tools/testing/maven_queue.py -Dmse=off -Dtest=<TestClass> test` |
| Shared code, or impact you are unsure of | The change-based smoke selection: `python3 tools/testing/run_categories.py --base <pre-task-commit> --workers 2 --run` |
| Structural rules (guards) | `python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B` |
| A public release | `-Pfull-suite`, through the [release workflow](#release-builds-in-github) only |

`<pre-task-commit>` is the commit your branch started from, for example the output
of `git rev-parse HEAD` before your first edit. Keep that SHA for validation,
including after integrating upstream changes. Read the category runner's final `VERDICT`
line: unexplained or required skips mean the run is incomplete.

## Running Tests

```bash
# Before editing, record the starting commit once. Keep BASE for later validation.
BASE=$(git rev-parse HEAD)

# After editing, inspect the selection, then run it.
python3 tools/testing/run_categories.py --base "$BASE"
python3 tools/testing/run_categories.py --base "$BASE" --workers 2 --run
# Routine broad coverage: all smoke candidates, yielding admission between batches.
python3 tools/testing/run_categories.py --category all --workers 2 --run
# Focused regression (reviewed small classes select lean automatically).
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestCollisionLogic test
# Single method.
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestCollisionLogic#testCollisionLogic' test
# Public release preparation ONLY: the former complete ordinary suite.
python3 tools/testing/maven_queue.py -Dmse=off -Pfull-suite test -B
```

Plain Maven `test` and `package` default to the existing `smoke` exclusions;
`-Psmoke` is an explicit alias. Full-suite validation is reserved for new public
releases, never routine development, integration, baseline or post-integration
checks. Add focused regressions, affected ROM/trace checks and separate guards
as needed. Category `all` is all smoke categories, not the exhaustive full suite.
ROM-heavy smoke coverage can still take an hour overall. The category runner
queues at most 50 candidate classes per ordinary batch, releases shared admission
between batches and before guards, and retains the worktree lock throughout.
Queue waits do not consume its execution timeout. Raw unfiltered Maven runs remain
monolithic; use the runner for broad local coverage and package with `-DskipTests`.

Every category `--run` checks tool prerequisites automatically. Its final
`VERDICT` states the tested source, executed coverage and allowed/unexplained skip
counts. Unexplained or required skips mean incomplete validation (exit 1). Passing
runs clean up automatically; failed or incomplete runs retain bounded diagnostics.
Inspect their `results.json`, then remove them with
`python3 tools/testing/run_categories.py --acknowledge <run-id>`.

See [testing utilities](../../../tools/testing/README.md#default-suite-and-release-only-validation).

Tests use one reused 3 GiB JVM by default. `test-concurrent` opts into two forks;
Jupiter execution remains serial inside each fork. The category runner groups
reviewed low-heap classes separately and caps Maven and each such fork at 1 GiB.
Selection stays complete; qualified two-fork batches use shared 5 GiB / 8-core
admission. Heavy two-fork batches retain normal exclusive admission.
Guards use a separate normal one-worker JVM.

### Short survival witnesses and manual soaks

Sonic Survivors' 20,000-frame endless-run soak belongs to `exhaustive-audit`.
Routine smoke retains the independent deadline/pause/rewind, reward lifetime and
emerald controls, plus a short live boundary witness after ordinary run entry.
That witness creates a real formation, seeds the crowded near-deadline/expiry
state, then checks ordinary-wave admission beyond 100 enemies, fresh formation
admission and actual removal of expired rewards. Its three post-entry production
frames do not certify five minutes of accumulated history. The long soak remains
available for an explicitly requested investigation.

### TraceChaser immutable consumer copies

Ordinary `TestTraceChaserV5ConsumerContract` runs the frozen v5 pack through Java
consumers and checks its declared provenance without an initialized submodule.
Its temporary local Git fixtures also distinguish immutable-origin preservation
from a later producer HEAD, and reject byte tampering and missing/extra files.

The opt-in method first requires the initialized checkout to match OpenGGF's
exact gitlink. It then compares all three declared copies, including complete v5
file membership and exact bytes, with Git objects at `provenance.json`'s
`origin_commit`. That origin must already be available locally; the test fails
clearly if it is absent and never fetches or substitutes HEAD. A passing
historical-copy check does not establish current producer compatibility.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=com.openggf.trace.TestTraceChaserV5ConsumerContract' test -B
python3 tools/testing/maven_queue.py -Dmse=off -Ptracechaser-integration \
  '-Dtest=com.openggf.trace.TestTraceChaserV5ConsumerContract#initializedCheckoutPreservesConsumerCopiesFromDeclaredImmutableOrigin' test -B
```

### TAS trick verdicts

Published Sonic TAS tricks are indexed in the
[representative verdict record](../../architecture/validation/2026-10-26-tas-trick-verdicts.md).
Each completed assessment records an independent native expectation, an executed
production scenario, a relevant control and a supported or unsupported verdict.
The record explicitly identifies any remaining named mechanism under investigation.
A passing current-limitation regression confirms an engine mismatch; it is not
proof that the trick works. Broad families retain their original IDs and can contain
different named tricks with different outcomes. Publication-route and whole-movie
synchronization remain separate from representative mechanism support. The
[first regression pass](../../architecture/validation/2026-10-26-tas-trick-regressions.md)
preserves the earlier component-evidence assessment.

## CI in GitHub

| Event | What runs |
|---|---|
| Push to `develop` or `next` | Basic CI: the `smoke` profile, once the branch has had no push for 30 minutes. Branch policy runs at once in its own workflow and checks every commit since its last successful run. |
| Non-draft pull request, any base | Routine CI: branch policy, the bounded smoke suite and `-Pguards`. Draft PRs run nothing until marked ready; a newer push cancels the older run. |
| Push to `master` | The Release workflow below: full validation, native packaging, publication. |
| Push to a feature or bugfix branch | Nothing. Open a pull request, or run CI by hand. |
| Manual dispatch | Smoke and guards on the chosen ref. `trace_replay` adds the ROM-fixture develop trace replay on the self-hosted runner. |

<details>
<summary>How the 30-minute quiet period works</summary>

The 30-minute quiet period is the `ci-quiet-period` environment's wait timer
(repository Settings -> Environments). The smoke job waits there without using a
runner, and a newer push to the same branch cancels the wait and starts another,
so a burst of pushes produces one smoke run for its last commit. Edit the timer
there to change the delay. Deleting the environment removes the delay: GitHub
recreates it on the next run without a timer.

</details>

Guards are not part of push CI, so run `-Pguards` locally before pushing
straight to `develop` or `next`. Pull requests do not build native packages;
packaging failures first show on the master push that would publish.

## Release builds in GitHub

The Release workflow builds Windows, macOS, Linux, and the universal JAR on
GitHub-hosted runners. Its blocking `test` job explicitly runs `-Pfull-suite` and
structural guards on Ubuntu, installing Lua 5.4 first. Private ROMs are absent
on these runners, so ROM-dependent skips remain visible in the uploaded test
evidence; a successful cloud build does not establish ROM replay parity.

A push to `master` validates, builds and publishes a release with a new
version tag; see [publishing a GitHub release](../../project/release-publishing.md).
Manual Release dispatch is for release preparation; it runs full-suite and builds
without publishing. Routine manual CI uses smoke.

ROM-backed release evidence is collected locally with the existing release
comparison and skip-classification tools. The optional `validate_roms` manual
input also enables the unchanged strict checks on a separately configured
`release-fixtures` runner. It defaults to false and is not a dependency of the
cloud builds. The no-regression policy in
[release 6 trace scope](../../status/trace-scope-release-6.md) still applies to
ROM-backed sign-off.

<details>
<summary>History boundary and content audit for release checks</summary>

Release checks use the fixed reviewed `develop` snapshot
`45cecf566825aa50612f5e687b2682fc9681aed1` as the 0.6 history boundary, as
approved by the maintainer on September 11, 2026. They audit every entry of
the final Git tree once for forbidden assets, oversized or uncompressed
payloads, scratch artifacts, and unsafe symlinks. Python 3 runs this audit.
All newer incoming commits retain their normal content checks, including
new local paths and violations introduced and subsequently removed. New
release commits also retain their documentation-trailer checks. The original
resource cutover remains the fallback for histories before this snapshot.

The baseline is an immutable commit, not a moving branch name. Existing
trailer debt is preserved without rewriting published history. Future
baseline changes require explicit review; an audit failure is never ignored.

</details>

## Rewind Tests And Benchmark

The rewind system has both ordinary regression tests and an opt-in benchmark. See
[Rewind System](rewind-system.md) for usage, limitations, and architecture details.

### Object population and contact boundaries

For contact retirement, start with the real landing/pushing, equal-key survivor,
occurrence and removal-callback boundaries:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestDynamicSolidContactRetirement,TestSolidContactMemory,TestObjectSolidContactController,TestTasS3kOrtExhaustion,TestObjectRemovalCallbackOwnership' test
```

`TestDynamicSolidContactRetirement` covers explicit/destroyed dynamic retirement
and a placed range-unload/reentry. Its placed case calls both object execution
and post-camera placement: counter-mode `update()` alone does not advance the
loader. It checks retired live bits, unchanged player state, fresh identity/slot
reuse and subsequent ordinary contact. This sole-owner case does not establish a
new policy for frame history or aliases across placed and dynamic owners.

For retained-SST transfer, snapshot round trips or late player effects, use:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSolidContactMemory,TestObjectSolidContactController,TestSolidObjectManager,TestObjectManagerRewindSnapshot,TestShieldRewindPendingRestore,TestObjectManagerDynamicChainRewindRestore' test
```

The first three classes cover contact memory, real solid calls and retained-SST
transfer; the manager snapshot class covers population round trips. The last pair
exercises late player-bound admission and children that precede their parents in
capture order. For a transfer walkthrough, start at
`TestObjectSolidContactController#transitionContactsRetainLiveCarriedPieceWithoutRecreatingClearedOrNoncarriedBits`:
real landings establish the bits, an object release and continued ride leave one
piece live, then the production transition carries only the surviving owner.
`TestSolidObjectManager` supplies the non-surviving-object control. For late
effects, follow reverse player-refresh/identity and retry in
`TestShieldRewindPendingRestore`. Snapshot piece-key restoration is a separate
boundary from live-manager transfer.

For ordinary graph reconstruction, constructor-child families and forward
references, use:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestConstructorChildFamilyRewind,TestObjectManagerDynamicChainRewindRestore,TestTwoPhaseRestoreOrdering,TestObjectManagerRewindSnapshot,TestModOwnedDynamicObjectRewind' test
```

`TestConstructorChildFamilyRewind` distinguishes interleaved same-class families,
moving children and deferred/absent parents. `TestObjectRewindExactAdoption`
instead exercises event-owned exact SST-slot adoption and rollback; add it when
changing that separate surface. It is not the ordinary constructor-child witness.

The late-effect witness calls actual player refresh hooks in reverse order and
compares captured identities and the complete object snapshot. It covers the
deferred scalar restore path; `TestTwoPhaseRestoreOrdering` covers references
between actors reconstructed in the ordinary graph pass.

Use the [ownership map](architecture.md#finding-an-object-lifecycle-owner) to find
the production entry. Check which cases executed and why any skipped. This
bounded command does not replace change-based smoke, guards or affected ROM/trace
checks required by the change.

### General rewind coverage

Run the regular rewind suite:

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=*Rewind*" test
```

Run the compact schema foundation tests when changing automatic rewind capture,
field policy classification, or value codecs:

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestRewindStateBuffer,TestRewindSchemaRegistry,TestCompactFieldCapturer,TestCompactFieldCapturerPolicy,TestRewindRecordCodecs,TestRewindHelperCodecs,TestRewindCollectionCodecs,TestRewindPolicyRegistry,TestRewindPlayerReferenceCodecs,TestRewindObjectReferenceCodecs,TestRewindIdentityTable" test
```

Generate the runtime-owner field inventory when planning object/player rewind
coverage work:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests test-compile exec:java \
  "-Dexec.mainClass=com.openggf.tools.rewind.RewindFieldInventoryTool"
```

Add `"-Dexec.args=--object-rollout-candidates"` to list default object subclass
capture candidates instead of unsupported fields. Use that list before adding
per-object rewind annotations or overrides; most scalar object state should move
through the central default-capture path.

Use `"-Dexec.args=--annotation-density"` to report `@RewindTransient` /
`@RewindDeferred` density by class, declared type, and package, including
redundant transient annotations that central policy already infers.

When object, boss, badnik, or trace work touches source guards, treat baselines as
shrink-only migration artifacts. New code should prefer `ObjectControlState`,
`ObjectPlayerQuery` / `ObjectPlayerParticipationPolicy`, `ObjectLifetimeOps`, and
canonical profiles under `com.openggf.game.profiles.*`; only add a baseline entry for a
documented legacy bridge or a temporary `level.objects` compatibility wrapper, and remove
entries when a migration closes them.

Run the child/spawn graph audit when planning object family coverage:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests test-compile exec:java \
  "-Dexec.mainClass=com.openggf.tools.rewind.ChildGraphPolicyInventoryTool"
```

Run the focused encounter validation foundation:

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestRewindEncounterValidation" test
```

Run the focused presentation checks after changing reverse audio, trace rewind,
or fade behaviour:

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=com.openggf.TestTraceSessionLauncherRewindPresentation,com.openggf.graphics.TestFadeManagerRewindSnapshot,com.openggf.game.rewind.TestLiveRewindManagerAudioCleanup,com.openggf.audio.TestAudioManagerRewindSuppression" test
```

Run the benchmark only when you need timing, footprint, or long-tail determinism data:

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=RewindBenchmark" \
  "-Dopenggf.rewind.benchmark.run=true" test
```

The benchmark accepts `-Dopenggf.rewind.benchmark.keyframeInterval=<frames>` to compare
memory and seek behaviour at intervals such as `60`, `30`, and `15`. Results are printed
to stdout and written to `target/rewind-benchmark-results.json`.

## ROM-Dependent Tests

Many tests require ROM files to load level data, object art, or audio. These tests
**skip gracefully** when ROMs are absent, so CI and contributors without ROMs can still
run the rest of the suite. The local category runner treats these missing-ROM
skips as incomplete coverage, so a ROM-less run cannot claim a passing verdict
for the selected ROM-backed checks.

### `@RequiresRom` Annotation

Annotate the test class with `@RequiresRom` and declare which game's ROM is needed.
The attached JUnit 5 extension handles ROM loading, game module detection, and environment
reset automatically. When the ROM is absent, the entire class is skipped.

```java
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

@RequiresRom(SonicGame.SONIC_2)
class TestMyFeature {

    @Test
    void testSomething() {
        // ROM is loaded, game module configured, environment reset —
        // just write your test logic
    }
}
```

Available game values: `SonicGame.SONIC_1`, `SonicGame.SONIC_2`, `SonicGame.SONIC_3K`.

The annotation system provides several benefits over manual checks:
- **Automatic environment reset** — `TestEnvironment.configureRomFixture(rom)` resets and rebuilds the gameplay context before each test.
- **ROM caching** — ROMs are loaded once per JVM and shared across all tests via `RomCache`.
- **Clean skip reporting** — JUnit reports skipped tests with a clear reason rather than
  silently passing.
- **Game module setup** — The ROM-backed game module is configured automatically.

### `@RequiresGameModule` (No Real ROM)

For tests that need a game module configured but don't need real ROM data (e.g., testing
logic that only depends on which game is active):

```java
import com.openggf.tests.rules.RequiresGameModule;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

@RequiresGameModule(SonicGame.SONIC_2)
class TestGameSpecificLogic {

    @Test
    void testSomething() {
        // Game module is set, but no ROM loaded
    }
}
```

Note: `@RequiresRom` and `@RequiresGameModule` are mutually exclusive on the same class.

### `@FullReset`

Use `@FullReset` when a test needs the full singleton/runtime reset path between methods.
This is the preferred replacement for older manual reset boilerplate.

```java
import com.openggf.tests.FullReset;
import org.junit.jupiter.api.Test;

@FullReset
class TestSomethingStateful {

    @Test
    void testSomething() {
        // The test environment is fully reset before this runs
    }
}
```

### Legacy: `RomTestUtils` (Manual Check)

Older tests use `RomTestUtils` to check for ROM availability inline. This still works but
is not recommended for new tests:

```java
import static com.openggf.tests.RomTestUtils.ensureRomAvailable;

@Test
void testEHZCollision() {
    File romFile = ensureRomAvailable();
    org.junit.jupiter.api.Assumptions.assumeTrue(romFile != null, "ROM not available");
    // ... test logic
}
```

### ROM Path Configuration

Queued test commands identify verified original `.gen` files by SHA-1 in the worktree
root, then (from a linked worktree) the main checkout, and pass their absolute paths. The
wrapper prints the found, caller-supplied and missing properties:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestSonic3kLevelLoading test
```

To pin a different dump, name the property yourself; a command that names any ROM property
keeps exactly its own ROM set, and an empty value such as `-Ds3k.rom.path=` proves a skip.
Environment variables also take precedence over discovery:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestS1Ghz1TraceReplay \
  -Dsonic1.rom.path=/absolute/other-s1.gen test
export SONIC_1_ROM_PATH="/absolute/s1.gen"
export SONIC_2_ROM_PATH="/absolute/s2.gen"
export SONIC_3K_ROM_PATH="/absolute/s3k.gen"
```

## HeadlessTestFixture

The `HeadlessTestFixture` provides a builder-pattern API for setting up a test level
without a GPU window. It initializes the game module, loads level data, and provides
frame-stepping controls.

### Basic Usage

```java
@Test
void testPlayerLandsOnGround() {
    HeadlessTestFixture fixture = HeadlessTestFixture.builder()
            .withZoneAndAct(0, 0)  // EHZ Act 1
            .startPosition((short) 0x100, (short) 0x200)
            .build();

    // Step 60 frames (1 second at 60fps)
    fixture.stepIdleFrames(60);

    // Assert the player landed on the ground
    AbstractPlayableSprite player = fixture.sprite();
    assertFalse(player.getAir());
    assertTrue(player.getY() > 0x200);  // Fell from starting position
}
```

### Setting Up Specific Scenarios

```java
// Set up fixture with specific start position
HeadlessTestFixture fixture = HeadlessTestFixture.builder()
        .withZoneAndAct(4, act)  // HTZ engine index; act is zero-based
        .startPosition((short) 0x500, (short) 0x300)
        .build();

// Snap camera to player
fixture.camera().setX((short) (0x500 - 160));
fixture.camera().setY((short) (0x300 - 112));
fixture.camera().updatePosition(true);  // Snap (no smooth scroll)

// Step frames with per-frame inspection
for (int i = 0; i < 120; i++) {
    fixture.stepFrame(false, false, false, false, false);  // no input
    if (fixture.sprite().getX() >= targetX) {
        break;
    }
}
```

### What HeadlessTestFixture Does

The fixture delegates to `HeadlessTestRunner` and the shared `RecordingFrameDriver`.
`LevelFrameStep` owns frame-level ordering and `SpriteManager.update(...)` owns
playable/team ordering, so tests and trace captures use the production update path.
Do not reproduce a partial camera/event/object loop in the test itself.

The snippets assume the matching `@RequiresRom` annotation and zero-based engine
zone/act indices. `startPosition(...)` uses top-left coordinates by default; add
`startPositionIsCentre()` when supplying ROM `x_pos` / `y_pos` coordinates and
compare them with `getCentreX()` / `getCentreY()`.

## Bounded work inside a test

Independent CPU-bound source audits can use
`com.openggf.tests.SourceScanExecutor.invokeOrdered` with one or two workers.
Each task owns its compiler/file manager, scanner state and result list. The
executor preserves input order, propagates worker exceptions and assertions,
and cancels unfinished siblings before waiting for teardown. Parser workers
count against the queued job's existing CPU and memory budget; adding threads
does not qualify a test for a smaller heap.

Keep engine/session state, configuration mutation and graphics contexts on
their owning thread. Worker preparation must consume captured independent
inputs rather than resolve global engine services. Jupiter test execution
remains serial. See the
[source-audit experiment](../../architecture/research/2026-10-27-local-validation-cost.md#bounded-parser-concurrency-experiment)
for the measured benefit, memory cost and focused validation limits.

## Test Organisation

Tests are grouped by the systems they exercise:

```
src/test/java/com/openggf/
  physics/         -- Physics and collision tests
  level/           -- Level loading, object spawning tests
  audio/           -- Audio regression tests
  game/
    sonic1/        -- S1-specific tests
    sonic2/        -- S2-specific tests
    sonic3k/       -- S3K-specific tests
```

### Naming Conventions

- `Test<Feature>.java` -- Unit tests for a specific feature.
- `Test<Zone><Feature>.java` -- Tests for zone-specific behavior (e.g., `TestHTZEarthquake`).
- `Test<Object>Instance.java` -- Tests for a specific object type.

## Physics Integration Tests

Physics tests verify that the engine produces the same player positions and velocities as
the original game for specific scenarios.

### Pattern: Step-Frame Position Assertion

```java
@Test
void testRollingDownSlope() {
    HeadlessTestFixture fixture = HeadlessTestFixture.builder()
            .withZoneAndAct(0, 0)  // EHZ Act 1
            .startPosition((short) 0x800, (short) 0x340)
            .build();

    // Set initial speed
    fixture.sprite().setGSpeed((short) 0x200);

    fixture.stepIdleFrames(30);

    // Player should have accelerated down the slope
    int speed = fixture.sprite().getGSpeed();
    assertTrue(speed > 0x200, "Player should accelerate on downhill slope");
}
```

### Pattern: Collision Edge Case

```java
@Test
void testWallCollisionAtSpeed() {
    HeadlessTestFixture fixture = HeadlessTestFixture.builder()
            .withZoneAndAct(0, 0)  // EHZ Act 1
            .startPosition((short) (wallX - 100), (short) wallY)
            .build();

    fixture.sprite().setGSpeed((short) 0xC00);  // 12 pixels/frame

    fixture.stepIdleFrames(30);

    // Player should stop at the wall, not pass through
    assertTrue(fixture.sprite().getX() <= wallX);
    assertEquals(0, fixture.sprite().getGSpeed());
}
```

## Trace Replay Tests

Trace replay tests run a BK2 movie through the engine and compare each frame against a trace
recorded from the original ROM in BizHawk. They are used for parity work where ordinary unit tests
are too local to expose the real divergence.

Current examples:

- `TestS1Ghz1TraceReplay`
- `TestS1Mz1TraceReplay`

Run these release-6 examples with the replay profile:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.forkCount=1 \
  -Dtest=TestS1Ghz1TraceReplay,TestS1Mz1TraceReplay test
```

### Diagnose one trace replay

Start at the concrete test class: its `game()`, `zone()`, `act()` and
`traceDirectory()` identify the engine entry and fixture. Read that fixture's
`metadata.json` for the recorded team, row count and `source_bk2`. The replay
loader first checks the fixture parent's `_movies/<source_bk2>`, then the run-root
movie beside the segment directories, then a retained `.bk2` in the fixture
directory. Sonic/Tails run segments use the run-root placement; not every movie
lives in the game's `_movies/` pool. A missing movie can skip the test.

Find the current lane in the `trace-replay`, `trace-replay-r7` and
`trace-segments` profiles in [root pom.xml](../../../pom.xml).
The [release-scope record](../../status/trace-scope-release-6.md) describes historical
scope; its status table and a test's comments are not current results. The first
HCZ Sonic/Tails leaf (`hcz`, recorded act 1 / engine act index 0) is standalone
segment instrumentation. Its lane is `trace-segments`, while run chains supply
release-gate evidence:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-segments \
  -Dsurefire.forkCount=1 -Dtrace.print.summary=true \
  '-Dtest=TestS3kSonicTailsHczSegmentTraceReplay' test
```

This leaf ends at the giant-ring transition into `ss_5`. For the act-spanning
neighbour, select `TestS3kSonicTailsHcz2SegmentTraceReplay` with the same command.
Its `Hcz2` identifies directory `hcz_2`, the second HCZ segment, not an act-2-only
test. It enters engine act index 0 and crosses the act transition.
Use metadata and recorded `zone_act_state` events to locate the mismatch's act;
the copied run-wide notes and the report's initial logical key are insufficient.

`-Dtrace.verification=all|physics|animation` selects the verification scope
(default `all`); animation-only requires CSV v7 character-animation fields.
Standalone reports use a separate scope suffix for physics and animation.
Triage uses the scope saved in the report's gate for its first divergence,
bootstrap diagnosis, context/auxiliary focus and frontier-log hint. Bootstrap
discrepancies belong to physics. The published totals and summary still cover
all verification groups, so a narrow brief can select a later mismatch or show
no selected divergence while the global error count remains positive.
Changing a JVM property while reading a saved report does not change that scope.

Narrow diagnosis requires every published error/warning group to carry a
canonical `verification_group` (`physics` or `animation`). Unclassifiable groups
fail evidence reading instead of appearing clean. ALL and absent-gate reports
retain their global diagnosis and accept older unclassified groups; absence of
a replay gate still means UNKNOWN completion.

For a shorter diagnostic run, `-Dtrace.frontierOnly=true` stops after the first
selected error and its context window; an early stop publishes an INCOMPLETE
gate. `-Dtrace.print.context=true` prints the first error's context to stderr.
`-Dtrace.context.radius=<frames>` sets the context radius (default 2), used by
both report windows and frontier stopping. These switches do not relax assertions
or make a shortened prefix complete.

Use Java 25. Check the wrapper's verified-ROM discovery line and ensure the
fixture payload and referenced movie are available. The standalone
`AbstractTraceReplayTest` driver prints its concrete test, resolved fixture,
exact published report and a copyable triage
command. `trace.print.summary=true` also prints that route for a successful
replay. Copy the command to inspect the report, its sibling context and the
fixture's plain or compressed auxiliary evidence without finding hashed report
names yourself.

After completion, read this class's fresh Surefire result under
`target/surefire-reports/`. The triage brief distinguishes a completed comparison
gate, an incomplete prefix and reports with no published gate, whose completion
is unknown. Some current special-stage/credits producers also omit this metadata
and do not print the standalone diagnostic route; inspect their own publication
and Surefire evidence. Credits currently publish only on divergence, so even a
clean rerun can leave an older failing report. UNKNOWN does not by itself mean an
old file.
Planned trace rows and gameplay comparisons count different boundaries; their
totals need not match. Auxiliary-availability notes are best effort: an absent or
unrecognised optional field does not establish full reference coverage. Triage
reads saved evidence and does not certify its freshness or process success. Confirm this invocation executed without errors
or skips and reached its intended gate. Setup failures and prerequisite skips may
happen before a new report is published, leaving an older same-test artifact.
Use fresh `-Dopenggf.surefire.reports=target/<attempt>/surefire` and
`-Dopenggf.trace.reports=target/<attempt>/trace-reports` directories when retaining
an invocation; an owner hash distinguishes tests, not freshness. Preserve prior
evidence before clearing generated reports, and keep output in this worktree's
`target/`.

On method entry, the standalone driver retires earlier report/context/owner files
only when their sidecar verifies this invocation, lane and verification scope.
It preserves unverified or foreign artifacts, and leaves the whole triple when
the context path is a link or directory. Retirement is also disabled if the report
path or any ancestor is symbolic, even though publication can follow that link.
Conditions and extensions that prevent method entry cannot use retirement, so
it does not replace fresh output directories and Surefire evidence.

For a real divergence, read the
[trace diagnosis procedure](../../../.agents/skills/trace-replay-bug-fixing/SKILL.md)
and its linked measurement hazards before reporting counts or changing code.
Comparison spans use the loaded `TraceFrame.frame()` key from the CSV; the replay
loop's row ordinal and ROM counters are separate. Check each auxiliary lookup's
key rather than assuming the numbers coincide;
trace comparisons do not authorise copying observed gameplay state into the
engine. Record new measured frontiers in the existing
[frontier log](../../status/trace-frontier-log.md), with the tested commit and command.

For the full workflow, including recording traces and reading divergence reports, see
[Trace Replay Testing](trace-replay.md).

## Visual Regression Tests

Visual regression tests capture rendered frames and compare them against reference images.
They verify that changes to the rendering pipeline or art loading do not introduce visual
regressions.

These tests require ROM files and a GPU context. On Linux, add
`-Dopenggf.headless.gl=surfaceless-egl` to the queued Maven command so they render
offscreen without a display; see
[offscreen rendering prerequisites](headless-testing.md#offscreen-rendering-prerequisites).
They typically:
1. Load a level and set the camera to a known position.
2. Render one or more frames.
3. Compare the rendered output against a stored reference image.
4. Fail if the pixel difference exceeds a threshold.

Visual regression tests are slower and more environment-sensitive than headless tests.
They are primarily used for validating art loading, palette, and rendering pipeline changes.

## Audio Regression Tests

Audio tests verify that the SMPS sequencer produces correct output for specific songs.
The approach:

1. Load a music track from the ROM.
2. Run the sequencer for N frames.
3. Capture the register writes or audio samples.
4. Compare against expected output (from SMPSPlay or a known-good capture).

These tests catch regressions in tempo calculation, note mapping, modulation, and channel
state management.

## Tips

- **Keep tests independent.** The default reuses one JVM, and an optional profile
  uses two forks. Do not depend on another test's state or execution order.
- **Skip gracefully without ROMs.** Use `@RequiresRom` to gate ROM-dependent tests.
  Never let a test fail just because a ROM is absent.
- **Use constants from the disassembly.** When asserting positions or velocities, use the
  same hex values that appear in the ASM. This makes it easier to trace failures back to
  the source.
- **Test behavior, not implementation.** Assert that the player lands on the ground, not
  that a specific internal method was called.

## Next Steps

- [Dev Setup](dev-setup.md) -- Build and run configuration
- [Tutorial: Implement an Object](tutorial-implement-object.md) -- Testing is step 7
- [Trace Replay Testing](trace-replay.md) -- ROM-vs-engine parity workflow
