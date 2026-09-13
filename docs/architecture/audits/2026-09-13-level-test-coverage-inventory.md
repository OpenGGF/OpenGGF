# Implemented-zone coverage inventory and work estimate

Source baseline: develop `1d441723c11ec8790bc928dbe37e4c723b46d472`, 2026-09-13.
Assessment: read-only source/document audit, including independent S1/S2 reviews;
no Maven, trace, native capture or mutation run was performed for this inventory.

The [standard](../../guide/contributing/level-test-standard.md) is the target.
The [backlog](../../status/level-test-coverage.md) records implementation progress.
This is a planning inventory with representative assertion/provider inspection,
not the completed per-act matrix audit and not current pass certification. Names
and trace fixtures were used to find evidence; counts of matching files were not
used as coverage percentages. No zone is certified against the whole new standard.

## Which zones count as completed?

There is no current authoritative per-zone completion certification table. The
[game-status guide](../../guide/playing/game-status.md) describes S1/S2 as playable
from start to finish, but has an older update header. The newer
[0.7 roadmap](../../project/v0.7-roadmap.md) treats S1/S2 routes/endings as delivered
while requiring campaign qualification and S3K closure. Use those as inventory
scope, not proof that every route/configuration works.

- **Primary delivered/playable cohort:** all seven S1 zones (19 gameplay acts), all
  eleven S2 zones (20 acts), and the two MHZ acts for the Sonic/Tails scope covered
  by the [direct completion audit](../research/s3k-zones/mhz-completion-audit.md).
  That is 19 zones / 41 gameplay acts. MHZ's excluded Knuckles route is unresolved,
  not silently unsupported or already covered by that completion judgment.
- **Conditional S3K cohort:** AIZ, HCZ, MGZ, CNZ, ICZ, LBZ and FBZ, two acts each,
  have substantial delivered route/component evidence and should be estimated now.
  They add seven zones / fourteen acts, but are not certified completed campaigns.
  LBZ has a concrete Knuckles boss/exit scenario; FBZ has known route failures.
- **Outside the completed-zone estimate:** S3K SOZ/LRZ/SSZ/DEZ/DDZ and auxiliary
  arenas/scenes, Hidden Palace/sanctuary, competition, bonus/special stages and
  endings. Some contain delivered functionality; exclusion means their complete
  playable-act scope was not established here, not that they have no implementation.
  Resolve these in LTS-02/LTS-08 before assigning an act-compliance estimate.

Do not count S1 SBZ3's underlying LZ ROM act 4 or FZ's SBZ ROM act 3 twice.
`TestSonic1SbzFinalZoneRouting` explicitly covers those canonical mappings. S2 has
one SCZ act despite historical `SCZAct2` names. S3K's 48 raw registry slots are not
48 independent playable acts. The backlog retains all 89 registry descriptors.

## Shared gaps found across the inventory

1. Per-act width/donor lifecycle coverage is not demonstrated by generic configuration
   or post-load tests. Every act needs real entry/reload/reset behavior across the
   required product, with supported character/team cases and interaction-sensitive
   combinations. FBZ's current preflight axis sweeps are useful but do not by
   themselves satisfy that full lifecycle product.
2. Rewind foundations are substantial. Many graph tests reconstruct fresh objects,
   verify non-default fields and relink player/parent identities. They still need
   production before/active/after event, camera, load and interaction spots, plus
   comparison with uninterrupted forward execution. Restoring fields or merely
   continuing without a crash is narrower evidence.
3. Complete-run/level-select trace classes provide input and independent-oracle
   assets, not a current passing complete-route result. Native recording inputs may
   need route adaptation under donated movement or different characters. The planned
   exhaustive lane is an axis sweep, not a full Cartesian traversal explosion.
4. Existing wide tests can use different width sets or direct camera stubs. Concrete
   examples: `TestSonic1FzWidescreenAndTeamSafety` and
   `TestLbzResidualCompatibility#supportedViewportWidthsDoNotChangeWorldSpaceInteractionSemantics`
   exercise 320/352/400/528/800, missing 512/640. Preserve existing 352/528 cases where
   useful and add the new contract; do not replace them blindly. The roadmap still
   calls some widths best-effort/exploratory: test breadth does not silently promote
   those widths to release support. Effective configuration and rendered pixels are
   separate assertions.
