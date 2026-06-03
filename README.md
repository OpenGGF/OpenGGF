# OpenGGF - The Open-Source Java-Based Speedy Erinaceidae Engine

> This project is a work in progress. For the current state, please see the latest version in the
> Releases section of this document.

## Introduction

OpenGGF is an open-source, Java-based game engine for research and preservation of classic Mega
Drive / Genesis platform games, specifically the mainline Sonic the Hedgehog series. It aims to
faithfully reimplement the physics and rendering behaviour of the original hardware using data
loaded from user-supplied ROM images. The project's primary goal
is accuracy: physics, collision, and audio are all verified against community-maintained
disassemblies of titles in the Sonic the Hedgehog series. No copyrighted assets are included in
this repository; a legally obtained ROM is required to run the engine.

The engine also aims to provide modern tooling such as a level editor and an open framework for
modding and customisation.

> **Disclaimer:** This project is not affiliated with or endorsed by Sega. Sonic the Hedgehog and
> all related characters, names, and trademarks are the property of Sega Corporation. No ROM images
> or other copyrighted game data are included in this repository. Users must supply their own
> legally obtained ROM files to use this software.
>
> The disclaimer is also shown in-engine on startup; it can be disabled by setting
> `SHOW_LEGAL_DISCLAIMER_ON_STARTUP=false` in `config.json`.

## User Guide

A comprehensive user guide is available in [`docs/guide/`](docs/guide/index.md), covering:

- **Players:** [Getting started](docs/guide/playing/getting-started.md), [controls](docs/guide/playing/controls.md), [configuration](docs/guide/playing/configuration.md), [game status](docs/guide/playing/game-status.md), and [troubleshooting](docs/guide/playing/troubleshooting.md).
- **Contributors:** [Dev setup](docs/guide/contributing/dev-setup.md), [architecture overview](docs/guide/contributing/architecture.md), [adding zones](docs/guide/contributing/adding-zones.md), [adding bosses](docs/guide/contributing/adding-bosses.md), [audio system](docs/guide/contributing/audio-system.md), [testing](docs/guide/contributing/testing.md), and [trace replay testing](docs/guide/contributing/trace-replay.md).
- **Cross-referencers:** [68000 primer](docs/guide/cross-referencing/68000-primer.md), [mapping exercises](docs/guide/cross-referencing/mapping-exercises.md), [per-game notes](docs/guide/cross-referencing/per-game-notes.md), and [tooling](docs/guide/cross-referencing/tooling.md).

Contributor tests are JUnit 5 / Jupiter only. Do not add JUnit 4 tests, rules, runners, or `org.junit.*` imports.

## Configuration

The engine reads runtime settings from `config.json`. Key bindings can be written either as GLFW
integer codes or as human-readable names such as `"SPACE"`, `"Q"`, or `"F9"`. See
[`CONFIGURATION.md`](CONFIGURATION.md) and the player guide for the full reference.

## Controls

> Currently, only keyboard controls are supported.

### Player Controls

| Key | Action |
|-----|--------|
| Arrow Keys | Movement |
| Space | Jump |
| Z | Cycle Acts |
| X | Cycle Zones |

### Debug Controls

| Key | Action |
|-----|--------|
| F1 | Show/Hide Debug Overlay (text and bounding boxes) |
| F2 | Show/Hide Shortcuts Overlay |
| F3 | Show/Hide Player Panel |
| F4 | Show/Hide Sensor Labels |
| F5 | Show/Hide Object Labels |
| F6 | Show/Hide Camera Bounds |
| F7 | Show/Hide Player Bounds |
| F9 | Show/Hide Ring Bounds |
| F10 | Show/Hide Plane Switchers |
| F11 | Show/Hide Touch Response |
| F12 | Show/Hide Art Viewer |

### Editor Controls

| Key | Action |
|-----|--------|
| Shift+Tab | Toggle between gameplay and the experimental editor overlay (`EDITOR_ENABLED` must be `true`) |
| F5 | Restart the playtest from editor mode |

## FAQ

### What does "GGF" stand for?

Gotta Go Fast!

### Is this an emulator?

