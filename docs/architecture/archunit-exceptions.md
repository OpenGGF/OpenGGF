# ArchUnit Exceptions

This document records frozen architecture-rule violations introduced with the
ArchUnit PR 2 rollout. These are not desired growth areas. They are existing
bridges or debt that should not expand while the project migrates toward clearer
ownership boundaries.

Freeze baselines live under `src/test/resources/archunit/frozen`. The store is
configured by `src/test/resources/archunit.properties` with store creation and
updates disabled during normal runs. Adding or changing committed allowances
requires an explicit, reviewed store update.

## Policy

- Use explicit allowlists for permanent bridge classes that are part of the
  architecture.
- Use `FreezingArchRule` for existing debt that should not grow.
- Prefer deleting frozen entries by refactoring toward providers, shared helper
  packages, or injected services.
- Do not use `freeze.refreeze=true` as a routine workflow; update baselines only
  when the architectural exception decision is reviewed.

## Decay Targets

Targets are tied to specific migrations, not calendar-bound. Counts below are
committed allowances read from the freeze store, not newly measured live
violations. When a target is hit, review the resolved records in a dedicated
baseline-update commit and pick the next target rather than weakening the rule.

| Rule | Committed allowance and unit | Target in that unit | Trigger |
|------|------------------------------|---------------------|---------|
| `low_level_layers_do_not_depend_on_runtime_layers` | 184 records | <=150 records | AudioManager/GraphicsManager runtime callbacks migrate off direct level/sprite imports |
| `shared_layers_do_not_depend_on_game_specific_packages` | 9 records | 0 records | Master-title ROM preview mapping moves behind provider contracts (`DefaultPowerUpSpawner` visual object creation moved behind `GameModule` factories, 2026-08-28) |
| `per_game_packages_do_not_cross_depend` | 34 member dependencies in 3 grouped slice records | <=20 member dependencies | Data-select preview loading, payload validation, and menu animation helpers extracted out of per-game packages |

2026-09-03: the two `AudioManager -> Rom.readBytes` SEGA-boot PCM edges were
solved and the frozen entry pruned. `GameAudioProfile.loadSegaPcm(Object)` now
declares the read and the per-game profiles perform it through
`com.openggf.game.audio.SegaPcmRomReader`, which lives in the runtime layer and
may depend on `com.openggf.data`. The rule description keeps its original
baseline wording because the freeze store is keyed by that full text.

The historical cross-game count of 37 counted physical lines: 3 slice headers
plus 34 member dependencies. The target is now explicitly <=20 **member
dependencies**. Three grouped records does not satisfy that target. Neither
physical lines nor grouped records can be substituted for member dependencies
when evaluating this migration.

## Source Ratchets

Some review findings are source-shape constraints rather than ArchUnit bytecode
rules. These live in `TestArchitecturalSourceGuard`. The figures below are
executable maximum budgets, not measurements of the current source. A passing
guard proves the source stays within its cap; reductions do not automatically
change these constants. Lower a cap explicitly when publishing a tighter budget.

| Rule | Enforced cap (not measured usage) | Target | Trigger |
|------|----------|--------|---------|
| Root `Engine` / `GameLoop` concrete Sonic references | `Engine.java`: 3, `GameLoop.java`: 15 | 0 | Mode startup, data-select, bonus-stage, and debug special cases move behind module/provider contracts |
| `ObjectManager` concrete Sonic references | 0 | 0 | Rewind dynamic-object recreation moves to game/module-registered codecs and factories |
| Low-level graphics/audio gameplay `GameServices` lookups | 0 | 0 | Camera/fade state is passed through render orchestration or explicit context objects |
| Root dispatcher method sizes | `Engine.draw`: 3, `Engine.init`: 181, `Engine.display`: 228, `GameLoop.stepInternal`: 235, `GameLoop.doExitBonusStage`: 142, `GameLoop.updateSpecialStageInput`: 105, `GameLoop.loadEndingDemoZone`: 95, `GameLoop.enterTitleCardFromResults`: 66, `GameLoop.enterBonusStage`: 45 | Decrease each touched method | Mode/render responsibilities extract into focused controllers without growing the root dispatchers |
| Raw child-spawn patterns in `OBJECT_CHILD_SPAWN_MIGRATED_FILES` | 0 matches per listed file | 0 | Migrated object-owned child creation stays on `spawnChild(...)` / `spawnFreeChild(...)` helpers |

