---
name: disasm-source-lookup
description: Use when locating exact routine labels, callers or bounded source evidence at an immutable disassembly commit, including an optional disassembly whose checkout is absent.
---

# Immutable disassembly source lookup

Use `python3 -B tools/disasm/disasm_citations.py lookup --help` from the
checkout containing the tool. Select an existing absolute Git directory, full
lowercase commit ID and literal repository-relative tree root explicitly.
The Git directory can belong to any game's disassembly, including an unpopulated
submodule. No initialization, fetch, checkout or moving-ref lookup is needed.

For Sonic CD source pin `1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`, supply the
actual existing object repository through `NATIVE_GIT_DIR`:

```bash
python3 -B tools/disasm/disasm_citations.py lookup \
  --git-dir "$NATIVE_GIT_DIR" \
  --rev 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff \
  --root src --label ObjTunnelPath --context 2 --json
```

This intentionally reports two definitions: Palmtree Panic and Wacky Workbench.
Choose the owning source from the task's evidence, then narrow with the exact
repository-relative path:

```bash
python3 -B tools/disasm/disasm_citations.py lookup \
  --git-dir "$NATIVE_GIT_DIR" \
  --rev 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff \
  --root src --label ObjTunnelPath --context 2 --json \
  --path 'src/Level/Palmtree Panic/Objects/Tunnel Path/Main.asm'
```

For other games select their object repository, commit and tree root (`.` searches
the entire tree). Quote paths; `--path=-leading-option.asm` treats a leading dash
literally. Paths are never globbed or guessed. Use `--scope GlobalLabel` for a
`.local`/`@local` query, and `--path` when the same global occurs in several files.

Interpret the result as textual evidence. The parser recognizes colon labels
and exact case-sensitive code tokens, ignoring strings and semicolon/star-line
comments. Each line/kind appears once. Equates and colonless labels are outside
this seam. Local scope is the preceding global colon label in the same file,
identified by name **and line**; includes invalidate it until another global.
Include order, macros and conditional assembly are not expanded. Unknown scope
stays unresolved; duplicate globals or scoped locals stay ambiguous.

JSON binds every hit to commit/blob/path/one-based line, full-file SHA256/bytes,
and a context window with exact UTF-8 text, byte offset, SHA256/bytes and actual
line bounds. Additive version-1 field `context.base64` encodes the same raw window
bytes as padded standard RFC 4648 Base64 ASCII without line breaks; decoding it
reproduces `context.text.encode('utf-8')`, the declared byte length and SHA256.
BOM, CRLF and a missing final LF are preserved. `git_show.argv` and its
POSIX-quoted `shell` reproduce the blob; clear inherited `GIT_*` overrides and
disable lazy fetch when reproducing manually. The tool itself isolates these
overrides and disables replacements, textconv and lazy fetch.

Default bounds are 20 hits and three context lines each side; `--max-hits` allows
1–100 and `--context` 0–20. Hard limits: 256 candidate `.asm` files, 16 MiB/blob,
64 MiB candidate source, 1 MiB metadata/report, 30 seconds/Git command. Exceeding
a bound fails without partial hits; narrow the query. The lexical scan consumes
LF-delimited lines lazily and stops at the first excess hit, using the remaining
budget across files without building a whole-file line index. Git grep skips symlinks;
explicit symlink paths, gitlinks and non-UTF-8/binary candidate sources fail.
Exit `0`: one textual definition with known scope; `1`: ambiguous, unresolved,
references-only or no match; `2`: invalid input, unavailable source or bound.

This reads source only and writes no index, evidence archive or runtime assets.
It does not authenticate USA/JP/EU media, establish a ROM offset or prove native
parity. Keep packet import, execution and validation with their existing owners;
do not promote lookup excerpts into runtime inputs.
