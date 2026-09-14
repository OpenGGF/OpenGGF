package com.openggf.physics;

import com.openggf.level.LevelManager;

/**
 * Gameplay-scoped authority for ROM background-plane collision semantics.
 * Collision routines keep their caller coordinates immutable and request an
 * optional layer-1 probe translated by Camera_X_diff/Camera_Y_diff.
 */
@FunctionalInterface
@com.openggf.game.ModApi
public interface BackgroundPlaneCollisionProvider {
    BackgroundPlaneCollisionProvider FOREGROUND_ONLY = () -> State.INACTIVE;

    State state();

    default int backgroundX(State current, int worldX, Direction direction) {
        return direction == Direction.LEFT
                ? translateLeftWallX(worldX, current.cameraDiffX())
                : worldX - current.cameraDiffX();
    }

    default int backgroundY(State current, int worldY) {
        return worldY - current.cameraDiffY();
    }

    /**
     * CheckLeftWallDist complements d3 before FindWall translates it. Our callers
     * supply an uncomplemented world point; GroundSensor mirrors the tile metric
     * internally. Translate that world point once, preserving the ROM word wrap.
     */
    private static int translateLeftWallX(int worldX, int cameraDiffX) {
        return (short) (worldX - cameraDiffX);
    }

    default State state(LevelManager probeLevel) {
        return state();
    }

    /** ROM keeps the BG result when its signed distance is equal or nearer. */
    default int selectNearerDistance(int foregroundDistance, int backgroundDistance) {
        return backgroundDistance <= foregroundDistance ? backgroundDistance : foregroundDistance;
    }

    @com.openggf.game.ModApi
    record State(boolean active, int cameraDiffX, int cameraDiffY) {
        public static final State INACTIVE = new State(false, 0, 0);
    }

}
