# Quickstart: Sonic 2 zone

Read [candidate setup and first build](../getting-started.md) first: Java 21, Maven, and matching absolute engine/SDK jars from one commit. Generate a complete starter with `ggfmod init /absolute/my-zone --id my-zone --kind zone --package example.myzone`. Build with the jar properties shown there, edit source, and repeat the same package command.

This quickstart uses the Sonic 2 format-v1 adapter. S3K has a delivered bounded
format-v2 adapter; see [S3K zones](../content-mods.md#add-a-sonic-3k-zone).

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
and the [content-mod guide](../content-mods.md). The Sonic 1 adapter remains planned. S3K format v2 is a separate authoring
contract with sparse palette ownership and no inherited stock zone events.
