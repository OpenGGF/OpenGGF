# S3K Competition miniature bank

`competition.json` inventories native 2P mapping IDs, original indexed PNGs,
per-frame hotspots, native box/area measurements and authored/alias/N/A status.
$00 is an empty sentinel; $27 aliases $26 as the native bank does. All other
body rows are authored. TALON's separate 16-frame appendage bank is N/A and is
suppressed explicitly by `CompetitionPresentation`, with wing motion in the body.

Built-in GPT Image transformed every native pose against the approved model
sheet and WAIT drawing. Two alternatives were requested for each grid. The
rotated TALON slope grid was rejected for upright flight poses and regenerated
with explicit head/boot orientation instructions. `generation.json` records
prompts, selections, original source hashes, reduction factors and scratch paths.
Raw generations and native comparison images stay outside Git. No native pixels
are packaged. Only background removal, uniform reduction, fixed-palette matching
and one-pixel inside silhouette ink follow generation; no contours are painted.

References use Competition's actual body palette, arena line 0 at ROM $A8A3C.
The $6C0C copy populates line 1, and is not Sonic's body palette. Miniature WAIT
anatomical estimates (±1px): Sonic head/torso heights 12/6, MICA about 11/6;
Tails 11/5, TALON about 11/5. These are miniature proportions, not scaled versions
of the full-size sheet. Grounded frames preserve the native exclusive foot plane;
rotated/airborne frames use the native hotspot's relative position.

Independent size check, all native selectors including aliases and blank:

| Set | sqrt(mean authored opaque area / mean native body area) |
| --- | ---: |
| MICA / Sonic 2P | 1.009 |
| TALON / Tails 2P | 1.045 |

Review: `$HOME/scratch/agent-tmp/talon-competition-20261110/review.html`.
It includes every frame at 3× on a common centre/baseline with head/torso guides,
native script order and timing in exact-palette full-frame 60Hz GIFs, and native
Sonic/Tails beside MICA/TALON split-screen race captures. Speed-driven walk/run
reviews are labelled 8-tick samples; browser GIF delays may clamp 10ms frames.
A short Azure Lake race is a rendering witness, not all-arena certification.
