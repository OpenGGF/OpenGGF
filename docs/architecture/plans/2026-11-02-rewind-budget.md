# Rewind budget implementation plan

Approved design: consumable 10-second allowance (or Unlimited), refill scope Off / Per checkpoint / Per act / Per zone, default Per act. Life-loss refill defaults on and occurs after completed respawn. Bonus and special-stage toggles default on and each visit gets an independent fresh pool using the shared maximum; the main pool is preserved. Rewinding never refunds spending or repeats a boundary refill. Exhaustion immediately ends held/coasting rewind. A denied press displays the empty plate for one second, under Opus 5.5 creative direction.

## Implementation

- [x] Runtime policy and lifecycle integration: `LiveRewindBudget`, `LiveRewindManager`, completed respawn boundary in `LevelManager`. Charge actual successful backward steps, outside snapshots; preserve scripted/trace rewind semantics.
- [x] Settings: catalog/defaults/timing, Gameplay selector and indented dependent rows, English ARB labels/help, ordered positive budgets plus Unlimited. Retain legacy enable gate for launch profiles and tools.
- [x] Opus HUD: extend existing `TransportHud` plate and `LiveRewindHudOverlay`, one row plus 2 px meter, empty response with shape/border distinction, captures at 320/400 and rubric evidence.
- [x] Focused behavior tests: policy pools, boundary repeat prevention, life-loss refill, speed/coast exhaustion, denied press duration, settings persistence/ordering/dependencies, HUD states.
- [ ] Inspect change-based smoke selection, run appropriate combined smoke and required affected checks once. Inspect skips and exact failures, acknowledge diagnostics.
- [ ] Review final diff, integrate into main workspace develop without switching branch, push develop only, account for and remove merged task worktree/branch.

## Review focus

Preserve legacy disabled preferences and per-game overrides; unlimited is not unlimited history storage. No refill from rewind restoration, repeat checkpoints, generic reloads, or death animation. A settings edit cannot silently grant a pool through incidental history reinstall. Stage transitions must preserve the main allowance even when rewind is disabled for the visited stage. UI must remain clear of native HUD and simultaneous recording/status plates.

## Validation checkpoint

Pre-task integration base: `92dd2135fb9ac376644e8001c268a21d1c6c2cea`. Feature commit `acc1db940f27a01f708b88bfcaadbde261f22c87` and upstream reconciliation `57b41127a` are frozen for the change-based smoke run. Upstream through `a8f3f8647ed1e0920e7ee5119a8ce11a1d6a2ad6` is incorporated. The changelog conflict preserved both additions; subsequent guidance merged without a code conflict.

Completed focused selections passed 71, 126 and 47 tests respectively, with zero failures/errors/skips. They cover policy/real-manager boundaries, settings persistence and dependencies, stage toggles, HUD pixels and widths, audio cleanup, localization catalog usage, and host pacing. An explicit lean retry exhausted its 1 GiB compiler heap before tests ran; the required normal-memory rerun passed. The API snapshot adds exactly five appended configuration fields and removes none. Opus reviewed actual S2 320/400 HUD captures and scored overall 9 with no criterion regression; the dated HUD audit records settings, cross-game and real-loop witnesses separately.

