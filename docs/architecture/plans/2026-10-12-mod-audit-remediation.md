# Mod audit remediation plan and execution status — 2026-10-12

**Goal:** remediate every concrete finding in the [mod author/framework audit](../audits/2026-10-12-mod-author-framework-audit.md),
then reduce repeated author work through proven existing helpers and narrow shared APIs.

**Initial plan base:** reconciled `develop` at `e76a380c3d0c8e3769fcdb745f27ef3d8889c8fe`.
The plan started at audit delivery `5a1f9b0fe`; upstream `dfa79f3b9` and `e76a380c3`
landed during preparation and were incorporated before integration. Initial audit
runtime evidence remains the historical `f9d4e0474` snapshot. This document is an
execution plan; the checkpoint below distinguishes implemented work from remaining delivery gates.

**Completed artifact checkpoint:** clean producer `175531f6dc4a0d0005c4593022393182d1f5e58c`
qualified the repaired consumers and accepted shared contracts at that historical source.
**Qualified creator producer:** clean `54e771314` also contains the later fixture/template,
presentation and standalone-bound repairs; matching kit and external qualifications
are complete at that explicitly recorded source.
The current inventory is **29 maintained manifests**, including Act Practice, bundled Time Attack,
example-mutators and Starpost Valley. Initial 25/28-manifest counts remain historical.
P01–P09, P11–P16, P18 and P20 have implemented repairs and focused qualification;
P09's actual warning ledger and README consolidation are complete. P10's export/test
contracts, seven fresh starter profiles and external controls are qualified.
P17 ends in the explicit implement/defer dispositions below. P19's matching binary
passed 11/11 and replaced only the obsolete installed jar with a preserved rollback.
Main-branch integration, post-integration comparison, push and accounted cleanup
remain delivery gates.

