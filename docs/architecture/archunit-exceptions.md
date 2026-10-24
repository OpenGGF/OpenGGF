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
- With ArchUnit 1.4.2, solved frozen records are strict too: evaluation requests a
  store save when violations disappear, and disabled updates reject that save.
  Prune verified resolved identities explicitly. Lower totals do not authorize
  replacing them with different callers or members.

## Decay Targets

Targets are tied to specific migrations, not calendar-bound. Counts below are
committed allowances read from the freeze store, not newly measured live
violations. When a target is hit, review the resolved records in a dedicated
baseline-update commit and pick the next target rather than weakening the rule.

| Rule | Committed allowance and unit | Target in that unit | Trigger |
|------|------------------------------|---------------------|---------|
| `low_level_layers_do_not_depend_on_runtime_layers` | 151 records | <=150 records | AudioManager/GraphicsManager runtime callbacks migrate off direct level/sprite imports |
| `shared_layers_do_not_depend_on_game_specific_packages` | 0 records | Hold at 0 records | Master-title ROM preview decoding/composition now belongs to game-owned providers (`DefaultPowerUpSpawner` visual object creation moved behind `GameModule` factories, 2026-08-28) |
| `per_game_packages_do_not_cross_depend` | 10 member dependencies in 2 grouped slice records | Hold at 10 explicit host-donation members | Menu animation now has a shared owner; remove donation members only through a separate host/donor contract migration |

2026-09-03: the two `AudioManager -> Rom.readBytes` SEGA-boot PCM edges were
solved and the frozen entry pruned. `GameAudioProfile.loadSegaPcm(Object)` now
declares the read and the per-game profiles perform it through
`com.openggf.game.audio.SegaPcmRomReader`, which lives in the runtime layer and
may depend on `com.openggf.data`. The rule description keeps its original
baseline wording because the freeze store is keyed by that full text.

2026-10-22: private audio-source typing replaces the null `AudioManager.rom`
sentinel allowance with the actual `BaseAudioSource` ROM field, constructor
parameter and accessor return type, plus the private same-source helper's ROM
parameter. This reviewed identity migration changes 184 committed records to
187; it does not demonstrate semantic debt growth or removal. The immutable
tuple remains the single volatile publication, and the public
`GameAudioProfile.loadSegaPcm(Object)` interface and game-owned ROM reads are
unchanged.

The historical cross-game count of 37 counted physical lines: 3 slice headers
plus 34 member dependencies. The initial target used <=20 **member dependencies**.
Shared selected-slot conversion, its result type, and common payload validation
moved to `game.dataselect` on 2026-10-22, removing 20 member dependencies and
the entire S2-to-S1 group.
After that extraction the allowance was 14 members in 2 groups. Neither physical lines nor
grouped records can be substituted for member dependencies when evaluating
this migration. On 2026-10-23, moving menu animation to `game.menu` removed the four
animator members, meeting the target of 10 while preserving explicit host-ROM donation.

## Active Migration Queue

Keep this queue small. Close one ownership boundary, prove its behavior, then
prune its exact allowances; a lower aggregate alone is not completion. The
[2026-10-23 plan](plans/2026-10-23-architecture-ratchet-paydown.md) records the
current campaign and delivery evidence. Earlier campaigns already removed
cross-game preview members and runtime-installed framework lookups; this work
continues those cuts rather than treating the inventory as untouched.

