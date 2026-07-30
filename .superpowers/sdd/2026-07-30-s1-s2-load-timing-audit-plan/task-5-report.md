# Task 5 report: mandatory native capture audit

## Outcome

Canonical Sonic 1 and Sonic 2 trace, complete-run, named-run, and special-stage
captures publish dynamic-art audit data without a positive opt-in:

- every stored level row has exactly one PLC state and one DPLC envelope;
- every stored special-stage row has exactly one DPLC envelope and no PLC
  capability;
- prefix and lag rows receive DPLC heartbeats;
- terminal observer output is merged into the final stored row by a one-row
  buffer;
- named-run gap transitions are published chronologically in
  `dynamic_art_gap_transitions`, including boundary and trailing gaps;
- each following segment arm requires an empty observer ledger;
- metadata advertises the applicable PLC/DPLC capabilities and advances only
  for audited output.

Canonical `Capture` entrypoints require retail ROM bytes and reject `null`
before any capture or publication work begins. Deliberate legacy reproduction
is isolated behind explicitly named `CaptureScratchLegacy` entrypoints whose
documentation forbids publication.

## Implementation

- Added `DynamicArtCaptureRowBuffer` to defer one complete physics/aux row,
  preserve row ordering, attach terminal envelopes to the pending final row,
  and flush exactly once.
- Integrated `S1DynamicArtObserver` and `S2DynamicArtObserver` across standalone
  and run capture runners, including reset/discard handling in S2.
- Integrated DPLC-only audit output into S1/S2 special-stage segments.
- Extended run manifests with schema-2
  `dynamic_art_gap_transitions` while preserving exact schema-1 output for
  explicit scratch callers.
- Updated metadata schema versions and capabilities for audited level and
  special-stage output.
- Extended fake hosts with CPU-register reads and address-specific execute
  callbacks so tests exercise production observer lifecycles.

The commit also includes the previously staged, agent-authored S2
special-stage recorder, run-objects observer, and tests. Mandatory DPLC-only
special-stage capture depends on that same trace-regeneration prerequisite.

## Boundary ownership correction

Round-one review found that an observer stayed segment-armed during the
`FrameAdvance` which changed game mode. The runner then terminal-forwarded
callbacks raised by that boundary advance into the prior segment. It also
drained gaps only at a later arm, so a final unrepresented gap could be lost.

The corrected lifecycle is two-phase:

1. Immediately before an armed advance, the observer snapshots the buffered
   edge count and exact ledger and records the run-wide movie cursor.
2. A normal stored row publishes the advance and clears the snapshot.
3. If the advance closes the segment, only edges which existed before the
   snapshot become `terminal_forwarded` edges on the pending final row.
4. Boundary callbacks remain buffered for manifest publication. New boundary
   submissions are reclassified to `run_gap`, their descriptors and
   fingerprints are rebuilt consistently, and all boundary edges use the
   run-wide movie cursor.
5. Run finalization drains the final gap before formatting the manifest.

This preserves a submission observed before close on the final row while
allowing its completion on the boundary advance to retire the same stable
transfer id in `dynamic_art_gap_transitions`. S1 complete-run capture without
a run id now emits its manifest whenever gap transitions are nonempty.

## TDD evidence

Initial mandatory-audit RED:

```text
tools/bizhawk-headless/test.sh --filter 'S1TraceCaptureRunner emits mandatory' --jobs 1
FAIL: expected 4 DPLC envelopes, observed 0
```

Round-one boundary RED:

```text
tools/bizhawk-headless/test.sh --filter 'RunCaptureRunner' --no-gates --jobs 1
S1 boundary completion: expected 0 completed segment edges, observed 1
S1 trailing gap: expected 2 transitions, observed 0
S2 terminal-boundary submission: expected 0 segment submissions, observed 1
S2 trailing reload gap: expected 2 transitions, observed 0
```

Round-one focused GREEN:

```text
BIZHAWK_HOME=... tools/bizhawk-headless/test.sh \
  --filter 'CaptureRunner' --no-gates --jobs 4
70 passed, 0 failed, 0 skipped
```

Special-stage and publication-boundary GREEN:

```text
BIZHAWK_HOME=... tools/bizhawk-headless/test.sh \
  --filter 'special-stage' --no-gates --jobs 4
20 passed, 0 failed, 0 skipped
```

Canonical CLI GREEN:

```text
BIZHAWK_HOME=... S1_ROM_PATH=... S2_ROM_PATH=... S3K_ROM_PATH=... \
tools/bizhawk-headless/test.sh --filter 'TraceCli' --no-gates --jobs 4
32 passed, 0 failed, 0 skipped
```

Final all-ROM no-gates GREEN:

```text
BIZHAWK_HOME=... S1_ROM_PATH=... S2_ROM_PATH=... S3K_ROM_PATH=... \
tools/bizhawk-headless/test.sh --no-gates --jobs 4 --slowest 20
488 passed, 0 failed, 0 skipped
```

## Representative real-ROM gate status

Before fleet regeneration, S1 GHZ1 and S2 EHZ1 canonical captures completed
without observer arm, ledger, or terminal-forwarding failures. Their
differential gates then failed only at the expected whole-file
`aux_state.jsonl` hashes because the checked-in fixtures predate mandatory
audit:

- S1 expected `026794...184a86`, captured `2de5a2...4ccc3`;
- S2 expected `5522e7...122dc2`, captured `baad7f...6d3a7`.

Those fixture hash updates belong to subsequent fleet-regeneration tasks and
do not affect Task 5's zero-failure/zero-skip no-gates acceptance suite.