The combined smoke command is:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 92dd2135fb9ac376644e8001c268a21d1c6c2cea --workers 2 --headless-gl surfaceless-egl --rom-directory <main-workspace> --run
```

Actual preflight passed. Selection is 4,397 candidate classes in 89 ordinary batches, followed by guards. Expected execution is about 30 minutes on the qualified two-worker host; shared admission has added substantial queue waiting. Full-suite is release-only and is not selected. Run `20261102T151818Z-9913df0f` remains active against `57b41127a`; this is pending coverage, not a pass claim.

Eight broad-run failures caused by the newly enabled default are repaired in the separate fixture branch `bugfix/ai-rewind-budget-fixtures` (`b7508d43e`). Mock Engine/GameLoop/native-stage fixtures explicitly disable rewind after their configuration reset because they supply no capture payload; the hub-chip assertion includes the approved default REWIND ON chip. Focused checks passed 48 tests across the four mock-related classes and six hub-chip tests, zero skips. Surefire XML confirms all eight original failing method identities passed. The repair is not yet merged into the frozen smoke tree.

Three other exact failures reproduce on unchanged baseline `a8f3f8647ed1e0920e7ee5119a8ce11a1d6a2ad6`, each with one test, one failure and zero skips:

- `TestGameLoopFreshLevelHandoff.realFadeCallbackKeepsItsOwnerAndDefersTheFourTitleArchives`: expected NONE but HOLD_BLACK remains. The native title-entry contract intentionally retains that state until the following entry.
- `TestEditorProjectLifecycle.cancellingNativeExitModalRestoresTheSameHeadlessPlaytestAndEditorView`: expected zero fade callbacks, observed one. Its input helper retains the pressed logical snapshot after physical release.
- `TestMasterTitleScreenAudio.cancellingRomLocationEmitsCancel`: expected UI_NAVIGATE/UI_CONFIRM/UI_CANCEL, observed UI_NAVIGATE/UI_CONFIRM.

Candidate and baseline assertion messages match. These unrelated failures are preserved. Final broad results, skips, guard coverage, diagnostic acknowledgment, integration, push and cleanup remain pending.

Batch 28 exposed an invocation prerequisite error: despite the explicit surfaceless EGL backend, the broad process inherited `DISPLAY=:0`. Eight cases in `TestSonic2NativeCompetitionHost`, `TestSonic2InterlacePixels` and `TestCompetitiveHalfpipeEgl` stop at their absent-DISPLAY assertion. A focused rerun in the verified repair tree explicitly removes DISPLAY and WAYLAND_DISPLAY; it is pending. These failures are not attributed to the feature or counted as passes. Any further affected cases require the same bounded environment-corrected verification.

The environment-corrected selection passed all eight cases, zero skips, on `b7508d43e` (documentation edits only beyond that source). Original failing method identities were checked in Surefire XML. Queue wait was 573 seconds; Maven execution was 39.935 seconds. Command: `env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dtest=TestSonic2NativeCompetitionHost,TestSonic2InterlacePixels,TestCompetitiveHalfpipeEgl -Dsonic2.rom.path=<absolute-original-S2-ROM> test`.

Later mod checks found two tower-defense input assertions and nine Three Islands/lobby assertions that also reproduce on baseline. The exact tower-defense pair ran 2 tests/2 failures/zero skips (296-second queue wait; 32.134-second Maven execution). The next exact selection ran 11 tests/9 failures/zero skips (6-second wait; 43.198-second execution): all eight Three Islands failures and the lobby pixel failure match, normalizing only JVM object-identity suffixes in one expected-FieldScreen/actual-StoryScreen assertion. Both standalone startup methods passed on baseline. Total inherited failure identities are now 14; they remain unchanged.

The two standalone startup failures are feature regressions: enabling live rewind by default exposed the native level adapter's existing AbstractLevel COW-geometry requirement when a creator-owned Level is wrapped in an owner-bound proxy. A focused candidate reproduction failed both methods with that exact capture stack. The repair gates native live history on compatible level geometry, preserving the creator boundary and allowing those games to launch; unsupported custom geometry does not get a native history controller. Held-rewind pacing uses the same support gate. Both real packaged startup tests now explicitly exercise the enabled default and assert that unsupported geometry leaves the native controller absent. Their focused regression plus manager ownership, budget/boundary and host-pacing checks is pending.

The first capability-gate regression run passed both standalone starts but failed one ownership invariant: disabled binding must not inspect the object world. The gate moved from mode classification into enabled history admission, with held-key pacing using the same compatibility test. A later native FBZ fixture still used the legacy boolean false profile constructor, whose defined meaning is DEFAULT; it now explicitly selects LaunchToggle.OFF for native parity. The corrected combined selection passed 58 tests, zero failures/errors/skips (557-second queue wait; Maven execution 1m42s), including the two real packaged startups, all four FBZ native teams, TestLiveRewindOwnerLifetime, TestGameLoopMutatorPacing, TestLiveRewindBudget and TestLiveRewindBoundaryPolicy. Source changes remain isolated from the frozen broad tree.
