# Cross-game complete-run audio parity design

## Status

Approved for implementation on the isolated
`bugfix/ai-s1-audio-parity-frontier` branch. The branch is for human audio
testing and must not be merged into `develop` without explicit human approval.

## Requirements

### Goals

1. Replay the committed Sonic 1 all-emeralds movie naturally through OpenGGF
   and compare its complete gameplay audio trace with the shipped REV01 ROM.
2. Include every movie row from the first gameplay segment through the end of
   the movie. Segment-transition gaps, special stages, endings, and terminal
   tails are part of the comparison interval.
3. Compare raw requests, driver admissions and rejections, priorities,
   channel ownership, temporary-music save/restore behavior, normalized driver
   state, and ordered YM2612/PSG transactions without event realignment.
4. Prove Sonic 1's extra-life behavior in particular: active SFX are removed,
   later SFX are blocked, the jingle owns the applicable channels, and the
   saved music state is restored exactly.
5. Provide the same complete-run capture and comparison functionality for the
   committed Sonic 2 all-emeralds and Sonic 3 & Knuckles all-super-emeralds
   movies, preserving each shipped sound driver's semantics.
6. Capture each producer twice and require byte-identical duplicate output
   before cross-producer comparison.
7. Produce a bounded first-mismatch report and retain large detailed artifacts
   only beneath ignored `target/` directories.

### Meaning of byte parity

The parity boundary is the canonical audio trace, not host PCM. It includes:

- native sound requests and their within-frame order;
- queue consumption, resolved sound identity, admission or rejection, and the
  reason and priority transition for that decision;
- every complete sound-driver service in source order, including zero or
  multiple services in one video frame;
- the normalized live and saved driver state after each service; and
- every decoded YM2612 `(port, register, value)` transaction and every raw PSG
  byte in issue order. YM2612 register `$2A` DAC data is not excluded.

The host mixer, resampler, audio device, and final interleaved stereo PCM are
outside this contract. They can receive a later independent PCM oracle; they
must not weaken or redefine this driver/chip-transaction contract.

### Non-goals

- Do not re-open the completed S1/S2/S3K FM operator-order work.
- Do not tune chip ports, reorder writes, or reset chip state to conceal a
  driver-state mismatch.
- Do not feed recorded admissions, priorities, owners, state, or writes into
  OpenGGF.
- Do not treat an isolated sound-test or a single level as complete-run proof.
- Do not commit ROMs, detailed captures, or reconstructive trace payloads.
- Do not merge or push the work to `develop` before human listening and review.

### Project constraints

- Build and test with JDK 21.
- Verify the exact ROM, BK2, run-manifest, BizHawk 2.11 core, and observer
  identities before capture.
- Model the shipped `FixBugs = 0`, `fixBugs = 0`, and `fix_sndbugs = 0` paths.
- Runtime audio assets come from the user-supplied ROM. Disassemblies are
  research sources, never runtime fallbacks.
- Trace data is comparison-only. Production packages must not import capture
  schemas, readers, or reference artifacts.
- Every output is create-new, strictly validated before publication, and
  published atomically from a sibling staging location.

### Acceptance criteria

For each of S1, S2, and S3K:

1. Metadata pins the correct ROM SHA-1/CRC32, BK2 SHA-256 and row count,
   manifest SHA-256 and segment inventory, emulator/core identities, observer
   profile, schema, and exact half-open comparison interval.
2. The reference capture contains every absolute BK2 row in the interval once,
   including lag rows and rows outside manifest segments.
3. The OpenGGF capture contains the same frame coordinates naturally, with one
   outer presentation per consumed BK2 row and no fast-forward.
4. Driver-service and chip-event ordinals are contiguous. Zero-service and
   multi-service frames are legal and retained without folding or alignment.
5. Two reference captures are byte-identical and two OpenGGF captures are
   byte-identical.
6. Cross-producer comparison is byte-identical and reports `MATCH`.
7. Game-specific priority, 1-up, restoration, tempo, SFX-contention, DAC, and
   special-stage assertions listed below are observed in the real run.
8. The complete-run replay reaches its terminal record. A trace/gameplay abort
   is a capture failure, not a partial success.

## Exploration synthesis

### Existing S1 lanes

The sound-test lane under `tools/audio/run_s1_audio_parity.sh` proved a complete
14,690-service GHZ cycle. Its schema, recurrence proof, movie identity, state
normalizer, and engine producer are all GHZ-specific. It does not exercise
gameplay requests, SFX, transitions, or temporary music.

