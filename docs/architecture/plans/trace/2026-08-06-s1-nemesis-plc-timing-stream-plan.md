# Plan: a recorded hardware-timing readiness stream for the Nemesis PLC queue

Date: 2026-08-06 (revised)
Status: **Planned, not built.** This revision supersedes the earlier 2026-08-06
draft at this path in its entirety.

Governing documents:

- Admission gate — [`../../designs/2026-08-06-s1-nemesis-plc-timing-kind-admission-review.md`](../../designs/2026-08-06-s1-nemesis-plc-timing-kind-admission-review.md).
  Every contract decision below is settled there; this plan does not re-argue any
  of them.
- Contract — [`../../designs/2026-07-27-cross-game-hardware-timing-trace-contract.md`](../../designs/2026-07-27-cross-game-hardware-timing-trace-contract.md).
- Observability evidence — [`../../research/trace/2026-08-06-s1-plc-arming-row-observability.md`](../../research/trace/2026-08-06-s1-plc-arming-row-observability.md).
- Address evidence — [`../../research/trace/2026-07-28-s1-s2-plc-readiness-evidence.md`](../../research/trace/2026-07-28-s1-s2-plc-readiness-evidence.md).

## What changed from the superseded draft

Recorded because the earlier draft is cited elsewhere and its errors should not
propagate.

| Superseded claim | Status |
|---|---|
| "ROM code polls `v_plc_patternsleft`" | **Wrong.** `v_plc_patternsleft` has six touches, all inside the PLC service block. The polled gate is `v_plc_buffer`. Review §1. |
| `compressedLength` must be carried; both sides need a Nemesis scanner; "do not shortcut this" | **Reversed.** Waived, with the XOR-mode bit moved into `compressionVariant`. Review §6. This deletes the largest line item on both sides. |
| Submit "one `HardwareWorkSubmission` per PLC entry at append/replace" | **Reversed.** Submission is at the arming decision, not at append. Append-time submission creates `ClearPLC` orphans that can never be released under `RECORDED` admission. Phase 4. |
| "Ordinal allocation across `ClearPLC`/`LoadPLC2` needs measuring before it can be written" | **Dissolved** by submitting at arming. `ClearPLC`/`LoadPLC2` drop *queued, never-armed* entries, which are now never submitted and consume no ordinal on either side. The measurement is downgraded from a blocker to a confirmation (M3). |
| "The 30-plus segment handoffs are the largest correctness risk" | **Superseded by a larger one.** The largest risk is fail-closed starvation of the level-load / title-card drain and the 21 inter-segment gaps, which the draft did not identify. Review §9; Phase 1 and Phase 4 here. |
| Decision gate 1 ("reopening the registry is a reviewed decision") | **Discharged** by the admission review. |
| Decision gate 2 ("fixture regeneration is a user decision") | **Discharged** — the user has explicitly allowed regeneration. It is no longer a gate, only a step (Phase 9). |
| Boundary order `VINT_SERVICE -> PRE_MAIN_LOOP -> objects -> POST_OBJECTS` (from `2026-07-28-s1-s2-plc-service-queues.md`) | **Stale.** Actual order is `VINT_SERVICE` → objects → `POST_OBJECTS` → `PRE_MAIN_LOOP` → `prepareAfterLoop` (`LevelFrameStep.java:144-150`, `HardwareServiceBoundary` javadoc). Phase 11 corrects the doc. |

## Evidence status legend

Each factual claim below is marked:

- **[E] Established** — measured or read directly out of the tree, with a citation.
- **[I] Inferred** — follows from established facts by argument, not measured.
- **[A] Assumed** — neither; a deferred measurement exists for it.

---

## Deferred measurements

Nothing downstream of these should be written until they land. Each names the
phase it blocks. **No number in this plan is invented to stand in for one.**