The former aggregate lifecycle figures (`setDestroyed(true)`: 585,
`addDynamicObject(...)`: 136) were historical source inventories, not executable
caps. Current aggregate usage is unmeasured here; the migrated-file guard does
not enforce a global raw-call total.

When a source ratchet fails, prefer moving the new dependency behind an existing
provider, service context, codec registry, or mode collaborator. Raising a
budget should be treated like accepting a new architecture exception: document
the reason and the next removal step in the same commit.

## Raw Dynamic Object Bridge Exceptions

`TestArchitecturalSourceGuard#migratedObjectChildSpawnsStayOnManagedHelpers`
ratchets specific migrated object instance files so they cannot return to
context-less `new ...` plus `ObjectManager.addDynamicObject*` child spawning.
The remaining production raw dynamic-object calls are accepted only at these
bridge boundaries:

- `ObjectManager` owns the slot allocator and the `addDynamicObject*` API family.
- `AbstractObjectInstance` owns `spawnChild`, `spawnFreeChild`, and legacy
  dynamic-spawn helper wrappers.
- `ObjectLifetimeOps` owns transferred-slot and reserved-slot lifecycle helpers.
- `DefaultPowerUpSpawner` is the shared power-up visual bridge. It already
  constructs object instances under `ObjectConstructionContext` when services
  are available, then delegates final slot placement through `ObjectManager` or
  `ObjectLifetimeOps`.
- `DestructionEffects` is the shared badnik death bridge. It handles in-place
  explosion replacement, animal release, and points popups across game-specific
  factories.
- `AbstractBossInstance#spawnDefeatExplosion` is the shared boss defeat helper
  for subclasses that use the base defeat sequencer.
- `SkidDustObjectInstance.spawn` is a static convenience bridge from playable
  sprite movement into the object manager.

New object-owned child/projectile/debris spawns should not be added to these
exceptions. Prefer instance-owned `spawnChild` / `spawnFreeChild`, or
`ObjectManager.createDynamicObject` only for static factory paths that have an
active construction context but no object instance to call through.

## Palette Ownership Bridge Exceptions

Gameplay-time S3K level palette mutations should flow through
`PaletteOwnershipRegistry` using `S3kPaletteWriteSupport` or the shared
`PaletteWriteSupport` helper. Direct palette writes remain accepted only at
these boundaries:

- `S3kPaletteWriteSupport`, `PaletteWriteSupport`, `PaletteOwnershipRegistry`,
  and `Palette` are the abstraction boundary for applying ownership claims,
  fallback writes, and color decoding.
- `Sonic3kPaletteCycler.cacheFallbackPaletteTexture` is a null-registry
  fallback for standalone/test construction paths. Remove this exception when
  runtime-owned registries are always supplied.
- `AizIntroArtLoader` may cache standalone intro-art palette lines when no
  level/runtime services are available during bootstrap.
- `Sonic3kLevel` initial palette construction/upload is load-time
  initialization, not a runtime multi-writer gameplay mutation.
- `Sonic3kWaterDataProvider` builds returned underwater palette data; it should
  use loader-local helpers and palette accessors, not gameplay ownership claims.
- S3K data select, level select, and title screen are frontend/menu rendering
  contexts. Their palette uploads route through `S3kFrontendPaletteUploader`
  until a broader menu palette abstraction exists.
- S3K special stage owns its own palette/fade/rotation model outside normal
  gameplay level runtime. Manager-owned fade/rotation uploads now route through
  a special-stage-local palette uploader, and results-screen uploads use that
  same boundary; any remaining special-stage palette work should use local
  helpers before considering `PaletteOwnershipRegistry`.
- `Sonic3k` / `Sonic3kGameModule` standalone character and host-compatible
  palette construction is out of the gameplay palette ownership surface.

New S3K gameplay object, event, palette-cycle, boss, or badnik palette writes
should not be added to these exceptions. Route them through ownership claims so
normal and underwater surfaces compose consistently.

## Published Frozen Record Counts

