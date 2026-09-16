package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.lwjgl.opengl.GL11.*;

/**
 * Measures loop and render allocations separately, excluding image readback and CSV writes.
 * Origin: September 2026 SOZ pyramid/allocation follow-up. Inputs are a ROM and ordinary
 * controller movie; output is aggregate bytes/frame, sampled route state and optional JFR.
 * This is allocation evidence, not timing or native parity evidence.
 */
public final class GameplayAllocationTool {
    private GameplayAllocationTool() { }

    public static void main(String[] args) throws Exception {
        var supported = java.util.Set.of("--rom", "--input", "--out-dir", "--zone", "--act",
                "--frames", "--warmup", "--width", "--main", "--sidekick", "--rewind", "--jfr");
        var options = new HashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length || !args[i].startsWith("--")) {
                throw new IllegalArgumentException("Expected --name value pairs");
            }
            if (!supported.contains(args[i])) throw new IllegalArgumentException("Unknown option " + args[i]);
            options.put(args[i], args[i + 1]);
        }
        for (String key : new String[]{"--rom", "--input", "--out-dir", "--zone", "--act"}) {
            if (!options.containsKey(key)) throw new IllegalArgumentException("Missing " + key);
        }
        int frames = Integer.parseInt(options.getOrDefault("--frames", "6000"));
        int warmup = Integer.parseInt(options.getOrDefault("--warmup", "600"));
        if (warmup < 0 || frames <= warmup) throw new IllegalArgumentException("Require 0 <= warmup < frames");
        int act = Integer.parseInt(options.get("--act"));
        if (act < 1) throw new IllegalArgumentException("--act is one-based");
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Allocation counters unavailable");
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(options.get("--input")));
        Path out = Path.of(options.get("--out-dir"));
        Files.createDirectories(out);
        String sidekick = options.getOrDefault("--sidekick", "tails").trim().toLowerCase(java.util.Locale.ROOT);
        if (sidekick.equals("none")) sidekick = "";
        var settings = new GameplayCaptureSession.Settings(
                Integer.parseInt(options.getOrDefault("--width", "400")),
                options.getOrDefault("--main", "sonic"), sidekick,
                "off", null, null, null);
        boolean jfr = Boolean.parseBoolean(options.getOrDefault("--jfr", "false"));
        try (var session = new GameplayCaptureSession(settings);
             var recording = new Recording(Configuration.getConfiguration("profile"));
             var csv = Files.newBufferedWriter(out.resolve("state.csv"))) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED,
                    Boolean.parseBoolean(options.getOrDefault("--rewind", "true")));
            GameServices.configuration().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
            session.boot(Path.of(options.get("--rom")), Integer.decode(options.get("--zone")),
                    act - 1, settings);
            GameServices.level().consumePendingInitialProcessSpritesPass();
            System.out.printf("CONFIG width=%d main=%s registered_sidekicks=%d rewind=%s%n",
                    session.width(), session.player().getCode(),
                    GameServices.sprites().getRegisteredSidekicks().size(),
                    options.getOrDefault("--rewind", "true"));
            recording.enable("jdk.ObjectAllocationSample").with("throttle", "300/s");
            if (jfr) recording.setDestination(out.resolve("allocation.jfr"));
            csv.write(GameplayCaptureSession.stateHeader() + ",logic_bytes,render_bytes\n");
            long logic = 0, render = 0;
            for (int frame = 0; frame < frames; frame++) {
                if (jfr && frame == warmup) recording.start();
                long a = bean.getCurrentThreadAllocatedBytes();
                var input = movie.getFrame(frame);
                session.step(input);
                long b = bean.getCurrentThreadAllocatedBytes();
                var graphics = GameServices.graphics();
                graphics.runPendingRenderThreadTasks();
                GameServices.level().setClearColor();
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                GameServices.level().drawWithSpritePriority(GameServices.sprites(), true);
                graphics.flush();
                glFinish();
                long c = bean.getCurrentThreadAllocatedBytes();
                if (frame >= warmup) {
                    logic += b - a;
                    render += c - b;
                }
                if (frame % 120 == 0) {
                    csv.write(session.stateLine(frame, input) + "," + (b - a) + "," + (c - b) + "\n");
                }
            }
            if (jfr) recording.stop();
            System.out.printf("COMPLETE measured_frames=%d logic_bytes_per_frame=%.3f render_bytes_per_frame=%.3f%n",
                    frames - warmup, logic / (double) (frames - warmup), render / (double) (frames - warmup));
        }
    }
}
