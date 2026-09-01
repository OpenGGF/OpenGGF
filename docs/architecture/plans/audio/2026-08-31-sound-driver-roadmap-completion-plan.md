# Sound-driver roadmap completion implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` to implement this plan task-by-task. Every behavior
> change follows test-first development and receives an independent task review.

**Goal:** Connect TraceChaser's native S2/S3K reference producers to OpenGGF's existing
complete-run consumer, add an independent production-owned OpenGGF replay producer, and then
advance sound-driver parity by first divergence without weakening the proven S1 lanes.

**Architecture:** TraceChaser/BizHawk remains the sole emulator-facing reference producer.
OpenGGF validates and projects that raw evidence, independently drives itself from the same
verified ROM and BK2 input, compares only explicitly declared semantic layers, and reports
missing reference authority as `REFERENCE_LIMITATION`. Shared code owns identity, capture,
comparison, and production replay mechanics; typed game profiles own ROM/movie identities,
state normalization, raw projection, and source-specific behavior.

**Tech stack:** Java 21, Maven/JUnit 5, TraceChaser C# native headless harness, BizHawk 2.11
GPGX, Lua 5.4 guards, gzip JSONL canonical capture store.

**Spec:**
`docs/architecture/designs/audio/2026-08-31-sound-driver-production-owned-validation-design.md`

## Global constraints

- Work only in isolated worktrees; never switch the main OpenGGF workspace branch.
- Accuracy comes from the shipped ROM/disassembly path. Preserve `FixBugs = 0`, `fixBugs = 0`,
  and `fix_sndbugs = 0` behavior and cite the owning source routine in behavior fixes.
- TraceChaser output is comparison-only. It may not hydrate, schedule, or select OpenGGF
  gameplay/audio behavior.
- The OpenGGF producer may consume only the exact ROM, current/previous BK2 controller rows,
  normal startup/configuration, strict manifest identity/segment inventory, and capture bounds.
- Do not consume `TraceData`, `TraceSessionLauncher`, `TraceReplaySessionBootstrap`,
  `TraceReplayDrive`, physics/auxiliary rows, hardware timing, reference requests/mailboxes,
  fixture coordinates, or direct level-load helpers from the authenticated OpenGGF producer.
- Reuse TraceChaser's reviewed buffered GPGX audio observer and closed service graph. Do not add
  caller-configurable execute/memory hooks or infer requests from state, service, or chip output.
- S2 uses profile `s2_rev01_complete_emeralds.v1` and comparison interval `[769,259590)`.
- S3K uses profile `s3k_locked_on_knuckles_superemeralds.v1` and comparison interval
  `[810,434417)`. The Sonic/Tails 5,400-row diagnostic remains a separate identity.
- A missing request/admission observation is `REFERENCE_LIMITATION`, never equal empty arrays,
  inferred parity, or `MATCH`.
- Comparison stops at the first divergence and never realigns.
- The S1 GHZ 14,690-tick and S1 sound-test 1,967-tick lanes are regression gates; their current
  evidence labels remain bounded driver-core/fixture-assisted rather than production-owned.
- Use JDK 21, `LUA_BIN=lua5.4`, absolute verified ROM paths, and check ROM-gated skip counts.
- Maven output stays in each worktree's `target/`. Raw captures use an explicit external task
  archive and are never committed uncompressed under trace resources.
- Human listening remains human-owned; agents only prepare the queue.
- Keep `docs/status/audio-frontier-log.md` current whenever a frontier moves or regresses.
- Follow repository trailers and documentation obligations; never use `--no-verify`.

---

## Requirements

### Goals

1. Preserve a single six-stage roadmap: reverse-engineer, specify, compare, catalogue,
   implement, validate/publish.
2. Make semantic coverage explicit in every complete-run profile and capture.
3. Expose stable TraceChaser complete-audio commands for the existing S2/S3K native runners.
4. Project validated raw reference services/state/chip evidence into OpenGGF's canonical store
   without inventing request/admission records.
5. Drive OpenGGF naturally from power-on using one BK2 row and one outer-audio presentation per
   outer frame.
6. Bind the reserved S2/S3K fixed producer classes only after identities and capability proofs
   are pinned.
7. Execute authenticated comparisons, then resolve each first divergence at its ROM-owned
   implementation boundary.

### Non-goals

