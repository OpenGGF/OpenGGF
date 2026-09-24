# S3K Sky Sanctuary Zone act 2 coverage matrix

Game / canonical zone / act: S3K `S3K_SKY_SANCTUARY_2`, engine zone `$0A` act index 1,
ROM `Current_zone_and_act = $A01`, SKL object set.
Character route: Knuckles only (`LevelSelect_CheckSonicTails` denies Sonic and Tails except with
`Debug_cheat_flag != 0`; no Sonic/Tails art or route exists for act 2).
Owning plan: [SSZ bring-up](../../plans/2026-09-17-ssz-bring-up.md).
Status: in progress. Arrival, crane, first defeat and transformation have cold-route/replay evidence at320/800. The complete cold fight reaches the accepted pre-ending stop at320/800; native parity, load-history isolation and remaining seeded presentation obligations remain open. Nothing below certifies the act.

Incoming: HPZ Knuckles teleporter pad → `$A01` (`HpzTeleporterRouteHelperObjectInstance`), level
select, save progression. Outgoing: the cold stop line is `loc_7BCFC`, 120 frames after the defeat
at `loc_7BCB0`; `loc_5E6C0`, `sub_5B18E`, `Obj_Ending` and the credits belong to the ending
campaign (user decision 2026-09-17).

Native fixture: `runs/s3k-knuckles-complete-superemeralds/hpz` (21441 rows, `bk2_frame_offset`
412501, start `$80,$6AE`) covers the whole of `$A01` including the ending camera rise to `$1CA0`.
**There is no Knuckles replay package** (`tests/trace/s3k/` holds only `sonictails` and
`tailsfullchain`), and Knuckles trace testing is out of scope by user decision; act-2 rows rest on
authored routes and native probes from that movie. The trace directory identity table lives in the
LRZ campaign's edit to [trace frontier log](../../../status/trace-frontier-log.md).

Five claims are tracked separately per row: **implemented**, **cold-reachable**,
**rewind-verified**, **native behaviour matched**, **visually matched**.

## Obligations

