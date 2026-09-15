# Mod API boundaries: flexibility and compatibility commitments

Date: 2026-09-15.

Inspected source baseline: `1a63f57015a8012e6a34b2fe4f07cc0d52f91056` on
`develop`.

Status: source analysis and recommendations; no API changes implemented or
approved for publication by this document. The request was to investigate API
narrowing while preserving flexible, low-ceremony mod authoring. This record
preserves the evidence, alternatives and recommended sequence from that review.

Release context: the [0.7 roadmap](../../project/v0.7-roadmap.md) prioritizes stock
campaign completion and retained-feature regression coverage. Final API
qualification/publication belongs to the [0.8 workstream](../../project/v0.8-roadmap.md).
The API remains the unpublished, mutable 0.7 candidate. The
[release descriptor](../../../mod-api-release-policy.properties) and
[compatibility policy](../mod-api-compatibility.md) remain authoritative; this
audit changes neither version, publication status nor release gates.

## Recommendation

Keep a broad, programmable mod API. Reduce accidental compatibility commitments
and add focused extension points where creators currently depend on engine
machinery. Preserve custom objects, characters, physics, camera behavior,
rendering, game-module decoration, provider replacement, zones, standalone games,
assets and mod-owned rewind state.

Use the existing supported/internal distinction together with selective
interfaces or contexts. Start at the graphics-to-engine boundary, then improve
save inputs, stable object queries and character simulation hooks. Treat rewind
reconstruction as a separate, larger design task. Preserve the existing module
decoration model while reviewing its engine-only members.

Success means creators can build ambitious mods through clear extension points,
while level loading, rendering infrastructure and rewind internals can evolve
without routinely breaking those mods. No target type count is proposed.

## Evidence and measurement limits

The review inspected the signature walker, compatibility rules, validator,
SDK packaging, creator documentation, maintained sample sources and relevant
integration tests. It did not compile Java or run the engine/mod test suites.
Test references below describe assertions present in source, not newly observed
passing executions. No third-party mod inventory was available to establish
external usage.

The committed
[signature inventory](../../../src/test/resources/mods/mod-api-signatures-0.7.txt)
contains 967 engine types reachable from 36 explicit
[API roots](../../../src/main/java/com/openggf/mods/code/ModApiSurfaceInventory.java).
Of these types, 408 are records; 4,082 of the 10,597 method entries belong to
records, including generated methods. Consequently, the total method count
overstates the amount of independently authored behavioral API. A large value
vocabulary may be inexpensive to maintain; a few lifecycle methods can impose
substantial compatibility constraints.

### Graph method

A temporary Python analysis reconstructed a directed graph from the committed
signature text. Each inventory type was a node. Public/protected signatures,
supertypes, interfaces, nested types, record components, sealed permits and
annotation references supplied owner-to-referenced-type edges. The analysis
normalized nested-type names and classfile descriptors, excluded annotation
string contents, and restricted traversal to inventoried engine types. Existing
engine-internal terminals were therefore already outside the graph.

Breadth-first traversal from the 36 roots reproduced the complete 967-type set;
representative shortest paths were checked against Java source. Counterfactuals
stopped traversal at selected types, excluding those types from the resulting
surface. Selected diagnostic records were added as explicit roots in the
preservation experiments. Inputs were checked unchanged against the inspected
commit before reporting the results.

These are static reachability estimates. They are not execution of a modified
reflection walker, proof of source/binary compatibility, or a validated API
migration. New replacement interfaces would also contribute their own types.
The temporary probe and graph were discarded; the pinned inputs and procedure
above are sufficient to repeat the analysis. A future implementation should
measure its actual closure with `ModApiSignatureSurface` and the signature tests.

| Graph experiment | Reachable types | Removed from original set |
|---|---:|---:|
| Current candidate | 967 | 0 |
| Treat `Engine` as internal | 733 | 234 |
| Treat `Engine` as internal and explicitly retain the two diagnostic records required by existing tests | 783 | 184 |
| Additionally treat `GameplayModeContext` and `EngineContext` as internal, retaining those diagnostics | 729 | 238 |

