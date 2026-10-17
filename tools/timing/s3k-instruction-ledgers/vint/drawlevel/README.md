# VInt_DrawLevel and Process_Nem_Queue_2 ledgers (kos46 research, 2026-10-17, R11-R12, unexecuted in the engine)

> Citations: sonic3k.asm line numbers here were converted on promotion (2026-10-17) from the local checkout 1a454a0
> used by research passes 1-13 to the superproject pin skdisasm 044fa46. ROM addresses and bytes are identical in both
> revisions; see [../../README.md](../../README.md#citations).

Source-only prototypes; the Java owner (t3-a2ddcd58) owns any production adoption. Instruction time only, pinned GPGX
051d430d table (equal to Motorola on every path here). FIFO waits on VDP data-port writes, interrupt acceptance,
refresh, Z80 waits and DMA freeze remain separate terms.

## VInt_DrawLevel ($4E758) - CLOSED given the descriptors
cycles = 38 + L(Plane_buffer) + 16 + (26 if Plane_buffer_2_addr == 0 else 12 + L(list at $FF1000))
L(list) = 50 + sum(row: 312 + 60n, column: 308 + 60n), n = longwords per VInt_VRAMWrite pass (2n per descriptor).
Empty frame = 130. Descriptors come from Setup_TileRowDraw / Setup_TileColumnDraw (rows n 1..$20, columns 1..$10,
wrap split into two descriptors, list ended by clr.w (a0)); Plane_buffer_2_addr only from Slots_BackgroundEvent.
Covered: 40 empty native buckets + 2 v7c buckets (2 rows + 2 columns, 72 blocks = 5690 cycles; histogram fixes only
rows/columns/total blocks and the cost is invariant to the split). Uncovered (synthetic): list 2, wrap splits,
single descriptors, bounds.

Missing engine input (per VInt): the ordered descriptor list (row/column, n) the ROM producers would have queued in
the preceding main-loop frame. ROM producer paths: ScreenEvents -> LevelEventArray[zone] FG/BG handlers ->
DrawTilesAsYouMove / Draw_TileColumn[2] / Draw_TileRow[2] (camera coarse-position delta; Plane_double_update_flag
doubles the draw on a $20 jump), Draw_BG / Draw_PlaneVert* / Draw_PlaneHorz* (zone BG deformation handlers),
Refresh_Plane* when they queue through Plane_buffer, Slots_BackgroundEvent (list 2). For the ending:
Ending_ScreenEvent / Ending_BackgroundEvent (sonic3k.asm 120921, 121254) call Draw_TileRow, Draw_BG,
Draw_PlaneVertBottomUp[Complex], DrawTilesAsYouMove and Refresh_PlaneFull. Not to be derived from pixels or scroll
proxies: the descriptor counts are the ROM producers' d6 values after the wrap split.

## Process_Nem_Queue_2 ($17F4) - CLOSED (R12), source-only
Empty (Nem_patterns_left == 0): 38. Nonempty: fixed part 612 (>= 4 patterns left) or 1014 + 76P (P <= 3, ShiftUp of
22 longwords, FixBugs = 0 keeps the 16th slot), plus the decoder cycles of the next min(3, P) patterns.
Decoder: nem/nem_decoder.py micro-executes the shipped Process_Nem_Queue_Init/Nem_Build_Code_Table (main loop) and
Process_Nem_Queue_2/Nem_PCD_* (VInt) on a private mini68k model (../../mini68k/nem_forms.py adds only LSL/LSR Dx, ASL #n,
OR/EOR Dn, JMP (An), ADDA.W #). Register shifts cost GPGX base + 2 x count (m68kops.h 9614-9623). Each VInt returns the
saved decoder state (source pointer, writer, carried run d0, palette, XOR row, data word, shift value); the final VInt
of an entry takes ShiftUp and does not save, so its RAM vars are stale exactly as on the ROM.
nem/nem_event_cost.py is the port-ready form: a format-level decoder whose per-event costs are sums of the pinned
instruction times along the ROM PC paths; it equals the micro-execution on every VInt (cycles, rows, carried d0/d6)
and on the main-loop Init term. A research Java draft of it and its source-level checker (101 arts / 3081 progress
points) stayed with the Java owner and were not promoted; neither was compiled or run here.
Evidence: REAL ROM ART - PLC_SKCredits (ArtNem_CreditsText $DDE34, XOR, 65 patterns, 22 VInts) and
PLC_CreditsKnuxPose (ArtNem_KnuxEndPose $DEA00, XOR, 134 patterns, 45 VInts) plus all 101 Offs_PLC arts (31 plain,
70 XOR) decode byte-equal to the independent nem/nem_reference.py. SYNTHETIC - plain/XOR, inline escapes, both
refills, runs carried across the VInt boundary, back-to-back entries (next entry starts the following frame).
NATIVE-ABSENT - no retained capture executes $1646. MUTATIONS - art bit flip, dropped carry, wrong writer, ignored
shift count, constant edits: all red.
Production inputs (per queue head): ROM image identity, art ROM address (header word gives XOR flag and pattern
count), patterns already decoded. Sonic3kLevelTitlePlcService owns the fresh-zone title phase
(beginFreshZoneTitle: primary Level PLC from LevelLoadBlock + character/HUD PLC; serviceVBlank 6 patterns in
LEVEL_TITLE_CARD, 3 in SPECIAL_STAGE_RESULTS; prepareAfterLoop owns the head boundary). Unowned: ordinary
LevelLoop / event-owned / ending Nemesis scheduling after that phase (objects, bosses, BG events, Ending credits;
VInt_8 -> Do_Updates -> Process_Nem_Queue_2), as its Javadoc states. Its NemesisPlcServiceQueue.Entry(sourceAddress,
destinationTile, totalPatterns, remainingPatterns) already carries the needed identity and progress. Semantic differences from the ROM: the engine
keeps a separate active slot, while the ROM decodes in queue slot 0 with an advancing source pointer; replaceQueued
never interrupts the active entry, whereas Load_PLC_2/Clear_Nem_Queue zero Nem_patterns_left and abort it; the engine
deque has no 16-slot bound (the title service refuses > 15), whereas Load_PLC/Load_PLC_Raw write past slot 16 with no
check. Ending credits use Load_PLC_Raw (append), so only the missing owner blocks them, not those differences.

Tests: drawlevel/test_drawlevel_cost.py (8), nem/test_nem_cost.py (6), nem/test_nem_decoder.py (8, ~45 s),
../test_vint_frame.py (4: whole VInt call tree equals native in all 42 buckets; credits job closes every VInt). Env: S3K_ROM, GPGX_GIT, KOS46_NATIVE_ROOT.
The interpreter extensions live in `../../mini68k/` (`vdp_forms.py`, `nem_forms.py`).
