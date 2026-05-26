# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OpenGGF is an open-source, Java-based game engine for research and preservation of classic Mega Drive / Genesis platform games, specifically the mainline Sonic the Hedgehog series. It faithfully reimplements the physics and rendering behaviour of the original hardware using data loaded from user-supplied ROM images (Sonic 1, 2, and 3&K). No copyrighted assets are included in this repository.

**Critical requirement:** The engine must replicate original physics pixel-for-pixel. Accuracy is paramount. Always verify against the disassembly.

## Current Work Priorities

The current delivery priority is **S3K playable vertical-slice parity**, not broad architecture migration for its own sake. Prefer work that closes an actual route through Sonic 3 & Knuckles: traversal objects, badniks, bosses, event/camera flow, scroll/parallax, animated tiles, palette/PLC state, sidekick behavior, trace blockers, rewind-relevant state, and visual validation.

Use the runtime-owned framework stack when the slice touches those systems: `ZoneRuntimeRegistry`, `PaletteOwnershipRegistry`, `AnimatedTileChannelGraph`, `ZoneLayoutMutationPipeline`, `ScrollEffectComposer`, `SpecialRenderEffectRegistry`, and `AdvancedRenderModeController`. Uplift S1/S2 or older S3K code opportunistically when it removes active duplication or risk, but do not let broad cleanup displace playable S3K progress.

Default order of work:
1. Close AIZ -> HCZ route blockers first.
2. Feed CNZ, MGZ, and ICZ work into the same slice standard.
3. Implement S3K objects by route impact: traversal blockers, terrain modifiers, hazards, bosses/miniboss support, then high-usage badniks.
4. Treat data select, special stages, and broad S1/S2 framework uplift as follow-up polish unless they directly block the active slice.

## Build & Run Commands

```bash
mvn package                          # Build (creates executable JAR with dependencies)
mvn test                             # Run tests
mvn "-Dtest=TestCollisionLogic" test # Run a single test class in PowerShell
java -jar target/OpenGGF-0.6.prerelease-jar-with-dependencies.jar  # Run (requires ROM)
```

Maven Silent Extension (MSE) is configured in this repo via `.mvn/extensions.xml`, and `.mvn/maven.config` enables `-Dmse=relaxed` by default for repo-local Maven commands. Use `-Dmse=off` when full Maven logs are needed.
In PowerShell, quote Maven `-D...` properties, especially focused test selectors such as `"-Dtest=com.openggf.package.TestClassName"`, so the shell does not reinterpret dots or punctuation.

Tests in this repository must use JUnit 5 / Jupiter only. Do not add JUnit 4 tests, rules, runners, or `org.junit.*` imports.

Trace replay frontier work must keep `docs/TRACE_FRONTIER_LOG.md` current.
Update it when a trace frontier moves, a trace fix is committed, a previously
passing trace regresses, or a full `*TraceReplay` sweep is used to choose the
next target. Record the command, commit/worktree context, pass/fail status,
error count, and first-error frame/field.

## Branch Documentation Policy

Tracked Git hooks live in `.githooks/`. Configure the repo with `git config core.hooksPath .githooks` so local commits and merges are checked against the policy below. CI mirrors the same rules on PRs into `develop`. The hook entrypoints dispatch through `.githooks/run-policy`: Windows uses `validate-policy.ps1`, while macOS/Linux use `validate-policy.sh`.

- Every non-`master` branch commit must carry these commit-message trailers, each starting with `updated` or `n/a`: `Changelog`, `Guide`, `Known-Discrepancies`, `S3K-Known-Discrepancies`, `Agent-Docs`, `Configuration-Docs`, `Skills`.
- `prepare-commit-msg` auto-appends the trailer block on non-merge commits. Fill it in rather than removing it. Each trailer maps to a file/dir (e.g. `Changelog` → `CHANGELOG.md`, `Agent-Docs` → both `AGENTS.md` and `CLAUDE.md`, `Skills` → staged files under both `.agents/skills/` and `.claude/skills/`). If the mapped files are staged, the trailer must not say `n/a`. See `.githooks/run-policy` for the authoritative mapping.
- When merging a non-`master` branch into `develop`, stage a `README.md` update summarizing the branch change in the release/change log section.
- The trailer block is the required attestation for the repo's "where relevant" documentation/discrepancy checks. Do not bypass it with `--no-verify`.

## ROM Requirement

Keep ROMs in the working directory (gitignored):
- `Sonic The Hedgehog (W) (REV01) [!].gen`
- `Sonic The Hedgehog 2 (W) (REV01) [!].gen`
- `Sonic and Knuckles & Sonic 3 (W) [!].gen`

For S3K tests: `-Ds3k.rom.path="Sonic and Knuckles & Sonic 3 (W) [!].gen"`. `TestRomLogic` is skipped when ROM is absent.

