package com.openggf.game.rewind;

import com.openggf.architecture.CompositionRoot;
import com.openggf.debug.SectionProfiler;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.util.EngineCallerAccess;

import java.util.Objects;

/**
 * Internal cross-package composition bridge for the gameplay context's world-scoped registry.
 * Only the exact gameplay context may supply the world identity; this is not a creator API.
 */
@CompositionRoot
public final class EngineRewindRegistryFactory {
    private EngineRewindRegistryFactory() { }

    public static RewindRegistry create(Object scope, SectionProfiler profiler,
                                        GameplayFrameController controller) {
        Class<?> caller = EngineCallerAccess.callerOutside(EngineRewindRegistryFactory.class);
        if (caller != GameplayModeContext.class
                || caller.getClassLoader() != EngineRewindRegistryFactory.class.getClassLoader()) {
            throw new SecurityException("World rewind registry creation belongs to GameplayModeContext");
        }
        return new RewindRegistry(profiler, controller, Objects.requireNonNull(scope, "scope"));
    }
}
