package com.openggf.mods.scene.host;

import com.openggf.control.InputActionMasks;
import com.openggf.audio.StreamedMusicPort;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.MenuRepeat;
import com.openggf.control.MouseWheel;
import com.openggf.control.PlayerInputState;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.OwnedSceneFactory;
import com.openggf.mods.scene.DebuggableScene;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneArt;
import com.openggf.mods.scene.SceneAudio;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneImage;
import com.openggf.control.PhysicalInput;
import com.openggf.mods.scene.SceneMusic;
import com.openggf.mods.scene.SceneNetwork;
import com.openggf.mods.scene.SceneMouse;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneStorage;
import com.openggf.mods.scene.host.music.ManagedSceneMusic;
import com.openggf.mods.scene.host.music.SceneMusicFactory;
import com.openggf.mods.scene.host.music.LiveSceneMusic;
import com.openggf.game.GameId;
import com.openggf.game.BuiltInRomDetectors;
import com.openggf.mods.scene.host.network.ManagedSceneNetwork;
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
        try {
            scene = factory.create();
            scene.enter(context);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    /** One 60 Hz tick. The input handler's frame edges are advanced by the caller. */
    public void update(InputHandler input) {
        if (scene == null || exiting) {
            return;
        }
        try {
            context.beginTick(input);
            scene.update(context);
            context.ticks++;
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
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
        try {
            scene.draw(context, canvas);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
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

    /**
     * Sends a debug command to the open scene if it implements {@link DebuggableScene} (tools
     * and tests); false when no scene is open, it has no debug entry point, or it did not
     * understand the command.
     */
    public boolean debugJump(String command) {
        try {
            return scene != null && OwnedSceneFactory.debugJump(scene, command);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    /** The open scene as the host holds it (inside its fault-boundary wrapper), or null; for tests. */
    ModScene openScene() {
        return scene;
    }

    /** Draw calls recorded by the last {@link #draw} (for tests and diagnostics). */
    List<SceneDrawOp> lastFrame() {
        return lastFrame;
    }

    /** Immutable recording for engine diagnostics and the separately distributed creator testkit. */
    public List<SceneDrawOp> recordedFrame() {
        return List.copyOf(lastFrame);
    }

    /** Closes the open scene: calls {@link ModScene#exit} and releases its textures. */
    public void close() {
        ModScene closing = scene;
        Context ctx = context;
        scene = null;
        context = null;
        lastFrame = List.of();
        // Retire networking before creator exit code runs; even an exit callback
        // that faults or retains its context cannot reopen the departing visit.
        if (ctx != null) ctx.network.close();
        try {
            if (closing != null) {
                closing.exit(ctx);
            }
        } catch (ModFaultBoundary.CallbackAborted aborted) {
            LOG.log(Level.WARNING, "Mod scene failed while closing", aborted);
        } finally {
            if (ctx != null) ctx.closeResources();
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
        if (exiting) {
            return;
        }
        exiting = true;
        if (context != null) context.network.close();
        if (action != null) action.run();
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
        private PhysicalInput physical = PhysicalInput.neutral();
        private ManagedSceneMusic music;
        private LiveSceneMusic liveMusic;
        private final ManagedSceneNetwork network = new ManagedSceneNetwork();
        private final MenuRepeat repeat = new MenuRepeat();
        private int heldButtons;
        private int pressedButtons;
        private int repeatedButtons;
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

                @Override
                public List<String> availableGames() {
                    return services != null && services.romLibrary() != null
                            ? services.romLibrary().availableGames() : SceneArt.super.availableGames();
                }

                @Override
                public SceneRomArt rom(String gameId) {
                    return services != null && services.romLibrary() != null
                            ? services.romLibrary().rom(gameId) : SceneArt.super.rom(gameId);
                }
            };
            this.audio = new SceneAudio() {
                @Override
                public void playMusic(int musicId) {
                    stopLiveMusic();
                    if (services != null && services.audio() != null) {
                        services.audio().playMusic(musicId);
                    }
                }

                @Override
                public boolean playMusic(String gameId, int musicId) {
                    if (services == null || services.audio() == null || services.romLibrary() == null) return false;
                    var game = GameId.fromCode(gameId);
                    var rom = services.romLibrary().sourceRom(game.code());
                    if (rom == null) return false;
                    var profile = BuiltInRomDetectors.forGame(game).createModule().getAudioProfile();
                    var next = new LiveSceneMusic(game.code(), musicId,
                            profile, rom, services.audio().outputSampleRate());
                    stopLiveMusic();
                    if (music != null) { music.close(); music = null; }
                    services.audio().stopMusic();
                    liveMusic = next;
                    services.audio().setScenePcmSource(next);
                    return true;
                }

                @Override
                public void playSfx(int sfxId) {
                    if (services != null && services.audio() != null) {
                        services.audio().playSfx(sfxId);
                    }
                }

                @Override
                public boolean playSfx(String localName) {
                    com.openggf.game.ModKeySyntax.requireOwnedKey(owner, localName);
                    var ref = new StreamedMusicPort.SfxRef(owner, localName);
                    return context == Context.this && services != null && services.audio() != null
                            && services.audio().playNamespacedSfx(ref);
                }

                @Override
                public void fadeOutMusic() {
                    if (liveMusic != null) liveMusic.fadeOut();
                    if (services != null && services.audio() != null) {
                        services.audio().fadeOutMusic();
                    }
                }

                @Override
                public void stopMusic() {
                    stopLiveMusic();
                    if (services != null && services.audio() != null) {
                        services.audio().stopMusic();
                    }
                }
            };
            Path root = services == null || services.storageRoot() == null
                    ? Path.of("saves") : services.storageRoot();
            this.storage = ModStorageFactory.forOwner(root, owner);
        }

        void beginTick(InputHandler handler) {
            input = handler;
            physical = handler == null ? PhysicalInput.neutral() : handler.capturePhysicalInput();
            LogicalInputSnapshot raw = handler == null ? LogicalInputSnapshot.neutral() : handler.logical();
            heldButtons = buttons(raw.player1(), true);
            pressedButtons = buttons(raw.player1(), false);
            repeatedButtons = 0;
            for (int bit = 1; bit <= SceneButtons.START; bit <<= 1) {
                if (repeat.pulse(bit, ticks, (heldButtons & bit) != 0, (pressedButtons & bit) != 0)) {
                    repeatedButtons |= bit;
                }
            }
            // Scenes follow the Genesis convention: A, C or Start confirms and B goes back.
            logical = raw.withMenuPolicy((pressedButtons & (SceneButtons.A | SceneButtons.C | SceneButtons.START)) != 0,
                    (pressedButtons & SceneButtons.B) != 0);
            mouse = readMouse(handler);
        }

        private void stopLiveMusic() {
            if (liveMusic != null) {
                liveMusic.close();
                liveMusic = null;
                services.audio().setScenePcmSource(null);
            }
        }

        void closeResources() {
            stopLiveMusic();
            network.close();
            try {
                if (music != null) music.close();
            } finally {
                if (services != null && services.romLibrary() != null) services.romLibrary().close();
            }
        }

        @Override public PhysicalInput physicalInput() { return physical; }

        @Override public SceneNetwork network() { return network; }

        @Override public SceneMusic music() {
            stopLiveMusic();
            if (music == null) {
                if (services == null || services.audio() == null || services.romLibrary() == null)
                    throw new UnsupportedOperationException("Finite scene music unavailable");
                music = SceneMusicFactory.create(services.audio(), services.romLibrary()::sourceRom);
            }
            return music;
        }

        /** Player 1's held or pressed state as {@link SceneButtons} bits (the pad's SACBRLDU byte). */
        private static int buttons(PlayerInputState pad, boolean held) {
            int directions = (held ? pad.heldMask() : pad.pressedMask()) & SceneButtons.DIRECTIONS;
            int actions = held ? pad.actionHeldMask() : pad.actionPressedMask();
            boolean start = held ? pad.startHeld() : pad.startPressed();
            return directions
                    | ((actions & InputActionMasks.ACTION_A) != 0 ? SceneButtons.A : 0)
                    | ((actions & InputActionMasks.ACTION_B) != 0 ? SceneButtons.B : 0)
                    | ((actions & InputActionMasks.ACTION_C) != 0 ? SceneButtons.C : 0)
                    | (start ? SceneButtons.START : 0);
        }

        private SceneMouse readMouse(InputHandler handler) {
            if (handler == null || services == null || services.mouse() == null) {
                return SceneMouse.none();
            }
            int wheel = MouseWheel.of(handler).takeNotches();
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
            SceneMouse m = SceneMouses.create(p[0], p[1], p[2] != 0, moved, left, leftPressed, !left && previousLeft,
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
        public boolean buttonDown(int buttons) {
            return (heldButtons & buttons) != 0;
        }

        @Override
        public boolean buttonPressed(int buttons) {
            return (pressedButtons & buttons) != 0;
        }

        @Override
        public boolean buttonRepeated(int buttons) {
            return (repeatedButtons & buttons) != 0;
        }

        @Override
        public LogicalInputSnapshot input() {
            return logical;
        }

        @Override
        public boolean keyDown(int key) {
            return input != null && input.isKeyDown(key);
        }

        @Override
        public boolean keyPressed(int key) {
            return input != null && input.isKeyPressed(key);
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
