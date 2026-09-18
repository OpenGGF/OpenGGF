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
