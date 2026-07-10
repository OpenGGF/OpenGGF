# Mod Support Phase 2 (Additive Content Mods) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mod jars ship compiled Java (objects/badniks, a `GamePatch`), baked art (reskins + new tilesets), and a new zone appended to an existing game; a `ggfmod` toolchain converts and validates.

**Architecture:** Six workstreams per the Phase 2 spec: (1) code loading + trust, (2) objects + art + pattern budget, (3) new-zone seams (`LevelDescriptor` retrofit, `ZoneProgressionPlan`, in-memory `Sonic2Level` overload, `ModZoneLoader`), (4) `ggfmod` SDK/CLI, (5) editor mod-facing features, (6) the sample-mod integration gate.

**Tech Stack:** Java 21, Jackson, `org.ow2.asm:asm` (NEW dependency, declared in the spec §E), JUnit 5.

**Specs:** `docs/superpowers/specs/2026-07-09-mod-support-phase2-design.md` (authoritative), parent `2026-07-09-mod-support-design.md`, Phase 0 spec, Phase 1 plan.

## CONTINGENCY PREAMBLE — read before executing anything

This plan is written against the **Phase 0 spec and Phase 1 plan interfaces, which are not implemented at plan-authoring time (2026-07-09)**. Every task consuming a Phase 0/1 type carries a **[P0]** / **[P1]** marker; its first step is always *re-verify the landed interface* (name, package, signature) and adapt mechanically — the landed code is authoritative over this plan's spelling of it. If a landed interface diverges structurally (not just in spelling), STOP and update this plan first. Do not begin this plan until Phase 0 (all three workstreams) and Phase 1 have merged to `develop`.

