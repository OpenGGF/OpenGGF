# Multi-Stage Trace Runs — Slots-Depth Plan: Slot-Machine Bonus Trace Replay

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend bonus-stage trace replay to the slot machine (spec engine addition #4): accept the `slots` token, invoke the deferred-setup seam headlessly, align the comparator with the slot runtime's player swap, and prove the slots boot headlessly — per the spec's slots-depth roadmap entry.

**Architecture (verified 2026-07-19 exploration):** The recorder already captures slots segments (zone 0x15, level schema) — the replay side is blocked in exactly three places: (1) `bonusStageTypeForToken` throws on `"slots"`; (2) `applyBonusStageEntry` never calls `onDeferredSetupComplete()` (the ONLY builder of `S3kSlotBonusStageRuntime`, which is proven headless-safe — pure Java + ROM data, existing unit suite boots it dozens of times); (3) **the player swap**: `bootstrap()` constructs a NEW `S3kSlotBonusPlayer`, swaps it into the sprite manager and focuses the camera on it, while `HeadlessTestFixture.sprite()` is a cached final field still pointing at the orphaned original — every sprite column would compare the WRONG object (the ROM's `$B000` slot player is what the recorder captured). Fix via a `comparedSprite()` seam on the replay base that the bonus base overrides to follow the CAMERA-FOCUSED sprite — ROM-faithful (the camera tracks the `$B000` object), uniform across all bonus types (gumball/pachinko focus the original player, so behavior is unchanged there), no type branch. Slot frame-driving needs NO new wiring: `LevelFrameStep` already calls `onFrameUpdate()` when `updateDuringLevelFrame()` is true, and the slot camera (custom tracking, default step suppressed) is read live by the comparator so camera columns stay meaningful. No rewind interaction (supportsRewind false for slots; headless replay never reaches `updateBonusStageMode`).

**Tech Stack:** Java 21 + JUnit 5 (Jupiter only). No lua changes (recorder v6.31 already handles zone 0x15).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-18-multi-stage-trace-runs-design.md` (slots-depth = addition #4 + token acceptance + comparator alignment). S1/S2 are later plans. MVP red-allowed posture.
- Comparison-only invariant. No zone/route/frame carve-outs (the comparedSprite seam keys on camera focus — engine state — not on bonus type).
- JUnit 5 only; commit policy as prior plans (trailer block; src/main feat → `Changelog: updated` + CRLF-verified CHANGELOG.md; stage exact paths; every commit ends with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_01LPPPMPSUQBgYpxpA82bad5`).
- Recordings absent: the slots replay test lands skip-if-missing on `src/test/resources/traces/s3k/bonus_slots/`; the boot smoke test runs TODAY.
- Guard awareness: `TestBuildToolingGuard` may react to `applyBonusStageEntry` changes (its registered baseline names the profile-gate line — verify the registered signal line is UNCHANGED by the edit; if the edit shifts it, re-register per convention). Test naming: the concrete slots replay test ends in `*TraceReplay.java` → its base `AbstractS3kBonusStageTraceReplayTest` is ALREADY registered in `TestTraceReplayInvariantGuard` (plan-b) — no new registration expected.

---

### Task 1: Token acceptance + deferred-setup call in `applyBonusStageEntry`

**Files:**
- Modify: `src/main/java/com/openggf/trace/replay/TraceReplaySessionBootstrap.java`
- Test: extend `src/test/java/com/openggf/trace/replay/TestTraceReplaySessionBootstrapBonus.java`

**Interfaces:**
- Consumes: `bonusStageTypeForToken` (`:429-438`, currently throws on "slots"); `applyBonusStageEntry` (`:451-499`, live-order mirror: setActiveBonusStageProvider → onEnter → registerBonusStageAdapter → rings/HUD/unhide → pachinko inject); `BonusStageProvider.onDeferredSetupComplete()` (default no-op, `AbstractBonusStageCoordinator.java:57-59`; SLOT_MACHINE-guarded runtime build, `Sonic3kBonusStageCoordinator.java:76-90`); live call order precedent `GameLoop.java:3021-3039` (deferred-setup AFTER onEnter, post-title-card).
- Produces: `bonusStageTypeForToken("slots")` returns `BonusStageType.SLOT_MACHINE` (the throw remains for unknown/null); `applyBonusStageEntry` calls `provider.onDeferredSetupComplete()` **unconditionally, immediately after `registerBonusStageAdapter`** (no-op for gumball/pachinko by the base default; builds the slot runtime for SLOT_MACHINE; NOT idempotent — exactly one call, and `applyBonusStageEntry` is already once-per-boot). Javadoc updated: the method now mirrors the live sequence THROUGH deferred setup (spec addition #4).

- [ ] **Step 1:** Failing test additions: `bonusTypeMappingCoversGumballAndPachinko` gains `assertEquals(BonusStageType.SLOT_MACHINE, ...bonusStageTypeForToken("slots"))` and keeps the unknown/null throws (use a different bogus token, e.g. `"casino"`).
- [ ] **Step 2:** Implement both edits → green. Run `TestBuildToolingGuard` — verify the registered `s3k_bonus_stage` signal line is unchanged (the edits don't touch the profile gate); if the guard fires anyway, re-register per convention with justification.
- [ ] **Step 3:** CHANGELOG line `- Trace replay: slot-machine bonus segments accepted (deferred-setup seam wired).` + commit (feat, src/main → Changelog: updated).

---

### Task 2: Comparator alignment — the `comparedSprite()` seam

**Files:**
- Modify: `src/test/java/com/openggf/tests/trace/AbstractTraceReplayTest.java`
- Modify: `src/test/java/com/openggf/tests/trace/s3k/AbstractS3kBonusStageTraceReplayTest.java`
- Test: `src/test/java/com/openggf/tests/trace/s3k/TestS3kBonusComparedSpriteSeam.java` (new; unit-level, no ROM)

**Interfaces:**
- Consumes: every comparator read of `fixture.sprite()` in `AbstractTraceReplayTest` (the compare loop `:320-347`, `:620-622`, `captureEngineDiagnostics :838+` — find ALL of them by grepping `fixture.sprite()` / `.sprite()` in the class); `GameServices.camera().getFocusedSprite()` (the slot bootstrap focuses the camera on the swapped player, `S3kSlotBonusStageRuntime.java:98-101`; gumball/pachinko leave focus on the original player).
- Produces: a `protected AbstractPlayableSprite comparedSprite(HeadlessTestFixture fixture)` hook on `AbstractTraceReplayTest`, default `return fixture.sprite();`, with EVERY comparator sprite read routed through it (mechanical substitution — the default preserves byte-identical behavior for all existing tests). `AbstractS3kBonusStageTraceReplayTest` overrides it: return the camera-focused sprite when it is an `AbstractPlayableSprite`, else fall back to `fixture.sprite()` — with a javadoc explaining the ROM-faithful rationale (the recorder captured `$B000`, which is the object the ROM camera tracks; the slot runtime swaps the tracked player, gumball/pachinko don't, so this is uniform across bonus kinds — engine-state-keyed, not type-keyed).

- [ ] **Step 1:** Failing unit test: subclass a minimal stub of the bonus base (or test the override method directly with a stubbed camera/fixture — read how `GameServices.camera()` is resolvable headlessly in unit context; if a full GameServices boot is too heavy for a unit test, test the override's selection logic through a small extracted package-private helper `selectComparedSprite(AbstractPlayableSprite focused, AbstractPlayableSprite fixtureSprite)` — pure function: returns focused when non-null playable, else fixture sprite).
- [ ] **Step 2:** Route ALL comparator sprite reads through the hook (grep-verified — state the count in your report); default-hook behavior proves itself via the existing trace suites: run `TestTraceDataParsing` + one existing concrete replay test class (e.g. the CNZ complete-run test, which will SKIP or run its assumptions — the point is compilation + no behavior change on the level path) + the bonus replay tests (still SKIP).
- [ ] **Step 3:** Commit (test-only trailers).

---

### Task 3: Slots headless boot smoke test (runs today)

**Files:**
- Modify: `src/test/java/com/openggf/tests/TestS3kBonusStageHeadlessBoot.java` (add the slots method)

**Interfaces:**
- Consumes: the class's existing `performBonusStageEntry` mirror + fixture idioms (gumball/pachinko methods); `Sonic3kZoneIds.ZONE_SLOT_MACHINE` (0x15, `Sonic3kZoneIds.java:38`); the coordinator cast for `getActiveType()`; `((Sonic3kBonusStageCoordinator) provider)` accessors for the runtime (read the coordinator for the runtime getter the unit suite uses — `TestS3kSlotBonusStageRuntime`/`TestS3kSlotBonusStageCoordinator` idioms at `:44-76`).
- Produces: `slotsZoneBootsHeadlesslyWithRuntime()`: fixture `withZoneAndAct(0x15, 0)`, full entry sequence INCLUDING `onDeferredSetupComplete()` (Task 1's seam — mirror `applyBonusStageEntry`'s exact order), assert active type SLOT_MACHINE, the slot runtime non-null + `isInitialized()`, the camera-focused sprite is NOT the fixture's original sprite (the swap happened — this also validates Task 2's seam premise), step 60 idle frames via the fixture's stepper without exceptions, player alive.

- [ ] **Step 1:** Write + run with ROM present → green or honest BLOCKED with the stack trace (a boot gap discovery is the task's purpose; do not mask).
- [ ] **Step 2:** Commit (test-only trailers, subject `test(s3k): slots headless boot smoke coverage`).

---

### Task 4: Skip-if-missing slots replay test + gate + docs

**Files:**
- Create: `src/test/java/com/openggf/tests/trace/s3k/TestS3kSlotsBonusTraceReplay.java` (mirror the gumball concrete test: `@RequiresRom(SonicGame.SONIC_3K)`, `game()/zone()=0x15/act()=0/traceDirectory()=traces/s3k/bonus_slots`)
- Modify: `tools/bizhawk/README.md` (extend the bonus recording subsection: slots = 20–34 rings at the star post — remainder 0; movie naming `s3k-aiz-slots.bk2`; the slots segment records under the level schema like gumball/pachinko)
- Modify: `docs/TRACE_FRONTIER_LOG.md` (entry: slots replay accepted; test skips pending the recording; comparator swap-alignment seam landed)

- [ ] **Step 1:** Concrete test SKIPs (failed=0). README + frontier entries (line-ending-clean; ring-range accuracy per the verified selector: 20–34 → remainder 0 → SLOT_MACHINE).
- [ ] **Step 2:** Full-suite gate (detached + monitor): no NEW failures vs baseline (known flakes: geyser, wire-cage — isolated-pass verify if present).
- [ ] **Step 3:** Commit docs. Merge-time README release-log reminder stands.

## Plan-level notes

- The recorder needs NO changes: zone 0x15 already produces `s3k_bonus_stage` segments with `bonus_stage_type: "slots"` (plan-a state machine).
- Rewind: no interaction (supportsRewind false for slots; headless replay never reaches updateBonusStageMode) — verified, no work.
- The camera columns are meaningful under the slot runtime's custom tracking (comparator reads the live camera; the suppressed default step is exactly what the ROM does).
- Chain/visual integration inherits automatically: the walker/advancer treat slots as any bonus_stage segment; `bonusStageTypeForToken` was the only type-keyed gate.
