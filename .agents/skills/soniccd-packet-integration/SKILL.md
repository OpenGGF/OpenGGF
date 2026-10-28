---
name: soniccd-packet-integration
description: Use when integrating frozen Sonic CD handoffs, checking reference custody or FINAL dependencies, preserving inherited bytes, attributing focused queued validation to installed source, or comparing explicit matched Surefire evidence.
---

# Sonic CD packet integration

Use `tools/soniccd/integration_packet.py` to replace repeated ad hoc packet and
Surefire inspection snippets. Read-only modes use the Python standard library;
apply also uses the existing worktree build/test lock. Run it from the checkout containing this tool, selecting
the destination worktree explicitly. Read-only modes do not stage, commit, fetch,
push, launch Maven, inspect foreign jobs or execute supplied source. Only the
explicit `run --execute` mode launches a focused Maven test through the existing queue.

Keep the full USA then JP/EU integration goal in the owning task. A validated
source packet is an integration aid, not evidence that a region or feature works.

## Check reference and resource custody

Use `custody` when integration depends on an optional native-reference checkout or
an explicitly reviewed resource link. It observes the complete gitlink/module
registration inventory, including pins hidden by `submodule.*.ignore`, and compares
initialized checkout HEADs and local changes with the index pins. It separately
inspects only the resource links named by the caller:

```bash
python3 -B tools/soniccd/integration_packet.py custody \
  --worktree "$DESTINATION_WORKTREE"
python3 -B tools/soniccd/integration_packet.py custody \
  --worktree "$DESTINATION_WORKTREE" \
  --link "reviewed-relative-path=$EXPECTED_ABSOLUTE_TARGET"
```

Repeat `--link` for each reviewed link. Relative and absolute link text may resolve
to the same expected canonical target; the tool does not read or authenticate target
content. Missing, broken, wrong-target and non-link paths remain explicit negative
observations. A linked submodule is not treated as an initialized canonical checkout.

Exit0 means the requested observations are ready,1 means observed custody issues,
and2 means invalid or incomplete evidence. Read the JSON reasons: an optional
uninitialized reference is not proof that runtime assets or an engine test are
unavailable. Initialize or repair nothing merely to turn this diagnostic green.
The command never changes configuration, links, submodules or source, obtains test
admission, queries jobs, imports a packet or runs Maven. Root source edits are outside
this diagnostic; use the separate source-bound runner for execution attribution.
Rechecks are non-atomic and cannot detect every change-and-restore or establish
exclusive ownership. Read the output's bounds and unsupported observations before
using it as evidence; `ready` is neither a custody lease nor native/API approval.
This mode requires POSIX. System/global Git configuration is disabled; configured
local clean/process/textconv/external-diff drivers stop status inspection without
printing their commands. Nested module contents and ignored files are not inspected.
The bounded command/status output and time budgets are reported in `limits`.

## Structured handoff and dependency readiness

Use one external, LF-terminated UTF-8 JSON handoff for canonical mappings, FINAL
dependencies, descriptive API changes/known defects and the intended focused test
inventory. The strict version-1 wrapper contains exactly `version`, `id`, `packet`,
`dependencies`, `api_changes`, `known_defects` and `tests`. `packet` is the complete
existing packet-manifest object described below, not a path or an inferred mapping.
Its sources and baselines retain all existing safety requirements.

Each dependency has exactly `name`, `path`, `sha256`, `bytes`, `mode`; the canonical
absolute path is external and its pins are independently supplied FINAL evidence.
Modes are `0444` or `0644`. A dependency may reuse the same exact incoming source
path with identical pins. Distinct hardlink names, conflicting roles/pins, symlinks
and dependency/destination aliases reject. Missing or changed dependencies block
readiness, including a missing dependency parent; they do not justify a fallback.

`api_changes` and `known_defects` are arrays of plain descriptions, not API approval
or execution commands. Whole-class `tests` retains this shape (illustrative class/count):

```json
{
  "classes": ["example.TestCanonicalOwner"],
  "properties": {"openggf.headless.gl": "surfaceless-egl", "soniccd.opening.egl": "true"},
  "expected_tests": {"example.TestCanonicalOwner": 3},
  "java_major": 25
}
```

Use 1–50 exact fully qualified classes, with independently established positive
per-class invocation counts and Java25. For an explicit subset of those classes,
add **both** optional `methods` and `expected_cases` maps. For example, a declared
`example.TestEgl` with `expected_tests` count2 can add:

```json
"methods": {"example.TestEgl": ["drawOpaque", "drawTransparent"]},
"expected_cases": {"example.TestEgl": [
  "example.TestEgl#drawOpaque()", "example.TestEgl#drawTransparent()"
]}
```

