# S3K Custom SMPS Meta-Command Capability Validation

**Date:** 2026-08-09

**Disposition:** Validated unavailable boundary; no runtime implementation

**Branch:** `feature/ai-s3k-smps-custom-meta-capability`

**Worktree:**
`.worktrees/s3k-smps-custom-meta-capability`

**Base:** `origin/develop` at
`e2aa50cd5980efc720f70c1c2a6209b2637b3042`

This evidence package is isolated for review. It is not merged or pushed.

## Validated outcome

`FF 01`, `FF 02`, and `FF 03` remain unreachable in supported locked-on-ROM
music and SFX. Their current decoder cases recognize and consume the
source-defined operand widths only. That defensive syntax behavior is not
custom-driver support:

| Command | Proved current behavior | Unsupported native behavior |
|---|---|---|
| `FF 01 <id>` | Advances through the operand and following `F2`; submits no `AudioManager` command and admits no presentation voice. | Driver-global S&K/S3 sound-ID dispatch. |
| `FF 02 <flag>` | Advances through the operand and following `F2`; a sibling FM song track consumes its first note and remains active. | Halt/resume, key-off, and PSG silence across the fixed nine song-track records. |
| `FF 03 <source-lo> <source-hi> <count>` | Advances through all three operands and following `F2`; sequence memory remains unchanged. | Shared-Z80-RAM `LDIR` into the current stream. |

Custom execution is unavailable because OpenGGF exposes no versioned custom
S3K SMPS format, driver declaration, validator, mod API, or ingestion route.
The existing mod-audio design uses streamed WAV/OGG music and explicitly leaves
new SMPS/VGM authoring out of scope at
`docs/architecture/designs/2026-07-09-mod-support-design.md:199-224`.

The reviewed design is
`docs/architecture/designs/2026-08-09-s3k-custom-smps-meta-command-capability.md`;
the reviewed executable plan is
`docs/architecture/plans/2026-08-09-s3k-custom-smps-meta-command-capability.md`.
Both reviews completed green before implementation.

## Authority and ownership audit

The checked-in S3K Z80 source remains the native authority:

- `zExtraCoordFlagSwitchTable` and `cfMetaCF` define dispatch and operand entry;
- `cfPlaySoundByIndex` enters driver-global `zPlaySoundByIndex`;
- `cfHaltSound` owns `zHaltFlag`, the nine records at
  `zTracksStart..zTracksEnd`, chip key-off, and PSG silence; and
- `cfCopyData` uses a 16-bit source address and exact Z80 `LDIR` semantics in
  shared 8 KiB Z80 RAM.

The runtime audit covered `AudioManager`, active and donor `SmpsLoader` routes,
`SoundTestApp`, `AbstractSmpsAudioBackend`, `SmpsDriver`,
`Sonic3kCoordFlagHandler`, `CoordFlagContext`, `SmpsSequencer`,
`AbstractSmpsData`, `SmpsSourceDescriptor`, presentation reconstruction, and
sequencer/driver rewind snapshots. The current coordinate handler owns only one
sequencer context and cannot safely own any of the three native effects:

1. `FF 01` requires non-re-entrant driver-wide command admission, S&K/S3 ID
   classification, exact update ordering, timeline publication, and rollback.
2. `FF 02` requires fixed-slot song state distinct from completion/removal,
   global halt state, chip silence/resume behavior, SFX interaction, and rewind.
3. `FF 03` requires shared addressable mutable Z80 RAM. Mutating the private
   `AbstractSmpsData` array would break native addressing, shared-memory and
   overlap behavior, immutable source fingerprints, and rewind reconstruction.

The local `docs/SMPS-rips/SMPSPlay` directory contains configuration/install
material rather than the implementation source. No behavior was inferred from
it. The checked-in Z80 driver and verified ROM therefore remain authoritative.

## ROM and environment

```text
Maven: 3.9.16
Java: 21.0.11 (Arch Linux, /usr/lib/jvm/java-21-openjdk)
ROM: Sonic and Knuckles & Sonic 3 (W) [!].gen
Size: 4,194,304 bytes
SHA-1: CFBF98C36C776677290A872547AC47C53D2761D6
CRC32: 63522553
```

Maven's hook-install step reported that the sandbox could not lock the shared
main-workspace `.git/config`; this did not affect compilation or test execution.
Commit policy is run separately at the repository hook boundary and is not
bypassed.

## Test evidence

### Pre-edit baseline

