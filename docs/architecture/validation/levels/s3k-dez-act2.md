# S3K Death Egg Zone act 2 coverage matrix

**Current route revalidation (2026-09-25, based on `3881f549a`):** the
preserved cold ordinary ending movie initially died at input21410 after the
native spike correction. Its controller inputs are now repaired:40316 frames
to the final stage and54692 through the ordinary ending, zero deaths. The dated
follow-up below distinguishes current verification from historical route lengths.

Game / canonical zone / act: S3K `S3K_DEATH_EGG_2`, engine zone `$0B` act index 1,
ROM `Current_zone_and_act = $B01`, SKL object set. **Not Sonic 2's Death Egg.**
Owning plan: [S3K DEZ bring-up](../../plans/2026-09-17-s3k-dez-bring-up.md).
Status: traversal/gravity families and end boss are implemented. Positioned
DEZ2-boss → final arena → complete DDZ controller routes at320/800 have eleven
whole-registry replay spots per width. Cold native320 Sonic+Tails now completes
both main DEZ acts and loads the final stage in40316 controller frames, zero
deaths. Eight Act2 routes carry204 full-registry replay spots; the actual final
load starts an isolated frame-zero timeline. Width/roster/donor and remaining
lifecycle breadth and native whole-scene acceptance
remain open. Historical slice
rows below are superseded by the dated follow-ups. Nothing below certifies the act.

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
| Implemented | Presentation foundation (static background, the two shared `AnPal_DEZ2` channels, the eight `AniPLC_DEZ` scripts, both `DEZ2_ScreenEvent` chunk stages, the direct-load routine values), reverse gravity for the player, shields, lost rings, solid objects and dust, springs and the sidekick (112 of 116 ROM references covered, none partial or missing and 4 not applicable — the open rows are listed in [s3k-known-bugs](../../../status/s3k-known-bugs.md)), the implemented gravity interaction families, and the traversal/badnik/shock-block families listed below (494/494 concrete placements) |
| Cold-reachable | Ordinary native320 Sonic+Tails from cold DEZ1 completes both main acts and loads final DEZ (`$1700`) in40316 frames, without death, health setup or transformation. Preserved `dez2-sonic-tails-incoming-clear-320` route; strict trace parity is a separate claim below |
| Rewind-verified | Eight cold Act2 routes total204 full-registry capture/restore and45-frame replay spots, including the gravity boss and exit. The final full load resets to frame zero; seeking that earliest snapshot retains zone23. Component and positioned320/800 encounter checks remain linked below; broader lifecycle/breadth still open |
| Native behaviour matched | The seeded route's first 1256 frames match native exactly in position, camera and rings; the first divergence, native row 21029, is a one-pixel `x` lag while riding a shared `$08` platform — not a Death Egg object. The six segment replay classes are unchanged from the `035e48a58` measurement below |
| Visually matched | Cold-route moving captures cover traversal, gravity switches, springs, carrier/launchers, unshielded tilting bridge, winding transports, eight-hit gravity boss defeat and final-stage arrival. Latest native320 clips and inspected frames are linked in the dated follow-ups. This is engine presentation evidence; matched native pixel acceptance and full width/roster breadth remain open |

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
| PLACEMENT: 494 act 2 objects | [inventory](../../research/s3k-zones/dez-object-inventory.md) | — | `TestS3kDezPlacementCensus` | 494 concrete / 0 placeholders | pass | Slices 3-5 |
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
| TRAVERSAL: SKL `$4F` staircase | `loc_476EA`–`loc_47814`: four solid SST sections, standing/underside trigger delays, signed word step rounding | all flips, upward/downward and shake variants; actual DEZ1 landing at 320/800 and inverted DEZ2 `$950,$790` landing | `TestS3kDezStaircaseHeadless`, placement census | implemented after `35390ba3c` | timer/art checks, forced graph recreation and placed trigger/carry replay | Cold native320 Sonic+Tails staircase entry/carry/replay covered by incoming routes; native comparison and further team/donor breadth remain open |
| TRAVERSAL: SKL `$4C` hang carrier | `loc_46FC2`–`sub_4703E`: P1 start, accelerated rise, native ceiling probe, finite horizontal travel, P1/P2 grabbing | unit contact/control/input boundaries; actual DEZ1 ride at 320/800 | `TestS3kDezHangCarrierObjectInstance`, `TestS3kDezHangCarrierHeadless` | implemented after `b1c767647` | real terrain, jump release and forced recreation/forward replay | Cold native320 Sonic+Tails carrier entry/travel/release/replay covered by the incoming roof route; native comparison and donor/character breadth remain open |
| TRAVERSAL: SKL `$4A` floating platforms (10) | `loc_25A7E`, `word_25AB8`, shared `sub_25974`; table offsets include native control word | all nine movers and four status flips; placed horizontal ride at 320/800 | `TestS3kDezFloatingPlatformObjectInstance`, `TestS3kDezFloatingPlatformHeadless`, census and PLC registry | implemented after `8a58aafbc` | ROM art, oscillator bytes, ramp thresholds, real carry and forced recreation/180-frame replay pass | Inverted entry, native comparison, donor/team/character breadth and cold route remain open |
| TRAVERSAL: SKL `$4B` tilting bridge | `loc_46E1C`–`loc_46F54`, signed ROM `byte_46ED8`, prior-standing aggregate, long velocity and delayed free fall | all eight standing rows, P1/P2 sum/cancellation, exhausted forward allocation; DEZ1 320/800; inverted DEZ2 declared entry | `TestS3kDezTiltingBridgeHeadless`, census, PLC registry | implemented after `6b7055cea` | nine focused checks: real landing/carry/collapse/floor release, complete graph recreation and forward replay; inverted carry/replay | Cold native320 Sonic+Tails traversal and full-registry replay now covered by the incoming tilt route; native comparison and donor/roster breadth remain open |
| BOSS: `$A7` end boss | `word_7F0BE` range, `word_7F0C6` arena, enemy-published eight hits, allocation prefixes, breakup and `$1700` request | 320/800; native P1/P2 component cases; solo Hyper movie | `TestDezEndBossEncounter`, child/resource suites, `TestS3kDezTeleporterHeadless` | implemented | focused checks pass, 2026-09-23 local campaign | Cold native320 Sonic+Tails route through all eight hits and real final-stage load now passes; native parity and remaining character/donor/team/width breadth open |
| REWIND: event routine words | Registry restore equals capture plus forward replay | 320 | `TestS3kDezPresentationRewind` | implemented | pass, `4e7655bf9` | Incoming route gravity flips, DEZ1-to-Act2 timeline isolation and positioned boss graph replay now covered by campaign suites; full Act2 cold completion and remaining breadth open |
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
At that checkpoint placement counts were 364/365 and 493/494. Both bosses are now registered: 365/365 and 494/494; this census does not certify either act.
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

### Act 2 boss component preparation (2026-09-23)

At the component checkpoint the encounter remained unregistered. `TestDezEndBossResources` checks all forty mapping frames against the
ROM archive and the real physical/module queues through final uploaded pixels.
`TestDezEndBossBumper` checks native-slot tracking, opening-angle clamping, all
four launch phases, deferred P1/P2 contact, defeat conversion and reconstruction
of two bumpers with their common parent through forty replayed passes.
`TestDezEndBossDamageState` checks the enemy-centred signed hitbox edges, vertical
orientation, publication versus consumption, thirty-two flash passes, six CRAM
words and pending-eighth-hit restoration. These component checks do not establish
the enemy lifecycle, actual fight, final child allocation prefixes or `$1700`
exit. The full source-backed encounter graph is recorded in the bring-up plan.

The subsequent child checks add `TestDezEndBossShield` (5) and
`TestDezEndBossEnemy` (9), all with zero skips. They cover opening/retraction touch
windows, same-sweep visor creation, gravity-before-motion under both signs,
actual arena ceiling/floor probes, kick/flip timing, three-shot allocation
prefixes and parent rewrites, and linked reconstruction with forward replay.
`TestObjectTerrainUtils` adds nine passing regressions for the injected ceiling
entry. These are component proofs; the subsequent encounter checkpoint below connects the root.


### Act 2 encounter connection (2026-09-23, after `5712654ae`)

The SKL `$A7` factory now owns the complete gravity-boss encounter; the S3KL
Carnival Night mapping is unchanged. Short tests exercise the entry, 192-pass
descent, launch/shield/visor graph, actual enemy hit publication, inherited
killing-hit wait, independent allocation prefixes and allocation failure without
healing, persistent gravity cleanup, explosion paths, door/foreground publication,
camera release and `$1700` request. The teleporter gate now consumes `_unkFAB8`
bit 0 for fresh P1/P2 riders while allowing active rides to finish.

