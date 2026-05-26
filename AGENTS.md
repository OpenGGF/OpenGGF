# Guidance for future AI agents

## Project Mission
OpenGGF is an open-source, Java-based game engine for research and preservation of classic Mega Drive / Genesis platform games, specifically the mainline Sonic the Hedgehog series. It faithfully reimplements the physics and rendering behaviour of the original hardware using data loaded from user-supplied ROM images. No copyrighted assets are included in this repository. It aims to:
1.  Use the original ROM data to render levels.
2.  Perfectly and precisely replicate the original physics. (This is IMPORTANT. The engine must recreate the original pixel-for-pixel)
3.  Provide modern tooling such as a level editor and an open framework for modding and customisation.

## Current Status
The project is in **alpha**. All three games (Sonic 1, 2, 3&K) are supported with game-specific modules, level loading, objects, audio, and scroll handlers. Architectural moves: two-tier service architecture (`GameServices` + `ObjectServices`) replacing singletons, decomposed `LevelManager`, 50+ shared base classes and utility helpers, `MutableLevel` foundation for the planned level editor, full data select + save system (S3K ROM-accurate with cross-game donation for S1/S2).

Zone-specific behavior is increasingly hosted by `GameplayModeContext` and the runtime-owned shared frameworks: `ZoneRuntimeRegistry` (typed zone state), `PaletteOwnershipRegistry` (multi-writer palette composition), `AnimatedTileChannelGraph` (animated tiles), `ZoneLayoutMutationPipeline` (live layout edits), `ScrollEffectComposer` (deform/parallax), `SpecialRenderEffectRegistry` (staged extra draw passes), `AdvancedRenderModeController` (frame-level render-mode overrides). These are the preferred reuse path when uplifting S1/S2 content or bringing up new S3K zones.

A gameplay-scoped rewind framework exists for trace debugging (`RewindController`, `PlaybackController`, keyframe storage, `RewindRegistry`). Coverage spans core managers plus player, sidekick, object, ring, level, palette, parallax, mutation, render-mode, and PLC progress state. Prefer central eligibility (`GenericRewindEligibility`), codecs (`com.openggf.game.rewind.schema`), and policy-registry rules over per-object annotations or rewind overrides.

### Subsystem rules (load-bearing)

- **Physics rule:** Per-game behavioral differences must be gated by feature flags (usually on `PhysicsFeatureSet`), **never** by game-name `if/else` chains (e.g. `if (module.getGameId() == GameId.S1)`). When a new ROM-level divergence is discovered, add a flag to `PhysicsFeatureSet`, set the correct value on each game's `SONIC_1` / `SONIC_2` / `SONIC_3K` constant, and branch on the flag at the call site. Always verify against the disassembly.
- **Trace rule:** Trace fixes must not add zone/route/frame carve-outs. If a trace diverges in AIZ, CNZ, MGZ, or any other zone, model the ROM state that actually drives the branch: object id/routine, status/control bits, frame-counter visibility, physics profile, event flag, or data-driven object/profile condition. Do not branch on zone id/name, trace route, frame number, or a "known failing trace" exception. "Use ROM-default behaviour except in AIZ" is still a zone-specific carve-out and is not acceptable.
- **Virtual Pattern IDs:** The engine extends the VDP's 11-bit pattern index (0-2047) with a virtual ID space. `PatternAtlas` uses a tiered lookup; use `GraphicsManager.renderPatternWithId()` when IDs exceed `0x7FF`. Owning manager classes hold the `*_PATTERN_BASE` constants — choose non-overlapping bases when adding new categories. See `KNOWN_DISCREPANCIES.md` for the range table.
- **Audio reference:** strive for hardware accuracy. Reference SMPSPlay (`docs/SMPS-rips/SMPSPlay/`) and libvgm chip cores rather than simplified versions.

### Delivery priority
S3K playable vertical-slice parity. Close AIZ → HCZ route blockers first, then CNZ/MGZ/ICZ. Implement S3K objects by route impact (traversal blockers, terrain modifiers, hazards, bosses, then high-usage badniks). Uplift S1/S2 or older S3K code onto runtime-owned frameworks opportunistically when it removes active duplication; data select and special-stage polish are follow-up work.

## Agent Directives
1.  **Branching:** Always create pull requests from the same branch within a session. Use the following naming convention:
    *   `feature/ai-` for new features.
    *   `bugfix/ai-` for bug fixes.
