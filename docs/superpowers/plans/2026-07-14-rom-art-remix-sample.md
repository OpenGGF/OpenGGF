# ROM-Art Remix Sample Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the gallery's eighth maintained source project: a complete Sonic 2 patch sample that teaches bounded ROM-art intake independently of native-Tails Flappy.

**Architecture:** The sample contributes one short format-v1 S2 level and one rewind-recreatable display object. Its registration transaction requests Tails flight art from the user's S2 ROM; the package contains only code, metadata, and original baked level data. The project uses the default Sonic team because S2 palette line 0 is shared by Sonic and Tails.

**Tech Stack:** Java 21, Maven, JUnit 5, `ggfmod`, Mod API 2.1 ROM-art intake, Sonic 2 ROM-gated integration tests.

**Design reference:** `docs/superpowers/specs/2026-07-14-rom-art-remix-sample-design.md`

**Prerequisites:** Complete the converter, S3K adapter, and gameplay-policy plans first. Keep the existing Flappy ROM-art consumer and `flappy-remix.md` guide in place throughout this plan; Plan D removes them only after this sample is green.

**Commit policy:** Keep the repository trailer block. Tasks 1-2 use `Changelog: n/a: covered by the aggregate gallery entry in Task 3`. Task 3 stages `CHANGELOG.md` and uses `Changelog: updated` and `Guide: n/a: modding guides are outside docs/guide`, with other trailers accurate.

---

## File map

- Create the complete `src/test/resources/mods/sample-rom-art-remix-src/` source project atomically.
- Create `RomArtRemixMod` and `TailsFlightArtObject` under `example.romartremix`.
- Create a deterministic one-screen S2 format-v1 level source using the parser's exact root keys and Base64 asset convention.
- Add package, level-source, registration, materialization, decoded-pattern, rewind, and archive tests.
- Create `docs/modding/guides/rom-art-remix.md` and move only the old guide's ROM-borrowing links in this plan.
- Leave non-ROM Flappy guide links and the old guide alive for Plan D's atomic native-guide migration.

### Task 1: Add one complete, packageable eighth source project

**Files:**
- Create: `src/test/resources/mods/sample-rom-art-remix-src/sample.properties`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/README.md`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/build.ps1`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/build.sh`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/pom.xml`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/README.md`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/src/main/resources/META-INF/openggf-mod.yaml`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/src/main/java/example/romartremix/RomArtRemixMod.java`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/src/main/java/example/romartremix/TailsFlightArtObject.java`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/src/main/mod/level-source/level.json`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/src/main/mod/level-source/binary-assets.properties`
- Create: `src/test/java/com/openggf/tools/modsdk/SampleRomArtRemixAssetGenerator.java`
- Create: `src/test/java/com/openggf/tools/modsdk/TestSampleRomArtRemixLevelSource.java`
- Create: `src/test/java/com/openggf/mods/code/TestSampleRomArtRemixRegistration.java`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java`

- [ ] **Step 1: Write failing gallery, exact-schema, and registration tests**

```java
@Test void maintainedGalleryContainsExactlyEightCompleteProjects() throws Exception {
    assertEquals(Set.of("openggf-gallery-music-sample", "phase2-reskin", "phase2-sample",
            "phase3-character", "phase3-standalone", "sample-flappy",
            "sample-platformer", "sample-rom-art-remix"), discoveredIds());
    assertEquals(8, discoverSampleProjects().size());
    assertPackagesAndValidates(ROM_ART_REMIX);
}

@Test void levelUsesOnlyTheStrictV1RootShapeAndOneDisplayObject() throws Exception {
    JsonNode level = readLevelSource();
    assertEquals(Set.of("formatVersion", "zoneName", "zoneIndex", "levelIndex",
            "blockGridSide", "width", "height", "bounds", "start", "music",
            "assets", "objects", "rings"), fieldNames(level));
    assertEquals(1, level.path("formatVersion").asInt());
    assertTrue(level.path("width").asInt() <= 3);
    assertEquals(2, level.path("height").asInt());
    assertEquals("sample-rom-art-remix:tails-flight-art",
            level.path("objects").get(0).path("objectKey").asText());
    assertEquals(1, level.path("objects").size());
    assertEquals(0, level.path("rings").size());
    assertFalse(level.has("game"));
    assertFalse(level.has("mapWidthPixels"));
}

@Test void registersExactTailsWindowWithoutGameplayPolicies() throws Exception {
    ModRegistrationPlan plan = compileAndRegister(SAMPLE);
    RomArtRequest request = plan.romObjectArt().get("sample-rom-art-remix:tails-flight");
    assertEquals(0x64320, request.artAddress());
    assertEquals(0xB8C0, request.uncompressedByteSize());
    assertEquals(0x739E2, request.mappingAddress());
    assertEquals(0x7446C, request.dplcAddress());
    assertEquals(0, request.paletteLine());
    assertTrue(plan.launchTeams().isEmpty());
    assertTrue(plan.inputFilters().isEmpty());
    assertTrue(plan.hudProfiles().isEmpty());
}
```

The initial run is red because the eighth project is absent. Do not add the project to `TestSampleModsPackage` until its entrypoint, level source, and assets all exist in this same task.

- [ ] **Step 2: Run and verify the missing-project failure**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.tools.modsdk.TestSampleRomArtRemixLevelSource,com.openggf.mods.code.TestSampleRomArtRemixRegistration" test`

