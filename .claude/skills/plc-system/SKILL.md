---
name: plc-system
description: Use when working with Pattern Load Cue systems across any game — runtime art loading, PLC table parsing, art decompression queuing.
---

# Cross-Game Pattern Load Cue (PLC) System

Reference for the shared PLC binary format used across S1, S2, and S3K, and the `PlcParser` utility.

## Agent Workflow Tooling

For S3K PLC/art intake, use the dedicated tool and runbook (prefer the S&K half; the tool flags `s3.asm`-sourced labels — the S3L standalone half — as a caution, since an object with no S&K equivalent may legitimately use them):

- **`RomArtIntakeTool`** — S3K ROM-backed art/mapping/PLC intake; wraps `RomOffsetFinder --game s3k`, recommends `StandaloneArtEntry` vs `LevelArtEntry`, and suggests `Sonic3kConstants` names + `Sonic3kPlcArtRegistry` hints. Accepts multiple labels:
  `mvn exec:java "-Dexec.mainClass=com.openggf.tools.RomArtIntakeTool" "-Dexec.args=ArtNem_AIZSwingVine Map_AIZSwingVine"`
- **Doc:** `docs/agent-workflow/runbooks/runbook-rom-art-mappings-plc.md` — end-to-end ROM art/mappings/PLC intake runbook.

## Shared Binary Format

All three Sonic games use an identical PLC table format:

### Offset Table
A list of 2-byte word offsets, one per PLC ID, relative to the table start.

### Per-PLC Data Block
- **Header word** (2 bytes): count-1 (for `dbf` loop). Negative (bit 15 set) = empty PLC.
- **Entries** (6 bytes each): `dc.l nemesis_rom_addr` (4 bytes), `dc.w vram_dest_bytes` (2 bytes)

VRAM destination stores `tile_index * 32`. To recover tile index: `vramDest / 32`.

## Per-Game Table Addresses

| Game | Constant | Address | Entry Count |
|------|----------|---------|-------------|
| S1 | `Sonic1Constants.ART_LOAD_CUES_ADDR` | `0x01DD86` | ~16 PLC IDs |
| S2 | `Sonic2Constants.ART_LOAD_CUES_ADDR` | `0x42660` | ~67 PLC IDs |
| S3K | `Sonic3kConstants.OFFS_PLC_ADDR` | `0x09238C` | 124 PLC IDs (0x00-0x7B) |

## PlcParser API

Located in `com.openggf.level.resources.PlcParser`.

### Records
- `PlcParser.PlcEntry(int romAddr, int tileIndex)` -- single PLC entry
- `PlcParser.PlcDefinition(int plcId, List<PlcEntry> entries)` -- parsed PLC with all entries

### Methods
```java
// Parse a PLC definition from any game's ROM
PlcParser.PlcDefinition parse(Rom rom, int tableAddr, int plcId)

// Convert entries to LoadOps for LevelResourcePlan (writes to level pattern buffer)
List<LoadOp> toPatternOps(PlcParser.PlcDefinition definition)

// --- Standalone decompression (no level buffer involvement) ---

// Decompress a single entry into standalone Pattern[] (no VRAM conflicts)
Pattern[] decompressEntry(Rom rom, PlcEntry entry)

// Batch decompress all entries into List<Pattern[]> (one array per entry)
List<Pattern[]> decompressAll(Rom rom, PlcDefinition definition)

// Decompress a single entry into raw bytes (for level buffer application)
byte[] decompressEntryRaw(Rom rom, PlcEntry entry)
```

### Standalone vs Level-Buffer Decompression

PLCs can be used in two ways:

| Mode | Method | Use Case |
|------|--------|----------|
| **Level buffer** | `toPatternOps()` / `decompressEntryRaw()` | Level init, act transitions — writes into shared level pattern buffer |
| **Standalone** | `decompressEntry()` / `decompressAll()` | Object/boss art — returns independent `Pattern[]` arrays |

**Why standalone matters:** On real hardware, boss PLCs intentionally overwrite existing VRAM tiles (e.g., boss fire art at 0x0482 overwrites spike/spring art at 0x0494). The ROM restores the overwritten art after the boss is defeated. Standalone decompression avoids this conflict entirely by keeping art in separate `Pattern[]` arrays, paired with mappings to create `ObjectSpriteSheet` instances.

