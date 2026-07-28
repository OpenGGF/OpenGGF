# ModRomArtIntake Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A supported `@ModApi` capability letting additive Sonic 2 patch mods stage ROM-art requests (art + mapping + optional DPLC addresses) at registration time; the engine materializes them from the user's ROM at gameplay launch and serves the result under the mod's namespaced object-art key.

**Architecture:** `ModContext.registerRomObjectArt(key, RomArtRequest)` stages requests into `ModRegistrationPlan` (registration has **no ROM access** — bounds checks use a static Sonic 2 ROM-size constant). `ModBackedGamePatch.apply(...)` — the existing per-launch patch-apply choke point where a throw becomes `ResolutionResult.LaunchAborted` — materializes each request via a new `RomArtMaterializer` (decompress → S2 mappings → optional DPLC flatten → `ObjectSpriteSheet`) and merges the sheets into the existing `ObjectArtOverlayProvider` decoration, so mod objects resolve them through the normal `getRenderer("owner:key")` path. DPLC flattening reuses `AizIntroArtLoader`'s proven remap logic, extracted to a shared utility.

**Tech Stack:** Java 21, JUnit 5 (Jupiter only), Maven, existing engine loaders (`PatternDecompressor`, `S2SpriteDataLoader`, `Sonic2PlayerArt.parseDplcFrames`).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-13-example-mods-design.md` (Part 1).
- Tests are JUnit 5 / Jupiter only — no `org.junit.*` (JUnit 4) imports.
- Never `git add -A` / `git add .` — this working tree is shared by concurrent sessions; stage exact paths only.
- Every non-merge commit needs the trailer block (`Changelog`, `Guide`, `Known-Discrepancies`, `S3K-Known-Discrepancies`, `Agent-Docs`, `Configuration-Docs`, `Skills`), each `updated` or `n/a[: reason]`. `feat`/`fix`/`perf` commits touching `src/main/` must either stage `CHANGELOG.md` with `Changelog: updated` or justify: intermediate tasks use `Changelog: n/a: covered by aggregate ModRomArtIntake CHANGELOG entry in the final task of this plan`. Run `git config core.hooksPath .githooks` once before the first commit.
- PowerShell: quote Maven properties — `mvn "-Dtest=..." test`. The Bash tool is real bash (use it for grep/head; PowerShell cmdlets unavailable there).
- ROM-gated tests use `com.openggf.tests.RomTestUtils.ensureSonic2RomAvailable()` (resolves `sonic2.rom.path` property → `SONIC_2_ROM_PATH` env → config → `s2.gen` in the working dir) with `Assumptions.assumeTrue`. Do NOT invent a new `s2.rom.path` property.
- Adding `@ModApi` surface = minor version bump to **2.1.0** with signature-baseline refreeze (Task 6). New types reachable from published signatures must carry `@ModApi`.
- Mod content must load with **no disk persistence of ROM-derived bytes** — materialized sheets live in memory only.
- Branch: work on `feature/ai-example-mods`.

---

### Task 1: Extract `DplcStaticFlattener` shared utility

`AizIntroArtLoader.applyDplcRemap(...)` (private static, `src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java:1185-1280`) is the proven "flatten DPLC-driven frames onto a full source pattern array" routine: it builds a `vramSlot → sourceTile` table from each frame's `TileLoadRequest`s, rewrites contiguous pieces' `tileIndex` to absolute source indices, and splits non-contiguous pieces into 1×1 sub-pieces using VDP column-major order (`tileOffset = tx * heightTiles + ty`). It is game-agnostic (operates on `SpriteMappingFrame` / `SpriteDplcFrame` only). Extract it so `RomArtMaterializer` (Task 4) can reuse it.

**Files:**
- Create: `src/main/java/com/openggf/util/DplcStaticFlattener.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java` (delegate `applyDplcRemap` to the new utility)
- Test: `src/test/java/com/openggf/util/TestDplcStaticFlattener.java`

**Interfaces:**
- Produces: `public static List<SpriteMappingFrame> DplcStaticFlattener.applyDplcRemap(List<SpriteMappingFrame> mappings, List<SpriteDplcFrame> dplcFrames)` — consumed by Task 4.

- [ ] **Step 1: Read the source method**

Read `AizIntroArtLoader.java:1185-1280` in full, plus the types it uses (`SpriteMappingFrame`, `SpriteDplcFrame`, `TileLoadRequest` — find their packages via the file's imports). Note the exact behavior: empty/absent DPLC list returns mappings unchanged with a warning log.

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/openggf/util/TestDplcStaticFlattener.java`. Use the real constructors of `SpriteMappingFrame`/`SpriteDplcFrame`/`TileLoadRequest` (check their signatures in the files found in Step 1 — the test below shows intent; adapt constructor arity to reality):

```java
package com.openggf.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TestDplcStaticFlattener {

    @Test
    void contiguousPieceIsRemappedToAbsoluteSourceTiles() {
        // One frame: a 2x2-tile piece at vram tile 0.
        // DPLC frame 0 loads 4 contiguous source tiles starting at source tile 10.
        // Expect: single piece survives, tileIndex remapped 0 -> 10.
        var piece = /* piece: x=0, y=0, widthTiles=2, heightTiles=2, tileIndex=0, no flips, pal 0, no priority */;
        var mapping = /* SpriteMappingFrame with List.of(piece) */;
        var dplc = /* SpriteDplcFrame with List.of(new TileLoadRequest(10, 4)) */;

        List<?> out = DplcStaticFlattener.applyDplcRemap(List.of(mapping), List.of(dplc));

        // frame count preserved; piece remapped, not split (contiguous)
        assertEquals(1, out.size());
        /* assert single piece with tileIndex == 10 */
    }

    @Test
    void nonContiguousPieceIsSplitIntoSingleTileSubPieces() {
        // One 2x1-tile piece at vram tile 0; DPLC loads tile0<-src 10, tile1<-src 99 (two requests).
        // Expect the piece split into two 1x1 pieces with tileIndex 10 and 99.
    }

    @Test
    void emptyDplcListReturnsMappingsUnchanged() {
        var mapping = /* any one-piece frame */;
        List<?> out = DplcStaticFlattener.applyDplcRemap(List.of(mapping), List.of());
        assertSame(/* same content */ 1, out.size());
    }
}
```

