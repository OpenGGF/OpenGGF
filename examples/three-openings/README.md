# Three Openings

Three real Sonic games in one window, driven by one held pad. This engine-owned
JVM tool launches isolated workers for Green Hill Act 1, Emerald Hill Act 1 and
Angel Island Act 1. All assets and game music/SFX come from your ROMs. The host's
own menu cues are decoded from the Sonic 1 ROM through standalone production
presentation; they never advance a worker.

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
Angel Island's opening intro run independently after the start barrier.

| Action | Keyboard | Standard GLFW gamepad |
| --- | --- | --- |
| Move | Arrows | D-pad / left stick |
| Genesis A / B / C | Z / X / C (Space also C) | X / B / A |
| Native game Start | Enter | Start |
| Host pause/resume | P | Back |
| Restart all three | R | Left bumper |
| Sound focus | Tab; 1/2/3 select directly | Right bumper |
| Return to title; exit from title | Escape | Keyboard Escape |

The first connected standard pad is used. Controller disconnect or window focus
loss pauses all three; resume explicitly. Input is neutral while unfocused.
Start is native game input, separate from host pause. No per-pane steering exists.
The focused game supplies sound; unfocused games still synthesize and simulate.
A step already admitted before host pause finishes once; its complete tuple is
published, its gameplay PCM is discarded, and no further step is admitted.

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
worker ticks, native centre positions, rings, clocks and last-polled input.
`focused.wav` contains focused output (including menu feedback mixed during
play), `menu.wav` the separately synthesized UI cues; neither proves device
output without the observed speaker/loopback check in the validation record.
PNG names identify title/loading/ready/countdown and selected committed ticks.

The isolation oracle boots each game alone, the triplet, duplicate Sonic 1 worlds,
then reversed member order; it compares every native RGBA frame, pre-focus PCM
packet and reported native state. It also loads/closes/kills a sibling and checks
survivor media against an independently booted oracle:

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