| id | Measurement | Blocks | Method |
|---|---|---|---|
| **M1** | For each of the 34 segments of `s1-sonic-complete-withemeralds`, the set of raw frames in `[bk2_frame_offset, bk2_frame_offset + trace_frame_count)` on which the arming path `0x0015F0` executes. Gives the exact per-segment edge count and the run-wide ordinal range. | 6, 8, 9 | Extend the existing probe (see M-method) with the manifest's segment windows. |
| **M2** | Whether the arming path executes on the first *gap* row immediately after any segment's last recorded row. | 4 (exportability), 6 | Same probe pass as M1. |
| **M3** | Whether any `ClearPLC` (`sonic.asm:1363`) or `NewPLC` (`:1336`) executes on a recorded row while an entry is armed but not retired, and whether any `LoadPLC`→`ClearPLC` pair occurs inside one raw frame. Confirms the arming-time submission model has no orphan case. | 4 | Add hooks at the reviewed `append begin` `0x001578`, `replace begin` `0x0015AA`, `clear begin` `0x0015DA` boundaries. |
| **M4** | The `PlcLifecyclePhase` the engine claims on every recorded row of every segment, and whether any row on which `0x0015F0` fires claims anything other than `ORDINARY_LEVEL`. | 4, 6 | Engine-side instrumented dry run over the fixture, cross-referenced against M1. |
| **M5** | Whether `v_vblank_routine` (`$FFFFF62A`) is `id_VBlank_Levels` (`$08`) on every armed-segment row on which `0x0015F0` fires. The recorder-side equivalent of M4. | 6 | Probe pass; requires adding `VblankRoutine` to `S1Ram.cs`. |
| **M6** | Whether the engine's arming *decisions* on recorded rows match the ROM's armings 1:1 in count and order, before any edge is applied. A count divergence fails closed but must be known before fixture capture, not after. | 6, 9 | Engine dry run with a diagnostic-only arming log, diffed against M1. |
| **M7** | Whether the comparator already treats the engine's idle-with-queued diagnostic (`prepared=false, remaining=-1`, `NemesisPlcServiceQueue.java:94-105`) as equal to the recorder's `PlcPatternsLeft == 0` on the denied row. Review §10. | 5, 10 | Focused replay of `ghz2_2` around row 107 with the deferral forced. |
| **M8** | The full `*TraceReplay` and visual-run baseline immediately before Phase 5's revert: pass set, error counts, first-error frame/field. | 5, 10 | `mvn -Dmse=off -Dtest='*TraceReplay' -DfailIfNoTests=false` plus both visual-run lanes, all three ROM properties. |

**M-method.** M1, M2, M3 and M5 are all one additional pass of the throwaway
native probe described in the observability research
(`tools/bizhawk-headless/.scratch/PlcProbe.cs`, untracked because `.gitignore`
ignores `tools/*`). **`PlcProbe.cs` and `PlcProbe2.cs` already exist in
`.scratch/` — read them before writing anything new there.** The probe is not part
of the harness build and must not become one.

---

## Phase 0 — Measurement pass A (M1, M2, M3, M5)

No production change. Produces the numbers Phases 4 and 6 are written against.

**Deliverable.** A short addendum to the observability research doc carrying: the
per-segment arming table, the gap-row answer, the clear/replace answer, and the
V-blank-routine answer. Not a new document — the research doc is the owner.

**Acceptance.** The addendum exists, is committed, and its per-segment arming
counts sum to a number that Phase 6 can pin as the expected edge count.

**Gate.** If M2 is positive — an arming does fall on a gap row after a segment —
Phase 4's `exportableAcrossSegment = false` decision produces a hard failure at
that handoff, and the plan must stop and be amended before Phase 6. Do not paper
over it by exporting the submission.

---

## Phase 1 — Bound every PLC-drain consumer

Independent of the timing stream and worth landing on its own.

Under `RECORDED` admission a starved job is permanently pending, and starvation
manifests as an unbounded wait rather than an exception
(`HardwareTimingService.releasePreparedInFifoOrder` is `LIVE`-only, `:68-75`).
**[E]** Every engine-side consumer that waits on PLC readiness must therefore be
bounded and must fail with a diagnosable message.

**Work.**

1. Audit every production site that loops on `NemesisPlcServiceQueue.isBusy()` or
   drives `prepareHead()` in a loop — principally the S1 level-entry
   presentation-omitted drain described in
   `2026-07-28-s1-s2-plc-service-queues.md` §"Deliberately skipped level
   presentation", and the `Level_TtlCardLoop` model.
2. Give each an explicit iteration budget derived from its own ROM-modelled
   maximum, and on exhaustion throw naming the queue head and, when one exists,
   the pending `HardwareWorkKind`/ordinal.

**Acceptance.** `TestSonic1PlcService` (or the owning class) gains a test that a
never-releasing readiness produces a named failure inside the budget rather than
hanging. No behaviour change on the passing path; the full S1 `*TraceReplay` set
keeps M8's baseline.

---

## Phase 2 — Kind, wire name, registry, boundary coupling

Smallest self-contained production step. Behaviourally inert until Phase 4
submits something.

**Work.**

1. `HardwareWorkKind` — **append** `NEMESIS_PLC_QUEUE` (never insert;
   `HardwareTimingSchedule.CANONICAL_ORDER` sorts on `kind().ordinal()` and every
   committed S3K stream depends on indices 0 and 1). Add `"nemesis_plc_queue"` to
   `fromWireName`.
2. `HardwareTimingSchedule.recordedAdmissionPolicies()` (`:101-110`) — add the
   kind as `RECORDED`. **[E]** Required: `HardwareTimingService.validateAdmissionPolicies`
   (`:565-585`) rejects a policy map missing any enum value.
3. `HardwareTimingSchedule`'s constructor invariant (`:55-59`) and
   `HardwareTimingStreamLoader.loadVersion` (`:83-87`) — couple the new kind to
   `PRE_MAIN_LOOP`, exactly as `KOS_DECOMPRESSION_QUEUE` already is.
