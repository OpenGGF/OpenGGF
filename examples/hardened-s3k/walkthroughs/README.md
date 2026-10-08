# Reproduce the local walkthroughs

`safe.script` and `failure.script` are maintained controller programs for Post Two
Ambush. Their BK2 containers contain controller input only. They do not contain
ROM assets or reference physics and never supply gameplay state.

Build the example from the checkout root, then compile each input program through
the production author and loader:

```sh
python3 examples/hardened-s3k/build.py
OPENGGF_CAPTURE_CP="target/classes:$(cat target/hardened-s3k/engine-classpath.txt)"
java -cp "$OPENGGF_CAPTURE_CP" com.openggf.tools.InputLogAuthorTool \
  --script examples/hardened-s3k/walkthroughs/safe.script \
  --out examples/hardened-s3k/walkthroughs/safe.bk2 --game s3k
java -cp "$OPENGGF_CAPTURE_CP" com.openggf.tools.InputLogAuthorTool \
  --script examples/hardened-s3k/walkthroughs/failure.script \
  --out examples/hardened-s3k/walkthroughs/failure.bk2 --game s3k
java -cp "$OPENGGF_CAPTURE_CP" com.openggf.tools.InputLogAuthorTool \
  --script examples/hardened-s3k/walkthroughs/missing-rom.script \
  --out examples/hardened-s3k/walkthroughs/missing-rom.bk2 --game s3k
```

The author re-parses each movie and verifies its held-button round trip. This is
a local positioned encounter walkthrough, not a cold full-act route or native
parity trace. The supported profile is solo Sonic, donor off, 320×224, no-save.

Set `OPENGGF_S3K_ROM` to the existing locked-on ROM and `OPENGGF_AMBUSH_CAPTURE` to
a **new directory outside the repository**. Do not overwrite earlier evidence:

```sh
java -cp "$OPENGGF_CAPTURE_CP" com.openggf.tools.ModWalkthroughCaptureTool \
  "$OPENGGF_S3K_ROM" target/hardened-s3k/hardened-s3k.jar \
  s3k MHZ 1 examples/hardened-s3k/walkthroughs/safe.bk2 \
  "$OPENGGF_AMBUSH_CAPTURE" --title
```

Run the failure movie independently with a different new output directory. Each
capture contains real GPU frames, synchronized `state.csv`, stereo PCM in
`audio.wav`, and a small capture summary. Select screenshots by the captured
controller/encounter state and physical checkpoint index, rather than assuming a
fixed frame number proves a milestone.

For a shareable movie, confirm `fps=60` in `capture.txt` for this supported ROM
profile, then encode the observed frames and PCM:

```sh
ffmpeg -framerate 60 -i "$OPENGGF_AMBUSH_CAPTURE/frames/%05d.png" \
  -i "$OPENGGF_AMBUSH_CAPTURE/audio.wav" \
  -vf 'scale=640:448:flags=neighbor' -c:v libx264 -crf 18 \
  -pix_fmt yuv420p -c:a aac -shortest "$OPENGGF_AMBUSH_CAPTURE/walkthrough.mp4"
```

Decode the whole movie and check its frame count before reporting it as footage.

The safe program visits the lesson, touches the real second post, takes the single
safe ring, waits for committed aim, retreats from both volleys, reaches the exit,
retries at the physical post, returns to the title, and begins a separate fresh
attempt. The failure program jumps over the recovery ring, reaches committed aim
with zero rings, receives an actual harmful projectile, retries at the real post,
and returns to the title. Movement times are authored input, not engine gates.

Check the first animated title frame and the first released PLAY frame as well as
tell, committed aim, both volleys, recovery, clear/failure, retry and title return.
Verify native checkpoint contact and respawn in the CSV, then inspect PCM in those
event windows. A scheduled sound event alone does not prove output. This GPU/PCM
tool does not observe a visible desktop window, hardware controller or speakers;
the implementation artifact records separate native-window/device evidence and
its exact caller/environment limits.

The dated blueprint and affected MHZ1 validation matrix hold the observed frame
map, source identity, commands, tests and remaining route obligations. Those
records must be refreshed when the pattern or entry timing changes.

`missing-rom.script` / `missing-rom.bk2` exercise the normal host's recovery UI:
dismiss the initial error, navigate the preserved master selection, request a
second unavailable-ROM launch and dismiss again. Use an unavailable path only in
an isolated launch configuration; do not create a fake ROM or change stock files.
This movie belongs to native host/window diagnostics. `ModWalkthroughCaptureTool`
requires a valid ROM and does not render the missing-ROM host screen. The dated
implementation evidence records the exact native diagnostic caller, owned-window
recipe and its limits separately from the GPU/PCM tool.

`native-safe.script/.bk2` is the native-keyboard variant with an extra neutral
entry margin. It round-trips1376 held-input rows and completes the same post,
volleys, clear, retry and fresh-launch flow through genuine X11 key edges. Native
window initialization may admit presentation at a different pace from the logical
GPU tool; starting movement before ENTRY releases can miss the tell. This input
margin is not an engine frame gate. The dated artifact records the qualified
owned-window diagnostic recipe and its actual backend/environment limitations.
