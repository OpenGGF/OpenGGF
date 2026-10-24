# Direct a scene with OpenGGF Stageplay

Stageplay directs supplied ROM characters and worlds, or saved editor sets, for
linear sprite films, animatics and mod cutscenes. Both the built-in editor HUD and
[OpenGGF Editor](https://github.com/OpenGGF/OpenGGF-Editor) use the engine's same
scene document, editing commands and evaluator. A `.stageplay` file opens in either.

This guide describes the current **0.7.prerelease development line**. Stageplay
and its unpublished candidate Mod API are outside the published 0.6 release scope;
use a matching development engine/editor build.

## Make a first scene

1. Open a level project, or enter the [level editor](level-editor.md) from gameplay.
2. Press **F4** or choose **Stageplay workspace**. A new scene starts at the current
   camera position. A saved, unchanged level is linked; unsaved terrain is copied
   to a new `projects/stageplay-<id>/set.ogfproject`, preserving the level editor's
   original document and savepoint.
3. Choose **Add actor**, name the role and select its ROM (`s1`, `s2`, `s3k`) and
   character. Supported native cast are Sonic, S2/S3K Tails and S3K Knuckles.
   Each game's ROM must be available when its cast or audio is used.
4. New roles default to **Puppet** mode. Set their spawn centre near the floor,
   then choose **Set actor key** to add a destination/urgency, stop or jump action.
   The native player controller handles acceleration, terrain, gravity, animation
   and landing. Floor snapping is enabled at spawn; movement uses native collision.
5. Choose **Poser** mode for manual positions, animations and fixed mapping frames. Poser keys
   use **HOLD**, **LINEAR** or **SMOOTH**; `Pose:-1` selects animation playback.
   Camera keys use the world's top-left coordinate and a zoom factor.
6. Add dialogue with a named speaker, captions and audio cues. End frames are exclusive. Existing keys
   and cues can be revisited, edited or deleted; frame-zero actor/camera keys remain.
7. Save beside the scene's referenced assets. Play, scrub and reopen it in the
   other editor. Switching back to the level editor preserves Stageplay undo and
   unsaved changes. New/open/quit offer save, discard and cancel choices.

The built-in HUD has **Cast**, **Timeline** and **Commands** pages. Arrow keys or
the pad navigate; left/right step frames, Shift steps one second, Space plays,
K sets an actor key, and F1 opens commands. The desktop editor provides cast,
direction and timeline panels with the same operations. The form's PATH fields
can browse local files. Properties apply together when their final action is chosen.

Puppet roles use destination and urgency commands, with independent jump button
holds. The inspector shows the resolved centre and movement status, including
arrival or blocked attempts. A goal does not guarantee a route: jump explicitly
where needed. Every scene uses one shared **LIVE** native world. Use a timed
**Clear objects** command at shot frame zero for a terrain-only picture; rings
remain separately owned. Camera, actor and handover capabilities stay the same.
[The authoring reference](stageplay-authoring.md#puppet-and-poser-modes)
describes command fields and the windowless simulation tool.

**Record directed take** stores physical movement intents for Puppet actors. Poser mode
retains baked visual movement at two world pixels per tick. Space or Stop finishes
the take, and the entire take undoes as one edit. Native input recordings remain
the engine recording system's separate format.

Scenes run at 60 frames per second, up to ten minutes, with up to 32 actors.
Use **Scene / set** to change duration, output dimensions or the source set.
Delete keys and cues outside a shorter duration before shortening. Preview fits
the authored picture into the game viewport; film output uses its exact dimensions.

## Direct a live world

In **Scene / set**, choose a ROM zone/act or link a saved `.ogfproject` set.
The native world starts with its ROM population. **Clear objects** and
**Clear badniks** are ordinary timed commands; neither clears rings. Each shot creates a fresh
native world with independent Puppet inputs, moving objects, rings, hazards,
collision, clocks and rewind. Each performer uses its supplied game's native movement rules and ROM art over
the shared terrain and collision owners. The document allows 32 actors, subject
to the available native pattern-bank capacity; missing art fails explicitly.

The selected actor's actions include **WAIT**, **RELEASE**, **CROUCH**, **LOOK_UP**
and **ROLL**. S3K Tails also has **FLY_TO** and **HOVER**; S3K Knuckles has
**GLIDE_TO**. Native physics owns takeoff, stamina, damage, recovery and landing.
Move/fly/glide can target an actual placement using its layout index, expected
object ID/subtype and offset. Use the offline tool's **level** command to discover
those values from the loaded ROM. A wait can gate later commands until arrival,
object support, landing, hurt, recovery or ring collection, with a finite timeout.
Inspect actual outcomes. A destination remains an input attempt; use **Route helper**
for bounded planning over the set's real collision terrain.

The external Editor inspector displays native ability state/timer, shared rings,
hurt, supporting placement and target status. Its timeline names waits, object
targets and camera following. Both editors use the same editing forms and preview
owner. Preview runs separately from the open gameplay/editor world and requires
the configured logical ROM for each game. Exact catalogue-selected image windows
and composites are transported to the worker as bounded descriptors; no joined
ROM files are created. Authored sets retain their confined scene asset root or
the original bytes loaded during mod registration. Seeking is silent;
forward playback admits native effects alongside authored music/voices. Add explicit
music cues when the scene needs music in both preview and export.

Films support **160–1920 × 112–1080** output dimensions and zoom,
with the same team, activation-window and binding rules after object clears. The completed opaque world is resampled separately
from captions and dialogue. Native viewport tiles retain the engine's 224-line
rendering contract. Tall views extend the bottom native background pattern band
instead of stacking the whole backdrop; foreground terrain retains its full height.
Physics and native camera dimensions remain unchanged. The
source view is bounded to 4096 pixels per side and 8 MiPixels in total, so extreme
combinations of output size and zoom fail preflight with an actionable message.
Current scenes activate a separate native-sized region around each performer,
including objects near distant cast members. They share the native object slot
budget; clear unnecessary objects or split a shot if that budget is exhausted.
The [examples](../../../examples/stageplay-demo/README.md) include platform
transfers, flight/glide, rings/hurt and the 60-second **One More Take** rehearsal.

## See the planned route

Select a player or walking object in **Cast**, then use **Plan collision route**.
When planning finishes, close the route menu to inspect the proposal in the viewport
before Apply. Cyan follows grounded samples; gold shows airborne movement and
small takeoff chevrons. A square marks the first observed position and a diamond
marks the requested destination. The line follows the camera, zoom and viewport fit.

A red cross marks the last observed point of a failed attempt; the overlay and route
menu give the planner's reason, such as missing floor, wall/clearance or a budget
limit. A search limit does not prove a destination unreachable. No line is invented
between missing observations or from an unfinished attempt to its goal. Failures
before any movement observation show only the destination and diagnostic.

After **Check inputs and apply route**, the selected actor's plan remains visible
while seeking or playing that shot. A white cross follows the player's actual preview
centre for comparison. Edits to the applied scene or asset-root changes hide the old
plan; undoing those edits restores it. Replanning, cancellation and the freshness
check still apply to proposals. Routes are temporary workspace aids: reopening a
scene does not restore them, and saving, film export and mod playback contain no
route graphics. Object routes retain the helper's visual walking limits.

## Direct the camera and change scenes

Press **C** for camera directing mode. WASD/arrows pan, Q/E zoom,
**K** places a
camera key and **R** records a camera take; Space finishes. A take is one undo edit.
Camera keys animate their framing centre and zoom with **HOLD**, **LINEAR** or
**SMOOTH** easing. A centred push-in keeps its centre fixed. Numeric properties
accept fractional zoom, and fractional camera positions survive composition;
only the completed opaque world picture is filtered, leaving text sharp.
The draft camera stays separate from the evaluated shake, so saving a key never
accidentally bakes earthquake offsets into it. Switch back to actor mode to direct
cast movement. LIVE camera keys can follow one actor or a group: set **follow**
to comma-separated actor IDs, then tune dead zones, look-ahead, smoothing and
offsets. Follow uses actual native positions and retains authored zoom. The directed
foreground, native sprites and priority mask share one view. In current scenes,
background parallax and deformation follow that directed view while their native
animation/event phase stays frozen to the displayed gameplay generation.
S3K Death Egg's deliberately stationary background stays stationary.

Both editors expose **Camera effect** and **Scene change** commands with editable
timeline rows. Camera effects add deterministic seeded shake over a half-open
frame interval. A scene change selects a location/act/game and **CUT**, **FADE_BLACK**,
**FADE_WHITE**, **WIPE_LEFT** or **WIPE_RIGHT**. The location switches at the covered
midpoint; fades/wipes straddle that frame. Every actor and camera receives an anchor
at the boundary, preventing interpolation between unrelated locations. Revisit those
anchors to stage the incoming shot. Non-cut transitions cannot overlap.

Stageplay authoring requests a 1280×720 window with a 16:9 constraint while active,
with a smaller fallback when the monitor workarea cannot fit it. Fullscreen and
maximized windows retain their operating-system state. Leaving Stageplay restores
the previous window policy and size. The film itself keeps its authored dimensions
and aspect ratio, letterboxed inside the available preview area.

For agent-driven authoring, [the screenplay tool](stageplay-authoring.md) discovers
ROM resources, compiles shot-local scripts, samples frames and renders without
opening editor menus. Both editors offer **Import screenplay** and **Export screenplay**.
Import creates an unsaved canonical scene; refine it, save the `.stageplay`, then
export a version-six screenplay and recompile without losing supported document
data. Export leaves the current save state and undo history intact.

## Speakers, typing and dialogue skins

Add a **Dialogue** line, choose its speaker and interval, then enter plain text.
The name stays visible while the text types; a pointer can identify the visible
actor. An explicit name also supports offscreen speakers. Dialogue has its own
panel, separate from title captions.

Reusable **Typer** profiles set reveal cadence and punctuation/space pauses.
**Dialogue skin** profiles set text/name/panel/border colours, padding and an
optional nine-slice PNG decoration. A line selects either profile, or uses the
built-in defaults. Optional custom reveal offsets support hand-timed pauses and
bursts; optional PNG portraits use a small area beside the text. Full text wraps
once and is validated against the panel before playback. Seeking does not make
text reflow or play typing sounds.

Mods can ship these definitions inside their `.stageplay` assets and include the
referenced PNGs in the same resource package. No extra plugin registration is
needed. Both editors expose the same editable profiles and preserve them through
undo and save. See [the exact fields and limits](stageplay-authoring.md#dialogue-profiles-and-decorations).
This version supports data-defined reveal timing and decoration, using the engine's
existing font; it does not execute custom glyph-effect code.

## Audio and files

Audio cues use `ROM_MUSIC`, `ROM_SFX`, `VOICE` or `MUSIC_STOP`. ROM sound IDs are
decimal native IDs; for example, Sonic 2 Emerald Hill music is 129 and Jump is
160. ROM cues use gain 1. Voice cues support WAV and Ogg, relative to the scene's
folder, with gain from 0 to 1. Copy the audio under that folder, then enter its
relative path. Each encoded clip is bounded; the scene's decoded voice cache is
limited to 64 MiB. Missing or invalid resources fail playback before it starts.

Player and ROM object cast members can speak through the dialogue track. Select a
cast member and choose **Speaker voice**, or use that action in a dialogue line's
menu. Six controls set tone/off, word/letter rhythm, pitch, variation, blip length
and level. `DEFAULT` restores the character's voice. Sonic, Tails, Knuckles and ROM
object roles have distinct defaults. These noises come from the engine's PSG and
follow the actual text reveal in preview and export. They require no audio files.
Imported recordings remain separate audio cues; use voice tone `OFF` when a recording
should be the only performance. See the [speech settings and screenplay fields](stageplay-authoring.md#character-speech-noises).

Seeking is visual and silences owned voice tails. It never retriggers historical
cues or reconstructs a song's arbitrary sample position. Playback starts cues at
and after the chosen playhead. Start at frame zero for the complete soundtrack.
Stopping restores the borrowed soundtrack; imported voices share the existing
final PCM mix without replacing other installed audio sources.

The version-7 JSON document stores resource references, player and object keys,
puppet commands, world clear/removal commands and presentation cues.
Only the current document version is accepted. The unpublished format has no
migration or compatibility modes. ROM data
stays in the supplied-ROM pipeline. Authored `.ogfproject` sets, dialogue PNGs and audio stay
beside the document. Move the whole folder to preserve relative references;
Save As refuses to relocate a scene away from its asset root. Atomic saves
validate staged bytes before replacing the previous scene.

## Export a film

Choose **Export film**, save the scene and choose a new `.mkv` destination.
Export runs in an independent process with its own transport and ROM views.
It does not advance the open editor or native gameplay. FFmpeg must be available.
The export preserves every frame, uses lossless video and the final mixed PCM,
and reports failure instead of publishing a partial film. Existing outputs are
never overwritten. Export requires a 60 Hz audio source.

The [demo](../../../examples/stageplay-demo/README.md) includes a saved scene and
CLI command. `StageplayCaptureTool` accepts `--scene`, `--out`, optional `--game`
or `--rom`, additional `--s1-rom` / `--s2-rom` / `--s3k-rom`, `--scale` (1–8)
and `--ffmpeg`. Without `--rom`, the normal logical-ROM catalogue is used,
including supported composite images.

The editor supplies the child JVM's native-access and macOS first-thread flags.
For manual CLI use, add `--enable-native-access=ALL-UNNAMED`, and on macOS
`-XstartOnFirstThread`, before `-cp`. Linux automation can unset `DISPLAY` and
`WAYLAND_DISPLAY` and set `-Dopenggf.headless.gl=surfaceless-egl`; see
[headless rendering](../contributing/headless-testing.md). Offline synthesis uses
the no-device audio backend.

## Use the same scene in a mod

Package the scene and its relative voice/set/dialogue image assets under your mod resources.
During the existing `ModContext` registration transaction:

```java
StageplayPlayback.register(context, "opening", "scenes/opening.stageplay");
```

Handle its checked `IOException` in your entrypoint. From an owned object, use
injected services to trigger it:

```java
var director = services().gameService(StageplayControl.class);
director.play();
```

`skip()`, `cancel()`, `status()` and `frame()` expose completion without a callback
that could accidentally fire again during rewind. `registerOnEntry` requests
automatic playback at the first ready native frame. Only one Stageplay service
is installed per effective module; it rejects another controller that already
owns frame admission. The service and its rewind state share one owner-bound
instance, with the normal creator fault boundary.

The existing gameplay world stays held while the scene replaces its picture, and
completion resumes it. Scenes run their own native world in the same isolated
preview host used by the editors; they do not replace or advance the held gameplay
world. Poser actors retain explicitly authored manual choreography.

For main-player handover, the registration overload accepts the actor ID
and `StageplayNativeBinding.FinishMode`. The bound actor must match the native
game and character, with unit actor scale. Camera zoom is presentation-only. Handover
requires a freely controlled player: dead, hurt, super, object-supported and
object/ability-controlled states are rejected. Other actors remain detached.
The worker returns resolved value poses; the interactive player remains owned
by its existing visit-bound lease. Binding requires the current native set and
character. Cross-set and authored-level binding remain rejected before borrowing
the player, and a changed level visit revokes the old lease.

- `RESTORE_ENTRY` returns the player's entry state and camera.
- `KEEP_FINAL_POSITION` restores gameplay control at the final centre with zero
  velocity, airborne and detached from solid-object support. The next native tick
  resolves its environment; authors must choose a safe destination.
- Cancellation restores entry state. A changed level visit invalidates the old
  binding; it cannot write into the new level.

These are unpublished candidate APIs whose version follows the engine version.
Match the engine and SDK commit.
Branching dialogue, reusable shot libraries, rigs, lip sync and
multi-user editing are future work.

## Direct ROM objects

The **ROM object cast / clear** command is shared by the in-engine editor and
OpenGGF Editor. Populated sets retain their ROM placements. Select a loaded object
by portable layout identity, or add a role from the native object catalog. The
catalog covers every byte ID and source context, with explicit factory, art and
visual-readiness reasons. Released animals use their existing native factory.

Object **Free** runs its real native routine and world effects. Arena-bound bosses
show **Free: native arena position, anchor ignored** and the owning routine
citation. Free requires the scene to match its authored source game/zone/act.
A globally registered arena routine can still run at its native coordinates in
another source context; choose its original arena or a directed mode. The anchor
cannot relocate an arena-bound routine. Object **Puppet** moves a visual anchor toward destinations;
object **Pose** authors positions and mapping frames. Both suspend the whole graph's
native routines and contacts. Directing or removing a holder releases only the
players it acquired through its native player-control cleanup, without completing
the world event. Native routine phases and timers are preserved; Free resumes
them with the acquired players released. Child parts follow the captured native relationships;
pure ROM animation tables may run, while state-machine-only appearances use an
explicit held/authored frame. Native timer-owned bird flap cycles animate in
Puppet/Pose; walking animal frames tied to native vertical velocity keep their
held fallback. Per-part frame overrides make those fallbacks editable.
A constructor with no visible graph needs a Free initialization in its native set
before a later directed key can capture it; missing visuals fail clearly.

**Clear badniks**, **Clear objects** and individual removal are timed, undoable
commands. Their placement suppression persists within the shot; a fresh shot starts
with its own population. Players, attached character effects and rings are separate
owners. Object properties, mode keys, visual move/stop commands, part overrides and
clear/removal cues are saved in document version seven and screenplay version six.
See the [authoring reference](stageplay-authoring.md#object-cast-screenplay-6-document-7)
for exact fields and the coverage-report command.

## Plan a basic route

Choose **Plan collision route** in either editor, select a Puppet actor and a destination
centre. Player routes start at shot entry; object routes may also start at an
existing visible Puppet key after a Free initialization lead-in. Planning runs in its own disposable process;
Cancel retires that work. Review the result before applying it as one undoable edit.
A changed scene, actor/frame, ROM or project invalidates the result. Planning never
moves the interactive player or replaces the current preview.

For playable characters, choose **Starting action**, **Travel speed**, **Ending action**
and **Ending facing** in the route form. Select a setting row to cycle its choices.
Walk / Walk / Gentle Stop / Inherit are the defaults. Walk starts without a jump;
terrain-required jumps remain available with a strong search penalty. Jump starts
with a native jump after landing; Spindash appears only for an actor whose declared
abilities and native donor both support it, and uses a fixed 24-tick charge. Any
falling start lands first.

Walk holds a deliberately slow 1.5-pixel-per-tick stroll cap through controller
inputs, including braking on downhills. Run reads the actor's current native top
speed, including underwater/super changes. Gentle Stop releases early to coast
within a small arrival tolerance without skidding; Hard Stop countersteers; Overshoot
passes the point by a small distance, turns back and settles. Left or Right adds
one native directional tap after stopping; Inherit keeps the resulting facing.
A native launch or terrain that forces the Walk cap over must fail qualification
and suggest Run. Settings stay on the saved route and survive screenplay export,
replanning and action edits. A simple grounded route emits one MOVE_TO and a stop,
with no intermediate destination. Multi-waypoint routes remain deferred.

The helper searches actual native physics and collision
with bounded movement/jump attempts, then cold-replays the emitted commands in a
fresh LIVE world. `VERIFIED` means that replay reached the destination grounded
and stable without hurt, death or a wait timeout. The result uses existing
`MOVE_TO`, `JUMP`, `SPINDASH`, `STOP` and facing-tap intents; `WAIT` remains available for authored
synchronization. There is no route opcode or teleport.
Runtime moves remain attempts if the scene is changed afterward.

For walking object Puppets, enter a visual clearance footprint and speed. This is
a **visual terrain route**: the picture follows checked floor/headroom waypoints
while the object's AI, contacts and world effects stay suspended. The footprint
is authored clearance, not a claim about that object's native controller or
collision radii. Object routes use foreground collision, reject active background
collision and do not navigate moving object/platform solids. Fresh replay checks
the visual footprint against terrain on every tick. Slopes and steps up to 8 pixels
use explicit lift/cross/lower waypoints. Gaps, walls, larger steps, jumps, blocked
headroom and unsupported changing collision report failure; it does not find detours. Pose and Free objects must first be deliberately switched to
Puppet; the helper does not change their mode silently.

The basic helper has explicit frame, node, tick and time budgets.
It never inserts a key or resets an actor at an arbitrary frame. Search exhaustion or a verification failure does not prove a route
impossible. Existing conflicting intents, intervening cuts/keys/clear commands
and unsupported dynamic terrain are rejected with actionable diagnostics. Use
the [terminal reference](stageplay-authoring.md#route-helper) for the same workflow.
