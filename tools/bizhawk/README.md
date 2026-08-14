# BizHawk Trace Recording

The canonical trace replay documentation now lives in:

- [`docs/guide/contributing/trace-replay.md`](../../docs/guide/contributing/trace-replay.md)

Use this folder for the recorder scripts and local BizHawk assets:

- `fetch_bizhawk_2_11_linux.sh` downloads and verifies the supported Linux build
- `run_bizhawk_lua.bat` launches any Lua/BK2/ROM combination safely for
  diagnostics and one-off probes
- `record_trace.bat` launches S1 recording through the reusable no-audio/no-render launcher
- `s1_trace_recorder.lua` is retained for predecessor diagnostics and recorder
  research; it is not a current publication path
- `s1_complete_run_recorder.lua` records per-level segments (plus special-stage
  segments and `run_manifest.json` in run mode) from a single Sonic 1 BK2
  playthrough for predecessor diagnostics; native v5 publication uses the
  headless harness
- `record_s2_trace.bat` launches the Sonic 2 recorder through the reusable no-audio/no-render launcher
- `record_s2_level_select_traces.ps1` records the Sonic 2 level-select BK2 set into test resources
- `s2_trace_recorder.lua` is retained for predecessor diagnostics and recorder
  research; it is not a current publication path
- `record_s3k_trace.bat` launches the Sonic 3&K recorder through the reusable no-audio/no-render launcher
- `s3k_trace_recorder.lua` is retained for predecessor diagnostics and recorder
  research; it is not a current publication path
- `s3k_complete_run_recorder.lua` records per-zone segments from any Sonic 3&K BK2 (see
  "Trace Run Manifests" section below) — natively superseded on Linux by the headless
  harness's `--trace-profile complete_run` / `--run-id` modes below; the Lua is
  retained for predecessor diagnostics
- `record_s1_credits_traces.bat` launches forced Sonic 1 credits-demo capture
- `s1_credits_trace_recorder.lua` records the built-in ending replays without a BK2
- `lib/oggf_trace_common.lua` is retained predecessor Lua support: a shared
  module of game-agnostic leaf helpers
  (`bk2_input_mask`, `hex`, `angle_to_ground_mode`, `read_speed`,
  `rom_joypad_to_mask`, `write_aux`, `json_escape`, `json_quote`, and the
  `INPUT_*` bitmask constants) that every recorder `loadfile`s at startup via a
  small `oggf_lib_dir()` loader. It holds only pure helpers whose emitted bytes
  are identical to the previously-inlined copies. Predecessor schema writers,
  version constants, and the fast-headless toggle block deliberately stay
  inline per recorder. `run_bizhawk_lua.bat` exports `OGGF_BIZHAWK_LIB` so the
  loader finds it on the historical wrapper/headless route; a `debug.getinfo`
  fallback covers direct `--lua=` diagnostics. These details document
  predecessor reproduction only and cannot select native v5 publication
  behavior.

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

## Sonic 1 GHZ music-driver parity

From the repository root, run the local two-sided driver check with:

```bash
tools/audio/run_s1_audio_parity.sh \
  --rom "/absolute/path/to/Sonic The Hedgehog (W) (REV01) [!].gen"
```

The ROM argument is optional when the pinned World REV01 image is present at
the main repository root. The command accepts `--movie`, `--bizhawk-home`, and
`--output-root` overrides; `BIZHAWK_HOME` is also honored. It verifies the ROM,
the controller-only `s1-soundtest-ghz.bk2` fixture, and the exact BizHawk 2.11
Linux x64 executable before launching anything.

Each invocation creates a new directory below
`target/audio-parity/s1-ghz/`. It retains two normalized BizHawk captures, two
normalized OpenGGF captures, the BizHawk process logs, a human report, and a
compact JSON report. The two captures from each producer must be byte-identical
before comparison. Capture and report publication refuses existing paths and
paths outside that canonical run directory. These detailed files are local, ignored diagnostics and
must not be copied into test resources or committed.

Exit status `0` means exact parity, `3` means both captures were valid and the
comparator found a state or ordered-register mismatch, `4` means validation,
capture, determinism, or tooling failed, and `2` means invalid command-line
usage. A status of `3` is a successful diagnostic run, not a capture failure.
Inspect the printed run directory before changing audio code; the tool never
realigns ticks or changes chip-port ordering.

## Sonic 1 GHZ1 gameplay-audio timeline

The gameplay timeline is a separate, stricter diagnostic: schema v2 compares
raw caller/ROM queue requests and later resolved admissions at their own frame
boundaries, followed by per-role contention decisions and final ownership over
the committed GHZ1 complete-run interval. Run it with the pinned REV01 ROM:

```bash
tools/audio/run_s1_ghz1_gameplay_audio_timeline.sh \
  --rom "/absolute/path/to/Sonic The Hedgehog (W) (REV01) [!].gen"
```

It accepts only an optional `--bizhawk-home` override. The movie, output root,
ROM hash, EmuHawk, installed core assembly, and Genesis Plus GX binary identities
are pinned. A run creates one unique child
of `target/audio-parity/s1-ghz1-gameplay/`, captures each producer twice, and
requires byte-identical captures before reporting semantic parity. BizHawk
writes only a fresh `.staging` child; Java validates the complete strict JSONL
stream and atomically create-new publishes it after a successful producer. A
failed producer enters a separate trusted discard path and never invokes
publication, even for complete staging bytes. The runner rejects Java/Mono command replacement environment seams,
never overwrites a capture or report, and preserves the four captures, logs,
and two reports for investigation. Exit status `3` is a valid semantic
mismatch; `4` is an untrusted/failed capture or tool failure.

