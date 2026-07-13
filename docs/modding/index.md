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
