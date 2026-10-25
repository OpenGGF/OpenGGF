# Stageplay from the terminal

`tools/stageplay/stageplay.py` runs Stageplay without opening an editor. It
compiles screenplays into scenes and back, checks and simulates them, finds
floors and routes, and renders films. People use it to script films; agents
use it to direct without menus or a desktop.

This page is the command reference and the technical detail behind it. The
[reference](stageplay-reference.md) documents the screenplay format, and the
[directing guide](stageplay-directing.md) explains the ideas. The tools target
the **0.7.prerelease development line**.

- [Set up](#set-up)
- [A typical session](#a-typical-session)
- [Commands](#commands)
- [Level placements](#level-placements)
- [Simulate](#simulate)
- [Find floors](#find-floors)
- [Route helper](#route-helper)
- [Render](#render)
- [Round trips between scene and screenplay](#round-trips-between-scene-and-screenplay)
- [Object coverage](#object-coverage)
- [How preview, simulation and export work](#how-preview-simulation-and-export-work)

## Set up

You need Java 25 (the launcher uses `$JAVA_HOME/bin/java`, or `java` on your
`PATH`, and does not check the version), Python 3, and FFmpeg for films. Build
the engine once:

```bash
python3 tools/stageplay/stageplay.py --build help
```

`--build` packages the current sources with tests skipped (through the Maven
queue); it is not a test run. Leave it out afterwards until the engine sources
change: the launcher otherwise uses the last packaged jar in `target/`, and an
older jar does not reflect newer sources. `--jar PATH` uses a jar you supply.
Launcher options go before the command.

The launcher runs the engine with `-Xmx4g`. On Linux it removes `DISPLAY` and
`WAYLAND_DISPLAY` and renders with surfaceless EGL, so it never opens a window;
on macOS it adds `-XstartOnFirstThread` and uses a hidden window. Audio is
synthesized offline and never plays through a speaker.

### Output and errors

- Each command prints one JSON object on stdout: `{"ok":true,"result":…}` or
  `{"ok":false,"error":"…"}`. Engine logs go to stderr.
- Exit code 0 means success, 2 means invalid input or failure, and 130 means
  cancelled (Ctrl-C).
- No command overwrites a file. Outputs are written to a temporary file and
  then linked into place, so a failure never leaves a partial file. The output
  folder must already exist (`render` creates it).
- Path options are resolved from your current directory: `--scene`,
  `--script`, `--out`, `--rom`, `--s1-rom`, `--s2-rom`, `--s3k-rom`,
  `--staging-dir`, `--observations` and `--queries`.
- Flags are `--name value` pairs. Unknown, repeated or missing flags are
  errors.

## A typical session

```bash
python3 tools/stageplay/stageplay.py init --out /your/films/take1.screenplay.json
python3 tools/stageplay/stageplay.py catalog --game s2 --rom /path/to/sonic2.gen --character tails --zone 0 --act 0
python3 tools/stageplay/stageplay.py ground --set s2/0/0 --queries /your/films/floors.json --s2-rom /path/to/sonic2.gen
python3 tools/stageplay/stageplay.py compile --script /your/films/take1.screenplay.json --out /your/films/take1.stageplay
python3 tools/stageplay/stageplay.py simulate --scene /your/films/take1.stageplay --out /your/films/take1.jsonl --s2-rom /path/to/sonic2.gen
python3 tools/stageplay/stageplay.py render --scene /your/films/take1.stageplay --out /your/films/take1.mkv --s2-rom /path/to/sonic2.gen --scale 2
```

1. `init` writes a starter screenplay (Tails in Emerald Hill).
2. `catalog` and `ground` find the set, its music and its floors.
3. `compile` turns the screenplay into a scene.
4. `simulate` plays it under the game's physics. Read the positions and
   statuses before you render.
5. `render` exports the film. Watch it at full speed.

Revise the screenplay and repeat with a new output name for each take. Keep
films and simulation output outside the repository.

## Commands

| Command | Purpose | ROM |
| --- | --- | --- |
| `help` | List commands, format versions and rules. | no |
| `init --out PATH` | Write a starter screenplay. | no |
| `catalog --game G --rom PATH [--character C] [--zone N --act N]` | Zones, start positions, music, character abilities and animations. | yes |
| `level --game G --rom PATH --zone N --act N` | Every placed object and ring in a level. | yes |
| `compile --script PATH --out PATH` | Screenplay to scene. | no |
| `decompile --scene PATH --out PATH` | Scene to screenplay. | no |
| `validate --scene PATH [--frame N]`, `inspect …` | Check a scene's structure and summarize it; with `--frame`, sample that frame. Same command, two names. | no |
| `timeline --scene PATH --out PATH [--every N]` | The authored timeline as JSON lines, without physics. | no |
| `simulate --scene PATH --out PATH [--every N] --s1-rom/--s2-rom/--s3k-rom` | Play the scene under the game's physics and write each frame as JSON lines. | yes |
| `ground (--set G/Z/A \| --scene PATH [--frame N]) --queries PATH` | Find floors for many columns. | yes |
| `route --scene PATH --actor ID --x X --y Y --out PATH …` | Plan a route and write the routed scene. | yes |
| `render --scene PATH --out PATH.mkv …` | Export a lossless film. | yes |
| `object-coverage --out PATH [--game all\|s1\|s2\|s3k] [--format json\|markdown]` | Inventory every object type and what Stageplay can do with it. | no |

`--game` takes `s1`, `s2` or `s3k`. Zone and act numbers start at zero.

### catalog

`catalog` checks that the ROM matches the game and reports its SHA-1, every zone
and act with its name, start position and `musicId`, the game's `music` list
(each song's `name`, `title` and native `soundId`), the character's available
actions (`intents`) and its animations and sprite frames. Sound numbers are the
game's own request IDs: Sonic 2's Emerald Hill is 130 (`$82`). With
`--zone` and `--act` it also decodes that level and reports its playable area.
`--character` defaults to `sonic`.

### validate, inspect and timeline

`validate` checks the scene's structure and limits without ROMs. It does not
check that art, sounds or levels exist; `simulate` and `render` do that.

`timeline` writes one line per frame (or every `--every N` frames) with the
time, the active set, the camera, keys, transitions, cues and `speech` (the
voice blips, with the frame each character appears). Puppets are listed as
`UNRESOLVED` there, because their positions come from physics; use `simulate`
for them.

## Level placements

```bash
python3 tools/stageplay/stageplay.py level --game s3k --rom /path/to/s3k.gen --zone 11 --act 1
```

`level` prints the ROM's SHA-1 and every object and ring placement in the
level. An object placement is identified by its `layoutIndex`, `objectId` and
`subtype`. Use these for action targets, `REMOVE_OBJECT` and taking over a
placed object. Runtime slot numbers are not stable and are never used.

## Simulate

`simulate` runs the scene in a fresh, windowless copy of the game, frame by
frame, and writes JSON lines of `{frame, seconds, sample, cues}`. It needs a
ROM flag for every game the scene uses. `--every N` only thins the output; the
simulation still runs every frame.

For each Puppet, `sample.puppets` gives its status, velocity, ground speed,
whether it is airborne, and the active action's ID. Statuses are `IDLE`,
`MOVING`, `STOPPING`, `ARRIVED` and `BLOCKED` (no progress; the action is not
cancelled). A cast member hidden for the whole shot is `INACTIVE`.

`sample.world` adds the game's frame counters, ring placements and which have
been collected, placed objects and their positions, and for each player:
rings, ring changes, hurt, the object it is standing on, ability (only one the
character has in its own game, so Sonic 1 Sonic reports no insta-shield and Sonic
2 Tails no flight), ability state and timer, and the actual animation and sprite frame. Events report boarding and
leaving objects, ring pickups and losses, hurt and recovery, ability changes
and wait outcomes (including `WAIT_TIMED_OUT`).

Read actual results before rendering. A picture of a platform, a counter or a
successful compile does not show that a character landed, rode or collected
anything. Shot boundaries place the cast afresh, which is not movement.

## Find floors

`ground` finds where an actor or object can stand in many columns at once,
in one engine run:

```bash
python3 tools/stageplay/stageplay.py ground --set s1/0/0 --queries floors.json --s1-rom /path/to/sonic1.gen
python3 tools/stageplay/stageplay.py ground --scene film.stageplay --frame 240 --queries floors.json --s1-rom /path/to/sonic1.gen
```

```json
{"maxMillis": 20000, "queries": [
  {"id": "prop", "x": 512, "top": 400, "bottom": 900,
   "object": {"halfWidth": 8, "height": 28, "footOffset": 14, "path": 0}},
  {"id": "player", "x": 600, "top": 400, "bottom": 900,
   "player": {"game": "s1", "character": "sonic", "path": 0}},
  {"id": "selected", "cast": "hero", "x": 700, "top": 400, "bottom": 900}
]}
```

Each query searches column `x` between `top` and `bottom`. Describe the
performer with `player` (game, character and collision path) or `object` (a
clearance box: half width, height above the feet, and the feet's offset below
the object's anchor). With `--scene`, `cast` names a cast member instead; an
object cast member still needs its `object` box. A returned `y` is the value to
use directly: the centre for a player, the anchor for an object.

Each answer's `outcome` is `FOUND` (with up to 16 `placements`, nearest
first, and refusals), `NONE_IN_BAND` (the whole band was searched), or
`INCOMPLETE` (stopped by a budget or cancelled; it proves nothing).
`CONTEXT_UNAVAILABLE` means the level could not be prepared, for example when no
ROM was given; the command still exits 0. `UNSUPPORTED` means the performer
cannot be described this way. `truncated` means more results existed.

The basis is `ENTRY_LAYOUT` for `--set` (the level as decoded, before any level
events) or `SCENE_FRAME` for `--scene` (the prepared scene at that frame, as the
scene was before any change you make based on the answer). Both are terrain
only: platforms and other objects are not included. Surfaces that need wall or
ceiling running, background-layer collision, reversed gravity or special loop
layers are refused with a reason.

Player answers are proposals from the game's own floor sensors; object answers
prove the clearance box fits. Apply them, then confirm the landing in
`simulate`. The reported `angle` is the game's raw angle byte.

Limits: 1–128 distinct query IDs per file (at most 256 KiB), bands of at most
1024 rows, `maxMillis` 1–60,000 (default 20,000), `maxScans` up to 10,000,000
(default 4,000,000) and `maxPreparationTicks` up to 36,000 (default 6000).
`ground` writes no file.

## Route helper

```bash
python3 tools/stageplay/stageplay.py route --scene /your/films/take1.stageplay --actor tails \
  --x 320 --y 648 --out /your/films/take1-routed.stageplay --s2-rom /path/to/sonic2.gen
python3 tools/stageplay/stageplay.py decompile --scene /your/films/take1-routed.stageplay \
  --out /your/films/take1-routed.screenplay.json
```

`route` is the editors' **Plan route**. It plans for one actor from a shot's
first frame to a destination centre, writes a new scene with the route's
actions added, and changes nothing else. Route a compiled scene, then
decompile it to keep working in the screenplay.

| Option | Default | Range |
| --- | --- | --- |
| `--frame` | 0 | A shot's first frame (objects: a visible Puppet key) |
| `--start` | `WALK` | `WALK`, `JUMP`, `SPINDASH` |
| `--travel` | `WALK` | `WALK`, `RUN` |
| `--ending` | `GENTLE_STOP` | `GENTLE_STOP`, `HARD_STOP`, `OVERSHOOT` |
| `--facing` | `INHERIT` | `INHERIT`, `LEFT`, `RIGHT` |
| `--frames` | up to 300, to the end of the shot | 1–3600 |
| `--nodes` | 1024 | 1–4096 |
| `--ticks` | 120,000 | 1–500,000 |
| `--millis` | 60,000 | 1–120,000 |
| `--urgency` | 1 | above 0, at most 1 |

The result reports a status. A plan is accepted only after a fresh playback of
the new actions, from the start of the shot, reaches the destination on the
ground and settles without being hurt, dying or timing out a wait; that is
`VERIFIED`. Failures are `UNSUPPORTED`, `SEARCH_EXHAUSTED`, `BUDGET_EXCEEDED`,
`CANCELLED`, `VERIFICATION_FAILED` and `STALE_INPUT` (the scene, ROM or assets
changed during planning). None of them proves the destination unreachable. A
failure exits 2 and still returns the result with its diagnostics.

Player searches are limited to 1536 pixels across and 768 up or down from the
start, and to the given budgets. They search real physics over the level's
terrain with the same steering as `MOVE_TO`, try direct steering first, then
64- and 128-pixel run-ups. Jumps that the terrain needs are allowed with a
penalty. A walk that fails is tried again at a run on the same terrain, if
budget remains; if running reaches the destination, the message adds `terrain
near x=…: needs Run`. A passed check describes this exact input; later edits,
moving objects, water and level events can change the outcome.

### Motion details

Walk caps speed at `$180` (1.5 pixels per frame), a quarter of normal top
speed. Staying below `$600` would still look fast: `SAnim_WalkRun`
(s2.asm:38449-38487) changes to the run cycle only at `$600`, which is also the
normal `Sonic_top_speed` (s2.asm:36187). The game's acceleration, friction,
slopes and live top speed stay in charge. Gentle Stop predicts coasting
distance from the current friction and releases early; Hard Stop countersteers
with the live deceleration; Overshoot passes the point by 24 pixels and comes
back. Left or Right adds one directional tap after the stop.

A Spindash start taps toward the destination, coasts to a standstill (braking
uphill if the slope would win), holds down until the game reports a crouch,
then presses jump once and releases down inside a fixed 24-frame window. The
game's charge decay and launch tables decide the speed (`Sonic_CheckSpindash` /
`Sonic_UpdateSpindash`, s2.asm:37555-37688); shipped `fixBugs=0` behaviour stays
active. `Sonic_CheckSpindash` reads the crouch before movement, and
`Sonic_SlopeResist` skips zero inertia (s2.asm:37709-37732), so a fixed wait
alone is unsafe on a downhill start. A roll then coasts with roll drag, which
differs from standing friction (`Sonic_RollSpeed`, s2.asm:37009-37013), so allow
a longer window for a Spindash start with Gentle Stop. Spindash requires Run,
and is offered only to characters whose game has it. A non-Overshoot ending
that passes the point fails the check with advice to use a Walk start or a
farther destination.

### Object routes

Object Puppets need a clearance box: `--half-width N --height N
--foot-offset N`, with optional `--collision-path 0|1` and `--speed N` (pixels
per frame, above 0, at most 16). An object route starts at a shot's first frame
or at an existing visible Puppet key in the same shot, so an object can start
**Free** for a moment before it is routed. The result, `VISUAL_TERRAIN`, is a
series of object `MOVE_TO` and `STOP` commands along the floor. It follows
slopes and steps of up to 8 pixels, checked against the terrain on every frame;
gaps, walls, larger steps, low ceilings, background-layer collision and moving
solids fail. The object's own behaviour, contact and effects stay off: it is a
visual route, not a controller.

## Render

```bash
python3 tools/stageplay/stageplay.py render --scene SCENE.stageplay --out FILM.mkv \
  --s1-rom PATH --s2-rom PATH --s3k-rom PATH --scale 4
```

| Option | Default | Meaning |
| --- | --- | --- |
| `--out` | (required) | A new `.mkv` file; missing folders are created. |
| `--s1-rom`, `--s2-rom`, `--s3k-rom` | | ROMs for the games the scene uses. |
| `--rom`, `--game` | the scene's first game | The ROM and game to start the engine with. Without `--rom`, the configured ROMs are used. |
| `--scale` | 1 | Whole-number upscale, 1–8, with sharp pixels. |
| `--ffmpeg` | `ffmpeg` | FFmpeg executable. A bare name is looked up on `PATH`; a path with a directory is resolved from your current directory. |
| `--staging-dir` | a new folder beside the output | An existing empty folder for temporary files. |

Films are FFV1 video and FLAC audio at 60 frames per second, at the scene's
size times `--scale`. Every resource is checked before encoding starts, the
audio and video clocks are checked on every frame, and a failed or cancelled
export publishes nothing. Upscaling cannot remove shimmer already present in a
fractional zoom.

The editors' **Export film** runs the same exporter in a separate process. To
run it directly:

```bash
java --enable-native-access=ALL-UNNAMED -cp target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar \
  com.openggf.tools.StageplayCaptureTool --scene SCENE.stageplay --out FILM.mkv --s2-rom PATH
```

On macOS add `-XstartOnFirstThread` before `-cp`. On Linux, unset `DISPLAY` and
`WAYLAND_DISPLAY` and add `-Dopenggf.headless.gl=surfaceless-egl`; see
[headless rendering](../contributing/headless-testing.md).

## Round trips between scene and screenplay

`decompile` writes a version-7 screenplay that compiles back to the identical
scene, including unused styles, object parts, exact IDs and list order. It
does not keep comments, formatting or your original shot names: shots come back
as `shot0`, `shot1` and so on. Both editors' **Import screenplay** and
**Export screenplay** do the same. Import opens an unsaved scene; export does
not change the save state or undo history.

- Literal `id` fields on actions, cues and transitions keep their IDs; without
  them, IDs are generated.
- `order` lists the IDs of `puppets`, `captions`, `audio`, `cameraEffects` and
  `dialogue` in their saved order when that differs from shot order.
- A caption, line or shake belongs to the shot where it starts and may run into
  later shots; it is kept whole, so typing, animation and shake patterns
  survive.
- Commands on the same frame keep their order.

Scenes with relative assets must be compiled and decompiled beside those
assets. The compiler rejects unknown or duplicate fields and invalid timing.

## Object coverage

```bash
python3 tools/stageplay/stageplay.py object-coverage --out coverage.json --format json
```

`object-coverage` lists every object number for every level context of each
game, with whether Stageplay can create it, whether its art loads, whether it
can be directed as a Puppet or Pose, and why not. A registered object is not
the same as one whose art is loaded and whose picture can be captured. Bosses
that always appear at their arena show **Free: native arena position, anchor
ignored**, with the routine responsible. Objects whose directed appearance
depends on a hardware sprite mask that cannot be kept are listed as appearance
exclusions; direct a different appearance or keep them Free.

`--observations reviewed.json` appends reviewed samples (version-1 file, at
most 10,000 entries, with source, ROM and scene hashes, mode, frame, evidence
and limitations). `--source-revision` records the source revision. A sample
covers only its exact context, frame and subtype.

## How preview, simulation and export work

These details matter when you investigate a problem or write tools.

- **One live world per shot.** Every shot starts a fresh copy of its level
  with the real objects, rings, collision, timers and level events. Puppets get
  controller input; nothing writes their position or velocity except the
  shot's starting placement. Each performer uses its own game's movement rules
  over the shot's terrain.
- **Isolated worlds.** Editor preview, simulation, routes, export and mod
  playback each run the scene in their own world. The editors' preview runs in
  a separate offscreen process; planning runs in another. None of them advances
  or replaces the game or level you are editing. On Linux these processes run
  without a display and require surfaceless EGL.
- **ROMs.** Each game's ROM is read through the normal ROM pipeline. Workers
  receive exact descriptions of the configured ROM images, including combined
  images, without writing new ROM files. Saved levels and assets come from the
  scene's own folder, or from the mod's registered assets.
- **Seeking.** Seeking restores the whole world from its rewind history and
  replays input silently. Forward playback plays the cues and the game's own
  sound effects once; seeking never replays sounds.
- **Activation.** Each performer and directed object gets its own game-sized
  activation area, so cast members far apart share a shot without loading
  everything between them. All areas share the game's object slots;
  exhaustion names the placement and suggests clearing objects or splitting
  the shot.
- **Camera and backgrounds.** The camera is presentation only; the game's own
  camera and scroll still run for physics. Backgrounds draw with the game's
  parallax and deformation at the directed view, and deliberately stationary
  backgrounds (such as Death Egg's) stay still. Views taller than the game
  screen repeat the bottom row of background tiles instead of another sky.
  Fractional zoom filters only the finished world picture; text is drawn
  separately and stays sharp.
- **Films.** Films hide the gameplay HUD and debug overlays, and show new
  terrain at once instead of the level's load fade. Authored transitions
  control the reveal.
- **Music.** A scene with any `ROM_MUSIC` or `MUSIC_STOP` cue takes over the
  level's automatic music for the whole playback, including level-entry and
  title-card requests, until playback stops. Boss and event music the level
  itself requests still plays.
- **Speech.** Voices run on the engine's emulated PSG chip, mixed into the
  normal output; they do not use the game's music or sound channels. At most
  32 scene samples overlap, and a scene may have at most 100,000 blips.
- **Facing.** Authors give Left or Right, never a raw sprite flip. Players turn
  through the game's own controls. A directed object keeps the art it had for
  its own facing and mirrors as a whole, child parts included, only when the
  requested direction differs; parts that turn independently in the game turn
  around their own anchors. A missing facing description for an object is an
  error before playback.

Agents should also read the
[stageplay-authoring skill](../../../.agents/skills/stageplay-authoring/SKILL.md).