The maps must have identical, nonempty class keys drawn from `classes`. Other
classes remain whole-class selections. Method lists contain distinct exact ASCII
Java identifiers, at most128 characters each,50/class and100 total; no signatures,
patterns, paths or shell syntax. The runner preserves declared class order and
sorts method identifiers into one `-Dtest=Class,OtherClass#method1+method2` argv item;
it never passes declarations through a shell. Arbitrary Maven arguments/profiles
remain unsupported.

Supply independently known **literal full Surefire identities** for each method-
selected class, unique and owned by the `Class#` prefix. Each list must match its
`expected_tests` count, with at most10000 identities total and2048 characters/ID;
empty names, control characters and invalid Unicode reject. Neither a method name
nor its count supplies an identity. Parameterized/display names are never normalized,
and overloads or one selector may produce multiple explicitly declared invocation
identities. The tool observes those literal cases; it cannot infer method ownership,
discovery or one invocation per selector/overload. Omitting both maps preserves the
existing whole-class schema and behavior; supplying only one or empty maps rejects.
Properties use the existing media/EGL allowlist, including `soniccd.opening.egl`;
Java version and worktree properties cannot be caller overrides. Unknown or
duplicate JSON keys, bool-as-integer values and unsafe declarations fail closed.
Bounds: handoff4MiB, 1024 packet files/64MiB incoming bytes, 128 dependencies,
16MiB/file, 192MiB observed packet/dependency bytes. Descriptions are limited to
128 entries of 2048 characters each. `id`/dependency names are distinct bounded
ASCII identifiers. These are observations with rechecks, not atomic snapshots.

```bash
# Read-only: supplied original destination baselines and FINAL dependencies.
python3 -B tools/soniccd/integration_packet.py handoff \
  --worktree "$DESTINATION_WORKTREE" --spec "$FINAL_HANDOFF_JSON"
# After reviewing readiness, emit the unchanged inner manifest for existing apply.
python3 -B tools/soniccd/integration_packet.py handoff \
  --worktree "$DESTINATION_WORKTREE" --spec "$FINAL_HANDOFF_JSON" --packet-only \
  > "$EXTERNAL_IMPORT_MANIFEST"
# Import remains explicit; all task-owned Maven requests must be terminal.
python3 -B tools/soniccd/integration_packet.py packet \
  --worktree "$DESTINATION_WORKTREE" --manifest "$EXTERNAL_IMPORT_MANIFEST" \
  --apply --maven-terminal
# Read-only: require actual destinations to equal incoming FINAL bytes.
python3 -B tools/soniccd/integration_packet.py handoff \
  --worktree "$DESTINATION_WORKTREE" --spec "$FINAL_HANDOFF_JSON" --installed
```

Default/baseline readiness and installed readiness are different checks. An
installed check never changes the supplied original baseline pins. `handoff`
exits0 for ready,1 for blocked and2 for malformed/unsafe evidence. `--packet-only`
requires baseline readiness and emits nothing on rejection; do not combine it with
`--installed`. Shell redirection creates/truncates its chosen file even on failure:
check the command status before using it. No mode infers or approves native/API claims.

## Source-bound focused run

`run` is a read-only installed-handoff/command plan until `--execute` is supplied:

```bash
python3 -B tools/soniccd/integration_packet.py run \
  --worktree "$DESTINATION_WORKTREE" --spec "$FINAL_HANDOFF_JSON"
python3 -B tools/soniccd/integration_packet.py run \
  --worktree "$DESTINATION_WORKTREE" --spec "$FINAL_HANDOFF_JSON" --execute
# Optional compact structured result; default progress remains complete.
python3 -B tools/soniccd/integration_packet.py run \
  --worktree "$DESTINATION_WORKTREE" --spec "$FINAL_HANDOFF_JSON" --execute --compact-output
# Independently bound child Maven stdout displayed on stderr; queue notices remain complete.
python3 -B tools/soniccd/integration_packet.py run \
  --worktree "$DESTINATION_WORKTREE" --spec "$FINAL_HANDOFF_JSON" --execute \
  --compact-output --maven-progress concise
```

Execution generates only the exact declared class/method selection, allowlisted properties,
`-Dmse=off`, `test -B`; it performs no imports, automatic fixes, retry, broad selection,
commit or push. Default lane is normal; `--lane auto`/`lean` retain the existing queue's
qualification/resource rules. Explicit RED executions remain failed observations.

The runner fingerprints actual tracked and visible nonignored untracked file bytes
plus HEAD/index pins before requesting admission. It revalidates source, handoff and
dependencies after admission, immediately before Maven launch. The existing queue
retains its worktree/execution leases through process-tree cleanup, final source and
dependency checks and fresh XML inspection. A mutation during queue waiting prevents
launch; an observed post-launch mutation makes evidence incomplete. Reports must be
rewritten by this invocation, match the complete class/count/environment inventory,
use Java25 and have no failures/errors/skips for a positive result. A nonzero Maven
exit can never become positive from green XML. Missing XML is incomplete.
For method-selected classes, the existing post-run fresh-XML callback also requires
exact equality with the declared literal identity set under the same held leases.
Equal totals with different identities produce **incomplete** evidence, retaining
actual cases and separate `case_identity_issues` missing/unexpected IDs; they never
become a positive result. Whole-class selections retain their existing count contract.
This adds no rerun, broad selection or discovery/native-authenticity claim.

