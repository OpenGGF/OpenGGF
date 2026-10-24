package com.openggf.game.rewind;

import com.openggf.debug.SectionProfiler;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.util.EngineCallerAccess;

import java.util.Objects;

/** Internal package bridge for world-owned registries; not a creator API. */
public final class EngineRewindRegistryFactory {
    private EngineRewindRegistryFactory() { }

    public static RewindRegistry create(Object scope, SectionProfiler profiler,
                                        GameplayFrameController controller) {
        Class<?> caller = EngineCallerAccess.callerOutside(EngineRewindRegistryFactory.class);
        if (caller.getClassLoader() != EngineRewindRegistryFactory.class.getClassLoader()) {
            throw new SecurityException("World rewind registry creation belongs to the engine");
        }
        return new RewindRegistry(profiler, controller, Objects.requireNonNull(scope, "scope"));
    }
}
