# Sonic CD: re-host on the shared engine

Date: 2026-11-12. Status: selected course correction. Supersedes the
implementation plan and handovers on `feature/ai-nothing-to-see-here`
(frozen at `8c84fb76a` as a reference; never continue its frame frontier).
The [fourth-game design](https://github.com/OpenGGF/OpenGGF/blob/feature/ai-nothing-to-see-here/docs/architecture/designs/2026-10-23-sonic-cd-fourth-game.md)
still holds; this plan returns to the shape it chose.

This is the only planning document a lane needs. Lanes read this file, their
brief and their disassembly sources. Nothing on the frozen branch is required
reading.

## Why the course changed

Measured on the frozen branch at `8c84fb76a`:

- `game/soniccd` holds 160.9k main and 211.7k test lines after 334 commits.
  Sonic 2, fully playable, is 160.5k main lines. The result was parts of
  Palmtree Panic on fixed input routes and the first seconds of two more zones.
- The module did not use the engine. Its object registry and touch table threw
  "unsupported"; level-event, zone-feature and object-art providers were null.
  A private World, object pool, player and solid owner replaced the shared ones.
- `CdObjectState` modelled the native 0x40-byte slot with field offsets. The
  [reimplementation boundary](../engine-map.md#reimplementation-boundary) rules
  that out, and the design had rejected the capsule shape it became.
- Progress was one frame frontier on one input route, so work could not be
  split between agents.
- No native trace existed. The tests compared the implementation with itself.

Sonic CD's level engine is the Sonic 1 engine with additions. The slot layout
in `src/Level/_Definitions.inc` matches Sonic 1 field for field, and
`ObjSonic_*` in `src/Level/_Objects/Sonic/Main.asm` is the Sonic 1 routine set
plus `TimeWarp`, `ExtCamera`, `Handle3DRamp`, `SpecialChunks`, `ChkFlipper`,
`GetMiniAnim` and `ChkBoredom`. Paths are relative to `docs/scddisasm` at
`1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`.

## Rules for every lane

1. Host on shared owners: `LevelManager`, `ObjectManager`,
   `AbstractObjectInstance`, `AbstractPlayableSprite` and
   `PlayableSpriteMovement`, shared solids, camera, PLC and rewind. A CD
   difference becomes a rule, provider or profile, never a second runtime.
2. No slot images, native addresses, receipts, hash custody or source packets.
   Git and the disc manifest already provide integrity.
3. Every constant and branch cites its label and lines, and models
   `FixBugs = 0`.
4. Done means a native check passed: a trace segment, a native pixel reference
   or a PCM reference. A test against the implementation's own output does not
   count.
5. A lane owns its directory and adds no line to a file another lane owns.
6. One focused test class and one trace segment per change. Smoke runs happen at
   integration only, and the lead owns them.
7. Generate tables from the disc and the disassembly with a script. Do not
   transcribe them by hand.

## Salvage map

Drafted from a name-reference census of the 424 main files on the frozen
branch. K3a confirmed the Keep row by dependency closure and carried it from
`8c84fb76a`; the Mine and Retire rows are still the draft census. Read frozen
files with `git show feature/ai-nothing-to-see-here:<path>`.

| Class | Size | Contents |
| --- | --- | --- |
| Keep (carried) | 60 main files, 34.5k lines; 54 test files, 11.5k lines | `data/cd` disc and ISO layer; `media`, `release`; catalogue and identity types (`CdProgramCatalogue`, `CdNativeFile`, `CdNativeProgram`, `CdCommand`, `CdStageKey`, `CdStagePayload`, `CdZone`, `CdPeriod`, `CdReleaseVariant`, `CdSaveProfile`) plus their dependencies `CdIpxProgress` and `CdAssetBytes`; stage data layouts for R1 (PPZ), R3 (CCZ), R4, R5, R6, R7 and R8 plus `CdSonicVisualAssets`; `CdNemesisAsset` and the Nemesis PLC decoder; `CdFm*`; PCM and stamp-graphics devices and `service/graphics`; `CdWarpAssets`, `CdWarpPalette`, `CdWarpScene`; checkpoint and time-warp save/load records; `CdPlayerCharge`. No shared-engine edit was needed |
| Keep, left behind | 2 files | `CdR32aDataLayout` needs `CdProgrammeReader` and `CdR11bLoadPlan`; `CdWarpPresentation` needs `RetainedPlaneSceneRenderer` and `CdObjectPresentation`. Mine both. The data-layout tests also stay behind: every one reads through `CdProgrammeReader` or terrain test helpers |
| Mine | 296 files, 78.2k lines | Objects (130 files, 89 written against the slot image), player movement, scroll, art residency, terrain. Use as annotated reference for edge cases and citations, then write the shared-engine version |
| Retire | 26 files, 49.7k lines | `CdWorld`, `*LoadingVisit`, `*ObjectPass`, pool, slot state, frame runtime, placement runtime, the 5.5k-line module |

Shared-engine edits on the frozen branch split the same way. Keep the
`GameId`, registry, detector, `GameDataSource`, `SaveManager` and
`HeadlessGameBoot` changes and `SonicCdMediaInspectTool`. Retire
`NativeDynamicObjectPass`, `NativePlayableSlot`, `ControlledVBlankOnlyOwner`
and `RetainedPlaneSceneRenderer`, and the capsule hooks in `LevelRenderer`,
`LevelSpritePresentation`, `SpriteManager`, `RewindRegistry` and
`ControlledFrameRuntime`, unless a hosted consumer needs one.

None of these shared-engine edits was needed to compile the Keep set, so K3a
carried none of them. `GameId`, registry, detector, `GameDataSource`,
`SaveManager`, `HeadlessGameBoot` and `SonicCdMediaInspectTool` move with module
hosting in K3b, under invariant 8 for the `@ModApi` `GameId`.

Carry tests only for kept files. K3a carried 62 `tools/soniccd` files: every
`*_probe.cs` with the runner each needs (`oracle_boot`, `device_observer`,
`contact_reference`, `player_reference`, `service_events`, `pcm_*`,
`stamp_fixture`, `native_owner`), `catalogue.py`, `stage_inventory.py`,
`retail_profiles.py` and 16 test files. The packet, custody, integration,
`java_*`, `junit_*` and `native_operand` tools, the charge, corroborate,
category, enum and R11A research scripts, and both `soniccd-*` skills stay
behind. `docs/scddisasm` is a submodule at the pin above. Convert the kept data
layouts to a generated resource and drop their instruction-operand pins.

## Target shape

- **Module.** `SonicCdGameModule` implements the ordinary `GameModule`
  providers. At K3b it was 109 lines, with 1,230 main lines hosting all 70
  stages; it has grown as the lanes' providers and game services were added.
- **Stages.** Zone is the `CdZone` ordinal. Act is the payload's position in
  its round: 0 to 3 are act 1 Present, Past, Good Future and Bad Future, 4 to 7
  are act 2, and 8 and 9 are act 3 Good and Bad Future. One `CdStageLoader`
  reads terrain, collision, layouts, palettes and placements from the stage
  file, driven by `stage-catalogue.tsv`, which `tools/soniccd/stage_catalogue.py`
  generates.
- **Per-stage engine copies.** Every stage file carries its own copy of the
  level engine. `tools/soniccd/variant_census.py` diffs them; each real
  difference in [engine variants](#engine-variants) becomes one profile entry
  keyed by stage family.
- **Player.** Sonic 1 physics values through the shared movement owner, with
  CD differences as rule entries: timed spin dash and peel-out charge, speed
  panning camera focus without a dead zone, roll-entry minimum speed, air
  control after a rolling jump, and falling walk reset. Lanes skip their own
  skip-policy edits; the lead adds rules at merge, because the rule-count test
  conflicts on every parallel edit.
- **Objects.** One class per object, registered by a per-zone fragment so
  lanes never share a registry file.
- **Time travel.** One campaign owner holds period, generator, projector and
  good-future state. A warp is a level transition carrying the
  `LoadTimeWarpData` payload.
- **Other programs.** See the [program hosting contract](2026-11-12-sonic-cd-programs.md).
  Stages, title, stage select, special stage and endings use their existing
  modes; every other program runs in the existing `OPTIONS` host. A
  `CdSceneCoordinator` requests transitions and `GameLoop` commits them. Each
  program lane owns one directory and implements `CdProgramHost`; the lead
  adds it to `CdProgramHosts` at merge.

## Engine variants

From `tools/soniccd/variant_census.py` over all 70 USA stage programs
(`python3 -I tools/soniccd/variant_census.py <scddisasm> <output-dir>`, about
16 seconds). The disassembly writes the same engine in two source dialects,
`Level/*` and `common/*` with `rN/*`. After normalisation they are identical,
so a dialect is never a behaviour difference. Solid objects, object draw and
animate, despawn, palette load, HUD, results and game over are one
implementation for every stage.

| Area | Stage families | Owner |
| --- | --- | --- |
| Player movement | Wacky Workbench has its own player with pole states; all other zones share one | K3c profile, then the Wacky Workbench zone lane |
| Player block check | Stub in Tidal Tempest and Metallic Madness act 1; separate routines for Collision Chaos, Quartz Quadrant, Stardust Speedway and Metallic Madness acts 2 and 3 | zone lanes, behind one K3c hook |
| Player object collision and hurt | Small state-bit gates in Collision Chaos, Wacky Workbench and Metallic Madness | zone lanes, behind one K3c hook |
| Level init and V-blank | Tidal Tempest and Quartz Quadrant acts 1 and 2 differ from the rest | zone lanes |
| Level events | Metallic Madness and Tidal Tempest act 1 Present bypass the zone dispatch | zone lanes |
| Palette fade | Wacky Workbench and Metallic Madness futures differ | zone lanes |
| Sprite tile priority | Tidal Tempest only, in explosion, flower and powerup | common-object lane |
| Scroll, palette cycles, water currents | Per-act by design | zone lanes, as data |

The census counts 164 object names: 17 common to every zone, and 14, 16, 30,
11, 12, 12 and 19 unique to Palmtree Panic, Collision Chaos, Tidal Tempest,
Quartz Quadrant, Wacky Workbench, Stardust Speedway and Metallic Madness.
About 20 more are shared by subsets of zones. Spawner and time-check
differences between dialects are script output only and need a hand check
before K3c relies on them. Japan and Europe differ from USA in 718 file
deltas, mostly the player, monitor and results code.

## Lanes

Tier S is Opus, Fable or Sol. Tier M is Sonnet-class. Tier C is Haiku-class or a
free model. A tier C or M lane gets one retry, then hands its failing evidence
to tier S.

### Wave 0: keel

| Lane | Tier | Owns | Outcome and check |
| --- | --- | --- | --- |
| K2 Trace recorder | S | TraceChaser CD recorder | v5 fixture of Palmtree Panic 1 Present entered through the retail title route; rows agree with `tools/soniccd/player_reference_probe.cs` |
| K3a Carry-over | S | new branch, kept files | Kept set compiles on `next` with its tests green; salvage map confirmed |
| K3b Hosting | S | module, stage loader, catalogue generator | R11A loads through `LevelManager` and renders against a native screenshot |
| K3c Player | S | CD movement rules; `scd` game id in the v5 validator, conformance pack and Java consumer together | K2 fixture green to the first missing object |
| K0 Program host | S (lead) | native-program mode contract | Contract merged; unblocks the lanes below |

K2 and K3a start together. K3b follows K3a, and K3c follows K3b.

Independent of the keel once K0 lands: title and secrets, special stage and
stamp renderer, Time Attack, D.A. Garden with Visual Mode and FMV, backup RAM,
FM effects, PCM music, CD audio. Each owns one directory under
`game/soniccd`. Special stage starts first because it is the largest unknown.

**Wave 0 gate:** the K2 Palmtree Panic 1 fixture replays green up to the first
unimplemented object. Passed on 12 November, and since the Palmtree Panic
object merge the whole fixture replays with no divergence.

### Wave 1: fan-out

| Lane | Count | Tier | Owns |
| --- | --- | --- | --- |
| Zone data and presentation | 7 | M | `zones/<zone>`: all periods, palettes, cycles, animated tiles, parallax, events |
| Common objects | 3 batches | M | 19 routines under `src/Level/_Objects` |
| Zone objects | rolling batches of 4 to 6 | M, C for scenery | `objects/<zone>`; 164 object names in total |
| Time travel | 1 | S | campaign owner, time posts, warp transition, cutscene |
| Trace supply | 1 | M | more fixtures per zone, act and period |

Keep six to eight lanes active at once. The Maven queue is the physical limit.

All seven zones have presentation merged as of 12 November. What remains
uncertified per zone is in the Status table.

### Wave 2: certification

One worktree per trace frontier using `trace-green-fleet`. Then bosses,
level-test matrices under the
[zone and act testing standard](../../guide/contributing/level-test-standard.md),
a complete-run USA trace, and Japan and Europe.

## Progress measure

`TestSonicCdFixtureFrontier` replays all 70 stage fixtures and reports, per
stage, the first diverging row, field and suspected cause. With
`-Dsoniccd.frontier.out=<file>` it writes the table. The headline number is
rows replayed before the first divergence, summed over the 70 fixtures.
The per-stage cells for the latest row (`6c5c0e2764`) are in the
[stage matrix](../validation/levels/scd-stages.md); update both together.

| Integration SHA | Rows green of 57,049 | Fully green stages | Causes of first divergence |
| --- | --- | --- | --- |
| PX merge, 12 Nov | 30,317 (53%) | 4 | 44 unhosted object, 20 unknown, 2 water |
| Five zone lanes merged, `fa3d425900` | 30,317 (53%) | 4 | unchanged, as expected for presentation work |
| Palmtree Panic objects A merged | 30,856 (54%) | 4 | 43 unhosted object, 21 unknown, 2 water |
| FRU merged | 33,553 (59%) | 6 | 53 unhosted object, 6 unknown, 3 zone player hook, 2 water |
| Wacky Workbench player merged | 34,283 (60%) | 6 | 56 unhosted object, 6 unknown, 2 water |
| Time travel merged | 34,287 (60%) | 6 | 57 unhosted object, 5 unknown, 2 water |
| Common objects 2 merged, `6ff040c28d` | 37,280 (65%) | 13 | 48 unhosted object, 7 unknown, 2 water |
| Collision Chaos and Tidal Tempest objects merged, `dfb7045ee2` | 37,931 (66%) | 14 | 46 unhosted object, 8 unknown, 2 water |
| All object lanes but Wacky Workbench merged, `8514ad3372` | 47,101 (83%) | 29 | 25 unhosted object, 14 unknown, 2 water |
| Wacky Workbench objects merged | 48,228 (85%) | 30 | 23 unhosted object, 15 unknown, 2 water |
| Guards 2, second-pass objects, flow | 49,493 (87%) | 33 | 18 unhosted object, 17 unknown, 2 water |
| Engine findings, bosses, Wacky Workbench 2, `e29dd891ba` | 54,166 (95%) | 54 | 12 unhosted object, 2 unknown, 2 water |
| Boss part 2, Collision Chaos objects 2, `c681f7344a` | 54,269 (95%) | 54 | 12 unhosted object, 2 unknown, 2 water |
| Metallic Madness objects 2, endings, presentation timing, `85576f5edf` | 54,269 (95%) | 54 | 12 unhosted object, 2 unknown, 2 water |
| Six bosses, zone player parts 1 and 2, picture pairing, `2bbfb08b81` | 54,617 (96%) | 57 | 11 unhosted object (label is the test's guess), 2 water |
| All seven bosses, `921c4a085c` | 54,617 (96%) | 57 | 11 unhosted object (label is the test's guess), 2 water |
| **Full-act routes** (28 stages, start to signpost; a different measure, see Status), `0b201e98e8` | 50,047 of 149,633 (33.4%) | 0 of 28 | first divergences: mostly objects, then terrain and player rules |
| Full-act routes after the player, capture, routes B, terrain, objects 3 and slot lanes, `872ef088aa` | 98,626 of 149,633 (65.9%) | 9 of 28 | objects not hosted or wrong, water, end-of-act pose. Fixtures on the same build: 56,308 of 57,049 rows, 64 of 70 green |
| Full-act routes after the boss follow-ups, second route passes, lag, act-end, player rules, execution order and terrain 2, `6c86bed2e8` | 132,865 of 149,633 (88.8%) | 21 of 28 | objects (six routes), water state (one). Fixtures on the same build: 56,501 of 57,049 rows, 65 of 70 green |
| Full-act routes after the third route passes, placement, launcher, player 3, session and timing lanes, `65a21fd7a2` | 144,085 of 149,633 (96.3%) | 24 of 28 | R42A (an unhosted wood block), R81A, R81B, R82A (objects). Fixtures on the same build: 56,679 of 57,049 rows, 67 of 70 green |
| Full-act routes after routes C, `4a97d84a6b`; unchanged by the lost V-INT, capture, notice, licence and load-time lanes, `6c5c0e2764` | 146,390 of 149,633 (97.8%) | 26 of 28 | R42A (water, row 4286), R82A (an unhosted object, row 4577). Fixtures on the same build: 56,879 of 57,049 rows, 68 of 70 green (open: R11C row 677, R51B row 549). Both totals count the diverging row as compared, so each open stage is one row high. Per-stage cells: [stage matrix](../validation/levels/scd-stages.md) |

## Native references

`~/Downloads/Sonic-CD/native/rehost-fixtures/<payload>/` holds, for each of
the 70 stage programs, a movie, a v5 trace, an early screenshot with nothing
pressed, an end screenshot and `manifest.json`. `index.json` and `README.md`
there list them. Each movie idles 60 frames after the title-card lock, then
holds Right for 600 frames with a jump every 60. These are a lane's default
native check. They are user-local and never committed; tests read the root
from a system property and skip as an optional input when it is absent.

## Brief template

A brief fits on one page.

```text
Outcome:    <one behaviour, stated as what a player or trace shows>
Owns:       <directory or files; nothing else is edited>
Sources:    <disassembly files and labels; frozen-branch files to mine>
Depends on: <merged contract or lane, with SHA>
Check:      <one test class and one trace segment or native reference>
Stop when:  <check green and PR open, or the check fails twice>
Report:     <SHA, command, counts, skips, limits; ten lines>
```

## Risks

- **Route depth.** Every stage has a native fixture, but each covers only
  about ten seconds from the start position, and six end in an early death
  (R31A, R41A, R51B, R52A, R82A, R82C). Deeper certification needs authored
  routes per stage or one human-recorded complete-run movie.
- **Untested data layouts.** The carried stage layouts lost their tests with
  the retired reader. K3b's all-payload load check is their first cover.
- **Recorder gates.** On the TraceChaser branch the S1 and S2 recorder gates
  fail 4 and 3 tests. The same seven fail on its baseline `161e45a`, so the
  branch regresses nothing, but those gates are red before and after.
- **Special stage.** Resolved: the stamp floor renders in the module and
  equals native (Status: special stage rows).
- **Salvage estimate.** The census counts name references, so the mine class
  may hold more directly reusable code than it shows.
- **Media-gated tests in smoke.** Resolved: the media-gated tests are
  classified in `release-skip-policy.json`. Originally: without `-Dsoniccd.media.root`, the carried
  classes skip 77 tests with no `release-skip-policy.json` rule, and
  `TestCdR4WaterMedia` fails by design. Classify or gate them before the first
  smoke run on this branch.
- **Scroll lock and death.** Resolved by PRES1 (one scroll-lock gate, the
  fitted lock frame removed). Originally: native stops `LevelScroll` while the title card
  holds `scrollLock` (from its slide-out to the end of the PLC wait) and while
  the player is dead. Zone picture tests supply both from the fixture. The
  title-card owner must drive it for real; Palmtree Panic's lock frame 131 is
  fitted, not derived.
- **Plane B window per band.** Palmtree Panic bad futures and any stage with
  band rows far right need a per-band background window in the shared renderer.
- **Rewind.** Zone palette and tile timers are not rewind-registered.
- **Background plane wrap.** Native background rows marked static wrap at 512
  pixels; the engine's wide plane does not. Owner: background drawing, not a
  zone lane.
- **Extra native screenshots.** Zone lanes capture moving-camera screenshots
  into `~/Downloads/Sonic-CD/native/rehost-fixtures-<zone>/`. Their tests skip
  without them.
- **Lag frames.** Native drops frames while pattern load cues decode (13 in
  the first 188 level frames of R11A, up to 60 per fixture in Tidal Tempest).
  The engine does not model them, so anything keyed to a frame clock drifts:
  HUD time digits, the RINGS flash, ring spin frames. Needs the engine's
  existing load-time manifest for this game. Picture tests mask these.
- **Object ownership.** Each object owner (`common`, one per zone, and
  `shared` for routines several zones assemble) has its own catalogue anchor
  module, generated TSV, accessor class and registration fragment. Objects
  resolve by stage: zone fragment, then shared, then common.
- **Program flow gaps.** Resolved by FLOW2 and the audio lane: results, warp
  and game over call `stageEnded`, stage-select program rows return to the
  title, and the title's audio reads the disc. Originally: nothing calls `stageEnded` yet, so stage-to-stage
  progression is unit-tested only; the results, warp and game-over owners must
  call it. A stage-select row that names a program restarts the stage select
  where native returns to the title. The title and stage select hand the
  configured cartridge ROM to audio; the audio lane must fix that.
- **Common player findings from the Wacky Workbench movies.** The first Down
  tap writes no duck (`ObjSonic_MoveGround` `.CheckDownPress`, Level/_Objects/Sonic/Main.asm:1075-1079) but the
  engine ducks; the balance settle subtracts `$D` where the engine subtracts
  `$C`; and the common `HurtPlayer` may clear the charge timer where the
  engine keeps it (unchecked). Owner: next player lane.
- **Special stage and audio follow-ups.** Special stage presentation and
  results; the per-zone PCM driver; wiring special stage entry and exit into
  the program flow; fades on power-up expiry and time warp.
- **Architecture guards are red on integration.** Resolved: the guard profile
  has been green on every merged-tree check since `e29dd891ba`. Originally: `ObjectManager` is 10
  effective lines over its budget, and `SonicCdRules` builds `GameRules`
  positionally outside an owned factory. Run `-Pguards` and fix every
  failure before the PR.
- **Ceiling probe above the level.** After the 3D-ramp launch in Palmtree
  Panic 1, a ceiling probe at y below 0 wraps to layout row 7; native passes
  and the engine collides (row 753 of three routes). Owner: terrain and
  player.
- **Warp cutscene and flow.** Resolved by FLOW2. Originally: the flow must run the warp cutscene and call
  `CdWarpTransition.loadDestination` when `readyWarp()` is set, and call
  `actCompleted` and `gameOver` on the time owner.
- **Remaining objects.** Superseded: the object and boss rows of the Status
  table and the [stage matrix](../validation/levels/scd-stages.md) record what
  remains. Originally: Tidal Tempest: move blocks, Yago, switch elevator,
  prizes, wood and break blocks, break pole, drowning. Metallic Madness:
  Bigbom, Scarab, Twin walker, spike crusher. Shared: projector, Amy Rose,
  tunnel path, tube door, seesaw, swing, spike ball, Dango, Hotaru, Poh-Bee,
  bumper and flipper, other zones' doors and spikes. Unowned: moving spring,
  Collision Chaos animal. All bosses.
- **Engine findings from object lanes.** Anton's floor test loops forever on
  one act 2 route (terrain; now throws). The exact solid routine crushes the
  player on a rising crusher in R82A where native does not. Five Collision
  Chaos stages stop on a ground-angle difference on flat ground. R63C's
  level-frame counter may trail native by one frame. The lamp pole in
  Stardust Speedway act 3 is about 2 px off.
- **Process.** Twelve lanes on one Maven queue left lanes waiting for most of
  their run (up to 2 h 50 min of 3 h 33 min). Keep about six active. Lanes
  must never use `pkill -f`: one cancelled two other lanes' jobs.
- **Load timing.** Partly resolved by the load-time lane (Status table): the
  stage-load and video-read durations come from a measured manifest in normal
  play, and replay still seeds the native count. Other disc reads (program
  loads, seeks after a long head move) are not paced; see Open items.

## Open items

What is still open at `6c5c0e2764`, from the lane reports' stopped and
unverified lists. The order is not a priority order.

**Accuracy frontiers**
- Full-act routes R42A (water: native is underwater at row 4286, the engine is
  not) and R82A (row 4577: the player loses `$600` x speed at a walker
  platform's edge). Fixtures R11C (row 677) and R51B (row 549), both on an
  unhosted object.
- 28 stages have a full-act route; the other 42 have only the 14-second
  fixture, and 14 of those (the act 3 stages) also have boss movies. No
  complete-run recording exists. Three routes (R81B, R81C, R82A) have no
  act-end assertion, and R42A's is `pending`.
- 38 of 70 stages place an object ID the engine does not host (pinned in
  `TestCdUnhostedObjectCensus`; 54 before Robotnik's projector was hosted).
- Robotnik's projector: its picture is compared with native in Palmtree Panic
  only, and its destruction is observed natively there only; the hologram
  animals draw nothing, Tidal Tempest's and Wacky Workbench's hologram animals
  are not hosted, and Collision Chaos's ground hologram animation is
  unverified.
- Lost rings after a hit differ from native in R81A (from row 3343) and R81C
  (from row 3968): Metallic Madness's Poh-Bee (`$2E`) is not hosted, so the
  spilled rings take other slots and their slot-phased floor checks bounce
  them on other rows. Whether rings collected in one time period stay
  collected after a time warp is unmeasured (`ObjRing_Init` reads the earlier
  periods' flags, Ring/Main.asm:96-113; the engine builds a new ring owner per
  load). The spill when the hurting object runs before the spill's owner has
  no native row.
- About 228 places in the disassembly call `DrawObject` just before the
  despawn check, which clears the on-screen bit so the object leaves by range
  alone; most engine objects read the previous frame's bit there and stay
  while on screen. Fixed for Hotaru, the searchlight and the Scarab; a likely
  cause of the remaining "native deletes it, the engine keeps it" slot
  differences (ring slots differ from native's on 13 recordings for this kind
  of reason).
- Tidal Tempest move block: an extra bottom row in the engine's picture; the
  cause is not established.
- Mini Sonic's restore (R82A restoring lasers) was not reached by a native
  recording (two attempts), so the shrunk radii, animation set, landing rise
  and solid box are unverified. The revolve door, shrinking and layer flips
  have little or no native row.

**Programs and presentation not started or not hosted**
- Visual Mode, D.A. Garden, the pencil test, Thank You, Comin' Soon, the
  attract demos (the opening plays; the R11A and special-stage demos are
  skipped), the RAM DATA manager, in-game save erase. Special stage results
  graphics. Start pressed during the opening is unverified.

**Timing, unverified or unmodelled**
- The HUD RINGS flash phase and the engine's HUD time digits have no native
  row. Lost V-INTs on the 140 recordings not re-recorded are unchecked (the
  counter census shows none).
- Load time: checkpoint and warp entries are single samples not run in an
  engine; 12 stages' checkpoint and warp keys may name the wrong queued list
  (R31C, R31D, R32C, R32D, R51A, R51C, R51D, R52A, R52C, R52D, R53C, R53D);
  R12A after an act clear or a special stage is one V-INT off; the video start
  is 2 and 6 frames early from the program's start; program-to-program disc
  loads, title, time attack, special stage and warp loads are unmeasured; real
  disc variance is unmeasured.
- Lag frames and CD audio start latency are unmodelled (see
  [known discrepancies](../../status/known-discrepancies.md#sonic-cd-hardware-timings-not-modelled)).
- Music during pause, real-boot title pixels, a damaged (not missing) disc
  track, a RAM cartridge, picture columns beyond 320 (in the time tunnel at
  400 wide the columns outside its 320 turn red as it fades to white), `--no-music` on a Sonic
  CD capture, a production act change through the game loop against native.

**Licence and history**
- Genesis Plus GX-derived commits remain in this branch's history (listed in
  the Licence row); four S3K timing files outside this branch's scope still
  cite `GPGX 051d430d`; two tracked tool generators insert hooks at short
  anchors in the core's source, which the owner should judge.
- Unsourced chip behaviours: PCM reset pan and unmapped reads, stamp priority
  mode 3 and the V-cell, H-dot and V-dot register widths; the stamp cost
  formula is measured only at widths 256 and 304.
- One old commit (`c49928fcd2`) carries `TODO` trailers and fails the commit
  trailer audit over the full range; every commit after it passes.
- 908 Sonic CD disassembly citations name no checkable file.
- Zone palette and tile timers are not rewind-registered; not assessed.

## Status

| Unit | State | SHA | Check |
| --- | --- | --- | --- |
| Plan and salvage draft | done | this commit | none run |
| K2 | done, except the `scd` game id | TraceChaser `5ce1790` on `feature/ai-scd-recorder` | 1,411-row fixture; 25,401 values equal the independent probe; deliberate break fails with 1,240 mismatches; v5 validator rejects game `scd`; S1/S2/S3K gates not run |
| Variant census | done | `5592e5a4f6` | 70 stages resolved, no unresolved includes; four hand spot checks agree |
| K3a | done | `9f26e1444` | `mvn -Dmse=off compile`; 49 carried classes, 807 tests, 0 failures, 0 skips with media and native fixtures; 229 tool tests, 8 opt-in skips |
| K3b | done | `e2808aa2c3`, merged at `5253829e32` | Lead rerun on the merged tree: 4 tests, 0 failures, 0 skips. R11A idle position and camera equal native for level frames 1 to 157; first-frame picture equals native outside the HUD, two rings, a monitor and a few background columns; all 70 payloads load |
| K3c player and trace replay | done | `0e065d6a0c`, merged at `ab1e9fc769` | R11A trace equals native in every compared field for rows 0 to 482; row 483 diverges at the unhosted 3D ramp. Lane: 36 S1 trace-replay tests and a 578-test S1/S2/S3K focused set, 0 failures. Lead rerun of the CD set on a clean merged build: 17 tests, 0 failures |
| Player extras: 3D ramp, special chunks, balance, boss boundary, fall-speed cap, per-zone player profile | done | `b05872c060`, merged | K2 R11A replay green for rows 0 to 701; row 702 is the unhosted 3D-ramp marker. Lane: 839 S1/S2/S3K and CD tests, 0 failures. Lead rerun on a clean merged build: 32 tests, 0 failures |
| Frontier unknowns | done | `9ac451a7ea`, merged | Three causes fixed from the routines: Wacky Workbench has no speed-panned camera; Quartz Quadrant's conveyor block check; a rolling jump keeps the roll radii. Lane: 549 S1/S2/S3K and CD tests, 0 failures. Handed on: lag-frame sampling (R33C/D, R83D), an unexplained wall push in R41A, Wacky Workbench pole and bounce routines, time warp in R52A |
| Metallic Madness presentation | done | `47c53799f5`, merged | 50 of 51 native shots match; nine stages certified. R83C early is 99.23% (a freed animal, not background). R82A early: one palette script runs one step behind native, unexplained. Boss-door tile gate and boss palette cycle wait for the boss |
| Picture tests share one JVM | done | `2d0b405ddb`, merged | Cause was production: `GraphicsManager.init()` kept two GL ids from the destroyed context. Lead run in one fork: 12 CD picture classes plus GL contract tests, 48 tests, 0 failures. New picture classes need no drain |
| Wacky Workbench player | done | `8136f0cae0`, merged | Floor bounce, sparks, beams, rotating pole, hang bar, and seven smaller differences hosted through the per-zone profile. R62D, R63C and R63D moved 90 to 356 rows later; no stage earlier. Two native movies recorded. Lane: 540 S1/S2/S3K and CD tests, 0 failures. Unverified: hang bar, beams, several gates. Signals other lanes must drive are in `WwzStageSignals` |
| Time travel | done, carry unverified | `2996602692`, merged | One owner (`timetravel/CdTimeTravel`) for period, warp, good-future, generator and projector state, rewind-registered. Time posts, time icon, stars, the warp and the stage-to-stage carry. R52A: touch, timer, trigger and camera lock equal native through row 583, and the saved payload equals native RAM. Carry into a native destination unverified: both recorded Palmtree Panic warps diverge on terrain at row 753 first. Two native warp movies recorded |
| Time Attack and backup RAM | done, RAM DATA screen open | `4ec6f66aee`, merged | Backup RAM bytes equal a native dump (8,192 of 8,192 after boot; 8,176 unmasked after a new game, a record with initials and two deletions). Time Attack menu video memory equals native at six states. Durable saves were not attached at interactive startup when this lane merged; the launcher lane did that later. The RAM DATA manager is a typed not-hosted step. `CdProgramFiber` runs the menu program on a virtual thread with strict hand-off: a design choice awaiting the user's review |
| PCM driver and title audio | done | `c5769a0731`, merged | Driver state equals native for Past music in all seven zones; output correlation 0.47 to 0.79 against controls below 0.12. One PCM effect checked. CD seek latency unmodelled |
| Special stage presentation and flow wiring | done, results open | `1022152ae2`, merged | Every native screenshot of the stage loop equals the engine's in four runs. Results graphics, pause and exit fade not done; a normal special stage, the Time Stone win and stages 2 to 5 and 7 never observed natively; the round trip after results is untested |
| Architecture guards | done at its base; second pass done (see the row below) | `51a6697164`, merged | Eleven branch-caused guard failures fixed with nothing raised. After the object lanes merged the profile has four more kinds of failure; the same lane is fixing them with six cross-lane wiring items |
| Zone objects: Palmtree Panic B, Collision Chaos, Tidal Tempest (20 of 30), Quartz Quadrant, Stardust Speedway, Metallic Madness (17 of 21); nine objects shared by several zones | done, merged at `ed4a96766c` | `2e2bb1333c`, `d208e1bade`, `59be4ee629`, `8645507c0f`, `86a8cd239f`, `9918e318b7`, `b194c1a8bf` | Per-lane reports in `~/Downloads/Sonic-CD/planning/rehost/*-objects-report.md` list each object's native check and the unverified ones. No full run yet on the merged tip |
| Cross-game session isolation | done | `8f8ace8216`, merged at `8514ad3372` | A Sonic 3&K session left the palette upload latch set for the next session of any non-latching game. Fixed at the palette owner's session boundary; regression test boots CD, S3K, CD. Lead run with other-game classes first: 1,254 tests, 3 failures, none palette-related (two Stardust Speedway appearance differences left red for that zone's next pass; one stale uncertified entry fixed) |
| Wacky Workbench objects | done | `ee6203d834`, merged | All twelve objects. Bounce platform and launcher equal native on every traced row and pixel; piston, Semi, statues, snake blocks and beams unverified. The Goddess statue spawns no rings yet. Lead run: all CD tests, 1,239, 2 known failures |
| Guards second pass with six wiring items | done | `631c099a2a`, merged | Guard profile green on its branch; switch flags, spikes per stage, stage-scoped registrations, touch decode, time-warp attack, water surface spawn |
| Engine findings (FRU2) | done | `98cb328c1f`, merged | 16-row collision window, negative floor height, level-frame counter 1 after load, dead-player bottom bound, hosted bottom-boundary level events, single-probe ceiling check, lag rows compared as in the other games. Other games: 878 tests, 0 failures |
| Game flow (FLOW2) | done | `e0f028c7da`, merged | Act clear to next stage, deaths and game over, giant ring to special stage and back, warp cutscene (354 frames) and destination, saves. Six native runs, all comparisons equal; native scenarios were set up with RAM pokes. Durable saves had no caller then; the launcher lane attached them |
| Boss pattern and Palmtree Panic boss (BOSS1) | done | `3571695ce9`, merged at `f8fc7ae479` | Every boss part equals native RAM on every row of the native fights. Arena camera fixed (`CdBossFragment.hostsLevelEvents()`); R13D fight green on all 2,745 rows, R13C to row 2433 (terrain reads the layout's right edge as a wall); bumper, death and act-end movies green; act end loads R31A. Eggman's slot differs (38 against 33): the shared spawner orders by chunk and X, native by file order. Skill `scd-implement-boss` and brief `briefs/zone-boss.md` for the six boss lanes |
| Shared objects 2; Tidal Tempest and Stardust Speedway second pass; Wacky Workbench part 2 | done | `8780d0d97b`, `84fe695302`, `bcb79c6ceb`, merged | Spike ball, swing, Hotaru, Poh-Bee, spike chain, tube door, horizontal door, moving spring; Yago; Goddess statue rings. One Hotaru kept (the shared one) |
| Merged-tree check at `e29dd891ba` | measured | | Guard profile 836 tests, 0 failures. Sonic CD suite 1,362 tests, 12 failures, each owned (boss 4, Collision Chaos objects 5, duplicate Hotaru 1 since fixed, palette first pass 2) |
| Collision Chaos objects 2 | done | `4ba65f351e`, merged at `c681f7344a` | Bumper, flipper object, Amy Rose and heart, animal, door. R31A equals native on 659 object rows. R32A to R32C still stop at the glass shatter `$29`, spawned by the zone's unhosted player block check (`r3/player_block_check.asm:224-259`). Native frees a collected ring's slot one frame before the engine (common objects). Its picture test picks the best of three engine frames by terrain and skips below 50% agreement: to be replaced by a fixed pairing |
| Merged-tree check at `c681f7344a` | measured | | Guard profile 836 tests, 0 failures. Sonic CD suite 1,368 tests, 5 failures, each owned (Collision Chaos glass shatter 3, Wacky Workbench palette first pass 1 and appearance 1) |
| Metallic Madness objects 2 | done | `e82d118861`, merged at `d51bddbf16` | Spike crusher and Scarab equal native (appearances and pictures; one authored movie green on 1,718 rows); Bigbom, twin walker and the Scarab's monitor, grab and kill have no native row. Native draws a spring over a badnik in the same sprite bucket where the engine draws the reverse (shared renderer, open) |
| Endings, video player, opening, sound test and title secrets (END) | done | `04a68944ad`, merged at `df54c7cb19` | Both endings, credits, opening, sound test with six codes, hidden pictures and best staff times equal native tiles, plane cells, scrolls, palette and pictures. Screen state is typed (`fmv/CdProgramScreenState`), with no video-memory image or register words; the streamer keeps the sector rate, bank rule and the game's audio-sync test and models no interrupts or CPU handshake. Drive seek is an unsupported hardware timing (`SEEK_VINTS` 20; results equal for 12 to 46). Start during the opening is unverified. Not started: Visual Mode, D.A. Garden, Thank You, Comin' Soon, attract demos |
| First-iteration presentation timing (PRES1) | done, follow-up done (next row) | `ea94addc61`, merged at `85576f5edf` | `CdFirstMainLoopIteration` runs the load-time scroll, palette and tile calls in native order; zone compensations and the fitted lock frame are removed; one scroll-lock gate for all zones. Wacky Workbench failures gone; Tidal Tempest R41B to R41D end and Metallic Madness R82A early certified. Still uncertified: R63D mid, R12B end (bush band 32 px), R41A end (deformation phase). Follow-up: fixed picture pairing in tests written since |
| Merged-tree check at `85576f5edf` | measured | | Guard profile 836 tests, 0 failures. Sonic CD suite 1,392 tests, 4 failures (Collision Chaos glass shatter 3, Collision Chaos picture pairing 1) |
| Picture pairing follow-up (PRES1 part 2) | done | `2149213b42`, merged at `0509f7e513` | One fixed pairing rule in the Collision Chaos, Metallic Madness and Tidal Tempest object pictures (fixture probe shots pair at lf(row+1)−1; boss probe shots at lf(row)−1). Amy and heart equal native exactly. Twenty Collision Chaos R32 shots uncertified: their native frames were captured one input frame ahead |
| Zone player behaviour (ZPH parts 1 and 2) | done in part | `51b3ffdc25`, `8e83de143f`, merged at `580d53e495` and `f0ea153a8a` | Water entry, underwater movement, air countdown, drowning and currents green on three native movies; loop chunks by layer (Stardust Speedway) green on 1,822 rows; Metallic Madness shrunk radii and layer remap; Collision Chaos block check with glass shatter through the mutation pipeline (R32A green). Not done: Metallic Madness remainder, common remainder, bubble breath (blocked on the water surface's slot) |
| Wacky Workbench boss | done, part 2 done (see its row) | `cba7bf3055`, merged at `dc9ba9d174` | Every boss slot equals native RAM on four movies; one movie green on all 3,113 rows; act end reaches R71A. Three death rows fail on the player's status bit 7. No native movie past the second floor: top floor, defeat, escape and capsule unverified |
| Collision Chaos boss | done, follow-up done (BOSSFIX row) | `dbd7bcefee`, merged at `4d80577bcc` | Two complete native fights recorded; top-route movies green with all slots equal; drain rewind link fixed; act end reaches R41A; CD `DrawObject` on-screen timing lifted into `CdObject`. Fights stop after the block-check rows on new boss-side rows; R33D gear picture 81.5% |
| Quartz Quadrant boss | done | `6e5ca6e6c0`, merged at `5f7ceaabd0` with the conveyor gate | Death movie green on 2,035 rows; both fights green past the capsule to the layout's right edge (terrain, rows 4210/4211); every slot equal; pictures 97.2% or better; act end reaches R61A. Platform `$30` unhosted; one FM driver flag write not modelled |
| Tidal Tempest boss | done, follow-up done (BOSSFIX row) | `a2b4ae93d7`, merged at `2bbfb08b81` | Boss door, chase and fight. Chase equal on 3,064 rows in both futures with every slot equal; R43C and R43D green. Fights green to row 3915 (player breath pose on a bubble pop); defeat, escape and capsule recorded natively, unchecked on the engine. Fight timeline and pictures place the player in the arena instead of following the route. Bubble and missile pictures under the gate on the merged tree |
| Metallic Madness final boss | merged, native check partly done (PLAYER3 and routes rows) | `2a4f7b60b4`, merged at `0fa1f153ee` | Whole fight; the defeat reaches the good ending on R83C and the bad on R83D in a running engine (hits triggered by the test, not by recorded play). Three native movies recorded, none comparable yet: the approach diverges at row 725 on the unhosted collapse floor `$25`. No native recording of the ending itself (two attempts) |
| Stardust Speedway boss (Metal Sonic race) | built, part 2 merged (next Stardust Speedway row) | `1e8ccfa2fd`, not merged | Both wins equal native on every row for player, camera position, appearances and race slots; Bad Future pictures 99% or better. Failing on outside causes: level events at load, camera horizontal centre, player status bit 7, the zone lamp's rewind link |
| Merged-tree check at `2bbfb08b81` | measured | | Guard profile 836 tests, 0 failures. Sonic CD suite 1,435 tests, 12 failures, each owned (Collision Chaos boss fights 2, gear picture 1, object pictures 1, appearances 3; Tidal Tempest fight pictures 1 and one certification promotion since made; Wacky Workbench death rows 3) |
| Wacky Workbench boss part 2 | done | `0c2eb23b51`, merged at `921c4a085c` | Complete native fights in both futures by real play (no RAM poke): defeat, escape, capsule, results and the next stage equal native, every boss slot equal on every row. Boss-region tile upload and palette-cycle timer inputs. Game-wide player object-flags bit 7 (`CdPlayerObjectFlags`), also set by the Stardust Speedway laser kill. All 12 picture kinds 99.97% or better |
| Stardust Speedway boss (Metal Sonic race) | done, follow-up done (BOSSFIX row) | `3dfd152729`, merged at `1c5ee72ae0` | Both wins equal native on every row; ten native movies (wins, losses, laser deaths, dash hit, shield, Amy's hug and jump) with zero slot mismatches. Level events now run inside the load-time first iteration; the shared camera has an internal writer for its horizontal centre. Open: race rewind (a ring group's child rings are not rebuilt on restore), one Good Future heart picture (results card on the hug path), the zone scroll test reaching the arena |
| Full-act native routes (LONG) | done | `22e8e9f30f`, merged at `0b201e98e8` | Cold power-on movies from stage start to the signpost for 13 of 14 Present stages (R82A stops at $200A) and the Past and Good Future of each act 1 (12 of 14 reach the goal), in `native/rehost-long/` with a search server (`_tools/`). **This is the accuracy measure from here on**: the 14-second fixtures overstate progress. On integration `0b201e98e8`: 50,047 of 149,633 rows green before the first divergence (33.4%), no stage green to its signpost. Table with attributions: `planning/rehost/frontier-long-2026-11-12-22e8e9f30f.md`. Opt-in second-divergence view: `-Dsoniccd.frontier.continue=true` |
| Merged-tree check at `921c4a085c` | measured | | Guard profile 836 tests, 0 failures. Sonic CD suite 1,456 tests, 11 failures, each owned by the boss follow-up lane (Collision Chaos 7, Tidal Tempest fight pictures 1, Stardust Speedway 3) |
| Remaining player behaviour (ZPH3, ZPH4) | done | `1476ba6b36`, `6881878f18`, merged at `d9e1304e25` and `d70876bc9c` | Breath pose and drown reset on a bubble pop; bumper touched every overlapping frame; shared stand-on-object landing; touch skips objects not yet on screen; forced pad under control lock; revolve door and Scarab holds, spin disc, mini Sonic's set, object control as bit 0, hang-bar animation mode, Wacky Workbench loop remap. No full-act route stops on a player rule. Synthetic move tests now wait for the title card (native holds the pad until it leaves). Unverified natively: the revolve door, shrinking, layer flips |
| Capture tools and the release promo's takes (CAPTURE) | done | `7d14658243`, merged at `d74993ece3` | `TraceCaptureTool` and `GameplayCaptureTool` film Sonic CD (game id `scd`, outside trace directories, `--zone R11B`, per-frame `state.csv`); `SonicCdEndingCaptureTool` for the ending. 32 takes for the promo's nine shots in `~/Downloads/Sonic-CD/captures/rehost-promo/` to `planning/rehost/PROMO-needs.md`. Open when it merged, since fixed by the session lane: a second Sonic CD capture in one JVM fails with a GL error. Still open: `--x/--y` leaves some backgrounds wrong; the effects-only stem needs CD audio and PCM music to honour the music-off switch |
| Route lanes (routes B, objects 3) | done, second passes done (next rows) | `2fc40ef93e`, `92da714f98`, merged at `9f8105f1ff` and `51598e899a` | Tunnel paths, Quartz Quadrant platform, Dango, crusher, tube door, snake blocks; Metallic Madness collapse floor, door and bumper; Palmtree Panic platform and tunnel path; Collision Chaos blocks and spikes; Tidal Tempest switch and door. Most new objects have no native picture check yet |
| Terrain and camera (TERRAIN) | done | `8c32286177`, merged at `6f992c07b1` | Layout right edge reads blank (native `GetLevelBlock` masks the column); negated flipped-block width in the object wall probe; explicit-path object ceiling distance; Tidal Tempest vertical wrap with a new native movie; forward wall check skipped anywhere on a ceiling. Palmtree Panic and Quartz Quadrant boss fights green to the stage exit. Cause found, not fixed: R12B end picture (plane B tile content), R41A end picture (wobble phase, unconfirmed) |
| Object slots and spawn order (SLOTS) | done | `60fe623282`, merged at `872ef088aa` | Objects spawn in the layout's stored order (`StoredOrderObjectLayout`, selected by Sonic CD); the water surface claims the first dynamic slot before the first spawn pass; a collected ring is deleted on native's frame; Metallic Madness rings at priority 0; 51 stage windows in 9 appearance tests assert exact slots. Remaining slot differences listed per stage: objects native deletes that the engine keeps, or unhosted |
| Boss follow-ups (BOSSFIX) | done | `8d4fb0b877`, merged at `5da4ee01e6` | Both Collision Chaos fights equal native on every row to the stage exit: `PlaceBlockAtPos` leaves the object loop's counter cut short when glass breaks (shipped behaviour, modelled through `ObjectLoopSlotBudget`); 381 object pictures retaken on the trace run; Tidal Tempest boss sprites pass; ring groups rebuilt on rewind; Stardust Speedway results card on the hug path; one home proposed for all zones' boss flags |
| Second route passes (routes A2, B2) | done, third passes done (its row) | `814ab4cece`, `ee0c370c2e`, merged at `283013559d` and `ada029317f` | Palmtree Panic swing, Tidal Tempest act 3 animal, seesaws, free-path tunnel junction; special touches that native tests every frame were firing only on first overlap (breakable wall, bumper platform); flipper get-off air bit; spinning disc frame. Open: the three Yago native deletes at level frame 1 (blocks the bubble breath patch); pictures for most hosted objects; a Quartz Quadrant platform picture exclusion whose rule is not yet established |
| After the signpost (GOAL) | done | `8c77d74e7e`, merged at `74ac393256` lineage | Signpost sound and results music on native's rows. `TestSonicCdActEndNative` replays every route from its start and lists it as `asserted`, `pending`, `slots` or `rings`; a pending route fails once it reaches its end until moved. A first version placed the player near the goal from trace rows: the trace-invariant guard rejected it (the lead's brief was at fault) and it was reworked |
| Lag rows, load timing and mid-pass samples (LAG) | done | `d2aef9e701`, merged at `74ac393256` | Census of 138 native movies: all 5,939 lag-flagged rows are mid-pass samples, ordinary rows are sampled at VSync. Water updates on every V-INT (game algorithm). One sampling rule, `CdFixtureReplay.completedRow`, replaces the lanes' local rules; it only chooses which native row is compared. Proposals only, for the owner when it merged: the level V-INT counter's base at row 0 (the timing lane implemented it) and the video player's drive seek (the load-time lane implemented it under the owner's 2026-11-13 contract extension). Tools: `tools/soniccd/lag_census.py`, `lag_native_probe.*` |
| Water (WATER) | done | `a5fbe968a0`, merged at `6baa709f47` | Vent bubbles keep their on-screen bit until drawn; drowning sound requests equal native. The bubble breath patch stays out until the Yago are fixed |
| Player rules (PLAYER2) | done | `75d04b6618`, merged at `93d1cbdd82` | Wall-distance probes publish their angles (all games); ledge balance cancels a held charge; left headroom probe no longer flips twice (all games); look-up tap follows the pad through hurt. No full-act route stops on a player rule. Not hosted: the idle (boredom) timer and its give-up. No native movie yet for mini Sonic's restore, a layer flip or the revolve door |
| Execution order (SLOTS2) | done | `0e17748db4`, merged at `7d829c361c` | A native child checks the object ID in its parent's stored slot each turn; 27 child objects converted, all 157 spawn sites audited; 26 more stage windows slot-exact. Open, with SLOTS3: layout entries without a saved-state bit respawn while their object is live (R41C), ring collection order (R51A), the lost-ring object ID |
| Terrain 2 (TERRAIN2) | done | `ed157c53d3`, merged at `6c86bed2e8` | Palmtree Panic act 1 camera-centre level event; object floor probe over two blank blocks and object left-wall probe as Sonic CD rules; a block at the chunk data's first address reads as blank (R81B). No full-act stop is terrain or camera |
| Other games' trace fleet | attributed | | `com/openggf/tests/trace/{s1,s2,s3k,kis2,runs}`: 494 tests, 16 failures and 1 error on the pre-task base `38c69faeb5` and identically (same identities, messages and first-error frames) on `93d1cbdd82` and `6c86bed2e8`. None is this branch's. List: `planning/rehost/trace-fleet-baseline-38c69faeb5-failures.txt` |
| Third route passes (routes A3, B3) | done | `54445ddbdc`, `b6eb7a6b4b`, merged at `85ceebb0a8` and `8de2bdcddf` | Spike-ball children freed with their parent, so the bubble breath is in; special touches continuous as the Sonic CD rule; collapse floor, move blocks, Taga-Taga, Metallic Madness Dango; landing roll clear in a wall ground mode; 134 new object pictures at 99–100% (horizontal move block 83%, not certified); object rows checked on the full-act routes |
| Placement (SLOTS3) | done | `0291a381b9`, merged at `27837585b1` | Untracked layout entries spawn while a copy is live; bridge pieces follow their hub's slot; lost rings carry Sonic CD's object ID. R52A's act end is listed on rings: the spill's floor check is phased by the level V-INT counter and native loses V-INTs there (see timing) |
| Launcher and durable saves (LAUNCH) | done | `74f043e0ae`, `6fc1372000`, merged at `2bbd27f5b9` and `d4bffcfc93` | Sonic CD is listed on the master title from `roms.soniccd` (or found by its header under `roms.directory`) and boots the production flow; the whole disc is checked against the USA record; saves persist under `<save root>/soniccd/`; unhosted menu entries fade back to the title. `TestSonicCdPlayerLaunch` starts the game as a player does and equals native on R11A. Mod API pin gained the one configuration constant; version unchanged |
| Idle timer and unverified player behaviour (PLAYER3) | done | `1690d6f4a2`, merged at `193cdccf33` | Idle timer and give-up equal native on an 11,490-row movie; a lag row does not poll the pad (shared replay driver); Metallic Madness monitors and time posts solid only on the player's layer; both door movies and the final boss approach equal native to their ends; `R83C-fight` equals native through row 6965. Not reached natively: mini Sonic's restore |
| State carried between stages (SESSION) | done | `2332548d86`, merged at `8eecf2a03d` | Four pieces of state survived a stage change that native clears at every load (object window, object V-INT clock, player stage word and pad latches, camera Y centre): 46 of 182 ordered stage pairs differed, now none. A second boot dropped no stale GL names: fixed at the graphics owner; nine picture tests boot each stage fresh. Guard: `TestSonicCdStageChangeIsolation` |
| Stage-entry timing (TIMING) | done; its proposal was implemented by the lost V-INT row below | `175536404b`, merged at `65a21fd7a2` | The level V-INT counter's base is seeded once at the load for replay under the contract's section 4, as the other games seed their object clock; production starts it at 0 (documented). The production load frame no longer admits a second level frame (`LevelLoadRunsFirstIteration`): real play was one frame ahead for the whole stage. Left for the owner when it merged: native loses V-INTs nine times on the four Quartz Quadrant full-act routes (proposal in `planning/rehost/TIMING-lost-vint-proposal.md`; implemented by the lost V-INT row below) |
| Documentation (DOCS) | done | `f504b946ed`, merged at `b4e738831b` | Changelog entry, guide, configuration, known discrepancies, the stage coverage matrix (`docs/architecture/validation/levels/scd-stages.md`), skills (with `scddisasm-guide`), architecture docs. Its numbers were from `6c86bed2e8`; the second documentation pass refreshed them to `6c5c0e2764` |
| Merged-tree check at `65a21fd7a2` | measured | | Sonic CD suite 1,597 tests, 0 failures. Guard profile 836 tests, 0 failures. Other games' trace fleet equal to the base (same 17 failures, same messages) |
| Remaining object stops and slot gaps (routes C) | done | `0bacf48ff7`, merged at `c64306b4f2` | A bubble off screen is deleted (R41C); the speed shoes end on the hurt routine's last frame (R81A, one shared timer condition); followers are freed with their leader (R81B); null objects are deleted at spawn; Noro Noro spikes, the Quartz Quadrant swing, the Tidal Tempest prize switches, the wood block `$44` and spikes `$22`. `TestCdUnhostedObjectCensus` pins which unhosted object IDs each stage places (54 of 70 stages place one at this merge). Full-act routes 144,085 → 146,390 rows, 24 → 26 green; fixtures 56,679 → 56,879 rows, 67 → 68 green. Open: R42A (water, row 4286) and R82A (row 4577); why the engine draws an extra bottom row on the Tidal Tempest move block is not established |
| Load-time census tool | done | `b13454b5c8`, merged at `f6d464725b` | `tools/soniccd/load_time_census.py` measures stage-entry V-INT counts from the recordings: 70 of 70 stage programs constant across 157 recordings. Measurements only |
| Disc-free suite and skip policy (PREP) | done | `7eb2eb6129`, merged at `9b6cafd20f` | Sonic CD tests with no disc and no captures: 1,426 tests, 759 pass, 667 skip, 0 failures, every skip classified (215 were unclassified before). With the disc and no captures 379 skip; with both, 1,534 tests and 5 skips. Sonic CD skip rules 508 → 709 (804 in the file at the integration tip). Bare disassembly file names in comments were rewritten to `scddisasm/<path>` where exactly one candidate file fits; 908 citations still name no checkable file. Limits: 17 classes skip at class level and report no cases; the full-range push-policy audit flags one old commit with `TODO` trailers (`c49928fcd2`) |
| Lost V-INT admission (VINT) | done | `c4d8c49a4e` (recorder: TraceChaser `ab66814` on `feature/ai-scd-recorder`), merged at `93974a65cd` | On a trace row where the recording observed no V-INT entry, replay services none, under a dated extension of the timing contract with its guard. 18 Quartz Quadrant and control traces re-recorded: physics and aux rows byte-identical, nine lost V-INTs found (R51A 2, R51B 4, R51C 2, R52A 1). The counter equals native on every compared row of R51A, R51B, R51C, R52A, R42A and R81A, rings equal on the four Quartz Quadrant routes, and R52A's act end moved `rings` → `asserted`. Limits: the HUD RINGS flash phase has no native row; 140 recordings were not re-recorded (the counter census shows no lost V-INT in them); a real console's loss is cycle time, so play never models it |
| Capture lane 2 | done | `a318e69155`, merged at `82eb425981` | The Metal Sonic race finish (a new native movie `R73C-neck` and a test that replays it), `--no-hud` for both capture tools, re-filmed picture takes at 528 and 800 wide, and a written design for Sonic CD audio following the music and effects volumes. Limits: the time warp was not re-filmed (its stars could not be drawn); pictures beyond 320 columns have no native reference |
| Develop carried into the branch (MERGE) | done | `4a97d84a6b`, merged at `7cd28230d1` | `develop` (through the `next` carry) merged in with both sides kept at every conflict; two silent conflicts fixed; the Mod API pin regenerated, candidate version unchanged (0.7.0). Fleet 502 tests, 21 failures and 1 error, identical to `develop`'s; guard profile 844 tests, 0 failures. Limit: `--no-music` on a Sonic CD capture was not checked |
| Third-party notice (NOTICE) | done | `2551935026`, merged at `bf30490a40` | `CdBramEcc` and `CdBackupRamVolume` are translations of superctr/buram (MIT) and now carry its notice, copyright and licence text (`LICENSES/buram-MIT.txt`) and a `NOTICE.md` section; `CdBackupRamFile` shares only the descriptor layout. The lane also found Genesis Plus GX-derived code (next row) |
| Licence (LICENCE) | done | `ccc4abfbb8` (commits `fde9cc228b` to `3ad5d9a495`), merged ahead of the load-time merge | The PCM chip is re-translated from MAME `rf5c68` and the stamp graphics from MAME `megacd` (both BSD-3-Clause), with notices and licence texts; behaviour citations re-sourced away from Genesis Plus GX; its core patches no longer tracked; the scddisasm credit added; `TestArchitecturalSourceGuard` rejects such citations in `game/soniccd` and `data/cd`. 188 native PCM, stamp and video tests pass before and after. Limits: the Genesis Plus GX-derived commits stay in this branch's history (`0336cfbb93`, `aac8ffd03c`, `c9e200e9f4`, `26f6389ecf`, `9f26e14446`); four S3K timing files still cite `GPGX 051d430d`; some chip behaviours have no source (PCM reset pan and unmapped reads, stamp priority mode 3 and register widths) |
| Stage-load and disc-read timing (LOADTIME) | done | `ad102a704e`, merged at `6c5c0e2764` | Two normal-play timing kinds, `SCD_STAGE_LOAD` and `CD_READ_READY`, approved by the owner on 2026-11-13 (contract extension and guard). A 75-entry manifest from 169 recordings: 70 fresh stage entries, one checkpoint restart, one warp arrival, three video reads. Equal to native on the counter at every compared fixture row for R11A, R31A, R41A, R51A, R61A, R71A, R81A and on the real-boot row 0; video first bank 75 frames as native. Limits: checkpoint and warp entries are single samples not checked in an engine run; 12 stages' checkpoint and warp keys may name the wrong queued list; R12A after an act clear is one V-INT off; the video start differs 2 (opening) and 6 (endings) frames from the program's start; real-disc variance unmeasured |
| Merged-tree check at `6c5c0e2764` | measured | | Sonic CD suite 1,687 tests, 0 failures, 5 opt-in skips. Guard profile 847 tests, 0 failures. Other games' trace fleet identical to `develop` (502 tests, 21 failures, 1 error, all present on `develop`). Without a disc: 0 failures, every skip classified |
| Music and effects volumes (AUDIOMIX) | done | `4e1e6f8191`, merged at `fa09e0599c` | Sonic CD's disc audio tracks and video songs follow the music volume; on the PCM chip a channel follows the effects volume while an effect track plays on it (`CdPcmDriver.sfxChannelMask`, after `PlaySFX`, PCM/_Driver.asm:695-770) and the music volume otherwise; FM is effects only. At default volumes the output is bit-identical (124 native audio tests unchanged). The split is an engine property proven on R11A and R71B (music at 0 equals the effects alone; the parts sum to the full mix within 1); no native reference has a player volume. Shipped data never puts an effect on a song's channel, so that case is unverified. Both capture tools take `--no-music` |
| Past music after a direct stage load (PASTMUS) | done | `b494312c14`, merged at `0668f1a791` | A Past stage reached by stage select, a direct boot, another zone or a restart played no music: the level music request was sent before the stage's PCM driver was loaded and was dropped. The driver now loads in its own load step before the music request, the order of `SPCmd_LoadLevel` (SPX.asm:745-772) and `PlayLevelMusic` (Level/Main.asm:555-573). All 14 Past stages end a direct load with their song playing; on seven native recordings the first sound follows the music command within one driver run (frame-exact on two; native is one frame later on five, its PCM timer being free-running). Unverified: where the song is at level frame 1 (natively it has played about 126 frames by then; the engine's load presents no frames) |
| Time-warp stars and Robotnik's projector (WARP) | done | `79df096547`, merged at `a368ffb6ec` | The stars draw `Art_TimeStars` from the disc on the player's trail (they had read one position-history entry too far back): 97.3% of sprite pixels equal native over 55 shots on two movies, 99.98% outside the player's box, appearances on native's rows. The projector (parent, beam, Metal Sonic holograms, hit while rolling, solid body, explosions, `projDestroyed` on `CdTimeTravel`) is hosted in the six zones that assemble it, 52 stages; Metallic Madness has none. An authored movie that destroys it in Palmtree Panic equals native on all 2,182 rows, 48 of 48 events, and every projector and explosion sprite pixel; the six full-act Past routes stay equal on every row. No frontier stage moved: the placeholder was not solid. Unverified: projector pictures and destruction outside Palmtree Panic, the hologram animals |
| Merged-tree check at `45463c80bd` (every lane merged) | measured | | Sonic CD suite 1,719 tests, 0 failures, 5 opt-in skips. Guard profile 847 tests, 0 failures. Fixtures 56,879 of 57,049 rows, 68 of 70; full-act routes 146,390 of 149,633 rows, 26 of 28: stage by stage the same as at `6c5c0e2764` |
| Rings against native (RINGS) | done, lost rings in two routes open | `d4c81dbce4`, merged at `a53e511df5` | `TestCdRingNativeParity` compares the ring counter, placed-ring positions, the lost-ring count and ring slots on every row of the 28 full-act routes and two Palmtree Panic loop runs (R11B, and R13C's loop with eight rings, added by the capture lane at `8b6e6e7a74`; the replay comparisons never compared rings). Three causes fixed: a spawn that finds no free slot clears its entry's loaded bit (`.SpawnFailed`, Object Spawner.asm:144-151), so a ring group returns after a camera reversal (R51A); Hotaru spawners, the searchlight and the Scarab leave by range on native's rows, which freed the slots three Stardust Speedway rings needed (R72A); the ring spill runs on its owner's turn (Ring/Main.asm:332-410). 15 recordings slot-exact, 13 equal in counter and positions, R81A (row 3343) and R81C (row 3968) pinned. Measured natively: rings return after a death |
| Trace clips past the recording (CAPTURE3) | done | `ec1f3ddd77`, merged at `0c58578aca` | `TraceCaptureTool` carries on up to 2,000 rows past a disc game's last trace row on the production loop; the hosted game declares how the run entered its stage (Sonic CD: New Game, act 1 Present only). Nothing is compared past the trace. Used for the promo's warp, tunnel and Past take |
| Merged-tree check at `a53e511df5` (rings merged) | measured | | Sonic CD suite 1,748 tests, 0 failures, 5 opt-in skips. Guard profile 847 tests, 0 failures. Fixtures and full-act routes unchanged stage by stage. Other games' trace fleet 502 tests with the same 22 failing as before the merge, all failing on `develop` |
| OBJ0 object hosting, first common objects, `scd-implement-object` skill | done | `2cc59294a3`, merged at `5740df95fa` | Object ID, slot and frame of every dynamic-slot appearance equal native before level frame 393, where the unhosted 3D plant allocates four children; ring counter equals native on 501 frames; HUD and ring pixels equal the native frame. CD's `SolidObject` is approximated, not ported. Time digits and RINGS flash phase differ because native lag frames are not modelled |
| Palmtree Panic objects A: 3D ramp and plant, scenery, log shadow, waterfall, Mosqui, Anton, Pata-Bata, Tamabboh, Taga-Taga | done | `56dc25c6df`, merged | Appearances match native in ten fixtures; sprite pixels equal native for Mosqui, Pata-Bata, Tamabboh, plant and ramp; Anton 8255/8293. No native reference for scenery, log shadow, waterfall, the missile or the ramp booster. **The K2 R11A trace now replays all 1,411 rows with no divergence** |
| Common objects 2, exact `SolidObject`, per-owner object catalogue | done | `5ed1aac247`, merged at `6ff040c28d` | CD's solid routine ported exactly on the shared solid owner (no path shared with Sonic 1). R51A equals native for rows 0 to 501 across a monitor landing, ride and edge release. Boulder, floating block, checkpoint, flower, animal, robot generator, test badnik, game over, goal post, signpost, big ring, results, title-card locks. Common objects spawn only at the ID each stage's index gives them. No native reference for checkpoint activation, game over, goal post, signpost, results, big ring. Lead run: all CD tests, 1,061, 3 failures (two guards, one method pin since fixed and rerun: 13 tests, 0 failures) |
| Palmtree Panic act 1 presentation | done | `6a5d4fb97a`, merged at `3d5c270a0d` | R11A background camera equals native for 778 level frames; three native pictures match; Past and both futures uncertified |
| Wacky Workbench presentation | done | `da590fefff`, merged with the animated-tile hook | Nine of ten stages certified against native pictures at four camera positions; R63D uncertified (one shot 96.0%, checker-floor palette phase, cause unknown). Boss-region tile upload and boss palette flag not modelled |
| Tidal Tempest presentation and water | done | `d79fcfb5d9`, merged with water, zone-feature and tile hooks | All ten stages match native on the early picture. End picture certified for R42A to R42D and R43C; uncertified for R41A (three background bands shifted, 92.4%), R41B to R41D (96.7 to 98.0%, suspected camera Y fraction) and R43D (99.0%, one unhosted block). Player water physics not in scope |
| Collision Chaos presentation | done | `cf8c669718`, merged | All ten stages: every unmasked pixel of both native pictures equal. Animated tiles (act 2) implemented but invisible in the fixtures, so uncertified |
| Quartz Quadrant presentation | done | `261f54ffa7`, merged | All ten stages match both native pictures. No fixture reaches the background swap (player X below `$E00`); several layer rates are off-screen and unverified |
| Palmtree Panic acts 2 and 3 presentation | done | `198f8a7cfe`, merged | R11B, R11C, R12A, R12C, R13C, R13D certified on both pictures. End picture uncertified for R11A (3D-ramp scroll input, now wired, not rechecked), R11D and R12D (need a Plane B window per band), R12B (water colours one cycle step out at frame 811, cause unknown) |
| Stardust Speedway presentation | done | `c482684b4c`, merged | Scroll certified on every counted shot of all ten stages (99.97 to 100%); R72A rests on three heavily masked shots and its late shot is uncertified (foreground tiles differ, cause unknown). Palette cycling certified for Bad Future only; Present's is invisible in the shots. Past wobble, second curve and flat runs are never on screen in the fixtures |
| K0 program flow, title screen, stage select | done | `8fee13c0f2`, merged | Title picture equals native USA frame 1500 on all 57,344 pixels, with one fitted value (the title's main-loop count, 13). Three fixture movies take the retail title and stage-select route to their own stage. No new `GameMode`; no shared file touched. Contract: [programs](2026-11-12-sonic-cd-programs.md) |
| Audio: FM effects and CD audio | done, PCM music open | `4430dac546`, merged | FM: 15 sound-test effects equal native in every parse and tick count. CD audio: nine stages send the native command and correlate above 0.85 with native audio. PCM chip output equals 20 native blocks, but the PCM driver is not hosted, so Past music and PCM voices are silent. CD start latency unmodelled (native 41 to 83 frames). One other-game audio test fails identically on base |
| Special stage: gameplay and floor | done, presentation open | `26f6389ecf`, merged | Floor pixels equal native; Sonic, UFOs, timer equal native on every update of stages 1, 6 and 8 (524, 676, 399 updates). Missing: background plane, sprites, HUD, title card, fades, results graphics. Time Stone win and results never observed natively; no route to stage 2 |
| Native references for every stage; `scd` in the v5 validator | done | TraceChaser `7921d87` on `feature/ai-scd-recorder` | 70 of 70 stages recorded through the retail stage-select secret; 57,049 rows; all pass the v5 validator; 1,027,092 values equal the independent probe; wrong-stage control rejects every frame |
| K0 | done: see the K0 program flow row above | `8fee13c0f2` | |
