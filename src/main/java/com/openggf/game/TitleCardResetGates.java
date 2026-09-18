package com.openggf.game;

/** Engine-internal sentinels for {@link TitleCardProvider#requestLevelGamestateResetAtInLevelDisplay(int, int)}. */
public final class TitleCardResetGates {
    /**
     * {@code additionalDispatches} value selecting the native {@code Obj_TitleCardWait} gate
     * (children arrived and the movement latch consumed) instead of a dispatch count.
     */
    public static final int NATIVE_WAIT_GATE = -1;

    private TitleCardResetGates() {}
}
