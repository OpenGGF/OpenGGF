# Quickstart: playable character

Read [candidate setup and first build](../getting-started.md) first: Java 21, Maven, and matching absolute engine/SDK jars from one commit. Generate a complete starter with `ggfmod init /absolute/my-character --id my-character --kind character --package example.mycharacter`. Build with the jar properties shown there, edit source, and repeat the same package command.

Playable characters are trusted code plus playable-v2 art.

1. Open the generated `my-character` project. Its playable art, sensors and
   owner-tagged character registration are already complete.
2. Choose the ROM-routing [behavior archetype](../concepts/character-archetypes.md)
   and edit the character's physics and `CharacterDefinition`.
3. Edit the source PNG/sheet, then repeat the Maven package command. The lifecycle
   runs the playable-art converter; review its reported pattern-bank cost.
4. Select one built-in secondary ability or implement the supported pre-dispatch hook.
5. Validate `target/my-character-mod.jar`, grant trust, and verify launch, sidekick
   policy, save, and rewind.

[`sample-character-src`](../../../src/test/resources/mods/sample-character-src/README.md)
is the maintained reference. As an alternative checkout route, follow its build-script
instructions to materialize the encoded assets; copying its raw project is insufficient.

The [character guide](../characters.md) documents exact fallbacks and non-goals;
notably mod super forms are disabled and portrait/HUD art uses stock fallback today.
