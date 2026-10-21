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
4. Select the role, move the timeline to a frame, and choose **Set actor key**.
   Positions are world-space sprite centres. Choose a native animation, or a
   fixed mapping frame with **Pose**. `-1` selects animation playback.
5. Use **HOLD**, **LINEAR** or **SMOOTH** for the segment leaving a key. Movement
   keys retain animation phase; changing animation or pose starts a new phase.
   Camera keys use the world's top-left coordinate and a zoom factor.
6. Add captions and audio cues. Caption end frames are exclusive. Existing keys
   and cues can be revisited, edited or deleted; frame-zero actor/camera keys remain.
7. Save beside the scene's referenced assets. Play, scrub and reopen it in the
   other editor. Switching back to the level editor preserves Stageplay undo and
   unsaved changes. New/open/quit offer save, discard and cancel choices.

The built-in HUD has **Cast**, **Timeline** and **Commands** pages. Arrow keys or
the pad navigate; left/right step frames, Shift steps one second, Space plays,
K sets an actor key, and F1 opens commands. The desktop editor provides cast,
direction and timeline panels with the same operations. The form's PATH fields
can browse local files. Properties apply together when their final action is chosen.

**Record directed take** captures WASD movement at two world pixels per tick.
Space or Stop finishes it; the entire take undoes as one edit. It is a baked visual
performance, not a controller-input recording or a simulation replay. Native
input recordings remain the engine recording system's separate format.

Scenes run at 60 frames per second, up to ten minutes, with up to 32 actors.
Use **Scene / set** to change duration, output dimensions or the source set.
Delete keys and cues outside a shorter duration before shortening. Preview fits
the authored picture into the game viewport; film output uses its exact dimensions.

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

The version-1 JSON document stores resource references, keys and cues. ROM data
stays in the supplied-ROM pipeline. Authored `.ogfproject` sets and audio stay
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

## Use the same scene in a mod

Package the scene and its relative voice/set assets under your mod resources.
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

By default the cast is detached: native physics, world clocks and objects are
held, the staged scene replaces their picture, and completion resumes the native
world. Sets depict their loaded terrain/background; native objects, rings,
water programs and evolving zone events are not simulated in the performance.

For actual main-player direction, the registration overload accepts the actor ID
and `StageplayNativeBinding.FinishMode`. The bound actor must match the native
game and character, with unit scale and unit camera zoom. The first version
requires a freely controlled player: dead, hurt, super, object-supported and
object/ability-controlled states are rejected. Other actors remain detached.

- `RESTORE_ENTRY` returns the player's entry state and camera.
- `KEEP_FINAL_POSITION` restores gameplay control at the final centre with zero
  velocity, airborne and detached from solid-object support. The next native tick
  resolves its environment; authors must choose a safe destination.
- Cancellation restores entry state. A changed level visit invalidates the old
  binding; it cannot write into the new level.

These are unpublished **0.7.0 candidate** APIs. Match the engine and SDK commit.
Branching dialogue, reusable shot libraries, rigs, lip sync, simulation takes and
multi-user editing are future work, not version-1 controls.
