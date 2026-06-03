package com.openggf.capture;

import java.nio.file.Path;

/**
 * Driver-agnostic recording façade. A driver calls {@link #start} once,
 * {@link #submit} per frame, then {@link #stop}. The output filename is
 * {@code capture-<label>-<timestamp>.mkv} under {@code outputDir}.
 *
 * <p>The timestamp string is injected so callers control formatting/clock and
 * tests stay deterministic.
 */
public final class CaptureRecorder {

    private final EncoderSink sink;
    private final Path outputFile;

    public CaptureRecorder(CaptureEncoder encoder, BackpressurePolicy policy, int queueCapacity,
                           Path outputDir, String label, String timestamp) {
        this.sink = new EncoderSink(encoder, policy, queueCapacity);
        this.outputFile = outputDir.resolve("capture-" + label + "-" + timestamp + ".mkv");
    }

    public Path outputFile() {
        return outputFile;
    }

    public void start(int width, int height, int fps, int sampleRate) throws CaptureException {
        sink.open(outputFile, width, height, fps, sampleRate);
    }

    public void submit(CapturedFrame frame) throws CaptureException {
        sink.submit(frame);
    }

    /** Drains and finalizes; returns the encoder's written file. */
    public Path stop() throws CaptureException {
        return sink.stop();
    }

    public long droppedCount() {
        return sink.droppedCount();
    }
}
