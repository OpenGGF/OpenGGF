package com.openggf.graphics;

/** Engine-only logical page geometry; outside the creator API. */
public final class ParallaxShaderInternalAccess {
    private ParallaxShaderInternalAccess() { }
    public static void setLogicalHeight(ParallaxShaderProgram shader, int height) {
        shader.setLogicalHeight(height);
    }
}
