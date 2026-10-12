package com.openggf.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Isolated
class TestRomTestUtilsPaths {
    @TempDir Path temporary;

    @Test void legacySonicTwoCallAcceptsTheGameSpecificOriginalPath() throws Exception {
        Path original = temporary.resolve("Original Sonic 2 (REV01) [!].gen");
        Files.write(original, new byte[] {1});
        String legacy = System.getProperty("sonic.rom.path");
        String game = System.getProperty("sonic2.rom.path");
        try {
            System.clearProperty("sonic.rom.path");
            System.setProperty("sonic2.rom.path", original.toString());
            assertEquals(original.toFile(), RomTestUtils.ensureRomAvailable());
            assertEquals(original.toFile(), RomTestUtils.ensureSonic2RomAvailable());
        } finally {
            restore("sonic.rom.path", legacy);
            restore("sonic2.rom.path", game);
        }
    }

    @Test void explicitLegacyPathKeepsItsExistingPrecedence() throws Exception {
        Path original = temporary.resolve("original.gen");
        Path legacyFile = temporary.resolve("legacy-selection.gen");
        Files.write(original, new byte[] {1});
        Files.write(legacyFile, new byte[] {2});
        String legacy = System.getProperty("sonic.rom.path");
        String game = System.getProperty("sonic2.rom.path");
        try {
            System.setProperty("sonic.rom.path", legacyFile.toString());
            System.setProperty("sonic2.rom.path", original.toString());
            assertEquals(legacyFile.toFile(), RomTestUtils.ensureRomAvailable());
            assertEquals(original.toFile(), RomTestUtils.ensureSonic2RomAvailable());
        } finally {
            restore("sonic.rom.path", legacy);
            restore("sonic2.rom.path", game);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }
}
