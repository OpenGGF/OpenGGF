---
name: stageplay-authoring
description: Use when creating or revising OpenGGF Stageplay cutscenes, animatics, choreographed stories or staged promotional footage, including agent-authored directing without engine menus or desktop input.
---

# Stageplay authoring

Use `tools/stageplay/stageplay.py` from the engine checkout. It drives the production
Java directing compiler and renderer, with one JSON result on stdout and diagnostics
on stderr. Read [the command and screenplay reference](../../../docs/guide/creating/stageplay-authoring.md)
for field names, bounds and examples. The canonical `.stageplay` file opens in both
editors and can be registered by a mod.

## Discover, direct, render

- Start from the user's intended story and duration. Write short shot-local beats,
  with purposeful action and reactions. Leave dialogue readable, identify its speaker,
  and give camera moves and transitions a narrative purpose. Apply the performance
  and pixel-motion guidance below before choosing a shot's keys.
  The existing `examples/stageplay-demo` shows runtime registration; the location
  remote screenplay beside it is an editable production example.
- Build the current engine once with the launcher `--build` option, and rebuild after
  source changes. Without it the launcher uses the last packaged engine; a successful
  run of an older jar does not validate current sources. Java 25 and FFmpeg are required
  for a movie. `help` lists commands; `init --out PATH` produces a starter screenplay.
- Discover existing ROM paths. `catalog --game s2 --rom PATH --character tails` reports
  ROM SHA-1, zones, starting coordinates, music IDs and available animation scripts.
  Use `--zone N --act N` to test a particular detached set and obtain its bounds.
  Registry presence alone does not mean that a detached set can be decoded.
  All frame/zone/act numbers are zero-based. Never invent art or copy ROMs to satisfy
  a command. Sprite positions use the sprite origin, not its top-left corner.
- Author current version-six screenplay JSON, which compiles to a version-seven
  canonical document. These unpublished formats accept only the current version;
  there are no migration or compatibility modes. Candidate API versions follow the
  engine version. Cast members default to physical puppets; use `puppet:false`
  for Poser mode and deliberate manual choreography. Each shot has its own duration, set, camera
  and actor tracks. Local frame zero anchors each present actor and camera. Missing
  cast tracks are hidden for that shot. The compiler creates every scene-boundary
  key and preserves explicit cross-game art/audio references. It never interpolates
  across a set switch. An incoming transition straddles the boundary, so leave enough
  room on both sides and avoid dialogue under its opaque midpoint.
- After editor refinements, use `decompile --scene PATH --out PATH` or either editor's
  **Export screenplay**, then recompile version-six JSON to preserve every supported
  canonical field and exact ID/order. Import starts an unsaved canonical scene; export
  preserves save/undo state. Decompile beside the scene when assets are relative.
  Do not hand-edit generated IDs or split cross-shot intervals to fit a shot: version
  six supports literal IDs, typed exact `order` permutations and whole intervals.
  Original source comments, formatting and shot names are not reconstructed.
- Compile beside the screenplay when it uses relative voice/level/PNG decoration or portrait assets. The compiler
  rejects unknown/duplicate fields and invalid timing and never overwrites outputs.
  Use a new take filename for revisions. `validate` checks structure; `inspect --frame N`
  reports a deterministic sample including active set, camera shake and transition.
  Resource availability is checked by the catalog, simulator and renderer, not by structural validation.
  Run `simulate --scene PATH --out state.jsonl --s2-rom PATH` (plus other required ROMs)
  before rendering physical actors. Inspect actual centres, velocities, grounded state
  and arrival/blocked statuses; this requires no graphics context or audio device.
- Render with explicit supplied-ROM flags. On Linux the launcher removes desktop display
  variables and uses surfaceless EGL. The capture host installs offline audio before boot;
  never substitute a live device backend or launch interactive Engine to get pixels.
  See [headless rendering](../headless-rendering/SKILL.md) for platform prerequisites.
  macOS/Windows use an owned hidden native window and are not display-free.
