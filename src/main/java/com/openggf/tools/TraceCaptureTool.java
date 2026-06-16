package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.capture.BackpressurePolicy;
import com.openggf.capture.CaptureRecorder;
import com.openggf.capture.CapturedFrame;
import com.openggf.capture.DrainPcmAudioTap;
import com.openggf.capture.FfmpegEncoder;
import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.catalog.TraceCatalog;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.trace.replay.RecordingFrameDriver;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Command-line trace-capture driver. Boots a headless gameplay session against
 * a real ROM/GL context, deterministically replays a recorded trace using the
 * <em>same</em> bootstrap and per-frame drive that {@code AbstractTraceReplayTest}
 * uses (via {@link TraceReplaySessionBootstrap} + {@link RecordingFrameDriver} +
 * {@link TraceReplayBootstrap} phase logic), and records the rendered frames +
 * chip audio to a lossless MKV via {@link FfmpegEncoder} / {@link CaptureRecorder}.
 *
 * <p>The capture used to drive frames through the live {@code GameLoop}/
 * {@code PlaybackDebugManager} playback path, which omits the P2/sidekick input
 * plumbing and the explicit phase loop that the trace-replay tests use. That
 * produced a capture run that desynced from the recorded (and ROM) trajectory
 * — e.g. AIZ rings 19 instead of the recorded 97 by the battleship loop. This
 * driver now reproduces the exact trace-faithful trajectory the tests validate.
 *
 * <p>Invocation (Maven):
 * <pre>
 *   mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceCaptureTool" \
 *       "-Dexec.args=--trace aiz1 --out-dir target/trace-videos"
 * </pre>
 *
 * <p>Output: {@code &lt;out-dir&gt;/capture-&lt;label&gt;-&lt;UTC&gt;.mkv}. MKV frame index
 * equals trace gameplay-comparison index, so frame N of the MKV is trace frame N.
 *
 * <p>{@code --verify} runs the same bootstrap + drive headlessly with NO GL
 * capture and prints the player rings + camera_x at requested trace frames, so
 * trace-faithfulness can be confirmed before a full capture.
 */
public final class TraceCaptureTool {

    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;

    private static final DateTimeFormatter UTC_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private TraceCaptureTool() {
    }

