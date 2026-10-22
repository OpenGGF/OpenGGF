package com.openggf.stageplay.runtime;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.TilePriorityFBO;
import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.util.FboHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL30.*;

/** Real EGL pixels: completed-world filtering, native viewport tiling and host-state ownership. */
@EnabledOnOs(OS.LINUX)
class TestStageplayWorldPresentation {
    @Test void boundsFailBeforeResourceAllocation() {
        assertDoesNotThrow(() -> StageplayWorldPresentation.validate(320, 224, .5));
        assertDoesNotThrow(() -> StageplayWorldPresentation.validate(1920, 1080, 1));
        assertThrows(IllegalArgumentException.class, () -> StageplayWorldPresentation.validate(1920, 1080, .05));
        assertThrows(IllegalArgumentException.class, () -> StageplayWorldPresentation.validate(320, 224, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> StageplayWorldPresentation.validate(320, 224, 0));
    }

    @Test void fractionalZoomFiltersOnlyTheCompletedOpaqueWorldAcrossTileEdges() {
        try (var context = PresentationOffscreenTestContext.open(24, 16)) {
            var graphics = new GraphicsManager();
            var target = FboHelper.createColorOnly(20, 12, GL_CLAMP_TO_EDGE);
            assertNotNull(target);
            try (var presentation = new StageplayWorldPresentation()) {
                glBindFramebuffer(GL_FRAMEBUFFER, target.fboId());
                glClearColor(0, 1, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
                glViewport(2, 3, 12, 6); graphics.setViewport(2, 3, 12, 6);
                var tiles = new ArrayList<String>();
                presentation.drawTiles(graphics, 0, 0, 1.5, 12, 6, 4, 3, (cx, cy, w, h) -> {
                    tiles.add(cx + ":" + cy);
                    paintWorld(cx, cy, w, h);
                });
                assertEquals(List.of("0:0", "4:0", "0:3", "4:3"), tiles);
                glBindFramebuffer(GL_READ_FRAMEBUFFER, target.fboId());
                for (int y = 0; y < 6; y++) for (int x = 0; x < 12; x++) {
                    int[] pixel = pixel(x + 2, 3 + 5 - y);
                    assertEquals(expectedRed(x, 1.5, 8), pixel[0], 1, "red at " + x + "," + y);
                    assertEquals(255 - expectedRed(x, 1.5, 8), pixel[2], 1, "blue at " + x + "," + y);
                    assertEquals(expectedGreen(y, 1.5, 4), pixel[1], 1, "green at " + x + "," + y);
                    assertEquals(255, pixel[3], "completed world is opaque");
                }
                assertArrayEquals(new int[]{0, 255, 0, 255}, pixel(0, 0), "outside the view remains owned by host");
                // Captions/overlays submitted after composition retain hard pixel edges.
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target.fboId());
                glEnable(GL_SCISSOR_TEST); glScissor(7, 5, 1, 1);
                glClearColor(1, 1, 1, 1); glClear(GL_COLOR_BUFFER_BIT); glDisable(GL_SCISSOR_TEST);
                assertArrayEquals(new int[]{255, 255, 255, 255}, pixel(7, 5));
                assertTrue(pixel(6, 5)[0] < 255, "neighbor did not acquire the overlay's white edge");
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0); FboHelper.destroy(target);
            }
        }
    }