Fill the placeholders with the real constructor calls discovered in Step 1 — the three behaviors (contiguous remap, non-contiguous 1×1 split in column-major order, empty-DPLC pass-through) are the contract.

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn "-Dtest=com.openggf.util.TestDplcStaticFlattener" test`
Expected: COMPILE FAILURE — `DplcStaticFlattener` does not exist.

- [ ] **Step 4: Create the utility by moving the code**

Create `src/main/java/com/openggf/util/DplcStaticFlattener.java`: copy the body of `AizIntroArtLoader.applyDplcRemap` verbatim (including its private helpers if any are used only by it), make it `public final class DplcStaticFlattener` with a private constructor and a single `public static List<SpriteMappingFrame> applyDplcRemap(List<SpriteMappingFrame> mappings, List<SpriteDplcFrame> dplcFrames)`. Keep the empty-DPLC warning behavior. Then replace `AizIntroArtLoader`'s private method body with a one-line delegation:

```java
private static List<SpriteMappingFrame> applyDplcRemap(
        List<SpriteMappingFrame> mappings, List<SpriteDplcFrame> dplcFrames) {
    return com.openggf.util.DplcStaticFlattener.applyDplcRemap(mappings, dplcFrames);
}
```

- [ ] **Step 5: Run the new test and the S3K guard tests**

Run: `mvn "-Dtest=com.openggf.util.TestDplcStaticFlattener" test`
Expected: PASS.
Run: `mvn "-Dtest=TestS3kAiz1SkipHeadless" test` (needs `s3k.gen`; if absent it self-skips — note that in the commit message)
Expected: PASS/SKIP with no new failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/util/DplcStaticFlattener.java src/main/java/com/openggf/game/sonic3k/objects/AizIntroArtLoader.java src/test/java/com/openggf/util/TestDplcStaticFlattener.java
git commit -m "refactor: extract DplcStaticFlattener from AizIntroArtLoader" # + trailer block, all n/a (no behavior change)
```

---

### Task 2: `RomArtRequest` + `RomArtCompression` value types

**Files:**
- Create: `src/main/java/com/openggf/mods/code/RomArtCompression.java`
- Create: `src/main/java/com/openggf/mods/code/RomArtRequest.java`
- Test: `src/test/java/com/openggf/mods/code/TestRomArtRequest.java`

**Interfaces:**
- Produces (consumed by Tasks 3-5):
  - `enum RomArtCompression { NEMESIS, KOSINSKI, UNCOMPRESSED }`
  - `record RomArtRequest(int artAddress, RomArtCompression compression, int uncompressedByteSize, int mappingAddress, int dplcAddress, int paletteLine, int bankSize)` with `boolean hasDplc()`.
  - Semantics: `dplcAddress == 0` means "no DPLC"; `uncompressedByteSize` is required (>0, multiple of 32) iff compression is `UNCOMPRESSED`, must be 0 otherwise; `paletteLine` is a palette **line** 0-3 (not colors); `bankSize` ≥ 1 (use 1 for static sheets — `AizIntroArtLoader` precedent).

- [ ] **Step 1: Write the failing test**

```java
package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TestRomArtRequest {

    private static RomArtRequest valid() {
        return new RomArtRequest(0x50000, RomArtCompression.UNCOMPRESSED, 0x2960,
                0x60000, 0x70000, 0, 1);
    }

    @Test
    void validRequestConstructs() {
        RomArtRequest r = valid();
        assertTrue(r.hasDplc());
        assertEquals(0, r.paletteLine());
    }

    @Test
    void zeroDplcAddressMeansNoDplc() {
        RomArtRequest r = new RomArtRequest(0x50000, RomArtCompression.NEMESIS, 0,
                0x60000, 0, 1, 1);
        assertFalse(r.hasDplc());
    }

    @Test
    void negativeAddressesRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(-1,
                RomArtCompression.NEMESIS, 0, 0x60000, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.NEMESIS, 0, -1, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.NEMESIS, 0, 0x60000, -1, 0, 1));
    }

    @Test
    void uncompressedRequiresPositiveMultipleOf32Size() {
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.UNCOMPRESSED, 0, 0x60000, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.UNCOMPRESSED, 33, 0x60000, 0, 0, 1));
    }

    @Test
    void compressedForbidsExplicitSize() {
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.NEMESIS, 32, 0x60000, 0, 0, 1));
    }

    @Test
    void paletteLineMustBeZeroToThree() {
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.NEMESIS, 0, 0x60000, 0, 4, 1));
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.NEMESIS, 0, 0x60000, 0, -1, 1));
    }

    @Test
    void bankSizeMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new RomArtRequest(0x50000,
                RomArtCompression.NEMESIS, 0, 0x60000, 0, 0, 0));
    }

    @Test
    void nullCompressionRejected() {
        assertThrows(NullPointerException.class, () -> new RomArtRequest(0x50000,
                null, 0, 0x60000, 0, 0, 1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn "-Dtest=com.openggf.mods.code.TestRomArtRequest" test`
Expected: COMPILE FAILURE.

- [ ] **Step 3: Implement the types**

`RomArtCompression.java`:

```java
package com.openggf.mods.code;

import com.openggf.game.ModApi;

/** Compression of a ROM-resident art block referenced by a {@link RomArtRequest}. */
@ModApi
public enum RomArtCompression {
    NEMESIS,
    KOSINSKI,
    UNCOMPRESSED
}
```

`RomArtRequest.java`:

```java
package com.openggf.mods.code;

import com.openggf.game.ModApi;
import java.util.Objects;

/**
 * Staged request to materialize object art from the user's Sonic 2 ROM at gameplay launch.
 * Registration happens with no ROM open; addresses are validated against static bounds at
 * registration and against the real ROM during materialization. {@code paletteLine} is a
 * palette line index (0-3) into the active zone palette, not color data. {@code dplcAddress}
 * of 0 means the mapping frames reference art tiles directly (no DPLC flattening).
 * ROM-derived bytes are never persisted to disk.
 */
@ModApi
public record RomArtRequest(
        int artAddress,
        RomArtCompression compression,
        int uncompressedByteSize,
        int mappingAddress,
        int dplcAddress,
        int paletteLine,
        int bankSize) {

    public RomArtRequest {
        Objects.requireNonNull(compression, "compression");
        if (artAddress < 0 || mappingAddress < 0 || dplcAddress < 0) {
            throw new IllegalArgumentException("ROM addresses must be non-negative");
        }
        if (compression == RomArtCompression.UNCOMPRESSED) {
            if (uncompressedByteSize <= 0 || uncompressedByteSize % 32 != 0) {
                throw new IllegalArgumentException(
                        "uncompressedByteSize must be a positive multiple of 32 for UNCOMPRESSED art");
            }
        } else if (uncompressedByteSize != 0) {
            throw new IllegalArgumentException(
                    "uncompressedByteSize is only valid for UNCOMPRESSED art");
        }
        if (paletteLine < 0 || paletteLine > 3) {
            throw new IllegalArgumentException("paletteLine must be 0-3");
        }
        if (bankSize < 1) {
            throw new IllegalArgumentException("bankSize must be >= 1");
        }
    }

    public boolean hasDplc() {
        return dplcAddress != 0;
    }
}
```

(`@ModApi` lives at `com.openggf.game.ModApi`; it is type-target-only, so annotating the types is sufficient.)

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn "-Dtest=com.openggf.mods.code.TestRomArtRequest" test`
Expected: PASS. (`TestModApiSignatureSurface` will now fail until Task 6 refreezes the baseline — that is expected; do not run the full suite between here and Task 6.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/mods/code/RomArtCompression.java src/main/java/com/openggf/mods/code/RomArtRequest.java src/test/java/com/openggf/mods/code/TestRomArtRequest.java
git commit -m "feat: RomArtRequest/RomArtCompression mod API value types" # trailers: Changelog: n/a: covered by aggregate ModRomArtIntake CHANGELOG entry in the final task of this plan; others n/a
```

---

### Task 3: `ModContext.registerRomObjectArt` + `ModRegistrationPlan` plumbing

**Files:**
- Modify: `src/main/java/com/openggf/mods/code/ModContext.java` (staging map ~L29, new method after `registerObjectArt` ~L130, `freeze()` ~L178)
- Modify: `src/main/java/com/openggf/mods/code/ModRegistrationPlan.java` (new record component + compatibility constructor; update `prepareObjectArt`/`prepareZones` copy sites)
- Test: `src/test/java/com/openggf/mods/code/TestModContextRomArt.java`

**Interfaces:**
- Consumes: `RomArtRequest`, `RomArtCompression` (Task 2).
- Produces (consumed by Tasks 4-5):
  - `public void ModContext.registerRomObjectArt(String key, RomArtRequest request)` — gates: rejects standalone contexts, rejects `baseGame != "s2"`, rejects addresses ≥ `0x100000` (static Sonic 2 World REV01 ROM size — registration has no ROM open), rejects keys colliding with either art map. Namespaces via `ModKeySyntax.requireOwnedKey(owner, key)`.
  - `Map<String, RomArtRequest> ModRegistrationPlan.romObjectArt()` — new record component (empty map when unused). The **previous canonical constructor is kept as an explicit delegating overload** (passing `Map.of()`) so the published 2.0 signature survives — additive change only.

- [ ] **Step 1: Read the two files**

Read `ModContext.java` and `ModRegistrationPlan.java` in full. Note: the exact structure of `registerObjectArt` (its `mutate`/`requireOpen`/`failure` wrapping), the constructor arity of `ModRegistrationPlan`, every `new ModRegistrationPlan(...)` call site (`freeze()`, `prepareObjectArt`, `prepareZones`), and the `standalone` field/guard used by `registerGamePatch` (~L90) and the `"s2"` gate in `registerZone` (~L150).

- [ ] **Step 2: Write the failing test**

Model setup on `TestModContextAndFaultBoundary` (`src/test/java/com/openggf/mods/code/TestModContextAndFaultBoundary.java:30-76`): construct `ModContext` directly with `ModAssetRoot.forTests(owner)`.

```java
package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.mods.ModAssetRoot;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TestModContextRomArt {

    private static RomArtRequest request() {
        return new RomArtRequest(0x50000, RomArtCompression.NEMESIS, 0, 0x60000, 0, 0, 1);
    }

    @Test
    void stagedRequestSurvivesFreezeUnderNamespacedKey() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        context.registerRomObjectArt("bird", request());
        ModRegistrationPlan plan = context.freeze();
        assertEquals(Set.of("owner:bird"), plan.romObjectArt().keySet());
        assertEquals(request(), plan.romObjectArt().get("owner:bird"));
    }

    @Test
    void standaloneContextRejectsRomArt() {
        ModContext context = new ModContext("owner", null, ModAssetRoot.forTests("owner"), null, true);
        assertThrows(ModRegistrationException.class,
                () -> context.registerRomObjectArt("bird", request()));
    }

    @Test
    void nonSonic2BaseGameRejected() {
        ModContext context = new ModContext("owner", "s1", ModAssetRoot.forTests("owner"));
        assertThrows(ModRegistrationException.class,
                () -> context.registerRomObjectArt("bird", request()));
    }

    @Test
    void addressBeyondStaticSonic2RomBoundRejected() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        RomArtRequest outOfBounds = new RomArtRequest(0x100000, RomArtCompression.NEMESIS,
                0, 0x60000, 0, 0, 1);
        assertThrows(ModRegistrationException.class,
                () -> context.registerRomObjectArt("bird", outOfBounds));
    }

    @Test
    void duplicateKeyAcrossBakedAndRomArtRejected() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        context.registerObjectArt("bird", new BakedSheetRef("art/bird.ggfs"));
        assertThrows(ModRegistrationException.class,
                () -> context.registerRomObjectArt("bird", request()));
    }

    @Test
    void planWithoutRomArtHasEmptyMap() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        ModRegistrationPlan plan = context.freeze();
        assertTrue(plan.romObjectArt().isEmpty());
    }
}
```