    /**
     * Parsed CLI arguments. Unspecified {@code --scale}, {@code --fps},
     * {@code --codec}, and {@code --out-dir} fall back to the {@code CAPTURE_*}
     * config defaults.
     */
    public record Args(String trace, Path outDir, int scale, int fps, String codec,
                       boolean showGhosts, long verifyFrame, int[] verifyFrames) {

        public static Args parse(String[] argv) {
            SonicConfigurationService config = GameServices.configuration();
            String trace = null;
            String outDir = config.getString(SonicConfiguration.CAPTURE_OUTPUT_DIR);
            int scale = config.getInt(SonicConfiguration.CAPTURE_SCALE);
            int fps = config.getInt(SonicConfiguration.CAPTURE_FPS);
            String codec = config.getString(SonicConfiguration.CAPTURE_CODEC);
            boolean showGhosts = config.getBoolean(SonicConfiguration.TRACE_SHOW_DESYNC_GHOSTS);
            int[] verifyFrames = null;

            for (int i = 0; i < argv.length; i++) {
                String arg = argv[i];
                switch (arg) {
                    case "--trace" -> trace = requireValue(argv, ++i, arg);
                    case "--out-dir" -> outDir = requireValue(argv, ++i, arg);
                    case "--scale" -> scale = Integer.parseInt(requireValue(argv, ++i, arg));
                    case "--fps" -> fps = Integer.parseInt(requireValue(argv, ++i, arg));
                    case "--codec" -> codec = requireValue(argv, ++i, arg);
                    case "--no-ghosts" -> showGhosts = false;
                    case "--ghosts" -> showGhosts = true;
                    case "--verify" -> verifyFrames = parseFrameList(requireValue(argv, ++i, arg));
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            if (trace == null || trace.isBlank()) {
                throw new IllegalArgumentException("--trace <id|name|dir> is required");
            }
            return new Args(trace, Paths.get(outDir), scale, fps, codec, showGhosts, -1, verifyFrames);
        }

        private static int[] parseFrameList(String spec) {
            String[] parts = spec.split(",");
            int[] frames = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                frames[i] = Integer.parseInt(parts[i].trim());
            }
            return frames;
        }

        private static String requireValue(String[] argv, int index, String flag) {
            if (index >= argv.length) {
                throw new IllegalArgumentException("Missing value for " + flag);
            }
            return argv[index];
        }
    }

    public static void main(String[] argv) {
        // CLI composition root: wire process-wide services before any config is
        // read (Args.parse below), mirroring how Engine bootstraps EngineServices.
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        Args args = Args.parse(argv);
        TraceCaptureTool tool = new TraceCaptureTool();
        HeadlessGameBoot boot = null;
        try {
            boot = tool.run(args);
        } catch (Exception e) {
            System.err.println("Trace capture failed: " + e.getMessage());
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

    /**
     * Resolves the trace, boots a headless session, replays + captures every
     * frame, and finalizes the MKV. Returns the boot so {@code main} can tear
     * it down in {@code finally}.
     */
    private HeadlessGameBoot run(Args args) throws Exception {
        // Apply the desync-ghost toggle so the shared LevelRenderer gate
        // (TraceRenderVisibility) honors it during capture. A trace-faithful
        // capture has no desync, so ghosts are off by default.
        GameServices.configuration().setConfigValue(
                SonicConfiguration.TRACE_SHOW_DESYNC_GHOSTS, args.showGhosts());

        // --- resolve trace -------------------------------------------------
        TraceEntry entry = resolveTrace(args.trace());
        System.out.println("Capturing trace: " + entry.dir()
                + " (" + entry.gameId() + " zone=" + entry.zone() + " act=" + entry.act() + ")");

        TraceData trace = TraceData.load(entry.dir());
        TraceMetadata meta = trace.metadata();
        Bk2Movie movie = new Bk2MovieLoader().load(entry.bk2Path());

        // --- pre-load configuration (must run BEFORE loadZoneAndAct) -------
        // Mirrors AbstractTraceReplayTest step 3: recorded team, cross-game
        // off, S3K intro-skip off for fresh-level traces. HeadlessGameBoot
        // loads the level AND registers the team, so this must precede boot().
        TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);

        // --- boot headless gameplay session -------------------------------
        HeadlessGameBoot boot = new HeadlessGameBoot(SCREEN_WIDTH, SCREEN_HEIGHT);
        Path romPath = Paths.get(RomManager.resolveRomForGame(entry.gameId()));
        GameLoop loop = boot.boot(romPath, entry.zone(), entry.act());

        // --- deterministic trace replay bootstrap -------------------------
        // Mirror AbstractTraceReplayTest steps 4-5: start position + ground
        // snap, then the shared replay bootstrap (timing prelude, native
        // frame-0 state, replay cursor). Build the recording driver over the
        // already-spawned player sprite and wire the BK2 movie + start frame.
        RecordingFrameDriver frameDriver =
                new RecordingFrameDriver(GameServices.camera().getFocusedSprite());
        CaptureFixture fixture = new CaptureFixture(frameDriver);
        frameDriver.setBk2Movie(movie,
                TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace));

        // Mirror HeadlessTestFixture.Builder.build steps 7-12: re-anchor
        // sidekicks, wire GroundSensor, re-run camera + level-event init,
        // re-apply S3K zone player state, refresh sidekick CPU bounds, and
        // ground-snap. The fixture runs these unconditionally at build time;
        // HeadlessGameBoot.boot only does loadZoneAndAct, so without these the
        // replay starts from default-load-derived bounds and drifts physics by
        // the first collision (e.g. AIZ x diverges ~400px by trace 2800). For
        // pre-level-intro-prefix traces applyStartPositionAndGroundSnap below
        // is a no-op, so this is the only place these run.
        TraceReplaySessionBootstrap.applyPostLoadLevelInit(trace);

        TraceReplaySessionBootstrap.applyStartPositionAndGroundSnap(trace, fixture);
        TraceReplaySessionBootstrap.BootstrapResult bootResult =
                TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);
        TraceReplayBootstrap.ReplayStartState replayStart = bootResult.replayStart();

        int startIndex = replayStart.startingTraceIndex();
        TraceFrame previousDriveFrame = replayStart.hasSeededTraceState()
                ? trace.getFrame(replayStart.seededTraceIndex())
                : startIndex > 0 ? trace.getFrame(startIndex - 1) : null;
        TraceReplaySessionBootstrap.alignFrameCountersForReplayStart(
                previousDriveFrame,
                startIndex < trace.frameCount() ? trace.getFrame(startIndex) : null);

        if (args.verifyFrames() != null) {
            runVerify(trace, meta, frameDriver, replayStart, args.verifyFrames());
            return boot;
        }

        // --- capture pipeline ---------------------------------------------
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(UTC_STAMP);
        String label = entry.dir().getFileName().toString();
        FfmpegEncoder encoder = new FfmpegEncoder(resolveFfmpeg(), args.scale());
        CaptureRecorder recorder = new CaptureRecorder(
                encoder, BackpressurePolicy.BLOCK, /* queueCapacity */ 8,
                args.outDir(), label, timestamp);

        GlReadPixelsGrabber grabber = new GlReadPixelsGrabber(SCREEN_WIDTH, SCREEN_HEIGHT);
        DrainPcmAudioTap audioTap = new DrainPcmAudioTap(GameServices.audio());
        int sampleRate = GameServices.audio().outputSampleRate();
        GameServices.audio().beginCaptureMode(sampleRate, args.fps());
        recorder.start(SCREEN_WIDTH, SCREEN_HEIGHT, args.fps(), sampleRate);

        long captured = 0;
        try {
            captured = driveAndCapture(trace, meta, frameDriver, replayStart,
                    loop, grabber, audioTap, recorder);
        } finally {
            Path out = recorder.stop();
            GameServices.audio().endCaptureMode();
            System.out.println("Captured " + captured + " frames -> " + out.toAbsolutePath());
            if (Files.isRegularFile(out)) {
                System.out.println("Output size: " + Files.size(out) + " bytes");
            }
        }
        return boot;
    }

