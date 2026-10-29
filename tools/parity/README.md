# Stock parity reporting

Inspect declared original-game obligations and the evidence needed to resolve
them. The initial catalogue is a curated, partial pilot across S1, S2 and S3K.
It is not a census of original behavior. A generated inventory preserves 801
original dispatch entries as unresolved semantic placeholders. One registered
producer measures three existing native trace gates. A report without a fresh
measurement still shows no qualification.

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

These fractions describe declared obligations and unresolved inventory gaps.
All initial scopes are open
and the true original-game denominator is unknown. Empty denominators have no
ratio. The core rejects closed scopes in schema 1 and always leaves whole-game
parity unavailable. It does not average pixels, frames, entities or test counts.

Skipped, missing, incomplete, stale, reference-limited, synthetic, unregistered,
insufficient-authority and post-divergence observations cannot verify an obligation.
Accepted discrepancy annotations do not turn a mismatch into a pass.

## Supply producer observations

Normalized observations are an integration contract for reviewed measurement
producers. The native trace producer below creates them in memory.
Do not hand-author a passing observation and describe it as executed evidence.
The unit tests construct isolated producers and synthetic inputs to verify the
reporter's arithmetic and rejection behavior. They do not measure OpenGGF.

## Measure the existing native trace gates

Set `S1_ROM`, `S2_ROM` and `S3K_ROM` to absolute paths of the original ROMs
listed in the repository guidance. The producer verifies their hashes.

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/parity/measure_native.py \
  --s1-rom "$S1_ROM" --s2-rom "$S2_ROM" --s3k-rom "$S3K_ROM"
```

Use `--games s1` to measure one game. Supply its ROM argument. Use `--format json`
for structured case diagnostics and first-divergence details.

The producer uses the existing Maven queue and exact `replayMatchesTrace`
selectors for S1 GHZ1, S2 EHZ1 and the S3K AIZ-to-HCZ-entry fixture. It verifies
the intended source, original ROM, complete reference files, movie settings,
Maven/XML verdicts, JUnit report ownership and the driver's gate-completion
marker. Injected trace overrides are rejected. It does not change replay input,
physics, comparison tolerances or the existing assertions.

Each observation is one completed existing trace gate. Recorded rows and
gameplay comparison counts remain diagnostic units. Non-gameplay rows can be
driven without producing gameplay comparisons, so the counts need not match.
The gate can establish agreement of its recorded state and animation fields.
It cannot establish RGB pixels, all objects, all routes or whole-game parity.

A completed gate with native differences remains a mismatch even though JUnit
reports an assertion failure. A clean aborted prefix remains incomplete.
Warnings or missing advertised auxiliary schemas remain reference-limited.
Raw logs, XML and trace reports live in an owned temporary directory below
`target/parity-native` and are removed after the completed summary is derived.
No older default report directory is deleted or reused. The final report goes
to standard output; no reusable qualification receipt is created.

The recorded movies use a historical BK2 header field named `SHA1` for their
32-character MD5. The producer checks that value as MD5 and independently checks
the original ROM's full SHA-1. It pins the whole movie, Genplus-gx core and
BizHawk 2.11 provenance rather than trusting the field name.

## Regenerate the original dispatch inventory

```bash
python3 tools/parity/native_inventory.py \
  --s1-rom "$S1_ROM" --s2-rom "$S2_ROM" --s3k-rom "$S3K_ROM" \
  --check tools/parity/reference_inventory.json
```

The generator reads big-endian code pointers directly from four original-ROM
tables. Their 140, 220, 256 and 185 entries retain game, ROM, table and native ID
identity. Aliases stay separate. These are 801 dispatch entries, not 801 distinct
objects or an exhaustive inventory of behavior. Dynamic children, fixed helpers
and internal state-machine routines remain outside this census.

The catalogue pins the generated artifact's bytes. Structural validation rejects
missing entries, changed table spans, invalid pointers and incorrect alias data.
Actual regeneration with verified ROMs establishes the pointer values. A new
generator revision requires regeneration and an explicit catalogue-pin update.

Every entry contributes an unresolved inventory placeholder. A measurement
cannot discharge that placeholder, and adding one behavior facet does not
remove the remaining semantic gap. Placeholder replacement requires reviewed
semantic inventory work. The JSON report retains every placeholder; Markdown
shows the aggregate count to keep the report readable.

## Observation envelope

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
defines the broader original inventories, native RGB/PCM adapters and scope
reconciliation still needed for project-wide measurements.
