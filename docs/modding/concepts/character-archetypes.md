# Character behavior archetypes

A character's archetype chooses stock ROM-routing assumptions; it is not a display
label and must not be inferred from a Java class name. The authoritative archetype
values are [`PlayerCharacter`](../../../src/main/java/com/openggf/game/PlayerCharacter.java),
stored as `CharacterDefinition.behavesLike`; construction checks live in
[`CharacterDefinition`](../../../src/main/java/com/openggf/game/CharacterDefinition.java)
and registration-time ownership checks in
[`PlayableCharacterRegistry`](../../../src/main/java/com/openggf/game/PlayableCharacterRegistry.java).

Choose the closest stock behavior family for art/data routing, then supply literal
physics and an allowed ability separately. Unsupported archetype/ability combinations
are rejected instead of silently falling back. The
[character sample](../../../src/test/resources/mods/sample-character-src/README.md)
is the executable contract.

See the [character guide](../characters.md) for main/sidekick factories, respawn
strategies, super-form gating, save identity, and rewind behavior.
