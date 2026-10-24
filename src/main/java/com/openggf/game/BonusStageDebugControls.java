package com.openggf.game;

/** Internal stage-owned diagnostic controls, separate from the pinned bonus provider API. */
public interface BonusStageDebugControls {
    void cycleObjectRenderFilter();
    void cycleObjectSourceFilter();
}