Adapt constructor arities and the `ModRegistrationException` type/import to what Step 1 found (the 3-arg and 5-arg `ModContext` constructors and `BakedSheetRef`'s package are as used in `TestModContextAndFaultBoundary`). If `registerRomObjectArt` failures surface as a different exception type via the poison model, assert that type instead — match `registerZone`'s existing test expectations.

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModContextRomArt" test`
Expected: COMPILE FAILURE (`registerRomObjectArt`, `romObjectArt()` missing).

- [ ] **Step 4: Implement**

In `ModContext.java`:

```java
/** Static Sonic 2 World REV01 ROM length; registration-time bound (no ROM open here). */
private static final int SONIC2_ROM_LENGTH = 0x100000;

private final Map<String, RomArtRequest> romArt = new LinkedHashMap<>();
```

New method, mirroring `registerObjectArt`'s exact wrapping (`mutate`/`requireOpen`/`failure` — copy the surrounding structure, not this sketch's guesswork):

```java
/**
 * Stages object art to be materialized from the user's Sonic 2 ROM at gameplay launch.
 * Only available to Sonic 2 patch mods; the art is served under the owner-namespaced key
 * through the same overlay as {@link #registerObjectArt}.
 */
public void registerRomObjectArt(String key, RomArtRequest request) {
    // inside the same mutate/requireOpen structure as registerObjectArt:
    if (standalone) {
        throw failure("ROM art intake is not available to standalone modules");
    }
    if (!"s2".equals(baseGame)) {
        throw failure("ROM art intake is supported only for Sonic 2 patch mods");
    }
    Objects.requireNonNull(request, "request");
    if (request.artAddress() >= SONIC2_ROM_LENGTH
            || request.mappingAddress() >= SONIC2_ROM_LENGTH
            || request.dplcAddress() >= SONIC2_ROM_LENGTH) {
        throw failure("ROM art request address beyond Sonic 2 ROM bounds: " + key);
    }
    String owned = ModKeySyntax.requireOwnedKey(owner, key);
    if (art.containsKey(owned) || romArt.putIfAbsent(owned, request) != null) {
        throw failure("Duplicate object art key: " + owned);
    }
}
```

Also add the reverse collision check to `registerObjectArt` (a baked registration must reject a key already staged in `romArt`).

In `freeze()`: pass `romArt` into the plan.

In `ModRegistrationPlan.java`: append `Map<String, RomArtRequest> romObjectArt` as the **last** record component; add an explicit constructor with the previous parameter list delegating with `Map.of()` (preserves the published 2.0 constructor signature); thread the component through the `prepareObjectArt`/`prepareZones` copy-constructor sites so it survives both. If the record has a compact-constructor invariant section, add `romObjectArt = Map.copyOf(romObjectArt);` defensiveness consistent with how the other maps are handled.

