# Mod Support Phase 0 (Engine Foundations) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the three mod-support foundations: the GamePatch framework with ordered composition (workstream A), the LoadOp/ResourceLoader load-source abstraction (B), and editor completion — spawn editing, collision editing, save payload v2, trace-entry refusal (C).

**Architecture:** A adapts the approved KiS2 framework tasks, replacing its static Task 4 with an engine-owned `ModuleResolutionService` + owner-tagged enablement. B widens `LoadOp` behind existing factories. C adds tracked spawn/collision editing and version-safe envelope migration.

**Tech Stack:** Java 21, existing engine seams only. No new dependencies.

**Specs:** `docs/superpowers/specs/2026-07-09-mod-support-phase0-design.md` (authoritative, incl. its five amendments to the KiS2 artifacts); parent `2026-07-09-mod-support-design.md`; KiS2 design + plan as amended.

## Global Constraints

- **JUnit 5 / Jupiter only.**
- **Commit trailers:** all 7 trailers on every commit; `feat`/`fix` commits touching `src/main/` use `Changelog: n/a: covered by final phase-0 changelog entry in this branch` until the final docs task sets `Changelog: updated`.
- **Never `git add -A`** — exact paths only (shared repo, concurrent sessions).
- **No new singletons; no new Maven dependencies.**
- **Execution branch (user directive 2026-07-10):** implement and commit directly on
  the existing `next` worktree; do not create a phase branch or merge-back commit.
- **Workstream order:** A tasks (A1–A7), then B (B1–B2), then C (C1–C6). A/B/C are independent — a different order or parallel worktrees is fine, but within a workstream the order is load-bearing.
- **The KiS2 plan is the code source for A.** Where a task below says "execute KiS2-plan Task N", open `docs/superpowers/plans/2026-06-12-game-patch-kis2.md`, follow that task's steps/code verbatim, applying only the amendment deltas listed here. Do not re-derive its code.
- **Read-first rule:** recon anchors are from 2026-07-09; before modifying any existing file, read the target region and follow the named methods if lines moved.
- **ArchUnit watch:** new package `com.openggf.game.patch` sits inside the `game` slice (`com.openggf.game.*` → top-level slice `game`), so A adds **no** new top-level edges. B and C stay inside existing `level`/`editor` slices. If `TestArchUnitRules` fails anyway, add exactly the edges its failure message names, with a comment citing this plan.
- **Trace safety:** any task that could affect gameplay behavior ends by running the S3K must-keep-green set: `mvn "-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils" test`.

## 2026-07-10 readiness amendments (authoritative)

These amendments supersede conflicting inherited KiS2 steps and older task prose:

- Workstream A creates an engine-owned `ModuleResolutionService`, not a static mutable
  registry. Registrations carry explicit `PatchOwner.BuiltIn`/`PatchOwner.Mod`, unique
  namespaced patch ids, and registration order. Enablement/order is by owner; unknown
  ids are errors. Metadata failures fail owner + dependents while independent owners
  continue; arbitrary creator-apply failure aborts launch because input mutation cannot
  be rolled back. Only engine-controlled decorator patches use last-good continuation.
- `WorldSession` retains both root and resolved modules. Every gameplay construction
  path—`initializeGame`, data select, time attack, recording restart,
  `AttemptReplayHarness`, trace launch, and `HeadlessGameBoot`—calls the same resolver.
  Add a two-patch repeated-resolution/data-select test proving no double wrapping.
- A7 inventories `currentOrBootstrapGameModule()` as well as the two explicitly named
  bootstrap methods and uses an audited deny-by-default call-site list.
- Workstream B uses the sealed `ModAssetRoot` jar/directory abstraction and all limits
  in `2026-07-10-mod-support-format-security-contracts.md`. Validate compressed mod
  ops, normalized paths, nulls, destinations, and base/overlay order during op/plan
  construction. Use bounded reads and include both a legacy-ROM byte-equality fixture
  and declared-size/streaming-overflow tests.
- C2 preserves source order within `x & 0xFF80` columns; it never fully sorts by X.
  Every object gets a stable unique placement id retained across move/undo/save. A
  module-owned `ObjectPlacementEncoding` handles S1 versus S2/S3K raw fields without
  silent masking. Include `MoveRingSpawnCommand` and duplicate-identical-spawn tests.
- Spawn mutation APIs are identity-safe and atomic; they set editor-modified state for
  user commands and only dirty/resync state for persisted apply. Do not mutate lists
  returned by level accessors.
- C4 commands operate on `MutableLevel` and indices through tracked COW/restore
  methods. They assert dirty/redraw, save state, undo, and rewind snapshot isolation.
  Extract a collision overlay builder with explicit inputs; do not toggle global debug
  state or call package-private renderer internals.
- C5 uses per-version DTOs and verifies v1 against its raw canonical JSON payload
  before migration. Persist stable placement ids and lossless raw fields. Test a real
  v1 JSON/hash fixture, empty v2 replacement, duplicate spawns, and v3 quarantine.
- Controller-visible persistence/apply status drives the S3K sidecar warning.
- Reorder inherited A1/A6 and C3 so the failing test/contract is written and observed
  before production changes. Rendering tests assert marker/text command content.
- Whenever a trace sweep changes `docs/TRACE_FRONTIER_LOG.md`, stage that file in the
  same task commit. Final documentation also updates the README release/change-log
  entry required for integration into `next`.

---

## Workstream A — GamePatch framework + composition

**Not in this plan:** KiS2 content (KiS2-plan Tasks 1, 5–6, 10–12). Execute those from the KiS2 plan afterwards, applying spec Amendment 3 (no `PhysicsFeatureSet`: skip Task 5 Step 2's feature-set decision, drop the `featureSetIsModuleScopedSonic2` test and the `getFeatureSet()` override; Task 1's feature-set pointer feeds the rule-placement decision instead).

### Task A1: GamePatch contracts + DelegatingGameModule + guard test

**Files:** exactly as KiS2-plan Task 2 (package `com.openggf.game.patch`: `GamePatch`, `PatchContext`, `GameplayLaunchRequest`, `LogicalRom`, `DelegatingGameModule`, test `TestDelegatingGameModuleCoversInterface`).

