# MICA — locked-on S3K ending art

Original native-coordinate pixel drawings, generated without raster scaling or
native artwork in the output. Run from repository root:

```sh
python3 -I examples/mica-and-talon/art/mica/endings/s3k/build.py --review /tmp/mica-art-endings-s3k-20261108
python3 -I examples/mica-and-talon/art/mica/endings/s3k/build.py --verify
```

Python 3, Pillow, PyYAML and the pinned disassemblies are required. No Java build.
`--group` regenerates only one group. The JSON-subset `s3k.yaml` records source
labels, native indices, piece geometry, canvas bounds, hotspots, palette bindings,
DPLC capacities and native script timing. PNGs are 4-bit indexed with binary
transparency. Normal art uses the unchanged shared MICA palette. Gilded poses
embed the first gold cycle; Float2 also supplies `gilded_file` variants alongside
the shared jade drawings. All retain the same indices: apply `presentation/gilded.yaml` at runtime. Disposable review sheets
show those powered poses with the first S3K Gilded cycle at 1× and 4×.

Static source tile addresses overlap between frames. Pack/rebase replacement
frame tiles in the engine bank; do not load every drawing at its original tile
offsets simultaneously. Piece geometry remains native. The verifier checks
piece coverage and aliases within each frame, not a final packed engine bank.
Super reunion selectors 0/1 are conservative coverage requested by the inventory;
the native Super bank renders as scrambled fragments through those selectors.
Only selector 2 is a coherent native final pose. Comparisons label this limitation.

Palette line 2 belongs to reunion portraits; the other surfaces use line 0.
Companions, plane art and the trio banner must retain their own palettes; the
engine lane must reserve/remap shared lines rather than recolouring those assets.
These are art handoff images, not runtime or ending-route certification.

All 25 inventory drawings are present: five reunion portraits, two passengers,
four trio eyecatch poses, eight shared Float2 views, one plane ride and five
transformation poses. Float2 turns in depth through side/front/back views; it
is not a screen-space tumble. Transformation retains the native repeated-frame
script rather than treating repeats as new drawings. Reviews include GIFs of
Float2, its Gilded palette variant, passengers, trio and transformation (GIF
frame durations round to 10ms).

Review repairs reduced/repositioned reunion hands and boots to fit the native
piece union, replaced a rejected Float2 tumble with depth turns, and moved the
transformation gloves forward of the hood. Weak points: small 8px passenger
facial details reduce to colour clusters; trio's idle change is mostly a boot
lift; Float2 rear views have restrained depth changes; gold crest flare is
rounder and less expansive than native Super Sonic's quills. Final bank packing,
shared palette-line allocation, Gilded fade/controller binding, and real scene
background/contact checks remain engine-lane work. No engine code or other
manifest is changed by this lane.

## Owner-directed native-pose redraw

`native-pose.json` stores exact prompts, raw-image SHA-256 and the reduced original
MICA pixels. `build.py` regenerates these rows instead of the earlier procedural
art. Native art remains disposable in `/tmp/mica-np-endings-s3k-20261108/native/`;
each reference is enlarged 8× nearest-neighbour before GPT Image receives it,
the MICA concept and the approved pilot. Super selectors 0/1 additionally receive
the matching normal pose because their own native art is scrambled. Gilded
Float2 variants share the normal frame's indices and use the recorded gold ramp.

With the disposable inputs available, re-reduce a group using:

```sh
python3 -I examples/mica-and-talon/art/mica/endings/s3k/native_pose_redraw.py --review /tmp/mica-np-endings-s3k-20261108 --group reunion
```

The reducer fits reunion portraits as complete silhouettes inside the irregular
piece union; these may be smaller within the unchanged canvas. Other groups
preserve native height and compact rows that exceed native piece spans. This
can narrow hood, glove or boot clusters; compare the raw column when judging
that compromise. All silhouettes receive an inside ink edge. Reviews retain native 8× inputs, full raw GPT images, and four-column
native/raw/reduced/previous MICA pages at 1×/4×. These checks certify image storage
and native layout only; scene backgrounds and animation contact need engine review.
