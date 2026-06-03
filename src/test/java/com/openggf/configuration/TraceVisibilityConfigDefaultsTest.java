package com.openggf.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceVisibilityConfigDefaultsTest {

    @BeforeEach
    void resetConfig() {
        // The config service is a singleton that loads user/local config.json;
        // reset to defaults so this test is independent of dev environment and
        // of any other test that mutated config values.
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void traceVisibilityDefaults() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        assertTrue(config.getBoolean(SonicConfiguration.TRACE_SHOW_DESYNC_GHOSTS),
                "ghosts default on");
        assertTrue(config.getBoolean(SonicConfiguration.TRACE_SHOW_GAME_HUD),
                "game HUD default on");
        assertFalse(config.getBoolean(SonicConfiguration.TRACE_SHOW_DEBUG_HUD),
                "debug HUD default off");
    }
}