No. OpenGGF is an independent reimplementation of the game logic and physics, written in Java
from scratch. It does not emulate the Mega Drive CPU or VDP. Instead, it reads data (level
layouts, art, music) from original ROM images and runs its own implementation of the game rules.
The implementation is developed and verified against the community-maintained disassemblies
([s1disasm], [s2disasm], [skdisasm]) to achieve pixel-accurate behaviour. The audio engine is a
partial exception: it features software emulation of the YM2612 FM synthesiser and SN76489 PSG
chips (based on [libvgm] and [Genesis Plus GX] reference cores) driven by a reimplemented SMPS
sound driver.

[libvgm]: https://github.com/ValleyBell/libvgm
[Genesis Plus GX]: https://github.com/ekeeke/Genesis-Plus-GX

[s1disasm]: https://github.com/sonicretro/s1disasm
[s2disasm]: https://github.com/sonicretro/s2disasm
[skdisasm]: https://github.com/sonicretro/skdisasm

### Which games are supported?

| Game | Status |
|------|--------|
| Sonic the Hedgehog (S1) | Broadly playable. All 7 zones, 6 bosses, special stages, title screen, ending/credits. When S3K is the donor, S1 can also use the donated S3K data select screen with runtime-generated zone previews and cross-game team launch support. |
| Sonic the Hedgehog 2 (S2) | Most complete. All zones, 9 bosses (including both DEZ bosses), special stages, Tails AI, credits/ending. When S3K is the donor, S2 can also use the donated S3K data select screen with runtime-generated zone previews and cross-game team launch support. |
| Sonic 3 & Knuckles (S3K) | Progressing, and now the main delivery focus. Angel Island Zone is substantially playable, Hydrocity now has early HCZ2 chase coverage, and S3K includes title screen, level select, data select with save/load support, Knuckles glide/climb, Blue Ball special stages (WIP), bonus-stage parity work, palette cycling, and expanding object/badnik coverage. Data select can also be donated to S1/S2 via cross-game donation. |

Recent engine work has also moved shared zone behavior onto runtime-owned frameworks: `ZoneRuntimeRegistry`, `PaletteOwnershipRegistry`, `AnimatedTileChannelGraph`, `ZoneLayoutMutationPipeline`, `ScrollEffectComposer`, `SpecialRenderEffectRegistry`, and `AdvancedRenderModeController`. The current roadmap priority is to use those systems to close playable S3K vertical slices rather than to run broad architecture migrations for their own sake. S1/S2 uplift remains valuable when it removes duplication or active risk in code already being touched, but S3K route completeness now leads work selection.

Current migration status is intentionally partial rather than universal. Sonic 2 already uses the runtime-owned stack for HTZ/CNZ runtime state, palette ownership, animated tile orchestration, CNZ staged overlay rendering, and CNZ layout mutations via `ZoneLayoutMutationPipeline`. Sonic 3&K uses the same stack for AIZ/HCZ/CNZ runtime-state adapters, AIZ staged render effects and advanced render modes, HCZ/SOZ animated tile channels, CNZ runtime-state-backed scroll behavior, and seamless terrain-swap/mutation paths routed through the mutation pipeline. The shared scroll-composition helpers are live in AIZ, HCZ, and MGZ. Other S1/S2/S3K zones still mix these frameworks with older zone-local paths and should be treated as follow-up migration work rather than implied complete adoption.

Near-term S3K work should be planned as playable route slices with explicit gates: required traversal objects and badniks, event/camera behavior, scroll/parallax, animated tiles, palette and PLC state, bosses or transitions, rewind coverage where state is gameplay-relevant, trace replay for known blockers, and visual validation against stable-retro where practical. The first target route is AIZ through HCZ, with CNZ/MGZ/ICZ work feeding the same slice-driven standard instead of a checklist-only rollout.

Work is ongoing across all three games. Recent branch work spans S3K route
bring-up (AIZ, CNZ, MGZ, ICZ, and Mushroom Hill), S2 trace-frontier closures
(Sky Chase and Casino Night level-select replays), object-physics
standardization onto shared contracts, expanded rewind coverage, and
architecture-guard hardening across runtime ownership, trace/rewind invariants,
and object-service boundaries. See CHANGELOG.md for the detailed, per-merge
history.

