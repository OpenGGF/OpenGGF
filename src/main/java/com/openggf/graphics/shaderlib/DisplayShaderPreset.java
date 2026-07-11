package com.openggf.graphics.shaderlib;

import java.util.List;

@com.openggf.game.ModApi
public record DisplayShaderPreset(String label, ShaderPhase phase, List<DisplayShaderPass> passes) {
    public DisplayShaderPreset {
        passes = List.copyOf(passes);
    }
}
