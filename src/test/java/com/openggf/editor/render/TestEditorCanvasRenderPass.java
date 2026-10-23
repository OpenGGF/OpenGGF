package com.openggf.editor.render;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/** Exercises the editor compositor against real display-free EGL pixels, without Engine boot. */
class TestEditorCanvasRenderPass {
    @Test
    void topLeftCropScalesTerrainAndMarkersTogetherAndRestoresCallerFramebuffer() {
        withContext(() -> {
            var graphics = new GraphicsManager();
            graphics.setGlInitialized(true);
            int texture = texture(1100, 500), drawFbo = framebuffer(texture);
            int readTexture = texture(1, 1), readFbo = framebuffer(readTexture);
            try (var pass = new EditorCanvasRenderPass()) {
                glDisable(GL_DITHER);
                for (int logicalWidth : new int[]{320, 528}) for (int displayScale : new int[]{1, 2}) {
                    int width = logicalWidth * displayScale, height = 224 * displayScale;
                    for (int zoom : new int[]{1, 2, 3, 8}) {
                        prepare(graphics, drawFbo, readFbo, width, height);
                        pass.render(graphics, zoom, logicalWidth, 224, () -> {
                            // Separate native-style terrain and marker submissions share the same picture.
                            graphics.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM,
                                    (cx, cy, cw, ch) -> {
                                        rect(logicalWidth, 224, 0, 0, 4, 4, 1, 0, 0);
                                        rect(logicalWidth, 224, 16, 16, 32, 32, 0, 0, 1);
                                        rect(logicalWidth, 224, logicalWidth * 3 / 4, 8, 8, 8, 0, 1, 0);
                                    }));
                            graphics.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM,
                                    (cx, cy, cw, ch) -> rect(logicalWidth, 224, 24, 24, 8, 8, 1, 1, 0)));
                            graphics.flushWithCamera((short) 0, (short) 0, (short) logicalWidth, (short) 224);
                        });
                        assertCaller(graphics, drawFbo, readFbo, width, height);
                        glBindFramebuffer(GL_READ_FRAMEBUFFER, drawFbo);
                        // Coordinates are logical top-left pixels in the displayed canvas.
                        assertArrayEquals(new int[]{255, 0, 0, 255}, pixel(2, 2, height, displayScale),
                                "top-left source pixel stays anchored at zoom " + zoom);
                        if (zoom <= 3) {
                            assertArrayEquals(new int[]{0, 0, 255, 255}, pixel(20 * zoom, 20 * zoom, height, displayScale));
                            assertArrayEquals(new int[]{255, 255, 0, 255}, pixel(26 * zoom, 26 * zoom, height, displayScale),
                                    "marker and terrain are magnified by the same transform");
                            assertArrayEquals(new int[]{0, 0, 255, 255}, pixel(36 * zoom, 26 * zoom, height, displayScale),
                                    "marker edge remains aligned with its terrain cell");
                        }
                        if (zoom > 1) {
                            assertArrayEquals(new int[]{0, 0, 0, 255}, pixel(logicalWidth - 8, 12, height, displayScale),
                                    "magnification crops the native picture instead of shrinking its viewport");
                        } else {
                            assertArrayEquals(new int[]{0, 255, 0, 255}, pixel(logicalWidth * 3 / 4 + 2, 10, height, displayScale),
                                    "1x retains the direct producer's full native world picture");
                        }
                        assertEquals(GL_NO_ERROR, glGetError());
                    }
                }
            } finally {
                graphics.discardQueuedCommands();
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glDeleteFramebuffers(drawFbo); glDeleteFramebuffers(readFbo);
                glDeleteTextures(texture); glDeleteTextures(readTexture);
            }
        });
    }

    @Test
    void producerFailureDiscardsQueuedWorkAndRestoresFramebufferViewportAndFlags() {
        withContext(() -> {
            var graphics = new GraphicsManager();
            graphics.setGlInitialized(true);
            int texture = texture(400, 250), drawFbo = framebuffer(texture);
            int readTexture = texture(1, 1), readFbo = framebuffer(readTexture);
            try (var pass = new EditorCanvasRenderPass()) {
                prepare(graphics, drawFbo, readFbo, 320, 224);
                graphics.setUseUnderwaterPaletteForBackground(false);
                graphics.setUseSpritePriorityShader(false);
                var failure = assertThrows(IllegalStateException.class, () -> pass.render(graphics, 2, 320, 224, () -> {
                    graphics.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM,
                            (cx, cy, cw, ch) -> fail("failed producer's remaining commands must be discarded")));
                    graphics.setUseUnderwaterPaletteForBackground(true);
                    graphics.setUseSpritePriorityShader(true);
                    glBindFramebuffer(GL_FRAMEBUFFER, 0);
                    glViewport(0, 0, 1, 1);
                    glDisable(GL_DEPTH_TEST);
                    throw new IllegalStateException("producer fault");
                }));
                assertEquals("producer fault", failure.getMessage());
                assertCaller(graphics, drawFbo, readFbo, 320, 224);
                assertFalse(graphics.isUseUnderwaterPaletteForBackground());
                assertFalse(graphics.isUseSpritePriorityShader());
                graphics.flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224);
                // Reuse after a failure must open a fresh scope and retain valid GL state.
                pass.render(graphics, 2, 320, 224, () -> rect(320, 224, 0, 0, 8, 8, 1, 0, 0));
                assertCaller(graphics, drawFbo, readFbo, 320, 224);
                glBindFramebuffer(GL_READ_FRAMEBUFFER, drawFbo);
                assertArrayEquals(new int[]{255, 0, 0, 255}, pixel(2, 2, 224, 1));
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                graphics.discardQueuedCommands();
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glDeleteFramebuffers(drawFbo); glDeleteFramebuffers(readFbo);
                glDeleteTextures(texture); glDeleteTextures(readTexture);
            }
        });
    }

    private static void prepare(GraphicsManager graphics, int drawFbo, int readFbo, int width, int height) {
        glBindFramebuffer(GL_FRAMEBUFFER, drawFbo);
        glViewport(9, 7, width, height);
        graphics.setViewport(9, 7, width, height);
        glDisable(GL_SCISSOR_TEST); glDisable(GL_BLEND);
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
        glEnable(GL_DEPTH_TEST);
    }

    private static void assertCaller(GraphicsManager graphics, int drawFbo, int readFbo, int width, int height) {
        assertEquals(drawFbo, glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
        assertEquals(readFbo, glGetInteger(GL_READ_FRAMEBUFFER_BINDING));
        int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport);
        assertArrayEquals(new int[]{9, 7, width, height}, viewport);
        assertEquals(9, graphics.getViewportX()); assertEquals(7, graphics.getViewportY());
        assertEquals(width, graphics.getViewportWidth()); assertEquals(height, graphics.getViewportHeight());
        assertTrue(glIsEnabled(GL_DEPTH_TEST));
    }

    private static void rect(int logicalWidth, int logicalHeight, int x, int y, int width, int height,
                             float red, float green, float blue) {
        int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport);
        int scaleX = viewport[2] / logicalWidth, scaleY = viewport[3] / logicalHeight;
        glDisable(GL_BLEND); glEnable(GL_SCISSOR_TEST);
        glScissor(viewport[0] + x * scaleX, viewport[1] + (logicalHeight - y - height) * scaleY,
                width * scaleX, height * scaleY);
        glClearColor(red, green, blue, 1); glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);
    }

    private static int[] pixel(int x, int y, int height, int scale) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        glReadPixels(9 + x * scale, 7 + height - 1 - y * scale, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        return new int[]{pixel.get(0) & 255, pixel.get(1) & 255, pixel.get(2) & 255, pixel.get(3) & 255};
    }

    private static int texture(int width, int height) {
        int texture = glGenTextures(); glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return texture;
    }

    private static int framebuffer(int texture) {
        int fbo = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
        return fbo;
    }

    private static void withContext(Runnable check) {
        assumeTrue(PresentationOffscreenTestContext.requested(), "Requires display-free surfaceless-egl; never uses the desktop");
        try (var context = PresentationOffscreenTestContext.open(64, 64)) { check.run(); }
    }
}
