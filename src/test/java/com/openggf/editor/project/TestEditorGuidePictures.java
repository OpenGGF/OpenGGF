package com.openggf.editor.project;

import com.openggf.Engine;
import com.openggf.ContentImportScreen;
import com.openggf.ContentImportScreenRenderer;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.editor.EditorFocusRegion;
import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.editor.LevelEditorController;
import com.openggf.editor.render.EditorOverlayRenderer;
import com.openggf.game.GameId;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.EditorModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Block;
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
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.glUseProgram;

/**
 * Refreshes the level-editor guide's real ROM-backed examples without a desktop.
 * Origin: the October 2026 editor pictures and shared-import task.
 * Inputs: the verified S2 ROM property, surfaceless EGL, and the explicit guidePictures flag.
 * Outputs: six 2x integer-scaled PNGs under target/editor-guide, for visual review before copying.
 */
@Isolated
class TestEditorGuidePictures {
    private static final int WIDTH = 426;
    private static final int HEIGHT = 224;

    @Test
    void captureCurrentEditorForTheGuide() throws Exception {
        assumeTrue(Boolean.getBoolean("openggf.editor.guidePictures"), "Explicit documentation picture refresh only");
        assumeTrue(PresentationOffscreenTestContext.requested(), "Guide pictures require display-free surfaceless EGL");
        try (var fixture = TestEditorProjectLifecycle.Fixture.open(GameId.S2)) {
            var config = SonicConfigurationService.getInstance();
            config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, WIDTH);
            config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, HEIGHT);
            try (var context = PresentationOffscreenTestContext.open(WIDTH, HEIGHT);
                 var menus = new EditorProjectMenuRenderer();
                 var imports = new ContentImportScreenRenderer()) {
                GraphicsManager graphics = GameServices.graphics();
                boolean previousBatching = graphics.isBatchingEnabled();
                int previousProjectionWidth = graphics.getProjectionWidth();
                int previousViewportX = graphics.getViewportX(), previousViewportY = graphics.getViewportY();
                int previousViewportWidth = graphics.getViewportWidth(), previousViewportHeight = graphics.getViewportHeight();
                float[] previousProjection = graphics.getProjectionMatrixBuffer();
                try {
                    graphics.setHeadlessMode(false);
                    graphics.init("shaders/shader_the_hedgehog.glsl");
                    assertTrue(graphics.isGlInitialized());
                    graphics.setBatchingEnabled(false);
                    graphics.setViewport(0, 0, WIDTH, HEIGHT);
                    graphics.setProjectionWidth(WIDTH);
                    float[] projection = new Matrix4f().ortho2D(0, WIDTH, 0, HEIGHT).get(new float[16]);
                    graphics.setProjectionMatrixBuffer(projection);

                    var engine = new Engine();
                    engine.setInputHandler(fixture.input);
                    engine.getGameLoop().setGameMode(GameMode.LEVEL);
                    var workspace = engine.getEditorProjectWorkspace();

                    // Use the same chooser and form a creator opens; do not imitate its labels.
                    workspace.openMenu();
                    TestEditorProjectLifecycle.choose(workspace, fixture.input, 0);
                    TestEditorProjectLifecycle.choose(workspace, fixture.input, 1);
                    TestEditorProjectLifecycle.choose(workspace, fixture.input, 0);
                    TestEditorProjectLifecycle.choose(workspace, fixture.input, 0);
                    assertEquals("New blank level", workspace.menu().title());
                    workspace.menu().setValue("name", "Emerald Practice");
                    workspace.menu().setValue("width", "8");
                    workspace.menu().setValue("height", "4");
                    picture("new-project", () -> menus.render(workspace.menu(), projection, WIDTH));
                    workspace.menu().submit();
                    assertNull(workspace.menu().error());

                    // These pictures exercise the same 426px offscreen layout as presentation
                    // QA. Derived display-aspect values mask raw setConfigValue writes; keep
                    // the picture host's explicit dimensions above that derived overlay.
                    config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, WIDTH);
                    config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, HEIGHT);
                    graphics.setViewport(0, 0, WIDTH, HEIGHT);
                    graphics.setProjectionWidth(WIDTH);
                    graphics.applyResolvedDisplayWidth(WIDTH);
                    graphics.setProjectionMatrixBuffer(projection);

