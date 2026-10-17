# S3K instruction-cost ledgers (kos46 research prototypes)

Offline models that price the 68000 instruction time of individual S3&K ROM routines from the original ROM bytes.
They were written during the S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count",
`docs/status/s3k-known-bugs.md`), rounds R6–R17 on 2026-10-16/17, and promoted on 2026-10-17 at base `00f8ab78a`.
Method, evidence and limits are recorded in
[the ending lifecycle design](../../../docs/architecture/designs/2026-10-13-s3k-ending-lifecycle.md#kos46-instruction-ledgers-promoted-as-diagnostics-2026-10-17).

Every model here is a **diagnostic**. None is registered with the engine, none supplies a runtime cost, gameplay
state, readiness decision or hardware-timing input, and no Java consumes them. Native captures appear only as
comparison or test-oracle input.

## What they charge and what they do not

They charge instruction cycles only, from the pinned Genesis Plus GX `051d430d` cycle table (Motorola MC68000
section 8 times as a cross-check), decoded from ROM bytes by [`../m68k-closure`](../m68k-closure/README.md). Branches
and loop counts follow from explicit semantic inputs that an engine owner would hold; unsupported paths raise.

These terms are separate and are never included:
- interrupt acceptance (50–59 cycles in GPGX);
- bus refresh;
- Z80-to-68000 bus waits and VDP FIFO waits;
- DMA freeze.

## Layout

| Directory | Round | Closes (given inputs) | Tests |
|---|---|---|---|
| `kos/` | R6, R7 | `kos_service.py`: resumable `Process_Kos_Queue`..`Restore_Kos_Bookmark` (`$1C1A`–`$1D0A`), instruction by instruction, with a per-window budget. `invert_fingerprints.py`: resolves load-time manifest fingerprints to ROM Kosinski descriptors. | `test_kos_service.py` 14 |
| `vint/` | R9–R13 | VInt / VInt_8 / Set_Kos_Bookmark, the lag VInt, the composed VInt call tree ([README](vint/README.md)) | 11 |
| `vint/hud/` | R10 | UpdateHUD and its draw callees ([README](vint/hud/README.md)) | 10 |
| `vint/drawlevel/` | R11 | VInt_DrawLevel given plane-buffer descriptors ([README](vint/drawlevel/README.md)) | 8 |
| `vint/nem/` | R11–R12 | Process_Nem_Queue_2 and the Nemesis decoder ([README](vint/nem/README.md)) | 14 |
| `vint/hint/` | R13 | water H-int handlers and deferred Do_Updates ([README](vint/hint/README.md)) | 10 |
| `mainloop/` | R14 | LevelLoop skeleton, Process_Sprites dispatch, Kos module queue, allocator, OscillateNumDo ([README](mainloop/README.md)) | 15 |
| `render/` | R15 | Render_Sprites family and Draw_Sprite ([README](render/README.md)) | 10 |
| `deform/` | R16 | ApplyDeformation (both copies), AIZ1_Deform, AIZ1_ApplyDeformWater ([README](deform/README.md)) | 7 |
| `palette/` | R17 | palette fade family `$3AF0`–`$3DBE` from palette-owner inputs ([README](palette/README.md)) | 6 |
| `mini68k/` | R10–R17 | the shared fail-closed 68000 interpreter and its form extensions | 15 |

`mini68k/` holds the only copy of the interpreter. Each extension adds named forms and delegates everything else
down the chain, which raises `Unsupported`: `palette_forms` → `deform_forms` → `render_forms` → `nem_forms` →
`vdp_forms` → `mini68k`. R16 and R17 sources were frozen at their qualification and hash-bound on copy (see their
READMEs).
`vint/test_vint8_cost.py` is also the shared native-oracle loader that the other ledger tests import.

### `kos/`

- `kos_service.py` executes the shipped routine on a minimal state and charges each executed instruction its GPGX
  cost. `serve(state, budget)` stops at the first instruction boundary at or after the budget, bookmarks only
  inside `Process_Kos_Queue_Main`..`Done` (as `Set_Kos_Bookmark` does), resumes through `Restore_Kos_Bookmark`, and
  retires at most one job per window. Interruption outside the bookmark range raises. The budget is an input; the
  native tests derive it from native per-PC counts, so it is comparison data, not a measured cost.
- `invert_fingerprints.py ROM sonic3k.lst s3k-fast-v1.json s3k-v1.json` recomputes
  `S3kLoadTimeProfileGenerator.computeFingerprint` over every listing label containing `Kos` and every KosM module
  child, and writes a JSON map to stdout. On the `00f8ab78a` manifests it resolves all 184 fingerprints; the
  46 multi-valued ones are all KosM module children decompressed to `Kos_decomp_buffer` (`$FFFFD000`).

## Running the tests

```bash
export S3K_ROM=<absolute path to the S3&K locked-on ROM, SHA-1 cfbf98c3...>
export GPGX_GIT=<Genesis-Plus-GX repository containing commit 051d430d3d1b54625f9900c8f152d7f232e06daf>
export KOS46_NATIVE_ROOT=<directory holding the retained native-kos-budget-* ending captures>
export KOS46_NATIVE_EXTRA_ROOTS=<further capture trees, os.pathsep-separated; used only by test_native_absence>
for d in kos mini68k vint vint/hud vint/drawlevel vint/nem vint/hint mainloop render deform palette; do
  python3 -m unittest discover -s tools/timing/s3k-instruction-ledgers/$d -p 'test_*.py'
done
```

The expected counts are 106 source-model tests (vint 11, hud 10, drawlevel 8, nem 14, hint 10, mainloop 15, render 10,
deform 7, palette 6, mini68k 15) plus 14 Kos-service tests. `vint/nem/test_nem_decoder.py` takes about 45 s.

Most test classes **skip** without `S3K_ROM` and `GPGX_GIT`, and the native comparison cases skip without
`KOS46_NATIVE_ROOT`. A skip is not a pass: check that the summary line reads `OK` with no `skipped=` count.
`vint/hint`'s `test_native_absence` needs more than 30 `frame-pc-histogram.csv` files across the native roots and
fails rather than passing on fewer.

## Citations

ROM addresses and labels are the primary citation. Research passes 1–13 cited line numbers from the local
`docs/skdisasm` checkout `1a454a0`; the superproject pins `044fa46`, where the same lines sit later (for example
`Process_Kos_Queue` is line 2840 at `1a454a0` and 2872 at `044fa46`). ROM bytes and addresses are identical in both
revisions. On promotion every `sonic3k.asm`/`sonic3k.constants.asm` line number in these files was converted to
`044fa46` with a `diff`-based line map and checked line by line. Passes R14–R17 already cited `044fa46`. Read the
pinned source with `git -C docs/skdisasm show 044fa46:sonic3k.asm`.

The `.lst` listings that `attribute_pcs.py`, `listing.py` and `invert_fingerprints.py` read are build listings of
whichever checkout produced them, so their label names can differ from the pin. One rename matters here: the H-int
handler `HInt_6` at `1a454a0` is `HInt6` at `044fa46` (`$F92`); `vint/hint/` keeps the old spelling.

## Critical-path share (prioritisation only)

`mainloop/attribute_pcs.py` assigns each executed PC of the 42 retained ending buckets (six captures × LF 4400–4406)
to a routine, with `mainloop/closed-ranges.txt` (sha256 `c51169213b3eea4fbc4fae4c732194382af95e7944278bf0f104079a4579d404`)
marking the ranges priced through R17:

```bash
python3 tools/timing/s3k-instruction-ledgers/mainloop/attribute_pcs.py <sonic3k.lst> <six capture directories> \
  --closed tools/timing/s3k-instruction-ledgers/mainloop/closed-ranges.txt --s3-lst <s3.lst> --rom "$S3K_ROM"
```

On the promoted tree this reports 12,721 executed instructions per bucket, of which 11,365 fall in closed ranges and
1,356 remain open (R14 left 3,220 open, R15 2,384, R16 1,717). The closed share includes 5,201 `Process_Kos_Queue` instructions, the consumer whose service count
is the unknown, and 2,765 idle `Wait_VSync` tests, whose count is a timing-model output. These are instruction
counts, not cycles, and say nothing about hardware closure. The largest open routines are `Ending_ScreenEvent_Index`
(662), `sub_5B0A8` (94), `Obj_Ending` (43) and the plane-buffer producers (about 130 together, for example
`Setup_TileRowDraw` 37 and `sub_4EA4A` 32).

## Not promoted

- Research Java drafts and their source-level checkers (`*.DRAFT.java`, `check_java_draft.py`,
  `java_draft_check.py`, the Kos service draft patch). The Java owner owns production.
- Derived dumps that the tools regenerate (`path-terms.json`, `hud-terms.json`, `dma-start-prefixes.json`,
  `closed-form-check.txt`, `cost-table.txt`). Their values are in the READMEs.
- `context-inventory-next/build_inventory.py` and its draft inventory, a one-off built from scratch data that also
  needs per-sample native files that were not retained.
- `instruction-closure-next/interval_sources.py`, a one-off width listing. The closure report's
  `refined_pcs`/`refinement_notes` replace it, and it mislabels S3-half PCs.
- Unfinished work after R17: the plane-buffer producers (R18) and the ending-event routines.
