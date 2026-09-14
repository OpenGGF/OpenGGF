package com.openggf.game.titlecard;

/** Internal runtime hook for a title loop whose exit test follows its object pass. */
public interface TitleCardLoopTail {
    /** Completes the current locked iteration without advancing another VBlank. */
    void completeLockedIteration();
}
