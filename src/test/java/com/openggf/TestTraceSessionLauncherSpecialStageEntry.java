package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.SpecialStageStartupPolicy;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class TestTraceSessionLauncherSpecialStageEntry {

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
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SonicConfigurationService config = SonicConfigurationService.getInstance();
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
