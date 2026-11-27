package com.openggf.game.internal;

/** Native results waits that advance the parked level's object V-int clock. */
public interface NativeSpecialStageResultsClock {
    /** A wait in the stage's finish loop is armed before the next object pass. */
    boolean awaitsResultsEntryVblank();
}
