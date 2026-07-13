# Maintained sample gallery

The default test suite builds and validates exactly these five checked-in sources.
They contain only original/generated test assets; built jars are not checked in.

1. [Music pack](phase4-gallery-music-pack/README.md) — a data-only API 1.0-compatible
   Sonic 2 stock-music override with generated WAV and loop metadata.
2. [Data-only reskin](../../../src/test/resources/mods/sample-reskin-src/META-INF/openggf-mod.yaml)
   — API 1.1 object-sheet conversion plus a stock art-key override, no Java/trust.
3. [Badnik and Sonic 2 zone](../../../src/test/resources/mods/sample-mod-src/README.md)
   — API 1.1 trusted entrypoint, namespaced object, baked art/level, progression,
   save, and rewind.
4. [Playable character](../../../src/test/resources/mods/sample-character-src/README.md)
   — API 1.2 owner-tagged character, distinct physics/archetype/ability policy,
   playable-v2 art, launch/save/rewind.
5. [Standalone game](../../../src/test/resources/mods/sample-standalone-src/README.md)
   — API 1.2 no-ROM module, level, badnik, character/team, streamed music/SFX,
   title launch, namespaced save, and Continue.

Use the linked source rather than a copied jar. Gallery CI exercises the real
`ggfmod package` validation boundary so manifest/container/API drift fails visibly.
