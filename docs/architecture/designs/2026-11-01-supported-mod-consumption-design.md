# Supported mod consumption: official examples use or extend the Mod API

Date: 2026-11-01. Status: design accepted; unit 1 in progress on `feature/ai-supported-mod-consumption`.
Produced with the starch-rival `architect` workflow: two read-only explorers (gpt-6.1-sol), two
independent design runners (Opus 5.5 and gpt-6.1-sol), one cross-judge (gpt-6.1-sol) and an Opus
synthesis. The base contract sketch is kept beside this file as
[`supported-mod-consumption/base-sketch.java.txt`](supported-mod-consumption/base-sketch.java.txt); its
type names are the vocabulary for later units, not final signatures.

## Problem

`@ModApi` is a compatibility promise, not an access boundary. Mods compile against the full engine jar,
load through a parent-first `ModDependencyClassLoader` that never consults the annotation, and
`ModValidator` reports unsupported engine references only as `NON_API_ENGINE_REFERENCE` warnings. Its
trust also includes the legacy `BUILTIN_API` allowlist and any classfile that carries the annotation,
whether or not the type is in the pinned surface.

As a result five official examples reached 56 distinct unmarked engine types (source scan of develop
`5ebd418ef`): typing-of-the-hedgehog 31, infinite-sonic 11, sonic-survivors 7, putt-putt-paradise 6,
starpost-valley 1. `racing/time-attack` (packaged with `--warnings error`), the other examples and the SDK
starters use none. Bytecode checking alone cannot see the worst cases: `static final` constants such as
`InputActionMasks.ACTION_B` and `ObjectArtKeys` strings are inlined, and a terminal value forwarded through
a marked type (`GameRules.dynamicArtDmaService()` in Survivors) needs no import.

The owner's rule (2026-11-01): every official example mod must either (1) use ModApi-supported
functionality, or (2) be the avenue through which official support is expanded, in a safe-access way.
Wholesale `@ModApi` promotion of native implementation classes is not an acceptable default.

## Usage (caller's view)

An official example compiles with the supported-consumption check. Any use of an engine symbol outside the
supported contract is a compile error at the offending line, including inlined constants and terminal
values passed along through `var`. A capability that does not exist yet is added as a facade in the same
change as, or before, the example that needs it.

```java
// starpost-valley TownInput, after unit 2: typed actions instead of InputActionMasks constants
PlayerInputState filtered = raw.withoutActions(PlayerInputState.Action.B);
boolean talk = pad.actionPressed(PlayerInputState.Action.B);

// sonic-survivors Enemy.fireShot, after unit 4: a registered, owner-bound auxiliary actor
services().auxiliaries().spawn(shots, AuxiliarySpawn.at(currentX, currentY)
        .withArguments(SpawnArguments.empty().withInt("vx", svx).withInt("vy", svy)));

// typing-of-the-hedgehog, after unit 6: draw Stagehand visuals; no Puppet objects
@Override public void drawWorldSprites(CourseDraw draw) {
    for (var visual : stagehand.all())
        draw.sprites().submit(art.frame(visual.artKey(), visual.frame()), visual.pose(), visual.order());
}
```

## Shape

### One consumption predicate

`SupportedModContract` is built from the engine's own classfiles through the same root inventory and
terminal/platform classification as `ModApiSignatureSurface`, and must agree with the normalized pins. A
member is **consumable** when all three hold:

1. its declaring type is an engine `@ModApi` type in the pinned, reachable surface (an annotation alone is
   not authority, and a mod-supplied annotation never is);
2. it is not marked `@EngineOnly` (new, member-level; removes a member from the promise and from
   consumption, used first for ambient singleton accessors such as `GraphicsManager.getInstance()`);
3. its signature, including generics, arrays, bounds and throws, names no engine-internal terminal.

