# Native Flappy Death and Title-Card Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore stock Tails death physics and publish the custom S3K palette before the initial title card renders.

**Architecture:** Make death enter through the existing `setDead` state-transition method so flight teardown remains centralized. Prime a newly installed custom-zone palette bridge in `LevelManager`, where graphics and palette-ownership services are available, while retaining the service-free in-memory level format.

**Tech Stack:** Java 21, Maven, JUnit 5, Mockito, OpenGGF mod integration harness.

---

## File map

- `src/main/java/com/openggf/sprites/playable/AbstractPlayableSprite.java` — canonical instant-death transition.
- `src/test/java/com/openggf/sprites/managers/TestPlayableSpriteMovementTailsFlight.java` — native flight/death velocity regression.
- `src/main/java/com/openggf/level/LevelManager.java` — custom-zone palette bridge installation and initial resolution.
- `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java` — real S3K sample boot and pre-frame palette regression.
- `docs/modding/guides/native-tails-flappy.md` — sample behavior and palette lifecycle guidance.
- `CHANGELOG.md` — user-visible corrections.

### Task 1: Preserve the stock death hop while clearing flight

**Files:**
- Modify: `src/test/java/com/openggf/sprites/managers/TestPlayableSpriteMovementTailsFlight.java`
- Modify: `src/main/java/com/openggf/sprites/playable/AbstractPlayableSprite.java`

- [ ] **Step 1: Write the failing regression**

Add a test that activates flight, calls `tails.applyCrushDeath()`, and asserts:

```java
assertTrue(tails.applyCrushDeath());
assertTrue(tails.getDead());
assertFalse(tails.getTailsFlightController().isActive());
assertEquals((short) -0x700, tails.getYSpeed());

movement.handleMovement(false, false, false, false,
        false, false, false, false);
assertEquals((short) -0x6C8, tails.getYSpeed());
```

- [ ] **Step 2: Prove the regression is red**

Run:

```powershell
mvn "-Dtest=com.openggf.sprites.managers.TestPlayableSpriteMovementTailsFlight" test
```

Expected: the new test fails because instant death leaves the flight controller active and applies flight gravity instead of the stock `+$38` death gravity.

- [ ] **Step 3: Route instant death through the canonical transition**

In `applyDeath(DamageCause cause)`, replace the direct field assignment with:

```java
setDead(true);
```

Do not alter the existing `setYSpeed((short) -0x700)`, countdown, camera, or sound logic.

- [ ] **Step 4: Prove the focused suite is green**

Run the command from Step 2. Expected: all tests in the class pass.

### Task 2: Resolve custom-zone palettes before title-card presentation

**Files:**
- Modify: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`
- Modify: `src/main/java/com/openggf/level/LevelManager.java`

- [ ] **Step 1: Write the failing initial-load regression**

In the Flappy launch harness, before any `HeadlessTestRunner` frame, assert that
the palette registry has resolved, line 0 color 6 is owned by
`host:s3k-character`, and its Sega word is `0x000E`:

```java
assertTrue(GameServices.paletteOwnershipRegistry().hasResolvedThisFrame());
assertEquals("host:s3k-character", GameServices.paletteOwnershipRegistry()
        .ownerAt(PaletteSurface.NORMAL, 0, 6));
assertEquals(0x000E, PaletteWriteSupport.segaWordFromColor(
        GameServices.level().getCurrentLevel().getPalette(0).getColor(6)));
```

- [ ] **Step 2: Prove the regression is red**

Run:

```powershell
mvn "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration" "-Ds3k.rom.path=s3k.gen" test
```

Expected: the new pre-frame assertion fails because bridge claims are first resolved by `LevelFrameStep`.

- [ ] **Step 3: Prime the bridge at its runtime installation point**

After `activeCustomZonePaletteBridge` is created in `LevelManager.initObjectArt()`,
obtain `GameServices.paletteOwnershipRegistryOrNull()`. When both values are
non-null, call `beginFrame()`, submit bridge claims, then invoke:

```java
PaletteWriteSupport.resolvePendingFrameWrites(
        paletteRegistry, level, null, graphicsManager);
```

Keep the lives-palette ownership routing that follows. Do not change stock-level
loading or make `Sonic3kLevel` resolve runtime services.

- [ ] **Step 4: Prove focused palette and sample suites are green**

Run:

```powershell
mvn "-Dtest=com.openggf.mods.code.TestSampleFlappyIntegration,com.openggf.mods.code.TestS3kModZoneAdapter,com.openggf.mods.code.TestS3kModZoneLifecycle,com.openggf.game.sonic3k.TestSonic3kLevelInMemoryConstruction" "-Ds3k.rom.path=s3k.gen" test
```

Expected: all selected tests pass and no ROM-gated sample assertion skips.

### Task 3: Document and package the corrected sample

**Files:**
- Modify: `docs/modding/guides/native-tails-flappy.md`
- Modify: `CHANGELOG.md`
- Rebuild: `mods/sample-flappy-mod.jar` (ignored runtime artifact)

- [ ] **Step 1: Update creator documentation**

State that fatal contact uses the host's stock instant-death transition, which
clears active flight before applying the normal death hop. State that the S3K
adapter resolves initial host and creator palette ownership before the title
card, while later frames continue through the registry.

- [ ] **Step 2: Add a changelog entry**

Under the current unreleased section, record the corrected active-flight death
transition and initial custom-S3K title-card palette publication.

- [ ] **Step 3: Run documentation guards**

Run:

```powershell
mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.docs.TestModdingDocumentationLinks" test
```

Expected: all documentation policy tests pass.

- [ ] **Step 4: Rebuild and install the maintained sample**

Run:

```powershell
mvn package -DskipTests
$out = Join-Path $env:TEMP ("sample-flappy-fix-" + [guid]::NewGuid())
& src/test/resources/mods/sample-flappy-src/build.ps1 `
  -EngineJar (Resolve-Path target/OpenGGF-0.6.prerelease.jar) `
  -SdkJar (Resolve-Path target/OpenGGF-0.6.prerelease-openggf-mod-sdk.jar) `
  -OutputDirectory $out
Copy-Item -Force (Join-Path $out "target/sample-flappy-mod.jar") `
  mods/sample-flappy-mod.jar
```

Expected: the engine artifacts and `mods/sample-flappy-mod.jar` are freshly rebuilt.

### Task 4: Verify, commit, and merge into next

**Files:**
- Verify all modified files above.
- Merge into: `C:/Users/farre/IdeaProjects/sonic-engine` (`next` worktree).

- [ ] **Step 1: Run the feature-branch completion gate**

Run:

```powershell
mvn package "-Ds3k.rom.path=s3k.gen"
```

Expected: build success with the complete test count and zero failures/errors.

- [ ] **Step 2: Commit the fixes with policy trailers**

Stage only the files in this plan. Commit with `Changelog: updated`,
`Guide: n/a: modding guide updated outside player guide`, and justified `n/a`
trailers for the remaining policy categories.

- [ ] **Step 3: Merge from the clean next worktree**

In `C:/Users/farre/IdeaProjects/sonic-engine`, confirm `next` is clean, then run:

```powershell
git merge --no-ff feature/ai-native-tails-flappy
```

Expected: a merge commit on `next` with no unrelated files changed.

- [ ] **Step 4: Rebuild, install, and verify merged next**

Copy or rebuild the ignored sample jar under the `next` worktree, then run:

```powershell
mvn package "-Ds3k.rom.path=s3k.gen"
```

Expected: the merged `next` branch passes the same complete package gate.