Result JSON is stdout; queue/Maven progress is stderr. It contains requested arguments,
actual terminal return code, admission-time UTC cutoff, source identities and exact
observed cases. The queue may qualify/augment JVM arguments under its normal rules;
requested arguments are not a claim of the final JVM command line. Exit0 means the
plan succeeded or the executed focused run passed;1 means observed test/count/Java
failure or skips;2 means invalid/incomplete evidence;130 means cancellation. Check
`executed` before interpreting exit0. Runtime proof is limited to that run.

Optional `--compact-output` changes only structured result presentation, for plans
and executed runs. It omits passing case records and the repeated `expected_cases`
identity lists, with exact per-class declared-identity omission counts in
`output.expected_case_identities_omitted`. All literal identities are still checked
before presentation. It includes at most20 failure/error/skipped case records across
all classes, in the normal report/case order. Each included diagnostic string,
including its displayed identity, is limited to1000 characters; the record's
`truncated_characters` maps each shortened field to its exact omitted character count.
Per-class and aggregate `omitted_cases` count omitted records by outcome, including
passes. Exact aggregate/per-class test/outcome counts remain visible.

`output.full_result_status` and `output.full_result_clean` repeat the full verification
verdict, never a verdict inferred from displayed cases; plans use `not-executed` and
null. Requested arguments/classes/methods, properties, source observations, terminal
Maven return code and all existing verification issues remain unchanged. Separate
`case_identity_issues` retain **every complete missing/unexpected literal identity**,
even when the displayed case record is omitted or shortened. These integrity details
are not capped, so compact JSON has no absolute byte bound. The default JSON shape
and full case expansion remain unchanged without the option. This flag does not
filter progress; default `--maven-progress full` keeps its existing complete streams.
It creates no full-result file, log archive, receipt or cache and changes no selection,
callback, source check, queue lane, exit code or execution authority.

Optional run-only `--maven-progress concise` is a separate presentation choice,
combinable with `--compact-output` for plans and execution. A plan describes the
limits with zero observed lines and launches nothing. Only the `maven_stdout`
sink passed to the existing queue is filtered: child stdout still displays on
stderr. Direct queue notices and child stderr retain their existing complete
streams. This is never an absolute bound on orchestration output or JSON. Unknown
values, abbreviated run options and use on another subcommand reject.

Concise mode displays at most64 Maven content lines, with4 slots reserved for
`BUILD SUCCESS`/`BUILD FAILURE`; at most8 warnings use the remaining60 slots.
Other eligible prefixes are Maven `[ERROR]`, class `Tests run:` summaries,
`Results:`, `Compiling`, `Total time:` and `Finished at:`; ANSI CSI decoration is
ignored only when recognizing those prefixes. Each displayed line retains at
most512 input characters, with a truncation marker when shortened. One final
counts line is limited to512 characters. Including markers and normalized LF
terminators, this sink prints at most65 lines and34241 Unicode characters.
The pending line prefix is limited to512 characters; the owned reader consumes
4096-byte pipe chunks with an incremental UTF-8 decoder and replacement of
undecodable bytes. CR, LF, split CRLF and final unterminated lines are handled
without retaining complete long lines or a full log.

Only concise selection adds `maven_progress` metadata to result JSON. Its counts
cover logical input lines and Unicode characters excluding CR/LF, displayed
input prefixes, fully suppressed lines/characters and truncated displayed
lines/suffix characters. Thus input characters equal displayed plus suppressed
plus truncated characters; added markers/normalized terminators are outside
these input-character counters. The final line explicitly reports suppression
and truncation counts; if extraordinarily large counters exceed its space,
it points to their exact values in JSON. Queue text never enters these counts.
Displayed text is incomplete and has no evidentiary authority: fresh complete
XML, literal identities, source/dependency checks, diagnostics, verdicts,
requested Maven arguments, queue lane/leases, callbacks and exit decisions keep
their existing contract. Display failures propagate after pipe drain and
terminal queue callbacks; cancellation preserves its original exception.
The owned descriptors/reader are closed/joined, borrowed stderr stays open,
and no log file, archive, cache, receipt or retry is created.

Source observations hash all Git-tracked regular bytes, including assume-unchanged
files, and nonignored untracked files; gitlinks contribute index pins, symlinks their
link text. Limits are50000 paths,512MiB/file,8GiB total. Initialized submodule contents,
symlink targets, ignored untracked files, media and other external/environment inputs
need separate custody; the handoff's explicit dependencies cover only their named
files. Rechecks are non-atomic and can miss changes restored between observations.
No success receipt, pass cache, log archive or foreign-job watcher is created. Existing
Maven diagnostics keep their normal cleanup policy; consume/delete diagnostic outputs.

