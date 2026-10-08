# Quickstart: standalone game

Read [candidate setup and first build](../getting-started.md) first: Java 21, Maven, and matching absolute engine/SDK jars from one commit. Generate a complete starter with `ggfmod init /absolute/my-standalone --id my-standalone --kind standalone --package example.mystandalone`. Build with the jar properties shown there, edit source, and repeat the same package command.

A standalone mod supplies an original no-ROM game and therefore has the broadest
surface.

1. Open the generated `my-standalone` project. It already contains a no-ROM module,
   playable character, level, art and audio.
2. Keep `type: standalone`, no `baseGame`, and exactly one owner-backed module
   registration while editing the game.
3. Edit its terminal progression route, default owner-tagged character, literal
   physics, source levels/art, and namespaced streamed music/SFX.
4. Repeat the Maven package command; it converts and packages the authored assets.
   Validate `target/my-standalone-mod.jar`, grant trust, then test New Game, slot-1
   Continue, completion/title return, and corrupt-save fallback without ROM files present.

[`sample-standalone-src`](../../../src/test/resources/mods/sample-standalone-src/README.md)
is the maintained reference. As an alternative checkout route, follow its build-script
instructions to materialize the encoded assets; copying its raw project is insufficient.

See the [standalone guide](../standalone-games.md) for capability boundaries and the
[identity reference](../concepts/id-semantics.md) for save/object/audio keys.