- A second emulator/reference framework inside OpenGGF.
- Replaying fixture requests, S2 speed coordinates, or S3K mailbox bytes into the engine.
- Claiming request parity from post-consumption Z80 state or audible/chip consequences.
- Replacing complete-run evidence with fresh-level scenarios.
- Treating analog mix or subjective quality as driver-state equality.

### Acceptance criteria

- Incompatible layer inventories fail before semantic comparison.
- Equal compared layers plus any unavailable layer return `REFERENCE_LIMITATION`; only an
  all-compared equal capture returns `MATCH`.
- S2/S3K raw streams without approved request events emit zero canonical requests/decisions.
- Stable TraceChaser CLI invocations publish create-new raw files only after complete capture.
- The production runner proves row/step/presentation/cursor cardinality and observer cleanup.
- Authority guards reject every forbidden dependency named in the spec.
- Fixed registry bindings load in a fresh CLI JVM and carry pinned runtime/observer/capability
  identities.
- Complete comparisons report a first semantic divergence or a layer-scoped limitation; they do
  not silently skip to the old EHZ or frame-242 diagnostic frontier.
- Focused, ordinary, and fresh-JVM guard suites introduce no new regression.

### Risks and conservative rulings

- The canonical store has no installed complete-run captures. The prerequisite therefore adopts
  `complete_run_audio.v2` and rejects v1 rather than accepting metadata without explicit producer
  observation and shared comparison inventories.
- A TraceChaser request-observer extension is not part of the initial integration. Current raw
  authority remains service/state/chip-only.
- If natural power-on replay cannot traverse a mode or special stage, the run reports an explicit
  product/route feasibility gap. It does not use trace pacing or direct loading.

## Exploration synthesis

- `CompleteRunAudioTrace.Metadata` lacks layer identity, while
  `CompleteRunAudioComparator` unconditionally compares requests/decisions and their terminal
  counts. This must change before any partial-authority reference can publish.
- `CompleteRunAudioProducerRegistry` already reserves the four correct S2/S3K producer class
  names. Both profiles intentionally expose `UnavailableProducerBinding`.
- `S2CompleteRunReferenceRawAdapter` and `S3kCompleteRunReferenceRawAdapter` already provide
  strict transactional scans; game-owned decoders and normalizers already exist.
- TraceChaser's `S2CompleteAudioCaptureRunner` and `S3kCompleteAudioCaptureRunner` already own
  verified native capture, but the public program has no stable complete-audio raw command.
- OpenGGF already has the required input and observation primitives:
  `Bk2MovieLoader`, `RecordedInputSnapshots`, `InputHandler.setLogicalOverride`,
  `AudioRequestObserver`, `AudioAdmissionObserver`, `SmpsDriverServiceObserver`, and
  `ChipWriteObserver`.
- Existing headless helpers are unsuitable because they direct-load levels or use trace
  bootstrap. `Engine.initializeGame()` and `Engine.presentOuterAudioFrame(...)` are the reusable
  production owners; they need the smallest tooling-visible headless seam.

## Architecture decision

The data flow is:

```text
verified ROM + BK2 ──> TraceChaser/BizHawk ──> validated raw reference
        │                                           │
        └──────────> OpenGGF production replay      │
                             │                      │
                             └──> canonical engine  │
                                                    v
                          OpenGGF canonical projection/comparator
                                      │
                         MATCH / first divergence /
                           REFERENCE_LIMITATION
```

TraceChaser owns capture correctness and raw publication. OpenGGF owns canonical publication,
independent execution, comparison, and acceptance. Both producers share identity inputs, never
runtime state. Rollback is removal of producer bindings: the existing unavailable profiles and
diagnostic tools remain usable without changing gameplay behavior.

## Feature design

- `ComparisonLayerInventory` declares `ROW_LAG`, `REQUESTS`, `DECISIONS`, `SERVICES`, `STATE`,
  `OWNERSHIP`, `LIFECYCLE`, `FRAME_CHIP_EVENTS`, `BOUNDARY_CHIP_STATE`, and `CUTOFF_FRONTIER`
  in canonical enum order. Each producer separately pins the same ten-layer observed/unobserved
  inventory.
- Each comparison layer is `COMPARED` with no reason or `UNAVAILABLE` with a nonblank reason;
  `COMPARED` requires both producer inventories observed, but observed evidence may deliberately
  remain unavailable for equality.
