# Release Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate the sidekick CPU audit and release-review findings into one actionable remediation plan, then fix the confirmed issues according to OpenGGF's ROM-accuracy, trace, documentation, and release policies.

**Architecture:** Treat the temp audit files as source evidence, not shippable artifacts. Fix release-facing regressions first, then sidekick trace parity, then medium-risk framework correctness, then documentation and hygiene. Every behavior change needs a failing test first, ROM/disassembly citation where it affects parity, and focused verification before broader sweeps.

**Tech Stack:** Java 21, Maven, JUnit 5, OpenGGF trace replay fixtures, Sonic 1/Sonic 2/Sonic 3&K disassemblies under `docs/`, project branch-policy trailers.

---

## Source Inputs

- `SIDEKICK_CPU_AUDIT.tmp.md`: disassembly-complete audit of `SidekickCpuController`, `TailsRespawnStrategy`, playable history buffers, and signpost/end-of-act side effects.
- `RELEASE_REVIEW_FINDINGS.tmp.md`: release architecture/code review findings, including entries already fixed, refuted entries, backlog entries, and confirmed release-facing defects.
- Conversation remediation plan from 2026-06-11: prioritize release blockers, sidekick parity, medium-risk correctness, docs/release hygiene, and verification.

These `.tmp.md` files must remain uncommitted. This plan is the durable replacement artifact.

## Implementation Status Snapshot

Completed in `bugfix/ai-release-remediation`:

- RB-1/RB-2: gated release debug/cheat/art-viewer shortcuts on `debug.flags.debugView`; moved ring-bounds off F9.
- RB-3: made CNZ post-capsule music/control restoration one-shot.
- RB-4: separated default pause/P2 Start bindings, gated pause to gameplay modes, and added a visible pause indicator.
- RB-5/RB-6: fixed KEY digit parsing/writer round-trip and clamped invalid FPS values.
- RB-7: documented the missing game-over/continue flow and restored sane zero-life pause behavior as the release-safe interim.
- SK-2/SK-3/SK-4/SK-5: restored S2 control-lock logical-input latching, removed the grounded+pushing jump hold carve-out, split the S3K-only object-control nudge gate from S2, added PANIC hurt/dead CPU skip behavior, fixed PANIC equality-facing to match the ROM's no-carry branch, covered PANIC post-`CheckDespawn` latch plus S2/S3K dead-fall threshold behavior, and routed S2 dead-Sonic flight through the ROM routine-4 fly-in path with entry/landing side effects.
- SK-1 unit slice: S3K fresh routine-0 sidekick spawns now reset kinematics, advance to NORMAL for the next object tick without same-frame follow steering, and preserve dormant sentinel `object_control=$83` semantics.
- SK-3 live-push-in-water slice: S2 live `Status_Push` with delayed Sonic not pushing now takes the ROM push-bypass branch underwater without requiring S3K's AIZ reload pulse heuristic.
- SK-3 delayed-press slice: sidekick CPU replay now consumes the delayed ROM press byte directly so consecutive recorded A/B/C press bytes are preserved instead of being collapsed by engine edge reconstruction.
- SK-7 waterline slice: sidekick fly-in target clamping now reads `Water_Level_1` semantics through `WaterSystem.getGameplayWaterLevelY(...)`, so oscillated gameplay waterlines are honored instead of the base/current water register.
- SK-3 airborne handoff slice: S2 airborne live `Status_Push` plus delayed Sonic `Status_Push` now falls through to follow steering instead of taking a non-ROM skip branch.
- SK-3 panic pinball slice: S2 PANIC now ignores `pinball_mode` when `spindash_flag` is clear, while S3K AutoSpin keeps the engine `pinballMode` bridge through `PhysicsFeatureSet.sidekickPanicTreatsPinballModeAsSpindashFlag()`.
- SK-1 HCZ monitor-release slice: S3K monitor breaks now recover the ROM P2 standing bit from monitor-edge geometry, release CPU sidekicks into air when Sonic breaks the shell, and suppress the released sidekick's same-frame gravity tick. `TestS3kHczCompleteRunTraceReplay` moved from frame 125 `tails_air` to frame 374 main-player `y_speed`.
- MC-1: cleared `CollisionSystem.pendingOddSensorFallbackAngles` on reset and documented the right-wall heuristics.
- MC-2: corrected the reviewed MarkObjGone coarse-back checks for AIZ falling log, S3K signpost, S2 Conveyor, SlidingSpikes, Tornado, Nebula, and Cloud.
- MC-3: made the parallax compositing pass the sole owner of BG per-column VScroll so the AIZ fire-wave offset is not applied twice.
- MC-4: reset `PaletteOwnershipRegistry.paletteRotationDisabled` at level-load/act-transition zone-feature boundaries.
- MC-5: refactored S2 Cloud movement onto `SubpixelMotion`.
- MC-5 editor-save subset: transient editor-save read failures are non-destructive, editor saves fall back when `ATOMIC_MOVE` is unsupported, and wrong-length block/chunk states quarantine before any partial map edits apply.
- MC-6: bounded stalled trace-capture encoder shutdown so ffmpeg backpressure cannot hang stop forever.
- MC-7: skipped full level tilemap invalidation during live rewind restore when block, chunk, and map arrays are already the live references.
- MC-8: made deferred GL command groups snapshot caller command lists so reused fallback-render lists cannot alias across buckets.
- MC-9: cached resolved integer/key configuration values with invalidation on config mutation and derived display-aspect refresh.
- DOC-5: corrected stale S1 SBZ/FZ and LZ water-slide source comments that described implemented behavior as TODO/stub work.
- Save/data-select hardening: added `SaveSlotState.UNAVAILABLE` for transient read failures and preserved it through data select so unreadable saves cannot be overwritten as fresh slots.
- Palette fallback hardening: resolved underwater palette fallback lookups through feature-remapped zone/act keys so remapped zones use the same key for storage and reads.
- S3K ICZ startup palette ownership hardening: added a post-registry-reset level-load palette override hook so ICZ1's lock-on mountain palette keeps owner `s3k.icz.startupPalette` after zone-scoped registries are cleared.
- Display boundary hardening: guarded startup centering and post-resize integer-scale snapping when GLFW cannot report a monitor or video mode.
- Documentation/hook/architecture hygiene: removed stale S3K AIZ2 battleship and S2 latch discrepancy entries, corrected AGENTS/CLAUDE drift, added known-bug/changelog notes, fixed the PowerShell trailer parser no-space substring bug, replaced the two production raw construction-context set/clear call sites with scoped helpers, pruned stale object-service migration guard baselines, added `SessionManager` guard coverage, and removed stale mutation-routing allow-list entries.

