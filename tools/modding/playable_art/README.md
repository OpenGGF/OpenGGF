# Native playable art preprocessing

Python 3 + Pillow (checked with Pillow 13.0.0); no Java build or ROM required.
Run from repository root:

```bash
python3 tools/modding/playable_art/pipeline.py examples/mica-and-talon/art/mica/recipe.json --review /tmp/mica-art-review-20261108
python3 tools/modding/playable_art/verify.py examples/mica-and-talon/art/mica
```

`recipe.json` selects equal-grid atlas cells and native dimensions. Sources may
have alpha or an explicit RGB `background` key for edge-connected flood removal.
The largest connected alpha component removes detached generation noise; use a
separate crop/source when a pose deliberately has detached parts. Downscaling uses
Lanczos, followed by thresholded alpha, nearest fixed-palette colour assignment
without dithering, and a one-pixel inside silhouette outline at index 1.
There are no partially transparent or off-palette output pixels.

`palette.yaml` contains transparent index 0 plus exactly 15 unique opaque colours,
all channels on the Mega Drive eight-level ladder. The pipeline never learns a
new palette per frame. Both YAML files use the JSON subset of YAML 1.2 so Python
stdlib can read them without PyYAML. Frame PNGs use indexed mode P, 4-bit indices,
and transparent index 0; every frame carries the same palette in the same order.

Standing `anchor` defaults to `[round(width * .64), height - 19]`, accounting for
MICA's long tail to the left. An explicit `[x,y]` is a source-local native hotspot;
`[-1,-1]` centres an airborne pose. Ball anchors are `[14,14]` in 29×29 pixels.
The temporary 80×80 alignment canvas is tight-cropped for output; exported
`hotspot` is the engine centre relative to that crop, in native pixels. Reconstruct
position with `top_left = engine_centre - hotspot`. A foot baseline at centre+19
is the **exclusive bottom edge**, so the last occupied row is centre+18.

`patches` contains hand-authored `[x,y,palette_index]` edits in the temporary native
80×80 canvas, whose engine centre is `[40,40]`. Patches refine eyes/laces and remove
the generated hanging bar. Final silhouette ink is reapplied after patches.
Derived frames use `derive` and a nearest-neighbour `angle` about the engine centre.
The recipe orders prerequisites before derived frames. Check patches and native
silhouettes again when replacing a source; coordinates are intentionally explicit.

The handoff `frames.yaml` records id, canonical animation name, order, file,
hotspot, angle set, notes and suggested 60Hz preview ticks. WALK is set-major:
8 flat, 8 up45, 8 steep, 8 inverted; RUN uses the same ordering with stride 4.
Angles are +0/+45/+90/+135 degrees visually counterclockwise, before engine facing
and orientation. These are rigid rotations, with repeated outline cleanup, rather
than separately drawn ground-contact deformations. Integration must check the W2
slope stride and direction contract before materializing the sheet.

`HOMING_DIVE` is a **custom handoff key**, not an existing `CanonicalAnimation`
enum member. The ability lane must bind the four scale-first poses explicitly.
STOP intentionally shares SKID artwork. Preview timings are illustrative; W2
supplies native speed-driven walk/run timing. The assembler supplies the fixed
script timing and end actions. The reducer stops at indexed frames; the assembler below supplies mapping pieces
and converter input.

Review generation writes 1×/4× contact sheets (nearest-neighbour hard pixels) and
4× GIFs for every animation and slope set. They are disposable, outside Git.
The verifier checks palette identity, indices, binary transparency, ink boundary,
standing and ball geometry, complete core coverage, keyed removal preserving
an enclosed eye white, and byte-for-byte frame regeneration.

## MICA review notes

Sources are built-in GPT Image edits against the approved concept; prompts and
accepted/rejected cells are recorded in the art's `generated/prompts.json`.
The checkpoint reviewed WAIT/WALK/ROLL at 1× before expanding. The first WALK
was replaced for subtle strides; the first dive was replaced because the muzzle
led the attack. Every final frame was checked on the full 1× contact sheet, with
larger eye/grip detail inspection. Standing WAIT and WALK are 39 pixels tall;
ROLL is 29×29. Crouch, sprint and airborne poses vary in bounds with their pose.

Weakest areas: slope rotations need engine contact validation; the first two RUN
phases are similar; SPRING has a small hood-shape shift between phases; very small
jade laces and scale lines merge at 1×. These are usable core art handoff frames,
not a claim that every native route or presentation state is certified.
Remaining W7 presentation/super/cutscene art is outside this core-set handoff.

## Assemble and bake the core sheet

```sh
python3 tools/modding/playable_art/assemble.py examples/mica-and-talon/art/mica examples/mica-and-talon/src/main/mod
python3 examples/mica-and-talon/build.py
python3 -m unittest discover -s tools/modding -p test_playable_art_assemble.py
```

The build reruns assembly before conversion. The atlas uses 8-bit indexed PNG
(the converter codec requires 8-bit storage), with only the same 16 indices. Replace/rebuild source frames, then
rerun it; no sheet coordinates are maintained by hand. Assembly preserves indexed
pixels and hotspots, pads each crop to tiles and cuts pieces at 32 pixels (4 tiles).
The current 96-frame sheet uses at most 36 tiles per frame. The converter's bank
report is the total resident source bank, not a 36-tile streaming bank: its current
full-frame runs retain distinct destination offsets. Do not confuse the two costs.

The 17 canonical animations use S2 Sonic as their authored script reference:
`SonAni_Walk` through `SonAni_Hurt` (s2.asm:38692-38754). Positive durations are
native timer bytes plus one. Host profiles supply WALK/RUN/ROLL/PUSH speed timing.
DUCK/LOOK_UP hold the last pose (the native one-frame repeat); SKID/STOP, SPRING
and GET_AIR switch to WALK. SPRING's two authored phases divide its native 48-tick
interval; GET_AIR retains four 12-tick steps with repeated art. Other original
cycles retain their reference's per-step duration and loop. WAIT condenses the
long native fidget into four original poses, preserving its 186-tick initial wait.
These are authored pose adaptations, not an assertion of stock frame identity.
WALK/RUN slopes are ordered flat/up45/steep/inverted, native selectors 0/2/4/6.

`mica-custom-frames.json` records HOMING_DIVE mapping indices for the ability
lane. All four frames are in the image and GGFP; no canonical enum member is
invented, and no native animation selects them yet. This file is a build hook,
not converter schema. Re-read it after source manifest reordering.