This runner has an explicit trusted-launch boundary. The kernel, system dynamic
loader, and parent environment are trusted until the runner process is created;
callers must launch it without any `LD_*` loader-injection variable. Because
Bash is dynamically linked, `LD_PRELOAD` or `LD_AUDIT` can execute (or, for an
inexistent library, make the loader print a diagnostic) before the script gets
control. The runner does not claim protection for that pre-start interval. Once
started, it rejects every inherited `LD_*` variable with exit status `4` before
argument parsing or project/tool work, retains its fixed absolute bootstrap
tools, and launches all producer/tool children from an `env -i` allowlist. Use
an external clean launcher or static bootstrap if the parent environment itself
is not trusted.

## Native S1/S2 v5 capture contract

Current S1 and S2 publication runs through
`tools/bizhawk-headless/run.sh`. Every candidate eligible for publication must
be a strict v5 envelope with `recorder: native-bizhawk-headless`,
`recorder_version: 3.0`, and `trace_schema: 5`; level segments use the shared
42-column physics row.
The predecessor metadata keys `lua_script_version`, `csv_version`,
`ss_csv_version`, `hardware_timing_schema`, and `run_schema` are absent and
are not compatibility inputs.

When `--load-queue-state` is selected, current captures advertise
`load_queue_state_per_frame` and
`dynamic_art_transfer_state_per_frame`. Validate every scratch candidate with
`tools/traces/validate_trace_v5.py` and follow
[`docs/guide/contributing/trace-v5-publication.md`](../../docs/guide/contributing/trace-v5-publication.md)
before replacing a committed fixture.

The standard invocation shape is:

```bash
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh --mode trace --rom /abs/path/to/game.gen \
  --movie /abs/path/to/movie.bk2 --output /abs/path/to/scratch-candidate
```

Use `--trace-profile complete_run` or `--run-id <id>` for the corresponding
native v5 run publication mode. Game-specific selection and output layout are
documented in the headless harness README.

## Pre-v5 historical evidence: S1/S2 native-port parity ledger

The following S1/S2 sections preserve the predecessor Lua-to-native parity
record. Their version stamps, secondary schema axes, fixture normalization,
and byte-identity rules are historical evidence only, not current capture or
publication instructions.

### Native headless GPGX harness (historical parity ledger)

The Linux-only native GPGX harness (`tools/bizhawk-headless/`) runs the
BizHawk 2.11 core through Mono without starting EmuHawk and without requiring
`DISPLAY`. It records **full canonical Sonic 1, Sonic 2, and Sonic 3 & Knuckles
traces across every recorder mode** and is the supported replacement for
`s1_trace_recorder.lua`, `s1_complete_run_recorder.lua`, `s2_trace_recorder.lua`,
`s3k_trace_recorder.lua` (all three STANDARD profiles), and now
`s3k_complete_run_recorder.lua` (complete-run and run mode) when recording on
Linux. With the S3K complete-run migration, **every Lua recorder that emits
this harness's `physics.csv`/`aux_state.jsonl`/`metadata.json` trace schema
now has a byte-parity-gated native port.** The remaining Lua-only surface is
narrow: `s2_ss_trace_recorder.lua` (the S2 special-stage-only recorder,
outside this schema) and `s1_credits_trace_recorder.lua` (a BK2-free credits
capture, also outside this schema) have no native equivalent, and the native
S3K ports (standard and complete-run) still defer 14 hook-driven aux
families (below) that only the Lua recorders can capture.
`--mode trace` auto-detects the game from the supplied ROM (S1 World REV01, S2
World REV01, or the S3&K locked-on combination) and selects the matching
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

### Sonic 1 complete-run and run mode

With an S1 World REV01 ROM, `--mode trace` also replaces
`s1_complete_run_recorder.lua` on Linux. Two flags select the complete-run
recorder pipeline (both route through the same engine, whose giant-ring
special-stage detour machine is always on, exactly like the Lua):

- **`--trace-profile complete_run`** — one movie pass over an entire
  playthrough BK2 emits a separate per-level segment directory
  (`physics.csv`, `aux_state.jsonl`, `metadata.json`) for every level the
  movie clears, using the recorder's ROM-derived directory tokens (so SBZ3
  lands in `lz4/` and Final Zone in `sbz3/` — the ROM encodes them as LZ act
  4 and SBZ act 3). `run_manifest.json` is emitted only if the movie takes a
  giant-ring detour (Lua gate); a stage-free pass publishes exactly the
  per-level directories.
- **`--run-id <id>`** (mirroring `OGGF_TRACE_RUN_ID`; mutually exclusive
  with `--trace-profile`) — forces `run_manifest.json` emission with that
  `run_id`, records any giant-ring special-stage detour as a dedicated `ss`
  segment (`trace_profile s1_special_stage`), and stamps run/source metadata
  fields. As in S2 run mode, output is staged fully and published as one
  all-or-nothing no-replace set with the manifest linked last.

Verified capture commands (BK2 frame offsets are auto-detected per segment):

```bash
# Complete run: 19 level segments from the canonical full-playthrough movie
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S1_ROM_PATH" \
  --movie "$PWD/src/test/resources/traces/s1/_movies/s1-complete-run.bk2" \
  --output "$PWD/target/bizhawk-headless-s1-completerun" \
  --trace-profile complete_run

# Run mode: level -> giant-ring special stage -> level round trip
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S1_ROM_PATH" \
  --movie "$PWD/src/test/resources/traces/s1/runs/s1-ghz-maze-roundtrip/s1-ghz-maze-roundtrip.bk2" \
  --output "$PWD/target/bizhawk-headless-s1-run" \
  --run-id s1-ghz-maze-roundtrip
```

**Byte-parity guarantee vs the Lua recorder:** three ROM-backed differential
gates in `tools/bizhawk-headless/test.sh` prove the port end to end:

