package com.openggf.mods.scene.host;

import com.openggf.graphics.ScreenshotCapture;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/** Native coverage of buffer replacement across small, full, texture and clip batches. */
class TestSceneRenderer {
    @Test
    void changingBatchSizesPreservesEveryPixelAndStreamingUpdates() throws Exception {
        boolean nativeDisplay = Boolean.getBoolean("openggf.test.gl.native");
        boolean initialized = false;
        long window = 0;
        SceneRenderer renderer = new SceneRenderer();
        try {
            glfwInitHint(GLFW_PLATFORM, nativeDisplay ? GLFW_ANY_PLATFORM : GLFW_PLATFORM_NULL);
            initialized = glfwInit();
            assumeTrue(initialized, "GLFW context provider unavailable");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            if (!nativeDisplay) glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
            window = glfwCreateWindow(64, 64, "scene batch regression", 0, 0);
            assumeTrue(window != 0, "OpenGL 4.1 unavailable");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glViewport(0, 0, 64, 64);
            glDisable(GL_DITHER);
            renderer.init();
            float[] projection = new Matrix4f().ortho(0, 64, 0, 64, -1, 1).get(new float[16]);
            SceneImage stream = SceneImage.streaming(1, 1);
            SceneImage white = new SceneImage(1, 1, new int[] {0xFFFFFFFF});
            for (int frame = 0; frame < 3; frame++) {
                int colour = frame % 2 == 0 ? 0xFF123456 : 0xFFABCDEF;
                stream.update(new int[] {colour});
                RecordingCanvas canvas = new RecordingCanvas(64, 64, null);
                // A tiny batch before two full 2048-quad batches exercises storage growth.
                canvas.draw(stream, 0, 0);
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        canvas.fill(x, y, 1, 1, base(x, y, frame));
                    }
                }
                canvas.draw(stream, 0, 0, SceneDraw.plain().withScale(16));
                canvas.clip(4, 4, 8, 8);
                canvas.fill(0, 0, 16, 16, 0xFF80C040);
                canvas.unclip();
                canvas.draw(white, 32, 32, SceneDraw.plain().withScale(16).withTint(0xFF80C040));
                glClear(GL_COLOR_BUFFER_BIT);
                renderer.render(canvas.ops(), projection, 64, 64, new int[] {0, 0, 64, 64});
                var pixels = ScreenshotCapture.captureFramebuffer(64, 64);
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        int expected = x < 16 && y < 16 ? colour : base(x, y, frame);
                        if ((x >= 4 && x < 12 && y >= 4 && y < 12)
                                || (x >= 32 && x < 48 && y >= 32 && y < 48)) {
                            expected = 0xFF80C040;
                        }
                        assertEquals(expected & 0xFFFFFF, pixels.pixels()[y * 64 + x] & 0xFFFFFF,
                                "frame=" + frame + " pixel=" + x + "," + y);
                    }
                }
                assertEquals(GL_NO_ERROR, glGetError());
            }
        } finally {
            if (window != 0) {
                renderer.cleanup();
                GL.setCapabilities(null);
                glfwDestroyWindow(window);
            }
            if (initialized) glfwTerminate();
            glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
        }
    }

    private static int base(int x, int y, int frame) {
        return 0xFF000000 | ((x + frame) << 16) | (y << 8) | 0x40;
    }
}
