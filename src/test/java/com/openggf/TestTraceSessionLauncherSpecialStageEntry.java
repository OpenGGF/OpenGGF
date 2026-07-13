package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.SpecialStageStartupPolicy;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class TestTraceSessionLauncherSpecialStageEntry {

    private SonicConfigurationService config;
    private TraceReplaySessionBootstrap.ConfigSnapshot originalConfig;

    @BeforeEach
    void captureOriginalConfig() {
        TestEnvironment.resetAll();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        config = SonicConfigurationService.getInstance();
        originalConfig = TraceReplaySessionBootstrap.snapshotGameplayConfig();
    }

    @AfterEach
    void restoreOriginalConfigAndVerify() {
        TraceReplaySessionBootstrap.restoreGameplayConfig(originalConfig);
        assertEquals(originalConfig.mainCharacterCode(),
                config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals(originalConfig.sidekickCharacterCode(),
                config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        assertEquals(originalConfig.crossGameFeaturesEnabled(),
                config.getConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
        assertEquals(originalConfig.displayAspect(),
                config.getConfigValue(SonicConfiguration.DISPLAY_ASPECT));
    }

    @Test
    void traceEntryRequestsAccurateStartupBeforeDisablingNativeLag() {
        GameLoop loop = mock(GameLoop.class);
        SpecialStageProvider provider = mock(SpecialStageProvider.class);

        TraceSessionLauncher.enterSpecialStageTrace(loop, provider, 4);

        InOrder order = inOrder(loop, provider);
        order.verify(loop).doEnterSpecialStage(provider, 4, true,
                SpecialStageStartupPolicy.TRACE_ACCURATE);
        order.verify(provider).setLagCompensation(0);
    }

    @Test
    void specialStageConfigurationUsesNativeViewportAndRecordedTeamThenRestoresAspect()
            throws Exception {
        config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9");
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
        config.resolveDisplayAspect();
        TraceReplaySessionBootstrap.ConfigSnapshot snapshot =
                TraceReplaySessionBootstrap.snapshotGameplayConfig();

        try {
            TraceMetadata metadata = TraceMetadata.load(
                    Path.of("src/test/resources/traces/s2/special_stage/metadata.json"));
            Method prepare = TraceSessionLauncher.class.getDeclaredMethod(
                    "prepareSpecialStageConfiguration", TraceMetadata.class);
            prepare.setAccessible(true);

            prepare.invoke(null, metadata);

            assertEquals("NATIVE_4_3", config.getString(SonicConfiguration.DISPLAY_ASPECT));
            assertEquals(320, config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
            assertEquals("sonic", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            assertEquals("tails", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
            assertEquals(false, config.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
        } finally {
            TraceReplaySessionBootstrap.restoreGameplayConfig(snapshot);
        }

        assertEquals("WIDE_16_9", config.getString(SonicConfiguration.DISPLAY_ASPECT));
        assertEquals(400, config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
        assertEquals("knuckles", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        assertEquals(true, config.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
    }
}
