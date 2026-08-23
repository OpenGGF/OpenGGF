# S3K Semantic Sound-Driver Timing Design

## Status and decision

Extend the existing high-level SMPS runtime with a source-derived semantic
execution clock for the locked-on Sonic 3&K version-4 sound driver. The clock
will timestamp chip writes from actual service state and source branches. It
will not emulate the complete Z80, identify individual sounds, or read native
trace data at runtime.

This is the middle course between two rejected extremes:

- a Collapse-specific delay or fade would conceal one symptom while leaving
  the same timing defect in every other PSG effect;
- running the original Z80 driver as a second production audio engine would
  duplicate stream, request, snapshot, rewind, and modding ownership.

The first delivery slice covers the common S3K SFX PSG service grammar reached
by the authenticated first-slice inventory: active/inactive slot dispatch,
duration service, next-note control flow, modulation, PSG volume envelopes,
frequency/noise/volume writes, rest, and track termination. Collapse and Dash
are acceptance cases, not runtime selectors. Every S3K SFX that follows an
authenticated covered path receives the same timing behavior.

This design amends, but does not replace,
`2026-08-23-s3k-smps-driver-pcm-parity-design.md`. Its evidence layers,
authenticated dialect tuple, inventory, trace-is-comparison-only rule, and
human listening gate remain normative.

## Problem statement

The current runtime reproduces Collapse's semantic lifetime and effective PSG
register state but not the native waveform. The authenticated native capture
and a diagnostic OpenGGF chip tap show why:

- native Collapse PSG writes occur roughly 58,000 to 120,000 Mega Drive master
  cycles after each VInt;
- OpenGGF publishes the complete PSG journal at the host service boundary;
- native PSG remains active through relative frame 121 and its final frame has
  RMS `44.695811`, with 107 non-zero native samples before the terminal mute;
- the corresponding OpenGGF tail frame has RMS `188.444964` and the next frame
  is fully silent;
- the engine and native effective PSG state digests nevertheless agree because
  that comparison discards sub-frame timing and phase.

The result is the reported dry, abrupt ending. Earlier services also reset or
change tone/noise/volume at the wrong sub-frame position, so fixing only the
terminal mute would not reproduce the effect's repeating, echo-like texture.

The source explains the required ownership. Locked-on version 4 calls
`zUpdateSFXTracks` before music, scans every SFX slot, and dispatches active FM
or PSG tracks (`Z80 Sound Driver.asm:727-759`). PSG service then runs the timer,
note parser, modulation, frequency output, volume envelope, and PSG writes in
one ordered path (`4058-4147`). Rest writes its attenuation in
`zRestTrack`/`zSilencePSGChannel` (`4205-4247`), while `cfStopTrack` releases
and silences a PSG owner through `zStopPSGTrack` (`3423-3529`). The Z80 returns
to the DAC playback
or idle loop, whose interrupt-enable windows determine the next service-entry
phase (`4249-4335`).

## Goals

1. Preserve source order and source-derived sub-frame timing for covered S3K
   YM and PSG writes under one driver-service clock.
2. Select timing from dialect, semantic path, and live track/driver state; never
   from sound ID, zone, movie, frame number, or captured amplitude.
3. Keep the existing ROM-backed high-level SMPS interpreter, request system,
   modding boundary, chip cores, presentation pipeline, and rewind model.
4. Make timing coverage finite and reviewable through the existing S3K
   reachability and driver-service inventories.
5. Leave S1, S2, and standalone-S3 compatibility behavior unchanged until each
   has its own source proof.
6. Make later timing-family additions incremental rather than requiring another
   driver or a new public runtime protocol.

## Non-goals

- full Z80 instruction, memory, bank, or interrupt emulation;
- runtime execution of disassembly text, diagnostic traces, or oracle JSON;
- bit-identical complete-game PCM in this first slice;
- timing every S3K routine before it has source and native evidence;
- changing chip algorithms, gains, filters, or presentation to compensate for
  a driver-scheduling mismatch;
- delaying gameplay or changing what any SMPS stream does.

## Authorities and invariants

The first slice is authenticated to locked-on S&K with:

