# Disassembly line citations — audit (2026-10-09)

**Question.** Engine comments, tests and docs cite the disassembly submodules by line
number. When a disassembly gains comments or labels, those numbers drift. How many are
already wrong, against which revision were they written, and what keeps them correct?

**Scope.** develop `8668a9012`; every tracked `.java`, `.md`, `.py`, `.lua`, `.ps1`, `.sh`
and `.json` under `src/main/java`, `src/test/java`, `docs`, `.agents`, `.claude` and
`tools` (disassembly trees excluded). Tool: [`tools/disasm/disasm_citations.py`](../../../tools/disasm/disasm_citations.py),
branch `feature/ai-disasm-citation-check`.

## Disassembly revisions

| Submodule | Pinned gitlink | Main checkout | Relation |
|---|---|---|---|
| s1disasm (sonicretro) | `28872a4` | `f6ece65` (`AS`) | checkout 10 commits behind the pin |
| s2disasm (sonicretro) | `34fc7f1` | `380f37a` (`master`) | checkout 1 commit ahead |
| skdisasm (`.gitmodules`: sonicretro; local remote: raiscan fork) | `044fa46` | `1a454a0` (`feature/ai-object-pointer-annotations`) | 158 ahead, 13 behind |

No checkout matches its pin. The skdisasm checkout is the fork's label-annotation branch,
which renames `loc_XXXXX` to descriptive names (usually keeping `; loc_XXXXX` on the label
line) and so moves lines.

## Method

The checker parses `file.asm:N[-M][, N-M]`, `file.asm line(s) N-M`, S1 split-file names
with spaces and commas (`_incObj/1A, 53 Collapsing Ledges and Floors.asm:172`), and the
bare `:N-M` form, which inherits the last explicit file in the same source file (Markdown:
same section). It reads disassembly files from git objects, so any revision can be the
reference. A citation is judged by the disassembly label named just before it:

| Status | Meaning |
|---|---|
| ok | the named label is in the cited range or at most 3 lines before it |
| near | the range lies within 250 lines after the label (plausible; a shift inside the routine is not detectable) |
| stale | the named label exists elsewhere |
| unverifiable | lines exist, but no label is named beside the citation (or a bare citation does not fit its inherited file) |
| out-of-range / missing-file | hard errors |
| generated / unresolved-file | cites a `.lst` build listing / names no single disassembly file |

## Results

| Game | Citations | ok + near at pin | stale at pin | ok + near at checkout | stale at checkout | Hard errors |
|---|---|---|---|---|---|---|
| S1 | 3,512 | 800 | 252 | 803 | 249 | 217 (188 missing files) |
| S2 | 8,773 | 834 | 1,936 | 834 | 1,936 | 6 |
| S3K | 13,281 | 582 | 3,807 | 3,010 | 1,371 | 8 |

Plus 49 `.lst` citations and 206 unresolvable file names. About two thirds of all
citations name no label and cannot be judged from their text.

- **S3K citations follow the fork branch, not the pin.** Against the pin, 87% of the
  labelled S3K citations are stale; against the checkout, 31%.
- **No single revision explains any game.** Scanning 150 revisions of each main file,
  the best S3K fit is a July 2025 commit (`10f3d16e5`, 3,377 labelled citations fit)
  rather than the checkout (2,982); S2's best fits are May–June 2026 upstream commits,
  where about half of its labelled citations fit. Citations were written against whatever
  each agent had checked out over a year, so a one-shot remap from one revision cannot
  repair the backlog.
- **S1 missing files are upstream renames** (June 2026): `sub PlatformObject.asm` →
  `sub PlatformObject & SlopeObject.asm`, `sub ReactToItem.asm` → `Sonic ReactToItem.asm`,
  and similar; `check` names the new file.
- Real malformed citations surfaced, e.g. an S2 range whose end is written in hex
  (`Sonic2MTZBossInstance`), a ChopChop range citing the `obj91.asm` mappings file
  although the lines belong to `s2.asm`, and a doubled `docs/s1disasm/s1disasm/` prefix.

## Remap validation

`remap` maps lines through `git diff -U0` and refuses any range that overlaps an edited
line or has lines inserted inside it. Carrying every citation that is `ok` at the checkout
to the pinned revision, then re-judging it by its label there:

| Submodule | Carried, still ok at pin | Refused (touches an edit) | Carried, wrong |
|---|---|---|---|
| skdisasm `1a454a0` → `044fa46` | 2,129 | 183 | 0 |
| s1disasm `f6ece65` → `28872a4` | 531 | 3 | 0 |
| s2disasm `380f37a` → `34fc7f1` | 532 | 0 | 0 |

Unit tests (`python3 -m unittest discover -s tools/disasm -p 'test_*.py'`) build throwaway
repositories and were each seen to fail before the fix they pin: endpoint-only range
mapping carried a range spanning an edit; the 250-line window let a shifted citation pass
(hence the separate `near` status); a pathspec-limited rename lookup hid renames; dropping
an S1 number prefix resolved `11 Bridge.asm` to `_maps/Bridge.asm`.

## Decisions and rejected approaches

- **Reference = pinned gitlink** by default (reproducible after `git submodule update`);
  `--rev checkout` and `--rev NAME=SHA` override it. `check` warns when a checkout differs
  from the reference.
- **Carry by diff, verify by label.** Rejected: a per-citation content-fingerprint
  lockfile (about 24,000 generated entries; fails the same way on edited lines).
- **No automatic repair yet.** History search for an "authoring revision" passes too
  easily for short or label-less ranges. A future repair needs a label inside the range at
  the candidate revision, a candidate no later than the citing commit (blame), a remap
  that crosses no edit, and the label still inside the remapped range; it must be shown to
  recover deliberately shifted citations before it is trusted.
- **No hooks yet.** The valuable check is "a commit that moves a disassembly pin also
  remapped citations"; it awaits approval, as does any mass rewrite.
- **Model review of citations was tried first** (OpenRouter, 56 sampled S3K badnik
  citations, $0.91): one real bug class (`V_int_run_count+3` read as arithmetic, three
  objects), five stale line numbers, two trivial comment issues and six false positives.
  Line drift is a mechanical problem; it went to this tool. Design review: Fable,
  2026-10-09.

## Open decision

Make the pins true before enforcing `check` strictly. S2 and S1 can stay on their
upstream pins. skdisasm citations follow the fork's annotation branch, which is not on
the `.gitmodules` URL and lacks 13 pinned upstream commits: either merge upstream into
the fork branch, point `.gitmodules` at the fork and pin the merge (then
`remap skdisasm 1a454a0 <merge> --write`), or keep the upstream pin and remap
`1a454a0 → 044fa46` (2,129 carried, 183 to review).
