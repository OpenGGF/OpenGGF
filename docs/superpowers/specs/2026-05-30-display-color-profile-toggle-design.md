# Design: Display Color Profile Toggle

## Problem

OpenGGF currently expands Mega Drive CRAM values directly to full-range RGB, so colors can appear brighter and more saturated than emulator output that applies analog, gamma, or NTSC-style color treatment. Users need a quick way to compare output modes at runtime, and the last selected mode should persist in `config.json`.

## Goals

- Keep the raw ROM/CRAM palette semantics intact.
- Add a display-only color profile layer for final RGB presentation.
- Allow cycling profiles with a configurable keybind, defaulting to the `#` key where GLFW exposes it.
- Save the selected profile back to `config.json` immediately after a runtime toggle.
- Keep the feature usable outside debug mode because it is a presentation preference, not a cheat.

## Non-Goals

- No per-game palette changes.
- No changes to object art, palette ownership, palette cycling, underwater palette composition/derivation, or CRAM storage semantics.
- No emulator-specific promise of exact visual parity until measured against reference screenshots.
- No UI menu work in this pass.

## Configuration

Add two `SonicConfiguration` entries:

| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `DISPLAY_COLOR_PROFILE` | string | `RAW_RGB` | Last selected color presentation profile |
| `DISPLAY_COLOR_PROFILE_TOGGLE_KEY` | key name | `WORLD_1` | Runtime key used to cycle profiles |

`DISPLAY_COLOR_PROFILE` should accept known enum names case-insensitively and fall back to `RAW_RGB` with a warning on invalid values.

The `#` default needs care because GLFW key names vary by keyboard layout. On UK keyboards, `#` is commonly represented by one of the non-US/world keys rather than a printable `#` name. Default the config to `WORLD_1` and add an explicit `"#"` alias in `GlfwKeyNameResolver` that maps to the same key code. This is a new alias mechanism for the resolver; today it only reflects `GLFW_KEY_*` constants and strips prefixes. The binding remains editable in `config.json` for layouts where `WORLD_1` is not reachable.

## Profiles

Start with a small explicit enum:

| Profile | Behavior |
|---------|----------|
| `RAW_RGB` | Current behavior: expand 3-bit channels to full `0..255` RGB |
| `MD_ANALOG` | Hardware-style darker/nonlinear ramp, still sharp RGB output |
| `NTSC_SOFT` | Analog ramp plus mild desaturation/gamma suitable for emulator comparison |

The exact ramps can be tuned independently, but profile selection should be stable and string-backed so config files remain readable.

## Architecture

Introduce a display color conversion component near the graphics/palette layer. It should transform `Palette.Color` values when palette textures are uploaded, not when ROM palette data is parsed. This keeps existing palette animation, water palette composition, rewind palette snapshots, and trace-oriented palette comparisons operating on the same logical palette values as today.

`GraphicsManager.cachePaletteTexture(...)` and the underwater palette upload path should call the same conversion helper before writing RGB bytes into the GPU buffer. The helper should replace the duplicated RGB/alpha byte-writing loops in both paths, not sit beside them, so normal and underwater uploads cannot drift.

Any direct clear-color paths that use palette colors should also resolve through the same display profile helper so backdrop color matches tiles and sprites. The implementation should verify each clear-color path before changing it; some paths use fixed RGB values rather than palette-derived colors.

Runtime selection can live in a small controller owned by the engine or graphics services. On a `DISPLAY_COLOR_PROFILE_TOGGLE_KEY` edge press, it should:

1. Advance to the next profile.
2. Store the new profile name with `SonicConfigurationService.setConfigValue(...)`.
3. Call `saveConfig()`.
4. Force palette texture refresh/re-upload so the change is visible immediately.

The refresh mechanism should be explicit in `GraphicsManager`, not delegated to `LevelManager.reloadLevelPalettes()`. `reloadLevelPalettes()` only re-uploads the current level palette lines and misses palettes cached by objects, HUDs, menus, special screens, donor render contexts, and other call sites. `GraphicsManager` should retain source palette references by palette line as they are cached, retain the last underwater palette inputs needed to rebuild the underwater texture, and expose a `refreshAllPaletteTextures()` method that re-runs the GPU upload byte conversion for every cached line and the underwater texture. Headless mode can update the retained references without issuing GL calls.

The toggle should be global during normal rendered modes and should not require `DEBUG_VIEW_ENABLED`.

## Input Behavior

Use `InputHandler.isKeyPressed(...)` for edge-triggered cycling. The key should be resolved through the existing named-key config path, so users can replace the default with another GLFW key name or numeric code.

The feature should not consume player inputs beyond the configured toggle key. If the configured key name is unrecognised, `GlfwKeyNameResolver.resolve(...)` returns `OptionalInt.empty()` and `SonicConfigurationService.getInt(...)` falls back through its normal default-key path. If that still produces no valid key code, the toggle is effectively unbound, but the profile from config still applies at startup.

## Testing

Add focused tests for:

- Invalid `DISPLAY_COLOR_PROFILE` values falling back to `RAW_RGB`.
- Profile cycling order and persistence via `setConfigValue(...)`.
- Color conversion output for representative CRAM levels, including white, midtones, and saturated primaries.
- Normal and underwater palette texture upload using converted bytes while leaving `Palette.fromSegaFormat(...)` unchanged.
- `GraphicsManager.refreshAllPaletteTextures()` re-emitting all retained palette lines after a profile change.
- `GlfwKeyNameResolver` alias handling for `"#"` mapping to `WORLD_1`.

Rendering screenshot comparison is useful later, but the first implementation can be covered by deterministic conversion and config tests.

## Documentation

Update the playing/configuration guide to document:

- The available display color profiles.
- The runtime toggle key.
- The fact that the selected profile is saved to `config.json`.
- The keyboard-layout caveat for the default `#` binding.
