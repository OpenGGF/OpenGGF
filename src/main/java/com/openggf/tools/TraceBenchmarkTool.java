package com.openggf.tools;

import com.openggf.LevelFrameStep;
import com.openggf.bench.BenchmarkReport;
import com.openggf.bench.BenchmarkReportIo;
import com.openggf.bench.GcSnapshot;
import com.openggf.bench.JvmEnvironment;
import com.openggf.bench.SectionTimeline;
import com.openggf.bench.SectionTiming;
import com.openggf.bench.SteadyStateDetector;
import com.openggf.bench.TrajectoryDigest;
import com.openggf.camera.Camera;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Headless benchmark driver: replays a recorded trace as fast as the machine
 * allows and reports the distribution of per-frame and per-subsystem times, so
 * the same deterministic workload can be measured across JVMs.
 *
 * <p>It deliberately reuses the trace-replay drive rather than a synthetic
 * workload. A benchmark is only worth running if it executes the code that
 * actually ships, on input that actually occurs; a trace gives both, plus the
 * property that every runtime is handed a bit-identical sequence of frames.
 *
 * <h2>What makes the numbers valid</h2>
 * <ul>
 *   <li><b>No pacing.</b> The loop never sleeps and never waits for vsync. At
 *       60Hz the engine finishes a frame in a fraction of its 16.6ms budget, so
 *       a paced run reports 16.6ms on every JVM and measures nothing.</li>
 *   <li><b>Fixed frame window.</b> Every iteration warms up over the same
 *       frames and measures the same frames, so iterations and runtimes are
 *       compared over identical work rather than identical wall time.</li>
 *   <li><b>Percentiles, not means.</b> Raw per-frame samples are kept and
 *       summarised afterwards — see {@link SectionTiming}.</li>
 *   <li><b>Trajectory digest.</b> Each run hashes the trajectory it simulated.
 *       Runtimes whose digests disagree did different work and cannot be
 *       compared, however clean their timings look.</li>
 *   <li><b>Allocation tracking off.</b> The overlay's per-section allocation
 *       counters cost a JMX call inside every measured section, and that call's
 *       cost is itself JVM-dependent. Opt back in with
 *       {@code --track-allocations} when the allocation profile is the
 *       question, but not when the timings are.</li>
 * </ul>
 *
 * <h2>Modes</h2>
 * {@code --mode update} (the default) drives gameplay with no rendering: this is
 * the real JVM comparison — physics, collision, object execution, audio
 * synthesis. {@code --mode full} additionally renders each frame; those timings
 * are dominated by the graphics driver rather than the runtime, so treat them as
 * a sanity check, not a ranking.
 *
 * <p>Invocation (Maven):
 * <pre>
 *   mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceBenchmarkTool" \
 *       "-Dexec.args=--trace aiz1 --json target/bench/temurin21-g1.json"
 * </pre>
 *
 * <p>Compare the resulting JSON files with {@link BenchmarkCompareTool}, or run
 * the whole runtime matrix with {@code scripts/bench-jvms.sh}.
 */
public final class TraceBenchmarkTool {

    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;

    static final String MODE_UPDATE = "update";
    static final String MODE_FULL = "full";

    private TraceBenchmarkTool() {
    }

