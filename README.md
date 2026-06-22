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
> `startup.legalDisclaimer: false` in `config.yaml`.

## User Guide

A comprehensive user guide is available in [`docs/guide/`](docs/guide/index.md), covering:

- **Players:** [Getting started](docs/guide/playing/getting-started.md), [controls](docs/guide/playing/controls.md), [configuration](docs/guide/playing/configuration.md), [game status](docs/guide/playing/game-status.md), and [troubleshooting](docs/guide/playing/troubleshooting.md).
- **Contributors:** [Dev setup](docs/guide/contributing/dev-setup.md), [architecture overview](docs/guide/contributing/architecture.md), [adding zones](docs/guide/contributing/adding-zones.md), [adding bosses](docs/guide/contributing/adding-bosses.md), [audio system](docs/guide/contributing/audio-system.md), [testing](docs/guide/contributing/testing.md), and [trace replay testing](docs/guide/contributing/trace-replay.md).
- **Cross-referencers:** [68000 primer](docs/guide/cross-referencing/68000-primer.md), [mapping exercises](docs/guide/cross-referencing/mapping-exercises.md), [per-game notes](docs/guide/cross-referencing/per-game-notes.md), and [tooling](docs/guide/cross-referencing/tooling.md).

Contributor tests are JUnit 5 / Jupiter only. Do not add JUnit 4 tests, rules, runners, or `org.junit.*` imports.

## Configuration

The engine reads runtime settings from `config.yaml` in the working directory. A legacy
`config.json` is migrated automatically on first run. Key bindings can be written either as GLFW
integer codes or as human-readable names such as `SPACE`, `Q`, or `F9`. See
[`CONFIGURATION.md`](CONFIGURATION.md) and the player guide for the full reference.

## Controls

> Currently, only keyboard controls are supported.

### Player Controls

| Key | Action |
|-----|--------|
| Arrow Keys | Movement |
| Space | Jump |
| Enter | Pause / unpause |

The bundled `config.yaml` exposes these under `input.pause` and `input.player1`.
Additional bindable controller inputs, including Player 1 Start, are documented in
[`CONFIGURATION.md`](CONFIGURATION.md); keys omitted from the template still use the
engine defaults until added explicitly.

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
| F8 | Show/Hide Object Points |
| F9 | Show/Hide Ring Bounds |
| F10 | Show/Hide Plane Switchers |
| F11 | Show/Hide Touch Response |
| F12 | Show/Hide Art Viewer |
| Page Up | Cycle Acts (`debug.keys.nextAct`) |
| Page Down | Cycle Zones (`debug.keys.nextZone`) |

`F9` is also the default level-select shortcut (`debug.keys.levelSelect`), so it
can both open level select and toggle ring bounds while debug overlays are enabled.

### Editor Controls

| Key | Action |
|-----|--------|
| Shift+Tab | Toggle between gameplay and the experimental editor overlay (`debug.flags.editor` must be `true`) |
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
| Sonic 3 & Knuckles (S3K) | Near-complete vertical-slice coverage and now the main parity/release focus. AIZ, HCZ, CNZ, MGZ, ICZ, MHZ, and parts of LBZ have substantial route object, event, boss/miniboss, scroll, palette/PLC, and trace coverage; FBZ and later zones remain the largest content frontier. S3K also includes title screen, level select, data select with save/load support, Knuckles glide/climb, Blue Ball special stages (WIP), bonus-stage parity work, palette cycling, and broad object/badnik coverage. Data select can also be donated to S1/S2 via cross-game donation. |

Recent engine work has also moved shared zone behavior onto runtime-owned frameworks: `ZoneRuntimeRegistry`, `PaletteOwnershipRegistry`, `AnimatedTileChannelGraph`, `ZoneLayoutMutationPipeline`, `ScrollEffectComposer`, `SpecialRenderEffectRegistry`, and `AdvancedRenderModeController`. The current roadmap priority is to use those systems to close playable S3K vertical slices rather than to run broad architecture migrations for their own sake. S1/S2 uplift remains valuable when it removes duplication or active risk in code already being touched, but S3K route completeness now leads work selection.

