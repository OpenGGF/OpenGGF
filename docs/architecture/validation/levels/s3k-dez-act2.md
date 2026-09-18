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
| Implemented | Presentation foundation (static background, the two shared `AnPal_DEZ2` channels, the eight `AniPLC_DEZ` scripts, both `DEZ2_ScreenEvent` chunk stages, the direct-load routine values), reverse gravity for the player, shields, lost rings, dust-free solid objects, springs and the sidekick (86 of 116 ROM references — the 11 open group A-I rows are listed in [s3k-known-bugs](../../../status/s3k-known-bugs.md)), and the `$5B` gravity swap that makes it reachable |
| Cold-reachable | Not started |
| Rewind-verified | Event routine words (`TestS3kDezPresentationRewind`) and the `$5B` write plus its side latch, capture/restore/forward replay (`TestS3kDezGravityObjectsHeadless`) |
| Native behaviour matched | Not started; replay frontiers measured at `035e48a58` below |
| Visually matched | Clips `030` (320 and 528), `031` and `032` show the flag being written by a real `$5B` and the inverted run, jump, roll, rings and Knuckles that follow; no native pixel comparison. The sidekick, hit/lost-ring, shield and solid-object clips are blocked on slice 3's remaining objects and the act 2 route — see `INDEX.md` for the measurements |

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
| PLACEMENT: 494 act 2 objects | [inventory](../../research/s3k-zones/dez-object-inventory.md) | — | `TestS3kDezPlacementCensus` | placeholder baseline recorded | unrun | Slices 3-5 |
| GRAVITY: `$5B` writer, both crossing directions, band edges, latch, Player 1 only | `sub_49228` / `loc_49270` (sonic3k.asm:95472-95543); write not toggle; band `[y_pos-$20, y_pos+$20)` | — | `TestS3kDezGravityObjectsHeadless` | implemented (`S3kDezGravitySwapObjectInstance`) | pass 10/10, `ff080949c`+ | Player-2 case is a guard against a future sidekick loop, not evidence about one |
| REWIND spot: flag written by `$5B`, mid-corridor | capture after the write, clear it forward, restore, replay the same crossing | — | `TestS3kDezGravityObjectsHeadless` | implemented | pass | Replay covers the object's `$32` latch as well as the global flag |
| GRAVITY: `$58` writer — toggle on the 4th update, both faces, rider release, occupancy-blocked rearm | `loc_48AD6`/`loc_48B7E`/`loc_48B9C` (sonic3k.asm:94800-94910); `eori.b #1`, `d6 & $14`, `move.w #20-1,$30` | — | `TestS3kDezGravityObjectsHeadless` | implemented (`S3kDezGravitySwitchObjectInstance`) | pass 18/18 | **Inherited gaps:** the pad is invisible (`Map_DEZGravitySwitch` / `ArtTile_DEZMisc+$143` not registered for DEZ) and silent (`sfx_Transporter` has no `GameSound` constant). Both are shared-surface work for the DEZ misc-object art slice |
| REWIND spot: `$58` mid-count | capture between the press and the toggle, run past it, restore, replay | — | `TestS3kDezGravityObjectsHeadless` | implemented | pass | Replay toggles on the same update as the first run |
| GRAVITY: `$59` writer | subtype bit 7 via `rol.b #1,d0 / andi.b #1,d0`, `cmpa.w #Player_1` (`loc_48DF2`, :95080) | — | `TestS3kDezGravityObjectsHeadless` | missing | unrun | Slice 3; ROM reading written up in the plan's 2026-09-18 handover |
| GRAVITY: `$5A` tube, `$5C` hub, `$5F` room, `$61` puzzle | `$5A` reads the flag (`loc_48FBA`, `loc_4904A`); the other three contain no flag reference and move the player with `object_control` | — | — | missing | unrun | Slice 3 |
| BOSS: `$A7` end boss | `word_7F0BE` range, `word_7F0C6` arena `$218,$288,$3400,$34E0`, 8 hits, `sub_7F8A0` gravity | — | `TestS3kDezAct2BossHeadless` | missing | unrun | Slice 8 |
| REWIND: event routine words | Registry restore equals capture plus forward replay | 320 | `TestS3kDezPresentationRewind` | implemented | pass, `4e7655bf9` | Mid-flip, act change and boss spots not started |
| ORACLE: Tails act 2 | `runs/s3k-tails-full-chain-all-emeralds/ssz_2` (5,202 rows) | — | `TestS3kTailsFullChainSsz2SegmentTraceReplay` (expected red) | — | blocked: 229 errors, first error frame 0 `camera_y` expected `0x080E` actual `0x0810` (`035e48a58`) | Whole campaign |
| ORACLE: Tails act 2 restart | `runs/s3k-tails-full-chain-all-emeralds/ssz_3` (3,877 rows; act 2 restart, i.e. lifecycle evidence) | — | `TestS3kTailsFullChainSsz3SegmentTraceReplay` (expected red) | — | blocked: 200 errors, first error frame 0 `camera_y` expected `0x044E` actual `0x0450` (`035e48a58`) | Whole campaign |

## Execution evidence

See the [act 1 matrix](s3k-dez-act1.md#execution-evidence) for the single frontier command;
all six classes ran in one invocation with 0 skips.
