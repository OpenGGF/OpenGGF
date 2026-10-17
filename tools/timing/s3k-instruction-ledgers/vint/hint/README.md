# Lag V-int and water H-int ledgers (kos46 research R13, 2026-10-17, unexecuted in the engine)

> Citations: sonic3k.asm line numbers here were converted on promotion (2026-10-17) from the local checkout 1a454a0
> used by research passes 1-13 to the superproject pin skdisasm 044fa46. ROM addresses and bytes are identical in both
> revisions; see [../../README.md](../../README.md#citations).

Source-only prototypes on the pinned ROM (SHA-1 CFBF98C3...) and GPGX 051d430d tables; Motorola tables agree on every
path. Instruction time only: interrupt acceptance, refresh, Z80/FIFO waits and DMA freeze stay separate.

## Lag V-int (V_int_routine == 0) - CLOSED (vint8_cost.py, Result.lag)
VInt $58E beq.w -> VInt_0_Main $608 (VInt_0 $606's addq.w #4,sp runs only for unofficial routine values that mask to
0; refused). It skips the VBLANK wait, Set_Kos_Bookmark and Do_Updates. 660 cycles for Game_mode $0C, NTSC, no
water, no competition; $88/$8C/$08 -72/-48/-24; any other Game_mode 464; PAL +17940; water +110 (full screen +102,
CRAM DMA from Normal/Water_palette); competition, no-water branch only, +188 / +196 (sprite page 0/1).
Inputs and owners: Game_mode, Graphics_flags bit 6 (region), Water_flag / Water_full_screen_flag (water owner),
Competition_mode, Current_sprite_table_page. WHETHER a lag V-int happens is not an input: it is an output of the frame
timing model (main loop not at Wait_VSync), never taken from trace lag rows.

## Water H-int with deferred Do_Updates - CLOSED (hint/hint_cost.py)
VInt_8 $946 sets Do_Updates_in_H_int when Water_flag and H_int_counter < 92 (Result.deferred_updates). The first
accepted line interrupt of the frame (H_int_flag set by the V-int) runs the handler body and, if deferred,
clr.b / movem / jsr|bsr Do_Updates ($95A) / movem / rte. Later line interrupts cost 42 (HInt2 58) and skip the deferred
check. Bodies: HInt3/HInt5 446 + 18 (counter < 200) or 28 (>= 200) + 4 + 480 per colour transfer, skip 476; HInt4/
HInt_6 596 + ... + 626 per transfer; HInt2 844. Transfers = first word of the Water_palette_data_addr table + 1,
low byte reduced by H_int_counter - 200 when >= 200. Deferred adds 390 (HInt2 388) plus UpdateHUD and
Process_Nem_Queue_2 from their own ledgers (vint_frame.FrameLedger.run_hint), +18 when Demo_timer != 0.
HInt5/HInt_6 are checked to be opcode copies of HInt3/HInt4 except the palette lea operand (offset $4C).
Inputs and owners: H_int_addr handler (water owner; HInt2 for HCZ, else HInt3 or HInt4 by V_blank_cycles >= $1000,
full-screen HInt5/HInt_6 from Handle_Onscreen_Water_Height), H_int_flag, H_int_counter, Water_palette_data_addr (ROM
table pointer), Do_Updates_in_H_int, Demo_timer. V_blank_cycles is a boot-time loop count (DetectPAL, sonic3k.asm
~511-524: 30-cycle poll loop over the active display, about $E40 before refresh, so stock timing selects HInt3/HInt5);
the engine must own it as a hardware-timing value, not read it from a trace.

## Six ending products
Ending zones have Water_flag 0 (CheckLevelForWater: the only Water_flag = 1 writer, sonic3k.asm 9819, is reached for
AIZ1/AIZ2/HCZ/CNZ2 (Knuckles)/ICZ2/LBZ2), so no water handler or deferral runs there. A lag V-int there takes the
NoWater branch. No Special_V_int_routine writer is reachable from Ending code (writers: AIZ1_AIZ2_Transition,
AIZ2BGE_WaitFire, MGZ2_LevelCollapse, LBZ1_EventVScroll, LBZ2BGE_*, SSZ1/SSZ2 screen events); the 42 buckets run
routine 0. The entry value is still engine-owned (Special_V_int_routine is outside the level-load clearRAM ranges).

Evidence: NATIVE-ABSENT for $608 and every H-int handler in all retained captures (test_native_absence). SYNTHETIC:
hand instruction sums, predicate vectors, twins, ROM-table colour counts, deferred Do_Updates body equal to the V-int
body, mutations (twin break, wrong table, unsupported handler, missing table). A research Java draft and its source
checker (632 lag and 51,200 H-int combinations) stayed with the Java owner and were not promoted.
Tests: hint/test_hint_cost.py (10). Env: S3K_ROM, GPGX_GIT, KOS46_NATIVE_ROOT; test_native_absence also needs
KOS46_NATIVE_EXTRA_ROOTS naming the other retained capture trees (it requires more than 30 histogram files).
Label note: skdisasm 044fa46 names the HInt_6 handler `HInt6` ($F92); this file keeps the 1a454a0 spelling.