| Obligation + spot | Contract / oracle (ROM owner) | Config cases | Test binding | Implemented | Cold-reachable | Rewind-verified | Native matched | Visually matched | Gap / action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LOAD: identity, resources, music, title card | `Sonic3kZoneRegistry` zone 10 act 1; `Pal_SSZ2` `$34` | 320 | — | pre-existing | yes (cold load) | — | — | baseline capture `raw-00-ssz2-before` | Resource identity not yet asserted by a test |
| CENSUS: placed objects and rings | `SSZ2_Sprites $1F95F2` (5 records: `$00:$00` ×3, `$79:$00`, `$B2:$00`), `SSZ2_Rings $1F98E8` (the `(0,0)` record only) | ROM decode | `TestS3kSszPlacementCensus` | yes | n/a | n/a | yes (decode pinned to the ROM) | n/a | Concrete `$B2` and its cold release/replay now covered below |
| ARRIVAL: screen init and controller | `SSZ2_ScreenInit` (`Obj_57C1E` X `$A0`, `$2D = $44`, camera `(0,$649)`, `Scroll_lock`, `loc_59078` with `$30 = 1`) | 320 + one wide | `TestS3kSszKnucklesArrivalHeadless` | screen init and arrival connected | direct act load through release | mid-rise replay passes | source-backed rise/release assertions | unverified | loc_59078 encounter owner and release now covered; HPZ load through rise/crane replay now covered; load-history isolation remains open |
| CAMERA: act-2 controller `loc_59078` | Encounter routine0; seeded presentation routines4/8 | Encounter gate plus complete/incomplete emerald branches | `TestSszAct2CameraController` | routines0/4/8 connected and focused-tested | encounter only; later presentation seeded | encounter, routines4/8 and connected redraw replay pass | ROM-derived math | encounter captures inspected; later presentation unverified | Eight controller cases pass; incoming HPZ continuity now covered; later seeded presentation remains open |
| BG: parallax and column waves | `SSZ2_*DeformArray`, `word_58C80` (FG per-line HScroll), `loc_5904A` (20-column VScroll waves) | 320 + 800 | `TestSszAct2Deformation`, `TestS3kSszKnucklesArrivalHeadless` | encounter FG stages < $C | direct arrival | captured table + handler rerender/replay | source-derived arithmetic; native pixels pending | engine stills inspected | Later seeded-mask redraw remains open; wide columns extend native edge offset |
| CUTSCENE: crane `$B2` | `Obj_KnuxFinalBossCrane`, `loc_7CB64` (`mus_EndBoss` then `mus_FinalBoss`), `loc_7D11C` | 320 + 800 | `TestS3kSszCraneRouteHeadless`, claw/pan/release component cases | connected | through floor/control release | grab → release restore/replay | source-backed graph; native checkpoints | engine movie + native crane checkpoints | Both fights continue in the cold route; native whole-route comparison remains open |
| BOSS: Mecha Sonic phase, forced run, Super phase, Master Emerald | `Obj_SSZEndBoss` act-2 init `($220,$4A0)`, `loc_7BBE0`, `Obj_SSZ2_Boss` (36 routines), `loc_7B996` (`mus_DDZ`) | 320 components; cold entry 320/800 | `TestSszAct2MechaEntry`, `TestS3kSszAct2ColdFight` | all36 Super dispatch branches present; behavior validation partial | first defeat through Super entry at320/800 | defeat-to-Super replay; charge callback/player release | source-derived timer/movement; native transformation observations | crane/entry only | First defeat and transformation pass320/800; both fights and the pre-ending stop now pass320/800; full native comparison remains open |
| PRESENT: palette rotation | `Run_PalRotationScript`, `Palette_cycle_counters+0`, `loc_7D09C` | 320 component | `TestSszAct2MechaEntry` | Mecha table interpreter + screen flash | not yet through cold first defeat | table transition and flash target replay | source-derived finite/infinite/custom commands and native cadence | final fight unverified | Full encounter presentation remains open |
| DEFEAT: save and stop line | `loc_7BCB0`, `loc_7BCFC` after119 | 320/800 | `TestS3kSszAct2FinalFight`; component effect suites | connected through accepted stop | full cold route | low-health and final-defeat world replay pass | source-backed timing; native comparison open | captures14/15 inspected | Disk clear payload verified320/800; native visual comparison remains open |
| PRESENT: post-defeat floor patch | `SSZ2_ScreenEvent` stage4 (`Ending_running_flag`) | 320/800, declared completion byte | `TestS3kSszKnucklesArrivalHeadless` | connected | not yet | terrain/event restore and replay pass | source-backed rows9/10; native pixels open | capture15 | Full cold defeat reaches stage8 at320/800 |
| PRESENT: tile fill and ending island mask (seeded) | stage8/$C, `loc_591D6` | seeded negative controller signal; positive event boundary; 320/800 | mask/retained-plane/arrival suites | connected;19-case combined selection passes | **cold-blocked** | mask motion, mixed-plane replay and derived-art regeneration pass | source mapping and seeded native island checkpoint verified | original-view checkpoint matches | Cold trigger is excluded ending owner; negative-signal chain and redraw replay pass320/800; wide sky extension verified without changing native viewport; other presentation checkpoints remain open |
| PRESENT: second redraw, shared palettes and streamed island descent (seeded) | `loc_58C1A/58C42/58C68`, `Draw_TileRow`, `loc_58F52/5906A` | 320/800 event replay; four native arithmetic samples | ending-plane/deformation/Knuckles-arrival suites | implemented through stage$18 | **cold-blocked** by excluded ending owner | mixed-plane, drawing cursor and palette replay | scroll bytes match four native samples; source-backed streaming | capture17 wide water/cloud entry | Whole-scene native comparison remains open; setup explicitly seeds omitted-owner signals |
| ANIM: act 2 has no AniPLC scripts | `Offs_AniFunc` → `AnimateTiles_NULL` (`rts`) | native load | `TestS3kSszPatternAnimation#act2AnimatesNothing` | yes | load registration | n/a | source-backed empty script list | n/a | Historical slice2 result: three cases pass; this checks script selection, not every runtime art writer |
| LOAD: ending (`sub_5B18E`, `Obj_Ending`, credits) | ending campaign | — | — | blocked | blocked | blocked | blocked | blocked | Out of scope |