| Slice / owner | Expected change | Completion evidence | Status |
|---|---|---|---|
| Menu and AniPLC composition / `game.menu`, S2 animation assembly | Cross-game 14 → 10 members; framework roots 1 → 0 records | Menu cadence/prime controls, post-install graphics rebinding, ROM animator adapter, strict frozen guards | Completed; affected checks and strict frozen rules passed |
| Canonical sprite geometry / `graphics`, legacy `level.render` ingress | Remove `SpriteSatEntry`'s 20 reverse records and replay callbacks; retain and explicitly relocate the pinned submission conversion | Complete field/geometry controls, SAT order/mask/ghost/replay checks, identical public API pins and audited member delta | Completed; low-level records 187 → 181; API and actual SAT pixel checks passed |
| Master-title preview / `GameModule` preview capabilities | Shared concrete-game 9 → 0 records | S1/S2/S3K pixel/token fingerprints, detached ROM views, card-owned selection, source and strict frozen guards | Completed; focused behavior/API checks and strict frozen rules passed; see the [preview plan](plans/2026-10-23-title-preview-providers.md) |
| Title-card sprite rendering / `game.titlecard` | Remove the helper's 14 low-level reverse dependencies | Exact tile submissions, asymmetric flipped-art pixels and manager draw order at 320/424 pixels, unchanged API pins and strict frozen rules | Completed; low-level records 181 → 167; focused behavior/API checks and strict frozen rules passed |
| Standalone audio composition / `tools.audio`, reusable presentation host | Remove the desktop app's eleven low-level records and the host's app callback | Parser/catalog characterization, actual supplied-profile use, independent host state, WAV export, ROM hub themes, launcher and strict guards | Completed; low-level records 167 → 156; behavior, CLI, API and complete guards passed; see the [second-loop plan](plans/2026-10-24-architecture-loop-two.md) |
| Background renderer lifetime / level rendering composition | Evaluate the five `GraphicsManager` → `BackgroundRenderer` records | Ground construction, initialization, per-level cleanup and API compatibility before choosing a cut | Investigation complete; retain the graphics/context owner and all five records. Native resource checks passed; see the [follow-through plan](plans/2026-10-24-architecture-followthrough.md) |

The October 23 combined guard run recorded one independently reproduced
pre-existing build-tooling assertion failure; that
[plan](plans/2026-10-23-architecture-ratchet-paydown.md) preserves its exact
identity, matched baseline/current command and coverage limits. That historical
result is not a claim about the current guard profile. The October 24
[follow-through plan](plans/2026-10-24-architecture-followthrough.md) records the
new validation separately.

Lifecycle calls are a separate migration inventory, not hundreds of automatically
safe replacements. Preserve the intentional slot/reconstruction bridges and move
callers only with focused lifecycle behavior evidence. Ambient test setup and
remaining low-level audio/data allowances stay outside this campaign.

2026-10-23: the subsequent [title-preview migration](plans/2026-10-23-title-preview-providers.md)
removed all nine shared-to-concrete mapping calls (S1: two, S2: one, S3K: six).
ROM offsets, palettes, animation and composition moved with them into per-game
providers. The source guard also rejects concrete game imports/entry switches in
shared preview code, including constants invisible to bytecode dependency counts.
The composition root resolves detached stock providers directly, avoiding live
module construction and active/suspended gameplay or donor selection. The rule's
historical `frozen baseline: 9` description remains its unchanged store key.

## Source Ratchets

Some review findings are source-shape constraints rather than ArchUnit bytecode
rules. These live in `TestArchitecturalSourceGuard`. The figures below are executable budgets. Class/reference checks enforce
maximums; dispatcher checks enforce exact current spans. Constants do not update
automatically: lower them explicitly when publishing a tighter budget.
Dispatcher budgets require exact current spans, so extraction fails until the
obsolete budget is lowered. Method spans come from the JDK parser, including
annotations and multiline declarations; comments, strings and text blocks cannot
truncate a body. The substantial `displayFrame`/`stepInternalBody` owners are
budgeted alongside their profiling wrappers. `ObjectManager`'s effective-source
cap is 3073, down from 3086. The other large-class caps are 2045
(`Sonic1ObjectArtProvider`), 3257 (`AbstractPlayableSprite`), 3142 (`LevelManager`)
and 3305 (`GameLoop`). The only allowed >=100-line `Engine` method owners are
`Engine#init()` and `Engine#displayFrame()`. Overloads and nested owners cannot
share these identities, and ambiguous dispatcher names fail. Exchanging one
large method for another no longer
fits an aggregate slot. No gameplay behavior changed to obtain these reductions.

