# BizHawk Trace Recording

The canonical trace replay documentation now lives in:

- [`docs/guide/contributing/trace-replay.md`](../../docs/guide/contributing/trace-replay.md)

Use this folder for the recorder scripts and local BizHawk assets:

- `fetch_bizhawk_2_11_linux.sh` downloads and verifies the supported Linux build
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
- `lib/oggf_trace_common.lua` is a shared module of game-agnostic leaf helpers
  (`bk2_input_mask`, `hex`, `angle_to_ground_mode`, `read_speed`,
  `rom_joypad_to_mask`, `write_aux`, `json_escape`, `json_quote`, and the
  `INPUT_*` bitmask constants) that every recorder `loadfile`s at startup via a
  small `oggf_lib_dir()` loader. It holds only pure helpers whose emitted bytes
  are identical to the previously-inlined copies — schema writers, `*_csv_version`
  constants, and the fast-headless toggle block deliberately stay inline per
  recorder. `run_bizhawk_lua.bat` exports `OGGF_BIZHAWK_LIB` so the loader finds
  it on the wrapper/headless route; a `debug.getinfo` fallback covers direct
  `--lua=` launches. Any edit here must be regen-validated for a byte-identical
  `physics.csv` / `aux_state.jsonl` / `metadata.json` before committing.

## Required BizHawk version

Linux trace recording is pinned to **BizHawk 2.11 Linux x64**. Install the
official release from any working directory with:

```bash
tools/bizhawk/fetch_bizhawk_2_11_linux.sh
```

The script verifies the release checksum and installs it locally at
`docs/BizHawk-2.11-linux-x64`. Local BizHawk installations follow the
`docs/BizHawk-<version>-<platform>-<architecture>` naming convention and are
ignored by Git.

Do not substitute BizHawk 2.11.1 for trace recording. BizHawk 2.11.1 removed
`client.invisibleemulation`, which these recorders require for fast no-render
capture. An existing 2.11.1 installation may remain locally, but it must not be
selected when running the trace tools.

## Native headless GPGX harness (S1 + S2 trace recorders on Linux)

The Linux-only native GPGX harness (`tools/bizhawk-headless/`) runs the
BizHawk 2.11 core through Mono without starting EmuHawk and without requiring
`DISPLAY`. It records **full canonical Sonic 1 and Sonic 2 traces** and is the
supported replacement for `s1_trace_recorder.lua` and `s2_trace_recorder.lua`
when recording on Linux. `--mode trace` auto-detects the game from the
supplied ROM (S1 World REV01 or S2 World REV01) and selects the matching
recorder pipeline.

### Sonic 1 trace mode

For S1 the harness records `physics.csv` (CSV v7), `aux_state.jsonl`, and
`metadata.json` (`trace_schema` 4). Verified trace-mode command (the BK2
frame offset is auto-detected from gameplay start, so there is no
`--bk2-frame-offset` in trace mode):

```bash
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S1_ROM_PATH" \
  --movie "$PWD/src/test/resources/traces/s1/ghz1_fullrun/ghz1_fullrun.bk2" \
  --output "$PWD/target/bizhawk-headless-trace"
```

**Byte-parity guarantee vs the Lua recorder:** on the canonical GHZ1 fixture
(`src/test/resources/traces/s1/ghz1_fullrun/`) the native harness reproduces
`physics.csv` and `aux_state.jsonl` byte-identically (sha256
`dd0a03bfddefa9570d4b49ee2d4ea5e35e2b8141147e17ab482a3654d311cb66` and
`026794b175c7fea65491f57cbf5a83684f183b802c7fabaa15eb699e82184a86`) with the
same auto-detected
`bk2_frame_offset` 840 and `trace_frame_count` 3905; `metadata.json` differs
only in the `recording_date` value. The ROM-backed differential gate
(`tools/bizhawk-headless/test.sh`, test
`S1TraceDifferential native capture matches canonical GHZ1 trace`) re-verifies
this end to end.

