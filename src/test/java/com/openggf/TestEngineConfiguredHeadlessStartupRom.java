package com.openggf;

import com.openggf.audio.AudioBackend;
import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.audio.LWJGLAudioBackend;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.data.Rom;
import com.openggf.game.GameId;
import com.openggf.game.GameMode;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Real configured Engine startup through ROM detection, presentation and title entry; no GL/device. */
@RequiresRom(SonicGame.SONIC_2)
@Isolated
class TestEngineConfiguredHeadlessStartupRom {
    private EngineContext services;
    private AudioManager audio;
    private GraphicsManager graphics;
    private Rom rom;
    private Engine engine;

    @BeforeEach
    void configureRealRomAndHeadlessGraphics() {
        TestEnvironment.resetAll();
        ModSubsystem.clearProcess();
        EngineContext bootstrap = EngineServices.current();
        graphics = new GraphicsManager();
        graphics.initHeadless();
        audio = bootstrap.audio();
        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        services = new EngineContext(bootstrap.configuration(), graphics, audio,
                bootstrap.roms(), bootstrap.profiler(), bootstrap.debugOverlay(),
                bootstrap.playbackDebug(), bootstrap.romDetection(), bootstrap.crossGameFeatures());
        EngineServices.configure(services);
        var configuration = services.configuration();
        configuration.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        configuration.setConfigValue(SonicConfiguration.SHOW_LEGAL_DISCLAIMER_ON_STARTUP, false);
        configuration.setConfigValue(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP, false);
        configuration.setConfigValue(SonicConfiguration.TITLE_SCREEN_ON_STARTUP, true);
        configuration.setConfigValue(SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);
        configuration.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        configuration.setConfigValue(SonicConfiguration.DEFAULT_ROM, "s2");
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        rom = new Rom();
        assertTrue(rom.open(RomTestUtils.ensureSonic2RomAvailable().getAbsolutePath()));
        services.roms().setRom(rom);
    }

    @AfterEach
    void releaseOwnedRuntime() throws Exception {
        try {
            if (engine != null) engine.closeConfiguredHeadlessSession();
        } finally {
            try {
                SessionManager.clear();
                if (audio != null) {
                    audio.destroy();
                    audio.resetState();
                    audio.setBackend(new NullAudioBackend());
                }
                if (services != null) services.roms().setRom(null);
                if (rom != null) rom.close();
                if (graphics != null) graphics.cleanup();
            } finally {
                Engine.clearGlobalInstance();
                ModSubsystem.clearProcess();
                TestEnvironment.resetAll();
            }
        }
    }

    @Test
    void synthesisEnabledStartupRetainsTheSuppliedOfflineOwnerThroughRealTitleEntry() {
        assertRealConfiguredStartupRetainsOwner(true);
    }

    @Test
    void synthesisDisabledStartupRetiresInheritedOwnershipAndRetainsTheSuppliedSilentOwner() {
        assertRealConfiguredStartupRetainsOwner(false);
    }

    private void assertRealConfiguredStartupRetainsOwner(boolean synthesisEnabled) {
        services.configuration().setConfigValue(SonicConfiguration.AUDIO_ENABLED, synthesisEnabled);
        AudioBackend inherited = spy(new NullAudioBackend());
        audio.setBackend(inherited);
        audio.playSfx(GameSound.RING);
        assertFalse(audio.captureLogicalSnapshot().ringLeft(), "begin in a reused, dirty audio lifetime");
        assertTrue(audio.captureLogicalSnapshot().commandEntryCount() > 0);
        AudioBackend supplied = synthesisEnabled
                ? spy(new HeadlessSmpsAudioBackend(services.configuration(), services.profiler()))
                : spy(new NullAudioBackend());
        InputHandler input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        engine = new Engine(services);

        // A historical replacement fails at construction, before setBackendForLaunch
        // can initialize a physical device. This guard also makes a negative source
        // mutation safe to run in a reused Surefire JVM.
        try (var physicalBackends = mockConstruction(LWJGLAudioBackend.class,
                (backend, construction) -> fail("configured headless startup constructed a device backend"))) {
            engine.initializeConfiguredHeadlessSession(input, supplied);

            assertEquals(GameMode.TITLE_SCREEN, engine.getCurrentGameMode(),
                    "exercise initializeGame and its presentation boundary, without overriding either");
            assertEquals(GameId.S2, SessionManager.getCurrentWorldSession().resolvedGameModule().getGameId());
            assertSame(input, engine.getGameLoop().getInputHandler());
            assertSame(supplied, audio.getBackend());
            assertTrue(audio.hasInstalledBackend(supplied));
            assertTrue(physicalBackends.constructed().isEmpty());
            verify(inherited, times(1)).destroy();
            verify(supplied, never()).destroy();

            engine.closeConfiguredHeadlessSession();
            engine.closeConfiguredHeadlessSession();

            verify(inherited, times(1)).destroy();
            verify(supplied, times(1)).destroy();
            assertSame(supplied, audio.getBackend(), "cleanup retires the exact supplied owner");
            assertFalse(input.hasLogicalOverride());
            assertNull(engine.getGameLoop().getInputHandler());
            assertNull(SessionManager.getCurrentWorldSession());
            assertTrue(audio.captureLogicalSnapshot().ringLeft());
            assertEquals(0, audio.captureLogicalSnapshot().commandEntryCount());
        }
    }
}
