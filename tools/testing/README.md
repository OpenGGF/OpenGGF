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

Run the exporter from PowerShell so multiple report roots remain an array:

```powershell
& ./tools/testing/Export-SurefireOutcomeInventory.ps1 `
    -SourceClassInventory ./evidence/candidate-classes.txt `
    -ReportRoot @(
        ./target/surefire-reports
    ) `
    -OutputPath ./evidence/candidate-outcomes.tsv
```

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
