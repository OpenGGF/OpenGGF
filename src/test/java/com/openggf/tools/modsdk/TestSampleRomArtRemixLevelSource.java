package com.openggf.tools.modsdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSampleRomArtRemixLevelSource {
    private static final Path SOURCE = Path.of(
            "src/test/resources/mods/sample-rom-art-remix-src/project/src/main/mod/level-source");
    private static final Set<String> ROOT_KEYS = Set.of(
            "formatVersion", "zoneName", "zoneIndex", "levelIndex", "blockGridSide",
            "width", "height", "bounds", "start", "music", "assets", "objects", "rings");
    private static final Set<String> ASSET_FILES = Set.of(
            "patterns.bin", "chunks.bin", "blocks.bin", "fg-map.bin", "solid-heights.bin",
            "solid-widths.bin", "solid-angles.bin", "collision-primary.bin",
            "collision-secondary.bin", "palettes.bin");

    @Test
    void levelUsesOnlyTheStrictV1RootShapeAndOneDisplayObject() throws Exception {
        JsonNode level = new ObjectMapper().readTree(SOURCE.resolve("level.json").toFile());
        Set<String> actualKeys = new java.util.LinkedHashSet<>();
        level.fieldNames().forEachRemaining(actualKeys::add);

        assertEquals(ROOT_KEYS, actualKeys);
        assertEquals(1, level.path("formatVersion").asInt());
        assertTrue(level.path("width").asInt() <= 3);
        assertEquals(2, level.path("height").asInt());
        assertEquals(1, level.path("objects").size());
        assertEquals("sample-rom-art-remix:tails-flight-art",
                level.path("objects").get(0).path("objectKey").asText());
        assertEquals(0, level.path("rings").size());
        assertFalse(level.has("game"));
        assertFalse(level.has("mapWidthPixels"));
    }

    @Test
    void binaryAssetsAreActualParserFilesEncodedAsBase64WithoutHashes() throws Exception {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(SOURCE.resolve("binary-assets.properties"))) {
            properties.load(input);
        }

        assertEquals(ASSET_FILES, properties.stringPropertyNames());
        for (String name : ASSET_FILES) {
            String encoded = properties.getProperty(name);
            assertFalse(name.toLowerCase(java.util.Locale.ROOT).contains("hash"));
            assertDoesNotThrow(() -> Base64.getDecoder().decode(encoded), name);
            assertTrue(Base64.getDecoder().decode(encoded).length > 0, name);
        }
    }
}
