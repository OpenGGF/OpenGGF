# Configuration Reference

All settings live in `config.yaml` in the working directory (next to the JAR). The bundled
`src/main/resources/config.yaml` is used as the default template. On first run, a legacy
`config.json` is automatically migrated to `config.yaml` and the original is backed up to
`config.json.bak`. Keys are now grouped into nested YAML sections rather than being flat
enum names.

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
| `display` | Aspect preset, window autosize, deadzone mode, color profile, FPS |
| `input` | `player1` / `player2` key bindings, `pause` key |
| `audio` | Enabled flag, region, DAC, FM6, PSG settings |
| `characters` | Main character, sidekick, data select combos |
| `roms` | ROM filenames for S1, S2, S3K; default game selection |
| `startup` | Title screen, master title, legal disclaimer flags |
| `rewind` | Live rewind enable/key, tape-coast parameters, audio history settings |
| `crossGame` | Cross-game feature donation enable and source |
| `discord` | Discord Rich Presence enable, show timer, show zone |

**`debug:` block** (developer/debug tooling — safe to ignore for normal play):

| Sub-section | Contents |
|-------------|----------|
| `debug.flags` | Debug view, editor enable, collision view overlay |
| `debug.keys` | All debug/developer keyboard shortcuts |
| `debug.startup` | Level select on startup, S3K skip intros |
| `debug.playback` | BK2 movie path and playback control keys |
| `debug.traceRewind` | Key held during Trace Test Mode to rewind engine state |
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
| `SCREEN_HEIGHT_PIXELS` | *(derived)* | int | `224` | Logical pixel height — the Mega Drive native vertical resolution (224 for NTSC, 240 for PAL). Derived; never stored. |
| `SCREEN_WIDTH` | `display` / `debug.window.width` | int | `640` | Actual window width in OS pixels. Derived from aspect preset when `display.windowAutosize=true`. |
| `SCREEN_HEIGHT` | `display` / `debug.window.height` | int | `448` | Actual window height in OS pixels. |
| `SCALE` | `debug.window.scale` | double | `1.0` | **DEPRECATED** additional rendering scale factor. |
| `FPS` | `display.fps` | int | `60` | Target frames per second. Affects game speed — use `60` for NTSC, `50` for PAL. |
| `DISPLAY_COLOR_PROFILE` | `display.colorProfile` | string | `"RAW_RGB"` | Palette presentation profile. `"RAW_RGB"` keeps the current direct 8-bit expansion, `"MD_ANALOG"` applies a darker Mega Drive-style analog ramp, and `"NTSC_SOFT"` applies the analog ramp plus mild desaturation. |
| `DISPLAY_COLOR_PROFILE_TOGGLE_KEY` | `display.colorProfileToggleKey` | key | `V` | Runtime key used to cycle display color profiles. The selected profile is saved to `config.yaml` and shown briefly in the bottom-left corner. |
| `DISPLAY_ASPECT` | `display.aspect` | string | `"NATIVE_4_3"` | Display aspect preset. Controls the native pixel width used by the renderer. Accepted values: `"NATIVE_4_3"` (320 px, default), `"WIDE_16_10"` (352 px), `"WIDE_16_9"` (400 px), `"ULTRA_21_9"` (528 px), `"SUPER_32_9"` (800 px). **EXPERIMENTAL / INCOMPLETE** — widescreen rendering (UI pillarbox, parallax column extension) is not finished; only `"NATIVE_4_3"` is fully supported. |
| `DISPLAY_WINDOW_AUTOSIZE` | `display.windowAutosize` | bool | `true` | When `true` and a widescreen preset is active, the OS window is derived from the preset at 2x baseline (e.g. `WIDE_16_9` → 800×448). When `false`, `SCREEN_WIDTH`/`SCREEN_HEIGHT` are used verbatim so a custom window size is preserved. Has no effect when `DISPLAY_ASPECT` is `"NATIVE_4_3"`. |
| `WIDESCREEN_DEADZONE_MODE` | `display.deadzoneMode` | string | `"PROPORTIONAL"` | Camera horizontal deadzone behaviour on wide screens: `"CENTER_SCALED"` keeps the native 16px deadzone band; `"PROPORTIONAL"` scales the band width with the screen width. **EXPERIMENTAL** — takes effect only when a widescreen preset is active. |

