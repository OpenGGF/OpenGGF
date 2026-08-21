# Which recorded trace fields are compared, and which are not

**Date:** 2026-08-21 · **Measured at:** `49c6e1438` · **Scope:** the 399 `aux_state.jsonl.gz`
fixtures under `src/test/resources/traces/`, plus the comparison paths in `TraceBinder`,
`AbstractTraceReplayTest` and `LiveTraceComparator`.

Point-in-time inventory, prompted by the CNZ slot-machine round: the recorder had been
capturing that subsystem's ROM state on every row of both fixtures since the fixtures were
made, nothing read it, and the resulting blind spot cost three rounds and two rejected
candidates to work around. The obvious question is how many others there are.

**Answer: 31 recorded event kinds, 9 compared, 22 not.** Eight of those 22 are unreferenced
anywhere outside the parser.

## Method, and what the table does not claim

Event kinds were enumerated by scanning every aux fixture. "Compared" means an exact
`TraceEvent.<Record>` reference appears in one of the three comparison files above. "Used"
means an exact reference appears anywhere else in `src/`. Both are token matches on the
record type, not a substring search — an earlier fuzzy pass produced several false positives
and is not the basis for anything here.

The table says nothing about whether an uncompared stream *should* be compared. Some are
plainly diagnostic (`object_appeared` drives the occupancy oracle; `lag_state` classifies
rows). The point is that nobody has asked the question per stream, and at least one answer
was worth three rounds.

## Compared today (9)

| event | games | sampled volume |
|---|---|---|
| `object_near` | s1, s2, s3k | 65,467 |
| `load_queue_state` | s1, s2, s3k | 19,813 |
| `dynamic_art_transfer_state` | s1, s2 | 18,310 |
| `cpu_state` | s2, s3k | 8,673 |
| `cnz_slot_machine_state` | s2 | 518 |
| `object_state_snapshot` | s2, s3k | 142 |
| `cpu_state_snapshot` | s2, s3k | 40 |
| `checkpoint` | s2, s3k | 21 |
| `player_history_snapshot` | s2 | 19 |

`cnz_slot_machine_state` joined this list on 2026-08-21 and is the reason for the audit.

## Recorded, parsed, never compared (22)

**Unreferenced anywhere outside the parser (8)** — captured, stored, and read by nothing:

| event | games | sampled volume | note |
|---|---|---|---|
| `object_state` | s3k | 12,216 | **the largest single item.** `TraceEvent`'s own comment calls it the event that dominates long complete-run aux files "by the hundreds of thousands", and describes the compact array-backed map built to store it economically. Nothing then reads it. |
| `interact_state` | s3k | 5,402 | |
| `game_paused_state` | s3k | 2,544 | |
| `slot_dump` | s1, s2, s3k | 616 | object-slot occupancy, in all three games |
| `s2_tornado_state` | s2 | 567 | subsystem ROM state, same shape as the CNZ slot block that turned out to be an oracle |
| `cursor_state` | s1, s2 | 317 | |
| `control_lock_state` | s3k | 215 | |
| `player_mode_set` | s3k | 21 | |

**Referenced for reporting, formatting or probes, but never compared (14):**
`air_countdown_state`, `camera_boundary`, `lag_state`, `mode_change`, `object_appeared`,
`object_removed`, `oscillation_state`, `routine_change`, `s1_obj64_state`,
`sidekick_interact_object`, `state_snapshot`, `v_objstate`, `v_oscillate`, `zone_act_state`.

These reach `DivergenceReport`, `TraceEventFormatter`, `ObjectOccupancyOracle` or a
`Debug*Probe` — visible to a human reading a report, invisible to the suite.

## What makes a stream an oracle rather than telemetry

The CNZ block qualified on a specific property, and it is the test to apply to the rest: **the
ROM derives the recorded values from state the engine also has, by arithmetic that can be
inverted.** `SlotMachine_Routine3` computes reel speeds from `V_int_run_count` by masking and
adding, so a recorded speed byte pins the exact counter the ROM used — comparison at an event
identified by its content, with no dependence on row alignment or on any model of frame
ordering. That is why it could settle a question two rounds of frame-ordering argument could
not.

By that test the most promising unexamined candidates are `s2_tornado_state` (subsystem ROM
state, directly analogous) and `object_state` (per-object SST fields at enormous volume, in
the game with the most unfinished object work). `slot_dump` is occupancy, which the existing
oracle already covers by another route.

## Suggested order, if anyone acts on this

1. `s2_tornado_state` — smallest, closest analogue to the case that motivated the audit.
2. `object_state` — largest payoff and largest cost; S3K object parity is the active frontier
   and this is per-object ROM state nothing checks.
3. The reporting-only 14 — cheap to re-classify, since they are already parsed and surfaced.

Wiring one in is not free: switching on the CNZ comparison immediately turned two green
release-scope classes red by exposing 767 pre-existing divergences. That is the expected
outcome of new coverage over untriaged state, and it is a reason to sequence the work, not to
skip it. See `docs/status/known-discrepancies.md` for how that one was landed and what its
promotion condition is.

**And before trusting any of it:** break each new comparison on purpose — corrupt an input,
confirm the field goes red — before believing a green. The CNZ comparison was first wired
into `LiveTraceComparator`, which the replay tests never execute, and reported a perfectly
clean green while never running.