## Reference Materials

- **`docs/s2disasm/`** - Sonic 2 disassembly (68000 assembly). Essential for accuracy verification.
- **`docs/skdisasm/`** - Sonic 3&K disassembly. Primary reference for S3K level layout and collision.
- **`docs/s1disasm/`** - Sonic 1 disassembly.
- **`docs/SMPS-rips/SMPSPlay/`** - SMPS audio driver source and reference implementations

These directories are untracked but available locally.

## ROM Offset Finder Tool

Use **RomOffsetFinder** (`com.openggf.tools.disasm.RomOffsetFinder`) to search disassembly items and find ROM offsets across S1, S2, S3K via `--game s1|s2|s3k` (auto-detects from disasm path otherwise). The `s3k-disasm-guide` / `s2disasm-guide` / `s1disasm-guide` skills cover the full command catalog (`search`, `verify`, `find`, `test`, `plc`, etc.).

**S3K note:** Compression type is encoded in label suffix (e.g. `AIZ1_8x8_Primary_KosM`) — the tool auto-infers, no file extension needed.

## Architecture

### Entry Point
`com.openggf.Engine` - GLFW window with manual timing loop (`display()` -> `update()` -> `draw()`).

### Two-Tier Service Architecture

The engine uses a **two-tier service model** that separates global access from per-object context:

**Tier 1: `GameServices` (static facade)** — Global access for managers, event handlers, and other non-object code. Exposes gameplay-scoped accessors (`camera()`, `level()`, `parallax()`, `water()`, `gameState()`, `timers()`, `sprites()`, `fade()`, `collision()`, `debugOverlay()`, ...) which require an active `GameplayModeContext`, plus engine globals (`rom()`, `audio()`, `graphics()`, `configuration()`, `module()`, ...) resolved via `EngineServices`, plus `*OrNull()` variants. See `GameServices.java` for the full surface.

**Tier 2: `ObjectServices` (injected per-object)** — Context-specific services for game objects. The interface lives at `com.openggf.level.objects.ObjectServices`; `DefaultObjectServices` is the production implementation, backed by `GameplayModeContext` and `EngineContext`. Inside an `AbstractObjectInstance` subclass, call `services()` to reach managers (`objectManager`, `renderManager`, `audioManager`, `camera`, `gameState`, `zoneFeatureProvider`, ...) plus audio shortcuts, level-transition requests, world session, RNG, ROM, config. See `ObjectServices.java` for the full surface.

Objects receive `ObjectServices` via injection at construction time (ThreadLocal context set by `ObjectManager`). **Never call `getInstance()` from object code** — use `services()` instead.

### Session Ownership (post runtime-ownership migration)

Gameplay state is split by lifetime across three layers in `com.openggf.game.session`:

- **`WorldSession`** — durable across editor mode swaps. Owns the active `GameModule`, loaded `Level`, and zone/act metadata.
- **`GameplayModeContext`** — disposable, rebuilt on each gameplay session entry. Owns all gameplay-scoped managers (camera, timers, game state, fade, RNG, water, parallax, collision, sprites, level, ...) plus the runtime-shared registries below. Provides `initializeFreshGameplayState()` for editor-exit counter reset.
- **`SessionManager`** — manages lifecycle (`openGameplaySession`, `enterEditorMode`, `resumeGameplayFromEditor`).

Editor entry/exit uses teardown+rebuild (no parking): the mode context is destroyed/recreated while `WorldSession` survives, then `LevelManager.restoreInheritedLevel()` reapplies any `MutableLevel` edits. The old `GameRuntime`/`RuntimeManager` façade is retired — prefer explicit dependencies from `GameplayModeContext`, `GameServices`, or `ObjectServices`. See `docs/superpowers/specs/2026-04-07-runtime-ownership-migration-design.md` for the full design.

### Runtime-Shared Framework Stack

`GameplayModeContext` hosts the shared registries/controllers used to normalize zone-specific behavior across games (accessed through `GameServices` or directly via `gameplayMode.getX()`):

- `RewindRegistry` / `RewindController` / `PlaybackController` - Gameplay-scoped keyframe capture, deterministic seek/replay, held-rewind trace debugging, and field-capture coverage audits. Automatic capture currently uses `GenericFieldCapturer`, `GenericRewindEligibility`, `@RewindTransient` / `@RewindDeferred`, stable identity ids in `com.openggf.game.rewind.identity`, and compact schema codecs/policies in `com.openggf.game.rewind.schema` (`CompactFieldCapturer`, `RewindCodecs`, `RewindPolicyRegistry`, `RewindSchemaRegistry`). The standalone `RewindFieldInventoryTool` lives at `com.openggf.tools.rewind`. Default non-badnik object subclasses use compact schema-backed sidecar state when all default scalar fields have codecs. Object coverage should prefer central eligibility, codecs, and policy-registry rules over repeated per-object annotations or rewind overrides unless bespoke state requires it.
- `ZoneRuntimeRegistry` - Typed per-zone runtime state adapters over raw event/state bytes
- `PaletteOwnershipRegistry` - Multi-writer palette arbitration, precedence, and underwater mirroring
- `AnimatedTileChannelGraph` - Shared animated tile channels for script-driven and custom tile uploads
- `ZoneLayoutMutationPipeline` - Deterministic queued/immediate live layout edits and redraw sequencing
- `SpecialRenderEffectRegistry` - Staged additional render passes layered into the normal scene
- `AdvancedRenderModeController` - Frame-level render-mode state such as per-line/per-cell scroll overrides