5. Scope/oracle mapping, executed case inventories, prerequisite failures and
   per-act evidence ledgers are largely still to be built. Existing shared tests
   must be credited only for act bindings and assertions they actually exercise.

## Per-zone estimate

Ranges are **engineer-days of test engineering**, assuming an experienced project
engineer and an approximately eight-hour working day. They are planning judgment,
not measured task durations, agent wall-time forecasts or delivery commitments.
They include deeper assertion mapping, test/scenario adaptation, configuration
expansion, rewind spots, route-axis wiring, focused validation, bounded fixture
triage, oracle justification and evidence documentation. Basic shared helpers and
existing recording inputs are assumed reusable. Shared tooling is costed once below.

They **exclude production gameplay/parity repairs**, missing route implementation,
new recording/capture campaigns, auxiliary-stage certification and elapsed time
waiting on external validation prerequisites. Qualification can expose blockers:
these ranges buy the test work, not a guarantee that all obligations become green.
Confidence is medium on the broad gap pattern and low-to-medium on per-zone effort;
re-estimate after the first pilots. Lower bounds assume reusable production setups;
upper bounds allow difficult state isolation and divergent route configurations.

### Sonic 1 — delivered/playable cohort

| Zone / acts | Existing evidence inspected | Main work to meet the standard | Days |
| --- | --- | --- | ---: |
| GHZ / 3 | Production placement/patrol/destruction persistence; bridge regressions; boss graph reconstruction. [TestS1Ghz1Headless](../../../src/test/java/com/openggf/tests/TestS1Ghz1Headless.java), [TestS1GhzBossGraphRewind](../../../src/test/java/com/openggf/game/rewind/TestS1GhzBossGraphRewind.java) | Act 2/3 production scenarios, lifecycle/configuration breadth, event/camera replay and combat-to-exit routes. | 8–12 |
| MZ / 3 | Lava/child recreation, contact/culling and local pushblock/glass/stomper mechanics. [TestSonic1LavaWallGraphRewind](../../../src/test/java/com/openggf/game/sonic1/objects/TestSonic1LavaWallGraphRewind.java), [TestSonic1LavaGeyserGraphRewind](../../../src/test/java/com/openggf/game/sonic1/objects/TestSonic1LavaGeyserGraphRewind.java) | Bind mechanics to all acts; donor/team lava/platform cases, world/boss replay, checkpoints and complete routes. | 10–16 |
| SYZ / 3 | Third-follower bumper identity restore; boss block/fragment reconstruction and parent links. [TestSonic1SyzBumperMultiSidekick](../../../src/test/java/com/openggf/game/sonic1/objects/TestSonic1SyzBumperMultiSidekick.java), [TestS1SyzBossBlockGraphRewind](../../../src/test/java/com/openggf/game/rewind/TestS1SyzBossBlockGraphRewind.java) | Production interaction binding, camera/boss phase and killing-hit replay, lifecycle and route axes. | 8–13 |
| LZ / 3 | Per-player slide/tunnel ownership; water-clock/order and LZ3 camera/wrap regressions. [TestSonic1LzMultiPlayerTransport](../../../src/test/java/com/openggf/game/sonic1/events/TestSonic1LzMultiPlayerTransport.java) | Water/camera/world replay, drowning/checkpoint resets, donor/team transport and ascent-boss composition. | 12–20 |
| SLZ / 3 | Seesaw participant authority/identity; spikeball reconstruction, boss links and scan-cache rebuild. [TestSonic1SlzSeesawMultiSidekick](../../../src/test/java/com/openggf/game/sonic1/objects/TestSonic1SlzSeesawMultiSidekick.java), [TestS1SlzBossSpikeballGraphRewind](../../../src/test/java/com/openggf/game/rewind/TestS1SlzBossSpikeballGraphRewind.java) | All-act placement/lifecycle scenarios, event/camera replay, boss phase/defeat and configuration routes. | 9–14 |
| SBZ / 3 | Production SBZ3/FZ ROM-slot/water/start/palette routing; P1 authority and exactly-once transition. [TestSonic1SbzFinalZoneRouting](../../../src/test/java/com/openggf/tests/TestSonic1SbzFinalZoneRouting.java), [TestSonic1Sbz3TransitionAuthority](../../../src/test/java/com/openggf/game/sonic1/events/TestSonic1Sbz3TransitionAuthority.java) | Machinery production integration; terrain/camera replay; SBZ2→SBZ3→FZ timeline boundaries and breadth. | 12–20 |
| FZ / 1 | Boss cylinder/launcher/plasma graph recreation; team containment and selected mocked widths; ROM readiness tests. [TestS1FzBossGraphRewind](../../../src/test/java/com/openggf/game/rewind/TestS1FzBossGraphRewind.java), [TestSonic1FzWidescreenAndTeamSafety](../../../src/test/java/com/openggf/game/sonic1/objects/bosses/TestSonic1FzWidescreenAndTeamSafety.java) | Production combat-to-ending, hit/cleanup replay, configured 512/640 widths and donor/team/reset routes. | 5–9 |

