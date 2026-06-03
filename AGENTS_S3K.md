# AGENTS_S3K.md — Sonic 3&K Implementation Details

This document captures S3K-specific implementation intricacies for AI agents and developers.
Referenced from [CLAUDE.md](CLAUDE.md) § "Sonic 3&K Bring-up Notes".

## Current Delivery Priority

S3K work should be planned around playable vertical slices, not isolated checklist closure.
The immediate route target is AIZ through HCZ, with CNZ, MGZ, and ICZ work feeding the
same standard. A slice is not "done" because its level loads or an object exists; it needs
coherent traversal, event/camera flow, scroll/parallax, animated tiles, palette/PLC state,
boss or transition behavior, sidekick-sensitive interactions, rewind-relevant state capture,
and trace or visual validation for known blockers.

When implementing slice features, use the runtime-owned shared systems where they apply:
`ZoneRuntimeRegistry`, `PaletteOwnershipRegistry`, `AnimatedTileChannelGraph`,
`ZoneLayoutMutationPipeline`, `ScrollEffectComposer`, `SpecialRenderEffectRegistry`, and
`AdvancedRenderModeController`. Broad S1/S2 or old-S3K migration is useful when it removes
active duplication or risk in code already touched by the slice; it should not displace
playable S3K progress.

## Per-Frame Palette Animation

### Overview

The ROM's `AnimatePalettes` routine runs **every frame** and dispatches to per-zone handlers
that cycle palette colors through ROM data tables. This is the `AnPal_*` system in the
disassembly (`sonic3k.asm:3105-3282`, `s3.asm:3245-3414`).

**Implementation:** `Sonic3kPaletteCycler` (called via `Sonic3kLevelAnimationManager` from
the level draw path — `LevelRenderer.drawWithRenderOptions` ticks
`animatedPaletteManager.update()` each frame), with writes composed through the runtime-owned
`PaletteOwnershipRegistry` via `S3kPaletteWriteSupport` before the frame is committed.

> Headless tests that bypass the renderer must explicitly tick the cycler — e.g.
> `LevelManager.getAnimatedPaletteManager().update()` — to advance palette state.

### Palette Animation vs. Palette Mutation

These are two distinct systems that both modify palette colors at runtime:

| System | Trigger | Example | Implementation |
|--------|---------|---------|----------------|
| **Palette Animation** (AnPal) | Timer-based, every N frames | AIZ waterfall shimmer, torch glow cycling | `Sonic3kPaletteCycler` |
| **Palette Mutation** (_Resize) | Camera-position threshold | AIZ1 hollow tree color 15 darkening at X≥$2B00 | `Sonic3kAIZEvents.applyResizePaletteMutation()` |

