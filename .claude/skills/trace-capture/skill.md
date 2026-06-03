---
name: trace-capture
description: Use when capturing a trace replay to a lossless synced video (demo reel) with the headless TraceCaptureTool — invocation, flags, ghost/HUD visibility, audio, and how the pipeline is wired.
---

# Trace Capture Tool

`com.openggf.tools.TraceCaptureTool` renders a recorded trace **headless** (offscreen, deterministic) and writes a **lossless, audio+video-synced MKV** — optionally including the desync ghost(s). Because the engine replays deterministically, capture is 1:1 with no dropped frames and no realtime deadline.

## Prerequisites

- The relevant **ROM** in the working directory (resolved per `RomManager.resolveRomForGame`, same lookup as the rest of the engine).
- **ffmpeg on `PATH`** (the encoder shells out to it; `FfmpegEncoder.findFfmpeg()` searches `PATH`).
- At least one trace under `TRACE_CATALOG_DIR` (default `src/test/resources/traces`).

## Invocation

```bash
mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceCaptureTool" "-Dexec.args=--trace <id|name|dir> --out-dir target/trace-videos"
```

In PowerShell, quote the `-D...` properties (as above). Add `-Dmse=off` if you want full Maven logs.

The output file is `capture-<trace-name>-<UTCyyyyMMdd'T'HHmmss'Z'>.mkv` in the output directory.

## Flags

All optional except `--trace`. Unspecified flags fall back to the `CAPTURE_*` config defaults.

| Flag | Default (config key) | Meaning |
|------|----------------------|---------|
| `--trace <id\|name\|dir>` | (required) | 0-based `TraceCatalog` index, a trace **directory name** (e.g. `cpz`, `ehz1_fullrun`), or a direct path to a trace directory. |
| `--out-dir <dir>` | `CAPTURE_OUTPUT_DIR` (`target/trace-videos`) | Output directory. |
| `--scale <n>` | `CAPTURE_SCALE` (`4`) | Integer nearest-neighbor upscale (4× → 1280×896). |
| `--fps <n>` | `CAPTURE_FPS` (`60`) | Output frame rate. |
| `--codec <name>` | `CAPTURE_CODEC` (`ffv1`) | Video codec. |
| `--no-ghosts` / `--ghosts` | `TRACE_SHOW_DESYNC_GHOSTS` (`true`) | Per-run override to hide / show the desync ghost(s). |

### Visibility (ghosts / HUD)

Three trace-framework config flags gate rendering during **both** live Trace Test Mode and capture (read via `TraceRenderVisibility` in `LevelRenderer`):

- `TRACE_SHOW_DESYNC_GHOSTS` (default `true`) — the desync ghost(s). CLI override: `--no-ghosts` / `--ghosts`.
- `TRACE_SHOW_GAME_HUD` (default `true`) — rings/score/time HUD. (No CLI flag yet — set the config key.)
- `TRACE_SHOW_DEBUG_HUD` (default `false`) — debug overlay; when on, individual `DebugOverlayToggle` panels apply. (No CLI flag yet.)

These flags only affect rendering while a trace session is active, so normal gameplay is unaffected.

## Output

- Container/codecs: **FFV1 video + FLAC audio in MKV** — bit-exact lossless.
- Video: native 320×224 upscaled by `--scale` with nearest-neighbor (ffmpeg-side `vflip,scale=...:flags=neighbor`).
- Audio: captured at the engine's **48 kHz** SMPS synthesis rate, stereo, synced 1:1 with video (no timestamps — equal frame/sample cadence).
- Files are large (lossless): a ~95 s clip is ~800 MB. `target/` is gitignored; re-generate as needed.

## Audio is headless / no device output

Headless capture installs an **offline** `LWJGLAudioBackend` (`offlineNoDevice=true`) that synthesizes the SMPS music for the recording but emits **nothing to the sound device**: `update()`/`startStream()` no-op and the OpenAL listener is muted. This also makes the deterministic capture runtime the **sole** consumer of the presentation FIFO — otherwise the backend's streaming would steal samples and speed the audio up.

> Known limitation: the offline backend still *opens* (but never feeds) an OpenAL device. On a machine with no audio device, `init()` falls back to `NullAudioBackend` → silent capture. A true no-device synthesis backend is a future item.

## How it's wired (for maintainers)

- `TraceCaptureTool` → `HeadlessGameBoot` (offscreen GL + gameplay session, no master-title/fade; installs the offline audio backend) → `TraceReplayDriver` (deterministic replay, extracted from `TraceSessionLauncher`; capture passes a **no-op desync-pause** so it records *through* mismatches to completion) → `TraceCaptureSession` (step → render → grab → submit) → `CaptureRecorder`/`EncoderSink`/`FfmpegEncoder` (`com.openggf.capture`).
- Video tap: `GlReadPixelsGrabber` (`glReadPixels`, raw RGBA, no Java-side flip). Audio tap: `DrainPcmAudioTap` → `AudioManager.drainCaptureFrame` (drains exactly the frame's produced samples; capture mode installed via `beginCaptureMode`/`endCaptureMode`).
- Desync ghosts render via `TraceGhostHook` (a shared active-renderer registry both the live launcher and the capture session register into), so `LevelRenderer` draws them for capture too.

## Troubleshooting

- **Silent audio**: usually means no synthesizing backend (default `NullAudioBackend`) — confirm `AUDIO_ENABLED=true` and that `HeadlessGameBoot` installed `LWJGLAudioBackend`. Verify with `ffmpeg -i <mkv> -af volumedetect -f null -` (silence ≈ `-91 dB`).
- **Audio too fast / out of sync**: a second consumer is draining the presentation FIFO — ensure the backend is in `offlineNoDevice` mode (its `update()`/`startStream()` must no-op).
- **Wrong sample rate**: capture must drive at `backend.outputSampleRate()` (48 kHz), not a hardcoded value.
- **`ffmpeg` not found**: install it / put it on `PATH`; the encoder throws a clear error otherwise.
- **Run never finishes / temp grows**: the capture loop is bounded (`trace.frameCount() + 600`); the driver uses a no-op desync-pause so a comparator mismatch does not freeze the run.
- **Inspect a frame**: `ffmpeg -i <mkv> -vf "select=eq(n\,1500)" -vframes 1 out.png` to confirm ghosts/HUD after a desync.