Recordings 100/101 show neutral opening runs; 102/103 show the last hit, breakup
and escape at 800/320. The input uses a declared positioned start `$34B0,$300`,
200 rings and seven Super Emeralds, then controller inputs only. Both 6830-frame
runs have zero hurt/death rows. Player states match through the killing hit at
6344 and first differ at 6346 during gravity cleanup; no native parity is claimed.
The 320 recording loads `$1700`; the 800 recording ends during its fade, and the
longer controller probe confirms that load. The destination arena is still missing.
The final 14-test encounter run includes 120 replayed attack passes and 200 replayed defeat passes after graph removal/recreation, checking shared parent links and event/camera state. Cold route, non-Hyper fight, donor and full team breadth remain open.


#### Shared arena centering (2026-09-23)

The native viewport is centered at wide resolutions without changing player
boundary words. `TestNativeArenaCameraFraming` covers LRZ1 and DEZ2 at all five
widths, both view limits, unchanged bounds, fresh state and captured policy
restoration. The LRZ arm regression retains world anchors `$2C20/$2D20`;
release checks exercise either side of both rebased thresholds and the
production seamless transition carries the framing flag. DEZ encounter tests
cover the native exit wall and the original final-act request threshold.
Positioned captures live under the campaign archive: LRZ
`miniboss-centered-20260923-800-v2`, DEZ `104-end-boss-centered-800`.
The 600-frame LRZ native run is unchanged and its wide gameplay CSV matches
exactly except for camera X minus 240. The DEZ 800px controller replay completes
eight real enemy hits and loads zone 23 after 6837 steps, no hurt/death rows.
These bounded checks do not close inherited cold-route, donor/team, native
parity or whole-zone rewind obligations. Combined delivery validation remains due.


#### Fixed widescreen X, live Y (2026-09-23)

The camera policy now fixes wide-view X at the centre of the ROM camera range,
keeps Y tracking and native player boundaries, and releases at corridor opening.
320px retains horizontal tracking. Five width cases plus existing camera and
encounter checks pass in a 60-case selection, zero skips; capture/restore includes
the anchor. Capture `105-end-boss-x-locked-800` holds X `$3380` after entry and
shows moving Y; its first 700 state rows match capture 104 except `cam_x`.
The positioned input replay still defeats all eight hits (last hit 6344) and
loads `$1700` after 6835 steps without hurt/death. Full cold-route, native and
breadth obligations remain open; no final-arena completion is implied.


### Incoming DEZ2 encounter chain (2026-09-23)

`TestDezIncomingFinalRouteCapture` native320 passes1test with zero failures,
errors or skips at22:29:25 BST on b6c1147a2 plus campaign edits. Positioned
DEZ2 ($34B0,$300), solo Sonic, donor off, boot-only200rings/sevenSuperEmeralds
continues through actual final-arena and DDZ loads without reseeding. The
21102-frame BK2 independently replays without death, matching all20862 author
rows before its240-input DDZ tail. Eight whole-registry restore/45-input replay
spots cover hands, core, escape ship and live destination flight. Captures116/117
show the two handoffs; see the [campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md).
This closes that native positioned continuity row, not cold DEZ2 traversal,
complete incoming DDZ combat, native parity or roster/donor/lifecycle breadth.


Two-width incoming follow-up: queued `-Dtest=TestDezIncomingFinalRouteCapture`
passes2cases, zero failures/errors/skips,22:34:53 BST (52.002s Maven). Both320
and800 verify every registry key at all eight restore/45-input replay spots.
The wide21109-input movie independently matches all20869 author rows, no deaths
or follower; capture118 fully decodes and stills20582/21050 were inspected.
The remaining240inputs show actual DDZ flight. No gameplay change was needed.
Complete incoming DDZ combat and cold DEZ2 traversal remain separate open rows.


Complete incoming DDZ verification now passes at320/800: queued Java21 with
absolute S3K ROM, `-Dtest=TestDezIncomingFinalRouteCapture test`,2cases, zero
failures/errors/skips, BUILD SUCCESS22:43:32 BST (62s Maven). The30918/31531
controller inputs run from the positioned DEZ2 boss through the final arena,
both DDZ phases and actual $D01 request, without deaths or reseeds. All11
required spots per width compare every registered key on restore and45-input
replay, including DDZ body damage, chase wrap and defeat. The11-family child
spawn regression plus59 mandatory S3K checks separately pass70cases, no skips
(22:42:16 BST). Earlier freshSuper route cases also pass; these are focused
checks, not the combined campaign suite. Wide incoming completion video is
`$VIDEO_ROOT/ddz-bring-up/campaign-20260923-incoming-dez2-completion-800/capture.mp4`:
31531state rows, no deaths/followers,7finalrings,1531filmed frames, full decode
passed;30369/31291/31530 inspected (last is white exit fade). It predates the
recreation-only fix, which does not run during normal forward playback.
Cold DEZ2 traversal, roster/donor breadth, history isolation and native whole-scene
matching remain open. Ending/credits remain excluded.

### Cold incoming route: vertical tube and tail mirroring (2026-09-24)

On `75e536735` plus this patch, ordinary Sonic+Tails input continues from the
verified DEZ1 clear through the Act2 entrance and the first conveyors. At the
placed vertical tube(3136,1248), the old implementation froze Sonic atY1057
with Y velocity1384. ROM `loc_49120` sets object_control bits6 and1, leaving bit0
clear; `Sonic_Control` at `loc_10BFC` therefore still runs movement. The tube now
uses the existing movement-active control state. It swings X while ordinary
player physics carries Y through the span. No shared physics algorithm changed.

`TestS3kDezGravityTubeRouteHeadless#placedVerticalTubeKeepsPlayerPhysicsMovingThroughItsSpan`
reproduced the stall before the fix using the actual placement. It now checks
progress, release and full-registry capture/restore/forward replay during the ride.
The cold input reaches the gravity hub at(3136,1472) with11rings and no death;
that hub intentionally needs a fresh direction press rather than a continuously
held direction, per its native input contract. Further traversal is still open.

The reverse-gravity inventory also exposed missing `Obj_Tails_Tail` rendering.
`loc_1613C` mirrors non-directional tail animations, except animation3, whose
angle already supplies its flips. `TailsTailsController.draw` now composes the
flag without mutating stored animation state. The regression covers standing,
spindash, flying, directional rolling, flag release and unchanged rewind state.
The group F row is covered; dust, Knuckles and other listed gaps remain open.

Queued Java21 with the absolute S3K ROM, `-Dmse=off`:
`-Dtest=TestS3kDezGravityTubeHeadless,TestS3kDezGravityTubeRouteHeadless,TestS3kReverseGravityRenderMirror,TestTailsTailsFlightSelection,TestTailsTailsDirectionalAnimation,TestSpriteManagerMainTailsTailsDispatch,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passed87 tests, zero skips. The subsequent explicit in-tube replay addition was
verified with `-Dtest=TestS3kDezGravityTubeRouteHeadless`:3 passed, zero skips.
The change-based plan selects the full suite through shared tail code; these are
focused iteration checks, with combined campaign delivery validation still owed.

Preserved `dez2-sonic-tails-incoming-first-hub-320.{script,bk2}` contains the
verified16,201-frame cold prefix through this hub. Its authored BK2 input rows
were compared exactly with the explored movie. The inspected native320 clip
`$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-vertical-tube-320/capture.mp4`
films15780–16599; all16,600 state rows are death-free and full video decode passes.
This is engine visual evidence; native pixel parity and other widths remain open.

### Reverse-gravity dust consumers (2026-09-24)

On `b89b2c41f` plus this patch, `loc_18C20` now composes the spindash-dust Y flip
and reverses Tails's four-pixel centre adjustment. `loc_18D14` now negates the
complete per-character skid-dust foot offset at spawn. Detached skid puffs keep
their own world coordinates; gravity changes do not relocate an existing puff.
No new state, ROM assets, game/zone carve-out or public API was added.

The new `TestS3kReverseGravityRenderMirror` case failed before the change, then
passed for Sonic and Tails, upright→inverted→upright, using the renderer call and
actual spawned skid coordinates. Queued Java21, absolute S3K ROM, `-Dmse=off`:
`-Dtest=TestS3kReverseGravityRenderMirror,TestSpindashDustControllerSplash,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passed71 tests, zero skips. The change-based plan selects the full ordinary suite
through shared code; this is focused iteration, with campaign-wide validation
still pending. Group F's two remaining rows are now covered; the table records97
covered ROM references and eight remaining group A-I gaps rather than certifying
the whole reverse-gravity implementation.