**Calibration note:** tasks creating pure new classes contain complete code; tasks modifying engine files or consuming unlanded interfaces are contract-and-anchor style (the Phase 0 plan's workstream-A convention) with mandatory read-first steps. That is deliberate — full code against unlanded dependencies would be false precision.

## Global Constraints

- **JUnit 5 only. Never `git add -A`. No new singletons.** Commit trailers per repo policy; intermediate `feat` commits touching `src/main/` use `Changelog: n/a: covered by final phase-2 changelog entry in this branch`.
- **New Maven dependency:** exactly one — `org.ow2.asm:asm` (latest stable), added in Task 16 with the spec citation. Nothing else.
- **Branch:** `feature/ai-mod-support-phase2` off `develop`.
- **ArchUnit:** new engine packages in this plan (`com.openggf.mods.code`, `com.openggf.tools.modsdk`) plus new cross-slice references will trip the `CORE_RUNTIME_TOP_LEVEL_DEPENDENCY_EDGES` ratchet. The rule's failure message lists exactly the missing edges; add precisely those to the allowlist with a comment citing this plan (expected at minimum: `mods -> game` for patch registration, `game -> level` already exists — verify, don't guess).
- **Task order:** workstreams are sequenced 1 → 2 → 3 → 5 → 4 → 6 (the CLI's `convert level` needs the editor export; the sample mod needs everything). Within a workstream, order as written.
- **Regression gates** (every workstream-final task): full default suite, S3K must-keep-green set, and the trace spot sweep (s1_ghz1 / s2_ehz1 / s3k_aiz1) with mods force-disabled — bit-identical expectations. Log sweeps in `docs/TRACE_FRONTIER_LOG.md`.

---

## Workstream 1 — code loading + trust

### Task 1: `ModClassLoaderFactory` — delegating loaders per code mod **[P1]**

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModClassLoaderFactory.java`, `ModDependencyClassLoader.java`
- Test: `src/test/java/com/openggf/mods/code/TestModClassLoaders.java`

**Interfaces:**
- Consumes: `ModDescriptor`/`ModCatalog.orderedEnabled()` [P1 — re-verify].
- Produces: `ModClassLoaderFactory.createLoaders(List<ModDescriptor> orderedEnabled)` → `Map<String, ClassLoader>` keyed by mod id. Each `ModDependencyClassLoader extends URLClassLoader` (parent = engine loader): `findClass` first tries the mod's own jar, then consults the loaders of the mod's **declared** dependencies in manifest order; undeclared siblings invisible. (Class delegation only — mod *assets* travel via jar `Path`/`ModAssetSource`, not classpath resources; add a matching `findResource` override only if a real consumer appears, and note it in the class Javadoc.) Loaders are created in catalog order so dependency loaders exist first (topological order is a Phase 1 catalog invariant — re-verify).

- [ ] **Step 1: Write the failing test.** Build two jars in `@TempDir` (Phase 1's `writeJar` helper idiom): `lib.jar` with class `libmod.LibClass` and `app.jar` with `appmod.AppClass` whose manifest declares `dependencies: [lib]`. Compile the two classes in the test via `javax.tools.JavaCompiler` (JDK is guaranteed — the repo builds with one; keep sources tiny, e.g. `public class LibClass { public static String ping() { return "lib"; } }`). Assert: app's loader loads `appmod.AppClass` and can resolve `libmod.LibClass`; a third mod without the dependency declaration cannot resolve `libmod.LibClass` (`ClassNotFoundException`); engine classes (e.g. `com.openggf.level.objects.ObjectSpawn`) resolve through the parent from any mod loader.
- [ ] **Step 2:** COMPILE FAILURE, then implement. Core of `ModDependencyClassLoader`:

```java
package com.openggf.mods.code;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * Parent-first to the engine, own-jar next, declared dependencies last.
 * Undeclared sibling mods are not visible (parent spec section 2).
 */
public final class ModDependencyClassLoader extends URLClassLoader {
    private final List<ClassLoader> dependencyLoaders;

    public ModDependencyClassLoader(String modId, URL[] jarUrls, ClassLoader engineParent,
                                    List<ClassLoader> dependencyLoaders) {
        super("mod:" + modId, jarUrls, engineParent);
        this.dependencyLoaders = List.copyOf(dependencyLoaders);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name); // own jar
        } catch (ClassNotFoundException ownMiss) {
            for (ClassLoader dep : dependencyLoaders) {
                try {
                    return dep.loadClass(name);
                } catch (ClassNotFoundException ignored) {
                    // try next dependency
                }
            }
            throw ownMiss;
        }
    }
}
```

- [ ] **Step 3:** Tests PASS. Commit (`feat: delegating mod classloaders`).

---

### Task 2: `GgfMod` + `ModContext` + entrypoint lifecycle **[P0][P1]**

**Files:**
- Create: `src/main/java/com/openggf/mods/code/GgfMod.java`, `ModContext.java`, `ModRegistrationException.java`, `ModCodeRuntime.java`, `BakedSheetRef.java`
- Modify: `src/main/java/com/openggf/mods/ModManifest.java`, `ModManifestParser.java` [P1]
- Test: `src/test/java/com/openggf/mods/code/TestModCodeRuntime.java`; extend `TestModManifestParser`

**Commit split (right-sizing):** this task lands as TWO commits — (1) `feat: phase-2 mod manifest schema fields` (schema + `BakedSheetRef` + parser tests; no runtime code), then (2) `feat: mod code entrypoint runtime and patch registration` (runtime + enablement). The schema commit has no dependency on the runtime and is consumed by Tasks 7/8/12 independently.

**Interfaces:**
- Consumes: `GamePatch`/`GamePatchRegistry`/`PatchEnablement` [P0 — re-verify the landed static facade], `ModCatalog`/`ModManifest` [P1], Task 1 loaders.
- Produces:
  - **Manifest schema extensions [P1 — this task owns them]:** `ModManifest` + `ModManifestParser` gain the Phase 2 fields the parser currently ignores as unknown keys: `entrypoint` (optional FQN string), `artOverrides` (typed `Map<String,String>` base-art-key → sheet asset path), `insertAfter` (optional stock-zone name, Task 12 consumes), `patternWindows` (int, default 1, Task 8 consumes). Parser tests extended; Phase 1's `ignoresUnknownKeysForForwardCompat` keeps passing for still-future keys.
  - `record BakedSheetRef(String entryPath)` in `com.openggf.mods.code` — a thin jar-entry pointer defined HERE. Task 6's engine-side reader takes raw bytes/streams; mod-side code resolves a `BakedSheetRef` to a stream before calling it (keeps the dependency direction `mods -> level`, never the reverse).
  - `public interface GgfMod { void register(ModContext ctx); }`
  - `ModContext` (one per mod, constructed by the runtime): `registerGamePatch(GamePatch)` (validates `patch.baseGameId()` equals the manifest `baseGame`, else `ModRegistrationException`); `registerObject(String key, int requestedId, ObjectFactory factory)` and `registerObjectArt(String key, BakedSheetRef ref)` (both record into the mod's pending-content set consumed by its patch — spec §A: content registration outside a patch is invalid, enforced when the patch applies); `modAssets()` returning the mod's jar `Path` for `LoadSource.ModAsset` factory use [P0].
  - `ModCodeRuntime.loadAll(ModCatalog, Map<String, ClassLoader>)` — for each enabled code+trusted mod with an `entrypoint`: load the class on the mod's loader, require `GgfMod`, instantiate (no-arg ctor), call `register(ctx)`. **Failure handling (spec §A):** any throw → mod disabled for the session, manager badge with the message, engine continues. The runtime also records every registered class in a plain `Map<String, Class<?>>` field — **stub now, wire later:** Task 4's `ModClassResolver` wraps that map; nothing in this task references the Task 4 type.
  - The mod-backed `PatchEnablement` implementation (Phase 0 Amendment 2's pinned contract): managed ids = mod patch ids, enabled per catalog + trust; unknown ids (built-ins) → enabled, `UNMANAGED_ORDER`. Installed via `GamePatchRegistry.setEnablement(...)` at the Phase 1 Engine wiring point.

- [ ] **Step 1:** Re-verify landed `GamePatch`/`GamePatchRegistry`/`PatchEnablement` signatures and Phase 1's `ModCatalog`/`ModDescriptor` shapes.
- [ ] **Step 2:** Failing tests: entrypoint happy path (test jar with a compiled `GgfMod` impl registering a stub patch → patch visible to `GamePatchRegistry`); baseGame mismatch → `ModRegistrationException`, mod disabled with reason; entrypoint throwing → disabled, engine continues; catalog-backed enablement honors the pinned unknown-id contract (built-in id enabled + `UNMANAGED_ORDER`).
- [ ] **Step 3:** Implement; tests PASS; commit (`feat: mod code entrypoint runtime and patch registration`).

---

### Task 2b: `@ModApi` surface + signature-snapshot guard + API version bump **[P0][P1]**

**Files:**
- Create: `src/main/java/com/openggf/game/ModApi.java`
- Modify: apply the annotation to the spec §G curated set (`GgfMod`, `ModContext`, `GamePatch`/`PatchContext`/`GameplayLaunchRequest`, `ObjectServices`, `AbstractObjectInstance`, `AbstractBadnikInstance`, `ObjectSpawn`, `ObjectControlState`, `ObjectLifetimeOps`, `ObjectPlayerQuery`, `PhysicsProfile`, the `level.objects` utility helpers, `BakedSheetRef` (exists as of Task 2); `BakedSheetReader`/`ModLevelDefinition` once they exist — later tasks add their own annotations as they create types); `src/main/java/com/openggf/mods/ModApiVersion.java` [P1]
- Test: `src/test/java/com/openggf/game/TestModApiSurfaceSnapshot.java`

**Contract (spec §G last bullet):**
- `@Retention(CLASS) @Target({TYPE}) public @interface ModApi {}` with Javadoc stating the compatibility promise.
- Signature-snapshot guard: reflect over every `@ModApi`-annotated type on the classpath, serialize its public members (name, params, return, modifiers) to a sorted text form, compare against a checked-in baseline resource (`src/test/resources/modapi/surface-baseline.txt`). **Additions allowed** (the test regenerates and tells you to update the baseline); **removals/changes fail** with the diff. Follow the rewind coverage-baseline guard idiom.
- **API version bump with backward acceptance:** `ModApiVersion.CURRENT_MAJOR` 1 → 2, and `isSatisfiedBy` widens from equality to `1 <= required <= CURRENT_MAJOR` — a Phase 1 music pack declaring `"1"` MUST keep loading (its formats are unchanged); Phase 2 code mods declare `"2"`. Update Phase 1's `apiVersionCompatibility` test accordingly (`"1"` true, `"2"` true, `"3"` false).

- [ ] Steps: re-verify landed `ModApiVersion` → failing tests (snapshot guard against a seeded baseline; version acceptance triple) → implement + annotate the already-existing types → PASS → commit (`feat: @ModApi surface, signature snapshot guard, API major 2`).

---

### Task 3: Trust gate **[P1]**

**Files:**
- Modify: `src/main/java/com/openggf/mods/ModState.java`, `ModStateStore.java`, `ModEligibility.java`, `ModCatalog.java`, `src/main/java/com/openggf/mods/ui/ModManagerScreen.java`
- Test: extend `TestModCatalog`, `TestModManagerScreen`, `TestModStateStore`

**Contract (spec §F):**
1. `ModState.Entry` gains `trusted` (boolean) + `trustedJarSha256` (nullable String). Store parsing is map-shaped [P1 fact] — added fields tolerated both directions; write both fields.
2. `ModEligibility`'s `"mod code is not supported yet"` rule is **replaced**: `containsCode && !trustedAndHashMatches` → block reason `"contains code — trust required (press accept twice)"`. Hash computed once per scan (SHA-256 of the jar), compared to `trustedJarSha256`; mismatch clears the persisted grant.
3. `ModManagerScreen.activateSelected()` on an untrusted code mod arms a trust confirm (reuse the cascade-confirm arm/commit mechanics and its disarm-on-cursor-move); second press records `trusted=true` + the hash and enables.
4. Tests: grant flow; hash-mismatch revocation; data-only mods unaffected; v1 `modstate.json` (no trust fields) loads with `trusted=false`.

- [ ] Steps: re-verify landed Phase 1 files → failing tests → implement → PASS → commit (`feat: mod code trust gate with jar-hash revocation`).

---

### Task 4: `ModClassResolver` + loader-aware rewind recreate

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModClassResolver.java`
- Modify: `src/main/java/com/openggf/level/objects/ObjectRewindDynamicCodecs.java` (~line 65)
- Test: `src/test/java/com/openggf/mods/code/TestModClassResolverRecreate.java`

