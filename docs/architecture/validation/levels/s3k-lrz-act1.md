# S3K Lava Reef Act 1 coverage matrix

Game / canonical zone / act: S3K `S3K_LAVA_REEF_1`, engine zone `$09` act index 0,
ROM `Current_zone_and_act = $900`, SKL object set (`Sprite_ListingK`, SK Set 2).
Character routes: Sonic + Tails, Sonic, Tails (falling intro at `($100,$20)`) and Knuckles
(start `($10,$7AD)`, intro run) through the act to the miniboss, results and the seamless
`-$2C00` handover to Act 2. Owning plan:
[LRZ bring-up](../../plans/2026-09-17-lrz-bring-up.md); starting inventory:
[LRZ placement inventory](../../research/s3k-zones/lrz-object-inventory.md).
Status: slice 0 baseline only.

Incoming: level select / data select `$900`, SOZ2 end boss -> `$900` (verified as a request and
load at the end of the campaign, not the route entry). Outgoing: seamless `$901`.

Widths / donors / characters / teams (support authority `LaunchProfile.sanitizedFor`,
same roster as HPZ and DDZ): widths 320/400/512/640/800; supported character/donor pairs
off x {Sonic, Tails, Knuckles}, S1 x Sonic, S2 x {Sonic, Tails}; teams: solo, Sonic+Tails,
S1 Sonic+Sonic duplicate, S2 Sonic+Tails, Sonic+Tails+Knuckles at 800.

Five claims are tracked separately and never aggregated: **implemented**, **cold-reachable**,
**rewind-verified**, **native behaviour matched**, **visually matched**. Nothing below certifies
the act.