- Decisions cannot be compared when requests are unavailable.
- A producer may claim ownership observed only when decision and lifecycle carriers are also
  observed; ownership equality uses their ordered flattened owner-transition projection rather
  than unavailable decision/lifecycle record grouping.
- Both inventories are mandatory and frozen into profile/capture metadata and source identity;
  no constructor or profile default may synthesize all-observed/all-compared claims.
- Comparator skips unavailable fields/counts but returns `REFERENCE_LIMITATION`, not `MATCH`,
  after all compared layers agree.
- Nullable frame/boundary fields mean unobserved; non-null empty means observed-empty. Frame owns
  lag, requests, decisions, services, post-row state, and frame chip events. `DriverService` owns
  only service semantics/lifetime/ancestry. Admission decisions remain frame-owned because
  `AudioAdmissionObserver` fires in `AbstractSmpsAudioBackend.playSfxSmps`/`evaluateAdmission`
  before and independently of `SmpsDriver` service callbacks.
- Baseline/cutoff service topology, boundary chip state/latches, normalized state, and owners are
  independently nullable within mandatory envelopes. Buffered-native authentication is validated
  whenever declared, even when its corresponding semantic layer is unavailable. Native topology
  and global chip/latch projections prove each present semantic boundary component independently.
- `Bk2InputCursor` indexes `Bk2Movie.getFrames()` directly, publishes current/previous logical
  input before `GameLoop.step()`, advances after audio presentation, and fails on exhaustion.
- A scoped observer lease installs append-only observers before startup and removes them in
  `finally`, refusing conflicting ownership.
- Game-owned reference projectors decode only approved raw events and state. Native TraceChaser
  service kinds are not assumed equivalent to OpenGGF's SMPS service callbacks; exact per-game
  projection tests must prove a shared semantic layer before its status becomes `COMPARED`.
  Missing semantic owners remain unavailable.
- Fixed producer classes validate producer kind/profile/paths, invoke their owned runner, write a
  private transaction, and expose no replacement/registration seam.

---

## Implementation plan

### Task 1: Layer-scoped capture identity and comparison

**Files:**

- Modify: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioTrace.java`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioProfile.java`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioProfiles.java`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioJson.java`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioComparator.java`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioReport.java`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/s2/S2CompleteRunAudioProfile.java`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/s3k/S3kCompleteRunAudioProfile.java`
- Test: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioTrace.java`
- Test: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioCaptureStore.java`
- Test: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioComparator.java`
- Test: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioCli.java`

**Interfaces:**

- Produces: canonical `complete_run_audio.v2`; `ComparisonLayer`, `ComparisonLayerStatus`,
  `ComparisonLayerClaim`, `ComparisonLayerInventory`, `ProducerObservationClaim`,
  `ProducerObservationInventory`, both mandatory profile inventories, strict nullable evidence
  shapes, and `CompleteRunAudioReport.Kind.REFERENCE_LIMITATION`.
- Consumes: existing strict metadata/profile/store validation.

- [x] Write RED constructor tests for duplicate, missing, reordered, and invalid dependent claims;
  add a metadata round-trip test with literal canonical JSON.
- [x] Run
  `mvn -Dmse=off "-Dtest=TestCompleteRunAudioTrace,TestCompleteRunAudioCaptureStore" test -B`
  and confirm failures are caused by the absent inventory contract.
- [x] Add immutable shared-comparison and producer-observation inventory types. Initially compare
  only `FRAME_CHIP_EVENTS` for S2/S3K; every other layer is an explicit limitation for both
  producers. S1's uninstalled complete-run producers claim no observed layers, while its legacy
  music/SFX oracle gates remain unchanged.
- [x] Freeze both inventories in `CompleteRunAudioProfiles.FrozenProfile`, append the selected
  producer inventory to strict v2 metadata JSON,
  and reject metadata whose inventory differs from its registered profile.
- [x] Write RED comparator tests proving engine requests do not mismatch an unavailable reference
  request layer, equal empty arrays cannot produce `MATCH`, incompatible inventories fail, and a
  real compared-layer mismatch still wins over the limitation result.
- [x] Run
  `mvn -Dmse=off "-Dtest=TestCompleteRunAudioComparator,TestCompleteRunAudioCli" test -B`
  and confirm the missing behavior fails.
- [x] Make cross-producer frame/boundary comparisons conditional on `COMPARED`, while retaining
  producer-local shape/profile/native authentication for observed-but-not-compared evidence. Return
  `REFERENCE_LIMITATION` only when every compared layer is equal; preserve exact first-divergence
  behavior otherwise. Emit inventory through existing source metadata in JSON/text reports.