- **19 complete-run segments** — one `--trace-profile complete_run` pass of
  `_movies/s1-complete-run.bk2` (195,493 input rows) reproduces all 19
  `src/test/resources/traces/s1/*_completerun` fixture directories:
  `physics.csv` and `aux_state.jsonl` byte-identical (LF line endings, like
  the fixtures), exactly the 19 segment directories and no
  `run_manifest.json`.
- **Maze round trip** — one `--run-id` pass of
  `runs/s1-ghz-maze-roundtrip/s1-ghz-maze-roundtrip.bk2` reproduces the
  `ghz1`/`ss`/`ghz2` segments' `physics.csv` and `aux_state.jsonl`
  byte-identically with **no normalization** (the fixture set carries the
  canonical Windows capture's CRLF line endings, which run-mode publication
  reproduces), plus `run_manifest.json` and each `metadata.json` under the
  normalization policy below.
- **Standalone special stage** — `src/test/resources/traces/s1/special_stage/`
  is a published copy of the same run capture's `ss/` segment (there was
  never a separate standalone invocation); the gate compares the produced
  `ss/` bytes against it.

**Normalization policy (metadata/manifest only):** exactly two things may
differ from the committed fixtures — the `recording_date` value, and one
pinned `lua_script_version` line. The native port stamps the current Lua's
version `"3.17"`; the complete-run fixtures are stamped `"3.14"` and the
run/standalone fixtures `"3.15"` (captured by an interim script). The
handed-down rule to verify the version-marker deltas before allowing them was
carried out against the Lua's own version-bump commit diffs
(`docs/s1-complete-run-behavior.md` §2 and `docs/s1-run-mode-behavior.md`
§10): for these fixtures' code paths the 3.14→3.17 and 3.15→3.17
output-affecting deltas are exactly the version strings, so the gates
substitute exactly that one line per file (fixture line must be the fixture
stamp, produced line must be `"3.17"`) and every other byte must match. The
8 `credits_*` fixture dirs are **not** produced by this recorder — they come
from a separate stable-retro credits pipeline and stay out of scope.

The byte-level porting contracts live in
`tools/bizhawk-headless/docs/s1-complete-run-behavior.md` (level-segment
state machine, per-segment offsets, encodings) and
`tools/bizhawk-headless/docs/s1-run-mode-behavior.md` (detour machine,
special-stage writer, manifest, version-stamp provenance); where any spec
text and the Lua disagree, the Lua wins.

### Sonic 2 trace mode (all three recorder modes)

With an S2 World REV01 ROM, `--mode trace` replaces `s2_trace_recorder.lua`
(v9.13-s2) on Linux across **all three of its operating modes**. The Lua
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
  exist without its segment files). Mirroring Lua v9.13-s2, run mode
  survives the in-level `Game_Mode $8C` reload family (death/star-post
  restarts, time overs, act and zone transitions, the ObjB2
  SCZ→WFZ→DEZ routes) — the armed level segment finalizes, a
  `death_restart` or `level_advance` transition is recorded, and the next
  `$0C` gameplay frame re-arms — and run-mode special-stage segments emit
  the hook-free subset of the standalone SS recorder's aux event stream.
  The run-mode-only `--effective-movie-length <frames>` flag injects a
  capture session's movie-length signal into the movie-done guard when the
  canonical session ended earlier than the BK2's file-derived row count
  (needed by the halfpipe differential gate; see
  `tools/bizhawk-headless/docs/s2-run-mode-behavior.md` §11.5).

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

**Byte-parity guarantee vs the Lua recorder:** five canonical fixtures gate
the S2 port end to end via the ROM-backed `S2TraceDifferential` tests in
`tools/bizhawk-headless/test.sh`:

- `src/test/resources/traces/s2/ehz1_fullrun/` — plain `gameplay_unlock`;
- `src/test/resources/traces/s2/arz/` — `level_gated_reset_aware`, segment 0;
- `src/test/resources/traces/s2/arz2/` — `level_gated_reset_aware`, segment 1;
- `src/test/resources/traces/s2/runs/s2-ehz-halfpipe-roundtrip/` — run mode
  over the full level → halfpipe → level → halfpipe → level round trip
  (regenerated from a verified native 9.13-s2 capture at the canonical
  session's effective movie length 22612 — exactly the documented §11.4
  delta vs the old 9.12 set: the `ss`/`ss_2` `aux_state.jsonl` go from
  0 bytes to the SS aux event stream, `lua_script_version` stamps move to
  `9.13-s2`, and everything else including the `.bk2` is unchanged);
- `src/test/resources/traces/s2/runs/s2-sonic-tails-complete-emeralds/` —
  run mode over the full complete-game movie (35 segments, all seven
  special stages, 34 transitions, no movie-length injection).

`physics.csv` and `aux_state.jsonl` are byte-identical on every fixture, and
run mode additionally reproduces `run_manifest.json` and every per-segment
file byte-identically with **no normalization**, plus an exact-output-layout
assertion (the canonical segment directories and `run_manifest.json`,
nothing else). `metadata.json` differs only
in the `recording_date` value, plus — on the older `ehz1_fullrun`/`arz`/`arz2`
fixtures stamped `9.11-s2` — the documented `lua_script_version`
`9.11-s2` → `9.13-s2` delta (the native port emits the v9.13-s2 surface,
whose plain-mode output is declared byte-identical to v9.11-s2 except that
version string; the run fixtures are themselves stamped `9.13-s2`, so no
version normalization applies there). The byte-level porting contracts live
in `tools/bizhawk-headless/docs/s2-trace-recorder-behavior.md` (plain +
segment-selection modes) and
`tools/bizhawk-headless/docs/s2-run-mode-behavior.md` (run mode, incl. the
§11 complete-run extension); where any spec text and the Lua disagree, the
Lua wins.