---

## ROM Files

Paths are relative to the working directory (where the JAR is launched).

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `DEFAULT_ROM` | `roms.default` | string | `"s2"` | Which game to boot: `"s1"`, `"s2"`, or `"s3k"`. Selects the corresponding ROM key below. |
| `SONIC_1_ROM` | `roms.sonic1` | string | `"Sonic The Hedgehog (W) (REV01) [!].gen"` | Filename of the Sonic 1 ROM. |
| `SONIC_2_ROM` | `roms.sonic2` | string | `"Sonic The Hedgehog 2 (W) (REV01) [!].gen"` | Filename of the Sonic 2 ROM. |
| `SONIC_3K_ROM` | `roms.sonic3k` | string | `"Sonic and Knuckles & Sonic 3 (W) [!].gen"` | Filename of the Sonic 3&K (locked-on) ROM. |

---

## Startup Flow

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `SHOW_LEGAL_DISCLAIMER_ON_STARTUP` | `startup.legalDisclaimer` | bool | `true` | Show the legal disclaimer screen on engine startup before the master title screen. White text on black, 5-second readability gate, any-key dismiss, fade-in/out transitions. Set `false` for headless tests, trace replay sessions, or CI runs that should not have to drive past this screen. |
| `MASTER_TITLE_SCREEN_ON_STARTUP` | `startup.masterTitleScreen` | bool | `true` | Show the master title / game-selection screen on launch. When `false`, boots directly into the game set by `DEFAULT_ROM`. |
| `TITLE_SCREEN_ON_STARTUP` | `startup.titleScreen` | bool | `true` | Show the game-specific title screen (e.g. Sonic 2 title screen) before gameplay. Ignored when `MASTER_TITLE_SCREEN_ON_STARTUP` is true and game selection is pending. |
| `LEVEL_SELECT_ON_STARTUP` | `debug.startup.levelSelectOnStartup` | bool | `false` | Jump straight to the level select screen instead of the title screen. Useful for development. |
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

## Characters

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `MAIN_CHARACTER_CODE` | `characters.main` | string | `"sonic"` | Identity of the player-controlled character. Currently only `"sonic"` is supported. |
| `SIDEKICK_CHARACTER_CODE` | `characters.sidekick` | string | `""` | CPU-controlled sidekick spawned alongside the main character. Set to `"tails"` to enable Tails AI, `"sonic"` to clone the player, or `""` (empty) to disable. |
| `DATA_SELECT_EXTRA_PLAYER_COMBOS` | `characters.dataSelectExtraCombos` | string | `""` | Extra team combinations shown on the S3K Data Select screen. Format is `main,sidekick1,sidekick2;main2,sidekick1`. The first character in each group is the main character; remaining entries are sidekicks. Example: `"sonic,knuckles;sonic,tails,tails;knuckles,tails"`. This only affects Data Select team choices; normal gameplay and Level Select still use `MAIN_CHARACTER_CODE` and `SIDEKICK_CHARACTER_CODE`. |

---

