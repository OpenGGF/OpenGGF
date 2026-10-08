package com.openggf.graphics;

import java.util.Objects;

/** Engine bootstrap bridge; creators read the projection through GraphicsManager. */
public final class GraphicsProjectionAccess {
    private GraphicsProjectionAccess() { }
    public static void install(GraphicsManager graphics, RenderProjection projection) {
        ClassLoader caller=com.openggf.util.EngineCallerAccess
                .callerOutside(GraphicsProjectionAccess.class).getClassLoader();
        if (caller!=GraphicsProjectionAccess.class.getClassLoader()
                || projection!=null && projection.getClass().getClassLoader()!=GraphicsProjectionAccess.class.getClassLoader())
            throw new SecurityException("Rendering projection installation belongs to the engine host");
        Objects.requireNonNull(graphics,"graphics").setProjectionSource(projection);
    }
}
