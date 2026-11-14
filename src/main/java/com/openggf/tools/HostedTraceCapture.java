package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.LevelFrameResult;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.control.RecordedInputOwnership;
import com.openggf.game.GameMode;
import com.openggf.graphics.GraphicsManager;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;
import com.openggf.capture.AudioFrameTap;
import com.openggf.capture.BackpressurePolicy;
import com.openggf.capture.CaptureRecorder;
import com.openggf.capture.CapturedFrame;
import com.openggf.capture.FfmpegEncoder;
import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.internal.HostedGameMedia;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.save.SelectedTeam;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@link TraceCaptureTool} for a trace whose game is hosted from media other than a cartridge ROM
 * (Sonic CD's disc). The game comes from the trace's {@code game}, its module from the stock
 * registry, and the media and starting level from the module's {@link HostedGameMedia}; a game
 * without that service is not handled here and keeps the cartridge path unchanged.
 *
 * <p>The replay is the one the hosted game's own fixture tests run: boot the level from the media,
 * then drive every trace row from the BK2 at {@code bk2_frame_offset}. Where the load already ran
 * the main loop's first iteration ({@link HostedGameMedia#loadIsFirstTraceRow}), row 0 is the
 * loaded state and its BK2 row is consumed without a step; every later row is a full step or, on a
 * frame the native loop did not complete, a V-INT-only row, chosen by the shared counter model
 * ({@link TraceReplayBootstrap#phaseForReplay}). Each row is one presented frame, so video frame N
 * is trace row N (or {@code N - START} within {@code --clip frames:START:END}).
 *
 * <p>The trace is comparison only: the BK2 inputs drive the engine, the recorded input of each row
 * is checked against them (a mismatch is a misaligned movie and stops the capture), and the first
 * row whose player position, speeds or camera differ from the recording is reported, not used.
 */
final class HostedTraceCapture {
    private static final DateTimeFormatter UTC_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final int SCREEN_WIDTH = 320;
    /** Rows a clip may run past the last trace row: the production loop carries on from the movie. */
    private static final int MAX_TAIL_ROWS = 2000;

    private final TraceEntry entry;
    private final TraceData trace;
    private final GameModule module;
    private final HostedGameMedia media;
    private final HostedGameMedia.Level level;

    private HostedTraceCapture(TraceEntry entry, TraceData trace, GameModule module, HostedGameMedia media,
                               HostedGameMedia.Level level) {
        this.entry = entry;
        this.trace = trace;
        this.module = module;
        this.media = media;
        this.level = level;
    }

    TraceEntry entry() {
        return entry;
    }

    HostedGameMedia.Level level() {
        return level;
    }

    /**
     * Resolves {@code spec} as a trace directory of a media-hosted game, or returns null when it is
     * not a trace directory or its game boots from a cartridge ROM.
     *
     * @param movieOverride the BK2 to drive with; null takes the trace directory's single
     *                      {@code .bk2}, else {@code movie.bk2} beside it (the native fixtures'
     *                      {@code <take>/trace} + {@code <take>/movie.bk2} layout)
     * @throws IllegalArgumentException naming the problem when the directory's game is unknown or
     *                                  its movie cannot be found
     */
    static HostedTraceCapture resolve(String spec, Path movieOverride) throws IOException {
        Path dir = Path.of(spec).toAbsolutePath().normalize();
        if (!Files.isRegularFile(dir.resolve("metadata.json"))) {
            return null;
        }
        TraceData trace = TraceData.load(dir);
        TraceMetadata meta = trace.metadata();
        GameModule module;
        try {
            module = hostedModule(String.valueOf(meta.game()));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Trace " + dir + " names game '" + meta.game()
                    + "', which no stock game module hosts");
        }
        HostedGameMedia media = HostedGameMedia.of(module);
        if (media == null) {
            return null;
        }
        Path movie = movieOverride != null ? movieOverride : findMovie(dir);
        if (movie == null || !Files.isRegularFile(movie)) {
            throw new IllegalArgumentException("Trace " + dir + " has no movie: pass --movie <file.bk2>"
                    + (movie == null ? "" : " (" + movie + " is not a file)"));
        }
        HostedGameMedia.Level level = media.levelOfTrace(dir);
        String main = meta.recordedMainCharacter();
        TraceEntry entry = new TraceEntry(dir, meta.game(), level.zone(), level.act(), trace.frameCount(),
                meta.bk2FrameOffset(), meta.preTraceOscillationFrames(),
                new SelectedTeam(main == null ? "sonic" : main, meta.recordedSidekicks()), movie, meta);
        return new HostedTraceCapture(entry, trace, module, media, level);
    }

    /**
     * The stock module of a game without a cartridge ROM, or null for a cartridge game (whose
     * module is not constructed, so its capture path is untouched).
     */
    static GameModule hostedModule(String game) {
        GameId id = GameId.fromCode(game);
        try {
            id.romGame();
            return null;
        } catch (IllegalStateException noCartridge) {
            if (id == GameId.STANDALONE) {
                throw new IllegalArgumentException("The standalone host has no stock game module", noCartridge);
            }
            return GameModuleRegistry.createStockModule(id);
        }
    }

    private static Path findMovie(Path dir) throws IOException {
        List<Path> own;
        try (Stream<Path> files = Files.list(dir)) {
            own = files.filter(p -> p.getFileName().toString().endsWith(".bk2")).toList();
        }
        if (own.size() == 1) return own.getFirst();
        Path beside = dir.getParent() == null ? null : dir.getParent().resolve("movie.bk2");
        return beside != null && Files.isRegularFile(beside) ? beside : null;
    }

    /** Replays and records; returns the boot for {@link TraceCaptureTool#main} to close. */
    HeadlessGameBoot run(TraceCaptureTool.Args args, TraceCaptureDimensions dimensions) throws Exception {
        TraceMetadata meta = trace.metadata();
        int[] window = args.clip() == null ? null : TraceCaptureTool.parseFrameWindow(args.clip());
        if (args.clip() != null && window == null) {
            throw new IllegalArgumentException("A " + meta.game() + " trace supports only --clip frames:START:END");
        }
        if (window != null && window[1] >= trace.frameCount() + MAX_TAIL_ROWS) {
            throw new IllegalArgumentException("clip end exceeds the trace row count (" + trace.frameCount()
                    + ") by more than " + MAX_TAIL_ROWS + " rows");
        }
        System.out.println(TraceCaptureTool.captureSetupSummary(entry, args, dimensions, args.verifyFrames() != null));
        System.out.println("Capturing trace: " + entry.dir() + " (" + entry.gameId() + " zone=" + level.zone()
                + " act=" + level.act() + ", movie " + entry.bk2Path() + ")");
        Bk2Movie movie = new Bk2MovieLoader().load(entry.bk2Path());

        TraceCapturePresentationSetup.CallerState callerPresentation =
                TraceCapturePresentationSetup.snapshot(GameServices.configuration());
        TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);
        TraceCapturePresentationSetup presentation = TraceCapturePresentationSetup.open(
                GameServices.configuration(), dimensions, callerPresentation, GameServices.audio(), args.fps());
        if (!args.nativeSpriteLimits()) {
            presentation.disableNativeSpriteLimits();
        }
        GameDataSource source = media.openConfiguredMedia();
        try (AutoCloseable closeSource = () -> { if (source instanceof AutoCloseable c) c.close(); };
             TraceCaptureTool.CaptureBootScope<HeadlessGameBoot> scope = TraceCaptureTool.CaptureBootScope.open(
                     presentation, () -> new HeadlessGameBoot(
                             dimensions.physicalWidth(), dimensions.physicalHeight(),
                             dimensions.logicalWidth(), dimensions.logicalHeight(),
                             SCREEN_WIDTH, TraceCaptureDimensions.LOGICAL_HEIGHT))) {
            HeadlessGameBoot boot = scope.boot();
            GameLoop loop = boot.boot(source, module, level.zone(), level.act());
            RecordingFrameDriver driver = new RecordingFrameDriver(GameServices.camera().getFocusedSprite());
            driver.setBk2Movie(movie, meta.bk2FrameOffset());
            Replay replay = new Replay(trace, driver, HostedGameMedia.loadIsFirstTraceRow(GameServices.module()));

            if (args.verifyFrames() != null) {
                verify(replay, args.verifyFrames());
            } else {
                capture(args, dimensions, window, replay, loop, movie);
            }
            return scope.transferAfterRestoration();
        }
    }

    private void verify(Replay replay, int[] frames) {
        int max = java.util.Arrays.stream(frames).max().orElse(-1);
        java.util.Set<Integer> wanted = new java.util.HashSet<>();
        for (int f : frames) wanted.add(f);
        System.out.println("=== VERIFY trajectory ===");
        for (int row = 0; row <= max && row < trace.frameCount(); row++) {
            replay.drive(row);
            if (wanted.contains(row)) {
                AbstractPlayableSprite sprite = GameServices.camera().getFocusedSprite();
                System.out.printf("VERIFY row=%d x=%04X y=%04X camera=%04X,%04X%n", row,
                        sprite.getCentreX() & 0xFFFF, sprite.getCentreY() & 0xFFFF,
                        GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF);
            }
        }
        replay.printDivergence();
        System.out.println("=== VERIFY complete ===");
    }

    private void capture(TraceCaptureTool.Args args, TraceCaptureDimensions dimensions, int[] window,
                         Replay replay, GameLoop loop, Bk2Movie movie) throws Exception {
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(UTC_STAMP);
        String label = entry.dir().getParent() != null && "trace".equals(entry.dir().getFileName().toString())
                ? entry.dir().getParent().getFileName().toString() : entry.dir().getFileName().toString();
        FfmpegEncoder encoder = new FfmpegEncoder(FfmpegEncoder.findFfmpeg().map(Path::toString).orElse("ffmpeg"), 1);
        SonicConfigurationService config = GameServices.configuration();
        encoder.setCodecs(args.codec(), config.getString(SonicConfiguration.CAPTURE_AUDIO_CODEC),
                config.getInt(SonicConfiguration.CAPTURE_ENCODER_THREADS),
                config.getString(SonicConfiguration.CAPTURE_ENCODER_PRESET));
        encoder.setCommandOverrides(config.getString(SonicConfiguration.CAPTURE_FFMPEG_PASS1_ARGS),
                config.getString(SonicConfiguration.CAPTURE_FFMPEG_PASS2_ARGS));
        CaptureRecorder recorder = new CaptureRecorder(encoder, BackpressurePolicy.BLOCK, 8, args.outDir(), label,
                timestamp, config.getString(SonicConfiguration.CAPTURE_CONTAINER));
        GlReadPixelsGrabber grabber = new GlReadPixelsGrabber(dimensions.physicalWidth(), dimensions.physicalHeight());
        int sampleRate = GameServices.audio().outputSampleRate();
        int frameRate = GameServices.audio().presentationFrameRate();
        var audioLease = GameServices.audio().beginLiveCaptureAudio(frameRate);
        AudioFrameTap audioTap = audioLease::drainPresentationFrame;
        try {
            recorder.start(dimensions.physicalWidth(), dimensions.physicalHeight(), frameRate, sampleRate);
        } catch (Throwable failedToOpen) {
            audioLease.close();
            throw failedToOpen;
        }
        TraceCaptureTool.HeadlessOuterAudioFrames audio = new TraceCaptureTool.HeadlessOuterAudioFrames(audioTap);
        short[] pcm = new short[16384];
        long captured = 0;
        boolean succeeded = false;
        try (TraceCaptureStateWriter state = new TraceCaptureStateWriter(args.outDir())) {
            int first = window == null ? 0 : window[0];
            int last = window == null ? trace.frameCount() - 1 : window[1];
            TailDrive tail = new TailDrive(loop, module, media, movie, trace.metadata().bk2FrameOffset());
            for (int row = 0; row <= last; row++) {
                if (row < trace.frameCount()) {
                    replay.drive(row);
                } else {
                    tail.step(row);
                }
                // Every row is one hardware frame: the V-INT that runs the sound driver
                // also ends a lag frame, so sound is presented on every row.
                audio.presentOuterFrame();
                if (row < first) {
                    audio.discardPresented();
                    continue;
                }
                if (row < trace.frameCount()) {
                    TraceReplayDrive.renderFrame(dimensions.logicalWidth());
                } else {
                    tail.render(dimensions.logicalWidth());
                }
                byte[] rgba = grabber.grab();
                int samples = audio.drainCaptured(pcm);
                state.row(captured, row, row < trace.frameCount() ? "LEVEL" : tail.mode().name());
                recorder.submit(new CapturedFrame(rgba, dimensions.physicalWidth(), dimensions.physicalHeight(),
                        pcm, samples, captured++));
            }
            TraceCaptureTool.requireCapturedFrames(captured);
            succeeded = true;
        } finally {
            try {
                if (succeeded) {
                    Path out = recorder.stop();
                    System.out.println("Captured " + captured + " frames -> " + out.toAbsolutePath()
                            + (window == null ? " (video frame N = trace row N)"
                            : " (video frame N = trace row N + " + window[0] + ")"));
                    replay.printDivergence();
                    Path manifest = TraceCaptureTool.publishCaptureManifest(out, entry, trace.metadata(), dimensions,
                            args.clip(), 0);
                    System.out.println("Output size: " + Files.size(out) + " bytes");
                    System.out.println("Capture manifest: " + manifest.toAbsolutePath());
                } else {
                    recorder.abort();
                }
            } finally {
                try {
                    audioLease.close();
                } finally {
                    grabber.close();
                }
            }
        }
    }

    /** The fixture tests' row drive, with the comparison reported rather than asserted. */
    static final class Replay {
        private final TraceData trace;
        private final RecordingFrameDriver driver;
        private final boolean loadIsFirstRow;
        private int firstDivergentRow = -1;
        private String divergence = "";

        Replay(TraceData trace, RecordingFrameDriver driver, boolean loadIsFirstRow) {
            this.trace = trace;
            this.driver = driver;
            this.loadIsFirstRow = loadIsFirstRow;
        }

        void drive(int row) {
            TraceFrame expected = trace.getFrame(row);
            TraceFrame prior = row > 0 ? trace.getFrame(row - 1) : null;
            int input;
            TraceExecutionPhase phase;
            if (row == 0 && loadIsFirstRow) {
                phase = TraceExecutionPhase.FULL_LEVEL_FRAME;
                input = driver.consumeRecordingFrameInputOnly();
            } else {
                phase = TraceReplayBootstrap.phaseForReplay(trace, prior, expected);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    input = driver.skipFrameFromRecording();
                } else {
                    int retries = 0;
                    do {
                        if (retries++ > 4096) {
                            throw new IllegalStateException("row " + row + ": the frame driver admits no gameplay");
                        }
                        input = driver.stepFrameFromRecording(Replay::beginPaletteFrame);
                    } while (driver.getLastFrameResult() == LevelFrameResult.SETUP_ONLY);
                }
            }
            if (input != expected.input()) {
                throw new IllegalStateException(String.format(
                        "Input alignment error at trace row %d: BK2=0x%04X trace=0x%04X (wrong movie or offset?)",
                        row, input, expected.input()));
            }
            if (firstDivergentRow < 0 && TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)) {
                compare(row, TraceReplayBootstrap.frameForGameplayComparison(trace, row, prior, expected, phase));
            }
        }

        private void compare(int row, TraceFrame expected) {
            AbstractPlayableSprite sprite = GameServices.camera().getFocusedSprite();
            int[] engine = {sprite.getCentreX() & 0xFFFF, sprite.getCentreY() & 0xFFFF, sprite.getXSpeed() & 0xFFFF,
                    sprite.getYSpeed() & 0xFFFF, sprite.getGSpeed() & 0xFFFF,
                    GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF};
            int[] recorded = {expected.x() & 0xFFFF, expected.y() & 0xFFFF, expected.xSpeed() & 0xFFFF,
                    expected.ySpeed() & 0xFFFF, expected.gSpeed() & 0xFFFF,
                    expected.cameraX() & 0xFFFF, expected.cameraY() & 0xFFFF};
            String[] names = {"x", "y", "x_speed", "y_speed", "g_speed", "camera_x", "camera_y"};
            StringBuilder diff = new StringBuilder();
            for (int i = 0; i < engine.length; i++) {
                if (engine[i] != recorded[i]) {
                    diff.append(String.format(" %s engine=%04X trace=%04X", names[i], engine[i], recorded[i]));
                }
            }
            if (!diff.isEmpty()) {
                firstDivergentRow = row;
                divergence = diff.toString();
            }
        }

        int firstDivergentRow() {
            return firstDivergentRow;
        }

        void printDivergence() {
            System.out.println(firstDivergentRow < 0
                    ? "Trace comparison: player and camera equal the recording on every compared row"
                    : "Trace comparison: first row differing from the recording " + firstDivergentRow + ":"
                    + divergence);
        }

        private static void beginPaletteFrame() {
            PaletteOwnershipRegistry registry = GameServices.paletteOwnershipRegistryOrNull();
            if (registry != null) registry.beginFrame();
        }
    }

    /**
     * Rows after the trace's last row. The recording ends at a level-frame boundary of the hosted run; what
     * follows (a warp, a fade, the next stage) belongs to the production game loop, which is stepped once per
     * row with the movie's own rows (neutral past the movie). Nothing is compared: the trace has no rows here,
     * and the hosted game declares how the run entered its stage before the first such row.
     */
    static final class TailDrive {
        private final GameLoop loop;
        private final GameModule module;
        private final HostedGameMedia media;
        private final Bk2Movie movie;
        private final int offset;
        private Bk2FrameInput previous;
        private boolean declared;

        TailDrive(GameLoop loop, GameModule module, HostedGameMedia media, Bk2Movie movie, int offset) {
            this.loop = loop;
            this.module = module;
            this.media = media;
            this.movie = movie;
            this.offset = offset;
        }

        /** The loop's mode, for the {@code mode} column of {@code state.csv}. */
        GameMode mode() {
            return loop.getCurrentGameMode();
        }

        void step(int row) {
            if (!declared) {
                media.declareRunPastTrace(module);
                declared = true;
            }
            int index = offset + row;
            Bk2FrameInput current = index < movie.getFrameCount() ? movie.getFrame(index)
                    : new Bk2FrameInput(index, 0, 0, false, 0, 0, false, "|..|........|........|");
            if (previous == null && index > 0) {
                previous = movie.getFrame(Math.min(index, movie.getFrameCount()) - 1);
            }
            try (var recorded = RecordedInputOwnership.acquire(loop.getInputHandler())) {
                loop.getInputHandler().setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
                previous = current;
                var ui = GameServices.graphics().getUiRenderPipeline();
                if (ui != null && !loop.ownsGameplayFadeLifecycle()) {
                    ui.updateFade();
                }
                loop.step();
            }
        }

        /** Draws the loop's current screen at {@code width}: the level, the title, or a native program screen. */
        void render(int width) {
            GraphicsManager graphics = GameServices.graphics();
            GameMode mode = loop.getCurrentGameMode();
            if (mode == GameMode.LEVEL) {
                TraceReplayDrive.renderFrame(width);
                return;
            }
            org.lwjgl.opengl.GL11.glViewport(0, 0, width, TraceCaptureDimensions.LOGICAL_HEIGHT);
            graphics.setViewport(0, 0, width, TraceCaptureDimensions.LOGICAL_HEIGHT);
            graphics.runPendingRenderThreadTasks();
            if (mode == GameMode.TITLE_SCREEN && !loop.isTitleEntryShowingLevel()) {
                var title = loop.getTitleScreenProvider();
                title.setClearColor();
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                title.draw();
                title.drawOverlay(new com.openggf.mods.ui.LevelOverlayCanvas(graphics,
                        graphics.getProjectionWidth(), graphics.getConfiguredScreenHeightPx()));
                graphics.flushScreenSpace();
            } else if (com.openggf.game.nativemenu.NativeMenuController.handles(mode)) {
                var screen = com.openggf.NativeMenuPresentationAccess.screen(loop);
                if (screen == null) throw new IllegalStateException("Native mode " + mode + " has no screen");
                screen.setClearColor();
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                graphics.resetForFixedFunction();
                screen.draw();
                graphics.flushScreenSpace();
            } else {
                throw new IllegalStateException("the tail of a clip reached mode " + mode + ", which no capture draws");
            }
            var ui = graphics.getUiRenderPipeline();
            if (ui != null) ui.renderFadePass();
            loop.renderHostedRunOverlay();
            glFinish();
        }
    }
}
