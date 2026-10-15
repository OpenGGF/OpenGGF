# Editor window chrome extension point

Date: 2026-10-15. Branch: `feature/ai-editor-window-chrome-seam` (base `7277cef40`).

## Decision

The Dear ImGui editor spike (`feature/ai-editor-imgui-spike`, `d2b331a4c`) produced a
mouse-first desktop editor. Review kept the engine free of a UI-toolkit dependency, kept the
in-engine editor as the controller-friendly one, and moved the ImGui editor to its own GPL-3.0
repository, [OpenGGF-Editor](https://github.com/OpenGGF/OpenGGF-Editor), which depends on the
engine. This branch adds only the seam that such an editor needs.

## Seam

| Piece | Role |
|---|---|
| `editor.EditorWindowChrome` | Lifecycle (init with GL, per-frame activate, game viewport, render after the final display pass, close), window input filters, editor-tick front end, optional gamepad ownership |
| `editor.EditorWindowChromes` | `ServiceLoader` discovery; an external provider replaces the built-in controller HUD |
| `EditorInputHandler.FrontEnd` | The chrome sees each editor tick first; the game viewport is always world canvas |
| `InputHandlerInternalAccess.suppressGamepad` | While a chrome that owns the pads is active, Genesis/menu mappings, debug buttons and the rewind bumper see a neutral pad; physical state and prompt-family tracking remain |
| `EditorCommandPalette.extraCommands` / `actionCommands` | Host and editor commands for chromes that draw their own menus |

Without an external provider the engine uses its controller-first HUD. The engine never
loads ImGui; the editor repository supplies `META-INF/services/com.openggf.editor.EditorWindowChrome` and a launcher
that calls `Engine.main`. The HUD implements this seam as the built-in default.
Initialisation errors propagate; no classic chrome fallback remains.

## Review

An independent review of `179fb8e79` led to three changes:

- Shift+Tab playtest committed only the mouse stroke before saving; it now finishes the front
  end's pending edit as well, as F5 and project playtest already did.
- Host calls belong in `update`. The ImGui editor ran Playtest, Save and project commands while
  drawing, i.e. after the frame's final display pass; it now queues them for its next tick.
- `LevelEditorController.objectPaletteOrNull` and `EditorCommandPalette.actionCommands(dispatch,
  include)` let chromes test for spawn editing without catching and select their action menus.
  Classic pane actions and their `drivesInGamePanes` filter were subsequently removed when the
  HUD became the sole built-in UI.

Rejected: wrapping every per-frame chrome call to catch exceptions and fall back to the in-game
chrome. Nothing was known to throw; the built-in HUD is engine code and an external editor is an
explicit install, so a bug in either should fail like any other engine bug
(`Engine.runFrameWithModAbort` deliberately rethrows non-mod failures).

## Open

- The seam is engine-internal host API, not Mod API, and is not covered by the signature pin.
- GraalVM native images would need the provider registered at build time; the external editor
  is JVM-only for now.