| Rule | Enforced budget | Target | Trigger |
|------|----------|--------|---------|
| Root `Engine` / `GameLoop` concrete Sonic references | `Engine.java`: 0, `GameLoop.java`: 0 | Hold at 0 | Registry metadata and bonus-stage capabilities own table selection, bootstrap repair and debug behavior |
| `ObjectManager` concrete Sonic references | 0 | 0 | Rewind dynamic-object recreation moves to game/module-registered codecs and factories |
| Low-level graphics/audio gameplay `GameServices` lookups | 0 | 0 | Camera/fade state is passed through render orchestration or explicit context objects |
| Root dispatcher method sizes | `Engine.draw`: 3, `Engine.init`: 170, `Engine.display`: 4, `Engine.displayFrame`: 229, `GameLoop.stepInternal`: 58, `GameLoop.stepInternalBody`: 277, `GameLoop.doExitBonusStage`: 113, `GameLoop.updateSpecialStageInput`: 9, `GameLoop.loadEndingDemoZone`: 84, `GameLoop.enterTitleCardFromResults`: 62, `GameLoop.enterBonusStage`: 45 | Decrease each touched method | Mode/render responsibilities extract into focused controllers without growing the root dispatchers |
| Raw child-spawn patterns in `OBJECT_CHILD_SPAWN_MIGRATED_FILES` | 0 matches per listed file | 0 | Migrated object-owned child creation stays on `spawnChild(...)` / `spawnFreeChild(...)` helpers |

`TestObjectPhysicsStandardizationGuard` now compares parsed lifecycle calls with
`architecture/object-lifecycle-call-baseline.txt`: 582 `setDestroyed(true)` calls
in 503 caller identities and 10 dynamic-add calls in 10 identities. Each key
retains file, lexical type/member signature and operation, with its exact count
and migration/bridge reason. Both growth and stale allowances fail. Local and
anonymous declarations retain per-caller lexical ordinals, so moving a call to
another declaration cannot spend removed debt; formatting does not change keys.
This inventory covers the guarded object packages, not all application calls.
The older figures 585/136 counted a different historical source scope.

Event-source permissions now pin MGZ's six remaining calls by caller, operation
and multiplicity. Three obsolete AIZ/HCZ file exemptions are gone. Object-service
source permissions pin eleven actual caller/operation identities; the previous
ten fragment entries covered two distinct registry callers with one entry.
Missing or malformed scan inputs, changed counts and stale permissions fail.
Shared event composition owners retain their explicit whole-file exclusions.
Invocation-shaped singleton/fallback patterns also recognize method references
as distinct operations, so an approved direct call cannot authorize one.

The ambient singleton-setup inventory now contains 98 methods across 97 files,
down from 102/100 after direct manager construction and named module fixtures
replaced four methods in three test classes. These count setup obligations,
not demonstrated leaks. Removing four unused GameLoop imports first tightened
stale accounting. The later registry metadata and bonus-stage capability migration
removes the remaining root coupling; both concrete-reference caps are now zero.
Sonic 1 music parsing now lives beside its game-owned loader, with its class body
unchanged and the sole naming allowance removed. The naming store is empty.

The rewind field-disposition census first pruned 41 already-resolved exemptions
(64 → 23). The October 24 [field audit](audits/2026-10-24-rewind-disposition-review.md)
records eight proven reconstruction policies, two captured fragment-art recipes,
the ride ship's captured live inventory and codec-compatible ICZ helper state.
Owner-bound LBZ panels and ordered wall-controller snapshots preserve progressed
values and current shared RNG binding. Two unused AIZ histories are removed;
exact captured actor links preserve finale triggering and emerald collection.
Seven exact obligations remain, including the arbitrary S1 parent-ring recipe
contract; stock placed recipes have bounded destructive-replay evidence. False-floor surviving children dispatch by
their restored original index, preserving the native collapse counter. The
guard fails stale keys as well as new gaps. The ICZ segment-column recreation
exclusion is gone. The justified recreate-throw baseline remains 13 files / 14
occurrences. Initialized provider registration, restore and retirement checks
establish the S2/S3K special-stage adapter coverage; static-state coverage now
retains only the module-selection exclusion. These classification changes do not
implement missing capture state for the remaining obligations.

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

