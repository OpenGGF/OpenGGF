# Release Readiness Roadmap

Date: 2026-06-06

This roadmap captures the architecture and code review findings gathered before
the next release. The release standard is: no known hidden failures. A release
candidate must not rely on disabled known-failing tests, CI paths that skip
policy, undocumented trace/bootstrap carve-outs, or guard tests that silently
miss known bad forms.

The work is split into two phases:

- Phase 1: Release hardening - make release validation honest and fix defects
  that can corrupt runtime state, rendering, or route confidence.
- Phase 2: Broad cleanup - remove tolerated debt, strengthen architectural
  boundaries, and reduce future trace/object/runtime migration risk.

## Phase 1: Release Hardening

These items should be completed, explicitly documented as release limitations,
or consciously removed from release claims before cutting a release candidate.

### 1. CI and test honesty

Goal: release CI and local verification must expose known failures instead of
hiding them.

Work:

- Run branch policy validation on release/master PR paths, not only PRs into
  `develop`.
  - Current gap: `.github/workflows/ci.yml` only scopes policy to `develop`.
  - Current gap: `.githooks/validate-policy.sh` returns early unless
    `base_ref == develop`.
- Resolve disabled "keep green" S3K tests.
  - `TestS3kAiz1SkipHeadless` disables hollow-log traversal scenarios.
  - `TestS3kCnzCarryHeadless` disables a known failing parity assertion.
  - Outcome must be one of: enabled and passing, enabled as an explicit blocker,
    or documented in the relevant discrepancy/release files and removed from
    "keep green" language.
- Fix ROM-gated tests to use the same resolved ROM path as `@RequiresRom`.
  - `TestSonic3kLifeIconAddresses` currently opens the default filename
    directly.
  - Add an assumption or fixture check around required disassembly/reference
    files when the test needs them.
- Make ROM availability checks validate that `rom.open(...)` succeeds.
  - `RomTestUtils` currently treats an existing configured file as available.
  - `RomCache` ignores the boolean return from `rom.open(...)`.
- Tighten guard tests that can hide release-relevant problems.
  - `TestNoServicesInObjectConstructors` misses variable-based
    `addDynamicObject` construction.
  - `TestSingletonLifecycleGuard` skips entire files based on a loose
    `@ExtendWith(SingletonResetExtension.class)` substring check.

Recommended order:

1. Extend CI policy coverage to release/master PRs.
2. Fix ROM path/open validation so test enablement is trustworthy.
3. Tighten guard tests to catch the known missed patterns.
4. Revisit disabled "keep green" tests and choose pass, explicit blocker, or
   documented release limitation for each.
5. Update `AGENTS.md`, `README.md`, `KNOWN_DISCREPANCIES.md`,
   `S3K_KNOWN_DISCREPANCIES.md`, or release notes where claims change.

Validation:

- Run the policy script against develop and release-style PR inputs.
- Run focused ROM-gated tests with the ROM supplied through property/env/config
  paths.
- Run the guard tests after adding fixtures for the missed forms.

### 2. Runtime and session safety

Goal: runtime-owned managers and module-owned providers must not survive beyond
their owning session or module.

Work:

- Make `GameLoop.refreshRuntimeBindings()` use the gameplay context readiness
  predicate instead of only checking `getCamera() == null`.
  - Current risk: a torn-down `GameplayModeContext` can still have non-null
    manager fields and be rebound by a surviving `GameLoop`.
- Clear or re-resolve `GameLoop.titleCardProvider` when the game module/session
  resets.
  - Current risk: returning to master title and switching ROM/game can keep the
    previous module's title-card provider.
- Keep AIZ terrain mutation on the supplied `ObjectServices` path.
  - Current risk: `AizIntroTerrainSwap` builds mutation context from injected
    services but applies through global `GameServices.zoneLayoutMutationPipeline`.
- Reset or scope AIZ intro overlay ROM data by ROM/session.
  - Current risk: `AizIntroTerrainSwap.cachedOverlayData` can outlive the ROM
    it was loaded from.

Recommended order:

1. Add focused tests for torn-down gameplay context binding and title-card
   provider reset.
2. Fix `GameLoop` runtime binding readiness.
3. Fix title-card provider cache invalidation.
4. Add AIZ overlay cache reset/scoping tests.
5. Keep AIZ terrain mutation on injected services, with a test that uses
   explicit services while another runtime is active or absent.

Validation:

- Run focused session/runtime tests.
- Run a headless or integration path that returns to master title and starts a
  different module.
- Run AIZ intro terrain swap tests after cache reset changes.

### 3. Object lifecycle and virtual pattern allocation

Goal: object-owned children must use shared lifecycle paths, and virtual pattern
IDs must not collide.

Work:

- Move constructor-time child spawning out of constructors for:
  - `TurtloidBadnikInstance`
  - `SolBadnikInstance`