The diagnostic records are `TraceEvent.CnzSlotMachineState` and
`TraceEvent.S2TornadoState`. Their shared `TraceEvent` hierarchy restores 50 types
through recursive traversal. The
[SDK tests](../../../src/test/java/com/openggf/tools/modsdk/TestModApiSdkPackager.java)
explicitly require these records to remain creator API. An implementation must
preserve that decision explicitly or separately justify changing it; a lower
inventory count is not sufficient reason to remove them.

### Principal exposure paths

```text
ObjectServices.graphicsManager()
  -> GraphicsManager.getEngine() / setEngine(Engine)
  -> Engine.getGameLoop()
  -> editor, replay and multiplayer orchestration

GameModule.getSaveSnapshotProvider()
  -> SaveSnapshotProvider.capture(..., RuntimeSaveContext)
  -> RuntimeSaveContext.gameplayMode()
  -> GameplayModeContext

ObjectServices.engineServices()
  -> EngineContext

RewindRecreatable.recreateForRewind(RewindRecreateContext)
  -> full object snapshot, ObjectManager and dynamic-entry structures
```

The public graphics getter and setter are the only incoming type-owner edge to
`Engine` in this graph. Removing the getter alone would leave the setter's
signature exposing `Engine`. The raw `Engine` cut removes 83 `net` and 63 `trace`
types, among other types; the diagnostic-preserving result above is the more
conservative planning reference.

Removing `ObjectServices` from the explicit root list changes no reachability:
other supported types still expose it. Real boundary changes must address
signatures, including inherited/protected members, rather than only root lists.

## Architectural alternatives

| Candidate approach | Benefits | Costs and limits | Recommendation |
|---|---|---|---|
| Retain the current surface unchanged | Maximum continuity; no migration work | Internal orchestration remains part of the prospective compatibility promise | Temporary baseline |
| Classify selected types as internal using existing machinery | Low runtime disruption; advanced access remains possible | Coupling remains; ordinary supported features must not acquire internal-reference warnings | Use selectively |
| Add focused interfaces or contexts at demonstrated problem boundaries | More coherent contracts, implementation freedom and simpler authoring | Adapters, sample migration and behavioral verification | Preferred direction |
| Add member-level API annotations throughout existing classes | Distinguishes supported operations from internal methods on the same class | Requires member-aware validation, signature collection, documentation and inheritance rules | Defer |
| Replace the extension model with a separate minimal API module | Strong physical separation | Large migration, duplication risk and likely loss of existing flexibility | Do not pursue now |

The recommendation combines selective internal classification with focused
contracts. It does not call for a wrapper around every class. Where an existing
type already provides a coherent creator contract, retain it.

The current [ModApi annotation](../../../src/main/java/com/openggf/game/ModApi.java)
is type-level, and the
[validator](../../../src/main/java/com/openggf/mods/validation/ModValidator.java)
classifies referenced engine types. Member-level support would require a
coordinated tooling change, not just a new annotation. Conversely, existing
[internal-terminal handling](../../../src/main/java/com/openggf/mods/code/ModApiSignatureSurface.java)
already allows an exposed internal type to carry no promise about its shape.

## Concrete candidates

### 1. Graphics projection access and engine orchestration

**Priority:** first boundary cleanup. **Confidence:** high.

[GraphicsManager](../../../src/main/java/com/openggf/graphics/GraphicsManager.java)
exposes `getEngine()` and `setEngine(Engine)`. The getter's three production
callers are `PatternRenderCommand`, `InstancedPatternRenderer` and
`BatchedPatternRenderer`; they inspect framebuffer projection state and display
height. `GraphicsManager` itself reads the engine's projection matrix.

Provide current projection matrix, framebuffer-projection state and effective
display height through graphics-owned state or a small projection interface.
Remove both public engine-typed signatures from the supported graphics contract.
Preserve custom rendering operations.

Selective internal classification is a possible first step with less runtime
change. Replacing the actual graphics-to-engine dependency is preferable if the
change remains bounded, because it also improves ownership. Neither approach
automatically preserves deliberately supported diagnostics: retain their roots
explicitly and review the resulting closure.

Verification: normal/offscreen rendering, projection switching and restoration,
signature closure, annotation inventory, Javadocs and maintained sample builds.
The inventory test requires annotated types to equal the reachable surface, so
pruning a path also requires reviewing annotations on types that become
unreachable; it is not a one-line metadata edit.

