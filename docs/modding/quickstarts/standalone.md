# Quickstart: standalone game

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
