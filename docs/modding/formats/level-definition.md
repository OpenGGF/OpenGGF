# `ModLevelDefinition` v1

The exact JSON field sets are the `ROOT`, `BOUNDS`, `START`, `MUSIC`, `ASSETS`, object,
and ring constants in
[`ModLevelDefinitionParser`](../../../src/main/java/com/openggf/mods/code/ModLevelDefinitionParser.java).
The binary writers and magic/version headers are in
[`FullLevelExporter`](../../../src/main/java/com/openggf/editor/persistence/FullLevelExporter.java).

An export directory contains `level.json`, `patterns.bin`, `chunks.bin`, `blocks.bin`,
`fg-map.bin`, optional `bg-map.bin`, three solid-profile files, primary/secondary
collision files, and `palettes.bin`. Integer fields are big-endian and every reader
requires exact lengths. Objects use exactly one `stockObjectId` or namespaced
`objectKey`; music uses exactly one stock id or owned track key. Runtime-allocated mod
indices are never persisted.

Create the directory through the in-engine exporter or `ggfmod convert level`; both
feed the same parser. The
[badnik+zone sample](../../../src/test/resources/mods/sample-mod-src/README.md) and
[standalone sample](../../../src/test/resources/mods/sample-standalone-src/README.md)
exercise complete definitions.
