package com.openggf.tools;

import com.openggf.Engine;
import com.openggf.InputBindingFactory;
import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.game.GameId;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Real ROM/session/audio boot; the injected graphics lifecycle creates no GL context. */
@RequiresRom(SonicGame.SONIC_2)
class TestHeadlessGameBootAudioIdentity {
    private EngineContext services;
    private HeadlessGameBoot boot;

    @BeforeEach
    void configureNativeBoot() {
        services = EngineServices.current();
        var configuration = services.configuration();
        configuration.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        configuration.setConfigValue(SonicConfiguration.AUDIO_ENABLED, true);
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        boot = new HeadlessGameBoot(320, 224, services, new HeadlessGameBoot.NativeGlLifecycle() {
            @Override public void initialize(HeadlessGameBoot ignored) {
                EngineServices.configure(services);
                services.graphics().initHeadless();
            }
            @Override public void close(HeadlessGameBoot ignored) { }
        });
    }

    @AfterEach
    void cleanupNativeOwners() {
        try {
            if (boot != null) boot.close();
        } finally {
            if (services != null) services.audio().destroy();
        }
    }

    @Test
    void nativeBootOwnsItsAudioAndPublishesOnePacketAtTheOrdinaryOuterBoundary() throws Exception {
        assertFalse(boot.hasInstalledHeadlessAudioBackend());
        var loop = boot.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0);
        assertEquals(GameId.S2, SessionManager.getCurrentWorldSession().resolvedGameModule().getGameId());
        assertTrue(boot.hasInstalledHeadlessAudioBackend());
        var input = new InputHandler(InputBindingFactory.supplier(services.configuration()));
        loop.setInputHandler(input);
        assertFalse(input.hasLogicalOverride());
        assertFalse(loop.externalFrameOrInputOwnerActive());
        try (var capture = services.audio().beginLiveCaptureAudio(60)) {
            loop.stepPresentationFrame();
            Engine.presentOuterAudioFrame(loop, false, false);
            short[] pcm = new short[capture.maxStereoFramesPerPacket() * 2];
            int frames = capture.drainPresentationFrame(pcm);
            assertEquals(services.audio().outputSampleRate() / 60, frames);
            assertEquals(frames, capture.totalStereoFrames());
            assertEquals(frames, capture.clockSnapshot().totalSamplesProduced());
            assertTrue(boot.hasInstalledHeadlessAudioBackend(), "the capture leaves native audio ownership intact");
        }
        boot.close();
        assertFalse(boot.hasInstalledHeadlessAudioBackend(), "retirement invalidates the boot's audio identity");
    }

    @Test
    void replacingTheBackendWithTheSameImplementationDoesNotMatchOrReinstallItsOwner() throws Exception {
        boot.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0);
        assertTrue(boot.hasInstalledHeadlessAudioBackend());
        var replacement = new HeadlessSmpsAudioBackend(services.configuration(), services.profiler());
        services.audio().setBackend(replacement);
        assertFalse(boot.hasInstalledHeadlessAudioBackend());
        assertTrue(services.audio().hasInstalledBackend(replacement), "the query must leave the replacement installed");
        boot.close();
        assertFalse(boot.hasInstalledHeadlessAudioBackend());
    }

    @Test
    void audioDisabledBootDoesNotClaimAnAlreadyInstalledHeadlessBackend() throws Exception {
        var previous = new HeadlessSmpsAudioBackend(services.configuration(), services.profiler());
        services.audio().setBackend(previous);
        services.configuration().setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
        boot.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0);
        assertFalse(boot.hasInstalledHeadlessAudioBackend());
        assertTrue(services.audio().hasInstalledBackend(previous));
    }
}
