# Load-time V-int trial: ROM-stated waits only

Base: `origin/develop` at `e6a8b4a34`. Branch: `bugfix/ai-trace-load-vint-trial`.
Scope: steps 2-3 of the
[synchronous load accounting design](../designs/2026-11-03-synchronous-load-vint-accounting.md),
under the 2026-11-03 trial ruling. Merge condition: every V-int count must come
from a wait or loop the ROM states as game logic. No CPU-cycle, DMA or VDP
bandwidth pricing, no fitted or measured constant, no fixture, zone or frame key.

**Result: a proven boundary.** Two counted V-ints are ROM-structural and are
implemented. The other 48 of the Mega witness's 53 residual V-ints are lag
V-ints. Their number is set by the CPU time of synchronous work, so they can
be closed only by cycle-level pricing. That is outside the engine boundary.

## Method

A temporary property-gated probe logged every `GameLoop.step()`: mode, live
object clock, movie cursor, hardware job submit/claim, title-card branch,
Nemesis arm and object-clock writes. It ran on the queued
`TestS3kMegaRunChain` (`-Ptrace-replay-r7`) at the base and after each change.
Native phases come from the recorded rows of the same BK2. These are
`vblank_counter` (low word of `V_int_run_count`), `lag_counter`
(`Lag_frame_count`, `$F628`), `load_queue_state`, the title owner's
`object_state` (slot 8, `Obj_TitleCard` `$2D690`) and the `hardware_timing`
completions. Row samples are taken at the end of each frame. The probe has
been removed.

## Native phases of one fresh S3K load

Let R be the row whose V-int is `Pal_FadeToBlack`'s 22nd `Wait_VSync`
(`Pal_FadeToBlack`, sonic3k.asm:5074-5085). The same pattern was observed on
all three Mega loads (AIZ row 4567, Gumball row 1299, AIZ2 row 2024):

| Row | Native evidence | ROM owner |
| --- | --- | --- |
| R | fade ends; object RAM not yet cleared | `Pal_FadeToBlack` |
| R+1 | object RAM cleared; `Obj_TitleCard` present at routine 0; no KosM queued | `Level` setup (sonic3k.asm:7547-7769) runs across this V-int. There is no `Wait_VSync` here, so it is a **lag** V-int |
| R+2 | title routine 2, four title modules queued | `loc_62CC` (sonic3k.asm:7769-7781): `Wait_VSync`, then `Process_Sprites` runs `Obj_TitleCardInit` (62161-62205), then `Process_Nem_Queue_Init` |
| ... | KosM modules retire 2 rows apart, then the card settles | `Process_Kos_Module_Queue` (2758-2816); the exit is bounded by the Nemesis queue (6 patterns per V-int, `Process_Nem_Queue`, 2182-2195) |
| last KosM row | non-lag V-int; terrain module retires at `post_objects` | `loc_7870` (9772-9782): `Wait_VSync` and then the final retirement |
| tail | every row is a lag row (`Lag_frame_count` +1), or a masked row with no count | `LoadLevelLoadBlock2`, `j_LevelSetup` under `#$2700`, `loc_6468` (7789-7915) |

## V-int sources

Deficits are native minus engine V-ints at each destination's admission.
"Before" is the base after the step-1 fade fix `8a9a678ad`.

