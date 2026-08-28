package com.openggf.tools;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.TilemapGpuRenderer;
import com.openggf.level.ParallaxManager;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SingletonResetExtension.class)
class TraceCapturePresentationSetupTest {
    private SonicConfigurationService configuration;

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        configuration = SonicConfigurationService.getInstance();
        configuration.resetToDefaults();
        configuration.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        configuration.resolveDisplayAspect();
    }

    @AfterEach
    void tearDown() {
        configuration.resetToDefaults();
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 400, 528})
    void scopesPresentationOwnersAndRestoresConfiguration(int width) throws Exception {
        TraceCaptureDimensions dimensions = TraceCaptureDimensions.resolve(width, 2);

        try (TraceCapturePresentationSetup ignored =
                     TraceCapturePresentationSetup.open(configuration, dimensions)) {
            assertEquals(width, configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
            assertEquals(width, new Camera(configuration).getWidth());
            ParallaxManager parallax = new ParallaxManager();
            assertEquals((width + 15) / 16,
                    privateInt(parallax, "BG_VSCROLL_COLUMN_COUNT"));
            assertEquals((width + 15) / 16,
                    new TilemapGpuRenderer(width).getVScrollColumnCapacity());
        }

        assertEquals(320, configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
        assertEquals("NATIVE_4_3",
                configuration.getString(SonicConfiguration.DISPLAY_ASPECT));
    }

    @org.junit.jupiter.api.Test
    void temporarilyReleasesNativeTestModeOverrideAndRestoresIt() {
        configuration.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        configuration.resolveDisplayAspect();

        try (TraceCapturePresentationSetup ignored = TraceCapturePresentationSetup.open(
                configuration, TraceCaptureDimensions.resolve(400, 1))) {
            assertEquals(400, configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
            assertTrue(!configuration.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
        }

        assertTrue(configuration.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
        assertEquals(320, configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
    }

    @org.junit.jupiter.api.Test
    void snapshotsCallerStateBeforeTracePreparationAndRestoresItAfterPresentation() {
        configuration.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "ULTRA_21_9");
        configuration.resolveDisplayAspect();
        TraceCapturePresentationSetup.CallerState callerState =
                TraceCapturePresentationSetup.snapshot(configuration);

        var metadata = TraceFixtures.metadata("s3k", 0, 0);
        TraceReplaySessionBootstrap.prepareConfiguration(null, metadata);
        assertEquals("NATIVE_4_3",
                configuration.getString(SonicConfiguration.DISPLAY_ASPECT));

        try (TraceCapturePresentationSetup ignored = TraceCapturePresentationSetup.open(
                configuration, TraceCaptureDimensions.resolve(400, 1), callerState)) {
            assertEquals("WIDE_16_9",
                    configuration.getString(SonicConfiguration.DISPLAY_ASPECT));
            assertTrue(!configuration.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
        }

        assertEquals("ULTRA_21_9",
                configuration.getString(SonicConfiguration.DISPLAY_ASPECT));
        assertTrue(configuration.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
    }

    private static int privateInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