2.  **Code Structure:** Keep logic within existing or new manager classes. Avoid putting all logic into `Engine.java` to maintain a strong object-oriented design.
3.  **Trace replay tests:** Use the **`trace-replay-bug-fixing`** skill when investigating or fixing failures in any `*TraceReplay` test. It covers the comparison-only invariant (trace data is read-only — engine state must never be hydrated/synced from the trace in committed test code), the recorder/parser/comparator pipeline, the regeneration workflow, and cross-game parity rules. Keep `docs/TRACE_FRONTIER_LOG.md` current when a trace frontier moves, a trace fix is committed, a previously passing trace regresses, or a full trace sweep is used to choose the next target.

## Branch Documentation Policy

Git hooks in `.githooks/` and CI enforce the branch policy below. Configure the repo once with `git config core.hooksPath .githooks` so local commits use the tracked hooks. The hook entrypoints dispatch through `.githooks/run-policy`: on Windows they call `validate-policy.ps1`, and on macOS/Linux they call `validate-policy.sh`.

- Every non-`master` branch commit must include these trailers (each starting with `updated` or `n/a`): `Changelog`, `Guide`, `Known-Discrepancies`, `S3K-Known-Discrepancies`, `Agent-Docs`, `Configuration-Docs`, `Skills`.
- `prepare-commit-msg` auto-appends the trailer block on non-merge commits. Do not delete it; fill it in.
- Each trailer maps to a file/dir (e.g. `Changelog` → `CHANGELOG.md`, `Agent-Docs` → both `AGENTS.md` and `CLAUDE.md`, `Skills` → both `.agents/skills/` and `.claude/skills/`). If the mapped files are staged, the trailer must not say `n/a`. See `.githooks/run-policy` for the authoritative mapping.
- When merging a non-`master` branch into `develop`, stage a `README.md` update summarizing the branch change in the release/change log section. Hooks and CI block the merge otherwise.
- Treat the trailers as explicit attestation for the "where relevant" judgment. Do not use `--no-verify` to bypass the policy.

## Key information
*   **Entry point:** `com.openggf.Engine` (declared in the manifest). A `main` method creates a GLFW window with a manual timing game loop.
*   **Build:** `mvn package`. Tests can be run with `mvn test` (JUnit 5 / Jupiter only).
*   **Maven output for agents:** `.mvn/extensions.xml` installs Maven Silent Extension (MSE) and `.mvn/maven.config` enables `-Dmse=relaxed` by default for all repo-local Maven commands. Use `-Dmse=off` when full Maven logs are required for debugging.
*   **PowerShell Maven args:** Quote Maven `-D...` properties in PowerShell so the shell does not reinterpret dots or punctuation. Prefer `mvn "-Dtest=TestClassName" test` or `mvn "-Dtest=com.openggf.package.TestClassName" test` for focused runs.
*   **Run:** `java -jar target/OpenGGF-0.6.prerelease-jar-with-dependencies.jar`.
*   **ROM Requirement:** The engine now supports Sonic 1, Sonic 2, and Sonic 3&K modules. Keep the relevant ROM in the project root (typically gitignored): `Sonic The Hedgehog 2 (W) (REV01) [!].gen`, `Sonic The Hedgehog (W) (REV01) [!].gen`, and `Sonic and Knuckles & Sonic 3 (W) [!].gen`. S3K-focused tests should pass `-Ds3k.rom.path="Sonic and Knuckles & Sonic 3 (W) [!].gen"` when needed.
*   **Important packages** under `src/main/java/com/openggf` (package names are mostly self-describing; non-obvious facts only):
    *   `game.zone` / `palette` / `animation` / `mutation` / `render` - runtime-owned shared framework layers
    *   `game.profiles.*` – canonical cross-game object behavior profiles. New solid, touch-response, and object-lifecycle vocabulary should live here and be adapted by `level.objects` execution code instead of creating game-local profile types.
    *   `game.dataselect` – shared data select framework. The `DataSelectProvider` interface itself lives in `com.openggf.game`.
    *   `game.rewind` – gameplay-scoped rewind framework: keyframes, deterministic seek/replay, generic field capture, rewind field annotations, identity ids, policy registry, compact schema capture.
    *   `level.objects` – unified `ObjectManager` (placement, collision, touch response), `ObjectServices` interface, shared base classes (`AbstractBadnikInstance`, `AbstractSpikeObjectInstance`, etc.), utility helpers (`SubpixelMotion`, `PatrolMovementHelper`, `PlatformBobHelper`, `DestructionEffects`, ...).
    *   `level.scroll.compose` – shared deform/parallax composition helpers built around `ScrollEffectComposer`.
    *   `physics` – sensors, terrain collision, unified `CollisionSystem`.
    *   `configuration` – `SonicConfiguration` / `SonicConfigurationService`. Dev-only: `TEST_MODE_ENABLED` (replaces master-title game-select with a trace picker, needs `TRACE_CATALOG_DIR`), `TRACE_CATALOG_DIR` (default `src/test/resources/traces`).
    *   `LevelFrameStep` lives at the `com.openggf` package root, not under `level`.