### Where do I get ROMs?

We do not supply ROM images. You must provide your own legally obtained copies. The engine expects
these specific revisions, placed in the working directory:

| Game | Expected filename | Revision |
|------|-------------------|----------|
| Sonic 1 | `Sonic The Hedgehog (W) (REV01) [!].gen` | World, Revision 01 |
| Sonic 2 | `Sonic The Hedgehog 2 (W) (REV01) [!].gen` | World, Revision 01 |
| Sonic 3&K | `Sonic and Knuckles & Sonic 3 (W) [!].gen` | World (lock-on combined ROM) |

Other revisions (REV00, etc.) are untested and will likely produce incorrect results, as
ROM addresses are verified against these specific builds. ROM filenames are configurable via
`config.json` (see `SONIC_1_ROM`, `SONIC_2_ROM`, `SONIC_3K_ROM` keys).

### What is cross-game feature donation?

A feature that lets a donor game (S2 or S3K) provide player sprites, spindash mechanics, sound
effects, and the data select (save/load) screen while you play a different base game (e.g.
Sonic 1). This means you can play S1 levels with S2's Sonic and Tails sprites, spindash, and
sidekick AI — and when S3K is the donor, you also get the full S3K data select screen with
save slots and team selection before gameplay begins.
When S3K is the donor, that donated data select now also uses host-specific emerald presentation
and runtime-generated S1/S2 zone preview screenshots. Data select donation is only enabled when
`CROSS_GAME_FEATURES_ENABLED` is `true` and `CROSS_GAME_SOURCE` is `"s3k"`. Enable it in
`config.json`:

```json
{
  "CROSS_GAME_FEATURES_ENABLED": true,
  "CROSS_GAME_SOURCE": "s3k"
}
```

Both the base game ROM and the donor game ROM must be present.

### Why Java?

We knew Java, and nobody had done it before. Every other Sonic engine reimplementation out there is
written in C, C++, or C#. A Java implementation proves it can be done on a managed runtime, and
the JVM's cross-platform nature means it runs on Windows, macOS, and Linux without platform-specific
builds (though a GraalVM native image is also available for those who prefer it).

### Will Sega shut this down?

This project contains no copyrighted material. No ROM data, sprites, music, or other Sega assets
are included in the repository. The engine is an independent reimplementation, developed and
verified against the community-maintained disassemblies, that requires users to supply their own
legally obtained ROM files. We have no affiliation with Sega and make no claim to any of their
intellectual property.

### What platforms does it run on?

Anywhere Java 21 and LWJGL run: Windows, macOS, and Linux. The engine uses OpenGL 4.1 core profile
(chosen for macOS compatibility). A GraalVM native image build is also supported for ahead-of-time compiled
binaries.

### Did you use AI to write this? / This is AI slop!

Various agents (Claude, Codex, and Gemini, in various models, versions and forms) have all been used at various points in the project's history, and
the commit history doesn't hide it; you'll see `Co-Authored-By` tags throughout. But the project
has been in development since 2013, long before AI coding assistants existed.

The core engine framework, architecture, rendering pipeline, physics engine, and collision system
were designed and coded by hand. The multi-game provider architecture, the GPU shader pipeline, the
SMPS audio driver, and the original physics rewrite are all human-authored. AI was brought in
for bulk analysis and research, to accelerate bulk object and boss implementation, debugging, validation, and
unit tests; all under direct architectural oversight, with accuracy verified against the original
ROM disassemblies. Every commit is reviewed, tested, and corrected where needed.

You can't prompt your way to ROM accuracy (yet!). But we certainly prompted our way through object
implementations, research and boilerplate code a lot faster than would have been possible by hand.

### How can I contribute?

The project is open source. Check the issue tracker, OBJECT_CHECKLIST.md for unimplemented game
objects, and CHANGELOG.md for the current state of each game. The codebase uses a provider-based
architecture that makes it relatively straightforward to add new objects, zones, and game-specific
behaviour.

## Releases

### v0.6.prerelease (Current development snapshot)

