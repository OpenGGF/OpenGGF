---
name: s1-implement-object
description: Use when implementing or changing a Sonic 1 object or badnik from the disassembly. For a boss, use s1-implement-boss.
---

# Sonic 1 objects and badniks

Locate the existing implementation, registry entry, and ROM routine before
adding a class. The object ID, zone/act, subtype, and character path determine
which behavior is required; derive unspecified details from the requested route.

## Porting checklist

- Read the routine dispatch, init fallthrough/return, movement, collision,
  animation, sound, child spawning, and deletion paths that the object can take.
  Preserve 68000 widths, signed comparisons, update order, and timer expiry edges.
- Use ROM center coordinates and `NativePositionOps` for playable-sprite writes.
  Radius/status changes do not imply a ROM position change. Distinguish the
  object-visible V-int clock from executed frames and the level clock.
- Reuse the closest existing behavior contract. The utility/API reference is
  `docs/architecture/object-implementation-reference.md`; consult the relevant
  part for movement, touch, solids, control, participation, or lifetime behavior.
- Keep native player-slot semantics explicit. Check per-player state, child-to-parent
  writes, allocation failure, and same-pass update visibility where the ROM uses them.
- Load art, mappings, DPLCs, and animation data through the ROM pipeline. Verify
  addresses and data shape, then register art and the object factory.
- Transcribe the sprite bucket: `move.b #n,obPriority(a0)` is bucket `n`
  (0 = front-most) and goes through `RenderPriority.bucket(n)`; override
  `getPriorityBucket()` on every class that draws, returning `bucket(0)` with the
  citation when the ROM leaves priority at 0. The art word's bit 15
  (`make_art_tile(.., 1)` / `bset #7,obGfx`) is `isHighPriority()`, never a bucket.
  Copy runtime priority writes and parent-to-child copies at the same points.
- Capture new persistent state and recreate paths for rewind. Exercise the
  affected routine edge/subtype with focused tests and the relevant object/art guards.
  Use trace replay when it provides evidence for the changed behavior.

## Game-specific entrypoints

S1 uses byte-count, 5-byte mapping pieces and unified solidity;
`setTopSolidBit()` / `setLrbSolidBit()` do not select an S1 collision plane.
Find object code under `docs/s1disasm/_incObj/`, mappings under `_maps/`,
and animations under `_anim/`.

Start code searches in `src/main/java/com/openggf/game/sonic1/`.
Use `Sonic1ObjectRegistry` for registration and the game's art provider for rendering.
Check the existing object checklist/profile when implementation coverage changes.

## Conditional references

- For label/address lookup, use `../s1disasm-guide/SKILL.md`.
- Search [ROM pitfalls](rom-pitfalls.md) by symptom or routine before reading matching
  entries: `rg -n '^##|standing|timer|child|touch|slot' <skill-dir>/rom-pitfalls.md`.
  These are source-cited examples, not a requirement to read the full catalog or
  apply an S2 convention to another game without checking its routine.
- For art queues or PLC parsing, use `../plc-system/SKILL.md`;
  for a failing replay, use `../trace-replay-bug-fixing/SKILL.md`.

Read only references needed for the behavior being changed. Integration and
project-wide verification follow the repository instructions.

## Per-act coverage obligation

Follow the [zone and act test standard](../../../docs/guide/contributing/level-test-standard.md)
for this change's affected contracts and the owning act/character-route matrix.
New acts require the full applicable matrix; local changes record inherited gaps.
Include supported viewport/donor/team breadth and the applicable rewind spots:
events, interactions, boss graphs, camera locks, world changes and loads/respawn.
Prove restoration and forward replay, or timeline isolation at an intentional reset.
Keep short checks independent of full routes and update the
[coverage backlog](../../../docs/status/level-test-coverage.md) in the same delivery.
