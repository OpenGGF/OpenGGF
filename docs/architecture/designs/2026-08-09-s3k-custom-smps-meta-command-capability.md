# S3K Custom SMPS Meta-Command Capability Boundary

**Date:** 2026-08-09
**Status:** Reviewed; custom-driver capability unavailable

## Outcome

OpenGGF does not currently have a supported custom S3K SMPS driver product.
The sound test, normal runtime, donor-audio path, and presentation rebuild path
all obtain SMPS data from a game ROM loader. The mod-support design deliberately
uses streamed audio for authored music and says that authoring new SMPS/VGM
music is out of scope.

The three native S3K meta commands below therefore remain **unsupported for
custom execution**:

| bytes | native routine | current engine behavior |
|---|---|---|
| `FF 01 <id>` | `cfPlaySoundByIndex` | Advances over the operand only. No sound command is dispatched. |
| `FF 02 <flag>` | `cfHaltSound` | Advances over the operand only. No song tracks are halted or resumed. |
| `FF 03 <source-lo> <source-hi> <count>` | `cfCopyData` | Advances over the three operands only. No memory is copied or mutated. |

This width recognition is defensive parser behavior for synthetic and
diagnostic data. It is not a custom-stream compatibility promise and must not
be described as command handling or partial semantic support.

The shipped locked-on ROM remains unaffected. A closed fixed-point traversal
of every loader-supported stream and both native 173-entry SFX banks proves
that none of the three commands is reached. Implementing guessed effects in
the current per-sequencer handler would add no shipped behavior and would
create false custom-driver claims.

## Requirements

1. Preserve the existing locked-on-ROM reachability proof and all shipped
   audio behavior.
2. Keep the decoder aligned after the source-defined operand widths, including
   in characterization tests. Do not call that semantic support.
3. Do not implement one command by tunnelling through a convenient singleton
   or callback while the other commands lack their native driver ownership.
4. Do not make ROM-loaded `AbstractSmpsData` mutable to approximate shared Z80
   RAM. Its bytes currently participate in immutable source identity,
   presentation reconstruction, and rewind dependency resolution.
5. Do not add an unused custom-driver interface, memory object, command bus, or
   configuration flag before a production ingestion route consumes it.
6. If custom SMPS becomes a product, reject unsupported files at the ingestion
   boundary until the declared driver capability is complete. Do not silently
   approximate their execution in the real-time decoder.

## Source authority

The native authority is the S3K Z80 driver in
`docs/skdisasm/Sound/Z80 Sound Driver.asm`:

- `zExtraCoordFlagSwitchTable` maps `FF 01`, `FF 02`, and `FF 03` to
  `cfPlaySoundByIndex`, `cfHaltSound`, and `cfCopyData` respectively
  (`:2982-2990`);
- `cfMetaCF` consumes the subcommand and exposes the following operand
  (`:3842-3853`);
- `cfPlaySoundByIndex` calls the driver-wide `zPlaySoundByIndex`
  (`:3865-3878`);
- `cfHaltSound` stores `zHaltFlag`, clears or sets the playing bit in the nine
  song track records, keys off on halt, and silences PSG (`:3880-3919`); and
- `cfCopyData` performs a Z80-memory `LDIR` from a 16-bit little-endian source
  address into the current stream immediately after the operands
  (`:3921-3944`). The source explicitly warns that this normally works only
  when the sequence was copied into Z80 RAM.

The shipped-ROM inventory, hashes, loader coverage, S&K/S3 dispatch difference,
and strict graph rules are recorded in
`docs/architecture/research/audio/2026-08-08-s3k-smps-meta-command-reachability.md`.
The local SMPSPlay checkout contains only `docs/config.ini`, so it supplies no
additional implementation authority. A future implementation must re-check a
complete SMPSPlay/libvgm source checkout rather than extrapolating from that
placeholder.

## Current ownership audit

### Ingestion is ROM-only

`AudioManager` resolves music and SFX through the active or donor
`SmpsLoader`. `SoundTestApp` also selects IDs from a supplied ROM loader. The
publicly reachable backend methods accept parsed `AbstractSmpsData`, but no
file format, mod manifest, validator, driver declaration, or user-facing route
constructs custom S3K sequence data.

The current mod architecture chooses WAV/OGG streamed tracks for authored
music and permits reuse of built-in SMPS IDs. It explicitly leaves new
SMPS/VGM authoring out of scope
(`docs/architecture/designs/2026-07-09-mod-support-design.md:199-224`).
Consequently, a synthetic unit-test byte array is not evidence of a supported
product route.

### The coordinate handler is too narrow

`Sonic3kCoordFlagHandler` receives a `CoordFlagContext` implemented by one
`SmpsSequencer`. That context owns track-local operations, tempo, pointers,
voice/envelope lookup, direct chip writes, and the existing continuous-SFX
counters. It does not own:

- high-level sound-ID loading and S&K-versus-S3 dispatch classification;
- safe mutation of the active music/SFX driver while it is iterating tracks;
- the fixed nine-record song-track array and global `zHaltFlag`; or
- a shared, addressable, mutable 8 KiB Z80 RAM image.

Adding those three powers to every game-specific coordinate handler would put
driver-global state in the wrong owner and would broaden the common context for
one unreachable custom-only path.

### `FF 01` is a driver command, not a sequencer callback

