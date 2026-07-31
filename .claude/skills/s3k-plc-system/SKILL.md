---
name: s3k-plc-system
description: Use when working with the S3K Pattern Load Cue system — runtime art loading, act transition PLCs, boss art, PLC table format.
---

# S3K Pattern Load Cue (PLC) System

> **Cross-game note:** The PLC binary format is shared across S1, S2, and S3K. The game-agnostic parser is `PlcParser` in `level.resources`. See the `plc-system` skill for the cross-game reference. This file covers S3K-specific PLC IDs, runtime loading, and GPU texture refresh.

Reference for the S3K Pattern Load Cue system: ROM format, runtime loading, GPU texture refresh, and zone event integration.

## Mandatory Art Corruption Guard Tests

When adding or changing S3K PLC-backed object art, standalone art, level-art mapping addresses, decompression sources, or `Sonic3kPlcArtRegistry` entries, run:

```bash
mvn "-Dtest=TestSonic3kPlcArtRegistry#s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits" test
```

For assets with a known disassembly shape, add a focused `TestSonic3kPlcArtRegistry` test for exact frame count, piece count, tile dimensions, and tile indices. Keep `TestPatternSpriteRendererCorruptionGuard` passing when changing sprite rendering or sheet construction; it is the engine-level guard that logs and suppresses massive corrupted sprite frames at runtime.

## Agent Workflow Tooling

Use these when sourcing PLC/art/mapping bytes from the ROM and wiring them into `Sonic3kPlcArtRegistry`:

- **`RomArtIntakeTool`** — S3K ROM-backed art/mapping/PLC intake. Wraps `RomOffsetFinder --game s3k`, flags (caution, not a hard reject) `s3.asm`-sourced labels (the S3L standalone half — classifies by source file). Prefer an S&K equivalent; if an object has none, the S3-half reference is legitimate (rare; verify). Recommends `StandaloneArtEntry` vs `LevelArtEntry`, and suggests `Sonic3kConstants` names plus `Sonic3kPlcArtRegistry` hints. Accepts multiple labels:
  ```bash
  mvn exec:java "-Dexec.mainClass=com.openggf.tools.RomArtIntakeTool" "-Dexec.args=ArtNem_AIZSwingVine Map_AIZSwingVine"
  ```
- **Runbook:** `docs/agent-workflow/runbooks/runbook-rom-art-mappings-plc.md` — step-by-step ROM art/mapping/PLC intake workflow.
- **CI guard explainer:** `docs/agent-workflow/ci-guard-failure-explainer.md` — maps `TestSonic3kPlcArtRegistry` / `TestPatternSpriteRendererCorruptionGuard` failures to the correct fix.

## PLC Table Format

### Offs_PLC Offset Table

Located at `Sonic3kConstants.OFFS_PLC_ADDR` (0x09238C). Contains 124 entries (IDs 0x00-0x7B), each a 2-byte word offset relative to the table start.

```
Offs_PLC:
    dc.w PLC_00-Offs_PLC    ; offset to PLC_00 data
    dc.w PLC_01-Offs_PLC    ; offset to PLC_01 data
    ...
```

### Per-PLC Data Block

Each PLC data block has:
- **Header word** (2 bytes): count-1 (for `dbf` loop). Value 0xFFFF = empty PLC.
- **Entries** (6 bytes each): `dc.l nemesis_rom_addr`, `dc.w vram_dest_bytes`

The VRAM destination word stores `tile_index * 32` (byte offset in VRAM). To recover the tile index: `tileIndex = vramDest / 32`.

### Example: PLC_0B (AIZ1 zone art)

```
PLC_0B: plrlistheader              ; dc.w 5 (count-1 = 5, so 6 entries)
    plreq ArtTile_AIZSwingVine,      ArtNem_AIZSwingVine
    plreq ArtTile_AIZSlideRope,      ArtNem_AIZSlideRope
    plreq ArtTile_AIZMisc1,          ArtNem_AIZMisc1
    plreq ArtTile_AIZFallingLog,     ArtNem_AIZFallingLog
    plreq ArtTile_Bubbles,           ArtNem_Bubbles
    plreq ArtTile_AIZFloatingPlatform, ArtNem_AIZCorkFloor
```

Each `plreq` expands to: `dc.l ArtNem_xxx` (4 bytes), `dc.w ArtTile_xxx * $20` (2 bytes).

## Load_PLC vs Load_PLC_2

| Routine | Behavior | Use Case |
|---------|----------|----------|
| `Load_PLC` | Appends entries to decompression queue | Zone art, boss art, act transitions |
| `Load_PLC_2` | Clears queue first, then loads | Level startup (character PLC), scene changes |