- [x] Run all focused classes, existing S1/S2/S3K fixture profile tests, legacy S1 oracle gates,
  the ordinary relevant suite, and fresh-JVM guards.
- [x] Commit with repository trailers as `feat(audio): declare complete-run comparison layers`.

### Task 2: Stable TraceChaser complete-audio producer command

**Files (TraceChaser repository/worktree):**

- Modify: `bizhawk-headless/src/Program.cs`
- Modify: `bizhawk-headless/src/Recording/S2CompleteAudioCaptureRunner.cs` only if a public command
  wrapper cannot call the existing pinned method without widening internal behavior.
- Modify: `bizhawk-headless/src/Recording/S3kCompleteAudioCaptureRunner.cs` under the same rule.
- Modify: `bizhawk-headless/tests/TraceCliTests.cs`
- Create: `bizhawk-headless/run-complete-audio.sh`
- Test: `bizhawk-headless/tests/S2CompleteAudioCaptureRunnerTests.cs`
- Test: `bizhawk-headless/tests/S3kCompleteAudioCaptureRunnerTests.cs`
- Modify: the TraceChaser capture guide owning command-line publication.
- Modify in OpenGGF after the TraceChaser commit: `tools/tracechaser` gitlink.

**Interfaces:**

- Produces stable commands equivalent to S2:
  `--complete-audio-game s2 --rom <absolute> --movie <absolute>
  --service-manifest <absolute> --capability <absolute> --output <create-new-absolute-file>`;
  and S3K:
  `--complete-audio-game s3k --rom <absolute> --movie <absolute>
  --service-manifest <absolute> --output <create-new-absolute-file>`.
  S2 requires `--capability`; S3K forbids it because its pinned runner/profile
  has no capability input.
  These are TraceChaser's observer service manifest and capability fixture, not OpenGGF's
  complete-run run manifest. OpenGGF retains the latter separately for canonical fixture/segment
  identity.
- Consumes the existing `CaptureRawPinned(...)` methods and raw sinks without changing their ABI,
  observer graph, service manifest, identity, bounds, or cutoff rules.
- Produces one dedicated Linux launcher below the TraceChaser root. It accepts only the closed
  complete-audio argv, sources the pinned environment, unsets `DISPLAY`, and executes the fixed
  built assembly through Mono. It does not accept the generic trace wrapper's repository/fixture
  roots and does not invoke a shell command string supplied by OpenGGF.

- [ ] Create an isolated TraceChaser feature worktree/branch from its pinned commit; do not switch
  the checkout used by the OpenGGF main workspace.
- [ ] Write RED argument/publication tests for exact game selection, absolute existing ROM/movie/
  service-manifest/capability inputs, create-new output, unknown options, short capture failure,
  and no partial destination after failure.
- [ ] Run the TraceChaser filtered argument/runner tests and confirm the command is absent.
- [ ] Add one closed command branch selecting only the two compiled runner methods. Stage output
  beside the destination, close and validate capture, then atomically create the destination;
  never overwrite an existing file.
- [ ] Dispatch complete-audio before the generic trace boundary and add the dedicated launcher;
  prove at Main level that generic boundary arguments remain rejected by the closed command and
  that the launcher preserves the exact argv.
- [ ] Run `bizhawk-headless/test.sh --jobs 1 --filter 'TraceCliTests'`, then the S2 and S3K
  complete-audio runner/raw-sink filters.
- [ ] Commit and independently review the TraceChaser change; update the OpenGGF submodule pointer
  only to that reviewed commit.

### Task 3: OpenGGF reference-process boundary and transactional projectors

**Files:**

- Create: `src/main/java/com/openggf/tools/audio/completerun/TraceChaserAudioProcess.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/s2/S2CompleteRunReferenceProjector.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/s2/S2CompleteRunReferenceProducer.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/s3k/S3kCompleteRunReferenceProjector.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/s3k/S3kCompleteRunReferenceProducer.java`
- Test: `src/test/java/com/openggf/tools/audio/completerun/TestTraceChaserAudioProcess.java`
- Test: `src/test/java/com/openggf/tools/audio/completerun/s2/TestS2CompleteRunReferenceProducer.java`
- Test: `src/test/java/com/openggf/tools/audio/completerun/s3k/TestS3kCompleteRunReferenceProducer.java`

