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
| I / K / J / L | Player 2 up / down / left / right (`input.player2.up/down/left/right`) |
| Right Shift | Player 2 action A / jump |
| Enter | Pause / unpause |
| Backspace | Player 1 start / in-game pause (engine default; add `input.player1.start` to `config.yaml` to override) |
| Right Control | Player 2 start |
| Q | Advance one frame (while paused) |

Keyboard B and C action bindings are intentionally unbound by default. All
three Mega Drive actions act as jump buttons during platforming gameplay when
bound.

In Sonic 2, these Player 2 bindings control the configured sidekick/manual-input
path; they do not enable the ROM's native two-player/competition mode. Human-P2
competition gameplay, including its monitor-break branch, is not currently
available.

## Continue screens

After Game Over, a remaining continue opens the game's countdown screen. Press
Player 1 Start (Backspace by default, or the gamepad Start button) to use it;
S3K also accepts Player 2 Start. Jump/action buttons do not accept a continue.
In Sonic 1, Start becomes available after Sonic lands on the floor. Acceptance
plays the departure animation, spends one continue and restores three lives.
S1/S2 restart from the act start; S3K retains the checkpoint. Letting the
countdown expire returns to title without spending the continue. These menu
screens do not support held gameplay rewind.

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

The master title also takes the mouse: point at a tab or button to highlight it
and click to open it (the setup chips are status only and do not respond), click a side card (or roll the wheel) to change game, click the
centre card to start it, and right-click to go back. Page dots report the
selection and are not clickable controls. The game browser, quit prompt, tools and start choices answer clicks the same way.
Settings and Game Setup accept hover and click on their rows, tabs and prompt buttons.
In the Mod Manager, hover a mod or button to highlight it and click to toggle the mod or
open the button; the wheel moves through the list (or scrolls a details page), clicking a
details or notices page closes it, a second click confirms a pending change, and
right-click goes back.

Additional gamepad bindings are hardcoded (not configurable) on the primary
connected pad:

| Position/Button | Xbox label | PlayStation label | Action |
|------------------|------------|--------------------|--------|
| North face button | Y | Triangle | Toggle debug movement mode (`debug.keys.debugMode`) when `debug.flags.debugView` is enabled |
| Left bumper | LB | L1 | Hold to rewind live gameplay (`rewind.liveKey`) |
| Right bumper | RB | R1 | Advance one frame while paused (`debug.keys.frameStep`) |
| Back button | View | Select | Open/close the per-game options panel on the main menu (stands in for `Tab` in that flow only) |
| Start | Start | Options | Pause / unpause (`input.pause`, same as Enter -- shows the "PAUSED" overlay and halts audio). Not wired to the separate silent ROM in-game pause (`input.player1.start` / Backspace). |

## Rewind

Enable live rewind in **Settings → Gameplay** or the selected game’s **Game Setup**.
The global setting is `rewind.liveEnabled`; a per-game setup can override it.
Visual Trace Test Mode uses the same default key through `debug.traceRewind.key`.

| Key | Action |
|-----|--------|
| R | Hold to rewind live gameplay (`rewind.liveKey`) |
| R | Hold to rewind visual trace playback (`debug.traceRewind.key`) |
| Ctrl + R | Rewind at half speed |
| Shift + R | Rewind at double speed |

Either left or right modifier works; holding both restores normal rewind speed.

## Zone Navigation

These shortcuts require **Debug tools** (`debug.flags.debugView`) and let you
move through the game during development or exploration.

| Key | Action |
|-----|--------|
| Page Down | Cycle to the next zone (`debug.keys.nextZone`) |
| Page Up | Cycle to the next act within the current zone (`debug.keys.nextAct`) |
| F9 | Open the level select screen (`debug.keys.levelSelect`) |

`Shift+F9` starts a gameplay input recording or opens Recordings at the hub.
Plain `F9` stops an active recording; otherwise it opens level select when debug
tools are enabled. The ring-bounds overlay uses `K`.

## Debug Overlays

