---
name: disasm-storage-layout
description: Query literal native storage declarations in a pinned source slice to compare symbol ranges with caller-supplied clear intervals. Use for field widths, zero-count aliases and clear coverage; this is source arithmetic, not native execution or parity.
---

# Bounded disassembly storage layout

Use `python3 -B tools/disasm/disasm_storage_layout.py --help` in the checkout
containing the tool. First inspect the source and choose an explicit finite
one-based line slice and a justified initial counter. Supply an origin label,
24- or 32-bit address space and at least one named half-open clear interval.
The origin and intervals are caller-supplied evidence, never inferred writes.

Read immutable Git source without populating a checkout:

```bash
python3 -B tools/disasm/disasm_storage_layout.py \
  --git-dir "$NATIVE_GIT_DIR" \
  --rev 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff \
  --path 'src/Level/_Definitions.inc' \
  --start-line 237 --end-line 341 \
  --base 0xFFF700 --origin-name caller-cameraX-origin --address-bits 24 \
  --clear camera-clear:0xFFF700:0xFFF800
```

That example selects the literal declarations starting at `cameraX`. It
assumes the caller has justified `cameraX = 0xFFF700`; the tool does not derive
that address from earlier `WORKRAM` expressions or mask a 32-bit origin to 24
bits. A `complete` result applies only to the selected slice and supplied origin.
Check the source owner and enclosing conditions separately before using it in
an engine change. Keep file/blob/line provenance with any cited result.

Alternatively use `--git-dir ... --blob <full-lowercase-blob-ID>` (no `--path`)
or `--file /absolute/source.inc --sha256 <lowercase-SHA256>`. Git revision
mode requires a full lowercase commit ID and one exact literal relative path;
moving refs, revision expressions, symlinks and gitlinks are rejected. Blob
mode has no verified repository path. File mode binds the exact bytes to the
caller-supplied hash. Nothing is checked out, fetched, indexed or cached.

The accepted grammar is deliberately small:

- Optional global ASCII label, optional attached colon, `rs.b`, `rs.w` or
  `rs.l`, and one unsigned decimal, `$hex` or `0xhex` count. Anonymous padding
  is retained. Widths are 1, 2 and 4 bytes per element, with no implicit alignment.
- Literal `rsset` and argument-free `rsreset` change the counter and are reported
  as events. Their literal values are preserved. Overflow stops the query;
  there is no sign extension, address truncation, masking or wrapping.
- Blank lines, semicolon comments and full-line `*` comments are accepted.
  ASCII space/tab delimit tokens; LF delimits lines. BOM/CRLF source bytes and
  the full selected-slice SHA256 remain available through provenance.

Every other active line stops at the first unsupported line, including `EQU`,
`ds`, expressions, symbolic counts, includes, macros, loops and conditionals.
Do not strip such lines or supply guessed zero widths to obtain success.
Select a smaller independently justified slice when that answers the question;
explain omitted context. Counter state from earlier lines is not evaluated.
Duplicate symbols fail conservatively without case distinctions.

Storage records retain declared element width/count and half-open byte ranges.
`contained`, `overlap` and `disjoint` describe each supplied clear interval.
Zero-count declarations are address markers covering **zero bytes**; their
`point_inside`/`point_outside` relation does not establish cleared storage.
Same-address names are preserved together; never infer an alias's byte extent
from a neighboring declaration or conflate similar field names across files.

JSON includes source revision/blob/path, full-file SHA256/bytes, selected-slice
line bounds/offset/SHA256/bytes, each declaration's original line, initial origin,
counter events and clear relations. On incomplete input, records describe only
the accepted prefix; `unsupported` identifies the first unhandled line, reason
and exact raw-line SHA256. `processed_through_line` excludes that line.

Exit `0` means the selected slice was completely parsed; `1` means incomplete
with an explicit prefix; `2` means invalid/unavailable source or report bound.
Hard bounds: 1 MiB source and report, 1,000 selected lines, 4,096 bytes/line,
16 clear intervals and 30 seconds/Git command. `--max-records` defaults to 256,
allows 1–1,024 and stops incomplete at the first excess declaration. Git metadata
is checked against 64 KiB; temporary spools are removed on command completion.
No result authenticates media, proves an actual native write, executes a CPU,
or establishes native parity. Runtime integration remains with its existing owner.
