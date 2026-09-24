# Widescreen boss arenas: signal-static side mask

Status: explicit activation prototype superseded by user-requested automatic bounds derivation; conversion under validation. Separate local
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
- Use dark, slightly cool monochrome grain, refreshed with an independent field
  at 60 Hz. Keep the subdued noise character of the rewind effect, but no spatial
  scrolling, moving tape band, wobble, chroma split or picture displacement.
  This is an original presentation adaptation, not Genesis behavior.
- Keep HUD, lives, menus and accessibility overlays readable. The preview footage
  already places its HUD within the central rectangle. A production pass must
  run before HUD composition; merely masking a finished frame is insufficient
  when a user's HUD layout occupies the wings.
- Dissolve the mask when the arena's player bounds actually reopen. Boss HP
  reaching zero alone is not enough: escapes, gated pads, results and subsequent
  phases may still own the restriction. No TV hiss or other new audio.

The preview's entry fade is illustrative, then holds through the rest of the
five-second clip. Neither fight unlocks. Looping restarts the entry demonstration. Its two supplied clips
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
checks clear entry, sustained masking and opaque outer wings, and fully decodes both
finished MP4s. Lossy MP4 encoding is not a pixel-parity oracle; the PNGs retain
the exact composed pixels.

Demo archive: `$HOME/Videos/OGGF/ssz-arena-static-camera-fixed-60fps-20260924`.
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

## Revision: remove vertical drift

The initial prototype (`fce524948`) used `y + tick*13` in a spatial hash. This
translated the same field upward by 13 pixels at each refresh; it was not fresh
random static. A separate moving brightness band reinforced the apparent motion.
User review identified the drift. Both are replaced with independent, seeded
noise fields per refresh, preserving deterministic playback. The initial 12 Hz cadence was subsequently
replaced with one fresh noise field per 60 fps video frame after user review.
A regression check compares consecutive fields at zero and 13-pixel vertical
offsets; neither may retain significant correlation.

Regenerated both demos: all 600 centre-preservation checks, fade endpoint checks,
full movie decodes, temporal correlation checks and deterministic replay passed.

## Revision: hold the mask through knockback

The original five-second preview faded out at 3.9 seconds without event knowledge.
That coincided with player damage and misleadingly suggested the arena unlocked.
The compositor never changed the source camera or collision bounds. Remove the
illustrative release from these ongoing fights: retain the mask through the final
frame and explicitly label looping as a restart. Damage must never release the
production mask; only the owning arena event reopening bounds does so.

Both revised clips passed all 600 centre-preservation assertions, full decode,
temporal decorrelation and final-frame sustained-mask checks.

## Camera and cadence review

The first source captures had genuine widescreen camera clamp errors: GHZ
followed knockback; MTZ jumped right by240px. The mask made the latter hide the
fight. The bring-up worktree now projects SSZ1 replica bounds using captured
event ownership, as already done for SSZ2. This prototype branch does not contain
that engine change; regenerated inputs are the camera-fixed recordings. Their
300 filmed state rows hold cameraX112 (GHZ) and5488 (MTZ), including44hurt rows
each. Native320 recordings retain352 and5728 respectively.

Noise now refreshes at60Hz, not the initially chosen12Hz. Both finished MP4s
were decoded; all167 consecutive frame pairs tested during the fully opaque
interval have changing noise. All600 centre-preservation checks still pass.

## In-engine architecture — visually approved

The user approved GHZ and MTZ replica arenas only. All other events remain off.
Common `ArenaMaskState` exposes `activate(activeWidth)`, `release()` and
`advance()`. The level event coordinator is the single owner; a boss can request
activation through that coordinator. No automatic boss/lock detection lives in
common code. An opted-in runtime implements `ArenaMaskSource` and includes the
state's16bytes in its own rewind snapshot. Advance once per executed gameplay
pass, never per draw; pause therefore freezes it and rewind restores the noise
clock and45-frame fade. Fresh runtime state is clear. Repeated activation is
idempotent; releasing mid-fade reverses smoothly. Native-width output is a no-op.

`ArenaMaskRenderer` uses an independent integer hash per pixel and gameplay frame.
It draws directly into the currently bound framebuffer before HUD composition,
restores GL state, and is cleaned up with GraphicsManager. It neither samples
framebuffer0 nor consumes gameplay RNG. This works with offscreen capture and
preserves the active centre by discarding its fragments.

