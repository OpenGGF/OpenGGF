# Quickstart: Sonic 2 zone

The current additive new-zone adapter targets Sonic 2.

1. Start with the generated project or
   [`sample-mod-src`](../../../src/test/resources/mods/sample-mod-src/README.md).
2. Author a block-aligned map in Tiled and import it with
   `ggfmod convert level --from-tmx <map.tmx> --palette <palettes.bin> --out <dir>`,
   or use the in-engine editor's complete export directory.
3. Register the baked `level.json` as an owned zone contribution, optionally after a
   valid stock progression anchor.
4. Use namespaced object and track keys in the level definition; stock ids remain
   numeric and game-local.
5. Package the exploded directory, validate the resulting jar, load it headless during
   development, and test save/disable fallback.

Tiled covers bulk layout and point spawns. Custom collision-profile shaping remains a
binary/editor concern. See [`ModLevelDefinition` v1](../formats/level-definition.md)
and the [content-mod guide](../content-mods.md). S1/S3K adapters are separately
scheduled follow-ons rather than part of the current S2 authoring path.
