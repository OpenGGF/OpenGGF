package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.capture.BackpressurePolicy;
import com.openggf.capture.CaptureRecorder;
import com.openggf.capture.DrainPcmAudioTap;
import com.openggf.capture.FfmpegEncoder;
import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceData;
import com.openggf.trace.catalog.TraceCatalog;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.trace.replay.TraceReplayDriver;
import com.openggf.trace.replay.TraceReplayFixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Command-line trace-capture driver. Boots a headless gameplay session against
 * a real ROM/GL context, deterministically replays a recorded trace through
 * {@link TraceReplayDriver}, and records the rendered frames + chip audio to a
 * lossless MKV via {@link FfmpegEncoder} / {@link CaptureRecorder}.
 *
 * <p>Invocation (Maven):
 * <pre>
 *   mvn exec:java "-Dexec.mainClass=com.openggf.tools.TraceCaptureTool" \
 *       "-Dexec.args=--trace aiz1 --out-dir target/trace-videos"
 * </pre>
 *
 * <p>Output: {@code &lt;out-dir&gt;/capture-&lt;label&gt;-&lt;UTC&gt;.mkv}. The UTC timestamp
 * string is formatted here in {@code main} (not inside the recorder) so the
 * recorder stays deterministic / testable.
 *
 * <p>Render visibility (desync ghosts, game HUD, debug HUD) is governed by the
 * {@code TRACE_SHOW_*} config consumed inside the level render path
 * ({@code LevelRenderer} / {@code TraceRenderVisibility}); this tool does not
 * override it.
 */
