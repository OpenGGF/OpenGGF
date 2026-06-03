package com.openggf.tools;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.audio.AudioManager;
import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.RomDetectionService;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;

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

    private final int width;
    private final int height;

    private long window = NULL;

    // JOML projection state, mirroring VisualReferenceGenerator.
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final float[] matrixBuffer = new float[16];

    private Rom rom;

    /**
     * Creates the hidden GLFW window and initialises the GL context /
     * graphics manager at the given framebuffer dimensions.
     */
    public HeadlessGameBoot(int width, int height) {
        this.width = width;
        this.height = height;
        initGl();
    }

    /**
     * Mirrors {@code VisualReferenceGenerator.initialize} lines 84-167:
     * hidden GLFW window, GL capabilities, graphics-manager shader init,
     * viewport, ortho projection, and alpha blending.
     */
    private void initGl() {
        // Process-wide engine services must be configured before any
        // gameplay session is opened.
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());

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

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        try {
            graphicsManager.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialise GraphicsManager shader", e);
        }

        glViewport(0, 0, width, height);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        projectionMatrix.identity().ortho2D(0, width, 0, height);
        projectionMatrix.get(matrixBuffer);
        glLoadMatrixf(matrixBuffer);

        // Engine.getInstance() returns null in this CLI context, so the
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
        // --- ROM + module ------------------------------------------------
        rom = new Rom();
        if (!rom.open(romPath.toString())) {
            throw new IOException("Failed to open ROM file: " + romPath);
        }
        RomManager.getInstance().setRom(rom);

        Optional<GameModule> detected =
                RomDetectionService.getInstance().detectAndCreateModule(rom);
        GameModule module = detected.orElseThrow(() ->
                new IOException("No game module detected for ROM: " + romPath));

        // --- gameplay session + managers --------------------------------
        EngineContext services = EngineServices.current();
        GameplayModeContext mode = SessionManager.openGameplaySession(module);
        GameplaySessionFactory.attachManagers(mode, services);
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
        // Must precede any music (loadZoneAndAct below) so the deterministic
        // capture runtime installed later by AudioManager.beginCaptureMode()
        // binds a real SMPS music stream. The default NullAudioBackend
        // synthesizes nothing, which is what made captured audio silent.
        // Mirrors Engine.initializeGlobalGameplayServices (Engine.java:676);
        // setBackend() falls back to NullAudioBackend if OpenAL init fails.
        SonicConfigurationService audioConfig = GameServices.configuration();
        if (audioConfig.getBoolean(SonicConfiguration.AUDIO_ENABLED)) {
            // Headless backend: synthesize SMPS for the capture tap but never
            // touch a sound device (no OpenAL).
            AudioManager.getInstance().setBackend(
                    new HeadlessSmpsAudioBackend(audioConfig, PerformanceProfiler.getInstance()));
        }

        // --- level + team -----------------------------------------------
        GameServices.level().loadZoneAndAct(zone, act);

        SonicConfigurationService configService = GameServices.configuration();
        GameplayTeamBootstrap.BootstrappedTeam team =
                GameplayTeamBootstrap.registerActiveTeam(
                        module, GameServices.sprites(), configService);
        GameServices.camera().setFocusedSprite(team.mainSprite());
        GameServices.camera().updatePosition(true);

        return loop;
    }

    @Override
    public void close() {
        if (rom != null) {
            try {
                rom.close();
            } catch (Exception ignored) {
                // best-effort teardown
            }
            rom = null;
        }
        if (window != NULL) {
            glfwDestroyWindow(window);
            window = NULL;
        }
        glfwTerminate();
    }
}
