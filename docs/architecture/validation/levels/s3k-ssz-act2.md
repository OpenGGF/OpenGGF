# S3K Sky Sanctuary Zone act 2 coverage matrix

Game / canonical zone / act: S3K `S3K_SKY_SANCTUARY_2`, engine zone `$0A` act index 1,
ROM `Current_zone_and_act = $A01`, SKL object set.
Character route: Knuckles only (`LevelSelect_CheckSonicTails` denies Sonic and Tails except with
`Debug_cheat_flag != 0`; no Sonic/Tails art or route exists for act 2).
Owning plan: [SSZ bring-up](../../plans/2026-09-17-ssz-bring-up.md).
Status: in progress (slice 0 only). Nothing below certifies the act.

Incoming: HPZ Knuckles teleporter pad → `$A01` (`HpzTeleporterRouteHelperObjectInstance`), level
select, save progression. Outgoing: the cold stop line is `loc_7BCFC`, 120 frames after the defeat
at `loc_7BCB0`; `loc_5E6C0`, `sub_5B18E`, `Obj_Ending` and the credits belong to the ending
campaign (user decision 2026-09-17).

Native fixture: `runs/s3k-knuckles-complete-superemeralds/hpz` (21441 rows, `bk2_frame_offset`
412501, start `$80,$6AE`) covers the whole of `$A01` including the ending camera rise to `$1CA0`.
**There is no Knuckles replay package** (`tests/trace/s3k/` holds only `sonictails` and
`tailsfullchain`), and Knuckles trace testing is out of scope by user decision; act-2 rows rest on
authored routes and native probes from that movie. The trace directory identity table lives in the
LRZ campaign's edit to [trace frontier log](../../../status/trace-frontier-log.md).

Five claims are tracked separately per row: **implemented**, **cold-reachable**,
**rewind-verified**, **native behaviour matched**, **visually matched**.

## Obligations

| Obligation + spot | Contract / oracle (ROM owner) | Config cases | Test binding | Implemented | Cold-reachable | Rewind-verified | Native matched | Visually matched | Gap / action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LOAD: identity, resources, music, title card | `Sonic3kZoneRegistry` zone 10 act 1; `Pal_SSZ2` `$34` | 320 | — | pre-existing | yes (cold load) | — | — | baseline capture `raw-00-ssz2-before` | Resource identity not yet asserted by a test |
| CENSUS: placed objects and rings | `SSZ2_Sprites $1F95F2` (5 records: `$00:$00` ×3, `$79:$00`, `$B2:$00`), `SSZ2_Rings $1F98E8` (the `(0,0)` record only) | ROM decode | `TestS3kSszPlacementCensus` | yes | n/a | n/a | yes (decode pinned to the ROM) | n/a | No remaining placeholder family |
| ARRIVAL: screen init and controller | `SSZ2_ScreenInit` (`Obj_57C1E` X `$A0`, `$2D = $44`, camera `(0,$649)`, `Scroll_lock`, `loc_59078` with `$30 = 1`) | 320 + one wide | slice 9 | open | open | open | open | open | Slice 9 |
| CAMERA: act-2 controller `loc_59078` | `loc_59078` routines 0/4/8 | — | slice 9 | open | open | open | open | open | Slice 9 |
| BG: parallax and column waves | `SSZ2_*DeformArray`, `word_58C80` (FG per-line HScroll), `loc_5904A` (20-column VScroll waves) | arithmetic at 320; wide render pending | `TestS3kSszScrollBands` | partial: cold `$1000` fan, fixed `$5E` background, BG deformation and 20 VSRAM columns; later FG-event modes pending | yes on cold load | runtime accumulator captured | cold constants/source arrays matched | open | Add `word_58C80` FG deformation, later event modes, and moving 320/wide inspection |
| CUTSCENE: crane `$B2` | `Obj_KnuxFinalBossCrane`, `loc_7CB64` (`mus_EndBoss` then `mus_FinalBoss`), `loc_7D11C` | 320 Knuckles | `TestS3kSszAct2FinaleHeadless`, `TestS3kSszPlacementCensus` | partial: placed owner, camera clamp, approach/return, music wait, scroll lock and boss allocation | yes to boss allocation | generic fields eligible; round trip pending | source constants and signed wait transcribed; grab children pending | open | Continue with exact grab/carry children |
| BOSS: Mecha Sonic phase, forced run, Super phase, Master Emerald | `Obj_SSZEndBoss` act-2 init `($220,$4A0)`, `loc_7BBE0`, `Obj_SSZ2_Boss` (36 routines), `loc_7B996` (`mus_DDZ`) | 320 Knuckles | `TestS3kSszAct2FinaleHeadless`; phase/defeat tests pending | partial: act-2 init, two eight-hit phases, arena/music handoff and save stop line; exact 36-routine Super graph and emerald child pending | yes to first combat phase | boss fields captured; graph round trip pending | entry/defeat constants source-matched; attack graph pending | open | Implement exact Super graph, Master Emerald and forced-run bridge |
| PRESENT: palette rotation | `Run_PalRotationScript`, `Palette_cycle_counters+0` | — | slice 9 | open | open | open | open | open | Slice 9 |
| DEFEAT: save and stop line | `loc_7BCB0` (`Events_fg_4+1`, `object_control $83`, `SaveGame`), `loc_7BCFC` after `(2*60)-1` | 320 Knuckles | phase test pending | partial: `$BF`, `$1F`, 119-word waits, event byte and progression save | boss path present; cold combat completion pending | captured | constants source-matched | open | Add killing-hit/control assertions and ending-object stop |
| PRESENT: post-defeat floor patch | `SSZ2_ScreenEvent` stage 4 (`Ending_running_flag`) | — | slice 9 | open | open | open | open | open | Slice 9 |
| PRESENT: tile fill and ending island mask (seeded) | `SSZ2_ScreenEvent` stage 8 / `$C`, `loc_591D6` (`Map_KnuxEndingIslandMask`) | seeded `Events_fg_4 = $FF00` | slice 9 | open | **cold-blocked** | open | open | open | Only cold trigger is the ending object `loc_5E6C0` (`loc_5E98A`); the seeded write is declared |
| ANIM: act 2 animates nothing | `Offs_AniFunc` → `AnimateTiles_NULL` (`rts`) | — | slice 2 test asserts it | open | n/a | n/a | open | n/a | Slice 2 |
| LOAD: ending (`sub_5B18E`, `Obj_Ending`, credits) | ending campaign | — | — | blocked | blocked | blocked | blocked | blocked | Out of scope |

## Execution evidence

Worktree `.worktrees/ai-ssz-bring-up`, branch `feature/ai-ssz-bring-up`, base develop `035e48a58`.
All Maven through `python3 tools/testing/maven_queue.py -Dmse=off …` with
`-Ds3k.rom.path=<absolute path to the worktree>/s3k.gen`.

Slice 0, 2026-09-17. `-Dtest=TestS3kSszPlacementCensus`: 7 tests, 0 failures, 0 errors, 0 skips,
after a deliberate break confirmed the comparison runs. Baseline capture
`~/Videos/OGGF/ssz-bring-up/raw-00-ssz2-before` (Knuckles, 320, 360 frames; level start `(128,32)`,
camera `(0,0)`); frame 200 inspected — the act renders its static cloud layout with no arrival
controller, camera controller or crane. The ROM's `$80,$6AE` start in the native fixture is written
by `Obj_57C1E` (`Camera_Y + $65` with camera Y `$649`), not by the start-location table.
