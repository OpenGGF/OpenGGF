# Mod API compatibility surface

OpenGGF's compiled-mod contract is marked by the runtime-visible, type-only
`com.openggf.game.ModApi` annotation. The supported inventory is not limited to
the initially curated roots: every engine type reachable through their public or
protected constructors, methods, fields, generic bounds, annotations, nested
types, supertypes, interfaces, record components, and sealed permits clauses is
part of the same contract and must also be annotated.

The published version is Mod API 2.0.0. Its recursive surface spans many hundreds
of engine types and is pinned exactly by `mod-api-signatures-2.0.txt` (the
`TestModApiSignatureSurface` guard is the authoritative count). The surface has a
single reconciled lineage, 1.1.0 -> 1.2.0 -> 2.0.0: 1.1.0 is the original closed
baseline; 1.2.0 was an additive minor bump (its frozen historical baseline
`mod-api-signatures-1.2.txt` contains **875 engine types** and **17,178 canonical
signature entries**, a strict superset of 1.1.0); and 2.0.0 is the current
breaking bump published on top of 1.2.0. The breadth is intentional. In
particular, the legacy-wide signatures of `GameModule`, `ObjectServices`, and
the object base classes expose substantial runtime infrastructure; silently
treating those transitive types as unsupported would make creator binaries depend
on an undocumented, unstable ABI.

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
`src/test/resources/mods/mod-api-signatures-2.0.txt`, pinned exactly to the
current canonical surface by `TestModApiSignatureSurface`. The prior
`mod-api-signatures-1.1.txt` (831 engine types, 16,483 canonical entries) and
`mod-api-signatures-1.2.txt` (875 engine types, 17,178 canonical entries) are
retained as closed historical records: 1.1 is the original contract and 1.2 is
its additive successor. `TestModApiSignatureSurface` verifies the full lineage —
1.1 -> 1.2 is asserted additive (1.2 is a strict superset of 1.1), and 1.2 -> 2.0
is asserted to be a declared breaking transition (see the 2.0.0
breaking-transition section below) — so 1.2's additions are never silently
absorbed into an undocumented 1.1 -> 2.0 jump. The guard requires the published
baseline to remain a subset of the current canonical surface, so removals and
changes fail while reviewed compatible additions can be published with an
appropriate semantic API version increase. Before updating the published
baseline:

1. run `TestModApiSignatureSurface` and inspect every added line;
2. annotate every newly reachable engine type;
3. narrow any third-party signature to a JDK or engine-owned contract instead of
   allowlisting the dependency;
4. add a JDK type only to the explicit platform allowlist after compatibility
   review; package-prefix exemptions are forbidden;
5. regenerate the sorted LF baseline and re-run the Javadoc/SDK packaging tests.

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

`ModApiVersion.CURRENT` is `2.0.0`. This is a deliberate major-version break from
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

When the current surface next drifts, refreeze `mod-api-signatures-2.0.txt`
(additions only, published with a same-major minor bump) or, for further
removals/changes, repeat this deliberate breaking-version transition.