Expected: compilation/resource discovery fails only for the new sample and tests.

- [ ] **Step 3: Implement the complete source project**

Use the real manifest vocabulary:

```yaml
formatVersion: 1
id: sample-rom-art-remix
name: ROM Art Remix
version: 1.0.0
authors: [Mod Author]
description: Minimal bounded Sonic 2 ROM-art intake sample.
engineApiRange: ">=2.1.0 <3.0.0"
type: patch
baseGame: s2
entrypoint: example.romartremix.RomArtRemixMod
dependencies: []
audioOverrides: {}
artOverrides: {}
```

The level JSON uses the exact v1 keys shown in Step 1. `binary-assets.properties` maps the actual parser asset names (`patterns.bin`, `chunks.bin`, `blocks.bin`, `fg-map.bin`, `solid-heights.bin`, `solid-widths.bin`, `solid-angles.bin`, `collision-primary.bin`, `collision-secondary.bin`, `palettes.bin`) to Base64-encoded file contents. It does not contain hashes. `SampleRomArtRemixAssetGenerator` mirrors `SampleFlappyAssetGenerator`'s deterministic binary writers and Base64 property output.

```java
context.registerObject("tails-flight-art",
        (spawn, registry) -> new TailsFlightArtObject(spawn));
context.registerRomObjectArt("tails-flight", new RomArtRequest(
        0x64320, RomArtCompression.UNCOMPRESSED, 0xB8C0,
        0x739E2, 0x7446C, 0, 1));
context.registerZone(new ModZoneContribution("rom-art-gallery",
        new BakedLevelRef("levels/rom-art-gallery/level.json"), "ehz2", null));
```

`TailsFlightArtObject` has one non-final `animTick`, alternates mapping frames 94/95 every four frames, draws `sample-rom-art-remix:tails-flight` at centre coordinates, and returns `new TailsFlightArtObject(context.spawn())` from `recreateForRewind`. It never controls or hides the playable.

- [ ] **Step 4: Generate assets and run the complete source-project gate**

Run: `mvn "-DskipTests" test-compile`

Run: `java -cp target/test-classes com.openggf.tools.modsdk.SampleRomArtRemixAssetGenerator`

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.tools.modsdk.TestSampleRomArtRemixLevelSource,com.openggf.mods.code.TestSampleRomArtRemixRegistration,com.openggf.mods.code.TestModContextRomArt" test`

Expected: all tests pass; the gallery becomes exactly eight only after the project compiles, packages, converts, and validates.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/mods/sample-rom-art-remix-src src/test/java/com/openggf/tools/modsdk/SampleRomArtRemixAssetGenerator.java src/test/java/com/openggf/tools/modsdk/TestSampleRomArtRemixLevelSource.java src/test/java/com/openggf/mods/code/TestSampleRomArtRemixRegistration.java src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java
git commit -m "feat: add complete ROM-art remix sample"
```

### Task 2: Prove materialization, default-team palette, decoded art, rewind, and archive safety

**Files:**
- Create: `src/test/java/com/openggf/mods/code/TestSampleRomArtRemixIntegration.java`

- [ ] **Step 1: Write the ROM-gated end-to-end test**

```java
@Test void defaultTeamMaterializesFramesPatternsAndRewindState() throws Exception {
    assertNotNull(RomTestUtils.ensureSonic2RomAvailable(),
            "requires the explicitly supplied Sonic 2 ROM");
    LoadedSample loaded = buildInstallAndResolveWithS2Rom(SAMPLE);
    assertEquals(CharacterKey.SONIC, loaded.launchTeam().main());

    ObjectSpriteSheet sheet = loaded.objectArt().getSheet(
            "sample-rom-art-remix:tails-flight");
    assertTrue(sheet.getFrameCount() > 95);
    assertFalse(sheet.getFrame(94).pieces().isEmpty());
    assertFalse(sheet.getFrame(95).pieces().isEmpty());
    SpriteFramePiece first = sheet.getFrame(94).pieces().getFirst();
    assertEquals(0, first.paletteIndex());
    Pattern pattern = sheet.getPatterns()[first.tileIndex()];
    assertTrue(IntStream.range(0, 64)
            .anyMatch(i -> pattern.getPixel(i % 8, i / 8) != 0));

    ObjectRefId id = loaded.onlyDisplayObjectId();
    int initialFrame = loaded.displayedMappingFrame();
    stepFrames(9);
    rewindToInitialKeyframe();
    assertEquals(id, loaded.onlyDisplayObjectId());
    assertEquals(initialFrame, loaded.displayedMappingFrame());
}
```

Add assertions that `Sonic2GameModule.getCharacterPaletteAddr()` yields the shared `SONIC_TAILS_PALETTE_ADDR` for the default Sonic/Tails cases and document, without rejecting, the Knuckles-main lock-on mutation of line-0 indices 2-5.

- [ ] **Step 2: Run with an explicit ROM and reject silent skips**

Run: `mvn "-Dsonic2.rom.path=s2.gen" "-Dtest=com.openggf.mods.code.TestSampleRomArtRemixIntegration" test`

Expected: the test executes (not skipped) and passes. If `s2.gen` is absent, verification is blocked.

- [ ] **Step 3: Inspect the real package contents**

```powershell
$build = Join-Path $env:TEMP ("sample-rom-art-remix-" + [guid]::NewGuid())
& src/test/resources/mods/sample-rom-art-remix-src/build.ps1 `
  -EngineJar (Resolve-Path target/OpenGGF-0.6.prerelease.jar) `
  -SdkJar (Resolve-Path target/OpenGGF-0.6.prerelease-openggf-mod-sdk.jar) `
  -OutputDirectory $build
if ($LASTEXITCODE -ne 0) { throw "sample-rom-art-remix build failed" }
$entries = jar tf (Join-Path $build 'target/sample-rom-art-remix-mod.jar')
if ($entries -match '\.gen$|art/tails-flight\.ggfs$') { throw "ROM-derived payload packaged" }
```