Both take PLC ID in `d0`, resolve through the offset table, and queue Nemesis decompressions for VBlank processing.

## Kosinski Moduled Runtime Queue (Not a Fixed Delay)

`Queue_Kos_Module` / `Process_Kos_Module_Queue` is a separate, gameplay-visible
scheduler. Do not replace `Kos_modules_left` polling with a guessed frame count,
and do not treat already-decompressed Java renderer art as proof that the native
readiness gate has opened.

- The FIFO has four six-byte entries: archive address (long) plus VRAM byte
  destination (word). Only the first, empty-queue enqueue reads and initializes
  its header immediately. Later enqueues must retain only those raw fields: do
  not read, validate, or derive header/module metadata until the entry shifts
  into slot zero. A malformed later header therefore enters the FIFO and fails
  only at that shift-time initialization boundary.
- Init consumes the big-endian size header, maps `$A000` to `$8000`, stores the
  active payload source as `archive+2`, derives `$800`-word modules, and stores
  the exact last-module word count. Reject invalid zero/one-byte headers rather
  than inventing a module.
- A normal module needs a start dispatch and a later DMA-completion dispatch.
  Completing an archive shifts the FIFO and initializes the next header, but
  cannot start that archive in the same call.
- After decompression from source `S` ends at `E`, the next source is
  `E + ((S-E) & $F)`: alignment preserves the payload source's low-nibble
  residue; it is not absolute 16-byte alignment.
- DMA destination advances by `$1000` bytes for a full module and by
  `Kos_last_module_size*2` for the final module. Use the header-derived word
  count (including its odd-byte truncation), not measured Java output length.
- Gameplay rewind must snapshot FIFO order, initialized/uninitialized entries,
  active payload source, evolving destination, modules-left raw phase/bit 7,
  last-module words, and the in-progress decompressor end. Recreated objects
  must use side-effect-free shells so queue restore is not followed by duplicate
  enqueues.

### Results-screen false-green checklist

`Obj_LevelResults` queues General -> `$520`, apparent-act Num1/Num2 -> `$568`,
and the selected character name -> `$578` or `$5A0`. `Obj_LevelResultsCreate`
returns without advancing its 360-frame wait while modules remain. It then
allocates twelve real `ObjArray_LevResults` SSTs: failure of the initial
`AllocateObjectAfterCurrent` retries without publication; failure of a later
`CreateNewSprite4` publishes the allocated prefix but leaves the parent's native
child count at twelve, so Wait2 retains the missing suffix. Do not heal it.
Children retire from the prior `Render_Sprites` bit-7 result using their own
width and native 320-pixel screen-space bounds; crossing an edge deletes on the
next object dispatch. Embedded arrays, fixed 9-frame gates, fixed offscreen
thresholds, and synthetic post-results retirement timers are false greens.

Focused validation:

```bash
mvn "-Dtest=TestKosinskiModuleQueue,TestKosinskiModuleQueueGameplayIntegration" test "-Ds3k.rom.path=s3k.gen"
mvn "-Dtest=TestS3kResultsKosQueueAndChildren,TestS3kResultsElementObjectInstance" test "-Ds3k.rom.path=s3k.gen"
```

## PLC ID Catalog

### Universal (0x00-0x09)
| ID | Contents | Notes |
|----|----------|-------|
| 0x00 | Sonic life icon, Ring/HUD, Starpost, Monitors | Unused |
| 0x01 | Sonic life icon, Monitors, Ring/HUD, Starpost, Spikes/Springs | Sonic character PLC |
| 0x02 | Explosion, Squirrel, Flicky | Unused |
| 0x03 | Game Over text | |
| 0x04 | Signpost art | Unused |
| 0x05 | Knuckles life icon, Monitors, Ring/HUD, Starpost, Spikes/Springs | Knuckles character PLC |
| 0x06 | 2P mode art | |
| 0x07 | Tails life icon, Monitors, Ring/HUD, Starpost, Spikes/Springs | Tails character PLC |
| 0x08 | Monitor art only | |
| 0x09 | Monitor art only | (duplicate of 0x08) |