**Interfaces:**
- Consumes: `GameModule` (all abstract + default methods), KiS2-plan Task 2 code.
- Produces (names are KiS2-plan Task 2's, authoritative): `record GameplayLaunchRequest(String gameId, String mainCharacter, List<String> sidekicks)` (the 3-component form — spec Amendment 4); `GamePatch` with `id()/displayName()/baseGameId()/activatesFor(GameplayLaunchRequest)/romPrerequisites() -> Set<LogicalRom>/providedMainCharacters() -> List<String>/apply(GameModule, PatchContext)`; `DelegatingGameModule` forwarding every `GameModule` method; the reflection guard test.

- [ ] **Step 1:** Execute KiS2-plan Task 2 test-first, replacing its no-stack/static-registry wording with owner-tagged composition through `ModuleResolutionService`.
- [ ] **Step 2:** Run the task's tests as specified there. Expected: PASS.
- [ ] **Step 3:** Commit with this plan's trailer convention:

```bash
git add src/main/java/com/openggf/game/patch/ src/test/java/com/openggf/game/patch/
git commit -m "feat: GamePatch contracts, DelegatingGameModule, coverage guard

Changelog: n/a: covered by final phase-0 changelog entry in this branch
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a"
```

---

### Task A2: owner-tagged `PatchEnablement` seam + contract test

**Files:**
- Create: `src/main/java/com/openggf/game/patch/PatchOwner.java`, `PatchEnablement.java`
- Test: `src/test/java/com/openggf/game/patch/TestPatchEnablementContract.java`

**Interfaces:**
- Produces explicit `PatchOwner.BuiltIn`/`PatchOwner.Mod` and owner-keyed enable/order. Built-ins sort before mods; unknown owners are impossible rather than inferred from patch ids.

- [ ] **Step 1: Write the failing test**

```java
package com.openggf.game.patch;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestPatchEnablementContract {

    /** Reference managed implementation used to validate the contract shape. */
    private static PatchEnablement managed(Map<PatchOwner, Integer> orderByEnabledId) {
        return new PatchEnablement() {
            @Override
            public boolean isEnabled(PatchOwner owner) {
                return owner instanceof PatchOwner.BuiltIn
                        || orderByEnabledId.getOrDefault(owner, -1) >= 0;
            }

            @Override
            public int orderOf(PatchOwner owner) {
                return owner instanceof PatchOwner.BuiltIn
                        ? PatchEnablement.BUILTIN_ORDER
                        : orderByEnabledId.getOrDefault(owner, Integer.MAX_VALUE);
            }
        };
    }

    @Test
    void allEnabledEnablesEverythingInRegistrationOrder() {
        assertTrue(PatchEnablement.ALL_ENABLED.isEnabled(new PatchOwner.BuiltIn("kis2")));
        assertTrue(PatchEnablement.ALL_ENABLED.isEnabled(new PatchOwner.Mod("anything")));
    }

    @Test
    void explicitBuiltinsAreOrderedBeforeManagedOwners() {
        PatchOwner a = new PatchOwner.Mod("mod-a");
        PatchOwner b = new PatchOwner.Mod("mod-b");
        PatchEnablement e = managed(Map.of(a, 0, b, 1));
        assertTrue(e.orderOf(new PatchOwner.BuiltIn("kis2")) < e.orderOf(a));
        assertTrue(e.orderOf(a) < e.orderOf(b));
    }

    @Test
    void sameLocalPatchIdCanBelongToDistinctOwners() {
        assertNotEquals(new PatchOwner.Mod("a"), new PatchOwner.Mod("b"));
    }
}
```

- [ ] **Step 2:** Run `mvn "-Dtest=com.openggf.game.patch.TestPatchEnablementContract" test` — COMPILE FAILURE.
- [ ] **Step 3: Implement**

`PatchOwner.java`:

```java
package com.openggf.game.patch;

public sealed interface PatchOwner permits PatchOwner.BuiltIn, PatchOwner.Mod {
    record BuiltIn(String id) implements PatchOwner {}
    record Mod(String modId) implements PatchOwner {}
}
```

`PatchEnablement.java`:

```java
package com.openggf.game.patch;

/**
 * Enablement + ordering gate for patch composition (Phase 0 spec Amendment 2).
 *
 * Enablement is based on explicit owner provenance, never patch-id inference.
 */
public interface PatchEnablement {

    int BUILTIN_ORDER = -1;

    boolean isEnabled(PatchOwner owner);

    int orderOf(PatchOwner owner);

    /** Default: everything enabled, everything unmanaged (registration order). */
    PatchEnablement ALL_ENABLED = new PatchEnablement() {
        @Override
        public boolean isEnabled(PatchOwner owner) {
            return true;
        }

        @Override
        public int orderOf(PatchOwner owner) {
            return owner instanceof PatchOwner.BuiltIn ? BUILTIN_ORDER : 0;
        }
    };
}
```

- [ ] **Step 4:** Run the test — all 3 PASS.
- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/game/patch/PatchOwner.java src/main/java/com/openggf/game/patch/PatchEnablement.java src/test/java/com/openggf/game/patch/TestPatchEnablementContract.java
git commit -m "feat: owner-tagged PatchEnablement seam

Changelog: n/a: covered by final phase-0 changelog entry in this branch
Guide: n/a
Known-Discrepancies: n/a
S3K-Known-Discrepancies: n/a
Agent-Docs: n/a
Configuration-Docs: n/a
Skills: n/a"
```

---

### Task A3: Logical ROM resolver

- [ ] **Step 1:** Execute KiS2-plan Task 3 verbatim (LogicalRom enum, combined-ROM backend via `RomManager.getSecondaryRom("s3k")`, `>= 0x200000` bounds guard, tests). No amendments apply.
- [ ] **Step 2:** Run its tests — PASS. Commit per its staging list with this plan's trailer convention (subject `feat: logical ROM resolver for patch prerequisites`).

---

### Task A4: engine-owned `ModuleResolutionService` (replaces KiS2-plan Task 4)

**Files:**
- Create: `RegisteredPatch.java`, `ResolutionContext.java`, `ResolutionResult.java`, `ModuleResolutionService.java` in `com.openggf.game.patch`
- Test: `src/test/java/com/openggf/game/patch/TestModuleResolutionService.java`

**Interfaces:**
- Consumes: `GamePatch`, `PatchContext`, `GameplayLaunchRequest`, `PatchEnablement` (A1/A2), logical-ROM resolver (A3). Read KiS2-plan Task 4 first.
- Produces a boot-owned service for immutable built-in registrations and a fresh
  `ResolutionContext` per launch combining them with a frozen mod plan, owner
  dependency graph, enablement, and session-local failures. Duplicate owner/id is
  rejected. Metadata failures fail owner + transitive dependents; arbitrary creator
  apply failure returns a typed launch-abort. Only engine-generated decorator-only
  backing patches may continue from last-good. No static or accumulating mod state.

- [ ] **Step 1:** Read KiS2-plan Task 4 for prerequisite/context logic, then write the
  instance-service tests before production code. Do not inherit its static facade.
- [ ] **Step 2: Write the failing test**

```java
package com.openggf.game.patch;

import com.openggf.game.GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestModuleResolutionService {

    /** Marker each stub patch layers on so composition order is observable. */
    interface PatchTrail {
        List<String> appliedPatchIds();
    }

    // NOTE: Task 4's own FakePatch returns a bare DelegatingGameModule and does
    // NOT record a trail — this trail-recording stub is NEW code in this test.
    // Method names/ctor per KiS2-plan Task 2's real signatures (verify in Step 1).
    private static GamePatch stubPatch(String id, String baseGameId, boolean activates) {
        return new GamePatch() {
            @Override public String id() { return id; }
            @Override public String displayName() { return id; }
            @Override public String baseGameId() { return baseGameId; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) { return activates; }
            @Override public java.util.Set<LogicalRom> romPrerequisites() { return java.util.Set.of(); }
            @Override public List<String> providedMainCharacters() { return List.of(); }
            @Override public GameModule apply(GameModule base, PatchContext ctx) {
                List<String> trail = new ArrayList<>(
                        base instanceof PatchTrail t ? t.appliedPatchIds() : List.of());
                trail.add(id);
                class Patched extends DelegatingGameModule implements PatchTrail {
                    Patched() { super(base, id); }
                    @Override public List<String> appliedPatchIds() { return List.copyOf(trail); }
                }
                return new Patched();
            }
        };
    }

    private static GameplayLaunchRequest anyRequest() {
        return new GameplayLaunchRequest("s2", "sonic", List.of());
    }

    @Test
    void zeroSurvivorsReturnsBaseInstanceUnchanged() {
        ModuleResolutionService service = ModuleResolutionService.forTests(PatchEnablement.ALL_ENABLED);
        GameModule base = new Sonic2GameModule();
        var context = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, List.of(
                new RegisteredPatch(new PatchOwner.Mod("test"), "test:p1",
                        stubPatch("p1", "s2", false), 0)), Map.of());
        assertSame(base, ((ResolutionResult.Resolved)
                service.resolve(context, base, anyRequest())).module());
    }

    @Test
    void survivorsComposeInEnablementThenRegistrationOrder() {
        PatchOwner builtin = new PatchOwner.BuiltIn("builtin");
        PatchOwner modB = new PatchOwner.Mod("mod-b");
        PatchOwner modA = new PatchOwner.Mod("mod-a");
        PatchEnablement policy = new PatchEnablement() {
            @Override public boolean isEnabled(PatchOwner owner) { return !owner.equals(modB); }
            @Override public int orderOf(PatchOwner owner) {
                return owner instanceof PatchOwner.BuiltIn ? -1 : owner.equals(modA) ? 0 : 1;
            }
        };
        ModuleResolutionService service = ModuleResolutionService.forTests(policy);
        var context = ResolutionContext.forTests(policy, List.of(
                new RegisteredPatch(builtin, "builtin", stubPatch("builtin", "s2", true), 0),
                new RegisteredPatch(modB, "mod-b:patch", stubPatch("mod-b", "s2", true), 1),
                new RegisteredPatch(modA, "mod-a:patch", stubPatch("mod-a", "s2", true), 2)), Map.of());
        GameModule resolved = ((ResolutionResult.Resolved)
                service.resolve(context, new Sonic2GameModule(), anyRequest())).module();
        assertEquals(List.of("builtin", "mod-a"), ((PatchTrail) resolved).appliedPatchIds());
    }

    @Test
    void wrongBaseGameNeverApplies() {
        ModuleResolutionService service = ModuleResolutionService.forTests(PatchEnablement.ALL_ENABLED);
        GameModule base = new Sonic2GameModule();
        var context = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, List.of(
                new RegisteredPatch(new PatchOwner.Mod("test"), "test:p1",
                        stubPatch("p1", "s3k", true), 0)), Map.of());
        assertSame(base, ((ResolutionResult.Resolved)
                service.resolve(context, base, anyRequest())).module());
    }
}
```

(Add duplicate-owner/id and predicate/apply-throw tests before implementing. Adapt only
fixture constructors to landed signatures; the instance/owner/fault contracts are fixed.)

- [ ] **Step 3:** Run `mvn "-Dtest=com.openggf.game.patch.TestModuleResolutionService" test` — COMPILE FAILURE.
- [ ] **Step 4: Implement** the instance service and owner-tagged registration:

```java
public ResolutionResult resolve(ResolutionContext context, GameModule base,
        GameplayLaunchRequest request) {
    List<RegisteredPatch> ordered = context.topologicallyOrderedFor(base);
    for (RegisteredPatch rp : ordered) {
        if (context.enablement().isEnabled(rp.owner())
                && !context.ownerOrDependencyFailed(rp.owner())) {
            context.evaluateMetadataOrFailOwnerAndDependents(
                    rp, request, this::romPrerequisitesSatisfied);
        }
    }
    List<RegisteredPatch> survivors = ordered.stream()
            .filter(rp -> context.enablement().isEnabled(rp.owner()))
            .filter(rp -> !context.ownerOrDependencyFailed(rp.owner()))
            .sorted(Comparator
                    .comparingInt((RegisteredPatch rp) -> context.enablement().orderOf(rp.owner()))
                    .thenComparingLong(RegisteredPatch::registrationIndex))
            .toList();
    GameModule module = base;
    for (RegisteredPatch rp : survivors) {
        module = applyOrAbort(context, rp, module, patchContextFor(rp.patch()));
    }
    return new ResolutionResult.Resolved(module);
}
```

`RegisteredPatch` contains owner, namespaced id, patch, and long registration index.
The service is owned by engine context and injected into launch/profile seams.

- [ ] **Step 5:** Run tests; port prerequisite/character coverage. Add a repeated
  data-select resolution test that always supplies `WorldSession.rootGameModule()` and
  proves two stacked decorators do not double-wrap. Add a dependent registered before
  its later-failing dependency and prove neither reaches apply.
  Add a disabled owner with a throwing predicate and prove metadata is never invoked.
- [ ] **Step 6: Commit** (`feat: engine-owned patch module resolution service`; stage exact files).

> **Spec-acceptance note:** the spec's §A acceptance cites "KiS2-plan Task 13's
> headless integration test shape". Real KiS2 content stays in its follow-on task
> sequence on `next`;
> Phase 0 maps the criterion to A4's composition matrix plus A6's fake stacked-patch
> `HeadlessGameBoot` test. This is an explicit substitute, not a dropped criterion.

---

### Task A5: LaunchProfile availability union

- [ ] **Step 1:** Write the failing availability-union tests, then adapt KiS2-plan Task 7 to call the injected `ModuleResolutionService` (no static facade) from the launch-profile construction seam.
- [ ] **Step 2:** Its tests PASS; commit per its staging list, this plan's trailers.

---

### Task A6: Choke-point wiring + save-context sanitization

- [ ] **Step 1:** Write failing source/integration contracts for every table entry in
  spec Amendment 4, then wire the injected resolver at `initializeGame`, data select,
  time attack, recording restart, `AttemptReplayHarness`, trace launch, and
  `HeadlessGameBoot`. Store root + resolved modules in `WorldSession`; deterministic
  paths inject mod-disabled policy before scanning.
   - Requests are the 3-component record (already Task 2's shape).
   - The trace path needs **no** extra wiring (spec Amendment 4: `TraceReplaySessionBootstrap.prepareConfiguration` already writes the character config keys before launch, so `initializeGame`'s synthesis covers traces).
   - **Task 8's `TestPatchResolutionAtBoot` cannot be taken verbatim** — it registers `com.openggf.game.sonic2.kis2.Kis2GamePatch` and asserts on `Kis2PhysicsProvider`, classes that only exist after the KiS2-content tasks this plan defers. Substitute a local fake patch (Task 7's inline `knucklesPatch()` stub idiom, or A4's trail stub): register it, assert a Knuckles-request launch resolves a decorated module (`instanceof DelegatingGameModule` / the stub's marker) and a Sonic-request launch resolves the same base instance. Keep every other assertion of that test.
- [ ] **Step 2:** Full `mvn test` + the S3K must-keep-green set + a trace spot sweep (`s1_ghz1`, `s2_ehz1`, `s3k_aiz1` replay classes) — all green: with no patches registered, `resolveModule` returns the base instance and behavior is bit-identical.
- [ ] **Step 3:** Commit per KiS2-plan Task 8/9 staging, this plan's trailers. Update `docs/TRACE_FRONTIER_LOG.md` with the sweep (command, commit, pass/fail) per repo policy.

---

### Task A7: Bootstrap-bypass audit + guard test

**Files:**
- Test: `src/test/java/com/openggf/game/patch/TestBootstrapModuleProviderCachingGuard.java`

**Interfaces:** scanner-based source test in the `TestObjectServicesMigrationGuard` idiom (read that class first and copy its file-walking/allowlist mechanics).

- [ ] **Step 1:** Inventory `bootstrapGameModule()`, `getBootstrapDefault()`, and
  `currentOrBootstrapGameModule()` including local assignments/helper calls; audit
  every production call site.
- [ ] **Step 2:** Write a deny-by-default call-site guard with a documented allowlist;
  do not rely on a chained-provider regex that misses split assignments.
- [ ] **Step 3:** Run it — PASS (if it fails, the failure is a real Phase 0 finding: fix the consumer to re-resolve via the session module, then re-run).
- [ ] **Step 4:** Commit (`test: guard against provider caching from bootstrap module`; this plan's trailers).

---

## Workstream B — Load-source abstraction

### Task B1: `LoadSource` + widened `LoadOp`

**Files:**
- Create: `src/main/java/com/openggf/level/resources/LoadSource.java`,
  `src/main/java/com/openggf/io/ModAssetRoot.java`, `ModInputLimits.java`,
  `ModKeySyntax.java`
- Modify: `src/main/java/com/openggf/level/resources/LoadOp.java`
- Test: `TestLoadOpSources`, `com.openggf.io.TestModAssetRoot`, `TestModKeySyntax`

**Interfaces:**
- Produces:
  - `ModAssetRoot extends AutoCloseable` with bounded jar/directory implementations, normalized path validation, real-path containment, duplicate/case-collision rejection, `readBounded`, and `describe`.
  - `sealed interface LoadSource` with `RomAddress(int)` and `ModAsset(ModAssetRoot root, String entryPath)`.
  - `record LoadOp(LoadSource source, CompressionType compressionType, int destOffsetBytes)`.
  - **Every existing factory static keeps its exact signature** (`base(int, CompressionType)`, `overlay`, `append`, `kosinskiBase/Overlay/Append`, `kosinskiMBase/MOverlay/MAppend`, `uncompressedBase` — read the current file for the authoritative list) and wraps the int in `RomAddress`.
  - New factories take `ModAssetRoot`; all are uncompressed. The `LoadOp` canonical constructor rejects nulls, compressed mod sources, invalid paths, and invalid destination values; plan build validates base/overlay order and aggregate budget.
  - Compat accessor `int romAddr()` — returns `RomAddress.addr()`, throws `IllegalStateException` for mod sources; Javadoc marks it legacy-compat (Phase 2 may remove).

- [ ] **Step 1: Read `LoadOp.java` fully** (current recon: 3-component record, factory statics, `APPEND_TO_PREVIOUS` sentinel, `appendsToPrevious()`).
- [ ] **Step 2: Write the failing test**

```java
package com.openggf.level.resources;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestLoadOpSources {

    @Test
    void romFactoriesProduceRomAddressSourcesAndCompatAccessor() {
        LoadOp op = LoadOp.kosinskiBase(0x123456);
        assertEquals(new LoadSource.RomAddress(0x123456), op.source());
        assertEquals(0x123456, op.romAddr());
        assertEquals(CompressionType.KOSINSKI, op.compressionType());
    }

    @Test
    void appendSentinelBehaviorUnchanged() {
        assertTrue(LoadOp.kosinskiAppend(0x10).appendsToPrevious());
        assertFalse(LoadOp.kosinskiBase(0x10).appendsToPrevious());
    }

    @Test
    void modAssetFactoriesAreUncompressedAndRejectRomAddrAccessor() {
        ModAssetRoot root = ModAssetRoot.forTests("m");
        LoadOp op = LoadOp.modAssetBase(root, "assets/patterns.bin");
        assertEquals(CompressionType.UNCOMPRESSED, op.compressionType());
        assertInstanceOf(LoadSource.ModAsset.class, op.source());
        assertThrows(IllegalStateException.class, op::romAddr);
        LoadOp overlay = LoadOp.modAssetOverlay(root, "a.bin", 0x80);
        assertEquals(0x80, overlay.destOffsetBytes());
        assertTrue(LoadOp.modAssetAppend(root, "a.bin").appendsToPrevious());
    }
}
```

- [ ] **Step 3:** Run `mvn "-Dtest=com.openggf.level.resources.TestLoadOpSources" test` — COMPILE FAILURE.
- [ ] **Step 4: Implement.** `LoadSource.java`:

```java
package com.openggf.level.resources;

