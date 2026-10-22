# `ModLevelDefinition` formats v1 and v2

The strict field sets are defined by
[`ModLevelDefinitionParser`](../../../src/main/java/com/openggf/mods/code/ModLevelDefinitionParser.java).
Unknown, duplicate, or missing JSON fields are errors. Integer fields in the binary
assets are big-endian, every reader requires exact lengths, and runtime-allocated mod
indices are never persisted.

## Format v1: Sonic 1, Sonic 2, and standalone levels

Format v1 is unchanged. It is the format for flat authored Sonic 1 levels,
additive Sonic 2 zones, and game-agnostic standalone levels. Sonic 1 uses
`blockGridSide: 16`; Sonic 2 uses `blockGridSide: 8`. Its exact inventory is:

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

V1 placement words use the canonical export encoding. The selected host reconstructs
its native placement semantics; S1's respawn flag and flip layout are not copied
from Sonic 2 raw words. Individual exported rings become S1 object-backed ring
placements at load time. Its primary/secondary collision assignments must agree
because the S1 host has one profile path. The bounded S1 adapter supports flat
scroll and independently usable stock factories; stock-zone events and loop
collision remaps are rejected rather than approximated.

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

The host runtime profile defaults to flat scroll with no inherited stock-zone
animation channels, PLC loads, features or events. Unsupported nonempty host
profiles fail before publication. Creator code may explicitly attach a verified
owner runtime factory with `ModZoneContribution.withRuntime`: water, custom
scroll, runtime state, tile and palette animation, staged render effects, render
modes and rewind adapters execute through their ordinary consumers and fault
boundaries. `ZoneEventFactory` separately supplies fresh owned event handlers;
`RewindableZoneEvents` adds capture, restore, missing reset and final reconciliation.
Namespaced objects remain available for interactions and authored finish behavior.
For the ordinary authored finish, the engine provides `openggf:authored-goal`.
It is an engine-owned object key, available without a separate creator mod, and
triggers the native signpost/results graph in a custom S3K level. Use that key
instead of assigning authored finish behavior to a numeric stock object ID;
creator code must not register or impersonate the engine key.
See [content runtime and composition](../content-mods.md) and the executable
[two-act campaign](../guides/two-act-campaign.md); these contracts do not inherit
arbitrary stock-zone scripts or PLC programs.

S3K saves persist a mod zone as the tagged identity
`savedZone.mod.{owner,local}`, never its synthetic runtime index. Reopening the slot,
editor round-trips, and rewind therefore retain the zone identity while the owner is
enabled. If the owner is disabled or the key cannot be resolved, data select safely
starts at AIZ1 (zone 0, act 0) rather than trusting a stale synthetic index. Historical
numeric stock-zone payloads remain readable unchanged.

Create either format through the [project editor](../../guide/creating/level-editor.md)
and `ggfmod convert level`; the converter parses the JSON first and enforces the
version-specific exact inventory before publishing its retained snapshot.

An `.ogfproject` is the complete editable working document, not a baked level
export or stock gameplay save. It retains exact RGB/native library data and
unavailable keyed records. Export applies the tighter Genesis format limits,
canonical placement encoding, palette ownership, and selected game-host checks;
a successful project save alone does not establish that it can be exported.


## Editor palette authoring

The level editor edits creator-owned palette cells using exact Genesis 9-bit
colors, with eight values per RGB channel. Existing projects keep their complete
RGB data; edits and undo preserve the save/reopen contract. Baked v1 exports retain
`palettes.bin`, while v2 retains sparse `paletteClaims` with the ownership rules
above. No fields or format versions are added by palette editing. Stock scroll
sources, authored water height and allowed character/team restrictions require a
separate versioned contract and are not silently written into v1/v2.
