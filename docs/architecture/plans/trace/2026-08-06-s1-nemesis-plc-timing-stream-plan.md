# Plan: a recorded hardware-timing readiness stream for the Nemesis PLC queue

Date: 2026-08-06
Status: **planned, not built.** Two decisions above the fix loop are outstanding
(§6). The observability gate that this plan depends on is measured and passed in
[`../../research/trace/2026-08-06-s1-plc-arming-row-observability.md`](../../research/trace/2026-08-06-s1-plc-arming-row-observability.md).

## 1. What this closes

The S1 emerald run's visual lane cannot get past `mz2_3` row 101 with a correct
model, because "which frame closure does this PLC queue head arm on" is a
sub-frame 68000 cycle-position question and the v5 aux stream leaves the two
outcomes with an identical row shape. `99746ffa9`'s
`TraceExecutionModel.isIterationHeldIntoNextRow` picks the rare outcome and is
wrong 14 times in 15; reverting it picks the common outcome and is wrong once —
but that once is `ghz2_2` 107, which lane 2 has to cross to reach `mz2_3` at all.

Hard rule 4 already names the **S1 PLC** pipeline as one whose readiness a
recorded hardware-timing stream may *delay*. This plan builds that.

## 2. Contract shape

- **Kind:** `NEMESIS_PLC_QUEUE`, wire name `nemesis_plc_queue`. Named for the
  hardware-service class, not the game — S1 and S2 share
  `NemesisPlcServiceQueue`, so a `S1_*` name would be a game carve-out (hard
  rule 2). Append the enum constant; do not insert it, because
  `HardwareTimingSchedule.CANONICAL_ORDER` sorts on the enum ordinal and every
  committed S3K fixture's canonical ordering depends on it.
- **Edge meaning:** the ROM's `RunPLC` promoted the FIFO head to the active
  decode slot on this raw frame. The engine still owns *which* entry (its own
  ROM-backed queue, in its own order), the pattern budget, the per-frame
  decrement and the retirement. The edge decides only *when* the promotion is
  observable — the exact test hard rule 4 states.
- **Boundary:** `pre_main_loop`, coupled in the loader the way
  `KOS_DECOMPRESSION_QUEUE` already is. `RunPLC` is the `Level_MainLoop` tail
  (`sonic.asm`:3032), behind every expensive call in the loop and ahead of the
  loop-top V-blank re-arm. Measured: the arm never precedes the frame's VInt
  service, in 0 of 68,001 probed frames.
- **Held-counter rows:** the `ghz2_2` edge lands on a lag row. That is the
  suppressed-row `pre_main_loop` case the cross-game contract already defines and
  `HardwareTimingReplayPort.applySuppressedRowCompletion` already implements. No
  new boundary semantics.

## 3. Engine work

S1 has no timing-port infrastructure at all today — no `RuntimeArtCoordinator`,
no submission, no ordinal, no submission fingerprint. In dependency order:

| # | Change | File |
|---|---|---|
| 1 | Append `NEMESIS_PLC_QUEUE` + wire name | `src/main/java/com/openggf/game/timing/HardwareWorkKind.java` |
| 2 | Add the kind to `recordedAdmissionPolicies()` and to the direct/`PRE_MAIN_LOOP` constructor invariant | `src/main/java/com/openggf/trace/timing/HardwareTimingSchedule.java` |
| 3 | Couple the kind to `pre_main_loop` at parse time | `src/main/java/com/openggf/trace/timing/HardwareTimingStreamLoader.java` (the existing kind/boundary check) |
| 4 | A `Sonic1RuntimeArtCoordinator` (and the S2 sibling when S2 needs it) returned from the game module, mirroring `Sonic3kGameModule`'s `S3kRuntimeArtCoordinator` wiring | `src/main/java/com/openggf/game/sonic1/…`, `Sonic1GameModule` |
| 5 | Submit one `HardwareWorkSubmission` per PLC entry at append/replace, and gate `prepareHead()` on readiness | `src/main/java/com/openggf/level/resources/NemesisPlcServiceQueue.java`, `src/main/java/com/openggf/game/sonic1/resources/Sonic1PlcService.java` |
| 6 | Revert `99746ffa9`'s deferral | `TraceExecutionModel.isIterationHeldIntoNextRow`, `TraceReplayBootstrap.markIterationHeldIntoNextRowForReplay` / `markReplayIterationDefersLoopTailPreparation` / `isIterationHeldIntoNextRowForReplay`, `PlcFrameLifecycleCoordinator.markRepresentedIterationDefersLoopTailPreparation` + `heldLoopTailPreparation`, the `LiveTraceComparator` call site, and the two tests added with it |
| 7 | Rewind capture of the new ledger (the existing `NemesisPlcQueueSnapshot` gains ordinal/fingerprint/released state) | `src/main/java/com/openggf/game/rewind/snapshot/NemesisPlcQueueSnapshot.java` |

