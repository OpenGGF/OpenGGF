package com.openggf.audio.runtime;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestLiveAudioCaptureTap {
    @Test
    void sixtyVideoFramesAt48000HzProduceExactly48000StereoFrames() {
        assertEquals(48_000, drainedFramesOverSixtyVideoFrames(48_000));
    }

    @Test
    void sixtyVideoFramesAt44100HzProduceExactly44100StereoFrames() {
        assertEquals(44_100, drainedFramesOverSixtyVideoFrames(44_100));
    }

    @Test
    void freshForwardPcmIsCopiedAndPaddedToTheCaptureClockCount() {
        LiveAudioCaptureTap tap = new LiveAudioCaptureTap(5, 2);
        tap.acceptForwardPcm(new short[] {1, 10}, 1);
        short[] target = filledPacket(tap);

        assertEquals(2, tap.drainPresentationFrame(target));
        assertArrayEquals(new short[] {1, 10, 0, 0, -1, -1}, target);
    }

    @Test
    void noFreshForwardPcmProducesZeroFilledPacket() {
        LiveAudioCaptureTap tap = new LiveAudioCaptureTap(5, 2);
        short[] target = filledPacket(tap);

        assertEquals(2, tap.drainPresentationFrame(target));
        assertArrayEquals(new short[] {0, 0, 0, 0, -1, -1}, target);
    }

    @Test
    void drainConsumesFreshnessAndSecondDrainIsSilence() {
        LiveAudioCaptureTap tap = new LiveAudioCaptureTap(4, 2);
        tap.acceptForwardPcm(new short[] {1, 10, 2, 20}, 2);

        short[] first = filledPacket(tap);
        short[] second = filledPacket(tap);

        assertEquals(2, tap.drainPresentationFrame(first));
        assertEquals(2, tap.drainPresentationFrame(second));
        assertArrayEquals(new short[] {1, 10, 2, 20}, first);
        assertArrayEquals(new short[] {0, 0, 0, 0}, second);
    }

    @Test
    void normalThenSilentStepThenDrainIsSilence() {
        LiveAudioCaptureTap tap = new LiveAudioCaptureTap(4, 2);
        tap.acceptForwardPcm(new short[] {1, 10, 2, 20}, 2);

        tap.clearForwardPcm();

        short[] target = filledPacket(tap);
        assertEquals(2, tap.drainPresentationFrame(target));
        assertArrayEquals(new short[] {0, 0, 0, 0}, target);
    }

    @Test
    void multipleNormalAdvancesRetainOnlyLatestPacket() {
        LiveAudioCaptureTap tap = new LiveAudioCaptureTap(4, 2);
        tap.acceptForwardPcm(new short[] {1, 10, 2, 20}, 2);
        tap.acceptForwardPcm(new short[] {3, 30, 4, 40}, 2);

        short[] target = filledPacket(tap);
        assertEquals(2, tap.drainPresentationFrame(target));
        assertArrayEquals(new short[] {3, 30, 4, 40}, target);
    }

    @Test
    void undersizedDestinationIsRejectedBeforeClockAdvances() {
        LiveAudioCaptureTap tap = new LiveAudioCaptureTap(5, 2);
        short[] undersized = new short[tap.maxStereoFramesPerPacket() * 2 - 1];

        assertThrows(IllegalArgumentException.class, () -> tap.drainPresentationFrame(undersized));

        short[] target = filledPacket(tap);
        assertEquals(2, tap.drainPresentationFrame(target));
    }

    private static int drainedFramesOverSixtyVideoFrames(int sampleRate) {
        LiveAudioCaptureTap tap = new LiveAudioCaptureTap(sampleRate, 60);
        short[] target = new short[tap.maxStereoFramesPerPacket() * 2];
        int total = 0;
        for (int frame = 0; frame < 60; frame++) {
            Arrays.fill(target, (short) -1);
            int drained = tap.drainPresentationFrame(target);
            total += drained;
            for (int sample = 0; sample < drained * 2; sample++) {
                assertEquals(0, target[sample]);
            }
        }
        return total;
    }

    private static short[] filledPacket(LiveAudioCaptureTap tap) {
        short[] packet = new short[tap.maxStereoFramesPerPacket() * 2];
        Arrays.fill(packet, (short) -1);
        return packet;
    }
}
