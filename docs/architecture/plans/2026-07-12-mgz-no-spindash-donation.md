# MGZ No-Spindash Donation Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove whether S1-style no-spindash donation blocks the paired MGZ dash-trigger route and add only the smallest capability-driven fallback if it does.

**Architecture:** A ROM-backed headless test is the decision gate. If it proves route blockage, `MGZDashTriggerObjectInstance` retains its native animation-9 path and adds a missing-capability-only sustained-intent path with rewind-captured per-player counters.

**Tech Stack:** Java 21, JUnit Jupiter, headless MGZ fixture, compact rewind schema, Maven Surefire.

---

### Task 1: Route characterization

**Files:**
- Create: `src/test/java/com/openggf/tests/TestS3kMgzDashTriggerNoSpindashDonation.java`

- [x] Load MGZ1 from the S3K ROM with intros skipped and no sidekick.
- [x] Locate the reproduced index-7 dash trigger and its paired trigger platforms from real placement data.
- [x] Apply the real S1-donor/S3K-host composed rules, establish grounded rightward contact, and sustain right input.
- [x] Prove the trigger remains idle and forward progress remains blocked before the paired platforms.
- [x] Run the test alone and record the route-blocking RED that authorizes Task 2.

### Task 2: Capability-driven fallback, conditional on Task 1 RED

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/MGZDashTriggerObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`
- Test: `src/test/java/com/openggf/game/sonic3k/objects/MgzDashTriggerObjectInstanceTest.java`
- Test: `src/test/java/com/openggf/tests/TestS3kMgzDashTriggerNoSpindashDonation.java`

- [x] Add a native-capability test proving sustained running never substitutes for animation 9.
- [x] Add a no-spindash test proving short/incidental contact does not arm and sustained grounded intent does.
- [x] Add a rewind round-trip test for the per-player sustained-intent state.
- [x] Implement the minimal per-player counter, gated by effective `spindashEnabled=false`, grounded adjacency, direction toward the trigger, and sustained run/push input.
- [x] Capture the counter map through the central rewind policy and clear state when eligibility ends or the trigger arms.
- [x] Run paired tests until green without changing native animation-9 behavior.

### Task 3: Verification and delivery

**Files:**
- Modify if production changes: `CHANGELOG.md`

- [x] Run focused dash-trigger/platform, donation, and rewind tests.
- [x] Run `TestRewindCoverageGuard` and `TestStaticStateRewindCoverageGuard`.
- [x] Run `TestS3kMgzCompleteRunTraceReplay` in isolation and confirm its frame-866 result matches exact baseline `a68084f79`.
- [x] Record the pre-existing `TestS3kMgzTraceReplay` frame-33271 input-alignment failure without modifying trace data.
- [x] Review the final diff for capability-only gating, native parity, rewind identity, test isolation, and FBZ exclusion.
- [x] Commit with all branch-policy trailers.
