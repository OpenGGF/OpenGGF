package com.openggf.widescreen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openggf.camera.DeadzoneGeometry;
import com.openggf.configuration.DeadzoneMode;
import com.openggf.configuration.DisplayWindowPolicy;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.sprites.managers.RightBoundary;
import org.junit.jupiter.api.Test;

/** Pins that the default (NATIVE_4_3) path reproduces every historical constant. */
class TestWidescreenNativeRegression {

    @Test
    void defaultConfigResolvesToNativeDimensions() {
        SonicConfigurationService cfg = SonicConfigurationService.createStandalone();
        cfg.resolveDisplayAspect();
        assertEquals(320, cfg.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
        assertEquals(224, cfg.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS));
        assertEquals(640, cfg.getInt(SonicConfiguration.SCREEN_WIDTH));
        assertEquals(448, cfg.getInt(SonicConfiguration.SCREEN_HEIGHT));
    }

    @Test
    void nativeCameraConstantsUnchanged() {
        for (DeadzoneMode mode : DeadzoneMode.values()) {
            assertEquals(144, DeadzoneGeometry.leftEdge(320, mode));
            // rightEdge(320) == 160 is both the scroll trigger and the focus-snap rest point.
            assertEquals(160, DeadzoneGeometry.rightEdge(320));
        }
    }

    @Test
    void nativeBoundaryConstantsUnchanged() {
        assertEquals(1000 + 0x128, RightBoundary.compute(1000, 320, 24, 64, true));
        assertEquals(1000 + 0x128 + 0x40, RightBoundary.compute(1000, 320, 24, 64, false));
    }

    @Test
    void nativeWindowPolicyUnchanged() {
        DisplayWindowPolicy.Resolved r =
                DisplayWindowPolicy.resolve(WidescreenAspect.NATIVE_4_3, true, 640, 448);
        assertEquals(320, r.pixelWidth());
        assertEquals(640, r.windowWidth());
        assertEquals(448, r.windowHeight());
    }
}