    /** Parsed CLI arguments. */
    record Args(String trace, String mode, int warmupFrames, int measureFrames, int iterations,
                Path json, Path markdown, String label, boolean trackAllocations, boolean audio) {

        static Args parse(String[] argv) {
            String trace = null;
            String mode = MODE_UPDATE;
            int warmupFrames = 2000;
            int measureFrames = 10000;
            int iterations = 3;
            Path json = null;
            Path markdown = null;
            String label = null;
            boolean trackAllocations = false;
            boolean audio = true;

            for (int i = 0; i < argv.length; i++) {
                String arg = argv[i];
                switch (arg) {
                    case "--trace" -> trace = CliArguments.requireValue(argv, ++i, arg);
                    case "--mode" -> mode = CliArguments.requireValue(argv, ++i, arg);
                    case "--warmup-frames" -> warmupFrames = parseCount(argv, ++i, arg, 0);
                    case "--measure-frames" -> measureFrames = parseCount(argv, ++i, arg, 1);
                    case "--iterations" -> iterations = parseCount(argv, ++i, arg, 1);
                    case "--json" -> json = Path.of(CliArguments.requireValue(argv, ++i, arg));
                    case "--markdown" -> markdown = Path.of(CliArguments.requireValue(argv, ++i, arg));
                    case "--label" -> label = CliArguments.requireValue(argv, ++i, arg);
                    case "--track-allocations" -> trackAllocations = true;
                    case "--no-audio" -> audio = false;
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            if (trace == null || trace.isBlank()) {
                throw new IllegalArgumentException("--trace <id|name|dir> is required");
            }
            if (!MODE_UPDATE.equals(mode) && !MODE_FULL.equals(mode)) {
                throw new IllegalArgumentException(
                        "--mode must be '" + MODE_UPDATE + "' or '" + MODE_FULL + "', got: " + mode);
            }
            return new Args(trace, mode, warmupFrames, measureFrames, iterations,
                    json, markdown, label, trackAllocations, audio);
        }

        private static int parseCount(String[] argv, int index, String flag, int minimum) {
            int value = CliArguments.parseInt(CliArguments.requireValue(argv, index, flag));
            if (value < minimum) {
                throw new IllegalArgumentException(flag + " must be >= " + minimum + ", got " + value);
            }
            return value;
        }

    }

    public static void main(String[] argv) {
        // CLI composition root: wire process-wide services before any config is
        // read, mirroring how Engine bootstraps EngineServices.
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        Args args = Args.parse(argv);
        HeadlessGameBoot boot = null;
        try {
            boot = run(args);
        } catch (Exception e) {
            System.err.println("Trace benchmark failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (boot != null) {
                try {
                    SessionManager.closeGameplaySession();
                } catch (Exception ignored) {
                    // best-effort teardown
                }
                boot.close();
            }
        }
    }

    private static HeadlessGameBoot run(Args args) throws Exception {
        TraceEntry entry = TraceCaptureTool.resolveTrace(args.trace());
        TraceData trace = TraceData.load(entry.dir());
        TraceMetadata meta = trace.metadata();
        Bk2Movie movie = new Bk2MovieLoader().load(entry.bk2Path());
        String traceLabel = entry.dir().getFileName().toString();

        System.out.println("Benchmarking trace: " + entry.dir()
                + " (" + entry.gameId() + " zone=" + entry.zone() + " act=" + entry.act() + ")");
        System.out.println("Mode: " + args.mode()
                + "  warmup=" + args.warmupFrames()
                + "  measure=" + args.measureFrames()
                + "  iterations=" + args.iterations());

        JvmEnvironment environment = JvmEnvironment.capture();
        System.out.println("Runtime: " + environment.vmName() + " " + environment.vmVersion()
                + " (" + environment.vmVendor() + ", Java " + environment.javaVersion() + ")");
        System.out.println("Flags: " + String.join(" ", environment.inputArguments()));
        warnAboutSuspectFlags(environment);

        TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);
        Path romPath = TraceToolRomLocations.resolve(
                entry.gameId(), GameServices.configuration(), Path.of(""));
        HeadlessGameBoot boot = new HeadlessGameBoot(SCREEN_WIDTH, SCREEN_HEIGHT);
        HardwareReadinessAdmissionPolicy admissionPolicy =
                admissionPolicyFor(trace);
        boot.boot(romPath, entry.zone(), entry.act(), admissionPolicy);

        List<BenchmarkReport.Iteration> iterations = new ArrayList<>();
        for (int index = 0; index < args.iterations(); index++) {
            if (index > 0) {
                // A fresh session per iteration: replaying a second time over a
                // session whose objects have already run is not the same
                // workload, and would make later iterations quietly cheaper.
                TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);
                boot.reboot(
                        romPath, entry.zone(), entry.act(), admissionPolicy);
            }
            BenchmarkReport.Iteration iteration =
                    runIteration(index, args, trace, movie);
            iterations.add(iteration);
            printIteration(iteration);
        }

        BenchmarkReport report = new BenchmarkReport(
                args.label() != null ? args.label() : environment.shortLabel(),
                traceLabel, entry.gameId(), entry.zone(), entry.act(), args.mode(),
                args.warmupFrames(), args.measureFrames(), environment, iterations);

        if (args.json() != null) {
            BenchmarkReportIo.write(report, args.json());
            System.out.println("Wrote report -> " + args.json().toAbsolutePath());
        }
        if (args.markdown() != null) {
            BenchmarkCompareTool.writeMarkdown(List.of(report), args.markdown());
            System.out.println("Wrote summary -> " + args.markdown().toAbsolutePath());
        }
        printSummary(report);
        return boot;
    }