Positioned visual evidence:
`$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-inverted-tails-dust-320/capture.mp4`
uses Tails at(4700,1267), declared reverse gravity,181 controller frames,
`30 -;20 D;1 D+A;90 D;40 R`. Frames70/120 are charging; the visible dust sits at
the ceiling with Tails's mirrored centre offset. This is engine inspection,
not cold-route or native-pixel evidence.

Independent ordinary route authoring continued from the preserved first hub:
release Right before the hub's fresh Right press; jump the ceiling steps, and
wait120frames before the Spikebonker crossing. The explored cold route reaches
(5899,1171) without changing the enemy. Later traversal and route breadth remain
open; exploratory inputs stay in the external campaign route-author directory
until the next stable route is preserved.

### Knuckles ceiling slide get-up and lower-route research (2026-09-24)

On `8ca9b0ea2` plus this patch, the remaining `Knuckles_Sliding .getUp` gravity
row is implemented. ROM `loc_16B2A` computes liveYRadius−defaultYRadius,
negates that word under Reverse_gravity_flag, then adds it to y_pos before
Knux_TouchFloor restores standing radii. The engine previously always applied
the upright adjustment: the new regression measured823 where841 was required
for an inverted centre at832 with radii10→19. The correction uses a native
centre-word addition and retains the fractional Y word. No physics constants,
terrain probes or wall-climb behavior were changed.

`TestPlayableSpriteMovement#knucklesSlideGetUpPreservesFeetAndFractionUnderReverseGravity`
failed before the correction and passes afterward, covering both gravity signs,
radius restoration, grounding and a nonzero Y fraction. Queued Java21,
`-Dmse=off`, absolute S3K ROM:
`-Dtest=TestPlayableSpriteMovement,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passed238 tests, zero skips. The change-based plan selects the full ordinary
suite through shared movement; this is focused iteration pending combined
campaign validation. The inventory now records98 covered references and seven
open group A-I rows; this does not certify Knuckles's remaining gravity paths.

The earlier rightward route from the first DEZ2 hub reached a monitor alcove at
Y1171. The native complete-emeralds recording reaches this region from below:
trace rows25260–25680 travel from(5640,2604) through the rising teleporter to
(6078,1363). It is not evidence that the alcove's wall collision is wrong.
Rows21545–21610 show a Down launch/bounce before the Left exit from the first
hub. Controller authoring also reaches the lower route directly with a fresh
Left press. The descending conveyor between spiked walls requires staying near
its middle until the lower exit; immediate Right drift or an early jump hits
the wall. Native rows are comparison/route research only, never gameplay writes.

The corrected controller input keeps the rider nearX1560 against the conveyor's
belt untilY1960, then exits right. The full cold route reaches(1937,2003),19rings,
zero deaths in17,180frames. This is a lower-route frontier, distinct from the
previous monitor-alcove X maximum. The inspected
`$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-lower-conveyor-320/capture.mp4`
films16200–17179; state rows, selected stills16750/16900/17150 and full MP4 decode
checked. `campaign-20260924-route-author/act2-lower-centred-belt.{script,bk2,csv}`
preserves the external exploration, with no gameplay state writes. The next
obstacle is the corridor's spiked overhang; no collision change was made for it.


### DEZ2 lower staircase and second tube cold route (2026-09-24)

On `1ccf38505` plus this validation change, the ordinary cold DEZ1 Sonic+Tails
route now reaches DEZ2(3196,2476), seven rings, in17921frames with zero deaths.
The preserved `dez2-sonic-tails-incoming-lower-320.{script,bk2}` continues through
the incoming sequence, first gravity hub, descending conveyor, spiked overhang,
Spikebonker, moving staircase and second tube's polarity release. Jumping before
the overhang removes the stationary approach problem; waiting120frames at the
next step avoids the Spikebonker. No runtime collision or enemy adjustment was
needed. A later hit in the lower corridor loses rings but does not kill the player.

`TestDezColdRouteCapture#coldIncomingActTwoTraversesGravityTubesConveyorAndStaircaseWithRewind`
passed1test, zero failures/errors/skips, with17 full-registry capture/restore and
45-frame forward-replay comparisons. Command: queued Maven, Java21,
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`
plus that method's `-Dtest` selection and `test`. Earlier independent DEZ1 tests
own the outgoing act's rewind spots; this test adds Act2 arrival, hub, belt,
staircase and tube spots and asserts the carried endpoint and follower roster.
This is focused route validation; combined campaign delivery remains pending.

Video `$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-lower-route-320/capture.mp4`
films16940–17920. All17921state rows, stills17080/17440/17660, and full MP4 decode
were checked. This is engine presentation evidence, not native pixel parity.
The next frontier is the timed energy-bridge ascent nearX3264–3328. Running past
it drops into the curved lower floor. Braking over the floating platform reaches
(3253,2556); the first jump's timing misses the bridge. These rejected inputs do
not establish a collision defect. Full Act2 completion and breadth remain open.


### DEZ2 energy-bridge ascent and gravity-switch cold route (2026-09-24)

On `7d39f684a` plus this validation change, the new preserved
`dez2-sonic-tails-incoming-middle-320.{script,bk2}` reaches(4853,2371),14rings,
in18931frames from ordinary cold DEZ1 Sonic+Tails, zero deaths. It retains the
previous lower-route input and adds braking onto the floating platform, three
jumps through sequential energy bridges, the launcher crossing, gravity-switch
landing and the next corridor. The two lower bridges activate before the higher
bridge: jumping into their off phase was the route issue, not a demonstrated
collision defect. Horizontal steering onto the gravity switch was authored with
a read-only feedback probe and preserved as fixed ordinary controller inputs;
no gameplay state is hydrated or overridden.

`TestDezColdRouteCapture#coldIncomingActTwoClimbsEnergyBridgesAndTogglesGravityWithRewind`
passed1test, zero failures/errors/skips, with14 full-registry capture/restore and
45-frame replay spots. Queued Java21 command: `-Dmse=off`
`-Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`, the named
method's `-Dtest` selection, and `test`. This adds coverage beyond the shorter
lower route's17 spots; combined campaign validation and full Act2 remain open.

`$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-middle-route-320/capture.mp4`
films17920–18930. Its18931state rows, stills18180/18340/18820 and complete MP4
decode were checked. This is moving engine presentation evidence, not native
pixel matching. Later external exploration in
`campaign-20260924-route-author/act2-spring-pair-late-jump.{script,bk2,csv}` jumps
at18960 before the spring trap, passes both transporters atX5456 and5968, and
reaches the upper corridor nearX6400. Earlier jumps hit the nearby geometry or
arrived between the opposed springs; the later jump crosses normally. That
extension still needs preserved route/rewind and video evidence; no runtime
change was made for these route-authoring failures.


### DEZ2 transporters and Chainspike parent retirement (2026-09-24)

On `812b78342` plus this patch, the preserved ordinary cold Sonic+Tails route
`dez2-sonic-tails-incoming-transporters-320.{script,bk2}` reaches(6709,1395),
15rings, zero deaths, in19810frames. It jumps out of the first spring pair,
rides both transporters atX5456/5968 and the intervening lift, then clears the
upper spring/Spikebonker approach. The route test asserts both transporter
holds and final free movement, adds14 full-registry 45-frame replay spots, and
keeps the earlier31 Act2 spots in shorter independent routes.

The new replay spot19730 failed on the unmodified parent implementation: replay
left an extra Chainspike child and used slot where forward simulation had none.
The child excluded its final parent from capture and recreated against whichever
live Chainspike was nearest. ROM `loc_91D8C` reads exact `parent3`; proximity is
not its ownership rule. The child now captures/restores an exact ObjectRefId
sidecar, and recreation preserves its saved spawn before relinking.

A generic strict reference was tried first and rejected by the capture itself:
a child can be waiting for its next update after the parent has left the manager.
An explicit sidecar represents that retired parent as null; a missing identity
for a still-live parent remains an error. This exposed the second omission:
`Sprite_CheckDeleteTouch -> loc_85094` sets status bit7 before scheduling deletion,
but manager-owned offscreen removal had left the Java body unmarked. Chainspike
now publishes retirement in `onUnload`, so `Child_CheckParent`'s child deletion
has its corresponding signal. The short regression covers exact replacement
identity, manager removal and the one-update orphan tail. No nearest-body
fallback or additional coverage gap was accepted. The architecture guard records
why this tombstone-bearing reference requires an explicit sidecar.

