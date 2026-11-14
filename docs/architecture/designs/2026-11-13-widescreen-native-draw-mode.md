# Widescreen native draw mode (sync-friendly widescreen)

Status: implemented (2026-11-13); see the decision log for corrections made during delivery. Extends the
[widescreen support design](2026-05-30-widescreen-support-design.md).

## Problem

Today's widescreen widens the simulation as well as the picture. The camera, the
object/ring/bumper placement windows, `isOnScreen` tests, the deadzone and the
right-boundary widening all key on `SCREEN_WIDTH_PIXELS`. A 16:9 player and a 4:3
player therefore play different games: objects load earlier, on-screen-gated logic
runs earlier, and RNG/slot order can diverge. That rules widescreen out of trace
comparison, Time Attack/competition fairness, netplay and shared BK2 replays.

## Goal

A configurable draw mode in which the game simulation is exactly the 320-pixel ROM
game while the picture is as wide as the display:

- **Simulation is native.** Camera width, deadzone, boundaries, placement windows,
  `isOnScreen` and every other gameplay read of the viewport see 320. A `NATIVE`
  run at any aspect produces the same gameplay state, frame for frame, as
  `NATIVE_4_3` with the same input.
- **The picture is centred on the native window, within the level.** The drawn
  viewport is the native camera window extended by an equal inset on each side,
  `presentationLeft = cameraX - (displayWidth - 320) / 2`, then slid to stay inside the
  world the ROM camera bounds can show (`[minX, maxX + 320)`). A range narrower than the
  display (a locked arena) is centred instead. The native window is always inside the
  picture (D8).
- **Dormant objects fill the margins.** Layout objects that the ROM has not loaded
  (or has unloaded) are drawn frozen at their spawn position, outside the object
  slot system, unless persistently destroyed. When the ROM loads them they become
  live; when the ROM unloads them they return to the frozen pose.

## Configuration

| Key | Values | Default | Meaning |
| --- | --- | --- | --- |
| `WIDESCREEN_DRAW_MODE` | `EDGE`, `NATIVE` | `EDGE` | `EDGE`: today's behaviour, objects load and run to the edge of the wide screen. `NATIVE`: simulation at 320, wide picture with dormant objects. |
| `WIDESCREEN_DORMANT_STYLE` | `GREYSCALE`, `NORMAL`, `HIDDEN` | `GREYSCALE` | How dormant objects are drawn in `NATIVE`. `HIDDEN` draws terrain only. |

Both are ignored at `NATIVE_4_3` (no margin). Both are level-load timing settings
(the simulation width is fixed for a loaded level).

## Architecture

### 1. Simulation width vs display width

`SCREEN_WIDTH_PIXELS` stays the **display** width (framebuffers, projection, tilemap
column buffers, HUD, parallax buffer sizes). A new resolver,
`com.openggf.configuration.SimulationViewport`, answers the **simulation** width:
`NATIVE` ⇒ 320, `EDGE` ⇒ `SCREEN_WIDTH_PIXELS`. Simulation readers move to it:

- `Camera` width (and so the deadzone, focus snap, `isOnScreen`, render-flag
  visibility, arena projection and every `camera.getWidth()` gameplay caller).
- `PlacementViewportWidth` (object/ring/bumper windows).
- The remaining direct `SCREEN_WIDTH_PIXELS` readers in gameplay or scroll paths
  (`SwScrlGhz`, `Sonic3kZoneFeatureProvider`, `LevelManager:2068`) are classified
  in the decision log.

`Camera` additionally knows the presentation inset and presentation width so the
renderer can find the drawn viewport without consulting config. Renderers read them
through `CameraPresentationAccess`, which keeps them off the `Camera` mod API (D9).

### 2. Presentation viewport

Everything that is drawn in world space draws relative to `presentationLeft`:

- `GraphicsManager.flush()` subtracts the presentation inset from the flush camera
  (and uses the presentation width); `flushScreenSpace()` uses the presentation
  width; the native SAT raster origin uses the presentation left.
- `LevelRenderer.drawWithRenderOptions` installs, for the duration of the level
  draw, a projection camera (`x = presentationLeft`, `width = displayWidth`, the same
  pattern as `withNativeCompetitionView`) and a scroll presentation rebased by the
  inset (`LevelScrollPresentation.insetForPresentation`). Hscroll words are shifted
  by `+inset`, so `planeX = pixelX - hscroll` covers `[-inset, 320 + inset)` with the
  ROM's own per-line values; per-column vscroll is extended by repeating the edge
  columns.

