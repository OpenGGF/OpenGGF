# Widescreen boss arenas: signal-static side mask

Status: presentation prototype for review, not engine behavior. Separate local
branch `codex/ssz-arena-static-demo`, based on develop `40d55783c`. The level
bring-up worktree remains independent. Do not merge or enable this prototype as
a finished gameplay feature.

## Problem and agreed direction

SSZ's original boss arenas occupy a 320-pixel camera view. At 800 pixels, keeping
original player bounds exposes large areas that Sonic cannot safely enter.
Invisible collision boundaries feel arbitrary; adding masonry walls conflicts
with SSZ's open-sky setting. Expanding the arena changes boss reach, attack timing,
and traversal. Zooming either crops vertical action or distorts the pixel art.

Keep the original fight geometry and signal the restricted view through quiet
TV static in the extra side space. Centre the original view during the encounter.
The user proposed matching the existing rewind shader's noise character.

## Presentation

- Show a centred, unchanged 320-pixel scene inside an 800-pixel viewport: demo
  interval `[240,560)`. At native width there is no effect. Other widths derive
  their margins from the active camera presentation, not these demo constants.
- Fade static into the extra width over 0.75 seconds. Full opacity beyond a
  12-pixel feather outside the active rectangle hides unusable scenery. Feather
  pixels must never enter the original view. The active area's pixels, scale,
  colours, and camera sampling remain unchanged.
- Use dark, slightly cool monochrome grain, updated at 12 Hz, plus a faint slow
  moving tape band. Borrow `hash21` and the band vocabulary from
  `src/main/resources/shaders/shader_vhs_rewind.glsl`; do not apply that effect's
  full-screen wobble, chroma split, bright dropouts, or horizontal displacement.
  This is an original presentation adaptation, not Genesis behavior.
- Keep HUD, lives, menus and accessibility overlays readable. The preview footage
  already places its HUD within the central rectangle. A production pass must
  run before HUD composition; merely masking a finished frame is insufficient
  when a user's HUD layout occupies the wings.
- Dissolve the mask when the arena's player bounds actually reopen. Boss HP
  reaching zero alone is not enough: escapes, gated pads, results and subsequent
  phases may still own the restriction. No TV hiss or other new audio.

The preview's lock/release animation runs on a five-second demonstration clock.
It does **not** depict an actual defeat-triggered unlock. Its two supplied clips
are declared checkpoint setups with 40 rings and neutral input.

## Proposed engine ownership

Expose a semantic arena-presentation state through the existing game/zone
presentation provider: native camera rectangle, active/transition state and
style. Shared rendering must not inspect SSZ identifiers or infer a boss arena
from a coincidental equality of camera min/max. Zone events retain authority over
camera locks, player bounds and transitions. Rendering never changes them.

A render-manager-owned mask pass consumes this state after world composition and
before HUD/fade/display processing. Inspect the actual draw pipeline when wiring
it: today's rewind VHS effect runs after the screen fade and before the user's
presentation shader, so its full-screen insertion point cannot simply be reused.
Allow the user's CRT/display shader to process the resulting signal normally.

Capture event state and the fade envelope for rewind. Advance the envelope on
executed gameplay passes; pause freezes it. A deterministic presentation clock
may drive noise, but drawing must not consume gameplay RNG. Rewind restores the
boundary/envelope; fresh loads and respawns clear or restore their owning event
state. Multiple active boss phases must not fight for ownership.

The camera's centred framing must be settled before the mask becomes opaque.
The mask is not a substitute for bounding scripted camera pans. In particular,
Mecha's later pan must still respect the original native arena bounds.

## Prototype and reproduction

`tools/visuals/ssz_arena_static_demo.py` composites only the side rectangles over
verified gameplay movies. `ssz_arena_static_demo.html` supplies local playback,
encounter selection, an original/static toggle preserving playback time, and a
replay button. Python requires NumPy and `ffmpeg` on PATH. No runtime assets are
committed; outputs belong outside the repository.

Example (substitute the external source/output paths):

```sh
python3 tools/visuals/ssz_arena_static_demo.py \
  --ghz /absolute/path/ghz-800/capture.mp4 \
  --mtz /absolute/path/mtz-800/capture.mp4 \
  --out /absolute/path/arena-static-demo
```

Open `index.html` beside the generated movies. `provenance.json` records source
hashes and validations. Each source must be a verified five-second, 60fps,
800x224 viewport recording (integer-upscaled input is supported). The renderer
asserts exact preservation of the centre before video encoding on all 600 frames,
checks clear fade endpoints and opaque outer wings, and fully decodes both
finished MP4s. Lossy MP4 encoding is not a pixel-parity oracle; the PNGs retain
the exact composed pixels.

Demo archive: `$HOME/Videos/OGGF/ssz-arena-static-demo-20260924`.
Footage is from the separate bring-up campaign's corrected GHZ/MTZ Eggmobile
captures. This branch does not include or duplicate those engine changes.

## Acceptance before engine integration

Review the moving prototype for boundary clarity, distraction, and apparent
playable width. Then integrate one SSZ replica fight behind an explicit style
setting before extending it to other encounters. A low-motion solid/dim variant
should be available if animated noise is uncomfortable; it is not implemented by
this prototype's original/static toggle.

Verify all five viewport presets, actual entry/exit event ownership, boss phase
changes, camera shake/pans, death/respawn, act loads, pause and rewind. Assert
unchanged player bounds and physics, native-width no-op behavior, no noise inside
the arena, and readable HUD for every supported layout. Test world/HUD/fade/CRT
ordering in real GPU output. Run relevant graphics and rewind guards and the
repository's combined change-based validation for the eventual engine change.

No engine tests are claimed for this standalone visual study. Integration,
configuration, event wiring and this acceptance matrix remain future work.

## Prototype verification — 2026-09-24

Generated both 300-frame movies; all 600 centre-preservation assertions and
full MP4 decode checks passed. Inspected both composed PNGs. Browser playback
loaded successfully; original/static switching, encounter selection and replay
were exercised. Python compilation, JavaScript syntax and Git whitespace checks
passed. These checks validate the demo only, not engine integration.
