# Production-owned sound-driver validation design

Date: 2026-08-31  
Status: proposed for review  
Scope: S2/S3K production-owned validation, with S1 as precedent and a regression gate

## Decision

Authenticated OpenGGF audio evidence must be produced by OpenGGF running from the
identified user-supplied ROM and BK2 controller input. OpenGGF must naturally submit its
own music and sound-effect requests and advance the production audio boundary. Reference
driver state, requests, mailbox values, trace physics, auxiliary state, and fixture
coordinates remain comparison-only.

This replaces the previously contemplated request-replay approach. It does not authorize a
new TraceChaser M68K execution or request hook. Existing fixture-assisted S2 and S3K tools
remain useful diagnostics, but cannot certify an authenticated `MATCH`.

## Six-stage roadmap

The work remains one cross-game roadmap, not merely an attempt to move two comparator
frontiers.

| Stage | Objective | Entry evidence | Exit gate |
|---|---|---|---|
| 1 | Reverse-engineer each shipped per-game driver. | Canonical disassembly, ROM identity, existing driver research. | Relevant entry points, RAM, tables, control flow, and shipped bug-compatible paths are cited. `FixBugs`, `fixBugs`, or `fix_sndbugs` paths follow the shipped-ROM setting, including zero/off behavior. |
| 2 | Document routines, state, cadence, and behavior. | Stage 1 findings. | A current behavior specification and shared comparison vocabulary distinguish request, admission, service, channel ownership, chip write, and audible output. Stale claims are corrected or explicitly marked historical. |
| 3 | Compare the ROM model with OpenGGF. | Current specification plus inspected engine code and tests. | Each claim is classified by evidence strength: code inspection, source-owned synthetic contract, fixture-assisted projection, or authenticated production comparison. |
| 4 | Catalogue the gaps. | Stage 3 findings with their evidence strength. | A current gap ledger names the source owner, OpenGGF owner, impact, evidence, dependencies, and next proof for each gap. |
| 5 | Implement source-owned gaps. | A Stage 4 gap with a primary-source owner. | Implementations model ROM state and routines without trace-, route-, zone-, game-name-, or frame-specific exceptions. Focused tests cite the owning source behavior. |
| 6 | Validate and publish. | Independent reference evidence plus production-owned OpenGGF behavior. | Exact identities and bounds validate; comparison stops at the first divergence and never realigns; focused, ordinary, and guard suites introduce no regression; the frontier log and durable evidence are current; human listening is completed by a human where required. |

The runner and observer work below enables comparisons in stage 3 and validation in stage 6.
It does not by itself complete
the earlier reverse-engineering, gap, or implementation objectives.

## Evidence terminology

- `MATCH` means a bounded, authenticated comparison in which the identified ROM/reference
  producer and OpenGGF independently receive the same identified controller input, OpenGGF
  naturally produces its requests, all records in the declared window compare equal, and
  no required observation is missing.
- `fixture-assisted projection` means a diagnostic whose OpenGGF-side behavior is partly
  selected or populated by reference data. It may identify a useful first divergence but
  cannot pass an acceptance gate.
- `unit/synthetic contract` means source-owned stimulus constructed to prove a specific
  routine or state-machine behavior. It is valid Stage 3/4 evidence but is not an
  end-to-end production comparison.
- `unresolved` means the required authority, observation, startup route, or implementation
  is absent. Missing data never implies equality.

Oracle execution, unit tests, and human listening prove different things and are never
substituted for one another. Agents may prepare the listening queue but cannot certify its
human-owned result.

## Authority boundary

### Permitted behavior inputs

The production OpenGGF capture may receive only:

1. an exact ROM selected through normal configuration and verified against the repository's
   pinned identity;
2. normal engine initialization and source-owned configuration;
3. the current and previous controller rows from an identified BK2 movie; and
4. ordinary user-independent capture bounds that decide when observation starts and stops,
   never what the engine does.

