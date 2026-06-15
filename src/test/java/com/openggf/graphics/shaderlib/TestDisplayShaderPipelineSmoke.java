package com.openggf.graphics.shaderlib;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class TestDisplayShaderPipelineSmoke {

    @Test
    public void brokenShaderActivationReturnsFalseAndInactive() {
        try (GlContext ignored = GlContext.open()) {
            DisplayShaderPipeline pipeline = new DisplayShaderPipeline();
            pipeline.resize(32, 32, 32, 32);

            DisplayShaderPreset preset = new DisplayShaderPreset("broken", ShaderPhase.FINAL, List.of(
                    new DisplayShaderPass(null, """
                            uniform sampler2D Texture;
                            void main() {
                                this is not valid glsl
                            }
                            """, GlslShape.FRAGMENT_ONLY, 1, ScaleType.SOURCE, false, WrapMode.CLAMP_TO_EDGE)));

            assertFalse(pipeline.activate(preset));
            assertFalse(pipeline.isActive());
            pipeline.dispose();
        }
    }

    @Test
    public void combinedRetroArchStyleSourceCompilesAndActivates() {
        try (GlContext ignored = GlContext.open()) {
            DisplayShaderPipeline pipeline = new DisplayShaderPipeline();
            pipeline.resize(32, 32, 32, 32);

            String source = """
                    #if defined(VERTEX)
                    attribute vec4 VertexCoord;
                    attribute vec4 TexCoord;
                    attribute vec4 COLOR;
                    varying vec4 vTexCoord;
                    varying vec4 vColor;
                    uniform mat4 MVPMatrix;
                    void main() {
                        gl_Position = MVPMatrix * VertexCoord;
                        vTexCoord = TexCoord;
                        vColor = COLOR;
                    }
                    #elif defined(FRAGMENT)
                    varying vec4 vTexCoord;
                    varying vec4 vColor;
                    uniform sampler2D Texture;
                    uniform vec2 InputSize;
                    uniform vec2 TextureSize;
                    uniform vec2 OutputSize;
                    uniform int FrameCount;
                    void main() {
                        vec2 sizeMix = (InputSize + TextureSize + OutputSize) * 0.0;
                        gl_FragColor = texture2D(Texture, vTexCoord.xy + sizeMix) * vColor + vec4(float(FrameCount) * 0.0);
                    }
                    #endif
                    """;

            DisplayShaderPreset preset = new DisplayShaderPreset("combined", ShaderPhase.FINAL, List.of(
                    new DisplayShaderPass(source, source, GlslShape.COMBINED, 1, ScaleType.SOURCE,
                            false, WrapMode.CLAMP_TO_EDGE)));

            assertTrue(pipeline.activate(preset));
            assertTrue(pipeline.isActive());
            pipeline.dispose();
        }
    }

    @Test
    public void fragmentOnlyPassthroughActivatesAndApplies() {
        try (GlContext ignored = GlContext.open()) {
            DisplayShaderPipeline pipeline = new DisplayShaderPipeline();
            pipeline.resize(32, 32, 32, 32);

            DisplayShaderPreset preset = new DisplayShaderPreset("passthrough", ShaderPhase.FINAL, List.of(
                    new DisplayShaderPass(null, """
                            uniform sampler2D Texture;
                            uniform vec2 InputSize;
                            uniform vec2 TextureSize;
                            uniform vec2 OutputSize;
                            void main() {
                                vec2 uv = gl_FragCoord.xy / OutputSize;
                                gl_FragColor = texture2D(Texture, uv);
                            }
                            """, GlslShape.FRAGMENT_ONLY, 1, ScaleType.SOURCE, false, WrapMode.MIRRORED_REPEAT)));

            assertTrue(pipeline.activate(preset));
            glViewport(0, 0, 32, 32);
            glClearColor(0.25f, 0.5f, 0.75f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);

            pipeline.apply(0, 0, 32, 32, 7);

            assertTrue(pipeline.isActive());
            pipeline.dispose();
        }
    }

    private static final class GlContext implements AutoCloseable {
        private final long window;

        private GlContext(long window) {
            this.window = window;
        }

        static GlContext open() {
            boolean initialized = glfwInit();
            Assumptions.assumeTrue(initialized, "GLFW init failed; skipping GL smoke test");

            long window = 0L;
            try {
                glfwDefaultWindowHints();
                glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
                glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
                glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
                glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
                glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

                window = glfwCreateWindow(64, 64, "DisplayShaderPipelineSmoke", 0L, 0L);
                Assumptions.assumeTrue(window != 0L, "OpenGL 4.1 core context unavailable; skipping GL smoke test");
                glfwMakeContextCurrent(window);
                GL.createCapabilities();
                return new GlContext(window);
            } catch (Throwable t) {
                if (window != 0L) {
                    glfwDestroyWindow(window);
                }
                glfwTerminate();
                Assumptions.assumeTrue(false, "OpenGL context unavailable; skipping GL smoke test: " + t);
                return null;
            }
        }

        @Override
        public void close() {
            glfwMakeContextCurrent(0L);
            if (window != 0L) {
                glfwDestroyWindow(window);
            }
            glfwTerminate();
        }
    }
}
