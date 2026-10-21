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
java -cp target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar \
  com.openggf.tools.StageplayCaptureTool \
  --scene examples/stageplay-demo/src/main/resources/scenes/opening.stageplay \
  --game s2 --out /absolute/output/opening.mkv
```

Use matching engine/SDK artifacts from the same unpublished 0.7 candidate commit.
See the [Stageplay guide](../../docs/guide/creating/stageplay.md) for audio assets,
native player binding and trigger-based playback. The example cannot be combined
with another controller that already owns native frame admission.