All startup values must be attributable to one of those owners. The capture may record the
BK2 cursor, game mode, requests, admissions, service events, ownership, and chip writes as
diagnostics or comparison fields.

### Forbidden behavior inputs

The production capture must not consume `TraceData`, `TraceEntry`, `TraceRunManifest`,
`TraceSessionLauncher`, `TraceReplayDrive`, `TraceReplaySessionBootstrap`, physics rows,
auxiliary rows, `hardware_timing.jsonl`, reference audio rows, reference sound IDs, queue
vectors, mailbox bytes, speed-up coordinates, or output-inferred requests. It must not jump
the input cursor, hydrate gameplay state, synchronize state each frame, or infer a request
from its later consequences.

The existing hardware-timing exception remains exactly as documented elsewhere: it may
delay readiness of matching production-submitted work or admit an already-existing lag
loop. It cannot carry a sound ID or decide what work exists. This design does not require
that exception, so this authenticated producer accepts no hardware-timing input. Guards
confine that decision to this producer and reject gameplay-bearing timing use; they do not
ban the repository's separate, policy-permitted timing port.

`TraceReplaySessionBootstrap` is therefore excluded, rather than treated as a permitted
pre-window reconstruction. Its position, collision, random, counter, and alignment values
are trace-derived gameplay state and cannot authenticate natural startup for this purpose.

## Production capture architecture

### `ProductionBk2AudioRunner`

A test/tooling-owned `ProductionBk2AudioRunner` will drive the genuine all-mode
`GameLoop` from startup without trace replay infrastructure. It will use existing narrow
production seams where possible:

- `Bk2MovieLoader` parses the BK2;
- `RecordedInputSnapshots.fromBk2(current, previous)` creates the logical controller
  state;
- `InputHandler.setLogicalOverride(...)` publishes that state before the step; and
- the outer-frame cadence is one `GameLoop.step()` followed by the same one outer-audio
  callback used by `Engine.display()`: `Engine.presentOuterAudioFrame(loop, false, false)`.

The runner advances only the BK2 cursor. Capture bounds control retention and termination,
not engine behavior. Fast-forward, trace sessions, per-mode cursor substitution, and direct
SMPS driver advancement are forbidden and asserted absent.

The implementation must extract the smallest package/test-visible headless
production-startup seam needed to exercise the same ROM selection, game initialization,
game-mode routing, and audio ownership as the normal engine. It should reuse the ownership
currently expressed by `Engine.initializeGame()`, `enterConfiguredStartupMode()`, and the
normal starting-level path while accepting an already configured headless graphics/audio
service set. `HeadlessGameBoot`, `HeadlessTestFixture`, and
`UserRecordingSmokeHarness` may inform tests, but none is sufficient as the authenticated
runner because each bypasses master/title routing or uses trace/fresh-level setup. The new
seam separates GLFW/window initialization from production game initialization; it must not
duplicate a second startup state machine or call a direct level-load convenience helper from
the runner.

Audio remains enabled and uses the production `AudioManager` and presentation producer. A
headless/no-device sink is supplied through the established service boundary; the runner
must not disable audio or silently initialize the live LWJGL/OpenAL backend in CI.
`Engine.presentOuterAudioFrame(...)` is the cadence authority. The runner must not combine a
helper that already presents audio, such as a `HeadlessTestRunner.stepFrame*()` path, with a
second presentation call.

### BK2 input ownership

Input is owned by a runner-local `Bk2InputCursor`. It exposes current and previous rows,
publishes their logical snapshot before `GameLoop.step()`, advances exactly once after
`Engine.presentOuterAudioFrame(...)`, fails if the movie is exhausted, and never seeks away
from its initial frame-zero position. This preserves held and pressed edges through normal
`InputHandler` semantics. The same input cursor must remain valid across legal disclaimer,
master title, per-game title/data-select, level, special-stage, and transition modes.
`PlaybackDebugManager` is not the cursor authority because its driving scope is currently
limited to level and bonus-stage modes. Programmatic calls such as master-title
`selectEntry(...)`, direct zone loads, forced game-mode changes, and skipped input rows may
be used only in separately labelled feasibility diagnostics; they cannot certify the
current power-on route.

