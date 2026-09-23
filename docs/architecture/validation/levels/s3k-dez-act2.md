# S3K Death Egg Zone act 2 coverage matrix

Game / canonical zone / act: S3K `S3K_DEATH_EGG_2`, engine zone `$0B` act index 1,
ROM `Current_zone_and_act = $B01`, SKL object set. **Not Sonic 2's Death Egg.**
Owning plan: [S3K DEZ bring-up](../../plans/2026-09-17-s3k-dez-bring-up.md).
Status: slice 1 (presentation foundation) delivered at `4e7655bf9`; slice 2 (reverse gravity core)
delivered and gated twice; slice 3 part-delivered (`$5B` only). Nothing below certifies the act.

LevelSizes (sonic3k.asm:38120): x `0`-`$6000`, y `0`-`$F10`. The engine's direct `$B01`
load places Sonic at centre `$140,$3AC`, which is also the ROM's post-act-change
transport landing (`loc_7E44C`; `$3B0` when `Player_mode == 2`). Level art
`levartptrs $38,$38,$21` (PLC `$38`, palette `$21`, `ArtKosM_DEZ_Primary` /
`ArtKosM_DEZ2_Secondary`, sonic3k.asm:199456). Music `Sonic3kMusic.DEZ2`.
Placements: 494 objects, 198 rings. All three reverse-gravity writers (`$58`, `$59`,
`$5B`) and the hub `$5C` and retracting spring `$5D` are act-2 only.

Incoming: seamless `$B00` → `$B01` (`loc_593EC`), and a direct load, which starts at
`Events_routine_fg = 4` and `_bg = 8` (`DEZ2_ScreenInit`/`DEZ2_BackgroundInit`), so
`DEZ2_ScreenEvent` stage 0 (chunks `$D7,$DC,$D7`) is reachable seamlessly only.
Outgoing: `Obj_DEZEndBoss` → `StartNewLevel $1700`.

Widths / donors / characters / teams: as act 1. Knuckles is level-select only
(user decision 2026-09-17).

## Five claims