The shared implementation was committed on the separate prototype branch at
`55e8ad12d` and reconciled with the campaign at `679f7cb87` (campaign implementation
`454184d52`, destination develop `40d55783c`). The user approved activation/hold
and the real defeat/pad-release clips, including the additional wide widths.
Only GHZ and MTZ replicas are authorized. Integration into develop and push
remain conditional on delivery checks, not another visual approval.

The combined campaign selection is 2904 ordinary classes plus guards. A PC
restart interrupted the first run after 6155 reported tests, with one failure
in the SSZ rewind coverage exception baseline and two opt-in benchmark skips.
That partial run is not a suite pass; guards did not run.

### Trial results

Focused queued Java21/Maven run (absolute S3K ROM, DISPLAY=:0, native GL enabled):
TestArenaMaskState, TestArenaMaskRenderer, TestNativeArenaCameraFraming,
TestS3kSszGhzArenaHeadless, TestS3kSszMtzArenaHeadless, TestSszColdRouteCapture,
TestS3kAiz1SkipHeadless, TestSonic3kLevelLoading, TestSonic3kBootstrapResolver,
TestSonic3kDecodingUtils:115tests, zero failures/errors/skips. After making the
GraphicsManager entry package-private through an internal bridge and adding
event activation assertions, reran mask state/realGPU/both replica events/cold
route:31tests, zero failures/errors/skips, finished09:24:53BST. GPU checks cover
all five widths at1x/2x, offset viewports, capture FBO, centre identity, per-frame
noise and deterministic replay, and GL state restoration. Cold route retains
its ten full-registry rewind/replay spots with the additional presentation state.

Live shader recordings: campaign-20260924-{ghz,mtz}-live-static-{320,800},450
frames each from explicit checkpoint/40rings/neutral input. Full MP4 decodes pass.
All450 gameplay CSV rows per recording match pre-mask camera-fixed recordings
exactly. All300 common filmed frames preserve the central320 pixels exactly
before encoding; native320 entire frames are identical. Wide frame400 of both
fights inspected: mask remains active through knockback, HUD readable.
The implementation is committed and reconciled as described above. Full combined
suite/guards and broader lifecycle/display-shader coverage remain pending; no
develop integration or push is claimed.

## Candidate research — Luna, max reasoning, 2026-09-24

Read-only review at campaign `679f7cb87`; no candidate footage was inspected.
Only SSZ1 GHZ/MTZ remain approved. Camera locks identify research sites, not
activation conditions. All possible excess-space problems below remain inference.

| Priority | Site and ROM owner | Native camera bounds | Next evidence and lifecycle owner |
|---|---|---|---|
| 1 | LRZ1 drill miniboss; `Obj_LRZMiniboss`, `word_784E8`; `LrzMinibossInstance` | X `$2C00`, Y `$710`, single native view; already centred in widescreen | Capture approach, drill/arm extremes, defeat, sign, seamless Act 2 rebase and both releases at 320/800. Mask could signal unusable wings only if no action needs them. Miniboss/event coordinator owns activation; post-defeat camera-release chain owns release, not HP zero. |
| 2 | FBZ1 miniboss; `Obj_FBZMiniboss`; `FbzMinibossInstance` | X `$2E20..$2EA0`, Y `$540`; 448px union of native views | Screen approach and full attack range before deciding on centring or a moving 320px window. Possible activation only after gate settles; `startAct2Sizes()` owns release after sign/results. |
| 3 | DEZ1 miniboss; `Obj_DEZMiniboss`; `DezMinibossInstance` / `DezMinibossTransport` | X `$3680..$36C0`, Y `$28C`; 384px union | Capture both phases, pursuit, vertical swings, beam and transport through bounds reopening. No explicit centred-arena flag yet. Activation after gate; transfer release ownership to transport/sign flow. |
| 4 | FBZ2 end boss; `FbzEndBossEventControlInstance` / `FbzEndBossInstance` | Max X `$32B8`, closes after approach; Y depends on player mode | Capture approach, settled fight and capsule exit. Centre/framing suitability unverified. Event controller activates after pan; capsule/exit bounds reopening releases. |

Source lookup anchors in `docs/skdisasm/sonic3k.asm`: LRZ1 159996,
FBZ1 146766, DEZ1 167659, FBZ2 109825 (line numbers at review revision).
Engine lookup anchors: LRZ miniboss 1001, FBZ miniboss 147, DEZ miniboss 183,
DEZ transport 167, FBZ end event 142, FBZ end boss 400. Confirm routine ownership
against current source before implementation. Each future trial also needs
knockback, death/respawn, rewind, participant bounds and transition checks.

