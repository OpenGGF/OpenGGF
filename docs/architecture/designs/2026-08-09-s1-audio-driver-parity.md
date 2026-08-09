# Sonic 1 Audio Driver Parity Harness

**Date:** 2026-08-09

## Goal

Build a differential harness that compares OpenGGF's Sonic 1 SMPS music
sequencing with the shipped Sonic 1 World REV01 driver. The first milestone is
GHZ music only. It compares logical driver state and the ordered YM2612/PSG
register transactions for a complete musical cycle plus one repeated cycle.

The harness must identify the first genuine divergence without treating final
PCM as the first diagnostic surface. PCM/sample parity and sound effects are
immediate follow-up work after the music MVP.

## Acceptance criteria

The harness is valid when all of the following hold:

1. BizHawk reaches the GHZ sound-test selection through recorded controller
   input against the verified REV01 ROM. Lua does not mutate emulated memory,
   registers, input, or savestates.
2. Capture begins at the exact invocation that accepts music ID `$81`, not at a
   gameplay or emulator frame chosen by observation.
3. Each reference record is keyed by a monotonic audio-driver invocation
   ordinal and contains normalized driver state plus ordered decoded chip
   writes for that invocation.
4. OpenGGF emits the same normalized contract from its real Sonic 1 ROM loader,
   SMPS configuration, sequencer, and synthesizer boundary.
5. The comparator distinguishes capture failure, state divergence, and
   register divergence and reports the first mismatch with bounded context.
6. Two independent BizHawk captures match byte-for-byte after normalization;
   two independent OpenGGF captures do the same.
7. The checked interval contains a proven complete musical cycle followed by a
   second identical cycle.
8. The GHZ music parity claim requires exact logical-state equality and exact
   ordered decoded-register equality throughout the checked interval.

Tool delivery and the parity result are distinct. A correct tool may initially
produce a red parity report. Any engine corrections discovered by that report
are separately reviewed changes, not part of validating the observer.

## Non-goals

- Final PCM, resampler, DAC byte-stream, or sample-accurate output parity.
- Sonic 1 sound-effect priority, channel stealing, or music/SFX contention.
- Sonic 2 or Sonic 3&K audio parity.
- Loading gameplay state from captured data.
- Automatically rewriting OpenGGF chip-write ordering after a mismatch.
- Committing a complete GHZ register stream, PCM capture, VGM, or other
  replayable representation of copyrighted music.

## Reference transport

The reference input is the user-authored movie currently located at:

```text
docs/BizHawk-2.11-linux-x64/Movies/s1-soundtest-ghz.bk2
```

Its verified properties are:

| Property | Value |
|---|---|
| SHA-256 | `622ff642d0b0835a4f77bee568f2413f288ead3306a8bc2a93e8d8f77f24ca9c` |
| Recorded frames | 991 |
| Emulator | BizHawk 2.11 |
| Core | Genesis Plus GX |
| Game | Sonic The Hedgehog World REV01 |

Implementation copies this controller-only BK2 to the tracked audio-test fixture:

```text
src/test/resources/audio/parity/s1/s1-soundtest-ghz.bk2
```

The ignored copy inside the local BizHawk installation remains untouched.

The movie is a launch transport, not the capture duration. It powers on the
ROM, passes the Sega and title sequences, enters Level Select through retail
controller input, selects `$81` in Sound Test, and triggers it. After the movie
ends, the capture runner continues advancing the emulator with neutral input
until the musical-cycle stop contract succeeds. This behavior is opt-in: the
shared probe runtime continues to exit at movie completion for existing
diagnostics.

The runner validates the ROM as Sonic 1 World REV01 using the project's
documented CRC32/SHA-1 values. The BK2 hash and sync settings are also checked
before execution. A wrong ROM, movie, core configuration, or movie hash is a
capture failure rather than a parity mismatch.

## Capture epoch and clock

Sonic 1's SMPS driver is 68000-side. The unit of comparison is an invocation of
`UpdateMusic` at ROM address `$71B4C`, not a gameplay frame, emulator frame, or
OpenGGF presentation packet.

The probe remains dormant through Sega and title playback. It arms only when
execution reaches `Sound_PlayBGM` at `$71FD2` with `D7 == $81`. That invocation
is audio tick zero. Events earlier in the invocation are discarded so title
music cannot contaminate the GHZ initialization transaction. Events from
`Sound_PlayBGM` onward, followed by the post-invocation state, form tick zero.
Subsequent `UpdateMusic` invocations increment the ordinal by one.

BizHawk frame count, movie frame, game mode, and interrupt context are retained
as diagnostics. They never select or realign a comparison record.

