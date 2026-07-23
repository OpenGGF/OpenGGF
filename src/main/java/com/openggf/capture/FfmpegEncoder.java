package com.openggf.capture;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

/**
 * {@link CaptureEncoder} that streams raw RGBA frames to ffmpeg and produces a
 * lossless MKV. Two phases: (1) pipe RGBA -> ffmpeg FFV1 video, append PCM to a
 * temp s16le file; (2) mux video (copy) + FLAC audio into the final file.
 *
 * <p>Y-flip and integer upscale are done by ffmpeg ({@code -vf vflip,scale=...})
 * so the producer hot path is a raw byte write.
 */
public final class FfmpegEncoder implements CaptureEncoder {
    @FunctionalInterface
    interface ProcessLauncher {
        Process start(List<String> command) throws IOException;
    }

    interface LifecycleSeam {
        LifecycleSeam NONE = new LifecycleSeam() {};
        default void beforeMuxPublication(Process process) throws InterruptedException {}
        default void beforeSuccessPublication() throws InterruptedException {}
    }

    private static final long DEFAULT_PROCESS_TIMEOUT_MILLIS = 30_000;

    private final String ffmpeg;
    private final int scale;
    private final int sampleRate0;            // captured for phase 2
    private final ProcessLauncher launcher;
    private final long processTimeoutMillis;
    private final LifecycleSeam lifecycleSeam;
    private final Object lifecycleLock = new Object();
    private Path finalOut;
    private Path tempVideo;
    private Path tempAudio;
    private Process videoProc;
    private Process muxProc;
    private OutputStream videoStdin;
    private OutputStream audioOut;
    private Thread stderrDrain;
    private int sampleRate;
    private boolean terminal;
    private boolean aborted;

    /** @param ffmpeg path/name of the ffmpeg executable; @param scale integer upscale factor */
    public FfmpegEncoder(String ffmpeg, int scale) {
        this(ffmpeg, scale, command -> new ProcessBuilder(command).redirectErrorStream(false).start(),
                DEFAULT_PROCESS_TIMEOUT_MILLIS, LifecycleSeam.NONE);
    }

    FfmpegEncoder(String ffmpeg, int scale, ProcessLauncher launcher, long processTimeoutMillis) {
        this(ffmpeg, scale, launcher, processTimeoutMillis, LifecycleSeam.NONE);
    }

    FfmpegEncoder(String ffmpeg, int scale, ProcessLauncher launcher, long processTimeoutMillis,
                  LifecycleSeam lifecycleSeam) {
        this.ffmpeg = ffmpeg;
        this.scale = Math.max(1, scale);
        this.sampleRate0 = 0;
        this.launcher = launcher;
        this.processTimeoutMillis = Math.max(1, processTimeoutMillis);
        this.lifecycleSeam = lifecycleSeam;
    }

    // ---- pure helpers (unit-tested) ----

    static List<String> phase1Command(String ffmpeg, Path videoOut,
                                      int width, int height, int fps, int scale) {
        List<String> c = new ArrayList<>();
        c.add(ffmpeg);
        c.add("-y");
        c.add("-f"); c.add("rawvideo");
        c.add("-pix_fmt"); c.add("rgba");
        c.add("-s"); c.add(width + "x" + height);
        c.add("-r"); c.add(String.valueOf(fps));
        c.add("-i"); c.add("pipe:0");
        c.add("-vf"); c.add("vflip,scale=" + (width * scale) + ":" + (height * scale)
                + ":flags=neighbor");
        c.add("-c:v"); c.add("ffv1");
        c.add("-an");
        c.add(videoOut.toAbsolutePath().toString());
        return c;
    }

    static List<String> phase2MuxCommand(String ffmpeg, Path videoMkv, Path audioRaw,
                                         int sampleRate, Path finalOut) {
        List<String> c = new ArrayList<>();
        c.add(ffmpeg);
        c.add("-y");
        c.add("-i"); c.add(videoMkv.toAbsolutePath().toString());
        c.add("-f"); c.add("s16le");
        c.add("-ar"); c.add(String.valueOf(sampleRate));
        c.add("-ac"); c.add("2");
        c.add("-i"); c.add(audioRaw.toAbsolutePath().toString());
        c.add("-c:v"); c.add("copy");
        c.add("-c:a"); c.add("flac");
        c.add(finalOut.toAbsolutePath().toString());
        return c;
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Search a PATH-like string for the ffmpeg executable. */
    static Optional<Path> findFfmpegOnPath(String pathEnv) {
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }
        String exe = isWindows() ? "ffmpeg.exe" : "ffmpeg";
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) continue;
            Path candidate = Paths.get(dir, exe);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static Optional<Path> findFfmpeg() {
        return findFfmpegOnPath(System.getenv("PATH"));
    }

    // ---- CaptureEncoder lifecycle (exercised by the Task 4 smoke test) ----

