# Stock parity reporting

Inspect declared original-game obligations and the evidence needed to resolve
them. The initial catalogue is a curated, partial pilot across S1, S2 and S3K.
It is not a census of original behavior. The producer registry is empty.
The current tool therefore reports no engine qualification.

```bash
python3 tools/parity/report.py report --format markdown
python3 tools/parity/report.py inventory --format json
python3 -m unittest discover -s tools/parity -p 'test_*.py'
```

The command performs no gameplay execution, capture or Maven invocation.
It needs neither ROMs nor optional disassembly checkouts. Successful report
calculation exits with `0`, even when every obligation is unresolved. Invalid
inputs exit with `2`. Exit `0` is not a parity pass.

## Read the counts

Each obligation describes one contextual ROM contract and its required cases.
Every required case must have eligible exact evidence to verify the obligation.
A valid mismatch remains visible even when another case is missing.

The report retains three fractions with their numerators and denominators.

| Fraction | Calculation |
| --- | --- |
| Verified declared obligations | Verified / declared |
| Resolved declared obligations | (Verified + mismatched) / declared |
| Agreement among resolved obligations | Verified / (verified + mismatched) |

These fractions describe declared obligations only. All initial scopes are open
and the true original-game denominator is unknown. Empty denominators have no
ratio. The core rejects closed scopes in schema 1 and always leaves whole-game
parity unavailable. It does not average pixels, frames, entities or test counts.

Skipped, missing, incomplete, stale, reference-limited, synthetic, unregistered,
insufficient-authority and post-divergence observations cannot verify an obligation.
Accepted discrepancy annotations do not turn a mismatch into a pass.

## Supply producer observations

Normalized observations are an integration contract for future reviewed
measurement producers. There are no production adapters in this delivery.
Do not hand-author a passing observation and describe it as executed evidence.
The unit tests construct isolated producers and synthetic inputs to verify the
reporter's arithmetic and rejection behavior. They do not measure OpenGGF.

```bash
python3 tools/parity/report.py report \
  --evidence target/parity/observations.json --format json
```

Each schema-1 evidence envelope contains these fields.

| Field | Required meaning |
| --- | --- |
| `source` | Full commit, actual-input `fingerprint` and explicit `dirty` boolean |
| `catalogue_digest` | Canonical SHA-256 of the complete catalogue |
| `producer` | Producer `id`, `version` and `comparator_sha256` |
| `synthetic` | Explicit boolean; `true` never resolves parity |
| `observations` | Observation records keyed by obligation and required case |

Each observation declares `obligation_id`, `case_id`, `contract_digest`,
`rom_sha1`, `status`, `evidence_kind`, `causal_status`, `expected`, `compared`,
`unit` and `artifact_sha256`. Optional numeric `metrics` retain native units.
Statuses are `exact`, `mismatch`, `skipped`, `incomplete` and
`reference-limited`. Causal status is `isolated`, `first-divergence` or
`post-divergence`. Only mismatches can identify the first divergence.

The obligation's `case_contracts` pins each case's expected extent, unit and
permitted evidence kinds. A producer cannot shorten both `expected` and
`compared` to turn a matching prefix into complete evidence. Exact and mismatch
verdicts require the complete pinned extent.

The canonical registry at `tools/parity/producers.json` contains reviewed
producer declarations with `id`, repository-relative `adapter_path`,
`adapter_sha256` and `evidence_kinds`. The core verifies registered adapter bytes.
The observation kind must be permitted by both the producer and the case.
Native state, RGB, PCM and independently derived ROM expectations are distinct
kinds. Engine self-consistency and implementation metadata cannot establish
native agreement.

Matching registry entries validate the origin of a claim by convention and code
identity. They do not cryptographically attest execution or authenticate a
capture. A reviewed adapter must verify effective configuration, original ROM,
input/reference hashes, complete artifacts and unchanged before/after source
identity. Reference emulator/core settings and movie identity belong in its
contract. Adding a registry entry requires delivering and reviewing that adapter.

## Bind the source identity

Parity producers use `source_identity(root, parsed_producers)` in `report.py`.
The producer argument includes registered adapters outside the fixed input roots.
The function hashes actual file
bytes, including dirty and untracked executable inputs, rather than relying on
HEAD. It covers source/tests, fixtures, POM/launchers, examples, build settings,
test tooling, parity definitions, hooks and registered adapter code. It excludes
build outputs and Python bytecode. Unsupported source symlinks fail closed.

Producer evidence needs matching commit and fingerprint. Old observations
become stale after relevant source changes. Deriving the report is inexpensive
relative to executing the measurements. Obtaining fresh current evidence may
require a new producer run; this tool grants no selective reuse policy.

## Compare revisions

Use invocation-owned reports while reviewing two candidates.

```bash
python3 tools/parity/report.py compare \
  --before /tmp/parity-before.json --after /tmp/parity-after.json \
  --format markdown
```

The comparison reports transitions only for unchanged obligation contracts.
Added, removed and changed obligations are separate. Removed mismatches receive
an explicit warning. A changed reference, mask, expected case, extent or unit
cannot count as behavioral progress against the old contract.

The tool writes only to standard output. It creates no pass cache, validation
receipt or diagnostic archive. Keep measurement inputs under invocation-owned
`target/` paths or outside the repository, and follow the producing runner's
existing cleanup requirements. Preserve only compact reviewed conclusions in
the task's normal engineering record.

The [architecture design](../../docs/architecture/designs/2026-10-29-quantitative-stock-parity.md)
defines the generated original inventories, native trace/RGB/PCM adapters and
scope reconciliation still needed for project-wide measurements.
