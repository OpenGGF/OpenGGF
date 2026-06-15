package com.openggf.graphics.shaderlib;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDisplayShaderPresetLoader {

    @TempDir
    Path tempDir;

    @Test
    public void loadsBizHawkCgpByMappingCgReferenceToGlslSibling() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        Path preset = root.resolve("BizHawk/BizScanlines.cgp");
        write(preset, """
                shaders = 1
                shader0 = BizScanlines.cg
                """);
        write(root.resolve("BizHawk/BizScanlines.glsl"), "void main() {}\n");

        DisplayShaderPreset loaded = new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.CGP),
                ShaderPhase.PRESENTATION);

        assertEquals("BizHawk/BizScanlines", loaded.label());
        assertEquals(1, loaded.passes().size());
        assertTrue(loaded.passes().get(0).fragmentSource().contains("void main()"));
    }

    @Test
    public void cgpWithoutGlslSiblingFailsGracefully() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        Path preset = root.resolve("BizHawk/BizScanlines.cgp");
        write(preset, """
                shaders = 1
                shader0 = BizScanlines.cg
                """);

        UnsupportedShaderException error = assertThrows(UnsupportedShaderException.class,
                () -> new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.CGP),
                        ShaderPhase.PRESENTATION));

        assertTrue(error.getMessage().contains("GLSL"));
    }

    @Test
    public void hq2xCgpDefaultsToScenePhase() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        Path preset = root.resolve("BizHawk/hq2x/hq2x.cgp");
        write(preset, """
                shaders = 1
                shader0 = hq2x.cg
                """);
        write(root.resolve("BizHawk/hq2x/hq2x.glsl"), "void main() {}\n");

        DisplayShaderPreset loaded = new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.CGP),
                ShaderPhase.PRESENTATION);

        assertEquals(ShaderPhase.SCENE, loaded.phase());
    }

    @Test
    public void loadsGlslpRelativeShaderSource() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        Path preset = root.resolve("RetroArch/shaders_glsl/scanlines/scanline.glslp");
        write(preset, """
                shaders = 1
                shader0 = shaders/scanline.glsl
                """);
        write(root.resolve("RetroArch/shaders_glsl/scanlines/shaders/scanline.glsl"), "void main() {}\n");

        DisplayShaderPreset loaded = new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.GLSLP),
                ShaderPhase.FINAL);

        assertEquals(ShaderPhase.FINAL, loaded.phase());
        assertEquals("void main() {}\n", loaded.passes().get(0).fragmentSource());
    }

    @Test
    public void glslpRejectsUnsupportedSlangReference() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        Path preset = root.resolve("RetroArch/shaders_slang/crt/crt.glslp");
        write(preset, """
                shaders = 1
                shader0 = crt.slang
                """);

        UnsupportedShaderException error = assertThrows(UnsupportedShaderException.class,
                () -> new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.GLSLP),
                        ShaderPhase.PRESENTATION));

        assertTrue(error.getMessage().contains(".glsl"));
    }

    @Test
    public void glslpRejectsReferenceDirectiveInheritance() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        Path preset = root.resolve("RetroArch/shaders_glsl/crt/child.glslp");
        write(preset, """
                #reference "../base.glslp"
                shaders = 1
                shader0 = pass.glsl
                """);
        write(root.resolve("RetroArch/shaders_glsl/crt/pass.glsl"), "void main() {}\n");

        UnsupportedShaderException error = assertThrows(UnsupportedShaderException.class,
                () -> new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.GLSLP),
                        ShaderPhase.PRESENTATION));

        assertTrue(error.getMessage().contains("inheritance"));
    }

    private static DisplayShaderPresetRef ref(Path root, Path path, DisplayShaderPresetRef.Kind kind) {
        return new DisplayShaderPresetRef(kind, root.relativize(path).toString().replace('\\', '/'), path);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
