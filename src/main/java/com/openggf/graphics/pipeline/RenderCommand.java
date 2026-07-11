package com.openggf.graphics.pipeline;

/**
 * Record of a render command for order verification.
 */
@com.openggf.game.ModApi
public record RenderCommand(
    RenderPhase phase,
    String component,
    int orderIndex
) {
    public static RenderCommand of(RenderPhase phase, String component, int orderIndex) {
        return new RenderCommand(phase, component, orderIndex);
    }
}
