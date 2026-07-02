package com.openggf.graphics.shaderlib;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRewindVhsEffectPass {

    @Test
    public void builtInPresetLoadsAndDeclaresRequiredUniforms() throws IOException {
        DisplayShaderPreset preset = RewindVhsEffectPass.builtInPreset();

        assertEquals(1, preset.passes().size(), "VHS effect is a single fragment-only pass");
        DisplayShaderPass pass = preset.passes().get(0);
        assertEquals(GlslShape.FRAGMENT_ONLY, pass.shape());
        assertEquals(ScaleType.VIEWPORT, pass.scaleTypeX(), "must not downsample the captured frame");

        String fragment = pass.fragmentSource();
        assertTrue(fragment.contains("uniform sampler2D Texture"), "missing Texture uniform");
        assertTrue(fragment.contains("uniform vec2 TextureSize"), "missing TextureSize uniform");
        assertTrue(fragment.contains("uniform vec2 OutputSize"), "missing OutputSize uniform");
        assertTrue(fragment.contains("uniform int FrameCount"), "missing FrameCount uniform");
        assertTrue(fragment.contains("uniform float RewindIntensity"), "missing RewindIntensity uniform");
        assertTrue(fragment.contains("uniform float RewindSpeed"), "missing RewindSpeed uniform");
    }
}
