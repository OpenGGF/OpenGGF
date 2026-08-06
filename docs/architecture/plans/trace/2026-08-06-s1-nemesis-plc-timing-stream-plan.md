# Plan: a recorded hardware-timing readiness stream for the Nemesis PLC queue

Date: 2026-08-06 (revision 2)
Status: **Planned, not built.** This revision supersedes the earlier 2026-08-06
draft at this path in its entirety. Revision 2 re-sequences the `99746ffa9`
revert to first, promotes M3 to a stop-gate, adds the `ghz2_2` end-to-end phase,
and closes an unenforced guard gap.

Governing documents:

- Admission gate — [`../../designs/2026-08-06-s1-nemesis-plc-timing-kind-admission-review.md`](../../designs/2026-08-06-s1-nemesis-plc-timing-kind-admission-review.md).
  Every contract decision below is settled there; this plan does not re-argue any
  of them.
- Contract — [`../../designs/2026-07-27-cross-game-hardware-timing-trace-contract.md`](../../designs/2026-07-27-cross-game-hardware-timing-trace-contract.md).
- Observability evidence — [`../../research/trace/2026-08-06-s1-plc-arming-row-observability.md`](../../research/trace/2026-08-06-s1-plc-arming-row-observability.md).
- Address evidence — [`../../research/trace/2026-07-28-s1-s2-plc-readiness-evidence.md`](../../research/trace/2026-07-28-s1-s2-plc-readiness-evidence.md).

## The goal

> **Any S1 BK2 — including movies nobody has recorded yet — replays at correct PLC
> arming timing, using a companion timing sidecar that is generated mechanically
> from that movie.**

This is a *capability*, not a fixture fix. `ghz2_2` 107 is the first reachable
instance of the class, and its value is that it is a **known** instance against
which the mechanism can be proved before unknown ones depend on it. It is not the
deliverable.

Two things follow, and they shape every phase below.

- **The sidecar must be cheap and automatic**, or the capability is theoretical.
  Phase 7a specifies a timing-only recorder mode: an arbitrary BK2 in, a
  segmented `hardware_timing.jsonl` set out, with no physics or aux payload.
- **Invariants must be route-independent.** Anything measured once on one route
  proves the mechanism; it must not become a constant. The generalisation
  mechanism throughout is the same — *gate on a ROM-state predicate evaluated per
  frame, and hard-fail on anything that does not match*. Review §11.5 tabulates
  which properties generalise and which do not.

**Scope boundary, stated up front:** the sidecar removes exactly one divergence
class. It does not make an arbitrary movie replay clean, and it does not confer
verifiability — detecting a desync still requires a recorded comparison payload.
Review §11 draws the full boundary, including the one structural gap (§11.4) that
does not generalise.

## What changed from the superseded draft

Recorded because the earlier draft is cited elsewhere and its errors should not
propagate.

