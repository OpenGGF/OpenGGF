# 68000 frame-time closure

Offline check that a ROM-derived 68000 cost model explains every cycle of a native frame window. It exists to
validate a future production CPU-cost ledger for S3K Kosinski service timing (known bug "Ending Reunion Kos
Service Count", `docs/status/s3k-known-bugs.md`) without fitting anything. Native captures are comparison input
only; nothing here supplies runtime costs, gameplay state or timing authority.

## Files

| File | Role |
|---|---|
| `m68k_timing.py` | Decodes instructions from ROM bytes. Motorola MC68000 section 8 times as exact values or explicit operand intervals. Unsupported encodings raise. |
| `gpgx_cycles.py` | Reads `m68ki_cycles.h` from an explicit Genesis-Plus-GX repository at a pinned commit and blob id, and applies GPGX's dynamic cycle rules. |
| `frame_closure.py` | `report` and `check` commands. |
| `refine.py` | Proof-based narrowing of operand/branch-dependent costs. Each rule checks its preconditions from ROM semantics and the bucket's own counts: Scc outcome from the adjacent Bcc; `dbf`-counter bit numbers; spec-declared non-negative decrement counters (`dbmi` exits); spec-declared Z-exit accounting through callers. A failed check leaves the interval and records a note. No rule reads timing or residuals. |
| `interrupt_phase.py` | Recovers each VInt's GPGX acceptance cost (50..59 cycles, `m68ki_cycle_interrupts[(cycles/7)%10]`) from observation-only probe stamps (`interrupt_phase.py ROM GPGX_REPO STAMPS COUNTERS CASE FIRST_LF LAST_LF`) and adds `vint_interrupt_cycles` to counter rows. Requires strict last/accept pairs at `$FFFFF0`, monotonic stamps, one derivation for every requested frame and a counter row for each; rejects pre-populated phases it does not reproduce. The stacked return PC must be a decoded successor of the interrupted instruction: the fall-through of a sequential instruction, the taken or fall-through target of a conditional (which fixes its outcome), or the only target of an unconditional static jump (`bra`, `jmp` to an absolute or PC-relative address). Any other return PC, or an operand-dependent cost of the interrupted instruction, fails closed. |
| `stall_counters.py` | Converts native stall-counter stamps (GPGX stall ABI v1, read at VInt `$584`, `Process_Kos_Queue` `$1C1A` and the `Level_frame_counter` increment `$6520`) into `--counters` JSON lines. Fails closed on missing, duplicated or regressing stamps, and when no complete bucket is present. |
| `specs/s3k-ending-kos46-v2.json` | v1 plus the interrupt table blob and the two verified refinement facts (Render_Sprites `d7`, AllocateObjectAfterCurrent). Use this spec. |
| `specs/s3k-ending-kos46-v1.json` | Exact case/window spec: ROM SHA-1, GPGX commit/blob, capture names with SHA-256, regions, windows, cited platform constants. Kept as the first committed spec; it lacks `cpu_header_blob`, so `check` now returns ERROR, exit 3, rather than silently using the 44-cycle Motorola acceptance time. |
| `test_frame_closure.py`, `test_stall_counters.py`, `test_refine.py`, `test_interrupt_phase.py`, `test_refresh_bound.py` | 53 tests: decoder vectors, synthetic PASS/FAIL/INCOMPLETE/ERROR CLI cases, stamp-conversion cases, a positive case and a broken-precondition control for every refinement rule, all ten acceptance phases and the unconditional-jump successor, and refresh bounds against an independent simulation. |

## Usage

```bash
python3 tools/timing/m68k-closure/frame_closure.py check \
  --spec tools/timing/m68k-closure/specs/s3k-ending-kos46-v2.json \
  --rom "<absolute path to the S3&K locked-on ROM>" \
  --capture-root <directory containing the native-kos-budget-* captures> \
  --gpgx-git <Genesis-Plus-GX repository containing commit 051d430d3d1b54625f9900c8f152d7f232e06daf> \
  [--counters <observer stall counters, schema openggf.m68k-stall-counters.v1, JSON lines>]
python3 tools/timing/m68k-closure/interrupt_phase.py ROM GPGX_REPO STAMPS.csv COUNTERS.jsonl CASE FIRST_LF LAST_LF \
  > COUNTERS_WITH_INTERRUPTS.jsonl
python3 -m unittest discover -s tools/timing/m68k-closure -p 'test_*.py'
```

`check` exit codes:

| Code | Status | Meaning |
|---|---|---|
| 0 | PASS | Every term is known and every window's whole residual interval lies within the spec tolerance. |
| 1 | FAIL | Closure is impossible: the model over-predicts, a fully known residual excludes zero, or an observed counter falls outside the model's bound. |
| 2 | INCOMPLETE | A term is unknown: an unsupported instruction, missing or partial stall counters, an endpoint VInt that did not provably arrive in the idle loop, or violated flow assumptions. Also when every term is known but operand-dependent times or unforced branch outcomes leave a residual interval that only overlaps the tolerance. |
| 3 | ERROR | Identity or usage failure: ROM, capture or GPGX source differs from the spec. |

`report` prints the full JSON (per-bucket terms, window residuals, solver evidence) and does not judge.

## Model and limits

- **Instruction cycles.** Conditional outcomes are solved from flow conservation on the static control-flow graph: fall-through, static targets and call/return pairing. Dynamic landings must fit a budget made up of:
  - indirect transfers;
  - interrupt entries;
  - the bucket start;
  - declared bookmark redirects;
  - non-interrupt `rte`s.

  Outcomes that conservation does not force stay as intervals.
- **Stall terms derived from GPGX 051d430d:**
  - interrupt acceptance `m68ki_cycle_interrupts[(cycles/7)%10]`, 50..59 cycles (`m68kcpu.h:1405,1449`): an interval per VInt unless counters carry `vint_interrupt_cycles`. Motorola's 44-cycle autovector time excludes the E-clock synchronisation GPGX models;
  - DMA freeze (`vdp_ctrl.c:632-690`);
  - Z80-area latency for absolute operands (`mem68k.c:144,179`);
  - refresh (`m68kcpu.c:308-313`): checked against `m68k.cycles`, which every stall also advances (`memz80.c:114`, `vdp_ctrl.c:690,2307,2401`, `m68kcpu.h:1449`). The bound therefore needs the measured Z80/FIFO stall; without it the window is INCOMPLETE. Upper: refreshes are 128 apart and a DMA freeze longer than 128 absorbs the excess. Lower: each interval exceeds 128 by less than its straddling fetch gap (idle loop at most 11, at most floor(B/128)+2 body gaps, every stall attached to one gap).
- **Not visible in 68000 execution counts:**
  - Z80 accesses to the 68000 bus (`memz80.c:95-118`);
  - VDP FIFO waits (`vdp_ctrl.c:2307,2401`);
  - the VInt-to-`Level_frame_counter` endpoint path.

  These come only from observer counters, so without `--counters` the result is INCOMPLETE, never PASS.
- **Windows** need the bucket before the window to be present. Both endpoint VInts must arrive in the idle loop with no Kos bookmark redirect.
- **Register-indirect Z80-area accesses** are not counted from operands. The observer's counters cover them.

## Citations

`sonic3k.asm` line numbers in `specs/s3k-ending-kos46-v1.json` refer to the local `docs/skdisasm` checkout `1a454a0`
used by the research (`sonic3k.asm:2976` is `Wait_VSync`). The superproject pins `044fa46`, where `Wait_VSync` is line
3008; ROM bytes and addresses are identical. The v2 spec cites the label, address and pinned line. That `why` text is
the only difference from the research patch (`specs/s3k-ending-kos46-v2.json` sha256 `59103621...` before,
`689e768f...` after).

## Results at promotion (2026-10-17)

With the v2 spec, the retained captures and no counters, `check` returns INCOMPLETE, exit 2 ("stall counters absent").
With the v9 stall and interrupt-phase counters recorded by the native stall-counter lane (stall ABI v1 stamps,
acceptance phases derived by `interrupt_phase.py`), the eight pinned windows return PASS, exit 0, identical to the
lane's run. Residuals in cycles: Tails/+2 NTSC 4401 −7.714 and 4401–4405 −10.286; Sonic/+2 NTSC 4401 0.286
and 4401–4404 −9.857; Tails/+2 PAL 4401 −1.571 and 4401–4404 0.0; Sonic/+2 PAL 4401 −2.571; Sonic/−2 NTSC
(`sonic-none`) 4401 −1.714; all within the
12-cycle spec tolerance. PASS is agreement within that tolerance on these windows only. It is not a production cost
model, not a prediction of the six measured service counts or the 46 multi-valued fingerprints, and not a readiness
input. The method and limits are in the
[ending lifecycle design](../../../docs/architecture/designs/2026-10-13-s3k-ending-lifecycle.md#kos46-instruction-ledgers-promoted-as-diagnostics-2026-10-17).
