# Level test coverage backlog

Infinite Sonic prototype (2026-10-01): the [mod GHZ1 matrix](../architecture/validation/levels/infinite-sonic-ghz1.md) records 20 passing focused ROM-backed checks for ring collection, bidirectional gap jumps, terrain-aware encounters, damage, attacks, traversal, backtracking, reload and rewind/replay, confirmed through queued Maven on 2026-10-02. Five supported widths are covered for solo Sonic. Version 0.8.0 extends the course to all 18 GHZ–SBZ acts, each with a focused build/start/dry-course traversal check (61 tests passing). Version 0.10.0 adds per-zone badnik line-ups and a session lives/CONTINUE death menu; 0.11.0 derives line-ups from each act's stock placement, earns lives only from rings and makes CONTINUE revive in place (74 tests passing). Version 0.12.0 adds platform stretches of stock Obj18/Obj52 stepping stones, crossed by real physics in each act that places them at 4:3 and 16:9 (129 run, 14 skipped for the seven acts without usable stones). Version 0.13.0 adds shield-monitor and 20-ring hit absorption and a slower scroll (134 run, 14 skipped). GPU presentation, donor and automatic respawn coverage remain open; this does not certify stock GHZ.

LBZ1 trace-campaign follow-up (2026-10-03): the [focused act matrix](../architecture/validation/levels/s3k-lbz-act1.md) covers cup contact and control ownership, rolling-drum deletion, the miniboss fatal hit, carried-results retirement and the retained title reset, and the Act 1→2 camera hold, each with restore/replay. The Sonic+Tails trace now matches through row 23532 but is still red. Full-route, viewport, donor and roster obligations remain open.

