package com.openggf.physics;

/** Read-only pixel queries for authoring routes; no actor, object routine or service mutation. */
public interface RouteTerrainProbe {
    record Floor(boolean found, int y, int angle) {
        public static Floor missing() { return new Floor(false, 0, 0); }
    }
    Floor floor(int x, int feetY, int collisionPath);
    /** Integer pixel rectangle, inclusive on all sides. */
    boolean clear(int left, int top, int right, int bottom, int collisionPath);
    boolean dynamicCollision();
}
