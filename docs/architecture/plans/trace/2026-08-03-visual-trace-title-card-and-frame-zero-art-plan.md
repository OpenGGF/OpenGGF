# Visual trace title-card and frame-zero dynamic-art implementation plan

Date: 2026-08-03

Design:
`docs/architecture/designs/trace/2026-08-03-visual-trace-title-card-and-frame-zero-art-design.md`

## Goal

Show the complete production title card before level-backed visual traces,
then start replay in a clean deterministic context whose first dynamic-art
comparison row matches the headless service-before-open lifecycle. Preserve
exact PLC/DPLC and hardware-timing comparison semantics for standalone traces
and complete runs.

## Task 1: Pin the deferred external-segment boundary

Files:

- Modify `src/test/java/com/openggf/game/resources/TestPlcFrameLifecycleCoordinator.java`
- Modify `src/main/java/com/openggf/game/resources/PlcFrameLifecycleCoordinator.java`

Steps:

1. Add a failing test that begins a dynamic-art run, requests deferred
   external ownership exactly where the real launcher does, then prepares an
   S1 player DPLC during the simulated `TraceReplayDriver.start()` level
   bootstrap, and finally drives one ordinary logical iteration.
2. Assert before implementation that the desired behavior is unavailable or
   fails: the S1 `submitted`/`completed` pair must be present in run-gap
   transitions, while the newly opened external generation publishes row zero
   with no edges and no outstanding transfer.
3. Add a failing cleanup test that closes the external controller before the
   first production claim and proves the pending window is cancelled without
   creating a generation or publication.
4. Implement an explicit deferred external-open state in
   `PlcFrameLifecycleCoordinator`:
   - share the existing validation/automatic-window close logic;
   - mark external ownership immediately;
   - service the first claim before opening the external window;
   - open before the iteration body/finish boundary;
   - provide a close operation that cancels a still-deferred open or closes an
     actual external window; and
   - clear deferred state when external management is released or the
     coordinator resets.
5. Run `TestPlcFrameLifecycleCoordinator` and retain all existing automatic,
   lag, overrun, and ownership-handoff behavior.

## Task 2: Pin clean presentation-context replacement

Files:

- Modify `src/test/java/com/openggf/TestEngine.java`
- Modify `src/test/java/com/openggf/TestGameLoop.java`
- Modify `src/test/java/com/openggf/TestTraceSessionLauncherFailureCleanup.java`
- Modify `src/main/java/com/openggf/Engine.java`
- Modify `src/main/java/com/openggf/TraceSessionLauncher.java`
- Add `src/main/java/com/openggf/game/session/VisualTraceReplayContextHandoff.java`

Steps:

1. Add a failing test around a gameplay context with:
   - a non-empty retained module rewind adapter standing in for S1/S2 PLC
     state;
   - active context-owned dynamic-art/runtime lifecycle state; and
   - a completed/resettable title-card provider.
2. Invoke the proposed Engine-owned trace replay-context reopen seam and
   assert that:
   - the old context is destroyed;
   - the title-card provider is reset;
   - every retained module rewind adapter receives
     `resetForMissingSnapshot()`;
   - the new context uses the requested live/recorded hardware admission;
   - production managers are attached and rebound to `GameLoop`;
   - `Engine` caches the replacement context's `LevelManager`,
     `SpriteManager`, and `Camera` used by `Engine.draw()`; and
   - no presentation dynamic-art state survives.
   Assert separately that the disposable presentation context uses `LIVE`
   admission and the replacement uses the requested `RECORDED` policy.
3. Keep `GameLoop` free of lifecycle replacement logic. Add a narrow
   package-owned `Engine.reopenCurrentGameplayForVisualTrace(...)` static
   composition action that validates the active Engine internally without
   exposing it to the launcher. Have it delegate through the instance-owned
   `reopenGameplayForVisualTrace(...)` seam to
   `VisualTraceReplayContextHandoff`, passing the loop's provider-reset
   callback and `Engine::bindGameplayMode`. The collaborator
   uses `SessionManager.reopenGameplaySession`, `GameplaySessionFactory`, and
   the current world-session module. Reset retained adapters before destroying
   their registered presentation context, reset and invalidate the cached
   title-card provider, configure fresh game-state special-stage progress, and
   bind the resulting context through the normal Engine seam. Do not mutate
   the legacy `GameModuleRegistry` compatibility state.
