---
name: stageplay-authoring
description: Create or revise OpenGGF Stageplay cutscenes and animatics with shot-local screenplays, ROM resource discovery, validation and offline rendering. Use for agent-authored directing; no engine menus or desktop input needed.
---

# Stageplay authoring

Use `tools/stageplay/stageplay.py` from the engine checkout. It drives the production
Java directing compiler and renderer, with one JSON result on stdout and diagnostics
on stderr. Read [the command and screenplay reference](../../../docs/guide/creating/stageplay-authoring.md)
for field names, bounds and examples. The canonical `.stageplay` file opens in both
editors and can be registered by a mod.

## Discover, direct, render

- Start from the user's intended story and duration. Write short shot-local beats,
  leave dialogue readable, and give camera moves and transitions a narrative purpose.
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
  timing/samples before selecting frames. Inspect representative moving segments,
  character/accessory poses, caption readability and every transition. A valid codec or
  one PNG is insufficient. Check FFprobe cadence, dimensions and audio, then decode the
  full movie with FFmpeg's `-xerror` option. Record exact source revision and commands.
  Save only the screenplay, canonical scene, drivers and concise provenance in source control.

For an explicitly requested promo, use [gameplay-highlights](../gameplay-highlights/SKILL.md),
including its production-source archive. A scene-authoring request alone does not imply
publishing a promotional reel or sending messages.

## Boundaries that affect directing

Staged sets use detached ROM resources: scene switches do not run native gameplay loads,
AI or zone-event simulations. Camera shake and zoom affect presentation, not collision or
native camera state. Native handover requires the same supported native set and resumes
unshaken positions; cross-game scenery belongs to staged playback. State these limits
when they affect the requested story. Imported voices are bounded relative WAV/Ogg assets;
no voice service or fabricated licensing claim is part of this workflow.