**Interfaces:**

- Consumes: `CompleteRunAudioProducer.Request`, Task 2's fixed command, raw adapters, state
  decoders/normalizers, and `CompleteRunAudioCaptureStore.writeNew(...)`.
- Produces: transactionally staged canonical reference records for state/chip/cutoff layers and any service/ownership/
  lifecycle layer whose exact native-to-canonical equivalence is proven by the game-owned
  projector tests; zero request/decision records under the current observer graph. Authenticated
  canonical store publication remains fail-closed until Task 7 installs exact reference runtime
  identities, observer identities/proofs, and capability summaries.

- [ ] Write RED process tests using a deterministic fake executable that records argv and creates
  or fails a raw file. Treat `Request.referenceHome()` as the pinned TraceChaser root and assert
  deterministic resolution of
  `bizhawk-headless/fixtures/gpgx-audio-service-manifests-v1.json` for both games and
  `bizhawk-headless/fixtures/gpgx-audio-capability-v1.json` for S2 only, exact absolute paths,
  no shell interpretation, bounded stderr, nonzero exit propagation, and cleanup of uncommitted
  output.
- [ ] Implement `ProcessBuilder` invocation with an explicit argv list and Task 2's closed game
  selector. Resolve the service manifest and dedicated complete-audio launcher only from the canonical fixed paths
  below `Request.referenceHome()` for both games; resolve and pass the capability fixture only
  for S2. Require ordinary non-symlink files and let TraceChaser enforce their pinned content
  hashes. OpenGGF's `Request.runManifest()` remains separate and is used only for canonical
  fixture/segment identity. Accept no caller-provided executable arguments beyond the typed
  request fields.
- [ ] Write RED projector tests from hand-authored minimal valid raw prefixes: state/chip/cutoff
  events become canonical records in source order; raw streams without approved request events
  produce no `Request`/`Decision`; unknown semantic events abort publication. For every native
  service kind, either prove a source-cited mapping to an OpenGGF service/ownership/lifecycle
  record with literal expected coordinates and order, or keep that layer unavailable.
- [ ] Implement each adapter `Sink` as a private staged transaction. Decode/normalize driver state
  through the existing game-owned classes and write canonical records only on adapter `commit()`.
- [ ] Write RED direct producer tests for wrong producer kind/profile/ROM/BK2/manifest/
  reference-home, pre-existing output, subprocess failure, adapter failure, and fail-closed
  authentication while the profile binding is unavailable. Exercise projector/store mechanics
  only with explicitly test-only synthetic metadata; never publish production captures with a
  placeholder identity. Move successful direct producer, registry, and CLI publication to Task 7.
- [ ] Implement the two reserved fixed reference producers and run their focused tests plus both
  existing raw-adapter suites.
- [ ] Commit as `feat(audio): consume TraceChaser complete-run references`.

### Task 4: Production BK2 cursor and authority guard

**Files:**

- Create: `src/main/java/com/openggf/tools/audio/completerun/Bk2InputCursor.java`
- Modify: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioAuthorityGuard.java`
- Create: `src/test/java/com/openggf/tools/audio/completerun/TestBk2InputCursor.java`

**Interfaces:**

- Produces: `publish(InputHandler)`, `advance()`, `absoluteFrame()`, and `exhausted()` over the
  immutable `Bk2Movie.getFrames()` list.
- Consumes: `RecordedInputSnapshots.fromBk2(current, previous)` and
  `InputHandler.setLogicalOverride(...)`.

- [ ] Write RED tests for frame zero, current/previous edges, two players, publish-before-advance,
  duplicate publish/advance rejection, and fail-fast exhaustion. Name the production mutation
  each test catches; do not assert on a mock input handler.
- [ ] Implement the minimal state machine without using clamping `Bk2Movie.getFrame(int)`.
- [ ] Extend the static authority guard with an authenticated-producer package inventory that
  rejects every forbidden trace/reference/timing/direct-load symbol from the design.
- [ ] Run `mvn -Dmse=off "-Dtest=TestBk2InputCursor,TestCompleteRunAudioAuthorityGuard" test -B`.
- [ ] Commit as `feat(audio): add production BK2 audio cursor`.

### Task 5: Headless production startup, cadence, and observer lease

**Files:**

- Modify: `src/main/java/com/openggf/Engine.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/ProductionBk2AudioRunner.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioObserverLease.java`
- Create: `src/test/java/com/openggf/tools/audio/completerun/TestProductionBk2AudioRunner.java`
- Create: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioObserverLease.java`
- Modify: `src/test/java/com/openggf/TestEngineLiveCapturePresentation.java`

