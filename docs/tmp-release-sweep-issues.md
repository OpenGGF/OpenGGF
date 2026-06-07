# Temporary Release Sweep Issues

Generated from the 2026-06-07 architecture/code review. This file is a working
scratch tracker for the release sweep; remove it or fold it into permanent docs
before final release sign-off.

Status legend: `[ ]` open, `[~]` in progress, `[x]` verified complete, `[d]`
explicitly deferred with rationale.

## Verification Gates

- [x] `mvn package`
- [x] Focused guard suite:
  `mvn "-Dtest=TestArchitecturalReviewGuard,TestArchitecturalSourceGuard,TestTraceReplayInvariantGuard,TestJunit5MigrationGuard,TestNoServicesInObjectConstructors,TestNoDirectMapMutationsInGameplay,TestObjectServicesMigrationGuard,TestPlaybackDebugLiveTracePolicy,TestTraceReplayStartPositionPolicy,TestTitleCardPhysicsPolicy,TestCompactFieldCapturerPolicy,TestRewindPolicyRegistry,TestS3kHczPaletteOwnershipMigrationGuard,TestS3kSuperPaletteOwnershipMigrationGuard" "-DfailIfNoTests=false" test`
- [x] Focused tests for every changed subsystem listed below.

Verification completed on 2026-06-07:

- `mvn clean test-compile`: passed.
- Focused guard/subsystem suite including the listed guards plus release-sweep
  tests: 231 passed, 0 failed, 0 errors, 0 skipped.
- S3K visual parity focused suite: 296 passed, 0 failed, 0 errors, 0 skipped.
- `mvn package`: 7070 passed, 0 failed, 0 errors, 2 skipped.

## Immediate Build Blocker

- [x] **Package suite order leak: `TestHczLargeFanRegistry` fails in full `mvn package`.**
  - Evidence: full package run failed at
    `com.openggf.game.sonic3k.objects.TestHczLargeFanRegistry#registryCreatesHczLargeFanForId0x39InS3klZoneSet`.
    The test passes in isolation.
  - Likely cause: S3K registry derives zone set from leaked `GameServices.levelOrNull()`
    via `AbstractObjectRegistry.currentRomZoneId()`, so a prior reused fork can leave
    an SKL-zone level active and force ID `$39` to a placeholder.
  - Files:
    `src/test/java/com/openggf/game/sonic3k/objects/TestHczLargeFanRegistry.java`,
    `src/main/java/com/openggf/level/objects/AbstractObjectRegistry.java`,
    `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kObjectRegistry.java`.
  - Done when: `mvn package` passes without relying on test-order luck.

## Trace / Physics Policy

- [d] **S3K AIZ legacy trace replay uses a zone/act/checkpoint carve-out and can compare trace data as actual engine state.**
  - Evidence: `TraceReplayBootstrap.isLegacyS3kAizIntroTrace(...)` gates replay
    behavior by `game == "s3k"`, AIZ act/checkpoint identity, including a path where
    pre-level rows can return `ReplayPrimaryState.fromTraceFrame(...)` as actual state.
  - Files: `src/main/java/com/openggf/trace/TraceReplayBootstrap.java`,
    `docs/KNOWN_DISCREPANCIES.md`, `docs/RELEASE_READINESS_ROADMAP.md`.
  - Done when: the carve-out is removed or explicitly deferred in permanent release
    docs with a bounded rationale that does not claim full trace proof for that path.
  - Status: trace-row primary-state substitution was removed; the remaining legacy
    fixture-identity predicate is documented as diagnostic-only release debt in
    `KNOWN_DISCREPANCIES.md` / `RELEASE_READINESS_ROADMAP.md`.

- [d] **Frame-0 bootstrap comparator is warning-only when engine sidekick CPU or object snapshot coverage is missing.**
  - Evidence: `AbstractTraceReplayTest.captureEngineSnapshot()` leaves sidekick CPU
    and object snapshots empty; `TraceBinder` emits warnings for missing comparable
    engine views and replay tests fail only on errors.
  - Files: `src/test/java/com/openggf/tests/trace/AbstractTraceReplayTest.java`,
    `src/main/java/com/openggf/trace/TraceBinder.java`.
  - Done when: native-prelude traces either compare these views as errors or the
    release tracker documents the accepted coverage gap.
  - Status: documented as frame-0 bootstrap snapshot coverage debt; release notes
    now avoid claiming strict sidekick/SST parity from warning-only gaps.

