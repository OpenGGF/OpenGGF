# MICA art round 3 — 2026-11-09

Owner-selected A SPINDASH, SPRING, HURT and HOMING_DIVE are unchanged.
WALK uses strip attempt 2; WAIT uses strip attempt 2 with nine native drawings;
ROLL uses strip attempt 2 with four broad-band turns and one highlighted blur.
RUN retains B2 phases 0/2, uses isolated generated contact attempt 3 for phase 1
and strip attempt 1 for phase 3. Exact prompts, raw hashes and selections are
adjacent. Raw outputs and decoded native references remain outside Git.

Native-pose inputs were exported in the real S2 player palette and enlarged
8x nearest-neighbour. Reduction first takes each equal strip cell, removes
transparent margins, then creates a native-aspect window around the whole pose
with transparent padding. Uniform Lanczos reduction, fixed-palette assignment
and inside silhouette ink follow. Unlike experiment B, width and height are
never independently fitted to a tight silhouette. No painted pixel edits.
The final original indexed rows in ../recipe.json reproduce the chosen art.

SonAni_Walk/Run/Roll/Wait/Spindash in docs/s2disasm/s2.asm own the script order.
The six charge drawing IDs reconcile as spindash-blur = $42 and
spindash-00..04 = $43..$47. The canonical script is
blur,00,blur,01,blur,02,blur,03,blur,04, each one tick. The assembler now resolves
these symbolic IDs and ROLL's alternating $41 blur after packing, preserving
integration's nativeFrames aliases and object-owned HANG drawings.
SPRING's one retained native $5B drawing lasts 48 ticks before WALK.

S2 WAIT uses mappings 1..9, six ticks per script entry, including repeated stand,
blink, bored glance, taps, hand gesture, lowering and late reclining feet.
S3K's equivalent is named AniSonic05 in the pinned Anim - Sonic.asm, not
AniSonic_Wait: $BA stand, $BB blink, $BC bored, $BD/$BE taps, then
$AD/$AE/$AF/$D9 gestures, ending FE $35. We follow the S2 sequence here.
Both faces remain relative to standing direction and mirror with the sprite.

**Engine handoff limit:** native S2 WAIT ends FE6 (repeat the final six entries).
The current converter exposes loop/hold/next only; the assembled WAIT repeats
the complete sequence. native-scripts.json and frames.yaml's nativeLoopBackSteps
retain the exact intended native repeat for the engine lane. Review previews
show the native intro and final repeat twice, then restart for viewing.
No Java was changed or built. The full native sequence is not runtime certified.

The unchanged deterministic derive_slopes.py rebuilt WALK/RUN up45, steep and
inverted from these final flats. The five four-frame dive direction sets are
in ../homing-directions/frames.yaml because the assembler rejects their new keys.
HOMING_DIVE remains A's forward-down pose. The sidecar provides an explicit alias;
its indices must be packed by the engine lane rather than copied from this sheet.

Visual inspection: near limbs remain lit/in front and far limbs dark/behind;
walk's forward foot and opposite forward arm swap between half-cycles. Narrow
passing poses occlude most of the far boot. RUN phase 3 still has a small head bob
relative to the retained B2 body; phase 1's new shoe arc reaches muzzle height.
WAIT's bored turn is restrained compared with Sonic's camera-facing glance.
Dive UP/FORWARD_UP rotate the face relative to world upright; phase 2 is rounder
than its neighbours and pulses. No independent face correction was painted.

Review, native references, raw generations and reduction/selection scripts:
$HOME/scratch/agent-tmp/mica-art-round3-20261109/review.html

Observed validation: 48 tools/modding Python tests passed, including Gilded sync;
verify.py passed palette/geometry/ink/regeneration. 116 live frames, 18 assembled
animations, max 30 tiles per frame; 20 additional directional sidecar frames.
No Java build, GGFP conversion or gameplay verification.
