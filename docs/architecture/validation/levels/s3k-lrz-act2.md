# S3K Lava Reef Act 2 coverage matrix

Game / canonical zone / act: S3K `S3K_LAVA_REEF_2`, engine zone `$09` act index 1,
ROM `Current_zone_and_act = $901`, SKL object set (`Sprite_ListingK`, SK Set 2).
Character routes: Sonic + Tails, Sonic, Tails (seamless arrival from Act 1, then the boulder
cutscene and `StartNewLevel $1600`) and Knuckles (`Obj_StartNewLevel` `$B3` at `($3FE0,$E0)` to
`$1601`). Owning plan: [LRZ bring-up](../../plans/2026-09-17-lrz-bring-up.md); starting inventory:
[LRZ placement inventory](../../research/s3k-zones/lrz-object-inventory.md).
Status: traversal families are implemented through slice 7 as of the 2026-09-22
continuation. Both exits and the boulder cutscene are implemented. Route certification
and the remaining breadth/lifecycle/native obligations are still separate gates.
The ordinary native320 Sonic+Tails cold Act1 route now reaches playable Act2
(2357,1980),zero deaths; see the [handoff evidence](s3k-lrz-act1.md#ordinary-cold-miniboss-clear-and-act2-handoff-2026-09-24).
The native320 Sonic+Tails route now completes Act2 and reaches the boss act
from cold Act1 in43761 inputs without a death. See the
[completion evidence](#ordinary-cold-act2-completion-2026-09-24); broader products remain open.

Incoming: seamless `$900` handover, level select `$901`, star-post reload.
Outgoing: `$1600` (Sonic/Tails, with the Act 3 carry) and `$1601` (Knuckles, with `SaveGame`).

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`,
same roster as HPZ and DDZ): widths 320/352/400/528/800; supported character/donor pairs
off x {Sonic, Tails, Knuckles}, S1 x Sonic, S2 x {Sonic, Tails}; teams: solo, Sonic+Tails,
S1 Sonic+Sonic duplicate, S2 Sonic+Tails, Sonic+Tails+Knuckles at 800.

Five claims are tracked separately and never aggregated: **implemented**, **cold-reachable**,
**rewind-verified**, **native behaviour matched**, **visually matched**. Nothing below certifies
the act.

Placement baseline (`TestS3kLrzPlacementCensus`): 455 placed objects, **zero remaining
placeholders** after traversal families, the boulder cutscene and exit;
281 live rings (282 records minus the leading `(0,0)` sentinel). Historical counts
were 281 at `035e48a58`, 188 before slice 7, 23 at `c708e1a2b`, and five at `87f0bf87b`.

The compatibility matrix verifies Act 2's ROM-backed skins, including its own
sinking rock, doors, buttons and swinging-spike-ball art. That does not establish
behavior at every placement; the remaining obligations below retain that distinction.

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| BASELINE: placed object and ring census | `LRZ2_Sprites` `$1F7C9C` (455), `LRZ2_Rings` `$1F8C7E` (282 records, 281 live) | native | `TestS3kLrzPlacementCensus` | implemented | pass, `3418eba6e` | Ratchet target 0 placeholders |
| OBJECT: orbiting spike balls `$2B` (12), `$2C` (40) | `Obj_LRZOrbitingSpikeBallHorizontal` (sonic3k.asm:89077-89145) and `Obj_LRZOrbitingSpikeBallVertical` (:89149-89222): `bclr #0,subtype` picks the 32x32 ball AND clears the bit, the byte angle is `(Level_frame_counter+1)*2` negated for `status` bit 0 plus the subtype, the ball is harmful (`collision_flags` `$9A` small, `$8F` large) and drawn in front only while that byte has bit 7 set, and the displacement is a fixed fraction of `cos` on one axis -- `cos asr 3`, `(cos + cos asr 1) asr 3`, `(cos + cos asr 2) asr 3` and `(cos asr 2) - (cos asr 5)` | native | `TestLrzOrbitingSpikeBall` (5) | implemented | pass | Five cases against the ROM's own `SineTable` through the routine's arithmetic, broken on purpose once (`cos asr 2` for `asr 3`) and failing on the two positional assertions only. **Owed**: no clip, no rewind spot on a route, no act 2 route position, and no wide/donor/roster row. The despawn uses the anchor x, `loc_1B666`'s own reference |
| ENTRY: `$901` resources, bounds, object set | Registry/sprite/screen-event tables | native | `TestSonic3kLevelLoading` | implemented (inherited) | pass | Not re-verified for this campaign |
| PRESENT: parallax, bands and shake | `sub_57082`, `LRZ2_BGDeformArray` `$20,$20,$20,$10x4,$F0,$10x3,$20`, `ApplyDeformation` at `HScroll_table`; `Camera_Y_pos_BG_copy` = `3Y/32` | 320 and 640 | `SwScrlLrzTest`, `TestS3kLrzScrollRegistrationHeadless` | implemented | pass, `bbd156d37` | Direct-load background art corroborated against native on2026-09-25 (below); synchronized scroll/palette phase remains open |
| PRESENT: animated tiles and `AniPLC_LRZ2` | `AnimateTiles_LRZ2` / `loc_282D0` and `loc_28364`; `Offs_AniFunc` pairs `$901` with `AniPLC_LRZ2` `$28A84`; `Animate_Init` does **not** seed `Anim_Counters+1/+3` for `$901` | native | `TestS3kLrzPatternAnimation` | implemented | pass, `1ef1256ca` | A direct or star-post `$901` load whose first phase is 0 skips its first upload, as the ROM does; current direct-load art is corroborated against native (below); full animation timing remains separate |
| PRESENT: rock sprites | `LRZ2_Rock_Placement` (10 placements, all at `y=$7C8`), same window and vertical test as act 1 | native + wide | `TestLrzRockSpriteRenderer` | implemented | pass, `fbbb793f7` | Act 2's rocks sit in the opening corridor; not yet seen on a cold route |
| PRESENT: palette cycles | `AnPal_LRZ2` (channel D keeps the `FixBugs = 0` duplicated pair) | native | `TestS3kLrzPaletteCycling` | implemented (inherited) | pass | Not re-verified |
| PRESENT: the post-defeat palette and camera releases | `loc_78AA8` and the `loc_78AE6`/`loc_78B08` pair (sonic3k.asm:160505-160545): `End_of_level_flag` gates both, then `Camera_min_X_pos` is written at `$2C0` (with `Pal_LRZ2` over palette line 1 and `Pal_LRZMiniboss3` over lines 2 and 3) and again at `$940`. The fight's own `Pal_LRZMiniboss1`/`Pal_LRZMiniboss2` reach act 2 because `Load_Level` copies no palette | native | `TestLrzPostDefeatCameraRelease` (3) | implemented | pass | Clip `35` shows the swap on capture frame 3539 and its gold half matches the native capture `native-lrz2-bg/run1/f416433.png`. `word_78EAA` now runs its thirteen ROM rows and freezes/releases the shared palette clock; focused and actual-route rewind checks pass. Native ramp colors/timers are corroborated; full-scene matching remains open ([acceptance gap](../../../status/s3k-known-bugs.md)) |
| PRESENT: Death Egg background sprite | End of `sub_57082`: `x = $678 - HScroll_table+$004`, kept when `x <= -$7E0`, else `0`; `y = $C0 - Camera_Y_pos_BG_copy`; `loc_5711E` deletes for `Player_mode 3` | native + wide | — | implemented | focused checks pass; native whole-scene comparison open | `TestLrzDeathEggBackground`: five widths, Knuckles deletion, recreation and45-input full-registry replay; both seamless handoffs allocate exactly one owner |
| OBJECT: lava blocks `$6E` (4 placements) | `Obj_InvisibleLavaBlock`; `sub_1F58C` | native, all five shield states | `TestSonic3kInvisibleHurtBlockHObjectInstance` | implemented | pass, `bbd156d37` | Act-2 placements not exercised on a route |
| OBJECT: doors and horizontal buttons `$19` (11), `$1C` (11) | `Obj_LRZDoor` act 2 skin (`mapping_frame` 1, art base `$090`, `height_pixels $20`, so a shorter solid box) and `Obj_LRZButtonHorizontal` act 2 skin (`Map_LRZButtonHorizontal2` over `ArtTile_LRZ2Misc`, palette 1) | native | `TestLrzDoorsButtonsAndTriggers` (act 1 decode), `TestS3kLrzPlacementCensus` | implemented | pass, `d2c58f148` | The act-2 skin is registered but not exercised: no act-2 unit case, no route spot. Act 2 doors `$01-$0B` each have a `$1C` button; `$33/$05` is extra |
| OBJECT: flame throwers `$29` (52) | `Obj_LRZFlameThrower` (sonic3k.asm:89227-89448): subtype bit 7 picks the variant and the rest is `$32 = (subtype & $7F) * 4`, the idle length between `2*60`-frame bursts; emission is gated on `(Level_frame_counter+1) & 3`, `$2E = sin(angle) asr 4` with `addq.b #8,angle`, and the flame leaves at `sin/cos($2E) asl 2` from `x_pos + $10` (or `y_pos + $10`); `tst.b render_flags / bpl` makes an off-screen thrower run its cycle and its sound but allocate nothing | native | `TestLrzFlameThrower` (8) | implemented | pass | Eight cases from the ROM's immediates and its own sine table, broken on purpose once (`addq.b #4` for `#8`) and failing on the angle case only. **Owed**: no clip, no rewind spot on a route, no act 2 route position and no wide/donor/roster row |
| OBJECT: solid moving platforms `$2D` (52) | `Obj_LRZSolidMovingPlatforms` (sonic3k.asm:51012-51110): `(subtype >> 4) & 1` picks one of `byte_25826`'s two entries and `subtype & $F` one of `off_258BC`'s nine movers -- an `rts`, `Oscillating_table+$0A`/`+$1E` on x and on y, and four `sub_25974` ramps at limits `$5F` and `$7F`; `sub_25974`'s `$36` is an 8.8 accumulator whose HIGH byte the limit is compared against, and `$40` is an 8.8 acceleration stepping by 4 | native | `TestLrzSolidMovingPlatform`, `TestS3kDezFloatingPlatformObjectInstance`, `TestS3kLrzSolidMovingPlatformHeadless` | implemented; oscillator offset and cleared art flips corrected after `8a58aafbc` | native-layout table regression and actual record 49 carry/recreation/replay pass at 320/800 | Earlier reset-only tests had selected velocity instead of position. Engine offsets are `$08/$1C`, excluding the control word. Positioned ride is not a cold route or native comparison; donor/roster breadth remains open |
| OBJECT: turbines `$32` (18 placements) | `Obj_LRZTurbineSprites`, `sub_44338`, `loc_44448`, `sub_4450A`; logical press byte, three capture bands, two native player slots | 320/352/400/528/800 × native/S1/S2 Sonic, plus native Tails/Knuckles | `TestLrzTurbineSprites`, `TestS3kLrzTurbineHeadless` | implemented in 2026-09-22 continuation | 26 cases, 0 failures/errors/skips | Real placement capture/release and whole-composite mid-ride/cooldown restore + forward replay. Art ready from ROM. Positioned moving captures at 320/800 and S1 400; 134 native ride-offset samples and three release velocities corroborated by read-only BK2 replay. Matched pixels and extra-team breadth open. |
| OBJECT: spike-ball launcher `$37` (9 placements) | `Obj_LRZSpikeBallLauncher`, `loc_448A8`, `loc_44916` | native | `TestLrzSpikeBallLauncher` | inherited implementation `c708e1a2b` | focused tests pass | In-flight child removal/recreation + whole-world replay pass in `TestS3kLrzLauncherRewindHeadless`; moving capture remains open. |
| OBJECT: chained platforms `$25` (3 placements) | `Obj_LRZChainedPlatforms`, ROM group/path tables, `sub_4A818` signed division; safe top, spiked underside | all five current widths × native/S1/S2 Sonic, native Tails/Knuckles | `TestLrzChainedPlatforms`, `TestS3kLrzChainedPlatformsHeadless` | implemented | 23 behavior cases pass; isolated rewind inventory passes | All paths close both ways; all three real groups recreate without duplication; riding and underside hurt verified. Clip 38 shows positioned ride. Native first-step fraction `$73F8` agrees. Cold-route, extended teams and matched pixels remain open. |
| OBJECT: badniks `$99 $9A $9B` (52 placements) | `Obj_Fireworm`, `Obj_Iwamodoki`, `Obj_Toxomister` | native 320 + 400 | `TestFirewormBadnikInstance`, `TestIwamodokiBadnikInstance`, `TestToxomisterBadnikInstance`, `TestS3kLrzCompatibilityMatrix` | implemented (all three; the classes are act-agnostic and the badniks share one art sheet across both acts) | pass, `cad4a2e07` | Placements exercised in act 2 only through the census and the matrix's art rows; no act 2 route or clip yet |
| BREADTH: act 2's own skins | `Obj_LRZSinkingRock`'s act branch (`mapping_frame` 1 and the `$090` tile base, sonic3k.asm:87907-87910) plus the act 2 door, button and swinging-spike-ball art keys | native 320 and 400, in act 2 | `TestS3kLrzCompatibilityMatrix#actTwoExercisesItsOwnSkins`, `#actTwoSkinsAlsoLoadWide` | implemented | pass | Asserts the load really is act 2 and that each act 2 art key has a ready ROM-backed renderer. Behaviour of the act 2 placements themselves is still unverified |
| OBJECT: `$0F` collapsing bridges (25) and `$24` tunnel (10) | `Obj_CollapsingBridge` zone-9 mappings; `Obj_AutomaticTunnel` subtypes `$55-$59`, `$D5-$D9` | native | `TestS3kLrzPlacementCensus` (classification only) | implemented | classification pass | Per-subtype behaviour unverified |
| EXIT: boulder cutscene and `$1600` request | `Obj_LRZ2CutsceneKnuckles` `$AE` at `($38B0,$240)`, `CutsceneKnux_LRZ2`, `loc_63C14` (Y >= `$4C0`: `Act3_flag`, ring/timer/shield carry) | 17 width/donor/character/team rows, plus Knuckles exclusion | `TestS3kLrzBoulderCutsceneHeadless` | implemented | production approach, carry and full-world replay pass in campaign | Cold arrival, additional teams and matched native pixels remain open |
| EXIT: Knuckles `Obj_StartNewLevel` `$B3` -> `$1601` | `Check_InMyRange word_86426`; word decode at `subtype` (SST `$2D` must be 0); `SaveGame` only for `Player_mode 3` | native Sonic/Tails/Knuckles | `TestS3kStartNewLevel`, `TestS3kLrzExitHeadless` | implemented | 20-case exit/census selection passes; real HPZ load verified | Positioned entry; cold reachability and wider roster/lifecycle breadth remain open |
| ROUTE: act 2 from the seamless change | The `lrz` fixture's own input column from row 25558 (`~/Videos/OGGF/lrz-bring-up/inputs/lrz2-native-route-v1.txt`), driven after the production act change | native | scratch probe, recorded in the [frontier log](../../../status/trace-frontier-log.md) | measured | 923 rows exact | Player x/y match rows 25558-26481; first player divergence row 26482 (spindash release `$900` against an implied `$A00`), first camera divergence row 26416 (3 px in y). Declared positioned probe: the engine is written to the fixture's row-25558 state first, because the filmed fight is not the recorded route |
| ORACLE: strict segment replay | `TestS3kSonicTailsLrzSegmentTraceReplay` (act 2 from row 25557), `TestS3kTailsFullChainLrz3SegmentTraceReplay` | `-Ptrace-segments` | — | — | see [trace frontier log](../../../status/trace-frontier-log.md) | Slice 11 |

## Execution evidence

Worktree `.worktrees/ai-lrz-bring-up`. Slice 0 evidence is shared with the
[Act 1 matrix](s3k-lrz-act1.md). Baseline media: `~/Videos/OGGF/lrz-bring-up/raw-00-lrz2-before/`.


### 2026-09-22 continuation

See the [campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md). Baseline is
updated develop `c91fd5ac7` plus the inherited LRZ branch; worktree
`.worktrees/ai-sk-zone-completion`. The old status table is historical: the newest
inherited commit already implements the launcher, and the current turbine slice
reduces Act 2 placeholders from 23 to 5 (`$25`, `$AE`, `$B3`). Registration does
not establish route completion.

Queued Maven `-Dmse=off -Dtest=TestS3kLrzTurbineHeadless,TestLrzTurbineSprites`
with absolute S1/S2/S3K ROM properties: **23 tests, 0 failures/errors/skips**,
2026-09-22 14:51:57 BST. The unit held-input regression failed before the logical
press fix. The first breadth attempts exposed a stale camera in the test session;
recreating the session after resolving the display preset fixed the test setup,
without an engine change. The actual supported presets are 320/352/400/528/800;
the older standard's 512/640 numeric rows are not selectable presets today.

Media: `$VIDEO_ROOT/lrz-bring-up/raw-52-turbine-320-20260922`,
`raw-53-turbine-800-20260922`, `raw-54-turbine-s1-400-20260922`, and
`raw-55-turbine-approach-320-20260922`. All are declared positioned entries,
not cold routes. The latter starts above the upper capture band, falls into the
real placement and jumps free at frame 240. Native corroboration is still open.

The chained-platform continuation uses the ROM's `SolidObjectFull` high d6 bits
18/19 (underside), not bits 20/21 (top landing). Queued focused tests passed 24
cases including the rewind inventory; its counts are 1157/917/240 (total/isolated/
graph-covered), with no unresolved buckets. The native exporter and complete
provenance are described in the campaign audit. New footage remains positioned.

## Exit continuation (2026-09-22)

`$B3:2D` now follows `Obj_StartNewLevel` ($863EC): half-open signed bounds,
packed subtype destination `$1601`, and `SaveGame` gated on LRZ + Knuckles only.
Sonic/Tails reaching this placement still take its transition, as in ROM.
`TestS3kStartNewLevel,TestS3kLrzPlacementCensus,TestS3kLrzExitHeadless` passed
20 tests without failures/errors/skips using the queued Maven wrapper and absolute
S3K ROM. The real placement at `(3FE0,E0)` reaches HPZ through GameLoop's fade
for native Sonic, Tails and Knuckles. Rewind/profile guards pass; the inventory is
1158 = 918 isolated passes + 240 graph-covered, with no failure tails.
Clip 39 shows a positioned Knuckles approach and HPZ arrival (360 frames, zero
hurt/dead frames); this does not establish a cold route or wide/donor breadth.

## Boulder continuation (2026-09-22)

`TestS3kLrzBoulderCutsceneHeadless` covers the actual `$AE` approach and `$1600`
request/load, 17 width/donor/character/team cases plus Knuckles exclusion. It
checks the native first-49-move trajectory, recreates all three cutscene objects,
and compares whole-world restore and forward replay. Rings/time and fire shield
carry through the receiving load; the separate continuation tests cover all three
elemental shields and failure/replacement/direct-entry boundaries. Clip 40 and
901 read-only native rows corroborate the stages; see the
[campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md).
Cold arrival, additional team breadth and matched native presentation remain open.


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


### Post-results palette and handoff follow-up (2026-09-23)

The13-row ROM palette ramp and shared clock freeze/release are implemented.
Native111-frame observation corroborates all colors/durations, callback68 and
post-AnPal write ordering (CRAM one frame later). Native/wide actual positioned
boss/results/Act2 routes have no hurt/death and pass whole-registry mid-ramp
restore/45-input replay. Final corrected selection passes77cases;24S3K palette
consumer classes pass128cases, no skips. Earlier LRZ/required-S3K selection
passed558cases before the native timer-order correction. Full campaign checks
remain owed. See the [campaign audit](../../audits/2026-09-22-sk-zone-bring-up.md).
This is not cold full-act or whole-scene native certification. The800px capture
reveals left-of-start scenery while the centered Act2 camera is negative; that
separate edge remains open; the Death Egg follow-up below closes its missing owner.


### Death Egg background follow-up (2026-09-23)

`LrzDeathEggBackgroundInstance` now owns loc_5711E/loc_57156, allocated once by
both direct initialization and seamless stage0. It uses ROM mapping$5719E,
runtime-queued art$15A112/tile$39F, palette3, sprite bucket7 and low hardware
priority. SwScrlLrz publishes the actual scroll-table word through rewind state.
Native320 retains the signed gate; wider views extend its offscreen lead-in by
width-minus320 and unwrap the relevant SAT coordinate turn into a single body.
The code documents this presentation difference, including earlier wide art loading.
Seven focused cases cover gate boundaries, all five widths' full-registry recreation
and45-input replay, and Knuckles deletion. Both existing real miniboss/results
handoffs now assert exactly one owner. With the required four S3K regression classes,
68cases pass, zero skips; selected priority/services/rewind guards pass18cases.
Native3301-row observation corroborates position/gate/priority; positioned320/800
movement captures have240frames each and zero hurt/death. This is not full-scene
native parity or cold-act certification. Campaign inventory and broader delivery
checks remain outstanding; see the campaign audit for the exact failed inventory.


### Finite foreground edges (2026-09-23)

The centered handoff's negative camera no longer displays opposite-end level
terrain. The shared renderer clips finite widescreen foreground samples outside
the layout while preserving explicit rings and native320sampling; camera/player
bounds are unchanged. Real-GL checks cover left/right edges, high-priority masks
and one-shot reset. Existing4200frame handoff gameplay rows are unchanged; native
1200PNGs are identical and wide359changed frames differ only outside the latched
layout extent. The newly exposed area shows Plane B, leaving a visible vertical
transition at X=0: scene-extension polish remains open, distinct from the fixed
unrelated-terrain repeat. Shared-renderer broad validation remains due.


### Act-title vertical camera release (2026-09-24)

On `6df1ba780` plus this fix, the cold Act1 handoff exposed a missing
`Change_Act2Sizes` child pair: min-Y remained `$710` while the player climbed
above the viewport. Native observation at movie417000 already has camera-Y1707
(player2737,1803), whereas the old engine remained at1808. The ROM's
`Child1_Act2LevelSize` creates max-X, min-Y and max-Y workers, in that order,
after the title; the engine previously created only max-X. The shared flow now
also creates the existing native gradual worker for min-Y and max-Y. Its
explicit act-level target source supplies the loaded ROM LevelSizes where no
cutscene runtime owns Camera_stored_*. Existing cutscene workers retain their
mutable runtime source. Rewind captures the source selection and accumulator.
The LRZ reload continues to preserve Y bounds: snapping them there would move
the release earlier than the ROM. The LRZ-specific post-defeat children only
advance min-X and are not substitutes for the title workers.

A new cold-clear assertion failed on the old code (expected min-Y0, actual1808).
Queued Java21/native GL verification, explicit `-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`,
`-Dtest=TestS3kBossDefeatSignpostFlow,TestLrzColdRouteCapture#coldTeamDefeatsMinibossAndReachesPlayableActTwo,TestLrzPostBossPaletteRouteCapture,TestMhzBossObjects,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`
passes188 tests, zero failures/errors/skips. This includes the26 whole-registry
cold-clear replay spots and both positioned handoff widths; the short worker
check proves quarter/half-pixel acceleration, target preservation and retirement.
This is focused evidence, not the outstanding combined campaign suite.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-act2-camera-release-320/capture.mp4`
films31440–32199 after the full cold prefix. At31800 the camera is1707; at32000
it is1482 with Sonic at1563. The player stays visible through the climb.
The inspected32000 still and full MP4 decode pass. The760-frame clip is ordinary
Sonic+Tails native320, zero deaths; Sonic does take a hit later in the clip.
Inputs authored against the previously stuck camera need further route work,
because correct camera movement changes which objects are active. Full Act2
cold traversal and the existing breadth/lifecycle/native obligations remain open.

Additional focused checks in the same Java21/ROM environment:
`-Dtest=TestS3kMhzAuthoredRoute test` passes1, zero skips, preserving MHZ's
cold defeat/title handoff; a separate JVM with
`-Pguards -Dtest=TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard test`
passes2, zero skips. The change-based plan was inspected against6df1ba780;
its broad run remains part of the combined campaign delivery, not repeated at
this local implementation checkpoint.


### Ordinary cold upper-route progress (2026-09-24)

On da563ad89, the authored Sonic-and-Tails/native320 continuation replays34674
inputs from cold Act1 entry, ending at5055/1004 with9 rings and zero deaths.
This passes the first Act2 platforms, vertical lift, both upper turbine launches,
upper passage and middle drop. It is an exploratory continuation, not yet a
committed Act2 route fixture or full-act/replay certification.

`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-act2-upper-route-320/capture.mp4`
films32300–34673 (2374frames). `GameplayCaptureTool --game s3k --zone lrz
--act 1 --main sonic --sidekick tails --frames 34674 --capture-from 32300`
uses the external route-author `middle-turn-east/variant-0.bk2` and explicit
`$PROJECT_ROOT/s3k.gen`. Fresh capture reproduces the authoring endpoint; the
33152 turbine-launch still is inspected and the entire video decodes. Source
rows confirm the camera follows the climb. No native trajectory equivalence
is claimed: the reference movie uses Super Sonic here.

Read-only BizHawk2.11 reference extensions `upper-route`, `top-route` and
`east-route` under `campaign-20260924-native-act2-floor` cover418600–425000
from the existing415400 state, with verified ROM/movie identity and zero host
failures. Inspected419400/419800 and421000/421200/421800 establish route geometry
and spring/drop choices. These are navigation/scene observations, not a
substitute for ordinary-engine cold completion or whole-scene pixel acceptance.


### Rewind preserves current Act2 art (2026-09-24)

The cold-route branch-authoring probe showed spring-like red fragments instead
of the orbiting spike balls. Direct Act2 entry rendered the correct ROM art.
A positioned real boss/results handoff initially also had correct PLC `$30`
pixels at tile `$40D`; its first registry restore replaced them with the old
miniboss image. Temporary write tracing identified
`KosinskiModuleQueue.restorePatternWrites`, not a late level PLC or animation
write. The diagnostic instrumentation was removed. This corrects the initial
suspicion of a corrupt seamless load: rewind, including the authoring tool's
branch restore, caused the overwrite. Fresh uninterrupted footage is not
evidence of the bug.

The shared DMA journal now samples current target bytes for its tracked ranges
when capturing. A Nemesis PLC or replacement level can have overwritten the
queue's last payload. An unflushed restore still captures its requested logical
image, avoiding sampling stale physical memory during owner gaps. Queue timing,
archive identity and sprite priority are unchanged. Reapplying PLC `$30` after
the corruption would hide the stale journal and was rejected.

Two pure regressions fail on c913c8d65: later overlapping writes (expected9,
actual2) and a replacement level image (expected7, actual1). Both native320 and
wide800 handoff regressions independently fail on PLC `$30` tile `$40D`
(expected0, actual12). The fixed handoff checks compare every pixel of both
ROM-decompressed PLC entries after the middle-ramp restore/replay.

Queued Java21 with native GL and `-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`:

- `-Dtest=TestKosinskiModuleQueue*,TestLrzPostBossPaletteRouteCapture,TestLrzColdRouteCapture#coldTeamDefeatsMinibossAndReachesPlayableActTwo,TestS3kLrzBossRewindHeadless,TestPatternSpriteRendererCorruptionGuard,TestSonic3kPlcArtRegistry#s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits test`:25 passed, zero skips. Includes26 cold-clear registry replay spots and pending-target journal cases.
- `-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test`:59 passed, zero skips.
- Fresh `-Pguards -Dtest=TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard,TestObjectPriorityBucketGuard test`:3 passed, zero skips.

The inspected change-based plan selects all2909 ordinary classes plus guards.
This shared algorithm remains in the combined campaign delivery scope; these87
focused/stability/guard checks are not a full-suite result.

After restoring the full engine registry at35660, inspected image
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-route-author/deep-spike-phase/variant-7-35900.png`
shows the proper metallic balls passing behind the pipe. Earlier branch images
under `deep-east-enemy` show the corruption. Native421800/422200 references
show the same ball art family; this is not whole-scene pixel parity. Input
authoring now crosses the hazardous pipe and reaches the next ledge at6197/1452.
The longer cold Act2 fixture, replay spots and completion remain open.


Fresh uninterrupted capture on9dca397d8 now replays36000 ordinary cold inputs
to6197/1452 with zero deaths. Video
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-act2-pipe-crossing-320/capture.mp4`
films35080–35999 (920frames), showing the descending platforms, pipe crossing
while its balls pass behind it, and the exit jump. State35770 and35830 inspected,
35830 still inspected and full video decode passes. This confirms the authored
route independently of branch restoration; the separate restored image and
regressions above establish the journal fix. Full-act completion remains open.


### Cold climb, door eight and pipe passage (2026-09-24)

`lrz2-sonic-tails-cold-pipe-passage-320.{script,bk2}` preserves36204 ordinary
controller inputs from cold LRZ1 entry, through the miniboss and seamless Act2
arrival, to6741/1484 with one ring and zero deaths. It climbs the opening steps,
rising platforms and two turbines, follows the upper route and descending
platforms, crosses the orbiting-ball pipe, presses door8's horizontal button
and passes beneath the following flamethrower. It does not complete Act2.

`TestLrzActTwoColdRouteCapture` exercises31 whole-registry restore/45-frame
forward-replay spots across those Act2 interactions and checks the real door8
is open, the final position, retained Tails roster and released player control.
The Act1 prefix is replayed normally; its existing test owns earlier rewind
spots. No replay window straddles a level load.

Fresh uninterrupted video on cfc53e5a9 plus these test inputs:
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-act2-door-eight-320/capture.mp4`
films35750–36203 (454frames). All36204 state rows contain no death; stills35930
and36090 inspected and full video decode passes. This establishes native320
Sonic+Tails reachability and engine presentation, not native pixel parity or
wide/donor coverage. The next enemy corridor and cold Act2 exit remain open.

Queued Java21/native GL, explicit `-Ds3k.rom.path=$PROJECT_ROOT/s3k.gen`,
`-Dmse=off -Dtest=TestLrzActTwoColdRouteCapture test`:1 test passed,
31 replay spots, zero failures/errors/skips. This test/input-only addition uses
focused validation: the inspected selection falls back to2912 ordinary classes
plus guards for unclassified route assets, whereas its full production consumer
is directly exercised here and independently in the fresh capture. Earlier
shared runtime changes still require combined campaign validation.


### Cold eastern turbine bank (2026-09-24)

Fresh replay on ff9343838 extends the ordinary native320 Sonic+Tails route to
36871 inputs, endpoint7824/684, zero rings and zero deaths. The controller
sequence clears the Toxomister corridor, steps up the pipe, catches the turbine
at7816/1224, releases into7824/968 and launches onto the high ledge. Subsequent
exploratory inputs reach the upper spring and walkway at7652/428. The full Act2
exit is still open; the committed31 replay spots end at the earlier pipe passage.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-act2-turbine-bank-320/capture.mp4`
contains667 frames, input36204–36870. All36871 state rows checked for death,
stills36750/36870 inspected and full decode passes. Reproducible input lives at
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-route-author/east-turbine-upper-exit/variant-0.bk2`.
The turbine's phase-dependent release, not a runtime change, supplies the
height. Native comparison-only rows423225–423500 show the same two-turbine route
and high-ledge destination; the reference is Super Sonic, not an ordinary
trajectory oracle. Whole-scene matching and new-section rewind coverage remain open.

A separate120-frame positioned direct-load recheck at `$2438,$629` on ff9343838
(`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-direct-background-recheck-320`) ends
at9272/1580 with camera9112/1484. Still60 shows purple background forms without
the obvious HUD lettering in the historical report. This observation does not
close the direct-load background issue: art ownership and a matched native
view still need verification. It is not evidence from the cold route.


### Eastern walls and lower tunnel reached (2026-09-24)

Fresh replay on a3af3f566 reaches8782/1289 after38552 cold inputs,14 rings and
zero deaths. Spindashing breaks the paired walls at8912/736 and8944/736. A
fast first approach rebounds from the upper spring; braking on the return lets
Sonic fall onto the lower pipe, then running west enters the tunnel at8832/976.
These were controller-route choices; no runtime change was made. Further
exploration takes the return drop to9260/1614, then reaches10528/1132 before
a lethal hazard. That failed tail is not part of the fresh video.

`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-act2-wall-tunnel-320/capture.mp4`
films37951–38551 (601frames). All38552 state rows checked for death, stills38340
and38551 inspected and full decode passes. Input is
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-route-author/lower-tunnel-entry/variant-0.bk2`.
The latest committed rewind fixture still ends at36204; new-section rewind
coverage and full-act completion remain open.

Native read-only `far-east` reference under `campaign-20260924-native-act2-floor`
adds4001 continuous rows425000–429000 from the existing415400 save/movie.
The host verifies the same ROM/movie/state identities and reports zero failures.
Inspected425500 confirms the lower tunnel;426000 shows the subsequent corridor.
The native reference is Super Sonic and cannot supply ordinary trajectory
expectations. Visual comparison also prompted a palette check: the cold route
does install Pal_LRZ2 and Pal_LRZMiniboss3, with later differences in cycling
entries. A complete native palette sample is being compared before attributing
the apparent colour difference to a runtime defect. Native pixel acceptance
remains open.


Palette follow-up on00ca5c135: the temporary full64-colour native sample at
425734 (`palette-check` under the same native root; zero host failures) has
Pal_LRZ2 byte-for-byte in line1. Differences from Pal_LRZMiniboss3 lie in the
`AnPal_LRZ2` cycle windows: line2 colours1–4, line3 colours1–2 and11–14. The
cold probe at36203 likewise retains the ROM table outside those windows.
Native still425734 shows the blue crystal phase, unlike the gold phase in
426000. Thus the observation does not establish a missing palette load or
justify changing the palette owner. This is table/phase corroboration, not
synchronized whole-scene pixel matching; the direct-load background acceptance
remains separate. Temporary probes are not promoted as runtime oracles.


### Cold lower corridor and door five (2026-09-24)

Fresh replay on eaf1529ae extends the cold Sonic+Tails native320 route to39830
inputs, endpoint11149/1805 with11 rings and zero deaths. It steers left from
the spring at9264/1660, returns over the pit, drops onto the floor button at
9816/1652 and crosses door5 at10160/1576 into the downhill section. The adjacent
giant ring made an overlong leftward jump enter a special stage; that candidate
was rejected. A12-input left jump lands on the button without entering it,
followed by a separate jump after landing. No runtime change was required.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-act2-door-five-320/capture.mp4`
films38971–39829 (859frames). All39830 rows checked for death, stills39486
(button top,9824/1632) and39607 (past the door,10279/1603) inspected and full
decode passes. Input is
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-route-author/deep-door-exit/variant-1.bk2`.
The following attempt reaches door6 at11600/1760; its elevated side button
requires a different approach from the low ceiling at the door face. Full-act
completion and rewind checks for the new section remain open.


### Door six and the late fixed platforms (2026-09-24)

Fresh cold native320 Sonic+Tails replay on97f47284e reaches12257/1516 after
40626 inputs, five rings and zero deaths. Down alone at39830 starts a roll
through the downhill curve, reaching the elevated side button at11576/1710
and opening door6 at11600/1760. Earlier Down+Right attempts did not roll and
missed the button before the low ceiling. Beyond the collapsing bridges, Sonic
jumps onto the fixed platform at12352/1688, then the right ledge and back onto
12288/1568. The next object at12192/1472 is a vertical oscillating platform;
its phase-dependent approach is still being authored. These are input choices,
not changes to collision, movement or native level geometry.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-act2-door-six-climb-320/capture.mp4`
films39830–40625 (796frames). All40626 state rows checked for death, stills39910
and40625 inspected and full decode passes. Input is
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-route-author/late-return-platform/variant-2.bk2`.
A subsequent brake variant stops safely on the upper platform at12276/1516.
Full-act completion, the added section's rewind spots and wider route products
remain open. The committed cold Act2 fixture still ends at36204.


### Lift, flame pause and ceiling route (2026-09-24)

Fresh cold native320 Sonic+Tails replay on eefb77ead reaches12237/1132 after
41862 inputs, five rings and zero deaths. A16-input wait before the westward
lift exit clears the upward flame at12048/1336 without losing rings. The next
spring launches Sonic onto the ceiling. At41640 the observed X velocity is
+1726 but ground speed is-1726: keeping Left sustains that inverted run.
Switching to Right at41611 decelerates it and drops Sonic. The initial suspicion
of an early Jump release was rejected by the actual BK2: Jump was already off
and both runs match through41610. Preserve the full ceiling traversal before
turning right on the upper platform. No movement or flame logic was changed.

Video `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-act2-lift-ceiling-320/capture.mp4`
films40626–41861 (1236frames). All41862 state rows checked for death, stills40942
and41640 inspected and full decode passes. Input is
`$VIDEO_ROOT/lrz-bring-up/campaign-20260924-route-author/late-high-gap/variant-2.bk2`.
The recording ends back on the safe platform after a jump encounters the second
oscillating lift while it is too high; that next lift at12344/1120 is still being
authored. Full-act completion and added-section rewind coverage remain open.


### Ordinary cold Act2 completion (2026-09-24)

On619eca534 plus the new input/test fixture, ordinary native320 Sonic+Tails
completes Act2 from cold Act1 in43761 inputs, with zero deaths and no initial
position, shield, ring or emerald writes. The boulder carries five rings into
zone22/act0; Sonic ends at296/1196 with six rings and a live Tails. The boss-act
flash is still active at the endpoint. This closes this Act2 traversal, not the
subsequent fight, native pixel matching, or other width/donor/roster products.

The preserved fixture is `lrz2-sonic-tails-cold-boss-act-arrival-320.script`/`.bk2`
in `src/test/resources/routes/s3k/`. `TestLrzActTwoColdRouteCapture` adds50
full-registry restore/45-input replay spots over the remaining traversal and
boulder phases, with separate post-load checks. No replay window crosses the
level load. Door5/6 opening, boulder control, carried rings, destination and
roster are asserted. The initial door6 assertion at39950 was premature: the
measured timer was40, with opening starting39911 and reaching the ROM's64-step
completion at39974. The final assertion samples39980; no runtime change.

Fresh moving evidence:
- `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-act2-high-bridges-320/capture.mp4`:450frames,41862–42311; stills42100/42251 inspected.
- `$VIDEO_ROOT/lrz-bring-up/campaign-20260924-act2-cold-boss-act-arrival-320/capture.mp4`:1449frames,42312–43760; stills43350/43760 inspected.

Both complete CSVs have zero deaths and both videos pass full decode. The second
lift requires a run-up; the next bridge jump must happen before running off its
edge. The rejected automated continuation dies in the boss act at44805 and is
not part of this fixture. Its first lava crossing is the next cold-route frontier.

Validation selection from619eca534 chooses2912 ordinary classes plus guards
because new route files are unclassified. This slice changes only controller
fixtures and their regression test, so focused route/replay validation is
proportionate; it is not a full-suite pass. Combined campaign validation,
including the earlier shared DMA journal change, remains owed.

Final focused command: `JAVA_HOME=<JDK21> DISPLAY=:0 python3
tools/testing/maven_queue.py -Dmse=off -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen
-Dtest=TestLrzActTwoColdRouteCapture test` on619eca534 plus this slice:
2 tests pass,0 failures/errors/skips,81 total full-registry replay spots.
The new script/BK2 passes the production author/loader round trip.


### Direct-load background art native corroboration (2026-09-25)

Current revision `f9944ccd7` does not reproduce the historical font-art symptom
at `($2438,$629)`. A180-frame native320 Sonic-solo capture settles at player
`(9272,1580)`, camera`(9112,1484)`, with no deaths. Still120 shows the purple
background forms; the whole MP4 decodes successfully.

```sh
python3 tools/testing/maven_queue.py -Dmse=off exec:java \
  -Dexec.mainClass=com.openggf.tools.GameplayCaptureTool \
  "-Dexec.args=--game s3k --rom $REPO_ROOT/s3k.gen --zone lrz --act 2 --x 0x2438 --y 0x0629 --frames 180 --stills 30,120 --out-dir $VIDEO_ROOT/lrz-bring-up/campaign-20260925-direct-act2-current-320"
```

Fresh native corroboration uses the existing `capture_movie_checkpoints.lua`
exporter and `capture_native_references.py` host, official Linux BizHawk2.11,
locked-on ROM SHA1`CFBF98C36C776677290A872547AC47C53D2761D6`, movie SHA256
`AD40FB0B0A74FA12B08AB71B2E48A7455B388D14F43F4CDED502AC4A15D1B3C0`,
and the existing415400 state (SHA256
`DE4CD8733D3B6F582A2BC592B54D7D8B8ABE7355586AA0766A507E2C2CBB09CF`).
Plan: `return {state_frame=415400,frames={425734}}`. Output is
`$VIDEO_ROOT/lrz-bring-up/campaign-20260925-native-act2-art/run1`.
The host completed with zero failures. The observed camera is`(9109,1486)`;
the inspected screenshot corroborates the same background near the engine view.
Its VRAM SHA256 is
`86e470aa157256dfa592712c63a070eb7a04c99d58b0195aded2c2a0e9da6467`.

Art comparison method: enumerate every pattern referenced by128px background
blocks`$D5–$E4`, pack each current engine8×8pattern into its32Genesis bytes,
and compare at `tileIndex*32` in native VRAM. All79static referenced tiles
(`$13C,$181,$2DD–$31F,$35E–$367`) match exactly. The remaining48tiles are
`$320–$343` and`$344–$34F`, the native `loc_282D0`/`loc_28364` animation
channels. Compare each whole channel with the eight ROM frames and legal
cyclic split-DMA rotations:

| Channel | ROM source / frame bytes | Engine frame, band | Native frame, band |
| --- | --- | --- | --- |
| `$320` | `$C0300 / $480`, rotation`band*$C0` | 5,2 | 6,2 |
| `$344` | `$C2700 / $180`, rotation`band*$60` | 3,0 | 3,0 |

Thus all127referenced patterns have native corroboration; none requires a
missing-art fallback or secondary-load patch. The earlier416433dump was not
sufficient: cameraXremained0 after the seamless change and its second channel
had not acquired a current animated frame. That rejected comparison does not
justify changing direct-load timing. The fresh later sample resolves the art
question without hydrating engine state from native observations.

This closes the specific missing-art report, not synchronized whole-scene
pixel or animation-phase acceptance. The native reference is Super Sonic;
the engine is a declared positioned ordinary Sonic load. The historical root
cause is unassigned. No production code changed and no engine test rerun is
needed for this evidence/status-only update.

## Tails solo opening traversal (2026-09-25)

A fresh uninterrupted native320 Tails-solo run now continues from cold Act1
through its seamless handoff and the opening Act2 flame/pillar section into the
upper-left climb. On `bc5e57709`, all36361 frames match the authored candidate
on12 recorded fields (position, velocities, ground speed, rings, death, mapping
frame, camera, follower presence and priority), with no deaths. Final state is
Tails at(3786,1329),11 rings, no follower. No gameplay state was seeded.

`$HOME/Videos/OGGF/lrz-bring-up/campaign-20260925-tails-act2-first-climb-320/capture.mp4`
records inputs34128–36360 after replaying the full prefix:2233 frames,60fps,
37.216667 seconds. Full video decode passed and the pillar/upper-turn stills were
inspected. Its external README preserves the exact command and source movie
(`campaign-20260925-tails-branch117/variant-0.bk2`).

This is partial cold traversal and engine presentation evidence. Act2 completion,
its full-registry replay spots and native visual matching remain open. The
committed Act1 movie/test already covers51 replay spots; those do not certify
this continuation. Route experiments that kept going right took a lower path;
the native Tails `lrz_3` rows5360–5540 instead show the upper-left turn. That
reference guided controller authoring only, without importing gameplay state.

## Tails middle corridor and replay checks (2026-09-25)

The preserved `lrz-tails-cold-act2-middle-320.{script,bk2}` now runs 41,922
ordinary inputs from cold Act1 to Tails at (5497,1008), one ring and zero deaths.
`TestLrzTailsColdRouteCapture#coldTailsRestoresActTwoTraversalToTheMiddleCorridor`
asserts the actual solo Tails roster/native320 and passes 25 full-registry
immediate restores plus 45-frame replays: platforms, both turbine captures and
releases, flight, monitor alcove, path switches, spring return and lower passage.
Act2 completion and its later mechanics remain open.

The fresh video at
`$HOME/Videos/OGGF/lrz-bring-up/campaign-20260925-tails-act2-turbines-corridor-320/capture.mp4`
records inputs37300–41921 after the entire cold prefix: 4622 frames, 60fps,
77.033333 seconds. All 41,922 rows match the authoring candidate on the same
12 state fields listed above, zero differences/deaths. Full decode passed;
stills37590,39221,41921 were inspected. The external README carries the exact
command. This is engine presentation and replay evidence, not native pixel parity.

The upper turbine needs a phase-dependent release (`loc_443C4`,
`byte_443B4`); jumping on its descending side was rejected because it launches
downward. Flying over the path switches near (3904,512)/(4096,512) left the
right-hand pipe impassable; returning below them and crossing normally succeeds.
The middle spring return initially failed because walking left hit the rock
badnik and bounced back into the spring. A leftward spindash clears that enemy.
These were controller choices; no production behavior changed.

On `c8f265822` plus this test/input change:
`JAVA_HOME=<JDK21> LUA_BIN=lua5.4 DISPLAY=:0 python3 tools/testing/maven_queue.py
-Dmse=off -Dopenggf.test.gl.native=true -Ds3k.rom.path=$PROJECT_ROOT/s3k.gen
-Dtest=TestLrzTailsColdRouteCapture#coldTailsRestoresActTwoTraversalToTheMiddleCorridor test`
passes one test, zero failures/errors/skips. Script regeneration yields the
identical BK2 input payload. The inspected change plan selects 2917 classes
because route assets are unclassified. Focused validation is proportionate for
this test/input-only extension: its complete production playback and all new
replay spots are exercised, with an independent fresh capture. No new broad
suite claim; combined campaign integration verification remains outstanding.

## Tails eastern climb and lower tunnel (2026-09-25)

Fresh playback on `42fa53430` extends the solo/native320 cold route to53,245
inputs, ending at(8724,1270),ten rings,zero deaths. It passes door8, the Fireworm
approach, the eastern turbine pair and breakable walls, then brakes before the
upper spring to enter the lower tunnel. The external source is
`campaign-20260925-tails-branch162/variant-0.bk2`; the committed replay fixture
still ends41922 and later rewind/exit coverage remains open.

`$HOME/Videos/OGGF/lrz-bring-up/campaign-20260925-tails-act2-eastern-tunnel-320/capture.mp4`
records49600–53244:3645 frames/60fps/60.75seconds. Full decode passes. All53,245
rows match authoring on12 recorded fields with zero differences/deaths. Stills
50153,51282,53244 were inspected; the final fresh image is pixel-identical to
the corresponding branch-authoring image. The external README gives the command.

The final tunnel's black background area was checked against existing BizHawk
`campaign-20260924-native-act2-floor/far-east/f425500.png`, which shows that
feature too. Its verified native CSV gives player(8751,1274),camera(8591,1146),
versus engine(8724,1270),camera(8580,1174). Host metadata records the stock ROM
SHA1, exit0 and no failures. This is nearby-scene corroboration of that feature,
not exact pixel parity, a matched Tails/clock oracle or closure of the broader
native presentation obligations. No runtime change was justified.

## Tails lower-door passage (2026-09-25)

Fresh cold playback on `aa5ce2094` reaches(11965,1776),16 rings,zero deaths
in56,062 inputs. The solo/native320 route descends to floor button(9816,1652),
opens the lower door and passes into the late lava platforms. Holding the left
jump for12 inputs entered the adjacent giant ring; a one-input jump followed
by11 Left inputs,25 neutral and a separate right jump avoids it. No movement,
object or collision behavior was changed.

`$HOME/Videos/OGGF/lrz-bring-up/campaign-20260925-tails-act2-lower-door-320/capture.mp4`
records53245–56061:2817 frames,60fps,46.95seconds. All56,062 rows match the
source `campaign-20260925-tails-branch171/variant-0.bk2` on the12 fields above,
with zero differences/deaths. Full decode passes; stills55375,55500,56061 were
inspected. The external README records the exact command. This extends fresh
engine presentation evidence; the committed rewind fixture still ends41922,
and Act2 completion/Act3 handoff remain open.

The rejected giant-ring attempt exposed an authoring-tool limitation: its
checkpoint restores gameplay state, but its boundary guard only checks level
identity. A second candidate started in the previous candidate's special-stage
results mode. That comparison is invalid and discarded; use separate processes
for candidates that leave gameplay until the guard is strengthened. The selected
route above independently replays from cold and never takes that detour.