## Audio

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `AUDIO_ENABLED` | `audio.enabled` | bool | `true` | Master switch for all audio output (music and SFX). |
| `REGION` | `audio.region` | string | `"NTSC"` | Hardware region: `"NTSC"` (60 Hz) or `"PAL"` (50 Hz). Affects SMPS tempo timing and DAC sample rates. |
| `DAC_INTERPOLATE` | `audio.dacInterpolate` | bool | `true` | Apply linear interpolation to DAC (drum) samples. Reduces aliasing noise for a smoother sound. |
| `AUDIO_INTERNAL_RATE_OUTPUT` | `audio.internalRateOutput` | bool | `false` | Output audio at the YM2612 internal sample rate (~53 kHz) rather than the system rate. Useful for bit-accurate captures; may cause issues on some audio drivers. |
| `PSG_NOISE_SHIFT_EVERY_TOGGLE` | `audio.psgNoiseShiftEveryToggle` | bool | `true` | PSG noise LFSR clock behaviour. `true` = shift on every polarity toggle (MAME-style, brighter noise); `false` = shift on positive edges only (Genesis Plus GX / libvgm style, darker noise). |
| `FM6_DAC_OFF` | `audio.fm6DacOff` | bool | `true` | Silence FM channel 6 whenever a DAC note is active. Matches the SMPSPlay parity hack used in Sonic 2; prevents FM bleed audible during percussion. |

## Debug

| Key | YAML path | Type | Default | Description |
|-----|-----------|------|---------|-------------|
| `DEBUG_VIEW_ENABLED` | `debug.flags.debugView` | bool | `true` | Eagerly initialise the debug overlay subsystem. Required for any runtime debug keys to function. Does not show anything on-screen until debug mode is activated. |
| `EDITOR_ENABLED` | `debug.flags.editor` | bool | `false` | Allow the experimental in-engine editor overlay to be entered from gameplay with `Shift+Tab`. |
| `DEBUG_COLLISION_VIEW_ENABLED` | `debug.flags.collisionView` | bool | `false` | Draw collision sensor rays and solid object outlines over the scene at all times. |
| `LIVE_REWIND_ENABLED` | `rewind.liveEnabled` | bool | `false` | Enable held-key rewind during ordinary live level play. Uses gameplay rewind snapshots, records live input while enabled, and presents reverse audio/fade state while held. |
| `LIVE_REWIND_TAPE_COAST_ENABLED` | `rewind.tapeCoastEnabled` | bool | `false` | Enable experimental live-rewind coast after releasing the rewind key. Disabled by default, so held rewind remains one step per visual frame. When enabled, reverse audio playback is resampled to match the current rewind speed (>1.0 pitches up, <1.0 plays slow-motion). |
| `LIVE_REWIND_TAPE_COAST_MIN_STEPS` | `rewind.tapeCoastMinSteps` | number | `0.25` | Initial rewind speed (in steps per visual frame) when the rewind key is first pressed. Values below 1.0 produce a slow-motion start: the speed controller's fractional accumulator spreads each physics step across multiple visual frames. Speed then accelerates toward `LIVE_REWIND_TAPE_COAST_MAX_STEPS`. Used only when tape coast is enabled. |
| `LIVE_REWIND_TAPE_COAST_ACCELERATION` | `rewind.tapeCoastAcceleration` | number | `0.25` | Optional tape-coast acceleration in rewind steps per held frame. Used only when tape coast is enabled. |
| `LIVE_REWIND_TAPE_COAST_DECELERATION` | `rewind.tapeCoastDeceleration` | number | `0.5` | Optional tape-coast deceleration in rewind steps per released frame. Used only when tape coast is enabled. |
| `LIVE_REWIND_TAPE_COAST_MAX_STEPS` | `rewind.tapeCoastMaxSteps` | number | `4.0` | Maximum rewind steps per visual frame for optional tape-coast rewind. Values below 1.0 cap the rewind in slow-motion. Used only when tape coast is enabled. |
| `REWIND_AUDIO_HISTORY_LIMIT_TYPE` | `rewind.audioHistoryLimitType` | string | `"time"` | How the rewind audio PCM history ring is capped. `"time"` caps by `REWIND_AUDIO_HISTORY_SECONDS`; `"size"` caps by `REWIND_AUDIO_HISTORY_SIZE_MB`. Held rewind beyond the cap plays silence on develop (the audio-rewind feature branch engages the reverse resynthesizer instead). |
| `REWIND_AUDIO_HISTORY_SECONDS` | `rewind.audioHistorySeconds` | int | `60` | Seconds of stereo PCM history kept for held-rewind playback when `REWIND_AUDIO_HISTORY_LIMIT_TYPE` is `"time"`. |
| `REWIND_AUDIO_HISTORY_SIZE_MB` | `rewind.audioHistorySizeMb` | int | `10` | Megabytes of stereo PCM history kept for held-rewind playback when `REWIND_AUDIO_HISTORY_LIMIT_TYPE` is `"size"`. Stereo 16-bit at 48 kHz consumes ~192 KB/s, so 10 MB is roughly 54 s at that sample rate (~57 s at 44.1 kHz). |
| `TEST_MODE_ENABLED` | `debug.testMode.enabled` | bool | `false` | Replace the master-title game-select with the Trace Test Mode picker that lists every trace in `debug.testMode.catalogDir` and plays the chosen trace back in the live engine. Dev-only. **When `true`, `DISPLAY_ASPECT` is always forced to `NATIVE_4_3` (320×224) regardless of its configured value** — trace replay and test-mode runs are parity-critical and must always run at 320×224. |
| `TRACE_CATALOG_DIR` | `debug.testMode.catalogDir` | string | `"src/test/resources/traces"` | Directory scanned by `TraceCatalog` when `TEST_MODE_ENABLED` is true. Resolved against `user.dir`. |
| `DISCORD_RICH_PRESENCE_ENABLED` | `discord.enabled` | bool | `false` | Opt in to publishing OpenGGF menu/gameplay status through the local Discord desktop client. Disabled by default for privacy and no-ops when Discord is unavailable. |
| `DISCORD_RICH_PRESENCE_SHOW_TIMER` | `discord.showTimer` | bool | `true` | Include the current level timer in Discord Rich Presence gameplay status when presence is enabled. |
| `DISCORD_RICH_PRESENCE_SHOW_ZONE` | `discord.showZone` | bool | `true` | Include the current zone and act in Discord Rich Presence gameplay status when presence is enabled. |

