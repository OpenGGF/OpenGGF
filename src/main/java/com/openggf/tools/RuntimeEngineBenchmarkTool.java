package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.Engine;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.trace.TraceCharacterState;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import com.openggf.trace.timing.HardwareTimingSchedule;
import com.openggf.trace.timing.TraceHardwareTimingBoundaryObserver;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Complete normal Engine workload for the 2026-10-12 runtime performance task.
 * Runs the shipping paced window/render/audio/rewind loop. Input plans are compiled
 * separately, so comparison physics/auxiliary data never enter this process.
 * CPU boundaries enclose complete presentation intervals, all process threads and
 * GC; update-to-update wall intervals include pacing and are not CPU measurements.
 * External Linux sampling supplies process RSS/peak and deduplicated DRM residency.
 */
public final class RuntimeEngineBenchmarkTool extends Engine {
    private static final com.sun.management.OperatingSystemMXBean OS =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private final RuntimeInputPlan plan;
    private final int measureFrom;
    private final int measureTo;
    private final long[] intervals;
    private final java.io.PrintWriter qualification;
    private final MessageDigest trajectory = MessageDigest.getInstance("SHA-256");
    private final MessageDigest controllers = MessageDigest.getInstance("SHA-256");
    private final ByteBuffer digestScratch = ByteBuffer.allocate(256);
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> transitions = new ArrayList<>();
    private HardwareTimingReplayPort timing;
    private TraceHardwareTimingBoundaryObserver boundary;
    private int cursor;
    private int presentationUpdates;
    private int fullFrames;
    private int measuredFullFrames;
    private int measuredExecutedFrames;
    private int intervalCount;
    private long measureCpuStart;
    private long measureWallStart;
    private long previousUpdate;
    private boolean playbackStarted;
    private boolean measured;
    private boolean measurementEnded;
    private boolean firstGameplayPresentationPending;
    private boolean firstGameplayPresentationObserved;
    private long startupWallStart;
    private long startupCpuStart;
    private long selectedLoadWallStart;
    private long selectedLoadCpuStart;

    private RuntimeEngineBenchmarkTool(EngineContext context, RuntimeInputPlan plan,
            int from, int to, Path qualificationCsv) throws Exception {
        super(context);
        if (from < 0 || to <= from || to >= plan.rows().size()) {
            throw new IllegalArgumentException("Measure window must finish before route EOF");
        }
        this.plan = plan;
        measureFrom = from;
        measureTo = to;
        // A fade can hold one controller row over multiple presentations.
        intervals = new long[to - from + 3000];
        qualification = qualificationCsv == null ? null
                : new java.io.PrintWriter(java.nio.file.Files.newBufferedWriter(qualificationCsv));
        if (qualification != null) qualification.println("route_frame,x,y,xvel,yvel,gspeed,air,anim,mapping,level_frame,input,angle,rolling,ground,xsub,ysub,camx,camy,rings,vblank,action,start,bk2_index,player_routine,player_status,player_stand,sidekick_present,sidekick_x,sidekick_y,sidekick_xvel,sidekick_yvel,sidekick_gspeed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_xsub,sidekick_ysub,sidekick_routine,sidekick_status,sidekick_stand,sidekick_anim,sidekick_mapping");
    }

