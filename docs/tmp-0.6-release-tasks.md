# 0.6 Release Blocker Scratch Tracker

Temporary execution tracker for the current release-gate pass. Do not treat this as release documentation.

## Already Completed

- [x] Restore the three previously-green S2 trace gates after `cpu_present` aux comparison regressed legacy/non-regenerated traces.
  - Commit: `9d3065466 fix(trace): restore S2 native prelude gates`
  - Verification: focused bootstrap/unit tests and EHZ1/SCZ/WFZ trace gates passed locally before push.
- [x] Reproduce the current `TestS3kAizTraceReplay` failure and confirm whether the first divergence is still the frame-3074 sidekick `Status_Push` bypass.
  - Current result: after `6b89814fe`, `mvn "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay" "-DfailIfNoTests=false" test` is RED with 4 failures.
  - First release-blocking divergence is now frame 2679: `tails_cpu_respawn_counter expected=0x0031 actual=0x0000`.
  - The older frame-3074 `Status_Push` bypass is still expected later, but it is no longer the first frontier.
- [x] Advance the AIZ sidekick frontier through the earlier render/push/input failures.
  - Local uncommitted progress: render flag/despawn-counter behavior, frame-3074 `Status_Push` bypass, object-order push input sampling, delayed jump-press reconstruction, and AIZ ending-pose control-lock timing now move the focused AIZ release trace to green.
  - Verification: `mvn "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtrace.context.radius=8" test` exited 0 with `MSE:OK modules=1 passed=181 failed=0 errors=0 skipped=0`.
- [x] Audit lag-frame trace execution handling across S1, S2, and S3K.
  - S1/S2: `gameplay_frame_counter` advancement is the authoritative full-level-frame signal; a counter plateau is classified as `VBLANK_ONLY` even when checked-in trace `vblank_counter` data is not useful.
  - S3K: `lag_counter`-only rows are classified as `VBLANK_ONLY`, while a `gameplay_frame_counter` delta wins over a simultaneous lag-counter delta.
  - Regression coverage now explicitly checks S1/S2 lag-counter rows as `VBLANK_ONLY` and verifies that S1/S2/S3K `gameplay_frame_counter` advancement wins over simultaneous lag-counter advancement.
  - Verification: `mvn "-Dtest=com.openggf.tests.trace.TestTraceExecutionModel,com.openggf.tests.trace.TestS3kSyntheticV3Fixture,com.openggf.tests.trace.TestS2SyntheticV3Fixture" test` exited 0. Surefire XML recorded `TestTraceExecutionModel` 23/0, `TestS2SyntheticV3Fixture` 1/0, and `TestS3kSyntheticV3Fixture` 1/0. MSE still printed the repository's known aggregate AIZ trace-gate failure; this was a focused lag-frame verification, not a full release sweep.

## Active Release Blockers

- [x] Model the S3K sidekick render-flag/despawn-counter behavior exposed by AIZ frame 2679.
- [x] Model the S3K sidekick push-bypass behavior from disassembly without trace hydration, zone carve-outs, or route/frame exceptions.
- [x] Model the S3K AIZ boundary/kill sidekick CPU transition exposed by AIZ frame 4679 without trace hydration, zone carve-outs, or route/frame exceptions.
- [x] Run and record the requested S2+S3K sidekick trace baseline once the focused sidekick path is stable enough to produce meaningful data.
  - Command: `mvn "-Ds2.rom.path=Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay,com.openggf.tests.trace.s2.TestS2SczLevelSelectTraceReplay,com.openggf.tests.trace.s2.TestS2WfzLevelSelectTraceReplay,com.openggf.tests.trace.s3k.TestS3kAizTraceReplay,com.openggf.tests.trace.s3k.TestS3kCnzTraceReplay,com.openggf.tests.trace.s3k.TestS3kMgzTraceReplay" test`.
  - Result: expected RED for the combined baseline, `MSE:TESTS total=217 passed=206 failed=10 errors=1 skipped=0`.
  - Green sidekick gates in that run: S2 EHZ1, S2 SCZ, S2 WFZ, and S3K AIZ.
  - Current S3K sidekick route blockers: CNZ full replay input-alignment error at frame 39672, MGZ full replay input-alignment error at frame 33271, and CNZ focused assertions around miniboss push-bypass/hurt-latched input, spring/terrain collision, arena camera clamp, and CNZ2 slot-pressure/object-order parity.

