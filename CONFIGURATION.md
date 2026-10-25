# Configuration Reference

This reference follows the **0.7 development branch**; published 0.6 packages may
have fewer settings and different menus.

Settings live in `config.yaml` in the working directory, and that file
holds **only the settings you have changed**. Every other setting reads its built-in
default, so a default that changes in a later build applies to every install that never
touched the key. The bundled [reference template](src/main/resources/config.yaml) documents settings with
their defaults and is written to **`config.yaml.example`** alongside your file on every run;
copy a line from it into `config.yaml` to change that setting. Your own `config.yaml` is
never overwritten by the example.

The file opens with `configFormat: 2`, which marks this sparse format. A `config.yaml`
written by an older build (one that copied every default into it) is converted once on
first load: keys still at their default are dropped, keys you changed are kept, and the
marker is written. One default changed before this format existed —
`gameplay.loadTimeSimulation` moved from `NONE` to `FAST` — so that value is treated as
the former default during the conversion; set `NONE` again afterwards if you want it and
it stays. On first run, a legacy `config.json` is migrated to `config.yaml` and backed up
to `config.json.bak`. Keys are grouped into nested YAML sections rather than flat enum
names.

For development configurations where keeping the complete editable setting list is more
useful, set `config.preserveExplicitDefaults: true` before starting the engine. The
one-time sparse conversion will then retain every explicitly listed recognized setting.
The flag defaults to `false`, preserving the release behavior described above.

**Changing a default** (maintainers): change the `putDefault` line in
`SonicConfigurationService`, the value in `src/main/resources/config.yaml`, and the row in
this file. Nothing else: no migration, no version bump. `TestSparseUserConfig` fails if the
bundled template and the registered default disagree.

