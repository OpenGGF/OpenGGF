# Maintained sample gallery

The default test suite builds and validates exactly these eight checked-in sources.
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
6. [Flappy remix](../../../src/test/resources/mods/sample-flappy-src/README.md) —
   API 2.1 additive S2 patch — object-controlled minigame gameplay, ROM-art intake,
   forced scroll, and layout obstacles. See the
   [build-along guide](../guides/flappy-remix.md) for a narrated walkthrough.
7. [Standalone platformer](../../../src/test/resources/mods/sample-platformer-src/README.md) —
   API 2.0 no-ROM standalone game — a Tiled-authored (`--from-tmx`) level with a
   namespaced streamed-music track, an original double-jumping character with a
   distinct `PhysicsProfile`, a patrolling badnik, and a spring gimmick. See the
   [build-along guide](../guides/standalone-platformer.md) for a narrated walkthrough
   and [AI-generated art](../guides/ai-art.md) for generating replacement sprites.
8. [ROM-art remix](../../../src/test/resources/mods/sample-rom-art-remix-src/README.md) —
   API 2.1 additive S2 patch whose default Sonic team displays Tails flight frames
   materialized at launch from bounded art, mapping, and DPLC requests against the
   player's ROM. See the [source-first guide](../guides/rom-art-remix.md) for the
   request, decoded-pattern, rewind, and no-ROM-package checks.

Use the linked source rather than a copied jar. Gallery CI exercises the real
`ggfmod package` validation boundary so manifest/container/API drift fails visibly.