Subtotal: **64–104 engineer-days**.

### Sonic 2 — delivered/playable cohort

| Zone / acts | Existing evidence inspected | Main work to meet the standard | Days |
| --- | --- | --- | ---: |
| EHZ / 2 | Grounded movement, follower recovery, signpost/results; boss cull survival and child cleanup. [TestS2Ehz1Headless](../../../src/test/java/com/openggf/tests/TestS2Ehz1Headless.java), [TestEHZBossPersistence](../../../src/test/java/com/openggf/game/sonic2/objects/bosses/TestEHZBossPersistence.java) | Act 2 scenarios, full boss exit, per-act lifecycle/breadth and production rewind replay. | 5–8 |
| CPZ / 2 | Tube capture/release/handoff/subpixels; water-rise threshold and staircase regressions. [TestCPZSpinTubeObjectInstance](../../../src/test/java/com/openggf/game/sonic2/objects/TestCPZSpinTubeObjectInstance.java), [TestSonic2CPZEvents](../../../src/test/java/com/openggf/game/sonic2/events/TestSonic2CPZEvents.java) | Production water/tube integration, negative thresholds, camera/water replay and boss/lifecycle axes. | 6–10 |
| ARZ / 2 | Authored plane-switch/wall, spring/loop traversal; separate boss palette/readiness and platform recreation. [TestS2Arz1Headless](../../../src/test/java/com/openggf/tests/TestS2Arz1Headless.java) | Underwater/alternate route and Act 2 cases; boss/lifecycle/rewind and water/camera breadth. | 5–9 |
| CNZ / 2 | Ceiling exit, flipper launch, forced-spin passage; boss arena mutation and separate slot-machine pixel checks. [TestS2Cnz1Headless](../../../src/test/java/com/openggf/tests/TestS2Cnz1Headless.java) | Production optional branches, boss mutation replay, Act 2 lifecycle and logical-width/donor routes. | 6–10 |
| HTZ / 2 | Earthquake/scroll, lava hurt and platform-floor release; terrain invalidation and seesaw teams. [TestS2Htz1Headless](../../../src/test/java/com/openggf/tests/TestS2Htz1Headless.java) | Before/active/after earthquake world replay, Act 2 and boss production cases, lifecycle and axes. | 7–12 |
| MCZ / 2 | Live child creation after production services injection; separate platform/vine identity restoration. [TestS2MczRotPformsLifecycle](../../../src/test/java/com/openggf/tests/TestS2MczRotPformsLifecycle.java) | More authored mechanic/culling scenarios, boss phases, production interaction replay and lifecycle/routes. | 5–9 |
| OOZ / 2 | Registry/subtypes, spring compression/release/plane contacts; arena lock, PLC and delayed boss spawn. [TestOOZPlacedObjectGaps](../../../src/test/java/com/openggf/game/sonic2/objects/TestOOZPlacedObjectGaps.java), [TestSonic2OOZEvents](../../../src/test/java/com/openggf/game/sonic2/events/TestSonic2OOZEvents.java) | Production oil/mechanism ownership/replay, complete boss exit, per-act lifecycle/configuration breadth. | 6–10 |
| MTZ / 3 | Seeded threshold negatives, bounds and boss delay; tube/cog/team contracts and boss reconstruction. [TestTodo10_MTZEventSpecs](../../../src/test/java/com/openggf/game/sonic2/TestTodo10_MTZEventSpecs.java), [TestS2MTZBossGraphRewind](../../../src/test/java/com/openggf/game/sonic2/objects/bosses/TestS2MTZBossGraphRewind.java) | Three-act production scenarios/lifecycle/routes; tube/cog/camera forward replay and combat/readiness composition. | 8–14 |
| SCZ / 1 | Real Tornado placement and stable riding; flight-path/camera event contracts. [TestSczSpawnOnTornado](../../../src/test/java/com/openggf/tests/TestSczSpawnOnTornado.java), [TestTodo11_SCZEventSpecs](../../../src/test/java/com/openggf/game/sonic2/TestTodo11_SCZEventSpecs.java) | Ride replay across configurations, death/restart, production SCZ→WFZ and exhaustive route axes. | 4–7 |
| WFZ / 1 | Dual event dispatch, thresholds/scroll, one-shot PLCs/team lock; boss/laser-wall reconstruction. [TestTodo12_WFZEventSpecs](../../../src/test/java/com/openggf/game/sonic2/TestTodo12_WFZEventSpecs.java), [TestS2WfzBossGraphRewind](../../../src/test/java/com/openggf/game/sonic2/objects/bosses/TestS2WfzBossGraphRewind.java) | Production boss→Tornado→DEZ, load/timeline isolation, replay boundaries and configuration routes. | 7–12 |
| DEZ / 1 | Trigger bounds and arena lock; Mecha/Robot/Eggman graphs and ending/background/team contracts. [TestTodo9_DEZEventSpecs](../../../src/test/java/com/openggf/game/sonic2/TestTodo9_DEZEventSpecs.java), [TestS2DeathEggRobotGraphRewind](../../../src/test/java/com/openggf/game/sonic2/objects/bosses/TestS2DeathEggRobotGraphRewind.java) | Two-boss combat, allocation/hit/cleanup replay, final handoff and per-configuration reset/routes. | 6–12 |

