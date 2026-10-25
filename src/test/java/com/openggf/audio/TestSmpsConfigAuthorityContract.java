package com.openggf.audio;

import com.openggf.audio.driver.SmpsRequestAdmissionPolicy;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsConfigBinding;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.smps.Sonic3kCoordFlagHandler;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSfxData;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Pre-injection compatibility characterization at production baseline 4eb8e8e1 (2026-10-24). */
class TestSmpsConfigAuthorityContract {
    static final SmpsSequencerConfig S3K = Sonic3kSmpsSequencerConfig.CONFIG;

    @Test
    void oldConstructorsAreLazyAndKeepConfigSinkRateValidationAndNullableProfiler() {
        try (var scope = new Scope()) {
            scope.singleton(null);
            scope.root(null);
            var config = configuration();
            assertEquals("configService", assertThrows(NullPointerException.class,
                    () -> new HeadlessSmpsAudioBackend(null, null, 0, null)).getMessage());
            assertEquals("musicRestoreSink", assertThrows(NullPointerException.class,
                    () -> new HeadlessSmpsAudioBackend(config, null, 0, null)).getMessage());
            assertEquals("sampleRate must be positive", assertThrows(IllegalArgumentException.class,
                    () -> new HeadlessSmpsAudioBackend(config, null, 0, () -> {})).getMessage());
            assertEquals("configService", assertThrows(NullPointerException.class,
                    () -> new LWJGLAudioBackend(null, null, null)).getMessage());
            assertEquals("musicRestoreSink", assertThrows(NullPointerException.class,
                    () -> new LWJGLAudioBackend(config, null, null)).getMessage());
            scope.backends.add(new HeadlessSmpsAudioBackend(config, null, 1, () -> {}));
            scope.backends.add(new HeadlessSmpsAudioBackend(config, null, () -> {}));
            scope.backends.add(new LWJGLAudioBackend(config, null, () -> {}));
            assertNull(scope.singleton(), "construction must not realize the legacy config authority");
            assertNull(scope.root(), "construction must not realize the configured service root");
            // No device initialization or DAC data is needed to construct either backend.
        }
    }