    @Override public void update() {
        try {
            if (!playbackStarted && GameServices.sprites().getMainPlayable() != null) {
                startPlayback();
            }
            long now = System.nanoTime();
            // The preceding update's normal draw/audio/swap has finished here.
            if (firstGameplayPresentationPending && !firstGameplayPresentationObserved) {
                firstGameplayPresentationObserved = true;
                report.put("startupToFirstGameplayPresentationWallNs", now - startupWallStart);
                report.put("startupToFirstGameplayPresentationProcessCpuNs", OS.getProcessCpuTime() - startupCpuStart);
                if (selectedLoadWallStart != 0) {
                    report.put("selectedLevelLoadToFirstPresentationWallNs", now - selectedLoadWallStart);
                    report.put("selectedLevelLoadToFirstPresentationProcessCpuNs", OS.getProcessCpuTime() - selectedLoadCpuStart);
                }
            }
            if (playbackStarted && cursor == measureFrom && !measured) {
                var rewind = rewindState();
                if ((int) rewind.get("retainedFrames") < 3600) {
                    throw new IllegalStateException("Configured rewind history is not filled: " + rewind);
                }
                report.put("rewindAtStart", rewind);
                measureWallStart = now;
                measureCpuStart = OS.getProcessCpuTime();
                previousUpdate = now;
                measured = true;
                System.out.println("RUNTIME_MEASURE_BEGIN cursor=" + cursor);
            } else if (measured && !measurementEnded && cursor > measureFrom && cursor <= measureTo) {
                intervals[intervalCount++] = now - previousUpdate;
                previousUpdate = now;
                if (cursor == measureTo) {
                    measurementEnded = true;
                    report.put("processCpuNs", OS.getProcessCpuTime() - measureCpuStart);
                    report.put("wallNs", now - measureWallStart);
                    report.put("rewindAtEnd", rewindState());
                    System.out.println("RUNTIME_MEASURE_END cursor=" + cursor);
                }
            }
            if (boundary != null && cursor < plan.rows().size()) {
                boundary.beginRawFrame(plan.rows().get(cursor).rawFrame());
            }
            int before = cursor;
            int counterBefore = GameServices.level().getFrameCounter();
            int zoneBefore = GameServices.level().getCurrentZone();
            int actBefore = GameServices.level().getCurrentAct();
            GameMode oldMode = getCurrentGameMode();
            if (playbackStarted && cursor > 0 && oldMode == GameMode.TITLE_CARD) {
                GameServices.playbackDebug().shouldSkipCurrentGameplayTick();
            }
            super.update();
            if (playbackStarted && cursor > 0 && cursor == before
                    && getCurrentGameMode() == GameMode.TITLE_CARD) {
                GameServices.playbackDebug().onLevelFrameAdvanced();
            }
            if (before >= measureFrom && before < measureTo && cursor > before) {
                var level = GameServices.level();
                if (level.getCurrentZone() != zoneBefore || level.getCurrentAct() != actBefore) {
                    throw new IllegalStateException("Measurement window crosses a level-load boundary");
                }
                int advanced = (level.getFrameCounter() - counterBefore) & 0xFFFF;
                if (advanced > 1) throw new IllegalStateException("Unexpected simulation cadence: " + advanced);
                measuredExecutedFrames += advanced;
            }
            if (oldMode != getCurrentGameMode()) {
                transitions.add(Map.of("cursor", cursor, "from", oldMode.name(),
                        "to", getCurrentGameMode().name(), "wallNs", System.nanoTime(),
                        "processCpuNs", OS.getProcessCpuTime()));
            }
            presentationUpdates++;
            if (cursor >= plan.rows().size()) {
                glfwSetWindowShouldClose((Long) field(this, "window"), true);
            } else if (presentationUpdates > plan.rows().size() + 3000) {
                throw new IllegalStateException("Playback stopped advancing at " + cursor);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void startPlayback() {
        playbackStarted = true;
        if (plan.recordedTiming()) {
            var gameplay = SessionManager.getCurrentGameplayMode();
            timing = new HardwareTimingReplayPort(gameplay.activateRecordedHardwareAdmission());
            timing.install(new HardwareTimingSchedule(plan.readiness()));
            boundary = new TraceHardwareTimingBoundaryObserver(timing);
            gameplay.setHardwareTimingBoundaryObserver(boundary);
            gameplay.getRewindRegistry().register(timing);
            boundary.enterUnrepresentedGap();
        }
        var movie = new Bk2Movie(Path.of("runtime-input-plan"), "", Map.of(), plan.inputs(), 1);
        var playback = GameServices.playbackDebug();
        playback.startSession(movie, 1);
        playback.setOverlayOwnedExternally(true);
        playback.setFrameObserver(new PlaybackDebugManager.PlaybackFrameObserver() {
            public int appliedInputOffset(com.openggf.debug.playback.Bk2FrameInput input) {
                return plan.rows().get(cursor).appliedInputOffset();
            }
            public boolean shouldSkipGameplayTick(com.openggf.debug.playback.Bk2FrameInput input) {
                return plan.rows().get(cursor).phase() != TraceExecutionPhase.FULL_LEVEL_FRAME;
            }
            public boolean shouldAdvanceVblankOnSkippedTick(com.openggf.debug.playback.Bk2FrameInput input) {
                return plan.rows().get(cursor).suppressedClosureCount() > 0;
            }
            public boolean hasUnconsumedRecordedRows() { return cursor < plan.rows().size(); }
            public void afterFrameAdvanced(com.openggf.debug.playback.Bk2FrameInput input, boolean skipped) {
                var row = plan.rows().get(cursor);
                if (row.phase() == TraceExecutionPhase.FULL_LEVEL_FRAME) {
                    firstGameplayPresentationPending = true;
                    fullFrames++;
                    if (cursor >= measureFrom && cursor < measureTo) measuredFullFrames++;
                }
                digestScratch.clear();
                digestScratch.putInt(row.traceIndex()).putInt(input.frameIndex())
                        .putInt(input.p1InputMask()).putInt(input.p1ActionMask())
                        .putInt(input.p1StartPressed() ? 1 : 0).putInt(input.p2InputMask())
                        .putInt(input.p2ActionMask()).putInt(input.p2StartPressed() ? 1 : 0);
                var applied = plan.inputs().get(cursor + 1 + row.appliedInputOffset());
                digestScratch.putInt(applied.frameIndex()).putInt(applied.p1InputMask())
                        .putInt(applied.p1ActionMask()).putInt(applied.p2InputMask())
                        .putInt(applied.p2ActionMask());
                controllers.update(digestScratch.array(), 0, digestScratch.position());
                digestScratch.clear();
                var sprites = GameServices.sprites();
                var primary = TraceCharacterState.fromSprite(sprites.getMainPlayable());
                putCharacter(primary);
                var sidekicks = sprites.getRegisteredSidekicks();
                var sidekick = TraceCharacterState.fromSprite(sidekicks.isEmpty() ? null : sidekicks.getFirst());
                putCharacter(sidekick);
                digestScratch.putInt(GameServices.level().getFrameCounter())
                        .putInt(GameServices.level().getCurrentZone()).putInt(GameServices.level().getCurrentAct())
                        .putInt(GameServices.camera().getX()).putInt(GameServices.camera().getY())
                        .putInt(sprites.getMainPlayable().getRingCount())
                        .putInt(GameServices.level().getObjectManager().getVblaCounter());
                trajectory.update(digestScratch.array(), 0, digestScratch.position());
                if (qualification != null) qualification.printf(
                        "%d,%d,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%s,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%s,%s,%d,%d,%d,%d,%d,%d,%d%n",
                        row.traceIndex(), primary.x(), primary.y(), primary.xSpeed(), primary.ySpeed(), primary.gSpeed(),
                        primary.air(), primary.animationId(), primary.mappingFrame(), GameServices.level().getFrameCounter(),
                        input.p1InputMask(), primary.angle() & 255, primary.rolling(), primary.groundMode(), primary.xSub(), primary.ySub(),
                        GameServices.camera().getX(), GameServices.camera().getY(), sprites.getMainPlayable().getRingCount(),
                        GameServices.level().getObjectManager().getVblaCounter(), input.p1ActionMask(), input.p1StartPressed(), input.frameIndex(),
                        primary.routine(), primary.statusByte(), primary.standOnObj(), sidekick.present(), sidekick.x(), sidekick.y(),
                        sidekick.xSpeed(), sidekick.ySpeed(), sidekick.gSpeed(), sidekick.angle() & 255, sidekick.air(), sidekick.rolling(),
                        sidekick.xSub(), sidekick.ySub(), sidekick.routine(), sidekick.statusByte(), sidekick.standOnObj(),
                        sidekick.animationId(), sidekick.mappingFrame());
                if (boundary != null) boundary.enterUnrepresentedGap();
                cursor++;
                if (cursor % 600 == 0) System.out.println("RUNTIME_PROGRESS cursor=" + cursor);
            }
        });
    }

    private void putCharacter(TraceCharacterState c) {
        digestScratch.putInt(c.present() ? 1 : 0).putInt(c.x()).putInt(c.y())
                .putInt(c.xSpeed()).putInt(c.ySpeed()).putInt(c.gSpeed()).putInt(c.angle())
                .putInt(c.air() ? 1 : 0).putInt(c.rolling() ? 1 : 0).putInt(c.groundMode())
                .putInt(c.xSub()).putInt(c.ySub()).putInt(c.routine()).putInt(c.statusByte())
                .putInt(c.standOnObj()).putInt(c.animationId()).putInt(c.mappingFrame());
    }

    private Map<String, Object> rewindState() throws ReflectiveOperationException {
        var live = field(getGameLoop(), "liveRewindManager");
        var rewind = (RewindController) field(live, "rewindController");
        return rewind == null ? Map.of("retainedFrames", -1) : Map.of(
                "current", rewind.currentFrame(), "earliest", rewind.earliestAvailableFrame(),
                "retainedFrames", rewind.currentFrame() - rewind.earliestAvailableFrame());
    }

    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        Class<?> owner = instance instanceof Engine ? Engine.class : instance.getClass();
        Field f = owner.getDeclaredField(name); f.setAccessible(true); return f.get(instance);
    }
    private static Method method(String name) throws ReflectiveOperationException {
        var method = Engine.class.getDeclaredMethod(name); method.setAccessible(true); return method;
    }
    private static void configure(SonicConfigurationService c, String game, Path romRoot) {
        c.setSessionOverride(SonicConfiguration.DEFAULT_ROM, game);
        c.setSessionOverride(SonicConfiguration.SONIC_1_ROM, romRoot.resolve("Sonic The Hedgehog (W) (REV01) [!].gen").toString());
        c.setSessionOverride(SonicConfiguration.SONIC_2_ROM, romRoot.resolve("Sonic The Hedgehog 2 (W) (REV01) [!].gen").toString());
        c.setSessionOverride(SonicConfiguration.SONIC_3K_ROM, romRoot.resolve("Sonic and Knuckles & Sonic 3 (W) [!].gen").toString());
        for (var key : List.of(SonicConfiguration.SHOW_LEGAL_DISCLAIMER_ON_STARTUP,
                SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP, SonicConfiguration.TITLE_SCREEN_ON_STARTUP,
                SonicConfiguration.LEVEL_SELECT_ON_STARTUP, SonicConfiguration.DEBUG_VIEW_ENABLED)) c.setSessionOverride(key, false);
        for (var key : List.of(SonicConfiguration.AUDIO_ENABLED, SonicConfiguration.LIVE_REWIND_ENABLED,
                SonicConfiguration.LAUNCH_S1_REWIND, SonicConfiguration.LAUNCH_S2_REWIND,
                SonicConfiguration.LAUNCH_S3K_REWIND)) c.setSessionOverride(key, true);
        c.setSessionOverride(SonicConfiguration.REWIND_HISTORY_SECONDS, 60);
        c.setSessionOverride(SonicConfiguration.FPS, 60);
        c.setSessionOverride(SonicConfiguration.DISPLAY_WINDOW_AUTOSIZE, false);
        c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH, 640);
        c.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT, 448);
    }

