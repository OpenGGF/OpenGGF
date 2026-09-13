# ROM image normalisation — Design

**Date:** 2026-09-13
**Status:** Implemented on `feature/ai-rom-image-normalisation` (2026-09-13); see
"Implementation notes" at the end.
**Roadmap anchor:** `docs/project/v0.8-roadmap.md` — "Original commitments and demand-driven follow-ons"
**Depends on nothing; unblocks:** the KiS2 patch tier two
(`2026-06-12-game-patch-kis2-design.md`, 2026-09-13 amendment) and S3K boot from
separate Sonic 3 and Sonic & Knuckles images.

## Problem

The engine assumes one physical file per game. `roms.sonic1`, `roms.sonic2` and
`roms.sonic3k` each name a file, `RomLocationResolver` maps a `RomGame` to that key,
`RomManager.getSecondaryRom(gameId)` opens it by game id, and each game's
`RomDetector` checks the header domestic name at offset 0. `LogicalRom` has one
member (`SK`) and `LogicalRomResolver` serves it only as the first 2 MB of the
configured S3K file.

Users own these images in practice, in any mix:

| Image | Size | Layout |
|---|---|---|
| Sonic 1 | 512 KiB | S1 at 0 |
| Sonic 2 | 1 MiB | S2 at 0 |
| Sonic 3 | 2 MiB | S3 at 0 |
| Sonic & Knuckles | 2 MiB | SK at 0 |
| Sonic 3 & Knuckles (locked on) | 4 MiB | SK at 0, S3 at `0x200000` |
| S&K + Sonic 1 (locked on) | 2.5 MiB | SK at 0, S1 at `0x200000` |
| S&K + Sonic 2 (locked on) | 3.25 MiB | SK at 0, S2 at `0x200000`, 256 KiB KiS2 chip at `0x300000` |

Today only the first, second and fifth boot anything. A user with a separate Sonic 3
and a separate S&K cannot boot S3K. A user whose only Sonic 2 is inside the S&K + S2
dump cannot boot Sonic 2. Nothing can serve the KiS2 chip. Knuckles in Sonic 2 needs
all three windows of the S&K + S2 dump at once: its object-layout pointer table at
S&K `0xDF370` points into the S&K half for most zones, into the S2 cart (`0x2E8C80`
for HPZ) for zones it left untouched, and into the chip (`0x33F06E` for CNZ).

## Goal

Any combination of user-supplied images that contains the bytes a game needs lets
that game boot, without the user renaming, splitting or joining files. Identity is
decided by header and hash, never by filename. No byte is ever synthesised, patched
or read from outside user-supplied images (runtime invariant 1).

## Design

### Catalogue, not per-game keys

A `RomImageCatalogue` is built once at startup and on config reload from every
candidate file: the three existing per-game keys, plus an optional `roms.directory`
scan of `*.gen|*.bin|*.md` in the working directory. Each file becomes a
`PhysicalImage` record: path, size, header names at `0` and `0x200000`, and the
CRC32/SHA-1 fingerprint (`RomFingerprintPolicy` already exists and is currently
`NONE` everywhere; this design turns it on for identity, not for gating).

A pure classifier maps a `PhysicalImage` to zero or more `(LogicalRom, window)`
entries:

| Logical ROM | Served from | Window |
|---|---|---|
| `S1` | S1 image; S&K + S1 dump | `0..0x80000` at 0 or `0x200000` |
| `S2` | S2 image; S&K + S2 dump | `0..0x100000` at 0 or `0x200000` |
| `S3` | S3 image; S3&K dump | `0..0x200000` at 0 or `0x200000` |
| `SK` | S&K image; any S&K lock-on dump | `0..0x200000` at 0 |
| `S3K` | S3&K dump; or composite `SK` + `S3` | 4 MiB; composite concatenates two windows |
| `KIS2_CHIP` | S&K + S2 dump only | `0x300000..0x340000` |
| `KIS2` | S&K + S2 dump; or composite `SK` + `S2` + `KIS2_CHIP` | 3.25 MiB lock-on address space |

`LogicalRom` grows to these members. Composite entries exist only when every part
resolves; they are views, never files on disk.

### Views instead of copies

`RomByteReader` gains `window(offset, length)` and a static `concat(readers...)`.
Both are bounds-checked views over the already-loaded `byte[]`; a read outside a
window is a hard error, as `LogicalRomResolver.windowSkFromCombined` already
enforces for `SK`. The catalogue hands out readers; nothing else opens files.
`Rom`'s shared `FileChannel` position hazard (closed for direct reads in 93ae77cc4)
does not apply to views because they read from memory.

### Consumers

- `RomManager.getSecondaryRom(gameId)` and `resolveRomForGame` resolve a logical ROM
  through the catalogue. The `s1`/`s2`/`s3k` ids map to `S1`/`S2`/`S3K`. Existing
  callers do not change.
- `LogicalRomResolver` becomes a thin facade over the catalogue; `PatchContext` is
  unchanged.
