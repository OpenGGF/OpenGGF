package com.openggf.audio;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.runtime.AudioCommandDataResolver;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.audio.runtime.ReverseAudioSession;
import com.openggf.audio.runtime.ReverseResynthesizer;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import com.openggf.audio.smps.SmpsSequencer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Task 7's shutdown hardening for the reverse-resynth worker.
 *
 * <p>The {@link AudioManager#shutdownReverseResynthWorker} helper is
 * package-private specifically so tests can drive a controllable thread
 * through both the happy-path (worker stops within budget) and the
 * timeout-detach path (worker doesn't respond; AudioManager detaches
 * the session, bumps the counter, and proceeds).
 */
class TestAudioManagerReverseResynthShutdown {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void shutdownCompletesWithinBudgetForCooperativeWorker() throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();

        ReverseResynthesizer worker = newWorkerForTest();
        // A thread that respects requestStop quickly. We can't easily
        // drive ReverseResynthesizer.run() in a test (it would need a full
        // session + chip emulation), so we use a thin runnable that polls
        // a flag we control via worker.requestStop() — modelled by piggy-
        // backing on a CountDownLatch since worker's stopping field is
        // private.
        CountDownLatch stopLatch = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            try {
                stopLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-cooperative-worker");
        thread.setDaemon(true);
        thread.start();

        // Bridge: when AudioManager.requestStop fires, release the latch
        // so the thread exits. We do this by wrapping the call below.
        new Thread(() -> {
            // Wait for the shutdown call to acquire the worker's
            // stopping flag, then signal.
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            stopLatch.countDown();
        }).start();

        boolean cleanExit = audio.shutdownReverseResynthWorker(worker, thread, 500L);
        assertTrue(cleanExit, "worker that responds to stop must exit within budget");
        assertFalse(thread.isAlive(), "thread must be joined");
        assertEquals(0L, audio.reverseResynthShutdownTimeoutsForTest(),
                "timeout counter must stay 0 on happy path");
    }

    @Test
    void shutdownDetachesAndBumpsCounterWhenWorkerHangs() throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();

        ReverseResynthesizer worker = newWorkerForTest();
        // A thread that never voluntarily exits inside the join budget.
        // The shutdown helper should join(timeout), see thread.isAlive(),
        // call worker.detachSession(), bump the counter, and return false.
        CountDownLatch never = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            try {
                never.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-hanging-worker");
        thread.setDaemon(true);
        thread.start();

        long before = audio.reverseResynthShutdownTimeoutsForTest();
        boolean cleanExit = audio.shutdownReverseResynthWorker(worker, thread, 50L);
        assertFalse(cleanExit, "shutdown helper must report timeout when worker doesn't stop");
        assertEquals(before + 1, audio.reverseResynthShutdownTimeoutsForTest(),
                "timeout counter must increment");

        // Let the test thread exit so we don't leak it.
        never.countDown();
        thread.join(1_000);
    }

    private static ReverseResynthesizer newWorkerForTest() {
        // Minimal session — never actually exercised; the test threads
        // above don't invoke worker.run(), only requestStop / detachSession.
        PcmHistoryRing ring = new PcmHistoryRing(16);
        ReverseAudioSession session = new ReverseAudioSession(
                ring,
                new AudioKeyframeStore().frozenView(),
                /* audioFloor */ 0L,
                List.of(),
                120, 60,
                SmpsSequencer.Region.NTSC,
                4, 2,
                SmpsDriverSnapshot.liveReferences(),
                () -> new SmpsDriver(120),
                new NullDataResolver(),
                null, null,
                false, false, false);
        return new ReverseResynthesizer(session);
    }

    private static final class NullDataResolver implements AudioCommandDataResolver {
        @Override
        public MusicData resolveMusic(AudioCommand.PlayMusic command) {
            return null;
        }

        @Override
        public SfxData resolveSfx(AudioCommand.PlaySfx command) {
            return null;
        }
    }

    // Unused imports kept for the imports above to compile cleanly. The
    // test fixtures lean on AudioOutputFifo / AudioFrameClock /
    // StreamBackedDeterministicAudioRuntime in the broader suite; this
    // file's minimal worker doesn't.
    @SuppressWarnings("unused")
    private static final Class<?>[] KEEP_IMPORTS = {
            AudioOutputFifo.class,
            AudioFrameClock.class,
            StreamBackedDeterministicAudioRuntime.class
    };
}
