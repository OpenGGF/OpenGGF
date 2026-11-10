# Gilded powered playable poses

The S2 and S3K GGFP powered banks are installed through `Form.poweredArt`.
Hyper shares the S3K Super geometry. Thirty-four generated base drawings cover
native overrides and shared-script movement/secondary-balance poses; deterministic
Scale2x rotations supply WALK/RUN slopes. `generation.json` records raw hashes,
selected cells, references, reduction geometry and independent area checks.
Normal `frames.yaml` and `recipe.json` are untouched.

Each host handoff has canonical phase keys. `normal-phase-aliases.json` records
phase reuse without duplicate normal PNGs; whole-animation aliases live in
`MicaGilded`. Shared native script pointers alone are not geometry evidence:
S3K blink, secondary balance, bubble recovery and lying mappings differ.
The latter reuse already-authored powered movement/stance geometry.
The native Dash2/Dash3 reserved table scripts have no live Sonic selector.
Transformation explicitly aliases MICA's existing five-phase SUPER_TRANSFORM.
Missing complete families retain the whole bound normal animation.

The old upright pilot is retained as provenance only; installed stances come
from the new native-pose generations, reviewed with the approved WAIT.
The powered palette changes pale-gold face/belly only in the powered view;
normal MICA's cream slots and S1's donor-free authored form retain their behavior.

Rebuild with `python3 examples/mica-and-talon/build.py`. Disposable native
references, exact-timing motion review and positioned EGL captures remain in
`$HOME/scratch/agent-tmp/mica-super-20261110/`; no decoded native pixels ship.

`pose-identity.json` records the reproducible silhouette/hotspot comparison for all
58 shared scripts. Reproduce after exporting references with `inventory.py
--output <scratch-native-directory> --compare-poses`.

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

The research reference pass deduplicates complete RGBA/hotspot references and
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

Independent size check after the selected 8×-native variant (square root of mean
opaque area relative to the host's native poses):

| Host | Walk | Run | Push | Wait | Balance | Duck |
|---|---:|---:|---:|---:|---:|---:|
| S2 | 1.021 | 1.001 | 1.011 | 1.007 | 1.013 | 1.004 |
| S3K | 1.012 | 0.971 | 1.036 | 1.014 | 0.974 | 0.980 |

S3K Blink is N/A: its interrupt is owned by competition control, not the native
single-player powered WAIT loop. Its C7 mapping also requires retained bank
context and is deliberately not recorded as a proven normal-pose alias. No base set falls
below 0.95. `generation.json` includes individual measurements; use the baseline
and head/torso guide review as well as area, which alone does not prove scale.

Observed production coverage (`TestCharacterArtCoverageAudit`):

| Host | Authored | Normal aliases | N/A | Fallback |
|---|---:|---:|---:|---:|
| S2 | 6 | 26 | 2 | 0 |
| S3K | 10 | 24 | 2 | 0 |

S2 N/A entries are retained Dash2/Dash3 data ($12/$13). S3K N/A entries are
Dash2 ($12) and competition-owned Blink ($0A). The four entries remain in the
matrix with reasons rather than being removed from its denominator.
The posed EGL review records standing, walking, running, pushing and ducking
in EHZ2/AIZ2 and S3K Hyper, beside native powered Sonic, with 80 declared rings
and full production frame steps. It is presentation evidence, not a route or
physical wall-contact certification.
