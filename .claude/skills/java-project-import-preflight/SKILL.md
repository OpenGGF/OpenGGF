---
name: java-project-import-preflight
description: Find fast source-only missing-import hints for explicitly selected project Java types before costly compile validation, using immutable Git type provenance and an exact source blob or pinned FINAL bytes. Does not resolve a classpath or certify compilation.
---

Use [java_project_import_preflight.py](../../../tools/soniccd/java_project_import_preflight.py)
when a source handoff introduces or moves references to known project types,
especially nested spellings such as `CameraArtResidency.Region`. Select a small
reviewed type list. This helper does not search a checkout or infer a destination.
The [source-bound packet handoff workflow](../soniccd-packet-integration/SKILL.md)
still owns source custody and the task's actual compile/test evidence.

Supply a canonical absolute local Git metadata directory, an exact lowercase
40-hex commit, and one or more `--known-type FQCN=exact/path.java` selections.
Each known type must have a matching ordinary top-level declaration and package
in that exact regular Git blob. Filenames alone establish nothing. Use the owning
Git/disassembly lookup separately if the type/path is not already known.

```bash
python3 -B tools/soniccd/java_project_import_preflight.py \
  --git-dir "$CANONICAL_GIT_DIR" --revision "$IMMUTABLE_COMMIT" \
  --known-type 'com.openggf.game.soniccd.resources.CdR12aCameraArtResidency=src/main/java/com/openggf/game/soniccd/resources/CdR12aCameraArtResidency.java' \
  --final "$ABSOLUTE_FINAL_JAVA" \
  --final-sha256 "$INDEPENDENT_FINAL_SHA256" --final-bytes "$INDEPENDENT_FINAL_BYTES"
```

For a committed consumer, replace the three FINAL options with
`--source exact/consumer/path.java`; that blob comes from the same revision.
FINAL pins must come from the agreed source handoff. The helper does not select
or authenticate the FINAL file for the caller. It reuses exact hash-pinned sibling
`java_declaration_census.py` safe readers and independently pinned
`enum_contract_audit.py` lexer bytes. A dependency mismatch rejects; do not bypass
pins or copy arbitrary Python helpers to obtain a result.

Read `uses`, `hints`, `incomplete_reasons` and provenance together. Root identifiers
matching selected simple type names are lexical candidates. Exact nonstatic
imports, package wildcard imports, same-package names and selected-file
ordinary top-level/member-type scopes can explain availability. Member scope is
limited to its lexical owner. Tokens after `.` are excluded as root candidates;
`Type.Member` produces one root candidate, while fully qualified spellings are
ignored rather than certified. Comments and literal contents produce no hits.

Exit0 means no hints within supported selected lexical scope. Exit1 means one or
more possible missing imports. Exit2 rejects invalid inputs, pins or reads.
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

Bounds can only be lowered:16 selected types,1MiB/file,4MiB aggregate selected
source bytes,100000 aggregate tokens,4096-token contexts,32 brace levels,256 root
hits,30 seconds checked between bounded operations and inside scope scans, and
1MiB accepted result JSON. Paths have at most4096 characters/64 components.
The shared lexer also bounds delimiter nesting at128; its bounded lex pass and
regular-file reads are checked before/after rather than interrupted mid-pass.
Selected source bytes are read again for non-atomic correspondence, so actual
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