Development since `v0.5.20260411` is the active 0.6 prerelease line. The detailed running notes now
live in `CHANGELOG.md`; this README keeps only the high-level shape of the release.

- **Widescreen foundation + Discord Rich Presence (2026-05-30).** New `DISPLAY_ASPECT` presets
  (NATIVE_4_3 / 16:10 / 16:9 / 21:9 / 32:9, height fixed at 224) make camera, boundaries, spawn
  windows, and the object despawn/visibility surface width-driven, with `NATIVE_4_3` kept
  byte-for-byte identical and pinned by a regression test; trace/headless runs are forced to
  320×224. Deliberate right-boundary/despawn divergences are logged in `KNOWN_DISCREPANCIES.md`;
  widescreen UI centering and title/special-stage backgrounds remain experimental. Also adds
  opt-in Discord Rich Presence behind `DISCORD_RICH_PRESENCE_*` config flags.
- **S2 trace-replay parity (2026-05-15 to 05-18).** Native title-card prelude ticking, frame-0
  bootstrap comparison, and re-recorded S2 traces surfaced and fixed six engine bugs; the Casino
  Night and Sky Chase level-select replays now reach green.
- **Editor groundwork.** A config-gated editor/playtest loop with block/chunk previews, world-grid
  navigation, and teardown-safe mode switching that preserves `MutableLevel` edits across
  gameplay-mode rebuilds.
- **Runtime/session modernization.** Retired the legacy `GameRuntime` / `RuntimeManager` façade;
  process-wide services sit behind `EngineServices`, with `SessionManager`, `WorldSession`, and
  `GameplayModeContext` owning lifecycle, durable world state, and gameplay-scoped managers.
- **Architectural hardening.** Object code stays on injected `ObjectServices`, shared
  checkpoint/level-loading paths avoid concrete S3K dependencies, animation managers join rewind
  snapshots, trace parity tests sit behind an explicit Maven profile, and ArchUnit guards plus
  review sweeps moved remaining game-id branches behind providers/feature flags.
- **S3K bring-up and parity.** Angel Island, Carnival Night, Hydrocity, Marble Garden, Ice Cap, and
  Mushroom Hill continue to gain ROM-cited objects, badniks, minibosses/bosses, events, parallax,
  animated tiles, and palette/PLC coverage, prioritizing complete playable route slices.
- **Display and audio polish.** Runtime-cycleable display color profiles (`RAW_RGB` / `MD_ANALOG` /
  `NTSC_SOFT`, persisted), S3K SMPS pitch/modulation/1-up restore timing fixes, and foreground-mask
  water alignment against the rendered viewport.
- **Rewind framework.** Gameplay-scoped keyframe capture, deterministic seek/replay, held-rewind
  trace debugging, opt-in live-play rewind (`LIVE_REWIND_ENABLED`) with reverse audio and a HUD,
  and broad coverage across player/sidekick/object/level/registry state via audit-first generic
  capture, stable identity ids, and compact schema codecs.
- **Trace replay and diagnostics.** Stronger recorder schemas, comparison-only aux streams,
  compressed fixtures, expanding per-frame diagnostic events (register, solid-object, control-lock,
  and wall-sensor capture), and a visual trace visualizer with ghost characters, first-desync pause,
  and a camera-focus cycler. Dozens of ROM-cited S3K physics and object fixes advanced the AIZ, CNZ,
  and MGZ replay frontiers; remaining blockers are documented with ROM constraints.
- **S3K known blockers.** Remaining replay-frontier blockers (e.g. AIZ F6920 collapsing-platform
  ordering, CNZ F6304 Tails-on-door re-land) are documented with ROM constraints and ruled-out
  hypotheses so future work avoids regression-prone hacks.
- **Object physics standardization.** Typed solid/touch/participation/control/native-position/
  lifecycle contracts with the highest-risk object paths migrated, updated agent guidance, and
  static guard tests enforcing explicit contracts.
- **Cross-game cleanup.** Collision, solid-object ordering, sidekick handling, feature-flagged
  physics differences, configuration UX, debug rendering, and performance hot paths continue to be
  tightened across all three games.
