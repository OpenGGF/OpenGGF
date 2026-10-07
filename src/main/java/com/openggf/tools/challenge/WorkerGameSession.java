package com.openggf.tools.challenge;

import com.openggf.ExclusiveLiveGameDriver;
import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tools.HeadlessGameBoot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;

/**
 * One process-owned normal opening, using production gameplay, GPU and PCM owners.
 * Originating task: 2026-10-07 Multigame prototype. No trace rows or positioned boot
 * state enter this session. A future in-process endpoint must pass the same media
 * and input isolation tests before replacing this process boundary.
 */
public final class WorkerGameSession implements ChallengeWorker.Playback {
    private final EngineContext services;
    private final HeadlessGameBoot boot;
    private final GameLoop loop;
    private final ExclusiveLiveGameDriver driver;
    private final short[] captureBuffer = new short[ChallengeProtocol.MAX_PCM_SHORTS];
    private final Thread ownerThread = Thread.currentThread();
    private boolean captureAttached;
    private boolean closed;
    private long diagnosticEpoch;

    private WorkerGameSession(EngineContext services, HeadlessGameBoot boot,
                              GameLoop loop, ExclusiveLiveGameDriver driver) {
        this.services = services;
        this.boot = boot;
        this.loop = loop;
        this.driver = driver;
    }

