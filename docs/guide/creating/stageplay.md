# Direct a scene with OpenGGF Stageplay

Stageplay turns supplied ROM art and saved editor levels into sets for linear
sprite films, animatics and mod cutscenes. Both the built-in editor HUD and
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
where needed. Native objects, moving platforms and zone events are not part of the
staged terrain simulation. [The authoring reference](stageplay-authoring.md#puppet-and-poser-modes)
describes command fields, compatibility and the windowless simulation tool.

**Record directed take** stores physical movement intents for Puppet actors. Poser mode
retains baked visual movement at two world pixels per tick. Space or Stop finishes
the take, and the entire take undoes as one edit. Native input recordings remain
the engine recording system's separate format.

Scenes run at 60 frames per second, up to ten minutes, with up to 32 actors.
Use **Scene / set** to change duration, output dimensions or the source set.
Delete keys and cues outside a shorter duration before shortening. Preview fits
the authored picture into the game viewport; film output uses its exact dimensions.

## Direct the camera and change scenes

Press **C** for camera directing mode. WASD/arrows pan, Q/E zoom, **K** places a
camera key and **R** records a camera take; Space finishes. A take is one undo edit.
Zoom uses held integer framing; it changes at a key rather than animating through
fractional nearest-neighbour scales. Pans retain their chosen interpolation.
The draft camera stays separate from the evaluated shake, so saving a key never
accidentally bakes earthquake offsets into it. Switch back to actor mode to direct
cast movement.

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

The version-4 JSON document stores resource references, keys, puppet commands and cues.
Versions 1–3 migrate strictly and retain their manual choreography. ROM data
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

The staged cast is detached from the live world. Puppet actors use private native
player controllers against the loaded terrain; Poser actors use authored keys.
The live world's clocks, players and objects remain held while the scene replaces
its picture, and completion resumes that world. Native objects, rings, water
programs and evolving zone events are not simulated in the staged performance.

For actual main-player direction, the registration overload accepts the actor ID
and `StageplayNativeBinding.FinishMode`. The bound actor must match the native
game and character, with unit actor scale. Camera zoom is presentation-only. The first version
requires a freely controlled player: dead, hurt, super, object-supported and
object/ability-controlled states are rejected. Other actors remain detached.

- `RESTORE_ENTRY` returns the player's entry state and camera.
- `KEEP_FINAL_POSITION` restores gameplay control at the final centre with zero
  velocity, airborne and detached from solid-object support. The next native tick
  resolves its environment; authors must choose a safe destination.
- Cancellation restores entry state. A changed level visit invalidates the old
  binding; it cannot write into the new level.

These are unpublished **0.7.0 candidate** APIs. Match the engine and SDK commit.
Branching dialogue, reusable shot libraries, rigs, lip sync and
multi-user editing are future work, not version-1 controls.
