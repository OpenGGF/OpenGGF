package com.openggf.tools;

import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-free enforcement of the audio boundary used by every native headless boot. */
class TestHeadlessAudioOwnership {
    private EngineContext services;
    private HeadlessGameBoot boot;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        services = EngineServices.current();
        boot = new HeadlessGameBoot(320, 224, services, new HeadlessGameBoot.NativeGlLifecycle() {
            @Override public void initialize(HeadlessGameBoot ignored) { }
            @Override public void close(HeadlessGameBoot ignored) { }
        });
    }

    @AfterEach
    void tearDown() {
        try {
            if (boot != null) boot.close();
        } finally {
            TestEnvironment.resetAll();
        }
    }

    @Test
    void silentBootRetiresInheritedBackendInsteadOfTrustingTheConfigurationFlag() {
        var inherited = new RetirementBackend();
        services.audio().setBackend(inherited);
        services.configuration().setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);

        boot.installAudioBackend();

        assertEquals(1, inherited.destroyCalls, "headless boot must retire inherited device ownership");
        assertEquals(NullAudioBackend.class, services.audio().getBackend().getClass());
        assertFalse(boot.hasInstalledHeadlessAudioBackend());
    }

    @Test
    void synthesisUsesAnOfflineOwnerAndDisablingItRetiresThatOwner() {
        var inherited = new RetirementBackend();
        services.audio().setBackend(inherited);
        services.configuration().setConfigValue(SonicConfiguration.AUDIO_ENABLED, true);

        boot.installAudioBackend();

        assertEquals(1, inherited.destroyCalls);
        assertInstanceOf(HeadlessSmpsAudioBackend.class, services.audio().getBackend());
        assertTrue(boot.hasInstalledHeadlessAudioBackend());
        services.configuration().setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);

        boot.installAudioBackend();

        assertEquals(NullAudioBackend.class, services.audio().getBackend().getClass());
        assertFalse(boot.hasInstalledHeadlessAudioBackend());
    }

    private static final class RetirementBackend extends NullAudioBackend {
        private int destroyCalls;

        @Override public void destroy() {
            destroyCalls++;
        }
    }
}
