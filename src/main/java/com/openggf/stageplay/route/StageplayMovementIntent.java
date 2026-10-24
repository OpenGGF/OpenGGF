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
        double speed = xSpeed / 256.0;
        // Sonic_ChgJumpDir (s2.asm:37158-37175), shift at 37161, doubles runAccel.
        // Ground countersteering uses runDecel; arrival/braking margins are authoring policy.
        double brake = Math.max(1.0 / 256, (airborne ? 2 * runAccel : runDecel) / 256.0);
        double dx = targetX - centreX;
        if (urgency == 0
                || Math.abs(dx) <= 4
                || Math.abs(dx) <= speed * speed / (2 * brake) + Math.abs(speed) + 4)
            return Math.abs(speed) <= Math.max(.25, brake) ? 0 : speed < 0 ? 1 : -1;
        int toward = dx < 0 ? -1 : 1;
        return speed * toward < 0 || Math.abs(speed) < maxSpeed / 256.0 * urgency ? toward : 0;
    }
}
