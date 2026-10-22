# Direct a scene with OpenGGF Stageplay

Stageplay directs supplied ROM characters and worlds, or saved editor sets, for
linear sprite films, animatics and mod cutscenes. Both the built-in editor HUD and
[OpenGGF Editor](https://github.com/OpenGGF/OpenGGF-Editor) use the engine's same
scene document, editing commands and evaluator. A `.stageplay` file opens in either.

## Make a first scene

1. Open a level project, or enter the [level editor](level-editor.md) from gameplay.
2. Press **F4** or choose **Stageplay workspace**. A new scene starts at the current
   camera position. A saved, unchanged level is linked; unsaved terrain is copied
   to a new `projects/stageplay-<id>/set.ogfproject`, preserving the level editor's
   original document and savepoint.
3. Choose **Add actor**, name the role and select its ROM (`s1`, `s2`, `s3k`) and
   character. The first version supports Sonic, S2/S3K Tails and S3K Knuckles.
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
where needed. New scenes use one shared native world. **LIVE** retains its placed objects and
rings; **TERRAIN_ONLY** starts that same world with those populations cleared.
Both choices use the same actor, camera and handover capabilities. Strictly
migrated older scenes retain their original compatibility simulation. [The authoring reference](stageplay-authoring.md#puppet-and-poser-modes)
describes command fields, compatibility and the windowless simulation tool.

**Record directed take** stores physical movement intents for Puppet actors. Poser mode
retains baked visual movement at two world pixels per tick. Space or Stop finishes
the take, and the entire take undoes as one edit. Native input recordings remain
the engine recording system's separate format.

Scenes run at 60 frames per second, up to ten minutes, with up to 32 actors.
Use **Scene / set** to change duration, output dimensions or the source set.
Delete keys and cues outside a shorter duration before shortening. Preview fits
the authored picture into the game viewport; film output uses its exact dimensions.

## Direct a live world

In **Scene / set**, choose **LIVE** for the loaded population or **TERRAIN_ONLY**
to clear initial placements and rings. Choose a ROM zone/act or link a saved
`.ogfproject` set. Both choices use the native world owner. Each shot creates a fresh
native world with independent Puppet inputs, moving objects, rings, hazards,
collision, clocks and rewind. Each performer uses its supplied game's native movement rules and ROM art over
the shared terrain and collision owners. The document allows 32 actors, subject
to the available native pattern-bank capacity; missing art fails explicitly.
Versions 1–5 retain their original world behavior under visible compatibility
policies when reopened. Selecting LIVE or TERRAIN_ONLY upgrades a shot explicitly.

The selected actor's actions include **WAIT**, **RELEASE**, **CROUCH**, **LOOK_UP**
and **ROLL**. S3K Tails also has **FLY_TO** and **HOVER**; S3K Knuckles has
**GLIDE_TO**. Native physics owns takeoff, stamina, damage, recovery and landing.
Move/fly/glide can target an actual placement using its layout index, expected
object ID/subtype and offset. Use the offline tool's **level** command to discover
those values from the loaded ROM. A wait can gate later commands until arrival,
object support, landing, hurt, recovery or ring collection, with a finite timeout.
Inspect actual outcomes; a destination does not supply general pathfinding.

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

Current native films support **160–1920 × 112–1080** output dimensions and zoom
in both contents policies. The completed opaque world is resampled separately
from captions and dialogue. Native viewport tiles retain the engine's 224-line
rendering contract; physics and native camera dimensions remain unchanged. The
source view is bounded to 4096 pixels per side and 8 MiPixels in total, so extreme
combinations of output size and zoom fail preflight with an actionable message.
Legacy LIVE shots retain their original 224-high, fixed-1x presentation until upgraded. Keep the cast
within the native camera's object activation window. A widely separated actor
fails with an actionable route message; use closer blocking or a new shot.
The [examples](../../../examples/stageplay-demo/README.md) include platform
transfers, flight/glide, rings/hurt and the 60-second **One More Take** rehearsal.

## Direct the camera and change scenes

Press **C** for camera directing mode. WASD/arrows pan, Q/E zoom in current native
shots, **K** places a
camera key and **R** records a camera take; Space finishes. A take is one undo edit.
Zoom holds until the next key. Numeric camera properties accept fractional zoom
in current native scenes; only the completed world picture is filtered. Pans retain their chosen interpolation.
The draft camera stays separate from the evaluated shake, so saving a key never
accidentally bakes earthquake offsets into it. Switch back to actor mode to direct
cast movement. LIVE camera keys can follow one actor or a group: set **follow**
to comma-separated actor IDs, then tune dead zones, look-ahead, smoothing and
offsets. Follow uses actual native positions. Current native shots retain authored zoom;
legacy LIVE shots remain at zoom one. The directed
foreground, native sprites and priority mask share one view. Background scroll,
deformation and effects retain the gameplay camera’s native phase; arbitrary-view
background parallax is not recomputed. Death Egg’s stationary background is intentional.

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
opening editor menus. Keep the screenplay as the programmatic source; either editor
can subsequently refine its generated canonical scene.

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

Seeking is visual and silences owned voice tails. It never retriggers historical
cues or reconstructs a song's arbitrary sample position. Playback starts cues at
and after the chosen playhead. Start at frame zero for the complete soundtrack.
Stopping restores the borrowed soundtrack; imported voices share the existing
final PCM mix without replacing other installed audio sources.

The version-6 JSON document stores resource references, keys, puppet commands and cues.
Versions 1–5 migrate strictly: manual choreography, detached physics and original
LIVE execution retain their behavior, including their previous presentation limits. ROM data
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
never overwritten. The first version requires a 60 Hz audio source.

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
completion resumes it. Current LIVE and TERRAIN_ONLY scenes run their own
native world in the same isolated preview host used by the editors; they do not
replace or advance the held gameplay world. Older detached scenes retain their
private native controllers and original authored choreography.

For main-player handover in either native contents policy, the registration overload accepts the actor ID
and `StageplayNativeBinding.FinishMode`. The bound actor must match the native
game and character, with unit actor scale. Camera zoom is presentation-only. The first version
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

These are unpublished **0.7.0 candidate** APIs. Match the engine and SDK commit.
Branching dialogue, reusable shot libraries, rigs, lip sync and
multi-user editing are future work, not version-1 controls.
