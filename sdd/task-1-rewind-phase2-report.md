# Task 1 Report: Generic Round-Trip Harness (Rewind Phase 2 Gate)

## Status: DONE — 4 REAL FINDINGS (preserved, not hidden)

## Branch / Worktree

- Branch: `feature/ai-rewind-phase2-generic-recreate`
- Worktree: `C:/Users/farre/IdeaProjects/sonic-engine/.claude/worktrees/aiz2-rewind-fix`

---

## What was built

### Files created / modified

| File | Action | Purpose |
|------|--------|---------|
| `src/test/java/com/openggf/game/rewind/RewindRoundTripHarness.java` | Created | Shared harness driving the REAL ObjectManager capture→restore path |
| `src/test/java/com/openggf/game/rewind/TestEveryObjectRewindRoundTrip.java` | Created | TDD keystone + parametrized sweep over all spawnable classes |
| `src/main/java/com/openggf/game/rewind/coverage/ObjectClasspathScan.java` | Modified | Widened to `public` so the harness (in a different package) can call it |
| `src/test/java/com/openggf/game/rewind/coverage/RewindRoundTripProbe.java` | Modified | Construction delegated to `RewindRoundTripHarness.constructHeadless()` — one construction path |

---

## Harness API

```java
// Factory — empty harness
RewindRoundTripHarness h = RewindRoundTripHarness.build(GameId.S2);

// Factory — harness with a placed spawn already materialised (preferred for keystone test)
RewindRoundTripHarness h = RewindRoundTripHarness.buildPlaced(GameId.S2, objectId);

// Instance ops
h.spawnPlacedAndStep(objectId, frames);  // rebuild OM with this spawn, step N frames
h.countByType();                          // live object counts by class name
h.firstArticulatedChildX();              // DEZ keystone helper
h.roundTrip();                           // capture → restore (real production path)
h.objectManager();                       // direct OM access for inspection

// Static sweep probe
RoundTripSweepResult result = RewindRoundTripHarness.probeClass(fqn);
// → Passed | Unprobed(reason) | CountMismatch(before, after) | ScalarMismatch(diffs)

// Shared construction entry point (delegated from RewindRoundTripProbe)
AbstractObjectInstance inst = RewindRoundTripHarness.constructHeadless(cls, stub);
```

---

## Why this is stronger than RewindRoundTripProbe

`RewindRoundTripProbe` calls `captureRewindState()` + fresh construction + `restoreRewindState()` on
isolated instances. This is tautological: a fresh construction from the same spawn always produces
consistent state, regardless of whether the rewind codec correctly serialises/deserialises runtime state.

The harness drives `ObjectManager.rewindSnapshottable()` → `RewindRegistry.capture()` →
`RewindRegistry.restore(CompositeSnapshot)` — the REAL production path — including:

- Actual codec execution (the `DynamicObjectRewindCodec.recreate()` path)
- Dynamic-object slot allocation through the ObjectManager
- Boss-child reconstructor suppression (no double-spawn on restore)
- State delta checking after round-trip (not just "construction succeeded")

---

## Test run results

```
mvn -Dmse=off "-Dtest=com.openggf.game.rewind.TestEveryObjectRewindRoundTrip,
    com.openggf.game.rewind.coverage.TestRewindCoverageAnalyzer,
    com.openggf.game.rewind.coverage.TestRewindCoverageGuard" test
```

```
Tests run: 794, Failures: 4, Errors: 0, Skipped: 0
```

### Counts

| Category | Count |
|----------|-------|
| Probed (have codec, round-trip attempted) | ~34 |
| Passed (round-trip clean) | 30 |
| Unprobed (no dynamic recreate path) | ~760 |
| Count mismatches (REAL FINDING) | 4 |
| Scalar mismatches | 0 |

### Keystone test

`dezRobotChildrenRoundTripExactlyThroughHarness` — **PASSES**. DEZ robot boss (placed spawn)
materialises its ArticulatedChild instances; round-trip produces identical count and identical
`currentX` scalar field. This validates that the harness wires the real ObjectManager +
RewindRegistry path and that boss-child codec execution is correct.

---

## Real findings (4 failures — NOT weakened)

All 4 are parent-dependent child objects whose codecs correctly return `null` from `recreate()`
when no parent boss exists in the ObjectManager. In production, the parent is always a placed
object reconstructed before its children. In the isolated harness sweep, no parent exists.

| Class | Failure type | Root cause |
|-------|-------------|-----------|
| `com.openggf.game.sonic3k.objects.CnzMinibossCoilInstance` | CountMismatch (before={…=1}, after={}) | `cnzMinibossChildCodec.recreate()` returns null — no `CnzMinibossInstance` parent present |
| `com.openggf.game.sonic3k.objects.CnzMinibossSparkInstance` | CountMismatch (before={…=1}, after={}) | Same — null parent lookup |
| `com.openggf.game.sonic3k.objects.CnzMinibossTopInstance` | CountMismatch (before={…=1}, after={}) | Same — null parent lookup |
| `com.openggf.game.sonic3k.objects.GumballMachineObjectInstance$ExitTriggerChild` | CountMismatch (before={…=1}, after={}) | `exitTriggerChildCodec.recreate()` returns null — no `GumballMachineObjectInstance` parent present |

**These failures are preserved as-is.** The brief mandates: "do NOT weaken the assertion to hide them."

### Recommended follow-up (next Phase 2 task)

Update `probeClass()` to detect when the codec's `recreate()` returns null (no-parent-available)
and classify these as `Unprobed("parent-dependent child — requires parent in ObjectManager")`.
This is a classification change, not an assertion weakening — the full round-trip test with a parent
present is already covered by the placed-spawn path (keystone test exercises the DEZ equivalent).

Alternatively: add a two-object placed sweep path that spawns parent + child together.

---

## Step 5: Construction unified into harness

`RewindRoundTripProbe.probeClass()` previously maintained its own duplicate of the four-strategy
construction loop (`zero-arg → (ObjectSpawn) → (ObjectSpawn,String) → (ObjectSpawn,ObjectServices)`).

After this task, `RewindRoundTripProbe` delegates both the initial construction and the
"second fresh construction" calls to `RewindRoundTripHarness.constructHeadless(cls, stub)`.
The `ConstructionStrategy` enum, `StrategyAndInstance` record, `tryConstruct()`, `tryConstructWith()`,
`constructWith()`, `invokeNoArg()`, `invokeWith()`, and `ThrowingSupplier` interface were all
removed from `RewindRoundTripProbe` — the harness is the single implementation.

---

## UNPROBED_ALLOWANCE

Set to `800` (measured count 760). This is the Phase 1 baseline. As Phase 2 adds codecs,
shrink this ceiling to surface regressions where new objects are added without codecs.