    static HardwareReadinessAdmissionPolicy admissionPolicyFor(TraceData trace) {
        return admissionPolicyFor(trace.hardwareTimingSchedule());
    }

    static HardwareReadinessAdmissionPolicy admissionPolicyFor(
            com.openggf.trace.timing.HardwareTimingSchedule schedule) {
        return schedule.hasRecordedInput()
                ? HardwareReadinessAdmissionPolicy.RECORDED
                : HardwareReadinessAdmissionPolicy.LIVE;
    }

    /**
     * Drives one full pass: warm up over the leading frames, then measure the
     * following window. Both phases run in the same loop so the measured frames
     * are entered with exactly the state the warmup left behind.
     */
    private static BenchmarkReport.Iteration runIteration(int index, Args args,
                                                          TraceData trace, Bk2Movie movie) {
        boolean cold = index == 0;

        PerformanceProfiler profiler = GameServices.profiler();
        profiler.reset();
        profiler.setEnabled(true);
        profiler.setAllocationTrackingEnabled(args.trackAllocations());

        RecordingFrameDriver frameDriver =
                new RecordingFrameDriver(GameServices.camera().getFocusedSprite());
        // Headless replay bypasses GameLoop, so without this wrapper the update
        // side of the frame reports no sections at all — only the render side
        // (which instruments itself) would show up, and `update` mode would
        // measure a frame with no visible contents.
        LevelFrameStep.StepWrapper profilingWrapper = (name, step) -> {
            profiler.beginSection(name);
            step.run();
            profiler.endSection(name);
        };
        frameDriver.setStepWrapper(profilingWrapper);

        TraceReplayDrive.DriverFixture fixture = new TraceReplayDrive.DriverFixture(frameDriver);
        frameDriver.setBk2Movie(movie,
                TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace));

