# Content mods

Phase 2 content mods can add code-backed objects, baked art, and complete Sonic 2
zones. They can also replace stock art without code. Mods are discovered from the
process `mods/` directory at restart; executable mods must be enabled and granted
trust in the Mod Manager before they run.

The public mod API is version `2.0.0` (a deliberate breaking bump from `1.1.0`;
see `docs/architecture/mod-api-compatibility.md`). Mods must declare a `2.x`
engine range such as `>=2.0.0 <3.0.0`. A creator build needs both release artifacts:

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
a strict manifest, a namespaced patrol badnik, an 8-by-8 Genesis sheet source, and a
minimal full-level editor export. The entrypoint registers the object, its baked art
and preview, and a zone inserted after `mtz3`.

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
It does not overwrite an existing output path. Copy the jar into `mods/`, start the
engine, enable it in the Mod Manager, confirm the code-trust warning, and restart.

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

## Add a Sonic 2 zone

Phase 2 new-zone support is intentionally Sonic 2 only. In the editor, start from a
level, make the desired changes, and use the full-level export into the mod project's
source tree. A full export is different from the editor's sidecar/delta save: the
export directory must contain exactly these files:

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

Package and validate the directory with the same commands. With the mod disabled,
the engine retains the original provider instance and behavior; with it enabled,
only the named art lookup is decorated.

For the complete CLI invocation and launcher details, see [the `ggfmod` guide](ggfmod.md).
For streamed stock-music replacement metadata, see [Music packs](music-packs.md).
