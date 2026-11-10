# MICA native title mascot banks

Original built-in GPT Image native-pose transformations, reduced against the
native title palette renders enlarged 8× nearest-neighbour. `prompts.json` and
the three `*-provenance.json` files record generation and size measurements.
Raw attempts and native renders stay in
`$HOME/scratch/agent-tmp/mt-titles-20261110/`; no decoded native art is packaged.

- S1: eight entrance/finger frames, native mapping origins and screen-plane mask.
- S2: body selectors 0–7/18 and separate hands 9/19. Lead uses 5–7/18 and 9;
  sidekick uses 0–4 and 19. The native controllers retain every delay and pass.
- S3K: thirteen 320×224 plane frames, split into 160×224 halves, binary native
  mascot clear masks, finger selectors 0/1, a split resting hand at selector 4, and wink selectors
  2/3. Selector 4 composes the hand which native art keeps in its plane. The final emblem mask is independent of the palette;
  matching pixels in the preceding mascot plane identify its original portrait.

S1/S2 images use the approved character palette; S3K uses the documented baked-3D
local palette. The engine reserves a separate
palette context for each role. `TitlePresentation.java` binds native selectors
and hotspots through the official `CharacterPresentation.titleMascots` API.
`manifest.json` is the audit's complete packaged-bank inventory.

Native object origins remain unchanged. TALON's S2 wings are connected to his
body and replace the separate fox-tail motion. No rotor/tail sprite is supplied.

S3K frames C/D now use the fresh baked-3D generations recorded below. Her wink
patch follows the near-eye landmark relative to the native wink object origin.
The sixth approaching-ball image aliases the fifth generated ball at native size.

The frame-D plane excludes the hand patch. Selector 4 restores its resting pose;
selectors 0/1 replace it at the same native object origin. This prevents a wag
from leaving a second hand in the plane. Native finger mappings cover 48×56;
MICA uses 56×60 to include the complete context. The source-local hotspot follows the authored wrist.

## Title polish (2026-11-10)

S2 stages MICA left with her generated thumbs-up, TALON right with his folded,
body-connected wings. TALON's separate hand selector 19 is explicitly transparent:
there is no detached glove or fist. Native entrance/hand clocks and banner passes
are unchanged. MICA sidekick and TALON solo retain their previous banks.
`polish-s2-provenance.json` records the area check and source hash; MICA's
`polish-prompts.json` records the original duo generation (the TALON fist is rejected).

MICA's S1 bank is regenerated for all eight native poses with the native boxes
and hotspots. Final5/6/7 use a shared generated head/torso and their generated
hand regions, superseded by round2 foreground glove art. Review includes eight unlabelled
pairs, native-aligned cyan/magenta onion skins and shared head/torso guides.

MICA's S3K bank is freshly generated in the native baked 3D style: upper-left
specular highlights, volumetric shade ramps and ordered dithering. All thirteen
plane selectors (including semantic blank1), finger 0/1, resting hand 4 and wink 2/3
are supplied. `s3k-palette.json` records its15 opaque colour budget; other hosts
retain the approved character palette. The resting hand is split by its exact
generated glove silhouette, not by cutting a rectangular hole in the plane.
The wink patches keep identical generated face borders around the eyelid inset.
`style-generation.json`, `style-reduction.json` and `style-patches.json` record
prompts, independent attempts, selections, reductions and source hashes.

Root coverage fix: `TitlePlaneMask(pixels, clearedPixels)` carries a binary
ownership array and native palette indices for removed pixels. Existing one-argument
masks still clear to transparency. S3K final planes use native black 15 inside
the native ring aperture and transparent 0 outside it. This also removes the
native portrait's backing above the ring when the new contour differs. Native
black 15 is verified in both participating palette rows. Native gold/emblem
pixels stay outside mask ownership. `aperture-provenance.json` records native
inner-boundary landmarks and the binary/index-only derivation; neither array
contains decoded RGB/tile data. Version notes and the normalized signature
pin are updated; repository policy requires the ordinary candidate release
descriptor to stay unchanged at unpublished 0.7.0. TALON's inherited S3K art
receives the same root mask correction but is not regenerated in this lane.

