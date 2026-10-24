# Native reference capture

[`capture_native_references.py`](capture_native_references.py) runs a supplied
Lua exporter in Linux BizHawk 2.11/GPGX with an isolated configuration. It works
with explicit ROM/BK2 inputs and a zone-owned plan; it supplies no zone selection,
checkpoint offsets or gameplay setup. Canonical trace recording remains in
the [TraceChaser capture workflow](../../.agents/skills/bizhawk-headless-trace/SKILL.md).

## Run a diagnostic

Use absolute paths. `ROM_SHA1` is the independently verified expected ROM hash
(see the root ROM identity table); `TASK_DIR` is an external task directory.
`OUTPUT` must not exist. Install Mono, `monodis`, and official BizHawk 2.11;
framebuffer content checks additionally require Pillow.

```bash
python3 tools/bizhawk/capture_native_references.py \
  --bizhawk-home "$BIZHAWK_HOME" \
  --rom "$ROM" --rom-sha1 "$ROM_SHA1" --movie "$BK2" \
  --exporter "$TASK_DIR/exporter.lua" --plan "$TASK_DIR/plan.lua" \
  --output "$OUTPUT" --require-output observations.csv --timeout 180
```

The plan is a Lua file returning the table the exporter needs, for example
`return {zone=0x0800}`. The host copies and hashes it without adding fields.
`--fixture-state "$STATE"` optionally identifies a native save state. The exporter
owns its loading/setup, input sequence, observations and `client.exit()` call.
A standalone save made after movie playback ended must be loaded **after**
`movie.stop()`; check the load result and actual zone before taking observations.
A loaded state can still contain an intro controller lock. Observe that state
instead of treating a running frame counter as evidence of admitted input.

The Lua environment is:

| Variable | Value |
| --- | --- |
| `OGGF_NATIVE_PLAN` | Copied plan path |
| `OGGF_NATIVE_OUTPUT` | Output directory |
| `OGGF_NATIVE_ROM_SHA1` | Verified ROM hash |
| `OGGF_NATIVE_BK2_SHA256` | Movie hash |
| `OGGF_NATIVE_FIXTURE_STATE` | Optional native save path; absent when not supplied |
| `OGGF_NATIVE_HOST_RECEIPT` | Host result path, written when the process ends |
| `OGGF_NATIVE_FRAMEBUFFER_PROBE` | Shared Python command for `--probe-framebuffer PATH` |
| `OGGF_NATIVE_PYTHON` | Python executable for that command |

`host.json` records source, plan, host and emulator hashes, process status and
failures. A timeout, nonzero emulator exit, logged Lua exception, or missing/empty
`--require-output` file makes the host exit nonzero. Repeat `--require-output`
for each required artifact. A file's presence does not validate its contents:
inspect row counts, state, screenshots and the intended observation boundary.
`execution_status: completed` is execution evidence only; acceptance remains
`pending-independent-pixel-and-state-review`.

`--probe-framebuffer` checks horizontal pixel content across separated active
rows in 320×224 or bordered 348×240 Genesis images. Its result says nothing about
which zone, event, or gameplay state the image depicts.

## Existing FBZ commands