    public static void main(String[] args) {
        try { run(args); }
        catch (Exception e) { throw new IllegalStateException("Runtime benchmark failed", e); }
    }

    private static void run(String[] args) throws Exception {
        if (args.length == 6 && args[0].equals("--load")) {
            runLoadProbe(args);
            return;
        }
        if (args.length < 5 || args.length > 6) throw new IllegalArgumentException(
                "Usage: RuntimeEngineBenchmarkTool <plan> <rom-root> <report-json> <measure-from> <measure-to> [qualification-csv]");
        var plan = RuntimeInputPlan.read(Path.of(args[0]));
        var services = EngineServices.current();
        var config = services.configuration();
        configure(config, plan.game(), Path.of(args[1]));
        var engine = new RuntimeEngineBenchmarkTool(services,
                plan, Integer.parseInt(args[3]), Integer.parseInt(args[4]), args.length == 6 ? Path.of(args[5]) : null);
        try {
            glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND);
            long wall = System.nanoTime(), cpu = OS.getProcessCpuTime();
            engine.startupWallStart = wall;
            engine.startupCpuStart = cpu;
            method("init").invoke(engine);
            engine.report.put("startupWallNs", System.nanoTime() - wall);
            engine.report.put("startupProcessCpuNs", OS.getProcessCpuTime() - cpu);
            var level = GameServices.level();
            if (level.getCurrentZone() != plan.zone() || level.getCurrentAct() != plan.act()) {
                wall = System.nanoTime(); cpu = OS.getProcessCpuTime();
                engine.selectedLoadWallStart = wall;
                engine.selectedLoadCpuStart = cpu;
                level.loadZoneAndActForFreshRuntime(plan.zone(), plan.act());
                engine.report.put("selectedLevelLoadWallNs", System.nanoTime() - wall);
                engine.report.put("selectedLevelLoadProcessCpuNs", OS.getProcessCpuTime() - cpu);
            }
            method("loop").invoke(engine);
            if (engine.cursor != plan.rows().size() || !engine.measurementEnded
                    || engine.intervalCount < engine.measureTo - engine.measureFrom
                    || engine.measuredFullFrames == 0
                    || engine.measuredFullFrames != engine.measuredExecutedFrames) {
                throw new IllegalStateException("Incomplete workload or simulation-count mismatch: "
                        + engine.measuredFullFrames + "/" + engine.measuredExecutedFrames);
            }
            if (engine.timing != null && !engine.timing.drainUnmatchedRecordedCompletions().isEmpty()) {
                throw new IllegalStateException("Unmatched hardware readiness input");
            }
            engine.report.put("schema", 1);
            engine.report.put("game", plan.game());
            engine.report.put("inputRows", engine.cursor);
            engine.report.put("requestedFullFrameRows", engine.fullFrames);
            engine.report.put("measuredExecutedFrames", engine.measuredExecutedFrames);
            engine.report.put("presentationUpdates", engine.presentationUpdates);
            engine.report.put("measuredPresentationIntervals", engine.intervalCount);
            engine.report.put("controllerSha256", HexFormat.of().formatHex(engine.controllers.digest()));
            engine.report.put("trajectorySha256", HexFormat.of().formatHex(engine.trajectory.digest()));
            engine.report.put("renderer", glGetString(GL_RENDERER));
            engine.report.put("driver", glGetString(GL_VERSION));
            engine.report.put("java", System.getProperty("java.runtime.version"));
            engine.report.put("jvmFlags", ManagementFactory.getRuntimeMXBean().getInputArguments());
            engine.report.put("qualificationCsvEnabled", engine.qualification != null);
            engine.report.put("transitions", engine.transitions);
            var settings = new java.util.TreeMap<String, Object>();
            for (var key : SonicConfiguration.values()) settings.put(key.name(), config.getConfigValue(key));
            engine.report.put("settings", settings);
            if (engine.timing != null) {
                var gameplay = SessionManager.getCurrentGameplayMode();
                var jobs = gameplay.hardwareTiming().capture().jobs().stream()
                        .collect(java.util.stream.Collectors.toMap(j -> j.handle(), j -> j));
                engine.report.put("pendingHardwareWork", gameplay.recordedCompletionAuthority()
                        .pendingSubmissions().stream().map(p -> Map.of(
                                "kind", p.handle().kind().name(), "ordinal", p.handle().ordinal(),
                                "fingerprint", p.handle().submissionFingerprint(),
                                "romSource", jobs.get(p.handle()).romSourceAddress())).toList());
            }
            long[] sorted = Arrays.copyOf(engine.intervals, engine.intervalCount); Arrays.sort(sorted);
            engine.report.put("frameIntervalMedianNs", (sorted[(sorted.length - 1) / 2] + sorted[sorted.length / 2]) / 2.0);
            engine.report.put("frameIntervalP95Ns", sorted[(int)Math.ceil(sorted.length * .95) - 1]);
            engine.report.put("frameIntervalP99Ns", sorted[(int)Math.ceil(sorted.length * .99) - 1]);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[2]).toFile(), engine.report);
            System.out.println("RUNTIME_COMPLETE inputRows=" + engine.cursor + " requestedFullFrameRows=" + engine.fullFrames);
        } finally {
            if (engine.qualification != null) engine.qualification.close();
            method("cleanup").invoke(engine);
            Engine.clearGlobalInstance();
        }
    }

    /** Separate LIVE fresh-load diagnostic; no trace, playback or score window. */
    private static void runLoadProbe(String[] args) throws Exception {
        var services = EngineServices.current();
        var config = services.configuration();
        configure(config, args[1], Path.of(args[4]));
        var engine = new LoadProbe(services);
        try {
            glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND);
            long wall = System.nanoTime(), cpu = OS.getProcessCpuTime();
            method("init").invoke(engine);
            engine.result.put("startupInitializationWallNs", System.nanoTime() - wall);
            engine.result.put("startupInitializationProcessCpuNs", OS.getProcessCpuTime() - cpu);
            engine.loadWall = System.nanoTime();
            engine.loadCpu = OS.getProcessCpuTime();
            GameServices.level().loadZoneAndActForFreshRuntime(
                    Integer.parseInt(args[2]), Integer.parseInt(args[3]));
            var neutralInputs = new ArrayList<com.openggf.debug.playback.Bk2FrameInput>();
            for (int i = 0; i <= 3000; i++) {
                neutralInputs.add(new com.openggf.debug.playback.Bk2FrameInput(i, 0, 0, false, 0, 0, false, ""));
            }
            GameServices.playbackDebug().startSession(
                    new Bk2Movie(Path.of("neutral-live-load"), "", Map.of(), neutralInputs, 1), 0);
            GameServices.playbackDebug().setOverlayOwnedExternally(true);
            method("loop").invoke(engine);
            if (!engine.completed) throw new IllegalStateException("Incomplete LIVE level-load probe");
            engine.result.put("game", args[1]);
            engine.result.put("zone", Integer.parseInt(args[2]));
            engine.result.put("act", Integer.parseInt(args[3]));
            engine.result.put("renderer", glGetString(GL_RENDERER));
            engine.result.put("presentationUpdates", engine.updates);
            engine.result.put("mode", engine.getCurrentGameMode().name());
            engine.result.put("readinessPolicy", "LIVE");
            engine.result.put("routeCertification", false);
            engine.result.put("controllerInputs", "neutral P1/P2; no trace admission");
            new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValue(Path.of(args[5]).toFile(), engine.result);
            System.out.println("RUNTIME_LOAD_COMPLETE game=" + args[1]);
        } finally {
            method("cleanup").invoke(engine);
            Engine.clearGlobalInstance();
        }
    }

    private static final class LoadProbe extends Engine {
        private final Map<String, Object> result = new LinkedHashMap<>();
        private long loadWall;
        private long loadCpu;
        private int updates;
        private boolean gameplayPresented;
        private boolean completed;

        private LoadProbe(EngineContext context) { super(context); }

        @Override public void update() {
            try {
                if (gameplayPresented) {
                    result.put("freshLoadToFirstGameplayPresentationWallNs", System.nanoTime() - loadWall);
                    result.put("freshLoadToFirstGameplayPresentationProcessCpuNs", OS.getProcessCpuTime() - loadCpu);
                    completed = true;
                    glfwSetWindowShouldClose((Long) field(this, "window"), true);
                    return;
                }
                int before = GameServices.level().getFrameCounter();
                super.update();
                gameplayPresented = getCurrentGameMode() == GameMode.LEVEL
                        && GameServices.level().getFrameCounter() != before;
                if (++updates > 3000) throw new IllegalStateException("LIVE level load never became playable");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