## Current Verification

- [x] Full non-ROM Maven suite: `mvn test` exited 0 with `MSE:OK modules=1 passed=7372 failed=0 errors=0 skipped=9`.
- [x] Focused S3K AIZ release trace: `mvn "-Dtest=com.openggf.tests.trace.s3k.TestS3kAizTraceReplay#replayMatchesTrace" "-Ds3k.rom.path=Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtrace.context.radius=8" test` exited 0.
- [x] Refreshed S2+S3K sidekick baseline after the logical jump-press cleanup; substantive result is unchanged from the baseline above.

## High Findings To Fix

- [x] Implement the CNZ2 post-capsule route end through the ROM cannon launch into ICZ1.
  - Local change: CNZ egg capsule now uses shared upright capsule button/results behavior, reports results completion back to the CNZ end-boss controller, spawns `Obj_CNZCannon` at the ROM handoff, forces the launcher jump input after the ROM wait, and requests ICZ1 only after the camera/Y offscreen threshold.
  - Verification: `TestS3kCnzTeleporterRouteHeadless` passed, 5 tests / 0 failures.
- [x] Prevent the CNZ2 -> ICZ1 frozen fade from showing or carrying cannon-launch state.
  - Commit: `6b89814fe fix(s3k): stabilize CNZ ICZ handoff diagnostics`
  - Verification: `TestS3kCnzTeleporterRouteHeadless` passed, 5 tests / 0 failures.
- [x] Prevent completionist big-ring crash when all emeralds are collected and the current code requests out-of-range `ZONE_HPZ`.
  - Local change: all HPZ-routed big-ring touches fall back to the implemented 50-ring/delete path until HPZ has registered `LevelData`.
  - Verification: `TestSonic3kSSEntryRingFormation` passed, 11 tests / 0 failures.
- [x] Prevent editor saves from baking gameplay event terrain mutations, such as the AIZ intro terrain swap, into user edit files.
  - Local change: `MutableLevel` now keeps editor-save snapshots separate from live runtime mutations, and `MutableLevelMutationSurface` uses runtime mutation APIs that dirty/redraw the level without changing the persisted editor-save payload.
  - Verification: `mvn "-Dtest=com.openggf.editor.persistence.TestEditorSaveManager,com.openggf.level.TestMutableLevelBaselineTracking,com.openggf.game.TestZoneLayoutMutationPipeline,com.openggf.game.mutation.TestZoneLayoutMutationPipelineNoRedraw" test` exited 0. Surefire XML recorded 27 tests / 0 failures / 0 errors across the selected slice.

## Carried Mediums

- [x] Render the stored data-select error message instead of only recording it.
  - Local change: S3K data-select presentation carries the stored launch error into save-screen object state, and the renderer draws a clipped, safe-glyph message on the save screen.
  - Verification: `mvn "-Dtest=com.openggf.game.sonic3k.dataselect.TestS3kDataSelectPresentation" test` exited 0 with 69 tests / 0 failures / 0 errors.
- [ ] Quarantine transient IO failures in `SaveManager` and configuration writes.
- [ ] Isolate configuration tests from process current-working-directory state.
- [ ] Remove or contain the `GumballMachine` static lifecycle hazard.

## Local Worktree Constraints

- Pre-existing modified files to avoid unless directly needed:
  - `src/main/java/com/openggf/level/LevelManager.java`
  - `src/main/java/com/openggf/level/objects/SkidDustObjectInstance.java`
- Pre-existing untracked review note:
  - `fable-arch-review.md`