*   **Tests:** Live under `src/test/java/com/openggf/tests` and cover ROM loading, decompression, collision, singleton lifecycle, and services migration. New or updated tests must use JUnit 5 / Jupiter only; do not add JUnit 4 tests, rules, runners, or `org.junit.*` imports.

## Coordinate Semantics

This is a frequent source of bugs and parity regressions.

- In this engine, ROM `x_pos` maps to `getCentreX()` / `setCentreX(...)`.
- In this engine, ROM `y_pos` maps to `getCentreY()` / `setCentreY(...)`.
- `getX()` / `getY()` are top-left sprite bounds, not ROM object position fields.
- When porting disassembly that reads or writes `x_pos` / `y_pos`, default to centre-coordinate APIs unless the code is explicitly working with sprite bounds, render extents, or collision box edges.
- For playable sprites, route native `x_pos` / `y_pos` writes through `NativePositionOps`; use raw preserve-subpixel setters only in lower-level sprite internals or non-playable/object-local cases.
- If camera, collision, object anchoring, or scripted movement starts drifting relative to the player, check for accidental mixing of `getX()` / `getY()` with ROM `x_pos` / `y_pos` semantics first.
- **Debug overlay caveat:** The in-engine debug HUD `Pos:` line (from `DebugRenderer`) prints `sprite.getX()` / `sprite.getY()` — the **top-left corner**, not the centre. It is NOT the ROM `x_pos` / `y_pos`. Do not quote those numbers directly against disassembly traces; convert to centre coordinates first (or read `getCentreX()` / `getCentreY()` in code).

## Headless Testing with HeadlessTestRunner

The `HeadlessTestRunner` utility (`com.openggf.tests.HeadlessTestRunner`) enables physics and collision integration tests without an OpenGL context.

### Usage
```java
HeadlessTestRunner runner = new HeadlessTestRunner(sprite);
runner.stepFrame(up, down, left, right, jump);  // Simulate one frame
runner.stepIdleFrames(5);                        // Step multiple idle frames
```

### Preferred: Automated Singleton Reset
Use `@ExtendWith(SingletonResetExtension.class)` or the `@FullReset` annotation for automated singleton teardown between tests. These call `resetState()` on all singletons.

```java
@ExtendWith(SingletonResetExtension.class)
class MyTest {
    @Test void testSomething() { /* singletons auto-reset */ }
}
```

### Manual Setup (Legacy)
1. **Reset test state:** `TestEnvironment.resetAll()`
2. **Initialize headless graphics:** `GameServices.graphics().initHeadless()`
3. **Create and register playable sprite first:** add the main sprite to `GameServices.sprites()` and set camera focus before `loadZoneAndAct(...)` (required by current `LevelManager` load path)
4. **Load level:** `GameServices.level().loadZoneAndAct(zone, act)`
5. **Fix GroundSensor:** `GroundSensor.setLevelManager(GameServices.level())` (static field becomes stale between tests)
6. **Update camera:** `GameServices.camera().updatePosition(true)` AFTER level load (bounds set during load)

See `TestHeadlessWallCollision.java` for a complete example.

### Test Infrastructure
| Class | Purpose |
|-------|---------|
| `SingletonResetExtension` | JUnit 5 extension for automated singleton teardown |
| `@FullReset` | Annotation triggering full engine reset |
| `StubObjectServices` | Test double for `ObjectServices` |
| `TestObjectServicesMigrationGuard` | Scanner-based guard preventing singleton access in migrated objects |
| `TestNoServicesInObjectConstructors` | Ensures objects don't call `services()` during construction |

## Two-Tier Service Architecture

### Tier 1: `GameServices` (Static Facade)

