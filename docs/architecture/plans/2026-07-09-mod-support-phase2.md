# Mod Support Phase 2 (Additive Content Mods) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mod jars ship compiled Java (objects/badniks, a `GamePatch`), baked art (reskins + new tilesets), and a new zone appended to an existing game; a `ggfmod` toolchain converts and validates.

**Architecture:** Six workstreams per the Phase 2 spec: (1) code loading + trust, (2) objects + art + pattern budget, (3) new-zone seams (`LevelDescriptor` retrofit, `ZoneProgressionPlan`, in-memory `Sonic2Level` overload, `ModZoneLoader`), (4) `ggfmod` SDK/CLI, (5) editor mod-facing features, (6) the sample-mod integration gate.

**Tech Stack:** Java 21, Jackson, `org.ow2.asm:asm` (NEW dependency, declared in the spec §E), JUnit 5.

**Specs:** `docs/superpowers/specs/2026-07-09-mod-support-phase2-design.md` (authoritative), parent `2026-07-09-mod-support-design.md`, Phase 0 spec, Phase 1 plan.

## CONTINGENCY PREAMBLE — read before executing anything

This plan is written against Phase 0/1 contracts that may not yet be implemented. Every consuming task carries **[P0]** / **[P1]**; re-verify the landed interface and update this plan on structural drift. Do not begin until Phase 0 and Phase 1 have merged to `next`.

**Calibration note:** tasks creating pure new classes contain complete code; tasks modifying engine files or consuming unlanded interfaces are contract-and-anchor style (the Phase 0 plan's workstream-A convention) with mandatory read-first steps. That is deliberate — full code against unlanded dependencies would be false precision.

## Global Constraints

- **JUnit 5 only. Never `git add -A`. No new singletons.** Commit trailers per repo policy; intermediate `feat` commits touching `src/main/` use `Changelog: n/a: covered by final phase-2 changelog entry in this branch`.
- **New Maven dependency:** exactly one — `org.ow2.asm:asm:9.9.1`, added in Task 0. Nothing else.
- **Execution branch (user directive 2026-07-10):** implement and commit directly on
  the existing `next` worktree; do not create a phase branch or merge-back commit.
- **ArchUnit:** new engine packages in this plan (`com.openggf.mods.code`, `com.openggf.tools.modsdk`) plus new cross-slice references will trip the `CORE_RUNTIME_TOP_LEVEL_DEPENDENCY_EDGES` ratchet. The rule's failure message lists exactly the missing edges; add precisely those to the allowlist with a comment citing this plan (expected at minimum: `mods -> game` for patch registration, `game -> level` already exists — verify, don't guess).
- **Task order:** workstreams are sequenced 1 → 2 → 3 → 5 → 4 → 6 (the CLI's `convert level` needs the editor export; the sample mod needs everything). Within a workstream, order as written.
- **Regression gates** (every workstream-final task): full default suite, S3K must-keep-green set, and the trace spot sweep (s1_ghz1 / s2_ehz1 / s3k_aiz1) with mods force-disabled — bit-identical expectations. Log sweeps in `docs/TRACE_FRONTIER_LOG.md`.

## 2026-07-10 readiness amendments (authoritative)

- Tasks 1–4 build an engine-owned `AutoCloseable ModRuntime`: boot-frozen loaders,
  restart-required state/trust changes, shutdown close/jar-unlock test, and fresh
  transactional per-session registration plans. `ModClassResolver` uses
  `(ownerModId,binaryClassName)` plus the owner loader, including dynamic children and
  duplicate-FQN tests; it is not a registration-time class map. A direct dependency
  lookup uses that dependency's own-jar/engine-parent view and cannot traverse its
  dependency list.
- Task 2 freezes contributions into `ModRegistrationPlan` and wraps them in an
  engine-owned `ModBackedGamePatch`. Registration is all-or-nothing. Add callback
  fault-boundary/session-abort tests per the parent design: provider/event proxies and
  mod-keyed object dispatch publish runtime findings, persist next-launch disable for
  owner/dependents, log the owner stack, and return the current session to title.
- Task 2b uses RUNTIME `@ModApi`; snapshots public + protected subclass surface,
  generics, exceptions, annotations, and transitive signature types; semver range
  satisfaction is real. ASM checks mutable statics and method bodies.
- Replace Task 5's byte allocator with a namespaced object-key registry and tagged
  `ObjectSpawn` reference. Stock-id creation always delegates untouched; mod-key
  creation never consults/captures a stock byte. Test stock placeholder isolation,
  absent keys, dynamic children, duplicate FQNs, save/editor/rewind identity.
- Tasks 6, 11, 12, 14, 16, and 17 implement the exact schemas, golden fixtures, path
  rules, and limits in `2026-07-10-mod-support-format-security-contracts.md`. Envelope
  mod objects are v3. `ggfmod package` validates; boot independently recomputes the
  structural validator against jar bytes before loader creation.
- Task 8 stores pattern-window assignments in session state and re-registers them
  immediately after `LevelManager.initObjectArt()` clears dynamic ranges. Test
  consecutive stock/mod loads and editor rebuilds.
- Task 10's progression API receives immutable `ZoneTopology` (zone/act counts and
  terminal behavior). Test variable act counts, credits, time-attack return,
  redirects, disabled-mod fallback, and event-chain rejection.
