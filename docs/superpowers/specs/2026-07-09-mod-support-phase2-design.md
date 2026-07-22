# Mod Support Phase 2 — Additive Content Mods Design

**Branch baseline:** `next`.

**Date:** 2026-07-09
**Status:** Approved (brainstorming session)
**Parent:** `2026-07-09-mod-support-design.md` §8 Phase 2. Siblings: Phase 0 spec
(`2026-07-09-mod-support-phase0-design.md` — GamePatch composition,
`LoadSource`, editor baseline) and the Phase 1 plan
(`2026-07-09-mod-support-phase1.md` — `ModCatalog`, manager UI, streamed audio).
**Depends on:** Phase 0 (all three workstreams) AND Phase 1 (loader/catalog/manager).
Recon date for all code claims: 2026-07-09.

## Goal

Mods stop being data-only: a mod jar may ship compiled Java (new
objects/badniks, a `GamePatch`), baked art (reskins and new tilesets), and a
complete **new zone in an existing game** — the flagship deliverable. A
`ggfmod` toolchain converts creator formats (PNG, editor exports) into the
baked assets the engine loads, and validates mod jars against the contracts
the engine's own guards enforce on first-party code.
Patch composition, objects, and art use game-module seams shared by S1/S2/S3K. The
new-zone acceptance is deliberately the S2 flagship because its level/progression
walls are the concrete Phase 2 lift; S1/S3K new-zone adapters remain original-scope
follow-ons tracked by Phase 4 rather than being implied complete here.

## Components

### A. Mod code loading (`GgfMod` + `ModContext` + classloader)

- Each enabled code mod gets a classloader whose parent is the engine
  classloader, owned by an engine-scoped `AutoCloseable ModRuntime` created after the
  effective startup catalog is frozen. Manager/trust changes require restart; there
  is no hot load/unload. The runtime closes every loader during engine shutdown (and
  tests verify Windows jar replacement after close). For each gameplay session it
  instantiates fresh entrypoints and freezes a fresh transactional
  `ModRegistrationPlan`; no entrypoint/patch instance leaks across sessions. (The Phase 1
  wiring point). Per parent §2, **inter-mod visibility uses a delegating
  loader**: a mod's loader consults only the own-jar/engine-parent view of each
  *directly declared* dependency (Phase 1's manifest `dependencies`, already
  topologically ordered by the catalog) before failing a lookup; undeclared
  siblings and a dependency's undeclared transitive dependencies stay invisible.
  Phase 1's `ModEligibility` refusal
  `"mod code is not supported yet"` is replaced by the trust gate (§F).
- Entry contract (parent spec §2): `class MyMod implements GgfMod
  { void register(ModContext ctx); }`, `entrypoint` named in the manifest.
- `ModContext` registration surface (all namespaced by mod id):
  - `registerGamePatch(GamePatch patch)` — contributes an owner-tagged, unique
    namespaced patch to the Phase 0 `ModuleResolutionService`.
  - `registerObject(String key, ObjectFactory factory)` and
    `registerObjectArt(String key, BakedSheetRef sheet)` — consumed by the
    engine-owned `ModBackedGamePatch` built from the frozen registration plan. Creator
    code never reads mutable pending registrations; content scopes to the manifest's
    base game automatically.
  - `registerZone(ModZoneContribution)` — registers an owner-tagged local zone key,
    bounded `BakedLevelRef`, optional per-zone stock `insertAfter` anchor (falling back
    to the manifest default), and optional owner-wrapped `ZoneEventFactory`. Task 12
    freezes contributions in registration order into `ModRegistrationPlan`; the
    engine backing patch decorates zone/level/progression providers atomically.
  - `modAssets()` — a bounded `ModAssetRoot` for the mod's own jar
    (Phase 0 §B `LoadSource.ModAsset` factory access).
- **Backing patch + failure handling:** every non-empty content transaction synthesizes
  one engine-owned decorator-only `ModBackedGamePatch`, ordered first within that
  owner. Explicit creator patches are optional and follow registration order.
  `register()` throwing discards the whole transaction; a base-game mismatch is a
  structured registration error.
