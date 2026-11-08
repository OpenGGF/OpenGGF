---
name: soniccd-junit-selection-source-preflight
description: Check explicit frozen version-1 handoff JUnit roots, literal methods and nested source scopes against a saved Git commit plus pinned incoming packet bytes before costly queue requests. Source only; no compilation, discovery, execution or count authority.
---

Use [the standalone preflight](../../../tools/soniccd/junit_selection_source_preflight.py)
before requesting expensive validation when a frozen handoff selects tests. It
addresses wrong package/root paths and omitted nested report declarations,
including mixed literal method/whole-root selections. It neither runs nor changes
the integration runner. Do not infer source, expected counts or owners from prior
results, targets, queues or moving checkouts.

```bash
python3 -B tools/soniccd/junit_selection_source_preflight.py \
  --git-dir /canonical/repository/.git \
  --commit FULL_LOWERCASE_40_HEX_SAVED_COMMIT \
  --handoff /canonical/external/handoff.json
```

Supply all three explicitly. Use one exact saved commit, not HEAD, a branch or a
revision expression. The external handoff must be LF-terminated UTF-8 version-1
JSON with the existing exact `integration_handoff.py` schema. Duplicate keys,
unknown fields, nonfinite numbers and invalid declarations are rejected. A
handoff may be mode 0444 or 0644; packet sources must be pinned regular mode 0444
UTF-8/LF files. All named paths must be canonical; selected external inputs are
opened by no-follow descriptors with hardlink rejection. No source is rewritten.

`tests.classes` alone selects roots, preserving literal qualified names. The
supported path is exactly `src/test/java/<qualified/name>.java`. A packet entry
with that exact destination selects its pinned source bytes, even for a new test
absent at Git; otherwise only that saved Git blob is read. An overlay failure
never falls back to Git. No alternate package, directory, repository or classpath
scan guesses a correction. Missing conventional source is **INCOMPLETE** because
generated/custom sources remain unresolved; it is never an absence certificate.
Nested `$` root selectors are outside this top-level subset.

Inspect `status`, `findings`, `roots`, `topology` and `limitations`:

- Exit 0 / `BOUNDED_SOURCE_ONLY`: no finding within the supported source subset.
- Exit 3 / `INCOMPLETE`: a root/package/method/group mismatch, unresolved grammar
  or binding, observed change or exhausted budget. Partial inventories can remain.
- Exit 2 / `INVALID`: unsafe/malformed input, pin mismatch or local Git rejection.
  Argument syntax errors use argparse stderr. No result authorizes a queue run.

Each root lists source provenance (Git commit/blob/SHA256 or external incoming
path/bytes/mode/SHA256), observed package, literal requested methods and member
class scopes with source lines and binary `$` names. `resolved_nested_test`
distinguishes a resolved `@Nested` from an ordinary/unknown member class. Unresolved
member classes remain visible with incomplete reasons; they are not asserted to
be JUnit reports. Candidate method names are written ordinary `@Test` candidates,
not invocations or an expected count. Helpers/literals are never counted as tests.
Properties, API prose and source bodies are not copied into output.

Supported complete scope is ordinary classes with direct literal JUnit `Test`,
`Nested`, lifecycle and `DisplayName` annotations. Exact JUnit imports or fully
qualified spellings are required; a lone JUnit type wildcard can bind annotations
when no competing wildcard/type shadows it. Unknown/composed/conditional/dynamic
annotations, parameterized/repeated factories, inheritance, generics, local or
anonymous classes, additional top-level classes, ambiguous annotation bindings
and unsupported annotated positions/shapes stay incomplete. Executable bodies
are opaque to the unchanged census recognizer; this is not a Java grammar checker.
Comments, strings, characters, text blocks and annotation argument literals do
not create scopes or packages. Java Unicode preprocessing is unsupported.

For nested scopes, require an explicit finite `tests.report_groups` entry whose
suite keys exactly match the observed class scopes, including ancestors. The
existing pinned schema validates group counts, owners and root totals. The tool
**never derives** per-report counts or owner lists from method counts. Preserve
the caller's literal counts/owners/case labels; obtain corrected declarations
from their authority. Extra invented suites also leave the preflight incomplete.
A nested helper without resolved `@Nested` cannot become clean merely by listing
it in groups. Explicit counts are never independently certified.

`topology` distinguishes `methodless_whole_roots`, `literal_methods_only` and
`mixed_literal_methods_and_whole_roots`. In the mixed case, Surefire global method
filtering can affect a whole-root selection. Neither mixed nor methodless source
evidence certifies runtime nested aggregation, actual executed counts or fresh
nested XML. The integration runner's strict source-bound executed evidence stays
necessary. Do not use this output as a receipt, cache, pass or approval.

Bounds: 50 roots, the existing 50-suite/100-literal-method schema bounds, 4 MiB
handoff, JSON depth 24 and 50,000 input/output nodes, 1 MiB/source, 8 MiB selected
source bytes, 100,000 tokens/file, 4,096 context tokens, 5,000 declarations/file,
32 declaration nesting levels, 128 lexer delimiter levels, 1 MiB stdout and 10
seconds per Git subprocess. `--max-*` flags only lower the listed adjustable
caps. Budget exhaustion never becomes a clean empty result. The program creates
no artifacts; only stdout JSON is emitted. Save it only in an authorized location.

Reads and rechecks are non-atomic. Selected files, tool dependencies and exact
Git blobs (including observed missing paths) are checked again. Transient changes
restored between observations may escape detection. Unselected packet sources
and handoff dependencies are schema-only and are never followed or certified;
matching repeated input pins are allowed, conflicting roles/pins are rejected.
Trusted local Git metadata is required. Git argv are fixed and shell-free, with
hooks, text conversion, replacement objects, lazy fetch and protocols disabled.
No moving worktree source, index, build/target, result, process or queue is read.

The reviewed sibling dependencies are executed only after exact size/SHA256
checks; candidate Git/packet Python is never imported. Changed dependency bytes
require review and renewed tests, not a bypass or fallback pin:

| Dependency | Bytes | SHA256 |
| --- | ---: | --- |
| `enum_contract_audit.py` | 47279 | `bc4d7daf1689216391efd423b4628c728a1f0b63395e89398b09930f686a770d` |
| `java_declaration_census.py` | 65795 | `304b6314407adaa114ae06f17e9e0c23da93f89110e454a1c681dfe51000a625` |
| `integration_packet.py` | 50642 | `418fcc6bc520687a4670d545713e1a4096983ea7790ad37c4a535bc52de088b3` |
| `integration_handoff.py` | 17292 | `844ec5be26c1260085db5be61db5bba322a2a933aeecc2c386191364a2dece65` |

Run focused tooling checks with owned `TMPDIR` and bytecode disabled:

```bash
TMPDIR=/owned/scratch PYTHONDONTWRITEBYTECODE=1 python3 -B -m unittest \
  discover -s tools/soniccd -p test_junit_selection_source_preflight.py -v
```

Also run the unchanged enum, census, census-lexer-dependency, ordinary-test-inventory
and integration-handoff Python suites when reviewing dependency reuse. These are
lightweight tooling tests; no Java/Maven/media/native/GL run is needed. Keep the
Claude skill mirror generated with `python3 -B tools/git/sync_skills.py`.
