# Main-loop skeleton ledgers (kos46 research R14, 2026-10-17, unexecuted in the engine)

Source: skdisasm **044fa46** (superproject pin; read with `git show 044fa46:sonic3k.asm`; the local checkout is at
1a454a0, whose line numbers passes 1-13 cited, converted on promotion), shipped FixBugs = 0, ROM SHA-1 CFBF98C3...,
pinned GPGX 051d430d instruction table (Motorola agrees except where noted). Instruction time only; interrupt ledgers (../vint),
acceptance, refresh, Z80/FIFO waits and DMA freeze are separate. No Java this round (the Java owner is fixing its
cache review items; production stays with it).

| Tool | Closes | Tests |
|---|---|---|
| `levelloop_cost.py` | One LevelLoop iteration ($650C..$65AC): body, Pause_Game gates, Wait_VSync, Demo_PlayRecord gate, no-water Handle_Onscreen_Water_Height gate, Process_Sprites dispatch (full and split loop), Process_Kos_Module_Queue + Queue_Kos | `test_levelloop_cost.py` (6) |
| `alloc_cost.py` | AllocateObject, AllocateObjectAfterCurrent, Delete_Current/Referenced_Sprite (child scheduling and deletion building blocks) | `test_alloc_cost.py` (4) |
| `osc_cost.py` | OscillateNumDo cycles and next oscillation state | `test_osc_cost.py` (5) |
| `attribute_pcs.py` | Ranks native bucket executions by routine (prioritisation only), sonic3k.lst + s3.lst at $200000 | used for the ranking below |

Env: S3K_ROM, GPGX_GIT, KOS46_NATIVE_ROOT; `closed-ranges.txt` lists the ranges priced by kos46 passes through R17
(sha256 `c51169213b3eea4fbc4fae4c732194382af95e7944278bf0f104079a4579d404`, copied unchanged). With it,
`attribute_pcs.py` leaves 1,356 of 12,721 instructions per ending bucket open (see `../README.md`); the 3,220 open at
the end of R14 is superseded.

## Closed terms (source constants)
* LevelLoop iteration, ending state (Ending_running_flag set, 0 objects, 1 Wait_VSync test, Kos modules idle):
  walker total; +18 per occupied SST slot (beq fall 8 + movea 4 + jsr 16 - beq taken 10), +22 per extra Wait_VSync
  test, LRZ +18, Slots +38, Ending_running_flag early return -76 versus the no-pause Pause_Main path.
  Exit order: Process_Sprites, then Restart_level_flag (bne.w Level), then the tail: Game_mode $08 -> DemoMode (open),
  $0C -> loop, otherwise rts: the loop exits when Game_mode changes (GameMode_SegaScreen / GameMode_Level writes after
  Ending_ScreenInit, 044fa46 sonic3k.asm 122956/122961).
* Process_Sprites: Teleport_active_flag returns at once; Competition_mode or Player_1 routine $C -> 110-slot loop;
  routine >= 6 -> 4 run, 90 draw-only (Draw_Sprite when render_flags bit 7), 16 run ("this is broken?").
  Physical-slot cursor: a slot is dispatched iff its code pointer is nonzero when the cursor reaches it, so a child
  made after the cursor (AllocateObjectAfterCurrent) runs this frame, one made before it (AllocateObject) runs next
  frame, and an object deleted ahead of the cursor is skipped. Handlers must preserve a0/d7; the native buckets'
  110 iterations are consistent with that.
* Process_Kos_Module_Queue: idle; not started and queue < 4 -> Queue_Kos + ori #$80; the queue-count word is compared
  unsigned, so the busy bit reads as a full queue; started and busy; complete -> Add_To_DMA_Queue (open), last module ->
  unrolled queue shift, then rts or jmp Process_Kos_Module_Queue_Init (open). GPGX charges andi.l #imm,Dn 16 cycles
  (Motorola model 14): asserted, GPGX is the authority.
* AllocateObject: first checked slot 4 (Dynamic_object_RAM + 1), 90 checks up to slot 93 (first Level_object_RAM
  slot); full RAM 2742. AllocateObjectAfterCurrent: counter from .lookup[(Dynamic_object_RAM_end - a0) >> 6]; from
  slot 92 it can allocate slot 93; callers in slots 0..3 index 104..107 past the 104-byte table and use the following
  code bytes ($43 for slot 3: 68 checks), executed as shipped; level-object callers wrap (slot 100 reads $C0 and
  returns; slot 94 reads $31 and would search past Object_RAM_end, refused). Delete_Referenced_Sprite 432,
  Delete_Current_Sprite 436.
* OscillateNumDo: 16 oscillators; Osc_Data2 speeds/limits equal OscillationManager's S2 tables; the engine's update rule
  equals the ROM's; the ROM skips the whole routine when Player_1 routine >= 6 outside Competition_mode (62 cycles).

## Evidence classes
* NATIVE (comparison only, 42 ending buckets): LevelLoop/Pause/Wait_VSync/Demo/water-gate/Process_Sprites per-PC
  counts equal; TEST-ORACLE inputs from the bucket, labelled: Wait_VSync test count (timing output), occupied slot
  count (object manager), Player_1 routine class. OscillateNumDo: state-independent PCs only.
* SOURCE-ONLY: Process_Kos_Module_Queue (Queue_Kos is shared with Ending event callers, and the capture's
  prepared_after_events row is sampled after the coordinator), the allocator (cursor/occupancy not in the capture),
  oscillation state transitions, split loop, gates and exits. Missing witness: Kos module state sampled at
  Process_Kos_Module_Queue entry ($1B28), and SST occupancy / Oscillating_table at the relevant calls.

## Production input contract
| Term | Inputs (RAM the ROM tests) | Owner |
|---|---|---|
| LevelLoop gates | Ending_running_flag, Life_count, Game_paused, Ctrl_1_pressed start, Demo_mode_flag, Restart_level_flag, Current_zone, Game_mode, Water_flag | ending events, pause/controller, level, water |
| Wait_VSync | number of V_int_routine tests before the V-int | frame timing model OUTPUT (never trace lag/rows) |
| Process_Sprites | Teleport_active_flag, Competition_mode, Player_1 routine, per-slot code pointer at cursor visit, render_flags bit 7 (split loop) | object manager (physical SST slots), player |
| Kos module queue | Kos_modules_left, Kos_decomp_queue_count (raw word), next Kos_module_queue entry | Kos consumer (Java owner); DMA owner for Add_To_DMA_Queue |
| Allocation/deletion | cursor slot, per-slot occupancy | object manager |
| OscillateNumDo | control word, 16 values, 16 deltas, Player_1 routine, Competition_mode | OscillationManager (its S3K caller must apply the routine >= 6 skip) |

## Still open main-loop functions (call counts come out of the walker as open terms)
Animate_Palette, SpecialEvents, Load_Sprites, DeformBgLayer, ScreenEvents (Ending_ScreenEvent/BackgroundEvent and the
plane-buffer producers), sub_714E, Load_Rings, Draw_LRZ_Special_Rock_Sprites, Animate_Tiles, ChangeRingFrame, Render_HUD, Render_Rings and sub_1CB68 (Render_Sprites, its flip piece writers and Draw_Sprite are closed in
../render, R15), Slots_RenderLayout /
Slots_CycleOptions, Add_To_DMA_Queue, Process_Kos_Module_Queue_Init, Draw_Sprite, every object handler (keyed by code
pointer), and the pause, demo-playback, DemoMode and water paths (OpenPath).