Reader startup failures retire the pipe once and reject reuse of that failed
sink. Constructor failure and cancellation after native thread launch are covered
by descriptor-ownership tests; a cancelled reader cannot close a reused file
descriptor. This is host output cleanup, not queue or test-verdict authority.

## Verify declared inherited-byte preservation

Use `preserve --recipe` for a whole-file check after declared migrations. Root must
independently export and retain the known baseline snapshot and supply its pins,
along with the FINAL handoff pins. Never generate baseline pins from an unknown
file, use moving checkout source, infer or approve edits, or auto-fix a mismatch.
This mode does not invoke Git, acquire a lease, import, or execute supplied code.

```bash
python3 -B "$OPENGGF_ROOT/tools/soniccd/integration_packet.py" preserve \
  --recipe "$EXTERNAL_RECIPE_JSON"
```

The external UTF-8 JSON recipe has exactly this version-1 shape. This example
describes `old\n` becoming `new\n`; its dummy SHA256 values must be replaced with
the independently supplied pins before use:

```json
{
  "version": 1,
  "baseline": {
    "path": "/external/retained/Baseline.java",
    "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
    "bytes": 4
  },
  "final": {
    "path": "/external/frozen/FINAL.java",
    "sha256": "1111111111111111111111111111111111111111111111111111111111111111",
    "bytes": 4
  },
  "edits": [{"offset": 0, "before": "b2xk", "after": "bmV3"}]
}
```

Offsets are zero-based **BASELINE byte coordinates**, including LF and UTF-8
bytes, never character indices or offsets shifted by earlier edits. Supply unique,
strictly increasing offsets and nonoverlapping `before` ranges, with exact original
bytes. `before` and `after` use canonical standard Base64 (including required
padding, no whitespace or URL alphabet). An empty string permits insertion or
deletion; empty/empty is rejected. An empty edits array checks identical files.
Unknown/duplicate keys, nonfinite values and wrong scalar/container types fail.

Both files require the frozen UTF-8/LF contract below and independently supplied
lowercase SHA256 plus exact byte counts; no pins are invented. Recipe and files
must be distinct canonical absolute regular paths, external to the checkout
containing the tool; symlinks, lexical aliases and mutual hardlink aliases fail.
This does not establish externality to some other checkout. Paths are limited to
4096 characters; recipe to 4 MiB; each input/reconstructed file to 16 MiB; edits
to 4096 and aggregate decoded `before` + `after` bytes to 1 MiB.

The verifier reconstructs expected FINAL from baseline and declared edits, compares
**all bytes** with the pinned FINAL, then reconstructs and compares the full inverse
with baseline. Preserved gaps participate in both comparisons. It rechecks recipe,
input content/metadata and parent directory identities before emitting a summary.
These observations are **non-atomic**: changes between or after checks, including
change-and-restore between observations, may escape detection. Mode `0444` cannot
prevent owner writes; reads may update atimes. Pins do not prove provenance,
semantic/API acceptance, test execution or native authenticity.

Exit `0` emits only pins, byte/edit counts, equality flags and that limit to stdout;
`2` emits a bounded JSON error on stderr with no success stdout. Mismatches name
an edit or first differing byte offset, without source excerpts. The mode writes
no files, receipts, caches or logs, and has no apply/output/worktree option.

## Freeze and pin the packet

Accept a worker's FINAL source-only handoff with absolute external frozen mirror
paths, SHA256, byte lengths and explicit canonical destinations. Keep Root-owned
R2 preserved originals and source evidence intact outside the destination tree;
the importer does not create backups. Do not treat an author report marked UNRUN
as execution evidence or reconstruct expected outputs by executing gameplay,
helpers or probes. Independently check the fixed research/source pin named by the
owning task against its retained provenance; this tool verifies file bytes and
the destination worktree HEAD, not the truth of a source-model/oracle claim.

### Draft from an explicit external mapping

Use the read-only `draft` subcommand to avoid handwriting a packet manifest.
Supply an external JSON spec with exactly `version` and nonempty `files`; every
entry has exactly `source`, `sha256`, `bytes` and `destination`. Source pins must
come from the externally supplied FINAL handoff. Do not manufacture those pins
by measuring unknown input. Destinations explicitly name reviewed canonical
relative POSIX paths; a mirror name or Java package is never path authority.
This illustrative spec uses dummy pins:

```json
{
  "version": 1,
  "files": [
    {
      "source": "/external/frozen-worker/ReviewMirror.java",
      "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
      "bytes": 123,
      "destination": "src/test/java/example/TestCanonicalOwner.java"
    }
  ]
}
```

```bash
python3 -B tools/soniccd/integration_packet.py draft \
  --worktree "$DESTINATION_WORKTREE" --spec "$EXTERNAL_MAPPING_JSON"
```

