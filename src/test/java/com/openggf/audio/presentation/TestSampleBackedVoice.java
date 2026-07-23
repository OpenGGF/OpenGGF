package com.openggf.audio.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSampleBackedVoice {
    private static final int UNITY_GAIN_Q16 = 1 << 16;

    @Test
    void monoDuplicatesAndLinearInterpolationUsesFixedPointStep() {
        SampleBackedVoice voice = voice(new short[] {0, 1_000, 2_000}, 1, 1_000, 1_000, 0.5,
                UNITY_GAIN_Q16, false);
        long[] accumulation = new long[8];

        voice.mixInto(accumulation, 4);

        assertArrayEquals(new long[] {0, 0, 500, 500, 1_000, 1_000, 1_500, 1_500}, accumulation);
    }

    @Test
    void stereoChannelsRemainIndependent() {
        SampleBackedVoice voice = voice(new short[] {100, -100, 300, -300}, 2, 1_000, 1_000, 1.0,
                UNITY_GAIN_Q16, false);
        long[] accumulation = new long[4];

        voice.mixInto(accumulation, 2);

        assertArrayEquals(new long[] {100, -100, 300, -300}, accumulation);
    }

    @Test
    void pitchChangesSourceFrameStepWithoutChangingOutputPacketSize() {
        SampleBackedVoice voice = voice(new short[] {100, 200, 300, 400}, 1, 1_000, 1_000, 2.0,
                UNITY_GAIN_Q16, false);
        long[] accumulation = new long[8];

        voice.mixInto(accumulation, 4);

        assertArrayEquals(new long[] {100, 100, 300, 300, 0, 0, 0, 0}, accumulation);
    }

    @Test
    void loopingMusicWrapsExactlyAcrossPacketBoundary() {
        SampleBackedVoice voice = voice(new short[] {100, 200}, 1, 1_000, 1_000, 1.0,
                UNITY_GAIN_Q16, true);
        long[] firstPacket = new long[6];
        long[] secondPacket = new long[6];

        voice.mixInto(firstPacket, 3);
        voice.mixInto(secondPacket, 3);

        assertArrayEquals(new long[] {100, 100, 200, 200, 100, 100}, firstPacket);
        assertArrayEquals(new long[] {200, 200, 100, 100, 200, 200}, secondPacket);
        assertFalse(voice.isComplete());
    }

    @Test
    void oneShotCompletesAndStopIsExplicit() {
        SampleBackedVoice voice = voice(new short[] {100}, 1, 1_000, 1_000, 1.0,
                UNITY_GAIN_Q16, false);
        long[] accumulation = new long[4];

        voice.mixInto(accumulation, 2);

        assertArrayEquals(new long[] {100, 100, 0, 0}, accumulation);
        assertTrue(voice.isComplete());
        voice.stop();
        assertTrue(voice.isComplete());
    }

    @Test
    void gainIsAppliedBeforeWideAccumulation() {
        SampleBackedVoice voice = voice(new short[] {1_000}, 1, 1_000, 1_000, 1.0,
                UNITY_GAIN_Q16 / 2, false);
        long[] accumulation = new long[] {50, -50};

        voice.mixInto(accumulation, 1);

        assertArrayEquals(new long[] {550, 450}, accumulation);
    }

    @Test
    void snapshotRestoreReproducesTheNextPacketBitExactly() {
        SampleBackedVoice voice = voice(new short[] {0, 1_000, 2_000, 3_000, 4_000}, 1,
                1_000, 1_000, 0.5, UNITY_GAIN_Q16, false);
        voice.mixInto(new long[6], 3);
        PresentationVoiceSnapshot.Sample snapshot = (PresentationVoiceSnapshot.Sample) voice.snapshot();
        long[] original = new long[8];
        voice.mixInto(original, 4);

        SampleBackedVoice restored = SampleBackedVoice.restore(snapshot,
                new DecodedPcm("fixture", 1, 1_000, new short[] {0, 1_000, 2_000, 3_000, 4_000}));
        long[] replayed = new long[8];
        restored.mixInto(replayed, 4);

        assertArrayEquals(original, replayed);
    }

    @Test
    void unsignedRawSegaPcmUsesExistingYmDacGain() {
        DecodedPcmCache cache = new DecodedPcmCache();
        DecodedPcm raw = cache.registerUnsigned8Mono("sega", 48_000,
                new byte[] {0, (byte) 0x80, (byte) 0xFF});
        SampleBackedVoice voice = SampleBackedVoice.rawSegaPcm(1, 0, raw, 48_000);
        long[] accumulation = new long[6];

        voice.mixInto(accumulation, 3);

        assertArrayEquals(new long[] {-8_192, -8_192, 0, 0, 8_128, 8_128}, accumulation);
    }

    @Test
    void rawPcmRegistrationCopiesCallerBytesAndKeepsStableAssetIdentity() {
        byte[] callerBytes = new byte[] {(byte) 128};
        DecodedPcmCache cache = new DecodedPcmCache();
        DecodedPcm registered = cache.registerUnsigned8Mono("sega", 48_000, callerBytes);
        callerBytes[0] = 0;

        SampleBackedVoice voice = SampleBackedVoice.rawSegaPcm(1, 0, registered, 48_000);
        PresentationVoiceSnapshot.Sample snapshot = (PresentationVoiceSnapshot.Sample) voice.snapshot();

        assertArrayEquals(new short[] {0}, registered.copySamples());
        assertTrue(snapshot.assetId().equals("sega"));
    }

    private static SampleBackedVoice voice(short[] samples, int channels, int sourceRate, int outputRate,
                                           double pitch, int gainQ16, boolean looping) {
        return new SampleBackedVoice(1, 0, new DecodedPcm("fixture", channels, sourceRate, samples),
                outputRate, pitch, gainQ16, looping);
    }
}