Palette mutations are one-shot writes in `_Resize` routines (event handlers), not cycling.
They should stay in `Sonic3kAIZEvents` (or the zone's event handler), typically routed through
the event helper methods backed by `PaletteOwnershipRegistry`, not in the cycler.

### The Counter/Step/Limit Pattern

Every AnPal channel follows the same ROM pattern:

```
counter += step
if counter >= limit: counter = 0
color = table[counter]
write color to palette_line + offset
```

Each channel has:
- **Counter** — current offset into the data table (from `Palette_cycle_counter0/1/counters+N`)
- **Step** — bytes to advance per tick (2 for 1-color, 6 for 3-color, 8 for 4-color writes)
- **Limit** — wrap point (total table size in bytes)
- **Timer** — frames between ticks (some channels share a timer via `Palette_cycle_counter1`)
- **Table** — ROM address of color data (`AnPal_PalXXX_N`)
- **Destination** — palette line + byte offset → engine palette index + color index

### Zone Inventory

10 zones have palette animation (entries from `OffsAnPal` dispatch table):

| Zone | ID | Act 1 Routine | Act 2 Routine | Status |
|------|----|---------------|---------------|--------|
| AIZ  | 0x00 | `AnPal_AIZ1` | `AnPal_AIZ2` | **Implemented** |
| HCZ  | 0x01 | `AnPal_HCZ1` | `AnPal_HCZ2` | **Implemented** |
| CNZ  | 0x03 | `AnPal_CNZ`  | `AnPal_CNZ`  | **Implemented** |
| FBZ  | 0x04 | `AnPal_FBZ`  | `AnPal_FBZ`  | N/A (no cycling) |
| ICZ  | 0x05 | `AnPal_ICZ`  | `AnPal_ICZ`  | **Implemented** |
| LBZ  | 0x06 | `AnPal_LBZ1` | `AnPal_LBZ2` | **Implemented** |
| LRZ  | 0x09 | `AnPal_LRZ1` | `AnPal_LRZ2` | **Implemented** |
| BPZ  | 0x0E | `AnPal_BPZ`  | `AnPal_BPZ`  | **Implemented** (competition) |
| CGZ  | 0x10 | `AnPal_CGZ`  | `AnPal_CGZ`  | **Implemented** (competition) |
| EMZ  | 0x11 | `AnPal_EMZ`  | `AnPal_EMZ`  | **Implemented** (competition) |

Zones with **no** palette animation (rts in dispatch): MGZ, MHZ, SOZ, SSZ, DEZ, DDZ, ALZ, DPZ.

### Implementation Priority

1. **AIZ** — Done. Fixes green fire in Act 2 (torch glow cycling) and waterfall shimmer.
2. **HCZ, LBZ, LRZ** — Commonly played zones with visible palette effects.
3. **CNZ, ICZ, FBZ** — Secondary priority.
4. **BPZ, CGZ, EMZ** — Competition zones, lowest priority.

### AIZ Channel Details

**AIZ Act 1 — Normal mode** (`AIZ1_palette_cycle_flag == 0`, timer period 8):

| Channel | Table | Step | Limit | Palette | Colors | Description |
|---------|-------|------|-------|---------|--------|-------------|
| 0 | `AnPal_PalAIZ1_1` | 8 | 0x20 | Line 3 (idx 2) | 11-14 | Waterfall shimmer (4 frames) |
| 1 | `AnPal_PalAIZ1_2` | 6 | 0x30 | Line 4 (idx 3) | 12-14 | Secondary water (8 frames, gated by level start) |

**AIZ Act 1 — Fire/intro mode** (`AIZ1_palette_cycle_flag != 0`, timer period 10):

| Channel | Table | Step | Limit | Palette | Colors | Description |
|---------|-------|------|-------|---------|--------|-------------|
| 2 | `AnPal_PalAIZ1_3` | 8 | 0x50 | Line 4 (idx 3) | 2-5 | Fire colors (10 frames) |
| 3 | `AnPal_PalAIZ1_4` | 6 | 0x3C | Line 4 (idx 3) | 13-15 | Fire secondary (10 frames) |

The flag is set at level start (intro) and cleared permanently at Camera X ≥ 0x1000.

**AIZ Act 2** (two independent timers):

| Channel | Table | Step | Limit | Timer | Palette | Colors | Description |
|---------|-------|------|-------|-------|---------|--------|-------------|
| 0 | `AnPal_PalAIZ2_1` | 8 | 0x20 | 6 frames | Line 4 (idx 3) | 12-15 | Water cycling (4 frames) |
| 1 | `AnPal_PalAIZ2_2/3` | 6 | 0x30 | shared w/0 | Lines 3+4 | 2:4,8 + 3:11 | Water trickle (camera-X switches at $3800) |
| 2 | `AnPal_PalAIZ2_4/5` | 2 | 0x34 | 2 frames | Line 4 (idx 3) | 1 | Torch/fire glow (camera-X switches at $3800) |

Channel 2 is the critical **green fire fix** — without it, fire sprites show green (vegetation palette).

### How to Add a New Zone

1. Find the zone's `AnPal_*` routine in the disassembly (`sonic3k.asm` or `s3.asm`)
2. Identify channels: counters, step sizes, limits, table labels, destination palette offsets
3. Use `RomOffsetFinder` to find ROM addresses for the data tables:
   ```bash
   mvn exec:java "-Dexec.mainClass=com.openggf.tools.disasm.RomOffsetFinder" \
       "-Dexec.args=--game s3k search AnPal_PalHCZ" -q
   ```
4. Add ROM addresses and sizes to `Sonic3kConstants.java`
5. Create a new `PaletteCycle` subclass in `Sonic3kPaletteCycler` (follow `Aiz1Cycle`/`Aiz2TorchCycle` patterns)
6. Register in `loadCycles()` switch case
7. Test with `SharedLevel.load(SonicGame.SONIC_3K, zoneId, act)` and verify palette changes

### Context: The 3-Day Debugging Story

AIZ Act 1 had persistent bright red artifacts in the hollow tree interior that survived 8+
fix attempts. The root cause was a missing **per-frame palette mutation** — the ROM's
`AIZ1_Resize` stage 2 dynamically writes `palette[2][15]` from bright red (`$020E`) to nearly
black (`$0004`) when cameraX ≥ `$2B00`. The engine loaded palettes once and never mutated them.

This discovery revealed the broader `AnimatePalettes` system: 10 zones cycle colors every frame
through ROM data tables. Without implementing this, fire objects in AIZ Act 2 rendered green
(they use palette line 4 color 1, which needs the torch glow cycling to show fire colors).

**Key insight:** Palette _animation_ (timer-based cycling in `AnPal_*`) and palette _mutation_
(camera-threshold writes in `_Resize` routines) are separate systems that both modify palettes
at runtime. Both must be implemented for visual accuracy.

## Agent Workflow Tooling

Five `com.openggf.tools` CLIs cut context loss on S3K object/zone/trace work. Full
PowerShell-quoted invocations and companion docs are in
[docs/agent-workflow/README.md](docs/agent-workflow/README.md) (with per-task runbooks under
`docs/agent-workflow/runbooks/`, plus a CI guard-failure explainer, pitfall index,
documentation-obligation checklist, and delegation prompt templates).

| Tool | Purpose | Invocation |
|------|---------|------------|
| `AgentWorkflowTool` | Preflight checklist for an object task: resolves the S3K object space (`S3KL` / `SKL` / `S3L`), registry status, `RomOffsetFinder` commands, required guards, and docs. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.AgentWorkflowTool" "-Dexec.args=object s3k MHZ 0x8A"` |
| `RomArtIntakeTool` | S&K-side-preferring ROM art/mapping/PLC intake; wraps `RomOffsetFinder --game s3k`. **Flags** (caution, not a hard reject) `s3.asm`-sourced labels (the S3L standalone half) — it classifies by source file, since a label search carries no ROM offset. Prefer an S&K equivalent; if an object has none, the S3-half reference is legitimate (rare; verify). Recommends `StandaloneArtEntry` vs `LevelArtEntry`, suggests `Sonic3kConstants` names + `Sonic3kPlcArtRegistry` hints. Accepts multiple labels. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.RomArtIntakeTool" "-Dexec.args=ArtNem_AIZSwingVine Map_AIZSwingVine"` |
| `ObjectScaffoldTool` | Guard-friendly object/badnik skeleton + JUnit5 test shell (no `getInstance()`, no ctor `services()`, no `addDynamicObject`/`setDestroyed`; center-coord note). `--game s3k --badnik` emits the `com.openggf.game.sonic3k.objects.badniks` package extending `AbstractS3kBadnikInstance`. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.ObjectScaffoldTool" "-Dexec.args=--game s3k --class MhzFooObjectInstance --id 0x8A --badnik"` |
| `TraceTriageTool` | Reads `target/trace-reports/<game>_<zone>_report.json` and prints a first-divergence brief (frame/field, ROM vs engine, likely owning subsystem, disasm search terms). Comparison-only; never hydrates engine state. | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceTriageTool" "-Dexec.args=s2 mtz1"` |
| `ZoneSpecNormalizerTool` | Normalizes an `s3k-zone-analysis` spec into the stable 13-section layout (palette cycling vs mutation kept separate; `(not analyzed)` placeholders for gaps). | `mvn exec:java "-Dexec.mainClass=com.openggf.tools.ZoneSpecNormalizerTool" "-Dexec.args=<path-to-zone-analysis-spec.md>"` |

## Reusable Engine Utilities for S3K Objects

When implementing S3K objects, bosses, or badniks, **always check for existing utilities before writing new code**. The following patterns have been reimplemented multiple times and should be reused:

### Object Behavior Contracts

New S3K object, boss, badnik, and trace work should use the shared object-physics contracts when they fit:

- Use `ObjectControlState` for native object-control bit intent and derived movement, touch, solid, and CPU predicates. Do not add raw object-control setter combinations unless they are compatibility bridges with tests.
- Use `ObjectPlayerQuery` plus an explicit `ObjectPlayerParticipationPolicy` for player selection. Distinguish native P1/P2 slot behavior from OpenGGF's extended multi-sidekick behavior deliberately.
- Use `NativePositionOps` for playable-sprite native `x_pos` / `y_pos` writes. Raw preserve-subpixel centre setters are for lower-level sprite internals or non-playable/object-local state.
- Use `ObjectLifetimeOps` for destruction, offscreen expiry, respawn-latch updates, dynamic expiry, and slot transfer semantics.
- Declare canonical `SolidRoutineProfile`, `TouchResponseProfile`, and `ObjectLifecycleProfile` values when they preserve current behavior. Leave bespoke boss or multi-part object behavior explicit and covered by focused tests.

### Physics & Movement

| Utility | Package | ROM Equivalent | API |
|---------|---------|----------------|-----|
| `SwingMotion.update()` | `com.openggf.physics` | `Swing_UpAndDown` (sonic3k.asm:177851) | `Result update(accel, vel, max, dirDown)` → `Result(velocity, directionDown, directionChanged)` |
| `ObjectTerrainUtils` | `com.openggf.physics` | `ObjCheckFloorDist` etc. | `checkFloorDist(x, y, yRadius)`, `checkCeilingDist()`, `checkLeftWallDist()`, `checkRightWallDist()` |
| `TrigLookupTable` | `com.openggf.physics` | `CalcAngle`, `GetSineCosine` | `calcAngle(dx, dy)`, `sinHex(angle)`, `cosHex(angle)` |

**Subpixel movement** (ROM's `MoveSprite` / `MoveSprite2`) — use `SubpixelMotion` utility (`com.openggf.level.objects`). `AbstractS3kBadnikInstance.moveWithVelocity()` also provides the standard 24-bit position arithmetic.

### Collision & Touch Response

| Interface | When to Use | `getCollisionFlags()` Return |
|-----------|-------------|------------------------------|
| `TouchResponseAttackable` + `TouchResponseProvider` | Destroyable enemies (normal badniks, boss bodies) | `sizeIndex & 0x3F` |
| `TouchResponseProvider` only | Non-destroyable hazards (body segments, projectiles, spikes) | `0x80 \| (sizeIndex & 0x3F)` |

### Object Infrastructure

| Class | Package | Purpose |
|-------|---------|---------|
| `AbstractS3kBadnikInstance` | `game.sonic3k.objects.badniks` | Base for attackable badniks. Provides `defeat()`, `spawnProjectile()`, `moveWithVelocity()`, rendering via `PatternSpriteRenderer`. |
| `S3kBadnikProjectileInstance` | `game.sonic3k.objects.badniks` | Reusable projectile with gravity, HURT collision, shield deflection. |
| `BoxObjectInstance` | `game.sonic2.objects` | Invisible trigger zones with debug box rendering. Used by AutoSpin. |
| `Sonic3kPlcArtRegistry` | `game.sonic3k` | Zone art registration. Add `StandaloneArtEntry` for dedicated-art objects, `LevelArtEntry` for PLC-loaded art. |

### Player State Manipulation

**Force roll** (ROM's `loc_1E9B6`): check `getRolling()`, call `setRolling(true)`, adjust Y via `getRollHeightAdjustment()`, set animation via `ScriptedVelocityAnimationProfile.getRollAnimId()`, play `GameSound.ROLLING`.

**Pinball mode** (`setPinballMode(true)`): prevents rolling from clearing on landing (ROM's `spin_dash_flag` bit 0).

### Child Object Spawning

Prefer `spawnChild()` for runtime children (body segments, projectiles, explosions):
```java
ChildObject child = spawnChild(() -> new ChildObject(spawn, params));
```

Legacy pattern (still works): `services().objectManager().addDynamicObject(childInstance)`. If called during the update loop, additions are queued and flushed after the frame.