- **Architectural cleanup.** Remaining game-id branches moved behind providers/feature flags;
  render overlays (HTZ earthquake, HCZ wall-chase) migrated into `SpecialRenderEffectRegistry`;
  `ScrollEffectComposer` adoption brought to 100% across all 26 scroll handlers; the rendering
  pipeline extracted from `LevelManager` into a new `LevelRenderer`; S1/S2 child spawns routed
  through `spawnChild`/`spawnFreeChild`; plus assorted hot-path allocation and lookup optimizations.
- **Object render-lifecycle fix.** Closed a "spawns but renders invisibly" class of bug introduced
  by the DI migration: objects that cache a sprite renderer in their constructor went invisible when
  spawned via raw `addDynamicObject` (which sets services only after construction). Fixed the S2 and
  HCZ egg-prison capsule animals, added a source guard (`TestNoRendererCaptureInUnsafeSpawn`) and a
  one-time runtime warning so the otherwise-silent failure cannot recur.
- **Trace video capture (2026-06-03).** A headless, deterministic `TraceCaptureTool` renders a
  recorded trace offscreen and writes a lossless, audio+video-synced MKV (FFV1 + 48 kHz FLAC) for
  demo reels — optionally including the desync ghosts. The scope-agnostic `com.openggf.capture`
  pipeline (recorder/sink/encoder + video/audio taps) sits above the game; a `HeadlessGameBoot`
  stands up an offscreen `GameLoop`, a `TraceReplayDriver` (extracted from `TraceSessionLauncher`)
  drives the deterministic replay, and an offline audio backend mode synthesizes for the recording
  without any sound-device output. See the `trace-capture` skill and `CONFIGURATION.md`. This
  develop sync also brings the S3K LBZ tube elevator, ride grapple, Ribot badnik, and rolling-drum
  parity work from concurrent branches.

See `CHANGELOG.md` for the detailed 0.6 prerelease change history.

### v0.5.20260411 (Released 2026-04-11)

A primarily architectural release. The engine internals have been restructured to prepare for level
editor support, safe gameplay-mode teardown, and multi-instance play-testing, while Sonic 3 & Knuckles
gameplay coverage has expanded across Angel Island and Hydrocity. AIZ2 now has the Flying Battery
bombing sequence, end boss, post-boss capsule/cutscene flow, and AIZ-to-HCZ transition represented,
while HCZ now has a larger object/event pass and HCZ1-to-HCZ2 progression.

- **Two-tier service architecture:** all 180+ game object classes migrated from direct singleton
  access to a two-tier dependency injection pattern (`GameServices` global facade + `ObjectServices`
  context-scoped injection). NoOp sentinels replace null checks throughout.
- **Gameplay session ownership:** this release introduced the first explicit gameplay-state
  ownership layer, later superseded by `SessionManager`, `WorldSession`, and
  `GameplayModeContext`. Enables safe editor mode enter/exit and level rebuilds.
- **LevelManager decomposition:** the engine's largest class broken into `LevelTilemapManager`,
  `LevelTransitionCoordinator`, and `LevelDebugRenderer` with ~73 methods extracted.
- **MutableLevel:** snapshot, mutation, and dirty-region tracking for level tile data — the
  foundation for the upcoming level editor's undo/redo and real-time tile editing.
- **Common code extraction (5 phases):** 15+ abstract base classes, 10+ shared utilities, and
  systematic deduplication across all three games, including `SubpixelMotion`, `AnimationTimer`,
  `FboHelper`, `AbstractMonitorObjectInstance`, `AbstractSpikeObjectInstance`,
  `AbstractZoneScrollHandler`, and more.
- **Knuckles** is now a playable character with full glide/climb state machine, ROM-accurate
  jump height, wall grab, ledge climb, and sliding physics. Works in S3K natively and via
  cross-game donation into S1/S2 with correct palette and HUD from the lock-on ROM.