- Convert those child spawns to `spawnChild`, `spawnFreeChild`, or a
  post-registration lifecycle hook that preserves construction context and
  parent/child ownership.
- Move `LightningSparkObjectInstance.SPARK_PATTERN_BASE` out of the shared
  object-art range or allocate it through the owning art/provider range.
  - Current value `0x20100` sits inside `PatternAtlasRange.OBJECTS`.
- Add guard coverage for hard-coded virtual pattern bases inside reserved
  ranges where practical.

Recommended order:

1. Add tests that fail on constructor-time service access/raw child spawn for
   Turtloid and Sol.
2. Introduce or reuse a post-registration child-spawn path.
3. Convert Turtloid and Sol.
4. Add a virtual pattern range collision test for lightning sparks.
5. Move the spark base and verify rendered pattern IDs no longer overlap object
   art allocation.

Validation:

- Run object lifecycle guard tests.
- Run focused Turtloid/Sol object tests or zone object-loading tests.
- Run focused lightning shield/spark render tests if present; otherwise add a
  non-GL atlas ID allocation test.

### 4. S3K runtime visibility

Goal: release-priority S3K zones should expose event state through the typed
runtime framework instead of ad hoc manager casts or camera formulas.

Work:

- Add ICZ runtime state registration.
  - Current gap: `Sonic3kLevelEventManager.installZoneRuntimeState` clears the
    registry for ICZ.
- Move ICZ palette and animated-tile phase queries onto typed runtime state.
  - Current gap: `Sonic3kPaletteCycler` casts back to
    `Sonic3kLevelEventManager`.
  - Current gap: `Sonic3kPatternAnimator` recomputes ICZ phase from camera.
- Add MHZ to `currentRuntimeStateUsesThisEventInstance`.
  - Current risk: MHZ state can be repeatedly reinstalled/reset.
- Decide ICZ character-route release scope.
  - Current behavior is Sonic-alone focused; Knuckles/Tails routes are not
    equivalent.

Recommended order:

1. Fix MHZ current-state recognition first; it is small and concrete.
2. Add tests proving ICZ currently lacks runtime state.
3. Introduce an ICZ runtime state backed by `Sonic3kICZEvents`.
4. Move ICZ palette/pattern consumers to the runtime state.
5. Add release-scope documentation or tests for Sonic/Tails/Knuckles ICZ route
   behavior.

Validation:

- Extend `TestS3kZoneRuntimeRegistrationHeadless` to cover ICZ and MHZ.
- Run focused S3K palette/pattern animator tests.
- Run ICZ route smoke tests for each character configuration claimed by the
  release.

### 5. Trace and physics release visibility

Goal: trace and physics exceptions must be either fixed or visible as release
limitations.

Work:

- Remove, replace, or explicitly document the legacy S3K AIZ trace replay
  carve-out.
  - Current risk: `TraceReplayBootstrap.isLegacyS3kAizIntroTrace` gates behavior
    on game, zone, act, and checkpoint.
  - Preferred fix: model the ROM state that drives the difference instead of
    recognizing the fixture.
- Decide how to handle S1/S2 bottom-boundary centre-Y parity.
  - Current risk: comments document ROM centre-Y behavior, but S1/S2 feature
    flags intentionally preserve top-left trace baselines.
  - Do not flip this casually; revalidate affected S1/S2 trace frontiers.
- Classify S2 Tornado ride-start bootstrap state injection.
  - Current risk: the bootstrap writes substantial route/prelude state. It may
    be acceptable as a fixture contract, but it should not remain implicit.

Recommended order:

1. Document the current trace exceptions in `docs/TRACE_FRONTIER_LOG.md` and
   discrepancy files before changing behavior.
2. Add focused tests around the AIZ intro trace bootstrap decision points.
3. Replace fixture recognition with ROM-state-driven phase handling, or mark it
   as a release-blocking discrepancy.
4. Add focused bottom-boundary tests for S1/S2 centre-Y behavior.
5. Re-run affected S1/S2 traces before changing the feature flags.
6. Document the S2 Tornado bootstrap contract or narrow it to ROM-state-driven
   inputs.

Validation:

- Run focused trace replay tests affected by each change.
- Update `docs/TRACE_FRONTIER_LOG.md` whenever a frontier moves or a trace debt
  is explicitly accepted.

## Phase 2: Broad Cleanup

These items should not block the release if Phase 1 makes them visible and the
release notes do not overclaim. They should be tracked so the same issues do not
return under new names.

### 1. Complete object child-spawn migration

Work:

- Audit and convert remaining object-owned raw child spawns:
  - `BuggernautBadnikInstance`
  - `CaterkillerJrHeadInstance`
  - `CheckpointObjectInstance`
  - any additional object-owned direct `addDynamicObject` calls found by the
    strengthened guard.
- Keep manager/framework bridge code as explicit exceptions with documented
  rationale.
