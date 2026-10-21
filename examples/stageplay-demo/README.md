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

The 51-second [screenplay](src/main/resources/scenes/location-remote.screenplay.json)
and [compiled scene](src/main/resources/scenes/location-remote.stageplay) tell a
short story about Tails' unreliable location remote. They demonstrate a three-role
cast in Puppet mode, visible tail accessories, native jumps and landings, stable
pans, earthquake effects, and wipes/fades
between locations, acts and games. Supply S1, S2 and S3&K ROMs for this scene. The
original S2-only opening remains the mod's default registration.

The location scene includes reusable per-speaker typing profiles and original
nine-slice panel decorations under `scenes/decor/`. Their colours, reveal cadence,
punctuation pauses and image references are ordinary typed scene data; mods can
replace or reuse them without adding Java callbacks. Names/pointers identify the
speaker. All camera zooms are held at one; the film uses pans and cuts for framing.

Open the compiled scene in either editor, or use the [agent authoring tool](../../docs/guide/creating/stageplay-authoring.md):

```sh
cp -R examples/stageplay-demo/src/main/resources/scenes /your/production
python3 tools/stageplay/stageplay.py compile \
  --script /your/production/location-remote.screenplay.json \
  --out /your/production/new-take.stageplay
python3 tools/stageplay/stageplay.py simulate \
  --scene /your/production/new-take.stageplay \
  --out /your/production/state.jsonl \
  --s1-rom /your/existing-s1.gen --s2-rom /your/existing-s2.gen --s3k-rom /your/existing-s3k.gen
python3 tools/stageplay/stageplay.py render \
  --scene /your/production/new-take.stageplay \
  --out /your/production/location-remote.mkv \
  --s1-rom /your/existing-s1.gen --s2-rom /your/existing-s2.gen --s3k-rom /your/existing-s3k.gen
```

Build the current engine first (`--build help`), choose a new output for each take,
and keep media outside the checkout. Screenplay version 2 compiles to Stageplay
version 4. Inspect the simulated native positions, velocities and goal statuses
before rendering: the authored timeline alone cannot resolve Puppet movement.

This performance uses native player physics on detached ROM terrain. Placed world
objects, moving platforms, rings, water/zone events and automatic obstacle navigation
do not run in this example. Each shot starts a new performance, so the scene changes
are authored cuts rather than native gameplay transitions. Poser mode remains
available for manual positioning, animation and mapping frames.
