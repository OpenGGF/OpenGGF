package com.openggf.game.internal;

/** Results entry loop which drains submitted resources before installing its sprite owner. */
public interface ResultsResourcePreparation {
    boolean isPreparingResults();
    void finishResultsPreparationIteration();
}
