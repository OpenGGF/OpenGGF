# Mod gap fixes: standalone object-art wiring + mod-character subclass rewind — design

Date: 2026-07-14
Status: approved (follow-up to `2026-07-13-example-mods-design.md`; fixes the two
engine gaps that project documented and Scheduled in `docs/modding/BACKLOG.md`)

## Goal

Make the two published-surface promises true in production:

1. **Gap A — standalone `registerObjectArt` wiring**: `ModContext.registerObjectArt`
   sheets must render in real standalone gameplay without the author hand-rolling a
   provider.
2. **Gap B — mod-character subclass rewind**: a custom playable character's own
   state fields (e.g. a double-jump latch) must survive every production rewind
   path — keyframe-exact seeks and cached-segment scrubs included.

## Part 1 — Gap A (engine, no API change)

- `ModRuntime.newRegistrationPlan()` passes `plan.preparedObjectArt()` into
  `OwnerAwareStandaloneModule.wrap(...)` (new parameter; plan is in scope at the
  call site, `ModRuntime.java:131-138`).
- When the prepared map is **non-empty**, the proxy's `BoundaryHandler`
  intercepts `getObjectArtProvider()` (mirroring the existing `getGameService`
  special-case) and returns a provider built once by **decorating the
  delegate's own provider** — `ModArtOverlayProvider.decorate(base, prepared)`
  where `base` is the delegate's `getObjectArtProvider()` result obtained
  through the boundary, exactly the semantic `ModBackedGamePatch` uses for
  patch mods (`ModBackedGamePatch.java:145-153` decorates `inherited`). The
  null-object base is a **fallback only**, used when the delegate returns
  `null` (today's `AbstractStandaloneGameModule` default). A future module
  that both overrides `getObjectArtProvider()` (custom HUD/zone art) and calls
  `registerObjectArt` therefore keeps its own provider with the registered
  sheets layered on top — never silently replaced. When the map is **empty**,
  behavior is unchanged (delegate's value passthrough) so the phase-3 sample
  and empty modules keep their exact current behavior.
- `ObjectArtOverlayProvider` requires a non-null base (ctor
  `Objects.requireNonNull`), so a new **engine-internal null-object base**
  (`EmptyObjectArtProvider` in `com.openggf.mods.code`, package-private, NOT
  `@ModApi`) supplies neutral behavior: no sheets, empty renderer keys,
  `ensurePatternsCached` returns `baseIndex`, no-op zone/HUD hooks, `isReady()`
  true.
- No published-surface change: `OwnerAwareStandaloneModule`, `ModRuntime`,
  `ModArtOverlayProvider`, and the null-object base are all engine-internal.
- **Sample follow-through**: `sample-platformer` drops
  `SheetBackedObjectArtProvider`, the sheet-map constructor parameter, and
  `buildObjectSheets` (its `registerObjectArt` calls stay and become the engine's
  overlay source). `TestSamplePlatformerIntegration` re-points its provider
  assertions at the engine path (provider present and serving both namespaced
  keys **without** any module override — the regression fixture the backlog
  promised). The `standalone-platformer.md` guide section "Why a standalone
  module has to serve its own object art" is rewritten to describe the engine
  wiring.

## Part 2 — Gap B (engine, additive API → Mod API 2.2.0)

- New `@ModApi` marker interface `PlayableSubclassRewindExtra` (nested in
  `PerObjectRewindSnapshot`, mirroring the existing `BadnikSubclassRewindExtra` /
  `ObjectSubclassRewindExtra` precedent). Implementations must be immutable —
  snapshots are stored as in-memory object graphs (no codec), so aliasing mutable
  state would corrupt keyframes; the interface Javadoc states this contract.
- Two new overridable hooks on `AbstractPlayableSprite` (both default no-ops):
  - `protected PlayableSubclassRewindExtra captureSubclassRewindState()` → `null`
  - `protected void restoreSubclassRewindState(PlayableSubclassRewindExtra extra)`
    (invoked on every restore, including with `null`)
- `PlayerRewindExtra` gains a `PlayableSubclassRewindExtra subclassExtra`
  component appended **last**, with the previous canonical constructor preserved
  as an explicit compat overload (the established trick — `PerObjectRewindSnapshot`
  already carries three such compat ctors; the old canonical ctor is pinned at
  `mod-api-signatures-2.1.txt:1364`).
- `captureRewindState(boolean)` (the single build site — the no-arg variant
  delegates) captures the hook's value; `restoreRewindState(...)` invokes the
  restore hook with the stored payload. Because keyframe-exact seeks,
  forward-replay seeks, and both cached-segment scrub paths all funnel through
  `restoreRewindState` (verified: `RewindController.java:285, 352, 413` → the
  `RewindSnapshottable` adapter registered in `SpriteManager` (~L1651), whose
  `restore` calls `aps.restoreRewindState` at `SpriteManager.java:1714`), one
  hook pair fixes every path.
- **Surface refreeze**: `ModApiVersion.CURRENT` → 2.2.0; new
  `mod-api-signatures-2.2.txt` baseline; `TestModApiSignatureSurface` pin test
  and additive-chain tests updated (2.1 counts freeze at 17,196; new 2.1→2.2
  additive test); compatibility-doc lineage entry. `TestModApiJavadocTool`
  compares type names only — the one new nested marker type must be annotated
  and inventoried.
- **Sample follow-through**: `BoltCharacter` implements the hooks with an
  immutable `record BoltRewindExtra(boolean doubleJumpUsed) implements
  PlayableSubclassRewindExtra`; the landing-clear in `draw()` stays (it is
  gameplay logic, not just a staleness bound). `TestSamplePlatformerIntegration`
  migrates its rewind check from the `GenericFieldCapturer` scaffold to the
  production `captureRewindState()`/`restoreRewindState(...)` round-trip. The
  `characters.md` known-limitation note is replaced by documentation of the new
  hooks; the guide's rewind chapter references them.

## Testing

- Gap A: engine-level test through `ModRuntime.prepareStandaloneModule` — a
  standalone owner with registered object art yields a provider serving its
  namespaced keys; an owner with no object art still yields `null` (behavioral
  parity). Sample integration test proves the shipped path with the hand-rolled
  provider deleted.
- Gap B: engine unit test with a test-local `AbstractPlayableSprite` subclass —
  hook value captured into the snapshot, restored on `restoreRewindState`,
  null-tolerant when unimplemented; compat-ctor coverage. Sample integration
  test exercises the production round-trip on Bolt.
- Both: `TestSampleModsPackage` (7 samples) and full `mvn test` green; surface
  guards green after the 2.2.0 refreeze.

## Docs closure

`CHANGELOG.md` entries (standalone wiring fix; Mod API 2.2.0 rewind hooks);
`docs/modding/BACKLOG.md` rows updated from **Scheduled** to delivered (moved to
sweep-reconciliation style notes); `characters.md`, `standalone-platformer.md`,
`content-mods.md`/`standalone-games.md` where they describe the old behavior;
agent docs' Mod API version prose (2.1.0 → 2.2.0).

## Out of scope

- Any change to the flappy sample (unaffected by both gaps).
- Codec/serialized rewind for the subclass payload (in-memory object graph is
  the player path's storage model).
- Publishing the null-object base provider or `OwnerAwareStandaloneModule`.
