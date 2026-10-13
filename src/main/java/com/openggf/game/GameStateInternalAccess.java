package com.openggf.game;

import java.util.function.BooleanSupplier;
import com.openggf.util.EngineCallerAccess;

/** Trusted binding only; creators cannot acquire or install gravity authority. */
public final class GameStateInternalAccess {
    private GameStateInternalAccess() { }
    public static void bindSignedGravity(GameStateManager manager, BooleanSupplier contribution) {
        if (EngineCallerAccess.callerOutside(GameStateInternalAccess.class).getClassLoader()
                != GameStateInternalAccess.class.getClassLoader()) throw new SecurityException("Engine binding only");
        manager.signedGravityContribution = contribution;
    }

    /** Native flag writers compare/toggle native state, never the composed temporary sign. */
    public static boolean nativeReverseGravity(GameStateManager manager) {
        if (EngineCallerAccess.callerOutside(GameStateInternalAccess.class).getClassLoader()
                != GameStateInternalAccess.class.getClassLoader()) throw new SecurityException("Engine flag access only");
        return manager.nativeReverseGravityActive();
    }
}