| Source | ROM citation | Status | Why |
| --- | --- | --- | --- |
| First title-loop `Wait_VSync` precedes `Obj_TitleCardInit` and the first `Process_Nem_Queue_Init` | `loc_62CC (sonic3k.asm:7769-7781)`, `Obj_TitleCardInit (sonic3k.asm:62161-62205)` | **Implemented** | The engine ran the init and armed Nemesis in the loading iteration. That iteration is `Level`'s own setup frame. Now both wait for the next serviced iteration |
| Final `loc_7870` iteration's `Wait_VSync` before the last terrain module retires | `LoadLevelLoadBlock / loc_7870 (sonic3k.asm:9772-9782)` | **Implemented** | The release scan already serviced this VBlank's hardware boundaries but did not count it on the object clock. Native row 1406 of the Gumball return is a non-lag V-int on which module 20 retires |
| Lag V-int while `Level` setup runs after the fade (`Clear_DisplayData` under `#$2700`, `clearRAM` and so on) | `Level (sonic3k.asm:7547-7769)`, `Clear_DisplayData (sonic3k.asm:1391-1420)` | Rejected | No wait is involved. Its existence depends on the DMA fill and CPU time |
| KosM pieces that take more than one frame to decompress (Gumball 4, Slots 12) | `Set_Kos_Bookmark` / `Process_Kos_Queue (sonic3k.asm:2829-2885)` | Rejected | Decompression bookmarks across V-ints; its duration is CPU time per piece. The existing FAST manifest is measured and was not extended |
| Post-terrain synchronous tail (Gumball 4, AIZ return 21, Slots 5) | `LoadLevelLoadBlock2 (sonic3k.asm:38714-38768)`, `loc_6310`/`j_LevelSetup` (7789-7803), `loc_6468` (7880-7915) | Rejected | Every recorded row is a lag row (`Lag_frame_count` increments) or a masked row. There is no `Wait_VSync` between `LoadLevelLoadBlock`'s return and `LevelLoop`. The count is CPU time |

| Mega boundary | Deficit before | After | Left: setup lag / KosM overrun / sync tail |
| --- | ---: | ---: | --- |
| Gumball admitted | 11 | 9 | 1 / 4 / 4 |
| Returned AIZ admitted (cumulative) | 33 | 30 | +21 sync tail; this load is represented, so recorded readiness aligns its queue phases |
| Slots admitted (cumulative) | 53 | 48 | +1 / 12 / 5 |

The engine's `loc_62CC` exit is bound by its Nemesis drain, not by the card.
Deferring only `Obj_TitleCardInit` therefore moved title-art completion by one
row without moving the exit. Deferring the first Nemesis arm as well moved the
exit; both are the same `loc_62CC` ordering. In the represented Gumball-return
load, the loading iteration belongs to the bonus phase and arms no title PLC,
so only the title owner and the release V-int change there.

## S2 CNZ1 slot seed

This tree's probe reproduces the 3d42aa8cd finding. The ARZ2→CNZ1 load runs
at the source tail. The harness then fills the clock to `$1B95B`. The engine's
locked title card runs level objects (`TitleCardProvider.shouldRunPlayerPhysics`),
so `CNZSlotMachineManager.routineInit` seeds `$B6` at `$1B9B6`. The harness
then rewrites the clock at admission. Native seeds `$53`, 9 V-ints before row
0. That is after the synchronous load, at the first `RunObjects` of the leave
loop (`Level_TtlCard` s2.asm:4914-4927, setup and leave loop s2.asm:4936-5066,
`SlotMachine_Routine1` s2.asm:59438-59460). This is a **phase-order** defect:
level objects run during the pre-load arrival loop. It also involves the
harness's post-hoc clock rewrite. It is not a missing load price, and no change
is made here.

## S1/S2/KiS2 sync-load lag cluster

The cluster's `dynamic-art-gap` axes are 36-40 movie rows early after each S2
or KiS2 special-stage return, 65 on the halfpipe chain and 80 at the S1
`sbz3` terminal tail. They match the recorded lag runs before `InitPlayers`.
For example, ss→seg2_ehz1 has census `[23,11,53,14,8,39,25]`, and its first
edge lies inside the final non-lag run. This is the same class as the S3K tail:
synchronous load CPU time, with no ROM-stated count. The contract-sanctioned
route is the existing recorded per-row lag admission (the gap admission census
`AbstractRunChainTest#admitLevelWhenReady` already uses for level→level gaps),
extended to special-stage return gaps. It is not ROM pricing and is not part of
this trial.

## Validation

See the lane report for the queued commands, focused tests and three-profile
before/after comparison.