- [ ] **Step 5: Run tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModContextRomArt" test` — Expected: PASS.
Run: `mvn "-Dtest=com.openggf.mods.code.TestModContextAndFaultBoundary" test` — Expected: PASS (no regression).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/mods/code/ModContext.java src/main/java/com/openggf/mods/code/ModRegistrationPlan.java src/test/java/com/openggf/mods/code/TestModContextRomArt.java
git commit -m "feat: stage ROM object-art requests through ModContext" # trailers: Changelog n/a: covered by aggregate ModRomArtIntake CHANGELOG entry in the final task of this plan
```

---

### Task 4: `RomArtMaterializer`

**Files:**
- Create: `src/main/java/com/openggf/mods/code/RomArtMaterializer.java` (engine-internal — NOT `@ModApi`, must not appear in any published signature)
- Test: `src/test/java/com/openggf/mods/code/TestRomArtMaterializer.java` (ROM-gated)

**Interfaces:**
- Consumes: `RomArtRequest` (Task 2), `DplcStaticFlattener.applyDplcRemap` (Task 1), `PatternDecompressor.nemesis(Rom,int)` / `.kosinski(Rom,int)` / `.uncompressed(RomByteReader,int,int)` (`src/main/java/com/openggf/util/PatternDecompressor.java:73,127,158`), `S2SpriteDataLoader.loadMappingFrames(RomByteReader,int)` (`:38`), `Sonic2PlayerArt.parseDplcFrames(RomByteReader,int)` (public static, `Sonic2PlayerArt.java:192`), `ModInputLimits` accessors (`maxSheetPatterns()`, `maxSheetFrames()`, `maxSheetPieces()`).
- Produces (consumed by Task 5): `static Map<String, ObjectSpriteSheet> RomArtMaterializer.materialize(String owner, Map<String, RomArtRequest> requests, Rom rom, ModInputLimits limits)` — throws `ModRegistrationException` with code `MOD_ROM_ART_INVALID` naming owner, key, and address on any failure. (Confirm `ModRegistrationException`'s `(owner, code, message, location, cause)` constructor shape from its source and use it; it is the established structured failure for mod materialization — see `ModRegistrationPlan.prepareObjectArt`'s `MOD_ART_ASSET_INVALID` usage.)

- [ ] **Step 1: Write the failing ROM-gated test**

```java
package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.io.ModInputLimits;
import com.openggf.tests.RomTestUtils;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TestRomArtMaterializer {

    private static com.openggf.data.Rom rom;

    @BeforeAll
    static void loadRom() throws Exception {
        // Use RomTestUtils' existing S2 resolution (sonic2.rom.path -> env -> config -> s2.gen).
        // Read RomTestUtils to pick the exact helper: prefer ensureSonic2RomAvailable() /
        // the find+assumeTrue idiom used by ~40 existing ROM-gated tests.
        rom = RomTestUtils.openSonic2RomOrNull();  // adapt to the real helper name
        assumeTrue(rom != null, "s2 ROM not available");
    }

    @Test
    void tailsUncompressedArtWithDplcMaterializes() {
        RomArtRequest tails = new RomArtRequest(
                Sonic2Constants.ART_UNC_TAILS_ADDR, RomArtCompression.UNCOMPRESSED,
                Sonic2Constants.ART_UNC_TAILS_SIZE,
                Sonic2Constants.MAP_UNC_TAILS_ADDR,
                Sonic2Constants.MAP_R_UNC_TAILS_ADDR,
                0, 1);
        Map<String, com.openggf.game.ObjectSpriteSheet> out = RomArtMaterializer.materialize(
                "owner", Map.of("owner:tails", tails), rom, ModInputLimits.production());
        var sheet = out.get("owner:tails");
        assertNotNull(sheet);
        // Tails has dozens of mapping frames; DPLC flattening must preserve the frame count.
        assertTrue(sheet.getMappingFrames().size() > 10);
        assertTrue(sheet.getPatterns().length > 0);
    }

    @Test
    void nemesisCompressedArtMaterializes() {
        // Pick any Nemesis-compressed object art constant from Sonic2Constants
        // (grep: rg "NEM" src/main/java/com/openggf/game/sonic2/constants/Sonic2Constants.java | head)
        // and its mapping constant; assert non-empty patterns and >=1 mapping frame.
    }

    @Test
    void garbageAddressFailsWithStructuredError() {
        // An address inside the ROM but pointing at non-Nemesis data: expect
        // ModRegistrationException with code MOD_ROM_ART_INVALID mentioning the key.
        RomArtRequest garbage = new RomArtRequest(0x000100, RomArtCompression.NEMESIS,
                0, 0x000200, 0, 0, 1);
        ModRegistrationException ex = assertThrows(ModRegistrationException.class,
                () -> RomArtMaterializer.materialize("owner",
                        Map.of("owner:bad", garbage), rom, ModInputLimits.production()));
        assertTrue(ex.getMessage().contains("owner:bad"));
    }

    @Test
    void patternCapEnforced() {
        // Lower the sheet-pattern cap below Tails' tile count via the lowering builder,
        // expect MOD_ROM_ART_INVALID.
        ModInputLimits tight = ModInputLimits.loweringBuilder().maxSheetPatterns(8).build();
        RomArtRequest tails = new RomArtRequest(
                Sonic2Constants.ART_UNC_TAILS_ADDR, RomArtCompression.UNCOMPRESSED,
                Sonic2Constants.ART_UNC_TAILS_SIZE,
                Sonic2Constants.MAP_UNC_TAILS_ADDR, 0, 0, 1);
        assertThrows(ModRegistrationException.class,
                () -> RomArtMaterializer.materialize("owner",
                        Map.of("owner:tails", tails), rom, tight));
    }
}
```

Before finalizing: read `RomTestUtils.java` (`src/test/java/com/openggf/tests/RomTestUtils.java`) and use its actual S2 helper; read `ModInputLimits.loweringBuilder()` for the real builder method names; read `ObjectSpriteSheet` for its actual accessor names (`getMappingFrames()`/`getPatterns()` are the expected shape — confirm). For the Nemesis case, substitute a real `ART_NEM_*`/mapping constant pair found by the grep in the comment (Sonic2Constants has many; any badnik pair works).

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn "-Dtest=com.openggf.mods.code.TestRomArtMaterializer" test`
Expected: COMPILE FAILURE (`RomArtMaterializer` missing). (If no s2 ROM is present locally the tests will skip once compiled — the compile failure still proves the red state.)

- [ ] **Step 3: Implement**

```java
package com.openggf.mods.code;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.ObjectSpriteSheet;
import com.openggf.game.sonic2.S2SpriteDataLoader;
import com.openggf.game.sonic2.Sonic2PlayerArt;
import com.openggf.io.ModInputLimits;
import com.openggf.util.DplcStaticFlattener;
import com.openggf.util.PatternDecompressor;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Materializes staged {@link RomArtRequest}s from the user's Sonic 2 ROM at gameplay launch.
 * ROM-derived data stays in memory only. Failures are owner-attributed
 * {@code MOD_ROM_ART_INVALID} registration exceptions; thrown during patch apply they abort
 * the launch through ModuleResolutionService's existing creator-apply fault path.
 */
final class RomArtMaterializer {

    private RomArtMaterializer() {}

    static Map<String, ObjectSpriteSheet> materialize(String owner,
            Map<String, RomArtRequest> requests, Rom rom, ModInputLimits limits) {
        Map<String, ObjectSpriteSheet> out = new LinkedHashMap<>();
        for (Map.Entry<String, RomArtRequest> entry : requests.entrySet()) {
            out.put(entry.getKey(), materializeOne(owner, entry.getKey(), entry.getValue(), rom, limits));
        }
        return out;
    }

    private static ObjectSpriteSheet materializeOne(String owner, String key,
            RomArtRequest request, Rom rom, ModInputLimits limits) {
        try {
            RomByteReader reader = RomByteReader.fromRom(rom);
            var patterns = switch (request.compression()) {
                case NEMESIS -> PatternDecompressor.nemesis(rom, request.artAddress());
                case KOSINSKI -> PatternDecompressor.kosinski(rom, request.artAddress());
                case UNCOMPRESSED -> PatternDecompressor.uncompressed(
                        reader, request.artAddress(), request.uncompressedByteSize());
            };
            if (patterns.length == 0) {
                throw invalid(owner, key, request, "art decompressed to zero patterns", null);
            }
            if (patterns.length > limits.maxSheetPatterns()) {
                throw invalid(owner, key, request,
                        "pattern count " + patterns.length + " exceeds limit "
                                + limits.maxSheetPatterns(), null);
            }
            var mappings = S2SpriteDataLoader.loadMappingFrames(reader, request.mappingAddress());
            if (request.hasDplc()) {
                var dplc = Sonic2PlayerArt.parseDplcFrames(reader, request.dplcAddress());
                mappings = DplcStaticFlattener.applyDplcRemap(mappings, dplc);
            }
            if (mappings.size() > limits.maxSheetFrames()) {
                throw invalid(owner, key, request,
                        "frame count " + mappings.size() + " exceeds limit "
                                + limits.maxSheetFrames(), null);
            }
            int pieces = mappings.stream().mapToInt(f -> f.pieces().size()).sum();
            if (pieces > limits.maxSheetPieces()) {
                throw invalid(owner, key, request,
                        "piece count " + pieces + " exceeds limit " + limits.maxSheetPieces(), null);
            }
            return new ObjectSpriteSheet(patterns, mappings, request.paletteLine(), request.bankSize());
        } catch (ModRegistrationException e) {
            throw e;
        } catch (Exception e) {
            throw invalid(owner, key, request, "materialization failed: " + e.getMessage(), e);
        }
    }

    private static ModRegistrationException invalid(String owner, String key,
            RomArtRequest request, String detail, Throwable cause) {
        return new ModRegistrationException(owner, "MOD_ROM_ART_INVALID",
                "ROM art '" + key + "' at 0x" + Integer.toHexString(request.artAddress())
                        + ": " + detail, key, cause);
    }
}
```