```text
ROM SHA-1: CFBF98C36C776677290A872547AC47C53D2761D6
SonicDriverVer = 4
fix_sndbugs = 0
FixMusicAndSFXDataBugs = 0
FixBugs = 0
```

Source constants come from checked executed-instruction rows joined to the
checked-out disassembly. Native GPGX captures validate those calculations but
cannot supply production timing constants. Every retained capture has A/B
byte equality, fixed caps, provenance hashes, and zero overflow/fault. The
runtime never opens the retained artifacts.

All arithmetic uses checked `long` operations. All pending writes are bounded,
self-contained, generation-stamped, and snapshot-safe. A service either
publishes its complete write/observer journal or restores its exact prior
state. Chip callbacks remain drain-bound; logical service callbacks remain
commit-bound.

## Architecture

### 1. Semantic execution journal

The sequencer already takes the semantic branches that correspond to the
shipped routines. During a timed service it will append compact typed events to
the driver's unpublished transaction instead of exposing a new public tracing
API. The event vocabulary is deliberately semantic and finite:

```text
SERVICE_ENTRY
TRACK_SLOT(active, type, slot)
TRACK_TIMER(expired)
STREAM_COMMAND(kind, operandClass)
NEXT_NOTE(rest, tie, durationClass)
MODULATION(branch, stepCount)
PSG_ENVELOPE(branch, valueClass)
FM_WRITE(port, register, value)
PSG_WRITE(value)
TRACK_TERMINAL(restOrStop)
SERVICE_EXIT
```

Events contain only state already used to take the runtime branch. They carry
no game name, sound ID, trace coordinate, or waveform value. Exact hardware
write values stay in their write entries so a timing program cannot authorize
the wrong write.

The journal is private to `SmpsDriver` and the configured timing profile.
Architecture guards permit emission only from the sequencer operations that
own the corresponding source branch. An event outside the profile grammar
poisons the unpublished timed service; it cannot partially publish.

The native loop scans seven fixed SFX RAM slots: FM3, FM4, FM5, FM6, PSG1,
PSG2, and PSG3. OpenGGF stores active effects as sequencers rather than a fixed
RAM array, so the configured profile also provides a typed
`DriverTrackSlotLayout`. Admission projects each physical channel claim onto
exactly one native slot; inactive slots remain explicit timing events. A second
owner for one slot is rejected by the existing contention transaction before
timing begins. The projection is rebuilt from semantic channel ownership on
restore and checked against its captured identity; it is not keyed by sound.

### 2. Source-derived timing program

`DriverExecutionTimingProfile` is a typed configuration owner copied by
`SmpsAssetCatalog`. Its disabled singleton preserves current behavior. The
locked-on profile consumes the semantic journal and returns an immutable
`ResolvedDriverService` containing:

- the service-entry master cycle;
- the next CPU phase state;
- an exact due master cycle for every YM and PSG write;
- the source row/citation identity used for each cost;
- the number and order of consumed semantic events.

The production table is generated from a checked calculation artifact. Each
row records decoded PC/opcode, executed branch outcome, Z80 T-states, GPGX's
explicit three-T-state average bank-window wait where applicable, YM busy-wait
behavior, semantic event, and source citation. Tests independently sum the
rows, join every semantic event and hardware write, and reject deletion,
reorder, wrong branch, wrong opcode, wrong write value, or orphan rows.

Timing programs are selected by the event grammar and live state. For example,
the same PSG update program handles any covered tone/noise track whose actual
events are timer-sustain, frequency calculation, modulation branch, envelope
branch, and three PSG writes. Collapse's ID is irrelevant.

Before executing a timed service, a bounded side-effect-free classifier proves
that the reachable service path is covered. If it is not covered, the service
uses the existing immediate behavior and its inventory row remains
`timing_status = UNAVAILABLE` or `PARTIAL`. A path declared `EXACT` must never
fall back; tests poison every selector boundary. This prevents a new retail
sound from crashing while preventing claimed coverage from silently lying.

### 3. Driver CPU phase