Inheriting a marked type that carries a terminal-bearing default bridge (for example
`ModRewindRecreatable`'s raw rewind overload) is allowed; calling, overriding or referencing that bridge
is not. `BUILTIN_API` contributes nothing to supported consumption.

### Two inspections, one contract

- **Attributed source check**: a javac `Plugin` (`com.sun.source` API) that runs after ANALYZE, before
  lowering and constant folding. It checks resolved symbols for identifiers, member selects, imports
  (including unused and static), annotations, class literals, `extends`/`implements`, overrides, member
  references and lambdas, and the static type of every expression and `var`. It also rejects reflective
  and class-loading operations in supported mode. Diagnostics name the source span and the capability
  group to use or add.
- **Bytecode check**: `ModValidator` gains a `SUPPORTED_ONLY` mode using the same contract: type and
  member references with inherited owners resolved, descriptors and signatures, supertypes, annotations,
  method handles, invokedynamic and constant-dynamic, across every class in the payload and its bundled
  dependencies. It catches precompiled code; the source check is still required for constant provenance.

### Where it runs

- `OfficialModsSupportedGuard` (test class `TestOfficialModsSupportedSurface`, `mods` smoke category,
  ROM-free): discovers official projects from their existing manifests (`examples/*`, `racing/time-attack`,
  SDK starters and templates), compiles each production source set in one in-process `JavaCompiler`
  harness against the compiled engine with the source check enabled, then runs `SUPPORTED_ONLY` on the
  output. Example test sources are checked against an explicit testkit contract. Outputs stay under
  `target/test-tmp`. Adversarial fixtures (inlined constant, static and FQN references, unused import,
  `var` terminal, discarded terminal result, raw rewind override, inherited internal member, reflection,
  forged annotation) must each produce exactly their expected finding.
- `build_project.py`, exported creator builds and bundled packaging use the supported check by default; an
  explicitly named expert lane keeps today's warning behaviour for third-party mods.

### Migration debt is a project set, not a symbol allowlist

The five examples above are listed once, as a constant in `TestOfficialModsSupportedSurface`. Every
other official project is gated from unit 1. The guard fails when a listed project has no findings (it
must be removed in that change) and when an unlisted project, including any new example, has any finding.
Adding a project to the set means editing the guard itself, which is visible in review; the set only
shrinks, and the final unit deletes it. A listed project can still gain new unsupported uses while it is
being migrated; that is the accepted cost of not blocking all work behind one cutover.

### Facades (later units)

Each new capability is a deep, owner-bound, revocable interface; mechanics, residency and rewind stay in
the engine. Names follow the base sketch.

| Group | Replacement |
|---|---|
| 1 Action bits | `PlayerInputState.Action` with `actionHeld`, `actionPressed`, `withoutActions`, `withAction` |
| 2 Global services | injected `TitleControl`/`TitleDraw`, `CourseControl` (existing) and new `CourseDraw`; objects keep `services()`; `@EngineOnly` ambient accessors |
| 3 Native title | `NativeTitle` driver with `TitleBackground` selections; `SceneRomArt.titleArtwork()` detached parts for Putt-Putt |
| 4 Auxiliary actors | `ModContext.registerAuxiliary(key, factory)` + `ObjectServices.auxiliaries().spawn(key, AuxiliarySpawn)` with bounded scalar `SpawnArguments`; Typing's render-only puppets are deleted |
| 5 Native spring | spring recipe with a side-entry policy; `GolfUpSpring` deleted |
| 6 Touch | `ModTouchPolicy.resolve(TouchContact)` before native dispatch: native, replace hit (`PlayerHitEdit`), or handle pickup (`PickupReward`), then `resolved(TouchOutcome)`; covers SPECIAL pickups, which a harm-only hook cannot |
| 7 Priority / multipart / scale | `drawWorldSprites(CourseDraw)` immutable submissions; compositor owns both priority passes, scaling and tile occlusion; stateless `RenderPriority` evaluated for promotion |
| 8 Stock keys / animals | promote `ObjectArtKeys` if every member passes the stateless criterion, else a catalogue key type; `WorldArtBank.animalFrames()` |
| 9 Detached art | `WorldArt.prepare(List<WorldArtSpec>)` returns a `WorldArtBank` owning CPU sheets and GPU lease; preparation reports every bad alias/frame with its mod-owned label in one failure |
| 10 Terrain rehearsal | `CourseControl.rehearse(RehearsalSpec)` returns `TerrainRehearsal` (floor strips, support placement, fractional foreground clearance, stepping, steering, checkpoints, monotone budgets) |
| 11 Held world | `CourseControl.holdWorld(HoldSpec)` returns an exclusive `HeldWorld` lease |
| 12 Actors / animation | `ObjectQuery` native-actor observations; `SpriteAnimationProfile` immutable edit |
| 13 Trig | promote `TrigLookupTable` if it passes the stateless criterion |
| 14 Music | `GameMusic` += `BOSS`, `FINAL_BOSS`, `ENDING` |
| 15 Terminals | `GameRules.withPowerUp`, `PowerUpRules` edits; terminal-bearing members are non-consumable |

Long-lived handles (`WorldArtBank`, `HeldWorld`, `TerrainRehearsal`) carry an engine-minted
`ResourceKey<T>`. Mods capture the key, never the wrapper, and resolve a fresh wrapper per callback. One
resource-state rewind adapter per owner and session captures logical identities and detached state, never
GPU handles; full level loads reset that timeline.

**Promotion criterion** for marking an existing helper wholesale: stateless, no lifecycle, ROM-semantic
values or fixed-point maths, and every public member acceptable as a promise.

## Synthesis decision

- **Base: the gpt-6.1-sol candidate.** The cross-judge (also gpt-6.1-sol, so self-preference was checked
  against the evidence) scored it higher on the owner's rule, enforcement and facade safety. Its ownership
  model was stronger: registered auxiliary factories, immutable sprite submission instead of presentation
  objects, pre-dispatch touch resolution, and explicit resource-key rewind ownership.
- **Grafted from the Opus candidate:** gate clean projects from unit 1 and migrate example by example
  (the base deferred every gate to one atomic cutover); `@EngineOnly`, because the base's derived
  terminal rule left marked ambient singletons such as `GraphicsManager.getInstance()` consumable; the
  fractional foreground-clearance query (Typing accepts a view when at least half its samples are
  transparent, `SectionTravel.java:219`); aggregated art-use diagnostics; individual promotion of stateless
  helpers instead of parallel replacements such as a new `NativeTrig`.
- **Rejected from the Opus candidate:** `onHarmfulTouch` (Infinite's rings and monitors award pickups on
  SPECIAL contacts through the listener, `CourseRing.java:57`, `CourseMonitor.java:63`); presentation
  objects refreshed on update (held rows need fresh pictures without native updates); a free-form
  auxiliary supplier; its symbol-keyed debt ledger (another use of an already-ledgered symbol passed).
- **Deferred from the base:** the strict runtime loader policy. It complements validation but needs
  adversarial verification; decide in the final unit.

## Tradeoffs accepted

- We accept a dependency on the public `com.sun.source` compiler API in exchange for seeing constants and
  inferred terminal values that bytecode cannot show.
- We accept a shrink-only pending-migration project set for the duration of the programme in exchange for
  enforcement on every other official project from the first unit. The set lives in
  `tools/modding/supported-consumption-pending.txt`, the single list read by the guard test, the creator-kit
  workflow and the exported example READMEs, which build those projects with `--expert-legacy`. The first
  PR run (15e18a232) failed `creator-kit` because the workflow built every example strictly while the set was
  a constant inside the test.
- We accept `@EngineOnly` as a change to surface policy in exchange for narrowing promises without
  unmarking types every mod touches.
- We accept callback signature changes (title providers, frame-controller draws) in exchange for no
  ambient lookup; each change migrates all callers in the same unit, so there are never two ways.
- We accept that some native title, art or custody profiles are explicitly unsupported until a provider
  exists, rather than falling back.

## Alternatives considered

- **API-only stub jar as the compile boundary.** Marked signatures name terminals, so a faithful stub still
  permits unsafe members and a filtered one invents shapes the runtime lacks; it also cannot check
  precompiled payloads. A generated IDE view may follow later from the same contract.
- **Strict bytecode validation alone.** Unsound for inlined constants and `var` terminals.
- **One `ModWorld` service or a command buffer for everything.** Re-badges `GameServices` or moves ordering
  semantics (pre-hurt touch, render priority) into a second protocol.
- **Promote the native protocols** (`MultiBucketRenderable`, `ObjectConstructionContext`,
  `SpringObjectInstance`, title `BackgroundOverride`). Shallow, violates the owner's rule.

## Sequencing

Each unit keeps every example building and pins/guards green; surface changes update pins in the same
commit.

| Unit | Delivers | Example status after it |
|---|---|---|
| 1 Enforcement | `SupportedModContract`, `@EngineOnly` (applied to ambient accessors), javac source check, `ModValidator` `SUPPORTED_ONLY`, `TestOfficialModsSupportedSurface` with adversarial fixtures and the pending-migration set, strict creator-build default | 20 clean projects gated |
| 2 Vocabulary | action queries, music cues, rules/animation edits, helper promotions | starpost-valley clean |
| 3 Callback contexts | `TitleControl`/`TitleDraw`, `CourseDraw`; migrate every caller | no `GameServices` in examples |
| 4 Interaction | `ModTouchPolicy`, spring recipe, `registerAuxiliary`, native-actor queries | sonic-survivors clean |
| 5 Titles | `NativeTitle`, `SceneRomArt.titleArtwork()` | infinite-sonic, putt-putt-paradise clean |
| 6 World art | `WorldArt`/`WorldArtBank`, `drawWorldSprites`, resource-key rewind adapter | Typing groups 4, 7-9 done |
| 7 Held world | `HeldWorld` lease | Typing groups 11-12 done |
| 8 Rehearsal | `TerrainRehearsal` | typing-of-the-hedgehog clean |
| 9 Close | delete pending set; decide loader policy; docs, skill, changelog | all official projects strict |

## Open questions and risks

- Does the all-official semantic compile fit routine smoke cost on the qualified host? Measure in unit 1.
- Can one resource-state rewind adapter restore a pre-hold checkpoint without changing registry layout?
  Must be proven before Typing migrates in unit 7.
- Which native title-scroll and flight-custody profiles can be supported without changing gameplay?
- Does `ObjectServices.floorSurface` match `ObjectTerrainUtils.checkFloorDist` for Typing's encounter
  floor following?
- `GolfOnline` calls scene capture from packet handling: does that run inside a row or draw callback?

## Next implementation step

Implement `SupportedModContract` and the source check with fixtures that reject
`InputActionMasks.ACTION_A` and a `var` use of `GameRules.dynamicArtDmaService()` while accepting the
projected rewind interface, then wire `TestOfficialModsSupportedSurface` over the discovered projects.