- `low_level_layers_do_not_depend_on_runtime_layers`: 151
- `shared_layers_do_not_depend_on_game_specific_packages`: 0
- `per_game_packages_do_not_cross_depend`: 2
- `runtime_owned_framework_packages_do_not_access_global_service_roots`: 0
- `runtime_registry_controllers_are_only_constructed_by_runtime_composition_roots`: 2

For cross-game slices, the same stored records contain **10 member
dependencies** (12 physical lines including headers); the decay target above
uses member dependencies. Low-level, framework-root, and registry-construction
labels still say 209, 13, and 4 respectively. Correcting their published counts
to 184, 11, and 2 records neither removed an allowance nor proved a live violation
was resolved by that accounting change. The later private audio-source identity
migration above raised the allowance to 187. The canonical sprite ownership
cut on 2026-10-23 removed 23 member identities and added exactly 17 accessor
identities at the unchanged public submission boundary, leaving 181 records.
The retained compatibility dependency is explicit: `GraphicsManager` accepts
`SpritePieceRenderer.PreparedPiece` and copies it once into `graphics.SpritePiece`.
SAT storage and replay use the graphics value and its single expansion owner.
This is a reviewed identity relocation, not a prune-only update or complete
isolation of graphics from runtime packages.

2026-10-24: `TitleCardSpriteRenderer` moves intact from `graphics` to
`game.titlecard`, beside the mapping value it consumes. All three managers keep
their existing calls and draw order. The move removes exactly 14 low-level
records from `40afa018-d450-4b84-8cb9-d539d75d8062`, reducing 181 to 167 without
replacement allowances or a forwarding class. Column-major title-card tile
submission remains separate from the graphics-owned sprite expansion contract.
The public static entry point keeps its parameter and return types.
The helper has no `@ModApi` marker or curated root, and no recursive candidate
signature names it. The release descriptor has no published API baselines.

2026-10-24, second loop: `SoundTestApp` moves intact from `audio.debug` to
`tools.audio`. Its selected profile is passed to the reusable presentation host,
as is the hub's existing detached profile. The host no longer calls the desktop
application, and its execution/device/cleanup ownership is unchanged. Exactly
eleven app records are removed, reducing 167 to 156 without replacement entries.
The old internal overloads are deleted with their callers migrated. A direct
dependency rule rejects non-tool references to the app and its nested classes;
the sole AWT exception follows the exact new file. The now-unused `audio.debug`
concrete-provider construction exemption is removed. This is not a claim of
complete transitive native-image closure.

The next graphics slice shares underwater eligibility, fixed-point channel ratios
and saturation through a package-private scalar policy. Legacy Palette sampling,
live appearance traversal and staged RGB reduction retain their respective owners.
Exactly nine legacy field-read records are replaced by four private RGB-adapter
records: 156 → 151. Eight legacy typed/materialization/getColor records and all
139 other records remain. This consolidates numerical policy without removing
the supported legacy Palette boundary or changing public signatures. The target
remains <=150; no behavior or permission is changed to reach it. See the
[continuation evidence](plans/2026-10-24-architecture-continuation.md).

The installed-owner rewind change removed 10 framework-root records, leaving
one. Injecting execution-time graphics selection into `ScriptFramesApplyStrategy`
removed that last record. The S2 composition supplies `GameServices::graphics`,
so rebinding after installation remains visible; `ChannelContext` and API pins
are unchanged.

## Package Cycle Ratchets

