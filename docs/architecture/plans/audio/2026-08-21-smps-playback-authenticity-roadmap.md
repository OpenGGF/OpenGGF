# SMPS Playback Authenticity Roadmap

## Outcome

OpenGGF should audibly reproduce the shipped Sonic 1 REV01, Sonic 2 REV01, and
Sonic 3 & Knuckles locked-on SMPS drivers as closely as the supported ROMs and
Mega Drive chip models permit. The acceptance surface is what reaches the
YM2612, PSG, and DAC/PCM paths: timing, register writes, sample selection,
mixing, interruption, and restoration.

This roadmap replaces broad semantic-transaction expansion as the active audio
priority. Trace and observer work is subordinate tooling. It is justified only
when a named playback discrepancy cannot be isolated with a smaller source-
backed test.

## Scope boundary

Work is in scope when it changes or verifies at least one of:

- music, SFX, jingle, or special-SFX scheduling;
- SMPS command and envelope interpretation;
- request priority, channel takeover, mixing, or release;
- pause, fade, speed-up, 1-up, and music replacement behavior;
- YM2612, PSG, DAC, or PCM writes and timing;
- ROM-backed song, SFX, voice, envelope, or sample resolution.

Work is out of scope unless a concrete playback mismatch proves it necessary:

- exhaustive semantic lifecycle ledgers;
- every-occurrence causal proofs across complete-game movies;
- new global trace schemas or native observer ABI expansion;
- full-run recapture solely to restamp metadata;
- architecture migration whose only outcome is cleaner abstraction;
- attempts to make the shipped drivers internally consistent by taking a
  `FixBugs`, `fixBugs`, or `fix_sndbugs` branch that the retail ROM did not take.

Short external captures are preferred to whole-game evidence. Diagnostics must
remain optional observers and must never select content or drive playback.

## Target architecture

Keep the common runtime already used by all three games:

`AudioManager -> presentation registry -> SmpsDriver/SmpsSequencer -> YM2612 + PSG + DAC -> mixer`

Game differences belong in immutable, typed configuration or the existing
game-owned loader/profile/coordination-flag boundary. Shared sequencer and chip
code must not branch on game names. The older duplicate backend is left alone
until audible parity is established; removing it early would mix cleanup risk
with behavioral correction.

## Delivery phases

Each phase is independently reviewable and shippable. A phase closes with
source citations, focused behavioral tests, the affected ROM integration tests,
and an audible/chip-write comparison where practical.

### Phase 0 — Small accuracy harness

- Keep compact register-write and PCM fixtures for named driver routines.
- Add short GPGX/BizHawk or SMPSPlay references only for mismatches that unit
  tests cannot distinguish.
- Compare ordered YM2612/PSG writes and PCM output around a bounded event, not
  an entire playthrough.
- Keep a human A/B listening checklist for tempo, modulation, takeover, pause,
  fade, and 1-up transitions.

### Phase 1 — Driver scheduler and tempo cadence

This is the first implementation slice.

- Separate "service this track" from "advance its duration". S2 and S3K still
  run envelopes, modulation, note fill, and command-side work on VInts where
  their tempo accumulator holds the note.
- Match S1 countdown behavior and its accumulator reset rules.
- Match S2 carry/no-carry duration extension and preserve its accumulator when
  the shipped driver does.
- Replace generic PAL tempo multiplication with the actual per-driver policy:
  no S1 compensation, and S2's extra music update every fifth PAL VInt for
  eligible songs while SFX remains single-service.
- Implement locked-on S&K's driver-global PAL repeat at the shared driver
  boundary. The shipped `fix_sndbugs=0` path
  seeds 5, reloads 6, and tests before decrementing, so every sixth PAL VInt
  repeats the full update, including SFX, music, and the speed/fade tails. Do
  not approximate it with counters owned by independently admitted sequencers.
- Seed and advance the S3K accumulator exactly as the driver does.
- Match S3K speed-shoes cadence, including the two timeout services per outer
  VInt that produce an extra music update every four VInts at value 8.

Acceptance: focused tests prove service count, duration progression,
accumulator phase, and modulation/envelope activity for all three modes,
including the locked-on PAL driver loop; ROM presentation integrations remain
green.

### Phase 2 — Request admission, priority, and takeover order

- Implement the single global SFX priority latch used by S1 and S2, including
  shipped `FixDriverBugs=0` behavior; do not invent a priority table for S3K.
- Establish channel ownership at request admission before the same-frame music
  service, rather than waiting for the first SFX chip write.
