package com.openggf.capture;

import com.openggf.audio.LiveCaptureAudioHandle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class LiveCaptureController implements AutoCloseable {
    public enum State { INACTIVE, STARTING, ACTIVE, STOPPING, FAILED }
    public enum StopReason { USER, VIEWPORT_CHANGED, CAPTURE_ERROR, SHUTDOWN }
    public interface AudioHandleFactory { LiveCaptureAudioHandle open(int frameRate); }
    public interface FrameGrabberFactory { VideoFrameGrabber create(CaptureViewport viewport); }
    public interface RecorderFactory { CaptureRecorder create(CaptureViewport viewport, int frameRate); }
    public record Dependencies(AudioHandleFactory audio, FrameGrabberFactory grabber,
                               RecorderFactory recorder, ExecutorService finalizer,
                               Duration shutdownTimeout) {
        public Dependencies {
            Objects.requireNonNull(audio);
            Objects.requireNonNull(grabber);
            Objects.requireNonNull(recorder);
            Objects.requireNonNull(finalizer);
            Objects.requireNonNull(shutdownTimeout);
        }
    }

    private final Dependencies deps;
    private volatile State state = State.INACTIVE;
    private volatile Throwable lastFailure;
    private CaptureViewport viewport;
    private LiveCaptureAudioHandle audio;
    private VideoFrameGrabber grabber;
    private CaptureRecorder recorder;
    private short[] pcm;
    private long frameIndex;
    private Future<?> finalization;

    public LiveCaptureController(Dependencies deps) {
        this.deps = deps;
    }

    public synchronized void start(CaptureViewport viewport, int frameRate) {
        if (state == State.ACTIVE || state == State.STARTING || state == State.STOPPING) return;
        state = State.STARTING;
        lastFailure = null;
        try {
            audio = deps.audio.open(frameRate);
            pcm = new short[Math.multiplyExact(audio.maxStereoFramesPerPacket(), 2)];
            grabber = deps.grabber.create(viewport);
            recorder = deps.recorder.create(viewport, frameRate);
            recorder.start(viewport.width(), viewport.height(), frameRate, audio.sampleRate());
            this.viewport = viewport;
            frameIndex = 0;
            state = State.ACTIVE;
        } catch (Throwable failure) {
            failAndAbort(failure);
        }
    }

    public synchronized void capturePresentedFrame(CaptureViewport currentViewport) {
        if (state != State.ACTIVE) return;
        if (!viewport.equals(currentViewport)) {
            requestStop(StopReason.VIEWPORT_CHANGED);
            return;
        }
        try {
            byte[] rgba = grabber.grab();
            int samples = audio.drainPresentationFrame(pcm);
            recorder.submit(new CapturedFrame(rgba, viewport.width(), viewport.height(),
                    pcm, samples, frameIndex++));
        } catch (Throwable failure) {
            failAndAbort(failure);
        }
    }

    public synchronized void requestStop(StopReason reason) {
        if (state != State.ACTIVE) return;
        state = State.STOPPING;
        Throwable audioCloseFailure = closeAudioOnCaller();
        if (audioCloseFailure != null) {
            if (recorder != null) recorder.abort();
            lastFailure = audioCloseFailure;
            state = State.FAILED;
            clearResources();
            return;
        }
        CaptureRecorder stoppingRecorder = recorder;
        finalization = deps.finalizer.submit(() -> {
            try {
                stoppingRecorder.stop();
                synchronized (LiveCaptureController.this) {
                    if (state == State.STOPPING) state = State.INACTIVE;
                    clearResources();
                }
            } catch (Throwable failure) {
                stoppingRecorder.abort();
                synchronized (LiveCaptureController.this) {
                    if (state == State.STOPPING) {
                        lastFailure = failure;
                        state = State.FAILED;
                    }
                    clearResources();
                }
            }
        });
    }

    public State state() { return state; }
    public Throwable lastFailure() { return lastFailure; }
    public boolean indicatorVisible() { return state == State.ACTIVE; }

    @Override
    public void close() {
        Future<?> pending;
        synchronized (this) {
            if (state == State.ACTIVE) requestStop(StopReason.SHUTDOWN);
            pending = finalization;
        }
        if (pending != null) {
            try {
                pending.get(deps.shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception failure) {
                CaptureRecorder r;
                synchronized (this) { r = recorder; }
                if (r != null) r.abort();
                pending.cancel(true);
            }
        }
        deps.finalizer.shutdown();
        synchronized (this) {
            closeAudioOnCaller();
            state = State.INACTIVE;
            clearResources();
        }
    }

    private void failAndAbort(Throwable failure) {
        closeAudioOnCaller();
        if (recorder != null) recorder.abort();
        lastFailure = failure;
        state = State.FAILED;
        clearResources();
    }

    private Throwable closeAudioOnCaller() {
        if (audio != null) {
            try {
                audio.close();
            } catch (Throwable closeFailure) {
                if (lastFailure == null) lastFailure = closeFailure;
                return closeFailure;
            } finally {
                audio = null;
            }
        }
        return null;
    }

    private void clearResources() {
        viewport = null;
        grabber = null;
        recorder = null;
        pcm = null;
    }
}