**Intentional differences from the Lua recorder** (no output-byte impact):
`metadata.json` is written once at capture end instead of Lua's periodic
crash-resilience rewrites; stdout progress/diagnostic text is different; and
`lua_script_version` stays `"3.5"` as a schema-compatibility marker even
though no Lua runs. The byte-level porting contract lives in
`tools/bizhawk-headless/docs/s1-trace-recorder-behavior.md`.

### Sonic 2 trace mode (all three recorder modes)

With an S2 World REV01 ROM, `--mode trace` replaces `s2_trace_recorder.lua`
(v9.12-s2) on Linux across **all three of its operating modes**. The Lua
recorder's environment inputs become CLI flags:

- **Plain `gameplay_unlock`** — the default; no extra flags.
- **`level_gated_reset_aware` + segment selection** — pass
  `--trace-profile level_gated_reset_aware` and `--gameplay-segment <N>`
  (mirroring `OGGF_S2_TRACE_PROFILE` / `OGGF_TRACE_GAMEPLAY_SEGMENT`) to
  record the Nth controllable gameplay segment of a level-select BK2.
- **Run mode** — pass `--run-id <id>` (mirroring `OGGF_TRACE_RUN_ID`) for the
  multi-stage run recorder: the special-stage detour state machine
  (level → halfpipe → level), the minimal special-stage segment writer,
  per-segment `seg<N>_<zone><act>` / `ss` output subdirectories, and
  `run_manifest.json` at the run root. `--run-id` is mutually exclusive with
  `--trace-profile` / `--gameplay-segment` because the Lua run capture
  procedure always records `gameplay_unlock` level segments with no segment
  skipping. Run-mode output is staged fully in memory and published as one
  all-or-nothing no-replace set (the manifest is linked last, so it can never
  exist without its segment files).

Verified plain-mode command (as with S1, the BK2 frame offset is
auto-detected):

```bash
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S2_ROM_PATH" \
  --movie "$PWD/src/test/resources/traces/s2/ehz1_fullrun/s2-ehz1.bk2" \
  --output "$PWD/target/bizhawk-headless-trace"
```

**Byte-parity guarantee vs the Lua recorder:** four canonical fixtures gate
the S2 port end to end via the ROM-backed `S2TraceDifferential` tests in
`tools/bizhawk-headless/test.sh`:

- `src/test/resources/traces/s2/ehz1_fullrun/` — plain `gameplay_unlock`;
- `src/test/resources/traces/s2/arz/` — `level_gated_reset_aware`, segment 0;
- `src/test/resources/traces/s2/arz2/` — `level_gated_reset_aware`, segment 1;
- `src/test/resources/traces/s2/runs/s2-ehz-halfpipe-roundtrip/` — run mode
  over the full level → halfpipe → level → halfpipe → level round trip.

`physics.csv` and `aux_state.jsonl` are byte-identical on every fixture, and
run mode additionally reproduces `run_manifest.json` and every per-segment
file byte-identically with **no normalization**. `metadata.json` differs only
in the `recording_date` value, plus — on the older `ehz1_fullrun`/`arz`/`arz2`
fixtures stamped `9.11-s2` — the documented `lua_script_version`
`9.11-s2` → `9.12-s2` delta (the native port emits the v9.12-s2 surface,
whose plain-mode output is declared byte-identical to v9.11-s2 except that
version string). The byte-level porting contracts live in
`tools/bizhawk-headless/docs/s2-trace-recorder-behavior.md` (plain +
segment-selection modes) and
`tools/bizhawk-headless/docs/s2-run-mode-behavior.md` (run mode); where any
spec text and the Lua disagree, the Lua wins.

### Limitations and smoke mode

