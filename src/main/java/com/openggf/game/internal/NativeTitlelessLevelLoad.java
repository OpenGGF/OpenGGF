package com.openggf.game.internal;

/** Production continuation whose counted entry waits have no title-card object owner. */
public interface NativeTitlelessLevelLoad {
    /** A ROM game-mode switch returns to its dispatcher before the next wait. */
    boolean switchesAtLoopTail(int zone, int act);
    default NativeLevelLoadCallerScope beginModeSwitchLoad() { return NativeLevelLoadCallerScope.NONE; }
    default boolean creditsSwitchesAtLoopTail() { return false; }
    boolean titlelessLoadSelected();
    boolean readyForEntryVBlank();
    void entryVBlankServiced();
    boolean ownsEntryReveal();
}
