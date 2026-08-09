# Sonic 1 GHZ1 Gameplay Audio Timeline Design

## Requirements

### Goal

Use the existing `sonic1-complete-withemeralds.bk2` gameplay recording to
identify when music and sound effects play during GHZ1 and compare the natural
ROM and OpenGGF timelines. The MVP must expose both music/SFX channel
contention and SFX/SFX contention.

### Acceptance criteria

- The reference capture uses Sonic 1 World REV01, BizHawk 2.11, Genesis Plus
  GX, and the exact committed complete-game BK2.
- GHZ1 is selected from the committed run manifest: BK2 offset `860`, trace
  frame count `4115`, giving the half-open trace-row interval `[860, 4975)`.
  The following movie row is an intentional gap and the special-stage mode
  transition occurs at frame `4976`.
- The reference output identifies each music or SFX request, the movie frame
  and sound-driver update that consumes it, and whether priority accepts or
  rejects it.
- The output identifies music-track ownership before, during, and after SFX
  overrides, plus replacement between overlapping SFX.
- OpenGGF runs the committed GHZ1 replay normally and reports commands it
  naturally produces. Reference data never injects sound IDs or other gameplay
  values into the engine.
- Comparison reports the first mismatch in request timing, request identity,
  acceptance, channel ownership, or restoration.
- The existing sound-test chip-write parity remains available for follow-up
  diagnosis; gameplay chip-write equality is not part of this MVP.
- Outputs are deterministic, strictly validated, bounded in memory, and
  published atomically beneath `target/audio-parity/`.

### Non-goals

- Do not cover later acts in the MVP.
- Do not create new BK2 recordings.
- Do not replace or weaken the existing GHZ sound-test music parity contract.
- Do not make trace data an audio authority or production runtime input.
- Do not merge or push before human listening and gameplay review.

### Risks and conservative choices

- A movie frame can contain more than one driver update. Every event therefore
  carries both an absolute BK2 frame and a monotonically increasing audio-tick
  ordinal.
- Queue writes and queue consumption are different events. Capturing both is
  required to distinguish gameplay scheduling from driver priority behavior.
- The ROM has three queue slots and a global SFX priority. Queue-slot identity,
  pre-consumption priority, selected sound ID, and post-consumption priority
  remain explicit rather than inferred from chip writes.
- SFX ownership is not equivalent to audible output. The MVP compares logical
  requests, decisions, and ownership; detailed chip writes remain a follow-up
  layer after the causal timeline is trustworthy.

## Exploration synthesis

The committed run manifest at
`src/test/resources/traces/s1/runs/s1-sonic-complete-withemeralds/run_manifest.json`
defines GHZ1 at BK2 frame `860` for `4115` trace frames. Its segment metadata
names the same committed BK2, so no new fixture or fitted boundary is needed.
The BK2 has SHA-256
`f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b`.

On the ROM side, `QueueSound1` (`$00138E`), `QueueSound2` (`$001394`), and
`QueueSound3` (`$00139A`) write the three bytes at sound RAM offsets
`$0A..$0C`. `CycleSoundQueue` (`$071F02`) chooses a queued request, and
`PlaySoundID` (`$071F4C`) applies the priority rules. Accepted dispatch enters
the BGM path at `$071FD2`, normal SFX at `$0721C6`, or special SFX at
`$07230C`. The
existing parity probe already verifies those opcode sites, brackets complete
`UpdateMusic` invocations at `$071B4C/$071C4C`, captures ordered YM2612/PSG
writes, and reads the global priority plus music-track override bits. The
gameplay observer should reuse those proven primitives without inheriting the
sound-test-only `$81` epoch or recurrence assertions.

On the engine side, `AudioManager` already records natural `PlayMusic` and
`PlaySfx` commands in an `AudioCommandTimeline` after `beginFrame`. The live
SMPS presentation snapshot identifies sequencers and FM/PSG lock owners. The
capture therefore needs observation seams, not a second command source. Its
GHZ1 runner extends `VisualRunReplayHarness` with the production outer-frame
audio boundary: `GameLoop.presentOuterFrame(false, false)` followed by
`GameServices.audio().update()` exactly once. It must not advance an SMPS
driver directly.

The alternative of replaying sidecar sound IDs directly into OpenGGF was
rejected for the authoritative path. It would bypass gameplay scheduling and
violate the repository rule that trace data is comparison-only. A separate
tool-only SMPS command reproducer may be added later for diagnosis, but its
result cannot satisfy this design's end-to-end acceptance criteria.

## Architecture decision

### Ownership and boundaries

1. A dedicated BizHawk gameplay-audio probe owns ROM observation. It validates
   identities and GHZ1 bounds, observes queue writes and consumption, brackets
   sound-driver ticks, and publishes a compact JSONL timeline.
