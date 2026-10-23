# Per-Game Notes

The three community disassemblies (s1disasm, s2disasm, skdisasm) are independent projects
with different structures, conventions, and quirks. This page covers the specifics that
matter when cross-referencing each game.

---

## Sonic 1 (s1disasm)

Repository: https://github.com/sonicretro/s1disasm

### Disassembly Structure

The main source file is `sonic.asm` (unlike S2's `s2.asm`). Constants are in
`_Constants.asm`.

Object code lives in **separate files** in `_incObj/`, named with a hex ID prefix:
```
_incObj/1F Badnik - Crabmeat.asm
_incObj/22, 23 Badnik - Buzz Bomber and Missile.asm
_incObj/3D, 48 Boss - GHZ Main and Wrecking Ball.asm
```

Related animation and mapping files:
```
_anim/Crabmeat.asm      -- animation scripts
_maps/Crabmeat.asm      -- sprite mappings
```

This is different from S2, where all object code is inline in `s2.asm`.

### Label Prefixes

S1 uses shorter, different prefixes than S2:

| S1 | S2 equivalent | Meaning |
|----|---------------|---------|
| `Nem_` | `ArtNem_` | Nemesis-compressed art |
| `Kos_` | `ArtKos_` | Kosinski-compressed art |
| `Art_` | `ArtUnc_` | Uncompressed art |
| `Map_` | `MapUnc_` | Sprite mappings |
| `Ani_` | (inline) | Animation scripts |
| `Blk16_` | (16x16 dir) | 16x16 block mappings |
| `Blk256_` | (128x128 dir) | 256x256 metatile mappings |
| `SonicDynPLC` | (inline) | Sonic's Dynamic Pattern Load Cues |

When using RomOffsetFinder with `--game s1`, use S1 prefixes (`Nem_`, not `ArtNem_`).

### Sprite Mapping Format

S1 uses **5-byte pieces**. Stock S2 uses **8-byte pieces**; S3K normally uses
**6-byte pieces**. S2 includes a second tile word for its two-player renderer:

| Field, in stored order | S1 | S2 | S3K standard |
|-------|-----|--------|--------|
| Y offset | 1 byte (signed) | 1 byte (signed) | 1 byte (signed) |
| Size | 1 byte | 1 byte | 1 byte |
| Pattern | 2 bytes | 2 bytes (1P) | 2 bytes |
| Extra pattern word | None | 2 bytes (2P) | None |
| X offset | 1 byte (signed) | 2 bytes (signed word) | 2 bytes (signed word) |

The single-byte X offset limits S1 sprite pieces to -128..+127 pixels from center.
S1 frame piece counts are bytes; stock S2 and standard S3K counts are words.
Use `S1SpriteDataLoader`, `S2SpriteDataLoader` or `S3kSpriteDataLoader` for the
matching format. S3K's loader also supports explicit legacy byte-X mappings.

### Level Data

S1 uses **256x256 pixel metatiles** (S2/S3K use 128x128). This means:
- Level layouts reference different-sized chunks.
- Metatile mappings are Kosinski-compressed files in `map256/` (not `mappings/128x128/`).
- 16x16 block mappings are Enigma-compressed files in `map16/` (not `mappings/16x16/`).
- Level layouts are **uncompressed** binary files in `levels/`.

### Object Field Names

S1 uses different field names from S2 for the same offsets:

| S1 field | S2 field | Offset | Description |
|----------|----------|--------|-------------|
| `obID` | `id` | 0x00 | Object ID |
| `obX` | `x_pos` | 0x08 | X position |
| `obY` | `y_pos` | 0x0C | Y position |
| `obVelX` | `x_vel` | 0x10 | X velocity |
| `obVelY` | `y_vel` | 0x12 | Y velocity |
| `obFrame` | `mapping_frame` | 0x1A | Current frame |
| `obRoutine` | `routine` | 0x24 | Routine number |
| `obSubtype` | `subtype` | 0x28 | Subtype |
| `obColType` | `collision_flags` | 0x20 | Collision type |

### ROM Address Verification

**Critical caveat:** Addresses derived from s1disasm labels do not always match the
compiled ROM binary. The disassembly source uses labels that resolve to different offsets
depending on assembly options and ROM revision.

Always verify addresses by searching the actual ROM binary for known data patterns, or
use `RomOffsetFinder --game s1 verify` to cross-check.

### SMPS Audio

S1 uses a different SMPS driver configuration from S2:
- Different base note mapping.
- Different PSG envelope tables.
- Different FM operator order.
- PC-relative pointers in music data.

The engine captures these differences in `Sonic1SmpsConstants` and the S1-specific
`SmpsSequencerConfig`.

---

## Sonic 2 (s2disasm)

Repository: https://github.com/sonicretro/s2disasm

### Disassembly Structure

The main source file is `s2.asm`. Constants are in `s2.constants.asm`.

Object code is **inline** in `s2.asm`, not in separate files. Search for `Obj__:` labels
to find object entry points.

Art, mappings, and level data are organised into subdirectories:
```
art/nemesis/     -- Nemesis-compressed sprite art
art/kosinski/    -- Kosinski-compressed level tiles
art/enigma/      -- Enigma-compressed mappings
art/palettes/    -- Uncompressed palette files
collision/       -- Collision height arrays
level/           -- Level layouts (Kosinski-compressed)
mappings/sprite/ -- Sprite frame mappings
mappings/16x16/  -- Block definitions
mappings/128x128/ -- Chunk definitions
sound/           -- SMPS music and SFX data
```

### Resource Overlays

Some zones share base resources with overlays applied on top. HTZ is the primary example:

| Resource | Base | Overlay | Byte offset |
|----------|------|---------|-------------|
| Patterns | EHZ_HTZ.bin | HTZ_Supp.bin | 0x3F80 |
| Blocks | EHZ.bin | HTZ.bin | 0x0980 |
| Chunks | EHZ_HTZ.bin | (shared) | -- |

The overlay is written at the specified offset after decompressing the base data. The
engine handles this in the level loading pipeline.

### REV00 vs REV01

The engine targets **REV01**. Some differences from REV00:
- Object code may have bug fixes or behavior changes.
- ROM addresses may shift due to code size differences.
- Some animation scripts have `rev02even` alignment directives.

Using REV00 ROMs will cause address mismatches and incorrect behavior.

### SMPS Audio

S2 uses OVERFLOW2 tempo mode (overflow = tick, higher tempo value = faster). S3K uses
OVERFLOW mode (overflow = skip, higher tempo value = slower). These are opposite
behaviors for the same overflow mechanism.

---

## Sonic 3 & Knuckles (skdisasm)

Repository: https://github.com/sonicretro/skdisasm

### Disassembly Structure

S3K has the most complex disassembly. The main source file is `sonic3k.asm`, but there
is also `s3.asm` for Sonic 3-specific data. Some labels (like `Map_Sonic`) are defined
in **both** files with different addresses.

**Verify addresses against the machine code and the owning routine's pointer.**
Start with `sonic3k.asm` for the locked-on game, but some resources legitimately
come from the Sonic 3 half. Matching a label in both files does not establish which
copy the routine uses. Constants live in `sonic3k.constants.asm` and `s3.constants.asm`.

### Combined 1P+2P Tables

S3K's `Map_Sonic` and `PLC_Sonic` are **combined** tables with 502 entries (251 for 1P +
251 for 2P). The first word of each table is `0x03EC` (502 x 2 = 1004).

