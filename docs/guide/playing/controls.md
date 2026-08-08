# Controls Reference

Gameplay accepts keyboard input and standard GLFW gamepads. Keyboard bindings
can be changed in `config.yaml` (see [Configuration](configuration.md)) using
GLFW integer codes, human-readable key names such as `"SPACE"` and `"F9"`, or a
name carrying its own modifiers such as `"CTRL+SHIFT+O"` — see
[Configuration](configuration.md) for the chord syntax and which shortcuts act on
one.

## Gameplay

| Key | Action |
|-----|--------|
| Arrow Keys | Move left/right, look up, crouch/roll |
| Space | Player 1 action A / jump |
| Right Shift | Player 2 action A / jump |
| Enter | Pause / unpause |
| Backspace | Player 1 start / in-game pause (engine default; add `input.player1.start` to `config.yaml` to override) |
| Right Control | Player 2 start |
| Q | Advance one frame (while paused) |

Keyboard B and C action bindings are intentionally unbound by default. All
three Mega Drive actions act as jump buttons during platforming gameplay when
bound.

## Gamepads

Controller input is enabled by default through GLFW's gamepad API. D-pad and
left stick feed movement. Face buttons use position-based Mega Drive action
mapping:

| Position | Mega Drive action | Xbox label | PlayStation label |
|----------|-------------------|------------|-------------------|
| West | A | X | Square |
| South | B | A | Cross |
| East | C | B | Circle |

The startup disclaimer, master title, and S3K-style data select accept
controller input. On data select screens, east/C is Back; A, B, and Start
confirm.

Additional gamepad bindings are hardcoded (not configurable) on the primary
connected pad:

| Position/Button | Xbox label | PlayStation label | Action |
|------------------|------------|--------------------|--------|
| North face button | Y | Triangle | Toggle debug movement mode (`debug.keys.debugMode`) |
| Left bumper | LB | L1 | Hold to rewind live gameplay (`rewind.liveKey`) |
| Right bumper | RB | R1 | Advance one frame while paused (`debug.keys.frameStep`) |
| Back button | View | Select | Open/close the per-game options panel on the main menu (stands in for `Tab` in that flow only) |
| Start | Start | Options | Pause / unpause (`input.pause`, same as Enter -- shows the "PAUSED" overlay and halts audio). Not wired to the separate silent ROM in-game pause (`input.player1.start` / Backspace). |

## Rewind

Live rewind is only active when `rewind.liveEnabled` is `true` in `config.yaml`.
Visual Trace Test Mode uses the same default key through `debug.traceRewind.key`.

| Key | Action |
|-----|--------|
| R | Hold to rewind live gameplay (`rewind.liveKey`) |
| R | Hold to rewind visual trace playback (`debug.traceRewind.key`) |

## Zone Navigation

These shortcuts let you move through the game quickly during development or exploration.

| Key | Action |
|-----|--------|
| Page Down | Cycle to the next zone (`debug.keys.nextZone`) |
| Page Up | Cycle to the next act within the current zone (`debug.keys.nextAct`) |
| F9 | Open the level select screen (`debug.keys.levelSelect`) |

`F9` also toggles the ring-bounds debug overlay. That overlap is current engine
behavior: the level-select shortcut is configurable, while the overlay toggle is
hardcoded in the debug overlay subsystem.

## Debug Overlays

These toggle visual debug information drawn over the game scene. They require
`debug.flags.debugView` to be `true` in config (it is by default).

| Key | Overlay |
|-----|---------|
| F1 | **Debug text** -- Player position, velocity, angle, and state information |
| F2 | **Shortcuts** -- On-screen reference for available key bindings |
| F3 | **Player panel** -- Detailed player state readout |
| F4 | **Sensor labels** -- Collision sensor ray positions and directions |
| F5 | **Object labels** -- Names and positions of active objects |
| F6 | **Camera bounds** -- Current camera boundary rectangle |
| F7 | **Player bounds** -- Player collision bounding box |
| F8 | **Object points** -- Object origin and debug points |
| F9 | **Ring bounds** -- Ring collision areas |
| F10 | **Plane switchers** -- Plane switcher trigger zones |
| F11 | **Touch response** -- Object touch/collision areas |
| F12 | **Art viewer** -- Loaded sprite art atlas |

## Debug Mode

| Key | Action |
|-----|--------|
| D | Toggle free-fly debug mode (move camera freely with arrow keys) |
| C | Teleport to the last checkpoint (furthest 'right') in this act. |

## Experimental Editor

These controls are only active when `debug.flags.editor` is `true` in `config.yaml`.

| Key | Action |
|-----|--------|
| Shift+Tab | Toggle between gameplay and the experimental editor overlay |
| F5 | Restart the playtest from editor mode |

## Super Sonic / Emerald Debug

| Key | Action |
|-----|--------|
| E | Instantly award all Chaos Emeralds |
| U | Toggle Super Sonic transformation (requires all emeralds) |

## Special Stage Debug

These keys are only active during a Special Stage. Sonic 2 implements both
developer tools. In Sonic 3&K, F12 toggles the manager's persisted state but no
viewer is drawn because its viewer provider is null; F3 is a no-op. Sonic 1
leaves both shortcuts as no-ops.

| Key | Action |
|-----|--------|
| Tab | Enter / exit Special Stage mode |
| End | Complete the current Special Stage (award emerald) |
| Delete | Fail the current Special Stage |
| F12 | Toggle Special Stage sprite debug state (viewer in S2; state only/no viewer in S3K; no-op in S1) |
| F3 | Cycle Special Stage plane visibility debug modes (S2; no-op in S1/S3K) |
