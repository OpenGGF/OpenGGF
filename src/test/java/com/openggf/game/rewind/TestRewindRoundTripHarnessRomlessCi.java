package com.openggf.game.rewind;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestRewindRoundTripHarnessRomlessCi {
    private static final String FORCE_ROMLESS_CI_PROPERTY =
            "openggf.rewind.harness.forceRomless";

    @AfterEach
    void tearDown() {
        System.clearProperty(FORCE_ROMLESS_CI_PROPERTY);
        RomManager.getInstance().close();
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void romlessCiSurrogateDoesNotTryToOpenMissingResultsScreenRom() {
        System.setProperty(FORCE_ROMLESS_CI_PROPERTY, "true");
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.resetToDefaults();
        config.setConfigValue(SonicConfiguration.DEFAULT_ROM, "s2");
        config.setConfigValue(SonicConfiguration.SONIC_2_ROM, "__missing_ci_rom__.gen");
        RomManager.getInstance().close();

        Logger romLogger = Logger.getLogger(Rom.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        romLogger.addHandler(handler);
        try {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(
                    S3kResultsScreenObjectInstance.class.getName());

            assertInstanceOf(RoundTripSweepResult.Unprobed.class, result,
                    "a missing prerequisite must never count as an executed round-trip");
            assertTrue(records.stream().noneMatch(TestRewindRoundTripHarnessRomlessCi::isRomOpenFailure),
                    "romless CI surrogate must not attempt to open absent copyrighted ROM assets");
        } finally {
            romLogger.removeHandler(handler);
        }
    }

    @Test
    void configuredAbsoluteRomPathIsNotMistakenForRomlessCi(@TempDir Path temporary) throws Exception {
        // This check tests path resolution only, and never opens these non-ROM bytes.
        Path configuredFile = Files.writeString(temporary.resolve("configured-asset.bin"), "path fixture");
        String previousRom = System.getProperty("s3k.rom.path");
        String previousDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("s3k.rom.path", configuredFile.toString());
            System.setProperty("user.dir", temporary.toString());
            var lookup = RewindRoundTripHarness.class.getDeclaredMethod("defaultRomFilesUnavailable");
            lookup.setAccessible(true);
            assertFalse((boolean) lookup.invoke(null),
                    "the harness must use configured ROM paths, not root filenames alone");
        } finally {
            restoreProperty("s3k.rom.path", previousRom);
            restoreProperty("user.dir", previousDirectory);
        }
    }

    private static void restoreProperty(String key, String previous) {
        if (previous == null) System.clearProperty(key);
        else System.setProperty(key, previous);
    }

    private static boolean isRomOpenFailure(LogRecord record) {
        return record.getLevel().intValue() >= Level.SEVERE.intValue()
                && record.getMessage() != null
                && record.getMessage().startsWith("Failed to open ROM:");
    }
}
