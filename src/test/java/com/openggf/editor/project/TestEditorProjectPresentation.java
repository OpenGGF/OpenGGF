package com.openggf.editor.project;

import com.openggf.game.GameServices;
import com.openggf.game.MenuTextEditor;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.GraphicsManager;
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
    void projectFormsRenderAtNativeAndWideWidths() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "Run with surfaceless EGL for pixel QA");
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        try (var context = PresentationOffscreenTestContext.open(426, 224);
             var menus = new EditorProjectMenuRenderer()) {
            // Engine and primitive commands share this owner; a private manager leaves chrome on an uninitialized host.
            GraphicsManager graphics = GameServices.graphics();
            boolean batching = graphics.isBatchingEnabled();
            int projectionWidth = graphics.getProjectionWidth();
            float[] projection = graphics.getProjectionMatrixBuffer();
            int viewportX = graphics.getViewportX(), viewportY = graphics.getViewportY();
            int viewportWidth = graphics.getViewportWidth(), viewportHeight = graphics.getViewportHeight();
            try {
                graphics.setHeadlessMode(false);
                graphics.init("shaders/shader_the_hedgehog.glsl");
                assertTrue(graphics.isGlInitialized(), "Primitive and tile commands need the same initialized host as Engine");
                graphics.setBatchingEnabled(false);
                for (int width : new int[] {320, 426}) {
                    float[] formProjection = new Matrix4f().ortho(0, width, 0, 224, -1, 1).get(new float[16]);
                    graphics.setViewport(0, 0, width, 224); graphics.setProjectionWidth(width);
                    graphics.setProjectionMatrixBuffer(formProjection);
                    var menu = new EditorProjectMenu();
                    menu.showForm("Level properties", List.of(
                            new EditorProjectMenu.Field("name", "Name", "A playable authored level", MenuTextEditor.Mode.TEXT),
                            new EditorProjectMenu.Field("start", "Start X", "64", MenuTextEditor.Mode.INTEGER),
                            new EditorProjectMenu.Field("path", "Project file", "projects/my-level.ogfproject", MenuTextEditor.Mode.PATH)),
                            "Apply properties", ignored -> {});
                    menu.setSubtitle("Changes apply after validation.");
                    picture(width, "project", () -> menus.render(menu, formProjection, width));
                    menu.report(new IllegalArgumentException("Missing dependency retained; enable owner:object to playtest."));
                    picture(width, "error", () -> menus.render(menu, formProjection, width));
                }
            } finally {
                graphics.cleanup(); graphics.setHeadlessMode(true);
                graphics.setBatchingEnabled(batching);
                graphics.setProjectionWidth(projectionWidth);
                graphics.setProjectionMatrixBuffer(projection);
                graphics.setViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            }
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
            int visible = 0;
            for (int y = 0; y < 224; y++) for (int x = 0; x < width; x++) {
                int i = ((223 - y) * width + x) * 4;
                int r = pixels.get(i) & 255, g = pixels.get(i + 1) & 255, b = pixels.get(i + 2) & 255;
                if (r > 30 || g > 30 || b > 30) visible++;
                image.setRGB(x, y, 0xff000000 | r << 16 | g << 8 | b);
            }
            Path output = Path.of("target", "editor-qa", width + "-" + name + ".png");
            Files.createDirectories(output.getParent()); ImageIO.write(image, "png", output.toFile());
            String counts = " width=" + width + " visible=" + visible;
            assertTrue(visible > 1000, "Project form and glyphs must reach the framebuffer: " + name + counts);
            assertEquals(GL_NO_ERROR, glGetError(), "Editor overlay must not rely on unavailable GL state" + counts);
        } finally { MemoryUtil.memFree(pixels); }
    }
}
