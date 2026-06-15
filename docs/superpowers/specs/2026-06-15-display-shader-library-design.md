# Design: Runtime Display Shader Library

**Date:** 2026-06-15
**Status:** Draft design for review
**Topic:** Add a user-facing display shader library rooted at `shaders/`, discovered at runtime and cycled with configurable keys.

## Problem

OpenGGF can render accurate game pixels, but it has no user-facing post-processing layer for emulator-style presentation effects. BizHawk ships a small set of useful display shaders under `docs/BizHawk-2.11-win-x64/Shaders/BizHawk`, including scanlines, gamma, bicubic filters, and `hq2x`. The current engine shader system is oriented around internal tile/sprite/fade rendering, not user-selectable final-frame effects.

Users need a way to drop compatible shader presets into a root `shaders/` folder, have the engine discover them at runtime, and cycle forward/backward through the available options with configurable keys.

## Goals

- Add a runtime-scanned shader library rooted at a configurable folder, defaulting to `shaders`.
- Support an explicit `Off` entry plus discovered shader entries.
- Let users cycle backward and forward through the library with configurable keys, defaulting to `[` and `]`.
- Persist the selected shader by stable relative path, not by fragile list index.
- Compile/load only the selected shader, so a broken shader does not prevent startup.
- Support BizHawk-style GLSL presets well enough for the local `BizHawk/*.cgp` files that have GLSL siblings.
- Support standalone GLSL post-process shaders that follow the engine compatibility contract.
- Keep shader effects display-only; they must not affect physics, traces, ROM palette state, or gameplay timing.
- Show a short on-screen confirmation when the active shader changes.

## Non-Goals

- No HLSL/Cg compilation in the first implementation. `.hlsl` and raw `.cg` files are discoverable only as unsupported companions unless a GLSL-compatible source is available.
- No promise that every BizHawk/RetroArch shader pack will work unchanged.
- No in-engine menu UI in this pass.
- No network shader downloads or marketplace integration.
- No shader hot-reload watcher in this pass. Runtime rescan can happen on startup and optionally when cycling if the library is dirty, but file watching is future work.
- No shader effects during headless trace replay or tests unless a test explicitly initializes GL presentation.

## User Workflow

Users place shaders in a root folder:

```text
shaders/
  BizHawk/
    BizScanlines.cgp
    BizScanlines.glsl
    bsnes-gamma.cgp
    bsnes-gamma.glsl
    hq2x.cgp
    hq2x.glsl
    bicubic-fast.cgp
    bicubic-fast.hlsl
    bicubic-normal.cgp
    bicubic-normal.hlsl
  Custom/
    warm-crt.glsl
```

At startup, the engine scans `shaders/` recursively and builds a sorted list:

```text
Off
BizHawk/BizScanlines.cgp
BizHawk/bicubic-fast.cgp
BizHawk/bicubic-normal.cgp
BizHawk/bsnes-gamma.cgp
BizHawk/hq2x.cgp
Custom/warm-crt.glsl
```

Pressing `]` advances to the next entry. Pressing `[` moves to the previous entry. Selecting `Off` disables the post-process pipeline. The current selection is saved, and the screen shows a brief confirmation such as:

```text
Shader: BizHawk/BizScanlines
Shader: Off
Shader failed: Custom/warm-crt
```

## Configuration

Add display configuration keys:

| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `DISPLAY_SHADER_LIBRARY_ROOT` | string | `shaders` | Root directory scanned for user display shaders |
| `DISPLAY_SHADER_SELECTION` | string | `OFF` | Last selected shader, stored as `OFF` or a root-relative path |
| `DISPLAY_SHADER_NEXT_KEY` | key | `RIGHT_BRACKET` | Runtime key for next shader |
| `DISPLAY_SHADER_PREVIOUS_KEY` | key | `LEFT_BRACKET` | Runtime key for previous shader |
| `DISPLAY_SHADER_DEFAULT_PHASE` | enum | `PRESENTATION` | Fallback render phase for standalone shaders |