        TraceReplaySessionBootstrap.applyPostLoadLevelInit(trace);
        TraceReplaySessionBootstrap.applyStartPositionAndGroundSnap(trace, fixture);
        TraceReplaySessionBootstrap.BootstrapResult bootResult =
                TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);
        TraceReplayBootstrap.ReplayStartState replayStart = bootResult.replayStart();

        int driveTraceIndex = replayStart.startingTraceIndex();
        TraceFrame previousDriveFrame = replayStart.hasSeededTraceState()
                ? trace.getFrame(replayStart.seededTraceIndex())
                : driveTraceIndex > 0 ? trace.getFrame(driveTraceIndex - 1) : null;
        TraceReplaySessionBootstrap.alignFrameCountersForReplayStart(
                previousDriveFrame,
                driveTraceIndex < trace.frameCount() ? trace.getFrame(driveTraceIndex) : null);

        // The cold pass records its warmup frames too — they are the only place
        // the JIT's climb to steady state is visible, and that climb is the
        // metric a player actually experiences.
        SectionTimeline warmupTimeline =
                cold && args.warmupFrames() > 0 ? new SectionTimeline(args.warmupFrames()) : null;
        SectionTimeline measured = new SectionTimeline(args.measureFrames());
        TrajectoryDigest digest = new TrajectoryDigest();

        boolean render = MODE_FULL.equals(args.mode());
        AudioPresenter audio = new AudioPresenter(args.audio());

        profiler.setSampleSink(warmupTimeline);
        long measureStartNanos = 0;
        long measureEndNanos = 0;
        // Baselined at the measurement boundary, not at process start: the
        // collections caused by class loading, level decode, and warmup belong
        // to nobody's frame budget and would swamp the figure that matters.
        GcSnapshot gcAtMeasureStart = GcSnapshot.capture();
        if (args.warmupFrames() == 0) {
            profiler.setSampleSink(measured);
            measureStartNanos = System.nanoTime();
        }

        int steppedFrames = 0;
        int targetFrames = args.warmupFrames() + args.measureFrames();
        try {
            while (driveTraceIndex < trace.frameCount() && steppedFrames < targetFrames) {
                TraceFrame driveFrame = trace.getFrame(driveTraceIndex);
                TraceExecutionPhase phase =
                        TraceReplayBootstrap.phaseForReplay(trace, previousDriveFrame, driveFrame);

                // beginFrame is unconditional and endFrame is not: a phase that
                // does not tick gameplay costs almost nothing, and recording it
                // as a frame would drag every percentile down with samples that
                // represent no work. beginFrame simply clears and re-arms, so an
                // unclosed frame leaves nothing behind.
                profiler.beginFrame();
                TraceReplayDrive.DriveOutcome outcome = TraceReplayDrive.driveOneFrame(
                        trace, frameDriver, replayStart, phase, driveTraceIndex);
                if (!outcome.consumedRow()) {
                    // The engine spent the frame on setup and never looked at
                    // this row, so it must be re-driven rather than skipped. The
                    // frame is left unclosed: it measured setup, not gameplay.
                    continue;
                }
                if (outcome.gameplayFrame()) {
                    if (render) {
                        // No enclosing "render" section: profiler sections are
                        // flat, so LevelRenderer's first inner section would
                        // implicitly close it and leave a sliver masquerading as
                        // the render total. The render.* sections are the render
                        // breakdown; the frame total is the frame total.
                        TraceReplayDrive.renderFrame();
                    }
                    audio.present(profiler);
                    profiler.endFrame();

                    steppedFrames++;
                    if (steppedFrames == args.warmupFrames()) {
                        profiler.setSampleSink(measured);
                        gcAtMeasureStart = GcSnapshot.capture();
                        measureStartNanos = System.nanoTime();
                    } else if (steppedFrames > args.warmupFrames()) {
                        observe(digest, driveFrame);
                    }
                }

                driveTraceIndex++;
                previousDriveFrame = driveFrame;
            }
            measureEndNanos = System.nanoTime();
        } finally {
            profiler.setSampleSink(null);
        }

        if (steppedFrames < targetFrames) {
            System.out.println("  note: trace ran out after " + steppedFrames
                    + " gameplay frames (asked for " + targetFrames
                    + "); measured window is short");
        }

        int framesToSteadyState = cold
                ? steadyStateAcrossWarmupAndMeasure(warmupTimeline, measured)
                : -1;

        return new BenchmarkReport.Iteration(index, cold, measured.frameCount(),
                Math.max(measureEndNanos - measureStartNanos, 0),
                digest.hex(), framesToSteadyState,
                measured.frameTiming(), measured.allTimings(),
                GcSnapshot.capture().since(gcAtMeasureStart), usedHeapBytes(),
                measured.overflowed());
    }

    /**
     * Steady state is looked for over the warmup and measured frames as one
     * series. Measuring it over the measured window alone would report a
     * suspiciously prompt convergence for every runtime — by then the warmup has
     * already done the settling that was the thing worth timing.
     */
    private static int steadyStateAcrossWarmupAndMeasure(SectionTimeline warmup,
                                                         SectionTimeline measured) {
        int warmupFrames = warmup != null ? warmup.frameCount() : 0;
        long[] combined = new long[warmupFrames + measured.frameCount()];
        if (warmup != null) {
            System.arraycopy(warmup.rawFrameNanos(), 0, combined, 0, warmupFrames);
        }
        System.arraycopy(measured.rawFrameNanos(), 0, combined, warmupFrames,
                measured.frameCount());
        return SteadyStateDetector.framesToSteadyState(combined, combined.length);
    }

    private static void observe(TrajectoryDigest digest, TraceFrame driveFrame) {
        AbstractPlayableSprite sprite = GameServices.camera().getFocusedSprite();
        Camera camera = GameServices.camera();
        if (sprite == null) {
            return;
        }
        digest.observe(driveFrame.frame(), sprite.getCentreX(), sprite.getCentreY(),
                sprite.getRingCount(), camera.getX(), camera.getY());
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Presents one outer audio frame per gameplay frame so SMPS/FM synthesis —
     * tight scalar arithmetic, and one of the most JIT-sensitive things the
     * engine does — is inside the measurement rather than silently absent.
     *
     * <p>If the headless audio backend cannot present (no backend installed,
     * audio disabled in config), the run says so once and continues without it
     * instead of failing or, worse, reporting an audio-free frame time as though
     * audio had been included.
     */
    private static final class AudioPresenter {
        private boolean enabled;

        AudioPresenter(boolean enabled) {
            this.enabled = enabled;
        }

        void present(PerformanceProfiler profiler) {
            if (!enabled) {
                return;
            }
            profiler.beginSection("audio");
            try {
                HeadlessGameBoot.presentHeadlessOuterAudioFrame();
            } catch (RuntimeException e) {
                enabled = false;
                System.out.println("  note: audio presentation unavailable ("
                        + e.getMessage() + "); benchmarking without the audio subsystem");
            } finally {
                profiler.endSection("audio");
            }
        }
    }

    /**
     * Warns about flags that make a result misleading rather than merely
     * different. These are easy to leave in a launcher script by accident and
     * produce numbers that look plausible.
     */
    private static void warnAboutSuspectFlags(JvmEnvironment environment) {
        for (String flag : environment.inputArguments()) {
            if (flag.startsWith("-agentlib:jdwp")) {
                System.out.println("WARNING: a debugger agent is attached; "
                        + "timings are not representative");
            }
            if (flag.equals("-Xint")) {
                System.out.println("WARNING: -Xint disables the JIT entirely; "
                        + "this measures the interpreter, not the runtime");
            }
            if (flag.startsWith("-Xrunjdwp") || flag.startsWith("-javaagent")) {
                System.out.println("WARNING: an instrumentation agent is attached ("
                        + flag + "); timings may be distorted");
            }
        }
    }

    private static void printIteration(BenchmarkReport.Iteration iteration) {
        SectionTiming frame = iteration.frameTiming();
        System.out.printf(Locale.ROOT,
                "  iteration %d%s: %d frames  p50=%.3fms p99=%.3fms max=%.3fms  %.0f fps  "
                        + "gc=%d/%dms%n",
                iteration.index(), iteration.cold() ? " (cold)" : "", iteration.frames(),
                frame.p50Millis(), frame.p99Millis(), frame.maxMillis(),
                iteration.throughputFps(),
                iteration.gc().totalCollections(), iteration.gc().totalTimeMs());
        if (iteration.framesToSteadyState() >= 0) {
            System.out.println("    steady state reached after "
                    + iteration.framesToSteadyState() + " frames");
        }
    }

    private static void printSummary(BenchmarkReport report) {
        report.representativeIteration().ifPresent(best -> {
            System.out.println();
            System.out.println("=== " + report.label() + " — " + report.traceLabel()
                    + " (" + report.mode() + ") ===");
            System.out.printf(Locale.ROOT, "frame p50 %.3fms  p90 %.3fms  p99 %.3fms  max %.3fms%n",
                    best.frameTiming().p50Millis(),
                    best.frameTiming().p90Nanos() / 1_000_000.0,
                    best.frameTiming().p99Millis(),
                    best.frameTiming().maxMillis());
            System.out.println("trajectory digest " + best.trajectoryDigest()
                    + " (must match across runtimes for the comparison to be valid)");
            System.out.println();
            System.out.println("heaviest sections (median ms/frame):");
            best.sections().stream().limit(10).forEach(section ->
                    System.out.printf(Locale.ROOT, "  %-24s %.4f%n",
                            section.name(), section.p50Millis()));
        });
    }
}