Queued Java21, native GL, absolute S3K ROM and `-Dmse=off`:
`-Dtest=TestDezColdRouteCapture#coldIncomingActTwoTraversesBothTransportersWithRewind,TestChainspikeBadnikInstance,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passed70tests, zero failures/errors/skips. Separate fresh-JVM `-Pguards` selection
`TestRewindArchitectureGuard,TestRewindFieldDispositionGuard,TestRewindCoverageGuard`
passed6tests, zero failures/errors/skips. This is focused iteration; campaign-wide
validation, integration, push and cleanup remain pending.

Video `$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-transporters-320/capture.mp4`
films18930–19809 from the cold start. The corrected build's19810 state rows
are identical to the pre-fix forward capture (zero deaths); complete MP4 decode
and the upper crossing still19770 pass inspection. This is engine evidence,
not native pixel parity. Later external input exploration
reaches the gravity switch at(7232,1720): staying over it during the jump toggles
gravity and rises to(7216,659). Overshooting the switch hits the monitor corridor.
`campaign-20260924-route-author/act2-east-switch-catch.{script,bk2,csv}` preserves
that exploration; the upper-left continuation and full Act2 completion remain open.


### DEZ2 upper gravity route, hanging carrier and countdown launch (2026-09-24)

On `6fc411db0` plus this route-only change, ordinary cold DEZ1 Sonic+Tails reaches
(8759,1132) in22960frames, zero deaths. The preserved
`dez2-sonic-tails-incoming-roof-320.{script,bk2}` includes the second pressure-pad
switch, vertical tube ascent, inverted spindash around the upper bend, lower
spring return, hanging carrier rise/travel/release, roof walkway, countdown
launcher and far-side conveyor descent. The launcher captures both real team
members and releases through its production controller; no positioned setup,
health seed or gameplay-state write is involved. Damage leaves zero rings at the
endpoint, so later route authoring must retain that vulnerability.

The native complete-emeralds DEZ segment (stored in the SSZ trace container) rows
26370–26410 explains the bend: the original input crouches/spindashes, then carries
roughly-$800 ground velocity around the curve. Merely running left from a stand
slides back, and an ordinary jump loses lateral momentum. An authored spindash
reproduces the functional crossing; those failed inputs did not justify a physics
change. Native rows are read-only comparison evidence, never state hydration.

`TestDezColdRouteCapture#coldIncomingActTwoCompletesUpperGravityRouteCarrierAndCountdownLaunchWithRewind`
passed1test, zero failures/errors/skips, with28 full-registry capture/restore and
45-frame replay spots. The shorter Act2 routes retain45 earlier spots, for73
across four preserved routes. Queued Java21 command uses `-Dmse=off`, native GL,
absolute `-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`, the named method's `-Dtest`
selection and `test`. It also asserts carrier/launcher control and final release.
This focused route test does not replace the pending combined campaign checks.

Video `$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-roof-route-320/capture.mp4`
films20480–22959. All22960state rows, stills20780/21200/21880/22040 and complete
MP4 decode were checked. This is engine moving presentation, not native pixel
matching. Continuing left reaches the lower shaft near(7507,1250); holding Left
rides its edge and returns upward. Steering toward its centre too early also
returns to the entry ledge. The next task is the deeper curved-wall/retracting-spring shaft and
route onward to the boss; full Act2 completion and breadth remain open.

### Inverted flat top-solid spring contact (2026-09-24)

On `831ba74c7` plus this fix, a real DEZ2 retracting-spring contact exposed the
final landing-height override forcing the upright face after the mirrored contact
and radius restoration. The isolated inverted standing case ended at Y891 rather
than ROM-derived Y948: a 57-pixel error. `loc_1E45A` uses
`objectY - d3 - radius - 1`; `loc_1E4D6` uses `objectY + d3 + radius`, then
`sub_22F98` applies its inverted -8 spring nudge. The override now preserves this
asymmetry. No spring velocity or terrain physics was tuned to the route.

Queued Java21 validation with native GL and the absolute S3K ROM path:
`python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen -Dtest=TestS3kDezRetractingSpringHeadless,TestS3kReverseGravitySolidObject,TestDezColdRouteCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passed 81 tests, zero failures/errors/skips. The new real-contact regression
failed before the fix and passes upright/inverted, standing/rolling afterward.
All seven preserved DEZ cold routes still pass. This is focused validation;
combined campaign checks remain pending. The inventory row is partial because
sloped variants and exact comparison-window boundaries need separate evidence.

Shared-consumer follow-up: queued `-Dmse=off
-Dtest=TestObjectSolidContactController,com.openggf.game.sonic2.objects.TestTopSolidRoutineProfileAdoption,com.openggf.game.sonic3k.objects.TestTopSolidRoutineProfileAdoption test`
passed 8 tests, zero failures/errors/skips on the same candidate.

### Cold inverted spring shaft route (2026-09-24)

On `2cc685260`, the preserved
`dez2-sonic-tails-incoming-shaft-320.{script,bk2}` reaches (8211,1683)
with 3 rings in 23741 ordinary controller frames from cold DEZ1 Sonic+Tails,
without death or gameplay-state writes. It crosses the curved wall, both inverted
retracting springs, the return-facing corridor spring and the first ceiling step.
Holding Left too long after the second spring returns to the shaft; steering
right during its launch arc clears the exit. This is input authoring, not an
additional physics correction.

Queued Java21 `-Dmse=off -Dopenggf.test.gl.native=true
-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen
-Dtest=TestDezColdRouteCapture#coldIncomingActTwoDescendsInvertedSpringShaftWithRewind test`
passed 1 test, zero failures/errors/skips. Fourteen full-registry capture/restore
and 45-frame replay spots cover the new interactions, bringing the five preserved
Act2 routes to 87 spots. Combined campaign validation is still pending.

Video `$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-inverted-spring-shaft-320/capture.mp4`
shows frames23240–23499. All23500 state rows, stills23340/23416/23480 and full
MP4 decode were inspected; zero deaths. This is engine presentation evidence,
not native pixel parity. Later exploratory input has reached the lower corridor
near (8501,2292); full Act2 traversal and its remaining breadth are open.

### Cold lower gravity switch and unshielded tilting bridge (2026-09-24)

On `c07f4a177` plus this route-only change,
`dez2-sonic-tails-incoming-tilt-320.{script,bk2}` reaches (9525,2156) with
7 rings in 25111 ordinary controller frames from cold DEZ1 Sonic+Tails, zero
deaths and no gameplay overrides. The second spring shaft leads to a leftward
backtrack and jump onto the pressure pad at (8020,2120). Continuing straight
right misses that gravity toggle and cannot use the lower staircase correctly.
The route then deploys the staircase, weights the tilting bridge's left end,
crosses its rising segments with staged jumps and rides the following lift.

The native complete-emeralds DEZ segment stored in the SSZ trace container uses
a lightning-shield double jump at row30075: airborne velocity changes from
$0198 to $FAB8 on jump input. Our route has lost that shield, so copying that
input is insufficient. Holding the bridge's left end too long caused collapse;
crossing too early left its right end too low. The preserved ordinary input
uses the existing ROM-backed bridge acceleration table to cross without a shield.
No runtime change or native-state hydration was needed.

Queued Java21 command `python3 tools/testing/maven_queue.py -Dmse=off
-Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen
-Dtest=TestDezColdRouteCapture#coldIncomingActTwoCrossesTiltingBridgeWithoutAShieldWithRewind test`
passed 1 test, zero failures/errors/skips after adding the explicit no-shield
assertion throughout the bridge crossing. Its 26 full-registry capture/restore
and 45-frame replay spots bring the six preserved Act2 routes to 113 spots.
This is focused route validation; combined campaign checks remain pending.

Video `$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-tilting-bridge-320/capture.mp4`
films23800–25110. All25111 state rows, stills24160/24340/24540/24740/25030 and
full MP4 decode were checked. This is engine presentation, not native pixel
parity. Further exploratory input clears the next spring and tube ascent to
(10325,851); the native route then turns left. Full Act2 completion and remaining
width/roster/lifecycle breadth remain open.

### Upper transport chains, hub handoff and trail retirement (2026-09-24)

On `893c9834c` plus this change, the preserved
`dez2-sonic-tails-incoming-chain-320.{script,bk2}` reaches the east hub's lower
chamber at (12992,2112), with 4 rings, in 28261 ordinary controller frames from
cold DEZ1 Sonic+Tails. Sonic is captured by the destination hub; Tails is present at the same coordinates.
The route includes the upper tube, westward launcher/sine transport, eastbound
corridor, another winding transport and the hub's Down command. It has zero
deaths and no gameplay overrides; the hub waits until a Left command continues.