The ROM capture records how many `UpdateMusic` invocations occur between title
music `$8A` being accepted and GHZ `$81` being accepted. The OpenGGF side
initializes the real Sonic 1 audio profile and ROM loader, starts title music,
advances it for that recorded invocation count without comparing its output,
then requests `$81` and labels that initialization service as tick zero. This
mirrors the real non-empty driver/chip precondition without making Sega, title,
or Level Select behavior part of the GHZ comparison. The observer advances
through the same sequencer service boundary and does not render extra frames
merely to make state line up. If title pre-roll differences affect the GHZ
transition, the report classifies tick zero as a precondition-sensitive
divergence instead of silently dropping initialization writes.

## Components

### BizHawk observer

A Sonic 1-specific read-only Lua observer owns:

- ROM, BK2, core, and sync-setting validation;
- `$81` epoch detection;
- `UpdateMusic` invocation bracketing;
- raw chip-port event capture;
- post-invocation reads of the `$FFF000` SMPS state block;
- contamination and stop-condition checks; and
- deterministic JSONL output under `target/audio-parity/s1-ghz/`.

The observer may reuse the shared probe runtime after adding an opt-in
continue-after-movie contract. Existing probe behavior must remain unchanged.
The observer must not call `mainmemory.write*`, `memory.write*`, `joypad.set`,
savestate mutation APIs, or `emu.setregister`.

### OpenGGF observer

A test/tool-only Java observer uses the existing `SmpsDriverSnapshot`,
`SmpsSequencerSnapshot`, and `SmpsTrackSnapshot` surfaces where they express ROM
semantics. A narrow observation port records ordered calls at the synthesizer
boundary without changing their order or chip behavior. It does not add
gameplay-visible logging, introduce a second sequencer, or make production
behavior depend on comparison state.

The Java observer emits the same normalized JSONL schema as Lua. Normalization
belongs to a dedicated audio-parity package rather than `AudioManager`,
`SmpsDriver`, or `SmpsSequencer`.

### Comparator

A local CLI reads one ROM reference stream and one OpenGGF stream. It validates
metadata and ordinal continuity before comparing state or events. It writes a
human-readable report and a compact machine-readable summary beneath
`target/audio-parity/s1-ghz/`.

## Raw chip-port capture

For every audio tick the ROM observer retains raw writes, in execution order,
to:

- YM2612 port 0 address/data at `$A04000`/`$A04001`;
- YM2612 port 1 address/data at `$A04002`/`$A04003`; and
- PSG at `$C00011`.

Before the main capture relies on memory-write callbacks, a short validation
probe must demonstrate that Genesis Plus GX supplies the written byte correctly
for these write-only ports. The validation cross-checks the FM result against
the real data-write instructions at `$72752` and `$72788`, where `D0` holds the
register and `D1` holds the value.

If the callback does not expose reliable values, capture falls back to a
reviewed PC-site manifest. PSG coverage then includes every direct and indirect
write instruction in the shipped driver, including the four writes through
`PSGSilenceAll`'s address register. The manifest is verified against the REV01
ROM bytes and cites `docs/s1disasm/sonic.lst`; it is not inferred from observed
GHZ execution alone.

The decoder folds valid YM address/data pairs into:

```text
{ chip: "ym2612", port: 0|1, register: 0x00..0xFF, value: 0x00..0xFF }
```

PSG writes remain:

```text
{ chip: "psg", value: 0x00..0xFF }
```

An orphaned YM address or data operation, malformed pair, or unsupported port
operation fails capture. Raw events remain in local output for diagnosis;
decoded transactions form the parity contract.

## Logical state schema

Each tick contains global state, a fixed set of music-track roles, and ordered
decoded writes. The initial schema is versioned independently of trace schema 5
because this is not a trace-replay fixture or gameplay authority.

### Global state

The shared subset includes:

- active music ID;
- tempo reload and current tempo counter;
- pause state;
- fade direction, delay, and remaining steps;
- normal/speed-shoes tempo state; and
- the driver flags needed to interpret the music-track update.

Queue bytes are retained as capture-contamination evidence but are not a way to
hydrate or synchronize OpenGGF.

### Music tracks

Records use fixed semantic roles:

```text
DAC, FM1, FM2, FM3, FM4, FM5, FM6, PSG1, PSG2, PSG3
```

An unused ROM slot and an absent OpenGGF track both normalize to an explicit
inactive role. Active roles compare the genuine common subset:

- playback, rest, override, and do-not-attack/tie flags;
- hardware channel and track type;
- sequence pointer;
- duration timeout and saved duration;
- transpose and volume;
- current frequency;
- pan, AMS, and FMS;
- voice/instrument identity;
- PSG envelope identity, position, and effective value;
- note-fill timeout and reload;
- modulation pointer, delay, speed, delta, steps, and accumulator;
- detune;
- loop counters; and
- return-stack position and values.

ROM data pointers are normalized to offsets inside the GHZ SMPS asset. Signed
byte/word fields use explicit sign extension. Voice bytes, sequence bytes, and
other runtime asset payloads are never copied into the output.

OpenGGF-only derived caches and ROM-only unused bytes are diagnostic or ignored.
They cannot fail parity unless a documented mapping proves they represent the
same driver state.

