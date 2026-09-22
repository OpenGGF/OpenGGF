# S3K Lava Reef Act 2 coverage matrix

Game / canonical zone / act: S3K `S3K_LAVA_REEF_2`, engine zone `$09` act index 1,
ROM `Current_zone_and_act = $901`, SKL object set (`Sprite_ListingK`, SK Set 2).
Character routes: Sonic + Tails, Sonic, Tails (seamless arrival from Act 1, then the boulder
cutscene and `StartNewLevel $1600`) and Knuckles (`Obj_StartNewLevel` `$B3` at `($3FE0,$E0)` to
`$1601`). Owning plan: [LRZ bring-up](../../plans/2026-09-17-lrz-bring-up.md); starting inventory:
[LRZ placement inventory](../../research/s3k-zones/lrz-object-inventory.md).
Status: traversal families are implemented through slice 7 as of the 2026-09-22
continuation. The boulder cutscene and Knuckles exit remain open; route certification
and the remaining breadth/lifecycle/native obligations are still separate gates.

Incoming: seamless `$900` handover, level select `$901`, star-post reload.
Outgoing: `$1600` (Sonic/Tails, with the Act 3 carry) and `$1601` (Knuckles, with `SaveGame`).

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`,
same roster as HPZ and DDZ): widths 320/400/512/640/800; supported character/donor pairs
off x {Sonic, Tails, Knuckles}, S1 x Sonic, S2 x {Sonic, Tails}; teams: solo, Sonic+Tails,
S1 Sonic+Sonic duplicate, S2 Sonic+Tails, Sonic+Tails+Knuckles at 800.

Five claims are tracked separately and never aggregated: **implemented**, **cold-reachable**,
**rewind-verified**, **native behaviour matched**, **visually matched**. Nothing below certifies
the act.

Placement baseline (`TestS3kLrzPlacementCensus`): 455 placed objects, **two remaining
placeholders** (`$AE:00`, `$B3:2D`) after the launchers, turbines and chained platforms;
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
| PRESENT: parallax, bands and shake | `sub_57082`, `LRZ2_BGDeformArray` `$20,$20,$20,$10x4,$F0,$10x3,$20`, `ApplyDeformation` at `HScroll_table`; `Camera_Y_pos_BG_copy` = `3Y/32` | 320 and 640 | `SwScrlLrzTest`, `TestS3kLrzScrollRegistrationHeadless` | implemented | pass, `bbd156d37` | **Not visually matched**: a direct `$901` load draws HUD font tiles in the upper background rows ([known bug](../../../status/s3k-known-bugs.md)); the clip waits for the seamless entry |
| PRESENT: animated tiles and `AniPLC_LRZ2` | `AnimateTiles_LRZ2` / `loc_282D0` and `loc_28364`; `Offs_AniFunc` pairs `$901` with `AniPLC_LRZ2` `$28A84`; `Animate_Init` does **not** seed `Anim_Counters+1/+3` for `$901` | native | `TestS3kLrzPatternAnimation` | implemented | pass, `1ef1256ca` | A direct or star-post `$901` load whose first phase is 0 skips its first upload, as the ROM does; act 2's background art gap (known bug) still blocks a visual check |
| PRESENT: rock sprites | `LRZ2_Rock_Placement` (10 placements, all at `y=$7C8`), same window and vertical test as act 1 | native + wide | `TestLrzRockSpriteRenderer` | implemented | pass, `fbbb793f7` | Act 2's rocks sit in the opening corridor; not yet seen on a cold route |
| PRESENT: palette cycles | `AnPal_LRZ2` (channel D keeps the `FixBugs = 0` duplicated pair) | native | `TestS3kLrzPaletteCycling` | implemented (inherited) | pass | Not re-verified |
| PRESENT: the post-defeat palette and camera releases | `loc_78AA8` and the `loc_78AE6`/`loc_78B08` pair (sonic3k.asm:160505-160545): `End_of_level_flag` gates both, then `Camera_min_X_pos` is written at `$2C0` (with `Pal_LRZ2` over palette line 1 and `Pal_LRZMiniboss3` over lines 2 and 3) and again at `$940`. The fight's own `Pal_LRZMiniboss1`/`Pal_LRZMiniboss2` reach act 2 because `Load_Level` copies no palette | native | `TestLrzPostDefeatCameraRelease` (3) | implemented | pass | Clip `35` shows the swap on capture frame 3539 and its gold half matches the native capture `native-lrz2-bg/run1/f416433.png`. `word_78EAA`'s rotation script between the two releases is NOT implemented ([known bug](../../../status/s3k-known-bugs.md)) |
| PRESENT: Death Egg background sprite | End of `sub_57082`: `x = $678 - HScroll_table+$004`, kept when `x <= -$7E0`, else `0`; `y = $C0 - Camera_Y_pos_BG_copy`; `loc_5711E` deletes for `Player_mode 3` | native + wide | — | not implemented | open | Slice 7; draw path untraced |
| OBJECT: lava blocks `$6E` (4 placements) | `Obj_InvisibleLavaBlock`; `sub_1F58C` | native, all five shield states | `TestSonic3kInvisibleHurtBlockHObjectInstance` | implemented | pass, `bbd156d37` | Act-2 placements not exercised on a route |
| OBJECT: doors and horizontal buttons `$19` (11), `$1C` (11) | `Obj_LRZDoor` act 2 skin (`mapping_frame` 1, art base `$090`, `height_pixels $20`, so a shorter solid box) and `Obj_LRZButtonHorizontal` act 2 skin (`Map_LRZButtonHorizontal2` over `ArtTile_LRZ2Misc`, palette 1) | native | `TestLrzDoorsButtonsAndTriggers` (act 1 decode), `TestS3kLrzPlacementCensus` | implemented | pass, `d2c58f148` | The act-2 skin is registered but not exercised: no act-2 unit case, no route spot. Act 2 doors `$01-$0B` each have a `$1C` button; `$33/$05` is extra |
| OBJECT: flame throwers `$29` (52) | `Obj_LRZFlameThrower` (sonic3k.asm:89227-89448): subtype bit 7 picks the variant and the rest is `$32 = (subtype & $7F) * 4`, the idle length between `2*60`-frame bursts; emission is gated on `(Level_frame_counter+1) & 3`, `$2E = sin(angle) asr 4` with `addq.b #8,angle`, and the flame leaves at `sin/cos($2E) asl 2` from `x_pos + $10` (or `y_pos + $10`); `tst.b render_flags / bpl` makes an off-screen thrower run its cycle and its sound but allocate nothing | native | `TestLrzFlameThrower` (8) | implemented | pass | Eight cases from the ROM's immediates and its own sine table, broken on purpose once (`addq.b #4` for `#8`) and failing on the angle case only. **Owed**: no clip, no rewind spot on a route, no act 2 route position and no wide/donor/roster row |
| OBJECT: solid moving platforms `$2D` (52) | `Obj_LRZSolidMovingPlatforms` (sonic3k.asm:51012-51110): `(subtype >> 4) & 1` picks one of `byte_25826`'s two entries and `subtype & $F` one of `off_258BC`'s nine movers -- an `rts`, `Oscillating_table+$0A`/`+$1E` on x and on y, and four `sub_25974` ramps at limits `$5F` and `$7F`; `sub_25974`'s `$36` is an 8.8 accumulator whose HIGH byte the limit is compared against, and `$40` is an 8.8 acceleration stepping by 4 | native | `TestLrzSolidMovingPlatform` (7) | implemented | pass | Seven cases on the ROM's own arithmetic, broken on purpose once (masking `$36` to a byte) and failing on the two ramp cases only. **Owed**: no clip, no rewind spot on a route and no wide/donor/roster row |
| OBJECT: turbines `$32` (18 placements) | `Obj_LRZTurbineSprites`, `sub_44338`, `loc_44448`, `sub_4450A`; logical press byte, three capture bands, two native player slots | 320/352/400/528/800 × native/S1/S2 Sonic, plus native Tails/Knuckles | `TestLrzTurbineSprites`, `TestS3kLrzTurbineHeadless` | implemented in 2026-09-22 continuation | 26 cases, 0 failures/errors/skips | Real placement capture/release and whole-composite mid-ride/cooldown restore + forward replay. Art ready from ROM. Positioned moving captures at 320/800 and S1 400; 134 native ride-offset samples and three release velocities corroborated by read-only BK2 replay. Matched pixels and extra-team breadth open. |
| OBJECT: spike-ball launcher `$37` (9 placements) | `Obj_LRZSpikeBallLauncher`, `loc_448A8`, `loc_44916` | native | `TestLrzSpikeBallLauncher` | inherited implementation `c708e1a2b` | focused tests pass | In-flight child removal/recreation + whole-world replay pass in `TestS3kLrzLauncherRewindHeadless`; moving capture remains open. |
| OBJECT: chained platforms `$25` (3 placements) | `Obj_LRZChainedPlatforms`, ROM group/path tables, `sub_4A818` signed division; safe top, spiked underside | all five current widths × native/S1/S2 Sonic, native Tails/Knuckles | `TestLrzChainedPlatforms`, `TestS3kLrzChainedPlatformsHeadless` | implemented | 23 behavior cases pass; isolated rewind inventory passes | All paths close both ways; all three real groups recreate without duplication; riding and underside hurt verified. Clip 38 shows positioned ride. Native first-step fraction `$73F8` agrees. Cold-route, extended teams and matched pixels remain open. |
| OBJECT: badniks `$99 $9A $9B` (52 placements) | `Obj_Fireworm`, `Obj_Iwamodoki`, `Obj_Toxomister` | native 320 + 400 | `TestFirewormBadnikInstance`, `TestIwamodokiBadnikInstance`, `TestToxomisterBadnikInstance`, `TestS3kLrzCompatibilityMatrix` | implemented (all three; the classes are act-agnostic and the badniks share one art sheet across both acts) | pass, `cad4a2e07` | Placements exercised in act 2 only through the census and the matrix's art rows; no act 2 route or clip yet |
| BREADTH: act 2's own skins | `Obj_LRZSinkingRock`'s act branch (`mapping_frame` 1 and the `$090` tile base, sonic3k.asm:87907-87910) plus the act 2 door, button and swinging-spike-ball art keys | native 320 and 400, in act 2 | `TestS3kLrzCompatibilityMatrix#actTwoExercisesItsOwnSkins`, `#actTwoSkinsAlsoLoadWide` | implemented | pass | Asserts the load really is act 2 and that each act 2 art key has a ready ROM-backed renderer. Behaviour of the act 2 placements themselves is still unverified |
| OBJECT: `$0F` collapsing bridges (25) and `$24` tunnel (10) | `Obj_CollapsingBridge` zone-9 mappings; `Obj_AutomaticTunnel` subtypes `$55-$59`, `$D5-$D9` | native | `TestS3kLrzPlacementCensus` (classification only) | implemented | classification pass | Per-subtype behaviour unverified |
| EXIT: boulder cutscene and `$1600` request | `Obj_LRZ2CutsceneKnuckles` `$AE` at `($38B0,$240)`, `CutsceneKnux_LRZ2`, `loc_63C14` (Y >= `$4C0`: `Act3_flag`, ring/timer/shield carry) | native, Sonic/Tails/Tails-alone | — | not implemented | open | Slice 8 |
| EXIT: Knuckles `Obj_StartNewLevel` `$B3` -> `$1601` | `Check_InMyRange word_86426`; word decode at `subtype` (SST `$2D` must be 0); `SaveGame` only for `Player_mode 3` | native, Knuckles | — | not implemented | open | Slice 8; open question on whether Sonic can reach it |
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
