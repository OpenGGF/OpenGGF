# Author Stageplay from a terminal

`tools/stageplay/stageplay.py` exposes the directing framework without opening editor
menus. It compiles a shot-local screenplay into, and decompiles from, the same `.stageplay` document used
by the in-engine editor, OpenGGF Editor and mod registration. Java 25 is required;
movie capture additionally needs FFmpeg and the platform's headless GL prerequisites.

```bash
python3 tools/stageplay/stageplay.py --build help
python3 tools/stageplay/stageplay.py init --out /your/production/first.script.json
python3 tools/stageplay/stageplay.py catalog --game s2 --rom /your/existing-s2.gen --character tails --zone 0 --act 0
python3 tools/stageplay/stageplay.py compile --script /your/production/first.script.json --out /your/production/first.stageplay
python3 tools/stageplay/stageplay.py decompile --scene /your/production/first.stageplay --out /your/production/refined.script.json
python3 tools/stageplay/stageplay.py inspect --scene /your/production/first.stageplay --frame 120
python3 tools/stageplay/stageplay.py simulate --scene /your/production/first.stageplay --out /your/production/state.jsonl --s2-rom /your/existing-s2.gen
python3 tools/stageplay/stageplay.py render --scene /your/production/first.stageplay --out /your/production/first.mkv --s2-rom /your/existing-s2.gen
```

Build once, then omit `--build` until engine sources change. It packages with tests
skipped; this is not a test result. `--jar PATH` selects a supplied packaged engine.
All command paths are relative to the caller. Output is one JSON `{ok,result}` or
`{ok:false,error}` object; engine diagnostics go to stderr. Exit 0 means success,
2 means invalid input or failure, and cancellation exits 130. Outputs are created
without replacing existing files. Choose a new output name for each take.

`validate --scene PATH` checks the complete structural contract without ROMs.
`inspect` includes cast, cues, changes, effects and optionally one sampled frame.
`timeline --scene PATH --out state.jsonl` streams every evaluated frame with its
seconds, active set, poses, camera, transition and cues. `--every N` chooses a
coarser inspection interval; it does not change the movie cadence. This is authored
directing state: physical actors are explicitly `UNRESOLVED` here. Use `simulate`
with explicit `--s1-rom`, `--s2-rom` and/or `--s3k-rom` paths to evaluate native
behavior without a window or audio playback. Every scene uses one `LIVE` native
world, including actors, objects, rings and events in `sample.world`. To stage a
terrain-only picture, author a `CLEAR_OBJECTS` world command at shot frame zero;
it clears object roots and their graphs, while rings remain separately owned.
Simulation reports actual positions, velocity, grounded state and movement status,
advancing every native tick even with `--every N`. Shot spawns and cuts remain
authored; these observations do not alone prove a continuous cold route or ROM
parity. Keep bulk timelines beside the movie, distinct from normal gameplay CSV.
`catalog` verifies the requested ROM identity and reports native registry metadata,
character animation IDs, delays and mapping frames. Add both `--zone` and `--act`
to decode one detached set and report bounds. A registry entry alone does not certify
set availability. Rendering eagerly validates resources before encoding.

The render command accepts `--s1-rom`, `--s2-rom`, `--s3k-rom`, optional `--rom` and
`--game` for the boot ROM, `--scale` (1..8), `--ffmpeg`, and `--staging-dir` (empty or
new owned staging directory). It produces lossless FFV1/FLAC MKV at the document's
60 Hz cadence using offline audio. Output dimensions are the scene dimensions multiplied by `--scale`, using integer
nearest-neighbour scaling (the default is 1).
Linux uses surfaceless EGL with desktop display variables removed. Other platforms
use the capture host's hidden native context; macOS uses Java's first thread.

## Return editor changes to a screenplay

`decompile --scene PATH --out PATH` reads the current version-seven canonical scene,
then writes deterministic version-six shot-local JSON. Recompile it to recover the
same complete document, including unused profiles, object parts,
cue ordering and exact IDs. Decompilation changes the source layout and formatting;
it does not reconstruct comments or the original shot names. It does not need ROMs.
Both editors expose the same import/export commands. Import starts an unsaved
canonical scene; export does not mark the current scene saved or clear undo history.