    /**
     * Drives the S3K phase loop (mirroring AbstractTraceReplayTest.replayS3kTrace)
     * and captures every gameplay-comparison frame. Returns the captured frame
     * count.
     */
    private long driveAndCapture(TraceData trace, TraceMetadata meta,
                                 RecordingFrameDriver frameDriver,
                                 TraceReplayBootstrap.ReplayStartState replayStart,
                                 GameLoop loop,
                                 GlReadPixelsGrabber grabber,
                                 DrainPcmAudioTap audioTap,
                                 CaptureRecorder recorder) throws Exception {
        short[] pcmBuffer = new short[16384];
        long frameIndex = 0;

        int driveTraceIndex = replayStart.startingTraceIndex();
        TraceFrame previousDriveFrame = replayStart.hasSeededTraceState()
                ? trace.getFrame(replayStart.seededTraceIndex())
                : driveTraceIndex > 0 ? trace.getFrame(driveTraceIndex - 1) : null;

        while (driveTraceIndex < trace.frameCount()) {
            TraceFrame driveFrame = trace.getFrame(driveTraceIndex);
            TraceExecutionPhase phase =
                    TraceReplayBootstrap.phaseForReplay(trace, previousDriveFrame, driveFrame);

            boolean stepped = driveOneFrame(trace, frameDriver, replayStart, phase, driveTraceIndex);

            if (stepped && TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)) {
                renderFrame();
                byte[] rgba = grabber.grab();
                int sampleCount = audioTap.drain(pcmBuffer);
                recorder.submit(new CapturedFrame(rgba, SCREEN_WIDTH, SCREEN_HEIGHT,
                        pcmBuffer, sampleCount, frameIndex++));
            }

            driveTraceIndex++;
            previousDriveFrame = driveFrame;
        }
        return frameIndex;
    }

    /**
     * Headless self-verify: drive the trace to the requested frames and print
     * rings + camera_x. No GL capture. Confirms trace-faithfulness (e.g. AIZ
     * rings=97 / camera_x=0x443C at trace frame 16507).
     */
    private void runVerify(TraceData trace, TraceMetadata meta,
                           RecordingFrameDriver frameDriver,
                           TraceReplayBootstrap.ReplayStartState replayStart,
                           int[] verifyFrames) {
        int maxFrame = 0;
        for (int f : verifyFrames) {
            maxFrame = Math.max(maxFrame, f);
        }
        java.util.Set<Integer> wanted = new java.util.HashSet<>();
        for (int f : verifyFrames) {
            wanted.add(f);
        }

        int driveTraceIndex = replayStart.startingTraceIndex();
        TraceFrame previousDriveFrame = replayStart.hasSeededTraceState()
                ? trace.getFrame(replayStart.seededTraceIndex())
                : driveTraceIndex > 0 ? trace.getFrame(driveTraceIndex - 1) : null;

        System.out.println("=== VERIFY trajectory (rings, camera_x at requested frames) ===");
        while (driveTraceIndex < trace.frameCount() && driveTraceIndex <= maxFrame) {
            TraceFrame driveFrame = trace.getFrame(driveTraceIndex);
            TraceExecutionPhase phase =
                    TraceReplayBootstrap.phaseForReplay(trace, previousDriveFrame, driveFrame);
            driveOneFrame(trace, frameDriver, replayStart, phase, driveTraceIndex);

            if (wanted.contains(driveFrame.frame())) {
                var sprite = GameServices.camera().getFocusedSprite();
                int rings = sprite.getRingCount();
                int camX = GameServices.camera().getX() & 0xFFFF;
                System.out.printf(
                        "VERIFY frame=%d rings=%d (0x%02X) camera_x=0x%04X x=%d y=%d phase=%s%n",
                        driveFrame.frame(), rings, rings, camX,
                        sprite.getCentreX(), sprite.getCentreY(), phase);
            }
            driveTraceIndex++;
            previousDriveFrame = driveFrame;
        }
        System.out.println("=== VERIFY complete ===");
    }

    /**
     * Drives one trace frame using the same phase rules as the test S3K loop.
     * Returns {@code true} if a gameplay tick was executed (false for cursor-only
     * advance phases).
     */
    private boolean driveOneFrame(TraceData trace, RecordingFrameDriver frameDriver,
                                  TraceReplayBootstrap.ReplayStartState replayStart,
                                  TraceExecutionPhase phase, int driveTraceIndex) {
        if (phase == TraceExecutionPhase.VBLANK_ONLY) {
            frameDriver.skipFrameFromRecording();
            // Mirror AbstractTraceReplayTest: the S3K complete-run handoff row
            // is skipped for comparison, but ROM ran a full LevelLoop on it and
            // incremented Level_frame_counter before Process_Sprites. Apply the
            // single counter tick so S3K Tails-CPU gates read ROM-visible
            // Level_frame_counter natively.
            if (driveTraceIndex == replayStart.startingTraceIndex()
                    && TraceReplayBootstrap.isS3kCompleteRunHandoffCounterTickRow(trace)) {
                SpriteManager handoffSprites = GameServices.spritesOrNull();
                if (handoffSprites != null) {
                    handoffSprites.setFrameCounter(handoffSprites.getFrameCounter() + 1);
                }
            }
            return false;
        }
        // NOTE: the S3K replay loop in AbstractTraceReplayTest.replayS3kTrace
        // does NOT special-case ADVANCE_ONLY; only VBLANK_ONLY is skipped and
        // every other phase drives a gameplay tick. Mirror that exactly — an
        // ADVANCE_ONLY frame still runs a tick (and advances the vbla counter),
        // so do not divert it to consumeRecordingFrameInputOnly or the vbla
        // counter slips by one mid-intro and the player path desyncs.
        if (TraceReplayBootstrap.shouldUsePreviousRecordingInputForTraceReplay(trace)) {
            frameDriver.stepFrameFromRecordingUsingPreviousInput();
        } else {
            frameDriver.stepFrameFromRecording();
        }
        return true;
    }

    /**
     * Renders the current LEVEL scene to the back buffer. Mirrors the default
     * LEVEL branch of {@code Engine.draw()}: clear with the level background
     * colour, draw level + sprites by priority, flush, and block until GL is
     * finished so the subsequent {@code glReadPixels} sees the completed frame.
     */
    private void renderFrame() {
        LevelManager levelManager = GameServices.level();
        GraphicsManager graphicsManager = GameServices.graphics();
        levelManager.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        levelManager.drawWithSpritePriority(GameServices.sprites());
        graphicsManager.flush();
        glFinish();
    }

    /**
     * Resolves {@code --trace} against the catalog by directory name, or by
     * 0-based catalog index, or as a direct filesystem path to a trace dir.
     */
    private TraceEntry resolveTrace(String spec) {
        Path catalogDir = Paths.get(GameServices.configuration()
                .getString(SonicConfiguration.TRACE_CATALOG_DIR));
        List<TraceEntry> entries = TraceCatalog.scan(catalogDir);

        // by 0-based catalog index
        try {
            int index = Integer.parseInt(spec.trim());
            if (index >= 0 && index < entries.size()) {
                return entries.get(index);
            }
        } catch (NumberFormatException ignored) {
            // not an index
        }

        // by trace directory name
        for (TraceEntry e : entries) {
            if (e.dir().getFileName().toString().equalsIgnoreCase(spec)) {
                return e;
            }
        }

        // as a direct filesystem path to a trace dir
        Path asPath = Paths.get(spec);
        for (TraceEntry e : TraceCatalog.scan(asPath.getParent() != null
                ? asPath.getParent() : asPath)) {
            if (e.dir().equals(asPath) || e.dir().toAbsolutePath().equals(asPath.toAbsolutePath())) {
                return e;
            }
        }

        throw new IllegalArgumentException("No trace matched '" + spec
                + "' (catalog " + catalogDir + " has " + entries.size() + " entries)");
    }

    private static String resolveFfmpeg() {
        return FfmpegEncoder.findFfmpeg()
                .map(Path::toString)
                .orElse("ffmpeg");
    }

    /**
     * {@link com.openggf.trace.replay.TraceReplayFixture} view backed by the
     * shared {@link RecordingFrameDriver}, so {@link TraceReplaySessionBootstrap}
     * sees exactly the fixture surface the test's {@code HeadlessTestFixture}
     * provides (sprite, gameplay mode, recording-cursor helpers).
     */
    private static final class CaptureFixture implements com.openggf.trace.replay.TraceReplayFixture {
        private final RecordingFrameDriver driver;

        private CaptureFixture(RecordingFrameDriver driver) {
            this.driver = driver;
        }

        @Override
        public com.openggf.sprites.playable.AbstractPlayableSprite sprite() {
            return driver.getSprite();
        }

        @Override
        public com.openggf.game.session.GameplayModeContext gameplayMode() {
            return SessionManager.getCurrentGameplayMode();
        }

        @Override
        public int stepFrameFromRecording() {
            return driver.stepFrameFromRecording();
        }

        @Override
        public int skipFrameFromRecording() {
            return driver.skipFrameFromRecording();
        }

        @Override
        public int consumeRecordingFrameInputOnly() {
            return driver.consumeRecordingFrameInputOnly();
        }

        @Override
        public void advanceRecordingCursor(int frameCount) {
            driver.advanceRecordingCursor(frameCount);
        }

        @Override
        public int peekRecordingInputAt(int offset) {
            return driver.peekRecordingInputAt(offset);
        }
    }
}