Current migration status is intentionally partial rather than universal. Sonic 2 already uses the runtime-owned stack for HTZ/CNZ runtime state, palette ownership, animated tile orchestration, CNZ staged overlay rendering, and CNZ layout mutations via `ZoneLayoutMutationPipeline`. Sonic 3&K uses the same stack for AIZ/HCZ/CNZ runtime-state adapters, AIZ staged render effects and advanced render modes, HCZ/SOZ animated tile channels, CNZ runtime-state-backed scroll behavior, and seamless terrain-swap/mutation paths routed through the mutation pipeline. The shared scroll-composition helpers are live in AIZ, HCZ, and MGZ. Other S1/S2/S3K zones still mix these frameworks with older zone-local paths and should be treated as follow-up migration work rather than implied complete adoption.

Near-term S3K work should be planned as playable route slices with explicit gates: required traversal objects and badniks, event/camera behavior, scroll/parallax, animated tiles, palette and PLC state, bosses or transitions, rewind coverage where state is gameplay-relevant, trace replay for known blockers, and visual validation against stable-retro where practical. AIZ through HCZ remains the primary release slice, but CNZ, MGZ, ICZ, MHZ, and LBZ now have enough coverage that work should be prioritized by route blockers and complete-run trace frontiers rather than by first-pass zone bring-up.

Work is ongoing across all three games. Recent branch work spans S3K route
stabilization (AIZ, HCZ, CNZ, MGZ, ICZ, Mushroom Hill, and Launch Base), an S3K complete-run per-zone
trace suite (one Sonic+Tails AIZ->Doomsday movie segmented per zone, each trace
spanning the act1->act2 transition through the zone-exit handoff) with
ROM-accurate in-game pause modelling, explicit trace-entry capability metadata,
and a frontier-only replay mode that bounds failing trace sweeps to the first
divergence plus diagnostic context, animated ROM-derived master-title game previews that replace the
bundled title emblem resource, S2 trace-frontier closures (Sky Chase and Casino
Night
level-select replays), object-physics standardization onto shared contracts,
Casino Night slot-machine reward/display alignment,
expanded rewind coverage, and architecture-guard hardening across runtime
ownership, trace/rewind invariants, and object-service boundaries. A recent
test-suite quality pass (driven by a multi-agent audit) replaced assertion-free
diagnostic, tautological, and source-text-grep tests with real behavioral
oracles, and added a guard that fails the build on assertion-free `@Test`
methods, plus order-dependence hardening (an S3K AIZ replay-probe crash fix, a
fork-mate state-leak fix flagged by the singleton-lifecycle guard, and the MZ1
lost-ring regression test rerouted through the production replay bootstrap so it
is deterministic rather than fork-order dependent). See CHANGELOG.md for the
detailed, per-merge history.

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
`config.yaml` (see `roms.sonic1`, `roms.sonic2`, and `roms.sonic3k`).

### What is cross-game feature donation?

A feature that lets a donor game (S2 or S3K) provide player sprites, spindash mechanics, sound
effects, and the data select (save/load) screen while you play a different base game (e.g.
Sonic 1). This means you can play S1 levels with S2's Sonic and Tails sprites, spindash, and
sidekick AI — and when S3K is the donor, you also get the full S3K data select screen with
save slots and team selection before gameplay begins.
When S3K is the donor, that donated data select now also uses host-specific emerald presentation
and runtime-generated S1/S2 zone preview screenshots. Data select donation is only enabled when
`crossGame.enabled` is `true` and `crossGame.source` is `"s3k"`. Enable it in
`config.yaml`:

```yaml
crossGame:
  enabled: true
  source: "s3k"
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

The pre-AI core — the engine framework and architecture, the rendering pipeline, the physics
engine and its subpixel movement model, and the sensor-based collision system — was designed and
coded by hand over years, long before any agent touched the repo. Other subsystems were built
with heavy AI assistance under direct human oversight; the SMPS audio engine, in particular, was
AI-built and steered against reference implementations rather than hand-written. AI was brought in for bulk analysis and research, to accelerate
object and boss implementation, debugging, validation, and unit tests; all with accuracy verified
against the original ROM disassemblies. Every commit is reviewed, tested, and corrected where
needed.

[You can't prompt your way to ROM accuracy (yet!)](docs/AI_JOURNEY.md). But we certainly prompted our way through object
implementations, research and boilerplate code a lot faster than would have been possible by hand.

For the visual version of that story, the [Development Timeline](docs/DEVELOPMENT_TIMELINE.md) is a
captioned gallery of real dev builds — bugs and all — from a 2015 white-box prototype through to
the present, including the audio engine slowly un-mangling itself.

### How can I contribute?

The project is open source. Start with [`CONTRIBUTING.md`](CONTRIBUTING.md), then check the issue
tracker, OBJECT_CHECKLIST.md for unimplemented game objects, and CHANGELOG.md for the current state
of each game. The codebase uses a provider-based architecture that makes it relatively
straightforward to add new objects, zones, and game-specific behaviour.

## Releases

### v0.6.prerelease (Current development snapshot)

Development since `v0.5.20260411` is the active 0.6 prerelease line. The release focus is S3K playable vertical-slice parity, trace-driven ROM accuracy, release hardening, and gameplay-scoped rewind reliability.

Highlights:

- S3K coverage has expanded across AIZ, HCZ, CNZ, MGZ, ICZ, MHZ, and LBZ, with route objects, badniks, bosses/minibosses, events, scroll/parallax, animated tiles, palette/PLC state, transitions, and rendering fixes advancing by route impact.
- Trace replay is now the main parity workflow: complete-run and level-select traces cover broad S1/S2/S3K routes, diagnostics default to frontier-focused output, and frame-by-frame evidence lives in [`docs/TRACE_FRONTIER_LOG.md`](docs/TRACE_FRONTIER_LOG.md) instead of this README.
- Rewind now has broader gameplay coverage, object identity capture, construction-child restore adoption, generic recreate support, coverage analysis, and round-trip guards for captured objects.
- Runtime-owned frameworks continue replacing zone-local behavior where they reduce duplication or active risk: typed zone state, palette ownership, animated tile channels, live layout mutation, scroll composition, staged render effects, and render-mode overrides.
- Release readiness work tightened policy hooks, trace/rewind invariants, object-service boundaries, ROM-only runtime asset rules, singleton lifecycle checks, architecture guards, and test quality gates.
- Player-facing work includes S3K data select/save support, cross-game donation paths, ROM-derived master-title previews, the legal-disclaimer startup flow, display shader support, pause/HUD fixes, multi-sidekick behavior, and level-editor plumbing.
- Playable sprite slope rendering now keeps steep-slope walk frames synchronized with facing-dependent flip flags during slow turnarounds.
- Sonic 2 multi-sidekick follow-up fixed Tails pausing after fly-in by clearing the approach timer before NORMAL follow, preventing a false Player 2 manual-control stall; chained sidekicks also use the root leader while a direct Sonic sidekick's delayed follow history warms.
- Sonic 2 sidekick CPU parity now keeps ROM `Tails_control_counter` separate from the engine's multi-sidekick approach cadence, clearing the ARZ1 trace frontier and advancing the MTZ/HTZ/CNZ sidekick-counter cluster.
- Trace-cluster fixes: trace reports now regenerate every run (killing a stale-report trap that masked working fixes), a root-count survey ranks traces by true distance-to-green, S1 GHZ collapsing-ledge/platform exact-touch landing now matches the ROM `blo #-16` penetration band, S3K MGZ lightning-shield attracted-ring give timing matches the ROM collision-response list, and the S3K ICZ path-follow platform now reads its ROM `width_pixels` for the on-object balance check (ICZ1 trace frontier f3116 -> f3139).
- Sonic 2 Rexon snake heads now stagger their oscillation phase by head number (ROM `objoff_39` seeding), keeping the head wave aligned to ROM so a rolling player's kill-bounce off the tip head lands (HTZ2 trace frontier f1078 -> f1343).
- Sonic 3 & K LBZ rolling-drum chains now clear the previous drum's standing flag when the player rolls onto the next drum (ROM `RideObject_SetRide` `bclr d6,status(a3)`), keeping the player grounded across drum-to-drum handoffs (LBZ1 trace frontier f1694 -> f1950).
- CPU sidekick auto-jump now re-triggers on the push-bypass cadence frame even while the jump latch is held, matching the ROM push-bypass route that branches straight to the trigger gate (S2 `s2.asm:39297`, S3K `sonic3k.asm:26702`); fixes a grounded pushing Tails failing to hop a breakable block (HTZ2 trace frontier f1343 -> f3315).
- The S3K MGZ/LBZ Smashing Pillar now uses the ROM-accurate inclusive right-edge solid bound (`bhi`, sonic3k.asm:41405), so a player pinned flush against the pillar keeps `Status_Push` every grounded frame as the ROM does (LBZ1 trace frontier f1950 -> f2270; MGZ held at baseline).
- A BizHawk capture worklist in [`docs/TRACE_FRONTIER_LOG.md`](docs/TRACE_FRONTIER_LOG.md) now records the exact register/RAM datapoints needed to resolve three deep, precisely-characterized trace blockers (ICZ1 f3139 solid-object push, HTZ2 f3315 inline-solid rebound, CNZ1 f1691 slot-machine convergence) that require ROM register traces.
- The S1 Obj18 GHZ platform now re-seats a rider to the platform's post-move surface on the jump-off frame (ROM `Plat_Action2` unconditional `MvSonicOnPtfm2`), so jumping off an upward-moving platform matches ROM instead of landing 2px high (GHZ2 trace frontier f2591 -> f3349; also advances GHZ3 f1246 -> f2693 and reduces GHZ1 camera errors).
- Right-wall sensor angle selection now drops a non-ROM cross-frame fallback cache and snaps straight from the current angle (ROM `applyAngleFromSensor` `(angle+0x20)&0xC0`), fixing a stale-angle resurrection on zero-distance odd-angle right-wall contacts shared by S1/S3K (LZ3 trace frontier f1415 -> f6517).
- The vertical camera now follows a moving bottom level boundary even on a sweet-spot frame that produces no normal scroll (ROM `SV_BottomBoundary` under `f_bgscrollvert`), fixing a one-frame boundary-follow lag on a fast roll-land. **This greens the GHZ2 complete-run trace — the first fully passing S1 complete-run trace replay** (no regressions across S1/S2/S3K camera traces).
- The S1 collapsing floor (obj 0x53, MZ/SLZ/SBZ) now lands a rider against the ROM `PlatformObject` `obY-8` entry surface and re-seats to the `obY-9` ride surface next frame (matching the Obj18 platform override family), instead of landing one frame late (MZ3 trace frontier f1702 -> f2079; also advances SLZ2 f651 -> f1016).

