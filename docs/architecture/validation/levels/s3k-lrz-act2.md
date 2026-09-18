# S3K Lava Reef Act 2 coverage matrix

Game / canonical zone / act: S3K `S3K_LAVA_REEF_2`, engine zone `$09` act index 1,
ROM `Current_zone_and_act = $901`, SKL object set (`Sprite_ListingK`, SK Set 2).
Character routes: Sonic + Tails, Sonic, Tails (seamless arrival from Act 1, then the boulder
cutscene and `StartNewLevel $1600`) and Knuckles (`Obj_StartNewLevel` `$B3` at `($3FE0,$E0)` to
`$1601`). Owning plan: [LRZ bring-up](../../plans/2026-09-17-lrz-bring-up.md); starting inventory:
[LRZ placement inventory](../../research/s3k-zones/lrz-object-inventory.md).
Status: act 1 slices 0-3 have brought the shared classes with them; act 2 traversal is slice 7.

Incoming: seamless `$900` handover, level select `$901`, star-post reload.
Outgoing: `$1600` (Sonic/Tails, with the Act 3 carry) and `$1601` (Knuckles, with `SaveGame`).

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`,
same roster as HPZ and DDZ): widths 320/400/512/640/800; supported character/donor pairs
off x {Sonic, Tails, Knuckles}, S1 x Sonic, S2 x {Sonic, Tails}; teams: solo, Sonic+Tails,
S1 Sonic+Sonic duplicate, S2 Sonic+Tails, Sonic+Tails+Knuckles at 800.

Five claims are tracked separately and never aggregated: **implemented**, **cold-reachable**,
**rewind-verified**, **native behaviour matched**, **visually matched**. Nothing below certifies
the act.

Placement baseline (`TestS3kLrzPlacementCensus`): 455 placed objects, of which **136 still build a
`PlaceholderObjectInstance`** (281 at `035e48a58`, 277 after slice 1,
255 after slice 3b's doors and horizontal buttons, 254 after the `$16` wall ride, 240 after the
`$20` swinging spike ball, 197 after slice 4's Iwamodoki and Toxomister, and 136 after slice 7's
two orbiting spike balls, `$2B` (12) and `$2C` (40)); 281 live rings (282 records minus the
leading `(0,0)` sentinel).

Act 2 classes reached so far are all shared with act 1 and were implemented there: `$6E` (4),
`$19` (11), `$1C` (11), `$16` (1), `$20` (14), `$9A` (34) and `$9B` (9). **None of their act 2 skins or
placements has been exercised in act 2 itself** - that remains an owed row, recorded in the
[bring-up plan](../../plans/2026-09-17-lrz-bring-up.md) handover.

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
| PRESENT: Death Egg background sprite | End of `sub_57082`: `x = $678 - HScroll_table+$004`, kept when `x <= -$7E0`, else `0`; `y = $C0 - Camera_Y_pos_BG_copy`; `loc_5711E` deletes for `Player_mode 3` | native + wide | — | not implemented | open | Slice 7; draw path untraced |
| OBJECT: lava blocks `$6E` (4 placements) | `Obj_InvisibleLavaBlock`; `sub_1F58C` | native, all five shield states | `TestSonic3kInvisibleHurtBlockHObjectInstance` | implemented | pass, `bbd156d37` | Act-2 placements not exercised on a route |
| OBJECT: doors and horizontal buttons `$19` (11), `$1C` (11) | `Obj_LRZDoor` act 2 skin (`mapping_frame` 1, art base `$090`, `height_pixels $20`, so a shorter solid box) and `Obj_LRZButtonHorizontal` act 2 skin (`Map_LRZButtonHorizontal2` over `ArtTile_LRZ2Misc`, palette 1) | native | `TestLrzDoorsButtonsAndTriggers` (act 1 decode), `TestS3kLrzPlacementCensus` | implemented | pass, `d2c58f148` | The act-2 skin is registered but not exercised: no act-2 unit case, no route spot. Act 2 doors `$01-$0B` each have a `$1C` button; `$33/$05` is extra |
| OBJECT: traversal families `$25 $29 $2B $2C $2D $32 $37` (186 placements) | `Obj_LRZFlameThrower`, `Obj_LRZOrbitingSpikeBall*`, `Obj_LRZSolidMovingPlatforms` (`off_258BC`, `byte_25826`) | native | — | not implemented | open | Slice 7 |
| OBJECT: badniks `$99 $9A $9B` (52 placements) | `Obj_Fireworm`, `Obj_Iwamodoki`, `Obj_Toxomister` | native 320 + 400 | `TestFirewormBadnikInstance`, `TestIwamodokiBadnikInstance`, `TestToxomisterBadnikInstance`, `TestS3kLrzCompatibilityMatrix` | implemented (all three; the classes are act-agnostic and the badniks share one art sheet across both acts) | pass, `cad4a2e07` | Placements exercised in act 2 only through the census and the matrix's art rows; no act 2 route or clip yet |
| BREADTH: act 2's own skins | `Obj_LRZSinkingRock`'s act branch (`mapping_frame` 1 and the `$090` tile base, sonic3k.asm:87907-87910) plus the act 2 door, button and swinging-spike-ball art keys | native 320 and 400, in act 2 | `TestS3kLrzCompatibilityMatrix#actTwoExercisesItsOwnSkins`, `#actTwoSkinsAlsoLoadWide` | implemented | pass | Asserts the load really is act 2 and that each act 2 art key has a ready ROM-backed renderer. Behaviour of the act 2 placements themselves is still unverified |
| OBJECT: `$0F` collapsing bridges (25) and `$24` tunnel (10) | `Obj_CollapsingBridge` zone-9 mappings; `Obj_AutomaticTunnel` subtypes `$55-$59`, `$D5-$D9` | native | `TestS3kLrzPlacementCensus` (classification only) | implemented | classification pass | Per-subtype behaviour unverified |
| EXIT: boulder cutscene and `$1600` request | `Obj_LRZ2CutsceneKnuckles` `$AE` at `($38B0,$240)`, `CutsceneKnux_LRZ2`, `loc_63C14` (Y >= `$4C0`: `Act3_flag`, ring/timer/shield carry) | native, Sonic/Tails/Tails-alone | — | not implemented | open | Slice 8 |
| EXIT: Knuckles `Obj_StartNewLevel` `$B3` -> `$1601` | `Check_InMyRange word_86426`; word decode at `subtype` (SST `$2D` must be 0); `SaveGame` only for `Player_mode 3` | native, Knuckles | — | not implemented | open | Slice 8; open question on whether Sonic can reach it |
| ORACLE: strict segment replay | `TestS3kSonicTailsLrzSegmentTraceReplay` (act 2 from row 25557), `TestS3kTailsFullChainLrz3SegmentTraceReplay` | `-Ptrace-segments` | — | — | see [trace frontier log](../../../status/trace-frontier-log.md) | Slice 11 |

## Execution evidence

Worktree `.worktrees/ai-lrz-bring-up`. Slice 0 evidence is shared with the
[Act 1 matrix](s3k-lrz-act1.md). Baseline media: `~/Videos/OGGF/lrz-bring-up/raw-00-lrz2-before/`.