Subtotal: **65–113 engineer-days**.

### Sonic 3 & Knuckles — MHZ delivered scope; others conditional

| Zone / acts | Existing evidence inspected | Main work to meet the standard | Days |
| --- | --- | --- | ---: |
| AIZ / 2 | Intro/log collision/reveal/re-entry; prepared fire reload equivalence; boss/child identity reconstruction. [TestS3kAiz1SkipHeadless](../../../src/test/java/com/openggf/tests/TestS3kAiz1SkipHeadless.java), [TestS3kAiz1FireTransitionPreparedReload](../../../src/test/java/com/openggf/level/TestS3kAiz1FireTransitionPreparedReload.java), [TestS3kAizEndBossGraphRewind](../../../src/test/java/com/openggf/game/rewind/TestS3kAizEndBossGraphRewind.java) | Both-act width×donor lifecycle, character paths/intro, event/fire/camera replay and authentic route axes. | 8–14 |
| HCZ / 2 | Transition save, chase collision-camera ordering and native carriers; water/transport and boss graph tests. [TestSonic3kHCZEvents](../../../src/test/java/com/openggf/game/sonic3k/events/TestSonic3kHCZEvents.java), [TestS3kHczEndBossGraphRewind](../../../src/test/java/com/openggf/game/rewind/TestS3kHczEndBossGraphRewind.java) | Water/chase/arena replay across widths/donors/teams; checkpoint/reset and both-act completion routes. | 8–14 |
| MGZ / 2 | Live mid-ride ownership restore/resume; collapse mutation, timing/idempotence and solid reconstruction. [TestS3kMgzTopPlatformLiveRewind](../../../src/test/java/com/openggf/game/rewind/TestS3kMgzTopPlatformLiveRewind.java), [TestSonic3kMgz2CollapseEvents](../../../src/test/java/com/openggf/game/sonic3k/events/TestSonic3kMgz2CollapseEvents.java) | Compare uninterrupted vs replay outcomes; both-act breadth, quake/collapse/load/camera spots and boss route axes. | 7–12 |
| CNZ / 2 | Detailed cannon/cylinder control/radii/standing-release and independent rider state; boss graphs. [TestS3kCnzDirectedTraversalHeadless](../../../src/test/java/com/openggf/tests/TestS3kCnzDirectedTraversalHeadless.java), [TestS3kCnzEndBossGraphRewind](../../../src/test/java/com/openggf/game/rewind/TestS3kCnzEndBossGraphRewind.java) | Configured act-level breadth and checkpoint/lifecycle; event/terrain/boss forward replay and alternate paths. | 7–12 |
| ICZ / 2 | Composite restore of incomplete queues/publication fences without duplication; real ICZ1→2 resource/camera assertions. [TestSonic3kIczRewindRoundTrip](../../../src/test/java/com/openggf/game/sonic3k/events/TestSonic3kIczRewindRoundTrip.java), [TestS3kIczAct1TransitionHeadless](../../../src/test/java/com/openggf/tests/TestS3kIczAct1TransitionHeadless.java) | Expand existing strong load evidence across configurations; snowboard/ice/camera/boss spots and representative routes. | 6–10 |
| LBZ / 2 | Follower capture/release/relink and selected widths; Knuckles boss/capsule/results/floor/carrier production sequence. [TestLbzResidualCompatibility](../../../src/test/java/com/openggf/game/sonic3k/objects/TestLbzResidualCompatibility.java), [TestLbzFinalBoss2ProductionRoute](../../../src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2ProductionRoute.java) | 512/640 configured cases, all-act donor lifecycle, complete character-route composition and camera/load/boss replay. | 8–15 |
| MHZ / 2 | ROM-backed seasons/custom layout/art, camera/event/ship transitions; extensive child graph and door-latch restoration. [TestSonic3kMHZEvents](../../../src/test/java/com/openggf/game/sonic3k/events/TestSonic3kMHZEvents.java), [TestS3kMhzEndBossGraphRewind](../../../src/test/java/com/openggf/game/rewind/TestS3kMhzEndBossGraphRewind.java) | Systematic configuration/lifecycle cases; season/arena/load/camera replay and exhaustive routes; Knuckles scope unresolved. | 6–10 |
| FBZ / 2 | Independent 13 reload preflights/26 local cases and native route; checkpoint teams, boss graphs and explicit 11-route lane. [TestFbzCompatibilityMatrix](../../../src/test/java/com/openggf/tests/TestFbzCompatibilityMatrix.java), [TestFbzCheckpointRoutes](../../../src/test/java/com/openggf/tests/TestFbzCheckpointRoutes.java), [TestFbzBossGraphRewind](../../../src/test/java/com/openggf/game/sonic3k/events/TestFbzBossGraphRewind.java) | Full short width×donor lifecycle product, standard rewind-axis/spot matrix and native-character route mapping; retain known route failures. | 3–6 |