Adapt to reality found while reading: the exact `ModRegistrationException` constructor, `ObjectSpriteSheet`'s constructor/packages, whether `SpriteMappingFrame` exposes `pieces()` (adjust the piece-count sum accordingly), and checked-exception handling on the decompressor calls (`nemesis`/`kosinski`/`uncompressed` throw `IOException`). Access ROM reads the same way stock loaders do (`PatternDecompressor` already synchronizes internally where needed — verify `Sonic2ObjectArt`'s `synchronized(rom)` idiom and mirror it if the decompressor doesn't).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn "-Dtest=com.openggf.mods.code.TestRomArtMaterializer" test`
Expected: PASS (4 tests; requires `s2.gen` in the working dir or `-Dsonic2.rom.path`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/mods/code/RomArtMaterializer.java src/test/java/com/openggf/mods/code/TestRomArtMaterializer.java
git commit -m "feat: materialize mod ROM-art requests from the Sonic 2 ROM" # trailers: Changelog n/a: covered by aggregate ModRomArtIntake CHANGELOG entry in the final task of this plan
```

---

### Task 5: Wire materialization into `ModBackedGamePatch.apply`

**Files:**
- Modify: `src/main/java/com/openggf/mods/code/ModBackedGamePatch.java` (apply/`getObjectArtProvider` decoration, ~L69-193 and L108-116)
- Modify: `src/main/java/com/openggf/mods/code/ModArtOverlayProvider.java` (overload accepting extra pre-built sheets)
- Modify: `src/main/java/com/openggf/mods/code/ModRuntime.java` (~L141, construct the patch with the production materializer source)
- Test: `src/test/java/com/openggf/mods/code/TestModBackedGamePatchRomArt.java`

**Interfaces:**
- Consumes: `ModRegistrationPlan.romObjectArt()` (Task 3), `RomArtMaterializer.materialize` (Task 4), `ModArtOverlayProvider.decorate(base, prepared)` (existing, `ModArtOverlayProvider.java:15-28`), `ObjectArtOverlayProvider` (existing).
- Produces:
  - `interface ModBackedGamePatch.RomArtSheetSource { Map<String, ObjectSpriteSheet> materialize(String owner, Map<String, RomArtRequest> requests); }` (nested, package-private is fine — it must NOT enter the `@ModApi` surface, so keep the new constructor parameter off any `@ModApi`-annotated signature path; verify in Task 6).
  - Existing 3-arg `ModBackedGamePatch` constructor preserved, delegating with the production source.
  - Behavior: when `plan.romObjectArt()` is non-empty, `apply(...)` materializes eagerly (throw → `ModuleResolutionService.resolve` catches → `ResolutionResult.LaunchAborted` — the spec's launch-abort contract) and the returned module's `getObjectArtProvider()` serves baked + ROM sheets through one `ObjectArtOverlayProvider`, resolvable via `getRenderer("owner:key")`.

- [ ] **Step 1: Read the three files**

Read `ModBackedGamePatch.java`, `ModArtOverlayProvider.java`, `ModRuntime.java` (construction site ~L141), and `ObjectArtOverlayProvider.java`. Note how `DelegatingGameModule.getObjectArtProvider()` is overridden today and where in `apply` the provider is built.

- [ ] **Step 2: Write the failing test**

No ROM needed — inject a fake `RomArtSheetSource`. Model plan construction on `TestModContextAndFaultBoundary`'s context usage:

```java
package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.mods.ModAssetRoot;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestModBackedGamePatchRomArt {

    private static ModRegistrationPlan planWithRomArt() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        context.registerRomObjectArt("bird",
                new RomArtRequest(0x50000, RomArtCompression.NEMESIS, 0, 0x60000, 0, 0, 1));
        return context.freeze();
    }

    @Test
    void applyMaterializesRomArtAndServesItThroughTheOverlay() {
        ModRegistrationPlan plan = planWithRomArt();
        var fakeSheet = /* minimal ObjectSpriteSheet: 1 blank Pattern, 1 one-piece frame, line 0, bank 1 */;
        ModBackedGamePatch patch = new ModBackedGamePatch(plan, faultBoundary(), findingSink(),
                (owner, requests) -> {
                    assertEquals(Map.of("owner:bird", plan.romObjectArt().get("owner:bird")), requests);
                    return Map.of("owner:bird", fakeSheet);
                });
        var module = patch.apply(baseModule(), patchContext());
        var provider = module.getObjectArtProvider();
        assertNotNull(provider.getRenderer("owner:bird"));
    }

    @Test
    void materializationFailurePropagatesOutOfApply() {
        ModRegistrationPlan plan = planWithRomArt();
        ModBackedGamePatch patch = new ModBackedGamePatch(plan, faultBoundary(), findingSink(),
                (owner, requests) -> {
                    throw new ModRegistrationException("owner", "MOD_ROM_ART_INVALID", "boom", "owner:bird", null);
                });
        assertThrows(ModRegistrationException.class,
                () -> patch.apply(baseModule(), patchContext()));
    }

    @Test
    void planWithoutRomArtNeverInvokesTheSource() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        ModRegistrationPlan plan = context.freeze();
        ModBackedGamePatch patch = new ModBackedGamePatch(plan, faultBoundary(), findingSink(),
                (owner, requests) -> { throw new AssertionError("must not materialize"); });
        assertNotNull(patch.apply(baseModule(), patchContext()));
    }

    // baseModule()/patchContext()/faultBoundary()/findingSink() helpers: reuse whatever fakes the
    // existing ModBackedGamePatch tests use — find them with:
    //   rg "new ModBackedGamePatch" src/test/java --files-with-matches
    // and copy that test's fixture approach.
}
```

The fixture helpers are the real work of this step: locate the existing `ModBackedGamePatch` test(s) and reuse their base-module/patch-context/fault-boundary fakes verbatim. For the fake `ObjectSpriteSheet`, construct the minimal real one (a single blank `Pattern` and a one-piece `SpriteMappingFrame` — same constructors as in Task 1's test).

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModBackedGamePatchRomArt" test`
Expected: COMPILE FAILURE (no 4-arg constructor / `RomArtSheetSource`).