`TestArchitecturalReviewGuard.archUnitPublishedBaselineCountsMatchFrozenStore`
checks these committed allowances through ArchUnit 1.4.2's
`TextFileBasedViolationStore.getViolations`. A record can span multiple escaped
physical lines. The lookup uses each rule's complete description, including its
`.because(...)` text. Historical `frozen baseline: N` labels remain part of those
keys; they are not the accounting source. These counts do not evaluate the live
production dependency graph. Update the matching documentation when committed
records intentionally change.

- `low_level_layers_do_not_depend_on_runtime_layers`: 184
- `shared_layers_do_not_depend_on_game_specific_packages`: 9
- `per_game_packages_do_not_cross_depend`: 3
- `runtime_owned_framework_packages_do_not_access_global_service_roots`: 11
- `runtime_registry_controllers_are_only_constructed_by_runtime_composition_roots`: 2

For cross-game slices, the same stored records contain **34 member
dependencies** (37 physical lines including headers); the decay target above
uses member dependencies. Low-level, framework-root, and registry-construction
labels still say 209, 13, and 4 respectively. Correcting their published counts
to 184, 11, and 2 records neither removes an allowance nor proves a live violation
was resolved by this accounting change.

## Package Cycle Ratchets

`package_slices_are_free_of_cycles`: 0 committed cycle records after the
explicit dependency-pair ignores below. This does not prove the full dependency
graph is acyclic; the rule description still names one historical core-runtime cluster.
`core_runtime_cycle_cluster_does_not_gain_top_level_edges`: 159 allowed
source/target top-level package pairs inside or adjacent to that cluster.
`TestArchitecturalReviewGuard` checks this permission count against the executable
set. It is not a newly measured count of live edges, and unused permissions are
not pruned by this accounting check.

- `cycle:core-runtime`: 16 top-level slices (`audio`, `camera`, `control`,
  `data`, `debug`, `editor`, `game`, `graphics`, `level`, `physics`, `sprites`,
  `testmode`, `timer`, `tools`, `trace`, `util`). Owner package: shared runtime
  architecture. Intended direction: split runtime service roots and tooling/debug
  edges out of gameplay/data/graphics ownership loops. Allowed directions are
  explicit source/target top-level package pairs in
  `CORE_RUNTIME_TOP_LEVEL_DEPENDENCY_EDGES`; new internal or adjacent edge pairs
  are not covered by a cluster-wide ignore. First decay target: remove at least
  one edge from that set when its dependency direction is eliminated.
- Phase 2 consciously adds `mods -> game` for creator `GamePatch` registration and
  the engine-owned `PatchOwner`/`RegisteredPatch`/`ModuleResolutionService` freeze,
  plus `mods -> level` solely for the canonical shared `ObjectFactory` contract.
  These are public mod-SDK/runtime-plan directions, not permission for
  game/level code to depend back on `mods`; decay them only if those contracts move
  to a genuinely lower shared API package without introducing parallel vocabulary.
- Mod API 0.7 consciously includes `mods -> control` because the creator-facing
  `GameplayInputFilter` consumes the canonical immutable `PlayerInputState` rather
  than publishing a parallel input model. The control package does not depend back
  on mods; decay this edge only if the canonical input value moves to a lower shared
  API package.

- Editor completion consciously adds `editor -> io` and `editor -> mods` for
  bounded baked-level import and destination-host export validation. The existing
  `ModAssetRoot`/`ModInputLimits` and strict parser remain authoritative; copying
  that parser into the editor would create a second safety contract.
  `editor_host_data_dependencies_use_only_explicit_owners` restricts the IO edge
  to `EditorProjectWorkspace` and the three asset-root/access/limit types. Its mod
  edge permits only that workspace's baked-level references, immutable definition
  values and `TrackKey`, strict parser, loader and staging validator;
  `EditorProjectRuntimeModule` may use the staging validator, and
  `FullLevelExporter` may use the strict metadata parser. Creator execution,
  registration and unrelated editor owners remain forbidden. These two adjacent
  edges do not change the frozen cycle count, cluster membership or cycle ignores.
  Stock host construction stays in `GameModuleRegistry`, an existing composition
  root; offline creation changes neither bootstrap nor active session ownership.
  Decay these edges only through a canonical lower shared baked-data boundary,
  rather than duplicating the loader or moving editor behavior into gameplay.

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