| Superseded claim | Status |
|---|---|
| "ROM code polls `v_plc_patternsleft`" | **Wrong.** `v_plc_patternsleft` has six touches, all inside the PLC service block. The polled gate is `v_plc_buffer`. Review §1. |
| `compressedLength` must be carried; both sides need a Nemesis scanner; "do not shortcut this" | **Reversed.** Waived, with the XOR-mode bit moved into `compressionVariant`. Review §6. This deletes the largest line item on both sides. |
| Submit "one `HardwareWorkSubmission` per PLC entry at append/replace" | **Reversed.** Submission is at the arming decision, not at append. Append-time submission creates `ClearPLC` orphans that can never be released under `RECORDED` admission. Phase 5. |
| "Ordinal allocation across `ClearPLC`/`LoadPLC2` needs measuring before it can be written" | **Dissolved as an ordinal problem, retained as a producer-ordering problem.** `ClearPLC`/`LoadPLC2` drop *queued, never-armed* entries, which are now never submitted and consume no ordinal on either side. But M3 is **promoted to a stop-gate** for a different reason — the `SignpostArtLoad` ordering hazard, below. |
| "The 30-plus segment handoffs are the largest correctness risk" | **Superseded by a larger one.** The largest risk is fail-closed starvation of the level-load / title-card drain and the 21 inter-segment gaps, which the draft did not identify. Review §9; Phase 2 and Phase 5 here. |
| Decision gate 1 ("reopening the registry is a reviewed decision") | **Discharged** by the admission review. |
| Decision gate 2 ("fixture regeneration is a user decision") | **Discharged** — the user has explicitly allowed regeneration. It is no longer a gate, only a step (Phase 10). |
| Boundary order `VINT_SERVICE -> PRE_MAIN_LOOP -> objects -> POST_OBJECTS` (from `2026-07-28-s1-s2-plc-service-queues.md`) | **Stale.** Actual order is `VINT_SERVICE` → objects → `POST_OBJECTS` → `PRE_MAIN_LOOP` → `prepareAfterLoop` (`LevelFrameStep.java:144-150`, `HardwareServiceBoundary` javadoc). Phase 12 corrects the doc. |
| Revision 1's phase order (revert fifth) | **Re-sequenced.** The revert is now Phase 1: it depends only on M7/M8, it is the pure correctness fix, it closes 14 of the 15 cases on its own, and landing it shrinks the surface every later phase is measured against. |
| Revision 2's `SignpostArtLoad` framing ("the engine's signpost producer runs in the object scan, inverting the ROM's order") | **Wrong; withdrawn.** `LevelFrameStep.java:415-418` runs the loop-tail slot *after* `PRE_MAIN_LOOP` (`:362`), with a comment stating the constraint. The engine already matches the ROM. M3 survives, rescoped to the queue kernel's clear-over-armed-decoder precondition, and is a gate on Phase 5 rather than on the programme. |
| Revision 1 and 2's deliverable ("give the emerald fixture a stream so `ghz2_2` 107 stops failing") | **Reframed as a capability** — any S1 BK2, including unrecorded ones, replays at correct arming timing from a mechanically generated sidecar. Adds Phase 7a and the §11.1 capability criterion, and re-marks M1/M2 as route-specific. |

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
| **M1** | For each of the 34 segments of `s1-sonic-complete-withemeralds`, the set of raw frames in `[bk2_frame_offset, bk2_frame_offset + trace_frame_count)` on which the arming path `0x0015F0` executes. **Route-specific: validates the mechanism, and must never become a committed expectation** (review §11.5). | 7, 9, 10 | Extend the existing probe (see M-method) with the manifest's segment windows. |
| **M2** | **Stop-gate for this route; a known non-generalising limit for others.** Whether the arming path executes on the first *gap* row immediately after any segment's last recorded row. Review §11.4. | 5 (exportability), 7 | Same probe pass as M1. |
| **M3** | **Gate on Phase 5, not on the programme.** Whether any `NewPLC` (`sonic.asm:1336`) or `ClearPLC` (`:1363`) executes **in the same `Level_MainLoop` iteration as, and after, an arming** — the `SignpostArtLoad` hazard below. Secondarily, whether any `LoadPLC`→`ClearPLC` pair occurs inside one raw frame. | 5 | Add hooks at the reviewed `append begin` `0x001578`, `replace begin` `0x0015AA`, `clear begin` `0x0015DA` boundaries, plus `SignpostArtLoad` entry, and record intra-frame ordering against `0x0015F0`. |
| **M4** | The `PlcLifecyclePhase` the engine claims on every recorded row of every segment, and whether any row on which `0x0015F0` fires claims anything other than `ORDINARY_LEVEL`. | 5, 7 | Engine-side instrumented dry run over the fixture, cross-referenced against M1. |
| **M5** | Whether `v_vblank_routine` (`$FFFFF62A`) is `id_VBlank_Levels` (`$08`) on every armed-segment row on which `0x0015F0` fires. The recorder-side equivalent of M4. | 7 | Probe pass; requires adding `VblankRoutine` to `S1Ram.cs`. |
| **M6** | Whether the engine's arming *decisions* on recorded rows match the ROM's armings 1:1 in count and order, before any edge is applied. A count divergence fails closed but must be known before fixture capture, not after. | 7, 10 | Engine dry run with a diagnostic-only arming log, diffed against M1. |
| **M7** | Whether the comparator already treats the engine's idle-with-queued diagnostic (`prepared=false, remaining=-1`, `NemesisPlcServiceQueue.java:94-105`) as equal to the recorder's `PlcPatternsLeft == 0` on the denied row. Review §10. | 1, 6, 11 | Focused replay of `ghz2_2` around row 107 with the deferral forced. |
| **M8** | The full `*TraceReplay` and visual-run baseline immediately before Phase 1's revert: pass set, error counts, first-error frame/field. | 1, 11 | `mvn -Dmse=off -Dtest='*TraceReplay' -DfailIfNoTests=false` plus both visual-run lanes, all three ROM properties. |

**M-method.** M1, M2, M3 and M5 are all one additional pass of the throwaway
native probe described in the observability research
(`tools/bizhawk-headless/.scratch/PlcProbe.cs`, untracked because `.gitignore`
ignores `tools/*`). **`PlcProbe.cs` and `PlcProbe2.cs` already exist in
`.scratch/` — read them before writing anything new there.** The probe is not part
of the harness build and must not become one.

### Why M3 is a gate: the `SignpostArtLoad` clear-over-armed-decoder hazard