### 2. Purpose-built save-provider context

**Priority:** next bounded boundary candidate. **Confidence:** high.

[RuntimeSaveContext](../../../src/main/java/com/openggf/game/save/RuntimeSaveContext.java)
exposes `GameplayModeContext`, `LevelManager` and `GameStateManager`. The
[standalone platformer provider](../../../src/test/resources/mods/sample-platformer-src/project/src/main/java/example/platformer/PlatformerModule.java)
uses current zone/act and selected-team information. The standalone sample has
the same basic requirement.

Expose a coherent save input: current location, selected team, relevant
progression, save reason and live-versus-durable state, plus a supported route for
mod-owned payloads. Preserve arbitrary quest flags, inventories and custom
progression; do not constrain every game to the stock Sonic save schema.

The callback should not require knowledge of manager attachment, teardown or
rewind registration. Review both accessors and factory signatures that expose
the gameplay context, and inventory other consumers before replacing them.

Verification: New Game, Continue, saves without live gameplay, progression and
custom payload round trips. Confirm the context represents one coherent capture
boundary rather than a mixture of live values from different instants.

### 3. Stable object queries without rewind bookkeeping

**Priority:** add useful operations before narrowing manager access.
**Confidence:** high.

[FlappyController](../../../src/test/resources/mods/sample-flappy-src/project/src/main/java/example/flappysample/FlappyController.java)
calls `activeObjectsOfType`, obtains the rewind identity table through
`captureIdentityContext().requireIdentityTable()`, and sorts pipe objects by
identity before recycling them. Deterministic ordering is a legitimate gameplay
requirement; identity-table registration and capture machinery need not be its
public access path.

Provide stable-order queries or stable handles plus an ordering operation.
Specify semantics across destruction, recreation, rewind and respawn. Preserve
typed queries, dynamic spawning and parent/child relationships. Distinguish
supported identity lookup from mutation of the engine's identity table.

Verification: unchanged Flappy recycle/score sequences, repeated forward replay,
destruction/recreation and rewind across first spawn. A list with undocumented
iteration order is not an adequate replacement.

### 4. Simulation hooks for character state

**Priority:** address before promoting the character API as mature.
**Confidence:** high in the problem; exact hook requires design.

[BoltCharacter](../../../src/test/resources/mods/sample-platformer-src/project/src/main/java/example/platformer/BoltCharacter.java)
resets its double-jump latch in `draw()` because there is no dedicated landing
callback. The
[integration test](../../../src/test/java/com/openggf/mods/integration/TestSamplePlatformerIntegration.java)
explicitly invokes `draw()` to exercise that reset. This gives gameplay state a
presentation dependency and intersects the proposed
[independent rendering rate work](../designs/2026-09-14-independent-render-frame-rate-design.md).

Provide a landing notification or well-defined simulation update hook. Specify
terrain/object landings, multiple contact resolutions in one frame,
object-controlled movement, restore onto ground and reset/reload behavior.
One landing event may not reproduce every intended reset case; define the
lifecycle before moving the code. Keep fault attribution and mod-owned rewind
state intact.

Verification: double-jump rearming during simulation with rendering absent or
repeated, restore/forward replay and relevant contact/reset paths. Adding this
hook expands useful capability while reducing dependence on incidental behavior.

### 5. Mod-owned rewind payloads and reconstruction

**Priority:** separate substantial investigation before publication.
**Confidence:** medium on replacement shape.

[RewindRecreateContext](../../../src/main/java/com/openggf/level/objects/RewindRecreateContext.java)
exposes a complete object snapshot, `ObjectManager` and a dynamic-entry
structure. Playable subclass payloads also live in the wider
[snapshot vocabulary](../../../src/main/java/com/openggf/level/objects/PerObjectRewindSnapshot.java).

Investigate a creator reconstruction context and independently named payload
interfaces supporting capture/restore of mod-owned state, recreation, reference
resolution and relationship reconnection at a defined restore phase. Preserve
parent-dependent reconstruction, object/player identity and classloader ownership.
A scalar-only replacement would remove real capabilities.

This is not part of the small graphics cleanup. Establish behavior and migration
coverage before reducing access to the existing snapshot/reconstruction surface.

### 6. Module decoration and broad gameplay customization