4. Change the launcher after-step handoff to require an active loop and call
   the narrow Engine-owned static composition action rather than acquiring
   the Engine singleton or passing `GameLoop::setGameplayMode` directly.
   Preserve the existing launch-failure abort path if the engine is absent or
   replacement fails.
5. Add replay-bootstrap failure tests that begin title-card presentation and
   then prove (a) a missing active `Engine` and (b) an exception from the
   Engine-owned reopen seam both clear the active trace, restore launch
   configuration/admission state, and return to the picker/master-title path
   when a loop is still available.
6. Run the focused handoff tests in `TestEngine`, `TestGameLoop`, and
   `TestTraceSessionLauncherFailureCleanup`.

## Task 3: Pin the title-card launch phase

Files:

- Add `src/main/java/com/openggf/trace/replay/VisualTraceLaunchPhase.java`
  if a small state owner keeps the launcher branch testable; otherwise keep an
  equivalent package-private state owner in `TraceSessionLauncher`
- Add `src/test/java/com/openggf/trace/replay/TestVisualTraceLaunchPhase.java`

Steps:

1. Write failing state tests proving:
   - a presentation phase does not admit replay while mode is `TITLE_CARD`;
   - returning to `LEVEL` is insufficient while the overlay remains active;
   - `LEVEL` plus a complete overlay admits replay exactly once; and
   - the phase owns early exit before a comparator exists.
2. Implement the minimal state machine with explicit presentation,
   bootstrapping, active, and terminal/aborted transitions. It must carry no
   trace frame, expected edge, gameplay state, or recorded hardware data.
3. Run the focused phase tests.

## Task 4: Integrate two-phase launch and first-window ownership

Files:

- Modify `src/test/java/com/openggf/TestTraceSessionLauncherRunBranch.java`
- Modify `src/test/java/com/openggf/TestTraceSessionLauncherFailureCleanup.java`
  as needed
- Modify `src/test/java/com/openggf/trace/live/TestLiveTraceComparatorObserver.java`
- Modify `src/main/java/com/openggf/TraceSessionLauncher.java`
- Modify `src/main/java/com/openggf/trace/live/LiveTraceComparator.java`
- Modify `src/main/java/com/openggf/LevelIterationAdmissionController.java`
  only if Escape ownership needs a narrow mode predicate update

Steps:

1. Update/add launcher tests before production edits to prove:
   - the game-bootstrap callback loads segment zero with the recorded team,
     consumes the pending automatic request, calls the production
     `enterTitleCard`, and leaves its provider initialized/advancing through
     `TITLE_CARD` rendering;
   - playback, comparator, hardware replay, HUD/ghost, and external
     dynamic-art ownership are all absent during that visible presentation;
   - the first segment-controller open requests deferred ownership and has no
     open dynamic-art segment until the first claim;
   - that first claim publishes atomic empty row zero after servicing a
     prepared S1 transfer into the gap;
   - a later run-segment open remains immediate;
   - abort before the first claim cancels deferred ownership and automatic
     ownership resumes;
   - title-card presentation owns Escape even with a null comparator; and
   - existing pending S2 work is retired rather than dropped, with its
     pre-segment completion remaining outside comparison row zero.
2. Refactor standalone and run game-bootstrap callbacks to begin a title-card
   presentation rather than immediately creating `LiveFixture` and
   `TraceReplayDriver`:
   - initial master-title gameplay admission is explicitly `LIVE`;
   - reset/load the requested segment-zero level and recorded team;
   - consume the automatic request and enter the production title card
     immediately;
   - make the session active with no playback/comparator/external segment.
