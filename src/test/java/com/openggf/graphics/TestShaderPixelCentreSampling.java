package com.openggf.graphics;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;

/** Exercises actual foreground and background shaders with coordinate-coded textures. */
class TestShaderPixelCentreSampling {
    @Test
    void pixelCentresSurviveNativeIntegerAndFractionalScalingWithViewportOffsets() throws Exception {
        boolean nativeDisplay = Boolean.getBoolean("openggf.test.gl.native");
        long window = 0;
        boolean initialized = false;
        TilemapGpuRenderer tilemap = null;
        ShaderProgram background = null;
        QuadRenderer quad = null;
        List<Integer> textures = new ArrayList<>();
        try {
            glfwInitHint(GLFW_PLATFORM, nativeDisplay ? GLFW_ANY_PLATFORM : GLFW_PLATFORM_NULL);
            initialized = glfwInit();
            assumeTrue(initialized, "GLFW context provider unavailable");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            if (!nativeDisplay) glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API);
            window = glfwCreateWindow(1024, 768, "pixel centre regression", 0, 0);
            assumeTrue(window != 0, "OpenGL 4.1 unavailable");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glDisable(GL_DITHER);
            glDisable(GL_BLEND);
            tilemap = new TilemapGpuRenderer();
            tilemap.init("shaders/shader_tilemap.glsl");
            tilemap.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND,
                    TilemapGpuRenderer.packWindowDescriptors(new int[40 * 28]), 40, 28);
            tilemap.setPatternLookupData(new byte[]{0, 0, 0, -1}, 1);
            byte[] atlasBytes = new byte[8 * 8 * 4];
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                atlasBytes[(y * 8 + x) * 4] = (byte) (1 + ((x + y) & 7));
                atlasBytes[(y * 8 + x) * 4 + 3] = -1;
            }
            int atlas = texture(atlasBytes, 8, 8, textures);
            byte[] paletteBytes = new byte[64 * 4];
            for (int i = 0; i < 64; i++) {
                paletteBytes[i * 4] = (byte) ((i & 15) * 16);
                paletteBytes[i * 4 + 3] = -1;
            }
            int palette = texture(paletteBytes, 16, 4, textures);
            byte[] backgroundBytes = new byte[320 * 224 * 4];
            for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
                backgroundBytes[(y * 320 + x) * 4] = (byte) expectedRed(x, 223 - y);
                backgroundBytes[(y * 320 + x) * 4 + 3] = -1;
            }
            int backgroundTexture = texture(backgroundBytes, 320, 224, textures);
            background = new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER,
                    "shaders/shader_parallax_bg.glsl");
            quad = new QuadRenderer();
            int dummy = glGenTextures();
            textures.add(dummy);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_1D, dummy);
            glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA8, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            for (boolean isBackground : new boolean[]{false, true}) {
                for (int[] size : new int[][]{{320, 224}, {640, 448}, {960, 672}, {528, 370}}) {
                    int width = size[0], height = size[1], ox = 19, oy = 13;
                    glViewport(0, 0, 1024, 768);
                    glClearColor(0, 0, 0, 1);
                    glClear(GL_COLOR_BUFFER_BIT);
                    glViewport(ox, oy, width, height);
                    if (!isBackground) {
                        tilemap.render(TilemapGpuRenderer.Layer.FOREGROUND, 320, 224,
                                ox, oy, width, height, 0, 0, 8, 8,
                                atlas, palette, palette, -1, false, false, false, 224);
                    } else {
                        background.use();
                        int program = background.getProgramId();
                        for (String sampler : new String[]{"HScrollTexture", "VScrollTexture", "VScrollColumnTexture"})
                            glUniform1i(glGetUniformLocation(program, sampler), 1);
                        glUniform1i(glGetUniformLocation(program, "BackgroundTexture"), 0);
                        glUniform1i(glGetUniformLocation(program, "NoHScroll"), 1);
                        uniform(program, "ScreenWidth", width); uniform(program, "ScreenHeight", height);
                        uniform(program, "ActiveDisplayWidth", 320);
                        uniform(program, "BGTextureWidth", 320); uniform(program, "BGTextureHeight", 224);
                        uniform(program, "ViewportOffsetX", ox); uniform(program, "ViewportOffsetY", oy);
                        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, backgroundTexture);
                        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_1D, dummy);
                        quad.draw(0, 0, width, height);
                        background.stop();
                    }
                    glFinish();
                    ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
                    try {
                        glReadPixels(ox, oy, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                        for (int y = 0; y < height; y++) for (int x : new int[]{0, width / 3, width / 2, width - 1}) {
                            int gameX = (int) Math.floor((x + .5) * 320 / width);
                            int gameY = (int) Math.floor((height - y - .5) * 224 / height);
                            assertEquals(expectedRed(gameX, gameY), pixels.get((y * width + x) * 4) & 255,
                                    "background=" + isBackground + " size=" + width + "x" + height + " pixel=" + x + "," + y);
                        }
                    } finally { MemoryUtil.memFree(pixels); }
                    assertBlack(ox - 1, oy); assertBlack(ox, oy - 1);
                    assertBlack(ox + width, oy); assertBlack(ox, oy + height);
                    assertEquals(GL_NO_ERROR, glGetError());
                }
            }
        } finally {
            if (tilemap != null) tilemap.cleanup();
            if (background != null) background.cleanup();
            if (quad != null) quad.cleanup();
            for (int texture : textures) glDeleteTextures(texture);
            if (window != 0) { GL.setCapabilities(null); glfwDestroyWindow(window); }
            if (initialized) glfwTerminate();
            glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
        }
    }

    private static int expectedRed(int x, int y) { return (1 + ((x + y) & 7)) * 16; }
    private static void uniform(int program, String name, float value) {
        glUniform1f(glGetUniformLocation(program, name), value);
    }
    private static int texture(byte[] bytes, int width, int height, List<Integer> textures) {
        int id = glGenTextures(); textures.add(id); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, id);
        ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
        try {
            data.put(bytes).flip(); glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        } finally { MemoryUtil.memFree(data); }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return id;
    }
    private static void assertBlack(int x, int y) {
        ByteBuffer pixel = MemoryUtil.memAlloc(4);
        try { glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel); assertEquals(0, pixel.get(0) & 255); }
        finally { MemoryUtil.memFree(pixel); }
    }
}