Subtotal: **53–93 engineer-days**.

## Evidence details that change the estimates

- S1 has act trace classes for all nineteen gameplay acts, and
  `TestSonic1PlcProducerOwnerCoverage#eventOwnerSubmitsBossCueAtItsNativeThreshold`
  exercises concrete GHZ3/MZ3/SYZ3/LZ3/SLZ3/SBZ2/FZ owners with independently decoded
  ROM queue descriptors. Its seeded camera/routine and mocked manager are not an
  authentic boss encounter or a replacement for production replay.
- S2 `TestS2PostLoadAssemblyHeadless` covers restoration/spawning using EHZ setup;
  it cannot discharge every act's checkpoint contract. `TestSonic2LevelEventRewindSnapshot`
  restores seeded EHZ/CPZ/HTZ/CNZ/WFZ state but is not trigger-to-cleanup replay.
- MGZ's live top-platform test proves restored rider ownership and resumed coherent
  movement. It does not compare the complete resumed sequence to an uninterrupted
  expected snapshot B. Add that evidence rather than discarding the test.
- ICZ has stronger load rewind coverage than a filename scan suggests:
  `TestSonic3kIczRewindRoundTrip` contains nested composite-registry tests for
  pre-acceptance/incomplete work, terminal fences and successful publication without
  duplication. Reuse this pattern for LOAD/REWIND; do not rebuild it as generic
  serialization-only coverage.
