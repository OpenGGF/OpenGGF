package com.openggf.stageplay.route;

import com.openggf.sprites.playable.route.NativeRouteSteering;

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
        return NativeRouteSteering.movementDirection(centreX, xSpeed, runAccel, runDecel,
                maxSpeed, airborne, targetX, urgency);
    }
}