Review: `$HOME/scratch/agent-tmp/title-polish-lane/review.html`. It contains
paired 900-presentation 60 Hz films, three-times stills, every-frame style gates,
S1 onion skins and a13-plane ring strip. Automatic comparison checksthe recorded
visible ring/aperture pixels with zero box frames. The ordinary capture exposes
12 plane selectors; plane 12 is also rendered through the engine in a separate
13-plane component fixture and ring strip. This is a stated capture limit, not a claim of observing it
in the ordinary animation. Raw attempts/native renders remain in scratch.

Patch area checks: finger/forearm **0.982**, wink patch **1.003**. The native
finger cells also contain blue hood and opaque black aperture pixels; comparing
the entire rectangular context to a transparent isolated hand would give 0.654.
`style-patches.json` records both raw and anatomical counts and the exact excluded
context ramp. The anatomical check uses the glove/forearm, while production native
coverage independently preserves the aperture. No native context pixels are
copied into the authored hand.

## Complete S3K hand ownership

The complete pointing glove, including warm lighting and its contour, now lives
in sprites0/1/4. A fresh generated handless cape fills the old plane footprint.
All three sprites own identical backing context there, so moved fingers reveal
cape instead of a second baked thumb or a black cutout. Hotspots and selector clocks stay unchanged. The subsequent anatomy correction
uses one fresh upright glove for all three poses (see below).

Native `Obj_TitleSonicFinger (sonic3k.asm:6239-6258)` uses the normal sprite clock.
`word_4F96 (General/Title/Map - S3 Sonic Anim.asm:7-11)` and
`word_4FB0 (General/Title/Map - S3 Sonic Anim.asm:12-16)` compose48×56 context
around the hand. Native rest selector4 is empty:
`word_4FE6 (General/Title/Map - S3 Sonic Anim.asm:23-23)`; its hand stays in the
plane. MICA uses the already-supported authored rest4 slot to remove its plane
hand entirely and retain the same contextual replacement principle.
`byte_4B36 (General/Title/Anim - S3 Sonic Finger.asm:3-4)` supplies delay5 and the
unchanged0/4/1 sequence, six presentations per phase.

`s3k_hand_ownership.py` rebuilds from approved plane13 and sprite0/1/4 snapshots
plus the fresh backing sheet (`--source`, `--background`). The binary-alpha
coverage contract is recorded as row spans in `s3k-hand-ownership.json`.
`s3k_hand_review.py --captures <dir> --before <prior-mod-capture>` checks it on
every real presentation, verifies unobscured rendered pixels and preserves all
pixels outside the hand rectangle. It emits all distinct wag poses and the
lead’s sample frames at4×. The regression tests also reject a missing sprite
context pixel and a surviving warm plane-thumb pixel.

Review: `$HOME/scratch/agent-tmp/title-s3k-finger/review.html`.
This hand correction is independent of any roll-in blur changes.

## S3K owner revision, round 2

Fresh built-in GPT Image attempts replace the final portrait and its finger/wink
patches. Take A keeps the more compact glove and broad rounded scales; take B is
retained in scratch. Approach selectors 1–12 retain their existing generated art.
The private title palette itself previously defined red at 14/15: the corrected
ramp is `#DB6D6D` / `#FF9292`, independent of the native banner cycle and fade.
The cream rim retains 8–10. Material-constrained reduction avoids quantizing
cream shadows into the ear ramp.

The final portrait no longer clips each generated pixel against native emblem
ownership: that produced speckled holes along the silhouette. Native mask and
clear arrays remain unchanged. Every uncovered native ring pixel remains stock.
Finger selectors share a wrist at (160,60), with a 39×49 motion envelope.
Wink borders copy the new plane at (108,40); only its own eye-shaped region changes.
This keeps the head, original outline and eyelashes registered in both wink states.
The lower glove is a rounded generated volume with no rectangular backing.

Rebuild from raw selected generations (scratch only):

```sh
python3 examples/mica-and-talon/art/mica/titles/s3k_polish.py \
  --body /scratch/body-source.png --patches /scratch/patch-attempts.png \
  --scratch /scratch/reduction
python3 examples/mica-and-talon/art/mica/titles/s3k_capture_review.py \
  --captures /scratch/title-r2-s3k --native /scratch/native-title-exports
```