    @Test
    void confinedFixtureRestoresExactNullableAndPopulatedGlobalsOnExceptionalExit() {
        try (var outer = new Scope()) {
            for (boolean populated : new boolean[] {false, true}) {
                AudioManager incoming = populated ? outer.manager(null) : null;
                outer.singleton(incoming);
                EngineContext root = populated ? outer.configureRoot(incoming) : null;
                outer.root(root);
                var failure = new IllegalStateException("fixture body");
                assertSame(failure, assertThrows(IllegalStateException.class, () -> {
                    try (var inner = new Scope()) {
                        inner.singleton(inner.manager(new Sonic3kAudioProfile()));
                        inner.configureRoot(inner.manager(null));
                        throw failure;
                    }
                }));
                assertSame(incoming, outer.singleton());
                assertSame(root, outer.root());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"music", "mixed", "standalone"})
    void eachBindingSelectsCurrentSingletonDespiteReceivingManagerRootAndRestoreSink(String route) {
        try (var scope = new Scope()) {
            var firstProfile = new RecordingProfile();
            var secondProfile = new RecordingProfile();
            AudioManager firstS = scope.manager(firstProfile);
            AudioManager secondS = scope.manager(secondProfile);
            AudioManager receivingA = scope.manager(new Sonic3kAudioProfile());
            AudioManager rootB = scope.manager(new Sonic3kAudioProfile());
            scope.configureRoot(rootB);
            scope.singleton(null);
            var restores = new AtomicInteger();
            var backend = scope.backend(restores::incrementAndGet);
            receivingA.setBackend(backend);
            backend.setAudioProfile(new Sonic3kAudioProfile());
            assertNull(scope.singleton(), "installation does not choose config authority");
            scope.singleton(firstS);
            scope.configureRoot(rootB);
            SmpsSequencer first = source(backend, route, incrementProgram(route), S3K);
            render(backend, route);
            assertEquals(3, firstProfile.owner.state().spindashRevCounter());
            assertEquals(3, fm(first).keyOffset, "E9 ran through the backend-created source");
            assertSame(firstProfile.owner.handlerFor("s3k"), first.getConfig().getCoordFlagHandler());
            assertNotSame(S3K.getCoordFlagHandler(), first.getConfig().getCoordFlagHandler());
            assertEquals(0, receivingA.commandTimeline().entryCount());
            var rootBefore = rootB.captureLogicalSnapshot();
            scope.configureRoot(scope.manager(new Sonic3kAudioProfile()));
            SmpsSequencer sameS = source(backend, route, incrementProgram(route), S3K);
            render(backend, route);
            assertSame(first.getConfig().getCoordFlagHandler(), sameS.getConfig().getCoordFlagHandler());
            assertEquals(6, firstProfile.owner.state().spindashRevCounter());
            assertEquals(12, fm(sameS).keyOffset);
            scope.singleton(secondS);
            SmpsSequencer second = source(backend, route, incrementProgram(route), S3K);
            render(backend, route);
            assertSame(secondProfile.owner.handlerFor("s3k"), second.getConfig().getCoordFlagHandler());
            assertNotSame(first.getConfig().getCoordFlagHandler(), second.getConfig().getCoordFlagHandler());
            assertEquals(3, secondProfile.owner.state().spindashRevCounter());
            assertEquals(6, firstProfile.owner.state().spindashRevCounter());
            assertSame(firstProfile.owner.handlerFor("s3k"), first.getConfig().getCoordFlagHandler(),
                    "existing references are not rebound by singleton replacement");
            first.restorePreviousMusic();
            second.restorePreviousMusic();
            assertEquals(2, restores.get(), "independent R remains the restore destination");
            assertEquals(rootBefore, rootB.captureLogicalSnapshot());
            assertEquals(0, firstS.commandTimeline().entryCount());
            assertEquals(0, secondS.commandTimeline().entryCount());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"music", "mixed", "standalone"})
    void ordinarySfxStartAndFf07ResetTheSelectedSessionThroughBackendPaths(String route) {
        try (var scope = new Scope()) {
            var profile = new RecordingProfile();
            scope.singleton(scope.manager(profile));
            var backend = scope.backend(() -> {});
            SmpsSequencer rev = source(backend, route, incrementProgram(route), S3K);
            render(backend, route);
            assertEquals(3, fm(rev).keyOffset);
            assertEquals(3, profile.owner.state().spindashRevCounter());
            backend.playSfxSmps(sfx(Sonic3kSfx.RING_LEFT.id, (byte) 0xF2), AudioTestFixtures.EMPTY_DAC, 1, S3K);
            assertEquals(0, profile.owner.state().spindashRevCounter(), "ordinary SFX admission resets S");
            source(backend, route, incrementProgram(route), S3K);
            render(backend, route);
            assertEquals(3, profile.owner.state().spindashRevCounter());
            // A >= BC SFX does not reset on admission; FF07 must execute to reset this value.
            AbstractSmpsData reset = route.equals("music")
                    ? music((byte) 0xFF, (byte) 0x07, (byte) 0xA0, (byte) 0x40)
                    : sfx(Sonic3kSfx.SLIDE_SKID_LOUD.id, (byte) 0xFF, (byte) 0x07, (byte) 0xA0, (byte) 0x40);
            source(backend, route, reset, S3K);
            assertEquals(3, profile.owner.state().spindashRevCounter(), "construction alone did not run FF07");
            render(backend, route);
            assertEquals(0, profile.owner.state().spindashRevCounter(), "FF07 ran in the selected handler");
        }
    }

    @Test
    void ordinaryTwoArgumentMusicBypassesBindingAndKeepsItsProfileHandler() {
        try (var scope = new Scope()) {
            scope.singleton(null);
            var callerState = new SmpsCoordFlagRuntimeState();
            var callerConfig = SmpsConfigBinding.bind(S3K, () -> new Sonic3kCoordFlagHandler(callerState));
            var backend = scope.backend(() -> {});
            backend.setAudioProfile(new Sonic3kAudioProfile() {
                @Override public SmpsSequencerConfig getSequencerConfig() { return callerConfig; }
            });
            var data = music((byte) 0xE9, (byte) 0xE9, (byte) 0xA0, (byte) 0x40);
            assertEquals("data", assertThrows(NullPointerException.class,
                    () -> backend.playSmps(data, null)).getMessage(),
                    "legacy driver admission, unlike raw sequencer construction, requires DAC data");
            assertNull(scope.singleton());
            backend.playSmps(data, AudioTestFixtures.EMPTY_DAC);
            render(backend, "music");
            assertSame(callerConfig, backend.stateForTesting().currentSmps().getConfig());
            assertEquals(2, callerState.spindashRevCounter());
            assertEquals(1, fm(backend.stateForTesting().currentSmps()).keyOffset);
            assertNull(scope.singleton(), "ordinary music does not bind even after the original DAC rejection");
        }
    }

    @Test
    void rejectedAndBlockedSfxDoNotBindButContinuousExtensionBindsBeforeExtending() {
        try (var scope = new Scope()) {
            scope.singleton(null);
            var backend = scope.backend(() -> {});
            backend.setAudioProfile(new Sonic3kAudioProfile() {
                @Override public SmpsRequestAdmissionPolicy getSfxAdmissionPolicy() {
                    return c -> new SmpsRequestAdmissionPolicy.AdmissionResult(false,
                            SmpsRequestAdmissionPolicy.RejectionReason.OTHER, -1, -1, c.resolvedSoundId());
                }
            });
            backend.playSfxSmps(sfx(Sonic3kSfx.SPINDASH.id, (byte) 0xE9), null, 1, S3K);
            assertNull(scope.singleton());
            backend.setAudioProfile(new Sonic3kAudioProfile() {
                @Override public boolean isSfxBlockingMusic(int id) { return true; }
                @Override public boolean isMusicOverride(int id) { return true; }
            });
            backend.playSmps(music((byte) 0xA0, (byte) 0x40), AudioTestFixtures.EMPTY_DAC); // Ordinary override sets the block.
            backend.playSfxSmps(sfx(Sonic3kSfx.SPINDASH.id, (byte) 0xE9), null, 1, S3K);
            assertTrue(backend.stateForTesting().sfxBlocked());
            assertNull(scope.singleton());
            backend.stopPlayback();
            // stopPlayback historically does not clear sfxBlocked; use a fresh raw backend.
            backend = scope.backend(() -> {});
            var profile = new RecordingProfile();
            scope.singleton(scope.manager(profile));
            var data = sfx(Sonic3kSfx.SLIDE_SKID_LOUD.id, (byte) 0xA0, (byte) 0x40);
            var continuous = new SmpsSfxPlaybackPolicy(0x70, false, true);
            backend.playSfxSmps(data, AudioTestFixtures.EMPTY_DAC, 1, S3K, continuous);
            var original = backend.stateForTesting().standaloneSfxSequencers().getFirst();
            var missingRegistration = scope.manager(null);
            scope.singleton(missingRegistration);
            var target = backend;
            assertEquals("no coord-flag handler registered for s3k", assertThrows(IllegalArgumentException.class,
                    () -> target.playSfxSmps(data, AudioTestFixtures.EMPTY_DAC, 1, S3K, continuous)).getMessage());
            assertFalse(backend.stateForTesting().standaloneSfxDriverSnapshot().continuousSfxFlag());
            assertSame(original, backend.stateForTesting().standaloneSfxSequencers().getFirst());
        }
    }

    @Test
    void explicitMusicBindsBeforeStreamedSuppressionWhileOrdinaryMusicStillBypasses() {
        try (var scope = new Scope()) {
            scope.singleton(scope.manager(null)); // S has no s3k registration.
            var backend = scope.backend(() -> {});
            // A port is only the suppression prerequisite; no manager lifecycle is mocked.
            var port = org.mockito.Mockito.mock(StreamedMusicPort.class);
            org.mockito.Mockito.when(port.outputRate()).thenReturn(48_000);
            org.mockito.Mockito.when(port.hasSource()).thenReturn(true);
            backend.installStreamedMusicPort(port);
            backend.update();
            backend.beginStreamedOverrideReplayBypass();
            var data = music((byte) 0xA0, (byte) 0x40);
            assertThrows(IllegalArgumentException.class, () -> backend.playSmps(data, null, S3K, false));
            assertDoesNotThrow(() -> backend.playSmps(data, null));
            assertNull(backend.stateForTesting().currentSmps());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"reset", "replacement"})
    void nextBindingUsesFreshSessionAfterAuthorityRetirementButRawReferencesDoNotMigrate(String retirement) {
        try (var scope = new Scope()) {
            var profile = new RecordingProfile();
            AudioManager singleton = scope.manager(profile);
            scope.singleton(singleton);
            var restores = new AtomicInteger();
            var backend = scope.backend(restores::incrementAndGet); // Independently retained, never installed on S.
            SmpsSequencer old = source(backend, "music", incrementProgram("music"), S3K);
            render(backend, "music");
            var oldOwner = profile.owner;
            var oldConfig = old.getConfig();
            var snapshot = backend.musicDriverForTesting().captureSnapshot();
            if (retirement.equals("reset")) singleton.resetState();
            else singleton.setBackend(new NullAudioBackend());
            singleton.setAudioProfile(profile);
            assertSame(old, backend.stateForTesting().currentSmps(), "S did not retire the raw backend");
            render(backend, "music");
            assertSame(oldConfig, old.getConfig());
            assertEquals(0, oldOwner.state().spindashRevCounter(),
                    "retirement clears the old mutable state even though the raw source survives");
            SmpsSequencer next = source(backend, "music", incrementProgram("music"), S3K);
            render(backend, "music");
            assertNotSame(oldOwner, profile.owner);
            assertNotSame(oldConfig.getCoordFlagHandler(), next.getConfig().getCoordFlagHandler());
            assertEquals(0, oldOwner.state().spindashRevCounter());
            assertEquals(3, profile.owner.state().spindashRevCounter());
            assertSame(oldConfig, old.getConfig());
            assertSame(oldConfig, snapshot.sequencers().getFirst().config());
            assertSame(old.getAudioManager(), snapshot.sequencers().getFirst().audioManager());
            old.restorePreviousMusic();
            next.restorePreviousMusic();
            assertEquals(2, restores.get());
        }
    }

    @Test
    void nonNullHandlerSelectsS3kRegardlessOfRootAndNullHandlerNeedsNoRegistration() {
        try (var scope = new Scope()) {
            var profile = new RecordingProfile() {
                @Override public String presentationGameId() { return "different-base-id"; }
            };
            scope.singleton(scope.manager(profile));
            scope.configureRoot(scope.manager(null));
            var backend = scope.backend(() -> {});
            var seq = source(backend, "music", incrementProgram("music"), S3K);
            render(backend, "music");
            assertSame(profile.owner.handlerFor("s3k"), seq.getConfig().getCoordFlagHandler());
            assertThrows(IllegalArgumentException.class, () -> profile.owner.handlerFor("different-base-id"));
            scope.singleton(scope.manager(null));
            var noHandler = new SmpsSequencerConfig.Builder().build();
            backend.stopPlayback();
            var nullBound = source(backend, "standalone", sfx(0xA0, (byte) 0xF2), noHandler);
            assertNull(nullBound.getConfig().getCoordFlagHandler());
            assertNotSame(noHandler, nullBound.getConfig());
        }
    }

    @Test
    void freshIndependentFactoriesKeepSuppliedHandlerOwnersWhileDefaultsOnlySelectRestoreDestination() {
        try (var scope = new Scope()) {
            var singletonProfile = new RecordingProfile();
            AudioManager s = scope.manager(singletonProfile);
            scope.singleton(s);
            scope.configureRoot(scope.manager(new Sonic3kAudioProfile()));
            for (int count = 0; count < 2; count++) {
                var state = new SmpsCoordFlagRuntimeState();
                var owner = new SmpsCoordFlagHandlerOwner(state);
                owner.register("s3k", Sonic3kCoordFlagHandler::new);
                var defaults = AudioPresentationSourceFactory.Settings.defaults();
                assertSame(s, defaults.audioManager());
                var factory = new AudioPresentationSourceFactory(() -> true, owner, defaults);
                var bound = factory.legacySequencerConfig("s3k", S3K);
                var driver = new com.openggf.audio.driver.SmpsDriver();
                var seq = new SmpsSequencer(incrementProgram("music"), null, driver, defaults.audioManager(), bound);
                seq.advanceSamples(2_000);
                assertEquals(3, state.spindashRevCounter());
                assertEquals(3, fm(seq).keyOffset);
                assertSame(owner.handlerFor("s3k"), bound.getCoordFlagHandler());
                assertNotSame(singletonProfile.owner.handlerFor("s3k"), bound.getCoordFlagHandler());
                assertEquals(0, singletonProfile.owner.state().spindashRevCounter());
            }
        }
    }

    static SonicConfigurationService configuration() {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT, false);
        config.setConfigValue(SonicConfiguration.AUDIO_FM_CORE, "accurate");
        config.setConfigValue(SonicConfiguration.REGION, "NTSC");
        config.setConfigValue(SonicConfiguration.DAC_INTERPOLATE, false);
        config.setConfigValue(SonicConfiguration.FM6_DAC_OFF, false);
        return config;
    }