## Execution evidence

Worktree `.worktrees/ai-ssz-bring-up`, branch `feature/ai-ssz-bring-up`, base develop `035e48a58`.
All Maven through `python3 tools/testing/maven_queue.py -Dmse=off …` with
`-Ds3k.rom.path=<absolute path to the worktree>/s3k.gen`.

Slice 0, 2026-09-17. `-Dtest=TestS3kSszPlacementCensus`: 7 tests, 0 failures, 0 errors, 0 skips,
after a deliberate break confirmed the comparison runs. Baseline capture
`~/Videos/OGGF/ssz-bring-up/raw-00-ssz2-before` (Knuckles, 320, 360 frames; level start `(128,32)`,
camera `(0,0)`); frame 200 inspected — the act renders its static cloud layout with no arrival
controller, camera controller or crane. The ROM's `$80,$6AE` start in the native fixture is written
by `Obj_57C1E` (`Camera_Y + $65` with camera Y `$649`), not by the start-location table.


### Campaign source reconciliation (2026-09-23, `ddf517a54` development tree)

The slice-0 baseline above is historical. `Sonic3kSSZEvents.applyScreenInit`
already selects act-2 arrival X `$A0`, rise counter `$44` and camera `(0,$649)`;
`SszArrivalControllerObjectInstance` has the act-2 release priority branch and
omits the act-1 cutscene spawner. `TestS3kSszArrivalHeadless` covers act 1 only,
so this source presence is not act-2 route/rewind evidence. The event update
currently advances act 1 only, and `SwScrlSsz` explicitly leaves act-2 background
init/events to this slice. No concrete Knuckles crane or Super Mecha final-phase
owner was found in the production S3K object inventory. Continue from these
existing arrival pieces rather than rewriting them from the old baseline.
The accepted Knuckles-trace and ending/credits exclusions above still apply.


The campaign now adds `TestS3kSszKnucklesArrivalHeadless` at 320/800: actual
Knuckles roster, `$649/$6AE` initial camera/player Y, `$44` eight-pixel rise
passes with the last camera decrement omitted, `$A0` pad alignment, the high
priority/swing release branch, and a mid-rise capture/restore plus forward replay.
The queued selection with the existing act-1 arrival regression passed eight tests with zero failures/errors/skips on 2026-09-23.
This deliberately does not certify `loc_59078`, the crane, final fight or ending.


Act2 camera foundation (2026-09-23, after `68352274e`): the queued camera,
runtime, act1/act2 arrival and cloud selections pass19 cases with zero skips.
The new controller occupies the next slot after arrival, stays inert until
Special_V_int_routine enables it, retains long fractional drift, and restores
its swing plus shared state through actual recreation/forward replay. Existing
act1 cloud behavior and arrival remain green. Act2's renderer, palette, crane,
boss and in-scope defeat presentation remain unfinished.

