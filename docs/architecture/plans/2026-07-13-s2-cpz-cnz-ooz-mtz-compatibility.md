# Sonic 2 CPZ, CNZ, OOZ, and MTZ Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the scoped S2 traversal objects safe for arbitrary sidekicks and widescreen/donation configurations without changing native trace behavior.

**Architecture:** Preserve every ROM-shaped P1/P2 holder and call order. Add object-local identity maps only for extension participants, encode their keys through compact rewind PlayerRefs, and leave already-scalable generic solid/query paths unchanged after focused verification.

**Tech Stack:** Java 21, JUnit Jupiter, Maven, compact rewind schema, Sonic 2 trace replay fixtures.

---

### Task 1: ForcedSpin identity extension

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/objects/ForcedSpinObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`
- Test: `src/test/java/com/openggf/game/sonic2/objects/TestForcedSpinMultiSidekick.java`

- [ ] **Step 1: Write failing main-plus-three, reorder, omission, and PlayerRef replacement tests**

Create a trigger with main plus three sidekicks, cross each identity independently, reorder the roster, capture with `RewindIdentityTable`, restore against replacement players, and assert the extension map contains only replacement keys.

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `mvn "-Dtest=TestForcedSpinMultiSidekick" test`

Expected: failure because third-and-later players are not processed and no identity extension state exists.

- [ ] **Step 3: Implement native-prefix identity state**

Keep `sonicPastTrigger` and `tailsPastTrigger`. Add native owner references plus an `IdentityHashMap<PlayableEntity, CrossingState>`. Dispatch main, native P2, then extensions. Use a rewind-stateful value:

```java
private static final class CrossingState implements RewindStateful<Boolean> {
    private boolean pastTrigger;
    public Boolean captureRewindStateValue() { return pastTrigger; }
    public void restoreRewindStateValue(Boolean state) { pastTrigger = Boolean.TRUE.equals(state); }
}
```

Apply the horizontal CPU recovery exception per sidekick. Prune omitted inactive entries and release no unrelated player state.

- [ ] **Step 4: Register exact compact policies and verify GREEN**

Mark native owners and extension state as `CAPTURED`. Run the focused test plus `TestForcedSpinObjectInstance`.

### Task 2: OOZ popping-platform riders and spring audit

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/objects/OOZPoppingPlatformObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`
- Test: `src/test/java/com/openggf/game/sonic2/objects/TestOOZPoppingPlatformMultiSidekick.java`
- Test: `src/test/java/com/openggf/game/sonic2/objects/TestOOZSpringMultiSidekick.java`

- [ ] **Step 1: Write failing simultaneous-rider and lifecycle tests**

Stand main plus three sidekicks on the player-triggered platform, assert a single shared rise captures all four, launch all four at apex in native order, and cover death, omission, reorder, unload, and replacement-player compact restore.

- [ ] **Step 2: Confirm RED**

Run: `mvn "-Dtest=TestOOZPoppingPlatformMultiSidekick" test`

Expected: third-and-later riders are neither locked nor launched.

- [ ] **Step 3: Extend rider ownership locally**

Keep the native main/P2 booleans. Add native owners and an identity set/map for extension locks. Build an ordered participant list once per update; use it for wait detection, lock carry, apex launch, and cleanup without changing shared movement cadence.

- [ ] **Step 4: Verify the OOZ spring is generic-contact driven**

Write main-plus-three contact tests against `OOZSpringObjectInstance`. If they pass before production changes, retain the class unchanged and document the audit-only conclusion.

- [ ] **Step 5: Run OOZ focused regression tests**

Run: `mvn "-Dtest=TestOOZPoppingPlatformMultiSidekick,TestOOZSpringMultiSidekick,TestOOZPlacedObjectGaps,TestSonic2TriggerParticipation" test`

### Task 3: MTZ tube and nut identity extensions

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/objects/MTZSpinTubeObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic2/objects/NutObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`
- Test: `src/test/java/com/openggf/game/sonic2/objects/TestMTZSpinTubeMultiSidekick.java`
- Test: `src/test/java/com/openggf/game/sonic2/objects/TestMTZNutMultiSidekick.java`

- [ ] **Step 1: Write failing tube ownership tests**

Enter main plus three sidekicks independently, assert native-first state progression, reorder identities, release dead/omitted players, unload, and restore non-empty state through replacement PlayerRefs.

- [ ] **Step 2: Implement tube extension states and verify GREEN**

Keep `mainCharState` and `sidekickCharState`. Add captured native owner references and an identity map of extension `CharacterState` values. Execute extensions after native P2 and clean up only owned forced control.

- [ ] **Step 3: Write failing nut action/standing tests**

Seed solid standing ownership for main plus three sidekicks, assert independent alignment/screwing state and native ordering, then cover reorder, omission, unload, and replacement-player compact restore.

- [ ] **Step 4: Implement nut extension states and verify GREEN**

Keep `p1`, `p2`, `standingP1/P2`, and contact latches. Add identity-keyed extension action/standing state and process it after native P2. Preserve the single shared nut routine/position and the standing-plus-run activation semantics.

- [ ] **Step 5: Run MTZ focused regressions**

Run: `mvn "-Dtest=TestMTZSpinTubeMultiSidekick,TestMTZNutMultiSidekick,TestMTZSpinTubeObjectInstance,TestS2MtzNutRewind,TestSonic2TriggerParticipation" test`

### Task 4: CPZ, long-platform, viewport, and donation audit

**Files:**
- Test: `src/test/java/com/openggf/game/sonic2/objects/TestS2Wave5ExistingCompatibility.java`
- Create: `docs/compatibility/2026-07-13-s2-cpz-cnz-ooz-mtz-remediation.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Prove existing CPZ tube identity behavior**

Cover main plus three independent states and replacement-player compact restore without changing `CPZSpinTubeObjectInstance` unless the test exposes a real defect.

- [ ] **Step 2: Prove long-platform proximity and generic rider scaling**

Exercise main plus three participants while preserving native world stop points and shared movement timing.

- [ ] **Step 3: Audit visible-screen constants and event thresholds**

Search CPZ/CNZ/OOZ/MTZ code for `320`, `224`, viewport, camera, activation, and culling predicates. Widen only proven viewport edges; add exact 320 and wide tests for any changed predicate. Leave world coordinates unchanged.

- [ ] **Step 4: Document donation conclusion**

Record that MTZ nuts activate by standing movement/displacement and need no spin-dash fallback. Record ForcedSpin as rolling-state control, not a spin-dash traversal requirement.

### Task 5: Full regression and delivery

**Files:**
- Modify only documentation required by verified results.

- [ ] **Step 1: Run compact and rewind guards**

Run: `mvn "-Dtest=TestScalarOnlyCodecDeletion,TestRewindCoverageGuard,TestStaticStateRewindCoverageGuard" test`

- [ ] **Step 2: Run all scoped trace replays**

Run CPZ1/2, CNZ1/2, OOZ1/2, and MTZ1/2/3 trace classes with native width and donation off. Any inherited known-red result must match its documented baseline; previously green traces must remain green.

- [ ] **Step 3: Run diff review and commit coherent implementation**

Run `git diff --check`, inspect every captured reference and lifecycle path, update the changelog/audit, and commit with repository trailers.