The semantic lane under
`tools/audio/run_s1_ghz1_gameplay_audio_timeline.sh` covers only BK2 frames
`[860,4975)`. Schema v2 correctly separates raw requests from later admissions,
but it has no chip transactions and infers some backend outcomes from submitted
commands and frame-final snapshots.

The deterministic GHZ1 captures in
`target/audio-parity/s1-ghz1-gameplay/run.avvaEipc/` expose the mandatory S1
extra-life oracle:

- frame 3698 queues music `$88` while `$87` and two SFX are active;
- frame 3699 gives FM3/FM4/FM5/PSG1/PSG2/PSG3 to `$88` in the ROM;
- raw SFX requests during the jingle receive no ROM admission; and
- frame 3910 restores `$87` to all six roles.

The existing OpenGGF reducer reports an SFX admission during this interval and
retains SFX owners at restore. `AudioCommandTimeline.PlaySfx` records submission
before `AbstractSmpsAudioBackend` can reject it through `sfxBlocked`, while
music-triggered SFX removals are not visible at the current contention seam.
The observer is therefore insufficient even before runtime behavior is judged.

More deeply, shipped S1 copies `$220` bytes of driver/music state while keeping
one physical YM2612 and PSG alive. OpenGGF currently saves a whole old
`SmpsDriver` and chip, creates a fresh driver/chip for the jingle, then restores
the frozen chip and refreshes voices. Exact transaction parity requires one
continuous chip and source-accurate state save/restore; refresh writes cannot be
used as a compensating approximation.

The S1 movie has 225,101 rows and 34 manifest segments. Its segments account
for 208,586 rows; 15,655 rows after the first gameplay row are transition gaps
or terminal tail. Existing visual-run proof reaches only segment 11 near frame
46,806, so no current test proves the complete route.

### S2 findings

The committed S2 movie has SHA-256
`e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5`,
259,590 input rows, 35 segments, and seven special stages. Its comparison epoch
starts at frame 769; the last segment ends at 245,021 and the movie continues
through a terminal tail.

S2's Z80 driver uses one global SFX priority before channel allocation. A
lower-priority request is rejected as a whole and an equal-priority request
replaces the old request. The current `SmpsDriver` adds a sequencer first and
arbitrates per hardware role, so a request can partially occupy free roles even
when the ROM rejects it. This must become a game-profile-owned request-admission
policy before per-role locking.

The shipped 68K-to-Z80 bridge also loops over four SFX bytes although only
three queue slots exist, overwriting the first voice-table byte. S2's 1-up
backs up stale global priority, blocks SFX through its 40-step restore fade,
and deliberately does not restore PSG noise type with `fixBugs=0`. Ring speaker
alternation, gloop suppression, and spindash retrigger state are observable
identity transformations.

S2 native music IDs and current engine API IDs differ. Comparison uses a
profile-provided native ID/content key at the producer boundary; engine-only API
IDs remain diagnostic. It never declares intentionally different numbers equal
without resolving both to the same ROM-backed content identity.

### S3K findings

The canonical Knuckles all-super-emeralds BK2 has SHA-256
`aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc`,
434,417 input rows and 67 manifest segments. Its epoch starts at frame 810 and
the final segment ends at 433,942. The exact BK2 currently lives under
`src/test/resources/traces/s3k/_movies/`, while the run manifest expects a
run-local sibling. Fixture validation must fail until that exact tracked movie
is available through the manifest's canonical path.

S3K has no S1/S2 priority table. The 68K bridge accepts up to two distinct SFX
requests per frame, ignores the slot-0 duplicate case, and lets Z80 source order
determine contention. Continuous SFX retrigger their existing state. The 1-up
path clears queues, saves music tracks/bank/voice/tempo/speed state, suppresses
later requests, and permits new SFX on the driver cycle after restore rather
than throughout the fade. Speed-up may execute extra music services in one
video frame. DPCM and SEGA PCM write `$2A` outside ordinary generic writers;
SEGA PCM pauses normal driver services.

### Observation boundary

S1's driver is 68K-resident and the current verified Lua probe can observe it.
S2 and S3K drivers are Z80-resident. The native GPGX host currently exposes
only M68K execute callbacks, even though the pinned core advertises `Z80 RAM`
and `Z80 BUS`. A runtime proof must establish Z80 execute and write callback
semantics before any S2/S3K capture contract is trusted. If bus writes are not
observable, a reviewed, opcode-verified Z80 PC manifest must cover generic
FM/PSG writers, direct DAC sites, and SEGA PCM sites.

