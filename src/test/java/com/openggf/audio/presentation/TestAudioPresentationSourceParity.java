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
import com.openggf.audio.smps.SmpsProgramView;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAudioPresentationSourceParity {
    private static final int SAMPLE_RATE = 48_000;
    private static final int MAX_FRAMES = 2_048;
    private static final DacData EMPTY_DAC =
            new DacData(Map.of(), Map.of(), 288);

    @Test
    void immutableRuntimeViewsExposeIndexedReadsInsteadOfRawArrays() {
        assertEquals(List.of(), publicRawArrayReturns(
                DacData.class, DacData.Sample.class, SmpsProgramView.class));
    }

    private static List<String> publicRawArrayReturns(Class<?>... owners) {
        return Arrays.stream(owners)
                .flatMap(owner -> Arrays.stream(owner.getMethods()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getReturnType().isArray()
                        || Map.class.isAssignableFrom(method.getReturnType()))
                .map(Method::toGenericString)
                .sorted()
                .toList();
    }

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
        fixture.factory.registerSmpsSfxAsset(
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
    void frozenProgramSurvivesLoaderAndPublicMutationAcrossRewindRestore() {
        MutableParitySmpsData source = MutableParitySmpsData.complete();
        FactoryFixture fixture = factoryFixture();
        AudioPresentationCommand.MusicVoiceEntry entry =
                fixture.factory.musicSmps(
                        "base", 0x91, 12, source, EMPTY_DAC,
                        indexedConfig(),
                        AudioSourceDescriptor.baseMusic(0x91), MAX_FRAMES);

        source.mutateOwnedInputs();
        SmpsCompositeVoice first = fixture.factory.recreateSmps(
                (AudioPresentationCommand.SmpsVoiceDescriptor)
                        entry.voiceDescriptor());
        SmpsCompositeVoice second = fixture.factory.recreateSmps(
                (AudioPresentationCommand.SmpsVoiceDescriptor)
                        entry.voiceDescriptor());
        SmpsSequencer firstSequencer =
                first.driver().firstMusicSequencer();
        SmpsSequencer secondSequencer =
                second.driver().firstMusicSequencer();
        AbstractSmpsData frozen = firstSequencer.getSmpsData();
        mutateFrozenPublicCopies(frozen);

        assertFrozenProgram(frozen);
        PresentationVoiceSnapshot.Smps snapshot =
                (PresentationVoiceSnapshot.Smps) first.snapshot();
        firstSequencer.getTracks().get(0).pos = 6;
        SmpsCompositeVoice recreated = fixture.factory.recreateSmps(snapshot);
        SmpsSequencer recreatedSequencer =
                recreated.driver().firstMusicSequencer();

        assertSame(frozen, secondSequencer.getSmpsData());
        assertSame(frozen, recreatedSequencer.getSmpsData());
        assertSame(firstSequencer.programView(),
                secondSequencer.programView());
        assertSame(firstSequencer.programView(),
                recreatedSequencer.programView());
        assertSame(firstSequencer.getDacData(),
                secondSequencer.getDacData());
        assertSame(firstSequencer.getDacData(),
                recreatedSequencer.getDacData());
        assertSame(firstSequencer.getSourceDescriptor(),
                secondSequencer.getSourceDescriptor());
        assertSame(firstSequencer.getSourceDescriptor(),
                recreatedSequencer.getSourceDescriptor());

        assertNotSame(firstSequencer, secondSequencer);
        assertNotSame(firstSequencer, recreatedSequencer);
        assertNotSame(firstSequencer.getTracks(),
                secondSequencer.getTracks());
        assertNotSame(firstSequencer.getTracks(),
                recreatedSequencer.getTracks());
        assertEquals(2, firstSequencer.getTracks().size());
        assertEquals(2, secondSequencer.getTracks().size());
        assertEquals(2, recreatedSequencer.getTracks().size());
        assertNotSame(firstSequencer.getTracks().get(0),
                secondSequencer.getTracks().get(0));
        assertNotSame(firstSequencer.getTracks().get(0),
                recreatedSequencer.getTracks().get(0));
        assertEquals(1, secondSequencer.getTracks().get(0).pos);
        assertEquals(1, recreatedSequencer.getTracks().get(0).pos);
        assertEquals(6, firstSequencer.getTracks().get(0).pos);
        assertFrozenProgram(recreatedSequencer.getSmpsData());
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

    private static SmpsSequencerConfig indexedConfig() {
        return new SmpsSequencerConfig.Builder()
                .fmChannelOrder(new int[] {0})
                .psgChannelOrder(new int[] {0x80})
                .build();
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

    private static void mutateFrozenPublicCopies(AbstractSmpsData frozen) {
        frozen.getData()[0] = 0x55;
        frozen.getFmPointers()[0] = 0x55;
        frozen.getFmKeyOffsets()[0] = 0x55;
        frozen.getFmVolumeOffsets()[0] = 0x55;
        frozen.getPsgPointers()[0] = 0x55;
        frozen.getPsgKeyOffsets()[0] = 0x55;
        frozen.getPsgVolumeOffsets()[0] = 0x55;
        frozen.getPsgModEnvs()[0] = 0x55;
        frozen.getPsgInstruments()[0] = 0x55;
        frozen.getVoice(7)[0] = 0x55;
        frozen.getPsgEnvelope(8)[0] = 0x55;
        frozen.getModEnvelope(9)[0] = 0x55;
        assertThrows(UnsupportedOperationException.class,
                () -> frozen.setId(0x55));
        assertThrows(UnsupportedOperationException.class,
                () -> frozen.setPalSpeedupDisabled(false));
    }

    private static void assertFrozenProgram(AbstractSmpsData frozen) {
        assertArrayEquals(MutableParitySmpsData.PROGRAM,
                frozen.getData());
        assertArrayEquals(new int[] {1}, frozen.getFmPointers());
        assertArrayEquals(new int[] {11}, frozen.getFmKeyOffsets());
        assertArrayEquals(new int[] {12}, frozen.getFmVolumeOffsets());
        assertArrayEquals(new int[] {8}, frozen.getPsgPointers());
        assertArrayEquals(new int[] {13}, frozen.getPsgKeyOffsets());
        assertArrayEquals(new int[] {14}, frozen.getPsgVolumeOffsets());
        assertArrayEquals(new int[] {15}, frozen.getPsgModEnvs());
        assertArrayEquals(new int[] {16}, frozen.getPsgInstruments());
        assertArrayEquals(new byte[] {17, 18}, frozen.getVoice(7));
        assertArrayEquals(new byte[] {19, 20}, frozen.getPsgEnvelope(8));
        assertArrayEquals(new byte[] {21, 22}, frozen.getModEnvelope(9));
        assertEquals(0x91, frozen.getId());
        assertTrue(frozen.isPalSpeedupDisabled());

        SmpsProgramView view = frozen;
        assertEquals(MutableParitySmpsData.PROGRAM.length,
                view.dataLength());
        assertEquals(0xF2, view.dataByteAt(1) & 0xFF);
        assertEquals(1, view.fmPointerCount());
        assertEquals(1, view.fmPointerAt(0));
        assertEquals(11, view.fmKeyOffsetAt(0));
        assertEquals(12, view.fmVolumeOffsetAt(0));
        assertEquals(1, view.psgPointerCount());
        assertEquals(8, view.psgPointerAt(0));
        assertEquals(13, view.psgKeyOffsetAt(0));
        assertEquals(14, view.psgVolumeOffsetAt(0));
        assertEquals(15, view.psgModEnvelopeAt(0));
        assertEquals(16, view.psgInstrumentAt(0));
        assertEquals(2, view.voiceLength(7));
        assertEquals(17, view.voiceByteAt(7, 0));
        assertEquals(2, view.psgEnvelopeLength(8));
        assertEquals(19, view.psgEnvelopeByteAt(8, 0));
        assertEquals(2, view.modEnvelopeLength(9));
        assertEquals(21, view.modEnvelopeByteAt(9, 0));
    }

    private static final class MutableParitySmpsData
            extends AbstractSmpsData {
        private static final byte[] PROGRAM = {
                1, (byte) 0xF2, 0, 0, 0, 0, 0, 0,
                (byte) 0xF2, 0, 0, 0, 0, 0, 0, 0
        };
        private final byte[] voice;
        private final byte[] psgEnvelope;
        private final byte[] modEnvelope;

        private MutableParitySmpsData(
                byte[] data,
                int[] fmPointers,
                int[] fmKeyOffsets,
                int[] fmVolumeOffsets,
                int[] psgPointers,
                int[] psgKeyOffsets,
                int[] psgVolumeOffsets,
                int[] psgModEnvs,
                int[] psgInstruments,
                byte[] voice,
                byte[] psgEnvelope,
                byte[] modEnvelope) {
            super(data, 0);
            this.fmPointers = fmPointers;
            this.fmKeyOffsets = fmKeyOffsets;
            this.fmVolumeOffsets = fmVolumeOffsets;
            this.psgPointers = psgPointers;
            this.psgKeyOffsets = psgKeyOffsets;
            this.psgVolumeOffsets = psgVolumeOffsets;
            this.psgModEnvs = psgModEnvs;
            this.psgInstruments = psgInstruments;
            this.voice = voice;
            this.psgEnvelope = psgEnvelope;
            this.modEnvelope = modEnvelope;
            setId(0x91);
            setPalSpeedupDisabled(true);
        }

        private static MutableParitySmpsData complete() {
            return new MutableParitySmpsData(
                    PROGRAM.clone(),
                    new int[] {1}, new int[] {11}, new int[] {12},
                    new int[] {8}, new int[] {13}, new int[] {14},
                    new int[] {15}, new int[] {16},
                    new byte[] {17, 18}, new byte[] {19, 20},
                    new byte[] {21, 22});
        }

        private void mutateOwnedInputs() {
            data[0] = 0x66;
            fmPointers[0] = 0x66;
            fmKeyOffsets[0] = 0x66;
            fmVolumeOffsets[0] = 0x66;
            psgPointers[0] = 0x66;
            psgKeyOffsets[0] = 0x66;
            psgVolumeOffsets[0] = 0x66;
            psgModEnvs[0] = 0x66;
            psgInstruments[0] = 0x66;
            voice[0] = 0x66;
            psgEnvelope[0] = 0x66;
            modEnvelope[0] = 0x66;
            setId(0x66);
            setPalSpeedupDisabled(false);
        }

        @Override protected void parseHeader() { }
        @Override public byte[] getVoice(int voiceId) {
            return voiceId == 0 || voiceId == 7 ? voice : null;
        }
        @Override public byte[] getPsgEnvelope(int id) {
            return id == 8 ? psgEnvelope : null;
        }
        @Override public byte[] getModEnvelope(int id) {
            return id == 9 ? modEnvelope : null;
        }
        @Override public int read16(int offset) {
            return ((data[offset] & 0xFF) << 8)
                    | (data[offset + 1] & 0xFF);
        }
        @Override public int getBaseNoteOffset() { return 0; }
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
        @Override protected void hookPlayWavSfx(
                String sfxName, float pitch) { }
        @Override protected void hookCleanupStoppedWavSfx() { }
        @Override protected void hookPause() { }
        @Override protected void hookResume() { }
    }
}
