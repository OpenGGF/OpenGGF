package com.openggf.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TestAudioHardwareDefaults {

    @TempDir
    Path configDir;

    @Test
    void defaultsToReferenceChipBehavior() {
        SonicConfigurationService config =
                SonicConfigurationService.createStandalone(configDir);

        assertFalse(config.getBoolean(SonicConfiguration.DAC_INTERPOLATE));
        assertFalse(config.getBoolean(
                SonicConfiguration.PSG_NOISE_SHIFT_EVERY_TOGGLE));
    }
}