Draft verifies the supplied source pins and frozen-text contract below, using the
same packet path, alias and source-budget checks. The spec must be canonical,
external, regular, non-symlink UTF-8 JSON, at most 16 MiB; unknown/duplicate keys
are rejected. It observes the actual destination HEAD and current destination
bytes, including uncommitted changes, or absence. New files require existing
parents. It emits only the existing version-1 manifest to stdout, with source
pins retained, `mode=0444`, `utf8=true`, `lf=true`, and observed destination
`expected` pins. It never infers destinations, approves source claims or dirty
baselines, writes files, acquires a lease, queries jobs or executes supplied code.
Only the existing fixed read-only Git root/HEAD calls run.

Before emitting, draft rechecks the spec, sources, destination content/metadata,
destination directories and HEAD; observed changes reject the draft. These are
**non-atomic observations**, not a filesystem snapshot: changes between or after
checks, including a HEAD change and restoration between reads, can escape them.
Frozen mode does not establish provenance or prevent owner writes; reads may
update atimes. Review the complete draft and independently establish FINAL source
custody and acceptable current baselines before saving a manifest outside the
repository and using normal read-only `packet`, then the authorized apply flow.
Draft does not weaken or bypass either validation. Exit `0` emits a draft; `2`
rejects it with diagnostics on stderr and no manifest stdout. There is no output,
apply, Maven or scheduler option.

### Review the normal packet manifest

Supply a version-1 manifest outside the repository with actual invocation paths,
externally supplied FINAL source pins and reviewed observed destination baselines.
This illustrative JSON uses dummy pins:

```json
{
  "version": 1,
  "worktree_head": "0000000000000000000000000000000000000000",
  "files": [
    {
      "source": "/external/frozen-worker/ReviewMirror.java",
      "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
      "bytes": 123,
      "mode": "0444",
      "utf8": true,
      "lf": true,
      "destination": "src/test/java/example/TestCanonicalOwner.java",
      "expected": {
        "sha256": "1111111111111111111111111111111111111111111111111111111111111111"
      }
    }
  ]
}
```

`worktree_head` is the exact destination worktree commit; destination SHA256 pins
cover its actual existing bytes, including reviewed uncommitted inputs. For a new
file use `"expected": {"absent": true}` instead. No unknown fields or duplicate
JSON keys are accepted. The deliberately odd source mirror name has no package
authority: `destination` must explicitly name the reviewed canonical source path.
The tool does not infer a package move, rename a class or transform source bytes.

```bash
python3 -B tools/soniccd/integration_packet.py packet \
  --worktree "$DESTINATION_WORKTREE" --manifest "$FINAL_PACKET_JSON"
```

Inspect the entire plan: current versus expected baseline, incoming hash/length/
mode and exact destination for every file. All sources must be external regular
files with exact mode `0444`, strict UTF-8, no CR/NUL, and a final LF unless empty.
Destinations are relative POSIX paths with existing parent directories. The tool
rejects symlink paths, traversal, Git metadata paths, duplicate destinations,
source/destination and destination/destination hardlink aliases, changed pins and
files over 16 MiB or packets over 64 MiB. Create/review new directories separately.
Frozen mode is a preservation convention, not a guarantee against owner writes.

## Import only when the worktree is ready

Before apply, the caller must ensure **all task-owned queued and running Maven
requests for this worktree are terminal** and stop other writers to these files.
The build/test lock excludes cooperating active builds/tests; it does **not**
prove that a queued request is terminal. Do not inspect/control foreign jobs or
delete any lock file to make progress.

```bash
python3 -B tools/soniccd/integration_packet.py packet \
  --worktree "$DESTINATION_WORKTREE" --manifest "$FINAL_PACKET_JSON" \
  --apply --maven-terminal
```

The declaration is caller supplied, not an automatic scheduler check. Apply
preflights the whole packet, acquires the existing worktree build/test lock, then
preflights everything again after any wait. It replaces each destination
atomically with identical bytes, mode `0644` and a fresh mtime; frozen source
content, mode and mtime remain unchanged. Sources may acquire normal read atimes.
Apply needs POSIX directory descriptors and `O_NOFOLLOW`; read-only planning
remains available elsewhere. Parents are opened without following symlinks.

The packet is **not** one multi-file transaction. On an I/O or detected concurrent
change, the tool stops, lists completed replacements and remaining files, attempts
to remove its own temporary file, and reports a cleanup failure with its exact
owned path. It does not roll back completed or unrelated data. Keep
cooperating writers excluded: the build lock cannot make a hash-check/rename pair
an atomic compare-and-swap against an arbitrary external editor. Reconcile actual
files and intentionally repin a remaining packet before retrying; never relax a
baseline merely to get apply accepted. Exit codes: `0` validated/applied, `2`
rejected or failed before any confirmed replacement, `3` partial import.

## Attribute focused test evidence