import java.nio.file.Path;

/**
 * Where a LoadOp reads its bytes. RomAddress is the historical behavior
 * (byte-identical); ModAsset reads a bounded entry from a validated asset root.
 */
public sealed interface LoadSource {

    record RomAddress(int addr) implements LoadSource {
    }

    record ModAsset(ModAssetRoot root, String entryPath) implements LoadSource {
    }
}
```

`ModAssetRoot.java` / `ModInputLimits.java` public contract (implement jar/directory
internals with counting streams and real-path containment from the shared spec):

```java
public sealed interface ModAssetRoot extends AutoCloseable
        permits JarModAssetRoot, DirectoryModAssetRoot, InMemoryModAssetRoot {
    byte[] readBounded(String normalizedEntry, long maxBytes) throws IOException;
    String describe();
    static ModAssetRoot jar(Path declaredRoot, Path jar, ModInputLimits limits) throws IOException {
        return new JarModAssetRoot(declaredRoot, jar, limits);
    }
    static ModAssetRoot jar(Path declaredRoot, Path jar) throws IOException {
        return jar(declaredRoot, jar, ModInputLimits.production());
    }
    static ModAssetRoot directory(Path declaredRoot, Path directory, ModInputLimits limits) throws IOException {
        return new DirectoryModAssetRoot(declaredRoot, directory, limits);
    }
    static ModAssetRoot directory(Path declaredRoot, Path directory) throws IOException {
        return directory(declaredRoot, directory, ModInputLimits.production());
    }
    static ModAssetRoot forTests(String description, ModInputLimits limits) {
        return new InMemoryModAssetRoot(description, limits);
    }
    static ModAssetRoot forTests(String description) {
        return forTests(description, ModInputLimits.production());
    }
}