`NemesisPlcServiceQueue` and `Sonic1PlcService` must not import
`com.openggf.trace` (`TestS1S2PlcComparisonOnlyGuard`), but *may* import
`com.openggf.game.timing`. `PlcFrameLifecycleCoordinator` is a gameplay owner and
may not import `com.openggf.trace.timing` at all
(`TestHardwareTimingAuthorityGuard.gameplayOwnersDoNotImportTimingParserTypes`),
so the readiness gate has to sit behind `HardwareTimingService`, reached the way
`S3kKosModuleQueue` reaches it.

### The submission fingerprint

`HardwareSubmissionFingerprint` hashes `(kind, romSourceAddress,
compressedLength, destinationAddress, destinationLength, compressionVariant,
moduleCount)`. For a Nemesis PLC entry:

| field | value | who can compute it |
|---|---|---|
| `romSourceAddress` | `PlcEntry.romAddr` | both — the recorder reads it out of `v_plc_buffer` slot 0 |
| `destinationAddress` | the entry's VRAM destination word | both — `v_plc_buffer+4` |
| `destinationLength` | patterns × `0x20` | both — the ROM header word at `romAddr`, masked `0x7FFF` |
| `compressionVariant` | `"nemesis"` | both |
| `moduleCount` | `1` | both |
| **`compressedLength`** | Nemesis stream byte length | **engine only, today** |

`compressedLength` is the one real cost. The engine derives pattern counts by
fully decompressing each entry (`NemesisPlcPatternCounts.derive` →
`PlcParser.decompressEntryRaw`) but does not report bytes consumed; the recorder
has no Nemesis reader at all. Both sides need one, in the shape of the existing
C# `InspectKosinskiModule` / `InspectStandardKos` scanners:

- engine: have the Nemesis decoder report consumed length, and carry it on the
  queue entry;
- recorder: a C# Nemesis scanner (code table to the `0xFF` terminator, then the
  bit-stream row decode to `patterns × 8` rows) plus cross-implementation
  vectors against the Java decoder for a fixed set of ROM offsets.

Do **not** shortcut this by fingerprinting `(source, destination, patterns)`
alone and calling `compressedLength` zero. The contract calls the fingerprint a
canonical tuple over the ROM source *span*, and the existing comparison-only
`QueueDiagnosticSnapshot.fingerprint` already occupies that weaker identity.

### Ordinal allocation

`HardwareTimingService.submit` allocates the ordinal, so the engine allocates on
append/replace. The recorder must allocate identically. Its natural point is
"an entry first appears in the mirrored FIFO", the way `ReconcileQueue` does for
S3K — but S1 has two shapes that need measuring before this is written:

- `ClearPLC` / `LoadPLC2` (`sonic.asm`:1342, 1363) drop *queued, never-armed*
  entries. Both sides must consume the same ordinals for them, or fail closed.
- a `LoadPLC` immediately followed by a `ClearPLC` inside one raw frame would be
  invisible to a frame-end mirror.

The reviewed hook set already contains the boundaries needed to measure this:
`append begin` `0x001578`, `append post-copy` `0x0015A4`, `replace begin`
`0x0015AA` / post-copy `0x0015D0`, `clear begin` `0x0015DA` / post `0x0015E2`
(all byte-checked in
[`../../research/trace/2026-07-28-s1-s2-plc-readiness-evidence.md`](../../research/trace/2026-07-28-s1-s2-plc-readiness-evidence.md)).
Measure first; do not assume frame-end mirroring is lossless.

### Segment handoff

The emerald run is 30-plus segments and `HardwareTimingReplayPort.handoffTo`
requires every exportable pending submission to have a matching next-segment edge
by `(kind, ordinal, fingerprint)`, with contiguous ordinals per kind
(`validateSchedule`). PLC entries submitted during a level load and cleared at
the next load must therefore be represented consistently on both sides across
every boundary in the run, not just inside the segment that contains `ghz2_2`.
This is the largest correctness risk in the whole plan.

## 4. Recorder work

Mirror the S3K structure rather than inventing a parallel mechanism.

