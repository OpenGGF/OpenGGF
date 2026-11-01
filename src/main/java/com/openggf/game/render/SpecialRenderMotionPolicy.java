package com.openggf.game.render;

/**
 * Engine-only presentation declaration for draws outside the collected sprite table.
 * Queries must be read-only. Permit motion only when the effect draws nothing or
 * consumes the picture's projected coordinates consistently across all its parts.
 * Undeclared effects keep the entire scene exact, including their attached objects.
 */
public interface SpecialRenderMotionPolicy {
    boolean permitsMotion(SpecialRenderEffectContext context);

    static boolean permitsScene(SpecialRenderEffectRegistry registry, SpecialRenderEffectContext context) {
        return registry == null || registry.permitsMotion(context);
    }
}
