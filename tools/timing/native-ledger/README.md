# Native stall ABI v2 ledger checker

Offline, fail-closed check of a native per-interval 68000 ledger recorded by the TraceChaser
`--m68k-ledger-window` runner (stall ABI v2, native stall-counter branch). It was written for the S3K ending Kos
CPU-budget research (known bug "Ending Reunion Kos Service Count", `docs/status/s3k-known-bugs.md`) on 2026-10-17
and promoted at base `00f8ab78a`. Native recordings are comparison evidence only; nothing here supplies runtime
costs, gameplay state, readiness or timing authority, and nothing is registered with the engine.

## Files

| File | Role |
|---|---|
| `ledger_closure.py` | The checker. Decoder and GPGX costs come from [`../m68k-closure`](../m68k-closure/README.md) (`--tools` overrides). |
| `test_ledger_closure.py` | 33 behavioural tests on tiny hand-constructed NOP/BEQ/Scc/DBcc recordings with pinned GPGX costs. Expected values are written out, not computed by the checker. |
| `DeclaredIngressStateProducerV1.cs` | Diagnostic probe source that produces the declared-setup ingress savestate the runner replays. See its header for build target, arguments and placement. |

## What the checker proves

For each complete VInt-to-VInt interval:

1. **Conservation.** Every master-clock cycle between the two acceptance instants is attributed, exactly:
   `close - open == sum(slot MCLK) + carry + 14*refresh + sum(classified kinds) - dma_rewind`.
2. **Instruction-cost admissibility.** Every executed PC's native time is admissible under the ROM decoder's GPGX
   `051d430d` costs, in one of four classes:
   - `exact`: a fixed cost `c` with `mclk == 7*count*c`;
   - `mix-admissible`: a conditional branch or DBcc whose total equals *some* mix of taken and fall-through
     outcomes. That is consistency only; the checker does not solve control flow from a mix;
   - `bounded`: an operand-dependent cost inside `[7*count*lo, 7*count*hi]` on the GPGX two-cycle lattice;
   - `mix-bounded`: both.

   RAM PCs are accepted only for the cited `$FFFFF0` `jmp (abs).l` on the verified S3K ROM.

Before selecting intervals it validates the whole recording: declared output hashes and required files, ROM and
provenance consistency (`declared_setup` ingress only), counts, sequences, unique slots, PC/context bounds, interval
joins, carries and straddles, acceptance stamps, kinds and costs, and frame labels. A requested `--labels` range with
absent labels is INCOMPLETE.

Exit codes: 0 PASS, 1 FAIL, 2 INCOMPLETE, 3 usage or identity error.

`--perturb KIND:SEQ[:PC[:CONTEXT]]` applies one in-memory negative control after validation: `count`, `mclk` (a
fixed-cost slot by default), `kind`, `refresh`, and `conserve_dma`/`conserve_z80_bus`. The last two move 7 MCLK from a
classified stall into an instruction, so conservation still closes and the cost check must catch it. A named branch
`count` control can stay admissible, because aggregate branch data cannot prove the path; one test demonstrates that.

## What it does not prove

- That the host used the declared region, or that the main/IRQ attribution is right.
- Anything about straddle, inline IRQ-delay or HInt paths in live data: the recordings contain none of them
  (only level-6 VInts at instruction boundaries), so only the synthetic tests cover them.
- Whole-game timing, or any prediction from engine state.

## Usage

```bash
python3 tools/timing/native-ledger/ledger_closure.py <runner output directory> \
  --rom "<absolute path to the S3&K locked-on ROM>" \
  --gpgx-git <Genesis-Plus-GX repository containing commit 051d430d3d1b54625f9900c8f152d7f232e06daf> \
  [--labels LO-HI] [--perturb KIND:SEQ[:PC[:CONTEXT]]]
GPGX_GIT=<same repository> python3 -m unittest discover -s tools/timing/native-ledger -p 'test_*.py'
```

The tests need `GPGX_GIT`; without it `setUpClass` errors rather than skipping. No ROM or capture is needed.

## Evidence at promotion

Run on the promoted tree against the retained Tails/+2 declared-setup recordings (labels 4392–4435):

| Recording (core) | Intervals | Residual | exact / mix-admissible / bounded / mix-bounded PC rows | Status |
|---|---|---|---|---|
| NTSC first proof (`f584d70c…`) | 44 | 0 in every interval | 38,799 / 8,026 / 92 / 151 | PASS, exit 0 |
| NTSC r2 core (`98be79a6…`) | 44 | 0 | 38,799 / 8,026 / 92 / 151 | PASS, exit 0 |
| PAL r2 core (`98be79a6…`) | 44 | 0 | 38,856 / 8,071 / 92 / 151 | PASS, exit 0 |

On the first complete NTSC r2 interval, the `count`, `mclk`, `kind`, `refresh`, `conserve_dma` and
`conserve_z80_bus` controls each return FAIL, exit 1. The design record holds the native commits, the earlier
uncorrected checker and why its "exact" label for branch mixes was rejected.