LRZ2 flame-shield follow-up (2026-09-30): the [Act2 matrix](../architecture/validation/levels/s3k-lrz-act2.md#flame-jet-fire-shield-immunity-2026-09-30) records real collision/shield regressions for all four shields and 167 focused tests without skips. Both jet variants now publish the ROM fire-immunity flag. Inherited route/rewind, donor, viewport and roster gaps remain.

LRZ2 background seam follow-up (2026-09-30): the [Act2 matrix](../architecture/validation/levels/s3k-lrz-act2.md#background-window-seam-2026-09-30)
records the direct-load source-row regression at all five widths and after rewind.
The initial physical tilemap must yield to streamed layout rows; broader route,
donor/team and native whole-scene obligations remain separate.


LRZ1 arm-explosion visibility follow-up (2026-09-29): the
[act matrix](../architecture/validation/levels/s3k-lrz-act1.md#arm-explosion-renderer-follow-up-2026-09-29)
adds a fresh-level production-init regression for renderer readiness and every
ROM explosion pixel. Previous burst-count/replay checks did not establish
visibility; native presentation remains blocked by the documented GL context.

LRZ horizontal-button orientation follow-up (2026-09-29): the
[Act 1](../architecture/validation/levels/s3k-lrz-act1.md#horizontal-button-orientation-2026-09-29)
and [Act 2](../architecture/validation/levels/s3k-lrz-act2.md#horizontal-button-orientation-2026-09-29)
matrices record placement-flip coverage through press/release and recreation.
Inherited route and whole-scene presentation gaps remain open.

Focused validation on develop, base `5a855456312` (Java 21; absolute S3&K ROM
path supplied, CRC32 `63522553`):

```sh
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestLrzButtonHorizontalRendering,TestLrzDoorsButtonsAndTriggers,TestLrzDoorButtonRewindSpots,TestS3kLrzButtonHorizontalLandingHeadless,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestPatternSpriteRendererCorruptionGuard,TestObjectPriorityBucketGuard' "-Ds3k.rom.path=$PWD/Sonic 3 & Knuckles (W) [!].gen" test
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestLrzButtonHorizontalRendering test
```

The first run passed 87 checks and failed the eight new cases because their
fixture omitted post-construction service injection. After correcting that
fixture, the second run passed all eight cases. Neither run skipped tests.
The production fix was unchanged between runs. This is focused validation,
not a full-suite pass. The change-based plan selected 2,947 classes due to an
unrelated untracked S2 movie; proportionate validation covers this object's
placement-only draw arguments, interaction states and recreation. No gameplay,
shared renderer, art decoder or timing behavior changed. No visual capture run.

LRZ Fireworm follow-up (2026-09-29): the
[Act 1](../architecture/validation/levels/s3k-lrz-act1.md#fireworm-defeat-and-palette-audit-2026-09-29)
and [Act 2](../architecture/validation/levels/s3k-lrz-act2.md#fireworm-defeat-and-palette-audit-2026-09-29)
matrices record defeat scatter/deletion, orphan rewind and loaded palette checks.
Full route-product and native whole-scene presentation gaps remain inherited.

LRZ1 sinking-rock art follow-up (2026-09-29): the
[Act 1 matrix](../architecture/validation/levels/s3k-lrz-act1.md#sinking-rock-art-binding-correction-2026-09-29)
records the ROM `$0D3` tile-base correction, a loaded-sheet regression for
both acts, and 154 focused passes without skips. Gameplay capture is blocked
by this environment's GLSL support; inherited route/configuration and native
whole-scene coverage gaps remain open.

## Current S&K campaign position — 2026-09-28

This summary reconciles the dated milestones below against integrated `ee8e470e4`.
Earlier entries retain their historical scope; their old “pending” labels are not
current blockers where a later entry records completion. No zone is certified by
route completion alone. Viewport, supported donor/roster, lifecycle, rewind and
native presentation obligations remain in the linked matrices.

| Zone | Observed route coverage | Current work / remaining acceptance |
| --- | --- | --- |
| [MHZ](../architecture/validation/levels/s3k-mhz-act2.md) | Native Sonic, Tails and native pair complete both acts; 800px Sonic completes both acts and reaches playable FBZ, with 25 whole-world replay windows. | Native camera/pillar corrections and persistent vine-rider rewind are integrated (`a6df1f1d0`, `936292c33`). Broader viewport/donor and native presentation obligations remain. MHZ Knuckles expansion remains excluded by the accepted scope. |
| [FBZ](../architecture/validation/levels/s3k-fbz-act1.md) | Act 1 native Sonic, Tails, Knuckles and 320/400 native pair reach released Act 2; frozen Tails/Knuckles inputs pass 44/50 whole-world replay windows. Act 2 retains its documented completion matrix. | Tails debris corrections and Knuckles input recovery are integrated (`10b066947`, `192a9956d`). Wider/donor and native presentation obligations remain. |
| [SOZ](../architecture/validation/levels/s3k-soz-act2.md) | Native solo Sonic, Tails and Knuckles complete both acts; Knuckles also completes Act 2 at 800px with whole-world replay. | The Knuckles reset and repaired routes are integrated (`49586cd86`, `38f799053`). Other viewport/donor products and native presentation acceptance remain. |
| [LRZ](../architecture/validation/levels/s3k-lrz-act2.md) | Native Sonic/team, solo Tails and Knuckles chains reach the appropriate next stage; repaired Knuckles route reaches HPZ. | The transport/glide repair and revised route are integrated in `49586cd86`; the [cold800 first-crusher follow-up](../architecture/validation/levels/s3k-lrz-act1.md#2026-09-28--lrz-cold-widescreen-crusher-lock-and-explosion-rewind) is integrated in `25609a0ab`, with40 two-cycle full-registry checkpoints and the native full-zone clear rechecked on destination. Broader widths/donors and native presentation remain. |
| [SSZ](../architecture/validation/levels/s3k-ssz-act1.md) | Native Sonic, Tails and pair complete Act 1; Tails completes 800px. Opening Death Egg camera tracking has focused signed-delta and five-width graph-recreation/replay coverage. Knuckles Act 2 reaches the accepted pre-ending stop at 320/800. | Selected original-game fight presentation is corroborated; this is not whole-scene parity. Remaining donor/roster, lifecycle and presentation products stay open; excluded ending ownership is not a campaign blocker. |
| [DEZ](../architecture/validation/levels/s3k-dez-act2.md) | Native Sonic, Tails and pair complete both acts; Sonic now clears Act 1 at 800px into released Act 2 with 58 whole-world replay windows. Incoming final-fight/escape checks and corrected gravity/floor behavior are documented. | The captured turbine contact-owner fix and 800px Act 1 route are integrated (`f733fddb8`). Continuing cold widescreen Act 2 chains and remaining native-presentation/breadth obligations remain distinct from positioned fight checks. |
| [DDZ](../architecture/validation/levels/s3k-ddz.md) | Fresh Super and positioned incoming Hyper complete at 320/800; native cold emerald-team DEZ1→DDZ reaches the accepted ending request. | Native scene comparison and remaining supported route products remain; the excluded ending implementation is outside this stop line. |

The shared seamless-presentation fix (`8258c3aed`) retains sprite publication
across in-place reloads and MHZ's background scroll origin. Combined ordinary
validation on `760a22892` completed 25,122 tests: one stale FBZ debris-lifetime
assertion, zero errors, 29 inherited skips. That oracle is corrected in
`936292c33` and its complete class plus defeat-child checks pass 24 tests.
All 672 structural guards passed. This is not a claim of a green full-suite run;
post-integration focused verification of `f411b75db` passes 122 ordinary cases,
six FBZ route cases and five separate structural guards, all with zero skips.

DEZ800 Act1 is integrated with21,100 frozen ordinary inputs, both eight-hit
phases, real load at19,889 and released Act2 control. Destination checks pass35
cases with zero skips; the [Act1 matrix](../architecture/validation/levels/s3k-dez-act1.md#2026-09-28--sonic-cold-800px-act-1-completion-and-turbine-contact-rewind)
records the58 whole-world replay windows and fresh videos. The continuing
Act2 input-authoring frontier reaches the lower gravity section but is not yet
a completed or independently certified Act2 product.

## Historical milestone ledger

[SOZ Knuckles Act1](../architecture/validation/levels/s3k-soz-act1.md#solo-knuckles-cold-completion-2026-09-27)
now has a24,059-input cold route through a real bonus visit/return, golem,
results and playable Act2, with29 full-world replay windows and both bonus
load history resets. Fresh native320 capture passes; wider/native-parity
acceptance and Knuckles Act2 remain open.
The [2026-10-03 controller refresh](../architecture/validation/levels/s3k-soz-act1.md#bonus-return-controller-refresh-2026-10-03)
re-authors the movie for the corrected bonus setup pass; all five solo cold cases pass.

[SOZ solo Sonic Act1](../architecture/validation/levels/s3k-soz-act1.md#solo-sonic-cold-completion-2026-09-27)
now has a31,671-input cold route through the golem, results and playable Act2,
with a fresh native320 movie and a dedicated full-world replay acceptance row.
Solo Sonic and Tails now complete both acts; Knuckles Act1 is covered below.
Knuckles Act2 and native-presentation breadth remain open.

SOZ [Act1](../architecture/validation/levels/s3k-soz-act1.md#production-display-lifecycle-refresh-2026-09-27)
and [Act2](../architecture/validation/levels/s3k-soz-act2.md#production-display-lifecycle-refresh-2026-09-27)
now exercise the actual320/352/400/528/800 menu presets for all physical posts,
repeated supported-team reloads and connected Act2 mechanisms:509 focused
checks, zero skips, including480 real death/reloads. Earlier512/640 cases are
historical custom-width evidence; full cold-route and native-presentation gaps
remain open.

The [Knuckles transport-reset follow-up](../architecture/validation/levels/s3k-lrz-act2.md#knuckles-transport-glide-reset-and-route-repair-2026-09-27)
reauthors the affected late Act2 inputs after the ROM-backed glide reset. The
candidate49,526-input cold route reaches playable HPZ with89 restore/replay windows;
fresh independent capture has no deaths. Shared-change broad validation and
integration remain pending; prior completion details below are historical.

[Knuckles LRZ2 completion](../architecture/validation/levels/s3k-lrz-act2.md#knuckles-cold-act2-completion-and-direct-hpz-2026-09-25)
now reaches playable Hidden Palace from cold Act1 in52659 inputs, zero deaths,
with81 additional restore/replay windows (289 across the five Knuckles tests).
The route exposed and repairs a stale collision-probe flag affecting balance
after rewind. The three mandatory native320 LRZ cold chains are complete;
broader products, native presentation and campaign integration remain open.
Earlier milestone notes below retain their historical scope.

[Knuckles LRZ2 upper climb](../architecture/validation/levels/s3k-lrz-act2.md#knuckles-upper-climb-and-door-release-2026-09-25)
now reaches the eastern door approach in40046 cold inputs, zero deaths, with53
additional restore/replay spots (101 Act2,208 across the Knuckles route tests).
Direct HPZ completion and the remaining campaign acceptance remain open.

[Knuckles LRZ2 middle route](../architecture/validation/levels/s3k-lrz-act2.md#knuckles-cold-lower-route-and-upper-tube-arrival-2026-09-25)
now reaches the upper tube arrival from cold Act1 in33763 inputs, zero deaths,
with 48 Act2 restore/replay spots and a matching fresh capture. Act2 completion,
direct HPZ, broader products and campaign integration remain open.

[Knuckles LRZ1 cold completion](../architecture/validation/levels/s3k-lrz-act1.md#knuckles-cold-act1-clear-and-act2-handoff-2026-09-25) now defeats the miniboss and reaches playable Act2 in25763 inputs,zero deaths, with37 fight/transition replay spots in addition to70 traversal spots. Knuckles Act2/directHPZ and broader products remain open.

The [Knuckles LRZ1 cold arrival](../architecture/validation/levels/s3k-lrz-act1.md#knuckles-cold-miniboss-arrival-2026-09-25) now has20410 preserved inputs,70 full-registry restore/replay spots, real cloud escape and a fresh matching capture. The boss fight and remaining Knuckles chain are still open.

Knuckles LRZ1: the [cloud escape follow-up](../architecture/validation/levels/s3k-lrz-act1.md#knuckles-cloud-escape-follow-up-2026-09-25) corrects glide-landing animation ownership, with production movement/rewind checks in both gravity directions and a fresh cold encounter capture. Full Knuckles completion remains open.

LRZ1 cloud-contact follow-up: the [Act 1 matrix](../architecture/validation/levels/s3k-lrz-act1.md#cloud-special-contact-and-revised-late-ascent-2026-09-24) records the real-controller regression, deferred contact rewind and reauthored cold ascent. The [cold miniboss arrival](../architecture/validation/levels/s3k-lrz-act1.md#cold-miniboss-arrival-and-final-lower-route-door-2026-09-24) extends this to thirteen routes and159 replay spots, with the final door and native priority marker asserted. The [hand-shot response check](../architecture/validation/levels/s3k-lrz-act1.md#miniboss-hand-shot-shield-response-2026-09-24) covers shield deflection and harmless-flight recreation. The [ordinary cold clear](../architecture/validation/levels/s3k-lrz-act1.md#ordinary-cold-miniboss-clear-and-act2-handoff-2026-09-24) now extends this to fourteen routes and 185 replay spots, including defeat/results/playable Act2 with zero deaths. It also fixes retired crusher-piece lifetime and pending arena-gate restoration. The [Act2 title camera release](../architecture/validation/levels/s3k-lrz-act2.md#act-title-vertical-camera-release-2026-09-24) now covers the missing vertical boundary workers and keeps the cold climb visible. The [drill replacement check](../architecture/validation/levels/s3k-lrz-act1.md#native-drill-replacement-and-detached-debris-2026-09-24) additionally verifies slot retirement and independent debris priority/recreation. The [cold Act2 climb and pipe passage](../architecture/validation/levels/s3k-lrz-act2.md#cold-climb-door-eight-and-pipe-passage-2026-09-24) now has36204 preserved inputs and31 Act2 replay spots. Full Act2 completion and other route products/breadth remain open.

FBZ2 laser-room graphics: the [Act 2 matrix](../architecture/validation/levels/s3k-fbz-act2.md)
tracks native child sprite priority and an independent rendered-tile comparison
at the room-exit plane swap. Execution and inherited visual gaps are recorded
in the [graphics audit](../architecture/audits/2026-09-14-fbz2-laser-room-graphics.md).

The [standard](../guide/contributing/level-test-standard.md) defines required coverage;
the [delivery plan](../architecture/plans/2026-09-13-level-test-standardisation.md)
defines the audit, pilots, reporting and migration work. This ledger starts the
backlog; it is not a completed coverage audit.

The [FBZ Act 1](../architecture/validation/levels/s3k-fbz-act1.md) and
[Act 2](../architecture/validation/levels/s3k-fbz-act2.md) matrices track the
2026-09-14 remaining-items delivery: the native Sonic + Tails cold Act 1 route
now reaches the real six-impact miniboss, sign/results and Act 2 title teardown
with player control released at widths 320 and 400 (27,051 and 25,765 ordinary
frames; final pair at `c7bb1cd94`, two passes and no skips). Thirty
independent entry/reload/reset rows cover both acts × widths 320/352/400/528/800 ×
native/S1/S2 donors. The actual Sandopolis load resets the timeline and passes two
capture/restore/forward cycles. ROM-backed spike tile banks, retained-title
initialization, scarce-slot workers and queued/presented animation-art rewind
also have bounded executed checks. The five animation channels have independently
reviewed opaque source-pixel evidence over six frames each. Full native checkpoint
geometry and strict replay parity stay open. All 13 ordinary Act2 completion rows and five independent plane-approach rows pass (`af0f9facc`); eleven bounded native world afterstates are accepted, with B2/B4 SAT presentation gaps recorded separately;
these bounded results do not certify either act. See each matrix and the dated
completion record for commands, commit limits and unexecuted obligations.

## Work packages

| ID | Work | Status | Completion evidence |
| --- | --- | --- | --- |
| LTS-01 | Publish standard, matrix template and implementation entrypoint links | Documented | Standard and mirrored guidance; [documentation validation](../architecture/validation/2026-09-13-level-test-standard.md) |
| LTS-02 | Resolve registry slots, aliases, routes and non-registry gameplay paths; map existing assertions | In progress | [Initial 26-zone source inventory and estimates](../architecture/audits/2026-09-13-level-test-coverage-inventory.md); per-act matrices and remaining dispositions pending |
| LTS-03 | FBZ/AIZ/HCZ reference matrices, plus S1 GHZ3 and S2 CPZ2 pilots | In progress | [Partial AIZ1 matrix](../architecture/validation/levels/s3k-aiz1-sonic.md), [HCZ1 partial matrix](../architecture/validation/levels/s3k-hcz1-sonic.md); Breadth, rewind boundaries, defect sensitivity and measured cost |
| LTS-04 | Minimal shared case/rewind helpers and coverage report | Pending | Required-to-executed identity joins and tooling acceptance scenarios. Route steering/diagnostic primitives landed separately in `com.openggf.tests.route` (commit 610464952) and cover only the failure-diagnostics part |
| LTS-05 | Enforce explicit-lane prerequisites and missing/skipped case inventory | Pending | Negative prerequisite/selection tests and wired commands |
| LTS-06 | Remaining implemented S3K acts/routes | Pending | Conformant matrices; known gaps stay open |
| LTS-07 | Remaining implemented S1/S2 acts/routes | Pending | Conformant matrices; all supported width/donor axes executed |
| LTS-08 | Auxiliary arenas, scenes, competition/bonus/special-stage dispositions | Pending | Applicable contracts and timeline policies, including paths outside registries |
| LTS-09 | Backlog closure and new-act/dimension drift detection | Pending | No unmapped implemented act or missing required evidence |

## Initial registry inventory

Seeded by source inspection at `5a3cb848a8d45b7bf236fc5ee8ca30cc8af1a33e`.
The [initial source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md)
now covers 26 zones / 55 gameplay acts for estimation, with explicit delivered versus
conditional S3K scope. Their rows link to that zone-level inventory; **conformance assessments remain pending**; the
[AIZ1 Sonic matrix](../architecture/validation/levels/s3k-aiz1-sonic.md) now records
the route-controller continuation, the level-entry camera correction and the
trace-campaign object regressions, with their inherited gaps; the
[HCZ1 partial matrix](../architecture/validation/levels/s3k-hcz1-sonic.md) records water-route
and miniboss composition, ten independent restore/replay spots, the production
reload boundary, 30 viewport/donor entry/reset cases, all 15 full width/donor
routes, and five rewind checks crossing horizontal arena admission. Other characters/teams,
checkpoint/death-restart and presentation obligations remain open. The other 34 slots
still need scope/disposition audit. No new execution result or pass is awarded.

Sources: [S1 registry](../../src/main/java/com/openggf/game/sonic1/Sonic1ZoneRegistry.java),
[S2 registry](../../src/main/java/com/openggf/game/sonic2/Sonic2ZoneRegistry.java),
[S3K registry](../../src/main/java/com/openggf/game/sonic3k/Sonic3kZoneRegistry.java).
The keys are game + registry descriptor, not a claim that a descriptor is a distinct
playable act. S3K exposes all 24 × 2 ROM slots, including aliases and non-act scenes.
Resolve canonical route and raw slot identity in LTS-02. Special stages and alternate
mode entrypoints outside these registries must be added rather than silently omitted.

Replace each pending disposition with a linked per-act matrix or a source-backed
alias/nonimplemented/non-gameplay disposition. Keep the registry key so future audits
can find missing/new slots. Track separate obligation and execution status inside
matrices; never collapse skipped or unrun into pass.

| Game | Registry descriptor | Disposition / matrix |
| --- | --- | --- |
| S1 | `S1_GREEN_HILL_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_GREEN_HILL_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_GREEN_HILL_3` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_MARBLE_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_MARBLE_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_MARBLE_3` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_SPRING_YARD_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_SPRING_YARD_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_SPRING_YARD_3` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_LABYRINTH_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_LABYRINTH_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_LABYRINTH_3` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_STAR_LIGHT_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_STAR_LIGHT_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_STAR_LIGHT_3` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_SCRAP_BRAIN_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_SCRAP_BRAIN_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_SCRAP_BRAIN_3` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_FINAL_ZONE` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-1--deliveredplayable-cohort); matrix pending |
| S1 | `S1_ENDING_FLOWERS` | Audit pending |
| S1 | `S1_ENDING_NO_EMERALDS` | Audit pending |
| S2 | `EMERALD_HILL_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `EMERALD_HILL_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `CHEMICAL_PLANT_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `CHEMICAL_PLANT_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `AQUATIC_RUIN_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `AQUATIC_RUIN_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `CASINO_NIGHT_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `CASINO_NIGHT_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `HILL_TOP_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `HILL_TOP_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `MYSTIC_CAVE_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `MYSTIC_CAVE_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `OIL_OCEAN_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `OIL_OCEAN_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `METROPOLIS_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `METROPOLIS_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `METROPOLIS_3` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `SKY_CHASE` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `WING_FORTRESS` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S2 | `DEATH_EGG` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-2--deliveredplayable-cohort); matrix pending |
| S3K | `S3K_ANGEL_ISLAND_1` | [Partial Sonic-route matrix](../architecture/validation/levels/s3k-aiz1-sonic.md); other main routes and full conformance pending |
| S3K | `S3K_ANGEL_ISLAND_2` | [Act 2 matrix](../architecture/validation/levels/s3k-mhz-act2.md) — historical scope reconciled; current route/lifecycle/breadth certification pending |
| S3K | `S3K_HYDROCITY_1` | [Local boss obligations and inherited gaps](../architecture/audits/2026-09-13-hcz1-miniboss-parity.md#hcz1-affected-route-matrix); [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_HYDROCITY_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_MARBLE_GARDEN_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_MARBLE_GARDEN_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_CARNIVAL_NIGHT_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_CARNIVAL_NIGHT_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_FLYING_BATTERY_1` | [Act 1 coverage matrix](../architecture/validation/levels/s3k-fbz-act1.md) — partial; execution/route/visual gaps remain |
| S3K | `S3K_FLYING_BATTERY_2` | [Act 2 coverage matrix](../architecture/validation/levels/s3k-fbz-act2.md) — native Sonic/Tails/Knuckles and donor completion routes pass; broader rewind/visual certification remains partial |
| S3K | `S3K_ICECAP_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_ICECAP_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_LAUNCH_BASE_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_LAUNCH_BASE_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_MUSHROOM_HILL_1` | [Act 1 matrix](../architecture/validation/levels/s3k-mhz-act1.md) — fresh native 320 Sonic solo controller route completes the miniboss and seamless Act 2 handoff, with six capture/restore/forward-replay spots; broader roster, viewport and lifecycle certification remains open |
| S3K | `S3K_MUSHROOM_HILL_2` | [Act 2 matrix](../architecture/validation/levels/s3k-mhz-act2.md) — real incoming handoff plus fresh Sonic solo leaf-blower entry/replay at all five presets; complete Act 2 route and broader certification remain open |
| S3K | `S3K_SANDOPOLIS_1` | [Act 1 matrix](../architecture/validation/levels/s3k-soz-act1.md) — v2 bring-up; concrete objects, bosses, coupled events and recreation tracked; full-route/native and transient redraw gaps remain |
| S3K | `S3K_SANDOPOLIS_2` | [Act 2 matrix](../architecture/validation/levels/s3k-soz-act2.md) — v2 bring-up; concrete objects, bosses, coupled events and recreation tracked; full-route/native and transient redraw gaps remain |
| S3K | `S3K_LAVA_REEF_1` | [Act 1 matrix](../architecture/validation/levels/s3k-lrz-act1.md) — all609 placements resolve,331 live rings. Traversal families, miniboss, results and seamless Act2 handoff are implemented. Positioned native/wide encounter routes now cross the real priority marker and complete the handoff; the post-results ROM palette ramp has full-registry replay and native color/timer corroboration. Cold full-act completion, roster/donor/lifecycle breadth and whole-scene native matching remain open. Earlier trace frontiers are historical, not current certification. Cold native320 ordinary Sonic+Tails now has a preserved4501-frame corkscrew/lower-platform route, with a native release assertion and21 passing full-registry replay spots. A second4901-frame route checks production shield deflection and retains the shield, with8 more replay spots. A third5001-frame route verifies the dash-elevator jump-off and adds4 spots. A fourth5501-frame route checks correct charge-byte selection and adds5 spots. A fifth5701-frame route verifies rock-fragment shield deflection and adds5 spots. A sixth6001-frame route checks fire-shield immunity through falling lava and adds5 spots, then3 more across button/door opening (51 total). Cold checks now verify old LRZ solids release their slots. A seventh6201-frame crusher-hit route adds5 replay spots (56 total) and checks native rebound recovery; full-act completion remains open |
| S3K | `S3K_LAVA_REEF_2` | [Act 2 matrix](../architecture/validation/levels/s3k-lrz-act2.md) - v2 bring-up: all 455 placements resolve to concrete implementations (281 placeholders at the slice 0 baseline), 281 live rings; scroll, animated tiles, rock sprites and the act 2 `$6E`/`$19`/`$1C`/`$16`/`$17`/`$20`/`$99`/`$9A`/`$9B` skins land, with the act 2 art keys now asserted ready at 320 and 400 by `TestS3kLrzCompatibilityMatrix`, with a positioned `$2D` moving-platform ride/recreation/replay at 320/800 after the oscillator-byte correction; the native320 Sonic+Tails cold Act1 chain now completes Act2 in43761 inputs with zero deaths, reaching the boss act with six rings. The boulder cutscene and both exits are implemented; the background Death Egg sprite now has its ROM art queue, native screen positioning, documented wide lead-in and five-width recreation/replay checks. The post-miniboss palette ramp is now implemented with native timing/color corroboration and320/800handoff replay; native comparison, other route products and remaining breadth/lifecycle obligations remain open |
| S3K | `S3K_SKY_SANCTUARY_1` | [Act 1 matrix](../architecture/validation/levels/s3k-ssz-act1.md) — v2 bring-up in progress; placement census, arrival, act-1 bounds, the opening cutscene, the background (both modes, clouds and solid cloud platforms), the animated tiles, every act-1 traversal family and the EggRobo, the death/checkpoint lifecycle and both act-1 boss recreations are covered: Green Hill (hit window, three-colour flash, ball hitbox and defeat scatter) and Metropolis (lock and spawn at both widths, the ship's two-level dispatch, the seven-orb ring and its front/back sort, the launch a hit triggers, the laser pass and the defeat that raises the `$79:$F6` pad), all driven through the real touch pass. Mecha Sonic is covered from the `$79` pad's allocation through the entry, the attack loop with
`byte_7D2FC`'s per-frame collision byte, the defeat and `loc_7D056`'s handover, by
`TestS3kSszMechaSpawnHeadless` and dated against the run's third SSZ segment `hpz_3`;
his palette rotation and spark child now pass source-row and launch-recreation checks.
The `loc_7C9BA` collision child now has ROM-table, real-hurt and rewind checks; native hit-window phase remains open. The bare allocation was verified to write no slot. The production results-to-DEZ1 launch now passes component handover/rewind checks at
320/400/800, native Sonic + Tails, Tails alone and S1 donor, with a controller Hyper
checkpoint recording through the destination load; exhausted slots, transition-history
isolation and native parity remain open. Both recreations now emit defeat explosions and have defeat-graph recreation/forward-replay checks; a refreshed Metropolis checkpoint recording shows the bursts. Replacement-slot stop-byte fidelity remains open. Both recreations' entry and defeat intervals are compared against the `hpz` segment's aux rows; their orbits, flashes and laser cadence are not, and the Metropolis fight is now filmed end to end (clips 20-22) with `GameplayCaptureTool --rings` |
| S3K | `S3K_SKY_SANCTUARY_2` | [Act 2 matrix](../architecture/validation/levels/s3k-ssz-act2.md) — v2 bring-up in progress; placement census pinned to the ROM; native/wide arrival/crane release and replay pass; encounter deformation, transformation/first Super cycle, defeat controls/effects and floor patch have component evidence; positive-signal island/redraw and ROM water-palette checks pass at320/800, including restore/replay. Full cold fight, incoming HPZ, remaining attack-family validation, native visuals and later presentation remain open |
| S3K | `S3K_DEATH_EGG_1` | [Act 1 matrix](../architecture/validation/levels/s3k-dez-act1.md) — cold Sonic+Tails320 now clears all six turbine panels, both miniboss phases and the actual Act2 load in14,231frames, with40 shorter-route and22 late-route replay spots plus load-boundary isolation. v2 bring-up started. Level load, music, intro run, shared objects, the `$5A` gravity tube, the `$5F` turbine corridor, the `$61` gravity puzzle inside it, the `$A4` Spikebonkers, the `$55` energy bridges and the `$A5` Chainspikes and `$60` bumper walls and `$6D` shock blocks and `$52` lightning and `$50` conveyor belts and `$4D` torpedo launchers and `$4F` staircases and `$5E` hover machines and `$4C` hanging carriers and `$56` curved energy bridge and `$4B` tilting bridges and `$53` conveyor pads and `$4E` lift pads and `$57` light tunnels (365 of 365 objects concrete). Slice 3 is complete and slice 4 is in progress. The corridor now keeps its controller alive, bounces carried players on the puzzle/walls and passes a positioned six-panel-to-exit route at 320 px, including a contact rewind/forward check. Cold act traversal and native comparison remain open. The registered miniboss has two-phase, contact, allocation-prefix and recreated-graph checks, with connected results/reload/transport/control-release regressions from three finishing positions and solo Hyper recordings at 320/800. Cold route, native comparison and remaining roster/donor breadth remain open |
| S3K | `S3K_DEATH_EGG_2` | [Act 2 matrix](../architecture/validation/levels/s3k-dez-act2.md) — v2 bring-up in progress. Reverse gravity is implemented for the player, shields, rings, solid objects, springs and the sidekick (112 of 116 ROM references covered, four not applicable; whole-route and presentation obligations remain in [s3k-known-bugs](s3k-known-bugs.md)), the five gravity families implemented so far are concrete (`$5B`, `$58`, `$59`, `$5A`, `$5C`), and slice 4 has added the `$A4` Spikebonkers, `$5D` retracting springs, `$55` energy bridges `$A5` Chainspikes and `$6D` shock blocks and `$52` lightning and `$50` conveyor belts and `$4D` torpedo launchers and `$4F` staircases and `$4C` hanging carriers and `$4A` floating platforms and `$4B` tilting bridges and `$53` conveyor pads and `$57` light tunnels — 494 of 494 objects, including the registered gravity boss. Act 2 has a **1256-frame route frontier** from the first frame of free play, exact in player position, camera and rings and ratcheted in `TestS3kDezColdRoutes` (390 → 472 → 527 → 616 → 1256 as those four landed); the first divergence is now a one-pixel `x` lag on a shared `$08` platform ride, not a Death Egg object. A cold `$B01` route is not comparable against the committed movie until the act 2 entrance sequence is implemented. Both fixed backgrounds now have 320/352/400/528/800 composition checks and inspected captures; native centre pixels remain unchanged. The seamless act change and entrance transport now have connected regressions and 320/800 solo Hyper recordings; the cold trace has not been remeasured. The gravity boss now has connected entry/fight/defeat/exit checks, allocation-prefix and rewind coverage, plus 320/800 solo Hyper recordings. Ordinary cold DEZ1→DEZ2 Sonic+Tails now reaches the lower staircase and second gravity tube in17921frames, zero deaths, with17 Act2 full-registry rewind/replay spots; a second preserved18931-frame route adds the energy-bridge ascent, gravity switch and middle corridor, with14 additional spots. A third19810-frame cold route clears both transporters and the upper spring, with14 further replay spots; it exposed and repairs Chainspike parent identity/retirement on rewind. A fourth23190-frame route completes the upper gravity route, carrier and countdown launch, adding28 replay spots (73 across the four Act2 routes). A fifth23893-frame route clears the inverted spring shaft and corridor step, adding14 replay spots (87 total). A sixth25263-frame route crosses the lower gravity switch, staircase and tilting bridge without a shield, adding26 replay spots (113 total). A seventh28413-frame route covers the upper transport chains and east hub descent with32 further replay spots (145 total), exposing and repairing a completed trail reference retained by its controller. Knuckles inverted glide/slide ceiling contacts are implemented with a focused real-terrain replay check; reversed climbing branches and real-terrain probes are covered, while complete Knuckles traversal remains open. Cold native320 Sonic+Tails now completes Act2 and loads final DEZ in40316frames from DEZ1, zero deaths or transformation, with59 further replay spots (204 across eight Act2 routes) and full-load history isolation. Width/roster/lifecycle breadth remains open. Native boss parity and remaining route breadth are open |
| S3K | `S3K_DOOMSDAY` | [Zone matrix](../architecture/validation/levels/s3k-ddz.md) — in progress; seeded Sonic route matches native; fresh Super routes complete320/800 with fight/wrap/exit replay; death/restart and live timeline reset covered at320/800; Hyper-star display-list/art-queue phase, retained HUD redraw and recording exit freeze corrected; full cold native320 Sonic+Tails now completes DEZ1→DEZ2→final→DDZ with boot-only emeralds in64648 inputs; seeded ending-load save request and history isolation pass at320/800; remaining native presentation and incoming width/donor breadth open |
| S3K | `S3K_DOOMSDAY_2` | Audit pending |
| S3K | `S3K_AIZ_INTRO` | Audit pending |
| S3K | `S3K_ENDING_SCENE` | Audit pending |
| S3K | `S3K_AZURE_LAKE` | Audit pending |
| S3K | `S3K_AZURE_LAKE_2` | Audit pending |
| S3K | `S3K_BALLOON_PARK` | Audit pending |
| S3K | `S3K_BALLOON_PARK_2` | Audit pending |
| S3K | `S3K_DESERT_PALACE` | Audit pending |
| S3K | `S3K_DESERT_PALACE_2` | Audit pending |
| S3K | `S3K_CHROME_GADGET` | Audit pending |
| S3K | `S3K_CHROME_GADGET_2` | Audit pending |
| S3K | `S3K_ENDLESS_MINE` | Audit pending |
| S3K | `S3K_ENDLESS_MINE_2` | Audit pending |
| S3K | `S3K_GUMBALL` | Audit pending |
| S3K | `S3K_GUMBALL_2` | Audit pending |
| S3K | `S3K_GLOWING_SPHERE` | Audit pending |
| S3K | `S3K_GLOWING_SPHERE_2` | Audit pending |
| S3K | `S3K_SLOT_MACHINE` | Audit pending; bonus-loop player priority and native glass overlap covered by `TestGameLoopBonusPlayerPriority` and `TestS3kSlotsGlassNative` ([scope and evidence](../architecture/validation/2026-09-14-slots-glass-layering.md)). Donor/team and rewind visual breadth remain open. |
| S3K | `S3K_SLOT_MACHINE_2` | Audit pending |
| S3K | `S3K_LRZ_BOSS` | [Boss-act matrix](../architecture/validation/levels/s3k-lrz-boss.md) —35 placements resolve,52 live rings; carry, checkpoint, flash, autoscroll, platforms/lava, boss, capsule/results and HPZ exit implemented. Ordinary native320 Sonic+Tails now completes the entire cold LRZ chain in53047 inputs, zero deaths, with a real fire-shield pickup and no encounter hurt. The earlier declared-shield standalone completion retains separate bonus-return evidence. Widescreen generator activation now preserves the native timing window; matched320/800 checkpoint movement agrees through2634 inputs. See the matrix for the separate centered-presentation checks. Native capsule pose/slots, timing/pixels and remaining route-product/lifecycle obligations remain open |
| S3K | `S3K_HIDDEN_PALACE` | [Act matrix](../architecture/validation/levels/s3k-hpz-act.md) — in progress; entry, events, animation and teleporter breadth covered; Knuckles fight and Sonic/Tails exit open |
| S3K | `S3K_DEZ_BOSS` | [Final boss matrix](../architecture/validation/levels/s3k-dez-final-boss.md) — final-floor contact corrected against native support:164 focused checks plus five repaired full-route cases pass; current solo/team endings60920/49448 inputs, cold emerald team through DDZ59722, positioned320/800 through DDZ27122/27224; prior lengths below are historical; v2 bring-up started; `$1700` now allocates its floor supports and final boss on the first production frame; forced entry and replay after rewind have focused coverage. Floor/collapse, scroll, laser-upload, retained-plane, hand/finger, vulnerable-core, mouth/beam, fireball, emerald, escape-scenery and ship-decoration and escape-ship and main-controller components now have focused checks and a distinct rewind runtime owner; production input destroys the fingers, defeats the core and reaches the escape-ship chase; retained-plane rendering and floor redraws are connected, with five-width entry/replay checks. Positioned incoming DEZ2→final→DDZ routes pass320/800; cold ordinary native320 Sonic+Tails now clears both main acts and all final phases through the real ending load in54692frames, zero deaths, with31 final-phase full-registry replay spots. Remaining lifecycle/roster/native breadth remains open |
| S3K | `S3K_SPECIAL_STAGE_ARENA` | [Sanctuary matrix](../architecture/validation/levels/s3k-hpz-sanctuary.md) — partial; results-return reveal missing |

Initial inventory: S1: 21 slots, S2: 20 slots, S3K: 48 slots; 89 total. These are inventory counts, not coverage percentages.

## KiS2 auxiliary-route continuation

[Presentation and Super Knuckles matrix](../architecture/plans/2026-09-14-kis2-presentation-super.md#coverage-matrix)
records the seven special-stage entry/rewind windows, title, ending/continue,
results and powered-form checks. It does not certify stock S2 act routes or
all supported viewport/donor/team combinations; those inherited obligations
remain in LTS-07/LTS-08.

[KiS2 trace-readiness follow-up](../architecture/plans/2026-09-14-kis2-trace-readiness.md)
adds targeted movement/input, checkpoint/reload and results lifecycle obligations.
It does not replace full special-stage routes or powered-form gameplay rewind
coverage; those still require independent route checks. The full chain recording
is now published. The [first frontier round](../architecture/research/trace/2026-09-14-kis2-chain-frontier.md)
exercises the initial EHZ1 route and first special-stage return, plus independent
chip PLC capture/restore/forward replay. It does not certify full-act gameplay
rewind or viewport/donor/team breadth. The wall-contact continuation now exercises
signed-width wall retention and glide grabs on both sides and both solidity paths,
KiS2/S3K wall-jump position preservation, and Coconuts init/idle capture, restore
and forward replay. The chain has crossed the sixth special-stage entry and return;
the touch continuation covers both attack directions, glide/slide admission,
non-attacking ability states, the active boss-hit glide exit, and its multi-sprite
and S3K exceptions. The chain now compares all EHZ2 and CPZ1 rows, with remaining
art/queue and CPZ1 movement differences recorded in that investigation.
These checks do not certify full-act or special-stage gameplay rewind.

FBZ integration verification (`f037a1218`, 2026-09-14): the full ordinary selection passes 20,281 tests with18 inspected skips and no failures/errors. Two structural-guard failures are unchanged from the baseline. This validates the implemented matrix obligations; the explicit strict replay prerequisite/SOZ entry and native SAT presentation gaps remain open. See the [completion record](../architecture/plans/2026-09-14-fbz-completion.md) for exact commands and limits.

FBZ hanging-handle follow-up (2026-09-14): both act matrices now track invisible
horizontal grab-region rendering and the positive vertical descent check. This
local fix does not certify the remaining native visual checkpoints.

FBZ Act 2 early `$0DC0` elevator: the 2026-09-14 S1 local phase sweep and
blocked/safe/crushed gameplay captures establish ordinary roll feasibility;
the Act 2 matrix records its width, entry-history and permanent-regression limits.

FBZ early elevator direction correction (2026-09-14): right-to-left S1
feasibility now has separate measured/captured evidence in the Act 2 matrix.
Its narrow successful script does not inherit the easier left-to-right timing
windows; extended phase coverage and whole-route guarantees remain open.

FBZ early reverse squeeze speed comparison: the Act 2 matrix now records
a matched car-phase/entry-position pair where about 9.4% more roll-entry speed
changes a crush into a safe crossing, reproduced by synchronized gameplay videos.

FBZ Act 1 miniboss presentation (2026-09-15): the [Act 1 matrix](../architecture/validation/levels/s3k-fbz-act1.md)
records the paired opened-boss setup and local plunger/face/priority corrections.
Full-act and complete-route visual certification remains open.

SOZ sand-rock continuation (2026-09-15): `TestSozSandRockProduction` adds short
positioned break/removal and twice-replayed registered rewind spots for both
acts, with Act 1 representative 320/640 widths, S1 donor and extra follower.
These are independent of the failed cold approach; see the
[Act 1 matrix](../architecture/validation/levels/s3k-soz-act1.md) and
[Act 2 matrix](../architecture/validation/levels/s3k-soz-act2.md). Full route,
all-character, checkpoint and native/pixel certification obligations remain open.


SOZ pushable-rock continuation (2026-09-15): the [Act 1 matrix](../architecture/validation/levels/s3k-soz-act1.md)
and [Act 2 matrix](../architecture/validation/levels/s3k-soz-act2.md) track positioned
push/fall/ride/stop spots and transition rewind checks. Cold approach, all-character
breadth, offscreen rider carry and the subtype `$87` door coupling remain open.

SOZ route-controller continuation (2026-09-15): loop fall-through `$3B` and static
solid sprites `$49` replace 33 placements. `TestSozRouteControllersProduction`
adds positioned whole-registry rewind spots at both solid shapes in both acts,
and Act 2 loop capture/held/release for all three main characters. See the
[Act 1](../architecture/validation/levels/s3k-soz-act1.md) and
[Act 2](../architecture/validation/levels/s3k-soz-act2.md) matrices for remaining
cold-route, configuration-breadth and native-comparison obligations.

SOZ connected-mechanism continuation (2026-09-15): the act matrices now bind
floating pillars `$42`, analog push switches `$45`, doors `$46`, and special-rock
slot coupling. Short production spots exercise a connected Act 2 puzzle and
moving-pillar rides with whole-registry rewind. Representative character, width,
team and S1-donor cases supplement local timing/contact/mapping tests. See the
[execution record](../architecture/plans/2026-09-15-soz-methodology-v2.md) for
observed validation and remaining gaps; full act completion is still open.

SOZ presentation obligations are explicit in both act matrices: Act 1 normal
parallax/heat shimmer, scroll-driven animated tiles and arena replacement; Act 2
event-selected backgrounds, darkness-coupled torch art, sand/wrap and boss modes.
Normal Act 1 parallax/shimmer, corrected animated art and sand palette cycling now
have focused checks and 320/528 captures; matched pixels and remaining event modes
stay open independently of the implemented placed-object families. See
the [revised SOZ batch order](../architecture/plans/2026-09-15-soz-methodology-v2.md#explicit-presentation-work-and-revised-next-batch).

SOZ cold-route continuation (2026-09-16): the [Act 1 matrix](../architecture/validation/levels/s3k-soz-act1.md#cold-controller-completion) now records a fixed-input native Sonic + Tails, width-320 run from cold entry through the real boss to playable Act 2. The explicit capture test passes on the merged runtime without skips. Other character/donor/viewport routes and exact native parity remain separate obligations.

SOZ Act 2 cold-route completion (2026-09-16): the [Act 2 matrix](../architecture/validation/levels/s3k-soz-act2.md#cold-controller-completion) records the fixed native Sonic + Tails, width-320 run through eight natural boss hits, capsule/results and playable Lava Reef. Dense capture and explicit destination-control assertions pass without skips. The lower subtype-$87 puzzle now has a connected positioned ordinary Sonic cork-to-door route with15 full-world replay spots; broader parity/configuration products remain open.


SOZ non-trace acceptance follow-up (2026-09-16): every authored checkpoint now
covers six supported character/donor combinations × five required widths, with
real activation, restore/replay and production death/reload (300 cases). Repeated native/mixed/duplicate
team reloads cover the same width/donor product in both acts (90 cases), and four
connected Act 2 mechanism routes cover that product with graph recreation/replay
(60 cases). See the [acceptance record](../architecture/plans/2026-09-15-soz-methodology-v2.md#2026-09-16-non-trace-acceptance-follow-up).
The lower subtype-$87 passage remains unverified after bounded native and engine
input attempts. Native-width cold recordings still complete; unchanged recordings
fail at widths 400/800, so wider full routes require independent input authoring.
Strict traces are deferred at the user's request; these results do not certify
native pixels or complete route/configuration breadth.


The corrected follow-up covers 30 positioned golem-to-Act2 victories and 30 solo
end-boss-to-LRZ victories: six supported character/donor combinations × five widths.
Each verifies natural victory and event/boss graph restore/replay. A five-width
threshold regression and an 800-pixel moving victory capture cover the corrected
Act 1 admission gate. These close bounded boss/transition obligations; full cold
routes remain open. Production launch policy supports Sonic/Tails/Knuckles with
donation off, Sonic with S1, and Sonic/Tails with S2. Earlier counts included
unsupported debug overrides and are superseded. Acceptance now checks usable
ROM-backed art and animations for every live participant, including after loads;
S1 team stress cases use Sonic duplicates. See the
[roster correction](../architecture/plans/2026-09-15-soz-methodology-v2.md#2026-09-16-supported-roster-correction).

SOZ playtest/priority follow-up (2026-09-16): the act matrices now cover native
slot/piece order, arena masks across five actual widths, submerged spike pixels,
miniboss child priority/recreation, Rockn child facing, synchronized pyramid
shake and dark boss-room entry/ghost fade with rewind. The user's missing-shell
and persistent-ghost entry path remains un-reproduced. See the
[combined evidence and limits](../architecture/plans/2026-09-15-soz-methodology-v2.md#playtest-findings-and-matched-allocation-measurement).


SOZ2 last-checkpoint debug skipping now has a production reload regression at
320/400/800px with native Sonic+Tails: destination events, boss wall art/solids,
brightness, preserved lives and rewind timeline isolation. Ordinary checkpoint
activation coverage did not exercise the former coordinate-only shortcut.


S&K campaign reconciliation (2026-09-22): the [audit](../architecture/audits/2026-09-22-sk-zone-bring-up.md)
distinguishes integrated MHZ/FBZ/SOZ/DDZ evidence from the local LRZ/SSZ/DEZ campaigns.
The LRZ summaries above predate their later commits. Current LRZ Act 1 has zero
placeholder placements and a working miniboss/results/act-change implementation;
Act 2 has five placeholders after the turbine continuation. These counts are not
completion claims. The [Act 2 matrix](../architecture/validation/levels/s3k-lrz-act2.md)
records 17 positioned turbine interaction and full-state rewind rows across all
five current viewport presets, S1/S2 donation and native characters, plus six
focused cases. Cold completion and the other inherited matrix gaps remain open.

SSZ Act 2 encounter deformation checkpoint (2026-09-23): the [act matrix](../architecture/validation/levels/s3k-ssz-act2.md)
now binds ROM foreground bands, horizontal/column waves and captured scroll state to
short tests. Native-width and 800-pixel arrival captures agree on all 720 gameplay
rows and on the sampled native world region. This is width preservation evidence,
not native-console visual parity; crane, final fight and seeded mask remain open.


SSZ2 post-defeat component update (2026-09-23): the [act matrix](../architecture/validation/levels/s3k-ssz-act2.md)
now records retained-DPLC breakup pieces (37-case selection, no skips) and the
stage4 floor mutation at320/800, including terrain restore/replay (8-case
arrival-class rerun, no skips). These do not close cold fight, native visual,
attack-family or seeded island/redraw obligations.


SSZ2 follow-up (2026-09-23): [act matrix](../architecture/validation/levels/s3k-ssz-act2.md)
now records hardware body-priority verification, five-preset scripted-pan checks,
wide Emerald preview handover, cold final-fight disk-clear persistence and the
real HPZ load through SSZ arrival/crane replay. These are focused advances, not
act-wide certification or a campaign full-suite result.


SSZ1 cold-route follow-up (2026-09-24): the [act matrix](../architecture/validation/levels/s3k-ssz-act1.md)
now covers fresh native320 arrival through first replica defeat, elevator/swinging
carriers and the upper walkway, with ten full-registry replay windows. The route
exposed and fixed premature carrier-child culling. User visual review also exposed
missing Act1 Eggmobile registration; both replica captures now include the body.
83focused checks pass; full-act, wide route and campaign delivery remain open.

SSZ replica arena-mask trial (2026-09-24): shared mask state/GL checks and
approved GHZ/MTZ event wiring are recorded in the [act1 matrix](../architecture/validation/levels/s3k-ssz-act1.md).
This is focused trial evidence; broader lifecycle/combined delivery checks remain open.


DEZ2 gravity-reference follow-up (2026-09-25): the
[spike hurt-routine checks](../architecture/validation/levels/s3k-dez-act2.md#spike-hurt-routine-selection-2026-09-25)
cover initial gravity/placement selection, later gravity changes, rewind and
actual solid contacts from both physical faces. Together with the preceding
monitor corrections, no gravity-reference row remains wholly missing; five
partial rows and the broader act/character/lifecycle obligations remain open.


The subsequent [hurt-boundary proof](../architecture/validation/levels/s3k-dez-act2.md#hurt-death-plane-early-return-proof-2026-09-25)
distinguishes the early hurt return from the later kill for all three characters.
Two gravity-reference rows remain partial; this evidence-only step does not
close the broader DEZ act-matrix obligations.


The [edge-balance probe proof](../architecture/validation/levels/s3k-dez-act2.md#inverted-edge-balance-probe-proof-2026-09-25)
covers shaped top-only columns, exact cutoff/precarious probes and the angle gate
for all three characters in both gravity states. The top-solid landing row is
the sole remaining partial gravity-reference row; full-act obligations remain.


DEZ2 top-solid follow-up (2026-09-25): exact flat windows and direct sloped
entry checks close the reference inventory at112 covered/four not applicable.
A mirrored slope-snap error was reproduced and fixed;161 focused checks pass,
zero skips. This is reference coverage, not full-act or combined delivery approval.


DDZ presentation follow-up (2026-09-25): Hyper-star startup now follows native
module completion, matching506 observed child updates. Focused, full DDZ route
and rewind-guard checks pass; the DDZ matrix records commands and capture limits.
HUD redraw timing and remaining whole-scene/incoming-route obligations remain.


DDZ HUD follow-up (2026-09-25): live ring awards and retained HUD digits now
follow separate native redraw semantics. Exact entry/drain/publication timing
and mid-hold replay agree with native observations. See the DDZ matrix for
focused test results, coordinate-test correction and capture limitations.


LRZ Tails follow-up (2026-09-25): the [solo Act1 cold clear](../architecture/validation/levels/s3k-lrz-act1.md#tails-solo-cold-clear-and-waiting-hand-restore-2026-09-25)
adds34128 controller inputs through miniboss/results/playable Act2 and51 full-world
restore/replay spots, without gameplay seeding or deaths. It exposed and corrected
waiting-hand creation metadata during rewind. Tails Act2→boss→HPZ, the full Knuckles
route, other breadth/lifecycle products and native scene matching remain open.

Tails LRZ2 follow-up (2026-09-25): the cold solo route now reaches the middle
corridor in 41,922 inputs with no deaths and 25 Act2 full-registry replay spots.
Fresh capture matches the candidate. Later Act2/Act3/HPZ completion and breadth
remain open; see the LRZ Act2 matrix for commands and evidence limits.

Tails LRZ2 completion (2026-09-25): the [cold solo completion](../architecture/validation/levels/s3k-lrz-act2.md#tails-cold-act2-completion-2026-09-25)
reaches the real boulder handoff and boss act in59,712 inputs,zero deaths.
The new test passes60 later Act2 full-registry replay spots, supplementing25
middle-route spots; doors, carry and destination roster are asserted. Fresh
capture matches all59,712 rows. Tails' boss/HPZ continuation and the Knuckles
cold chain remain open; this does not certify other viewport/donor products.

Tails LRZ chain completion (2026-09-25): [cold LRZ1→LRZ2→boss→HPZ](../architecture/validation/levels/s3k-lrz-boss.md#tails-ordinary-cold-completion-2026-09-25)
now completes in68,977 inputs,zero deaths. The new focused test passes62 boss-act
replay spots and observes14 mine hits, earned fire shield, capsule/results and
playable HPZ; prior Act1/Act2 tests retain51/85 spots. Fresh playback matches
all68,977 rows. Knuckles' cold chain and broader products remain open.


DEZ1 solo route follow-up (2026-09-25): ordinary native320 Sonic alone now
clears both eight-hit miniboss phases and reaches released Act2 control from
cold entry in23533 inputs, zero deaths;61 full-registry restore/replay windows
and seamless history isolation pass. See the [Act1 matrix](../architecture/validation/levels/s3k-dez-act1.md#ordinary-sonic-solo-cold-completion-2026-09-25).
Solo Act2/final, Tails, wider products and native presentation remain open.


DEZ1 Tails follow-up (2026-09-25): ordinary native320 Tails alone now completes
cold Act1 and its real Act2 arrival in29521 inputs with75 additional full-world
rewind/replay windows, zero deaths. Both solo tests pass (136 spots total).
See the [Act1 matrix](../architecture/validation/levels/s3k-dez-act1.md#ordinary-tails-solo-cold-completion-2026-09-25).
The mandatory native cold Act1 roster is covered; solo Act2/final and remaining
breadth/native presentation are open.


DEZ2 Sonic solo follow-up (2026-09-25): the ordinary native320 cold Act1/2
route now clears all8 gravity-boss hits, loads the final stage and passes103
full-registry replay windows plus actual-load history reset/seek. See the
[Act2 matrix](../architecture/validation/levels/s3k-dez-act2.md#ordinary-sonic-solo-cold-completion-2026-09-25)
for the slot-restoration and retired-parent repairs, focused results and video.
Solo final completion, Tails Act2/final, remaining breadth and combined campaign
verification/integration are still open.


DEZ2 Tails pad follow-up (2026-09-25): the lower-bridge cold frontier now has12
full-registry replay windows and a fresh47140-row capture match. The gravity-pad
codec preserves pending press/occupancy callbacks, fixing the snapshot-only
extra toggle. See the [Act2 matrix](../architecture/validation/levels/s3k-dez-act2.md#tails-lower-gravity-pad-replay-repair-2026-09-25)
for failing-before regressions, focused checks/guards and scope. Tails Act2/final
completion and remaining campaign obligations are still open.

DEZ2 gravity-render correction (2026-09-25): the
[Act2 matrix](../architecture/validation/levels/s3k-dez-act2.md#player-sprite-mirror-cancellation-2026-09-25)
records the animation/draw double-XOR regression and corrected cold solo-clear
video. All53842 recorded movement rows remain identical; frame49680 now displays
the native inverted orientation. This closes the reported player mirror defect,
not the remaining route, presentation or combined delivery obligations.

DEZ solo Sonic cold-chain milestone (2026-09-25):
[final matrix](../architecture/validation/levels/s3k-dez-final-boss.md#ordinary-solo-sonic-cold-ending-route-2026-09-25)
now records64477 inputs from DEZ1 through both acts, final hands/core/ship and
actual ending load,0 deaths,68 full-registry replay windows. Solo Tails final
completion and remaining native/width/donor/lifecycle obligations stay open.

FBZ checkpoint contact follow-up (2026-09-25): all11 posts × four native teams
now activate by ordinary movement from a declared48px local approach and pass
two full-registry90-input replays. Combined placement/contact/saved-reload test:
89 pass,0 skips. See the FBZ act matrices; this does not establish cold reachability
to every post or the remaining lifecycle/viewport/donor products.

DEZ2 Tails cold completion (2026-09-25): the
[Act2 matrix](../architecture/validation/levels/s3k-dez-act2.md#tails-solo-cold-act2-completion-2026-09-25)
now records62588 inputs,56 full-registry replay windows,all eight boss hits and
the actual final-stage load/history reset. Fresh capture matches the authored
route with no deaths. Tails' final fight and broader lifecycle/native/breadth
obligations remain open; the initial continuation runs out of time.

DEZ Tails ordinary ending route (2026-09-25): the
[final matrix](../architecture/validation/levels/s3k-dez-final-boss.md#ordinary-solo-tails-cold-ending-route-2026-09-25)
records68266 cold controller inputs through all final phases and actual ending,
with a separately captured20-field row match and no deaths. The84 new registry
replay windows passed (1 test, no failures/errors/skips); campaign validation/integration and
remaining breadth/lifecycle/native obligations are still open.

SSZ solo Sonic replica route (2026-09-25): the
[Act1 matrix](../architecture/validation/levels/s3k-ssz-act1.md#solo-sonic-replica-route-2026-09-25)
records both eight-hit fights and escapes in 11,200 cold inputs, no deaths,
42 passing full-registry replay windows and a separately matched capture.
Final Mecha/DEZ handoff and other inherited matrix obligations remain open.

SSZ solo Sonic full clear (2026-09-25):
[complete route](../architecture/validation/levels/s3k-ssz-act1.md#solo-sonic-complete-cold-route-2026-09-25)
now loads DEZ1 after all three bosses in 19,845 cold inputs, no deaths. The final
route adds 41 passing restore/replay windows and actual handoff history isolation;
Tails and remaining viewport/donor/lifecycle/native breadth remain open.

FBZ checkpoint lifecycle (2026-09-25): both act matrices now include physical
contact followed by two full death/reloads at each of eleven posts, four native
teams and five selectable widths (220 cases, 440 loads). The full checkpoint
class passes 309 tests without skips, including team/runtime replacement and
live-history isolation. Local approaches and induced pit death are declared;
donor coverage and cold reachability to every post remain distinct obligations.

FBZ donor checkpoint lifecycle (2026-09-25): real S1 Sonic and supported S2
standard teams add 220 post/width cases with two real reloads each. Native plus
donor coverage now verifies 880 reloads; the whole class passes 529 tests with
no skips. Donor activation, retained capabilities and decoded participant art
are asserted after each load. Cold routes and other matrix obligations remain open.

SSZ Tails opening (2026-09-25): the signed flight-ceiling correction restores
ascent with the native negative camera minimum. A preserved 2,556-input cold
opening clears the raised platform without deaths; complete Tails SSZ remains
open. Existing LRZ/DEZ cold routes still pass unchanged (10 tests, no skips).

SSZ Tails continuation (2026-09-26): the
[act matrix](../architecture/validation/levels/s3k-ssz-act1.md) records a fresh
17,670-input cold completion through DEZ1 and matched engine captures. New
53-window replica and47-window upper/final rewind tests pass. An800px
replay exposed an unreachable GHZ entry gate; a bounded preliminary camera
projection fix now passes the five-width cold-approach regression (7 tests
including both routes, no skips); integrated42-case route/arena verification
now passes without skips, including all8 final Tails cases.
Native presentation parity and other inherited breadth/lifecycle gaps stay open.

The800 replica continuation additionally passes53 full-registry replay windows,
both eight-hit defeats and both gated transport releases. Fresh wide movies
include defeat/release and match the independent probe's state rows. Full800
Mecha completion is still open; no additional mask behavior was changed.


SSZ wide final continuation (2026-09-26): cold Tails exposed a35px Mecha attack-box
shift missed by checkpoint starts. The final captured arena flag now uses the
existing widescreen fixed-X policy; the shared camera and player bounds are
unchanged. Cold320/800 pre-activation/allocation regressions and five checkpoint
presets pass. Native17,670 and wide16,893-input full Tails routes now pass with
actual DEZ load, all eight final hits, no deaths, history isolation and lock
retirement;100 native/102 wide route rewind windows are verified. Fresh800 video
includes the fight, results, ascent and DEZ arrival. The reusable controller
lookahead author is promoted with independent fresh-replay verification.
Other matrix gaps and native-emulator presentation parity remain open.


MHZ physical-post lifecycle follow-up (2026-09-26):
`TestMhzCheckpointRoutes` adds ROM-inventoried local activation and whole-world
rewind for all nine posts and the three accepted native rosters; real two-death
reload cases span all five presets and supported native/S1/S2 rosters. These are
local approaches, not new cold-route certification. Scripted checkpoint, broader
team and presentation obligations remain separately tracked in both act matrices.


MHZ Tails route (2026-09-26): native 320 cold Act 1 completion now has preserved
controller inputs, all six miniboss hits, actual MHZ2 rebase/released movement
and seven whole-registry replay spots. Fresh moving evidence covers fight and
handoff. The route reaches the Act 2 leaf blower; its Act 2 completion, wider
route configurations and native-emulator presentation remain open.


MHZ2 floor grab (2026-09-26): solo Tails exposed a player-slot/character-identity
confusion in raw pose selection. The ROM table is now selected by character, with
phase-specific unit coverage and 15 production entry/pose/lift/release/replay
cases across Sonic, Tails and their native pair at all five presets. Fresh Tails
video changes only the 124 affected mapping-frame rows in its shared input range.
Tails Act 2 route completion remains open.


MHZ Tails Act 2 continuation (2026-09-26): preserved cold inputs now complete
both acts, the nine-hit endboss, capsule/results, ship carry and released FBZ1.
The shared Sonic/Tails Act 2 route test adds live palette-fade deletion/recreation
and forward replay; Tails also covers the late pulley and upper flight. The live
route exposed and fixed a populated-array reconstruction error missed by the
empty-constructor inventory probe. Fresh native 320 video/state replay agrees
through the handoff. Wider/team/donor route and native presentation gaps remain.


MHZ native pair (2026-09-26): a controller-only Sonic + Tails cold Act1 route
now completes the miniboss and the Act2 introduction. Nine whole-world replay
spots exposed and now cover shared horizontal-bar hang/cooldown ownership;
three short reconstruction cases cover jump and both automatic releases.
The fresh engine movie includes the fight and paired floor-grab/lift scene.
Paired Act2 completion and wider/donor/native-reference gaps remain open.


DEZ physical checkpoint follow-up (2026-09-26): both act matrices bind
`TestDezCheckpointRoutes` to every physical starpost, local activation with full-world
restore/replay, repeated real death/reloads across five widths and supported
character/donor teams, plus800px native trio. Act2 post5 reaches inverted gravity
through its real nearby swap before each death. These positioned local checks do
not replace cold routes or native presentation evidence.


SSZ native presentation follow-up (2026-09-26): read-only original-movie
observations corroborate replica ship/body ordering and Mecha's high tile priority
through both Knuckles fights and airborne power-down. Fresh native320 engine team
and Knuckles captures complete without deaths. Per-act matrices retain the
unsupported claims explicitly: differing inputs preclude aligned pixel/timing
parity, and the native Hyper victory skips MTZ's later normal orb/laser phases.


SSZ normal MTZ reference follow-up (2026-09-26): the
[Act1 matrix](../architecture/validation/levels/s3k-ssz-act1.md) now records all
eight normal phases across two declared native checkpoint experiments. A live
laser-pair regression covers the corrected art priority and native0/2 subtypes;
92 combined encounter, cold-route and mandatory S3K checks pass without skips.
This does not certify aligned pixel/timing parity or the remaining route products.


MHZ native-pair completion (2026-09-27): the
[Act2 matrix](../architecture/validation/levels/s3k-mhz-act2.md) now records a
40,630-input cold MHZ1→MHZ2→playable FBZ route, all nine final-boss hits and zero
deaths. Sixteen paired Act2 full-registry replay spots and a separate production
GameLoop live-history reset check pass. The short Act1/entry fixtures remain
independent; remaining width/donor/team products and native visual parity stay open.


### SSZ1 bridge checkpoint follow-up (2026-09-27)

The [SSZ1 matrix](../architecture/validation/levels/s3k-ssz-act1.md#cold-bridge-checkpoint-and-repeated-respawn-2026-09-27)
adds cold arrival to the implicit checkpoint and two real GameLoop death/reloads
for25 native/donor/preset configurations. It reproduces and corrects the bridge's
activation-history/index mismatch. This closes the specific bridge-restart gap;
remaining SSZ route, roster and native-presentation obligations remain open.


### SOZ2 lower puzzle: ordinary Knuckles and viewport breadth (2026-09-27)

The [Act2 matrix](../architecture/validation/levels/s3k-soz-act2.md#ordinary-knuckles-lower-puzzle-and-current-viewport-breadth-2026-09-27)
adds a positioned ordinary-Knuckles completion and checks both Sonic/Knuckles
routes at all five current display presets, including connected cork/rock/switch/
door behavior and whole-registry replay. Full cold routes and other roster/donor
products remain separate.

SSZ MTZ orb ownership (2026-09-27): the [Act1 matrix](../architecture/validation/levels/s3k-ssz-act1.md)
adds harmful/unlisted ordinary touch rejection, production Hyper dispatch and
whole-registry pending-touch replay. Per-hit orbit counts match the native fight's
seven nonfatal launches. The paired input fixture is reauthored through the fight
and still preserves all 22/37 replica/complete replay spots and live DEZ-history
isolation. The complete route now reaches DEZ after 19,457 inputs. Solo Sonic/Tails
routes pass unchanged; freed-slot edge cases and broader native parity remain open.


### SOZ paired cold-route revalidation (2026-09-27)

The [Act1](../architecture/validation/levels/s3k-soz-act1.md#frozen-cold-route-replay-revalidation-2026-09-27)
and [Act2](../architecture/validation/levels/s3k-soz-act2.md#cold-route-revalidation-2026-09-27)
matrices now bind ordinary native320 Sonic + Tails cold completion to
`TestSozColdRouteCapture`. Act1 retains its input; Act2 has reauthored inputs after
source-backed push-switch landing corrections invalidated its old recording.
The routes cover61 periodic/destination full-world replay windows, with additional
semantic mode/boss/capsule/results checks and actual LRZ history isolation.
No runtime physics was changed to preserve the old recording. Other cold
character/donor/width products and matched native presentation remain open.


SOZ solo Sonic cold completion (2026-09-27): the
[Act2 matrix](../architecture/validation/levels/s3k-soz-act2.md#solo-sonic-cold-route-2026-09-27)
now records the native320 controller-only route through eight hits, capsule,
results and playable LRZ. `TestSozColdRouteCapture` covers both solo acts as
well as the pair. Other roster/width/donor products and native presentation
remain open; see the campaign audit for verification and integration state.


SOZ Tails Act1 cold route (2026-09-27): the
[Act1 matrix](../architecture/validation/levels/s3k-soz-act1.md#solo-tails-cold-route-2026-09-27)
adds ordinary native320 Tails golem/Act2 completion and 18 full-world traversal/
destination replay windows plus the results-start observation. Tails Act2 is
covered below; other cold products remain open. Execution/integration evidence
is in the audit.


SOZ Tails Act2 cold route (2026-09-27): the
[Act2 matrix](../architecture/validation/levels/s3k-soz-act2.md#solo-tails-cold-route-2026-09-27)
now defines the native320 solo Tails product through eight boss hits, capsule,
results and playable LRZ, with30 traversal/destination full-world replay
windows and semantic encounter/load-boundary checks. Execution and delivery
evidence is in the campaign audit. This does not close Knuckles cold routes,
other viewport/donor products or matched native presentation.


Knuckles raw-animation ownership (2026-09-27): the FBZ1 matrix now covers
placed-pole capture, glide-state clearing and whole-world restore/replay at320/800;
the SOZ2 wire receives the same short independent coverage. SSZ2's existing cold
fight/rewind route passes both widths after a one-frame controller release repair.
The refreshed800px capture exposed an inherited island/cloud rendering gap;
the SSZ2 matrix now records its shared shader correction, reproducing GPU test
and fresh full-route video. This is focused evidence,
not full campaign or widescreen visual acceptance.

### SOZ Knuckles cold Act 2 completion (2026-09-27)

The [Act 2 matrix](../architecture/validation/levels/s3k-soz-act2.md#knuckles-cold-act-2-completion-2026-09-27) now records ordinary Knuckles solo from cold native-320 entry through
the cork/rock and upper-switch puzzles, eight boss hits, capsule/results and
playable Lava Reef. The permanent fixed input has 35,315 frames; the dedicated
`TestSozColdRouteCapture` method checks 48 traversal/destination replay windows,
including eleven puzzle points, plus boss/background/capsule/results semantics
and real load-history isolation. Fresh fixed-input video and the candidate
replay checks are in the matrix; combined delivery evidence is in the campaign
audit. This completes native-320 cold routes for Sonic, Tails and Knuckles in
both SOZ acts, alongside the paired route. It does not close every act obligation.
The original input fails at 800px after an earlier light-switch hit. A separate
35,440-input [800px cold route](../architecture/validation/levels/s3k-soz-act2.md#knuckles-800px-cold-completion-2026-09-27)
now also reaches playable LRZ, with 48 replay windows and 18 semantic events.
Other viewport/donor products and native presentation certification remain open.


MHZ widescreen delivery (2026-09-27, `a2fad9a79` from base `5ff400010`): controller-only 800px Sonic Act 1 completion adds 17 whole-world
rewind/replay spots; independent five-width checks cover the miniboss gate, arena
repeat and inherited Act 2 results-window lifecycle. Native Act 2 Sonic/Tails/team
routes remain passing. The per-act matrices record exact focused counts and the
inherited reload-art glitch. 800px Act 2 completion remains pending. The integrated 299-case focused
selection and all 672 structural guards pass under the documented proportionate
validation scope; no full ordinary-suite pass or additional act certification
is claimed.

MHZ handoff art follow-up (2026-09-27): the inherited glyph corruption was a
background-tile retention fault, not stale sprite art. The existing resource
handoff now preserves the two direct-DMA ranges through reload; the per-act
matrices link the independent immediate-reload regression. Fresh native/800
captures show intact backgrounds. Empty sprite publication at the boundary and
800px Act 2 traversal remain open; this does not certify another route.

Seamless-publication candidate (2026-09-27, base`6088735c8`): independent five-zone
reload checks distinguish retained SAT/HUD from full-load reset, and MHZ carries
its native loop-adjusted background origin.187 focused cases pass. Fresh native/
800 MHZ and native DEZ/LRZ handoffs are rendered. Current-base route revalidation
also exposes FBZ Tails/Knuckles deaths;
see [open issues](s3k-known-bugs.md#cold-route-evidence--fbz-tailsknuckles-open).
Historical completion results remain historical, not current-base guarantees.

SOZ capture reconciliation: enabling the native falling intro and executing one
ordinary neutral setup step before input0 reproduces the cold Sonic route. Base
and candidate both reach Act2 at31392 with zero deaths; all31492 state rows match.
The earlier default-tool death is caused by its skip-intro entry mode, not a
regression in this native-start route. A fresh corrected SOZ handoff clip exists.


MHZ 800px continuation (2026-09-27, candidate from760a22892): frozen cold
Sonic-only inputs now reach playable FBZ through both acts with zero deaths.
The production-loop route adds25 named whole-world replay spots, each with
two replay cycles. It exposed the twisted vine's omitted persistent rider set;
four independent lower/upper-curve and320/800px cases cover both player IDs
and release after recreation. Source-only diagnostic replay passes; normal
Maven verification and integration remain pending. See the Act2 matrix for
capture provenance and the unresolved RecordingFrameDriver discrepancy.

FBZ Knuckles follow-up (2026-09-27, candidate): input-only corrections retry
the prior hub after knockback and time the upper-carousel jump. Frozen22,055
inputs reach released Act2; an independent fresh capture matches all gameplay
rows with zero deaths. Whole-world frozen-input replay and five existing route
products await normal Maven verification; native320 only, not donor/width
completion. The Act1 matrix records rejected inputs and provenance.


DEZ Act2 800px follow-up (2026-09-28, integrated559cb83d5):53,564 cold solo Sonic inputs
clear both main acts and reach the actual final-stage load at53443. The new
Act2 route adds110 whole-registry windows with two replay cycles each and
live-history reset verification. The first run exposed a falling tilting-bridge
section's stale parent reference; a local routine-boundary retirement fix and
short independent regression pass in the72-case focused selection, with no
failures/errors/skips. Destination verification adds13 passing tests with no
skips; all110 wide rewind windows and load isolation pass. Fresh forward captures
have no deaths. See the Act2 matrix and campaign audit; broader/native parity
obligations remain open.


DEZ800 ordinary ending (2026-09-28, integrated8cebbd847):60,298 cold solo Sonic inputs now
clear both main acts and all final phases through actual ending zone13/1. The
faster Act2 route saves3072 inputs without changing the timer. Fresh replay matches
all20 state fields over the complete route, zero deaths. The177-window two-cycle
rewind test and both full-load history checks pass in the corrected12-case
focused run, zero failures/errors/skips. The integrated full-route test also
passes all177 windows and both load-history checks; see the
final-arena matrix. The longer110-window Act2 route remains independent.


LRZ cold 800 Act1 follow-up (2026-09-28): a separate25,650-input native-pair
fixture now reaches all six miniboss hits, results, seamless rebase and playable
Act2 without deaths. The 115 full-world two-cycle replay windows and live-history isolation pass;
the completed ordinary lane passed25,146 tests with29 inspected skips, and the
separate guard lane passed672 checks. The runner stopped between lanes because
the later Act2 test was added during execution; that test passed separately.
[The Act1 matrix](../architecture/validation/levels/s3k-lrz-act1.md#2026-09-28--cold-widescreen-act-1-completion)
records the final evidence. The short crusher check remains independent;
The41,348-input cold wide Act2 continuation now reaches playable boss-act
arrival with104 two-cycle checkpoints and full-load history isolation passing;
see the [Act2 matrix](../architecture/validation/levels/s3k-lrz-act2.md#cold-800px-sonictails-act2-clear-2026-09-28-candidate).
The50,659-input cold wide boss continuation now reaches playable HPZ, with71
two-cycle checkpoints, earned-shield/14-hit/capsule/results observations and
full-load history isolation passing. Its independent video matches every CSV
row. [Boss matrix](../architecture/validation/levels/s3k-lrz-boss.md#cold-800px-sonictails-complete-route-2026-09-28-candidate).
Integrated as `074dafe84` / `46666b019`; destination114 tests pass with no
failures/errors/skips, final candidate guards672 pass. Other roster/donor/
native-scene breadth remains open.


SSZ collapsing-column lifetime (2026-09-28): the [Act1 matrix](../architecture/validation/levels/s3k-ssz-act1.md#collapsing-column-parent-lifetime-2026-09-28)
records a cold800 pair-route discovery and a short regression for falling debris
whose parked parent has left the rewind world. Hanging/released links, the final
parent-Y sample and repeated restore/forward replay are covered independently
of the unfinished wide pair route. This does not close the act's breadth or
native visual acceptance.


SSZ cold800 Sonic+Tails first replica (2026-09-28): the [Act1 matrix](../architecture/validation/levels/s3k-ssz-act1.md#cold-800px-pair-through-the-first-replica-2026-09-28)
adds a permanent9,914-input cold prefix through GHZ defeat and its transport,
with40 two-cycle whole-registry replay windows. Independent rendered replay
matches all recorded state fields. The complete wide pair route and native
visual parity remain open.


LRZ Toxomister presentation (2026-09-29): the
[Act1 evidence](../architecture/validation/levels/s3k-lrz-act1.md#toxomister-presentation-and-dispersal-2026-09-29)
and [Act2 binding check](../architecture/validation/levels/s3k-lrz-act2.md#toxomister-presentation-follow-up-2026-09-29)
cover native stalk coordinates, ROM cloud animation/deletion and puff rewind.
Gameplay capture is blocked by the existing macOS headless OpenGL context;
renderer commands and production ROM art are checked, without native pixel or
broader route certification.

The [Toxomister facing follow-up](../architecture/validation/levels/s3k-lrz-act1.md#facing-correction-after-the-presentation-fix-2026-09-29)
adds live nearest-player turning, breath-side, cadence and rewind checks; the
previous placement-only flip test did not cover that path.

### LRZ ordered issue fixes — 2026-09-29

The [Act 1 matrix](../architecture/validation/levels/s3k-lrz-act1.md#ordered-lrz-issue-fixes-2026-09-29)
records the eight-issue delivery from develop `144ff8b165c6`. The lava-fall
loaded-art regression checks the literal terrain tile base and mapping shape;
local evidence does not certify the inherited route/configuration/native-pixel gaps.

Issue 2 adds both-act, both-direction cardinal render checks plus existing
hazard rewind tests:13 passed,zero skips. The chain draw offset is local and
configuration independent; act-wide gaps remain inherited.

Issue 3 adds crusher art admission, upload, renderer and DMA restore/replay
coverage:25 focused tests and8 rewind guard checks passed without skips; the
final two resource checks also passed. Native cleanup enemy-art restoration
remains an inherited limitation, recorded in the Act1 matrix.

Issue 4 is an audit, not a claimed fix: the ROM targets Player1 and does not
avoid walls. Added placement/P1/P2/render-facing regression; the reported scene
remains unconfirmed pending location/team details. See both LRZ act matrices.

Issue 5 covers all four launcher/projectile flip combinations and renderer
arguments after movement and rewind recreation; unchanged cadence and hazard
state retain their independent tests. Broader act gaps remain inherited.

Issue 6 restores the arm’s subtype6 controllers:37 focused checks passed,zero
skips, including exact three-burst cadence, allocation/RNG behavior and16-frame
whole-world restoration across burst emission and controller retirement.

Issue 7 restores the main drill/body’s subtype0 death controller and adds
31-burst counting plus96-frame whole-world restore/replay through its lifetime.
The firing hand’s shorter controller is covered by issue6.

Issue 8 covers the ending pole’s parent-owned art/display priority, both
priority domains across changes and rewind relinking, and the actual LRZ
child creation and loaded1x2 mapping. No new zone-specific renderer path.

Final ordered-delivery checks: issue8’s27 checks pass; combined stability/local
rewind has65 passes and3 cold-route bootstrap errors (GLSL410 unsupported),
zero skips. Targeted structural guards pass38 with zero skips. The2951-class
plan was not run; broad preflight lacks Lua5.4/PowerShell. See the Act1 matrix
for exact commands, per-issue evidence and remaining limits. Issue4 remains
unconfirmed; no claim that all eight reports or the full suite are green.

SSZ arrival presentation (2026-09-30): the [Act1 matrix](../architecture/validation/levels/s3k-ssz-act1.md#arrival-camera-and-beam-presentation-2026-09-30)
adds 320/800 title-entry camera-lock checks, ROM swing/landing camera checks, and
an independent beam draw/flicker regression. Existing arrival rewind breadth
remains covered; whole-scene visual and donor products remain open.

SSZ presentation fixes (2026-09-30): the [Act1 matrix](../architecture/validation/levels/s3k-ssz-act1.md#ssz-presentation-fixes-2026-09-30)
tracks the seven requested local corrections and focused validation. The opening
Death Egg now uses its native skyline sprite mask, and bridge rendering covers
its full solid span through extension and local restore/replay at320/800; broad compatibility and native
whole-scene parity remain inherited gaps.

SSZ elevator hang facing: the same presentation-fix matrix now adds both incoming
facings and restored ROM-backed player drawing, retaining the existing elevator
whole-registry replay obligation.

SSZ EggRobo attachment: the presentation-fix matrix adds real moving body/child
anchors, mirrored offsets, live flame versus delayed gun Y and graph recreation
in both orientations, including immediate object-manager capture after recreation.
Existing fly-by scaling and animal-launch gaps remain.

SSZ diagonal bridge collapse: the presentation-fix matrix records four local
slope-flip × collapse-side cases, exact intact-to-fragment ROM tile composition,
full stagger delays, deleted-fragment recreation and whole-registry replay.

SSZ rotating platform presentation: the matrix adds all256 pose angles against
ROM mapping geometry, both incoming facings, and complete post/carrier rotations.

SSZ background transitions: the matrix records reproduced blank-cache frames
at320/800 and adds both mode boundaries plus future-mode restore re-render checks.


AIZ fire-reload follow-up (2026-10-03): the
[AIZ1 matrix](../architecture/validation/levels/s3k-aiz1-sonic.md#fixed-fire-reload-camera-lock--2026-10-03)
covers the fixed reload lock. The
[HCZ1 matrix](../architecture/validation/levels/s3k-hcz1-sonic.md) adds the fresh
live-load ordering obligation.
