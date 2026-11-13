package com.openggf.game.internal;

import com.openggf.game.resources.PlcLifecyclePhase;

/** Native ending loops whose non-object VBlank waits own their clock explicitly. */
public interface NativeEndingVBlank {
    void serviceEndingVBlank(PlcLifecyclePhase phase);
}
