# Art Converter Column-Major Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make SDK-baked multi-tile sprite pieces use the Genesis column-major pattern order expected by `SpritePieceRenderer`.

**Architecture:** Keep the native renderer unchanged and correct the single shared conversion loop in `ArtConverter`. Prove the contract with a 2-by-3-tile fixture whose six tiles use distinct palette indices; `PlayableArtConverter` inherits the fix because it delegates its base sheet to `ArtConverter`.

**Tech Stack:** Java 21, JUnit 5, Maven, OpenGGF GGFS baked-sheet reader/writer.

---

### Task 1: Lock the converter/renderer ordering contract

**Files:**
- Modify: `src/test/java/com/openggf/tools/modsdk/TestArtConverter.java`

- [ ] **Step 1: Add a failing non-square ordering test**

Add this test and helper beside the existing exact-palette conversion test:

```java
@Test
void emitsMultiTilePiecePatternsInGenesisColumnMajorOrder() throws Exception {
    int[] colors = {
            0xff240000, 0xff490000,
            0xff6d0000, 0xff920000,
            0xffb60000, 0xffdb0000
    };
    BufferedImage image = new BufferedImage(16, 24, BufferedImage.TYPE_INT_ARGB);
    for (int tileY = 0; tileY < 3; tileY++) {
        for (int tileX = 0; tileX < 2; tileX++) {
            int color = colors[tileY * 2 + tileX];
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    image.setRGB(tileX * 8 + x, tileY * 8 + y, color);
                }
            }
        }
    }
    Path png = temp.resolve("column-major.png");
    ImageIO.write(image, "png", png.toFile());
    Path yaml = temp.resolve("column-major.yaml");
    Files.writeString(yaml, columnMajorManifest());
    Path output = temp.resolve("column-major.ggfsheet");

    new ArtConverter().convert(png, yaml, output);
    BakedSheetReader.BakedSheet sheet = BakedSheetReader.read(Files.readAllBytes(output));

    assertEquals(6, sheet.patterns().length);
    assertArrayEquals(new int[]{1, 3, 5, 2, 4, 6},
            java.util.Arrays.stream(sheet.patterns())
                    .mapToInt(pattern -> pattern.getPixel(0, 0))
                    .toArray());
}

private static String columnMajorManifest() {
    return """
            formatVersion: 1
            paletteLine: 0
            palette: ["#000000", "#240000", "#490000", "#6D0000", "#920000", "#B60000", "#DB0000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000"]
            frames:
              - delay: 5
                pieces:
                  - sourceX: 0
                    sourceY: 0
                    widthPixels: 16
                    heightPixels: 24
                    xOffset: 0
                    yOffset: 0
                    hFlip: false
                    vFlip: false
                    paletteIndex: 0
                    priority: false
            """;
}
```

- [ ] **Step 2: Run the focused test and confirm the red state**

Run:

```powershell
mvn "-Dtest=com.openggf.tools.modsdk.TestArtConverter#emitsMultiTilePiecePatternsInGenesisColumnMajorOrder" test
```

Expected: FAIL because the actual pattern markers are row-major
`[1, 2, 3, 4, 5, 6]`, not `[1, 3, 5, 2, 4, 6]`.

- [ ] **Step 3: Commit the red regression test**

```powershell
git add src/test/java/com/openggf/tools/modsdk/TestArtConverter.java
git commit -m "test: cover column-major converted sprite pieces"
```

Use the repository-required documentation trailers; this test-only commit has no
changelog or guide change.

### Task 2: Correct the shared art conversion loop

**Files:**
- Modify: `src/main/java/com/openggf/tools/modsdk/ArtConverter.java:108-119`

- [ ] **Step 1: Change only the piece tile traversal order**

Replace the current nested loops with column-major traversal while leaving pixel
order inside each 8-by-8 pattern unchanged:

```java
for (int tileX = 0; tileX < piece.widthPixels(); tileX += 8) {
    for (int tileY = 0; tileY < piece.heightPixels(); tileY += 8) {
        Pattern pattern = new Pattern();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int color = image.getRGB(piece.sourceX() + tileX + x,
                    piece.sourceY() + tileY + y) & 0xFFFFFF;
            pattern.setPixel(x, y, paletteIndex.get(color).byteValue());
        }
        patterns.add(pattern);
        if (patterns.size() > limits.maxSheetPatterns()) {
            throw invalid("pattern count exceeds limit");
        }
    }
}
```

- [ ] **Step 2: Run the focused converter test class**

Run:

```powershell
mvn "-Dtest=com.openggf.tools.modsdk.TestArtConverter" test
```

Expected: PASS, including the new 2-by-3 ordering test and playable conversion test.

- [ ] **Step 3: Run renderer and sample packaging regressions**

Run:

```powershell
mvn "-Dtest=com.openggf.level.render.TestSpritePieceRendererSatPreparation,com.openggf.mods.TestSampleModsPackage" test
```

Expected: PASS. The renderer stays unchanged and packaged samples remain valid.

- [ ] **Step 4: Update the changelog**

Add a concise fix entry to the current unreleased section of `CHANGELOG.md` stating
that SDK art conversion now writes Genesis column-major pattern order for multi-tile
sprite pieces, fixing scrambled gallery art and playable sheets.

- [ ] **Step 5: Commit the implementation**

```powershell
git add src/main/java/com/openggf/tools/modsdk/ArtConverter.java CHANGELOG.md
git commit -m "fix: emit converted sprite pieces column-major"
```

Use `Changelog: updated` and the remaining repository-required documentation
trailers.

### Task 3: Verify the independent bugfix

**Files:**
- Verify only; no new source files.

- [ ] **Step 1: Run the complete focused regression set from a clean Maven invocation**

Run:

```powershell
mvn "-Dtest=com.openggf.tools.modsdk.TestArtConverter,com.openggf.level.render.TestSpritePieceRendererSatPreparation,com.openggf.mods.TestSampleModsPackage" test
```

Expected: PASS with zero failures and zero errors.

- [ ] **Step 2: Check policy and worktree scope**

Run:

```powershell
git diff --check HEAD~2..HEAD
git status --short
```

Expected: no whitespace errors; only the user's pre-existing
`docs/rewind/real-gaps.md` modification and generated `mods/` directory remain
outside the committed converter work.