                    // Blank creation keeps only reference-closed native definitions. A strict
                    // whole-stock-layout clone can require art that its current PLC never loaded.
                    Level level = workspace.document().level();
                    LevelEditorController controller = engine.getLevelEditorController();
                    int floor = TestEditorProjectLifecycle.authorCourse(workspace.document(), controller, GameId.S2);
                    // Illustrate normal creator placement with the available native ground
                    // composition, rather than the lifecycle test's single-pattern physics floor.
                    int[] nativeStart = fixture.module.getZoneRegistry().getStartPosition(0, 0);
                    int groundBlock = fixture.library.getMap().getValue(0,
                            nativeStart[0] / level.getBlockPixelSize(),
                            (nativeStart[1] + 32) / level.getBlockPixelSize()) & 0xFF;
                    visibleChunkCell(level, level.getBlock(groundBlock));
                    for (int x = 0; x < level.getMap().getWidth(); x++) {
                        controller.placeBlock(0, x, floor / level.getBlockPixelSize(), groundBlock);
                    }
                    int cellX = 2;
                    int cellY = floor / level.getBlockPixelSize();
                    int block = level.getMap().getValue(0, cellX, cellY) & 0xFF;
                    controller.setWorldCursor(new EditorCursorState(
                            cellX * level.getBlockPixelSize() + 64, cellY * level.getBlockPixelSize() + 64));
                    controller.selectBlock(block);
                    controller.focusRegion(EditorFocusRegion.BLOCK_PANE);
                    controller.libraryBrowser().selectCursor(0);
                    controller.setStatus("Choose a block, focus the world, and place it. Enter edits its composition.");
                    var editor = SessionManager.getCurrentEditorMode();
                    assertNotNull(editor);
                    assertTrue(editor.isEditorRuntimeReady());
                    var camera = editor.getCamera();
                    camera.refreshViewportDimensions(config);
                    engine.syncEditorState();
                    camera.setX((short) 0);
                    camera.setY((short) 0);
                    camera.captureRenderCopy();
                    assertEquals(WIDTH, camera.getWidth());
                    var overlay = new EditorOverlayRenderer(controller, graphics);
                    picture("world", () -> renderEditor(graphics, overlay, editor));

