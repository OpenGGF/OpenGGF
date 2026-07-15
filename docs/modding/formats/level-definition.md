# `ModLevelDefinition` formats v1 and v2

The strict field sets are defined by
[`ModLevelDefinitionParser`](../../../src/main/java/com/openggf/mods/code/ModLevelDefinitionParser.java).
Unknown, duplicate, or missing JSON fields are errors. Integer fields in the binary
assets are big-endian, every reader requires exact lengths, and runtime-allocated mod
indices are never persisted.

## Format v1: Sonic 2 and standalone levels

Format v1 is unchanged. It remains the format for additive Sonic 2 zones and
game-agnostic standalone levels. Its exact inventory is:

```text
level.json
patterns.bin
chunks.bin
blocks.bin
fg-map.bin
bg-map.bin                 # optional
solid-heights.bin
solid-widths.bin
solid-angles.bin
collision-primary.bin
collision-secondary.bin
palettes.bin
```

The `assets` object must name `palettes.bin`, and `ggfmod convert level` rejects a v1
export without that file. Objects use exactly one `stockObjectId` or namespaced
`objectKey`; music uses exactly one stock id or owned track key.

## Format v2: additive Sonic 3&K levels

Format v2 adapts a bounded level to the S3K host. It removes the monolithic
`palettes.bin` asset and adds typed host metadata plus sparse palette claims. A
minimal exact definition is:

```json
{
  "formatVersion": 2,
  "zoneName": "Demo Zone",
  "zoneIndex": 64,
  "levelIndex": 1024,
  "blockGridSide": 8,
  "width": 1,
  "height": 1,
  "bounds": {"minX": 0, "maxX": 127, "minY": -32, "maxY": 96},
  "start": {"x": 32, "y": 48},
  "music": {"stockId": 129},
  "assets": {
    "patterns": "patterns.bin",
    "chunks": "chunks.bin",
    "blocks": "blocks.bin",
    "foregroundMap": "fg-map.bin",
    "solidHeights": "solid-heights.bin",
    "solidWidths": "solid-widths.bin",
    "solidAngles": "solid-angles.bin",
    "collisionPrimary": "collision-primary.bin",
    "collisionSecondary": "collision-secondary.bin"
  },
  "hostMetadata": {"s3k": {"objectZoneSet": "S3KL"}},
  "paletteClaims": [{"line": 2, "color": 0, "sega": 0}],
  "objects": [],
  "rings": []
}
```

`bg-map.bin` and its `backgroundMap` field remain optional. The other ten files are
required. `palettes.bin` is forbidden in a v2 inventory, and `assets.palettes` is an
unknown field.

`hostMetadata.s3k.objectZoneSet` accepts exactly `S3KL` or `SKL` and is required in
the exact v2 JSON shape. A level containing only namespaced `objectKey` spawns should
write `S3KL` as the default. A level containing any `stockObjectId` must deliberately
select the intended set, and registration rejects stock factories that require a
real ROM-zone identity. Prefer namespaced objects for portable custom zones.

`paletteClaims` owns individual `(line, color)` cells, not whole lines. Each entry
contains `line` (1-3), `color` (0-15), and a Genesis color word `sega` whose only
allowed bits are `0x0EEE`. Claims must be unique. Every nonzero indexed color
reachable through the foreground or background art must be claimed. Because indexed
zero is transparent, reachable zero pixels also require the visible backdrop claim
at line 2, color 0.

The S3K host owns all of line 0 for the selected character and reserves the actual
palette cells used by the lives HUD under `host:s3k-hud`; creator claims cannot
replace either. The bridge submits only those used HUD cells, so every other line
1-3 cell remains available to sparse creator claims. HUD cells use the host's lives
palette override when present and otherwise use the character palette. This
ownership composition is also applied after editor resume and rewind restore.

The v2 runtime profile is intentionally empty: flat scroll, with no stock animation
channels, PLC loads, special render effects, advanced render modes, stock zone
features, or stock level events. Unsupported nonempty profiles fail before the zone
is published; custom behavior belongs in namespaced mod objects.

S3K saves persist a mod zone as the tagged identity
`savedZone.mod.{owner,local}`, never its synthetic runtime index. Reopening the slot,
editor round-trips, and rewind therefore retain the zone identity while the owner is
enabled. If the owner is disabled or the key cannot be resolved, data select safely
starts at AIZ1 (zone 0, act 0) rather than trusting a stale synthetic index. Historical
numeric stock-zone payloads remain readable unchanged.

Create either format through the in-engine exporter/importer path and
`ggfmod convert level`; the converter parses the JSON first and enforces the
version-specific exact inventory before publishing its retained snapshot.
