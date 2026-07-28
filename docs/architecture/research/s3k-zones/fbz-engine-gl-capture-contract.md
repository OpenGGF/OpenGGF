# FBZ deterministic engine GL capture contract

Status: approved native contract; implementation is present and native evidence capture is pending final build verification. Compatibility capture remains fail closed.

The capture runner must create a real, hidden GLFW/OpenGL context and render through the production frame pipeline. A headless physics runner, synthetic image, tilemap-only renderer, or trace-state hydration is not an acceptable visual reference. Native validation renders a 320x224 framebuffer. Compatibility validation renders the exact configured framebuffer width and 224-pixel height, while separately recording the native 320x224 comparison crop and its horizontal origin.

## Proposed API surface

- `FbzVisualCaptureTool`: command-line entry point accepting `--rom`, `--manifest`, hash-bound `--evidence-amendment`, `--checkpoint`, `--mode-key`, `--framebuffer-width`, `--framebuffer-height=224`, `--native-crop-x`, `--native-crop-width=320`, and `--output-root`. It verifies all source and build inputs before boot.
- `HiddenGlCaptureSession`: owns the hidden width-by-224 GLFW window/context, production graphics initialization, deterministic frame stepping, complete scene/HUD/fade ordering, full-frame and native-crop framebuffer readback, and teardown.
- `FbzVisualScenarioDriver`: maps every manifest checkpoint id to a typed setup recipe. It may load an authoritative route savestate or use explicit test-fixture state, but must read back every mutation before advancing and must never substitute a nearest-coordinate trace frame.
- `FbzVisualStateProbe`: package-private, read-only snapshot of zone/act, player centre coordinates, camera bounds/position, foreground/background event routines and words, plane assignment, collision/shake/boss flags, PLC/AniPLC progress, object identities/routines, and relevant queue state.
- `FbzVisualFixturePort`: package-private deterministic fixture operations for event words, camera/player centre coordinates, AniPLC counters, and object/event-controller allocation. This is validation-only infrastructure; production event behavior remains owned by the normal managers.
- `FbzVisualCaptureReceipt`: JSON sidecar containing commit id and dirty-worktree fingerprint, built artifact path/SHA-256, effective configuration and mode key, ROM/BK2/manifest hashes, source savestate hash and origin, deterministic input-schedule hash and origin, checkpoint id, recipe version, preboot verification, RNG seed and complete capturable RNG state, pre-mutation state, requested mutations, readbacks, executed phases, observed transient routines/hooks, final state, framebuffer/native-crop geometry, PNG SHA-256 values, and rejection reason.

## Mode keys and non-overwriting paths

Mode keys are stable identities, not display labels. At minimum the runner supports `native-320`, `widescreen-352`, `widescreen-400`, `widescreen-528`, `widescreen-800`, `multi-sidekick-native`, `multi-sidekick-duplicate-native`, `s1-donation-native`, `s2-donation-native`, and the explicitly selected combined compatibility modes. Each receipt records the complete effective character roster, donation source, width, crop, and feature flags.

| Base mode key | Full framebuffer | Native crop `(x,y,width,height)` |
| --- | --- | --- |
| `native-320` | 320x224 | `(0,0,320,224)` |
| `widescreen-352` | 352x224 | `(16,0,320,224)` |
| `widescreen-400` | 400x224 | `(40,0,320,224)` |
| `widescreen-528` | 528x224 | `(104,0,320,224)` |
| `widescreen-800` | 800x224 | `(240,0,320,224)` |

Character/donation variants append stable suffixes to the base mode key and retain that base mode's exact geometry. The runner rejects a supplied crop that does not equal the table entry.

Native outputs remain at the frozen manifest's `output` path. Every compatibility output is isolated beneath `target/fbz-validation/compat/<mode-key>/<checkpoint-id>/full-<width>x224.png`; its native crop is `crop-320x224.png`, and provenance is `receipt.json`. A compatibility mode must fail before rendering if its resolved path aliases any native or other mode's PNG/receipt.

## Fail-closed execution