4. `tools/traces/validate_trace_v5.py` — `KIND_ORDER` (`:32`) and the boundary
   check (`:219`).

**Acceptance.**

- `TestHardwareTimingStreamLoader` gains: the wire name parses; a
  `nemesis_plc_queue` edge at `vint_service` or `post_objects` is rejected at
  load; an unknown fourth wire name is still rejected.
- `TestCommittedHardwareTimingFixtures` **green, unchanged** — this is the
  verification of admission-review §7.4 that no S3K fixture is affected.
- `TestHardwareTimingAuthorityGuard` **green, unmodified**.

---

## Phase 3 — Replace the registry guard

Separate commit from Phase 2 so the guard change is visible in isolation and
reviewable on its own.

**Work.** Rewrite `TestS1S2PlcComparisonOnlyGuard.timingKindRegistryAdmitsOnlyKosinskiWork`
(`:79-90`) per admission review §5.5 — five assertions:

1. `HardwareWorkKind` values are exactly
   `{KOS_MODULE_QUEUE, KOS_DECOMPRESSION_QUEUE, NEMESIS_PLC_QUEUE}`.
2. Kinds whose name contains `PLC` are exactly `{NEMESIS_PLC_QUEUE}`, with the
   message rewritten to *"S1/S2 PLC readiness is native deterministic service
   except at the reviewed `Level_MainLoop` arming site"*.
3. `com.openggf.level.resources.NemesisPlcServiceQueue` does not import
   `com.openggf.game.timing` — the shared kernel stays clean so S2 cannot inherit
   the authority through it. **New assertion; this is the substantive
   replacement.**
4. The only production source constructing a `HardwareWorkSubmission` with
   `NEMESIS_PLC_QUEUE` is
   `com.openggf.game.sonic1.resources.Sonic1RuntimeArtCoordinator`.
5. The only kind submitted with `compressedLength == 0` is `NEMESIS_PLC_QUEUE`
   (confines the §6 waiver).

The class's three existing package-isolation tests are **unchanged**.

**Acceptance.** The rewritten class passes; each of the five assertions has a
crafted-violation negative self-test in the style
`TestHardwareTimingAuthorityGuard` already uses.

---

## Phase 4 — The S1 submission path

The largest engine phase. Blocked on M2, M3, M4.

### 4.1 Where the code goes

**[E]** Nothing exists today: `Sonic1GameModule` does not override
`GameModule.createRuntimeArtCoordinator` (`GameModule.java:46-50`) and inherits
`RuntimeArtCoordinator.NONE`, although `GameplayModeContext.java:193-201` already
builds a `HardwareTimingService` for every S1 session. The only implementation of
the interface is `S3kRuntimeArtCoordinator` (`:19-91`).

| # | Change | File |
|---|---|---|
| 1 | New `Sonic1RuntimeArtCoordinator implements RuntimeArtCoordinator`, constructed from `HardwareTimingService` + `Sonic1PlcService`, mirroring `S3kRuntimeArtCoordinator`'s shape | `src/main/java/com/openggf/game/sonic1/resources/` |
| 2 | Override `createRuntimeArtCoordinator` to return it | `Sonic1GameModule.java` |
| 3 | Route `prepareAfterLoop(ORDINARY_LEVEL)` through the coordinator; every other phase keeps calling `queue.prepareHead()` directly | `Sonic1PlcService.java:162-166` |
| 4 | Expose the head entry's `(romAddr, tileIndex)` and the ROM header word to the coordinator without exposing mutation | `NemesisPlcServiceQueue.java`, `Sonic1PlcService.java` |

### 4.2 Submission point and per-boundary contract

**[E]** `HardwareBoundaryDispatch.serviceBoundary` fixes the order:

```
1. coordinator.beforeTimingService(PRE_MAIN_LOOP)   submit the arming request
2. hardwareTiming.service(PRE_MAIN_LOOP)            prepare it
3. observer.onBoundary(PRE_MAIN_LOOP)               replay port applies the edge
4. coordinator.afterTimingService(PRE_MAIN_LOOP)    claim ready work -> prepareHead()
```

**[I]** The submission must be created in step 1 and prepared by step 2, because
`admitRecordedCompletion` rejects an unprepared job
(`HardwareTimingService.java:523-528`). The preparation is boundary-driven and
completes in one boundary, so `serviceBoundaryDrivenHead` (`:341-360`) captures it
inside step 2. `S3kKosDecompressionQueue.DirectPreparation` (`:235-305`) is the
closer analogue than the module parent's.

**Submit when, and only when:** the coordinator's own queue state says the ROM
would arm — no active decode entry, and at least one queued entry — **and** the
iteration's claimed phase is `ORDINARY_LEVEL`, **and** no arming request is
already unclaimed. The phase is latched at `frame.claim(phase)`
(`LevelFrameStep.java:144`), before the frame's first boundary.