| Claim | State |
| --- | --- |
| Implemented | Presentation foundation (static background, the two shared `AnPal_DEZ2` channels, the eight `AniPLC_DEZ` scripts, both `DEZ2_ScreenEvent` chunk stages, the direct-load routine values), reverse gravity for the player, shields, lost rings, dust-free solid objects, springs and the sidekick (94 of 116 ROM references — the 11 open group A-I rows are listed in [s3k-known-bugs](../../../status/s3k-known-bugs.md)), the implemented gravity interaction families, and the traversal/badnik/shock-block families listed below (493/494 concrete placements) |
| Cold-reachable | Seeded from the first frame of act 2 free play: **1256 frames** of exact player x, y, camera and ring parity (`TestS3kDezColdRoutes`, ratcheted). The cold `$B01` route remains recorded at 0 frames; the connected entrance now lands, but its strict trace has not been remeasured |
| Rewind-verified | Event routine words (`TestS3kDezPresentationRewind`) and the `$5B` write plus its side latch, capture/restore/forward replay (`TestS3kDezGravityObjectsHeadless`) |
| Native behaviour matched | The seeded route's first 1256 frames match native exactly in position, camera and rings; the first divergence, native row 21029, is a one-pixel `x` lag while riding a shared `$08` platform — not a Death Egg object. The six segment replay classes are unchanged from the `035e48a58` measurement below |
| Visually matched | Clips `030` (320 and 528), `031` and `032` show the flag being written by a real `$5B` and the inverted run, jump, roll, rings and Knuckles that follow; no native pixel comparison. The sidekick, hit/lost-ring, shield and solid-object clips are blocked on slice 3's remaining objects and the act 2 route — see `INDEX.md` for the measurements. Slice 4: clips `037-spikebonker-320`, `038-retracting-spring-320` and `039-energy-bridge-320`; the `$5D` extension stroke, the `$55` relight and the `$A5` slam have no positioned-entry site and are recorded as such in `INDEX.md` |

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| ENTRY: `$B01` resources, bounds, object set | LevelSizes `$6000`x`$F10`, `levartptrs $38/$38/$21`, SKL set | — | — | present before this campaign | unrun | Slice 0 records it; no assertion yet |
| ENTRY: direct load starts at `Events_routine_fg = 4`, `_bg = 8` | `DEZ2_ScreenInit` / `DEZ2_BackgroundInit` (sonic3k.asm:118770) | 320 | `TestS3kDezScreenEvents` | implemented | pass, `4e7655bf9` | — |
| PRESENT: background scroll | `DEZ2_BackgroundInit` clears `Camera_X/Y_pos_BG_copy`; `PlainDeformation` keeps both BG scroll words at 0 | 320 + one wide | `TestS3kDezScrollHeadless` | implemented (`SwScrlS3kDez`) | pass, `4e7655bf9` | Act 2 moving capture not taken; native comparison open |
| PRESENT: `AnPal_DEZ2` channels B and C | Act 2 enters at the `AnPal_DEZ2` label, so channel A (line 4) does **not** run: B `AnPal_PalDEZ12_1` ($3444) → line 3 colours 13-14, C `AnPal_PalDEZ12_2` ($3474) → line 3 colours 8-12 | 320 | `TestS3kDezPaletteCycling`, including a 200-pass check that line 4 never moves | implemented | pass, `4e7655bf9` | Native comparison open |
| PRESENT: `AniPLC_DEZ` 8 scripts | Same table as act 1 ($28AEE) | 320 | `TestS3kDezAnimatedTiles` | implemented | pass, `4e7655bf9` | Native comparison open |
| EVENT: `DEZ2_ScreenEvent` stage 0 chunks | `movea.w $38(a3),a1; addq.w #1,a1` = FG layout row 14, columns 1-3 = `$D7,$DC,$D7`; reachable only after the seamless change | 320 | `TestS3kDezScreenEvents` | implemented | pass, `4e7655bf9`, with the routine driven back to 0 | Not cold-reachable until the seamless change lands (slice 7) |
| EVENT: `DEZ2_ScreenEvent` stage 1 chunk | `movea.w $18(a3),a1; move.b #$BC,$6B(a1)` = FG layout row 6, column `$6B` | 320 | `TestS3kDezScreenEvents` | implemented | pass, `4e7655bf9` | Production trigger is the end boss (slice 8) |
| EVENT: `DEZ2_BackgroundEvent` bottom-up redraw | stages 0-1 `Draw_PlaneVertBottomUp` (`loc_59532`/`loc_59556`) | — | — | missing | unrun | Slice 7 |
| PLACEMENT: 494 act 2 objects | [inventory](../../research/s3k-zones/dez-object-inventory.md) | — | `TestS3kDezPlacementCensus` | 493 concrete / 1 placeholder | pass | Slices 3-5 |
| BADNIK: `$A4` `Obj_Spikebonker`, 11 act 2 and 7 act 1 placements | `Obj_Spikebonker`, sonic3k.asm:198893-199124 | — | `TestSpikebonkerBadnikInstance` | implemented (`SpikebonkerBadnikInstance`) | pass 10/10 | Ten mechanisms, seventeen deliberate breaks. Landing it moved the seeded act 2 route frontier from 390 to 472 frames, which is the strongest evidence any object in this campaign has: the divergence it closed was the badnik's own destruction rebound. Filmed: `037-spikebonker-320` (patrol and hover only; the slam and the kill want a route-driven capture) |
| BADNIK: `$A5` `Obj_Chainspike`, 6 act 1 and 12 act 2 placements | `Obj_Chainspike`, sonic3k.asm:199132-199420 | 320 | `TestChainspikeBadnikInstance` | implemented (`ChainspikeBadnikInstance`) | pass 9/9 | Nine assertions, twelve deliberate breaks. Landing it took the seeded act 2 route past the end of its 1200-frame window: the frontier is now 1256 frames and the first divergence is a shared `$08` platform ride, not a Death Egg object. Filmed: `040-chainspike-800` |
| TRAVERSAL: `$55` `Obj_DEZEnergyBridge`, 13 act 1 and 12 act 2 placements in six subtypes | `Obj_DEZEnergyBridge`, sonic3k.asm:93909-93990, subtype decoder `sub_47DDE` :93879-93902 | 320 | `TestS3kDezEnergyBridgeHeadless` | implemented (`S3kDezEnergyBridgeObjectInstance`) | pass 11/11 | Eleven assertions, twelve deliberate breaks. Landing it, together with the route carrying the ROM's `Level_frame_counter`, moved the seeded act 2 frontier from 527 to 616 frames. One coverage limit is stated in the suite: a headless fixture has no pattern renderer, so the off-state draw gate is not observable there. Filmed: `039-energy-bridge-320` |
| TRAVERSAL: `$5D` `Obj_DEZRetractingSpring`, 13 act 2 placements, all subtype `$02` | `Obj_DEZRetractingSpring`, sonic3k.asm:94098-94185, launch `sub_22F98` :47719-47749 | 320 | `TestS3kDezRetractingSpringHeadless` | implemented (`S3kDezRetractingSpringObjectInstance`) | pass 13/13 | Thirteen assertions, eleven deliberate breaks across four groups. Landing it moved the seeded act 2 route frontier from 472 to 527 frames: the `-$A00` launch at native row 20245 and the 55 frames of ballistic arc after it are now exact. Filmed: `038-retracting-spring-320` |
| GRAVITY: `$5B` writer, both crossing directions, band edges, latch, Player 1 only | `sub_49228` / `loc_49270` (sonic3k.asm:95472-95543); write not toggle; band `[y_pos-$20, y_pos+$20)` | — | `TestS3kDezGravityObjectsHeadless` | implemented (`S3kDezGravitySwapObjectInstance`) | pass 10/10, `ff080949c`+ | Player-2 case is a guard against a future sidekick loop, not evidence about one |
| REWIND spot: flag written by `$5B`, mid-corridor | capture after the write, clear it forward, restore, replay the same crossing | — | `TestS3kDezGravityObjectsHeadless` | implemented | pass | Replay covers the object's `$32` latch as well as the global flag |
| GRAVITY: `$58` writer — toggle on the 4th update, both faces, rider release, occupancy-blocked rearm | `loc_48AD6`/`loc_48B7E`/`loc_48B9C` (sonic3k.asm:94800-94910); `eori.b #1`, `d6 & $14`, `move.w #20-1,$30` | — | `TestS3kDezGravityObjectsHeadless` | implemented (`S3kDezGravitySwitchObjectInstance`) | pass 20/20 + `TestS3kDezGravitySwitchArt` 2/2 | Art and sound landed with the class's presentation pass: `Map_DEZGravitySwitch` ($48BEA) at `ArtTile_DEZMisc+$143` palette 1, two frames, and `sfx_Transporter` ($73) on the press frame only. Clip `033-gravity-switch-pad` |
| REWIND spot: `$58` mid-count | capture between the press and the toggle, run past it, restore, replay | — | `TestS3kDezGravityObjectsHeadless` | implemented | pass | Replay toggles on the same update as the first run |
| GRAVITY: `$59` teleporter — mirrored capture window, four refusals, spin ramp, midpoint write, exit nudge, release | `loc_48C44`/`loc_48D2C`/`loc_48DCA`/`loc_48E94` (sonic3k.asm:94913-95168); subtype bit 7 via `rol.b #1,d0 / andi.b #1,d0`, `cmpa.w #Player_1` (`loc_48DF2`, :95080) | — | `TestS3kDezTeleporterHeadless` | implemented (`S3kDezTeleporterObjectInstance`) | pass 10/10 | Nine mechanisms, nine breaks, nine reds. `_unkFAB8` bit 0 (:94965) is deliberately not modelled: its only writer is `Ending_ScreenInit`'s `Obj_5D86A` (:123769), so that refusal is unreachable during Death Egg gameplay. No clip: the object is invisible and its 21 placements are not reachable until the act 2 route |
| GRAVITY: `$5A` gravity tube — both bodies, subtype span and band, mount angle tables, cosine ride, inverted `flip_angle` reflection on exit | `loc_48EEC`/`sub_48F12` (horizontal) and `loc_4906A`/`sub_49090` (vertical), sonic3k.asm:95169-95401; `loc_48FBA` :95278 and `loc_4904A` :95320 are its two flag reads | — | `TestS3kDezGravityTubeHeadless` | implemented (`S3kDezGravityTubeObjectInstance`) | pass 11/11 | Ten mechanisms, ten breaks, ten reds. The vertical body reads the flag nowhere, which is asserted rather than assumed |
| GRAVITY: `$5C` gravity hub — capture window, per-axis centring, the press-masked four-way exit and the launched-state reset | `sub_492D4`, sonic3k.asm:95545-95690; `word_49420` :95672 | — | `TestS3kDezGravityHubHeadless` | implemented (`S3kDezGravityHubObjectInstance`) | pass 10/10 | Eight mechanisms, eight breaks, eight reds. Contains no `Reverse_gravity_flag` reference |
| GRAVITY: `$5F` room, `$61` puzzle | neither contains a `Reverse_gravity_flag` reference; they move the player with `object_control` | — | `TestS3kDezGravityRoomHeadless`, `TestS3kDezGravityPuzzleHeadless` | implemented | pass 8/8 and 12/12 | Both are act 1 placements; the act 1 matrix owns their rows |
| HAZARD: SKL `$6D` invisible shock block | `Obj_InvisibleShockBlock` bit 5; `sub_1F58C` shield reaction, status bits 0/1 choose face | unit: all four flip combinations and five shield states; placed DEZ1 floor: native Sonic 320 | `TestSonic3kInvisibleHurtBlockHObjectInstance`, `TestS3kDezShockBlockHeadless`, placement census | implemented after `674a99f72` | unit and five shield/rewind cases pass | Act-2 placement census verified; act-2 inverted-contact routes, donor/team breadth and native comparison remain open |
| HAZARD: SKL `$52` lightning | `Obj_DEZLightning`, `loc_478BE`–`loc_4791A`; ROM animation `$47926`, map `$4792E` | direct timer cases 0/1/$24/$FF; native Sonic 320 contact | `TestS3kDezLightningHeadless`, placement census and PLC registry | implemented after `de73bead8` | ROM animation/art, previous-list contact and rewind checks; engine clip `050` | Native comparison and donor/team/inverted-contact breadth remain open |
| TRAVERSAL: SKL `$50` conveyor belt | `sub_47854`: unsigned X/subtype window, Y ±$30, grounded only; above/below chooses ±2 pixel carry | unit: both native player slots, all flips and edges; placed DEZ1 belt 320/800 | `TestS3kDezConveyorBeltObjectInstance`, `TestS3kDezConveyorBeltHeadless` | implemented after `bdfd129ff` | carry and restore/forward replay checked | Native comparison, act-2 route and donor breadth remain open |
| HAZARD: SKL `$4D` torpedo launcher | `loc_471D6` visible countdown, `loc_4726C` recoil, `loc_4728A` independent projectile | subtype 0/1/$FF, both directions, full SST pool; production player-contact spot | `TestS3kDezTorpedoHeadless`, census and PLC registry | implemented after `222473083` | ROM art/timers, failure path, same-pass movement and damage restore/replay checked | Native comparison, donor/team breadth and cold route remain open |
| TRAVERSAL: SKL `$4F` staircase | `loc_476EA`–`loc_47814`: four solid SST sections, standing/underside trigger delays, signed word step rounding | all flips, upward/downward and shake variants; actual DEZ1 landing at 320/800 and inverted DEZ2 `$950,$790` landing | `TestS3kDezStaircaseHeadless`, placement census | implemented after `35390ba3c` | timer/art checks, forced graph recreation and placed trigger/carry replay | Act-2 route entry, native comparison and team/donor breadth remain open |
| TRAVERSAL: SKL `$4C` hang carrier | `loc_46FC2`–`sub_4703E`: P1 start, accelerated rise, native ceiling probe, finite horizontal travel, P1/P2 grabbing | unit contact/control/input boundaries; actual DEZ1 ride at 320/800 | `TestS3kDezHangCarrierObjectInstance`, `TestS3kDezHangCarrierHeadless` | implemented after `b1c767647` | real terrain, jump release and forced recreation/forward replay | native comparison, Act-2 entry and donor/character breadth remain open |
| TRAVERSAL: SKL `$4A` floating platforms (10) | `loc_25A7E`, `word_25AB8`, shared `sub_25974`; table offsets include native control word | all nine movers and four status flips; placed horizontal ride at 320/800 | `TestS3kDezFloatingPlatformObjectInstance`, `TestS3kDezFloatingPlatformHeadless`, census and PLC registry | implemented after `8a58aafbc` | ROM art, oscillator bytes, ramp thresholds, real carry and forced recreation/180-frame replay pass | Inverted entry, native comparison, donor/team/character breadth and cold route remain open |
| TRAVERSAL: SKL `$4B` tilting bridge | `loc_46E1C`–`loc_46F54`, signed ROM `byte_46ED8`, prior-standing aggregate, long velocity and delayed free fall | all eight standing rows, P1/P2 sum/cancellation, exhausted forward allocation; DEZ1 320/800; inverted DEZ2 declared entry | `TestS3kDezTiltingBridgeHeadless`, census, PLC registry | implemented after `6b7055cea` | nine focused checks: real landing/carry/collapse/floor release, complete graph recreation and forward replay; inverted carry/replay | Cold route, native comparison and donor/roster breadth remain open |
| BOSS: `$A7` end boss | `word_7F0BE` range, `word_7F0C6` arena `$218,$288,$3400,$34E0`, 8 hits, `sub_7F8A0` gravity | — | `TestS3kDezAct2BossHeadless` | missing | unrun | Slice 8 |
| REWIND: event routine words | Registry restore equals capture plus forward replay | 320 | `TestS3kDezPresentationRewind` | implemented | pass, `4e7655bf9` | Mid-flip, act change and boss spots not started |
| ORACLE: Tails act 2 | `runs/s3k-tails-full-chain-all-emeralds/ssz_2` (5,202 rows) | — | `TestS3kTailsFullChainSsz2SegmentTraceReplay` (expected red) | — | blocked: 229 errors, first error frame 0 `camera_y` expected `0x080E` actual `0x0810` (`035e48a58`) | Whole campaign |
| ORACLE: Tails act 2 restart | `runs/s3k-tails-full-chain-all-emeralds/ssz_3` (3,877 rows; act 2 restart, i.e. lifecycle evidence) | — | `TestS3kTailsFullChainSsz3SegmentTraceReplay` (expected red) | — | blocked: 200 errors, first error frame 0 `camera_y` expected `0x044E` actual `0x0450` (`035e48a58`) | Whole campaign |

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

