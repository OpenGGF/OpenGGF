---
name: java-project-import-preflight
description: Find bounded source-only missing-import, opt-in known-enum constant and direct-member import hints for explicitly selected project Java sources before costly compile validation, using immutable Git type provenance and an exact source blob or pinned FINAL bytes. Does not resolve a classpath or certify compilation.
---

Use [java_project_import_preflight.py](../../../tools/soniccd/java_project_import_preflight.py)
when a source handoff introduces or moves references to known project types,
especially nested spellings such as `CameraArtResidency.Region`. Select a small
reviewed type/enum list. This helper does not search a checkout or infer a destination.
The [source-bound packet handoff workflow](../soniccd-packet-integration/SKILL.md)
still owns source custody and the task's actual compile/test evidence.

Supply a canonical absolute local Git metadata directory, an exact lowercase
40-hex commit, and at least one `--known-type FQCN=exact/path.java` or
`--known-enum FQCN.Enum=exact/path.java` selection, or an explicit member-owner
selection below. The16-selection cap is shared.
Each known type must have a matching ordinary top-level declaration and package
in that exact regular Git blob. Filenames alone establish nothing. Use the owning
Git/disassembly lookup separately if the type/path is not already known.
Enum selections require one exact ordinary enum declaration, including its
package and nested owners, in that immutable blob. Constants come from the
unchanged shared enum token extractor, never a guessed filename or project scan.

```bash
python3 -B tools/soniccd/java_project_import_preflight.py \
  --git-dir "$CANONICAL_GIT_DIR" --revision "$IMMUTABLE_COMMIT" \
  --known-type 'com.openggf.game.soniccd.resources.CdR12aCameraArtResidency=src/main/java/com/openggf/game/soniccd/resources/CdR12aCameraArtResidency.java' \
  --final "$ABSOLUTE_FINAL_JAVA" \
  --final-sha256 "$INDEPENDENT_FINAL_SHA256" --final-bytes "$INDEPENDENT_FINAL_BYTES"
```

For an explicit enum check, substitute or add this option to the same pinned invocation:

```bash
--known-enum 'com.openggf.game.soniccd.level.CdR12bProfile.Asset=src/main/java/com/openggf/game/soniccd/level/CdR12bProfile.java'
```

This catches `CdR12bProfile.Asset.TOUCH_DIMENSIONS` and `.ENEMY_POINTS` as
absent from the selected enum constants. The real declarations belong to
`CdR12bPlayerAssets.Asset`; selecting that immutable enum accepts both names.
The caller chooses the intended source family. The helper does not migrate it.

For a committed consumer, replace the three FINAL options with
`--source exact/consumer/path.java`; that blob comes from the same revision.
FINAL pins must come from the agreed source handoff. The helper does not select
or authenticate the FINAL file for the caller. It reuses exact hash-pinned sibling
`java_declaration_census.py` safe readers and independently pinned
`enum_contract_audit.py` lexer bytes. A dependency mismatch rejects; do not bypass
pins or copy arbitrary Python helpers to obtain a result.

Read `uses`, `enum_uses`, `hints`, `incomplete_reasons` and provenance together.
Version1 adds `known_enums` with ordered constant names/source pins and
`enum_uses` with literal qualifier/member spelling, line, known-constant and
ambiguity observations. New hints use `possible_missing_enum_member` with
reason `not_in_selected_enum_constants`. Existing import hints remain unchanged. Root identifiers
matching selected simple type names are lexical candidates. Exact nonstatic
imports, package wildcard imports, same-package names and selected-file
ordinary top-level/member-type scopes can explain availability. Member scope is
limited to its lexical owner. Tokens after `.` are excluded as root candidates;
`Type.Member` produces one root candidate, while fully qualified spellings are
ignored by the import check rather than certified. Comments and literal contents produce no hits.

The enum check recognizes only explicitly spelled selected owner chains such as
`Type.Nested.MEMBER` or its complete package spelling. It preserves identifier/dot
tokens across whitespace/comments; it never joins split identifiers. Short
`Nested.MEMBER`, static-imported constants, class literals and method calls are
out of scope. Member/static-field lookup, inherited names and executable-body
grammar are not resolved. An absent declared enum constant is only a hint.
Known-name declarations/values/type parameters/lambda parameters, indirect
receivers, competing imports and unsupported containing scopes make the check incomplete;
blocked bindings emit no missing-member hint. Missing or uncertain wildcard
imports or unsupported scopes elsewhere in the file may retain explicitly
ambiguous hints. The actual large-consumer control remains incomplete after
fixing both names. Read those flags with the status.

Exit0 means no hints within supported selected lexical scope. Exit1 means one or
more possible missing imports or selected enum constants. Exit2 rejects invalid inputs, pins or reads.
Exit3 means incomplete/ambiguous/limited observation and can still contain hints.
For example, the original Log/Mosqui test yields hints at lines46/49; the
single-import correction removes those hints. Both remain incomplete because
static wildcard imports leave type/value binding unobserved. An explicit import
can be a useful correction without changing an incomplete result into compiler
evidence. No result is a compile pass, import-completeness check or reusable gate.

Selected-name value/type-parameter clues, local/anonymous/annotation type scopes,
inheritance/generic type headers, competing imports and multiple selected types
sharing a simple name are incomplete. Value, inherited and package shadowing are
not fully recognized; lexical hits can be false positives and lexical
availability can be false availability. Java executable-body grammar is not
validated. External libraries and unselected project types stay out of scope.
Unicode preprocessing and unsupported non-ASCII identifier grammar reject
incomplete; Unicode within ordinary comments/literals is merely redacted.

