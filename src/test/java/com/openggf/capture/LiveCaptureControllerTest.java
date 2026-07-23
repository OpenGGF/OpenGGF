package com.openggf.capture;

import com.openggf.audio.LiveCaptureAudioHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LiveCaptureControllerTest {
    private final List<ExecutorService> executors = new ArrayList<>();

    @AfterEach void shutdown() {
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test void startCaptureAndAsyncStopReverseResourceOrder() throws Exception {
        Harness h = new Harness();
        LiveCaptureController c = h.controller();
        c.start(h.viewport, 60);
        assertEquals(LiveCaptureController.State.ACTIVE, c.state());
        assertTrue(c.indicatorVisible());
        c.capturePresentedFrame(h.viewport);
        c.capturePresentedFrame(h.viewport);
        assertEquals(List.of(0L, 1L), h.recorder.indexes);
        c.requestStop(LiveCaptureController.StopReason.USER);
        awaitNotStopping(c);
        assertEquals(LiveCaptureController.State.INACTIVE, c.state());
        assertEquals(List.of("audio-close", "recorder-stop"), h.events);
        assertFalse(c.indicatorVisible());
    }

    @Test void viewportMismatchStopsBeforeGrabOrSubmit() throws Exception {
        Harness h = new Harness();
        LiveCaptureController c = h.controller();
        c.start(h.viewport, 60);
        c.capturePresentedFrame(new CaptureViewport(5, 0, 320, 224));
        awaitNotStopping(c);
        assertEquals(0, h.grabs);
        assertTrue(h.recorder.indexes.isEmpty());
    }

    @Test void startFailuresAbortAndCloseAcquiredResources() {
        Harness h = new Harness();
        h.failRecorderCreate = true;
        LiveCaptureController c = h.controller();
        c.start(h.viewport, 60);
        assertEquals(LiveCaptureController.State.FAILED, c.state());
        assertEquals(List.of("audio-close"), h.events);
        h.failRecorderCreate = false;
        c.start(h.viewport, 60);
        assertEquals(LiveCaptureController.State.ACTIVE, c.state());
    }

    @Test void frameFailuresAbortAndEnterFailed() {
        Harness h = new Harness();
        LiveCaptureController c = h.controller();
        c.start(h.viewport, 60);
        h.failDrain = true;
        c.capturePresentedFrame(h.viewport);
        assertEquals(LiveCaptureController.State.FAILED, c.state());
        assertTrue(h.recorder.aborted);
        assertFalse(c.indicatorVisible());
    }

    @Test void audioCloseFailureAbortsAndEntersFailed() {
        Harness h = new Harness();
        h.failAudioClose = true;
        LiveCaptureController c = h.controller();
        c.start(h.viewport, 60);
        c.requestStop(LiveCaptureController.StopReason.USER);
        assertEquals(LiveCaptureController.State.FAILED, c.state());
        assertTrue(h.recorder.aborted);
    }

    @Test void audioAcquireFailureIsReported() {
        Harness h = new Harness();
        h.failAudio = true;
        LiveCaptureController c = h.controller();
        c.start(h.viewport, 60);
        assertEquals(LiveCaptureController.State.FAILED, c.state());
        assertNotNull(c.lastFailure());
    }

    @Test void recorderOpenFailureAbortsRecorderAndClosesAudio() {
        Harness h = new Harness();
        h.recorder.failStart = true;
        LiveCaptureController c = h.controller();
        c.start(h.viewport, 60);
        assertEquals(LiveCaptureController.State.FAILED, c.state());
        assertTrue(h.recorder.aborted);
        assertTrue(h.audioClosed);
    }

    @Test void submitAndGrabFailuresAbort() {
        Harness h = new Harness();
        LiveCaptureController c = h.controller();
        c.start(h.viewport, 60);
        h.failGrab = true;
        c.capturePresentedFrame(h.viewport);
        assertEquals(LiveCaptureController.State.FAILED, c.state());
        h.failGrab = false;
        c.start(h.viewport, 60);
        h.recorder.failSubmit = true;
        c.capturePresentedFrame(h.viewport);
        assertEquals(LiveCaptureController.State.FAILED, c.state());
    }

    @Test void finalizationFailurePersistsAndCanRetry() throws Exception {
        Harness h = new Harness();
        h.recorder.failStop = true;
        LiveCaptureController c = h.controller();
        c.start(h.viewport, 60);
        c.requestStop(LiveCaptureController.StopReason.USER);
        awaitNotStopping(c);
        assertEquals(LiveCaptureController.State.FAILED, c.state());
        h.recorder.failStop = false;
        c.start(h.viewport, 60);
        assertEquals(LiveCaptureController.State.ACTIVE, c.state());
    }

    @Test void repeatedStopCloseAndStartWhileStoppingAreSafe() throws Exception {
        Harness h = new Harness();
        h.recorder.stopGate = new java.util.concurrent.CountDownLatch(1);
        LiveCaptureController c = h.controller();
        c.start(h.viewport, 60);
        c.requestStop(LiveCaptureController.StopReason.USER);
        c.requestStop(LiveCaptureController.StopReason.USER);
        c.start(h.viewport, 60);
        assertEquals(LiveCaptureController.State.STOPPING, c.state());
        h.recorder.stopGate.countDown();
        awaitNotStopping(c);
        c.close();
        c.close();
        assertEquals(LiveCaptureController.State.INACTIVE, c.state());
    }

    private static void awaitNotStopping(LiveCaptureController c) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (c.state() == LiveCaptureController.State.STOPPING && System.nanoTime() < end) {
            Thread.sleep(5);
        }
    }

    private final class Harness {
        final CaptureViewport viewport = new CaptureViewport(0, 0, 320, 224);
        final FakeRecorder recorder = new FakeRecorder();
        final List<String> events = new ArrayList<>();
        boolean failAudio, failRecorderCreate, failDrain, failGrab, failAudioClose, audioClosed;
        int grabs;

        LiveCaptureController controller() {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executors.add(executor);
            return new LiveCaptureController(new LiveCaptureController.Dependencies(
                    rate -> {
                        if (failAudio) throw new IllegalStateException("audio");
                        return new LiveCaptureAudioHandle() {
                            public int sampleRate() { return 48000; }
                            public int frameRate() { return rate; }
                            public int maxStereoFramesPerPacket() { return 801; }
                            public int drainPresentationFrame(short[] target) {
                                if (failDrain) throw new IllegalStateException("drain");
                                return 800;
                            }
                            public void close() {
                                audioClosed = true;
                                events.add("audio-close");
                                if (failAudioClose) throw new IllegalStateException("audio close");
                            }
                        };
                    },
                    v -> new VideoFrameGrabber() {
                        public int width() { return v.width(); }
                        public int height() { return v.height(); }
                        public byte[] grab() {
                            grabs++;
                            if (failGrab) throw new IllegalStateException("grab");
                            return new byte[v.rgbaByteSize()];
                        }
                    },
                    (v, rate) -> {
                        if (failRecorderCreate) throw new IllegalStateException("recorder");
                        return recorder;
                    }, executor, Duration.ofSeconds(1)));
        }

        final class FakeRecorder extends CaptureRecorder {
            final List<Long> indexes = new ArrayList<>();
            boolean aborted, failStart, failSubmit, failStop;
            java.util.concurrent.CountDownLatch stopGate;

            FakeRecorder() {
                super(new CaptureEncoder() {
                    public void open(Path p, int w, int h, int f, int s) {}
                    public void encode(CapturedFrame frame) {}
                    public Path finish() { return Path.of("out"); }
                    public void abort() {}
                }, BackpressurePolicy.BLOCK, 1, Path.of("target"), "fake", "now");
            }
            @Override public void start(int w, int h, int f, int s) throws CaptureException {
                if (failStart) throw new CaptureException("start");
            }
            @Override public void submit(CapturedFrame frame) throws CaptureException {
                if (failSubmit) throw new CaptureException("submit");
                indexes.add(frame.frameIndex());
            }
            @Override public Path stop() throws CaptureException {
                if (stopGate != null) try { stopGate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                events.add("recorder-stop");
                if (failStop) throw new CaptureException("stop");
                return Path.of("out");
            }
            @Override public void abort() { aborted = true; events.add("recorder-abort"); }
        }
    }
}
