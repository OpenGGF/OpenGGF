package com.openggf.configuration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaptureConfigDefaultsTest {

    @Test
    void captureDefaults() {
        SonicConfigurationService c = SonicConfigurationService.createStandalone();
        assertEquals("target/trace-videos", c.getString(SonicConfiguration.CAPTURE_OUTPUT_DIR));
        assertEquals(4, c.getInt(SonicConfiguration.CAPTURE_SCALE));
        assertEquals(60, c.getInt(SonicConfiguration.CAPTURE_FPS));
        assertEquals("ffv1", c.getString(SonicConfiguration.CAPTURE_CODEC));
    }
}
