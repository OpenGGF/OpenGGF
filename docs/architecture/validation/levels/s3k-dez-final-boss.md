# S3K Death Egg final boss arena coverage matrix

Game / canonical zone / act: S3K `S3K_DEZ_BOSS`, engine zone `$17` act index 0,
ROM `Current_zone_and_act = $1700`. **Not Sonic 2's Death Egg**, and not the
`$1701` Super Emerald sanctuary, which has its own
[matrix](s3k-hpz-sanctuary.md). Owning plan:
[S3K DEZ bring-up](../../plans/2026-09-17-s3k-dez-bring-up.md).
Status: not started. Nothing below certifies the act.

LevelSizes (sonic3k.asm:38143): x `0`-`$6000`, y `$20`-`$20`. Level art
`levartptrs $4C,$4C,$40` (PLC `$4C`, palette `$40`, `ArtKosM_DEZ3`,
sonic3k.asm:199483). Music `Sonic3kMusic.DEZ2`. Animated tiles: `AnimateTiles_NULL`
(Offs_AniFunc entry 46) — no AniPLC script; the only animated art is the laser DMA
`sub_5A79E`. No AnPal entry (`AnPal_None`).

Incoming: the act 2 boss exit (`loc_7F310` saves `Act3_ring_count`, `Act3_timer` and
`Saved2_status_secondary`, then `StartNewLevel $1700`). The ROM level select lists
`$1700` as "DDZ act 2" (sonic3k.asm:10161); the engine level select has no entry
(`Sonic3kLevelSelectConstants:96-97`) — adding one is in scope (slice 9).
Outgoing: `loc_803D6` → `$C00`, `$D01` or `Game_mode 0`.

## Baseline behaviour without a resource profile (measured 2026-09-17, `035e48a58`)

A direct `$1700` load through `GameplayCaptureTool` boots the real DEZ3 layout, art and
palette (the Earth backdrop renders) but places Sonic at centre `$60,$70` — the Start
Location file, which the ROM overwrites in `loc_7FD9E` (P1 `$30,$CD`, P2 `$10,$CD`,
`object_control $81`). With no `Obj_5A7C8` arena floor the player falls out of the
level and dies at frame 98. No title card is drawn. Capture:
`~/Videos/OGGF/s3k-dez-bring-up/raw-00-baseline-before-work/1700-final-boss`.

## Five claims

| Claim | State |
| --- | --- |
| Implemented | Not started (standard level load only; no `$1700` resource profile, no events, no objects, and the scroll handler is HPZ's because `Sonic3kScrollHandlerProvider` keys zone `$17` without the act — slice 1's new `ZONE_DEZ` case is zone `$0B` only and does not touch `$17`) |
| Cold-reachable | Not started |
| Rewind-verified | Not started |
| Native behaviour matched | Not started; replay frontiers measured at `035e48a58` below |
| Visually matched | Not started; `raw-00-baseline-before-work/1700-final-boss` is the "before" capture |

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| ENTRY: `$1700` resource profile | `levartptrs $4C/$4C/$40`; player placement `loc_7FD9E`; no title card (`Act3_flag`, `loc_62B6`) | — | `TestS3kDezFinalArenaHeadless` | missing | unrun | Slice 9 |
| ENTRY: `Act3_*` carry | `loc_7F310` saves rings, timer and `Saved2_status_secondary`; `DEZ3_ScreenEvent` stage 0 `loc_5A49A` restores them | — | slice 9 test | missing | unrun | Slices 8 and 9; shared owner with LRZ |
| PRESENT: scroll and plane | `sub_5A508`, `sub_5A76C`, `loc_5A734`, FG wrap `Camera_X_copy & $1FF`, `Camera_Y_copy = $20 + shake` | 320 + one wide | `TestS3kDezFinalArenaHeadless` | missing (`$17` resolves to `SwScrlHpz`) | unrun | Slice 9; the provider's act-keying for `$16`/`$17` is the LRZ campaign's shared edit |
| PRESENT: laser DMA | `sub_5A79E`, `ArtUnc_DEZFBLaser` → tile `$208`, `$40` words when `Events_bg+$10 != +$12` | 320 | slice 9 test | missing | unrun | Slice 9 |
| EVENT: arena shrink stages | `Events_bg+$00`: `$6C0` → `$2C0` → `$6C0` → 0 | — | slice 9 test | missing | unrun | Slice 9 |
| OBJECT: arena floor and falling blocks | `Obj_5A7C8`, `Obj_5A872`, `Obj_5A8E6`, `Obj_5A922`, `Obj_5A94C` | — | slice 9 test | missing | unrun | Slice 9 |
| BOSS: `Obj_DEZ3_Boss` phases and chase | per-phase | — | `TestS3kDezFinalBossHeadless` | missing | unrun | Slice 10 |
| EXIT: `loc_803D6` branches | `SaveGame`; `Player_mode < 2` and 7 emeralds → `$C00`; else `Player_mode != 3` → `$D01`; else `Game_mode 0` | — | `TestS3kDezExitBranches` | missing | unrun | Slice 10; closes the DDZ seeded-entry caveat |
| ORACLE: Sonic + Tails arena | `runs/s3k-sonic-tails-complete-emeralds/dez23_8` (`zone_id 23`, act index 0, 5,181 rows, offset 509032) — **this is `$1700`, not "Hidden Palace proper"** | — | `TestS3kSonicTailsDez238SegmentTraceReplay` (expected red) | — | blocked: 621 errors, first error frame 0 `x_sub` expected `0x0000` actual `0x0C00` (`035e48a58`) | Slices 9-10 |
| ORACLE: Tails arena | `runs/s3k-tails-full-chain-all-emeralds/dez23_8` (5,550 rows) | — | `TestS3kTailsFullChainDez238SegmentTraceReplay` (expected red) | — | blocked: 339 errors, first error frame 0 `camera_y` expected `0x0010` actual `0x0020` (`035e48a58`) | Slices 9-10 |

## Execution evidence

See the [act 1 matrix](s3k-dez-act1.md#execution-evidence) for the single frontier command;
all six classes ran in one invocation with 0 skips.
