package com.openggf.tests.route;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Ordinary-input steering primitives for headless route controllers. Every
 * method returns a pad mask or a frame budget built only from the player's
 * live state; none reads route, frame or fixture identity.
 *
 * <p>Inputs: the playable sprite's centre, speeds and effective acceleration
 * tables. Origin: extracted from the FBZ2 route controller
 * ({@code TestFbzAct2TraversalPreboss}) in commit 610464952.
 */
public final class RouteSteering {
    /**
     * Frames of margin {@link #ordinaryRightCrossingBudget} adds over the
     * modelled crossing frame: the route compares the budget against a live
     * runway before stepping the frame it decides on, and the object's touch
     * response runs after the movement that carries P1 across, so the
     * modelled frame alone would be one frame optimistic on each side.
     */
    public static final int CROSSING_MARGIN_FRAMES = 2;

    private RouteSteering() { }

    /** Hold LEFT or RIGHT toward {@code targetX} from a centre {@code x}; neutral inside the tolerance. */
    public static int steerMask(int x, int targetX, int tolerance) {
        if (x < targetX - tolerance) return AbstractPlayableSprite.INPUT_RIGHT;
        if (x > targetX + tolerance) return AbstractPlayableSprite.INPUT_LEFT;
        return 0;
    }

    /**
     * {@link #steerMask(int, int, int)} on the centre projected eight frames
     * ahead by the current x speed, so retained inertia brakes before the
     * target rather than past it.
     */
    public static int steerMask(AbstractPlayableSprite player, int targetX, int tolerance) {
        int x = player.getCentreX() & 0xFFFF;
        int projectedX = x + ((player.getXSpeed() * 8) >> 8);
        return steerMask(projectedX, targetX, tolerance);
    }

    /**
     * Walk toward {@code targetX} without exceeding {@code speedCap} ground
     * speed; inside the tolerance, brake against any speed above $80.
     */
    public static int walkMask(AbstractPlayableSprite player, int targetX, int tolerance,
                               int speedCap) {
        int x = player.getCentreX() & 0xFFFF;
        int groundSpeed = player.getGSpeed();
        int delta = targetX - x;
        if (Math.abs(delta) > tolerance) {
            if (Math.abs(groundSpeed) >= speedCap) return 0;
            return delta > 0 ? AbstractPlayableSprite.INPUT_RIGHT
                    : AbstractPlayableSprite.INPUT_LEFT;
        }
        if (Math.abs(groundSpeed) <= 0x80) return 0;
        return groundSpeed > 0 ? AbstractPlayableSprite.INPUT_LEFT
                : AbstractPlayableSprite.INPUT_RIGHT;
    }

    /**
     * Pixels needed to stop from the current positive ground speed under the
     * player's effective run deceleration, rounded up.
     */
    public static int ordinaryBrakeDistancePixels(AbstractPlayableSprite player) {
        int speed = Math.max(0, player.getGSpeed());
        int deceleration = Math.max(1, player.getEffectiveRunDecel() & 0xFFFF);
        long frames = (speed + (long) deceleration - 1) / deceleration;
        long fixedDistance = frames
                * (2L * speed - (frames - 1) * deceleration) / 2;
        return (int) ((fixedDistance + 0xFF) >> 8);
    }

    /**
     * Frames an ordinary RIGHT run needs to cover {@code distancePixels} from
     * the player's current ground speed, modelled conservatively: the current
     * speed is integrated before acceleration, an above-maximum speed is
     * clamped, and a negative speed decelerates to rest first. The result
     * carries {@link #CROSSING_MARGIN_FRAMES} over the modelled crossing
     * frame (a non-positive distance is already crossed and returns the
     * margin alone). Returns {@code simulationLimit} when the distance is not
     * reached within {@code simulationLimit - CROSSING_MARGIN_FRAMES} frames.
     */
    public static int ordinaryRightCrossingBudget(
            AbstractPlayableSprite player, int distancePixels, int simulationLimit) {
        if (distancePixels <= 0) return CROSSING_MARGIN_FRAMES;
        long requiredFixedDistance = (long) distancePixels << 8;
        long travelledFixed = 0;
        int acceleration = Math.max(1, player.getRunAccel() & 0xFFFF);
        int deceleration = Math.max(1, player.getRunDecel() & 0xFFFF);
        int maximum = Math.max(1, player.getMax() & 0xFFFF);
        int speed = Math.min(player.getGSpeed(), maximum);
        int frameLimit = simulationLimit - CROSSING_MARGIN_FRAMES;
        for (int frame = 1; frame <= frameLimit; frame++) {
            travelledFixed += speed;
            if (travelledFixed >= requiredFixedDistance) {
                return frame + CROSSING_MARGIN_FRAMES;
            }
            if (speed < 0) {
                speed = Math.min(0, speed + deceleration);
            } else {
                speed = Math.min(maximum, speed + acceleration);
            }
        }
        return simulationLimit;
    }
}
