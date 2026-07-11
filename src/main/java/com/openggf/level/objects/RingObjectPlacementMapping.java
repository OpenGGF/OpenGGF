package com.openggf.level.objects;

import com.openggf.level.rings.RingSpawn;

import java.util.List;
import java.util.Map;

/** Supplies the object-backed stage-ring groups used by Sonic 1. */
@com.openggf.game.ModApi
public interface RingObjectPlacementMapping {
    Map<ObjectSpawn, List<RingSpawn>> ringObjectPlacementMapping();
}
