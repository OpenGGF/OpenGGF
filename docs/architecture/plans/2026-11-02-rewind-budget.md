# Rewind budget implementation plan

Approved design: consumable 10-second allowance (or Unlimited), refill scope Off / Per checkpoint / Per act / Per zone, default Per act. Life-loss refill defaults on and occurs after completed respawn. Bonus and special-stage toggles default on and each visit gets an independent fresh pool using the shared maximum; the main pool is preserved. Rewinding never refunds spending or repeats a boundary refill. Exhaustion immediately ends held/coasting rewind. A denied press displays the empty plate for one second, under Opus 5.5 creative direction.

## Implementation

- [x] Runtime policy and lifecycle integration: `LiveRewindBudget`, `LiveRewindManager`, completed respawn boundary in `LevelManager`. Charge actual successful backward steps, outside snapshots; preserve scripted/trace rewind semantics.
- [x] Settings: catalog/defaults/timing, Gameplay selector and indented dependent rows, English ARB labels/help, ordered positive budgets plus Unlimited. Retain legacy enable gate for launch profiles and tools.
- [x] Opus HUD: extend existing `TransportHud` plate and `LiveRewindHudOverlay`, one row plus 2 px meter, empty response with shape/border distinction, captures at 320/400 and rubric evidence.
- [ ] Focused behavior tests: policy pools, boundary repeat prevention, life-loss refill, speed/coast exhaustion, denied press duration, settings persistence/ordering/dependencies, HUD states.
- [ ] Inspect change-based smoke selection, run appropriate combined smoke and required affected checks once. Inspect skips and exact failures, acknowledge diagnostics.
- [ ] Review final diff, integrate into main workspace develop without switching branch, push develop only, account for and remove merged task worktree/branch.

## Review focus

Preserve legacy disabled preferences and per-game overrides; unlimited is not unlimited history storage. No refill from rewind restoration, repeat checkpoints, generic reloads, or death animation. A settings edit cannot silently grant a pool through incidental history reinstall. Stage transitions must preserve the main allowance even when rewind is disabled for the visited stage. UI must remain clear of native HUD and simultaneous recording/status plates.

## Validation checkpoint

Base: `92dd2135fb9ac376644e8001c268a21d1c6c2cea`; working tree has the implementation and supporting settings/docs, not yet committed. First focused command selected TestLiveRewindBudget, TestLiveRewindOwnerLifetime, TestLiveRewindBoundaryPolicy, TestTransportHudPresentation, TestLiveRewindHudOverlay, TestEngineSettingsDraft and TestSettingTiming: 71 tests, zero failures/errors/skips. Queue wait 478 seconds; Maven execution 1m37s. A subsequent atomic legacy-gate settings fix is under focused verification; no pass is claimed for that edit yet. Preflight passes with Lua 5.4 selected explicitly. Change-based smoke selection is 4,396 candidates plus guards, expected about 30 minutes execution on the qualified two-worker host; queue wait is separate. Full-suite is release-only and will not run. Pixel captures/rubric, API pin regeneration, smoke, integration, push and cleanup remain pending.

Expanded focused check: 126 tests, zero failures/errors/skips (EngineSettingsDraft, EngineSettingsScreen, SonicConfigurationFileBootstrap, SonicConfigurationSessionOverrides, LaunchProfileApplier, SuspendedSettings, special/bonus/audio rewind managers, HUD widths/transport/recording and UI catalog usage). Queue wait 187 seconds; Maven execution 1m44s. The API snapshot adds exactly the five appended configuration fields and removes none. Opus reviewed actual S2 320/400 HUD captures and scored overall 9 with no criterion regression; see the dated HUD audit. A final focused pacing/API check is pending for the drained-held-key admission edge case. Upstream develop has advanced to `2557cf5b09c2bc4c4feff41540b3b4d606b3fa39`; reconcile before combined smoke.

The final pacing/API selection ran 48 tests with one new fixture error: `TestGameLoopMutatorPacing.livePolicyEditAndRestoreKeepFractionAndEdgesWhileModeFadeAndExternalInputStopThePump` called budget observation on a mock ready owner with no LevelManager. The pacing-only fixture now explicitly disables rewind; its held-rewind cases enable it themselves. The 24 real manager cases, 12 API-surface cases and drained-key host gate passed. The narrow fixed-fixture/UI rerun is pending. Upstream through `dc25d989be7f9a1a4ff14f44fb31b482bcd5b937` is incorporated; the sole changelog conflict preserved both additions.

Native title restarts can intentionally reuse their WorldSession. Both owning GameLoop `startNewGameFromTitle()` call sites now explicitly start a new budget run; ordinary context rebuilds and stage returns still preserve spending. Pure policy and real manager tests cover this distinction with life-loss refill Off. The pacing fixture's first override was cleared by `configureGameModuleFixture()`; it was moved after that reset. A normal-memory build prepares these final classes under the standard worktree/build-activity leases before the already queued lean pacing check executes. No test-admission rules or reservations changed.
