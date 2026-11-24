# Sonic 2 Native Debug Placement

**Date:** 2026-08-09
**Status:** Runtime implementation for Sonic 2 and chip-backed Knuckles in Sonic 2.

## Decision

The 2026-11-22 owner ruling replaces the original all-or-nothing activation
conditions. ROM placement is the default, and Settings also offers engine
free-fly. The engine owns the placement and preview implementation. The
cartridge owns controller behavior, zone list contents, player state changes
and gameplay gates. The initial contract ratchet is historical research.

`LevelDebugPlacement` is the shared rewindable session owner.
`LevelDebugPlacementProvider` supplies admission and level-load rules, cursor
constants and limits, the catalogue, preview and player reset. Only the S2
family supplies the service in this implementation.

## Source authority

The reference is Sonic 2 World REV01, SHA-1
`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`, built with `fixBugs = 0` and
`gameRevision = 1`.

The owning routines and data are:

- normal, hurt, and dead-player entry at `docs/s2disasm/s2.asm:36224-36230`,
  `38164-38170`, and `38255-38261`;
- `DebugMode` through `LoadDebugObjectSprite` (s2.asm:88580-88865);
- `DebugObjectLists` (s2.asm:88869-88887) and its row data; and
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

- With Engine free-fly selected and debug view enabled, `D` toggles the
  existing movement aid.
- Native placement may be entered in S2-family level play when the
  debug-cheat authority is enabled and player 1 presses B from the ROM-owned
  normal, hurt, or dead dispatch boundary.
- Native placement controls only the lead player. It must not toggle or
  repurpose the CPU sidekick's free-fly flag.
- A capability implementation must expose native state explicitly; it must not
  redefine `isDebugMode()` to mean two different things.

The sound-test code sets a session unlock that survives menu re-entry and
starting a new game from the title. The active level flag resets at the title.
`LoadLevel_VDP` (s2.asm:4839-4847) grants the active flag on a load with A held
after unlock. At KiS2 revision `c336fede`, the load rule is the same (s2.asm
lines 5159–5170), and the sound-test sequence has its own eight digits.
The active flag stays latched through act loads and respawns.

### State owner

The shared placement snapshot holds both cheat flags, the selected row, the
movement delay and speed and the saved vertical bounds. Cursor position uses
the primary player's native 16.16 coordinates and rewinds with that player.
The catalogue row determines the preview on draw. Spawned objects occupy
ordinary runtime slots and rewind through the object manager.

### Movement and exit

The S2 provider specifies `Debug_Control` (s2.asm:88657-88736) with a 12-frame
hold countdown, speed byte `$01` on entry and `$0F` after a neutral frame. A
direction edge moves immediately. On each repeat after the countdown, the
speed byte increases by one up to `$FF`. The 16.16 step is
`(speed + 1) << 12`, giving a one-pixel tap after neutral and a 16-pixel
maximum. Left clamps at zero. The S2 provider supplies the camera minimum Y
and target maximum Y plus 223 as vertical limits.

S1 `Debug_Control` in `_incObj/DebugMode.asm` and S3K `sub_92AD4` in
`sonic3k.asm` use the same shipped initial speed, countdown and acceleration.
Shipped S1 clamps Y to the absolute range 0–`$7FF`. The S3K routine uses
camera bounds as S2 does. Future providers can supply their own timing and
vertical limits.

Held A plus C cycles backward. A cycles forward, C places, and B exits.
Exit restores only `Camera_Min_Y_pos` and `Camera_Max_Y_pos_target`
(`Debug_ExitDebugMode`, s2.asm:88789-88821). SCZ's expanded X bounds and
changes made during placement to other camera bounds carry into gameplay.

### Catalogue, preview and allocation

`Sonic2LevelDebugPlacement` decodes the verified ROM's ordered lists and
subtypes, including the REV00 address variant and the KiS2 chip's two-entry
shared list. Previews use normal object sprite sheets and ring art. The
shipped REV01 EHZ waterfall row uses blank frame zero. Placement reserves a
live slot before asking the registry to create the object and rejects
placeholder factories. Placed rings have a slot-backed registry object and
use the existing stage-ring reward and art path.

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

## Verification and remaining coverage

The historical activation conditions included exact VDP-address previews,
every factory and global gate, and a dedicated cartridge-authored capture
before enabling the mode. The 2026-11-22 ruling supersedes that prerequisite.
Engine regressions use physical B/A/C inputs and exercise EHZ1 cursor timing,
rewind and sidekick solid contact, the CPZ tube, SCZ camera exit, ordinary
level-load admission and the chip-backed KiS2 cheat. The
[validation record](../validation/2026-08-09-s2-native-debug-placement.md)
preserves the original inventory run.

Remaining work includes the native HUD, three raw placement factories,
additional preview and gate coverage and dedicated cartridge recordings. See
[debug placement residuals](../../status/known-bugs.md#s2-debug-placement-object-and-gate-coverage).
Trace data remains comparison-only.
