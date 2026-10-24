package com.openggf.tools;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.InputBindingFactory;
import com.openggf.audio.AudioManager;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.data.RomGame;
import com.openggf.data.RomManager;
import com.openggf.game.GameModule;
import com.openggf.game.RomDetectionService;
import com.openggf.game.recording.RecordingLaunchContext;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.constants.Sonic2AudioConstants;
import com.openggf.tests.RomCatalogueFixture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real recording restarts; guard loader ownership before eager SFX loading can catch a fault. */
@RequiresRom(SonicGame.SONIC_2)
class TestRecordingRestartAudioOwnership {
    private EngineContext previous;
    private EngineContext services;
    private HeadlessGameBoot boot;
    private RomCatalogueFixture catalogue;
    private RomManager manager;
    private final Map<Rom, Binding> bindings = new IdentityHashMap<>();
    private final Map<SmpsLoader, Rom> loaderOwners = new IdentityHashMap<>();
    private final List<Rom> audioSourcesAtClose = new ArrayList<>();
    private boolean observeRestart;
    private boolean failReopen;
    private int ownershipViolations;
    private final IOException reopenFailure = new IOException("controlled recording reopen failure");

    @BeforeEach
    void prepareOwnedRealBoot() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        previous = EngineServices.current();
        var configuration = previous.configuration();
        configuration.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        configuration.setConfigValue(SonicConfiguration.AUDIO_ENABLED, true);
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");

        // This manager owns only this boot's readers, never RomCache's borrowed reader.
        var constructor = RomManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        manager = spy(constructor.newInstance());
        doAnswer(invocation -> {
            if (observeRestart) audioSourcesAtClose.add(basePart(services.audio(), "rom"));
            return invocation.callRealMethod();
        }).when(manager).close();
        doAnswer(invocation -> {
            if (failReopen) throw reopenFailure;
            return invocation.callRealMethod();
        }).when(manager).getRom();

        RomDetectionService detection = spy(previous.romDetection());
        doAnswer(invocation -> {
            Rom rom = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            Optional<GameModule> detected = (Optional<GameModule>) invocation.callRealMethod();
            if (detected.isEmpty()) return detected;
            Binding binding = new Binding(rom);
            bindings.put(rom, binding);
            GameModule module = spy(detected.orElseThrow());
            doReturn(binding.profile).when(module).getAudioProfile();
            return Optional.of(module);
        }).when(detection).detectAndCreateModule(any(Rom.class));

