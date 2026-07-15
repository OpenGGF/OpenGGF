package com.openggf.tools.modsdk;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