**Disposition:** retain the programming model; review engine-only members.

Keep `GamePatch`, `DelegatingGameModule`, `AbstractStandaloneGameModule` and
provider replacement. The
[character physics sample](../../../src/test/resources/mods/sample-character-src/project/src/main/java/example/phase3character/SampleCharacterPhysicsPatch.java)
replaces its physics profile while delegating other behavior. Standalone samples
use custom level, object, audio and progression providers.

Review runtime-art timing coordination and session-management integration
individually. A new registration language for every provider would add ceremony
and make unusual combinations harder. Avoid a broad `ObjectServices` redesign
until specific consumer needs and alternate exposure paths are understood.

## Keeping authoring flexible

- Preserve ordinary Java classes, overrides, functions and providers.
- Keep advanced internal access available with compatibility warnings under the
  existing [trust policy](../../modding/concepts/trust.md). No additional
  permission or capability-negotiation system is proposed. Existing identity,
  registration, fault-boundary and rewind requirements remain in force.
- Prefer coherent interfaces of useful size. A capable camera or object
  interface is better than dozens of tiny handles authors must assemble.
- Preserve intentional gameplay mutation: position, velocity, camera and
  collision control are legitimate mod capabilities. Read-only access everywhere
  would prevent useful designs.
- Require supported paths for maintained examples. Reclassifying a dependency
  while leaving standard samples dependent on internals transfers the problem to
  creators.
- Treat supported/internal as a compatibility distinction. Moving metadata alone
  does not create a runtime isolation boundary.

The generated project compiles against the engine artifact. The
[SDK packager](../../../src/main/java/com/openggf/tools/modsdk/ModApiSdkPackager.java)
packages creator tooling and generates Javadocs from the curated inventory; it
does not distribute a replacement runtime containing only those API classes.
Preserving advanced access requires no new distribution channel.

## Recommended sequence and acceptance criteria

These are proposed implementation batches, not delivered milestones or new 0.7
release prerequisites. Keep campaign-serving fixes bounded and integrate changes
to shared owners in sequence. Final API qualification remains in the 0.8 plan.

1. **Clean the graphics boundary.** Preserve intended diagnostic contracts
   explicitly; update annotations, candidate pins and documentation together.
   Confirm ordinary samples gain no internal-reference warnings.
2. **Improve creator ergonomics.** Add simulation hooks for Bolt and stable
   queries/identity operations for Flappy; introduce the save input context.
   Migrate examples before reducing their old supported access. Simulation-hook
   work may need to precede other candidates when independent rendering requires
   it; the graphics-first recommendation is not a correctness dependency.
3. **Design the expensive contracts separately.** Rewind reconstruction,
   broad manager interfaces and engine-only module methods need their own
   consumer inventory and behavior analysis.

For each implemented batch, verify both:

- Compatibility machinery: signature inventory, exact annotation closure,
  internal-terminal pins, validator findings, Javadocs, SDK packaging and real
  maintained sample packaging/builds.
- Behavior: affected sample gameplay, custom rendering, save/load,
  rendering-independent simulation, capture/restore and forward replay.

Use repository validation policy for the actual change. This audit has not run
these checks and awards no implementation or release pass.

Before larger restrictions, exercise custom camera choreography,
terrain-changing objects, an unconventional movement ability and mod-owned
persistent state. Include interacting providers/mods where ordering matters.
The maintained samples establish a capability floor, not the full flexibility
envelope. Review external consumers if they become available.

## Decisions and open questions

- **Recommended:** selective internal classification plus focused creator
  contracts, beginning with observed exposure paths and sample workarounds.
- **Deferred:** member-level support annotations; their tooling cost is not
  justified by the first bounded candidates.
- **Not recommended now:** wholesale minimal-API replacement, a fixed type-count
  target, or replacing module decoration with a restrictive content DSL.
- **Still open:** exact simulation hook semantics; stable identity/order scope;
  save access to mod-owned state; parent-dependent reconstruction contract; and
  the intended long-term creator diagnostic/network surface. None is settled
  merely by making a type unreachable.
- **Publication constraint:** candidate changes require intentional review and
  pin/documentation updates. This analysis does not imply that an eventual
  published contract can be narrowed without its compatibility transition.