**Limitations:** Linux/Mono only, and single-game S1/S2 recorders only — the
S3K recorders and the S1/S3K complete-run recorders remain Lua scripts, and
`s1_trace_recorder.lua` / `s2_trace_recorder.lua` remain the reference
implementations and the recording path on non-Linux platforms. The harness
needs the BizHawk 2.11 Linux x64 assemblies (`BIZHAWK_HOME` must point at an
absolute install, default `docs/BizHawk-2.11-linux-x64`), Mono, and a
verified Sonic 1 or Sonic 2 World REV01 ROM (`S1_ROM_PATH` / `S2_ROM_PATH`
for the test suite's differential gates).

The original smoke-capture proof-of-concept mode is still available
(`--mode smoke`, the default) with explicit `--bk2-frame-offset` /
`--max-frames`; its `smoke.csv` is a deterministic developer diagnostic only —
not the canonical trace schema — and must not be committed as a trace fixture.

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

## Linux Launcher (`run_bizhawk_lua.sh` / `record_trace.sh`)

`run_bizhawk_lua.sh` is the Linux counterpart to `run_bizhawk_lua.bat`, and
`record_trace.sh` mirrors `record_trace.bat`. They launch EmuHawk via `mono` and
export the same `OGGF_BIZHAWK_LIB` contract for the shared-lib loader. Bring-up
facts encoded there (BizHawk 2.11.1 `bizhawk-bin` on CachyOS/Wayland):

- BizHawk runs portable and writes config/system dirs beside `EmuHawk.exe`; the
  packaged `/opt/bizhawk` is root-owned, so run against a writable copy
  (`cp -a /opt/bizhawk ~/.local/share/bizhawk-run`) via `BIZHAWK_HOME`.
- `DISPLAY` must be set (EmuHawk is WinForms even headless); XWayland `:0` works.
- Hardware GL under XWayland fails (`eglMakeCurrent … EGL_BAD_ACCESS`); the
  launcher forces Mesa software GL by default (or set config `DispMethod=1` for
  GDI+). `--luaconsole` is passed to dodge a `Stack empty` crash that
  command-line `--lua` + `--movie` otherwise throws in `LuaConsole.EnableLuaFile`.
- **KNOWN BLOCKER (upstream BizHawk 2.11.1 + mono, not the launcher):** loading a
  BK2 via `--movie` hangs inside the movie-load path right after `WaterboxHost
  Sealed`, before the form's `OnShown` — so the recorder's Lua never runs and no
  `trace_output/` is produced. The hung process maps no X window (not a
  dismissible dialog). The same recorder Lua launched *without* `--movie` runs
  fine (Lua loads, frames advance, clean exit), isolating the fault to
  command-line BK2 loading on this build. An end-to-end Linux regen needs a
  working headless BK2 path (a different BizHawk build, a real X server via Xvfb,
  or a Lua-side movie loader). Until then, run the byte-diff regen gate on a
  platform where BizHawk plays BK2s headlessly (e.g. Windows).

## Capture Launch Notes (verified live 2026-07-19)

Facts established during the first round-trip captures — they override any older
invocation text in this file:

- **Output location:** BizHawk's Lua working directory is the LUA SCRIPT'S OWN
  DIRECTORY, so every recorder writes to `<dir containing the .lua>/trace_output/`
  (e.g. `tools/bizhawk/trace_output/` of whichever checkout's script you launched).
  No recorder reads an `OGGF_TRACE_OUTPUT_DIR` variable.
- **Direct launch works and is the simplest route:**
  `docs\BizHawk-2.11-win-x64\EmuHawk.exe --chromeless --lua=<script> --movie=<bk2> <rom>`
  (note the `=` forms). `run_bizhawk_lua.bat` also works now that all three recorders
  carry the guard-satisfying `pcall(client.SetSoundOn, false)` snippet.
- **Lua errors are INVISIBLE in `--chromeless` mode** — a script that errors at load
  produces no console output and no files, and EmuHawk either exits quickly or idles.
  If a capture produces nothing, re-run WITHOUT `--chromeless` and watch for the error
  dialog before suspecting anything else.
- **Console `print()` output never reaches stdout** in either mode; judge success by
  the output files, and validate with the VERIFY-ON-FIRST-CAPTURE checks directly
  against the CSVs.

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

## Recording S3K Slot-Machine Round-Trip Traces

A **slot-machine round-trip trace** captures a single level playthrough that includes a
star-post bonus zone (slot machine). The trace includes both the level segment (up to
star-post entry) and the slot-machine bonus segment (from entry to exit and return to the
level). These are recorded as separate segment directories in a single trace run.

**Human Recording Procedure (BizHawk 2.11 + Genplus-gx):**

1. Start a new movie from power-on with `s3k.gen`.
2. Play AIZ Act 1 through to a star post. Collect exactly:
   - **20–34 rings** at the star post (selector formula `((rings-20)/15)%3` yields remainder 0),
     referenced at ROM `sonic3k.asm:61891-61920` (SLOT_MACHINE assignment at 61897).
   
3. Approach and enter the star circle at the star post.
4. Play through the slot-machine bonus stage to its conclusion (inserting tokens, collecting
   rings, reaching exit or losing all tokens).
5. Receive the ring bonus, return to the level, and play for 3–5 additional seconds (this
   guarantees the re-entry segment `aiz_2` in the trace output).
6. Stop the movie and save as `s3k-aiz-slots.bk2`.

**Recording Notes:** The slot-machine bonus suppresses sidekicks at runtime (only Sonic plays),
and the sidekick comparator columns bypass the sprite seam for slot sessions. Record **SONIC-SOLO**
exclusively — a team recording (Sonic + Tails) adds pure noise to the comparator output.

**Recorder Invocation:**

Run the complete-run recorder over your movie file:

```bat
set OGGF_TRACE_RUN_ID=s3k-aiz-slots-roundtrip

tools\bizhawk\run_bizhawk_lua.bat ^
  tools\bizhawk\s3k_complete_run_recorder.lua ^
  s3k-aiz-slots.bk2 ^
  s3k.gen
```

The bonus round-trip detour already triggers manifest emission (per plan (a)); the
`OGGF_TRACE_RUN_ID` env var ensures a stable `run_id` is recorded in the manifest, used for
organizing the commit layout under `src/test/resources/traces/s3k/runs/<run_id>/`. The manifest
records all segment transitions, including the star-post entry boundary and the bonus-exit
return boundary.

**Expected Output:**

The output directory will contain:
- `run_manifest.json` — indexed transitions for level→bonus and bonus→level boundaries.
- `aiz/` — level segment (AIZ Act 1, frames 0 to star-post entry).
- `slots/` — bonus segment with `trace_profile: "s3k_bonus_stage"` and `bonus_stage_type: "slots"`
  in `metadata.json`. Both segments contain `physics.csv` and `aux_state.jsonl` (plain format;
  gzip compression is applied at commit time).
- `aiz_2/` — AIZ re-entry segment following the bonus return. Step 5 above guarantees this
  segment will be present. Repeat segments are named with `_2`, `_3`, etc. to avoid
  directory collisions.

**Commit Layout:**

Place the bonus segment in test resources:

```
src/test/resources/traces/s3k/bonus_slots/
  ├── metadata.json
  ├── physics.csv.gz
  ├── aux_state.jsonl.gz
  ├── s3k-aiz-slots.bk2           # or under _movies/ with source_bk2 field
  └── ...
```

Also preserve the run directory and manifest (used by plan-(c) chain tests):

```
src/test/resources/traces/s3k/runs/s3k-aiz-slots-roundtrip/
  ├── run_manifest.json
  ├── aiz/
  │   ├── metadata.json
  │   ├── physics.csv.gz
  │   └── aux_state.jsonl.gz
  └── slots/
      ├── metadata.json
      ├── physics.csv.gz
      └── aux_state.jsonl.gz
```

The test class `TestS3kSlotsBonusTraceReplay` automatically activates (skip-if-missing) once
the `bonus_slots/` directory exists in test resources.

## Recording S3K Blue-Spheres Round-Trip Traces

A **blue-spheres round-trip trace** captures a single playthrough that includes entry into
a special stage (blue-spheres, accessed via giant ring) mid-level, completion or failure of
the stage, and return to the level. The trace includes both the level segment and a dedicated
`special_stage` segment with the 20-column S3K special-stage schema.

**Human Recording Procedure (BizHawk 2.11 + Genplus-gx):**

1. Start a new movie from power-on with `s3k.gen`.
2. Play AIZ Act 1 through to the giant ring entrance (typically at the midpoint).
3. Enter the giant ring to trigger the blue-spheres special stage.
4. Play through the blue-spheres stage to its conclusion (collecting spheres, reaching exit,
   or failing to collect enough). The stage ends when fade timer completes the exit animation.
5. Return to the level after blue-spheres completion and play for 3–5 additional seconds
   (this guarantees the re-entry segment `aiz_2` in the trace output).
6. Stop the movie and save as `s3k-aiz-bluespheres.bk2`.

**Recorder Invocation:**

Run the complete-run recorder over your movie file:

```bat
set OGGF_TRACE_RUN_ID=s3k-aiz-bluespheres-roundtrip

tools\bizhawk\run_bizhawk_lua.bat ^
  tools\bizhawk\s3k_complete_run_recorder.lua ^
  s3k-aiz-bluespheres.bk2 ^
  s3k.gen
```

The special-stage detour already triggers manifest emission. The `OGGF_TRACE_RUN_ID` env var
ensures a stable `run_id` for organizing trace history.

**Expected Output:**

The output directory will contain:
- `run_manifest.json` — indexed transitions for level→special-stage and special-stage→level boundaries.
- `aiz/` — level segment (AIZ Act 1, frames 0 to giant-ring entry).
- `ss/` — special-stage segment with `trace_profile: "s3k_special_stage"`, `ss_csv_version: 1`,
  and `special_stage_index` in `metadata.json`. Both segments contain `physics.csv` and
  `aux_state.jsonl` (plain format; gzip compression is applied at commit time).
  The special-stage segment metadata also includes `"fresh_load": false` (giant-ring entry
  is mid-level, never a fresh stage boot).
- `aiz_2/` — AIZ re-entry segment following the special-stage return. Step 5 above guarantees
  this segment will be present.

**VERIFY-ON-FIRST-CAPTURE Self-Check:**

At SS-segment open and every 300 frames during the stage, the recorder prints diagnostics
to stdout:

```
SS segment armed at BizHawk frame N (dir=ss, special_stage_index=0).
SS frame 0: spheres_left=8 ring_count=0 started=1 x_pos=0x0080 y_pos=0x0080
SS frame 300: spheres_left=7 ring_count=5 started=1 x_pos=0x0120 y_pos=0x0150
```

These prints verify the RAM map (documented in the plan) against the first real capture.
Eyeball the progression to confirm spheres_left decreases toward 0, started stays 1 after
the first frame, and x_pos/y_pos remain within 0xFFFF. These diagnostics are mandatory
for validating that the schema addresses are correctly mapped to the ROM's special-stage
state block.

## Recording S1 Maze Round-Trip Traces (s1-ghz-maze-roundtrip)

An **S1 maze round-trip trace** captures a single GHZ playthrough that includes entry into
the special stage (maze, accessed via the giant ring past the signpost) mid-act, completion
or failure of the maze, and continuation into the next act. The trace includes the GHZ1 level
segment, a dedicated `special_stage` segment with the S1 maze schema, and the GHZ2 level
segment.

**Human Recording Procedure (BizHawk 2.11 + Genplus-gx):**

1. Start a new movie from power-on with the S1 World REV01 ROM, on a **fresh save/no-emeralds
   state**. Do not resume from a save that already collected an emerald — after a first
   emerald is collected, `v_lastspecial`'s pre-`SS_Load` value can name a stage the ROM's skip
   loop rejects, mislabeling the segment.
2. Play GHZ1, collecting at least 50 rings.
3. Touch the giant ring past the signpost to trigger the maze special stage.
4. Play through the maze to its conclusion — complete it, or fail out of it. Either outcome is
   acceptable for this recording.
5. Continue into GHZ2 and keep playing until control is settled (a few seconds of normal
   act-2 gameplay after the transition).
6. Stop the movie and save under a descriptive name (e.g. `s1-ghz-maze-roundtrip.bk2`; see
   the truthful-name commit rule below).

**Recorder Invocation** (verified 2026-07-19 — the direct launch used for the committed
capture; see "Capture Launch Notes" above for where the output lands):

```bat
set OGGF_TRACE_RUN_ID=s1-ghz-maze-roundtrip

docs\BizHawk-2.11-win-x64\EmuHawk.exe --chromeless ^
  --lua=tools/bizhawk/s1_complete_run_recorder.lua ^
  --movie=docs/BizHawk-2.11-win-x64/Movies/s1-ghz-maze-roundtrip.bk2 s1.gen
```

The `$10` (special-stage) detour is handled automatically by the recorder's state machine — no
extra flags are needed. The `OGGF_TRACE_RUN_ID` env var ensures a stable `run_id` is recorded in
the manifest, used for organizing the commit layout under `src/test/resources/traces/s1/runs/<run_id>/`.

**Expected Output:**

The output directory will contain:
- `run_manifest.json` — indexed transitions for the GHZ1→maze and maze→GHZ2 boundaries.
- `ghz1/` — level segment (GHZ Act 1, frames 0 to giant-ring entry).
- `ss/` — special-stage segment with the S1 maze schema. Both segments contain `physics.csv`
  and `aux_state.jsonl` (plain format; gzip compression is applied at commit time).
- `ghz2/` — GHZ Act 2 segment following the maze exit. Step 5 above guarantees this segment
  will be present.

**Commit Layout:**

Commit the whole run under test resources:

```
src/test/resources/traces/s1/runs/s1-ghz-maze-roundtrip/
  ├── run_manifest.json
  ├── ghz1/
  │   ├── metadata.json
  │   ├── physics.csv.gz
  │   └── aux_state.jsonl.gz
  ├── ss/
  │   ├── metadata.json
  │   ├── physics.csv.gz
  │   └── aux_state.jsonl.gz
  └── ghz2/
      ├── metadata.json
      ├── physics.csv.gz
      └── aux_state.jsonl.gz
```

Then copy the `ss/` segment (its `metadata.json`, `physics.csv`, and the source bk2) to
`src/test/resources/traces/s1/special_stage/` to activate `TestS1SpecialStageTraceReplay`.

**Commit the bk2 under its TRUTHFUL name** (superseding the earlier rename-to-
`s1-complete-run.bk2` mandate): the shared `traces/s1/_movies/s1-complete-run.bk2` is a
DIFFERENT movie (the original complete-run), and `TraceCatalog.resolveBk2` resolves the
shared `_movies/` name FIRST — a same-named round-trip bk2 in `special_stage/` would be
shadowed by the wrong movie. Instead commit the movie under its own name (e.g.
`s1-ghz-maze-roundtrip.bk2`) in both trace directories and patch `source_bk2` in the
bundle's `run_manifest.json` + every segment `metadata.json` to match (the recorder
hardcodes `"s1-complete-run.bk2"` at `write_metadata`; patching ALL copies keeps the
bundle internally consistent — this is what the committed
`traces/s1/runs/s1-ghz-maze-roundtrip/` bundle does).

**VERIFY-ON-FIRST-CAPTURE Self-Check:**

At SS-segment open, every 300 frames during the stage, and at finalize time, the recorder
prints diagnostics to stdout. Confirm all of the following before committing the trace:
- A plausible angle range in the finalize summary (`SS self-check: angle range seen=...`).
- The final `ss_rotate` ramping toward `0x1800` (the exit ramp target).
- Rings and emeralds behaving sensibly across the printed samples.
- The finalize `v_lastspecial` re-read printing `(special_stage_index + 1) % 6`. Anything else
  means the `SS_Load` emerald-skip loop fired and the recorded `special_stage_index` is
  suspect.

Any surprise in these prints means re-derive the RAM map before committing the trace — do not
commit a trace whose self-check output looks off.

## Recording S2 Halfpipe Round-Trip Traces (s2-ehz-halfpipe-roundtrip)

An **S2 halfpipe round-trip trace** captures a single EHZ playthrough that includes entry into
the circling-stars special stage (halfpipe, accessed via a star post) mid-act, completion or
failure of the halfpipe, and continuation back into the level. This exercises the run-mode
detour state machine added to `s2_trace_recorder.lua` (env-gated on `OGGF_TRACE_RUN_ID`): the
level segment finalizes at the `Game_Mode=$10` edge, a minimal special-stage segment is sampled
directly (no `event.onmemoryexecute` hooks), and the return level segment re-arms on exit.

**Human Recording Procedure (BizHawk 2.11 + Genplus-gx):**

1. Start a new movie from power-on with the S2 World REV01 ROM, 1-player Sonic+Tails.
2. Play EHZ Act 1, collecting at least 50 rings. Keep the emerald count below 7 — 7 emeralds
   changes the star-post behaviour (no special-stage entry).
3. Touch a star post to open the circling special stars, then enter them to trigger the
   halfpipe special stage.
4. Play the halfpipe to its conclusion — complete it, or fail out of it. Either outcome is
   acceptable for this recording.
5. Continue playing in the level after the halfpipe returns you to EHZ, until control settles
   (a few seconds of normal gameplay after the transition).
6. Stop the movie and save as `s2-ehz-halfpipe-roundtrip.bk2`.

**Recorder Invocation:**

Run the S2 recorder over your movie file through `record_s2_trace.bat` (not the raw
`run_bizhawk_lua.bat` invocation used by the S1/S3K sections above) — the wrapper is what
populates `OGGF_BK2_BASENAME` and `OGGF_BK2_FRAME_COUNT` for you:

```bat
set OGGF_TRACE_RUN_ID=s2-ehz-halfpipe-roundtrip

tools\bizhawk\record_s2_trace.bat ^
  "s2.gen" ^
  "s2-ehz-halfpipe-roundtrip.bk2"
```

Leave `OGGF_S2_TRACE_PROFILE` unset (do not pass a third argument) — `record_s2_trace.bat`
defaults it to `gameplay_unlock` when omitted, and that is the profile the run's level segments
must carry. Setting `OGGF_TRACE_RUN_ID` puts the recorder into run mode: every new
run-mode code path is gated on `run_id ~= nil` (`s2_trace_recorder.lua`, `run_id` assignment
near the top of the run-mode block), so plain-mode recordings are unaffected.

**`OGGF_TRACE_OUTPUT_DIR` does not apply here:** unlike `s1_complete_run_recorder.lua` and
`s3k_complete_run_recorder.lua`, `s2_trace_recorder.lua`'s `OUTPUT_DIR` is a hardcoded
`"trace_output/"` local (not read from an env var), so output always lands under
`tools\bizhawk\trace_output\` relative to the recorder script. Do not set that env var expecting
it to redirect S2 output.

**Expected Output:**

Run mode writes numbered per-segment subdirectories under `tools\bizhawk\trace_output\`:
- `run_manifest.json` — indexed transitions for the EHZ1→halfpipe and halfpipe→EHZ1 boundaries.
- `seg1_ehz1/` — level segment (EHZ Act 1, frames 0 to star-post entry). Run-mode segment dirs
  are named `seg<N>_<zone><act>`, where `N` counts level arms only (the `ss` segment does not
  consume a number).
- `ss/` — special-stage segment. The dir token is the literal `"ss"` with no counter (a
  single-detour MVP; a future multi-detour run would need an S1/S3K-style repeat counter).
- `seg2_ehz1/` — EHZ Act 1 re-entry segment following the halfpipe return. Step 5 above
  guarantees this segment will be present.

`record_s2_trace.bat`'s own post-processing step checks for a top-level `trace_output\metadata.json`
before compressing and printing the metadata summary; run mode never writes one (metadata always
lands inside a per-segment subdir), so the wrapper will print a benign
`WARNING: No trace output found` and skip its own compression/summary step. This is expected —
the `seg1_ehz1/`, `ss/`, `seg2_ehz1/`, and `run_manifest.json` files are still written correctly.
Apply gzip compression yourself before committing, recursing into the segment subdirectories
(`compress-traces.ps1 <path-to-trace_output> -Recurse -ThresholdBytes 0`).

**PROHIBITION:** do NOT copy the run's `ss/` segment over `src/test/resources/traces/s2/special_stage`
— the committed interior trace there is produced by `s2_ss_trace_recorder.lua` with the
RunObjects PC hooks and is governed by the `Assert-SsAuxCoverage` contract; the run's `ss/`
segment has a reduced aux surface (no `run_objects_end` stream) and is consumed by the run/chain
path only.

**Row-0 alignment note:** the run port's `ss/` segment samples row 0 on the *next* `$10` frame
after entry (`bk2_frame_offset` is captured at entry, but the entry branch returns without
writing a row) — one frame later than the interior `s2_ss_trace_recorder.lua`'s convention,
which records frame 0 immediately at arm time. See the comment above `start_ss_segment()` in
`s2_trace_recorder.lua` for the full rationale; keep this one-frame difference in mind for any
future comparator work against interior-recorder `ss` traces.

**Commit Layout:**

Commit the whole run under test resources, with the source bk2 alongside it:

```
src/test/resources/traces/s2/runs/s2-ehz-halfpipe-roundtrip/
  ├── run_manifest.json
  ├── s2-ehz-halfpipe-roundtrip.bk2
  ├── seg1_ehz1/
  │   ├── metadata.json
  │   ├── physics.csv.gz
  │   └── aux_state.jsonl.gz
  ├── ss/
  │   ├── metadata.json
  │   ├── physics.csv.gz
  │   └── aux_state.jsonl.gz
  └── seg2_ehz1/
      ├── metadata.json
      ├── physics.csv.gz
      └── aux_state.jsonl.gz
```

Each segment's `metadata.json` records `"source_bk2": "s2-ehz-halfpipe-roundtrip.bk2"` (from
`OGGF_BK2_BASENAME`, populated automatically by `record_s2_trace.bat` from the bk2 filename) —
keep the committed bk2's filename matching that recorded basename.

**VERIFY-ON-FIRST-CAPTURE Self-Check:**

At the halfpipe entry (`starpost_special` transition) and exit (`stage_exit` transition), the
recorder prints every transition field to stdout. Confirm all of the following before committing
the trace:
- `special_bonus_entry_flag` (`f_bigring`) reads `1` at entry.
- `saved_x_pos`/`saved_y_pos` are plausible coordinates near the star post that was touched.
- `rings_before` is at least 50.
- `rings_after` (printed at the `stage_exit` transition) reads `0` — the ROM zeroes ring/emerald
  tracking on the level reload that follows a special stage, so this is the expected, correct
  value, not a bug.
- `special_stage_index` (printed when the `ss` segment arms) is a plausible index.

Any surprise in these prints means re-verify the RAM table before committing the trace.

If you update the trace workflow, update the guide page above first so the contributor docs stay in
sync with the tools.

## Recorder capability parity follow-up

Treat `s1_complete_run_recorder.lua` as the current reference bar for recorder
ergonomics and resilience: repeated-segment naming, multi-mode run manifests,
direct BK2 input alignment, periodic metadata rewrites, lag and object-state
diagnostics, self-check summaries, movie-end finalization, and fast-headless
operation through the shared launcher. Audit the S1/S2/S3K single-segment and
complete-run recorders against that capability list before the next recorder
schema uplift. Game-specific RAM fields may differ; lifecycle safety,
reporting quality, truthful source-movie metadata, and launch behavior should
not.

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