- **Runtime fault boundary:** creator provider/event registrations are wrapped in
  owner-aware proxies, and `ObjectManager` invokes mod-keyed object callbacks through
  the same engine-owned `ModFaultBoundary`. Non-VM-fatal callback failure publishes
  an owner finding and stack trace, atomically marks the owner and dependents disabled
  in pending state for the next launch, and aborts the current gameplay session to the
  title. It never hot-unloads code or resumes a partially mutated frame. The manager
  reads the shared Phase 1 runtime-finding store. Creator patch-apply failure retains
  the separate Phase 0 launch-abort rule.

### B. Object/badnik modding

- Mod object classes extend the existing base classes
  (`AbstractObjectInstance` / `AbstractBadnikInstance`) and receive
  `ObjectServices` through the normal ThreadLocal construction path — no
  parallel object runtime (parent spec §3).
- **Identity model (recon-corrected):** stock `ObjectSpawn.objectId` remains the ROM's
  hard 8-bit id. Mod identity does **not** share that namespace:
  - Mod-supplied spawn data (mod zones, §D) references objects by
    **namespaced string key** (`mymod:buzzer2`).
  - `ObjectSpawn` gains an optional namespaced `objectKey`/tagged reference. When set,
    creation consults the mod-key registry; when absent, it delegates to the untouched
    stock registry. No byte allocator, requested-id hint, or placeholder census exists,
    so stock layouts cannot be hijacked.
  - Dynamic children use the same key/owner context or `spawnChild` factory ownership;
    save/editor/rewind data persists the key and owner, never a runtime number.
  - **Not in Phase 2:** injecting mod objects into *stock* zone layouts
    (that's layout mutation of base levels; revisit with demand).
- **Rewind (two engine fixes + one rule):**
  1. Snapshots store `(ownerModId, binaryClassName)`. `ModClassResolver` asks the
     boot-scoped owner-loader registry to load the name, so unregistered dynamic child
     classes work and identical FQNs in two mods remain distinct.
  2. Registry-path recreation persists and resolves the namespaced object key.
  3. Rule: mod objects wanting bespoke dynamic-recreate implement
     `RewindRecreatable` exactly like first-party objects. `ggfmod validate`
     (§E) checks recreate and field coverage and rejects every static except
     compile-time primitive/String constants. Custom mod static adapters are deferred
     because mod gameplay state must be instance- or session-owned.

### C. Art pipeline: baked sheets, reskins, pattern budget

- **Baked sheet format (SDK output, engine input):** a versioned container
  (one file per sheet in the mod jar) holding: pattern data (N × 32-byte
  4bpp Sega-format tiles — `Pattern.fromSegaFormat`'s exact layout: 8 rows ×
  4 bytes, high nibble = left pixel), mapping frames (per frame: piece list
  with `xOffset, yOffset, widthTiles, heightTiles, tileIndex, hFlip, vFlip,
  paletteIndex, priority` — the `SpriteMappingPiece` component set), palette
  line index, frame delay, and optionally a 16-color palette line. The
  engine-side reader materializes an `ObjectSpriteSheet` directly; the SDK
  writes the container from PNG + a small YAML sheet manifest
  (frame boxes / piece layout). No ROM byte formats in the container —
  `SpriteMappingPiece` fields are the schema, serialized plainly.
- **Reskins (`artOverrides`, deferred from Phase 1):** manifest maps base
  art keys (`Sonic2ObjectArtKeys` strings and their S1/S3K equivalents) to
  mod sheet assets; the patch's decorated `ObjectArtProvider` swaps the
  sheet at `loadArtForZone` time. Later mods win (parent §7). A reskin-only
  mod is manifest + baked sheets, zero code — it synthesizes an implicit
  data-only patch (no entrypoint needed), keeping Phase 1's no-code story
  for pure reskins.
- **Pattern-ID budget (closes the parent's open question; amended 2026-07-22):**
  each enabled mod is allocated one `0x8000`-pattern window, in enabled
  order, starting at the first window-aligned address at or above the highest
  permanent `PatternAtlasRange.endExclusive()`. The current highest permanent
  range is `MGZ_ZOOM_CUES`, ending at `0x188000`, so the first dynamic mod
  window starts at `0x188000`. (The original draft's `0x108000` start predated
  that permanent range.) Windows are registered via
  `PatternAtlas.registerRange(base, size,
  "mod:" + id)`. **Engine change required (recon):**
  `validatePatternIdGovernance` accepts only ids inside
  `PatternAtlasRange.forPatternId(...)` — the *enum* constants — so
  dynamically registered ranges are rejected at render time today.
  Governance must additionally consult the dynamically registered ranges,
  and `registerRange(int, int, String)` must enforce the same
  block-alignment invariant the enum tier gets (dynamic ranges live in the
  sparse-fallback storage tier, which is fine for mod volumes). Mods
  needing more than one window declare it in the manifest; the manager
  surfaces total budget use.
  Window assignments live in session state and are re-registered immediately after
  `LevelManager.initObjectArt()` clears dynamic ranges, before any mod art is cached;
  consecutive level loads and editor session rebuilds are regression tests.

### D. New zone in an existing game (flagship)

Recon identified exactly two structural blockers plus a seam list; the design
addresses each:

1. **`LevelData` enum is compile-time.** Zone enumeration moves behind a
   small interface: `LevelDescriptor` (levelIndex, startX, startY) with the
   enum constants adapting to it (a default `LevelData implements
   LevelDescriptor` retrofit — zero behavior change for stock zones), and
   `ZoneRegistry`/`AbstractZoneRegistry` typed against the interface. Mod
   zones provide synthetic descriptors with **synthetic level indices from a
   reserved band** (0x400+, far above every per-game enum band) so nothing
   collides with ROM-derived indices.
2. **`Sonic2.loadLevel` resolves every data address from ROM directories by
   zone id.** Mod zones never enter that path: the mod's `GamePatch`
   decorates the module so that for synthetic level indices, `loadLevel`
   routes to a **`ModZoneLoader`**, and `getMusicId(levelIdx)` (which
   otherwise reads garbage from `LEVEL_SELECT_ADDR` for synthetic indices)
   likewise resolves from the definition. The loader builds the level with
   a `LoadSource.ModAsset` plan (patterns/chunks/blocks/collision — the
   four plan-shaped kinds, Phase 0 §B) and a **`ModLevelDefinition`**
   asset: layout map, object/ring spawn lists (object entries by
   tagged namespaced key), 4×16-color palettes,
   solid-tile collision data, boundaries, start positions, music reference (stock
   SMPS id or namespaced Phase 1 `TrackKey`), zone display name. Its schema and exact
   export-directory contract are pinned by the cross-phase format/security spec.
   **Engine change required (recon):** the existing plan constructor
   (`Sonic2Level(rom, zone, characterPaletteAddr, levelPalettesAddr, …,
   LevelResourcePlan, mapAddr, solidTileHeightsAddr, …)`) still takes the
   layout map, palettes, and solid-tile data as **ROM address ints** —
   exactly the seams Phase 0 §B deferred. Phase 2 adds a constructor
   builder accepting in-memory layout/palette, solid height/width/angle profiles,
   and primary/secondary collision
   data alongside the plan, reusing the existing decode logic; this is the
   promised "provider-level overrides" realization, confined to
   `Sonic2Level` (S3K mod zones follow the same shape when demanded).
3. **Progression: append-only indices + a successor redirect.** Zone
   identity in S2's events, scroll handlers, and save slots IS the
   progression list index (`Sonic2LevelEventManager`/
   `Sonic2ScrollHandlerProvider` switch on `ZONE_EHZ=0..ZONE_DEZ=10`;
   `S2SaveSnapshotProvider` persists the index), so **splicing a mod zone
   into the stock order is unsafe** — every downstream stock zone would
   inherit its neighbor's events and existing saves would silently retarget.
   Therefore:
   - Mod zones are **appended at stable indices ≥ the stock count**
     (S2: 11+). Stock indices never move; stock saves are untouched; the
     index-keyed event/scroll switches see an out-of-range index for mod
     zones and fall to their graceful defaults (recon-verified `default ->
     null` / 1:1 scroll), exactly like the synthetic-ROM-id story in item 4
     covers the ROM-id-keyed paths.
   - **Reachability via a successor redirect (engine change):**
     `advanceToNextLevel()` gains a `ZoneProgressionPlan` seam constructed with an
     immutable `ZoneTopology` snapshot (zone count, act counts, terminal behavior).
     Its default is today's linear list order, bit-identical. A patch
     overrides successors, e.g. "MTZ results → mod zone 11; mod zone 11
     results → SCZ (8)". Redirects are only valid at **results-driven**
     boundaries (S2: EHZ..MTZ; the SCZ→WFZ→DEZ tail is event/cutscene
     chained and DEZ's boss requests credits directly — recon); the
     manifest declares `insertAfter: <stock zone>` and a redirect after an
     event-chained zone is refused at load with a manager badge. Default:
     after the last results-driven stock zone.
     Multiple enabled mods may target the same stock anchor: they form one chain in
     stable effective order (`anchor → first mod → ... → last mod → original stock
     successor`). Dependencies therefore precede dependents in the chain; disabling a
     member rebuilds the chain without dangling redirects. Duplicate/invalid mod-zone
     identities are refused rather than silently shadowed.
     One owner may register multiple zones. Synthetic indices allocate first by
     effective owner order and then by that owner's registration order. Zones sharing
     an anchor chain in that same order; explicit different valid anchors form
     independent chains. Duplicate owner-local keys fail the whole registration
     transaction. Disable/rebuild recomputes indices/chains from the frozen effective
     contributions. Saves/data-select persist tagged `ZoneKey.mod(modId, localName)`,
     never the recomputed synthetic index. Stock saves retain their legacy numeric
     index. On load, a mod key resolves against the current registry; reordering or
     disabling another owner cannot retarget it. A missing/disabled key preserves the
     slot, reports a finding, and restarts at zone 0. A legacy numeric value at or
     above the stock count without a key also falls back and can never bind to a
     newly allocated mod index.
   - Data-select support is NOT automatic (recon: `S2DataSelectProfile`
     hardcodes an 11-entry `CLEAR_RESTARTS` list with bounds checks, and
     the S2 preview-image cache uses fixed zone-key sets). Phase 2 extends
     the S2 data-select host profile (module-owned, so the patch can
     decorate it) to consult tagged saved zone identity: mod-zone slots get a
     **generic mod-zone preview
     tile** and a registry-driven restart destination. A slot saved in a
     mod zone and later loaded with that mod disabled follows the parent
     §7 rule (preserved, not quarantined) and restarts at zone 0 with a
     logged notice.
4. **Synthetic ROM zone id:** mod zones carry a synthetic `zoneIndex` from
   a reserved band (0x40+) used only by the graceful per-zone switches
   (palette cycler, event manager, boss art, resource plans — all default
   to no-op/null for unknown ids, recon-verified). The known non-graceful
   consumer is `TitleCardManager`'s hardcoded `ZONE_NAMES` array (a
   duplicate of the registry's name list, with a wrong-name
   `ZONE_NAMES[0]` fallback for out-of-range zones); it gains a
   registry-driven fallback (name from `ZoneRegistry.getZoneName`), which
   also removes the existing fallback bug. (Further hardcoded name copies
   exist in the debug-only level-select screens; those are out of scope —
   mod zones simply don't appear there in Phase 2.)
5. **Minimal viable zone is genuinely minimal:** static palette, no
   animation, no events, no water — every runtime framework
   (`ZoneRuntimeRegistry`, `PaletteOwnershipRegistry`,
   `AnimatedTileChannelGraph`, palette cycler, event manager) defaults to
   no-op for unregistered zones (recon-verified). Ambitious mod zones
   register real handlers through their patch code.
6. **Trace/rewind:** `TraceCatalog`'s S2/S3K zone mapping is pass-through
   (inert for mod zones); `ZoneRuntimeSnapshot` identity validation only
   engages if the mod installs a `ZoneRuntimeState`, which must then report
   its stable synthetic `zoneIndex()`.

### E. `ggfmod` SDK/CLI

- **Location and published artifact:** sources live in
  `com.openggf.tools.modsdk` inside the existing engine module, following the tools
  CLI conventions (`main()` + pure-logic split), but release packaging also attaches
  a separate `openggf-mod-sdk` classifier jar containing those converter/packager
    classes, templates, and service resources. An attached classifier shares the main
  POM and cannot depend on its own unclassified artifact, so distribution/build docs
  explicitly require both engine and SDK jars. This satisfies the parent's
  two-artifact contract without a risky multi-module source-tree move.
  CLI entry `GgfModCli` dispatching subcommands; invoked
  `java -cp <engine-jar><path-separator><openggf-mod-sdk-jar>
  com.openggf.tools.modsdk.GgfModCli <cmd>` (a thin
  `ggfmod` script ships in `docs/modding/`).
- Subcommands (parent §5, made concrete):
  - `init` — scaffold a Maven mod project (manifest, compilable sample badnik using
    `ObjectScaffoldTool`'s generator style, minimal editor-export level source,
    sample sheet manifest, and build wiring). Phase 3 extends the same template with
    the original character stub once the character API exists.
  - `convert art` — PNG + sheet manifest → baked sheet container (§C);
    quantization report; hard errors: >16 colors/line, non-8×8-multiple
    dimensions, piece boxes outside the image.
  - `convert level` — editor full-level export (§G) → plan assets
    (pattern/chunk/block/collision binaries) + `ModLevelDefinition`.
  - `convert audio` — Phase 1's audio validation (loop metadata, wav/ogg).
  - `validate` — manifest schema, asset integrity, object-key and pattern
    budget checks, entrypoint presence, and the checks the engine's
    source-scan guards can't do on mods: object classes extend the base
    classes, instance rewind rules, the compile-time-static-only rule,
    recreate path, `final` scalar inventory, object-ref field inventory,
    constructor `services()` calls, and ref-field capture usage via **ASM over jar
    bytes before any owner loader exists**. References to reachable but non-`@ModApi`
    engine types are supported-surface warnings, not validation errors—the trust model
    permits them but gives no compatibility promise. **Declared new dependency:**
    `org.ow2.asm:asm` (compile scope, small and stable), required because the engine's
    own guard is a source scanner with nothing reusable for external compiled jars.
  - `package` — run `validate` and fail on any error before assembling the jar. At
    boot, the engine independently recomputes the structural validator from jar bytes
    before loader creation; it does not trust an embedded author record.
  - `run` — dev-mode launch with the mod from build output. (Correction:
    dev mode is **built in Phase 2**, not inherited — Phase 1 ships no dev
    flag; the scanner gains exploded-directory support behind a system
    property here, exempt from trace/test force-disable per §7.) The exact property
    is `ggfmod.dev.modDir`; only its explicit presence enables the exemption, and the
    directory is copied once into an engine-owned immutable snapshot before parsing.

### F. Trust gate

- Enabling a mod with `containsCode` requires a one-time trust grant: the
  manager's existing two-press arm/commit idiom (Phase 1's cascade-confirm
  pattern) shows *"This mod contains code and runs with full permissions"*
  on first press; second press grants. The grant persists on
  `ModState.Entry` as two added fields: `trusted` (boolean) and
  `trustedJarSha256` (the jar's content hash recorded at grant time); a
  hash mismatch at scan time revokes the grant for the next boot and re-prompts;
  enable/trust changes show Restart required (hashing
  happens once per scan, not per frame). Format compatibility is a
  non-issue by construction: Phase 1's `ModStateStore` parses
  `modstate.json` into a plain `Map` (not a POJO), so added fields are
  tolerated in both directions. Untrusted code mods stay disabled with a
  badge. Data-only mods are unaffected.

### G. Editor mod-facing features (the Phase 2 half of parent §6)

- **Chunk/block library pane** sourced from the active mod's tileset, plus an upgrade
  of Phase 0's browsable stock object palette: append namespaced mod keys and asset
  previews while retaining stock ids/names/subtype behavior.
- **Full-level export** ("export to mod project"): unlike Phase 0's
  delta-envelope, serializes the complete level from `MutableLevel` —
  patterns, chunks, blocks, map, solid tiles + chunk collision indices,
  palettes, spawns, boundaries, start position — into the mod project's
  source tree where `ggfmod convert level` bakes it.
- **Envelope spawn entries for mod objects persist the namespaced key, never a byte
  id.** This is envelope **v3**: v1/v2 readers quarantine it rather than interpreting a
  drifting numeric fallback. A missing mod key skips that spawn with a structured
  logged count. Stock v2 entries remain unchanged. Version-specific DTO/hash rules are
  pinned by the cross-phase format/security contract. This is also the
  new-zone authoring entry point: start from a snapshot of any loaded stock
  zone (`MutableLevel.snapshot`), edit freely, export as a NEW zone (the
  export never contains ROM-copyright-relevant data beyond what the creator
  chose to keep — the docs make the licensing implication explicit: shipping
  a lightly-edited stock zone ships Sega's level data, and mod authors are
  responsible for their content).
- **`@ModApi` surface:** annotation `com.openggf.game.ModApi` (RUNTIME
  retention) applied to the curated set — `GgfMod`, `ModContext`,
  `GamePatch` + `PatchContext` + `GameplayLaunchRequest`, `ObjectServices`,
  `AbstractObjectInstance`, `AbstractBadnikInstance`, `ObjectSpawn`,
  `ObjectControlState`, `ObjectLifetimeOps`, `ObjectPlayerQuery`,
  `PhysicsProfile`, the `level.objects` utility helpers, the baked-sheet
  reader types, `ModLevelDefinition`. (The set spans four packages —
  recon-confirmed — the annotation, not a package, defines the surface.)
  The guard closes the surface transitively: every non-JDK type in an annotated
  public/protected signature must also be annotated, including creator-facing
  provider/handler interfaces, `RewindRecreatable`, `GameModule`, `ObjectFactory`,
  and registration handles. The exact annotated inventory drives generated Javadoc
  published beside the engine and `openggf-mod-sdk` jars. Trusted mods may still reference internal
  types, but validation reports only a compatibility warning for those references.
  A signature-snapshot guard freezes public API, protected subclass hooks, generic
  signatures, exceptions, annotations, and transitive signature types (additions
  allowed within semver rules; removals/changes fail). Phase 2 is additive over the
  Phase 1 format surface, so the mod API version becomes `1.1.0`, not a new major.
  The canonical Phase 1 range `>=1.0.0 <2.0.0` remains eligible.

## Explicitly out of scope for Phase 2

Streamed SFX overrides (Phase 1 follow-on), characters and standalone games
(Phase 3), Tiled import and docs site (Phase 4), mod objects in stock zone
layouts, S1-based mod zones (S1 bypasses the plan seam entirely — Phase 0 §B
scope note), lifting the editor's S3K runtime re-apply gate.

## Verification strategy

- Unit: stock-id/mod-key isolation; baked-sheet golden round-trip (SDK write → engine
  read → `ObjectSpriteSheet` equality); `ModClassResolver` recreate
  round-trip with a test jar + child loader; `LevelDescriptor` retrofit
  (stock zones bit-identical); synthetic-index routing.
- Integration: a **sample mod** built by `ggfmod init` in CI style — one
  badnik + one minimal zone appended to S2 — loaded headless, zone
  playable-loads, badnik spawns, snapshot/restore round-trips.
- Regression gates: full suite, S3K must-keep-green, trace spot sweep with
  the sample mod force-disabled (mods-off behavior bit-identical), and one
  sweep with it enabled but its zone unvisited (stock zones unaffected).

## Resolved contracts and remaining polish

Object-id headroom is no longer a question because mod objects use namespaced tagged
references. All binary/JSON formats and hostile-input limits are authoritative in
`docs/superpowers/specs/2026-07-10-mod-support-format-security-contracts.md`.
- **`TitleCardManager` letter art for mod zone names:** stock title cards
  compose from ROM letter art; a mod zone name may need character coverage
  the ROM set lacks. Fallback: skip the title card for mod zones (config
  default) until mod-supplied title art is specced.