A new full-registry snapshot exposed an invalid controller-to-spawner reference
after the trail completed. The independent short test
`TestS3kDezTunnelLauncherHeadless#finishedTrailRetiresBeforePlayersWithoutLeavingARewindReference`
reproduced the same identity-table error before the fix. ROM
`Obj_DEZTunnelControl` initializes the player timers to10 but leaves the trail
timer zero; the trail thus finishes first. `DEZTunnelControl_Done` never reads its
completed channel's pointer, while `loc_4889E` deletes the spawner in its own slot.
The engine now drops that unused Java link as the channel completes, preserving
the final spawner update and deletion. Keeping the child alive or weakening
identity validation would conceal the lifetime mismatch and was not used.

Queued Java21 command `python3 tools/testing/maven_queue.py -Dmse=off
-Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen
-Dtest=TestS3kDezTunnelLauncherHeadless,TestDezColdRouteCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passed79 tests, zero failures/errors/skips. All10 preserved DEZ cold routes pass,
including the new chain route's32 full-registry capture/restore and45-frame replay
spots. The seven Act2 routes now total145 spots. Combined campaign validation
remains pending.

Video `$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-transport-chain-320/capture.mp4`
shows25070–28260, including the deliberate wait before the Down input. All28261
state rows, stills25380/25990/27030/27490/28260 and complete MP4 decoding were
checked. Recorded on the pre-fix forward path; after the lifetime fix, a fresh
continuation matched the first28260 state rows in every non-input-text field.
At28260 the continuation deliberately presses Left while the recording holds
Right, accounting for its only velocity difference. This is engine presentation,
not native pixel parity. Further exploratory controls traverse the return loop
and reach the upper corridor at (13781,812); boss completion and breadth remain open.

### Cold main-act completion and final-stage load (2026-09-24)

On `6672feb84` plus this route-only change,
`dez2-sonic-tails-incoming-clear-320.{script,bk2}` completes DEZ1, DEZ2 and
the actual `$1700` final-stage load in40410 ordinary controller frames, zero
deaths, with the real Sonic+Tails team and no health/emerald/shield setup or
transformation. The remaining return loops and upper corridor use normal jumps,
tubes and countdown paths. At the gravity boss, repeatedly leaving and re-entering
the left column reverses gravity through production capture logic; released
enemies publish all eight hits. A first cross-arena rolling attempt dealt one
hit but died after repeated enemy contact. The safer column input preserves
11 rings through the fight; the final load briefly initializes zero before the
production carry restores them during arrival. No boss/player state is written.

`TestDezColdRouteCapture#coldIncomingActTwoDefeatsGravityBossAndLoadsFinalStageWithoutTransformation`
passed1test, zero failures/errors/skips, with59 new full-registry capture/restore
and45-frame replay spots. It asserts no transformation/death, eight consumed
boss hits, final zone23/act0, roster, and actual live-history isolation. This
brings eight Act2 routes to204 replay spots. Queued Java21 command uses
`-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`
and the named test method's `-Dtest` selection followed by `test`. The affected
DEZ1 seamless-handoff test was also rerun separately:1pass, zero skips/failures.
Combined campaign validation remains pending.

The initial new test passed all replay spots and boss assertions but failed its
last history assertion: it incorrectly reused the seamless handoff's monotonic
frame-number assumption. `LiveRewindManager.handleLevelLoadBoundary` resets both
input and controller numbering to zero; the seamless handler retains numbering.
The corrected test seeks to frame zero and verifies the restored world is final
DEZ, alongside the reset-origin check. This was a test-oracle correction, not a
runtime fix or a relaxed history-isolation obligation.

Video `$VIDEO_ROOT/s3k-dez-bring-up/campaign-20260924-act2-cold-clear-320/capture.mp4`
shows39600–40439, including both remaining hits, breakup, exit and final arrival.
All40440 state rows, stills39680/39990/40190/40340/40420 and complete MP4 decode
were checked; zero deaths. This is engine presentation rather than native pixel
parity. A first ordinary-Sonic continuation clears the final hands and core but
falls in the escape-ship chase at51716 with six ship hits remaining; that cold
final-stage route and remaining viewport/roster/lifecycle/native breadth are open.

The subsequent ordinary-team continuation now clears the final hands/core/ship
and loads the ordinary ending in54786 total frames, zero deaths, with31 additional
final-phase rewind spots. See the [final arena matrix](s3k-dez-final-boss.md).
This supersedes the failed first continuation above.


### Knuckles inverted glide/slide contact (2026-09-25)

On `39c04c324`, the glide/slide helper still probed both feet downward regardless
of gravity. Two new isolated regressions fail: the reversed ceiling probe
returns null, and the slide remains at Y200 instead of the ROM-derived Y203.
`sub_11FD6` selects `Sonic_CheckCeiling`, retains the nearer signed distance,
then mirrors the angle with `+$40 / neg.b / -$40`. `Knuckles_Sliding` negates
the final distance before its Y-word addition. The helper and both glide/slide
snap consumers now follow those branches, preserving native Y fractions.
The ceiling's odd-angle fallback is `$80` before the wrapper, not floor `$00`.

`TestPlayableSpriteMovement` covers both ceiling feet, slope-angle reflection
and the reversed slide snap, alongside its existing upright glide controls.
`TestS3kReverseGravityDezCorridor#knucklesSlideUsesTheRealCeilingAndReplaysItsContact`
seeds the documented gravity flag and slide state against actual DEZ2 terrain:
at X`$1ACC`, ceiling clear Y`$520` plus the native10pxglide radius gives
rest Y`$52A`. It checks the3px expulsion, retained Y fraction, continued slide,
and matching position/speed after a full-world capture/restore and three-input
forward replay. This is positioned contact evidence, not a cold Knuckles route.

The row31004 inventory is implemented. Fall-from-glide radius restoration and
both alternate wall-climb bodies remain open. This is shared movement code;
the inspected plan selects all2915ordinary classes and guards, and matched
cross-game trace checks remain required before campaign integration. Focused
iteration does not replace those gates.

Focused iteration (Java21, absolute S3K ROM):

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  -Dtest=TestPlayableSpriteMovement,TestS3kReverseGravityDezCorridor,TestDezColdRouteCapture test
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  '-Dtest=TestS3kReverseGravityDezCorridor#knucklesSlideUsesTheRealCeilingAndReplaysItsContact,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test
```

The first selection passes236tests (181movement,44existing corridor,
11cold DEZ routes),0failures/errors/skips. The new real-contact case was added
after that compilation and therefore checked separately. The second selection
passes59stability checks; its new contact test initially failed because its
seeded slide retained the standing19pxradius (expected1322/actual1331).
Correcting that setup to the native10pxglide radius requires no production
change. The two isolated regressions failed on the pre-fix implementation.

The corrected contact-only rerun passes1test,0failures/errors/skips:
`python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" '-Dtest=TestS3kReverseGravityDezCorridor#knucklesSlideUsesTheRealCeilingAndReplaysItsContact' test`.
No changed production code needed a repeated cold-route run.


### Knuckles inverted wall climbing (2026-09-25)

At `8a6602ca2`, inverted Up still moved Y`$100→$FF`; the new regression expects
native Y`$101` and fails. `loc_16DA8`/`loc_16C7C` now have explicit branches:

- Up probes the wall at Y+11 and the world floor at Y+8 using lrb solidity.
  Wall distance>=4 starts the existing mirrored ledge animation; a small dip
  stops movement. Negative vertical distance pushes out; otherwise movement
  is+1 (+2 powered), capped by maxY+$D0 unless minY=-$100 indicates wrapping.
- Down undoes the first `$BD` ledge pose, probes the wall at Y-11 and the world
  ceiling at Y-9 using top solidity. Any nonzero wall distance releases the
  grab. Negative vertical distance grounds with a mirrored angle and native
  animation5; zero clearance continues moving -1 (-2 powered).
- The idle `FixBugs=0` floor-distance/animation clobber remains unmirrored,
  as the ROM specifies. The shared upright branch is unchanged.

The new `GlideWallGrabTerrain.climbVerticalDistance` uses `GroundSensor.scanWorld`
with explicit offsets and the player's live solidity bit. Four-direction
probe assertions cover its interface, and actual DEZ2 corridor probes confirm
-3px overlap at each native reversed probe point. No trace row supplies
runtime state. Complete cold inverted Knuckles traversal is still separate.

