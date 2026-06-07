# Controls Reference

All controls are keyboard-based. Key bindings can be changed in `config.yaml`
(see [Configuration](configuration.md)) using either GLFW integer codes or
human-readable key names such as `"SPACE"` and `"F9"`.

## Gameplay

| Key | Action |
|-----|--------|
| Arrow Keys | Move left/right, look up, crouch/roll |
| Space | Jump |
| Enter | Pause / unpause |
| Backspace | Player 1 start / in-game pause |
| Q | Advance one frame (while paused) |

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

These keys are only active during a Special Stage.

| Key | Action |
|-----|--------|
| Tab | Enter / exit Special Stage mode |
| End | Complete the current Special Stage (award emerald) |
| Delete | Fail the current Special Stage |
| F12 | Toggle Special Stage sprite debug viewer |
| F3 | Cycle Special Stage plane visibility debug modes |