**Interfaces:**

- Produces `Engine.initializeConfiguredHeadlessSession(InputHandler, AudioBackend)`, which sets
  the supplied logical input owner, installs the caller-owned production headless backend, marks
  that exact backend initialized, and delegates to one extracted `initializeConfiguredStartup()`
  method. `ensureAudioBackend()` must retain that backend and only ensure its presentation sink;
  it must never construct `LWJGLAudioBackend` for this session.
  The ordinary `Engine.init()` path calls that same extracted method after GLFW/graphics setup;
  it preserves the existing legal-disclaimer → master-title → `initializeGame()` choice and the
  existing `initializeGame()` → title/level-select/default-level ownership. The seam receives an
  already configured headless `EngineContext`, creates no GLFW/OpenAL device, and contains no
  mode or direct-level decision of its own.
- Makes `Engine.presentOuterAudioFrame(GameLoop, boolean, boolean)` tooling-visible without
  duplicating its `GameLoop.presentOuterFrame(...)` then `GameServices.audio().update()` order.
- Produces a runner callback carrying the absolute BK2 row and append-only observed events after
  exactly one step/presentation/cursor advance.

- [ ] Write RED engine tests proving
  `initializeConfiguredHeadlessSession(InputHandler, AudioBackend)` selects the same
  legal/master/title/level-select/default startup branches as the ordinary initialization block,
  installs and retains the exact input/backend instances through `initializeGame()` and master
  title creation, and never constructs or installs `LWJGLAudioBackend`.
- [ ] Extract the current post-platform startup branch into
  `initializeConfiguredStartup()`, call it from both `init()` and the headless seam, and keep
  `enterConfiguredStartupMode()` plus `loadDefaultStartingLevel(...)` private and unchanged.
- [ ] Write RED runner tests covering legal/master/title/level/special-stage mode transitions,
  one row → one `step()` → one outer presentation/update → one cursor advance, and failure when
  trace fast-forward/user-recording pumps are active.
- [ ] Write RED observer-lease tests for install-before-start, exclusive ownership, behaviorally
  inert append-only observation, and cleanup after success plus each injected failure point.
- [ ] Implement the runner and lease. Construct `HeadlessSmpsAudioBackend` over the established
  no-device presentation sink, pass it to the headless session seam, and assert identity after
  startup. Audio stays enabled and the runner never double-presents.
- [ ] Run the new tests plus `TestEngineLiveCapturePresentation`,
  `TestGameLoopAudioPresentationModes`, `TestAudioPresentationBoundary`, and
  `TestAudioDiagnosticObservers`.
- [ ] Run ROM-gated S2 and S3K frame-zero route feasibility tests with absolute ROM/BK2 paths.
  A failed natural transition becomes a named product gap and focused RED, not a bypass.
- [ ] Commit as `feat(audio): drive production audio from BK2 input`.

### Task 6: Canonical OpenGGF capture reducer and fixed engine producers

**Revised dependency:** Task 6 consumes the reviewed v2 contract above, not the former bundled
service model. Its reducer must populate frame-owned admission decisions, post-row state, and chip
events directly from their production observers even when a row has zero or a different number of
semantic services. It must select the OPENGGF producer observation inventory explicitly and emit
null for every unobserved layer; an empty observed list is not a substitute. Task 6 may widen a
producer inventory only after focused source-backed projection tests, and may not widen shared
comparison authority by itself.

**Files:**

- Create: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioCaptureReducer.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/s2/S2CompleteRunOpenGgfProducer.java`
- Create: `src/main/java/com/openggf/tools/audio/completerun/s3k/S3kCompleteRunOpenGgfProducer.java`
- Test: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioCaptureReducer.java`
- Test: `src/test/java/com/openggf/tools/audio/completerun/s2/TestS2CompleteRunOpenGgfProducer.java`
- Test: `src/test/java/com/openggf/tools/audio/completerun/s3k/TestS3kCompleteRunOpenGgfProducer.java`

**Interfaces:**

- Consumes Task 5 observations and game profiles; produces canonical frame/lifecycle/cutoff/
  terminal records through `CompleteRunAudioCaptureStore`.
