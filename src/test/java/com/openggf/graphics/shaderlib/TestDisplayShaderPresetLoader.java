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
    public void offLoadsAsEmptyPassthroughPreset() throws Exception {
        DisplayShaderPreset loaded = new DisplayShaderPresetLoader().load(DisplayShaderPresetRef.OFF,
                ShaderPhase.FINAL);

        assertEquals("Off", loaded.label());
        assertEquals(ShaderPhase.FINAL, loaded.phase());
        assertEquals(0, loaded.passes().size());
    }

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

        DisplayShaderLoadException error = assertThrows(DisplayShaderLoadException.class,
                () -> new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.CGP),
                        ShaderPhase.PRESENTATION));

        assertTrue(error.getMessage().contains("GLSL"));
    }

    @Test
    public void hq2xCgpDefaultsToScenePhase() throws Exception {
        assertScalerCgpDefaultsToScene("BizHawk/hq2x/hq2x.cgp", "hq2x.cg", "hq2x.glsl");
    }

    @Test
    public void hq4xCgpDefaultsToScenePhase() throws Exception {
        assertScalerCgpDefaultsToScene("BizHawk/hq4x/hq4x.cgp", "hq4x.cg", "hq4x.glsl");
    }

    @Test
    public void scalefxCgpDefaultsToScenePhase() throws Exception {
        assertScalerCgpDefaultsToScene("RetroArch/shaders_glsl/scalefx/scalefx.glslp", "scalefx.glsl", "scalefx.glsl");
    }

    @Test
    public void omniscaleCgpDefaultsToScenePhase() throws Exception {
        assertScalerCgpDefaultsToScene("RetroArch/shaders_glsl/omniscale/omniscale.glslp", "omniscale.glsl", "omniscale.glsl");
    }

    @Test
    public void xbrzCgpDefaultsToScenePhase() throws Exception {
        assertScalerCgpDefaultsToScene("RetroArch/shaders_glsl/xbrz/xbrz.glslp", "xbrz.glsl", "xbrz.glsl");
    }

    private void assertScalerCgpDefaultsToScene(String presetRelativePath, String shaderRef, String sourceFileName)
            throws Exception {
        Path root = tempDir.resolve("display-shaders");
        Path preset = root.resolve(presetRelativePath);
        write(preset, """
                shaders = 1
                shader0 = %s
                """.formatted(shaderRef));
        write(preset.getParent().resolve(sourceFileName), "void main() {}\n");
        DisplayShaderPresetRef.Kind kind = presetRelativePath.endsWith(".cgp")
                ? DisplayShaderPresetRef.Kind.CGP
                : DisplayShaderPresetRef.Kind.GLSLP;

        DisplayShaderPreset loaded = new DisplayShaderPresetLoader().load(ref(root, preset, kind),
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

        DisplayShaderLoadException error = assertThrows(DisplayShaderLoadException.class,
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

        DisplayShaderLoadException error = assertThrows(DisplayShaderLoadException.class,
                () -> new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.GLSLP),
                        ShaderPhase.PRESENTATION));

        assertTrue(error.getMessage().contains("inheritance"));
    }

    @Test
    public void cgpRejectsReferenceAssignmentInheritance() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        Path preset = root.resolve("BizHawk/child.cgp");
        write(preset, """
                reference = base.cgp
                shaders = 1
                shader0 = child.cg
                """);
        write(root.resolve("BizHawk/child.glsl"), "void main() {}\n");

        DisplayShaderLoadException error = assertThrows(DisplayShaderLoadException.class,
                () -> new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.CGP),
                        ShaderPhase.PRESENTATION));

        assertTrue(error.getMessage().contains("unsupported"));
    }

    @Test
    public void cgpRejectsReferenceDirectiveInheritance() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        Path preset = root.resolve("BizHawk/child.cgp");
        write(preset, """
                #reference "base.cgp"
                shaders = 1
                shader0 = child.cg
                """);
        write(root.resolve("BizHawk/child.glsl"), "void main() {}\n");

        DisplayShaderLoadException error = assertThrows(DisplayShaderLoadException.class,
                () -> new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.CGP),
                        ShaderPhase.PRESENTATION));

        assertTrue(error.getMessage().contains("inheritance"));
    }

    @Test
    public void cgpRejectsIncludeDirective() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        Path preset = root.resolve("BizHawk/child.cgp");
        write(preset, """
                #include "base.cgp"
                shaders = 1
                shader0 = child.cg
                """);
        write(root.resolve("BizHawk/child.glsl"), "void main() {}\n");

        DisplayShaderLoadException error = assertThrows(DisplayShaderLoadException.class,
                () -> new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.CGP),
                        ShaderPhase.PRESENTATION));

        assertTrue(error.getMessage().contains("includes"));
    }

    @Test
    public void glslpRejectsHistoryRuntimeInputInPassSource() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        Path preset = root.resolve("RetroArch/shaders_glsl/crt/history.glslp");
        write(preset, """
                shaders = 1
                shader0 = history.glsl
                """);
        write(root.resolve("RetroArch/shaders_glsl/crt/history.glsl"), """
                #pragma parameter glow "Glow" 0.5 0.0 1.0 0.1
                uniform sampler2D OriginalHistory0;
                void main() {}
                """);

        DisplayShaderLoadException error = assertThrows(DisplayShaderLoadException.class,
                () -> new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.GLSLP),
                        ShaderPhase.PRESENTATION));

        assertTrue(error.getMessage().contains("runtime input"));
    }

    @Test
    public void glslpRejectsLutSamplerInPassSource() throws Exception {
        Path root = tempDir.resolve("display-shaders");
        Path preset = root.resolve("RetroArch/shaders_glsl/crt/lut.glslp");
        write(preset, """
                shaders = 1
                shader0 = lut.glsl
                """);
        write(root.resolve("RetroArch/shaders_glsl/crt/lut.glsl"), """
                uniform sampler2D LUT;
                void main() {}
                """);

        DisplayShaderLoadException error = assertThrows(DisplayShaderLoadException.class,
                () -> new DisplayShaderPresetLoader().load(ref(root, preset, DisplayShaderPresetRef.Kind.GLSLP),
                        ShaderPhase.PRESENTATION));

        assertTrue(error.getMessage().contains("external texture"));
    }

    private static DisplayShaderPresetRef ref(Path root, Path path, DisplayShaderPresetRef.Kind kind) {
        return new DisplayShaderPresetRef(kind, root.relativize(path).toString().replace('\\', '/'), path);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
