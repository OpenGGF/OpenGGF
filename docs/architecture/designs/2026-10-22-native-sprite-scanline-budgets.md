# Native sprite scanline budgets

Task: the 2026-10-22 sprite_limits audit lane, based on develop `94c40570a15a11b70caf7456e9b0d0a8eec8a9b4`.
The internal evaluator/fragment contract is committed as
`ccab504e3eb0d708bd2fee0433cc611034d2a174`; production consumer follow-up is
recorded in this lane's delivery commits.

The audit's 21 overlapping one-tile pieces survived the old SAT postprocessor.
A new production GraphicsManager regression reproduced 21 commands where H40
admits 20, including with no S3K mask request. The root causes were the absence
of hardware evaluation and LevelRenderer choosing SAT collection only in mask
zones. Ordinary gameplay and the separately drawn HUD bypassed it.

## Hardware authority and behavior

Primary reference: Genesis Plus GX revision
[`49c584764893b0505ac7f768a754f97330fa4392`](https://github.com/ekeeke/Genesis-Plus-GX/blob/49c584764893b0505ac7f768a754f97330fa4392/core/vdp_render.c),
`parse_satb_m5` and `render_obj_m5`; `vdp_ctrl.c` selects the H32/H40 width.
The Sega Genesis Software Manual's Mode-5 sprite capacity table independently
lists 64/16/256 for H32 and 80/20/320 for H40 (table entries / pieces per line /
fetched pixels per line).

Evaluation visits linked SAT order, before painter replay and independently of
the sprite/background priority bit. Vertically active pieces consume one count
and their full declared width, including transparent, overlapping, offscreen
and already masked art. Count overflow excludes later pieces on that line.
Pixel overflow keeps the final piece's remaining screen-left tile columns;
horizontal flipping changes which source columns occupy that edge. Reaching
the pixel budget exactly arms X=0 masking for the next line. Count overflow
alone does not. Masking does not stop width evaluation, and a line that does
not exhaust its pixel budget clears carry.

S3K's optional whole-word `$07C0` rewrite remains separate from hardware
admission: it makes that entry X=1 and its successor X=0. Native X=0 masking
also applies without the rewrite, and masks the whole logical scanline. The
scratch evaluator starts each collection with no prior-frame carry; it never
retains gameplay, trace, rewind or comparison-row state.

## Runtime integration and HUD topology

`SpriteSatInternalAccess.beginCollection(GraphicsManager, Raster)` defines a logical raster in
the same coordinate space as submitted pieces. `Raster.h40` and `Raster.h32`
retain native capacities. Producers submit complete native mapping footprints,
then finish with `endSpriteSatCollectionAndReplay`. Partial columns use source
column clipping; vertical windows remain pixel precise through direct,
instanced and immutable presentation replay.

`SpriteSatInternalAccess.submitGroups(GraphicsManager, List<SpriteSatEntry>, Runnable)` admits each supplied
native footprint once while freezing its producer's scattered ROM-backed art
fragments. Empty art does not release hardware capacity. This avoids counting
individual HUD letters or digit tiles as separate native sprites. The runtime
ROM supplies the geometry through the internal `HudSatLayoutSource`; creator
`HudStaticArt` remains unchanged. Verified map roots and ROM identities:

| Game | Mapping root | Shape | SHA-1 |
| --- | --- | --- | --- |
| S1 World REV01 | `$1CD6A` | byte count, 5-byte pieces, 4 frames | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| S2 World REV01 | `$40A9A` | word count, 8-byte pieces, 4 frames | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| S3&K locked-on | `$DBB6` | word count, 6-byte pieces, 6 frames | `CFBF98C36C776677290A872547AC47C53D2761D6` |

Normal HUDs have ten footprints. S1/S2 warning frames change palettes without
releasing pieces. S3K warning frames remove label pieces; bonus frames use a
rings-only layout with lives. Debug digits change art without adding hardware
pieces. These claims have direct, independently expected ROM topology tests.
Frozen-fragment checks also cover the wide bonus HUD's existing left origin,
and native two-digit life placement at x48/x56 (one digit stays at x56).
HUD geometry precedes ordinary gameplay pieces in the admitted table, and HUD
visibility filtering happens afterward. VBlank numeric-art publication reuses
the already admitted head geometry; it does not introduce more SAT pieces.

Legacy live gameplay now prepares and consumes the same immutable admitted
presentation values as the published-table renderer, retaining high-background
priority flags and permitting post-admission layer filtering. Graphics scratch
and frozen fragments are consumed before a new collection; retained frames and
existing sprite-table rewind snapshots own immutable values.

## Wider presentation and other consumers

Wider gameplay retains H40's fixed 20/320/80 capacity. Side pieces compete for
that capacity. Ordinary expanded coordinates do not wrap at the native nine-bit
X field, but explicit X=0 controls still mask the entire logical raster and
carry uses the native 320-pixel threshold. This is an engine presentation
extension, not native-wide parity. It follows the existing distinction between
wider visibility and fixed native world/object capacity; no proportional sprite
capacity had an established contract.

Rejected: proportional count/pixel/table budgets. The repository documents
wider viewports but no native capacity-scaling rule; scaling would change the
meaning of overflow-derived masking. Also rejected: leaving hardware admission
behind the S3K flag, or admitting expanded HUD tiles individually; each leaves
an actual production bypass or invents overflow.

S2's special stage is a fixed inner H32 raster, even in a wide outer display.
Its separate renderer consumer belongs to the S2 special-stage lane; split
competition view adapters belong to native_modes. Neither should infer hardware
capacity from the outer framebuffer, nor submit one entry per expanded tile.
Interlace/compressed mapping production belongs to its native consumer adapter;
ordinary 8px Mode-5 tile replay is not proof of that separate mode.

## Validation scope

Focused logic boundaries distinguish 20/21 and 16/17 pieces, count versus pixel
exhaustion, offscreen widths, exact-budget carry, masked consumption, table
walk limits, helper markers, both flips, translated origins and repeated calls.
EGL pixel tests exercise both replay paths. ROM-backed gameplay checks exercise
native HUD competition and actual displayed pixels for S1/S2/S3K, rather than
only constructing the helper. Exact completed commands/results are recorded in
the lane handoff; the integration coordinator owns combined smoke. No fixture
or comparison row supplies rendered or gameplay state.


## Pixel ownership and producer order

An independent read-only lifecycle review found that painter replay plus terrain
fragment discard leaks a later high-priority sprite through an earlier opaque
low-priority sprite hidden by high terrain. The native sprite result must be
selected before comparing that winner with the planes. The admitted SAT now
replays front to back inside a scoped stencil coverage pass. Transparent indexed
art discards before claiming coverage; a terrain-hidden winner blends no color
while retaining ownership. Direct, batched, instanced and immutable-frame paths
use that rule. Partial rows and flips retain their source-pixel coverage.

The scope preserves foreign stencil contents, attachments, framebuffer bindings,
stencil/depth/blend/scissor state, program, VAO, buffer and renderer-touched
texture bindings; deferred failure unwinding closes it. Color-only owned scene
FBOs receive a temporary owned stencil attachment. Existing packed depth/stencil
images use a backup with their exact format rather than an incompatible stencil-only
blit. No desktop display, gameplay state or rewind identity belongs to this pass.

Review also found that always collecting SAT entries lost the AIZ bridges'
selective terrain palette mask. That mask is now frozen with each entry and
fragment, preserved by clipping, and restored during replay. A palette-3 versus
palette-0 EGL control distinguishes the waterfall exemption from ordinary terrain
occlusion. A separate published-frame regression exposed the plain batch's
failure to treat a high-priority tile's effective occlusion mask as zero; batch
transitions now use the effective mask in all three rendering modes.

The internal `SpriteTableBuildProfile` selects placed rings independently of
game names: S2 `BuildSprites` (`s2.asm:30523–30535`) and S3K `Render_Sprites`
(`sonic3k.asm:36358–36373`) emit HUD, then placed rings, then object buckets.
S1 rings emit through their actual object slots in bucket 2; `Ring_Collect`
changes their subsequent sparkle submission to bucket 1 (`_incObj/25, 37 Rings.asm:148–165`).
`RingDrawInternalAccess` separates the placed list from reserved attracted-ring
emissions, retaining frozen art callbacks and draw-only state. Lost rings already
emit through their individual object owners. Rendering no longer clears expired
placement sparkle state. Ring producers also freeze their semantic `RINGS` layer
inside object-slot traversal: `includeRings` and `includeObjectSprites` remain
orthogonal after admission. Hidden rings still compete for native capacity.
Actual S1/S2/S3K EGL controls compare objects-only, rings-only and neither,
including a surviving lower ring row beneath a full upper scanline. The native
entry fade is scoped out only for this displayed-palette control; no gameplay
or fade clock advances.

The external slot bridge preserves the existing entry list, including repeated
or inverted DisplaySprite FIFO entries. It can anchor legacy external emissions
between entries without sorting or deduplicating that list. This is a documented
mixed bridge, not proof of complete native queue topology. The generic queue
backend belongs to the coverage lane; its admitted slot/bucket values, live slot
resolution and per-slot dispatch are a coordinated dependency. Ring motion must
run at its actual reserved slot before submitting a genuine `Draw_Sprite` call;
guessed early admission and a late slot sort were rejected because they change
capacity and call order. The ring consumer follow-up must retain native deletion,
creation-after-current and restore phases without double advancement.

## Explicit remaining integration boundaries

The shared count/width evaluator does not establish exact native topology for
all producers. The S2 special-stage lane owns its native H32 SAT consumer;
native_modes owns compressed/interlace and split-view consumers. Exact sprite
queue closure also depends on each object's authored DisplaySprite call sites,
including the separately owned S1 Bomb duplicate/orphan-pointer behavior.

S2 special-stage shadows require operation metadata alongside admitted geometry.
Its owner independently decoded the original ROM's flat/diagonal/side Nemesis
families and found only indices 0 and 15. Pinned GPGX's `make_lut_bgobj_ste`
selects shadow for palette-3 index 15 and intensity increment for index 14;
the old shader comment calling any nonzero index a shadow was rejected. A first
`SHADOW_15` implementation preserved row clipping, frozen fragments and first-pixel
ownership, but its final-RGB multiplication was rejected before publication.
Synthetic 204-to-102 readback proved multiplication, not native intensity. GPGX
`palette_init` selects shadow intensity `n` versus normal `2*n` before output
conversion; low-priority background pixels already at shadow intensity must not
be darkened again. Canonical FULL output rounding and the nonlinear display
profiles also make post-conversion halving incorrect.

The bounded replacement samples a producer-owned shadow-intensity color target
made from the same native background mappings, track strips and scroll projection.
`withShadow15Backdrop` supplies its live texture and bottom-left pixel rectangle;
no GPU identity enters the admitted frame or rewind state. Missing input fails
explicitly. `NativeIntensityPalette.shadow` carries the original palette and
native source-line ownership so fades run before intensity and display conversion.
RAW output uses `round(255*n/14)` for FULL and `17*n` for LIMITED. Analog and soft
profiles extend their existing normal ramps by interpolating the intermediate
four-bit levels before cross-channel mixing and output gain; that is explicit
presentation policy. Source index 0 discards, index 15 selects this target, and
other nonzero indices are rejected. Index 14 remains a source-proven highlight
operator outside this bounded contract, rather than silently becoming a shadow.
The S2 renderer owns companion production and low/high plane intensity choice;
this shared seam does not certify its consumer until composed checks pass.

Rejected validation interpretations: offscreen Tails mappings at origin (0,0)
losing unused presentation versions did not prove lost ROM art; moving the
unchanged art comparison inside the native raster preserved its purpose.
A test probe at virtual tile9000 failed atlas range governance before drawing;
using the task-owned transient-effects subrange corrected the test harness.
S3K boot could retire a dynamic probe during native entry; the final check
inserts it after neutral entry steps and exercises real table capture,
publication, restore and framebuffer replay without a gameplay fixture seed.
