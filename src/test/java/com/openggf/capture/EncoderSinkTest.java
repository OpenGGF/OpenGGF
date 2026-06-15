package com.openggf.capture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class EncoderSinkTest {

    private static CapturedFrame frame(long index) {
        return new CapturedFrame(new byte[4], 1, 1, new short[2], 1, index);
    }

    /** Records encoded frame indices; an optional gate blocks the first encode. */
    private static final class FakeEncoder implements CaptureEncoder {
        final List<Long> encoded = new ArrayList<>();
        final CountDownLatch gate;
        volatile boolean opened;
        volatile boolean finished;
        volatile boolean aborted;

        FakeEncoder(CountDownLatch gate) { this.gate = gate; }

        @Override public synchronized void open(Path output, int w, int h, int fps, int sr) { opened = true; }
        @Override public void encode(CapturedFrame f) throws CaptureException {
            if (gate != null) {
                try { gate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CaptureException("interrupted", e);
                }
            }
            synchronized (encoded) { encoded.add(f.frameIndex()); }
        }
        @Override public synchronized Path finish() { finished = true; return Path.of("out.mkv"); }
        @Override public synchronized void abort() { aborted = true; }
    }

    @Test
    void blockNeverDropsAndPreservesOrder() throws Exception {
        FakeEncoder enc = new FakeEncoder(null);
        EncoderSink sink = new EncoderSink(enc, BackpressurePolicy.BLOCK, 2);
        sink.open(Path.of("out.mkv"), 1, 1, 60, 48000);
        for (long i = 0; i < 50; i++) {
            sink.submit(frame(i));
        }
        sink.stop();
        assertTrue(enc.opened);
        assertTrue(enc.finished);
        assertEquals(50, enc.encoded.size());
        for (int i = 0; i < 50; i++) {
            assertEquals((long) i, enc.encoded.get(i), "order preserved");
        }
        assertEquals(0, sink.droppedCount());
    }

    @Test
    void dropOldestDiscardsWhenFull() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        FakeEncoder enc = new FakeEncoder(gate);
        EncoderSink sink = new EncoderSink(enc, BackpressurePolicy.DROP_OLDEST, 1);
        sink.open(Path.of("out.mkv"), 1, 1, 60, 48000);
        // First submit is taken by the worker and blocks in encode() on the gate.
        sink.submit(frame(0));
        // Give the worker a moment to pull frame 0 into encode().
        TimeUnit.MILLISECONDS.sleep(50);
        // Queue capacity 1: fill it, then overflow to force drops.
        sink.submit(frame(1)); // sits in queue
        sink.submit(frame(2)); // drops frame 1, queues frame 2
        sink.submit(frame(3)); // drops frame 2, queues frame 3
        gate.countDown();
        sink.stop();
        assertTrue(sink.droppedCount() >= 1, "at least one frame dropped");
        // Frame 0 (in-flight) and the newest survivor (3) must be encoded.
        assertTrue(enc.encoded.contains(0L));
        assertTrue(enc.encoded.contains(3L));
    }

    @Test
    void failThrowsWhenFull() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        FakeEncoder enc = new FakeEncoder(gate);
        EncoderSink sink = new EncoderSink(enc, BackpressurePolicy.FAIL, 1);
        sink.open(Path.of("out.mkv"), 1, 1, 60, 48000);
        sink.submit(frame(0)); // taken by worker, blocks on gate
        TimeUnit.MILLISECONDS.sleep(50);
        sink.submit(frame(1)); // queued (capacity 1)
        assertThrows(CaptureException.class, () -> sink.submit(frame(2)));
        gate.countDown();
        sink.stop();
    }

    /** An encoder that throws on its first encode. */
    private static final class FailingEncoder implements CaptureEncoder {
        volatile boolean aborted;
        @Override public void open(Path output, int w, int h, int fps, int sr) { }
        @Override public void encode(CapturedFrame f) throws CaptureException {
            throw new CaptureException("boom");
        }
        @Override public Path finish() { return Path.of("never"); }
        @Override public void abort() { aborted = true; }
    }

    @Test
    @Timeout(10)
    void encoderFailureSurfacesWithoutHanging() throws Exception {
        FailingEncoder enc = new FailingEncoder();
        EncoderSink sink = new EncoderSink(enc, BackpressurePolicy.BLOCK, 1);
        sink.open(Path.of("out.mkv"), 1, 1, 60, 48000);
        // The worker dies on the first encode; further submits and stop must
        // surface the failure rather than block forever on a full queue.
        CaptureException failure = assertThrows(CaptureException.class, () -> {
            for (long i = 0; i < 1000; i++) {
                sink.submit(frame(i));
            }
            sink.stop();
        });
        assertTrue(failure.getMessage().contains("encoder thread failed")
                || "boom".equals(failure.getCause() != null ? failure.getCause().getMessage() : null));
        assertTrue(enc.aborted, "encoder aborted on failure");
    }

    private static final class BlockingEncoder implements CaptureEncoder {
        final CountDownLatch enteredEncode = new CountDownLatch(1);
        final CountDownLatch releaseEncode = new CountDownLatch(1);
        volatile boolean aborted;

        @Override public void open(Path output, int w, int h, int fps, int sr) { }

        @Override public void encode(CapturedFrame f) throws CaptureException {
            enteredEncode.countDown();
            try {
                releaseEncode.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CaptureException("interrupted", e);
            }
        }

        @Override public Path finish() { return Path.of("out.mkv"); }

        @Override public void abort() {
            aborted = true;
            releaseEncode.countDown();
        }
    }

    @Test
    @Timeout(10)
    void stopAbortsAndThrowsWhenEncoderThreadDoesNotExitBeforeTimeout() throws Exception {
        BlockingEncoder enc = new BlockingEncoder();
        EncoderSink sink = new EncoderSink(enc, BackpressurePolicy.BLOCK, 2, 50);
        sink.open(Path.of("out.mkv"), 1, 1, 60, 48000);
        sink.submit(frame(0));
        assertTrue(enc.enteredEncode.await(1, TimeUnit.SECONDS));

        CaptureException failure = assertThrows(CaptureException.class, sink::stop);

        assertTrue(failure.getMessage().contains("encoder thread did not stop"));
        assertTrue(enc.aborted, "stalled encoder must be aborted so ffmpeg pipes/processes are released");
    }
}
