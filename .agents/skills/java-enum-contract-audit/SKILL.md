---
name: "java-enum-contract-audit"
description: "Use when Java enum entries, manifests or subset selectors change and their direct consumers need bounded read-only inventory before costly validation. No Java compiler, symbol resolver or consumer rewriting."
---

# Java enum contract audit

Use the [auditor](../../../tools/soniccd/enum_contract_audit.py) after changing an
enum's entries, a manifest or a subset selector. Compare actual enum names/order
against an immutable pre-change commit, then inspect direct consumers before
expensive checks. The tool is generic despite its `tools/soniccd/` location.
It reads source and Git objects; it never imports packets, changes files, runs
Java/Maven, inspects jobs or contacts a service. No approval ceremony is added.

Run from the checkout containing the tool, choosing the audited worktree
explicitly. Supply a full lowercase 40-character commit and canonical relative
source/scan directories. Repeat `--scan-root` for distinct, non-overlapping roots;
include production and test consumers relevant to the actual owner.

```bash
python3 -B tools/soniccd/enum_contract_audit.py \
  --root "$AUDIT_WORKTREE" --base "$PRE_CHANGE_FULL_SHA" \
  --source src/main/java/example/Owner.java \
  --enum Table --type example.Owner.Table \
  --scan-root src/main/java/example --scan-root src/test/java/example --json
```

`--type` accepts the full package-qualified type or the exact lexical owner path
(`Owner.Table`). Source selection requires one matching declaration. Constants
retain their order; comments, literals, text blocks, constructor arguments and
anonymous constant bodies are excluded. `append_only_prefix` says whether every
old constant kept its ordinal; it does not prove semantic compatibility.

Resolve each finding against the actual owner and intended consumer contract:

- `stale_total_assertion`: a lexical `assertEquals(oldCount, Type.values().length)`
  candidate after the total changed, including reversed arguments, whitespace,
  parentheses and an optional third message argument. Correct new totals and
  symbolic/arithmetic expected counts are inventoried without evaluation. Only
  whole integer literals are compared; no JUnit overload resolution is claimed.
- `values_length`: direct total-count inventory, including non-assertion uses.
- `unbounded_stream_skip`: REVIEW a direct `Arrays.stream(Type.values()).skip(N)`
  chain without an explicit `limit`. A prefix, tail or full manifest may be
  intentional. `bounded_stream_skip` observes a syntactic limit in that chain,
  even if its value is symbolic; neither finding proves subset semantics.
- `values_foreach`: an exact immediate `for (var item : Type.values())` source,
  also allowing a plain dotted declaration type and optional `final`. This
  inventories the source; it infers no element binding, filter, loop coverage or
  iteration outcome. Annotated/wrapped sources remain incomplete.
- `values_index`: a direct `Type.values()[expression]`. `literal` is the whole
  integer when recognized; `index_expression` preserves bounded token text with
  comments/whitespace removed. Expressions, including nested calls/indexes,
  remain unevaluated. No index range, loop bound or complete manifest is proved.
  Empty/opaque indexes, statement separators and another immediate array-index
  tail remain incomplete; the tool does not validate Java expression grammar.
- `bounded_list_sublist`: exact `Arrays.asList(Type.values()).subList(start, end)`
  with two whole integer bounds satisfying `0 <= start <= end <= newCount`.
  `start_inclusive` and `end_exclusive` describe that half-open subset. Symbolic,
  arithmetic, invalid or indirect/nested bounds/tails remain incomplete. A fixed
  old-prefix upper bound stays fixed when the enum grows; it is not a stale total
  or an instruction to expand the subset.
- `unsupported_*` / `unresolved_type_reference`: incomplete interpretation. Inspect
  the source instead of treating the omitted consumer as covered. An observed
  unqualified zero-argument `values()` in the selected enum body is incomplete,
  not a resolved total. Other observed indirect `values()` uses, method references
  or static values imports are reported;
  undiscovered reflection/aliases remain outside the scan's proof.

Supported type spellings are the full qualified name, the exact owner path in
the same package, and spellings shortened by exact single-type imports (ordinary
or static nested-type imports). The enum source itself also admits its simple
enum name. Wildcard imports, local shadowing, inherited names and binding aliases
are not resolved. Stream wrappers are `java.util.Arrays.stream`, `Arrays.stream`
with its exact type import, or `stream` with its exact static import. List
wrappers similarly admit `java.util.Arrays.asList`, `Arrays.asList` with the
exact type import, or `asList` with its exact static import. The enum array must
be the entire immediate argument, and `subList` the immediate final selector.
Streams with generic/indirect tails are incomplete. For the three new direct
forms, conflicting exact imports and obvious same-name declarations anywhere
in the file conservatively produce incomplete output. This includes repeated owner
type names in the selected source; a simple name is not resolved across those scopes. These checks do not
resolve scope, inheritance or aliases; absence of a collision proves no binding. An unrelated `Other.Table` is not
made a target merely because its literal total looks familiar.

Version-1 JSON includes enum names/counts/order, base commit/blob/SHA256, observed
HEAD before/after, computed current blob SHA-1, source SHA256 before/after, and
hashes of the selected Java file set. Findings carry path, line, kind, literal,
canonical type and file SHA256. The three direct kinds also carry
`source_shape`; index and subset rows add the expression or bounds described
above, and subset rows retain the wrapper spelling. Text output includes those
details too. Output order is deterministic. Reads are two
non-atomic observations of the selected Java files plus enum source and HEAD;
they exclude unselected files and the index. They can detect observed changes,
not edits restored between observations. The enum source is compared but its
consumers are scanned only if it lies under a selected scan root.

Exit codes: `0` stable bounded inventory with no migration/review candidate;
`1` stale total, unbounded slice or broken old ordinal prefix; `2` unsafe/invalid
input; `3` incomplete grammar, observed change or exhausted budget. Inspect the
whole report even on nonzero exits; partial findings are retained. Never
interpret `0`, `stable_bounded_read` or zero findings as compiler/test success or
complete consumer proof. The tool is not a review replacement or pass cache.

Defaults bound 1,000 Java files, 20,000 directory entries, 1 MiB/file, 16 MiB per
current-source pass, 1,000 findings, 200,000 tokens/file and 4,096 context tokens.
The baseline has its own per-file cap. Names, nesting and scan-root counts also
have fixed bounds. `--help` lists adjustable caps and hard maxima. Oversized
inputs fail visibly; do not silently narrow scope to obtain a clean result.
Symlink paths/entries, traversal and ref expressions are rejected. UTF-8 with
ASCII identifiers is supported; Java Unicode-escape preprocessing is incomplete.

Make essential migrations only after checking the contract: a total may grow
while an old-prefix slice needs a fixed bound. Do not change enum/runtime behavior
to make the audit green. Continue the owning task's relevant validation; this
skill adds no engine run solely to test Python. Validate the auditor itself with
its [temporary-fixture tests](../../../tools/soniccd/test_enum_contract_audit.py):

```bash
python3 -B -m unittest discover -s tools/soniccd -p test_enum_contract_audit.py -v
```
