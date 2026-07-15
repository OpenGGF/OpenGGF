# Mod API compatibility surface

OpenGGF's compiled-mod contract is marked by the runtime-visible, type-only
`com.openggf.game.ModApi` annotation. The supported inventory is not limited to
the initially curated roots: every engine type reachable through their public or
protected constructors, methods, fields, generic bounds, annotations, nested
types, supertypes, interfaces, record components, and sealed permits clauses is
part of the same contract and must also be annotated.

The published version is Mod API 2.3.0. Its recursive surface spans many hundreds
of engine types and is pinned exactly by `mod-api-signatures-2.3.txt` (the
`TestModApiSignatureSurface` guard is the authoritative count). The surface has a
single reconciled lineage, 1.1.0 -> 1.2.0 -> 2.0.0 -> 2.1.0 -> 2.2.0 -> 2.3.0:
1.1.0 is the
original closed baseline; 1.2.0 was an additive minor bump (its frozen historical
baseline `mod-api-signatures-1.2.txt` contains **875 engine types** and **17,178
canonical signature entries**, a strict superset of 1.1.0); 2.0.0 was a deliberate
breaking bump published on top of 1.2.0 (now itself a closed historical baseline,
`mod-api-signatures-2.0.txt`, **873 engine types** and **17,165 canonical signature
entries**); 2.1.0 was an additive minor bump published on top of 2.0.0 (now itself
a closed historical baseline, `mod-api-signatures-2.1.txt`, **875 engine types**
and **17,196 canonical signature entries**); 2.2.0 was an additive minor bump on
top of 2.1.0 (now a closed historical baseline, **876 engine types** and **17,205
canonical signature entries**); and 2.3.0 is the current additive minor bump on
top of 2.2.0 (**885 engine types** and **17,323 canonical signature entries**).
The breadth is intentional. In particular, the legacy-wide
signatures of `GameModule`, `ObjectServices`, and the object base classes expose
substantial runtime infrastructure; silently treating those transitive types as
unsupported would make creator binaries depend on an undocumented, unstable ABI.

The Phase 2 zone seam replaces the fixed `LevelData` enum in creator-facing
signatures with `LevelDescriptor`. `LevelData` remains the stock implementation
and every enum constant delegates its unchanged index and start coordinates
through that interface; the enum itself is no longer part of the creator ABI.
This replacement was made while 1.1 remained unpublished, before the final
Phase 2 baseline freeze.

The 1.1 surface also includes the additive loader-aware rewind contract:
`DynamicObjectEntry.ownerModId` identifies the compiled-mod loader that owns a
captured dynamic class, while `RewindClassResolver` lets the engine preserve
that ownership across recreation. Legacy `DynamicObjectEntry` constructors are
retained and produce ownerless engine entries, so existing 1.1 binaries remain
source- and binary-compatible.

The published baseline is
`src/test/resources/mods/mod-api-signatures-2.3.txt`, pinned exactly to the
current canonical surface by `TestModApiSignatureSurface`. The prior
`mod-api-signatures-1.1.txt` (831 engine types, 16,483 canonical entries),
`mod-api-signatures-1.2.txt` (875 engine types, 17,178 canonical entries),
`mod-api-signatures-2.0.txt` (873 engine types, 17,165 canonical entries), and
`mod-api-signatures-2.1.txt` (875 engine types, 17,196 canonical entries), and
`mod-api-signatures-2.2.txt` (876 engine types, 17,205 canonical entries) are
retained as closed historical records: 1.1 is the original contract, 1.2 is its
additive successor, 2.0 is the deliberate breaking bump, and 2.1/2.2 are additive
steps that 2.3 extends. `TestModApiSignatureSurface` verifies the full lineage —
1.1 -> 1.2 is asserted additive (1.2 is a strict superset of 1.1), 1.2 -> 2.0 is
asserted to be a declared breaking transition (see the 2.0.0 breaking-transition
section below), 2.0 -> 2.1 is asserted additive (2.1 is a strict superset of 2.0;
see the 2.1.0 additive-bump section below), and 2.1 -> 2.2 is asserted additive
(2.2 is a strict superset of 2.1; see the 2.2.0 additive-bump section below), and
2.2 -> 2.3 is asserted additive (2.3 is a strict superset of 2.2; see the 2.3.0
additive-bump section below) — so
each step's changes are never silently absorbed into an undocumented jump. The
guard requires the published baseline to remain a subset of the current canonical
surface, so removals and changes fail while reviewed compatible additions can be
published with an appropriate semantic API version increase. Before updating the
published baseline:

1. run `TestModApiSignatureSurface` and inspect every added line;
2. annotate every newly reachable engine type;
3. narrow any third-party signature to a JDK or engine-owned contract instead of
   allowlisting the dependency;
4. add a JDK type only to the explicit platform allowlist after compatibility
   review; package-prefix exemptions are forbidden;
5. regenerate the sorted LF baseline with the dependency-complete snapshot command
   below and re-run the Javadoc/SDK packaging tests.

On PowerShell, `ModApiSignatureSurface --snapshot` requires both the compiled engine
classes and its ASM dependency. Use this exact reproducible sequence (the shorter
`java -cp target/classes ...` form is insufficient and fails with
`NoClassDefFoundError`):

```powershell
mvn "-DskipTests" compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/mod-api-snapshot-classpath.txt"
$cp = "target/classes;$((Get-Content target/mod-api-snapshot-classpath.txt -Raw).Trim())"
java -cp $cp com.openggf.mods.code.ModApiSignatureSurface --snapshot |
    Set-Content -Encoding utf8NoBOM src/test/resources/mods/mod-api-signatures-2.3.txt
```

The 1.2 roots add the character and standalone creator path: owner-tagged
`CharacterKey`/`CharacterDefinition` registration and playable construction, plus
`GameDataSource`, `AbstractStandaloneGameModule`, `ModGame`, and
`StandaloneLevelLoader`. It also publishes `DelegatingGameModule`, the forwarding
decorator used by creator patches that wrap a stock module, plus `GroundSensor` and
`AbstractLevelInitProfile` for the prescribed standalone character and level-lifecycle
implementation. These are additive to the 1.1 object/zone surface; the old baseline is
not overwritten or renamed.

Release packaging generates exact-inventory Javadoc and attaches
`openggf-mod-sdk` and `openggf-mod-sdk-javadoc` classifier jars beside the
engine artifact. Architecture guards ignore only the `@ModApi` marker edge and
the release tool's exact inventory lookup; the annotation does not establish a
runtime ownership dependency.

New creator APIs should use narrow engine-owned facades and value types so this
closure decays rather than expands. Existing supported signatures cannot be
removed, narrowed, or unannotated merely to reduce the inventory; that requires
a deliberate breaking-version transition and migration guidance.

## Mod API 2.0.0 breaking transition

At publication, `ModApiVersion.CURRENT` became `2.0.0`. This is a deliberate major-version break from
`1.2.0` (the additive successor of `1.1.0`): the rewind-reference-closure and
per-game rules work removed and changed signatures that were frozen in the 1.2
surface — and, because 1.2 is a strict superset of 1.1, in the 1.1 surface as
well. Per semver, removed/changed public signatures are a major-version break, so
the published API moved to a new major rather than silently re-baselining. The
1.1 and 1.2 baselines are preserved as historical records;
`TestModApiSignatureSurface` asserts that 1.1 -> 1.2 is additive and that
1.2 -> 2.0 is a declared breaking transition (the frozen 1.2 lines are no longer a
subset of the current surface) and pins the current surface to
`mod-api-signatures-2.0.txt`.

Because 2.0 breaks compatibility, mods that declared an engine range under
`<2.0.0` no longer load on this engine (`ModJarValidator` /
`EffectiveCatalogBuilder` reject them as `ENGINE_API_INCOMPATIBLE`). A mod
targeting this engine must declare a `2.x` range, e.g. `>=2.0.0 <3.0.0`; the SDK
template and sample mods now do so.

Signatures removed or changed between 1.1 and 2.0 (the reason for the major bump):

- **Rewind-state closure consolidation.** `PerObjectRewindSnapshot$PlayerRewindExtra`
  no longer nests the four `*$RewindState` sub-records
  (`PlayableSpriteMovement$RewindState`, `SpindashDustController$RewindState`,
  `PlayableSpriteAnimation$RewindState`, `DrowningController$RewindState`) as
  components/accessors, and its canonical constructor changed accordingly.
  `PerObjectRewindSnapshot$SidekickCpuRewindExtra` dropped the
  `carryParentagePending`, `flyingCarryingFlag`, `releaseCooldown`, `carryLatchX`,
  and `carryLatchY` components. These are engine rewind-serialization internals
  reachable only transitively through `AbstractObjectInstance.captureRewindState()`
  / `restoreRewindState()`; mods override those hooks but should treat the
  snapshot as an opaque engine token rather than destructuring its extras.