- Task 12 uses namespaced `TrackKey` for new music; numeric ids remain stock overrides.
  Test two mods with same local track name, stock isolation, save/reload, and rewind.
- All data-only parsing is bounded and path-safe. Add zip-bomb, dishonest-size,
  `../`/absolute/case-collision, oversized count/image/audio, and malformed binary
  tests before happy-path implementations.
- Commands: focused tasks use `mvn "-Dtest=<exact class>" test`; workstream gates use
  `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils" test`;
  final gate uses `mvn test`. Record exact trace commands/routes and stage
  `docs/TRACE_FRONTIER_LOG.md` when changed. Final docs update README release notes.

---

## Workstream 1 — code loading + trust

### Task 0: reusable compiled-mod validator core **[P1]**

**Files:** create `ModValidator`, `ModValidationFinding`, `ModValidationReport` under
`com.openggf.mods.validation`; modify `pom.xml` with pinned ASM 9.9.1; test
`TestModValidator` using generated classfile/jar fixtures without creating a loader.

- [ ] Failing tests cover entrypoint/base-class checks; constructor `services()` use;
  recreate paths; final scalars; object refs/rewind ids; rejection of every static
  except compile-time primitive/String constants; malformed bytecode; and API
  references, where `@ModApi` references are clean and reachable non-API engine
  references emit compatibility warnings rather than eligibility errors;
  and validation of an
  untrusted code jar without executing its classes.
- [ ] Implement one reusable ASM/jar-byte validator invoked by boot eligibility and
  Task 16's CLI before any owner loader is created. The engine recomputes validation
  from jar bytes; no embedded author record is trusted and structural validation does
  not use reflection/class loading.
- [ ] Run focused test; PASS; commit dependency + validator core together.

### Task 1: engine-owned `ModRuntime` + delegating loaders **[P1]**

**Files:**
- Create: `ModRuntime.java`, `ModClassLoaderFactory.java`, `ModDependencyClassLoader.java`
- Modify: `Engine`/engine-service ownership and shutdown
- Test: `TestModRuntime`, `TestModClassLoaders`

**Interfaces:**
- Consumes: valid `ModDescriptor`s from
  `ModCatalog.effective().orderedEnabled()` [P1 — re-verify]; invalid scan entries
  never reach loader construction.
- Produces `AutoCloseable ModRuntime`: Task 0 validates jar bytes without executing
  them; dependency-ordered loaders are frozen by owner; `loadOwned(owner,binaryName)`
  supports dynamic rewind children; shutdown closes all loaders. Manager changes
  require restart. Dependency visibility remains declared-only; assets use
  `ModAssetRoot`, not classpath resources.

