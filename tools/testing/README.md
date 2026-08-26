# Test session tooling

## Frozen-next baseline adapter

Frozen commit `84d9a3761` predates the session-output Maven properties. Its
baseline evidence therefore uses `frozen-next-session-launch.sh` and
`frozen-next-session-adapter.sh` with the pinned detached develop harness.
They are historical baseline-only tooling, never a production launcher. The
adapter preflights Linux unprivileged user, mount, and PID namespaces, creates an
ignored empty real `target` directory, and privately bind-mounts the exact
coordinator build root there. Nested private binds route temporary, Surefire,
trace, diagnostic, artifact, and distribution output to their exact session
roots while Maven and tests retain worktree-local lexical and canonical paths.
Adapter Maven arguments must never include `clean`, because frozen next's
historical clean plugin can destroy the authenticated mount topology.

The adapter preserves the frozen platform-effective `surefire.argLine` for
CDS, Mockito, heap, and macOS options, then adds a distinct direct-session
`lwjgl-${surefire.forkNumber}` path. Frozen next continues to own
`java.io.tmpdir=<worktree>/target/test-tmp`; both its lexical and canonical
values remain worktree-local, while recorded mount device/inode identities
prove the exact coordinator temp backing. Because root mapping would otherwise
change Java's home to `/root`, the adapter authenticates the outer UID's passwd
home against canonical `HOME` and appends that `user.home` to both Maven JVMs
and Surefire forks. Caller overrides of these channels are rejected.

An exact `unshare --fork --kill-child=KILL` supervisor owns a private PID 1.
PID 1 shell-natively publishes its host `NSpid` identity without remounting
`/proc`; the parent authenticates the supervisor's single child edge, both
PID/start identities, the private PID namespace, and their common mount
namespace at the ready/go barrier while proving its own `target` view is the
original empty non-mount directory. Teardown unmounts nested binds in reverse
order and requires both exact process identities to be absent or recycled
before removing only the same empty ordinary directory with `rmdir`. PID-1
exit contains detached and nested-namespace descendants; forced cleanup kills
the exact supervisor so `--kill-child=KILL` kills PID 1. The adapter does not
scan unrelated host processes. Normal and outer recovery never
recursively delete, follow, replace, read, or unlink a target link; an unsafe
target is preserved for inspection.

One frozen test intentionally rewrites the tracked
`docs/status/rewind-round-trip-gaps.md`. Baseline runs authenticate its exact
committed blob, archive both versions in session diagnostics, require the
current session's exact probe testcase outcome, and atomically restore only
that report before a valid final digest. Any second mutation, unsafe file type,
or archive/restore failure remains identity-invalid. Launcher `INT`/`TERM`
forces coordinator finalization first, so mandatory outer recovery restores
the authenticated report only after an `INVALID_IDENTITY_CHANGED` manifest;
restoration never makes that interrupted run certifying.

Adapter safety or cleanup failures arm a pinned tracked-report identity
tripwire before the coordinator's final digest. The launcher authenticates the
tripwire's run, reason, child status, adapter status, hash, and length, then
restores only the exact report preimage after finalization while leaving any
unsafe target untouched. Ordinary Maven failure with successful cleanup stays
`FAILED` and valid; parent evidence must authenticate launcher and cleanup
diagnostics as well as the manifest. A tripwire-arm failure, unrelated trigger,
or missing terminal marker is non-certifying. The launcher authenticates one
exact, line-anchored coordinator end marker whose run ID, manifest, state, and
valid fields agree with the recovered manifest; prefix collisions, wrong-run
markers, and duplicates are rejected. The exact frozen coordinator can
omit its end marker during a diagnosed `manifest.json.tmp` signal race, but
that shape is accepted only by the interruption safety fixture to prove
hygiene—never as baseline evidence.

