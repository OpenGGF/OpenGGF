package com.openggf.game.internal;

import com.openggf.game.resources.PlcLifecyclePhase;

/** Game-owned VBlank handlers that upload the prepared gameplay sprite table. */
public interface SpriteTablePublication {
    boolean publishesSpriteTable(PlcLifecyclePhase phase);
    boolean updatesHudCounters(PlcLifecyclePhase phase);
    boolean advancesHudTimer(PlcLifecyclePhase phase);
}
