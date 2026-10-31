# OpenGGF

OpenGGF is a free, open-source game engine that plays the classic Sega Mega Drive /
Genesis games *Sonic the Hedgehog*, *Sonic the Hedgehog 2* and *Sonic 3 & Knuckles*.
It is written in Java. You bring your own copies of the games, and OpenGGF does the rest.

> [!NOTE]
> OpenGGF is still in development. This branch holds the next version, 0.7. The
> latest release is [0.6.20260917](https://github.com/OpenGGF/OpenGGF/releases/tag/v0.6.20260917).
> [Game status](docs/guide/playing/game-status.md) lists what works and what doesn't yet.

> [!IMPORTANT]
> OpenGGF is a fan project with no connection to Sega. Sega has not sponsored,
> approved or endorsed it. Sonic the Hedgehog and all related characters, names and trademarks
> belong to Sega Corporation. This repository contains no game files, art or music.
> You must supply your own legally obtained ROMs.

## How it works

A game cartridge holds two things: the game's data, such as levels, art and music,
and the game's code, the instructions that make Sonic run, jump and collect rings.

An emulator pretends to be a Mega Drive. It runs the original code exactly as the
console did, one instruction at a time.

OpenGGF doesn't do that. We rewrote the code for all three games in Java, by hand.
To make it, we used a combination of strategies:

1. Resources written by the Sonic fan community on the games' physics and mechanics.
2. Emulators and Lua scripts that watch the console's memory and pinpoint exactly
   what changes during gameplay.
3. The disassemblies, which are the games' original code turned back into readable
   form by the fan community, when we need to understand complex behaviour.

You don't need any of these to play. OpenGGF loads the data from your ROM file, the
copy of the cartridge you supply, and runs our code in place of Sega's.

The goal is for OpenGGF to play exactly like the original games, bugs included. To
check, our tests press the same buttons in OpenGGF and in an emulator and compare the
two games frame by frame.

## Why rebuild the games?

Sega wrote each game for one console, one TV picture size and one cartridge that
nobody else would ever change. Because OpenGGF's code is ours, we can change it to do
things the original developers never had to plan for.

- **One engine for all three games.** Sonic 1, Sonic 2 and Sonic 3 & Knuckles run on
  the same engine. They share physics, collision, the camera, water and sound, and
  each game adds its own rules on top. We don't know of another project that runs
  all three this way.
- **The games can borrow from each other.** Play Sonic 1 with Sonic 2's spin dash, or
  use Sonic 3 & Knuckles' save screen in Sonic 2. Knuckles in Sonic 2 is built in.
- **Mods without ROM hacking.** Changing the original games means patching assembly
  code inside the ROM. An OpenGGF mod is ordinary Java code that uses our mod API. Mods
  can add new objects, characters, levels and whole campaigns, or turn the engine into
  a different game. Our example mods include a platform fighter, a farming game, a
  rhythm game and a deck-building game. A mod holds only its own code and loads the
  art and music from the player's ROM, so you can share it without sharing Sega's files.
- **Widescreen.** OpenGGF knows the whole level, not just the part on screen, so it can
  show more of it on a wide display.
- **New tools.** OpenGGF adds a [level editor](docs/guide/creating/level-editor.md),
  [Stageplay](docs/guide/creating/stageplay.md) for making short films with the games'
  characters, live rewind, video capture, and Time Attack with online racing.
- **Code you can read.** The engine is a readable record of how these games work. Its
  comments point to the matching routines in the disassemblies, which makes it useful
  for research and preservation.

## Features

- Sonic 1, Sonic 2 and Sonic 3 & Knuckles, plus Knuckles in Sonic 2.
- Physics, collision, objects and bosses built to match the original games.
- Each game's own music and sound effects, played through emulated Mega Drive sound chips.
- Live rewind, with the sound played backwards.
- Widescreen modes, as well as the original 4:3 picture.
- Features borrowed between games.
- Video capture, and recording and playback of your button presses.
- A level editor for all three games.
- Stageplay, for making short films and cutscenes.
- Mods, from music packs to completely new games.
- Time Attack, with ghosts of your best runs and online racing.

[See all features](docs/guide/features.md).

## Getting started

### What you need

- A Windows, macOS or Linux computer whose graphics driver supports OpenGL 4.1.
- ROMs for the games you want to play. Our tests use these versions:

| Game | Version | CRC32 | SHA-1 |
|---|---|---|---|
| Sonic the Hedgehog | World, Revision 01 | `AFE05EEE` | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| Sonic the Hedgehog 2 | World, Revision 01 | `7B905383` | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| Sonic 3 & Knuckles | World, locked-on | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |

Other versions are untested and will probably not work correctly. The file names don't
matter, because OpenGGF recognises each ROM by its contents. It also accepts
lock-on dumps, and separate Sonic 3 and Sonic & Knuckles ROMs. See
[ROM files](CONFIGURATION.md#rom-files).

### Download a release

1. Download the package for your computer from
   [Releases](https://github.com/OpenGGF/OpenGGF/releases) and extract it.
2. Put your ROMs next to `config.yaml`, or find them from OpenGGF's start screen.
3. Run `OpenGGF.exe` on Windows, `OpenGGF.app` on macOS or `./OpenGGF` on Linux.

You don't need to install Java for these packages.

### Build from source

You need JDK 25 and Maven.

```sh
git clone --branch develop https://github.com/OpenGGF/OpenGGF.git
cd OpenGGF
mvn -Dmse=off -DskipTests package
java -jar target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar
```

On macOS, add `-XstartOnFirstThread` before `-jar`. If you plan to contribute, follow
[dev setup](docs/guide/contributing/dev-setup.md) instead. It also installs the
repository's Git hooks.

[Getting started](docs/guide/playing/getting-started.md) walks through every step,
[controls](docs/guide/playing/controls.md) lists the keys and gamepad buttons, and
[configuration](docs/guide/playing/configuration.md) explains the settings.

## Game status

| Game | Status |
|---|---|
| Sonic the Hedgehog | Playable from start to finish, including bosses, special stages, the ending and credits. |
| Sonic the Hedgehog 2 | Playable from start to finish, including all zones and bosses, special stages, the ending and credits. |
| Sonic 3 & Knuckles | Most of the game is playable as Sonic, Tails and Knuckles, through to the endings. We are still testing every route and fixing differences from the original. |

Playable doesn't mean identical to the original everywhere. The
[game status guide](docs/guide/playing/game-status.md) lists the known gaps, and
[known discrepancies](docs/status/known-discrepancies.md) lists the differences we
made on purpose.

## Documentation

| I want to… | Start here |
|---|---|
| Play | [Getting started](docs/guide/playing/getting-started.md), [controls](docs/guide/playing/controls.md), [configuration](docs/guide/playing/configuration.md), [troubleshooting](docs/guide/playing/troubleshooting.md) |
| Make levels | [Level editor](docs/guide/creating/level-editor.md) |
| Make films and cutscenes | [Stageplay](docs/guide/creating/stageplay.md), [your first film](docs/guide/creating/stageplay-first-film.md) |
| Make mods | [Mod creator handbook](docs/modding/index.md), [installing mods](docs/modding/installing-mods.md) |
| Contribute code | [Contributing](CONTRIBUTING.md), [dev setup](docs/guide/contributing/dev-setup.md), [architecture](docs/guide/contributing/architecture.md), [testing](docs/guide/contributing/testing.md) |
| Compare the engine with the disassembly | [How the engine reads ROMs](docs/guide/cross-referencing/how-the-engine-reads-roms.md), [68000 primer](docs/guide/cross-referencing/68000-primer.md), [per-game notes](docs/guide/cross-referencing/per-game-notes.md) |

The [user guide](docs/guide/index.md) lists every guide, and
[CONFIGURATION.md](CONFIGURATION.md) lists every setting.

## FAQ

### What does "GGF" stand for?

Gotta Go Fast!

### Is this an emulator?

No. See [how it works](#how-it-works). Sound is the one exception. OpenGGF emulates
the Mega Drive's two sound chips, the YM2612 and the SN76489, and our own version of
each game's sound program plays music through them. For the YM2612 you can choose a
fast version or an accurate one based on Nuked OPN2. [CREDITS.md](CREDITS.md) lists
the projects we learned from, including [libvgm] and [Genesis Plus GX].

The disassemblies we work from are [s1disasm], [s2disasm] and [skdisasm].

[s1disasm]: https://github.com/sonicretro/s1disasm
[s2disasm]: https://github.com/sonicretro/s2disasm
[skdisasm]: https://github.com/sonicretro/skdisasm
[libvgm]: https://github.com/ValleyBell/libvgm
[Genesis Plus GX]: https://github.com/ekeeke/Genesis-Plus-GX

### Where do I get ROMs?

We don't supply ROMs. You must provide your own legally obtained copies. See
[what you need](#what-you-need) for the versions we test.

### Why Java?

We knew Java, and nobody had done it before. Every other Sonic engine like this is
written in C, C++ or C#. OpenGGF shows it can be done in Java, and Java programs run
on Windows, macOS and Linux without a separate build for each. We also offer native
builds for those who prefer them.

### But Java sucks? Why not Rust?

OpenGGF began in 2013, two years before Rust 1.0 came out. Rewriting it now would
take years, and players wouldn't notice the difference. The hard part of this project
is matching the original games exactly, and a new language doesn't help with that.

Java also suits a moddable engine. A Java program can load new code while it runs,
which is how OpenGGF loads mods. Our native packages run without Java installed and
still load those mods, at a small cost in speed.

And modern Java is fast enough to run all three games at full speed.

### What platforms does it run on?

Windows, macOS and Linux. OpenGGF uses OpenGL 4.1, the newest version macOS supports.
Native packages are available for Windows, Linux and Apple Silicon Macs, and they
load mods that contain code. Intel Macs use the Java version, which needs Java 25.

Each release is a snapshot. Security updates for the libraries OpenGGF uses go into
the next version, not into releases already published.

### Will Sega shut this down?

This project contains no copyrighted material. The repository has no ROM data,
sprites, music or other Sega files. OpenGGF is our own code, and players must supply
their own legally obtained ROMs. We have no connection with Sega and make no claim to
any of their intellectual property.

OpenGGF also shows this disclaimer when it starts. Set
`startup.legalDisclaimer: false` in `config.yaml` to turn it off.

### Did you use AI to write this? / This is AI slop!

We have used AI agents at various points, including Claude, Codex and Gemini in many
models and versions. The commit history doesn't hide it, and you'll see
`Co-Authored-By` tags throughout. But the project began in 2013, long before AI coding
assistants existed.

We wrote the core of the engine by hand over many years, before any agent touched the
code. That core is the engine's framework and design, the graphics, the physics and
its fine-grained movement, and the collision system. We built other parts with heavy
AI help under close human supervision. The sound engine in particular was built by
AI and checked against existing sound chip emulators rather than written by hand. We
also used AI for research, and to speed up work on objects, bosses, debugging and
tests. We checked accuracy against the original disassemblies throughout. We review
and test every commit, and fix it where needed.

[You can't prompt your way to ROM accuracy (yet!)](docs/project/ai-journey.md). But we
certainly prompted our way through objects, research and boilerplate code a lot
faster than we could have by hand.

The [development timeline](docs/project/development-timeline.md) tells the same story
in pictures. It shows real builds, bugs and all, from a 2015 prototype with white
boxes for graphics to today, including the sound slowly getting less garbled.

### How can I contribute?

Start with [CONTRIBUTING.md](CONTRIBUTING.md). Then check the issue tracker,
[game status](docs/guide/playing/game-status.md) and the
[coverage backlog](docs/status/level-test-coverage.md) for gaps. Right now our main
goal is finishing and testing the three original games. See the
[0.7 roadmap](docs/project/v0.7-roadmap.md).

## Releases

| Release | Status | Main themes |
|---|---|---|
| 0.7 | In development | Finishing and testing the original games, especially every character's route through Sonic 3 & Knuckles. It also adds the new start screen, widescreen, Knuckles in Sonic 2, the level editor for all three games, Stageplay, mods and Time Attack. See the [0.7 changelog](CHANGELOG.0.7.prerelease.md) and [roadmap](docs/project/v0.7-roadmap.md). |
| [0.6.20260917](docs/changelog/v0.6-release-summary.md) | Latest release | Accurate play through Sonic 3 & Knuckles from Angel Island to Hydrocity, and most of Sonic 1 and 2. More accurate sound, a faster sound chip emulator, continue screens, rewind, and video capture. 0.6.20260917 is 0.6.20260911 with a fix for the native packages. See the [0.6 changelog](CHANGELOG.0.6.md). |
| [0.5.20260411](CHANGELOG.0.5.md) | Released | A rebuilt engine design, more of Sonic 3 & Knuckles, the start of the editor, better graphics and sound, and more tests. |
| [0.4.20260304](CHANGELOG.0.4.md) | Released | More of Sonic 1 and Sonic 2, the first Sonic 3 & Knuckles zone, and better level loading and tools. |
| [0.3.20260206](CHANGELOG.0.3.md) | Released | Support for several games, playable Tails, rewritten physics, and many more objects and bosses. |

[CHANGELOG.md](CHANGELOG.md) lists earlier releases, the
[0.8 roadmap](docs/project/v0.8-roadmap.md) and other release documents.

## Licence

OpenGGF is free software under the [GNU General Public License, version 3](LICENSE).

The YM2612 sound code in `src/main/java/com/openggf/audio/synth/nuked/` is a Java port
of [Nuked OPN2](https://github.com/nukeykt/Nuked-OPN2) by Alexey Khokholov (Nuke.YKT).
It stays under the [GNU Lesser General Public License, version 2.1 or later](LICENSES/LGPL-2.1.txt),
and you may take it out and reuse it under that licence on its own. We distribute the
combined program under GPL-3 through LGPL section 3. [NOTICE.md](NOTICE.md) records
where that code came from and what we changed. [CREDITS.md](CREDITS.md) lists every
contributor, reference and library. The JAR includes these files under
`META-INF/openggf/`, and release downloads include them next to the program.