- Match ordinary BGM replacement: S1 preserves live normal/special SFX and
  rebinds their channel overrides to the new song, while S2 and S3K stop SFX.
- Match each driver's music/SFX service order, takeover writes, and release
  point. Remove injected reset/key-off behavior where the retail driver relies
  on SFX bytecode instead.
- Preserve game-specific ordinary-BGM behavior: S1 retains active SFX and
  re-marks overrides; S2 kills SFX before loading music.

Acceptance: bounded contention tests cover free and occupied channels, lower /
equal / higher priority, same-frame writes, and exact restore timing.

Status: complete for the bounded engine playback path. S1/S2 use the global
priority latch, ownership is claimed at admission, service order is per-driver,
ordinary BGM replacement is game-authentic, and PSG release follows the
shipped rest-vs-same-VInt behavior.

### Phase 3 — Pause, fade, speed, and 1-up lifecycle

- Implement driver-level pause mute/restore rather than only pausing the host
  sink, including the correct DAC-service behavior.
- Port each game's fade channel set, step count, delay, terminal cleanup, and
  shipped PSG-envelope interaction.
- Port 1-up save/replace/restore behavior, speed-state handling, and S3K's
  native fade-back instead of a generic frozen-synth swap.
- Preserve shipped S1/S2 restore bugs where their `FixBugs=0` paths are audible.

Acceptance: ordered register-write tests around pause, fade start/end, first and
repeated 1-up, and restore; short external references for transitions.

### Phase 4 — SMPS bytecode and envelope quirks

- Audit modulation, volume envelopes, PSG envelopes, note fill, ties/holds,
  transposition, and coordination flags against each shipped interpreter.
- Port S3K's shipped modulation-envelope signed-byte and command-82 bugs.
- Restore the shipped S2 spindash-release transpose and request-transform
  timing rather than the current corrected/approximated behavior.
- Add a regression for every supported bytecode quirk before changing its
  interpreter.

Acceptance: command-level chip-write fixtures and representative real ROM
songs/SFX that execute each path.

### Phase 5 — Chip, DAC, PCM, and regional behavior

- Correct the S2 DAC service-cycle constant and validate DAC latch timing.
- Select hardware-reference PSG noise and DAC interpolation defaults; retain
  enhancements only as explicit non-authentic options.
- Use region-correct YM2612/PSG/Z80 clocks.
- Implement S3K StopSEGA/SEGA PCM exclusivity through YM DAC rather than mixing
  host PCM over active SMPS playback.
- Remove non-native time caps and global FM6/DAC workarounds once their owning
  driver behavior is modeled.

Acceptance: deterministic PCM/register fixtures and short external audio
goldens at NTSC and PAL rates.

### Phase 6 — ROM loader and content hardening

- Verify supported-ROM song/SFX/voice/envelope/sample tables against exact
  retail offsets and compression framing.
- Remove silent heuristic fallbacks for supported hashes; fail clearly when a
  required mapping is absent or malformed.
- Keep runtime bytes ROM-owned. Disassemblies provide labels and meaning only.

Acceptance: all supported content IDs resolve from each verified ROM, malformed
tables fail closed, and representative playback remains byte-stable.

### Phase 7 — Cleanup after parity

- Retire or isolate the legacy duplicate backend only after the shared runtime
  owns all verified behavior.
- Rename provisional/shadow presentation terminology once it is unquestionably
  the production authority.
- Remove diagnostic scaffolding that no longer protects a playback boundary.

Cleanup is not allowed to lead the roadmap or broaden a parity change.

## Working rules

1. Start from the shipped disassembly path and cite it next to non-obvious
   behavior, especially every `FixBugs=0` choice.
2. Write a focused failing behavioral test before production changes.
3. Prefer chip writes, PCM, track state, and audible lifecycle as assertions;
   do not assert internal architecture for its own sake.
4. Put a game difference in the smallest typed owner; never add game-name
   branches to shared runtime code.
5. Stop and repartition if a proposed diagnostic change is larger than the
   playback behavior it exists to prove.
6. Do not block a useful phase on unrelated complete-run evidence closure.

## Immediate slice

Phase 1 begins with the cadence distinction because it affects nearly every
sustained note in S2 and S3K: modulation, PSG envelopes, note fill, and tempo
phase are currently updated on the wrong VInts. The implementation should be a
small sequencer/config change with source-backed tests; it must not modify the
native observer, trace schemas, or semantic evidence fixtures.
