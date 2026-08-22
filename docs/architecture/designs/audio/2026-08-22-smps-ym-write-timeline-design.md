# SMPS YM2612 Write Timeline Design

## Decision

Add a small, driver-owned timeline for source-derived YM2612 writes emitted by
SMPS service routines. The first implementation covers the locked-on S3K FM
admission, voice upload, note-on, completion, and music-voice restore path that
owns the audible Blue Sphere defect. It does not emulate the Z80, consume a
trace at runtime, or add sound-ID or special-stage behavior.

The change remains on `feature/ai-smps-playback-verification`. It is not merged
into `develop` until automated native parity is green and the reported sound
passes a human listen test.

## Problem

The SMPS interpreter currently executes a complete driver service as a group
of immediate calls to `VirtualSynthesizer.writeFm`. All writes produced by an
operation such as S3K `cfSetVoice` therefore reach `Ym2612Chip` on one rendered
sample.

The shipped driver does not behave that way. Its Z80 instruction stream and YM
write helpers place time between the admission key-off, maximum-release writes,
instrument fields, frequency, and key-on. That time is observable because a
YM2612 key-on begins from the operators' current envelope attenuation. It is
especially audible when an SFX has finished, music has temporarily reclaimed
FM5, and a later SFX reclaims the channel. A rapid same-SFX retrigger begins
from a different envelope history and largely hides the defect.

The existing completed-replay test compares two OpenGGF runs. It proves that
OpenGGF is internally deterministic, but it cannot detect a timing error shared
by both runs.

## Root-cause evidence

The diagnostic spike used the locked-on S3K ROM, the reviewed complete-emeralds
BK2, BizHawk 2.11, and the diagnostic Genesis Plus GX core.

- Replaying every native YM write at its native timestamp through OpenGGF's
  `Ym2612Chip` closely matches native FM5 output for all twelve observed Blue
  Sphere pickups. This excludes the Java YM2612 envelope implementation as the
  primary cause.
- A controlled OpenGGF driver run aligned to the native special-stage music
  start and native pickup intervals emits the same FM5 register values and the
  same SFX completion/music-restore voice as the shipped driver.
- Native `cfSetVoice` spans roughly 225 observed internal YM samples from its
  first maximum-release write to key-on. OpenGGF emits the corresponding batch
  at one sample ordinal.
- Collapsing only the native FM5 writes by VInt, without changing any register
  or value, reproduces OpenGGF's fresh-after-gap key-on envelope. At the
  representative isolated pickup, the collapsed replay reports operator
  attenuation `[132, 385, 273, 1023]`; the aligned OpenGGF run reports
  `[130, 385, 274, 1023]`.
- Overlapping pickups remain stable in both paths. The divergence appears when
  the previous SFX has completed and FM5 has crossed the release/restore
  boundary, matching the listening report.

The spike code and scratch core instrumentation are diagnostic only. They are
not production dependencies and must be removed before delivery.

## Rejected approaches

### Blue Sphere delay or envelope normalization

Rejected. A sound-ID check, fixed gain, forced attenuation, or special-stage
condition would fit one recording rather than model the driver. It would also
violate the shared-runtime carve-out rule.

### Global fixed delay after every YM write

Rejected. S1's 68k driver and the S2/S3K Z80 drivers have different routines,
branches, and service order. A universal delay would merely replace one
approximation with another and could disturb DAC, music, and unrelated SFX.

### Full Z80 or 68k sound-driver emulation

Rejected for this delivery. It could eventually provide the strongest timing
model, but it would displace the current S3K release work and greatly expand
the ownership, save-state, and content-loading surface.

## Architecture

### Source-owned timing profile

`SmpsSequencerConfig` gains an immutable YM service-timing profile. The profile
identifies source operations, not games, zones, sounds, register values, or
trace coordinates. Initial operation kinds are limited to:

- FM admission key-off and SSG-EG clear;
- S3K maximum-release preparation;
- S3K FM instrument upload;
- FM frequency and key-on;
- S3K SFX completion and music-instrument restore.

Relative offsets are derived from the shipped `fix_sndbugs = 0` instruction
paths and documented beside the profile. Native traces validate the result but
are not the authority for constants and are never read by runtime code.

