# Quickstart: playable character

Playable characters are trusted code plus playable-v2 art.

1. Copy [`sample-character-src`](../../../src/test/resources/mods/sample-character-src/README.md).
2. Choose the ROM-routing [behavior archetype](../concepts/character-archetypes.md),
   provide literal physics, and register an owner-tagged `CharacterDefinition`.
3. Convert art with `ggfmod convert art --playable ... --out <runner.ggfp>` and review
   the reported pattern-bank cost.
4. Select one built-in secondary ability or implement the supported pre-dispatch hook.
5. Build/package, validate the jar, grant trust, and verify launch, sidekick policy,
   save, and rewind.

The [character guide](../characters.md) documents exact fallbacks and non-goals;
notably mod super forms are disabled and portrait/HUD art uses stock fallback today.