Focused queued Java21 commands with an absolute locked-on ROM path:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestPlayableSpriteMovement,TestGlideWallGrabTerrain test
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  -Dtest=TestS3kReverseGravityDezCorridor,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test
```

Initial focused selection passes188tests, and the corridor/stability selection
passes105tests, both0failures/errors/skips. The expanded movement selection
adds powered movement and `$BD`-pose release checks. The inspected change-based
plan still selects the full ordinary suite and guards. These focused results
do not replace the campaign's required broad and matched trace validation.

The expanded movement/probe selection passes189tests,0failures/errors/skips.
No production edits followed the105-case corridor/stability pass.


### Knuckles fall-from-glide radius (2026-09-25)

At `f2edd4271`, `Knuckles_Fall_From_Glide` restored standing radii through the
shared collision callback but omitted its preceding native Y-word correction.
Normal released-glide entry already restores the radius, so that path's delta
is usually zero. The regression explicitly seeds10px and19pxfall radii against
the measured DEZ2 corridor. Before the correction, the smaller upright case
lands at1365 instead of1356 (9pxinside the standing contact).

The normal fall path now captures `current_y_radius-default_y_radius` before
collision, then applies its gravity-signed native word addition on landing.
This preserves the existing collision/restoration ownership without applying
the correction to unrelated hurt movement. `Knuckles_Fall_From_Glide`
(sonic3k.asm:30918–30926; reverse test at ROM`$16ACE`) owns the arithmetic.
The fractional Y word survives. The expected contacts are floor`$55F` minus
standing radius, and ceiling clear`$520` plus standing radius.

Queued Java21/absolute S3K ROM verification:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  -Dtest=TestPlayableSpriteMovement,TestS3kReverseGravityDezCorridor,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test
```

291tests pass,0failures/errors/skips. The new contact case exercises both
gravity directions and both radii, requiring stopped velocities, native move
lock and retained fraction. This is focused validation on `f2edd4271` plus the
change, not the pending full campaign or cross-game trace gate. The inspected
change-based plan remains the full ordinary suite plus guards. Inventory is
105covered,5partial,2missing,4not-applicable; monitors/spikes remain unimplemented.


### Upside-down monitor contact and falling (2026-09-25)

At `faacc78e8`, `Touch_Monitor` skipped the direction/Y-flip/position branches
and immediately entered the break rules. Two regressions failed: a knock-loose
contact destroyed the monitor, and an exact rejected position boundary bounced
the player anyway. The port now mirrors the signed Y-velocity copy under reverse
gravity, checks the placement's Y-flip, and performs the unsigned `y+$10` word
comparison before break eligibility. CPU players and non-attacking players can
knock a monitor loose, as the earlier native branch permits. The player's actual
velocity is negated only on an accepted contact.

`Obj_MonitorFallUpsideDown` moves with the previous velocity, applies `-$38`,
then settles against the ceiling at distance <=0. Upright falling retains the
native floor branch, including zero velocity/clearance. Both use the initialized
`y_radius=$F`, not the separate solid-object `d2=$10`. A new captured primitive
tracks touch-initiated motion without making these placed objects persistent.
The hidden-monitor persistence contract remains separate.

Commands (queued, Java21; absolute existing ROM supplied):

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  -Dtest=TestSonic3kMonitorObjectInstance,TestS3kHiddenMonitorInstance,TestS3kReverseGravityDezCorridor,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  -Dtest=TestSonic3kMonitorObjectInstance,TestS3kHiddenMonitorInstance,TestS3kMonitorGraphRewind,TestLiveRewindMonitorState,TestLiveRewindMonitorPresentation test
```

128 tests passed in the first selection. After adding the non-attacking/zero
clearance case, the monitor/rewind selection passed27 tests. Both have zero
failures/errors/skips; the live monitor tests are S2 controls, whereas the new
S3K fall test restores mid-flight and compares subsequent ceiling settling.
The new monitor branches use mocked terrain distances; actual DEZ terrain is
covered by the unchanged corridor tests, not an ordinary monitor traversal.
The change-based plan selects2442 classes plus guards; the combined campaign
still requires its broader delivery selection and shared-movement trace checks.

Inventory is now106 covered,5 partial,1 missing,4 not applicable. This closes
the monitor gravity-reference row only. Existing shell/icon flip rendering and
inverted reward-icon motion are explicitly still open; a full upside-down
monitor presentation capture would be premature.

`-Pguards -Dtest=TestRewindFieldDispositionGuard,TestHelperStateRewindCoverageGuard`
also passes2 tests, zero failures/errors/skips, in a separate queued JVM.


### Monitor shell and contents presentation (2026-09-25)

Follow-up to `b1c00284f`: `Obj_MonitorSpawnIcon` copies placement render flags
into the contents. Both the shell and icon now draw with those X/Y flips.
`loc_1D7CE` reverses initial icon speed and `loc_1D83C` subtracts `$18` each
update. The latter branches on BMI, admitting a zero-velocity tick: upright
reward on update33, inverted reward on update34. The shared base now exposes
a semantic inverted-rise hook (false by default); S3K selects it from placement
Y-flip, never from current world gravity. Applied rewards enter the wait phase
without re-entering motion at zero velocity. No extra mutable state is added.

Two regressions failed before the change: the inverted arc went upward and the
renderer discarded placement flips. The expanded tests cover all four flip
combinations, fixed-point apex motion, zero-velocity/reward timing and replay
through icon expiry. The isolated rewind test rebinds its detached test player
explicitly; the existing S3K graph tests cover registry links separately.

Queued Java21 verification at `b1c00284f` plus the change:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  -Ds3k.rom.path="$REPO_ROOT/s3k.gen" -Dsonic1.rom.path="$REPO_ROOT/s1.gen" \
  -Dsonic2.rom.path="$REPO_ROOT/s2.gen" \
  '-Dtest=*Monitor*,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test
```

118 tests pass, zero failures/errors/skips, including S1/S2 monitor controls,
S3K graph rewind and four S2 EHZ1 monitor-break regression cases. The inspected
change-based selection remains all2915 ordinary classes plus guards; the
combined campaign delivery gate is still pending.

Presentation clip: `$VIDEO_ROOT/dez-bring-up/campaign-20260925-monitor-flips-320/capture.mp4`.
This declared probe uses the FBZ2 start as a clear backdrop, with two inserted
monitors and simultaneous production touch callbacks at frame90. It is not an
ordinary-route claim. Native Sonic solo/320,240 neutral frames, static camera,
zero deaths,20 awarded rings; frames60/121 inspected and full MP4 decode passes.
The first corridor composition hid the motion behind terrain and was rejected.
The final clip shows complete opposing arcs and flipped broken shells.


### Spike hurt-routine selection (2026-09-25)

At `43badd5f1`, spikes used the shared base's mapping-first contact rule. Native
`Obj_Spikes` first selects upright/sideways damage, then `loc_23FE8` XORs a copy
of placement status Y-flip with the current reverse-gravity flag and can install
`loc_2413E` instead. That last assignment overrides sideways too. This is a
one-time native code-pointer selection, not movement dispatch and not a live
world-gravity check. The old inventory note conflated those separate decisions.

S3K now captures the selected underside-contact rule on its init-only execution.
Its own `shouldHurt` preserves native precedence; the sideways push-latch clear
runs only for the selected sideways routine. Subsequent gravity changes leave
the choice intact. The selected boolean is captured by the existing rewind
schema. The shared S2 spike behavior and subtype movement are unchanged.

The branch regression failed before the fix on flipped sideways spikes. It now
checks both gravity states, four placement flags, upright/sideways mappings,
all three contact categories, a later gravity change and restored forward state.
A second test uses the real `ObjectManager`/`SolidObjectFull` path for eight
physical above/below approaches against upright/downward spikes under both
gravity states. CPU Tails supplies a damage witness without ring/death setup.
Its first lower probe was outside the native asymmetric contact window: the
unchanged controller adds4 to relative Y, so centre+29 returned no contact.
Moving the declared probe to centre±25 (six pixels of overlap) reaches both
branches; no production tuning was used to repair that test setup.

