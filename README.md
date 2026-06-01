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

Work is ongoing across all three games. Recent branch work added compact
palette-cycle rewind coverage and adopted ArchUnit architecture guard tests
with frozen baselines for current boundary debt. A follow-up architecture-
guarding merge then expanded those guards across runtime ownership,
trace/rewind invariants, object-service access, source-level architecture
hazards, and singleton lifecycle setup drift, with documented baselines for
existing migration debt. A later architecture cleanup moved residual
game-policy branches behind providers, removed profiler singleton escapes,
gave editor mode an explicit level-view runtime, and trimmed hot-path
allocation churn in shared render/palette systems. The current line also
continues S3K IceCap bring-up with Freezer and ice-cube object coverage.
A subsequent rewind merge hardened generic rewind
capture for final in-place helper fields such as `SubpixelMotion.State`,
covering S3K monitor and AIZ cutscene object rewind capture paths. The
latest architectural review merge tightens rewind registry ownership,
trace-replay comparison guardrails, object construction boundaries, graphics
runtime rebinding, and MGZ scroll-event state routing through shared
`MgzZoneRuntimeState` instead of direct scroll-handler mutation.
The latest S2 trace-frontier merge advances the Sky Chase level-select replay
through the Tornado/Turtloid route by tightening SCZ object spawning, Tornado
ride input timing, Turtloid projectile placement, and object hurt/platform
landing parity.
A follow-up S2 trace-frontier merge closes the Casino Night level-select
replay to green by tightening CNZ object streaming, slot machine, bumper,
bonus-block, forced-spin, monitor/solid-object, Hex Bumper, and Tails CPU
off-screen respawn parity.
The object physics standardization branch adds typed solid routine, touch
response, player-participation, object-control, native-position, and object
lifecycle contracts, migrates the highest-risk object paths onto those APIs,
updates agent-facing implementation guidance, and installs static guard tests
so future object work declares these physics contracts explicitly instead of
reintroducing ad hoc state mutations.
The latest route-parity pass expands S3K object, event, sidekick, rewind, and
trace-debug coverage around AIZ/CNZ/MGZ slice work; removes temporary trace
bootstrap zone carve-outs in favour of recorder capabilities or live object
semantics; and records the active trace frontier state in
`docs/TRACE_FRONTIER_LOG.md`.
The merged CNZ cutscene and object parity branch fills in the Act 2 Knuckles
cutscenes, Sparkle and Batbot behavior, CNZ cannon/balloon/trap-door fixes,
Act 1 miniboss arena graphics and defeat flow, and CNZ palette/scroll follow-up
coverage.
The follow-up CNZ miniboss merge tightens the Act 1 boss-room background window,
wrapped Plane B tile selection, boss/electricity render ordering, defeat
dismantle coverage, Sparkle floor/ceiling behavior, and Act 2 Knuckles button
cutscene handling.
The latest develop sync repairs CNZ1 solo carry-in Tails, CNZ barber-pole
handoff, CNZ2 cutscene water/lights/wall behavior, water splash dust routing,
and documents the reverted AIZ2 battleship wrap-seam attempt and follow-up
lessons.
This merge keeps the local HCZ/ICZ branch line synchronized with those develop
updates while preserving the reverted AIZ2 wrap behavior.
The S2 Metropolis/Wing Fortress parity branch lands MTZ object/badnik/boss and
WFZ parity passes, advances the MTZ3 trace frontier through a series of
ROM-state-driven fixes, adds a ROM-accurate S3K speed-shoes byte timer
(every-8th-frame decrement) gated on the level frame counter, and wraps the CNZ
conveyor (Obj72) width to a byte so the WFZ level-select trace replay now passes
end to end, with no S1/S2 or S3K trace regressions.
See CHANGELOG.md for detailed progress.

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
  (NATIVE_4_3 / 16:10 / 16:9 / 21:9 / 32:9, height fixed at 224) make the gameplay/config layer
  width-driven: camera deadzone+snap, player/MGZ boundaries, all spawn-placement windows, and the
  full object despawn/visibility surface (32 S1/S2/S3K object sites) scale with the configured
  width, with `NATIVE_4_3` kept byte-for-byte identical and pinned by a native-regression test.
  `TEST_MODE_ENABLED` and headless trace runs are forced to 320x224 so parity traces never run wide.
  The in-level scene renders wide (vscroll columns and background FBO are viewport-driven) and the
  master title screen is widescreen-aware. The deliberate divergences (right-boundary / despawn
  windows) are logged in `KNOWN_DISCREPANCIES.md`. Widescreen UI surface centering and
  title/special-stage backgrounds remain an experimental follow-up (the projection-swap compositor
  was reverted in favour of per-surface width-aware coordinates; see
  `docs/superpowers/specs/2026-05-30-widescreen-rendering-design.md`). Also adds opt-in Discord Rich
  Presence (gameplay state, timer, zone/act) behind `DISCORD_RICH_PRESENCE_*` config flags.
