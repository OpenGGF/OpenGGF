# OpenGGF mod creator handbook

OpenGGF's mod workflow is source-first and reproducible: author files, convert them
with `ggfmod`, validate/package a jar, then enable it in the Mod Manager and restart.
Choose the smallest quickstart that matches your goal; they are ordered by typical
effort. Mod API 0.7 includes exclusive fresh-game insertion plus destination-scoped,
launch-only teams, deterministic input filters, and row-only HUD profiles; the
[content-mod reference](content-mods.md#choose-a-fresh-game-destination-and-presentation)
defines their ordering, replay, persistence, width, stock-default, and owner-fault
contracts.

## Native builds vs. the JVM jar

Code-bearing mods (objects, characters, zones, and standalone games) require the
**JVM jar**. They are loaded at runtime by the engine's mod classloader, which a
GraalVM native-image binary cannot use under closed-world AOT. Native builds do
not load these mods: the Mod Manager marks them `UNSUPPORTED` and refuses to
enable them, and a boot notice lists any that were enabled. **Data-only music
packs and reskins are unaffected and work on native builds.** To use code-bearing
mods, run `OpenGGF-<ver>-jar-with-dependencies.jar` (or the universal jar).

1. [Music pack](quickstarts/music-pack.md)
2. [Data-only art reskin](quickstarts/reskin.md)
3. [Object or badnik](quickstarts/object.md)
4. [Sonic 2 zone](quickstarts/zone.md)
5. [Playable character](quickstarts/character.md)
6. [Standalone game](quickstarts/standalone.md)

## Follow-along guides

- [ROM-art remix](guides/rom-art-remix.md) — source-first tour of the
  `sample-rom-art-remix` gallery sample: bounded Sonic 2 art, mapping, and DPLC
  intake, launch-memory materialization, decoded-pattern probes, and rewind.
- [Native-Tails Flappy](guides/native-tails-flappy.md) — build-along tour of the
  `sample-flappy` gallery sample: an anchorless S3K fresh-game destination, scoped
  Tails/input/HUD policies, fixed camera, and rewind-stable recycling pipes.
- [Standalone platformer](guides/standalone-platformer.md) — build-along tour of the
  `sample-platformer` gallery sample: a no-ROM standalone game with a Tiled-authored
  level, an original character with a double jump, a patrolling badnik, and a spring
  gimmick.
- [AI-generated art](guides/ai-art.md) — prompting, quantizing, and laying out
  original sprite/tile PNGs for `ggfmod convert art`, and swapping generated art into
  either build-along sample.

## Experimental projects

- [Infinite Sonic](../../examples/infinite-sonic/README.md) — a separate endless Sonic 1 project with terrain-aware ground/flying encounters using ROM-derived sections, seeded world recycling and the existing code-patch API. Not part of the eight maintained gallery samples.

- [Putt Putt Paradise](../../examples/putt-putt-paradise/README.md) — Sonic 2 Mini Golf using ROM-backed Emerald Hill courses, timed charges, independent alternating golfers, and host-authoritative direct TCP play. An external candidate-API project, not one of the eight maintained gallery samples.

## Reference

- [`ggfmod` command reference](ggfmod.md)
- [Manifest v1](formats/manifest.md)
- [Baked art containers](formats/baked-containers.md)
- [`ModLevelDefinition` formats v1 and v2](formats/level-definition.md)
- [Content mods and Mod API 0.7](content-mods.md)
- [Audio manifest v1](formats/audio-manifest.md)
- [Character archetypes](concepts/character-archetypes.md)
- [Executable-code trust](concepts/trust.md)
- [Namespaced identity semantics](concepts/id-semantics.md)
- [`ggfmod validate` findings](troubleshooting.md)
- [Maintained sample gallery](samples/index.md)
- [Deferred-backlog decisions](BACKLOG.md)
- [GUI tooling evaluation](GUI_TOOLING_EVALUATION.md)

The eight sample sources are built by the default test suite. Treat them as
executable contracts rather than snippets copied out of context.

Custom game modules can opt into faster interactive gameplay with
`GameModule.gameplayStepsPerFrame()` (default 1, host range 1–32). Return alternating
counts for fractional rates and capture the accumulator through `rewindAdapters()`.
Return the continuous matching rate from `gameplayAudioPlaybackRate()` (default
1.0, bounded to 1–32) to accelerate music and effects without alternating their
pitch with the integer step budget. The host releases its audio rate at pause,
rewind, death, scene changes and teardown; external trace/movie owners retain
control of their own playback.

A module can also drive the engine's live rewind itself by returning a
`ScriptedRewind` from `GameModule.scriptedRewind()` (default `null`). While one is
returned, level play records rewind history even with live rewind switched off. When
its `requested()` is true the host rewinds with the ordinary presentation, taking
`stepsThisFrame()` steps per frame and checking `reachedTarget()` on the restored
state after each, then calls `ended(boolean)` once. Keep the implementation's own
state out of `rewindAdapters()`: it decides where the restore stops. Infinite Sonic's
CONTINUE is the worked example.
The host advances complete simulation ticks, preserving per-tick collision and
movement. Pause, rewind, external movie/trace ownership, transitions and non-level
scenes retain their normal pacing. `GameLoop.step()` remains one deterministic tick;
interactive hosts use `stepPresentationFrame()`. The
[Infinite Sonic example](../../examples/infinite-sonic/README.md) demonstrates a
rewindable clock that compounds speed every 30 seconds of active play.
A module can also pin a display aspect for its session with
`GameModule.requiredDisplayAspect()` (a `display.aspect` preset name; the master title
restores the player's setting) and hide the level select with
`GameModule.suppressesLevelSelect()`. Infinite Sonic uses both.
