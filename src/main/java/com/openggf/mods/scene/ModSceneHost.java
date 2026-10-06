package com.openggf.mods.scene;

import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.OwnedSceneFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Runs the active mod scene for {@code GameMode.MOD_SCENE}: builds its context, forwards
 * ticks and draws, renders the recorded canvas, and tears everything down when the scene
 * closes. One host lives in the game loop; it holds at most one open scene. Engine-internal.
 */
public final class ModSceneHost {
    private static final Logger LOG = Logger.getLogger(ModSceneHost.class.getName());

    private final SceneRenderer renderer = new SceneRenderer();
    private SceneFontAtlas font;
    private ModScene scene;
    private Context context;
    private int width = 320;
    private int height = 224;
    private boolean exiting;
    private List<SceneDrawOp> lastFrame = List.of();

    /** True while a scene is open. */
    public boolean isOpen() {
        return scene != null;
    }

    /**
     * Opens a scene from {@code factory} (a registered startup scene, which runs every call
     * inside its owner's fault boundary); any previous scene is closed first.
     */
    public void open(OwnedSceneFactory factory, SceneServices services, int logicalWidth, int logicalHeight) {
        close();
        width = logicalWidth;
        height = logicalHeight;
        if (font == null) {
            font = SceneFontAtlas.load();
        }
        context = new Context(factory.ownerModId(), services);
        exiting = false;
        scene = factory.create();
        scene.enter(context);
    }

    /** One 60 Hz tick. The input handler's frame edges are advanced by the caller. */
    public void update(InputHandler input) {
        if (scene == null || exiting) {
            return;
        }
        context.beginTick(input);
        scene.update(context);
        context.ticks++;
    }

    /**
     * Draws the scene. {@code projection} is the engine's screen-space projection;
     * {@code viewport} the framebuffer rectangle {x, y, w, h}; pass null projection to
     * record without rendering (headless).
     */
    public void draw(float[] projection, int[] viewport) {
        if (scene == null || context == null) {
            return;
        }
        RecordingCanvas canvas = new RecordingCanvas(width, height, font);
        scene.draw(context, canvas);
        lastFrame = canvas.ops();
        if (projection == null || viewport == null) {
            return;
        }
        try {
            if (!renderer.initialized()) {
                renderer.init();
            }
            renderer.render(canvas.ops(), projection, width, height, viewport);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Scene renderer unavailable", e);
        }
    }

    /** Draw calls recorded by the last {@link #draw} (for tests and diagnostics). */
    List<SceneDrawOp> lastFrame() {
        return lastFrame;
    }

    /** Closes the open scene: calls {@link ModScene#exit} and releases its textures. */
    public void close() {
        ModScene closing = scene;
        Context ctx = context;
        scene = null;
        context = null;
        lastFrame = List.of();
        if (closing != null) {
            try {
                closing.exit(ctx);
            } catch (ModFaultBoundary.CallbackAborted aborted) {
                LOG.log(Level.WARNING, "Mod scene failed while closing", aborted);
            }
        }
        if (renderer.initialized()) {
            renderer.releaseTextures();
        }
    }

    /**
     * Engine shutdown: closes the open scene (so its {@link ModScene#exit} runs and can save)
     * and releases GL resources. Call while the GL context is still alive.
     */
    public void cleanup() {
        close();
        renderer.cleanup();
    }

    private void requestExit(Runnable action) {
        if (exiting || action == null) {
            return;
        }
        exiting = true;
        action.run();
    }

    /** The context handed to the scene for one visit. */
    private final class Context implements SceneContext {
        private final String owner;
        private final SceneServices services;
        private final SceneArt art;
        private final SceneAudio audio;
        private final SceneStorage storage;
        private long ticks;
        private InputHandler input;
        private LogicalInputSnapshot logical = LogicalInputSnapshot.neutral();
        private SceneMouse mouse = SceneMouse.none();
        private boolean previousLeft;
        private boolean previousRight;
        private int lastX = -1;
        private int lastY = -1;
        private boolean mouseLast;