- Keep durable movies and previews outside the repository, under one task directory.
  Use `simulate` JSONL for native puppet positions/status and `timeline` for pure authored
  state (physical actors are `UNRESOLVED`). Keep these distinct from full-world gameplay
  state CSV. Read the state and screenplay before selecting frames; use every-frame
  output when auditing all cues.
  Watch the intended footage at playback speed, including entrances, exits, reactions,
  speaking turns, camera moves and every transition. Check character/accessory animation,
  speaker identity, caption reading time and pixel stability through the whole move.
  Stills help inspect composition; they cannot reveal zoom shimmer, frozen performances
  or poor timing. A valid codec or one PNG is insufficient. Check FFprobe cadence,
  dimensions and audio, then decode the full movie with FFmpeg's `-xerror` option. Record exact source revision and commands.
  Save only the screenplay, canonical scene, drivers and concise provenance in source control.

For an explicitly requested promo, use [gameplay-highlights](../gameplay-highlights/SKILL.md),
including its production-source archive. A scene-authoring request alone does not imply
publishing a promotional reel or sending messages.

## Direct performance and readable dialogue

Give each beat a visible action and consequence: a character enters, approaches a
partner, turns toward an object, speaks, reacts, or leaves. Select the actions that
serve the story; no fixed combination is required in every shot. For physical actors,
use `MOVE_TO` with destination/urgency, `STOP`, and `JUMP` with button-hold duration.
Native acceleration, animation, slopes, gravity, collision and landing own the motion.
Do not keyframe a parabola or force velocity to make a physical jump fit a beat.
Time other characters' reactions to the observed action or line. Use intentional
pauses for emphasis or reading time. Avoid leaving the entire cast in idle loops
through a promo whose story depends on their interaction.

Place a physical spawn centre near the floor; floor snapping defaults on at spawn,
then native terrain collision controls contact during movement. A MOVE_TO destination
is an attempt, and `BLOCKED` must be inspected rather than hidden with a teleport.
For basic player shot-entry planning, use `route --scene PATH --actor ID --frame N --x X
--y Y --out NEW.stageplay` with supplied ROM flags. The bounded helper emits
existing movement/jump/wait intents and requires cold LIVE replay before VERIFIED;
search failure is not proof of impossibility. Object Puppets require explicit
visual footprint/speed and report VISUAL_TERRAIN, with native AI still suspended.
Walking object routes follow slopes and steps up to eight pixels with explicit
lift/cross/lower MOVE_TO corners. Gaps, walls, larger steps, insufficient headroom
and changing collision fail explicitly; no detour or jump planner is provided.
ROM/asset content fingerprints remain necessary because an unchanged scene document
can refer to edited terrain or replaced ROM bytes; Apply checks those inputs again.
An object route can also start at an existing visible Puppet key after an explicit
Free initialization lead-in; the worker replays that prefix, never inserts a key
or silently initializes a directed graph.
In either editor, select the player or walking object in Cast and use Plan collision
route. Close the menu to review the observed path before Apply: cyan ground, gold
air/takeoff chevrons, start square, goal diamond and red failed endpoint. Route
state, colour/marker keys and plain-language explanations stay in the HUD workbench
or desktop Direction inspector, with a short status line. No route text covers the
picture; explanations wrap rather than cutting words.
Do not replace missing samples or a failed prefix with an invented goal line. Apply
retains the plan during that shot's playback, with a white actual-player-centre cross.
Camera framing transforms the overlay; it never enters the scene or exported film.
Old plans hide after scene/root changes; changed ROM/assets require replanning.
Capture editor evidence through its frontend, and separately verify clean film output.
Decompile the routed canonical scene to return it to screenplay. Both editors
expose the same helper; review diagnostics and actual simulation before rendering. Check actual arrival and grounded state
in simulation, then revise goals/timing as needed. Shot boundaries reset actors and
commands. Interior physical keys may change visibility only. Poser mode retains
position, facing, animation and pose keys for deliberate nonphysical staging.
Confirm that accessories such as Tails' tails follow actual motion and facing.

