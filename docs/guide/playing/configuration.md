# Configuration

The engine reads `config.yaml` from the working directory. This sparse file holds
only your overrides; missing keys use built-in defaults. Each run writes the bundled
reference to `config.yaml.example` without replacing your settings. A legacy
`config.json` is migrated automatically on first run. This page answers common setup
questions. For the full reference of every key, see the
[Configuration Reference](../../../CONFIGURATION.md).

---

## How do I change the interface language?

Open **Settings > Display > Language / Idioma**, choose a language by its own name,
then save your changes. **Automatic** follows your system language. Cancelling the
picker or Settings leaves the active language unchanged; a failed save can be retried.

The startup legal notice also shows a **Language / Idioma** shortcut, so a language
can be chosen before any ROM loads. Confirming there saves immediately. The preference
is `ui.language` in `config.yaml`; `auto`, `pt-BR` and `zh-Hant` are examples.
Original game text remains ROM-owned. A partial translation uses English for missing
or outdated messages.

## How do I change the window size?

You can resize or maximise the window normally. **Settings → Display → Scaling**
chooses integer scaling or a proportional fit. For a fixed startup size, set
`display.windowAutosize` to `false`, then set `debug.window.width` and
`debug.window.height` to the window dimensions you want in OS pixels.
For pixel-perfect 2x scaling of the native 320x224 resolution:

```yaml
display:
  windowAutosize: false
debug:
  window:
    width: 640
    height: 448
```

For 3x:

```yaml
display:
  windowAutosize: false
debug:
  window:
    width: 960
    height: 672
```

Logical pixel dimensions are derived from `display.aspect` and are not writable
configuration keys. All current aspect presets are 224 pixels high.

## How do I change fullscreen mode?

In **Settings > Display**, choose **Window mode**.
When saving a changed **Window mode**, choose **Keep changes** within 15 seconds
to save the settings. **Revert display**, Back or timeout restores the old mode
and leaves your changes available to edit. Unconfirmed modes are not saved.

## How do I skip the title screen?

To skip the master title screen (game picker) and boot directly into a game:

```yaml
startup:
  masterTitleScreen: false
roms:
  default: "s2"
```

To also skip the game's own title screen and go straight to gameplay:

```yaml
startup:
  masterTitleScreen: false
  titleScreen: false
roms:
  default: "s2"
```

## How do I start on a specific zone?

Enable the level select screen:

```yaml
debug:
  startup:
    levelSelectOnStartup: true
```

This opens the game's level select menu instead of starting from the first zone.

## How do I play as Tails?

For Sonic 2, open **Game Setup → Character → Tails**, with **Sidekick → None**.
For Sonic 3&K, select Tails on Data Select. Available characters depend on the
game, donor and any installed character patches.

For a direct Sonic 2 launch that bypasses the hub, set the main character:

```yaml
characters:
  main: "tails"
  sidekick: ""
```

To keep Sonic as the main character and add a CPU Tails follower instead, use
`main: "sonic"` and `sidekick: "tails"`. A comma-separated value such as
`"tails,knuckles"` adds several CPU sidekicks in follow order. In the engine,
**Settings > Advanced > Direct-launch sidekicks** edits that line as a list:
change each sidekick's character, add, move or remove sidekicks, then save Settings.
Hub launches use their per-game setup; these global character keys configure
direct launches.

The save screen (Data Select) offers its built-in teams, followed by any extra
teams in `characters.dataSelectExtraCombos`, written as
`main,sidekick;main,sidekick` (for example `"sonic,knuckles;knuckles,tails"`).
**Settings > Advanced > Extra character teams** edits them without typing: add a
team, choose its player and sidekicks, and reorder or remove teams.

Sonic 2 does not currently expose the ROM's native two-player/competition mode
(`Two_player_mode`), so Player 2 bindings do not create a human second player.
They remain available for the configured sidekick/manual-input paths. Native
S2 level debug placement (`Debug_placement_mode`, including ring/item placement)
also has no configuration or input route; the `D` shortcut is engine free-fly
debug movement only.

## How do I enable cross-game features?

Cross-game feature donation lets a donor game provide player sprites, spindash, and
SFX while you play a different base game. On the master title this is **Borrow from**
in each game's **Game Setup**; the YAML below sets it for direct launches. For example, playing Sonic 1 with Sonic 2's
sprites and spindash:

