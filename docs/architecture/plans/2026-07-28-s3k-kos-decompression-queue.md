# S3K direct Kosinski decompression queue implementation plan

Date: 2026-07-28

Design:
[`../designs/2026-07-28-s3k-kos-decompression-queue.md`](../designs/2026-07-28-s3k-kos-decompression-queue.md)

## Delivery constraints

- Develop on `bugfix/ai-s3k-kos-decompression-queue` in an isolated worktree
  created from the main workspace's current `develop`.
- Use JDK 21 (`mvn -v` is authoritative).
- Follow test-first red/green/refactor for each behavior task.
- Do not modify committed files under `src/test/resources/traces/` without a
  separate explicit user approval for an exact reviewed publication candidate.
- The native headless recorder is fixture authority. Lua changes are optional
  corroboration and are not required solely for parity.
- C# changes remain C# 7.x and new files, if any, are added to both project
  files and registered in the plain test registry.
- Preserve unrelated dirty files in the main workspace.

## Task 1: Per-kind hardware authority and schema 2

### Tests first

Extend:

- `src/test/java/com/openggf/game/timing/TestHardwareTimingService.java`
- `src/test/java/com/openggf/game/timing/TestHardwareTimingRewind.java`
- `src/test/java/com/openggf/trace/timing/TestHardwareTimingStreamLoader.java`
- `src/test/java/com/openggf/trace/timing/TestHardwareTimingReplayPort.java`
- `src/test/java/com/openggf/trace/timing/TestHardwareTimingAuthorityGuard.java`
- `src/test/java/com/openggf/trace/TestTraceDataHardwareTiming.java`
- `src/test/java/com/openggf/trace/timing/TestCommittedHardwareTimingFixtures.java`
- `src/test/java/com/openggf/TestLevelFrameHardwareTimingBoundaries.java`
- `src/test/java/com/openggf/TestLevelIterationHardwareTimingAdmissionOrder.java`
- `src/test/java/com/openggf/tools/TestRecordingFrameDriverHardwareTiming.java`

Prove:

- schema 1 authorizes only `KOS_MODULE_QUEUE`;
- schema 2 authorizes module and direct kinds;
- no stream leaves both kinds live;
- direct readiness is live while a module parent remains recorded under
  schema 1;
- an edge for a non-recorded kind is rejected;
- per-kind policy and ordinal state round-trip through rewind;
- canonical ordering and ordinal continuity remain per kind.
- schema-2 direct readiness is admitted at PRE before same-frame consumers,
  followed by an independently matching module POST edge;
- committed schema-1 streams remain loadable and enforce recorded module work
  while live-only direct jobs do not become leftover recorded submissions.

Run the focused tests and observe the expected failures before implementation.

### Implementation

Update:

- `HardwareWorkKind`
- `HardwareTimingService` and snapshot/job helpers
- `RecordedCompletionAuthority`
- `HardwareTimingReplayPort`, schedule, loader, coordinator/metadata plumbing
- schema/version validation and error messages

Replace the single admission policy with an immutable/snapshotted per-kind
map. Make preparation and live readiness FIFO ordering per kind. Preserve
generic authority isolation.

### Verification

```bash
mvn "-Dtest=TestHardwareTimingService,TestHardwareTimingRewind,TestHardwareTimingStreamLoader,TestHardwareTimingReplayPort,TestHardwareTimingAuthorityGuard,TestTraceDataHardwareTiming,TestCommittedHardwareTimingFixtures,TestLevelFrameHardwareTimingBoundaries,TestLevelIterationHardwareTimingAdmissionOrder,TestRecordingFrameDriverHardwareTiming" test
```

Reviewer checklist: no gameplay APIs become reachable; schema 1 behavior for
module-only fixtures remains parse-compatible; unknown schema/kind fails.

## Task 2: Shared direct queue and standard-Kos scanner

### Tests first

Create `TestS3kKosDecompressionQueue` and extend
`TestResumableKosinskiDecoder`.

Prove:

- standard Kos source begins at its descriptor and scans its exact compressed
  and decoded lengths;
- descriptor refill, literal, short/long match, no-output command, terminator,
  invalid backreference, and ROM-bound failures;