Follow the owning task's test-first flow: observe the named new regression RED
against the pinned original, import production source, observe its positive run,
then complete the bounded affected consumer/guard checks. Choose these from the
actual change. This skill adds no Maven invocation, approval ceremony, engine
suite or repeated unchanged review. Use repository queue/selection policy when
the owning task authorizes execution.

Once the task-owned command is terminal, inspect only its worktree's fresh
`target/surefire-reports`. Supply the actual run-start UTC timestamp and the
complete exact class set for that invocation, plus expected media/EGL properties:

```bash
python3 -B tools/soniccd/integration_packet.py junit \
  --worktree "$DESTINATION_WORKTREE" --since "$RUN_START_UTC" \
  --class example.TestCanonicalOwner --class example.TestAffectedConsumer \
  --property "soniccd.media.root=$MEDIA_ROOT" \
  --property openggf.headless.gl=surfaceless-egl
```

This reads flat `TEST-*.xml` files only and requires an exact fresh class set,
matching suite/testcase ownership, unique actual identities, and consistent
declared/observed counts. Unrelated reports older than the invocation cutoff
are counted and ignored without parsing or deleting them. A requested class's
stale report still fails, and any unexpected fresh class fails. The tool never
accepts a foreign qualified testcase owner. Exact simple testcase class names
(as emitted by ArchUnit) are attributed to the full suite owner while their
reported name is retained separately in the output. The tool never
deletes reports. It rejects stale requested, missing, unexpected fresh, empty
and malformed evidence (reports also have a 16 MiB
per-file read limit). It reports every observed test
identity and exact failure/error/skip message. Missing `java.version`, missing or
wrong `user.dir`, and absent/mismatched requested properties are flagged. Read the
actual Java version when the task requires Java 25. Only the fixed media/EGL/JVM
allowlist is exposed; arbitrary JVM properties and credentials are not printed.

Exit `0` means the specified XML observations have no failures/errors/skips or
environment issues; `1` reports those negative observations; `2` rejects the
evidence. XML freshness does not prove process completion, source identity or
test discovery. Attribute the result to the exact command, tested SHA and relevant
uncommitted source, invocation cutoff, classes, environment, identities and skips.
`head_at_inspection` is not a claimed execution SHA. Never combine older narrow
passes into an invented fresh total. No output is retained unless the caller
explicitly redirects text; do not create pass caches, receipts or raw-log archives.

## Compare selected matched test evidence

After both task-owned commands are terminal, use `compare-junit` for a bounded
matched baseline/candidate control. Select **distinct** canonical worktree roots,
actual per-side run-start UTC timestamps (`Z` or `+00:00`), and the shared complete
exact class set. Declare each side's required allowlisted media/EGL properties;
values may differ deliberately. Select meaningful controls from the disputed
failure and affected consumers. Do not infer equivalent commands or use this
comparison to replace the owning task's required validation.

```bash
python3 -B tools/soniccd/integration_packet.py compare-junit \
  --baseline-worktree "$BASELINE_WORKTREE" --baseline-since "$BASELINE_RUN_START_UTC" \
  --candidate-worktree "$CANDIDATE_WORKTREE" --candidate-since "$CANDIDATE_RUN_START_UTC" \
  --class example.TestCanonicalOwner --class example.TestAffectedConsumer \
  --baseline-property openggf.headless.gl=surfaceless-egl \
  --candidate-property openggf.headless.gl=surfaceless-egl
```

The existing fresh exact-class XML reader owns parsing and identities. Comparison
uses exact `identity`, `status`, `message` and `type`, retaining assertion text,
whitespace and dynamic values. It reports unchanged failures, errors and skips;
new, worsened or changed failures; resolved failures/skips; new/changed skips;
failures becoming skipped; and added/missing identities separately. A failure
becoming an error is labelled `worsened_failures` as an outcome transition only.
A different existing assertion remains `changed_failures`, not a known baseline
failure. A missing identity is never resolved. Counts do not establish equivalence.

Exit0 means exact test-outcome equality, including any unchanged failures/skips;
exit1 means accepted evidence with differences, **including resolutions**; exit2
rejects invalid/incomplete evidence. `candidate_all_passed` describes XML outcomes
only. Required environment mismatches reject; Java version and independently
requested properties remain visible, with observed differences separate from test
outcome equality. A property not requested on one side is marked unobserved there.
Read the actual Java version and establish relevant environment/command equivalence
independently. Neither exit0 nor inspected HEAD authenticates execution or source.

Bounds per side:1–50 classes,4096 flat report-directory entries,16MiB/selected XML,
64MiB selected XML,50000 testcase elements,XML depth64. Selected XML must be UTF-8
without NUL/DTD/entity declarations. Report files must be regular and paths/parents
canonical, with no symlinks or selected hardlinks. Missing/stale requested or unexpected fresh
classes, duplicate identities, malformed counts and unsupported outcomes reject.
Safe unrelated stale reports remain unparsed and undeleted. Report bytes, directory
identities/inventory and inspected HEAD are rechecked across both selections;
observations are non-atomic and can miss change-and-restore. The tool certifies no
command completion, command equivalence, source identity, causality or discovery.
It acquires no lease and runs, reruns, imports or writes nothing; no pass cache,
receipt or log archive is created. Consume existing diagnostics under normal policy.

