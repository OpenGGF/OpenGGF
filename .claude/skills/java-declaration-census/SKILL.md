---
name: java-declaration-census
description: Inventory written Java source declarations or a bounded ordinary JUnit Test subset at an immutable Git commit, optionally checking an explicitly pinned FINAL file. Use for source handoff inventories and stale simple whole-class count checks, not compilation, ABI or JUnit discovery/execution claims.
---

Use the repository's [census tool](../../../tools/soniccd/java_declaration_census.py) to count record components, written constructors/methods and overload signatures. This is read-only convenience evidence, with source lines and Git blob/SHA256 provenance. It establishes no owner, phase, authentication, compilation, test result or binary compatibility.

Run from the repository with Python 3 and the unchanged existing sibling `tools/soniccd/enum_contract_audit.py` (39,145 bytes; SHA256 `1931593367393ea399fb01d8f6a474756d0e1b61056dfd31c9e3ef29463516e0`). No third-party package, Java, Maven, javap, runtime or network is needed. The tool verifies that dependency pin before using its shared lexer/pairs. A changed dependency requires explicit review and renewed safety testing; do not bypass the pin.

Supply a canonical absolute local Git metadata directory, full lowercase 40-hex commit and exact relative `.java` path. Never substitute a moving ref or working-tree file. The tool accepts an explicit Git directory for linked worktrees too; resolve it separately if authorized, without inferring the destination from the source package. It reads only the selected immutable blob, without scanning the checkout.

```bash
python3 -B tools/soniccd/java_declaration_census.py \
  --git-dir "$OPENGGF_ROOT/.git" \
  --revision f4a968965ef55f47ebff8b3c7aa0827c8ef7aad4 \
  --source src/main/java/com/openggf/game/soniccd/objects/CdLogShadow.java
```

For comparison, append `--final /canonical/explicit/FINAL.java --final-sha256 SUPPLIED_SHA256 --final-bytes SUPPLIED_BYTES`. Both pins must come from the external handoff or another explicitly trusted input authority. Do not calculate fresh pins from an unknown FINAL merely to make verification pass, invent pins, infer a source/package/destination mapping, or apply migrations. Missing pins require obtaining them before comparison; baseline inventory can proceed independently. FINAL must be a regular file with no symlink, hardlink alias or traversal in its path. The tool writes JSON only to stdout; save it only to an authorized destination.

Read `status`, exit code, `limitations` and any diff ambiguities before using the rows:

- `0`: bounded inventory complete, or comparison observes no declaration changes.
- `1`: bounded comparison complete with added/removed/changed declarations; this is an observed difference, not a tool failure.
- `2`: invalid input, path, pin or local Git read. Argument syntax errors use argparse stderr.
- `3`: incomplete grammar/scope, exhausted bound, observed input change or uncomparable redacted declaration literals. Never convert this to a clean empty census.

`baseline` and optional `final` each contain `provenance` and ordered `declarations`. Each row has enclosing `owner` (package for top-level types), `name`, `kind`, written `modifiers`, `arity`, `parameters`, token `signature`, `type` and the name token's `line`. A type row has no callable arity. Record components are separate rows. Compact constructors have `arity: null`, no written parameters, and a contextual `record_component_count`; do not manufacture a written canonical constructor or accessor from a record header. Implicit interface modifiers, inherited methods and compiler-generated members are absent.

Type/signature strings join lexical tokens with spaces (`String . . .` is varargs); they are not qualified, erased or resolved types. Literal contents become `<literal>` in the shared lexer. Across different file bytes, an otherwise equal declaration header containing a literal makes the comparison incomplete rather than claiming equality. Identical full bytes can prove that equality. Comments, line shifts and executable-body changes alone are outside the declaration diff. Overloads match exact lexical parameter types first, then a unique residual owner/kind/name pair; groups with several unmatched overloads remain added/removed with `ambiguous_groups`. Do not infer overload-call binding from that pairing.

Supported scope is bounded ordinary classes/interfaces/enums/records, nested member types, generic/annotated/array/varargs declarations and written compact/explicit constructors. Annotation types, receivers, local/anonymous types and unrecognized declaration grammar stop as incomplete. Fields, enum constants, initializer blocks and executable bodies are opaque and excluded; their Java validity is not checked. This is a declaration recognizer using the existing lexer, not a Java 25 grammar implementation.

