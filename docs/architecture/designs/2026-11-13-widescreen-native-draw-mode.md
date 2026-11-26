# Widescreen native draw mode (sync-friendly widescreen)

Status: implemented (2026-11-13); animated phantoms and last states added 2026-11-26 (D15–D18). See the
decision log for corrections made during delivery. Extends the
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
  (or has unloaded) are drawn at their spawn position, outside the object slot system,
  unless persistently destroyed. Stationary objects play a baked animation loop; moving
  ones are frozen. When the ROM loads them they become live; when the ROM unloads them a
  stationary object keeps its last state and a moving one returns to its spawn pose.

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
- **Pose:** detached throwaway copies, built with
  `ObjectConstructionContext.withProbeConstruction` (children are not registered) and
  given services; the pose draws them through their own `appendRenderCommands`.
  `DormantPoseLoop` runs a first copy for up to 64 updates, comparing each update's
  render output (its prepared sprite tiles plus bucket/priority keys) with the first.
  If the copy never moves and its output returns to the first frame's, the pose is a
  loop: one copy per distinct frame, each stepped to that frame from a fresh
  construction and checked to reproduce it, played back cyclically on the layer's own
  presentation tick (advanced once per frame boundary, never read by gameplay).
  Otherwise (it moved, never repeated, drew uncomparable primitives, or a fresh copy
  disagreed) the pose is a single copy after exactly one update (D16). Side effects of
  every copy are discarded by one rewind `capture()`/`restore()` bracket around the
  whole probe batch, with audio muted for the bracket. A spawn with no registry entry
  (or a placeholder) has no pose; any other failure propagates (D13).
- **When:** once per layout list (a new level load or a spawn-list replacement),
  whenever the display is wider than the simulation window (D10), in slices: each frame
  boundary bakes spawns until it has spent 512 copy updates, inside its own rewind
  bracket, and the next boundary continues. Boundaries are the start of
  `LevelFrameStep.execute` and of each locked title-card iteration that does not run the
  frame step, so a level's bake normally completes during its title card (D18). Poses
  are cached by spawn; a spawn not yet baked has no dormant draw.
- **Live hand-off:** a live instance always wins. When the ROM unloads an object that
  stands at its spawn position, the retired instance and its render buckets become its
  last state (getters only: no object logic runs), drawn instead of a frozen pose; a
  looping pose keeps looping. A moving object returns to its pose at the spawn position
  (D6, D17). Last states are derived presentation: never snapshotted, and forgotten on
  any rewind restore, object reset and layout replacement, so they cannot show a state
  from a later frame.
- **Rings:** placed rings (S2/S3K stage ring list) that the native ring window has not
  loaded and that are not collected are drawn at their layout position, in the same
  style, after the live rings, on the spin frame the live placed rings use that frame,
  so a ring turning live keeps its phase (D12, D15). S1 rings are layout objects, so they
  get dormant poses like any other object; their pose draws through the ring manager's
  live spin frame too.
- **Style:** `GREYSCALE` draws through the existing ghost shader mode with alpha
  1.0 (opaque desaturated); `NORMAL` draws plainly; `HIDDEN` skips the layer.

### 4. ROM screen checks that start or end work

Render_Sprites-style "has it left the screen" tests whose result ends an object or
starts work (title-card teardown and `LoadEnemyArt`, results-element retirement and
the act handover, S1 title-card slide length, ring windows) read the gameplay screen:
the camera's width via `CameraPresentationAccess.simulationScreenWidth()` or
`PlacementViewportWidth.simulation()`. Drawing offsets that centre a 320 composition
keep the projection width. In `EDGE` the two are equal; in `NATIVE` and in recorded-run
capture (which always plays on a 320 camera) only the picture is wider (D14).

The layer never writes gameplay state, is not rewind-snapshotted (it is derived
from snapshotted placement state), and is inactive without a presentation margin, so
trace replay (always `NATIVE_4_3`) cannot observe it.

## Verification

- Unit: config parsing and defaults; `SimulationViewport` resolution;
  `insetForPresentation` word/column arithmetic; dormant candidate filtering; dormant
  rings on the live spin frame (`TestRingDrawEmission`); loop search
  (`TestDormantPoseLoop`); last states recorded for stationary objects only and forgotten
  on reset and rewind restore (`TestDormantObjectLastState`).
- ROM equivalence: the same input in `NATIVE` at 16:9 and at `NATIVE_4_3` yields
  identical player and object state every frame (S1 GHZ1, S2 EHZ1, S3K AIZ1).
- Probe isolation: after the probe batch, RNG, slot table, placement state and the
  rewind composite snapshot are unchanged. The equivalence routes also check that a
  rewind restore leaves no last state behind.
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
Many of those draws lie just outside the picture's edge. Of those poses, 10 (S1), 13 (S2)
and 16 (S3K) are animation loops, and up to 15, 28 and 4 unloaded stationary objects
held a last state at once on the same routes.

Bake cost (2026-11-26, `DormantBakeTimingProbe`, 32:9, wall clock, 7 repetitions after
2 warm-ups, median/max). Before: one copy update per pose, a single bake. After: loop
search and per-frame copies, sliced at 512 updates per frame boundary.

