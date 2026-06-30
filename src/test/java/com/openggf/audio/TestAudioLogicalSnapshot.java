package com.openggf.audio;

import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TestAudioLogicalSnapshot {
    private AudioManager audio;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new AudioTestFixtures.RecordingAudioBackend());
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
    }

    @Test
    void capturesAudioManagerIntentWithoutBackendObjects() {
        audio.beginCommandTimelineFrame(42);
        audio.playSfx("JUMP");
        audio.playSfx(GameSound.RING);

        AudioLogicalSnapshot snapshot = audio.captureLogicalSnapshot();

        assertFalse(snapshot.ringLeft());
        assertEquals(42, snapshot.commandTimelineFrame());
        assertEquals(2, snapshot.commandTimelineNextOrder());
        assertEquals(2, snapshot.commandEntryCount());
        assertTrue(snapshot.donorBindings().isEmpty());
        assertTrue(snapshot.donorGameIds().isEmpty());
    }

    @Test
    void capturesDonorBindingsAsDescriptorsOnly() {
        AudioTestFixtures.StubSmpsLoader donor = new AudioTestFixtures.StubSmpsLoader();
        audio.registerDonorLoader("s3k", donor, AudioTestFixtures.EMPTY_DAC);
        audio.registerDonorSound(GameSound.SPINDASH_CHARGE, "s3k", 0xA4);

        AudioLogicalSnapshot snapshot = audio.captureLogicalSnapshot();

        assertEquals(Set.of("s3k"), snapshot.donorGameIds());
        assertEquals(Set.of(new AudioLogicalSnapshot.DonorSfxBindingSnapshot(
                GameSound.SPINDASH_CHARGE, "s3k", 0xA4)), snapshot.donorBindings());

        Set<String> bindingComponents = Arrays.stream(
                        AudioLogicalSnapshot.DonorSfxBindingSnapshot.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());
        assertFalse(bindingComponents.contains("loader"));
        assertFalse(bindingComponents.contains("dacdata"));
        assertFalse(bindingComponents.contains("config"));
    }

    @Test
    void sourceDescriptorsDoNotDependOnJavaObjectIdentity() {
        AudioSourceDescriptor baseMusic = AudioSourceDescriptor.baseMusic(0x81);
        AudioSourceDescriptor donorSfx = AudioSourceDescriptor.donorSfx("s3k", 0xA4);
        AudioSourceDescriptor namedSfx = AudioSourceDescriptor.baseNamedSfx("RING_LEFT");

        assertEquals(AudioSourceDescriptor.Route.BASE_MUSIC_ID, baseMusic.route());
        assertEquals(0x81, baseMusic.id());
        assertNull(baseMusic.name());
        assertNull(baseMusic.donorGameId());

        assertEquals(AudioSourceDescriptor.Route.DONOR_SFX_ID, donorSfx.route());
        assertEquals(0xA4, donorSfx.id());
        assertEquals("s3k", donorSfx.donorGameId());

        assertEquals(AudioSourceDescriptor.Route.BASE_SFX_NAME, namedSfx.route());
        assertEquals("RING_LEFT", namedSfx.name());
    }

    @Test
    void logicalSnapshotRecordExcludesOpenAlPresentationState() {
        Set<String> recordComponentNames = Arrays.stream(AudioLogicalSnapshot.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());
        Set<String> forbiddenFragments = Set.of(
                "openal", "source", "buffer", "device", "context", "native", "queued", "processed");

        for (String componentName : recordComponentNames) {
            for (String forbidden : forbiddenFragments) {
                assertFalse(componentName.contains(forbidden),
                        () -> "presentation field leaked into snapshot: " + componentName);
            }
        }

        assertEquals(EnumSet.allOf(AudioSourceDescriptor.Route.class), AudioSourceDescriptor.supportedRoutes());
    }

    @Test
    void capturesBackendLogicalRuntimeStateWhenBackendProvidesIt() {
        SmpsDriverSnapshot driverSnapshot = newEmptyDriverSnapshot();
        SnapshotAudioBackend backend = new SnapshotAudioBackend(new AudioBackendLogicalSnapshot(
                AudioSourceDescriptor.baseMusic(0x81),
                true,
                true,
                true,
                2,
                List.of(AudioSourceDescriptor.baseMusic(0x88), AudioSourceDescriptor.donorMusic("s3k", 0x2A)),
                driverSnapshot,
                null));
        audio.setBackend(backend);

        AudioLogicalSnapshot snapshot = audio.captureLogicalSnapshot();

        assertEquals(AudioSourceDescriptor.baseMusic(0x81), snapshot.backend().currentMusic());
        assertTrue(snapshot.backend().sfxBlocked());
        assertTrue(snapshot.backend().pendingRestore());
        assertTrue(snapshot.backend().speedShoesEnabled());
        assertEquals(2, snapshot.backend().speedMultiplier());
        assertEquals(
                List.of(AudioSourceDescriptor.baseMusic(0x88), AudioSourceDescriptor.donorMusic("s3k", 0x2A)),
                snapshot.backend().overrideStack());
        assertSame(driverSnapshot, snapshot.backend().musicDriver());
        assertNull(snapshot.backend().standaloneSfxDriver());
    }

    @Test
    void nonParticipatingBackendCapturesEmptyLogicalRuntimeState() {
        AudioLogicalSnapshot snapshot = audio.captureLogicalSnapshot();

        assertEquals(AudioBackendLogicalSnapshot.empty(), snapshot.backend());
    }

    @Test
    void lwjglBackendPublishesLogicalSpeedStateWithoutOpenAlInitialization() {
        LWJGLAudioBackend backend = new LWJGLAudioBackend(SonicConfigurationService.getInstance());

        backend.setSpeedShoes(true);
        backend.setSpeedMultiplier(3);

        AudioBackendLogicalSnapshot snapshot = backend.captureLogicalSnapshot();
        assertNull(snapshot.currentMusic());
        assertFalse(snapshot.sfxBlocked());
        assertFalse(snapshot.pendingRestore());
        assertTrue(snapshot.speedShoesEnabled());
        assertEquals(3, snapshot.speedMultiplier());
        assertEquals(List.of(), snapshot.overrideStack());
    }

    @Test
    void audioManagerPassesFallbackMusicDescriptorToBackendSnapshot() {
        DescriptorRecordingBackend backend = new DescriptorRecordingBackend();
        audio.setBackend(backend);

        audio.playMusic(0x90);

        assertEquals(AudioSourceDescriptor.fallbackMusic(0x90),
                audio.captureLogicalSnapshot().backend().currentMusic());
    }

    @Test
    void audioManagerPassesDonorMusicDescriptorToBackendSnapshot() {
        DescriptorRecordingBackend backend = new DescriptorRecordingBackend();
        audio.setBackend(backend);
        AudioTestFixtures.StubSmpsLoader donor = new AudioTestFixtures.StubSmpsLoader();
        donor.musicResults.put(0x2A, new AudioTestFixtures.StubSmpsData("donor-music"));
        audio.registerDonorLoader("s3k", donor, AudioTestFixtures.EMPTY_DAC);

        audio.playDonorMusic("s3k", 0x2A);

        assertEquals(AudioSourceDescriptor.donorMusic("s3k", 0x2A),
                audio.captureLogicalSnapshot().backend().currentMusic());
    }

    @Test
    void restoreLogicalSnapshotRestoresManagerCursorAndBackendState() {
        RestorableSnapshotBackend backend = new RestorableSnapshotBackend();
        audio.setBackend(backend);
        audio.beginCommandTimelineFrame(12);
        audio.playSfx(GameSound.RING);
        backend.snapshot = new AudioBackendLogicalSnapshot(
                AudioSourceDescriptor.baseMusic(0x81),
                true,
                true,
                true,
                3,
                List.of(AudioSourceDescriptor.donorMusic("s3k", 0x2A)));
        AudioLogicalSnapshot snapshot = audio.captureLogicalSnapshot();

        audio.beginCommandTimelineFrame(20);
        audio.resetRingSound();
        backend.snapshot = AudioBackendLogicalSnapshot.empty();

        audio.restoreLogicalSnapshot(snapshot);

        AudioLogicalSnapshot restored = audio.captureLogicalSnapshot();
        assertFalse(restored.ringLeft());
        assertEquals(12, restored.commandTimelineFrame());
        assertEquals(1, restored.commandTimelineNextOrder());
        assertEquals(snapshot.backend(), backend.restored);
        assertEquals(snapshot.backend(), restored.backend());
    }

    @Test
    void restoreLogicalSnapshotPassesSmpsDependencyResolverToBackend() {
        ResolverRecordingBackend backend = new ResolverRecordingBackend();
        audio.setBackend(backend);
        AudioTestFixtures.StubSmpsLoader donor = new AudioTestFixtures.StubSmpsLoader();
        AbstractSmpsData donorSfx = new AudioTestFixtures.StubSmpsData("donor-sfx");
        donorSfx.setId(0xA4);
        donor.sfxResults.put(0xA4, donorSfx);
        SmpsSequencerConfig donorConfig = new SmpsSequencerConfig.Builder().tempoModBase(0x222).build();
        audio.registerDonorLoader("s3k", donor, AudioTestFixtures.EMPTY_DAC, donorConfig);

        audio.restoreLogicalSnapshot(new AudioLogicalSnapshot(
                true,
                0,
                0,
                0,
                AudioBackendLogicalSnapshot.empty(),
                Set.of("s3k"),
                Set.of()));

        assertSame(donorSfx, backend.resolvedData);
        assertSame(AudioTestFixtures.EMPTY_DAC, backend.resolvedDac);
        assertSame(donorConfig, backend.resolvedConfig);
    }

    @Test
    void repeatedLogicalSnapshotRestoresReuseResolvedBaseSmpsData() {
        CountingSmpsLoader loader = new CountingSmpsLoader();
        AbstractSmpsData music = new AudioTestFixtures.StubSmpsData("music");
        music.setId(0x81);
        loader.musicResults.put(0x81, music);
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(loader));
        audio.setRom(null);

        SmpsResolvingBackend backend = new SmpsResolvingBackend(SmpsSourceDescriptor.baseMusic(music), music);
        audio.setBackend(backend);
        AudioLogicalSnapshot snapshot = new AudioLogicalSnapshot(
                true,
                0,
                0,
                0,
                AudioBackendLogicalSnapshot.empty(),
                Set.of(),
                Set.of());

        audio.restoreLogicalSnapshot(snapshot);
        audio.restoreLogicalSnapshot(snapshot);

        assertEquals(2, backend.resolveCalls);
        assertEquals(1, loader.musicLoadCalls);
    }

    private static final class SnapshotAudioBackend extends NullAudioBackend {
        private final AudioBackendLogicalSnapshot snapshot;

        private SnapshotAudioBackend(AudioBackendLogicalSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public AudioBackendLogicalSnapshot captureLogicalSnapshot() {
            return snapshot;
        }
    }

    private static final class RestorableSnapshotBackend extends NullAudioBackend {
        private AudioBackendLogicalSnapshot snapshot = AudioBackendLogicalSnapshot.empty();
        private AudioBackendLogicalSnapshot restored;

        @Override
        public AudioBackendLogicalSnapshot captureLogicalSnapshot() {
            return snapshot;
        }

        @Override
        public void restoreLogicalSnapshot(AudioBackendLogicalSnapshot snapshot) {
            restored = snapshot;
            this.snapshot = snapshot;
        }
    }

    private static final class ResolverRecordingBackend extends NullAudioBackend {
        private AbstractSmpsData resolvedData;
        private com.openggf.audio.smps.DacData resolvedDac;
        private SmpsSequencerConfig resolvedConfig;

        @Override
        public void restoreLogicalSnapshot(
                AudioBackendLogicalSnapshot snapshot,
                SmpsDriverSnapshot.DependencyResolver resolver) {
            AbstractSmpsData placeholder = new AudioTestFixtures.StubSmpsData("placeholder");
            placeholder.setId(0xA4);
            SmpsSequencer sequencer = new SmpsSequencer(
                    placeholder,
                    AudioTestFixtures.EMPTY_DAC,
                    AudioManager.getInstance(),
                    new SmpsSequencerConfig.Builder().build());
            SmpsDriverSnapshot.SequencerEntry entry = new SmpsDriverSnapshot.SequencerEntry(
                    true,
                    SmpsSourceDescriptor.donorSfx("s3k", sequencer.getSmpsData()),
                    null,
                    sequencer.getSmpsData(),
                    AudioTestFixtures.EMPTY_DAC,
                    AudioManager.getInstance(),
                    new SmpsSequencerConfig.Builder().build(),
                    sequencer.captureSnapshot());
            resolvedData = resolver.resolveSmpsData(entry);
            resolvedDac = resolver.resolveDacData(entry);
            resolvedConfig = resolver.resolveConfig(entry);
        }
    }

    private static final class SmpsResolvingBackend extends NullAudioBackend {
        private final SmpsSourceDescriptor source;
        private final AbstractSmpsData placeholder;
        private int resolveCalls;

        private SmpsResolvingBackend(SmpsSourceDescriptor source, AbstractSmpsData placeholder) {
            this.source = source;
            this.placeholder = placeholder;
        }

        @Override
        public void restoreLogicalSnapshot(
                AudioBackendLogicalSnapshot snapshot,
                SmpsDriverSnapshot.DependencyResolver resolver,
                boolean preservePresentationQueue) {
            SmpsSequencer sequencer = new SmpsSequencer(
                    placeholder,
                    AudioTestFixtures.EMPTY_DAC,
                    AudioManager.getInstance(),
                    new SmpsSequencerConfig.Builder().build());
            SmpsDriverSnapshot.SequencerEntry entry = new SmpsDriverSnapshot.SequencerEntry(
                    true,
                    source,
                    null,
                    sequencer.getSmpsData(),
                    AudioTestFixtures.EMPTY_DAC,
                    AudioManager.getInstance(),
                    new SmpsSequencerConfig.Builder().build(),
                    sequencer.captureSnapshot());
            resolver.resolveSmpsData(entry);
            resolveCalls++;
        }
    }

    private static final class CountingSmpsLoader implements SmpsLoader {
        private final java.util.Map<Integer, AbstractSmpsData> musicResults = new java.util.HashMap<>();
        private int musicLoadCalls;

        @Override
        public AbstractSmpsData loadMusic(int musicId) {
            musicLoadCalls++;
            return musicResults.get(musicId);
        }

        @Override
        public AbstractSmpsData loadSfx(int sfxId) {
            return null;
        }

        @Override
        public AbstractSmpsData loadSfx(String sfxName) {
            return null;
        }

        @Override
        public DacData loadDacData() {
            return AudioTestFixtures.EMPTY_DAC;
        }
    }

    private static SmpsDriverSnapshot newEmptyDriverSnapshot() {
        return new SmpsDriverSnapshot(
                SmpsSequencer.Region.NTSC,
                com.openggf.audio.driver.SmpsDriver.ReadMode.HYBRID,
                -1,
                false,
                0,
                List.of(),
                new int[0],
                new int[0]);
    }

    private static final class DescriptorRecordingBackend extends NullAudioBackend {
        private AudioSourceDescriptor currentMusic;

        @Override
        public void prepareLogicalMusicSource(AudioSourceDescriptor descriptor) {
            currentMusic = descriptor;
        }

        @Override
        public void playMusic(int musicId) {
        }

        @Override
        public void playSmps(com.openggf.audio.smps.AbstractSmpsData data,
                             com.openggf.audio.smps.DacData dacData,
                             com.openggf.audio.smps.SmpsSequencerConfig config,
                             boolean forceOverride) {
        }

        @Override
        public AudioBackendLogicalSnapshot captureLogicalSnapshot() {
            return new AudioBackendLogicalSnapshot(
                    currentMusic,
                    false,
                    false,
                    false,
                    1,
                    List.of());
        }
    }
}