### Explored approaches

1. **Stretch the two S1 schemas.** This is superficially quick but keeps GHZ
   recurrence assumptions, GHZ1 frame bounds, S1 roles, and 68K hook semantics
   in a purported cross-game contract. Rejected.
2. **Replay reference requests into an isolated OpenGGF audio runner.** This is
   useful for source-derived unit scenarios, but a recorded request sidecar can
   conceal gameplay scheduling errors and violates the acceptance requirement
   that OpenGGF emit the route naturally. Rejected as an acceptance lane.
3. **Natural full-run replay with a cross-game envelope and driver profiles.**
   This preserves request causality, supports different driver domains and
   state inventories, and makes gameplay/audio scheduling mismatches visible.
   Selected.

## Architecture decision

### Ownership and boundaries

`CompleteRunAudioProfile` is a tooling-side immutable profile selected by the
validated game/run identity. It owns:

- comparison epoch and fixture identities;
- native request classes and content-key resolution;
- canonical hardware-role inventory;
- global and track-state field inventory;
- priority, queue, 1-up, fade, tempo, and PCM observation semantics; and
- the reference observer implementation/version.

It does not own production behavior. Runtime differences remain in existing
game audio profiles, sequencer configs, loaders, and the smallest driver-owned
policy interface needed to reproduce the ROM.

The production observation seams remain disabled no-ops by default. Capture
installs an immutable append-only observer before the first driver or chip is
constructed. Observers report decisions already made; they never choose an
outcome or mutate playback.

### Capture data flow

```text
Pinned BK2 + ROM
        |
        +--> BizHawk/GPGX + game reference observer
        |       |
        |       +--> raw staging records
        |
        +--> natural OpenGGF visual-run replay
                |
                +--> request + backend decision + driver/chip observers
                        |
                        +--> raw staging records

raw staging -> strict validator -> fixed-row deterministic chunks
            -> atomic create-new capture directory

reference capture x2 --byte identity--+
                                      +--> streaming no-realignment comparator
OpenGGF capture x2 ----byte identity--+
```

The reference capture is never opened by the OpenGGF producer. A static guard
forbids production imports of complete-run schema/readers and a behavioral test
proves the OpenGGF runner accepts only the ROM, BK2/run manifest, output path,
and profile identity.

### Comparison epoch

Power-on SEGA logos, title menus, and demos are deliberately excluded because
the existing complete-run manifests begin at the first gameplay segment and
OpenGGF's headless visual-run harness skips the master title. The comparable
half-open intervals are:

| Game | First row | Exclusive end |
|---|---:|---:|
| S1 | 860 | 225101 |
| S2 | 769 | 259590 |
| S3K | 810 | 434417 |

The first row includes a profile-validated baseline sampled immediately before
its input is consumed. Every later row is retained, whether or not it belongs
to a manifest segment. The terminal record proves the exclusive end and all
record counts.

### Canonical record model

The envelope schema is `complete_run_audio.v1`:

- `metadata`: all identities, comparison interval, profile and field
  inventories, observer/callback proof, chunk policy, and producer kind;
- `baseline`: absolute frame and complete normalized audio state;
- `frame`: absolute BK2 row, segment coordinate if applicable, lag flag,
  ordered raw requests, and ordered driver-service records;
- `service`: global service ordinal, game-local service kind, ordered decisions,
  normalized post-service state, and ordered chip events;
- `lifecycle`: reset, pause, stop-all, save, restore, SEGA-PCM enter/leave, or
  other profile-declared boundary that occurs outside a normal service;
- `terminal`: frame, request, service, decision, YM, PSG, and lifecycle counts
  plus the canonical root digest.

A request has a stable ordinal, native ID, canonical ROM-backed content key,
class, queue source/slot, and submission order. A decision references that
ordinal and records resolved native ID/content key, accepted/rejected status,
reason, priority before/after when applicable, requested roles, and ordered
per-role displaced/final owners.

An owner carries class, native content identity, and originating request
ordinal. It therefore distinguishes same-ID retriggers.

Driver state uses a strict profile-versioned inventory. Pointer-like fields are
normalized as ROM-backed asset key plus relative byte cursor. State includes
inactive-role markers without stale bytes and explicitly represents saved
temporary-music state rather than hiding it in diagnostics.