Related scroll/deform reuse lives in `level.scroll.compose`, centered on `ScrollEffectComposer` and helper plans such as `DeformationPlan` and `WaterlineBlendComposer`.

When adding or refactoring gameplay systems, prefer plugging into these runtime-owned frameworks rather than introducing new zone-local registries or one-off manager state.

### Core Managers
- **LevelManager** - Thin coordinator after decomposition (see below)
- **SpriteManager** - All game sprites, input handling, render bucketing
- **GraphicsManager** - OpenGL rendering, shader management
- **AudioManager** - SMPS audio driver, YM2612/PSG synthesis
- **Camera** - Camera position tracking

### LevelManager Decomposition

`LevelManager` has been decomposed into focused subsystems:

| Class | Responsibility |
|-------|----------------|
| `LevelManager` | Thin coordinator, level load orchestration |
| `LevelTilemapManager` | Tilemap loading, chunk/block management, VRAM upload |
| `LevelTransitionCoordinator` | Act transitions, seamless loading, warp sequences |
| `LevelDebugRenderer` | All debug overlay rendering (collision, chunks, paths) |
| `LevelGeometry` *(record)* | Immutable level dimension/boundary data |
| `LevelDebugContext` *(record)* | Snapshot of debug state for rendering |

### MutableLevel

`MutableLevel` (`com.openggf.level`) provides snapshot + mutation + dirty-region tracking for level tile data. Foundation for the planned level editor. Uses `Block.saveState()/restoreState()` for undo/redo. Dirty regions are processed per-frame via `LevelFrameStep.processDirtyRegions()` (`LevelFrameStep` is at the `com.openggf` package root, not under `com.openggf.level`).

### Key Packages
Package names are generally self-describing; a few with non-obvious facts:

| Package | Note |
|---------|------|
| `level.objects` | Hosts `ObjectServices` (object interface lives here, not under `game`) and shared base classes / utility helpers |
| `level.scroll.compose` | Shared deform/parallax composition helpers built around `ScrollEffectComposer` |
| `audio.*` | Split across `audio` (backend), `audio.synth` (chip emulation), `audio.smps` (sequencer/loader), `audio.driver`, `audio.runtime`, `audio.rewind`, `audio.debug` |
| `game` | Core game-agnostic interfaces incl. `GameServices`, `PlayableEntity`, `DamageCause`. `DataSelectProvider` itself lives here even though the framework is under `game.dataselect` |
| `game.zone` / `palette` / `animation` / `mutation` / `render` | Runtime-owned shared frameworks (zone state, palette ownership, animated tiles, layout mutation, special render effects, advanced render modes) |
| `game.sonicN.*` | Per-game modules, level loading, audio/data select implementations, constants |
| `tools` | Compression utilities (Kosinski, Nemesis, Saxman) plus `ObjectDiscoveryTool` and disassembly tools (incl. `RomOffsetFinder`) |

`LevelFrameStep` lives at the `com.openggf` package root, not under `level`.

### Consolidated Subsystems

**ObjectManager** inner classes: `Placement` (spawn windowing), `SolidContacts` (riding/landing/ceiling/side collision), `TouchResponses` (enemy bounce/hurt), `PlaneSwitchers` (plane switching logic). Injects `ObjectServices` into all objects at construction time.

**RingManager** inner classes: `RingPlacement` (collection state, sparkle, spawning), `RingRenderer` (cached pattern rendering), `LostRingPool` (lost ring physics).

**PlayableSpriteController** (`sprites.playable`) coordinates `DrowningController` (`sprites.playable`) and three managers in the sibling `sprites.managers` package: `PlayableSpriteMovement` (physics), `PlayableSpriteAnimation` (animation state), `SpindashDustController` (spindash dust effects).

**CollisionSystem** (`com.openggf.physics`) - Unified collision orchestration: terrain probes via `TerrainCollisionManager`, solid object resolution via `ObjectManager.SolidContacts`, post-resolution ground mode/headroom checks. Supports trace recording via `CollisionTrace`.