Still outstanding:

- HCZ complete-run progression: frames 125, 374, 624, 896, 898, 940, 953, 957, 973, 974, 1020, 1581, 1671, 2486, 2501, 2635, 2801, 2894, 2976, 3066, 3124, 3318, 3355, 3850, 4286, 4403, and 4872 have been cleared or advanced by the release-remediation sidekick/object slices. The current HCZ first error is frame 5726 native-P2/Tails `y_speed` (`expected=-0490`, `actual=0x0490`) around the collapsing-bridge/monitor/air-count cluster. The frame-3850 native-P2 Tails rolling-state mismatch was resolved by using the ROM minimum roll-speed threshold for shared roll-stop, the frame-4286 main-player `y_speed` mismatch was resolved by honoring HCZ water-skim object-order gravity suppression, the frame-4403 one-pixel vertical/camera carry was resolved by preserving the ROM `y_sub` word during the HCZ water-skim surface pin, and the frame-4872 main-player X/camera frontier was resolved by preserving ROM `x_pos` during AutoSpin forced-roll width changes on wall modes.
- Rejected HCZ frame-896 hypothesis: button-local `isSolidFor` counters/underwater-entry deferrals and a broad shared first-frame render-flag lifecycle change were tested. Neither moved the HCZ trace; the shared lifecycle attempt regressed a focused `TestSolidObjectManager` boundary case, so both directions were removed. The accepted direction was ROM `SolidObjectTop_1P` boundary rejection plus `Obj_Button` same-frame trigger publication.
- Remaining SK-1 verification: S3K complete-run trace coverage for fresh sidekick spawn/init-only frame and dormant park semantics. HCZ frame-2894 sidekick follow-history jump-edge publication, frame-3318 conveyor release center preservation, frame-3355 conveyor coarse-back culling, frame-3850 native-P2 roll-stop, frame-4286 water-skim airborne gravity handoff, frame-4403 water-skim subpixel pin, and frame-4872 AutoSpin wall-mode X preservation are covered and advanced; HCZ now needs ROM-state triage of the frame-5726 native-P2/Tails vertical-velocity sign frontier, while ICZ/LBZ/MGZ complete-run coverage remains outstanding.
- Remaining sidekick audit backlog: complete-run SK-1 trace verification for ICZ/LBZ/MGZ, CNZ/MGZ input-alignment frontiers, MGZ complete-run ring mismatch, and ICZ frame-0 rolling mismatch. The former HCZ frame-2894 sidekick input frontier is now resolved.
- Lower-priority release-review hygiene that was not part of the release-blocker fix set.

## Non-Negotiable Rules

