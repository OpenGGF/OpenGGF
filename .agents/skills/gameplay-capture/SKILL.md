---
name: gameplay-capture
description: Take a picture or a movie of any section of OpenGGF gameplay (any game, zone, act, position, width, donor or team) driven by a BizHawk input log, with a per-frame state CSV that says which frame to look at.
---

# Gameplay capture

Use [headless-rendering](../headless-rendering/SKILL.md) for GL backend selection,
display isolation, prerequisites and rendering lifecycle checks.

`com.openggf.tools.GameplayCaptureTool` boots a zone/act on the production path
(`HeadlessGameBoot` + `GameLoop.step()`), optionally teleports the leader, drives it
frame by frame from a BizHawk input log, and writes PNG frames, `state.csv`, and an
MP4 when `ffmpeg` is on `PATH`. Use it to show a user what happens at a location, to
check a visual change, or to reproduce a route failure with real rendering.

For choreographed dialogue, directed performances or staged promo scenes, use
[stageplay-authoring](../stageplay-authoring/SKILL.md). Its timeline JSONL describes
authored presentation; `simulate` JSONL adds native puppet motion against detached
terrain, without world objects/events. This capture's state CSV describes executed
gameplay. Use
this workflow when the actual gameplay behavior is the subject of the footage.

Use `--interactive-pacing` for title menus, gameplay observers and presentation-timed
effects such as Chaos Mode. It uses the production `GameLoop.stepPresentationFrame`
owner: each input row represents one presentation, which may admit zero or several
native ticks. P1 controller input is mapped to the capture's owned input handler;
P2 and debug inputs are rejected. The default remains one canonical native tick
per input row. Neither mode sends keyboard events to the desktop.

Title captures share Engine's native data-select launch owner: No Save and slot
confirmation retain the selected team and native save/progression behavior.
An explicitly loaded mod gets fresh registrations for that exact launch team;
the capture keeps its physical input handler and follows the new player.
Headless Return to hub remains unsupported.

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
On Linux, add `-Dopenggf.headless.gl=surfaceless-egl` to the JVM and unset `DISPLAY`/`WAYLAND_DISPLAY`
to render with no display connection at all (Mesa surfaceless EGL pbuffer); it fails closed if
surfaceless EGL is unavailable. Never point a capture at the user's shared desktop session.
On macOS GLFW must own the first thread, which `exec:java` cannot give it: run the tool with
`java -XstartOnFirstThread -cp "target/classes:<dependency classpath>" com.openggf.tools.GameplayCaptureTool ...`
(the dependency classpath from `mvn dependency:build-classpath`).
Use a task directory outside the repository for captures the user should keep.

| Flag | Default | Meaning |
| --- | --- | --- |
| `--game s1\|s2\|s3k` | `s3k` | Host game; ROM from configuration unless `--rom <path>` |
| `--mod <jar>` | none | Apply a packaged patch mod (as an enabled, trusted mod would be) before boot, to film mod gameplay |
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
| `--rings <n>` | unchanged | Declared ring count the route carried in; a boss filmed from a positioned start otherwise begins on 0 rings, where the first touch is fatal |
| `--camera-x-sub <n>` | fresh | Declared inherited `Camera_X_pos` low word; only zones that keep a camera fraction (S3K DDZ) accept it |
| `--rings <n>` | unchanged | Declared ring count the route carried in; a boss filmed from a positioned start otherwise begins on 0 rings, where the first touch is fatal |
| `--title-screen` | off | Initialize the production title before first assembly; first zone/act only, no positioned/checkpoint/clock/donor seeds |
| `--interactive-pacing` | off | Use production presentation/input pacing, including menus and gameplay observer callbacks; P1 controller input only, fps 60, no debug stage completion |
| `--audio` | off | Write `audio.wav` from one final SMPS PCM packet per outer frame; every=1, capture-from=0, matching 60-Hz presentation. Video remains silent until muxed. Music and SFX are one mix; there is no SFX-only stem |
| `--title-card` | off | Keep and draw title-card presentations instead of omitting them. Additive mod zones skip their card by default (`mods.skipModZoneTitleCards`); custom zone-name cards are unsupported |
| `--complete-special-stage` | off | Request the debug special-stage completion while a special stage runs (awards its emerald with 50 rings; no key binding is involved), so the capture continues into the results screen; results frames render as `Engine` draws them, including the S3K Super Emerald sanctuary backdrop. Special-stage frames do too: the level during the entry fade-to-white, then the stage's own provider draw |
| `--frames <n>` | settle + log length | Total frames to step |
| `--capture-from`, `--every` | `0`, `1` | First captured frame and PNG stride |
| `--stills a,b` | none | Extra `still-<frame>.png` copies at those frames |
| `--stop-on-death`, `--death-grace` | `true`, `60` | Stop after the leader dies plus grace frames |
| `--no-video`, `--scale`, `--fps` | video on, `3`, `60` | MP4 encoding (nearest-neighbour upscale) |
| `--out-dir <dir>` | required | Output root |

## Read the result

Read `state.csv` before opening any image. Columns: frame, x, y, xvel, yvel, gspeed,
air, rolling, spindash, hurt, dead, rings, mapping_frame, cam_x, cam_y,
sk_present, sk_x, sk_y, high_priority, mode, input. Title-first capture appends
host_paused and mutator_revision; direct-level route callers retain the input-last
schema. Do not extend their rows silently: authoring comparators remove only the
final input column.
Find the frame of interest (first `dead=1`, a stall where `x` stops rising, the frame
`rolling` flips) and view only `frames/<frame>.png` or the matching still. Send the
user the MP4 plus one or two stills, with the frame numbers and what they show.

For title/configuration captures, use a fresh owned preference root and maintained
input script. Configuration PCM continues through its modal play hold; window/user
pause still uses the existing audio pause owner. The capture host does not install
the interactive Engine hub-return callback; use the real JVM engine to certify
exit/relaunch. Never label a black end frame as observed hub recovery.

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
- A byte-identical before/after capture is not evidence that the change is inert: the
  affected plane may not be visible at that position. A Lava Reef Act 2 background change
  produced zero differing frames over 420 frames at two positions, then differed at two of
  eight positions swept across the act, because the foreground is opaque almost everywhere.
  Sweep several positions before concluding either way, and confirm the plane is on screen
  at the one you film.

Stageplay LIVE shots use the Stageplay offline world host and native priority
renderer. Use its simulate world events to corroborate contact, rings, hurt and
abilities; a detached Puppet capture does not demonstrate placed-world interaction.
See [stageplay-authoring](../stageplay-authoring/SKILL.md).
