package com.openggf.graphics.shaderlib;

public record DisplayShaderPass(
        String vertexSource,
        String fragmentSource,
        GlslShape shape,
        double scale,
        ScaleType scaleType,
        boolean filterLinear,
        WrapMode wrapMode) {
}