### Zone Art (0x0A-0x41)
| ID Range | Zone | Notes |
|----------|------|-------|
| 0x0A | AIZ intro sprites | Wave/spray art |
| 0x0B | AIZ1 | Vine, rope, misc, log, bubbles, cork floor |
| 0x0C-0x0D | AIZ2 | Misc2, vine, tree, bubbles, button, cork floor 2 |
| 0x0E-0x0F | HCZ1 | Part 1 + Part 2 |
| 0x10-0x11 | HCZ2 | Part 1 + Part 2 |
| 0x12-0x13 | MGZ1 | |
| 0x14-0x15 | MGZ2 | |
| 0x16-0x19 | CNZ | Shared across acts |
| 0x1A-0x1B | FBZ1 | |
| 0x1C-0x1D | FBZ2 | |
| 0x1E-0x1F | ICZ1 | |
| 0x20-0x21 | ICZ2 | |
| 0x22-0x23 | LBZ1 | |
| 0x24-0x25 | LBZ2 | |
| 0x26-0x29 | MHZ | Shared across acts |
| 0x2A-0x2B | SOZ1 | |
| 0x2C-0x2D | SOZ2 | |
| 0x2E-0x2F | LRZ1 | |
| 0x30-0x31 | LRZ2 | |
| 0x32-0x35 | SSZ | Shared across acts |
| 0x36-0x37 | DEZ1 | |
| 0x38-0x39 | DEZ2 | |
| 0x3A-0x3F | DDZ | Shared across acts + endings |
| 0x40-0x41 | Ending | Blank |

### Bonus/Special (0x42-0x51)
| ID | Contents |
|----|----------|
| 0x42-0x46 | ALZ, BPZ, DPZ, CGZ, EMZ (competition zones) |
| 0x47 | Gumball bonus |
| 0x48-0x4B | HPZ |
| 0x4C-0x4D | DEZ3 |
| 0x4E-0x4F | Spikes and springs (unused) |
| 0x50 | Glowing bonus |
| 0x51 | Slots bonus |
| 0x52 | Miles (Tails) life icon |

### Boss Art (0x53-0x7B)
| ID Range | Boss |
|----------|------|
| 0x53-0x5A | AIZ1 boss |
| 0x5B | HCZ1 boss |
| 0x5C-0x5D | CNZ1 boss |
| 0x5E | FBZ1 boss (unused) |
| 0x5F | ICZ1 boss |
| 0x60 | LBZ1 Eggman |
| 0x61 | Boss explosion (unused) |
| 0x62-0x6A | FBZ2 subboss |
| 0x6B | AIZ2 boss |
| 0x6C | HCZ2 boss |
| 0x6D | MGZ2 boss |
| 0x6E | CNZ2 boss |
| 0x6F | FBZ2 end boss |
| 0x70 | ICZ2 boss |
| 0x71 | LBZ2 final boss 1 |
| 0x72-0x76 | DEZ2 boss |
| 0x77 | LBZ2 Eggman |
| 0x78-0x7B | Boss ship and explosion |

## Runtime PLC Loading Pattern

### Level-Load PLCs (via LevelResourcePlan)

During level load, PLCs are converted to `LoadOp` entries and applied as pattern overlays:

```java
// In Sonic3k.appendPlcPatternOps():
PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, plcIndex);
List<LoadOp> ops = Sonic3kPlcLoader.toPatternOps(plc);
for (LoadOp op : ops) {
    planBuilder.addPatternOp(op);
}
```

### Runtime PLCs (zone events, act transitions)

During gameplay, PLCs are applied directly to the level and GPU textures are refreshed:

```java
// In Sonic3kZoneEvents.applyPlc():
PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, plcId);
List<TileRange> modified = Sonic3kPlcLoader.applyToLevel(plc, level);
Sonic3kPlcLoader.refreshAffectedRenderers(modified, levelManager);
```

### Pre-Decompression (avoiding frame hitches)

For transitions that must be seamless (AIZ intro), pre-decompress during level load:

```java
// During level load:
PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, 0x0B);
List<PreDecompressedEntry> cached = Sonic3kPlcLoader.preDecompress(plc);

// Later at transition frame:
List<TileRange> modified = Sonic3kPlcLoader.applyPreDecompressed(cached, level);
Sonic3kPlcLoader.refreshAffectedRenderers(modified, levelManager);
```

## Standalone Art Loading from PLCs (Object/Boss Art)

Boss and object art is often delivered via PLCs but must NOT be written into the level's shared pattern buffer, because PLC tile ranges can overlap (e.g., boss fire art at 0x0482 overwrites spike/spring art at 0x0494). Use standalone decompression via the shared `PlcParser` API:

```java
// Parse PLC to discover art ROM addresses and tile destinations
PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, PLC_ID);

// Decompress all entries into standalone Pattern[] arrays (no level buffer writes)
List<Pattern[]> artArrays = PlcParser.decompressAll(rom, plc);

// Pair each entry's patterns with its ROM-parsed mappings
ObjectSpriteSheet sheet = new ObjectSpriteSheet(
    artArrays.get(entryIndex),
    S3kSpriteDataLoader.loadMappingFrames(reader, mappingAddr),
    paletteIndex, 1);
```

