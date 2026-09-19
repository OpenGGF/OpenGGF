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
| CAMERA: act-2 controller `loc_59078` | `loc_59078` routines 0/4/8 | — | slice 9 | partial: load camera/arrival and foreground stage gate; gradual ending camera routines pending | cold load | runtime state captured | initial constants matched | open | Implement ending-only gradual camera stages |
| BG: parallax and column waves | `SSZ2_*DeformArray`, `word_58C80` (FG per-line HScroll), `loc_5904A` (20-column VScroll waves) | arithmetic at 320; wide render pending | `TestS3kSszScrollBands` | partial: cold `$1000` fan, fixed `$5E` background, BG deformation and 20 VSRAM columns; later FG-event modes pending | yes on cold load | runtime accumulator captured | cold constants/source arrays matched | open | Add `word_58C80` FG deformation, later event modes, and moving 320/wide inspection |
| CUTSCENE: crane `$B2` | `Obj_KnuxFinalBossCrane`, `loc_7CA3A`..`loc_7CB64`, hook `loc_7CCFE`..`loc_7CE66`, camera helper `loc_7D11C` | 320 Knuckles | `TestS3kSszAct2FinaleHeadless`, `TestS3kSszPlacementCensus` | yes: placed owner, camera clamp, approach, hook descend/alignment, capture, lift, return, music waits, scroll lock and boss allocation | yes to boss allocation | yes at the hook capture boundary and one-frame forward replay | source constants, signed wait and `_unkFAB8` bits 0..4 asserted | open | Add a rendered inspection of the crane and carried Knuckles |
| BOSS: Mecha Sonic phase, forced run, Super phase, Master Emerald | `Obj_SSZEndBoss` act-2 init `($220,$4A0)`, `loc_7B8E6`..`loc_7BBE0`, `Obj_SSZ2_Boss` (36 routines), burst `loc_7C6F0`/`ChildObjDat_7D4A8`, missile pod `loc_7C78E`, emerald `loc_7C818` | 320 Knuckles | `TestS3kSszAct2FinaleHeadless`, `TestS3kSszMechaSpawnHeadless`, `TestSonic3kPlcArtRegistry` | partial: act-2 init, fresh eight-hit Super phase, transformation/forced-run bridge, all 36 native dispatcher slots, eight-way harmful energy burst, mirrored ROM-animated missile pod, ROM-art Master Emerald, arena/music handoff and save stop line; attached laser pending | yes into distinct Super dispatcher | yes: bridge, burst children, missile pod, emerald and graph fields captured; live graph capture/restore plus one-frame forward replay | route order, routine byte domain, waits, eight `word_7D172` velocity rows, pod offset/script, emerald ROM addresses and the effects sheet's 26 owned mapping frames source-matched; laser timing pending | open: ROM sheet now loads; moving inspection pending | Add the attached laser child and inspect the effects |
| PRESENT: palette rotation | `Run_PalRotationScript`, `Palette_cycle_counters+0` | — | slice 9 | open | open | open | open | open | Slice 9 |
| DEFEAT: save and stop line | `loc_7BCB0` (`Events_fg_4+1`, `object_control $83`, `SaveGame`), `loc_7BCFC` after `(2*60)-1` | 320 Knuckles | phase test pending | partial: `$BF`, `$1F`, 119-word waits, event byte and progression save | boss path present; cold combat completion pending | captured | constants source-matched | open | Add killing-hit/control assertions and ending-object stop |
| PRESENT: post-defeat floor patch | `SSZ2_ScreenEvent` stage 4 (`Ending_running_flag`) | Knuckles 320 | `TestS3kSszAct2FinaleHeadless` | yes: `$17/$18` ×4 plus `$17`, `$19` ×9 through mutation pipeline | yes from defeat handshake | pipeline + event state captured | write sequence matched | open | Add rendered inspection of the patched floor |
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