**[I]** Phase-gating is what prevents fail-closed starvation of the title-card
drain, which reaches the boundaries through
`LevelFrameStep.executeHardwareTimedObjectScan` (`:353`, `:362`). Admission review
§9.2 argues why this is a ROM-loop rule and not a carve-out. M4 confirms it holds
over the fixture.

### 4.3 The submission descriptor

Per admission review §6.3:

```
kind                = NEMESIS_PLC_QUEUE
romSourceAddress    = head entry's ROM art pointer
compressedLength    = 0                       (waived; self-terminating codec)
destinationAddress  = head entry's VRAM destination word
destinationLength   = (header & 0x7FFF) * 0x20
compressionVariant  = "nemesis" | "nemesis_xor"   (header bit 15)
moduleCount         = 1
exportableAcrossSegment = false
```

**[E]** The header is a 2-byte big-endian ROM read at `romSourceAddress` — the
same read `LoadQueueStateProjector.NemesisPatternCount` (`:123-138`) already makes
on the recorder side. Read it directly; do **not** derive the count from
`NemesisPlcPatternCounts.derive`'s decompression result, so that both sides are
literally reading the same two bytes.

**[I]** `exportableAcrossSegment = false` is correct on ROM grounds as well as
safety grounds: a level restart's `ClearPLC` (`sonic.asm:2711`) wipes the queue,
so an arming cannot survive a segment boundary. It makes M2's failure mode loud —
`handoffTo` throws *"non-exportable pending hardware submission at segment end"*
naming the job (`HardwareTimingReplayPort.java:222-226`).

### 4.4 Claim and retirement

Step 4 claims every ready `NEMESIS_PLC_QUEUE` job in FIFO order and, for each,
calls the queue's `prepareHead()`. Everything after that — the 3-or-9-pattern
budget, the per-frame decrement, `ProcessPLC_ShiftCue`'s retirement — stays
exactly where it is in `NemesisPlcServiceQueue.servicePatterns` (`:52-68`). The
timing port never touches it.

### 4.5 Package isolation

**[E]** `Sonic1PlcService`, `Sonic1RuntimeArtCoordinator` and
`NemesisPlcServiceQueue` may import `com.openggf.game.timing` but never
`com.openggf.trace` or `com.openggf.trace.timing`
(`TestS1S2PlcComparisonOnlyGuard`, `TestHardwareTimingAuthorityGuard`).
`PlcFrameLifecycleCoordinator` is a gameplay owner and gains no timing import at
all. The route is the one `S3kKosModuleQueue` already uses: hold an injected
`HardwareTimingService`, ask `timing.isReady(handle)`, never know a trace exists.

**Acceptance.**

- New `TestSonic1RuntimeArtCoordinator`: submits exactly once per arming
  opportunity; does not resubmit while an arming is unclaimed; submits nothing for
  `LEVEL_TITLE_CARD`, `PALETTE_FADE`, `CREDITS_TEXT`, `SPECIAL_STAGE_RESULTS`,
  `TITLE_SCREEN`, `LEVEL_SELECT` or `CREDITS_DEMO`; the descriptor matches the
  §4.3 tuple for a known ROM PLC id; the XOR-mode entry produces
  `"nemesis_xor"`.
- Java/C# fingerprint parity for a fixed set of S1 PLC entries, asserted from a
  committed language-neutral vector file under
  `src/test/resources/nemesis/plc-submission-vectors.tsv`, consumed by both
  `TestHardwareSubmissionFingerprint` and a new C# test — the same two-consumer
  pattern `src/test/resources/kosinski/standard-scanner-vectors.tsv` already uses.
  **The waiver removes the need for a Nemesis *scanner* vector file, not for a
  fingerprint vector file.**
- `TestS1S2PlcComparisonOnlyGuard` and `TestHardwareTimingAuthorityGuard` green.
- Live (non-trace) S1 play unchanged: with `LIVE` admission,
  `releasePreparedInFifoOrder` releases in the same boundary, so the arming lands
  exactly where it does today. A focused S1 `*TraceReplay` subset must keep M8's
  baseline with no timing stream present.

---

## Phase 5 — Revert `99746ffa9`

Blocked on M7 and M8. Landing this before Phase 6 is deliberate: it is the change
that fixes 14 of the 15 cases, and it should be measurable on its own.

**[E]** `99746ffa9` is an ancestor of HEAD and touched 11 files. Revert surface:

| Symbol | File |
|---|---|
| `isIterationHeldIntoNextRow` | `trace/TraceExecutionModel.java:67-76` |
| `markIterationHeldIntoNextRowForReplay`, `markReplayIterationDefersLoopTailPreparation`, `isIterationHeldIntoNextRowForReplay` | `trace/TraceReplayBootstrap.java:605-626` |
| `representedIterationDefersLoopTailPreparation`, `heldLoopTailPreparation` fields; `markRepresentedIterationDefersLoopTailPreparation()`; the release block in `claim()`; the branch in `prepareAfterLoop()`; the clear in `finish()`; the two `reset()` lines | `game/resources/PlcFrameLifecycleCoordinator.java:25-26, 286-288, 300-301, 421-428, 466-477, 508` |
| the call at `:318` inside `activatePreparedProductionMarker()` | `trace/live/LiveTraceComparator.java:305-326` |
| 3 tests | `TestPlcFrameLifecycleCoordinator.java:572, 599, 605, 630` |
| 4 tests | `TestTraceExecutionModel.java:66, 74, 86, 94` |
| 1 test (`replaysTheSecondGiantRingAndTheSpecialStageBehindIt`, `:111-119`) | `TestS1CompleteEmeraldVisualRun.java` |

`isVblankStarvedRow` and `markReplayProductionIterationWithoutVblank` are **not**
part of this commit and must survive untouched.

**Acceptance.**

- The full `*TraceReplay` sweep and both visual-run lanes are re-measured against
  M8. Expected: the 14 standalone-fixture cases improve or are unaffected
  (`LiveTraceComparator` is the deferral's only production caller, so the
  standalone lane never saw it); the emerald visual lane's stop moves **back** to
  `ghz2_2` 107 or earlier.
- The new stopping point is confirmed to be `ghz2_2` 107 specifically, not
  `mz2_3` 101 relocated. If it is anywhere else, stop — the model of the failure
  is wrong.
- `TestS1CompleteEmeraldVisualRun`'s second test is removed with the commit that
  removes the behaviour it pins; it is re-added in Phase 10 against the stream.
- Frontier log updated with both measurements.

---

## Phase 6 — Recorder: `S1NemesisPlcTimingEventEngine`

Blocked on M1, M2, M4, M5, M6. Mirrors the S3K engine's structure; does not
invent a parallel mechanism.

### 6.1 The hook

**One** address-filtered `M68K BUS` execute callback at REV01 PC `0x0015F0` —
`movea.l (v_plc_buffer).w,a0`, the arming path taken.

**[E]** An execute callback fires before the instruction retires, so slot 0 still
holds the entry's original ROM pointer; `sonic.asm:1405`'s write-back of the
advanced pointer has not happened. Slot 0 + 4 still holds the original VRAM
destination; `ProcessPLC_9Tiles`/`_3Tiles` (`:1438`, `:1450`) have not advanced it
for this entry. Both fingerprint inputs are clean at this PC and nowhere else.

**[E] Frame-end RAM sampling is not a substitute.** The naive rule
"`v_plc_patternsleft` went 0 → nonzero" disagrees with execution on **536**
emerald frames and **438** complete-run frames, because the `9SRAW`/`3SRAW` shape
retires and arms inside one frame and the counter is never sampled at zero.
Structural FIFO mirroring could recover it, but is strictly weaker than the exact
observation and requires retaining head identity across an advancing pointer. Do
not substitute it.

### 6.2 The harness rule this needs amended

**[E]** `tools/bizhawk-headless/CLAUDE.md:176-184` (and `AGENTS.md:179-187`)
scopes the sole permitted callback exception to S3K:

> Do not "helpfully" add general M68K exec/memory-write callback support. The sole
> permitted exception is the address-filtered S3K hardware-timing submission
> observer at `M68K BUS` PC `0x001B46`, immediately after
> `Process_Kos_Module_Queue` returns from `Queue_Kos`. It may mirror or stage
> direct-FIFO submission lifecycle only; it must not emit a completion, select a
> trace sync point, mutate emulation state, or enable any diagnostic-hook output.
> Its Mono delegate must remain strongly rooted while registered and be
> deterministically unregistered when capture ends. Behavioral tests,
> ROM/disassembly evidence, independent review, and corrected-candidate
> differentials gate this exception.

Two facts matter. First, the S1 site is outside that text as written. Second,
`S1DynamicArtObserver.cs:65-79` **already registers four S1 execute callbacks**
and is not mentioned in the section at all — so the written rule is already
narrower than practice.

**Work.** Amend both files (they must stay in sync) to enumerate the permitted
exceptions rather than name one, adding the S1 Nemesis PLC arming observer at
`0x0015F0` with the identical gating clauses — observe only, never emit a
completion by itself, never select a sync point, never mutate emulation state,
never enable diagnostic-hook output, Mono delegate strongly rooted and
deterministically unregistered — and regularising the existing dynamic-art
registrations in the same list. Do **not** relax the general prohibition.

### 6.3 The engine

Mirror `HardwareTimingEventEngine` (`:14-1434`), not a new shape.

- Constants beside `:17-22`: `NemesisPlcKindName = "NEMESIS_PLC_QUEUE"`,
  `NemesisPlcEventKind = "nemesis_plc_queue"`, variants `"nemesis"` /
  `"nemesis_xor"`.
