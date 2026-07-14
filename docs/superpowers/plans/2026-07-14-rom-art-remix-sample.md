# ROM-Art Remix Sample Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the gallery's eighth maintained source project: a minimal Sonic 2 patch mod that teaches safe ROM-art intake independently of the native-Tails Flappy sample.

**Architecture:** The sample contributes one short baked Sonic 2 level and one rewind-recreatable display object. Its registration transaction requests the existing Tails flight art from the user's Sonic 2 ROM, while the packaged mod contains only creator source, baked level data, and metadata. It launches with the normal data-select team; Sonic and Tails share S2 palette line 0, while the guide accurately calls out the Knuckles lock-on palette mutation.

**Tech Stack:** Java 21, Maven, JUnit 5, `ggfmod`, Mod API 2.1-compatible ROM-art intake, Sonic 2 ROM-gated integration tests.

**Design reference:** `docs/superpowers/specs/2026-07-14-rom-art-remix-sample-design.md`

**Prerequisites:** Complete `docs/superpowers/plans/2026-07-14-art-converter-column-major-fix.md`, `docs/superpowers/plans/2026-07-14-s3k-mod-zone-adapter.md`, and `docs/superpowers/plans/2026-07-14-mod-gameplay-policies.md` first. Do not delete the old Flappy ROM-art consumer until Task 5 is green.

**Commit policy:** Keep the repository trailer block on every commit. Tasks 1-4 use `Changelog: n/a: covered by the aggregate gallery entry in Task 5`; Task 5 stages the guide and changelog and uses `Changelog: updated`, `Guide: updated`, with other mappings marked accurately.

---

## File map

- Create the complete `src/test/resources/mods/sample-rom-art-remix-src/` source project.
- Create `RomArtRemixMod` and `TailsFlightArtObject` under `example.romartremix`.
- Create a one-screen S2 format-v1 baked level source and deterministic asset generator.
- Add package, level-source, runtime-materialization, rendering, rewind, and no-ROM-payload tests.
- Move the old Flappy ROM-art lesson into `docs/modding/guides/rom-art-remix.md` and update gallery indexes.
- Modify no engine runtime code; any runtime defect exposed here belongs in a separately reviewed fix.

### Task 1: Scaffold and admit the eighth maintained source project

**Files:**
- Create: `src/test/resources/mods/sample-rom-art-remix-src/sample.properties`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/README.md`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/build.ps1`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/build.sh`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/pom.xml`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/README.md`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/src/main/resources/META-INF/openggf-mod.yaml`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java`

- [ ] **Step 1: Write the failing eight-project gallery assertions**

```java
private static final Path ROM_ART_REMIX = Path.of(
        "src/test/resources/mods/sample-rom-art-remix-src/project");

private static final Set<String> EXPECTED_IDS = Set.of(
        "openggf-gallery-music-sample", "phase2-reskin", "phase2-sample",
        "phase3-character", "phase3-standalone", "sample-flappy",
        "sample-platformer", "sample-rom-art-remix");

@Test void maintainedGalleryContainsExactlyEightProjects() {
    assertEquals(8, discoverSampleProjects().size());
}
```

Add `sample-rom-art-remix -> >=2.1.0 <3.0.0` and include it in the trusted-code sample set.

- [ ] **Step 2: Run and verify the count/id failure**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test`

Expected: the expected ID is absent and the gallery count is seven.

- [ ] **Step 3: Add reproducible wrapper files and the S2 manifest**

```yaml
id: sample-rom-art-remix
name: ROM Art Remix
version: 1.0.0
engineApiRange: ">=2.1.0 <3.0.0"
type: code
baseGame: s2
entrypoint: example.romartremix.RomArtRemixMod
```

Copy the maintained wrapper conventions from `sample-flappy-src`: resolve the engine SDK JAR explicitly, invoke the `ggfmod` Maven goal, and write only beneath the sample's `target/` directory. `sample.properties` identifies `project` and the expected packaged JAR; neither wrapper searches for or copies a ROM.

- [ ] **Step 4: Run the package contract test**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage" test`

