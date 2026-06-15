package com.openggf.graphics.shaderlib;

import java.util.Map;

public record DisplayShaderPass(
        String vertexSource,
        String fragmentSource,
        GlslShape shape,
        double scale,
        ScaleType scaleType,
        boolean filterLinear,
        WrapMode wrapMode,
        Map<String, Float> parameterValues) {

    public DisplayShaderPass {
        parameterValues = parameterValues == null || parameterValues.isEmpty()
                ? Map.of()
                : Map.copyOf(parameterValues);
    }

    public DisplayShaderPass(String vertexSource, String fragmentSource, GlslShape shape,
                             double scale, ScaleType scaleType, boolean filterLinear, WrapMode wrapMode) {
        this(vertexSource, fragmentSource, shape, scale, scaleType, filterLinear, wrapMode, Map.of());
    }
}