`DISPLAY_SHADER_SELECTION` should be path-normalized with forward slashes for stable config files across Windows/macOS/Linux. If the saved selection is missing at startup, fall back to `Off` and log a warning.

Playback is not currently a user-facing default flow, so shader cycling owns `LEFT_BRACKET`/`RIGHT_BRACKET` by default. To prevent double firing and keep dormant playback tooling from consuming ordinary presentation keys, implementation must change all playback-only key defaults to unbound. Users who actively use playback can rebind those controls in config.

The existing YAML config emitter should place these under `display`:

```yaml
display:
  shaderLibraryRoot: shaders
  shaderSelection: OFF
  shaderNextKey: RIGHT_BRACKET
  shaderPreviousKey: LEFT_BRACKET
  shaderDefaultPhase: PRESENTATION
```

`ConfigCatalog` implementation requirements:

- Insert the new keys in the existing normal `display` section, before every `debug.*` key, so debug sections remain contiguous and last.
- Register `DISPLAY_SHADER_DEFAULT_PHASE` with `ofEnum(...)` and allowed values `SCENE`, `PRESENTATION`, and `FINAL`.
- Register `DISPLAY_SHADER_SELECTION` and `DISPLAY_SHADER_LIBRARY_ROOT` as free-form strings.

## Discovery Rules

`DisplayShaderLibrary` scans the configured root recursively. If the root is missing, the library contains only `Off`.

Selectable entries:

1. `.cgp` preset files.
2. Standalone `.glsl` files that are not already paired with a discovered `.cgp` preset of the same basename.

Ignored as standalone entries:

- `.hlsl`
- `.cg`
- `.txt`
- files in hidden directories
- files outside the configured root after path normalization

Sort order is stable, case-insensitive, and root-relative. `Off` is always index `0`.

The scan should produce metadata only. It must not compile every shader during startup. Compilation happens when an entry is selected. This means `.cgp` files that only have HLSL/Cg sources, such as the local bicubic presets, still appear in the library and fail gracefully when selected until a GLSL source is added.

The root `shaders/` folder is user-supplied runtime content. The implementation should add `/shaders/` to `.gitignore` by default. Sample shaders must not be committed at the repo root unless their license, attribution, and redistribution terms have been reviewed and documented.

## Preset Loading

`DisplayShaderPresetLoader` accepts two sources:

### `.cgp`

Parse a focused subset first:

| Field | Behavior |
|-------|----------|
| `shaders` | Number of passes |
| `shaderN` | Source path for pass `N` |
| `scaleN` | Integer output scale relative to previous pass/source |
| `scale_typeN` | Supports `source` and `viewport` initially; missing value defaults to `source` |
| `filter_linearN` | Optional texture filtering override |

The local BizHawk `.cgp` files reference `.cg` names, e.g. `shader0 = BizScanlines.cg`, while GLSL siblings exist beside them. The loader should resolve in this order:

1. Exact referenced path if it is `.glsl`.
2. Same basename with `.glsl` beside the `.cgp`.
3. Unsupported, with a clear warning that HLSL/Cg is not compiled.

Missing `scale_typeN` must default to `source`, matching the Cg/RetroArch preset behavior used by `BizScanlines.cgp` and `hq2x.cgp` (`scale0 = 2`). Treating a missing `scale_typeN` as `viewport` would change scanline/scaler density and is not acceptable. `scale_typeN = viewport` is supported for presets such as the local bicubic `.cgp` files, although those specific files remain unsupported until GLSL sources exist. `absolute` scaling is out of scope for the first pass and should produce a clear unsupported-preset error rather than silently falling back.

### Standalone `.glsl`

Treat as a single-pass preset. It uses `DISPLAY_SHADER_DEFAULT_PHASE` unless the file later grows an engine-specific metadata comment.

Supported GLSL shapes:

- BizHawk combined files with `#ifdef VERTEX` and `#ifdef FRAGMENT` sections.
- Fragment-only files that sample `s_p` or `SceneTexture`; these use the engine's fullscreen textured quad vertex shader.