Make the speaker identifiable on the first readable frame of each line. Prefer
the dialogue track with an explicit actor/offscreen speaker, a nameplate and optional
pointer, supported by framing, facing and performance; color alone is not sufficient
identification. Use captions for titles and overlays. Reusable `dialogueTypers` and
`dialogueSkins` support cadence, pauses, custom reveal schedules and creator PNG
decorations. Package their relative assets with the scene, and reject overflow or
missing resources before rendering. Scrubbing/reveal sampling is silent; use audio
cues for deliberately timed sound. The existing engine font coverage still applies. Keep labels consistent
when several roles share the same stock sprite. Time captions and voice cues with
the speaking turn, leave enough time to read, and avoid obscuring the line beneath
a transition's opaque midpoint. Listener reactions help the exchange read as action.

## Keep pixel-art camera motion stable

Current native worlds animate camera position and zoom with HOLD / LINEAR /
SMOOTH easing. The bounded world-compositing pass filters the completed opaque
world picture, then draws text separately. Use held integer framing when the
shot calls for pixel-stable pauses; review every animated zoom in motion at the
intended output size and cadence. A larger export scale cannot repair an alias
already present in the authored frame. Framing centres interpolate with zoom, so
centred pushes keep their centre fixed. Fractional camera origins survive composition;
the private world framebuffer is reused and grows only when needed. Tall views
extend the bottom native pattern band; review water, clouds and moving tall views
for seams or stretched scanlines.

## Boundaries that affect directing

Every set uses the same native LIVE session. A terrain-only picture uses an ordinary
`CLEAR_OBJECTS` command at shot frame zero. It clears object roots and their graphs;
rings remain separately owned, and native events keep running. Zoom, sizes, native
binding/hand-back, team limits and activation windows use the same rules everywhere. Neither authored shot spawns nor isolated simulation alone
proves a continuous cold route or ROM parity. Camera shake and zoom affect presentation, not collision or
native camera state. Native handover requires the same supported native set and resumes
unshaken positions; cross-game scenery belongs to staged playback. State these limits
when they affect the requested story. Imported voices are bounded relative WAV/Ogg assets;
no voice service or fabricated licensing claim is part of this workflow.

## Live-world route evidence

Every screenplay uses a native world; author object clears as timed
world commands. Use the
`level` command to inspect stable ROM placements and rings, and `catalog` for native
capabilities. Read the live schema in the authoring guide before using object
targets, WAIT conditions, abilities or camera following. Unsupported donors must
fail validation; S2 Tails cannot acquire flight by posing.

Use actual `sample.world` state/events to establish moving-platform contact, ring
collection, hazard hurt/recovery and native airborne abilities. A picture of a
platform, counter value, animation or compile pass is insufficient. Verify native
contact/displacement, once-only ring disappearance, ring loss and recovery, ability
state/timer and landing. Compare backward seek + forward replay across those events.
Current worlds activate separate native-sized windows around performers; distant
cast members can share a shot without activating the entire gap. They share the
native object slot budget. An exhaustion message requires clearing unnecessary
objects or splitting the shot. Native damage/destruction remains active.

LIVE simulate/capture owns an offline gameplay session and delegates to
LevelFrameStep. Editor preview uses a persistent offscreen worker, with the same
world sampling and rendering, exact logical-ROM window/composite transport and confined authored-set assets, with no-device native SFX admitted once by forward playback. Seeking stays silent. Never open a new session over the user's active
gameplay/editor world. Do not tween placed objects, force runtime positions or
velocities, assign pickup counters, or substitute a hurt pose. Only initial shot
placement may set an actor's centre. Save source scenes in the repo and durable
state/movie evidence in the task's external capture directory.

LIVE film views omit gameplay HUD/debug overlays and reveal fresh terrain without
advancing actors through the native load palette fade. Author shot transitions
for the intended reveal. LIVE camera controls show evaluated framing clamped to the loaded level camera
envelope at the current viewport and zoom, including authored keys and shake;
authored keys are preserved. Both editors show the resolved view;
Capture key / Record stores authored framing and clears following at those keys.
The camera form keeps follow targets and applies position edits as follow offsets.

