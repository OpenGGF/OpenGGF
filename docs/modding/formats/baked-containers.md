# Baked art containers

Object art uses `GGFS` v1. Its authoritative writer version is
`BakedSheetWriter.VERSION` in
[`BakedSheetWriter`](../../../src/main/java/com/openggf/tools/modsdk/BakedSheetWriter.java),
and runtime validation is in
[`BakedSheetReader`](../../../src/main/java/com/openggf/level/objects/BakedSheetReader.java).
The [reskin sample](../../../src/test/resources/mods/sample-reskin-src/META-INF/openggf-mod.yaml)
exercises this path.

Playable art uses `GGFP` v2. The fixed section tags/order and version are defined by
[`PlayableSheetWriter`](../../../src/main/java/com/openggf/tools/modsdk/PlayableSheetWriter.java)
and checked by
[`PlayableSheetReader`](../../../src/main/java/com/openggf/level/objects/PlayableSheetReader.java).
The [character sample](../../../src/test/resources/mods/sample-character-src/README.md)
and [standalone sample](../../../src/test/resources/mods/sample-standalone-src/README.md)
exercise it.

Always create these containers through `ggfmod convert art`; do not hand-encode them.
Container versions are compatibility contracts. Unknown required sections, duplicate
sections/keys, invalid indices, reserved bits, trailing bytes, and all size/count
violations fail validation.