Absolute service-entry timing cannot be derived from the host VInt alone. The
Z80 accepts the interrupt only during an enabled window in `zPlayDigitalAudio`.
The profile therefore owns a small `S3kAudioCpuPhase` value, not a general Z80
machine:

```text
mode: DAC_IDLE | DAC_PLAYBACK
loopPhaseMasterCycles
dacRate
dacNibblePhase
lastRenderedMasterCycle
```

The phase advances from rendered master cycles and the existing DAC playback
state. It models only the source-defined idle-loop and two-nibble playback-loop
costs and their `EI`/`DI` windows. Interrupt entry resolves to the first legal
window at or after VInt. It then hands one service cursor to the semantic timing
program. On service exit the returned phase resumes the matching loop.

Driver initialization anchors the phase at the first `.dac_idle_loop` `EI`
after the source-derived DAC-disable write. Hard reset reconstructs that exact
seed at the current chip frontier. No arbitrary host-time or capture-phase
offset is accepted.

This state does not decode samples or generate DAC values. `Ym2612Chip` remains
the DAC producer. The phase model only answers when the already-existing driver
service and its writes occur. Unsupported SEGA PCM and unmodeled DAC branches
remain explicit inventory frontiers until separately implemented.

`S3kAudioCpuPhase` is captured by `SmpsDriverSnapshot`, live command mutation
tokens, presentation replacement, and rewind. Restore validates dialect and
clock identity. Phase is driver-global, never per sequencer.

### 4. PSG write timeline

Add a bounded `PsgWriteTimeline` beside `YmWriteTimeline`. An entry contains:

```text
dueMasterCycle
ordinal
value
driverGeneration
serviceOrdinal
SmpsSourceDescriptor
semanticSegment
```

The timeline capacity is exactly 4,096 entries. It validates nondecreasing due cycle, dense
ordinal, current generation, source identity, and byte range. Commit is atomic.
Snapshots preserve pending entries, next ordinal, capacity, and generation.
Hard reset, synth replacement, and full silence cross the same generation
barriers as YM. Ordinary SFX completion does not erase already committed writes.

`PsgChip.renderStereo` drains entries in master-cycle order. Rendering is split
at an entry's exact PSG clock boundary, applies the write, then continues from
the preserved oscillator/LFSR and blip-resampler state. Equal-cycle entries
retain source ordinal. The chip observer fires only when the real write drains;
discarded generation entries emit no callback.

Untimed PSG writes remain immediate when no timed predecessor exists. A timed service cannot mix immediate and
scheduled PSG publication: doing so aborts before commit. YM and PSG entries
share the resolved service ordinal and CPU cursor, preserving cross-chip source
order without merging their chip-specific render queues.

Once a timed SFX service has committed delayed writes, a later unprofiled
same-VInt sibling cannot overtake them. Its PSG writes enter an ordered fence at
or after the timed service cursor. They preserve native SFX-before-music order
but remain classified timing-partial until their own source program is added.

### 5. Transaction, capacity, and presentation

The existing service reservation independently preflights each chip timeline's
aggregate maximum for every service that can occur in the requested render
horizon, including the locked-on PAL repeat. N succeeds; N-1 fails before sequencer,
phase, timeline, chip, observer, lock, or diagnostic ordinal mutation.

The PSG shadow used for observer snapshot fidelity replays committed entries in
due order up to each frozen logical boundary. It does not apply future entries
early. A service-end snapshot therefore contains the chip state observable at
that boundary plus the self-contained pending timeline.

Both `SAMPLE_ACCURATE` and `HYBRID` rendering fence at the earliest pending YM
or PSG write and the next modeled driver service. Chunk partitioning must yield
byte-identical PCM and deep-equal driver/chip/timeline snapshots.

## First-slice coverage

The first slice implements the common locked-on SFX PSG grammar exercised by
the finite ROM-backed first-slice inventory:

- scan inactive and active SFX track slots in native order;
- timer sustain and expiry;
- note/rest/tie and the already-supported coordination commands reached before
  the next hardware write;
- `zPrepareModulation`, `zUpdateFreq`, and reached `zDoModulation` branches;
- PSG frequency, noise, and volume-envelope writes;
- `zRestTrack`, `zSilencePSGChannel`, and `cfStopTrack` PSG termination;
- resume into the DAC idle/playback loop.