    static SmpsSequencer source(HeadlessSmpsAudioBackend backend, String route,
            AbstractSmpsData data, SmpsSequencerConfig config) {
        if (route.equals("music")) {
            backend.playSmps(data, AudioTestFixtures.EMPTY_DAC, config, false);
        } else {
            if (route.equals("mixed") && backend.stateForTesting().currentStream() == null) {
                backend.playSmps(music((byte) 0xA0, (byte) 0x7F), AudioTestFixtures.EMPTY_DAC);
            }
            backend.playSfxSmps(data, AudioTestFixtures.EMPTY_DAC, 1, config);
        }
        var state = backend.stateForTesting();
        return route.equals("standalone") ? state.standaloneSfxSequencers().getLast()
                : state.musicSequencers().getLast();
    }

    static void render(HeadlessSmpsAudioBackend backend, String route) {
        var state = backend.stateForTesting();
        AudioStream stream = route.equals("standalone") ? state.sfxStream() : state.currentStream();
        assertNotNull(stream);
        // Two 800-frame reads include the first music dispatch at this tempo;
        // the old one-read fixture stopped before its E9 track executed.
        for (int frame = 0; frame < 2; frame++) {
            assertEquals(1_600, stream.read(new short[1_600], 1_600));
        }
        backend.update();
    }

