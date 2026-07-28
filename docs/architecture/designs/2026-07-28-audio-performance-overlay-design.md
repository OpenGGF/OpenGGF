# Audio Performance Overlay Metric Design

## Problem

The unified audio presentation work now runs at the outer-frame boundary in
`Engine.presentOuterAudioFrame()`. The existing `audio` profiler section in
`GameLoop` no longer surrounds that work; it only marks the audio step as
updated. Its measured duration is therefore effectively zero and falls below
the performance overlay's six-row limit.

## Design

Measure the complete outer-frame audio boundary as one aggregate `audio`
section. The measurement covers both:

- `GameLoop.presentOuterFrame()`, which performs synthesis and PCM
  presentation; and
- `AudioManager.update()`, which pumps the presentation sink and audio device.

Because this boundary executes inside the existing `update` profiler section,
it must not use nested `beginSection()` and `endSection()` calls. Instead,
`Engine` will measure elapsed nanoseconds and call
`PerformanceProfiler.recordSectionTime("audio", elapsedNanos)`. That API
credits the elapsed time to `audio` while removing the same interval from the
active `update` section, preventing double-counting.

The obsolete `GameLoop` audio timing calls will be removed. The
`audioUpdatedThisStep` lifecycle remains unchanged.

## Scope

This change restores one aggregate metric. It does not add audio submetrics,
change audio scheduling, change the overlay's six-row limit, or pin particular
metric names in the renderer.

## Testing

A focused regression test will execute the engine-owned outer-frame audio
boundary with observable work and assert that the profiler snapshot contains a
non-zero `audio` section. Existing engine, game-loop, profiler, and audio
presentation tests will then be run to detect scheduling or accounting
regressions.