Consumer checkpoint: queued `-Dtest=TestS3kSszScrollBands,TestEveryObjectRewindRoundTrip,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
with Java21/absolute S3K ROM passes1364 cases, no failures/errors/skips, including
1294 every-object recreation checks. Together with the19 foundation cases this
validates the bounded camera/state change, not the unfinished act2 presentation
or full campaign suite.


Pending crane/entry component checks (2026-09-23 development tree):
`TestSszCraneCameraPan` covers release-after-boundary and rewind;
`TestSszCranePlayerRelease` covers moving init, pose/gravity overlap, rewind and
ROM-floor landing; `TestSszAct2MechaEntry` covers camera-ready gating, native
spawn/arena words, attack-counter seed and rewind. Art decoding is selected
in `TestSonic3kPlcArtRegistry.sszAct2CraneGraphHasRomBackedSheetsIncludingKnucklesHead`.
These are queued, not verified passes, and their actors are not yet connected
into the complete crane/boss route. The existing act1 Mecha regression suite
is queued for the shared entry change. Full route and Super-phase obligations
remain open.


The $B2 placement is now wired to the complete crane ship/child implementation
in the development tree. `TestS3kSszCraneRouteHeadless` is queued for cold neutral
Knuckles routes at320/800 through floor/control release and Mecha allocation.
This replaces the previous unwired-component status, but remains pending
verification. The first-defeat transformation and Super phase are still open.


Completed narrow checks: all4 act2 arrival/scroll-restore cases pass with no
skips after matching the captured level/render clock. The act2 Mecha entry
case also passes. These prove the specific entry/restore boundaries only; the
new cold crane routes and claw graph checks remain pending.


Crane route results: `TestSszCraneClaw` passes2 cases; the cold-route/release/art
selection passes5 cases (two cold routes at320/800, two release cases, one
four-sheet ROM mapping/decode case), all with zero failures/errors/skips.
This establishes cold arrival through the crane's floor/control handoff and
Mecha entry; it does not establish either boss defeat or the Super phase.


The extended cold-route rewind test also passes both widths, zero skips:
capture at grab, restore after floor release, then forward replay reproduces
player position/control, camera X, complete SSZ runtime bytes and one live
Mecha. This covers the real ship/head/flame/claw/parts graph and its handoff.


Transformation/first Super-cycle component checkpoint (2026-09-23, `b6c1147a2`
development tree): `TestSszAct2MechaEntry`, `TestSszMechaAim`, existing act1
`TestS3kSszMechaSpawnHeadless`, and the ROM sheet-limit check pass41 cases with
zero failures/errors/skips. Component coverage reaches the first transformation,
release-boundary rewind, crossing/particle rewind, projectile phase and emerald
recharge. Screen-flash steps/target restoration and finite/custom palette-table
commands are independently asserted. This is **not** a cold first-defeat or
complete Super fight result: dive/slam, low-health attack/rewind, allocation
pressure, second defeat and seeded ending mask remain open.


Final control boundary component (same development tree): the act2 graph plus
existing act1 regression selection passes33 cases, zero skips. After the declared
second-defeat callback, it checks arena locking,192/32-pass waits, white-fade
completion, ending flags/player control,119-pass cold stop and mid-fade restore /
forward replay. This does not prove real Super-phase damage, save-file persistence,
final explosion/debris/shake presentation or a cold route through either defeat.
Those obligations remain open.


Shake boundary (same development tree): seeded rumble checks in
`TestS3kSszKnucklesArrivalHeadless` pass at320/800 with actual game-loop stepping,
FG/BG offset assertions, same-frame rerender and registry restore/forward replay.
The combined50-case selection (arrival/deformation/Mecha components and act1
regressions) has zero skips. Cold historical-input capture06 instead dies during
the first fight at2026; it does not demonstrate the transformation, final shake
or second defeat. Native visual shake comparison and complete cold fight remain open.


Breakup pieces (2026-09-23, same development tree): the37-case debris/Mecha/art
selection passes without skips. Both parent flips cover motion, flicker,
recreation/replay and retirement; the connected graph allocates16 fragments.
ROM mappings use the retained defeated-frame-$E bank. This is component/art
validation, not a cold defeat or native visual comparison. Allocation pressure
and the remaining attack-family/route obligations stay open.


Post-defeat terrain boundary (same development tree): the two320/800 cases in
`TestS3kSszKnucklesArrivalHeadless` pass as part of its8-case rerun, no skips.
A declared boss-completion byte triggers the native rows9/10 floor patch,
separate ending-running flag and stage8; full registry restore returns the
original map and replay reproduces the patch. Existing Mecha8/crane2 cases
passed in the preceding18-case run, whose only errors were the corrected test
map-width assumption. Seeded controller4/8, island mask and delayed redraw
remain open, along with the complete cold fight.


Seeded presentation camera (same development tree):13 controller/arrival cases
pass without skips, including complete/incomplete emerald branches, negative
signal/two-crossing entry, unsigned fractional-speed cap, one-pixel motion,
endpoint signal/lifetime and registry replay to the same pass count. The camera
motion component declares Y=$400 after entering routine4. Later routine8,
island mask/redraw and cold-route closure are not established by this result.


Island cover remains a component: two lifetime/fractional-motion replay cases
and the art-mapping guard pass (3 total, no skips). Tile-fill testing exposed
stale sheet placeholders after level-pattern capacity growth; the local rebind
fix is awaiting its focused run. Stage8 allocation, delayed redraw, derived-art
regeneration after rewind and native/widescreen visual checks remain open.


Combined island/redraw result (2026-09-23, `b6c1147a2` development tree):19 cases
pass with zero skips, completed16:21 BST. Positive stage8 signal → fill/mask →
eight redraw passes → stage$10/camera clear is checked at320/800, including
mid-redraw restore/replay and intentional derived-art corruption repaired by
the restore callback. Water colors match ROM rows over64 real-loop frames and
replay at both widths. Full negative-signal chain, native pixels, actual sprite
submission and wide cover extent remain open; actual ending is excluded.


Arrival-order follow-up (2026-09-23): the initial sprite pass now owns arrival
initialization before ordinary Knuckles physics. Grounded status during the rise
is regression-checked at320/800 (both assertions failed before the correction);
the12 arrival/event cases and2 fixed-slot adapter cases pass without skips.
Crane route/replay recheck is pending after replacing its permanent-bit4
assumption with observation of the consumed pan signal. Ordinary movie playback
now lands three first-phase hits, but dies before completion; no cold-fight
certification follows.


Cold first defeat and transformation (2026-09-23): the authored controller-only
route reaches eight first-phase hits at 3294 and Super entry at 4016 in ordinary
800-pixel capture. The Master Emerald/forced-run sequence is visible in capture08;
frame4000 and full video decode were checked. The reproducible 4017-frame script
and BK2 live under `src/test/resources/routes/s3k/ssz2-first-defeat.*`.
`TestS3kSszAct2ColdFight` (320/800, snapshot at first defeat and replay through
Super entry) is queued, so that broader claim is pending. Native Super input
continuation lands three Super hits and dies; complete second defeat remains open.
Controller routine8 motion/lifetime and fractional replay have a separate queued
check in `TestSszAct2CameraController`; actual ending allocation remains excluded.


Seeded native island comparison (2026-09-23, development tree at `b6c1147a2`):
BizHawk loaded the recorded pre-ending cold stop at movie frame419846, deleted
the defeated boss before its excluded ending allocation, and declared the
negative `Events_fg_4` signal. The reference also applies the omitted owner's
`Scroll_lock`/screen-shake setup (`loc_5E70E`) and restores `Target_palette` to
`Normal_palette` to represent completion of its fade-from-white worker
(`loc_85EE6`). It stops at stage$C with one row left, before `sub_5B18E`.
The engine follows the negative-signal camera/redraw chain from its declared
seed; the connected 320/800 rewind regression is awaiting its queued run.

At the penultimate redraw checkpoint, native image `01629.png` (framebuffer
crop origin14,8) and engine capture09 image `01615.png` have zero differing
Genesis 3-bit RGB pixels in screen rectangle x0..319/y64..189 (40,320 pixels),
including the island. The narrower x40..284/y60..184 rectangle also matches
(30,625 pixels). No fitted translation was applied; HUD is outside the region.
This establishes that native-view checkpoint only: widescreen exposes cloud
fragments beyond the original viewport and remains under investigation.
The earlier native seeds without scroll lock (camera overwritten) and without
palette restoration (white frame) were rejected, not counted as references.

The corrected crane route test completed with two cases, no failures/errors/skips
(`TestS3kSszCraneRouteHeadless`, queued Maven, 17:15 BST). It observes the pan
signal before the landing event consumes it, then checks release and replay.


Queued focused results completed 17:16 BST on the same development tree:
`TestSszAct2CameraController` passes all eight cases, no skips, including the
connected negative-signal camera → stage$C chain at320/800, partial-redraw
restore/replay, and incomplete-emerald routine8 fractional movement/deletion.
`TestS3kSszAct2ColdFight` passes both320/800 cases without skips: eight actual
first-phase hits, first defeat, transformation to Super and identical replay
from the defeat snapshot. These do not establish second-phase completion.
The separate68-case arrival/bootstrap/loading/lifecycle selection has one
checkpoint-reload failure (67 pass, no skips): SSZ incorrectly consulted the
post activation mark rather than restored checkpoint index. The native
`SSZ1_ScreenInit` gate fix and targeted recheck are in progress.


Checkpoint gate follow-up: the24-case targeted run passes23 cases, including
all three production lifecycle checks and18 arrival cases, with no skips. The
remaining bridge-spawner timing assertion counted setup-only initialization as
an ordinary gameplay frame; the full bridge/cutscene cases pass. Its assertion
now explicitly consumes the initial sprite pass before counting the remaining
`$60` decrements and awaits a narrow three-case recheck.


Widescreen island projection investigation: descriptor probes identify the
extra clouds as Plane B layout art (for example screen448/8 samples descriptor
`$6166`), while Plane A at the same samples is transparent tile`$4000`.
Extending Plane A would not address the source and was rejected. The new
wide-only background projection repeats the last native visible ROM tile
column beyond the island view, retaining native columns, Y sampling and tile
priority. It is gated by the seeded ending-plane lifetime; ordinary encounter
projection is unchanged. Focused projection/arrival/controller tests and a new
render are pending; this is not yet a visually verified fix.


Controller-only Super route progress (capture11, 2026-09-23): four Super hits
at4815,4847,4993,5953; cold playback reaches6200 alive at800px. The second
projectile-volley failure was a player jump into its high laser, observed in
a per-object position probe. Delaying that jump and approaching the recharge
window lands the fourth hit. The extended probe still dies at6765, so this
is not complete Super-fight evidence. Capture11 records1501 frames from4700,
with original input/action bits preserved by the authoring-tool round trip.
CSV has6201 rows and zero deaths; still5953 and full video decode were checked.
The previously documented first-defeat replay remains the automated route
boundary; the new four-hit continuation is not yet promoted to that test.


Full controller route candidate (2026-09-23): ordinary800px gameplay lands
Super hits at4815,4847,4993,5953,6878,7023,7661,8156 and reaches the accepted
pre-ending cold stop at8703 (`act2EndingActive`, stage8, boss timer0). Both
fights use actual collision hits; no health, position or physics values are
hydrated. The input has been promoted to `ssz2-final-defeat.script/.bk2` and
round-trips through the production authoring tool (8704 frames).
`TestS3kSszAct2FinalFight` is queued for320/800, including first transformation,
low-health restore/replay and final-defeat restore/replay, comparing active
object positions, camera/player, palette, clock and SSZ runtime bytes at stop.
These queued assertions are not yet results. A native-width direct replay and
new complete-fight video remain pending compilation. Save-file persistence and
native presentation comparison remain separate obligations.


Final focused results (2026-09-23, development tree at `b6c1147a2`):
queued Maven with Java21 and the absolute locked-on ROM completes
`TestS3kSszKnucklesBridgeHeadless` (3 cases),
`TestS3kSszKnucklesArrivalHeadless,TestSszAct2BackgroundPriority,TestSszAct2CameraController`
(23 cases), and `TestS3kSszAct2FinalFight` (2 cases). All have zero
failures/errors/skips. The final-fight result completes17:42 BST and proves
actual collision defeats plus transformation, low-health and final-defeat replay
at320/800. Independent320px ordinary playback also reaches health0/timer0 at8703.

Capture12's seeded wide island has no stray cloud strips; its entire native
320px viewport is byte-identical to the pre-extension capture, and the40,320
non-HUD native-reference pixels still match after3-bit RGB quantization.
Capture13 records2004 frames from6700 through8703 of the full cold route;
8704 CSV rows contain no deaths. Stills8156/8430 and full video decode pass.
Both captures have input/setup and compiled-class provenance outside the repo.
These are focused and ordinary-route results, not a full-suite or strict-parity
certification. Integration, broader campaign verification and the remaining
matrix obligations are still open.


2026-09-23 priority/framing follow-up: the body now preserves `ObjSlot_MechaSonic`
art_tile bit15 independently of queue$280. `TestSszAct2MechaEntry` reads the ROM
word; `TestS3kSszAct2FinalFight` checks the tile mask throughout both fights and
replay. Both cold320/800 cases pass including real disk clear payloads. The
widescreen Emerald preview hands over without an early native allocation;
its live-state gate is tested before setup, drawing, retirement and rewind.
Both pans are checked at actual presets320/352/400/528/800, with native bounds
and completion timing retained. Initial wide component checks accidentally kept
a320px fixture; corrected aspect/session setup passes all10 preset cases.
The combined focused selections establish42 distinct passing cases, zero skips;
this is not the combined campaign suite. Captures14/15 (external task directory)
contain the pan/preview and late fight, both full-decode checked with zero deaths.


Incoming continuity follow-up: `TestS3kHpzLifecycleProduction#knucklesUpperTeleporterStartsSkySanctuaryActTwo`
now follows the actual HPZ pad/load into SSZ's mid-rise, captures there, and
replays through crane release at320/400/512/640/800. All five cases pass, zero
skips. The new manager/Knuckles roster/width and empty checkpoint history are
checked; elapsed arrival-to-release frames, player/camera words and all SSZ
runtime bytes match after restore. The initial assertion used0 for the empty
checkpoint index; the engine's documented empty sentinel is-1, corrected in
the test. This starts at the real HPZ pad and is not a cold whole-HPZ route or
proof of rewind-history isolation across the load.