## Render Phases

The pipeline supports explicit insertion phases:

| Phase | Placement | Use |
|-------|-----------|-----|
| `SCENE` | After scene/sprites are rendered, before HUD and fade | Pixel scalers such as `hq2x` |
| `PRESENTATION` | After HUD/fade, before post-fade diagnostics/debug overlays | Default for scanlines, gamma, CRT effects |
| `FINAL` | After diagnostics/debug overlays, before screenshot/present | True monitor simulation, opt-in |

Default behavior should be `PRESENTATION`. This keeps trace/debug diagnostics readable while applying display effects to the normal game presentation. Presets can override this later through metadata; for the first pass, `.cgp` presets use the configured default unless a hardcoded compatibility table marks a scaler such as `hq2x` as `SCENE`.

## Rendering Architecture

Introduce:

- `DisplayShaderLibrary`
- `DisplayShaderPresetRef`
- `DisplayShaderPresetLoader`
- `DisplayShaderPreset`
- `DisplayShaderPass`
- `DisplayShaderPipeline`
- `DisplayShaderController`

`GraphicsManager` owns the pipeline and initializes it when GL is available. `Engine.display()` asks the pipeline to begin/end capture at the configured phase and composite the final texture back to the viewport.

The pipeline uses ping-pong FBOs:

```text
viewport render -> capture FBO -> pass 0 FBO -> pass 1 FBO -> default framebuffer
```

FBO textures should use `GL_NEAREST` by default to preserve pixel edges. `filter_linearN` may request `GL_LINEAR` per pass for bicubic-style shaders when needed.

Reuse the existing `FboHelper` and `QuadRenderer` patterns instead of duplicating FBO lifecycle or full-screen draw setup. The pipeline may need a new textured-quad renderer for BizHawk combined vertex/fragment shaders, but FBO creation, cleanup, and viewport save/restore should stay on the shared helper path.

The pipeline should resize FBOs when the viewport changes. FBO dimensions are derived from:

- source logical size: `SCREEN_WIDTH_PIXELS x SCREEN_HEIGHT_PIXELS`
- current viewport size: `viewportWidth x viewportHeight`
- `.cgp` `scaleN` and `scale_typeN`

## Shader Compatibility Contract

The engine should provide BizHawk-style aliases so compatible shaders need minimal edits:

```glsl
uniform sampler2D s_p;
uniform sampler2D SceneTexture;

uniform mat4 MVPMatrix;

uniform struct {
    vec2 video_size;
    vec2 texture_size;
    vec2 output_size;
} IN;
```

For combined BizHawk GLSL, the post-process quad must provide:

```glsl
in vec4 VertexCoord;
in vec2 TexCoord;
```

This is required for shaders like `hq2x.glsl`, which compute neighboring sample coordinates in the vertex stage. The existing `shader_fullscreen.vert` that relies only on `gl_VertexID` is still useful for fragment-only engine shaders, but BizHawk-compatible combined shaders need a textured quad vertex format with attributes.

Uniform mapping:

| Uniform | Value |
|---------|-------|
| `s_p` | previous pass texture |
| `SceneTexture` | same texture as `s_p` |
| `MVPMatrix` | identity/fullscreen transform unless a shader requires otherwise |
| `IN.video_size` | logical source game size |
| `IN.texture_size` | previous pass texture size |
| `IN.output_size` | current pass output size |

## Error Handling

Shader errors must be recoverable:

- Missing root folder: library is `Off` only.
- Missing saved selection: warn, select `Off`.
- Unsupported preset source: keep entry in the list but fail selection with a notification.
- Compile/link failure: log shader info log, select `Off`, and continue rendering normally.
- Runtime FBO failure: disable pipeline and continue rendering normally.

Cycling should skip entries that are known unsupported in the current process only after they fail once. A later rescan/startup can try them again.

## Notifications

