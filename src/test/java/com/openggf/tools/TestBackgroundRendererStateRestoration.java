package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.render.BackgroundRenderer;
import com.openggf.util.FboHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL30.*;
import static org.mockito.Mockito.*;

/** Native background work must return to the scene owner, including separate read/draw targets. */
@EnabledOnOs(OS.LINUX)
class TestBackgroundRendererStateRestoration {
    @Test
    void uninitializedOwnerDoesNotReadNativeState() {
        var background = new BackgroundRenderer(mock(GraphicsManager.class));
        background.ensureCapacity(2048, 512);
        background.beginTilePass(320, 224, true);
        background.endTilePass();
        assertFalse(background.isInitialized());
    }

    @Test
    void capacityGrowthAndTilePassRestoreTheNativeCallerState() throws Exception {
        var config = GameServices.configuration();
        Object oldWidth = config.getConfigValue(SonicConfiguration.SCREEN_WIDTH);
        config.setConfigValue(SonicConfiguration.SCREEN_WIDTH, 320);
        try (var context = SurfacelessEglContext.create(64, 48, true)) {
            GL.createCapabilities();
            glDisable(GL_DITHER);
            GraphicsManager graphics = mock(GraphicsManager.class);
            when(graphics.getViewportWidth()).thenReturn(320);
            when(graphics.getViewportHeight()).thenReturn(224);
            var background = new BackgroundRenderer(graphics);
            FboHelper.FboHandle draw = null;
            FboHelper.FboHandle read = null;
            try {
                background.init("shaders/shader_parallax_bg.glsl");
                draw = FboHelper.createColorOnly(64, 48, GL_CLAMP_TO_EDGE);
                read = FboHelper.createColorOnly(64, 48, GL_CLAMP_TO_EDGE);
                assertNotNull(draw);
                assertNotNull(read);
                clear(draw.fboId(), 1, 0, 0);
                clear(read.fboId(), 0, 0, 1);
                int[] viewport = {7, 9, 31, 27};
                bindCaller(draw.fboId(), read.fboId(), viewport);
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

                int grownWidth = background.getFBOAllocWidth() + 16;
                background.ensureCapacity(grownWidth, 272);
                assertEquals(grownWidth, background.getFBOAllocWidth(), "exercise actual GPU growth");
                assertCallerState(draw.fboId(), read.fboId(), viewport, true);
                assertCallerPixels(draw.fboId(), read.fboId());
                background.ensureCapacity(16, 16);
                assertCallerState(draw.fboId(), read.fboId(), viewport, true);

                for (boolean blending : new boolean[]{false, true}) {
                    bindCaller(draw.fboId(), read.fboId(), viewport);
                    if (blending) glEnable(GL_BLEND); else glDisable(GL_BLEND);
                    background.beginTilePass(40, 32, true);
                    try {
                        int tileFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
                        assertNotEquals(draw.fboId(), tileFbo);
                        assertNotEquals(read.fboId(), tileFbo);
                        assertEquals(tileFbo, glGetInteger(GL_READ_FRAMEBUFFER_BINDING));
                        assertViewport(new int[]{0, 0, 40, 32});
                        assertFalse(glIsEnabled(GL_BLEND), "descriptor metadata is written without blending");
                    } finally {
                        background.endTilePass();
                    }
                    assertCallerState(draw.fboId(), read.fboId(), viewport, blending);
                    assertCallerPixels(draw.fboId(), read.fboId());
                }
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                background.cleanup();
                FboHelper.destroy(draw);
                FboHelper.destroy(read);
            }
        } finally {
            GL.setCapabilities(null);
            config.setConfigValue(SonicConfiguration.SCREEN_WIDTH, oldWidth);
        }
    }

    private static void clear(int fbo, float red, float green, float blue) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glClearColor(red, green, blue, 1);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private static void bindCaller(int drawFbo, int readFbo, int[] viewport) {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }

    private static void assertCallerState(int drawFbo, int readFbo, int[] viewport, boolean blending) {
        assertEquals(drawFbo, glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
        assertEquals(readFbo, glGetInteger(GL_READ_FRAMEBUFFER_BINDING));
        assertViewport(viewport);
        assertEquals(blending, glIsEnabled(GL_BLEND));
        assertEquals(GL_SRC_ALPHA, glGetInteger(GL_BLEND_SRC_RGB));
        assertEquals(GL_ONE_MINUS_SRC_ALPHA, glGetInteger(GL_BLEND_DST_RGB));
    }

    private static void assertViewport(int[] expected) {
        int[] actual = new int[4];
        glGetIntegerv(GL_VIEWPORT, actual);
        assertArrayEquals(expected, actual, "restore the actual GL viewport rather than the cached display viewport");
    }

    private static void assertCallerPixels(int drawFbo, int readFbo) {
        int savedReadFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        try {
            assertArrayEquals(new int[]{255, 0, 0, 255}, pixel(drawFbo));
            assertArrayEquals(new int[]{0, 0, 255, 255}, pixel(readFbo));
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, savedReadFbo);
        }
    }

    private static int[] pixel(int readFbo) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
        ByteBuffer color = BufferUtils.createByteBuffer(4);
        glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, color);
        return new int[]{color.get(0) & 255, color.get(1) & 255, color.get(2) & 255, color.get(3) & 255};
    }
}