Review and raw attempts: `$HOME/scratch/agent-tmp/title-r2-s3k/review.html`.
The checker records literal differences outside the new contour separately from
native-preservation failures; clearing the old Sonic silhouette cannot literally
equal stock. Native animated sprite context exclusions are explicit. This is a
bounded 900-presentation title check, not gameplay parity. Plane12 remains a
separate component view because ordinary timing never exposes it. Coverage rows
stay authored; no fallback obligations move.

## S1/S2 title correction round 2 (2026-11-10)

MICA's eight S1 poses are fresh native-pose generations with a rounded scale
dome, pangolin snout, eyelashes and coral ears. The generated glove overlaps
the hood in the foreground. Native canvases and hotspots remain unchanged.

Historical round2 staging (superseded below) uses the additive, default-off
`TitleMascot.swapS2Slots` option. When the
configured lead requests it and both creator banks exist, the host exchanges
presentation owners only: MICA inherits the left Tails-slot rise and native
passes; TALON inherits the right Sonic-slot rise. Crop origins are native,
without the previous lateral displacement. Single/missing banks keep native
roles. TALON's new right-slot bust has folded connected wings and no separate
glove; hand selectors9/19 are explicitly empty. MICA's left-slot BODY2–4 carry
a palm-facing right-hand thumbs-up, curled fingers toward the viewer, thumb
on viewer right and cuff below/right. HAND19 is empty to avoid a second glove.
The independent native hand objects still run their native clocks.

`round2-generation.json` records prompts, attempts, selections, source hashes
and size checks. Raw generations and real stock/mod capture frames remain at
`$HOME/scratch/agent-tmp/title-r2-s1s2/`; `review.html` contains 3× lockstep
strips, 60fps paired films and a real-capture palm before/after zoom. S3K is
unchanged by this lane. Alternate-role/solo art is inherited and not visually
certified here. Coverage counts stay unchanged: this corrects authored banks.

## S2 own native roles (owner follow-up, 2026-11-10)

This supersedes round2's exchanged staging. The example explicitly passes
`swapS2Slots=false`: MICA uses the centre-right, front Sonic slot; TALON uses
the left Tails slot. The additive API option and its default-off constructor
remain available; no API or engine changes are needed here. Every crop retains
its native origin, with no lateral displacement.

Fresh built-in GPT Image generations supply MICA BODY5/6/7/18 and HAND9, and
TALON BODY0–4. MICA BODY6 shows the approaching palm, BODY7 the bent glove,
and BODY18 excludes the hand. Separate HAND9 supplies her left palm-facing
thumbs-up: thumb on viewer left, curled finger pads on viewer right, cuff
below, short forearm down-left. Native Sonic hand positions and timing are
unchanged, and the native top pass puts the glove in front of the head.
TALON's connected wings carry his own motion; HAND19 remains transparent.
His native Tails reference had the fox/rotor tails removed before generation.

`s2-roles-generation.json` records full prompts, attempts, selections, hashes
and size checks. Independent area ratios are MICA body/hand1.014 and TALON
bodies1.013 (absent independent hand excluded). Real900-tick stock/mod output
has35 distinct portrait-difference frames and three Sonic hand phases; all
recorded native sprite selectors, positions and active flags match. All35
frames are inspected in3× lockstep strips; paired60fps film, palm phase zooms,
native-aligned onion skins and shared guides are in
`$HOME/scratch/agent-tmp/title-s2-roles/review.html`. Native foreground branding
compares equal over8,123,400 masked pixels on all900 ticks. This is a title
presentation check, not gameplay parity. S1/S3K and inherited alternate-role
art are unchanged. Coverage rows stay authored, with no fallback delta.

The reusable review recipe is MICA's `s2_capture_review.py`. It accepts the
paired capture directory and an external native logo alpha mask; neither
the input native pixels nor its regenerable strips/movies belong in Git.

## Initial S3K roll-in trail (4a69cb17, rejected)

