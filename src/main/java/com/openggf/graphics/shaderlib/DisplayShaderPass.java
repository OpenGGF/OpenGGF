package com.openggf.graphics.shaderlib;

public record DisplayShaderPass(
        String vertexSource,
        String fragmentSource,
        GlslShape shape,
        int scale,
        ScaleType scaleType,
        boolean filterLinear,
        WrapMode wrapMode) {
}