Non-object code (managers, event handlers, controllers) accesses core managers through `GameServices` instead of direct `getInstance()` calls. Exposes gameplay-scoped accessors (`camera()`, `level()`, `gameState()`, `timers()`, `sprites()`, `fade()`, `collision()`, `parallax()`, `water()`, `debugOverlay()`, ...) requiring an active `GameplayModeContext`, plus engine globals (`rom()`, `audio()`, `graphics()`, `configuration()`, `module()`, ...) resolved via `EngineServices`, plus `*OrNull()` variants. See `GameServices.java` for the full surface.

### Tier 2: `ObjectServices` (Per-Object Injection)

All `AbstractObjectInstance` subclasses receive `ObjectServices` via injection at construction time (ThreadLocal context set by `ObjectManager`). **Never call `getInstance()` from object code** — use `services()` instead. Reaches `objectManager`, `renderManager`, `audioManager`, `camera`, `gameState`, `zoneFeatureProvider`, plus audio shortcuts, level-transition requests, world session, RNG, ROM, config. See `ObjectServices.java`.

### Session Ownership (post runtime-ownership migration)

Gameplay state is split by lifetime across three layers in `com.openggf.game.session`:

- **`WorldSession`** — durable, survives editor mode swaps. Owns the active `GameModule`, loaded `Level` (incl. `MutableLevel`), and zone/act metadata.
- **`GameplayModeContext`** — disposable, rebuilt per gameplay session. Owns all gameplay-scoped managers (camera, timer, game state, fade, RNG, water, parallax, collision, sprite, level, ...) and the runtime-shared registries. Provides `initializeFreshGameplayState()` for editor-exit counter reset.
- **`SessionManager`** — manages lifecycle (`openGameplaySession`, `enterEditorMode`, `resumeGameplayFromEditor`).

Editor entry/exit tears down and rebuilds the mode context while `WorldSession` survives; `MutableLevel` mutations survive via `LevelManager.restoreInheritedLevel()`. The old `GameRuntime` / `RuntimeManager` façade is retired — prefer explicit dependencies from `GameplayModeContext`, `GameServices`, or `ObjectServices`. See `docs/superpowers/specs/2026-04-07-runtime-ownership-migration-design.md` for the full design.

### Runtime-Shared Framework Stack

`GameplayModeContext` hosts shared registries/controllers used to normalize zone-specific logic across games: `ZoneRuntimeRegistry` (typed zone state), `PaletteOwnershipRegistry` (palette-write arbitration + underwater mirroring), `AnimatedTileChannelGraph` (animated tile channels), `ZoneLayoutMutationPipeline` (live layout edits + redraw sequencing), `SpecialRenderEffectRegistry` (staged extra render passes), `AdvancedRenderModeController` (per-line/per-cell scroll overrides). Scroll/deform reuse lives in `level.scroll.compose` around `ScrollEffectComposer` / `DeformationPlan` / `WaterlineBlendComposer`. Prefer plugging into these rather than adding new zone-local registries.

## Consolidated Subsystems

Manager classes consolidated for reduced complexity:

- **`LevelManager`** decomposed into `LevelTilemapManager` (chunks/blocks/VRAM), `LevelTransitionCoordinator` (act transitions, warps, seamless), `LevelDebugRenderer`, plus `LevelGeometry` / `LevelDebugContext` records. `MutableLevel` provides snapshot/mutation/dirty-region tracking processed per-frame via `LevelFrameStep.processDirtyRegions()`.
- **`ObjectManager`** holds inner classes `Placement` (spawn windowing), `SolidContacts` (riding/landing/ceiling/side), `TouchResponses` (enemy bounce/hurt), `PlaneSwitchers`. Injects `ObjectServices` into all objects at construction.
- **`RingManager`** holds `RingPlacement` (collection/sparkle/spawn), `RingRenderer` (cached patterns), `LostRingPool` (lost ring physics).
- **`PlayableSpriteController`** (owned by `AbstractPlayableSprite`) coordinates `PlayableSpriteMovement`, `PlayableSpriteAnimation`, `SpindashDustController`, `DrowningController`.
- **`Sonic2LevelAnimationManager`** implements both `AnimatedPatternManager` and `AnimatedPaletteManager` via `Sonic2PatternAnimator` (uses `AniPlcParser`/`AniPlcScriptState`) and `Sonic2PaletteCycler`.
- **`CNZBumperManager`** unifies placement windowing and ROM-accurate bounce physics.
- **`CollisionSystem`** orchestrates collision in phases: terrain probes (`TerrainCollisionManager`) → solid object resolution (`ObjectManager.SolidContacts`) → post-resolution (ground mode, headroom). Supports trace recording via `CollisionTrace` (`RecordingCollisionTrace` / `NoOpCollisionTrace`).
- **`UiRenderPipeline`** (`graphics.pipeline`) enforces render order: Scene → HUD overlay → Fade pass. `Engine.display()` drives screen transitions through it. `RenderOrderRecorder` is available for tests.