- No trace-to-engine hydration in committed replay code.
- No zone, route, frame, or "known failing trace" carve-outs for trace fixes.
- Shared behavior differences must be modeled as ROM state, object/profile state, or a `PhysicsFeatureSet`/equivalent feature flag.
- Object runtime assets must come from the user ROM, never from `docs/` fallback bytes.
- Tests use JUnit 5 only.
- If a trace frontier moves, update `docs/TRACE_FRONTIER_LOG.md` with command, context, result, and first-error frame/field.
- If object/badnik fixes reveal a reusable pitfall, update the relevant `.agents/skills/.../rom-pitfalls.md` and `.claude/skills/.../rom-pitfalls.md`.
- Use `CHANGELOG.md`, discrepancy docs, AGENTS/CLAUDE docs, and commit trailers honestly when touched behavior warrants it.

---

## Confirmed Release-Blocking Problem Statements

### RB-1: Gameplay debug and cheat keys are live in release configs

`debug.flags.debugView=false` is documented as disabling runtime debug keys, but gameplay-affecting keys are still polled in `GameLoop` and `SpriteManager`. This exposes give-emeralds, Super Sonic, free-fly debug mode, level/act skips, special/bonus-stage entry, checkpoint teleport, and level select in normal release play.

Impact: accidental keypresses mutate game state in shipped default config.

Primary files:
- `src/main/java/com/openggf/GameLoop.java`
- `src/main/java/com/openggf/sprites/managers/SpriteManager.java`
- `src/main/java/com/openggf/configuration/SonicConfigurationService.java`
- `CONFIGURATION.md`

Required fix:
- Gate gameplay-affecting debug key polling on `SonicConfiguration.DEBUG_VIEW_ENABLED`.
- Add guard tests proving keys are inert when disabled and still work when enabled.

### RB-2: F12 art-viewer toggle freezes gameplay invisibly in release configs

`DebugOverlayManager.updateInput` runs unconditionally and toggles `OBJECT_ART_VIEWER` with F12. `GameLoop` freezes the level tick while the art viewer is enabled, but the debug UI is gated by `DEBUG_VIEW_ENABLED`, so default release configs can appear hung after a screenshot/debug key collision.

Impact: visible release hang/freeze with no clear recovery affordance.

Primary files:
- `src/main/java/com/openggf/GameLoop.java`
- `src/main/java/com/openggf/debug/DebugOverlayManager.java`
- `src/main/java/com/openggf/debug/DebugOverlayToggle.java`
- `src/main/java/com/openggf/Engine.java`

Required fix:
- Do not process debug overlay toggles when `DEBUG_VIEW_ENABLED=false`, or make art-viewer freeze conditional on debug being enabled.
- Remove or resolve the F9 collision between ring-bounds toggle and level-select key.

### RB-3: CNZ post-boss controller repeats one-shot restoration every frame

After capsule results complete but before cannon spawn, `CnzEndBossInstance` repeatedly calls `restoreLevelMusic()` and `restorePlayerControl()`. This restarts CNZ2 music every frame and clobbers player rolling/control state through the post-boss walk window.

Impact: supported S3K CNZ->ICZ route has broken music and control state.

Primary file:
- `src/main/java/com/openggf/game/sonic3k/objects/CnzEndBossInstance.java`

Required fix:
- Make music/control restoration one-shot at the ROM-appropriate handoff.
- Add a focused test that would fail with repeated `playMusic`/control release calls.

### RB-4: Pause and Player 2 Start share ENTER by default

`input.pause` and `input.player2.start` both default to ENTER. Pause handling runs before sprite input, so P2 Start is eaten by the pause early-return. Pause also runs in menu modes with no visible indicator.

Impact: P2 sidekick takeover is unusable by default; menu screens can appear frozen.

Primary files:
- `src/main/resources/config.yaml`
- `src/main/java/com/openggf/configuration/SonicConfigurationService.java`
- `src/main/java/com/openggf/GameLoop.java`

Required fix:
- Change one default binding so pause and P2 Start do not collide.
- Gate pause to gameplay modes.
- Render a minimal pause indicator or otherwise make pause state visible.
- Add config/default and input-ordering tests.

### RB-5: Digit key config bindings and writer round-trip corrupt number-row keys

KEY values parse raw integers before GLFW key names, so `"1"` resolves to key code `1` instead of `GLFW_KEY_1=49`. The YAML writer emits key names unquoted, so a correct code 49 round-trips to bare `1` and becomes dead.

Impact: valid user key bindings silently break.

Primary files:
- `src/main/java/com/openggf/configuration/SonicConfigurationService.java`
- `src/main/java/com/openggf/configuration/ConfigYamlWriter.java`
- `src/main/java/com/openggf/control/InputHandler.java`

Required fix:
- Resolve GLFW key names before raw integer parsing for KEY config values, or special-case single digit key names.
- Quote key names that YAML would parse as scalars.
- Add load and save/reload round-trip tests for `0` through `9`.

### RB-6: `display.fps` accepts zero and negative values

`Engine.loop()` divides by `targetFps` with no validation. `fps: 0` throws on startup; negative FPS creates an uncapped busy loop.

Impact: user-editable config can crash or unthrottle the engine.

