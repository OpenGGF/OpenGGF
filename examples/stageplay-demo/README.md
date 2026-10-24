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

Start with the [screenplay](src/main/resources/scenes/wait-for-me.screenplay.json)
or [portable scene](src/main/resources/scenes/wait-for-me.stageplay): a 26-second
story followed by eight seconds of continued native platform riding for an end card.
Sonic races onto the other lift, hears Tails' objection, returns, and counts them
into a crossing together. Tails points out that he could fly; Sonic chooses to cross
with him. Companionship gives the shared arrival its purpose.

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
   180-frame timeout. Tails looks up at frame 390; Sonic crouches at 535 for 45
   frames before returning at 660. The pair set off at 1140/1145 and board the
   other lift at 1199/1204. A target is an attempt, with no automatic pathfinder.
4. **Camera:** follow both actors, smoothing 0.2, offset Y zero and zoom one.
   Keep their separation visible so the mistake and return read without a cut.
5. **Dialogue:** named speakers, pointers and blue/gold skins identify each turn.
   Top-band panels leave both supporting lifts visible. The first boast ends before
   takeoff; explicit `revealFrames` begin “Three!” at 1135 and complete it at 1140,
   immediately before Sonic leaves the platform at 1141. Check the full jump arc
   when changing placement or timing, including Tails' accessory animation.
6. **Preview and save:** watch all 34 seconds, including reactions, platform
   contact and the last reply. The story ends at frame 1560; the remaining 480
   frames continue the ride on placement 166. Save a new take before experimenting.

For a bounded experiment, change only Sonic's first `JUMP.holdFrames` from 20 to
8, keeping the approach and camera rules. In this take, both attempts land safely:
8 frames produces a 20.34375-pixel lower native apex and boards placement 166 at
frame 285 instead of 293. Compare source frames 180–449 at normal speed. That
measured result describes this route, not a general obstacle solver.

The [authoring reference](../../docs/guide/creating/stageplay-authoring.md) provides
compile, simulate and render commands. Keep screenplay JSON as the source for
scripted revisions; editor edits save the portable scene, not a screenplay round-trip.
For a film with a separate continuous music bed, make a new take with a supported
`MUSIC_STOP` cue at frame zero. It retains native SFX; keep the combined music/SFX
capture for standalone playback. Verify matching world, actors and camera before
sharing one SFX stem across dialogue/clean views. Editorial notes
in the tutorial are annotations, not editor UI.

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
and keep media outside the checkout. Every bundled screenplay is version seven;
canonical scenes are version eight and open directly in the current engine.
Inspect the simulated native positions, velocities and goal statuses before
rendering: the authored timeline alone cannot resolve Puppet movement.

This performance uses the shared native world with a frame-zero `CLEAR_OBJECTS`
command in every shot. Object roots and their graphs are cleared; rings and native
zone/events remain active under their own owners. The helper does not add automatic
obstacle navigation. Each shot starts a new performance, so the scene changes
are authored cuts rather than native gameplay transitions. Poser mode remains
available for manual positioning, animation and mapping frames.

## Live-world demonstrations

Four ROM-backed sources accompany the location-remote scene in the current formats:

| Source | Length | Native obligations |
| --- | --- | --- |
| `live-platform-relay` | 8 s | Two performers board, ride and transfer DEZ2 placements 179 → 166 → 179. |
| `live-air-traffic` | 10 s | S3K Tails takes off, flies, hovers, releases and lands; Knuckles jumps, glides, releases and lands. |
| `live-ring-run` | 4 s | Three placed rings disappear once; the placed DEZ1 hazard causes hurt, knockback, native scattered rings and recovery. Camera hands off to Tails. |
| `one-more-take` | 60 s | Five-shot rehearsal: relay, ring-spill mishap, airborne regrouping, retry, and all-three closing run. |

All use the native world and physical performers. Air traffic smoothly widens to
zoom 0.7 while Tails hovers above Knuckles, then returns to zoom one after landing.
Its RELEASE stops new flap inputs; the scene waits for native descent before the
last reply. The other demonstrations use zoom one. Their controllers
publish independent native input. Objects, rings, collision, clocks and RNG belong
to one production world per shot. Posers use explicit manual choreography;
all shots have the same framing, team, binding and activation-window capabilities. The authored routes make explicit jump and
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
native SFX only during forward playback. The worker transports exact configured logical-ROM windows and composites without
materializing joined ROMs. Separate native-sized activation windows follow each
performer and explicit object role. They share native object slots; clear
unnecessary objects or split the shot if that budget is exhausted.

## Unified native world sample

[Screenplay](unified-world.script.json) and [canonical scene](unified-world.stageplay)
show the current version-eight canonical model (screenplay seven): S1 Sonic, S2 Tails and S3K Knuckles perform
native movement together, beside a posed actor. The 384×216 film holds fractional
zoom 1.2 in both a populated world and the same world after `CLEAR_OBJECTS`.
Rings remain separately owned. Supply all three ROMs to simulate or render it
with the Stageplay CLI. All examples use `LIVE`; there are no format migrations
or legacy world modes. The original opening deliberately uses manual Poser keys;
physical examples use native Puppets.