Chip events are:

- `ym`: monotonically ordered `(port, register, value)` unsigned bytes;
- `psg`: one unsigned byte; or
- a profile-declared lifecycle marker where hardware writes are deliberately
  paused.

Raw callback arguments and PCs are diagnostic fields excluded from semantic
equality but validated strictly.

### Storage and publication

Captures are directories partitioned into deterministic 4,096-BK2-row chunks.
Each chunk is canonical JSONL compressed with a deterministic gzip header and
has both compressed and canonical-uncompressed SHA-256 digests. A canonical
manifest lists chunks, counts, bounds, and a root digest over uncompressed
records.

The producer writes a sibling staging directory, closes it, validates every
record and digest in bounded memory, and atomically renames the directory to a
fresh destination. Unsupported atomic directory publication fails closed.
Validation or producer failure removes only that invocation's staging path and
never replaces an existing capture.

The comparator validates both captures fully, binds a digest to each source,
then streams them again without realignment. It retains at most eight complete
records before and after the first mismatch. A source change between passes is
a capture failure.

### Migration and rollback

The GHZ sound-test and GHZ1 semantic commands remain available as focused
regressions until each corresponding complete-run assertion is green. The new
schema does not silently accept old metadata. Production policy seams default
to current behavior until selected by the existing per-game audio profile.

Each game lands as a separate commit series. If a game implementation is
reverted, shared validated captures and other game profiles remain usable.
No migration writes user configuration or save data.

## Feature design

### Shared APIs

The shared implementation will expose these tooling contracts:

```java
public interface CompleteRunAudioProfile {
    String id();
    CompleteRunFixture fixture();
    List<HardwareRole> hardwareRoles();
    NativeSoundIdentity resolveRequest(RawAudioRequest request);
    StateInventory stateInventory();
}

public interface CompleteRunAudioRecordSink extends AutoCloseable {
    void baseline(AudioBaseline baseline) throws IOException;
    void frame(AudioFrame frame) throws IOException;
    void terminal(AudioTerminal terminal) throws IOException;
}

public record DriverService(
        long ordinal,
        String kind,
        List<AudioDecision> decisions,
        NormalizedAudioState state,
        List<ChipEvent> chipEvents) { }
```

The exact sealed record types, JSON field names, unsigned bounds, role order,
and allowed state inventories are defined once in the shared schema tests.

Production behavior gets only narrowly owned observer/policy seams:

```java
public interface AudioAdmissionObserver {
    void onDecision(AudioAdmissionDecision decision);
}

public interface SmpsRequestAdmissionPolicy {
    AdmissionResult evaluate(SmpsAdmissionContext context);
}
```

The observer is append-only and defaults to `NONE`. The policy is selected by
the existing game audio profile; shared driver code contains no game-name
checks.

### Natural OpenGGF capture

`VisualRunReplayHarness.replayAudio` gains a complete-run mode that:

- uses an explicit row budget derived from the validated BK2, never the 60,000
  default;
- calls one outer presentation and `audio.update()` for every consumed BK2 row;
- rejects cursor jumps and retains lag rows;
- reports transition-gap rows with `segment = null` rather than dropping them;
- installs observers before bootstrap audio construction;
- records baseline immediately before the epoch's first input row; and
- requires coordinator completion plus the exact terminal movie cursor.

The ordinary trace comparator remains active. A gameplay divergence, pause,
softlock, missing transition, or premature movie end fails audio capture. This
is intentional: a naturally wrong route cannot prove naturally correct audio.

### Reference observation

S1 retains the proven memory-callback decoder and opcode-verified fallback,
extended from GHZ-only lifecycle sites to the full S1 sound driver.

S2 and S3K first add a declarative GPGX callback domain to the native headless
host. A capability probe records and cross-checks Z80 PC callbacks with native
YM/PSG writes. The selected callback source and positive proof counts are
pinned in metadata. Fallback manifests are game/revision-specific, verify every
opcode/operand before capture, and include direct DAC/SEGA-PCM sites.

### S1 behavior

- Requests enter the three-slot mailbox in source order and are consumed only
  at the ROM-equivalent service boundary. This must eliminate the known
  same-frame OpenGGF admission at GHZ1 frame 958.
- Admission observation occurs after the backend actually accepts or rejects a
  request. Submission is never relabeled admission.
