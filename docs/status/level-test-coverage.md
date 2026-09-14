# Level test coverage backlog

The [standard](../guide/contributing/level-test-standard.md) defines required coverage;
the [delivery plan](../architecture/plans/2026-09-13-level-test-standardisation.md)
defines the audit, pilots, reporting and migration work. This ledger starts the
backlog; it is not a completed coverage audit.

## Work packages

| ID | Work | Status | Completion evidence |
| --- | --- | --- | --- |
| LTS-01 | Publish standard, matrix template and implementation entrypoint links | Documented | Standard and mirrored guidance; [documentation validation](../architecture/validation/2026-09-13-level-test-standard.md) |
| LTS-02 | Resolve registry slots, aliases, routes and non-registry gameplay paths; map existing assertions | In progress | [Initial 26-zone source inventory and estimates](../architecture/audits/2026-09-13-level-test-coverage-inventory.md); per-act matrices and remaining dispositions pending |
| LTS-03 | FBZ/AIZ/HCZ reference matrices, plus S1 GHZ3 and S2 CPZ2 pilots | Pending | Breadth, rewind boundaries, defect sensitivity and measured cost |
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
conditional S3K scope. Their rows link to that zone-level inventory; **all detailed
per-act matrices and conformance assessments remain pending**. The other 34 slots
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
| S3K | `S3K_ANGEL_ISLAND_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_ANGEL_ISLAND_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_HYDROCITY_1` | [Local boss obligations and inherited gaps](../architecture/audits/2026-09-13-hcz1-miniboss-parity.md#hcz1-affected-route-matrix); [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_HYDROCITY_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_MARBLE_GARDEN_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_MARBLE_GARDEN_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_CARNIVAL_NIGHT_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_CARNIVAL_NIGHT_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_FLYING_BATTERY_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_FLYING_BATTERY_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_ICECAP_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_ICECAP_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_LAUNCH_BASE_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_LAUNCH_BASE_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_MUSHROOM_HILL_1` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_MUSHROOM_HILL_2` | [Source inventory](../architecture/audits/2026-09-13-level-test-coverage-inventory.md#sonic-3--knuckles--mhz-delivered-scope-others-conditional); matrix pending |
| S3K | `S3K_SANDOPOLIS_1` | Audit pending |
| S3K | `S3K_SANDOPOLIS_2` | Audit pending |
| S3K | `S3K_LAVA_REEF_1` | Audit pending |
| S3K | `S3K_LAVA_REEF_2` | Audit pending |
| S3K | `S3K_SKY_SANCTUARY_1` | Audit pending |
| S3K | `S3K_SKY_SANCTUARY_2` | Audit pending |
| S3K | `S3K_DEATH_EGG_1` | Audit pending |
| S3K | `S3K_DEATH_EGG_2` | Audit pending |
| S3K | `S3K_DOOMSDAY` | Audit pending |
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
| S3K | `S3K_SLOT_MACHINE` | Audit pending |
| S3K | `S3K_SLOT_MACHINE_2` | Audit pending |
| S3K | `S3K_LRZ_BOSS` | Audit pending |
| S3K | `S3K_HIDDEN_PALACE_SANCTUARY` | Audit pending |
| S3K | `S3K_DEZ_BOSS` | Audit pending |
| S3K | `S3K_SPECIAL_STAGE_ARENA` | Audit pending |

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
coverage; those remain pending the chain recording and independent route checks.
