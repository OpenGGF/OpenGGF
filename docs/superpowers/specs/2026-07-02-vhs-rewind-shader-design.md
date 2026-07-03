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
- **Speed latch through release:** with tape coast disabled (the default),
  `RewindSpeedController` resets to speed 0.0 on the first non-held frame, while the
  envelope still has a 10-frame visual tail. The envelope therefore latches the last
  nonzero speed and holds it for the whole release ramp, so the tear bands keep scrolling
  as the effect fades instead of freezing. Covered by a dedicated envelope test.

### 2. `LiveRewindManager` (edit)

Owns the envelope; ticks it from the existing per-frame entry points. Exposes
`effectIntensity()` and `effectSpeed()`. `GameLoop` surfaces these to `Engine` alongside the
existing `renderLiveRewindHud` pattern.

### 3. `RewindVhsEffectPass` (new, `com.openggf.graphics.shaderlib`)

- Owns the private `DisplayShaderPipeline`. **Prewarmed, not lazy:** when
  `LIVE_REWIND_ENABLED && LIVE_REWIND_VHS_EFFECT`, the built-in classpath preset is
  compiled and activated once during engine GL initialization (alongside
  `initializeDisplayShaders()`), so the first rewind press never pays a shader-compile
  hitch — the same hitch cited when rejecting the preset-swap alternative. Cost of
  prewarming is a couple of viewport-sized FBO textures held for the session; accepted.
- Per frame with intensity > 0: `apply(...)` with dynamic uniforms
  `RewindIntensity` and `RewindSpeed`. Zero intensity → no call, zero cost.
- **Failure detection:** `DisplayShaderPipeline.apply(...)` returns void and internally
  logs and disposes itself on apply failure. The pass accepts those logging semantics and
  does not duplicate them: it latches a failed state when `activate(...)` returns false at
  prewarm, or when `pipeline.isActive()` is false after an `apply(...)` call (the
  pipeline's dispose-on-failure makes this a reliable signal). Once failed, the pass logs
  one summary warning and never retries for the session.
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
- `LIVE_REWIND_ENABLED` false, `LIVE_REWIND_VHS_EFFECT` false, or headless → pipeline is
  never activated (no prewarm, no shader compile, no GL resources).
- Non-LEVEL modes (legal disclaimer, master title, data select, ...) → the prewarmed
  pipeline stays activated but the pass never **applies**: envelope intensity only rises
  during LEVEL-mode live rewind, and zero intensity means no `apply(...)` call.
  Activation must NOT be deferred to LEVEL entry — that would reintroduce the
  first-rewind compile hitch the prewarm exists to avoid.
- No effect on trace replay, playback seeks, or user recording capture.

## Testing

- `TestRewindEffectEnvelope` — attack/release timing, instant-zero on boundary/clear,
  speed normalization, and the release-tail speed latch (JUnit 5, no GL).
- Resource sanity test — preset resource loads and declares the required uniforms (no GL).
- GL smoke test — extend the existing `TestDisplayShaderPipelineSmoke` harness
  (`GlContext.open()`): build the built-in VHS preset, assert activation succeeds, and
  apply it once with the dynamic uniforms, asserting the pipeline stays active. This
  catches GLSL syntax/link errors that a no-GL resource check cannot.
- Visuals verified manually by running the game and holding the rewind key.

## Files

New: `shaders/shader_vhs_rewind.glsl`, `RewindVhsEffectPass.java`,
`RewindEffectEnvelope.java`, `TestRewindEffectEnvelope.java` (+ resource sanity test).
Edited: `DisplayShaderPipeline.java`, `LiveRewindManager.java`, `GameLoop.java`,
`Engine.java`, config service key list, `CONFIGURATION.md`, `CHANGELOG.md`.

## Addendum (2026-07-03): rewind speed modifiers + softer tear bands

Approved follow-up after the first visual pass.

### Softer tear bands

Tuning only, same layers: in-band displacement ~25 → ~14 source px; in-band luma
static 40% → 25%; in-band chroma kill full → 70% (a ghost of the image colour
survives inside a band); band edge tear-line brightness 0.35 → 0.22; band
slightly narrower (edge smoothstep 0.045/0.06 → 0.035/0.05). Bands remain
clearly visible; gameplay stays readable through them.

### Rewind speed modifiers

- Holding `LIVE_REWIND_HALF_SPEED_KEY` (default Left Ctrl, YAML
  `rewind.liveHalfSpeedKey`) with the rewind key rewinds at 0.5x; holding
  `LIVE_REWIND_DOUBLE_SPEED_KEY` (default Left Shift, YAML
  `rewind.liveDoubleSpeedKey`) rewinds at 2.0x. Both held cancel to 1.0x
  (multiplicative). The mirrored left/right variant of a configured modifier
  key also counts (`LiveRewindManager.mirroredModifier`).
- `RewindSpeedController` gains a per-frame `setHeldSpeedMultiplier(double)`:
  the effective step rate is base speed x multiplier, fed through the
  fractional step accumulator, which now also runs in non-coast mode (half
  speed = one engine step every other frame, double = two per frame). With
  tape coast enabled the multiplier scales the coast curve's output without
  compounding into the acceleration. `currentSpeed()` reports the effective
  speed, so reverse-audio resampling and the VHS envelope's speed latch pick
  the modifier up with no further wiring. `reset()` restores the unit
  multiplier.
- The manager sets the multiplier only in the held branch; a coast tail after
  release keeps the last multiplier (tape momentum).
- Shader: the hard 2-band/3-band cutover at `speed > 2.0` is replaced by a
  third band whose amplitude fades in via `smoothstep(1.2, 2.0, speed)` —
  fully visible at double speed, absent at normal/half speed, and no band
  teleport when the modifier is pressed or released mid-rewind (this also
  resolves the band-pop noted in the final branch review).