                    controller.descend();
                    assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());
                    int[] visibleCell = visibleChunkCell(level, level.getBlock(block));
                    controller.selectActiveCell(visibleCell[0], visibleCell[1]);
                    int chunk = level.getBlock(block).getChunkDesc(visibleCell[0], visibleCell[1]).getChunkIndex();
                    controller.focusRegion(EditorFocusRegion.CHUNK_PANE);
                    controller.libraryBrowser().selectCursor(chunk);
                    controller.selectLibraryEntry();
                    controller.setStatus("The right pane edits this block. Choose a 16x16 chunk from the library.");
                    picture("block", () -> renderEditor(graphics, overlay, editor));

                    controller.descend();
                    assertEquals(EditorHierarchyDepth.CHUNK, controller.depth());
                    controller.focusRegion(EditorFocusRegion.PATTERN_PANE);
                    int pattern = level.getChunk(chunk).getPatternDesc(0, 0).getPatternIndex();
                    controller.libraryBrowser().selectCursor(pattern);
                    controller.selectLibraryEntry();
                    controller.setStatus("Each chunk contains four 8x8 patterns. G changes the palette; T toggles priority.");
                    picture("chunk", () -> renderEditor(graphics, overlay, editor));

                    workspace.properties();
                    assertEquals("Level properties", workspace.menu().title());
                    picture("properties", () -> menus.render(workspace.menu(), projection, WIDTH));
                    workspace.menu().close();

                    var importField = Engine.class.getDeclaredField("contentImportScreen");
                    importField.setAccessible(true);
                    var importScreen = (ContentImportScreen) importField.get(engine);
                    try {
                        importScreen.open();
                        picture("import-content", () -> imports.render(importScreen, projection, WIDTH));
                    } finally { importScreen.close(); }
                    engine.setInputHandler(null);
                } finally {
                    graphics.cleanup();
                    graphics.setHeadlessMode(true);
                    graphics.setBatchingEnabled(previousBatching);
                    // cleanup/resetAll do not clear viewport/projection overrides. Do not let
                    // this optional 426px capture change subsequent native-width pointer tests.
                    graphics.setProjectionWidth(previousProjectionWidth);
                    graphics.setViewport(previousViewportX, previousViewportY,
                            previousViewportWidth, previousViewportHeight);
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

    private static void renderEditor(GraphicsManager graphics, EditorOverlayRenderer overlay, EditorModeContext editor) {
        // Match Engine.drawEditor's world pass followed by screen-space chrome.
        editor.getLevelManager().processDirtyRegions();
        editor.getLevelManager().drawWithSpritePriority(editor.getSpriteManager());
        overlay.renderWorldSpaceOverlay();
        graphics.flush();
        prepareScreenSpace();
        graphics.executeCapturedCommands(overlay::renderScreenSpaceOverlay, 0, 0, WIDTH, HEIGHT);
    }

    private static int[] visibleChunkCell(Level level, Block block) {
        int mostVisible = -1;
        int[] cell = {0, 0};
        for (int cy = 0; cy < block.getGridSide(); cy++) for (int cx = 0; cx < block.getGridSide(); cx++) {
            var chunk = level.getChunk(block.getChunkDesc(cx, cy).getChunkIndex());
            int visible = 0;
            for (int py = 0; py < 2; py++) for (int px = 0; px < 2; px++) {
                var pattern = level.getPattern(chunk.getPatternDesc(px, py).getPatternIndex());
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                    if (pattern.getPixel(x, y) != 0) visible++;
                }
            }
            if (visible > mostVisible) { mostVisible = visible; cell = new int[] {cx, cy}; }
        }
        assertTrue(mostVisible > 0, "The focused example must contain visible ROM art");
        return cell;
    }

    private static void prepareScreenSpace() {
        glActiveTexture(GL_TEXTURE0);
        glUseProgram(0);
        glDisable(GL_DEPTH_TEST);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glViewport(0, 0, WIDTH, HEIGHT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void picture(String name, Runnable render) throws Exception {
        prepareScreenSpace();
        glClearColor(0.01f, 0.02f, 0.04f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        render.run();
        glFinish();
        ByteBuffer pixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * 4);
        try {
            glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            var image = new BufferedImage(WIDTH * 2, HEIGHT * 2, BufferedImage.TYPE_INT_RGB);
            var colors = new HashSet<Integer>();
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
                int offset = ((HEIGHT - 1 - y) * WIDTH + x) * 4;
                int rgb = (pixels.get(offset) & 255) << 16
                        | (pixels.get(offset + 1) & 255) << 8 | pixels.get(offset + 2) & 255;
                colors.add(rgb);
                // Exact integer scaling keeps the native pixel-art examples readable in the guide.
                for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                    image.setRGB(x * 2 + dx, y * 2 + dy, rgb);
                }
            }
            assertTrue(colors.size() > 6, "Guide picture must contain rendered terrain or menu chrome and text: " + name);
            Path output = Path.of("target", "editor-guide", name + ".png");
            Files.createDirectories(output.getParent());
            assertTrue(ImageIO.write(image, "png", output.toFile()));
            assertEquals(GL_NO_ERROR, glGetError(), "Editor guide capture must leave valid GL state: " + name);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }
}