### Level Editor (experimental)

The in-engine level editor (see `EDITOR_ENABLED` / `debug.flags.editor`) uses **hardcoded**
key/mouse bindings — not configurable in `config.yaml`. While playing with `EDITOR_ENABLED` true,
press `Shift+Tab` to toggle gameplay (playtest) ↔ editor.

| Input | Action |
|-------|--------|
| `Shift+Tab` | Toggle editor / playtest mode |
| Arrows | Move world cursor / nudge selection |
| `Tab` | Cycle focused region |
| `Space` | Apply primary action (place selected block) |
| `E` | Eyedrop block under cursor |
| `L` | Toggle active layer (FG / BG) |
| `Enter` / `Escape` | Descend / ascend the hierarchy |
| `Ctrl+Z` / `Ctrl+Y` / `Ctrl+S` | Undo / Redo / Save |
| Left mouse (drag) | Paint selected block (one undoable stroke) |
| Right mouse | Eyedrop hovered tile |

Bindings live in `EditorInputHandler` and are not affected by the Key Bindings entries above.

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

Invalid key names log a warning and fall back to the default binding for that key.

The tables below list each key's name, default code, and the human-readable key name for the default.

### Gameplay Controls

| Key | YAML path | Default | Key Name | Description |
|-----|-----------|---------|----------|-------------|
| `UP` | `input.player1.up` | `265` | ↑ Arrow | Look up / enter tubes. |
| `DOWN` | `input.player1.down` | `264` | ↓ Arrow | Crouch / roll / spindash charge. |
| `LEFT` | `input.player1.left` | `263` | ← Arrow | Move left. |
| `RIGHT` | `input.player1.right` | `262` | → Arrow | Move right. |
| `JUMP` | `input.player1.jump` | `32` | Space | Jump / action button. |
| `PAUSE_KEY` | `input.pause` | `257` | Enter | Pause / unpause the game. |
| `FRAME_STEP_KEY` | `debug.keys.frameStep` | `81` | Q | Advance one frame while paused. |
| `TRACE_REWIND_KEY` | `debug.traceRewind.key` | `82` | R | Hold during visual Trace Test Mode replay to rewind deterministic engine state in real time, including reverse audio presentation and restored fade snapshots. |
| `LIVE_REWIND_KEY` | `rewind.liveKey` | `82` | R | Hold during live level play to rewind deterministic gameplay state when `LIVE_REWIND_ENABLED` is true, including reverse audio presentation and restored fade snapshots. |

