package com.openggf.graphics;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lwjgl.opengl.GL;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

/** Native uniform value and program-ownership checks for shipping and partial shaders. */
@EnabledIfSystemProperty(named = "openggf.shaderNative", matches = "true")
class TestTilemapUniformLifetimeNative {
    @Test
    void uniformWritesSurviveRepeatedCachingAndProgramReplacement() throws Exception {
        assertTrue(glfwInit());
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        long window = glfwCreateWindow(32, 32, "UniformCache", 0, 0);
        assertNotEquals(0, window);
        TilemapShaderProgram shader = null;
        TilemapShaderProgram replacement = null;
        int firstProgram = 0;
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            shader = new TilemapShaderProgram("shaders/shader_tilemap.glsl");
            shader.cacheUniformLocations();
            verifyWorldOffset(shader, 73.0f, -19.0f);
            shader.cacheUniformLocations();
            verifyWorldOffset(shader, -31.0f, 47.0f);

            shader.setProgramId(shader.getProgramId());
            shader.cacheUniformLocations();
            verifyWorldOffset(shader, 17.0f, 23.0f);

            firstProgram = shader.getProgramId();
            replacement = new TilemapShaderProgram("shaders/tilemap_uniform_cache_contract.glsl");
            int nextProgram = replacement.getProgramId();
            assertNotEquals(firstProgram, nextProgram);
            shader.setProgramId(nextProgram);
            replacement.setProgramId(0); // Transfer ownership to the tested wrapper.
            shader.cacheUniformLocations();
            verifyWorldOffset(shader, -59.0f, 83.0f);
            shader.cacheUniformLocations();
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            glUseProgram(0);
            if (shader != null) shader.cleanup();
            if (replacement != null) replacement.cleanup();
            if (firstProgram != 0) glDeleteProgram(firstProgram);
            GL.setCapabilities(null);
            glfwMakeContextCurrent(0);
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    private static void verifyWorldOffset(TilemapShaderProgram shader, float x, float y) {
        shader.use();
        shader.setWorldOffset(x, y);
        int program = shader.getProgramId();
        int xLocation = glGetUniformLocation(program, "WorldOffsetX");
        int yLocation = glGetUniformLocation(program, "WorldOffsetY");
        assertTrue(xLocation >= 0 && yLocation >= 0);
        assertEquals(x, glGetUniformf(program, xLocation));
        assertEquals(y, glGetUniformf(program, yLocation));
    }

}
