package com.openggf.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.configuration.SonicConfiguration.*;

class TestDisplaySettingsTrial {
    @TempDir Path directory;

    @Test void previewExpiresWithoutMenuUpdatesOrPersistingCandidate() {
        var config = SonicConfigurationService.createStandalone(directory);
        var clock = new AtomicLong();
        var trial = new DisplaySettingsTrial(config, clock::get);
        trial.begin("FULLSCREEN");
        assertEquals("FULLSCREEN", trial.windowMode());
        assertEquals("WINDOWED", config.getString(DISPLAY_WINDOW_MODE));
        assertEquals("WINDOWED", SonicConfigurationService.createStandalone(directory).getString(DISPLAY_WINDOW_MODE));
        assertEquals(15, trial.secondsRemaining());
        clock.set(14_999_999_999L);
        assertTrue(trial.active());
        clock.incrementAndGet();
        assertEquals("WINDOWED", trial.windowMode());
        assertFalse(trial.active());
    }

    @Test void cancellationPreservesExistingModeAndUnrelatedSessionOverrides() {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setSessionOverride(DISPLAY_ASPECT, "WIDE_16_9");
        config.setConfigValue(DISPLAY_WINDOW_MODE, "BORDERLESS");
        var trial = config.displaySettingsTrial();
        trial.begin("WINDOWED");
        trial.cancel();
        assertEquals("BORDERLESS", trial.windowMode());
        assertTrue(config.hasSessionOverride(DISPLAY_ASPECT));
        assertThrows(IllegalArgumentException.class, () -> trial.begin("INVALID"));
    }
}
