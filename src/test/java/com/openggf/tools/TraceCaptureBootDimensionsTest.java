package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.tests.FullReset;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

@FullReset
@ExtendWith(SingletonResetExtension.class)
class TraceCaptureBootDimensionsTest {

    @ParameterizedTest
    @ValueSource(ints = {400, 528})
    void realBootKeepsNativeGameplayCameraAndWidePresentationCaches(int width)
            throws Exception {
        File rom = RomTestUtils.ensureSonic3kRomAvailable();
        Assumptions.assumeTrue(rom != null, "Sonic 3&K ROM is required");
        TraceCaptureDimensions dimensions = TraceCaptureDimensions.resolve(width, 1);
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        configuration.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        configuration.setConfigValue(
                SonicConfiguration.DISPLAY_ASPECT, dimensions.aspect().name());
        configuration.resolveDisplayAspect();

        try (HeadlessGameBoot boot = new HeadlessGameBoot(
                dimensions.physicalWidth(), dimensions.physicalHeight(),
                dimensions.logicalWidth(), dimensions.logicalHeight(),
                TraceCaptureDimensions.NATIVE_WIDTH,
                TraceCaptureDimensions.LOGICAL_HEIGHT)) {
            boot.boot(rom.toPath(), 0, 0, HardwareReadinessAdmissionPolicy.LIVE);

            var mode = SessionManager.getCurrentGameplayMode();
            assertEquals(320, mode.getCamera().getWidth(),
                    "trace gameplay camera authority must remain native");
            assertEquals(width, privateInt(mode.getLevelManager(), "cachedScreenWidth"),
                    "level rendering cache must use presentation width");
            assertEquals((width + 15) / 16,
                    privateInt(mode.getParallaxManager(), "BG_VSCROLL_COLUMN_COUNT"),
                    "parallax capacity must use presentation width");
            assertEquals((width + 15) / 16,
                    GameServices.graphics().getTilemapGpuRenderer()
                            .getVScrollColumnCapacity(),
                    "tilemap GPU capacity must use presentation width");
        }
    }

    private static int privateInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