- **S2 native-prelude trace replay (2026-05-15).** Engine title-card phase now ticks objects and
  player physics natively (ROM-faithful `TitleCard_Main` for S1/S2/S3K with per-game gating).
  New `TraceBinder.compareBootstrapFrame0` + `BootstrapDivergence` infrastructure asserts engine
  frame-0 state against the recorder's pre-trace snapshots for traces at `lua_script_version
  >= 9.2-s2`. Diagnostic `oggf.trace.hydrate` switch (CI-asserted off) snaps engine state to the
  recorded frame-0 snapshot for prelude-vs-gameplay bug isolation. All nine S2 trace recordings
  re-recorded at v9.2-s2; comparator surfaced six real engine bugs (CPZ Grabber rolling-kill,
  WFZ Tornado two-frame init, CNZ Flipper per-player cooldown + y_pos, HTZ Lift solid-while-falling,
  S2 sidekick bottom-bound centre-Y kill, S2 sidekick deferred-despawn flow) all fixed against
  `s2disasm` citations. See `docs/superpowers/specs/2026-05-15-s2-native-prelude-traces-design.md`
  for the full orchestration record (12 stages, ADR-1 through ADR-7 with R1/R2 fold-ins, blocker
  resolution pass).
- **S2 CNZ trace-frontier closure (2026-05-18).** Casino Night Zone level-select replay now reaches
  green. The branch fixed CNZ slot-machine timing, object streaming and out-of-range behavior,
  bumper/bonus-block/forced-spin parity, S2 monitor and Big Block solid geometry, launcher-spring
  Tails recapture, and S2 Tails CPU off-screen respawn marker/counter behavior while keeping trace
  data comparison-only and updating the trace frontier log.
- **Editor groundwork:** a config-gated editor/playtest loop, focused block and chunk previews,
  derive edits, world-grid navigation, and safer mode switching are being built toward usable
  in-engine editing. The editor review pass now preserves controller-owned mutable levels across
  gameplay-mode teardown, restores an editor-safe level view while editing, flushes dirty regions before
  editor rendering, and omits reverted baseline edits from saved deltas.
- **Runtime/session modernization:** the legacy `GameRuntime` / `RuntimeManager` facade has been
  retired. Process-wide services now sit behind `EngineServices`, while `SessionManager`,
  `WorldSession`, and `GameplayModeContext` own gameplay lifecycle, durable world state, and
  gameplay-scoped managers. `GameServices`, `ObjectServices`, and runtime-owned frameworks remain
  the active access path for palette ownership, zone state, animated tiles, layout mutation, scroll
  composition, special render passes, and advanced render modes.
- **Architectural review hardening:** the service boundary now keeps object code on injected
  `ObjectServices`, shared checkpoint and level-loading paths avoid concrete S3K dependencies,
  animation managers participate in rewind snapshots, and trace replay parity tests are exposed
  through an explicit Maven profile instead of hidden default-suite exclusions.
- **S3K bring-up and parity:** Angel Island, Carnival Night, Hydrocity, Marble Garden, data select,
  save handling, and sidekick/object interactions continue to gain ROM-cited behavior and tests.
  Carnival Night Act 1 now includes the miniboss arena handoff: miniboss music, boss/raw child
  animations, the spinner/top and coil children, vertical tunnel scrolling, arena wall mutations,
  and the cylinder carry follow-down fix are covered by focused headless/object tests. IceCap now
  includes the harmful ice object registration and object coverage. Future S3K work should close
  whole playable slices first: traversal blockers, event flow, object coverage, visual parity,
  trace blockers, and rewind-relevant state before lower-impact decorative backlog items.
- **Display and audio polish:** The renderer now has runtime-cycleable display color profiles
  (`RAW_RGB`, `MD_ANALOG`, and `NTSC_SOFT`) with persisted configuration and player-facing
  documentation. The same branch tightens S3K SMPS pitch ramp, modulation wait/freeze, and
  1-up music restore timing, and fixes foreground-mask water alignment against the rendered
  viewport.
- **Trace replay and diagnostics:** S1, S2, and S3K trace replay tooling now has stronger recorder
  schemas, comparison-only aux streams, compressed fixtures, and focused workflows for parity fixes.
  The gameplay rewind stack can also be enabled during ordinary live play with `LIVE_REWIND_ENABLED`,
  recording live inputs into the same rewind buffer and drawing a compact live rewind HUD.
  Test-mode visual trace sessions can also render grayscale ghost copies of traced characters during
  desyncs, using isolated sidekick-style DPLC banks and the same sprite layering priorities as the
  live characters while drawing behind them. The live trace visualizer now pauses on first desync,
  shows the configured resume key in the HUD, and keeps the trace picker open when a relaunch is
  attempted during the return-to-menu fade. Pause during a trace session also exposes a camera
  focus cycler: P1 LEFT/RIGHT cycle the viewpoint between Default, the engine and ROM-trace
  sidekick, and the engine and ROM-trace main player, with the active selection shown in the
  top-right HUD; the original camera is restored on unpause and gameplay determinism is preserved
  across frame-step.
- **Rewind framework:** gameplay-scoped keyframe capture, deterministic seek/replay, held-rewind
  support in Visual Trace Test Mode, and headless parity/benchmark coverage are now in place for
  trace debugging. Coverage has expanded across player, sidekick, object, ring, level, runtime-zone,
  palette, parallax, mutation, render-mode, and PLC progress state. Automatic capture is being
  hardened through an audit-first `GenericFieldCapturer`, stable identity ids for player/object
  references, the `RewindFieldInventoryTool`, `RewindPolicyRegistry`, and compact schema codecs for
  value, helper, collection, record, player-reference, and object-reference fields. The current
  object rollout intentionally centralizes default subclass scalar capture and field policy so broad
  coverage does not require repeated leaf-object rewrites. Compact schema-backed sidecars now cover
  default non-badnik object subclass scalar state when codecs are available, with inventory modes for
  annotation density, object rollout candidates, child/spawn graph hotspots, and encounter replay
  validation. Live and visual-trace rewind presentation now also carries reverse audio from the
  PCM history ring, keeps graphical fades aligned with restored rewind snapshots, and treats final
  object-reference collections as structural state instead of compact scalar sidecar payload. The
  contributor and player docs now describe those rewind audio/fade presentation paths, trace-mode
  controls, and focused validation commands.
- **Trace recorder:** S3K v6.6 AIZ diagnostics expose tree/boundary pre/post state at the F4679
  sidekick boundary frame, transition-floor SolidObjectTop decisions at the F5415 frame, and
  fire-handoff terrain/SolidObjectTop state around F5435 while keeping trace data comparison-only;
  S3K v6.7 CNZ diagnostics now expose cylinder P2 slot and
  execution state around the F4508 frame plus regenerated focused Tails position-write hooks around
  F4790, with recorder diagnostic locals compacted under BizHawk's NLua limit, and engine-side
  sidekick CPU/control diagnostics around the F5087 blocker. S3K v6.11-s3k now records `(a1)`/`(a0)`
  M68K registers per `position_write` hit and a new `solid_object_cont_entry` event capturing
  `y_radius`/`default_y_radius` at `SolidObject_cont` entry so the CNZ F7614 geometric contradiction
  (captured `loc_1E154` lift PCs vs. trace numerics that should fail the precondition) can be
  resolved once the trace is regenerated. S3K v6.12-s3k adds a `control_lock_state_per_frame`
  event capturing `Ctrl_1_locked` / `Ctrl_2_locked` / `Ctrl_1_logical` / `Ctrl_2_logical` per
  frame so AIZ F7381 lock-site hypotheses can be tested directly against ROM RAM. S3K v6.13-s3k
  adds a `terrain_wall_sensor_per_frame` event capturing per-frame wall-sensor and player
  geometry state for both Sonic and Tails so the AIZ F7552 sidekick airborne wall-collision
  parity gap at world `(0x1208, 0x0314)` can be diagnosed against ROM-side wall probes;
  `velocity_write` and `position_write` events now both support multi-window capture. Velocity-
  setter probe diagnostics localised the CNZ F7919 triple `-0x0800` write to
  `ClamerObjectInstance.applySpringLaunch` (correct ROM dispatch given the inputs it sees);
  the upstream divergence is Tails's CPU/flight state in the F7872→F7918 window.
- **S3K trace replay fixes:** Carnival Night sidekick push/facing ordering, grounded release
  input timing, S3K air right-wall separation, wire-cage release parity, high-speed cage capture
  velocity, horizontal-spring airborne contact handling, the SolidObject on-screen gate now
  reading per-object width_pixels against the previous frame's camera (matching ROM render_flags
  bit 7 timing), the new `solidObjectTopBranchAlwaysLiftsOnUpwardVelocity` feature flag
  (matching ROM `loc_1E154`'s position lift before the upward-velocity check at
  `sonic3k.asm:41606-41632`, gated S3K-only) with per-(player, object) standing-bit tracking
  mirroring ROM `a0.d6` semantics, and the cross-game spring-trigger `Status_OnObj` clear
  (matching ROM `sub_22F98`/`sub_233CA`/`sub_234E6` `bclr #Status_OnObj` after
  `bset #Status_InAir` for S3K, S2 `s2.asm:33732-33733`, and S1
  `_incObj/41 Springs.asm:88-89/183-184`) plus the wired `onObjectAtFrameStart` snapshot in
  the Tails CPU `loc_13DA6` follow-steering gate now advance the CNZ v6.5/v6.7 replay frontier
  from F3905 to F7919 while preserving S1/S2 trace baselines.
