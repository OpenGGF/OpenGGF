# S3K Death Egg Zone act 2 coverage matrix

Game / canonical zone / act: S3K `S3K_DEATH_EGG_2`, engine zone `$0B` act index 1,
ROM `Current_zone_and_act = $B01`, SKL object set. **Not Sonic 2's Death Egg.**
Owning plan: [S3K DEZ bring-up](../../plans/2026-09-17-s3k-dez-bring-up.md).
Status: traversal/gravity families and end boss are implemented. Positioned
DEZ2-boss → final arena → complete DDZ controller routes at320/800 have eleven
whole-registry replay spots per width. Cold DEZ2 traversal, roster/donor breadth,
history isolation and native whole-scene acceptance remain open. Historical slice
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
| Implemented | Presentation foundation (static background, the two shared `AnPal_DEZ2` channels, the eight `AniPLC_DEZ` scripts, both `DEZ2_ScreenEvent` chunk stages, the direct-load routine values), reverse gravity for the player, shields, lost rings, dust-free solid objects, springs and the sidekick (98 of 116 ROM references — the 7 open group A-I rows are listed in [s3k-known-bugs](../../../status/s3k-known-bugs.md)), the implemented gravity interaction families, and the traversal/badnik/shock-block families listed below (494/494 concrete placements) |
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
| TRAVERSAL: SKL `$4F` staircase | `loc_476EA`–`loc_47814`: four solid SST sections, standing/underside trigger delays, signed word step rounding | all flips, upward/downward and shake variants; actual DEZ1 landing at 320/800 and inverted DEZ2 `$950,$790` landing | `TestS3kDezStaircaseHeadless`, placement census | implemented after `35390ba3c` | timer/art checks, forced graph recreation and placed trigger/carry replay | Act-2 route entry, native comparison and team/donor breadth remain open |
| TRAVERSAL: SKL `$4C` hang carrier | `loc_46FC2`–`sub_4703E`: P1 start, accelerated rise, native ceiling probe, finite horizontal travel, P1/P2 grabbing | unit contact/control/input boundaries; actual DEZ1 ride at 320/800 | `TestS3kDezHangCarrierObjectInstance`, `TestS3kDezHangCarrierHeadless` | implemented after `b1c767647` | real terrain, jump release and forced recreation/forward replay | native comparison, Act-2 entry and donor/character breadth remain open |
| TRAVERSAL: SKL `$4A` floating platforms (10) | `loc_25A7E`, `word_25AB8`, shared `sub_25974`; table offsets include native control word | all nine movers and four status flips; placed horizontal ride at 320/800 | `TestS3kDezFloatingPlatformObjectInstance`, `TestS3kDezFloatingPlatformHeadless`, census and PLC registry | implemented after `8a58aafbc` | ROM art, oscillator bytes, ramp thresholds, real carry and forced recreation/180-frame replay pass | Inverted entry, native comparison, donor/team/character breadth and cold route remain open |
| TRAVERSAL: SKL `$4B` tilting bridge | `loc_46E1C`–`loc_46F54`, signed ROM `byte_46ED8`, prior-standing aggregate, long velocity and delayed free fall | all eight standing rows, P1/P2 sum/cancellation, exhausted forward allocation; DEZ1 320/800; inverted DEZ2 declared entry | `TestS3kDezTiltingBridgeHeadless`, census, PLC registry | implemented after `6b7055cea` | nine focused checks: real landing/carry/collapse/floor release, complete graph recreation and forward replay; inverted carry/replay | Cold route, native comparison and donor/roster breadth remain open |
| BOSS: `$A7` end boss | `word_7F0BE` range, `word_7F0C6` arena, enemy-published eight hits, allocation prefixes, breakup and `$1700` request | 320/800; native P1/P2 component cases; solo Hyper movie | `TestDezEndBossEncounter`, child/resource suites, `TestS3kDezTeleporterHeadless` | implemented | focused checks pass, 2026-09-23 local campaign | Native parity, full route and remaining character/donor/team breadth open |
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
