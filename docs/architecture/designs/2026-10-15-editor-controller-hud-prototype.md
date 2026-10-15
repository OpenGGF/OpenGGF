# Controller-first editor HUD on the engine's own UI (prototype)

Date: 2026-10-15. Branch: `feature/ai-editor-controller-ui` (base `7277cef40`).
Status: prototype for review; not integrated. It is the engine's built-in
`EditorWindowChrome` (see `2026-10-15-editor-window-chrome-seam.md`); a desktop editor on the
class path replaces it and `-Dopenggf.editor.ui=classic` keeps the in-game chrome.

## Why a second prototype

The ImGui spike fixed the resolution problem but produced a desktop editor: menu bar,
docked panels, hover tooltips, small text, mouse-first. Review asked for an editor that
works from a controller and for no new dependency. This prototype keeps the spike's one
structural fix, drawing chrome at window resolution beside an integer-scaled game, and
builds everything else on code the engine already ships: `MenuPixelFont` (9x10 and compact
6x8 faces), `TexturedQuadRenderer`, `MenuRepeat`, `ControllerPromptStyle` and the physical
gamepad channel (`InputHandler.capturePhysicalInput`).

## Design rules

- **Ten-foot type.** UI units are a whole number of framebuffer pixels chosen to leave at
  least 640x360 units, so the menu font reads at 18-30 screen pixels from 720p to 4K.
- **The level is never under a panel.** The game keeps an integer scale in the stage between
  a top bar and the hotbar dock. In Block/Chunk view it steps down one scale and a workbench
  takes the freed side.
- **One button per verb, the same everywhere.** South paints/places/chooses, West picks, North
  opens the library, East goes back, bumpers move through brushes or pages, triggers zoom the
  World > Block > Chunk hierarchy, View holds the tools wheel, Menu lists every command.
- **Contextual, live prompts.** The bottom bar names the buttons that do something now, drawn
  as Xbox or PlayStation glyphs for the pad last used, or as keycaps after keyboard input.
- **Brushes live on a hotbar.** Ten slots per kind (blocks, chunks, patterns, objects), seeded
  with the level's most used entries. The highlighted slot is the brush; choosing something
  already on the bar selects it, anything else replaces the highlighted slot, so positions
  never move under the player's thumbs.
- **A held stroke is one edit.** Holding South while steering previews each block live and
  commits a single `StrokeCommand`, so one undo reverts the stroke.

## Components

| Piece | Responsibility |
|---|---|
| `editor.hud.EditorHudLayout` | UI scale, bars, stage, workbench and game viewport (pure, tested) |
| `editor.hud.EditorHudCanvas` | Batched quads, clip, pixel-font text, glyph masks in UI units |
| `editor.hud.EditorHudGlyphs` | Button glyph masks drawn in code (no image assets) |
| `editor.hud.EditorHudPad` | All buttons, sticks, analog triggers, edges and menu repeat |
| `editor.hud.EditorHud` | Modes (canvas, library, wheel, menu), hotbar, stroke, prompts |
| `editor.hud.EditorHudPainter` | Draws the frame and records pointer targets |
| `editor.hud.EditorTileAtlas` | ROM-decoded thumbnails of every block/chunk/pattern (from the spike) |
| `editor.hud.EditorHudRuntime` | The built-in `EditorWindowChrome`: activation, viewport, render, editor tick, owns the pads |

Input ownership: as the window chrome the runtime sees each editor tick first. It reports
`ownsGamepads()`, so while it is active the engine sets `InputHandlerInternalAccess.suppressGamepad`, so
Genesis mappings, menu mappings, the debug/frame-step buttons and the rewind bumper see a
neutral pad; physical state and prompt-family tracking continue. Keyboard and mouse keep
their existing bindings; the HUD adds Tab, Q, F1 and 1-0 and owns the keyboard only while a
sheet, wheel or menu is open.

## Evidence

`TestEditorHudPictures` drives the HUD with a synthetic Xbox pad, then a DualSense, then the
keyboard, over a ROM-backed S2 project at 1600x900 under surfaceless EGL, and asserts:

- RB/LB move between hotbar slots and change the controller's block brush.
- A held South steered with the D-pad paints three blocks; one undo reverts all three.
- North opens the library; D-pad moves; South chooses a different block.
- Holding View, steering right and releasing toggles the layer.
- RT enters the block under the cursor, RT again its chunk; East climbs back to the world.

It writes 13 pictures (world, painted stroke, library, wheel, menu, block, block library,
chunk, objects, object library, PlayStation glyphs, keyboard prompts).

Lessons recorded while building it:

- `LevelEditorController.focusRegion` commits pending edits, so the HUD only sets focus when it
  changes; setting it every tick split a held stroke into one undo entry per cell.
- The classic handler committed pending edits on every mouse-up tick; with a front end that
  also ends its stroke. Mouse release now finishes only the mouse stroke (`179fb8e79`).
- The 9x10 menu face has no descender room (p, g, y lose their tails), so BODY/LARGE text is
  set in capitals and mixed-case text uses the compact 6x8 face, which has descenders.
- The controller enters a chunk only with a chunk selected; RT adopts the hotbar's chunk.

## Rejected

- **Dear ImGui in the engine** (`d2b331a4c`): readable, but a mouse-first desktop editor and a
  new native dependency. It continues as the separate GPL-3.0 project
  [OpenGGF-Editor](https://github.com/OpenGGF/OpenGGF-Editor) through `EditorWindowChrome`.

## Open before integration

- Real-window check with a physical pad (no isolated display on the prototype machine).
- Persist hotbars per project; a "recent" row in the library; library filtering by usage.
- Object thumbnails from object art rather than id badges.
- Zoom and pan of the canvas.
- The editor projects menu (Tools > Editor projects) and project forms still use the classic
  logical-pixel renderer.
- Retire `EditorUiLayout` and the logical-pixel chrome once one window UI is chosen.
