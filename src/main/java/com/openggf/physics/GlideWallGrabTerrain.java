package com.openggf.physics;

import com.openggf.level.objects.ClimbableWall;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Terrain fit for Knuckles_BeginClimb / Knuckles_Gliding_HitWall.
 *
 * <p>Every probe here also sees a {@link ClimbableWall}. That part is the engine's own: the original
 * routines read terrain alone, and no level of the games they ship in holds such an object.
 */
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
                    int terrain = result == null ? Integer.MAX_VALUE : result.distance();
                    int wall = climbableWallDistance(sprite, direction, x, y);
                    return wall != TerrainCheckResult.NO_COLLISION && wall < terrain ? wall : terrain;
                });
        if (fit == null) return false;
        NativePositionOps.addXPosPreserveSubpixel(sprite, fit.x());
        NativePositionOps.addYPosPreserveSubpixel(sprite, fit.y());
        return true;
    }

    /** KiS2/S3K GetDistanceFromWall, using FindWall's signed-width state machine. */
    public static TerrainCheckResult distanceFromWall(AbstractPlayableSprite sprite,
                                                       int probeY, boolean facingRight) {
        // GetDistanceFromWall subtracts one before the left radius/mirror entry.
        int xOffset = facingRight ? sprite.getXRadius() : -sprite.getXRadius() - 1;
        return scanWall(sprite, facingRight, xOffset, probeY - sprite.getCentreY());
    }

    /** CheckLeft/RightWallDist use a fixed ten-pixel offset, without the climb's left subtraction. */
    public static TerrainCheckResult glideWallDistance(AbstractPlayableSprite sprite, boolean facingRight) {
        return scanWall(sprite, facingRight, facingRight ? 10 : -10, 0);
    }

    /** Knuckles' collision dispatcher retains Collision_addr and the current d5 across probes. */
    public static TerrainCheckResult glideWallDistance(AbstractPlayableSprite sprite, boolean facingRight,
                                                       int solidityBit, boolean secondaryCollisionPath) {
        GroundSensor sensor = new GroundSensor(sprite, Direction.DOWN, (byte) 0, (byte) 0,
                true, secondaryCollisionPath);
        Direction wall = facingRight ? Direction.RIGHT : Direction.LEFT;
        int xOffset = facingRight ? 10 : -10;
        return withClimbableWalls(sprite, wall, xOffset, 0, sensor.scanWorld(wall,
                (short) xOffset, (short) 0, (short) 0, (short) 0, solidityBit));
    }

    /**
     * Knuckles_Wall_Climb's single vertical probe: up/ledge uses lrb_solid_bit
     * at eight pixels plus signed x_radius; down/floor uses top_solid_bit at
     * nine plus signed x_radius. These are the caller's pre-radius offsets:
     * CheckCeilingDist_WithRadius / sub_F828 then subtract/add EXT.W x_radius.
     * Reverse branches swap world direction, retaining the offsets and bits.
     */
    public static TerrainCheckResult climbVerticalDistance(AbstractPlayableSprite sprite,
                                                            boolean towardsLedge, boolean reversed) {
        boolean upwards = towardsLedge != reversed;
        int offset = ((towardsLedge ? 8 : 9) + (byte) sprite.getXRadius()) * (upwards ? -1 : 1);
        GroundSensor sensor = new GroundSensor(sprite, Direction.DOWN, (byte) 0, (byte) 0, true);
        Direction vertical = upwards ? Direction.UP : Direction.DOWN;
        return withClimbableWalls(sprite, vertical, 0, offset, sensor.scanWorld(vertical,
                (short) 0, (short) offset, (short) 0, (short) 0,
                towardsLedge ? sprite.getLrbSolidBit() : sprite.getTopSolidBit()));
    }

    private static TerrainCheckResult scanWall(AbstractPlayableSprite sprite, boolean facingRight,
                                               int xOffset, int yOffset) {
        GroundSensor sensor = new GroundSensor(sprite, Direction.DOWN, (byte) 0, (byte) 0, true);
        Direction wall = facingRight ? Direction.RIGHT : Direction.LEFT;
        // Empty extensions retain the native finite distance; signed widths can return exact contact.
        return withClimbableWalls(sprite, wall, xOffset, yOffset, sensor.scanWorld(wall,
                (short) xOffset, (short) yOffset, (short) 0, (short) 0, sprite.getLrbSolidBit()));
    }

    /** Copies the terrain result at once (GroundSensor reuses it) and lets a nearer stand-in wall win. */
    private static TerrainCheckResult withClimbableWalls(AbstractPlayableSprite sprite, Direction direction,
                                                         int xOffset, int yOffset, SensorResult terrain) {
        TerrainCheckResult result = terrain == null ? TerrainCheckResult.noCollision()
                : new TerrainCheckResult(terrain.distance(), terrain.angle(), terrain.tileId());
        int wall = climbableWallDistance(sprite, direction, xOffset, yOffset);
        // A flat face: the angle a full block would report to a probe from that side.
        return wall < result.distance()
                ? new TerrainCheckResult(wall, (byte) switch (direction) {
                    case DOWN -> 0x00;
                    case LEFT -> 0x40;
                    case UP -> 0x80;
                    case RIGHT -> 0xC0;
                }, -1)
                : result;
    }

    /**
     * The nearest {@link ClimbableWall} along a probe from the player's centre plus an offset, or
     * {@link TerrainCheckResult#NO_COLLISION}. Collision paths and solidity bits do not apply: the box
     * is solid to its object's own routine whichever path the player is on.
     */
    private static int climbableWallDistance(AbstractPlayableSprite sprite, Direction direction,
                                             int xOffset, int yOffset) {
        var level = sprite.currentLevelManagerIfAvailable();
        var objects = level == null ? null : level.getObjectManager();
        if (objects == null) return TerrainCheckResult.NO_COLLISION;
        int x = (short) (sprite.getCentreX() + xOffset);
        int y = (short) (sprite.getCentreY() + yOffset);
        int nearest = TerrainCheckResult.NO_COLLISION;
        for (ObjectInstance object : objects.getActiveObjects()) {
            if (object instanceof ClimbableWall wall) {
                ClimbableWall.Box box = wall.climbableWall();
                if (box != null) nearest = Math.min(nearest, box.distance(direction, x, y));
            }
        }
        return nearest;
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