- **Sonic 3&K** expands with title screen (SEGA logo, Sonic morph animation, interactive menu),
  level select screen (SONICMILES background, zone icons, sound test), AIZ miniboss completion
  (defeat flow, napalm attack, staggered explosions), AIZ2 Flying Battery bombing/end-boss work,
  signpost and results screen, Blue Ball special stages (WIP) with per-character art/palette,
  S3K bonus-stage work across Gumball, Glowing Sphere/Pachinko, and Slots, per-character physics
  profiles, palette cycling for all zones, HCZ water rush / conveyor / fan / block / door /
  miniboss coverage, and many new badniks/objects including CollapsingBridge, MegaChopper,
  Poindexter, Blastoid, Buggernaut, Bubbler, TurboSpiker, and InvisibleHurtBlockH.
- **Insta-shield** fully implemented with ROM parity: activation, hitbox expansion, persistent
  lifecycle, cross-game donation, and DPLC cache management.
- **Multi-sidekick system** with configurable sidekick chains, per-character respawn strategies,
  virtual VRAM bank allocation, and VDP-accurate sprite priority ordering.
- **Tails AI rework:** ROM-accurate respawn gating, PANIC mode rewrite, flying/despawn
  improvements, P2 manual override, and per-zone boss/event wiring.
- **Cross-game donation** now bidirectional: S1 can donate into S2/S3K, with `DonorCapabilities`
  interface, `CanonicalAnimation` vocabulary, and `AnimationTranslator` for any game pair.
- **Rendering pipeline:** PatternAtlas slot reclamation, batched DPLC updates, virtual pattern ID
  validation, SAT sprite-mask replay ordering for mixed-priority S3K bonus-stage art, and
  fail-fast shader error handling.
- **Trace replay testing:** automated accuracy verification that records per-frame physics state
  from the real ROM, then replays the same inputs through the engine and compares every field.
  First trace (S1 GHZ1, 3,905 frames) passes with 0 errors; the latest GHZ bridge pass fixes
  the F2967 rider Y divergence by keeping Bri_Solid's final `Plat_NoXCheck` width and updating
  the rider bend log before sag calculation (`docs/s1disasm/_incObj/11 Bridge.asm:98-114`,
  `135-152`, `_incObj/sub PlatformObject.asm:19-42`, `58-76`, `_incObj/sub ExitPlatform.asm:8-23`).
  A second baseline (S1 MZ1, 7,936 frames) now passes after the Obj52 Moving Block
  jump-carry fix: S1 `MBlock_StandOn` clears Sonic's on-object status via
  `ExitPlatform`, then still moves the block and applies one final `MvSonicOnPtfm2`
  carry on the jump-off frame (`docs/s1disasm/_incObj/52 Moving Blocks.asm:65-83`,
  `_incObj/sub ExitPlatform.asm:5-24`, `_incObj/15 Swinging Platforms.asm:177-194`).
  Supports both BizHawk (Windows, Lua) and **stable-retro** (cross-platform,
  Python) as recording backends — both produce identical output consumed by the same Java test
  infrastructure.
- Comprehensive user guide, 15+ design specs and implementation plans, and broad test coverage
  improvements including automated singleton lifecycle testing.

See CHANGELOG.md for full details.

### v0.4.20260304 (Released 2026-03-04)

A release-sized update focused on expanding playable coverage, ending sequences, and engine maturity.

- **Package rename** from `uk.co.jamesj999.sonic` to `com.openggf` across the entire codebase.
- **Master title screen** implemented: engine-wide PNG-based title screen with animated clouds, game
  selection, and pixel font renderer. Displayed on startup before entering game-specific title flow.
- **Sonic 1** has moved from initial support to feature complete: title screen flow, special
  stages, major per-zone event scripting, extensive object and badnik additions, multiple boss
  implementations (GHZ, MZ, SYZ, LZ, SLZ, FZ), Labyrinth water/drowning/splash behaviour,
  ending/credits work, SBZ post-level-end sequence, demo playback, edge balance and push block
  collision corrections, and slope crest sensor guard. Expect minor bugs, but the game should be playable
  from beginning to end.
- **Sonic 2** adds title screen support, major object passes for MTZ/SCZ/WFZ/OOZ, 9 boss fights
  (MCZ, MTZ, WFZ, and both DEZ bosses — Mecha Sonic and Death Egg Robot, plus Robotnik escape),
  a complete credits and ending cutscene system with ROM-accurate visuals, expanded per-zone event
  architecture, demo playback, signpost/badnik palette/stair block art fixes, and a systematic
  TODO resolution pass with disassembly validation.
