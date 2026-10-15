package com.openggf.editor.project;

import com.openggf.editor.EditorFocusRegion;
import com.openggf.editor.LevelEditorController;
import com.openggf.editor.render.EditorOverlayRenderer;
import com.openggf.game.GameId;
import com.openggf.game.GameServices;
import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.game.MenuTextEditor;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.MutableLevel;
import com.openggf.level.ChunkDesc;
import com.openggf.level.PatternDesc;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.glUseProgram;

/** Display-free visual QA; pictures are temporary worktree outputs, never desktop captures. */
@Isolated
class TestEditorProjectPresentation {
    @Test
    void projectFormsAndEditingHierarchyRenderAtNativeAndWideWidths() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "Run with surfaceless EGL for pixel QA");
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        try (var context = PresentationOffscreenTestContext.open(426, 224);
             var menus = new EditorProjectMenuRenderer()) {
            // Engine and primitive commands share this owner; a private manager leaves chrome on an uninitialized host.
            GraphicsManager graphics = GameServices.graphics();
            try {
                graphics.setHeadlessMode(false);
                graphics.init("shaders/shader_the_hedgehog.glsl");
                assertTrue(graphics.isGlInitialized(), "Primitive and tile commands need the same initialized host as Engine");
                graphics.setBatchingEnabled(false);
                var level = MutableLevel.snapshot(new TestEditorProjects.Fixture(GameId.S2));
                for (int p = 0; p < level.getPaletteCount(); p++) {
                    for (int c = 1; c < 16; c++) {
                        var color = level.getPalette(p).getColor(c);
                        color.r = (byte) (c * 16); color.g = (byte) (255 - c * 12); color.b = (byte) 200;
                    }
                    graphics.cachePaletteTexture(level.getPalette(p), p);
                }
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                    level.getPattern(1).setPixel(x,y,(byte) (1 + (x + 2 * y) % 15));
                }
                // Project persistence's sparse fixture has only one visible pattern. A world thumbnail
                // reduces that entire 128px block to 16px, leaving at most one colored sample.
                // Dense composition provides enough real texture samples to test the strict pixel regions.
                for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
                    level.getChunk(1).setPatternDesc(x, y,
                            new PatternDesc(1 | (x << 11) | (y << 12) | ((x + 2 * y) << 13)));
                }
                for (int y = 0; y < level.getBlock(1).getGridSide(); y++) {
                    for (int x = 0; x < level.getBlock(1).getGridSide(); x++) {
                        level.getBlock(1).setChunkDesc(x, y,
                                new ChunkDesc(1 | ((x & 1) << 10) | ((y & 1) << 11)));
                    }
                }
                level.setBlockInMap(0,0,0,1);
                for (int p = 0; p < level.getPatternCount(); p++) graphics.cachePatternTexture(level.getPattern(p), p);
                for (int width : new int[] {320, 426}) {
                    float[] projection = new Matrix4f().ortho(0, width, 0, 224, -1, 1).get(new float[16]);
                    graphics.setViewport(0, 0, width, 224); graphics.setProjectionWidth(width);
                    graphics.setProjectionMatrixBuffer(projection);
                    var controller = new LevelEditorController(); controller.attachLevel(level);
                    controller.setStatus("Saved project. F5 playtests its authored start.");
                    controller.selectBlock(1); controller.focusRegion(EditorFocusRegion.BLOCK_PANE);
                    var overlay = new EditorOverlayRenderer(controller, graphics);
                    picture(width, "world", () -> graphics.executeCapturedCommands(overlay::renderScreenSpaceOverlay, 0, 0, width, 224));
                    controller.descend(); controller.selectChunk(1); controller.focusRegion(EditorFocusRegion.CHUNK_PANE);
                    assertEquals(EditorHierarchyDepth.BLOCK, controller.depth());
                    picture(width, "block", () -> graphics.executeCapturedCommands(overlay::renderScreenSpaceOverlay, 0, 0, width, 224));
                    controller.descend(); controller.focusRegion(EditorFocusRegion.PATTERN_PANE);
                    assertEquals(EditorHierarchyDepth.CHUNK, controller.depth());
                    controller.selectPattern(1); controller.toggleDescriptorHFlip(); controller.togglePatternPriority();
                    picture(width, "pattern", () -> graphics.executeCapturedCommands(overlay::renderScreenSpaceOverlay, 0, 0, width, 224));
                    var menu = new EditorProjectMenu();
                    menu.showForm("Level properties", List.of(
                            new EditorProjectMenu.Field("name", "Name", "A playable authored level", MenuTextEditor.Mode.TEXT),
                            new EditorProjectMenu.Field("start", "Start X", "64", MenuTextEditor.Mode.INTEGER),
                            new EditorProjectMenu.Field("path", "Project file", "projects/my-level.ogfproject", MenuTextEditor.Mode.PATH)),
                            "Apply properties", ignored -> {});
                    menu.setSubtitle("Changes apply after validation.");
                    picture(width, "project", () -> menus.render(menu, projection, width));
                    menu.report(new IllegalArgumentException("Missing dependency retained; enable owner:object to playtest."));
                    picture(width, "error", () -> menus.render(menu, projection, width));
                }
            } finally { graphics.cleanup(); graphics.setHeadlessMode(true); }
        } finally { TestEnvironment.resetAll(); }
    }

    private static void picture(int width, String name, Runnable render) throws Exception {
        // Same modern screen-space setup as Engine.prepareOverlayState; no fixed-function matrices are involved.
        glActiveTexture(GL_TEXTURE0); glUseProgram(0);
        glDisable(GL_DEPTH_TEST); glBindTexture(GL_TEXTURE_2D,0);
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D,0); glActiveTexture(GL_TEXTURE0);
        glViewport(0, 0, width, 224);
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glClearColor(0.01f, 0.02f, 0.04f, 1); glClear(GL_COLOR_BUFFER_BIT);
        render.run(); glFinish();
        ByteBuffer pixels = MemoryUtil.memAlloc(width * 224 * 4);
        try {
            glReadPixels(0, 0, width, 224, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            BufferedImage image = new BufferedImage(width, 224, BufferedImage.TYPE_INT_ARGB);
            int visible = 0, libraryChrome = 0, libraryArt = 0, focusedArt = 0;
            for (int y = 0; y < 224; y++) for (int x = 0; x < width; x++) {
                int i = ((223 - y) * width + x) * 4;
                int r = pixels.get(i) & 255, g = pixels.get(i + 1) & 255, b = pixels.get(i + 2) & 255;
                if (r > 30 || g > 30 || b > 30) visible++;
                if (x >= 12 && x <= 152 && y >= 40 && y <= 194 && b > 180 && g > 120 && r < 150) libraryChrome++;
                if (x >= 28 && x < 136 && y >= 82 && y < 190 && b >= 190 && b <= 210 && Math.abs(r - g) > 20) libraryArt++;
                if (x >= width - 148 && x < width - 4 && y >= 40 && y < 194
                        && b >= 190 && b <= 210 && Math.abs(r - g) > 20) focusedArt++;
                image.setRGB(x, y, 0xff000000 | r << 16 | g << 8 | b);
            }
            Path output = Path.of("target", "editor-qa", width + "-" + name + ".png");
            Files.createDirectories(output.getParent()); ImageIO.write(image, "png", output.toFile());
            String counts = " width=" + width + " visible=" + visible + " libraryChrome=" + libraryChrome
                    + " libraryArt=" + libraryArt + " focusedArt=" + focusedArt;
            assertTrue(visible > 1000, "Editor chrome and glyphs must reach the framebuffer: " + name + counts);
            if (List.of("world", "block", "pattern").contains(name)) {
                assertTrue(libraryChrome > 100, "Shared-owner library chrome must render: " + name + counts);
                assertTrue(libraryArt > 30, "Library thumbnails must contain actual cached art: " + name + counts);
                if (!name.equals("world")) assertTrue(focusedArt > 30, "Focused composition must render cached art: " + name + counts);
            }
            assertEquals(GL_NO_ERROR, glGetError(), "Editor overlay must not rely on unavailable GL state" + counts);
        } finally { MemoryUtil.memFree(pixels); }
    }
}