Key bindings accept either **GLFW key codes** (integers) or human-readable key names such as
`"SPACE"`, `"Q"`, and `"GLFW_KEY_F9"`. See the
[GLFW key token reference](https://www.glfw.org/docs/latest/group__keys.html) for the full list.
Common values are shown in the tables below.

---

## Sections

The `config.yaml` is organized into the following top-level sections:

**Normal sections** (relevant to all users):

| Section | Contents |
|---------|----------|
| `display` | Aspect preset, window autosize, display shader library, deadzone mode, color profile and expansion, FPS |
| `gameplay` | Normal-play hardware load-time simulation |
| `config` | Configuration-file persistence behavior |
| `input` | `player1` / `player2` key bindings, `pause` key and controller settings |
| `audio` | Enabled flag, hub music, region, FM core, DAC and FM6 settings |
| `characters` | Main character, sidekick, data select combos |
| `roms` | ROM filenames for S1, S2, S3K; default game selection |
| `capture` | Trace and live video output, codecs, encoder settings and recording shortcut |
| `startup` | Title screen, master title, legal disclaimer flags |
| `hub` | Last focused game and ignored legacy continue values |
| `mods` | Mod-zone title-card behavior |
| `rewind` | Live rewind enable/key, tape-coast parameters, audio history settings |
| `crossGame` | Cross-game feature donation enable and source |
| `launch` | Per-game master-title launch profiles |
| `discord` | Discord Rich Presence enable, show timer, show zone |

**`debug:` block** (developer/debug tooling — safe to ignore for normal play):

| Sub-section | Contents |
|-------------|----------|
| `debug.flags` | Debug subsystem, editor enable, collision view overlay |
| `debug.keys` | All debug/developer keyboard shortcuts |
| `debug.startup` | Level select on startup, S3K skip intros |
| `debug.playback` | BK2 movie path and playback control keys (unbound by default) |
| `debug.recording` | Gameplay input recording shortcut |
| `debug.traceRewind` | Key held during Trace Test Mode to rewind engine state |
| `debug.rewind` | Live-rewind determinism audit |
| `debug.traceRender` | Trace Test Mode / capture visibility flags |
| `debug.testMode` | Test mode enable flag and trace catalog directory |
| `debug.crossGame` | Data-select image regeneration overrides and coord-log key |
| `debug.window` | **DEPRECATED** manual width/height/scale — use `display.aspect` + `display.windowAutosize` instead |

> **Note:** `SCREEN_WIDTH_PIXELS` and `SCREEN_HEIGHT_PIXELS` are **derived** values computed from the
> `display.aspect` preset. They are never stored in `config.yaml`. `debug.window.width` /
> `debug.window.height` / `debug.window.scale` are deprecated; widescreen is driven by
> `display.aspect` profiles.

---

## Display

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `SCREEN_WIDTH_PIXELS` | *(derived)* | int | `320` | Logical pixel width — the Mega Drive native horizontal resolution. Derived from `display.aspect`; never stored. |
| `SCREEN_HEIGHT_PIXELS` | *(derived)* | int | `224` | Logical pixel height. All current aspect presets use 224, including PAL timing; derived and never stored. |
| `SCREEN_WIDTH` | `display` / `debug.window.width` | int | `640` | Actual window width in OS pixels. Derived from aspect preset when `display.windowAutosize=true`. |
| `SCREEN_HEIGHT` | `display` / `debug.window.height` | int | `448` | Actual window height in OS pixels. |
| `SCALE` | `debug.window.scale` | double | `1.0` | **DEPRECATED** additional rendering scale factor. |
| `FPS` | `display.fps` | int | `60` | Target frames per second under NTSC; affects game speed. PAL region forces 50 regardless of this setting. |
| `LOAD_TIME_SIMULATION` | `gameplay.loadTimeSimulation` | enum | `FAST` | Normal-play ROM-load timing: `NONE` completes as soon as production preparation allows; `PROFILED` uses the generator-owned measured profile data; `FAST` (default) uses the hand-tuned copy of that data (`load-time-profiles/s3k-fast-v1.json`, which also carries the S3K title-screen Sonic frame decodes taken from the original hardware capture), and warns then behaves as `NONE` for a game without a FAST manifest; `REALISTIC` is a retained reserved alias that warns when the profile is resolved and returns `PROFILED`. S1/S2 resolve through their default module factory to the supplied immediate profile; their ROM-derived PLC and dynamic-art/DPLC lifecycles remain game-owned and are not retimed by this setting. Trace replay uses its recorded hardware-timing policy and does not consume this setting; queue-state trace diagnostics are comparison-only and never alter this configuration. |
| `CONFIG_PRESERVE_EXPLICIT_DEFAULTS` | `config.preserveExplicitDefaults` | bool | `false` | Developer override: retain explicitly listed recognized settings, including values equal to defaults, when converting an older full configuration to sparse format. |
| `DISPLAY_COLOR_PROFILE` | `display.colorProfile` | string | `"RAW_RGB"` | Palette presentation profile. `"RAW_RGB"` keeps the current direct 8-bit expansion, `"MD_ANALOG"` applies a darker Mega Drive-style analog ramp, and `"NTSC_SOFT"` applies the analog ramp plus mild desaturation. |
| `DISPLAY_COLOR_EXPANSION` | `display.colorExpansion` | string | `"FULL"` | Independent palette RGB output range, selectable as Full or Limited in Settings → Display. Full preserves current output (maximum 255); Limited applies rounded 238/255 gain after fades and the selected profile, including arbitrary creator palette RGB. The saved choice applies to cached normal and underwater palettes, the palette colour-0 backdrop and clear colour, and headless gameplay captures (applied at each capture boot through the same owner as the window). RAW_RGB + LIMITED matches GPGX normal digital ROM channel levels; this is not video 16–235 range or shadow/highlight emulation. |
| `DISPLAY_COLOR_PROFILE_TOGGLE_KEY` | `display.colorProfileToggleKey` | key | `V` | Runtime key used to cycle display color profiles. The selected profile is saved to `config.yaml` and shown briefly in the bottom-left corner. |
| `DISPLAY_ASPECT` | `display.aspect` | string | `"NATIVE_4_3"` | Display aspect preset. Controls the logical pixel width used by the renderer. Accepted values: `"NATIVE_4_3"` (320 px, exact native behavior), `"WIDE_16_10"` (352 px, supported), `"WIDE_16_9"` (400 px, primary supported widescreen target), `"ULTRA_21_9"` (528 px, best-effort smoke tier), and `"SUPER_32_9"` (800 px, exploratory). Wider presentation does not widen ROM world boundaries or trace-comparison authority. A game module (such as a mod) may pin a preset for its session; the setting itself is unchanged and returns at the master title. |
| `DISPLAY_WINDOW_MODE` | `display.windowMode` | enum | `"WINDOWED"` | `"WINDOWED"` allows normal resizing and maximising; `"BORDERLESS"` covers the current monitor without window borders; `"FULLSCREEN"` uses exclusive fullscreen at the monitor's current resolution. Saving a changed mode previews it for 15 seconds; Keep changes persists it, while Revert, Back or timeout restores the previous mode. Returning to windowed mode restores the previous window placement. |
| `DISPLAY_SCALING` | `display.scaling` | enum | `"INTEGER"` | `"INTEGER"` uses the largest whole-number scale that fits, preserving equal-sized square pixels. `"FIT"` uses the largest proportional fit, allowing fractional scaling. Both preserve the game image's proportions and centre it with bars where necessary. Changes apply when saved. |
| `DISPLAY_WINDOW_AUTOSIZE` | `display.windowAutosize` | bool | `true` | When `true` and a widescreen preset is active, the OS window is derived from the preset at 2x baseline (e.g. `WIDE_16_9` → 800×448). When a launch changes the aspect (a launch profile, or a module's required aspect such as a mod laid out for 16:9), a windowed, non-maximised window is refitted: it keeps its whole-number height scale and takes the new aspect's width, within the monitor, rather than letterboxing. When `false`, `SCREEN_WIDTH`/`SCREEN_HEIGHT` are used verbatim so a custom window size is preserved, and launches never resize the window. At startup it has no effect when `DISPLAY_ASPECT` is `"NATIVE_4_3"`. |
| `DISPLAY_SHADER_LIBRARY_ROOT` | `display.shaderLibraryRoot` | string | `"shaders"` | Root directory scanned for user display shaders, resolved relative to the working directory. |
| `DISPLAY_SHADER_SELECTION` | `display.shaderSelection` | string | `"OFF"` | Last selected display shader. Use `"OFF"` or a root-relative forward-slash path under `DISPLAY_SHADER_LIBRARY_ROOT`. |
| `DISPLAY_SHADER_NEXT_KEY` | `display.shaderNextKey` | key | `RIGHT_BRACKET` | Runtime key used to advance to the next display shader. |
| `DISPLAY_SHADER_PREVIOUS_KEY` | `display.shaderPreviousKey` | key | `LEFT_BRACKET` | Runtime key used to move to the previous display shader. |
| `DISPLAY_SHADER_PICKER_KEY` | `display.shaderPickerKey` | key | `BACKSLASH` | Runtime key used to open the searchable display shader picker. |
| `DISPLAY_SHADER_DEFAULT_PHASE` | `display.shaderDefaultPhase` | enum | `"PRESENTATION"` | Fallback render phase for standalone display shaders. Accepted values: `"SCENE"`, `"PRESENTATION"`, `"FINAL"`. |
| `WIDESCREEN_DEADZONE_MODE` | `display.deadzoneMode` | string | `"PROPORTIONAL"` | Camera horizontal deadzone behaviour on wide screens: `"CENTER_SCALED"` keeps the native 16px deadzone band; `"PROPORTIONAL"` scales the band width with the screen width. **EXPERIMENTAL** — takes effect only when a widescreen preset is active. |
| `DISPLAY_HUD_ANCHOR` | `display.hudAnchor` | string | `"DEFAULT"` | Horizontal position of the score/time/rings/lives HUD on widescreen profiles: `"DEFAULT"` keeps the centered native-frame position; `"LEFT_EDGE"` anchors it to the screen's left edge. Both settings produce the same position at native 4:3. |
| `DISPLAY_HUD_TIME_HUNDREDTHS` | `display.hudTimeHundredths` | bool | `false` | Show hundredths of a second in the HUD's TIME (`0:00.00`), as Sonic CD does, in each game's own HUD digits (the point is the lower dot of the game's colon). The original three games show whole seconds. Read when a level starts. Mods can also ask for it during a run (`RunHandle.setHudTimeHundredths`). |

### Display shader library

Display shaders are user-supplied post-processing shaders loaded from the root-level
`shaders/` directory. This is separate from `src/main/resources/shaders`, which contains
engine-owned shaders required for normal rendering. The root `shaders/` directory is
gitignored so local shader packs and third-party shader licenses stay outside the repo
unless they are reviewed separately.

Recommended layout:

```text
shaders/
  Custom/
    warm-crt.glsl
  BizHawk/
    BizScanlines.cgp
    BizScanlines.glsl
  libretro-glsl/
    crt/
    scanlines/
    ...
    .openggf-libretro-glsl.properties
```

The engine scans `.glsl`, `.cgp`, and `.glslp` files at runtime and always includes
`Off`. Use `]` and `[` to cycle quickly, or press `BACKSLASH` to open the searchable
picker for large libraries. The picker filters by root-relative path and inferred
category, so typing `crt` narrows entries such as `libretro-glsl/crt/...`.

The optional libretro GLSL pack installer is available from the shader picker: open the
picker with `BACKSLASH`, then press `F5` to install or update the pack. The app downloads
the upstream zip archive from GitHub, extracts it into `shaders/libretro-glsl/`, strips the
archive's top-level folder, stores update metadata in
`shaders/libretro-glsl/.openggf-libretro-glsl.properties`, and rescans the shader library
when the install/update finishes. That folder is owned by the installer; put personal
shaders in a sibling folder such as `shaders/Custom/`.

Compatibility is intentionally bounded. Fragment-only shaders can sample the current
scene through declared samplers named `s_p`, `SceneTexture`, or `Texture`. RetroArch and
BizHawk-style uniforms are populated by location when declared: `IN.video_size`,
`IN.texture_size`, `IN.output_size`, `InputSize`, `TextureSize`, `OutputSize`,
`FrameCount`, `FrameDirection`, and `MVPMatrix`. Combined shaders may declare
`VertexCoord`, `TexCoord`, and `COLOR` attributes; the engine binds them to fixed quad
locations. The engine does not inject uniform declarations.

HLSL/Cg-only presets can be discovered but fail on selection unless a loadable GLSL
sibling exists. Unsupported preset inheritance, external LUT textures, previous-frame
history, and malformed shaders fail safely: the selection is rejected, the shader is
remembered as failed for the current process, rendering continues, and the user can return
to `Off`.

Display shaders affect presentation only. F12 screenshots are captured after the display
shader composite, so screenshots include the selected shader. Trace replay/capture uses a
separate render path that stops before display shader application, so trace artifacts stay
shader-free; if trace capture is ever refactored to call the main `Engine.display()` path,
display shader application must be gated off for trace capture.

---

## ROM Files

ROM-backed `baseGame: any` mod scenes use this configured catalogue to combine
available games. Sitar Hero's optional launch script creates an isolated config
for an explicit ROM subset; it packages no ROM data. Its keyboard/gamepad remaps,
lefty mode and input/display calibration belong to owner-scoped mod settings,
rather than global gameplay bindings. Raw standard
gamepads remain available to physical-input scenes when the Genesis mapper is
disabled. Sitar Hero keeps separate player control/calibration files
(`settings.txt`, `settings-p2.txt`), bounded story progress (`career.txt`) and
independent score records (`profile.txt`),
and the explicitly entered peer address (`online-address.txt`) in its owner-scoped
scene storage. The settings menu selects the player and melodic/bongo profile;
lefty flip, reduced flashes and highway speed are also player-specific. Network
host/join is opened only by the Direct-connect menu, using an explicit IP or
localhost and port. See [Sitar Hero controls](examples/sitar-hero/README.md#controls).

Paths are relative to the working directory (where the JAR is launched).

The engine builds a catalogue of every image it can see: the files named by the
three per-game keys plus every `*.gen`, `*.bin` or `*.md` file in
`roms.directory`. Each image is identified by its size and the cartridge header
titles at `0` and `0x200000`, never by its filename, so nothing needs renaming.
A game boots when any combination of your images contains its bytes: a lock-on
dump serves the games it contains, and Sonic 3 & Knuckles can be assembled from a
separate Sonic 3 image and a separate Sonic & Knuckles image. The assembled view
lives in memory only; the engine never writes, joins, patches or downloads a file.

The per-game keys are explicit overrides. A key that names an existing file is
served as configured (as the window that contains the game, or whole when the
layout is not recognised). A key that names a missing file fails closed with that
value in the error; the engine never silently substitutes another image. The
built-in default names (`s1.gen`, `s2.gen`, `s3k.gen`) are only hints: when the
hinted file is absent, or the key is blank, the directory scan applies. Among
scanned images an image whose hash matches the identity table below wins, then
the first image in name order.
An image whose header matches but whose hash is unknown is still used and is
logged as unverified; a hash mismatch never blocks boot. Hashing happens only
when an image is opened or a tie must be broken, so a folder of unrelated images
costs a few header reads at startup.

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `DEFAULT_ROM` | `roms.default` | string | `"s2"` | Which game to boot: `"s1"`, `"s2"`, or `"s3k"`. Selects the corresponding ROM key below. |
| `SONIC_1_ROM` | `roms.sonic1` | string | `"s1.gen"` | Explicit image for Sonic 1. May name any image that contains it, including an S&K + Sonic 1 lock-on dump. Blank, or the default name when that file is absent, means the catalogue scan applies; any other missing file is an error. |
| `SONIC_2_ROM` | `roms.sonic2` | string | `"s2.gen"` | Explicit image for Sonic 2. May name any image that contains it, including an S&K + Sonic 2 lock-on dump. Blank, or the default name when that file is absent, means the catalogue scan applies; any other missing file is an error. |
| `SONIC_3K_ROM` | `roms.sonic3k` | string | `"s3k.gen"` | Explicit image for Sonic 3 & Knuckles: a 4 MiB lock-on dump. Blank, or the default name when that file is absent, means the catalogue scan applies (including the Sonic 3 + Sonic & Knuckles composite); any other missing file is an error. |
| `ROMS_DIRECTORY` | `roms.directory` | string | `"."` | Directory scanned for images (`*.gen`, `*.bin`, `*.md`), relative to the working directory. |
| `ROMS_PREFER_COMPOSITE` | `roms.preferComposite` | bool | `false` | When true, a Sonic 3 & Knuckles assembled from separate Sonic 3 and Sonic & Knuckles images is preferred over a single lock-on dump. |

### Image identity

Images are recognised by exact size and header; the hashes below mark the dumps
the engine is verified against. Lock-on dumps are verified per half, so a lock-on
dump whose halves match the standalone rows verifies without a row of its own.

| Image | Size | Layout | Verified dump |
|-------|------|--------|---------------|
| Sonic 1 | 524,288 bytes (512 KiB) | Sonic 1 at `0` | World REV01: CRC32 `AFE05EEE`, SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| Sonic 2 | 1,048,576 bytes (1 MiB) | Sonic 2 at `0` | World REV01: CRC32 `7B905383`, SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| Sonic 3 | 2,097,152 bytes (2 MiB) | Sonic 3 at `0` | CRC32 `9BC192CE`, SHA-1 `75E9C4705259D84112B3E697A6C00A0813D47D71` |
| Sonic & Knuckles | 2,097,152 bytes (2 MiB) | S&K at `0` | CRC32 `0658F691`, SHA-1 `88D6499D874DCB5721FF58D76FE1B9AF811192E3` |
| Sonic 3 & Knuckles (lock-on) | 4,194,304 bytes (4 MiB) | S&K at `0`, Sonic 3 at `0x200000` | CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` |
| S&K + Sonic 1 (lock-on) | 2,621,440 bytes (2.5 MiB) | S&K at `0`, Sonic 1 at `0x200000` | Verified per half against the S&K and Sonic 1 rows |
| S&K + Sonic 2 (lock-on) | 3,407,872 bytes (3.25 MiB) | S&K at `0`, Sonic 2 at `0x200000`, 256 KiB Knuckles in Sonic 2 chip at `0x300000` | MD5 `3E5E4B18D035775B916A06F2B3DC5031`, SHA-1 `6CD0537A3AEE0E012BB86D5837DDFF9342595004`, CRC32 `2AC1E7C6` |

The S&K + Sonic 2 dump is the only source of the Knuckles in Sonic 2 chip; the
engine identifies it by the size and hashes above and has no other way to obtain
it. The catalogue only exposes the dump's windows to the engine; Knuckles in Sonic 2
playability is tracked by the KiS2 patch design, not by this table.

---

## Startup Flow

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `SHOW_LEGAL_DISCLAIMER_ON_STARTUP` | `startup.legalDisclaimer` | bool | `true` | Show the legal disclaimer screen on engine startup before the master title screen. White text on black, 5-second readability gate, any-key dismiss, fade-in/out transitions. Set `false` for headless tests, trace replay sessions, or CI runs that should not have to drive past this screen. |
| `MASTER_TITLE_SCREEN_ON_STARTUP` | `startup.masterTitleScreen` | bool | `true` | Show the master title / game-selection screen on launch. When `false`, boots directly into the game set by `DEFAULT_ROM`. |
| `TITLE_SCREEN_ON_STARTUP` | `startup.titleScreen` | bool | `true` | Show the game-specific title screen (e.g. Sonic 2 title screen) before gameplay. Ignored when `MASTER_TITLE_SCREEN_ON_STARTUP` is true and game selection is pending. |
| `SKIP_MOD_ZONE_TITLE_CARDS` | `mods.skipModZoneTitleCards` | bool | `true` | Skip Sonic 2 title cards for additive mod zones until mod-supplied title-card art is supported. Stock cards still use the active zone registry name. |
| `LEVEL_SELECT_ON_STARTUP` | `debug.startup.levelSelectOnStartup` | bool | `false` | Jump straight to the level select screen instead of the title screen. Useful for development. Ignored (with the level-select key) when the active game module hides the level select. |
| `S3K_SKIP_INTROS` | `debug.startup.s3kSkipIntros` | bool | `false` | (S3K only) Skip zone intro sequences such as the AIZ biplane cutscene and boot straight into playable gameplay. |

---

## Cross-Game Donation

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `CROSS_GAME_FEATURES_ENABLED` | `crossGame.enabled` | bool | `false` | Enable cross-game feature donation. When `false`, each game uses only its own native frontend and gameplay assets. |
| `CROSS_GAME_SOURCE` | `crossGame.source` | string | `"s2"` | Donor game for cross-game features. Currently supports `"s2"` and `"s3k"`. |
| `CROSS_GAME_S1_DATA_SELECT_IMAGE_GEN_OVERRIDE` | `debug.crossGame.s1DataSelectImageGenOverride` | bool | `false` | Force regeneration of the runtime Sonic 1 donated Data Select screenshot cache on the next eligible boot. |
| `CROSS_GAME_S2_DATA_SELECT_IMAGE_GEN_OVERRIDE` | `debug.crossGame.s2DataSelectImageGenOverride` | bool | `false` | Force regeneration of the runtime Sonic 2 donated Data Select screenshot cache on the next eligible boot. |

### Cross-Game Debug Key

| Key | YAML path | Default | Key Name | Description |
|-----|-----------|---------|----------|-------------|
| `CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY` | `debug.crossGame.s1DataSelectImageCoordLogKey` | `39` | Apostrophe | While playing Sonic 1, log the current camera as a preview override point for donated Data Select screenshot tuning. |

---

## Launch Profiles

The master title stores per-game launch defaults under `launch.s1`, `launch.s2`,
and `launch.s3k`, edited from **Game Setup** on the hub.

### The hub

The hub is a shelf of game cards. The selected game's card is in the centre with
its original ROM-backed animated title logo; neighbouring games recede to the
sides, and the shelf slides when the selection changes. Logo textures are drawn
directly into the window viewport, so a larger window recovers source detail. The
default logical resolution remains 320x224; wider viewports show more of the shelf.
Games without a ROM stay on the shelf with a **ROM MISSING** card and a
**Locate ROM** action. Images are identified by their contents, not filenames.

Focus moves between three rows with Up/Down:

1. **Header tabs**, engine-wide: **Mods** (with the enabled-mod count),
   **Settings**, **Extras** (only when mods add title entries), **Advanced**, **Quit**.
2. **The game card**. Left/Right changes game (wrapping), `Enter`/A starts it.
3. **Actions** for the selected game: **Start**, **Game Setup**, **Recordings**.
   Standalone mod games offer Start only. A missing stock ROM replaces Start with
   **Locate ROM**, opening a file picker. The selected image is identified by its
   contents before its path is saved; any filename is accepted. Cancelling leaves
   the selected game and saved preferences unchanged.

The quiet badges below the game name report the saved setup, such as
`SONIC & TAILS`, `4:3`, `REWIND ON` or `2 MODS`; they are not controls.
Amber means a value is set for this game only. Long configurations end with
`+N options`; Game Setup shows the full configuration. Pagination dots are also
status only; game selection stays on the carousel. With Extras present at narrow
widths, Advanced is labelled **More** and Mods omits its count to preserve branding.

`Esc`/B returns to the card from any other row; on the card it opens the quit
confirmation (**Return to menu** is selected by default). A line under the
buttons describes the focused action or points to Game Setup. The footer lists only the controls that work on the current row,
as key caps (or controller buttons) with what each does; it never lists Up/Down.
`Tab` (controller Back/Select) opens Game Setup and `M` opens Mods as optional
shortcuts. Error pages wait for `Enter`/A or `Esc`/B.

The mouse works throughout the hub: hovering focuses a tab or button and
clicking runs it, clicking a side card selects that game, clicking the centre card
starts it, the wheel changes game and the right button goes back.

### Game Setup

Game Setup edits one game's launch profile. Its header reads **THIS GAME ONLY**.
Rows are grouped as **Players** (Character, Sidekick), **Screen** (Aspect ratio),
and **Extras** (Rewind, Borrow from, Debug tools, Mods). Up/Down moves between
rows; Left/Right or `Enter` changes the value; `Backspace` (controller north
button) returns the focused row to its default. The description box explains the
focused row and, for switches, shows both the all-games value and this game's
value.

Rewind, Debug tools and Aspect ratio follow the all-games Settings value until a
game overrides them. Those rows show **Use global (Off)**, with the current all-games
value in brackets; choosing On or Off overrides it for this game only and marks
the row amber. **Borrow from** (cross-game donation) and the Players rows are
per-game only. When Borrow from is Sonic 3&K, the donated save screen picks the
team, so the Players rows show **Chosen on save screen**. The Mods row lists the
enabled mods that change this game; `Enter` opens the mod manager, and Game Setup
resumes when it closes.

Edits form a draft. A **Save changes** footer button appears while edits are unsaved;
reach it after Mods with Down, before the first row with Up, or click it to save and close. `Esc`/B with unsaved
changes asks **Save**, **Discard** or **Keep editing**; without changes it simply
returns. `Tab` (controller Back/Select) saves and closes. Failed saves keep the
draft open for another try. 21:9 and 32:9 aspect ratios are marked EXPERIMENTAL.

### Engine settings from the title

**Settings** holds persisted engine preferences for every game; its header reads
**ALL GAMES**. Categories are tabs across the top: **Display**, **Sound**,
**Controls**, **Gameplay**, **Files** and **Capture**. Up from the first row
focuses the tab strip, where Left/Right switches tabs; Down returns to the rows.
Controls first focuses its Player 1 / Player 2 / Controllers / Shortcuts group
strip; Up again reaches the category tabs.
On a row, Left/Right changes booleans, enums and numbers, and `Enter`/A opens a
value picker, text editor or key-binding menu. Eight rows show at a time, with a
scrollbar for longer pages. Values that differ from the engine default are amber,
and a dot marks tabs that contain them. **Controls** holds the player bindings and
every non-debug shortcut key (display, rewind and capture shortcuts included).
**Gameplay** holds Rewind and Debug tools, which games follow unless their Game
Setup overrides them. Debug, playback, Discord, direct-launch characters and
cross-game keys live in **Advanced > Advanced settings**, alongside the shader
library folder, shader rendering phase, game speed, debug viewer scale, ROM
loading policy and Tape coast tuning. Rewind remains one Gameplay row. Display
keeps everyday screen, shader selection and colour options. Its **Window mode**
and **Scaling** rows apply from the menu, including from a paused game. Saving a
changed window mode opens a 15-second confirmation: **Keep changes** saves the
whole draft; **Revert display**, Back or timeout restores the old mode and keeps
the draft editable. The preview is never written to disk before confirmation.
Scaling applies when saved. Maximising a
window fills the desktop work area; the game image remains proportional, with
square pixels and centred bars where needed. Choose **Integer** scaling for
whole-number pixel multiples or **Fit** for the largest proportional image.

Windows smaller than the native picture downscale to keep the whole picture visible,
even with integer scaling selected. Fullscreen uses the current desktop resolution
and refresh rate. On Wayland, the compositor manages fullscreen and monitor placement;
borderless uses compositor fullscreen without automatic minimisation on focus loss.

Text editing supports ordinary keyboard typing and an onscreen keyboard with all
printable ASCII, case/symbol pages, cursor movement, deletion, and default
restoration. Down moves from the text field onto the keypad; Up from its top row
returns to the field. Arrows and D-pad move the same focus. Enter/A chooses the
highlighted key on the keypad or accepts the value in the text field; the keypad
also has an **OK** key. Typing directly returns focus to the field. Switching input
devices changes prompts without moving focus. This editor is shared with recording
target frames, join addresses, and lobby chat. Key bindings also offer physical
key/chord capture. Hold modifiers with a key for a chord; tap and release a
modifier to bind that key alone.

Each tab has a confirmed **Reset tab to defaults** action; Controls offers
**Reset all bindings**. Resets remain draft edits until saved. Changed rows carry
an amber dot. Save prompts list the old and new values, including per-game
inheritance changes, so you can review what will be saved.

Edits form a draft, counted in the footer. A **Save changes** header button appears
when dirty; click it or navigate Up past the category tabs (or Down past the last
row) and confirm to save and close. `Esc`/B with unsaved changes asks
**Save**, **Discard** or **Keep editing**, the same prompt as Game Setup. Save
writes the draft atomically; save errors retain the draft for retry.

**Details** (`F1` or the controller's north button) opens manually scrollable help,
the complete value/error, and when the setting takes effect: as soon as you save,
when you next start a game, or after a restart.

Settings that only take effect after you close and reopen OpenGGF carry an amber
**RESTART** tag on their row, and their help reads *Needs a restart: close and reopen
OpenGGF.* These are the frame rate, display shaders and their keys, the colour
profile, the rewind VHS effect, Discord, the start-up screens and default ROM, test
mode, window size, and turning sound off. After you save one with a new value, the
Settings and game-shelf footers show **RESTART NEEDED** until you restart. The
Settings tab row names the waiting changes (or counts them when the names do not
fit). Saving a setting back to the value OpenGGF started with clears the notice.
Everything else applies as soon as you save or when you next start a game; Details
says which.
Unchanged ROM previews are retained after saving. Current launch/session overrides
remain authoritative until the next launch. YAML remains an optional editing route.

Number fields use a numeric keypad; join addresses prioritize address punctuation.
For path fields, Up from the text field opens a controller browser. The browser
loads folders asynchronously and can be cancelled; **Use this folder** selects a
directory, while selecting a file returns its path. Type directly to enter a new
path. Unsupported font characters appear as `[U+...]` identities while stored text
remains unchanged. Details shows the full representation without truncation.

Hold a direction to repeat after a short delay; confirmation and Back never repeat.
Prompts use Xbox A/B/Y, PlayStation Cross/Circle/Triangle, or physical button
positions South/East/North for unrecognized controllers.

Mods also keeps a draft: **Apply** saves without leaving, while Back asks before
discarding unsaved changes. Applied mod changes still require a restart. Trace and
recording catalogs load in the background with a visible Cancel action.

**Advanced** offers **Trace replays** (the trace catalog, without enabling test
mode), **Advanced settings** and **Controls / help** in compact rows. Help is also
available directly from the hub footer (`F1` or the controller north button). Each enabled mod may add one
entry of its own under the **Extras** tab, including when only one mod contributes
an entry; existing function-key shortcuts remain optional accelerators. Global
display and capture shortcuts remain available while the game card is focused,
but yield to the other hub rows and child pages so typing or rebinding a key
cannot change unrelated settings. Playback shortcuts do not run on the master title.

Recordings has a separate options page for the target frame, pause-on-desync,
fast-forward, playback, and full recording details. Time Attack is the bundled
Time Attack mod's entry; see [Time Attack (bundled mod)](#time-attack-bundled-mod). Mods exposes **Details**, **Order**, **Notices**, and
**Apply**, with paginated findings and confirmations. Trace replay lists,
loading/failure pages, standalone New Game/Continue, and help share the same
native typography and keyboard/controller navigation.

Profiles are persistent defaults for future manual launches, but applying a profile is
session-only. A launch can temporarily override live rewind, cross-game donation, debug
tools, display aspect, main character, and sidekick without writing those values into the
global gameplay keys (`rewind.liveEnabled`, `crossGame.*`, `debug.flags.debugView`,
`display.aspect`, or `characters.*`). `rewind`, `debugTools` and `aspect` set to
`"global"` apply no override, so the game follows the all-games value; `"on"`/`"off"`
(or a pinned aspect) override it for that game. Configs written before these
switches had a Default value are migrated on load: `true` becomes `"on"` and the old
stock `false` becomes `"global"`. Programmatic trace launches clear any stale launch
profile overrides and skip profile application.

`crossGameSource: "off"` disables donation for that game launch and restores the built-in
`crossGame.source` default in the session overlay so the donor choice cannot leak from a
previous launch. Game Setup labels this key **Borrow from**. When `crossGameSource`
is `"s3k"`, Game Setup shows the team as chosen on the save screen instead of the
`mainCharacter` and `sidekick` rows, because the donated S3K Data Select screen owns
team selection. When character rows are shown, their options follow the active donor:
Sonic is always available, Tails requires Sonic 2 or Sonic 3&K data, and Knuckles
requires Sonic 3&K data; hand-edited saved values outside that donor set are clamped
before launch. `aspect: "global"` inherits the normal `display.aspect` setting; pinned
aspect values such as `"WIDE_16_9"` apply only for that game session. They change the
logical projection within the existing window, restoring the global projection
when returning to the master title. In Game Setup, pinned aspects are amber
overrides. The 21:9 preset remains a best-effort smoke tier, while 32:9 is
exploratory; both are red and marked EXPERIMENTAL so Game Setup does not imply the
same support level as 16:10/16:9.

Stock defaults:

| Game | `rewind` | `crossGameSource` | `debugTools` | `aspect` | `mainCharacter` | `sidekick` |
|------|----------|-------------------|--------------|----------|-----------------|------------|
| Sonic 1 | `"global"` | `"off"` | `"global"` | `"global"` | `"sonic"` | `"none"` |
| Sonic 2 | `"global"` | `"off"` | `"global"` | `"global"` | `"sonic"` | `"tails"` |
| Sonic 3&K | `"global"` | `"off"` | `"global"` | `"global"` | `"sonic"` | `"tails"` |

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `LAUNCH_S1_REWIND` | `launch.s1.rewind` | enum | `"global"` | Live rewind for manual Sonic 1 launches: `"global"` follows `rewind.liveEnabled`; `"on"`/`"off"` override it. |
| `LAUNCH_S1_CROSS_GAME_SOURCE` | `launch.s1.crossGameSource` | enum | `"off"` | Cross-game donor for manual Sonic 1 launches: `"off"`, `"s2"`, or `"s3k"`. |
| `LAUNCH_S1_DEBUG_TOOLS` | `launch.s1.debugTools` | enum | `"global"` | Debug tools for manual Sonic 1 launches: `"global"` follows `debug.flags.debugView`; `"on"`/`"off"` override it. |
| `LAUNCH_S1_ASPECT` | `launch.s1.aspect` | enum | `"global"` | Display aspect for manual Sonic 1 launches: `"global"` or a `display.aspect` preset. |
| `LAUNCH_S1_MAIN_CHARACTER` | `launch.s1.mainCharacter` | enum | `"sonic"` | Main character for manual Sonic 1 launches. |
| `LAUNCH_S1_SIDEKICK` | `launch.s1.sidekick` | enum | `"none"` | Sidekick for manual Sonic 1 launches; `"none"` disables sidekick spawning. |
| `LAUNCH_S2_REWIND` | `launch.s2.rewind` | enum | `"global"` | Live rewind for manual Sonic 2 launches: `"global"` follows `rewind.liveEnabled`; `"on"`/`"off"` override it. |
| `LAUNCH_S2_CROSS_GAME_SOURCE` | `launch.s2.crossGameSource` | enum | `"off"` | Cross-game donor for manual Sonic 2 launches: `"off"`, `"s1"`, or `"s3k"`. |
| `LAUNCH_S2_DEBUG_TOOLS` | `launch.s2.debugTools` | enum | `"global"` | Debug tools for manual Sonic 2 launches: `"global"` follows `debug.flags.debugView`; `"on"`/`"off"` override it. |
| `LAUNCH_S2_ASPECT` | `launch.s2.aspect` | enum | `"global"` | Display aspect for manual Sonic 2 launches: `"global"` or a `display.aspect` preset. |
| `LAUNCH_S2_MAIN_CHARACTER` | `launch.s2.mainCharacter` | enum | `"sonic"` | Main character for manual Sonic 2 launches. |
| `LAUNCH_S2_SIDEKICK` | `launch.s2.sidekick` | enum | `"tails"` | Sidekick for manual Sonic 2 launches; `"none"` disables sidekick spawning. |
| `LAUNCH_S3K_REWIND` | `launch.s3k.rewind` | enum | `"global"` | Live rewind for manual Sonic 3&K launches: `"global"` follows `rewind.liveEnabled`; `"on"`/`"off"` override it. |
| `LAUNCH_S3K_CROSS_GAME_SOURCE` | `launch.s3k.crossGameSource` | enum | `"off"` | Cross-game donor for manual Sonic 3&K launches: `"off"`, `"s1"`, or `"s2"`. |
| `LAUNCH_S3K_DEBUG_TOOLS` | `launch.s3k.debugTools` | enum | `"global"` | Debug tools for manual Sonic 3&K launches: `"global"` follows `debug.flags.debugView`; `"on"`/`"off"` override it. |
| `LAUNCH_S3K_ASPECT` | `launch.s3k.aspect` | enum | `"global"` | Display aspect for manual Sonic 3&K launches: `"global"` or a `display.aspect` preset. |
| `LAUNCH_S3K_MAIN_CHARACTER` | `launch.s3k.mainCharacter` | enum | `"sonic"` | Main character for manual Sonic 3&K launches. |
| `LAUNCH_S3K_SIDEKICK` | `launch.s3k.sidekick` | enum | `"tails"` | Sidekick for manual Sonic 3&K launches; `"none"` disables sidekick spawning. |

Allowed launch profile enums:

| Field | Values |
|-------|--------|
| `crossGameSource` | `"off"`, `"s1"`, `"s2"`, `"s3k"`; the launched game cannot donate to itself and is clamped to `"off"` when hand-edited. |
| `aspect` | `"global"`, `"NATIVE_4_3"`, `"WIDE_16_10"`, `"WIDE_16_9"`, `"ULTRA_21_9"`, `"SUPER_32_9"` |
| `mainCharacter` | `"sonic"`, `"tails"`, `"knuckles"`; selectable values are donor-gated. |
| `sidekick` | `"none"`, `"sonic"`, `"tails"`, `"knuckles"`; selectable values are donor-gated. |

---

## Characters

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `MAIN_CHARACTER_CODE` | `characters.main` | string | `"sonic"` | Identity of the player-controlled character for direct launches. Native choices are Sonic in S1, Sonic/Tails in S2, and Sonic/Tails/Knuckles in S3K; donors and registered character patches can change availability. Hub launches use Game Setup or Data Select instead. |
| `SIDEKICK_CHARACTER_CODE` | `characters.sidekick` | string | `"tails"` | CPU-controlled sidekick spawned alongside the main character. Defaults to `"tails"` (Tails AI enabled). Set to `"sonic"` to clone the player, or `""` (empty) to disable the sidekick. |
| `DATA_SELECT_EXTRA_PLAYER_COMBOS` | `characters.dataSelectExtraCombos` | string | `""` | Extra team combinations shown on the S3K Data Select screen. Format is `main,sidekick1,sidekick2;main2,sidekick1`. The first character in each group is the main character; remaining entries are sidekicks. Example: `"sonic,knuckles;sonic,tails,tails;knuckles,tails"`. This only affects Data Select team choices; normal gameplay and Level Select still use `MAIN_CHARACTER_CODE` and `SIDEKICK_CHARACTER_CODE`. |

---

## Audio

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `AUDIO_ENABLED` | `audio.enabled` | bool | `true` | Master switch for all audio output (music and SFX). |
| `AUDIO_HUB_MUSIC_ENABLED` | `audio.hubMusicEnabled` | bool | `false` | Play the selected stock game's ROM title theme in the hub. |
| `AUDIO_HUB_MUSIC_VOLUME` | `audio.hubMusicVolume` | int | `20` | Hub theme volume, 0–100 percent; the theme is filtered and quieter while a child menu is open. |
| `REGION` | `audio.region` | string | `"NTSC"` | Hardware region: `"NTSC"` (60 Hz) or `"PAL"` (50 Hz). Affects SMPS tempo timing and DAC sample rates. Under NTSC, each presented frame advances the sound chips by one NTSC hardware frame (896,040 master clocks), so at a 60 Hz presentation music sounds about 1.29 per mille higher and faster than on a 59.92 Hz console. Under PAL, each frame advances the chips by 1/50 s, because PAL chip clocks are not modelled. |
| `DAC_INTERPOLATE` | `audio.dacInterpolate` | bool | `false` | Optional smoothing with no hardware counterpart: `true` writes a linearly interpolated value between DAC (drum) samples when the chip bus is idle. Off by default to preserve the hardware's stepped output; it never changes sample pitch or length. |
| `AUDIO_FM_CORE` | `audio.fmCore` | string | `"fast"` | Which FM synthesis core renders music and SFX. `"accurate"` is the cycle-exact Nuked-OPN2 port and the audio parity oracle; `"fast"` is the default register-level clean-room core (lower CPU cost, not bit-exact). Existing explicit selections are preserved. Unknown values fall back to `"accurate"`. Physical audio parity captures explicitly use the accurate core. |
| `AUDIO_INTERNAL_RATE_OUTPUT` | `audio.internalRateOutput` | bool | `false` | Output audio at the YM2612 internal sample rate (~53 kHz) rather than the system rate. Useful for bit-accurate captures; may cause issues on some audio drivers. |
| `FM6_DAC_OFF` | `audio.fm6DacOff` | bool | `true` | Silence FM channel 6 whenever a DAC note is active. Matches the SMPSPlay parity hack used in Sonic 2; prevents FM bleed audible during percussion. |

`audio.psgNoiseShiftEveryToggle` was removed in 0.6: the PSG noise LFSR now
always clocks once per rising edge of the noise square wave (the hardware/
libvgm rule), matching the shipped console. If it is still present in an
existing `config.yaml`, it is ignored with a logged warning rather than
rejected.

## Capture

OpenGGF has two separate recording systems:

- **Live viewport recording:** press `Shift+O` — or whatever `capture.toggleKey`
  is set to, since the modifiers are part of the value — during normal windowed
  execution to start and press it again to stop. This
  writes `capture-live-<UTC timestamp>.mkv` under `capture.outputDir` using
  lossless FFV1 video and stereo FLAC audio. It captures only the physical game
  viewport—not window borders or letterbox/pillarbox bars—and automatically
  stops if the viewport moves or changes size. Forward play, pause/frame-step
  silence, and held-rewind/release audio are synchronized with the displayed
  frames. The red-dot/white-`REC` indicator is window-only: both the MKV and F12
  screenshots exclude it. If a recording ends for a reason you did not ask for,
  a red `REC STOPPED: RESIZED` or `REC STOPPED: ERROR` notice replaces the
  indicator in the same corner for three seconds, so an interrupted recording
  is distinguishable from one you stopped yourself. Pressing the toggle again
  clears the notice. It is window-only on the same terms as the indicator.

### Capture codecs

`capture.codec` selects the video codec and `capture.audioCodec` the audio
codec, for both live viewport recording and trace capture.

| Codec | Lossless | Notes |
|---|---|---|
| `ffv1` (default) | yes | Largest files, most widely playable. |
| `h264` | yes | Much smaller than FFV1. Encoded as RGB — see below. |
| `h265` | yes | Smaller still, slowest to encode. Encoded as RGB. |
| `flac` (default) | yes | Audio matches the engine's output exactly. |
| `aac` | **no** | Lossy. Small files; the audio is not what the engine produced. |
| `mp3` | **no** | Lossy. Most portable, largest quality loss. |

**Why H.264 and H.265 use RGB.** The usual "lossless" settings — `-crf 0`, or
`-x265-params lossless=1` — are lossless in the codec's own colour space, but
the recorder submits RGB frames and converting them to YUV and back does not
return the original pixels. Even 4:4:4, which discards no chroma resolution,
differs. So `h264` uses `libx264rgb` and `h265` uses planar RGB (`gbrp`), which
are byte-exact. The cost is compatibility: RGB H.264/H.265 is valid but some
players will not decode it. If a recording will not play elsewhere, use `ffv1`.

Choosing `aac` or `mp3` means the recording is no longer a faithful capture of
the engine's audio. That is a legitimate choice for sharing a clip; it is not
appropriate for comparing audio against reference material.

### Containers

`capture.container` sets the recording's file extension, and ffmpeg picks its
muxer from it. Recent ffmpeg will write any of the codecs above into either
container — FFV1 and FLAC in MP4 both produce valid files — but *player*
support is far narrower than what ffmpeg will write:

| Container | Plays reliably with |
|---|---|
| `mkv` (default) | Everything here. The safe choice. |
| `mp4` | `h264` or `h265` video with `aac` audio. An MP4 holding FFV1 or FLAC is a valid file that most players will refuse. |

So MP4 is worth choosing when the recording is going somewhere that expects
it, paired with the lossy example below. For anything else, keep `mkv`.

`-movflags +faststart` — the usual "web-optimised" flag — is an MP4 feature
and only takes effect when the container is MP4. Add it through
`capture.ffmpegPass2Args`.

### Overriding the ffmpeg commands

Recording runs ffmpeg twice: the first pass encodes frames arriving on
`pipe:0` into a lossless intermediate, the second muxes that with the raw audio
into the finished file. `capture.ffmpegPass1Args` and `capture.ffmpegPass2Args`
replace the argument list of each pass independently. The executable itself is
resolved from `PATH` and prepended; everything after it is yours.

Each key takes one of three values:

| Value | Effect |
|---|---|
| `default` | The engine's built-in command. Keep this unless you need something specific — it means later improvements still reach you. |
| *(empty)* | Skip the pass. Only valid for pass 2: the encode output is published as-is, so **the recording has no audio**. A fast video-only capture. |
| anything else | Used literally, after placeholder expansion. |

Placeholders for pass 1: `{width}` `{height}` `{fps}` `{scale}`
`{scaledWidth}` `{scaledHeight}` `{videoCodecArgs}` `{videoOut}`.
For pass 2: `{videoIn}` `{audioIn}` `{sampleRate}` `{audioCodecArgs}`
`{output}`.

The unquoted `{videoCodecArgs}` and `{audioCodecArgs}` placeholders expand into
separate ffmpeg arguments. Other placeholder values, including paths containing
spaces, remain single values.

Arguments split on whitespace. Quote a value whose expansion may contain
spaces — `"{output}"` — and the placeholder still expands inside the quotes. An
unknown placeholder fails when the recording starts, rather than reaching
ffmpeg as a filename and failing later with an unrelated error.

Pass 1 cannot be empty: it is where the submitted frames are encoded. Frames
always arrive as `rawvideo`/`rgba` on `pipe:0`, so a replacement command must
still read them from there.

```yaml
capture:
  codec: "h264"
  audioCodec: "flac"
  # Video only, no mux pass:
  ffmpegPass2Args: ""
```

#### Example: small, portable, lossy

The codec keys stay lossless by design, so compressing for sharing means
replacing the encode pass. This writes CRF 24 H.264 in `yuv420p` — the
most widely playable combination there is — and lets the built-in mux
pass add AAC:

```yaml
capture:
  audioCodec: "aac"
  ffmpegPass1Args: >-
    -y -f rawvideo -pix_fmt rgba -s {width}x{height} -r {fps} -i pipe:0
    -vf vflip,scale={scaledWidth}:{scaledHeight}:flags=neighbor
    -c:v libx264 -crf 24 -preset veryfast -pix_fmt yuv420p -an {videoOut}
```

Note what changed relative to the lossless `h264` codec setting: this uses
`libx264` with `yuv420p` rather than `libx264rgb`, and a CRF of 24 rather
than 0. Both make it lossy, which is the point here — the result is a
small file that plays anywhere, and it is no longer a faithful capture.
There is no large lossless intermediate either, since the encode pass
compresses directly and the mux pass only copies the video stream.

For a web-ready file, set `capture.container` to `mp4` as well and add
`-movflags +faststart` to the mux pass:

```yaml
capture:
  container: "mp4"
  audioCodec: "aac"
  ffmpegPass2Args: >-
    -y -i {videoIn} -f s16le -ar {sampleRate} -ac 2 -i {audioIn}
    -c:v copy {audioCodecArgs} -movflags +faststart {output}
```

- **Trace capture:** the headless `TraceCaptureTool` renders a chosen trace.
  Its scale, frame-rate, and video codec can be selected by trace-tool options;
  `capture.container`, `capture.audioCodec`, and the ffmpeg pass settings control
  the published container and audio mux, just as they do for live recording.

Both paths require `ffmpeg` on `PATH`; live media verification and inspection
also use `ffprobe`. Live recording is distinct from the Shift+F9
`debug.recording.recordKey` input/movie recorder. Rewind is blocked while
an input/movie recording is active, because its input log cannot represent
rolling the world back. Stop that recording before rewinding.

Live recording treats an unavailable or failed audio tap as an audio-only
failure: video continues and the MKV retains a phase-correct stereo-silence
track. Encoder, output-file, and mux failures still stop the whole recording.
Stopping waits while queued frames continue encoding, then allows the encoder
to finalize the recording under its own timeout. The five-second queue watchdog
measures stalled frame progress, not total recording-finalization time. Application
shutdown can still cancel an unfinished recording after its bounded wait.
For development validation only, launch the JVM with
`-Dopenggf.debug.liveCaptureAudioFailAfterFrames=N` to inject a tap failure
before drain `N + 1`, after exactly `N` successful audio-frame drains. The
property is not stored in `config.yaml`; it is disabled when absent or set to
`-1`. Invalid values and values below `-1` warn and disable injection.

These keys set the code defaults; trace CLI flags override the trace-specific
ones. The bundled `config.yaml` supplies the live toggle default.

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `CAPTURE_OUTPUT_DIR` | `capture.outputDir` | string | `"target/trace-videos"` | Output directory for live and trace capture videos. |
| `CAPTURE_TOGGLE_KEY` | `capture.toggleKey` | key | `SHIFT+O` | Live viewport recording toggle. The modifiers live in the value (`CTRL+SHIFT+O`, `META+O`, or a bare key such as `SCROLL_LOCK` for none) and are matched exactly. A bare `O` is reserved — the compatibility migration rewrites it back to `SHIFT+O` on every launch. |
| `CAPTURE_SCALE` | `capture.scale` | int | `4` | Trace capture only: integer nearest-neighbor upscale factor applied to captured frames; live viewport recording always uses scale 1. |
| `CAPTURE_FPS` | `capture.fps` | int | `60` | Trace capture only: output frame rate; live recording uses the engine's effective display rate. |
| `CAPTURE_CODEC` | `capture.codec` | string | `"ffv1"` | Video codec for live and trace capture: `ffv1`, `h264` or `h265`. All three are lossless — see the note below. |
| `CAPTURE_CONTAINER` | `capture.container` | string | `"mkv"` | Recording file extension. ffmpeg selects its muxer from this — see "Containers" below. |
| `CAPTURE_AUDIO_CODEC` | `capture.audioCodec` | string | `"flac"` | Audio codec: `flac`, `aac` or `mp3`. **`aac` and `mp3` are lossy**: the recorded audio will not match what the engine produced. `flac` is lossless. |
| `CAPTURE_FFMPEG_PASS1_ARGS` | `capture.ffmpegPass1Args` | string | `"default"` | **Advanced.** Full ffmpeg argument list for the encode pass. See "Overriding the ffmpeg commands" below. |
| `CAPTURE_FFMPEG_PASS2_ARGS` | `capture.ffmpegPass2Args` | string | `"default"` | **Advanced.** Full ffmpeg argument list for the mux pass; leave empty to skip it and record video only. |

Live recording reuses a bounded pool of RGBA arrays (encoder queue capacity plus
two in-flight frames). On OpenGL 2.1 or newer, two additional GPU pixel-pack
buffers overlap readback with the next presentation; their memory is outside
`capture.queueBudgetMb`. Older contexts use synchronous readback. Video and audio
remain paired, and stopping flushes the final pending frame. Sustained GPU or
encoder overload can still block lossless recording; the queue cannot compensate
for an encoder that remains slower than playback.

Invoke the tool through Maven (requires a ROM in the working directory, an offscreen-capable GL context, and `ffmpeg` on `PATH`):

```bash
mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceCaptureTool" "-Dexec.args=--trace <id|name|dir> --out-dir target/trace-videos"
```

CLI flags (all optional except `--trace`; unspecified flags fall back to the `CAPTURE_*` defaults above):

| Flag | Default | Description |
|------|---------|-------------|
| `--trace <id\|name\|dir>` | (required) | Trace to capture: a 0-based `TraceCatalog` index, a trace directory name, or a direct path to a trace directory. |
| `--out-dir <dir>` | `CAPTURE_OUTPUT_DIR` | Output directory. The file is written as `capture-<trace-name>-<UTC>.mkv`. |
| `--scale <n>` | `CAPTURE_SCALE` | Integer nearest-neighbor upscale factor. |
| `--fps <n>` | `CAPTURE_FPS` | Output frame rate. |
| `--codec <name>` | `CAPTURE_CODEC` | Capture video codec. |
| `--no-ghosts` / `--ghosts` | `TRACE_SHOW_DESYNC_GHOSTS` | Hide / show the desync ghost(s) in the captured video (overrides the config flag for this run). |

Desync-ghost / game-HUD / debug-HUD visibility during capture is governed by the `TRACE_SHOW_*` keys in the Debug section; `--no-ghosts`/`--ghosts` is a per-run override for the ghost flag. Game-HUD and debug-HUD visibility are set via those config keys (no CLI flag yet).

Audio: headless capture installs `HeadlessSmpsAudioBackend`, a true no-device SMPS backend that synthesizes the music for the recording but **opens no audio device** (no speaker output; works on machines with no audio hardware). Audio is captured at the engine's 48 kHz synthesis rate and muxed as lossless FLAC, synced 1:1 with video.

## Time Attack (bundled mod)

Time Attack and multiplayer racing ship as the bundled, enabled-by-default **Time Attack**
mod. Open it through **Extras > Time Attack**; disable or re-enable it in **Mods**.
The engine no longer owns `timeAttack.*` settings. On its first opening, the mod imports
previous retry, network and minimap preferences from `config.yaml` or legacy `config.json`,
including the engine's `.bak` and numbered backup files, if it has no `settings.txt` yet.
Saved complete join addresses are discarded during import and are no longer written.
Other mod settings and the source files are preserved. During a run, **R** retries the
act from its start by default; change **Settings > Retry key** to use another key. The mod keeps best ghosts in `ghosts/<game>/` and the player identity in
`identity/`, the same locations as before the move.

The menu's **Mode** row offers **Solo**, **Host LAN**, **Join LAN** and **Browse** (rooms on a
master server). A room admits only players whose engine build and ROM for the room's game
match the host's, so select the room's game before joining. Each round plays as its own run;
the lobby returns when the run ends, and leaving the lobby closes the room connection (and,
for a host, the room).

Room creation offers **Public** and **Private**. Public rooms stay in the browser.
Private rooms are unlisted and require the creation password on every new
connection, including joins by invite and relay fallback. Passwords contain
15–128 Unicode code points, at most 512 UTF-8 bytes, without trimming or
normalization. Passwords are never saved in `settings.txt`.

The lobby's invite panel starts masked. Its eye reveals the code for up to ten
seconds; Copy writes it without revealing it. Focus loss, expiry, navigation or
a permission change hides it again. Codes have six case-insensitive letters or
digits, displayed in groups of three, and expire ten minutes after issuance.
Looking up or copying a code does not extend its lifetime. After expiry, an
authorized reveal/copy can request a fresh code. Expiry leaves admitted players
connected. The master accepts at most five code lookups per rolling minute per
identity and source IP across connections. Players behind one NAT share the IP
budget and receive retry timing when limited.

**Members can invite** defaults to on. Turning it off rotates the code and
restricts retrieval to the host. Previously shared codes stop resolving, but
recipients can still forward an invitation while it is valid. Code rotation
does not change the private password or remove admitted players. Direct joins
reveal the host's endpoint to the joining peer; relay routing protects it from
that peer. The trusted master still processes routing metadata.

Short codes use the selected `masterUrl` for remote and player-hosted games.
Without a configured or available master, manual LAN joining retains the longer
pinned invite, such as `HOST_IP:27888#<share-code>`. Replace `HOST_IP` with the
host's reachable LAN IP or name before sharing. Join LAN requires the complete
address, certificate pin and host-identity pin; private rooms also require the
password. Plain addresses and `ws://` room connections are rejected. Invitations
are no longer automatically logged, written to `lan-invite.txt` or saved as a
last join address. Existing user-authored exports are left untouched.

Invite, password, chat, display-name and URL fields use keyboard text entry.
Enter accepts and Escape cancels. Global display/capture shortcuts stay quiet
while an editor is open. Invite and password fields are masked while typing.

### Time Attack settings

The mod keeps its own settings in `settings.txt` (UTF-8 `key=value` lines) in its storage
folder. Edit them from the **Settings** row of the Time Attack menu; changes save at once.

| Key | Default | Description |
|-----|---------|-------------|
| `retryKey` | `82` (`R`) | Keyboard key that retries the act. The settings page captures a key; the file accepts GLFW key codes or names such as `F6`. |
| `hostPort` | `27888` | Port for player-hosted rooms (manual LAN and master-listed direct rooms), served as a pinned TLS WebSocket. |
| `displayName` | `""` | Multiplayer display name; blank uses the identity prefix. |
| `masterUrl` | `""` | Master-server `wss://` URL for browsing and short invite codes, including player-hosted rooms; blank leaves manual LAN joining available. Plaintext `ws://` is allowed only with a literal loopback IPv4/IPv6 address, not `localhost` or other hostnames. |
| `masterTrustInsecure` | `false` | Local development only: trust master-server and recording-upload TLS certificates at literal loopback addresses. Insecure upload mode rejects remote endpoints before taking custody of a recording; remote masters/uploads require normal certificate validation. |
| `minimap` | `true` | Show the multiplayer minimap progress strip. |

### Racing server: master verifier settings

These keys belong in the racing server's master YAML (`MasterServerMain
--config`, built from `racing/server`), not the engine's `config.yaml`:

| YAML key | Default | Description |
|----------|---------|-------------|
| `port` | `27900` | Public TLS WebSocket and HTTPS listener for the browser, relay rooms, recordings and verifier API. |
| `tlsCertPath` / `tlsKeyPath` | required | PEM certificate chain and private key paths. Remote deployments require normal TLS validation. |
| `dbPath` | `master-identities.db` | Identity database path; a relative path is beneath the master's `--data` directory. |
| `adminPort` | `27901` | HTTP administration/readiness listener, bound to loopback. Do not publish it from a container. |
| `adminToken` | required | Literal admin bearer token. Prefer `adminTokenFile` for deployment. |
| `adminTokenFile` | unset | Read the admin token from a UTF-8 file instead of `adminToken`. Relative file paths resolve against the YAML directory. |
| `newIdentityCacheSize` | `10000` | Maximum entries in each of the anonymous identity-age cache and the creation-proof reconnect-stamp LRU cache. Stamp eviction requires another creation proof for identities without a durable row. |
| `newIdentityCacheTtlMinutes` | `60` | Anonymous identity-age cache idle TTL and creation-proof reconnect-stamp lifetime from proof completion. Reconnects update stamp recency without extending its expiry. |
| `verifierRegistrationToken` | disabled | Shared bootstrap token required by `openggf-verifier` workers. Keep it secret and rotate it if disclosed. |
| `verifierRegistrationTokenFile` | unset | File-backed alternative to `verifierRegistrationToken`, resolved like `adminTokenFile`. |
| `maxRecordingBytes` | `65536` | Maximum accepted input-only attempt recording size. |
| `maxRecordingStorageBytes` | `536870912` | Maximum total bytes retained in the content-addressed recording store; new uploads receive HTTP 507 when full. |
| `recordingUploadMinIntervalMillis` | `1000` | Minimum interval between accepted recording uploads from the same player identity. |
| `uploadDeadlineSeconds` | `180` | Casual spot-check upload deadline. |
| `verifiedUploadDeadlineSeconds` | `15` | Verified-room upload deadline. |
| `recordingRetentionDays` | `3` | Recording blob retention before scheduled deletion. |
| `verdictGraceMillis` | `10000` | Extra verified-room result hold after the upload window. |
| `spotCheckTopTimes` | `1` | Top casual standings entries selected for spot-checking. |
| `cheatBanDays` | `0` | Failed-verification ban duration; `0` is permanent. |
| `verifierStaleSeconds` | `120` | Worker heartbeat/registration age after which it is unavailable; minimum 120 seconds leaves the 30-second worker heartbeat time to recover from one timed-out request. |
| `verifierLeaseSeconds` | `300` | Job lease duration before requeue. |
| `publicBaseUrl` | `""` | Absolute public HTTPS base for recording requests; blank sends a relative path that clients resolve against their master URL. |

Tokens contain printable non-space ASCII characters and are limited to 16 KiB.
File forms use UTF-8, strip surrounding whitespace, and reject empty, multiline,
malformed or unreadable files. Do not specify both literal and file forms of one
token. YAML token file paths resolve against the YAML directory; the verifier's
CLI token path resolves against its working directory. Secrets are loaded at
startup; recreate services after rotating files.
The authenticated `GET /admin/health` endpoint returns 204 when the master's
listeners are running. `racing-server healthcheck --config master.yaml` checks it
with bounded HTTP timeouts, without exposing the token in its arguments.

The verifier CLI takes `--master https://host:27900`, repeated `--rom /path/game.gen`,
`--data /path/worker-identity`, and either `--registration-token-file /path/token`
or the existing `--registration-token TOKEN`. Give every concurrent worker its
own data directory. Continuous workers retry initial registration and subsequent
transport failures. `--shutdown-grace-seconds` (default 30, range 1–300) stops new
polls on shutdown and bounds the wait for an active cycle to finish; allow a longer
container stop timeout. `--once` performs one registration/poll cycle and reports
failure instead of retrying indefinitely. Heartbeats do not renew job leases.
See [Docker deployment](racing/deploy/README.md) for the same configuration with
standalone images or Compose, including TLS, mounts, restart limits and backups.

## Debug

BK2 playback controls are unbound by default and must be rebound in `debug.playback`
before BK2 playback can be controlled from the keyboard.

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `DEBUG_VIEW_ENABLED` | `debug.flags.debugView` | bool | `false` | Eagerly initialise the debug overlay subsystem. Enables gameplay debug shortcuts and most overlay controls. The visible debug HUD starts hidden until toggled; performance overlay, screenshot and recording controls have their own gates. |
| `EDITOR_ENABLED` | `debug.flags.editor` | bool | `false` | Allow gameplay entry into the experimental level editor with `Shift+Tab`; project authoring is also available from Advanced → Editor projects at the hub. |
| `LIVE_REWIND_ENABLED` | `rewind.liveEnabled` | bool | `false` | Enable held-key rewind during ordinary live level play. Uses gameplay rewind snapshots, records live input while enabled, and presents reverse audio/fade state while held. |
| `LIVE_REWIND_DETERMINISM_AUDIT` | `debug.rewind.determinismAudit` | bool | `false` | Audit live-rewind determinism: re-simulates each completed rewind keyframe segment during live play and logs the first state divergence, pinpointing state that is missing from rewind capture. Disarms after the first divergence because replayed out-of-snapshot state cannot be rolled back. |
| `LIVE_REWIND_TAPE_COAST_ENABLED` | `rewind.tapeCoastEnabled` | bool | `false` | Enable experimental live-rewind coast after releasing the rewind key. Disabled by default, so held rewind remains one step per visual frame. When enabled, reverse audio playback is resampled to match the current rewind speed (>1.0 pitches up, <1.0 plays slow-motion). |
| `LIVE_REWIND_TAPE_COAST_MIN_STEPS` | `rewind.tapeCoastMinSteps` | number | `0.25` | Initial rewind speed (in steps per visual frame) when the rewind key is first pressed. Values below 1.0 produce a slow-motion start: the speed controller's fractional accumulator spreads each physics step across multiple visual frames. Speed then accelerates toward `LIVE_REWIND_TAPE_COAST_MAX_STEPS`. Used only when tape coast is enabled. |
| `LIVE_REWIND_TAPE_COAST_ACCELERATION` | `rewind.tapeCoastAcceleration` | number | `0.25` | Optional tape-coast acceleration in rewind steps per held frame. Used only when tape coast is enabled. |
| `LIVE_REWIND_TAPE_COAST_DECELERATION` | `rewind.tapeCoastDeceleration` | number | `0.5` | Optional tape-coast deceleration in rewind steps per released frame. Used only when tape coast is enabled. |
| `LIVE_REWIND_TAPE_COAST_MAX_STEPS` | `rewind.tapeCoastMaxSteps` | number | `4.0` | Maximum rewind steps per visual frame for optional tape-coast rewind. Values below 1.0 cap the rewind in slow-motion. Used only when tape coast is enabled. |
| `LIVE_REWIND_VHS_EFFECT` | `rewind.vhsEffect` | bool | `true` | Render an authentic VHS picture-search effect (scrolling noise bars, scanline jitter, chroma bleed, tape dropouts, head-switch strip) during live rewind or a controller-owned reverse-playback request. Native live rewind fades out over ~10 frames after release. Applied after the fade pass and before any user display shader. Controller requests work independently of `LIVE_REWIND_ENABLED` and are suppressed by host pause. |
| `LIVE_REWIND_VHS_TEAR_BANDS` | `rewind.vhsTearBands` | bool | `true` | Include the scrolling tear bands in the VHS rewind effect. Set `false` to keep the rest of the effect (scanline jitter, chroma bleed, tape dropouts, head-switch strip, wobble) without the bands. Only meaningful when the VHS effect is enabled. |
| `REWIND_HISTORY_SECONDS` | `rewind.historySeconds` | int | `60` | Seconds of live rewind keyframe and input history to retain. The effective retained window may be up to one keyframe interval longer so replay always has a complete keyframe-to-target input segment. |
| `REWIND_AUDIO_HISTORY_LIMIT_TYPE` | `rewind.audioHistoryLimitType` | string | `"time"` | How the rewind audio PCM history ring is capped. `"time"` caps by `REWIND_AUDIO_HISTORY_SECONDS`; `"size"` caps by `REWIND_AUDIO_HISTORY_SIZE_MB`. Held rewind beyond retained PCM history plays silence. |
| `REWIND_AUDIO_HISTORY_SECONDS` | `rewind.audioHistorySeconds` | int | `60` | Seconds of stereo PCM history kept for held-rewind playback when `REWIND_AUDIO_HISTORY_LIMIT_TYPE` is `"time"`. |
| `REWIND_AUDIO_HISTORY_SIZE_MB` | `rewind.audioHistorySizeMb` | int | `10` | Megabytes of stereo PCM history kept for held-rewind playback when `REWIND_AUDIO_HISTORY_LIMIT_TYPE` is `"size"`. Stereo 16-bit at 48 kHz consumes ~192 KB/s, so 10 MB is roughly 54 s at that sample rate (~57 s at 44.1 kHz). |
| `PLAYBACK_MOVIE_PATH` | `debug.playback.moviePath` | string | `""` | Path to a BizHawk BK2 movie for playback debugging. |
| `PLAYBACK_TOGGLE_KEY` | `debug.playback.toggleKey` | key | `""` / unbound | Toggle playback mode. |
| `PLAYBACK_LOAD_KEY` | `debug.playback.loadKey` | key | `""` / unbound | Load/reload the BK2 movie. |
| `PLAYBACK_PLAY_PAUSE_KEY` | `debug.playback.playPauseKey` | key | `""` / unbound | Toggle playback play/pause. |
| `PLAYBACK_STEP_BACK_KEY` | `debug.playback.stepBackKey` | key | `""` / unbound | Step the BK2 cursor backward by one frame. |
| `PLAYBACK_STEP_FORWARD_KEY` | `debug.playback.stepForwardKey` | key | `""` / unbound | Step the BK2 cursor forward by one frame. |
| `PLAYBACK_JUMP_BACK_KEY` | `debug.playback.jumpBackKey` | key | `""` / unbound | Jump the BK2 cursor backward by a larger interval. |
| `PLAYBACK_JUMP_FORWARD_KEY` | `debug.playback.jumpForwardKey` | key | `""` / unbound | Jump the BK2 cursor forward by a larger interval. |
| `PLAYBACK_FAST_RATE_KEY` | `debug.playback.fastRateKey` | key | `""` / unbound | Cycle playback rate (1x/2x/4x/8x). |
| `PLAYBACK_RESET_TO_START_KEY` | `debug.playback.resetToStartKey` | key | `""` / unbound | Reset the BK2 cursor to `PLAYBACK_START_OFFSET_FRAME`. |
| `PLAYBACK_START_OFFSET_FRAME` | `debug.playback.startOffsetFrame` | int | `0` | Starting frame offset for BK2 playback. |
| `RECORDING_RECORD_KEY` | `debug.recording.recordKey` | key | `F9` | `Shift+Record` starts recording from live `LEVEL` mode or opens the master-title recordings menu; plain Record stops an active recording. |
| `TEST_MODE_ENABLED` | `debug.testMode.enabled` | bool | `false` | Replace the master-title game-select with the Trace Test Mode picker that lists every trace in `debug.testMode.catalogDir` and plays the chosen trace back in the live engine. Dev-only. **When `true`, `DISPLAY_ASPECT` is always forced to `NATIVE_4_3` (320×224) regardless of its configured value** — trace replay and test-mode runs are parity-critical and must always run at 320×224. |
| `TRACE_CATALOG_DIR` | `debug.testMode.catalogDir` | string | `"src/test/resources/traces"` | Directory scanned by `TraceCatalog` when `TEST_MODE_ENABLED` is true. Resolved against `user.dir`. |
| `TRACE_SHOW_DESYNC_GHOSTS` | `debug.traceRender.showDesyncGhosts` | bool | `true` | In Trace Test Mode and trace capture, render the desync ghost(s). |
| `TRACE_SHOW_GAME_HUD` | `debug.traceRender.showGameHud` | bool | `true` | Render the game HUD (rings/score/time) during trace replay/capture. |
| `TRACE_SHOW_DEBUG_HUD` | `debug.traceRender.showDebugHud` | bool | `false` | Render the debug HUD during trace replay/capture; individual panels follow the existing `DebugOverlayToggle` states. |
| `DISCORD_RICH_PRESENCE_ENABLED` | `discord.enabled` | bool | `false` | Opt in to publishing OpenGGF menu/gameplay status through the local Discord desktop client. Disabled by default for privacy and no-ops when Discord is unavailable. |
| `DISCORD_RICH_PRESENCE_SHOW_TIMER` | `discord.showTimer` | bool | `true` | Include the current level timer in Discord Rich Presence gameplay status when presence is enabled. |
| `DISCORD_RICH_PRESENCE_SHOW_ZONE` | `discord.showZone` | bool | `true` | Include the current zone and act in Discord Rich Presence gameplay status when presence is enabled. |

Live rewind stores gameplay checkpoints every 10 ticks and audio checkpoints every
60 ticks. A cold backward step therefore replays at most nine gameplay ticks.
Compared with the previous 60-tick gameplay cadence, this retains roughly six
times as many gameplay snapshots and performs more forward-play capture work.
`rewind.historySeconds` bounds that history; reduce it if memory use matters.
Trace playback retains its configured checkpoint interval.

### Level Editor (experimental)

**F4** switches between level editing and the [Stageplay workspace](docs/guide/creating/stageplay.md)
in both editor frontends. Stageplay owns its input while active: Space plays/pauses,
left/right steps frames, Shift steps 60 frames, K directs the selected actor, C
toggles camera direction, R records a take and F1 opens its commands. In a Puppet
take, A/D move, S brakes and W jumps; a Poser take moves with WASD; Space or Esc
stops recording. Stageplay settings live in the scene file, and these shortcuts are
currently fixed. The [Stageplay reference](docs/guide/creating/stageplay-reference.md#in-engine-editor-commands-and-keys)
lists every key for both editors.

Open **Advanced → Editor projects** at the master title to create, open, or import
levels. Choose **New blank level**, then a game and ROM asset source. Entering from
a supported gameplay layout adopts it as a project; **Clone this layout** makes
a copy of an open document. Native collision remaps such as S1 loops cannot be
cloned into a flat authored level. The [level editor guide](docs/guide/creating/level-editor.md)
covers the complete workflow and supported scope.

`EDITOR_ENABLED` / `debug.flags.editor` allows `Shift+Tab` entry from ordinary
gameplay. While editing a project, the same shortcut switches to/from its
playtest. Editor key/mouse shortcuts are hardcoded and are not configurable in
`config.yaml`.

| Input | Action |
|-------|--------|
| `Shift+Tab` | Switch between the editor document and its playtest |
| `F5` | Start a fresh current-project playtest at the authored start; does not save |
| `F1` / controller Menu | Open the editor menu, including project commands |
| Arrows / gamepad menu directions | Move the cursor or selection in an open library |
| `Tab` / north controller button | Open the library for the current brush kind |
| `Page Up` / `Page Down` | Move through pages in an open library |
| `Enter` / South in a library | Select the highlighted brush |
| `O` | Cycle terrain, objects, and rings |
| `Space` / left click | Place or apply the brush |
| `E` / right click | Pick nearby terrain or a spawn |
| `Delete` | Erase terrain or delete a spawn |
| `M` | Move the selected spawn to the world cursor |
| `L` | Toggle FG / BG |
| `Enter` / `Escape` | Descend / ascend the hierarchy |
| `H` / `J` | Flip a selected block/chunk cell or object horizontally / vertically |
| `G` / `T` in Chunk view | Cycle pattern palette / toggle priority |
| `=` / `-` with an object selected | Increase / decrease that object's subtype |
| `R` with an object selected | Toggle respawn tracking |
| `Ctrl+Z` / `Ctrl+Y` / `Ctrl+S` | Undo / redo / save the project |
| `Ctrl+Shift+E` | Export to a new chosen directory |
| `C` / `P` | Toggle collision overlay / select collision path |
| `V` in Block view | Cycle the selected cell's collision mode |
| `[` / `]` | Decrease / increase the selected chunk's collision-profile index |
| Left drag in World | Paint terrain as one undoable stroke |
| `F2` with an object selected | Named property list in HUD; desktop uses its inspector |
| `F3` | Authored level settings: background scroll source, water height and allowed team |
| `Shift` + left drag / `F6` at two corners | Rectangular world-cell selection |
| `Ctrl+C` / `Ctrl+X` / `Ctrl+V` | Copy / cut / preview paste of region |
| `Enter` / `Escape` with paste preview | Commit / cancel paste |
| Middle drag / `Ctrl+arrows` | Pan canvas independently of cursor |
| Mouse wheel / `Ctrl+=` / `Ctrl+-` / `Ctrl+0` | Integer zoom 1×–8× / reset |

Bindings live in `EditorInputHandler` and are independent of gameplay Key
Bindings. Library sheets, project forms, and the editor menu own their input
while open. HUD clicks cannot paint through to the world.

The editor draws a controller-first HUD at window resolution around the
integer-scaled level: a top bar, a ten-slot brush hotbar and live button
prompts for the pad family in use (Xbox, PlayStation or keyboard). While it is
shown it owns the gamepad, so Genesis mappings, debug and rewind buttons do not
fire in the editor. The HUD is the only built-in editor UI; initialisation
failures propagate as engine errors.
A separate desktop editor, [OpenGGF-Editor](https://github.com/OpenGGF/OpenGGF-Editor),
replaces the HUD when its jar is on the class path; the engine finds it through the
`EditorWindowChrome` service.
With the HUD, `Tab` opens the library sheet, `F1` the editor menu, `Q` the tools
wheel and `1`–`0` choose a hotbar slot; the other keys above are unchanged.

| Controller | Action |
|------------|--------|
| Left stick / D-pad | Move the cursor (D-pad steps whole blocks) or the cell in Block/Chunk view |
| Right stick | Move the cursor quickly |
| South (A / Cross) | Paint or place; hold and steer to paint one undoable stroke |
| West (X / Square) | Pick what is under the cursor |
| North (Y / Triangle) | Open the library for the current brush kind |
| LB / RB | Previous / next hotbar brush; page the library or menu |
| RT / LT | Edit the block or chunk under the cursor / go back up |
| East (B / Circle) | Back: close a sheet or return to the parent view |
| View (hold) | Tools wheel; steer and release, or use the D-pad and South |
| Menu | Save, playtest, export, project commands and every editor command |
| L3 / R3 | Toggle FG/BG layer / collision overlay |
| Hold LB+RB, left stick / D-pad up/down | Pan canvas / zoom 1×–8× |
| Object properties: up/down / left/right | Choose named field / change value |
| Level palette: LB/RB, up/down, left/right | Choose color, RGB channel, native value |

#### Projects, playtests, and export

**Save project as** creates a complete `.ogfproject`; `Ctrl+S` saves to that path.
Projects retain environment/team settings, campaign order, imported-art reservations, art, palettes, collision profiles, both layouts, typed gizmo placements and ring
groups, game/source identity, and launch properties. They are independent of
stock gameplay saves and legacy sidecars. Failed saves preserve the preceding
project. Missing object keys remain in the document and block playtest until
resolved, rather than disappearing on the next save.

**Level properties** edits name, start, bounds, music, and the S3K object family.
**Resize level** uses block cells (256 pixels for S1, 128 for S2/S3K), preserves
overlap, adds empty terrain, and requires explicit crop authorization when shrinking.
The start is clamped into the resized bounds. Resize and bounds changes establish
new editing checkpoints. **Close project** offers Continue editing / Save and
continue / Discard changes and continue for an unsaved document. Music accepts
`stock:<decimal id>` or `owner:key`.

**Playtest from level start**, **Playtest from cursor**, and `F5` construct fresh
runtime snapshots of the current project. Returning restores authored data, selection,
and history. Playtest changes do not alter the document, trigger destructive
auto-save, or write stock gameplay saves.

**Export level** uses the project properties and chooses a new destination each
time; an existing directory is never replaced. S1/S2 use `ModLevelDefinition` v1 with
16×16/8×8 blocks respectively. S3K uses v2 with explicit `S3KL`/`SKL` object-family
metadata and sparse palette claims. S3K's engine-provided
`openggf:authored-goal` object supplies an authored finish without repurposing a
numeric stock ID.

The exporter validates complete references, exact Genesis colors, and destination
host capabilities, then reads back the staged directory before publication.
Projects preserve exact RGB and larger native libraries; a project save does not
certify a portable export. Unsupported native stock objects/events or host-owned
palette cells produce actionable errors and leave the project intact.

Exports contain ROM-derived data as well as authored changes. OpenGGF does not
grant redistribution rights to Sega assets. Treat them as local development
material unless you have permission to distribute every included asset.

---

## Key Bindings

Key bindings accept any of the following formats:

| Format | Example | Notes |
|--------|---------|-------|
| GLFW numeric code | `81` | Traditional format |
| Numeric string | `"81"` | Same as above, as a string |
| Key name | `"Q"` | Human-readable, case-insensitive |
| Named key | `"SPACE"`, `"ENTER"`, `"F9"` | Special keys by name |
| Modifier key | `"LEFT_SHIFT"`, `"RIGHT_CONTROL"` | Modifier keys |
| GLFW prefix | `"GLFW_KEY_Q"` | Full GLFW constant name (prefix stripped) |
| Chord | `"SHIFT+O"`, `"CTRL+SHIFT+O"`, `"META+LEFT_BRACKET"` | Key qualified by modifiers — acted on by the bindings listed under *Modifier support per binding* below |

Invalid key names log a warning and fall back to the default binding for that key.

#### Chord syntax

- **Modifier aliases** (case-insensitive): `CTRL`/`CONTROL`, `SHIFT`,
  `ALT`/`OPTION`, and `META`/`SUPER`/`CMD`/`COMMAND`/`WIN`. Whitespace around
  `+` is tolerated, so `"Shift + O"` and `"shift+o"` are the same value.
- **Canonical order is `CTRL, SHIFT, ALT, META`.** That is the spelling the
  engine writes back when it saves the config, whatever order you typed.
- **Chord-aware matching is exact.** A binding listed as honouring chords below
  fires only when its declared modifiers are
  held *and* the others are released, so a plain `"O"` does not fire while any
  modifier is down.
- **Binding the plus key:** `+` is the separator, so write `EQUAL` (or `KP_ADD`
  for the numpad). A value of only separators (`"+"`, `"++"`) names no key, so
  it is an unresolvable value under the next bullet and falls back to the
  binding's registered default — it does **not** unbind. `capture.toggleKey:
  "+"` leaves recording live on `SHIFT+O`. Only `""` unbinds.
- **Unresolvable values.** A **non-empty** value that resolves to no key —
  `"NOT_A_KEY"`, `"CTRL+"`, an unknown modifier — logs a warning and falls back
  to that binding's registered default. An **explicitly empty** value (`""`) is
  unbound outright: no default is substituted. That asymmetry is how a shortcut
  is deliberately switched off.
- **`O` is reserved for `capture.toggleKey`.** The migration that carries
  existing installs onto the `SHIFT+O` default matches on the value rather than
  on a schema version, so it re-runs on every launch and rewrites every value
  that resolves to an unmodified `O` — `O`, `GLFW_KEY_O`, `KEY_O`, `79` and the
  quoted `"79"` — back to `SHIFT+O`. Binding *this one action* to a bare `O` is
  not possible; pick another key.

#### Modifier support per binding

Modifiers parse everywhere, but only some bindings act on them. There are three
states, and the third is invisible from the config file:

| State | Meaning | Bindings |
|-------|---------|----------|
| Chord honoured | Read as a chord and matched exactly | `CAPTURE_TOGGLE_KEY`, `SUPER_SONIC_DEBUG_KEY`, `GIVE_EMERALDS_KEY`, `HYPER_FORM_DEBUG_KEY`, `GIVE_SUPER_EMERALDS_KEY` |
| Modifiers ignored | Read as a bare key code; a chord resolves to its key and the modifiers are dropped, so `"CTRL+P"` fires on plain `P` | every binding not named in the other two rows |
| Chord permanently dead | Read through a "no modifier held" check, so the modifier you must hold to type the chord is exactly what blocks the shortcut. The modifiers are still dropped, so `debug.playback.toggleKey: "CTRL+P"` binds plain `P`: the chord as written never fires, and an unmodified `P` does | the nine `PLAYBACK_*` keys; `SPECIAL_STAGE_KEY`, `SPECIAL_STAGE_COMPLETE_KEY`, `SPECIAL_STAGE_FAIL_KEY`, `SPECIAL_STAGE_SPRITE_DEBUG_KEY`, `SPECIAL_STAGE_PLANE_DEBUG_KEY`, `NEXT_ACT`, `NEXT_ZONE`, `DEBUG_LAST_CHECKPOINT_KEY`, `LEVEL_SELECT_KEY`, `CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY` |

`UP`/`DOWN`/`LEFT`/`RIGHT` and `DEBUG_MODE_KEY` are in **two** states at once.
Modifiers are ignored for `UP`/`DOWN`/`LEFT`/`RIGHT` on the gameplay path, and
the chord is dead on the special-stage sprite-debug path, which reads them
through the same unmodified-debug-key check. `DEBUG_MODE_KEY`'s chord is dead in
`GameLoop` (both reads go through that check), but `SpriteManager`'s debug-mode
toggle and the live-rewind input recorder read it with a plain key-pressed
check, so `debug.keys.debugMode: "CTRL+D"` still toggles sprite debug mode on a
plain `D` with the Ctrl neither required nor checked.

Some shortcuts are hardcoded and consume a keystroke whatever a binding says:
Shift/Ctrl/Alt+`B` (bonus-stage debug, exactly one modifier), Shift+`Tab`, left
Ctrl+`P` (copy performance stats — debug-only, see below), the editor's
`Tab`-without-Shift and Ctrl+`Z`/`S`/`Y`, and the display shader picker's confirm
key. The sixteen debug-overlay toggles
(`F1`-`F8`, `F10`-`F12`, `K`, `` ` ``, `=`, `P`, `O`) are hardcoded too. They fire
on their bare key with any modifier held — a modifier is usually held for an
unrelated reason, and player two's jump defaults to right Shift — but stand aside
for the frame a chord bound to the *same* key is satisfied, which is what stops
the `SHIFT+O` capture default toggling the object-debug overlay. Binding
`capture.toggleKey` to one of these keys with no modifier makes both fire on the
same keystroke.

A toggle only stands aside for an action that can actually run, so `P` gives way
to the stats copy only while `DEBUG_VIEW_ENABLED` is on. With debug shortcuts
off — the shipped default — Ctrl+`P` toggles the performance overlay and copies
nothing, so no install has a keystroke that silently overwrites the OS clipboard.
The stats copy also needs the **left** Ctrl specifically: `CTRL` in a configured
chord means either Ctrl, but right Ctrl is player two's default Start, so
matching either would let player two's Start turn player one's `P` into a
clipboard write. `RECORDING_RECORD_KEY` is a
configurable binding whose Shift is still hardcoded at its call sites (the
runtime recording controls and the master title screen), so its modifier cannot
be moved into the value yet.

**Under playback:** live rewind records and reproduces real Shift, Ctrl, Alt and
Super states, so a chord behaves the same on replay as it did live. BK2 movies
carry no modifier column at all, so all four read as released under BK2
playback and any chord requiring a modifier does not fire there.

The tables below list each key's name, default code, and the human-readable key name for the default.

### Gameplay Controls

Keyboard action bindings are Mega Drive actions. A, B, and C all function as
jump buttons during platforming gameplay. By default only A is bound on
keyboard: P1 A is `SPACE`, P2 A is `RIGHT_SHIFT`, and keyboard B/C are
unbound.

Gamepads use GLFW's standard position-based gamepad layout. D-pad and left
stick feed digital movement; the west face button maps to Mega Drive A, south
maps to Mega Drive B, and east maps to Mega Drive C. On an Xbox controller
that is X/A/B respectively; on a PlayStation controller that is
Square/Cross/Circle respectively.

| Key | YAML path | Default | Key Name | Description |
|-----|-----------|---------|----------|-------------|
| `UP` | `input.player1.up` | `265` | ↑ Arrow | Look up / enter tubes. |
| `DOWN` | `input.player1.down` | `264` | ↓ Arrow | Crouch / roll / spindash charge. |
| `LEFT` | `input.player1.left` | `263` | ← Arrow | Move left. |
| `RIGHT` | `input.player1.right` | `262` | → Arrow | Move right. |
| `P1_A` | `input.player1.a` | `32` | Space | Player 1 action button A / jump. |
| `P1_B` | `input.player1.b` | `-1` | unbound | Player 1 action button B. |
| `P1_C` | `input.player1.c` | `-1` | unbound | Player 1 action button C. |
| `START` | `input.player1.start` | `259` | Backspace | Player 1 Start: ROM-accurate in-game pause (`Game_paused` / `Pause_Loop`). A press during level gameplay freezes the level update for the frame while the frame counter still advances; press again to resume. Distinct from `PAUSE_KEY`, which is the loop/timing-level pause that also halts audio. Keyboard-only: the gamepad Start button is instead wired to `PAUSE_KEY`'s pause (see below), since this one has no visible feedback. |
| `P2_UP` | `input.player2.up` | `73` | I | Player 2 look up. |
| `P2_DOWN` | `input.player2.down` | `75` | K | Player 2 crouch / roll. |
| `P2_LEFT` | `input.player2.left` | `74` | J | Player 2 move left. |
| `P2_RIGHT` | `input.player2.right` | `76` | L | Player 2 move right. |
| `P2_A` | `input.player2.a` | `344` | Right Shift | Player 2 action button A / jump. In S2 this feeds the configured sidekick/manual-input path; it does not enable native two-player/competition gameplay. |
| `P2_B` | `input.player2.b` | `-1` | unbound | Player 2 action button B. |
| `P2_C` | `input.player2.c` | `-1` | unbound | Player 2 action button C. |
| `P2_START` | `input.player2.start` | `345` | Right Control | Player 2 Start. |
| `CONTROLLER_ENABLED` | `input.controller.enabled` | `true` | true | Enable GLFW gamepad/controller input. |
| `CONTROLLER_DEADZONE` | `input.controller.deadzone` | `0.35` | 0.35 | Left-stick digital direction deadzone. |
| `CONTROLLER_PLAYER1` | `input.controller.player1` | `"auto"` | auto | Controller assignment for Player 1 (`auto` or `none`). |
| `CONTROLLER_PLAYER2` | `input.controller.player2` | `"auto"` | auto | Controller assignment for Player 2 (`auto` or `none`). |

The gamepad Back/Select/View button on the primary connected pad is a hardcoded (not remappable, no config key) stand-in for the keyboard `Tab` key on the main-menu game-select screen (`MasterTitleScreen`) and its per-game options panel (`LaunchConfigPanel`): it opens the options panel for the selected game and closes it again, mirroring `Tab`'s role in that specific flow only. It has no effect on `Tab`'s other keyboard uses elsewhere (level editor toggle, Special Stage entry, art viewer), which are unrelated screens/modes.

| `PAUSE_KEY` | `input.pause` | `257` | Enter | Pause / unpause the game (`userPaused`): shows the "PAUSED" HUD overlay and halts audio. The gamepad Start button on the primary connected pad also toggles this pause, unconditionally (not remappable) -- gamepad Start does NOT trigger the ROM `Game_paused` ("Player 1 Start" / `START`) pause above, since that one has no visible feedback. |
| `FRAME_STEP_KEY` | `debug.keys.frameStep` | `81` | Q | Advance one frame while paused. The gamepad right bumper (RB/R1) on the primary connected pad also steps a frame, unconditionally (not remappable). |
| `RECORDING_RECORD_KEY` | `debug.recording.recordKey` | `298` | F9 | `Shift+Record` starts/opens user recording flows; plain Record stops active recording. |
| `TRACE_REWIND_KEY` | `debug.traceRewind.key` | `82` | R | Hold during visual Trace Test Mode replay to rewind deterministic engine state in real time, including reverse audio presentation and restored fade snapshots. |
| `LIVE_REWIND_KEY` | `rewind.liveKey` | `82` | R | Hold during live level play to rewind deterministic gameplay state when `LIVE_REWIND_ENABLED` is true, including reverse audio presentation and restored fade snapshots. The gamepad left bumper (L1/LB) on the primary connected pad also holds rewind, unconditionally (not remappable). |
| `LIVE_REWIND_HALF_SPEED_KEY` | `rewind.liveHalfSpeedKey` | `341` | Left Ctrl | Modifier held together with the rewind key for half-speed rewind (one engine step every other frame; reverse audio plays slow-motion). The mirrored left/right variant of a modifier key also counts. Holding both speed modifiers cancels back to normal speed. |
| `LIVE_REWIND_DOUBLE_SPEED_KEY` | `rewind.liveDoubleSpeedKey` | `340` | Left Shift | Modifier held together with the rewind key for double-speed rewind (two engine steps per frame; reverse audio pitches up, and the VHS effect shows a third tear band). The mirrored left/right variant of a modifier key also counts. |

Controlled mods can consume the configured `LIVE_REWIND_KEY` shortcut and primary
L1/LB bumper independently of `LIVE_REWIND_ENABLED`. Putt Putt Paradise uses them
for whole-shot undo with its own setup allowances: rewinds per hole
`off / 3 / 5 / *`, rewinds per turn `1 / 3 / *` (`*` is unlimited). Defaults are
three per golfer/hole and one per turn. Unbinding the keyboard shortcut keeps
the bumper available to controlled mods. Start → Rewind Shot provides an
A-operated alternative during WATCH, including for Genesis movie replay. Settled
shots pass the turn automatically; completed turns cannot be rewound. Live shortcut input
is suppressed while a movie owns the controller rows.

### Debug Navigation

| Key | YAML path | Default | Key Name | Description |
|-----|-----------|---------|----------|-------------|
| `NEXT_ACT` | `debug.keys.nextAct` | `266` | PAGE_UP | Skip to the next act within the current zone. |
| `NEXT_ZONE` | `debug.keys.nextZone` | `267` | PAGE_DOWN | Skip to the first act of the next zone. |
| `DEBUG_MODE_KEY` | `debug.keys.debugMode` | `68` | D | Toggle free-fly debug movement mode (requires `DEBUG_VIEW_ENABLED`). The gamepad north face button (Y/Triangle) on the primary connected pad also toggles it when debug tools are enabled (not remappable). |
| `DEBUG_LAST_CHECKPOINT_KEY` | `debug.keys.lastCheckpoint` | `67` | C | Reload at the furthest-right checkpoint, initializing destination events without losing a life. |
| `LEVEL_SELECT_KEY` | `debug.keys.levelSelect` | `298` | F9 | Open the level select screen at runtime. |
| `TEST` | `debug.keys.test` | `84` | T | Generic test button used during development. |

### Super Sonic / Emerald Debug

| Key | YAML path | Default | Key Name | Description |
|-----|-----------|---------|----------|-------------|
| `SUPER_SONIC_DEBUG_KEY` | `debug.keys.superSonic` | `85` | U | Toggle Super Sonic transformation (requires `DEBUG_VIEW_ENABLED` and all emeralds). |
| `GIVE_EMERALDS_KEY` | `debug.keys.giveEmeralds` | `69` | E | Instantly award all Chaos Emeralds (debug shortcut). |
| `HYPER_FORM_DEBUG_KEY` | `debug.keys.hyperForm` | `SHIFT+U` | Shift+U | Toggle the highest S3K form unlocked by Super Emeralds: Hyper Sonic, Super Tails, or Hyper Knuckles. |
| `GIVE_SUPER_EMERALDS_KEY` | `debug.keys.giveSuperEmeralds` | `SHIFT+E` | Shift+E | Instantly award all Super Emeralds (debug shortcut). |

### Special Stage Debug

These shortcuts require debug tools. Tab enters or exits a Special Stage; the
other shortcuts apply while one is running. Sprite and plane diagnostics are
provided by Sonic 2 only. See the [controls reference](docs/guide/playing/controls.md#special-stage-debug)
for the game-specific tools.

| Key | YAML path | Default | Key Name | Description |
|-----|-----------|---------|----------|-------------|
| `SPECIAL_STAGE_KEY` | `debug.keys.specialStage` | `258` | Tab | Enter / exit Special Stage mode (debug). |
| `SPECIAL_STAGE_COMPLETE_KEY` | `debug.keys.specialStageComplete` | `269` | End | Complete the current Special Stage and award the emerald. |
| `SPECIAL_STAGE_FAIL_KEY` | `debug.keys.specialStageFail` | `261` | Delete | Fail the current Special Stage without awarding the emerald. |
| `SPECIAL_STAGE_SPRITE_DEBUG_KEY` | `debug.keys.specialStageSpriteDebug` | `301` | F12 | Toggle the Special Stage sprite debug viewer. |
| `SPECIAL_STAGE_PLANE_DEBUG_KEY` | `debug.keys.specialStagePlaneDebug` | `292` | F3 | Cycle Special Stage plane visibility debug modes. |

---

## Sonic3K Chaos Mode example

The [Chaos Mode mod](examples/sonic3k-chaos-mode/README.md) stores its own settings
through owner-local mod storage, separately from `config.yaml`. Its title and pause
menus provide Available/Selected effect lists and apply settings only after a
successful save. The interval is 5–120 seconds (default 30); effect duration is
0.25–3× in quarter steps (default 1×). Individual effects retain their own bounded
base duration. Five optional sidegames and five demanding context effects start
unselected; an empty selected pool is valid.

The versioned `settings.txt` and `settings-backup.txt` contain `interval`,
`durationQuarters`, stable selected effect IDs, `enabled` and `seed`. A seed of zero
picks a fresh seed for each run; a nonzero seed starts every run from the same random
state. Unrecoverable corrupt or incompatible
preferences remain preserved until explicit Reset defaults; a valid backup can be
recovered automatically. Gameplay rewind
restores the admitted scheduler and effects, while saved player preferences remain
the latest successful publication. The development launcher uses
`target/sonic3k-chaos-mode/player-settings`, or an explicit `--save-root`.

## Install location and bundled mods

These JVM system properties (`-D<name>=<value>` on the `java` command line) are not
stored in `config.yaml`.

| Property | Default | Purpose |
| --- | --- | --- |
| `openggf.installDir` | Directory holding the engine jar (for an exploded class directory such as `target/classes`, its parent) | Application install directory whose `bundled/` folder supplies the jars of mods bundled with this build. The working directory is never used for this. Set it only when the engine jar was moved away from its `bundled/` folder. |

Bundled mods are listed, with their exact SHA-256, in a manifest inside the engine
artifact; only jars matching it are trusted by default (see
[trust](docs/modding/concepts/trust.md#first-party-bundled-mods-a-deliberate-narrowing)).
The universal jar embeds them and extracts them to `bundled-mod-cache/` under the save
root (`saves/` unless `openggf.saveRoot` is set). The cache is re-verified on every
normal start, pruned of earlier builds' jars, and safe to delete. Native builds carry
no bundled mods. A player's enable/disable choice is stored in `mods/modstate.json`
like any other mod's.

---

## Test-only system properties

JVM properties are supplied as `-D<name>=<value>`, separately from `config.yaml`.
The historical `oggf.trace.hydrate` switch no longer has an implementation in the
committed replay path. Leave it unset: `TestTraceHydrateSwitchDefault` rejects it
in test runs. Trace rows remain comparison-only; recorder version metadata does
not enable gameplay hydration. See the [trace replay guide](docs/guide/contributing/trace-replay.md)
for the current replay workflow.

---

## Example `config.yaml`

A user file contains overrides, not a second copy of every default. For example:

```yaml
configFormat: 2
display:
  aspect: "WIDE_16_9"
rewind:
  liveEnabled: true
launch:
  s1:
    rewind: "off"
    debugTools: "on"
```

Omit an override to follow the built-in default. Launch-profile `rewind` and
`debugTools` accept `"global"`, `"on"` or `"off"`; `"global"` is their default.
The [bundled template](src/main/resources/config.yaml), also written to
`config.yaml.example` on startup, is the maintained reference for the full example.

## Prepared mutator settings

The JVM [Mutator Lab](examples/example-mutators/README.md) keeps requested choices
in `<openggf.saveRoot>/mutators/mutator-preferences-s2.json` (`saves/` by default).
The developer builder uses its own `target/example-mutators/player-settings` root.
These preferences are independent of historical rewind state. Resume admits LIVE
edits; qualifying full rebuilds admit LOAD; a new game session admits LAUNCH.
The current playable examples only expose LIVE edits. Unsupported or unsafe
preference I/O reports a visible error and retains the in-memory draft.
The common title owns Back: Escape backs out of help/options and, from the title,
saves before Return to the game hub. Failed saves keep that title and error;
accepted exits hold menu input throughout the fade. Gameplay configuration also
keeps Start, Resume, Restart and Return behind their persistence boundary.


### Hub history and game discovery

The hub remembers the last focused game, falling back to Sonic 2 if that entry is
no longer available. History is persisted under `hub.lastGame` and is maintained
by the launcher rather than exposed as an ordinary Settings row. Use the
paused-game card to resume an exact live session. The former `hub.continueS1*`,
`hub.continueS2*` and `hub.continueS3k*` values are retained for configuration
compatibility but are ignored; the hub no longer records or restarts reached acts.

With no games found, **Add your games** offers **Locate a game** and
**Scan a folder**. Discovery recognizes supported images by their contents,
including all supported games found in the selected folder. Original files stay
in place. The browser offers Home, Downloads and Last folder shortcuts and puts
likely ROM extensions ahead of unrelated files; the ROM? marker is a filename
hint, not proof that a file is a supported game. Selecting a file validates it.