Screenplays accept optional literal `id` fields to actions/cues and `transition.id`.
Omitting them retains generated IDs. The optional root `order` object has only
`puppets` (actor IDs), `captions`, `audio`, `cameraEffects` and `dialogue`: each
provided list must be an exact permutation of that track's IDs. This preserves
canonical list order when shot grouping would otherwise reorder it. Unknown keys,
duplicate IDs, missing IDs and foreign IDs fail explicitly. Decompilation emits
neutral `shot0`, `shot1`, … names independently of transition IDs.

A caption, effect or dialogue interval belongs to the shot containing its start.
Its local `endFrame` may extend into a later shot, bounded by the complete scene;
the interval is retained whole so reveal, animation phase and effect seeds survive.
Commands at the same frame retain their authored order. Decompile scenes with
relative assets beside their canonical file; compile beside the screenplay. Neither
command overwrites existing outputs. No hidden canonical payload is embedded.

## Learn with a complete small scene

[Wait for me](../../../examples/stageplay-demo/README.md#your-first-scene-wait-for-me)
is a 26-second beginner example with a clear goal, a mistake and a changed choice.
It uses two physical actors, actual moving platforms, finite landing waits, a group
camera and named dialogue. Open its `.stageplay` in either editor, or revise the
screenplay and use the commands above. Change one choice, preview its consequence,
then watch the whole scene before exporting. The accompanying production archive
shows each directing concept beside the resulting performance, followed by the
uninterrupted short.

## Screenplay format

Only screenplay `version:6` and canonical document `version:7` are accepted.
These formats and the candidate API are unpublished; examples are authored directly
in the current shape, with no migration or compatibility modes. The API version
follows the engine release version. Every shot uses the same native `LIVE` world.
Use timed `CLEAR_OBJECTS` commands for an empty object cast; rings remain separate.
JSON numbers retain their types: numeric strings, duplicate fields, unknown fields
and trailing input are rejected. Screenplays permit up to 32 MiB to accommodate
lossless shot-local expansion of an 8-MiB canonical document.

```json
{
  "version": 6,
  "name": "A change of scenery",
  "width": 400,
  "height": 224,
  "cast": [{"id":"tails","name":"Tails","gameId":"s2","character":"tails"}],
  "shots": [
    {
      "id":"hello", "durationFrames":240,
      "set":{"gameId":"s2","zone":0,"act":0},
      "camera":[{"frame":0,"x":0,"y":500},{"frame":239,"x":40,"y":500,"zoom":1}],
      "actors":{"tails":[{"frame":0,"x":80,"y":655}]},
      "actions":{"tails":[{"frame":0,"kind":"MOVE_TO","x":140,"y":648,"urgency":0.5},
                          {"frame":30,"kind":"JUMP","holdFrames":12}]},
      "dialogue":[{"startFrame":60,"endFrame":220,"speakerActor":"tails",
                   "text":"A small change of scenery..."}]
    },
    {
      "id":"elsewhere", "durationFrames":240,
      "set":{"gameId":"s1","zone":0,"act":0},
      "transition":{"kind":"FADE_WHITE","durationFrames":30},
      "camera":[{"frame":0,"x":0,"y":800}],
      "actors":{"tails":[{"frame":0,"x":140,"y":944}]},
      "cameraEffects":[{"startFrame":20,"endFrame":50,"amplitudeX":3,"amplitudeY":2,"frequency":8,"seed":7}],
      "dialogue":[{"startFrame":60,"endFrame":220,"speakerActor":"tails",
                   "text":"...a very small change."}]
    }
  ]
}
```

The example illustrates the format; inspect the chosen set and framing before
calling it a finished shot. Width defaults to 400 and height to 224. There may be
32 cast members and 129 shots, with a combined maximum of 36,000 frames. IDs are
unique, bounded scene identifiers. Namespaced event IDs include their shot prefix,
so use short shot/event names.

Each shot requires `id`, positive `durationFrames`, `set` and `camera`. Sets use
`gameId`, zero-based `zone` and `act`; optional `romSha1` pins exact lowercase SHA-1,
and `levelProject` references a relative `.ogfproject`. Both default to empty strings.
Each camera and present actor track starts at local frame zero, with strictly ascending
keys before the shot's end. Missing actors are hidden, with a boundary key inserted.
A shot is an explicit cut even when it reuses the preceding set.

| Track | Fields and defaults |
| --- | --- |
| Camera key | `frame,x,y`; `zoom:1`, `interpolation:"LINEAR"` |
| Actor key | `frame`; `x:0,y:0,animation:0,pose:-1,flipX:false,visible:true,scale:1`; interpolation defaults to `HOLD` for puppets, `LINEAR` for Poser actors |
| Dialogue | `startFrame,endFrame,text` and `speakerActor` or `speakerName`; optional profiles, timing and panel fields below |
| Caption | `startFrame,endFrame,text`; `x:12,y:12,argb:-1`, optional `id` |
| Audio | `frame,kind`; `gameId` defaults to the shot's game, `soundId:0,asset:"",gain:1`, optional `id` |
| Camera effect | `startFrame,endFrame,amplitudeX,amplitudeY,frequency`; `seed:0`, optional `id` |
| Incoming transition | `kind` (`CUT`, `FADE_BLACK`, `FADE_WHITE`, `WIPE_LEFT`, `WIPE_RIGHT`), `durationFrames` |

For Poser actors, interpolation is `HOLD`, `LINEAR` or `SMOOTH`. Actor `pose:-1` selects its animation;
nonnegative values hold a mapping frame. Positions use sprite origins. Captions are
screen-space pixels. Audio kinds are `ROM_MUSIC`, `ROM_SFX`, `VOICE`, `MUSIC_STOP`;
voices reference bounded WAV/Ogg files beside/below the scene. Relative resource scenes
must compile beside the screenplay to preserve their root.

An absent transition means `CUT` with duration zero. Non-cut transitions last 2..600
frames and straddle the incoming shot's start: the set changes at the fully covered
midpoint. The first shot cannot have an incoming transition. Leave sufficient room
on both sides; transitions cannot overlap. Events use half-open intervals and may
extend beyond their starting shot within the scene. Shake is deterministic sine motion in stage pixels at
1/60-second steps; `frequency` is Hz, greater than zero and at most 30.

The compiler flattens offsets, adds all boundary anchors and prevents interpolation
across them. Both editors can edit the canonical scene and export a lossless
shot-local screenplay for subsequent programmatic revision.


## Puppet and Poser modes

Cast members default to `puppet:true`, `snapToFloor:true` and
`collisionPath:0` (**Puppet** mode). Add `puppet:false` for **Poser** mode, with
manual position, animation and frame selection. A physical
actor's frame-zero key is a spawn centre; it keeps `animation:0`, `pose:-1`,
`scale:1` and `interpolation:"HOLD"`. The native controller selects its actual
animation, pose and facing. Interior keys may change visibility only; movement and
turning come from commands. Every shot resets the actor from its boundary key and
clears earlier goals and jump holds.

The shot's `actions` object maps actor IDs to ordered command arrays:

| Action | Fields |
| --- | --- |
| `MOVE_TO` | `frame,kind,x,y`; `urgency:0.5`, in `(0,1]` |
| `STOP` | `frame,kind` |
| `JUMP` | `frame,kind`; `holdFrames:12`, from 1 to 60 |

An optional `id` is preserved literally; omitted IDs are generated from the shot and actor. A movement command and a
jump may share a frame; two movement commands may not. Jump holds require a released tick between presses and cannot cross a shot boundary. The engine receives directional and jump button inputs:
urgency requests a fraction of that character's native running speed, rather than
setting velocity. Acceleration, slopes, jump impulse, variable jump height, gravity,
terrain collision and landing remain native behavior. `STOP` brakes through inputs.
No command teleports an actor to its destination or guarantees arrival.

Initial floor snapping searches near the spawn feet. Put the spawn near a real
surface; snapping is not a search through the entire level. Once moving, normal
native collision maintains floor contact or lets the actor fall from an edge.
`collisionPath` selects terrain path 0 or 1. A destination's Y is part of the
arrival check, not a vertical movement command: use `JUMP` explicitly when needed.
A MOVE_TO command does not automatically find a route or jump obstacles. The
optional [route helper](#route-helper) emits explicit attempts. The native world uses
objects and collision with explicit routes/targets and bounded waits. A timed
`CLEAR_OBJECTS` command clears object roots while retaining native simulation.

Simulation reports `IDLE`, `MOVING`, `STOPPING`, `ARRIVED` or `BLOCKED` plus the
active movement-command ID. Blocking describes lack of progress and does not
silently cancel the attempt. Roles hidden for the entire shot report `INACTIVE` and
do not need a floor spawn in that shot. Inspect actual positions and status before rendering;
a pure `timeline` sample deliberately reports `UNRESOLVED` for physical actors.
Scrubbing and repeated drawing resolve the same frame without adding ticks or
playing historical sounds. Native preview/export includes forward-only native SFX;
distinguish those observed events from authored audio cues.

## Dialogue profiles and decorations

Use `dialogue` for speech and `captions` for titles or ordinary overlays. A dialogue
cue names its `speakerActor`; its displayed name comes from that cast member.
`speakerName` overrides the label or identifies an offscreen speaker when the actor
reference is empty. The name appears immediately, independently of text reveal.
An optional pointer follows an onstage speaker through camera motion.

Define up to 64 reusable `dialogueTypers` and 64 `dialogueSkins` at screenplay root:

```json
{
  "dialogueTypers": [{"id":"excited","framesPerGrapheme":1,"punctuationPauseFrames":4,"spacePauseFrames":0}],
  "dialogueSkins": [{"id":"blue","nameArgb":-9060353,"borderArgb":-9060353,
    "decorationAsset":"decor/blue.png","decorationBorder":5,"padding":8}]
}
```

These fields join the normal screenplay root; this fragment is not a complete film.
A shot's line selects them with `typerId:"excited"` and `skinId:"blue"`. IDs are local
to the document, so two installed mods do not compete for a global style name.
Unknown references fail validation. Empty IDs use the built-in profiles.

| Definition | Fields and compiler defaults |
| --- | --- |
| Typer | `id`; `framesPerGrapheme:2` (0–30), `punctuationPauseFrames:6` and `spacePauseFrames:0` (0–120) |
| Skin | `id`; `textArgb:-593700`, `nameArgb:-11172`, `panelArgb:-15722457`, `borderArgb:-7826009`, `padding:6`, `decorationAsset:""`, `decorationBorder:0`, `pointer:true` |
| Dialogue cue | `startFrame,endFrame,text`; `speakerActor:""`, `speakerName:""`, `skinId:""`, `typerId:""`, `x:12,y:12,width:filmWidth-24,height:min(70,filmHeight-24)`, `portraitAsset:""`, `revealFrames:[]`, optional `id` |

Colour values are signed 32-bit ARGB integers. The canonical document requires all
fields; the screenplay compiler supplies these defaults. Typing is deterministic at
60 Hz: the first grapheme appears at elapsed frame zero, followed by the selected
cadence and pauses. A rate of zero displays the whole line immediately. `revealFrames`
can override the profile with one nondecreasing elapsed-frame offset per Unicode
grapheme. This supports manually timed bursts and pauses without executable scripts.
All text must finish before the line ends. Scrubbing and drawing emit no sounds;
use explicit audio cues for authored sound effects.

The complete text wraps before revealing, so words stay in place. The panel must
fit inside the film and contain the speaker name and all wrapped rows; overflow is
an error. The existing engine font and its glyph coverage are unchanged. These are
portable reveal profiles, not arbitrary glyph-animation callbacks or custom fonts.

A decoration is a creator PNG interpreted as a nine-slice panel. `decorationBorder`
sets equal edge/corner insets in source pixels; it must leave a nonempty middle and
fit the panel. An optional portrait PNG fits a 32×32 region beside the text. PNGs
are limited to 1024×1024 each and four million decoded pixels across the scene.
Keep PNGs beside or beneath the scene, compile beside the screenplay, and move the
whole package together. In mods, images use the registering owner's bounded asset
root and typed loads, sharing the existing encoded asset budget with voices/sets.
The same definitions work in both editors and offline rendering without mod code.

## Pixel stability and performance

Camera keys interpolate the framing centre and zoom using their `interpolation`
easing, so two keys with the same centre keep that centre fixed through a push-in.
Fractional camera positions survive world composition and resampling; slow pans do
not snap to whole source pixels. Fractional zoom filters only the completed opaque
world picture; captions and dialogue remain sharp. The private world framebuffer
is reused and grows only when needed, with the active source extent cropped for
each view. A larger export `--scale` cannot repair aliasing already present
in the authored frame. Stage entrances, exits, turns and listener reactions, then
review the entire movie in motion at its intended output size.

## Native ROM worlds (screenplay 6, document 7)

Every scene uses a native world. A shot selects a populated native level with
`"set":{"gameId":"s3k","zone":11,"act":1}`.
Saved authored terrain, mixed supplied-game performers, zoom, output dimensions,
team size, activation windows and native binding use the same rules with populated
or cleared objects. Output dimensions are 160–1920 by 112–1080; the source view
(width/zoom by height/zoom) is bounded to 4096 per side and 8 MiPixels. Extreme
framing fails resource preflight explicitly. Native physics still uses its
original viewport and 224-line scroll owners. Films omit
gameplay HUD/debug overlays, while native ring telemetry remains available. The
cinematic draw reveals fresh terrain immediately instead of showing the native
load palette fade; native fade timers and actor/world clocks remain unchanged.
Authored shot transitions still control the film reveal. Live spawn
snapping attaches feet near the floor; disable it for an authored fall. Live worlds run in an
owned offline process; preview never replaces the editor's active world. The
in-engine workspace uses a persistent offscreen worker for native pixels and the
same sampled state used by simulate/capture. The worker receives exact logical
ROM descriptors, including original image paths, window bounds and fingerprints,
and reconstructs composite readers without writing joined ROMs. Authored sets
use the confined editor asset root or bounded original registration snapshots. Linux workers
remove desktop display variables and require surfaceless EGL. Failure is visible.

Discover original placements and rings before choosing a route:

```bash
python3 tools/stageplay/stageplay.py level --game s3k --rom "$S3K_ROM" --zone 11 --act 1
python3 tools/stageplay/stageplay.py catalog --game s3k --rom "$S3K_ROM" --character tails
```

`level` emits the ROM SHA-1 and complete object/ring placement lists. An object
anchor uses its original `layoutIndex`, expected `objectId` and `subtype`, plus
`offsetX`/`offsetY`. Runtime slots and object pointers are not portable identities.
`MOVE_TO` optionally takes `"target":{"layoutIndex":179,"objectId":74,"subtype":1,
"offsetX":0,"offsetY":-33}`. With no target the compiler supplies the empty identity
and uses world-centre `x`/`y`. A target resolves every tick, including after rewind
recreation. Validation rejects unknown placement/type/subtype combinations; an
object that has not entered the native activation window reports `NOT_ACTIVE`;
a previously active object that disappears reports `DESPAWNED` and the actor stops
steering toward stale coordinates.

Commands keep earliest shot-local `frame` values. `WAIT` gates later due commands
until `condition` succeeds or `timeoutFrames` expires; neither timing branch moves
a player. Conditions: `ARRIVED`, `ON_OBJECT`, `GROUNDED`, `AIRBORNE`, `HURT`,
`RECOVERED`, `RING_COLLECTED`, `TARGET_AVAILABLE`. `ON_OBJECT` can name a particular
placement; `TARGET_AVAILABLE` requires one. Use a finite timeout, inspect the result,
and author the next route decision. A timed-out wait continues to later due actions
and emits `WAIT_TIMED_OUT`; it never silently teleports to satisfy the route.
A move remains an input attempt. There is no universal pathfinder.

Native intents add `FLY_TO`, `HOVER`, `GLIDE_TO`, `RELEASE`, `CROUCH`, `LOOK_UP`,
`ROLL`. `FLY_TO`/`GLIDE_TO` use destination/urgency like `MOVE_TO`; `HOVER`,
`CROUCH`, `LOOK_UP` have finite `holdFrames`. Flight requires S3K Tails and glide
requires S3K Knuckles. A second jump edge and ordinary jump holds/flaps invoke
native ability controllers. Exhaustion, gravity, hurt and landing remain native.
`RELEASE` releases the ability button and brakes horizontal motion; Tails' existing
flight does not instantly cancel, so allow native descent/landing time. Hurt and
ring loss come from actual hazard contact; no sprite-only damage command exists.

Camera keys optionally include a `follow` object:

```json
{"actors":["sonic","tails"],"deadZoneX":24,"deadZoneY":16,
 "lookAhead":8,"smoothing":0.15,"offsetX":0,"offsetY":-16}
```

One actor follows that performer; several frame their centre bounds at authored
zoom. Empty actors preserve authored camera keys. Handoffs occur at camera keys.
The director uses simulated positions and deterministic tick-based smoothing,
validates the finite composition bounds, then composes the existing shake. Native camera/focus
continues to own gameplay camera state; directed rendering samples immutable native
scroll phases without advancing them. Background camera ratios, deformation and
stationary regions come from the owning game handler, with private background
residency for the complete bounded view. Tall views repeat the bottom native
pattern band beyond the 224-line backdrop instead of wrapping another sky or
stretching one scanline. Gameplay camera locks remain native.
The shared camera controls start from the resolved LIVE follow view. Capture key
and Record camera store authored framing, clearing following at the captured keys.
The camera form preserves follow targets: editing its displayed position adjusts
follow offsets while preserving the authored smoothing seed. An unchanged form
submission does not bake the current follow result into that seed.
Current scenes activate separate native-sized windows around players and explicit
object roles, including vertically separated performers. The gap between windows
is not activated. All windows share native object slots and removal/remember
state; exhaustion reports which placement needs space and suggests clearing
objects or splitting the shot. Native damage/destruction still applies.

`simulate` emits `sample.world`: native clocks, ring population/collection state,
placed-object positions, actor ring deltas, hurt, support placement, ability state,
ability timer and actual animation/mapping frame. Its events identify boarding,
leaving, pickups/loss, hurt/recovery, ability transitions and bounded wait outcomes.
Repeated frame sampling does not tick. Seeking restores the whole native rewind
registry and director/control state, then silently replays controller inputs.
Shot boundaries start a clean world; never interpret spawn placement as movement.

Current native-world registered cutscenes use the same isolated world and forward-only native SFX
packets as editor preview. The optional native binding uses the same world model,
with a matching interactive set and character, using visit-bound ownership and explicit
KEEP_FINAL_POSITION / RESTORE_ENTRY handover; see the Stageplay guide.

Direct music explicitly with a `ROM_MUSIC` cue at each shot's local frame zero,
using the matching `catalog` music ID. The isolated preview worker stops its
automatic level music because the parent owns authored music; offline exports
consume those same cues alongside native SFX. Do not rely on capture-only level
music as the film's directed soundtrack.

## Object cast (screenplay 6, document 7)

Object roles are separate from player controller tracks. Declare their native
source context in `objectCast`, then use shot-local object keys and actions:

```json
{
  "objectCast": [
    {"id":"prop","name":"ROM prop","gameId":"s2","zone":0,"act":0,
     "objectId":86,"subtype":0,"layoutIndex":-1,"sourceKind":"LAYOUT_OBJECT"}
  ],
  "shots": [{
    "id":"object-shot","durationFrames":180,
    "set":{"gameId":"s2","zone":0,"act":0},
    "camera":[{"frame":0,"x":0,"y":500}],
    "objects":{"prop":[
      {"frame":0,"x":128,"y":600,"mode":"FREE"},
      {"frame":60,"x":128,"y":600,"mode":"PUPPET"},
      {"frame":120,"x":200,"y":600,"mode":"POSE","pose":0,
       "parts":[{"partId":"root/0","animation":0,"pose":0}]}
    ]},
    "objectActions":{"prop":[
      {"frame":60,"kind":"MOVE_TO","x":200,"y":600,"speed":2},
      {"frame":110,"kind":"STOP"}
    ]},
    "worldCommands":[{"frame":150,"kind":"REMOVE_OBJECT","actorId":"prop"}]
  }]
}
```

This fragment belongs inside a version-six screenplay with its normal name,
size and player cast fields. Mapping/part IDs are object-specific: inspect the
captured graph and coverage before selecting a frame; the sample numbers are
syntax, not a guarantee of that boss's mapping layout.

`layoutIndex:-1` creates a new role; a nonnegative layout index selects the loaded
`(layoutIndex, objectId, subtype)` tuple in the matching native game/zone/act.
The native level population is already present in populated worlds; claiming it
adds directing controls without a second native object. Runtime slots are never
portable identities. `sourceKind:RELEASED_ANIMAL` addresses the existing released
animal factory, with `objectId:0` and `layoutIndex:-1`; the source set supplies the
ROM species/art. S1 layout animals remain ordinary native object IDs.

Keys expose `frame,x,y,mode,animation,pose,flipX,visible,interpolation,parts`.
`parts` contains stable parent/creation paths with independent animation/frame
selection. `pose:-1` selects pure animation where available, otherwise the captured
held frame. Native timer-owned bird flap cycles also run in Puppet/Pose. Walking
animal frames selected by native vertical velocity keep a documented held fallback;
direction does not invent hop physics. Explicit mapping frames win over animation.
State-machine-only appearances stay held/authored with the owning routine citation.
Uninitialized or unavailable art fails explicitly, with the native source context
and the preparation needed; it is never replaced by unrelated art.

Free is the native routine, including contacts, child spawning and world effects.
Some bosses reset themselves to their native arena coordinates. Their catalog and
both editors display **Free: native arena position, anchor ignored**, with the
initialization routine citation. Free requires the scene to match the cast entry's
authored source game/zone/act. Registry context restrictions still apply. If a
registry exposes an arena routine in another zone/act, it runs at its native
coordinates there, with its real world effects; the tool does not infer another
arena restriction from the boss category. Use its original arena or a directed
visual mode for predictable placement. There are no object-specific spatial adapters. Switching an existing graph to Puppet/Pose
suspends all parent/child routines and contacts before the next native tick.
Directing or removing a holder releases its acquired players through the native
player-only cleanup. Helper handoffs and queued ending poses retain ownership
through rewind; interruption does not complete a cutscene or change the world.
Visual parts retain their native relative transforms. In directed modes, `flipX`
mirrors the captured graph around its visual anchor; it is an additional visual
transform, rather than a controller-facing input. In Free mode the initial flag
is passed to the native spawn. Puppet `MOVE_TO` drives its
visual anchor at the requested pixels per frame; `STOP` ends that displacement.
It is not controller input or a relocated native arena. Pose keys directly author
position/mapping frames. Returning an existing graph to Free resumes its frozen
native state/position, rather than moving its native arena to the last picture.

`CLEAR_BADNIKS` and `CLEAR_OBJECTS` have no target. `REMOVE_OBJECT` takes exactly
one `actorId` or `target:{layoutIndex,objectId,subtype}`. Clears/removals suppress
native placement reloads for the shot. Players, their attached effects and rings
have separate ownership and are preserved by these commands. Shot boundaries
start fresh worlds. Both editors expose these same operations through **ROM object
cast / clear**, including editing and deleting cues and child-part overrides.

Generate the complete ID/context inventory with
`object-coverage --out coverage.json --format json` (or `markdown`). Read its
readiness and reason fields: a registered factory is distinct from actual loaded
ROM art and a complete captured visual graph. Dynamic/parent-created routines are
reported separately from layout IDs. Optional `--observations reviewed.json` appends
exact-context capture, exclusion and failure samples without promoting the registry
rows. The strict version-one observation file records source/ROM/scene hashes,
mode, frame, evidence paths and limitations; it validates provenance fields, not
whether the footage was reviewed. Full continuously driven private native
boss graphs are deferred; directed pictures never keep native effects running
behind them.

An active native SAT mask/control is an explicit directed-appearance exclusion;
it cannot be dropped while keeping only the parent body. The catalog and the
**Available parts / appearance exclusions** menu identify known restrictions with
the owning routine. SKL's DEZ miniboss mask is one such conditional exclusion.
Initialize and direct a different supported appearance, or keep the graph Free.

## Route helper

```bash
python3 tools/stageplay/stageplay.py route --scene /your/production/first.stageplay --actor tails --frame 0 --x 320 --y 648 --out /your/production/routed.stageplay --s2-rom /your/existing-s2.gen
python3 tools/stageplay/stageplay.py decompile --scene /your/production/routed.stageplay --out /your/production/routed.script.json
```

Use the actor's native centre as the destination. Compile a screenplay first;
route the canonical scene, then decompile it back into editable screenplay.
Both editors expose the same **Route helper**. Output publication never overwrites
a file and failures do not publish a partial scene. Relative assets keep their
source directory. Existing commands and actors outside the requested patch remain
unchanged. The helper never inserts position keys or changes an actor's mode.

Player planning starts at a shot entry in the native world.
It searches a bounded graph of native
physics attempts over the actual ROM/authored collision terrain, using the same
steering as LIVE `MOVE_TO` and native jump button holds. A proposal is accepted
only after fresh linear cold LIVE replay reaches the requested centre grounded
and stable, without hurt, death, wait timeout or an unexpected scene change.
`VERIFIED` certifies that replay of this exact input; it does not certify arbitrary
future edits or every route through moving objects, water and zone events.

Object routes start at shot entry or an existing visible Puppet key in the same
shot. This permits an explicit Free initialization lead-in for constructor-gated
visuals; the worker replays that prefix before suspending the graph. Other
interior frames, Free, Pose and hidden keys reject. No key is inserted for routing.
Object Puppets require `--half-width N --height N --foot-offset N`;
`--collision-path 0|1` selects the queried path and `--speed N` is visual pixels
per tick. The footprint is an authored clearance envelope: half-width, height
above the feet, and feet offset below the object's anchor. It is not a native
collision-radius adapter. `VISUAL_TERRAIN` means checked floor/headroom waypoints
for the suspended picture, emitted as ordinary object `MOVE_TO`/`STOP` commands.
No object routine, child AI, contact, jump or native world effect is enabled.
The object graph uses fixed foreground collision on the selected path; active
background-plane collision rejects explicitly. Moving object/platform solids are
not graph nodes and visual Puppets have no native contact response. Fresh linear
replay checks the actual visual positions against each tick's foreground terrain,
so changed terrain or insufficient clearance fails rather than certifying the
entry snapshot for the whole shot. Floor following handles slopes and steps up to
8 pixels using explicit lift/cross/lower waypoints. Brief step clearance above the
floor is visual direction, not a native grounded controller. Gaps, walls, larger
steps, insufficient headroom and dynamic collision report failure; the helper
does not search detours or jump.

Optional budgets are `--frames` (default up to 300 remaining shot frames,
maximum 3600), `--nodes` (default 1024, maximum 4096), `--ticks` (default 120000,
maximum 500000), and `--millis` (default 60000, maximum 120000). `--urgency` is
0..1 exclusive of zero; object speed is 0..16 exclusive of zero. Search resolution
and budgets are authoring policies, not ROM physics constants. Player search is
limited to destinations within 1536 horizontal and 768 vertical pixels of entry
and at most 256 retained frontier checkpoints; these are basic-tool limits. `UNSUPPORTED`,
`SEARCH_EXHAUSTED`, `BUDGET_EXCEEDED`, `CANCELLED`, `VERIFICATION_FAILED` and
`STALE_INPUT` explain bounded failures; none means that every possible route is
impossible. CLI failures exit 2. Inspect the returned diagnostics and simulate
the routed scene before rendering.

Editor planning owns an independent logic worker. Its input binds the canonical
document, request, supplied logical ROM windows and content snapshots of relative
project/assets. Freshness is checked again before one undoable Apply; changed
inputs or late cancelled work cannot overwrite newer authoring edits. The content
fingerprint is retained because a terrain project or ROM can change while the
scene document remains equal; document equality cannot certify the tested route.
Planning does not borrow the active gameplay session, preview worker or audio ownership.

Evaluated cameras, including authored keys, follow offsets, shake and paused director
drafts, clamp to the loaded level camera envelope at the current viewport and zoom.
Keys retain their authored values. Both editors display the resolved framing.