**Pattern for standalone PLC art loading:**
```java
PlcDefinition plc = PlcParser.parse(rom, tableAddr, plcId);
List<Pattern[]> artArrays = PlcParser.decompressAll(rom, plc);

// Pair each entry's patterns with its mappings
ObjectSpriteSheet sheet = new ObjectSpriteSheet(
    artArrays.get(entryIndex),
    S3kSpriteDataLoader.loadMappingFrames(reader, mappingAddr),
    paletteIndex, 1);
```

## Per-Game Integration

### S1 (Level Init)
PLCs are parsed during level loading in `Sonic1.readPatternLoadCues()` via `PlcParser.parse()`. Both primary and secondary ArtLoadCues are loaded and passed to `Sonic1Level.loadPatterns()`.

### S3K (Level Init + Runtime)
- **Level load:** PLCs converted to `LoadOp` entries via `Sonic3kPlcLoader.toPatternOps()` -> `LevelResourcePlan`
- **Runtime:** Zone events call `Sonic3kPlcLoader.applyToLevel()` for act transitions and boss art
- **Pre-decompression:** `Sonic3kPlcLoader.preDecompress()` for hitch-free transitions (AIZ intro)
- See `s3k-plc-system` skill for S3K-specific PLC ID catalog and runtime patterns

### S2 (Level Init)
S2 ArtLoadCues are parsed via `Sonic2PlcLoader.java`, which uses `PlcParser.parse()` with `Sonic2Constants.ART_LOAD_CUES_ADDR`. Zone-specific PLCs are loaded during level init. S2 can also use `PlcParser.decompressEntry()` for standalone boss/object art loading.

## Key Files

| File | Purpose |
|------|---------|
| `level/resources/PlcParser.java` | Shared PLC format parser |
| `game/sonic2/Sonic2PlcLoader.java` | S2-specific PLC parsing via `PlcParser` |
| `game/sonic3k/Sonic3kPlcLoader.java` | S3K-specific PLC application, GPU refresh |
| `game/sonic1/Sonic1.java` | S1 PLC parsing via `PlcParser` |
| `game/sonic1/Sonic1Level.java` | S1 pattern loading from PLC entries |

## Frame-Level Queue Diagnostics

Trace fixtures that advertise `load_queue_state_per_frame` compare physical load
queues at `END_OF_LOGICAL_FRAME`. Treat these fields as ordinary zero-tolerance
frontier fields:

- `s1_nemesis_plc` and `s2_nemesis_plc` cover the native six-byte PLC queue;
- `s3k_kos_direct` covers only physical direct Kosinski entries;
- `s3k_kos_module` covers physical KosM parents, never their direct children.

Use `GameplayModeContext.captureQueueDiagnostics()` for engine-side state. Keep
diagnostics read-only: snapshots must never accept trace data or release timing
jobs. A ready-but-unclaimed S3K timing job is no longer physically queued and
must not appear busy. Ordered fingerprints are part of the exact comparison
contract. In schema version 1, `service_observations` is reserved and must be an
empty array. This is mandatory schema padding, not evidence that service did
not run. Do not infer a consumed sub-frame boundary from end-frame state;
diagnose service and retirement from membership, prepared, and remaining-work
transitions.

For S3K, do not conflate submission with preparation. A direct Kos descriptor
queued at `POST_OBJECTS` is busy but unprepared until `PRE_MAIN_LOOP` sets the
retail queue-count sign bit. A KosM parent is prepared when the low seven bits
of `Kos_modules_left` are nonzero; bit 7 means its direct child is in progress,
not that the parent was initialized.

When a trace first diverges under `queue.<kind>.*`, investigate the owning queue
lifecycle before changing downstream audio, objects, events, or physics. For
S1/S2 recorder work, remember that `RunPLC` overwrites the active queue source
with the Nemesis decoder cursor; preserve the original descriptor by observing
the lifecycle before preparation rather than guessing it from an end-frame
sample.