Selectors2–7 use fresh native-pose GPT Image motion-smear generations: broad
lit jade cores with short darker overlapping echoes, not separate particles.
`TitleSonic_Frames (sonic3k.asm:5943-5964)` binds these native frames to shared
Kosinski art and individual Enigma maps/palettes. `TitleSonic_LoadFrame (sonic3k.asm:5850-5940)` installs complete planes; no sprite mapping or DPLC
participates. `TitleAnim_FlipBuffer (sonic3k.asm:5775-5804)` reloads3 and
`Iterate_TitleSonicFrame (sonic3k.asm:5824-5847)` advances at delay1: four
native loop iterations. The engine capture observes selectors2–6 at234–253,
then7 at254–266 while the next load completes; timing is unchanged.

`MapEni_S3TitleSonic1 (s3.asm:118484-118504)` introduces the first seven plane
maps; `ArtKos_S3TitleSonic1 (s3.asm:118531-118533)` owns their shared tile art.
`s3k-rollin-provenance.json` records owning labels, mapped tile IDs, attempts,
rejected first small-ball set and native-box area checks. The existing private
palette supplies six jade shades within the native15 opaque-colour budget.
Only selector7’s binary clear ownership also changes: the old extractor truncated
at row80, leaving1107 blue native trail pixels in SEGA letter gaps through row131.
The full native7 contribution is isolated against the preceding logo plane;
new art is clipped behind those original glyphs. No native RGB/tile bytes ship.
No approved later image/mask, palette, binding or host code changes.

```sh
python3 examples/mica-and-talon/art/mica/titles/s3k_rollin.py \
  --generation /scratch/large-complete-attempts.png --small-generation /scratch/small-attempts.png \
  --native /scratch/native-title-exports
python3 examples/mica-and-talon/art/mica/titles/s3k_rollin_review.py \
  --captures /scratch/title-s3k-rollin --native /scratch/native-title-exports \
  --baseline-sha 5278d637cd
```

Review: `$HOME/scratch/agent-tmp/title-s3k-rollin/review.html`: every one of33
real roll-in presentations at3×, and a360-frame60Hz side-by-side film. All633
frames267–899 are byte-identical PNGs to a fresh5278d637cd integration capture;
all non-roll-in frames0–233 are also byte-identical. This is a bounded title
presentation check, not hardware timing parity. Title audit obligations stay
six authored; none moves from fallback.

## Heavy-blur retry

The lead’s254/267/279/289 evidence corresponds to engine selectors7/8/9/10.
Pass1 changed only the small ball and missed the large blurred close-ups.
Current art covers2–10; settling11/12, ring13, portrait, wink and finger are
unchanged by this blur commit. All607 frames293–899, including all604 ring
frames296–899, remain byte-identical to integration5278d637cd.

Both fresh built-in GPT Image and deterministic shutter candidates were made
for every selector2–10. Unlabelled1×/3× comparisons select GPT2–7 and the
image-space radial/turn exposures8–10. The latter dissolve lashes, plates,
gloves and laces into multiple ghost contours instead of a sharp model with
an added trail. Native measurements and sampling rectangles, exposure kernels,
selection reasons, raw area ratios and limitations are recorded in
`s3k-heavy-blur-provenance.json`. The native flattened art does not uniquely
reveal its original3D shutter; visible copy counts/landmarks are estimates,
while dither/light coverage and source-detail metrics are measured separately.
Native large planes use their own descriptors in
`TitleSonic_Frames (sonic3k.asm:5943-5973)`; timing stays host-owned.

All tools require Pillow; the offline candidate/measurement tools also require
NumPy (install in a scratch virtualenv, not the runtime). From a scratch set
containing approved `sharp/2..13.png`, native exports and `gpt-small.png` /
`gpt-7..10.png`, run these scripts with `--scratch <dir> --native <exports>`:
`s3k_heavy_blur.py`, `s3k_gpt_blur_reduce.py`, `s3k_blur_measure.py`, then
`s3k_heavy_review.py` after real stock/baseline/mod captures. The selected
320×224 candidate planes split at x160 into existing left/right assets.

Review: `$HOME/scratch/agent-tmp/title-s3k-heavy/review.html`; it includes the
requested3-way real-capture strips at1×/3× and a360-frame60Hz three-way film.
SEGA checks preserve100,763 native pixels and reject any residual blue body.
Input anatomy stays approved; extra low-opacity ghost support expands the raw
visible silhouette area. Colour budget stays15 opaque entries, with4×4 ordered
shade/coverage dithering. No runtime blur, API, palette or clock changes.