Bounds can only be lowered:16 total selected types/enums/member owners,1MiB/file,4MiB aggregate selected
source bytes,100000 aggregate tokens,4096-token contexts,32 brace levels,256 root
hits across both checks,30 seconds checked between bounded operations and inside scope scans, and
1MiB accepted result JSON. Paths have at most4096 characters/64 components.
The shared lexer also bounds delimiter nesting at128; its bounded lex pass and
regular-file reads are checked before/after rather than interrupted mid-pass.
The unchanged enum extractor also caps10000 constants per selected enum;
aggregate token/output caps still apply. Selected source bytes are read again for non-atomic correspondence, so actual
read volume includes those rechecks. JSON error responses use a separate small
fixed shape and can exceed a caller's lowered accepted-result byte cap.

All selected filesystem paths use no-follow descriptor walks; FINAL hardlinks,
symlinks and lexical aliases reject. Only fixed bounded read-only local Git
commands run, with protocols/lazy fetch disabled. There is no persistent index,
cache, Java/Maven execution, shell/network call, source mutation or output file.
Trusted Git metadata remains a prerequisite. Re-observations are non-atomic;
change-and-restore and later changes can escape them. Preserve existing task
permissions and validation ownership; this convenience check adds no approval
ceremony or authority to read a frozen worktree.

Package/import spelling is checked as alternating identifier/dot tokens, with a
final import wildcard only; split identifiers are incomplete. Type-header context
limits include the entire span across parenthesis/array jumps and the final brace.

Fully spelled enum qualifiers still check their first identifier against imports:
exact imported type/value bindings block the selected-member hint; wildcard
bindings keep the observation incomplete and any hint ambiguous. An annotated
generic parameter-list clue (`<@` or `,@`) conservatively blocks enum-member
hints for the consumer and reports incomplete. This includes type-use cases the
helper cannot distinguish; ordinary annotations outside generic lists retain
their existing behavior. No Java binding or compiler claim is added.

## Opt-in member types after a static on-demand import

Use this additional check for unqualified types after `import static Outer.*`,
such as `OrdinaryAccess`, `BlockRead`, `Store`, `PlaceBlock`, `Fm` or `SubCpu`.
That import does not import nonstatic member classes. An ordinary named import
(`import package.Outer.BlockRead;`), ordinary owner on-demand import
(`import package.Outer.*;`) or explicit `Outer.BlockRead` spelling can address the
member import; owner-root visibility, accessibility and compilation remain separate.
A package on-demand import `package.*` and being in the same package do not make
nonstatic nested types available by themselves.

Select the exact ordinary owner, including nested owners when needed:

```bash
--known-member-owner 'package.Outer=exact/immutable/Outer.java'
```

For an owner not yet committed, independently pin its canonical FINAL source and
use this separate explicit mapping. It never falls back to a checkout:

```bash
--known-member-owner-final "package.Outer=$OWNER_BYTES=$OWNER_SHA256=$ABSOLUTE_OWNER_JAVA"
```

Both options are repeatable and share the existing16-selection cap with types and
enums. Git directory/revision and the exact consumer source/FINAL declarations
remain required. FINAL owner paths use the same no-follow, hardlink/alias rejection,
byte/hash pins and source re-observation as the consumer. A file reused for several
owners or as consumer is byte/token-accounted once. No Java input executes.

The new fields appear **only when opted in**: `member_check_version:1`,
`known_member_owners` (direct declarations, staticness, source pins and incomplete
reasons) and `member_uses`. New advisory hints have kind
`possible_missing_nonstatic_member_import`, the ordinary suggested import when
unambiguous, and reason `static_on_demand_excludes_nonstatic_member_class`.
Existing no-opt-in JSON, limits, import/enum behavior and exit meanings are unchanged.
Only direct declarations under explicitly selected static-on-demand owners are
checked; absent inherited/unselected names are not certified.

Member records/enums/interfaces, classes declared in interfaces and explicitly
static classes are static. Other ordinary direct member classes are nonstatic.
Same enclosing actual lexical declarations, actual named/owner-on-demand imports
and explicit qualification are distinguished. Unknown modifiers, local/anonymous,
inheritance/generic scopes, conflicting imports, indirect qualifiers and type/value
or method shadows stay incomplete/advisory. A possible hint may remain ambiguous
inside an anonymous Ports implementation; adding the correct import can remove that
hint while other scopes remain incomplete. Do not read exit0 as Java resolution.

All existing byte/token/context/depth/time/output bounds remain. The256 `max_hits`
ceiling is shared across root/enum/member observations and separately caps the
selected direct-member inventory. No new source discovery, classpath, index, mutation,
permission workflow or runtime authority is added. Run the standard-library
[synthetic member controls](../../../tools/soniccd/test_java_member_import_preflight.py)
with `python3 -B`, `PYTHONDONTWRITEBYTECODE=1` and an owned temporary root; they use
in-memory Git fixtures and clean their files. Supplying the explicit
`PREFLIGHT_COMPAT_BASELINE_GIT_DIR` additionally compares no-opt-in JSON/errors and
limits with the hash-pinned immutable baseline; this optional read does not write Git.

Qualified inner construction (`receiver.new Inner()` with optional explicit constructor
type arguments) is selected through its receiver, so it receives no missing-import
hint; receiver binding stays incomplete. Enum constant-specific class bodies also
keep opt-in member observations incomplete, including bodies in a selected owner.
These checks do not change the legacy no-opt-in scope parser or certify Java binding.
