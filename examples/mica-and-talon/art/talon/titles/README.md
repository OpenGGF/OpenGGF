# TALON native title mascot banks

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

All images use the approved character palette; the engine reserves a separate
palette context for each role. `TitlePresentation.java` binds native selectors
and hotspots through the official `CharacterPresentation.titleMascots` API.
`manifest.json` is the audit's complete packaged-bank inventory.

Native object origins remain unchanged. TALON's S2 wings are connected to his
body and replace the separate fox-tail motion. No rotor/tail sprite is supplied.

The frame-D plane excludes the hand patch. Selector 4 restores its resting pose;
selectors 0/1 replace it at the same native object origin. This prevents a wag
from leaving a second hand in the plane. Patch dimensions retain the native
48×56 box; the source-local hotspot follows the authored wrist.

## Title polish (2026-11-10)

S2 stages MICA left with her generated thumbs-up, TALON right with his folded,
body-connected wings. TALON's separate hand selector 19 is explicitly transparent:
there is no detached glove or fist. Native entrance/hand clocks and banner passes
are unchanged. MICA sidekick and TALON solo retain their previous banks.
`polish-s2-provenance.json` records the area check and source hash; MICA's
`polish-prompts.json` records the original duo generation (the TALON fist is rejected).

MICA's S1 bank is regenerated for all eight native poses with the native boxes
and hotspots. Final5/6/7 use a shared generated head/torso and their generated
hand regions, keeping the wag behind the hood. Review includes eight unlabelled
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

## S1/S2 title correction round 2 (2026-11-10)

MICA's eight S1 poses are fresh native-pose generations with a rounded scale
dome, pangolin snout, eyelashes and coral ears. The generated glove overlaps
the hood in the foreground. Native canvases and hotspots remain unchanged.

S2 uses the additive, default-off `TitleMascot.swapS2Slots` option. When the
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
