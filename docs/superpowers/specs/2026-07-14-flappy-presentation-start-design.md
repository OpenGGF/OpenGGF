# Flappy Presentation and Game-Start Design

## Goal

Make the checked-in `sample-flappy` mod visually correct and make it the first
level entered by a fresh Sonic 2 no-save or save-slot launch. The minigame remains
endless; it does not need to complete into Emerald Hill Zone Act 1.

## Confirmed defects

The pipe corruption is a general baked-art ordering defect. `ArtConverter` emits
the patterns for one multi-tile mapping piece in row-major order, while
`SpritePieceRenderer` consumes the Genesis mapping-piece layout in column-major
order. Square pieces are effectively transposed and non-square pieces are
scrambled.

The black bird and life icon have a separate cause. A normal Sonic 2 level loads
the host character palette into CRAM line 0 from the ROM, but the additive mod-zone
loader copies all four authored palette lines verbatim. The Flappy export leaves
line 0 black because it assumes the host owns that line. The sample also puts sky
colours in palette positions used by Sonic 2 HUD labels, recolouring the HUD.

Finally, the current additive-zone contract supports only results-driven
`insertAfter` anchors. It has no representation for a zone that should be selected
as the initial destination of a new game.

## Art conversion

`ArtConverter` will emit multi-tile piece patterns in Genesis column-major order:
tile column first, then tile row. `SpritePieceRenderer` will remain unchanged
because it already matches ROM mapping tables and is used by native game art.

A converter regression test will use a non-symmetric multi-tile image with a
different marker in every tile. Reading the baked sheet and rendering its logical
tile order must reproduce the source image. The test must fail against the current
row-major converter before the converter changes.

## Palette ownership

Sonic 2 patch-mod levels will inherit the active host character palette for line 0
through the Sonic 2 level-loading path. Standalone levels will continue to own all
four palette lines because they have no base ROM or host character palette.

The Flappy level generator will keep its sky and pipe colours in mod-owned lines,
but will reserve the Sonic 2 HUD colour indices on the HUD text line. This fixes the
bird, life icon, and HUD without embedding copyrighted character palette bytes in
the sample JAR.

Tests will assert that an additive Sonic 2 level receives the ROM-backed character
palette on line 0, that standalone palette behavior is unchanged, and that the
Flappy palette leaves the required HUD entries intact.

## Game-start insertion

The zone contribution API will gain an explicit creator-facing
`ModZoneContribution.atGameStart(...)` factory. Its internal insertion point is
distinct from stock `insertAfter` anchors, so the framework does not pretend that
game start is a results boundary.

`ZoneProgressionPlan.Builder.insertBeforeStart(...)` will support an ordered list of
prepended zones, and `ZoneProgressionPlan.initial(...)` will expose the resulting
initial destination. The initial destination is the first enabled prepended zone;
if such a zone ever completes, normal successor resolution chains through any other
prepended zones and then into stock zone 0. Existing `insertAfter` behavior and
persisted synthetic zone indices remain unchanged.

`DataSelectHostProfile.newGameDestination()` will default to zone 0, act 0. Sonic
2's mod-decorated profile will resolve that destination from its zone registry.
Only `NO_SAVE_START` and a genuinely new save slot use the new initial destination;
existing saves, clear restarts, level select, time attack, and direct launches
retain their explicit destinations.

`sample-flappy` will register `flappy-garden` at game start. Starting Sonic 2 with
the mod enabled will therefore enter Flappy Garden immediately. Because the sample
is intentionally endless, no new goal object or forced transition to EHZ1 is added.

## Compatibility and scope

The new creator entry point and host-profile methods will be additive defaults so
existing Mod API implementations continue to link. The public Mod API will advance
from 2.2.0 to 2.3.0, and the sample's declared floor will become
`>=2.3.0 <3.0.0`. Documentation and validator expectations will be updated together.

This work will not change native ROM sprite ordering, add a Flappy completion
sequence, redesign the pipe artwork, or add a general mod GUI/direct-launch option.

## Verification

Verification will cover:

1. red/green converter tests for column-major multi-tile pieces;
2. red/green palette tests for Sonic 2 patch levels and Flappy HUD indices;
3. red/green progression and data-select tests for game-start contributions;
4. the ROM-backed `TestSampleFlappyIntegration` with an assertion that a fresh
   launch resolves to `sample-flappy:flappy-garden`;
5. `TestSampleModsPackage`, mod validation, and the relevant Mod API compatibility
   guards;
6. a fresh engine package, rebuilt `sample-flappy-mod.jar`, zero-finding validation,
   and installation into `mods/` for visual testing.