**UiRenderPipeline** (`com.openggf.graphics.pipeline`) - Render ordering: Scene -> HUD overlay -> Fade pass. `Engine.display()` uses it for screen transitions.

**Sonic2LevelAnimationManager** - Implements `AnimatedPatternManager` and `AnimatedPaletteManager` (pattern animation scripts + zone-specific palette cycling).

**CNZBumperManager** - Placement windowing and ROM-accurate bounce physics for all 6 bumper types.

### Terminology (differs from standard Sonic 2 naming)
- **Pattern** = 8x8 pixel tile
- **Chunk** = 16x16 pixel tile (composed of Patterns)
- **Block** = 128x128 pixel area (composed of Chunks)

### Configuration
`SonicConfigurationService` loads from `config.json`. Key bindings are stored as GLFW key-name strings (e.g. `"D"` / `"GLFW_KEY_D"`) and resolved to integer key codes at lookup. See [CONFIGURATION.md](CONFIGURATION.md) for the full key list. Two flags worth flagging:

- `SHOW_LEGAL_DISCLAIMER_ON_STARTUP` — boot through `GameMode.LEGAL_DISCLAIMER` first (default `true`). Set `false` in tests that boot the full `Engine`.
- `TEST_MODE_ENABLED` — replaces the master-title game-select with a trace picker (dev-only; requires `TRACE_CATALOG_DIR`).
- `TRACE_CATALOG_DIR` — directory scanned by `TraceCatalog` (default `src/test/resources/traces`).

**Startup order:** `Engine.init()` now boots through `GameMode.LEGAL_DISCLAIMER` first when `SHOW_LEGAL_DISCLAIMER_ON_STARTUP=true` (the default). The disclaimer screen owns a `FadeManager` reveal, a 5-second readability gate, and a fade-to-black on dismiss; control then chains into the existing `MASTER_TITLE_SCREEN` or direct-gameplay path inside `Engine.exitLegalDisclaimer()`. Set the flag `false` in tests that boot the full `Engine`.

## Level Resource Overlay System

Some zones share level resources with overlays (e.g., HTZ shares base data with EHZ, then applies HTZ-specific pattern/block overlays). Implemented in `com.openggf.level.resources`:

- `LoadOp` - Single load operation (ROM address, compression, dest offset)
- `LevelResourcePlan` - Lists of LoadOps for patterns, blocks, chunks, collision
- `ResourceLoader` - Loading with overlay composition (copy-on-write)
- `Sonic2LevelResourcePlans` - Factory for zone-specific resource plans

To add overlay support for other zones: add ROM offsets to `Sonic2Constants`, create a plan in `Sonic2LevelResourcePlans`, update `getPlanForZone()`.

- **PLC system:** `PlcParser` in `level.resources` provides game-agnostic PLC parsing. See `plc-system` skill for cross-game reference, `s3k-plc-system` for S3K-specific details.

## Multi-Game Support Architecture

Game-specific behavior is isolated behind the `GameModule` interface. `GameModuleRegistry` holds the current module, `RomDetectionService` auto-detects ROM type.

Each `GameModule` exposes per-game providers for zones, objects, audio, physics, level events, art, data select, save snapshots, etc. — see `GameModule.java` for the authoritative list.

Each game has its own module (`Sonic1GameModule`, `Sonic2GameModule`, `Sonic3kGameModule`) and `RomDetector`.

## Unified Level Event Framework

Level events (boss arena setup, dynamic boundaries, zone transitions) are managed through a shared base class with game-specific subclasses:

- **`AbstractLevelEventManager`** (`game/`) - Shared state machine mechanics: dual routine counters (`eventRoutineFg` and `eventRoutineBg`; S1/S2 only use Fg, S3K uses both), zone/act tracking, `initLevel()`/`update()` lifecycle, boss spawn coordination.
- **`Sonic1LevelEventManager`** (`game/sonic1/events/`) - S1 zone event handlers. Per-zone handler classes.
- **`Sonic2LevelEventManager`** (`game/sonic2/`) - S2 zone event handlers (HTZ earthquake, boss arenas, EHZ/CPZ/ARZ/CNZ events).
- **`Sonic3kLevelEventManager`** (`game/sonic3k/`) - S3K zone event handlers. Per-zone handler classes (in `game/sonic3k/events/`): `Sonic3kAIZEvents`, `Sonic3kCNZEvents`, `Sonic3kHCZEvents`, `Sonic3kICZEvents`, `Sonic3kMGZEvents`. Other zones not yet covered.
- **`PlayerCharacter`** enum (`game/`) - Character identity enum (`SONIC_AND_TAILS`, `SONIC_ALONE`, `TAILS_ALONE`, `KNUCKLES`) matching ROM's `Player_mode` variable for character-specific branching in event logic.

