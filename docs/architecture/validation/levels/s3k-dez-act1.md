# S3K Death Egg Zone act 1 coverage matrix

Routine selection update (2026-10-27): the reviewed full configuration products
and long cold-campaign qualifications in the [local budget record](../../research/2026-10-27-local-validation-cost.md#thirty-minute-local-command-target)
now require explicit `exhaustive-audit`. Short real contact, lifecycle and
restore/forward witnesses remain routine. Historical qualification rows retain
their original source/input scope; a current smoke pass does not renew deferred
cold reachability, accumulated route state or every replay window.

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

LevelSizes (sonic3k.asm:38159): x `0`-`$6000`, y `0`-`$B20`. Start location `$30,$9AC`
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

Physical starpost activation, rewind and repeated death/reload breadth is now
covered by the 2026-09-26 follow-up below; other lifecycle obligations remain open.

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
| GRAVITY: `$5A` gravity tube, 24 act 1 placements | `loc_48EEC`/`sub_48F12` and `loc_4906A`/`sub_49090` (sonic3k.asm:95215-95447) | — | `TestS3kDezGravityTubeHeadless` | implemented (`S3kDezGravityTubeObjectInstance`) | pass 11/11 | Behaviour is act-independent; the tests place it at the act 2 site beside the `$5B` at `$1A40,$08C0`. No act 1 route reaches one yet (slice 6) |
| GRAVITY: `$5F` turbine corridor, 1 act 1 placement (`$2480,$0840`) | `sub_4964A`, sonic3k.asm:95860-95998 | — | `TestS3kDezGravityRoomHeadless` | implemented (`S3kDezGravityRoomObjectInstance`) | pass 14/14 at 21:41 BST after `bf7e5175c` | Includes actual placed-controller lifetime, unsigned range edges, puzzle/closed-gate contact, production rewind and 320 px six-panel controller route and actual 320/800 closed-gate bounce; cold entry, native matching and team/donor breadth remain open |
| GRAVITY: `$61` gravity puzzle, 1 act 1 placement (`$2690,$0840`, inside the `$5F` corridor) | `Obj_DEZGravityPuzzle`, sonic3k.asm:96133-96291 | — | `TestS3kDezGravityPuzzleHeadless` | implemented (`S3kDezGravityPuzzleObjectInstance`) | pass 12/12 | Twelve mechanisms, fifteen deliberate breaks, every one red. The six panel bits live in `S3kDezZoneRuntimeState` because the ROM keeps them in `MHZ_pollen_counter`, a level RAM byte. Filmed standing on the solid top (`036`), airborne bounce (`043`/`044`) and six-panel controller traversal (`045`). Corrected positive object-control admission and airborne returned side bits; native matching remains open |
| GRAVITY ROOM: `$60` `Obj_DEZBumperWall`, 10 act 1 placements in three shapes | `Obj_DEZBumperWall`, sonic3k.asm:96004-96128, launch `sub_49848`/`loc_49850` :96091-96126 | 320 | `TestS3kDezBumperWallHeadless` | implemented (`S3kDezBumperWallObjectInstance`) | pass 4/4 | Four assertions, eleven deliberate breaks. Subtype 0 is the `$17`x`$20`/`$21` wall (records 311/312), a positive subtype a `$13`-wide post whose height is the subtype itself (305/306 and 329/330 at `$18`, 327/328 at `$38`), and a negative one the room's exit gate (331/332), which moves itself to `$7F00` the update all six `$61` panel bits are set. Production closed-gate bounce and six-panel opening now checked alongside the room. Shared ROM side bits include airborne contact; native comparison remains open |
| TRAVERSAL: `$55` `Obj_DEZEnergyBridge`, 13 act 1 and 12 act 2 placements in six subtypes | `Obj_DEZEnergyBridge`, sonic3k.asm:93955-94036, subtype decoder `sub_47DDE` :93925-93948 | 320 | `TestS3kDezEnergyBridgeHeadless` | implemented (`S3kDezEnergyBridgeObjectInstance`) | pass 11/11 | Eleven assertions, twelve deliberate breaks. Landing it, together with the route carrying the ROM's `Level_frame_counter`, moved the seeded act 2 frontier from 527 to 616 frames. One coverage limit is stated in the suite: a headless fixture has no pattern renderer, so the off-state draw gate is not observable there. Filmed: `039-energy-bridge-320` |
| BADNIK: `$A4` `Obj_Spikebonker`, 7 act 1 placements | `Obj_Spikebonker`, sonic3k.asm:199000-199231 | — | `TestSpikebonkerBadnikInstance` | implemented (`SpikebonkerBadnikInstance`) | pass 10/10 | Shared with act 2, where landing it moved the seeded route frontier from 390 to 472 frames. No act 1 clip: no act 1 route reaches one yet |
| BADNIK: `$A5` `Obj_Chainspike`, 6 act 1 and 12 act 2 placements | `Obj_Chainspike`, sonic3k.asm:199239-199527 | 320 | `TestChainspikeBadnikInstance` | implemented (`ChainspikeBadnikInstance`) | pass 9/9 | Nine assertions, twelve deliberate breaks. Landing it took the seeded act 2 route past the end of its 1200-frame window: the frontier is now 1256 frames and the first divergence is a shared `$08` platform ride, not a Death Egg object. Filmed: `040-chainspike-800` |
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


## Ordinary Tails solo cold completion (2026-09-25)

After `6dfdbc5a4`, `dez1-tails-solo-cold-complete-320.{script,bk2}` preserves
29521 ordinary inputs from cold entry through both eight-hit miniboss phases,
the actual seamless Act2 load at28320 and released Tails at(320,944). Native320,
donoroff, no follower, transformations, gameplay seeds or deaths. The solo route
class now shares its phase/load/roster/history assertions between Sonic and Tails;
Tails adds75 full-registry immediate restore and45-input replay windows. These
span lower opening/flight, conveyor, upper pads, lower corridor, turbine panels,
launchers, rising stair, both fight phases and incoming transport. Sonic retains61.

Queued Java21 with `DISPLAY=:0`:
`python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestDezSoloColdRouteCapture test`
passes2 tests,0 failures/errors/skips,77.77s test/1:43 Maven. The change-based
plan against `6dfdbc5a4` selects2919 ordinary classes plus guards because route
inputs are unclassified; focused validation is proportionate for this test/input
change. Both consumers of the shared test helper were rerun. This is not a new
broad-suite pass; combined campaign validation remains owed for runtime changes.

Fresh production playback independently matches all28321 author rows on12 player
fields. All29521 capture rows have no deaths or followers. Video
`$HOME/Videos/OGGF/s3k-dez-bring-up/campaign-20260925-tails-cold-act1-clear-320/capture.mp4`
shows26400–29520 (3121 frames,60fps,52.016667s); full decode and
stills26600/27962/29520 were inspected. The separate21.666667s approach clip
(`campaign-20260925-tails-cold-miniboss-approach-320`) matches19740 cold rows.
Neither clip is native pixel parity. Mandatory native cold Act1 roster is now
covered for Sonic+Tails, solo Sonic and solo Tails; solo Act2/final and remaining
width/donor/lifecycle/native presentation obligations remain open.

Input lessons: Tails needs the lower opening return and repeated ordinary flight
inputs, not the team movie verbatim. Leaving the upper pad at the wrong phase
returns to the upper ledge; the lower passage needs a wait before heading east.
A flight clears the lower spike corridor, then turbine steering with longer
up/down holds reaches all six panels. Flying over the second launcher fails;
braking beside it and walking into its capture window preserves the intended
launch chain. The accepted boss search records inputs only, uses engine-owned
rewind for forecasts, and is independently replayed without the controller.
No runtime change was needed for this milestone.


### Physical checkpoint lifecycle coverage (2026-09-26)

`TestDezCheckpointRoutes` independently inventories all seven ROM-authored DEZ
starposts from `Levels/DEZ/Object Pos/{1,2}.bin`; this act owns posts 1/2/3.
Declared local approaches activate the real placed objects with ordinary input,
then capture/restore and replay complete registry state twice. These approaches
are not evidence of cold-route reachability. Post 6 uses a short drop onto its
ledge: the generic 48px-left setup fell below the post before contact.

Each post receives two real GameLoop death/reloads for native Sonic, Tails,
Sonic+Tails and level-select Knuckles at widths 320/352/400/528/800, the supported
S1/S2 donor subset, S1 Sonic+Sonic at all five widths, and native Sonic+Tails+Knuckles
at800. Checks bind the actual roster, checkpoint position/index, new object/runtime
owners, participant ROM renderers/animations, donor rules, viewport, title/fade
release and outgoing rewind-history isolation. The initial load is positioned;
checkpoint state is never injected. Pit death is a declared production stimulus.

At Act2 post5, ordinary Right input first crosses the placed `$5B` gravity swap at
`($1A40,$8C0)` in both cycles. Gravity must be active before death and clear at
reload. ROM `loc_60DE` clears `$F700..$F7FF`, including `Reverse_gravity_flag` at
`$F7C6`; the starpost save bank does not restore it. Existing engine behavior passes
this contract without a runtime change. Native visual comparison and other route,
encounter and lifecycle obligations remain separate. See the campaign audit for
combined validation and integration results.


### Seamless sprite-publication follow-up (2026-09-27, candidate)

`TestLevelSpritePresentationLifecycle` independently exercises FBZ/MHZ/SOZ/LRZ/
DEZ reloads: pending/published SAT and HUD counters survive, while fresh loads
still clear them. Shared validation and exact route limitations are recorded in
[the campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md#seamless-sprite-publication-and-mhz-scroll-carry--2026-09-27).
This adds transition coverage, not another whole-act or native-pixel certification.


## 2026-09-28 — cold Sonic 800px Act 1 boss arrival

Controller-only authoring on integrated `f411b75db` extends the previously
reproduced6,884-input southwest prefix to17,230 inputs and the real Act1 boss.
No runtime code or seeded position/rings/health/clock changed. Sonic solo,
800px, donor off, cold DEZ1, no settle step. The winning full script/BK2 lives
in the external task evidence directory below; this remains an authoring
frontier, not a completed act or whole-world rewind acceptance row.

The lower route required earlier braking, repeated jumps through the hazardous
conveyor and a jump across the next gap. Turbine steering initially left panel
bits at0x39. Temporary read-only panel diagnostics identified the missing left
middle/bottom contacts: a downward approach changes0x39→0x3b→0x3f and releases
the gate. Short upper-staircase jumps fall back into the lower route; a longer
ordinary run-up reaches the upper corridor. The fixed native-width fight tail
then dies at17,963; controller-policy exploration is unfinished. This input
failure is not evidence for a boss implementation change.

Independent uninterrupted production captures match every author row on twelve
player fields with zero deaths:

| Frontier | Rows | Video (60fps,1600x448) | Final state |
| --- | ---: | --- | --- |
| Turbine entrance | 7,795 | 995 frames,16.583s | (9912,2100),8 rings |
| Turbine gate released | 14,450 | 1,450 frames,24.167s | Past the real gate |
| Act1 boss arrival | 17,230 | 2,090 frames,34.833s | (14234,749),15 rings |

All three videos completely decode. Evidence root:
`$HOME/Videos/OGGF/s3k-dez-bring-up/authoring-20260928-wide-cold/`.
Inputs are `turbine-7795`, `turbine-clear-14450` and `boss-arrival-17230`
(`.script/.bk2`); movie subdirectories are `fresh-prefix`, `turbine-clear` and
`boss-arrival`, each with `capture.mp4` and `state.csv`. Engine rendering is not
original-emulator pixel parity. Next: author and independently verify the boss
clear, continue the real Act2 chain, then promote the completed route and its
short/whole-world rewind obligations through the normal delivery workflow.


## 2026-09-28 — Sonic cold 800px Act 1 completion and turbine contact rewind

On base `121837f86`, branch `feature/ai-dez-wide-cold-route`, frozen
`src/test/resources/routes/s3k/dez1-sonic-cold-800.script/.bk2` contains21,100
ordinary inputs from cold DEZ1, Sonic solo/800px/donor off/no settle or state
seeds. Both eight-hit phases are defeated, the actual Act2 load occurs at19,889,
and the incoming transport releases control before the final ordinary movement.
This completes this Act1 product, not the remaining Act2 route or other products.

The first snapshot-based boss author (180-frame horizon,12 policies) died at
18,252 after three hits. A300-frame horizon and18 policies, including ordinary
jump release/repress inputs, reach the second phase's fatal hit at19,532.
These are controller-author choices, not fitted gameplay values. The method is
preserved as `DezMinibossInputAuthorTool`; its observer distinguishes the first
phase's eight-hit counter from final defeat and never treats absence as victory.
Fresh ordinary playback reproduces the winning input and the real Act2 load.

The initial20,150-input recording stopped during incoming
results/transport. It established the boss clear but did not establish released
control. The accepted21,100-input recording extends the same buttons through
release. `TestDezSoloColdRouteCapture` adds58 whole-registry restore/forward
windows, each replayed twice, across traversal, all six panels, staircase,
both boss phases, defeat/results, load and incoming release. No interval crosses
the load. Live history separately verifies outgoing snapshots are discarded
while the seamless frame numbering continues. Native Sonic/Tails routes remain.

At13,174, the new route exposed `S3kDezGravityPuzzleObjectInstance` clearing
`playerOnePushedThisUpdate` after the generic codec had restored it. The immediate
snapshot diff was scalarData[4], expected1/actual0. That flag models the `a1`
owner in `loc_499EC`: shipped `FixBugs=0` lets P2's same-pass contact mark P1's
panel. The repair removes only the post-restore clear; the next ordinary update
still clears it. An independent short two-player test restores between contacts
and checks both native ownership and its normal next-update reset. No forward
physics, collision, panel selection or visual timing changed.

Validation plan: the change-based runner selects all2,939 ordinary classes
because the author tool and route resources are shared/unclassified. Impact is
bounded to a standalone diagnostic tool, tests and one object's restore hook;
no shared codec, timing or runtime contract changed. Proportionate validation
covers the short two-player behavior, complete puzzle/room tests, both native
cold consumers, the new whole-world route and author phase-accounting tests.
The first candidate selection completed65 tests with one exact wide-route
restore failure above and no skips; all59 mandatory S3K loading/bootstrap cases
and both native routes passed. After the repair and input extension,
`-Dtest=TestDezSoloColdRouteCapture,TestDezMinibossInputAuthorTool,TestS3kDezGravityPuzzleHeadless,TestS3kDezGravityRoomHeadless`
passes33 tests,zero failures/errors/skips,1m26 Maven. Java21, DISPLAY=:0,
absolute root S3K ROM, serial Maven queue. This is focused validation, not a
full-suite result. Destination verification/integration remain to be recorded.

Fresh engine captures on unchanged forward runtime `f411b75db`:
`$HOME/Videos/OGGF/s3k-dez-bring-up/campaign-20260928-sonic-cold-act1-clear-800/`
(2,950 frames,60fps,1600x448,49.167s) and
`$HOME/Videos/OGGF/s3k-dez-bring-up/campaign-20260928-sonic-act2-release-800/`
(2,232 frames,60fps,1600x448,37.2s). The latter independently runs22,032 inputs,
zero deaths; all20,150 shared rows match the first capture on every CSV field.
The first19,890 rows also match the input author on twelve player fields.
The retained21,100-input route is a prefix of that verified release capture.
Complete decode and selected boss/release stills checked. The boss currently
uses the original left-anchored arena bounds, with the automatic mask on its
right; centering/presentation acceptance is not newly claimed by this route.
Original-emulator pixel parity and remaining viewport/donor/roster breadth stay
open in the matrix. All earlier frontier-only notes are superseded for this
specific Act1 route; the full seven-zone campaign remains open.

The promoted author also cold-replays the frozen prefix and independently reports
`DEFEATED` at19,532. Final author-only tests pass5 cases,zero skips,1m12 Maven,
including invalid-prefix rejection before boot. The original33-case route/domain
pass remains valid; the later edit only validates this diagnostic CLI argument.


Integrated on `develop` as `ee8e470e4` (source `f733fddb8`), with no upstream
changes or merge conflicts. Destination command (Java21, DISPLAY=:0, absolute
S3K ROM, serial queue):
`python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=$OPENGGF_ROOT/s3k.gen -Dtest=TestDezSoloColdRouteCapture,TestDezMinibossInputAuthorTool,TestS3kDezGravityPuzzleHeadless,TestS3kDezGravityRoomHeadless test`.
All35 tests pass,zero failures/errors/skips,2m03 Maven. This is the documented
focused destination validation, not a full ordinary/guard run. Native Sonic,
Tails and the new800px product pass, including58 two-cycle replay windows and
real incoming control/history release. The isolated task branch is fully merged;
cleanup follows the push. Unrelated main-workspace files/submodules are preserved.


### Route drawing policy follow-up (2026-10-09)

The affected cold-route/checkpoint JUnit checks now simulate every input and
service queued render work every tick, with drawing at snapshot/replay and
playable/load checkpoints. Complete forward/replay branches stay fully drawn;
discarded screenshot readback is omitted. Their normal result establishes route
behavior and these drawn checkpoints, not presentation on every intervening frame.
For every-frame drawing use `-Dopenggf.tests.drawEveryFrame=true`. The bounded
state/pixel controls and their limits are described in the
[headless testing guide](../../../guide/contributing/headless-testing.md).
This performance change supplies no new native reference, full-route visual
certification or missing viewport/donor/character coverage; inherited gaps remain.


## Released checkpoint-world replay follow-up (2026-10-14)

The existing physical-post death driver now uses only the production GameLoop
fade owner and services pending render work after each tick. It checks exactly
one life lost at fresh assembly and after actual title/fade/control release,
then captures and replays the complete registered incoming world twice in both
restore directions after EACH of two real deaths. Every45-input branch requires
an alive leader, unchanged lives/post, retained roster/donor/viewport and the
same current runtime/object manager; a raw restore never crosses a load.
Short independent native320/S2-Tails800 witnesses precede the product methods.

This act contributes138 products over all its authored posts and all five
production presets, using four native standard teams, supported S1/S2 standard teams, S1 duplicate Sonic and native800 trio. The composed
`262e9bbb10bb317d183dbf289dcfa8568fa0258c` plus final test changes passes the
12 short witnesses in request93678 at12:58:56 UTC, then all1077 affected
products in63733 at13:13:31 UTC, zero failures/errors/skips (3:32 Maven,
queue wait separate). DEZ2 post5 still reaches its placed gravity writer through
ordinary movement and verifies the real inverted death and native cleared
gravity after reload. This adds incoming-world replay to the previously
qualified physical contact/death scope; other unchanged checks were not repeated.
[Exact selectors and source hashes](../../plans/2026-10-13-s3k-playable-completion.md#existing-checkpoint-driver-qualification-2026-10-14)
are in the completion plan. Final boss-act and further mechanic/cold-route products remain open. No native pixel/audio, overall act
certification, full-suite or combined delivery claim follows.


## Native player-launcher art publication — 2026-10-20

The active $78 factory already supplies launcher motion, SolidObjectTop and
FLOOR_LAUNCHER sound. Its missing DEZ1 presentation entry is now supplied by
Sonic3kPlcArtRegistry: native Obj_FBZDEZPlayerLauncher at $3B942 selects mapping
frame1 at $3BA8A, art base $02FC, palette1. DEZ1 PLC$36 loads
ArtNem_DEZMiniboss there. The provider compacts the selected frame to local0,
so the existing object draw call stays unchanged.

[TestS3kDezPlayerLauncherArt](../../../../src/test/java/com/openggf/game/sonic3k/TestS3kDezPlayerLauncherArt.java)
passes four checks: plan selection, literal full native mapping/PLC/ten placed
$78 records, actual fresh-DEZ1 provider/ready-renderer geometry and six patterns
compared with the ROM decode, and unchanged fresh-FBZ1 frame0 control.
The negative DEZ2 plan check is intentional: fresh PLC$38 does not load the
$02FC resource. No DEZ2 fallback/load was invented.

Qualified on local HEAD691de5183, fullsrc+pom fingerprint
6d16137f01e4c88d142da720def8100c5a9e94eb3131b9edd31b737c431d070b.
Candidate-3 has four maintained checks plus one separate ICZ authoring episode:
5T/0F/0E/0S, Maven28.719s. Earlier compiler/API and fixture-reset errors remain
in the [composition plan](../../plans/2026-10-13-s3k-playable-completion.md#native-ship-and-dez-launcher-art--2026-10-20).
This checks ROM-backed data and prepared rendering, not native GPU pixels,
physical display-list publication, launcher traversal or whole-act certification.
Duplicate $78 registration remains a separate issue.


## Typed boss admission and deferred initialization (2026-10-26)

The [bounded receiving record](../../plans/2026-10-22-native-gap-swarm.md#post-223-dez-boss-admission-and-discovery-receipt-2026-10-26)
qualifies stock-DEZ-only SKL `$A6/$A7` admission with matching catalog/profile
metadata, preserved CNZ/custom-table contracts and typed-runtime initialization
waiting. The whole miniboss/end-boss owners execute27 cases within the52-case
ordinary cohort; a separate fresh profile guard executes2 cases. No F/E/skips
or unexpected caught faults occurred. Existing allocation, defeat, transition
and graph-recreation oracles remain intact. This is component/lifecycle coverage,
not a cold route or native timing certificate; the original same-movie DEZ1
cold intake at offset468982 still needs requalification. Existing roster, donor,
viewport and native acceptance gaps above remain.


## Initial focus calculation — 2026-10-28

The [scoped salvage increment](../../plans/2026-10-22-native-gap-swarm.md#scoped-native-salvage--2026-10-28)
restores `Get_LevelSizeStart`'s fresh `$B00` focus-word addition `$B0` before
horizontal deadzone subtraction. `TestSonic3kLevelStartCamera` adds exact-entry,
character, checkpoint, wrap and maximum controls while preserving the six
existing cases. It does not move the player or turn the focus offset into a
minimum-X boundary. Component qualification and its command/results are recorded
in that work record. The later title-boundary snap, complete inlet/viewport/donor/
team coverage, and issue #229's same-movie cold route remain open; no route or
native pixel result is inferred from these arithmetic checks.

## Spikebonker creation and the original cold opening — 2026-10-29

On develop source `f66d4f65e`, read-only native original-movie replay proves the
first owning difference at row 109: the initialized mace must retain angle 0,
while the engine prematurely ran the orbit decrement. The
[frontier evidence](../../../status/trace-frontier-log.md#2026-10-29--dez-cold-opening-spikebonker-child-initialization-owns-the-first-contact-difference)
records original ROM/movie identities, native hooks, exact commands and limits.
The local repair preserves the creation pass in the folded mace owner.

| Affected obligation | Check | Observed scope | Inherited gap |
| --- | --- | --- | --- |
| Object creation and first orbit dispatch | `TestSpikebonkerBadnikInstance#theMaceCreationPassPreservesItsInitialOrbitAngle` plus existing patrol, slam and facing checks | New assertion fails on unchanged production (expected 0, actual `$F8`), passes with the repair | No whole-act native certification |
| First companion mace contact | `TestS3kDezColdRoutes#coldActOneOpeningPreservesNativeCompanionMaceContact` | Fresh native320, donor off, Sonic+Tails, 200 original movie inputs; movement, fractions, routine and air/roll match, and hurt contact must occur | Other widths, donors, characters and rosters are not newly qualified |
| Creation-phase capture, actual recreation and forward step | `TestDeathEggAlertGuard#maceInitializationSurvivesActualRecreation` plus existing mature graph restore check | Actual registered ObjectManager capture/recreation restores the new phase and next orbit step | Complete campaign/world rewind breadth remains separate |

The combined focused request passes 105 tests without failures, errors or skips,
including the four required S3K anchors. Its temporary observer sees no companion
field difference through 2000 original inputs; Sonic remains alive past the old
1008-input death. That observer is bounded evidence, not Act 1 completion or a
whole-segment trace pass. Longer continuation and strict frontier results belong
to the linked frontier record; historical route qualifications above retain
their original source and setup scope.

## Lift-pad new-landing boundary — 2026-10-29

The [bounded original-movie investigation](../../../status/trace-frontier-log.md#2026-10-29--dez-cold-lift-pad-exact-boundary-landing)
starts at pushed develop `445feabf82ad9cd3607991702a23625fbb886f49`.
Native callbacks identify `SolidObjectTop`'s zero-overlap rejection at row 2697,
then the actual lift landing at row 2700. The local correction selects
that existing provider contract; shared collision and rewind owners remain unchanged.

| Affected obligation | Focused check | Scope and remaining gap |
| --- | --- | --- |
| OBJECT / ORACLE: new lift landing | `TestS3kDezLiftPadHeadless#nativeLandingWindowRejectsTheExactBoundary` | Eight independent real solid checkpoints: exact boundary for rolling, standing and inverted players; separation, one-pixel and 16-pixel acceptance, and 17-pixel rejection. Relative geometry is independent of viewport width and movement donor; expanded character/team/donor coverage remains inherited debt. |
| ROUTE / ORACLE: first original-movie lift | `TestS3kDezColdRoutes#coldActOneLiftLandingPreservesNativeBoundary` | Fresh native320 Sonic+Tails, donor off, 2720 original inputs; main movement/fractions, rings, camera, level counter and air/roll are assertions only. No whole-act, incoming handoff or campaign certification. |
| OBJECT / REWIND: arm admission and actual recreation | Existing `TestS3kDezLiftPadHeadless` allocation, retirement and 320/800 placed-lift restore/forward cases | No new persistent state. Existing real graph recreation must remain green; wider act/world obligations remain open. |

The completed focused request passes 79 cases (78 asserting checks and one
bounded observation shell), with zero failures/errors/skips. The eight new
component cases and original-input landing regression first fail on old behavior,
then pass with the local declaration. Existing allocation/retirement and actual
320/800 graph-recreation cases plus all required S3K anchors remain green.
The temporary continuation moves the first main Y difference from 2697 to 3268,
but its death remains at 4843 inputs; this is no endpoint advance or whole-route
certification. Exact selection, fingerprints, warnings and timings are in the
linked frontier entry.


## Lift-pad continued-ride release — 2026-10-30

The [walk-off investigation](../../../status/trace-frontier-log.md#2026-10-30--dez-lift-continued-ride-standing-latch)
checks the inherited review finding independently of the later cold frontier.
`loc_47440` runs the lift body before `SolidObjectTop`; `sub_4757A` consumes
only P1's standing bit. `SolidObjectTop_1P` clears the exiting participant's
bit through `loc_1E2E0`, after the body's pause decision. The existing provider
opt-in preserves this order without changing the shared collision owner.

| Affected obligation | Focused check | Scope and inherited gap |
| --- | --- | --- |
| OBJECT / CONTACT: P1 and P2 release | `TestS3kDezLiftPadHeadless#continuedRideExitReleasesOnlyItsPlayerBitAndNextUpdatePauseThroughRecreation` | Real registered Sonic/Tails and production manager body/contact dispatch; P2-only contact cannot start the lift; P1 contact starts on the following body; both in-bounds edges retain pause; left/right walk-off and airborne release clear only the exiting bit. Pause stays 30 on exit and decrements next update despite P2 support. |
| REWIND: held and released latches, actual graph recreation | Same three parameter cases | Registered-world restore of a released checkpoint, then destructive pad/arm removal and recreation from held contact, with identical 120-update return and P2 rider trajectory. Existing 320/800 placed-lift, allocation-exhaustion and unload checks remain separate. |
| ROUTE / ORACLE: original cold continuation | Temporary comparison-only observer; command, fingerprints and completed results in the linked frontier entry | Fresh native320 Sonic+Tails, original movie offset 468982; no row hydration or input edits. This does not certify later native causality, Act 1 completion, incoming handoffs, pixels/audio, or expanded donor/character/viewport/team coverage. |

Focused results and the relationship to row 3268/death 4843 belong to the linked
frontier record; neither endpoint is assumed to be caused by the latch.


## Conveyor moving standing-bit ownership — 2026-10-30

The [matched original-input decision probe](../../../status/trace-frontier-log.md#2026-10-30--dez-cold-conveyor-moving-standing-bit-owner)
starts at pushed develop `6f267e8103188214ec40570248ad8ea9322cbc40`.
Native and engine agree at the jump/radius write. Moving the pad's engine spawn
metadata loses its standing-bit lookup key before its own full-solid checkpoint;
the native SST status survives the move. Two local provider declarations select
live-instance latch ownership and the owner's airborne checkpoint, without a
shared collision, player, input, timing, loading, audio or rewind edit.

| Affected obligation | Focused check | Scope and inherited gap |
| --- | --- | --- |
| OBJECT / OWNERSHIP: moving pad P1/P2 bits and jump return | `TestS3kDezConveyorPadSolidOwnership#movingPadRetainsEachParticipantsBitUntilItsOwnAirborneCheckpointAndRecreates` | Each native participant jumps independently while the other stays supported; moving metadata retains both bits until the pad's checkpoint, whose airborne return preserves Y and releases only that participant. Declared component setup is separate from the original-input oracle. |
| ALLOCATION / REWIND: live occupant identity | Same parameter cases | Removal/recreation restores both riders and bits with the captured physical slot; a 45-dispatch continuation repeats exactly; a replacement occupant in the retired slot starts without either bit. No allocator or rewind implementation change. |
| ROUTE / ORACLE: native jump boundary | `TestS3kDezColdRoutes#coldActOneConveyorJumpPreservesNativeBoundary` | Fresh native320 Sonic+Tails, donor off, 3280 original movie inputs, 13 strict main/camera/clock fields and exact input admission. No trace-state seeding, tolerance or input change. Whole-act and expanded donor/roster/viewport native parity remain open. |
| EXISTING BREADTH: conveyor variants, lift lifetime, S3K anchors | `TestS3kDezConveyorPadHeadless`, `TestS3kDezLiftPadHeadless` and the four mandatory S3K anchor classes | Existing 320/800, inverted, per-player/direction, allocation-exhaustion, arm retirement and graph recreation checks remain separate from native matching. Exact completed results and limitations are in the frontier record. |

## Floor-launcher exact landing boundary — 2026-10-30

The [original-input continuation](../../../status/trace-frontier-log.md#2026-10-30--dez-original-input-continuation-and-floor-launcher-landing-boundary)
starts at pushed develop `270fda5f4566cc404ea6f3ea2b692bc266fd2c98`.
At row 5516 the ordinary physics step still matches native; the floor launcher's
new-contact checkpoint accepts a zero-distance boundary which its actual
`SolidObjectTop` call rejects. Qualification and the subsequent bounded frontier
are recorded in the linked entry; this is not an act-completion certificate.

| Affected obligation | Focused check | Scope and inherited gap |
| --- | --- | --- |
| OBJECT / CONTACT: floor-launcher new landing | `TestS3kFloorLauncherLanding#exactBoundaryRejectsEachParticipantBeforeLandingAndAfterRecreation` | Real P1/P2, both launcher directions, upright and reverse-gravity representatives. Exact boundary preserves airborne rolling motion and fractions; one pixel inside still lands. Declared component setup is separate from the original-input route. |
| SLOT / REWIND: rejected and held contact | Same parameter cases | Destructive removal/recreation preserves the exact physical slot and rejected state; restored supported contact repeats 24 object dispatches through launch and early return. A replacement occupant inherits neither participant's standing bit. This is bounded manager replay, not free-flight physics or a new complete-world route claim. |
| ROUTE / ORACLE: original floor-launcher approach | `TestS3kDezColdRoutes#coldActOneFloorLauncherLandingPreservesNativeBoundary` | Fresh native320 Sonic+Tails, donor off, 5520 unchanged movie inputs and 13 main/camera/clock fields. No position, clock, object or physics-row hydration. |
| EXISTING SEMANTICS: shared FBZ/DEZ object | `TestFbzPlayerTransportObjects` and required S3K anchors | Existing acceleration, return, rider ordering, lifetime and facing checks remain separate. Expanded donors, viewports, solo characters/rosters, whole-act, pixels/audio and campaign parity remain open. |


## Staircase moving standing-bit ownership — 2026-10-30

SKL `$4F` uses the live section's standing bits across its Y update, and its own
`SolidObjectFull_1P` call consumes an airborne rider without a new-contact lift.
The correction uses existing provider contracts; shared collision and rewind
owners remain unchanged. Native slot-19 execute callbacks on the original movie
prove the branch at row 6797. The complete native control independently matches
12000 contiguous rows / 180000 fields; it is observation, not engine hydration.

`TestS3kDezStaircaseAirborneRelease` exercises real P1/P2 × parent/child ×
normal/reverse gravity. It asserts support/latch survival through an unrelated
earlier solid and the moving section's own checkpoint, exact jump position,
velocity and fractions, held/released family recreation at restored physical
slots, replay and a replacement occupant with clear latches. All eight cases
fail on the unchanged runtime after correcting the reverse-gravity component
setup. `TestS3kDezStaircaseHeadless` supplies independent forward-allocation
failure, section ordering, native rounding, parent/child graph restoration and
placed normal/inverted carry/replay checks at 320/800.

`TestS3kDezColdRoutes#coldActOneStaircaseJumpPreservesNativeBoundary` retains the
unchanged 13-field comparator and asserts the first 6810 original-input rows
from the fresh native320 Sonic+Tails inlet. The temporary 15-field continuation
uses the same original input and stops at the first mismatch, death, act change,
12000 rows or 60 seconds of stepping. Completed results, tested source hashes,
queue/execution attribution and the remaining frontier belong to the
[frontier entry](../../../status/trace-frontier-log.md#2026-10-30--dez-staircase-airborne-standing-decision-at-original-row-6797).
These component/route checks do not extend donor, roster or width coverage,
certify an entire act/campaign, or establish pixel/audio parity.


## Conveyor walk-off standing-bit retirement — 2026-10-30

The [original-input causal investigation](../../../status/trace-frontier-log.md#2026-10-30--dez-conveyor-walk-off-standing-bit-retirement-at-original-row-8759)
identifies slot 11's own full-solid left walk-off at row 8758 as the missing
per-player standing-bit clear. That bit gates the next body’s conveyor carry;
P2 remains attached while P1 leaves. The correction selects the existing
continued-ride-exit contract only on this DEZ object, including its narrow/wide
collision widths and both gravity directions. An in-bounds object-controlled
rider retains its standing bit as the native seat helper returns early.

`TestS3kDezConveyorPadWalkOff` uses real P1/P2 contacts, both conveyor directions
and gravity modes, the final supported carry and absence of subsequent carry,
other-participant support, controlled in-bounds latch retention, exact physical
slot recreation/replacement and 24-dispatch forward replay. The independent
`TestS3kDezColdRoutes#coldActOneConveyorWalkOffPreservesNativeBoundary` compares
8770 original-input rows from the fresh ROM inlet, without gameplay hydration.
Completed commands, old-red/new-green results and the bounded continuation belong
to the linked frontier entry. This does not certify whole-act native matching,
Act 2 cold reachability, donor/expanded-roster/viewport breadth, pixels or audio;
those inherited matrix obligations remain open.


## Turbine terrain preparation after the 12000-row prefix — 2026-10-30

The [bounded original-input investigation](../../../status/trace-frontier-log.md#2026-10-30--dez-turbine-owned-terrain-preparation-after-the-12000-row-prefix)
starts at pushed develop `b69637a2d3d01b18e0d54a16a48da11eaf000fc0`.
The room now refreshes the existing motion-dependent probe activation before its
own terrain call. Shared movement, collision, input, scheduling and rewind owners
are unchanged. Native callbacks independently witness the row12622 ceiling
separation; the committed input/reference/comparator remain unchanged.

`TestS3kDezGravityRoomTerrain` covers native P1/P2 at320/800 with real DEZ1
ceiling separation after grounded activation, uncaptured-slot isolation,
controller recreation at the same slot, exact contact restoration and24-dispatch
forward replay, release and clean replacement. Clear-space and already-active
ceiling controls are separate. The controller creates no children and this edit
adds no allocation or persistent state; allocation-exhaustion products are not
newly qualified. These are positioned owner checks, not full production-loop or
whole-world route replay certification. The existing14 room cases also pass,
including their production-loop puzzle contact/replay and controller route.

The new `TestS3kDezColdRoutes#coldActOneTurbineCeilingPreservesNativeBoundary`
strictly compares the existing13 fields and original input through12630 rows.
The temporary unchanged15-field continuation matches13251 rows (indices0–13250),
then stops alive in Act1 at row13251, inputDown, with Y velocity, ground velocity
and Y fraction differences. The next owner is the gravity puzzle's declared
landing width: the generic `$18` width rejects the native `$20`-wide contact.
This follow-up does not repair that successor or certify the Act2 load at18670.
The native reference itself is independently matched through18700 rows.
Donor/character/team breadth, other routes, restart/death/load replay and native
pixels/audio remain inherited gaps. Final focused selection:90 tests,0 failures,
0 errors,0 skips;60 are the required S3K anchors and one is the temporary observer.


## Gravity puzzle native top landing width — 2026-10-30

The [bounded original-input investigation](../../../status/trace-frontier-log.md#2026-10-30--dez-gravity-puzzle-native-top-landing-width-at-row-13251)
composes local turbine candidate `a8637c9d9` and pushed develop `70879a451` in
an isolated provisioned checkout. Actual native callbacks prove that the puzzle
reaches its new-landing helper at original row13251, accepts X offset `-$1E`
using width_pixels `$20`, and establishes support before the turbine moves P1.
The provider now declares that width and the native per-occupant standing-bit
identity, own-checkpoint airborne release and no-contact return. Shared solid
math and turbine behavior are unchanged. Native comparison rows remain read-only evidence.

`TestS3kDezGravityPuzzleLanding` checks P1/P2 at inclusive `-$20`, excluded
`-$21`/`+$20`, included `+$1F` and centre controls through real object-manager
contacts. Four positive-control cases cover P1/P2 at320/800, exact word-only Y
correction, velocity reset/copy, untouched other participant, same-slot forced
recreation, exact24-dispatch replay and replacement without stale support.
Two additional P1/P2 checks force a moving-metadata update and an earlier solid
slot before the puzzle consumes its airborne standing bit.
No persistent state, slot allocation or child lifetime changes are introduced;
the existing folded panel-child representation and allocation-exhaustion products
are not newly qualified. These are owner-level positioned checks, separate from
the production-loop original-input witness.

`TestS3kDezColdRoutes#coldActOneGravityPuzzleLandingPreservesNativeBoundary`
compares the unchanged13 fields and exact original inputs from the fresh inlet
through13260 rows. The linked log records old-red/new-green results, mandatory
anchors, the separate15-field observation and its stopping boundary. Existing
puzzle/panel/room tests retain their scope. Whole-act/campaign certification,
other donor/character/team products, restart/death/load rewind, native pixels and
audio remain inherited gaps. A solid seat immediately before the room can still
select grounded sensor activation; this adjacent concern is recorded separately
rather than changing shared physics or enabling probes by a route/frame key.


Completed focused validation:120 tests,0 failures,0 errors,0 skips (60 mandatory
S3K anchors; one temporary observer). The unchanged15-field continuation matches
rows0–13726, then stops at13727 with a room-floor landing velocity difference.
The puzzle prefix is green; Act2 row18670 remains unreached. The room's empty
landing callback is the next owner, distinct from the grounded-seat activation
concern. Detailed commands, identities, timings and coverage limits are in the
linked frontier record.

The subsequent walk-off review correction clears each departing participant's
standing bit at the native range exit. `TestS3kDezGravityPuzzleLanding` covers
both edges and P1/P2 re-entry, plus in-bounds object-controlled latch retention.
Its 101-case focused DEZ/anchor selection passed with zero skips; broader
overlapping-support, sustained object-control and whole-route coverage remain open.


## Turbine direct floor landing — 2026-10-30

The [bounded native/engine witness](../../../status/trace-frontier-log.md#2026-10-30--dez-turbine-direct-floor-landing-at-row-13727)
starts from explicit candidate624 and composes the lead-reviewed puzzle walk-off
correctione87. It distinguishes correct geometric resolution from the turbine's
missing direct-floor velocity/reset callback. Native original-input callbacks
reach the same floor pair +1/-4 at angle `$F8`, then clear Y speed, copy X speed
into inertia and call TouchFloor before restoring airborne status. Positive clear
ceiling distances differ without changing the taken branch; shared collision is
unchanged. The separate grounded-seat sensor-activation concern remains open.

Four short `TestS3kDezGravityRoomTerrain` cases cover Sonic P1/Tails P2 at320/800
against actual DEZ1 floor tiles, fraction preservation, native velocity/reset
writes, room control/air retention, untouched other participant, forced same-slot
controller recreation, exact24-dispatch forward replay, corridor release and
clean same-slot replacement. Positioned component inlets are separate from the
fresh original-input cold witness. Existing clear-space/ceiling controls and
room/puzzle consumers retain their own coverage. The new cold method compares
the unchanged13 fields and exact original input through the repaired contact.

No new persistent field, allocation, child lifetime, load or rewind schema is
introduced. This does not certify full companion state, downward-motion floor
landings, wider donor/character/team products, restart/death/load rewind, Act2
loading, native pixels/audio or whole-act/campaign parity. The linked record owns
completed command results and the next observed stopping boundary.

The follow-up hurt-contact check covers both P1 and P2 at the direct floor:
the separate HurtStop owner retains hurt routine, room control and invulnerability
through the next room dispatch. The focused DEZ/anchor selection passed 108 cases
with no skips. This does not establish the untested ability or route products above.


## Original-input bumper slot-order boundary — 2026-10-30

At pushed develop `45bdfed16`, the [bounded following-row witness](../../../status/trace-frontier-log.md#2026-10-30--dez-row-13731-shared-object-order-boundary)
freshly qualifies native rows0–13732 against the original movie/reference, while
the engine strictly matches15 fields through0–13730. At13731, native lower
bumper placement330 occupies slot13 and consumes the turbine slot12 output;
the engine bumper occupies slot11 and checks before turbine movement. Its NONE
result samples a player still outside horizontal reach. The turbine output
itself matches the native pre-bumper state.

This establishes a shared allocation/order handoff, not a local bumper-launch
or turbine-terrain correction. The earlier allocation/lifetime cause remains
unproven. No maintained runtime/test, comparator, fixture, allocation, rewind,
load/save or audio changes are made. Mandatory S3K anchors pass60 cases with
zero skips; the temporary original-input observer reproduces the row13731
failure. There is no new-green or whole-act/campaign claim. The preceding
positioned owner/rewind checks retain their original scope; broader configuration,
companion, lifecycle, allocation-exhaustion and native pixel/audio obligations
remain open. The linked record owns exact commands, identities, timing, retained
source probes and cleanup.

## Puzzle display-child slot and bumper order — 2026-10-30

The [original-input allocation history](../../../status/trace-frontier-log.md#2026-10-30--dez-puzzle-display-child-allocation-behind-row-13731)
resolves the preceding shared-owner stopping point. Allocatable occupancy matches
for8293 consecutive rows before puzzle placement319 initializes at row12420.
Native shaft9 creates display-child10; the engine had folded its drawing without
reserving the slot. That hole moves the final lower bumper to11 before turbine12,
where native lower bumper13 consumes the room's moved player.

The object-local correction uses the existing after-parent folded reservation
and rewind state. `TestDezGravityPuzzleAllocation` covers a lower free hole,
an occupied next slot, one-time allocation, actual placed-owner unload, destructive
recreation and forward replay from before/after initialization. Existing puzzle
headless/landing, bumper and room-terrain checks retain separate P1/P2 and
controlled-contact/rewind coverage. The maintained cold floor/bumper check now
compares13733 original-input rows, through the following row13732; its comparison
rows remain read-only. Exact completed results are owned by the linked record.

This is a bounded320px Sonic+Tails inlet and owner/lifecycle qualification.
Full companion trajectory, later Act1/Act2, other rosters/donors/viewports,
allocation exhaustion and dynamic-authoring lifetimes, rendering priority,
native pixels/audio and whole-act/campaign parity remain open. The first new
occupancy gap in the reached causal interval is identified; earlier transient
inlet differences are not asserted fixed.


## Post-turbine original-input frontier — 2026-10-30

The [cold continuation evidence](../../../status/trace-frontier-log.md#2026-10-30--dez-cold-continuation-to-the-shared-tails-control-frontier)
extends the fresh320 Sonic+Tails original-input comparison on develop4415f23d9.
`TestS3kDezColdRoutes#coldActOnePostTurbineApproachPreservesNativePrefix`
strictly compares14316 rows /186108 main/camera/clock fields (13 per row),
indices0–14315, without gameplay seeding. The read-only diagnostic additionally
compares main angle/routine, then finds the first main mismatch at14316: Y and
cameraY are three pixels high. The native power-on replay independently matches
14321 rows /15 fields through14320 against the unchanged fixture.

The engine's companion lands on the shared FBZ/DEZ launcher before the native
companion, starting its motion early. Companion observation first diverges at
13017, after matching ten fields on every row0–13016. The linked investigation
owns the earlier control/flight-state evidence and the explicit shared-owner
stopping point. No runtime fix, new allocation contract or comparison tolerance
is introduced here. Existing short puzzle allocation/lifetime and P1/P2
landing/room/launcher destructive-recreation checks remain separate from this
route observation; their completed requalification is recorded in that evidence.

Actual Act2 admission at18670 remains unreached. Full companion trajectory,
other rosters/donors/viewports, allocation exhaustion, rendering priority,
native pixels/audio and whole-act/campaign parity remain unqualified.


## Shared Tails suppressed dispatch — 2026-10-30

The [shared-owner evidence](../../../status/trace-frontier-log.md#2026-10-30--tails-suppressed-control-flight-dispatch)
records native S3K's per-dispatch flight-byte clear, the S2/KiS2 and host/donor
boundary, old-red assertions and matched original-input observation. Runtime
changes only shared player dispatch; room, launcher, allocation and fixtures are
unchanged. `TestS3kDezColdRoutes#coldActOneTailsSuppressionReleaseMatchesNative`
compares14106 original-input rows, indices0–14105:13 main/camera/clock fields and
ten companion movement/routine fields, all exact. The prior main-only14316-row
method and earlier methods retain their assertions and bounds.

`TestS3kDezGravityRoomTerrain#tailsSuppressedDispatchAndReleaseRepeatAfterRoomRecreation`
adds independent short native320 component inlets for main Tails alone and CPU
P2 Tails. Each captures a real controlling room and pre-dispatch flight flag,
clears through player movement, releases through the room, and verifies ordinary
`+$38` next-frame gravity. Registered restore destroys/recreates the room, restores
the original flag and capture ownership, then reproduces the whole composite
continuation. This is component setup, not a trace-state seed or a cold-route
rewind claim. Player/controller recreation and cross-game/donor, movement-active,
animation and input-edge controls live in `TestPlayableSpriteMovementTailsFlight`.

The ten-field P2 frontier advances from13017 to14106, where X/ground speed differs.
The separate15-field main observer still first differs at14316, Y/cameraY three
pixels high. Both frontiers remain open; neither is hidden by the bounded test.
The observation is not a full strict replay, animation or pixel comparison.
Lead-owned broad smoke remains required before integration. Actual Act2 load18670,
whole-act/campaign, alternate route/configuration and native pixel/audio
qualification are not established.


## Gravity-tube animation word and launcher continuation — 2026-10-30

The [native owner and bounded comparison](../../../status/trace-frontier-log.md#2026-10-30--dez-gravity-tube-animation-word-behind-tails-row-14106)
identify the wide horizontal tube's zero-inertia animation word as the cause of
the former P2 row14106 speed frontier and later P1 row14316 launcher consequence.
The correction stays in SKL $5A; shared animation/CPU/movement/collision/timing/
allocation and existing fixture/comparison gates are unchanged.

`TestS3kDezGravityTubeAnimation` adds eight independent component cases: native
main Tails alone and Sonic+CPU Tails; horizontal/vertical mount byte pairs;
zero-inertia horizontal re-arm; nonzero-inertia and rejected-contact controls;
and registered destructive tube recreation both before the write and with its
pending anim0/prev_anim1 comparison captured. Animate_Tails owns the eventual
push clear. Both replay continuations compare every registered snapshot entry,
including the actual ride link, slot, object angle and player animation owner.
The existing14 tube checks and three production-loop route checks pass, with the
old animation-ID1 mount expectation corrected to the ROM word's two bytes.
There is no new allocation path or allocation-pressure claim.

The original-input native320 observer measures14521 rows: main15 fields match
0–14520 and P2 ten fields match0–14501. P2's next marker-shaped mismatch at14502
remains open. The observer is temporary; all previously maintained cold prefix
gates retain their exact bounds. Native power-on callbacks independently verify
14321 rows/25 fields and the actual tube/Animate/CPU/collision ownership chain.
These are bounded field comparisons and component rewind checks, not strict
whole-act replay, full-route rewind, donor/viewport breadth or pixel/audio
certification. Those inherited obligations remain open for both acts.


## Gravity-tube live interact word — 2026-10-30

The [original-input callbacks and matched engine observation](../../../status/trace-frontier-log.md#2026-10-30--dez-gravity-tube-live-interact-word-at-p2-row-14502)
identify the missing tube `RomObjectCodePointerProvider` at P2 row14502. The
previous launcher CPU word3 is compared with the newly ridden tube's live word4
while offscreen. The timer is120, the current tube slot is live, and visibility
matches; the existing shared CPU branch already models the native marker entry.
Both tube bodies install code pointers in bank4, so the correction is one local
provider with no new persistent state or allocation path.

`TestS3kDezGravityTubeInteractWord` has six independent native320 Sonic+CPU Tails
component cases across horizontal/vertical bodies: changed-word despawn from an
executed launcher latch, visible word refresh followed by offscreen same-word
survival, and retired-slot zero-word detection. The changed-word cases recreate
the actual managed tube twice, restoring before mount and while mounted, then
compare every registered snapshot entry after CPU marker entry and rider release.
Declared component contacts establish the latch by executing CPU control; no
trace physics, auxiliary or bootstrap state is hydrated. Native velocities,
fractions, prior latch and compare-before-refresh ordering remain asserted.

The new independent `TestS3kDezColdRoutes#coldActOneOffscreenTubeDespawnMatchesNative`
compares14521 original-input rows0–14520,13 main/camera/clock fields and ten P2
fields. The older prefix methods keep their bounds. The temporary observer adds
main angle/routine and separate CPU/visibility/slot diagnostics. The linked record
owns actual run outcomes and limits. Native callback/trajectory evidence now
reaches14520; nothing beyond that endpoint is graded here. Whole-act/Act2 admission,
full-route rewind, alternate viewports/donors/rosters, and native pixels/audio
remain inherited gaps. Shared flight upstream qualification is still lead-owned
and pending; this leaf schedules no broad smoke.