public final class TraceCaptureTool {

    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;
    private static final int QUEUE_CAPACITY = 8;

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
                       boolean showGhosts) {

        public static Args parse(String[] argv) {
            SonicConfigurationService config = GameServices.configuration();
            String trace = null;
            String outDir = config.getString(SonicConfiguration.CAPTURE_OUTPUT_DIR);
            int scale = config.getInt(SonicConfiguration.CAPTURE_SCALE);
            int fps = config.getInt(SonicConfiguration.CAPTURE_FPS);
            String codec = config.getString(SonicConfiguration.CAPTURE_CODEC);
            boolean showGhosts = config.getBoolean(SonicConfiguration.TRACE_SHOW_DESYNC_GHOSTS);

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
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            if (trace == null || trace.isBlank()) {
                throw new IllegalArgumentException("--trace <id|name|dir> is required");
            }
            return new Args(trace, Paths.get(outDir), scale, fps, codec, showGhosts);
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
        // (TraceRenderVisibility) honors it during capture.
        GameServices.configuration().setConfigValue(
                SonicConfiguration.TRACE_SHOW_DESYNC_GHOSTS, args.showGhosts());

        // --- resolve trace -------------------------------------------------
        TraceEntry entry = resolveTrace(args.trace());
        System.out.println("Capturing trace: " + entry.dir()
                + " (" + entry.gameId() + " zone=" + entry.zone() + " act=" + entry.act() + ")");

        TraceData trace = TraceData.load(entry.dir());
        Bk2Movie movie = new Bk2MovieLoader().load(entry.bk2Path());

        // --- boot headless gameplay session -------------------------------
        HeadlessGameBoot boot = new HeadlessGameBoot(SCREEN_WIDTH, SCREEN_HEIGHT);
        Path romPath = Paths.get(RomManager.resolveRomForGame(entry.gameId()));
        GameLoop loop = boot.boot(romPath, entry.zone(), entry.act());

        // --- deterministic trace replay bootstrap -------------------------
        PlaybackDebugManager playback = GameServices.playbackDebug();
        TraceReplayFixture fixture = new HeadlessFixture(playback, loop);
        // No-op desync-pause: capture must record through mismatches (the
        // diverging ghost is the point) and run to trace completion, not freeze.
        TraceReplayDriver driver = new TraceReplayDriver(
                trace, movie, fixture, loop, () -> GameServices.camera().getFocusedSprite(),
                () -> { });
        driver.start(entry.zone(), entry.act());

        // --- capture pipeline ---------------------------------------------
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(UTC_STAMP);
        String label = entry.dir().getFileName().toString();
        FfmpegEncoder encoder = new FfmpegEncoder(resolveFfmpeg(), args.scale());
        CaptureRecorder recorder = new CaptureRecorder(
                encoder, BackpressurePolicy.BLOCK, QUEUE_CAPACITY,
                args.outDir(), label, timestamp);

        GlReadPixelsGrabber grabber = new GlReadPixelsGrabber(SCREEN_WIDTH, SCREEN_HEIGHT);
        DrainPcmAudioTap audioTap = new DrainPcmAudioTap(GameServices.audio());
        TraceCaptureSession session = new TraceCaptureSession(
                loop, driver, grabber, audioTap, recorder, args.fps());

        // Drive capture at the audio backend's real synthesis rate (the SMPS
        // driver runs at the device rate — 48 kHz). A hardcoded rate would
        // mismatch the synth and pitch-shift the audio.
        int sampleRate = GameServices.audio().getBackend().outputSampleRate();
        session.start(SCREEN_WIDTH, SCREEN_HEIGHT, sampleRate);
        long frames = 0;
        // Defensive cap: the comparator drives completion, but bound the loop a
        // little past the trace length so a stuck cursor can never grow the
        // ffmpeg temp unbounded.
        long maxFrames = (long) trace.frameCount() + 600;
        while (session.stepAndCapture()) {
            if (++frames >= maxFrames) {
                System.err.println("Capture frame cap reached (" + maxFrames
                        + "); finalizing early.");
                break;
            }
        }
        Path out = session.finish();
        System.out.println("Captured " + frames + " frames -> " + out.toAbsolutePath());
        if (Files.isRegularFile(out)) {
            System.out.println("Output size: " + Files.size(out) + " bytes");
        }
        return boot;
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
     * Headless {@link TraceReplayFixture} backed by the booted {@link GameLoop}
     * and {@link PlaybackDebugManager}. Mirrors the launcher's {@code LiveFixture}
     * (the trace-replay bootstrap only uses {@link #sprite()},
     * {@link #gameplayMode()}, and the BK2 step helpers).
     */
    private static final class HeadlessFixture implements TraceReplayFixture {
        private final PlaybackDebugManager playback;
        private final GameLoop gameLoop;

        private HeadlessFixture(PlaybackDebugManager playback, GameLoop gameLoop) {
            this.playback = playback;
            this.gameLoop = gameLoop;
        }

        @Override
        public AbstractPlayableSprite sprite() {
            return GameServices.camera().getFocusedSprite();
        }

        @Override
        public GameplayModeContext gameplayMode() {
            return SessionManager.getCurrentGameplayMode();
        }

        @Override
        public int stepFrameFromRecording() {
            Bk2FrameInput frame = playback.currentFrameOrThrow();
            int mask = toReplayValidationMask(frame);
            gameLoop.step();
            return mask;
        }

        @Override
        public int skipFrameFromRecording() {
            Bk2FrameInput frame = playback.currentFrameOrThrow();
            int mask = toReplayValidationMask(frame);
            playback.advanceCurrentFrameWithoutGameplay();
            return mask;
        }

        @Override
        public int consumeRecordingFrameInputOnly() {
            Bk2FrameInput frame = playback.currentFrameOrThrow();
            int mask = toReplayValidationMask(frame);
            playback.advanceCurrentFrameWithoutGameplay();
            return mask;
        }

        @Override
        public void advanceRecordingCursor(int frameCount) {
            for (int i = 0; i < frameCount; i++) {
                playback.advanceCurrentFrameWithoutGameplay();
            }
        }

        private static int toReplayValidationMask(Bk2FrameInput frame) {
            int mask = frame.p1InputMask();
            if (frame.p1ActionMask() != 0) {
                mask |= AbstractPlayableSprite.INPUT_JUMP;
            }
            return mask;
        }
    }
}
