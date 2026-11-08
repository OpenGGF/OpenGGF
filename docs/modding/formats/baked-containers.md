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

### GGFP canonical animations (unpublished v2)

The revised, unpublished `ANIM` table uses exact uppercase `CanonicalAnimation`
names in sorted UTF-8 order; `WAIT` is required. Older experimental tables are
not supported. See [character animation manifests](../characters.md#canonical-animation-table).
After each existing step list (`u16 frame`, `u16 duration`, `u8 loop flag`,
`u8 reserved=0`), each entry stores `u8 speedDriven` (0/1), `u8 end` (0 loop,
1 hold, 2 switch), a length-prefixed UTF-8 canonical next-animation name, and
`u8 slopeSetCount` (0 or 4). Each slope set contains one `u16` frame index per
step. End metadata controls playback; legacy step-loop flags must occur only
on the last step. All indices, counts, targets and speed/slope combinations
are validated. Canonical scripts stay unbound until the host installs the art;
raw native animation IDs are never serialized in this table.