The profile is absent for a driver until its source path has been audited. An
absent profile retains the existing immediate behavior; no game inherits S3K
timing accidentally.

### Driver-owned write queue

`SmpsDriver` owns a bounded `YmWriteTimeline`. During a profiled operation,
sequencer writes retain their existing order and values but are recorded with a
monotonic relative due time. Rendering drains due writes before producing the
corresponding sample.

The queue stores only already-authorized hardware mutations:

- lock/contention is decided when the SMPS operation schedules the write;
- draining does not repeat arbitration or inspect current gameplay state;
- queued entries retain the source sequencer identity needed by diagnostics;
- PSG and DAC paths are unchanged by this first slice.

The timeline has fixed capacity derived from the largest permitted operation.
Overflow fails before partial publication. It cannot silently drop, merge, or
reorder writes.

### Operation boundaries

Existing source-shaped methods provide the timing scopes:

- SFX admission preparation;
- `prepareVoiceSelection` / `refreshInstrument`;
- frequency preparation and note-on;
- `releaseLocks` / `setChannelOverridden(false)` restoration.

The implementation must not infer an operation from a sound ID or register
pattern. Tests must prove that the same operation profile works for another
S3K FM SFX using the same driver path.

### State, reset, and rewind

The timeline cursor and every pending entry are part of the `SmpsDriver`
snapshot. Restore reproduces pending writes and their exact due order.

Stop-all, driver replacement, and reset clear pending entries at the same
logical boundary as the synthesizer reset. Ordinary SFX completion does not
discard writes already committed by the completing driver service.

No queue entry may outlive the driver generation that scheduled it.

## S1 and S2 ruling

The same immediate-write architecture exists for S1 and S2, so both require a
source audit. They do not automatically receive the S3K profile.

For each game the audit compares:

1. admission and first-service order;
2. FM voice-write helper instruction path;
3. completion/release and music restoration;
4. an isolated SFX replay after the previous effect has completed;
5. a rapid overlapping retrigger.

If the source and a native capture show a materially different timeline, that
game receives its own typed profile and native oracle in a separate TDD slice.
If not, the audit records why immediate behavior is acceptable. This delivery
does not change S1/S2 merely for symmetry.

## Verification

### RED

A ROM-backed S3K test drives special-stage music and Blue Sphere admissions at
the native music-relative intervals. It must fail on the current atomic batch
by comparing:

- ordered FM5 register/value sequence;
- relative write ordinals within admission, upload, and key-on;
- key-on operator attenuation;
- a bounded first-onset FM5 sample digest or exact sample window;
- completion, restore, idle gap, and subsequent admission boundaries.

The oracle is generated by the native diagnostic harness from the pinned ROM
and BK2. Durable expected timing values must also cite the shipped instruction
path that produces them.

### GREEN and regression gates

- The source-timed S3K test passes for both overlapping and completed-then-idle
  pickup sequences.
- A second S3K FM SFX proves the profile is operation-based.
- Queue capacity N succeeds atomically; N-1 fails without chip mutation.
- Snapshot/restore in the middle of a pending upload is byte-identical.
- Stop-all/reset cannot leak delayed writes into a replacement driver.
- Existing S3K service-order, contention, modulation, pause, fade, 1-up, PAL,
  and special-stage playback tests remain green.
- Focused S1/S2 onset tests prove they are unchanged unless their own audited
  timing slices are implemented.
- The three-ROM audio suite and clean full-suite baseline comparison introduce
  no attributable failure.

### Human gate

The listen test must cover:

- the first Blue Sphere pickup;
- rapid consecutive pickups before completion;
- the first pickup after the preceding sound fully finishes;
- a pickup after a turn or other comparable idle gap;
- a pickup after another FM5 SFX;
- special-stage rings and music tempo as non-regression checks.

Automated parity is necessary but does not replace this gate.

## Non-goals

- Full sound-CPU emulation.
- A complete-run audio evidence or capture subsystem expansion.
- Runtime playback of trace-derived timing or values.
- Changes to gameplay collision, pickup timing, or special-stage routing.
- Gain, EQ, panning, or envelope normalization for this symptom.
- Merging or pushing before the user confirms a positive audible result.