- [ ] **Step 1: Write the failing test.** Build direct dependency jars plus an
  A→B→C chain. Assert A sees B, B sees C, A cannot see C unless A also declares C,
  an undeclared sibling remains invisible, and engine classes resolve parent-first.
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
    private final List<ModDependencyClassLoader> dependencyLoaders;

    public ModDependencyClassLoader(String modId, URL[] jarUrls, ClassLoader engineParent,
                                    List<ModDependencyClassLoader> dependencyLoaders) {
        super("mod:" + modId, jarUrls, engineParent);
        this.dependencyLoaders = List.copyOf(dependencyLoaders);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name); // own jar
        } catch (ClassNotFoundException ownMiss) {
            for (ModDependencyClassLoader dep : dependencyLoaders) {
                try {
                    return dep.loadOwnOrParent(name); // never traverses dep dependencies
                } catch (ClassNotFoundException ignored) {
                    // try next dependency
                }
            }
            throw ownMiss;
        }
    }

    Class<?> loadOwnOrParent(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) return loaded;
            try {
                return getParent().loadClass(name);
            } catch (ClassNotFoundException parentMiss) {
                return super.findClass(name); // URLClassLoader own jar only
            }
        }
    }
}
```

- [ ] **Step 3:** Add identical-FQN, invalid-validation, close-idempotence, session
  restart, Engine shutdown, and Windows jar-replace-after-close tests. PASS. Commit.

---

### Task 2: `GgfMod` + `ModContext` + entrypoint/fault lifecycle **[P0][P1]**

**Files:**
- Create: `GgfMod.java`, `ModContext.java`, `ModRegistrationPlan.java`, `ModBackedGamePatch.java`, `ModRegistrationException.java`, `ModFaultBoundary.java`, `BakedSheetRef.java`
- Modify: mod provider/event registration wrappers, `ObjectManager` mod-key dispatch,
  gameplay return-to-title handling, Phase 1 `ModRuntimeFindingStore`, and pending
  state persistence
- Modify: `src/main/java/com/openggf/mods/ModManifest.java`, `ModManifestParser.java` [P1]
- Test: `TestModRegistrationPlan`; extend `TestModManifestParser`

**Commit split (right-sizing):** this task lands as TWO commits — (1) `feat: activate phase-2 mod manifest fields` (`BakedSheetRef` + validation/consumer tests; no runtime code), then (2) `feat: mod code entrypoint runtime and patch registration` (runtime + enablement). The activation commit has no dependency on the runtime and is consumed by Tasks 7/8/12 independently.

**Interfaces:**
- Consumes: Phase 0 `ModuleResolutionService`/owner-tagged patch contracts, Phase 1 catalog, and Task 1 loaders.
- Produces:
  - **Manifest field activation:** Phase 1 already strictly parses `entrypoint`, typed
    `artOverrides`, `insertAfter`, and `patternWindows` under manifest v1 but marks
    their owners ineligible. Phase 2 consumes those existing fields: resolve
    `insertAfter` only against the target game's stock progression keys; allocate
    1–16 windows per owner and at most 128 total in effective order; retain unknown-
    field rejection. Extend eligibility/consumer tests without changing manifest v1.
  - `record BakedSheetRef(String entryPath)` in `com.openggf.mods.code` — a thin jar-entry pointer defined HERE. Task 6's engine-side reader takes raw bytes/streams; mod-side code resolves a `BakedSheetRef` to a stream before calling it (keeps the dependency direction `mods -> level`, never the reverse).
  - `public interface GgfMod { void register(ModContext ctx); }`
  - `ModContext` (fresh per owner/session): registers an owner-tagged patch, namespaced object factory, and art reference into a private transaction; exposes bounded `ModAssetRoot`; validates base game/namespace; then freezes atomically into `ModRegistrationPlan` and engine-owned `ModBackedGamePatch`.
  - `ModRuntime.newRegistrationPlan(request)` creates fresh entrypoints and private
    transactions per session. Content registrations always synthesize one engine-owned
    backing patch, ordered first within the owner; explicit creator patches are
    optional and follow registration order. Failure publishes nothing and fails owner
    + dependents. No class map or entrypoint/patch instance survives the session.
  - The mod-backed enablement implementation answers by explicit `PatchOwner.Mod(modId)`; built-ins carry `PatchOwner.BuiltIn`. Unknown owner/id is a registration error. Install it in the engine-owned resolution service.

- [ ] **Step 1:** Re-verify landed owner-tagged `ModuleResolutionService`/`PatchEnablement` and Phase 1 catalog shapes.
- [ ] **Step 2:** Failing tests: creator calls only documented `ModContext`; successful transaction yields one `ModBackedGamePatch`; base-game/namespace/duplicate failures publish nothing; throwing entrypoint is isolated; explicit built-in/mod owners order correctly. Exercise pre-apply metadata failure (independent owners continue) and a creator patch that mutates then throws (typed launch abort, no last-good claim, persisted owner/dependent disable, manager finding/log). Then exercise throwing provider, event, and mod-object callbacks: owner/dependents are persisted pending-disabled, independent owners remain eligible next launch, manager shows the finding, the owner stack is logged, gameplay returns to title, and no partially mutated frame resumes. Do not catch `VirtualMachineError` or `ThreadDeath`.
- [ ] **Step 3:** Implement; tests PASS; commit (`feat: mod code entrypoint runtime and patch registration`).

---

### Task 2b: `@ModApi` surface + signature-snapshot guard + API minor version **[P0][P1]**

**Files:**
- Create: `src/main/java/com/openggf/game/ModApi.java`,
  `src/main/java/com/openggf/tools/modsdk/ModApiJavadocTool.java`
- Modify: apply the annotation to the spec §G curated set (`GgfMod`, `ModContext`, `GamePatch`/`PatchContext`/`GameplayLaunchRequest`, `ObjectServices`, `AbstractObjectInstance`, `AbstractBadnikInstance`, `ObjectSpawn`, `ObjectControlState`, `ObjectLifetimeOps`, `ObjectPlayerQuery`, `PhysicsProfile`, the `level.objects` utility helpers, `BakedSheetRef` (exists as of Task 2); `BakedSheetReader`/`ModLevelDefinition` once they exist — later tasks add their own annotations as they create types); `src/main/java/com/openggf/mods/ModApiVersion.java` [P1]
- Test: `src/test/java/com/openggf/game/TestModApiSurfaceSnapshot.java`

**Contract (spec §G last bullet):**
- `@Retention(RUNTIME) @Target(TYPE) public @interface ModApi {}` with Javadoc stating the compatibility promise.
- Signature-snapshot guard serializes public and protected subclass surface,
  constructors, generics, throws, annotations, and transitive signature types to a
  checked-in baseline. Every non-JDK type in a supported signature must itself be
  `@ModApi` (including `RewindRecreatable`, creator-facing provider/handler
  interfaces, `GameModule`, `ObjectFactory`, and registration-handle types) or the
  build fails; there is no accidentally unsupported transitive type. Semver rules
  decide compatible additions; removals/changes fail.
- `ModApiJavadocTool` invokes the JDK `DocumentationTool` on the exact annotated type
  inventory. A golden inventory test proves every supported root/type appears and an
  unannotated engine internal does not; release packaging publishes the generated
  Javadoc beside the engine and attached `openggf-mod-sdk` jars.
- Set semantic API version `1.1.0`; this phase is additive. Assert the canonical
  Phase 1 range `>=1.0.0 <2.0.0` remains eligible, while `>=1.2.0` does not. Do not
  accept every lower integer major implicitly.

- [ ] Steps: re-verify landed `ModApiVersion` → failing tests (snapshot guard against a seeded baseline; version acceptance triple) → implement + annotate the already-existing types → PASS → commit (`feat: @ModApi surface, signature snapshot guard, API minor 1.1`).

---

### Task 3: Trust gate **[P1]**

**Files:**
- Modify: Phase 1 `ModState`, `ModStateStore`, `PendingModStateEditor`,
  `EffectiveCatalogBuilder`, and `ModManagerScreen`
- Test: extend `TestModCatalog`, `TestModManagerScreen`, `TestModStateStore`

**Contract (spec §F):**
1. `ModState.Entry` gains `trusted` (boolean) + `trustedJarSha256` (nullable String). Store parsing is map-shaped [P1 fact] — added fields tolerated both directions; write both fields.
2. `ModEligibility`'s `"mod code is not supported yet"` rule is **replaced**: `containsCode && !trustedAndHashMatches` → block reason `"contains code — trust required (press accept twice)"`. Hash computed once per scan (SHA-256 of the jar), compared to `trustedJarSha256`; mismatch clears the persisted grant.
3. The manager arms/disarms trust confirmation; second press writes trusted hash to
   **pending** state and displays Restart required. It never loads or enables code in
   the effective process snapshot.
4. Tests: grant flow; hash-mismatch revocation; data-only mods unaffected; v1 `modstate.json` (no trust fields) loads with `trusted=false`.

- [ ] Steps: re-verify landed Phase 1 files → failing tests → implement → PASS → commit (`feat: mod code trust gate with jar-hash revocation`).

---

### Task 4: `ModClassResolver` + loader-aware rewind recreate

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModClassResolver.java`
- Modify: `src/main/java/com/openggf/level/objects/ObjectRewindDynamicCodecs.java` (~line 65)
- Test: `src/test/java/com/openggf/mods/code/TestModClassResolverRecreate.java`