Each `GameModule` returns its game-specific subclass via `LevelEventProvider`. Call sites use `AbstractLevelEventManager` for polymorphic access.

## Per-Game Physics Framework

Physics differences across S1/S2/S3K are handled through a layered provider system:

| Class | Purpose |
|-------|---------|
| `PhysicsProfile` | Immutable per-character movement constants (18 fields, values in subpixels where 256=1px) |
| `PhysicsModifiers` | Water/speed shoes multiplier rules (shared `STANDARD` across all games) |
| `PhysicsFeatureSet` | Feature flags gating mechanics per game (primary extension point) |
| `CollisionModel` | Enum: `UNIFIED` (S1) vs `DUAL_PATH` (S2/S3K) |
| `PhysicsProvider` | Interface tying above together, per game module |

### Resolution Flow
1. `AbstractPlayableSprite` constructor calls `defineSpeeds()` (S2 fallback values)
2. Then `resolvePhysicsProfile()` queries `GameModuleRegistry.getCurrent().getPhysicsProvider()`
3. Profile values overwrite fallbacks; modifiers and feature set are cached
4. Getters apply modifiers dynamically (water/speed shoes)
5. Feature set gates checked at call sites

### PhysicsFeatureSet Fields

`collisionModel` selects `UNIFIED` (S1) vs `DUAL_PATH` (S2/S3K) and routes collision code paths — see the section below. All other per-game flags (spindash, angle thresholds, look-scroll delay, water shimmer, elemental shields, edge balance, sidekick rules, etc.) live on `PhysicsFeatureSet`. See `PhysicsFeatureSet.java` and its `SONIC_1` / `SONIC_2` / `SONIC_3K` factory constants for the authoritative list.

### Collision Model: UNIFIED vs DUAL_PATH

**S1 (UNIFIED):** Single collision index, solidity bits hardcoded per-routine, no dynamic path switching.

**S2/S3K (DUAL_PATH):** Dual collision indices (Primary bits 0x0C/0x0D, Secondary bits 0x0E/0x0F), per-sprite `top_solid_bit`/`lrb_solid_bit`, plane switchers dynamically swap collision paths.

The setters `setTopSolidBit()`/`setLrbSolidBit()` on `AbstractPlayableSprite` silently ignore calls when `CollisionModel.UNIFIED`, making springs and plane switchers automatic no-ops for S1.

### Adding Per-Game Physics Differences

1. Identify difference in disassembly with exact ROM references
2. Add field to `PhysicsFeatureSet` (behavioral toggle), `PhysicsProfile` (per-character constant), or `PhysicsModifiers` (multiplier rule)
3. Gate behavior at call site - S2 behavior is always the fallback when `physicsFeatureSet` is null
4. Add tests following `TestSpindashGating`/`TestCollisionModel` pattern (TestableSprite inner class, no ROM/OpenGL required)

**Rules:** Always verify against disassembly. Never use game-name `if/else` chains - always use feature flags. Per-game behavioral differences must be gated by feature flags (usually on `PhysicsFeatureSet`), never by code like `if (module.getGameId() == GameId.S1)`. When a ROM-level divergence is discovered, add a flag to `PhysicsFeatureSet`, set the correct value on each game's `SONIC_1` / `SONIC_2` / `SONIC_3K` constant, and branch on the flag at the call site.

Trace fixes must not add zone/route/frame carve-outs. If a trace diverges in AIZ, CNZ, MGZ, or any other zone, model the ROM state that actually drives the branch: object id/routine, status/control bits, frame-counter visibility, physics profile, event flag, or data-driven object/profile condition. Do not branch on zone id/name, trace route, frame number, or a "known failing trace" exception. "Use ROM-default behaviour except in AIZ" is still a zone-specific carve-out and is not acceptable. Zone/event/object providers may expose ROM state at the owning boundary, but shared physics/sidekick/object code must consume semantic predicates and must not branch solely because `zone == AIZ` or similar.

### Physics Tests

Tests in `src/test/java/com/openggf/game/`: `TestPhysicsProfile`, `TestPhysicsProfileRegression`, `TestSpindashGating`, `TestCollisionModel`.

## Object & Badnik System

Objects use a factory pattern with game-specific registries. `ObjectRegistry` creates `ObjectInstance` from `ObjectSpawn`. Factories registered via `AbstractObjectRegistry` subclasses (`Sonic1ObjectRegistry`, `Sonic2ObjectRegistry`, `Sonic3kObjectRegistry`).

**Service injection:** All objects receive `ObjectServices` at construction via `ObjectManager`. Inside any object, call `services()` to access camera, audio, level, game state, and zone features. **Never use `getInstance()` in object code.**

**Child spawning:** Use `spawnChild(() -> new ChildObject(spawn, params))` instead of manually calling `ObjectManager.addDynamicObject()`. Direct manager insertion is reserved for manager/framework bridge code with focused tests.

