# Bounded trace-run segment ownership

## Status

Phase-one descriptor planning and descriptor-backed catalog validation were
implemented and measured on 2026-08-21. Actual replay still uses the eager
`SegmentPlan` path. The active-segment cursor migration remains future work and
requires separate approval.

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
- the opening-row summary needed by bootstrap and return-boundary comparison;
- terminal dynamic-art descriptors and cross-segment ledger summaries;
- the complete hardware-timing schedule and its compact raw-frame mapping;
- a compact per-row lag mapping keyed by segment-local raw frame; and
- any scalar level-loop or special-stage policy values currently read from the
  retained `TraceData`.

Replay opens one segment cursor at entry and closes it at every successful,
failed, or aborted boundary. The cursor provides:

- a physics window containing previous, current, and one-row lookahead;
- all typed auxiliary events for the current raw frame, materialised only for
  that row;
- the current dynamic-art row state; and
- special-stage rows through the same segment-local lifetime.

This is option C from the performance validation plan. Option A still retains
a complete eager `TraceData` for the largest active segment and leaves the
parser/container amplification intact. Option B keeps physics globally and
introduces an auxiliary random-access index that no measured consumer needs.

## Consumer contract

The existing consumers divide into these ownership groups:

| Owner | Required data | Lifetime |
|---|---|---|
| Run plan, manifest, hardware timing, playback coordinator | Metadata, topology, policy scalars, timing schedule, opening and terminal summaries | Whole run |
| Live comparator, row policy, presentation bridge, special-stage driver, active dynamic-art comparison | Previous/current/next physics row and current raw-frame auxiliary events | Active segment only |
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

## Accuracy constraints

- Trace payloads remain comparison-only. This design does not expand either
  hardware-timing exception.
- Row order, raw-frame identity, previous/current/lookahead semantics, and all
  typed auxiliary lookup results remain identical.
- Planning performs the same schema, manifest, phase, hardware-timing, and
  dynamic-art validation before replay begins.
- A segment boundary cannot discard its terminal comparison ledger before the
  destination opening comparison has consumed it.
- Cursor cleanup must be deterministic on normal completion, assertion
  failure, constructor failure, and launcher abort.

## Implementation boundary

The implementation sequence introduces a payload-independent segment
descriptor and then, under separate approval, a closeable segment cursor:

1. **Complete:** extract planning summaries and move catalog validation while
   preserving the current eager replay load as the reference path;
2. move `LiveTraceComparator` and row policy to the cursor window;
3. move current-frame auxiliary and dynamic-art lookups to the cursor;
4. change boundary/bootstrap consumers to immutable opening summaries; and
5. stop retaining `SegmentPlan.trace()` and assert that no payload owner remains
   reachable from the run plan.

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

Raw evidence is retained under
`$AGENT_SCRATCH_ROOT/tasks/performance-candidate-validation-20260821T161822Z-3319778-904a0080/trace-retention/`
and the phase-one benchmark and verification logs under
`$AGENT_SCRATCH_ROOT/tasks/trace-segment-descriptor-benchmark-20260821T192520Z-4127426-d936ca5a/`.