## Musical-cycle stop contract

The capture does not use an audibly estimated duration. It computes a recurrence
signature over tempo phase and all active music-track playback state, excluding
frame numbers, ordinals, and non-musical monotonic metadata.

A repeated signature is only a candidate boundary. The candidate period is
accepted after the immediately following period has:

- the same normalized state-signature sequence; and
- the same per-tick decoded-register event hashes.

This rejects a repeated note or phrase that happens to share one state. Capture
ends at the third occurrence of the accepted boundary, yielding one proven
cycle plus one identical repeated cycle. Failure to establish the cycle within
36,000 audio-driver invocations is a capture failure with the candidate history
included in the report.

## Comparison order and diagnostics

The comparator performs these gates in order:

1. Metadata, identity, schema, and capture-integrity validation.
2. Tick count and ordinal continuity.
3. Exact normalized logical-state comparison.
4. Exact decoded-register transaction comparison, including order.
5. Local raw-bus context attachment for any register mismatch.

State divergence reports name the tick, track role, field, ROM value, and
OpenGGF value. Register divergence is classified as missing, extra, reordered,
or value-different and includes the eight preceding and eight following
transactions. The report also includes the current normalized track context and
ROM routine where the capture can identify it without guessing.

No mismatch automatically authorizes a production change. A proposed chip
ordering correction requires:

- proof that the capture point and tick alignment are correct;
- the exact shipped-ROM driver routine responsible for the order;
- an explanation of why the current OpenGGF order is incorrect rather than
  merely redundant or represented at a different abstraction boundary; and
- a focused regression test that preserves adjacent known-good transactions.

This safety policy is especially important because OpenGGF's chip-write ordering
is already largely correct and has been regressed by broad audio changes in the
past.

## Capture contamination rules

After tick zero, capture fails rather than silently filtering if any of these
occurs:

- another music or SFX request enters a sound queue;
- `$81` is accepted a second time;
- pause, fade, reset, Sega PCM, or speed-up commands are requested externally;
- the emulator leaves the stable Level Select/Sound Test mode unexpectedly;
- the sound driver is reinitialized; or
- movie completion produces non-neutral controller state.

Driver-generated DAC note activity is part of GHZ music and is not
contamination. DAC sample bytes and sample timing remain outside the MVP
comparison.

## Verification

### Lua and capture tests

- Compile Lua with `lupa.LuaRuntime().compile(...)`, matching the repository's
  established diagnostic validation command.
- Enforce the read-only probe policy lexically and through focused contract
  tests.
- Verify the ROM and BK2 identities before capture.
- Prove that the `$71FD2`/`D7 == $81` epoch is reached exactly once.
- Cross-check raw port callbacks against PC hooks over a short initialization
  window.
- Test malformed YM pairing, missing PSG coverage, contamination, movie-end
  continuation, safety-limit exhaustion, and cleanup.
- Run the reference capture twice and require byte-identical normalized output.

### Java tests

- Unit-test every signed-field and pointer normalization.
- Test fixed role mapping, inactive tracks, modulation, envelopes, loop
  counters, and return stacks with synthetic snapshots.
- Use a recording synthesizer seam to prove observation preserves the exact
  call order and values.
- Mutation-test the comparator with changed state, changed value, swapped
  events, missing events, and extra events.
- Run the OpenGGF capture twice and require byte-identical normalized output.

### End-to-end validation

Run the BizHawk capture, OpenGGF capture, and comparator against the discovered
REV01 ROM. The report must show a valid two-cycle interval. Zero state and
register mismatches are required before documenting GHZ music parity. A red
report remains a valid diagnostic result but does not establish parity.

Focused and full Maven verification run on JDK 21. Full-suite delivery compares
against the integration baseline using the repository's documented
failure-manifest method; pre-existing failures do not block delivery, but no
new or worsened result is permitted.

## Repository and copyright boundaries

Tracked deliverables may include source code, tests, the controller-only BK2,
schema documentation, and compact non-reconstructive summaries. Detailed ROM
and engine capture streams live only under ignored `target/audio-parity/`
paths.

Never commit:

- complete YM2612/PSG transaction streams;
- PCM, WAV, or VGM output;
- sequence or voice bytes copied from the ROM; or
- any other payload capable of replaying the GHZ composition without the
  user's ROM.

The Lua and Java streams are comparison-only. Runtime gameplay and audio state
must never read them, and they grant no timing or behavior authority.

## Follow-up sequence

After GHZ music reaches parity:

1. Add music-only cases that cover distinct S1 driver features without changing
   the schema opportunistically.
2. Add isolated SFX captures.
3. Add music/SFX contention and channel-override cases.
4. Add DAC timing and final PCM comparison as separately designed layers.
5. Consider Sonic 2 and Sonic 3&K only after the contract proves portable
   without game-name carve-outs in shared runtime code.
