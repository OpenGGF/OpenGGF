# Three Openings

Three real Sonic games in one window, driven by one held pad. This engine-owned
JVM tool launches isolated workers for Green Hill Act 1, Emerald Hill Act 1 and
Angel Island Act 1. All assets and game music/SFX come from your ROMs. The host's
own menu cues are native Sonic 1 sound effects decoded from that ROM through
standalone production presentation; they never advance a worker. The lamppost
marks the title and the all-ready barrier, rings count down and confirm a new
sound focus, the spring starts the run, the switch pauses, resumes and leaves a
run, and the wall smash marks a stopped run.

Build from the repository root with Java 21:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package
```

Run the packaged tool from that same root (use absolute ROM paths):

```sh
java -cp target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar \
  com.openggf.tools.challenge.ThreeOpeningsTool \
  --s1 '/absolute/path/Sonic The Hedgehog (W) (REV01) [!].gen' \
  --s2 '/absolute/path/Sonic The Hedgehog 2 (W) (REV01) [!].gen' \
  --s3k '/absolute/path/Sonic and Knuckles & Sonic 3 (W) [!].gen'
```

The paths are arguments, not copied assets. All three identities must match the
[repository ROM table](../../AGENTS.md#rom-and-reference-setup). Missing/wrong
ROMs produce a recoverable notice; fix the launch paths/files and retry. ROMs are
validated before any gameplay worker starts. A valid Sonic 1 ROM enables title
sound; missing it leaves the title silent until fixed.

Enter or pad A prepares all games. Enter/pad A again begins a three-count
transition, then offers the same held signal to all three. Native title cards and
Angel Island's opening intro run independently after the start barrier. Until
then each pane shows a standby card for its zone: the workers' pre-start
snapshot is not a frame the console would present, so the host never shows it.

| Action | Keyboard | Standard GLFW gamepad |
| --- | --- | --- |
| Move | Arrows | D-pad / left stick |
| Genesis A / B / C | Z / X / C (Space also C) | X / B / A |
| Native game Start | Enter | Start |
| Host pause/resume | P; Escape also pauses during play | Back |
| Restart all three | R | Left bumper |
| Sound focus | Tab; 1/2/3 select directly | Right bumper |
| Return to title (loading, ready, countdown, pause or fault screen) | Escape | B |
| Quit from the title | Escape | Keyboard Escape or close the window |

Escape during play opens the pause screen rather than discarding the run; a
second Escape returns to the title. Pad B is the game's own B button during
play and only means "back" on host screens. The first connected standard pad is
used. Controller disconnect or window focus
loss pauses all three; resume explicitly. Input is neutral while unfocused.
Start is native game input, separate from host pause. No per-pane steering exists.
The focused game supplies sound; unfocused games still synthesize and simulate.
A step already admitted before host pause finishes once; its complete tuple is
published, its gameplay PCM is discarded, and no further step is admitted.

Each pane names its game and shows whether it is heard: **SOUND ON** for the
focused game, **MUTED** with the number key that selects it for the others. A
sustained slow host shows a notice after two seconds of play; all three always
wait for the slowest game rather than skipping or catching up.

The window opens on the title once the desktop actually shows it. If the desktop
holds new windows back (a locked KDE Plasma session did), the host waits idle,
prints a one-line notice on the terminal and stays closable; it no longer spins
inside window creation. See [troubleshooting](../../docs/modding/guides/three-openings.md#supported-prototype-and-troubleshooting).

A worker fault stops all gameplay and offers whole-run restart. Restart creates
fresh processes/generations. Escape during loading cancels and closes every
acquired worker. A worker cannot poll physical devices, invoke debug/editor
shortcuts, attach a movie/trace or accelerate its loop. Its exclusive production
lease admits one LIVE iteration per common held signal; native polling determines
its own pressed edges. Worker command streams contain no physics or trace data.

## Reproducible authoring and evidence

[`common.pad`](common.pad) is maintained source for a movement/jump witness. Each
row contains a positive tick count and `NEUTRAL` or `+`-joined held Genesis
buttons. It is **not** a winning program or evidence of completing all three acts.

```text
60 NEUTRAL
90 RIGHT
12 RIGHT+C
```

Reject examples: `0 RIGHT`, `1 LEFT+RIGHT`, `1 JUMP`, a third column, unknown
buttons, empty programs, or more than 36,000 total ticks. All segments are bounded.
The host captures a sample once per admitted challenge tick. An OS stall slows
presentation; it never drops samples or adds sibling catch-up steps.

For an automated visible run with screenshots, synchronized state and PCM:

```sh
java -cp target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar \
  com.openggf.tools.challenge.ThreeOpeningsTool \
  --s1 /absolute/s1.gen --s2 /absolute/s2.gen --s3k /absolute/s3k.gen \
  --program examples/three-openings/common.pad \
  --capture-dir /absolute/outside-repository/three-openings
