# Sonic 2 Native Debug Placement

**Date:** 2026-08-09
**Status:** design-ready, runtime capability still unavailable

## Decision

Do not map Sonic 2's native `Debug_placement_mode` onto OpenGGF's existing
free-fly debug movement and do not advertise level debug placement yet. The
shipped mode is a level-wide player state with its own input, preview, object
allocation, camera, scroll, collision, event, and exit semantics. OpenGGF has
most of the referenced object factories, but it does not yet have an accurate
preview-art path, a dynamic stage-ring path, or a distinct state owner through
which every global gate can be expressed.

The evidence is safe to strengthen incrementally behind an unavailable
boundary, but production behavior is not. `DebugModeProvider.hasLevelDebug()`
remains `false` until the activation gates in this document are all satisfied.
An isolated parser, preview resolver, spawn API, or controller without a live
complete consumer would be more unfinished code, so pre-activation slices are
test/research probes against existing owners only. The production components
land together with the complete controller route and capability activation.

## Source authority

The reference is Sonic 2 World REV01, SHA-1
`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`, built with `fixBugs = 0` and
`gameRevision = 1`.

The owning routines and data are:

- normal, hurt, and dead-player entry at `docs/s2disasm/s2.asm:36224-36230`,
  `38164-38170`, and `38255-38261`;
- the native state machine, movement, selection, allocation, and exit at
  `s2.asm:88453-88740` (`DebugMode` through `LoadDebugObjectSprite`);
- the zone-ordered catalog and record macros at `s2.asm:88742-88774`;
- the catalog rows at `s2.asm:88776-89079`; and
- globals at `docs/s2disasm/s2.constants.asm:1666-1671` and `1896`.

The REV01 ROM contains the `DebugObjectLists` table at `$41D0C`. The table has
17 zone slots. Its zone-expanded lists contain 340 rows; counting each aliased
list definition once gives 265 rows and 117 unique object IDs. HPZ and OOZ
share the same 33-row list. Zones without a dedicated list use the two-row
ring/monitor default.

Each catalog row is eight bytes:

```text
u32 object-id/high-byte + 24-bit mappings address
u8  subtype
u8  preview mapping frame
u16 preview art_tile
```

The word at the selected list head is its row count. All reads must use the
user-supplied ROM pipeline; the disassembly is research evidence, not a runtime
asset fallback.

## Native contract

### Identity and entry

Native placement and engine free-fly are different modes.

- `D` continues to toggle the shared `AbstractPlayableSprite.debugMode`
  movement aid when engine debug view is enabled.
- Native placement may be entered only for Sonic 2 level play, when the S2
  debug-cheat authority is enabled and player 1 presses B from the ROM-owned
  normal, hurt, or dead dispatch boundary.
- Native placement controls only the lead player. It must not toggle or
  repurpose the CPU sidekick's free-fly flag.
- A capability implementation must expose native state explicitly; it must not
  redefine `isDebugMode()` to mean two different things.

The existing `launch.s2.debugTools` option is suitable authority for enabling
the retail debug-cheat path, but enabling that option alone must not claim that
placement exists. The native controller, once complete, interprets A/B/C and
directions from `PlayerInputState`'s separate action masks; it must not collapse
the three Mega Drive buttons into the engine's generic action binding.

### State owner

A module-owned `Sonic2LevelDebugPlacementController` is the smallest accurate
owner. It is created and reset with the S2 level session, receives services by
injection, and exposes a read-only state/predicate port to the generic frame
loop and the S2 subsystems that have shipped debug gates. Its mutable state is:

- inactive, init, or main routine;
- selected catalog row;
- 16.16 cursor position (the lead player's native `x_pos`/`y_pos` storage);
- acceleration timer and speed;
- saved camera minimum Y and target maximum Y; and
- any explicit preview handle needed by the renderer.

The controller owns entry, same-pass initialization, movement, cycling,
spawning, and exit. `GameLoop`, `SpriteManager`, or `Engine` must not acquire an
S2-only branch. The game module exposes the optional typed controller at the
same boundary as its other game-specific services.

The controller state is captured by a module rewind adapter. A rewind while
active must restore the catalog cursor, acceleration state, saved camera
bounds, position, preview, and every global gate. A rewind across entry or exit
must restore the correct active/inactive state without replaying input or
creating an object.

### Movement and exit

The implementation ports `Debug_Control` directly:

- a newly pressed direction moves at the initial step;
- holding a direction counts the acceleration timer down from `$0C`, then
  raises the byte-sized speed with its shipped wrap behavior;
- releasing all directions sets timer `$0C` and speed `$0F`;
- position remains 16.16, X clamps at zero, and Y clamps to the current camera
  minimum and target maximum plus 223;
- A press cycles forward; held A plus C press cycles backward; C otherwise
  spawns; B exits; and
- entry applies the SCZ camera-X expansion, `$7FF` Y masks, scroll-lock clear,
  cursor animation, and catalog-index normalization from the ROM.

Exit restores the regular Sonic mappings, art tile, animation, collision,
camera, and status path before clearing native state. The shipped `fixBugs = 0`
branches are intentional compatibility requirements: entry does not clear the
in-air bit, allocation does not clear the preview's on-screen render flag, and
exit clears underwater status and forces in-air even though the source marks
the consequences as bugs. Each ported site must name that choice in a comment.

### ROM-backed catalog

`Sonic2DebugPlacementCatalog` decodes the selected zone's table and rows from
the verified S2 ROM. It validates table/list bounds, count arithmetic, object
ID, 24-bit mappings address, subtype, frame, and art tile. It preserves table
aliasing as data rather than copying zone-specific lists or branching on zone
names.

The catalog is not itself a capability. Its production decoder is introduced
only in the same change as the complete production controller and activation
route. Tests may independently decode the ROM contract before then to ratchet
the evidence without shipping an unused runtime abstraction.

### Preview rendering

The preview is not the selected object's initialized sprite. The ROM loads the
catalog's mappings address, frame, and VDP `art_tile` directly into the player
object and calls `DisplaySprite`. OpenGGF instead decompresses S2 object art
into named sprite sheets and allocates virtual atlas pattern IDs. Those IDs do
not preserve the VDP destinations encoded by `art_tile`.

`Sonic2DebugPreviewResolver` must therefore prove a ROM-backed association
between the selected catalog tuple and currently loaded art. The intended
input is `(zone/act context, mappings address, mapping frame, art_tile)`; the
output is an immutable render descriptor for the normal mapping renderer. The
resolver may join the existing PLC definition's ROM source and destination
with `Sonic2PlcArtRegistry`/`Sonic2ObjectArtProvider`, but it must not infer a
sprite sheet from an object class name, substitute another object's frame, or
render `art_tile + mappingTile` against the virtual atlas.

Every one of the 265 rows across unique list definitions must either resolve
to the exact currently loaded art/mappings or reproduce the ROM's blank
preview. In particular, with `fixBugs = 0`, EHZ waterfall subtype 0 uses the
blank frame 0 rather than the source's fixed frame 1.

### Allocation and lifecycle

Spawning uses an injected `Sonic2DebugSpawnService`, not a registry singleton.
It preserves the ROM's forward first-free object-slot allocation, centre
coordinates, object ID, subtype, status/render-flag transfer, and
`no_balancing` clear. Allocation failure leaves the mode active and creates
nothing.

There are two explicit spawn routes:

1. ring rows add a normal stage ring through a new `RingManager` dynamic-ring
   operation with collection, rewind, and slot/lifetime semantics; and
2. object rows request a placement-safe factory from the S2 object registry.

A registered factory is not sufficient evidence. The debug catalog references
117 unique IDs and the registry currently reports factories for 113. The four
without a registry factory are `$25` Ring, `$46` OOZ Ball, `$73` Rotating
Rings, and `$D3` Bomb Prize. `$46` is an unused beta leftover and must remain
unimplemented unless its shipped routine is deliberately ported. `$D3` is
currently modeled only as a parent-dependent dynamic child. All 117 IDs,
including zone-aware registrations and parent/child objects, require
construction/lifecycle tests from their catalog rows. A placeholder is never a
successful native spawn.

The service must reject an unsupported row atomically while the capability is
under development. Full mode advertisement requires no unsupported row in any
reachable REV01 list.

### Global gates

REV01 with `fixBugs = 0` has more than thirty compiled behavioral reads of
`Debug_placement_mode` outside the controller and reset paths. They span:

- player dispatch, death, touch, solid-object, checkpoint, signpost/end-point,
  forced-spin, plane-switch, grab, tube, launcher, oil, and vine behavior;
- event and camera/deformation/scroll behavior, including SCZ and WFZ; and
- object-family behavior in CPZ, MTZ, CNZ, OOZ, WFZ, and SCZ.

Each site must be classified against the shipped branch and assigned to its
existing semantic owner. Where engine free-fly and native placement genuinely
share an interaction bypass, a narrowly named predicate such as
`isInteractionBypassActive()` may combine them at that boundary. The identity
and controller state remain separate. No CPZ-only or zone-name branch may
stand in for the global mode.

REV00-only and `fixBugs = 1` sites are excluded from the REV01 implementation,
but the audit must record them so a later revision profile does not silently
inherit the wrong behavior.

## Activation gates

`hasLevelDebug()` may become `true` only when one reviewed change set proves:

1. all 17 zone slots and all 265 unique-list rows decode from REV01 with exact
   table aliases and `fixBugs = 0` variants;
2. every row has exact preview behavior through the production renderer;
3. every row has placement-safe lifecycle behavior, including rings and the
   four currently missing factory paths, with no placeholder fallback;
4. entry, input, movement, selection, allocation failure, spawn, and exit match
   the disassembly;
5. every compiled global gate has an owning implementation and focused test;
6. the entire state and spawned-object graph survives rewind and session reset;
7. ordinary S2 play and engine free-fly retain their current behavior; and
8. a dedicated controller-authored BK2 is captured against the verified REV01
   ROM and replayed through an end-to-end OpenGGF test covering entry, both
   cycle directions, ring and object spawn, allocation failure or full-slot
   behavior, and exit. A second capture must exercise representative
   zone-specific gates, including CPZ and SCZ.

The existing ordinary S2 traces cannot close this evidence gate: their historic
recorder sampled `$FE08` (`Debug_placement_mode`) as a frame counter and the
normal movies leave the byte at zero. Trace data remains comparison-only and
must never select a catalog row or hydrate controller state.

## Delivery slices

1. **Contract ratchet (test-only):** ROM-backed tests assert the table address,
   list counts/aliases, record decoding, shipped conditional rows, current
   registry inventory, and the honest unavailable capability. They decode
   directly from existing ROM readers and do not add a production catalog.
2. **Readiness probes (test/research-only):** use the existing PLC definitions,
   art registry/provider, ring manager, object registry, and rewind harnesses to
   produce row-by-row preview and lifecycle inventories. Close evidence that
   can be closed inside those existing owners; record the missing associations
   and construction paths. Do not add production resolver metadata, dynamic
   ring APIs, placement factories, or controller code in this slice.
3. **Reference evidence (test/tool-only):** record the dedicated REV01 BK2s and
   read-only native state/object-slot evidence needed by the activation test.
   This may add comparison fixtures and test tooling, but cannot drive gameplay
   or create a partial engine capability.
4. **Coherent production activation:** in one reviewed change, add and wire the
   production catalog, exact preview bridge, dynamic-ring and placement-safe
   spawn routes, module controller, all compiled global gates, rewind/session
   ownership, and the dedicated replay coverage. Set `hasLevelDebug()` true and
   update player-facing documentation only after the same change satisfies
   every activation gate.

A slice that adds unused runtime classes or an inaccessible partial controller
is rejected even if its unit tests pass.

## Non-goals

- Replacing or removing engine free-fly movement.
- Adding Sonic 1 or Sonic 3&K placement modes.
- Implementing Sonic 2 competition/human-P2 mode.
- Supporting REV00 or a hypothetical `fixBugs = 1` ROM profile.
- Reading mappings or art bytes from `docs/s2disasm` at runtime.
- Treating factory count, an ordinary trace, or a CPZ interaction fix as proof
  that native placement is complete.

## Current disposition

The audit materially narrows the feature from “add ring/item placement” to the
four delivery slices above and provides exact activation evidence. Runtime
implementation stops at the unavailable boundary for now because preview
rendering and four lifecycle paths are unresolved architectural dependencies.
The next safe executable change is the test-only contract ratchet, followed by
test/research readiness probes; it is not a controller toggle or a production
preview/spawn scaffold.
