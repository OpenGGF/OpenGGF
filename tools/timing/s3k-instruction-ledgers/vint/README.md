# VInt_8 / Set_Kos_Bookmark instruction ledger (kos46 rounds R9-R10, R13; research prototype)

> Citations: sonic3k.asm line numbers here were converted on promotion (2026-10-17) from the local checkout 1a454a0
> used by research passes 1-13 to the superproject pin skdisasm 044fa46. ROM addresses and bytes are identical in both
> revisions; see [../README.md](../README.md#citations).

## Scope and boundary
- **Closed routines:**
  - V_int_jump `$FFFFF0`;
  - VInt `$584-$5E4` (including the PAL delay);
  - VInt_8 `$7EA-$976`, with both the Hyper flash and NoFlash paths;
  - Poll_Controllers `$10DE-$1118`;
  - SpecialVInt_Function with routine 0;
  - Process_DMA_Queue `$1588-$15B8`;
  - the Do_Updates skeleton;
  - Set_Kos_Bookmark `$1BF0-$1C18`, both the live and the bookmarked path.
- **Charged here:** only emulated instruction cycles, using GPGX 051d430d timings (Motorola agrees on every tested path).
- **Not charged here:**
  - interrupt acceptance (50-59 cycles);
  - refresh;
  - Z80→68000 bus waits;
  - FIFO waits;
  - DMA freeze, which the Java DMA ledger owner prices.

## Files

| File | Contents |
|---|---|
| `vint8_cost.py` | ROM walk. Branches are decided only by `State`; hardware invariants are asserted (VBLANK wait and BUSACK wait each run once); ROM pins are checked. R13 adds the lag path (`Result.lag`). |
| `test_vint8_cost.py` | 7 tests. Also the shared native-oracle loader (`gpgx_cost`, `motorola_cost`, `oracle`) the other ledger tests import. |
| `vint_frame.py`, `test_vint_frame.py` | Composition of the closed VInt parts (R11-R13); 4 tests. |
| `listing.py` | Helper: linear decode of ROM ranges with listing text and native per-PC counts. |

The research draft is superseded by the Java-owned `S3kVIntInstructionCycles` diagnostic projection.
`S3kVIntDmaLedger.Record.instructions()` records its conditional closed-handler term and explicitly names the
unpriced callees. It supplies no readiness, gameplay state or timing-port input. Java tests compare the closed
form with a 1,920-state grid exported from this independent ROM walk; the grid is never runtime input.
The derived research dumps `path-terms.json`, `closed-form-check.txt` and `dma-start-prefixes.json` remain
regenerable from `vint8_cost.py`. The earlier research grid checked 3,840 states after correcting the flash/water
and deferred-Do_Updates/Demo_timer interactions.

The Java projection verifies every byte of the original ROM prefix `[0,$4E810)`, SHA-256
`29bc98328ecbdb7d8a8a6f00c98af597949b16fdd53ffe9276610aee4a7e4743`, through the routine-0
`rts` at `$4E80E`. This deliberately conservative check includes immediates, displacements and absolute call
targets; opcode-only pins accepted altered PAL delay counts and callees. It does not authenticate code after
that prefix or certify modified ROM compatibility. The offline source oracle is run only with the original
locked-on ROM identity specified in the parent README.

**What the tests check:**
- **Native oracle:** the walk reproduces native per-PC counts exactly in all closed routines for 42 buckets (6 captures, NTSC/PAL, idle/bookmarked, 0/1 DMA entries; 4,708 PCs, 30,598 executions). The native data supplies only test state and expected counts.
- **Negative perturbations** (PAL, DMA+1, Kos busy flip, Hyper flash) all break the match.
- **Fail-closed** for VInt_0, competition mode and more than 18 DMA entries.
- **Pin mutation** is rejected.

**Path terms:**

| Term | Cycles |
|---|---|
| Base (NTSC, idle) | 1,804 |
| PAL | +17,940 |
| Per DMA entry | +116 (a full queue of 18 totals 2,068) |
| Kos bookmarked | +110 |
| Kos busy, PC below Main | +38 |
| Kos busy, PC at or after Done | +60 |
| Hyper flash | +1,088 |
| Water full screen (NoFlash path only) | −8 |
| Water H-int ≥ 92 | +24 |
| Water H-int < 92 (skips Do_Updates) | −72 |
| Demo_timer non-zero (only when Do_Updates runs) | +18 |

## Engine inputs required

Each input names the RAM value the ROM tests. The Java diagnostic currently uses engine-owned region,
physical Kos-queue emptiness and water presence, plus a rewound fresh-level `Demo_timer` mirror. It refuses busy
Kos, water, Hyper flash, unknown producers/region/timer and unsupported handler paths. The table below lists the
full model's input obligations; it does not assert that every owner or writer is already available.

| Input | ROM value | Source |
|---|---|---|
| Selected VInt routine | `V_int_routine`; LevelLoop selects 8 | Main-loop mode owner |
| Region | `Graphics_flags` bit 6 | Configuration |
| Hyper flash active | `Hyper_Sonic_flash_timer` ≠ 0 | Hyper Sonic double-jump owner. The flash path also skips the CRAM DMA, so the DMA ledger must agree. |
| Water flags | `Water_full_screen_flag`, `Water_flag`, `H_int_counter` | Water owner |
| DMA entry count | per VInt | The Java DMA ledger (aca516) |
| Kos state at VInt | busy bit and interrupted PC | The Java Kos consumer (c781) |
| `Demo_timer`, `Competition_mode`, `Special_V_int_routine` | — | — |

**Open callees.** Their cycles are not estimated, and each needs its own owner input:
- **VInt_DrawLevel:** the plane-buffer tile writes queued by the frame's draw routines.
- **UpdateHUD:** which HUD fields changed, and their digits.
- **Process_Nem_Queue_2:** Nemesis queue occupancy and tiles per frame.

The VInt_0 lag path and non-zero SpecialVInt routines are open terms. Native histograms remain test oracles only.

## Wait-loop preconditions (GPGX 051d430d source, not inferred from samples)

**VBLANK wait** (`$592-$59C`, `move.w (VDP_control_port).l,d0 / andi.w #8,d0 / beq.s`):
- `system.c:446-449` sets status bit 3 at VBLANK start, before `m68k_run(vint_cycle)` raises IRQ 6 (lines 489-505).
- `system.c:582-583` clears the bit only on the last line of the frame.
- The handler reaches the read about 150 instruction cycles after acceptance.
- **The single iteration is exact if** VInt is accepted at least that long before the frame's last line, i.e. interrupts were not masked for almost the whole VBLANK. That is an explicit precondition. If it is violated the loop spins into the next VBLANK, and this ledger refuses to model that case.

**BUSACK wait** (`$7F2-$7FA`, `btst #0,(Z80_bus_request).l`):
- `stopZ80` writes `$100` to `$A11100`, and `genesis.c gen_zbusreq_w` sets `zstate |= 2` immediately (lines 462-480).
- `mem68k.c:341-357` returns D0 = 0 exactly when `zstate == 3`.
- **The single iteration is exact if** the Z80 is not held in reset (`zstate` bit 0 set). That is an explicit precondition; with the Z80 in reset the loop never exits.

## DMA trigger start prefixes

The prefix is the instruction cycles from the `$FFFFF0` jump to the start of each trigger write. It excludes interrupt acceptance, refresh, Z80 and FIFO waits, and every earlier freeze; the DMA owner adds those in order.

| Path | CRAM | HScroll | SAT | Queue entries |
|---|---|---|---|---|
| NTSC | 896 | 1,030 | 1,160 | 1,298 + 116·k for entry k ≥ 0 |
| PAL | 18,836 | 18,970 | 19,100 | 19,238 + 116·k |
| Water palette (CRAM trigger `$884`) | 898 | 1,022 | 1,152 | — |
| Hyper flash (no CRAM DMA) | — | 2,118 | 2,248 | 2,386 + 116·k |
