# S3K Structural Replay Phase Detection

## Goal

Remove the `pre_level_intro_prefix`, `sidekick_seed_frame_prelude`, and
`pre_trace_osc_frames` metadata dependencies from S3K trace replay. Replay must
derive native execution phases from recorder-emitted execution provenance
without copying recorded outcome state into the engine.

## Design

The recorder will emit generic `execution_phase` aux events at capture
boundaries. Each event reports which native scheduling phase completed, not the
values produced by that phase:

- `pre_level_vblank`: capture is outside live level execution.
- `level_setup_boundary`: synchronous level setup completed, but the first
  `LevelLoop` has not.
- `setup_object_pass`: the native pre-`LevelLoop` `Process_Sprites` pass
  completed.
- `setup_oscillator_pass`: a native setup oscillator pass completed.
- `level_loop`: a complete native `LevelLoop` iteration completed.

The recorder determines these events from the ROM execution path/capture gate,
not by inspecting resulting player velocity, sidekick velocity, animation, or
oscillator bytes. No always-on diagnostic PC hooks are added: the recorder
already owns the capture profiles and emits the applicable start-boundary event
when each profile arms. The vocabulary is shared across profiles and contains no
zone, route, or fixture names.

`TraceReplayBootstrap` consumes this provenance to select `VBLANK_ONLY`,
sidekick-prelude, oscillator-prelude, or full-frame native engine operations.
It must not set position, velocity, animation, object, sidekick CPU, oscillator,
or other runtime values from CSV or aux data.

For an intro-prefix capture, the boundaries are distinct:

1. Intro begins at the first `pre_level_vblank` row.
2. The first LEVEL-mode setup row carries `level_setup_boundary` and remains
   `VBLANK_ONLY`.
3. The next `level_loop` row is the first driven gameplay iteration.
4. Strict comparison begins at the existing `gameplay_start` checkpoint.
5. Prefix classification and previous-input driving remain active through the
   `gameplay_start` checkpoint.

## Compatibility

Legacy metadata fields remain parseable so old fixtures and tooling do not fail
to load, but regenerated S3K replay policy will prefer execution provenance and
ignore conflicting legacy values. Recorder metadata generation will stop
emitting the phase-control extras. Existing source guards and
`KNOWN_DISCREPANCIES.md` will migrate to require execution provenance while
retaining the comparison-only/no-hydration ratchets. Other games retain their
existing bootstrap rules.

## Testing

Focused policy tests will first prove that:

1. AIZ is classified as a pre-level-prefix replay from provenance after removing
   its metadata marker.
2. CNZ receives the native setup object/sidekick and oscillator preludes from
   provenance after removing its metadata marker and oscillator count.
3. Conflicting legacy marker values cannot change those classifications.
4. AIZ complete-run, CNZ complete-run, MGZ counter-zero, bonus-stage,
   single-character, and visible-hold starts do not acquire either prelude unless
   their recorder provenance says that phase completed.
5. Guards reject inference from frame-zero player/sidekick motion or oscillator
   outcome values.

The AIZ and CNZ fixtures will then be regenerated with provenance events before
their trace replay tests verify input alignment and frontier behavior. Existing
trace bootstrap contract tests, build-tooling guards, negative start-shape
fixtures, and the S3K must-keep-green tests will guard unrelated replay and
engine behavior.