Queued Java21 verification:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  -Dtest=TestSonic3kSpikeObjectInstance,TestS3kReverseGravityDezCorridor,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestSonic3kSpikeObjectInstance test
```

The first selection ran120 tests:119 passed and the declared lower-probe setup
failed, zero errors/skips. After correcting only that setup, all14 spike tests
pass with zero failures/errors/skips. The106 unchanged corridor/stability cases
are not rerun. The change-based plan selects2442 classes plus guards; combined
campaign validation remains pending. These isolated contact checks are not a
cold DEZ route, native whole-scene comparison or roster/viewport certification.

The gravity-reference table is now107 covered,5 partial,0 missing,4 not
applicable. The five partial rows and remaining act-matrix obligations still
prevent claiming full reverse-gravity or level completion.

Separate queued `-Pguards -Dtest=TestRewindFieldDispositionGuard,TestHelperStateRewindCoverageGuard`
passes2 tests, zero failures/errors/skips. Mirrored object-pitfall guidance is
byte-identical and `git diff --check` is clean.


### Hurt death-plane early-return proof (2026-09-25)

Production at `39832f596` already implements the inverted early death test in
`applyHurtStopBottomKill`. Previous assertions could not distinguish it from
the later `Player_LevelBound` death. The new
`TestPlayableSpriteMovement.invertedHurtDeathStopsBeforeTerrainForEveryS3kCharacter`
invokes the actual airborne hurt controller with concrete Sonic, Tails and
Knuckles sprites using S3K rules and a terrain-admission observer. Eighteen
cases cover minY`$100` and zero, one pixel above/on/below each boundary, including
signed wrapped Y`$FFFF`. Dying cases must skip terrain entirely; surviving cases
must enter it. A later kill cannot satisfy that observed early-return contract.
This closes the evidence gap without altering engine behavior.

Queued Java21 commands:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestPlayableSpriteMovement#invertedHurtDeathStopsBeforeTerrainForEveryS3kCharacter test
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestPlayableSpriteMovement test
```

The isolated test passes, then the full movement class passes186 tests with zero
failures/errors/skips, including an explicit S3K-rule assertion on each concrete
sprite. An initial test compilation attempted a protected test-only setter;
it was removed in favour of constructing the real sprites under the S3K module.
No production file changed. Focused movement validation is proportionate to this
test/evidence-only follow-up; it does not replace pending campaign delivery gates.
The change-based plan was inspected. Live and target camera bounds are equal in
these cases; the separately documented held-bound mask discrepancy remains.

The reverse-gravity reference inventory is110 covered,2 partial,0 missing,4 not
applicable. Remaining partial rows are edge balancing and top-solid landing
windows/slopes. Whole-route/roster/viewport/lifecycle obligations remain open.


### Inverted edge-balance probe proof (2026-09-25)

At `6dec2c546`, the ceiling ground-sensor rotation already matches
`ChkFloorEdge_ReverseGravity`: preserve requested X, subtract live Y radius,
scan upward with top solidity and mirror the empty-extension Y nibble. The
previous evidence only covered flat ceilings. A synthetic column-ramp test now
uses the real sensors and balance controller for Sonic, Tails and Knuckles.
Paired normal/inverted terrain uses top-only solidity, so an accidental ceiling
hit/lrb mask cannot pass. Left/right edge cases require native distances 11,
12 and empty; Sonic's second pose requires the separate six-pixel probe to
reach distance12. All characters pass at angle`$1F` and reject balance at`$20`,
matching the native angle gate rather than claiming balance on steep slopes.
The previous AnglePos tilt sentinel is explicit fixture setup, not inferred
from the fresh sensor result.

Queued Java21 commands at `6dec2c546` plus the test:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestGroundSensor#s3kBalanceUsesNativeCentreAndSixPixelProbesOnMirroredSlopedColumns test
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  -Dtest=TestGroundSensor,TestPlayableSpriteMovement,TestS3kReverseGravityDezCorridor test
```

The isolated test passes. After adding the rejected-angle controls, the combined
selection passes263 tests with zero failures/errors/skips. It includes existing
real-DEZ corridor tests; the new balance geometry is synthetic and derived from
native FindFloor arithmetic, not a claimed ROM placement or synchronized native
capture. No production code changed. The inspected category plan is broader
than this test-only change; focused sensor/movement/corridor checks are used for
this follow-up, with combined campaign delivery validation still outstanding.

Inventory:111 covered,1 partial,0 missing,4 not applicable. Only the top-solid
landing window/slope row remains partial in this reference table. Full act-route,
character, viewport, lifecycle and presentation obligations remain separate.


### Exact top-solid windows and direct sloped entry (2026-09-25)

At `e7bb9d66d`, flat retracting-spring landing already accepts overlaps 1–16
and horizontal offsets [-16,16), rejecting zero and 17. A real DEZ fixture
exercises 120 combinations of gravity, standing/rolling radius, overlap and X.
The direct sloped helpers instead expose a snap error: their ROM branches go
straight to `loc_1E45A`, bypassing the gravity dispatch at `loc_1E44C`.
The engine correctly kept their upright overlap calculation but then reversed
the correction. The new regression failed at expected Y252 versus actual248.
The existing direct-top semantic contract now keeps that correction upright;
flat platforms retain their explicit inverted branch. No new state is added.

The slope test covers both gravity directions, X flips, live radii and exact
accepted/rejected overlap edges using native sampled-height arithmetic.
Queued Java21 validation, with the real S3K ROM:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path="$REPO_ROOT/s3k.gen" \
  -Dtest=TestSolidObjectManager,TestS3kDezRetractingSpringHeadless,TestS3kReverseGravitySolidObject,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test
```

All161 tests pass, zero failures/errors/skips. The inspected change-based plan
selects all2915 ordinary classes plus guards because this is shared collision
code; that combined campaign gate remains outstanding. The synthetic slope
is a helper-contract proof, not a new DEZ placement or native-video comparison.
All112 applicable gravity references now have evidence (four are not applicable).
Whole-route, roster, lifecycle, viewport and presentation obligations remain open.


### 2026-09-25 — cold routes repaired after native spike initialization correction

At `3881f549a`, the unchanged ordinary full-ending test fails at input21410
(one test, one failure, zero skips). The first changed player contact is20019:
the upright spike at(6912,1750) was initialized while gravity was reversed and
retains its underside damage routine after the next gravity change. ROM
`Obj_Spikes` / `loc_23FE8` XORs placement flip with gravity once, then replaces
the object's routine pointer; `loc_2413E` tests underside contacts. A matched
class-only diagnostic using the pre-`39832f596` spike implementation restores
the old top damage and rebound timing. This attributes the recording change;
it is not a whole-commit baseline or a native full-route comparison.

Keep the corrected engine behavior. The controller route now brakes onto the
(7232,1720) pressure pad, adjusts departure from the conveyor for its changed
contact history, and jumps for rings before the later upper hazard. Trying to
reuse the entire old tail after only the pad repair dies at23989; repairing the
shaft departure advances to31127, where the old path reaches a hazard without
its previous ring pickup. The updated approach survives. Final-stage input is
aligned to the actual load, not the earlier recording's boss-death frame.
No positions, velocities, object state, health or rings are injected.

Preserved independent route lengths are roof23190, shaft23893, tilt25263,
chain28413 and clear40316. Replay checkpoints follow the same interactions;
all28+14+26+32 traversal spots pass in four tests, zero failures/errors/skips.
The roof endpoint is now(8718,1132); other independent endpoints retain their
previous assertions, including the tilting-bridge route's own neutral tail.
The full ordinary ending capture reaches zone13/act1 after54692 inputs,
zero deaths. Final/boss test verification is recorded in the final-arena matrix.

Commands use Java21, `DISPLAY=:0`, `python3 tools/testing/maven_queue.py
-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$S3K_ROM test` with
`-Dtest=TestDezColdRouteCapture#coldIncomingActTwoCompletesUpperGravityRouteCarrierAndCountdownLaunchWithRewind+coldIncomingActTwoDescendsInvertedSpringShaftWithRewind+coldIncomingActTwoCrossesTiltingBridgeWithoutAShieldWithRewind+coldIncomingActTwoTraversesUpperTransportChainsAndEastHubWithRewind`.
All six changed script/BK2 pairs pass production author/loader round-trip checks.
The local selection plan falls back to2916 classes because route resources are
unclassified. These input/test-only changes use direct consumer route and full
registry replay checks under proportionate validation; the combined campaign's
shared-code broad run remains required and is not claimed here.

Video: `$VIDEO_ROOT/dez-bring-up/campaign-20260925-cold-switch-repair-320/capture.mp4`,
420 frames at60fps,7 seconds; complete decode and approach/ascent/exit frames
inspected. It is engine presentation evidence, not native parity. The ordinary
full-route state is in `campaign-20260925-repaired-route-trial5`. The same route
with seven Super Emeralds declared only at boot reaches DDZ at54691; incoming
DDZ completion remains separate work.


