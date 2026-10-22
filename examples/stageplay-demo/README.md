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

## Your first scene: Wait for me

Start with the [26-second screenplay](src/main/resources/scenes/wait-for-me.screenplay.json)
or [portable scene](src/main/resources/scenes/wait-for-me.stageplay). Sonic races
onto the other lift, realizes he has left Tails behind, returns, and counts them
into a crossing together. Every move changes the situation; the goal is a shared
arrival, rather than a collection of unrelated abilities.

Supply your S3&K ROM. In the in-engine level editor, press **F4**, choose **Open
scene**, and select `wait-for-me.stageplay`; the same file opens in OpenGGF Editor's
Stageplay workspace. The original S2 opening remains the mod's registered scene.
This tutorial sample is opened explicitly.

1. **Set and cast:** Death Egg act 2, `LIVE`, Sonic and Tails in Puppet mode. The
   moving lifts come from the ROM's level data. Initial floor snapping is disabled
   for their short drop onto the starting lift; native contact controls the ride.
2. **Movement:** Sonic's first `MOVE_TO` starts at frame 240, aims at placement
   166 with offset X −16 / Y −35, and requests urgency 0.65. `JUMP` at frame 241
   holds the button for 20 frames. These are native inputs, not a position curve.
3. **Wait and react:** `WAIT: ON_OBJECT` gates `STOP` until landing, with a finite
   180-frame timeout. Sonic returns at frame 660; the pair set off at 1140/1145.
   Inspect the result: a target is an attempt, and there is no automatic pathfinder.
4. **Camera:** follow both actors, smoothing 0.2 and zoom 1. Keep their separation
   visible so the mistake and return read without an explanatory cut.
5. **Dialogue:** named speakers, pointers, distinct blue/gold skins, and a shared
   two-frame text reveal. Leave time to read and let the response motivate an action.
6. **Preview and save:** watch the entire 26 seconds, including platform contact,
   camera travel and the last reply. Save a new take. Try changing one line or the
   timing of Sonic's return, then preview again before exporting.

The [authoring reference](../../docs/guide/creating/stageplay-authoring.md) provides
compile, simulate and render commands. Keep screenplay JSON as the source for
scripted revisions; editor edits save the portable scene, not a screenplay round-trip.
The [tutorial production](https://github.com/OpenGGF/OpenGGF-Media/tree/main/projects/stageplay/sources/media/revision-7)
contains the conceptual walkthrough, full short, exact edit and rebuild instructions.
Its explanatory cards and track diagram are editorial annotations, not editor UI.

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
and keep media outside the checkout. The bundled version-two screenplay has a
legacy version-four compiled scene; the current compiler writes version five
with terrain-only behavior for this source. Inspect the simulated native positions, velocities and goal statuses
before rendering: the authored timeline alone cannot resolve Puppet movement.

This performance uses native player physics on detached ROM terrain. Placed world
objects, moving platforms, rings, water/zone events and automatic obstacle navigation
do not run in this example. Each shot starts a new performance, so the scene changes
are authored cuts rather than native gameplay transitions. Poser mode remains
available for manual positioning, animation and mapping frames.

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
wait decisions; no universal pathfinder is claimed. Each shot explicitly cues
its matching ROM track at local frame zero: DEZ1 22, DEZ2 23 and AIZ1 1, verified
by `catalog` against the supplied locked-on ROM. The parent preview and exporter
consume the same music cues alongside native SFX. LIVE films hide the gameplay
HUD and reveal terrain immediately; authored transitions own their opening reveal.

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
