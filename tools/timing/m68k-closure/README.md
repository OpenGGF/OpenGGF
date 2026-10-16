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
| `stall_counters.py` | Converts native stall-counter stamps (GPGX stall ABI v1, read at VInt `$584`, `Process_Kos_Queue` `$1C1A` and the `Level_frame_counter` increment `$6520`) into `--counters` JSON lines. Fails closed on missing, duplicated or regressing stamps. |
| `specs/s3k-ending-kos46-v1.json` | Exact case/window spec: ROM SHA-1, GPGX commit/blob, capture names with SHA-256, regions, windows, cited platform constants. |
| `test_frame_closure.py`, `test_stall_counters.py` | Decoder vectors, synthetic PASS/FAIL/INCOMPLETE/ERROR CLI cases and stamp-conversion cases. |

## Usage

```bash
python3 tools/timing/m68k-closure/frame_closure.py check \
  --spec tools/timing/m68k-closure/specs/s3k-ending-kos46-v1.json \
  --rom "<absolute path to the S3&K locked-on ROM>" \
  --capture-root <directory containing the native-kos-budget-* captures> \
  --gpgx-git <Genesis-Plus-GX repository containing commit 051d430d3d1b54625f9900c8f152d7f232e06daf> \
  [--counters <observer stall counters, schema openggf.m68k-stall-counters.v1, JSON lines>]
python3 -m unittest discover -s tools/timing/m68k-closure -p 'test_*.py'
```

`check` exit codes:

| Code | Status | Meaning |
|---|---|---|
| 0 | PASS | Every window closes within the spec tolerance and every term is known. |
| 1 | FAIL | Closure is impossible: the model over-predicts, a fully known residual excludes zero, or an observed counter falls outside the model's bound. |
| 2 | INCOMPLETE | A term is unknown: an unsupported instruction, missing or partial stall counters, an endpoint VInt that did not provably arrive in the idle loop, or violated flow assumptions. |
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
  - interrupt entry (44);
  - DMA freeze (`vdp_ctrl.c:632-690`);
  - Z80-area latency for absolute operands (`mem68k.c:144,179`);
  - refresh (`m68kcpu.c:308-313`). Refresh is simulated exactly over every phase for the idle loop and bounded by step overshoot elsewhere.
- **Not visible in 68000 execution counts:**
  - Z80 accesses to the 68000 bus (`memz80.c:95-118`);
  - VDP FIFO waits (`vdp_ctrl.c:2307,2401`);
  - the VInt-to-`Level_frame_counter` endpoint path.

  These come only from observer counters, so without `--counters` the result is INCOMPLETE, never PASS.
- **Windows** need the bucket before the window to be present. Both endpoint VInts must arrive in the idle loop with no Kos bookmark redirect.
- **Register-indirect Z80-area accesses** are not counted from operands. The observer's counters cover them.