- **Per-game rules records.** The canonical constructors of `CollisionRules`,
  `PlayerCapabilityRules`, `RingRules`, `SidekickCpuRules`, and
  `WaterSystemSnapshot$DynamicWaterEntry` changed as components were added.
  Records in the audited surface are constructor-frozen, so any added component
  is a breaking constructor-signature change.
- **`SpriteManager.drawUnifiedBucketWithPriority`.** The
  `(int, GraphicsManager, Runnable, Runnable)` overload was removed in favor of
  the `(int, GraphicsManager)` form.

## Mod API 2.1.0 additive bump

At publication, `ModApiVersion.CURRENT` became `2.1.0`. This is a same-major additive minor bump
above `2.0.0`: it publishes the ROM-art intake surface that lets Sonic 2 patch
mods stage object art materialized from the user's ROM at gameplay launch
instead of shipping baked art assets.

Added signatures:

- **`ModContext.registerRomObjectArt(String, RomArtRequest)`.** Stages a ROM-art
  request under an owner-namespaced key, served through the same overlay as
  `registerObjectArt`. Only available to Sonic 2 patch mods (`baseGame` `"s2"`);
  standalone modules and other base games are rejected at registration time.
- **`RomArtRequest`** (record) — the staged request: ROM art/mapping/DPLC
  addresses, `RomArtCompression`, palette line, and bank size. Registration
  validates static address bounds with no ROM open; the real ROM is read only
  during gameplay-launch materialization, and ROM-derived bytes are never
  persisted to disk.
- **`RomArtCompression`** (enum) — `NEMESIS`, `KOSINSKI`, `UNCOMPRESSED`.

`ModRegistrationPlan` gained a `romObjectArt` component and a new 12-component
canonical constructor to carry staged ROM-art requests through the freeze/apply
pipeline; the pre-existing 11-component constructor is preserved as a
compatibility overload, matching the pattern used by the earlier standalone and
Phase-2 canonical-shape additions. `ModRegistrationPlan` is not itself part of
the published `@ModApi` surface (it is returned only from the package-private
`ModContext.freeze()`), so this constructor addition does not appear in the
signature diff between 2.0 and 2.1; the diff is limited to the three items
above. The engine-internal `RomArtMaterializer` (real ROM decompression/parsing)
and `ModBackedGamePatch.RomArtSheetSource` remain package-private and are not
part of the creator ABI.

No existing 2.0 signature was removed, narrowed, or changed. `mod-api-signatures-2.0.txt`
is retained as a closed historical baseline; `mod-api-signatures-2.1.txt` is now
also a closed historical baseline, extended by 2.2.

## Mod API 2.2.0 additive bump

At publication, `ModApiVersion.CURRENT` became `2.2.0`. This is a same-major additive minor bump
above `2.1.0`: it publishes the playable-subclass rewind hooks that let a
compiled-mod playable character (Phase 3 owner-tagged character) capture and
restore its own rewind-relevant state alongside the base `PlayerRewindExtra`
surface, so rewind seek/scrub stays coherent for mod-defined characters instead
of silently dropping their subclass-local fields.

Added signatures:

- **`PerObjectRewindSnapshot$PlayerRewindExtra.PlayableSubclassRewindExtra`**
  (nested marker interface) — an empty, `@ModApi`-annotated marker that a mod's
  immutable subclass-rewind payload (typically a `record`) implements. The
  payload is stored as-is in the snapshot's in-memory object graph with no
  serialization step and no defensive copy, so implementations must be
  immutable and must never alias mutable live sprite/controller state.
- **`PerObjectRewindSnapshot$PlayerRewindExtra.subclassExtra()`** — the new
  trailing record component/accessor carrying the captured
  `PlayableSubclassRewindExtra` payload, or `null` when the owning snapshot
  carries no subclass state.
- **`AbstractPlayableSprite.captureSubclassRewindState()`** (protected hook) —
  called on every keyframe capture; the default implementation returns `null`
  ("no subclass state to capture"), which is correct for the base playable
  sprites and any subclass that has not overridden the hook.