### Debug Navigation

| Key | YAML path | Default | Key Name | Description |
|-----|-----------|---------|----------|-------------|
| `NEXT_ACT` | `debug.keys.nextAct` | `88` | X | Skip to the next act within the current zone. |
| `NEXT_ZONE` | `debug.keys.nextZone` | `90` | Z | Skip to the first act of the next zone. |
| `DEBUG_MODE_KEY` | `debug.keys.debugMode` | `68` | D | Toggle free-fly debug movement mode (requires `DEBUG_VIEW_ENABLED`). |
| `DEBUG_LAST_CHECKPOINT_KEY` | `debug.keys.lastCheckpoint` | `67` | C | Teleport the player to the most recently activated checkpoint. |
| `LEVEL_SELECT_KEY` | `debug.keys.levelSelect` | `298` | F9 | Open the level select screen at runtime. |
| `TEST` | `debug.keys.test` | `84` | T | Generic test button used during development. |

### Super Sonic / Emerald Debug

| Key | YAML path | Default | Key Name | Description |
|-----|-----------|---------|----------|-------------|
| `SUPER_SONIC_DEBUG_KEY` | `debug.keys.superSonic` | `85` | U | Toggle Super Sonic transformation (requires `DEBUG_VIEW_ENABLED` and all emeralds). |
| `GIVE_EMERALDS_KEY` | `debug.keys.giveEmeralds` | `69` | E | Instantly award all Chaos Emeralds (debug shortcut). |

### Special Stage Debug

These keys are only active while a Special Stage is running.

| Key | YAML path | Default | Key Name | Description |
|-----|-----------|---------|----------|-------------|
| `SPECIAL_STAGE_KEY` | `debug.keys.specialStage` | `258` | Tab | Enter / exit Special Stage mode (debug). |
| `SPECIAL_STAGE_COMPLETE_KEY` | `debug.keys.specialStageComplete` | `269` | End | Complete the current Special Stage and award the emerald. |
| `SPECIAL_STAGE_FAIL_KEY` | `debug.keys.specialStageFail` | `261` | Delete | Fail the current Special Stage without awarding the emerald. |
| `SPECIAL_STAGE_SPRITE_DEBUG_KEY` | `debug.keys.specialStageSpriteDebug` | `301` | F12 | Toggle the Special Stage sprite debug viewer. |
| `SPECIAL_STAGE_PLANE_DEBUG_KEY` | `debug.keys.specialStagePlaneDebug` | `292` | F3 | Cycle Special Stage plane visibility debug modes. |

---

## Test-only system properties

These properties are read by JVM system property lookups (`-D<name>=<value>` on
the `mvn` or `java` command line) rather than `config.yaml`. They exist for
diagnostic test runs only and must remain unset in CI.

