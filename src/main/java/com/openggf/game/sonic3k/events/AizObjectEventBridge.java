package com.openggf.game.sonic3k.events;

/**
 * Narrow bridge for AIZ object code that needs to mutate level-event state.
 */
public interface AizObjectEventBridge {
    void setBossFlag(boolean value);
    void setEventsFg5(boolean value);
    void triggerScreenShake(int frames);
    void onBattleshipComplete();
    void onBossSmallComplete();
    boolean isFireTransitionActive();
    boolean isAct2TransitionRequested();

    /** ROM {@code Events_routine_fg == $C}: AIZ2SE_EndRefresh after the battleship leaves. */
    default boolean isAiz2ShipEndRefreshActive() {
        return false;
    }
}
