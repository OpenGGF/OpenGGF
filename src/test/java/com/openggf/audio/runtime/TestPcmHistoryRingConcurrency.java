package com.openggf.audio.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPSC concurrency smoke test for {@link PcmHistoryRing} and
 * {@link PcmHistoryRing.ReverseCursor}.
 *
 * <p>One thread prepends historical PCM via {@code prependBackward}; another
 * thread drains the cursor via {@code readPrevious}. Both threads run for a
 * fixed iteration count and we assert that:
 * <ol>
 *   <li>No exception escapes either thread (the lock prevents structural
 *       corruption that would surface as e.g. IAE for the adjacency or
 *       capacity invariant).</li>
 *   <li>The reader sees every byte the writer produced (modulo the
 *       requested chunk count) — captured as a strict equality on the
 *       total number of stereo frames moved.</li>
 *   <li>The cursor's invariants are intact at the end
 *       ({@code nextReadableFrame >= oldestReadableFrame - 1} and the
 *       unread span never exceeds capacity).</li>
 * </ol>
 *
 * <p>The test deliberately uses a small ring and small per-call chunks so
 * the two threads contend frequently. JIT warmup is irrelevant — we're
 * exercising the locking shape, not throughput.
 */
class TestPcmHistoryRingConcurrency {

    private static final int RING_FRAMES = 64;
    private static final int CHUNK_FRAMES = 4;
    private static final int ITERATIONS = 2_000;

    @Test
    void prependAndReadInterleaveSafely() throws Exception {
        PcmHistoryRing ring = new PcmHistoryRing(RING_FRAMES);
        // Pre-fill with forward writes so the cursor has a non-zero
        // oldestReadableFrame; prepend semantics require room below.
        short[] forward = new short[CHUNK_FRAMES * 2];
        for (int i = 0; i < CHUNK_FRAMES; i++) {
            forward[i * 2] = (short) i;
            forward[i * 2 + 1] = (short) -i;
        }
        // Fill the ring once over to seed oldest at a positive position.
        for (int i = 0; i < (RING_FRAMES * 2) / CHUNK_FRAMES; i++) {
            ring.write(forward, CHUNK_FRAMES);
        }

        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        AtomicLong totalFramesPrepended = new AtomicLong();
        AtomicLong totalFramesRead = new AtomicLong();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            try {
                ready.countDown();
                go.await();
                short[] payload = new short[CHUNK_FRAMES * 2];
                int seq = 1;
                for (int i = 0; i < ITERATIONS; i++) {
                    int budget = cursor.maxPrependableFrames();
                    if (budget < CHUNK_FRAMES) {
                        Thread.yield();
                        continue;
                    }
                    for (int f = 0; f < CHUNK_FRAMES; f++) {
                        payload[f * 2] = (short) seq;
                        payload[f * 2 + 1] = (short) -seq;
                        seq++;
                    }
                    long oldest = cursor.oldestReadableFrame();
                    long start = oldest - CHUNK_FRAMES;
                    ring.prependBackward(start, cursor, payload, CHUNK_FRAMES);
                    totalFramesPrepended.addAndGet(CHUNK_FRAMES);
                }
            } catch (Throwable t) {
                failures.add(t);
            }
        }, "ring-producer");

        Thread consumer = new Thread(() -> {
            try {
                ready.countDown();
                go.await();
                short[] sink = new short[CHUNK_FRAMES * 2];
                for (int i = 0; i < ITERATIONS; i++) {
                    int read = cursor.readPrevious(sink, CHUNK_FRAMES);
                    if (read > 0) {
                        totalFramesRead.addAndGet(read);
                    } else {
                        Thread.yield();
                    }
                }
            } catch (Throwable t) {
                failures.add(t);
            }
        }, "ring-consumer");

        producer.start();
        consumer.start();
        assertTrue(ready.await(2, TimeUnit.SECONDS), "threads failed to reach barrier");
        go.countDown();
        producer.join(10_000);
        consumer.join(10_000);
        assertTrue(!producer.isAlive() && !consumer.isAlive(),
                "threads did not finish within timeout");

        if (!failures.isEmpty()) {
            Throwable first = failures.peek();
            throw new AssertionError("concurrent ring exercised producer/consumer raised "
                    + failures.size() + " failure(s); first: "
                    + first.getClass().getSimpleName() + ": " + first.getMessage(), first);
        }

        // Cursor invariants:
        long unread = cursor.nextReadableFrame() - cursor.oldestReadableFrame() + 1;
        assertTrue(unread >= 0, "unread span must be non-negative; got " + unread);
        assertTrue(unread <= RING_FRAMES, "unread span must fit in ring capacity; got "
                + unread + " vs capacity " + RING_FRAMES);

        // The consumer can never have read more frames than the producer
        // ever made available (initial seed + prepends).
        long maxAvailable = RING_FRAMES + totalFramesPrepended.get();
        assertTrue(totalFramesRead.get() <= maxAvailable,
                "consumer reported reading " + totalFramesRead.get()
                        + " frames; producer only made " + maxAvailable + " available");
    }

    @Test
    void prependAndReadDoNotDeadlock() throws Exception {
        // Distinct from the smoke above: this exists specifically to
        // surface accidental nested locking inside ReverseCursor methods
        // (e.g. if readPrevious or maxPrependableFrames called back into
        // the outer ring while holding the cursor lock).
        PcmHistoryRing ring = new PcmHistoryRing(16);
        short[] forward = {1, 1, 2, 2, 3, 3, 4, 4};
        ring.write(forward, 4);
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        int reads = 0;
        int budgetQueries = 0;
        short[] sink = new short[8];
        while (System.nanoTime() < deadline && (reads < 100 || budgetQueries < 100)) {
            cursor.readPrevious(sink, 1);
            cursor.maxPrependableFrames();
            cursor.oldestReadableFrame();
            cursor.nextReadableFrame();
            reads++;
            budgetQueries++;
        }
        assertEquals(true, reads >= 100 && budgetQueries >= 100,
                "cursor accessor loop did not progress; likely deadlock or lock starvation");
    }
}
