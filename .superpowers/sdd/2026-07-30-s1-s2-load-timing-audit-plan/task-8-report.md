# Task 8 report: compare DPLC envelopes without authority

## Result

Implemented read-only, zero-tolerance comparison of validated
`dynamic_art_transfer_state` envelopes against immutable production
`DynamicArtDiagnosticsSnapshot` values.

- `DynamicArtSpecialStageComparator` exposes comparison only. It owns no
  stepping, lifecycle, submission, completion, or runtime-owner API.
- All emitted frontier fields use the `dynamic_art.*` namespace.
- Edge order, request order, lifecycle identity, publication coordinates,
  terminal forwarding, and the outstanding-transfer ledger compare exactly.
- `rom_callback_pc` remains validated fixture evidence and is deliberately
  absent from the comparison field set.
- Ordinary replay, live replay, metadata-only run rows, and the S1/S2
  special-stage harnesses capture production snapshots after production-owned
  stepping. Lag and terminal rows receive DPLC-only comparisons when ordinary
  gameplay fields are intentionally omitted.
- Legacy fixtures without the capability retain their prior comparison
  policies and do not gain synthetic DPLC expectations.

## TDD evidence

The focused tests were introduced before their corresponding implementation:

1. Comparator tests failed to compile because
   `DynamicArtSpecialStageComparator` did not exist.
2. DPLC-only binder publication failed because `compareDynamicArt` did not
   exist.
3. Metadata-only run-row comparison failed because the walker seam did not
   exist.
4. Special-stage trace accessor tests failed because the singular typed
   accessor did not exist.
5. Live lag-row tests failed because the comparator had no immutable snapshot
   supplier seam.

Each failure was made green with the smallest owning implementation.

## Verification

JDK:

```text
Apache Maven on Java 21.0.11
```

Focused comparator, parser, live, lifecycle, walker, ghost-isolation, and
authority matrix:

```text
mvn -Dmse=off \
  -Dtest=TestDynamicArtDiagnosticsComparator,TestDynamicArtTransferTrace,TestLiveTraceComparatorObserver,TestS1S2PlcComparisonOnlyGuard,TestHardwareTimingAuthorityGuard,TestTraceRunReplayWalkerControlFlow,TestDynamicArtLifecycleService,TestDynamicArtGhostIsolation \
  test

Tests run: 103, Failures: 0, Errors: 0, Skipped: 0
```

Final comparator/authority ratchet after cleanup:

```text
mvn -q -Dmse=off \
  -Dtest=TestDynamicArtDiagnosticsComparator,TestS1S2PlcComparisonOnlyGuard,TestHardwareTimingAuthorityGuard \
  test

Exit: 0
```

Representative ROM-backed compatibility replays:

```text
mvn -Dmse=off \
  -Dsonic1.rom.path='<repo>/Sonic The Hedgehog (W) (REV01) [!].gen' \
  -Dsonic2.rom.path='<repo>/Sonic The Hedgehog 2 (W) (REV01) [!].gen' \
  -Dtest=TestS1Ghz1TraceReplay,TestS2Ehz1TraceReplay,TestS1SpecialStageTraceReplay,TestS2SpecialStageTraceReplay \
  test

TestS1Ghz1TraceReplay:             1 passed
TestS2Ehz1TraceReplay:             1 passed
TestS1SpecialStageTraceReplay:     1 passed
TestS2SpecialStageTraceReplay:     2 passed
Total:                             5 passed, 0 failures/errors/skips
```

The four representative committed fixture families do not yet advertise
`dynamic_art_transfer_state_per_frame_v1`. These replay results therefore
prove legacy compatibility and unchanged unrelated gameplay policies. Positive
audit comparison is covered by the typed first/interior/last, lag, terminal,
missing/extra edge, request-order, ledger-order, callback-PC exclusion, live,
and run-walker test matrix. Installing regenerated advertised fixtures remains
Task 9 work.

## Guard coverage

- Trace and ghost production sources cannot import or name
  `DynamicArtLifecycleService`.
- Crafted negatives reject dynamic-art seed, submit, completion, lifecycle,
  publication, observation, restore, and reset calls.
- Dynamic-art comparison/parser sources cannot access
  `HardwareTimingService`, apply `HardwareTimingReplayPort`, or construct
  `hardware_timing.jsonl`.

## Concerns

No blocking implementation concern remains. The positive end-to-end ROM
frontier cannot be reported from the currently committed legacy fixtures; it
must be established from the frozen regenerated audit fixtures in Task 9.
