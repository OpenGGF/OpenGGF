package com.openggf.graphics;

import com.openggf.util.IntIndexedView;
import com.openggf.util.ShortIndexedView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lwjgl.opengl.GL;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/** Native readback of both production upload overloads and resource recreation. */
@EnabledIfSystemProperty(named = "openggf.scrollNative", matches = "true")
class TestScrollBufferUploadNative {
    @Test
    void arraysAndViewsUploadExactValuesAcrossResourceAndContextRecreation() {
        for (int context = 0; context < 2; context++) {
            assertTrue(glfwInit(), "GLFW initialization");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            long window = glfwCreateWindow(32, 32, "ScrollUpload", 0, 0);
            assertNotEquals(0, window, "OpenGL 4.1 context");
            try {
                glfwMakeContextCurrent(window);
                GL.createCapabilities();
                for (boolean foreground : new boolean[] {false, true}) verifyHorizontal(foreground);
                for (int entries : new int[] {1, 20, 33, 224}) verifyVertical(entries);
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                GL.setCapabilities(null);
                glfwMakeContextCurrent(0);
                glfwDestroyWindow(window);
                glfwTerminate();
            }
        }
    }

    private static void verifyHorizontal(boolean foreground) {
        HScrollBuffer buffer = new HScrollBuffer(foreground);
        int[] packed = {0x7fff8000, 0x80007fff, 0xffff0001};
        IntIndexedView view = new IntIndexedView() {
            public int size() { return packed.length; }
            public int get(int index) { return packed[index]; }
        };
        buffer.upload(packed); // Must remain safe before initialization.
        buffer.upload(view);
        try {
            for (int lifecycle = 0; lifecycle < 2; lifecycle++) {
                buffer.init();
                buffer.upload(packed);
                float[] expected = new float[224];
                expected[0] = foreground ? 1 : -1;
                expected[1] = foreground ? -1 : 1;
                expected[2] = (foreground ? -1 : 1) / 32767.0f;
                assertArrayEquals(expected, read(buffer.getTextureId(), 224));
                buffer.upload(new int[] {0});
                buffer.upload(view);
                assertArrayEquals(expected, read(buffer.getTextureId(), 224));
                buffer.upload((int[]) null);
                buffer.upload((IntIndexedView) null);
                assertArrayEquals(expected, read(buffer.getTextureId(), 224));
                buffer.upload(new int[] {0});
                assertArrayEquals(new float[224], read(buffer.getTextureId(), 224));
                int oldTexture = buffer.getTextureId();
                buffer.cleanup();
                assertFalse(glIsTexture(oldTexture), "cleanup must delete the texture");
            }
        } finally { buffer.cleanup(); }
    }

    private static void verifyVertical(int entries) {
        VScrollBuffer buffer = new VScrollBuffer(entries);
        short[] values = {Short.MIN_VALUE, Short.MAX_VALUE, -1};
        ShortIndexedView view = new ShortIndexedView() {
            public int size() { return values.length; }
            public short get(int index) { return values[index]; }
        };
        buffer.upload(values);
        buffer.upload(view);
        try {
            for (int lifecycle = 0; lifecycle < 2; lifecycle++) {
                buffer.init();
                float[] expected = new float[entries];
                expected[0] = -1;
                if (entries > 1) expected[1] = 1;
                if (entries > 2) expected[2] = -1 / 32767.0f;
                buffer.upload(values);
                assertArrayEquals(expected, read(buffer.getTextureId(), entries));
                buffer.upload(new short[] {0});
                buffer.upload(view);
                assertArrayEquals(expected, read(buffer.getTextureId(), entries));
                buffer.upload((short[]) null);
                buffer.upload((ShortIndexedView) null);
                assertArrayEquals(expected, read(buffer.getTextureId(), entries));
                buffer.upload(new short[] {0});
                assertArrayEquals(new float[entries], read(buffer.getTextureId(), entries));
                int oldTexture = buffer.getTextureId();
                buffer.cleanup();
                assertFalse(glIsTexture(oldTexture), "cleanup must delete the texture");
            }
        } finally { buffer.cleanup(); }
    }

    private static float[] read(int texture, int entries) {
        assertEquals(0, glGetInteger(GL_TEXTURE_BINDING_1D), "upload must unbind");
        glBindTexture(GL_TEXTURE_1D, texture);
        assertEquals(entries, glGetTexLevelParameteri(GL_TEXTURE_1D, 0, GL_TEXTURE_WIDTH));
        float[] values = new float[entries];
        glGetTexImage(GL_TEXTURE_1D, 0, GL_RED, GL_FLOAT, values);
        glBindTexture(GL_TEXTURE_1D, 0);
        return values;
    }
}