Badniks extend `AbstractBadnikInstance` (`com.openggf.level.objects` — game-agnostic) which provides touch response collision, destruction behavior via `DestructionEffects`, and movement/animation framework. Subclasses implement `updateMovement()` and `getCollisionSizeIndex()`.

**Object behavior contracts:** New object/boss/badnik/trace work should prefer the shared contracts for object physics standardization: `ObjectControlState` (control bits + derived predicates), `ObjectPlayerQuery`/`ObjectPlayerParticipationPolicy` (which players a routine targets), `NativePositionOps` (playable-sprite `x_pos`/`y_pos` writes), `ObjectLifetimeOps` (destruction/offscreen/respawn/slot transfer). Canonical behavior profiles live under `com.openggf.game.profiles.*`. Raw setters and direct `setDestroyed(true)` calls are legacy compatibility — do not grow guard baselines for new implementations without documenting the exact reason.

### Reusable Object Utilities

**Before implementing any object, check these utilities — do NOT reimplement.** The implement-object skills (S1/S2/S3K) have full details.

| Utility | Package | Purpose |
|---------|---------|---------|
| `SubpixelMotion` | `level.objects` | 16:8 fixed-point position updates (moveSprite, moveSprite2, moveX) |
| `PatrolMovementHelper` | `level.objects` | Left-right patrol with edge detection |
| `PlatformBobHelper` | `level.objects` | Sine-based standing-nudge for platforms |
| `SpringBounceHelper` | `level.objects` | Shared spring bounce physics |
| `DestructionEffects` | `level.objects` | Badnik explosion + animal + points |
| `AnimationTimer` | `util` | Cyclic frame animation timer |
| `LazyMappingHolder` | `util` | Lazy-loading sprite mapping holder |
| `PatternDecompressor` | `util` | Bytes→Pattern[] conversion |
| `FboHelper` | `util` | FBO creation/destruction + viewport |

**Base classes** (in `level.objects`): `AbstractBadnikInstance`, `AbstractProjectileInstance`, `AbstractSpikeObjectInstance`, `AbstractMonitorObjectInstance`, `AbstractPointsObjectInstance`, `AbstractFallingFragment`, `GravityDebrisChild`. Other reusable helpers in the same package include `SpringHelper`, `WaypointPathFollower`, `ObjectControlledSolidContactController`, `SlopedSolidProvider`, and `MultiPieceSolidProvider`.

**Inherited from `AbstractObjectInstance`**: `getRenderer(artKey)`, `buildSpawnAt(x, y)`, `isPlayerRiding()`, `isOnScreen(margin)`.

To add objects: add ID to `Sonic2ObjectIds`, create instance class, register factory in `Sonic2ObjectRegistry`.

### Game-Specific Art Loading

**Keep `ObjectArtData` game-agnostic.** Game-specific sprites (badniks, zone objects) go through `Sonic2ObjectArt` (loader methods) -> `Sonic2ObjectArtProvider` (registration/access) -> `Sonic2ObjectArtKeys` (string keys).

Pattern: add ROM address to `Sonic2Constants`, add key to `Sonic2ObjectArtKeys`, add loader method in `Sonic2ObjectArt`, register in `Sonic2ObjectArtProvider.loadArtForZone()`.

**S2 object art:** Prefer `S2SpriteDataLoader.loadMappingFrames(reader, mappingAddr)` to parse S2 mappings from ROM at runtime. Add mapping ROM address to `Sonic2Constants.java`, then call the shared utility from `Sonic2ObjectArt`. Object instance files should use `S2SpriteDataLoader` directly instead of inline parser copies. The `loadMappingFramesWithTileOffset()` variant supports VRAM tile index adjustment.

**S1 object art:** Use `Sonic1ObjectArt.buildArtSheet(artAddr, mappings, palette, bankSize)` for Nemesis-compressed art with mappings. Use `S1SpriteDataLoader.loadMappingFrames(reader, mappingAddr)` for ROM-parsed S1 mappings (5-byte pieces, byte piece count). Note: most S1 object mappings are inline `spritePiece` macros in the assembly (not separate binary tables), so `buildArtSheetFromRom()` is available but many objects still use hardcoded mappings with `buildArtSheet()`.

**S3K level-art objects:** Prefer `Sonic3kObjectArt.buildLevelArtSheetFromRom(mappingAddr, artTileBase, palette)` to parse S3K mappings from ROM at runtime. Add mapping ROM address to `Sonic3kConstants.java` (use RomOffsetFinder). Extract art_tile base and palette from the object code's `make_art_tile()` call. Only hardcode mapping pieces when the ROM table can't be used directly.

