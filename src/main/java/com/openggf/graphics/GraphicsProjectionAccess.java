package com.openggf.graphics;

import java.util.Objects;
import java.util.Set;

/** Engine bootstrap bridge; creators read the projection through GraphicsManager. */
public final class GraphicsProjectionAccess {
    private GraphicsProjectionAccess() { }
    public static void install(GraphicsManager graphics, RenderProjection projection) {
        ClassLoader caller=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,
                StackWalker.Option.SHOW_HIDDEN_FRAMES)).walk(frames->frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type->type!=GraphicsProjectionAccess.class && type.getClassLoader()!=null)
                .findFirst().orElseThrow().getClassLoader());
        if (caller!=GraphicsProjectionAccess.class.getClassLoader()
                || projection!=null && projection.getClass().getClassLoader()!=GraphicsProjectionAccess.class.getClassLoader())
            throw new SecurityException("Rendering projection installation belongs to the engine host");
        Objects.requireNonNull(graphics,"graphics").setProjectionSource(projection);
    }
}
