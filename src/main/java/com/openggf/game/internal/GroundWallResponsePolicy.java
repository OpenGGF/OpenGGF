package com.openggf.game.internal;

/** Native zone-owned adjustment of the ground-wall response angle, after a negative probe. */
public interface GroundWallResponsePolicy {
    /** Returns the byte angle used for response-quadrant rounding; never changes the terrain probe. */
    int groundWallResponseAngle(int rotatedAngle);
}