## Ordinary Sonic solo cold completion (2026-09-25)

After `cc07626d9`, `dez2-sonic-solo-incoming-clear-320.{script,bk2}` preserves
the complete cold DEZ1 prefix and clears ordinary solo Act2 in53842 inputs,
including120 neutral final-stage arrival frames. Actual `$1700` load is input53721;
all8 boss hits, no deaths, no follower, no state seeds, donor off/native320.
`TestDezSoloActTwoColdRouteCapture` checks103 whole-registry immediate restores
and45-input forward replays across traversal, gravity changes, transporters,
bridge/hub interactions, boss phases, defeat and arrival. Live history resets
at the actual load and seeking frame0 remains in zone23. Test/input presence
alone is not the claim: the complete cold run passed.

Two runtime faults surfaced. After an act transition, re-registering level adapters
moved objects after rings; object restore then erased attracted-ring reservations.
Ring restore also released future slot numbers that could belong to restored objects.
Restore now rebuilds object-owned slots first, clears future ring records without
freeing those numbers, and reserves saved ring slots. A short allocation regression
failed before the repair. The first repair alone was rejected by an immediate
restore failure at28910; fixing registration order was also necessary.

At the boss load, `DezEndBossEscape.parent` retained the deleted root. ROM
`loc_70068 -> loc_7F74C` stops reading parent3 once Robotnik runs; `loc_7F79C`
switches the door to `Sprite_OnScreen_Test`. Release those Java references at
those handoffs and continue their independent routines. The boss regression
covers waiting before the signal, release, and continued motion/rendering.

Focused Java21/DISPLAY=:0 commands, each with `-Dmse=off` and absolute
`-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`, through `tools/testing/maven_queue.py`:
- `-Dtest=TestRingManager,TestRingManagerRewindSnapshot,TestLostRingRewindGenericRestore,TestGameplayModeContextRewindRegistry,TestDezSoloActTwoColdRouteCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`:137 passing checks,0 skips; the route alone then errored on the retired boss reference.
- `-Dtest=TestDezEndBossEncounter,TestDezSoloActTwoColdRouteCapture test`:15 boss checks pass,0 skips; the route's final history assertion was incorrectly comparing71 pre-load frames with120 arrival frames plus replay windows.
- `-Dtest=TestDezSoloActTwoColdRouteCapture test`:1 pass,0 failures/errors/skips,
  1:08 Maven, after asserting the frame-origin reset at the actual load instead.

Fresh capture agrees with all53722 authored-prefix rows on12 fields.
`$HOME/Videos/OGGF/s3k-dez-bring-up/campaign-20260925-sonic-cold-act2-clear-320/`
contains the5592-frame/60fps/93.2s active-fight, defeat and arrival video, full
state CSV and inspected stills. Full decode passes. It predates the rewind-only
repairs and does not exercise rewind. Native visual comparison, remaining
width/donor/lifecycle breadth and solo final-fight completion remain open.
The exploratory final continuation reaches the core but hits the9:59 timer
limit; its waiting-heavy Act2 inputs need shortening for an ending route.
Shared restore changes require the combined campaign broad selection (2920
ordinary classes plus guards against `e6c6ac79`), still pending; these focused
results are not a full-suite pass.


## Tails lower gravity-pad replay repair (2026-09-25)

After `b7d7ee91f`, `dez2-tails-solo-lower-gravity-pad-320.{script,bk2}` preserves
47140 ordinary solo/native320 controller inputs from cold DEZ1 through the lower
Act2 bridge. This is a traversal frontier, not Act2 completion. No state seeds,
donor, follower or death. The new Tails method in `TestDezSoloActTwoColdRouteCapture`
checks12 full-registry immediate restores and45-input forward replays. It asserts
the occupied inverted pad at46581, the production jump-off/return toggle and
normal gravity at46681, and actual grounded bridge arrival.

An exploratory snapshot branch crossed this pad while a fresh replay stayed
inverted. First divergence was46582: restored Y2144 versus uninterrupted Y2136.
`S3kDezGravitySwitchObjectInstance` manually reset pending contact flags on restore.
`loc_48AD6` and `loc_48B9C` consume `SolidObjectFull` contact in-line; the engine's
split phases can publish contact after this object's update. Those flags are
therefore persistent across a frame boundary. Dropping occupied contact let the
zero rearm counter expire, return the pad8px and accept an unintended new press.
The codec now captures both flags. Two short armed/occupied-rearm regression
cases failed before the fix. Do not certify the old snapshot-only branch061;
its apparent successful toggle was a rewind artefact. Branch064 instead jumps
off and returns to the rearmed pad through ordinary input.

Queued Java21/DISPLAY=:0 validation with `-Dmse=off` and absolute
`-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`:
- `-Dtest=TestS3kDezGravityObjectsHeadless,TestS3kReverseGravityDezCorridor,TestS3kDezGravitySwitchArt,TestDezSoloActTwoColdRouteCapture test`:73 pass,0 failures/errors/skips,1:59 Maven. This includes Sonic's103-window complete Act2 route.
- `-Dtest=TestDezSoloActTwoColdRouteCapture#coldTailsLowerGravityPadPreservesPendingContactAcrossRewind,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`:60 pass,0 failures/errors/skips,1:01 Maven.
- `-Pguards -Dtest=TestRewindArchitectureGuard,TestRemainingRewindTailInventory test`:5 pass,0 failures/errors/skips,23.796s Maven (no ROM required).

Fresh capture matches all47140 authored rows on12 fields, no deaths/follower.
`$HOME/Videos/OGGF/s3k-dez-bring-up/campaign-20260925-tails-lower-pad-fixed-320/`
has590 frames at60fps (9.833333s), inputs46550–47139, plus inspected occupied-pad,
released-gravity and bridge stills. Full video decode passes. Engine presentation
only; native pixel comparison and broader lifecycle products remain open.
Tails traversal beyond the lower bridge, both solo final fights, and combined
campaign validation/integration remain pending.

Local validation for this pad-only follow-up is proportionate: its only runtime
change is the existing pad codec's two pending contact booleans; both consuming
routines, real cold contact, adjacent gravity behavior, Sonic's complete route,
required loading checks and rewind guards are exercised. The diagnostic path
selection against `b7d7ee91f` falls back to all2920 ordinary classes because the
new route resources are unclassified. That does not replace the pinned combined
campaign gate against `e6c6ac79`, which remains pending.

## Player sprite mirror cancellation (2026-09-25)

The original cold solo-clear clip exposed upright Sonic under reversed gravity.
The animator's native final flag was XORed again by the player draw helper.
Remove the drawing XOR for Sonic/Tails/Knuckles; object-controlled mappings retain
their owner's flags, and Tails carry publishes its own native facing/gravity.
The new animation-to-renderer and object-owned orientation tests both failed on
`41d7cb05f` before the fix. Independent flag-only tests had missed the cancellation.

Fresh capture: `$HOME/Videos/OGGF/s3k-dez-bring-up/campaign-20260925-sonic-gravity-mirror-fixed-320/`.
Same cold native320 solo input,53842 steps, video48250–53841,5592 frames at60fps
(93.2s). All53842 CSV rows match the original across every recorded column;0 deaths.
Full video decode passes. Frame49680 now draws Sonic upside down where the old
clip drew him upright. This is verified engine presentation, not native pixel parity.

The combined campaign selection against `e6c6ac79a8b411f32998ae13c8e5c94099c1818c`
remains2920 ordinary classes plus guards. Shared rendering consumers and earlier
campaign changes still require that broad run before integration; focused results
here do not replace it or certify the remaining level matrix.

Verification on `41d7cb05f` plus this correction, Java21/DISPLAY=:0:
```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestS3kReverseGravityRenderMirror,TestPlayableSpriteAnimation,TestTailsCarryController,TestS3kReverseGravityShields,TestHeadlessTestFixture,TestDezSoloActTwoColdRouteCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils \
  -Ds3k.rom.path=/absolute/path/to/s3k.gen test
```
139 tests passed,0 failures/errors/skips,3:25 Maven. Both cold route methods
passed: Sonic's103 replay windows and Tails'12 lower-pad windows. The prior
regression-only run had4 tests with2 expected failures and0 errors/skips.

The shortened Act2 prefix now has an independently verified continuation through
the complete ordinary solo Sonic final fight and actual ending load: see the
[final matrix](s3k-dez-final-boss.md#ordinary-solo-sonic-cold-ending-route-2026-09-25).
Its68 additional replay windows include the shortened waits and final encounter;
the original103-window Act2 fixture remains independently runnable.