- four physical jobs fit and a fifth ordinary submission fails;
- only `PRE_MAIN_LOOP` advances/retire direct work;
- ready-unclaimed payload does not consume physical capacity or keep the
  queue-pending predicate true;
- head shift, identical adjacent jobs, and retire-plus-append retain identity;
- snapshot/restore works before, during, and after active decoding;
- recorded direct admission holds an already prepared head until its exact PRE
  edge;
- exact `0xFFFFxxxx` destination bits participate in fingerprinting.

An ordinary production call that attempts to append beyond physical capacity
is an invariant failure. Module service always checks capacity first and
returns/retries without submitting. Ready-but-unclaimed retired payload is not
physical occupancy.

### Implementation

Add beside `S3kKosModuleQueue`:

- direct descriptor;
- direct queue/session owner;
- preparation and rewind snapshot;
- standard-Kos inspection result if the existing reader cannot express it.

Reuse `ResumableKosinskiDecoder`; do not add a second codec. Keep physical FIFO
state separate from ready/claimed timing jobs. Add exact canonical 68000 RAM
destination constants/mapping.

Wire the physical owner into the gameplay-session lifecycle. Inspect and update
the actual construction path among `GameplayModeContext`, `GameServices`,
`Sonic3kGameModule`, and `Sonic3kObjectArtProvider`; register/reset exactly one
owner and ensure all facades resolve it after level load, seamless transition,
rewind restore, and session teardown. Add registration/reset tests and include
`TestRewindCoverageGuard` and `TestStaticStateRewindCoverageGuard`.

### Verification

```bash
mvn "-Dtest=TestS3kKosDecompressionQueue,TestResumableKosinskiDecoder,TestHardwareTimingRewind,TestRewindCoverageGuard,TestStaticStateRewindCoverageGuard" test
```

Reviewer checklist: ROM bytes only; exact four-entry FIFO; PRE boundary; no
zone/route branches; rewind contains active decoder and physical order.

## Task 3: Compose KosM parents over direct children

### Tests first

Extend:

- `TestS3kKosModuleQueue`
- `TestS3kKosModuleReadiness`
- `TestS3kKosStructuralSequence`
- `TestS3kKosTimingRewindIntegration`

Prove frame-by-frame:

- POST A submits child N and returns;
- PRE B completes or prepares child N;
- POST B retires/claims child N and returns;
- POST C submits child N+1;
- ordinary work before and after a child delays module advancement until the
  complete direct FIFO is empty;
- full direct capacity defers child submission rather than throwing;
- direct PRE retirement and final module POST retirement can share a raw frame;
- one physical child produces one direct event and one archive produces at
  most one final module event;
- schema-1 live direct children can prepare a recorded module parent;
- rewind after direct retirement but before module claim is exact.

### Implementation

Refactor `S3kKosModuleQueue`:

- remove its embedded direct decoder;
- make archive parents coordinator-owned aggregate jobs;
- submit exact aligned standard-Kos child streams into the shared direct owner;
- perform at most one module state transition per POST service;
- assemble the parent payload only from claimed child payloads;
- retain parent-child handle metadata in snapshots.

Update all construction sites so facades share the one session/resource owner.
Do not broaden generic timing-service knowledge of S3K parents.

### Verification

```bash
mvn "-Dtest=TestS3kKosModuleQueue,TestS3kKosModuleReadiness,TestS3kKosStructuralSequence,TestS3kKosTimingRewindIntegration" test
```

Reviewer checklist: no private decoder remains; physical capacity/queue-empty
is shared; parent preparation is never generic-scheduler-driven.

## Task 4: AIZ and ICZ gameplay consumers

### Tests first

Extend:

- `TestSonic3kAIZEvents`
- `TestS3kZoneKosRewind`
- AIZ terrain/mutation tests
- ICZ event and level-transition tests
- `TestZoneEventRewindSchemaGuard`
- `TestSonic3kLevelEventRewindSnapshot`
- `TestS3kIczAct1TransitionHeadless`
- `TestSonic3kIczRewindRoundTrip`

Prove:

- AIZ queues its direct block and KosM work once;
- AIZ event/deformation progression and 16x16 publication occur on frame N's
  first scan after final PRE retirement;
- AIZ KosM 8x8 publication waits until the module parent retires at POST and is
  not consumer-visible before frame N+1;
- ICZ queues two ordinary direct streams plus its KosM archive at `$6900`;
- ICZ contains no 41-frame readiness path and waits on physical direct-empty;
- ICZ handles survive PRE completion, in-frame `executeActTransition`, later
  POST module retirement, and ICZ2 publication;
- rewind works before and after both decisive boundaries.

### Implementation

- Split `AizIntroTerrainSwap` direct-block publication from KosM pattern
  publication.
- Add a ROM-derived S3K level-resource profile for the AIZ intro
  load that composes immediate LevelLoadBlock entry 0 with deferred
  declarations sourced from entry 26: the exact main-level 16x16 block and
  KosM pattern descriptors. Keep entry 0's secondary intro resources loaded.
  Keep the profile immutable and
  create a fresh exact-consumption tracker per `Sonic3k.loadLevel` call.
  Consume/verify it in the S3K `LevelResourcePlan` builder without an AIZ
  branch in shared code; prove repeated intro loads reproduce the same
  deferral, entry 0 assets remain visible, entry 26 bytes remain absent until
  their fences, missing/duplicate/non-matching entries fail, and the non-intro
  profile/later ordinary loads are unaffected.
- Update `Sonic3kAIZEvents` ownership and rewind handle rebinding.
- Replace `ICZ2_SECONDARY_KOS_DRAIN_FRAMES` and
  `act2TransitionKosDrainFrames` with shared-queue submissions and handles.
- Transfer ICZ module ownership explicitly through the seamless-transition
  resource owner; do not reset session timing state.
- Add an immutable transition-scoped deferred-resource manifest containing the
  exact already-submitted ICZ2 secondary chunk, block, and KosM descriptors.
  Register it as an immutable `SeamlessTransitionResourceHandoff` containing
  the exact production handles/facades in a session-owned rewind-snapshottable
  registry; carry only its opaque id on the request.
  `LevelActTransitionExecutor` atomically claims/removes the id before any
  mutation, creates a local consumption tracker, passes the policy through a
  `LevelManager.loadLevelData`/`Game.loadLevel` overload, verifies exact-once
  consumption, initializes target events, then transfers runtime ownership.
  Preserve the handoff when `RELOAD_SAME_LEVEL` is rebuilt. The S3K
  `LevelResourcePlan` builder consumes descriptor identity, omits each matched
  op exactly once, fails missing/duplicate/non-matches, and contains no
  ICZ/zone-name branch. Immediate and queued transitions use the same handoff
  path; ordinary later loads and unrelated transitions remain unchanged.
- Add negative/isolation tests for missing, duplicate, non-matching, and
  double-consumed manifests; exact-once omission; queued execution;
  `RELOAD_SAME_LEVEL` preservation; unrelated transitions; and an ordinary
  later ICZ2 load. Add rewind tests before and after registry claim, plus a
  failed execution proving the claimed id is not restored for retry.
- Update rewind schemas rather than baselining new gaps.

### Verification

```bash
mvn "-Dtest=TestSonic3kAIZEvents,TestS3kZoneKosRewind,TestZoneEventRewindSchemaGuard,TestSonic3kLevelEventRewindSnapshot,TestS3kIczAct1TransitionHeadless,TestSonic3kIczRewindRoundTrip" test
```

Reviewer checklist: ROM predicate, centre-coordinate conventions unaffected,
no fixed delay, no zone check in shared runtime, mutations use the pipeline.

## Task 5: Native recorder schema-2 direct ledger

### Tests first

Extend:

- `tools/bizhawk-headless/tests/HardwareTimingEventEngineTests.cs`
- `tools/bizhawk-headless/tests/S3KTraceMetadataWriterTests.cs`
- `tools/bizhawk-headless/tests/S3KCompleteRunPublicationTests.cs`
- `tools/bizhawk-headless/tests/S3KTraceDifferentialTests.cs`
- applicable standard/complete-run capture runner tests