```

This follows the same title/loading/countdown and production play path, then
exits at the end of the supplied common program. `state.csv` names committed
worker ticks, native centre positions, rings, clocks and the polled held byte
(`-1` when that iteration did not poll).
`focused.wav` contains captured focused packets (including a paused in-flight
tuple suppressed at the device), `menu.wav` the separately synthesized UI cues.
Neither proves device
output without the observed speaker/loopback check in the validation record.
PNG names identify title/loading/ready/countdown and selected committed ticks.
`presentation.csv` records scene/generation/tick/focus and pending-step changes,
including a pause before any already-admitted tuple finishes. The maintained
1,800-tick program leaves 1,440 neutral ticks for native entry before offering
movement/jump to all games; use the tick1,536 and final images with `state.csv`.

The isolation oracle boots each game alone, a Sonic 1/2 pair, the triplet, duplicate Sonic 1 worlds,
then reversed member order; it compares every native RGBA frame, pre-focus PCM
packet and reported native state. It also loads/closes/kills a sibling and checks
survivor media against an independently booted oracle. A separately managed S1
checkpoint/replay diagnostic must also complete while survivor media stays equal:

```sh
java -cp target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar \
  com.openggf.tools.challenge.ChallengeProbe \
  /absolute/s1.gen /absolute/s2.gen /absolute/s3k.gen \
  examples/three-openings/common.pad /absolute/outside-repository/isolation
```

See the [human and agent walkthrough](../../docs/modding/guides/three-openings.md)
for the safe adaptation boundary, native polling example, support limits and
troubleshooting. The [implementation evidence](../../docs/architecture/validation/2026-10-07-multigame-prototype.md)
records exactly what was observed and what remains unproved.

For Linux X11/compatible PipeWire, `tools/challenge/capture_host.py` records the
actual host window and a private48k OpenAL output, then closes its recorders and
unloads only its owned audio module. Supply the same three ROMs and program:

```sh
python3 tools/challenge/capture_host.py \
  --classpath "$PWD/target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar" \
  --s1 /absolute/s1.gen --s2 /absolute/s2.gen --s3k /absolute/s3k.gen \
  --program examples/three-openings/common.pad \
  --output /absolute/outside-repository/three-openings-device
```

Add `--cycle-sound` with the maintained1,800-tick program to tap host sound focus
at observed committed ticks900 and1,440. This selects all three native music
streams after preparation; `presentation.csv` and the receipt record the actual
focus changes. It changes host sound selection only, retaining the same common
held-pad samples and independent worker simulation. Shorter programs that cannot
reach both observations fail this requested check.

The helper requires Python Xlib, `pactl` and ffmpeg. It leaves the desktop default
sink unchanged. `window.mkv`, `speaker.wav` and `capture.json` provide the actual
window/device evidence and exact managed-process cleanup outcome. The recorder
checks the exact host PID/title, viewable mapping and positive geometry immediately
before capture, then counts actual encoded frames. It focuses only that window.

Two optional flags apply only to spawned diagnostic processes: `--disable-vsync`
sets driver environment variables while retaining ordered 60 Hz host admission;
`--keyutils-preload /absolute/existing/libkeyutils.so.1` supplies the existing
dependency on desktops where OpenAL reports an unresolved `keyctl` symbol. Neither
changes global display/audio settings. By default the helper uses the normal mapped host window. If that native mapping
stalls, `--unmanaged-window` is an explicit diagnostic fallback: it maps only the
exact owned PID/title surface without window-manager decorations. Its receipt
labels that changed presentation setup; it does not certify default-WM startup.
The validation record preserves both normal successes and failed attempts.

On the same Linux desktop, exercise loading cancellation, pause with a deliberately
stalled owned worker, audio focus, restart/generation, fault/retry, Escape from
play to pause to title, missing ROM and repeated exit through actual UI keys:

```sh
python3 tools/challenge/check_host_lifecycle.py \
  --classpath "$PWD/target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar" \
  --s1 /absolute/s1.gen --s2 /absolute/s2.gen --s3k /absolute/s3k.gen \
  --output /absolute/outside-repository/three-openings-lifecycle
```

The probe sends native UI keys only to its own host window and signals only exact
worker processes acquired by that host. It resumes stopped workers during cleanup,
records `lifecycle.json`, synchronized timelines/screenshots/video and device PCM,
and unloads its private audio module. This diagnostic does not certify any route.

When the window does not appear, separate the desktop from the host before
changing anything:

```sh
python3 tools/challenge/check_window_startup.py \
  --classpath "$PWD/target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar" \
  --s1 /absolute/s1.gen --s2 /absolute/s2.gen --s3k /absolute/s3k.gen \
  --output /absolute/outside-repository/three-openings-startup
```

It first maps a plain Xlib control window. An unmapped control means the desktop
session is withholding new windows. It then launches the host on its title,
records the busiest host thread's CPU share, the held scenes and the waiting
notice, and closes the window through `WM_DELETE_WINDOW`. `--map-after` adds the
explicit override_redirect diagnostic map; it is not normal window-manager
evidence. Title cues go to a private null sink and gameplay never starts.

Run the process-local S1 checkpoint diagnostic independently in a fresh JVM:

```sh
java -cp target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar \
  com.openggf.tools.challenge.WorkerReplayDiagnostic \
  --game s1 --rom /absolute/s1.gen \
  --output /absolute/outside-repository/new-checkpoint-directory
```

It captures its own normal registry/audio state after native control release,
then compares36 forward/replayed GPU and PCM packets. A mismatch exits1 and
names the first owning state difference in `result.txt`; `state.csv`, PNGs and
48 kHz stereo raw PCM preserve the evidence. This tool exposes no snapshot input
or linked host rewind. Other diagnostic game cells require separate evidence.
