# Held VBlank service and clock ownership

## Problem and grounding

Physical service, clock advancement, gameplay execution and recorded-row
consumption are separate decisions. The [held-fade repair](../audits/2026-11-03-held-fade-vint-service.md)
showed the danger of gating physical service on comparison ownership. The
[post-return investigation](../audits/2026-11-03-s3k-post-return-kosm-queue.md)
found a different error: setup-only title release was mistaken for a completed
destination gameplay row. Neither finding justifies replacing resource queues
or inventing elapsed load time.

Grounding source: `b4e2ac27cf8f6dfdda1f5fe2e04a317e6ef908a0`.
`LevelFrameStep` owns production boundary order; `LevelIterationAdmissionController`
owns admission and fade-row consumption; `GameLoop` owns bonus iteration counts
and completion. `PlcLifecycleFrame` owns the semantic PLC phase and once-only
game VBlank publication. `HardwareBoundaryDispatch` owns resource-service order.
The bound `ObjectManager` owns its clock; the detached `VIntRunCounter` remains
the power-on carrier. Rewind already captures those owners separately.

Three callers currently pair `serviceHardwareVBlankOnly` with a clock tick.
The first migration makes that pair one operation in its existing owner.

## Usage, before the type sketch

Held transition fade preserves the observer decision sampled before callbacks:

```java
boolean ownsRecordedRow = playback.observerHasUnconsumedRecordedRows();
if (objects != null) {
    LevelFrameStep.serviceHeldVBlank(context, frame, objects::advanceVblaCounter);
}
if (ownsRecordedRow) playback.onLevelFrameAdvanced();
```

Bonus lag preserves its caller-selected count and live clock lookup after service:

```java
for (int tick = 0; tick < vblankTicks; tick++) {
    LevelFrameStep.serviceHeldVBlank(
            LevelFrameContext.from(gameplayMode), activePlcLifecycleFrame,
            () -> levelManager.getObjectManager().advanceVblaCounter());
}
```

Bonus exit hold uses the same operation once, inside its existing object-manager
guard, then advances playback once. Audio stays at each caller's existing site.

## Shape and invariants

The sketch is a single package-private entry in `LevelFrameStep`:

```java
static void serviceHeldVBlank(
        LevelFrameContext context, PlcLifecycleFrame frame, Runnable advanceClock);
// Validate advanceClock; dispatch game VBlank; service VINT_SERVICE; advanceClock.
```

The clock callback follows the existing supplied-pause-clock precedent. It
preserves the captured fade receiver versus bonus callers' live receiver.
No new state, snapshot, registry, phase claim or frame-count derivation is added.
Remove the old raw helper and migrate all three callers together.

Only game VBlank publication is once per token. Hardware service and the clock
run on every invocation: bonus lag can request zero or multiple services under
one token. Pause's first-successful-claim clock policy remains separate.
Service failure does not advance the clock. Observer ownership never enters
the physical-service operation. Existing null guards retain their eligibility
policy; detached-clock behavior is outside this migration.

The small interface hides ordering that callers previously repeated. It cannot
prove that a supplied callback advances the intended clock, so real caller
tests remain necessary. It deliberately does not absorb playback or audio.

## Synthesis decision

Two independent design arms used the configured Codex model family; this was
limited model diversity. The coordinator synthesized the candidates without an
additional cross-family judging round for this bounded change.

Candidate A strengthens `LevelFrameStep` as above. Candidate B introduces a
`HeldLevelIteration` collaborator with three closed protocol kinds, owning audio
placement, tick counts, clock binding and playback completion. B hides more
caller decisions, but moves correct audio/playback ownership and introduces a
live-access adapter to reproduce existing distinctions. It still needs the same
underlying service semantics. A is the base: smaller migration, no additional
owner, and a direct equivalence argument. B's explicit behavior table informs
the preservation checks.

