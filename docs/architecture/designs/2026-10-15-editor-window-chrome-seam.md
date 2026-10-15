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
| `editor.EditorWindowChromes` | `ServiceLoader` discovery; `-Dopenggf.editor.ui=classic` disables it |
| `EditorInputHandler.FrontEnd` / `setExternalChrome` | The chrome sees each editor tick first; logical-pixel hit regions turn off while it draws the panels |
| `InputHandlerInternalAccess.suppressGamepad` | While a chrome that owns the pads is active, Genesis/menu mappings, debug buttons and the rewind bumper see a neutral pad; physical state and prompt-family tracking remain |
| `EditorCommandPalette.extraCommands` / `actionCommands` | Host and editor commands for chromes that draw their own menus |

Without a provider the engine behaves as before. The engine never loads ImGui; the editor
repository supplies `META-INF/services/com.openggf.editor.EditorWindowChrome` and a launcher
that calls `Engine.main`. A controller-first in-engine HUD (`feature/ai-editor-controller-ui`)
is expected to implement the same seam as the built-in default.

## Open

- The seam is engine-internal host API, not Mod API, and is not covered by the signature pin.
- GraalVM native images would need the provider registered at build time; the external editor
  is JVM-only for now.