## Check explicit native binary joins

Use `tools/soniccd/native_operand_check.py` when an integration report needs a
repeatable check of a **previously reviewed** binary identity, finite table spans
and literal address operands. This is static research evidence. It neither
extracts assets nor admits a runtime fallback from native source.

```bash
python3 -B tools/soniccd/native_operand_check.py \
  --root "$CANONICAL_BINARY_ROOT" --recipe "$ABSOLUTE_RECIPE_JSON"
```

The selected root and recipe must be canonical absolute paths without symlinks;
the binary path is explicitly relative to that root. Inputs must be distinct
regular files. The recipe contains exactly `version`, `binary`, `spans`,
`literals` and `operands`. For example, replace these dummy pins and observations
with independently reviewed native/media evidence before running it:

```json
{
  "version": 1,
  "binary": {
    "path": "R11A__.MMD", "bytes": 262144,
    "sha256": "0000000000000000000000000000000000000000000000000000000000000000"
  },
  "spans": [{
    "id": "reviewed-table", "offset": 1024, "bytes": 16,
    "sha256": "1111111111111111111111111111111111111111111111111111111111111111"
  }],
  "literals": [{"id": "reviewed-absolute-form", "offset": 512, "hex": "43f900200400"}],
  "operands": [{
    "id": "reviewed-address", "kind": "absolute-long", "offset": 514,
    "width": 4, "encoded": "00200400", "expected_target": 2098176
  }]
}
```

`absolute-long` reads an unsigned big-endian four-byte value, with **no** base.
`pc-relative-word` requires width2 and an explicit `pc_base`;
`table-relative-word` requires width2 and an explicit `table_base`. Both WORD
forms add the signed big-endian displacement to the supplied base. A PC base is
the source-proved native base, **not an automatically assumed operand-end PC**.
An absolute-long LEA is not a PC-relative LEA. To check that distinction, include
the entire reviewed opcode/operand encoding in a literal check; the tool does
not disassemble or infer an instruction from an address alone. Derived targets
must fit unsigned32 without wrapping. Hex is lowercase, byte-aligned and has no
spaces or prefix. All numbers are JSON integers, not hexadecimal strings.

Checks have globally unique short ASCII IDs. Spans, literals and operands each
reject internal overlaps; overlaps **between** these categories are permitted
and counted, so a literal can cover the operand it corroborates. No span union
is claimed to cover a routine or source file. Full binary size and SHA256 are
checked before individual observations; trailing data is a mismatch, not ignored
padding. Missing/unknown keys, duplicate JSON keys and nonfinite numbers reject.

Exit0 emits verified hashes, lengths, offsets and derived targets, never span or
literal bytes. Exit1 means a checked identity/byte/target mismatch; exit2 means
invalid input; exit3 means unsupported form/platform/version or an incomplete
observation. Error JSON goes to stderr with no success stdout. Limits are32MiB
per binary,256KiB per recipe,512 total checks and64KiB aggregate literal/operand
bytes. The tool writes no output files, invokes no Git/Maven/native process,
reads no disc track layout and changes no references or queue policy.

File and parent identities are rechecked, but observations remain non-atomic;
change-and-restore can escape them and reads may update atimes. Caller-supplied
pins, PC/table bases and source/region names are not independently authenticated.
A successful recipe proves those explicit byte/arithmetic comparisons only, not
source provenance, native control flow, reader extent, gameplay parity, ABI,
coverage or engine execution. Preserve the original evidence and use the normal
source-bound validation flow for the implementation.

## Check an original CUE root MMD in memory

Use `tools/soniccd/native_operand_disc.py` for the same bounded span/literal/operand
checks when the reviewed native file remains inside an original retail CUE/data
track. Select the original CUE root explicitly; do not discover other media or
extract an MMD to satisfy the binary tool. This separate CLI accepts a separate
strict version-1 **disc** recipe; the binary CLI/schema above remain unchanged.

```bash
python3 -B tools/soniccd/native_operand_disc.py \
  --root "$CANONICAL_ORIGINAL_CUE_ROOT" --recipe "$ABSOLUTE_EXTERNAL_DISC_RECIPE"
```

The recipe contains exactly `version`, `cue`, `mmd`, `spans`, `literals` and
`operands`. `cue` contains exactly `path` (a root CUE basename), `bytes` and
`sha256`. `mmd` contains exactly `name`, `bytes` and `sha256`: an uppercase 1–8
character ASCII alphanumeric/underscore basename plus `.MMD`, without a path or
version suffix. The exact root ISO lookup appends `;1`; it does not search,
case-fold or choose another version. Both byte counts and full-file lowercase
SHA256 pins are required. The MMD pin covers its entire original file, including
header and trailing data, rather than a relocated copy or supplied Word RAM image.
The three check arrays use the existing schema, bounds, literal comparisons and
explicit-base arithmetic described above. No additional operand kind is supported.
An indexed LEA can be checked as a full literal; the tool does not decode its
extension/index or infer a dynamic effective address from it.

