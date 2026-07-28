# S3K Generic Traversal Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make in-scope S3K generic forced-traversal objects safe for arbitrary sidekick rosters without changing native P1/P2 behavior, widescreen behavior, or donor capability semantics.

**Architecture:** Preserve the ROM P1/P2 state prefix and process extension players afterward through identity-keyed state. Reconcile promotion, demotion, omission, death, unload, and rewind replacement before running per-player state machines. Stateless traversal checks expand their participation policy only where ignoring an extra player can block or strand that player.

**Tech Stack:** Java 21, JUnit 5, Maven Surefire, compact rewind schema, S3K locked-on placement data.

---

### Task 1: Inventory and baseline

**Files:**
- Modify: `docs/compatibility/2026-07-13-s3k-generic-traversal-remediation.md`

- [ ] Record locked-on placements by parsing six-byte object records through the first `$FFFF` terminator: AutoSpin (`$26`) is AIZ2=8, HCZ1=8, ICZ2=9; AutomaticTunnel (`$24`) is LBZ1=14; CNZ Cylinder (`$47`) is CNZ1=32 and CNZ2=43.
- [ ] Classify Door, CollapsingBridge, TwistedRamp, Spring, and event-spawned Signpost from live call sites, excluding FBZ and every zone after LBZ.
- [ ] Run focused existing tests and freeze CNZ `7130/f1846`, ICZ `3206/f3139`, LBZ `5881/f2270`, and currently green AIZ traces before production edits.

### Task 2: AutoSpin identity crossing state

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/AutoSpinObjectInstance.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestAutoSpinObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`

- [ ] Write tests proving a third sidekick crosses independently, native-P2 state follows its actor through reorder/promotion/demotion, omitted identities are pruned, and rewind keys relink to replacement players.
- [ ] Run the focused tests and confirm failures show the native two-boolean limitation.
- [ ] Add an identity-keyed extension crossing map plus a captured native-P2 owner, reconciling roles before native P1, native P2, then extensions are processed.
- [ ] Re-run the focused tests to green.

### Task 3: AutomaticTunnel forced-control ownership

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/AutomaticTunnelObjectInstance.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestAutomaticTunnelObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`

- [ ] Write tests for third-sidekick capture, active native-P2 demotion, active extension promotion, omission, death, unload, unrelated replacement control, native update order, persistence, and rewind replacement relinking.
- [ ] Run the tests and confirm the expected native-two-state failures.
- [ ] Keep stable native state holders, copy state values between native P2 and identity-keyed extension states, and release only players whose control signature is still owned by the tunnel.
- [ ] Process extensions after native P1/P2 and include them in persistence.
- [ ] Re-run focused tests to green.

### Task 4: CNZ Cylinder extension riders

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/CnzCylinderInstance.java`
- Modify or create focused cylinder multi-sidekick tests under `src/test/java/com/openggf/game/sonic3k/objects/`
- Modify: `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`

- [ ] Write failing tests for simultaneous third/fourth riders, identity reorder, omission, death, unload, unrelated control, and rewind replacement.
- [ ] Add identity-keyed extension rider states while retaining native standing bits and P1/P2 diagnostics unchanged; aggregate extension standing only for shared cylinder motion.
- [ ] Process native slots first and extension slots in roster order, releasing omitted owners safely.
- [ ] Re-run cylinder focused and CNZ trace tests.

### Task 5: Bounded stateless traversal participation

**Files:**
- Modify only call sites proven traversal-sensitive among `DoorObjectInstance.java`, `CollapsingBridgeObjectInstance.java`, `Sonic3kTwistedRampObjectInstance.java`, and `Sonic3kSpringObjectInstance.java`.
- Test the changed classes with focused JUnit tests.

- [ ] Write failing tests showing extras ignored by Door triggers, Twisted Ramp launch, horizontal Spring approach, or directional bridge selection where applicable.
- [ ] Expand those checks to `MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED`, preserving ordered native prefix.
- [ ] Keep `S3kSignpostInstance` native-only when its use is presentation/bump arbitration rather than mandatory traversal, and document that classification.

### Task 6: Compatibility and regression verification

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `docs/compatibility/2026-07-13-s3k-generic-traversal-remediation.md`

- [ ] Audit all changed thresholds/camera reads at 320/352/400/528/800. Do not add width branches where mechanics are world-coordinate-only.
- [ ] Confirm AutoSpin and AutomaticTunnel force rolling/path movement directly and therefore do not require spin dash; add no donor workaround.
- [ ] Run focused suites, both rewind guards, exact known-red comparisons, AIZ green traces, and `git diff --check`.
- [ ] Update the changelog, commit with required documentation trailers, and request independent review.
