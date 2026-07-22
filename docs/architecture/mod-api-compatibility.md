# Mod API compatibility surface

OpenGGF Mod API 0.7 (`0.7.0`) is the first published compiled-mod contract. The
runtime-visible, type-only `com.openggf.game.ModApi` annotation marks its roots.
Every engine type reachable through those roots' public or protected
constructors, methods, fields, generic bounds, annotations, nested types,
supertypes, interfaces, record components, and sealed permits clauses belongs to
the same recursive contract and must also be annotated.

The exact current inventory is
`src/test/resources/mods/mod-api-signatures-0.7.txt`. `TestModApiSignatureSurface`
requires that file to be unique, sorted canonical UTF-8 text and exactly equal to
the dependency-complete runtime surface. This is the only published signature
pin. Earlier 1.x and 2.x labels were provisional development markers and carry
no compatibility promise.

Creator manifests should declare the maintained engine range:

```yaml
engineApiRange: ">=0.7.0 <0.8.0"
```

Manifest `formatVersion: 1` is a separate wire-format version. It does not mean
Mod API 1.x and must not be used to infer compiled-code compatibility.

## What the 0.7 contract includes

The first baseline publishes the accumulated creator capabilities together:

- restart-loaded music packs and trusted, code-backed patches;
- namespaced object factories, bounded baked object art, Sonic 2 ROM-derived art,
  complete Sonic 2 zones, and host-adapted Sonic 3&K custom zones;
- owner-tagged playable characters, playable-subclass rewind payload hooks, and
  no-ROM standalone modules over durable bounded assets;
- exclusive custom game-start destinations, destination-scoped launch teams,
  deterministic input filters, and row-only HUD profiles;
- tagged saves, game-agnostic baked levels, deterministic TMX conversion, and
  the two-artifact `ggfmod` workflow.

The zone seam uses `LevelDescriptor` in creator-facing signatures; stock
`LevelData` values implement that interface without becoming creator ABI. Dynamic
object rewind entries retain their owning compiled-mod loader through
`DynamicObjectEntry.ownerModId` and `RewindClassResolver`. Creator callbacks stay
transactional, owner-fault-bounded, and engine-authoritative.

## Publishing and reviewing the recursive surface

Before changing the published baseline:

1. Run `TestModApiSignatureSurface` and inspect every added or changed line.
2. Annotate every newly reachable engine type.
3. Narrow third-party signatures to JDK or engine-owned contracts instead of
   allowlisting dependencies.
4. Add a JDK type only to the explicit platform allowlist after compatibility
   review. Package-prefix exemptions are forbidden.
5. Regenerate the sorted LF snapshot and rerun the Javadoc, SDK packaging, and
   maintained sample tests.

On PowerShell, the snapshot tool needs both compiled engine classes and ASM. Use:

```powershell
mvn "-DskipTests" compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/mod-api-snapshot-classpath.txt"
$cp = "target/classes;$((Get-Content target/mod-api-snapshot-classpath.txt -Raw).Trim())"
java -cp $cp com.openggf.mods.code.ModApiSignatureSurface --snapshot |
    Set-Content -Encoding utf8NoBOM src/test/resources/mods/mod-api-signatures-0.7.txt
```

Release packaging generates exact-inventory Javadoc and attaches
`openggf-mod-sdk` and `openggf-mod-sdk-javadoc` classifier jars beside the engine
artifact. Architecture guards ignore only the `@ModApi` marker edge and the
release tool's exact inventory lookup; the annotation does not establish runtime
ownership.

New creator APIs should prefer narrow engine-owned facades and immutable value
types. A public or protected signature may not leak an unannotated engine type or
an unreviewed third-party type. Once 0.7 is published, removals, narrowing changes,
record-shape changes, and unannotation require an intentional compatibility
decision rather than a silent snapshot rewrite.

## 0.7 reset inventory

The reset removed provisional compatibility shims before establishing the first
baseline. `TestNoProvisionalModApiShims` is the executable evidence that these
members remain absent.

