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
transparency and the unchanged shared MICA palette. Gilded art keeps the same
indices: apply `presentation/gilded.yaml` at runtime. Disposable review sheets
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
