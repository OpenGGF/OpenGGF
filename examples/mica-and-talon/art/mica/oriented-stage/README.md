# Native special-stage orientations — 2026-11-10

The optional final-orientation bank keeps the native animation selector and its
clock. Native Sonic/Tails art and behavior are unchanged. The example uses only
the official additive StageSpriteSet helpers. The runtime path still owns sprite
order, first-pixel ownership against shadow/spheres, priority, palette and fades.

Native inventory:
- S3K `loc_907E` (sonic3k.asm:11502-11519) accumulates velocity in the native
  animation timer; `loc_90A8` (sonic3k.asm:11521-11540) selects the running or jump
  table. `byte_91E8` (sonic3k.asm:11628-11629) is
  `2,6,7,8,7,6,2,3,4,5,4,3,1`. Cycle phases 1–5 select H mappings 6–8;
  phases 0 and 6–11 do not. Turning retains this table. Standing selector 1 and
  jump `byte_91F6` (sonic3k.asm:11630-11631), `9,11,10,11` repeated, have no H/V
  pieces. `word_ABE86` (docs/skdisasm/General/Sprites/Sonic/Map - SStage Sonic.asm:47-50)
  and the following two mappings reuse source art 3–5 through H pieces.
- S2 `SSPlayer_SetAnimation` (s2.asm:69709-69740) uses eight angle sectors:
  diagonal H, upright, diagonal, horizontal, diagonal V, upright V, diagonal HV,
  horizontal H. Jumping clears both flags. `SSPlayer_Animate`
  (s2.asm:69746-69790) also toggles upright H every four animation advances.
  Thus frames 0–11 need H/V/HV variants; frames 12–15 need H: 40 per actor.

The existing S3K creator bank already contained independent selector images
6–8. Its lighting jump was baked into those images, rather than an extra runtime
hardware flip. The new optional bank makes final orientation explicit and supplies
fresh correctly lit drawings. S2 genuinely used native H/V flags at draw time;
explicit variants now suppress only their matching render flips. Missing
variants retain the previous rules. No frame order/rate adjustment is justified:
all observed native/mod per-tick selectors, positions, native flags and stage
motion/fade state match, and stock PNG hashes are unchanged.

Fresh built-in GPT Image attempts used the real-palette native pose enlarged 8×,
model sheets and approved art. The generation manifest retains all successful
alternatives, exact prompts, selected hashes, native inventory, style/landmark
measurements and rejected contact/front-view attempts. No decoded native art,
raw generation, edit chain or repaint is packaged. Rebuild selected originals
with `tools/modding/playable_art/oriented_stage.py`; its check verifies indexed
pixels, palette and alpha. S3K uses at most nine opaque colours; S2 at most fifteen.
Native head/torso guides and contact phases are checked independently from area.

External evidence:
`$HOME/scratch/agent-tmp/mt-bluesphere-flip-20261110/review.html` contains all
111 unlabelled gates (62 direct capture crops; 49 rendered with the exact sampled
capture palette), lossless RGB 60-fps/3× lockstep movies, two complete per-tick
cycles, centroid plots, palette samples and full diff summaries. S3K main native
palette lines are Sonic 0 / Tails 1; S2 Sonic 1 / Tails 2. Creator indexed line 0
uses its isolated physical row and the same fade/upload owner. All 48 sampled
colours match the locked palette exactly. The earlier baseline Tails probe
reported logical palette 0 even though native solo Tails draws line 1; the probe
now records the true line. The real baseline pixels remain the authority.

Size sqrt(mean original opaque area / mean native reference area):
MICA S3K 1.002 (3), MICA S2 1.005 (56), TALON S3K 0.991 (8), TALON S2 1.036 (44).
There are 86 new explicit-orientation obligations, separate from native-ID
coverage; all move ordinary-selector fallback → authored. Twenty-five existing
base selectors are refreshed, with no additional native IDs or claimed route
coverage.

Focused checks: 83 engine/pixel/required-S3K tests and 65 integration/audit/API-pin
tests pass with zero skips. Python discovery: 57 pass. Package validation: zero
findings. Changed renderer citations: seven valid. Broader smoke is recorded in
the task handoff. These are positioned presentation encounters, not complete
routes; successful completion mechanics are unchanged and rely on the existing
native/fade regressions, rather than a newly completed special-stage route.

The strict release descriptor accepts data only, so a proposed comment was
rejected by its parser tests and removed. Candidate 0.7.0 remains unpublished;
its descriptor is byte-identical to 7a5d73912b. The commit hook requires ordinary
candidate signature regeneration to leave the release descriptor untouched, so
the attempted explicit empty-alias field was also removed. Candidate descriptions
belong in ModApiVersion and the guide, alongside the mutable signature pin.
A static Drawing[] table was rejected by the creator static-state validator;
the example now constructs the immutable drawing records inside its load call.

The broad change-based smoke run completed 4,517 candidate classes / 38,073
tests with VERDICT FAILED: 33 failures, seven errors, 49 allowed skips and no
unexplained or required skips. Three parser errors came from the removed
descriptor comment; their focused recheck is recorded in the handoff. All
remaining 37 failure identities are compared with base 7a5d73912b in external
failure-comparison.json. OOZ requires the identical 50-class batch to reproduce;
three MHZ expected Set renderings differ only in iteration order. No allowances,
skip rules, gameplay logic or native baselines were changed to hide failures.

Final handoff: all 37 remaining smoke failure identities reproduce on
7a5d73912b, including the four guards. After correcting the descriptor, all
32 policy/signature tests pass with zero skips. Final packaging has zero
findings and every jar entry is byte-identical to the captured mod. Eight
movie decode checks match every RGB source pixel exactly. The focused engine
and integration runs remain 83 + 65 passing tests, zero skips; the independent
Python discovery is 57 passing tests. The broad smoke remains FAILED on
inherited cases, rather than being described as a full-suite pass.

Smoke execution 1863.93s; preparation 33.13s; queue wait 889s. No permission/user wait. Art-generation time was not separately measured.