- None. The previous `AizIntroTerrainSwap` immediate-mutation bridge now routes
  through injected `ObjectServices`.

Target direction:

- Keep mutation/collision/sprite dependencies routed through injected object or
  manager collaborators.
- Keep `ObjectServices` as the object-instance dependency boundary.

### Shared Layers Must Not Depend On Game-Specific Packages

Rule: `shared level and game layers should not depend on game-specific packages`

Frozen violations 1–9 are in `MasterTitleRomPreview`, which composes the
cross-game master-title previews through concrete Sonic 1, Sonic 2, and Sonic
3&K title-screen mappings. The five former `DefaultPowerUpSpawner` entries
(Sonic 1 splash and S3K shield/insta-shield construction) were removed on
2026-08-28 when that creation moved behind `GameModule.getShieldFactory()`,
`getInstaShieldFactory()`, and `getWaterSplashFactory()`. The frozen set is now
9 entries under UUID `e0b8ef04-86e9-4001-b35e-c5de3ef4d940`.

Target direction:

- Keep bootstrap assembly behind explicitly annotated composition roots, and move
  game-specific visual object creation behind providers.

### Per-Game Packages Must Not Cross-Depend

Rule: `per-game packages should not cross-depend`

The 3 committed grouped records contain 34 member dependencies across these
categories (the live graph has not been remeasured here):

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

The 11 committed records fall into these categories:

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

The 2 committed records are:

- `Sonic2PaletteCycler` and `Sonic3kPaletteCycler` create fallback
  `PaletteOwnershipRegistry` instances for standalone/test paths when no
  runtime registry is supplied.

The historical `DefaultObjectServices` fallback-constructor allowances are no
longer present in this store. This is a store inventory, not a new live check.

Target direction:

- Require runtime-owned registries/controllers to be supplied by
  `GameplaySessionFactory` or `GameplayModeContext`.
- Keep test and legacy bootstrap paths on explicit stub/runtime collaborators
  instead of constructing ad hoc registries.

### Shared Code Must Not Construct Concrete Sonic Provider Classes

Rule: `shared code should not construct concrete Sonic provider/art/object classes`

Frozen violations: none. The former `DefaultPowerUpSpawner` Sonic 1 splash
construction now comes from `Sonic1GameModule.getWaterSplashFactory()`
(baseline reduced to 0 on 2026-08-28).

Target direction:

- Keep game-specific visual object creation behind `GameModule` provider
  contracts or a dedicated composition root.
  Keep shared object helpers independent of concrete Sonic object classes.

### Mod API Compatibility Metadata And Release Tooling

Two count-neutral dependency exceptions support the compiled-mod compatibility
contract without redefining runtime ownership:

- Dependencies whose target is exactly `com.openggf.game.ModApi` are ignored by
  the top-level cycle, low-level-layer, and network-isolation rules. The type-only annotation is
  compatibility metadata; it does not let the annotated class call into the game
  runtime.
- Exactly `ModApiJavadocTool` and `ModApiSdkPackager` may depend on
  `ModApiSurfaceInventory`. These package-phase release tools must consume the
  canonical inventory to produce exact Javadoc and SDK classifier jars; no other
  class in `com.openggf.tools.modsdk` inherits the exception.

Neither exception raises a frozen violation count or adds a ratchet edge. The
exceptions should decay if the marker moves to an architecture-neutral metadata
module or Maven packaging can consume the canonical inventory without a
production-class dependency. They must not be broadened to packages, arbitrary
annotations, or additional tooling classes.

## Maintenance

When a frozen violation is removed:

1. Run the affected rules through the queued Maven wrapper with normal store
   updates disabled, for example
   `python3 tools/testing/maven_queue.py -Dmse=off -Pguards "-Dtest=TestArchUnitRules,TestArchUnitTestRules,TestArchitecturalReviewGuard" test -B`.
2. If a baseline update is separately authorized, remove only demonstrated
   resolved records in that reviewed change; do not routinely refreeze.
3. Review full records, including multiline groups, and preserve complete rule
   description keys. Keep `freeze.store.default.allowStoreUpdate=false` for
   normal runs.
4. Update record counts, member-dependency counts, and affected exception prose
   in this document. Keep committed allowances separate from measured live
   violations and package-pair permissions.