After vertical pans or follow-target changes, compare visible feet and support
objects with the rendered foreground and its occlusion mask. Grounded state,
visible body pixels and complete decoding can all pass while scenery uses a
different camera origin. Check contact before and after the move; distinguish
transparent native floor art from a displaced foreground. Current backgrounds
compose their owning handler's immutable native phase at the directed camera,
including parallax, line/column deformation and deliberately stationary regions.
They never retick a native handler while drawing. Review AIZ pans and S3K DEZ
stationary framing; do not alter physics or force uniform foreground-speed
background movement to disguise a rendering fault.

Place explicit `ROM_MUSIC` cues at each shot's local frame zero using verified
`catalog` IDs. Preview's worker silences automatic level music because the parent
owns authored cues; exports use the same cues and keep native SFX. A soundtrack
heard only in a capture is not evidence of matching preview playback.

## Complete-cast continuation decisions

Free means the object's actual native routine. Arena-bound bosses may ignore
the placed anchor: do not translate their native coordinates or invent per-object
spatial adapters. Report and display **Free: native arena position, anchor ignored**
with the owning initialization routine. Reject incompatible native sets when their
factory/context cannot support the object. Directed Puppet/Pose relocation must
include the parent's native child relationships; it must not masquerade as relocated
Free collision or arena behavior. Coverage distinguishes independent factories,
parent-created parts, invisible controllers, unavailable art and unsupported contexts.


## ROM object cast and native arena restrictions

Use the shared ROM object cast / clear commands in either editor, or screenplay
v6 `objectCast`, shot-local `objects`, `objectActions` and `worldCommands`.
Object placement identity includes game, zone, act, layout index, object ID and
subtype; never use a runtime SST slot or reuse an identity in another act.
Consult `object-coverage --out PATH --format json` for every native byte ID and
source context; registry presence, loaded ROM art and complete visual graphs are
separate checks. Preserve explicit exclusions and owning routine citations. Append
reviewed exact-context samples with `--observations reviewed.json`; a successful
sample never certifies other subtypes, contexts, frames or animal variants.

Free alone runs native AI and world effects. Arena-bound routines retain their
native coordinates: display **Free: native arena position, anchor ignored**.
Do not author spatial adapters or claim that moving the anchor moves such a boss.
Free source context must match the set. A globally registered arena routine may
still run at its native coordinates in another source context; this is not a
relocation. Prefer its original arena or a directed visual. Puppet and Pose suspend the whole native
graph, including child routines and contacts; their motion is visual direction,
not a controller that arbitrary objects do not have. Directing/removing a holder
releases its acquired players through native player-only cleanup. It must not
complete the cutscene/world event or reset its native routine, timers or transport
progress. Only acquired-player ownership and native release cooldowns change;
Free resumes the frozen routine with players released. Helper and queued-pose
ownership must rewind. Pure ROM animation scripts and native timer-owned bird flap cycles
run independently in Puppet/Pose. Walking animal frames tied to native vertical
velocity retain an explicit held fallback; direction does not invent hop physics. Otherwise use the held/native mapping frame or explicit
per-part frames and record the state-machine appearance exclusion. A newly cast
object whose constructor has no visible graph must first appear as Free in its
native set before a later directed key captures it; do not run its hidden AI in
the directed scene to manufacture missing parts.

Review multipart bosses in moving footage, including a known held-frame fallback.
Check declared appearance exclusions in the parts menu. Active unsupported SAT
mask/control graphs must fail explicitly, never omit the mask while keeping the
body. Use per-part editing for long override lists; preserve stable child creation
paths across rewind and keep the native cast order when editing a role.
Clear badniks removes semantic badnik roots; Clear objects removes world object
roots and their graphs. Players, player-attached effects and ring populations are
separate owners. Keep clear/removal cues in the source instead of editing the ROM
layout or relying on a runtime slot. Full continuously isolated native graphs for
directed bosses remain deferred.
