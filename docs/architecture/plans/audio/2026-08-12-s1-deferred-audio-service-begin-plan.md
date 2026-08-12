# S1 Deferred Audio Service Begin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Materialize exactly one kind-4 `UpdateMusic` begin after the row-8775 root kind-6 wait service ends, while retaining every physical retry as raw proof and preserving the single-owner semantic model.

**Architecture:** ABI v3 gains one bounded deferred-begin hook action and no struct-size change. Native owns the one-slot reservation and atomic END→BEGIN release; managed code proves identical callback identity and transactionality; Java stores the pending reservation only in buffered-native raw diagnostics while canonical comparison sees the released service normally.

**Tech Stack:** C99 GPGX patch/selftest harnesses, C# BizHawk headless observer/capture tests, Java 21 complete-run schema/store/comparator tests, Bash deterministic build/install tooling.

## Global Constraints

- Use Sonic 1 World REV01 SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` and the pinned all-emeralds BK2 without copying, renaming, or symlinking either fixture.
- Keep ABI version 3 and all native config/event/range/kind/hook struct sizes unchanged.
- Capacity is one deferred reservation; observation count is not fitted to the recorded value three.
- Do not grant kind 6 `ALLOW_CHILDREN`, infer recently-closed ancestry, or publish a partial capture.
- Native/core/schema changes are conductor-owned; S2/S3 game semantics remain unchanged.
- Build and test Java on JDK 21.

---

### Task 1: Freeze the native deferred-begin action

**Files:**
- Modify: `tools/bizhawk-headless/native/gpgx-audio-observer/selftest/matrix_harness.c`
- Modify: `tools/bizhawk-headless/native/gpgx-audio-observer/selftest/cpu_boundary_harness.c`
- Modify: `tools/bizhawk-headless/native/gpgx-audio-observer/0001-buffer-z80-audio-events.patch`

**Interfaces:**
- Produces: action constant `ACTION_DEFER_BEGIN_UNTIL_TOP_END = 11`, marker value `4`, and one native pending reservation.
- Consumes: existing service stack, action-10 hook validation, `EVENT_HOOK_MARKER`, and service push/pop helpers.

- [ ] Add matrix cases that fail to compile/configure before action 11 exists: valid kind-6 reservation, blocker with `ALLOW_CHILDREN`, duplicate reservation hook, non-M68K CPU, nonzero range/reserved bytes, wrong opcode, reset while pending, different M68K hook while pending, missing/wrong blocker release, capacity-short atomic release, cross-frame carry, and cutoff retention.
- [ ] Run `tools/bizhawk-headless/native/gpgx-audio-observer/selftest/run.sh`; verify the new action-11 case is RED while all earlier cases reach their prior assertions.
- [ ] Extend ABI-v3 validation so action 11 requires M68K, nonzero known target kind, a known expected blocker without `ALLOW_CHILDREN`, zero range/predicate/reserved fields, and a unique hook shape.
- [ ] Add the one-slot reservation fields and marker-value-4 emission. Bind markers to the current blocker token/kind/current ancestry and retain target kind/hook separately.
- [ ] Make blocker completion reserve all snapshot/END/BEGIN events before mutation, emit END then one root BEGIN, and clear the reservation only after successful publication.
- [ ] Reject reset, conflicting M68K hooks/writes, duplicate reservations, wrong blocker completion, and discard-before-cutoff-proof. Preserve Z80 blocker ownership and continuation aging.
- [ ] Rerun all six native harnesses and require every prior ABI1/2/3 case plus action 11 to pass.
- [ ] Commit the native RED/GREEN selftest checkpoint with the required policy trailers.

### Task 2: Project and correlate the deferred service transaction

**Files:**
- Modify: `tools/bizhawk-headless/src/Audio/CompleteRunAudioObserver.cs`
- Modify: `tools/bizhawk-headless/src/Audio/S1CompleteRunAudioReferenceCapture.cs`
- Modify: `tools/bizhawk-headless/fixtures/s1-audio-service-manifest-v1.json`
- Modify: `tools/bizhawk-headless/tests/CompleteRunAudioObserverTests.cs`
- Modify: `tools/bizhawk-headless/tests/S1CompleteRunAudioReferenceCaptureTests.cs`

**Interfaces:**
- Consumes: action 11, event kind 10/value 4, adjacent blocker END→released BEGIN.
- Produces: transactional `DeferredBeginEvidence` keyed by blocker token and callback A7/return identity.

- [ ] Add focused REDs for the exact row-8775 synthetic sequence: kind-4 END, root kind-6 BEGIN, three same-identity deferred markers, kind-6 END, one root kind-4 BEGIN.
- [ ] Add REDs for changed A7/return identity, wrong blocker token/kind/depth, interposed M68K hook/chip event, duplicate released begin, missing release, reset, consumer rejection, and later-frame validation rollback.
- [ ] Add the exact S1 action-11 manifest hook at `$71B4C` for expected kind 6 and target kind 4; leave begin alternatives `[0,2,3]` and kind-6 flags unchanged.
- [ ] Implement one transactional managed reservation. Correlate every marker to one callback, require identical A7/return identity, and consume it only on adjacent blocker END→BEGIN.
- [ ] Ensure cloned/rollback state includes the reservation and that streaming success cannot leak retained diagnostic state.
- [ ] Run `tools/bizhawk-headless/test.sh --filter CompleteRunAudioObserverTests --jobs 1` and the S1 capture filter; require all synthetic cases green with the real gate still opt-in.
- [ ] Commit the managed/manifest checkpoint with policy trailers.

### Task 3: Retain lossless raw reservation diagnostics

**Files:**
- Modify: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioTrace.java`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioJson.java`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioComparator.java`
- Modify: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioTrace.java`
- Modify: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioCutoffFrontier.java`
- Modify: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioComparator.java`
- Modify: `src/test/java/com/openggf/tools/audio/completerun/TestCompleteRunAudioCaptureStore.java`

**Interfaces:**
- Produces: immutable `NativeDeferredServiceBegin` in buffered-native diagnostics and cutoff sidecar.
- Consumes: marker-value-4 correlation chains and released ordinary `DriverService` begin.

- [ ] Add compile-time REDs for the missing immutable diagnostic and JSON fields.
- [ ] Define exact-width fields for blocker/target/hook identity, first/latest coordinates, observation count, pending/consumed status, and released token.
- [ ] Make raw JSON/storage roots sensitive to every field while semantic JSON/root excludes the diagnostic. Forbid the sidecar for OpenGGF/callback producers.
- [ ] Extend stream validation with one retained reservation across frames; require ordered markers, blocker continuity, adjacent blocker END→BEGIN release, exact cutoff carry, and terminal discharge.
- [ ] Add adversarial RED/GREEN cases for missing/duplicate/forged markers, changed coordinates, wrong released token, semantic-root stability, raw-root sensitivity, reset, cutoff pending, and cleanup immutability.
- [ ] Run the five CompleteRunAudio Java classes plus authority/CLI on JDK 21 and require zero failures/errors.
- [ ] Commit the raw-schema/validator checkpoint with policy trailers.

### Task 4: Prove the real S1 frontier and freeze the source contract

**Files:**
- Modify: `docs/architecture/research/audio/2026-08-11-complete-run-audio-frontier-checkpoint.md`
- Modify: `docs/status/trace-frontier-log.md`
- Test: `tools/bizhawk-headless/tests/S1CompleteRunAudioReferenceCaptureTests.cs`

**Interfaces:**
- Consumes: final action-11 core/install and exact S1 ROM/BK2.
- Produces: row-8775 ordinal proof and the next exact frontier.

- [ ] Build/install a diagnostic action-11 core from the locked source/toolchain without changing committed identity literals.
- [ ] Run the opt-in row-8775 test and require ordinals 12/13/20/21, three raw markers with identical managed identity, exactly one released kind-4 begin, and no output on any failure.
- [ ] Run a bounded capture beyond row 8775; record the next exact fault or the clean bound without inventing a fix.
- [ ] Update the research checkpoint and append-only frontier log with command, branch/worktree, identities, result, and next frontier.
- [ ] Rerun S1 synthetic and real focused gates.
- [ ] Commit the real-frontier evidence with policy trailers.

### Task 5: Regenerate deterministic artifacts and run compatibility gates

**Files:**
- Modify: `tools/bizhawk-headless/native/gpgx-audio-observer/0001-buffer-z80-audio-events.patch`
- Modify: `tools/bizhawk-headless/native/gpgx-audio-observer/artifact-lock.json`
- Modify: `tools/bizhawk-headless/native/gpgx-audio-observer/task7-build-recipe.json`
- Modify: `tools/bizhawk-headless/native/gpgx-audio-observer/README.md`
- Modify: `tools/bizhawk-headless/fixtures/gpgx-audio-capability-v1.json`
- Modify: `src/main/java/com/openggf/tools/audio/completerun/CompleteRunAudioProfiles.java`
- Modify: identity assertions under `src/test/` and `tools/bizhawk-headless/tests/`

**Interfaces:**
- Produces: one frozen action-11 native/core/managed/capability identity family.

- [ ] Regenerate the canonical patch from reviewed source and prove reverse/apply byte equality.
- [ ] Produce two clean builds from distinct copied durable toolchains and two installs; require raw core, compressed core, Build ID, source bundle, identity, and whole install tree byte equality.
- [ ] Record stale identity REDs before repinning Java, C#, capability, and documentation constants.
- [ ] Run `verify-deterministic-build.sh` across two checkout paths including spaces and hostile ambient compiler variables.
- [ ] Run native six-harness, managed observer/S1/S2/S3 focused gates, Java complete-run aggregate, real S1 row8775, S2 row769, S3K row810, and legacy S2/S3 vector/lifecycle gates.
- [ ] Run isolated paired S2 and S3K performance gates. Bind only fresh final-identity samples and require median <=10% and worst <=15%.
- [ ] Request independent review of exact frozen bytes; fix every Critical/Important/Minor finding under RED/GREEN and repeat affected identity gates.
- [ ] Commit the reviewed freeze with policy trailers. Do not push the worktree branch.

### Task 6: Integrate the approved R5 game slices

**Files:**
- Integrate commits: S2 `110bc8e06`, `52aa85e4d`
- Integrate commits: S3K `3b704da19`, `73d6fe095`
- Modify only derived identity files if the combined managed assembly changes.

**Interfaces:**
- Consumes: independently clean S2 decoder/catalog and S3K publication-inert preflight.
- Produces: one reviewed remote-backed integration checkpoint.

- [ ] Cherry-pick the clean game commits after the conductor freeze, resolving only derived documentation/identity conflicts.
- [ ] Capture the expected deterministic executable/capability RED, recompute the combined tuple, and update derived constants once.
- [ ] Run Java combined audio tests, C# game/raw/profile tests, real row gates, native harnesses, legacy vectors/lifecycle, deterministic verifier, and fresh identity-bound performance.
- [ ] Request final cross-commit review and correct any stale handoff text or hash.
- [ ] Push only `bugfix/ai-s1-audio-parity-frontier` after the worktree is clean and review is CLEAN.
