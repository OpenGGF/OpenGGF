package com.openggf.testmode;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceRenderVisibilityTest {

    @BeforeEach
    @AfterEach
    void resetConfig() {
        // These tests mutate the config singleton; reset before and after so
        // neither dev environment nor sibling tests leak state across runs.
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void readsAllThreeFlagsIndependentlyFromConfig() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.TRACE_SHOW_DESYNC_GHOSTS, true);
        config.setConfigValue(SonicConfiguration.TRACE_SHOW_GAME_HUD, false);
        config.setConfigValue(SonicConfiguration.TRACE_SHOW_DEBUG_HUD, true);

        TraceRenderVisibility vis = TraceRenderVisibility.fromConfig(config);
        assertTrue(vis.showGhosts());
        assertFalse(vis.showGameHud());
        assertTrue(vis.showDebugHud());
    }

    @Test
    void reflectsFlippedFlags() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.TRACE_SHOW_DESYNC_GHOSTS, false);
        config.setConfigValue(SonicConfiguration.TRACE_SHOW_GAME_HUD, true);
        config.setConfigValue(SonicConfiguration.TRACE_SHOW_DEBUG_HUD, false);

        TraceRenderVisibility vis = TraceRenderVisibility.fromConfig(config);
        assertFalse(vis.showGhosts());
        assertTrue(vis.showGameHud());
        assertFalse(vis.showDebugHud());
    }
}
