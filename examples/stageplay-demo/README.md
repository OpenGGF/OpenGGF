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
cast, visible tail accessories, active choreography, stable pans, earthquake effects, and wipes/fades
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
python3 tools/stageplay/stageplay.py render \
  --scene /your/production/new-take.stageplay \
  --out /your/production/location-remote.mkv \
  --s1-rom /your/existing-s1.gen --s2-rom /your/existing-s2.gen --s3k-rom /your/existing-s3k.gen
```

Build the current engine first (`--build help`), choose a new output for each take,
and keep media outside the checkout. This is authored staging, not native gameplay
or a continuous route. The stage resources represent the ROM's decoded load state.

## Live-world demonstrations

Screenplay 3 / portable scene 5 adds four ROM-backed sources beside the original
terrain-only location remote:

| Source | Length | Native obligations |
| --- | --- | --- |
| `live-platform-relay` | 8 s | Two performers board, ride and transfer DEZ2 placements 179 → 166 → 179. |
| `live-air-traffic` | 9 s | S3K Tails takes off, flies, hovers, releases and lands; Knuckles jumps, glides, releases and lands. |
| `live-ring-run` | 4 s | Three placed rings disappear once; the placed DEZ1 hazard causes hurt, knockback, native scattered rings and recovery. Camera hands off to Tails. |
| `one-more-take` | 60 s | Five-shot rehearsal: relay, ring-spill mishap, airborne regrouping, retry, and all-three closing run. |

All use `worldPolicy:LIVE`, physical performers and zoom one. Their controllers
publish independent native input. Objects, rings, collision, clocks and RNG belong
to one production world per shot. Posers and old files retain their original
presentation/terrain-only behavior. The authored routes make explicit jump and
wait decisions; no universal pathfinder is claimed.

From this engine worktree, use Java 25, Lua 5.4, FFmpeg, and the existing supplied
locked-on ROM. Build once after source changes, then use a fresh external output
directory for each take (the tools never overwrite results):

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export LUA_BIN=/usr/bin/lua5.4
STAGEPLAY_ROM='/absolute/path/to/your/existing/Sonic and Knuckles & Sonic 3 (W) [!].gen'
STAGEPLAY_OUT='/absolute/external/captures/stageplay-delivery/revision-5'
python3 tools/stageplay/stageplay.py --build help
python3 tools/stageplay/stageplay.py level --game s3k --rom "$STAGEPLAY_ROM" --zone 11 --act 1
python3 tools/stageplay/stageplay.py catalog --game s3k --rom "$STAGEPLAY_ROM" --character tails
python3 tools/stageplay/stageplay.py simulate \
  --scene examples/stageplay-demo/src/main/resources/scenes/live-platform-relay.stageplay \
  --out "$STAGEPLAY_OUT/relay-new-take.jsonl" --s3k-rom "$STAGEPLAY_ROM"
python3 tools/stageplay/stageplay.py render \
  --scene examples/stageplay-demo/src/main/resources/scenes/one-more-take.stageplay \
  --out "$STAGEPLAY_OUT/one-more-take-new-take.mkv" --s3k-rom "$STAGEPLAY_ROM" --scale 2
```

The launcher selects surfaceless EGL and removes desktop display variables on
Linux. Rendering uses offline synthesis and never opens a speaker device. Source
screenplays compile with `compile --script NAME.screenplay.json --out NEW.stageplay`.
Keep canonical source scenes in Git; movies, state JSONL and review images belong
in the external task directory.

Inspect actual `sample.world` data, not only actor poses. Placement IDs identify
support; `rings` identifies original ring placements and sparkle state; actor ring
values are the native shared total, so do not add them together. Native lost rings
appear as `LostRingObjectInstance` entries. Events record hurt/recovery, ring
deltas, boarding/leaving, abilities and bounded-wait outcomes. The ROM-backed
`TestStageplayLiveDemosRom` executes these source routes and compares whole-world
seek/replay through transfers, pickup/spill, airborne abilities and shot changes.

Both editors consume the shared scene schema and preview owner. Live in-engine
preview runs a persistent offscreen worker, retains the parent world, accepts
unkeyed camera drafts without advancing a repeated frame, and admits synthesized
native SFX only during forward playback. The worker currently requires configured
original whole-image ROM paths; it does not materialize composite ROMs. Keep the
ensemble in the native camera's activation window; an authored offset does not
create another activation region. Separated casts fail with an actionable route
message. Author a closer route or a new shot.