```yaml
crossGame:
  enabled: true
  source: "s2"
```

Both the base game ROM and the donor game ROM must be present.

When the donor is `s3k`, Sonic 1 and Sonic 2 can also use the donated S3K Data Select
save screen. The frontend comes from S3K, but slot validation, progression, restart
rules, and save writes still belong to the host game.

On that donated save screen, zone previews and emerald progress are host-owned metadata.
Emeralds keep the S3K save-card layout, but their colors are adapted from the host
game's emerald palette rather than reusing raw host palette slots directly.

## How do I change controls?

Key bindings accept GLFW key codes (integers), human-readable names, or a name
qualified by modifiers. The following formats all work:

- `81`
- `"81"`
- `"Q"`
- `"SPACE"`
- `"LEFT_SHIFT"`
- `"GLFW_KEY_F9"`
- `"SHIFT+O"`
- `"CTRL+SHIFT+O"`

The modifier names are `CTRL`/`CONTROL`, `SHIFT`, `ALT`/`OPTION`, and
`META`/`SUPER`/`CMD`/`COMMAND`/`WIN`, all case-insensitive. For the chord-aware
shortcuts listed below, matching is exact: a binding fires only with the modifiers
it names. Because `+` is the separator, bind the plus key as
`"EQUAL"` or `"KP_ADD"`.

Live viewport recording (`capture.toggleKey`, default `"SHIFT+O"`) and the four
form/emerald debug shortcuts (`superSonic`, `giveEmeralds`, `hyperForm`,
`giveSuperEmeralds` under `debug.keys`) honour chords. Other configured bindings
currently read the bare key: their modifiers are dropped, and some also require
that no modifier is held. The full per-binding
table is in [CONFIGURATION.md](../../../CONFIGURATION.md).