- `$88` uses one live chip and source-accurate saved driver/music state. All
  active SFX are stopped, subsequent SFX are rejected while the jingle owns the
  driver, and `$87` plus its channel state restore at the ROM boundary.
- `FixBugs=0` FM6/DAC restore behavior is preserved and named in source.
- The real frames 3698, 3699, 3702-through-jingle, and 3910 are mandatory
  semantic and chip-transaction regressions.

### S2 behavior

- Evaluate the global SFX priority before constructing or inserting a
  sequencer. Reject lower priority as a whole; replace equal priority; preserve
  jump's transient signed priority behavior.
- Preserve the shipped fourth queue-transfer overwrite.
- Ordinary music stops SFX through the shipped path.
- The 1-up kills six SFX tracks, saves the correct stale global priority,
  blocks SFX through the 40-step fade, restores DAC/music state, and does not
  restore PSG noise type with `fixBugs=0`.
- Resolve native music content keys, alternating ring speaker IDs, gloop
  suppression, and continuous spindash retriggers before equality.
- Record DAC/FM6 state and every `$2A` data write.

### S3K behavior

- Admit at most two different SFX IDs per 68K frame using the two-slot source
  order. Preserve duplicate-slot behavior without adding a priority table.
- Continuous SFX retrigger/extend their existing identity and state.
- The 1-up clears input/internal queues, saves track/bank/voice/tempo/speed
  state, suppresses later requests, preserves the shipped save-loop bug, and
  permits SFX on the first eligible post-restore service.
- Speed-up records multiple music services in one video frame rather than
  folding them.
- DPCM and SEGA PCM direct writes are captured; normal service pauses during
  SEGA PCM are represented explicitly.
- Use locked-on S&K-half addresses and the exact `fix_sndbugs=0` driver.

### Failure modes

The CLI exit contract is common to all games:

- `0`: duplicate determinism gates and cross-producer parity all match;
- `2`: invalid arguments or fixture identity;
- `3`: valid deterministic captures with a first parity mismatch; and
- `4`: capture, validation, publication, observer-proof, replay, or tooling
  failure.

Partial capture, missing terminal, incomplete chunk publication, callback
fallback without proof, gameplay replay abort, absent contention/1-up evidence,
or a changed source between comparator passes is exit 4.

### Acceptance tests by game

S1 must demonstrate:

- GHZ jump and ring request/admission cadence;
- lower/equal priority rejection/replacement;
- `$88` takeover of all applicable roles during active music and SFX;
- SFX rejection throughout the 1-up;
- exact `$87` restore and fade progression;
- all six special stages and the ending/credits tail; and
- exact ordered state/chip bytes for every service.

S2 must demonstrate:

- whole-request lower-priority rejection and equal-priority replacement;
- the queue-transfer overwrite and music-stops-SFX path;
- a real 1-up save/block/40-step restore;
- ring alternation, gloop suppression, and spindash retrigger;
- all seven special stages and terminal tail; and
- exact Z80 state, non-DAC writes, and DAC `$2A` bytes.

S3K must demonstrate:

- two-slot unique/duplicate SFX behavior and overlapping SFX contention;
- continuous-SFX retrigger;
- 1-up save/suppression/restore and immediate eligible post-restore SFX;
- speed-up frames with multiple services;
- DPCM, SEGA-PCM pause/resume, all fourteen special stages, bonus stages, and
  terminal tail; and
- exact Z80 state and every chip transaction.

## Implementation plan

Execution is split into four test-first plans under
`docs/architecture/plans/audio/`:

1. shared complete-run schema, chunk storage, comparator, authority guards, and
   reference-host observation capability;
2. Sonic 1 complete-run producer and source-accurate mailbox/1-up behavior;
3. Sonic 2 complete-run producer and global-priority/temporary-music behavior;
4. Sonic 3K complete-run producer and two-slot/tempo/PCM behavior.

Each plan ends with duplicate real captures, cross-producer comparison, compact
validation evidence, and an independent review gate. The S1 plan executes
first; S2 and S3K may begin only after the shared Z80 capability gate is green.

## Human review and integration

Completion produces an integration report and end-to-end review under the
matching architecture validation directory. Human listening should cover at
least normal music, FM and PSG SFX, 1-up takeover/restore, speed shoes, and
high-contention scenes in all three games. The branch remains unmerged until a
human explicitly approves integration.
