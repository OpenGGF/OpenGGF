# Sonic 3&K Mod-Zone Adapter Design

## Status and scope

This design promotes the scheduled Sonic 3&K mod-zone adapter because the
`sample-flappy` project is now its concrete adopter. It is the reusable foundation;
Flappy-specific controls, HUD changes, obstacle rules, and art fixes are specified
separately in `2026-07-14-flappy-native-tails-design.md`.

The adapter adds original-data patch zones to an S3K host. It does not make mod
assets a ROM fallback, change stock S3K loading, emulate stock zone events for
creator zones, or generalise the S1 adapter at the same time.

## Existing constraint

The current additive-zone path is deliberately Sonic 2-only. `ModContext` rejects
other base games and `ModZoneLoader` always constructs a `Sonic2Level`. Reusing that
constructor would lose S3K's zone-set identity and bypass its palette, animation,
mutation, scroll, render, event, and rewind obligations.

The new path therefore introduces an adapter boundary owned by the base game. Shared
mod runtime code resolves a prepared contribution through that boundary; it does not
branch on a raw game name.

## Adapter boundary

A game module may expose a mod-zone adapter describing whether it accepts a prepared
zone, how it validates game-specific metadata, and how it constructs the resulting
`Level`. The S2 implementation wraps the existing loader unchanged. S3K supplies a
new implementation backed by an original-data S3K level construction path.

`ModBackedGamePatch.loadLevelOverride(...)` asks the resolved module adapter to load
the contribution. A module without an adapter rejects additive zones at registration
with an owner-attributed finding. The shared patch decorator never tests `s2`, `s3k`,
or a concrete module class.

The S3K implementation builds from the bounded `ModAssetRoot` data already captured
by `PreparedModZone`. The published adapter boundary receives the engine-supplied
owner id and already-published immutable `ModLevelDefinition`; it does not expose
the engine-owned `PreparedModZone` record through the recursive Mod API. ROM reads
remain permitted only for host-owned shared assets
that the contract names explicitly, such as the active character and ring art. The
level's patterns, blocks, chunks, layouts, collision profiles, boundaries, objects,
and zone palette all come from the mod export.

## Identity and format contract

The public zone identity remains an owner-tagged `ZoneKey`; synthetic runtime indices
remain an internal compatibility detail. An S3K contribution additionally declares
which stock object-addressing vocabulary it uses: `S3KL` or `SKL`. This declaration
is typed and validated rather than inferred from the synthetic numeric index.

The declaration controls stock object ID interpretation only. Namespaced mod objects
remain owner-keyed and independent of either stock table. An unknown declaration, a
stock object incompatible with the selected set, or an omitted declaration when a
stock object is present prevents publication. A zone using only namespaced objects
uses the documented `S3KL` default.

The baked-level format remains deterministic and bounded. If game-specific metadata
requires a format revision, the parser accepts the prior version for S2 and
standalone content while requiring the new S3K metadata only on S3K contributions.
No field is reinterpreted based on file names or asset contents.

## Original-data S3K level

S3K receives an in-memory construction path with the same validated outcomes as its
ROM-backed loader:

- an 8-by-8 block grid and validated pattern, block, and chunk references;
- foreground and optional background maps with explicit dimensions;
- primary and secondary collision indices and solid profiles;
- explicit world boundaries, start position, object spawns, and ring spawns;
- a character palette supplied by the host and three creator-owned level lines;
- inherited ring art and character art from the active S3K module;
- a stable tagged zone identity and declared object zone set.

This is a focused S3K builder or sibling level type, not a constructor full of dummy
ROM addresses. Stock `Sonic3kLevel` construction continues to use locked-on ROM
addresses and `LevelResourcePlan` exactly as it does today.

## Runtime-framework obligations

Before a custom S3K level is published, the adapter supplies a runtime profile for
each S3K-owned framework:

- `ZoneRuntimeRegistry`: namespaced state owned by the contribution;
- `PaletteOwnershipRegistry`: a new initial-palette/HUD bridge described below,
  plus normal creator runtime writes;
- `AnimatedTileChannelGraph`: declared channels, or an explicit empty graph;
- `ZoneLayoutMutationPipeline`: normal mutable-level routing and dirty regions;
- `ScrollEffectComposer`: a declared creator scroll policy, defaulting to flat
  foreground/background tracking;
- `SpecialRenderEffectRegistry`: an explicit empty contribution by default;
- `AdvancedRenderModeController`: normal plane rendering unless declared otherwise;
- level events: an owner-fault-bounded creator event factory or a no-event manager;
- PLC progress: an empty completed plan unless the format later gains explicit,
  bounded runtime art loads.