- A `NemesisPlcTimingEventEngine` alongside `HardwareTimingEventEngine`: mirrors
  the 16-entry FIFO from `S1Ram.PlcBuffer`, assigns ordinals on observed
  submission, computes the fingerprint with the same
  `ComputeSubmissionFingerprint` helper, and emits one
  `hardware_work_completed` line at `pre_main_loop` per arming.
- One address-filtered `M68K BUS` execute callback at `0x0015F0` (arming path
  taken). This is a **second** permitted exception to
  `tools/bizhawk-headless/CLAUDE.md`'s "diagnostic hooks are deliberately not
  ported" rule and needs that document updated with the same gating language the
  `0x001B46` exception carries: it may observe only, never emit a completion by
  itself, never select a sync point, never mutate emulation state, and its Mono
  delegate must stay strongly rooted and be deterministically unregistered.
- Wire it into `S1RunCaptureRunner` (the emerald run is run mode) and
  `S1TraceCaptureRunner`, and stage the stream through the existing
  `IRunSegmentSink` the way `S3KStagedSegmentSink` does, so per-segment
  `hardware_timing.jsonl` files land next to `physics.csv` / `aux_state.jsonl`.
- Tests in `tools/bizhawk-headless/tests/`, registered in
  `TestMain.BuildRegistry()`, and both `.csproj` files updated by hand — there is
  no globbing.

## 5. Fixture work

- `s1-sonic-complete-withemeralds` is mandatory. Nothing else is: reverting the
  deferral (§3.6) restores the correct behaviour for every fixture that does not
  contain a `ghz2_2`-shaped case, and `LiveTraceComparator` is the deferral's only
  caller so the standalone `*TraceReplay` lane never saw it.
- Follow `tools/bizhawk-headless/CLAUDE.md`'s publication contract exactly:
  capture into scratch, freeze digests/lengths/counts/ordering/ranges, categorise
  every byte-level delta against a named cause, obtain explicit user approval for
  the exact candidate, then copy byte-for-byte. Never hand-edit an event.
- Payloads compress at publication; never commit an uncompressed `physics*.csv`
  or `aux_state*.jsonl` (`TestTraceFixtureCompressionGuard`).
- `tools/bizhawk/trace_output.s1-complete-emeralds-backup/` is an untracked
  backup from earlier work. Leave it alone.

## 6. The two decisions this needs

Neither is an implementation detail and neither was taken here.

1. **The kind registry is currently closed by policy, not by accident.**
   `TestS1S2PlcComparisonOnlyGuard.timingKindRegistryAdmitsOnlyKosinskiWork`
   asserts that no admitted kind name contains `PLC` and that the admitted set is
   exactly `{KOS_MODULE_QUEUE, KOS_DECOMPRESSION_QUEUE}`, with the message *"PLC
   readiness is native deterministic service, not timing-stream authority"*. The
   cross-game contract says the same: `PLC_QUEUE` "remains a non-authoritative
   inventory candidate; each requires separate ROM evidence and design review",
   and its acceptance criterion 1 requires the S1/S2 PLC audit to prove per-handler
   service and polling behaviour before a new kind is justified. That audit exists
   and its disposition is `NATIVE_MODEL_APPROVED` — explicitly *not* authorising a
   hardware-timing event kind. Reopening it is a reviewed decision.
   `TestHardwareTimingAuthorityGuard` must stay green unmodified throughout; it is
   not where these literals live.
2. **Fixture regeneration is a user decision.** Re-recording
   `s1-sonic-complete-withemeralds` replaces committed ground truth for a
   209k-frame run and can unmask latent engine bugs three segments downstream of
   anything it fixes. It needs explicit approval of the exact candidate bytes and
   a before/after frontier measurement recorded in
   `docs/status/trace-frontier-log.md`.

## 7. Acceptance

- Both lanes of `TestS1CompleteEmeraldVisualRun` pass at their current pins, then
  the pin moves and the new stopping point is confirmed to be a **different**
  error, not `mz2_3` 101 relocated.
- `TestS1Mz3CompleteRunTraceReplay` and the rest of the S1 `*TraceReplay` fleet
  keep their current pass set — the 13 other cases live there.
- `TestHardwareTimingAuthorityGuard` green, unmodified.
- Missing, duplicate, reordered, wrong-boundary, wrong-fingerprint and unprepared
  Nemesis edges fail structurally; rewind consumes an edge exactly once again
  after restore.
- Live (non-trace) S1 play still arms from the production scheduler, not from a
  recorded edge.