- `RomDetectionService` and each game's `RomDetector` keep their header checks but
  run them against the logical view, so a Sonic 2 extracted from a lock-on dump
  passes the same check as a standalone file.
- The master title hub and `LaunchProfile` availability read the catalogue to dim
  games and characters; today they infer availability from configured filenames.

### Configuration

The three per-game keys remain and win as explicit overrides; they may now name
any image that contains the requested logical ROM (a user may point `roms.sonic2`
at the S&K + S2 dump). New: `roms.directory` (default `.`) and
`roms.preferComposite` (default `false`: a real S3&K dump beats an `SK` + `S3`
composite). `ConfigCatalog` metadata and `CONFIGURATION.md` rows follow the existing
pattern. Identity tables in `CONFIGURATION.md` gain the S&K, S3, and lock-on dump
hashes; the S&K + S2 dump is `3,407,872` bytes, MD5 `3E5E4B18D035775B916A06F2B3DC5031`,
SHA-1 `6CD0537A3AEE0E012BB86D5837DDFF9342595004`, CRC32 `2AC1E7C6`.

### Errors

- Nothing resolvable for a game: the game is dimmed on the hub and `DEFAULT_ROM`
  boot fails with the existing `MISSING_ROM_PREFIX` message naming the logical ROM.
- Two images claim the same logical ROM: explicit key wins, then a hash-verified
  image, then the first in directory order; the choice is logged once.
- A file whose header matches but whose hash does not: accepted, logged as
  unverified. Hash mismatch never blocks boot; wrong-revision handling stays with
  each game's detector.

## Non-goals

- No IPS/binary patching, no joining or splitting on disk, no downloading, no
  synthesised headers. The engine only reads user-supplied bytes.
- No per-game carve-outs in shared code: the classifier is table-driven; games ask
  for logical ROMs by identity.
- No change to trace-replay ROM identity requirements; fixtures still record which
  logical ROM they were captured against.

## Testing

- Classifier table test with synthetic images: sizes and header names only, no
  ROMs required. One row per line in the table above plus `unknown`.
- View tests: `window` bounds, `concat` seam reads, composite `S3K` equals a real
  4 MiB dump byte-for-byte (ROM-gated with `@RequiresRom`).
- Resolution precedence tests: explicit key vs directory scan vs composite.
- Boot smoke: `HeadlessGameBoot` for each of S1, S2, S3K from a directory that
  contains only lock-on dumps (ROM-gated; skipped when the dumps are absent).
- Existing `TestSonic3kBootstrapResolver` and `TestSonic3kLevelLoading` stay green.

## Delivery

One bounded item in 0.8 under "Original commitments and demand-driven follow-ons".
It precedes KiS2 tier two and can ship alone. Documentation obligations:
`CONFIGURATION.md` (new keys, identity table), `docs/guide` ROM setup, and the
`CHANGELOG.md` Unreleased section on `next`.

## Implementation notes (2026-09-13)

- `RomImageCatalogue`, `PhysicalImage`, `RomImageClassifier`, `RomIdentityTable`,
  `RomHeaderName` and `RomFingerprint` live in `com.openggf.data`. The data layer
  may not depend on `com.openggf.game.patch` (frozen ArchUnit layering rule), so the
  catalogue keys on a data-layer twin enum, `com.openggf.data.RomIdentity`;
  `LogicalRom` maps onto it through a package-private accessor and a test keeps the
  two in lockstep. Moving `LogicalRom` itself was rejected because it is a pinned
  Mod API type that concurrent KiS2 work depends on.
- `RomByteReader.window` and `concat` are true views (shared backing arrays, seam
  reads assembled across parts). `Rom.fromReader` wraps a view as a read-only
  in-memory ROM with no file channel; every engine call site that read through
  `Rom.getFileChannel()` now opens a private positioned `RomChannel` or calls
  `readBytes`, which also removes the shared-position lock those sites needed.
- `RomLocationResolver` returns a path only for a logical ROM served by one whole
  file; windows and composites have no path, so path-based tools report the ROM as
  unconfigured rather than opening the wrong bytes. `HeadlessGameBoot.boot(RomIdentity, ...)`
  boots straight from the catalogue.
- Verification hashes windows, not files, so lock-on dumps verify per half; hashing
  is lazy and memoised per image and window. The catalogue is rebuilt when any ROM
  key changes and when the title hub refreshes after Settings apply.
- Composite parts follow the same directory-order rule as everything else, so with
  `roms.preferComposite` a single lock-on dump can serve both halves of its own
  composite; the ROM-gated tests use this to prove the composite path byte-for-byte
  without a standalone Sonic 3 or Sonic & Knuckles image on the machine.
- No S&K + Sonic 2 dump was available while implementing; KiS2 and the chip are
  covered by the synthetic classifier and catalogue tests, and the ROM-gated KiS2
  test skips cleanly when the dump is absent.
