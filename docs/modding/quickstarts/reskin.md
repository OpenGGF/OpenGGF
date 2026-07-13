# Quickstart: data-only reskin

An art reskin needs no Java or trust prompt.

1. Start from [`sample-reskin-src`](../../../src/test/resources/mods/sample-reskin-src/META-INF/openggf-mod.yaml).
2. Draw an original PNG and describe its frames/pieces in the object-sheet YAML.
3. Convert it with `ggfmod convert art --image <png> --sheet <yaml> --out <sheet.ggfs>`.
4. Map a stock `ObjectArtProvider` key to the baked path in `artOverrides`.
5. Package the exploded directory, validate the resulting jar, then enable/restart.

Palette-line, alignment, mapping, and pattern-span errors are build failures. See the
[baked-container reference](../formats/baked-containers.md) and
[content-mod guide](../content-mods.md).
