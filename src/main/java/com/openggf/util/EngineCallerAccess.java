package com.openggf.util;

import java.util.Objects;
import java.util.Optional;

/** Internal caller provenance; it does not locate a runtime or grant a reported owner authority. */
public final class EngineCallerAccess {
    private static final StackWalker CALLERS = com.openggf.game.session.EngineCallerInspectionBootstrap.create();

    private EngineCallerAccess() { }

    /** Finds the actual caller beyond one engine-owned intake boundary, including hidden method references. */
    public static Class<?> callerOutside(Class<?> boundary) {
        return inspect(boundary).orElseThrow();
    }

    /** Retains the private-registry construction fallback when no external application frame exists. */
    public static Class<?> callerOutsideOrBoundary(Class<?> boundary) {
        return inspect(boundary).orElse(boundary);
    }

    private static Optional<Class<?>> inspect(Class<?> boundary) {
        Objects.requireNonNull(boundary, "boundary");
        if (boundary.getClassLoader() != EngineCallerAccess.class.getClassLoader())
            throw new SecurityException("Caller inspection boundaries belong to the engine");
        return CALLERS.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type != EngineCallerAccess.class && type != boundary && type.getClassLoader() != null)
                .findFirst());
    }
}