These toggle visual debug information drawn over the game scene. They require
`debug.flags.debugView` to be `true` in config (it defaults to `false`).

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
| K | **Ring bounds** -- Ring collision areas |
| F10 | **Plane switchers** -- Plane switcher trigger zones |
| F11 | **Touch response** -- Object touch/collision areas |
| F12 | **Art viewer** -- Loaded sprite art atlas |
| Backtick | **Collision view** -- Level collision overlay |
| = | **Tile priority** -- Priority visualization |
| O | **Object debug** -- Object diagnostic overlay |

`P` toggles the performance overlay even when debug tools are disabled. With
debug tools enabled, left Ctrl+P copies performance statistics to the clipboard.

F12 also saves a screenshot as `screenshot_yyyyMMdd_HHmmss.png` in the working
directory. PNG encoding and disk writing run in the background, with at most
two screenshots in flight. If both slots are busy, the new request is skipped
and logged. Successful writes and failures are logged; shutdown allows up to
five seconds for accepted writes to finish.

## Debug Mode

| Key | Action |
|-----|--------|
| D | Toggle free-fly debug mode (move camera freely with arrow keys) |
| C | Reload at the furthest-right checkpoint in this act, initializing destination events without losing a life. |

The `D` mode is the engine's free-fly debug movement capability. It is not Sonic
2's native `Debug_placement_mode`: ring/item placement and the other level-wide
native placement branches are unavailable.

## Experimental Level Editor

Open **Advanced → Editor projects** at the master title. To enter from ordinary
gameplay, set `debug.flags.editor: true` in `config.yaml`. See the
[level editor guide](../creating/level-editor.md) for creation, hierarchy editing,
properties, resizing, and export limits.

| Key | Action |
|-----|--------|
| Shift+Tab | Switch between the project editor and its playtest |
| F5 | Fresh playtest of the current project at its authored start |
| F1 / controller Menu (Start/Options) | Open the editor command menu |
| Tab / north controller button | Open the brush library |
| Q / hold controller View (Select) | Open the tools wheel |
| Ctrl+S | Save the complete `.ogfproject` |
| Ctrl+Shift+E | Export to a new chosen directory |
| Ctrl+Z / Ctrl+Y | Undo / redo |
| H / J | Flip a selected block/chunk cell or object |
| G / T in Chunk view | Cycle pattern palette / toggle priority |
| = / - with an object selected | Change the selected object's subtype |
| R with an object selected | Toggle its respawn tracking |

Playtest uses a fresh runtime copy and retains the authored document and history.
It does not auto-save the project or write stock gameplay saves.

## Super Sonic / Emerald Debug

These shortcuts require **Debug tools** and honour their configured modifiers.

| Key | Action |
|-----|--------|
| E | Instantly award all Chaos Emeralds |
| U | Toggle the current character’s super transformation (requires the appropriate emeralds) |
| Shift+E | Award all Super Emeralds |
| Shift+U | Toggle the highest unlocked S3K form: Hyper Sonic, Super Tails or Hyper Knuckles |

## Special Stage Debug

These shortcuts require **Debug tools**. Tab enters or exits a Special Stage;
the remaining keys apply during a stage. Unsupported tools are
unavailable for the active game's stage provider and do not silently call a
stage no-op or consume a stage rewind boundary. The global debug-overlay
bindings remain active during the stage; F1/F3/F4/F12 can toggle their general
overlays (and F12 can take a screenshot), so those states may be visible after
leaving the stage.
Sonic 2 exposes the sprite, plane, alignment, and lag diagnostics. Sonic 1
exposes only direct movement through the normal debug-mode key. Sonic 3&K
exposes stage/layout navigation; its sprite, plane, alignment, and lag tools
are unavailable.

