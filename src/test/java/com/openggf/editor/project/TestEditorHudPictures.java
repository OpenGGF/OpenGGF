package com.openggf.editor.project;

import com.openggf.Engine;
import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.GamepadStateSource;
import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.data.RomManager;
import com.openggf.editor.EditorCommandPalette;
import com.openggf.editor.EditorFocusRegion;
import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.editor.EditorInputHandler;
import com.openggf.editor.EditorSpawnEditMode;
import com.openggf.editor.EditorWindowChrome;
import com.openggf.editor.LevelEditorController;
import com.openggf.editor.render.EditorOverlayRenderer;
import com.openggf.editor.hud.EditorHudRuntime;
import com.openggf.game.GameId;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.EditorModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.glUseProgram;

/**
 * Drives the controller-first editor HUD with a synthetic gamepad over real ROM-backed editor
 * frames, without a desktop, and writes full-window PNGs for review. Origin:
 * feature/ai-editor-controller-ui, 2026-10-15. Inputs: the S2 ROM property, surfaceless EGL and
 * -Dopenggf.editor.hudPictures=true. Outputs: target/editor-hud/*.png.
 */
@Isolated
class TestEditorHudPictures {
    private static final int LOGICAL_WIDTH = 426;
    private static final int LOGICAL_HEIGHT = 224;
    private static final int WINDOW_WIDTH = Integer.getInteger("openggf.editor.hudWidth", 1600);
    private static final int WINDOW_HEIGHT = Integer.getInteger("openggf.editor.hudHeight", 900);
    private static final int BUTTON_A = 0, BUTTON_B = 1, BUTTON_Y = 3, BUTTON_LB = 4, BUTTON_RB = 5,
            BUTTON_BACK = 6, BUTTON_START = 7, DPAD_RIGHT = 12, DPAD_DOWN = 13;
    private static final int AXIS_RIGHT_TRIGGER = 5;

    private final boolean[] buttons = new boolean[15];
    private final float[] axes = {0, 0, 0, 0, -1, -1};
    private String padName = "Xbox Wireless Controller";

    @Test
    void captureControllerHud() throws Exception {
        assumeTrue(Boolean.getBoolean("openggf.editor.hudPictures"), "Explicit HUD picture refresh only");
        assumeTrue(PresentationOffscreenTestContext.requested(), "HUD pictures require display-free surfaceless EGL");
        try (var fixture = TestEditorProjectLifecycle.Fixture.open(GameId.S2)) {
            var config = SonicConfigurationService.getInstance();
            config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, LOGICAL_WIDTH);
            config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, LOGICAL_HEIGHT);
            try (var context = PresentationOffscreenTestContext.open(WINDOW_WIDTH, WINDOW_HEIGHT);
                 var hud = new EditorHudRuntime()) {
                GraphicsManager graphics = GameServices.graphics();
                boolean previousBatching = graphics.isBatchingEnabled();
                int previousProjectionWidth = graphics.getProjectionWidth();
                int previousViewportX = graphics.getViewportX(), previousViewportY = graphics.getViewportY();
                int previousViewportWidth = graphics.getViewportWidth(), previousViewportHeight = graphics.getViewportHeight();
                float[] previousProjection = graphics.getProjectionMatrixBuffer();
                try {
                    graphics.setHeadlessMode(false);
                    graphics.init("shaders/shader_the_hedgehog.glsl");
                    graphics.setBatchingEnabled(false);
                    float[] projection = new Matrix4f().ortho2D(0, LOGICAL_WIDTH, 0, LOGICAL_HEIGHT).get(new float[16]);

                    var engine = new Engine();
                    engine.setInputHandler(fixture.input);
                    engine.getGameLoop().setGameMode(GameMode.LEVEL);
                    var workspace = engine.getEditorProjectWorkspace();
                    workspace.openMenu();
                    TestEditorProjectLifecycle.choose(workspace, fixture.input, 0);
                    TestEditorProjectLifecycle.choose(workspace, fixture.input, 1);
                    TestEditorProjectLifecycle.choose(workspace, fixture.input, 0);
                    TestEditorProjectLifecycle.choose(workspace, fixture.input, 0);
                    workspace.menu().setValue("name", "Emerald Practice");
                    workspace.menu().setValue("width", "8");
                    workspace.menu().setValue("height", "4");
                    workspace.menu().submit();

                    config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, LOGICAL_WIDTH);
                    config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, LOGICAL_HEIGHT);
                    graphics.setProjectionWidth(LOGICAL_WIDTH);
                    graphics.applyResolvedDisplayWidth(LOGICAL_WIDTH);
                    graphics.setProjectionMatrixBuffer(projection);

                    Level level = workspace.document().level();
                    LevelEditorController controller = engine.getLevelEditorController();
                    int floor = TestEditorProjectLifecycle.authorCourse(workspace.document(), controller, GameId.S2);
                    int[] nativeStart = fixture.module.getZoneRegistry().getStartPosition(0, 0);
                    int size = level.getBlockPixelSize();
                    int groundBlock = fixture.library.getMap().getValue(0, nativeStart[0] / size,
                            (nativeStart[1] + 32) / size) & 0xFF;
                    int cellY = floor / size;
                    for (int x = 0; x < level.getMap().getWidth(); x++) controller.placeBlock(0, x, cellY, groundBlock);
                    controller.setWorldCursor(new EditorCursorState(2 * size + 64, cellY * size + 64));
                    controller.selectBlock(groundBlock);
                    controller.focusRegion(EditorFocusRegion.WORLD_CANVAS);

