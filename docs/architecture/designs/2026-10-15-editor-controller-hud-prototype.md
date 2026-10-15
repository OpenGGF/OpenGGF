# Controller-first editor HUD on the engine's own UI (prototype)

Date: 2026-10-15. Branch: `feature/ai-editor-controller-ui` (base `7277cef40`).
Status: prototype for review beside the Dear ImGui spike (`feature/ai-editor-imgui-spike`,
`d2b331a4c`); not integrated. `-Dopenggf.editor.ui=classic` keeps the in-game chrome.

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
| `editor.hud.EditorHudRuntime` | Engine seam: activation, viewport, render, `EditorInputHandler.FrontEnd` |

Input ownership: the runtime is the `EditorInputHandler` front end and sees each editor tick
first. While it is active the engine sets `InputHandlerInternalAccess.suppressGamepad`, so
Genesis mappings, menu mappings, the debug/frame-step buttons and the rewind bumper see a
neutral pad; physical state and prompt-family tracking continue. Keyboard and mouse keep
their existing bindings; the HUD adds Tab, Q, F1 and 1-0 and owns the keyboard only while a
sheet, wheel or menu is open.

## Evidence

Pending the first build and offscreen run.

## Open before integration

- Real-window check with a physical pad (no isolated display on the prototype machine).
- Persist hotbars per project; a "recent" row in the library; library filtering by usage.
- Object thumbnails from object art rather than id badges.
- Zoom and pan of the canvas, which both prototypes still lack.
- Retire `EditorUiLayout` and the logical-pixel chrome once one window UI is chosen.
