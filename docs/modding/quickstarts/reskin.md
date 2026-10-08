# Quickstart: data-only reskin

Read [candidate setup and first build](../getting-started.md) first: Java 21, Maven, and matching absolute engine/SDK jars from one commit. Generate a complete starter with `ggfmod init /absolute/my-reskin --id my-reskin --kind reskin --package example.myreskin`. Build with the jar properties shown there, edit source, and repeat the same package command.

An art reskin needs no Java or trust prompt.

1. Open the generated `my-reskin` project; its Sonic 2 signpost source has all six frames.
2. Edit `src/main/mod/sample.png` and describe its frames/pieces in
   `src/main/mod/sample-sheet.yaml`.
3. Repeat the Maven package command; it converts the sheet to `art/sample.ggfs`.
4. Keep an exact key from `ggfmod art-keys --game s2` mapped to that baked path in
   `artOverrides`.
5. Validate `target/my-reskin-mod.jar`, then enable, Apply and restart.

The [`sample-reskin-src` manifest](../../../src/test/resources/mods/sample-reskin-src/META-INF/openggf-mod.yaml)
is a reference, not another project to copy over the generated starter. For manual
conversion syntax, see the [CLI reference](../ggfmod.md); choose a fresh output path.

Palette-line, alignment, mapping, and pattern-span errors are build failures. See the
[baked-container reference](../formats/baked-containers.md) and
[content-mod guide](../content-mods.md).

The maintained Sonic 2 signpost uses `signpost` and supplies frames 0–5: idle uses
frame 2, spin consumes all six, and final faces use 0/1. Its sheet palette describes
quantization; it does not replace the host's live palette. The sample uses white
index 6 on normal Sonic/Tails palette line 0. Preserve these indices, or create an
owned object/zone with a palette you control. A single-frame replacement can validate
yet disappear when the stock consumer selects another frame.