`TestS3kDezWidescreenBackground` covers 320/352/400/528/800. Act 2
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

See the [act 1 matrix](s3k-dez-act1.md#execution-evidence) for the single frontier command;
all six classes ran in one invocation with 0 skips.

2026-09-22 follow-up after `33e6b66d5`: straight energy bridges now retain the
expiry draw and consume carried render visibility for zap sound. The new DEZ
retirement tails use the established viewport term (native `$280` unchanged),
including the turbine's extra `$400`. The 87-case affected-family selection
passes with zero skips; seeded frontier remains 1256, cold entry remains 0.
This does not close Act-2 route or participant breadth.

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

### Seamless entry connected (2026-09-23)

`TestS3kDezSeamlessActChange` traverses the actual result signal and production
Kos queues into the synchronous reload. It checks player/camera rebasing,
reverse-gravity retention, palette lines 0–1, event routines and eight-pass
retained-background redraw with capture/restore/forward replay.
`TestDezMinibossEncounter` checks the complete defeat/results/transport chain
from left, centre and right finishing positions; `TestDezMinibossTransport`
checks the native transport clocks, independent camera workers and title owner.
Solo Hyper recordings 098/099 in the external task capture directory reach Act 2
and return control at 800/320. These positioned runs do not certify the cold
entry trace, full-act route or character/team/donor breadth.