**Contract (spec §B fix 1):** rewind entries carry optional owner mod id plus binary class name. An injected resolver asks the boot-scoped owner-loader registry to load the name; it does not require registration-time discovery. Engine entries fall back to the engine loader. Tests cover an unregistered dynamic child, identical FQNs in two owners, missing owner, closed runtime, and mod-less parity.

- [ ] Steps: read `ObjectRewindDynamicCodecs` + its recreate-context plumbing → failing test (a class from a child loader recreates via resolver; an engine class still resolves via fallback; EMPTY resolver = today's behavior) → implement → PASS → run the rewind test suites (`mvn "-Dtest=com.openggf.game.rewind.*" test` adjusted to real class names) → commit (`fix: loader-aware rewind recreate for mod object classes`).

---

## Workstream 2 — objects, art, pattern budget

### Task 5: Namespaced object-key registry + tagged spawns

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModObjectKeyRegistry.java`, `ModDecoratedObjectRegistry.java`
- Modify: `src/main/java/com/openggf/level/objects/ObjectSpawn.java` and rewind/editor spawn codecs for the optional tagged key
- Test: `src/test/java/com/openggf/mods/code/TestModObjectKeyRegistry.java`

**Contract (spec §B):**
- Input: immutable owner-tagged registrations `(modId, namespacedKey, factory)` with duplicate-key rejection.
- `ObjectSpawn` retains its stock byte id and gains an optional namespaced object key. If the key is absent, `ModDecoratedObjectRegistry.create` delegates directly to the base registry; if present, it resolves only the key registry. There is no numeric allocation or requested-id hint.
- Rewind/editor/level formats persist the key and owner. Dynamic children use `spawnChild` ownership or an explicit key; no mod numeric identity enters saves.

- [ ] Steps: failing tests for stock placeholder isolation/delegation, canonical key
  grammar/case/owner mismatch, duplicate/absent owner, dynamic child, and
  save/editor/rewind round trip → implement → PASS → commit.

---

### Task 6: Baked sheet container — engine reader + modsdk writer

**Files:**
- Create: `src/main/java/com/openggf/level/objects/BakedSheetReader.java` (NO ref type here — `BakedSheetRef` was created in Task 2 in `mods.code`; the reader takes **raw inputs** (`InputStream` or `byte[]`), and ref→stream resolution stays mod-side, so no `level -> mods` dependency edge is created)
- Create: `src/main/java/com/openggf/tools/modsdk/BakedSheetWriter.java`
- Test: `src/test/java/com/openggf/tools/modsdk/TestBakedSheetRoundTrip.java`

**Format:** implement baked-sheet v1 exactly as specified in
`2026-07-10-mod-support-format-security-contracts.md`, including magic, field widths,
ordering, bounds, trailing-byte rejection, golden fixtures, and bounded reads. The
older inline `GGFSHEET` sketch is superseded.

- [ ] Steps: failing round-trip test (writer → bytes → reader → field-equality against the source `SpriteMappingPiece` list and pattern pixels; bad magic / future version rejected) → implement both sides (complete code expected — pure serialization) → PASS → commit (`feat: baked sprite sheet container with modsdk writer`).

---

### Task 7: Art overrides + implicit data-only patch **[P0][P1]**

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModArtOverlayProvider.java` (decorates a base `ObjectArtProvider`)
- Modify: `ModRegistrationPlan`/`ModBackedGamePatch` for data-only art contributions
- Test: `src/test/java/com/openggf/mods/code/TestModArtOverrides.java`

**Contract (spec §C):** validated sheets decorate the base provider and later owners
win. Content registrations always receive one engine-owned backing patch; no explicit
creator patch is required. A missing/invalid declared asset is a structured catalog
error that blocks the contribution; runtime I/O failure aborts the session rather than
silently switching art.

- [ ] Steps: failing tests for override/later-wins, backing-patch order, structured
  missing-asset block, and runtime failure boundary → implement → PASS → commit.

---

### Task 8: PatternAtlas governance + per-mod windows **[P1]**

**Files:**
- Modify: `src/main/java/com/openggf/graphics/PatternAtlas.java` (`validatePatternIdGovernance` ~584-594, `registerRange(int,int,String)` ~144)
- Modify: `src/main/java/com/openggf/level/LevelManager.java` and session-owned mod pattern-window state
- Modify: `src/main/java/com/openggf/mods/ui/ModManagerScreen.java`
- Create: `src/main/java/com/openggf/mods/code/ModPatternWindowAllocator.java`
- Test: `src/test/java/com/openggf/graphics/TestPatternAtlasDynamicRanges.java`, extend `TestModManagerScreen`

**Contract (spec §C, engine change):** governance accepts ids inside dynamically registered ranges and enforces alignment. `ModPatternWindowAllocator` retains deterministic assignments in session state and registers them from the first free `endExclusive`. `LevelManager.initObjectArt()` immediately re-registers those assignments after its clear and before caching/rendering. Budget display comes from the retained allocator. Tests cover 1/16-window owners, rejection of 0/17, deterministic effective-order allocation, overlap avoidance, the 128-window aggregate eligibility failure naming the blocked owner, manager per-owner/total display, consecutive level loads, and editor teardown/rebuild.

- [ ] Steps: read the two PatternAtlas regions → write the full failing budget/governance/UI matrix above → implement → PASS → run graphics/pattern and manager suites → commit (`feat: dynamic PatternAtlas ranges for per-mod pattern windows`).

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

**Contract (spec §D.3):** `ZoneProgressionPlan.next(ZoneTopology topology, int zone, int act)` returns a successor or credits. `ZoneTopology` is an immutable registry snapshot containing zone count, per-zone act counts, and terminal/event-chain metadata. Default linear behavior is exhaustively compared with current logic, including variable act counts and credits. Mods sharing one stock `insertAfter` anchor chain in stable effective order, then rejoin the original successor; disabling any member rebuilds the chain without dangling redirects. Time-attack early return, redirects, dependency ordering, duplicate identity, disabled-mod fallback, and event-chain rejection have focused tests.

- [ ] Steps: read `advanceToNextLevel` + `advanceZoneActOnly` → failing tests (LINEAR equivalence; MTZ→mod11→SCZ; MTZ→mod11→mod12→SCZ in effective/dependency order; disable either mod and rebuild) → implement → PASS → trace spot sweep (progression untouched for stock) → commit (`feat: ZoneProgressionPlan seam for mod zone reachability`).

---

### Task 11: `Sonic2Level` in-memory data overload **[P0]**

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/Sonic2Level.java`
- Test: `src/test/java/com/openggf/game/sonic2/TestSonic2LevelInMemoryConstruction.java`

**Contract (spec §D.2):** builder accepts the plan plus in-memory layout, four palette
lines, solid height/width/angle arrays, primary/secondary collision indices,
boundaries, and tagged spawns using the exact v1 format. Extract shared decode helpers;
ROM constructors delegate. A mod-only plan uses `ResourceLoader.forModAssetsOnly()`
and never relies on an implicitly nullable ROM.

- [ ] Steps: **re-verify the landed Phase 0 loader's null-Rom behavior first** — the "nullable Rom" claim comes from the Phase 0 plan's Task B2 contingency note, not a pinned spec guarantee; if the landed `ResourceLoader` rejects null, add its `forModAssetsOnly()` factory here → read `Sonic2Level` fully (both constructors + every load method) → failing test (construct from fixture in-memory data + a tiny ModAsset plan in `@TempDir`; assert map/palette/solid-tile/boundary getters) → implement by extraction, not duplication → PASS → HTZ trace gate (the plan-constructor zone) + S2 zone-loading tests → commit (`feat: in-memory level data construction for mod zones`).

---

### Task 12: `ModLevelDefinition` + `ModZoneLoader` + music routing **[P0]**

**Files:**
- Create: `src/main/java/com/openggf/mods/code/ModLevelDefinition.java` (+ parser),
  `ZoneKey.java`, `BakedLevelRef.java`, `ModZoneContribution.java`, `ZoneEventFactory.java`,
  `ModZoneLoader.java`
- Modify: `ModContext`, `ModRegistrationPlan`, `ModBackedGamePatch`, zone/level/
  progression provider decorators, and the `@ModApi` inventory
- Test: `src/test/java/com/openggf/mods/code/TestModZoneLoader.java`

**Contract (spec §D.2):** use the exact `level.json` + binary layout in the format/security contract. `ModContext.registerZone(ModZoneContribution)` supplies the owner; local key, baked level ref, optional per-zone anchor/default, and optional event factory freeze transactionally into the registration plan. `ModBackedGamePatch` atomically decorates zone, level, event, and progression providers. Spawn entries are tagged stock ids or namespaced keys; music is stock id or namespaced `TrackKey`. Synthetic indices allocate by effective owner then registration order; same-anchor zones chain deterministically; duplicate local keys fail the owner transaction. Runtime/save surfaces translate through tagged `ZoneKey`; no synthetic mod index is persisted. Event-chain insertion is refused.

- [ ] Steps: re-verify Tasks 2/5/9/10/11 landed shapes → failing tests (definition parse + validation errors; transaction publication; loader playable fixture; key/music routing; one owner with two zones; two owners sharing an anchor; distinct anchors; duplicate key; disable/rebuild; stable ZoneKey resolution; event fault boundary; invalid insertion refusal) → implement → PASS → commit (`feat: transactional mod zone registration and loader`).

---

### Task 13: Title card fallback + data-select extension

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/titlecard/TitleCardManager.java` (~182, ~392), `src/main/java/com/openggf/game/sonic2/dataselect/S2DataSelectProfile.java` (+ preview generator as needed), `S2SaveSnapshotProvider` and its versioned slot DTO
- Test: focused tests per file

**Contract (spec §D.4 + §D.3):** title cards use registry names with the documented skip flag. S2 slot metadata becomes a backward-compatible tagged saved-zone union: legacy stock numeric index or `ZoneKey.mod(owner, local)`. Save mod zones by key; load resolves the current synthetic index. Missing/disabled keys and legacy numeric values beyond stock preserve the slot but restart at zone 0 with a finding, never retarget. Data-select uses the key for generic preview/restart. Read title card, save provider/DTO, profile, image cache, and restart consumer before changes.

- [ ] Steps: read all named files + restart consumer → failing tests for stock backward compatibility, save owner-B/reorder-or-disable owner-A/resume B, missing B fallback, and numeric-collision non-retarget → implement → PASS → commit (`feat: keyed mod-zone saves and registry-driven presentation` with `Configuration-Docs: updated`).

---

## Workstream 5 — editor mod-facing features (before workstream 4: the CLI consumes the export)

### Task 14: Full-level export + envelope `objectKey` **[P0]**

**Files:**
- Create: `src/main/java/com/openggf/editor/persistence/FullLevelExporter.java`
- Create: `EditorSavePayloadV3`, `ObjectSpawnStateV3`, and explicit v1/v2→v3 migrator
- Modify: `EditorSaveManager`, controller/input export action; leave v1/v2 DTOs,
  canonical writers, and hashes byte-stable
- Test: `src/test/java/com/openggf/editor/persistence/TestFullLevelExporter.java` + envelope compat tests

**Contract (spec §G):** export the complete `MutableLevel` using the exact `level.json` + binary directory contract and golden fixtures. Spawns remain tagged stock ids or namespaced keys. Editor envelopes containing mod keys are v3; older readers quarantine them. Missing keys skip with a structured logged count.

- [ ] Steps: failing tests for historical v1/v2 raw hashes, v3 stock/key tagged union,
  v1/v2-reader quarantine of v3, migration, key/owner validation, absent-key skip, and
  full export golden round trip → implement → PASS → commit.

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

### Task 16: CLI skeleton + `validate` wrapper **[P1]**

**Files:**
- Create: `src/main/java/com/openggf/tools/modsdk/GgfModCli.java`, `ModJarValidator.java`
- Test: `src/test/java/com/openggf/tools/modsdk/TestModJarValidator.java`

**Contract (spec §E):** thin CLI over Task 0's reusable validator plus manifest,
audio, asset/key, pattern-budget, format, and API-range checks. Include the
compile-time-static-only rule; custom mod static adapters are deferred. There is no numeric mod-object id-range rule. Output
numbered findings and exit 0/1.

- [ ] Steps: failing tests (a known-good fixture jar passes; seeded violations each produce their finding — one test per rule) → implement → PASS → commit (`feat: ggfmod validate with reflection and bytecode checks` — stage pom.xml; `Configuration-Docs: n/a`, note the dependency in the commit body).

---

### Task 17: `convert art`, `convert level`, `convert audio`, `init`, `package`, `run` (dev mode) **[P1]**

**Files:**
- Create: `src/main/java/com/openggf/tools/modsdk/ArtConverter.java`, `LevelConverter.java`, `ProjectScaffolder.java`, `JarPackager.java`
- Modify: `pom.xml` to attach the `openggf-mod-sdk` classifier jar containing only
  `com/openggf/tools/modsdk/**` plus templates/service resources
- Test: per-converter tests

**Contract (spec §E):**
- `convert art`: PNG (via `javax.imageio`) + YAML sheet manifest (frame/piece boxes) → Task 6 baked container. Quantization: exact-match against the declared 16-color line, error listing offending pixels/colors (>16 colors, non-8×8 dimensions, out-of-bounds boxes).
- `convert level`: Task 14's exact `level.json` + binary directory contract; JSON is
  the only level metadata format.
- `convert audio`: the spec §E subcommand — validates/normalizes wav/ogg + loop metadata by reusing Phase 1's decoders and audio-manifest parser [P1]; thin wrapper, own tests.
- `init`: scaffold the canonical manifest, a compilable sample badnik/sheet, a
  minimal editor-export level source consumable by `convert level`, assets, and build
  instructions; Phase 3 later adds the character stub when that API exists. Its
  golden file-set test compiles/packages the unedited scaffold. `package`: run Task 16
  validation and fail on errors, then assemble; runtime independently recomputes Task
  0 validation and never trusts an embedded author record.
- `run` — **dev mode is built HERE, not inherited** (correcting the spec's "Phase 1's flag" misattribution — Phase 1 ships no dev mode; a one-line spec §E amendment is staged with this plan's commit): `ModRepositoryScanner` [P1] gains exploded-directory support behind a system property (`-Dggfmod.dev.modDir=<build output>` scanned as if a jar), exempt from trace/test force-disable per parent §5/§7; `run` launches the engine with that property set.
- Packaging excludes `com/openggf/tools/modsdk/**` from the main engine jar and
  attaches a separate `openggf-mod-sdk` jar with the exact SDK class/template
  inventory. Because classifier metadata shares the main POM, creator/build docs
  require both coordinates explicitly. A jar-content test rejects engine internals
  leaking into the SDK, rejects SDK classes in the engine jar, and launches `ggfmod`
  with both artifacts on the classpath.
- [ ] Steps: re-verify Phase 1 scanner/decoders → failing tests per converter (art round-trip PNG→container→reader→pixel equality; level convert consumes a Task 14 fixture export; audio validation; exploded-dir scan; unedited init scaffold file set compiles and packages) → implement → PASS → commit (`feat: ggfmod converters, dev mode, and project scaffolding`).

---

## Workstream 6 — integration gate

### Task 18: Sample mod + headless integration + docs **[P0][P1]**

- [ ] **Step 1:** Build the sample mod exactly as a creator would: `ggfmod init` → one badnik (patrol + destruction via the standard helpers) + one minimal zone (static palette, no events; small layout authored via the editor export fixture) → `convert` → `package`. Keep the sample's source in `src/test/resources/mods/sample-mod-src/` with a build script; the built jar is produced during the test run, not committed.
- [ ] **Step 1b:** Add a separate checked-in data-only reskin source under
  `src/test/resources/mods/sample-reskin-src/`; package it through the real CLI and
  assert a stock art key is replaced while mods-off rendering/provider identity is
  unchanged. This is the Phase 4 gallery's reskin source.
- [ ] **Step 2:** Headless integration test: load the jar through the real scanner/catalog/runtime, launch S2 headless (HeadlessGameBoot idiom + the Phase 0 patch resolution), assert: zone 11 loads and is playable-shaped (level non-null, spawns resolved, music id routed), the badnik spawns and destructs, a rewind snapshot/restore round-trips the badnik, save/reload resumes zone 11, and disabling the mod preserves the slot but restarts at zone 0 with the specified finding. With the mod force-disabled all other behavior is bit-identical to stock (assert the resolved module is the base instance).
- [ ] **Step 3:** Regression gates (global constraints) + `docs/modding/content-mods.md` (creator guide: project layout, object how-to, zone how-to, validate/package/run) + CHANGELOG + CLAUDE.md/AGENTS.md pointers.
- [ ] **Step 4:** Final commit with `Changelog: updated`, `Agent-Docs: updated`, `Guide: n/a` (or `updated` if docs/GUIDE exists — check), rest per policy.
- [ ] **Step 5:** Finalize the Phase 2 `@ModApi` public/protected baseline after all
  annotated types exist; review the diff and set exact API version `1.1.0`.

---

## Execution notes

- The plan's [P0]/[P1] re-verify steps are not optional — this plan was authored before its dependencies landed.
- Workstream boundaries are commit-clean checkpoints; pausing between workstreams is safe.
- Completion flow: commit verified task slices directly on `next`; the final task
  includes README release notes. No merge-back step exists.