Scroll handlers still compute from the native camera (they are part of the
simulation contract and drive BG collision in some zones).

### 3. Dormant object layer

A presentation-only layer, `DormantObjectLayer` (level/objects), owned by the
`ObjectManager` and drawn before live objects:

- **Candidates:** layout spawns (`getAllSpawns()`) whose x lies within the
  presentation viewport (plus a sprite margin), that have no live instance
  (`getActiveObjectForRewind(spawn) == null`) and are not persistently destroyed
  (`remembered` without `stayActive`, S3K `destroyedInWindow`, S1/S2 state bit).
- **Pose:** a frozen list of draw calls (`DormantPose`) captured from a throwaway
  copy. The copy is built with `ObjectConstructionContext.withProbeConstruction`
  (children are not registered), given services, run for one `update()`, and its
  `appendRenderCommands` output is recorded. Its side effects are discarded by a
  rewind `capture()`/`restore()` bracket around the whole probe batch, with audio
  muted for the bracket. A spawn with no registry entry (or a placeholder) has no pose; any other failure propagates (D13).
- **When:** the probe batch runs at the start of `LevelFrameStep.execute`, once per
  layout list (a new level load or a spawn-list replacement), whenever the display is
  wider than the simulation window (D10). Poses are cached by spawn.
- **Live hand-off:** a live instance always wins. When the ROM unloads the object
  it is drawn again from its cached pose at the spawn position.
- **Rings:** placed rings (S2/S3K stage ring list) that the native ring window has not
  loaded and that are not collected are drawn frozen on their first spin frame at
  their layout position, in the same style, after the live rings (D12). S1 rings are
  layout objects, so they get dormant poses like any other object; the pose draw holds
  the ring manager on the first spin frame.
- **Style:** `GREYSCALE` draws through the existing ghost shader mode with alpha
  1.0 (opaque desaturated); `NORMAL` draws plainly; `HIDDEN` skips the layer.

The layer never writes gameplay state, is not rewind-snapshotted (it is derived
from snapshotted placement state), and is inactive whenever the inset is 0, so
trace replay (always `NATIVE_4_3`) cannot observe it.

## Verification

- Unit: config parsing and defaults; `SimulationViewport` resolution;
  `insetForPresentation` word/column arithmetic; dormant candidate filtering.
- ROM equivalence: the same input in `NATIVE` at 16:9 and at `NATIVE_4_3` yields
  identical player and object state every frame (S1 GHZ1, S2 EHZ1, S3K AIZ1).
- Probe isolation: after the probe batch, RNG, slot table, placement state and the
  rewind composite snapshot are unchanged.
- Visual: a surfaceless-EGL capture at 16:9 shows terrain and dormant objects in the
  margins; demo capture for the PR.

## What the margins actually show

The ROM's own windows are generous: objects load `0x280` ahead of the camera and
unload only some way behind it (`0x80` beyond chunk rounding in S2), and rings use a
similar window. At 16:9 and 21:9 almost everything inside the picture is therefore
genuinely live and drawn in colour; dormant poses appear at the picture's outer
edges, most visibly at 32:9. Measured on the equivalence routes at 32:9 (900 frames,
holding right with jumps): S1 GHZ1 baked 214 poses, with dormant draws on 698 frames; S2 EHZ1
baked 135 poses, with dormant draws on 621 frames; S3K AIZ1 baked 153 poses, with dormant draws on 684 frames.
Many of those draws lie just outside the picture's edge.

## Known gaps (accepted)

- Zone render features that size themselves from `camera.getWidth()` (e.g. some S3K
  overlay effects) draw only the native 320 band in `NATIVE`.
- A live object outside the native window that skips its own draw when
  `!isOnScreen()` is invisible in the margin (it is live, so the dormant layer does
  not draw it either).
- Camera-driven terrain edits fire at native positions; the margin can briefly show
  pre-edit tiles.
- Objects whose art is only loaded by their own spawn have no pose.

## Decision log

Use this log when the implementation meets a conflict with the design: record the
fork, the choice and the evidence, newest last. Errata to the sections above are
recorded here and then folded into the section text.

