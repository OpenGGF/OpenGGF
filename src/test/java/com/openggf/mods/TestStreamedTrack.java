package com.openggf.mods;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestStreamedTrack {
    @Test
    void introThenHalfOpenLoopWrapsSampleAccurately() {
        StreamedTrack track = new StreamedTrack(new StreamedTrackData(prepared(
                "loop", stereoFrames(10, 20, 30, 40, 50), 2, 5, 1.0f, false)));
        short[] output = new short[16];

        assertEquals(8, track.mixInto(output, 8, 1.0f, 1.0));

        assertArrayEquals(stereoFrames(10, 20, 30, 40, 50, 30, 40, 50), output);
        assertEquals(2.0, track.position());
        assertFalse(track.ended());
    }

    @Test
    void nonLoopingTrackEndsWithoutTouchingUnusedOutput() {
        StreamedTrack track = new StreamedTrack(new StreamedTrackData(prepared(
                "once", stereoFrames(100, 200), 0, 0, 1.0f, false)));
        short[] output = {1, 1, 1, 1, 7, 7, 7, 7};

        assertEquals(2, track.mixInto(output, 4, 1.0f, 1.0));

        assertArrayEquals(new short[] {101, 101, 201, 201, 7, 7, 7, 7}, output);
        assertTrue(track.ended());
    }

    @Test
    void stereoSaturatesAfterTrackAndFadeGainWhileMonoDuplicates() {
        StreamedTrack stereo = new StreamedTrack(new StreamedTrackData(prepared(
                "stereo", new short[] {20_000, -20_000}, 0, 0, 2.0f, false, 2)));
        short[] stereoOutput = {30_000, -30_000};
        stereo.mixInto(stereoOutput, 1, 1.0f, 1.0);
        assertArrayEquals(new short[] {Short.MAX_VALUE, Short.MIN_VALUE}, stereoOutput);

        StreamedTrack mono = new StreamedTrack(new StreamedTrackData(prepared(
                "mono", new short[] {20_000}, 0, 0, 1.0f, false, 1)));
        short[] monoOutput = {1_000, -1_000};
        mono.mixInto(monoOutput, 1, 0.5f, 1.0);
        assertArrayEquals(new short[] {11_000, 9_000}, monoOutput);
    }

    @Test
    void fractionalRateUsesDeterministicLinearInterpolationAndLoopBoundaryNeighbor() {
        StreamedTrack track = new StreamedTrack(new StreamedTrackData(prepared(
                "fast", stereoFrames(10, 20, 30, 40, 50), 0, 0, 1.0f, true)));
        short[] output = new short[8];

        assertEquals(4, track.mixInto(output, 4, 1.0f, 1.25));

        assertArrayEquals(stereoFrames(10, 23, 35, 48), output);
        assertTrue(track.ended());

        StreamedTrack looping = new StreamedTrack(new StreamedTrackData(prepared(
                "interpolate-loop", stereoFrames(0, 10, 20, 30, 40), 2, 5, 1.0f, true)));
        looping.restorePosition(4.5);
        short[] acrossBoundary = new short[2];
        looping.mixInto(acrossBoundary, 1, 1.0f, 1.25);
        assertArrayEquals(stereoFrames(30), acrossBoundary);

        StreamedTrack negative = new StreamedTrack(new StreamedTrackData(prepared(
                "negative-round", new short[] {-10, -11}, 0, 0, 1.0f, true, 1)));
        negative.restorePosition(0.5);
        short[] negativeOutput = new short[2];
        negative.mixInto(negativeOutput, 1, 1.0f, 1.25);
        assertArrayEquals(new short[] {-11, -11}, negativeOutput);

        StreamedTrack overshoot = new StreamedTrack(new StreamedTrackData(prepared(
                "overshoot", stereoFrames(0, 10, 20, 30, 40), 2, 5, 1.0f, true)));
        short[] overshootOutput = new short[6];
        overshoot.mixInto(overshootOutput, 3, 1.0f, 4.0);
        assertArrayEquals(stereoFrames(0, 40, 20), overshootOutput);
        assertEquals(3.0, overshoot.position());
    }

    @Test
    void validatesOutputBoundsRateGainAndRestoredPosition() {
        StreamedTrack track = new StreamedTrack(new StreamedTrackData(prepared(
                "bounds", stereoFrames(1, 2), 0, 0, 1.0f, false)));
        assertThrows(NullPointerException.class, () -> track.mixInto(null, 1, 1.0f, 1.0));
        assertThrows(IllegalArgumentException.class, () -> track.mixInto(new short[1], 1, 1.0f, 1.0));
        assertThrows(IllegalArgumentException.class, () -> track.mixInto(new short[2], -1, 1.0f, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> track.mixInto(new short[2], Integer.MAX_VALUE, 1.0f, 1.0));
        assertThrows(IllegalArgumentException.class, () -> track.mixInto(new short[2], 1, -0.1f, 1.0));
        assertThrows(IllegalArgumentException.class, () -> track.mixInto(new short[2], 1, Float.NaN, 1.0));
        assertThrows(IllegalArgumentException.class, () -> track.mixInto(new short[2], 1, 1.0f, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> track.mixInto(new short[2], 1, 1.0f, Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> track.restorePosition(-1));
        assertThrows(IllegalArgumentException.class, () -> track.restorePosition(2));
        assertDoesNotThrow(() -> track.restorePosition(1.5));
    }

    @Test
    void normalMixPathContainsNoAllocationIoStreamsOrCollections() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/mods/StreamedTrack.java"));
        String body = source.substring(source.indexOf("public int mixInto("),
                source.indexOf("private double canonicalEnd()"));
        assertFalse(body.contains("new short["));
        assertFalse(body.contains("new int["));
        assertFalse(body.contains("new ArrayList"));
        assertFalse(body.contains("new Hash"));
        assertFalse(body.contains(".stream("));
        assertFalse(body.contains("java.io"));
        assertFalse(body.contains("List<"));
        assertFalse(body.contains("Map<"));
    }

    private static PreparedTrack prepared(String name, short[] samples, long loopStart, long loopEnd,
                                          float gain, boolean tempoEffects) {
        return prepared(name, samples, loopStart, loopEnd, gain, tempoEffects, 2);
    }

    private static PreparedTrack prepared(String name, short[] samples, long loopStart, long loopEnd,
                                          float gain, boolean tempoEffects, int channels) {
        return new PreparedTrack(new TrackKey("owner", name), PcmData.takeOwnership(8_000, channels, samples),
                loopStart, loopEnd, gain, tempoEffects, "a".repeat(64));
    }

    private static short[] stereoFrames(int... values) {
        short[] samples = new short[values.length * 2];
        for (int index = 0; index < values.length; index++) {
            samples[index * 2] = (short) values[index];
            samples[index * 2 + 1] = (short) values[index];
        }
        return samples;
    }
}
