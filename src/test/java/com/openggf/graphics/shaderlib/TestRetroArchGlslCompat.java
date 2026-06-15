package com.openggf.graphics.shaderlib;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRetroArchGlslCompat {

    @Test
    public void emits410CoreAndStageDefineBeforeSource() throws Exception {
        String staged = RetroArchGlslCompat.stageSource("#version 120\nvarying vec2 uv;", "FRAGMENT");

        assertTrue(staged.startsWith("#version 410 core\n#define FRAGMENT\n"));
        assertFalse(staged.contains("#version 120"));
    }

    @Test
    public void rejectsVersionsAboveEngineContext() {
        assertThrows(DisplayShaderLoadException.class,
                () -> RetroArchGlslCompat.stageSource("#version 450\nvoid main() {}", "FRAGMENT"));
    }

    @Test
    public void injectsLegacyFragmentPreludeWithoutUniformDeclarations() throws Exception {
        String source = """
                varying vec2 vTexCoord;
                uniform sampler2D Texture;
                void main() {
                    gl_FragColor = texture2D(Texture, vTexCoord);
                }
                """;

        String staged = RetroArchGlslCompat.stageSource(source, "FRAGMENT");

        assertTrue(staged.contains("#define texture2D texture\n"));
        assertTrue(staged.contains("#define varying in\n"));
        assertTrue(staged.contains("out vec4 FragColor;\n"));
        assertTrue(staged.contains("#define gl_FragColor FragColor\n"));
        assertFalse(staged.contains("uniform vec"));
        assertFalse(staged.contains("OutputSize"));
        assertFalse(staged.contains("FrameCount"));
    }

    @Test
    public void injectsLegacyVertexPreludeStageAware() throws Exception {
        String source = """
                attribute vec4 VertexCoord;
                varying vec2 vTex;
                void main() {}
                """;

        String staged = RetroArchGlslCompat.stageSource(source, "VERTEX");

        assertTrue(staged.contains("#define attribute in\n"));
        assertTrue(staged.contains("#define varying out\n"));
    }

    @Test
    public void existingFragmentOutputPreventsFragColorInjection() throws Exception {
        String source = """
                out vec4 SomeColor;
                void main() {
                    SomeColor = vec4(1.0);
                }
                """;

        String staged = RetroArchGlslCompat.stageSource(source, "FRAGMENT");

        assertFalse(staged.contains("out vec4 FragColor;"));
        assertFalse(staged.contains("#define gl_FragColor FragColor"));
    }
}
