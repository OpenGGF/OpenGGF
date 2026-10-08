package com.openggf.game;

/** Engine-internal native state writes; this bridge is outside the Mod API surface. */
public final class NativeGameStateOps {
    private NativeGameStateOps() { }

    /** Clear the existing captured collection word without changing other session state. */
    public static void clearSpecialRingCollection(GameStateManager state) {
        state.clearSpecialRingCollection();
    }
}
