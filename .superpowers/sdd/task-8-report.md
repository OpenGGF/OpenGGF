# Task 8 Report: ADVANCE_ONLY live and visual-rewind semantics

Status: DONE

## Root causes

- `PlaybackDebugManager.getCurrentForcedInputMask()` replaced the pending
  action edge on every movie-row publication. Publishing the held row after an
  `ADVANCE_ONLY` press therefore erased the edge before gameplay could observe
  it.
- `TraceSessionLauncher.VisualTraceRewindStepper` did not handle
  `ADVANCE_ONLY` explicitly and fell through to a full `LevelFrameStep`,
  advancing gameplay and its counters.

## Changes

- Live playback now OR-latches newly pressed action bits and clears the pending
  edge only after an actual gameplay tick executes. Paused frames retain it.
- Visual rewind now publishes and latches input for `ADVANCE_ONLY` without a
  gameplay, animation, VBlank, lag, object, or oscillator tick. Structural
  no-gameplay rows preserve the edge; the next full gameplay row consumes it
  exactly once.
- Rewind restoration derives the pending edge from restored player state so a
  keyframed pending edge is not discarded.
- Added real S3K integration coverage for the forward live bridge and visual
  rewind stepper.

No trace hydration, fixture predicates, route/frame carve-outs, or comparison
outcome inference were added.

## RED / GREEN evidence

Forward live bridge RED:

```bash
mvn -Dtest=TestPlaybackAdvanceOnlyInputBridge \
  -Ds3k.rom.path=/home/farrell/code/projects/OpenGGF/s3k.gen test
```

Failed at `publishing the following held row must not erase the pending edge`
(expected `true`, actual `false`). The same command passes after the fix.

Visual rewind RED:

```bash
mvn -Dtest=TestTraceSessionLauncherAdvanceOnlyRewind \
  -Ds3k.rom.path=/home/farrell/code/projects/OpenGGF/s3k.gen test
```

Failed on the first no-gameplay invariant: sprite frame expected `0`, actual
`1`. The same command passes after the fix.

## Verification

- Focused bridge/rewind/driver/reference/invariant/bootstrap/must-keep command:
  exit 0.
- Architectural, rewind coverage, static-state rewind coverage, production
  singleton closure, replay invariant, and hydrate-default guards: exit 0.
- `TestLiveTraceComparatorObserver`: exit 0 independently. A mixed run after
  ROM fixture classes reveals pre-existing static sidekick-state test-order
  leakage, so it was verified in isolation.
- Focused AIZ: expected exit 1, 16 tests with 14 failures and 0 errors;
  `replayMatchesTrace` has 1,298 errors, 0 warnings, and its first mismatch
  remains f2707 `tails_animation_id` (`0x0000` / `0x0005`). There is no f717
  regression.

Maven Silent Extension can print stale historical Surefire report summaries
from other classes; command exit status and freshly selected reports were used
for the results above.