Entries 0-250 are 1P frame offsets. Entries 251-501 are 2P frame offsets.

The 2P sub-table at entry 251 starts at offset 0x01F6 from the combined table. This
creates a "table within a table" where the 2P entries also function as a standalone
251-entry table. This can be misleading -- the combined table start address is the
correct one to use.

When parsing these tables, trim to the first half (`size / 2`) to get only the 1P data.

### Level Loading

S3K uses `LevelLoadBlock` with **24-byte entries** (S2 uses a 12-byte `MainLoadBlock`).
Its six longwords hold primary/secondary art, 16×16 block and 128×128 chunk
pointers, with additional data packed into high bytes. Layouts and collision
indices use separate `LevelPtrs` and `SolidIndexes` tables. Layout data itself is
uncompressed; terrain art is moduled Kosinski and both mapping sizes use Kosinski.

### SolidIndexes Pointer Format

`SolidIndexes` has one longword per act. For the standard combined ROM,
`LoadSolids (sonic3k.asm:9575-9593)` chooses non-interleaved data by comparing
the pointer with `S3_Level_Solid_Data` (`0x260000`). The alternate
`Sonic3_Complete` build uses a high-bit marker instead.

`Sonic3k.decodeCollisionPointer()` accepts the address threshold and explicit
high/low-bit markers. It removes the high marker and keeps the 24-bit address,
including an intentional low-bit `+1` offset; do not clear that bit from the address.
Non-interleaved data has secondary indices at primary + `0x600`. Interleaved data
uses primary and secondary at adjacent bytes, stepping by two through a `0xC00`
block. Object families (`S3KL`/`SKL`) do not determine this encoding.

