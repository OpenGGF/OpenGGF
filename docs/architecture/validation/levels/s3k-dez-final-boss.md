# S3K Death Egg final boss arena coverage matrix

Game / canonical zone / act: S3K `S3K_DEZ_BOSS`, engine zone `$17` act index 0,
ROM `Current_zone_and_act = $1700`. **Not Sonic 2's Death Egg**, and not the
`$1701` Super Emerald sanctuary, which has its own
[matrix](s3k-hpz-sanctuary.md). Owning plan:
[S3K DEZ bring-up](../../plans/2026-09-17-s3k-dez-bring-up.md).
Status: implementation in progress. The `$1700` identity, Act 3 carry, initial event
words/camera, arena floor/block actors, staged background redraw, dedicated deformation,
ROM-backed arena/boss art and dynamic boss graph are implemented and focused-tested.
Laser attacks, exact graph timing/composition and route captures remain open, so this
matrix does not yet certify the act.

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
| Implemented | Partial: `$1700` has DEZ runtime/events rather than HPZ, carry restore, dedicated deformation, staged redraw, arena floor/wall/falling blocks, ROM-backed art and an eight-child dynamic boss graph. Exact laser/boss sequencing remains open. |
| Cold-reachable | Not started |
| Rewind-verified | Partial: the session carry and twelve `Events_bg` words round-trip; final graph passes focused construction/state tests. Empirical mid-phase route rewind remains open. |
| Native behaviour matched | Not started; replay frontiers measured at `035e48a58` below |
| Visually matched | Not started; `raw-00-baseline-before-work/1700-final-boss` is the "before" capture |

## Obligations

| Obligation + spot | Contract / oracle | Config cases | Test binding | Implementation | Result (revision) | Gap / action |
| --- | --- | --- | --- | --- | --- | --- |
| ENTRY: `$1700` resource profile | `levartptrs $4C/$4C/$40`; player placement `loc_7FD9E`; no title card (`Act3_flag`, `loc_62B6`) | — | `TestS3kDezFinalArenaHeadless` | missing | unrun | Slice 9 |
| ENTRY: `Act3_*` carry | `loc_7F310` saves rings, timer and `Saved2_status_secondary`; `DEZ3_ScreenEvent` stage 0 `loc_5A49A` restores them | — | `TestSonic3kAct3Carry`, `TestS3kDezAct3RuntimeState` | implemented as session-lived `Sonic3kAct3Carry`; armed by the DEZ2 boss and consumed by DEZ3 | focused green, 2026-09-19 | Cold handoff and shield visual route still need the slice 11 capture |
| PRESENT: scroll and plane | `sub_5A508`, `sub_5A76C`, `loc_5A734`, FG wrap `Camera_X_copy & $1FF`, `Camera_Y_copy = $20 + shake` | 320 + one wide | `SwScrlHpzTest` | dedicated `SwScrlDez3`, event-word-derived BG position and staged full-plane redraw installed | focused green, 2026-09-19 | Wide moving capture remains open |
| PRESENT: laser DMA | `sub_5A79E`, `ArtUnc_DEZFBLaser` → tile `$208`, `$40` words when `Events_bg+$10 != +$12` | 320 | slice 9 test | missing | unrun | Slice 9 |
| EVENT: arena shrink stages | `Events_bg+$00`: `$6C0` → `$2C0` → `$6C0` → 0 | — | `TestS3kDezFinalArenaControllerInstance` | event/controller handoff and staged bottom-up refresh implemented | focused green, 2026-09-19 | Exact native frame cadence remains route-open |
| OBJECT: arena floor and falling blocks | `Obj_5A7C8`, `Obj_5A872`, `Obj_5A8E6`, `Obj_5A922`, `Obj_5A94C` | — | `TestS3kDezFinalArenaControllerInstance` | persistent solid floor/wall and independently rewound falling blocks implemented; `Map_DEZ3Blocks` is ROM-backed | focused green, 2026-09-19 | Moving capture and exact emitter cadence remain open |
| BOSS: `Obj_DEZ3_Boss` phases and chase | per-phase | — | `TestS3kDezFinalBossInstance` | initial run-in, eight-hit fight, arena-word handoff, chase and Sonic exit branches implemented in a rewind graph | focused green, 2026-09-19 | Graph composition, timings, presentation and Knuckles `Game_mode 0` branch remain open; not certified |
| EXIT: `loc_803D6` branches | `SaveGame`; `Player_mode < 2` and 7 emeralds → `$C00`; else `Player_mode != 3` → `$D01`; else `Game_mode 0` | — | `TestS3kDezExitBranches` | missing | unrun | Slice 10; closes the DDZ seeded-entry caveat |
| ORACLE: Sonic + Tails arena | `runs/s3k-sonic-tails-complete-emeralds/dez23_8` (`zone_id 23`, act index 0, 5,181 rows, offset 509032) — **this is `$1700`, not "Hidden Palace proper"** | — | `TestS3kSonicTailsDez238SegmentTraceReplay` (expected red) | — | blocked: 621 errors, first error frame 0 `x_sub` expected `0x0000` actual `0x0C00` (`035e48a58`) | Slices 9-10 |
| ORACLE: Tails arena | `runs/s3k-tails-full-chain-all-emeralds/dez23_8` (5,550 rows) | — | `TestS3kTailsFullChainDez238SegmentTraceReplay` (expected red) | — | blocked: 339 errors, first error frame 0 `camera_y` expected `0x0010` actual `0x0020` (`035e48a58`) | Slices 9-10 |

## Execution evidence

- 2026-09-19, working tree after `9ebc40e31d`: focused Maven invocation of
  `TestSonic3kAct3Carry`, `TestS3kDezAct3RuntimeState`,
  `TestS3kDezFinalBossInstance`, `TestS3kDezEndBossInstance`, `SwScrlHpzTest`
  and `TestSonic3kHpzRuntimeStateRegistration`: 21 tests, 0 failures, 0 errors,
  0 skips.
- 2026-09-19, working tree after `eddc0f3708`: `TestSonic3kPlcArtRegistry`
  with the locked-on ROM: 77 tests, 0 failures/errors/skips. The combined final-arena
  object, boss, runtime, scroll and art invocation is recorded with the next milestone.

See the [act 1 matrix](s3k-dez-act1.md#execution-evidence) for the single frontier command;
all six classes ran in one invocation with 0 skips.