## S3K Runtime Assets / Visual Parity

- [x] **HCZ runtime mappings are still hand-built instead of parsed from the user ROM.**
  - Evidence: `Sonic3kPlcArtRegistry` permits `mappingAddr == -1`; HCZ water splash
    and related paths build mappings manually in `Sonic3kObjectArt` and
    `HCZWaterSkimHandler`.
  - Files: `src/main/java/com/openggf/game/sonic3k/Sonic3kPlcArtRegistry.java`,
    `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArt.java`,
    `src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java`.
  - Done when: affected HCZ mapping frames are ROM-parsed or explicitly deferred
    as an alpha limitation in release docs.

- [x] **AIZ fire curtain renderer can synthesize fallback tile descriptors when ROM-backed cache is unavailable.**
  - Evidence: `AizFireCurtainRenderer` documents and builds synthetic fallback
    descriptors if cache priming misses.
  - File: `src/main/java/com/openggf/game/sonic3k/features/AizFireCurtainRenderer.java`.
  - Done when: rendering fails closed or proves ROM-backed cache availability with tests.

## Resource Lifetime / Rendering

- [x] **Startup failures can leak GLFW/GL/audio resources.**
  - Evidence: `Engine.run()` calls `init()` before entering cleanup `finally`;
    `init()` creates native resources before later failure points.
  - File: `src/main/java/com/openggf/Engine.java`.
  - Done when: `init()` failure triggers tolerant cleanup and has a focused test or
    code-level guard.

- [x] **Failed OpenAL backend init can leak partially created native audio resources.**
  - Evidence: `AudioManager.setBackend()` installs fallback without destroying the
    failed candidate; `LWJGLAudioBackend.hookInitDevice()` allocates native handles
    before catch/rethrow.
  - Files: `src/main/java/com/openggf/audio/AudioManager.java`,
    `src/main/java/com/openggf/audio/LWJGLAudioBackend.java`.
  - Done when: failed backend candidates are destroyed and partial LWJGL init cleans
    up its own handles.

- [x] **Incomplete FBO status is logged but still treated as initialized.**
  - Evidence: `FboHelper.createColorOnly(...)` logs incomplete status and returns a
    handle; `TilePriorityFBO` marks itself initialized.
  - Files: `src/main/java/com/openggf/util/FboHelper.java`,
    `src/main/java/com/openggf/graphics/TilePriorityFBO.java`.
  - Done when: incomplete FBO creation fails closed with cleanup and deterministic fallback.

## Release Docs / CI

- [x] **Release install docs describe artifacts the release workflow does not publish.**
  - Evidence: getting-started docs describe JAR/run script install, while release
    workflow packages native `OpenGGF` artifacts.
  - Files: `docs/guide/playing/getting-started.md`,
    `.github/workflows/release.yml`.
  - Done when: docs and published artifact shape match.

- [x] **Power-up graphics regression test ignores configured release ROM fixture paths.**
  - Evidence: release CI passes ROM path properties; `TestPowerUpGraphicsRegression`
    checks exact working-directory filenames and can skip despite configured fixtures.
  - File: `src/test/java/com/openggf/game/TestPowerUpGraphicsRegression.java`.
  - Done when: test uses shared ROM path/property handling and no longer silently skips
    when fixture properties are present.

- [x] **Permanent release issue tracker has unresolved release-relevant items.**
  - Evidence: `docs/release-architecture-review-issues.md` still marks multiple
    release risks open.
  - File: `docs/release-architecture-review-issues.md`.
  - Done when: each item is resolved or explicitly deferred with rationale.

- [x] **Local untracked IDE file can be accidentally committed.**
  - Evidence: `git status --short --branch` reports `.idea/encodings.xml`.
  - File: `.gitignore`.
  - Done when: IDE metadata policy is explicit and the untracked file is removed,
    ignored, or intentionally staged.
  - Status: `.idea/encodings.xml` is intentionally included with the already
    tracked IntelliJ project metadata so source/resource UTF-8 settings are explicit.