    @Test void integerZoomKeepsNativePixelsAndFractionalSourceExtentIsNotRounded() {
        try (var context = PresentationOffscreenTestContext.open(24, 16)) {
            var graphics = new GraphicsManager();
            var target = FboHelper.createColorOnly(12, 6, GL_CLAMP_TO_EDGE);
            assertNotNull(target);
            try (var presentation = new StageplayWorldPresentation()) {
                glBindFramebuffer(GL_FRAMEBUFFER, target.fboId()); glViewport(0, 0, 12, 6);
                for (double zoom : new double[]{2, 1.6, 1.65}) {
                    presentation.drawTiles(graphics, 0, 0, zoom, 12, 6, 4, 3, TestStageplayWorldPresentation::paintWorld);
                    glBindFramebuffer(GL_READ_FRAMEBUFFER, target.fboId());
                    int sourceWidth = (int) Math.ceil(12 / zoom);
                    for (int x = 0; x < 12; x++) {
                        int red = pixel(x, 5)[0];
                        int expected = zoom == 2 ? ((x / 2) % 2 == 0 ? 255 : 0) : expectedRed(x, zoom, sourceWidth);
                        assertEquals(expected, red, 1, "zoom " + zoom + " pixel " + x);
                    }
                }
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0); FboHelper.destroy(target);
            }
        }
    }

    @Test void restoresSeparateTargetsProjectionAndNativeGlStateAfterSuccessAndFailure() {
        try (var context = PresentationOffscreenTestContext.open(24, 16)) {
            var graphics = new GraphicsManager();
            var draw = FboHelper.createColorOnly(20, 12, GL_CLAMP_TO_EDGE);
            var read = FboHelper.createColorOnly(20, 12, GL_CLAMP_TO_EDGE);
            assertNotNull(draw); assertNotNull(read);
            int texture = glGenTextures();
            try (var presentation = new StageplayWorldPresentation()) {
                float[] projection = new float[16]; projection[0] = 7;
                graphics.setProjectionMatrixBuffer(projection); graphics.setProjectionWidth(640);
                graphics.setViewport(6, 7, 20, 12);
                for (boolean fail : new boolean[]{false, true}) {
                    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw.fboId());
                    glBindFramebuffer(GL_READ_FRAMEBUFFER, read.fboId()); glViewport(2, 3, 12, 6);
                    glActiveTexture(GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D, texture);
                    glEnable(GL_BLEND); glBlendFuncSeparate(GL_ONE, GL_ZERO, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA);
                    glEnable(GL_SCISSOR_TEST); glScissor(1, 2, 4, 5);
                    glClearColor(.25f, .5f, .75f, 1);
                    Runnable action = () -> presentation.drawTiles(graphics, 0, 0, 1.5, 12, 6, 4, 3, (cx, cy, w, h) -> {
                        assertEquals(4, graphics.getViewportWidth()); assertEquals(3, graphics.getConfiguredScreenHeightPx());
                        assertNotSame(projection, graphics.getProjectionMatrixBuffer());
                        if (fail) throw new IllegalStateException("world producer failed");
                        paintWorld(cx, cy, w, h);
                    });
                    if (fail) assertEquals("world producer failed", assertThrows(IllegalStateException.class, action::run).getMessage());
                    else action.run();
                    assertEquals(draw.fboId(), glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
                    assertEquals(read.fboId(), glGetInteger(GL_READ_FRAMEBUFFER_BINDING));
                    int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport);
                    assertArrayEquals(new int[]{2, 3, 12, 6}, viewport);
                    assertEquals(GL_TEXTURE3, glGetInteger(GL_ACTIVE_TEXTURE));
                    assertEquals(texture, glGetInteger(GL_TEXTURE_BINDING_2D));
                    assertTrue(glIsEnabled(GL_BLEND)); assertTrue(glIsEnabled(GL_SCISSOR_TEST));
                    assertEquals(GL_ONE, glGetInteger(GL_BLEND_SRC_RGB)); assertEquals(GL_ZERO, glGetInteger(GL_BLEND_DST_RGB));
                    assertEquals(GL_ONE_MINUS_SRC_ALPHA, glGetInteger(GL_BLEND_SRC_ALPHA));
                    assertEquals(GL_SRC_ALPHA, glGetInteger(GL_BLEND_DST_ALPHA));
                    assertSame(projection, graphics.getProjectionMatrixBuffer());
                    assertEquals(640, graphics.getProjectionWidth());
                    assertEquals(6, graphics.getViewportX()); assertEquals(7, graphics.getViewportY());
                    assertEquals(20, graphics.getViewportWidth()); assertEquals(12, graphics.getViewportHeight());
                }
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                glDisable(GL_SCISSOR_TEST); glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glDeleteTextures(texture); FboHelper.destroy(draw); FboHelper.destroy(read);
            }
        }
    }

    @Test void nestedFramebufferAllocationAndPriorityPassRetainBothCallerTargets() {
        try (var context = PresentationOffscreenTestContext.open(24, 16)) {
            var draw = FboHelper.createColorOnly(20, 12, GL_CLAMP_TO_EDGE);
            var read = FboHelper.createColorOnly(20, 12, GL_CLAMP_TO_EDGE);
            assertNotNull(draw); assertNotNull(read);
            var priority = new TilePriorityFBO();
            FboHelper.FboHandle nested = null;
            try {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw.fboId()); glBindFramebuffer(GL_READ_FRAMEBUFFER, read.fboId());
                glViewport(2, 3, 12, 6); glClearColor(.25f, .5f, .75f, 1);
                nested = FboHelper.createWithDepth(8, 6, GL_CLAMP_TO_EDGE);
                assertNotNull(nested);
                assertEquals(draw.fboId(), glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
                assertEquals(read.fboId(), glGetInteger(GL_READ_FRAMEBUFFER_BINDING));
                priority.init(8, 6);
                assertEquals(draw.fboId(), glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
                assertEquals(read.fboId(), glGetInteger(GL_READ_FRAMEBUFFER_BINDING));
                priority.begin();
                try {
                    assertNotEquals(draw.fboId(), glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
                    assertThrows(IllegalStateException.class, priority::begin);
                } finally { priority.end(); }
                assertEquals(draw.fboId(), glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
                assertEquals(read.fboId(), glGetInteger(GL_READ_FRAMEBUFFER_BINDING));
                float[] clear = new float[4]; glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
                assertArrayEquals(new float[]{.25f, .5f, .75f, 1}, clear);
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0); priority.cleanup();
                FboHelper.destroy(nested); FboHelper.destroy(draw); FboHelper.destroy(read);
            }
        }
    }

    private static void paintWorld(int cameraX, int cameraY, int width, int height) {
        glEnable(GL_SCISSOR_TEST);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean red = (cameraX + x) % 2 == 0;
            glScissor(x, height - 1 - y, 1, 1);
            glClearColor(red ? 1 : 0, (cameraY + y) * (32 / 255f), red ? 0 : 1, 0);
            glClear(GL_COLOR_BUFFER_BIT);
        }
    }

    private static int expectedRed(int x, double zoom, int sourceWidth) {
        double source = Math.clamp((x + .5) / zoom - .5, 0, sourceWidth - 1);
        int first = (int) Math.floor(source), second = Math.min(first + 1, sourceWidth - 1);
        double fraction = source - first;
        return (int) Math.round(255 * ((first % 2 == 0 ? 1 : 0) * (1 - fraction) + (second % 2 == 0 ? 1 : 0) * fraction));
    }

    private static int expectedGreen(int y, double zoom, int sourceHeight) {
        return (int) Math.round(32 * Math.clamp((y + .5) / zoom - .5, 0, sourceHeight - 1));
    }

    private static int[] pixel(int x, int y) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(4);
        glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return new int[]{pixels.get(0) & 255, pixels.get(1) & 255, pixels.get(2) & 255, pixels.get(3) & 255};
    }
}
