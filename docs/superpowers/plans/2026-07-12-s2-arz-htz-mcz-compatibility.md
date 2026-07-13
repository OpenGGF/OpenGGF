# Sonic 2 ARZ, HTZ, and MCZ Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make implemented ARZ, HTZ, and MCZ mechanics safe for arbitrary sidekicks and widescreen play without changing native 320px or trace behavior.

**Architecture:** Preserve native P1/P2 fields and execution order, adding identity-keyed extension state only after that prefix. Player references use the central compact rewind schema; viewport changes use shared live dimensions only for proven render-screen predicates.

**Tech Stack:** Java 21, JUnit 5, Maven, `ObjectPlayerQuery`, `RewindStateful`, compact rewind codecs, trace replay tests.

---

### Task 1: MCZ VineSwitch extension ownership

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/objects/VineSwitchObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`
- Create: `src/test/java/com/openggf/game/sonic2/objects/TestS2VineSwitchMultiSidekick.java`

- [ ] **Step 1: Write failing main-plus-three tests** proving the first two actors use native P1/P2 state and later actors grab/release independently in roster order.
- [ ] **Step 2: Run** `mvn "-Dtest=TestS2VineSwitchMultiSidekick" test` and confirm slot sharing or lost ownership fails.
- [ ] **Step 3: Add** an identity-keyed extension `PlayerState` (`grabbed`, `releaseDelay`) while retaining native scalar fields; bind P1/P2 owners explicitly and process extensions afterward.
- [ ] **Step 4: Add failing lifecycle tests** for death, unload, omission, reorder, runtime replacement, unrelated control, and non-empty replacement-instance rewind.
- [ ] **Step 5: Implement ownership-only release/pruning** and central `CAPTURED` policies for owner/extension fields.
- [ ] **Step 6: Re-run focused tests** and confirm all pass.

### Task 2: MCZ MovingVine extension ownership

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/objects/MovingVineObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`
- Create: `src/test/java/com/openggf/game/sonic2/objects/TestS2MovingVineMultiSidekick.java`
- Verify: `src/test/java/com/openggf/game/sonic2/objects/TestS2MovingVineRewind.java`

- [ ] **Step 1: Write failing main-plus-three tests** for independent grab/release delay, shared motion activation, and native P1/P2-first dispatch.
- [ ] **Step 2: Run the focused test** and record the native-P2 slot collision failure.
- [ ] **Step 3: Extend existing identity state** so third+ actors have independent grabbed/release state without changing MCZ/WFZ shared movement or trigger cadence.
- [ ] **Step 4: Add failing lifecycle/rewind tests** for death, unload, omission, reorder, replacement, no ownership transfer, and PlayerRef restoration.
- [ ] **Step 5: Implement ownership-only cleanup and compact policies**, using `NativePositionOps` for player position writes.
- [ ] **Step 6: Run both new and existing MovingVine rewind tests** to green.

### Task 3: HTZ Seesaw multi-sidekick launch

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/objects/SeesawObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic2/objects/SeesawBallObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`
- Create: `src/test/java/com/openggf/game/sonic2/objects/TestS2HtzSeesawMultiSidekick.java`
- Verify: `src/test/java/com/openggf/game/rewind/TestSeesawBallGraphRewind.java`

- [ ] **Step 1: Write failing main-plus-three standing/launch tests** that assert native P1 then P2 launch first, extensions afterward in roster order, with exact native velocity/animation fields.
- [ ] **Step 2: Run the focused test** and confirm the two-slot implementation omits or transfers later riders.
- [ ] **Step 3: Add identity-owned extension standing state** while preserving native fields and ball-to-parent launch ordering.
- [ ] **Step 4: Add failing omission/reorder/replacement/death/rewind tests** and unrelated-player assertions.
- [ ] **Step 5: Implement lifecycle pruning and central compact policy**, then run focused and graph-rewind tests.

### Task 4: ARZ Whisp viewport-aware activation

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/objects/badniks/WhispBadnikInstance.java`
- Create: `src/test/java/com/openggf/game/sonic2/objects/badniks/TestWhispViewportActivation.java`
- Verify: `src/test/java/com/openggf/tests/trace/TestS2ObjectOccupancyOracle.java`

- [ ] **Step 1: Write failing boundary tests** at 320px and 800px for the exact `Render_Sprites` overlap inequalities, including the one-frame wait-to-chase transition.
- [ ] **Step 2: Run the focused test** and confirm only the wider visible position fails.
- [ ] **Step 3: Replace screen constants with shared viewport dimensions** while retaining object radius and strict comparison operators.
- [ ] **Step 4: Re-run boundary and Whisp occupancy tests** to green.

### Task 5: Event and donation audit documentation

**Files:**
- Create: `docs/compatibility/2026-07-12-s2-arz-htz-mcz-remediation.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Audit ARZ/HTZ/MCZ event handlers** and classify every threshold as world-coordinate, camera lock, or visible-screen edge.
- [ ] **Step 2: Audit mandatory routes for spin-dash dependencies** using effective capability semantics; add no workaround unless a blocker is demonstrated.
- [ ] **Step 3: Document object changes, event classification, donation conclusion, trace evidence, and FBZ exclusion.**
- [ ] **Step 4: Update the changelog** with the user-visible compatibility result.

### Task 6: Regression verification and commit

**Files:**
- Modify only if a trace frontier moves: `docs/TRACE_FRONTIER_LOG.md`

- [ ] **Step 1: Run focused tests** for VineSwitch, MovingVine, HTZ Seesaw, Whisp, graph rewind, and scalar codecs.
- [ ] **Step 2: Run** `TestRewindCoverageGuard` and `TestStaticStateRewindCoverageGuard`.
- [ ] **Step 3: Run relevant ARZ, HTZ, and MCZ trace replay classes** and compare any known-red first frame/count with baseline.
- [ ] **Step 4: Confirm commit diff contains no FBZ path or Flying Battery symbol.**
- [ ] **Step 5: Self-review** player ownership, native order, centre-coordinate writes, viewport inequalities, cleanup boundaries, and documentation claims.
- [ ] **Step 6: Commit** with required branch-policy trailers and report the SHA plus exact verification evidence.