2. A tooling-only Java schema owns strict parsing and canonical serialization
   of both producers' timeline records.
3. A GHZ1 replay observer reads OpenGGF's existing command timeline and
   presentation snapshots after normal frame execution. It never submits a
   command obtained from reference data.
4. A comparator owns alignment and first-mismatch reporting. It compares
   semantic events before consulting diagnostic bus writes.

Production gameplay code must not import the parity tooling package. Any new
observation seam belongs at an existing audio boundary, is disabled by
default, is omitted from snapshots, and cannot alter ordering or state.

### Timeline lifecycle

The reference producer starts observing before movie frame `860`, because GHZ
music `$81` is queued during level/title-card setup before gameplay control is
unlocked. It buffers observations from boot, discards everything before the
last queue-1 `$81` request preceding the manifest arm, retains that finite
pre-roll, and marks frame `860` as `gameplay_arm`; trace rows remain the
half-open interval `[860, 4975)`. Audio ticks are numbered from zero at the
first retained complete `UpdateMusic` invocation. Queue writes retain their
absolute BK2 frame even when consumed on a later audio tick. The capture closes
after the manifest interval and its single gap, at the direct
gameplay-to-special-stage transition on frame `4976`.

The OpenGGF producer runs the committed GHZ1 segment through
`VisualRunReplayHarness.replay(..., stopAfterSegmentBody(0))`. This bypasses
only the master title screen while retaining production run launch, level
bootstrap, and title-card behavior. Its segment frame `0` maps to BK2 frame
`860`. It emits the same absolute movie-frame coordinate and assigns
audio-tick ordinals at the normal presentation advance boundary. Lag rows
still consume one BK2 row and one outer presentation; gameplay counters are
diagnostic and never substitute for the BK2 coordinate.

### Record contract

The first JSONL record is metadata containing schema version, capture kind,
ROM and BK2 digests, emulator/producer identity, GHZ1 bounds, and field
inventories. Subsequent records are one of:

- `request`: queue-write/request origin, movie frame, within-frame order,
  sound ID, and class (`music`, `sfx`, `special_sfx`, or `command`);
- `decision`: audio tick, queue slot, selected sound ID, priority before and
  after, and result (`accepted` or `rejected`);
- `ownership`: audio tick and fixed hardware role with music-overridden and
  normal/special-SFX owner state;
- `writes`: optional ordered decoded YM2612/PSG events for that audio tick.

The GHZ1 MVP requires `request`, `decision`, and `ownership`. Reference chip
writes may be captured immediately by reusing the proven callback/manifest
source selection, but OpenGGF write attachment and write equality are deferred
unless they can be added through the existing disabled-by-default observer
without changing presentation ownership or cadence.

Numbers are unsigned normalized integers. Records have exact allowed fields,
duplicate keys are rejected, ordinals are monotonic, and event arrays preserve
source order. Diagnostic raw callback data is validated but excluded from
semantic equality.

### Comparison order

The comparator validates both complete inputs before comparison, then checks:

1. capture identity and GHZ1 bounds;
2. request frame/order/class/ID;
3. decision tick, selected queue slot, acceptance, and priority transition;
4. ownership transitions and music restoration;
5. normalized ownership context for the first mismatch.

There is no event realignment after the first missing, extra, reordered, or
value-different record. Reports contain at most eight preceding and eight
following semantic records from each side.

### Failure and publication behavior

Identity mismatch, malformed JSON, an incomplete driver invocation, an event
before the retained GHZ `$81` pre-roll anchor or after frame `4976`,
non-monotonic ordinals, missing terminal metadata, or a changed input between
validation and comparison is a capture failure. Producers write
to a sibling temporary file, validate it, and publish with an atomic create-new
operation. A failed run preserves prior outputs and deletes its temporary file.

## Feature behavior and acceptance tests

- A synthetic Lua test proves queue-write, later consumption, equal-priority
  replacement, lower-priority rejection, and music-channel restoration.
- Java schema tests reject unknown, duplicate, type-invalid, out-of-range, and
  non-monotonic data at every nesting level.
- Engine observation tests prove that normal `AudioManager` requests are
  visible while reference records cannot submit commands.
- A synthetic comparator test distinguishes request timing, sound-ID,
  acceptance, priority, ownership, and restoration mismatches.
- A real BizHawk GHZ1 discovery run must contain GHZ music and at least one SFX
  request. The report inventories observed IDs and both contention classes
  present in the movie; absence of a class is reported rather than fabricated.
- Two reference captures and two OpenGGF captures must be byte-identical within
  each producer before any parity conclusion is reported.

## Rollback and extension

All new runtime hooks are disabled-by-default observers, so rollback removes
the tooling and seams without changing saved state or gameplay behavior. Once
GHZ1 is useful, later acts can reuse the manifest-selected interval contract;
no schema field is keyed to GHZ by name or fitted event frames.