`package_slices_are_free_of_cycles`: 0 committed cycle records after the
explicit dependency-pair ignores below. This does not prove the full dependency
graph is acyclic; the rule description still names one historical core-runtime cluster.
`core_runtime_cycle_cluster_does_not_gain_top_level_edges`: 154 allowed
source/target top-level package pairs inside or adjacent to that cluster.
`TestArchitecturalReviewGuard` checks this permission count against the executable
set. The live dependency census on 2026-10-23 found seven unused permissions:
`debug -> data`, `sprites -> ghost`, `stageplay -> configuration`,
`timer -> camera`, `tools -> ghost`, `tools -> net`, and `util -> tools`.
These were pruned from 159 to 152; editor art import adds the narrowly owned
`editor -> tools` pair (also used by Stageplay screenplay import/export, owned by
`editor_host_data_dependencies_use_only_explicit_owners`), and Stageplay world presentation
adds `stageplay -> util`, giving the merged tree 154 permissions. The three matching obsolete
cycle ignores (`debug -> data`, `timer -> camera`, `util -> tools`) were removed too. The runtime-edge guard now rejects both
unapproved edges and stale permissions; the documentation check verifies the
published allowance count.

- `cycle:core-runtime`: 16 top-level slices (`audio`, `camera`, `control`,
  `data`, `debug`, `editor`, `game`, `graphics`, `level`, `physics`, `sprites`,
  `testmode`, `timer`, `tools`, `trace`, `util`). Owner package: shared runtime
  architecture. Intended direction: split runtime service roots and tooling/debug
  edges out of gameplay/data/graphics ownership loops. Allowed directions are
  explicit source/target top-level package pairs in
  `CORE_RUNTIME_TOP_LEVEL_DEPENDENCY_EDGES`; new internal or adjacent edge pairs
  are not covered by a cluster-wide ignore. The first decay target is met by the seven measured unused permissions above;
  future removals require a live dependency census too.
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
  `editor_host_data_dependencies_use_only_explicit_owners` restricts these
  dependencies to exact named bridge owners and targets. `EditorProjectWorkspace`
  retains the three asset-root/access/limit types, baked-level references,
  immutable definition values and `TrackKey`, strict parser, loader and staging
  validator; `EditorProjectRuntimeModule` may use the staging validator and
  `AuthoredStageplayServices/Prepared` for bounded playtest composition, and
  `FullLevelExporter` may use the strict metadata parser. The art import owner
  `EditorArtImport` and scene-resource export owner `EditorCampaignResources`
  may use only the same three bounded IO contracts. `EditorCampaign` uses the
  normalized path contract and canonical campaign limit. `EditorCampaignExportService`
  uses only the input budget, API/version values, campaign limit and staging
  validator. Nested records/callbacks inherit their named owner's exact target set.
  Creator execution, registration and unrelated editor owners remain forbidden.
  These IO/data edges do not change the frozen cycle count or cluster membership.
  Stock host construction stays in `GameModuleRegistry`, an existing composition
  root; offline creation changes neither bootstrap nor active session ownership.
  Decay these data edges only through a canonical lower shared baked-data boundary,
  rather than duplicating the loader or moving editor behavior into gameplay.
- Editor authoring also adds `editor -> tools` solely to reuse
  `EditorArtImport -> ArtConverter/Result` and
  `EditorCampaignExportService -> JarPackager`. The same data owner guard rejects
  any other editor/tool pairing, preserving one canonical conversion and
  no-clobber publication path rather than copying those implementations.
- Authored scene registration deliberately composes the existing public
  `StageplayPlayback` and `StageplayControl` contracts through
  `mods.code.AuthoredStageplayServices` and its nested router. The cycle rule
  ignores only this exact owner to those public targets; no complete package edge
  or frozen cycle member is added. `stageplay_boundaries_have_explicit_owners`
  rejects this adapter's dependencies on Stageplay runtime implementation types,
  and rejects all other mod owners entering the player. This is an intentional
  registration/control callback pair with Stageplay's existing mod registration
  dependency, not a new legacy cycle cluster.
  `EditorCampaignResources` alone may read `StageplayDocument` values,
  `StageplayStore` and `runtime.StageplayAssets` to copy declared dependencies
  through the bounded IO loader; it cannot enter playback. Decay these bridges
  only if the canonical public registration or dependency-manifest contract moves
  to a lower shared owner.