- **Sonic 3&K** sees major AIZ progress including intro cutscene systems, hollow tree and vine
  traversal parity work, miniboss object set bring-up, initial badnik implementations, shield/PLC
  integration fixes, a full water system with provider architecture and underwater palettes,
  seamless AIZ fire transition flow, and related regressions/tests.
- **Cross-game feature donation** implemented: a donor game (S2 or S3K) can provide player sprites,
  spindash dust, physics, palettes, and SFX while the base game handles levels, collision, objects,
  and music. Now includes cross-game Super Sonic delegation.
- **Per-game physics** and Super Sonic state/control flow (implemented for S2, with cross-game
  delegation to S1 and S2 game modules).
- **Profile-driven level loading:** declarative `LevelInitProfile` system with 13 ROM-aligned
  steps per game, replacing the monolithic `loadLevel()` path.
- **Testability refactor:** `GameContext`, `SharedLevel`, `HeadlessTestFixture` builder, and
  profile-driven test teardown. Test grouping by level and 8-JVM parallel execution.
- **Engine fixes:** solid object edge jitter fix, S1 slope crest sensor guard, jump-while-airborne
  guard, fade transition flash fix, results screen rendering fix, HTZ earthquake fixes, SFX
  channel replacement fix.
- PLC/art-loader refactors, RomOffsetFinder/ObjectDiscoveryTool enhancements, configuration
  documentation, and broad audio/stability/performance hardening.

See CHANGELOG.md for full details.

### v0.3.20260206

A massive release covering 366 commits across every major subsystem.

- **Tails** (Miles Prower) is now a playable character with ROM-accurate CPU AI follower behaviour,
  input replay, flight, and configurable sidekick toggle.
- **Multi-game architecture:** The engine has been refactored to support multiple games via a
  provider-based abstraction layer, with initial Sonic 1 ROM support (level select, title cards, HUD,
  audio with S1-specific SMPS driver configuration) alongside the existing Sonic 2 support.
- **Physics:** The physics engine has been completely rewritten to match ROM behaviour.
- **Bosses and objects:** Boss fights are implemented for 5 zones (EHZ, CPZ, HTZ, CNZ, ARZ), along
  with 15+ new badniks and 50+ new game objects spanning all implemented zones.
- **Water:** A full water system with drowning mechanics is in place for CPZ and ARZ.
- **Graphics:** The graphics backend has been migrated from JOGL to LWJGL with a GPU-accelerated
  rendering pipeline (pattern atlas, tilemap shader, instanced sprite batching, priority FBOs).
- **Audio:** Major accuracy improvements to YM2612 FM synthesis (based on Genesis-Plus-GX reference)
  and the SMPS driver.
- **Infrastructure:** Per-game ROM configuration, a HeadlessTestRunner for physics integration
  testing, visual and audio regression test suites, a multi-game test annotation framework, GraalVM
  native build support, and significant performance optimisations throughout.

See CHANGELOG.md for full details.

### v0.2.20260117

Improvements and fixes across the board. Special stages are now implemented, feature complete with a
few known issues. Physics have been improved, parallax backgrounds implemented and complete for EHZ,
CPZ, ARZ and MCZ. Some sound improvements, title cards, level outros, etc.

### v0.1.20260110

Now vaguely resembles the actual Sonic 2 game. Real collision and graphics data is loaded from the
Sonic 2 ROM and rendered on screen. The majority of the physics are in place, although it is far
from perfect. A system for loading game objects has been created, along with an implementation for
most of the objects and badniks in Emerald Hill Zone. Rings are implemented, life and score tracking
is implemented. SFX and music are implemented. Everything has room for improvement, but this now
resembles a playable game.

### v0.05 (2015-04-09)

Little more than a tech demo. Sonic is able to run and jump and collide with terrain in a reasonably
correct way. No graphics have yet been implemented so it's a moving white box on a black background.

### v0.01 (Pre-Alpha, first documented 2013-05-22)

A moving black box. This version will be complete when we have an unskinned box that can traverse
terrain in the same way Sonic would in the original game.