## Multi-Sidekick System

The engine extends the ROM's single CPU-controlled sidekick (Tails at `$FFFFB040`) to support an arbitrary number of sidekick characters configured via comma-separated `SIDEKICK_CHARACTER_CODE` (e.g. `"tails,knuckles,sonic,sonic"`). This is a novelty feature — not present in any official Sonic game.

### Key Classes

| Class | Purpose |
|-------|---------|
| `SidekickCpuController` | Per-sidekick AI state machine (INIT, SPAWNING, APPROACHING, NORMAL, PANIC). Holds a `leader` reference for daisy-chain following and `getEffectiveLeader()` for chain healing. |
| `SidekickRespawnStrategy` | Interface for per-character respawn behavior during APPROACHING state. |
| `TailsRespawnStrategy` | Flies in from above (ROM-accurate). Default strategy. |
| `KnucklesRespawnStrategy` | Glides in from screen edge, drops when X-aligned or after 3s timeout. |
| `SonicRespawnStrategy` | Walks/spindashes in from nearest floor at screen edge. Requires physics (`requiresPhysics() = true`). |
| `SpriteManager.getSidekicks()` | Returns ordered list of all CPU-controlled sidekicks. |

### Daisy Chain

Each sidekick follows the one in front via a 17-frame position/input history delay. When a middle sidekick despawns, `getEffectiveLeader()` walks up the chain to the nearest settled leader (or main player). `isSettled()` returns true after 15 consecutive frames in NORMAL state.

### VRAM Banks

Duplicate characters (e.g. multiple Sonics) need separate DPLC pattern banks to avoid atlas corruption. Banks are allocated at `SIDEKICK_PATTERN_BASE` (`0x38000+`) with a global running offset. Tail appendages (Obj05) for duplicate Tails use `0x39000+`. `PlayerSpriteRenderer` calls `renderPatternWithId()` to bypass the VDP's 11-bit limit. See `KNOWN_DISCREPANCIES.md` for range table and capacity limits.

### Important Implementation Details

- **`reset()` preserves `leader`** — the leader field is a structural chain relationship, not per-level state. Nulling it in `reset()` permanently breaks the sidekick.
- **`requiresPhysics()`** — strategies that rely on ground speed (Sonic) must return `true` so `SpriteManager` doesn't skip the physics pipeline during APPROACHING.
- **P2 input** — only sidekick[0] receives Player 2 controller input.
- **Respawn** uses `getEffectiveLeader()` for both condition checks and approach targeting, enabling parallel respawn when all sidekicks despawn simultaneously.

## Multi-Game Support Architecture

The engine supports multiple Sonic games (Sonic 1, Sonic 2, Sonic 3&K) through a provider-based abstraction layer.

### Core Components
| Class/Interface | Purpose |
|-----------------|---------|
| `GameModule` | Central interface defining all game-specific providers |
| `GameModuleRegistry` | Singleton holding the current game module |
| `RomDetectionService` | Auto-detects ROM type and sets appropriate module |
| `RomDetector` | Interface for game-specific ROM detection logic |

### Key Providers
| Provider | Purpose |
|----------|---------|
| `ZoneRegistry` | Zone/level metadata (names, act counts, start positions) |
| `ObjectRegistry` | Object creation factories and ID mappings |
| `SpecialStageProvider` | Chaos Emerald special stage logic |
| `BonusStageProvider` | Checkpoint bonus stage logic (S3K) |
| `ScrollHandlerProvider` | Per-zone parallax scroll handlers |
| `ZoneFeatureProvider` | Zone-specific mechanics (CNZ bumpers, water) |
| `RomOffsetProvider` | Type-safe ROM address access |

