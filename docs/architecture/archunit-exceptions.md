# ArchUnit Exceptions

This document records frozen architecture-rule violations introduced with the
ArchUnit PR 2 rollout. These are not desired growth areas. They are existing
bridges or debt that should not expand while the project migrates toward clearer
ownership boundaries.

Freeze baselines live under `src/test/resources/archunit/frozen`. The store is
configured by `src/test/resources/archunit.properties` with store creation
disabled after baseline generation, so adding a new frozen rule requires an
explicit baseline update.

## Policy

- Use explicit allowlists for permanent bridge classes that are part of the
  architecture.
- Use `FreezingArchRule` for existing debt that should not grow.
- Prefer deleting frozen entries by refactoring toward providers, shared helper
  packages, or injected services.
- Do not use `freeze.refreeze=true` as a routine workflow; update baselines only
  when the architectural exception decision is reviewed.

## Decay Targets

Targets are observable (tied to specific migrations), not calendar-bound. When
a target is hit, refresh the baseline in a dedicated commit and pick the next
target rather than walking back the assertion.

| Rule | Baseline | Target | Trigger |
|------|----------|--------|---------|
| `low_level_layers_do_not_depend_on_runtime_layers` | 213 | <=150 | AudioManager/GraphicsManager runtime callbacks migrate off direct level/sprite imports |
| `shared_layers_do_not_depend_on_game_specific_packages` | 11 | 0 | `DefaultPowerUpSpawner` visual object creation moves behind provider contracts and bootstrap module construction becomes an explicit composition-root allowlist |
| `per_game_packages_do_not_cross_depend` | 37 | <=20 | Data-select preview loading, payload validation, and menu animation helpers extracted out of per-game packages |

## Source Ratchets

Some review findings are source-shape constraints rather than ArchUnit bytecode
rules. These live in `TestArchitecturalSourceGuard` and use shrink-only budgets:
the current count is the maximum, and reductions do not require a baseline
refresh unless this document is updated to publish a lower target.

| Rule | Baseline | Target | Trigger |
|------|----------|--------|---------|
| Root `Engine` / `GameLoop` concrete Sonic references | `Engine.java`: 3, `GameLoop.java`: 15 | 0 | Mode startup, data-select, bonus-stage, and debug special cases move behind module/provider contracts |
| `ObjectManager` concrete Sonic references | 0 | 0 | Rewind dynamic-object recreation moves to game/module-registered codecs and factories |
| Low-level graphics/audio gameplay `GameServices` lookups | 0 | 0 | Camera/fade state is passed through render orchestration or explicit context objects |
| Root dispatcher method sizes | `Engine.draw`: 3, `Engine.init`: 180, `Engine.display`: 174, `GameLoop.stepInternal`: 207, `GameLoop.doExitBonusStage`: 142, `GameLoop.updateSpecialStageInput`: 101, `GameLoop.loadEndingDemoZone`: 95, `GameLoop.enterTitleCardFromResults`: 91, `GameLoop.enterBonusStage`: 86 | Decrease each touched method | Mode/render responsibilities extract into focused controllers without growing the root dispatchers |
| Object lifecycle raw calls in production object packages | `setDestroyed(true)`: 585, `addDynamicObject(...)`: 136 | 0 | New lifecycle work routes through `ObjectLifetimeOps`, `spawnChild(...)`, `spawnFreeChild(...)`, or `ObjectManager.createDynamicObject(...)`; shared lifecycle owner/wrapper classes remain exempt |

When a source ratchet fails, prefer moving the new dependency behind an existing
provider, service context, codec registry, or mode collaborator. Raising a
budget should be treated like accepting a new architecture exception: document
the reason and the next removal step in the same commit.

## Published Baseline Counts

These counts are mechanically checked against `src/test/resources/archunit/frozen/stored.rules`
by `TestArchitecturalReviewGuard.archUnitPublishedBaselineCountsMatchFrozenStore`.
When a frozen baseline shrinks or grows intentionally, update the matching count
in the same commit.

- `low_level_layers_do_not_depend_on_runtime_layers`: 213
- `shared_layers_do_not_depend_on_game_specific_packages`: 11
- `per_game_packages_do_not_cross_depend`: 37

## Package Cycle Ratchets

`package_slices_are_free_of_cycles`: 1 frozen top-level package cycle cluster.
`core_runtime_cycle_cluster_does_not_gain_top_level_edges`: 122 current
top-level dependency edges inside or adjacent to that cluster.

- `cycle:core-runtime`: 16 top-level slices (`audio`, `camera`, `control`,
  `data`, `debug`, `editor`, `game`, `graphics`, `level`, `physics`, `sprites`,
  `testmode`, `timer`, `tools`, `trace`, `util`). Owner package: shared runtime
  architecture. Intended direction: split runtime service roots and tooling/debug
  edges out of gameplay/data/graphics ownership loops. Current debt is ratcheted
  as explicit source/target top-level dependency edges in
  `CORE_RUNTIME_TOP_LEVEL_DEPENDENCY_EDGES`; new internal or adjacent edge pairs
  are not covered by a cluster-wide ignore. First decay target: remove at least
  one edge from that set when its dependency direction is eliminated.