Placement baseline (`TestS3kLrzPlacementCensus`): 609 placed objects, of which **205 still build a
`PlaceholderObjectInstance`** after slice 1 (239 at `035e48a58`); 331 live rings (332 records minus
the leading `(0,0)` sentinel). The baseline only ratchets down.

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| BASELINE: placed object and ring census | `LRZ1_Sprites` `$1F6E50` (609), `LRZ1_Rings` `$1F874C` (332 records, 331 live; `loc_EB52`/`loc_E8BE`) | native | `TestS3kLrzPlacementCensus` | implemented | pass, `3418eba6e` | Ratchet target 0 placeholders |
| ENTRY: `$900` resources, bounds, object set, title card | Registry/sprite/screen-event tables; `Sonic3kLevelResourceProfile` | native | `TestSonic3kLevelLoading`, `TestS3kLrzFallingIntroBootstrap` | implemented (inherited) | pass | Not re-verified for this campaign |
| ENTRY: falling intro / Knuckles intro run | `loc_68A6`; `LRZ1_BackgroundInit` Knuckles `$F6` chunk | native 320 | `TestS3kLrzFallingIntroBootstrap` | partially implemented (intro only; no `$F6` chunk) | pass (intro) | Knuckles background chunk: slice 5 |
| PRESENT: parallax, bands and shake | `LRZ1_Deform`, `LRZ1_BGDeformArray` `$40,$20,$10x5,$100,$10x3,$20`, `ApplyDeformation` at `HScroll_table+$00C`; hand-walked band runs for camera `($800,$320)` | 320 and 640 | `SwScrlLrzTest`, `TestS3kLrzScrollRegistrationHeadless` | implemented | pass, `bbd156d37` | Visually matched only against the previous fallback (clip `02`), not against native |
| PRESENT: animated tiles ch0/ch1 and `AniPLC_LRZ1` | `AnimateTiles_LRZ1` / `loc_282D0`, `word_2834C`, `AniPLC_LRZ1` `$28A6A` | native | — | not implemented (AniPLC only, no custom split DMA) | open | Slice 2 |
| PRESENT: palette cycles | `AnPal_LRZ1` | native | `TestS3kLrzPaletteCycling` | implemented (inherited) | pass | Not re-verified for this campaign |
| PRESENT: rock sprites | `Draw_LRZ_Special_Rock_Sprites`, `sub_1CB68`, window `loc_1CAF4` | native + wide | — | not implemented | open | Slice 2; sprite-budget interaction |
| EVENT: runtime state and rewind adapter | `Events_routine_bg`, `Events_bg+$0C/$10/$12`, background camera copies, `LRZ_rocks_routine`, stored camera bounds, `ShakeScreen_Setup` | native | `TestS3kLrzScrollRegistrationHeadless#runtimeStateCaptureRestoreRoundTrips` | implemented | pass | Words for later slices join the same state; no route rewind spot yet |
| EVENT: screen-event chunk edits and rock crusher | `LRZ1_ScreenEvent` `Events_bg+$0C` both signs; `loc_90512`/`loc_9056E` | native | — | not implemented | open | Slice 3d |
| EVENT: dome regions and locked background | `sub_56DCA`/`word_56F88` (three 5-word rows), `sub_56DAC`, `Obj_56EA0` | native + wide | — | not implemented | open | Slice 5 |
| OBJECT: lava blocks `$6E` (34 placements, 4 subtypes) | `Obj_InvisibleLavaBlock` -> `bset #4,shield_reaction` -> `Obj_InvisibleHurtBlockHorizontal`; `sub_1F58C` mask `$73` | native, all five shield states | `TestSonic3kInvisibleHurtBlockHObjectInstance` | implemented | pass, `bbd156d37` | Fire-shield clip deferred to slice 3 (no teleport-and-walk route from a `$05` monitor to a `$6E`); clip `05` shows the hurt |
| OBJECT: traversal families `$15 $16 $17 $18 $19 $1A $1B $1C $1D $1E $1F $20 $21 $22 $9C` | Per-id `Obj_LRZ*` routines and tables | native | — | not implemented (placeholders) | open | Slice 3 |
| OBJECT: badniks `$99 $9A $9B` (74 placements) | `Obj_Fireworm`, `Obj_Iwamodoki`, `Obj_Toxomister`; `PLCKosM_LRZ` | native | — | not implemented | open | Slice 4 |
| OBJECT: shared families already concrete (105 rows, 340 placements) | SK Set 2 pointer table | native | `TestS3kLrzPlacementCensus` (classification only) | implemented | classification pass | Per-subtype behaviour unverified |
| BOSS: miniboss `$9D` at `($2CA0,$880)` | `Obj_LRZMiniboss`, `off_7854C` 11 slots, `collision_property` 6 | native | — | not implemented | open | Slice 6 |
| LOAD: results and seamless `$901` handover | `Events_fg_5` -> `loc_56CAA`: `-$2C00` on players, objects, camera and bounds; `Clear_Switches`; `LRZ_rocks_routine` cleared | native | — | not implemented | open | Slice 6; timeline isolation |
| ORACLE: strict segment replay | `TestS3kSonicTailsLrzSegmentTraceReplay`, `TestS3kTailsFullChainLrzSegmentTraceReplay` | `-Ptrace-segments` | — | — | see [trace frontier log](../../../status/trace-frontier-log.md) | Slice 11 |

## Execution evidence

Worktree `.worktrees/ai-lrz-bring-up`, ROMs by absolute path, `maven_queue.py -Dmse=off`.
Slice 0 (`3418eba6e`): `-Dtest=TestS3kLrzPlacementCensus,TestSonic3kRingPlacement,TestS3kLrzFallingIntroBootstrap,TestS3kLrzPaletteCycling,TestSonic3kLevelLoading`
= 65 tests, 0 failures, 0 errors, 0 skips. Shared ring change re-checked with
`TestS3kAiz1SkipHeadless,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestRingManager,TestS3kAiz2BigRingCollision,TestS3kAiz2BigRingFormation,TestS3kCnzLateSSEntryRingPlacement,TestRingSparkleDelay`
= 59 tests, 0 failures, 0 errors, 0 skips.
Slice 1 (`bbd156d37`): focused batch of 1263 tests, 0 failures, 0 skips, plus `-Pguards` 669 tests,
0 failures. Media: `raw-00-lrz1-before/`, clips `00a-lrz1-baseline-before-work.mp4`,
`02-lrz1-parallax-before-after.mp4`, `05-lrz1-lava-block-before-after.mp4`.
