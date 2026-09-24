# Level test coverage backlog

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
the route-controller continuation and its inherited gaps; the
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
| S3K | `S3K_LAVA_REEF_1` | [Act 1 matrix](../architecture/validation/levels/s3k-lrz-act1.md) — all609 placements resolve,331 live rings. Traversal families, miniboss, results and seamless Act2 handoff are implemented. Positioned native/wide encounter routes now cross the real priority marker and complete the handoff; the post-results ROM palette ramp has full-registry replay and native color/timer corroboration. Cold full-act completion, roster/donor/lifecycle breadth and whole-scene native matching remain open. Earlier trace frontiers are historical, not current certification |
| S3K | `S3K_LAVA_REEF_2` | [Act 2 matrix](../architecture/validation/levels/s3k-lrz-act2.md) - v2 bring-up: all 455 placements resolve to concrete implementations (281 placeholders at the slice 0 baseline), 281 live rings; scroll, animated tiles, rock sprites and the act 2 `$6E`/`$19`/`$1C`/`$16`/`$17`/`$20`/`$99`/`$9A`/`$9B` skins land, with the act 2 art keys now asserted ready at 320 and 400 by `TestS3kLrzCompatibilityMatrix`, with a positioned `$2D` moving-platform ride/recreation/replay at 320/800 after the oscillator-byte correction; broader act-2 routes remain open. The boulder cutscene and both exits are implemented; the background Death Egg sprite now has its ROM art queue, native screen positioning, documented wide lead-in and five-width recreation/replay checks. The post-miniboss palette ramp is now implemented with native timing/color corroboration and320/800handoff replay; native comparison, cold-route certification and remaining breadth/lifecycle obligations remain open |
| S3K | `S3K_SKY_SANCTUARY_1` | [Act 1 matrix](../architecture/validation/levels/s3k-ssz-act1.md) — v2 bring-up in progress; placement census, arrival, act-1 bounds, the opening cutscene, the background (both modes, clouds and solid cloud platforms), the animated tiles, every act-1 traversal family and the EggRobo, the death/checkpoint lifecycle and both act-1 boss recreations are covered: Green Hill (hit window, three-colour flash, ball hitbox and defeat scatter) and Metropolis (lock and spawn at both widths, the ship's two-level dispatch, the seven-orb ring and its front/back sort, the launch a hit triggers, the laser pass and the defeat that raises the `$79:$F6` pad), all driven through the real touch pass. Mecha Sonic is covered from the `$79` pad's allocation through the entry, the attack loop with
`byte_7D2FC`'s per-frame collision byte, the defeat and `loc_7D056`'s handover, by
`TestS3kSszMechaSpawnHeadless` and dated against the run's third SSZ segment `hpz_3`;
his palette rotation and spark child now pass source-row and launch-recreation checks.
The `loc_7C9BA` collision child now has ROM-table, real-hurt and rewind checks; native hit-window phase remains open. The bare allocation was verified to write no slot. The production results-to-DEZ1 launch now passes component handover/rewind checks at
320/400/800, native Sonic + Tails, Tails alone and S1 donor, with a controller Hyper
checkpoint recording through the destination load; exhausted slots, transition-history
isolation and native parity remain open. Both recreations now emit defeat explosions and have defeat-graph recreation/forward-replay checks; a refreshed Metropolis checkpoint recording shows the bursts. Replacement-slot stop-byte fidelity remains open. Both recreations' entry and defeat intervals are compared against the `hpz` segment's aux rows; their orbits, flashes and laser cadence are not, and the Metropolis fight is now filmed end to end (clips 20-22) with `GameplayCaptureTool --rings` |
| S3K | `S3K_SKY_SANCTUARY_2` | [Act 2 matrix](../architecture/validation/levels/s3k-ssz-act2.md) — v2 bring-up in progress; placement census pinned to the ROM; native/wide arrival/crane release and replay pass; encounter deformation, transformation/first Super cycle, defeat controls/effects and floor patch have component evidence; positive-signal island/redraw and ROM water-palette checks pass at320/800, including restore/replay. Full cold fight, incoming HPZ, remaining attack-family validation, native visuals and later presentation remain open |
| S3K | `S3K_DEATH_EGG_1` | [Act 1 matrix](../architecture/validation/levels/s3k-dez-act1.md) — v2 bring-up started. Level load, music, intro run, shared objects, the `$5A` gravity tube, the `$5F` turbine corridor, the `$61` gravity puzzle inside it, the `$A4` Spikebonkers, the `$55` energy bridges and the `$A5` Chainspikes and `$60` bumper walls and `$6D` shock blocks and `$52` lightning and `$50` conveyor belts and `$4D` torpedo launchers and `$4F` staircases and `$5E` hover machines and `$4C` hanging carriers and `$56` curved energy bridge and `$4B` tilting bridges and `$53` conveyor pads and `$4E` lift pads and `$57` light tunnels (365 of 365 objects concrete). Slice 3 is complete and slice 4 is in progress. The corridor now keeps its controller alive, bounces carried players on the puzzle/walls and passes a positioned six-panel-to-exit route at 320 px, including a contact rewind/forward check. Cold act traversal and native comparison remain open. The registered miniboss has two-phase, contact, allocation-prefix and recreated-graph checks, with connected results/reload/transport/control-release regressions from three finishing positions and solo Hyper recordings at 320/800. Cold route, native comparison and remaining roster/donor breadth remain open |
| S3K | `S3K_DEATH_EGG_2` | [Act 2 matrix](../architecture/validation/levels/s3k-dez-act2.md) — v2 bring-up in progress. Reverse gravity is implemented for the player, shields, rings, solid objects, springs and the sidekick (94 of 116 ROM references; the 11 open group A-I rows are in [s3k-known-bugs](s3k-known-bugs.md)), the five gravity families implemented so far are concrete (`$5B`, `$58`, `$59`, `$5A`, `$5C`), and slice 4 has added the `$A4` Spikebonkers, `$5D` retracting springs, `$55` energy bridges `$A5` Chainspikes and `$6D` shock blocks and `$52` lightning and `$50` conveyor belts and `$4D` torpedo launchers and `$4F` staircases and `$4C` hanging carriers and `$4A` floating platforms and `$4B` tilting bridges and `$53` conveyor pads and `$57` light tunnels — 494 of 494 objects, including the registered gravity boss. Act 2 has a **1256-frame route frontier** from the first frame of free play, exact in player position, camera and rings and ratcheted in `TestS3kDezColdRoutes` (390 → 472 → 527 → 616 → 1256 as those four landed); the first divergence is now a one-pixel `x` lag on a shared `$08` platform ride, not a Death Egg object. A cold `$B01` route is not comparable against the committed movie until the act 2 entrance sequence is implemented. Both fixed backgrounds now have 320/352/400/528/800 composition checks and inspected captures; native centre pixels remain unchanged. The seamless act change and entrance transport now have connected regressions and 320/800 solo Hyper recordings; the cold trace has not been remeasured. The gravity boss now has connected entry/fight/defeat/exit checks, allocation-prefix and rewind coverage, plus 320/800 solo Hyper recordings. Native boss parity and remaining route breadth are open |
| S3K | `S3K_DOOMSDAY` | [Zone matrix](../architecture/validation/levels/s3k-ddz.md) — in progress; seeded Sonic route matches native; fresh Super routes complete320/800 with fight/wrap/exit replay; death/restart covered, native presentation and full incoming DEZ continuity open |
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
| S3K | `S3K_LRZ_BOSS` | [Boss-act matrix](../architecture/validation/levels/s3k-lrz-boss.md) —35 placements resolve,52 live rings; carry, checkpoint, flash, autoscroll, platform/lava presentation, end boss, capsule/results and HPZ exit are implemented. A12820-frame fresh boss-act route (declared initial fire shield/37rings) completes the bonus detour, fight and playable HPZ without hurt/death. Component/graph/cold-entry/return replay checks pass; native capsule pose/slots, timing-admission and full route-product obligations remain open |
| S3K | `S3K_HIDDEN_PALACE` | [Act matrix](../architecture/validation/levels/s3k-hpz-act.md) — in progress; entry, events, animation and teleporter breadth covered; Knuckles fight and Sonic/Tails exit open |
| S3K | `S3K_DEZ_BOSS` | [Final boss matrix](../architecture/validation/levels/s3k-dez-final-boss.md) — v2 bring-up started; `$1700` now allocates its floor supports and final boss on the first production frame; forced entry and replay after rewind have focused coverage. Floor/collapse, scroll, laser-upload, retained-plane, hand/finger, vulnerable-core, mouth/beam, fireball, emerald, escape-scenery and ship-decoration and escape-ship and main-controller components now have focused checks and a distinct rewind runtime owner; production input destroys the fingers, defeats the core and reaches the escape-ship chase; retained-plane rendering and floor redraws are connected, with five-width entry/replay checks. Full fight/exit, remaining lifecycle breadth and full-phase visual verification remain open |
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

SOZ Act 2 cold-route completion (2026-09-16): the [Act 2 matrix](../architecture/validation/levels/s3k-soz-act2.md#cold-controller-completion) records the fixed native Sonic + Tails, width-320 run through eight natural boss hits, capsule/results and playable Lava Reef. Dense capture and explicit destination-control assertions pass without skips. The lower subtype-$87 puzzle and broader parity/configuration products remain open.


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
