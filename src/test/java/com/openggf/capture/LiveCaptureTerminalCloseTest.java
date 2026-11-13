package com.openggf.capture;

import com.openggf.audio.ClockedSilenceAudioHandle;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.runtime.AudioFrameClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class LiveCaptureTerminalCloseTest {
    @Test void closingBeforeFirstStartPreventsEveryAcquisition() throws Exception {
        try (Harness h = new Harness(Duration.ZERO)) {
            h.controller.close();
            h.controller.close();
            h.controller.start(h.viewport, 60);
            h.controller.capturePresentedFrame(h.viewport);

            assertAll(
                    () -> assertEquals(LiveCaptureController.State.INACTIVE, h.controller.state()),
                    () -> h.assertAcquisitions(0),
                    () -> assertFalse(h.controller.indicatorVisible()),
                    () -> assertTrue(h.controller.consumeInterruption().isEmpty()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryStopOrFailureStillAllowsFreshRecordingUntilClose(boolean failStop) throws Exception {
        try (Harness h = new Harness(Duration.ofSeconds(5))) {
            h.controller.start(h.viewport, 60);
            h.recorders.getFirst().failStop = failStop;
            h.controller.requestStop(LiveCaptureController.StopReason.USER);
            h.finalizer.awaitIdle();
            assertEquals(failStop ? LiveCaptureController.State.FAILED
                    : LiveCaptureController.State.INACTIVE, h.controller.state());
            assertEquals(failStop, h.controller.consumeInterruption().isPresent());

            h.controller.start(h.viewport, 60);
            h.controller.capturePresentedFrame(h.viewport);
            assertEquals(LiveCaptureController.State.ACTIVE, h.controller.state());
            h.assertAcquisitions(2);
            assertEquals(1, h.recorders.get(1).submits.get());

            h.controller.close();
            h.controller.start(h.viewport, 60);
            assertAll(
                    () -> assertEquals(LiveCaptureController.State.INACTIVE, h.controller.state()),
                    () -> h.assertAcquisitions(2),
                    () -> assertTrue(h.controller.consumeInterruption().isEmpty()));
        }
    }

    @Test void closeDeniesStartEvenWhenFinalizationCompletesBeforeCloseReturns() throws Exception {
        try (Harness h = new Harness(Duration.ofSeconds(5))) {
            h.controller.start(h.viewport, 60);
            h.finalizer.holdCloseWait = true;
            AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            Thread closer = new Thread(() -> {
                try {
                    h.controller.close();
                } catch (Throwable failure) {
                    closeFailure.set(failure);
                }
            }, "capture-close-caller");
            closer.start();
            try {
                await(h.finalizer.closeWaitEntered);
                assertTrue(closer.isAlive(), "close is still in progress");
                assertFalse(h.finalizer.isShutdown(), "executor shutdown has not supplied a gate");
                assertEquals(LiveCaptureController.State.INACTIVE, h.controller.state(),
                        "encoder finalization has already completed");
                h.controller.start(h.viewport, 60);
                h.controller.capturePresentedFrame(h.viewport);
                assertAll(
                        () -> assertEquals(LiveCaptureController.State.INACTIVE, h.controller.state()),
                        () -> h.assertAcquisitions(1));
            } finally {
                h.finalizer.releaseCloseWait.countDown();
                closer.join(5_000);
            }
            assertFalse(closer.isAlive());
            assertNull(closeFailure.get());
            assertEquals(List.of(closer), h.audioCloseThreads);
            assertEquals(List.of(closer), h.grabberCloseThreads);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timedOutFinalizerCannotRetireResourcesFromAPostCloseStart(boolean failStop) throws Exception {
        try (Harness h = new Harness(Duration.ZERO)) {
            h.deferred = true;
            h.controller.start(h.viewport, 60);
            Recorder first = h.recorders.getFirst();
            first.holdSubmit = true;
            first.failStop = failStop;
            h.controller.capturePresentedFrame(h.viewport);
            h.controller.requestStop(LiveCaptureController.StopReason.USER);
            await(first.submitEntered);
            h.controller.close();
            assertFalse(h.finalizer.isTerminated(), "the old finalizer really outlives close");
            assertEquals(0, first.releases.get(), "the submitting finalizer still owns the frame");

            h.controller.start(h.viewport, 60);
            h.controller.capturePresentedFrame(h.viewport);
            LiveCaptureController.State afterAttempt = h.controller.state();
            first.releaseSubmit.countDown();
            assertTrue(h.finalizer.awaitTermination(5, TimeUnit.SECONDS));
            assertAll(
                    () -> assertDoesNotThrow(() -> h.controller.capturePresentedFrame(h.viewport),
                            "late finalization must leave public capture calls safe"),
                    () -> assertEquals(LiveCaptureController.State.INACTIVE, afterAttempt),
                    () -> assertEquals(LiveCaptureController.State.INACTIVE, h.controller.state()),
                    () -> h.assertAcquisitions(1),
                    () -> assertEquals(1, first.submits.get()),
                    () -> assertEquals(1, first.releases.get()),
                    () -> assertEquals(failStop ? 2 : 1, first.aborts.get()),
                    () -> assertEquals(List.of(Thread.currentThread()), h.audioCloseThreads),
                    () -> assertEquals(List.of(Thread.currentThread()), h.grabberCloseThreads,
                            "a late finalizer must never close a newly acquired GL resource"),
                    () -> assertTrue(h.controller.consumeInterruption().isEmpty(),
                            "late shutdown completion must not report a new capture interruption"));
        }
    }

    @Test void closeReleasesAnUnclaimedFinalFrameWithoutSubmittingIt() throws Exception {
        try (Harness h = new Harness(Duration.ZERO)) {
            CountDownLatch blockerEntered = new CountDownLatch(1);
            h.finalizer.execute(() -> {
                blockerEntered.countDown();
                awaitUninterruptibly(h.finalizer.releaseCloseWait);
            });
            await(blockerEntered);
            h.deferred = true;
            h.controller.start(h.viewport, 60);
            Recorder recorder = h.recorders.getFirst();
            h.controller.capturePresentedFrame(h.viewport);
            h.controller.close();
            assertEquals(1, recorder.releases.get());
            assertEquals(0, recorder.submits.get());
            h.finalizer.releaseCloseWait.countDown();
            assertTrue(h.finalizer.awaitTermination(5, TimeUnit.SECONDS));
            h.controller.close();
            assertEquals(1, recorder.releases.get());
            assertEquals(0, recorder.submits.get());
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(5, TimeUnit.SECONDS), "expected boundary was not reached");
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    /** Holds only close's timed Future.get, after the actual task has completed. */
    private static final class Finalizer extends ThreadPoolExecutor {
        final CountDownLatch closeWaitEntered = new CountDownLatch(1);
        final CountDownLatch releaseCloseWait = new CountDownLatch(1);
        boolean holdCloseWait;

        Finalizer() {
            super(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        }

        @Override protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
            return new FutureTask<>(runnable, value) {
                @Override public T get(long timeout, TimeUnit unit)
                        throws InterruptedException, ExecutionException, TimeoutException {
                    T result = super.get(timeout, unit);
                    if (holdCloseWait) {
                        closeWaitEntered.countDown();
                        if (!releaseCloseWait.await(timeout, unit)) throw new TimeoutException();
                    }
                    return result;
                }
            };
        }

        void awaitIdle() throws InterruptedException {
            CountDownLatch idle = new CountDownLatch(1);
            execute(idle::countDown);
            await(idle);
        }
    }

    private static final class Harness implements AutoCloseable {
        final CaptureViewport viewport = new CaptureViewport(0, 0, 1, 1);
        final Finalizer finalizer = new Finalizer();
        final List<Recorder> recorders = new ArrayList<>();
        final List<Thread> audioCloseThreads = new ArrayList<>();
        final List<Thread> grabberCloseThreads = new ArrayList<>();
        final LiveCaptureController controller;
        int audioOpens, grabberCreates, encoderStarts;
        boolean deferred;

        Harness(Duration timeout) {
            controller = new LiveCaptureController(new LiveCaptureController.Dependencies(
                    rate -> {
                        audioOpens++;
                        return new LiveCaptureAudioHandle() {
                            final ClockedSilenceAudioHandle silence = new ClockedSilenceAudioHandle(48_000, rate);
                            public int sampleRate() { return silence.sampleRate(); }
                            public int frameRate() { return silence.frameRate(); }
                            public int maxStereoFramesPerPacket() { return silence.maxStereoFramesPerPacket(); }
                            public long totalStereoFrames() { return silence.totalStereoFrames(); }
                            public AudioFrameClock.Snapshot clockSnapshot() { return silence.clockSnapshot(); }
                            public int drainPresentationFrame(short[] target) {
                                return silence.drainPresentationFrame(target);
                            }
                            public void close() { audioCloseThreads.add(Thread.currentThread()); }
                        };
                    }, () -> 48_000,
                    v -> {
                        grabberCreates++;
                        return new VideoFrameGrabber() {
                            public int width() { return v.width(); }
                            public int height() { return v.height(); }
                            public byte[] grab() { return new byte[v.rgbaByteSize()]; }
                            public boolean deferredReadback() { return deferred; }
                            public void beginReadback() { }
                            public void close() { grabberCloseThreads.add(Thread.currentThread()); }
                        };
                    }, (v, rate) -> {
                        Recorder recorder = new Recorder(() -> encoderStarts++);
                        recorders.add(recorder);
                        return recorder;
                    }, finalizer, timeout));
        }

        void assertAcquisitions(int count) {
            assertAll(
                    () -> assertEquals(count, audioOpens, "audio acquisitions"),
                    () -> assertEquals(count, grabberCreates, "GL acquisitions"),
                    () -> assertEquals(count, recorders.size(), "recorder acquisitions"),
                    () -> assertEquals(count, encoderStarts, "encoder starts"));
        }

        @Override public void close() throws Exception {
            finalizer.releaseCloseWait.countDown();
            recorders.forEach(recorder -> recorder.releaseSubmit.countDown());
            controller.close();
            finalizer.shutdownNow();
            assertTrue(finalizer.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static final class Recorder extends CaptureRecorder {
        final CountDownLatch submitEntered = new CountDownLatch(1);
        final CountDownLatch releaseSubmit = new CountDownLatch(1);
        final AtomicInteger submits = new AtomicInteger();
        final AtomicInteger releases = new AtomicInteger();
        final AtomicInteger aborts = new AtomicInteger();
        final Runnable onStart;
        boolean holdSubmit, failStop;

        Recorder(Runnable onStart) {
            super(new CaptureEncoder() {
                public void open(Path p, int w, int h, int f, int s) { }
                public void encode(CapturedFrame frame) { }
                public Path finish() { return Path.of("out"); }
                public void abort() { }
            }, BackpressurePolicy.BLOCK, 1, Path.of("target"), "terminal", "test");
            this.onStart = onStart;
        }

        @Override public void start(int w, int h, int fps, int rate) { onStart.run(); }

        @Override CapturedFrame grabFrame(VideoFrameGrabber grabber, short[] pcm, int samples, long index) {
            return CapturedFrame.ownedPixels(grabber.grab(), grabber.width(), grabber.height(),
                    pcm, samples, index, releases::incrementAndGet);
        }

        @Override public void submit(CapturedFrame frame) {
            submits.incrementAndGet();
            submitEntered.countDown();
            if (holdSubmit) awaitUninterruptibly(releaseSubmit);
            frame.release();
        }

        @Override public Path stop() throws CaptureException {
            if (failStop) throw new CaptureException("delayed finalization failed");
            return Path.of("out");
        }

        @Override public void abort() { aborts.incrementAndGet(); }
        @Override void abort(Duration timeout) { aborts.incrementAndGet(); }
    }
}