**Hard rule: ROM-only runtime assets.** If the engine needs object art, mappings, DPLCs, animation scripts, PLC data, or any other gameplay/runtime asset bytes, they must come from the user-supplied ROM through the engine's ROM-loading pipeline. Do **not** read runtime asset bytes from checked-in disassembly/reference files under `docs/` as a fallback. The disassembly tree is for research, labels, and offset discovery only. If a ROM-backed source is missing, find or verify the ROM address/path instead of loading from `docs/`.

### Constants Files (`game.sonic2.constants`)

`Sonic2Constants` (ROM offsets), `Sonic2ObjectIds` (object type IDs), `Sonic2ObjectConstants` (touch collision data), `Sonic2AnimationIds` (animation scripts), `Sonic2AudioConstants` (SFX IDs — music IDs live in `game.sonic2.audio.Sonic2Music`).

## Sonic 3&K Bring-up Notes

High-cost landmines (full S3K detail in [AGENTS_S3K.md](AGENTS_S3K.md) and the `s3k-*` skills):

- **Use S&K-side addresses only — never Sonic 3 standalone addresses:** The locked-on ROM has two halves: S&K (`< 0x200000`) and S3 (`>= 0x200000`). Shared assets exist in both halves with identical bytes, but the engine's S3KL runtime only references the S&K half. **Always put S&K-side (`sonic3k.asm`) offsets in `Sonic3kConstants.java` and never substitute an `s3.asm` address.** Run `RomOffsetFinder` with `--game s3k`. When a label returns both `sonic3k.asm` and `s3.asm` hits, pick `sonic3k.asm`; if only `s3.asm` hits come back, re-search with different label variants rather than falling back to the S3 address. See `s3k-disasm-guide` for details.
- **Dual object pointer tables (zone-set system):** S3K uses two object pointer tables that remap many IDs by zone. `S3kZoneSet`: `S3KL` (zones 0-6: AIZ-LBZ) and `SKL` (zones 7-13: MHZ-DDZ). Resolve names via `Sonic3kObjectRegistry.getPrimaryName(id, zoneSet)`; `ObjectDiscoveryTool` uses composite `"objectId:name"` keys so same-ID-different-name objects get separate entries.
- **Known limitation:** Some S3K levels log `maxChunkPatternIndex > patternCount` (dynamic art/PLC parity incomplete).

**Keep these S3K tests green:** `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`.

## Audio Engine