## Fixed S3K wag anatomy

`s3k-hand-master.png` is one fresh generated upright glove: thumb towards the
face, index raised, three curled fingers on the opposite side. The annotated
real-capture strip labels each digit. `s3k-hand-anatomy.json` fixes all five
landmarks and the wrist. `s3k_hand_anatomy.py` derives selectors0/1 by Scale2x
to8×, rotation about the wrist, then majority reduction into the same palette.
Native fingertip/wrist measurements give relative angles−11.304° and+6.562°;
rest4 remains zero. No independent pose generation can change the anatomy.
The complete cape context, planes and ownership mask from the preceding fix
remain unchanged. Six-tick native wag timing is unchanged.

The independent glove-area scale ratios against native are rest4 0.964,
left0 0.979 and right1 0.964, excluding the replacement context. Provenance,
four fresh attempts, selection and reduction are recorded in
`s3k-hand-anatomy-provenance.json`. Regression tests enforce exact derivation,
five material landmarks on their correct sides, and reject an independent
finger mutation. These are structural checks; anatomy is also inspected.
Review: `$HOME/scratch/agent-tmp/title-s3k-anatomy/review.html`, including the
6× stock/mod strip, five-digit annotated copy and60Hz film. Across900 frames,
967608 ownership checks and900812 visible pixel checks pass; every pixel
outside the hand is byte-identical to integration2447194fa1.

## Whole-lash S3K wink ownership

Native `word_4FCA (General/Title/Map - S3 Sonic Anim.asm:17-19)` and
`word_4FD8 (General/Title/Map - S3 Sonic Anim.asm:20-22)` each compose 32×48
from 32×32 and 32×16 pieces at x−16, y−24/+8.
`Obj_TitleSonicWink (sonic3k.asm:6261-6277)` places that complete eye/context
patch at screen 104,48 before vertical scroll. MICA's lashes are larger: the old
56×48 patch cut through them. The replacement 80×60 patch at 108,32 owns the
whole eye and lash group, with an irregular opaque footprint and two pixels of
unchanged plane context at its edge. The example's existing hotspot changes
to 12,40; the native host clock, palette, plane and hand are unchanged.
`byte_4BA6 (General/Title/Anim - S3 Sonic Wink.asm:3-4)` still selects half 2 for
two presentations, closed 3 for eight, half 2 for six, then empty 4 for 104+48.

Two fresh half-closed and two fresh closed GPT Image alternatives use the native
8× patch and approved MICA eye. Selected left candidates move all three lashes
with the lid, rather than retaining upper stubs from the plane.
`s3k_wink_rebuild.py --generation <sheet>` reproduces the reduction and context
band byte-exactly. Prompts, selection and checks are in
`s3k-wink-provenance.json`; `s3k-wink-ownership.json` records the complete old
ink footprint, alpha ownership and lid-root/three-tip landmarks.

`s3k_wink_continuity.py` checks that all 233 old eye/lash ink pixels have an
opaque surround, each new lash tip is 8-connected to its lid root, palettes
match and any ink crossing the alpha boundary equals the plane on both sides.
Negative controls sever a root, remove ownership and introduce a false ink seam.
`s3k_wink_review.py --captures <scratch> --before <hand-capture> --stock <stock>`
checks every rendered wink pixel and all pixels outside ownership, then emits
all three distinct states at 6× and an annotated copy with frame/alpha borders.
Across 900 presentations, 173312 visible patch pixels pass and outside ownership
changes are zero. Another 660272 full-glove pixel checks, including every
derived pose outside the old ownership footprint, preserve the hand exactly.
State CSV is byte-identical; 836 complete PNGs, including all
296 intro PNGs, are byte-identical to the hand commit. The prior SEGA check
also passes 100763 stock pixel comparisons with no residual blue.

Patch-context area is 2708 pixels versus native 1526, sqrt ratio 1.332. This grows
ownership around the existing eye/lashes; it does not rescale the portrait.
No audit rows move. Review and 60 Hz film:
`$HOME/scratch/agent-tmp/title-s3k-wink/review.html`. The ink topology checks
complement visual inspection; they do not classify arbitrary generated lashes.
