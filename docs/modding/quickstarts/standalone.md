# Quickstart: standalone game

Read [candidate setup and first build](../getting-started.md) first: Java 21, Maven, and matching absolute engine/SDK jars from one commit. Generate a complete starter with `ggfmod init /absolute/my-standalone --id my-standalone --kind standalone --package example.mystandalone`. Build with the jar properties shown there, edit source, and repeat the same package command.

A standalone mod supplies an original no-ROM game and therefore has the broadest
surface.

1. Copy [`sample-standalone-src`](../../../src/test/resources/mods/sample-standalone-src/README.md).
2. Use `type: standalone`, omit `baseGame`, and register exactly one owner-backed
   module.
3. Supply at least one terminal progression route, a default owner-tagged character,
   literal physics, baked levels/art, and any namespaced streamed music/SFX.
4. Package the exploded directory, validate the resulting jar, grant trust, then test
   New Game, slot-1 Continue, completion/title return, and corrupt-save fallback
   without ROM files present.

See the [standalone guide](../standalone-games.md) for capability boundaries and the
[identity reference](../concepts/id-semantics.md) for save/object/audio keys.