- [ ] **Step 4: Implement**

In `ModBackedGamePatch.java`:

```java
/** Source of materialized ROM-art sheets; injectable for tests. Engine-internal. */
interface RomArtSheetSource {
    Map<String, ObjectSpriteSheet> materialize(String owner, Map<String, RomArtRequest> requests);
}

static RomArtSheetSource productionRomArtSource() {
    return (owner, requests) -> {
        try {
            return RomArtMaterializer.materialize(owner, requests,
                    GameServices.rom().getRom(), ModInputLimits.production());
        } catch (IOException e) {
            throw new ModRegistrationException(owner, "MOD_ROM_ART_INVALID",
                    "ROM unavailable during art materialization", null, e);
        }
    };
}
```

Add the 4-arg constructor storing the source; keep the existing 3-arg constructor delegating with `productionRomArtSource()`. In `apply(...)`, before building the provider override:

```java
Map<String, ObjectSpriteSheet> romSheets = plan.romObjectArt().isEmpty()
        ? Map.of()
        : romArtSource.materialize(plan.owner(), plan.romObjectArt());
```

Then extend the provider decoration (currently `plan.preparedObjectArt().isEmpty() ? inherited : ModArtOverlayProvider.decorate(inherited, plan.preparedObjectArt())`) to:

```java
objectArtProvider = (plan.preparedObjectArt().isEmpty() && romSheets.isEmpty())
        ? inherited
        : ModArtOverlayProvider.decorate(inherited, plan.preparedObjectArt(), romSheets);
```

In `ModArtOverlayProvider.java`, add the overload (keep the existing 2-arg `decorate` delegating with `Map.of()`):

```java
static ObjectArtProvider decorate(ObjectArtProvider base,
        Map<String, BakedSheetReader.BakedSheet> prepared,
        Map<String, ObjectSpriteSheet> extraSheets) {
    Map<String, ObjectSpriteSheet> converted = new LinkedHashMap<>();
    prepared.forEach((key, baked) -> converted.put(key, baked.toObjectSpriteSheet()));
    converted.putAll(extraSheets);
    return new ObjectArtOverlayProvider(base, converted);
}
```