Expected: all eight projects are discovered, scanned, and validated.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/mods/sample-rom-art-remix-src src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java
git commit -m "feat: scaffold ROM-art remix sample"
```

### Task 2: Add a deterministic one-screen Sonic 2 level

**Files:**
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/src/main/mod/level-source/level.json`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/src/main/mod/level-source/binary-assets.properties`
- Create: `src/test/java/com/openggf/tools/modsdk/SampleRomArtRemixAssetGenerator.java`
- Create: `src/test/java/com/openggf/tools/modsdk/TestSampleRomArtRemixLevelSource.java`
- Modify: `src/test/resources/mods/sample-rom-art-remix-src/project/pom.xml`

- [ ] **Step 1: Write a failing source-level contract**

```java
@Test void sourceIsShortStaticS2FormatWithOneDisplayObject() throws Exception {
    JsonNode level = mapper.readTree(LEVEL_SOURCE.resolve("level.json").toFile());
    assertEquals(1, level.path("formatVersion").asInt());
    assertEquals("s2", level.path("game").asText());
    assertEquals(1, level.path("objects").size());
    assertEquals("sample-rom-art-remix:tails-flight-art",
            level.path("objects").get(0).path("key").asText());
    assertTrue(level.path("mapWidthPixels").asInt() <= 640);
}
```

Also parse every declared binary asset and assert exact lengths, indices, and palette bounds.

- [ ] **Step 2: Run and verify missing source failure**

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleRomArtRemixLevelSource" test`

Expected: source assets do not exist.

- [ ] **Step 3: Generate the small static sky/platform level**

The generator writes deterministic format-v1 S2 blocks/chunks/map/collision/palette inputs using the same byte-order and SHA-256 manifest conventions as `SampleFlappyAssetGenerator`. The level contains ordinary baked background/terrain only and a single layout placement for the display object. Keep camera bounds stationary enough to make the art comparison repeatable.

```java
writeProperties(Map.of(
        "blocks.bin", sha256(blocks),
        "chunks.bin", sha256(chunks),
        "map.bin", sha256(map),
        "collision.bin", sha256(collision),
        "palette.bin", sha256(palette)));
```

- [ ] **Step 4: Convert and validate twice for reproducibility**

Run: `mvn "-DskipTests" test-compile`

Run: `java -cp target/test-classes com.openggf.tools.modsdk.SampleRomArtRemixAssetGenerator`

Run: `mvn "-Dtest=com.openggf.tools.modsdk.TestSampleRomArtRemixLevelSource" test`

Run: `mvn "-DskipTests" package`

Expected: the focused test passes and repeated conversion leaves no tracked diff.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/mods/sample-rom-art-remix-src/project/src/main/mod src/test/resources/mods/sample-rom-art-remix-src/project/pom.xml src/test/java/com/openggf/tools/modsdk/SampleRomArtRemixAssetGenerator.java src/test/java/com/openggf/tools/modsdk/TestSampleRomArtRemixLevelSource.java
git commit -m "feat: add ROM-art remix level source"
```

### Task 3: Register the exact bounded Sonic 2 ROM-art request

**Files:**
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/src/main/java/example/romartremix/RomArtRemixMod.java`
- Create: `src/test/resources/mods/sample-rom-art-remix-src/project/src/main/java/example/romartremix/TailsFlightArtObject.java`
- Create: `src/test/java/com/openggf/mods/code/TestSampleRomArtRemixRegistration.java`
- Modify: `src/test/resources/mods/sample-rom-art-remix-src/project/README.md`

- [ ] **Step 1: Write failing isolated registration assertions**

```java
@Test void registersOneS2ZoneAndTheExactTailsArtWindow() throws Exception {
    ModRegistrationPlan plan = compileAndRegister(SAMPLE);
    assertEquals(1, plan.romObjectArt().size());
    RomArtRequest request = plan.romObjectArt().get("sample-rom-art-remix:tails-flight");
    assertEquals(0x64320, request.artAddress());
    assertEquals(0xB8C0, request.artLength());
    assertEquals(0x739E2, request.mappingAddress());
    assertEquals(0x7446C, request.dplcAddress());
    assertEquals(0, request.paletteLine());
    assertFalse(plan.zones().getFirst().gameStart());
}
```

Assert the zone is inserted after a valid S2 anchor and that no launch-team, input-filter, or HUD contribution is registered.

- [ ] **Step 2: Run and verify classes are absent**