Primary files:
- `src/main/java/com/openggf/Engine.java`
- `src/main/java/com/openggf/configuration/SonicConfigurationService.java`
- `src/main/java/com/openggf/configuration/ConfigCatalog.java`

Required fix:
- Clamp or validate FPS to at least 1.
- Prefer general INT range validation metadata if small enough; otherwise clamp at read/constructor boundary and add tests.

### RB-7: No game-over/continue flow at zero lives

When lives reach zero, the engine respawns indefinitely and pause is disabled by the zero-lives pause guard. Continues are tracked but not consumed. This affects all three games.

Impact: core supported gameplay flow diverges significantly from ROM behavior.

Primary files:
- `src/main/java/com/openggf/game/GameStateManager.java`
- `src/main/java/com/openggf/GameLoop.java`
- `src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java`
- `docs/KNOWN_DISCREPANCIES.md`

Required fix:
- Implement the smallest ROM-faithful game-over/continue state flow that fits current architecture, or explicitly document the discrepancy if full implementation is deferred.
- At minimum, restore sane pause behavior if gameplay is intentionally allowed after zero lives.

---

## Sidekick CPU Trace-Parity Problem Statements

### SK-1: S3K sidekick lacks fresh Obj_Tails spawn and init-only first frame

S3K ROM creates a fresh `Obj_Tails` at every level load, zeros kinematics, runs `Tails_Init` on the first frame without physics/CPU, then enters routine `$00` on frame 2 and normal follow later. The engine currently preserves carried-in velocity and runs normal logic during `updateInit`.

Impact: live f1 frontiers in HCZ, ICZ, LBZ, and MGZ complete-run traces.

Primary files:
- `src/main/java/com/openggf/sprites/playable/SidekickCpuController.java`
- `src/main/java/com/openggf/level/LevelManager.java`
- `src/main/java/com/openggf/game/sonic3k/Sonic3kLevelEventManager.java`
- `src/test/java/com/openggf/tests/trace/s3k/*`

Required fix:
- Add S3K sidekick fresh-spawn reset at level load.
- Add init-only first sidekick frame semantics.
- Add ICZ/SSZ dormant park entry with `object_control=$83` semantics at the routine-0 boundary.
- Verify against S3K complete-run first-frame traces and must-keep-green S3K tests.

### SK-2: S2 `controlLockLatchesLogicalInput` is disabled despite forced-input bypass

S2 ROM latches `Ctrl_1_Logical` while `Control_Locked`; the engine has the latch and a forced-input bypass, but the S2 feature flag remains false after a prior regression workaround.

Impact: MTZ2 and other control-lock routes under-accelerate sidekick follow history.

Primary files:
- `src/main/java/com/openggf/game/PhysicsFeatureSet.java`
- `src/main/java/com/openggf/sprites/playable/AbstractPlayableSprite.java`
- `src/main/java/com/openggf/game/sonic2/objects/SignpostObjectInstance.java`

Required fix:
- Flip S2 flag only after focused red/green coverage demonstrates forced signpost input is not re-regressed.
- A/B EHZ1, MTZ2, WFZ, and SCZ trace tests.

### SK-3: Shared Tails CPU normal path contains ungated non-ROM carve-outs

The shared CPU code has S3K-trace-motivated or engine-artifact conditions applied to S2/S3K without feature gates or ROM state ownership:
- grounded+pushing `jumpingFlag` hold/clear carve-out,
- underwater push-pulse narrowing (covered by the SK-3 live-push-in-water slice and `sidekickPushBypassUsesGraceStatus`),
- airborne live/delayed push handoff (covered by the SK-3 airborne handoff slice),
- S3K-only obj_control bit-0 nudge gate applied to S2 (covered by `sidekickFollowNudgeBlockedByObjectControlBit0`),
- panic pinball-mode OR applied to S2 (covered by `sidekickPanicTreatsPinballModeAsSpindashFlag`).

Impact: plausible contributors to S2 ARZ/CNZ/HTZ/MTZ/OOZ sidekick frontiers and violates project trace rules.

Primary file:
- `src/main/java/com/openggf/sprites/playable/SidekickCpuController.java`

Required fix:
- Prefer modeling the stale ROM state root cause instead of compensating in shared CPU code.
- If a divergence is real per game, introduce a narrow feature flag with S2/S3K disassembly citations.
- Add focused CPU parity tests for each gate before changing production code.

### SK-4: Panic/dead/despawn paths diverge from ROM

PANIC originally lacked the hurt/dead CPU-skip gate and inverted equality-facing. The audit also flagged post-`CheckDespawn` latch writes and dead-fall thresholds; current code models those via `refreshPanicDiagnosticInputBeforeDespawn()` and the deferred dead-fall marker path, now covered by focused tests.

Impact: early despawns and diagnostic divergence in death/hazard-heavy traces.