- **`AbstractPlayableSprite.restoreSubclassRewindState(PlayableSubclassRewindExtra)`**
  (protected hook) — called on every rewind restore, always invoked (never
  skipped), and tolerant of a `null` argument (the common case for base sprites,
  non-overriding subclasses, and snapshots produced by a pre-2.2 canonical
  constructor). It runs after all base `PlayerRewindExtra` fields,
  controller-owned state, and sensor offsets have already been restored, so
  overrides may safely read already-restored base sprite/controller state.

`PerObjectRewindSnapshot$PlayerRewindExtra` gained a new 154-component canonical
constructor appending `subclassExtra` as the trailing parameter. The prior
153-component canonical constructor (the 2.1 published shape) is preserved
verbatim as a compatibility overload and always yields a `null` `subclassExtra`,
matching the pattern used by the earlier standalone, Phase-2, and ROM-art
canonical-shape additions.

No existing 2.1 signature was removed, narrowed, or changed. Both
`mod-api-signatures-2.1.txt` and `mod-api-signatures-2.2.txt` are retained as closed
historical baselines; 2.2 is a strict superset of 2.1.

## Mod API 2.3.0 additive bump

`ModApiVersion.CURRENT` is `2.3.0`. This same-major additive minor bump above 2.2.0
publishes the host-adapted additive-zone seam used by strict Sonic 3&K level format
v2 while preserving the Sonic 2/standalone format-v1 route unchanged.

Added signatures:

- **`GameModule.getModZoneAdapter()`** and the forwarding
  **`DelegatingGameModule.getModZoneAdapter()`**, with
  **`GameModule.EMPTY_MOD_ZONE_ADAPTER`**, let registration validate and load a
  complete level through the selected host instead of branching on a game name in
  shared mod runtime code.
- **`ModZoneAdapter`** publishes exactly the narrow `validate`, `load`, and
  `runtimeProfile` contract over immutable, game-owned `ModZoneLevelData`. Data-select
  decoration stays on the unannotated internal `ModZoneDataSelectDecorator`; it is
  deliberately absent from the creator ABI. An unsupported host
  rejects the contribution before publication. `ModZoneRuntimeContribution` and
  `ZoneRegistry.modZoneRuntimeContribution(...)` carry that resolved payload to
  gameplay without introducing a `game -> mods` or `level -> mods` dependency.
- **`ModZoneHostMetadata`** and **`ModObjectZoneSet`** (`S3KL`/`SKL`) carry typed
  object-factory identity, while `ModLevelDefinition.hostMetadata()` exposes it
  without converting the tagged set into a runtime index.
- **`ModPaletteClaim`** and `ModLevelDefinition.paletteClaims()` publish sparse
  per-cell Genesis palette ownership. S3K retains host line 0 and the exact
  lives-HUD cells used by its mixed-palette mapping (line 0 icon/digits and the
  line 1 second icon piece); level art must claim every other reachable indexed
  color. A ROM-independent fixed mask enforces those reservations before creator
  publication, while runtime art-derived masks route each cell to its piece's
  actual palette line. The line-0 lives override is never reused as a line-1
  palette: line 1 receives the canonical host words derived from `Pal_AIZ`, which
  are verified against the stock ROM but stored internally for ROM-independent
  creator validation and composition.
- **`ModZoneRuntimeProfile`** and its `FLAT` scroll policy describe the intentionally
  empty initial runtime: no stock animation, PLC, special-render, or advanced-render
  features.
- **`GameModule.createCustomZonePaletteBridge(...)`** and
  **`CustomZonePaletteBridge`** keep host palette composition behind a game-owned
  interface; shared `LevelManager` never names the S3K implementation.

The engine implementation also preserves S3K mod zones in saves as tagged
owner/local identities and falls back to AIZ1 when an owner is disabled, without
trusting a synthetic runtime index. Those save/profile adapters are host internals,
not additional recursive creator types.

No existing 2.2 signature was removed, narrowed, or changed.
`mod-api-signatures-2.2.txt` remains immutable; `mod-api-signatures-2.3.txt` is its
strict additive superset and the live exact pin.

When the current surface next drifts, add a new baseline for an additive same-major
minor bump; never rewrite 2.3. For removals or changes, repeat a deliberate breaking
transition like 2.0.0.
