# MICA whole-cycle experiment 2 — 2026-11-08

Isolated follow-up to the first experiment. Live frames, assembly and mod assets
are untouched. Built-in imagegen generated six additional WALK strips and three
each for RUN, ROLL and SPINDASH (15 separate still-strip calls). Exact prompts,
SHA-256s, selected cells and per-attempt reviews are in `provenance.json`.
Model version is not exposed by the tool. No API fallback was used.

[Leg-layering table](LEG-LAYERING.md) records the native WALK/RUN phase order,
forward leg and camera-depth ownership. Near stays lit/in front; far stays shaded
behind. The earlier prompt incorrectly conflated depth with forward travel.
WALK attempt 6 and RUN attempt 2 were checked against this table after reduction.
WALK near boot is lifted in 00, forward in 01–03, planted in 04, back in 05–07;
the far boot is shaded throughout. No remaining near/far reversal was observed.
The far passing boot in 04 is largely occluded, as expected at the crossing.

Selected complete strips: WALK 6, RUN 2, ROLL 2, SPINDASH 2. No per-frame repairs
were needed. ROLL retains right/down/left/up profile poses. SPINDASH has five
visibly different compressed/arched/nose-down/rear-raised/launch poses, rather
than a texture shimmer. It exposes the head, so is an original charge adaptation,
not literal closed-shell native pose parity. RUN retains pale cuff details, and
native-box fitting can compress the hood/tail or change apparent proportions.
Small face/boot clusters still vary in ROLL; runtime contact remains unverified.

Reduction uses the existing pipeline: magenta edge flood (tolerance 50), largest
component, Lanczos native-box fit, unchanged fixed MICA palette, inside ink.
Repeated native mappings share one drawing. No script repaint, limb transplant,
bitmap rotation or native pixels. `recipe.json` stores only original MICA rows,
so ordinary regeneration needs neither raw images nor the disassembly.

Review page, GIFs, annotated phase sheets and all raw/reduced attempt sheets:
`$HOME/scratch/agent-tmp/mica-cycle-exp2-20261108/review.html`.
Selected cells are outlined in the all-attempt sheets. Each animation has paired
native-timing and slow inspection GIFs. Timing uses the first experiment's native
speed examples: WALK inertia $0400 (5 ticks), RUN $0600 (3), ROLL $0600 (1),
SPINDASH script (1); cumulative GIF centisecond rounding preserves cycle length.
Native references remain comparison-only in scratch.

```sh
python3 examples/mica-and-talon/art/mica/cycle-experiment/verify.py examples/mica-and-talon/art/mica/cycle-experiment/exp2
python3 tools/modding/playable_art/pipeline.py examples/mica-and-talon/art/mica/cycle-experiment/exp2/recipe.json
# Re-slicing requires scratch references.json, prompts.json, selection.json and raw strips:
python3 examples/mica-and-talon/art/mica/cycle-experiment/exp2/reduce.py --work $HOME/scratch/agent-tmp/mica-cycle-exp2-20261108
```

Art-only scope: no Java build/tests, GGFP bake or in-game certification.

Observed verification: the existing cycle verifier passed all 30 steps / 23 unique
mappings, including palette, binary alpha, ink boundary, native boxes/hotspots,
alias equality and byte-identical regeneration. Review image links resolve.
