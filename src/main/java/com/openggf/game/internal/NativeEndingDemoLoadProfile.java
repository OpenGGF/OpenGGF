package com.openggf.game.internal;

import com.openggf.game.EndingProvider;

/** Binds the actual ending caller only while it prepares its production level load. */
public interface NativeEndingDemoLoadProfile {
    NativeLevelLoadCallerScope beginEndingDemoLoad(EndingProvider owner);
    void completeDemoPresentationPlcs();

}