                    EditorModeContext editor = SessionManager.getCurrentEditorMode();
                    var camera = editor.getCamera();
                    camera.refreshViewportDimensions(config);
                    engine.syncEditorState();
                    camera.setX((short) 0);
                    camera.setY((short) 0);
                    camera.captureRenderCopy();

                    var dispatcher = new EditorInputHandler(controller);
                    hud.init(new EditorWindowChrome.Host() {
                        public LevelEditorController controller() { return controller; }
                        public void dispatch(EditorInputHandler.Action action) { dispatcher.handleAction(action); }
                        public List<EditorCommandPalette.HostCommand> commands() {
                            return EditorCommandPalette.forController(controller).extraCommands();
                        }
                    }, 1f);
                    assertTrue(hud.isAvailable(), "The HUD must initialise on the offscreen GL context");
                    assertTrue(hud.ownsGamepads());
                    dispatcher.setFrontEnd(new EditorInputHandler.FrontEnd() {
                        public boolean update(InputHandler input) { return hud.update(input); }
                        public void finishPendingEdits() { hud.finishPendingEdits(); }
                    });
                    GamepadStateSource source = () -> List.of(GamepadStateSource.DeviceState.connected(
                            0, padName, buttons, axes));
                    var pads = new InputHandler(InputBindingFactory.standaloneSupplier(), source);
                    // As in the engine: while the HUD owns the editor, pad mappings see a neutral pad.
                    InputHandlerInternalAccess.suppressGamepad(pads, true);
                    var overlay = new EditorOverlayRenderer(controller, graphics);
                    var shot = new Shot(graphics, hud, overlay, editor, projection, pads, dispatcher);

                    // A first pad press switches every prompt to pad glyphs; RB then LB returns to slot one.
                    shot.press(BUTTON_RB);
                    int secondSlot = controller.selectedBlockIndex();
                    assertNotEquals(groundBlock, secondSlot, "RB moves the brush to the next hotbar slot");
                    shot.press(BUTTON_LB);
                    assertEquals(groundBlock, controller.selectedBlockIndex(), "LB returns to the first slot");
                    controller.setStatus("Hold South and steer to paint a stroke");
                    shot.picture("world");

                    // D-pad steps whole blocks; a held South paints a stroke that undoes in one step.
                    int startX = controller.worldCursor().x() / size;
                    shot.press(BUTTON_RB);
                    int brush = controller.selectedBlockIndex();
                    buttons[BUTTON_A] = true;
                    shot.tick();
                    shot.press(DPAD_RIGHT);
                    shot.press(DPAD_RIGHT);
                    buttons[BUTTON_A] = false;
                    shot.tick();
                    for (int x = startX; x <= startX + 2; x++) {
                        assertEquals(brush, level.getMap().getValue(0, x, cellY) & 0xFF, "stroke paints cell " + x);
                    }
                    controller.undo();
                    for (int x = startX; x <= startX + 2; x++) {
                        assertEquals(groundBlock, level.getMap().getValue(0, x, cellY) & 0xFF, "one undo reverts the stroke");
                    }
                    controller.redo();
                    shot.picture("world-painted");

                    shot.press(BUTTON_Y);
                    shot.picture("library");
                    int libraryStart = controller.selectedBlockIndex();
                    shot.press(DPAD_RIGHT);
                    shot.press(DPAD_DOWN);
                    shot.picture("library-browse");
                    shot.press(BUTTON_A);
                    assertNotEquals(libraryStart, controller.selectedBlockIndex(), "South in the library chooses a block");

                    buttons[BUTTON_BACK] = true;
                    shot.tick();
                    axes[0] = .95f;
                    shot.tick();
                    shot.picture("wheel");
                    axes[0] = 0;
                    buttons[BUTTON_BACK] = false;
                    int layerBefore = controller.activeLayer();
                    shot.tick();
                    if (level.getMap().getLayerCount() > 1) {
                        assertNotEquals(layerBefore, controller.activeLayer(),
                                "Releasing View on the right-hand slot toggles the layer");
                        controller.toggleActiveLayer();
                    }

                    shot.press(BUTTON_START);
                    shot.picture("menu");
                    shot.press(BUTTON_B);

                    axes[AXIS_RIGHT_TRIGGER] = 1;
                    shot.tick();
                    axes[AXIS_RIGHT_TRIGGER] = -1;
                    shot.tick();
                    assertEquals(EditorHierarchyDepth.BLOCK, controller.depth(), "RT edits the block under the cursor");
                    shot.press(DPAD_RIGHT);
                    shot.press(DPAD_DOWN);
                    shot.picture("block");
                    shot.press(BUTTON_Y);
                    shot.picture("block-library");
                    shot.press(BUTTON_B);

