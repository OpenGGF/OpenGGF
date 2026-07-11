# OpenGGF - The Open-Source Java-Based Speedy Erinaceidae Engine

> This project is a work in progress. For the current state, please see the latest version in the
> Releases section of this document.

## Introduction

OpenGGF is a community-made, fan-made, open-source Java game engine for research and preservation
of classic Mega Drive / Genesis platform games, specifically the mainline Sonic the Hedgehog
series. It aims to faithfully reimplement the physics and rendering behaviour of the original
hardware using data loaded from user-supplied ROM images. The project's primary goal
is accuracy: physics, collision, and audio are all verified against community-maintained
disassemblies of titles in the Sonic the Hedgehog series. No copyrighted assets are included in
this repository; a legally obtained ROM is required to run the engine.

The engine also aims to provide modern tooling such as a level editor and an open framework for
modding and customisation.

> **Disclaimer:** OpenGGF is a community-made fan project. It is not affiliated with, sponsored by,
> approved by, or endorsed by Sega. Sonic the Hedgehog and all related characters, names, and
> trademarks are the property of Sega Corporation. No ROM images or other copyrighted game data are
> included in this repository. Users must supply their own legally obtained ROM files to use this
> software.
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

Keyboard and standard GLFW gamepads are supported for gameplay and the basic
startup/title/data-select menus.

### Player Controls

| Key | Action |
|-----|--------|
| Arrow Keys | Movement |
| Space | Player 1 action A / jump |
| Right Shift | Player 2 action A / jump |
| Enter | Pause / unpause |

The bundled `config.yaml` exposes keyboard bindings under `input.pause`,
`input.player1`, and `input.player2`. Keyboard B/C are unbound by default;
gamepads map west/south/east face buttons to Mega Drive A/B/C. On Xbox-style
pads that is X/A/B; on PlayStation-style pads that is Square/Cross/Circle.
Additional bindable inputs, including Start and controller assignment, are
documented in [`CONFIGURATION.md`](CONFIGURATION.md); keys omitted from the
template still use the engine defaults until added explicitly.

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
| Sonic the Hedgehog (S1) | Most complete. Includes all zones, bosses, special stages, title screen, ending, and credits. |
| Sonic the Hedgehog 2 (S2) | Broadly playable. Includes all zones, bosses, special stages, Tails AI, ending, and credits. |
| Sonic 3 & Knuckles (S3K) | Work in progress. The Sonic/Tails path has completed AIZ through LBZ coverage, but S3K remains the main active development area. |

Work is ongoing across all three games. See `CHANGELOG.md` for detailed, per-merge history.

### Where do I get ROMs?

We do not supply ROM images. You must provide your own legally obtained copies. The engine expects
these specific revisions, placed in the working directory:

| Game | Expected filename | Expected revision and hash |
|------|-------------------|----------------------------|
| Sonic 1 | `s1.gen` | World, Revision 01; CRC32 `AFE05EEE`; SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| Sonic 2 | `s2.gen` | World, Revision 01; CRC32 `7B905383`; SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| Sonic 3&K | `s3k.gen` | World lock-on combined ROM; CRC32 `63522553`; SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` |

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

- **Sonic 3 & Knuckles route coverage:** the Sonic/Tails path has completed AIZ through LBZ coverage, with ongoing work across bosses, events, objects, bonus stages, scroll/parallax, animated tiles, palette/PLC state, transitions, and rendering parity.
- **Sonic 2 trace closeout:** the full S2 level-select trace suite now passes, including the late OOZ2, ARZ2, CNZ2, and MTZ3 boss/event frontiers and the S2 impatient-wait input gate.
- **Sonic 1 trace progress:** multiple complete-run frontiers advanced or turned green through ROM-order platform, camera, spring, ring, badnik, conveyor, seesaw, staircase, and collision fixes.
- **Sonic 1 bug batch (2026-07-05):** a 25-bug triage-and-fix pass closed 10 player-reported issues (special-stage jump input, egg-prison/lamppost/lavafall/glass-reflection visual lifetimes, Yadrin spike geometry, hurt-spring control recovery, bumper bounce direction, LZ1 door-gated current, and six rewind-state gaps spanning breath/conveyor/speed-shoes/invincibility-music/boss-spikeball state), confirmed 4 reports as genuine ROM behavior, and documented capture recipes for the 4 that need real-hardware evidence; see `docs/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Sonic 1/2 bug batch wave 2 (2026-07-06):** play-testing follow-ups: starpost twirl now rests dead-centre (ROM 32-step terminal angle), lava geyser maker no longer flashes the prior cycle's ending frame, the dormant SYZ Roller is hidden exactly as ROM never displays it, the ROM-verified 62px glass-reflection shimmer is pinned by test, rewind is blocked while special/bonus-stage transitions are pending (S2 softlock fix), and boss child objects are re-adopted by identity with orphan reconciliation after rewind (EHZ boss desync fix); see `docs/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Rewind hardening wave 3 (2026-07-06):** boss children recreated by rewind now re-register with their parent through a single central mechanism (closing the EHZ wheel orphan gap and unifying DEZ/MTZ registration), trace-session rewind gained the same transition-freeze gate as live rewind, and rewind engagement is now blocked while any completion-bearing fade is in flight (closing silently-dropped or softlocked death/act/giant-ring transitions) while gameplay itself keeps ticking through those fades exactly as before; see `docs/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Rewind relink hardening wave 4 (2026-07-06):** rewind recreate-time parent relinks are now bounded by per-object geometric radii (rejecting far same-class matches and dropping the child instead of silently adopting the wrong parent, e.g. a checkpoint twirl attaching to a different lamppost), with unbounded lookup kept only as a named opt-in where a parent legitimately roams; see `docs/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Rewind and audio debt wave 5 (2026-07-06):** held rewind now clamps before committing to a keyframe captured mid-fade with an unrestorable completion callback (closing the scrub-through-fade softlock), the static-state rewind coverage guard now also audits per-GameModule services (four newly visible gaps baselined with justification), S1 SFX id 0xD0 dispatches through the ROM's special SFX pointer table, and the rewind round-trip probe report was regenerated; an empirical DEZ rewind test additionally uncovered (and documented for priority follow-up) an active child-state-loss bug for dynamically event-spawned bosses; see `docs/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Dynamic-boss rewind reconstruction fix wave 6 (2026-07-06):** dynamically event-spawned bosses (SYZ3/GHZ bosses, S3K minibosses) no longer lose all child state on forced rewind reconstruction: phase-1 child adoption now parks unresolved entries and retries to a fixed point while parent reconstruction populates the scratch pool, and codec probe construction no longer leaks real wrongly-parented child objects; see `docs/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Bonus-stage rewind (2026-07-07):** held live rewind now works *within* the Sonic 3 & Knuckles Gumball and Pachinko bonus stages via a per-provider `supportsRewind()` capability, a widened rewindable-mode gate, a per-frame capture hook in the bonus-stage update, and a coordinator adapter that snapshots the ring/life/shield reward accumulators; the timeline is severed at the mode boundary in both directions. The Slot Machine bonus stage stays non-rewindable pending a dedicated runtime snapshot (planned alongside Sonic 1's Special Stage); see `docs/superpowers/plans/2026-07-06-bonus-stage-rewind-gumball-pachinko.md`.
- **Special-stage rewind (2026-07-08):** held live rewind now works within Sonic 1 special stages through a provider capability gate, special-stage replay stepper, and Sonic 1 runtime snapshot adapter. Level entry/exit boundaries intentionally remain rewind timeline boundaries.
- **Gumball rewind capture hardening (2026-07-09):** S3K Gumball Machine capture now ignores stale removed dispenser/spring object references and rebuilds those live links after restore, preventing `RewindIdentityTable` crashes during bonus-stage rewind.
- **Test-suite and rewind determinism hardening (2026-07-09):** the full Maven suite is green again after capturing fixed-skid dust cadence in playable rewind state, rebuilding ARZ Obj83 child slots through identity-preserving reconstruction, correcting Sonic 2 blink/get-up donor mappings and delayed sidekick jump edges, tightening singleton test isolation, and restoring architecture/test-quality guard baselines.
- **S3K Blue Spheres visual parity (2026-07-09):** solo Tails now uses the ROM's player-2 palette line for his special-stage body and tail appendage instead of Sonic's palette line.
- **S2 special-stage trace capture & replay (2026-07-09):** a new BizHawk Lua recorder and PowerShell driver capture Sonic 2 half-pipe special-stage traces (48-column physics schema with BK2 input-alignment verification, real lag-frame flagging, and aux checkpoint/message/results events), with a committed 5299-frame MVP trace for Special Stage 1 (Sonic+Tails). A headless trace-paced replay harness (`TestS2SpecialStageTraceReplay` plus a determinism test) boots the production special-stage provider and diffs every frame against the recorded ROM run via the new read-only `Sonic2SpecialStageManager.captureComparisonState()` accessor — divergences are recorded to the trace report (ratchet intentionally disabled at the MVP frontier; see `docs/TRACE_FRONTIER_LOG.md`). The trace catalog and test-mode picker gained special-stage-aware profiles/labels, a `GameLoop` special-stage skip-gate enables live lag-paced visual SS trace sessions in test mode, and special-stage team resolution now uses the standard two-key character config (making Tails actually spawn in team play); see `docs/superpowers/specs/2026-07-09-s2-special-stage-trace-design.md`.
- **S2 Special Stage 1 trace-green closeout (2026-07-10):** ROM-ordered bootstrap, deferred `RunObjects`, controller sampling, object collisions, checkpoint/emerald completion, per-player rings, swap/timer state, and refresh-gated rings-to-go comparisons now replay 3,228 compared frames with 0 errors and 0 warnings. Every comparator is release-blocking, the scheduled trace profile explicitly keeps the run green, and normal play uses a rewind-safe deterministic lag model derived from the committed 5,299-frame recording. Normal entry now compresses the hidden ROM bootstrap, keeps the transition opaque until the full scene is ready, and starts music with the reveal; trace validation retains the exact startup cadence.
- **Rewind reliability:** gameplay rewind now covers more object graphs, level-trigger/static manager state, child recreation, object identity links, audio history boundaries, and route-specific boss/ride/carry state, with guards for future coverage gaps.
- **Controller support:** gamepad bindings now cover gameplay controls, live rewind, frame advance, debug movement, pause, title/credits confirmations, level-select menus, and launch-option navigation.
- **Player-facing systems:** ROM-backed SEGA boot screens, S3K data select and save/load support, cross-game donation, ROM-derived master-title previews, the legal-disclaimer startup flow, display shaders, pause/HUD fixes, multi-sidekick behavior, user recording/playback, and level-editor plumbing all moved forward.
- **Rendering and performance:** live rewind gained the VHS picture-search effect, rewind tilemap rebuilds were reduced, display/title rendering was tightened, trace ghost rendering was added, and the PSG/audio implementation was cleaned up around the single Genesis Plus GX-derived core.
- **Project-wide performance pass (2026-07-10):** deterministic audio command processing now avoids more than 98% of its measured steady-state allocations, audio rewind history ownership halves the default retained PCM memory, and stereo FIR resampling halves tap-window traversal. Render and special-stage hot paths now reuse frame-owned state, visibility/SAT/overlay buffers, decoded/static geometry, pooled deferred commands, palette uploads, and page-aware virtual-pattern batches; GL teardown is recreatable and verified across repeated reinitialization. The banked series passed the full 11,405-test suite plus the green Sonic 2 special-stage trace and replay-determinism gates.
- **Performance follow-up (2026-07-11):** scrolling background tilemaps now upload only the two entering columns (8,192 B → 256 B for a normal window, 96.875% less), inactive trace rendering eliminates 16 captured callbacks per frame, sprite suppression resolves once per render pass, and warm rewind hits bypass keyframe/callback expansion setup. Repeated source-guard scans were deduplicated, cutting the focused guard median by 15.4%. The integrated batch passed 11,452 tests with zero failures and preserved render ordering, rewind rollback, audio, boundary, SAT, and Gumball determinism checks.
- **Time-attack verifier benchmarks (2026-07-07):** two non-gating JUnit benchmarks measure the replay verifier's real cost against the trace corpus (per-run single-core cost, per-level retained footprint, and warm pooled-reuse behaviour), giving future verifier work a measured basis; the warm-reuse probe surfaced a headless palette-write accumulator leak in the shared `LevelFrameStep` frame step (the per-frame `PaletteOwnershipRegistry` drain was owned solely by `GameLoop`, so headless replay grew O(n^2) writes), now fixed by draining at the single-source frame step; see `docs/superpowers/plans/2026-07-04-time-attack-phase5-verifier.md`.
- **Release hardening:** policy hooks, trace and rewind invariants, BizHawk/stable-retro trace tooling, object-service boundaries, ROM-only runtime asset rules, singleton lifecycle checks, architecture guards, test quality gates, and agent workflow docs were tightened for the prerelease line.

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
- **LevelManager decomposition:** the engine's largest class is now a thin compatibility coordinator
  over focused collaborators including `LevelTilemapManager`, `LevelRenderer`,
  `LevelPlayableArtInitializer`, `LevelDirtyRegionDispatcher`, `LevelWaterCoordinator`,
  `LevelCheckpointCoordinator`, `LevelActTransitionExecutor`, `LevelTransitionCoordinator`,
  and `LevelDebugRenderer`.
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