- Both game producers consume only the whitelisted request fields and run from movie frame zero.

- [ ] Write RED reducer tests for request/admission/service/chip ordering, zero/multiple services
  per row, role ownership, lifecycle, segment gaps, cutoff frontier, terminal counts/digests, and
  first failure abort.
- [ ] Implement a game-neutral reducer; game-specific normalization/resolution remains in profile
  collaborators and packages.
- [ ] Write RED producer authority tests that poison every forbidden manifest/bootstrap field and
  prove output is unchanged or rejected before startup. Assert the engine producer never reads
  `referenceHome`.
- [ ] Implement S2/S3K fixed engine producers, driving from frame zero and retaining only each
  profile's declared comparison interval without resetting audio at the first retained row.
- [ ] Run focused reducer/producer/authority tests and the S1 regression oracle tests.
- [ ] Commit as `feat(audio): capture production-owned complete-run audio`.

### Task 7: Pin fixed identities and enable fresh-CLI execution

**S2 prerequisite:** keep the production REFERENCE binding unavailable until TraceChaser exposes a
new authenticated raw contract carrying the true pre-row-769 begin row and native ordinal (or an
equivalent typed carried-in origin) for every boundary service. Strictly validate and project that
evidence before pinning S2's buffered-native identity/capability. Do not infer the DPCM origin from
the comparison boundary, weaken strict raw v1 with optional fields, or publish callback provenance.
The existing OPENGGF/callback prefix-projector metadata is test-only.

**Files:**

- Modify: `src/main/java/com/openggf/tools/audio/completerun/s2/S2CompleteRunAudioProfile.java`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/s3k/S3kCompleteRunAudioProfile.java`
- Modify only if required by the existing closed dispatch:
  `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioProducerRegistry.java`
- Modify: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioCli.java`
- Modify: `src/test/java/com/openggf/tools/audio/completerun/s2/TestS2CompleteRunAudioFixture.java`
- Create: `src/test/java/com/openggf/tools/audio/completerun/s3k/TestS3kCompleteRunAudioFixture.java`
- Modify: `tools/audio/run_complete_audio_parity.sh`

**Interfaces:**

- Produces pinned `ProducerRuntimeIdentity`, `ObserverProof`, `ObserverRuntimeIdentity`, and
  `PinnedProducerBinding` entries for both producer kinds. `NativeCapabilitySummary` is pinned
  only for TraceChaser reference producers using the buffered native observer; OpenGGF uses its
  callback/production observer identity and does not impersonate native capability.
- Consumes reviewed TraceChaser artifacts and the built OpenGGF producer artifact hash.

- [ ] Write RED fresh-JVM CLI tests for `producer-status`, deterministic double production, validate,
  compare, unknown profile, wrong producer kind, changed binary/core/observer/capability hash, and
  output no-replace behavior.
- [ ] Replace unavailable bindings only with exact reviewed hashes/proofs. Finalize each profile's
  layer inventory from Task 3's projector evidence: a service/ownership/lifecycle layer stays
  unavailable unless its exact cross-producer equivalence was proven. Keep request/decision
  limitations until a separately reviewed source observation exists.
- [ ] Immediately after installing each exact REFERENCE binding, run the deferred Task 3 direct
  producer success/create-new-store tests before enabling registry/CLI production. The resulting
  store must pass both fixture-profile and runtime-profile validation; no late identity patching.
- [ ] Update the shell wrapper to validate its external run root and TraceChaser home, invoke both
  fixed producers twice, require byte-identical captures, compare, and publish only the small run
  manifest/report.
- [ ] Run `mvn -Dmse=off package -B` followed by the fresh-CLI tests in a new JVM.
- [ ] Commit as `feat(audio): enable fixed S2 and S3K parity producers`.

### Task 8: Execute comparisons and advance first divergences

**Files:**

- Modify only the ROM-owned production source and focused tests proven by each first divergence.
- Modify: `docs/status/audio-frontier-log.md`
- Modify: current behavior specs/gap ledger when evidence changes.
- Add validation reports under `docs/architecture/validation/audio/` for durable completed runs.

**Interfaces:**

- Consumes Tasks 1-7; produces an authenticated service/state/chip first frontier or
  `REFERENCE_LIMITATION` for each complete run.

- [ ] Run the S2 full command from frame zero against `[769,259590)` and preserve the first report.
- [ ] Run the S3K Knuckles full command from frame zero against `[810,434417)` and preserve the
  first report. Do not run the Sonic/Tails diagnostic under the Knuckles identity.