This mode requires Linux no-follow reads and `/proc/self/fd`. Missing descriptor
access returns incomplete/exit3, with no path-reopen fallback. The selected root,
recipe, original CUE and data file must be canonical, without symlink parents;
recipe must be external to the media root. Input files must be distinct. CUE FILE
names must be distinct root basenames without lexical or case aliases. The reused
`retail_profiles.parse_cue` accepts only its documented split35-track profile:
MODE1/2352 data track with INDEX01 zero, followed by34 split audio tracks with the
supported indexes. Audio names are validated as text, but audio files are never
opened, statted or hashed; their availability/content is not established.

The original data file is bound by an O_NOFOLLOW descriptor and fed internally to
existing `stage_inventory.RawIso` through `/proc/self/fd`, retaining its independent
PVD, raw-sector mode, both-endian field, extent and duplicate-record checks. Reads
are bounded before inherited allocation:64KiB CUE,1GiB data-track extent (not a
full-track read/hash),1MiB root directory,4096 root file entries,256KiB MMD, and
1,312,768 aggregate logical ISO bytes. Physical sector reads include raw framing
and may reread sectors; the reported logical byte count is not a whole-disc scan.
Recipe/check bounds remain256KiB,512 checks and64KiB aggregate literal/operand bytes.

`stage_inventory.Mmd` validates its existing header/copy bounds. Output includes
its header facts, declared/file-backed/unresolved copy-byte counts, file extent and
original file-offset domain/native base. All checks read original file-backed
bytes only. A nonzero unresolved tail remains explicit and does not prevent an
in-range file check; a check needing absent tail bytes rejects. No uninitialized
Word RAM, relocated padding, native entry/control-flow traversal or runtime image
is fabricated. A passing header observation does not prove entry/interrupt targets
or an entire copied program can execute.

Exit0 emits metadata/hashes/offsets/derived targets only;1 is a pin/literal/span/
operand mismatch;2 is invalid input or unsupported CUE/ISO/MMD structure;3 is an
unsupported platform/version or incomplete/changing observation. Errors go to
stderr with no success stdout. CUE/data/recipe identities and all parent signatures
are rechecked; these observations remain non-atomic and can miss change-and-restore.
The output separately states that audio and full-track hashes were not observed.
CUE shape and an exact named MMD pin do not authenticate a whole disc, region,
source, physical provenance, native control flow, gameplay or engine execution.
No payload bytes, extraction, output file, server, Maven/Git/native execution,
retry, log archive, receipt or cache is created.

## Plan Sonic CD smoke coverage with the matching category runner

Use the destination's matched `tools/testing/run_categories.py` and
`test-categories.json` to inspect the complete source-based selection before
launching combined validation:

```bash
cd "$DESTINATION_WORKTREE"
python3 -B tools/testing/run_categories.py --category soniccd --json
python3 -B tools/testing/run_categories.py --base "$INTEGRATION_BASE" --json
# The delivery lead owns actual launch-environment preflight and required execution.
python3 -B tools/testing/run_categories.py --base "$INTEGRATION_BASE" --preflight
```

The `soniccd` category selects the complete CD package candidate inventory plus
always-included common tests. Reviewed internal CD production prefixes add the
existing content, physics, rendering, audio and rewind categories as appropriate;
this is broader than a CD-only test selection. Module/bootstrap, unknown scopes
and shared changes keep their existing conservative selection. Add semantic
categories when the actual change reaches other consumers, and inspect the whole
plan. Never filter candidate lists or substitute a hand-picked class subset for
required change-based coverage.

Policy schema2 uses `ownership_rules` and optional validated
`category_dependencies`; `gameplay` depends on `soniccd` to preserve the previous
gameplay candidate set after relocating CD package ownership. The matched loader
also accepts legacy schema1 without dependency fields. Install policy and runner
together: the old reader ignores the version number but fails on schema2's absent
legacy `owners` key before selection. This is fail-closed incompatibility, not
backward execution support. Do not add a raw `owners` alias, downgrade the policy,
or invoke an older absolute category runner against schema2. Using an updated
absolute Maven queue wrapper remains a separate supported scheduling operation;
it is not a category-policy reader.

A dry plan reports candidate classes, not discovered JUnit invocations, elapsed
cost, completed checks or whole-CD certification. The new category changes no
tags, smoke/default/all inventory, guards, JVM/backend settings, queue admission,
release-only full-suite or explicit exhaustive-audit policy. Required original
media, EGL, native/trace and domain checks remain separate. Check actual skips and
failures, keep the existing diagnostic cleanup, and create no pass cache or receipt.