Primary files:
- `src/main/java/com/openggf/sprites/playable/SidekickCpuController.java`
- `src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java`

Completed fix:
- Preserve the completed PANIC hurt/dead skip and equality-facing behavior with focused CPU parity tests.
- Cover PANIC timeout despawn continuing to expose the ROM-visible DOWN latch on the marker frame.
- Cover S2 `Tails_Max_Y_pos+$100` and S3K `Camera_Y_pos+$100` deferred dead-fall marker thresholds.

### SK-5: S2 dead-Sonic flight and fly-in landing omit ROM side effects

Dead-Sonic flight originally routed through S3K-like auto-recovery and missed S2 status wipes, spindash clear, water target clamp, high-priority inheritance, and landing animation/status writes.

Impact: post-death sidekick windows diverge.

Primary files:
- `src/main/java/com/openggf/sprites/playable/SidekickCpuController.java`
- `src/main/java/com/openggf/sprites/playable/TailsRespawnStrategy.java`

Completed fix:
- Route S2-style Tails dead-leader flight into the regular Tails fly-in strategy without running the respawn teleport.
- Restore S2 ROM side effects on dead-Sonic flight entry and landing: spindash clear, status wipe, water-clamped fly target path, Walk animation, priority, and solid-bit inheritance.
- Keep S3K on the existing auto-recovery path via `PhysicsFeatureSet.sidekickRespawnEntersCatchUpFlight()`.

---

## Medium-Risk Correctness Problem Statements

### MC-1: `CollisionSystem.pendingOddSensorFallbackAngles` is not reset or rewind-captured

The cross-frame odd-sensor fallback map is engine-invented, absent from `resetState`, and absent from rewind snapshots.

Required fix:
- Clear it in `CollisionSystem.resetState`.
- Move/capture/clear it on rewind seek, or document it in `docs/KNOWN_DISCREPANCIES.md`.

### MC-2: Seven objects hand-roll MarkObjGone coarse-camera checks incorrectly

Several S2/S3K objects omit the ROM `-0x80` coarse-back margin or masking. AIZ falling log and S3K signpost are on supported S3K content.

Required fix:
- Replace hand-rolled checks with `AbstractObjectInstance.isInRangeAt` or a shared coarse-back helper.
- Prioritize AIZ falling log and S3K signpost, then S2 Conveyor, SlidingSpikes, Tornado, Nebula, and Cloud.

### MC-3: Per-column BG VScroll is applied twice

The VScroll texel-count fix corrected sampling, but an existing path still applies per-column BG VScroll both in the BG tile pass and the parallax scroll pass.

Required fix:
- Determine ownership of per-column VScroll and remove the duplicate application.
- Verify standard and widescreen rendering tests.

Status:
- Completed in `bugfix/ai-release-remediation`: BG per-column VScroll is owned by the parallax compositing pass, with a source-level regression test preventing the BG tile FBO pass from consuming the same buffer.

### MC-4: Palette rotation disable flag can leak across level reload

`PaletteOwnershipRegistry.paletteRotationDisabled` survives frame clears and only resets on full gameplay context teardown.

Required fix:
- Reset at level-load/act-transition boundaries or require writers to release it in teardown.

### MC-5: Editor/save/rewind backlog items

Outstanding release-review entries include MutableLevel disabling `instanceof Sonic3kLevel` event gates, editor save transient I/O handling, atomic move fallback, wrong-length chunk/block drops, and unbounded rewind/input history.

Required fix:
- Triage individually after release-blocker and sidekick work; add focused tests before production changes.

Status:
- Completed in `bugfix/ai-release-remediation`: editor-save transient read failures, atomic-move fallback, and wrong-length block/chunk state validation.
- Completed in `bugfix/ai-release-remediation`: live rewind keyframe, audio logical keyframe, and input history are pruned to a configurable `rewind.historySeconds` horizon aligned to retained keyframe boundaries.
- Completed in `bugfix/ai-release-remediation`: persisted editor edits are temporarily skipped for S3K level loads so the runtime keeps its concrete `Sonic3kLevel` event/overlay surface until `MutableLevel` supports those overlays directly.

### MC-6: Capture encoder stop can hang on stalled ffmpeg

`EncoderSink.stop()` delivered its poison pill with a bounded offer loop but then waited forever on `worker.join()`. If ffmpeg stopped consuming stdin, the worker could remain blocked in `FfmpegEncoder.encode()` and the trace-capture tool would never finish.

Required fix:
- Bound the stop join, abort the encoder on timeout, and surface a `CaptureException` rather than hanging.

Status:
- Completed in `bugfix/ai-release-remediation`: `EncoderSink.stop()` now times out, calls `encoder.abort()`, and throws; `EncoderSinkTest` covers the stalled-worker path with a short timeout.

### MC-7: Live rewind rebuilds full level tilemaps on unchanged geometry

