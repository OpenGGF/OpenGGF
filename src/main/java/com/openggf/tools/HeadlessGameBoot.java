package com.openggf.tools;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.ModSubsystem;
import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRouting;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.graphics.GraphicsManager;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;

import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glLoadMatrixf;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Boots a fully wired gameplay session against a hidden offscreen GL context,
 * without going through {@link Engine}, the master-title flow, or the input
 * loop. This is the shared headless entry used by the trace-capture driver
 * tool so it can drive a real {@link GameLoop} frame-by-frame and read the
 * rendered framebuffer back via {@code GlReadPixelsGrabber}.
 *
 * <p>The boot sequence mirrors the live engine path exactly:
 * <ol>
 *   <li>the offscreen GL setup of
 *       {@code VisualReferenceGenerator.initialize}, and</li>
 *   <li>the gameplay-session wiring of
 *       {@code Engine.initializeGameplayRuntime} (session open + manager
 *       attach + module registration + team bootstrap + camera focus).</li>
 * </ol>
 *
 * <p>Callers own the returned {@link GameLoop}'s frame stepping and must
 * {@link #close()} this boot to release the GL context and GLFW.
 */
public final class HeadlessGameBoot implements AutoCloseable {

    @FunctionalInterface
    interface BackendFactory {
        Backend create();
    }

    interface Backend extends AutoCloseable {
        void initialize(int width, int height, int logicalWidth, int logicalHeight)
                throws Exception;

        @Override
        void close() throws Exception;
    }

    @FunctionalInterface
    interface SessionCloser {
        void close() throws Exception;
    }

    private static final NativeGlLifecycle LWJGL_NATIVE_GL = new NativeGlLifecycle() {
        @Override
        public void initialize(HeadlessGameBoot boot) {
            boot.initGl();
        }

        @Override
        public void close(HeadlessGameBoot boot) {
            boot.closeNativeGl();
        }
    };

    private final int width;
    private final int height;
    private final int logicalWidth;
    private final int logicalHeight;
    private final int gameplayWidth;
    private final int gameplayHeight;
    private final EngineContext engineServices;
    private final NativeGlLifecycle nativeGlLifecycle;
    private final Backend backend;
    private final SessionCloser sessionCloser;

    private long window = NULL;

    // JOML projection state, mirroring VisualReferenceGenerator.
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final float[] matrixBuffer = new float[16];

    private Rom rom;
    private boolean closed;

    /**
     * Creates the hidden GLFW window and initialises the GL context /
     * graphics manager at the given framebuffer dimensions.
     */
    public HeadlessGameBoot(int width, int height) {
        this(width, height, width, height, width, height,
                EngineContext.fromLegacySingletonsForBootstrap(), LWJGL_NATIVE_GL,
                SessionManager::closeGameplaySession);
    }

    public HeadlessGameBoot(int width, int height, int logicalWidth, int logicalHeight) {
        this(width, height, logicalWidth, logicalHeight, logicalWidth, logicalHeight,
                EngineContext.fromLegacySingletonsForBootstrap(), LWJGL_NATIVE_GL,
                SessionManager::closeGameplaySession);
    }

    public HeadlessGameBoot(int width, int height, int logicalWidth, int logicalHeight,
            int gameplayWidth, int gameplayHeight) {
        this(width, height, logicalWidth, logicalHeight, gameplayWidth, gameplayHeight,
                EngineContext.fromLegacySingletonsForBootstrap(), LWJGL_NATIVE_GL,
                SessionManager::closeGameplaySession);
    }

    public HeadlessGameBoot(int width, int height, EngineContext engineServices) {
        this(width, height, width, height, width, height, engineServices, LWJGL_NATIVE_GL,
                SessionManager::closeGameplaySession);
    }

    private HeadlessGameBoot(int width, int height, int logicalWidth, int logicalHeight,
            int gameplayWidth, int gameplayHeight,
            EngineContext engineServices, NativeGlLifecycle nativeGlLifecycle,
            SessionCloser sessionCloser) {
        if (width <= 0 || height <= 0 || logicalWidth <= 0 || logicalHeight <= 0
                || gameplayWidth <= 0 || gameplayHeight <= 0) {
            throw new IllegalArgumentException("headless display dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.gameplayWidth = gameplayWidth;
        this.gameplayHeight = gameplayHeight;
        this.engineServices = java.util.Objects.requireNonNull(engineServices, "engineServices");
        this.nativeGlLifecycle = java.util.Objects.requireNonNull(nativeGlLifecycle,
                "nativeGlLifecycle");
        this.backend = null;
        this.sessionCloser = java.util.Objects.requireNonNull(sessionCloser, "sessionCloser");
        initializeNative();
    }

    HeadlessGameBoot(int width, int height, EngineContext engineServices,
                     NativeGlLifecycle nativeGlLifecycle) {
        this(width, height, width, height, width, height, engineServices, nativeGlLifecycle,
                SessionManager::closeGameplaySession);
    }

    HeadlessGameBoot(int width, int height, int logicalWidth, int logicalHeight,
            BackendFactory backendFactory) {
        this(width, height, logicalWidth, logicalHeight, backendFactory,
                SessionManager::closeGameplaySession);
    }

    HeadlessGameBoot(int width, int height, int logicalWidth, int logicalHeight,
            BackendFactory backendFactory, SessionCloser sessionCloser) {
        if (width <= 0 || height <= 0 || logicalWidth <= 0 || logicalHeight <= 0) {
            throw new IllegalArgumentException("headless display dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.gameplayWidth = logicalWidth;
        this.gameplayHeight = logicalHeight;
        this.engineServices = null;
        this.nativeGlLifecycle = null;
        this.backend = java.util.Objects.requireNonNull(backendFactory, "backendFactory").create();
        java.util.Objects.requireNonNull(this.backend, "backendFactory.create()");
        this.sessionCloser = java.util.Objects.requireNonNull(sessionCloser, "sessionCloser");
        try {
            backend.initialize(width, height, logicalWidth, logicalHeight);
        } catch (Throwable initializationFailure) {
            try {
                backend.close();
            } catch (Throwable cleanupFailure) {
                initializationFailure.addSuppressed(cleanupFailure);
            }
            if (initializationFailure instanceof Error error) throw error;
            if (initializationFailure instanceof RuntimeException exception) throw exception;
            throw new IllegalStateException("headless setup failed", initializationFailure);
        }
    }

    private void initializeNative() {
        try {
            nativeGlLifecycle.initialize(this);
        } catch (RuntimeException | Error initializationFailure) {
            try {
                nativeGlLifecycle.close(this);
            } catch (RuntimeException | Error cleanupFailure) {
                initializationFailure.addSuppressed(cleanupFailure);
            }
            throw initializationFailure;
        }
    }

    /**
     * Mirrors {@code VisualReferenceGenerator.initialize} lines 84-167:
     * hidden GLFW window, GL capabilities, graphics-manager shader init,
     * viewport, ortho projection, and alpha blending.
     */
    private void initGl() {
        // Process-wide engine services must be configured before any
        // gameplay session is opened.
        EngineServices.configure(engineServices);

        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

        window = glfwCreateWindow(width, height, "Headless Game Boot", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        glfwMakeContextCurrent(window);
        GL.createCapabilities();

        GraphicsManager graphicsManager = EngineServices.current().graphics();
        try {
            graphicsManager.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialise GraphicsManager shader", e);
        }

        glViewport(0, 0, width, height);
        graphicsManager.setProjectionWidth(logicalWidth);
        graphicsManager.applyResolvedDisplayWidth(logicalWidth);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        projectionMatrix.identity().ortho2D(0, logicalWidth, 0, logicalHeight);
        projectionMatrix.get(matrixBuffer);
        glLoadMatrixf(matrixBuffer);

        // There is no live Engine instance in this CLI context, so the
        // projection matrix must be supplied to the GraphicsManager directly
        // for shader-based rendering.
        graphicsManager.setProjectionMatrixBuffer(matrixBuffer.clone());

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        graphicsManager.setViewport(0, 0, width, height);
    }

    /**
     * Opens the ROM, detects the game module, opens a gameplay session,
     * attaches all gameplay managers, builds and wires a {@link GameLoop},
     * loads the requested zone/act, and registers the active team. Returns
     * the fully bound loop ready to be stepped.
     */
    public GameLoop boot(Path romPath, int zone, int act) throws IOException {
        return boot(romPath, zone, act, null, HardwareReadinessAdmissionPolicy.LIVE);
    }

    /**
     * Boots gameplay with an optional deterministic RNG seed applied after the
     * replay subsystem reset and before team/level initialization.
     */
    public GameLoop boot(Path romPath, int zone, int act, Long initialRngSeed) throws IOException {
        return boot(romPath, zone, act, initialRngSeed,
                HardwareReadinessAdmissionPolicy.LIVE);
    }

    public GameLoop boot(Path romPath, int zone, int act,
            HardwareReadinessAdmissionPolicy admissionPolicy) throws IOException {
        return boot(romPath, zone, act, null, admissionPolicy);
    }

    public GameLoop boot(Path romPath, int zone, int act, Long initialRngSeed,
            HardwareReadinessAdmissionPolicy admissionPolicy) throws IOException {
        // Process-wide services were configured in initGl(); resolve them via
        // the EngineServices locator rather than raw singletons.
        EngineContext services = engineServices;

        // --- ROM + module ------------------------------------------------
        rom = new Rom();
        if (!rom.open(romPath.toString())) {
            throw new IOException("Failed to open ROM file: " + romPath);
        }
        services.roms().setRom(rom);

        Optional<GameModule> detected =
                services.romDetection().detectAndCreateModule(rom);
        GameModule rootModule = detected.orElseThrow(() ->
                new IOException("No game module detected for ROM: " + romPath));
        // --- gameplay session + managers --------------------------------
        SessionManager.armNextGameplayAdmissionPolicy(admissionPolicy);
        GameplayModeContext mode = openResolvedSessionForBoot(services, rootModule);
        GameModule module = mode.getWorldSession().resolvedGameModule();
        GameplaySessionFactory.attachManagers(mode, services, createGameplayCamera(services));
        if (!mode.isGameplayRuntimeReady()) {
            throw new IllegalStateException(
                    "Gameplay runtime not ready after attachManagers");
        }

        // --- game loop wiring -------------------------------------------
        GameLoop loop = new GameLoop(services);
        loop.setGameplayMode(mode);
        loop.setInputHandler(new InputHandler());
        loop.setGameMode(GameMode.LEVEL);

        GameModuleRegistry.setCurrent(module);

        // --- audio backend (real SMPS synthesis) ------------------------
        // Must precede any music (loadZoneAndAct below) so the presentation
        // producer this backend installs is the one that admits the level's
        // music/SFX voices. The default NullAudioBackend synthesizes nothing,
        // which is what made captured audio silent.
        // Mirrors Engine.initializeGlobalGameplayServices (Engine.java:676);
        // setBackend() falls back to NullAudioBackend if OpenAL init fails.
        SonicConfigurationService audioConfig = services.configuration();
        if (audioConfig.getBoolean(SonicConfiguration.AUDIO_ENABLED)) {
            // Headless backend: it builds the normal presentation producer
            // over a NoDeviceAudioSink (AudioBackend.createPresentationSink),
            // so the same SMPS/WAV/raw-PCM voice registry renders offline
            // without ever opening an audio device. AudioManager's offline
            // capture lease is a non-consuming view of that producer, not a
            // replacement for it.
            services.audio().setBackend(
                    new HeadlessSmpsAudioBackend(audioConfig, services.profiler()));
        }

        // --- per-replay subsystem reset ---------------------------------
        // Clear the per-zone subsystem state (sprites, collision, camera, fade,
        // game state, timers, water, parallax, level events, RNG seed) the same
        // way the headless trace tests do via TestEnvironment.resetPerTest() and
        // the live launcher does via resetLevelSubsystemsForReplay(). Without
        // this, residual state left by the bootstrap EngineContext (title-screen
        // defaults, default level, residual level-event/intro state) leaks into
        // the AIZ intro and slips object/event timing by ~1 vbla frame mid-intro,
        // which cascades into a player-path desync (e.g. AIZ landing at trace
        // ~2170 lands 3px off and snowballs).
        TraceReplaySessionBootstrap.resetLevelSubsystemsForReplay();
        if (initialRngSeed != null) {
            GameServices.rng().setSeed(initialRngSeed);
        }

        // --- team + level -----------------------------------------------
        // Register the active team BEFORE loadZoneAndAct so the level load's
        // spawnPlayerAtStartPosition finds the main sprite (otherwise the
        // camera's focusedSprite ends up null / the player is never spawned at
        // the start position). This mirrors the trace-replay test fixture and
        // the live TraceReplayDriver bootstrap order.
        SonicConfigurationService configService = GameServices.configuration();
        GameplayTeamBootstrap.BootstrappedTeam team =
                GameplayTeamBootstrap.registerActiveTeam(
                        module, GameServices.sprites(), configService);

        GameServices.level().loadZoneAndAct(zone, act);

        GameServices.camera().setFocusedSprite(team.mainSprite());
        GameServices.camera().updatePosition(true);

        return loop;
    }

    private Camera createGameplayCamera(EngineContext services) {
        if (gameplayWidth == logicalWidth && gameplayHeight == logicalHeight) {
            return new Camera(services.configuration());
        }
        Object previousAspect = services.configuration().getConfigValue(
                SonicConfiguration.DISPLAY_ASPECT);
        try {
            services.configuration().setConfigValue(
                    SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
            services.configuration().resolveDisplayAspect();
            return new Camera(services.configuration());
        } finally {
            services.configuration().setConfigValue(
                    SonicConfiguration.DISPLAY_ASPECT, previousAspect);
            services.configuration().resolveDisplayAspect();
        }
    }

    /**
     * The no-graphics portion of headless boot, exposed so launch-resolution
     * integration can be verified without creating a native GLFW context.
     */
    public static GameplayModeContext openResolvedSessionForBoot(
            EngineContext services, GameModule rootModule) {
        return openResolvedSessionForBoot(services, rootModule,
                ModuleResolutionService.LaunchPolicy.DETERMINISTIC);
    }

    /** No-graphics boot using an explicit launch policy; integration-only STANDARD seam. */
    public static GameplayModeContext openResolvedSessionForBoot(
            EngineContext services, GameModule rootModule,
            ModuleResolutionService.LaunchPolicy policy) {
        java.util.Objects.requireNonNull(services, "services");
        try {
            if (!services.roms().isRomAvailable()) {
                throw new IllegalStateException("Headless stock boot requires an active ROM data source");
            }
            return openResolvedSessionForBoot(services, rootModule, policy,
                    com.openggf.game.StockGameDataSources.pinned(
                            services.roms().getRom(), rootModule));
        } catch (java.io.IOException sourceFailure) {
            throw new IllegalStateException("Failed to pin headless ROM data source", sourceFailure);
        }
    }

    /** No-graphics boot with an explicit source, used by metadata-only integration tests. */
    public static GameplayModeContext openResolvedSessionForBoot(
            EngineContext services, GameModule rootModule,
            ModuleResolutionService.LaunchPolicy policy,
            com.openggf.game.GameDataSource dataSource) {
        if (policy == ModuleResolutionService.LaunchPolicy.DETERMINISTIC) {
            disableExternalContentForDeterminism();
        }
        java.util.Objects.requireNonNull(services, "services");
        java.util.Objects.requireNonNull(rootModule, "rootModule");
        java.util.Objects.requireNonNull(policy, "policy");
        java.util.Objects.requireNonNull(dataSource, "dataSource");
        ModuleResolutionService moduleResolutionService = services.moduleResolutionService();
        GameModule module = moduleResolutionService.resolveForLaunch(rootModule,
                GameplayLaunchRequest.fromConfig(
                        services.configuration(), rootModule.getGameId().code()),
                policy);
        return SessionManager.openGameplaySession(rootModule, module, dataSource, null);
    }

    /** Detection-free no-ROM join point for an already owner-wrapped standalone module. */
    public static GameplayModeContext openStandaloneSessionForBoot(
            EngineContext services, GameModule module,
            com.openggf.game.GameDataSource dataSource) {
        return openStandaloneSessionForBoot(services, module, dataSource, null);
    }

    public static GameplayModeContext openStandaloneSessionForBoot(
            EngineContext services, GameModule module,
            com.openggf.game.GameDataSource dataSource,
            com.openggf.game.save.SaveSessionContext saveContext) {
        java.util.Objects.requireNonNull(services, "services");
        java.util.Objects.requireNonNull(module, "module");
        java.util.Objects.requireNonNull(dataSource, "dataSource");
        if (!GameModuleRouting.isStandalone(module)
                || !module.getGameCode().equals(module.getIdentifier())
                || dataSource.rom().isPresent()
                || saveContext != null && !module.getGameCode().equals(saveContext.gameCode())) {
            throw new IllegalArgumentException("Invalid standalone module/data source join");
        }
        return SessionManager.openGameplaySession(module, module, dataSource, saveContext);
    }

    static void disableExternalContentForDeterminism() {
        ModSubsystem.disableCurrentSessionForDeterminism();
    }

    /**
     * Closes the current gameplay session and ROM, then boots a fresh one on the
     * existing GL context.
     *
     * <p>Repeated measured passes need a genuinely fresh session — a second
     * bootstrap over a session whose objects have already spawned and despawned
     * is not the same workload as the first — but they must not pay for GL/GLFW
     * re-initialisation, and they must not accumulate ROM images: a leaked ~4MB
     * image per pass would show up directly in the heap and GC figures the run
     * exists to report.
     */
    public GameLoop reboot(Path romPath, int zone, int act) throws IOException {
        return reboot(
                romPath, zone, act, HardwareReadinessAdmissionPolicy.LIVE);
    }

    public GameLoop reboot(
            Path romPath,
            int zone,
            int act,
            HardwareReadinessAdmissionPolicy admissionPolicy)
            throws IOException {
        Throwable failure = null;
        try {
            sessionCloser.close();
        } catch (Throwable sessionFailure) {
            failure = sessionFailure;
        }
        if (rom != null) {
            try {
                rom.close();
                rom = null;
            } catch (Throwable romFailure) {
                failure = combine(failure, romFailure);
            }
        }
        if (failure != null) {
            rethrowCloseFailure(failure);
        }
        return boot(romPath, zone, act, admissionPolicy);
    }

    private static Throwable combine(Throwable first, Throwable later) {
        if (first == null) {
            return later;
        }
        first.addSuppressed(later);
        return first;
    }

    private static void rethrowCloseFailure(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        throw new IllegalStateException("headless teardown failed", failure);
    }

    /**
     * The single headless outer-frame audio boundary. Headless capture drivers
     * (the trace-capture tool and {@link TraceCaptureSession}) call this exactly
     * once for each outer framebuffer frame they treat as presented, then drain
     * that packet exactly once after the framebuffer grab.
     *
     * <p>{@code GameLoop.step()} / {@code stepInternal()} deliberately do not
     * present, so fast-forward simulation steps may enqueue audio commands
     * without multiplying the audio cadence. The mode is always
     * {@link PresentationMode#FORWARD}: a headless capture run has no modal
     * picker, pause, frame-step, or held rewind.
     */
    public static void presentHeadlessOuterAudioFrame() {
        GameServices.audio().presentFrame(PresentationMode.FORWARD);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        Throwable failure = null;
        try {
            sessionCloser.close();
        } catch (Throwable sessionFailure) {
            failure = sessionFailure;
        }
        if (failure != null) {
            rethrowCloseFailure(failure);
        }
        if (rom != null) {
            try {
                rom.close();
                rom = null;
            } catch (Throwable romFailure) {
                failure = romFailure;
            }
        }
        if (failure != null) {
            rethrowCloseFailure(failure);
        }
        if (backend != null) {
            try {
                backend.close();
            } catch (Throwable backendFailure) {
                failure = combine(failure, backendFailure);
            }
        } else {
            try {
                nativeGlLifecycle.close(this);
            } catch (Throwable nativeFailure) {
                failure = combine(failure, nativeFailure);
            }
        }
        if (failure != null) {
            rethrowCloseFailure(failure);
        }
        closed = true;
    }

    private void closeNativeGl() {
        if (window != NULL) {
            glfwDestroyWindow(window);
            window = NULL;
        }
        glfwTerminate();
    }

    interface NativeGlLifecycle {
        void initialize(HeadlessGameBoot boot);
        void close(HeadlessGameBoot boot);
    }
}