Do not infer applicability from these other locks: MHZ1 wraps/repeats; MHZ2 and
LRZ3 scroll or pan through phases; SOZ1 has a broad mobile arena and SOZ2 keeps
horizontal movement; SSZ2 has a later arena pan; DEZ3/DDZ wrap, scroll or change
arenas. DEZ2's centred X/free Y lock and ROM-pixel planet extension are already
handled. A bounded S1/S2 sweep supplied no stronger concrete candidate.

## Proposed bounds-derived default — discussion, 2026-09-24

The user proposed deriving masking from camera bounds instead of activating it
per encounter. This is a proposed successor, not the behavior validated above.
The reusable renderer/noise/rewind work still applies; the activation geometry
would move to a common presentation owner. No additional site is enabled yet.

For a finite horizontal camera-origin interval `[minX, maxX]`, the union of
native views is `[minX, maxX + 320)`. Subtract the actual rendered camera origin
from both ends, clip to the viewport, and mask only outside that interval.
Do not collapse a 448px arena with 128px camera travel into a centred 320px
strip. Left/right exposure can differ. The shader needs independent edges;
`activeWidth` alone currently centres them and cannot express this geometry.
Native-width output should remain unchanged. Bounds expansion and the actual
camera movement should move mask edges without a separate boss-defeat trigger.

`PlayableSpriteMovement.doLevelBoundary` confirms S3K horizontal player limits
use native camera bounds: left centre `minX+16`, right centre `maxX+296`.
Those collision offsets are not crop edges: retain the native view's margins
for the player sprite and action. S1/S2 can additionally allow 64px on the right
outside strict locks, and S2 may consume pre-eased maxX; a cross-game default
must account for those semantic rules instead of assuming S3K's exact contract.
Object-controlled players skip this boundary path, and camera freezes also
serve death/cutscenes. Thus a stopped camera alone does not prove an arena.

Before implementation, establish a common finite/scrolling/wrapping presentation
contract and how scripted cameras declare exemption. Preserve native signed-word
and modular behavior; do not sort transient inverted bounds into a fabricated
arena. Do not use Y death bounds as top/bottom masking limits. The proposed
rule would also mask exposed ordinary level edges, a broader product behavior
than the two manually approved fights. Verify off-centre cameras, moving and
easing bounds, wrap rebases, cinematic actors beyond player limits, death,
respawn, pause, rewind and all supported widths before replacing the explicit
implementation. No physics or player-bound changes are implied.

## Automatic conversion — authorized 2026-09-24

The user chose to replace explicit per-arena activation with a default derived
from bounds. The earlier manual-only policy and approvals describe the previous
prototype, not the scope of this conversion. Keep the shader but remove
`ArenaMaskSource`, SSZ event calls and SSZ-owned animation state. Derive immutable
left/right edges and noise time alongside `LevelScrollPresentation`, which already
retains and rewinds the camera generation paired with the displayed sprite table.
Other publication modes derive from the current camera and captured level clock.
A common `LevelBoundsMaskTransition` retains and snapshots fade history; this
avoids duplicated event lifecycle and changes to published snapshot APIs.

The mask covers only horizontal excess beyond the union of native views, including
ordinary level edges. Wrapping foreground domains and transient inverted bounds
produce no finite mask; native320 output is unchanged. S1/S2's ordinary-play
right extension is preserved. No camera freeze, player hurt flag, HP or zone name
activates the effect. Camera/player geometry remains unchanged. Existing centring
policies remain independently owned by their camera events.

Spatial 12px feather and independently hashed per-gameplay-frame noise remain.
The initial conversion removed the 45-frame event fade; the user correctly
requested retaining activation fade when already-visible scenery becomes masked.
The shared transition now tracks per-column opacity in world coordinates: newly
covered visible pixels fade in, existing masked wings remain opaque, and newly
exposed offscreen pixels arrive masked. Release fades out over 45 gameplay ticks. Noise
uses the captured object-execution counter (which keeps advancing when a ROM
event holds Level_frame_counter), never render count or gameplay RNG.
The shader accepts an asymmetric interval instead of a centred width.

Conversion validation includes geometry at all five widths, variable-width arena
and one-sided edges, bounds expansion, native/inverted/wrapping no-ops, actual GPU
clear-interval preservation and noise cadence, retained scroll/rewind state,
SSZ replica lifecycle and the cold-route replay. Refresh the in-engine captures
and combined delivery tests before claiming acceptance of the converted behavior.
The previous combined run was deliberately interrupted for this changed scope;
its partial results do not certify this implementation.
