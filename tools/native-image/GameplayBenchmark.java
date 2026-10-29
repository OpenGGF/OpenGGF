package com.openggf.tools.nativeimage;

import com.openggf.bench.SectionTiming;
import com.openggf.bench.TrajectoryDigest;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.tools.GameplayCaptureSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import static org.lwjgl.opengl.GL11.*;

/** Unpaced production GameLoop + completed offscreen rendering comparison.
 * Inputs: original S3K ROM, committed level-local BK2, JSON output, traversal
 * frames, measured frames and optional discarded full passes (for JVM JIT).
 * Origin: 2026-10-27 Community -Os versus default optimization measurement.
 * Run prebuilt artifacts through tools/performance/run_isolated.py.
 */
public final class GameplayBenchmark {
    public static void main(String[] args) throws Exception {
        if (args.length != 6) throw new IllegalArgumentException(
                "rom bk2 json traversal-frames measured-frames discarded-passes");
        Path rom = Path.of(args[0]), input = Path.of(args[1]), output = Path.of(args[2]);
        int traversal = Integer.parseInt(args[3]), count = Integer.parseInt(args[4]);
        int discardedPasses = Integer.parseInt(args[5]);
        if (traversal < 0 || count < 1 || discardedPasses < 0)
            throw new IllegalArgumentException("Invalid frame/pass count");
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        var movie = new Bk2MovieLoader().load(input);
        if (movie.getFrameCount() < traversal + count)
            throw new IllegalArgumentException("Movie shorter than requested window");
        long[] wall = new long[count], cpu = new long[count];
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var clock = new LinuxProcessCpuClock()) {
          for (int pass = 0; pass <= discardedPasses; pass++) {
            boolean measured = pass == discardedPasses;
            var digest = new TrajectoryDigest();
            long objectObservations = 0;
            try (var session = new GameplayCaptureSession(settings)) {
                // This movie includes the ordinary native AIZ intro, not a trace bootstrap.
                GameServices.configuration().setSessionOverride(
                        com.openggf.configuration.SonicConfiguration.S3K_SKIP_INTROS, false);
                session.boot(rom, 0, 0, settings);
                String renderer = glGetString(GL_RENDERER), version = glGetString(GL_VERSION);
                for (int frame = 0; frame < traversal + count; frame++) {
                    long cpuStart = clock.now(), start = System.nanoTime();
                    session.step(movie.getFrame(frame));
                    session.renderFrame(); // includes glFinish, with no pacing or readback
                    long elapsed = System.nanoTime() - start;
                    long usedCpu = clock.now() - cpuStart;
                    if (pass == 0 && frame == 0) System.out.println("BENCH_FIRST_FRAME");
                    if (measured && frame >= traversal) {
                        wall[frame - traversal] = elapsed;
                        cpu[frame - traversal] = usedCpu;
                        var player = session.player();
                        var camera = GameServices.camera();
                        digest.observe(frame, player.getCentreX(), player.getCentreY(), player.getRingCount(),
                                camera.getX(), camera.getY());
                        objectObservations += GameServices.level().getObjectManager().getActiveObjects().size();
                    }
                }
                if (measured) {
                    // Positive rendering control, outside the measured window.
                    var pixels = session.render().pixels();
                    if (java.util.Arrays.stream(pixels).distinct().limit(2).count() < 2)
                        throw new IllegalStateException("Blank framebuffer");
                    String json = "{\n" + timing("wall", wall) + ",\n" + timing("process_cpu", cpu)
                            + String.format(Locale.ROOT,
                            ",\n\"traversal_frames\":%d,\"measured_frames\":%d,\"discarded_passes\":%d,"
                            + "\"trajectory\":\"%s\",\"object_observations\":%d,\"final_x\":%d,\"final_y\":%d,"
                            + "\"pixel_hash\":%d,\"renderer\":\"%s\",\"gl_version\":\"%s\"\n}\n",
                            traversal, count, discardedPasses, digest.hex(), objectObservations,
                            session.player().getCentreX(), session.player().getCentreY(),
                            java.util.Arrays.hashCode(pixels), renderer, version);
                    Files.createDirectories(output.toAbsolutePath().getParent());
                    Files.writeString(output, json);
                    System.out.println("BENCH_RESULT " + json.replace('\n', ' '));
                }
            }
          }
        }
    }

    /** Avoid MXBean times(2) tick quantization on Linux: the same nanosecond
     * CLOCK_PROCESS_CPUTIME_ID counter in every runtime, including driver/JIT/GC
     * threads. LWJGL already provides the JNI bridge; no added native library.
     */
    private static final class LinuxProcessCpuClock implements AutoCloseable {
        private final java.nio.ByteBuffer timespec;
        private final long function;
        private final long library;

        LinuxProcessCpuClock() {
            if (org.lwjgl.system.Platform.get() != org.lwjgl.system.Platform.LINUX
                    || org.lwjgl.system.Pointer.POINTER_SIZE != 8)
                throw new IllegalStateException("CPU benchmark requires 64-bit Linux");
            library = org.lwjgl.system.linux.DynamicLinkLoader.dlopen("libc.so.6", 1); // RTLD_LAZY
            if (library == 0) throw new IllegalStateException("Cannot open libc");
            function = org.lwjgl.system.linux.DynamicLinkLoader.dlsym(library, "clock_gettime");
            if (function == 0) throw new IllegalStateException("clock_gettime unavailable");
            timespec = org.lwjgl.system.MemoryUtil.memAlloc(16);
        }

        long now() {
            long address = org.lwjgl.system.MemoryUtil.memAddress(timespec);
            // Linux CLOCK_PROCESS_CPUTIME_ID=2; struct timespec has two 64-bit longs.
            if (org.lwjgl.system.JNI.invokePI(2, address, function) != 0)
                throw new IllegalStateException("Process CPU clock failed");
            return org.lwjgl.system.MemoryUtil.memGetLong(address) * 1_000_000_000L
                    + org.lwjgl.system.MemoryUtil.memGetLong(address + 8);
        }

        @Override public void close() {
            org.lwjgl.system.MemoryUtil.memFree(timespec);
            org.lwjgl.system.linux.DynamicLinkLoader.dlclose(library);
        }
    }

    private static String timing(String name, long[] samples) {
        var t = SectionTiming.of(name, samples, samples.length);
        return String.format(Locale.ROOT,
                "\"%s\":{\"mean_ms\":%.6f,\"median_ms\":%.6f,\"p99_ms\":%.6f,\"max_ms\":%.6f,\"total_s\":%.6f}",
                name, t.meanMillis(), t.p50Millis(), t.p99Millis(), t.maxMillis(), t.totalNanos() / 1e9);
    }
}