Run: `mvn "-Dtest=com.openggf.mods.code.TestSampleRomArtRemixRegistration" test`

Expected: sample compilation fails because its entrypoint and object do not exist.

- [ ] **Step 3: Implement registration and a rewind-recreatable display object**

```java
context.registerObject("tails-flight-art",
        (spawn, registry) -> new TailsFlightArtObject(spawn));
context.registerRomObjectArt("tails-flight", new RomArtRequest(
        0x64320, RomArtCompression.UNCOMPRESSED, 0xB8C0,
        0x739E2, 0x7446C, 0, 1));
context.registerZone(new ModZoneContribution("rom-art-gallery",
        new BakedLevelRef("levels/rom-art-gallery/level.json"), "ehz2", null));
```

`TailsFlightArtObject` keeps only a non-final `animTick`, selects mapping frames 94/95 every four frames, draws `sample-rom-art-remix:tails-flight` at its centre coordinates, and recreates from `context.spawn()`. It does not hide, replace, recolour, or control the playable sprite.

- [ ] **Step 4: Run registration and transaction tests**

Run: `mvn "-Dtest=com.openggf.mods.code.TestSampleRomArtRemixRegistration,com.openggf.mods.code.TestModContextRomArt,com.openggf.mods.code.TestModContextAndFaultBoundary" test`

Expected: all tests pass, including existing non-S2 and standalone ROM-art rejection coverage.

- [ ] **Step 5: Commit**

```powershell
git add src/test/resources/mods/sample-rom-art-remix-src/project/src/main/java src/test/resources/mods/sample-rom-art-remix-src/project/README.md src/test/java/com/openggf/mods/code/TestSampleRomArtRemixRegistration.java
git commit -m "feat: teach bounded Sonic 2 ROM-art intake"
```

### Task 4: Prove default-team rendering, palette behavior, rewind, and packaging

**Files:**
- Create: `src/test/java/com/openggf/mods/code/TestSampleRomArtRemixIntegration.java`
- Modify: `src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java`

- [ ] **Step 1: Add a ROM-gated end-to-end test before moving old assertions**

```java
@Test void defaultTeamMaterializesRendersAndRewindsTailsFlightArt() throws Exception {
    assumeTrue(Files.isRegularFile(Path.of("s2.gen")));
    LoadedSample loaded = buildInstallResolveWithS2Rom(SAMPLE);
    assertEquals(CharacterKey.SONIC, loaded.launchTeam().main());
    PatternSpriteRenderer renderer = loaded.objectArt().getRenderer(
            "sample-rom-art-remix:tails-flight");
    assertNotNull(renderer);
    assertEquals(96, renderer.sheet().frames().size());
    assertFramePixelProbe(renderer, 94, EXPECTED_TAILS_FLIGHT_PROBE);
    ObjectRefId id = loaded.onlyDisplayObjectId();
    stepFrames(9);
    rewindToInitialKeyframe();
    assertEquals(id, loaded.onlyDisplayObjectId());
    assertEquals(initialRenderCommands(), currentRenderCommands());
}
```

Copy the exact ROM materialization and frame-94/95 pixel probes from `TestSampleFlappyIntegration` while the old test still exists. Add a palette assertion proving the default Sonic team and a Tails main both leave line 0 on the shared `Pal_SonicTails` values; do not assert that selecting Tails is required.

- [ ] **Step 2: Run and verify the new integration is initially red**

Run: `mvn "-Ds2.rom.path=s2.gen" "-Dtest=com.openggf.mods.code.TestSampleRomArtRemixIntegration" test`

Expected: any missing materialized resource, frame probe, or sample packaging assertion fails before transfer is considered complete.

- [ ] **Step 3: Make the new sample own the full ROM-art contract**

The integration must verify:

- no ROM byte range appears as an entry in the source or packaged JAR;
- materialization occurs only after an accepted S2 ROM source is installed;
- mapping frames 94/95 retain DPLC remapping and palette line 0;
- the stock/default Sonic team launches successfully;
- rewind restores the object's animation scalar and stable layout identity;
- a Knuckles-main lock-on is documented as a presentation caveat because it shifts palette indices 2-5, not as a rejected configuration.

- [ ] **Step 4: Build the sample and inspect the archive**

