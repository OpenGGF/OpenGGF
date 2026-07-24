package com.openggf.audio.output;

import com.openggf.audio.presentation.AudioPresentationCommandQueue;
import com.openggf.audio.presentation.AudioPresentationMixer;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.LiveCaptureAudioHandle;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TestOpenAlPcmSink {
    @Test
    void aggregatesVariablePresentationPacketsInto1024FrameDeviceBuffers() {
        FakeDevice device = new FakeDevice(57_600);
        OpenAlPcmSink sink = sink(device);
        AudioPresentationProducer producer = producer(sink, 57_600, 60);
        int queued;
        try {
            producer.present(0, PresentationMode.SILENT);
            producer.present(1, PresentationMode.SILENT);
            sink.updateDevice();
            queued = sink.queuedStereoFrames();
        } finally { producer.close(); }

        assertEquals(List.of(1_024), device.enqueuedFrames);
        assertEquals(896, queued);
    }

    @Test
    void reverseBoundaryFlushesDeviceAndSpeakerFifoBeforeReprime() {
        FakeDevice device = new FakeDevice(48_000);
        OpenAlPcmSink sink = sink(device);
        AudioPresentationProducer producer = producer(sink, 48_000, 60);
        try {
            producer.present(0, PresentationMode.SILENT);
            assertEquals(800, sink.queuedStereoFrames());
            producer.beginReverse(1.0);
        } finally { producer.close(); }

        assertEquals(0, sink.queuedStereoFrames());
        assertEquals(1, device.flushCount);
    }

    @Test
    void stalledDeviceDoesNotBlockProducerAndDropsSpeakerOnlyPcm() {
        FakeDevice device = new FakeDevice(10);
        OpenAlPcmSink sink = sink(device);
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            AudioPresentationProducer producer = producer(sink, 10, 1);
            try {
                for (int frame = 0; frame < 10_000; frame++) {
                    producer.present(frame, PresentationMode.SILENT);
                }
            } finally {
                producer.close();
            }
        });
        assertTrue(sink.droppedStereoFrames() > 0);
    }

    @Test
    void overrunWarningIsRateLimitedToOncePerSecond() {
        FakeDevice device = new FakeDevice(10);
        AtomicLong nanos = new AtomicLong();
        List<String> warnings = new ArrayList<>();
        OpenAlPcmSink sink = new OpenAlPcmSink(device, ignored -> {
        }, nanos::get, warnings::add);
        AudioPresentationProducer producer = producer(sink, 10, 1);
        try {
            for (int frame = 0; frame < 5; frame++) {
                producer.present(frame, PresentationMode.SILENT);
            }
            assertEquals(1, warnings.size());
            nanos.set(999_999_999L);
            producer.present(5, PresentationMode.SILENT);
            assertEquals(1, warnings.size());
            nanos.set(1_000_000_000L);
            producer.present(6, PresentationMode.SILENT);
            assertEquals(2, warnings.size());
        } finally {
            producer.close();
        }
    }

    @Test
    void enqueueFailureAtomicallyRequestsNoDeviceReplacementOnce() {
        FakeDevice device = new FakeDevice(61_440);
        device.failEnqueue = true;
        AtomicInteger failures = new AtomicInteger();
        OpenAlPcmSink sink = new OpenAlPcmSink(device,
                ignored -> failures.incrementAndGet(), System::nanoTime,
                ignored -> {
                });
        AudioPresentationProducer producer = producer(sink, 61_440, 60);
        try {
            producer.present(0, PresentationMode.SILENT);
            sink.updateDevice();
            sink.updateDevice();
        } finally { producer.close(); }

        assertEquals(1, failures.get());
        assertEquals(1, device.closeCount);
        assertEquals(0, sink.queuedStereoFrames());
    }

    @Test
    void updateFailureClearsStaleSpeakerPacketsAndRequestsReplacementOnce() {
        FakeDevice device = new FakeDevice(48_000);
        device.failUpdate = true;
        AtomicInteger failures = new AtomicInteger();
        OpenAlPcmSink sink = new OpenAlPcmSink(device,
                ignored -> failures.incrementAndGet(), System::nanoTime,
                ignored -> {
                });
        AudioPresentationProducer producer = producer(sink, 48_000, 60);
        try {
            producer.present(0, PresentationMode.SILENT);
            sink.updateDevice();
            sink.updateDevice();
        } finally { producer.close(); }

        assertEquals(1, failures.get());
        assertEquals(1, device.closeCount);
        assertEquals(0, sink.queuedStereoFrames());
    }

    @Test
    void deviceCleanupFailureDoesNotPreventNoDeviceReplacement() {
        FakeDevice device = new FakeDevice(48_000);
        device.failUpdate = true;
        device.failClose = true;
        List<Throwable> failures = new ArrayList<>();
        OpenAlPcmSink sink = new OpenAlPcmSink(device,
                failures::add, System::nanoTime, ignored -> {
                });

        sink.updateDevice();

        assertEquals(1, failures.size());
        assertEquals(1, failures.getFirst().getSuppressed().length);
        assertEquals(1, device.closeCount);
    }

    @Test
    void closeIsIdempotent() {
        FakeDevice device = new FakeDevice(48_000);
        OpenAlPcmSink sink = sink(device);
        sink.close();
        sink.close();

        assertEquals(1, device.closeCount);
    }

    @Test
    void replacingOnlyTheSinkPreservesProducerHistoryClockCaptureAndNextPacket() {
        FakeDevice device = new FakeDevice(48_000);
        OpenAlPcmSink first = sink(device);
        AudioPresentationProducer producer = producer(first, 48_000, 60);
        producer.setHistoryArmed(true);
        producer.present(0, PresentationMode.FORWARD);
        producer.beginReverse(1.0);
        LiveCaptureAudioHandle capture = producer.attachCapture(60);
        var before = producer.transactionFingerprint();
        RecordingSink replacement = new RecordingSink(48_000);

        producer.replaceSink(replacement);

        assertEquals(before, producer.transactionFingerprint());
        producer.present(1, PresentationMode.REVERSE);
        short[] captured =
                new short[capture.maxStereoFramesPerPacket() * 2];
        int frames = capture.drainPresentationFrame(captured);
        assertEquals(replacement.frames, frames);
        assertArrayEquals(replacement.samples,
                java.util.Arrays.copyOf(captured, frames * 2));
        capture.close();
        producer.close();
    }

    private static OpenAlPcmSink sink(FakeDevice device) {
        return new OpenAlPcmSink(device, ignored -> {
        }, System::nanoTime, ignored -> {
        });
    }

    private static AudioPresentationProducer producer(
            AudioPresentationSink sink, int sampleRate, int frameRate) {
        int maxFrames = (sampleRate + frameRate - 1) / frameRate;
        return new AudioPresentationProducer(sampleRate, frameRate,
                sampleRate, 0, new AudioVoiceRegistry(),
                new AudioPresentationCommandQueue(),
                new AudioPresentationMixer(maxFrames), sink);
    }

    private static final class FakeDevice implements OpenAlPcmSink.Device {
        private final int sampleRate;
        private final List<Integer> enqueuedFrames = new ArrayList<>();
        private boolean failEnqueue;
        private boolean failUpdate;
        private boolean failClose;
        private int flushCount;
        private int closeCount;

        private FakeDevice(int sampleRate) {
            this.sampleRate = sampleRate;
        }

        @Override
        public int initialize() {
            return sampleRate;
        }

        @Override
        public void enqueue(
                short[] stereoPcm, int stereoFrames, int sampleRate) {
            if (failEnqueue) {
                throw new IllegalStateException("enqueue");
            }
            enqueuedFrames.add(stereoFrames);
        }

        @Override
        public void update() {
            if (failUpdate) {
                throw new IllegalStateException("update");
            }
        }

        @Override
        public void flush() {
            flushCount++;
        }

        @Override
        public void pause() {
        }

        @Override
        public void resume() {
        }

        @Override
        public void close() {
            closeCount++;
            if (failClose) {
                throw new IllegalStateException("close");
            }
        }
    }

    private static final class RecordingSink
            implements AudioPresentationSink {
        private final int sampleRate;
        private short[] samples = new short[0];
        private int frames;

        private RecordingSink(int sampleRate) {
            this.sampleRate = sampleRate;
        }

        @Override public int sampleRate() {
            return sampleRate;
        }

        @Override
        public void accept(
                com.openggf.audio.presentation.AudioPresentationFrameView frame) {
            frames = frame.stereoFrames();
            samples = new short[frames * 2];
            frame.copyTo(samples, 0);
        }

        @Override public void onReverseBoundary() {
        }

        @Override public void close() {
        }
    }
}
