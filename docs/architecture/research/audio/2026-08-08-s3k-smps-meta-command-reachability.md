# S3K SMPS meta-command reachability

## Finding

The S&K-loader-supported streams in the locked-on Sonic 3&Knuckles ROM do not reach the three meta commands that
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
  native source documents S&K's `DC` CreditsK special case and DD–DF aliases;
  the S3-native SFX table and alias targets remain open pending offset proof.
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
| S&K-loader SFX | `33-DB` | 169 | 0 |

Music blobs also contain zero raw `FF 01`, `FF 02`, or `FF 03` pairs. A raw
scan is not used to classify SFX: `Sonic3kSmpsLoader` returns a bank-backed
buffer containing voices and unrelated streams, so raw SFX bytes would produce
false positives. Instead, the test starts at every resolved loaded track entry
and computes a fixed point over note/duration bytes plus jump, call, counted
loop, conditional loop-exit, continuous, and terminal edges across each
loader-provided full Z80 bank; shared-bank targets are traversed inside the
same address space. Every edge must close with no unexplored frontier (or a cycle) before recording
live FF subcommands. The 51 S&K music, 50 S3 music, and 169 S&K-loader SFX
graphs observe live `FF 00`/`FF 07` commands but no `01/02/03` route. The
S3-native SFX table (including differing IDs 9B/AD) and DC–DF alias targets
are deliberately open pending a ROM-offset-verified parser.

`EB` is the separate native conditional loop-exit command; it is included in
the graph edges and is not one of the three FF meta-command gaps.

The characterization is maintained by
`TestSonic3kSmpsMetaCommandReachability`, run with:

```bash
mvn -Dmse=off \
  -Dtest=com.openggf.game.sonic3k.audio.smps.TestSonic3kSmpsMetaCommandReachability \
  -Ds3k.rom.path="/path/to/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

The inventory is a static ROM-data proof and does not invoke the audio singleton
or use trace data to hydrate gameplay/audio state.
