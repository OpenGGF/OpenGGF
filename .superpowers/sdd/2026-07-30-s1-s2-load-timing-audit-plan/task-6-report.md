# Task 6 report: strict Java audit fixture validation

## Outcome

Java now reads the native S1/S2 player-art audit as a typed,
comparison-only lifecycle and rejects malformed advertised fixtures before
replay:

- `dynamic_art_transfer_state_per_frame_v1` requires exactly one envelope on
  every stored physics row, including prefix, lag, first, and terminal rows;
- unknown `event` values fail closed when that capability is advertised,
  while the recorder's known generic native snapshot events retain their
  lossless `StateSnapshot` representation;
- requests enforce explicit ROM/RAM source sentinels and hardware address
  domains;
- segment validation starts from an independently empty ledger, validates
  callback order, contiguous zero-based logical cursors, strictly increasing
  run-wide edge ordinals, never-reused transfer identities,
  submission/completion pairing, terminal forwarding, and each published
  outstanding-id ledger;
- S1 submissions are ordered ROM-backed batches and every completion is the
  physical `$C800 -> VRAM $F000`, `$2E0`-byte staging transfer rather than a
  fabricated repetition of the submitted ROM DPLC batch;
- S2 ROM/RAM completion batches must match their submissions exactly;
- `rom_callback_pc` is validated against the pinned game/phase profile but is
  absent from the comparison view and event summary;
- descriptor fingerprints and ledger hashes reproduce the recorder's exact
  big-endian `ODAT`/`ODAL` schema-v1 SHA-256 inputs.

`StoredPhysicsFrameDomain` scans plain or gzip physics payloads without
interpreting game-specific columns and requires contiguous zero-based rows.
The metadata-only run-interior path and both S1/S2 special-stage loaders use
that scanner, so special-stage audit completeness is checked despite their
different CSV schemas.

Run manifests now accept legacy schema 1 only with the historical omission
and parse schema 2's mandatory `dynamic_art_gap_transitions`. Validation
checks segment range/transition adjacency, before/after ledgers and hashes,
separate segment/gap cursor domains, complete gap lifecycles, run-wide
ordinal and transfer identity, and an empty post-gap ledger only before a
following segment arms. A true movie end may retain real pending ROM work
whether its submission appeared on the final segment row or in the trailing
gap.

## Implementation notes

- Added `DynamicArtTransfer` as the focused immutable model/validator rather
  than expanding the already-large `TraceEvent` parser with lifecycle and
  fingerprint machinery.
- Added typed `TraceEvent.DynamicArtTransferState` dispatch and a strict
  advertised-capability parse mode. The explicit known-generic event allowlist
  covers `state_snapshot`, `cursor_state`, `slot_dump`, `s2_tornado_state`,
  and `cnz_slot_machine_state`; genuinely unknown event names still fail.
- Added a shared `LifecycleIdentity` for segment/gap traversal so run-wide
  monotonic ordinals and transfer-id allocation are validated in publication
  order rather than as disconnected sets.
- Kept validation and manifest consumption read-only. No parser or validator
  references gameplay, renderer, PLC, DMA, or hardware-timing authority.
- Java additionally bounds source/callback addresses to the Mega Drive's
  24-bit domain and VRAM destinations to 16 bits; valid native fixtures are
  unchanged.

## TDD evidence

Initial focused RED:

```text
mvn -Dmse=off \
  -Dtest=TestDynamicArtTransferTrace,TestLoadQueueTraceComparison,TestTraceRunManifest,TestTraceRunReplayWalkerControlFlow \
  test

BUILD FAILURE
21 compilation errors for the absent typed event, model, frame-domain,
manifest schema-2, and lifecycle APIs.
```

Initial required focused GREEN on JDK 21:

```text
mvn -Dmse=off \
  -Dtest=TestDynamicArtTransferTrace,TestLoadQueueTraceComparison,TestTraceRunManifest,TestTraceRunReplayWalkerControlFlow \
  test

Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Review round 1 corrections

The review regressions were added before their fixes. The first run failed
seven new assertions covering the known generic native event mixture,
unconditional S1 phase domains, pending final gaps, run-wide identity and
ordinal order, contiguous cursors, and mixed cursor-domain fields:

```text
mvn -Dmse=off \
  -Dtest=TestDynamicArtTransferTrace,TestLoadQueueTraceComparison,TestTraceRunManifest,TestTraceRunReplayWalkerControlFlow \
  test

Tests run: 62, Failures: 7, Errors: 0, Skipped: 0
BUILD FAILURE
```

After the corrections, the same focused selection passed:

```text
mvn -Dmse=off \
  -Dtest=TestDynamicArtTransferTrace,TestLoadQueueTraceComparison,TestTraceRunManifest,TestTraceRunReplayWalkerControlFlow \
  test

Tests run: 62, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The focused test now loads representative mixed-event aux data and invokes
the real S1 loader with plain physics/aux payloads and the real S2 loader with
gzip payloads. Both loaders prove advertised completeness succeeds and a
missing heartbeat fails.

Adjacent loader compatibility remained green:

```text
mvn -Dmse=off \
  -Dtest=TestTraceDataHardwareTiming,SpecialStageTraceFrameTest,TestS1SpecialStageTraceParsing,TestS2SyntheticRunFixture \
  test

Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Maven reported Java `21.0.11`. The validate phase also printed the existing
non-fatal hook-install warning because the shared worktree Git config is
read-only in the sandbox.