`LevelRewindSnapshotAdapter.restore()` invalidated all manager-owned tilemaps after every restore, even when the restored block, chunk, and map arrays were the same references already installed in the live level. Holding live rewind could therefore force redundant foreground/background cache rebuilds and texture uploads every rendered frame.

Required fix:
- Compare the current level geometry references with the snapshot references before restore.
- Keep invalidating tilemaps when any geometry reference changes.
- Skip invalidation when all geometry references are already current while still bumping the COW epoch and restoring frame/HUD/checkpoint state.

Status:
- Completed in `bugfix/ai-release-remediation`: `LevelRewindSnapshotAdapter` now invalidates only on geometry-reference swaps, with regression coverage for both changed-reference and same-reference restores.

### MC-8: Deferred GL command groups alias caller-owned reusable command lists

`GLCommandGroup` stored the mutable `List<GLCommand>` passed by callers. `ObjectManager` reuses one fallback line-command list across bucket draws before `GraphicsManager.flushWithCamera()` executes the deferred groups, so earlier groups could render the last bucket's line commands instead of their own.

Required fix:
- Snapshot the command list at `GLCommandGroup` construction.
- Keep the command objects themselves shared; only list membership/order needs isolation.
- Add a focused regression test that mutates the source list after construction and verifies the group retains the original commands.

Status:
- Completed in `bugfix/ai-release-remediation`: `GLCommandGroup` now owns an immutable list snapshot and `TestGLCommandGroup` covers caller-side list reuse.

### MC-9: Per-frame key binding reads repeatedly parse strings and allocate fallback exceptions

`GameLoop` reads several key bindings from `SonicConfigurationService.getInt()` every frame. After the digit-key correctness fix, string-backed key bindings still traversed name resolution and fallback parsing repeatedly, and invalid string values could allocate `NumberFormatException` objects each frame.

Required fix:
- Cache resolved integer values inside `SonicConfigurationService`.
- Invalidate the cache when config values change, defaults reset, enum validation rewrites values, or display-aspect derivation refreshes transient integer overlays.
- Add regression coverage proving a cached key is re-resolved after `setConfigValue()`.

Status:
- Completed in `bugfix/ai-release-remediation`: `SonicConfigurationService` now caches `getInt()` results in an `EnumMap`, clears the cache at mutation/derived-overlay boundaries, and `TestConfigKeyNameResolution` verifies cache invalidation.

---

## Documentation and Release Hygiene Problem Statements

### DOC-1: S3K discrepancy registry is stale for AIZ2 battleship wrap

`docs/S3K_KNOWN_DISCREPANCIES.md` still says post-bombing wrap uses `$80`, but code now uses ROM `$200`.

Required fix:
- Rewrite or remove the stale entry and record current state honestly.

### DOC-2: AGENTS/CLAUDE docs are stale

AGENTS says `debug.flags.debugView` defaults true; CLAUDE understates S3K event handler coverage and misclassifies `debugOverlay()`.

Required fix:
- Update AGENTS/CLAUDE in the same logical change as debug gating/docs cleanup.

### DOC-3: Temporary release review artifacts are tracked

`fable-arch-review.md` and `docs/tmp-0.6-release-tasks.md` are tracked scratch/review artifacts. The current `.tmp.md` audit files are untracked and must stay that way.

Required fix:
- Remove tracked scratch files before release or move them to untracked/ignored local notes.
- Add the unrelated gumball singleton removal to `CHANGELOG.md` if it remains part of a release commit history.

### DOC-4: Config template misses documented keys

Bundled `config.yaml` omits `input.player1.start` and `capture.*` keys.

Required fix:
- Add missing keys and a capture section title.

### DOC-5: S1 event source comments mislabel implemented behavior as TODO work

`Sonic1SBZEvents` still said the SBZ2 boss/collapsing-floor sequence was a TODO and that boss objects were not implemented, even though the handler now spawns `Sonic1FalseFloorInstance`, `Sonic1ScrapEggmanInstance`, and `Sonic1FZBossInstance`. `Sonic1LZWaterEvents.checkWaterSlide()` also described itself as a TODO stub despite the implemented chunk-ID matching and inertia application path.

Required fix:
- Rewrite the SBZ/FZ class comment to describe the implemented routines and keep only the true FZ pattern-preload TODO.
- Rewrite the LZ water-slide Javadoc to describe the current chunk-ID sampling contract.

Status:
- Completed in `bugfix/ai-release-remediation`: stale TODO/stub language was removed and replaced with current implementation notes.

---

## Implementation Tasks

### Task 1: Establish Branch and Durable Plan

**Files:**
- Create: `docs/superpowers/plans/2026-06-11-release-remediation.md`

- [x] **Step 1: Create branch `bugfix/ai-release-remediation` from current worktree.**

Run: `git switch -c bugfix/ai-release-remediation`

