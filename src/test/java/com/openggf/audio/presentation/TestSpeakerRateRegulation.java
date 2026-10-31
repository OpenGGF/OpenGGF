package com.openggf.audio.presentation;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.output.OpenAlPcmSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TestSpeakerRateRegulation {
    private static final int SAMPLE_RATE = 48_000;
    private static final int TARGET = 4_096;

    record Skew(double producer, double crystal, int pumps) { }

    static Stream<Skew> skews() {
        return Stream.of(30, 60, 144).flatMap(pumps -> Stream.of(-0.002, 0.002)
                .flatMap(producer -> Stream.of(-0.0002, 0.0002)
                        .map(crystal -> new Skew(producer, crystal, pumps))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("skews")
    void independentlyClockedPacketsAndJitteredPumpStayBoundedForTwoHours(Skew skew) {
        var clock = new AtomicLong();
        var device = new ClockedDevice(clock, skew.crystal);
        try (var sink = sink(device, clock)) {
            short[] samples = new short[800 * 2];
            var frame = new AudioPresentationFrameView(samples);
            frame.update(samples, 800, 0, PresentationMode.FORWARD);
            double packetPeriod = 1_000_000_000.0 / (60 * (1 + skew.producer));
            double pumpPeriod = 1_000_000_000.0 / skew.pumps;
            double nextPacket = 0, nextPump = 0;
            long packets = 0, pumps = 0;
            long end = 7_200_000_000_000L;
            double backlogSum = 0, ratioSum = 0;
            long observations = 0;
            long previousCursor = 0;
            while (Math.min(nextPacket, nextPump) <= end) {
                clock.set((long) Math.min(nextPacket, nextPump));
                device.advance();
                if (nextPacket <= nextPump) {
                    sink.accept(frame);
                    nextPacket = ++packets * packetPeriod;
                } else {
                    sink.updateDevice();
                    assertTrue(sink.playbackRate() >= 0.995 && sink.playbackRate() <= 1.005);
                    long cursor = sink.consumedStereoFrames();
                    assertTrue(cursor >= previousCursor, "rate changes retain source-frame cursor order");
                    previousCursor = cursor;
                    if (clock.get() >= end - 60_000_000_000L) {
                        backlogSum += sink.backlogStereoFrames();
                        ratioSum += sink.playbackRate();
                        observations++;
                    }
                    // Alternating 20% jitter has zero mean and never shares the producer clock.
                    nextPump += pumpPeriod * (++pumps % 2 == 0 ? 0.8 : 1.2);
                }
            }
            assertEquals(0, device.underruns, "two hours must have no stopped-source transitions");
            assertEquals(0, sink.droppedStereoFrames(), "two hours must retain every speaker source frame");
            assertEquals(TARGET, backlogSum / observations, 100, "last minute's mean serviced backlog");
            assertEquals((1 + skew.producer) / (1 + skew.crystal), ratioSum / observations, 0.0001);
            assertTrue(sink.isRateRegulated());
        }
    }

    @Test
    void matchedBacklogIsUnityAndDuplicateCallsCannotChangeTheController() {
        var clock = new AtomicLong();
        var device = new ClockedDevice(clock, 0);
        try (var sink = sink(device, clock)) {
            device.fixedBacklog = TARGET;
            device.playing = true;
            for (int i = 1; i <= 600; i++) {
                clock.set(i * 1_000_000_000L);
                sink.updateDevice();
                assertEquals(TARGET, sink.backlogStereoFrames());
                assertEquals(1.0, sink.playbackRate(), 0.0);
            }
            device.fixedBacklog += 100;
            clock.addAndGet(1_000_000_000L);
            sink.updateDevice();
            double ratio = sink.playbackRate();
            assertTrue(ratio > 1);
            for (int repeat = 0; repeat < 100; repeat++) sink.updateDevice();
            assertEquals(ratio, sink.playbackRate(), 0.0, "no elapsed time means no filtering, integration or slew");
            assertEquals(0, sink.saturationCount());
        }
    }

    @Test
    void correctionHasTheRightSignAndClampsWithSaturationDiagnostics() {
        assertCorrection(12_000, 1.005);
        assertCorrection(1_024, 0.995);
    }

    private static void assertCorrection(int backlog, double expected) {
        var clock = new AtomicLong();
        var device = new ClockedDevice(clock, 0);
        try (var sink = sink(device, clock)) {
            // Hold the device backlog independently of the software queue to exercise both signs.
            device.fixedBacklog = backlog;
            device.playing = true;
            double previous = 1.0;
            for (int i = 0; i <= 600; i++) {
                clock.set(i * 1_000_000_000L);
                sink.updateDevice();
                assertTrue(Math.abs(sink.playbackRate() - previous) <= 0.000500000001,
                        "500 ppm/s bounds pitch slew regardless of pump count");
                previous = sink.playbackRate();
            }
            assertEquals(expected, sink.playbackRate(), 1e-12);
            assertEquals(1, sink.saturationCount(), "one uninterrupted excursion to a rate limit");
            assertEquals(backlog, sink.backlogStereoFrames());
            device.fixedBacklog = backlog > TARGET ? 1_024 : 12_000;
            for (int i = 601; i <= 900; i++) {
                clock.set(i * 1_000_000_000L);
                sink.updateDevice();
            }
            assertEquals(2.0 - expected, sink.playbackRate(), 1e-12, "anti-windup must permit the opposite correction");
        }
    }

    @Test
    void pauseResumeReverseStarvationAndReplacementResetRateAndDoNotIntegrateIdleTime() {
        var clock = new AtomicLong();
        var device = new ClockedDevice(clock, 0);
        try (var sink = sink(device, clock)) {
            device.fixedBacklog = 12_000;
            device.playing = true;
            for (int i = 0; i <= 40; i++) {
                clock.set(i * 1_000_000_000L);
                sink.updateDevice();
            }
            assertTrue(sink.playbackRate() > 1);
            sink.pause();
            assertEquals(1.0, device.ratio);
            clock.addAndGet(3_600_000_000_000L);
            sink.updateDevice();
            sink.resume();
            sink.updateDevice();
            assertEquals(1.0, device.ratio, "paused time must never enter the integral or slew budget");
            clock.addAndGet(1_000_000_000L);
            sink.updateDevice();
            assertTrue(sink.playbackRate() > 1);
            sink.onReverseBoundary();
            assertEquals(1.0, sink.playbackRate());
            assertEquals(0, sink.backlogStereoFrames());
            device.fixedBacklog = -1;
            clock.addAndGet(3_600_000_000_000L);
            sink.updateDevice();
            assertEquals(1.0, device.ratio, "unprimed output stays at unity");
            offer(sink, TARGET - 1);
            sink.updateDevice();
            assertFalse(device.playing, "priming retains one FIFO buffer in addition to three AL buffers");
            offer(sink, 1);
            sink.updateDevice();
            assertTrue(device.playing);
            clock.addAndGet(1_000_000_000L);
            device.advance();
            sink.updateDevice();
            assertEquals(1, sink.underrunCount());
            assertEquals(1.0, sink.playbackRate(), "stopped/starved output resets instead of integrating");
            assertEquals(1_024, sink.queuedStereoFrames());
            offer(sink, 3_072);
            sink.updateDevice();
            assertEquals(1.0, sink.playbackRate());
        }
        assertEquals(1.0, device.ratio, "retiring an output leaves no pitch trim behind");
        try (var replacement = sink(new ClockedDevice(clock, 0), clock)) {
            assertEquals(1.0, replacement.playbackRate());
        }
    }

    @Test
    void managerDistinguishesRegulatedSilentAndUnsupportedOutputs() {
        var clock = new AtomicLong();
        var device = new ClockedDevice(clock, 0);
        try (var speaker = sink(device, clock)) {
            assertTrue(manager(speaker).isSpeakerRateRegulated());
            device.supportsRate = false;
            assertFalse(manager(speaker).isSpeakerRateRegulated());
        }
        assertTrue(manager(new NoDeviceAudioSink(SAMPLE_RATE)).isSpeakerRateRegulated());
        assertFalse(manager(new AudioPresentationSinkStub()).isSpeakerRateRegulated());
    }

    private static AudioManager manager(AudioPresentationSink sink) {
        AudioManager manager = AudioManager.createSilent();
        manager.setBackend(new NullAudioBackend() {
            @Override
            public AudioPresentationSink createPresentationSink(
                    java.util.function.Consumer<Throwable> failure,
                    java.util.function.Consumer<String> warning) {
                return sink;
            }
        });
        return manager;
    }

    private static final class AudioPresentationSinkStub implements AudioPresentationSink {
        @Override public int sampleRate() { return SAMPLE_RATE; }
        @Override public void accept(AudioPresentationFrameView frame) { }
        @Override public void onReverseBoundary() { }
        @Override public void close() { }
    }

    private static OpenAlPcmSink sink(ClockedDevice device, AtomicLong clock) {
        return new OpenAlPcmSink(device, failure -> fail(failure), clock::get, warning -> fail(warning));
    }

    private static void offer(OpenAlPcmSink sink, int frames) {
        short[] samples = new short[frames * 2];
        var frame = new AudioPresentationFrameView(samples);
        frame.update(samples, frames, 0, PresentationMode.FORWARD);
        sink.accept(frame);
    }

    private static final class ClockedDevice implements OpenAlPcmSink.Device {
        private final AtomicLong clock;
        private final double crystal;
        private long lastNanos;
        private long enqueued;
        private double consumed;
        private double ratio = 1;
        private long underruns;
        private boolean playing;
        private boolean paused;
        private boolean supportsRate = true;
        private long fixedBacklog = -1;

        ClockedDevice(AtomicLong clock, double crystal) {
            this.clock = clock;
            this.crystal = crystal;
            lastNanos = clock.get();
        }

        void advance() {
            long now = clock.get();
            if (playing && !paused) {
                consumed += (now - lastNanos) / 1_000_000_000.0 * SAMPLE_RATE * (1 + crystal) * ratio;
                if (consumed >= enqueued) {
                    consumed = enqueued;
                    underruns++;
                    playing = false;
                }
            }
            lastNanos = now;
        }

        @Override public int initialize() { return SAMPLE_RATE; }
        @Override public boolean supportsPlaybackRate() { return supportsRate; }
        @Override public void setPlaybackRate(double ratio) { this.ratio = ratio; }
        @Override public void enqueue(short[] samples, int frames, int rate) {
            assertEquals(SAMPLE_RATE, rate);
            assertEquals(1_024, frames, "producer packets are never retimed or resized for the speaker");
            enqueued += frames;
            playing = true;
        }
        @Override public QueueState update() {
            long remaining = fixedBacklog >= 0 ? fixedBacklog : enqueued - (long) consumed;
            int buffers = fixedBacklog >= 0 ? 3 : (int) ((enqueued + 1_023) / 1_024 - (long) consumed / 1_024);
            return new QueueState(buffers, remaining, playing);
        }
        @Override public long consumedStereoFrames() { return (long) consumed; }
        @Override public long underrunCount() { return underruns; }
        @Override public void flush() {
            enqueued = 0;
            consumed = 0;
            underruns = 0;
            playing = false;
        }
        @Override public void pause() { paused = true; }
        @Override public void resume() { paused = false; }
        @Override public void close() { }
    }
}