### Observation

Observers are attached before frame zero or before any game-owned request can be submitted:

- `AudioRequestObserver` records raw OpenGGF caller requests;
- admission/contention observers record resolution and hardware-role arbitration;
- driver-service and chip-write observers record sequencer and output consequences.

Observers are append-only and disabled by default. They are never consulted by production
decisions, never stored in rewind state, and are detached in `finally`. A scoped observer
lease owns installation and cleanup, refuses conflicting ownership, and exposes enough
diagnostic state for tests to prove cleanup on success and failure. The runner fails if the
lease cannot acquire exclusive observer ownership or cleanup leaves an observer installed.

Request, admission, service, ownership, state, and chip writes remain distinct event kinds.
Later output cannot be used to reconstruct a missing earlier request.

### Independent producer flow

The reference producer and the OpenGGF producer run separately and complete their captures
before comparison. No reference path, stream, parsed row, callback, or request collection is
passed to the OpenGGF producer. The OpenGGF capture is regenerated under the current
worktree's `target/` tree or an explicit external output directory; Maven output is never
redirected to a shared durable root.

The comparator validates both streams completely, then compares from their declared common
boundary. It reports the first missing, extra, reordered, or different semantic event and
does not realign after divergence. Frame, tick, service, and cursor coordinates describe
where an event was observed; they cannot trigger it.

An identity-only `ProductionAudioRunManifest` replaces trace run manifests for this lane.
It may name the profile, ROM and BK2 paths and hashes, producer identity, capture bounds,
schema version, and expected comparison range. It may not carry segment bootstrap state,
physics/auxiliary paths, trace replay descriptors, request values, hardware timing, or any
coordinate used to choose engine behavior. A trusted launcher may validate this manifest;
the OpenGGF producer receives only the already validated profile, ROM, BK2, bounds, and
output path.

### Event contract

The game-neutral timeline schema records four ordered layers. Every request carries a
producer-owned request ordinal, request class, raw sound ID, source owner, BK2 cursor, and
outer-frame ordinal. Admission records correlate a request ordinal and carry their own
ordinal, resolved identity, requested/acquired roles, priority/arbitration outcome, and
displaced/final owner. Service records carry a service ordinal and kind, outer-frame
ancestry, normalized driver/track state, and final ownership vector. Chip records carry a
service ordinal plus ordered YM2612 or PSG writes. Per-game source PCs, mailbox addresses,
queue slots, and native tick ordinals are diagnostics owned by typed profiles, not required
behavior inputs.

Result kinds are closed: `MATCH`, `MISMATCH`, `REFERENCE_LIMITATION`,
`AUTHORITY_VIOLATION`, `CAPTURE_UNAVAILABLE`, and `INVALID_INPUT`. A missing request layer
is `REFERENCE_LIMITATION` or `CAPTURE_UNAVAILABLE`, never an inferred `MATCH`.

## Feasibility gates

Implementation proceeds in fail-closed gates. A failed gate produces an explicit product or
authority gap rather than a weaker acceptance claim.

1. **Headless production initialization.** The exact ROM can enter and step the normal
   startup game mode without GLFW presentation and without trace bootstrap, direct level
   load, or fixture state.
2. **Input cardinality and edges.** One BK2 cursor row is applied per production outer
   frame. Held/pressed semantics, cursor advancement, presentation, and
   `AudioManager.update()` each have asserted cardinality, including non-gameplay modes.
   Trace fast-forward and user-recording pump paths are disabled and asserted absent.
3. **Natural audio observation.** Observers attached before the first request record
   OpenGGF-owned requests and their later consequences without changing behavior.
4. **Route reachability.** The identified movie naturally reaches the intended comparable
   game mode and window. An unsupported transition, special-stage cadence, title path, or
   initialization mode is recorded as a product gap.
