# S3K Death Egg Zone act 1 coverage matrix

Game / canonical zone / act: S3K `S3K_DEATH_EGG_1`, engine zone `$0B` act index 0,
ROM `Current_zone_and_act = $B00`, SKL object set. **Not Sonic 2's Death Egg**: the
`TestDEZ*`/`TestS2Dez*` classes and the `*dez-boss-fixes*` documents are Sonic 2.
Owning plan: [S3K DEZ bring-up](../../plans/2026-09-17-s3k-dez-bring-up.md).
Status: slice 1 (presentation foundation) delivered at `4e7655bf9`. Nothing below certifies the act.

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
| Implemented | Presentation foundation, runtime event words and 348/365 concrete placements, including gravity tubes, turbine room/puzzle/bumper walls, energy bridges, Spikebonkers and Chainspikes. Remaining objects, miniboss and act change are open |
| Cold-reachable | Not started |
| Rewind-verified | Palette/event state plus implemented object spot checks; turbine approach/contact now has production-loop restore and forward replay. Cold route and load-boundary coverage remain open |
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
| PLACEMENT: 365 act 1 objects | [inventory](../../research/s3k-zones/dez-object-inventory.md) | — | `TestS3kDezPlacementCensus` | 348 concrete / 17 placeholder | pass | Remaining families are listed in the census and bring-up plan; implementation counts do not certify a route |
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
| BOSS: `$A6` miniboss | `word_7DDA4` range Y `$18C`-`$38C` X `$3400`-`$3780`; arena `$28C,$28C,$3680,$36C0`; 8 hits | — | `TestS3kDezMinibossHeadless` | missing | unrun | Slice 6 |
| ROUTE (Sonic + Tails cold): `$B00` entry → results | Complete-run BK2 from movie frame 468982 (segment directory `ssz`, `zone_id 11`) | native 320 | `TestS3kDezColdRoutes` | missing | unrun | Slice 6 |
| ROUTE (Tails cold) | `runs/s3k-tails-full-chain-all-emeralds` `ssz`, offset 444059 | native 320 | `TestS3kDezColdRoutes` | missing | unrun | Slice 6 |
| REWIND: cycle counters, event routine words | Registry restore equals capture plus forward replay | 320 | `TestS3kDezPresentationRewind` (2 tests) | implemented | pass, `4e7655bf9` | Entry, object and load-boundary spots not started |
| ORACLE: route timing | `runs/s3k-sonic-tails-complete-emeralds/ssz` (DEZ, `zone_id 11`, 40,049 rows, offset 468982; both acts and the handover) | — | `TestS3kSonicTailsSszSegmentTraceReplay` (expected red) | — | blocked: 7005 errors, first error frame 0 `camera_x` expected `0x0040` actual `0x0000` (`035e48a58`, `-Ptrace-replay-r7`) | Whole campaign |
| ORACLE: Tails route timing | `runs/s3k-tails-full-chain-all-emeralds/ssz` (act 1, 23,249 rows, offset 444059) | — | `TestS3kTailsFullChainSszSegmentTraceReplay` (expected red) | — | blocked: 1661 errors, first error frame 0 `camera_x` expected `0x0040` actual `0x0018` (`035e48a58`) | Whole campaign |

## Execution evidence

Worktree `.worktrees/ai-s3k-dez-bring-up`, ROM by absolute path, `maven_queue.py -Dmse=off`.
Frontier command (2026-09-17, `035e48a58`):
`python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=<abs>/s3k.gen -Ptrace-replay-r7 "-Dtest=TestS3kSonicTailsSszSegmentTraceReplay,TestS3kSonicTailsDez238SegmentTraceReplay,TestS3kTailsFullChainSszSegmentTraceReplay,TestS3kTailsFullChainSsz2SegmentTraceReplay,TestS3kTailsFullChainSsz3SegmentTraceReplay,TestS3kTailsFullChainDez238SegmentTraceReplay" -DfailIfNoSpecifiedTests=false test`
— 6 tests, 6 failures, 0 errors, **0 skipped** (the ROM path resolved).
