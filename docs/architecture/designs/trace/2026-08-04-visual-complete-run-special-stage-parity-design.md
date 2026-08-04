# Visual complete-run special-stage parity design

## Problem and observed boundaries

The complete-run visual path has three different owners at the GHZ1 → S1
special-stage → GHZ2 seam:

* `SpecialStageEntryPresentationController` owns the visible fade/reveal.
* `TraceRunSpecialStageRowDriver` owns special-stage row pacing and dynamic-art
  publication, but the run launcher leaves the normal trace HUD and playback
  observer attached to the previous level.
* `PlaybackDebugManager` owns level input/comparison callbacks, but destination
  level admission installs a new comparator without replacing the playback
  frame observer.

The S1 provider also reports its entry presentation ready unconditionally,
even when `TRACE_ACCURATE` deliberately leaves the ROM startup hold observable.
That makes the visual reveal occur before the recorded startup rows have been
consumed. Finally, live capture shortcut modifier checks read the logical
trace-input override, so a physical capture chord can be hidden by recorded
input.

## Design

1. **Use the provider's native startup readiness.** S1's provider will report
   `Sonic1SpecialStageManager.isEntryPresentationReady()`. FAST startup already
   consumes the hold during initialization, while TRACE_ACCURATE keeps it
   opaque until the production loop reaches the ROM reveal boundary. No trace
   row will be used to set provider state.

2. **Make special-stage ownership explicit at admission.** When a run admits a
   special-stage segment, install a special-stage HUD model for that segment.
   It will use the same layout, labels, input glyphs, completion banner, and
   recent comparison summary as the normal trace HUD, with row-driver dynamic
   art comparisons contributing errors/warnings. The HUD's input supplier will
   read the segment's physical BK2 row, not the last level comparator.
   When the segment closes, destination level admission replaces this model
   with the normal HUD for the new comparator.

3. **Rebind the playback observer with every shared-clock destination.**
   The run's `BoundaryProbe` remains the sole
   `PlaybackDebugManager` frame observer: it forwards input/lag/comparison
   callbacks to its current delegate and also captures transient
   giant-ring/bonus requests that are visible only inside that callback.
   Destination admission will replace the probe's delegate with the new
   comparator before the destination's first production row. This restores
   input-offset calculation and live desync pause/reporting for GHZ2 and every
   later level without losing later transient boundary latches.

4. **Keep global capture shortcuts physical.** Add a raw physical modifier
   query to `InputHandler` and use it only for the live-capture chord. Logical
   trace overrides continue to drive gameplay, while the user's physical
   capture key is not hidden by a recorded modifier state during ordinary live
   capture. Existing trace/test-mode recording ownership guards remain in
   force; this change does not bypass them. Existing chord semantics (rising
   edge and exact modifiers) remain unchanged.

## Error and transition handling

The existing run coordinator remains the single transition authority. A HUD
switch is performed only by destination admission; if admission fails, the
existing failure path returns to the trace picker and records the diagnostic.
The S1 readiness change preserves normal interactive startup because FAST mode
finishes the same manager-owned hold before `begin()` is called.

## Verification

Add focused tests for:

* S1 TRACE_ACCURATE readiness staying opaque until manager startup completes,
  while FAST remains immediately ready; an integration step asserts the
  44-tick hold, provider reveal/music/fade ordering, and row cursor alignment.
* run destination comparator installation replacing the boundary probe's
  delegate while the probe remains installed, including a later transient
  boundary latch;
* special-stage HUD progress/input/completion state and dynamic-art mismatch
  counts;
* capture chord matching physical modifiers while a logical trace override is
  active, without bypassing trace/test-mode ownership guards.

Run the existing complete-run visual launcher tests, S1 special-stage replay
tests, run coordinator tests, capture tests, and the full `*TraceReplay` sweep.
Record any frontier movement in `docs/status/trace-frontier-log.md`.