Run: `mvn "-Ds2.rom.path=s2.gen" "-Dtest=com.openggf.mods.code.TestSampleRomArtRemixIntegration,com.openggf.tools.modsdk.TestSampleModsPackage" test`

Run:

```powershell
$build = Join-Path $env:TEMP ("sample-rom-art-remix-" + [guid]::NewGuid())
& src/test/resources/mods/sample-rom-art-remix-src/build.ps1 `
  -EngineJar (Resolve-Path target/OpenGGF-0.6.prerelease.jar) `
  -SdkJar (Resolve-Path target/OpenGGF-0.6.prerelease-openggf-mod-sdk.jar) `
  -OutputDirectory $build
jar tf (Join-Path $build 'target/sample-rom-art-remix-mod.jar')
```

Expected: tests pass; the archive contains code, manifest, and baked creator assets, with no `.gen`, `.bin` ROM dump, or materialized Tails sheet.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/openggf/mods/code/TestSampleRomArtRemixIntegration.java src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java
git commit -m "test: prove ROM-art remix materialization"
```

### Task 5: Transfer the guide and release the old Flappy consumer

**Files:**
- Create: `docs/modding/guides/rom-art-remix.md`
- Delete: `docs/modding/guides/flappy-remix.md`
- Modify: `docs/modding/index.md`
- Modify: `docs/modding/samples/index.md`
- Modify: `docs/modding/content-mods.md`
- Modify: `src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Add failing guide-link and ownership assertions**

Extend the documentation/gallery checks in `TestSampleModsPackage` to require `rom-art-remix.md`, reject stale links to `flappy-remix.md`, and assert that only `sample-rom-art-remix` registers `RomArtRequest` after the subsequent Flappy migration.

- [ ] **Step 2: Run both consumers together before deleting anything**

Run: `mvn "-Ds2.rom.path=s2.gen" "-Dtest=com.openggf.mods.code.TestSampleRomArtRemixIntegration,com.openggf.mods.code.TestSampleFlappyIntegration" test`

Expected: both are green. Stop here if the replacement sample is not green.

- [ ] **Step 3: Write the source-first guide and remove transferred Flappy assertions**

The guide explains the bounded request fields, validation/materialization boundary, DPLC frame ordering, package inspection, and legal asset model. State explicitly:

```text
Sonic 2 palette line 0 is Pal_SonicTails, shared by Sonic and Tails, so the
sample works with the default Sonic team. A Knuckles-main lock-on changes line
0 indices 2-5 and may recolour borrowed Tails art.
```

Remove ROM-art materialization/render assertions from `TestSampleFlappyIntegration` only after the new integration owns them. Do not yet rewrite Flappy's S2 implementation; that is the next plan.

- [ ] **Step 4: Run the documentation/sample completion gate**

Run: `mvn "-Ds2.rom.path=s2.gen" "-Dtest=com.openggf.tools.modsdk.TestSampleModsPackage,com.openggf.tools.modsdk.TestSampleRomArtRemixLevelSource,com.openggf.mods.code.TestSampleRomArtRemixRegistration,com.openggf.mods.code.TestSampleRomArtRemixIntegration" test`

Expected: all tests pass and the maintained gallery count remains exactly eight.

- [ ] **Step 5: Commit**

```powershell
git add docs/modding/guides/rom-art-remix.md docs/modding/guides/flappy-remix.md docs/modding/index.md docs/modding/samples/index.md docs/modding/content-mods.md src/test/java/com/openggf/mods/code/TestSampleFlappyIntegration.java CHANGELOG.md
git commit -m "docs: move ROM-art lesson to remix sample"
```

## Completion gate

- [ ] Run the Task 5 focused command.
- [ ] Run `mvn package`.
- [ ] Run the Task 4 PowerShell wrapper command; on a Bash host, run `build.sh` with the same engine JAR, SDK JAR, and a fresh temporary output directory.
- [ ] Confirm the packaged sample contains no ROM-derived art payload.
- [ ] Confirm the integration launches the default Sonic team without selecting Tails.
- [ ] Confirm documentation names the real Knuckles-main indices 2-5 caveat.
- [ ] Confirm the old Flappy ROM-art assertions were removed only after the replacement test passed.
- [ ] Confirm `git diff --check` is clean.
