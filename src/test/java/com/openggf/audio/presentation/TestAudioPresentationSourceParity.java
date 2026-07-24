package com.openggf.audio;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.presentation.AudioPresentationCommand;
import com.openggf.audio.presentation.AudioPresentationMixer;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.DecodedPcm;
import com.openggf.audio.presentation.DecodedPcmCache;
import com.openggf.audio.presentation.PresentationVoice;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.presentation.PresentationVoiceSource;
import com.openggf.audio.presentation.SampleBackedVoice;
import com.openggf.audio.presentation.SmpsAssetKey;
import com.openggf.audio.presentation.SmpsCompositeVoice;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAudioPresentationSourceParity {
    private static final int SAMPLE_RATE = 48_000;
    private static final int MAX_FRAMES = 2_048;
    private static final DacData EMPTY_DAC =
            new DacData(Map.of(), Map.of(), 288);

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
    }

    @Test
    void smpsMusicSmpsSfxWavSfxAndRawPcmAppearInOnePacket()
            throws Exception {
        FactoryFixture fixture = factoryFixture();
        AbstractSmpsData music = data("music", 0x81);
        AudioPresentationCommand.MusicVoiceEntry musicEntry =
                fixture.factory.musicSmps(
                        "base", 0x81, 1, music, EMPTY_DAC,
                        config(), AudioSourceDescriptor.baseMusic(0x81),
                        MAX_FRAMES);
        AudioVoiceRegistry registry = fixture.registry();
        registry.apply(new AudioPresentationCommand.ReplaceMusic(musicEntry));
        SmpsCompositeVoice composite =
                (SmpsCompositeVoice) registry.orderedVoiceAt(0);
        primeSynth(composite.driver());

        AbstractSmpsData sfx = data("sfx", 0xA0);
        SmpsAssetKey key = new SmpsAssetKey(
                "base", SmpsAssetKey.Route.BASE_ID, 0xA0, null);
        fixture.factory.warmSmpsSfxAsset(
                key, sfx, EMPTY_DAC, config());
        registry.apply(new AudioPresentationCommand.AddSmpsSfx(
                fixture.factory.resolveSmpsSfx(
                        2, key, 1 << 16, 0x70, 0, 0, MAX_FRAMES)));

        SampleBackedVoice wav = fixture.factory.fallbackSfx(
                3, "JUMP", 3, 1.0f);
        registry.apply(AudioPresentationCommand.StartSampleSfx.fromVoice(wav));
        DecodedPcm rawPcm = fixture.factory.registerUnsigned8Mono(
                "sega", new byte[] {(byte) 0xFF, 0}, SAMPLE_RATE);
        registry.apply(AudioPresentationCommand.ReplaceRawPcm.fromVoice(
                fixture.factory.segaPcm(4, rawPcm)));

        assertEquals(2, composite.driver().captureSnapshot()
                .sequencers().size());
        short[] packet =
                new AudioPresentationMixer(2).mix(registry, 2).clone();

        assertEquals(3, registry.orderedVoiceCount());
        assertNotEquals(0, packet[0]);
        assertNotEquals(0, packet[1]);
        assertFalse(ArraysAreAllZero(packet));
    }

    @Test
    void fallbackMusicLoopsWhileMultiplePitchedWavSfxCompleteIndependently()
            throws Exception {
        FactoryFixture fixture = factoryFixture();
        AudioVoiceRegistry registry = fixture.registry();
        registry.apply(new AudioPresentationCommand.ReplaceMusic(
                fixture.factory.fallbackMusic(
                        1, 0x71,
                        AudioSourceDescriptor.fallbackMusic(0x71))));
        registry.apply(AudioPresentationCommand.StartSampleSfx.fromVoice(
                fixture.factory.fallbackSfx(2, "JUMP", 1, 1.0f)));
        registry.apply(AudioPresentationCommand.StartSampleSfx.fromVoice(
                fixture.factory.fallbackSfx(3, "SKID", 1, 2.0f)));

        new AudioPresentationMixer(8).mix(registry, 8);
        PresentationVoiceSnapshot.Sample music =
                (PresentationVoiceSnapshot.Sample)
                        registry.orderedVoiceAt(0).snapshot();
        PresentationVoiceSnapshot.Sample slower =
                (PresentationVoiceSnapshot.Sample)
                        registry.orderedVoiceAt(1).snapshot();
        PresentationVoiceSnapshot.Sample faster =
                (PresentationVoiceSnapshot.Sample)
                        registry.orderedVoiceAt(2).snapshot();

        assertTrue(music.looping());
        assertFalse(music.stopped());
        assertFalse(registry.orderedVoiceAt(0).isComplete());
        assertTrue(faster.sourcePositionQ32()
                >= slower.sourcePositionQ32());
        assertTrue(registry.orderedVoiceAt(2).isComplete());
    }

    @Test
    void segaPcmReplacementAndStopPreserveExistingRules() {
        FactoryFixture fixture = factoryFixture();
        AudioVoiceRegistry registry = fixture.registry();
        SampleBackedVoice first = fixture.factory.segaPcm(
                1, fixture.factory.registerUnsigned8Mono(
                        "sega-a", new byte[] {0, (byte) 0x80},
                        SAMPLE_RATE));
        SampleBackedVoice second = fixture.factory.segaPcm(
                2, fixture.factory.registerUnsigned8Mono(
                        "sega-b", new byte[] {(byte) 0xFF},
                        SAMPLE_RATE));
        registry.apply(AudioPresentationCommand.ReplaceRawPcm.fromVoice(first));
        PresentationVoice ownedFirst = registry.orderedVoiceAt(0);
        registry.apply(AudioPresentationCommand.ReplaceRawPcm.fromVoice(second));

        assertTrue(ownedFirst.isComplete());
        assertFalse(first.isComplete(),
                "command submitter retains no mutable registry ownership");
        assertEquals(1, registry.orderedVoiceCount());
        assertEquals(2, registry.orderedVoiceAt(0).voiceId());
        PresentationVoice ownedSecond = registry.orderedVoiceAt(0);

        registry.apply(new AudioPresentationCommand.StopRawPcm());

        assertEquals(0, registry.orderedVoiceCount());
        assertTrue(ownedSecond.isComplete());
        assertFalse(second.isComplete(),
                "command submitter retains no mutable registry ownership");
    }

    @Test
    void musicOverrideStackRestoresCompositeAndLoopingSampleCursor()
            throws Exception {
        FactoryFixture fixture = factoryFixture();
        AudioVoiceRegistry registry = fixture.registry();
        AudioPresentationCommand.MusicVoiceEntry base =
                fixture.factory.fallbackMusic(
                        1, 0x71,
                        AudioSourceDescriptor.fallbackMusic(0x71));
        registry.apply(new AudioPresentationCommand.ReplaceMusic(base));
        new AudioPresentationMixer(2).mix(registry, 2);
        long cursor = ((PresentationVoiceSnapshot.Sample)
                registry.orderedVoiceAt(0).snapshot()).sourcePositionQ32();

        AudioPresentationCommand.MusicVoiceEntry override =
                fixture.factory.musicSmps(
                        "base", 0x91, 2, data("override", 0x91),
                        EMPTY_DAC, config(),
                        AudioSourceDescriptor.baseMusic(0x91), MAX_FRAMES);
        registry.apply(new AudioPresentationCommand.PushMusicOverride(
                override));
        assertTrue(registry.orderedVoiceAt(0)
                instanceof SmpsCompositeVoice);

        registry.apply(new AudioPresentationCommand.RestoreMusicOverride());

        PresentationVoice restored = registry.orderedVoiceAt(0);
        assertTrue(restored instanceof SampleBackedVoice);
        assertEquals(cursor, ((PresentationVoiceSnapshot.Sample)
                restored.snapshot()).sourcePositionQ32());
    }

    @Test
    void thirtyThreeSampleSfxObeyPriorityAdmission() {
        FactoryFixture fixture = factoryFixture();
        AudioVoiceRegistry registry = fixture.registry();
        for (int index = 0; index < 32; index++) {
            registry.apply(AudioPresentationCommand.StartSampleSfx.fromVoice(
                    sample(fixture, index + 1, 10, "sample-" + index)));
        }
        SampleBackedVoice rejected =
                sample(fixture, 40, 9, "rejected");
        registry.apply(AudioPresentationCommand.StartSampleSfx.fromVoice(
                rejected));
        assertEquals(32, registry.orderedVoiceCount());
        assertFalse(rejected.isComplete(),
                "a rejected descriptor must not materialize its caller voice");

        registry.apply(AudioPresentationCommand.StartSampleSfx.fromVoice(
                sample(fixture, 41, 11, "admitted")));

        assertEquals(32, registry.orderedVoiceCount());
        assertTrue(containsVoice(registry, 41));
        assertFalse(containsVoice(registry, 1));
    }

    @Test
    void malformedVoiceDoesNotStopOtherVoices() {
        AtomicInteger failures = new AtomicInteger();
        SampleBackedVoice healthy = SampleBackedVoice.oneShot(
                2, 0,
                new DecodedPcm("healthy", 1, SAMPLE_RATE,
                        new short[] {321}),
                SAMPLE_RATE, 1.0f, 1.0f);
        PresentationVoice malformed = new PresentationVoice() {
            @Override public long voiceId() { return 1; }
            @Override public int priority() { return 0; }
            @Override public void mixInto(long[] accumulation, int frames) {
                throw new IllegalStateException("malformed");
            }
            @Override public boolean isComplete() { return false; }
            @Override public void stop() { }
            @Override public PresentationVoiceSnapshot snapshot() {
                return healthy.snapshot();
            }
        };
        PresentationVoiceSource voices = new PresentationVoiceSource() {
            @Override public int orderedVoiceCount() { return 2; }
            @Override public PresentationVoice orderedVoiceAt(int index) {
                return index == 0 ? malformed : healthy;
            }
        };

        short[] mixed = new AudioPresentationMixer(
                1, ignored -> failures.incrementAndGet()).mix(voices, 1);

        assertArrayEquals(new short[] {321, 321}, mixed);
        assertEquals(1, failures.get());
        assertTrue(healthy.isComplete());
    }

    @Test
    void legacyAndPresentationSmpsDriversRenderTheSameFirstTenPackets() {
        SonicConfigurationService configuration =
                SonicConfigurationService.createStandalone();
        configuration.setSessionOverride(
                SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT, false);
        configuration.setSessionOverride(
                SonicConfiguration.REGION, "NTSC");
        configuration.setSessionOverride(
                SonicConfiguration.DAC_INTERPOLATE, true);
        configuration.setSessionOverride(
                SonicConfiguration.FM6_DAC_OFF, false);
        configuration.setSessionOverride(
                SonicConfiguration.PSG_NOISE_SHIFT_EVERY_TOGGLE, true);
        NoDeviceBackend legacy = new NoDeviceBackend(configuration);
        legacy.init();
        legacy.setAudioProfile(new AudioTestFixtures.StubAudioProfile(
                new AudioTestFixtures.StubSmpsLoader()) {
            @Override public SmpsSequencerConfig getSequencerConfig() {
                return config();
            }
        });
        AbstractSmpsData source = data("parity", 0x81);
        legacy.prepareLogicalMusicSource(
                AudioSourceDescriptor.baseMusic(0x81));
        legacy.playSmps(source, EMPTY_DAC, config(), false);
        SmpsDriver legacyDriver = legacy.musicDriverForTesting();

        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(
                        () -> true,
                        new SmpsCoordFlagHandlerOwner(
                                new SmpsCoordFlagRuntimeState()),
                        new AudioPresentationSourceFactory.Settings(
                                SAMPLE_RATE, SmpsSequencer.Region.NTSC,
                                true, true, false, false, 1,
                                AudioManager.getInstance(),
                                new DecodedPcmCache(),
                                ignored -> null));
        AudioPresentationCommand.MusicVoiceEntry entry =
                factory.musicSmps(
                        "base", 0x81, 1, source, EMPTY_DAC, config(),
                        AudioSourceDescriptor.baseMusic(0x81), MAX_FRAMES);
        SmpsDriver presentation = factory.recreateSmps(
                (AudioPresentationCommand.SmpsVoiceDescriptor)
                        entry.voiceDescriptor()).driver();
        primeSynth(legacyDriver);
        primeSynth(presentation);

        int[] packetFrames = {1, 7, 31, 128, 3, 256, 64, 511, 2, 1024};
        for (int frames : packetFrames) {
            short[] expected = new short[frames * 2];
            short[] actual = new short[frames * 2];
            assertEquals(expected.length,
                    legacyDriver.read(expected, expected.length));
            assertEquals(actual.length,
                    presentation.read(actual, actual.length));
            assertArrayEquals(expected, actual);
        }
    }

    @Test
    void legacyS3kMusicAndSfxShareBackendPrivateCoordHandler() {
        SonicConfigurationService configuration =
                SonicConfigurationService.createStandalone();
        NoDeviceBackend legacy = new NoDeviceBackend(configuration);
        legacy.init();
        legacy.setAudioProfile(new AudioTestFixtures.StubAudioProfile(
                new AudioTestFixtures.StubSmpsLoader()) {
            @Override public SmpsSequencerConfig getSequencerConfig() {
                return Sonic3kSmpsSequencerConfig.CONFIG;
            }
        });

        legacy.playSmps(data("music", 0x81), EMPTY_DAC,
                Sonic3kSmpsSequencerConfig.CONFIG, false);
        legacy.playSfxSmps(data("sfx", 0xA0), EMPTY_DAC, 1.0f,
                Sonic3kSmpsSequencerConfig.CONFIG);

        List<com.openggf.audio.rewind.SmpsDriverSnapshot.SequencerEntry>
                sequencers =
                legacy.musicDriverForTesting().captureSnapshot().sequencers();
        assertEquals(2, sequencers.size());
        assertSame(sequencers.get(0).config().getCoordFlagHandler(),
                sequencers.get(1).config().getCoordFlagHandler());
        assertNotEquals(Sonic3kSmpsSequencerConfig.CONFIG
                        .getCoordFlagHandler(),
                sequencers.get(0).config().getCoordFlagHandler());
    }

    @Test
    void legacyAndPresentationRawPcmRenderTheSameCursorSequence() {
        byte[] source = {
                0, 32, 64, 96, (byte) 128, (byte) 192, (byte) 255
        };
        PcmSampleStream legacy =
                new PcmSampleStream(source, SAMPLE_RATE, SAMPLE_RATE);
        SampleBackedVoice presentation =
                SampleBackedVoice.unsigned8Mono(
                        1, 0, "sega", source,
                        SAMPLE_RATE, SAMPLE_RATE, 0.25f);

        for (int frames : new int[] {1, 2, 3, 5, 8, 13}) {
            short[] expected = new short[frames * 2];
            long[] actualWide = new long[frames * 2];
            legacy.read(expected, expected.length);
            presentation.mixInto(actualWide, frames);
            short[] actual = new short[actualWide.length];
            for (int index = 0; index < actual.length; index++) {
                actual[index] = (short) actualWide[index];
            }
            assertArrayEquals(expected, actual);
            assertEquals(legacy.isComplete(), presentation.isComplete());
        }
    }

    @Test
    void legacyFallbackWavMetadataMatchesDecodedPresentationAsset()
            throws Exception {
        byte[] wav = wav(new short[] {100, -100, 300, -300},
                2, 22_050);
        WavDecoder legacy = WavDecoder.decode(
                new ByteArrayInputStream(wav));
        FactoryFixture fixture = factoryFixture(
                ignored -> new ByteArrayInputStream(wav));
        SampleBackedVoice voice =
                fixture.factory.fallbackSfx(1, "JUMP", 2, 1.0f);
        PresentationVoiceSnapshot.Sample snapshot =
                (PresentationVoiceSnapshot.Sample) voice.snapshot();
        DecodedPcm decoded =
                fixture.factory.resolvePcm(snapshot.assetId());

        assertEquals(legacy.channels, decoded.channels());
        assertEquals(legacy.sampleRate, decoded.sampleRate());
        assertEquals(legacy.data.length / 2, decoded.copySamples().length);
        assertEquals("sfx/jump.wav", decoded.assetId());
        assertEquals(2, snapshot.priority());
    }

    private static SampleBackedVoice sample(
            FactoryFixture fixture,
            long voiceId,
            int priority,
            String assetId) {
        DecodedPcm pcm = fixture.factory.registerUnsigned8Mono(
                assetId, new byte[] {(byte) 0x80, (byte) 0x81},
                SAMPLE_RATE);
        return SampleBackedVoice.oneShot(
                voiceId, priority, pcm, SAMPLE_RATE, 1.0f, 1.0f);
    }

    private static boolean containsVoice(
            AudioVoiceRegistry registry, long voiceId) {
        for (int index = 0; index < registry.orderedVoiceCount(); index++) {
            if (registry.orderedVoiceAt(index).voiceId() == voiceId) {
                return true;
            }
        }
        return false;
    }

    private static FactoryFixture factoryFixture() {
        return factoryFixture(ignored -> new ByteArrayInputStream(
                wav(new short[] {100, 200, 300, 400}, 1, SAMPLE_RATE)));
    }

    private static FactoryFixture factoryFixture(
            AudioPresentationSourceFactory.WavAssets assets) {
        SmpsCoordFlagHandlerOwner handlers =
                new SmpsCoordFlagHandlerOwner(
                        new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(
                        () -> true, handlers,
                        new AudioPresentationSourceFactory.Settings(
                                SAMPLE_RATE, SmpsSequencer.Region.NTSC,
                                false, false, false, false, 1,
                                AudioManager.getInstance(),
                                new DecodedPcmCache(), assets));
        return new FactoryFixture(factory, handlers);
    }

    private record FactoryFixture(
            AudioPresentationSourceFactory factory,
            SmpsCoordFlagHandlerOwner handlers) {
        AudioVoiceRegistry registry() {
            return new AudioVoiceRegistry(
                    factory, factory, handlers, ignored -> {
                    });
        }
    }

    private static AbstractSmpsData data(String name, int id) {
        AbstractSmpsData data =
                new AudioTestFixtures.StubSmpsData(name);
        data.setId(id);
        return data;
    }

    private static SmpsSequencerConfig config() {
        return new SmpsSequencerConfig.Builder().build();
    }

    private static boolean ArraysAreAllZero(short[] samples) {
        for (short sample : samples) {
            if (sample != 0) {
                return false;
            }
        }
        return true;
    }

    private static byte[] wav(
            short[] samples, int channels, int sampleRate) {
        int dataBytes = samples.length * Short.BYTES;
        ByteBuffer out = ByteBuffer.allocate(44 + dataBytes)
                .order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.putInt(36 + dataBytes);
        out.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        out.putInt(16);
        out.putShort((short) 1);
        out.putShort((short) channels);
        out.putInt(sampleRate);
        out.putInt(sampleRate * channels * Short.BYTES);
        out.putShort((short) (channels * Short.BYTES));
        out.putShort((short) 16);
        out.put("data".getBytes(StandardCharsets.US_ASCII));
        out.putInt(dataBytes);
        for (short sample : samples) {
            out.putShort(sample);
        }
        return out.array();
    }

    private static void primeSynth(SmpsDriver driver) {
        driver.setDacData(new DacData(
                Map.of(1, new byte[] {0, 24, 64, 127}),
                Map.of(0x81, new DacData.DacEntry(1, 4)), 295));
        driver.setDacInterpolate(true);
        driver.writeFm(driver, 0, 0x22, 0x0B);
        driver.writeFm(driver, 0, 0x2B, 0x80);
        driver.setInstrument(driver, 0, new byte[] {
                0x32, 0x71, 0x0D, 0x33, 0x01, 0x5F, 0x5F, 0x5F,
                0x5F, 0x14, 0x0E, 0x0E, 0x0E, 0x08, 0x08, 0x08,
                0x08, 0x0F, 0x0F, 0x0F, 0x0F, 0x1B, 0x16, 0x1F,
                0x00
        });
        driver.writeFm(driver, 0, 0xA4, 0x22);
        driver.writeFm(driver, 0, 0xA0, 0x69);
        driver.writeFm(driver, 0, 0xB4, 0xC7);
        driver.writeFm(driver, 0, 0x28, 0xF0);
        driver.playDac(driver, 0x81);
        driver.writePsg(driver, 0x84);
        driver.writePsg(driver, 0x12);
        driver.writePsg(driver, 0x92);
    }

    private static final class NoDeviceBackend
            extends AbstractSmpsAudioBackend {
        private NoDeviceBackend(SonicConfigurationService configuration) {
            super(configuration, null);
        }

        @Override protected int getDeviceSampleRate() { return SAMPLE_RATE; }
        @Override protected void hookInitDevice() { }
        @Override protected void hookDestroyDevice() { }
        @Override protected void hookStartStream() { }
        @Override protected void hookStopStreamSource() { }
        @Override protected void hookUpdateStream() { }
        @Override protected void hookStopAndClearMusicSource() { }
        @Override protected void hookStopAndUnqueueAllMusicBuffers() { }
        @Override protected void hookStopAndClearAllMusicBuffers() { }
        @Override protected void hookRestartStreamIfDry() { }
        @Override protected void hookStopAndDeleteWavSfxSources() { }
        @Override protected void hookUploadStreamBuffer(
                int bufferId, short[] pcm, int sampleRate) { }
        @Override protected void hookPlayWavSfx(
                String sfxName, float pitch) { }
        @Override protected void hookCleanupStoppedWavSfx() { }
        @Override protected void hookPause() { }
        @Override protected void hookResume() { }
    }
}
