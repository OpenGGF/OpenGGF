# BizHawk Trace Recording

The canonical trace replay documentation now lives in:

- [`docs/guide/contributing/trace-replay.md`](../../docs/guide/contributing/trace-replay.md)

Use this folder for the recorder scripts and local BizHawk assets:

- `record_trace.bat` launches headless recording
- `s1_trace_recorder.lua` captures the ROM-side trace data using schema v3
- `record_s2_trace.bat` launches the Sonic 2 headless recorder
- `record_s2_level_select_traces.ps1` records the Sonic 2 level-select BK2 set into test resources
- `s2_trace_recorder.lua` captures Sonic 2 ROM-side trace data using schema v8, including
  first-sidekick state for Sonic/Tails parity debugging
- `record_s3k_trace.bat` launches the Sonic 3&K headless recorder
- `s3k_trace_recorder.lua` captures Sonic 3&K ROM-side trace data using schema v3, including
  `zone_act_state` diagnostics and the `aiz_end_to_end` checkpoint stream
- `record_s1_credits_traces.bat` launches forced Sonic 1 credits-demo capture
- `s1_credits_trace_recorder.lua` records the built-in ending replays without a BK2

Schema v3 records the execution counters used by replay:

- `gameplay_frame_counter` changes only when the level main loop completed
- `vblank_counter` changes on every VBlank
- `lag_counter` is diagnostic where the ROM exposes it

For the S3K end-to-end AIZ fixture, run:

```bat
tools\bizhawk\record_s3k_trace.bat ^
  "Sonic and Knuckles & Sonic 3 (W) [!].gen" ^
  "src\test\resources\traces\s3k\aiz1_to_hcz_fullrun\s3k-aiz1-aiz2-sonictails.bk2" ^
  aiz_end_to_end
```

That profile starts at BK2 frame `0` instead of waiting for gameplay unlock, and `aux_state.jsonl`
will include deterministic same-frame ordering of `zone_act_state` followed by any semantic
checkpoint event for the fixture.

For the Sonic 2 level-select movies, run the generator instead of copying recorder output by hand:

```powershell
PowerShell -NoProfile -ExecutionPolicy Bypass -File tools\bizhawk\record_s2_level_select_traces.ps1 `
  -RomPath "Sonic The Hedgehog 2 (W) (REV01) [!].gen"
```

Use `-Only cpz` or another route slug for a single fixture. The generator uses the
`level_gated_reset_aware` recorder profile, validates that `zone_id` is the engine progression id
and `rom_zone_id` is the raw Sonic 2 ROM zone id, normalizes the physics input column from the BK2
log, checks BK2 input alignment, and stores only compressed `physics.csv.gz` and
`aux_state.jsonl.gz` payloads under `src/test/resources/traces/s2`.
`dez_ending` remains parser/catalog-only until the ending route has replay coverage.

If you update the trace workflow, update the guide page above first so the contributor docs stay in
sync with the tools.
