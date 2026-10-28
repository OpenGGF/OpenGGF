---
name: soniccd-packet-integration
description: Use when integrating frozen Sonic CD handoffs, checking reference custody or FINAL dependencies, preserving inherited bytes, or attributing focused queued validation to installed source.
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
or execution commands. `tests` has exactly this shape (illustrative class/count):

```json
{
  "classes": ["example.TestCanonicalOwner"],
  "properties": {"openggf.headless.gl": "surfaceless-egl", "soniccd.opening.egl": "true"},
  "expected_tests": {"example.TestCanonicalOwner": 3},
  "java_major": 25
}
```

Use 1–50 exact fully qualified classes, with independently established positive
per-class invocation counts and Java25. Methods, patterns and arbitrary Maven
arguments/profiles are unsupported here; use the normal queue for other shapes.
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
```

Execution generates only the exact declared class selection, allowlisted properties,
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

Result JSON is stdout; queue/Maven progress is stderr. It contains requested arguments,
actual terminal return code, admission-time UTC cutoff, source identities and exact
observed cases. The queue may qualify/augment JVM arguments under its normal rules;
requested arguments are not a claim of the final JVM command line. Exit0 means the
plan succeeded or the executed focused run passed;1 means observed test/count/Java
failure or skips;2 means invalid/incomplete evidence;130 means cancellation. Check
`executed` before interpreting exit0. Runtime proof is limited to that run.

Source observations hash all Git-tracked regular bytes, including assume-unchanged
files, and nonignored untracked files; gitlinks contribute index pins, symlinks their
link text. Limits are50000 paths,512MiB/file,8GiB total. Initialized submodule contents,
symlink targets, ignored untracked files, media and other external/environment inputs
need separate custody; the handoff's explicit dependencies cover only their named
files. Rechecks are non-atomic and can miss changes restored between observations.
No success receipt, pass cache, log archive or foreign-job watcher is created. Existing
Maven diagnostics keep their normal cleanup policy; consume/delete diagnostic outputs.

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
