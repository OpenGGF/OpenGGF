# BizHawk Trace Recording

The canonical trace replay documentation now lives in:

- [`docs/guide/contributing/trace-replay.md`](../../docs/guide/contributing/trace-replay.md)

Use this folder for the recorder scripts and local BizHawk assets:

- `run_bizhawk_lua.bat` launches any Lua/BK2/ROM combination safely for
  diagnostics and one-off probes
- `record_trace.bat` launches S1 recording through the reusable no-audio/no-render launcher
- `s1_trace_recorder.lua` captures the ROM-side trace data using schema v3
- `record_s2_trace.bat` launches the Sonic 2 recorder through the reusable no-audio/no-render launcher
- `record_s2_level_select_traces.ps1` records the Sonic 2 level-select BK2 set into test resources
- `s2_trace_recorder.lua` captures Sonic 2 ROM-side trace data using schema v8, including
  first-sidekick state for Sonic/Tails parity debugging
- `record_s3k_trace.bat` launches the Sonic 3&K recorder through the reusable no-audio/no-render launcher
- `s3k_trace_recorder.lua` captures Sonic 3&K ROM-side trace data using schema v3, including
  `zone_act_state` diagnostics and the `aiz_end_to_end` checkpoint stream
- `s3k_complete_run_recorder.lua` records per-zone segments from any Sonic 3&K BK2 (see
  "Trace Run Manifests" section below)
- `record_s1_credits_traces.bat` launches forced Sonic 1 credits-demo capture
- `s1_credits_trace_recorder.lua` records the built-in ending replays without a BK2

## Trace Run Manifests

