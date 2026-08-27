# Testing utilities

OpenGGF uses Maven directly. Build and test output stays below the current
worktree's `target/` directory.

Install the repository hooks once per worktree:

```bash
tools/testing/install-hooks.sh
```

PowerShell uses `tools/testing/install-hooks.ps1`.

## Complete Surefire outcome inventories

The PowerShell utilities in this directory export, validate, partition, and
compare complete Surefire outcome inventories:

- `Export-SurefireOutcomeInventory.ps1` converts one or more report roots into
  an ordinal-sorted TSV.
- `Compare-SurefireOutcomeInventory.ps1` compares candidate outcomes with one
  or more parent inventories.
- `New-SurefirePartitionMap.ps1` creates deterministic class partitions for a
  suite that cannot complete as one monolithic run.
- `Test-SurefireOutcomeInventory.ps1` exercises the inventory contract.

An inventory source file contains one fully qualified selected top-level class
per line. A testcase belongs to that root when its XML `classname` is exactly
the root or begins with the exact `root + '$'` boundary. Duplicate testcase
identities are fatal unless a reviewed cardinality file declares the exact
identity, count, and reason.

An authenticated explicit-source export is atomic. Retain all of these
artifacts from the run being exported:

- the ordinal-sorted top-level source-class inventory;
- its exact ordinal-bijective slash-path selector file, supplied to Maven by
  one canonical absolute `surefire.includesFile` argument;
- the exact Maven argument vector, one argument per line;
- the effective POM generated with the same profiles and property overrides;
  and
- the exact `OPENGGF_RUNTIME_INPUTS` value used for the run. It contains the
  canonical selector exactly once, the effective-POM path exactly once for a
  direct capacity override, and the reviewed repeated-identity cardinality
  file exactly once when that file is used.

Direct Maven may use an explicit `surefire.argLine` only as a capacity
override. The resolved value must preserve the effective POM's exact CDS and
Mockito-agent arguments, followed only by one `-Xmx` argument. The effective
project property must prove the same capacity and standard property wiring;
the selected Surefire execution must prove the fully resolved exact value and
then retain Maven's fork-local temp and LWJGL properties. Unresolved runtime
arguments, unsupported property placeholders, or mismatched evidence fail
closed.
Unlike historical managed-session evidence, direct mode neither supplies nor
accepts invented adapter-owned `user.home`, LWJGL, session-root, or run-id
suffixes.

For example, this records a truthful one-fork/3-GiB direct invocation. The
class and selector inventories must already contain the complete selected
suite described above:

```powershell
$worktree = (Resolve-Path .).Path
$evidence = Join-Path $worktree 'target/surefire-inventory-evidence'
$classes = Join-Path $evidence 'ordinary-classes.txt'
$selector = (Resolve-Path (Join-Path $evidence 'ordinary.includes')).Path
$effectivePom = Join-Path $evidence 'ordinary-effective-pom.xml'
$argumentInventory = Join-Path $evidence 'ordinary-maven-arguments.txt'
$localRepository = (& mvn -Dmse=off help:evaluate `
    -Dexpression=settings.localRepository -q -DforceStdout).Trim()
$mockitoAgent = Join-Path $localRepository `
    'org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar'
$capacityArgLine = "-Xshare:off -javaagent:`"$mockitoAgent`" -Xmx3g"
$mavenArguments = @(
    '-Dmse=off'
    "-Dsurefire.argLine=$capacityArgLine"
    "-Dsurefire.includesFile=$selector"
    'test'
)

New-Item -ItemType Directory -Force -Path $evidence | Out-Null
& mvn -Dmse=off "-Dsurefire.argLine=$capacityArgLine" `
    "-Dsurefire.includesFile=$selector" help:effective-pom `
    "-Doutput=$effectivePom"
[IO.File]::WriteAllLines($argumentInventory, $mavenArguments,
    [Text.UTF8Encoding]::new($false))
$env:OPENGGF_RUNTIME_INPUTS = @($selector, $effectivePom) -join `
    [IO.Path]::PathSeparator
& mvn @mavenArguments

& ./tools/testing/Export-SurefireOutcomeInventory.ps1 `
    -SourceClassInventory $classes `
    -SelectorPatternInventory $selector `
    -MavenArgumentInventory $argumentInventory `
    -RuntimeInputs $env:OPENGGF_RUNTIME_INPUTS `
    -EffectivePomPath $effectivePom `
    -ReportRoot (Join-Path $worktree 'target/surefire-reports') `
    -DirectMaven `
    -CanonicalWorktree $worktree `
    -OutputPath (Join-Path $evidence 'ordinary-outcomes.tsv')
```

Run the exporter from PowerShell so multiple report roots remain an array:

```powershell
& ./tools/testing/Export-SurefireOutcomeInventory.ps1 `
    -SourceClassInventory ./evidence/candidate-classes.txt `
    -ReportRoot @(
        ./target/surefire-reports
    ) `
    -DirectMaven `
    -CanonicalWorktree (Resolve-Path .).Path `
    -OutputPath ./evidence/candidate-outcomes.tsv
```

`-DirectMaven` makes expected-red reports usable without inventing coordinator
session values. It requires the explicit canonical worktree and accepts report
roots only at or below that worktree's `target/surefire-reports`; volatile
worktree paths are normalized to `<WORKTREE>`. Direct mode rejects symbolic
links and reparse points in the worktree/report ancestry and anywhere below a
report root before reading XML, so lexical containment cannot hide an external
report tree. Historical managed-session
evidence remains supported without `-DirectMaven`, where any red outcome still
requires the complete `CanonicalWorktree`, `SessionRoot`, and `RunId` provenance
set.

The export schema is:

```text
identity class method outcome red_kind exception_type normalized_message red_body_bytes red_body_sha256 report
```

Outcomes are `PASS`, `FAILURE`, `ERROR`, or `SKIPPED`. Red bodies have LF line
endings before their complete UTF-8 byte length and streaming SHA-256 are
recorded. Textual TSV values use the reversible escape layer implemented by
the exporter and validator.

Compare candidate and parent inventories with:

```powershell
& ./tools/testing/Compare-SurefireOutcomeInventory.ps1 `
    -ParentInventoryPath ./evidence/parent-outcomes.tsv `
    -CandidateInventoryPath ./evidence/candidate-outcomes.tsv `
    -OutputPath ./evidence/parent-candidate-comparison.tsv
```

If a monolithic suite cannot produce a complete inventory, create a stable
union map and run every non-empty per-tree selector:

```powershell
& ./tools/testing/New-SurefirePartitionMap.ps1 `
    -NextClassInventory ./evidence/next-classes.txt `
    -DevelopClassInventory ./evidence/develop-classes.txt `
    -CandidateClassInventory ./evidence/candidate-classes.txt `
    -SlotSize 75 `
    -OutputPath ./evidence/surefire-partitions.tsv
```

A partial monolithic run is not a suite result. Retain it as failed-run
evidence; only a complete monolithic inventory or complete deterministic
partition aggregate may be reported.

Run the literal fixture suite with:

```powershell
pwsh -NoProfile -File tools/testing/Test-SurefireOutcomeInventory.ps1
```

## Trace fixture validation

The remaining trace validation scripts in this directory validate committed
metadata, run manifests, timing sidecars, and fixture compression. They are
independent of the Maven launcher and continue to operate on caller-supplied
paths.