public final class ModInputLimits {
    // One DEFAULT_* constant for every row in the shared limits table.
    public static final long DEFAULT_MAX_ASSET_BYTES = 64L * 1024 * 1024;
    public static final long DEFAULT_MAX_MOD_VALIDATION_BYTES = 512L * 1024 * 1024;
    public static final int DEFAULT_MAX_MOD_JARS = 1_024;
    public static final long DEFAULT_MAX_REPOSITORY_VALIDATION_BYTES = 8L * 1024 * 1024 * 1024;
    public static ModInputLimits production();
    public static Builder loweringBuilder();
    // Accessors cover every row in the shared limits table. Builder values must be
    // positive and <= their DEFAULT_* value; it exists for deterministic small tests.
}
```

`LoadOp.java`: widen to the source record, retain ROM factories, add root-based mod
factories, and enforce constructor/build validation. Implement shared
`ModKeySyntax.requireManifestId/requireOwnedKey` with the cross-spec golden tests. Root
creators own closure: runtime roots close at engine shutdown; converter/test roots use
try-with-resources. `ModAssetRoot`, repository scanners, metadata parsers, converters,
and audio preparation receive an immutable `ModInputLimits`; production wiring always
passes `ModInputLimits.production()`, while tests use only `loweringBuilder()`. Test
that upward overrides are rejected, plus declared-root/jar symlink escape, path/case
collisions, declared and streaming overflow, and close behavior. Production jar roots
and trusted-dev directory roots copy into private temp-disk snapshots at construction
and serve every later read from the snapshot; verify source mutation after construction
is invisible and close deletes only the owned snapshot. Directory roots are rejected
outside explicit dev/test composition. Charge thread-safe actual cumulative read bytes
against `maxModValidationBytes` (successful repeated reads charge again; failed reads
roll back their reservation).

```java
/**
 * Legacy accessor for ROM-sourced ops. Prefer source() in new code; Phase 2
 * may remove this once ad-hoc art loads migrate to source-typed factories.
 */
