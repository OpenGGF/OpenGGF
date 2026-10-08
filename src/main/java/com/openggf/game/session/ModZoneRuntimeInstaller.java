package com.openggf.game.session;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.util.EngineCallerAccess;
import java.util.List;

/** Internal level-load bridge; creator identity is established before any adapter reaches this port. */
public final class ModZoneRuntimeInstaller {
    private ModZoneRuntimeInstaller() { }
    public static void install(WorldSession expectedSession, List<RewindSnapshottable<?>> adapters) {
        Class<?> caller = EngineCallerAccess.callerOutside(ModZoneRuntimeInstaller.class);
        if (caller.getClassLoader() != ModZoneRuntimeInstaller.class.getClassLoader())
            throw new SecurityException("Zone rewind installation belongs to the engine load lifecycle");
        GameplayModeContext mode = SessionManager.getCurrentGameplayMode();
        if (mode != null && mode.getWorldSession() == expectedSession) mode.installContributedZoneAdapters(adapters);
    }
}