Emulates Mega Drive sound hardware: **YM2612** (FM synthesis, 6 channels), **PSG/SN76489** (square wave + noise, 4 channels), **SMPS Driver** (Sega's sound format).

The audio package splits across `audio` (backend), `audio.synth` (chip emulation — note `PsgChipGPGX` is active, `PsgChip` deprecated), `audio.smps` (sequencer/loader with `OVERFLOW`/`OVERFLOW2`/`TIMEOUT` tempo modes), `audio.driver`, `audio.runtime` (deterministic FIFO/PCM ring buffers used by gameplay rewind), `audio.rewind`, `audio.debug`. Per-game audio data lives under `game.sonicX.audio`.

Reference implementations live in `docs/SMPS-rips/SMPSPlay/` (SMPSPlay source) and the ripped audio data under `docs/SMPS-rips/`. Strive for hardware accuracy — reference SMPSPlay and the libvgm chip cores rather than simplified versions.

## Headless Testing

`HeadlessTestRunner` enables physics/collision integration tests without OpenGL.

```java
HeadlessTestRunner runner = new HeadlessTestRunner(sprite);
runner.stepFrame(up, down, left, right, jump);
runner.stepIdleFrames(5);
```

**Preferred test setup:** Use `@ExtendWith(SingletonResetExtension.class)` or `@FullReset` annotation for automated singleton teardown between tests. The extension calls `resetState()` on all singletons.

**Manual setup (legacy)** - see `TestHeadlessWallCollision.java` for a complete example. Key pitfalls:
- Reset singletons first using `resetState()` (NOT the deprecated `resetInstance()`)
- Call `GroundSensor.setLevelManager()` AFTER loading a level (static field)
- Call `Camera.updatePosition(true)` AFTER level load (bounds set during load)
- Failing to reset Camera can leave `frozen=true` from death sequences

**Test infrastructure classes:**
- `SingletonResetExtension` — JUnit 5 extension for automated singleton teardown
- `@FullReset` — Annotation triggering full engine reset
- `StubObjectServices` — Test double for `ObjectServices`
- `TestObjectServicesMigrationGuard` — Scanner-based guard preventing singleton regression in objects
- `TestNoServicesInObjectConstructors` — Ensures objects don't call `services()` during construction

## Trace Replay Tests

When working on trace replay test bugs, use the **`trace-replay-bug-fixing`** skill. It covers the comparison-only invariant (trace data is read-only diagnostic input — engine state must never be hydrated/synced from the trace in committed test code), the recorder/parser/comparator pipeline, the regeneration workflow, and cross-game parity rules.

## Coordinate System & Rendering

### Player Sprite Coordinates

**Critical:** The ROM uses **center coordinates** for player position. Always use `getCentreX()`/`getCentreY()` for object interactions, NOT `getX()`/`getY()` (which return top-left corner for rendering). Using top-left creates a ~19px vertical offset causing incorrect collision detection.

For playable-sprite native `x_pos` / `y_pos` writes, prefer `NativePositionOps`. Use raw preserve-subpixel centre setters only in lower-level sprite internals or non-playable/object-local cases.

**Debug overlay note:** The on-screen debug HUD `Pos:` field (rendered by `DebugRenderer`) intentionally shows `sprite.getX()` / `sprite.getY()` — the **top-left** corner, NOT the ROM-centre `x_pos`/`y_pos`. Do not treat the overlay's X/Y as ROM `x_pos`/`y_pos` when diagnosing parity issues or comparing against disassembly traces — add the sprite's half-width/half-height (or call `getCentreX()`/`getCentreY()` in code) to get the ROM-equivalent values. (Camera `Cam:` and sensor probe coordinates in the overlay are world-space and unaffected.)

### Y-Axis Convention
Engine uses Mega Drive convention: **Y increases downward** (Y=0 at top). `BatchedPatternRenderer` flips to OpenGL convention automatically.

### Sprite Tile Ordering
VDP sprites use **column-major** ordering: `tileIndex = column * heightTiles + row`. H-flip draws from last column first; V-flip from bottom row first.

### VDP Coordinate Offset (Disassembly Only)
VDP hardware adds 128 to X/Y. Convert: `screen_position = vdp_value - 128`. Our engine uses direct screen coordinates.

## Virtual Pattern ID System

The Mega Drive VDP uses 11-bit pattern indices (0x000–0x7FF, 2048 tiles). The engine extends this with a **virtual pattern ID** space so multiple subsystems can cache patterns without colliding. `PatternAtlas` uses a tiered lookup: flat array (`fastEntries[8192]`) for dense low IDs (level tiles), `HashMap<Integer, Entry>` for sparse high IDs.

Each pattern category claims a non-overlapping base: level tiles at `0x00000`, special stages per-game in `0x01000`–`0x10000`, objects at `0x20000`, HUD at `0x28000`, water surface at `0x30000`, sidekicks at `0x38000+`, title cards at `0x40000`/`0x50000`, results/credits/special UI in the higher ranges. Owning manager classes hold the authoritative `*_PATTERN_BASE` constants; choose a non-overlapping base when adding new categories.

**Key classes:**
- `PatternAtlas` — stores all patterns keyed by virtual ID; tiered flat+sparse lookup
- `DynamicPatternBank` — fixed-size bank for DPLC-driven updates (player sprites, objects)
- `PlayerSpriteRenderer` — renders player sprites using `renderPatternWithId()` to bypass the 11-bit VDP limit in `PatternDesc`
- `GraphicsManager.renderPatternWithId(patternId, desc, x, y)` — explicit pattern ID for atlas lookup, used when IDs exceed 0x7FF

See **[docs/KNOWN_DISCREPANCIES.md](docs/KNOWN_DISCREPANCIES.md)** for additional notes on the range table.

## Data Select & Save System

Full data select (save/load) screen with cross-game donation. `DataSelectProvider` (`com.openggf.game`) holds the lifecycle states; `DataSelectSessionController` is the presentation-independent state machine; each game implements `DataSelectHostProfile` (team configs, slot counts, zone labels, restart destinations). S3K renders with `S3kDataSelectManager`; S1/S2 fall back to `SimpleDataSelectManager` unless cross-game donation routes them through the S3K presentation. `SaveManager` (`game.save`) persists slots as JSON with SHA256 integrity and quarantines corrupt files. Title-screen `ONE_PLAYER` flows through `StartupRouteResolver` → `TitleActionRoute.DATA_SELECT` → controller → `DataSelectAction` → `Engine.launchGameplayFromDataSelect()`.

## Intentional Divergences

Documented in **[docs/KNOWN_DISCREPANCIES.md](docs/KNOWN_DISCREPANCIES.md)**: Gloop sound toggle, spindash release transpose fix, pattern ID ranges, HTZ cloud scroll fix, MCZ child cleanup, multi-sidekick system.

## Special Stage Implementation

S2 special stage code lives in `com.openggf.game.sonic2.specialstage` (`Sonic2SpecialStageManager` + track animator/decoder/loader/constants). The track frame format, segment types, orientation triggers, and progression rules are documented in those classes' Javadoc.

## Code Style

- Keep logic in manager classes, not in `Engine.java`
- Source files end with newline
- Java 21 features
- Branch naming: `feature/ai-*`, `bugfix/ai-*`
