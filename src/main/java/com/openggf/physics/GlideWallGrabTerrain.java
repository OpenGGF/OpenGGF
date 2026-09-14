package com.openggf.physics;

import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Terrain fit for Knuckles_BeginClimb / Knuckles_Gliding_HitWall. */
public final class GlideWallGrabTerrain {
    private GlideWallGrabTerrain() { }

    /** Aligns a successful grab; a rejected fit leaves position and velocity alone. */
    public static boolean align(AbstractPlayableSprite sprite, boolean facingRight, boolean reverseGravity) {
        GroundSensor sensor = new GroundSensor(sprite, Direction.DOWN, (byte) 0, (byte) 0, true);
        Alignment fit = findAlignment(sprite.getXRadius(), sprite.getYRadius(), facingRight,
                reverseGravity, (direction, x, y) -> {
                    SensorResult result = sensor.scanWorld(direction, (short) x, (short) y,
                            (short) 0, (short) 0, sprite.getLrbSolidBit());
                    // Read immediately: GroundSensor reuses its result between probes.
                    return result == null ? Integer.MAX_VALUE : result.distance();
                });
        if (fit == null) return false;
        NativePositionOps.addXPosPreserveSubpixel(sprite, fit.x());
        NativePositionOps.addYPosPreserveSubpixel(sprite, fit.y());
        return true;
    }

    @FunctionalInterface
    interface Probe { int distance(Direction direction, int x, int y); }
    record Alignment(int x, int y) { }

    static Alignment findAlignment(int xRadius, int yRadius, boolean facingRight,
                                   boolean reverseGravity, Probe probe) {
        // CheckLeft/RightCeilingDist use y_radius horizontally and x_radius
        // vertically. Preserve the retail convention (both are 10 in a glide).
        Direction wall = facingRight ? Direction.RIGHT : Direction.LEFT;
        int side = facingRight ? yRadius : -yRadius;
        int upper = probe.distance(wall, side, -xRadius);
        int lower = probe.distance(wall, side, xRadius);
        if ((upper | lower) == 0) {
            // Only the left exact-fit branch adds one pixel before success.
            return new Alignment(facingRight ? 0 : 1, 0);
        }
        // .checkFloorCommon probes beyond the wall at y-11 using the live
        // lrb_solid_bit, not the top-solid mask. S3K's reverse-gravity branch
        // mirrors Y and the correction. KiS2 retains the normal-gravity path.
        int distance = probe.distance(reverseGravity ? Direction.UP : Direction.DOWN,
                side + (facingRight ? 1 : -1), reverseGravity ? 11 : -11);
        if (distance < 0 || distance >= 12) return null;
        return new Alignment(0, reverseGravity ? -distance : distance);
    }
}
