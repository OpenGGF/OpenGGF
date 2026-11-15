package com.openggf.level.objects;

import com.openggf.physics.Direction;
import com.openggf.physics.TerrainCheckResult;

/**
 * A solid object that stands where a level's art shows a wall and its terrain has none.
 *
 * <p>No original game puts a climber in front of one. Sonic 1 builds Green Hill's cliff faces from
 * objects and has nobody who climbs; Sonic 3 &amp; Knuckles' walls are all terrain, which is the only
 * thing Knuckles' grab and climb read. The engine adds these boxes to what those probes find, so a
 * donated Knuckles can climb them. This is the engine's own rule, not an original routine: see
 * docs/status/known-discrepancies.md, "Knuckles Climbs Sonic 1 Edge Walls".
 */
public interface ClimbableWall {
    /** How far a terrain probe sees past its own point: FindFloor and FindWall read two tiles. */
    int PROBE_REACH = 0x20;

    /** The pixels a climber's probes treat as wall, or null while this object is not one. */
    Box climbableWall();

    /** Solid pixels in level space; all four bounds are inclusive. */
    record Box(int left, int top, int right, int bottom) {
        /**
         * The distance a terrain probe would report from {@code (x, y)} had these pixels been terrain:
         * zero on the last free pixel before the box, negative by the depth inside it.
         *
         * @return the distance, or {@link TerrainCheckResult#NO_COLLISION} when the probe's line misses
         *         the box, starts past it, or the box lies beyond {@link #PROBE_REACH}
         */
        public int distance(Direction direction, int x, int y) {
            boolean horizontal = direction == Direction.LEFT || direction == Direction.RIGHT;
            if (horizontal ? y < top || y > bottom : x < left || x > right) {
                return TerrainCheckResult.NO_COLLISION;
            }
            int distance = switch (direction) {
                case RIGHT -> x > right ? PROBE_REACH : left - x - 1;
                case LEFT -> x < left ? PROBE_REACH : x - right - 1;
                case DOWN -> y > bottom ? PROBE_REACH : top - y - 1;
                case UP -> y < top ? PROBE_REACH : y - bottom - 1;
            };
            return distance < PROBE_REACH ? distance : TerrainCheckResult.NO_COLLISION;
        }
    }
}