The focused baseline command selected
`TestSonic3kSmpsMetaCommandOperands` and
`TestSonic3kSmpsMetaCommandReachability` with the verified ROM:

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```

This was three operand-width tests plus four fixed-point ROM reachability tests.

The pre-edit `TestArchitecturalSourceGuard` baseline was already red:

```text
Tests run: 69, Failures: 2, Errors: 0, Skipped: 0
ObjectManager.java: 3036 effective lines > 2914 budget
AbstractPlayableSprite.java: 3180 effective lines > 3161 budget
```

### Characterization

After the test-first edit:

```text
TestSonic3kSmpsMetaCommandOperands
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

The three existing exact-width assertions were renamed as syntax-only tests.
Three non-effect assertions were added:

- `ff01DoesNotDispatchSoundCommand` uses `FF 01 A4 F2`, the normal singleton
  reset, real `AudioManager` command history, and a presented frame; command
  and presentation-voice counts remain unchanged and the FM track ends at
  `0x44`.
- `ff02DoesNotHaltSiblingSongTrack` uses an exact DAC-plus-two-FM header. The
  `FF 02 01 F2` track ends at `0x44`, while the sibling `81 7F` track remains
  active at `0x62` with note `0x81` and duration `0x7F`.
- `ff03DoesNotMutateSequenceMemory` uses `FF 03 70 00 01 F2` and a distinct
  source byte. `sequencer.getData()[0x45]` remains `F2`, and the command track
  ends inactive at `0x46`.

### Final focused and adjacent suite

Command:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  mvn -Dmse=off \
  -Ds3k.rom.path="/absolute/path/to/OpenGGF/.worktrees/s3k-smps-custom-meta-capability/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  -Dtest=com.openggf.game.sonic3k.audio.smps.TestSonic3kSmpsMetaCommandOperands,com.openggf.game.sonic3k.audio.smps.TestSonic3kSmpsMetaCommandReachability,com.openggf.tests.TestSonic3kCoordFlagParity,com.openggf.audio.smps.TestSmpsSequencerSnapshot,com.openggf.audio.driver.TestSmpsDriverSnapshot,com.openggf.audio.TestAudioPresentationSnapshotParity \
  test
```

```text
Tests run: 59, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The 59 tests comprise 6 syntax-boundary, 4 ROM-reachability, 29 S3K
coordinate-flag parity, 2 sequencer-snapshot, 7 driver-snapshot, and 11 audio
presentation-snapshot tests. Expected warning paths in the presentation tests
exercise injected rollback failures; they are passing assertions, not errors.

`TestBuildToolingGuard` also passes independently:

```text
Tests run: 78, Failures: 0, Errors: 0, Skipped: 0
```

The final `TestArchitecturalSourceGuard` comparison exactly reproduces the
pre-edit baseline:

```text
Tests run: 69, Failures: 2, Errors: 0, Skipped: 0
ObjectManager.java: 3036 effective lines > 2914 budget
AbstractPlayableSprite.java: 3180 effective lines > 3161 budget
```

There is no new guard failure and neither existing ratchet measurement grew.

## Future activation gates

Custom S3K SMPS can be advertised only as one coherent product slice that
provides all of the following:

1. a versioned format/API, driver variant, load-address/memory declaration,
   validator, and deterministic unsupported-command rejection;
2. a non-re-entrant driver command queue with exact S&K/S3 dispatch and atomic
   music/SFX/fade/stop/SEGA-PCM update ordering;
3. fixed-slot or proven-equivalent nine-song-record halt semantics, chip
   silence/resume, SFX interaction, and lifetime separation;
4. shared 16-bit-addressed mutable Z80 RAM with exact `LDIR`, overlap, wrap,
   bounds, and loaded-region behavior separated from immutable source bytes;
5. snapshot/restore of halt and memory state, stable source descriptors,
   command-timeline rollback, and deterministic presentation reconstruction;
   and
6. native routine vectors, driver integration, self-modifying rewind,
   ordering, malformed-file rejection, and a custom fixture exercising all
   three commands while shipped-ROM results remain byte-identical.

Until those gates exist, a future loader must reject streams that require the
commands rather than letting defensive syntax consumption imply execution.

## Scope statement

The only `src/main` change is comment text in
`Sonic3kCoordFlagHandler`; executable production tokens and shipped behavior do
not change. No custom-loader scaffold, callback, memory model, configuration
flag, warning loop, or fixture format was added. No trace was captured and no
trace frontier moved.