    static SmpsSequencer.Track fm(SmpsSequencer seq) {
        return seq.getTracks().stream().filter(t -> t.type == SmpsSequencer.TrackType.FM).findFirst().orElseThrow();
    }

    static AbstractSmpsData incrementProgram(String route) {
        byte[] track = {(byte) 0xE9, (byte) 0xE9, (byte) 0xE9, (byte) 0xA0, 0x40};
        return route.equals("music") ? music(track) : sfx(Sonic3kSfx.SPINDASH.id, track);
    }

    static Sonic3kSmpsData music(byte... track) {
        byte[] data = new byte[0x200];
        data[1] = 1; // LE voice table at 0x100.
        data[2] = 2; // DAC + FM1.
        data[4] = 1;
        data[5] = (byte) 0x80;
        data[6] = (byte) 0x80;
        data[10] = (byte) 0x90;
        data[0x80] = (byte) 0xF2;
        System.arraycopy(track, 0, data, 0x90, track.length);
        var result = new Sonic3kSmpsData(data, 0);
        result.setId(1);
        return result;
    }

    static Sonic3kSfxData sfx(int id, byte... track) {
        byte[] data = new byte[0x100];
        data[2] = 1;
        data[3] = 1;
        data[4] = (byte) 0x80;
        data[5] = 2;
        data[6] = 0x40;
        System.arraycopy(track, 0, data, 0x40, track.length);
        var result = new Sonic3kSfxData(data, 0, 0, 0);
        result.setGlobalVoiceData(new byte[25]);
        result.setId(id);
        return result;
    }

