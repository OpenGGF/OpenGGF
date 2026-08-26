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

[You can't prompt your way to ROM accuracy (yet!)](docs/AI_JOURNEY.md). But we certainly prompted our way through object
implementations, research and boilerplate code a lot faster than would have been possible by hand.

For the visual version of that story, the [Development Timeline](docs/project/development-timeline.md) is a
captioned gallery of real dev builds — bugs and all — from a 2015 white-box prototype through to
the present, including the audio engine slowly un-mangling itself.

### How can I contribute?

The project is open source. Start with [`CONTRIBUTING.md`](CONTRIBUTING.md), then check the issue
tracker, OBJECT_CHECKLIST.md for unimplemented game objects, and CHANGELOG.md for the current state
of each game. The codebase uses a provider-based architecture that makes it relatively
straightforward to add new objects, zones, and game-specific behaviour.
## Releases

### v0.6.prerelease — Current development snapshot

OpenGGF 0.6 is the current development release focused on accurate, playable
routes through the main Sonic 3 & Knuckles slice, while continuing to improve
Sonic 1 and Sonic 2 parity. The engine loads runtime data from user-supplied
ROMs and validates behavior against the original games' disassemblies and
recorded hardware traces.

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
- **Sonic 2 special-stage timing authority:** recorded `VBlank_Lag` rows remain
  scheduling-only replay inputs, while ordinary play retains its existing
  stateless slowdown approximation until causal hardware timing can replace it.
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
- **Audio and video hardware modeling:** YM2612 FM, PSG, DAC/PCM, SMPS
  sequencing, pause/resume behavior, PAL clocks, priority rendering, tilemaps,
  shaders, sprite batching, and staged art loading have received substantial
  accuracy and stability work. Source-timed S3K FM/PSG services, repeated SFX
  ownership, and the retail two-cell SFX request order preserve incumbent
  services during repeated boss explosions. Reference chip defaults and HQ
  PSG rendering improve Collapse, Spindash Release, Blue Sphere, and mixed
  music/SFX playback. Extra-life retriggers, fade-time SFX admission, and
  override cleanup now preserve the distinct retail S1, S2, and S3K rules;
  S3K's 1-up jingle retains priority over an expiring invincibility theme and
  releases its SFX gate before later boss fades. The standalone sound-test
  launcher provides ROM-backed listening through the same unified presentation
  pipeline.
- **Gameplay-scoped rewind:** dynamic objects, child graphs, rider state,
  level events, audio history, and relevant static state are captured and
  restored with explicit ownership rules. Completed in-frame act reloads such
  as the AIZ fire-curtain transition now re-root history at the destination
  frame, preventing incompatible cross-act restores while preserving rewind
  within the new act. Sonic 2 special stages also preserve their first
  pre-start object-pass deferral across fade-from-white restores.
- **Modern development and validation tools:** level-editor foundations,
  ROM offset and compression tools, headless gameplay tests, BizHawk trace
  replay, visual/audio regression checks, and release/architecture guards.
  Automated test sessions use timestamped session-owned roots and isolate
  LWJGL extraction per Surefire fork so concurrent agent runs cannot corrupt
  one another. Their full build output stays in a searchable session log by
  default, while compact start/end markers expose the manifest and log paths.
- **Agent-friendly workflows:** Codex and Claude workflows include ROM
  cross-referencing, object/boss/zone implementation guidance, trace diagnosis,
  and isolated test-session procedures.
- **Normal local launchers:** `run.sh`, `run.cmd`, `dev.sh`, and `dev.cmd` keep
  the direct package-and-launch workflow for interactive development, while
  certifying builds, tests, and trace evidence remain session-wrapped.

#### Current release status

0.6 is not a final release yet. The automated suite and trace-replay gates are
active, but known parity gaps remain in parts of the Sonic 2 CPZ/WFZ
frontier and the Sonic 3 & Knuckles trace/run-chain frontier. Human end-to-end
gameplay and audio QA are still required before release sign-off.

The current S3K release priority is the AIZ → HCZ playable route. Knuckles
routes, later-zone completeness, and some bonus/special-stage paths remain
outside the primary release slice or are still under active development.

#### Release documentation

- [0.6 changelog](CHANGELOG.0.6.md)
- [Release Summary for website and GitHub](docs/changelog/v0.6-release-summary.md)
- [Detailed development ledger](docs/changelog/v0.6-prerelease-detailed.md)
- [Trace scope and release gate](docs/status/trace-scope-release-6.md)
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
