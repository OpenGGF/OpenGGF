# Handover Follow-ups Implementation Plan

## Delivery baseline

- Integration branch: the branch currently checked out in the main workspace,
  `develop`.
- Development branch/worktree: `bugfix/ai-handover-followups` at
  `.worktrees/handover-followups`.
- Actual starting commit: `2c64e09d4925cd6d9628ea59ac9874b2e26e6829`. The handover's
  `220e0bc35` is an ancestor.
- Required JVM: JDK 21 as reported by `mvn -v`.
- Verified ROM inputs (resolved from the repository root at execution time):
  - S1 REV01: `<repo>/Sonic The Hedgehog (W) (REV01) [!].gen`,
    SHA-1 `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`.
  - S2 REV01: `<repo>/Sonic The Hedgehog 2 (W) (REV01) [!].gen`,
    SHA-1 `8bca5dcef1af3e00098666fd892dc1c2a76333f9`.
  - S3K locked-on: `<repo>/Sonic and Knuckles & Sonic 3 (W) [!].gen`,
    SHA-1 `cfbf98c36c776677290a872547ac47c53d2761d6`.
- Baseline focused outcomes:
  - S2 contract: 6 tests, 1 failure, exact version mismatch.
  - AIZ complete: 57 comparator errors over 6,344 represented rows, then direct
    admission `#35` at raw 6346.
  - HCZ isolated replay method: 28 comparator errors over 3,295 represented rows,
    then direct admission `#90` at raw 3341.
  - MHZ complete: 865 comparator errors over 7,218 represented rows, then direct
    admission `#335` at raw 7221.

Run every complete-run replay in its own Maven invocation. Preserve each generated report
and context long enough to compare the next candidate, but do not commit generated target
output.

## Task 1: Correct the S2 canonical recorder assertion

Files:

- `src/test/java/com/openggf/tests/trace/s2/S2SpecialStageRecorderContractTest.java`

Steps:

1. Change only the committed-artifact assertion from `1.4-s2ss` to the exact native
   fixture stamp `1.4-s2ss-native`.
2. Leave the Lua source assertion at `1.4-s2ss`.
3. Do not edit or regenerate the fixture.

Verification:

```bash
mvn -Dmse=off \
  -Dtest=com.openggf.tests.trace.s2.S2SpecialStageRecorderContractTest test
tools/bizhawk-headless/test.sh --filter "S2 standalone special-stage"
```

If the native headless dependency is unavailable locally, record that exact environment
limitation; the Java contract is mandatory.

## Task 2: Publish and act on the bounded persistence audit

Files:

- `docs/architecture/audits/2026-08-02-aiz-hcz-mhz-persistence-audit.md`
- `src/main/java/com/openggf/game/sonic3k/objects/AizDrawBridgeObjectInstance.java`
- `src/test/java/com/openggf/game/sonic3k/objects/TestAiz2BossEndSequenceObjects.java`
- `src/test/java/com/openggf/level/objects/TestObjectManagerCounterBasedDynamicUnload.java`
- `src/main/java/com/openggf/game/sonic3k/objects/MhzSwingVineObjectInstance.java`
- `src/test/java/com/openggf/game/sonic3k/objects/TestMhzSwingVineObjectInstance.java`

### 2.1 Audit artifact

Publish the reviewed table for the concrete route shortlist. Include placement counts,
Java behavior, S&K ROM tail, disposition, and current test coverage. Mark the AIZ Draw
Bridge as actionable, the MHZ Swing Vine as characterization-gated, and the other entries
as exact self-managed, coupled, fixed-slot, or event-owned lifetimes.

### 2.2 AIZ Draw Bridge test-first correction

1. Add a direct contract test showing persistence is false and the cull reference is the
   fixed pivot rather than the bridge's moving display position.
2. Add a manager-level test using the real object class and a placement-backed spawn:
   - keep the bridge loaded at the native `$280` boundary;
   - move past the boundary and prove its dynamic slot unloads;
   - prove the respawn/load state is cleared rather than pinned.
3. Implement the ROM tail contract:
   - `isPersistent()` is false in normal/wait phases and true only during the triggered
     collapse countdown that self-deletes without a range tail;
   - `checksOutOfRangeAfterRoutine()` lets the wait operation consume the collapse trigger
     before the manager selects range deletion versus countdown persistence;
   - `usesCustomOutOfRangeCheck()` returns true;
   - `isCustomOutOfRange(cameraX)` computes
     `coarseBack = (cameraX - 0x80) & 0xFF80` and compares the saved `pivotX` bucket
     with unsigned native distance `> 0x280`.