- **S3K trace replay fixes:** Angel Island sidekick boundary, AIZ1 resize parity, stale reload
  object handoff, reload frame-counter cadence, catch-up flight gating, and the AIZ collapsing-
  platform state-1→state-2 transition slope-sample skip, and the state-2→state-3 unconditional
  promotion (releasing stuck rider state when the platform's stay timer expires with no player
  standing), and the new `levelBoundaryUsesCentreY` feature flag (matching ROM `Player_LevelBound`
  / `Tails_Check_Screen_Boundaries` centre-Y compare for S3K) now advance the AIZ v6.6/v6.9 replay
  frontier from F4679 to F7171; the centre-Y flag is ROM-correct and gated S3K-only pending S1/S2
  trace re-validation; the AIZ2 SonicResize1 miniboss-skip now gates on
  `apparent_zone_and_act == 1` (matching ROM `sonic3k.asm:39053`/`:39164`) instead of the
  heuristic `enteredAsAct2`, and the sidekick LEVEL_BOUNDARY kill now writes `y_vel = -0x700`
  (matching ROM `Kill_Character` at `sonic3k.asm:21149`) so `MoveSprite_TestGravity2` produces
  the ROM-correct in-frame upward shift after the kill, and runs the post-Kill_Character
  MoveSprite step before collision (matching ROM `Tails_Stand_Freespace` at
  `sonic3k.asm:27559`); the kill's touch-floor reset rolling-radius adjustment now uses
  ROM's `old_y_radius - default_y_radius` formula (matching `sonic3k.asm:29134-29156`) instead
  of the engine's prior `getHeight() - getStandYRadius()` -- the latter was injecting a +13
  px error into rolling sidekick deaths and is fully resolved at F4679 (1050 -> 1049 errors).
  AIZ2 SonicResize2 now reads `camera().previewNextX()` (matching ROM's `Do_ResizeEvents`
  ordering inside `DeformBgLayer` AFTER `MoveCameraX` at `sonic3k.asm:38303-38316`), the
  sidekick dead-falling path now preserves `Kill_Character`'s `y_vel = -0x700` across the
  `sub_13ECA` despawn warp (matching ROM `MoveSprite_TestGravity` shifting y_pos by the
  preserved velocity at `sonic3k.asm:36032-36042` before the +0x38 gravity), and Fire Shield
  Dash now mirrors ROM `Reset_Player_Position_Array` at `sonic3k.asm:22166-22193` by zeroing
  the input/status replay buffers alongside the position refill — fixing the F7381 stale
  Stat_table read and advancing the AIZ replay frontier to F7552. F7552 was resolved by the
  hurt-airborne MoveSprite-then-boundary ordering fix (matching ROM `Sonic_Hurt`/`Tails_Hurt`
  at `sonic3k.asm:24449-24467`/`29194-29209`, S2 `s2.asm:37820-37834`, S1
  `_incObj/01 Sonic.asm:1791-1804`); the AIZ Miniboss `Swing_UpAndDown` peak bounce-back
  (matching `sonic3k.asm:177851-177879`) further advanced AIZ to F8927; F8927 is documented as
  a likely airborne wall-sensor x_radius probe-offset gap (engine probes `centreX + xRadius`
  while ROM `CheckRightWallDist` uses a fixed `+10` offset at `sonic3k.asm:20195`).
  `ClamerObjectInstance` now hosts the ROM `Clamer_Index` parent state machine
  (`sonic3k.asm:185866-185998`), including the `loc_88FEC` auto-close gate driven by a
  `Find_SonicTails`-equivalent closer-player lookup, mirroring ROM behaviour across routines
  0x02 (idle) / 0x04 (snap-shut) / 0x06 (auto-close) — foundation for further CNZ Clamer
  parity work. The F7918 spring fire's relatch widening was reverted in favour of a ROM-correct
  three-state spring routine (LIVE / COOLDOWN_DRAIN / COOLDOWN_DONE) mirroring
  `sonic3k.asm:185953-185973` and a player-identity-aware `collision_property` byte mirroring
  ROM `Touch_Special.loc_103FA`'s +1/+2 cprop accumulation (`sonic3k.asm:21186-21194`) so the
  Clamer launches the correct player (Sonic vs Tails) per ROM's `Check_PlayerCollision`
  `cprop & 3` indexing — advancing the CNZ replay frontier from F7919 to F8123.
  Visual trace bootstrap now uses the shared replay bootstrap so AIZ/CNZ visualiser sessions
  match headless replay's seed/cursor policy.