`zPlaySoundByIndex` can start music, start SFX, execute fade/stop commands, or
play the SEGA PCM depending on the ID and driver variant. It runs while a track
update has saved its Z80 track pointer. In OpenGGF, equivalent requests enter
through `AudioManager`, loader/profile dispatch, `AbstractSmpsAudioBackend`,
and `SmpsDriver` mutation boundaries.

A call from `Sonic3kCoordFlagHandler` to `GameServices.audio()` would be
re-entrant, bypass the sequencer's injected ownership, and leave ordering and
rollback undefined. A callback that only starts another sequencer would still
omit stop/fade/music replacement, ID classification, presentation command
timeline, and failed-command rollback semantics.

### `FF 02` is fixed-slot song-driver state

The native routine operates on `zTracksStart..zTracksEnd`, the nine song track
records, not merely the track that encountered the command. OpenGGF creates
only declared tracks and represents music and SFX as sequencers mixed by a
driver. It has no fixed inactive song records to receive the native resume-bit
write and no snapshotted `zHaltFlag` owner.

Implementing `active = false/true` in the current handler would also confuse a
temporary halt with track completion: the driver removes completed sequencers
and releases channel locks. Native halt, chip key-off/PSG silence, resume, SFX
interaction, and rewind must be designed together at the driver boundary.

### `FF 03` requires a different memory model

`SmpsSequencer.data` is the byte array exposed by `AbstractSmpsData`. The
presentation path also freezes/copies that source, and `SmpsSourceDescriptor`
hashes it to resolve rewind dependencies. `SmpsSequencerSnapshot` captures
track positions and logical fields but no mutable sequence bytes. Treating the
16-bit operand as an index into the private array would therefore be wrong in
four independent ways:

1. it would discard the native Z80 address space and load address;
2. it would not model writes shared with other Z80-resident data;
3. it would not define exact `LDIR` overlap and wrap/bounds behavior; and
4. the mutation would be missing from rewind or would invalidate the immutable
   source descriptor used to reconstruct the sequencer.

This command cannot be added safely until immutable source identity is
separated from a snapshotted mutable driver-memory image.

## Options considered

### Implement all three directly in `Sonic3kCoordFlagHandler`

Rejected. The handler lacks the native owners, and expanding it would create
re-entrant audio dispatch, completion-versus-halt bugs, and unsnapshotted
self-modifying sequence data.

### Add callbacks for `FF 01` and `FF 02`, defer `FF 03`

Rejected. This would create an unversioned partial dialect with no ingestion
validator and would add production APIs whose only caller is unreachable in
the shipped ROM. It also leaves the most consequential memory contract
undefined.

### Warn from the real-time decoder

Not selected for this scope. No supported ingestion route can reach the cases,
and per-tick logging could flood on loops without making execution safer. A
future custom loader should reject a stream before playback when its declared
driver capability is unavailable; that is a clearer and deterministic failure
boundary.

### Publish an explicit unavailable boundary

Selected. Keep exact operand-width consumption, add characterization for the
absence of halt/memory-copy effects, revise code comments and current docs to
say “syntax only,” and publish the exact activation prerequisites below. No
runtime behavior changes.

## Future activation contract

Custom S3K SMPS may be advertised only when one coherent product slice owns all
of the following:

1. **Format and ingestion:** a versioned custom sequence format or mod API,
   driver-variant declaration, Z80 load address/memory layout, validation, and
   deterministic rejection of unsupported commands.
2. **Driver execution owner:** a non-re-entrant queue for native sound commands,
   exact S&K/S3 ID dispatch, and an explicit update boundary for mutations of
   music, SFX, fade, stop, and SEGA PCM state.
3. **Song halt owner:** native fixed-slot or proven equivalent song-track
   semantics, `zHaltFlag`, key-off/PSG silence, resume behavior, SFX interaction,
   and completion/lifetime separation.
4. **Z80 memory owner:** a shared 16-bit-addressed Z80 RAM model with declared
   loaded regions, exact `LDIR` semantics, overlap/wrap/bounds policy, and a
   mutable working copy separate from immutable source bytes.
5. **Rewind and presentation:** snapshot/restore of halt state and memory
   mutations, stable immutable source descriptors, audio-command timeline and
   rollback behavior, and deterministic presentation reconstruction.
6. **Evidence:** Z80-routine unit vectors for every branch, sequencer/driver
   integration tests, self-modifying stream rewind round trips, sound-command
   ordering tests, malformed-file rejection, and a custom fixture exercising
   all three commands. The shipped-ROM reachability test must remain green and
   prove byte-identical supported-ROM outcomes.

Until then, custom streams containing these commands are unsupported even
though the decoder knows their byte widths.

## Verification for this boundary change

The implementation plan will:

- strengthen `TestSonic3kSmpsMetaCommandOperands` so its names and assertions
  distinguish syntax consumption from semantics;
- retain the ROM-backed `TestSonic3kSmpsMetaCommandReachability` proof;
- update the current audit, roadmap, validation, guides, README, changelog, and
  research note to agree on the unavailable/non-goal status; and
- run focused JDK 21 S3K audio tests, ROM reachability, adjacent sequencer and
  snapshot tests, documentation/source guards, and commit policy.

No trace frontier changes, no new SMPS fixture format, and no `src/main`
runtime behavior are part of this remediation item.
