---
name: scddisasm-guide
description: Use when locating Sonic CD disassembly routines, labels, per-stage source files or program sources in docs/scddisasm.
---

# Sonic CD disassembly lookup

`docs/scddisasm` is an optional research submodule (`git submodule update --init
docs/scddisasm`) pinned at `1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`. Paths
below are relative to `docs/scddisasm/src`. Runtime bytes come only from the
user's USA disc image (`-Dsoniccd.media.root`); never read the disassembly at
runtime and never transcribe its tables.

## Where things are

- **One stage = one program.** `Level/<Zone>/Act N <Period>.asm` is a stage's
  entry file (period `Present`, `Past`, `Good Future`, `Bad Future`; act 3 has
  only the futures). It includes that stage's own copy of the level engine.
- **Two dialects of the same engine.** Some stages include `Level/*.asm` and
  `Level/_Objects/`; others include `common/*.asm`, `objects/*.asm` and
  `rN/*` (N = 1, 3-8: the zone's round). After normalisation the two are the
  same code, so a dialect is never a behaviour difference. Read the stage's
  entry file to see which one it includes, then cite that file.
- **Per-stage data:** object index (`rN/object_index_*.asm` or the zone's
  `Object Index (...).asm`), object layouts, graphics lists (PLC cues),
  palettes, scroll and palette-cycle routines, all beside the entry file.
- **Shared definitions:** `Level/_Definitions.inc` (slot fields and level
  RAM), `_Include/*.inc` (sub-CPU, BIOS calls, sound IDs).
- **Programs:** `CD Initial Program/IPX.asm` (the game flow), `CD System
  Program/SP.asm` and `SPX.asm` (sub-CPU file engine, CD audio, PCM driver
  commands), `Special Stage/`, `Time Attack/`, `Backup RAM/`, `FMV/`,
  `DA Garden/`, `Thank You/`, `Comin Soon/`, `Sound Drivers/`.

## Lookup

```bash
rg -n '^<Label>:' docs/scddisasm/src
rg -n 'include' 'docs/scddisasm/src/Level/Palmtree Panic/Act 1 Present.asm'
python3 -I tools/soniccd/variant_census.py docs/scddisasm <output-dir>   # per-stage engine differences
```

Generated tables come from the disc and the disassembly by script:
`tools/soniccd/stage_catalogue.py`, `object_catalogue.py --owner <z>`,
`program_catalogue.py` and the per-zone catalogue scripts beside them.

## Citing

Name the label beside the lines, relative to `src/`:
`ObjSonic_ChkBoredom (Level/_Objects/Sonic/Main.asm:11-40)`. The label must be
at or before the range (within 250 lines); a local `.Label` does not count,
so name its parent. `python3 tools/disasm/disasm_citations.py check <paths>`
verifies against the pin; the pre-commit hook rejects new stale citations.
Model `FixBugs = 0`.

For object and boss work use `scd-implement-object` and `scd-implement-boss`.