5. **Authenticated comparison.** Only after gates 1-4 pass may the capture be compared and
   described as `MATCH` or as a first divergence.

## Game-specific application

### Sonic 1

The existing GHZ music result is a bounded driver-core comparison: OpenGGF starts the
source-owned GHZ song but uses reference metadata to define the cadence and terminal bound.
The sound-test SFX result is fixture-assisted because `S1OpenGgfSfxAudioCapture` replays the
reference `dispatches` sequence. Neither is a production-owned `MATCH` under this design's
new terminology. The S1 gameplay timeline provides a useful observer/schema/comparator
split, but its `VisualRunReplayHarness` and trace-derived level bootstrap are not the startup
authority for this stricter cross-game path. Existing driver-core regression tests remain
green; any future production-owned S1 claim must be rerun through the new authority boundary
instead of exempting legacy hydration.

### Sonic 2

The first target remains the EHZ window whose fixture-assisted projection currently reaches
ticks 0-209 and diverges at tick 210. The runner must attempt the identified movie from its
natural startup and traverse every intervening mode, including the special stage, until the
EHZ window. The speed-up transition must arise from OpenGGF gameplay/runtime state.
`SPEED_UP_ROW` may identify a comparison mismatch but cannot schedule that transition.
The movie is `sonic-2-sonic-tails-complete-emeralds.bk2`, SHA-256
`e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5`; the current reference
window is `[10150,10900)` with the diagnostic first divergence at movie row 10412.

Before the long-route capture is attempted, a dedicated feasibility test must prove that
pure BK2 logical input can enter, run, and exit the S2 special stage through normal
`GameLoop` cadence without `TraceSessionLauncher.currentSpecialStagePassPacing()` or any
row-derived pacing. Failure keeps route reachability `unresolved`; it does not authorize
reuse of trace-specific special-stage pacing.

`S2OracleEngineCapture.DriverRequest` remains a source-owned synthetic seam. Static and
runtime guards must prevent reference fixtures or trace rows from being connected to it.
`S2AudioOracleComparator.compare(...)`, its fixture-derived `speedUpTick`, and
`S2OracleEngineCapture.capture(..., speedUpTick, ...)` are fenced diagnostic entry points.
The production comparator rejects their v1 schema and their result/logging path reports only
`fixture-assisted projection`, never authenticated `MATCH`.

If production startup cannot reach the current EHZ boundary, the result is `unresolved` and
the route blocker becomes implementation work. A smaller fresh-EHZ source-owned scenario
may test driver behavior, but it is labelled `unit/synthetic contract` and cannot be
relabeled as authentication of the existing movie window.

### Sonic 3&K

The first target remains the power-on fixture through source frame 242 / driver service 128.
The OpenGGF runner must use the same identified power-on movie and naturally traverse boot,
SEGA/title/data selection, and AIZ routing. Existing complete-run AIZ segments begin after
this boundary and do not establish the same input identity or startup route.
The movie is `s3k-complete-sonic-tails.bk2`, SHA-256
`82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf`; the current reference
fixture covers source frames `[0,5400)`.

`S3kOpenGgfAudioCapture` currently dispatches `referenceTick.mailbox()` and copies reference
mailbox state into its result. That path is explicitly a `fixture-assisted projection`. It
must be fenced from authenticated entry points and cannot emit `MATCH`. In the production
path, mailbox/request identity must be OpenGGF-produced and independently observed. A
missing policy-compliant pre-consumption M68K-to-Z80 observation remains `unresolved`; chip
writes or output bursts cannot stand in for it.

Accordingly, service 128 / source frame 242 is an investigation target, not currently a
`MATCH`-eligible endpoint. Until an already policy-compliant reference observation exposes
the pre-consumption request, the authority-correct outcome at that boundary is
`REFERENCE_LIMITATION`. A complete authenticated comparison may stop through service 127;
it must not silently extend across service 128.

