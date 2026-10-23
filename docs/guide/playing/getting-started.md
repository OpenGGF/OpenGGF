# Getting Started

This guide follows the **0.7 development branch**. Published 0.6 packages may have
different menus and fewer features; use the documentation included with your download
for that release.

## What You Need

- **For native release packages:** no separate Java install is required. The universal
  JAR and source-built JAR require Java 25 or later.
- **For source builds:** JDK 25. The executable JAR requires Java 25 or later. Download from [Adoptium](https://adoptium.net/) or
  your preferred distribution. Run `java -version` to check.
- **A graphics driver that supports an OpenGL 4.1 core context.** Support depends
  on both the GPU and its operating-system driver.
- **ROM files** for the games you want to play. The engine does not include any game data.
  You must supply your own legally obtained copies.

### Expected ROM Files

The engine is verified against these specific ROM revisions. Other revisions may produce
incorrect results.

| Game | Expected revision and hash |
|------|----------------------------|
| Sonic 1 | World, Revision 01; CRC32 `AFE05EEE`; SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| Sonic 2 | World, Revision 01; CRC32 `7B905383`; SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| Sonic 3&K | World lock-on combined ROM; CRC32 `63522553`; SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` |

Filenames do not matter. Put your images next to `config.yaml` (or in the folder named by
`roms.directory`) and the engine recognises each one by its size and cartridge header:

- a standalone Sonic 1, Sonic 2, Sonic 3 or Sonic & Knuckles image;
- a Sonic 3 & Knuckles lock-on dump (4 MiB), which also serves as Sonic 3 and Sonic & Knuckles;
- a Sonic & Knuckles + Sonic 1 or + Sonic 2 lock-on dump, which also serves the game it carries;
- a separate Sonic 3 image plus a separate Sonic & Knuckles image, which together boot
  Sonic 3 & Knuckles with no joining on disk.

The per-game keys (`roms.sonic1`, `roms.sonic2`, `roms.sonic3k`) remain as explicit
overrides through master-title **Settings** or `config.yaml`: leave them at their
defaults or blank to let the engine find your images, or set one to a file that must
exist. See the [image identity table](../../../CONFIGURATION.md#image-identity)
for all recognised layouts.

## Install and Run

### Option A: Download a Release

1. Download a package for your platform from [GitHub Releases](https://github.com/OpenGGF/OpenGGF/releases):
   - Windows: `OpenGGF-windows.zip`
   - macOS: `OpenGGF-macos.zip`
   - Linux: `OpenGGF-linux.tar.gz`
2. Extract it to a folder.
3. Place your ROM files next to the editable `config.yaml` included in the package.
4. Start OpenGGF:
   - Windows: double-click `OpenGGF.exe`, or run it from a terminal.
   - macOS: open `OpenGGF.app`.
   - Linux: run `./OpenGGF` from the extracted `OpenGGF` directory.
5. Configure any missing ROM paths in the package’s Settings or `config.yaml`.
   In development builds, **Locate ROM** and **Add your games** also locate images
   or scan a folder; filenames need not match the defaults.

Windows terminal example:
   ```
   .\OpenGGF.exe
   ```

Linux terminal example:
   ```
   cd OpenGGF
   ./OpenGGF
   ```

### Option B: Build from Source

1. Clone the repository:
   ```
   git clone --branch develop https://github.com/OpenGGF/OpenGGF.git
   cd OpenGGF
   ```
2. Build with Maven (packaging without running tests):
   ```
   tools/testing/install-hooks.sh
   python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package
   ```
   On Windows, use `tools/testing/install-hooks.ps1` and your Python 3 launcher.
   For development validation, follow the [testing guide](../contributing/testing.md).
3. Place your ROM files in the project root directory (next to `pom.xml`); any filename works.
4. Run:
   ```
   java -jar target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar
   ```
   On macOS, add `-XstartOnFirstThread` before `-jar`. The filename follows the
   version in `pom.xml`; use the artifact produced by your checkout.

## First Launch

The legal disclaimer appears first by default. After accepting it:

1. **Master title screen** -- A shelf of game cards with each game's original
   animated title logo. Left/right changes game and Enter (A) starts it. Under the card,
   **setup chips** show what Start will launch, such as `SONIC & TAILS`, `4:3` or
   `REWIND ON`; amber chips are set for that game only. The chips are status only;
   change them in **Game Setup**. Down goes to **Start**, **Game Setup** and
   **Recordings**. Up reaches the engine-wide tabs: **Mods**, **Settings**,
   **Extras** (mod-added modes such as the bundled **Time Attack** mod), **Advanced**
   and **Quit**. The footer shows only the keys that work on the focused row, as key
   caps or your controller's buttons. Back returns to the game card; on the card it
   asks whether to quit. Games without a ROM stay on the shelf and name the file to
   add. Opening Mods from Game Setup shows mods for that
   game, including shared mods. The header Mods tab shows the full catalog.
   The Mods list labels each installed mod with its target game: S1, S2, S3K,
   S1/S2/S3K for all three, or Standalone. When audio is enabled, navigation,
   confirmation and errors use short host-owned cues that do not depend on the
   selected game's ROM.
2. **Game title screen** -- The selected game's original title screen (e.g., the Sonic 2
   "PRESS START BUTTON" screen).
3. **Gameplay** -- The first zone of the selected game.

The hub remembers the game you last focused. **Start** opens the selected game's
title screen; use the paused card to resume a live session at its exact position. After a short idle, a ROM terrain preview pans inside the
card. Optional **Hub music** and its volume are under Settings → Sound.

If no ROMs are found, **Add your games** lets you locate a game or scan a folder
for all three supported games. A missing game's card also opens its ROM browser.
The browser has Home, Downloads and Last folder shortcuts and lists likely ROM
files first. Your files stay in their original locations.

## Quick Configuration

**Game Setup** (on the hub, or `Tab`) sets up one game: players, aspect ratio,
rewind, **Borrow from** (another game's sprites, spindash and sounds) and debug
tools. Rewind, debug tools and aspect ratio show **Default** and follow Settings
until you choose a value for that game.

**Settings** applies to every game. Categories are tabs across the top (Display,
Sound, Controls, Gameplay, Files, Capture); press Up from the first row to switch
tabs. Controls groups player bindings and warns about duplicate keys. Expert ROM
loading and rewind tuning live in Advanced settings; Gameplay keeps the Rewind
switch. Values that differ from the default have an amber dot. Each tab offers a
confirmed reset, which remains a draft until saved. In text editors, Down enters
the keypad and Up from its top row returns to the text field. Enter/A chooses a key
or accepts the focused field; you can also type normally. Number fields use a
numeric keypad, and Up in path fields opens a controller file browser. **Details**
(`F1` or controller north button) shows complete help, values and errors with manual
scrolling, including when a setting takes effect. Hold directions for repeated
navigation.

Settings, Game Setup and Mods all keep a draft: the footer counts unsaved changes,
and Back lists the old and new values before asking **Save**, **Discard** or **Keep editing**. Applied mod changes reload
the mods at the title. Controller prompts follow Xbox/PlayStation labels or physical
button positions. See [configuration](../../../CONFIGURATION.md#engine-settings-from-the-title)
for details.

The engine reads settings from `config.yaml` in the working directory. It stores only your overrides;
`config.yaml.example` lists the bundled reference settings. Missing keys use built-in
defaults. A few settings you might want to change immediately:

| Setting | What it does | Default |
|---------|-------------|---------|
| `roms.default` | Which game boots when the master title is bypassed (`"s1"`, `"s2"`, or `"s3k"`) | `"s2"` |
| `roms.directory` | Folder scanned for ROM images, identified by size and header rather than name | `"."` |
| `startup.masterTitleScreen` | Show game picker on launch | `true` |
| `display.windowAutosize` | Derive the window size from the aspect preset | `true` |
| `audio.enabled` | Enable or disable sound | `true` |
| `characters.sidekick` | CPU sidekick for direct launches; hub launches use each game’s Game Setup | `"tails"` |
| `debug.flags.editor` | Allow `Shift+Tab` to open the experimental editor overlay | `false` |

Key bindings can be written as names like `"SPACE"` and `"F9"` instead of raw numeric key codes.

For the full list, see [Configuration](configuration.md) or the
[Configuration Reference](../../../CONFIGURATION.md).

## What Next?

- [Controls](controls.md) -- Learn the keyboard layout
- [Game Status](game-status.md) -- See what works in each game
- [Troubleshooting](troubleshooting.md) -- If something went wrong