(Match the existing method's exact conversion code — copy it, then add `putAll`.) `ModRuntime.java` needs no change if the 3-arg constructor now supplies the production source; verify the L141 construction site compiles unchanged.

- [ ] **Step 5: Run tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestModBackedGamePatchRomArt" test` — Expected: PASS.
Run: `mvn "-Dtest=com.openggf.mods.code.TestModArtOverrides" test` and the existing ModBackedGamePatch test class found in Step 2 — Expected: PASS (no regression).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/openggf/mods/code/ModBackedGamePatch.java src/main/java/com/openggf/mods/code/ModArtOverlayProvider.java src/test/java/com/openggf/mods/code/TestModBackedGamePatchRomArt.java
git commit -m "feat: serve materialized ROM art through the mod art overlay" # trailers: Changelog n/a: covered by aggregate ModRomArtIntake CHANGELOG entry in the final task of this plan
```

(Stage `ModRuntime.java` too if it needed edits.)

---

### Task 6: `@ModApi` surface refreeze → 2.1.0

The new published surface: `ModContext.registerRomObjectArt(String, RomArtRequest)`, `ModRegistrationPlan.romObjectArt()` (+ new canonical constructor alongside the preserved old one), `RomArtRequest`, `RomArtCompression`. Per `docs/architecture/mod-api-compatibility.md` (L47-59, L76-79): additions = same-major MINOR bump.

**Files:**
- Modify: `src/main/java/com/openggf/mods/ModApiVersion.java:33` (`CURRENT` → `new SemanticVersion(2, 1, 0)`)
- Create: `src/test/resources/mods/mod-api-signatures-2.1.txt` (regenerated baseline)
- Modify: `src/test/java/com/openggf/mods/TestModApiSignatureSurface.java` (point the pin test at the 2.1 baseline + version, following the procedure documented in the test/compatibility doc)
- Modify: `docs/architecture/mod-api-compatibility.md` (record the 2.0 → 2.1 lineage: what was added and why)

**Interfaces:**
- Produces: published Mod API 2.1.0. Existing mods declaring `engineApiRange: ">=2.0.0 <3.0.0"` remain compatible — verify no sample manifest needs edits.

- [ ] **Step 1: Run the surface guard to see the drift**

Run: `mvn "-Dtest=com.openggf.mods.TestModApiSignatureSurface" test`
Expected: FAIL — new signatures not in the 2.0 baseline. Inspect every added line: it must be exactly the Task 2/3 additions (`RomArtRequest`, `RomArtCompression`, `registerRomObjectArt`, `romObjectArt`, the new plan constructor). If `RomArtMaterializer`, `RomArtSheetSource`, `ObjectSpriteSheet`, or any other unintended type appears, fix visibility (package-private) rather than annotating it.

- [ ] **Step 2: Annotate and refreeze**

Follow the refreeze procedure documented in `TestModApiSignatureSurface` / `mod-api-compatibility.md`: confirm every newly reachable type carries `@ModApi` (Task 2 already annotated the two new types; the guard's recursive-reachability test will name any others), then regenerate the sorted-LF snapshot into `src/test/resources/mods/mod-api-signatures-2.1.txt`, update the pin test to assert against the 2.1 file and `ModApiVersion.CURRENT == 2.1.0`, and bump `ModApiVersion.java`. Keep `mod-api-signatures-2.0.txt` untouched (historical baselines are retained — 1.1 and 1.2 files still exist).

- [ ] **Step 3: Run the full mod API guard set**

Run: `mvn "-Dtest=com.openggf.mods.TestModApiSignatureSurface" test` — Expected: PASS.
Run: `mvn "-Dtest=TestModApiJavadocTool" test` and `mvn "-Dtest=TestModApiSdkPackager" test` — Expected: PASS (Javadoc/SDK packaging pick up the new types; fix any missing-Javadoc findings by writing real Javadoc, which Tasks 2-3 code already carries).

- [ ] **Step 4: Update the compatibility doc**

In `docs/architecture/mod-api-compatibility.md`, add a 2.1.0 lineage entry: additive ROM-art intake for Sonic 2 patch mods (`registerRomObjectArt`, `RomArtRequest`, `RomArtCompression`, `ModRegistrationPlan.romObjectArt`), old plan constructor preserved, no breaking changes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/openggf/mods/ModApiVersion.java src/test/resources/mods/mod-api-signatures-2.1.txt src/test/java/com/openggf/mods/TestModApiSignatureSurface.java docs/architecture/mod-api-compatibility.md
git commit -m "feat: publish Mod API 2.1.0 with ROM-art intake surface" # trailers: Changelog n/a: covered by aggregate ModRomArtIntake CHANGELOG entry in the final task of this plan
```

(Also stage any files that gained `@ModApi` annotations in Step 2.)

---

### Task 7: Creator docs, CHANGELOG, spec sync, full verification

**Files:**
- Modify: `docs/modding/content-mods.md` (new "ROM art intake" section)
- Modify: `CHANGELOG.md` (aggregate entry)
- Modify: `docs/superpowers/specs/2026-07-13-example-mods-design.md` (two reality corrections)
- Modify: `docs/modding/ggfmod.md` only if it documents the API surface list (check; likely no change)

**Interfaces:** none — documentation and verification.

- [ ] **Step 1: Write the creator-facing doc section**

Add a "ROM art intake (Sonic 2 patch mods)" section to `docs/modding/content-mods.md` near the object-art material. Content requirements: what it is (materialize art from the *player's* ROM at launch — the mod jar ships no ROM bytes), the exact API (`context.registerRomObjectArt("bird", new RomArtRequest(...))` with a worked Tails-flying-frames example using the `Sonic2Constants` addresses and noting RomOffsetFinder for finding others), the gates (S2 patches only, standalone rejected at registration, static ROM-bounds check), palette semantics (line 0-3 of the active zone palette — colors come from the mod zone's own `palettes.bin`), DPLC support (S2 player-format DPLC tables, flattened to a static sheet), limits (`ModInputLimits` sheet caps), and the fault contract (bad address/decompression failure aborts launch with an owner-attributed `MOD_ROM_ART_INVALID` diagnostic). Match the file's existing tone and heading depth.

- [ ] **Step 2: CHANGELOG entry**

Add under the unreleased section, matching existing entry style:

```
- Mod API 2.1.0: ROM art intake for Sonic 2 patch mods — `ModContext.registerRomObjectArt`
  materializes object art (Nemesis/Kosinski/uncompressed + S2 mappings + optional DPLC)
  from the player's ROM at launch under the mod's namespaced art key.
```

- [ ] **Step 3: Sync the spec with two reality corrections**

In `docs/superpowers/specs/2026-07-13-example-mods-design.md`:
1. Testing & CI: replace the "**new** `-Ds2.rom.path` property" sentence — S2 ROM gating already exists via `RomTestUtils` (`sonic2.rom.path` / `SONIC_2_ROM_PATH` / `s2.gen` in the working dir); no new property.
2. Part 1 input list: palette source is an explicit palette **line (0-3)** only (S2 object sheets reference a palette line; colors come from the mod zone's own palettes) — drop "ROM palette address".

- [ ] **Step 4: Full verification**

Run: `mvn test`
Expected: no new failures vs the branch baseline (pre-existing failures documented in memory are acceptable; anything touching `mods`, `sonic3k/objects`, or the API surface must be green). Record the result honestly in the commit body if any pre-existing red exists.

- [ ] **Step 5: Commit**

```bash
git add docs/modding/content-mods.md CHANGELOG.md docs/superpowers/specs/2026-07-13-example-mods-design.md
git commit -m "docs: document mod ROM-art intake; changelog for Mod API 2.1.0" # trailers: Changelog: updated; others n/a
```

---

## Plan-level notes for the executor

- **Read before writing:** Tasks 3-5 modify files whose exact internal structure (constructor arities, `mutate` wrapping, fixture helpers) this plan describes from research, not from quoted source. Each task's Step 1 is mandatory — adapt the sketched code to the real structure; the *behavioral contract* (gates, semantics, fault codes, test assertions) is the fixed part.
- **Do not run the full suite between Tasks 2 and 6** — the signature-surface guard is expected red until the refreeze lands.
- **The published-surface leak check in Task 6 Step 1 is the safety net** for accidental API exposure from Tasks 4-5 (`RomArtMaterializer`, `RomArtSheetSource` must stay internal).
- Sub-project 2 (sample-flappy) consumes exactly: `registerRomObjectArt` + the Tails constants worked example from Task 7's doc section. Its plan is written after this plan executes.