- LBZ's `realCapsuleResultsFloorAndCarrierCompleteTheKnucklesRoute` exercises ordinary
  touch/production owners with local positioning helpers. It is a valuable boss/exit
  integration scenario, not an untouched level-entry-to-completion traversal.
- The [FBZ benchmark](../validation/2026-09-13-fbz-test-lanes.md) is the latest
  attributable execution evidence reused here: 26 extracted cases pass, one ordinary
  native route and all eleven exhaustive routes retain known failures. Older FBZ
  action documents describe a superseded matrix shape. This estimate does not
  convert those failures into passes or assume they are all gameplay defects;
  route-controller/setup defects and ROM parity need separate diagnosis.

## Work model, totals and sequencing

A typical two-act S2 zone illustrates the allocation behind the estimates:

| Activity | Typical days | Whole S2 allocation |
| --- | ---: | ---: |
| Assertion/production/oracle audit and per-act matrices | 0.5–1 | 6–10 |
| Short mechanics/lifecycle scenarios and configuration breadth | 1.5–2.5 | 17–28 |
| Production rewind spots and deterministic forward comparison | 1.5–3 | 17–31 |
| Authentic representative/exhaustive axis routes | 1.5–3 | 18–32 |
| Focused validation, defect sensitivity and evidence | 0.5–1 | 7–12 |
| Total | 5.5–10.5 | 65–113 |

These are allocations of planning ranges, not independently measured estimates.
S2's short-check/audit/replay work accounts for approximately 47–81 days; routes
add 18–32. Existing trace inputs reduce recording work, and shared helpers reduce
wiring, but neither supplies all per-act behavioral assertions or makes a native
input sequence valid under donated movement.

Shared work, charged once: **8–15 days** (case/configuration/fixture support 2–4;
reusable rewind/effect comparisons 1–3; required-to-executed coverage reporting 3–5;
explicit-lane prerequisite/inventory enforcement 2–3). The ranges assume the basic
helpers land before repetitive per-zone migration. Do not add a separate full pilot
cost on top of its zone row; pilot work is part of those estimates.

| Scope | Zone-specific days | With shared work once |
| --- | ---: | ---: |
| S1 + S2 + delivered MHZ scope (19 zones / 41 acts) | 135–227 | **143–242** |
| Additional seven substantial S3K zones (14 acts) | 47–83 | Incremental; shared work already counted |
| All 26 estimated zones / 55 acts | 182–310 | **190–325** |

This is the cost of the ambitious full standard, not just adding a five-value
parameter source. Multiple independent workstreams can reduce calendar duration;
do not divide these sums by an agent count and present that as a promised schedule.
Unknown gameplay/parity repairs remain an unestimated additional workstream.

Recommended first batch: FBZ plus the minimum reusable configuration/rewind helpers
(**approximately 6–11 days**, included in the totals), preserving its red routes.
Measure actual effort and case runtimes, then do AIZ/HCZ and GHZ3/CPZ2 pilots before
committing the whole backlog to a schedule. FBZ is the smallest marginal uplift;
ICZ is a strong LOAD/REWIND template. LZ/SBZ, MTZ and LBZ carry larger risks because
of water/terrain, chained loads, machinery or materially different boss routes.

Update each act matrix after that audit, not only this zone-level summary. No
current conformance percentage is supportable from this inventory. This delivery
advances LTS-02 to an initial source inventory; it does not complete LTS-02 or start
implementing the missing tests.

## Documentation validation

Check source links, per-game zone/act counts, range arithmetic and exact backlog
row mapping. Preserve AGENTS/CLAUDE and skill mirrors. No executable test/runner/
POM/CI changes are made; documentation verification is proportionate and no new
engine-suite pass, trace frontier or runtime benchmark is claimed.

Completed documentation checks: `git diff --check` and a temporary Python verifier
passed 121 local file/heading links, all 89 registry keys, the 55 inventoried / 34
pending split, 26-zone / 55-act arithmetic, per-game estimate subtotals and existing
instruction/skill mirrors. Inspected the change-based plan with
`python3 tools/testing/run_categories.py --base 1d441723c11ec8790bc928dbe37e4c723b46d472`
without `--run`. Markdown-only scope was verified; no engine execution was needed.
The temporary verifier and generated helpers are discarded with the task worktree.
