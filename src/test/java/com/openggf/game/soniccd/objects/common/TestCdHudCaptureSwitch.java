package com.openggf.game.soniccd.objects.common;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sonic CD's HUD is drawn by its own objects ({@link CdHud}, and the HUD's {@link CdTimeIcon}); a capture's
 * {@code --no-hud} pass ({@code debug.traceRender.showGameHud} false) leaves them undrawn, as the shared HUD is.
 */
class TestCdHudCaptureSwitch {
    private final SonicConfigurationService config = SonicConfigurationService.getInstance();

    @AfterEach
    void restore() {
        config.resetToDefaults();
    }

    @Test
    void hudObjectsAreDrawnUnlessTheCaptureLeavesTheGameHudOut() {
        config.resetToDefaults();
        ObjectServices services = new StubObjectServices() {
            @Override
            public SonicConfigurationService configuration() {
                return config;
            }
        };
        assertTrue(CdHud.shown(services), "the switch defaults to drawing the HUD");
        config.setConfigValue(SonicConfiguration.TRACE_SHOW_GAME_HUD, false);
        assertFalse(CdHud.shown(services), "--no-hud leaves the HUD objects out");
        config.setConfigValue(SonicConfiguration.TRACE_SHOW_GAME_HUD, true);
        assertTrue(CdHud.shown(services));
        assertTrue(CdHud.shown(new StubObjectServices()), "no configuration service: drawn");
    }
}
