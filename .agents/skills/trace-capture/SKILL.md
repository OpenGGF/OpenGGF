---
name: trace-capture
description: Render an existing OpenGGF trace replay to synchronized lossless MKV video with the headless TraceCaptureTool.
---

# Trace capture

For ROM image comparisons, enable `DISPLAY_SPRITE_SCANLINE_LIMIT`
(`display.spriteScanlineLimit: true`): normal play defaults to false.
`TraceCaptureTool` enables it for its capture scope and restores the caller's
preference on completion or failure. For other engine screenshot/probe paths,
set it explicitly in the isolated capture configuration and restore it afterward.
Physics/aux comparisons do not require this presentation setting.

Use [headless-rendering](../headless-rendering/SKILL.md) for GL backend selection,
display isolation, prerequisites and rendering lifecycle checks.

`com.openggf.tools.TraceCaptureTool` replays deterministically with offscreen GL
and encodes FFV1 video plus FLAC audio in MKV. It renders existing evidence;
it does not record a new ROM trace or prove parity.

Have the relevant user-supplied ROM discoverable by `RomManager`, `ffmpeg` on
`PATH`, and an existing trace directory. Do not rename/copy a ROM to satisfy an
example. The trace catalog defaults to `src/test/resources/traces`.


An individual segment path beneath `run_manifest.json` resolves only the movie
of its validated containing run. Its metadata must match the declared run and
segment; shared `_movies` placement has the same priority as a run launch.
Traversal/absolute source aliases are rejected. This does not make whole joined
run capture available: select an individual supported segment, whose existing
bootstrap still applies. A scene publication is not established by matching
post-step physics fields alone.

```bash
python3 tools/testing/maven_queue.py exec:java "-Dexec.mainClass=com.openggf.tools.TraceCaptureTool" \
  "-Dexec.args=--trace <id|name|dir> --out-dir target/trace-videos"
```

Output is `capture-<trace-name>-<UTC timestamp>.mkv`. Use a task directory outside
the repository for durable captures; `target/trace-videos` is disposable output.

| Flag | Default | Meaning |
| --- | --- | --- |
| `--trace <id\|name\|dir>` | Required | Zero-based catalog index, directory name, or path |
| `--out-dir <dir>` | `CAPTURE_OUTPUT_DIR` | Destination directory |
| `--scale <n>` | `CAPTURE_SCALE=4` | Integer nearest-neighbor scaling of 320×224 |
| `--fps <n>` | `CAPTURE_FPS=60` | Capture/engine cadence; a region-pinned rate such as PAL 50 takes precedence |
| `--codec <name>` | `CAPTURE_CODEC=ffv1` | Video codec; changing it may change losslessness |
| `--clip frames:START:END` | None | Fast-forward from the original start, then capture the inclusive zero-based trace-row window, retaining elapsed-VBlank title/lag frames; explicit windows do not add `--tail-frames` |
| `--skip-lag-frames` | Lag frames kept | With `--clip frames:START:END` only: leave the original's lag frames out of the picture and the sound, for footage that should not repeat a picture where the original could not keep up |
| `--no-ghosts` / `--ghosts` | `TRACE_SHOW_DESYNC_GHOSTS=true` | Desync ghost visibility |
| `--no-sprite-limits` | Native sprite limits on | Render without native sprite dropout, as normal play does by default; for promotional footage, not ROM image comparison |
| `--no-music` | Music recorded | Silence music and keep the sound effects on the audio track: a sound-effects stem of the run for footage that gets its own music. The picture does not change |

A full-run capture presents no frame on trace lag rows (`lag_counter` != 0): MKV frame
n is row n only until the first lag row, then row minus the lag rows before it (measured
on `soz_completerun`, 2026-09-17).

A frame window keeps those rows, because it films every row the original displayed.
The recording is of the original on its own hardware, so on a lag row gameplay stood
still while the display went on, and the clip shows the previous picture again: 19
times in `s2/cpz2` rows 4439 to 4505, which a viewer reads as the engine stuttering.
`--skip-lag-frames` leaves them out:

- **What counts as lag** comes from the recording's counters, never from comparing
  pictures (a still scene repeats pictures too): the replay ran no gameplay frame for the
  row, `vblank_counter` advanced, `gameplay_frame_counter` held, and the hold is at most
  four rows long. In the committed traces of all three games a level loop that fell
  behind held for one row, rarely two. Every longer hold there is five rows or more,
  and is the game running outside its level loop (a title card, a fade, a load): not
  lag, so it stays in. `lag_counter` is not read: only Sonic 3&K records one.
- **The window still means trace rows** START to END. The clip is shorter by the lag
  rows inside it. The tool prints the rows it left out and the output-frame to trace-row
  mapping (`clip: output frame -> trace row: 0-138 -> 4300-4438, 139-142 -> 4440-4443,
  ...`), and the capture manifest carries the same as `clip.skipped_lag_rows` and
  `clip.frame_rows`, so a film can still cut on events.
- **Audio is supported**, with `--no-music` or without. A row left out gets no audio
  packet either, from the start of the trace and not only inside the window, so the
  track is as long as the picture and every sound effect starts on the frame that makes
  it. The music plays through without a gap; it simply does not advance over a lag
  frame, where the original's sound driver did.
- Only what is filmed changes. Every row is still driven, in the same order with the
  same input, so the replay, its comparison and the gameplay are those of a capture
  without the option.

HUD visibility uses config rather than CLI flags:
`TRACE_SHOW_GAME_HUD=true`, `TRACE_SHOW_DEBUG_HUD=false`; enabled debug panels
also obey `DebugOverlayToggle`. These visibility flags apply to active trace
sessions in both live playback and capture.

Audio uses the no-device `HeadlessSmpsAudioBackend`, fixed at 48 kHz stereo.
The capture runtime must be the sole PCM consumer: a live device backend draining
the same FIFO causes fast/out-of-sync audio. The capture pins the music and SFX
volume settings to 100, so saved player volumes do not reach the evidence. For
silence, check `AUDIO_ENABLED` and backend installation; inspect the output with
`ffmpeg -i <mkv> -af volumedetect -f null -`. Unexpected speaker output likewise
indicates that the wrong backend was installed.

For pipeline changes, follow `TraceCaptureTool` → `HeadlessGameBoot` →
`TraceReplayDriver` → `TraceCaptureSession` → `CaptureRecorder`/`FfmpegEncoder`.
`GlReadPixelsGrabber` captures RGBA; ffmpeg flips/scales it.
`DrainPcmAudioTap` drains the samples produced by each capture frame.
`TraceGhostHook` registers ghost rendering for `LevelRenderer`.

The driver deliberately continues through mismatches instead of pausing, with a
bounded capture loop. Check `target/trace-reports/` for parity evidence even when
the video finishes. For queue/dynamic-art debugging, use
`trace-replay-bug-fixing`; regenerate missing audit observations with
`bizhawk-headless-trace`. A video cannot supply missing fixture capabilities.