The adapter self-test exercises supervisor and private-PID-1 start-time, PID
namespace, and common mount-namespace mismatches at ready/go and recovery.
Functional normal and forced-cleanup cases also prove that the same PID with
the same recorded start returns cleanup status 76 and preserves the exact
authenticated target, while a different start permits its exact `rmdir`.
Adapter/shim controls used to exercise those trust-boundary cases require the
exact self-test mode and publish a run-bound `frozen-next-test-seam.env` marker.
The launcher independently authenticates the complete inherited seam-variable
list and always marks such a run non-admissible, even when its coordinator
manifest is otherwise `PASSED` and valid. A seam variable without exact mode,
or a missing/mismatched seam marker, fails closed. Empty-valued seam variables
still activate quarantine; the self-test mechanically compares the adapter and
launcher inventories against every referenced test control and rejects marker
mode or variable-list mutation. Normal production evidence contains no seam
marker and remains eligible for admission.

## Complete Surefire outcome inventories

`Export-SurefireOutcomeInventory.ps1` converts one or more coordinator-owned
Surefire report roots into an ordinal-sorted TSV. The source-class inventory is
one fully qualified selected top-level class per line. A testcase covers a
selected root only when its XML `classname` is exactly that root or begins with
the exact `root + '$'` boundary. Thus a selected `example.Outer` may export
`example.Outer$Nested#method`, preserving that actual nested classname and
identity, while `example.Outerish$Nested` remains unselected. Selector roots
containing `$`, lookalike prefixes, duplicate nested identities, and roots with
neither exact nor nested testcase coverage are fatal. Naming-convention helpers
that execute no tests require a separately reviewed TSV with `class` and
non-empty `reason` columns.

Run the exporter from PowerShell so multiple report roots remain an array:

```powershell
& ./tools/testing/Export-SurefireOutcomeInventory.ps1 `
    -SourceClassInventory ./evidence/candidate-classes.txt `
    -ReportRoot @(
        ./evidence/candidate-ordinary/surefire-reports,
        ./evidence/candidate-guards/surefire-reports
    ) `
    -CanonicalWorktree /canonical/integration-worktree `
    -SessionRoot /canonical/agent-scratch/session-root `
    -RunId openggf-test-20260826-012345-abcdef12 `
    -EmptyHelperAllowlist ./evidence/candidate-empty-helpers.tsv `
    -OutputPath ./evidence/candidate-outcomes.tsv