        Context(String owner, SceneServices services) {
            this.owner = owner;
            this.services = services;
            this.art = new SceneArt() {
                @Override
                public SceneImage png(byte[] pngBytes) {
                    return ScenePng.decode(pngBytes);
                }

                @Override
                public SceneRomArt rom() {
                    return services == null ? null : services.romArt();
                }
            };
            this.audio = new SceneAudio() {
                @Override
                public void playMusic(int musicId) {
                    if (services != null && services.audio() != null) {
                        services.audio().playMusic(musicId);
                    }
                }

                @Override
                public void playSfx(int sfxId) {
                    if (services != null && services.audio() != null) {
                        services.audio().playSfx(sfxId);
                    }
                }

                @Override
                public void fadeOutMusic() {
                    if (services != null && services.audio() != null) {
                        services.audio().fadeOutMusic();
                    }
                }

                @Override
                public void stopMusic() {
                    if (services != null && services.audio() != null) {
                        services.audio().stopMusic();
                    }
                }
            };
            Path root = services == null || services.storageRoot() == null
                    ? Path.of("saves") : services.storageRoot();
            this.storage = new FileSceneStorage(root.resolve("mods").resolve(owner));
        }

        void beginTick(InputHandler handler) {
            input = handler;
            logical = handler == null ? LogicalInputSnapshot.neutral() : handler.logical();
            mouse = readMouse(handler);
        }

        private SceneMouse readMouse(InputHandler handler) {
            if (handler == null || services == null || services.mouse() == null) {
                return SceneMouse.none();
            }
            int wheel = handler.consumeScrollNotches();
            boolean left = handler.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            boolean right = handler.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            int[] p = handler.hasMouseInputSeen()
                    ? services.mouse().map(handler.getMouseX(), handler.getMouseY())
                    : new int[] {-1, -1, 0};
            boolean moved = handler.hasMouseInputSeen() && (p[0] != lastX || p[1] != lastY);
            boolean leftPressed = left && !previousLeft;
            boolean rightPressed = right && !previousRight;
            // The latest input decides: mouse activity sets it, a key or pad press clears it.
            if (moved || leftPressed || rightPressed || wheel != 0) {
                mouseLast = true;
            }
            if (handler.isAnyKeyJustPressed() || padPressed(logical)) {
                mouseLast = false;
            }
            SceneMouse m = new SceneMouse(p[0], p[1], p[2] != 0, moved, left, leftPressed, !left && previousLeft,
                    right, rightPressed, !right && previousRight, wheel, mouseLast);
            lastX = p[0];
            lastY = p[1];
            previousLeft = left;
            previousRight = right;
            return m;
        }

        private static boolean padPressed(LogicalInputSnapshot in) {
            return in.player1().pressedMask() != 0 || in.player1().actionPressedMask() != 0
                    || in.player1().startPressed();
        }

        @Override
        public String ownerModId() {
            return owner;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public long ticks() {
            return ticks;
        }

        @Override
        public LogicalInputSnapshot input() {
            return logical;
        }

        @Override
        public boolean keyDown(int glfwKey) {
            return input != null && input.isKeyDown(glfwKey);
        }

        @Override
        public boolean keyPressed(int glfwKey) {
            return input != null && input.isKeyPressed(glfwKey);
        }

        @Override
        public SceneMouse mouse() {
            return mouse;
        }

        @Override
        public SceneArt art() {
            return art;
        }

        @Override
        public SceneAudio audio() {
            return audio;
        }

        @Override
        public SceneStorage storage() {
            return storage;
        }

        @Override
        public void exitToGameTitle() {
            requestExit(services == null ? null : services.toGameTitle());
        }

        @Override
        public void exitToMasterTitle() {
            requestExit(services == null ? null : services.toMasterTitle());
        }
    }
}