                    axes[AXIS_RIGHT_TRIGGER] = 1;
                    shot.tick();
                    axes[AXIS_RIGHT_TRIGGER] = -1;
                    shot.tick();
                    assertEquals(EditorHierarchyDepth.CHUNK, controller.depth(), "RT again edits the chunk");
                    shot.press(DPAD_RIGHT);
                    shot.picture("chunk");
                    shot.press(BUTTON_B);
                    shot.press(BUTTON_B);
                    assertEquals(EditorHierarchyDepth.WORLD, controller.depth(), "East climbs back to the world");

                    if (controller.objectPalette() != null) {
                        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
                        shot.tick();
                        shot.picture("objects");
                        shot.press(BUTTON_Y);
                        shot.picture("objects-library");
                        shot.press(BUTTON_B);
                        controller.setSpawnEditMode(EditorSpawnEditMode.TILES);
                    }

                    padName = "DualSense Wireless Controller";
                    shot.press(BUTTON_LB);
                    shot.press(BUTTON_BACK);
                    shot.picture("wheel-playstation");
                    shot.press(BUTTON_B);

                    pads.handleKeyEvent(GLFW_KEY_LEFT, GLFW_PRESS);
                    shot.tick();
                    pads.handleKeyEvent(GLFW_KEY_LEFT, GLFW_RELEASE);
                    shot.picture("world-keyboard");
                    engine.setInputHandler(null);
                } finally {
                    graphics.cleanup();
                    graphics.setHeadlessMode(true);
                    graphics.setBatchingEnabled(previousBatching);
                    graphics.setProjectionWidth(previousProjectionWidth);
                    graphics.setViewport(previousViewportX, previousViewportY, previousViewportWidth, previousViewportHeight);
                    graphics.setProjectionMatrixBuffer(previousProjection);
                }
            }
        } finally {
            SessionManager.clear();
            RomManager.getInstance().setRom(null);
            Engine.clearGlobalInstance();
            TestEnvironment.resetAll();
            SonicConfigurationService.getInstance().resetToDefaults();
            GameModuleRegistry.reset();
        }
    }

    private final class Shot {
        private final GraphicsManager graphics;
        private final EditorHudRuntime hud;
        private final EditorOverlayRenderer overlay;
        private final EditorModeContext editor;
        private final float[] projection;
        private final InputHandler pads;
        private final EditorInputHandler dispatcher;

        Shot(GraphicsManager graphics, EditorHudRuntime hud, EditorOverlayRenderer overlay, EditorModeContext editor,
             float[] projection, InputHandler pads, EditorInputHandler dispatcher) {
            this.graphics = graphics;
            this.hud = hud;
            this.overlay = overlay;
            this.editor = editor;
            this.projection = projection;
            this.pads = pads;
            this.dispatcher = dispatcher;
        }

        /** One editor tick with the synthetic pad state, then one rendered frame. */
        void tick() {
            hud.setActive(true);
            pads.refreshLogicalSnapshot();
            dispatcher.update(pads);
            pads.update();
            frame();
        }

        void press(int button) {
            buttons[button] = true;
            tick();
            buttons[button] = false;
            tick();
        }

        void picture(String name) throws Exception {
            // Sheets scroll and the selection pulse settles over a few frames.
            for (int i = 0; i < 12; i++) frame();
            capture(name);
        }

        private void frame() {
            hud.setActive(true);
            int[] viewport = hud.gameViewport(WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT,
                    LOGICAL_WIDTH, LOGICAL_HEIGHT);
            glViewport(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
            glDisable(GL_SCISSOR_TEST);
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            graphics.setViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            graphics.setProjectionMatrixBuffer(projection);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            editor.getLevelManager().processDirtyRegions();
            editor.getLevelManager().drawWithSpritePriority(editor.getSpriteManager());
            overlay.renderWorldSpaceOverlay();
            graphics.flush();
            resetState();
            hud.render(WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT);
        }
    }

    private static void capture(String name) throws Exception {
        glFinish();
        ByteBuffer pixels = MemoryUtil.memAlloc(WINDOW_WIDTH * WINDOW_HEIGHT * 4);
        try {
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadPixels(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            var image = new BufferedImage(WINDOW_WIDTH, WINDOW_HEIGHT, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < WINDOW_HEIGHT; y++) for (int x = 0; x < WINDOW_WIDTH; x++) {
                int offset = ((WINDOW_HEIGHT - 1 - y) * WINDOW_WIDTH + x) * 4;
                image.setRGB(x, y, (pixels.get(offset) & 255) << 16 | (pixels.get(offset + 1) & 255) << 8
                        | pixels.get(offset + 2) & 255);
            }
            Path output = Path.of("target", "editor-hud", name + ".png");
            Files.createDirectories(output.getParent());
            assertTrue(ImageIO.write(image, "png", output.toFile()));
            assertEquals(GL_NO_ERROR, glGetError(), "HUD capture must leave valid GL state: " + name);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private static void resetState() {
        glActiveTexture(GL_TEXTURE0);
        glUseProgram(0);
        glDisable(GL_DEPTH_TEST);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
    }
}
