package com.openggf.route;

/** A ROM placement: the level's layout index plus object id and subtype. */
public record PlacementKey(int zone, int act, int layoutIndex, int objectId, int subtype) {
    public PlacementKey {
        if (layoutIndex < 0) throw new IllegalArgumentException("dynamic objects have no placement");
    }
}