- Replace raw-call count ratchets with semantic checks where possible.

Recommended order:

1. Expand the scanner to classify direct child construction assigned to
   variables.
2. Inventory all raw object-owned child spawns.
3. Convert the easiest non-bridge cases first.
4. Document remaining bridge exceptions.
5. Ratchet the guard downward after each conversion.

### 2. Strengthen architectural guardrails

Work:

- Tighten direct map mutation detection.
  - Earlier local review noted the guard claims to ban both `getMap().setValue`
    and `map.setValue`, but the scan primarily catches the former shape.
- Improve singleton lifecycle scanning so extension use does not skip an entire
  file.
- Add a guard for hard-coded virtual pattern bases in owned ranges.
- Keep JUnit 4 and runtime-docs asset-read guards in place; no active defect was
  found there.

Recommended order:

1. Add regression fixtures for every known missed pattern.
2. Improve scanners one guard at a time.
3. Verify existing intentional exceptions still pass.
4. Document exception criteria in `docs/architecture/archunit-exceptions.md` or
   the closest guardrail doc.

### 3. Trace bootstrap and fixture contract cleanup

Work:

- Move trace bootstrap policy toward ROM-state predicates instead of trace
  names, zones, checkpoints, or route-specific heuristics.
- Make comparison-only invariants easier to audit.
- Give S2 Tornado bootstrap a named contract and tests that prove it does not
  hydrate engine state from trace rows during replay comparison.

Recommended order:

1. Inventory all trace bootstrap predicates.
2. Split fixture metadata interpretation from runtime state application.
3. Add tests that reject new zone/route/frame carve-outs.
4. Convert one bootstrap exception at a time.

### 4. S3K zone runtime completion

Work:

- Bring remaining S3K zones onto runtime-owned state where event, palette,
  parallax, animated-tile, or mutation behavior is currently manager-local.
- Verify ICZ, MHZ, LBZ, and later zones against the zone analysis docs.
- Move direct camera/event formulas into typed state where the same value is
  consumed by multiple systems.

Recommended order:

1. Finish ICZ from Phase 1.
2. Audit each `docs/s3k-zones/*-analysis.md` against registered runtime state.
3. Prioritize zones by release route impact.
4. Add zone runtime registration tests as each zone is completed.

### 5. S3K asset provenance and documentation cleanup

Work:

- Verify S3-half address exceptions in `Sonic3kConstants`.
  - The AIZ/HCZ/MGZ/LBZ breakable-wall mapping block currently uses an
    "S3 half addresses for S3-era zones" comment rather than documenting
    per-label S&K-side lookup results.
- Refresh stale comments and docs discovered during review.
  - Example: S3K object registry comments that imply placeholder-only behavior
    despite real factories.
  - Example: boss/object comments that still describe implemented code as
    stubbed.

Recommended order:

1. Use `RomOffsetFinder` or disassembly lookup to verify each S3-half exception.
2. Update comments with exact provenance.
3. Move real discrepancies into `S3K_KNOWN_DISCREPANCIES.md`.
4. Remove stale implementation comments as files are touched for nearby work.

### 6. S1/S2 physics parity debt

Work:

- Revisit physics flags that intentionally preserve old trace baselines despite
  known ROM behavior.
- Start with bottom-boundary centre-Y because the code already documents the ROM
  evidence.
- Advance trace frontiers deliberately and update trace docs.

Recommended order:

1. Add focused unit/headless tests for the ROM-level behavior.
2. Run affected S1/S2 trace suites before flipping flags.
3. Flip one game at a time.
4. Fix resulting divergences from ROM state, not route exceptions.
5. Update `docs/TRACE_FRONTIER_LOG.md`.

## Items Reviewed With No Current Fix Required

These areas were checked during the review and did not produce an actionable
defect:

- No real JUnit 4 test source leakage was found.
- No production runtime object/art/PLC loader was found reading gameplay asset
  bytes from `docs/` disassembly files.
- The shared ENEMY touch-response path still polls continuously every frame.
- Plain `renderPattern` did not show a proven virtual-ID truncation issue; the
  actionable defect is the overlapping hard-coded spark allocation.

## Recommended Branch and Commit Shape

Use one hardening branch for Phase 1, for example:

`bugfix/ai-release-hardening`

Suggested commits:

1. `fix(ci): enforce release policy checks`
2. `fix(test): make rom-gated release tests honest`
3. `fix(runtime): avoid stale gameplay bindings`
4. `fix(runtime): reset module-owned title and aiz caches`
5. `fix(objects): move constructor child spawns to lifecycle path`
6. `fix(render): move lightning spark virtual patterns out of object range`
7. `fix(s3k): register icz and mhz runtime state correctly`
8. `docs(trace): expose remaining trace bootstrap release debts`

Phase 2 should use smaller follow-up branches by subsystem rather than one broad
cleanup branch.