4. Do not duplicate child deletion in the object. The manager owns ordinary range slot
   release, while the collapse operation retains its existing timed rider ejection and
   self-delete; verify referenced internal bridge pieces do not survive root removal.

### 2.3 MHZ Swing Vine characterization, then correction if reachable

1. Add a focused grabbed/off-screen test that exercises camera forcing and the root's
   coarse-back cull decision together.
2. If the real reachable state can cross the native boundary while a player is grabbed,
   change the root to non-persistent with the fixed `$280` anchor predicate, preserving
   `onUnload()` player-control cleanup and child-chain ownership.
3. If camera forcing makes the contradictory state unreachable, retain current production
   behavior and record the negative result in the audit rather than adding an artificial
   setup-only change.

Focused verification:

```bash
mvn -Dmse=off \
  -Dtest=TestAiz2BossEndSequenceObjects,TestMhzSwingVineObjectInstance,TestObjectManagerCounterBasedDynamicUnload,TestRewindCoverageGuard,TestStaticStateRewindCoverageGuard \
  test
```

Remeasure AIZ and MHZ after the candidate; a lifetime fix need not move the current early
frontier, but it must not worsen counts or move a previously matching field earlier.

## S3K route pipeline ownership

Tasks 3 through 5 are strictly serial: AIZ, merge/review/remeasure, then HCZ,
merge/review/remeasure, then MHZ. They must not run as concurrent edits because HCZ and
MHZ may both touch `Sonic3kObjectArtProvider`, and all three can affect shared placement or
transition code.

For each route, use a short-lived isolated worktree/branch created from the then-current
`bugfix/ai-handover-followups` tip:

| Route | Worktree | Local branch | Exclusive production ownership |
|---|---|---|---|
| AIZ | `.worktrees/handover-aiz` | `bugfix/ai-handover-aiz` | AIZ event, cutscene, and transition owner selected by triage |
| HCZ | `.worktrees/handover-hcz` | `bugfix/ai-handover-hcz` | StarPost, HCZ water-wall/geyser, or sidekick owner selected by triage; may own `Sonic3kObjectArtProvider` |
| MHZ | `.worktrees/handover-mhz` | `bugfix/ai-handover-mhz` | entry-ring/placement owner selected by triage; may own `Sonic3kObjectArtProvider` only after HCZ integration |

Each pipeline has three explicit handoffs:

1. **Triage:** read-only reproduction and instrumentation; name the causal or independent
   owner, exact files, focused failing test, and expected frontier effect. If evidence selects
   a file outside the candidate list or changes an architectural assumption, amend the design
   and plan and rerun their review gates before Fix.
2. **Fix:** test-first production change confined to the named owner; local focused tests and
   self-review.
3. **Verify:** an independent reviewer checks ROM citations, hard-rule compliance, changed
   files, focused tests, route replay, and cross-route counts. Blocking findings return to Fix.

After Verify is green, merge the local route branch into `bugfix/ai-handover-followups`,
rerun the three isolated route replays, remove the clean route worktree, and delete the fully
merged local route branch. Route branches are never pushed.

## Task 3: Resolve or re-characterize the AIZ direct `#35` frontier

Likely files, selected only after measurement:

- `src/main/java/com/openggf/game/sonic3k/events/Sonic3kAIZEvents.java`
- `src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesAiz1Instance.java`
- `src/main/java/com/openggf/level/LevelActTransitionExecutor.java`
- `src/test/java/com/openggf/game/sonic3k/events/TestSonic3kAIZEvents.java`
- the existing AIZ cutscene/transition focused test nearest the demonstrated owner

The Triage handoff replaces this candidate list with one exact exclusive ownership set before
Fix begins.

Investigation gate:

1. Rerun the complete segment and extract every queue group at frames 1106-1238,
   6216-6288, and 6300 through the terminal edge.
2. Instrument, without committing diagnostics, these native lifecycle boundaries:
   - Knuckles intro-exit completion and allocated title-card dispatch;
   - forced camera snap and the first transient `$1400` crossing;
   - `serviceAiz1MainLevelArt` claim/submission;
   - seamless AIZ1-to-AIZ2 mutation and transition completion.
