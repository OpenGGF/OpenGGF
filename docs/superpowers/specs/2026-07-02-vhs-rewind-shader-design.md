# VHS Picture-Search Rewind Shader — Design

**Date:** 2026-07-02
**Status:** Approved
**Scope:** Live held-key rewind only (`LiveRewindManager`), not debug/trace playback seeks.

## Goal

While live rewind is active, present the frame like a VCR in picture-search (rewind/review)
mode: scrolling horizontal noise/tear bands, per-line jitter, chroma bleed, tape dropouts,
head-switching noise, and vertical wobble. Full "picture-search" intensity — gameplay stays
readable between the bands. The effect is the video sibling of the existing reverse audio
presentation (`AudioManager.beginReverseAudioPresentation`) and reverse fade presentation
(`FadeManager.beginReversePresentation`).

The existing post-fade `REWIND <frame>` HUD label is kept unchanged and stays crisp
(it renders after the effect pass).

## Architecture (Approach A — dedicated engine-owned post-process pipeline)

A second, engine-owned `DisplayShaderPipeline` instance runs a built-in single-pass VHS
fragment shader. It is applied in `Engine.display()` **after** `uiPipeline.renderFadePass()`
and **before** `applyDisplayShaderPhase(ShaderPhase.PRESENTATION)`, so:

- The tape damage hits scene + HUD + fade output.
- The user's selected display shader (e.g. a CRT preset) then renders the damaged signal —
  the physically authentic order (tape artifacts are in the signal; the TV displays them).
- The user's shader selection, picker, and persistence are untouched.
- Gated on `!userRecordingSceneSuppressed` so demo-reel capture is unaffected.

Rejected alternatives: `SpecialRenderEffectRegistry` (zone/gameplay-scoped, runs before
HUD/fade, no fullscreen capture machinery); temporarily swapping the user's display-shader
preset (stomps user selection, compile hitch on engage, tangles with picker/failure logic).

## Components

### 1. `RewindEffectEnvelope` (new, `com.openggf.game.rewind`)

Small, plain-JUnit-testable intensity envelope:

- **Attack:** 0 → 1 over 4 frames when rewind engages (fast, like a VCR head-speed change).
- **Release:** 1 → 0 over 10 frames after rewinding stops (covers the coast-after-release
  window).
- **Instant zero** on `clear()`, rewind boundaries, and mode exit — every existing
  `LiveRewindManager` cleanup path resets it.
- Exposes normalized speed derived from `RewindSpeedController.currentSpeed()` so a longer
  hold (faster tape) drives a busier effect.

### 2. `LiveRewindManager` (edit)

Owns the envelope; ticks it from the existing per-frame entry points. Exposes
`effectIntensity()` and `effectSpeed()`. `GameLoop` surfaces these to `Engine` alongside the
existing `renderLiveRewindHud` pattern.

### 3. `RewindVhsEffectPass` (new, `com.openggf.graphics.shaderlib`)

- Owns the private `DisplayShaderPipeline`; activates lazily on the first frame with
  intensity > 0, loading the built-in preset from the classpath.
- Per frame with intensity > 0: `apply(...)` with dynamic uniforms
  `RewindIntensity` and `RewindSpeed`.
- On activation/apply failure: log one warning, mark failed, never retry (rides the
  pipeline's existing disable-and-log path).
- Disposed with other GL resources at shutdown.

### 4. `DisplayShaderPipeline` (edit)

Add an `apply(...)` overload taking `Map<String, Float>` per-frame dynamic uniforms, merged
after static preset `parameterValues`. Existing callers unchanged.

### 5. `shader_vhs_rewind.glsl` (new, `src/main/resources/shaders/`)

Single fragment-only pass, GLSL 410, source-pixel space via `TextureSize`. Uniforms:
`Texture`, `TextureSize`, `FrameCount`, `RewindIntensity`, `RewindSpeed`. Hash noise seeded
by `FrameCount`. All layers scale with `RewindIntensity`:

1. **Picture-search tear bands** — 2 wide horizontal noise bands (3rd appears at high
   `RewindSpeed`) scrolling vertically at a rate proportional to rewind speed. Inside a
   band: ragged per-line horizontal displacement up to ~25 source px, ~40% luma static,
   chroma fully killed. Band edges get a bright 1–2 px tear line.
2. **Per-scanline jitter** — ±1.5 px horizontal offset per line, everywhere.
3. **Chroma fringing + desaturation** — R sampled ~+1.5 px, B ~−1.5 px, global ~12%
   desaturation.
4. **Tape dropouts** — sparse bright horizontal dashes (1–2 per frame), not single-pixel
   salt noise.
5. **Head-switching strip** — bottom ~2.5% of the frame: strong displacement + static.
6. **Vertical wobble** — slow sub-pixel sine, ~0.5 px.

No scanlines/curvature — that is the user's CRT display shader's job; this pass simulates
the tape, not the TV.

## Configuration

New boolean key `LIVE_REWIND_VHS_EFFECT` mapped to YAML path `rewind.vhsEffect` in
`ConfigCatalog` (matching `LIVE_REWIND_ENABLED` → `rewind.liveEnabled`,
`LIVE_REWIND_TAPE_COAST_ENABLED` → `rewind.tapeCoastEnabled`), default `true` in
`SonicConfigurationService`; only meaningful when live rewind is enabled. Documented in
`CONFIGURATION.md`.

## Error handling

- Shader compile/apply failure → warn once, effect permanently off for the session;
  gameplay and the user's display shader unaffected.
- Config off, headless, or non-LEVEL modes → pass never activates.
- No effect on trace replay, playback seeks, or user recording capture.

## Testing

- `TestRewindEffectEnvelope` — attack/release timing, instant-zero on boundary/clear,
  speed normalization (JUnit 5, no GL).
- Resource sanity test — preset resource loads and declares the required uniforms (no GL).
- GL compile/visuals verified manually by running the game and holding the rewind key.

## Files

New: `shaders/shader_vhs_rewind.glsl`, `RewindVhsEffectPass.java`,
`RewindEffectEnvelope.java`, `TestRewindEffectEnvelope.java` (+ resource sanity test).
Edited: `DisplayShaderPipeline.java`, `LiveRewindManager.java`, `GameLoop.java`,
`Engine.java`, config service key list, `CONFIGURATION.md`, `CHANGELOG.md`.