If the normal OpenGGF product cannot reproduce the power-on/title route, that is an upstream
product gap. An AIZ fresh-level scenario may provide source-owned unit evidence but cannot
authenticate service 128 of the existing power-on fixture.

## TraceChaser boundary

No new general M68K execute, memory-write, sound-request, or managed native callback is part
of this design. TraceChaser's two established hardware-timing observers retain their narrow
scope. A one-off Lua script may diagnose a source boundary and must be discarded after use;
its result cannot publish a canonical request fixture or authorize replay into OpenGGF.

Consequently, a reference-side pre-consumption request observation remains a declared
limitation wherever the current approved producer lacks one. The design prefers an honest
unresolved boundary over output-derived request inference or a policy exception hidden in
tooling.

## Record and publication contract

Each capture begins with metadata naming schema version, producer, ROM identity, BK2
identity, configuration relevant to startup, capture bounds, event inventory, and command
provenance. Records use unsigned normalized values, strict allowed fields, ordered arrays,
monotonic ordinals, and terminal completeness metadata. Identity or schema mismatch makes
the run invalid before comparison.

OpenGGF capture streams are regenerable outputs, not new gameplay inputs. Durable validation
evidence commits only the small design/validation report and identity/hash manifest under the
matching `docs/architecture/validation/audio/` or research location. Raw durable captures
belong in an explicit external task archive named by the report; transient Maven output
stays below the worktree's `target/`. No
uncompressed trace payload is committed under trace resources. Publications use validated,
atomic create-new behavior so a failed run cannot replace prior evidence.

`docs/status/audio-frontier-log.md` must be updated whenever a frontier moves, a prior pass
regresses, or a complete sweep selects a new target. It records the command, commit and
worktree context, result category, comparison count, error count, and first divergent
frame/service/field. Existing S2 0-209 and S3K 0-127 wording must be corrected to
`fixture-assisted projection` until independent evidence exists.
This audio-specific record is additional to, not a replacement for, any general
`docs/status/trace-frontier-log.md` obligation triggered by a trace-replay sweep or change.

## Failure behavior

The runner or comparator fails closed on:

- ROM or BK2 identity mismatch;
- unavailable or bypassed production startup;
- any trace/bootstrap/reference dependency on the engine producer;
- an input cursor jump, missing row, duplicate consumption, or incorrect press edge;
- fast-forward or an unexpected mode-specific input owner;
- zero or multiple presentation/audio advances for an outer row;
- observer replacement, mutation, or incomplete cleanup;
- a missing request observation needed by the declared comparison;
- malformed, duplicate, non-monotonic, incomplete, or out-of-window records; or
- unequal record cardinality or a first semantic divergence.

No failure mode falls back to fixture injection, reconstructed requests, event realignment,
or a weaker claim under the same result label.

## Verification design

The implementation must add or extend tests that prove:

1. BK2 conversion and `InputHandler` overrides preserve held/pressed edges across menu and
   gameplay modes.
2. The headless startup seam uses normal ROM selection, initialization, and `GameLoop` mode
   routing without trace bootstrap or direct level hydration.
3. Every BK2 cursor row owns exactly one production outer frame: one step, one outer-audio
   presentation/update, and one cursor advance, including legal/master/title/data-select
   modes. This is an outer-frame contract, not a claim that every mode advances one gameplay
   tick. Trace fast-forward and user-recording pump paths are disabled; any pause retention
   is source-cited and represented in the contract rather than fitted.
4. Audio observers are behaviorally inert, installed before first request, and detached on
   both success and failure.
5. A static authority guard rejects `com.openggf.trace.*`, `TraceSessionLauncher`,
   `TraceReplaySessionBootstrap`, `TraceReplayDrive`, `TraceData`, `TraceRunManifest`,
   reference readers, any hardware-timing input to this producer,
   `S2OracleEngineCapture.DriverRequest`,
   `SPEED_UP_ROW`, `S3kOpenGgfAudioCapture`, and `referenceTick.mailbox()` dependencies from
   authenticated producers. Existing diagnostic tools may retain their current dependencies
   only behind result types and labels that cannot emit authenticated `MATCH`.
