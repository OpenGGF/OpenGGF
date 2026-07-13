# Namespaced identity semantics

Manifest ids and local registry names are different types. A persisted mod key is
`(modId, localName)` and displays exactly as `mod-id:local/name`; it is never converted
to a runtime numeric allocation. The common grammar and owner checks are implemented
by [`ModKeySyntax`](../../../src/main/java/com/openggf/game/ModKeySyntax.java).

- Manifest ids are lower-case `[a-z0-9][a-z0-9-]{0,63}`.
- Local object/art/track/SFX/animation names are owner-local normalized paths/names.
- Stock object/music identities remain numeric within one base-game domain.
- Mod objects, tracks, SFX, characters, zones, save locations, and rewind recreation
  retain their declaring owner.
- A declaration cannot spoof another owner; case mismatch, extra colon, empty/dot
  segments, or overlong UTF-8 is rejected.

The design prevents load-order changes from retargeting saves or rewind state. See
[`ModLevelDefinition` v1](../formats/level-definition.md) for tagged object/music
unions and the [standalone guide](../standalone-games.md) for namespaced saves.