- Fingerprint via the **existing** `ComputeSubmissionFingerprint` (`:1079-1118`),
  which is already byte-identical to `HardwareSubmissionFingerprint.computeCanonical`
  — length-prefixed UTF-8 kind, four big-endian int32s, length-prefixed UTF-8
  variant, big-endian int32 module count. No new hashing code. **[E]**
- Header read via the existing `LoadQueueStateProjector.NemesisPatternCount`
  logic (`:123-138`), extended to also surface bit 15. No Nemesis decoder. **[E]**
- Emission via `WriteCompletion`'s exact byte shape (`:1325-1346`): compact JSON,
  fixed key order, LF-terminated, boundary hardcoded `"pre_main_loop"`.
- `S1Ram.cs` gains `VblankRoutine = 0xF62A` for the M5 gate. `PlcBuffer 0xF680`,
  `PlcPatternsLeft 0xF6F8`, `FrameCount 0xFE04` already exist (`:24-29`).

### 6.4 Ordinal allocation — the deliberate divergence from S3K

**[E]** `HardwareTimingEventEngine` assigns ordinals at mirror creation and keeps
a run-wide ledger across gaps, using a null writer to advance it outside
represented segments (`S3KCompleteRunCaptureRunner.ObserveUnexportedHardwareBoundary`).

**S1 must do the opposite: advance the ordinal only while a segment is armed.**

**[I]** Rationale, from admission review §9.2: S3K's engine can submit and claim
during a gap because its coordinator runs there; S1's cannot, because transition
gap rows run no level body, traverse no boundary, and never set
`lastServicedBoundary`. If the recorder counted gap armings the two ledgers would
diverge immediately at the first act boundary. Counting only armed-segment armings
makes `NEMESIS_PLC_QUEUE` ordinals contiguous across the entire run with no gaps —
strictly easier to satisfy than the contract's gap-tolerant rule, and exactly what
`HardwareTimingReplayPort.validateSchedule`'s `+1` contiguity check (`:398-409`)
wants.

**Emit an edge when, and only when:** `0x0015F0` executes, the run is inside an
armed level segment, and `v_vblank_routine == id_VBlank_Levels ($08)`. Any arming
observed inside an armed segment under a different V-blank routine is a **hard
recorder failure**, not a silent skip — it means M4/M5 were wrong and the engine
and recorder gates have diverged.

### 6.5 Plumbing

**[E]** The S1 path has no timing file today.

| Site | Change |
|---|---|
| `RunSegmentSink.cs:17-36` `RunSegmentStreams` | third writer, using `S3KSegmentStreams`' 2-arg→3-arg constructor pattern (`S3KCompleteRunCaptureRunner.cs:16-49`) so existing callers keep compiling with `TextWriter.Null` |
| `StagedRunSegmentSink.cs:47-49, 72-129` | third staged stream, mirroring `S3KStagedSegmentSink.cs:27-28, 47-104` including its open-failure `DisposeOpenStreams` path |
| `Program.cs:25-30` `TraceOutputFileNames` | add `hardware_timing.jsonl`; check the `:550` no-replace preflight |
| `S1RunCaptureRunner.cs` | engine field on `RunState` beside `auxEngine` (`:364`), constructed in the `RunState` ctor (`:398-414`), callback registered in a `using` around the enumerator (`:196`) and disposed in the existing `finally` (`:306-312`), writer captured in `OpenSegmentStreams` (`:758-764`), `ObserveFrameEnd` in `AppendLevelRow` after `:485` |
| `S1TraceCaptureRunner.cs` | fourth `TextWriter` threaded through `Capture` / `CaptureScratchLegacy` / `CaptureCore`; engine beside `dynamicArt` (`:107-111`); `ObserveFrameEnd` after the aux cascade (`:229`) |
| `BizHawk.Headless.Gpgx.csproj` (`:56-126`) and `.Tests.csproj` (`:39-89`) | hand-add every new file; **there is no globbing** |
| `TestMain.BuildRegistry()` (`:153-214`) | register the new test class, or it silently never runs |

**Do not add a `hardware_timing_schema` metadata key.** **[E]** v5 removed it, and
five tests pin its absence for S1 (`S1TraceMetadataWriterTests.cs:58`,
`S1CompleteRunMetadataWriterTests.cs:117`, `TraceCliTests.cs:397, 702, 1286`).
Presence of the *file* is the whole discovery mechanism.

**Acceptance.**

- New `S1NemesisPlcTimingEventEngineTests`, registered in `BuildRegistry`:
  fingerprint golden values matching the Phase 4 vector file; the ordinal ledger
  advances only inside armed segments; an arming under a non-`$08` V-blank routine
  inside an armed segment throws; `Reset` clears the ledger; the emitted line is
  byte-exact.
- The three S1 no-metadata-key tests and `TraceCliTests` green.
- A scratch capture of a short S1 movie produces a well-formed
  `hardware_timing.jsonl` that `tools/traces/validate_trace_v5.py` accepts and
  `HardwareTimingStreamLoader` loads.