The updated S3K consumer selection (`TestEveryObjectRewindRoundTrip`, AIZ1 skip,
level loading, bootstrap resolver and decoding utilities) passes1371 cases with
zero failures/errors/skips on the current development tree. This includes the
new camera-owner field and runtime projection state; it is focused consumer
validation, not the full ordinary/guard suites.


Seeded presentation follow-up: `TestSszEndingPlaneState,TestS3kSszKnucklesArrivalHeadless,TestSszAct2Deformation`
passes39 cases with zero failures/errors/skips (Java21, queued Maven, absolute
locked-on ROM,18:34 BST, development tree `b6c1147a2` plus current edits).
This includes four native scroll-byte hash samples and the second redraw/shared
palette/drawing-cursor replay checks. Capture16 exposed missing streaming after
the initial blank plane; capture17 shows the repaired water/cloud rows at2625.
The source-backed streaming consumer is distinct from the native arithmetic
oracle. No cold-route or complete native visual-parity claim follows.

After adding the captured streaming cursor, `TestSszZoneRuntimeState,TestEveryObjectRewindRoundTrip` passes1314 cases, zero failures/errors/skips (18:35 BST, same Java21/ROM/worktree). Capture17 completes2626 steps and1226 PNGs, ends stage$18 at cameraY=$804, and fully decodes. These are focused results; combined campaign verification and integration remain open.


### Crane recreation evidence (2026-09-23)

`TestS3kSszCraneRouteHeadless` now asserts that the real placed encounter has its
claw, both claw parts and ship decoration alive at capture. It destroys the ship
and children, restores every registry key, replays45frames, then compares every
key again at the complete player-release endpoint. Both320/800cases pass.
The isolated round-trip sweep cannot construct these parent-dependent children;
its three classifications now point to this graph evidence. Reconciled campaign
inventory:1313classes,1070isolated passes,243graph-covered,no-codec0 and no
unclassified remainder. This does not certify every SSZ lifecycle/route obligation.

Final-fade rewind follow-up (2026-09-24): `TestS3kSszAct2FinalFight`
now captures the active final white fade and restores it after retirement at
320/800, replaying to the same accepted pre-ending stop with matching palette,
objects, camera and runtime state. The 33-test focused rewind closure run passed
with no skips; full campaign delivery validation remains separate.