3. In the all-mode after-step hook, wait until mode is `LEVEL` and the title
   provider reports complete. Transition once to replay bootstrap, reopen the
   clean context with the stored trace admission policy, then execute the
   existing standalone or run launch body.
4. Change the first `DynamicArtSegmentController` open to use deferred
   ownership. Its close delegates to the coordinator so an early abort can
   cancel the pending open. All subsequent opens call the normal immediate
   `DynamicArtLifecycleService.openComparisonSegment` path.
5. Arm a one-shot comparator authorization only for the launcher's deferred
   initial window. Allow the pre/post production check to recognize only that
   shape: expected row zero, an unpublished before snapshot, and row zero
   published in the exactly adjacent generation with a newer delivery serial.
   Consume authorization on the first publication attempt. Retain exact edge
   and outstanding-transfer comparison, with failing tests for generation
   skips, nonzero rows, stale delivery serials, published-before snapshots, and
   attempted authorization reuse after a stable first publication.
6. Preserve current comparator, HUD, ghost, rewind, complete-run coordinator,
   gap-journal, failure-status, and teardown setup after replay bootstrap.
   Special-stage launch remains direct and marks the visual phase active.
7. Extend early-exit handling so the presentation phase aborts cleanly without
   requiring a comparator.
8. Run the launcher, failure cleanup, run coordinator, dynamic-art lifecycle,
   GameLoop, and `TestEngineRenderDispatcher` focused suites. The render
   dispatcher assertion must retain `TITLE_CARD` routing to the title-card draw
   action while the new launcher integration test proves that the launcher
   actually enters that mode.

## Task 5: User-facing and trace-frontier documentation

Files:

- Modify `CHANGELOG.md`
- Modify `README.md`
- Modify `docs/status/trace-frontier-log.md`

Steps:

1. Record that visual level traces now show the complete title card before
   deterministic replay begins and that S1 frame-zero DPLC comparison follows
   headless service ordering.
2. Record the exact S1 GHZ1 headless command/result and the visual-only
   regression/fix context in the trace frontier log. Do not claim a gameplay
   frontier moved.
3. Include the reviewed design and plan in the delivered commit.

## Task 6: Verification, review, and integration

1. Run focused non-ROM tests:

   ```bash
   mvn -Dmse=relaxed \
     -Dtest=com.openggf.game.TestProductionSingletonClosureGuard,com.openggf.game.resources.TestPlcFrameLifecycleCoordinator,com.openggf.TestEngine,com.openggf.TestTraceSessionLauncherRunBranch,com.openggf.TestTraceSessionLauncherFailureCleanup,com.openggf.TestGameLoop,com.openggf.trace.replay.TestVisualTraceLaunchPhase \
     test
   ```

2. Run relevant dynamic-art/visual launch suites discovered from the touched
   code, including special-stage launch regression coverage.
3. Run `TestS1Ghz1TraceReplay` and
   `TestS1Ghz1CompleteRunTraceReplay` with the verified S1 REV01
   ROM. Run representative S2 EHZ1 and an S3K hardware-timed run/trace canary
   because launch admission and external segment zero are cross-game.
4. Run `TestProductionSingletonClosureGuard` explicitly alongside the
   architecture/authority guards for hardware timing, PLC comparison-only
   behavior, rewind coverage, and trace payload compression.
5. Run the complete Maven suite on JDK 21 and compare its exact result with an
   updated clean `develop` baseline as required by `AGENTS.md`.
6. Delegate the implementation diff for independent code review. Fix every
   valid issue and repeat until no blocking issue remains.
7. Fetch and fast-forward the main `develop` workspace without disturbing its
   user-owned changes. Rebase or merge the development branch onto that
   integration baseline as needed, rerun feature and full verification, merge
   into `develop`, update `README.md` for the required develop merge, run the
   post-merge regression comparison, and push only `develop`.
8. After verifying the feature branch is fully merged and its worktree has no
   user/unmerged changes, remove the worktree, delete the local feature branch,
   and prune worktree metadata.
