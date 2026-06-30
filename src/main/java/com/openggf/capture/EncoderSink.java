package com.openggf.capture;

import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link FrameSink} that hands frames to a {@link CaptureEncoder} on a single
 * background thread via a bounded queue. Queue-full behavior follows the
 * configured {@link BackpressurePolicy}. {@code BLOCK} guarantees no drops.
 */
public final class EncoderSink implements FrameSink {

    private static final CapturedFrame POISON =
            new CapturedFrame(new byte[0], 0, 0, new short[0], 0, -1L);
    private static final long DEFAULT_STOP_JOIN_TIMEOUT_MILLIS = 5_000L;

    private final CaptureEncoder encoder;
    private final BackpressurePolicy policy;
    private final BlockingQueue<CapturedFrame> queue;
    private final long stopJoinTimeoutMillis;
    private final AtomicLong dropped = new AtomicLong();
    private Thread worker;
    private volatile CaptureException workerFailure;
    private volatile Path output;

    public EncoderSink(CaptureEncoder encoder, BackpressurePolicy policy, int capacity) {
        this(encoder, policy, capacity, DEFAULT_STOP_JOIN_TIMEOUT_MILLIS);
    }

    EncoderSink(CaptureEncoder encoder, BackpressurePolicy policy, int capacity, long stopJoinTimeoutMillis) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        if (stopJoinTimeoutMillis < 1) {
            throw new IllegalArgumentException("stopJoinTimeoutMillis must be >= 1");
        }
        this.encoder = encoder;
        this.policy = policy;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.stopJoinTimeoutMillis = stopJoinTimeoutMillis;
    }

    public void open(Path output, int width, int height, int fps, int sampleRate) throws CaptureException {
        encoder.open(output, width, height, fps, sampleRate);
        worker = new Thread(this::runWorker, "capture-encoder");
        worker.start();
    }

    @Override
    public void submit(CapturedFrame frame) throws CaptureException {
        if (workerFailure != null) {
            throw new CaptureException("encoder thread failed", workerFailure);
        }
        switch (policy) {
            case BLOCK -> {
                try {
                    // Timed offer rather than a blocking put: if the worker dies
                    // while the queue is full, an unbounded put() would block the
                    // producer forever and it would never reach stop(). Re-check
                    // worker health between attempts.
                    while (!queue.offer(frame, 50, TimeUnit.MILLISECONDS)) {
                        if (workerFailure != null) {
                            throw new CaptureException("encoder thread failed", workerFailure);
                        }
                        if (!worker.isAlive()) {
                            throw new CaptureException("encoder thread exited unexpectedly");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CaptureException("interrupted while submitting", e);
                }
            }
            case DROP_OLDEST -> {
                while (!queue.offer(frame)) {
                    if (queue.poll() != null) {
                        dropped.incrementAndGet();
                    }
                }
            }
            case FAIL -> {
                if (!queue.offer(frame)) {
                    throw new CaptureException("capture queue full (FAIL policy)");
                }
            }
        }
    }

    @Override
    public Path stop() throws CaptureException {
        try {
            // Deliver the poison pill without hanging: if the worker has already
            // died (e.g. encoder failure) the queue may be full and a blocking
            // put would never return. Offer with a timeout while the worker is
            // alive; bail out as soon as it has exited or recorded a failure.
            while (worker.isAlive() && !queue.offer(POISON, 50, TimeUnit.MILLISECONDS)) {
                if (workerFailure != null) {
                    break;
                }
            }
            worker.join(stopJoinTimeoutMillis);
            if (worker.isAlive()) {
                encoder.abort();
                worker.join(250);
                throw new CaptureException("encoder thread did not stop within "
                        + stopJoinTimeoutMillis + " ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            encoder.abort();
            throw new CaptureException("interrupted while stopping", e);
        }
        if (workerFailure != null) {
            throw new CaptureException("encoder thread failed", workerFailure);
        }
        return output;
    }

    public long droppedCount() {
        return dropped.get();
    }

    private void runWorker() {
        try {
            while (true) {
                CapturedFrame frame = queue.take();
                if (frame == POISON) {
                    output = encoder.finish();
                    return;
                }
                encoder.encode(frame);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerFailure = new CaptureException("encoder thread interrupted", e);
            encoder.abort();
        } catch (CaptureException e) {
            workerFailure = e;
            encoder.abort();
        }
    }
}