[`capture_fbz_visual_references.py`](capture_fbz_visual_references.py) is the
compatibility profile. Existing flags, default exporter, reviewed manifest,
start-frame offsets and boundary recipes remain there. It delegates launching
and failure handling to the common host and supplies both the new environment
names and the historical `OGGF_FBZ_*` aliases for old external exporters.
Repository FBZ Lua exporters now use `OGGF_NATIVE_*`. The old command remains
valid for [recorded FBZ validation recipes](../../docs/architecture/research/s3k-zones/fbz-validation.md#reproducible-linux-native-capture).

## Trace checkpoint pixels

[`capture_movie_checkpoints.lua`](capture_movie_checkpoints.lua) saves framebuffer PNGs
and whole VRAM/CRAM/VSRAM dumps at planned BK2 frames during movie playback. Its plan can
save a state on a first pass (`save_state_frame`, `save_state_path`); later passes supply
it with `--fixture-state` and `state_frame`. For trace row r the frame is
`bk2_frame_offset + pre_trace_osc_frames + r`.
[`compare_trace_checkpoint_pixels.py`](compare_trace_checkpoint_pixels.py) pairs those
frames with a full-run `TraceCaptureTool` MKV at scale 1, skipping lag rows (the MKV has
no frame for them), and compares 3-bit Genesis colour with optional masked rectangles.
See the SOZ plan section "Presentation follow-up: native pixel checkpoint survey".

## Focused checks

```bash
python3 tools/bizhawk/test_native_capture.py
python3 src/test/resources/bizhawk/fbz_framebuffer_probe_test.py
lua5.4 tools/bizhawk/test_fbz_boundary_fixture.lua tools/bizhawk/capture_fbz_boundary_fixture.lua
```

The ordinary JUnit suite also invokes the shared host tests through
`TestNativeReferenceCaptureTool`; `TestFbzVisualExporterGuard` exercises the
existing Lua exporter and framebuffer guards.


## Native headless rendered observations

The optional pinned TraceChaser harness also exposes
[`IGpgxVideoCaptureHost`](../tracechaser/bizhawk-headless/src/Core/IGpgxHost.cs)
for owned headless diagnostics. Its [rendered-frame contract](../tracechaser/bizhawk-headless/README.md#diagnostic-rendered-frames)
uses `AdvanceWithVideo()` followed by `CapturePublishedFrame()` to copy actual
ARGB pixels and published VRAM/CRAM/VSRAM, with dimensions, frame/lag identity
and domain endianness. Ordinary `Advance()` stays video-disabled. Ordinary,
audio, failed advancement and same-frame state loading invalidate the previous
rendered generation; stale pixels cannot be relabeled as current.

This is an API for an explicit scene/input observer, not a new screenshot CLI
or canonical fixture publisher. Supply and verify original ROM/movie/runtime
inputs, declare setup writes before observation, preserve original pixels and
CPU/prepared/published identities, and keep artifacts in an external task
directory. Frame-end VDP memory does not prove every earlier scanline's data.
Use normal shared admission and the intended source/classes build lease for
compiler helpers, including external outputs; retain it through owned compiler
children and release it before queued tests. Engine captures continue through
GameplayCaptureTool. The Lua exporter host above remains available for its
explicit isolated diagnostic workflow.

### S2 fixed-tail pressure diagnostic

[`run_s2_fixed_tail_pressure.py`](run_s2_fixed_tail_pressure.py) builds the pinned
TraceChaser host and [`S2FixedTailPressureProbe.cs`](S2FixedTailPressureProbe.cs)
in an owned external directory. It uses the original S2 REV01 ROM and CPZ2 BK2,
explicit-installation preflight, build leases and ordinary shared admission.
It never rebuilds the optional checkout or uses an unverified existing harness
binary. Supply an absent external task root; later `run` reuses only that root's
matching ownership marker and prepared source/binary fingerprints. Preparation
also binds the canonical installation path, required preflight files, every file
under its `dll/` tree and potentially shadowing staged runtime dependencies.
`run` rejects changed/different dependencies and repeats the unmodified strict
installation preflight under build leases **before** native admission. It checks
the binding again after queue waiting and native execution. Older preparation
records without this binding require preparation in a new owned directory;
their existing observations remain evidence, without being treated as a launch
authorization for changed inputs.

```bash
python3 -B tools/bizhawk/run_s2_fixed_tail_pressure.py --phase prepare \
  --task-root "$TASK_ROOT" --bizhawk-home "$BIZHAWK_HOME" \
  --rom "$S2_ROM" --movie "$MAIN_CHECKOUT/src/test/resources/traces/s2/cpz2/s2-lvl-select-CPZ.bk2"
python3 -B tools/bizhawk/run_s2_fixed_tail_pressure.py --phase run \
  --task-root "$TASK_ROOT" --bizhawk-home "$BIZHAWK_HOME" \
  --rom "$S2_ROM" --movie "$MAIN_CHECKOUT/src/test/resources/traces/s2/cpz2/s2-lvl-select-CPZ.bk2"
```

All paths must be absolute. The six cases are `live-last`, `empty-last`,
`reinstall-last`, `live-two`, `empty-two` and `reinstall-two`; repeated `--case`
selects an explicit subset. Each fresh core replays original inputs/sync settings
for at most 11,000 ingress frames, then observes 360 frames after setup. There
is a 180-second process timeout. No display or sound device is opened.
During preparation `MONO_PATH` contains only the existing runtime DLL directory;
the built host directory is added for `run`. This avoids Mono warnings from an
absent build directory interfering with the exact compiler-version check.

This Linux launcher starts each command in its own session/process group. It
uses the existing TERM/KILL cleanup helper and a scoped child subreaper to reap
adopted compiler descendants in that group, including on timeout, cancellation
or a parent exiting before its helpers. Cleanup completes before build/admission
leases release. It never sweeps other process groups or queue state. Focused
harmless-process/runtime-selection controls run with:

```bash
python3 -B tools/bizhawk/test_s2_fixed_tail_pressure.py -v
```

`setup-manifest.json` declares the constructed parent SSTs, original positions,
synthetic unused remember entries, optional fixed-tail clear and separately
declared one-time Sidekick reinitialization trigger. Setup runs after Sidekick
dispatch, before the final dynamic slot's ID read. Every byte write records its
old/new value; all subsequent writes come from native execution. These cases
skip placement-loader admission and natural death/respawn and do not certify
their route. The separate DPLC cache is never initialized by the setup.

ROM-byte-checked execute hooks record allocator return, failure-tail writes,
ID-zero/live fixed-slot walks, actual Obj05 copies, DPLC decisions and DMA queue
effects, native installation, physical pair deletion and remember-bit release.
Complete parent/target bytes and CPU/caller context accompany observations.
Each case writes `observations.jsonl` and `summary.json`; the launcher writes
`provenance.json`, `host.json` and bounded launch logs under its external run
directory. Inspect exact events/counts and unmet assertions. `completed` requires
the asserted paths and complete window; missing installation/retirement is
`incomplete`, not evidence that the behavior is absent. Output is reference
research only, with no canonical fixture publication or engine hydration.