| Key | Action |
|-----|--------|
| Tab | Enter / exit Special Stage mode |
| End | Complete the current Special Stage (award emerald) |
| Delete | Fail the current Special Stage |
| X | Advance to the next S3K special-stage layout |
| Z | Switch between S3 and S&K special-stage layout sets (S3K) |
| F12 | Toggle the Special Stage sprite viewer (S2 only) |
| F3 | Cycle Special Stage plane visibility debug modes (S2 only) |
| F4 | Toggle the Special Stage alignment test (S2 only) |
| F1 | Toggle the Special Stage lag-compensation display (S2 only) |

## Host menu and editor navigation

Host menus repeat held directions after a short delay; confirm and Back remain single
presses. Prompt names follow the last device: Xbox A/B/Y, PlayStation
Cross/Circle/Triangle, or South/East/North for unknown controllers.

On the master-title game card, Confirm starts the selected game or resumes its
paused session. Left/Right changes game; Down enters its actions and Up enters
the engine tabs. Settings and text editors expose full manually scrollable Details with F1 or
the north controller button. Numeric values use a numeric keypad; Up from a path
field opens the file browser. Mods requires explicit Apply to save drafts.

During a game, tap **Escape** to see the *Hold to return to games* toast at the top
of the screen; hold it for one second to return to the master title. At the
master title Escape is Back: it returns to the game card, then opens **Quit OpenGGF?**.

Sonic 1, Sonic 2 and Sonic 3 & Knuckles games are paused rather than closed: the game
freezes and smoothly shrinks into a **Paused** card, showing the moment you left,
first on the game shelf.
**Resume** (or Enter on the card) zooms the picture back to full size, then carries
on from exactly that moment, music included;
**End game** closes it. Starting another game, watching a recording, changing mods or
opening an extra, trace replays, editor projects or content import asks before ending
the paused game, and **Keep it paused** is the default.

Settings and Game Setup stay open while a game is paused, and the paused card has its
own **Game Setup** button. When you resume, the game takes your changes to **rewind**,
**debug tools**, **controls** (key bindings and controller), **wide camera movement**,
the window's **automatic size** and **capture** settings, and, if you paused in a level,
the **aspect ratio**. Everything else (characters, Borrow from, ROMs, sound, region,
frame rate) is part of how the game was started and applies the next time you start
it. Each row's description says which applies: *Your paused game takes this when it
resumes* or *Applies when you next start a game*. Rewind switched on mid-game records
from the moment you resume, so you can rewind back to there but not further.
Settings show an amber **RESTART** badge only beside values changed from what
OpenGGF is running with. Saved changes keep their badge until you restart OpenGGF
or restore the running value. Game Setup changes take effect on resume or the next
game, as described in each row.

Recordings, trace replays,
editor playtests, mod scenes and standalone mod games still close when you hold Escape.

Mod scenes and standalone mod games often use a tap of Escape themselves (for
example, to open their own pause menu). While one is running, a tap reaches the mod
and shows no toast; the toast appears once Escape has been held for a quarter of a
second, and the full one-second hold still returns to the master title.

The experimental level editor opens its command menu with F1 or controller
Menu (Start/Options). The north button opens the brush library. Directions choose
a command, Confirm executes it, and Back closes the menu. It includes placement, hierarchy/focus, library/filter,
undo/redo, Editor projects, Save project as, Level properties, Resize level,
Playtest from level start/cursor, Export level, and Close project. Project forms
use the shared text editor and file browser; their final action applies the fields and Back cancels the form. Modal input suppresses
underlying painting and global playtest shortcuts.

## Knuckles in Sonic 2 title and sound-test codes

With Knuckles selected as the Sonic 2 main character and the supported lock-on
ROM dump available, enter **Up, Up, Up, Down, Down, Down, Left, Right, Left,
Right** on the KiS2 title, then hold controller **A** and press **Start** to
open level select. These are controller actions, using your configured bindings.

On the sound-test row, A increments the sound number; B or C plays it. Play
**1, 6, 7, 7, 7, 2, 1, 6** to enable all seven emeralds for the selected gameplay
start. Super Knuckles still requires at least 50 rings and a fresh jump-button
press while airborne. A second jump button can be pressed while the first is held.
Native ROM debug placement and its code are not implemented.