1. Resolve the requested mode and output paths. Native mode disables widescreen, multi-sidekick, donation, debug overlays, rewind, editor, and every non-native extension; compatibility modes enable only their declared matrix dimensions.
2. Before engine boot, verify ROM, manifest, BK2/input schedule, route savestate, built artifact, effective configuration, git commit/dirty fingerprint, mode geometry, and every source hash. Record the verification result even on rejection.
3. Initialize the declared RNG seed/state before loading FBZ; record it before and after every capture phase.
4. Load FBZ and reach the recipe's named phase using the production frame loop and the hashed input schedule or declared fixture route. A queued native title card must be completed or explicitly consumed before recipe stepping; every captured step must remain `LEVEL` -> `LEVEL` and advance `Level_frame_counter` by exactly one. A title-card/no-op step is a rejection, even when direct level rendering could still produce a plausible PNG.
5. Assert the full pre-state. Apply only declared fixture mutations and assert their readbacks.
6. Execute the exact number and ordering of camera, event, object, PLC/AniPLC, deform, and render phases named by the recipe.
7. Observe required transient states (redraw routine, DMA tuple, object allocation, queue completion) and final state. Native start separately proves the all-zero 16-byte initialization invariant, the first animation tick `[63,1,7,1,1,1,7,1,7,1,0,0,0,0,0,0]`, and the independently reviewed exact state of the first fully visible gameplay frame. It must not assume that visible frame is `Level_frame_counter=1`. Each dedicated AniPLC recipe must leave counters unmodified, capture a zero-step control plus at least four consecutive production one-step frames spanning a natural timer expiry/frame-index advance, hash the exact destination VRAM and crop for every frame, and correlate the advance with an independently reviewed visible region. These checks run before publication or acceptance.
8. Render the accepted production frame (or every cadence frame), read the full framebuffer and declared native crop, write versioned PNGs and per-frame receipts atomically, then verify all hashes and dimensions.
9. On any mismatch, delete every PNG for that checkpoint/mode and emit only a rejection receipt. Nearest coordinate/frame candidates remain non-authoritative.

## Output and review

The native tool writes checkpoint PNGs to the manifest's `output` path and sidecars beneath `target/fbz-validation/provenance/native-320/`. Compatibility writes only to its mode-key directory described above. Time-series captures use the versioned `aniplc-cadence-*-v2-<index>` naming scheme and include an individual receipt per frame; the preserved unversioned series are always `SUPERSEDED`. Reference acceptance remains a semantic, feature-presence review against the locked-on BizHawk reference; a raw pixel diff is diagnostic only because the engine and emulator capture pipelines are not guaranteed to have identical raster post-processing.

## Required compatibility evidence matrix

After native captures pass, run the same scenarios without changing the accepted references. Each matrix cell needs a distinct receipt and explicit assertions; a screenshot alone is insufficient.

- Multi-sidekicks: the main playable plus at least three simultaneously active sidekicks must cross every event boundary and boss/exit gate, proving more than two interacting participants. Exercise solid carriers, grabs/attachment ownership, moving platforms, touch hazards, damage/knockback, and arena/camera locks without assuming Player 2 is the final participant.
- Duplicate characters: include at least two sidekicks sharing one character type. Record stable distinct playable identities, allocated DPLC/appendage pattern-bank bases, owner ids for carrier/grab/hazard contacts, and prove no atlas or interaction-owner aliasing.
- Widescreen: execute widths 352, 400, 528, and 800. At every boundary, miniboss, subboss, plane transition, boss arena, exit release, and capsule gate, assert exact left/right/top/bottom camera bounds, target bounds, player/sidekick containment, spawn/despawn margins, and that camera release never exposes a death fall or enables premature interaction. Record full framebuffer plus centered native crop.
- S1 donation: use a Sonic 1-donated playable with no Spindash capability and prove the complete FBZ route, all progression objects, carriers/grabs, bosses, exit door, and capsule transition remain reachable. Any compatibility activation must be capability-driven and clearly labeled; do not branch on the checkpoint, route, or frame.
- S2 donation: run the same full route and complete checkpoint capture set as native mode with the Sonic 2 donation active. Prove every checkpoint retains native S3K event, object, camera, boss, exit, and capsule semantics and that no donor-specific workaround or S1 no-Spindash compatibility path activates.
- Combined modes: run at least S1 donation at width 800 with more than two sidekicks including a duplicate character, proving the individual compatibility mechanisms compose without changing native behavior.

## Current blockers

- The 21 native recipe executors and their hidden-GL/provenance pipeline require a clean serialized Maven build and native capture sweep.
- Reference PNGs and named semantic comparison sidecars remain incomplete; engine PNGs alone cannot turn the generated validation report green.
- Compatibility-mode configuration/receipts and their full evidence matrix remain intentionally fail closed.
- BizHawk/BK2 complete-run trace capture is the final polish step after implementation and native visual validation are largely complete.
