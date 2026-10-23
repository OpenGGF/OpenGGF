package com.openggf.game.session;

import com.openggf.game.resources.PlcLifecycleService;

/** Engine-only factory; opening a world must not resolve ROM or submit work. */
@FunctionalInterface
public interface WorldPlcLifecycleFactory {
    PlcLifecycleService open(WorldSession world);
}
