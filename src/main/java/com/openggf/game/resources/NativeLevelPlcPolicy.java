package com.openggf.game.resources;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.LevelLoadContext;

/** Authoritative additive runtime profiles prevent native ROM header lookup for authored levels. */
public final class NativeLevelPlcPolicy {
    private NativeLevelPlcPolicy() { }
    public static boolean allows(LevelLoadContext context) {
        GameModule module=context.getGameModule()!=null ? context.getGameModule() : GameServices.module();
        var contribution=module.getZoneRegistry().modZoneRuntimeContribution(context.getLevelIndex());
        return contribution==null || contribution.runtimeProfile().plcLoads();
    }
    public static boolean allowsCurrent() {
        var level=GameServices.levelOrNull();
        if(level==null) return true;
        var registry=GameServices.module().getZoneRegistry();
        var acts=registry.getLevelDataForZone(level.getCurrentZone());
        int act=level.getCurrentAct();
        if(act<0 || act>=acts.size()) return true;
        var contribution=registry.modZoneRuntimeContribution(acts.get(act).levelIndex());
        return contribution==null || contribution.runtimeProfile().plcLoads();
    }
}