    static class RecordingProfile extends Sonic3kAudioProfile {
        SmpsCoordFlagHandlerOwner owner;
        @Override public void configurePresentationCoordFlagHandlers(SmpsCoordFlagHandlerOwner owner) {
            this.owner = owner;
            owner.register("s3k", Sonic3kCoordFlagHandler::new);
        }
    }

    /** Only new fixtures use reflection; production retains its original public API. */
    static final class Scope implements AutoCloseable {
        private final Field instance = field(AudioManager.class, "instance");
        private final Field current = field(EngineServices.class, "current");
        private final AudioManager previousSingleton = singleton();
        private final EngineContext previousRoot = root();
        final List<AudioBackend> backends = new ArrayList<>();
        private final List<AudioManager> managers = new ArrayList<>();

        AudioManager manager(GameAudioProfile profile) {
            AudioManager manager = AudioManager.createSilent();
            managers.add(manager);
            if (profile != null) manager.setAudioProfile(profile);
            return manager;
        }

        HeadlessSmpsAudioBackend backend(MusicRestoreSink sink) {
            var backend = new HeadlessSmpsAudioBackend(configuration(), null, sink);
            backends.add(backend);
            backend.setAudioProfile(new Sonic3kAudioProfile());
            return backend;
        }

        AudioManager singleton() { return (AudioManager) read(instance); }
        void singleton(AudioManager value) { write(instance, value); }
        EngineContext root() { return (EngineContext) read(current); }
        void root(EngineContext value) { write(current, value); }

