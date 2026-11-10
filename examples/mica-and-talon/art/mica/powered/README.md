# Gilded powered-pose research checkpoint

This directory is **not an installed powered sheet**. The playable Gilded form
still uses normal MICA art with its existing palette cycle. None of the 70
powered audit obligations has moved to authored, alias or N/A on this branch.

`inventory.py` reads the pinned native animation tables and expands movement's
implicit slope and S2 alternate-walk frame selection before counting frames.
It emits research JSON and, with `--references`, native pose PNGs at 1× and
8× nearest-neighbour **only under scratch/agent-tmp**:

```sh
python3 examples/mica-and-talon/art/mica/powered/inventory.py \
  --output "$HOME/scratch/agent-tmp/mica-super-20261110/native" --references
```

S2 has 34 merged script obligations, six table overrides, 140 reachable frame
indices and 86 indices in those override scripts after implicit selection.
S3K has 36 merged script obligations, six table overrides, 106 reachable frame
indices and 50 indices in override scripts. These are frame-index counts,
**not a final count of new MICA drawings**. Transformation is already referenced
by each normal table and must be considered separately despite not being an
override. S3K powered mapping/DPLC identity must also be checked for scripts
shared with normal Sonic. Hyper shares the powered geometry.

The optional reference pass deduplicates complete RGBA/hotspot references and
compares S3K powered mappings/DPLCs against the normal bank. The first pass
renders 140 distinct S2 references and 102 distinct S3K references. S3K `$C7`
has an additional contextual-bank requirement: the mapping addresses 22 tiles
but its DPLC loads 21. The exporter records and omits this isolated image; it
must not invent the retained tile or call a partial image a complete reference.
This reference limitation does not establish a runtime defect.

The upright pilot is a GPT Image native-pose transformation, reduced to the
native `$72` reference box. It is a review candidate, not accepted gameplay
art. Its raw generations, native references, reduction comparison and review
HTML belong in the task scratch directory. The selected pilot's original
indexed pixels and provenance may be retained here without installing them.

Engine proposal, binding/rewind obligations, art acceptance and remaining work
are in the powered-bank section of the existing
[character design](../../../../../docs/architecture/designs/2026-11-08-original-character-mod.md).