Original baked level binaries are allowed. The forbidden entries are ROM images and a materialized Tails sheet; materialization remains in memory at launch.

- [ ] **Step 4: Run ROM-art and rewind regression tests**

Run: `mvn "-Dsonic2.rom.path=s2.gen" "-Dtest=com.openggf.mods.code.TestSampleRomArtRemixIntegration,com.openggf.mods.code.TestRomArtMaterializer,com.openggf.game.rewind.coverage.TestRewindCoverageGuard" test`

Expected: all tests execute and pass.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/openggf/mods/code/TestSampleRomArtRemixIntegration.java
git commit -m "test: prove ROM-art remix materialization"
```

### Task 3: Publish the ROM-art guide without orphaning native-Flappy links

**Files:**
- Create: `docs/modding/guides/rom-art-remix.md`
- Modify: `docs/modding/guides/standalone-platformer.md`
- Modify: `docs/modding/index.md`
- Modify: `docs/modding/samples/index.md`
- Modify: `docs/modding/content-mods.md`
- Modify: `src/test/resources/mods/sample-rom-art-remix-src/README.md`
- Modify: `src/test/resources/mods/sample-rom-art-remix-src/project/README.md`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Write the source-first ROM-art guide**

Explain the bounded address fields, S2-only validation, launch-time in-memory materialization, DPLC frame ordering, decoded pattern probe, rewind scalar, and package inspection. State exactly:

```text
Sonic 2 palette line 0 is Pal_SonicTails, shared by Sonic and Tails, so the
sample works with the default Sonic team. A Knuckles-main lock-on changes line
0 indices 2-5 and may recolour borrowed Tails art.
```

- [ ] **Step 2: Move only links that belong to the ROM-borrowing chapter**

Change `docs/modding/guides/standalone-platformer.md`'s Chapter-3 borrowing link to the matching anchor in `rom-art-remix.md`. Add the new sample/guide to the indexes and content reference. Do not delete `flappy-remix.md`, change its non-ROM Chapter-4/6 links, or change the Flappy README in this plan; those links remain valid until Plan D creates their native replacement.

- [ ] **Step 3: Add a non-orphan link assertion**

Extend `TestSampleModsPackage` to require the new guide and the updated Chapter-3 link, while still requiring `flappy-remix.md` for the remaining links. Do not add a repository-wide stale-`flappy-remix` rejection yet.

- [ ] **Step 4: Run the sample/documentation gate**

Run: `mvn "-Dsonic2.rom.path=s2.gen" "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.tools.modsdk.TestSampleRomArtRemixLevelSource,com.openggf.mods.code.TestSampleRomArtRemixRegistration,com.openggf.mods.code.TestSampleRomArtRemixIntegration" test`

Expected: all tests pass; both the new ROM-art guide and the still-live old Flappy guide have valid consumers.

- [ ] **Step 5: Commit**

```powershell
git add docs/modding/guides/rom-art-remix.md docs/modding/guides/standalone-platformer.md docs/modding/index.md docs/modding/samples/index.md docs/modding/content-mods.md src/test/resources/mods/sample-rom-art-remix-src/README.md src/test/resources/mods/sample-rom-art-remix-src/project/README.md src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java CHANGELOG.md
git commit -m "docs: publish ROM-art remix sample"
```

## Completion gate

- [ ] Run Task 3's focused command with `-Dsonic2.rom.path=s2.gen`; a missing ROM blocks the gate.
- [ ] Run `mvn package`.
- [ ] Confirm the gallery contains exactly eight complete projects.
- [ ] Confirm the package contains no ROM image or materialized Tails sheet.
- [ ] Confirm the default Sonic team is used and the Knuckles indices 2-5 caveat is documented.
- [ ] Confirm `flappy-remix.md` and every remaining non-ROM link still resolve for Plan D.
- [ ] Confirm `git diff --check` is clean.
