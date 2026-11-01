# Presentation interpolation

Status: design synthesized 2026-10-31; implementation on `feature/ai-presentation-interpolation`
(stacked on `feature/ai-live-frame-pacing`, PR #243). Grounding read at `36684ade1`.

## Problem

Gameplay runs fixed 60/50 Hz region services. The live-frame-pacing work locks one service to one
picture on ~60 Hz panels and draws tick-gated pictures elsewhere. On fixed 75/144/165 Hz panels
without VRR a picture can only change at tick rate, so motion judders. Interpolation draws, at each
refresh, a picture whose continuous positions (camera and scroll, players, objects) are blended
between the two latest **displayed** generations by `alpha`. It is render-only and opt-in
(`display.interpolation`, default off).

Constraints found in grounding:

- Sprite tiles are already screen-relative. `SpritePresentation.Builder` subtracts the collection
  camera; replay adds the live camera and `GraphicsManager.flush()` subtracts it again, so changing
  the replay camera alone moves nothing.
- S3K displays the *published* table, one tick behind the prepared one, and lag phases do not
  publish (`LevelSpritePresentation`, `LevelFrameStep`). S1/S2 collect their sprite frame during each
  draw from live state (`LevelRenderer.renderSpriteObjectPass`).
- Tiles carry no cross-tick identity. `Subject` is empty or `name:slot`. `ObjectRefId` (creation
  ordinal) and `PlayerRefId` exist but never reach tiles. About 1,050 object files emit tiles.
- Terrain, the priority FBO, the waterline and the background read one `currentScrollPresentation`
  seam through the `presentation*()` accessors.
- Capture pictures double as window pictures (pinned by `TestLiveFrameRuntime`). Offline tools call
  the renderer directly. Reverse-audio restores skip the scheduler's `restoreEpoch`.
- Draw purity is guarded by `TestDrawPathFieldWriteGuard` and `TestRepeatedPictureDrawPurity`.
- `@ModApi` types (`Camera`, `GraphicsManager`, `SpriteManager`, `ObjectManager`,
  `TilemapGpuRenderer`, `SonicConfiguration`) must not gain engine-internal public members. The new
  `SonicConfiguration.DISPLAY_INTERPOLATION` constant *is* an API addition and goes through the
  candidate pin workflow.

## Usage (caller's view)

**Player.** Display → Picture → Interpolation: On. The adjacent Rendering limit defaults to
Auto (display refresh), falling back to the region rate when refresh is unknown. Positive numeric
limits remain supported. On 60 Hz
panels (`Locked`, interval 1) nothing changes. On 75/144/165 Hz, and on 120/240 Hz where the lock
interval would be 2 or more, the runtime uses `Exact` at interval 1 and blends. The picture trails
the newest service by `(1 - alpha)` of a tick. Recording and F12 screenshots always show exact
pictures; while recording, the window shows the exact capture picture, as today.

**Live runtime** (`runExact`, after all reserved services):

```java
var phase = scheduler.observePhase(clock.getAsLong() - anchor);   // read-only; never reserves ticks
LivePictureRequest picture = selectPicture(host, phase, changed, forcedPicture, drawn);
if (!drawn && !owner.draw(picture)) return aborted(completed);
```

**Engine**, the only live caller of the level bridge:

```java
public boolean tick() { ... advanceFrame();
    LevelMotionAccess.observeService(levelManager, spriteManager, interpolationActive); ... }
private void renderScheduledFrame(LivePictureRequest picture) { ...
    LevelMotionAccess.present(levelManager, picture.selection(),
            () -> renderAdvancedFrame(frame, perf, picture.capture())); ... }
```

**A new object written tomorrow** by an agent copying the nearest example writes ordinary
`appendRenderCommands` in world coordinates and moves smoothly with no motion code: the central
dispatcher stamps its tiles with its `ObjectRefId` and ROM centre position. If its draw positions
tiles from the camera, `TestCameraRelativeDrawGuard` fails until it carries
`@CameraRelativeDraw(SCREEN_POSITION)` (or `CULL_ONLY` when the camera read only culls).

**Offline tools and tests** change nothing and never blend: they never call
`LevelMotionAccess.present`, and a guard pins Engine as its only production caller. A pixel test that
wants a blend calls `present` explicitly with `PictureSelection.between(a)`.

## Shape

### Types

```java
// com.openggf — runtime
sealed interface LivePictureRequest {
    record CaptureLatest() implements LivePictureRequest { }          // exact; consumes a capture interval
    record Window(PictureSelection selection) implements LivePictureRequest { }
    default boolean capture() { return this instanceof CaptureLatest; }
    default PictureSelection selection() { return this instanceof Window w ? w.selection() : PictureSelection.LATEST; }
}
// FixedSimulationScheduler
Phase observePhase(long unaccountedElapsedNanos);   // pure; Available(alpha) | Unavailable
interface LiveFrameRuntime.Owner { boolean draw(LivePictureRequest picture); ... }   // was draw(boolean)

// com.openggf.level — level presentation (not @ModApi)
public sealed interface PictureSelection {             // Latest | Between(alpha in [0,1))
    PictureSelection LATEST = new Latest();
    static PictureSelection between(double alpha) { ... }   // alpha >= 1 returns LATEST
}
public final class LevelMotionAccess {                 // the only cross-package entry
    public static void observeService(LevelManager level, SpriteManager sprites, boolean active);
    public static void present(LevelManager level, PictureSelection selection, Runnable draw);
    public static void cut(LevelManager level, MotionCut cut);   // explicit relocation events
}
final class LevelMotionHistory { ... }                 // package-private field of LevelRenderer
```

### Data: one owner

`LevelMotionHistory` is a package-private field of `LevelRenderer`. Only `observeService` (once per
live service) and invalidation write it. Draws never do, and it is never a `RewindSnapshottable`.

It holds two **display slots**, each `(serviceStamp, frame, MotionPacket, scroll, collectionCamera)`,
plus one precomputed `Pair` (matched owner deltas, camera delta, BG compatibility), which is built
once at observation so a draw only evaluates `alpha`. The older slot is compacted to its packet and
scroll after pairing; only the current slot keeps tile and art payload.

A slot is exactly what a latest picture drawn now would show:

| Game | Frame | Scroll | Cost |
|---|---|---|---|
| S3K (`SpriteTablePublication`) | `published()` by reference | `publishedScroll()` | References only. Lag outputs rotate `B→B`, so the picture holds. |
| S1/S2 | one `captureSceneSpriteTable` per service | `LevelScrollPresentation.capture` | One producer pass per service, **only while interpolation is active**. Adopted pictures replay it and skip the per-draw recapture. |

**Identity lives in a sidecar, not in native frames.** `Frame`, `Tile`, `Subject` and
`Tables.State` stay unchanged, so rewind payloads and equality are untouched, and the feature-off
path does no extra work. While interpolation is active, collection produces `(Frame, MotionPacket)`.
The packet holds the collection camera (the actual projection origin: render-copy camera with shake,
both axes), an owner table (`ObjectRefId`, or `PlayerRefId` plus a player-instance incarnation),
each owner's ROM centre anchor, its `Anchoring`, and an identity-keyed tile→owner association. For
S3K the packet is frozen when the table is **prepared** and follows it to publication: the history
selects the packet whose frame is reference-equal to the published frame. A frame with no packet
(after a restore, or before priming) draws exact until a fresh pair forms.

**Stamping** happens at the two dispatch funnels every object and player passes through:
`ObjectRenderParts.appendRenderCommands` and the `SpriteManager` player loop. Ownership rides on the
transient SAT entry (`SpriteSatEntry`) through clipping, fragments and replay, as `presentationSubject`
does today, and central head/HUD composition helpers carry the association to replacement tiles.

`Anchoring` is decided by the dispatcher from class metadata, never by producers:

- `FOLLOW` (default): engine objects and players, including `@CameraRelativeDraw(CULL_ONLY)`
  classes. The owner's own motion blends.
- `WORLD`: no identity: rings and other external emissions, ghosts, mod-loaded classes. World
  geometry moves with the camera only.
- `SCREEN`: HUD layers and `@CameraRelativeDraw(SCREEN_POSITION)` classes. Stays fixed on screen.

`@CameraRelativeDraw` is not inherited; a subclass that reads the camera needs its own.

### Per-picture math

Let `w = 1 - alpha`, `step = previous - current` (16-bit arithmetic), and round with `Math.rint`.
Version 1 snaps to whole native pixels.

```
camD   = rint(cameraStep * w)        // render-copy camera, shake from the current generation
ownerD = rint(ownerStep  * w)        // FOLLOW owners that are paired and not cut, else 0

terrain : current.forCinematicCamera(cam + camD)        // existing FG reframe, current deformation
          + BG per-line/column lerp, whole-layer snap on topology/period/residency change
scene   : GraphicsManager.flush() subtracts (camera + camD)   // every world submission moves together
sprites : tile + ownerD (FOLLOW) | tile + camD (SCREEN, cancels the flush offset) | tile (WORLD)
```

- A camera-followed player has `ownerStep == cameraStep`, so it stays exactly where the latest
  picture puts it.
- Terrain, the priority FBO, the waterline, zone features and debug geometry all shift by the same
  integer `camD`, so priority composition stays aligned.
- At `alpha = 1` (`PictureSelection.LATEST`) the exact path runs unchanged. `Between` with zero
  deltas must be bit-identical to it: that is the anchor test.

Discrete state always comes from the current generation: art, DPLC `PatternVersion`s, animation
frames, palette and fade latch, tile edits, visibility, SAT admission and row clips, masks,
priority, painter order, ghosts, pixel operations, HUD digits, primitives and screen shake.

### Discontinuities

| Signal | Mechanism | Result |
|---|---|---|
| Restore, seek, reverse (including reverse-audio restores) | Own post-restore callback registered beside the sprite tables | Invalidate; latest |
| Fresh load, in-place act reload, renderer reset | The call sites that already reset or invalidate `spriteTables` | Invalidate (the native SAT is kept on act reload; only pairing clears) |
| Pause, frame step, capture, screenshot, boundary change, Locked/Probing, setting off, aborted frame | Runtime selection | Exact picture; history dropped when inactive |
| Exact barrier picture (screenshot, forced) | Pair marked presented-latest | Later pictures of the same pair stay latest: no backward step |
| Unsupported scene: special stage, competition, cinematic/Stageplay, editor, mutator scene policies, profiles without `LevelMotionPresentation` | `supportsMotion()` at observe and at adoption | Nothing recorded; latest |
| Explicit relocation: respawn, teleport, scripted camera set, act rebase | `LevelMotionAccess.cut(level, MotionCut)` from the relocating owner, bound to the generation that publishes it | That owner, or the whole picture for a camera cut, snaps |
| Camera step above the ROM `fastScrollCap` (16 S1/S2, 24 S3K), vertical wrap, viewport change | Pair construction | Whole picture latest |
| Owner step above the owner tolerance, unpaired key, slot reuse | Pair construction | That owner drawn at its current position |
| Created or deleted object | Unpaired key, or absent from the current frame | Appears in place; never a ghost |

The camera bound is ROM-derived: native camera follow never steps further than `fastScrollCap`
except S1's uncapped leftward move, which then snaps. The owner tolerance is a **presentation
tolerance, not a ROM law**: one named constant (`2 × fastScrollCap`) with its rationale in the
source and tests on both sides. A relocation that is smaller than the tolerance and has no explicit
cut shows as one tick of motion. That is the accepted residual risk; explicit cuts close it at the
known relocation owners.

### Scheduling

- **Cadence.** Interpolation is usable when the setting is on, the cap exceeds the region rate, and
  the scene is not paused, capturing or stepping. While usable, nomination accepts a lock only at
  interval 1; any other display runs `Exact` at interval 1. `Probing` never presents a blended
  picture, and `Locked` never borrows a phase.
- **Wake.** `min(nanosUntilTick, nanosUntilRender)` while usable, reviving the render deadline the
  pacing design kept dormant. A picture is presented whenever `render` is due, even with no new
  service.
- **Alpha.** `observePhase` runs after all reserved services, immediately before the draw, and is
  pure: services are identical with interpolation on or off. It returns `Unavailable` when a whole
  tick is due, debt is pending, time was clipped, or the batch aborted; the runtime then draws
  latest and wakes at once to service.
- **VRR.** `DisplayOutput` makes no VRR claim today. When it can, a qualified VRR display selects
  tick-gated latest without changing these interfaces.

### What deliberately does not happen

- The history never reads the prepared table, the camera copy words, or `Tables` timing, and never
  enters `RewindRegistry`.
- No producer runs per picture, and the gameplay camera is never moved for a draw.
- No image crossfade, no extrapolation, no VDP emulation.

## Synthesis decision

Two candidates were sketched independently: Opus (`/tmp/arena-interp/candidate-opus`) and Sol
(`/tmp/arena-interp/candidate-sol`). The cross-judge (Sol, the only other-family route available,
so it judged its own candidate) scored Sol 23/30 and Opus 17/30 and recommended Sol. The Opus runner
saw the picker rubric while listing the directory and reported it. The coordinator chose **Opus as
the base**, against the judge, because:

- Sol's fail-closed continuity makes every object tick-stepped until its motion family is certified.
  Moving objects keep the judder the feature exists to remove, and the judge named certifying every
  position writer as Sol's riskiest assumption.
- Sol's float raster mode, float scroll uploads and per-family `ScrollMotion` adapters are a
  programme across every zone of three games, not a modest series of commits.
- Opus's alpha-1 bit-exact anchor and single integer camera delta give one strong, cheap oracle for
  terrain, priority and sprite alignment.

Grafted from Sol:

1. Identity and collection camera in a sidecar keyed to the frame, frozen at preparation, selected
   by publication, dropped on restore; native frames and rewind payloads unchanged; zero feature-off
   cost.
2. The sealed `LivePictureRequest` (`CaptureLatest | Window(selection)`), so capture cannot be
   paired with a blend, plus the screenshot barrier.
3. A fresh post-work `observePhase` returning `Unavailable` under debt, instead of predicting the
   next swap and clamping alpha to 1.
4. Explicit relocation cuts (`MotionCut`) at known relocation owners, bound to the publishing
   generation, on top of the bounds. The owner bound is relabelled a presentation tolerance.
5. Interpolation-usable displays lock only at interval 1, so 120/240 Hz panels blend instead of
   repeating each tick.

Also adopted from the judge: the collection camera is the render-copy projection origin in both
axes, and an exact barrier marks the pair presented-latest so no picture steps backward.

Rejected from Sol: certification-gated continuity, float terrain rendering (deferred, see open
questions), and per-family temporal scroll adapters (version 1 uses per-line lerp with whole-layer
snap). Rejected from Opus: `Motion` inside native `Frame`/`Tile`, swap-return phase prediction, and
always-on identity collection.

## Tradeoffs accepted

- We accept whole-native-pixel steps in exchange for bit-exact latest pictures and aligned priority
  composition; slow pans of one pixel per tick gain little.
- We accept a one-tick slide for an unannounced relocation below the owner tolerance in exchange
  for smooth motion on every ordinary object without per-family certification.
- We accept exact window pictures while recording and for screenshots in exchange for keeping the
  capture/window reuse and recordings equal to what was seen.
- We accept that pieces a parent draws at per-tick offsets (chains, boss sub-sprites) move
  discretely relative to their smoothly moving anchor, rather than matching tiles across animation
  frames.
- We accept a one-pixel background band-edge artifact for one refresh when a line selector moves,
  in exchange for not writing a scroll adapter per zone.
- We accept `(1 - alpha)` of a tick of added latency on top of S3K's native publication lag.

## Alternatives considered

- **Previous-position fields on objects, producers re-run per picture.** The classic engine shape;
  it lost because it re-runs ~1,261 producers per refresh under moved gameplay state, which draw
  purity forbids.
- **Camera-only interpolation.** The followed player would jitter against the screen.
- **Interpolating published→prepared on S3K.** No added latency, but it shows the next VBlank's
  table early and erases native lag frames.
- **Certified continuity with float rendering (Sol).** Stronger discontinuity guarantees, but most
  motion stays stepped and the scope is per zone family.
- **World-space scene graph rebuilt from two simulation snapshots.** Clean for new code, but it
  replaces the native SAT pipeline and gives agents a second presentation model.
- **Image crossfade or optical flow.** Cannot keep current art, priority or occlusion.

## Open questions and risks

- Should enabling interpolation raise the FPS limit automatically? At the default 60 it does nothing.
- Is whole-pixel stepping enough on large windows, or is fractional rendering worth its shader and
  upload work later?
- Is an S1/S2 picture drawn through a captured `LevelScrollPresentation` pixel-identical to today's
  live-accessor draw in every zone? A mismatch is a capture bug, never a zone carve-out.
- Which owner declares bonus stages unsupported?
- What does the S1/S2 per-service capture cost in high-object scenes? Measure before claiming it.
- Do compositors that return swaps out of phase with scan-out make the CPU-submission alpha visibly
  uneven? Real-display qualification is a user step.

## Next implementation step

Add `LivePictureRequest`, `observePhase` and the `Exact`-only selection and wake, with fake-clock
tests proving services are identical with interpolation on or off.

## Implementation units

1. **Runtime and setting** (lane `runtime`): `LivePictureRequest`, `observePhase`,
   `Owner.draw(LivePictureRequest)`, usable-interpolation selection, render-deadline wake, interval-1
   lock rule, `display.interpolation` (catalog, YAML, i18n, `CONFIGURATION.md`, candidate pin). Engine
   accepts the request and draws latest for every picture until unit 3 wires it. Tests:
   `TestLiveFrameRuntime`, `TestFixedSimulationScheduler`, configuration round-trip.
2. **Level presentation** (lane `level`): `PictureSelection`, `LevelMotionAccess`, identity stamping
   and SAT carriage, `MotionPacket` sidecar, `LevelMotionHistory`, pure blend math, adoption in
   `LevelRenderer`, the flush offset, invalidation and cuts, `@CameraRelativeDraw` and its guard.
   Tests: ROM-free math and propagation tests; ROM-backed EGL pixel tests in S1 GHZ1, S2 EHZ1, S3K
   AIZ1 (alpha-1 anchor, followed player fixed, repeated draws pure, restore/reload/load snap, lag
   hold, unsupported scenes exact).

   #### Implementation notes (level)

   `LevelScrollPresentation.forCinematicCamera` normally rebuilds arena-mask geometry from raw
   bounds. Motion uses its mask-taking overload with the current tick's opacity sample, projected
   with the camera, so interpolation cannot advance or discard the native mask fade. Zero motion
   returns the current scroll presentation directly.

   Motion ownership is resolved centrally. A bound `PowerUpObject` shares its player's roster
   identity, incarnation and centre anchor, including shields and stars with null spawns. Other
   objects follow their native centre only with a non-null spawn or cached overrides of both
   position getters. Nonspatial objects carry no identity or anchor and receive WORLD camera
   compensation; `@CameraRelativeDraw(SCREEN_POSITION)` instead carries an anchor-free SCREEN tag.
   No default position getter is called on a null spawn. The guard derives null-spawn classes and
   concrete descendants from compiled constructor/source inventory, including insta-shield.

   Graphics carries opaque owner keys; `LevelMotionOwners` resolves object and player identities.
   Forced camera centering increments a camera-local presentation serial, sampled into each
   picture's metadata at collection. A changed serial rejects pairing at publication without a
   camera-to-level dependency or an added `Camera` mod API member.

   Player attachment draw bridges also stamp the player owner during collection, including the
   no-mutator `drawCommands` fast path. A shield can inherit the null `boundPlayer()` default;
   ownership must follow the semantic player supplied to its actual draw bridge, not depend on
   a mocked resolver binding or the outer object dispatcher. Body, appendage, shields,
   insta-shield, invincibility and super/hyper effects use the same player anchor on those paths.

   Special effects run outside the collected SAT and may use physical or secondary cameras.
   `SpecialRenderMotionPolicy` is an engine-internal, read-only declaration checked both when
   observing a service and when adopting a picture. Undeclared effects require an exact scene.
   An effect may permit blending only when its draw is inactive or its projection is compatible.
   AIZ's curtain and battleship permit it only while their overlays are inactive. The active
   battleship keeps the entire scene exact, including the strip, overlay-owned propellers and
   attached bombs, until a secondary-camera presentation model exists. Leaving an exact scene
   primes fresh history. The default cannot silently apply world-camera motion to a new effect.

3. **Integration** (lead): Engine wiring of `observeService` and `present`, exact-consumer guard,
   docs and changelog, cost measurement, change-based smoke.
