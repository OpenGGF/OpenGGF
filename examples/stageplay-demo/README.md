# OpenGGF Stageplay demo

`src/main/resources/scenes/opening.stageplay` is a fifteen-second, two-actor scene
using Emerald Hill art and music from your Sonic 2 ROM. It contains directing
data, not ROM assets. Open it in either Stageplay editor to change its keys,
captions and audio; the mod and film renderer consume that same file.

```sh
python3 examples/build_example.py stageplay-demo
```

Install the resulting `target/examples/stageplay-demo/stageplay-demo.jar` in the
JVM engine, enable it in the Mod Manager, and start Sonic 2. The first ready act
pauses while the scene plays, then resumes its unchanged native world. This
example uses detached cast members. It does not bind Sonic's native player.

To preview the scene, enter the level editor and press **F4**, choose **Open
scene**, and open the `.stageplay` file above. **Export film** renders a new MKV
with synchronized music and effects. The command-line equivalent is:

```sh
java --enable-native-access=ALL-UNNAMED \
  -cp target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar \
  com.openggf.tools.StageplayCaptureTool \
  --scene examples/stageplay-demo/src/main/resources/scenes/opening.stageplay \
  --game s2 --out /absolute/output/opening.mkv
```

On macOS add `-XstartOnFirstThread` before `-cp`. For display-free Linux rendering,
unset `DISPLAY` and `WAYLAND_DISPLAY` and add
`-Dopenggf.headless.gl=surfaceless-egl` before `-cp`. Film synthesis uses the
no-device audio backend; it does not play through desktop speakers.

Use matching engine/SDK artifacts from the same unpublished 0.7 candidate commit.
See the [Stageplay guide](../../docs/guide/creating/stageplay.md) for audio assets,
native player binding and trigger-based playback. The example cannot be combined
with another controller that already owns native frame admission.

## Location, Location, Location

The 46-second [screenplay](src/main/resources/scenes/location-remote.screenplay.json)
and [compiled scene](src/main/resources/scenes/location-remote.stageplay) tell a
short story about Tails' unreliable location remote. They demonstrate a three-role
cast, visible tail accessories, panning/zoom, earthquake effects, and wipes/fades
between locations, acts and games. Supply S1, S2 and S3&K ROMs for this scene. The
original S2-only opening remains the mod's default registration.

Open the compiled scene in either editor, or use the [agent authoring tool](../../docs/guide/creating/stageplay-authoring.md):

```sh
python3 tools/stageplay/stageplay.py compile \
  --script examples/stageplay-demo/src/main/resources/scenes/location-remote.screenplay.json \
  --out /your/production/location-remote.stageplay
python3 tools/stageplay/stageplay.py render \
  --scene /your/production/location-remote.stageplay \
  --out /your/production/location-remote.mkv \
  --s1-rom /your/existing-s1.gen --s2-rom /your/existing-s2.gen --s3k-rom /your/existing-s3k.gen
```

Build the current engine first (`--build help`), choose a new output for each take,
and keep media outside the checkout. This is authored staging, not native gameplay
or a continuous route. The stage resources represent the ROM's decoded load state.
