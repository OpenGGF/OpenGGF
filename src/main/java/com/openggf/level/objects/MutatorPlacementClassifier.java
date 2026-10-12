package com.openggf.level.objects;

import com.openggf.game.mutators.MonitorContent;

/** Native registries translate numeric ROM identity into engine semantics. */
public interface MutatorPlacementClassifier {
    MonitorContent monitorContent(ObjectSpawn spawn);
    boolean isRingPlacement(ObjectSpawn spawn);
}