**Benefits over hardcoded art addresses:**
- PLC ID is the single source of truth — no need for separate `ART_NEM_*` and `ARTTILE_*` constants
- Only mapping addresses and palette indices need constants (these aren't in PLCs)
- Avoids VRAM overlap conflicts that happen with level buffer application
- The ROM restores overwritten tiles via `Load_PLC(PLC_Monitors)` after boss defeat; standalone arrays make this unnecessary

**Example: AIZ miniboss (PLC 0x5A)**
```java
// PLC 0x5A has 4 entries: main boss, small debris, flame, boss explosion
PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, Sonic3kConstants.PLC_AIZ_MINIBOSS);
List<Pattern[]> decompressed = PlcParser.decompressAll(rom, plc);

registerSheet(KEY_MAIN,  new ObjectSpriteSheet(decompressed.get(0), mainMappings,  1, 1));
registerSheet(KEY_SMALL, new ObjectSpriteSheet(decompressed.get(1), smallMappings, 0, 1));
registerSheet(KEY_FLAME, new ObjectSpriteSheet(decompressed.get(2), flameMappings, 1, 1));
```

**When to use standalone vs level buffer:**

| Use Case | Method | Writes to Level Buffer? |
|----------|--------|------------------------|
| Level init PLCs (zone art) | `toPatternOps()` → `LevelResourcePlan` | Yes |
| Runtime act transitions | `applyToLevel()` + `refreshAffectedRenderers()` | Yes |
| Boss/object art | `PlcParser.decompressAll()` → `ObjectSpriteSheet` | No |
| Hitch-free preload | `preDecompress()` → `applyPreDecompressed()` | Yes (deferred) |

## Integration with Zone Event Handlers

Zone event handlers inherit `applyPlc(int plcId)` from `Sonic3kZoneEvents`. Future zone handlers become one-liners:

```java
// HCZ act 2 transition:
applyPlc(0x10);
applyPlc(0x11);

// AIZ2 boss arena:
applyPlc(0x6B);
```

## GPU Texture Refresh

After PLC application, object renderer GPU textures must be re-uploaded for any renderers whose backing Pattern data was modified. The system uses tile-range overlap detection:

1. `Sonic3kObjectArtProvider` tracks which level tile indices each sheet depends on (via `registerLevelArtSheet`)
2. `Sonic3kPlcLoader.applyToLevel()` returns the tile ranges it modified
3. `Sonic3kPlcLoader.refreshAffectedRenderers()` finds overlapping renderers and calls `updatePatternRange()`

## Key Engine Files

| File | Purpose |
|------|---------|
| `Sonic3kPlcLoader.java` | PLC parsing, application, pre-decompression, GPU refresh |
| `Sonic3kConstants.java` | `OFFS_PLC_ADDR`, `PLC_ENTRY_SIZE`, tile index constants |
| `Sonic3kObjectArtProvider.java` | Tile range tracking, `getAffectedRendererKeys()` |
| `Sonic3kZoneEvents.java` | `applyPlc()` convenience method for zone handlers |
| `AizIntroTerrainSwap.java` | Runtime PLC application example (PLC 0x0B) |
| `Sonic3k.java` | Level-load PLC application via `appendPlcPatternOps()` |
| `level/resources/PlcParser.java` | Shared PLC format parser (game-agnostic) |

## Queue Timing and Trace Reports

Capture audited native traces with `--load-queue-state` and require
`load_queue_state_per_frame` in `metadata.json`. In comparator reports,
`queue.s3k_kos_direct.*` describes physical direct Kosinski jobs and
`queue.s3k_kos_module.*` describes physical KosM parents; a module's direct
child is not another module parent.

Hardware-timing schema 2 covers readiness for both direct and module domains.
It may release only a matching, prepared, production-submitted ROM job after
kind, ordinal, stable submission fingerprint, and service boundary match. It
must never create queue work or carry asset/gameplay payloads. Separate a
hardware-timing admission error from a `queue.*` comparator mismatch before
changing PLC code.

If player-art DPLC evidence is present,
`dynamic_art_transfer_state_per_frame_v1` records ordered submissions,
completions, requests, outstanding transfer IDs, and run-gap ledger carry.
Treat both `queue.*` and `dynamic_art.*` as zero-tolerance, comparison-only
frontiers. Record first frame, field, and error count in
`docs/status/trace-frontier-log.md`; use `trace-replay-bug-fixing` for triage
and `trace-green-fleet` for fleet work.