- **S3K trace replay fixes:** Marble Garden frame-zero replay timing now treats traces whose
  first row already contains Sonic's input-driven movement as native frame-zero rows while still
  keeping the S3K sidekick setup prelude, and the S3K `Screen_Y_wrap_value` mask now wraps
  playable and camera-focused Y at MGZ's `$1000` boundary while preserving the low `y_sub` word,
  S3K monitor break now releases recorded standing/pushing players into air, and S3K monitor
  solidity applies the ROM `SolidObject_cont` vertical overlap offset while clearing stale P2
  standing bits on no-contact; lightning shield sparks now allocate even without headless art and
  lightning double-jump clears the ROM jump-height latch, while move-lock-filtered sidekick
  steering still blocks roll entry from raw held left/right, S3K negative-min-Y object loading
  now applies the ROM vertical band instead of spawning every non-counter object, and S3K airborne
  left-wall collisions now continue into the floor probe (matching `Tails_DoLevelCollision` while
  preserving S2's early-return path); S3K slope resistance now keeps the ROM's from-rest slope
  impulse when the computed effect reaches `$0D` (unlike S1/S2's zero-inertia return), and S3K
  diagonal springs now preserve ground velocity on launch while using the ROM `$10` sloped catch
  range; sidekick offscreen marker recovery now preserves subpixels like the ROM word writes,
  and full-solid contacts now skip off-screen sidekicks like the ROM `render_flags` gate;
	  MGZ dash trigger object 0x59 now uses the ROM `byte_25F0E` sloped-solid table for
	  standing riders, and the S3K sidekick push-release grace now keeps MGZ on ROM's
	  already-loaded `Ctrl_2` sample while leaving the AIZ object-order bridge scoped to
	  its hollow-tree/collapsing-platform context; MGZ Bubbles badniks now remain inert under
		  the ROM `Obj_WaitOffscreen` gate before activation, and S3K Tails hurt-routine
			  frames now skip the normal CPU off-screen timeout path, and S3K jump re-presses
			  clear roll-jump before shield ability dispatch so MGZ air control resumes on
				  ROM's frame, S3K roll landing now uses the ROM current-radius
				  roll-clear snap, and MGZ swinging platform top-solid geometry now
				  samples the ROM-cited pre-control rolling-air player phase, advancing
				  the MGZ replay frontier from F0 to F1451 to F1466 to F1659 to F1910
				  to F2007 to F2015 to F2080 to F2395.
- **S3K known blockers:** Angel Island F6920 sloped collapsing-platform ordering is documented with
  ROM constraints — including precise slope-sample arithmetic, ruled-out hypotheses, and remaining
  open hypotheses — so future work avoids previous-X sampling hacks that regress earlier AIZ frames.
  Carnival Night F6304 Tails-on-door re-land is documented with ROM cites covering the door's
  `SolidObjectFull` solidity triplet and Tails CPU `leader_fast` follow-leader interaction so future
  cross-game work can land an airborne-from-above latch without ad-hoc S3K-only branches.
- **S3K bring-up and parity:** AIZ intro setup now re-adopts the live intro object across headless
  event reinitialization, keeping ROM-style pre-frame intro state available to tests.
- **Cross-game cleanup:** collision, solid-object ordering, sidekick handling, feature-flagged
  physics differences, configuration UX, debug rendering, and performance hot paths continue to be
  tightened across Sonic 1, Sonic 2, and Sonic 3 & Knuckles.
- **Architectural follow-ups:** Routed all remaining direct `objectManager.addDynamicObject(...)`
  call sites in S1 object instance code through the inherited `spawnChild` / `spawnFreeChild`
  helpers (~50 sites across 40 files: badniks, bosses, level objects), so child constructors
  consistently get `CONSTRUCTION_CONTEXT` set and can safely call `services()`. Boss spawn paths
  invoked from outside `AbstractObjectInstance` route through a new `ObjectManager.createDynamicObject(Supplier)`
  helper. Investigation also concluded that the deferred S2 SMPS music ROM-resolution priority
  inversion is not implementable on the current architecture — the relevant tables live inside
  the Saxman-compressed Z80 driver blob and engine `Sonic2Music` IDs are systematically shifted
  relative to the disassembly's `zMasterPlaylist`; the misleading `resolveMusicOffsetFromRom`
  resolver and its dead-pointing constants were removed and the prerequisites documented.
- **Architectural fixes sweep:** review-driven cleanup eliminated remaining game-id branches
  (`LevelManager` respawn-table latch and inline-object-execution gate, `WaterSystem` visual-water
  oscillation, `DefaultPowerUpSpawner` invincibility-stars factory and S1 fixed shield slot — now
  feature-flag/provider gated); removed runtime `.asm` reads from `Sonic3kObjectArtProvider`;
  hardened the trace-replay invariant guard against frame-zero snapshot hydration in S1 credits
  demos and converted hidden divergences into documented known issues; unified
  `GameServices.hasRuntime()` with the gameplay-mode predicate and migrated `bonusStage()` to
  session-owned access; migrated HTZ earthquake and HCZ wall-chase render overlays into
  `SpecialRenderEffectRegistry`; brought `ScrollEffectComposer` adoption to 100% across all 26
  scroll handlers; ported S1 badnik subpixel arithmetic to `SubpixelMotion` and routed S1/S2 child
  spawns through `spawnChild`/`spawnFreeChild` for `CONSTRUCTION_CONTEXT` safety; extracted the
  rendering pipeline from `LevelManager` into a new `LevelRenderer` (4812→3768 lines, GL imports
  collapsed); collapsed `PatternAtlas.isSlotShared` to O(1) via per-slot reference counts;
  eliminated per-call allocations in `endSpriteSatCollectionAndReplay`; replaced bytecode
  constant-pool heuristic in the `ObjectServices` migration guard with a source-level scan; fixed
  `DebugRenderer` Y-coord mix to `getCentreY()`; documented the S2 CPZ visual-water `-8` recentre
  vs ROM `lsr.w #1` divergence in `KNOWN_DISCREPANCIES.md`.

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
