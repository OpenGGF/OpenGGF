# OpenGGF - The Open-Source Java-Based Speedy Erinaceidae Engine

> OpenGGF is in active development. This branch documents `0.7.prerelease`;
> the published release remains `0.6.20260911`. See [Releases](#releases) and
> [game status](docs/guide/playing/game-status.md) for their scope and limitations.

## Introduction

OpenGGF is a community-made, fan-made, open-source Java game engine for research and preservation
of classic Mega Drive / Genesis platform games, specifically the mainline Sonic the Hedgehog
series. It aims to faithfully reimplement the physics and rendering behaviour of the original
hardware using data loaded from user-supplied ROM images. The project's primary goal
is accuracy: physics, collision, and audio are all verified against community-maintained
disassemblies of titles in the Sonic the Hedgehog series. No copyrighted assets are included in
this repository; a legally obtained ROM is required to run the engine.

The engine also aims to provide modern tooling such as a level editor and an open framework for
modding and customisation. The experimental [level editor](docs/guide/creating/level-editor.md)
authors playable levels for all three games with safe projects, isolated playtests
and host-validated exports. Open it from the hub’s Advanced menu (More at narrow widths); gameplay entry uses the editor debug flag. The `develop`
and `next` branches share the 0.7 development line and its unpublished Mod API candidate, documented in the
[creator handbook](docs/modding/index.md); it is outside the 0.6 release scope.

> **Disclaimer:** OpenGGF is a community-made fan project. It is not affiliated with, sponsored by,
> approved by, or endorsed by Sega. Sonic the Hedgehog and all related characters, names, and
> trademarks are the property of Sega Corporation. No ROM images or other copyrighted game data are
> included in this repository. Users must supply their own legally obtained ROM files to use this
> software.
>
> The disclaimer is also shown in-engine on startup; it can be disabled by setting
> `startup.legalDisclaimer: false` in `config.yaml`.

## Stageplay: make your own Sonic films

![Day Off, a short film made with Stageplay: Sonic 1's Motobug says "Thirty-five years" on a Green Hill ledge.](docs/guide/creating/images/stageplay/day-off-motobug.png)

Stageplay is OpenGGF's film and cutscene director, new in 0.7. Cast Sonic, Tails,
Knuckles and the games' own badniks, animals and bosses from all three games, put
them in any of their levels, and direct them: where they go, when they jump, what
they say, where the camera looks and what music plays. Characters move with the
games' real physics, routes are planned and checked under that physics, and every
speaker gets a voice from the Genesis sound chip. Direct in the engine's built-in
editor, in [OpenGGF Editor](https://github.com/OpenGGF/OpenGGF-Editor), or as a
text screenplay, then export a lossless film or play the scene as a mod cutscene.

Start with [the Stageplay guide](docs/guide/creating/stageplay.md) and
[your first film](docs/guide/creating/stageplay-first-film.md), or see
[how Day Off was made](docs/guide/creating/stageplay-day-off.md). Stageplay is part
of the 0.7 development line; it is not in the published 0.6 release.

## User Guide

A comprehensive user guide is available in [`docs/guide/`](docs/guide/index.md), covering:

- **Players:** [Getting started](docs/guide/playing/getting-started.md), [controls](docs/guide/playing/controls.md), [configuration](docs/guide/playing/configuration.md), [game status](docs/guide/playing/game-status.md), and [troubleshooting](docs/guide/playing/troubleshooting.md).
- **Creators:** [Level editor](docs/guide/creating/level-editor.md), [Stageplay](docs/guide/creating/stageplay.md) (start with [your first film](docs/guide/creating/stageplay-first-film.md)), and the [mod creator handbook](docs/modding/index.md).
- **Contributors:** [Dev setup](docs/guide/contributing/dev-setup.md), [architecture overview](docs/guide/contributing/architecture.md), [adding zones](docs/guide/contributing/adding-zones.md), [adding bosses](docs/guide/contributing/adding-bosses.md), [audio system](docs/guide/contributing/audio-system.md), [testing](docs/guide/contributing/testing.md), and [trace replay testing](docs/guide/contributing/trace-replay.md).
- **Cross-referencers:** [68000 primer](docs/guide/cross-referencing/68000-primer.md), [mapping exercises](docs/guide/cross-referencing/mapping-exercises.md), [per-game notes](docs/guide/cross-referencing/per-game-notes.md), and [tooling](docs/guide/cross-referencing/tooling.md).

Contributor tests are JUnit 5 / Jupiter only. Do not add JUnit 4 tests, rules, runners, or `org.junit.*` imports.

## Configuration

The engine reads runtime settings from `config.yaml` in the working directory and saves only
your overrides; omitted settings use the current built-in defaults. A legacy
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

Enable **Debug tools** in Settings/Game Setup (`debug.flags.debugView`, off by
default) to use these shortcuts.

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
| K | Show/Hide Ring Bounds |
| F10 | Show/Hide Plane Switchers |
| F11 | Show/Hide Touch Response |
| F12 | Show/Hide Art Viewer |
| Page Up | Cycle Acts (`debug.keys.nextAct`) |
| Page Down | Cycle Zones (`debug.keys.nextZone`) |

`F9` opens level select (`debug.keys.levelSelect`). See the
[controls guide](docs/guide/playing/controls.md) for controller bindings, rewind,
ROM debug controls, and configurable shortcuts.

### Editor Controls

| Key | Action |
|-----|--------|
| Shift+Tab | Enter from stock gameplay with `debug.flags.editor=true`, or return from an editor playtest |
| F5 | Start a fresh playtest from the project's authored start while editing |

Hub **Advanced → Editor projects** provides New/Open/Import. See the
[level editor guide](docs/guide/creating/level-editor.md) for editing, saving and exporting.

## FAQ

### What does "GGF" stand for?

Gotta Go Fast!

### Is this an emulator?

No. OpenGGF is an independent reimplementation of the game logic and physics, written in Java
from scratch. It does not emulate the Mega Drive CPU or VDP. Instead, it reads data (level
layouts, art, music) from original ROM images and runs its own implementation of the game rules.
The implementation is developed and verified against the community-maintained disassemblies
([s1disasm], [s2disasm], [skdisasm]) to achieve pixel-accurate behaviour. The audio engine is a
partial exception: it emulates the YM2612 FM synthesiser and SN76489 PSG chips,
driven by a reimplemented SMPS sound driver. FM synthesis has fast and accurate
cores, including a Java port of Nuked OPN2; the PSG is a clean-room implementation.
[Credits](CREDITS.md) records the implementation and reference sources, including
[libvgm] and [Genesis Plus GX].

[libvgm]: https://github.com/ValleyBell/libvgm
[Genesis Plus GX]: https://github.com/ekeeke/Genesis-Plus-GX

[s1disasm]: https://github.com/sonicretro/s1disasm
[s2disasm]: https://github.com/sonicretro/s2disasm
[skdisasm]: https://github.com/sonicretro/skdisasm

### Which games are supported?

| Game | Status |
|------|--------|
| Sonic the Hedgehog (S1) | Broad end-to-end implementation, including zones, bosses, special stages, ending, credits and demos; remaining parity gaps are tracked. |
| Sonic the Hedgehog 2 (S2) | All zones and bosses, special stages, Tails AI, ending and credits are implemented; trace and presentation frontiers remain. |
| Sonic 3 & Knuckles (S3K) | Substantial campaign implementation across both halves, with character-specific routes, bosses and finales. Complete route and ROM-parity qualification remains in progress. |

These are development-branch summaries, not a claim that every route matches the ROM.
See [game status](docs/guide/playing/game-status.md) for current boundaries and
[the changelog index](CHANGELOG.md) for release history.

### Where do I get ROMs?

We do not supply ROM images. You must provide your own legally obtained copies. The engine expects
the reference revisions below. Their default filenames are hints; the engine can also
discover recognized images in `roms.directory` (the working directory by default):

| Game | Default filename | Reference revision and hash |
|------|-------------------|----------------------------|
| Sonic 1 | `s1.gen` | World, Revision 01; CRC32 `AFE05EEE`; SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| Sonic 2 | `s2.gen` | World, Revision 01; CRC32 `7B905383`; SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| Sonic 3&K | `s3k.gen` | World lock-on combined ROM; CRC32 `63522553`; SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` |

Other revisions (REV00, etc.) are untested and will likely produce incorrect results, as
ROM addresses are verified against these specific builds. Set explicit paths with
`roms.sonic1`, `roms.sonic2`, and `roms.sonic3k`, or configure the scan directory.
The development engine also resolves logical ROMs from lock-on dumps and can
combine separate Sonic & Knuckles and Sonic 3 images in memory. See
[ROM configuration](CONFIGURATION.md#rom-files) for precedence and supported inputs.

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

Anywhere Java 25 and LWJGL run: Windows, macOS, and Linux. The engine uses OpenGL 4.1 core profile
(chosen for macOS compatibility). A GraalVM native image build is also supported for ahead-of-time compiled
binaries for Windows x64, Linux x64, and macOS Apple Silicon. Intel macOS uses the JVM
build; GraalVM 25 does not supply a macOS x64 native-image toolchain. Source builds use
JDK 25 exactly. Address dependency advisories (including LWJGL and Jackson) with aligned
updates on `develop` and `next`, rather than merging dependency bumps into the released
`master` line. A release snapshot is not a guarantee that its dependencies remain free of advisories.

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
tracker, the [game status guide](docs/guide/playing/game-status.md), and the
[coverage backlog](docs/status/level-test-coverage.md) for current gaps. Per-game object
checklists record registry coverage; they do not certify complete routes or parity. The codebase uses a provider-based architecture that makes it relatively
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

### v0.7.prerelease — Current development snapshot

`develop` now carries the post-0.6 work previously staged on `next`, including
widescreen presentation, further S3K campaign coverage, and existing modding,
racing, loading and audio foundations. The editor supports all-game project
authoring, safe saves, isolated playtesting and usable export, with a
controller-first interface drawn around the level. [Stageplay](docs/guide/creating/stageplay.md)
adds shared directing tools for sprite films and mod cutscenes in both editors.
The Mod API is still an
unpublished candidate. [0.7's roadmap](docs/project/v0.7-roadmap.md) prioritizes
complete campaigns before the feature/API publication gates in 0.8.

See the [0.7 changelog](CHANGELOG.0.7.prerelease.md),
[development summary](docs/changelog/v0.7-release-summary.md), and
[trace scope](docs/status/trace-scope-release-7.md).
`develop` and `next` currently share the `0.7.prerelease` line; 0.8 remains future
roadmap scope. The published 0.6 snapshot is retained on
`master`, tag `v0.6.20260911`, and branch `release/0.6.20260911`.


### v0.6.20260911

OpenGGF 0.6 is an accuracy and tooling release focused on accurate, playable
routes through the main Sonic 3 & Knuckles slice and broad Sonic 1 and Sonic 2
gameplay. The engine loads runtime data from user-supplied ROMs and validates
behavior against the original games' disassemblies and recorded hardware
traces.

#### 0.6 highlights

A high-level summary of what 0.6 is about. Every individual change is recorded
in [the 0.6 changelog](CHANGELOG.0.6.md).

- **Three-game engine:** Sonic 1, Sonic 2, and Sonic 3 & Knuckles each run from
  their own ROM through shared, rule-driven systems — physics, subpixel
  movement, sensors, collision, solid objects, water, camera, title cards,
  level events, bosses, badniks, sidekicks, Super Sonic, and cross-game feature
  donation are implemented from ROM-owned rules and data rather than per-game
  special cases.

- **Playable routes:** Sonic 1 has broad end-to-end coverage, Sonic 2 covers
  most zones, and Sonic 3 & Knuckles is playable through the Angel Island to
  Hydrocity slice with later zones in progress. 0.6 extends zone-specific
  behaviour across Wing Fortress, Casino Night, Marble Garden, Icecap and
  Angel Island, including multi-sidekick support and act-transition continuity.

- **Sound driver accuracy:** the largest single area of 0.6. The engine's SMPS
  driver, YM2612 FM, PSG and DAC/PCM paths were reverse-engineered against the
  three ROMs' drivers and are now checked by committed driver-parity oracles
  covering music, sound effects, fades, extra-life restoration and request
  scheduling. Full parity and listening validation remain open; the measured
  boundaries are recorded in
  [`docs/status/audio-frontier-log.md`](docs/status/audio-frontier-log.md) and
  the [audio handover](docs/architecture/plans/audio/2026-09-04-audio-parity-handover.md).

- **Faster FM core:** new configurations select a register-level FM core that
  passes all 178 supported chip scripts; `audio.fmCore=accurate` still selects
  Java Nuked. Audio playback now allocates about an eighth of what it did per
  frame on either core, with every oracle sample unchanged. Full-game
  listening sign-off remains open. See the
  [fast FM validation record](docs/architecture/validation/audio/2026-09-06-fast-fm-release.md).

- **Runtime performance:** capture, rewind, art decoding, animated-art uploads
  and shader state handling all do less redundant work per frame. See the
  [initial measurements](docs/architecture/validation/2026-09-05-runtime-performance.md)
  and [follow-up evidence](docs/architecture/validation/performance/2026-09-05-followup.md).

- **Continue screens:** ROM-backed countdowns and character sequences follow
  Game Over when continues remain, with per-game restart behaviour.

- **Gameplay-scoped rewind:** dynamic objects, child graphs, rider state, level
  events, audio history, and relevant static state are captured and restored
  under explicit ownership rules, including across in-frame act reloads.

- **Recording and capture:** live and offline trace recording share the
  presentation pipeline and can emit DNxHR SQ video with lossless 24-bit PCM
  for DaVinci Resolve on Linux.

- **Development and validation tools:** experimental editor foundations, ROM offset
  and compression tools, headless gameplay tests, trace replay, visual/audio
  regression checks, and release/architecture guards. Trace recording, probing
  and publication live in [`OpenGGF/TraceChaser`](https://github.com/OpenGGF/TraceChaser),
  pinned as an optional submodule that is not needed to build, test, package or
  run the engine.

- **Agent-friendly workflows:** ROM cross-referencing, object/boss/zone
  implementation guidance, trace diagnosis and worktree-local Maven procedures,
  with the three canonical disassemblies pinned as optional submodules outside
  the build and runtime dependency graph.

- **Normal local launchers:** `run.sh`, `run.cmd`, `dev.sh` and `dev.cmd` keep
  the direct package-and-launch workflow for interactive development.

#### Published 0.6 release status

The published snapshot is `v0.6.20260911`. Its
[release summary](docs/changelog/v0.6-release-summary.md),
[trace scope](docs/status/trace-scope-release-6.md), and linked validation records
preserve the tested commits, known failures and release limitations. Those
historical results do not certify the current development tree.

0.6 focused on the AIZ → HCZ playable slice and broad Sonic 1/2 gameplay.
Later S3K routes and full audio/trace parity remained open, and the Mod API was
outside its scope. Follow the current [game status guide](docs/guide/playing/game-status.md)
and [0.7 roadmap](docs/project/v0.7-roadmap.md) for development beyond that snapshot.

#### Release documentation

- [Publishing a GitHub release](docs/project/release-publishing.md) — pushes to
  `master` automatically publish after validation and builds succeed.
- [0.6 changelog](CHANGELOG.0.6.md)
- [Release Summary for website and GitHub](docs/changelog/v0.6-release-summary.md)
- [Detailed development ledger](docs/changelog/v0.6-prerelease-detailed.md)
- [Archived entry-by-entry 0.6 ledger](docs/changelog/v0.6-development-ledger.md)
- [Trace scope and release evidence](docs/status/trace-scope-release-6.md)
- [Known discrepancies](docs/status/known-discrepancies.md)
- [Release-readiness roadmap](docs/project/release-readiness-roadmap.md)

### Development roadmaps: 0.7 campaigns and future 0.8 features

`develop` and `next` share the 0.7 candidate line. The 0.8 roadmap records future
qualification and API-publication goals, not a separate current branch version.
The published 0.6 release scope remains unchanged. The [0.7 roadmap](docs/project/v0.7-roadmap.md) completes and
validates the stock games, with locked-on Sonic, Tails and Knuckles campaigns,
ROM-authored finales/outros and continuous terminal replay chains as the main
remaining S3K gates. Existing modding, racing, editor and widescreen work stays
under regression coverage while those campaign owners settle.

The [0.8 roadmap](docs/project/v0.8-roadmap.md) then graduates those additions:
final policy/API design, qualification of the bundled Time Attack mod and shared
multiplayer services, measured extensions to the already-delivered background loading, live FM
switching, and feature/platform qualification. API publication follows those
consumers; the current Mod API remains an unpublished candidate. Native builds
currently reject code mods and omit bundled racing; its current availability
is JVM-only. These roadmaps describe future release gates;
the 0.6 snapshot and its release evidence remain authoritative for 0.6.

### Previous releases

| Release | Notes |
| --- | --- |
| [0.5.20260411](CHANGELOG.0.5.md) | Architectural overhaul, S3K expansion, editor foundations, rendering/audio improvements, and stronger testing infrastructure. |
| [0.4.20260304](CHANGELOG.0.4.md) | S1 expansion, S2 additions, S3K AIZ bring-up, level-loading and tooling improvements. |
| [0.3.20260206](CHANGELOG.0.3.md) | Multi-game architecture, playable Tails, physics rewrite, major object/boss coverage, rendering and audio foundations. |
| [Earlier releases](CHANGELOG.md) | Historical 0.2, 0.1, 0.05, and 0.01 notes. |

See [CHANGELOG.md](CHANGELOG.md) for the release index and [CONTRIBUTING.md](CONTRIBUTING.md)
for contribution guidance.