```

### Authenticated explicit-source fallback

When a completed ordinary monolith is inventory-invalid because Surefire
discovers a nested class both independently and through its top-level Jupiter
owner, retain that invalid run unchanged. The first fallback is one complete
ordinary session selected by exact top-level source patterns, not a
deduplicated report and not a partition.

Construct the selector only after applying that tree's ordinary source includes
and effective POM excludes. Ordinal-sort the exact top-level FQCN roots, then
map each root mechanically from `com.openggf.Foo` to
`com/openggf/Foo.java`. The resulting generated-test-classes-relative file must
contain neither `$` nor wildcard characters and must be an ordinal bijection
with the root inventory. Store it at a canonical absolute path under an
owner-only task directory created by `agent-scratch`; record its SHA-256 and
line count before launch. For example:

```powershell
$taskRoot = agent-scratch new surefire-explicit-source
$selector = Join-Path $taskRoot 'ordinary.includes'
$roots = [string[]](Get-Content -LiteralPath ./evidence/candidate-classes.txt)
[Array]::Sort($roots, [StringComparer]::Ordinal)
$patterns = $roots | ForEach-Object { $_.Replace('.', '/') + '.java' }
[IO.File]::WriteAllText($selector, (($patterns -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
$selector = (Resolve-Path -LiteralPath $selector).Path
$selectorHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $selector).Hash.ToLowerInvariant()
$selectorCount = $patterns.Count
```

Make the selector an authenticated coordinator input before launch. Preserve
any existing runtime inputs and add the selector exactly once using
`[IO.Path]::PathSeparator`:

```powershell
$existingInputs = @($env:OPENGGF_RUNTIME_INPUTS -split [IO.Path]::PathSeparator |
    Where-Object { $_ })
$env:OPENGGF_RUNTIME_INPUTS = (@($existingInputs) + $selector) -join [IO.Path]::PathSeparator
$mavenArguments = @('-Dmse=relaxed', "-Dsurefire.includesFile=$selector", 'test')
```

The Maven invocation must contain exactly one canonical absolute
`-Dsurefire.includesFile=<selector>` property. Reject `-Dtest`,
`-Dsurefire.includes`, a second `surefire.includesFile`, group/excluded-group
properties, excludes, or any other caller selector override. `-Dtest` is
specifically forbidden because it replaces the POM's ordinary includes and
excludes. The selector must be present exactly once in
`OPENGGF_RUNTIME_INPUTS`, so the coordinator records pre/post hashes of the
same file.

The exporter can preflight the static mapping, invocation, and runtime-input
contract. Put the exact Maven argument vector in a UTF-8 file, one argument per
line, and supply:

```powershell
& ./tools/testing/Export-SurefireOutcomeInventory.ps1 `
    -SourceClassInventory ./evidence/candidate-classes.txt `
    -SelectorPatternInventory $selector `
    -MavenArgumentInventory ./evidence/candidate-maven-arguments.txt `
    -RuntimeInputs $env:OPENGGF_RUNTIME_INPUTS `
    -ReportRoot ./evidence/candidate-ordinary/surefire-reports `
    -OutputPath ./evidence/candidate-outcomes.tsv
```

Before accepting this fallback, capture the exact effective POM once without
and once with the selector property. Select the ordinary
`maven-surefire-plugin` execution (normally `default-test`). It must have no
configured `<includes>` because `includesFile` appends to configured includes;
its ordered excludes, groups, excluded groups, fork count, and fork-reuse value
must be identical in both effective POMs. Preserve those parsed values with the
selector hash/count evidence. The exporter enforces and prints that comparison:

```powershell
& ./tools/testing/Export-SurefireOutcomeInventory.ps1 `
    -SourceClassInventory ./evidence/candidate-classes.txt `
    -EffectivePomPath ./evidence/candidate-effective-ordinary.xml `
    -SelectorEffectivePomPath ./evidence/candidate-effective-selector.xml `
    -SurefireExecutionId default-test `
    -ReportRoot ./evidence/candidate-ordinary/surefire-reports `
    -OutputPath ./evidence/candidate-outcomes.tsv
```

This static/effective-POM preflight supplements, but does not replace, the real
frozen-POM proof: one exact top-level root containing `@Nested` must execute its
nested testcase exactly once, a class named by an effective POM exclude must
emit no report, no unrelated report may appear, and the coordinator manifest
must authenticate the selector runtime input. Repeat that proof for each frozen
parent and the later merged candidate.

When `pwsh -File` must carry multiple paths, join them with
`[IO.Path]::PathSeparator` (`:` on POSIX, `;` on Windows). XML is loaded with
DTDs prohibited and no external resolver. Malformed XML, duplicate testcase
identities, unselected reported classes, missing selected classes, and red
outcomes without a deterministic type/body signature are fatal. A testcase
with more than one semantic outcome element across `failure`, `error`,
`skipped`, and `disabled` is also invalid. If any red testcase is present,
`CanonicalWorktree`, `SessionRoot`, and `RunId` are all required.

The export schema is:

```text
identity	class	method	outcome	red_kind	exception_type	normalized_message	red_body_bytes	red_body_sha256	report
```

Decoded `identity` is the exact XML `classname#name` without trimming either
attribute, including a parameterized testcase's complete Surefire `name` and
any boundary whitespace. Outcomes are exactly
`PASS`, `FAILURE`, `ERROR`, or `SKIPPED`. Red bodies have LF line endings and
replace the supplied worktree, session root, run ID, and ISO-8601 timestamps
before their complete UTF-8 byte length and streaming SHA-256 are recorded.
Zoned, numeric-offset, and unzoned ISO-8601 timestamps normalize to the same
`<TIMESTAMP>` token. A zero-byte red body is valid and uses SHA-256
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.

All textual TSV values use a reversible escape layer before joining columns:
`\\` is backslash, `\q` is a double quote, `\t` is tab, `\r` is carriage
return, `\n` is line feed, and `\s` protects a boundary space that an import
parser could otherwise trim. Consumers decode and validate these escapes.
Therefore an actual line feed encoded as `\n` remains distinct from a literal
backslash followed by `n`, which encodes as `\\n`; likewise, a quote encoded
as `\q` remains distinct from a literal backslash followed by `q`, which
encodes as `\\q`. Wire rows contain no literal double quote for quote-aware
TSV import to reinterpret.

Compare the candidate against both frozen parents with:

```powershell
& ./tools/testing/Compare-SurefireOutcomeInventory.ps1 `
    -ParentInventoryPath @(
        ./evidence/frozen-next-outcomes.tsv,
        ./evidence/frozen-develop-outcomes.tsv
    ) `
    -CandidateInventoryPath ./evidence/candidate-outcomes.tsv `
    -ReviewedRemovalPath ./evidence/reviewed-removals.tsv `
    -OutputPath ./evidence/parent-candidate-comparison.tsv
```

The optional reviewed-removal TSV has `identity` and non-empty `reason`
columns. It exempts only that exact parent identity when it is absent from the
candidate. The comparison schema is:

```text
identity	baseline_source	baseline_outcome	candidate_outcome	baseline_red_signature	candidate_red_signature	classification	owner	disposition
```

Each parent source that owns an identity receives its own row, sorted by
decoded identity and then source, so the outcome, signature, classification,
and requested isolated rerun remain bound to one exact frozen parent. An
identity owned by no parent receives one row whose `baseline_source` is
`CANDIDATE_ONLY` and whose baseline outcome is `ABSENT`. Missing candidate
identities are emitted as `ABSENT`. Parent PASS regressions,
unapproved removals, red-kind changes, and same-kind red signature changes make
the command nonzero after it writes the report. A same-kind signature change
is `RED_SIGNATURE_CHANGED_REQUIRES_PAIRED_RERUN`; rerun that exact identity in
the frozen parent and candidate under the same selector/environment, then
record its owner and disposition in the review ledger.

Inventory consumption requires the exact export schema and reversibly decodes
every field. It verifies `identity == class + '#' + method`, exact
`FAILURE`/`failure` and `ERROR`/`error` pairing, empty red metadata on non-red
rows, a non-negative body byte count, a 64-digit lowercase SHA-256, and the
canonical empty-body hash when the byte count is zero.

## Deterministic OOM-safe partitions

If the attempted monolithic suite cannot produce a complete inventory, create
one ordinal union map for the three trees:

```powershell
& ./tools/testing/New-SurefirePartitionMap.ps1 `
    -NextClassInventory ./evidence/frozen-next-classes.txt `
    -DevelopClassInventory ./evidence/frozen-develop-classes.txt `
    -CandidateClassInventory ./evidence/candidate-classes.txt `
    -SlotSize 75 `
    -OutputPath ./evidence/surefire-partitions.tsv
```

The partition schema is:

```text
slot	union_selector	next_selector	develop_selector	candidate_selector
```

Slots are stable, numbered, and contain at most 75 ordinal union classes. Each
tree selector is filtered to the classes present in that tree, so parent-only
and candidate-only tests do not create false selector failures. Run every
non-empty per-tree selector through its own coordinator session and aggregate
only after every executable class appears in exactly one successful slot.

A partial monolithic run is never a suite result. Retain it as failed-session
evidence; only a complete monolithic inventory or the complete deterministic
partition aggregate may be reported as the suite outcome.

The literal fixture suite for all three tools is:

```powershell
pwsh -NoProfile -File tools/testing/Test-SurefireOutcomeInventory.ps1
```
