package com.openggf.tools;

import com.openggf.bench.SectionTimeline;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Frame-hitch probe over an engine-authored route movie.
 *
 * <p>Purpose: find the frames a player would feel as stutter on a real route.
 * {@link TraceBenchmarkTool} replays recorded traces, which leave their route
 * wherever the engine still diverges from the ROM; a route movie authored
 * against the engine stays on its route. This drives the movie through
 * {@link GameplayCaptureSession} (the real {@code GameLoop}, live rewind
 * recording when asked, offscreen GL rendering with a {@code glFinish}) and
 * writes one CSV row per frame: wall time, every profiler section, bytes the
 * thread allocated, garbage collections that completed and the player's zone,
 * act and position, so a spike can be placed on the route.
 *
 * <p>Inputs: {@code --route <bk2>} (required), {@code --game s1|s2|s3k}
 * (default s3k), {@code --zone}/{@code --act} (boot level, default LRZ1),
 * {@code --main}/{@code --sidekick} (an empty sidekick is solo),
 * {@code --width} (default 320), {@code --title-card}, {@code --intros}
 * (S3K intros on), {@code --emeralds <states>} and
 * {@code --vint-run-count N} (the authored launch state of a route),
 * {@code --setup-step} (one neutral step before the movie, as captures that
 * consume the one-shot setup pass do) and {@code --input-start N} (first movie
 * row to drive),
 * {@code --frames N} (default whole movie), {@code --rewind}, {@code --audio}
 * (presents one outer audio frame per step, as {@code GameLoop} and
 * {@link TraceBenchmarkTool} do; without it the SMPS driver never advances,
 * so an extra-life jingle never restores and queued commands drain in bursts),
 * {@code --paced} (sleep to 60 Hz so background preparers get real wall time)
 * and {@code --frame-log <csv>}. Origin: 2026-11-12 LRZ frame-budget study;
 * launch-state flags added by the 2026-11-13 all-level performance sweep
 * ({@code tools/performance/level_sweep.py}).
 */
public final class RouteFrameBudgetProbe {
    private RouteFrameBudgetProbe() {
    }