### Usage
```java
// Access current game module
GameModule module = GameModuleRegistry.getCurrent();
ObjectRegistry objects = module.createObjectRegistry();
ZoneRegistry zones = module.getZoneRegistry();

// Auto-detect ROM and set module
GameModuleRegistry.detectAndSetModule(rom);
```

### Sonic 3&K Bring-up Notes (Critical)

Full S3K detail in [AGENTS_S3K.md](AGENTS_S3K.md) and the `s3k-*` skills. High-cost landmines:

- **S&K-side addresses only — never Sonic 3 standalone:** The locked-on ROM has S&K (`< 0x200000`) and S3 (`>= 0x200000`) halves with identical shared bytes. The engine's S3KL runtime only references the S&K half. Always put `sonic3k.asm` offsets in `Sonic3kConstants.java`; never substitute an `s3.asm` address. Run `RomOffsetFinder` with `--game s3k`. See `s3k-disasm-guide`.
- **Dual object pointer tables (zone-set system):** S3K uses two pointer tables that remap many IDs by zone. `S3kZoneSet`: `S3KL` (zones 0-6: AIZ-LBZ, 256 entries) and `SKL` (zones 7-13: MHZ-DDZ, 185 entries). Resolve names via `Sonic3kObjectRegistry.getPrimaryName(id, zoneSet)`.
- **Known limitation:** Some S3K acts log `maxChunkPatternIndex > patternCount` (dynamic art/PLC parity incomplete).

**Keep these S3K tests green:** `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`.

## Object & Badnik System

Game objects use a factory pattern with game-specific registries. All objects receive `ObjectServices` at construction via `ObjectManager` injection.

### Key Classes
| Class | Purpose |
|-------|---------|
| `ObjectManager` | Unified manager with Placement, SolidContacts, TouchResponses, PlaneSwitchers; injects `ObjectServices` into all objects |
| `ObjectServices` | Per-object service interface (camera, audio, level, game state) |
| `DefaultObjectServices` | Concrete `ObjectServices` implementation backed by `GameplayModeContext` and `EngineContext` |
| `AbstractObjectRegistry` | Shared base for `Sonic1ObjectRegistry`, `Sonic2ObjectRegistry`, `Sonic3kObjectRegistry` |
| `AbstractBadnikInstance` | Base class for enemy AI (`com.openggf.level.objects` — game-agnostic) |
| `ObjectFactory` | Functional interface for object creation |

### Service Access in Objects
```java
// CORRECT — use injected services:
services().audioManager().playSfx(sfxId);
services().camera().getX();
PatternSpriteRenderer renderer = getRenderer(artKey);  // static method on AbstractObjectInstance

// WRONG — do NOT use singletons in objects:
AudioManager.getInstance().playSfx(sfxId);  // PROHIBITED
```

### Object Behavior Contracts

New object/boss/badnik/trace work should use the shared object-control and profile vocabulary rather than one-off flags: `ObjectControlState` (control bits + derived predicates), `ObjectPlayerQuery`/`ObjectPlayerParticipationPolicy` (which players a routine targets), `NativePositionOps` (playable-sprite `x_pos`/`y_pos` writes), `ObjectLifetimeOps` (destruction/offscreen/respawn/slot transfer). Canonical behavior profiles live under `com.openggf.game.profiles.*`; `level.objects` hosts execution + compatibility adapters. Raw setters and direct `setDestroyed(true)` are legacy compatibility — do not grow guard baselines for new implementations without documenting the exact reason.

### Child Object Spawning
Use `spawnChild(() -> new ChildObject(spawn, params))` so slot ownership, parent/child lifecycle, and remembered-spawn stay on the shared lifetime path. Direct `ObjectManager.addDynamicObject(...)` is reserved for manager/framework bridge code with focused tests.

### Adding New Objects
1. Add object ID to `Sonic2ObjectIds.java`
2. Create instance class extending `AbstractObjectInstance` (or `AbstractBadnikInstance` for enemies)
3. Register factory in `Sonic2ObjectRegistry.registerDefaultFactories()`
4. For solid objects, collision is handled automatically via `ObjectManager.SolidContacts`
5. For enemies, touch response is handled via `ObjectManager.TouchResponses`

### Shared Base Classes (in `level.objects`)
| Base Class | Purpose |
|------------|---------|
| `AbstractBadnikInstance` | All badniks — touch response, destruction via `DestructionEffects` |
| `AbstractSpikeObjectInstance` | Spike objects with retract/extend behavior |
| `AbstractMonitorObjectInstance` | Monitor objects — shared icon-rise physics |
| `AbstractPointsObjectInstance` | Floating score popups |
| `AbstractProjectileInstance` | Fire-and-forget projectiles |
| `AbstractFallingFragment` | Collapsing platform fragment physics |
| `GravityDebrisChild` | Debris children with gravity |

