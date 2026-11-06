package com.openggf.stageplay.route;

/** The existing LIVE MOVE_TO controller; input selection only, never a physics-state write. */
public final class StageplayMovementIntent {
    private StageplayMovementIntent() {}

    public static int direction(
            double centreX,
            int xSpeed,
            int runAccel,
            int runDecel,
            int maxSpeed,
            boolean airborne,
            double targetX,
            double urgency) {
        return com.openggf.sprites.playable.route.NativeRouteSteering.movementDirection(centreX, xSpeed, runAccel,
                runDecel, maxSpeed, airborne, targetX, urgency);
    }
}
