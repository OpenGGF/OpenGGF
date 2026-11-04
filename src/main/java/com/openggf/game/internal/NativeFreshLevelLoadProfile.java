package com.openggf.game.internal;

import com.openggf.game.LevelLoadContext;

/** Native Level continuation capability; ordinary decode/restoration uses the immediate profile. */
public interface NativeFreshLevelLoadProfile {
    NativeLevelLoadPlan freshLevelLoadPlan(LevelLoadContext context);
    void serviceLoadZeroRoutineVInt();
    /** Whether this continuation resumes Level on the row of its final results fade wait. */
    default boolean continuesResultsLoadAfterFadeWait() { return false; }
}
