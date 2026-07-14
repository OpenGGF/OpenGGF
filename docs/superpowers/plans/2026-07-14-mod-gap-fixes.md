# Mod Gap Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the two Scheduled engine gaps from the example-mods project: standalone `registerObjectArt` wiring (Gap A, engine-internal) and mod-character subclass rewind capture (Gap B, additive Mod API 2.2.0), with the `sample-platformer` gallery sample migrated to the fixed paths as the regression fixture.

**Architecture:** Spec: `docs/superpowers/specs/2026-07-14-mod-gap-fixes-design.md`. Gap A threads `plan.preparedObjectArt()` into `OwnerAwareStandaloneModule.wrap`; when non-empty, the proxy handler intercepts `getObjectArtProvider()` and returns `ModArtOverlayProvider.decorate(base, prepared)` where `base` is the **delegate's own provider obtained through the boundary** (the `ModBackedGamePatch.java:145-153` semantic), with `EmptyObjectArtProvider` only as the null-fallback base. Gap B adds the `PlayableSubclassRewindExtra` marker + two protected hooks on `AbstractPlayableSprite`, carried as a new last component of `PlayerRewindExtra` (old canonical ctor preserved), captured/restored at the single build/restore sites that all rewind paths funnel through.

**Tech Stack:** Java 21, JUnit 5, Mod API surface machinery (`TestModApiSignatureSurface` refreeze to 2.2.0).

## Global Constraints

- Branch `feature/ai-mod-gap-fixes` (off `next` at the example-mods merge). Never `git add -A`; exact paths only. The working tree may contain a concurrent session's edit to `docs/rewind/real-gaps.md` — never stage it.
- Tests JUnit 5 only. Commit trailer block on every commit; `feat`/`fix` commits touching `src/main/` stage `CHANGELOG.md` with `Changelog: updated` OR justify (`Changelog: n/a: covered by aggregate mod-gap-fixes CHANGELOG entry in the final task`).
- PowerShell: quote Maven `-D` properties. Bash tool is real bash.
- Gap A adds NOTHING to the published surface (all engine-internal). Gap B's additions are exactly: the marker interface, the two protected hooks, the new `PlayerRewindExtra` component + preserved old canonical ctor. Anything else appearing in the Task 4 baseline diff is a leak to fix by visibility.
- Do not run `TestModApiSignatureSurface` expecting green between Task 3 and Task 4 (red by design after the Gap B API lands).
- All research anchors below were verified on this branch tip (`bd11786dd`); re-verify surrounding code while editing, per read-then-adapt discipline.

---

### Task 1: Gap A engine wiring

**Files:**
- Create: `src/main/java/com/openggf/mods/code/EmptyObjectArtProvider.java` (package-private, NOT @ModApi)
- Modify: `src/main/java/com/openggf/mods/code/OwnerAwareStandaloneModule.java` (wrap signature + handler short-circuit)
- Modify: `src/main/java/com/openggf/mods/code/ModRuntime.java:131-138` (pass `plan.preparedObjectArt()`)
- Test: `src/test/java/com/openggf/mods/code/TestStandaloneObjectArtWiring.java`

**Interfaces:**
- Consumes: `ModArtOverlayProvider.decorate(ObjectArtProvider base, Map<String, BakedSheetReader.BakedSheet> overlays)` (public 2-arg, `ModArtOverlayProvider.java:15`); `ObjectArtOverlayProvider` (non-null base required, ctor `Objects.requireNonNull` at `:28`).
- Produces: `OwnerAwareStandaloneModule.wrap(String owner, GameModule delegate, ModFaultBoundary boundary, Map<CharacterKey, CharacterDefinition> characters, Map<String, BakedSheetReader.BakedSheet> preparedObjectArt)`. **KEEP the existing 4-arg overload delegating with `Map.of()`** — `TestOwnerAwareStandaloneModule` has ~10 4-arg call sites (verified) and must compile untouched. Behavior: non-empty map → `getObjectArtProvider()` returns one lazily-built decorated provider whose base is the **delegate's own `getObjectArtProvider()` result invoked through the boundary** (mirror `ModBackedGamePatch.java:145-153`; `EmptyObjectArtProvider` only when the delegate returns null), NOT re-proxied; empty map → delegate passthrough (today `null`) — exact behavioral parity for art-less standalone modules like phase3-standalone (verified: its entrypoint never calls registerObjectArt).

