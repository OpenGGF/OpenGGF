package com.openggf.capture;

import com.openggf.tests.TestTempFiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FfmpegEncoderCommandTest {

    @Test
    void phase1CommandHasRawVideoInputAndScaledFfv1Output() {
        List<String> cmd = FfmpegEncoder.phase1Command(
                "ffmpeg", Path.of("/tmp/v.mkv"), 320, 224, 60, 4);
        // raw rgba stdin input
        assertTrue(cmd.contains("rawvideo"));
        assertEquals("rgba", cmd.get(cmd.indexOf("-pix_fmt") + 1));
        assertEquals("320x224", cmd.get(cmd.indexOf("-s") + 1));
        assertEquals("60", cmd.get(cmd.indexOf("-r") + 1));
        assertEquals("pipe:0", cmd.get(cmd.indexOf("-i") + 1));
        // vflip + 4x nearest-neighbor scale
        String vf = cmd.get(cmd.indexOf("-vf") + 1);
        assertTrue(vf.contains("vflip"), vf);
        assertTrue(vf.contains("scale=1280:896:flags=neighbor"), vf);
        // ffv1 video codec, output last
        assertEquals("ffv1", cmd.get(cmd.indexOf("-c:v") + 1));
        assertTrue(cmd.get(cmd.size() - 1).endsWith("v.mkv"));
    }

    @Test
    void phase2MuxCopiesVideoAndEncodesFlac() {
        List<String> cmd = FfmpegEncoder.phase2MuxCommand(
                "ffmpeg", Path.of("/tmp/v.mkv"), Path.of("/tmp/a.raw"),
                48000, Path.of("/out/final.mkv"));
        assertEquals("copy", cmd.get(cmd.indexOf("-c:v") + 1));
        assertEquals("flac", cmd.get(cmd.indexOf("-c:a") + 1));
        // raw s16le audio input declared
        assertTrue(cmd.contains("s16le"));
        assertEquals("48000", cmd.get(cmd.indexOf("-ar") + 1));
        assertEquals("2", cmd.get(cmd.indexOf("-ac") + 1));
        assertTrue(cmd.get(cmd.size() - 1).endsWith("final.mkv"));
    }

    @Test
    void findFfmpegReturnsEmptyForBogusPath() {
        assertTrue(FfmpegEncoder.findFfmpegOnPath("").isEmpty());
    }

    @Test
    void finishClosesAudioOutputBeforeCheckingVideoExit() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/capture/FfmpegEncoder.java"));

        assertFalse(source.contains("if (vexit != 0) throw new CaptureException(\"ffmpeg video exited \" + vexit);\n"
                        + "            audioOut.close();"),
                "finish must close audioOut even when the video ffmpeg process exits non-zero");
        assertTrue(source.contains("closeAudioOut();"),
                "finish should route audio cleanup through a guarded helper");
    }

    @Test
    void implementationRetainsAndAbortsBothPhaseProcesses() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/capture/FfmpegEncoder.java"));
        assertTrue(source.contains("private Process videoProc;"));
        assertTrue(source.contains("private Process muxProc;"));
        assertTrue(source.contains("destroyAndWait(videoProc);"));
        assertTrue(source.contains("destroyAndWait(muxProc);"));
        assertTrue(source.contains("deleteQuietly(finalOut);"));
        assertTrue(source.contains("interface ProcessLauncher"));
        assertTrue(source.contains("waitFor(processTimeoutMillis, TimeUnit.MILLISECONDS)"));
    }

    @Test
    void videoHangIsForciblyDestroyedWithinBoundedWait() throws Exception {
        FakeProcess video = new FakeProcess(false);
        Path output = TestTempFiles.createTempDirectory("ffmpeg-test").resolve("out.mkv");
        FfmpegEncoder encoder = new FfmpegEncoder("ffmpeg", 1, command -> video, 5);
        encoder.open(output, 1, 1, 60, 48000);
        CaptureException failure = assertThrows(CaptureException.class, encoder::finish);
        assertTrue(failure.getMessage().contains("video process timed out"));
        assertTrue(video.destroyed);
        assertFalse(Files.exists(output));
    }

    @Test
    void muxHangIsDestroyedAndPartialOutputDeleted() throws Exception {
        FakeProcess video = new FakeProcess(true);
        FakeProcess mux = new FakeProcess(false);
        Path output = TestTempFiles.createTempDirectory("ffmpeg-test").resolve("out.mkv");
        int[] launches = {0};
        FfmpegEncoder encoder = new FfmpegEncoder("ffmpeg", 1, command -> {
            if (launches[0]++ == 0) return video;
            Files.writeString(output, "partial");
            return mux;
        }, 5);
        encoder.open(output, 1, 1, 60, 48000);
        CaptureException failure = assertThrows(CaptureException.class, encoder::finish);
        assertTrue(failure.getMessage().contains("mux process timed out"));
        assertTrue(mux.destroyed);
        assertFalse(Files.exists(output));
    }

    @Test
    void abortBeforeMuxPublicationDestroysUnpublishedMuxAndPreventsSuccess() throws Exception {
        FakeProcess video = new FakeProcess(true);
        FakeProcess mux = new FakeProcess(false);
        Path output = TestTempFiles.createTempDirectory("ffmpeg-race").resolve("out.mkv");
        CountDownLatch atPublication = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        int[] launches = {0};
        FfmpegEncoder encoder = new FfmpegEncoder("ffmpeg", 1, command -> {
            if (launches[0]++ == 0) return video;
            Files.writeString(output, "partial");
            return mux;
        }, 20, new FfmpegEncoder.LifecycleSeam() {
            @Override public void beforeMuxPublication(Process process) throws InterruptedException {
                atPublication.countDown();
                release.await();
            }
        });
        encoder.open(output, 1, 1, 60, 48000);
        AsyncFinish finish = finishAsync(encoder);
        assertTrue(atPublication.await(1, TimeUnit.SECONDS));
        encoder.abort();
        release.countDown();
        awaitFinish(finish);
        assertInstanceOf(CaptureException.class, finish.result().get());
        assertTrue(mux.destroyed, "the launched-but-unpublished mux must not escape");
        assertFalse(Files.exists(output));
    }

    @Test
    void abortBeforeSuccessPublicationPreventsReturningDeletedPath() throws Exception {
        FakeProcess video = new FakeProcess(true);
        FakeProcess mux = new FakeProcess(true);
        Path output = TestTempFiles.createTempDirectory("ffmpeg-race").resolve("out.mkv");
        CountDownLatch atSuccess = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        int[] launches = {0};
        FfmpegEncoder encoder = new FfmpegEncoder("ffmpeg", 1, command -> {
            if (launches[0]++ == 0) return video;
            Files.writeString(output, "complete-looking");
            return mux;
        }, 20, new FfmpegEncoder.LifecycleSeam() {
            @Override public void beforeSuccessPublication() throws InterruptedException {
                atSuccess.countDown();
                release.await();
            }
        });
        encoder.open(output, 1, 1, 60, 48000);
        AsyncFinish finish = finishAsync(encoder);
        assertTrue(atSuccess.await(1, TimeUnit.SECONDS));
        encoder.abort();
        release.countDown();
        awaitFinish(finish);
        assertInstanceOf(CaptureException.class, finish.result().get(),
                "finish must not return a path after abort deleted it");
        assertFalse(Files.exists(output));
    }

    private record AsyncFinish(Thread thread, AtomicReference<Object> result) {}

    private static AsyncFinish finishAsync(FfmpegEncoder encoder) {
        AtomicReference<Object> result = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                result.set(encoder.finish());
            } catch (Throwable failure) {
                result.set(failure);
            }
        }, "ffmpeg-finish-race-test");
        thread.start();
        return new AsyncFinish(thread, result);
    }

    private static void awaitFinish(AsyncFinish finish) throws InterruptedException {
        finish.thread().join(1_000);
        assertFalse(finish.thread().isAlive());
    }

    private static final class FakeProcess extends Process {
        private final boolean exits;
        private final ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        private volatile boolean destroyed;

        private FakeProcess(boolean exits) { this.exits = exits; }
        @Override public OutputStream getOutputStream() { return stdin; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() throws InterruptedException {
            while (!exits && !destroyed) Thread.sleep(1);
            return 0;
        }
        @Override public boolean waitFor(long timeout, TimeUnit unit) {
            return exits || destroyed;
        }
        @Override public int exitValue() {
            if (!exits && !destroyed) throw new IllegalThreadStateException();
            return 0;
        }
        @Override public void destroy() { destroyed = true; }
        @Override public Process destroyForcibly() { destroyed = true; return this; }
        @Override public boolean isAlive() { return !exits && !destroyed; }
    }
}
