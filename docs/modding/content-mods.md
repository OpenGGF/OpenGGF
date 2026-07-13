# Content mods

This page is the detailed additive-content reference. New creators should start at
the [handbook index](index.md), which orders the six quickstarts by effort and links
the format, trust, identity, troubleshooting, and sample references.

OpenGGF Mod API 2.0 supports restart-loaded music packs, code-backed objects,
baked art, complete Sonic 2 zones, playable characters, and no-ROM standalone
games. Mods are discovered from the
process `mods/` directory at restart; executable mods must be enabled and granted
trust in the Mod Manager before they run.

The public mod API is version `2.1.0` (a deliberate breaking bump from `1.1.0` via
the additive `1.2.0` step, followed by the additive `2.1.0` ROM-art intake step; see
`docs/architecture/mod-api-compatibility.md`). Mods must declare a `2.x` engine range
such as `>=2.0.0 <3.0.0`. Start with the guide for the contribution you are building:

- [Music packs](music-packs.md) — data-only WAV/Ogg replacements for stock music.
- This guide — Phase 2 objects, art reskins, and complete Sonic 2 zones.
- [Playable characters](characters.md) — owner-tagged character identity, physics,
  playable art, archetypes, abilities, saves, and rewind.
- [Standalone games](standalone-games.md) — a complete `GameModule`, levels,
  characters, music, and SFX that launch without a ROM.
- [The `ggfmod` CLI](ggfmod.md) — launcher syntax, project scaffolding, and all
  converters.

A creator build needs both release artifacts:

- the engine jar, which contains the public API and runtime dependencies; and
- the `openggf-mod-sdk` classifier jar, which contains `ggfmod`, converters, and
  project templates.

Use `docs/modding/ggfmod.ps1` on Windows or `docs/modding/ggfmod` on macOS/Linux.
Pass the engine jar and SDK jar first, followed by the command. The examples below
abbreviate that launcher as `ggfmod`.

## Start a project

```text
ggfmod init my-mod --id my-mod --package example.mymod
```

The generated Maven project is a working reference mod, not pseudocode. It contains
a strict API 1.2 manifest, a namespaced patrol badnik, a Phase 3 character stub, an
8-by-8 Genesis sheet source, and a minimal full-level editor export. The character
stub deliberately has no playable art or terrain sensors; use the
[character guide](characters.md) and checked-in acceptance sample before enabling it
for gameplay. The entrypoint also registers the object, its baked art and preview,
and a zone inserted after `mtz3`.

Build the generated Java code against the engine jar, convert its source assets, and
package the resulting classes and resources:

```text
ggfmod convert art --image src/main/mod/sample.png --sheet src/main/mod/sample-sheet.yaml --out target/classes/art/sample.ggfs
ggfmod convert level --from-export src/main/mod/level-source --out target/classes/levels/sample
mvn package
ggfmod package --input target/classes --out target/my-mod.jar
ggfmod validate target/my-mod.jar
```

`package` creates a deterministic jar and validates it before publishing the output.
It does not overwrite an existing output path. The separate `validate` command shown
above is useful for printing the sorted findings for an existing jar. Copy the jar
into `mods/`, start the engine, enable it in the Mod Manager, confirm the code-trust
warning, and restart.

For an explicit local development launch, use `ggfmod run target/classes`. This is
the only exploded-directory entry point. The engine snapshots that directory once;
later source-tree changes are not observed by the running session. Deterministic
test, replay, capture, and time-attack launches still force external mods off.

## Add an object

Register object keys through `ModContext.registerObject`. Keys are owner-local at
registration and become namespaced as `<mod-id>:<local-key>` in level exports and
saves. Never assign a stock byte object id to a mod object.

The generated badnik demonstrates the minimum runtime contracts:

- extend an appropriate public object base such as `AbstractBadnikInstance`;
- use injected `services()` only after construction, never a singleton or a
  constructor-time service lookup;
- use public shared helpers such as `PatrolMovementHelper` and the standard
  `DestructionEffects.DestructionConfig` path instead of copying engine behavior;
- implement `RewindRecreatable` and recreate from `RewindRecreateContext.spawn()`;
  and
- keep mutable gameplay state in instance fields that the rewind schema can capture.

Do not add mutable static gameplay state, uncaptured object references, or immutable
`final` scalar state whose runtime value changes. `ggfmod validate` checks compiled
classes for these object, service, and rewind rules before the engine creates an
owner class loader.

Art sheets are registered with `registerObjectArt` and a `BakedSheetRef`; editor
previews use `registerObjectPreview`. The sheet YAML assigns each 8-by-8 tile to a
Genesis palette line and describes bounded pieces. `convert art` rejects images
whose dimensions, palette use, or piece bounds cannot be represented exactly.

## ROM art intake (Sonic 2 patch mods)

`ModContext.registerRomObjectArt(key, request)` materializes object art from the
*player's own ROM* at gameplay launch, instead of shipping baked art in the mod jar.
This is the supported way to remix a stock game's existing art (for example, Tails'
flying frames) into a new mod object — the mod jar itself ships zero ROM bytes; the
sheet is decoded into memory only after the engine opens the player's `s2.gen`.

The request names a ROM art address, its compression, an S2 mapping table address, an
optional DPLC table address, a palette line, and a bank size:

```java
context.registerRomObjectArt("bird", new RomArtRequest(
        0x64320,                 // artAddress: Tails' flying-frame art
        RomArtCompression.UNCOMPRESSED,
        0xB8C0,                  // uncompressedByteSize (UNCOMPRESSED only)
        0x739E2,                 // mappingAddress
        0x7446C,                 // dplcAddress (0 = no DPLC flattening)
        0,                       // paletteLine (0-3)
        1));                     // bankSize (1 for a static sheet)
```

The literals above are `Sonic2Constants.ART_UNC_TAILS_ADDR` /
`ART_UNC_TAILS_SIZE` / `MAP_UNC_TAILS_ADDR` / `MAP_R_UNC_TAILS_ADDR`, and palette
line `0` matches `ART_TILE_TAILS`'s stock palette assignment. Use `RomOffsetFinder`
(`--game s2`) to locate art/mapping/DPLC addresses for other stock objects; a label's
compression is usually visible in its ROM offset finder result or the surrounding
disassembly.

**Gates.** ROM art intake is available only to additive Sonic 2 patch mods
(`baseGame: s2`, `type: patch`); a standalone module (or any non-S2 `baseGame`) fails
registration outright. Because registration happens before any ROM is open, addresses
are checked only against a static Sonic 2 ROM-length bound at registration time; the
real decompression, mapping, and DPLC parsing happen at gameplay launch once the
player's ROM is available.

**Palette.** `paletteLine` is a palette *line* index (0-3) into the active zone
palette, not a ROM color address — the sheet's actual colors come from the mod
zone's own `palettes.bin` (or the stock zone's palette, for objects placed in
unmodified zones), matching whichever palette line the mod assigns.

**DPLC.** An optional `dplcAddress` (S2 player-format DPLC table) flattens
frame-by-frame VRAM tile swaps into one static sheet, the same technique the engine
itself uses for objects such as the AIZ intro plane and the ICZ snowboard. Pass `0`
when the mapping's pieces already reference art tiles directly.

**Limits.** Materialized sheets are bounded by the same `ModInputLimits` sheet caps
(`maxSheetPatterns`, `maxSheetFrames`, `maxSheetPieces`) enforced elsewhere in the mod
pipeline, so a garbage or oversized request cannot allocate unboundedly.

**Faults.** A bad address, a decompression failure, or a sheet that exceeds the
`ModInputLimits` caps aborts launch with an owner-attributed `MOD_ROM_ART_INVALID`
diagnostic naming the offending key and the hex ROM address — the same creator-apply
fault contract as other launch-time mod failures.

Once materialized, the sheet is served through the normal object-art path: call
`getRenderer("<mod-id>:bird")` from object code exactly as you would for
`registerObjectArt`.

## Add a Sonic 2 zone

Phase 2 new-zone support is intentionally Sonic 2 only. In the editor, start from a
level, make the desired changes, and use the full-level export into the mod project's
source tree. A full export is different from the editor's sidecar/delta save: the
export directory must contain these required files:

```text
level.json
patterns.bin
chunks.bin
blocks.bin
fg-map.bin
solid-heights.bin
solid-widths.bin
solid-angles.bin
collision-primary.bin
collision-secondary.bin
palettes.bin
```

`bg-map.bin` is the only optional inventory entry and is present when the level has a
background layout.

`level.json` carries boundaries, start position, music, tagged spawns, and references
to the ten binary assets. The binaries contain the complete pattern/chunk/block,
collision, and palette data needed to load without a ROM-address fallback.
`ggfmod convert level` validates this exact inventory and copies a retained snapshot
to the baked output.

Register the result with `registerZone(new ModZoneContribution(...))`. Mod zones use
synthetic ROM-facing zone ids from `0x40` upward and synthetic level ids from `0x400`
upward; creators must not use those reserved bands for stock content. Runtime list
indices are append-only after Sonic 2's 11 stock zones, while `insertAfter` creates a
results-boundary progression redirect without renumbering stock zones. Use a valid
results-driven stock anchor such as `mtz3`.

Object spawns in the export retain namespaced keys such as
`my-mod:sample-badnik`. Music may reference a valid stock Sonic 2 music id or a
namespaced converted track. A minimal zone needs no custom events, animation,
water, palette cycling, or parallax handler; unknown synthetic ids use the engine's
graceful defaults.

Saved mod-zone locations use a tagged zone key rather than the allocated runtime
index. If the mod is later disabled or missing, the slot remains intact, loading
reports the missing zone, and play restarts at zone 0. Re-enabling the mod makes the
tagged destination resolvable again.

Full exports may contain material derived from a user-supplied ROM. Mod authors are
responsible for ensuring they have the right to distribute every exported asset;
shipping a lightly edited stock level may distribute copyrighted level data.

## Make a data-only art reskin

A reskin needs no Java entrypoint and no trust grant. Set `type: patch` and the
appropriate `baseGame`, omit `entrypoint`, convert the sheet, and map an exact stock
art key in `artOverrides`:

```yaml
artOverrides:
  EndSign: art/reskin.ggfs
```

Package the directory and validate the resulting jar with the same commands. With the
mod disabled, the engine retains the original provider instance and behavior; with it
enabled, only the named art lookup is decorated.

For the complete CLI invocation and launcher details, see [the `ggfmod` guide](ggfmod.md).
For streamed stock-music replacement metadata, see [Music packs](music-packs.md).
