# S3K SMPS meta-command reachability

## Finding

The shipped Sonic 3&Knuckles ROM does not reach the three meta commands that
were previously described as discarded semantics in
`Sonic3kCoordFlagHandler.handleMetaCommand(...)`:

| Meta byte sequence | Native name | Shipped-ROM reachability | Engine disposition |
|---|---|---|---|
| `FF 01 <id>` | `SND_CMD` | Not reached | Consume the one operand to preserve stream alignment; do not claim native dispatch. |
| `FF 02 <flag>` | `MUS_PAUSE` | Not reached | Consume the one operand to preserve stream alignment; do not claim all-track halt/resume. |
| `FF 03 <ptr-lo> <ptr-hi> <count>` | `COPY_MEM` | Not reached | Consume the three operands to preserve stream alignment; do not claim shared-Z80-memory copying. |

The handler now says this explicitly. It does not add behavior for a command
that has no caller in the supported ROM. A custom or modified stream that
reaches one of these commands remains an identified parity gap, rather than a
silently simulated command.

## Native contract

The S3K Z80 driver defines the extra-coordinate table at
`docs/skdisasm/Sound/Z80 Sound Driver.asm:2980-2990`. The dispatch at
`:3842-3853` consumes the `FF` prefix and subcommand before entering the
handler. The native routines define the following contracts:

* `SND_CMD` (`:3865-3878`) calls `zPlaySoundByIndex` with one ID operand. The
  ID dispatcher classifies music `01-32`, SFX `33-DB`, and fade/control ranges
  at `:1632-1669`.
* `MUS_PAUSE` (`:3880-3919`) stores the operand as `zHaltFlag`. Nonzero clears
  the playing bit and keys off every track, then silences PSG; zero restores
  the playing bit for every track.
* `COPY_MEM` (`:3921-3944`) reads a little-endian Z80 source pointer and an
  eight-bit count, then copies from shared Z80 RAM into the current track's
  stream immediately after the three operands. Playback resumes after the
  copied bytes. The driver notes that this only works when the song/SFX was
  copied into Z80 RAM.

The local `docs/SMPS-rips/SMPSPlay` checkout contains configuration/install
placeholders rather than the SMPSPlay source tree. The command spellings and
operand contracts above are therefore pinned to the checked-in S3K Z80 source
of truth; the SMPSPlay/libvgm references remain useful for future driver-level
implementation if a supported ROM or custom stream reaches one of them.

## ROM inventory

The evidence uses the locked-on S3&K ROM with SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6` (CRC32 `63522553`). It enumerates
the actual loader tables rather than assuming a filename or a subset of songs:

| Loader table | IDs inspected | Loaded streams | `FF 01/02/03` reached |
|---|---:|---:|---:|
| S&K music | `01-33` | 51 | 0 |
| S3 music | `01-32` | 50 | 0 |
| SFX | `33-DB` | 169 | 0 |

Music blobs also contain zero raw `FF 01`, `FF 02`, or `FF 03` pairs. A raw
scan is not used to classify SFX: `Sonic3kSmpsLoader` returns a bank-backed
buffer containing voices and unrelated streams, so raw SFX bytes would produce
false positives. Instead, the test runs every loaded stream through
`SmpsSequencer` with a recording S3K coordination handler and records only
subcommands encountered at a live track position. It observes the live `FF 00`
tempo command and SFX `0xAB`'s `FF 07` command, proving that the instrumentation
is traversing real meta commands while still finding no `01/02/03` route.

The characterization is maintained by
`TestSonic3kSmpsMetaCommandReachability`, run with:

```bash
mvn -Dmse=off \
  -Dtest=com.openggf.game.sonic3k.audio.smps.TestSonic3kSmpsMetaCommandReachability \
  -Ds3k.rom.path="/path/to/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

The test keeps the `E2` operand advance local to the recording harness so it
does not invoke the global audio singleton during inventory. It does not use
trace data or hydrate gameplay/audio state from a trace.