If a temporary package-cycle cluster is accepted later, document it as
``cycle:<name>`` with current count, owner package, intended direction, and first
decay target. Add a matching `@ArchTest` method or frozen-store entry in the same
commit so `TestArchitecturalReviewGuard.archUnitCycleClusterDocumentationHasMatchingRatchets`
can enforce the ratchet.

## Frozen Rules

### Object Packages Must Not Access Global GameServices

Rule: `object packages should not access global GameServices except approved bridges`

Allowed bridge classes are excluded directly in the ArchUnit rule:

- `com.openggf.level.objects.AbstractObjectInstance`
- `com.openggf.level.objects.DefaultObjectServices`
- `com.openggf.level.objects.BootstrapObjectServices`
- `com.openggf.level.objects.AbstractObjectRegistry`
- `com.openggf.game.sonic1.objects.Sonic1ObjectRegistry`
- `com.openggf.game.sonic2.objects.Sonic2ObjectRegistry`
- `com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry`

These are exact fully qualified class exclusions. Other registry names do not
inherit an exception.

Frozen violations:

- None. The previous `AizIntroTerrainSwap` immediate-mutation bridge now routes
  through injected `ObjectServices`.

Target direction:

- Keep mutation/collision/sprite dependencies routed through injected object or
  manager collaborators.
- Keep `ObjectServices` as the object-instance dependency boundary.

### Shared Layers Must Not Depend On Game-Specific Packages

Rule: `shared level and game layers should not depend on game-specific packages`

No committed records remain under UUID
`e0b8ef04-86e9-4001-b35e-c5de3ef4d940`. The title-preview provider migration on
2026-10-23 removed all nine `MasterTitleRomPreview` mapping dependencies. Per-game
providers now own the concrete Sonic 1, Sonic 2, and Sonic 3&K preview decoding
and composition. The five former `DefaultPowerUpSpawner` entries
(Sonic 1 splash and S3K shield/insta-shield construction) were removed on
2026-08-28 when that creation moved behind `GameModule.getShieldFactory()`,
`getInstaShieldFactory()`, and `getWaterSplashFactory()`. The rule description's
historical `frozen baseline: 9` remains part of its unchanged store key.

Target direction:

- Keep bootstrap assembly behind explicitly annotated composition roots, and move
  game-specific visual object creation behind providers.

### Per-Game Packages Must Not Cross-Depend

Rule: `per-game packages should not cross-depend`

The 2 committed grouped records contain 10 member dependencies for explicit
host donation:

- S3K host-preview presentation uses the S1/S2 cached preview managers and S1
  special-stage palette extraction for explicit host ROM donation.

The selected-slot converter, `LoadedPreview` result, and common payload validator
now belong to `com.openggf.game.dataselect`. Both host paths consume that converter
directly; the pass-through S2 preview loader was deleted. This removes 7 S2-to-S1,
11 S3K-to-S1, and 2 S3K-to-S2 member dependencies without adding replacement
cross-game allowances. ROM title previews remain outside this migration.

Target direction:

- Menu animation now belongs to shared `game.menu`, closing the four former
  S3K-to-S2 animator members without replacement cross-game dependencies.
- Keep cross-game donation explicit when reuse is intentional and ROM-accurate
  host presentation depends on older-game assets.

### Runtime-Owned Frameworks Must Not Access Global Service Roots

Rule: `runtime-owned framework packages should not access GameServices, SessionManager, or EngineServices directly`

No committed records remain. `ScriptFramesApplyStrategy` resolves an injected
graphics supplier at apply time, with S2 composition selecting the current runtime
manager. Live rewind binds its installed gameplay owner, registered
special-stage provider and fade explicitly, with engine-owned audio injected.
The 10 former manager/stepper root-access records were removed rather than
relocated.

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