- Sonic 2 Death Egg Zone boss fight: the Death Egg Robot's attack clock now processes ROM's group-animation `$C0` end-marker frame, Silver Sonic keeps `Current_Boss_ID` so the player's right boundary stays boss-strict, and the jet-stomp reads its targeting sensor with ROM's one-frame slot-order latency — collapsing the DEZ1 complete-run trace from 127 to 46 errors (frontier f4007 -> f5952).

- Jump headroom: the ceiling probe no longer double-applies the ROM `eori #$F` Y-flip (the probe pre-applied it and `GroundSensor.verticalTileLookupY` applied it again), which had under-reported ceiling clearance and wrongly blocked jumps ROM performs (Spring Yard `SYZ2` trace frontier f1088 -> f6845, 311 -> 55 errors; shared S1/S2/S3K player-physics, GHZ2 stays green).

- Moving-platform ride-off ordering: a rider walking off a horizontally-sliding Obj18 platform now uses the platform's pre-move x for the walk-off bounds and applies ROM `MvSonicOnPtfm2`'s unconditional final carry (matching ROM's ExitPlatform-before-Plat_Move order), so the engine no longer drops the rider one frame early. **This greens the Spring Yard `SYZ2` complete-run trace — the second fully passing S1 complete-run trace replay** (shared S1/S2/S3K solid-contact code; GHZ2 stays green, S3K CNZ/MGZ platform traces byte-identical).

- The S1 GHZ collapsing ledge now accepts a landing across its full ROM `SlopeObject` width (ROM `Ledge_ChkTouch` passes the half-width directly with no `obActWid` narrowing), so a player falling onto the ledge near its left edge is no longer dropped airborne for a few frames (GHZ1 trace frontier f2790 -> f3246, 436 -> 255 errors; GHZ-only object, GHZ2 stays green).

For details, see [`CHANGELOG.md`](CHANGELOG.md); for trace frontier movements and evidence, see [`docs/TRACE_FRONTIER_LOG.md`](docs/TRACE_FRONTIER_LOG.md); for the previous verbose v0.6 merge ledger, see [`docs/changelog/v0.6-prerelease-detailed.md`](docs/changelog/v0.6-prerelease-detailed.md).

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
