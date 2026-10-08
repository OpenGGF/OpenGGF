package com.openggf.tools.challenge;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.RgbaImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestWorkerGameSessionContract {
    @TempDir Path directory;

    @Test
    void nativeRgbaRetainsOrientationAndAlphaRatherThanDoubleFlipping() {
        RgbaImage image = new RgbaImage(1, 2, new int[] {0x12345678, 0x9ABCDEF0});
        assertArrayEquals(new byte[] {0x34, 0x56, 0x78, 0x12,
                        (byte) 0xBC, (byte) 0xDE, (byte) 0xF0, (byte) 0x9A},
                WorkerGameSession.topDownRgba(image));
    }

    @Test
    void launchDoesNotInheritUserDebugDonorPlaybackOrIntroSkipping() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        try {
            config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
            config.setSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, "s2");
            config.setSessionOverride(SonicConfiguration.PLAYBACK_MOVIE_PATH, "untrusted.bk2");
            WorkerGameSession.configure(config, "s3k");
            assertFalse(config.getBoolean(SonicConfiguration.S3K_SKIP_INTROS));
            assertFalse(config.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED));
            assertFalse(config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED));
            assertFalse(config.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
            assertEquals("off", config.getString(SonicConfiguration.CROSS_GAME_SOURCE));
            assertEquals("", config.getString(SonicConfiguration.PLAYBACK_MOVIE_PATH));
            assertEquals("tails", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
            assertEquals("sonic", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            assertEquals("REALISTIC", config.getString(SonicConfiguration.LOAD_TIME_SIMULATION));
            assertEquals(320, config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
            assertEquals(224, config.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS));
            assertTrue(config.getBoolean(SonicConfiguration.AUDIO_ENABLED));
            WorkerGameSession.configure(config, "s2");
            assertEquals("", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        } finally {
            config.clearSessionOverrides();
            config.resetToDefaults();
        }
    }

    @Test
    void wrongSourceIsRejectedBeforeNativeBoot() throws Exception {
        Path wrong = directory.resolve("wrong.gen");
        Files.write(wrong, new byte[0x80000]);
        assertThrows(IOException.class, () -> WorkerGameSession.open("s1", wrong));
        assertThrows(IOException.class, () -> WorkerGameSession.validateRom("s2", wrong));
        assertThrows(IOException.class, () -> WorkerGameSession.validateRom("s1", Path.of("s1.gen")));
        assertThrows(IllegalArgumentException.class, () -> WorkerGameSession.validateRom("custom", wrong));
    }
}
