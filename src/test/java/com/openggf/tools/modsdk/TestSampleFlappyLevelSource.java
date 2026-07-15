package com.openggf.tools.modsdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.level.Pattern;
import com.openggf.level.objects.BakedSheetReader;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the sample-flappy level source materializes and converts through the real CLI. */
class TestSampleFlappyLevelSource {

    private static final Path SOURCE = Path.of(
            "src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source");
    private static final Set<String> ROOT_KEYS = Set.of(
            "formatVersion", "zoneName", "zoneIndex", "levelIndex", "blockGridSide",
            "width", "height", "bounds", "start", "music", "assets", "objects",
            "rings", "hostMetadata", "paletteClaims");
    private static final Set<String> ASSET_FILES = Set.of(
            "patterns.bin", "chunks.bin", "blocks.bin", "fg-map.bin", "solid-heights.bin",
            "solid-widths.bin", "solid-angles.bin", "collision-primary.bin",
            "collision-secondary.bin");

    @TempDir Path temp;

    @Test
    void levelUsesExactV2ShapeOneControllerNoRingsAndEqualNestedBounds() throws Exception {
        JsonNode level = new ObjectMapper().readTree(SOURCE.resolve("level.json").toFile());
        Set<String> actualKeys = new java.util.LinkedHashSet<>();
        level.fieldNames().forEachRemaining(actualKeys::add);

        assertEquals(ROOT_KEYS, actualKeys);
        assertEquals(2, level.path("formatVersion").asInt());
        assertEquals("S3KL", level.at("/hostMetadata/s3k/objectZoneSet").asText());
        assertEquals(1, level.path("objects").size());
        assertEquals("sample-flappy:controller",
                level.path("objects").get(0).path("objectKey").asText());
        assertEquals(0, level.path("rings").size());
        assertEquals(level.at("/bounds/minX"), level.at("/bounds/maxX"));
        assertEquals(level.at("/bounds/minY"), level.at("/bounds/maxY"));
        assertFalse(level.has("game"));
        assertFalse(level.has("cameraMinX"));
        assertFalse(level.has("visibleHeight"));

        int stockId = level.at("/music/stockId").asInt();
        Sonic3kMusic music = Sonic3kMusic.fromId(stockId);
        assertNotNull(music, "the S3K patch must reject unknown stock music IDs");
        assertSame(Sonic3kMusic.AIZ1, music);
    }

    @Test
    void v2BinaryInventoryOmitsLegacyPaletteFile() throws Exception {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(SOURCE.resolve("binary-assets.properties"))) {
            properties.load(input);
        }
        assertEquals(ASSET_FILES, properties.stringPropertyNames());
        assertFalse(properties.containsKey("palettes.bin"));
    }

    @Test
    void pipeSheetFinalRenderLineAndUsedColorsAreCoveredBySparseClaims() throws Exception {
        Path pipeSheet = temp.resolve("pipe.ggfs");
        assertCli("convert", "art", "--image", SOURCE.resolveSibling("pipe.png").toString(),
                "--sheet", SOURCE.resolveSibling("pipe-sheet.yaml").toString(),
                "--out", pipeSheet.toString());

        BakedSheetReader.BakedSheet sheet = BakedSheetReader.read(Files.readAllBytes(pipeSheet));
        BakedSheetReader.Palette palette = sheet.palette().orElseThrow();
        Set<Integer> finalRenderLines = new HashSet<>();
        sheet.frames().forEach(frame -> frame.mapping().pieces().forEach(piece ->
                finalRenderLines.add((palette.line() + piece.paletteIndex()) & 0x3)));

        Set<Integer> usedColorIndices = new HashSet<>();
        for (Pattern pattern : sheet.patterns()) {
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    int color = Byte.toUnsignedInt(pattern.getPixel(x, y));
                    if (color != 0) {
                        usedColorIndices.add(color);
                    }
                }
            }
        }

        JsonNode level = new ObjectMapper().readTree(SOURCE.resolve("level.json").toFile());
        Map<PaletteCell, Integer> claims = new HashMap<>();
        level.path("paletteClaims").forEach(claim -> claims.put(
                new PaletteCell(claim.path("line").asInt(), claim.path("color").asInt()),
                claim.path("sega").asInt()));

        assertEquals(Set.of(2), finalRenderLines,
                "paletteLine is a base; each piece adds its own palette index");
        assertEquals(Set.of(2, 3, 4, 5), usedColorIndices);
        Map<Integer, Integer> actualPipeWords = new HashMap<>();
        usedColorIndices.forEach(color -> actualPipeWords.put(color, palette.colors()[color]));
        assertEquals(Map.of(2, 0x0040, 3, 0x0060, 4, 0x0080, 5, 0x00A0),
                actualPipeWords);
        for (int line : finalRenderLines) {
            for (int color : usedColorIndices) {
                PaletteCell cell = new PaletteCell(line, color);
                assertTrue(claims.containsKey(cell),
                        () -> "missing sparse palette claim for rendered pipe cell " + cell);
                assertEquals(palette.colors()[color], claims.get(cell).intValue(),
                        () -> "claim must preserve the GGFS palette word for " + cell);
            }
        }
    }

    @Test
    void flappyLevelSourceConvertsCleanly() throws Exception {
        Path src = SOURCE;
        Path export = temp.resolve("export");
        Files.createDirectories(export);
        Files.copy(src.resolve("level.json"), export.resolve("level.json"));
        Properties props = new Properties();
        try (var in = Files.newInputStream(src.resolve("binary-assets.properties"))) {
            props.load(in);
        }
        for (String name : props.stringPropertyNames()) {
            Files.write(export.resolve(name), Base64.getDecoder().decode(props.getProperty(name)));
        }
        Path out = temp.resolve("out");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exit = GgfModCli.run(new String[] {
                "convert", "level", "--from-export", export.toString(), "--out", out.toString()},
                new PrintStream(bytes));
        assertEquals(0, exit, bytes.toString(StandardCharsets.UTF_8));
        assertTrue(Files.exists(out.resolve("level.json")));
    }

    private static void assertCli(String... arguments) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exit = GgfModCli.run(arguments, new PrintStream(bytes));
        assertEquals(0, exit, bytes.toString(StandardCharsets.UTF_8));
    }

    private record PaletteCell(int line, int color) {}
}