- [x] **Step 2: Create this consolidated remediation plan.**

This file replaces the two untracked `.tmp.md` files as the durable planning artifact.

- [x] **Step 3: Verify temp audit files remain untracked and are not staged.**

Run: `git status --short`

Expected: `SIDEKICK_CPU_AUDIT.tmp.md` and `RELEASE_REVIEW_FINDINGS.tmp.md` are untracked unless intentionally ignored later.

### Task 2: Debug-Key and Art-Viewer Release Gating

**Files:**
- Modify: `src/main/java/com/openggf/GameLoop.java`
- Modify: `src/main/java/com/openggf/sprites/managers/SpriteManager.java`
- Modify: `src/main/java/com/openggf/debug/DebugOverlayManager.java`
- Test: existing or new config/input tests under `src/test/java/com/openggf/tests` or `src/test/java/com/openggf/configuration`

- [x] **Step 1: Write failing tests for disabled debug keys.**

Test should assert that with `DEBUG_VIEW_ENABLED=false`, debug shortcuts do not call level select, checkpoint teleport, special/bonus stage entry, give emeralds, Super debug, or sprite debug mode.

- [x] **Step 2: Write failing test for F12 art viewer.**

Test should assert that `OBJECT_ART_VIEWER` cannot enable and cannot freeze gameplay while debug view is disabled.

- [x] **Step 3: Implement a single debug-shortcut gate.**

Route all gameplay-affecting debug key polling through a helper such as `debugShortcutsEnabled()` or inject the existing config state into the call sites.

- [x] **Step 4: Run focused tests.**

Run the new/changed debug input tests.

### Task 3: CNZ Post-Boss One-Shot Restore

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/CnzEndBossInstance.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/...`

- [x] **Step 1: Write a failing test that advances the post-defeat window for multiple frames.**

Assert `playMusic(CNZ2)` and player-control release happen once after capsule results completion and before cannon spawn.

- [x] **Step 2: Implement a one-shot latch at the ROM handoff boundary.**

Do not repeatedly clear rolling/control state during player walk.

- [x] **Step 3: Run focused CNZ boss tests and S3K route guards.**

Run CNZ post-boss test plus `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`, and `TestSonic3kDecodingUtils`.

### Task 4: Input Config Defaults and Validation

**Files:**
- Modify: `src/main/resources/config.yaml`
- Modify: `src/main/java/com/openggf/configuration/SonicConfigurationService.java`
- Modify: `src/main/java/com/openggf/configuration/ConfigYamlWriter.java`
- Modify: `src/main/java/com/openggf/configuration/ConfigCatalog.java`
- Modify: `src/main/java/com/openggf/GameLoop.java`

- [x] **Step 1: Write failing digit-key parse and round-trip tests.**

Cover `"0"` through `"9"` and integer GLFW key codes for number-row keys.

- [x] **Step 2: Write failing FPS validation tests.**

Cover `fps: 0`, negative FPS, and valid FPS.

- [x] **Step 3: Write failing default-collision tests for pause/P2 Start and config template keys.**

Assert defaults do not collide and template includes documented keys.

- [x] **Step 4: Implement parsing/writer/default/validation fixes.**

Keep unknown-name fallback behavior intact.

- [x] **Step 5: Run focused config tests.**

Run all configuration tests and any GameLoop pause tests.

### Task 5: Sidekick CPU S3K Spawn/Init Fix

**Files:**
- Modify: `src/main/java/com/openggf/sprites/playable/SidekickCpuController.java`
- Modify: `src/main/java/com/openggf/level/LevelManager.java` if level-load ownership requires it
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kLevelEventManager.java`
- Test: sidekick CPU unit tests and S3K trace tests

- [x] **Step 1: Write failing unit coverage for S3K fresh spawn reset and init-only first frame.**

Assert first update does not apply normal follow nudge/physics and kinematics are reset on level load.

- [x] **Step 2: Add dormant park sentinel tests.**

Assert object-control `$83`-equivalent state suppresses same-frame physics through the semantic offscreen sentinel path.

- [x] **Step 3: Implement ROM-modeled routine-0 boundary behavior.**

Use feature/provider state, not generic zone carve-outs in shared follow code.

- [ ] **Step 4: Run S3K focused traces.**

Run HCZ/ICZ/LBZ/MGZ complete-run traces where available plus must-keep-green S3K tests.

### Task 6: Sidekick CPU Shared Carve-Out Cleanup

**Files:**
- Modify: `src/main/java/com/openggf/sprites/playable/SidekickCpuController.java`
- Modify: `src/main/java/com/openggf/game/PhysicsFeatureSet.java` if feature flags are needed
- Test: sidekick CPU parity tests and S2/S3K traces

- [x] **Step 1: Write failing tests for ROM jumping-flag lifecycle.**

Assert held jump is output while `jumpingFlag` is set and flag clears on grounded regardless of pushing.

