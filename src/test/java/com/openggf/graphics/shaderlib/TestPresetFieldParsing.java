package com.openggf.graphics.shaderlib;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPresetFieldParsing {

    @TempDir
    Path tempDir;

    @Test
    public void parsesCoreCgpFields() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        write(root.resolve("crt/pass0.glsl"), "void main() {}\n");
        write(root.resolve("crt/pass1.glsl"), "void main() {}\n");
        Path preset = root.resolve("crt/two-pass.cgp");
        write(preset, """
                shaders = 2
                shader0 = pass0.glsl
                scale0 = 2
                shader1 = pass1.glsl
                scale1 = 3
                scale_type1 = viewport
                filter_linear1 = true
                wrap_mode1 = clamp_to_border
                """);

        DisplayShaderPreset loaded = new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.CGP),
                ShaderPhase.PRESENTATION);

        assertEquals("crt/two-pass", loaded.label());
        assertEquals(ShaderPhase.PRESENTATION, loaded.phase());
        assertEquals(2, loaded.passes().size());
        assertEquals(2, loaded.passes().get(0).scale());
        assertEquals(ScaleType.SOURCE, loaded.passes().get(0).scaleType());
        assertFalse(loaded.passes().get(0).filterLinear());
        assertEquals(WrapMode.CLAMP_TO_EDGE, loaded.passes().get(0).wrapMode());
        assertEquals(3, loaded.passes().get(1).scale());
        assertEquals(ScaleType.VIEWPORT, loaded.passes().get(1).scaleType());
        assertTrue(loaded.passes().get(1).filterLinear());
        assertEquals(WrapMode.CLAMP_TO_BORDER, loaded.passes().get(1).wrapMode());
    }

    @Test
    public void missingScaleTypeDefaultsToSource() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        write(root.resolve("plain.glsl"), "void main() {}\n");
        Path preset = root.resolve("plain.glslp");
        write(preset, """
                shaders = 1
                shader0 = plain.glsl
                scale0 = 4
                """);

        DisplayShaderPreset loaded = new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.GLSLP),
                ShaderPhase.FINAL);

        assertEquals(ScaleType.SOURCE, loaded.passes().get(0).scaleType());
    }

    @Test
    public void parsesGlslpWithSameCoreFields() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        write(root.resolve("retro/shaders/pass0.glsl"), "void main() {}\n");
        write(root.resolve("retro/shaders/pass1.glsl"), "void main() {}\n");
        Path preset = root.resolve("retro/two-pass.glslp");
        write(preset, """
                shaders = 2
                shader0 = shaders/pass0.glsl
                scale0 = 2
                shader1 = shaders/pass1.glsl
                scale1 = 3
                scale_type1 = viewport
                filter_linear1 = true
                wrap_mode1 = repeat
                """);

        DisplayShaderPreset loaded = new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.GLSLP),
                ShaderPhase.PRESENTATION);

        assertEquals(2, loaded.passes().size());
        assertEquals(ScaleType.SOURCE, loaded.passes().get(0).scaleType());
        assertFalse(loaded.passes().get(0).filterLinear());
        assertEquals(ScaleType.VIEWPORT, loaded.passes().get(1).scaleType());
        assertTrue(loaded.passes().get(1).filterLinear());
        assertEquals(WrapMode.REPEAT, loaded.passes().get(1).wrapMode());
    }

    private static DisplayShaderPresetRef ref(Path root, Path path, DisplayShaderPresetRef.Kind kind) {
        return new DisplayShaderPresetRef(kind, root.relativize(path).toString().replace('\\', '/'), path);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