        EngineContext configureRoot(AudioManager audio) {
            EngineContext basis = root();
            if (basis == null) basis = EngineContext.fromLegacySingletonsForBootstrap();
            EngineContext configured = new EngineContext(configuration(), basis.graphics(), audio,
                    basis.roms(), basis.profiler(), basis.debugOverlay(), basis.playbackDebug(),
                    basis.romDetection(), basis.crossGameFeatures(), basis.moduleResolutionService());
            EngineServices.configure(configured);
            return configured;
        }

        @Override public void close() {
            try {
                try { for (AudioBackend backend : backends) backend.destroy(); }
                finally { for (AudioManager manager : managers) manager.destroy(); }
            } finally {
                // Match the production static synchronized accessors. Never call getInstance/current to save.
                try { singleton(previousSingleton); }
                finally { root(previousRoot); }
            }
        }

        private static Field field(Class<?> type, String name) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }

        private static Object read(Field field) {
            synchronized (field.getDeclaringClass()) {
                try { return field.get(null); }
                catch (IllegalAccessException failure) { throw new AssertionError(failure); }
            }
        }

        private static void write(Field field, Object value) {
            synchronized (field.getDeclaringClass()) {
                try { field.set(null, value); }
                catch (IllegalAccessException failure) { throw new AssertionError(failure); }
            }
        }
    }
}
