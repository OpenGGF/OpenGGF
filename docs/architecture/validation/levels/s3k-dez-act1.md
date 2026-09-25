# S3K Death Egg Zone act 1 coverage matrix

Game / canonical zone / act: S3K `S3K_DEATH_EGG_1`, engine zone `$0B` act index 0,
ROM `Current_zone_and_act = $B00`, SKL object set. **Not Sonic 2's Death Egg**: the
`TestDEZ*`/`TestS2Dez*` classes and the `*dez-boss-fixes*` documents are Sonic 2.
Owning plan: [S3K DEZ bring-up](../../plans/2026-09-17-s3k-dez-bring-up.md).
Status: traversal families and the two-phase miniboss/results/Act 2 transport are
implemented. A 14,231-frame cold Sonic+Tails controller route now clears the turbine, both
miniboss phases and the real Act 2 load, without deaths or setup overrides. The
shorter upper/turbine routes retain 40 independent full-registry replay spots;
late-route verification is recorded in the dated follow-up below. Positioned solo Hyper
completion at320/800 is recorded below; remaining character/roster/donor breadth and native timing
acceptance remain open. Historical slice rows below are superseded by the dated
follow-ups where explicitly stated. Nothing below certifies the act.

LevelSizes (sonic3k.asm:38119): x `0`-`$6000`, y `0`-`$B20`. Start location `$30,$9AC`
(measured: the engine's cold `$B00` load places Sonic at centre `$30,$9AC`).
Level art: `levartptrs $36,$36,$20` (PLC `$36`, palette `$20`,
`ArtKosM_DEZ_Primary`/`ArtKosM_DEZ1_Secondary`, sonic3k.asm:199455). Music
`Sonic3kMusic.DEZ1`. `Obj_LevelIntro_PlayerRun` runs at cold entry
(`SpawnLevelMainSprites` `loc_6986`; already implemented).
Placements: 365 objects, 278 rings, no gravity writer (`$58/$59/$5B` are act 2 only).

Incoming: SSZ `$A01` → `$B00` (SSZ campaign owns the request). Outgoing: seamless
`$B00` → `$B01` through `DEZ1_BackgroundEvent` `loc_593EC`.

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`):
widths 320/352/400/528/800; off×{Sonic,Tails,Knuckles}, S1×Sonic, S2×{Sonic,Tails};
teams solo, Sonic+Tails, S1 Sonic+Sonic, S2 Sonic+Tails, Sonic+Tails+Knuckles at 800.
Knuckles is **level-select only** for this act (user decision 2026-09-17): he must load,
play and invert correctly, but owes no cold chain and no trace frontier.

## Five claims

| Claim | State |
| --- | --- |
| Implemented | Presentation foundation, runtime event words and 365/365 concrete placements, including gravity tubes, turbine room/puzzle/bumper walls, energy bridges, Spikebonkers and Chainspikes. Miniboss and act-change code is connected; continuous positioned defeat/transport and cold native320 Sonic+Tails completion are verified; remaining breadth and native comparison remain open |
| Cold-reachable | Ordinary Sonic+Tails native320: complete Act 1 through actual Act 2 load in14,231frames; see dated follow-up |
| Rewind-verified | Palette/event/object checks plus62 distinct cold-route replay spots and real Act1→Act2 rewind isolation; remaining breadth/lifecycle checks stay open |
| Native behaviour matched | Not started; replay frontier measured at `035e48a58`, see the row below |
| Visually matched | Engine inspection only, at 320 and 800 px (`raw-01-presentation-320`, `raw-02-presentation-800`, clips `01a`-`01d`); no native pixel comparison yet. Slice 4: clip `040-chainspike-800` shows the `$A5` charge, stop and return at 800 px; clips `043`/`044` show turbine bounces at 320/800 px and `045` shows six-panel steering followed by exit; no native pixel match |

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| ENTRY: `$B00` resources, bounds, object set | LevelSizes `$6000`x`$B20`, `levartptrs $36/$36/$20`, SKL set | — | — | present before this campaign | unrun | Slice 0 records it; no assertion yet |
| ENTRY: title card, intro run | `Obj_LevelIntro_PlayerRun` `loc_6986` | — | — | implemented (shared) | unrun | Slice 6 |
| PRESENT: background scroll | `DEZ1_BackgroundInit` clears `Camera_X/Y_pos_BG_copy`; `PlainDeformation` never rewrites them, so both BG scroll words stay 0 | 320 + one wide | `TestS3kDezScrollHeadless` (6 tests) | implemented (`SwScrlS3kDez`) | pass, `4e7655bf9`; clips `01a`, `01d` | Native pixel comparison open |
| PRESENT: `AnPal_DEZ1` channel A | counter `Palette_cycle_counters+$0A` reload `$F`, index `+$04` step 8 limit `$30`, `AnPal_PalDEZ1` ($349C, $30 bytes) → palette line 4 colours 12-15 | 320 | `TestS3kDezPaletteCycling` (4 tests) | implemented | pass, `4e7655bf9`; clip `01b` | Table frames 1/5 and 2/4 are byte-identical, so the test accepts either alignment; native comparison open |
| PRESENT: shared AnPal channels B and C | B: `Palette_cycle_counter1` reload 4, `counter0` step 4 limit `$30`, `AnPal_PalDEZ12_1` ($3444) → line 3 colours 13-14. C: `counters+$08` reload `$13`, `counters+$02` step `$A` limit `$28`, `AnPal_PalDEZ12_2` ($3474) → line 3 colours 8-12 | 320 | `TestS3kDezPaletteCycling` | implemented | pass, `4e7655bf9`; clip `01b` | `AnPal_PalDEZ12_2` frames 1 and 3 are byte-identical; native comparison open |
| PRESENT: `AniPLC_DEZ` 8 scripts | `AniPLC_DEZ` ($28AEE), durations `0,1,3,-1,4,4,1,0`, script 7 = 132 frames; generic `AnimateTiles_DoAniPLC`, no gate | 320 | `TestS3kDezAnimatedTiles` (7 tests) | implemented | pass, `4e7655bf9`; clip `01c` | Per-frame DMA order not compared against native |
| EVENT: `DEZ1_ScreenEvent` chunk `$BD` | `Events_fg_4` → `movea.w $14(a3),a1; move.b #$BD,$6E(a1)` = FG layout row 5, column `$6E` | 320 | `TestS3kDezScreenEvents` (6 tests) | implemented (`Sonic3kDEZEvents`) | pass, `4e7655bf9`, driven from the runtime state | Production trigger is the miniboss (slice 6); not yet reachable cold |
| PLACEMENT: 365 act 1 objects | [inventory](../../research/s3k-zones/dez-object-inventory.md) | — | `TestS3kDezPlacementCensus` | 365 concrete / 0 placeholders | pass | Remaining families are listed in the census and bring-up plan; implementation counts do not certify a route |
| GRAVITY: `$5A` gravity tube, 24 act 1 placements | `loc_48EEC`/`sub_48F12` and `loc_4906A`/`sub_49090` (sonic3k.asm:95169-95401) | — | `TestS3kDezGravityTubeHeadless` | implemented (`S3kDezGravityTubeObjectInstance`) | pass 11/11 | Behaviour is act-independent; the tests place it at the act 2 site beside the `$5B` at `$1A40,$08C0`. No act 1 route reaches one yet (slice 6) |
| GRAVITY: `$5F` turbine corridor, 1 act 1 placement (`$2480,$0840`) | `sub_4964A`, sonic3k.asm:95814-95952 | — | `TestS3kDezGravityRoomHeadless` | implemented (`S3kDezGravityRoomObjectInstance`) | pass 14/14 at 21:41 BST after `bf7e5175c` | Includes actual placed-controller lifetime, unsigned range edges, puzzle/closed-gate contact, production rewind and 320 px six-panel controller route and actual 320/800 closed-gate bounce; cold entry, native matching and team/donor breadth remain open |
| GRAVITY: `$61` gravity puzzle, 1 act 1 placement (`$2690,$0840`, inside the `$5F` corridor) | `Obj_DEZGravityPuzzle`, sonic3k.asm:96087-96245 | — | `TestS3kDezGravityPuzzleHeadless` | implemented (`S3kDezGravityPuzzleObjectInstance`) | pass 12/12 | Twelve mechanisms, fifteen deliberate breaks, every one red. The six panel bits live in `S3kDezZoneRuntimeState` because the ROM keeps them in `MHZ_pollen_counter`, a level RAM byte. Filmed standing on the solid top (`036`), airborne bounce (`043`/`044`) and six-panel controller traversal (`045`). Corrected positive object-control admission and airborne returned side bits; native matching remains open |
| GRAVITY ROOM: `$60` `Obj_DEZBumperWall`, 10 act 1 placements in three shapes | `Obj_DEZBumperWall`, sonic3k.asm:95958-96082, launch `sub_49848`/`loc_49850` :96045-96080 | 320 | `TestS3kDezBumperWallHeadless` | implemented (`S3kDezBumperWallObjectInstance`) | pass 4/4 | Four assertions, eleven deliberate breaks. Subtype 0 is the `$17`x`$20`/`$21` wall (records 311/312), a positive subtype a `$13`-wide post whose height is the subtype itself (305/306 and 329/330 at `$18`, 327/328 at `$38`), and a negative one the room's exit gate (331/332), which moves itself to `$7F00` the update all six `$61` panel bits are set. Production closed-gate bounce and six-panel opening now checked alongside the room. Shared ROM side bits include airborne contact; native comparison remains open |
| TRAVERSAL: `$55` `Obj_DEZEnergyBridge`, 13 act 1 and 12 act 2 placements in six subtypes | `Obj_DEZEnergyBridge`, sonic3k.asm:93909-93990, subtype decoder `sub_47DDE` :93879-93902 | 320 | `TestS3kDezEnergyBridgeHeadless` | implemented (`S3kDezEnergyBridgeObjectInstance`) | pass 11/11 | Eleven assertions, twelve deliberate breaks. Landing it, together with the route carrying the ROM's `Level_frame_counter`, moved the seeded act 2 frontier from 527 to 616 frames. One coverage limit is stated in the suite: a headless fixture has no pattern renderer, so the off-state draw gate is not observable there. Filmed: `039-energy-bridge-320` |
| BADNIK: `$A4` `Obj_Spikebonker`, 7 act 1 placements | `Obj_Spikebonker`, sonic3k.asm:198893-199124 | — | `TestSpikebonkerBadnikInstance` | implemented (`SpikebonkerBadnikInstance`) | pass 10/10 | Shared with act 2, where landing it moved the seeded route frontier from 390 to 472 frames. No act 1 clip: no act 1 route reaches one yet |
| BADNIK: `$A5` `Obj_Chainspike`, 6 act 1 and 12 act 2 placements | `Obj_Chainspike`, sonic3k.asm:199132-199420 | 320 | `TestChainspikeBadnikInstance` | implemented (`ChainspikeBadnikInstance`) | pass 9/9 | Nine assertions, twelve deliberate breaks. Landing it took the seeded act 2 route past the end of its 1200-frame window: the frontier is now 1256 frames and the first divergence is a shared `$08` platform ride, not a Death Egg object. Filmed: `040-chainspike-800` |
| HAZARD: SKL `$6D` invisible shock block | `Obj_InvisibleShockBlock` bit 5; `sub_1F58C` shield reaction, status bits 0/1 choose face | unit: all four flip combinations and five shield states; placed DEZ1 floor: native Sonic 320 | `TestSonic3kInvisibleHurtBlockHObjectInstance`, `TestS3kDezShockBlockHeadless`, placement census | implemented after `674a99f72` | unit and five shield/rewind cases pass | Act-2 placement census verified; act-2 inverted-contact routes, donor/team breadth and native comparison remain open |
| HAZARD: SKL `$52` lightning | `Obj_DEZLightning`, `loc_478BE`–`loc_4791A`; ROM animation `$47926`, map `$4792E` | direct timer cases 0/1/$24/$FF; native Sonic 320 contact | `TestS3kDezLightningHeadless`, placement census and PLC registry | implemented after `de73bead8` | ROM animation/art, previous-list contact and rewind checks; engine clip `050` | Native comparison and donor/team/inverted-contact breadth remain open |
| TRAVERSAL: SKL `$50` conveyor belt | `sub_47854`: unsigned X/subtype window, Y ±$30, grounded only; above/below chooses ±2 pixel carry | unit: both native player slots, all flips and edges; placed DEZ1 belt 320/800 | `TestS3kDezConveyorBeltObjectInstance`, `TestS3kDezConveyorBeltHeadless` | implemented after `bdfd129ff` | carry and restore/forward replay checked | Native comparison, act-2 route and donor breadth remain open |
| HAZARD: SKL `$4D` torpedo launcher | `loc_471D6` visible countdown, `loc_4726C` recoil, `loc_4728A` independent projectile | subtype 0/1/$FF, both directions, full SST pool; production player-contact spot | `TestS3kDezTorpedoHeadless`, census and PLC registry | implemented after `222473083` | ROM art/timers, failure path, same-pass movement and damage restore/replay checked | Native comparison, donor/team breadth and cold route remain open |
| TRAVERSAL: SKL `$4F` staircase | `loc_476EA`–`loc_47814`: four solid SST sections, standing/underside trigger delays, signed word step rounding | all flips, upward/downward and shake variants; actual DEZ1 landing at 320/800 | `TestS3kDezStaircaseHeadless`, placement census | implemented after `35390ba3c` | timer/art checks, forced graph recreation and placed trigger/carry replay | Native comparison and team/donor breadth remain open |
| TRAVERSAL: SKL `$5E` hover machine | `loc_494EA` / `sub_4952A`: old-angle orbit/priority and horizontal-offset lift envelope | native P1/P2 and actual DEZ1 `$5B8,$8D0` at 320/800 | `TestS3kDezHoverMachineObjectInstance`, `TestS3kDezHoverMachineHeadless` | implemented after `46be7745e` | allocation exhaustion, scalar recreation, native-clock sound, placed lift and 180-frame replay | native comparison, cold route and donor/character breadth remain open |
| TRAVERSAL: SKL `$4C` hang carrier | `loc_46FC2`–`sub_4703E`: P1 start, accelerated rise, native ceiling probe, finite horizontal travel, P1/P2 grabbing | unit contact/control/input boundaries; actual DEZ1 ride at 320/800 | `TestS3kDezHangCarrierObjectInstance`, `TestS3kDezHangCarrierHeadless` | implemented after `b1c767647` | real terrain, jump release and forced recreation/forward replay | native comparison, Act-2 entry and donor/character breadth remain open |
| TRAVERSAL: SKL `$56` curved energy bridge | `loc_47F2C`–`sub_47F9C`: phase timer, P1/P2 collision paths, expiry air bit | native contact-edge tests; actual `$880,$920` at 320/800 | `TestS3kDezCurvedEnergyBridgeObjectInstance`, `TestS3kDezCurvedEnergyBridgeHeadless` | implemented after `33e6b66d5` | timer/path transitions, forced recreation/300-frame replay, and controller-driven curve-to-carrier approach/replay at 320/800 | native comparison, cold route and donor/character breadth remain open |
| TRAVERSAL: SKL `$4B` tilting bridge | `loc_46E1C`–`loc_46F54`, signed ROM `byte_46ED8`, prior-standing aggregate, long velocity and delayed free fall | all eight standing rows, P1/P2 sum/cancellation, exhausted forward allocation; DEZ1 320/800; inverted DEZ2 declared entry | `TestS3kDezTiltingBridgeHeadless`, census, PLC registry | implemented after `6b7055cea` | nine focused checks: real landing/carry/collapse/floor release, complete graph recreation and forward replay; inverted carry/replay | Cold route, native comparison and donor/roster breadth remain open |
| BOSS: `$A6` miniboss | `word_7DDA4` range Y `$18C`-`$38C` X `$3400`-`$3780`; arena `$28C,$28C,$3680,$36C0`; two eight-hit phases | native Sonic 320/800 entry; isolated graph at 320 | `TestDezMinibossEncounter`, `TestDezMinibossTransport`, component suites | implemented in development | both eight-hit phases, real placed entry and graph replay pass; continuous transition under validation | Cold route, native comparison, allocation prefixes and roster/donor breadth remain open |
| ROUTE (Sonic + Tails cold): `$B00` entry → results | Complete-run BK2 from movie frame 468982 (segment directory `ssz`, `zone_id 11`) | native 320 | `TestS3kDezColdRoutes` | missing | unrun | Slice 6 |
| ROUTE (Tails cold) | `runs/s3k-tails-full-chain-all-emeralds` `ssz`, offset 444059 | native 320 | `TestS3kDezColdRoutes` | missing | unrun | Slice 6 |
| REWIND: cycle counters, event routine words | Registry restore equals capture plus forward replay | 320 | `TestS3kDezPresentationRewind` (2 tests) | implemented | pass, `4e7655bf9` | Entry, object and load-boundary spots not started |
| ORACLE: route timing | `runs/s3k-sonic-tails-complete-emeralds/ssz` (DEZ, `zone_id 11`, 40,049 rows, offset 468982; both acts and the handover) | — | `TestS3kSonicTailsSszSegmentTraceReplay` (expected red) | — | blocked: 7005 errors, first error frame 0 `camera_x` expected `0x0040` actual `0x0000` (`035e48a58`, `-Ptrace-replay-r7`) | Whole campaign |
| ORACLE: Tails route timing | `runs/s3k-tails-full-chain-all-emeralds/ssz` (act 1, 23,249 rows, offset 444059) | — | `TestS3kTailsFullChainSszSegmentTraceReplay` (expected red) | — | blocked: 1661 errors, first error frame 0 `camera_x` expected `0x0040` actual `0x0018` (`035e48a58`) | Whole campaign |

## Conveyor-pad follow-up (2026-09-23)

SKL `$53` now implements `loc_479F0`–`sub_47B58`: delayed rider activation,
native-slot conveyor carry with gravity reversal, finite vertical travel,
horizontal floor following, delayed gravity fall, wall turns and ROM animation.
`TestS3kDezConveyorPadHeadless` has eleven checks, including actual horizontal
and vertical rides at 320/800, forced horizontal-pad recreation and replay,
and an inverted Act-2 placement's underside ride/replay. The S3KL `$53` MGZ
platform remains unchanged. Placement counts are now 354/365 and 489/494.
Native comparison, cold routes and donor/character breadth remain open.

## Widescreen background follow-up (2026-09-23)

`TestS3kDezWidescreenBackground` covers 320/352/400/528/800. Act 1
retains the native centre without repeating the complete backdrop. Act 1
reflects outer-wall tiles; Act 2 continues the planet's curve using ROM
surface pixels. The extra scenery is a deliberate presentation extension.
The planet projection also checks palette-fade independence, restore and
act-load isolation. Positioned final 800-pixel clips 078/079 in the external DEZ
task directory show six seconds each, with zero hurt/death rows. These
checks do not certify a cold route or native pixel parity. Study 080 also
checks all four narrower widths in gameplay; the native frame-120 PNG matches
the earlier engine capture byte-for-byte.

## Execution evidence

Worktree `.worktrees/ai-s3k-dez-bring-up`, ROM by absolute path, `maven_queue.py -Dmse=off`.
Frontier command (2026-09-17, `035e48a58`):
`python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=<abs>/s3k.gen -Ptrace-replay-r7 "-Dtest=TestS3kSonicTailsSszSegmentTraceReplay,TestS3kSonicTailsDez238SegmentTraceReplay,TestS3kTailsFullChainSszSegmentTraceReplay,TestS3kTailsFullChainSsz2SegmentTraceReplay,TestS3kTailsFullChainSsz3SegmentTraceReplay,TestS3kTailsFullChainDez238SegmentTraceReplay" -DfailIfNoSpecifiedTests=false test`
— 6 tests, 6 failures, 0 errors, **0 skipped** (the ROM path resolved).

## Lift-pad follow-up (2026-09-23)

SKL `$4E` now implements `Obj_DEZLiftPad`, `sub_4748E` and `sub_4757A`:
P1 standing starts the accelerating arm; a standing P1 holds the endpoint's
30-frame return pause. P2 alone does neither. Seven checks cover orientation,
independent forward allocation and failure, sprite-coordinate aliasing, the
complete out/pause/back cycle, ROM art and actual placed rides at 320/800.
Forced parent/arm recreation and forward replay match, including jump release.
Counts are 361/365 and 489/494; tunnel launchers and the bosses remain open.
External clips 086/087 show seven seconds at 320/800 with no hurt/death rows.
These positioned component checks do not establish cold routes, native parity
or donor/character/team breadth.

## Light-tunnel implementation follow-up (2026-09-23)

SKL `$57` includes independent launcher/controller/trail/ring slots. The ROM's
P1-only countdown, P2 waiting, byte timers, fixed-point lines, both circle sizes,
both sine directions and preserved fraction-word curve centres are implemented.
`TestS3kDezTunnelLauncherHeadless` checks all eight ROM path endpoints, the
first circle's exact position/fraction/velocity writes, allocation exhaustion,
P2 versus extra followers, ring-animation termination and actual Act-1 entry
at 320/800 with countdown, transport graph recreation, release and exit replay.
The S3KL MGZ trigger-platform alias retains its own implementation and checks.
Placement counts are 364/365 and 493/494: each act's boss remains a placeholder.
Native per-mode cadence, all seven cold entries, Act-2 positioned traversal,
full character/donor/team breadth and the final-boss route remain open.

Miniboss preparation after `595ec0cd9`: eye/art/palette owners and orb/explosion,
arm/spike, beam/feet and debris components have focused checks. The latest child
selection passes 24 tests with no skips, including actual platform contact and
release and recreated chain/cross-link graphs. These use minimal test parents;
A6 remains a placeholder until the real root, sign/results, surviving transport
and seamless act transition are connected. The BOSS row remains missing, with
native/widescreen moving evidence and character/donor/team breadth outstanding.

### Root and Act-2 handoff connection (2026-09-23, development)

The missing BOSS row above is now partially covered by the connected root and
transport tests. A separate production-queue reload test checks the actual
results signal, coordinate/camera rebase, gravity retention, retained palette
lines, the eight-pass background rewrite and forward replay. The 320/800
positioned opening recordings (093/094 in the external DEZ capture directory)
run 900 frames with matching player damage/motion and no deaths. They are
opening-cycle evidence, not a completed fight or cold-route certificate.

A controller-driven Hyper probe reached all sixteen hits through normal touch
handling and then exposed the invisible transport's inherited world-position
flag at reload. The transport now explicitly keeps native render_flags bit 2
clear; its connected regression passes for left, centre and right finishing positions.

The final positioned solo Hyper recordings 098 (800px) and 099 (320px) replay
the same authored controller log for 4000 frames with no hurt/death and no
follower. They include both phases, results, the reload, floor opening, launch,
landing and released control. The first player-state difference between widths
is the resource-gated reload (frame 1653 wide / 1652 native); results elements
then take eight additional passes to leave the wider viewport before transport.
Landing is 2964 wide / 2956 native. The native queue-duration oracle remains open;
no delay was fitted to align these captures. Native pixel/clock comparison and
non-Hyper/player-roster/donor routes are still owed.


### Cold upper traversal (2026-09-24)

`routes/s3k/dez1-sonic-tails-cold-upper-320.{script,bk2}` preserves5,301 ordinary
controller frames from the actual DEZ1 entry. It reaches(9589,640),32rings on the
upper moving pad, with zero deaths. The route traverses energy bridges, the
opening hover/lift sequence, two lift pads, tubes, conveyor elevators, the timed
three-bridge gap and the high spring launch. It does not reach the turbine room,
miniboss or Act2 and is not native parity evidence.

`TestDezColdRouteCapture` checks the live Sonic+Tails roster, alive traversal,
endpoint and21 full-registry45-frame restore/replay spots:
650,1260,1700,2100,2182,2400,2500,2640,2700,3050,3150,3240,3750,4110,4300,4370,
4450,4650,4840,4950,5200. Queued Java21/native GL with the absolute S3K ROM and
`-Dmse=off -Dtest=TestDezColdRouteCapture` passed1test,0failures/errors/skips on
c4e4f78aa plus the route/test additions. An initial invocation loaded the input
before its final5301-frame truncation and failed its endpoint; the final authored
movie was rerun successfully. No production change was made for this slice.

The fixed input was compiled and round-tripped with `InputLogAuthorTool`.
`campaign-20260924-cold-upper-320/capture.mp4` in the external DEZ archive shows
frames2910–5300 from cold boot: conveyor lift, stair/platform traversal, timed
bridge crossing, spring launch, elevator and upper moving pad. All5301 state rows
show zero deaths; selected stills were inspected and the MP4 decoded completely.
The next cold frontier is leaving the upper moving pad at X9589, then the remaining
act traversal. Native-width team evidence does not close wide/donor/solo routes.


### Cold turbine completion and door correction (2026-09-24)

`routes/s3k/dez1-sonic-tails-cold-turbine-320.{script,bk2}` extends the independently
retained upper route to8593frames. Native Sonic+Tails320 reaches(10763,2096),17rings,
zero deaths, all six panels pressed and movement released beyond the real exit
door. No position, health, panel or object-state writes occur during the route.
The input-authoring tool validates the script/BK2 round trip.

The cold route exposed `DoorObjectInstance` rejecting any object control. ROM
`sub_30F58` uses `TST.B object_control / BMI`, so positive turbine `$01` must open
the door; only bit7 rejects it. The shared door trigger now uses the existing
bit7 predicate. Its focused regression covers vertical and horizontal doors,
negative rejection and positive acceptance, and failed before the correction.
The mirrored object pitfall note no longer equates all control with bit7.

`TestDezColdRouteCapture` retains the short upper test and adds the connected
six-panel/door route. Nineteen new45-frame replay spots join the original21:
5540,5620,5800,5960,6100,6330,6410,6520,6760,6890,7290,7460,7750,7840,8100,8260,
8400,8470,8540. All40 compare the registered world after restore and forward play.
Queued Java21/native GL/absolute S3K ROM verification on34a51c401 plus this patch:
`-Dtest=TestDoorObjectInstance,TestS3kDezGravityRoomHeadless,TestDezColdRouteCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
passed88tests without skips before the extended route was added; the final
`-Dtest=TestDezColdRouteCapture` passed both cold routes,0skips. Both used `-Dmse=off`.

`campaign-20260924-cold-turbine-exit-320/capture.mp4` films7840–8592 after cold boot;
all8593 state rows, zero deaths, selected stills and full MP4 decode checked.
The next frontier starts beyond the door. Remaining act traversal, miniboss,
cold Act2 transition and width/character/donor breadth are still open.

### DEZ1 ordinary cold completion and detached-child rewind (2026-09-24)

On `23bf09e25` plus this change, `dez1-sonic-tails-cold-complete-320.bk2`
extends the turbine route through the launcher/conveyor ascent and the ordinary
miniboss. Both phases receive eight real hits; the second phase is completed by
retreating between arm sweeps, not by altering the boss. The actual Act 2 load
occurs at input14230 (14,231 total frames), with Sonic+Tails and no deaths,
position/health/emerald overrides or native-state hydration. The authored input
script is compressed by held-button runs; the BK2 contains the same frames.

Fresh snapshots after projectile retirement exposed dangling creator references:
`loc_7E916`/`loc_7E972` fragments never read a parent, and
`CreateBossExp00`/`CreateBossExp06` use stationary copied positions rather than
`Obj_WaitForParent`. The engine now omits those unused live references. Actual
follower bursts still retain their parent. The cold test first failed on an orb
fragment reference and then on the final finite burst after boss deletion;
neither failure was a sprite-priority defect. The shorter hazard regression now
captures and replays after the creator has disappeared as well as before it.

The complete route adds22 full-registry45-frame replay spots at8800,8900,9350,
9490,9650,9930,10070,10500,11080,11260,11420,11570,12190,12320,12470,12640,
12740,13100,13270,13860,13950,14100. The earlier upper/turbine tests retain40
independent spots. Live history is armed after those probes to test the actual
load boundary. Seamless transitions retain the logical frame counter and re-root
the oldest seekable snapshot; a first test incorrectly expected a zero counter.

Media: `$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-cold-complete-320/capture.mp4`
films11250–14349 after the full cold prefix. All14,350 state rows contain no death;
selected combat/defeat/arrival stills and the complete MP4 decode were inspected.
This establishes ordinary native-width reachability, not native pixel parity or
other width/character/donor coverage. DEZ2 cold traversal and the campaign's
remaining matrix obligations continue separately.

Focused verification used queued Java21, native GL and the absolute S3K ROM:
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestDezMiniboss*,TestDezColdRouteCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`:
108 tests,107 passed, one test-oracle failure at the seamless counter assertion,
zero skips. After correcting only that assertion, the focused
`-Dtest=TestDezColdRouteCapture#coldCompleteRouteDefeatsBothMinibossPhasesAndLoadsActTwo`
passed1 test, zero skips. No remaining failure in that selection; this is focused
validation, not a combined campaign suite pass.

The post-fix `campaign-20260924-cold-act2-arrival-320/capture.mp4` continues
through the floor opening, launch and free control at(320,940), filming13840–15239.
All15,240 state rows are death-free; stills14840/15030/15230 and full video decode
were inspected. This additional clip uses the same cold input followed by neutral.


## Ordinary Sonic solo cold completion (2026-09-25)

After `dfe7baae1`, `dez1-sonic-solo-cold-complete-320.{script,bk2}` preserves
23533 ordinary controller inputs from cold DEZ1 through both eight-hit miniboss
phases, the real seamless Act2 load at22332 and incoming free control at(320,940).
Native320, donoroff, Sonic alone, no transformations, gameplay seeds or deaths.
`TestDezSoloColdRouteCapture` asserts the actual solo roster throughout, both
phase completions, load, control release and outgoing-history isolation, plus61
full-registry immediate restore and45-input replay windows spanning traversal,
turbine, launchers, staircase, fight, defeat and incoming transport.

Focused command from the task worktree, Java21 and `DISPLAY=:0`:
`python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestDezSoloColdRouteCapture test`.
Result:1 test passed,0 failures/errors/skips,18.89s test/40.572s Maven.
The change-based plan against `dfe7baae1` selects2919 ordinary classes plus guards
because recorded input files are unclassified. For this test/input-only milestone,
the full cold production route and61 replay windows directly exercise the new
contract; proportionate focused validation applies. Shared runtime changes in the
larger campaign still require combined broad verification before integration.

Fresh `GameplayCaptureTool` playback matches all22333 authored prefix rows on12
player-state fields; all23533 capture rows are death-free and have no follower.
Video `$HOME/Videos/OGGF/s3k-dez-bring-up/campaign-20260925-sonic-cold-act1-clear-320/capture.mp4`
shows inputs20300–23532,3233 frames at60fps,53.883333s. Full decode and selected
combat/arrival stills20600/21800/23532 pass inspection. This is engine presentation
evidence, not native pixel parity. Solo Act2/final, Tails and remaining breadth
remain open.

Method: the team route first diverged at the conveyor; ordinary input timing
resolved the jump, turbine, launcher chain and rising stair. Direct boss policies
died after two hits; a forecast policy with excessive damage cost avoided attacking.
A short-horizon policy forced every zero-ring option toward one ring and died after
four hits. The accepted controller searches only ordinary inputs using engine-owned
rewind snapshots, a180-frame forecast and20-frame committed segments; keeping both
ring-recovery and free movement alternatives clears the fight. No gameplay state
was synthesized, and independent playback plus full replay checks establish that
the saved input is sufficient without the search. No runtime change was justified.