### Shared Utilities (in `level.objects`)
| Utility | Purpose |
|---------|---------|
| `SubpixelMotion` | 16:8 fixed-point position updates (moveSprite, moveSprite2, moveX) |
| `PatrolMovementHelper` | Left-right patrol with edge detection |
| `PlatformBobHelper` | Sine-based platform bobbing |
| `SpringBounceHelper` | Shared spring bounce physics |
| `DestructionEffects` | Badnik explosion + animal + score |
| `WaypointPathFollower` | Conveyor/path-following objects |

### Game-Specific Art Loading

Keep `ObjectArtData` game-agnostic. Game-specific art (badniks, zone objects) goes through a provider pattern: add ROM address to `SonicNConstants.java`, add art key to `SonicNObjectArtKeys.java`, add loader method to `SonicNObjectArt.java`, register in `SonicNObjectArtProvider.loadArtForZone()`.

Prefer ROM-parsed mappings via the per-game data loaders:
- **S1:** `Sonic1ObjectArt.buildArtSheet(...)` + `S1SpriteDataLoader.loadMappingFrames(...)`. Most S1 mappings are inline assembly macros, so many objects still use hardcoded mappings.
- **S2:** `S2SpriteDataLoader.loadMappingFrames(reader, mappingAddr)` — use directly instead of inline parser copies.
- **S3K:** `Sonic3kObjectArt.buildLevelArtSheetFromRom(mappingAddr, artTileBase, palette)`. Extract `art_tile` base + palette from the object code's `make_art_tile()` call.

**Hard rule: ROM-only runtime assets.** Object art, mappings, DPLCs, animation scripts, PLC data, and any other gameplay/runtime asset bytes must come from the user-supplied ROM through the engine's ROM-loading pipeline. Do **not** read runtime asset bytes from `docs/` disassembly/reference files as a fallback — that tree is for research, labels, and offset discovery only. If a ROM-backed source is missing, find or verify the ROM address instead.

**PLC system:** `PlcParser` in `level.resources` provides game-agnostic PLC parsing. See `plc-system` skill (cross-game) and `s3k-plc-system` (S3K specifics).

### Constants Files
| File | Contents |
|------|----------|
| `Sonic2Constants.java` | Sonic 2 primary ROM offsets |
| `Sonic2ObjectIds.java` | Sonic 2 object type IDs (0x41=Spring, 0x26=Monitor) |
| `Sonic2ObjectConstants.java` | Sonic 2 touch collision data |
| `Sonic2AudioConstants.java` | Sonic 2 SFX IDs (music IDs are in `game.sonic2.audio.Sonic2Music`) |
| `Sonic1Constants.java` | Sonic 1 ROM offsets (zone IDs, level data, collision, palettes, art) |

## Adding New Game Support

To add support for a new game:
1. Create `GameModule` implementation (e.g., `Sonic3KGameModule`)
2. Create `RomDetector` to identify the ROM
3. Implement required providers (`ZoneRegistry`, `ObjectRegistry`, audio profile)
4. Register detector in `RomDetectionService.registerBuiltInDetectors()`
5. Add a `GameProfile` factory method in `RomOffsetFinder.GameProfile` for the ROM Offset Finder tool

All three games are fully supported: `Sonic1GameModule`, `Sonic2GameModule`, and `Sonic3kGameModule` are merged and functional on `master`. Each module provides its own `ZoneRegistry`, `ObjectRegistry`, `ScrollHandlerProvider`, audio profile, and related providers. The ROM Offset Finder tool supports S1, S2, and S3K via `GameProfile` factory methods (`sonic1()`, `sonic2()`, `sonic3k()`).

## ROM Offset Finder Tool

If a disassembly tree is present under `docs/s1disasm/`, `docs/s2disasm/`, or `docs/skdisasm/`, the **RomOffsetFinder** tool (`com.openggf.tools.disasm.RomOffsetFinder`) searches disassembly items, calculates ROM offsets, verifies them against ROM data, and exports verified results as Java constants. Pass `--game s1|s2|s3k` (auto-detects from disasm path otherwise) and run via `mvn exec:java`:

```bash
mvn exec:java "-Dexec.mainClass=com.openggf.tools.disasm.RomOffsetFinder" "-Dexec.args=<command>" -q
```

Commands: `search <pattern>`, `verify <label>`, `verify-batch [type]`, `list [type]`, `test <offset> <type>`, `export <type> [prefix]`, `search-rom <hex> [start] [end]`, `plc <name>`. Verification status codes: `[OK]` match, `[!!]` mismatch, `[??]` not found, `[ER]` error.

**Per-game quirks:**
- **S1** uses `bincludePalette` directives and `sonic.asm`; most object mappings are inline `spritePiece` macros.
- **S2** is the default profile, uses `s2.asm` and the `palette` macro (expanded to `art/palettes/`).
- **S3K** uses `sonic3k.asm` and `binclude` for palettes; compression type is encoded in label suffixes (e.g. `_KosM`, `_Kos`, `_Nem`) since files use `.bin` extension. The tool auto-infers.

**PLC cross-referencing:** search results for art labels list which PLCs reference that art. Use `plc <name>` to dump a PLC definition's art entries.

**Permanent anchor offsets** live in `GameProfile.sonic1()` / `sonic2()` / `sonic3k()` factory methods in `RomOffsetFinder.java`; verified offsets are added as runtime anchors during a session. Programmatic API is available — see `com.openggf.tools.disasm` (`RomOffsetFinder`, `DisassemblySearchTool`, `ConstantsExporter`). The `s1disasm-guide` / `s2disasm-guide` / `s3k-disasm-guide` skills cover full per-game usage.

## Audio Engine hints
*   **Useful locations:**
    *   `docs` – Contains lots of information about the audio engine in saved htm files.
	*   `docs/YM2612.java.example` – Contains a port of the Gens emulator's YM2612 implementation. Missing PCM functionality. May not be correct!
	*   `docs/SMPS-rips` – Contains ripped audio for various games, including `Sonic the Hedgehog 2`. Contains configurations for SMPSPlay.
	*   `docs/SMPS-rips/SMPSPlay` – This contains the source for SMPSPlay, which is an open-source implementation of playback of rips for game sfx/music, for games that use the SMPS driver for the Sega Genesis.
	*   The libvgm chip cores (`emu/cores/ym2612.c`, `emu/cores/sn76489*.c`) are an excellent reference — fetch a copy of `libvgm` separately if needed. They are high-accuracy implementations that we cross-reference for YM2612 / SN76489 behavior.
*   **Important guidelines:** We strive for accuracy in the audio engine. Wherever possible, we should be implementing features identically to hardware. We should reference the existing libvgm cores, the SMPSPlay source, and the documentation to achieve this. We should not "twiddle knobs" or implement simplified versions of logic, instead preferring to diagnose issues and compare to reference/sources of truth.
## Useful tips

*   **Player Coordinates:** The original ROM uses **center coordinates** for player position. When implementing object interactions:
    *   `player.getX()` / `player.getY()` → Top-left corner (for rendering)
    *   `player.getCentreX()` / `player.getCentreY()` → Center position (for collision/interactions)
    *   **Always use center coordinates** for object collision checks to match ROM behavior. Using top-left creates ~19 pixel vertical offset errors.
    *   **Debug HUD:** The overlay's `Pos:` field shows the top-left (`getX()` / `getY()`), NOT the ROM-centre position. Treat it as render-space only when cross-referencing the disassembly.
*   **Terminology**: The codebase uses specific terms for level components that differ from standard Sonic 2 naming:
    *   **Pattern:** An 8x8 pixel tile.
    *   **Chunk:** A 16x16 pixel tile, composed of Patterns.
    *   **Block:** A 128x128 pixel area, composed of Chunks.
*   **Dependencies:** Running the engine requires LWJGL (OpenGL, OpenAL, GLFW bindings) and JOML (math library), already declared as dependencies in `pom.xml`.
*   **Debug:** `DEBUG_VIEW_ENABLED` (true by default) overlays sensor and collision info during gameplay.
*   **Level Loading:** Performed by `LevelManager`, which reads from the ROM through classes in `com.openggf.data`.
*   **Conditional Tests**: `TestCollisionLogic` uses `Assume.assumeTrue` to skip when a ROM file is not present. This is a known and accepted conditional skip, not a hard `@Ignore`.
*   **File Endings**: Ensure all source code files end with a newline character.