No custom zone is allowed to inherit AIZ, HCZ, or another stock zone's handler merely
because its synthetic index overlaps a ROM zone. Unsupported requested behavior is a
validation error; it is never silently approximated by a stock handler.

## Palette ownership

S3K character palette line 0 remains host-owned and is loaded directly into
`palettes[0]` for the resolved main character, matching current `Sonic3kLevel`
construction. Creator zone art supplies declared entries on level lines 1 through 3.

This composition is new work. `PaletteOwnershipRegistry` currently arbitrates only
submitted runtime writes by integer priority; it does not reserve initial palette
entries or give the HUD intrinsic precedence. `HudRenderManager`'s current lives
palette override also uploads directly and bypasses the registry. The adapter work
adds an internal host-palette bridge that:

- validates creator declarations against character and HUD-reserved entries before
  publication;
- exposes the S3K HUD provider's required palette entries as host-owned claims,
  keeping the live icon override on line 0 and supplying reserved line-1 cells
  from the canonical ROM-derived `Pal_AIZ` values rather than creator colors;
- submits HUD-visible palette writes through `PaletteOwnershipRegistry` at a defined
  host priority instead of using the direct lives-palette upload path; and
- leaves stock level construction byte-for-byte unchanged when no custom zone is
  active.

The export/manifest records creator-owned entries rather than requiring four complete
palette lines. Duplicate ownership, a write into a reserved character/HUD entry, an
out-of-range entry, or a mismatch between indexed art and its declared palette is
rejected before launch. Standalone games retain their existing all-lines-owned
behavior because they have no host character palette.

## Save, disable, and progression behavior

Save and progression continue to store tagged `ZoneKey` identities. Resuming an
enabled S3K custom zone resolves the same contribution and its declared zone set. If
the owner is disabled or removed, the existing safe fallback policy selects the
nearest valid stock destination; no synthetic numeric index is trusted as a stock
zone.

S3K currently exposes no results-driven stock anchors. An S3K contribution may
therefore omit `insertAfter`; the zone is addressable by tagged identity but is not
inserted into `ZoneProgressionPlan`. Existing S2 behavior remains unchanged: an
omitted anchor still receives the historical `mtz3` default. The gameplay-policy
revision uses this anchorless form for an explicit game-start zone; it does not add
`aiz1` or any cutscene handoff to `StockProgressionAnchors`.

New-game insertion is an independent data-select contribution described by the
Flappy design. The S3K adapter supports being selected as a data-select initial
destination but does not make every custom S3K zone a game-start zone or change
`ZoneProgressionPlan`'s results-successor responsibility.

## Rewind and lifecycle

The constructed level participates in `MutableLevel` snapshot epochs and normal
gameplay context teardown. The adapter registers all runtime state that can affect a
future frame. Empty managers still provide explicit deterministic snapshots where
the shared framework requires them.

Publication is transactional. Parser, adapter validation, asset decoding, registry
initialisation, or creator-factory failure disables the owner and its dependents
without retaining a partially created level or global S3K state. Removing the mod
and opening a new gameplay session restores stock behavior.

## Compatibility

The creator-facing additions are recursive `@ModApi` types with immutable values and
defaults that preserve existing S2 and standalone mods. This independently
mergeable adapter revision advances Mod API 2.2.0 to 2.3.0 and freezes 2.3 as a
closed historical baseline. The subsequent gameplay-policy revision advances to
2.4.0 rather than mutating the published 2.3 snapshot. Each revision advances its
SDK, validator, handbook, compatibility snapshot, and relevant API floor
declarations together.

## Verification

Test-first delivery must cover:

1. adapter selection without raw game-name branches;
2. headless construction of a minimal original-data S3K zone;
3. tagged zone identity and explicit `S3KL`/`SKL` object interpretation;
4. rejection of incompatible stock objects and unsupported runtime requirements;
5. host character palette composition and creator palette bounds;
6. explicit empty animation, PLC, event, scroll, and render contracts;
7. mutable-level, save/resume, disable fallback, and rewind round trips;
8. unchanged S2 mod-zone and standalone construction;
9. the S3K must-keep-green suite and representative stock-route trace spots;
10. package, Mod API compatibility, isolation, and hostile-input validation guards.

The maintained minimal fixture is the native-Tails Flappy zone, but adapter tests use
a smaller neutral fixture so foundation failures are distinguishable from Flappy
gameplay failures.

## Delivery boundary

This specification receives its own test-first implementation plan and lands before
the gameplay-policy and Flappy-rebuild plans. It does not absorb game-start launch
policy, input filtering, HUD layout policy, Flappy objects, or the separate Sonic 2
ROM-art sample.
