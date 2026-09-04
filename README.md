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
modding and customisation. Neither is delivered yet: the editor is an experimental, config-gated
prototype and the modding framework is planned but not implemented.

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
| Sonic the Hedgehog (S1) | Broadest end-to-end coverage: all zones, bosses, special stages, title screen, ending, credits, and demo playback. |
| Sonic the Hedgehog 2 (S2) | Most complete module by object coverage (122/122 checklist objects) and trace parity. Includes all zones, bosses, special stages, Tails AI, ending, and credits. |
| Sonic 3 & Knuckles (S3K) | Work in progress. AIZ through LBZ have substantial route coverage, but this is not full parity: the AIZ miniboss napalm FallingShot and AIZ2 end-boss splash children now have native implementations with route/trace validation still outstanding, while Knuckles' LBZ Big Arm handoff remains inert. |

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

[You can't prompt your way to ROM accuracy (yet!)](docs/project/ai-journey.md). But we certainly prompted our way through object
implementations, research and boilerplate code a lot faster than would have been possible by hand.

For the visual version of that story, the [Development Timeline](docs/project/development-timeline.md) is a
captioned gallery of real dev builds — bugs and all — from a 2015 white-box prototype through to
the present, including the audio engine slowly un-mangling itself.

### How can I contribute?

The project is open source. Start with [`CONTRIBUTING.md`](CONTRIBUTING.md), then check the issue
tracker, OBJECT_CHECKLIST.md for unimplemented game objects, and CHANGELOG.md for the current state
of each game. The codebase uses a provider-based architecture that makes it relatively
straightforward to add new objects, zones, and game-specific behaviour.
## Licensing

OpenGGF is free software under the GNU General Public License, version 3
([`LICENSE`](LICENSE)). The FM sound core under
`src/main/java/com/openggf/audio/synth/nuked/` is a Java port of
[Nuked OPN2](https://github.com/nukeykt/Nuked-OPN2) by Alexey Khokholov
(Nuke.YKT) and remains under the GNU Lesser General Public License, version
2.1 or later ([`LICENSES/LGPL-2.1.txt`](LICENSES/LGPL-2.1.txt)); it may be
extracted and reused under that licence on its own, and the combined program
is conveyed under GPL-3 through LGPL section 3. [`NOTICE.md`](NOTICE.md)
records the component, its pinned upstream revision and the modifications
made; [`CREDITS.md`](CREDITS.md) lists every contributor, reference source and
library. The executable JAR carries `LICENSE`, `LICENSES/`, `NOTICE.md` and
`CREDITS.md` under `META-INF/openggf/`, and the release archives ship them
next to the launcher.

## Releases

### v0.6.prerelease — Current development snapshot

OpenGGF 0.6 is the current development release focused on accurate, playable
routes through the main Sonic 3 & Knuckles slice and broad Sonic 1 and Sonic 2
gameplay. The engine loads runtime data from user-supplied ROMs and validates
behavior against the original games' disassemblies and recorded hardware
traces.

#### 0.6 highlights

- **Three-game engine:** Sonic 1, Sonic 2, and Sonic 3 & Knuckles each have
  game-specific providers for level loading, physics, objects, bosses, audio,
  rendering, and special stages.
- **Playable game functionality:** Sonic 1 has broad end-to-end coverage;
  Sonic 2 has extensive zone, boss, title, ending, and special-stage support;
  Sonic 3 & Knuckles has a growing playable vertical slice centered on
  Angel Island and Hydrocity, with substantial work across later zones and
  bonus stages.
- **Sonic 2 Tornado parity:** Wing Fortress now preserves ObjB2's retail
  standing, initialization, and reused leader-wait/jump-countdown state across
  every trace row where the parent Tornado can be identified unambiguously.
- **Sonic 2 CNZ slot-machine parity:** Casino Night now runs its zone-global
  slot routine in the retail post-object order with ROM-accurate word
  arithmetic; both release traces match every compared row exactly.
- **Sonic 2 water-palette alignment:** sprite-priority shaders now resolve
  logical scanlines after removing viewport letterboxing, keeping characters,
  objects, terrain, and backgrounds aligned at dynamic waterlines such as CPZ2.
- **Stable object-priority layering:** priority buckets now share one
  palette-mask transition path, preserving slot draw order and water/bridge
  occlusion while keeping the rendering facade within its structural budget.
- **Sonic 2 special-stage timing authority:** recorded `VBlank_Lag` rows remain
  scheduling-only replay inputs, while ordinary play retains its existing
  stateless slowdown approximation until causal hardware timing can replace it.
- **Sonic 2 request-window producer is a command:** TraceChaser now captures
  and extracts request windows from arguments (movie, hash, row interval,
  manifests, installation, output), the raw sink records the recording it
  actually opened, and every published S2 window cites that command.
- **S3K first music update matches the driver:** the post-load DAC pass keys
  off FM6 and restores FM3 mode, `cfSetVoice` writes the release-rate reset,
  notes send the frequency once without a pan write, and PSG frequencies keep
  their full width; the S3K oracle clears tick 138 write for write.
- **Sonic 2 request oracle widened:** four new duplicate-captured request
  windows (EHZ1 continuation, the special-stage transition, and a Chemical
  Plant level-select route) are committed; the oracle matches all 52 transfers
  of the next 750 rows and pins a new first divergence at movie row 12,132.
- **Second Sonic 1 gameplay oracle is a full match:** the shipped
  `Sound_PlaySpecial` silence tail, which writes two stale data bytes instead
  of the intended PSG3 latch pair when a normal PSG3 effect is playing, is now
  emitted as the ROM does; both S1 gameplay recordings match end to end.
- **S3K request sidecar published and wired:** the fourteen source-observed
  mailbox writes are a committed comparison-only fixture the v2 oracle resolves
  against completed services; PSG tracks keep the ROM's AMS/FMS default and
  music activation emits `zBGMLoad`'s single register write, moving the S3K
  oracle from tick 128 to 138.
- **Sonic 1 gameplay oracle widened and doubled:** the GHZ1 window now runs
  to its real boundary (2,562 updates, MATCH), ending where the invincibility
  theme replaces the song, and a second recording from a different complete
  run (5,257 updates) pins a new first divergence at update 1,906.
- **S3K SEGA chant is played by the driver:** the Sonic 3 & Knuckles session
  now runs `zPlaySEGAPCM`'s blocking DAC transport itself (one byte per 248 Z80
  cycles, interrupts held), replacing the presentation-layer sample for that
  game; the S3K oracle moves from tick 50 to 128.
- **Sonic 1 gameplay driver oracle is a full match:** normal sound effects now
  read the special-effect voice bank the shipped driver's `SendVoiceTL` bug
  points them at, closing the last divergence; the engine matches the
  recorded driver over all 2,343 updates of the Green Hill gameplay window.
- **S3K driver init ends where the ROM's does:** the DAC idle loop's entry
  write now opens the first interrupt window instead of the init service,
  moving the S3K oracle from tick 0 to tick 50, the SEGA chant, whose PCM
  transport the driver does not yet own.
- **Sonic 1 special sound effects follow the driver:** the Green Hill waterfall
  now waits for a busy channel, restores its own voice on release, is walked
  after the normal effect slots, and survives a normal effect taking its channel,
  moving the gameplay oracle from update 618 to 1,759.
- **S3K driver oracle reference v2:** the AIZ1 reference is now sampled by
  the observer core at the driver's `zVInt` return, one tick per completed
  service (5,263 ticks over 5,400 frames, 725,898 writes), from two byte-identical
  captures, with the frame shape recorded and the frame field proven
  provenance-only; the first divergence is a single init write at tick 0.
- **S3K oracle reference sampling diagnosed:** the AIZ1 v1 reference samples
  driver RAM mid-invocation on music-load frames, so its tick-138 state is a
  truncated update rather than an engine divergence; the next S3K reference
  must be sampled at the driver's return from the vertical interrupt.
- **Sonic 1 gameplay oracle records special sound effects:** the gameplay
  probe now captures `Sound_PlaySpecial` dispatches alongside normal SFX, and
  the v2 reference (81 live dispatches) exercises the engine's special-SFX
  driver path for the first time, pinning its first divergence at update 618.
- **Sonic 3 & Knuckles music requests observed at the source:** the pinned
  TraceChaser observer now reads the `Play_Music` mailbox while the Z80 is
  stopped, supplying the request the AIZ1 oracle was missing; the driver
  comparison moves from an unobservable input at service 128 to a real
  divergence at update 138. The native observer build is a plain script with
  recorded provenance, no longer gated on host toolchain hashes.
- **Sonic 1 SFX channel ownership follows the driver:** sound effects claim
  their music channels at admission and the driver walks its fixed SFX slots in
  channel-RAM order, moving the gameplay oracle from update 302 to 629, where
  the reference's special-SFX dispatches are not yet captured.
- **Second Sonic 1 audio oracle from real gameplay:** a duplicate-captured
  reference from the complete-run movie (power-on through early Green Hill,
  2,343 driver updates, 70 live sound effects) now sits beside the sound-test
  oracles, with its first divergence pinned at update 302.
- **Committed Sonic 2 request-window fixture:** the driver-oracle gate now
  runs from a published, duplicate-captured reference in the repository rather
  than a scratch capture, and the next audio roadmap (S3K request authority,
  wider windows, two recordings per game) is recorded under the audio plans.
- **Sonic 2 sound driver parity:** the engine's SMPS driver now matches the
  recorded hardware driver over the full EHZ reference window (698 updates)
  with every sound request transfer agreeing, driven from the same BK2 movie.
  Level music starts on the shipped level-entry cadence, SFX release and PSG
  override semantics follow the Z80 driver, and Sonic 2 requests travel the
  ROM's mailbox and queue order.
- **DAC sample pitch:** optional DAC interpolation no longer stalls the
  sample clock (it played drums about 9.5 semitones flat when enabled), and
  the option now defaults to off.
- **S3K temporary-music restoration:** extra-life completion now preserves its
  fade-to-previous request through the presentation boundary, while the AIZ1
  miniboss escape restores the current level track directly.
- **AIZ presentation continuity:** the Angel Island fire curtain now remains
  continuous across exact art-loading seams and completes its ROM-shaped
  release tail in normal play and trace renders, while level music restoration
  follows the ROM escape timer across the AIZ1-to-AIZ2 reload.
- **ROM-accurate gameplay systems:** physics, subpixel movement, sensors,
  collision, solid objects, water, camera behavior, title cards, level events,
  bosses, badniks, sidekicks, Super Sonic, and cross-game feature donation are
  implemented through ROM-owned rules and data. S3K data-select launches retain
  their retail entry cue, music-fade cadence, and destination reveal timing,
  including host-native equivalents when that presentation is donated to S1 or
  S2.
- **MGZ2 boss handoff physics:** jumping out of Tails's carry clears stale
  physical-object support, while the transition camera remains owned by MGZ2's
  native resize event.
- **ICZ multi-sidekick freezer support:** boss frost puffs and placed freezer
  clouds preserve native Player 1/Player 2 ordering while freezing every
  additional configured sidekick into a rescuable ice block, even when the
  native object pool is full.
- **Audio and video hardware modeling:** YM2612 FM, PSG, DAC/PCM, SMPS
  sequencing, priority rendering, tilemaps, shaders, sprite batching, and staged
  art loading have received substantial accuracy and stability work. Audio
  output runs through the unified presentation pipeline, which live recording,
  offline trace capture, and the standalone ROM-backed sound-test launcher all
  share. A further SMPS and chip-level parity programme built during the 0.6
  cycle is deferred to 0.7 and is not part of this release; see the release
  summary for what was withdrawn and why.
- **Sound-driver reverse-engineering groundwork:** disassembly-cited routine
  maps and behaviour specs for all three games' SMPS drivers, an engine gap
  analysis, and committed driver-parity oracles (S1 GHZ music and sound-test
  SFX, an S2 EHZ windowed driver capture, and the S3K AIZ1 intro) with a new
  `docs/status/audio-frontier-log.md` recording every comparison; all
  artifacts are indexed from
  `docs/architecture/designs/audio/2026-08-30-sound-driver-re-index.md`. The
  first source-backed corrections now lock live sequencing to one V-blank
  service per outer frame and reproduce S2/S3K PAL repeat cadence and S3K's
  shared speed-up tail. Sonic 1's committed sound-test SFX oracle now matches
  all 1,967 ticks while its protected GHZ music oracle remains matched across
  14,690 ticks; the current S2 and S3K frontiers stop where their first-version
  captures cannot observe a request before the retail driver consumes it.
- **Resolve-ready capture:** live and trace recording can select DNxHR SQ video
  with lossless 24-bit PCM audio in a MOV container for DaVinci Resolve on Linux.
- **Gameplay-scoped rewind:** dynamic objects, child graphs, rider state,
  level events, audio history, and relevant static state are captured and
  restored with explicit ownership rules. Completed in-frame act reloads such
  as the AIZ fire-curtain transition now re-root history at the destination
  frame, preventing incompatible cross-act restores while preserving rewind
  within the new act. Sonic 1 swinging platforms now also stop chain creation
  cleanly when object RAM is full, so every retained link has a rewind identity.
  Sonic 2 special stages preserve their first pre-start object-pass deferral
  across fade-from-white restores.
- **Modern development and validation tools:** level-editor foundations,
  ROM offset and compression tools, headless gameplay tests, trace replay,
  visual/audio regression checks, and release/architecture guards. Trace
  recording, probing, and publication utilities live in
  [`OpenGGF/TraceChaser`](https://github.com/OpenGGF/TraceChaser), pinned here
  as an optional submodule. They are not required to build, test, package, or
  run the engine; trace contributors can initialize them with
  `git submodule update --init --recursive tools/tracechaser`. TraceChaser uses
  a verified official BizHawk 2.11 installation rather than vendoring the
  emulator.
- **Agent-friendly workflows:** Codex and Claude workflows include ROM
  cross-referencing, object/boss/zone implementation guidance, trace diagnosis,
  and worktree-local direct-Maven procedures. The canonical Sonic 1, Sonic 2,
  and Sonic 3 & Knuckles disassemblies are pinned as optional Sonic Retro Git
  submodules, so GitHub preserves the exact reference revisions without making
  them part of the engine's build, test, or runtime dependency graph.
- **Normal local launchers:** `run.sh`, `run.cmd`, `dev.sh`, and `dev.cmd` keep
  the direct package-and-launch workflow for interactive development; builds,
  tests, guards, and trace evidence use the same direct Maven boundary.

#### Current release status

0.6 is not a final release yet. Automated build, test, guard, and trace
no-regression gates remain active, and human end-to-end gameplay and audio QA
are still required before release sign-off. Known-red Sonic 2 CPZ2 and
Sonic 3 & Knuckles trace/run-chain frontiers are documented 0.6 limitations;
finishing those parity campaigns is deferred to the next release. A frontier
still returns to the 0.6 fix queue when it exposes a confirmed release-impacting
gameplay defect.

Known limitations: the Game Over / Continue flow is missing in all three games
(`docs/status/known-bugs.md`), there is no modding framework, the level editor
is a dormant prototype, and the SMPS audio parity programme is deferred to 0.7.
Release documentation was reconciled with these facts on 2026-08-28: the
release summary now carries the commit-stamped validation numbers, the guard
and trace policy statements match `docs/status/trace-scope-release-6.md`, and
`RELEASE_NOTES_v0.6.prerelease.md` is a pointer to the summary.
Contributor and player guides were corrected the same day (hook installation,
config defaults, player-2 bindings, dead links), and the skill mirrors were
resynchronised.
Repository hygiene followed: IDE/scratch files and two native libraries were
untracked, root plans and launcher scripts moved to `docs/architecture/` and
`scripts/`, saved third-party web pages replaced with provenance stubs, and
`CREDITS.md` now attributes every runtime library, test tool, and chip core.
The object checklists were regenerated from the registries (S2 122/122, S3K
173/303), S3K object `$4F` is now gated by zone set so DEZ no longer spawns
MGZ sinking mud, and the stale S2/S3K bug lists were folded into
`docs/status/known-bugs.md`.
Runtime decompressors moved from `tools/` to `com.openggf.data.compression`,
power-up visuals now come from per-game `GameModule` factories instead of a
shared spawner naming S3K classes, and four unreferenced classes were removed.
The GAME OVER / TIME OVER card now runs in all three games from ROM art and
mappings: a time over restarts the act, a game over fades to the title screen;
continue screens are still absent (`docs/status/known-bugs.md`).
The SN76489 PSG core was rewritten clean-room from public hardware documentation
(`docs/architecture/research/audio/2026-08-29-sn76489-clean-room-spec.md`), removing
the Genesis Plus GX-derived `psg.c` code; behaviour was verified against a pinned GPGX
harness (`docs/architecture/validation/2026-08-29-psg-clean-room-capture-comparison.md`).
The FM:PSG mix balance was restored to its pre-rewrite ratio (PSG preamp 38 % in the mixer),
recorded as uncalibrated against hardware in `docs/status/known-discrepancies.md`
(`docs/architecture/validation/2026-08-29-audio-mix-calibration.md`).

The current S3K release priority is the AIZ → HCZ playable route. Knuckles
routes, later-zone completeness, and some bonus/special-stage paths remain
outside the primary release slice or are still under active development.

#### Release documentation

- [0.6 changelog](CHANGELOG.0.6.md)
- [Release Summary for website and GitHub](docs/changelog/v0.6-release-summary.md)
- [Detailed development ledger](docs/changelog/v0.6-prerelease-detailed.md)
- [Trace scope and release evidence](docs/status/trace-scope-release-6.md)
- [Known discrepancies](docs/status/known-discrepancies.md)
- [Release-readiness roadmap](docs/project/release-readiness-roadmap.md)

### Previous releases

| Release | Notes |
| --- | --- |
| [0.5.20260411](CHANGELOG.0.5.md) | Architectural overhaul, S3K expansion, editor foundations, rendering/audio improvements, and stronger testing infrastructure. |
| [0.4.20260304](CHANGELOG.0.4.md) | S1 expansion, S2 additions, S3K AIZ bring-up, level-loading and tooling improvements. |
| [0.3.20260206](CHANGELOG.0.3.md) | Multi-game architecture, playable Tails, physics rewrite, major object/boss coverage, rendering and audio foundations. |
| [Earlier releases](CHANGELOG.md) | Historical 0.2, 0.1, 0.05, and 0.01 notes. |

See [CHANGELOG.md](CHANGELOG.md) for the release index and [CONTRIBUTING.md](CONTRIBUTING.md)
for contribution guidance.