Defaults/hard ceilings: one baseline plus at most one FINAL file, 1 MiB per file, 2 MiB combined, 100,000 tokens per file, 5,000 rows per file, 4,096 header/context tokens, 32 member/generic levels (shared lexer delimiter cap 128), and 10 seconds per Git subprocess. `--max-*` options only lower limits. Git argv shapes are fixed, with no shell, replacement objects, lazy fetch or allowed network protocols. Selected paths/bytes are checked twice; the observations are non-atomic, so transient changes restored between observations can escape detection. Unselected files/index/HEAD are not covered. Retain these limits when reporting results.

For a **static preflight of a simple whole-class test count**, select its exact
qualified class explicitly. Run this before a costly Maven invocation when a
handoff declares an ordinary whole-class expected count:

```bash
python3 -B tools/soniccd/java_declaration_census.py \
  --git-dir "$OPENGGF_ROOT/.git" \
  --revision 59889292592609ca89f96d6c39c7c093ef2c11fb \
  --source src/test/java/com/openggf/game/soniccd/level/TestSonicCdPastLevelRetail.java \
  --test-inventory com.openggf.game.soniccd.level.TestSonicCdPastLevelRetail \
  --expected-count 5
```

The expected count remains an explicit caller assertion; review names and source
identity before changing a stale handoff. Never revise a count merely to make a
check green. The same optional FINAL flags and independent provenance/pin contract
apply. `--expected-count` compares FINAL if present, otherwise baseline; output
names that side. It is invalid outside test-inventory mode. In this mode exit0
means a complete bounded static proposal (matching the expected count if given),
exit1 means a complete proposal disagrees with the explicit count, exit2 rejects
input, and exit3 is incomplete. No declaration diff is computed in this mode.

Each side has `provenance` and `test_inventory`, with `selected_class`, ordered
`candidate_methods` (`owner`, `name`, name-token `line`), `complete`,
`incomplete_reasons` and `proposed_count`. An incomplete side has a null proposal;
its visible candidate names are partial source evidence. Expected comparison is
null when that selected side is incomplete. Either incomplete side makes the
whole request exit3, even if the other side is complete. Do not turn that result
into zero or guess an invocation total.

The supported subset is deliberately small: one ordinary top-level class, no
other/member types, no generics or written `extends`/`implements`/`permits`, an
implicit or one nonprivate zero-argument constructor, and ordinary nonprivate,
nonstatic, zero-argument `void @Test` methods with bodies. Only direct exact
`org.junit.jupiter.api` imports or fully qualified literal `Test`, `BeforeAll`,
`AfterAll`, `BeforeEach` and `AfterEach` annotations are trusted; argument-free
parentheses are accepted. Lifecycle shapes must be ordinary zero-argument void
methods with bodies; BeforeAll/AfterAll must be static. The tool does not evaluate
annotation arguments or run Java. Unresolved/ambiguous/wildcard type imports,
unknown/composed/extension/conditional/disabled annotations, parameterized,
repeated, dynamic, nested or inherited forms, unsupported annotated positions,
invalid recognized method shapes and no literal test candidates are incomplete.
The existing lexer/declaration grammar and byte/token/depth bounds still apply.

A qualified annotation spelling is not automatically a trusted package name.
An explicitly imported or declared type named `org`, or an unknown static
on-demand import that could contribute it, leaves `@org...` binding incomplete.
The known JUnit Assertions static wildcard remains supported; a direct single
JUnit Test import keeps its separate unqualified binding. No classpath resolver
is added and unobserved external types remain part of the limitations below.

This is **not JUnit discovery or execution**. External configuration, filters,
automatic extensions, classpath, compilation, assumptions, lifecycle failures and
actual passes/skips are unobserved. `@BeforeAll` may abort a run even when the
static five-method proposal is complete. This hint neither supplies nor relaxes
`integration_run.py`'s strict expected executed-XML counts, source-bound run
validation or the owning task's required tests. It creates no report/XML pass
cache, receipt or output file; JSON goes only to stdout.

For tool changes, also run [the inventory safety tests](../../../tools/soniccd/test_java_test_inventory.py).
Use [the standard-library tests](../../../tools/soniccd/test_java_declaration_census.py) with `TMPDIR` set to owned scratch and `PYTHONDONTWRITEBYTECODE=1`; synthetic Git fixtures are disposable. Run the existing enum safety suite if the shared dependency changes. Keep this skill byte-identical to `.claude/skills/java-declaration-census/SKILL.md`.
