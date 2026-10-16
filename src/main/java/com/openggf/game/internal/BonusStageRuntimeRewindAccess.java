package com.openggf.game.internal;

import com.openggf.game.BonusStageProvider;
import com.openggf.game.rewind.RewindRegistry;

/** Registers the admitted native lifecycle owner without a game/zone test in the session. */
public final class BonusStageRuntimeRewindAccess {
    public static final String KEY = "bonus-stage-runtime";
    private BonusStageRuntimeRewindAccess() { }
    public static void register(RewindRegistry registry, BonusStageProvider provider) {
        unregister(registry);
        if (provider != null && provider.supportsRewind() && provider instanceof NativeBonusRuntimeRewindOwner owner) {
            registry.register(owner.bonusRuntimeRewindAdapter());
            registry.registerPostRestoreCallback(KEY, owner::afterBonusRuntimeRewindRestore);
        }
    }
    public static void unregister(RewindRegistry registry) {
        registry.deregister(KEY);
        registry.deregisterPostRestoreCallback(KEY);
    }
}