    public static void main(String[] argv) throws Exception {
        Path route = null;
        Path frameLog = null;
        int zone = 9;
        int act = 0;
        int frames = -1;
        String main = "sonic";
        String sidekick = "tails";
        boolean rewind = false;
        boolean audio = false;
        boolean paced = false;
        String game = "s3k";
        int width = 320;
        boolean titleCard = false;
        boolean intros = false;
        String emeralds = null;
        Integer vIntRunCount = null;
        boolean setupStep = false;
        int inputStart = 0;
        for (int i = 0; i < argv.length; i++) {
            switch (argv[i]) {
                case "--route" -> route = Path.of(argv[++i]);
                case "--frame-log" -> frameLog = Path.of(argv[++i]);
                case "--zone" -> zone = Integer.parseInt(argv[++i]);
                case "--act" -> act = Integer.parseInt(argv[++i]);
                case "--frames" -> frames = Integer.parseInt(argv[++i]);
                case "--main" -> main = argv[++i];
                case "--sidekick" -> sidekick = argv[++i];
                case "--rewind" -> rewind = true;
                case "--audio" -> audio = true;
                case "--paced" -> paced = true;
                case "--game" -> game = argv[++i];
                case "--width" -> width = Integer.parseInt(argv[++i]);
                case "--title-card" -> titleCard = true;
                case "--intros" -> intros = true;
                case "--emeralds" -> emeralds = argv[++i];
                case "--vint-run-count" -> vIntRunCount = Integer.parseInt(argv[++i]);
                case "--setup-step" -> setupStep = true;
                case "--input-start" -> inputStart = Integer.parseInt(argv[++i]);
                default -> throw new IllegalArgumentException("Unknown argument: " + argv[i]);
            }
        }
        if (route == null || frameLog == null) {
            throw new IllegalArgumentException("--route <bk2> and --frame-log <csv> are required");
        }
        Bk2Movie movie = new Bk2MovieLoader().loadMovieOrInputLog(route);
        int available = Math.max(0, movie.getFrameCount() - inputStart);
        int count = frames < 0 ? available : Math.min(frames, available);
        var settings = new GameplayCaptureSession.Settings(width, main, sidekick, "off", null, null, null,
                emeralds, titleCard, false, vIntRunCount, null, false, null, false);

        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        threads.setThreadAllocatedMemoryEnabled(true);
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        SectionTimeline timeline = new SectionTimeline(count);
        double[] wall = new double[count];
        double[] stepMs = new double[count];
        long[] epochMicros = new long[count];
        double[] renderMs = new double[count];
        long[] alloc = new long[count];
        long[] gcCount = new long[count];
        long[] gcMs = new long[count];
        int[][] where = new int[count][6];

        try (var session = new GameplayCaptureSession(settings)) {
            Path rom = TraceToolRomLocations.resolve(game, GameServices.configuration(), Path.of(""));
            if (intros) {
                GameServices.configuration().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
            }
            if (rewind) {
                session.enableLiveRewind();
            }
            if (audio) {
                session.enableAudio();
            }
            session.boot(rom, zone, act, settings);
            if (rewind) {
                GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
            }
            if (setupStep) {
                session.step(null);
                if (audio) {
                    HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                }
            }
            PerformanceProfiler profiler = GameServices.profiler();
            profiler.reset();
            profiler.setEnabled(true);
            profiler.setSampleSink(timeline);
            System.out.printf(Locale.ROOT, "Route %s: %d frames, rewind=%s audio=%s paced=%s%n",
                    route.getFileName(), count, rewind, audio, paced);
            for (int frame = 0; frame < count; frame++) {
                long start = System.nanoTime();
                java.time.Instant now = java.time.Instant.now();
                epochMicros[frame] = now.getEpochSecond() * 1_000_000L + now.getNano() / 1000;
                long allocStart = threads.getCurrentThreadAllocatedBytes();
                long gcStart = collections(collectors, false);
                long gcMsStart = collections(collectors, true);
                profiler.beginFrame();
                session.step(movie.getFrame(inputStart + frame));
                long stepped = System.nanoTime();
                session.renderFrame();
                long rendered = System.nanoTime();
                if (audio) {
                    profiler.beginSection("audio");
                    HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                    profiler.endSection("audio");
                }
                stepMs[frame] = (stepped - start) / 1e6;
                renderMs[frame] = (rendered - stepped) / 1e6;
                profiler.beginSection("render.gpu_wait");
                glFinish();
                profiler.endSection("render.gpu_wait");
                profiler.endFrame();
                long end = System.nanoTime();
                wall[frame] = (end - start) / 1e6;
                alloc[frame] = threads.getCurrentThreadAllocatedBytes() - allocStart;
                gcCount[frame] = collections(collectors, false) - gcStart;
                gcMs[frame] = collections(collectors, true) - gcMsStart;
                var level = GameServices.levelOrNull();
                var player = session.player();
                var camera = GameServices.camera();
                where[frame][0] = level != null ? level.getCurrentZone() : -1;
                where[frame][1] = level != null ? level.getCurrentAct() : -1;
                where[frame][2] = player != null ? player.getCentreX() : -1;
                where[frame][3] = player != null ? player.getCentreY() : -1;
                where[frame][4] = camera.getX();
                where[frame][5] = camera.getY();
                if (paced) {
                    paceTo(start + 16_666_667L);
                }
                if (frame % 5000 == 0) {
                    System.out.printf(Locale.ROOT, "  frame %d zone %d act %d at %d,%d%n",
                            frame, where[frame][0], where[frame][1], where[frame][2], where[frame][3]);
                }
            }
            profiler.setSampleSink(null);
        }
        write(frameLog, timeline, epochMicros, wall, stepMs, renderMs, alloc, gcCount, gcMs, where);
        System.out.println("Wrote per-frame log -> " + frameLog.toAbsolutePath());
    }

    private static long collections(List<GarbageCollectorMXBean> collectors, boolean time) {
        long total = 0;
        for (var collector : collectors) {
            total += Math.max(0, time ? collector.getCollectionTime() : collector.getCollectionCount());
        }
        return total;
    }

    private static void paceTo(long deadline) {
        long now;
        while ((now = System.nanoTime()) < deadline) {
            if (deadline - now > 2_000_000L) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } else {
                Thread.onSpinWait();
            }
        }
    }

    private static void write(Path path, SectionTimeline timeline, long[] epochMicros, double[] wall, double[] stepMs,
                              double[] renderMs, long[] alloc,
                              long[] gcCount, long[] gcMs, int[][] where) throws java.io.IOException {
        List<String> sections = timeline.sectionNames();
        StringBuilder out = new StringBuilder("frame,epoch_us,wall_ms,step_ms,render_ms,alloc_kb,gc_count,gc_ms,zone,act,"
                + "player_x,player_y,camera_x,camera_y");
        for (String section : sections) {
            out.append(',').append(section.replace(',', '_'));
        }
        out.append('\n');
        for (int i = 0; i < timeline.frameCount(); i++) {
            out.append(String.format(Locale.ROOT, "%d,%d,%.3f,%.3f,%.3f,%.1f,%d,%d", i, epochMicros[i], wall[i], stepMs[i],
                    renderMs[i], alloc[i] / 1024.0,
                    gcCount[i], gcMs[i]));
            for (int value : where[i]) {
                out.append(',').append(value);
            }
            for (String section : sections) {
                out.append(',').append(String.format(Locale.ROOT, "%.3f", timeline.rawSection(section)[i] / 1e6));
            }
            out.append('\n');
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, out.toString());
    }
}
