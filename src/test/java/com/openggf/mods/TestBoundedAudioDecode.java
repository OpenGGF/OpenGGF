package com.openggf.mods;

import com.openggf.io.ModInputLimits;
import com.openggf.audio.WavDecoder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestBoundedAudioDecode {
    @Test
    void pcmOwnershipAndDefensiveAccessArePinned() {
        short[] owned = {1, -2, 3, -4};
        PcmData pcm = PcmData.takeOwnership(8_000, 2, owned);
        owned[0] = 7;

        assertEquals(8_000, pcm.sampleRate());
        assertEquals(2, pcm.channels());
        assertEquals(4, pcm.sampleCount());
        assertEquals(7, pcm.sampleAt(0));
        short[] copy = pcm.copySamples();
        copy[0] = 99;
        assertEquals(7, pcm.sampleAt(0));
        assertThrows(IllegalArgumentException.class, () -> PcmData.takeOwnership(7_999, 1, new short[] {1}));
        assertThrows(IllegalArgumentException.class, () -> PcmData.takeOwnership(8_000, 3, new short[] {1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> PcmData.takeOwnership(8_000, 2, new short[] {1}));
        assertThrows(IllegalArgumentException.class, () -> PcmData.takeOwnership(8_000, 1, new short[0]));
    }

    @Test
    void wavPcm16HappyPathAndHostileStructureValidation() throws Exception {
        PcmDecoder decoder = new PcmDecoder();
        byte[] valid = wav(8_000, 1, 16, new short[] {-32768, 0, 32767}, true);
        PcmData pcm = decoder.decode("audio/test.wav", valid, ModInputLimits.production());
        assertArrayEquals(new short[] {-32768, 0, 32767}, pcm.copySamples());

        assertThrows(IOException.class, () -> decoder.decode("audio/bad.wav", new byte[11], ModInputLimits.production()));
        byte[] unsigned8 = wav8(8_000, new byte[] {0, (byte) 128, (byte) 255});
        assertArrayEquals(new short[] {-32768, 0, 32512},
                decoder.decode("audio/8bit.wav", unsigned8, ModInputLimits.production()).copySamples());
        WavDecoder legacy = WavDecoder.decode(new ByteArrayInputStream(unsigned8));
        assertEquals(8, legacy.bitsPerSample);
        assertArrayEquals(new byte[] {0, (byte) 128, (byte) 255}, legacy.data);
        assertThrows(IOException.class, () -> WavDecoder.decode(new ByteArrayInputStream(valid), 16));
        ModInputLimits normalizedTooLarge = ModInputLimits.loweringBuilder().maxAudioTrackPcmBytes(5).build();
        assertThrows(IOException.class,
                () -> decoder.decode("audio/8bit.wav", unsigned8, normalizedTooLarge));
        ModInputLimits oneSecond = ModInputLimits.loweringBuilder().maxAudioTrackSeconds(1).build();
        assertThrows(IOException.class, () -> decoder.decode("audio/long.wav",
                wav(8_000, 1, 16, new short[8_001], false), oneSecond));
        assertThrows(IOException.class, () -> decoder.decode("audio/bad.wav",
                wav(8_000, 1, 24, new short[] {1}, false), ModInputLimits.production()));
        assertThrows(IOException.class, () -> decoder.decode("audio/bad.wav",
                wav(7_999, 1, 16, new short[] {1}, false), ModInputLimits.production()));
        assertThrows(IOException.class, () -> decoder.decode("audio/bad.wav",
                wav(8_000, 3, 16, new short[] {1, 2, 3}, false), ModInputLimits.production()));
        byte[] truncated = java.util.Arrays.copyOf(valid, valid.length - 1);
        assertThrows(IOException.class,
                () -> decoder.decode("audio/bad.wav", truncated, ModInputLimits.production()));
    }

    @Test
    void wavHostileHeaderMatrixRejectsUnsignedSizesOrderingDuplicatesAndInconsistentFormat() {
        byte[] base = wav(8_000, 1, 16, new short[] {1, 2, 3}, false);
        List<byte[]> hostile = new ArrayList<>();
        hostile.add(withInt(base, 0, 0x58464952)); // RIFX
        hostile.add(withInt(base, 0, 0x34364652)); // RF64
        hostile.add(withInt(base, 8, 0x21444142)); // not WAVE
        hostile.add(withInt(base, 16, 0x80000000));
        hostile.add(withInt(base, 40, 0xffffffff));
        hostile.add(withInt(base, 12, 0x61746164)); // data before fmt
        hostile.add(withInt(base, 36, 0x20746d66)); // duplicate fmt
        hostile.add(withShort(base, 20, 3)); // IEEE float
        hostile.add(withShort(base, 20, 0xfffe)); // extensible
        hostile.add(withShort(base, 34, 24));
        hostile.add(withShort(base, 34, 32));
        hostile.add(withInt(base, 28, 1));
        hostile.add(withShort(base, 32, 1));

        byte[] duplicateData = java.util.Arrays.copyOf(base, base.length + 10);
        putInt(duplicateData, 4, duplicateData.length - 8);
        putInt(duplicateData, base.length, 0x61746164);
        putInt(duplicateData, base.length + 4, 2);
        hostile.add(duplicateData);

        byte[] missingOddPadding = java.util.Arrays.copyOf(wav8(8_000, new byte[] {1, 2, 3}), 47);
        putInt(missingOddPadding, 4, missingOddPadding.length - 8);
        hostile.add(missingOddPadding);

        byte[] partialHeader = java.util.Arrays.copyOf(base, base.length + 1);
        putInt(partialHeader, 4, partialHeader.length - 8);
        hostile.add(partialHeader);

        byte[] partialPayload = withInt(base, 40, 8);
        hostile.add(partialPayload);

        PcmDecoder decoder = new PcmDecoder();
        for (int index = 0; index < hostile.size(); index++) {
            byte[] sample = hostile.get(index);
            assertThrows(IOException.class,
                    () -> decoder.decode("audio/hostile.wav", sample, ModInputLimits.production()),
                    "hostile WAV case " + index);
        }

        RecordingPcmProbe allocationProbe = new RecordingPcmProbe();
        PcmDecoder observed = new PcmDecoder(allocationProbe);
        ModInputLimits tooSmall = ModInputLimits.loweringBuilder().maxAudioTrackPcmBytes(5).build();
        assertThrows(IOException.class, () -> observed.decode("audio/capped.wav", base, tooSmall));
        assertEquals(0, allocationProbe.allocations);
    }

    @Test
    void generatedCc0OggUsesHandleDecodeAndHonorsLoweredPreflightLimits() throws Exception {
        // Generated silence: ffmpeg anullsrc=r=8000:cl=mono, 0.05s, libvorbis q=0. CC0 test fixture.
        byte[] ogg;
        try (var input = TestBoundedAudioDecode.class.getResourceAsStream(
                "/mods/audio-fixtures/generated-silence-8000-mono.ogg")) {
            assertNotNull(input);
            ogg = input.readAllBytes();
        }
        RecordingProbe probe = new RecordingProbe();
        OggPcmDecoder decoder = new OggPcmDecoder(probe);
        PcmData pcm = decoder.decode(ogg, ModInputLimits.production());
        assertEquals(8_000, pcm.sampleRate());
        assertEquals(1, pcm.channels());
        assertTrue(pcm.sampleCount() > 0);
        assertEquals(1, probe.nativeAllocations);
        assertEquals(1, probe.handlesClosed);
        assertEquals(1, probe.nativeBuffersFreed);
        assertEquals(1, probe.directInputsFreed);

        assertThrows(IOException.class, () -> decoder.decode(new byte[] {1, 2, 3}, ModInputLimits.production()));
        ModInputLimits tooShort = ModInputLimits.loweringBuilder().maxAudioTrackSeconds(1).build();
        assertDoesNotThrow(() -> decoder.decode(ogg, tooShort));
        ModInputLimits tooFewBytes = ModInputLimits.loweringBuilder().maxAudioTrackPcmBytes(32).build();
        RecordingProbe rejectedProbe = new RecordingProbe();
        assertThrows(IOException.class, () -> new OggPcmDecoder(rejectedProbe).decode(ogg, tooFewBytes));
        assertEquals(0, rejectedProbe.nativeAllocations);
        assertEquals(1, rejectedProbe.handlesClosed);
        assertEquals(1, rejectedProbe.directInputsFreed);
        ModInputLimits peakTooSmall = ModInputLimits.loweringBuilder().maxAudioCacheBytes(6_000).build();
        assertThrows(IOException.class, () -> new OggPcmDecoder().decode(ogg, peakTooSmall));
    }

    @Test
    void oggFacadeRejectsMetadataBeforePcmAllocationAndAlwaysReleasesOpenedResources() {
        FakeVorbisApi badRate = new FakeVorbisApi(new OggPcmDecoder.Info(7_999, 1), 2);
        assertThrows(IOException.class, () -> decodeFake(badRate, ModInputLimits.production(), new RecordingProbe()));
        badRate.assertCleanup(1, 0, 1);

        FakeVorbisApi badChannels = new FakeVorbisApi(new OggPcmDecoder.Info(8_000, 2), 2);
        ModInputLimits monoOnly = ModInputLimits.loweringBuilder().maxAudioChannels(1).build();
        assertThrows(IOException.class, () -> decodeFake(badChannels, monoOnly, new RecordingProbe()));
        badChannels.assertCleanup(1, 0, 1);

        FakeVorbisApi empty = new FakeVorbisApi(new OggPcmDecoder.Info(8_000, 1), 0);
        assertThrows(IOException.class, () -> decodeFake(empty, ModInputLimits.production(), new RecordingProbe()));
        empty.assertCleanup(1, 0, 1);

        FakeVorbisApi unopened = new FakeVorbisApi(new OggPcmDecoder.Info(8_000, 1), 2);
        unopened.openHandle = 0;
        assertThrows(IOException.class, () -> decodeFake(unopened, ModInputLimits.production(), new RecordingProbe()));
        unopened.assertCleanup(0, 0, 1);
    }

    @Test
    void oggFacadeRepeatsDecodeAndCleansUpOnZeroThrowAndJavaAllocationFailure() throws Exception {
        FakeVorbisApi repeated = new FakeVorbisApi(new OggPcmDecoder.Info(8_000, 1), 3);
        repeated.maxFramesPerDecode = 1;
        PcmData pcm = decodeFake(repeated, ModInputLimits.production(), new RecordingProbe());
        assertEquals(3, pcm.frameCount());
        assertEquals(3, repeated.decodeCalls);
        repeated.assertCleanup(1, 1, 1);

        FakeVorbisApi earlyZero = new FakeVorbisApi(new OggPcmDecoder.Info(8_000, 1), 2);
        earlyZero.returnZero = true;
        assertThrows(IOException.class, () -> decodeFake(earlyZero, ModInputLimits.production(), new RecordingProbe()));
        earlyZero.assertCleanup(1, 1, 1);

        FakeVorbisApi throwing = new FakeVorbisApi(new OggPcmDecoder.Info(8_000, 1), 2);
        throwing.throwOnDecode = true;
        assertThrows(IllegalStateException.class,
                () -> decodeFake(throwing, ModInputLimits.production(), new RecordingProbe()));
        throwing.assertCleanup(1, 1, 1);

        FakeVorbisApi javaAllocationFailure = new FakeVorbisApi(new OggPcmDecoder.Info(8_000, 1), 2);
        OggPcmDecoder.AllocationProbe rejectingProbe = new RecordingProbe() {
            @Override public void beforeJavaPcmAllocation(long bytes) {
                throw new IllegalStateException("test allocation rejection");
            }
        };
        assertThrows(IllegalStateException.class,
                () -> decodeFake(javaAllocationFailure, ModInputLimits.production(), rejectingProbe));
        javaAllocationFailure.assertCleanup(1, 1, 1);
    }

    @Test
    void oggFacadeCleansUpPreciselyWhenMetadataOrNativeAllocationStagesThrow() {
        FakeVorbisApi infoFailure = new FakeVorbisApi(new OggPcmDecoder.Info(8_000, 1), 2);
        infoFailure.throwOnInfo = true;
        RecordingProbe infoProbe = new RecordingProbe();
        assertThrows(IllegalStateException.class,
                () -> decodeFake(infoFailure, ModInputLimits.production(), infoProbe));
        infoFailure.assertCleanup(1, 0, 1);
        infoProbe.assertAllocations(0, 0);

        FakeVorbisApi lengthFailure = new FakeVorbisApi(new OggPcmDecoder.Info(8_000, 1), 2);
        lengthFailure.throwOnLength = true;
        RecordingProbe lengthProbe = new RecordingProbe();
        assertThrows(IllegalStateException.class,
                () -> decodeFake(lengthFailure, ModInputLimits.production(), lengthProbe));
        lengthFailure.assertCleanup(1, 0, 1);
        lengthProbe.assertAllocations(0, 0);

        FakeVorbisApi allocationFailure = new FakeVorbisApi(new OggPcmDecoder.Info(8_000, 1), 2);
        allocationFailure.throwOnAllocatePcm = true;
        RecordingProbe allocationProbe = new RecordingProbe();
        assertThrows(IllegalStateException.class,
                () -> decodeFake(allocationFailure, ModInputLimits.production(), allocationProbe));
        allocationFailure.assertCleanup(1, 0, 1);
        allocationProbe.assertAllocations(1, 0);

        FakeVorbisApi overDuration = new FakeVorbisApi(new OggPcmDecoder.Info(8_000, 1), 8_001);
        RecordingProbe durationProbe = new RecordingProbe();
        ModInputLimits oneSecond = ModInputLimits.loweringBuilder().maxAudioTrackSeconds(1).build();
        assertThrows(IOException.class, () -> decodeFake(overDuration, oneSecond, durationProbe));
        overDuration.assertCleanup(1, 0, 1);
        durationProbe.assertAllocations(0, 0);
    }

    @Test
    void legacyWavBoundedReadHandlesZeroProgressAndExactOverflowWithoutSpinning() throws Exception {
        byte[] valid = wav8(8_000, new byte[] {0, 1});
        InputStream zeroThenData = new ByteArrayInputStream(valid) {
            boolean zero = true;
            @Override public int read(byte[] bytes, int offset, int length) {
                if (zero) { zero = false; return 0; }
                return super.read(bytes, offset, length);
            }
        };
        assertEquals(8, WavDecoder.decode(zeroThenData, valid.length).bitsPerSample);
        assertThrows(IOException.class,
                () -> WavDecoder.decode(new ByteArrayInputStream(new byte[17]), 16));
        InputStream endlesslyZeroBulk = new InputStream() {
            @Override public int read(byte[] bytes, int offset, int length) { return 0; }
            @Override public int read() { return 0; }
        };
        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertThrows(IOException.class, () -> WavDecoder.decode(endlesslyZeroBulk, 32)));
    }

    private static byte[] wav(int rate, int channels, int bits, short[] samples, boolean oddJunk) {
        int junkBytes = oddJunk ? 10 : 0;
        int dataBytes = samples.length * 2;
        ByteBuffer out = ByteBuffer.allocate(12 + junkBytes + 24 + 8 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46464952).putInt(out.capacity() - 8).putInt(0x45564157);
        if (oddJunk) out.putInt(0x4b4e554a).putInt(1).put((byte) 7).put((byte) 0);
        out.putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(rate).putInt(rate * channels * 2).putShort((short) (channels * 2)).putShort((short) bits);
        out.putInt(0x61746164).putInt(dataBytes);
        for (short sample : samples) out.putShort(sample);
        return out.array();
    }

    private static byte[] wav8(int rate, byte[] samples) {
        int padded = samples.length + (samples.length & 1);
        ByteBuffer out = ByteBuffer.allocate(12 + 24 + 8 + padded).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46464952).putInt(out.capacity() - 8).putInt(0x45564157);
        out.putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate).putShort((short) 1).putShort((short) 8);
        out.putInt(0x61746164).putInt(samples.length).put(samples);
        if ((samples.length & 1) != 0) out.put((byte) 0);
        return out.array();
    }

    private static byte[] withInt(byte[] source, int offset, int value) {
        byte[] copy = source.clone();
        putInt(copy, offset, value);
        return copy;
    }

    private static byte[] withShort(byte[] source, int offset, int value) {
        byte[] copy = source.clone();
        ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, (short) value);
        return copy;
    }

    private static void putInt(byte[] target, int offset, int value) {
        ByteBuffer.wrap(target).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value);
    }

    private static PcmData decodeFake(FakeVorbisApi api, ModInputLimits limits,
                                      OggPcmDecoder.AllocationProbe probe) throws IOException {
        return new OggPcmDecoder(api, probe).decode(new byte[] {1, 2, 3, 4}, limits);
    }

    private static class RecordingProbe implements OggPcmDecoder.AllocationProbe {
        int nativeAllocations;
        int javaAllocations;
        int handlesClosed;
        int nativeBuffersFreed;
        int directInputsFreed;

        @Override public void beforeNativePcmAllocation(long bytes) { nativeAllocations++; }
        @Override public void beforeJavaPcmAllocation(long bytes) { javaAllocations++; }
        @Override public void handleClosed() { handlesClosed++; }
        @Override public void nativePcmFreed() { nativeBuffersFreed++; }
        @Override public void directInputFreed() { directInputsFreed++; }

        void assertAllocations(int expectedNative, int expectedJava) {
            assertEquals(expectedNative, nativeAllocations);
            assertEquals(expectedJava, javaAllocations);
        }
    }

    private static final class RecordingPcmProbe implements PcmDecoder.AllocationProbe {
        int allocations;
        @Override public void beforeJavaPcmAllocation(long bytes) { allocations++; }
    }

    private static final class FakeVorbisApi implements OggPcmDecoder.VorbisApi {
        final OggPcmDecoder.Info info;
        final int frames;
        long openHandle = 17;
        int maxFramesPerDecode = Integer.MAX_VALUE;
        boolean returnZero;
        boolean throwOnDecode;
        boolean throwOnInfo;
        boolean throwOnLength;
        boolean throwOnAllocatePcm;
        int decodeCalls;
        int closes;
        int pcmFrees;
        int inputFrees;

        FakeVorbisApi(OggPcmDecoder.Info info, int frames) {
            this.info = info;
            this.frames = frames;
        }

        @Override public ByteBuffer allocateInput(int bytes) { return ByteBuffer.allocateDirect(bytes); }
        @Override public void freeInput(ByteBuffer input) { inputFrees++; }
        @Override public OggPcmDecoder.OpenResult open(ByteBuffer input) {
            return new OggPcmDecoder.OpenResult(openHandle, openHandle == 0 ? 1 : 0);
        }
        @Override public OggPcmDecoder.Info info(long handle) {
            if (throwOnInfo) throw new IllegalStateException("test info failure");
            return info;
        }
        @Override public int streamLength(long handle) {
            if (throwOnLength) throw new IllegalStateException("test length failure");
            return frames;
        }
        @Override public ShortBuffer allocatePcm(int samples) {
            if (throwOnAllocatePcm) throw new IllegalStateException("test PCM allocation failure");
            return ShortBuffer.allocate(samples);
        }
        @Override public int decode(long handle, int channels, ShortBuffer target) {
            decodeCalls++;
            if (throwOnDecode) throw new IllegalStateException("test decode failure");
            if (returnZero) return 0;
            return Math.min(maxFramesPerDecode, target.remaining() / channels);
        }
        @Override public void close(long handle) { closes++; }
        @Override public void freePcm(ShortBuffer pcm) { pcmFrees++; }

        void assertCleanup(int expectedCloses, int expectedPcmFrees, int expectedInputFrees) {
            assertEquals(expectedCloses, closes);
            assertEquals(expectedPcmFrees, pcmFrees);
            assertEquals(expectedInputFrees, inputFrees);
        }
    }

}