### Sonic 3 & Knuckles trace mode (STANDARD recorder, all three profiles)

With the S3&K locked-on ROM, `--mode trace` replaces the frozen
`s3k_trace_recorder.lua` (v6.37-s3k) on Linux across its three
STANDARD-recorder profiles, selected
with `--trace-profile` (mirroring the Lua's `OGGF_S3K_TRACE_PROFILE`):

- **`gameplay_unlock`** — the default; no extra flags.
- **`aiz_end_to_end`** — the AIZ1 → AIZ2 → HCZ handoff checkpoint stream;
  arms on the first level-family frame and records that arm frame itself as
  trace row 0 (the only profile that does).
- **`level_gated_reset_aware`** — discards and re-arms on a soft reset back
  to title/level-select, and finalizes on a zone-leave rather than either
  movie-end stop.

`s3k_complete_run_recorder.lua` (the separate per-zone-segment / bonus /
special-stage recorder, frozen at `6.37-s3k-completerun`) is a
**separately natively ported recorder** — see "Sonic 3 & Knuckles
complete-run and run mode" below — selected by `--trace-profile complete_run`
or `--run-id` instead of a `--trace-profile` value from the table above;
`--gameplay-segment` remains rejected outright on the S3K ROM (the Lua never
reads `OGGF_TRACE_GAMEPLAY_SEGMENT` for either S3K recorder).

Verified capture commands (BK2 frame offsets are auto-detected):

```bash
# aiz_end_to_end
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S3K_ROM_PATH" \
  --movie "$PWD/src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/s3-aiz1-2-sonictails.bk2" \
  --output "$PWD/target/bizhawk-headless-s3k-trace" \
  --trace-profile aiz_end_to_end

# level_gated_reset_aware (CNZ fixture; MGZ uses the same profile)
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S3K_ROM_PATH" \
  --movie "$PWD/src/test/resources/traces/s3k/cnz/s3k-cnz-sonic-tails.bk2" \
  --output "$PWD/target/bizhawk-headless-s3k-trace" \
  --trace-profile level_gated_reset_aware
```

#### S3K hardware-timing stream

Current native S3K captures use `trace_schema: 5`,
`recorder: native-bizhawk-headless`, and `recorder_version: 3.0`. Optional
`hardware_timing.jsonl` uses one module-plus-direct grammar with exactly two
production-submitted work kinds:

- `KOS_DECOMPRESSION_QUEUE`, emitted when the mirrored `$FF40-$FF5F` direct
  FIFO retires a proven head at `pre_main_loop`; and
- `KOS_MODULE_QUEUE`, emitted at `post_objects` when a final-active parent
  retires through the canonical ROM FIFO shift. Observation on a held level-
  counter row does not move that ROM-owned service phase to `vint_service`.

Each kind owns an independent ordinal ledger. Events carry only kind,
ordinal, submission fingerprint, raw frame, and boundary; they never contain
compressed or decoded bytes. When both kinds retire on one raw frame, the
module `post_objects` edge is serialized before the direct `pre_main_loop`
edge. The canonical final-active FIFO transition may attribute a held-row
retirement without changing event identities or queue ledgers.

There is no timing-schema compatibility mode. The native recorder and the
cross-game hardware-timing contract own current behavior; frozen Lua and
numbered predecessor schemas are research evidence only. A candidate stays in
scratch until strict v5 validation, literal comparison, candidate-root replay,
native gates, and explicit publication review succeed. Never hand-edit an
installed payload to cross the v5 boundary.

The byte-level contract lives in
`tools/bizhawk-headless/docs/s3k-trace-recorder-behavior.md` (RAM map,
physics CSV, v5 timing ledgers/stream, metadata) and
`tools/bizhawk-headless/docs/s3k-profiles-and-hooks.md`
(profiles, stop ordering, hook architecture); `tools/bizhawk-headless/docs/s3k-aux-events.md`
owns the aux event surface. Their `Pre-v5 historical evidence` sections retain
the numbered predecessor record without creating a live compatibility axis.

**Deferred: hook-driven aux families.** The Lua's M68K exec/memory-write
diagnostic hooks (`OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1`) drive 13 aux event
families (e.g. `tails_cpu_normal_step`, `aiz_boundary_state`,
`collision_response_list_per_frame`, `cnz_event_ram`) that the native port
does not implement — every gated fixture was captured with hooks unset, and
`S3KHookAbsenceTests` pins that absence to the fixture bytes. Implementing
them would require a native LibGPGX exec/mem callback surface on `GpgxHost`;
see `s3k-profiles-and-hooks.md` §2.4 for the deferral rationale.

**Environment variables that now refuse loudly instead of silently
diverging.** The Lua recorder reads its entire diagnostic surface from the
environment, never from CLI flags, so an operator's shell still exporting one
of these would otherwise change what the Lua would have produced from the
same movie while the native capture — which models none of them — got
committed as canonical with no diagnostic. The CLI now refuses (exits
non-zero with an explanatory message) whenever any of the following is set,
scoped to the S3K trace branch only:

| Class | Variables | Reason |
|---|---|---|
| Hook arming | `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1`, `OGGF_S3K_RNG_CALL_RANGE`, `OGGF_S3K_CNZ_EVENT_RAM_RANGE` | Arms a deferred hook-driven family and appends it to `aux_schema_extras`. |
| Polled-family window overrides | `OGGF_S3K_AIZ_FIRE_RANGE`, `OGGF_S3K_AIZ_WALL_SENSOR_RANGE`, `OGGF_S3K_CRL_RANGE`, `OGGF_S3K_CNZ_CYLINDER_RANGE`, `OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START`/`_END` | Retunes a frame-polled family the port implements with the Lua's default window pinned as a constant. |
| Early stop | `OGGF_TRACE_STOP_FRAME`, `OGGF_BK2_FRAME_COUNT` | Finalizes before the movie/zone stop and truncates both output files. |

The window overrides belonging to families the port defers entirely
(`OGGF_S3K_POSITION_WRITE_RANGE`, `OGGF_S3K_VELOCITY_WRITE_RANGE`,
`OGGF_S3K_SOLID_CONT_RANGE`, `OGGF_S3K_AIZ_SHIP_LOOP_RANGE`,
`OGGF_S3K_AIZ_BOUNDARY_RANGE` and its legacy `_FRAME_START/END` pair,
`OGGF_S3K_AIZ_TRANSITION_FLOOR_FRAME_START/END`) are deliberately **not**
refused — their flushes are additionally hook-gated, so with the hook switch
off (itself a refusal) they change no byte of the Lua's own output either.
Full table and rationale: `s3k-aux-events.md` §5.1.

### Sonic 3 & Knuckles complete-run and run mode

With the S3&K locked-on ROM, `--mode trace` uses the same sole v5 envelope:
`trace_schema: 5`, `recorder: native-bizhawk-headless`, and
`recorder_version: 3.0`. Complete-run timing uses the same
module-plus-direct grammar as the STANDARD recorder. This is the separate
per-zone-segment / bonus-stage / special-stage recorder, selected the same way
as S1's complete-run recorder, not via `--trace-profile <one of the three
STANDARD profiles>`:

- **`--trace-profile complete_run`** — one movie pass over an entire
  playthrough BK2 emits a separate per-zone segment directory
  (`physics.csv`, `aux_state.jsonl`, `metadata.json`) for every zone the
  movie clears, using the recorder's ROM-derived zone tokens (`zone_token_for`,
  distinct from the STANDARD recorder's `ZONE_NAMES` table). `run_manifest.json`
  is emitted only on a bonus/giant-ring detour (Lua gate); a stage-free pass
  publishes exactly the per-zone directories.
- **`--run-id <id>`** (mirroring `OGGF_TRACE_RUN_ID`; mutually exclusive with
  `--trace-profile`) — forces `run_manifest.json` emission, records bonus-stage
  detours as `bonus_stage`-kind segments (`trace_profile s3k_bonus_stage`) and
  special-stage detours as `special_stage`-kind segments (`trace_profile
  s3k_special_stage`), and stamps run/source metadata fields. As in S1/S2 run
  mode, output is staged fully and published as one all-or-nothing no-replace
  set with the manifest linked last. `--gameplay-segment` remains rejected.

Verified capture commands (BK2 frame offsets are auto-detected per segment):

```bash
# Complete run: 15 zone segments from the canonical Sonic+Tails playthrough
# movie (only the first 7 — AIZ..MHZ — are committed as *_completerun fixtures)
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S3K_ROM_PATH" \
  --movie "$PWD/src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2" \
  --output "$PWD/target/bizhawk-headless-s3k-completerun" \
  --trace-profile complete_run

# Run mode: Knuckles multi-bonus movie with an explicit run id
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S3K_ROM_PATH" \
  --movie "$PWD/src/test/resources/traces/s3k/_movies/s3-knux-multibonus-ss.bk2" \
  --output "$PWD/target/bizhawk-headless-s3k-run-c" \
  --run-id s3k-multibonus

# Run mode: the same movie under a second explicit run id
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S3K_ROM_PATH" \
  --movie "$PWD/src/test/resources/traces/s3k/_movies/s3-knux-multibonus-ss.bk2" \
  --output "$PWD/target/bizhawk-headless-s3k-run-b" \
  --run-id s3-knux-multibonus-ss

# Knuckles complete super-emerald run: candidate capture only after approval
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S3K_ROM_PATH" \
  --movie "$PWD/src/test/resources/traces/s3k/_movies/s3k-knuckles-complete-superemeralds.bk2" \
  --output "$PWD/tools/bizhawk-headless/.scratch/s3k-knuckles-complete-superemeralds-v5-candidate" \
  --run-id s3k-knuckles-complete-superemeralds
```

#### Pre-v5 historical capture notes

Everything below this heading records predecessor fixture identities,
differential gates, and migration decisions. Versioned stamps and schema axes
here are historical evidence only; they do not select live recorder, parser,
replay, or publication behavior.

The super-emerald movie has 434,417 input rows and SHA-256
`aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc`.
The committed 6.40 schema-2 native capture publishes 67 segments and 48
transitions at
`src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/`.
Its exact file lengths, hashes, timing edges, and manifest are frozen in
`src/test/resources/traces/s3k/hardware-timing-publication.tsv`; the reviewed
inventory and terminal-tail arithmetic are recorded in
`docs/architecture/validation/2026-07-30-s3k-knuckles-superemerald-trace-publication.md`.

The command above creates a 6.42 candidate in scratch; do not run or install
it without the separate review and publication approval.

**Current schema-2 fixture parity:** committed complete-run/run fixtures are
`6.40-s3k-completerun`, trace schema 7, hardware-timing schema 2. The
maintained native writer emits `6.42-s3k-completerun` with the same schemas.
Three ROM-backed differential gates cover
the capture identities below (`docs/s3k-run-publication.md` §0). In this
historical inventory, “byte-identical” refers to physics/aux payloads and
manifests; current metadata has the exact declared version/schema delta, and
schema-2 timing streams are publication candidates:

- **`S3KCompleteRunSegmentsDifferentialTests`** — one untruncated
  `--trace-profile complete_run` pass over the 466,334-row complete-playthrough
  movie (measured ~6 min wall, ~235 MB peak RSS, 2.84 GB scratch under
  `tools/bizhawk-headless/.scratch/`) reproduces all seven committed
  **identity (A)** `*_completerun` fixtures (`aiz_completerun`,
  `hcz_completerun`, `mgz_completerun`, `cnz_completerun`, `icz_completerun`,
  `lbz_completerun`, `mhz_completerun`) byte-identical — `physics.csv` and
  `aux_state.jsonl` by length AND sha256, zero normalization — plus a
  full 15-segment summary assertion (through `fbz`/DDZ) proving the
  segmenter's arm/finalize ordering on both sides of every fixture boundary,
  and asserts no `run_manifest.json` is produced (no detour, no `--run-id`).
- **`S3KCompleteRunDifferentialTests`** — a fast, truncated (`--run-id
  s3k-multibonus --effective-movie-length 7001`) pass over the Knuckles
  multi-bonus movie reproduces the **identity (C)** `bonus_gumball` fixture
  byte-identical in about five seconds, as a quick end-to-end smoke gate
  layered under the full run-mode gate below.
- **`S3KRunModeDifferentialTests`** — two untruncated passes over the same
  movie (measured ~2m20s wall combined, ~235 MB peak RSS): `--run-id
  s3k-multibonus` reproduces all four **identity (C)** fixtures
  (`bonus_gumball`, `bonus_slots`, `bonus_pachinko`, `special_stage`)
  byte-identical; `--run-id s3-knux-multibonus-ss` reproduces the **identity
  (B)** run tree
  (`src/test/resources/traces/s3k/runs/s3-knux-multibonus-ss/`) — all 25
  segment directories and `run_manifest.json` — likewise byte-identical
  (`physics.csv` / `aux_state.jsonl` by length AND sha256, zero
  normalization; `run_manifest.json` byte-identical). Identity (B) was not
  always this clean a gate — see "Identity (B) reached that state by
  regeneration" below for the CRLF / `ADDR_FRAMECOUNT` / armed-hooks history
  and the `ADDR_VBLA_WORD` `0xFE12`→`0xFE0E` recapture that keeps it
  byte-identical through this fix. `S3KHookAbsenceTests` pins that all
  eleven (A)+(C) fixtures and all 25 (B) segments now carry zero hook-driven
  aux events, including the four (B) segments (`hcz_2`, `hcz_6`, `mgz`,
  `mgz_3`) that used to carry real ones before the hooks-off recapture.

**Pinned metadata compatibility (no loose normalization):** committed
metadata carries `6.40-s3k-completerun`, trace schema 7, hardware-timing
schema 2. Current native metadata carries `6.42-s3k-completerun` with the same
schemas. Apart from `recording_date`, only that exact version literal may
differ. Timing events are never normalized away; publication of newly
captured bytes still requires separate approval.

Identity (B) reached that state by regeneration rather than by normalization.
The committed `runs/s3-knux-multibonus-ss/` set was a 2026-07-19 Windows
capture from a Lua build three versions behind, and differed from any fresh
6.32 capture three independent ways — CRLF line endings, the pre-`6564667eb`
`ADDR_FRAMECOUNT` `0xFE08` (`Debug_placement_mode`, dead-zero) counter, and
real hook events on four segments from an armed-hooks run. No current
recorder, Lua or native, could reproduce it, so the gate could only assert
structure. It was recaptured at 6.32 with hooks unset (segmentation provably
unchanged: identical offsets and row counts for all 25 segments, every
physics cell outside the counter column byte-identical), and the gate now
asserts all 25 segments plus `run_manifest.json` byte-for-byte with zero
normalization. It was recaptured again at 6.33 for the `ADDR_VBLA_WORD`
`0xFE12`→`0xFE0E` fix (identity (B) was carrying `Life_count`, i.e. `lives
<< 8`, in its `vblank_counter` column, same as every other S3K fixture); the
byte-for-byte gate held through that recapture too, since the native port's
own `S3KRam.VblankWord` moved to `0xFE0E` in lockstep.

The byte-level porting contracts live in
`tools/bizhawk-headless/docs/s3k-complete-run-behavior.md` (segmentation
state machine, per-segment offsets, `zone_token_for`),
`tools/bizhawk-headless/docs/s3k-completerun-profiles.md` (the three segment
profiles, the retired `ADDR_FRAMECOUNT` recorder-identity fork now unified on
`0xFE04` for both S3K recorders, the `game_paused_state` aux addition), and
`tools/bizhawk-headless/docs/s3k-run-publication.md`
(directory/metadata/manifest byte layout, the three capture identities, the
full environment-variable surface). Frozen Lua bytes remain authoritative
for their published schema-1 physics/aux surface; current schema-2 timing is
owned by the native recorder and approved timing contract.

**Deferred: hook-driven aux families (same deferral as the STANDARD
recorder).** Every committed fixture across all three identities is now
captured with the Lua's M68K exec/memory-write diagnostic hooks unset, so the
port implements none of the 14 hook/env-armed families for this recorder
either. The four identity-(B) segments that used to carry real hook events
(hcz_2, hcz_6, mgz, mgz_3) lost them in the 6.32 recapture, and
`S3KHookAbsenceTests` now pins their absence to the fixture bytes alongside
the rest — so regenerating any fixture with hooks armed fails the gate, which
is the designed signal that a native LibGPGX exec/mem callback surface must
then actually be built. See `s3k-completerun-profiles.md` §11.2 for the full
rationale.

**Environment variables that now refuse loudly instead of silently
diverging.** The complete-run recorder reads its own, distinct environment
surface (`OGGF_TRACE_RUN_ID` and `OGGF_BK2_BASENAME` instead of
`OGGF_S3K_TRACE_PROFILE`, which this recorder never reads because it
hard-pins `TRACE_PROFILE` to `"complete_run"`). The CLI refuses whenever any
of the following is set, scoped to the S3K complete-run branch only:

| Class | Variables | Reason |
|---|---|---|
| Hook arming | `OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1`, `OGGF_S3K_CNZ_EVENT_RAM_RANGE` | Arms a deferred hook-driven family (`OGGF_S3K_CNZ_EVENT_RAM_RANGE` is the sole gate on the frame-polled `cnz_event_ram` emit for this recorder). |
| Polled-family window overrides | `OGGF_S3K_AIZ_WALL_SENSOR_RANGE`, `OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START`/`_END`, `OGGF_S3K_CRL_RANGE`, `OGGF_S3K_CNZ_CYLINDER_RANGE` | Retunes a frame-polled family the port implements with the Lua's default window pinned as a constant. |
| Early stop | `OGGF_TRACE_STOP_FRAME`, `OGGF_BK2_FRAME_COUNT` | Finalizes before the movie/zone stop and truncates both output files; the native recorder models the movie-length signal through `--effective-movie-length` instead. |

Deliberately **not** refused for this subcommand, pinned by a test so the
guard cannot degrade into a blanket `OGGF_*` ban: `OGGF_S3K_TRACE_PROFILE`
(dead — this recorder hard-pins its own profile), `OGGF_S3K_AIZ_FIRE_RANGE`
and `OGGF_S3K_RNG_CALL_RANGE` (both gate families that can never fire under
this recorder's fixed profile / hooks-off default), the window overrides for
families this port defers entirely regardless of the hook switch
(`OGGF_S3K_POSITION_WRITE_RANGE`, `OGGF_S3K_VELOCITY_WRITE_RANGE`,
`OGGF_S3K_SOLID_CONT_RANGE`, `OGGF_S3K_AIZ_SHIP_LOOP_RANGE`,
`OGGF_S3K_AIZ_BOUNDARY_RANGE` and its legacy `_FRAME_START/END` pair,
`OGGF_S3K_AIZ_TRANSITION_FLOOR_FRAME_START/END`), and `OGGF_TRACE_OUTPUT_DIR`
/ `OGGF_BK2_BASENAME` / `OGGF_TRACE_RUN_ID` (modeled by `--output`, the movie
file name, and `--run-id`). Full table and rationale:
`s3k-run-publication.md` §8.1 and the `RejectUnmodeledS3kCompleteRunEnvironment`
doc comment in `tools/bizhawk-headless/src/Program.cs`.

### Limitations and smoke mode

**Limitations:** Linux/Mono only. The S2 special-stage-only recorder
(`s2_ss_trace_recorder.lua`) remains a Lua script with no native port,
and `s1_trace_recorder.lua` / `s1_complete_run_recorder.lua` /
`s2_trace_recorder.lua` / `s3k_trace_recorder.lua` /
`s3k_complete_run_recorder.lua` remain the reference implementations and the
recording path on non-Linux platforms. The harness
needs the BizHawk 2.11 Linux x64 assemblies (`BIZHAWK_HOME` must point at an
absolute install, default `docs/BizHawk-2.11-linux-x64`), Mono, and a
verified Sonic 1 World REV01, Sonic 2 World REV01, or Sonic 3&K locked-on ROM
(`S1_ROM_PATH` / `S2_ROM_PATH` / `S3K_ROM_PATH` for the test suite's
differential gates).

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

## Pre-v5 historical capture notes: Lua launch

This section preserves the retired interactive Lua launch procedure and its
pre-v5 schema observations. It is diagnostic history only, not a current
capture or publication workflow. Current S3K recording begins at the native
v5 round-trip section below.

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

## Recording S3K Round-Trip Traces (Native v5)

Gumball, pachinko, slot-machine, and blue-spheres round trips are captured by
the native headless complete-run path. Its only envelope is `trace_schema: 5`,
`recorder: native-bizhawk-headless`, and `recorder_version: 3.0`. Record the
desired route as a BK2, then capture into a new scratch candidate directory:

```bash
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S3K_ROM_PATH" \
  --movie /abs/path/to/s3k-roundtrip.bk2 \
  --output "$PWD/target/s3k-roundtrip-v5-scratch" \
  --run-id s3k-roundtrip-candidate
```

Change the movie and run id for each route; never point `--output` at the
installed fixture tree. Validate the entire scratch fleet before replay or
review:

```bash
python3 tools/traces/validate_trace_v5.py \
  "$PWD/target/s3k-roundtrip-v5-scratch"
```

Then run candidate-root replay and the native ROM-backed gates, produce the
literal comparison artifacts, and obtain explicit publication approval using
[`trace-v5-publication.md`](../../docs/guide/contributing/trace-v5-publication.md).
Compression and installation are publication actions, never capture steps.

## Pre-v5 historical capture notes: S3K Bonus

The procedure below is retained to explain the predecessor bonus fixtures. It
uses the retired Lua publication path and must not be used to create or
install a current trace.

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

## Pre-v5 historical capture notes: S3K Slot Machine

The procedure below is retained to explain the predecessor slot-machine
fixtures. It is not a live recorder or publication workflow.

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

## Pre-v5 historical capture notes: S3K Blue Spheres

The procedure below is retained to explain the predecessor blue-spheres
fixtures, including its retired special-stage CSV version field. It is not a
live recorder or publication workflow.

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

Since v9.13-s2, run-mode `ss` segments also carry the hook-free subset of the standalone SS
recorder's aux event stream (frame −1 `state_snapshot`, `control_state`, `checkpoint`,
`stage_finished`, `message_state`, `results_started`) in their previously empty
`aux_state.jsonl` — the committed halfpipe fixture set was regenerated accordingly (its `.bk2`
is unchanged).

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

## Recording S2 Complete-Game Runs (s2-sonic-tails-complete-emeralds)

`s2_trace_recorder.lua` v9.13-s2 extends run mode (`OGGF_TRACE_RUN_ID`) from the
single-detour halfpipe round trip to **complete-game runs**. Design contract:
`tools/bizhawk-headless/docs/s2-run-mode-behavior.md` §11 (the Lua wins on any
disagreement).

**Title-card-reload survival:** every in-level reload funnels through
`Game_Mode $8C` (Level with the title-card bit set) — death and star-post
restarts, time overs, act 1→2 and zone→zone transitions, and the ObjB2
SCZ→WFZ→DEZ routes. v9.12 finalized the whole run at the first such reload;
v9.13 instead finalizes only the armed level segment, records a pending
transition, and re-arms on the next `$0C` gameplay frame, so a full playthrough
captures end-to-end. Two new manifest transition kinds classify the boundary by
comparing `Current_ZoneAndAct` at the `$8C` frame against the finished
segment's start zone/act: **`death_restart`** (equal — death, star-post
respawn, time over) and **`level_advance`** (differs — act/zone transitions and
ObjB2 routes). `TraceRunManifest.ENTRY_KINDS` accepts both. Genuinely terminal
modes (`$14` continue screen, `$20` ending, `$00` game over/Sega) still
finalize the run. Run-mode `ss` segments now also emit the standalone SS
recorder's hook-free aux event stream (see the halfpipe section above).

**Canonical run fixture:**
`src/test/resources/traces/s2/runs/s2-sonic-tails-complete-emeralds/` — a
259,590-row Sonic+Tails movie from title screen through the DEZ ending,
collecting all seven emeralds: 35 segments (`seg1_ehz1` … `seg28_dez1` plus
`ss` … `ss_7`) and 34 transitions (7 `starpost_special`, 7 `stage_exit`,
19 `level_advance`, 1 `death_restart` in SCZ), with the source
`sonic-2-sonic-tails-complete-emeralds.bk2` committed alongside. The installed
set comes from a native 9.13-s2 capture proven content-identical — modulo
CRLF vs LF and `recording_date` — to a validated Lua reference capture of the
same movie. The permanent differential gate
(`S2TraceDifferential native run mode capture matches canonical complete
emeralds run`, in `tools/bizhawk-headless/test.sh`) re-runs one native
`--run-id` capture and asserts per-segment sha256 for all 35 `physics.csv` /
`aux_state.jsonl` pairs, normalized metadata/manifest equality
(`recording_date` only), and the exact output layout.

**Verified native capture command (Linux):**

```bash
BIZHAWK_HOME=/abs/path/to/docs/BizHawk-2.11-linux-x64 \
tools/bizhawk-headless/run.sh \
  --mode trace \
  --rom "$S2_ROM_PATH" \
  --movie "$PWD/src/test/resources/traces/s2/runs/s2-sonic-tails-complete-emeralds/sonic-2-sonic-tails-complete-emeralds.bk2" \
  --output "$PWD/target/bizhawk-headless-trace" \
  --run-id s2-sonic-tails-complete-emeralds
```

No `--effective-movie-length` is needed for this movie: its file-derived row
count matches the capture session's movie-length signal (unlike the halfpipe
fixture — see §11.5 of the run-mode spec).

**Verified Lua reference capture command (Linux, BizHawk 2.11 via
`fetch_bizhawk_2_11_linux.sh` — full movie ≈ 6 minutes; run ONE EmuHawk at a
time, and move any existing `tools/bizhawk/trace_output/` aside first, since
the recorder always writes there):**

```bash
cd <repo-root> && DISPLAY=:0 \
BIZHAWK_HOME=$PWD/docs/BizHawk-2.11-linux-x64 \
OGGF_TRACE_RUN_ID=s2-sonic-tails-complete-emeralds \
OGGF_BK2_FRAME_COUNT=259590 \
OGGF_BK2_BASENAME=sonic-2-sonic-tails-complete-emeralds.bk2 \
tools/bizhawk/run_bizhawk_lua.sh tools/bizhawk/s2_trace_recorder.lua \
  <path-to-bk2> s2.gen
```

(The "KNOWN BLOCKER" note in the Linux Launcher section above applies to
BizHawk **2.11.1** only — the pinned 2.11 build plays BK2s through
`run_bizhawk_lua.sh` fine, verified with this capture.)

**Newline convention:** Lua on Linux writes LF; the native run publisher
writes CRLF (matching the committed run-fixture convention of
`s2-run-mode-behavior.md` §9). Content comparisons between the two must
normalize line endings; committed run fixtures are CRLF.

**Commit layout:** as with the halfpipe run — gzip `physics.csv` /
`aux_state.jsonl` per segment (`compress-traces.ps1 <dir> -Recurse
-ThresholdBytes 0` or equivalent), keep each segment's `metadata.json` and the
root `run_manifest.json` plain, and commit the `.bk2` under its truthful name
matching every `source_bk2` field.

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
for /f "usebackq delims=" %%I in (`python tools\agent-scratch new htz2-diag`) do set "OGGF_TASK_DIR=%%I"
set OGGF_OUT=%OGGF_TASK_DIR%\htz2_diag.txt
tools\bizhawk\run_bizhawk_lua.bat ^
  tools\bizhawk\diag_s2_htz2_obj30.lua ^
  src\test\resources\traces\s2\htz2\s2-lvl-select-HTZ.bk2 ^
  s2.gen
```

`tools/agent-scratch new` prints the disk-backed managed root followed by the absolute,
fresh task directory; the `for /f` assignment retains that final task-directory line. Do not
replace it with `C:\tmp`, `%TEMP%`, or `%TMP%`: those paths are only suitable for the
launcher's short-lived wrapper/config files, never durable diagnostic output.

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
