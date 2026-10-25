package com.openggf.audio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.SmpsSequencerHost;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Restore destination migration, originating at 97eb86f186b4 (2026-10-24). */
class TestSmpsRestoreOwnerContract {
    private final EngineContext previousRoot = EngineServices.current();
    private final AudioManager audio = AudioManager.getInstance();
    private HeadlessSmpsAudioBackend backend;
    private final List<AudioManager> otherManagers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (backend != null) backend.destroy();
        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        for (AudioManager other : otherManagers) other.destroy();
        EngineServices.configure(previousRoot);
    }

    @Test
    void backendRequiresDestinationAfterConfigAndBeforeSampleRate() {
        var config = SonicConfigurationService.createStandalone();
        assertEquals("configService", assertThrows(NullPointerException.class,
                () -> new HeadlessSmpsAudioBackend(null, null, -1, null)).getMessage());
        assertEquals("musicRestoreSink", assertThrows(NullPointerException.class,
                () -> new HeadlessSmpsAudioBackend(config, null, -1, null)).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> new HeadlessSmpsAudioBackend(config, null, -1, audio));
        assertEquals("configService", assertThrows(NullPointerException.class,
                () -> new LWJGLAudioBackend(null, null, null)).getMessage());
        assertEquals("musicRestoreSink", assertThrows(NullPointerException.class,
                () -> new LWJGLAudioBackend(config, null, null)).getMessage());
        assertTrue(java.lang.reflect.Modifier.isFinal(
                assertDoesNotThrow(() -> AbstractSmpsAudioBackend.class
                        .getDeclaredField("musicRestoreSink")).getModifiers()));
        var allowed = java.util.Set.of(
                List.of(SonicConfigurationService.class, com.openggf.debug.PerformanceProfiler.class,
                        MusicRestoreSink.class),
                List.of(SonicConfigurationService.class, com.openggf.debug.PerformanceProfiler.class,
                        MusicRestoreSink.class, AudioManager.class));
        for (var type : List.of(LWJGLAudioBackend.class, HeadlessSmpsAudioBackend.class)) {
            assertEquals(allowed, java.util.Arrays.stream(type.getConstructors())
                    .map(constructor -> List.of(constructor.getParameterTypes()))
                    .collect(java.util.stream.Collectors.toSet()));
        }
        for (var constructor : SmpsSequencer.class.getConstructors()) {
            assertTrue(List.of(constructor.getParameterTypes()).contains(MusicRestoreSink.class),
                    "every sequencer constructor needs an explicit destination");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"music", "mixed", "standalone"})
    void laterSourcesRetainAAndExecuteItsIntentTimelineAndMirror(String source) {
        audio.resetState();
        configureRoot(audio);
        backend = new HeadlessSmpsAudioBackend(SonicConfigurationService.createStandalone(), null, audio);
        audio.setBackend(backend);
        // Producer replacement retires voices, so prepare the foreground on
        // the newly installed producer before exercising the legacy source.
        prepareManagerMusic();
        AudioManager b = otherManager();
        configureRoot(b);
        SmpsSequencer seq = buildBackendSource(source);
        assertSame(audio, seq.getAudioManager());
        var bBefore = b.captureLogicalSnapshot();
        long mirrors = audio.shadowParitySnapshot().commandCount();
        int entries = audio.commandTimeline().entryCount();
        audio.beginCommandTimelineFrame(42);
        List<AudioMutatorAccess.Kind> intents = new ArrayList<>();
        try (var ignored = AudioMutatorAccess.bind(audio, intent -> {
            intents.add(intent.kind());
            assertEquals(entries, audio.commandTimeline().entryCount(),
                    "intent precedes recording the restore");
        })) {
            seq.restorePreviousMusic();
        }
        assertEquals(List.of(AudioMutatorAccess.Kind.RESTORE), intents);
        var entry = audio.commandTimeline().entries().getLast();
        assertEquals(42, entry.frame());
        assertEquals(0, entry.order());
        assertEquals(new AudioCommand.RestoreMusic(AudioCommand.RestoreCause.EXPLICIT), entry.command());
        assertEquals(mirrors + 1, audio.shadowParitySnapshot().commandCount());
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(0x81, audio.captureLogicalSnapshot().presentation().activeMusic().musicId());
        assertEquals(bBefore, b.captureLogicalSnapshot(), "configured B receives nothing");
        // restoreMusic sends no direct legacy backend command. The ledger and
        // authoritative presentation, rather than a legacy stack pop, are the oracle.
    }

    @Test
    void rewindSuppressionStillReachesARestoreIntentWithoutTimelineOrMirror() {
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        configureRoot(audio);
        backend = new HeadlessSmpsAudioBackend(SonicConfigurationService.createStandalone(), null, audio);
        configureRoot(otherManager());
        SmpsSequencer seq = buildBackendSource("music");
        int entries = audio.commandTimeline().entryCount();
        long mirrors = audio.shadowParitySnapshot().commandCount();
        List<AudioMutatorAccess.Kind> intents = new ArrayList<>();
        try (var binding = AudioMutatorAccess.bind(audio, intent -> intents.add(intent.kind()));
             var replay = audio.beginRewindReplay(20, 10, AudioReplayReason.SEEK)) {
            seq.restorePreviousMusic();
        }
        assertEquals(List.of(AudioMutatorAccess.Kind.RESTORE), intents);
        assertEquals(entries, audio.commandTimeline().entryCount());
        assertEquals(mirrors, audio.shadowParitySnapshot().commandCount());
        assertEquals(0, otherManagers.getFirst().commandTimeline().entryCount());
    }

    @Test
    void destructiveDriverRecreationUsesCapturedDestinationAndAllowsExplicitResolverOverride() {
        audio.resetState();
        configureRoot(audio);
        backend = new HeadlessSmpsAudioBackend(SonicConfigurationService.createStandalone(), null, audio);
        SmpsSequencer original = buildBackendSource("music");
        original.triggerFadeIn(12, 2);
        var captured = backend.musicDriverForTesting().captureSnapshot();
        assertSame(audio, captured.sequencers().getFirst().audioManager());
        AudioManager b = otherManager();
        configureRoot(b);
        backend.stopPlayback();
        var restored = new SmpsDriver();
        restored.restoreSnapshot(captured);
        var restoredEntry = restored.captureSnapshot().sequencers().getFirst();
        assertSame(audio, restoredEntry.audioManager());
        assertNotSame(original, restored.sequencersForTesting().getFirst());
        assertEquals(captured.sequencers().getFirst().snapshot().fade(), restoredEntry.snapshot().fade());
        restored.sequencersForTesting().getFirst().restorePreviousMusic();
        assertEquals(1, audio.commandTimeline().entryCount());
        assertEquals(0, b.commandTimeline().entryCount());

        restored.restoreSnapshot(captured, new SmpsDriverSnapshot.DependencyResolver() {
            @Override public AbstractSmpsData resolveSmpsData(SmpsDriverSnapshot.SequencerEntry entry) {
                return entry.smpsData();
            }
            @Override public DacData resolveDacData(SmpsDriverSnapshot.SequencerEntry entry) {
                return entry.dacData();
            }
            @Override public MusicRestoreSink resolveAudioManager(SmpsDriverSnapshot.SequencerEntry entry) {
                return b;
            }
            @Override public SmpsSequencerConfig resolveConfig(SmpsDriverSnapshot.SequencerEntry entry) {
                return entry.config();
            }
        });
        assertSame(b, restored.captureSnapshot().sequencers().getFirst().audioManager());
        List<AudioMutatorAccess.Kind> overridden = new ArrayList<>();
        try (var binding = AudioMutatorAccess.bind(b, intent -> overridden.add(intent.kind()))) {
            restored.sequencersForTesting().getFirst().restorePreviousMusic();
        }
        assertEquals(List.of(AudioMutatorAccess.Kind.RESTORE), overridden);
        assertEquals(1, audio.commandTimeline().entryCount());
    }

    @Test
    void resetAndReplacementRetireSourcesWhileKeepingTheManagerLedger() {
        audio.resetState();
        configureRoot(audio);
        backend = new HeadlessSmpsAudioBackend(SonicConfigurationService.createStandalone(), null, audio);
        audio.setBackend(backend);
        SmpsSequencer old = buildBackendSource("music");
        audio.resetState();
        configureRoot(otherManager());
        buildBackendSource("music").restorePreviousMusic();
        assertEquals(1, audio.commandTimeline().entryCount());
        var ledger = List.copyOf(audio.commandTimeline().entries());
        var replacement = new HeadlessSmpsAudioBackend(SonicConfigurationService.createStandalone(), null, audio);
        audio.setBackend(replacement);
        assertNull(backend.stateForTesting().currentSmps());
        assertSame(audio, old.getAudioManager(), "retired references do not transfer to B");
        assertEquals(ledger, audio.commandTimeline().entries());
        backend = replacement;
        assertSame(audio, buildBackendSource("standalone").getAudioManager());
        backend.destroy();
        assertNull(backend.stateForTesting().sfxStream());
        assertEquals(ledger, audio.commandTimeline().entries(), "teardown is not a restore request");
    }

    @Test
    void restoreSinkFailurePropagatesWithoutRecordingOrTransferringTheRequest() {
        audio.resetState();
        configureRoot(audio);
        backend = new HeadlessSmpsAudioBackend(SonicConfigurationService.createStandalone(), null, audio);
        configureRoot(otherManager());
        SmpsSequencer seq = buildBackendSource("music");
        var failure = new IllegalStateException("restore intent failed");
        try (var binding = AudioMutatorAccess.bind(audio, intent -> { throw failure; })) {
            assertSame(failure, assertThrows(IllegalStateException.class, seq::restorePreviousMusic));
        }
        assertEquals(0, audio.commandTimeline().entryCount());
        assertEquals(0, otherManagers.getFirst().commandTimeline().entryCount());
        backend.destroy();
        assertNull(backend.stateForTesting().currentStream());
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1NaturalRestoreRemainsDeferredAndExactlyOnce() throws Exception {
        assertNaturalRestore(new Sonic1AudioProfile(), RomTestUtils.ensureSonic1RomAvailable());
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonic2NaturalRestoreRemainsDeferredAndExactlyOnce() throws Exception {
        assertNaturalRestore(new Sonic2AudioProfile(), RomTestUtils.ensureSonic2RomAvailable());
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kNaturalRestoreRemainsDeferredAndExactlyOnce() throws Exception {
        assertNaturalRestore(new Sonic3kAudioProfile(), RomTestUtils.ensureSonic3kRomAvailable());
    }

    private void assertNaturalRestore(GameAudioProfile profile, java.io.File file) throws Exception {
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        configureRoot(audio);
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(file.getAbsolutePath()));
            audio.setAudioProfile(profile);
            audio.setRom(rom);
            int level = profile instanceof Sonic1AudioProfile ? 0x81
                    : profile instanceof Sonic2AudioProfile ? Sonic2Music.EMERALD_HILL.id : 1;
            audio.playMusic(level);
            for (int frame = 0; frame < 120; frame++) audio.presentFrame(PresentationMode.FORWARD);
            audio.playMusic(profile.getExtraLifeMusicId());
            audio.presentFrame(PresentationMode.FORWARD);
            AudioManager b = otherManager();
            configureRoot(b);
            var midJingle = audio.captureLogicalSnapshot();
            audio.restoreLogicalSnapshot(midJingle);
            List<AudioMutatorAccess.Kind> intents = new ArrayList<>();
            long[] mirrorsAtRequest = {-1};
            int[] foregroundBeforeFrame = {-1};
            try (var binding = AudioMutatorAccess.bind(audio, intent -> {
                intents.add(intent.kind());
                if (intent.kind() == AudioMutatorAccess.Kind.NATURAL_RESTORE) {
                    mirrorsAtRequest[0] = audio.shadowParitySnapshot().commandCount();
                    assertEquals(profile.getExtraLifeMusicId(), foregroundBeforeFrame[0]);
                    assertThrows(IllegalStateException.class, audio::captureLogicalSnapshot,
                            "the natural request still runs within the active service boundary");
                }
            })) {
                for (int frame = 0; frame < 900 && intents.isEmpty(); frame++) {
                    foregroundBeforeFrame[0] = audio.captureLogicalSnapshot()
                            .presentation().activeMusic().musicId();
                    audio.presentFrame(PresentationMode.FORWARD);
                }
                assertEquals(List.of(AudioMutatorAccess.Kind.NATURAL_RESTORE), intents);
                assertEquals(mirrorsAtRequest[0] + 1, audio.shadowParitySnapshot().commandCount(),
                        "one deferred publication at the outer presentation boundary");
                for (int frame = 0; frame < 20; frame++) audio.presentFrame(PresentationMode.FORWARD);
                assertEquals(List.of(AudioMutatorAccess.Kind.NATURAL_RESTORE), intents);
            }
            assertTrue(audio.commandTimeline().entries().stream()
                    .noneMatch(entry -> entry.command() instanceof AudioCommand.RestoreMusic),
                    "natural restore must not become an extra explicit command");
            assertEquals(level, audio.captureLogicalSnapshot().presentation().activeMusic().musicId());
            assertEquals(0, b.commandTimeline().entryCount());
            audio.resetState(); // release the ROM before it closes
        }
    }

    private void prepareManagerMusic() {
        var loader = new AudioTestFixtures.StubSmpsLoader();
        loader.musicResults.put(0x81, new AudioTestFixtures.StubSmpsData("base"));
        loader.musicResults.put(0x82, new AudioTestFixtures.StubSmpsData("jingle"));
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(loader) {
            @Override public int getExtraLifeMusicId() { return 0x82; }
            @Override public SmpsSequencerConfig getSequencerConfig() {
                return Sonic2SmpsSequencerConfig.CONFIG;
            }
        });
        audio.setRom(new Rom());
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        audio.playMusic(0x82);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(0x82, audio.captureLogicalSnapshot().presentation().activeMusic().musicId());
    }

    private SmpsSequencer buildBackendSource(String source) {
        backend.setAudioProfile(new Sonic2AudioProfile());
        if (!source.equals("standalone")) {
            backend.playSmps(new AudioTestFixtures.StubSmpsData("music"), AudioTestFixtures.EMPTY_DAC,
                    Sonic2SmpsSequencerConfig.CONFIG, false);
        }
        if (!source.equals("music")) {
            backend.playSfxSmps(new AudioTestFixtures.StubSmpsData("sfx"), AudioTestFixtures.EMPTY_DAC,
                    1.0f, Sonic2SmpsSequencerConfig.CONFIG);
        }
        var state = backend.stateForTesting();
        return source.equals("standalone") ? state.standaloneSfxSequencers().getLast()
                : state.musicSequencers().getLast();
    }

    private AudioManager otherManager() {
        AudioManager other = AudioManager.createSilent();
        otherManagers.add(other);
        return other;
    }

    private void configureRoot(AudioManager owner) {
        EngineServices.configure(new EngineContext(previousRoot.configuration(), previousRoot.graphics(),
                owner, previousRoot.roms(), previousRoot.profiler(), previousRoot.debugOverlay(),
                previousRoot.playbackDebug(), previousRoot.romDetection(), previousRoot.crossGameFeatures(),
                previousRoot.moduleResolutionService()));
    }

    @Test
    void explicitSequencerValidationOrderNullableDacAndLegacyTrustStayStable() {
        var data = new AudioTestFixtures.StubSmpsData("validation");
        var config = new SmpsSequencerConfig.Builder().build();
        var driver = new SmpsDriver();
        assertEquals("synth", assertThrows(NullPointerException.class,
                () -> new SmpsSequencer(null, null, null, audio, null)).getMessage());
        assertEquals("smpsData", assertThrows(NullPointerException.class,
                () -> new SmpsSequencer(null, null, null, null, null, null, null, null)).getMessage());
        assertEquals("sourceDescriptorTrust", assertThrows(NullPointerException.class,
                () -> new SmpsSequencer(data, null, null, null, null, null, null, null)).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> new SmpsSequencer(data, null, null, null, null, null, null,
                        SmpsSequencer.SourceDescriptorTrust.PRECOMPUTED_IMMUTABLE));
        var brokenData = org.mockito.Mockito.mock(AbstractSmpsData.class);
        var descriptorFailure = new IllegalStateException("descriptor computation failed");
        org.mockito.Mockito.when(brokenData.getData()).thenThrow(descriptorFailure);
        assertSame(descriptorFailure, assertThrows(IllegalStateException.class,
                () -> new SmpsSequencer(brokenData, null, null, null, null, null, null,
                        SmpsSequencer.SourceDescriptorTrust.LEGACY_RECOMPUTE)),
                "descriptor computation still precedes sink validation");
        assertEquals("audioManager", assertThrows(NullPointerException.class,
                () -> new SmpsSequencer(data, null, null, null, null, null, null,
                        SmpsSequencer.SourceDescriptorTrust.LEGACY_RECOMPUTE)).getMessage());
        assertEquals("synth", assertThrows(NullPointerException.class,
                () -> new SmpsSequencer(data, null, null, null, audio, null, null,
                        SmpsSequencer.SourceDescriptorTrust.LEGACY_RECOMPUTE)).getMessage());
        assertEquals("host", assertThrows(NullPointerException.class,
                () -> new SmpsSequencer(data, null, driver, null, audio, null, null,
                        SmpsSequencer.SourceDescriptorTrust.LEGACY_RECOMPUTE)).getMessage());
        assertEquals("config", assertThrows(NullPointerException.class,
                () -> new SmpsSequencer(data, null, driver, SmpsSequencerHost.NONE, audio, null, null,
                        SmpsSequencer.SourceDescriptorTrust.LEGACY_RECOMPUTE)).getMessage());
        for (SmpsSequencer seq : new SmpsSequencer[] {
                new SmpsSequencer(data, null, audio, config),
                new SmpsSequencer(data, null, driver, audio, config)}) {
            assertSame(audio, seq.getAudioManager());
            assertNull(seq.getDacData());
            assertEquals(SmpsSourceDescriptor.from(data), seq.getSourceDescriptor());
            assertEquals(SmpsSequencer.SourceDescriptorTrust.LEGACY_RECOMPUTE,
                    seq.getSourceDescriptorTrust());
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void unchangedRootFixedInputPcmWritesServicesAndLogicalState() throws Exception {
        // Fixed, short in-memory oracle established before changing production.
        // These digests are engine-preservation evidence, not native parity.
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT, false);
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic2RomAvailable().getAbsolutePath()));
            var loader = new Sonic2SmpsLoader(rom);
            for (String scenario : new String[] {"music", "mixed", "standalone", "one-up"}) {
                backend = new HeadlessSmpsAudioBackend(config, null, audio);
                backend.setAudioProfile(new Sonic2AudioProfile());
                Oracle oracle = new Oracle();
                backend.setChipWriteObserver(oracle);
                backend.setDriverServiceObserver(oracle);
                if (!scenario.equals("standalone")) {
                    backend.playSmps(loader.loadMusic(Sonic2Music.EMERALD_HILL.id),
                            loader.loadDacData(), Sonic2SmpsSequencerConfig.CONFIG, false);
                }
                int frames = scenario.equals("one-up") ? 360 : 18;
                for (int frame = 0; frame < frames; frame++) {
                    if ((scenario.equals("mixed") && frame == 6)
                            || (scenario.equals("standalone") && frame == 0)) {
                        backend.playSfxSmps(loader.loadSfx(Sonic2Sfx.RING_LEFT.id),
                                loader.loadDacData(), 1.0f, Sonic2SmpsSequencerConfig.CONFIG);
                    }
                    if (scenario.equals("one-up") && frame == 60) {
                        backend.playSmps(loader.loadMusic(Sonic2Music.EXTRA_LIFE.id),
                                loader.loadDacData(), Sonic2SmpsSequencerConfig.CONFIG, true);
                    }
                    oracle.add("frame:" + frame);
                    short[] pcm = new short[800 * 2];
                    AudioStream stream = scenario.equals("standalone")
                            ? backend.sfxStream : backend.currentStream;
                    stream.read(pcm, pcm.length);
                    oracle.pcm(pcm);
                    backend.update();
                    var state = backend.stateForTesting();
                    oracle.state(state.musicDriverSnapshot());
                    oracle.state(state.standaloneSfxDriverSnapshot());
                    oracle.add(state.currentMusicId() + ":" + state.sfxBlocked()
                            + ":" + state.pendingRestore() + ":" + state.overrideStack().size());
                }
                String actual = oracle.finish();
                assertEquals(switch (scenario) {
                    case "music" -> "cc53647fc3dbe7e170b53ef512bf5063429855ec5ad866579d9704c00bdcc359:0a8fc546ff65b0c63b7e660dcad31b2052f405d9c91cd206e6bc08498288bbb1";
                    case "mixed" -> "98fb44d28f2ef48ce5a45bde928b89fac07d5386c09a9531e67ec4a64327b276:aa0194e036a93812c16c77245323e6a74f838a353c0ec18d66c70350acf6c05b";
                    case "standalone" -> "de718f4c07f382d83fe03eb0991492418459f90d52f253b4f3d4b2ca480f477a:aaa1bd7d02bf5107532a378b1ecb7ef1e5f548a21c0317d38d242d0f2cd5c208";
                    case "one-up" -> "14116e8c6f9714dd753744a26c15c396f7ef64d5328ca6e443a81f91c6b68b8e:77103c0f821d8b0b9e4e22df9e3c71d33c19e22ac000f9c8ef3ad97af4e08860";
                    default -> throw new AssertionError(scenario);
                }, actual, scenario + " PCM and ordered service/write/logical oracle");
                backend.destroy();
                backend = null;
            }
        }
    }

    @JsonIgnoreProperties({"smpsData", "dacData", "audioManager", "config"})
    private abstract static class RetainedDependencies { }

    private static final class Oracle implements ChipWriteObserver, SmpsDriverServiceObserver {
        private final ObjectMapper json = new ObjectMapper().addMixIn(
                SmpsDriverSnapshot.SequencerEntry.class, RetainedDependencies.class);
        private final MessageDigest events;
        private final MessageDigest pcm;

        Oracle() throws Exception {
            events = MessageDigest.getInstance("SHA-256");
            pcm = MessageDigest.getInstance("SHA-256");
        }

        void add(String value) {
            events.update((value + "\n").getBytes(StandardCharsets.UTF_8));
        }

        void state(SmpsDriverSnapshot state) {
            add(json.valueToTree(state).toString());
        }

        void pcm(short[] samples) {
            var bytes = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short sample : samples) bytes.putShort(sample);
            pcm.update(bytes.array());
        }

        @Override public void onYm2612Write(int port, int register, int value) {
            add("ym:" + port + ":" + register + ":" + value);
        }
        @Override public void onPsgWrite(int value) { add("psg:" + value); }
        @Override public void onServiceBegin(ServiceEvent event) { add("begin:" + event); }
        @Override public void onServiceEnd(ServiceEvent event, SmpsDriverSnapshot snapshot) {
            add("end:" + event);
            state(snapshot);
        }
        @Override public void onLifecycle(LifecycleEvent event) { add("lifecycle:" + event); }

        String finish() {
            return HexFormat.of().formatHex(pcm.digest()) + ":"
                    + HexFormat.of().formatHex(events.digest());
        }
    }
}
