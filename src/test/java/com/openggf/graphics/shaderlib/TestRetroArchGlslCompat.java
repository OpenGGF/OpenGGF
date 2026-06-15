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
    public void emitsCompatStageAliasesBeforeCompatGuardedBlocks() throws Exception {
        String fragment = RetroArchGlslCompat.stageSource("""
                #ifdef COMPAT_FRAGMENT
                vec4 enabledFragmentBlock;
                #endif
                """, "FRAGMENT");
        String vertex = RetroArchGlslCompat.stageSource("""
                #ifdef COMPAT_VERTEX
                vec4 enabledVertexBlock;
                #endif
                """, "VERTEX");

        assertTrue(fragment.contains("#define COMPAT_FRAGMENT\n"));
        assertTrue(fragment.indexOf("#define COMPAT_FRAGMENT") < fragment.indexOf("#ifdef COMPAT_FRAGMENT"));
        assertTrue(vertex.contains("#define COMPAT_VERTEX\n"));
        assertTrue(vertex.indexOf("#define COMPAT_VERTEX") < vertex.indexOf("#ifdef COMPAT_VERTEX"));
    }

    @Test
    public void injectsFragmentOutputAfterLeadingExtensionDirectives() throws Exception {
        String source = """
                #version 120
                #extension GL_ARB_gpu_shader5 : enable
                void main() {
                    gl_FragColor = vec4(1.0);
                }
                """;

        String staged = RetroArchGlslCompat.stageSource(source, "FRAGMENT");

        assertFalse(staged.contains("#version 120"));
        assertTrue(staged.indexOf("#extension GL_ARB_gpu_shader5 : enable") < staged.indexOf("out vec4 FragColor;"));
        assertTrue(staged.indexOf("out vec4 FragColor;") < staged.indexOf("void main()"));
    }

    @Test
    public void detectsTexture2DWithWhitespaceBeforeCall() throws Exception {
        String staged = RetroArchGlslCompat.stageSource("""
                void main() {
                    gl_FragColor = texture2D (Texture, uv);
                }
                """, "FRAGMENT");

        assertTrue(staged.contains("#define texture2D texture\n"));
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

    @Test
    public void preservesRetroArchCompatMacrosInBody() throws Exception {
        String source = """
                #define COMPAT_TEXTURE texture
                #ifdef COMPAT_FRAGMENT
                COMPAT_TEXTURE(Texture, vTexCoord);
                #endif
                """;

        String staged = RetroArchGlslCompat.stageSource(source, "FRAGMENT");
        String body = staged.substring("#version 410 core\n#define FRAGMENT\n".length());

        assertTrue(staged.startsWith("#version 410 core\n#define FRAGMENT\n"));
        assertTrue(body.contains("#define COMPAT_TEXTURE texture"));
        assertTrue(body.contains("#ifdef COMPAT_FRAGMENT"));
        assertTrue(body.contains("COMPAT_TEXTURE(Texture, vTexCoord);"));
    }
}