3. Compare engine transitions to the fixture's physics/aux rows and the corresponding
   `sonic3k.asm` dispatch order. Do not infer producer time from an end-of-frame camera row
   alone.
4. Establish whether direct `#35` is causally downstream of the same transition skew or is
   an independent repeated producer edge.

Implementation gate:

- Add a focused failing lifecycle test for the measured ROM boundary before changing code.
- Correct only the owner that collapses or delays a native dispatch: event, allocated object
  turn, transition sequencing, or placement cursor as the evidence shows.
- Do not gate on trace frame, route identity, zone name in shared code, or recorded edge.
- Do not change `HardwareTimingService`, `HardwareTimingReplayPort`, or fixture data.

Terminal condition:

- Advance/remove `#35` without new regressions, or publish an exact negative result with
  the demonstrated causal chain, unchanged baseline, and next safe owner.

## Task 4: Resolve or re-characterize the HCZ direct `#90` frontier

Likely files, selected only after measurement:

- `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kStarPostObjectInstance.java`
- `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtProvider.java`
- `src/main/java/com/openggf/game/sonic3k/objects/HCZWaterWallObjectInstance.java`
- sidekick control owner identified from the frame-3253 report
- corresponding existing StarPost, HCZ water-wall, and sidekick tests

The Triage handoff must choose exactly one causal/independent owner set and its focused test
before Fix begins. `Sonic3kObjectArtProvider` cannot be assigned elsewhere during this task.

Steps:

1. Confirm fixture direct `#90` is the Stars3 child of module `28a69b8f...` and map the
   complete engine producer call chain into `spawnBonusStars()`.
2. Dump the StarPost object/contact and P1/P2 state from before frame 3253 through raw 3342.
3. Determine whether the earlier Tails motion divergence changes StarPost contact or whether
   the missing Stars3 submission is independent. Also compare the second geyser enemy-art
   reload because it can shift object/sidekick lifecycle without being the StarPost owner.
4. Add a focused test at the first causal production boundary, implement the smallest
   ROM-derived correction, and remeasure.

Terminal condition:

- Advance/remove `#90`, or record exact evidence that the edge remains downstream of an
  unresolved owner with unchanged baseline. Never synthesize the Stars3 work from timing
  data.

## Task 5: Resolve or re-characterize the MHZ direct `#335` frontier

Likely files, selected only after measurement:

- `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kSSEntryRingObjectInstance.java`
- `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtProvider.java`
- placement/slot or sidekick owner established by instrumentation
- `src/test/java/com/openggf/game/sonic3k/objects/TestSonic3kSSEntryRingFormation.java`
- relevant placement/lifetime tests

The Triage handoff must name the exact entry-ring/placement owner and focused test before Fix
begins. `Sonic3kObjectArtProvider` is available only after the HCZ branch is integrated and
cleaned up.

Steps:

1. Compare the three repeated ROM explosion-art submissions (`#334` at 1670, `#335` at
   7221, and `#336` at 7986) by entry-ring identity, slot, retirement reason, and producer
   call path.
2. Determine whether the later ring is absent because its placement never occurs, it
   retires through a different branch, or the earlier slot/RNG divergence changes the route.
3. Use the persistence audit result to rule the Swing Vine in or out; do not assume it
   explains pollen/bouncing-ring slot drift.
4. If an independent ROM-owned entry-ring lifecycle defect is isolated, add a focused test
   and fix it even if the frame-3420 ring comparator remains red. Otherwise publish the
   exact negative result and next safe owner.

Terminal condition:

- Advance/remove `#335`, or record an evidence-backed negative with unchanged baseline.
  The timing layer must not create the missing repeated work.

## Task 6: Regression, documentation, and review

Documentation files:

- `CHANGELOG.md` for any `src/main` fix.
- `README.md`, updated on the feature branch after the final behavior/validation result so
  the merge into `develop` stages the required release/change-log summary.
- `docs/status/trace-frontier-log.md` whenever a frontier, count, or interpretation changes.
- `docs/architecture/validation/trace/2026-08-02-handover-followups-validation.md`
- `docs/architecture/validation/trace/2026-08-02-handover-followups-integration.md`
- `docs/architecture/validation/trace/2026-08-02-handover-followups-end-to-end-review.md`

Focused authority/core batch:

```bash
mvn -Dmse=off \
  -Dtest=TestHardwareTimingAuthorityGuard,TestHardwareTimingReplayPort,TestHardwareTimingService,TestLevelIterationHardwareTimingAdmissionOrder,TestS3kKosDecompressionQueue,TestS3kKosModuleQueue,TestS3kKosStructuralSequence,TestS3kHardwareTimingReplay \
  '-Ds3k.rom.path=<repo>/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

S3K owner/keep-green batch:

```bash
mvn -Dmse=off \
  -Dtest=TestSonic3kAIZEvents,TestSonic3kHCZEvents,TestHCZWaterWallObjectInstance,TestS3kSignpostInstance,TestS3kResultsScreenObjectInstance,TestSonic3kSSEntryRingFormation,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestRewindCoverageGuard,TestStaticStateRewindCoverageGuard \
  '-Ds3k.rom.path=<repo>/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

Trace batch, one invocation per class:

```bash
mvn -Ptrace-replay -Dmse=off -Dtest=TestS3kAizCompleteRunTraceReplay \
  '-Ds3k.rom.path=<repo>/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
mvn -Ptrace-replay -Dmse=off -Dtest=TestS3kHczCompleteRunTraceReplay#replayMatchesTrace \
  '-Ds3k.rom.path=<repo>/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
mvn -Ptrace-replay -Dmse=off -Dtest=TestS3kMhzCompleteRunTraceReplay \
  '-Ds3k.rom.path=<repo>/Sonic and Knuckles & Sonic 3 (W) [!].gen' test
```

Use this exact JDK 21 command unchanged at all three full-suite gates:

```bash
mvn -Dmse=off \
  '-Dsonic1.rom.path=<repo>/Sonic The Hedgehog (W) (REV01) [!].gen' \
  '-Dsonic2.rom.path=<repo>/Sonic The Hedgehog 2 (W) (REV01) [!].gen' \
  '-Ds3k.rom.path=<repo>/Sonic and Knuckles & Sonic 3 (W) [!].gen' \
  test
```

Before integration, fetch and fast-forward the main-workspace `develop` branch without
overwriting uncommitted files. Run the command first on the updated main-workspace baseline,
then in the development worktree, recording exact Surefire failures for both. After merge,
run the same command a third time on `develop` and compare exact failures. A pre-existing red
baseline is acceptable; any new or worsened failure is not.

### Whole-delivery review gate

After code, tests, audit, validation, frontier log, changelog, and README are final, produce
the End-to-End Review artifact listed above. An independent reviewer must check:

- every requirement and acceptance criterion against a changed file or recorded result;
- architecture consistency and hard-rule-4 confinement;
- code quality, ROM citations, test-first evidence, documentation, and commit policy;
- exact focused/trace/full-suite outcomes and baseline comparisons;
- unresolved risks, explicit deferrals (including the atomic parameter rename), and the
  final human-review checklist.

Fix every blocking finding and rerun the independent review until it reports no blocker.
Only then proceed to integration.

## Task 7: Atomic rename deferral

Do not change the `frameCounter` parameter in this branch. Record in validation that
`ObjectExecutionController` supplies `ObjectManager.vblaCounter()` while approximately
590 update implementations use the misleading name. The dedicated future change must:

1. begin from a quiet tree;
2. rename the interface and every override/use to `vIntRunCount` atomically;
3. assess local bridges such as `resolveVIntRunCount` after the hierarchy rename;
4. compile before running focused and full tests;
5. avoid combining the rename with behavior changes.

## Commit and integration sequence

1. Commit the reviewed design and implementation plan with the implementation changes;
   include all required trailers and never bypass hooks.
2. Complete the serial AIZ, HCZ, and MHZ Triage -> Fix -> Verify pipelines, integrating and
   cleaning each route branch before starting the next.
3. Obtain an independent code/disassembly review for every changed S3K object or event owner;
   repeat until no blocking issue remains.
4. Produce the validation report, README release summary, and green End-to-End Review.
5. Fetch and fast-forward `develop`, record its full-suite baseline, merge the development
   branch into the main workspace without switching branches, and run the post-merge suite.
6. Complete the integration report with the merge commit, exact regression comparison,
   upstream/conflict reconciliation, and push result.
7. Push only `develop`.
8. Verify the worktree is clean/merged, remove it, delete the fully merged local feature
   branch, and prune worktree metadata.
