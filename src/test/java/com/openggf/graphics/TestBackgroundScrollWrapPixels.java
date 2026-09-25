package com.openggf.graphics;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;

/** DDZ exposed a one-pixel seam when a 512px plane lives in an 800px FBO allocation. */
class TestBackgroundScrollWrapPixels {
    @Test void integerScrollNeverSamplesOutsideTheRenderedPeriod() throws Exception {
        boolean nativeDisplay = Boolean.getBoolean("openggf.test.gl.native");
        long window = 0;
        ShaderProgram shader = null; QuadRenderer quad = null; HScrollBuffer scroll = null;
        int texture = 0; ByteBuffer data = null;
        try {
            glfwInitHint(GLFW_PLATFORM, nativeDisplay ? GLFW_ANY_PLATFORM : GLFW_PLATFORM_NULL);
            assumeTrue(glfwInit(), "GLFW unavailable");
            glfwDefaultWindowHints(); glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            if (!nativeDisplay) glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
            window = glfwCreateWindow(800, 224, "background wrap regression", 0, 0);
            assumeTrue(window != 0, "OpenGL 4.1 unavailable");
            glfwMakeContextCurrent(window); GL.createCapabilities();
            glDisable(GL_DITHER); glDisable(GL_BLEND); glViewport(0, 0, 800, 224);
            shader = new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER, "shaders/shader_parallax_bg.glsl");
            quad = new QuadRenderer(); scroll = new HScrollBuffer(); scroll.init();
            texture = glGenTextures(); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture);
            data = MemoryUtil.memAlloc(800 * 272 * 4);
            for (int y = 0; y < 272; y++) for (int x = 0; x < 800; x++)
                data.put((byte) (x < 512 ? 100 : 0)).put((byte) 0).put((byte) 0).put((byte) (x < 512 ? 255 : 0));
            data.flip(); glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 800, 272, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            int program = shader.getProgramId();
            shader.use();
            for (String sampler : new String[]{"HScrollTexture", "VScrollTexture", "VScrollColumnTexture", "ColumnRemapX", "ColumnRemapY"})
                glUniform1i(glGetUniformLocation(program, sampler), 1);
            glUniform1i(glGetUniformLocation(program, "BackgroundTexture"), 0);
            uniform(program, "ScreenWidth", 800); uniform(program, "ScreenHeight", 224);
            uniform(program, "ActiveDisplayWidth", 800); uniform(program, "BGTextureWidth", 512);
            uniform(program, "BGTextureHeight", 272); uniform(program, "FBOAllocationWidth", 800);
            uniform(program, "FillTransparentWithBackdrop", 1);
            int[] words = new int[224];
            // Actual integer VDP words, normalized/uploaded by the production buffer.
            for (int base = -32767; base <= 32767; base += 224) {
                for (int y = 0; y < 224; y++) words[y] = Math.min(32767, base + y) & 0xFFFF;
                scroll.upload(words); scroll.bind(1);
                glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture);
                quad.draw(0, 0, 800, 224); glFinish(); data.clear();
                glReadPixels(0, 0, 800, 224, GL_RGBA, GL_UNSIGNED_BYTE, data);
                for (int y = 0; y < 224; y++) for (int x = 0; x < 800; x++) {
                    int red = data.get((y * 800 + x) * 4) & 255;
                    if (red != 100) fail("outside period: scroll=" + (short) words[223-y] + " x=" + x + " red=" + red);
                }
            }
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            if (data != null) MemoryUtil.memFree(data);
            if (scroll != null) scroll.cleanup(); if (quad != null) quad.cleanup(); if (shader != null) shader.cleanup();
            if (texture != 0) glDeleteTextures(texture);
            if (window != 0) { GL.setCapabilities(null); glfwDestroyWindow(window); }
            glfwTerminate(); glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
        }
    }
    private static void uniform(int program, String name, float value) {
        glUniform1f(glGetUniformLocation(program, name), value);
    }
}
