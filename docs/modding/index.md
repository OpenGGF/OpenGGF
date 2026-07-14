# OpenGGF mod creator handbook

OpenGGF's mod workflow is source-first and reproducible: author files, convert them
with `ggfmod`, validate/package a jar, then enable it in the Mod Manager and restart.
Choose the smallest quickstart that matches your goal; they are ordered by typical
effort.

1. [Music pack](quickstarts/music-pack.md)
2. [Data-only art reskin](quickstarts/reskin.md)
3. [Object or badnik](quickstarts/object.md)
4. [Sonic 2 zone](quickstarts/zone.md)
5. [Playable character](quickstarts/character.md)
6. [Standalone game](quickstarts/standalone.md)

## Follow-along guides

- [Flappy remix](guides/flappy-remix.md) — build-along tour of the `sample-flappy`
  gallery sample: object-controlled minigame gameplay, ROM-art intake, forced scroll,
  and layout obstacles inside a Sonic 2 patch.
- [Standalone platformer](guides/standalone-platformer.md) — build-along tour of the
  `sample-platformer` gallery sample: a no-ROM standalone game with a Tiled-authored
  level, an original character with a double jump, a patrolling badnik, and a spring
  gimmick.
- [AI-generated art](guides/ai-art.md) — prompting, quantizing, and laying out
  original sprite/tile PNGs for `ggfmod convert art`, and swapping generated art into
  either build-along sample.

## Reference

- [`ggfmod` command reference](ggfmod.md)
- [Manifest v1](formats/manifest.md)
- [Baked art containers](formats/baked-containers.md)
- [`ModLevelDefinition` v1](formats/level-definition.md)
- [Audio manifest v1](formats/audio-manifest.md)
- [Character archetypes](concepts/character-archetypes.md)
- [Executable-code trust](concepts/trust.md)
- [Namespaced identity semantics](concepts/id-semantics.md)
- [`ggfmod validate` findings](troubleshooting.md)
- [Maintained sample gallery](samples/index.md)
- [Deferred-backlog decisions](BACKLOG.md)
- [GUI tooling evaluation](GUI_TOOLING_EVALUATION.md)

The seven sample sources are built by the default test suite. Treat them as
executable contracts rather than snippets copied out of context.