    /** Validates the declared source before acquiring a native context or session. */
    public static WorkerGameSession open(String game, Path rom) throws IOException {
        validateRom(game, rom);
        if (SessionManager.getCurrentWorldSession() != null) {
            throw new IllegalStateException("A challenge worker requires its own fresh process world");
        }
        EngineContext services = EngineContext.fromLegacySingletonsForBootstrap();
        configure(services.configuration(), game);
        HeadlessGameBoot boot = null;
        ExclusiveLiveGameDriver driver = null;
        WorkerGameSession session = null;
        try {
            boot = new HeadlessGameBoot(ChallengeProtocol.WIDTH, ChallengeProtocol.HEIGHT, services);
            GameLoop loop = boot.boot(rom, 0, 0, HardwareReadinessAdmissionPolicy.LIVE);
            // Confirm the actual pinned bytes too, rather than trusting that the
            // user's path remained unchanged between validation and ROM open.
            try {
                String pinned = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                        .digest(services.roms().getRom().readAllBytes()));
                if (!expectedSha1(game).equals(pinned)) throw new IOException("ROM changed during worker boot");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("JVM is missing SHA-1", impossible);
            }
            if (!game.equals(GameServices.module().getGameId().code())) {
                throw new IOException("Declared game does not match the detected ROM module");
            }
            driver = ExclusiveLiveGameDriver.acquire(loop);
            session = new WorkerGameSession(services, boot, loop, driver);
            if (services.audio().outputSampleRate() != ChallengeProtocol.RATE
                    || services.audio().presentationFrameRate() != 60) {
                throw new IllegalStateException("Worker requires 48000 Hz stereo at 60 Hz presentation");
            }
            services.audio().beginCaptureMode(ChallengeProtocol.RATE, 60);
            session.captureAttached = true;
            return session;
        } catch (IOException | RuntimeException | Error failure) {
            try {
                if (session != null) session.close();
                else release(services, boot, driver, false);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /** Preparation and START are observable snapshots, with no synthesized/played tick. */
    @Override
    public ChallengeProtocol.Frame initial(long generation, long sequence) {
        requireOpen();
        return frame(generation, sequence, new short[0], -1);
    }

    @Override
    public ChallengeProtocol.Frame step(long generation, long sequence, int heldMask) {
        requireOpen();
        var iteration = driver.step(heldMask);
        if (!diagnosticBoundaryStable()) diagnosticEpoch++;
        // The loop owns native pause presentation semantics. Muted/focus changes
        // happen in the host after this producer packet and never suppress service.
        loop.presentOuterFrame(false, false);
        int stereoFrames = services.audio().drainCaptureFrame(captureBuffer);
        if (stereoFrames < 0 || stereoFrames * 2 > captureBuffer.length) {
            throw new IllegalStateException("Production PCM exceeded the bounded worker packet");
        }
        return frame(generation, sequence, Arrays.copyOf(captureBuffer, stereoFrames * 2),
                iteration.controllerPolled() ? heldMask : -1);
    }

    /** Opaque process-local diagnostic only: never accepted from worker transport. */
    static final class DiagnosticCheckpoint {
        private final WorkerGameSession owner;
        private final GameplayModeContext context;
        private final Object level;
        private final long loadGeneration, layoutVersion, epoch;
        private final int zone, act;
        private final CompositeSnapshot gameplay;
        private final AudioLogicalSnapshot audio;

        private DiagnosticCheckpoint(WorkerGameSession owner, GameplayModeContext context,
                                     long epoch, AudioLogicalSnapshot audio) {
            this.owner = owner;
            this.context = context;
            var manager = context.getLevelManager();
            level = manager.getCurrentLevel();
            loadGeneration = manager.getCompletedProductionLoadGeneration();
            layoutVersion = context.getRewindRegistry().courseLayoutVersion();
            zone = manager.getCurrentZone();
            act = manager.getCurrentAct();
            this.epoch = epoch;
            gameplay = context.getRewindRegistry().capture();
            this.audio = audio;
        }
    }

    DiagnosticCheckpoint captureDiagnosticCheckpoint() {
        requireDiagnosticBoundary();
        return new DiagnosticCheckpoint(this, SessionManager.getCurrentGameplayMode(),
                diagnosticEpoch, services.audio().captureLogicalSnapshot());
    }

    void restoreDiagnosticCheckpoint(DiagnosticCheckpoint checkpoint) {
        requireDiagnosticBoundary();
        Objects.requireNonNull(checkpoint, "checkpoint");
        var context = SessionManager.getCurrentGameplayMode();
        var manager = context.getLevelManager();
        if (checkpoint.owner != this || checkpoint.context != context
                || checkpoint.epoch != diagnosticEpoch
                || checkpoint.level != manager.getCurrentLevel()
                || checkpoint.loadGeneration != manager.getCompletedProductionLoadGeneration()
                || checkpoint.zone != manager.getCurrentZone() || checkpoint.act != manager.getCurrentAct()
                || checkpoint.layoutVersion != context.getRewindRegistry().courseLayoutVersion()) {
            throw new IllegalArgumentException("Checkpoint crossed a worker, load or timeline boundary");
        }
        // The ordinary rewind controller also pairs gameplay registry state with
        // audio logical keyframes. Capture consumers do not own driver state.
        context.getRewindRegistry().restore(checkpoint.gameplay);
        services.audio().discardAudioCommandsAfter(checkpoint.audio.commandTimelineFrame());
        services.audio().restoreLogicalSnapshot(checkpoint.audio);
    }

    CompositeSnapshot captureDiagnosticGameplay() {
        requireDiagnosticBoundary();
        return SessionManager.getCurrentGameplayMode().getRewindRegistry().capture();
    }

    boolean diagnosticBoundaryStable() {
        var context = SessionManager.getCurrentGameplayMode();
        if (context == null || loop.getCurrentGameMode() != GameMode.LEVEL || loop.isPaused()) return false;
        // ROM Game_paused is separate from window/user and controlled-presentation
        // pause. The normal level admission owner reads this same native flag.
        if (context.getGameStateManager().isGamePaused()) return false;
        var controller = ControlledFrameRuntime.controller(context);
        if (controller != null && controller.presentationPaused()) return false;
        var manager = context.getLevelManager();
        var title = loop.getTitleCardProvider();
        var player = context.getCamera().getFocusedSprite();
        return manager != null && manager.getCurrentLevel() != null
                && player != null && !player.getDead() && !player.isInDeathRestartRoutine()
                && !player.isControlLocked() && !player.isObjectControlled()
                && !manager.hasPendingLevelExit() && !manager.hasPendingFreshLevelTransitionBoundary()
                && !context.getFadeManager().hasPendingCompletion()
                && (title == null || !title.isOverlayActive());
    }

    private void requireDiagnosticBoundary() {
        requireOpen();
        if (!diagnosticBoundaryStable())
            throw new IllegalStateException("Diagnostic rewind requires stable unpaused level play");
    }

    private ChallengeProtocol.Frame frame(long generation, long sequence, short[] pcm, int polledMask) {
        byte[] rgba = topDownRgba(render());
        var player = GameServices.camera().getFocusedSprite();
        return new ChallengeProtocol.Frame(generation, sequence, rgba, pcm,
                loop.getCurrentGameMode().name(), player == null ? 0 : player.getCentreX(),
                player == null ? 0 : player.getCentreY(), player == null ? 0 : player.getRingCount(),
                EngineTiming.vIntRunCounter(services).value(),
                GameServices.level().getFrameCounter(), polledMask);
    }

    /** Mirrors Engine's native scene/title/fade recipe, retaining full clears and HUD. */
    private RgbaImage render() {
        GameMode mode = loop.getCurrentGameMode();
        if (mode != GameMode.LEVEL && mode != GameMode.TITLE_CARD
                && mode != GameMode.CONTINUE_SCREEN) {
            throw new IllegalStateException("This opening prototype cannot present " + mode
                    + "; restart the complete challenge");
        }
        GraphicsManager graphics = services.graphics();
        var level = GameServices.level();
        graphics.runPendingRenderThreadTasks();
        glColorMask(true, true, true, true);
        if (mode == GameMode.CONTINUE_SCREEN) glClearColor(0, 0, 0, 1);
        else level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        if (mode == GameMode.CONTINUE_SCREEN) {
            GameServices.camera().setX((short) 0);
            GameServices.camera().setY((short) 0);
            var provider = loop.getContinueScreenProvider();
            if (provider == null) throw new IllegalStateException("Missing native continue renderer");
            provider.draw();
        } else {
            var controller = mode == GameMode.LEVEL
                    ? ControlledFrameRuntime.controller(SessionManager.getCurrentGameplayMode()) : null;
            if (controller == null || !controller.drawScene()) {
                level.drawWithSpritePriority(GameServices.sprites());
            }
            graphics.flush();
            if (controller != null) {
                controller.drawOverlay();
                graphics.flushScreenSpace();
            }
            var title = loop.getTitleCardProvider();
            if (title != null && (mode == GameMode.TITLE_CARD || title.isOverlayActive())) {
                graphics.flush();
                graphics.resetForFixedFunction();
                title.draw();
                graphics.flushScreenSpace();
            }
        }
        graphics.flush();
        var ui = graphics.getUiRenderPipeline();
        if (ui != null) ui.renderFadePass();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(ChallengeProtocol.WIDTH, ChallengeProtocol.HEIGHT);
    }

    static byte[] topDownRgba(RgbaImage image) {
        byte[] bytes = new byte[image.pixels().length * 4];
        int offset = 0;
        // ScreenshotCapture has already inverted GL's bottom-up orientation.
        for (int argb : image.pixels()) {
            bytes[offset++] = (byte) (argb >>> 16);
            bytes[offset++] = (byte) (argb >>> 8);
            bytes[offset++] = (byte) argb;
            bytes[offset++] = (byte) (argb >>> 24);
        }
        return bytes;
    }

    static void configure(SonicConfigurationService config, String game) {
        expectedSha1(game);
        config.resetToDefaults();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        config.setSessionOverride(SonicConfiguration.DISPLAY_WINDOW_AUTOSIZE, false);
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH, ChallengeProtocol.WIDTH);
        config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT, ChallengeProtocol.HEIGHT);
        config.setSessionOverride(SonicConfiguration.DISPLAY_SHADER_SELECTION, "OFF");
        config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
        config.setSessionOverride(SonicConfiguration.FPS, 60);
        config.setSessionOverride(SonicConfiguration.REGION, "NTSC");
        config.setSessionOverride(SonicConfiguration.LOAD_TIME_SIMULATION, "REALISTIC");
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.EDITOR_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_VHS_EFFECT, false);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT, false);
        config.setSessionOverride(SonicConfiguration.AUDIO_FM_CORE, "fast");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, "off");
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                "s3k".equals(game) ? "tails" : "");
        config.setSessionOverride(SonicConfiguration.PLAYBACK_MOVIE_PATH, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.resolveDisplayAspect();
    }

    static void validateRom(String game, Path rom) throws IOException {
        Objects.requireNonNull(rom, "rom");
        String expected = expectedSha1(game);
        long expectedSize = switch (game) {
            case "s1" -> 0x80000;
            case "s2" -> 0x100000;
            case "s3k" -> 0x400000;
            default -> throw new IllegalArgumentException("Unknown opening game");
        };
        if (!rom.isAbsolute() || !Files.isRegularFile(rom) || !Files.isReadable(rom)
                || Files.size(rom) != expectedSize) {
            throw new IOException("Opening requires a readable absolute path to the supported " + game + " ROM");
        }
        try (InputStream input = Files.newInputStream(rom)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int size;
            long total = 0;
            while ((size = input.read(buffer)) != -1) {
                total += size;
                if (total > expectedSize) throw new IOException("ROM changed size during validation");
                digest.update(buffer, 0, size);
            }
            if (total != expectedSize) throw new IOException("ROM changed size during validation");
            if (!expected.equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new IOException("Unsupported ROM identity for " + game
                        + "; use the documented World REV01/locked-on image");
            }
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM is missing SHA-1", impossible);
        }
    }

    private static String expectedSha1(String game) {
        return switch (Objects.requireNonNull(game, "game")) {
            case "s1" -> "69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b";
            case "s2" -> "8bca5dcef1af3e00098666fd892dc1c2a76333f9";
            case "s3k" -> "cfbf98c36c776677290a872547ac47c53d2761d6";
            default -> throw new IllegalArgumentException("Opening game must be s1, s2 or s3k");
        };
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Worker session is closed");
        requireOwner();
    }

    private void requireOwner() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Worker session belongs to its native owner thread");
        }
    }

    @Override
    public void close() {
        if (closed) return;
        requireOwner();
        closed = true;
        release(services, boot, driver, captureAttached);
    }

    private static void release(EngineContext services, HeadlessGameBoot boot,
                                ExclusiveLiveGameDriver driver, boolean captureAttached) {
        Throwable failure = null;
        if (captureAttached) {
            try { services.audio().endCaptureMode(); }
            catch (RuntimeException | Error e) { failure = e; }
        }
        try { if (driver != null) driver.close(); }
        catch (RuntimeException | Error e) { failure = combine(failure, e); }
        try { SessionManager.closeGameplaySession(); }
        catch (RuntimeException | Error e) { failure = combine(failure, e); }
        try { services.audio().destroy(); }
        catch (RuntimeException | Error e) { failure = combine(failure, e); }
        // Dispose GPU resources while the context is current, even if native
        // session teardown later throws. The process supervisor bounds hard faults.
        try { if (boot != null) services.graphics().cleanup(); }
        catch (RuntimeException | Error e) { failure = combine(failure, e); }
        try { if (boot != null) boot.close(); }
        catch (RuntimeException | Error e) { failure = combine(failure, e); }
        services.configuration().clearSessionOverrides();
        if (failure instanceof Error e) throw e;
        if (failure instanceof RuntimeException e) throw e;
    }

    private static Throwable combine(Throwable first, Throwable next) {
        if (first == null) return next;
        first.addSuppressed(next);
        return first;
    }
}