Checked boxes record achieved implementation and the stated focused evidence, not a
full-suite or delivery pass. The updated destination baseline completed with **26
known failures**, not a green ordinary suite; current candidate/integrated outcomes
must be compared by test identity and full assertion. See the
[execution record](../validation/2026-10-12-mod-audit-remediation.md) and the
[current audit dispositions](../audits/2026-10-12-mod-author-framework-audit.md#remediation-execution-and-current-scope-2026-10-12).

**Approach:** correct behavior in the actual consumers first, preserving their game
rules and regression evidence. Extract common mechanics after those consumers prove
the contract. Java 25, Jupiter, the existing SDK/testkit and queued Maven remain the
toolchain. Use the repository's delivery flow for each integrated batch, without a
mandatory approval/review ceremony between routine steps.

Task file lists use the named example's `src/main/java` package root for mod-local
paths, `src/main/java/com/openggf/` for engine package paths, and
`src/test/java/com/openggf/` for engine test paths. Newly proposed files are identified
as new; test selectors without file paths name existing engine wrappers unless marked new.

## Reconciliation with the new upstream framework

The [hosted-run guide](../../modding/guides/gameplay-runs.md) and
[racing subsystem](../../../racing/README.md) add capabilities that must be reused:

- `RunHost.admitStep` already holds **stock-only** scene-launched gameplay while retaining
  native input. `SceneGameplay.launch` deliberately excludes all mod gameplay content.
  It therefore does not directly replace Survivors/Infinite's patched modules or Golf's
  controlled course. P11 extends the existing module seam and tests coexistence with the
  hosted stock-run gate; it does not add another stock-run API.
- Shared annotated `com.openggf.net` clients, room services and JDK host contracts are
  now available independently of Time Attack. P17 compares/reuses these before proposing
  transport changes. Race-specific protocol types must not become Golf's game rules.
- `ModStorage.readBytes`/`writeBytes` already supply 4 MiB binary files alongside 1 MiB
  text files. P01's storage fakes implement both; P12 adds load/recovery outcomes over
  existing storage rather than inventing binary I/O. Size alone does not establish a
  safe whole-world/chunked generation protocol.
- [Act Practice](../../../examples/act-practice/README.md) and the bundled
  `racing/time-attack` are new sources outside the initial audit's 25-manifest inventory.
  P01 includes Act Practice's observed unchecked best-time write; P20 audits the new
  sources and records any additional findings. Initial audit counts remain historical.
  The actual exporter now has thirteen projects including Tide; portable example test
  sources still total 60 with only Hello's one selected at this base.

This reconciliation inspects the changed seams and source owners; it does not claim a
new full audit or passing upstream engine suite. Upstream's own validation record reports
incomplete ordinary-suite coverage and attributed baseline failures; future batches must
use the current matched baseline rather than inheriting a full-green assumption.

## Sequence and ownership

| Wave | Tasks | Result |
|---|---|---|
| **1 — protect progress and restore correct play** | P01–P06 | Save preservation/results, bounded decoder, generated camp, render-independent targeting, payout regression/repair, practice and calibration correctness. |
| **2 — remove lifecycle and authoring friction** | P07–P11 | Generation-scoped caches, instance-owned catalogues, owner-storage/helper adoption, reproducible creator forks, native-input-preserving holds. P11 is the first shared runtime API change. |
| **3 — extract proven common mechanics** | P12–P16 | Save-slot helper, native contact/effects, composition/canvas helpers, verified ROM recipes and character/registry convenience. |
| **4 — resolve remaining capability and quality decisions** | P17–P20 | Bounded investigations with explicit implement/defer verdicts, focused orchestration/diagnostic cleanup, installed-artifact verification and audit of newly landed mod sources. |

Tasks in a wave can be independent, but source ownership matters. Keep a single owner
for Flappy scene/records, Sitar scene/settings, Eggman's Sky state/art, and Survivors
Stage/RunState/Profile during a batch. P01/P02 overlap Eggman's Sky; P04/P14 overlap
Slay presentation; P05/P11 overlap Survivors. Run these sequentially in their shared
files. Export/tooling can proceed independently. If delegation is used, give workers
disjoint source ownership and integrate contracts before dependent migrations.

Do not wait for a shared helper to fix a player-visible defect. P12 depends on P01/P02;
P14 depends on P04; P16 does not depend on unrelated world/camera work. P09 records
the remaining dependency inventory, which P13/P15/P16/P17 subsequently reduce.

## Coverage map

| Audit item | Owning task(s) | Recorded disposition / remaining qualification |
|---|---|---|
| R1 — rejected/malformed/future saves | P01, P02, P12 | Implemented: rejected bytes survive entry/exit/retry; candidate decode validates before allocation/publication. Includes Starpost explicit replacement and exact Capsule targets. |
| R2 — recovery and save outcomes | P01, P12 | Implemented: checked dirty snapshots, validated recovery and required-backup failure gates; explicit reset retains a usable following generation. Per-file atomicity only. |
| R3 — Starfall camp | P03 | Implemented and focused-qualified: generated camp interaction, all supported world sizes and restore. |
| R4 — Slay draw-dependent targeting | P04, P14 | Implemented: prepared layout is authoritative; zero/sparse/duplicate draws preserve outcomes. ROM CPU composite pixels pass; GPU availability remains a recorded skip. |
| R5 — Survivors payout across rewind | P05 | Reproduced and repaired: persistent once-per-run settlements and milestone accounting survive production restore and failed-write retry. |
| R6 — menu/world hold semantics | P11 | Implemented: native/controlled/HOLD admission, host pause before callbacks, once-only input/audio/fade and native-act retirement; focused runtime/ROM checks passed. Final broad comparison remains open. |
| R7 — galaxy/cache identity | P07 | Implemented and focused-qualified: generation identity, bounded caches, re-entry/new expedition/Continue isolation. |
| R8 — practice/calibration | P06 | Implemented: explicit practice capability and configured timestamped rising edges, with remapping/device/retry regressions. |
| R9 — internals and raw storage | P09, P13, P15, P16, P17 | Existing alternatives and proven narrow services migrated. Actual 29-manifest/45-reference ledger and README consolidation complete; retained expert bridges have purpose/owner/reopening dispositions. |
| R10 — omitted creator tests/tools | P10 | Export/test/generator repairs implemented; real external forks and starters qualified at recorded identities. Final starter/failure/empty/mismatch matrix remains open. |
| R11 — repeated hot-path definitions | P08 | Implemented: immutable owner preparation, protected arrays, seeded/RNG equality and concurrent/reload isolation; measured lookups changed 1,000→1 identities. |
| U1 — save-slot orchestration | P12 | Implemented: Three Islands and Slay profiles, plus Starpost progress/settings, use checked owner-local slots. Specialized Starfall/ghost protocols remain separate. |
| U2 — ordinary/controlled/HOLD rows | P11 | Implemented in one production path; Golf neutral stepping and intentional Infinite partial native phases remain preserved. |
| U3 — terrain/contact/effects | P13 | Implemented bounded semantic floor/contact/spring/destruction seams with real consumers; full stock spring/title/auxiliary internals remain explicitly retained where equivalence is unproved. |
| U4 — composite geometry/canvas transforms | P14 | Implemented and migrated: pure placement, scoped clipping/alpha, nested retirement and draw independence; CPU pixel evidence does not certify the GPU renderer. |
| U5 — stock art and level-kit adoption | P09, P15 | Implemented verified recipes and Starfall material crops; ten backdrop variants, Blue Flicky frame semantics and native title/GPU bridges retain explicit raw intake dispositions. |
| U6 — test-ready forks/starters/launchers | P10 | Implemented source/testkit/export contracts; final clean-kit starter and negative discovery/identity checks remain separate gates. |
| U7 — character and registry convenience | P16 | Implemented: factory-bound canonical identity and declarative verified-owner dispatch, with fresh/reused-class/foreign-key/recreation and SDK-only checks. |

Further audit observations map to P17 (art preparation, transport, auxiliary spawning,
title cards, font/focus), P18 (orchestration and diagnostic status), and P19 (stale
installed sample jar). Documentation corrections already delivered by `904b5f263` /
`5a1f9b0fe` need no repeat implementation; update them only when behavior changes.

## Execution and verification rules

At each batch start fetch/fast-forward the main workspace's current branch, pin its
actual integration SHA as `BATCH_BASE`, and create a `feature/ai-*` or `bugfix/ai-*`
branch in a separate `.worktrees/` checkout. Never switch the main branch or disturb
unrelated changes. Install hooks in each worktree. Resolve routine implementation
choices within the stated contract; do not start a separate approval ceremony.

Before a production fix, add a regression at the real failing boundary and demonstrate
its failure for the stated reason. R5/R6/R7 are source-supported risks, not completed
runtime reproductions: establish their behavior before changing it. If the production
test disproves a risk, keep the test and record the actual protecting mechanism in the
audit rather than inventing a repair. Use real Jupiter discovery for test suites;
standalone probes are diagnostics, not suite passes.

The focused commands below are iteration checks, not complete delivery qualification.
Run them through `tools/testing/maven_queue.py`. Discover existing ROMs, verify the
required identity, and set absolute `S1_ROM`, `S2_ROM`, `S3K_ROM` paths; do not create,
rename or copy ROMs to make an example work. Append the relevant properties to ROM-
backed commands: `-Dsonic1.rom.path="$S1_ROM"`, `-Dsonic2.rom.path="$S2_ROM"`,
`-Ds3k.rom.path="$S3K_ROM"`. Record actual skipped test identities/reasons. Use normal
queue reservations for ROM, route, graphics and memory-heavy checks; `--lean` is only
for bounded exact-selector unit checks without profiles or custom JVM overrides.

For the combined batch inspect `python3 tools/testing/run_categories.py --base "$BATCH_BASE"`.
Use the repository's proportionate-validation exception only for bounded, understood
changes with direct production/edge-case coverage; state its coverage limits. Shared
algorithms, public contracts, build policy and timing changes require normal change-
based validation. Before a broad run report selected class count, expected cost
(about 24 minutes ordinary plus 10 minutes guards at the audit), stopping rule, and
run actual-environment `--preflight`. Run `--base "$BATCH_BASE" --run` without manually
narrowing selection, plus affected domain/trace checks. Compare failures by identity
and message, attribute disputed failures with matched focused baseline checks, inspect
`results.json`, and acknowledge consumed diagnostics. Do not run a full engine suite
solely to verify a prose-only update.

API tasks keep the mutable candidate policy consistent: annotate every reachable new
engine type, regenerate the `0.7` signature pin, and verify descriptor/`ModApiVersion`
agreement. Keep `0.7.0 candidate` unless the batch deliberately changes that policy;
additions alone do not publish a baseline or require a fictional version bump. Test
registration transactions, foreign-owner rejection, callback faults and required-
dependent disable. Shared code takes semantic rules/providers, not mod/game names.
Assets remain supplied-ROM or bounded original creator content. Trace comparison data
never hydrates gameplay. New gameplay state uses production capture/recreation.

Each batch updates affected creator guides, sample READMEs, API Javadocs, validation/
route obligations and the audit disposition. Follow thematic develop changelog policy;
add all seven documentation trailers and the contributing model co-author trailer,
checking the exact prepared message with `git interpret-trailers --parse`. Integrate
into the unchanged main branch, verify the integrated range, push only that branch,
then remove only accounted fully merged worktrees/branches and temporary outputs.
Preserve unrelated installed mods, saves, dirty submodules and unknown work.

## Wave 1 — concrete correctness repairs

### P01. Preserve rejected files and report persistence outcomes

**Owns:** R1/R2; prerequisite for U1. **Files:** Flappy `Records.java`/`FlappyScene.java`
and tutorial part 5; Sitar `model/CareerJournal.java`, `model/PlayerProfile.java`,
`controls/ControlSettings.java`, `SitarScene.java`; Three Islands `Game.java` and
save notices in `screen/FieldScreen.java`/`MenuScreen.java`; Slay `scene/Shell.java`/
`Profile.java`; Hello `HelloScene.java`; Tower `TowerDefenseScene.java`; new Act
Practice `PracticeHost.java` and engine `TestActPracticeExample.java`.

- [x] Add `futureFileSurvivesOpenExitAndFinishedRun`,
  `corruptPrimaryRecoverySurvivesFailedPrimaryWrite`, and
  `failedRecordWriteRetriesWithoutCountingTheCampaignTwice` regressions in the
  corresponding source/scene tests. Inject successful, false-once, false-always and
  throwing storage outcomes; test primary failure, backup failure and delete failure.
- [x] Return explicit local load disposition (absent, valid, recovered, incompatible,
  corrupt) and save eligibility. Preserve the original bytes after rejected input;
  record format headers before accepting settings. A deliberate New Game replacement
  must follow the mod's existing overwrite interaction, not occur during entry/exit.
- [x] In Three Islands retain the last validated generation and never rotate arbitrary
  corrupt primary bytes over it. In each scene separate in-memory progress, counted
  outcomes and dirty persistence state; propagate boolean results and retry without
  duplicate rewards. Show success only after publication; use one bounded failure notice.
  Apply the same checked best-time/dirty-write treatment to Act Practice; a failed write
  must not be hidden behind an updated in-memory best. Storage fakes implement the
  current text and binary methods even where these consumer cases use only text.
- [x] Extend the actual scene lifecycle checks, including future file → entry → exit
  with no play, finish/retry, current/legacy data, and unknown fields. Update Hello and
  tutorial save examples so authors learn checked outcomes. Preserve room-entry saves,
  profile durability and Starfall's existing defensive generation behavior.

**Verify:** queued `-Dmse=off "-Dtest=TestModStorage,TestFlappyTailsExample,TestFlappyTailsScene,TestFlappyTailsTutorial,TestSitarHeroCareer,TestSitarHeroControls,TestSlayTheRobotnikExample,TestSlayTheRobotnikScene,TestThreeIslandsExample,TestThreeIslandsScene,TestHelloSceneExample,TestRobotnikTowerDefenseExample,TestRobotnikTowerDefenseScene,TestActPracticeExample" test`
with required ROM properties. Run these as smaller affected subsets during iteration;
the combined selector defines the full P01 consumer coverage.

**Done:** rejected files remain byte-identical; false writes are observable and retried;
each saved/recovered generation remains readable. No generic helper is required to land
this repair.

### P02. Validate Eggman's Sky before allocating or publishing decoded state

**Owns:** R1 decoder portion. **Files:** `eggsky/game/Player.java`, `Inventory.java`,
`Catalog.java`, `eggsky/Game.java`, `SkyScene.java`; tests in
`src/test/java/com/openggf/mods/code/TestEggmansSkyQualityOfLife.java` and
`TestEggmansSkyScene.java`. Sequence after the Eggman's Sky portion of P01.

- [x] Convert the audit's prose/future/unchecked-capacity probes into Jupiter regressions:
  reject `version=999`, reject arbitrary prose, and reject `bonusSlots=1000000` before
  calling `cargo.resize`; assert active Player and original stored bytes stay unchanged.
- [x] Decode into a candidate with a checked result; validate supported headers, required
  identity fields, duplicates, finite coordinates and integer arithmetic before allocation.
  Derive technology limits from actual catalogue definitions and earned slot mechanics;
  cite those owners rather than inventing a fitted capacity threshold.
- [x] Publish the candidate only after complete validation. Offer Continue only for a
  usable/recoverable save; preserve incompatible input during scene exit. Keep explicitly
  supported legacy omissions and test them as named fixtures.
- [x] Add negative/extreme counts, capacity overflow, invalid technology, partial input,
  oversized storage, valid inventory and current encode/decode round trips; update save
  compatibility notes and the P01 failure notice.

**Verify:** queued `-Dmse=off "-Dtest=TestEggmansSkyQualityOfLife,TestEggmansSkyScene,TestModStorage" test`.
**Done:** bad input neither drives allocation nor mutates live state nor overwrites its
source; valid and supported legacy expeditions retain their inventory/progress.

### P03. Give Starfall one authoritative camp/beacon location

**Owns:** R3. **Files:** `starfall/World.java`, `FrontierView.java`, and camp/map/recall
consumers; `examples/starfall-frontier/src/test/java/starfall/WorldTest.java`,
`WorldSizeTest.java`, `WorldSavesTest.java`; engine `TestStarfallFrontierScene.java`.

- [x] Add generated-world regression `beaconCompletesAtGeneratedCamp` for SMALL/MEDIUM/LARGE
  and supported legacy geometry: establish completed wardens/core inventory, then use
  actual camp interaction. Assert legacy row 27 cannot finish a new expanded world.
- [x] Add world-owned `campTileX()`/`campTileY()` accessors and use them in generation,
  rendering/map markers, recall and interaction. Derive Y from each world's surface;
  retain the existing interaction tolerance and consumed-item/one-shot victory policy.
- [x] Update the old progression test to generate current geometry, then test save/load
  and capture/restore/forward replay through the final interaction.
- [x] Exercise one real scene input interaction at the visible camp; update coverage
  obligations. Keep this location a Starfall rule rather than adding an engine landmark API.

**Verify:** queued `-Dmse=off "-Dtest=TestStarfallFrontierExample,TestStarfallFrontierScene" test`.
**Done:** all world sizes finish once at their visible camp and maintain the same result
after restoration.

### P04. Prepare Slay target geometry independently of rendering

**Owns:** R4; prerequisite for U4. **Files:** Slay `scene/CombatView.java`,
`EnemyVisuals.java`, `LevelStages.java`; create mod-local `scene/CombatLayout.java`;
engine `TestSlayTheRobotnikScene.java` and source scene tests.

- [x] Add `targetedActionsAreIndependentOfDrawCadence`: identical card and targeted-
  monitor inputs with zero draws, every-tick draws, every-fifth-tick draws and duplicate
  draws. Compare enemy HP, energy, chosen target, pending choices, run/save state and faults.
- [x] Split stage selection/placement from `LevelStages.draw`; split EnemyVisuals pose/
  part preparation and bounds from canvas emission. Store one immutable CombatLayout
  per relevant logic update containing stage placement and each active target's bounds.
- [x] Make hotspot/target/effect placement consume that layout; make drawing consume
  prepared poses without clearing/populating interaction state. Define missing-art
  placeholder bounds before drawing and recompute after spawn/death/size changes.
- [x] Verify origin/flip/scale/shake conventions and representative composite-boss pixels.
  Update Slay's tour to describe the corrected behavior; keep card rules and native
  choreography local. Do not replace boss tables or introduce U4 before this regression passes.

**Verify:** queued `-Dmse=off "-Dtest=TestSlayTheRobotnikExample,TestSlayTheRobotnikScene" test`.
**Done:** no gameplay or target authority is produced by draw; sparse/duplicate drawing
has equivalent outcomes.

### P05. Reproduce and make Survivors settlement idempotent across rewind

**Owns:** R5. **Files:** Survivors `Stage.java`, `RunState.java`, `Profile.java`,
`SurvivorsMod.java`; `src/test/java/com/openggf/mods/code/TestSonicSurvivors.java`.

- [x] Extend the real death-banking regression: capture active gameplay before death,
  earn a known reward, bank, restore through the production session registry, die/bank
  again, and compare bank, kills, elites, species totals and wins. Add retire/victory,
  restored run-start, failed persistence and distinct-new-run variants.
- [x] If it reproduces, introduce a semantic captured run identity and non-rewound
  settlement record in the existing service bundle. Credit **once per run**; replay of
  the same settled run cannot add awards or run counts. A deliberate fresh-run command
  creates a new identity; restoration/replay/level reload does not. Retain prior records
  needed by seeks within the current history epoch, and bound/reset them only at a real
  history/session boundary that makes old settlements unreachable.
- [x] Apply the award and its settlement status together in the profile's persisted
  representation. Retry failed writes without applying the award twice. Keep profile
  outside rollback and run simulation captured; do not solve it by disabling rewind or
  rolling back all lifetime records. Preserve old profile loading with an explicit migration.
- [x] Verify complete registry restoration, Continue/load and session disposal, including
  repeated failure and new runs. If the initial test disproves the risk, document the
  existing boundary and retain the regression instead of adding an unnecessary ledger.

**Verify:** queued `-Dmse=off "-Dtest=TestSonicSurvivors,TestInfiniteSonic" test` with S2/S1
ROM properties. Infinite protects its existing run-ID/anti-duplicate behavior as a comparison.
**Done:** production evidence establishes the policy and the same settlement cannot
change persistent totals twice. Keep the game-specific economy local.

### P06. Correct practice eligibility and configured calibration edges

**Owns:** R8. **Files:** Flappy `FlappyScene.java`, `Run.java`, `Records.java`;
Sitar `SitarScene.java`, `controls/MappedControls.java`, `ControlSettings.java`;
`src/main/java/com/openggf/control/ActionReducer.java` as an existing reference, not
an automatic edit. Coordinate with P01's source owners.

- [x] Add Flappy menu tests for practice at zone zero and every unlocked zone, retry,
  and normal Play at zero. Practice increases totals but never best scores or medals.
  Pass `practice=true` from the Practice command independently of its starting offset.
- [x] Add Sitar calibration tests for a remapped key, alternate pad button, alternate
  device and supported signed-axis/trigger binding. Feed timestamped physical events,
  including rest/repeat/held/release, and assert one calibration tap per configured rising edge.
- [x] Evaluate calibration through the same configured binding/reducer semantics as the
  kick action, retaining original physical timestamps and sample-clock conversion.
  Reset edge state on calibration entry/rebind/disconnect; remove the BUTTON_A special case.
  Do not replace timestamped judgement with tick-time sampling.
- [x] Verify menu transitions with held buttons, unsupported calibration bindings with
  a visible restriction if any remains, and both profiles/devices. Update practice/
  calibration wording to match the exercised contract.

**Verify:** queued `-Dmse=off "-Dtest=TestFlappyTailsExample,TestFlappyTailsScene,TestSitarHeroControls,TestSitarHeroModel" test`.
**Done:** menu policy and authored bindings, rather than coordinates or fixed buttons,
determine eligibility and calibration.

## Wave 2 — lifecycle, adoption and author tooling

### P07. Scope Eggman's Sky caches to generation identity

**Owns:** R7. **Files:** `eggsky/Game.java`, `world/Planet.java`, `space/SpaceMode.java`,
`art/Art.java`, surface recolour keys; `TestEggmansSkyScene.java`/
`TestEggmansSkyQualityOfLife.java`. Depends on P02's validated load identity.

- [x] Reproduce new expedition/galaxy and Continue in one retained scene at the same
  system/planet coordinate with different seeds; compare planet specs and derived
  texture/recolour data to fresh-scene results.
- [x] Introduce immutable generation keys including seed, galaxy identity and any
  algorithm schema that changes output. Clear or replace generation-scoped planet,
  orbital texture and transformed-image caches on validated load/new expedition.
- [x] Bound procedural/recolour caches by owning planet/surface lifetime or an explicit
  capacity; remove lookup references without invalidating currently displayed images.
  Keep host-owned texture disposal separate from mod-owned CPU image references.
- [x] Test long exploration/re-entry, correct per-generation data, retained entry count,
  active-image validity and failed-load non-mutation. Report memory measurements only
  when actually collected; keep the cache repair mod-local.

**Verify:** queued `-Dmse=off "-Dtest=TestEggmansSkyScene,TestEggmansSkyQualityOfLife,TestEggmansSkyFauna" test`.

### P08. Prepare immutable definitions once per owner

**Owns:** R11. **Files:** Infinite `CourseSpecies.java`/`CourseBadnik.java`;
Survivors `Species.java`/`Enemy.java`/`Stages.java`/`Stage.java`; Tower `core/Catalog.java`/
`core/Battlefield.java`/`ui/BattleView.java`; Flappy `Zone.java`/`Run.java`; Sitar
`model/SongCatalog.java`/`SongSpec.java`/`SitarScreens.java`.

- [x] Pin seeded encounter/content results and array ownership, then measure warmed
  repeated trait/lineup/wave/tour/song lookups. Use construction/lookup counts or a
  bounded allocation diagnostic; avoid brittle wall-clock assertions.
- [x] Build immutable tables/indexes in per-module or per-scene services and pass them
  to consumers. Prepare Tower previews and act lineups once; cache species traits for
  an entity identity. Preserve bounded lifecycle and no mutable static authority.
- [x] Keep derived caches recreation-safe and separate from captured gameplay state;
  protect frame arrays from consumer mutation. Verify seeded RNG draw counts/order,
  reload isolation and two concurrent module instances.
- [x] Remove only measured repeated filtering/traversal allocations. Design an engine
  visitor/index only if an existing query cannot meet measured demand; do not introduce
  a generic catalogue API for ordinary Java instance tables.

**Verify:** queued affected subsets of
`-Dtest=TestInfiniteSonic,TestSonicSurvivors,TestRobotnikTowerDefenseExample,TestFlappyTailsExample,TestSitarHeroModel,TestSitarHeroCharts`.
**Done:** warmed definition lookup reuses preparation; gameplay results and isolation
are unchanged. Any performance claim has matched before/after evidence.

### P09. Adopt existing storage/helpers and account for internal dependencies

**Owns:** R9 adoption; part of U5/U6. **Files:** Infinite `InfiniteSonicMod.java`,
`Leaderboard.java`, `InfiniteLevel.java`, `CourseZones.java`; Infinite/Survivors
`build.py` plus `examples/build_example.py`; per-mod art/control/UI wrappers and READMEs.

- [x] Package each example and collect **actual** validator warning identities by mod,
  referenced class and call purpose. Categorize each as an existing alternative,
  narrow missing semantic service, or temporarily retained expert bridge. Record this
  ledger in the dated audit; do not treat the 31 import sites as a warning count.
- [x] Change Leaderboard to retain `ModContext.storage()` and use bounded read/checked
  writes. Preserve top-ten order/ties/run-ID replacement and eligible legacy reads;
  test malformed/oversized data, false writes and concurrent module instances.
- [x] Replace redundant Infinite forwarding with `DelegatingLevel`/
  `DelegatingZoneRegistry`; migrate exact existing art recipes, anchors, palette,
  settings/action and text helpers where equivalent. Explicitly retain RNG streams,
  raw animation dialects, inspectable disabled controls and authored visual styling.
- [x] Consolidate duplicate Infinite/Survivors builders on the shared launcher while
  preserving their documented jar output paths and run behavior. Avoid direct player
  modstate manipulation in new launch flows.
- [x] Update READMEs with the final current residual
  warning inventory and matching-build guidance.

**Execution:** shared builders, bounded owned leaderboard and equivalent helper
adoptions are implemented. Completed clean `175531f6d` compiled/packaged all 29 sources;
26 are strict-clean, while Infinite/Golf/Survivors retain 24/8/13 advisory references
(45 total). The actual call/purpose/owner/reopening ledger and README consolidation
are complete; README commit `54e771314` retains the checkpoint attribution and
matching-build guidance. The historical 28-package ledger is preserved as dated evidence.

**Verify:** queued `-Dmse=off "-Dtest=TestInfiniteSonic,TestSonicSurvivors,TestModStorage,TestModValidator" test`,
portable launcher tests, and SDK packaging/validation of each changed example.
**Done:** existing APIs replace their duplicate mechanics; every residual internal
dependency has an owner, reason and follow-up task. Do not annotate internal packages
or weaken validation just to erase warnings.

### P10. Export runnable creator tests/tools and test-ready starters

**Owns:** R10/U6. **Files:** `tools/modding/build_creator_kit.py`, `build_project.py`,
`test_creator_kit.py`; SDK `ProjectScaffolder.java`/`StarterProjects.java` and templates;
`docs/modding/testing.md` and exported/generated project instructions.

- [x] Add Python/export assertions for every authored test tree and required source
  generator, including Three Islands maps. Classify the 60 source test Java files:
  export portable source/tests/helpers; adapt checkout-only tests to the distributed
  testkit or clearly retain them as repository-only with a portable replacement.
- [x] Emit a test profile for each portable fork and starter with pinned existing
  Jupiter/Surefire and matching engine/SDK/testkit identity. Include a minimal packed-
  mod integration test in generated projects; authors should not copy the profile manually.
- [x] Verify the actual unpacked kit in an external task directory with no checkout:
  run Hello, a pure-model fork (Tower or Flappy), Three Islands generator, and an
  appropriate ROM-backed fork with explicit user ROM paths. Regeneration must reproduce
  authored inputs; no source checkout paths, ROM files or player state enter the kit.
- [x] Test green, deliberate failure and zero discovery via Jupiter; mismatched artifact
  identities must reject before launch. Inspect mod jars for absence of testkit/JUnit/
  test classes and inspect source ZIPs for absence of targets, saves and generated caches.
- [x] Preserve launcher output/argument compatibility and update testing/setup guidance.
  Packaging verifies structure, not gameplay; record the actual fork tests/ROM skips.

**Execution:** portable authored tests/resources/generators, seven starter profiles,
matching-identity checks and launcher contracts are implemented. Actual provisional
external fork/starter/generator checks are recorded; Starpost at clean `175531f6d`
passes 231/231 and strict packaging. That producer's music creator-profile failure
exposed an absent-plan template bug: `545d045f6` repairs it with 23/23 focused passes,
zero skips. Fresh seven-starter plus Hello Maven profiles at clean `54e771314` pass
10/10, zero skips, with main/test artifact separation checked. Portable failure,
empty-discovery, identity and generator controls retain their recorded `175531f6d`
identity; the unchanged launcher/testkit classes and all 83 unchanged scaffold files
are explicitly compared rather than relabelled as new executions. Source generators,
portable model forks and canonical-ROM Starpost checks remain producer-attributed.

**Verify:** `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/modding -p test_creator_kit.py`,
queued `-Dmse=off "-Dtest=TestCreatorTestLauncher,TestModTestKit,TestModSdkArtifactVerifier,TestModApiSdkPackager" test`,
and the real exported consumer commands. Build/SDK-template changes also receive normal
change-based delivery validation. **Done:** portable tests and generators are present,
discoverable and executable, while distributed main jars contain only main content.

### P11. Extend frame control to preserve ordinary native input

**Owns:** R6/U2. **Files:** `game/mode/GameplayFrameController.java`,
`ControlledFrameRuntime.java`, its owner-bound adapters and all stepping consumers;
Survivors `Stage.java`/`RunState.java`/service registration; Golf `GolfMode.java`;
Infinite controller phases. Depends on P05 for Survivors settlement policy. Also inspect
`HostedRunController.java`/`SceneRunLauncher.java` and the current GameLoop host-admission
path: hosted stock runs already hold native-input rows, but exclude these gameplay mods.

- [x] Reproduce Speed Shoes consumption during a long Survivors menu through the
  real loop. Pin Golf's current neutral-step physics/input and ordinary stock movement
  before introducing the extension; retain current input/movie/P2/filter ownership.
- [x] Add proposed `@ModApi GameplayFrameDisposition` values
  `ADVANCE_NATIVE_INPUT`, `ADVANCE_CONTROLLED`, `HOLD`. Add
  `GameplayFrameController.beforeFrame(CourseControl, LogicalInputSnapshot)` with a
  default mapping of existing `beforeTick()==true` to `ADVANCE_CONTROLLED`, false to
  `HOLD`. Runtime invokes exactly one decision per row; `afterTick(..., advanced)`
  retains its existing meaning. Existing controllers therefore keep neutral stepping.
- [x] In the one production runtime, preserve supplied native logical input for native
  advance, retain current neutralization for controlled advance, and gate world work
  for HOLD. Bind the new callback to its verified owner. Retain documented title-entry
  and music-publication work; represent audio/presentation policy separately from physics.
- [x] Introduce a captured Survivors mode-state/coordinator shared through its service
  bundle. Move menu entry/release/confirmation ahead of world stepping, then remove
  per-clock restoration patches after the regression covers all clock owners. Objects
  read the same pause state; do not add another simulation loop or stale Stage reference.
- [x] Give Infinite an explicit phase disposition table: partial ready/continue holds
  that intentionally leave enemies/animation live stay advancing. Migrate only true
  world freezes. Confirm Golf's existing neutral policy and stock unmodified path remain intact.
  Reuse the hosted-run admission implementation where equivalent; test that a row decision
  is made once, stock-run content remains mod-free, scene visits/handles are revoked on exit,
  and new module dispositions do not bypass existing run policy or callback attribution.
- [x] Cover shoes/invincibility/protection/RNG/object lifetime/touch/physics, airborne/
  hurt/rolling/platform state, menu edges, held-button release, entry transitions,
  rewind/capture/forward replay and supported characters/viewports. Update runtime
  contract, signature pins and affected route matrices.

**Verify:** queued `-Dmse=off "-Dtest=TestCourseControl,TestPuttPuttParadise,TestSonicSurvivors,TestInfiniteSonic,TestLevelFrameHardwareTimingBoundaries,TestHostedRunController,TestSceneRunLauncher,TestModSceneHostGameplay,TestActPracticeExample" test`
plus the API gate below. This timing/public-contract change requires normal change-
based validation and affected reference/trace fixtures. **Done:** native advance is
behavior-equivalent to ordinary input; HOLD freezes the declared world; Golf and
stock frame/timing behavior retain their contracts.

## Wave 3 — shared API extraction from repaired consumers

Run the common API gate for P11–P16 and any public P17/P18 addition:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  "-Dtest=TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestModApiRuntimePolicy,TestModValidator,TestModContextAndFaultBoundary" test
```

This complements each task's tests and actual SDK-only consumer compilation; it does
not replace broader delivery, owner-callback tests or route/ROM qualification.

### P12. Extract validated save-slot outcomes and recovery

**Owns:** U1; follows P01/P02. **Proposed new API files:**
`mods/state/ModSaveSlot.java`, `ModSaveCodec.java`, `ModSaveLoadResult.java`,
`ModSaveWriteResult.java`. **Tests:** create `mods/state/TestModSaveSlot.java`;
reuse `TestModStorage` and migrated mod scenes.

- [x] Extract the repaired local behavior from Three Islands and Slay profiles,
  defining typed read outcomes and checked writes over an injected `ModStorage` handle.
  Existing binary owner storage is retained for recording/ghost consumers; the first
  slot coordinator operates over the current text contract and does not alter its caps.
  Codecs return version/validation status explicitly; they do not perform I/O or
  derive creator ownership. Corrupt and incompatible are distinct. Unexpected codec
  programming failures propagate through the owner boundary rather than becoming
  silent "invalid save" results.
- [x] Provide `ModSaveSlot<T>.load()` and `save(T)` with primary/optional backup local
  names, creator `ModSaveCodec<T>` and explicit replacement eligibility. Expose recovered
  status and failed publication. Preserve original incompatible bytes; validate a backup
  before promotion. An incompatible primary does not silently fall back to an older
  writable backup; a failed required backup write aborts publication, retaining the
  previous valid generation. Never label an interrupted multi-file sequence crash-atomic.
- [x] Test the full false/exception/partial-rotation matrix, future schemas, bounds and
  foreign names through production storage. Keep a successfully decoded generation
  available after failed primary/backup writes. Model retries without duplicate updates.
- [x] Migrate at least two independent repaired consumers and delete their duplicate
  slot bookkeeping. Keep Starfall's chunked generation protocol specialized until
  quota/transaction needs prove a separate extension. Preserve format/save boundaries.

**Verify:** queued `-Dmse=off "-Dtest=TestModSaveSlot,TestModStorage" test`, common API gate,
and each migrated consumer's P01 selectors. **Done:** one checked helper replaces real
duplicated slot orchestration without forcing a shared game-save schema.

### P13. Expose narrow native terrain/contact/effect capabilities

**Owns:** U3 and part of R9. **Files:** `level/objects/ObjectServices.java`, existing
terrain/destruction/spring owners; new small semantic result/options types adjacent
to their owners; Survivors `Enemy.java`/`Boss.java`/`Pickup.java`/`Shot.java`, Infinite
destruction, standalone `SpringPad.java`/`ZapBug.java`, Golf `objects/GolfUpSpring.java`.

- [x] Pin consumers' actual ROM/game-rule behavior before extraction: live bounded
  floor probes, stock destruction order/animals/points, namespaced hit sound and spring
  launch/contact. Cite owning routines for ROM constants and shipped FixBugs branches.
- [x] Add an injected bounded live-terrain query with explicit collision path/layer,
  distance and surface result. Reuse the native collision owner. Keep Infinite's detached
  terrain survey separate; do not export `ObjectTerrainUtils` wholesale.
- [x] Extend existing `DestructionEffects` with immutable semantic recipe/options access
  and namespaced/semantic sound handling, preserving one-shot/order/slot semantics.
  Add a creator contact/bounce operation against the existing spring/player owner;
  do not duplicate the already-public `setSpringing` method or port the stock spring class.
- [x] Migrate the real consumers and remove their specific internal dependencies.
  Exercise slopes/flips/top solidity/no floor/search bounds, main+sidekick, overlap/
  velocity/shape edges, foreign owner/faults and restoration/recreation.

**Verify:** P09/P11 gameplay selectors plus queued
`-Dmse=off "-Dtest=TestSamplePlatformerIntegration,TestPlayableSubclassRewind,TestNativePositionOps" test`,
common API gate and relevant native collision/destruction regressions selected by the
change planner. **Done:** semantic services replace the targeted workarounds, with
shipped behavior and provenance retained.

### P14. Share pure composite geometry and canvas transforms

**Owns:** U4; follows P04. **Proposed files:**
`mods/scene/art/SpriteComposition.java`, `mods/ui/SceneCanvasTransform.java` and
small immutable geometry values; tests in `mods/scene/art/TestSceneArtHelpers.java`,
`mods/ui/TestCreatorUi.java`, `mods/scene/host/TestRecordingCanvas.java`.

- [x] Extract P04's pure composition preparation into ordered sprite parts with explicit
  origins/offsets/scale/flip and measurable bounds. Share geometry/draw emission only;
  Slay and Three Islands retain their boss recipes, phases and native choreography.
- [x] Provide a per-draw translated/faded canvas view. Specify clear behavior, viewport,
  nested translation, multiplicative alpha/tint, clip scope, image regions, sprites,
  text and default backdrop rendering. Width/height retain the target viewport; clear
  retains full-viewport semantics independent of translation, using a viewport fill
  when faded. Offset ordinary draw/clip coordinates; restore the prior clip scope;
  multiply existing image/color opacity rather than replacing it. Reject invalid/overflowing geometry before partial
  emission; never retain a live canvas beyond its draw callback.
- [x] Compare recorded operations and representative pixels with Slay/Three Islands
  composites and Sitar `Motion.ShiftedCanvas`, Flappy shake offsets and Golf canvas
  offsets. Migrate independent consumers and remove equivalent forwarding arithmetic.
- [x] Run P04 cadence regressions again after the extraction. Check optional missing
  pieces, nonzero origins, mirror axes, scale extremes, clipping and nested alpha.

**Verify:** queued `-Dmse=off "-Dtest=TestSceneArtHelpers,TestCreatorUi,TestRecordingCanvas" test`,
common API gate and migrated scene/presentation tests. **Done:** bounds and drawing
share pure placement values; layer styling and game rules remain local.

### P15. Adopt/extend verified stock recipes and existing level kits

**Owns:** U5; follows P09's ledger. **Files:** `mods/scene/art/StockSceneArt.java`,
`SceneArtCache.java`; `mods/scene/SceneLevelKit.java`, `SceneRomArt.java` and
host `RomSceneArt.java` only where a proven capability is missing; per-mod art tables.

- [x] Inventory exact repeated raw requests by game/revision/art/mapping/DPLC/frame
  count/palette line. Migrate identities already in StockSceneArt first; cache only
  repeated decoding, not every one-time bank. Preserve mod palette and animation choices.
- [x] Add named S1/S2/S3K recipes only for independently reused verified banks. Compare
  every frame/origin/palette to the current raw request on the exact supported ROM SHA-1;
  wrong revision/game must reject clearly. Runtime bytes always come from the supplied ROM.
- [x] Compare Starfall's 16-pixel samples, backdrop variants and first-frame animation
  preparation with `SceneRomArt.levelKit` output. Use equivalent cropping/existing
  capabilities; add bounded block identity/presentation-variant access only for proven gaps.
  Delete the local decoder only after pixel/identity equivalence is established.
- [x] Address Golf/Infinite title-art warnings through detached ROM-art values/resource-
  owner adapters where possible. Keep GPU atlas lifetime host-owned, test title re-entry/
  level transitions/viewport widths, and retain an explicit expert bridge for any capability
  this task does not actually implement.

**Verify:** queued `-Dmse=off "-Dtest=TestStockSceneArtRom,TestSceneArtHelpers,TestModContextRomArt" test`,
affected scene/Golf/Infinite selectors, common API gate and actual pixel captures.
**Done:** duplicate request facts are centralized with ROM evidence; unsupported variants
are explicit. No raw decoder is deleted on resemblance alone.

### P16. Simplify canonical character identity and owned registry assembly

**Owns:** U7; part of R9. **Files:** `AbstractPlayableSprite.java`,
`game/CharacterConstructionScope.java`/`CharacterDefinition.java`,
`mods/code/OwnerAwareCharacterDefinition.java`/`OwnerAwareStandaloneModule.java`,
`game/StandaloneGameSpec.java`; character/standalone/platformer fixtures and SDK templates.

- [x] Add tests exposing canonical identity from the engine's existing factory-bound
  key, including reused sprite classes for distinct owned characters and main/sidekick
  runtime suffixes. Verify native fallbacks and forged/mismatched factory declarations.
- [x] Make the default creator identity read the authoritative bound key, while
  retaining validated expert overrides. Remove `_p\d+` parsing from the three samples.
  Add a protected standard art-render helper only if the repeated forwarding can use it
  without changing native playable draws/sensors or renderer ownership.
- [x] Build a declarative owner-key-to-factory registry from the validated registration
  transaction or a narrow registry builder integrated with StandaloneGameSpec. One
  definition must supply create/known/browse dispatch; preserve custom expert registries
  and callback boundaries, rejecting unknown/foreign keys before publication.
- [x] Migrate maintained samples/starter resources; verify fresh factories, gameplay
  save/respawn/recreation, subclass state, object art and portable generated projects.
  Defer a baked-ring helper until another real consumer demonstrates the same assembly need.

**Verify:** queued `-Dmse=off "-Dtest=TestModCharacterConstructionIdentity,TestOwnedCharacterRegistry,TestOwnerAwareStandaloneModule,TestPhase3SampleCharacterIntegration,TestPhase3StandaloneSampleIntegration,TestSamplePlatformerIntegration,TestStandaloneObjectArtWiring" test`,
common API gate and starter packaging tests. **Done:** authors no longer reconstruct
engine identity syntax or duplicate registry dispatch to use the ordinary supported path.

## Wave 4 — capability decisions and remaining quality work

### P17. Resolve exploratory author-friction candidates with bounded experiments

These are concrete investigation tasks, not a promise to implement every suggested API.
Each ends in an explicit implement/defer verdict in the existing audit plus, for an
accepted contract, a dated design linked to an execution task. A deferral records the
missing evidence and reopening condition; it does not leave an untracked “TBD”.

| Candidate / source owners | Steps and decisive evidence | Initial disposition |
|---|---|---|
| Cancellable art preparation — Eggman's Sky `ui/LoadingMode.java`, Three Islands `screen/LoadingScreen.java`, scene ROM host | Measure expensive preparation steps; prototype host-owned independent ROM handles/work units with immutable detached results. Test cancel/replacement/owner fault/teardown and stale-result rejection; bound progress and memory. Never run current non-thread-safe SceneRomArt on a mod worker. | Design after P07/P15. Implement only if safe cancellation/ownership is proved and the measured loading cost warrants it; existing music preparation is the reference, not a claimed art API. |
| Gameplay-session transport — Golf connection/listener/outbound queues, scene network host, newly exported `net.client.RaceConnection`/`RaceClient` and `net.host.RaceRoomHost` | Inventory the now-public shared client/host/protocol contracts before designing anything new. Compare Golf's reliable-control/latest-view needs and caller/session lifetime with those contracts and SceneNetwork. Probe close during DNS/connect/read/write, backpressure, terminal delivery and reconnect. Keep golf protocol/authority/fingerprints local; race protocol types cannot silently change its rules. | Prefer a protocol-preserving adapter to existing shared primitives. Retain Golf's local implementation where no equivalent contract exists, then design only the measured missing session-lifecycle/queue seam; do not claim shared networking is absent. |
| Auxiliary spawning — Survivors `ArenaObjects.java`, ObjectManager native/auxiliary admission | Characterize native-slot versus auxiliary capacity/scheduling; prototype injected admission with verified owner, probe protection, faults, destruction and reconstruction. Test repeated restore with no duplicate spawn/identity. | Consider a narrow operation for this real consumer; no global object-pool redesign. Export only after service/lifetime/rewind tests pass. |
| ROM title-card presentation — Flappy `TitleCard.java`, Sitar `SitarScreens.java`, Slay `scene/TitleCard.java` | Extract a pure time/hold/release evaluator plus provider metadata for omitted elements. Compare arrival/staggered exit, hurry, replacement name, missing frames and 320/400/528/800 widths in all three scenes. | Strong repeated demand: implement a small helper after P14/P15 if existing providers do not already cover the choreography. Caller owns elapsed time and game flow. |
| Atlas case / inspectable focus — Three Islands `art/Font.java`, Slay hotspots, AtlasFont/FocusRegions | Add an opt-in exact-case glyph policy while preserving existing uppercase defaults and malformed/duplicate limits. Separate focusability/description from activation; identify a second real focus consumer and compare navigation behavior. | Case support is a bounded helper extension when Three Islands migrates. Keep Slay's focus policy local until the second-consumer case is established. |

**Verification:** exact candidate-specific Jupiter tests plus affected scenes, common
API gate for accepted additions, actual cancellation/network/renderer/rewind probes as
specified. Record evidence and explicit limitations; do not call a prototype a shipped
capability. These investigations may proceed separately from the core defect repairs.

**Execution verdict:** title choreography and exact-case atlas lookup are implemented
with real consumer migrations (`505d52bfd`, integrated as `87d990bab`). Presentation
and common API checks passed; the provisional external consumer matrix passed 313
tests without skips. Cancellable art, protocol-neutral Golf transport, auxiliary
spawn convenience and inspectable disabled focus are deferred with measured/source
evidence and explicit reopening conditions in the audit/validation record. The
repeated art-failure ledgers have an explicit deferred pure-status uplift disposition
in the validation record, including an owner and clock/fault/resource-lifetime
reopening requirements. Their local bounded repairs are implemented under P18;
no public preparation/status capability is claimed.
Final clean-kit consumer and combined delivery qualification remain separate gates.

### P18. Improve orchestration and diagnostic clarity where it serves the fixes

**Owns:** remaining code-quality observations. **Files:** Eggman's Sky SurfaceMode/Game/
art facade, Slay CombatView/art facade, Three Islands art facade, Starfall scene flow.

- [x] Extract the responsibilities already made explicit by P02/P04/P07/P11 into
  focused layout, generation/cache, menu/phase and settlement owners. Preserve captured
  state/recreation boundaries and externally tested flow; avoid a wholesale rewrite
  based on file length or a shared combat/mining engine.
- [x] Replace swallowed-and-permanently-cached art failures with bounded status/diagnostic
  entries naming the request/recipe and cause. Missing optional ROM, unsupported art
  and invalid/corrupt request are distinct; real callback faults retain owner attribution.
- [x] Adopt existing named actions/text layout in the scene wrappers where edge/repeat/
  focus/markup behavior matches. Keep intentional custom font/focus/native animation
  behavior until P17 provides an equivalent path.
- [x] Clarify terrain-edit lifetime in Eggman's Sky: either document current scene/planet-
  lifetime carving or implement explicitly requested durable carving as a separate
  save-schema task. The audit found no existing promise justifying an automatic new
  terrain persistence subsystem. Update guides and source comments to the chosen contract.

**Verify:** affected source/scene tests, fault-boundary/optional-ROM cases and capture/
restore paths for moved state. **Done:** fixes have focused owners and useful failure
status; accepted behavior, algorithms and saved streams remain unchanged.

**Scope limit:** these repairs qualify the named scene facades, including Starpost
Fish/Tree/Museum followups and terminal close. Infinite's retained native-title
`Module.titleLevel` still broadly catches runtime failures and caches absence in a
long-lived title owner. Ledger G records the bounded native-title recovery,
ROM/session-generation and owner-fault reopening requirements. Removing this cache
alone would repeat decode through update/draw/backdrop. No complete native-title
recovery or public asynchronous art capability is claimed.

### P19. Verify rebuilt installed artifacts without disturbing player state

**Owns:** obsolete installed `sample-flappy-mod.jar`; final source/artifact qualification.
Reinventory at execution time: new Act Practice and bundled Time Attack artifacts have
independent source identities and installation/bundling rules.

- [x] Build the maintained Native Flappy source using matching final candidate engine/
  SDK artifacts into a dedicated task output; inspect range, identity and validator findings.
  Never patch the old binary manifest to pretend code compatibility.
- [x] Verify a clean isolated runtime with explicit S3K ROM and the normal code-trust/
  title Apply flow. Keep jars, ROMs, saves and modstate from the player's current runtime
  out of generated test directories and creator exports.
- [x] Inventory actual installed hashes/source identities at execution time. Replace
  only the specifically requested obsolete artifact after preserving its rollback copy
  outside `mods/`; unknown installed jars remain untouched. Qualify new-hash trust,
  rescan/Apply and startup in the isolated runtime; preserve player enablement/trust.
- [x] Record final candidate source/artifact identities, residual warnings, exercised
  routes, skips and any pending replacement in the existing audit/validation record.

**Execution:** the maintained SDK build produced a matching historical `175531f6d`
Native Flappy jar, strict validator-clean, with `sample-flappy` version 1.0.0,
range `>=0.7.0 <0.8.0`, base `s3k`. Its SHA-256 is
`03c311c6701058eb69721a9540f2ab327af0573f10e00b6c73203ad92b9e0bfd`.
Actual maintained-binary request 43929 passed 11/11, zero failures/errors/skips,
through isolated canonical-ROM trust/Apply/runtime checks at clean `175531f6d`.
This completes that checkpoint's runtime obligation. A fresh maintained build against
clean `54e771314` produces the identical jar; its actual binary runtime repeat 1077
passes 11/11, zero skips. Only the obsolete installed jar was replaced atomically
after a hash-checked rollback copy outside `mods/`; all 38 other inventoried files
remain byte-identical. Player enablement/trust remains disabled/untrusted. Normal
new-hash trust/rescan/Apply was qualified in the isolated runtime, preserving that
player choice. Installed replacement is observed separately from the build/test.

**Done:** maintained sources build against one candidate identity, generated artifacts
are qualified, and any requested local replacement has an explicit observed outcome.

### P20. Audit the newly landed Act Practice and bundled Time Attack sources

**Owns:** scope delta from upstream `dfa79f3b9`/`e76a380c3`, outside the historical
25-manifest audit. **Files:** `examples/act-practice/`,
`racing/time-attack/src/main/java/openggf/timeattack/` and its `mp`/ghost/UI owners,
their manifests/tests, shared run/storage/network adapters and the linked upstream
Time Attack extraction plan. The standalone operator server is a separate subsystem;
inspect boundary usage without turning this mod-remediation task into a server rewrite.

- [x] Inventory new production areas, manifests, docs, source tests and actual validator
  findings. Record them as a dated scope extension in the existing audit. Distinguish
  strict bundled package validation from live run/network/timing qualification.
- [x] Examine stock-run launch/admission/retry/end, suspended scene resumption, revoked
  visit handles, callback faults, no-mod gameplay isolation, ghost pose comparison-only
  authority and attempts/recordings. Include restored/rewound Act Practice attempts and
  timer/best-score persistence; P01 owns the already-observed ignored write result.
- [x] Examine Time Attack settings/legacy import and binary recording/ghost bounds,
  corrupt/future-file preservation, peer queue/disconnect/teardown, shared-client/host
  usage and mutable-static/asset ownership. Treat existing typed network rejection and
  owner-private binary storage as implemented capabilities, not new uplift proposals.
- [x] Add real regressions only for established failures or missing important boundaries;
  map each new finding to P01/P05/P09/P10/P11/P12/P17 or a separate bounded repair.
  Compare newly repeated author patterns with both these mods and the original consumers
  before changing public APIs. Record explicit evidence-based disposition when no fix is needed.
- [x] Preserve upstream's exact inherited failures/incomplete suite coverage in the
  batch baseline. Run the new targeted boundary checks through the root queue/Jupiter
  configuration, which includes the bundled mod's test sources, and record actual skips.

**Verify:** queued `-Dmse=off "-Dtest=TestActPracticeExample,TestHostedRunController,TestSceneRunLauncher,TestModSceneHostGameplay,TestTimeAttackModPackage,TestTimeAttackRuntime,TestTimeAttackRuntimeMultiplayerSeams,TestTimeAttackSettings,TestAttemptRecordingVault,TestModStorage" test`
with relevant ROMs; use affected shared-network tests for any identified transport change.
**Done:** the current source inventory has no silently excluded new mod, and additional
findings have observed evidence, explicit remediation ownership or a justified disposition.

## Batch handoff and completion criteria

Start implementation with **P01/P02 save safety, P03 camp, P04 targeting and the P05
settlement regression**. P06 is a small independent correctness follow-up. Run P10
tooling alongside disjoint consumer work where useful. P11 is the first cross-cutting
runtime delivery; do not couple all seven API uplifts into one large merge.
Run P20 early alongside disjoint repairs so the newly landed consumers inform shared
contracts before P12–P17. It does not require repeating unaffected original audit work.

For each integrated batch mark only actually achieved checkboxes, record exact source,
commands/failures/skips and migrated consumers, and update the coverage map disposition
in the audit. Task completion includes documentation, verification, main-branch
integration/push and accounted cleanup. New/worsened failures block integration;
pre-existing failures require exact attribution rather than unrelated cleanup.

The audit is remediated when R1–R11 have verified fixes or a disproved-risk regression,
each U1–U7 has a delivered consumer-backed contract or a documented evidence-based
disposition, exploratory candidates have explicit verdicts, and residual internal
dependencies/coverage gaps remain visible. A package pass, model probe, or plan checkbox
alone does not certify full gameplay, graphics, audio, timing or API publication.

## Execution scope update — published Starpost Valley

Published develop `f02bdbfd570276f7377d7055d236f63e34691484` adds the29th
maintained manifest, Starpost Valley, plus scene-to-native-act ownership and music
pacing capabilities. The updated destination supersedes b6d0ff76f for combined
qualification. Preserve the historical audit/28-package ledger as dated evidence.

- [x] P01/P12: reproduce and repair Starpost progress/settings false-write outcomes,
  rejected/future-file preservation and explicit New Game replacement. Retain the
  exact bedtime checkpoint for retry without repeating calendar/RNG/progress.
- [x] P09/P10: record the source/docs/framework inventory and actual package warnings,
  preserve creator generators/tests and qualify the Java25 creator fixture.
- [x] P11: compose controller admission with native-act exit/hold before any world
  decision; test both fades, once-only input/audio/presentation service, exact return
  result/team restoration and gameplay-context retirement.
- [x] P20: preserve fresh Game/save-section construction, same-identity town/activity/
  Ruins restore boundaries and once-only outcome guards; record native-route coverage
  limits rather than reimplementing this newly delivered subsystem.

- [x] P02/P05: reject Capsule counts above each declared bundle target, preserve
  rejected save bytes, make completed delivery a no-op and retain exact-once rewards;
  exercise over-target files and actual menu delivery.

**Observed qualification:** queued Java25 Starpost Example passed its two wrapper
tests and all 231 authored cases; six canonical native classes passed 94/94 with
zero skips. The final external exported project from clean `175531f6d` independently
passes 231/231, zero skips, with strict package zero findings. This does not extend
the native route matrix or certify missing GPU/audio/end-to-end network coverage.

The updated-base full3155-class ordinary/guard run completed: 27,431 ordinary tests,
26 failures, zero errors and 63 skips; fresh guards passed 677/677, zero skips.
Exact baseline assertions and skip reasons are retained in the validation record.
Clean `175531f6d` completed the candidate union: 27,627 ordinary checks, 31 failures,
zero errors and 64 skips; guards ran 677 with three failures and zero errors/skips.
The 26 baseline identities recur; the SSZ blob-hash difference has bounded semantic
attribution. Five new ordinary and three guard failures received recorded repairs
and focused qualification. The 120-minute execution and 10-minute idle limits were
unchanged. The fresh `54e771314` artifact/starter/binary gates and observed local
replacement are complete. Main integration, its required repaired-source comparison,
push and accounted cleanup remain open. No full-green or delivered claim follows
from these focused results.