- [ ] **Step 1: Read the three engine files** — `OwnerAwareStandaloneModule.java` in full (the `BoundaryHandler` invocation handler, the `getGameService`/`getLevelMusicReference` special-cases at ~L60/L90, `wrapReturnedValue` re-proxying at L110-116), `ModRuntime.java:67-180`, `ModArtOverlayProvider.java`, `ObjectArtOverlayProvider.java` (every method that dereferences `base` — the null-object must satisfy all of them: `getRenderer`, `ensurePatternsCached` (return `baseIndex`), `getRendererKeys` (empty list), `loadArtForZone`, `getSheet`, `getAnimations`, `getArtBundle`, HUD getters, `registerLevelTileArt`, `reloadStandaloneArtForActTransition`, `isReady` (true) — read `ObjectArtProvider`'s interface for the full set and default methods).

- [ ] **Step 2: Write the failing engine test**

`TestStandaloneObjectArtWiring` drives the REAL runtime path (model setup on however `TestPhase3StandaloneSampleIntegration` or a lighter existing ModRuntime test constructs a runtime with an in-memory/staged mod — find the lightest existing fixture; if only the heavy jar-build path exists, test at the `OwnerAwareStandaloneModule.wrap` level directly instead with a stub GameModule):

```java
@Test
void standaloneModuleWithRegisteredArtServesItThroughTheProxy() {
    // wrap(owner, stubModuleReturningNullProvider, boundary, Map.of(), preparedWithOneSheet)
    // -> getObjectArtProvider() != null and getRenderer("owner:key") resolves after
    //    pattern caching; provider is NOT null and NOT the delegate's null
}

@Test
void standaloneModuleWithoutArtKeepsNullProvider() {
    // wrap(..., Map.of()) -> getObjectArtProvider() == null (exact parity)
}

@Test
void decoratedProviderIsNotReProxied() {
    // returned provider is the engine ObjectArtOverlayProvider instance (or at
    // minimum: not a java.lang.reflect.Proxy)
}
```

Build the one-sheet `BakedSheetReader.BakedSheet` fixture the way existing `mods.code` tests do (e.g. whatever `TestModArtOverrides` uses — reuse its helper/idiom). Run: `mvn "-Dtest=com.openggf.mods.code.TestStandaloneObjectArtWiring" test` — expect compile failure (5-arg wrap missing).

- [ ] **Step 3: Implement** — `EmptyObjectArtProvider` (neutral implementation per Step 1's inventory); extend `wrap` to 5 args, keeping the 4-arg overload (test callers exist); in `BoundaryHandler.invoke`, intercept `getObjectArtProvider`: invoke the delegate's method through the boundary, decorate its result (null → `EmptyObjectArtProvider` base) with the prepared sheets, cache the decorated provider (lazily, consistent with the handler's idioms), and return it directly (engine-owned, not re-proxied).

- [ ] **Step 4: Green + regression** — Run the new test, plus `mvn "-Dtest=com.openggf.mods.integration.TestPhase3StandaloneSampleIntegration" test` (art-less standalone parity) and `mvn "-Dtest=com.openggf.mods.integration.TestSamplePlatformerIntegration" test` (must still pass — the sample still has its hand-rolled provider at this point; under decoration semantics its provider becomes the BASE with the same sheets overlaid on top, which is harmless key-shadowing; verify and note it).

- [ ] **Step 5: Commit** — `feat: wire standalone registerObjectArt sheets through the module proxy` (+ trailer block, `Changelog: n/a: covered by aggregate mod-gap-fixes CHANGELOG entry in the final task`).

---

### Task 2: Migrate sample-platformer to the engine path

**Files:**
- Modify: `src/test/resources/mods/sample-platformer-src/project/src/main/java/example/platformer/PlatformerModule.java` (delete field L47, sheet-map ctor param L49-54, `getObjectArtProvider()` override L71-81, inner `SheetBackedObjectArtProvider` L188-228)
- Modify: `.../example/platformer/PlatformerMod.java` (drop `buildObjectSheets` arg at L40-41 and the helper at L47-65; `registerObjectArt` calls at L35/L37 stay)
- Modify: `src/test/java/com/openggf/mods/integration/TestSamplePlatformerIntegration.java` (provider assertions L225-233 + javadoc L190-201: assert the provider exists and serves both namespaced keys WITH the module no longer overriding anything — add an explicit assertion that `PlatformerModule` does not override `getObjectArtProvider` (e.g. reflect `getDeclaredMethod` throws NoSuchMethodException) so the fixture can never silently regress to hand-rolling)
- Modify: `docs/modding/guides/standalone-platformer.md:557-589` (rewrite the "Why a standalone module has to serve its own object art" section: `registerObjectArt` now just works; keep a short historical note pointing at the fixed backlog row) and touch the `registerObjectArt` references at L96/L98/L107 for consistency.

- [ ] **Step 1: Make the test assert the engine path first** (it should pass already if Task 1's precedence is engine-side; if the module override wins instead, this red test drives the deletion), then delete the hand-rolled code, then green: `mvn "-Dtest=com.openggf.mods.integration.TestSamplePlatformerIntegration" test` and `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test`.
- [ ] **Step 2: Rewrite the guide section** — accurate to the shipped engine behavior; code excerpts verbatim from the now-simplified sample.
- [ ] **Step 3: Commit** — `fix: sample-platformer rides the engine object-art wiring` (trailers all n/a — test resources + docs).

---

### Task 3: Gap B engine hooks

**Files:**
- Modify: `src/main/java/com/openggf/level/objects/PerObjectRewindSnapshot.java` (new nested `@ModApi` marker interface `PlayableSubclassRewindExtra` beside `BadnikSubclassRewindExtra` at ~L148; new LAST component `PlayableSubclassRewindExtra subclassExtra` on the `PlayerRewindExtra` record at L350-459 + explicit compat constructor preserving the previous canonical parameter list (third use of the trick — see the existing compat ctor at ~L470 for the idiom); thread through the compact ctor's defensive-copy section unchanged)
- Modify: `src/main/java/com/openggf/sprites/playable/AbstractPlayableSprite.java` (hooks + capture at the single build site `captureRewindState(boolean)` L917 (payload into the new component at ~L998), restore invocation inside `restoreRewindState` L1023+ — invoked ALWAYS, passing the stored value which may be null)
- Test: `src/test/java/com/openggf/sprites/playable/TestPlayableSubclassRewind.java`

**Interfaces:**
- Produces (published in Task 4): `PerObjectRewindSnapshot.PlayableSubclassRewindExtra` (marker, Javadoc mandates immutability — in-memory snapshot graph, no defensive copying of payloads); `protected PlayableSubclassRewindExtra AbstractPlayableSprite.captureSubclassRewindState()` default `null`; `protected void restoreSubclassRewindState(PlayableSubclassRewindExtra extra)` default no-op; `PlayerRewindExtra.subclassExtra()`.

- [ ] **Step 1: Read** `PerObjectRewindSnapshot.java` (record decl, both existing compat ctors, marker interfaces), `AbstractPlayableSprite.java:913-1100` (exact build/restore sites, how `sidekickCpuExtra` is captured at L918-919 and restored), and one existing sprite-level test for construction idioms (how tests instantiate a playable sprite headlessly — e.g. whatever `TestSamplePlatformerIntegration` or physics tests do; a minimal local subclass with stub sensors is fine if a precedent exists).
- [ ] **Step 2: Failing test** — a test-local subclass with one `int` of state implementing the hooks via an immutable record payload: (a) `captureRewindState()` stores the payload (assert via `snapshot.playerExtra().subclassExtra()`); (b) mutate the field, `restoreRewindState(snapshot)` restores it; (c) a subclass NOT overriding the hooks round-trips with `subclassExtra() == null` and no throw; (d) the preserved old canonical `PlayerRewindExtra` ctor still constructs (compile-level assertion — call it with the old arg list, assert `subclassExtra() == null`).
- [ ] **Step 3: Implement, green** — `mvn "-Dtest=com.openggf.sprites.playable.TestPlayableSubclassRewind" test`; sanity `mvn "-Dtest=TestPhysicsProfile" test` for no playable-sprite regression; do NOT run the signature guard (red until Task 4).
- [ ] **Step 4: Commit** — `feat: subclass rewind capture hooks for playable sprites` (+ trailers, aggregate-changelog justification).

---

### Task 4: Surface refreeze → Mod API 2.2.0

**Files:**
- Modify: `src/main/java/com/openggf/mods/ModApiVersion.java:39` (→ `new SemanticVersion(2, 2, 0)`, lineage Javadoc entry)
- Create: `src/test/resources/mods/mod-api-signatures-2.2.txt`
- Modify: `src/test/java/com/openggf/mods/TestModApiSignatureSurface.java` (`PUBLISHED_BASELINE`/`PUBLISHED_VERSION` constants at L38/L43; pin test `publishedTwoOneSurfaceIsPinnedToTheCurrentSurface` L106 renamed/re-pointed; `twoZeroToTwoOneIsAnAdditiveMinorBump` L170 re-scoped to closed historical baselines with 2.1 frozen at 17,196 (L179) and a NEW `twoOneToTwoTwoIsAnAdditiveMinorBump` test — follow exactly the structure the 2.0→2.1 bump used, visible in that file's git history at commit 5096befbd)
- Modify: `docs/architecture/mod-api-compatibility.md` (2.2.0 lineage entry: additive playable-subclass rewind hooks, old `PlayerRewindExtra` canonical ctor preserved)
- Modify: collateral version-literal tests if any assert 2.1.0 (`TestSemanticVersionAndRange`, `TestEffectiveCatalogBuilder` — same two files the 2.1 bump touched; rebase literals preserving intent)

- [ ] **Step 1** — run `mvn "-Dtest=com.openggf.mods.TestModApiSignatureSurface" test`, inspect EVERY added line: must be exactly the Task 3 additions (marker type, two protected methods, `subclassExtra` accessor + new canonical ctor). `EmptyObjectArtProvider` or anything Gap-A must NOT appear.
- [ ] **Step 2** — refreeze per the file's own documented procedure; keep `mod-api-signatures-2.1.txt` untouched.
- [ ] **Step 3** — green: signature surface, `TestModApiJavadocTool`, `TestModApiSdkPackager`, `TestSemanticVersionAndRange`, `TestEffectiveCatalogBuilder`, `TestSampleModsPackage` (samples declare `>=2.0.0 <3.0.0` and `>=2.1.0 <3.0.0` — both satisfied by 2.2.0).
- [ ] **Step 4: Commit** — `feat: publish Mod API 2.2.0 with playable subclass rewind surface`.

---

### Task 5: Bolt migrates to the production rewind path

**Files:**
- Modify: `src/test/resources/mods/sample-platformer-src/project/src/main/java/example/platformer/BoltCharacter.java` (implement both hooks with `private record BoltRewindExtra(boolean doubleJumpUsed) implements PerObjectRewindSnapshot.PlayableSubclassRewindExtra`; restore tolerates null (fresh default); the landing-clear in `draw()` L80-83 STAYS — it is gameplay logic; update the L28-34 field javadoc to describe the production capture path)
- Modify: `src/test/java/com/openggf/mods/integration/TestSamplePlatformerIntegration.java` (`exerciseDoubleJumpAndRewindLatch` L386-411: replace the `GenericFieldCapturer`/`GenericObjectSnapshot` scaffold (imports L25-27) with the production round-trip — `var snap = player.captureRewindState(); /* mutate latch via a fresh jump+ability */ player.restoreRewindState(snap); assert latch restored`; rewrite the L374-384 javadoc which currently documents the limitation)

- [ ] **Step 1** — red first: the migrated test against pre-hook Bolt fails (latch not restored); then implement Bolt's hooks; green: `mvn "-Dtest=com.openggf.mods.integration.TestSamplePlatformerIntegration" test` + `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test` (revalidates the mod source through ModValidator).
- [ ] **Step 2: Commit** — `fix: Bolt double-jump latch rides production rewind capture` (trailers all n/a — test resources + test code).

---

### Task 6: Docs closure + full verification

**Files:**
- Modify: `docs/modding/characters.md:192-202` (replace the known-limitation block with documentation of the two hooks + the immutability contract + a short Bolt excerpt)
- Modify: `docs/modding/BACKLOG.md` rows at ~L106 and ~L119 (verdicts → **Delivered 2026-07-14** with one-line pointers to the fix commits/tests; or move to the sweep-reconciliation prose — match the file's own conventions for delivered items, read how it handles them)
- Modify: `docs/modding/guides/standalone-platformer.md` (rewind chapter references the hooks; confirm Task 2's object-art section landed), `docs/modding/content-mods.md` / `docs/modding/standalone-games.md` if they describe the old null-provider behavior (grep `getObjectArtProvider` / `registerObjectArt` in docs/modding)
- Modify: `CHANGELOG.md` (two entries: standalone object-art wiring fix; Mod API 2.2.0 subclass rewind hooks), `CLAUDE.md` + `AGENTS.md` (2.1.0 prose → 2.2.0 — same sentence updated by the previous project; trailer `Agent-Docs: updated`)

- [ ] **Step 1** — docs edits per above (read each target section first; preserve line-ending style).
- [ ] **Step 2** — full `mvn test`: expect zero new failures vs the branch baseline (13,155-level green; report actual totals honestly, noting any known transient flakes distinctly).
- [ ] **Step 3: Commit** — `docs: close out mod gap fixes; changelog for Mod API 2.2.0` (trailers: `Changelog: updated`, `Agent-Docs: updated`, `Guide: n/a`, rest n/a).

---

## Plan-level notes for the executor

- Under decoration semantics Task 2's test change starts GREEN (the overlay serves the keys whether or not the hand-rolled base exists) — the deletion in Task 2 is still mandatory, and its no-override reflection assertion is what prevents regression.
- The 2.2.0 refreeze reuses the exact procedure from commit `5096befbd` (the 2.0→2.1 bump) — read that commit's diff of `TestModApiSignatureSurface.java` as the template.
- `PlayerRewindExtra` has ~145 components; when appending the new one, use an IDE-safe mechanical edit and let the compiler find every construction site (the compat ctor covers external callers; internal build sites in `AbstractPlayableSprite` move to the new canonical).
- After the final task: merge `feature/ai-mod-gap-fixes` into `next` and push (standing user directive).
