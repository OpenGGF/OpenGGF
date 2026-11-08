# MICA whole-cycle art experiment — 2026-11-08

Isolated comparison candidates; nothing here is assembled into the mod or replaces
live `../frames`. The owner judges adoption. Built-in `image_gen.imagegen` exposes
one image result, with no GIF/WebP/APNG, sequence output, or model/version selector.
Thus each attempt requested one evenly spaced horizontal sprite strip. The claim
“GPT Image 2.6 supports contiguous animations” could not be tested with this tool.
No API fallback or Java build was used.

Three attempts per animation, using the freshly mapped S2 cycle (correct player
palette, 8× nearest-neighbour), `concept/mica.png`, and approved native-pose MICA
frames as separate inputs. Exact prompts, all attempt hashes, chosen raw paths,
base/pin identities, cell bounds, reference mapping labels, boxes and hotspots are
in `provenance.json`. Native pixels remain comparison-only in scratch.

Selected: WALK attempt 2; RUN, ROLL and SPINDASH attempt 3. Reduction uses the
existing `pipeline.py` functions: edge-connected magenta removal (tolerance 50),
largest component, Lanczos fit to the matching native tight box, shared palette
assignment, inside ink outline. No hand-redrawn limbs, head transplant or bitmap
rotations. Repeated native mapping IDs alias the first reduced drawing. This is
light consistency cleanup, recorded per step; all original generated occurrences
remain visible in the raw strips. Native envelopes cause anisotropic fitting,
so the long tail/hood can look squeezed during narrow phases.

The 30 script steps comprise 23 unique drawings: WALK 8, RUN 4, ROLL 4 spin + blur,
SPINDASH 5 charge + interleave. `recipe.json` holds final original pixel rows;
`frames.yaml` records native hotspots. S2 source owners: `SonAni_Walk`,
`SonAni_Run`, `SonAni_Roll`, `SonAni_Spindash`, `SAnim_WalkRun`, `SAnim_Roll` in
`docs/s2disasm/s2.asm`, pinned by provenance. Steady previews select normal Sonic
inertia $0400 for WALK (5 ticks), $0600 for RUN (3 ticks) and ROLL (1 tick).
SPINDASH uses script duration byte 0 + 1 = 1 tick. GIF centiseconds are rounded
cumulatively, preserving the period as closely as possible. Slow views are 150 ms.
No universal fixed walking/running/rolling interval is claimed.

Review and all twelve raw strips:
`$HOME/scratch/agent-tmp/mica-cycle-exp-20261108/review.html`.
The comparison includes earlier branch per-frame art for WALK/RUN, not the other
lane's unfinished regeneration. Native assets and disposable previews are not
committed. Complete reduction/review method is retained as `reduce.py`.

```sh
python3 examples/mica-and-talon/art/mica/cycle-experiment/verify.py examples/mica-and-talon/art/mica/cycle-experiment
# Ordinary regeneration needs only committed original rows and palette:
python3 tools/modding/playable_art/pipeline.py examples/mica-and-talon/art/mica/cycle-experiment/recipe.json
# Re-slice/reduce/review needs the external scratch inputs:
python3 examples/mica-and-talon/art/mica/cycle-experiment/reduce.py --work $HOME/scratch/agent-tmp/mica-cycle-exp-20261108
```

Observed checks: 30 native boxes/hotspots, shared palette indices, binary alpha,
inside ink boundary, repeated aliases and byte-identical pipeline regeneration
pass. No Java, full engine suite, GGFP bake or in-game verification was performed.

Design assessment: whole strips improve identity/proportion consistency over the
earlier per-frame art, particularly RUN's head and boot wheel. WALK's arm swing
and boot shading change, but near/far alternation remains ambiguous (especially
phases 4/8). RUN phases 2/4 remain too similar and white cuff accents persist.
ROLL is the strongest result: readable quarter turns and an explicit blur.
SPINDASH's closed shell matches native pose intent, but scale travel reads as
texture shimmer rather than strong rotation/charge. Whole-strip generation helps
consistency; it does not solve animation mechanics automatically.

## Follow-up

[Experiment 2](exp2/README.md) adds the explicit native leg-depth table and 15 new
whole-cycle attempts, with separate reduced candidates and review. First-round
results above remain intact.