| # | Date | Decision | Alternatives rejected | Evidence / reason |
| --- | --- | --- | --- | --- |
| D1 | 2026-11-13 | Keys `WIDESCREEN_DRAW_MODE` (`EDGE`/`NATIVE`) and `WIDESCREEN_DORMANT_STYLE`; default `EDGE` | Changing default to `NATIVE` | User naming. `EDGE` preserves existing users' behaviour. |
| D2 | 2026-11-13 | `SCREEN_WIDTH_PIXELS` remains the display width; add a simulation-width resolver | Make `SCREEN_WIDTH_PIXELS` the simulation width and add a display key | ~70 gameplay files read `camera.getWidth()`, ~8 core render files read the display width; the renderer, framebuffers and HUD already key on `SCREEN_WIDTH_PIXELS`/projection width. Fewer, more central edits. |
| D3 | 2026-11-13 | Picture is centred on the native window (equal inset) | Asymmetric (more look-ahead), or clamp to level edges | Simplest exact mapping; keeps the native window's pixels identical to 4:3. Edge clamping is presentation-only and can be added later without touching the simulation. |
| D4 | 2026-11-13 | Margins use the ROM's per-line hscroll rebased by the inset; per-column vscroll edge-extended | Re-running scroll handlers at a wider width; directed `forCinematicView` phase sampling | Scroll handlers are simulation (BG collision, events). Directed phases exist only for some handlers. Rebasing is what a wider VDP would show with the same scroll RAM. |
| D5 | 2026-11-13 | Dormant pose = throwaway copy run for one `update()`, side effects rolled back by one rewind capture/restore per probe batch | Per-object static pose descriptors; a services-only sandbox | User direction ("spawn a copy, run its setup, use that first frame"). `ObjectServices` exposes whole mutable managers, so a services sandbox cannot isolate writes; the rewind registry covers camera, RNG, objects, rings, level and zone state and is always built. One bracket per level load keeps cost bounded. |
| D6 | 2026-11-13 | Dormant objects drawn at their spawn position | Last-seen position | The ROM reloads at the spawn position; drawing elsewhere would visibly jump on reload. |
| D7 | 2026-11-13 | `GREYSCALE` default, via ghost shader mode with alpha 1.0 | New filter shader; translucent ghost | User suggestion to reuse the greyscale filter; the existing shader already gives opaque desaturation at alpha 1. |
| D8 | 2026-11-13 | **Erratum to D3.** The picture slides within the camera's `[minX, maxX + 320)` world range; narrower ranges centre on the range | Strict centring | First S2 EHZ1 capture at 21:9: strict centring showed ~104px of masked void left of x=0 for the whole level start. Sliding is presentation-only (the simulation camera is untouched) and matches the existing `NativeViewportFraming` arena centring. |
| D9 | 2026-11-13 | Renderer helpers go through non-API access classes (`CameraPresentationAccess`, `RewindPresentationProbeAccess`, `DormantObjectPresentationAccess`); only the two config constants change the Mod API pin | Public methods on `Camera`, `RewindRegistry`, `ObjectManager` | The signature snapshot showed eight new members on `@ModApi` types. Project idiom (`CameraMotionInternalAccess`) keeps engine-only helpers off the surface. |
| D10 | 2026-11-13 | The dormant layer and scroll rebase activate on "display wider than simulation", not on a non-zero inset | Gate on `inset > 0` | After D8 the inset is 0 at level start, so the bake never ran: the equivalence test's pose assertion went red (0 poses) at 32:9. |
| D11 | 2026-11-13 | Probe restores run all derived-state post-restore callbacks but Engine's `live-scheduling` callback skips them (`isPresentationProbeRestore`) | Skip all callbacks; no rewind bracket | That callback discards queued gameplay input and restarts pacing, which would make a NATIVE run diverge from 4:3 at the bake frame. The other callbacks re-derive state the restore needs. |
| D12 | 2026-11-13 | Frozen rings: uncollected stage-list rings outside the ring window are drawn on spin frame 0 in the dormant style, from `RingManager` (collected and active sets only); S1 ring objects' poses hold spin frame 0 | Leave rings to the object layer only; animate dormant rings | User request ("we should show frozen rings too"). S2/S3K rings are not objects, so the object layer never saw them. A fixed frame matches the other frozen poses. The collected `BitSet` persists across unloads, so a collected ring never reappears. |
| D13 | 2026-11-14 | **Erratum to D5.** Probe and dormant-draw failures propagate; no catch-and-skip. `PlacementViewportWidth.current()` keeps its original body and a new `simulation()` applies the draw mode. `CameraPresentationAccess` returns the camera's own window when there is no margin | Fail-closed catches around probe and draw; editing the legacy catch body | `TestBroadCatchSwallowingGuard` rejected the new broad catches and the changed legacy site (project rule: no defensive containment). Removing the catches left pose counts unchanged on all three equivalence routes, so no probe there was failing. The smoke run also showed `captureArenaMask` reading presentation values from camera mocks that stub only `getWidth`. |
