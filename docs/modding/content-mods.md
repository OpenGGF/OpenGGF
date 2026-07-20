# Content mods

This page is the detailed additive-content reference. New creators should start at
the [handbook index](index.md), which orders the six quickstarts by effort and links
the format, trust, identity, troubleshooting, and sample references.

OpenGGF Mod API 2.4 supports restart-loaded music packs, code-backed objects,
baked art, complete Sonic 2 and Sonic 3&K zones, playable characters, and no-ROM
standalone games. Mods are discovered from the
process `mods/` directory at restart; executable mods must be enabled and granted
trust in the Mod Manager before they run.

The public mod API is version `2.4.0` (a deliberate breaking bump from `1.1.0` via
the additive `1.2.0` step, followed by the additive `2.1.0` ROM-art intake step and
the additive `2.2.0` playable-subclass rewind capture hooks, the additive `2.3.0`
host-adapted S3K zone surface, and the additive `2.4.0` gameplay-policy surface; see
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
- [Native-Tails Flappy](guides/native-tails-flappy.md) — a maintained S3K patch
  combining the 2.4 fresh-game, team, input, and HUD policies with a fixed-camera
  dynamic-object minigame.

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

Code-bearing mods require the JVM jar because GraalVM native-image builds cannot
load new classes under closed-world AOT. On native builds they remain disabled for
the current process and appear as `UNSUPPORTED` in the Mod Manager; data-only music
packs and reskins continue to work. Run `OpenGGF-<ver>-jar-with-dependencies.jar`
(or the universal jar) to use code-bearing mods.

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
The maintained [ROM-art remix guide](guides/rom-art-remix.md) follows the complete
source, decoded-pattern probe, rewind, and package-inspection workflow.

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
palette, not a ROM color address. For an additive S2 format-v1 zone, the host
replaces line 0 with the active ROM character palette after decoding creator level
data while preserving creator-owned lines 1-3. Sonic and Tails share
`Pal_SonicTails`; a Knuckles-main lock-on changes line 0 indices 2-5 and can recolour
borrowed Tails art.

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

The format-v1 additive-zone path is intentionally Sonic 2 only; S3K uses format v2
as described in the next section. In the editor, start from a level, make the desired
changes, and use the full-level export into the mod project's source tree. A full
export is different from the editor's sidecar/delta save: the
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

## Add a Sonic 3&K zone

Mod API 2.3 adds an S3K host adapter for additive zones. Use level format v2 and
declare `baseGame: s3k`; the v1 Sonic 2 and standalone paths above are unchanged.
Format v2 keeps the bounded pattern, chunk, block, map, solid, and collision files,
but removes `palettes.bin`. Its `hostMetadata.s3k.objectZoneSet` value is `S3KL` or
`SKL`, while `paletteClaims` lists only the line 1-3 color cells used by reachable
level art. See the [exact level-format reference](formats/level-definition.md).

For a namespaced-object-only level, write `S3KL` as the default object set. If any
entry uses `stockObjectId`, select the intended set explicitly; registration rejects
stock objects whose factories depend on a real ROM zone. Namespaced mod objects are
the reliable path for custom gameplay.

S3K supplies the selected character palette on line 0 and reserves only the cells
actually used by the lives HUD. The creator owns every other declared sparse cell.
Claims that overlap host-owned line 0 or a live HUD cell fail registration instead
of creating a frame-order-dependent palette conflict. The custom-zone runtime is
otherwise deliberately empty: flat scroll and no stock animated tiles, PLC loads,
zone features/events, special passes, or advanced render modes.

Registration remains additive and tagged. Runtime zone indices may change with the
enabled mod set, so saves store `savedZone.mod.owner/local` identity rather than that
synthetic index. If the owner is later disabled, S3K data select preserves the slot
but safely falls back to AIZ1; re-enabling the owner makes the tagged destination
resolvable again.

## Choose a fresh-game destination and presentation

The maintained [Native-Tails Flappy guide](guides/native-tails-flappy.md) exercises
this complete policy set against a real S3K launch and shows how the policies stay
destination-scoped while the gameplay controller remains ordinary mod object code.

Mod API 2.4 lets a complete-zone patch mark one owned zone as a fresh-game start and
attach launch-only policies to that tagged destination. Set the trailing
`ModZoneContribution` component to `true`; the four-argument constructor remains
compatible and means `gameStart=false`. A mod using these contracts must declare an
engine range that includes 2.4, such as `>=2.4.0 <3.0.0`:

```java
var destination = ZoneKey.mod("my-mod", "flappy");
context.registerZone(new ModZoneContribution(
        "flappy", new BakedLevelRef("levels/flappy/level.json"), null, null, true));
context.registerLaunchTeam(new ModLaunchTeamContribution(
        destination, CharacterKey.TAILS, List.of()));
```

Game-start selection is exclusive. Enabled patch order is authoritative: the last
effective declaration wins, each shadowed owner receives `MOD_GAME_START_SHADOWED`,
and disabling the winner reveals the previous declaration or the host's stock fresh
destination. This does not insert the zone into results progression. Both New Slot
and No Save fresh starts use the resolved destination.

The launch-team policy replaces only the copied gameplay-session team after the
resolved character registry verifies every required identity. It does not mutate
`config.yaml`, the player's data-select choice, or the durable team saved in an active
slot. A missing required character aborts launch; the engine never silently substitutes
a partial team.

An input filter transforms P1's logical snapshot without adding a movement framework:

```java
context.registerInputFilter(new ModInputFilterContribution(destination, raw ->
        PlayerInputState.of(
                raw.heldMask() & ~(AbstractPlayableSprite.INPUT_LEFT
                        | AbstractPlayableSprite.INPUT_RIGHT),
                raw.pressedMask() & ~(AbstractPlayableSprite.INPUT_LEFT
                        | AbstractPlayableSprite.INPUT_RIGHT),
                raw.actionHeldMask(), raw.actionPressedMask(),
                raw.startHeld(), raw.startPressed())));
```

The engine records the raw `InputHandler.logical()` snapshot first, then applies the
filter downstream. Trace playback and rewind re-simulation therefore replay the same
raw snapshot and reapply the filter deterministically. `PlayerInputState` reconstructs
the legacy jump bits from `actionHeldMask` and `actionPressedMask`; preserve those
action masks when suppressing directions or jump will be lost.

`registerHudProfile(new ModHudProfileContribution(destination, profile))` installs a
row-only presentation over the existing SCORE, TIME, RINGS, and LIVES labels and
counters. Each immutable `HudRow` chooses visibility, label, metric, label/value
coordinates, width, and a `NONE`, `TIMER_FLASH`, or `ZERO_FLASH` warning. Numeric
metrics accept widths 1 through 9 and saturate non-negative values into that width;
TIME requires the existing four-character width. A profile can label the RINGS metric
as SCORE and omit the original score row, but it does not create or replace gameplay
counters.

These destination policies are required as a set. Creator/provider/filter failures
run through the owner fault boundary, record `MOD_CALLBACK_FAILED`, pending-disable
the owner and dependents, persist that decision, and abort rather than continuing with
a partial launch. With no matching contribution—or after session teardown—the stock
defaults are the selected team, `GameplayInputFilter.IDENTITY`, and
`HudProfile.stock()`.

Mod API 2.4 intentionally adds no fixed-forward-movement controller, forced-camera or
scroll policy, world wrapping/rebasing runtime, or flight-fatigue rule. Fixed-camera
minigames should keep the player stationary, move and recycle their own obstacles,
filter unwanted directions, and use already-published character ability state.

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
