# MICA playable art

The 274-frame / 52-key sheet combines approved core, slope and earlier native
pose batches with the 2026-11-10 object-pose closure. Rebuild with the maintained
playable-art `pipeline.py`, then `assemble.py`; the recipe contains original
indexed pixels only. No native reference pixels or raw generations are packaged.

`object-poses-wave4/native-pose-provenance.json` records native mapping indices,
selected attempts, hashes and cleanup; `prompts.json` records the exact prompts.
Sixty new object drawings, one legacy inverted run and five normal transformation
phases are generated against native 8× nearest references and the approved model.
Three object aliases reuse existing spin/grip art. Frame/alias additions form a
single appended block to ease integration with the powered-art lane.

Corkscrew and gravity-loop references include perspective changes and distinct
limb placements; the outstanding native poses are not duplicate planar quarter
turns. Their silhouettes were checked for exact 90°/mirror equivalence before
selection. Existing approved slope and batch-3 deterministic rotations remain
unchanged. The S3K extra art bank supplies native mappings at/above $DA.

Reduction preserves aspect and uses the locked palette, alpha threshold and
inside ink. The $B5 overhead pose is uniformly reduced from 50 to 48 pixels to
fit the existing atlas; $93 is re-reduced 6% larger following the independent
opaque-area check. See `object-poses-wave4/size-check.json` for delivered set
measurements. Review raw attempts and native pixels stay in the task's external
scratch directory, linked in provenance. Native script timing and camera/facing
stay host-owned; these are artwork obligations, not all-route certification.

Blue Sphere's eleven nonblank rear-view poses use a fresh native-style generation
set with native per-frame colour budgets, exact native script previews, aligned
style gates and a positioned run/turn/jump capture. See [the wave3 handoff](wave3/README.md#blue-sphere-native-style-redraw-2026-11-10).
The set's independent area ratio is 0.983; native selectors and hotspots stay
unchanged. Coverage remains authored rather than claiming new fallback closure.