**Contract (spec §B fix 1):** `ObjectRewindDynamicCodecs.genericRecreate` currently does loader-less `Class.forName(entry.className())` (recon: ObjectRewindDynamicCodecs.java:65). Change: consult an injected `ModClassResolver` first (a name → `Class<?>` map populated by `ModCodeRuntime` at registration; static-free — threaded through the existing recreate context or a registry seam, read the file to pick the cleanest injection point), falling back to `Class.forName` for engine classes. `ModClassResolver.EMPTY` for mod-less sessions — behavior byte-identical.

- [ ] Steps: read `ObjectRewindDynamicCodecs` + its recreate-context plumbing → failing test (a class from a child loader recreates via resolver; an engine class still resolves via fallback; EMPTY resolver = today's behavior) → implement → PASS → run the rewind test suites (`mvn "-Dtest=com.openggf.game.rewind.*" test` adjusted to real class names) → commit (`fix: loader-aware rewind recreate for mod object classes`).

---

## Workstream 2 — objects, art, pattern budget

### Task 5: Deterministic object-id allocator + decorated registry

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModObjectIdAllocator.java`, `ModDecoratedObjectRegistry.java`
- Test: `src/test/java/com/openggf/mods/code/TestModObjectIdAllocator.java`

**Contract (spec §B):**
- Input: the base registry's registered id set (read via a new `AbstractObjectRegistry` accessor `Set<Integer> registeredIds()` — small engine addition, read the class first) + the ordered list of (modId, objectKey, requestedId) registrations.
- Allocation: honor `requestedId` when free; otherwise lowest free byte; iteration order = mod enable order, then key lexicographic. Same input → same output (test: shuffled registration arrival order yields identical allocation). Exhaustion → the mod is blocked with a manager badge (throw a typed exception the runtime converts).
- `ModDecoratedObjectRegistry` wraps the base registry: `create(spawn)` consults mod factories for allocated ids first, else delegates; `getPrimaryName` returns the namespaced key for mod ids. The mod's `GamePatch` returns it from `createObjectRegistry()`.
- Key→id lookup exposed for §D spawn resolution and §G envelope apply: `OptionalInt idFor(String namespacedKey)`.

- [ ] Steps: failing tests (determinism incl. shuffle, requestedId honor + conflict fallback, exhaustion, delegation) → implement (pure logic — complete code expected here, ~80 lines) → PASS → commit (`feat: deterministic mod object id allocation and registry decoration`).

---

### Task 6: Baked sheet container — engine reader + modsdk writer

**Files:**
- Create: `src/main/java/com/openggf/level/objects/BakedSheetReader.java` (NO ref type here — `BakedSheetRef` was created in Task 2 in `mods.code`; the reader takes **raw inputs** (`InputStream` or `byte[]`), and ref→stream resolution stays mod-side, so no `level -> mods` dependency edge is created)
- Create: `src/main/java/com/openggf/tools/modsdk/BakedSheetWriter.java`
- Test: `src/test/java/com/openggf/tools/modsdk/TestBakedSheetRoundTrip.java`

**Format (spec §C, versioned container, one file per sheet):**

```
magic "GGFSHEET" (8 bytes) | u16 version=1 | u16 patternCount
| patternCount * 32 bytes (Sega 4bpp, Pattern.fromSegaFormat layout)
| u16 frameCount | per frame: u16 pieceCount, per piece:
    s16 xOffset, s16 yOffset, u8 widthTiles, u8 heightTiles,
    u16 tileIndex, u8 flags (bit0 hFlip, bit1 vFlip, bit2 priority),
    u8 paletteIndex
| u8 paletteLineIndex | u8 frameDelay
| u8 hasPalette (0/1) | if 1: 16 * u16 palette entries (Mega Drive 9-bit color words)
```

All multi-byte values big-endian. Reader materializes `ObjectSpriteSheet(Pattern[] via fromSegaFormat, List<SpriteMappingFrame>, paletteLineIndex, frameDelay)`; unknown version → typed exception (mod load skips the sheet with a badge, never crashes).

- [ ] Steps: failing round-trip test (writer → bytes → reader → field-equality against the source `SpriteMappingPiece` list and pattern pixels; bad magic / future version rejected) → implement both sides (complete code expected — pure serialization) → PASS → commit (`feat: baked sprite sheet container with modsdk writer`).

---

### Task 7: Art overrides + implicit data-only patch **[P0][P1]**

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModArtOverlayProvider.java` (decorates a base `ObjectArtProvider`)
- Modify: `ModCodeRuntime` (synthesize an implicit patch for data-only mods with `artOverrides`)
- Test: `src/test/java/com/openggf/mods/code/TestModArtOverrides.java`

**Contract (spec §C):** the decorated provider's `getRenderer(key)`/`getSheet(key)` serve the mod sheet for overridden keys (loaded lazily via `BakedSheetReader` from the mod jar), else delegate; later mods win. A data-only mod with `artOverrides` and no entrypoint gets a synthesized minimal `GamePatch` (always-activates for its `baseGame`) that installs just the art decoration — keeping "content always scopes via a patch" (§A) without requiring code.

- [ ] Steps: re-verify `ObjectArtProvider` surface → failing tests (override served, later-wins, missing sheet asset → skip with logged reason + base art) → implement → PASS → commit (`feat: mod art overrides via decorated art provider`).

---

### Task 8: PatternAtlas governance + per-mod windows **[P1]**

**Files:**
- Modify: `src/main/java/com/openggf/graphics/PatternAtlas.java` (`validatePatternIdGovernance` ~584-594, `registerRange(int,int,String)` ~144)
- Create: `src/main/java/com/openggf/mods/code/ModPatternWindowAllocator.java`
- Test: `src/test/java/com/openggf/graphics/TestPatternAtlasDynamicRanges.java`

**Contract (spec §C, engine change):** governance accepts ids inside dynamically registered ranges (today: enum-only via `PatternAtlasRange.forPatternId`); `registerRange(int,int,String)` enforces block alignment like the enum tier. `ModPatternWindowAllocator`: one `0x8000` window per enabled mod from `0x108000` upward in enable order, extra windows by manifest `patternWindows` declaration (Task 2's schema field), registered as `"mod:" + id`. **Budget display (spec §C last sentence):** `ModManagerScreen` [P1] rows for code/art mods show window count (e.g. `[2 windows]`) sourced from the allocator — one extra rendered segment, logic unit-tested like the screen's other row fields.

- [ ] Steps: read the two PatternAtlas regions → failing tests (dynamic-range id passes governance; unregistered id above 0x108000 still fails; misaligned dynamic registerRange throws; allocator hands out sequential windows) → implement → PASS → run graphics/pattern test suites → commit (`feat: dynamic PatternAtlas ranges for per-mod pattern windows`).

---

## Workstream 3 — new zone

### Task 9: `LevelDescriptor` retrofit

**Files:**
- Create: `src/main/java/com/openggf/level/LevelDescriptor.java`
- Modify: `src/main/java/com/openggf/level/LevelData.java` (implements it), `src/main/java/com/openggf/game/ZoneRegistry.java` + `AbstractZoneRegistry.java` (+ the three per-game registries' generics/signatures as the compiler demands)
- Test: `src/test/java/com/openggf/level/TestLevelDescriptorRetrofit.java`

**Contract (spec §D.1):** `interface LevelDescriptor { int levelIndex(); int startX(); int startY(); }`; `LevelData` implements it delegating to its existing getters (recon: no switch/EnumMap/values() consumers exist, retrofit is mechanical). Registries type against the interface. Synthetic descriptors use indices ≥ 0x400 (clear of all enum bands, max 0xEA). Stock behavior bit-identical (the retrofit test asserts every `LevelData` constant round-trips identically through the interface).

- [ ] Steps: grep for every `LevelData` consumer → failing test → mechanical retrofit → full `mvn package` + zone-loading tests → commit (`refactor: LevelDescriptor interface over LevelData for mod zones`).

---

### Task 10: `ZoneProgressionPlan` seam

**Files:**
- Create: `src/main/java/com/openggf/game/ZoneProgressionPlan.java`
- Modify: `src/main/java/com/openggf/level/LevelManager.java` (`advanceToNextLevel` ~2717-2732)
- Test: `src/test/java/com/openggf/game/TestZoneProgressionPlan.java`

**Contract (spec §D.3):** `ZoneProgressionPlan.next(int zone, int act)` → `Successor(zone, act)` or `CREDITS`. Default `LINEAR` implementation reproduces today's `currentAct++ / currentZone++ / credits-at-end` logic bit-identically (test: exhaustive walk over an 11-zone fixture matches old behavior). `advanceToNextLevel` consults the plan (module-provided via a new `GameModule` default method `getZoneProgressionPlan()` → `LINEAR`; a patch overrides it). **Document in the seam's Javadoc:** `advanceZoneActOnly()` (S1 big-ring path, S1-only caller) bypasses the plan by design — S1 mod zones are out of scope; the plan seam is the progression authority for everything else.

- [ ] Steps: read `advanceToNextLevel` + `advanceZoneActOnly` → failing tests (LINEAR equivalence; a redirect plan routes MTZ→11→SCZ) → implement → PASS → trace spot sweep (progression untouched for stock) → commit (`feat: ZoneProgressionPlan seam for mod zone reachability`).

---

### Task 11: `Sonic2Level` in-memory data overload **[P0]**

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/Sonic2Level.java`
- Test: `src/test/java/com/openggf/game/sonic2/TestSonic2LevelInMemoryConstruction.java`

**Contract (spec §D.2 engine change):** a builder/overload accepting: the `LevelResourcePlan` (as today) plus **in-memory** layout bytes, 4×16 palette data, solid-tile heightmap/angle bytes, boundaries, and the spawn lists — reusing the existing decode methods' logic (extract the byte-level decode from the ROM-read wrappers where needed; the ROM-address constructors delegate to the same decode). Requires a ROM only for what the plan's ROM ops need (a mod-asset-only plan needs none — pass the nullable Rom the Phase 0 loader already tolerates).

- [ ] Steps: **re-verify the landed Phase 0 loader's null-Rom behavior first** — the "nullable Rom" claim comes from the Phase 0 plan's Task B2 contingency note, not a pinned spec guarantee; if the landed `ResourceLoader` rejects null, add its `forModAssetsOnly()` factory here → read `Sonic2Level` fully (both constructors + every load method) → failing test (construct from fixture in-memory data + a tiny ModAsset plan in `@TempDir`; assert map/palette/solid-tile/boundary getters) → implement by extraction, not duplication → PASS → HTZ trace gate (the plan-constructor zone) + S2 zone-loading tests → commit (`feat: in-memory level data construction for mod zones`).

---

### Task 12: `ModLevelDefinition` + `ModZoneLoader` + music routing **[P0]**

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModLevelDefinition.java` (+ parser), `ModZoneLoader.java`
- Test: `src/test/java/com/openggf/mods/code/TestModZoneLoader.java`

**Contract (spec §D.2):** `ModLevelDefinition` (YAML or the baked-container idiom — pick YAML for the metadata + separate binary assets for bulk data, consistent with §C): zone name, synthetic zoneIndex (0x40+ band), acts (each: plan asset entries for the four kinds, layout asset, palette asset, solid-tile asset, boundaries, start position, music id, spawn lists with object entries by namespaced key). `ModZoneLoader` resolves keys through Task 5's allocator, builds the Task 11 level, and the mod's patch decorates `loadLevel`/`getMusicId` to route synthetic indices (≥0x400) to it. Appended registry entries (Task 9 descriptors) + a `ZoneProgressionPlan` redirect (Task 10) complete the wiring; `insertAfter` validation refuses event-chained zones at load.

- [ ] Steps: re-verify Tasks 5/9/10/11 landed shapes → failing tests (definition parse + validation errors; loader builds a playable-shape level from fixture assets; key resolution; music id routing; insertAfter refusal) → implement → PASS → commit (`feat: mod level definition and zone loader`).

---

### Task 13: Title card fallback + data-select extension

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/titlecard/TitleCardManager.java` (~182, ~392), `src/main/java/com/openggf/game/sonic2/dataselect/S2DataSelectProfile.java` (+ preview generator as needed)
- Test: focused tests per file

**Contract (spec §D.4 + §D.3):** title card name resolves via `ZoneRegistry.getZoneName` when the index exceeds the hardcoded array (also fixing the wrong-name `ZONE_NAMES[0]` fallback); if the ROM letter-art set can't render the name, skip the title card for that zone. The skip is a **new config flag** — full repo obligations apply: `SonicConfiguration` constant + `ConfigCatalog` meta (`TestConfigCatalog` enforces) + `CONFIGURATION.md` entry + `Configuration-Docs: updated` trailer on this task's commit. Data-select: indices ≥ stock count get the generic mod-zone preview tile + registry-driven restart; absent-mod slots restart at zone 0 with a logged notice (parent §7 preservation). **All three code areas are in scope, read-first:** `TitleCardManager` (~182/~392), `S2DataSelectProfile` (`CLEAR_RESTARTS` bounds checks), and `S2DataSelectImageCacheManager` (`EXPECTED_ZONE_KEYS` fixed zone-key set — the preview cache must tolerate/serve the generic tile for mod-zone keys); trace the restart-resolution consumer from `S2DataSelectProfile`'s restart indices before changing it.

- [ ] Steps: read the three files + the restart consumer → failing tests → implement → PASS → commit (`feat: registry-driven title card and data-select handling for mod zones` with `Configuration-Docs: updated`).

---

## Workstream 5 — editor mod-facing features (before workstream 4: the CLI consumes the export)

### Task 14: Full-level export + envelope `objectKey` **[P0]**

**Files:**
- Create: `src/main/java/com/openggf/editor/persistence/FullLevelExporter.java`
- Modify: the Phase 0 v2 envelope spawn-entry type (+ `EditorSaveManager` apply path) for the optional `objectKey` field; `LevelEditorController`/`EditorInputHandler` for the export action
- Test: `src/test/java/com/openggf/editor/persistence/TestFullLevelExporter.java` + envelope compat tests

**Contract (spec §G):** export serializes the complete `MutableLevel` — patterns (32-byte Sega format), chunks, blocks, map, solid tiles + chunk collision indices, palettes, spawns (mod-range ids as namespaced keys via Task 5's `idFor` reverse lookup), boundaries, start position — into a directory the SDK consumes. Envelope spawn entries: optional `objectKey`, map-shaped tolerant parsing (declared Phase 0 §C.3 amendment), version stays 2; apply resolves keys via the allocator, absent-mod keys skipped with a logged count.

- [ ] Steps: re-verify landed Phase 0 envelope code → failing tests (export round-trip against a fixture MutableLevel; v2-without-key compat; key resolution + absent-mod skip) → implement → PASS → commit (`feat: editor full-level export and key-based envelope spawns`).

---

### Task 15: Library panes (chunk/block + object palette)

**Files:**
- Create: `src/main/java/com/openggf/editor/render/EditorLibraryBrowserPane.java` (or extend `EditorLibraryPaneRenderer` — read first)
- Modify: `LevelEditorController`/`EditorInputHandler` for browse-select
- Test: logic-level tests per the editor suite pattern

**Contract (spec §G):** browsable chunk/block picker over the loaded level's (or mod tileset's) libraries; object palette listing stock ids + the enabled mods' namespaced keys (from the decorated registry), selecting into the Phase 0 spawn brush. Rendering follows the existing pane renderers; logic (cursor, filter, selection) is unit-tested, drawing smoke-tested via `TestEditorRenderingSmoke`.

- [ ] Steps: read the pane renderers + Phase 0 spawn-brush code → failing logic tests → implement → PASS → commit (`feat: editor library browser panes`).

---

## Workstream 4 — `ggfmod` SDK/CLI

### Task 16: CLI skeleton + `validate` (reflection + ASM) + the ASM dependency **[P1]**

**Files:**
- Modify: `pom.xml` (add `org.ow2.asm:asm`)
- Create: `src/main/java/com/openggf/tools/modsdk/GgfModCli.java`, `ModJarValidator.java`
- Test: `src/test/java/com/openggf/tools/modsdk/TestModJarValidator.java`

**Contract (spec §E):** `GgfModCli` dispatches subcommands (tools conventions: pure logic + thin `main`). `validate` checks: manifest schema (reuse Phase 1 parser), audio manifest, asset presence for every manifest reference, id-range + pattern-budget arithmetic, entrypoint presence/implements-`GgfMod`; reflection checks on an isolated loader (object classes extend the base classes, `RewindRecreatable` presence where dynamic, `final` scalar + object-ref field inventories per the three rewind rules); ASM checks (constructor bodies calling `services()`; ref-field capture usage). Output: numbered findings, exit code 0/1.

- [ ] Steps: failing tests (a known-good fixture jar passes; seeded violations each produce their finding — one test per rule) → implement → PASS → commit (`feat: ggfmod validate with reflection and bytecode checks` — stage pom.xml; `Configuration-Docs: n/a`, note the dependency in the commit body).

---

### Task 17: `convert art`, `convert level`, `convert audio`, `init`, `package`, `run` (dev mode) **[P1]**

**Files:**
- Create: `src/main/java/com/openggf/tools/modsdk/ArtConverter.java`, `LevelConverter.java`, `ProjectScaffolder.java`, `JarPackager.java`
- Test: per-converter tests

**Contract (spec §E):**
- `convert art`: PNG (via `javax.imageio`) + YAML sheet manifest (frame/piece boxes) → Task 6 baked container. Quantization: exact-match against the declared 16-color line, error listing offending pixels/colors (>16 colors, non-8×8 dimensions, out-of-bounds boxes).
- `convert level`: Task 14 export directory → plan asset binaries + `ModLevelDefinition` YAML.
- `convert audio`: the spec §E subcommand — validates/normalizes wav/ogg + loop metadata by reusing Phase 1's decoders and audio-manifest parser [P1]; thin wrapper, own tests.
- `init`: scaffold (manifest, sample badnik source via `ObjectScaffoldTool`'s generator style, sheet manifest, `build.md` instructions). `package`: assemble the jar (manifest + assets + compiled classes dir).
- `run` — **dev mode is built HERE, not inherited** (correcting the spec's "Phase 1's flag" misattribution — Phase 1 ships no dev mode; a one-line spec §E amendment is staged with this plan's commit): `ModRepositoryScanner` [P1] gains exploded-directory support behind a system property (`-Dggfmod.dev.modDir=<build output>` scanned as if a jar), exempt from trace/test force-disable per parent §5/§7; `run` launches the engine with that property set.
- [ ] Steps: re-verify Phase 1 scanner/decoders → failing tests per converter (art round-trip PNG→container→reader→pixel equality; level convert consumes a Task 14 fixture export; audio validation; exploded-dir scan; init output file set) → implement → PASS → commit (`feat: ggfmod converters, dev mode, and project scaffolding`).

---

## Workstream 6 — integration gate

### Task 18: Sample mod + headless integration + docs **[P0][P1]**

- [ ] **Step 1:** Build the sample mod exactly as a creator would: `ggfmod init` → one badnik (patrol + destruction via the standard helpers) + one minimal zone (static palette, no events; small layout authored via the editor export fixture) → `convert` → `package`. Keep the sample's source in `src/test/resources/mods/sample-mod-src/` with a build script; the built jar is produced during the test run, not committed.
- [ ] **Step 2:** Headless integration test: load the jar through the real scanner/catalog/runtime, launch S2 headless (HeadlessGameBoot idiom + the Phase 0 patch resolution), assert: zone 11 loads and is playable-shaped (level non-null, spawns resolved, music id routed), the badnik spawns and destructs, a rewind snapshot/restore round-trips the badnik (Task 4's resolver), and with the mod force-disabled everything is bit-identical to stock (assert the resolved module is the base instance).
- [ ] **Step 3:** Regression gates (global constraints) + `docs/modding/content-mods.md` (creator guide: project layout, object how-to, zone how-to, validate/package/run) + CHANGELOG + CLAUDE.md/AGENTS.md pointers.
- [ ] **Step 4:** Final commit with `Changelog: updated`, `Agent-Docs: updated`, `Guide: n/a` (or `updated` if docs/GUIDE exists — check), rest per policy.

---

## Execution notes

- The plan's [P0]/[P1] re-verify steps are not optional — this plan was authored before its dependencies landed.
- Workstream boundaries are commit-clean checkpoints; pausing between workstreams is safe.
- Merge flow: `superpowers:finishing-a-development-branch`; README release-log note on merge to `develop`.