Follow the `DisplayColorProfileController` pattern for notification state: a short-lived `notificationText` with a frame countdown, rendered from `Engine` through the existing pixel-font path. Shader and color-profile notifications must not occupy the exact same slot without stacking. If both are visible, stack them vertically or route them through a tiny shared display-notification renderer.

## Screenshots and Capture

F12 screenshots currently happen after normal rendering. By default, screenshots should capture the display shader output because it is what the user sees. Visual-regression/image-comparison tests remain stable because the default selection is `Off`.

Trace/video capture should keep its current parity behavior unless the capture mode explicitly asks for presentation shaders. This avoids accidentally changing comparison-oriented trace artifacts.

## Testing

Add JUnit 5 coverage for non-GL logic:

- Library scan returns `Off` plus sorted `.cgp` and unpaired `.glsl` entries.
- Hidden/outside-root files are ignored.
- `.cgp` parser reads `shaders`, `shaderN`, `scaleN`, `scale_typeN`, and `filter_linearN`.
- Missing `scale_typeN` defaults to `source`, while explicit `viewport` is honored.
- `.cgp` source resolution maps `BizScanlines.cg` to sibling `BizScanlines.glsl`.
- `.cgp` presets without a GLSL source remain selectable metadata entries but fail selection gracefully.
- Saved selection resolves by relative path and falls back to `Off` when missing.
- Controller cycles forward/backward, wraps around, persists selection, and shows notification text.
- Controller ignores unbound previous/next keys.
- All playback-only key defaults are unbound so shader cycling does not double-fire with dormant playback tooling.
- `DISPLAY_SHADER_DEFAULT_PHASE` is catalogued as an enum with the exact allowed values `SCENE`, `PRESENTATION`, and `FINAL`.
- `display.*` shader keys emit before the debug block.
- Shader compatibility metadata computes `video_size`, `texture_size`, and `output_size`.

Add focused GL smoke coverage where practical:

- Compile one fragment-only test shader.
- Compile one combined BizHawk-style test shader with `VERTEX`/`FRAGMENT` sections.
- Verify a selected bad shader does not abort startup and leaves rendering disabled.

These GL smoke tests cannot use the standard headless harness. They should either create an explicit GLFW/OpenGL context and skip when unavailable, or remain as manual/dev-only checks until the project has a reliable GL test fixture.

Render-order tests should assert the pipeline is inserted before or after fade/diagnostics according to phase, similar to the existing post-fade diagnostic guard.

## Documentation

Update `CONFIGURATION.md` with:

- `display.shaderLibraryRoot`
- `display.shaderSelection`
- `display.shaderNextKey`
- `display.shaderPreviousKey`
- `display.shaderDefaultPhase`
- The default `shaders/` directory layout.
- The GLSL compatibility contract.
- The HLSL/Cg limitation.
- The fact that root-level `shaders/` is user-supplied and gitignored unless sample shader licensing is explicitly handled.
- The recovery behavior for broken shaders.

Add a short note that repository-internal shaders under `src/main/resources/shaders` are engine shaders, while root-level `shaders/` is the user display shader library.

Because this change makes all playback-only key defaults unbound, update the existing checked-in config artifacts so the committed reference does not contradict the new code defaults:

- `src/main/resources/config.yaml` — set the `debug.playback.*Key` entries (`toggleKey`, `loadKey`, `playPauseKey`, `stepBackKey`, `stepForwardKey`, `jumpBackKey`, `jumpForwardKey`, `fastRateKey`, `resetToStartKey`) to the unbound representation, and add the new `display.shader*` keys.
- `CONFIGURATION.md` — the embedded sample `config.yaml` repeats the same playback key defaults; update those entries to match, and note that playback controls are unbound by default and must be rebound to use playback.

## Implementation Notes

- Use root-relative paths in config and notifications.
- Do not copy shader source into `src/main/resources`; user shader files are external runtime content.
- Keep the first implementation conservative: `.cgp` plus GLSL, single/multipass FBO chain, no file watcher.
- Avoid branching shader behavior on game module; this is a display layer and should be game-agnostic.
