package com.openggf.audio.presentation;

import com.openggf.audio.output.AudioPresentationSink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestHubMusicPresentationSink {
    @Test
    void volumeDuckMuteAndLowPassAffectOnlyDeliveredPcm() {
        Capture capture = new Capture();
        HubMusicPresentationSink sink = new HubMusicPresentationSink(capture);
        short[] samples = new short[9600];
        java.util.Arrays.fill(samples, (short) 10000);
        AudioPresentationFrameView frame = new AudioPresentationFrameView(samples);
        frame.update(samples, 4800, 4, PresentationMode.FORWARD);
        sink.setVolume(20, false);
        sink.accept(frame);
        assertEquals(2000, capture.last, 20, "20 percent quiet theme");
        sink.setVolume(20, true);
        sink.accept(frame);
        assertEquals(700, capture.last, 20, "Dialog duck keeps UI cues clear");
        sink.setVolume(0, false);
        sink.accept(frame);
        assertEquals(0, capture.last, 10);
        assertEquals(10000, samples[0], "Original PCM remains owned by its producer");
        sink.setVolume(100, false);
        for (int i = 0; i < samples.length; i++) samples[i] = (short) ((i / 2 % 2 == 0) ? 10000 : -10000);
        sink.accept(frame);
        assertTrue(Math.abs(capture.last) < 2000, "High-frequency title detail is low-passed");
        sink.close();
        assertTrue(capture.closed);
    }

    private static final class Capture implements AudioPresentationSink {
        int last;
        boolean closed;
        @Override public int sampleRate() { return 48000; }
        @Override public void accept(AudioPresentationFrameView frame) { last = frame.sampleAt(frame.stereoFrames() - 1, 0); }
        @Override public void onReverseBoundary() { }
        @Override public void close() { closed = true; }
    }
}
