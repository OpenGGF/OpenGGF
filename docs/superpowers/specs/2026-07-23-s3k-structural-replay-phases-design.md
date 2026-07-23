# S3K Structural Replay Phase Detection

## Goal

Remove the `pre_level_intro_prefix`, `sidekick_seed_frame_prelude`, and
`pre_trace_osc_frames` metadata dependencies from S3K trace replay. Replay must
derive native execution phases from recorded structural evidence without copying
recorded state into the engine.

## Design

`TraceReplayBootstrap` will classify trace starts from the trace timeline:

- A pre-level prefix exists when the trace begins outside live level gameplay
  and later reaches its first gameplay row. Existing zone/act state and
  checkpoint events provide that transition; no route or zone name is used.
- A sidekick-only setup row exists when the first captured row shows the native
  setup boundary: the primary player has not received a gameplay motion tick,
  while the recorded sidekick has received its setup/object tick. Classification
  uses the generic first-row character/counter transition, not a fixture flag.
- Oscillator pre-advance is computed from the first natively driven gameplay
  counter. It is never read from `pre_trace_osc_frames` for S3K replay.

These classifications may select `VBLANK_ONLY`, sidekick-prelude, or full-frame
engine operations. They must not set position, velocity, animation, object,
sidekick CPU, oscillator, or other runtime values from CSV or aux data.

## Compatibility

Legacy metadata fields remain parseable so old fixtures and tooling do not fail
to load, but S3K replay policy will ignore them. Recorder metadata generation
will stop emitting the phase-control extras. Other games retain their existing
bootstrap rules.

## Testing

Focused policy tests will first prove that:

1. AIZ is classified as a pre-level-prefix replay after removing its metadata
   marker.
2. CNZ receives the native sidekick-only setup prelude after removing its
   metadata marker.
3. Conflicting legacy marker values cannot change those classifications.
4. S3K oscillator pre-advance comes from structural counters.

Then the AIZ and CNZ trace replay tests will verify input alignment and frontier
behavior. Existing trace bootstrap contract tests and the S3K must-keep-green
tests will guard unrelated replay and engine behavior.