| Property | Type | Purpose |
| --- | --- | --- |
| `oggf.trace.hydrate` | Boolean (default `false`) | Diagnostic hydrate switch for trace replay tests. When `true` AND the trace's `metadata.json` declares a recorder version at or above `9.2-s2` (see `TraceMetadata.nativePreludeMode()`), the test harness snaps engine state to the recorded ROM frame-0 snapshot (player position-record buffer, sidekick CPU state, per-slot SST values) BEFORE the per-frame comparison loop begins. A run with this enabled is **NOT a valid green replay**: the switch masks the very divergences trace replay is designed to surface. Use only to isolate prelude bugs from gameplay-loop bugs. A `WARN`-level log line emits when the switch fires; `TestTraceHydrateSwitchDefault` is the CI guard that asserts the property is unset on master. |
| `openggf.trace.s3k.probes` | Boolean (default `false`) | Enables verbose S3K-specific trace replay probes (cnz cylinder, aiz boundary, etc.). Diagnostic only. |

---

## Example `config.yaml`

The following is the bundled default `src/main/resources/config.yaml`:

```yaml
# OpenGGF configuration — grouped and documented.
# Indentation is significant (YAML). This file is rewritten cleanly on save.

# ── Display ──
display:
  aspect: "NATIVE_4_3"   # Display aspect preset; resolves screen pixel width, height stays 224
  windowAutosize: true   # Derive the window size from the aspect preset at the 2x baseline
  deadzoneMode: "PROPORTIONAL"   # Camera horizontal deadzone behaviour on wide screens
  colorProfile: "RAW_RGB"   # Display-only color profile for Mega Drive palette presentation
  colorProfileToggleKey: V   # Runtime key to cycle the display color profile
  fps: 60   # Frames per second to render (changes game speed)

# ── Input ──
input:
  pause: ENTER   # Toggle pause
  player1:
    up: UP   # Player 1: look up
    down: DOWN   # Player 1: crouch/roll
    left: LEFT   # Player 1: move left
    right: RIGHT   # Player 1: move right
    jump: SPACE   # Player 1: jump
  player2:
    up: I   # Player 2: look up
    down: K   # Player 2: crouch/roll
    left: J   # Player 2: move left
    right: L   # Player 2: move right
    jump: RIGHT_SHIFT   # Player 2: jump
    start: ENTER   # Player 2: start

# ── Audio ──
audio:
  enabled: true   # Enable music and SFX
  region: "NTSC"   # Region for audio timing
  dacInterpolate: true   # DAC interpolation (smoother sound)
  internalRateOutput: false   # Output audio at the internal YM2612 rate (~53kHz)
  psgNoiseShiftEveryToggle: true   # PSG noise LFSR clock mode: true=shift on every toggle (MAME), false=positive edges (GPGX)
  fm6DacOff: true   # Mute FM6 when a note plays on it while DAC is enabled (SMPSPlay parity hack)

# ── Characters ──
characters:
  main: "sonic"   # Sprite code of the main playable character
  sidekick: "tails"   # Sprite code of the CPU sidekick; empty string disables the sidekick
  dataSelectExtraCombos: ""   # Semicolon-separated extra player combos for the data select screen

# ── ROMs ──
roms:
  sonic1: "Sonic The Hedgehog (W) (REV01) [!].gen"   # Filename of the Sonic 1 ROM
  sonic2: "Sonic The Hedgehog 2 (W) (REV01) [!].gen"   # Filename of the Sonic 2 ROM
  sonic3k: "Sonic and Knuckles & Sonic 3 (W) [!].gen"   # Filename of the Sonic 3&K ROM
  default: "s2"   # Which game to load by default

# ── Startup ──
startup:
  titleScreen: true   # Show the title screen on startup
  masterTitleScreen: true   # Show the master (game-selection) title screen on startup
  legalDisclaimer: true   # Show the legal disclaimer screen before the master title screen

# ── Rewind (live) ──
rewind:
  liveEnabled: false   # Enable held-key rewind during ordinary live play
  liveKey: R   # Key held to rewind during live play
  tapeCoastEnabled: false   # Continue rewinding with a decelerating tape-coast after key release
  tapeCoastAcceleration: 0.25   # Per-tick speed increase while tape-coast is held
  tapeCoastDeceleration: 0.5   # Per-tick speed decrease after release
  tapeCoastMaxSteps: 4.0   # Maximum rewind steps per tick
  tapeCoastMinSteps: 0.25   # Minimum rewind steps per tick; below 1.0 gives slow-motion rewind
  audioHistoryLimitType: "time"   # How the rewind audio PCM history ring is sized
  audioHistorySeconds: 60   # Seconds of PCM history kept when audioHistoryLimitType=time
  audioHistorySizeMb: 10   # Megabytes of PCM history kept when audioHistoryLimitType=size

# ── Cross-Game ──
crossGame:
  enabled: false   # Enable cross-game feature donation (e.g. S2 sprites in S1)
  source: "s2"   # Donor game for cross-game features

# ── Discord Rich Presence ──
discord:
  enabled: false   # Enable Discord Rich Presence updates
  showTimer: true   # Show the level timer in Rich Presence
  showZone: true   # Show the zone and act in Rich Presence

# ════════════════════════════════════════════
#  DEBUG  (developer tooling — safe to ignore for normal play)
# ════════════════════════════════════════════
debug:
  flags:
    debugView: true   # Show the on-screen debug HUD
    editor: false   # Allow entering the level editor from gameplay
    collisionView: false   # Draw the collision overlay
  keys:
    test: T   # Debug-only test button
    nextAct: PAGE_UP   # Advance to the next act
    nextZone: PAGE_DOWN   # Advance to the next zone
    debugMode: D   # Toggle debug movement mode
    frameStep: Q   # Step forward one frame while paused
    lastCheckpoint: C   # Teleport to the last checkpoint
    levelSelect: F9   # Open the level select screen
    superSonic: U   # Toggle Super Sonic debug mode
    giveEmeralds: E   # Give all chaos emeralds
    specialStage: TAB   # Toggle special stage mode
    specialStageComplete: END   # Complete the special stage with an emerald
    specialStageFail: DELETE   # Fail the special stage
    specialStageSpriteDebug: F12   # Toggle the special stage sprite debug viewer
    specialStagePlaneDebug: F3   # Cycle special stage plane visibility debug modes
  startup:
    levelSelectOnStartup: false   # Open Level Select on startup instead of loading the first zone
    s3kSkipIntros: false   # Skip S3K zone intro sequences (AIZ biplane, etc.)
  playback:
    moviePath: ""   # Path to a BizHawk BK2 movie for playback debugging
    toggleKey: B   # Toggle playback mode
    loadKey: N   # Load/reload the BK2 movie
    playPauseKey: M   # Toggle playback play/pause
    stepBackKey: COMMA   # Step the cursor back one frame
    stepForwardKey: PERIOD   # Step the cursor forward one frame
    jumpBackKey: LEFT_BRACKET   # Jump the cursor back by a larger interval
    jumpForwardKey: RIGHT_BRACKET   # Jump the cursor forward by a larger interval
    fastRateKey: SLASH   # Cycle playback rate (1x/2x/4x/8x)
    resetToStartKey: BACKSLASH   # Reset the cursor to the start offset
    startOffsetFrame: 0   # Starting frame offset for BK2 playback
  traceRewind:
    key: R   # Key held in Trace Test Mode to rewind deterministic engine state
  testMode:
    enabled: false   # Replace the master title with the trace picker (dev-only)
    catalogDir: "src/test/resources/traces"   # Directory scanned for traces when test mode is enabled
  crossGame:
    s1DataSelectImageGenOverride: false   # Force regeneration of the S1 data-select image cache
    s2DataSelectImageGenOverride: false   # Force regeneration of the S2 data-select image cache
    s1DataSelectImageCoordLogKey: APOSTROPHE   # Log the current camera position as an S1 data-select preview override
  window:
    width: 640   # DEPRECATED manual window width; used only when display.windowAutosize=false
    height: 448   # DEPRECATED manual window height; used only when display.windowAutosize=false
    scale: 1.0   # DEPRECATED AWT debug-viewer scale factor
```