Prove:

- direct RAM count uses `$FF0E & $7FFF`;
- direct RAM busy state uses bit 15 and remains part of the mirrored lifecycle
  evidence instead of being discarded after count extraction;
- direct FIFO is `$FF40`, four eight-byte entries;
- pending/busy, shift without zero, stale final slot, identical jobs, and
  all retire-plus-append count transitions (`1 -> 1`, `1 -> 2`, `2 -> 3`,
  and longer unchanged-count cases) reconcile through proven canonical
  suffix/prefix overlap;
- unexplained mutation of any occupied slot, including slot zero, fails;
- a still-busy head cannot retire or change identity; busy-to-not-busy proves
  retirement even for an identical `A -> A` replacement, while a stable
  identical snapshot emits no completion and consumes no ordinal;
- standard-Kos scan lengths and fingerprints come from one checked-in
  language-neutral vector source consumed independently by Java and C# tests;
- those shared vectors cover descriptor refill, literal, short/long/extended
  matches, no-output commands, invalid backreferences, terminators, ROM-bound
  failure, compressed length, and decoded length;
- all direct events use `pre_main_loop`;
- direct PRE then module POST sort canonically on the same raw frame;
- gaps/handoffs preserve both ledgers;
- accepted discard/reset clears both ledgers and restarts both ordinal bases;
- no event is fabricated without a previously mirrored submission;
- metadata publishes hardware timing schema 2 and bumped recorder versions.
- schema-1 output remains deliberately selectable for compatibility tests.
- STANDARD differential metadata validation accepts current production
  `6.38-s3k` / trace-schema 7 / hardware-schema 2 output while retaining an
  explicit load-only compatibility path for committed `6.37-s3k` /
  trace-schema 7 / hardware-schema 1 fixtures.

### Implementation

Update:

- `S3KRam.cs`
- `HardwareTimingEventEngine.cs`
- `S3KTraceMetadataWriter.cs`
- `S3KCompleteRunMetadataWriter.cs`
- standard and complete-run capture plumbing only where schema selection or
  reset/handoff requires it
- `S3KTraceDifferentialTests.cs`, so current `6.38-s3k` / schema 7 /
  hardware-schema 2 output validates both ledgers while committed
  `6.37-s3k` / schema 7 / hardware-schema 1 remains load-only and cannot be
  normalized into direct-authority differential success

Keep the existing source file unless separation materially improves clarity;
avoid new ignored files when unnecessary. Do not update the frozen Lua
recorder merely for parity; the maintained cross-language vectors are Java and
native C#.

If a new ignored harness file is unavoidable, add it with `git add -f` and
verify tracking through both `git ls-files tools/bizhawk-headless` and the
eventual `git show --stat`.

### Verification

```bash
cd tools/bizhawk-headless
./test.sh --filter HardwareTimingEventEngine --jobs 1
./test.sh --no-gates
```

Run `--no-gates` with all locally available ROM variables supplied as well as
without them, and treat any ROM-enabled runner/CLI failure as blocking. Record
pass/fail/skip counts. Do not publish or replace fixtures.

Reviewer checklist: ROM-derived lifecycle, count-based reconciliation, no
self-certifying expectations, exact C# 7.x compatibility.

## Task 6: Documentation, guards, and compatibility inventory

Update:

- the approved cross-game hardware-timing design;
- the S3K hardware-timing inventory;
- trace format/recorder documentation and known discrepancy/status files
  whose claims change;
- `CHANGELOG.md`;
- `README.md` release/change-log entry required for integration into
  `develop`;
- trace-frontier log only when a measured frontier moves or regresses.

Add a checked inventory of committed schema-1 traces that reach AIZ intro or
the ICZ act transition. Do not change their payloads. State that they remain
loadable but cannot certify those direct-count boundaries until a separately
approved schema-2 publication.

Run documentation/authority/fixture guards.

## Task 7: End-to-end verification and review

