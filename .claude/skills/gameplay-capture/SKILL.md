---
name: gameplay-capture
description: Take a picture or a movie of any section of OpenGGF gameplay (any game, zone, act, position, width, donor or team) driven by a BizHawk input log, with a per-frame state CSV that says which frame to look at.
---

# Gameplay capture

`com.openggf.tools.GameplayCaptureTool` boots a zone/act on the production path
(`HeadlessGameBoot` + `GameLoop.step()`), optionally teleports the leader, drives it
frame by frame from a BizHawk input log, and writes PNG frames, `state.csv`, and an
MP4 when `ffmpeg` is on `PATH`. Use it to show a user what happens at a location, to
check a visual change, or to reproduce a route failure with real rendering.

It is not parity evidence and does not read traces. For rendering an existing trace
replay use `trace-capture`; for authoring the input log use `bk2-input-authoring`.

For a multi-capture work compilation or an updated reel, use
[gameplay-highlights](../gameplay-highlights/SKILL.md) for provenance, editorial
selection, act ordering and final video verification.

## Run

Author the input first (or use a recorded `.bk2`):

```bash
python3 tools/testing/maven_queue.py exec:java "-Dexec.mainClass=com.openggf.tools.InputLogAuthorTool" \
  "-Dexec.args=--inline '60 R; 1 D+R; 40 D+R; 60 -' --out target/capture/run.txt"
python3 tools/testing/maven_queue.py exec:java "-Dexec.mainClass=com.openggf.tools.GameplayCaptureTool" \
  "-Dexec.args=--game s3k --zone fbz --act 2 --x 0x1CF0 --y 0x76C \
    --input target/capture/run.txt --out-dir target/capture/fbz2-squeeze"
```

`exec:java` does not recompile; add `compile` after editing engine code.
Use a task directory outside the repository for captures the user should keep.

| Flag | Default | Meaning |
| --- | --- | --- |
| `--game s1\|s2\|s3k` | `s3k` | Host game; ROM from configuration unless `--rom <path>` |
| `--zone <name\|n>` | required | Zone constant name (`aiz`, `fbz`, `ghz`, `ehz`) or number |
| `--act <n>` | required | One-based act |
| `--x`, `--y` | level start | Teleport the leader (ROM `x_pos`/`y_pos`, hex `0x`/`$` ok) |
| `--star-post` | off | Save a checkpoint at `--x/--y` and restart from it, instead of teleporting. Required wherever the act's `ScreenInit` runs a scripted intro that overrides `--x/--y` outright (S3K SSZ act 1) |
| `--width <px>` | `320` | 320, 352, 400, 528 or 800; height is always 224 |
| `--main`, `--sidekick` | `sonic`, `none` | Characters; `--sidekick tails` for a team |
| `--donor off\|s1\|s2` | `off` | Cross-game donation; ROM from configuration unless `--donor-rom` |
| `--input <file>` | none | `Input Log.txt` or `.bk2`; neutral input after it ends |
| `--settle <n>` | `0` | Neutral frames before the input log starts |
| `--input-start <n>` | `0` | First movie frame to play (e.g. a level start late in a complete-run `.bk2`) |
| `--emeralds <7 digits>` | unchanged | S3K `Collected_emeralds_array` setup (0 none, 1 Chaos, 2 grey Super, 3 Super), applied after boot |
| `--vint-run-count <n>` | fresh | Declared inherited `V_int_run_count` (objects gating on its low bits, e.g. DDZ turret aim) |
| `--camera-x-sub <n>` | fresh | Declared inherited `Camera_X_pos` low word; only zones that keep a camera fraction (S3K DDZ) accept it |
| `--title-card` | off | Keep and draw title-card presentations instead of omitting them |
| `--complete-special-stage` | off | Request the debug special-stage completion while a special stage runs (awards its emerald with 50 rings; no key binding is involved), so the capture continues into the results screen; results frames render as `Engine` draws them, including the S3K Super Emerald sanctuary backdrop |
| `--frames <n>` | settle + log length | Total frames to step |
| `--capture-from`, `--every` | `0`, `1` | First captured frame and PNG stride |
| `--stills a,b` | none | Extra `still-<frame>.png` copies at those frames |
| `--stop-on-death`, `--death-grace` | `true`, `60` | Stop after the leader dies plus grace frames |
| `--no-video`, `--scale`, `--fps` | video on, `3`, `60` | MP4 encoding (nearest-neighbour upscale) |
| `--out-dir <dir>` | required | Output root |

## Read the result

Read `state.csv` before opening any image. Columns: frame, x, y, xvel, yvel, gspeed,
air, rolling, spindash, hurt, dead, rings, mapping_frame, cam_x, cam_y, mode, input.
Find the frame of interest (first `dead=1`, a stall where `x` stops rising, the frame
`rolling` flips) and view only `frames/<frame>.png` or the matching still. Send the
user the MP4 plus one or two stills, with the frame numbers and what they show.

## Pitfalls

- A black frame or a frame with tiles but no player is a broken capture, not a
  gameplay fact. `TestGameplayCaptureSmoke` guards both; if it fails, do not trust
  the PNGs. The test-fixture render path (`LevelFrameTestStep` +
  `drawWithSpritePriority`) never draws the playable sprite; this tool avoids it.
- Donation: the boot resets the cross-game provider, so the session re-initialises it
  after boot and refuses to continue if it does not report active. If a donor run
  shows native behaviour (spindash with `--donor s1`), the donor ROM path is wrong.
- Donor re-registration after boot does not reapply the zone start location: an S2-donor
  Tails capture of S3K Doomsday started at (69,656) while the production fixture starts at the
  ROM's (0,$100). Do not use donor captures as start-position or route evidence.
- Teleporting with `--x/--y` skips plane switchers and level events between the act
  start and that point; priority, water and camera bounds reflect a fresh load.
- `--x/--y` is not always the leader's start. `SSZ1_ScreenInit` runs a scripted arrival that
  overrides it and `Obj_57C1E` then pins Player 1 to `Camera_Y + $65`, so a Sky Sanctuary act 1
  capture aimed at the arena floor drops through it and settles at `($100,$C4C)`. `--star-post`
  is the flag for that: it saves a checkpoint at the requested position and restarts from it,
  the way `TestS3kSszGhzArenaHeadless.bootAtCheckpoint` does. Read the tool's own arguments
  before concluding a zone's collision is broken.
- One capture per JVM is the supported shape. A second `GameplayCaptureSession` in
  the same process works only because `close()` releases the graphics singleton.
- The input log is held state per frame; a jump is one frame of `A` then release.
  Scripted timing is fixed, so anything keyed to object phases (elevators, polarity
  cycles) needs the `--settle` count tuned from `state.csv`, not guessed.