### Compression

S3K uses **KosinskiM** (Kosinski Moduled) for many resources, in addition to standard
Kosinski and Nemesis. KosinskiM wraps standard Kosinski with a module header that allows
streaming decompression. In the engine, this is `CompressionType.KOSINSKI_MODULED`.

### Z80 Sound Driver

S3K's Z80 driver is significantly different from S1/S2:

- **Music bank list:** 1-byte entries (not 2-byte words). Each byte is a Z80 bank number;
  `romBank = bankByte << 15`.
- **DAC bank list:** Also 1-byte entries with the same shift.
- **Driver loading:** The Kosinski-compressed driver at `Z80_DRIVER_ADDR` decompresses to
  only ~4459 bytes. A separate Kosinski block at `Z80_ADDITIONAL_DATA_ADDR` is loaded to
  Z80 RAM 0x1300. Together they form the complete driver.
- **DAC drum entries:** 5-byte descriptors (rate, length, pointer) using the DACExtract.c
  case 0x19 format, not MMPR/case 0x06.
- **DPCM encoding:** S3K uses DPCM (Differential PCM) for some samples, requiring a
  dedicated decoder.

### Tempo Mode

S3K uses **OVERFLOW** tempo: when the tempo counter overflows, the sequencer **skips** a
tick (delay). This means a higher tempo value produces *slower* music. S2 uses
**OVERFLOW2**: overflow means **tick**. They are opposites.

### Palette Mutations

S3K has per-frame palette color writes in resize/event routines that do not go through the
normal palette cycle system. These are specific colors written directly during level events
(e.g., during the AIZ fire transition).

---

## Summary of Key Differences

| Feature | S1 | S2 | S3K |
|---------|-----|-----|------|
| Object files | Separate (`_incObj/`) | Inline (`s2.asm`) | Inline (`sonic3k.asm`) |
| Art label prefix | `Nem_` | `ArtNem_` | `ArtNem_` / `ArtKos_` |
| Standard mapping format | 5 bytes/piece | 8 bytes/piece | 6 bytes/piece |
| Metatile size | 256x256 | 128x128 | 128x128 |
| Layout compression | Uncompressed | Kosinski | Uncompressed |
| Tempo mode | TIMEOUT | OVERFLOW2 | OVERFLOW |
| ROM scanner | No | No | Yes (`Sonic3kRomScanner`) |
| RomOffsetFinder flag | `--game s1` | (default) | `--game s3k` |
