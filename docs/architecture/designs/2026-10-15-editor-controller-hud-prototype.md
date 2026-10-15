# Controller-first editor HUD on the engine's own UI (prototype)

Date: 2026-10-15. Branch: `feature/ai-editor-controller-ui` (base `7277cef40`).
Status: integrated on develop at `1259b0016`. It is the engine's built-in
`EditorWindowChrome` (see `2026-10-15-editor-window-chrome-seam.md`); a desktop editor on the
class path replaces it. The classic logical-pixel editor chrome has been retired.

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

## Review fixes

An independent static review of `179fb8e79..613007950` found two major defects and several
minor ones; the picture test's one-tick presses could not see the pad ones. Seam-side fixes are
in the chrome seam note.

- **Held controls carried into the next context.** A South still held after it chose a library
  entry, wheel tool or menu item painted the selected Block/Chunk cell on the next tick (and
  overwrote what Flip/Palette/Priority had just toggled); a button held from the projects menu
  read as a fresh press when the HUD returned. `EditorHudPad.consumeHeld` now swallows every
  control still held when a modal closes or the depth changes, until it is released;
  `resync` does the same on reactivation; the stick that steered the wheel stays latched until
  it returns to centre.
- Not adopted: the review also noted that an exception from the HUD ends the engine. Containing
  it was rejected; the HUD is engine code and a bug in it should fail like any other engine bug.
- The wheel opened on Undo, so tapping View then South undid; it now opens with nothing chosen.
- Clicks ran the pad's highlighted entry, and a pointer resting where a sheet opened moved the
  highlight; clicks now choose what they land on and opening a modal ignores the resting pointer.
  Hit areas are trimmed to the visible rows.
- Focus could rest on the undrawn in-game panes (and "Every command" offered pane navigation);
  the HUD now keeps focus on its canvas even without a pad, and the menu leaves those out.
- The tile atlas rebuilt every block, chunk and pattern sheet after each composition edit
  (a 2048x2048 block sheet for S2); it now redraws only changed chunks and the blocks that
  show them, and a palette-line change redraws only patterns.
- Smaller: blend factors and the scissor box are restored separately, the brush already in hand
  no longer evicts the most-used hotbar entry when bars are seeded, world strokes use the same
  floor division as the cursor, and spawn-palette null checks use `objectPaletteOrNull`.

`TestEditorHudControls` covers these without a ROM or GL by holding controls over several
ticks; one of its checks first shows the carried-over press acting, so it is not vacuous.
Left as is: the painter reads the editor camera through `SessionManager`, as
`EditorWorldOverlayRenderer` does.

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

## Classic chrome retirement

The HUD is the sole built-in editing UI. Removed the logical-pixel toolbar,
command strip, library browser, focused-pane rendering and hit layout, along with
their navigation state and the palette's modal UI. `EditorCommandPalette` remains
the shared action/workflow catalogue for the HUD and desktop host. World overlays,
editing actions and the block/chunk canvas focus used by both modern workbenches
remain. Project forms and content import retain their existing renderers.

The classic option and engine initialisation fallback are gone: a window-chrome
initialisation error propagates. External `EditorWindowChrome` providers still
replace the HUD through `ServiceLoader`. The desktop consumer now lists actions
directly because no classic pane actions remain to filter, and owns its palette
open/close state and F1 handling. Host commands still queue for the editor tick. Guide pictures come
from the ROM-backed HUD capture rather than the removed classic picture test.

### Retirement validation

The retirement is based on develop `1259b0016`. The task source passed production/test
compilation, 191 focused editor/input tests, the ROM-backed HUD picture capture (one
test), and 681 guards, all without failures, errors or skips. The three guide images
are the reviewed 1600×900 `TestEditorHudPictures` World/Block/Chunk outputs.

The tooling category completed all nine batches: 2,982 tests, one failure, zero
errors and three skips. `openggf.racing.e2e.TestDirectConnectEndToEnd` invocation
`fullRoundWithLatencyProxiedGuest(RaceHostImpl, Path)[1]` failed at line 151 with
`latency-proxied guest never rendered host ghost`. No racing/network files were
changed. Its standalone baseline/current pair passed two tests each. An identical
50-class ordering context passed 383 tests on each source. This does **not**
reproduce or conclusively attribute the original failure; it remains unattributed.
A final baseline command passed two tests, but nine repeated `surefire:test` goals
were deduplicated and supplied no additional coverage. No unrelated networking
fix was attempted. The tooling diagnostics were inspected and acknowledged.

Tooling skipped the opt-in HUD picture refresh, surfaceless project-form pictures,
and native slots-glass rendering. The first two were exercised successfully by
the separate capture and focused runs; native slots-glass rendering is outside
this editor retirement's coverage.

Baseline comparisons used only this task worktree: save the complete binary diff,
verify its hash, reverse-apply it, verify tracked source equals `1259b0016`, run the
bounded check, and restore/hash-check/remove the temporary patch. No branch switch,
stash, main-checkout change or extra worktree was needed. The final package starts
with a clean build to remove any baseline-only compiled classes. The desktop
provider owns its palette lifecycle and consumes canvas input while it is open;
its ROM-free input tests cover F1, releases and editor deactivation.

Required commands (all engine Maven invocations used the desktop project's private
repository via `-Dmaven.repo.local` or inherited `MAVEN_ARGS`, keeping `~/.m2` intact):

```bash
python3 tools/testing/maven_queue.py -Dmse=off test-compile
# Focused run: unset DISPLAY/WAYLAND_DISPLAY, select surfaceless EGL and the absolute S2 ROM.
python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestEditorHudControls,TestEditorHudLayout,TestEditorWindowChromes,TestEditorInputHandlerFrontEnd,TestInputHandlerGamepadSuppression,TestLevelEditorController,TestEditorCommandPalette,TestEditorCommands,TestEditorExportInput,TestEditorInteractionSafety,TestEditorRenderingSmoke,TestEditorSaveManager,TestEditorProjectPresentation,TestEditorProjectWorkspace' test
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --category tooling --rom-directory "$OPENGGF_ROM_DIRECTORY" --run
# Display-free HUD capture with the absolute S2 ROM property:
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.editor.hudPictures=true "-Dsonic2.rom.path=$SONIC2_ROM" -Dtest=TestEditorHudPictures test
# The standalone baseline/current networking pair:
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=openggf.racing.e2e.TestDirectConnectEndToEnd test
```

The desktop compatibility change is local commit `1686a71` in OpenGGF-Editor; it
is not pushed. Its build uses the private Maven repository and shared admission:

```bash
# From OpenGGF-Editor; MAVEN_ARGS points at its .m2/repository.
# OPENGGF_WORKTREE is the exported absolute task worktree path.
python3 ~/.cache/openggf-imgui-spike/bin/admitted.py --root="$OPENGGF_WORKTREE" --kind=editor-desktop-build --estimate=420 -- bash -c 'set -e; (cd "$OPENGGF_WORKTREE" && mvn -q -Dmse=off clean); scripts/install-engine.sh "$OPENGGF_WORKTREE"; mvn -q -Dmse=off package'
```
