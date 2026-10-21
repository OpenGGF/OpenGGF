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
- Author a version-one screenplay JSON. Each shot has its own duration, set, camera
  and actor tracks. Local frame zero anchors each present actor and camera. Missing
  cast tracks are hidden for that shot. The compiler creates every scene-boundary
  key and preserves explicit cross-game art/audio references. It never interpolates
  across a set switch. An incoming transition straddles the boundary, so leave enough
  room on both sides and avoid dialogue under its opaque midpoint.
- Compile beside the screenplay when it uses relative voice/level assets. The compiler
  rejects unknown/duplicate fields and invalid timing and never overwrites outputs.
  Use a new take filename for revisions. `validate` checks structure; `inspect --frame N`
  reports a deterministic sample including active set, camera shake and transition.
  Resource availability is checked by the catalog and renderer, not by structural validation.
- Render with explicit supplied-ROM flags. On Linux the launcher removes desktop display
  variables and uses surfaceless EGL. The capture host installs offline audio before boot;
  never substitute a live device backend or launch interactive Engine to get pixels.
  See [headless rendering](../headless-rendering/SKILL.md) for platform prerequisites.
  macOS/Windows use an owned hidden native window and are not display-free.
- Keep durable movies and previews outside the repository, under one task directory.
  Export `timeline --scene PATH --out state.jsonl` and read its evaluated
  timing/samples before selecting frames. This is authored directing state and exact-frame
  cues, not measured gameplay state CSV; use every-frame output when auditing all cues.
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
serve the story; no fixed combination is required in every shot. Match translation
to an appropriate catalogued walk/run animation, settle into a deliberate pose,
and time the other characters' reactions to the action or line. Use intentional
pauses for emphasis or reading time. Avoid leaving the entire cast in idle loops
through a promo whose story depends on their interaction.

Stageplay does not supply gameplay AI or collision to fill in missing performance.
Author entrances/exits with positions and visibility keys, facing and eyelines,
pose changes and reactions explicitly. Check where the sprite origin and feet land
against the set. Confirm that accessories such as Tails' tails follow the chosen
motion and facing throughout the shot.

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

Staged sets use detached ROM resources: scene switches do not run native gameplay loads,
AI or zone-event simulations. Camera shake and zoom affect presentation, not collision or
native camera state. Native handover requires the same supported native set and resumes
unshaken positions; cross-game scenery belongs to staged playback. State these limits
when they affect the requested story. Imported voices are bounded relative WAV/Ogg assets;
no voice service or fabricated licensing claim is part of this workflow.