---

## Phase 7 — Rewind ledger coverage

**[E]** `HardwareTimingService` (`REWIND_KEY = "hardware-timing"`) and
`HardwareTimingReplayPort` (`"hardware-timing-replay"`) already capture the
ordinal ledger, job states, compiled edges and the consumption cursor. What is
missing is the S1 side.

**Work.**

1. `NemesisPlcQueueSnapshot` (`:7-21`) currently carries only
   `(activeEntry, queuedEntries)` with `Entry(sourceAddress, destinationTile,
   totalPatterns, remainingPatterns)`. Extend it — or add a sibling snapshot owned
   by `Sonic1RuntimeArtCoordinator` — to carry the in-flight arming request's
   `(kind, ordinal, submissionFingerprint)` and its claimed/unclaimed state.
2. Restore rebinds through `HardwareTimingService.pendingHandle(kind, ordinal)`
   (`:157-169`) rather than resubmitting, exactly as `S3kKosDecompressionQueue`
   does (`:189-225`). A restore must never call `submit`.
3. `Sonic1RuntimeArtCoordinator` implements `registerRewindAdapters` /
   `deregisterRewindAdapters` / `resetForMissingSnapshot`, mirroring
   `S3kRuntimeArtCoordinator:77-91`. `Sonic1PlcService` remains the sole rewind
   registration owner for its kernel state; do not register the kernel twice.

**Acceptance.**

- `TestRewindCoverageGuard` and `TestStaticStateRewindCoverageGuard` green with no
  baseline entry added.
- Snapshot/restore round trips immediately **before**, **on**, and **after** an
  arming admission, in both `LIVE` and `RECORDED` modes, reproduce the same
  readiness edge and consume the same future edge exactly once — the contract's
  explicit requirement.
- A restore across the `ghz2_2` deferral re-consumes the suppressed-row edge
  exactly once.

---

## Phase 8 — Segment handoff and ordinal continuity

Blocked on M1, M2.

**[E]** The emerald run has **34** segments and 21 inter-segment level-load gaps
of 216-236 rows. `handoffTo` (`:203-249`) enforces: policy equality; no
next-segment edge repeating a consumed identity; every pending submission
exportable **and** matched by a next-segment edge on `(kind, ordinal,
fingerprint)`. `validateSchedule` (`:364-413`) enforces per-kind `+1` ordinal
contiguity *within* a schedule. `HardwareTimingStreamLoader` bounds `raw_frame` to
`[0, traceFrameCount)` (`:88-91`).

**[I]** Under Phase 4's `exportableAcrossSegment = false` and Phase 6.4's
armed-segment-only ledger, the intended steady state at every one of the 33
handoffs is: **zero pending submissions, zero unconsumed edges, and the next
segment's first ordinal equal to the previous segment's last + 1.**

**Work.** No new mechanism. Add the assertions that pin the intended state, and
the negative coverage the contract's failure semantics require.

**Acceptance.**

- A run-chain test over the published fixture asserts, at every handoff: no
  pending `NEMESIS_PLC_QUEUE` submission; no unconsumed edge; ordinal continuity
  across the boundary.
- Negative coverage, each failing structurally with both sides named: a missing
  edge; a duplicate edge; a reordered pair; an edge at `post_objects`; a
  wrong-fingerprint edge; an edge whose job is unprepared; an edge in an
  unrepresented gap; a pending submission at a handoff.
- A shifted-but-valid edge produces an ordinary strict comparison failure, not a
  structural one.

---

## Phase 9 — Fixture regeneration

Blocked on M1, M6 and Phases 4-8 being green. Regeneration is explicitly allowed
and is not a separate approval gate; the **publication** contract still applies in
full.

**Scope.** `s1-sonic-complete-withemeralds` only. **[E]** No other S1 fixture
needs one: standard S1 fixtures are single-act and never cross a level-load
boundary, `s1-ghz-maze-roundtrip` has only bridge splits, and the 14
non-`ghz2_2` cases are fixed by Phase 5's revert, whose only production consumer
was `LiveTraceComparator`. No S2 fixture and **no S3K fixture** — admission review
§7.4.

**Procedure**, per `tools/bizhawk-headless/CLAUDE.md`'s publication contract:

1. Capture into scratch. Never hand-edit an event.
2. Freeze digests, lengths, event counts, ordering, ranges and semantic inventory
   as immutable evidence *before* comparing.
3. Categorise every byte-level delta against a named cause. The expected deltas
   are: 34 new `hardware_timing.jsonl` files, and the `trace_schema` integer if
   Phase 12 has landed. **Any physics or aux delta is unexplained and blocks
   publication.**
4. Cross-check the total edge count against M1 and the ordinal range against M6.
5. Obtain explicit user approval of the exact candidate, then copy byte-for-byte.
6. Payloads compress at publication; never commit an uncompressed `physics*.csv`
   or `aux_state*.jsonl` (`TestTraceFixtureCompressionGuard`).