Both were screened for shallow wrappers, split ownership, temporal decomposition,
duplicate APIs, importable internals and hand-synchronized lists. A removes the
raw paired-service entry and keeps the replacement package-private. A general
transition state machine and whole-token clock deduplication are rejected.

## Implementation and verification

1. Preserve the existing focused lifecycle baseline.
2. Replace the raw helper, migrate the three pairs, and update bonus caller
   coverage to invoke the real operation.
3. Add short behavioral witnesses for repeated same-token services, ordered
   resource/clock effects, once-only publication and service failure. Preserve
   observer sampling, absent-object behavior and captured/live clock binding.
4. Inspect the change-based selection; verify affected lifecycle, pause, title,
   timing-authority, counter/rewind and S3K loading consumers. Attribute failures
   by identity. Integrate and push through the repository delivery flow.

This is structural hardening. It adds no timing budget, queue work, trace
authority or gameplay correction. Destination row zero retains its existing
setup-only contract. The [synchronous-load accounting proposal](2026-11-03-synchronous-load-vint-accounting.md)
remains deferred; unresolved load spans must not be priced from observed deficits.
Future work should earn a larger protocol with repeated concrete failures that
the smaller boundary cannot prevent.

## Validation selection

The change-based plan selects all 4,419 smoke classes because the entry files
are shared. This extraction preserves the existing algorithm, service counts,
receiver binding, cursor/audio order and eligibility conditions. Focused checks
cover every migrated caller and the affected lifecycle, title, pause, authority,
clock/rewind and S3K loading consumers instead of repeating the whole engine suite.

These selectors reproduce the baseline and candidate commands. `S3K_ROM` is the
absolute path to the existing original locked-on ROM, whose SHA-1 was verified
against the repository's documented identity; no ROM was copied or linked for
these commands.

```bash
HELD_BASELINE_TESTS=TestLevelIterationAdmissionController,TestLevelIterationHardwareTimingAdmissionOrder,TestGameLoopBonusStageSession,TestLevelFrameHardwareTimingBoundaries,TestPausedVBlankAdmission,TestPlcFrameLifecycleCoordinator,TestPlcLifecycleDriverParity,TestGameLoopVIntRunCounter,TestVIntRunCounter
HELD_CONSUMER_TESTS=TestHardwareTimingAuthorityGuard,TestHardwareTimingService,TestGameLoopTitleCardLifecycle,TestGameLoopTitleCardOwnership,TestGameLoopTitleCardPause,TestLiveRewindStepperPlcLifecycle,TestTraceRunReplayWalkerControlFlow,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils

env JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  "-Dtest=$HELD_BASELINE_TESTS" test -B

env JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  "-Dtest=$HELD_BASELINE_TESTS,$HELD_CONSUMER_TESTS" \
  "-Ds3k.rom.path=$S3K_ROM" test -B

env JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dtest=TestGameLoopBonusStageSession test -B
```

The baseline on the grounding SHA passed 94 tests, without failures, errors or
skips. The candidate command executed 306 tests: 304 passed and two bonus held-row
cases failed, without errors or skips. Their static frame-step mock suppressed
the private resource-service dispatch needed by the new rebinding witness. The
fixture now runs the real operation with a real token/context and verifies actual
resource service, live clock rebinding and absence of gameplay/rewind effects.
Only that test fixture changed after the combined run; production code and the
other 284 passing tests' inputs are unchanged. The last command passed all 22
tests in the affected class, with no failures, errors or skips. There are no
remaining failures in this selected coverage. This is focused validation, not
an all-smoke or campaign-trace qualification.

Preflight passed with Java 25 and explicit Lua 5.4. Documentation links,
whitespace and the unchanged instruction mirror were checked. Queue waits were
303 seconds for the baseline, 428 for the combined candidate and 79 for the
affected-class rerun; Maven execution reported 108, 112 and 38.122 seconds
respectively. Other elapsed work was not measured.
