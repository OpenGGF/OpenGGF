# Bounded trace-run segment ownership

## Status

Phase-one descriptor planning and descriptor-backed catalog validation were
implemented and measured on 2026-08-21. Actual replay still uses the eager
`SegmentPlan` path. The phase-two active-segment design below was approved on
2026-08-22. Implementation and its replay-memory measurements remain pending.

## Problem

`TraceRunReplayWalker.plan()` retains a `TraceData` for every segment in a run.
Each `TraceData` eagerly owns its parsed physics rows and auxiliary-event
objects. The 67-segment Knuckles super-emerald run contains 1,607,123,581
uncompressed payload bytes, of which 1,548,327,089 are auxiliary JSONL.

A forced-GC class histogram taken after all 67 plans were built reported
1,094,956,904 live bytes. Direct `TraceEvent` implementations accounted for
399,294,016 bytes (36.47%), `CompactFieldMap` for 86,444,992 bytes (7.89%),
and physics/special-stage frame records for 62,909,200 bytes (5.75%). Common
trace-dominated arrays, strings, boxed integers, lists, and map storage
accounted for another 535,363,856 bytes (48.89%). By contrast, hardware-timing
classes occupied 102,864 bytes (0.0094%).

The ownership boundary, rather than parsing throughput, is therefore the
measured problem.

## Phase-one result

`TraceRunReplayWalker.planDescriptors()` now performs the existing validation
scan sequentially and publishes payload-independent summaries.
`TraceCatalog.validateRunLaunch()` consumes those summaries, while
`prepareRunLaunch()` and every replay caller retain the eager `plan()` path.

On the real 67-segment Knuckles super-emerald run, both planners first ran
unmeasured whole-run warmups whose graphs were released before separate
forced-GC measurement phases. The warmed measurement reported 1,087,200,800
retained bytes for eager planning and 8,660,152 bytes for descriptor planning.
The descriptor graph retained 1,078,540,648 fewer
bytes (99.20%) while representing the same 409,630 rows. This is a planning
and catalog-validation result, not a replay-memory result: live, visual, and
audio replay memory is unchanged until the cursor work below is authorised
and implemented.

## Full design decision

Retain compact run and segment plans globally, but own physics and auxiliary
payloads through one closeable, segment-local cursor while replay is driving
that segment.

Planning scans each segment once, in manifest order, and closes it before
opening the next. The scan validates schemas and ledgers and produces an
immutable descriptor containing only:

- metadata, manifest topology, row counts, and the classified execution policy;
- a compact per-row execution-phase vector produced by the existing validated
  phase classifier;
- the opening-row summary needed by bootstrap and return-boundary comparison;
- terminal dynamic-art descriptors and cross-segment ledger summaries;
- the complete hardware-timing schedule and its compact raw-frame mapping;
- a compact per-row lag mapping keyed by segment-local raw frame; and
- scalar bootstrap, level-loop, missing-schema, and special-stage policy values
  currently discovered by whole-payload scans.

Replay opens one segment cursor at entry and closes it at every successful,
failed, or aborted boundary. The cursor provides:

- a physics window containing previous, current, and one-row lookahead;
- all typed auxiliary events for the current and next raw frames, materialised
  only for those rows;
- rolling latest-checkpoint and zone/act state plus an 80-prior-frame
  `SonicRecordPos` diagnostic ring;
- the current dynamic-art row state; and
- special-stage rows through the same segment-local lifetime, using their
  existing game-owned representation initially.

The phase vector is required because the current classifier can inspect two
rows behind and can transitively inspect beyond a single lookahead. Precomputing
its already-existing outcome avoids widening the physics window or changing
which replay loop a row takes. Current-plus-next auxiliary ownership is required
because load-queue projection joins state across adjacent raw frames. The
diagnostic ring preserves mismatch-report context without retaining arbitrary
auxiliary buckets.

Special-stage row formats remain owned by their S1, S2, and S3K readers. Their
payload is removed from the global plan and attached only to the active segment
owner. This bounds run ownership without combining this change with three
profile-schema migrations. The acceptance measurement decides whether that
bounded eager representation is sufficient: if a special-stage payload becomes
the dominant peak and prevents the required reduction, profile-specific
streaming is required before integration.

### Alternatives considered

1. **Selected: stream ordinary rows and bound special stages to the active
   segment.** This removes the measured ordinary physics/auxiliary amplification
   while preserving the three game-owned special-stage contracts.
2. **Stream every special-stage format in this change.** This offers the lowest
   theoretical peak, but adds S1/S2/S3 parser migrations. S2 also requires a
   separate pass-binding and dynamic-art spill-normalisation pass, making this a
   materially larger accuracy risk.
3. **Load one complete eager `TraceData` per segment.** This removes whole-run
   retention but leaves parser/container amplification for the largest active
   level. It does not meet the durable ordinary-level cursor goal.

## Consumer contract

The existing consumers divide into these ownership groups:

| Owner | Required data | Lifetime |
|---|---|---|
| Run plan, manifest, hardware timing, playback coordinator | Metadata, topology, policy scalars, timing schedule, opening and terminal summaries | Whole run |
| Live comparator, row policy, presentation bridge, active dynamic-art comparison | Previous/current/next physics row, compact execution phase, current/next raw-frame auxiliary events, and bounded rolling summaries | Active segment only |
| Special-stage driver | Existing game-owned special-stage rows, pass binding, and spill-normalised comparison data | Active segment only |
| Visual run/audio harness frame view | Segment row counts and lag outcome for a BK2 cursor, including presentation gaps and handoffs | Whole run, compact lag mapping only |
| Trace catalog | Metadata and row counts | Whole run descriptor |