| Route | Before: poses, updates, bake | After: poses (looped), kept frames, updates | After: total | After: slices, worst slice |
| --- | --- | --- | --- | --- |
| S1 GHZ1 | 214, 214, 2.35 / 3.41 ms | 214 (10), 264, 12 415 | 48.5 / 54.3 ms | 24, 5.4 / 8.0 ms |
| S2 EHZ1 | 135, 135, 1.22 / 1.38 ms | 135 (13), 174, 5 959 | 23.0 / 27.1 ms | 12, 3.2 / 3.7 ms |
| S3K AIZ1 | 153, 153, 3.00 / 4.00 ms | 153 (16), 196, 9 215 | 110.8 / 117.7 ms | 18, 8.3 / 11.1 ms |

Most of the cost is stationary objects that never change: a held first frame cannot be
told from a static one without watching the whole 64-update window.

## Known gaps (accepted)

- Zone render features that size themselves from `camera.getWidth()` (e.g. some S3K
  overlay effects) draw only the native 320 band in `NATIVE`.
- A live object outside the native window that skips its own draw when
  `!isOnScreen()` is invisible in the margin (it is live, so the dormant layer does
  not draw it either).
- Camera-driven terrain edits fire at native positions; the margin can briefly show
  pre-edit tiles.
- Objects whose art is only loaded by their own spawn have no pose.
- Baked loops play on the layer's own tick, so an animated object (unlike a ring) can
  change phase when it turns live or dormant. Objects whose loop depends on clocks the
  bake does not advance (a level frame counter, the global oscillators) bake as static.
- Until a level's sliced bake finishes (normally during its title card), unbaked
  spawns have no dormant draw.

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
| D14 | 2026-11-14 | Lifetime and work-triggering screen checks read the gameplay camera's width, not the projection width; drawing keeps the projection width | Leave them on the projection width; per-site config reads | Investigation report (task-lxbBdWm6): at a 400 picture over a 320 camera the S3K title-card teardown fired `LoadEnemyArt` 2 frames late (row 36 vs 34) and results elements handed the act over 2 frames late, so recorded KosM completions never matched and `hcz_completerun`/`mgz_completerun` captures overflowed the KosM FIFO. The same delay would break `NATIVE`'s same-game claim after any title card or results screen. Ring windows also read the display width; harmless in HCZ but a real leak. |
| D15 | 2026-11-26 | **Amends D12.** Dormant rings animate: S2/S3K stage-list rings in the margins draw the spin frame the live placed rings use that frame (`stageRingFrame` of the same frame counter), and S1 ring-object poses draw through the ring manager's live spin frame (the frozen-spin hold is removed) | Keep spin frame 0; a separate presentation spin clock | User-approved design (animated phantoms). Rings share one global phase in all three games, so using the live rings' frame means a ring that turns live never changes phase. `TestRingDrawEmission` pins one shared, advancing frame across live and dormant rings. |
| D16 | 2026-11-26 | Stationary objects play baked loops. The bake runs a copy for up to 64 updates (vertical-blank argument advancing by one per update), compares prepared sprite tiles plus bucket/priority keys, and takes the shortest period seen twice in full; it keeps one copy per distinct frame, each rebuilt from a fresh construction and checked to reproduce its frame, and plays them on the layer's own presentation tick. Moving, never-repeating, uncomparable (primitive geometry) or unreproducible copies keep the single one-update pose | Stop at the first repeat of frame 0, as first briefed; snapshot/restore per-object rewind state per frame; replay recorded tiles | The first-repeat rule made every held first frame look static: 0 loops on all three routes. A period seen twice found 10/13/16 loops. Per-object rewind state needs an identity table for some fields, and replaying recorded tiles would bypass the SAT, mask and head paths, so each frame keeps a real instance drawn by its own `appendRenderCommands`. Copies stay inside the bake's single rewind bracket per slice; the equivalence routes still match 4:3 every frame. `TestDormantPoseLoop` covers static, looping, moving, never-repeating and unreproducible copies. |
| D17 | 2026-11-26 | **Narrows D6.** When the native loader retires a live object standing at its spawn position (and not destroyed for good), its instance and render buckets become its last state, drawn instead of a frozen pose; a looping pose keeps looping. Moving objects keep the spawn pose. The cache is derived: never snapshotted, forgotten on every object rewind restore (including the bake's own), object reset and layout replacement | Last-seen positions for all objects; snapshotting the cache | The ROM reloads at the spawn position, so only an object that is there can keep its look without a jump. Recording reads getters only (`getPriorityBucket`, `isHighPriority`, render parts); no update or draw runs, which `TestDormantObjectLastState` counts, and no gameplay state is written. A restore could otherwise show a state from a later frame; the equivalence routes assert none survives a restore. |
| D18 | 2026-11-26 | Bake placement: sliced across frame boundaries at 512 copy updates per boundary, and also run at the boundary of locked title-card iterations that do not run `LevelFrameStep.execute` | Leave the single bake at the first gameplay frame; a background thread | Measured (table above): the full bake grew from 1-4 ms to 23-118 ms, several frames. A 768-update slice still peaked at 15.0 ms on S3K AIZ1; 512 keeps the worst slice at 11.1 ms. Each slice is its own frame-boundary rewind bracket, so the probe contract is unchanged, and a 12-24-slice bake fits inside a title card while gameplay is frozen. A background thread is ruled out: copies mutate live managers, and ROM reads share one file-channel position. |
