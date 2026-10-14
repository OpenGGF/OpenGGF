package com.openggf.game.internal;

/** Game-owned presentation choice for a specific native level-entry handoff. */
public interface LevelEntryPresentation {
    /** Whether the source and destination palette boundaries use white instead of black. */
    boolean usesWhiteTransition(int destinationZone, int destinationAct);
}