Planning-time validation may scan an entire payload, but it must not publish
payload objects into the whole-run plan. Return-boundary comparison consumes
the immutable destination opening summary, not a prematurely opened destination
cursor. Hardware timing remains global because its measured footprint is
negligible and it coordinates production-submitted work across boundaries.
The recorded lag outcome is likewise retained as a compact per-row scheduling
mapping: `VisualRunReplayHarness.frameView()` resolves an arbitrary BK2 cursor
across every segment and may do so during presentation gaps, when no segment
payload cursor is active. The mapping admits only the already-existing ROM lag
loop; it carries no gameplay value or work identity.

The cursor is run-only and monotonic. Standalone replay keeps eager `TraceData`
and its existing rewind seek support. Run sessions already disable rewind
because a rewind cannot safely cross a structural segment boundary; phase two
does not change that contract.

Segment zero is not opened during title-card setup. Configuration and launch
preparation consume descriptor/bootstrap summaries. The launcher opens the
first active owner immediately before bootstrap and comparator attachment.

## Accuracy constraints

- Trace payloads remain comparison-only. This design does not expand either
  hardware-timing exception.
- Row order, raw-frame identity, previous/current/lookahead semantics, and all
  typed auxiliary lookup results remain identical.
- The stored execution-phase vector is the output of the existing classifier;
  it neither adds a trace authority path nor changes row admission.
- Planning performs the same schema, manifest, phase, hardware-timing, and
  dynamic-art validation before replay begins.
- Auxiliary streaming requires non-decreasing raw-frame order while preserving
  same-frame event order. Planning enforces that constraint before launch so a
  malformed stream cannot fail only after gameplay has started.
- A segment boundary cannot discard its terminal comparison ledger before the
  destination opening comparison has consumed it.
- Cursor cleanup must be deterministic on normal completion, assertion
  failure, constructor failure, and launcher abort.

## Active-owner lifecycle

For every replay driver and harness:

1. retain descriptors only after planning;
2. open segment zero immediately before bootstrap;
3. retain the source owner through source-tail capture, observer detachment,
   special-stage verification, dynamic-art closure, terminal comparison, and
   gap-journal publication;
4. close the source in a failure-preserving `finally` before entering the gap;
5. compare the destination opening summary and perform timing handoff while no
   payload owner is open;
6. open and attach exactly one destination owner; and
7. close the final owner before terminal-tail playback.

Partial construction closes every reader already opened. Session abort, user
exit, comparison failure, production failure, harness failure, and repeated
teardown all close the active owner idempotently. A cleanup exception is
suppressed onto the primary failure rather than replacing it.

## Implementation boundary

Phase one introduced the payload-independent descriptor. Phase two proceeds in
the following compatibility-preserving order:

1. add compact phase/bootstrap summaries and launch-time auxiliary ordering
   validation while preserving eager replay as the reference path;
2. implement failure-atomic ordinary physics and auxiliary cursors, including
   current-plus-next aux ownership and bounded rolling summaries;
3. migrate `LiveTraceComparator`, structural comparison, row policy, binder,
   bootstrap, and dynamic-art comparison to a narrow forward-window contract;
4. introduce a closeable active owner that wraps the ordinary cursor or one
   game-owned special-stage payload;
5. migrate launcher, headless, visual, and complete-audio lifecycle ownership;
6. change boundary/bootstrap and arbitrary BK2 frame-view consumers to compact
   descriptor summaries; and
7. remove `SegmentPlan.trace()` and `SegmentPlan.specialStageRows()`, then assert
   no payload, event graph, or I/O owner is reachable from the run plan.

Do not combine this change with parser-format changes, compact-field encoding,
trace schema changes, or hardware-timing redesign. Those would obscure the
ownership proof and make the replay oracle ambiguous.

## Acceptance gate

Run the same 67-segment fixture at the same commit-derived baseline and require:

- the identical first mismatch (`camera_x`, expected `0x1300`, actual `0x1308`)
  and terminal segment-0 `giant_ring` boundary failure;
- all 1,653 AIZ physics rows consumed before the boundary failure;
- identical unmatched hardware completions and dynamic-art comparison outcome;
- a lower post-plan forced-GC live heap and lower peak live heap with the same
  or lower configured `-Xmx`;
- no missing or starved trace class in a fresh complete trace sweep; and
- identical visual-run and complete-audio harness frame/lag observations,
  including gap and handoff cursors; and
- no open file descriptor, gzip stream, or mapped-buffer growth across segment
  boundaries and failure exits.

Structural gates additionally require that an ordinary physics cursor retain no
more than previous/current/next rows, an ordinary auxiliary cursor retain no
more than current/next frame buckets plus the documented bounded summaries, and
the run have at most one active segment owner. The special-stage owner must be
reachable only while its segment is active. If it dominates the measured peak
enough to prevent the required replay-memory reduction, its profile must stream
before this design can be marked implemented.

Raw evidence is retained under
`$AGENT_SCRATCH_ROOT/tasks/performance-candidate-validation-20260821T161822Z-3319778-904a0080/trace-retention/`
and the phase-one benchmark and verification logs under
`$AGENT_SCRATCH_ROOT/tasks/trace-segment-descriptor-benchmark-20260821T192520Z-4127426-d936ca5a/`.