If a temporary package-cycle cluster is accepted later, document it as
``cycle:<name>`` with current count, owner package, intended direction, and first
decay target. Add a matching `@ArchTest` method or frozen-store entry in the same
commit so `TestArchitecturalReviewGuard.archUnitCycleClusterDocumentationHasMatchingRatchets`
can enforce the ratchet.

## Frozen Rules

### Object Packages Must Not Access Global GameServices

Rule: `object packages should not access global GameServices except approved bridges`

Allowed bridge classes are excluded directly in the ArchUnit rule:

- `AbstractObjectInstance`
- `DefaultObjectServices`
- `BootstrapObjectServices`
- classes whose simple name ends with `ObjectRegistry`

Frozen violations:

- `AizIntroTerrainSwap` still reaches `GameServices.hasRuntime()` and
  `GameServices.zoneLayoutMutationPipeline()` during immediate terrain mutation.
- `ObjectManager.SolidContacts` reaches `GameServices.collision()` from shared
  object collision resolution.
- `ObjectManager.TouchResponses` reaches `GameServices.spritesOrNull()` while
  restoring rewind overlap state.

Target direction:

- Route mutation/collision/sprite dependencies through injected object or manager
  collaborators.
- Keep `ObjectServices` as the object-instance dependency boundary.

### Shared Layers Must Not Depend On Game-Specific Packages

Rule: `shared level and game layers should not depend on game-specific packages`

Frozen violations fall into these categories:

- `GameModuleRegistry` and `RomDetectionService` construct built-in game modules
  and detectors as bootstrap composition-root behavior.
- `DefaultPowerUpSpawner` constructs Sonic 1 splash and S3K shield/insta-shield
  visuals from shared object code.

Target direction:

- Move game-specific visual object creation behind providers.

### Per-Game Packages Must Not Cross-Depend

Rule: `per-game packages should not cross-depend`

Frozen violations fall into these categories:

- S2 data-select preview loading reuses S1 preview loader and common payload
  validator code.
- S3K data-select and host-preview presentation reuses S1/S2 preview loaders,
  cached preview managers, S1 special-stage palette extraction, and S2 selected
  slot previews.
- S3K level select reuses the S2 menu background animator.

Target direction:

- Extract shared data-select preview loading, payload validation, and menu
  animation helpers out of per-game packages.
- Keep cross-game donation explicit when reuse is intentional and ROM-accurate
  host presentation depends on older-game assets.

### Runtime-Owned Frameworks Must Not Access Global Service Roots

Rule: `runtime-owned framework packages should not access GameServices, SessionManager, or EngineServices directly`

Frozen violations fall into these categories:

- `ScriptFramesApplyStrategy` resolves `GameServices.graphics()` at apply time
  as a bridge for existing animated tile script paths.
- `LiveRewindManager` reaches `GameServices.audio()`,
  `GameServices.fadeOrNull()`, and `SessionManager.getCurrentGameplayMode()`
  while the live rewind presentation path is still process-service driven.
- `LiveRewindStepper` reaches `GameServices` optional accessors for the
  current sprites, level, and camera during replay stepping.

Target direction:

- Pass graphics/audio/fade/runtime dependencies through explicit channel,
  rewind, or gameplay-mode collaborators.
- Keep the runtime-owned framework packages independent of static service roots
  so they remain owned by `GameplayModeContext`.

### Runtime Registries Must Be Constructed By Runtime Composition Roots

Rule: `runtime-owned registries and controllers should only be constructed by runtime composition roots`

Frozen violations:

- `Sonic2PaletteCycler` and `Sonic3kPaletteCycler` create fallback
  `PaletteOwnershipRegistry` instances for standalone/test paths when no
  runtime registry is supplied.
- `DefaultObjectServices` creates fallback `ZoneRuntimeRegistry` and
  `ZoneLayoutMutationPipeline` instances in its legacy constructor.

Target direction:

- Require runtime-owned registries/controllers to be supplied by
  `GameplaySessionFactory` or `GameplayModeContext`.
- Keep test and legacy bootstrap paths on explicit stub/runtime collaborators
  instead of constructing ad hoc registries.

### Shared Code Must Not Construct Concrete Sonic Provider Classes

Rule: `shared code should not construct concrete Sonic provider/art/object classes`

Frozen violations:

- `DefaultPowerUpSpawner` directly constructs the Sonic 1 splash object from
  shared object code.

Target direction:

- Move game-specific visual object creation behind `GameModule` provider
  contracts or a dedicated composition root.
  Keep shared object helpers independent of concrete Sonic object classes.

## Maintenance

When a frozen violation is removed:

1. Run `mvn test "-Dtest=TestArchUnitRules,TestArchUnitTestRules"`.
2. Let ArchUnit remove obsolete lines from the relevant freeze file.
3. Review the diff to confirm only resolved violations disappeared.
4. Update this document if the exception category or target direction changed.