Invalid names log a warning and fall back to the default binding for that action.
Leaving a binding empty (`""`) is different: it switches that shortcut off, and no
default is substituted. If you want
the raw numeric values, find the code for your preferred key in the
[GLFW key reference](https://www.glfw.org/docs/latest/group__keys.html).

Common key codes:

| Key | Code | Key | Code |
|-----|------|-----|------|
| Arrow Up | 265 | Space | 32 |
| Arrow Down | 264 | Enter | 257 |
| Arrow Left | 263 | Tab | 258 |
| Arrow Right | 262 | Escape | 256 |
| A | 65 | Z | 90 |
| S | 83 | X | 88 |

Example: rebind Player 1 action A (jump) to the A key:

```yaml
input:
  player1:
    a: "A"
```

The engine models Mega Drive A, B, and C as separate actions. All three act as
jump buttons during platforming gameplay, but keyboard B/C are unbound in the
default config. Gamepads use position-based face buttons: west maps to Mega
Drive A, south maps to B, and east maps to C.

Controller input is controlled by `input.controller.enabled`,
`input.controller.deadzone`, `input.controller.player1`, and
`input.controller.player2`. Player assignments currently accept `"auto"` or
`"none"`.

See [Controls](controls.md) for the full list of bindable actions and
`CONFIGURATION.md` for current YAML key names.

## How do I enable the editor overlay?

```yaml
debug:
  flags:
    editor: true
```

With that enabled, press `Shift+Tab` during gameplay to enter the experimental
level editor. Project creation/open/import is also available from **Advanced →
Editor projects** at the master title. In a project, `Shift+Tab` switches between
the authored document and its isolated playtest; `F5` restarts that project from
its authored start. See the [level editor guide](../creating/level-editor.md).

## How do I enable live rewind?

Enable **Rewind** in Settings → Gameplay, or override it for one game in
**Game Setup**. The equivalent global setting is:

```yaml
rewind:
  liveEnabled: true
  liveKey: R
```

With that enabled, hold the configured live rewind key during normal level play to rewind the live
gameplay buffer. The on-screen live rewind HUD is hidden during ordinary play and
appears only while the key is held, showing the current rewind frame. Rewind history
resets at committed level and act transition boundaries.

Live rewind also reverses audio presentation from the recent mixed PCM history
and freezes graphical fade progression to the restored rewind snapshots. The
game resumes normal forward audio/fade presentation when the key is released.

Held rewind defaults to one rewind step per visual frame and stops immediately when
released. Experimental tape-coast rewind is available only when
`rewind.tapeCoastEnabled` is set to `true`. With coast enabled, the rewind
speed starts at `rewind.tapeCoastMinSteps` on press (values below 1.0 give
a slow-motion start), accelerates while held using
`rewind.tapeCoastAcceleration` up to `rewind.tapeCoastMaxSteps`,
and decays after release using `rewind.tapeCoastDeceleration`. Reverse audio
is resampled in lockstep with the current rewind speed, so fast rewind pitches up
and slow-motion rewind plays back lower and stretched in time.

## How do I mute audio?

```yaml
audio:
  enabled: false
```

## How do I make colors less bright?

In **Settings → Display**, choose **Colour expansion → Limited** to cap palette RGB
channels at 238. **Full** is the default and preserves the current output up to 255.
Apply saves the choice and refreshes resident normal and underwater palettes and the
backdrop colour without changing native palette bytes or gameplay. Headless gameplay
captures render with the same saved setting. You can also set it in `config.yaml`:

```yaml
display:
  colorExpansion: "LIMITED"
```

Expansion is separate from **Colour profile**. Limited applies a rounded 238/255
output gain after palette fades and the selected profile, including arbitrary
creator palette colours. With Raw RGB, the eight ROM channel levels become
0, 34, 68, 102, 136, 170, 204 and 238, matching GPGX normal digital output.
It does not emulate shadow/highlight or a 16–235 video range. Shader effects and
UI colours drawn outside the palette pipeline retain their own colour contracts.

The default colour profile is Raw RGB. You can independently select a darker
analogue-style presentation or a softer NTSC-style presentation:

```yaml
display:
  colorProfile: "MD_ANALOG"
```

Accepted values are `"RAW_RGB"`, `"MD_ANALOG"`, and `"NTSC_SOFT"`. During play, press
`V` to cycle profiles; the selected profile is saved to `config.yaml` and a short
confirmation appears in the bottom-left corner. The key can be rebound with
`display.colorProfileToggleKey`.

## How do I use a wider display aspect?

Choose **Settings → Display → Aspect ratio**, or override it for one game in
**Game Setup**. Native 4:3, 16:10 and 16:9 are supported presentation targets;
21:9 and 32:9 remain experimental. Support does not imply every route, boss,
character and donor combination has been verified at that width.

For a global 16:9 setting:

```yaml
display:
  aspect: "WIDE_16_9"
```

Available presets:

| Value | Pixel width | Notes |
|-------|-------------|-------|
| `"NATIVE_4_3"` | 320 | Default; native presentation |
| `"WIDE_16_10"` | 352 | Supported |
| `"WIDE_16_9"` | 400 | Primary widescreen target |
| `"ULTRA_21_9"` | 528 | Experimental; best-effort coverage |
| `"SUPER_32_9"` | 800 | Experimental; exploratory coverage |

By default (`display.windowAutosize: true`), the OS window is automatically sized to
twice the preset pixel width (e.g. `WIDE_16_9` → 800×448). To keep a custom window size,
set `display.windowAutosize` to `false` and configure `debug.window.width` /
`debug.window.height` manually.

## How do I switch between NTSC and PAL?

```yaml
audio:
  region: "PAL"
```

PAL selects 50 Hz simulation and audio service; NTSC selects 60 Hz.
`display.fps` limits window rendering independently and applies immediately.
`display.pacing: "AUTO"` (the default) locks those services to a display that refreshes
at a whole multiple of the region rate; `"EXACT"` keeps them on the system clock.
The logical display height stays 224 pixels.
This setting does not select a different ROM revision or establish complete PAL parity.

## How do I use different ROM filenames?

Ordinary filenames need no configuration: the engine scans `roms.directory`
(`"."` by default) and identifies images by size and header. Use explicit paths
when you want to select particular images:

```yaml
roms:
  sonic1: "my-sonic1.bin"
  sonic2: "my-sonic2.bin"
  sonic3k: "my-s3k.bin"
```

Paths are relative to the working directory, or may be absolute. An explicit
non-default path that does not exist is an error; blank/default hints allow
discovery. The hub also offers **Locate ROM** and **Scan a folder**. See the
[image identity table](../../../CONFIGURATION.md#image-identity) for verified dumps
and separate Sonic 3 + Sonic & Knuckles support.

## How do I skip S3K intro cutscenes?

```yaml
debug:
  startup:
    s3kSkipIntros: true
```

This skips sequences like the AIZ biplane intro and boots straight into gameplay.