The implementation must run a ROM-wide SFX census. Every stream service shape
is classified as covered or unavailable using its semantic path; no case list
is maintained. Collapse and Dash must be covered. At least one unrelated S3K
PSG SFX from each distinct covered grammar family is an acceptance control.

Existing S3K YM source timing remains enabled. The new shared service cursor
must reproduce its accepted relative write vectors and may improve absolute
placement, but cannot regress Blue Sphere, Ring Loss, Spike Hit, Spindash, or
music restore. S1 and S2 profiles remain byte-for-byte behavior controls.

## Evidence and tests

### Strict RED

Before production changes, a package-confined PCM diagnostic captures Collapse
through the real driver and asserts native component boundaries. The current
engine must fail because the native partial terminal frame contains 107
non-zero PSG samples while the aligned engine frame contains zero. Additional
REDs compare selected attack/repeat-frame native PSG digests so a terminal-only
delay cannot pass.

### Source proof

- capture fresh A/B locked-on instruction/write/PCM streams with the pinned
  headless GPGX core;
- generate checked semantic timing rows from decoded instructions;
- independently reproduce the artifact and compare it byte-for-byte;
- prove the calculated write cycles equal every selected native group without
  reading captured deltas into production rows;
- retain zero DMA/fault/overflow and exact source-owner joins.

### Runtime proof

- timeline ordering, equal-cycle order, capacity N/N-1, overflow, stale
  generation, reset-before-due, ordinary completion retention;
- snapshot before drain, partial drain, restore, live rollback, observer
  exception, retry-once, and PSG shadow fidelity;
- sample/hybrid and buffer-partition equality;
- exact effective PSG writes and component PCM for Collapse and Dash;
- Collapse's repeating noise texture and partial final frame;
- no change to semantic lifecycle, request admission, priority, or channel
  ownership;
- ROM-wide covered/unavailable census stability;
- S3K YM parity and S1/S2 controls.

### Listening proof

Automated gates are necessary but not sufficient. The handoff build repeats:

- Collapse in isolation and over music, including its complete tail;
- Spindash Release;
- Blue Sphere and ring collection;
- Invincibility melody note fills;
- one unrelated PSG-noise SFX and one PSG-tone SFX.

No merge or push occurs until the user confirms a positive improvement.

## Failure handling

- A malformed timing artifact, ambiguous semantic selector, event mismatch,
  write mismatch, arithmetic overflow, capacity failure, or invalid snapshot
  aborts the unpublished service and restores its mutation token.
- An inventory path not yet claimed exact uses existing immediate timing and is
  reported as unavailable; it is not guessed from a neighboring path.
- A path claimed exact but unresolved is a test and diagnostic failure, never a
  silent fallback.
- Native capture disagreement blocks that coverage family. It does not invite
  tuning a delay until PCM looks closer.

## Rejected alternatives

### Per-sound timing constants

Keying a delay to Collapse `$59`, its frame 121, or its observed RMS would be a
fixture-fitted runtime carve-out. It would not correct other streams using the
same routines and is forbidden.

### Terminal fade or synthetic reverb

The native sound is produced by correctly phased PSG noise and volume writes,
not a presentation reverb. A fade would alter the waveform and mask earlier
mistimed bursts.

### Complete Z80 emulation

A complete CPU would give broader timing fidelity but duplicate production
driver state and greatly expand integration, rewind, and modding scope. The
semantic clock models only the source routines whose timing affects existing
high-level behavior. If future coverage requires most of the CPU instruction
set or shared RAM, this decision must be revisited explicitly rather than
letting the semantic model grow into an accidental emulator.

## Completion boundary

This slice is complete when the common covered PSG grammar is source-derived,
the ROM-wide census has no unclassified path, Collapse/Dash native PSG component
gates pass, existing YM and cross-game controls remain green, the full-suite
red identity ledger introduces no attributable regression, and the listening
gate confirms the Collapse texture and ending improve.

It does not claim complete S3K PCM parity. Remaining unavailable timing families
stay in the tracked inventory and are prioritized by audible/route impact.