**A review pass framed this as an ordering inversion between the ROM and the
engine. That framing was checked and is wrong; the corrected, narrower hazard is
below.** The gate is retained because what remains is still a real precondition
failure, but it is scoped to the queue kernel and does **not** invalidate Phase 5's
submission model.

**[E]** The ROM's loop tail is:

```
3032		bsr.w	RunPLC					; run PLC, if any
3033		bsr.w	OscillateNumDo
3034		bsr.w	SynchroAnimate
3035		bsr.w	SignpostArtLoad				; ... and lock left boundary
```

and `SignpostArtLoad` ends:

```
3204		moveq	#plcid_Signpost,d0
3205		bra.w	NewPLC					; add to new PLC queue
```

**[E]** `NewPLC` (`:1336`) begins with `ClearPLC` (`:1363`). So within one
`Level_MainLoop` iteration the ROM can arm a head at `3032` and then **wipe the
queue** at `3035`.

**[E] The engine already reproduces that order exactly.**
`LevelFrameStep.java:415-418` runs
`LevelEventProvider.updateAtLevelLoopTail()` *after*
`serviceBoundary(PRE_MAIN_LOOP)` (`:362`) and after `prepareAfterLoop` (`:364`),
under a comment stating the constraint outright — *"this runs after RunPLC in the
same frame, so work queued here is only serviced from the next frame"* — and
`LevelEventProvider.java:181-198` documents the slot as ROM `sonic.asm:3032`'s
tail. `Sonic1LevelEventManager:172` is the S1 implementation. So both sides arm
the outgoing head and then clear. **Phase 5's submission model is not at risk from
this**, and the earlier claim that the engine's signpost producer runs in the
object scan is withdrawn.

