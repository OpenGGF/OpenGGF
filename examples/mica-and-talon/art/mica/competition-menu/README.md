# Competition menu portraits

`player.png` replaces the native full-body wheel sprite, also used on the arena
selector and Grand Prix/Time Attack results. `record.png` replaces the smaller
Time Attack record pose. Indexed PNGs use the character's independent palette.
The hotspot is the native foot-plane origin: player image horizontal centre and
exclusive bottom, record X=11 for MICA / 8 for TALON and exclusive bottom.

Built-in GPT Image generated two alternatives per surface against the actual
native menu pose enlarged 8× nearest-neighbour and both approved model sheets.
The real menu palette was decoded from ROM $20A55E, with record icons using the
native menu BG palette at $39D282. No decoded native pixels are packaged.
Selection/reduction/opaque areas and generation hashes are in each provenance
JSON. Both characters use the final refinement against the approved WAIT frames.
Initial TALON player alternative 0 reduced too small; the first MICA record
pose missed its overhead glove phase. Both were rejected and regenerated. Reduction is uniform
Lanczos with thresholded alpha and fixed palette assignment; no painted contours.

Independent sqrt(opaque area / native opaque area): MICA player 1.045, record
1.090; TALON player 0.979, record 1.002. Head/torso guides and native-foot-plane
lineups are in the review. Native full-body heights are 39/35; authored 39/36.
Record heights are native 24/19 and authored 24/19. MICA's tail increases icon area;
TALON's wings replace the native tail silhouette.

Review: `$HOME/scratch/agent-tmp/mt-competition-wheel-20261110/review.html`.
Raw generations and native reference renders remain in that scratch directory.
The review includes production headless menu captures beside native, using the
same selection/results/records state; this is menu rendering evidence, not an
all-arena gameplay certification. Character-name text is N/A: native Competition
menus identify characters by sprites, with player numbers and arena labels.