7. `tools/bizhawk/trace_output.s1-complete-emeralds-backup/` is an untracked
   backup from earlier work. Leave it alone.

**Acceptance.** Committed tests use frozen literal expectations and must not
compute them by invoking the recorder that produced the candidate. The fixture
loads, validates under `tools/traces/validate_trace_v5.py`, and Phase 10 runs
against it.

---

## Phase 10 — End-to-end acceptance

- Both lanes of `TestS1CompleteEmeraldVisualRun` pass at their current pins;
  `replaysTheSecondGiantRingAndTheSpecialStageBehindIt` is restored, now passing
  against the stream rather than against the deferral.
- The lane's pin then moves, and the new stopping point is confirmed to be a
  **different** error — not `ghz2_2` 107 or `mz2_3` 101 relocated.
- `TestS1Mz3CompleteRunTraceReplay` and the rest of the S1 `*TraceReplay` fleet
  keep M8's pass set. The other 14 cases live there and must be fixed by the
  revert alone, with no timing stream present.
- `TestHardwareTimingAuthorityGuard` green, **unmodified**.
  `TestS1S2PlcComparisonOnlyGuard` green with the Phase 3 replacement.
  `TestCommittedHardwareTimingFixtures` green.
- All previously-green non-LBZ S3K replays and the required S3K
  bootstrap/loading guards green.
- Live (non-trace) S1 play arms from the production scheduler. A live AIZ/HCZ-style
  acceptance check has no S1 analogue; the equivalent evidence is that a
  no-timing-stream S1 replay of the same acts produces identical queue columns.
- Frontier log updated with command, commit/worktree context, pass/fail, error
  count and first-error frame/field for each of Phase 5, Phase 8 and Phase 10.

---

## Phase 11 — Documentation

- Amend the cross-game contract's Completion-event-schema section with the
  scoped `compressedLength` waiver (admission review §6), and move `PLC_QUEUE`
  from the non-authoritative candidate list to *admitted as `NEMESIS_PLC_QUEUE`,
  scoped to `Level_MainLoop`'s `RunPLC`*, leaving `LEVEL_LOAD`, VDP transfer
  fences and plane-draw fences rejected/pending as they are.
- Correct `2026-07-28-s1-s2-plc-service-queues.md`'s stale boundary order and
  record that its deferred-fallback clause has now been exercised.
- Update the *Sonic 1/2 Native PLC Readiness* and *Hardware-Timing Replay Input
  Exception* entries in `docs/status/known-discrepancies.md`.
- Update `plc-system` (and `s1-trace-replay` if it names the deferral) in
  `.agents/skills/` **and** the `.claude/skills/` mirror.
- Update `tools/bizhawk-headless/docs/s1-run-mode-behavior.md` and
  `s1-trace-recorder-behavior.md` for the new output file — keeping their existing
  statement that the `hardware_timing_schema` *metadata key* is absent.
- `CHANGELOG.md`, and `AGENTS.md` / `CLAUDE.md` if hard rule 4's wording needs the
  admitted-kind list refreshed.

---

## Phase 12 — Schema bump

Per the user's decision: `trace_schema` 5 → 6, **no compatibility handling, no
shim, no migration path**.

**[E]** Sites: `TraceMetadata.java:495-498`; `TraceRunManifest.java:41` (with
`:177`, `:183`, `:391`); `TraceContract.cs:12`; `tools/traces/validate_trace_v5.py`;
and the `trace_schema` integer in every committed `metadata.json` and
`run_manifest.json`.

**One commit.** `TraceMetadata` rejects any value but the current one with no
fallback, so a partial bump leaves the whole suite red. This is a mechanical
integer re-stamp — no payload byte changes — but it touches the entire corpus.

Sequence it **last**, after Phase 9's publication, so the emerald run is captured
and approved once rather than twice.

**Acceptance.** Every committed fixture loads; the full `*TraceReplay` sweep
matches Phase 10's result exactly; `validate_trace_v5.py` (renamed to match)
accepts the corpus.

---

## Phase dependency summary

```
M1 M2 M3 M5 ── Phase 0 ─┬─────────────────────────────┐
                        │                             │
M4 ─────────────────────┤                             │
                        v                             v
        Phase 1 ──> Phase 2 ──> Phase 3 ──> Phase 4 ──> Phase 6 ──> Phase 8
                                                │           │          │
M7 M8 ──────────────> Phase 5 ──────────────────┘           │          │
                                                            v          v
                                            Phase 7 ──> Phase 9 ──> Phase 10
                                                                       │
                                                        Phase 11 ──────┤
                                                                       v
                                                                   Phase 12
```

Phases 1, 2, 3, 5 and 7 are independently landable and independently verifiable.
Phase 5 is the one that moves the frontier on its own, and should be measured
alone.
