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
- Author a version-three screenplay JSON for live worlds (version two remains supported). New cast members default to physical puppets;
  use `puppet:false` for Poser mode and deliberate manual choreography. Version-one recipes remain manual. Each shot has its own duration, set, camera
  and actor tracks. Local frame zero anchors each present actor and camera. Missing
  cast tracks are hidden for that shot. The compiler creates every scene-boundary
  key and preserves explicit cross-game art/audio references. It never interpolates
  across a set switch. An incoming transition straddles the boundary, so leave enough
  room on both sides and avoid dialogue under its opaque midpoint.
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
then native terrain collision controls contact during movement. There is no automatic
pathfinding or obstacle jump: a destination is an attempt, and `BLOCKED` must be
inspected rather than hidden with a teleport. Check actual arrival and grounded state
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

Camera zoom now holds until its next key; the current renderer does not animate
zoom. Use zoom one or held integer pixel scales. Create variation with pans, composition and shot cuts. Do not animate
fractional nearest-neighbour zoom: changing pixel sizes creates stepping and
shimmer in sprites and scenery. Hold the same zoom during a pan and use a shot cut to change integer framing.
Imported fractional values are retained for compatibility; choose integer framing
for newly authored shots.

Animated fractional zoom is appropriate only with a smooth rendering implementation
that has been reviewed on moving footage at the intended output size and cadence.
Review sprites, scenery and captions through the entire move, not just its endpoints.
A new smoothing implementation is separate engine work; existing rendering options
must be judged by their observed motion, not the apparent smoothness of camera keys.

## Boundaries that affect directing

TERRAIN_ONLY sets use detached ROM resources: their scene switches do not run native gameplay loads,
world-object AI or zone-event simulations. LIVE sets opt into the isolated native world described below. `TERRAIN_ONLY` Puppets run native player
physics against detached static terrain; that does not prove full-world route completion
or ROM parity. Camera shake and zoom affect presentation, not collision or
native camera state. Native handover requires the same supported native set and resumes
unshaken positions; cross-game scenery belongs to staged playback. State these limits
when they affect the requested story. Imported voices are bounded relative WAV/Ogg assets;
no voice service or fabricated licensing claim is part of this workflow.

## Live-world route evidence

Opt into `worldPolicy:LIVE` explicitly; legacy documents stay terrain-only. Use the
`level` command to inspect stable ROM placements and rings, and `catalog` for native
capabilities. Read the live schema in the authoring guide before using object
targets, WAIT conditions, abilities or camera following. Unsupported donors must
fail validation; S2 Tails cannot acquire flight by posing.

Use actual `sample.world` state/events to establish moving-platform contact, ring
collection, hazard hurt/recovery and native airborne abilities. A picture of a
platform, counter value, animation or compile pass is insufficient. Verify native
contact/displacement, once-only ring disappearance, ring loss and recovery, ability
state/timer and landing. Compare backward seek + forward replay across those events.
Keep actors in the native activation window; the cinematic camera does not create
additional gameplay activation regions. Describe unsupported separated casts honestly.

LIVE simulate/capture owns an offline gameplay session and delegates to
LevelFrameStep. Editor preview uses a persistent offscreen worker, with the same
world sampling and rendering, with no-device native SFX admitted once by forward playback. Seeking stays silent. Never open a new session over the user's active
gameplay/editor world. Do not tween placed objects, force runtime positions or
velocities, assign pickup counters, or substitute a hurt pose. Only initial shot
placement may set an actor's centre. Save source scenes in the repo and durable
state/movie evidence in the task's external capture directory.

LIVE film views omit gameplay HUD/debug overlays and reveal fresh terrain without
advancing actors through the native load palette fade. Author shot transitions
for the intended reveal. LIVE camera controls show the resolved follow view;
Capture key / Record stores authored framing and clears following at those keys.
The camera form keeps follow targets and applies position edits as follow offsets.

After vertical pans or follow-target changes, compare visible feet and support
objects with the rendered foreground and its occlusion mask. Grounded state,
visible body pixels and complete decoding can all pass while scenery uses a
different camera origin. Check contact before and after the move; distinguish
transparent native floor art from a displaced foreground. LIVE backgrounds retain
the native gameplay camera phase, including intentionally stationary backgrounds;
arbitrary-view background parallax is not recomputed. Do not alter physics or
force the background to move like foreground terrain to disguise a rendering fault.

Place explicit `ROM_MUSIC` cues at each shot's local frame zero using verified
`catalog` IDs. Preview's worker silences automatic level music because the parent
owns authored cues; exports use the same cues and keep native SFX. A soundtrack
heard only in a capture is not evidence of matching preview playback.
