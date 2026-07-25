package com.openggf.capture;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Driver-agnostic recording façade. A driver calls {@link #start} once,
 * {@link #submit} per frame, then {@link #stop}. The output filename is
 * {@code capture-<label>-<timestamp>.<container>} under {@code outputDir},
 * where the container defaults to {@code mkv}.
 *
 * <p>The timestamp string is injected so callers control formatting/clock and
 * tests stay deterministic.
 */
public class CaptureRecorder {

    private final EncoderSink sink;
    private final Path outputFile;
    private boolean aborted;

    public CaptureRecorder(CaptureEncoder encoder, BackpressurePolicy policy, int queueCapacity,
                           Path outputDir, String label, String timestamp) {
        this(encoder, policy, queueCapacity, outputDir, label, timestamp, "mkv");
    }

    /**
     * @param container output file extension without the dot, e.g. {@code mkv}
     *        or {@code mp4}. ffmpeg selects its muxer from this, so it must
     *        accept the configured codecs — FFV1 has no MP4 mapping, for
     *        instance. Validated here so a bad value fails before a recording
     *        starts rather than at the first frame.
     */
    public CaptureRecorder(CaptureEncoder encoder, BackpressurePolicy policy, int queueCapacity,
                           Path outputDir, String label, String timestamp, String container) {
        this.sink = new EncoderSink(encoder, policy, queueCapacity);
        this.outputFile = outputDir.resolve(
                "capture-" + label + "-" + timestamp + "." + normalizeContainer(container));
    }

    private static String normalizeContainer(String container) {
        String normalized = container == null ? "" : container.trim();
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("capture container must not be empty");
        }
        if (!normalized.matches("[A-Za-z0-9]+")) {
            throw new IllegalArgumentException(
                    "capture container must be a bare file extension such as mkv or mp4,"
                            + " not '" + container + "'");
        }
        return normalized.toLowerCase(java.util.Locale.ROOT);
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

    public synchronized void abort() {
        abort(Duration.ofSeconds(30));
    }

    synchronized void abort(Duration timeout) {
        if (!aborted) {
            aborted = true;
            sink.abort(timeout);
        }
    }

    public long droppedCount() {
        return sink.droppedCount();
    }
}