        services = new EngineContext(configuration, previous.graphics(), previous.audio(), manager,
                previous.profiler(), previous.debugOverlay(), previous.playbackDebug(), detection,
                previous.crossGameFeatures());
        var image = RomTestUtils.ensureSonic2RomAvailable();
        assertNotNull(image, "the actual recording-restart fixture requires S2");
        catalogue = RomCatalogueFixture.bind(configuration, manager, Map.of(RomGame.S2, image.toPath()));
        services.audio().resetState();
        boot = new HeadlessGameBoot(320, 224, services, new HeadlessGameBoot.NativeGlLifecycle() {
            @Override public void initialize(HeadlessGameBoot ignored) {
                EngineServices.configure(services);
                services.graphics().initHeadless();
            }
            @Override public void close(HeadlessGameBoot ignored) { }
        });
    }

    @AfterEach
    void closeOnlyOwnedReadersAndPresentation() throws Exception {
        observeRestart = false;
        failReopen = false;
        try {
            if (boot != null) boot.close();
        } finally {
            try {
                if (services != null) services.audio().setRom(null);
                if (manager != null) manager.close();
            } finally {
                try {
                    if (services != null) services.audio().destroy();
                    if (catalogue != null) catalogue.close();
                } finally {
                    if (previous != null) EngineServices.configure(previous);
                }
            }
        }
    }

    @Test
    void realRestartRetiresTheOldSourceBeforeCloseAndCarriesLiveAudioOntoTheNewReader() throws Exception {
        GameLoop loop = start();
        AudioManager audio = services.audio();
        Rom retired = manager.getRom();
        Binding old = bindings.get(retired);
        assertNotNull(old);
        audio.playMusic(Sonic2AudioConstants.MUS_EMERALD_HILL);

        try (var capture = audio.beginLiveCaptureAudio(60)) {
            assertTrue(present(loop, capture, 12) > 0, "the original real source is audible");
            long framesBefore = capture.totalStereoFrames();
            observeRestart = true;
            try {
                loop.restartFromRecordingLaunchContext(launch());
            } finally {
                observeRestart = false;
            }

            Rom replacement = manager.getRom();
            assertNotSame(retired, replacement);
            assertFalse(retired.isOpen());
            assertTrue(replacement.isOpen());
            assertEquals(1, audioSourcesAtClose.size());
            assertNull(audioSourcesAtClose.getFirst(), "detach the base source before retiring its reader");
            Binding current = bindings.get(replacement);
            assertNotNull(current);
            assertNotSame(old.profile, current.profile, "both profiles are real S2 profiles with distinct owners");
            assertTrue(current.loaderConstructions > 0);
            assertSame(replacement, basePart(audio, "rom"));
            assertSame(replacement, loaderOwners.get(basePart(audio, "loader")));
            assertTrue(boot.hasInstalledHeadlessAudioBackend());

            audio.playMusic(Sonic2AudioConstants.MUS_EMERALD_HILL);
            assertTrue(audio.playSfx(Sonic2AudioConstants.SFX_RING_RIGHT));
            assertTrue(present(loop, capture, 12) > 0, "restarted music/SFX produce real nonzero PCM");
            assertTrue(current.musicLoads > 0);
            assertTrue(current.sfxLoads > 0, "the real restarted source supplies the SFX");
            assertTrue(capture.totalStereoFrames() > framesBefore, "the same live capture survives replacement");
            assertNotNull(audio.captureLogicalSnapshot().presentation().activeMusic());
            assertEquals(Sonic2AudioConstants.MUS_EMERALD_HILL,
                    audio.captureLogicalSnapshot().presentation().activeMusic().musicId());
            assertEquals(0, ownershipViolations);
        }
    }

    @Test
    void failedReopenLeavesAudioDetachedInsteadOfNamingTheClosedReader() throws Exception {
        GameLoop loop = start();
        AudioManager audio = services.audio();
        Rom retired = manager.getRom();
        var originalProfile = audio.getAudioProfile();
        failReopen = true;
        observeRestart = true;
        try {
            var failure = assertThrows(RuntimeException.class,
                    () -> loop.restartFromRecordingLaunchContext(launch()));
            assertSame(reopenFailure, failure.getCause());
        } finally {
            observeRestart = false;
            failReopen = false;
        }

        assertFalse(retired.isOpen());
        assertEquals(1, audioSourcesAtClose.size());
        assertNull(audioSourcesAtClose.getFirst());
        assertNull(basePart(audio, "rom"));
        assertNull(basePart(audio, "loader"));
        assertSame(originalProfile, audio.getAudioProfile(), "null-ROM detachment retains the profile contract");
        assertEquals(0, ownershipViolations);
        // This control makes no claim that the failed gameplay load rolls back.
    }

    private GameLoop start() throws Exception {
        var image = RomTestUtils.ensureSonic2RomAvailable();
        assertNotNull(image);
        GameLoop loop = boot.boot(image.toPath(), 0, 0);
        loop.setInputHandler(new InputHandler(InputBindingFactory.supplier(services.configuration())));
        return loop;
    }

    private static RecordingLaunchContext launch() {
        return new RecordingLaunchContext("s2", 0, 0, "sonic", List.of(), false, "owned-audio-regression");
    }

    private static long present(GameLoop loop, LiveCaptureAudioHandle capture, int count) {
        short[] pcm = new short[capture.maxStereoFramesPerPacket() * 2];
        long energy = 0;
        for (int frame = 0; frame < count; frame++) {
            loop.stepPresentationFrame();
            Engine.presentOuterAudioFrame(loop, false, false);
            int frames = capture.drainPresentationFrame(pcm);
            assertTrue(frames > 0, "the ordinary outer owner publishes an audio packet");
            for (int sample = 0; sample < frames * 2; sample++) energy += (long) pcm[sample] * pcm[sample];
        }
        return energy;
    }

    @SuppressWarnings("unchecked")
    private static <T> T basePart(AudioManager audio, String name) throws Exception {
        Field field = AudioManager.class.getDeclaredField("baseAudioSource");
        field.setAccessible(true);
        Object source = field.get(audio);
        var accessor = source.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return (T) accessor.invoke(source);
    }

    private void requireReader(Rom expected, Rom actual) {
        if (expected != actual || !expected.isOpen()) {
            ownershipViolations++;
            // AssertionError is deliberately outside the eager S2 loader's caught exception set.
            throw new AssertionError("Audio loader attempted a wrong or retired ROM reader");
        }
    }

    private final class Binding {
        private final Sonic2AudioProfile profile;
        private int loaderConstructions;
        private int musicLoads;
        private int sfxLoads;

        private Binding(Rom expected) {
            profile = spy(new Sonic2AudioProfile());
            doAnswer(invocation -> {
                requireReader(expected, invocation.getArgument(0));
                loaderConstructions++;
                SmpsLoader loader = spy((SmpsLoader) invocation.callRealMethod());
                Answer<Object> reader = access -> {
                    requireReader(expected, expected);
                    return access.callRealMethod();
                };
                doAnswer(access -> { musicLoads++; return reader.answer(access); })
                        .when(loader).loadMusic(anyInt());
                doAnswer(access -> { musicLoads++; return reader.answer(access); })
                        .when(loader).loadMusicWithReadiness(anyInt());
                doAnswer(access -> { sfxLoads++; return reader.answer(access); })
                        .when(loader).loadSfx(anyInt());
                doAnswer(access -> { sfxLoads++; return reader.answer(access); })
                        .when(loader).loadSfx(anyString());
                doAnswer(reader).when(loader).loadDacData();
                doAnswer(reader).when(loader).findMusicOffset(anyInt());
                loaderOwners.put(loader, expected);
                return loader;
            }).when(profile).createSmpsLoader(any(Rom.class));
        }
    }
}