6. S2 synthetic `DriverRequest` and S3K fixture-mailbox entry points cannot be called by an
   authenticated runner and cannot publish a `MATCH` result.
7. The comparator rejects alignment shifts and reports the exact first request, admission,
   service, ownership, state, or write divergence.
8. ROM-gated feasibility tests prove the natural S2 and S3K routes separately before their
   full comparisons are enabled.
9. Existing S1 bounded driver-core/fixture-assisted results and focused audio/parity suites
   do not regress, without relabelling them as production-owned `MATCH`.
10. Delivery runs focused tests, the ordinary suite, and the fresh-JVM `-Pguards` suite on
    JDK 21 with all three absolute ROM properties and verified skip counts.

## Remaining work beyond the first S2/S3K frontiers

After authenticated production capture is established, stages 1-4 and 6 continue across
source-owned scenarios for queue and priority behavior, pause bursts, 1-up save/restore,
fade and speed paths, SFX takeover/release, S3K modulation envelopes and every-frame
frequency behavior, ROM-read tables, and DAC/PCM timing. The gap ledger and behavior specs
must be refreshed as each claim changes status. Analog mix and subjective audibility remain
separate from driver-state equality and stay in the human listening queue.

## Rejected alternatives

- **Replay reference requests, mailbox bytes, or speed coordinates into OpenGGF.** This
  would make comparison data decide what happens and could only produce a fixture-assisted
  projection.
- **Add a new TraceChaser M68K request hook.** Current TraceChaser policy forbids the needed
  general callback, and changing that policy is outside this design.
- **Infer hidden requests from service bursts or chip writes.** Consequences do not prove
  request identity or timing.
- **Use trace-hydrated level replay as natural startup.** It can validate behavior after a
  declared reconstruction boundary, but cannot authenticate the power-on/current-window
  route selected here.
- **Replace end-to-end evidence with fresh-level synthetic scenarios.** Those scenarios are
  valuable source-owned unit evidence, but prove a different boundary.

## Explicitly unresolved until implemented and observed

- a clean, headless frame-zero production startup shared with the normal engine;
- natural S3K production request capture through source frame 242 / service 128;
- a policy-compliant reference-side pre-consumption M68K-to-Z80 request observation where
  the current producer lacks one;
- the S2 speed-up transition independently produced by OpenGGF on the identified route;
- the S3K mailbox/request identity independently produced by OpenGGF;
- S2/S3K complete-run producer availability;
- authenticated pause, 1-up, fade, full DAC/PCM, table, and modulation coverage; and
- the remaining human listening checklist.

These are design gates, not promises already satisfied by existing green tests.

## Expected ownership split

The implementation plan should preserve one game-neutral framework and typed profiles:

- `src/main/java/com/openggf/tools/audio/production/ProductionAudioTimeline.java`
- `src/main/java/com/openggf/tools/audio/production/ProductionAudioTimelineJsonl.java`
- `src/main/java/com/openggf/tools/audio/production/ProductionAudioComparator.java`
- `src/main/java/com/openggf/tools/audio/production/ProductionAudioProfile.java`
- `src/main/java/com/openggf/tools/audio/production/ProductionAudioRunManifest.java`
- `src/test/java/com/openggf/tools/audio/production/ProductionBk2AudioRunner.java`
- `src/test/java/com/openggf/tools/audio/production/TestProductionAudioAuthorityGuard.java`
- per-game S2/S3K capture profiles below the same test tooling package; and
- thin `tools/audio/run_s2_production_audio_validation.sh` and S3K equivalents.

Shared runner, schema, comparator, and manifest code contain no game names, zone names,
route literals, sound IDs, or frame literals. Those facts belong in typed per-game profiles
and authenticated manifests. Production gameplay packages do not import the tooling schema.
