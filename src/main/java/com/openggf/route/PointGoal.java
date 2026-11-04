package com.openggf.route;

import com.openggf.sprites.playable.route.NativeRouteSteering;

import java.util.Objects;

/**
 * Reach a point and settle there, steered by native buttons: the scene-shot goal. The search
 * arrives when P1 is grounded within {@link #ARRIVE_X} by {@link #ARRIVE_Y} pixels of the target
 * and moves no faster than {@link #ARRIVE_SPEED}; the replayed end of the route must then be
 * settled there no faster than {@link #SETTLED_SPEED}. Steering numbers belong to
 * {@link NativeRouteSteering}; these fields only select its settings.
 *
 * @param maxRows rows the route may use, including its settle and facing tail
 */
public record PointGoal(PlayerSlot player, double targetX, double targetY, int maxRows, Start start,
                        NativeRouteSteering.Settings steering, Facing facing, double urgency) {
    public static final int ARRIVE_X = 6;
    public static final int ARRIVE_Y = 12;
    /** Raw 8.8 speed at which the search accepts arrival. */
    public static final int ARRIVE_SPEED = 12;
    /** Raw 8.8 speed allowed at the route's last row; a quarter pixel per tick. */
    public static final int SETTLED_SPEED = 64;

    public enum Start { WALK, JUMP, SPINDASH }

    public enum Facing { INHERIT, LEFT, RIGHT }

    public PointGoal {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(steering, "steering");
        Objects.requireNonNull(facing, "facing");
        if (maxRows < 1) throw new IllegalArgumentException("a point goal needs rows");
        if (!(urgency > 0 && urgency <= 1)) throw new IllegalArgumentException("urgency is in (0, 1]");
    }

    /** Whether {@code view} has arrived: grounded, inside the tolerance box, slower than {@code speed}. */
    public boolean arrivedAt(PlayerView view, int speed) {
        return !view.air() && Math.abs(view.x() - targetX) <= ARRIVE_X && Math.abs(view.y() - targetY) <= ARRIVE_Y
                && Math.abs(view.xSpeed()) <= speed && Math.abs(view.groundSpeed()) <= speed;
    }
}