| Removed member or family | Evidence marker |
| --- | --- |
| Legacy `CheckpointState.RewindState` constructor | canonical-record constructor assertion |
| Legacy `CameraSnapshot` constructor | canonical-record constructor assertion |
| Legacy `GameStateSnapshot` constructor | canonical-record constructor assertion |
| Legacy `WaterSystemSnapshot.DynamicWaterEntry` constructor | canonical-record constructor assertion |
| Legacy `CollisionRules` constructor families | exact canonical-plus-`AirCollisionRules` constructor set |
| Legacy `ObjectInteractionRules` constructor | canonical-record constructor assertion |
| Legacy `PlayerAnimationRules` constructor | canonical-record constructor assertion |
| Legacy `PlayerCapabilityRules` constructor | canonical-record constructor assertion |
| Legacy `RingRules` constructor families | canonical-record constructor assertion |
| Legacy `SidekickCpuRules` constructor | canonical-record constructor assertion |
| Legacy `PerObjectRewindSnapshot.SidekickCpuRewindExtra` constructor | canonical-record constructor assertion |
| Legacy `PerObjectRewindSnapshot.PlayerRewindExtra` constructor | canonical-record constructor assertion |
| Legacy `ModZoneContribution` constructor | canonical-record constructor assertion |
| Legacy `PlayableSpriteMovement.RewindState` constructor | canonical-record constructor assertion |
| Legacy `PlayableSpriteController.RewindState` constructor | canonical-record constructor assertion |
| Legacy `TraceMetadata` constructor | canonical-record constructor assertion |
| `SpriteManager.drawUnifiedBucketWithPriority(int, GraphicsManager, Runnable, Runnable)` | reflected method-absence assertion |
| `ObjectManager.snapshotPersistentDynamicObjectsForTransition()` | reflected method-absence assertion |
| `AbstractPlayableSprite.mgzTopPlatformCarrySolidContactObject` | reflected field-absence assertion |
| `AbstractPlayableSprite.mgzTopPlatformSpringHandoffPending` | reflected field-absence assertion |
| `AbstractPlayableSprite.mgzTopPlatformSpringHandoffXVel` | reflected field-absence assertion |
| `AbstractPlayableSprite.mgzTopPlatformSpringHandoffYVel` | reflected field-absence assertion |
| Provisional compatibility comments and marker phrases in production Java | exact empty marker allowlist |

The underlying current behaviors remain available through their canonical 0.7
owners; this inventory records deleted shims, not removed product capabilities.

## Historical development corpus

The following dated documents preserve development history and scope provenance.
They are not current version authority:

- `docs/superpowers/specs/2026-07-10-mod-support-format-security-contracts.md`
- `docs/superpowers/specs/2026-07-13-example-mods-design.md`
- `docs/superpowers/specs/2026-07-14-flappy-native-tails-design.md`
- `docs/superpowers/specs/2026-07-14-mod-gap-fixes-design.md`
- `docs/superpowers/specs/2026-07-14-rom-art-remix-sample-design.md`
- `docs/superpowers/specs/2026-07-14-s3k-mod-zone-adapter-design.md`
- `docs/superpowers/specs/2026-07-22-mod-api-0-7-reset-design.md`
- `docs/superpowers/plans/2026-07-13-mod-rom-art-intake.md`
- `docs/superpowers/plans/2026-07-13-sample-flappy-mod.md`
- `docs/superpowers/plans/2026-07-13-sample-platformer-mod.md`
- `docs/superpowers/plans/2026-07-14-mod-gap-fixes.md`
- `docs/superpowers/plans/2026-07-14-mod-gameplay-policies.md`
- `docs/superpowers/plans/2026-07-14-native-tails-flappy.md`
- `docs/superpowers/plans/2026-07-14-rom-art-remix-sample.md`
- `docs/superpowers/plans/2026-07-14-s3k-mod-zone-adapter.md`

This document is the sole current Mod API version authority. The maintained
creator workflow and format documentation begins at
[`docs/modding/index.md`](../modding/index.md).