- [ ] For each product-route or semantic divergence: identify the exact shipped routine/state,
  write and verify a focused RED, implement the minimal source-owned behavior, run GREEN, rerun
  S1 regression gates, then restart comparison from the profile boundary.
- [ ] Update the frontier log on every movement with command, commit/worktree, result category,
  comparison/error counts, and first frame/service/field.
- [ ] Stop only at complete compared-layer equality, an honest reference limitation, or a
  reproducible product gap whose next source-owned RED is recorded. Never realign or skip ahead.

### Task 9: Refresh the six-stage evidence and remaining queue

**Files:**

- Modify: `docs/architecture/audits/audio/2026-08-31-sound-driver-re-current-state-audit.md`
- Modify: the current cross-game gap analysis and affected per-game behavior specs.
- Modify: `docs/architecture/validation/audio/2026-08-21-smps-playback-listening-checklist.md`
- Modify release/discrepancy documents only when their mapped facts actually change.

- [ ] Reclassify every roadmap item with the new production-owned evidence, distinguishing
  `MATCH`, `REFERENCE_LIMITATION`, first divergence, unit/synthetic contract, and
  fixture-assisted projection.
- [ ] Catalogue remaining source-owned gaps: request authority, priority/queue state, pause,
  1-up/fade/speed, modulation/frequency edges, ROM-read tables, DAC/PCM timing, and analog mix.
- [ ] Add affected scenarios to the human listening queue without marking them heard.
- [ ] Commit the evidence refresh with correct documentation trailers.

### Task 10: End-to-end review and delivery verification

**Files:**

- Create: `docs/architecture/validation/audio/2026-08-31-sound-driver-roadmap-integration-report.md`
- Create: `docs/architecture/audits/audio/2026-08-31-sound-driver-roadmap-end-to-end-review.md`
- Modify: `README.md` during final merge into `develop`, as required by policy.

- [ ] Run independent whole-branch review against the requirements, design, and this plan; fix all
  Critical/Important findings and record residual risk.
- [ ] Fetch and fast-forward the main-workspace `develop` without disturbing user changes; record
  the updated baseline full-suite failures exactly.
- [ ] In the feature worktree run focused audio/complete-run tests, S1 oracle regressions,
  TraceChaser native tests, the ordinary suite, and fresh-JVM guards on JDK 21 with Lua 5.4 and
  all three absolute ROM paths.
- [ ] After explicit human integration approval, merge into main-workspace `develop`, update the
  required README release summary, rerun the full ordinary/guard regression comparison, and push
  only `develop`.
- [ ] Remove only verified clean/merged worktrees and fully merged local scaffolding branches;
  preserve and report any unknown or user-authored change.

## Verification command set

Focused shared framework:

```bash
LUA_BIN=lua5.4 mvn -Dmse=off \
  '-Dtest=com.openggf.tools.audio.completerun.**,com.openggf.audio.TestAudioDiagnosticObservers,com.openggf.audio.TestAudioPresentationBoundary' \
  test -B
```

S1 regression gates:

```bash
LUA_BIN=lua5.4 mvn -Dmse=off \
  '-Dtest=com.openggf.tools.audio.parity.s1.**,com.openggf.audio.smps.TestSmpsSequencerCadence,com.openggf.audio.TestSmpsFadeAudioThroughput' \
  test -B
```

Ordinary and guards with verified absolute ROM paths:

```bash
LUA_BIN=lua5.4 mvn -Dmse=off \
  '-Dsonic1.rom.path=<absolute S1 REV01 ROM>' \
  '-Dsonic2.rom.path=<absolute S2 REV01 ROM>' \
  '-Ds3k.rom.path=<absolute locked-on S3K ROM>' test -B

LUA_BIN=lua5.4 mvn -Dmse=off -Pguards \
  '-Dsonic1.rom.path=<absolute S1 REV01 ROM>' \
  '-Dsonic2.rom.path=<absolute S2 REV01 ROM>' \
  '-Ds3k.rom.path=<absolute locked-on S3K ROM>' test -B
```

## Required closing artifacts

- Integration Report: exact changed files/commits, TraceChaser gitlink, commands/results,
  frontiers, limitations, and deferrals.
- End-to-End Review: requirements traceability, architecture consistency, findings/fixes,
  residual risk, and human listening/integration checklist.