- [x] **Step 2: Write failing tests for live-push bypass in water.**

Assert S2 live pushing + delayed non-pushing skips follow steering regardless of water and x speed, unless a feature flag explicitly says otherwise.

- [x] **Step 3: Write failing tests for S2/S3K obj_control nudge gate split and panic pinball split.**

S2 must not consume S3K-only bit-0 nudge suppression or pinball panic OR.

- [ ] **Step 4: Implement minimal ROM-faithful behavior or feature flags.**

Every branch must cite S2/S3K disassembly lines in comments or surrounding docs.

- [ ] **Step 5: Run S2 and S3K sidekick trace guards.**

Start with EHZ1, MTZ2, WFZ, SCZ, ARZ/CNZ/HTZ, then S3K AIZ/CNZ/MGZ.

### Task 7: Sidekick Panic/Death/Flying Cleanup

**Files:**
- Modify: `src/main/java/com/openggf/sprites/playable/SidekickCpuController.java`
- Modify: `src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java`
- Modify: `src/main/java/com/openggf/sprites/playable/TailsRespawnStrategy.java`

- [x] **Step 1: Write focused tests for PANIC hurt/dead skip and dead-fall threshold.**

Assert counters freeze during hurt/dead PANIC and dead-fall despawn follows `Tails_Max_Y_pos+$100`.

- [x] **Step 2: Write failing tests for S2 dead-Sonic flight side effects.**

Cover spindash clear, status wipe, water clamp, landing animation/status writes.

- [x] **Step 3: Implement ROM side effects with feature flags where needed.**

- [ ] **Step 4: Run death/hazard trace guards.**

Prioritize MTZ/MCZ/OOZ routes with sidekick death windows.

### Task 8: Medium-Risk Framework/Object Fixes

**Files:**
- Modify: `src/main/java/com/openggf/physics/CollisionSystem.java`
- Modify: affected object files listed in MC-2
- Modify: `src/main/java/com/openggf/game/palette/PaletteOwnershipRegistry.java` or level-load owner
- Modify: rendering files for MC-3

- [x] **Step 1: Add reset/rewind tests for odd-sensor fallback state.**
- [x] **Step 2: Add object despawn-window tests for coarse-back margin.**
- [x] **Step 3: Add palette flag reset test.**
- [x] **Step 4: Add/render VScroll ownership test.**
- [x] **Step 5: Implement each minimal fix and run focused tests after each.**

### Task 9: Documentation and Hygiene

**Files:**
- Modify: `docs/S3K_KNOWN_DISCREPANCIES.md`
- Modify: `docs/KNOWN_DISCREPANCIES.md`
- Modify: `AGENTS.md`
- Modify: `CLAUDE.md`
- Modify: `CONFIGURATION.md`
- Modify: `CHANGELOG.md`
- Delete or move: `fable-arch-review.md`, `docs/tmp-0.6-release-tasks.md`

- [x] **Step 1: Update discrepancy docs for AIZ2 wrap and any deferred game-over behavior.**
- [x] **Step 2: Update AGENTS/CLAUDE debug default and service-surface statements.**
- [x] **Step 3: Remove tracked scratch artifacts.**
- [x] **Step 4: Update CHANGELOG for user-visible fixes and prior unmentioned gumball refactor if required.**
- [x] **Step 5: Keep `.tmp.md` audit inputs uncommitted.**

### Task 10: Verification Sweep

**Commands:**
- `mvn "-Dtest=TestTraceReplayInvariantGuard" test`
- `mvn "-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils" test`
- `mvn "-Dtest=*TraceReplay" "-DfailIfNoTests=false" test`
- `mvn test`

- [ ] **Step 1: Run focused tests after every task.**
- [x] **Step 2: Run trace replay invariant guard before any trace-related completion claim.**
- [x] **Step 3: Run S3K must-keep-green tests after S3K sidekick/object/event changes.**
- [ ] **Step 4: Run broader trace sweep after sidekick fixes.**
- [ ] **Step 5: Run full Maven test suite before final completion.**
- [ ] **Step 6: Update `docs/TRACE_FRONTIER_LOG.md` for every frontier movement/regression/sweep.**

---

## Current Prioritization

1. RB-1/RB-2 debug-key and art-viewer release gating.
2. RB-3 CNZ post-boss one-shot restore.
3. RB-4/RB-5/RB-6 input config defaults, digit keys, pause behavior, and FPS validation.
4. SK-1 S3K sidekick fresh spawn/init-only first frame.
5. SK-2/SK-3/SK-4/SK-5 sidekick parity cleanup.
6. MC-1 through MC-5 medium-risk framework/object fixes.
7. DOC-1 through DOC-4 documentation and release hygiene.

This order fixes shipped user-facing regressions before deep trace frontier work, while still preserving the sidekick audit as a first-class release remediation track.
