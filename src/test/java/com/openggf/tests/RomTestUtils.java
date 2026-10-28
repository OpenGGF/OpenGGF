package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;

import java.io.File;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

public final class RomTestUtils {
    private RomTestUtils() {
    }

    /**
     * Gives sparse public-read fixtures an open, immutable UNKNOWN identity.
     * Reads still delegate to the supplied fixture, preserving mutable table
     * aliases and intentional IO failures. This is not an asset byte-reader.
     */
    public static Rom openSparseReadFixture(Rom reads) throws IOException {
        Rom rom = spy(Rom.fromReader(RomByteReader.fromBytes(new byte[0]), "sparse synthetic reads"));
        doAnswer(call -> reads.readBytes(call.<Long>getArgument(0), call.<Integer>getArgument(1)))
                .when(rom).readBytes(anyLong(), anyInt());
        doAnswer(call -> reads.readByte(call.<Long>getArgument(0)))
                .when(rom).readByte(anyLong());
        doAnswer(call -> reads.read16BitAddr(call.<Long>getArgument(0)))
                .when(rom).read16BitAddr(anyLong());
        return rom;
    }

    // Sonic 2 (default / backward-compatible)
    private static final String ROM_PATH_PROPERTY = "sonic.rom.path";
    private static final String ROM_PATH_ENV = "SONIC_ROM_PATH";

    // Per-game ROM filenames
    private static final String S1_ROM_FILENAME = "s1.gen";
    private static final String S2_ROM_FILENAME = "s2.gen";
    private static final String S3K_ROM_FILENAME = "s3k.gen";

    // Per-game system properties
    private static final String S1_ROM_PATH_PROPERTY = "sonic1.rom.path";
    private static final String S2_ROM_PATH_PROPERTY = "sonic2.rom.path";
    private static final String S3K_ROM_PATH_PROPERTY = "s3k.rom.path";

    // Per-game env vars
    private static final String S1_ROM_PATH_ENV = "SONIC_1_ROM_PATH";
    private static final String S2_ROM_PATH_ENV = "SONIC_2_ROM_PATH";
    private static final String S3K_ROM_PATH_ENV = "SONIC_3K_ROM_PATH";

    /**
     * Ensures the Sonic 2 ROM is available (backward-compatible).
     * Explicit legacy property/environment settings retain precedence; otherwise
     * use the same game-specific path/configuration lookup as current fixtures.
     * Returns the ROM file, or null if not found locally.
     */
    public static File ensureRomAvailable() {
        File legacy = findGameRomOrNull(ROM_PATH_PROPERTY, ROM_PATH_ENV, null, null);
        return legacy != null ? legacy : ensureSonic2RomAvailable();
    }

    /**
     * Ensures the Sonic 1 ROM is available.
     * Lookup order: system property, env var, config value, default filename.
     * No auto-download; returns null-safe File that must exist.
     */
    public static File ensureSonic1RomAvailable() {
        return findGameRomOrNull(S1_ROM_PATH_PROPERTY, S1_ROM_PATH_ENV,
                SonicConfiguration.SONIC_1_ROM, S1_ROM_FILENAME);
    }

    /**
     * Ensures the Sonic 2 ROM is available (explicit game-specific variant).
     * Lookup order: system property, env var, config value, default filename.
     * Returns the ROM file, or null if not found locally.
     */
    public static File ensureSonic2RomAvailable() {
        return findGameRomOrNull(S2_ROM_PATH_PROPERTY, S2_ROM_PATH_ENV,
                SonicConfiguration.SONIC_2_ROM, S2_ROM_FILENAME);
    }

    /**
     * Ensures the Sonic 3&K ROM is available.
     * Lookup order: system property, env var, config value, default filename.
     * No auto-download; returns null-safe File that must exist.
     */
    public static File ensureSonic3kRomAvailable() {
        return findGameRomOrNull(S3K_ROM_PATH_PROPERTY, S3K_ROM_PATH_ENV,
                SonicConfiguration.SONIC_3K_ROM, S3K_ROM_FILENAME);
    }

    /**
     * Tries to find a game-specific ROM, returning null if not found.
     */
    private static File findGameRomOrNull(String sysProp, String envVar,
                                           SonicConfiguration configKey, String defaultFilename) {
        // 1. System property
        String path = System.getProperty(sysProp);
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) return f;
        }

        // 2. Environment variable
        path = System.getenv(envVar);
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) return f;
        }

        // 3. Config value from SonicConfigurationService
        try {
            String configValue = configKey == null ? null
                    : SonicConfigurationService.getInstance().getString(configKey);
            if (configValue != null && !configValue.isEmpty()) {
                File f = new File(configValue);
                if (f.exists()) return f;
            }
        } catch (Exception ignored) {
            // Config service may not be available in all test contexts
        }

        // 4. Default filename in working directory
        if (defaultFilename == null) return null;
        File f = new File(defaultFilename);
        return f.exists() ? f : null;
    }

}