A **trace run** is a complete playthrough captured in typed per-zone or per-stage segment
directories (`aiz/`, `hcz/`, `mgz/`, etc.), each containing `physics.csv`, `aux_state.jsonl`,
and `metadata.json`. The `run_manifest.json` file (at the run's root) indexes all segments,
records game-mode transitions and stage-detour boundaries (special stages, bonus zones), and
marks the BK2 frame where each transition occurred. A manifest is emitted only when:
- The playthrough includes a stage detour (special stage finalization via `Game_Mode=$34` or
  bonus zone entry at zone id `0x13`–`0x15` under the level-family `Game_Mode`), or
- The `OGGF_TRACE_RUN_ID` environment variable is explicitly set.

The S3K complete-run recorder handles stage detours as follows:
- **Special Stages** (`Game_Mode=$34`): The level segment finalizes when `Game_Mode` changes.
  The `run_manifest.json` records a single merged transition boundary with the `giant_ring`
  mode change frame (the blue-spheres special stage). Per-frame CSV rows are only recorded for
  the level segment; blue-spheres row writer and segment directories land with future phases.
- **Bonus Zones** (zone id `0x13`–`0x15`, `Game_Mode` stays level-family): Enter a new `s3k_bonus_stage` segment on the same
  schema as level segments. The level segment also finalizes, and the manifest records both
  mode-change boundaries explicitly.
- **Mode Guard**: Per-frame row writes are gated on the current `Game_Mode` family (level vs.
  stage). Stage detours trigger a transition-boundary record but do not write rows until the
  mode changes back into a recordable category, avoiding pollution of level segments with
  out-of-scope stage data.
- **Repeat Segments**: If a route re-enters a zone, segment directories are named with a
  repeat index (e.g. `aiz_2`, `aiz_3`) to avoid collisions while preserving
  contiguous frame ranges within each segment.

The `OGGF_TRACE_RUN_ID` environment variable forces manifest emission and sets the `run_id`
field, allowing trace runs with no detours to be tracked explicitly (useful for complete-game
runs or for organizing capture sessions).

Schema v3 records the execution counters used by replay:

- `gameplay_frame_counter` changes only when the level main loop completed
- `vblank_counter` changes on every VBlank
- `lag_counter` is diagnostic where the ROM exposes it

For the S3K end-to-end AIZ fixture, run:

```bat
tools\bizhawk\record_s3k_trace.bat ^
  "s3k.gen" ^
  "src\test\resources\traces\s3k\aiz1_to_hcz_fullrun\s3k-aiz1-aiz2-sonictails.bk2" ^
  aiz_end_to_end
```

That profile starts at BK2 frame `0` instead of waiting for gameplay unlock, and `aux_state.jsonl`
will include deterministic same-frame ordering of `zone_act_state` followed by any semantic
checkpoint event for the fixture.

For the Sonic 2 level-select movies, run the generator instead of copying recorder output by hand:

```powershell
PowerShell -NoProfile -ExecutionPolicy Bypass -File tools\bizhawk\record_s2_level_select_traces.ps1 `
  -RomPath "s2.gen"
```

Use `-Only cpz` or another route slug for a single fixture. Long level-select BK2s can include
both act 1 and act 2; the generator exposes act-2 fixtures as separate slugs such as `cpz2` and
`cnz2` so each trace keeps a contiguous BK2 input offset across only one controllable gameplay
segment. The generator uses the `level_gated_reset_aware` recorder profile, validates that `zone_id`
is the engine progression id and `rom_zone_id` is the raw Sonic 2 ROM zone id, normalizes the
physics input column from the BK2 log, checks BK2 input alignment, and stores only compressed
`physics.csv.gz` and `aux_state.jsonl.gz` payloads under `src/test/resources/traces/s2`.
`dez_ending` remains parser/catalog-only until the ending route has replay coverage.
Metropolis Act 3 is recorded as route `mtz3`; Sonic 2 stores it as raw ROM zone id `0x05`
with act byte `0`, so the recorder reports metadata act `3` while preserving the raw
zone/act in aux diagnostics.

## Recording S3K Bonus Round-Trip Traces

A **bonus round-trip trace** captures a single level playthrough that includes a
star-post bonus zone (gumball or pachinko). The trace includes both the level
segment (up to star-post entry) and the bonus stage segment (from entry to
exit and return to the level). These are recorded as separate segment
directories in a single trace run.

**Human Recording Procedure (BizHawk 2.11 + Genplus-gx):**

1. Start a new movie from power-on with `s3k.gen`.
2. Play AIZ Act 1 through to a star post. Collect either:
   - **50–64 rings** for a gumball bonus (selector formula `((rings-20)/15)%3` yields remainder 2),
     referenced at ROM `sonic3k.asm:61891-61920` (GUMBALL assignment at 61917-61920), or
   - **35–49 rings** for a glowing-sphere/pachinko bonus (selector remainder 1).
   
   **Warning:** 20–34 rings selects the slot-machine bonus (remainder 0), which is a deferred feature
   — do not use for these recordings. Only gumball (50–64) and pachinko (35–49) are supported today.
3. Approach and enter the star circle at the star post.
4. Play through the bonus stage to its conclusion (collecting orbs, reaching exit).
5. Receive the ring bonus, return to the level, and play for 3–5 additional seconds (this guarantees
   the re-entry segment `aiz_2` in the trace output).
6. Stop the movie and save as either `s3k-aiz-gumball.bk2` or `s3k-aiz-pachinko.bk2`.

**Recorder Invocation:**

Run the complete-run recorder over your movie file:

```bat
set OGGF_TRACE_OUTPUT_DIR=C:\tmp\s3k_bonus_trace
set OGGF_TRACE_RUN_ID=s3k-aiz-gumball-roundtrip

tools\bizhawk\run_bizhawk_lua.bat ^
  tools\bizhawk\s3k_complete_run_recorder.lua ^
  s3k-aiz-gumball.bk2 ^
  s3k.gen
```

Replace `s3k-aiz-gumball-roundtrip` and the file paths for pachinko runs. The bonus round-trip
detour already triggers manifest emission (per plan (a)); the `OGGF_TRACE_RUN_ID` env var ensures
a stable `run_id` is recorded in the manifest, used for organizing the commit layout under
`src/test/resources/traces/s3k/runs/<run_id>/`. The manifest records all segment transitions,
including the star-post entry boundary and the bonus-exit return boundary.

**Expected Output:**

The output directory will contain:
- `run_manifest.json` — indexed transitions for level→bonus and bonus→level boundaries.
- `aiz/` — level segment (AIZ Act 1, frames 0 to star-post entry).
- `gumball/` or `pachinko/` — bonus segment with `trace_profile: "s3k_bonus_stage"` in
  `metadata.json`. Both segments contain `physics.csv` and `aux_state.jsonl` (plain format;
  gzip compression is applied at commit time).
- `aiz_2/` — AIZ re-entry segment following the bonus return. Step 5 above guarantees this
  segment will be present. Repeat segments are named with `_2`, `_3`, etc. to avoid
  directory collisions.

**Commit Layout:**

Place the bonus segment in test resources:

```
src/test/resources/traces/s3k/bonus_gumball/
  ├── metadata.json
  ├── physics.csv.gz
  ├── aux_state.jsonl.gz
  ├── s3k-aiz-gumball.bk2           # or under _movies/ with source_bk2 field
  └── ...
```

Also preserve the run directory and manifest (used by plan-(c) chain tests):

```
src/test/resources/traces/s3k/runs/s3k-aiz-gumball-roundtrip/
  ├── run_manifest.json
  ├── aiz/
  │   ├── metadata.json
  │   ├── physics.csv.gz
  │   └── aux_state.jsonl.gz
  └── gumball/
      ├── metadata.json
      ├── physics.csv.gz
      └── aux_state.jsonl.gz
```

The test classes `TestS3kGumballBonusTraceReplay` and `TestS3kPachinkoBonusTraceReplay`
automatically activate (skip-if-missing) once their respective `bonus_gumball/` and
`bonus_pachinko/` directories exist in test resources.

If you update the trace workflow, update the guide page above first so the contributor docs stay in
sync with the tools.

For trace recording, use the `record_*_trace.bat` wrappers. They route through
`run_bizhawk_lua.bat`, which means recorder regeneration gets the same generated
no-audio config, fast Lua wrapper, and invisible-emulation mode as one-off
diagnostics.

For one-off diagnostics, copy `diag_template_fast.lua`, set the capture window
environment variables, and run the reusable launcher instead of constructing a
PowerShell `Start-Process` argument array:

```bat
set OGGF_START=16300
set OGGF_STOP=16320
set OGGF_OUT=C:\tmp\htz2_diag.txt
tools\bizhawk\run_bizhawk_lua.bat ^
  tools\bizhawk\diag_s2_htz2_obj30.lua ^
  src\test\resources\traces\s2\htz2\s2-lvl-select-HTZ.bk2 ^
  s2.gen
```

The launcher resolves all three input paths to absolute paths, writes a per-launch
temporary no-audio/offscreen diagnostic config, passes `--audiosync false`, wraps
the Lua in a per-launch temporary script so the fast-headless calls run first and
are re-applied on frame start, verifies that the diagnostic itself contains
executable fast-headless template calls before its main loop, and invokes EmuHawk
with normal Windows quoting. On the default path it also runs EmuHawk through a
hidden process wrapper that hides every top-level window owned by the EmuHawk
process immediately and keeps hiding them if BizHawk re-shows UI after WinForms
startup. The Lua template mutes audio with
`client.SetSoundOn(false)` and disables rendering with
`client.invisibleemulation(true)`. Accidentally inherited
`BIZHAWK_ALLOW_SLOW_LUA=1` now fails unless `BIZHAWK_CONFIRM_VISIBLE_DEBUG=1` is
also set. This avoids BizHawk 2.11 failures such as
`Unrecognized command or argument '<path>\s2.gen'` and
`System.ArgumentException: The path is not of a legal form`.