    @Override
    public void open(Path output, int width, int height, int fps, int sampleRate)
            throws CaptureException {
        this.finalOut = output;
        this.sampleRate = sampleRate;
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            this.tempVideo = Files.createTempFile("trace-capture-", ".video.mkv");
            this.tempAudio = Files.createTempFile("trace-capture-", ".audio.raw");
            this.videoProc = launcher.start(
                    phase1Command(ffmpeg, tempVideo, width, height, fps, scale));
            this.videoStdin = videoProc.getOutputStream();
            this.audioOut = Files.newOutputStream(tempAudio);
            this.stderrDrain = drainAsync(videoProc);
        } catch (IOException e) {
            abort();
            throw new CaptureException("failed to start ffmpeg video process", e);
        }
    }

    @Override
    public void encode(CapturedFrame frame) throws CaptureException {
        try {
            videoStdin.write(frame.rgba());
            // s16le little-endian PCM for this frame
            short[] pcm = frame.pcm();
            int n = frame.sampleCount() * 2;
            byte[] bytes = new byte[n * 2];
            for (int i = 0; i < n; i++) {
                short s = pcm[i];
                bytes[i * 2] = (byte) (s & 0xFF);
                bytes[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
            }
            audioOut.write(bytes);
        } catch (IOException e) {
            abort();
            throw new CaptureException("ffmpeg write failed", e);
        }
    }

    @Override
    public Path finish() throws CaptureException {
        synchronized (lifecycleLock) {
            if (aborted) throw new CaptureException("ffmpeg capture aborted");
            if (terminal) throw new CaptureException("ffmpeg capture already finalized");
        }
        boolean success = false;
        try {
            videoStdin.close();              // EOF -> ffmpeg finalizes video
            int vexit = waitForProcess(videoProc, "video");
            if (stderrDrain != null) stderrDrain.join(2000);
            closeAudioOut();
            if (vexit != 0) throw new CaptureException("ffmpeg video exited " + vexit);
            // phase 2 mux
            Process launchedMux = launcher.start(
                    phase2MuxCommand(ffmpeg, tempVideo, tempAudio, sampleRate, finalOut));
            lifecycleSeam.beforeMuxPublication(launchedMux);
            synchronized (lifecycleLock) {
                if (aborted) {
                    destroyAndWait(launchedMux);
                    throw new CaptureException("ffmpeg capture aborted before mux publication");
                }
                muxProc = launchedMux;
            }
            Thread muxDrain = drainAsync(launchedMux);
            int mexit = waitForProcess(launchedMux, "mux");
            muxDrain.join(2000);
            if (mexit != 0) throw new CaptureException("ffmpeg mux exited " + mexit);
            lifecycleSeam.beforeSuccessPublication();
            synchronized (lifecycleLock) {
                if (aborted) throw new CaptureException("ffmpeg capture aborted before success publication");
                terminal = true;
                success = true;
            }
            return finalOut;
        } catch (IOException e) {
            throw new CaptureException("ffmpeg finish failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CaptureException("interrupted during ffmpeg finish", e);
        } finally {
            closeAudioOutQuietly();
            deleteQuietly(tempVideo);
            deleteQuietly(tempAudio);
            if (!success) {
                destroyAndWait(videoProc);
                destroyAndWait(muxProc);
                deleteQuietly(finalOut);
                synchronized (lifecycleLock) { terminal = true; }
            }
        }
    }

    @Override
    public void abort() {
        abortUntil(System.nanoTime() + Duration.ofMillis(processTimeoutMillis).toNanos());
    }

    void abortUntil(long deadlineNanos) {
        Process video;
        Process mux;
        synchronized (lifecycleLock) {
            if (aborted || terminal) return;
            aborted = true;
            video = videoProc;
            mux = muxProc;
        }
        try { if (videoStdin != null) videoStdin.close(); } catch (IOException ignored) { }
        closeAudioOutQuietly();
        forceDestroy(video);
        forceDestroy(mux);
        waitUntil(video, deadlineNanos);
        waitUntil(mux, deadlineNanos);
        deleteQuietly(tempVideo);
        deleteQuietly(tempAudio);
        deleteQuietly(finalOut);
        synchronized (lifecycleLock) { terminal = true; }
    }

    private static void forceDestroy(Process process) {
        if (process != null && process.isAlive()) process.destroyForcibly();
    }

    private static void waitUntil(Process process, long deadlineNanos) {
        if (process == null) return;
        long remaining = Math.max(0, deadlineNanos - System.nanoTime());
        try {
            process.waitFor(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int waitForProcess(Process process, String phase)
            throws InterruptedException, CaptureException {
        if (!process.waitFor(processTimeoutMillis, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(processTimeoutMillis, TimeUnit.MILLISECONDS);
            throw new CaptureException("ffmpeg " + phase + " process timed out");
        }
        return process.exitValue();
    }

    private void destroyAndWait(Process process) {
        if (process == null) return;
        if (process.isAlive()) process.destroyForcibly();
        try {
            process.waitFor(processTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Thread drainAsync(Process p) {
        Thread t = new Thread(() -> {
            try (var in = p.getErrorStream()) {
                byte[] buf = new byte[4096];
                while (in.read(buf) >= 0) {
                    // discard ffmpeg diagnostics; just keep the pipe drained
                }
            } catch (IOException ignored) {
            }
        }, "ffmpeg-stderr-drain");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void closeAudioOut() throws IOException {
        if (audioOut != null) {
            audioOut.close();
            audioOut = null;
        }
    }

    private void closeAudioOutQuietly() {
        try {
            closeAudioOut();
        } catch (IOException ignored) {
        }
    }

    private static void deleteQuietly(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
    }
}
