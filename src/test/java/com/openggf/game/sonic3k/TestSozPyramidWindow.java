package com.openggf.game.sonic3k;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.scroll.SwScrlSoz;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozPyramidWindow {
    @AfterEach void resetConfiguration() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 400, 512, 528, 640, 800})
    void pyramidWindowCoversViewportAndRestoresNormalDesertPeriod(int width) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        com.openggf.game.session.SessionManager.clear();
        com.openggf.tests.TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8, 0).build();
        assertEquals(width, fixture.camera().getWidth());
        var state = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var handler = new SwScrlSoz(GameServices.rom().getRom());
        handler.init(0, 0x4310, 0x960);
        assertEquals(512, handler.getBgPeriodWidth());
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        state.events().backgroundRoutine(8);
        int period = handler.getBgPeriodWidth();
        assertEquals(0, period % 16);
        assertTrue(period >= width + 16, "allow aligned source base and shimmer at the right edge");
        if (width <= 400) assertEquals(512, period, "native window is unchanged");
        int[] scroll = new int[224];
        for (int alignment = 0; alignment < 16; alignment++) {
            fixture.camera().setXCopy((short) (0x4310 + alignment));
            fixture.camera().setYCopy((short) 0x960);
            handler.update(scroll, 0x4310 + alignment, 0x960, 32, 0);
            int base = Math.floorDiv(handler.getBgCameraX(), 16) * 16;
            for (int row : scroll) {
                int sourceLeft = -(short) row;
                assertTrue(sourceLeft >= base, "shimmer must retain its preceding source pixel");
                assertTrue(sourceLeft + width <= base + period, "right edge must remain resident");
            }
        }
        registry.restore(before);
        assertEquals(512, handler.getBgPeriodWidth(), "period follows restored event state");
    }
}