Before the end-to-end runs, restore the `LevelManager` source-size guard
without increasing its budget. Extract the self-contained implementation of
`findPatternOffset(...)` to a package-local `LevelPatternLocator`; keep the
existing public method as a thin delegate and add focused parity tests for
successful lookup, search order, coordinate conversion, and not-found
results. Run `TestArchitecturalSourceGuard` to prove `LevelManager` is at or
below 2500 effective lines. Do not change the pre-existing GameLoop or
AbstractPlayableSprite budgets/failures as part of this extraction.

Repair attributable stale harnesses before the full suite:

- make `TestLevelIterationHardwareTimingAdmissionOrder` prepare Kos work
  through the runtime-owned direct/module queue path instead of submitting a
  fake raw module job to `HardwareTimingService`;
- update `TestGameplayModeContextRewindRegistry` to assert the registered
  seamless-transition handoff adapter and the resulting ten-key registry.
- give AIZ fire-transition queue tests a deliberately scoped drain helper that
  invokes timing admission, direct-FIFO retirement, and module-parent
  coordination in production order; do not call it from unrelated intro or
  save-event tests.

Run each repaired test in isolation before repeating combined verification.

### Java

Discover actual ROM filenames at repository root and supply the verified S3K
path through `-Ds3k.rom.path=...`.

Run focused queue, timing, AIZ, ICZ, rewind, guard, and trace tests, followed
by:

```bash
mvn "-Dtest=TestS3kIczCompleteRunTraceReplay" test
mvn "-Dtest=TestCommittedHardwareTimingFixtures" test
mvn test
```

### Native recorder

Run:

```bash
cd tools/bizhawk-headless
./test.sh --no-gates
```

If BizHawk and all ROMs are available, run the full suite with required
environment variables. Treat fixture-differential failures caused solely by
schema-2 output as an unresolved publication gate, not permission to replace
fixtures.

### Independent review

Review the completed diff against the design for:

- PRE/POST visibility and same-frame ordering;
- shared physical FIFO and parent-child dependency;
- per-kind authority isolation;
- rewind and seamless ICZ ownership;
- recorder correctness independent of fixture output;
- documentation and commit-policy obligations.

Fix all blocking findings and repeat review until green.

## Task 8: Publication candidate and approval gate

If any affected AIZ/ICZ fixture or differential gate fails because it lacks
schema-2 direct events:

1. Capture selected AIZ/ICZ publication candidates with the reviewed native
   recorder into harness `.scratch/`, never into committed fixtures.
2. Independently review recorder semantics against the named ROM lifecycle and
   the completed behavioral/unit tests.
3. Freeze candidate SHA-256 digests, byte lengths, metadata/recorder versions,
   segment and event inventories, boundary ordering, ordinal/fingerprint
   ranges, and categorise every byte delta mechanically against a named cause.
4. Measure the trace frontiers before candidate installation.
5. Present the exact candidate/evidence to the user and stop for explicit
   publication approval.

After approval only:

1. Install the exact reviewed native bytes without hand edits.
2. Pin frozen literal publication expectations.
3. Re-run native differential gates, fixture loader/schema/compression/reference
   guards, and Java replay tests.
4. Measure after-publication frontiers and update
   `docs/status/trace-frontier-log.md` with command, context, failures, error
   count, and first-error field/frame.
5. Independently review the installed byte deltas and publication evidence.

Without approval, do not modify `src/test/resources/traces/`, merge, push, or
claim completion if the implementation introduces attributable fixture/gate
failures.

## Task 9: Integration

Following the root `AGENTS.md`:

1. Fetch and fast-forward the main workspace `develop` without overwriting its
   unrelated dirty files.
2. Record the full-suite baseline on updated `develop`.
3. Rebase/merge updated `develop` into the worktree and repeat focused/full
   verification.
4. Commit with required trailers and no `--no-verify`.
5. Merge into main-workspace `develop`, including the staged README update.
6. Run the merged full suite and compare against baseline.
7. Push only main-workspace `develop`.
8. Verify the worktree is clean/merged, remove it, delete its fully merged local
   branch, and prune metadata.

Integration proceeds only after either all attributable tests/gates are green
without fixture changes or an explicitly approved publication restores them.
Do not merge/push a reduced delivery unless the user separately authorizes that
exact unresolved state, and never claim it as completed delivery.