public int romAddr() {
    if (source instanceof LoadSource.RomAddress rom) {
        return rom.addr();
    }
    throw new IllegalStateException("LoadOp has a non-ROM source: " + source);
}
```

- [ ] **Step 5:** Run the new test AND the package's existing tests (`mvn "-Dtest=com.openggf.level.resources.*" test`, plus `LevelResourceOverlayTest` wherever it lives — find it by name) — all PASS; then `mvn package` to prove the ~30 factory call sites compile untouched.
- [ ] **Step 6: Commit** (`feat: LoadSource abstraction behind LoadOp factory statics`; stage the two main files + test; this plan's trailers).

---

### Task B2: `ResourceLoader` source dispatch

**Files:**
- Modify: `src/main/java/com/openggf/level/resources/ResourceLoader.java`
- Test: `src/test/java/com/openggf/level/resources/TestResourceLoaderModSources.java`

**Interfaces:**
- Consumes: `LoadSource`/`LoadOp` (B1); jar-writing test helper idiom from `TestModRepositoryScanner.writeJar` (Phase 1 plan Task 2 — if Phase 1 hasn't landed yet, inline the same ~10-line `JarOutputStream` helper in this test).
- Produces: `decompress(LoadOp)` dispatches ROM byte-identically and reads mod data only through `root.readBounded(entryPath, limits.maxAssetBytes())`. Compression/source/sequence validation already occurred at construction/build. Logging uses `root.describe() + "!" + entryPath` and never calls `romAddr()` eagerly. Aggregate plan reads reserve/check the shared budget before allocation.

- [ ] **Step 1: Read `ResourceLoader.java` fully** (recon: `decompress` switch, `UNCOMPRESSED` throws, all reads via `Rom`).
- [ ] **Step 2: Write the failing test**

```java
package com.openggf.level.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TestResourceLoaderModSources {

    static void writeJar(Path jar, Map<String, byte[]> entries) throws Exception {
        try (OutputStream fileOut = Files.newOutputStream(jar);
             JarOutputStream out = new JarOutputStream(fileOut)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                out.putNextEntry(new JarEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
    }

    @Test
    void modAssetOpsComposeWithOverlaysWithoutRom(@TempDir Path tmp) throws Exception {
        Path jar = tmp.resolve("m.jar");
        writeJar(jar, Map.of(
                "base.bin", new byte[]{1, 1, 1, 1},
                "overlay.bin", new byte[]{9, 9}));
        ResourceLoader loader = new ResourceLoader(null); // no ROM needed for mod sources
        try (ModAssetRoot root = ModAssetRoot.jar(tmp, jar)) {
            byte[] composed = loader.loadWithOverlays(List.of(
                    LoadOp.modAssetBase(root, "base.bin"),
                    LoadOp.modAssetOverlay(root, "overlay.bin", 1)), 4);
            assertArrayEquals(new byte[]{1, 9, 9, 1}, composed);
        }
    }

    @Test
    void modAssetAppendGrowsBuffer(@TempDir Path tmp) throws Exception {
        Path jar = tmp.resolve("m.jar");
        writeJar(jar, Map.of("a.bin", new byte[]{1, 2}, "b.bin", new byte[]{3, 4}));
        try (ModAssetRoot root = ModAssetRoot.jar(tmp, jar)) {
            byte[] composed = new ResourceLoader(null).loadWithOverlays(List.of(
                    LoadOp.modAssetBase(root, "a.bin"),
                    LoadOp.modAssetAppend(root, "b.bin")), 2);
            assertArrayEquals(new byte[]{1, 2, 3, 4}, composed);
        }
    }

    @Test
    void missingEntryAndCompressedModSourceFailClearly(@TempDir Path tmp) throws Exception {
        Path jar = tmp.resolve("m.jar");
        writeJar(jar, Map.of("a.bin", new byte[]{1}));
        try (ModAssetRoot root = ModAssetRoot.jar(tmp, jar)) {
            ResourceLoader loader = new ResourceLoader(null);
            assertThrows(IOException.class, () -> loader.loadSingle(LoadOp.modAssetBase(root, "nope.bin")));
            assertThrows(IllegalArgumentException.class, () ->
                    new LoadOp(new LoadSource.ModAsset(root, "a.bin"), CompressionType.KOSINSKI, 0));
        }
    }
}
```

Note: if `ResourceLoader`'s constructor rejects a null `Rom` (read it in Step 1), add an overload/factory `ResourceLoader.forModAssetsOnly()` or make the `Rom` nullable with a null-check only on the ROM branch — pick whichever the file's style supports; the test's intent (mod ops need no ROM) is the contract.

- [ ] **Step 3:** Run — COMPILE FAILURE / test failure.
- [ ] **Step 4: Implement.** In `decompress(LoadOp)`:

```java
// Plus: replace op.romAddr() in the LOG.fine(...) format calls with
// describeSource(op) AND switch those format specifiers from 0x%06X to %s.
private static String describeSource(LoadOp op) {
    if (op.source() instanceof LoadSource.RomAddress rom) {
        return String.format("ROM 0x%06X", rom.addr());
    }
    LoadSource.ModAsset asset = (LoadSource.ModAsset) op.source();
    return asset.root().describe() + "!" + asset.entryPath();
}

private byte[] decompress(LoadOp op) throws IOException {
    if (op.source() instanceof LoadSource.ModAsset modAsset) {
        return modAsset.root().readBounded(
                modAsset.entryPath(), limits.maxAssetBytes());
    }
    int romAddr = op.romAddr();
    // ... existing switch, verbatim, using romAddr ...
}

```

- [ ] **Step 5:** Add a legacy-ROM byte-equality fixture plus declared-size,
  streaming-overflow, aggregate-budget, missing-entry, directory-root, and close tests.
  Run focused tests, the S3K set with `-Ds3k.rom.path=s3k.gen`, and the S2 HTZ trace
  gate; compare with the `next` branch baseline and stage any trace-log update.
- [ ] **Step 6: Commit** (`feat: ResourceLoader reads mod-jar assets beside ROM sources`; this plan's trailers).

---

## Workstream C — Editor completion

### Task C1: Trace-mode editor-entry refusal (new production code)

**Files:**
- Modify: `src/main/java/com/openggf/Engine.java` (`toggleEditorPlaytestMode()`, ~line 1156) and/or `src/main/java/com/openggf/GameLoop.java` (editor toggle branch, ~867–878 — put the check at whichever single choke point both paths flow through; read both first)
- Test: `src/test/java/com/openggf/editor/TestEditorTraceModeGuard.java`

- [ ] **Step 1:** Read both toggle paths. The refusal: when `TraceSessionLauncher.active() != null`, decline editor entry with a log line, mirroring the adjacent trace gates in `GameLoop.stepInternal` (~826/833/891).
- [ ] **Step 2:** Write the failing test against a **pure decision helper** — extract `static boolean editorEntryAllowed(boolean editorEnabled, boolean traceActive)` (or equivalent) so the rule is testable without booting Engine or touching `TraceSessionLauncher` state: false whenever `traceActive`, else `editorEnabled`. The production toggle path passes `TraceSessionLauncher.active() != null` as the second argument. (If an integration-level assertion is wanted too, the existing precedent for manipulating the launcher is reflection on its private static `activeSession` field — see `TestGameLoopSpecialStageSkipGate` ~line 117; do NOT add a package-visible seam, the test lives in a different package.)
- [ ] **Step 3:** Implement the pure decision helper + wire it into the toggle path(s); run the test — PASS.
- [ ] **Step 4:** Commit (`fix: refuse editor entry during trace sessions`; this plan's trailers, `Changelog: n/a: covered by final phase-0 changelog entry in this branch`).

---

### Task C2: Object/ring spawn editing commands

**Files:**
- Create: object place/move/delete and ring place/move/delete command classes
- Create: `src/main/java/com/openggf/editor/EditorStockObjectPalette.java`
- Create: `src/main/java/com/openggf/editor/EditorSpawnFactory.java`, `src/main/java/com/openggf/level/objects/ObjectPlacementEncoding.java`, S1/common-S2-S3K encoding implementations
- Modify: `GameModule`, `DelegatingGameModule`, `Sonic1GameModule`, `Sonic2GameModule`, `Sonic3kGameModule` and the delegation guard
- Modify: `src/main/java/com/openggf/level/MutableLevel.java` (identity-safe insert/move/delete/replace APIs)
- Modify: `src/main/java/com/openggf/editor/LevelEditorController.java`, `src/main/java/com/openggf/editor/EditorInputHandler.java`, `src/main/java/com/openggf/editor/EditorFocusRegion.java` (or a new spawn-edit sub-mode enum — follow the file's structure)
- Test: `src/test/java/com/openggf/editor/TestEditorSpawnCommands.java`,
  `src/test/java/com/openggf/editor/TestEditorStockObjectPalette.java`

**Interfaces:**
- Consumes: `MutableLevel.addObjectSpawn/removeObjectSpawn/moveObjectSpawn/addRingSpawn/removeRingSpawn` (exist, zero callers — recon), `ObjectSpawn` record (`x, y, objectId, subtype, renderFlags, respawnTracked, rawYWord, layoutIndex`), `EditorCommand` interface, `EditorHistory`.
- Produces: six undoable commands (object place/move/delete and ring place/move/delete); a module-owned `ObjectPlacementEncoding` plus editor placement-id allocator; and identity-safe `MutableLevel` insert/move/delete/replace APIs. `EditorStockObjectPalette` browses ids `0x00..0xFF`, labels them through the active `ObjectRegistry`, navigates with logical keyboard/gamepad actions, and edits subtype; Phase 2 appends mod-key entries. Insertions go after existing entries in the target `x & 0xFF80` column and preserve source order within that column; they are **not** fully X-sorted. Every re-add path retains the stable placement id. User commands mark modified + dirty; persisted replacement marks dirty without pretending to be a new user edit. Controller verbs `placeObjectSpawnAtCursor`, `deleteSpawnAtCursor`, `moveSelectedSpawn`, and `eyedropSpawnAtCursor` use the same APIs; ring commands mirror the verified ring ordering rule.

- [ ] **Step 1: Read first:** `ObjectSpawn`, `ObjectRegistry`, S1 and common S2/S3K placement decoders, `AbstractPlacementManager`'s chunk-column stable-order rule, `MutableLevel` spawn methods, `PlaceBlockCommand`, and both object/ring resync paths. Write failing tests first for palette wrap/navigation/name/subtype/eyedrop behavior, the known S1 out-of-full-X-order case, and two byte-identical duplicate spawns with distinct placement ids.
- [ ] **Step 2: Write the failing test** — command-level, no GL:

```java
package com.openggf.editor;

import com.openggf.editor.commands.DeleteObjectSpawnCommand;
import com.openggf.editor.commands.MoveObjectSpawnCommand;
import com.openggf.editor.commands.PlaceObjectSpawnCommand;
import com.openggf.level.MutableLevel;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestEditorSpawnCommands {

    // Build a minimal MutableLevel the way TestEditorCommands does (read that
    // test first and reuse its fixture helper for a small in-memory level).

    @Test
    void placeAppliesAndUndoRemoves() {
        MutableLevel level = fixtureLevel();
        int before = level.getObjects().size();
        ObjectSpawn spawn = EditorSpawnFactory.createObjectSpawn(512, 256, 0x26, 0, 0, true);
        PlaceObjectSpawnCommand cmd = new PlaceObjectSpawnCommand(level, spawn);
        cmd.apply();
        assertEquals(before + 1, level.getObjects().size());
        assertTrue(level.consumeObjectsDirty());
        cmd.undo();
        assertEquals(before, level.getObjects().size());
    }

    @Test
    void moveIsUndoableAndPreservesIdentityFields() {
        MutableLevel level = fixtureLevel();
        ObjectSpawn spawn = EditorSpawnFactory.createObjectSpawn(512, 256, 0x26, 3, 0, true);
        new PlaceObjectSpawnCommand(level, spawn).apply();
        MoveObjectSpawnCommand move = new MoveObjectSpawnCommand(level, spawn, 528, 256);
        move.apply();
        assertTrue(level.getObjects().stream()
                .anyMatch(s -> s.x() == 528 && s.objectId() == 0x26 && s.subtype() == 3));
        move.undo();
        assertTrue(level.getObjects().stream().anyMatch(s -> s.x() == 512));
    }

    @Test
    void deleteIsUndoable() {
        MutableLevel level = fixtureLevel();
        ObjectSpawn spawn = EditorSpawnFactory.createObjectSpawn(512, 256, 0x26, 0, 0, true);
        new PlaceObjectSpawnCommand(level, spawn).apply();
        DeleteObjectSpawnCommand del = new DeleteObjectSpawnCommand(level, spawn);
        del.apply();
        assertFalse(level.getObjects().contains(spawn));
        del.undo();
        assertTrue(level.getObjects().contains(spawn));
    }

    @Test
    void insertionPreservesSourceOrderWithinPlacementColumn() {
        MutableLevel level = fixtureLevel();
        new PlaceObjectSpawnCommand(level,
                encodedSpawn(0x180, 0, 1)).apply();
        new PlaceObjectSpawnCommand(level,
                encodedSpawn(0x140, 0, 2)).apply();
        // Same 0x80 column: retain source/editor insertion order, even though full X
        // order is descending. This preserves S1 slot cadence.
        assertEquals(List.of(0x180, 0x140), editorObjectXs(level));
    }

    @Test
    void identicalSpawnsRetainDistinctStablePlacementIdsAcrossUndoAndMove() {
        ObjectSpawn a = encodedSpawn(0x200, 0x100, 1);
        ObjectSpawn b = encodedSpawn(0x200, 0x100, 1);
        assertNotEquals(a.layoutIndex(), b.layoutIndex());
    }

    private static MutableLevel fixtureLevel() { return EditorTestFixtures.mutableLevel(); }
}
```

(Adapt accessor names — `ObjectSpawn.x()` etc. — to the real record read in Step 1; `consumeObjectsDirty` name per recon. Ring commands get two analogous tests.)

- [ ] **Step 3:** COMPILE FAILURE, then implement commands (each ~15 lines, `PlaceBlockCommand` shape: hold the level + the spawn (+ old/new position for move), `apply`/`undo` call the `MutableLevel` methods), the factory, controller verbs, and input wiring per the read-first findings.
- [ ] **Step 4:** Tests PASS; `mvn "-Dtest=com.openggf.editor.*" test` for the existing editor suite — no regressions.
- [ ] **Step 5: Commit** (`feat: editor object and ring spawn placement commands`; this plan's trailers).

---

### Task C3: Spawn overlay rendering

**Files:**
- Modify: `src/main/java/com/openggf/editor/render/EditorWorldOverlayRenderer.java` (+ `EditorOverlayRenderer` orchestration if needed)

- [ ] **Step 1:** Read `EditorWorldOverlayRenderer` + `EditorTextRenderer`. Add a spawn-markers pass: for each `level.getObjects()` / `getRings()` entry in the visible region, draw a marker + `%02X:%02X`-formatted id/subtype text at world coords (rings: a simple marker). Toggle rides the spawn-edit sub-mode (markers always on while in it; off otherwise).
- [ ] **Step 2:** Add a renderer-command test asserting object/ring marker coordinates
  and id/subtype text, plus the existing headless smoke.
- [ ] **Step 3:** Commit (`feat: editor spawn overlay markers`; this plan's trailers).

---

### Task C4: Collision editing commands

**Files:**
- Create: `src/main/java/com/openggf/editor/commands/CycleCellCollisionModeCommand.java`, `SetChunkSolidTileIndexCommand.java`
- Modify: controller/input plus `EditorOverlayRenderer`; create a reusable collision-command builder with explicit level/camera/visibility inputs
- Test: `src/test/java/com/openggf/editor/TestEditorCollisionCommands.java`

**Interfaces:**
- Consumes `MutableLevel`, block/chunk indices, and tracked COW/restore methods.
- Produces `CycleCellCollisionModeCommand(level, blockIndex, cellIndex, path)` and
  `SetChunkSolidTileIndexCommand(level, chunkIndex, path, newIndex)`. Apply constructs
  copied state and routes through tracked setters; undo restores saved state. Direct
  `Block`/`Chunk`/`ChunkDesc` mutation is forbidden.

- [ ] **Step 1:** Read `ChunkDesc`, `Chunk`, and how `DeriveBlockFromChunksCommand` addresses block cells (reuse its addressing). Confirm which layer bit (`0x3000` vs `0xC000`) maps to which collision path (primary bits 0x0C/0x0D vs secondary 0x0E/0x0F per the dual-path model) and document in the command Javadoc.
- [ ] **Step 2:** Failing tests: four-mode cycle; exact undo; solid-index round trip;
  dirty/redraw propagation; editor-modified state; and rewind snapshot isolation.
- [ ] **Step 3:** Implement + wire input + overlay toggle; tests PASS; editor suite green.
- [ ] **Step 4:** Commit (`feat: editor collision mode and solid-tile-index editing`; this plan's trailers).

---

### Task C5: Save payload v2 (spawns) + reader compat + toolbar warning

**Files:**
- Modify: `src/main/java/com/openggf/editor/persistence/EditorSavePayload.java`, `EditorSaveEnvelope.java` (if version lives there), `EditorSaveManager.java`, `src/main/java/com/openggf/editor/render/EditorToolbarRenderer.java`
- Test: extend `src/test/java/com/openggf/editor/persistence/TestEditorSaveManager.java`

**Interfaces:**
- Produces: version-specific v1/v2 DTOs; v2 full object/ring tables with stable placement ids and lossless game-specific raw placement fields as pinned by the format/security contract; atomic `MutableLevel.replaceObjectSpawns/replaceRingSpawns` apply APIs; and controller-visible persistence/apply status for the toolbar. Verify a v1 hash from the raw canonical payload tree before migration. V1 missing tables mean untouched; v2 missing/empty tables mean replace with empty; version > 2 quarantines.

- [ ] **Step 1:** Read persistence/apply code and write failing tests for: a checked-in
  genuine v1 JSON + historical canonical hash; v1 leaves spawns untouched; v2 full
  round trip; v2 explicitly empty replaces with empty; duplicate-identical spawns keep
  stable ids; raw S1 and S2/S3K placement words round-trip; v3 quarantines; apply marks
  dirty without user-modified; and persisted/unsupported S3K state renders the warning.
- [ ] **Step 2:** Implement; tests PASS; run the full editor + persistence suites.
- [ ] **Step 3:** Commit (`feat: editor save payload v2 with spawn tables`; this plan's trailers).

---

### Task C6: Authoring smoke + docs + changelog

- [ ] **Step 1: Manual smoke (requires ROM + display):** in EHZ with `debug.flags.editor` on — place a badnik and a ring, move both, delete one, toggle a cell's collision mode, reassign a solid-tile index, Ctrl+S, exit to gameplay (badnik spawns, solidity blocks the player), re-enter editor (edits present), restart engine (edits re-applied from sidecar). Record results in the commit body.
- [ ] **Step 2:** Update `CHANGELOG.md` (one phase-0 entry covering A+B+C), `CLAUDE.md` + `AGENTS.md` (GamePatch framework + editor capabilities pointers), `docs/KNOWN_DISCREPANCIES.md` only if the smoke surfaced an accepted divergence.
- [ ] **Step 3:** Full `mvn test` + S3K must-keep-green + trace spot sweep; update `docs/TRACE_FRONTIER_LOG.md` for the sweep.
- [ ] **Step 4: Commit**

```bash
git add CHANGELOG.md README.md CLAUDE.md AGENTS.md docs/KNOWN_DISCREPANCIES.md docs/TRACE_FRONTIER_LOG.md
git commit -m "docs: phase-0 foundations changelog and agent-doc updates

Changelog: updated
Guide: n/a
Known-Discrepancies: updated
S3K-Known-Discrepancies: n/a
Agent-Docs: updated
Configuration-Docs: n/a
Skills: n/a"
```

(Drop `Known-Discrepancies: updated` to `n/a` if Step 2 didn't touch it.)

---

## Execution notes

- A1/A3/A5/A6 are thin wrappers over KiS2-plan tasks — the executor's first action in each is opening that plan. A4 REPLACES its Task 4; do not execute both.
- The KiS2 content tasks (1, 5–6, 10–12) plus Task 13/14 verification are the natural
  follow-on task sequence after Phase 0 Workstream A commits directly on `next`; they
  validate the framework with a real patch.
- Completion flow: after each task's focused gates and the phase-wide gates pass,
  commit its exact files directly on `next`; the final task includes the README
  release-log note. No merge-back step exists.