**[I]** What survives is narrower, pre-existing, and owned elsewhere.
`NemesisPlcServiceQueue.clearQueued` carries the idle-decoder precondition from
`2026-07-28-s1-s2-plc-service-queues.md`'s Task 1 gate and **fails rather than
silently discarding work** when a decoder is active. The arming lands one slot
earlier in the same iteration than the signpost clear, so a coincidence of the two
drives `clearQueued` against a freshly armed decoder — which is exactly the retail
aliasing that gate was written for (*"`ClearPLC` zeroes that longword without
clearing the other decoder scalars"*), now reachable at the arming site.

**[E]** The exposure is bounded and cheap to inspect. `SignpostArtLoad` returns
early on `v_debuguse` and on `act3`, and latches `v_limitleft2` so it fires **at
most once per act** — roughly 22 candidate iterations across the emerald run.

**If M3 fires**, the fix is owned by the queue kernel's clear/replace semantics —
re-answer the Task 1 gate for the case "clear arrives one slot after an arming in
the same iteration", from the ROM's actual aliasing behaviour. Do **not** reorder
the engine's loop-tail slot to suit the port (it is already correct), and do not
special-case the affected iteration. The timing port's model stands either way,
which is why this is a scoped gate on Phase 5 rather than a stop-gate on the
programme.

---

## Phase 1 — Revert `99746ffa9`

**Mandatory independently of everything else in this plan.** It is the pure
correctness fix: `99746ffa9`'s model is right 1 time in 15 and the revert is right
14. If the admission is later withdrawn, or the programme is descheduled behind
the S3K vertical slice, **this phase still lands.** Nothing below it is a
prerequisite; it is blocked only on M7 and M8.

Landing it first also shrinks the surface every later phase is measured against:
after Phase 1 the only remaining S1 arming divergence in the corpus is `ghz2_2`
107, so any new failure introduced by Phases 2-11 is unambiguously attributable.

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
  is wrong, and every phase below it is built on that model.
- `TestS1CompleteEmeraldVisualRun`'s second test is removed in the same commit
  that removes the behaviour it pins; it is re-added in Phase 6 against a
  synthetic stream and re-verified in Phase 11 against the published one.
- Frontier log updated with both measurements.

---

## Phase 0 — Measurement pass A (M1, M2, M3, M5)

Numbered 0 because it gates the *specification* of Phases 5 and 7 rather than
their prerequisites; it can run concurrently with Phase 1, which needs none of it.

No production change. Produces the numbers Phases 5 and 7 are written against.

**Deliverable.** A short addendum to the observability research doc carrying: the
per-segment arming table (M1), the gap-row answer (M2), the
arming-versus-`NewPLC`/`ClearPLC` intra-iteration ordering answer (M3), and the
V-blank-routine answer (M5). Not a new document — the research doc is the owner.

**Acceptance.** The addendum exists, is committed, and its per-segment arming
counts sum to a number Phase 7 can pin as the expected edge count.

**Stop-gates.** Two, and both halt the programme rather than being worked around:

- **M2 positive** — an arming falls on a gap row after a segment. Phase 5's
  `exportableAcrossSegment = false` then produces a hard failure at that handoff.
  Do not paper over it by exporting the submission.
- **M3 positive** — a `NewPLC`/`ClearPLC` follows an arming inside one iteration.
  Phase 5's submission model is invalid as written; see the hazard note above.

Phase 1 is unaffected by either and proceeds regardless.

---

## Phase 2 — Bound every PLC-drain consumer

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
keeps Phase 1's post-revert baseline.

---

## Phase 3 — Kind, wire name, registry, boundary coupling

Smallest self-contained production step. Behaviourally inert until Phase 5
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

## Phase 4 — Replace the registry guard, and close the trace-import gap

Separate commit from Phase 3 so the guard change is visible in isolation and
reviewable on its own.

### 4.1 The unenforced prohibition

**[E]** The `com.openggf.trace` ban this design relies on does **not** currently
cover the classes it adds to the readiness path:

- `TestHardwareTimingAuthorityGuard` forbids gameplay owners from reaching
  `TIMING_PACKAGE_PREFIX = "com.openggf.trace.timing"` (`:28`, `:595-614`) — the
  *parser* package only. It says nothing about the rest of `com.openggf.trace`.
- The broader ban lives in
  `TestS1S2PlcComparisonOnlyGuard.nativePlcServicesDoNotDependOnTracePackages`,
  whose `PLC_SERVICES` scan list (`:29-31`) is exactly
  `{Sonic1PlcService, Sonic2PlcService}`.

**[I]** A plain `import com.openggf.trace.TraceMetadata;` in
`Sonic1RuntimeArtCoordinator` or `NemesisPlcServiceQueue` therefore trips
**neither guard**. Closing this is required, not optional hardening.

### 4.2 Work

1. Extend `PLC_SERVICES` (`:29-31`) to include
   `com.openggf.game.sonic1.resources.Sonic1RuntimeArtCoordinator` and
   `com.openggf.level.resources.NemesisPlcServiceQueue`. Rename the constant to
   reflect that it is now the PLC *readiness path*, not just the two services.
   **This must land in the same commit as, or before, Phase 5** — the guard is
   worthless if it arrives after the class it is meant to constrain.
2. Rewrite `timingKindRegistryAdmitsOnlyKosinskiWork` (`:79-90`) per admission
   review §5.5 — five assertions:
   1. `HardwareWorkKind` values are exactly
      `{KOS_MODULE_QUEUE, KOS_DECOMPRESSION_QUEUE, NEMESIS_PLC_QUEUE}`.
   2. Kinds whose name contains `PLC` are exactly `{NEMESIS_PLC_QUEUE}`, with the
      message rewritten to *"S1/S2 PLC readiness is native deterministic service
      except at the reviewed `Level_MainLoop` arming site"*.
   3. `com.openggf.level.resources.NemesisPlcServiceQueue` does not import
      `com.openggf.game.timing` — the shared kernel stays clean so S2 cannot
      inherit the authority through it. **New assertion; the substantive
      replacement for what the old test protected.**
   4. The only production source constructing a `HardwareWorkSubmission` with
      `NEMESIS_PLC_QUEUE` is
      `com.openggf.game.sonic1.resources.Sonic1RuntimeArtCoordinator`.
   5. The only kind submitted with `compressedLength == 0` is
      `NEMESIS_PLC_QUEUE` (confines the §6 waiver).

Note that assertions 2.3 and 4.2.1 are complementary, not redundant: 4.2.1 keeps
`com.openggf.trace` out of the kernel, 2.3 keeps `com.openggf.game.timing` out of
it. The kernel must reach neither.

The class's other two package-isolation tests
(`traceProductionSourcesDoNotDependOnNativePlcServices`,
`replayAndBootstrapSourcesDoNotReferenceNativePlcServices`) are **unchanged**.

**Acceptance.** The rewritten class passes; each of the six assertions has a
crafted-violation negative self-test in the style
`TestHardwareTimingAuthorityGuard` already uses — including one that adds
`import com.openggf.trace.TraceMetadata;` to a synthetic coordinator source and
proves the widened scan list rejects it.

---

## Phase 5 — The S1 submission path

The largest engine phase. Blocked on M2, M3, M4 — and on Phase 4's widened scan
list, which must not arrive afterwards.

### 5.1 Where the code goes

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

### 5.2 Submission point and per-boundary contract

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

### 5.3 The submission descriptor

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

### 5.4 Claim and retirement

Step 4 claims every ready `NEMESIS_PLC_QUEUE` job in FIFO order and, for each,
calls the queue's `prepareHead()`. Everything after that — the 3-or-9-pattern
budget, the per-frame decrement, `ProcessPLC_ShiftCue`'s retirement — stays
exactly where it is in `NemesisPlcServiceQueue.servicePatterns` (`:52-68`). The
timing port never touches it.

### 5.5 Package isolation

`Sonic1PlcService`, `Sonic1RuntimeArtCoordinator` and `NemesisPlcServiceQueue` may
import `com.openggf.game.timing` but never `com.openggf.trace` in any form.
**[E]** As Phase 4.1 records, that is only true once Phase 4's scan list has
landed — today neither guard covers the two new classes.
`PlcFrameLifecycleCoordinator` is a gameplay owner and gains no timing import at
all. The route is the one `S3kKosModuleQueue` already uses: hold an injected
`HardwareTimingService`, ask `timing.isReady(handle)`, never know a trace exists.

**Acceptance.**

- New `TestSonic1RuntimeArtCoordinator`: submits exactly once per arming
  opportunity; does not resubmit while an arming is unclaimed; submits nothing for
  `LEVEL_TITLE_CARD`, `PALETTE_FADE`, `CREDITS_TEXT`, `SPECIAL_STAGE_RESULTS`,
  `TITLE_SCREEN`, `LEVEL_SELECT` or `CREDITS_DEMO`; the descriptor matches the
  §5.3 tuple for a known ROM PLC id; the XOR-mode entry produces `"nemesis_xor"`.
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
  exactly where it does today. A focused S1 `*TraceReplay` subset must keep
  Phase 1's post-revert baseline with no timing stream present.

---

## Phase 6 — The `ghz2_2` shape, end to end, against a synthetic stream

**This phase exists because it is the one row the entire programme is for, and
without it the first end-to-end proof would arrive after Phase 10's expensive
capture.** It needs no recorder, no published fixture, and no re-record: a
hand-built `HardwareTimingSchedule` and a two-row synthetic trace are sufficient.

Blocked on Phase 5. Blocks Phase 10.

### 6.1 The shape to reproduce

From the probe, `ghz2_2` rows 107-108 (raw 9848-9849):

```
raw    order  patsleft  meaning
9848   3S     0         service, ProcessPLC_ShiftCue retires the head, RunPLC never entered
9849   RAW    14        lag row: no ProcessPLC service, but the whole of RunPLC runs
```

The engine must produce: submission at row `f`'s `PRE_MAIN_LOOP`, **denied**;
row `f+1` executed as a lag row with `VINT_SERVICE` only and no PLC service;
suppressed-row admission of the `pre_main_loop` edge on that lag row; the
coordinator's post-service hook claiming it and calling `prepareHead()`, leaving
`remaining = 14` at row `f+1`'s sample point.

### 6.2 The unknown this de-risks

**[A]** It is not established that the S1 lag-row path reaches
`TraceSuppressedRowClosure` the way S3K's does. **[E]** S1 lag rows go through
`LevelFrameStep`'s `VINT_SERVICE`-only entry (`:123-128`) and
`serviceVBlankOnly(..., LAG)`; **[E]** `applySuppressedRowCompletion`
(`HardwareTimingReplayPort.java:143-166`) requires `lastAppliedBoundary ==
VINT_SERVICE` on the latched current row, which that path satisfies. **[A]** What
is *not* established is that the S1 replay driver invokes the closure at all —
the closure is currently exercised only by S3K routes.

If it does not, that is a plan amendment, not a bug to patch at the edge: the
correct fix is to route S1 lag rows through the same closure the contract already
defines, **not** to invent a second admission path or relax
`applySuppressedRowCompletion`'s precondition.

### 6.3 Work

1. A focused replay-level test constructing a `HardwareTimingSchedule` with one
   `NEMESIS_PLC_QUEUE` edge at `(raw_frame = f+1, pre_main_loop, ordinal 0,
   fingerprint F)`, a two-row trace whose second row is a lag row, and a seeded
   PLC queue whose head hashes to `F`.
2. Assert the full sequence: denied at `f`; queue state at `f` matches the ROM's
   idle-with-queued shape (this is M7's question, and this test is where its
   answer is pinned); admitted at `f+1`; `remaining` correct at `f+1`; the edge
   consumed exactly once.
3. Negative twins on the same fixture: no edge → the arming never happens and the
   consumer's bounded wait (Phase 2) fails with a named message; edge one row
   late → structural failure; edge at `post_objects` → rejected at load.
4. Rewind twin: snapshot on the denied row, restore, replay — the edge is
   consumed exactly once again.

**Acceptance.** All of the above green, with **no published fixture and no
recorder involvement**. `TestS1CompleteEmeraldVisualRun`'s
`replaysTheSecondGiantRingAndTheSpecialStageBehindIt`, removed in Phase 1, is
re-added here in its synthetic form and re-verified against the real stream in
Phase 11.

**Gate.** Phase 10's capture does not start until this phase is green. Discovering
the §6.2 unknown after a 209k-frame capture is the specific outcome this phase
exists to prevent.

---

## Phase 7 — Recorder: `S1NemesisPlcTimingEventEngine`

Blocked on M1, M2, M4, M5, M6 and Phase 6. Mirrors the S3K engine's structure;
does not invent a parallel mechanism.

### 7.1 The hook

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

### 7.2 The harness rule this needs amended

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

### 7.3 The engine

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

### 7.4 Ordinal allocation — the deliberate divergence from S3K

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

### 7.5 Plumbing

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
  fingerprint golden values matching the Phase 5 vector file; the ordinal ledger
  advances only inside armed segments; an arming under a non-`$08` V-blank routine
  inside an armed segment throws; `Reset` clears the ledger; the emitted line is
  byte-exact.
- The three S1 no-metadata-key tests and `TraceCliTests` green.
- A scratch capture of a short S1 movie produces a well-formed
  `hardware_timing.jsonl` that `tools/traces/validate_trace_v5.py` accepts and
  `HardwareTimingStreamLoader` loads.

---

## Phase 7a — The timing-only sidecar mode

**This is the phase that turns a fixture fix into a capability, and it is
first-class rather than incidental.** Blocked on Phase 7.

### 7a.1 What it must produce, and the constraint that shapes it

**[E]** `raw_frame` is a **segment-relative row index**, hard-bounded by that
segment's own row count (`HardwareTimingStreamLoader.loadVersion:88-91`) and
compiled against the segment's rows by
`TraceHardwareTimingScheduleCompiler.compileForInstall`. A bare list of edges is
therefore not consumable: the sidecar must also carry the **segmentation** — the
per-segment `bk2_frame_offset` and row count — or nothing can resolve an edge to a
row.

So "timing-only" means *no comparison payload*, not *no structure*:

| Artefact | Full capture | Timing sidecar |
|---|---|---|
| `physics.csv` | yes | **no** |
| `aux_state.jsonl` | yes | **no** |
| `hardware_timing.jsonl` (per segment) | yes | **yes** |
| `metadata.json` (per segment) | yes | **yes** — row counts and offsets only |
| `run_manifest.json` | yes | **yes** |

**[I]** The segmentation comes from the same arm predicate the full runner already
applies (`v_gamemode == 0x0C && obCtrlLock == 0`), so it is the same code path
with the payload writers suppressed — not a second segmentation implementation. A
second implementation would be a source of drift and must not be built.

### 7a.2 Work

**[E]** `S1RunCaptureRunner` already threads its payload writers through
`RunSegmentStreams` / `StagedRunSegmentSink`, so the mode is a sink variant plus a
CLI flag, not a new runner:

1. A `--timing-only` (or equivalently named) flag on the S1 run capture command,
   which selects a sink that opens the timing and metadata streams and binds
   `TextWriter.Null` for physics and aux.
2. Suppress the per-row projector work that only feeds the suppressed payloads —
   `S1AuxEventEngine.ProcessFrame` and `LoadQueueStateProjector.CaptureS1` — while
   **keeping** the arm/finalize segment lifecycle and the timing engine's
   `ObserveFrameEnd`. This is where the runtime saving comes from.
3. `Program.cs`'s output-filename registration and `:550` no-replace preflight
   must accept the reduced file set rather than demanding the full one.
4. The run manifest writer must emit a manifest that `TraceRunManifest` accepts
   with no payload capabilities declared. **[A]** Whether it currently can is
   unverified — `TraceRunManifest.java:391` rejects a `trace_schema: 5` segment
   that *"omits dynamic-art capability"*, and the loader may require payload
   capabilities that a timing-only manifest cannot honestly declare. **This is
   deferred measurement M9.**

### 7a.3 Deferred measurement M9

| id | Measurement | Blocks | Method |
|---|---|---|---|
| **M9** | Whether `TraceRunManifest` and `TraceMetadata` will load a manifest/metadata pair that declares no physics or aux payload, and if not, precisely which declared capabilities are mandatory. | 7a | Read `TraceRunManifest.java:170-200, 380-400` and `TraceMetadata.java:480-520`; construct a minimal manifest and attempt a load. |

**Do not guess the answer.** If mandatory payload capabilities exist, the honest
options are (a) relax them for a declared timing-only manifest kind, or (b) accept
that the sidecar ships alongside a payload capture. Option (b) would substantially
weaken the capability claim and must be surfaced, not absorbed.

### 7a.4 Runtime expectation

**[A]** No runtime figure is stated here because none has been measured. What is
**[E]** known: a full S1 emerald capture is roughly 3.5 minutes of native capture,
and the payload writers plus `S1AuxEventEngine` / `LoadQueueStateProjector` are
the per-row work being removed while emulation itself is unchanged. Emulation
therefore bounds the sidecar from below and the full capture bounds it from above.
**M10: measure the timing-only wall-clock and output size against the full capture
on the same movie**, and record both. A sidecar that is not materially cheaper
than a full capture has not delivered this phase's purpose and the phase should be
re-examined rather than declared done.

**Acceptance.**

- The mode runs over a short S1 movie the fixture corpus has never seen and emits
  a segmented timing set that `tools/traces/validate_trace_v5.py` accepts and
  `HardwareTimingStreamLoader` loads.
- The same movie, captured in full and in timing-only mode, produces
  **byte-identical** `hardware_timing.jsonl` files. This is the anti-drift check
  and is the single most important assertion in the phase.
- The engine replays that previously-unseen movie with the sidecar installed, and
  every edge is consumed exactly once with no structural failure.
- M10 recorded.

---

## Phase 8 — Rewind ledger coverage

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
- Phase 6's rewind twin still green.

---

## Phase 9 — Segment handoff and ordinal continuity

Blocked on M1, M2.

**[E]** The emerald run has **34** segments and 21 inter-segment level-load gaps
of 216-236 rows. `handoffTo` (`:203-249`) enforces: policy equality; no
next-segment edge repeating a consumed identity; every pending submission
exportable **and** matched by a next-segment edge on `(kind, ordinal,
fingerprint)`. `validateSchedule` (`:364-413`) enforces per-kind `+1` ordinal
contiguity *within* a schedule. `HardwareTimingStreamLoader` bounds `raw_frame` to
`[0, traceFrameCount)` (`:88-91`).

**[I]** Under Phase 5's `exportableAcrossSegment = false` and Phase 7.4's
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

## Phase 10 — Fixture regeneration

Blocked on M1, M6, **Phase 6 green**, and Phases 5, 7, 8, 9 green. Regeneration
is explicitly allowed and is not a separate approval gate; the **publication**
contract still applies in full.

**Scope.** `s1-sonic-complete-withemeralds` only. **[E]** No other S1 fixture
needs one: standard S1 fixtures are single-act and never cross a level-load
boundary, `s1-ghz-maze-roundtrip` has only bridge splits, and the 14
non-`ghz2_2` cases are fixed by Phase 1's revert, whose only production consumer
was `LiveTraceComparator`. No S2 fixture and **no S3K fixture** — admission review
§7.4.

**Procedure**, per `tools/bizhawk-headless/CLAUDE.md`'s publication contract:

1. Capture into scratch. Never hand-edit an event.
2. Freeze digests, lengths, event counts, ordering, ranges and semantic inventory
   as immutable evidence *before* comparing.
3. Categorise every byte-level delta against a named cause. The expected deltas
   are: 34 new `hardware_timing.jsonl` files, and the `trace_schema` integer if
   Phase 13 has landed. **Any physics or aux delta is unexplained and blocks
   publication.**
4. Cross-check the total edge count against M1 and the ordinal range against M6.
5. Obtain explicit user approval of the exact candidate, then copy byte-for-byte.
6. Payloads compress at publication; never commit an uncompressed `physics*.csv`
   or `aux_state*.jsonl` (`TestTraceFixtureCompressionGuard`).
7. `tools/bizhawk/trace_output.s1-complete-emeralds-backup/` is an untracked
   backup from earlier work. Leave it alone.

**Acceptance.** Committed tests use frozen literal expectations and must not
compute them by invoking the recorder that produced the candidate. The fixture
loads, validates under `tools/traces/validate_trace_v5.py`, and Phase 11 runs
against it.

---

## Phase 11 — End-to-end acceptance

### 11.1 The capability criterion

This is the criterion the programme is judged on, and it must be met on a movie
that was **not** used to develop any of it:

- Take an S1 BK2 outside the committed corpus. Generate its sidecar with Phase
  7a's mode. Replay it. **Every edge is consumed exactly once, no structural
  failure occurs, and no per-movie code, constant, or exception was added to make
  it work.**
- Repeat on a second movie with a materially different shape — a different zone
  order, a death and restart in an unfamiliar place, or a special-stage entry at
  an arbitrary point — so that the route-independence claims of review §11.5 are
  exercised rather than asserted.
- Any failure is triaged against review §11: if it is §11.4's non-generalising
  gap, it must present as the named `handoffTo` failure and be recorded as such,
  not worked around.

**Asserting route-independence is not evidence of it.** If no second movie is
available, say so and mark the criterion `EVIDENCE_INCOMPLETE` rather than
declaring the capability met on one route.

### 11.2 Regression criteria

- Both lanes of `TestS1CompleteEmeraldVisualRun` pass at their current pins;
  `replaysTheSecondGiantRingAndTheSpecialStageBehindIt` is verified against the
  published stream, having already passed against Phase 6's synthetic one.
- The lane's pin then moves, and the new stopping point is confirmed to be a
  **different** error — not `ghz2_2` 107 or `mz2_3` 101 relocated.
- `TestS1Mz3CompleteRunTraceReplay` and the rest of the S1 `*TraceReplay` fleet
  keep Phase 1's post-revert pass set. The other 14 cases live there and must be
  fixed by the revert alone, with no timing stream present.
- `TestHardwareTimingAuthorityGuard` green, **unmodified**.
  `TestS1S2PlcComparisonOnlyGuard` green with the Phase 4 replacement.
  `TestCommittedHardwareTimingFixtures` green.
- All previously-green non-LBZ S3K replays and the required S3K
  bootstrap/loading guards green.
- Live (non-trace) S1 play arms from the production scheduler. A live AIZ/HCZ-style
  acceptance check has no S1 analogue; the equivalent evidence is that a
  no-timing-stream S1 replay of the same acts produces identical queue columns.
- Frontier log updated with command, commit/worktree context, pass/fail, error
  count and first-error frame/field for each of Phase 1, Phase 9 and Phase 11.

---

## Phase 12 — Documentation

- Amend the cross-game contract's Completion-event-schema section with the
  scoped `compressedLength` waiver (admission review §6), and move `PLC_QUEUE`
  from the non-authoritative candidate list to *admitted as `NEMESIS_PLC_QUEUE`,
  scoped to `Level_MainLoop`'s `RunPLC`*, leaving `LEVEL_LOAD`, VDP transfer
  fences and plane-draw fences rejected/pending as they are.
- Correct `2026-07-28-s1-s2-plc-service-queues.md`'s stale boundary order, and
  record both that its deferred-fallback clause has now been exercised and how M3
  re-answered its Task 1 producer/decoder aliasing gate at the arming site.
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

## Phase 13 — Schema bump

Per the user's decision: `trace_schema` 5 → 6, **no compatibility handling, no
shim, no migration path**.

**[E]** Sites: `TraceMetadata.java:495-498`; `TraceRunManifest.java:41` (with
`:177`, `:183`, `:391`); `TraceContract.cs:12`; `tools/traces/validate_trace_v5.py`;
and the `trace_schema` integer in every committed `metadata.json` and
`run_manifest.json`.

**One commit.** `TraceMetadata` rejects any value but the current one with no
fallback, so a partial bump leaves the whole suite red. This is a mechanical
integer re-stamp — no payload byte changes — but it touches the entire corpus.

Sequence it **last**, after Phase 10's publication, so the emerald run is captured
and approved once rather than twice.

**Acceptance.** Every committed fixture loads; the full `*TraceReplay` sweep
matches Phase 11's result exactly; `validate_trace_v5.py` (renamed to match)
accepts the corpus.

---

## Phase dependency summary

```
M7 M8 ─────────────> Phase 1  (revert; MANDATORY, independent of all below)
                        │
M1 M2 M3 M5 ─> Phase 0 ─┤   (concurrent with Phase 1; stop-gates on M2 and M3)
                        │
                        v
        Phase 2 ──> Phase 3 ──> Phase 4 ──> Phase 5 ──> Phase 6 ──> Phase 7
                                                           │           │
                                                           │           v
                                                           │       Phase 7a <── M9
                                                           │           │
                                              Phase 8 <────┘           │
                                                 │                     │
                                              Phase 9                  │
                                                 │                     │
                                                 └──> Phase 10 <───────┘
                                                          │
                                                      Phase 11
                                                          │
                                          Phase 12 ───────┤
                                                          v
                                                      Phase 13
```

Edges the diagram flattens, stated explicitly because the measurement table
carries them: **M4** blocks Phases 5 and 7; **M6** blocks Phases 7 and 10;
**M7** blocks Phases 1, 6 and 11; **M1/M2** additionally block Phase 9;
**M9** blocks Phase 7a; **M10** is recorded by Phase 7a rather than blocking it.
Phase 10 is additionally gated on Phase 6 being green, and Phase 11's §11.1
capability criterion is gated on Phase 7a.

Phases 1, 2, 3, 4 and 8 are independently landable and independently verifiable.
**Phase 1 is the one that moves the frontier on its own, is mandatory regardless
of whether anything else here proceeds, and must be measured alone.** **Phase 7a
is the one the capability claim rests on** — without it the programme delivers a
fixture fix, whatever this document says in its goal.
