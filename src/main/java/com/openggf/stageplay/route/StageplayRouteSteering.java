package com.openggf.stageplay.route;

import static com.openggf.stageplay.StageplayDocument.*;

/** Directing policy expressed only as native buttons, shared by search and LIVE replay. */
public final class StageplayRouteSteering {
    public static int deceleration(com.openggf.sprites.playable.AbstractPlayableSprite actor) {
        return actor.getRolling() ? actor.getRollDecel() : actor.getRunDecel();
    }

    public static int friction(com.openggf.sprites.playable.AbstractPlayableSprite actor) {
        // Sonic_RollSpeed (s2.asm:37009-37013): natural roll drag is acceleration / 2.
        return actor.getRolling() ? actor.getRunAccel() / 2 : actor.getFriction();
    }

    public static int slopeImpulse(com.openggf.sprites.playable.AbstractPlayableSprite actor) {
        return (actor.getSlopeRunning() * com.openggf.physics.TrigLookupTable.sinHex(actor.getAngle() & 0xFF)) >> 8;
    }
    // Authoring choice: 1.5 px/tick ($180), one quarter of normal native top speed.
    // SAnim_WalkRun (s2.asm:38449-38487) switches only at $600, also the normal
    // Sonic_top_speed initialization (s2.asm:36187); just below $600 still looks like running.
    public static final int STROLL_CAP = 0x180;
    public static final int CHARGE_FRAMES = PuppetCommand.SPINDASH_CHARGE_FRAMES;
    public static final double OVERSHOOT_PIXELS = 24;
    private StageplayRouteSteering() { }

    public static int direction(double x, int xSpeed, int groundSpeed, int accel, int decel,
            int friction, int maxSpeed, int slopeImpulse, boolean airborne, double targetX, double urgency,
            RouteMotion motion, boolean returning, int approach) {
        double goal = motion.endingAction() == EndingAction.OVERSHOOT && !returning
                ? targetX + approach * OVERSHOOT_PIXELS : targetX;
        double speed = (airborne ? xSpeed : groundSpeed) / 256.0;
        int cap = motion.travelSpeed() == TravelSpeed.WALK ? Math.min(STROLL_CAP, maxSpeed) : maxSpeed;
        cap = Math.max(1, (int) (cap * urgency));
        int toward = goal < x ? -1 : 1;
        // Native Sonic_SlopeResist (s2.asm:37709-37732) precedes acceleration.
        // Predict it from the actor's live slope factor, without changing physics.
        int drift = airborne || groundSpeed == 0 ? 0 : slopeImpulse;
        double predictedSpeed = (airborne ? xSpeed : groundSpeed) + drift;
        if (motion.travelSpeed() == TravelSpeed.WALK && (Math.abs(predictedSpeed) > cap || Math.abs(xSpeed) > cap))
            return speed < 0 ? 1 : -1;
        if (motion.endingAction() != EndingAction.GENTLE_STOP &&
                !(motion.endingAction() == EndingAction.OVERSHOOT && returning))
            {
            int direction = StageplayMovementIntent.direction(x, xSpeed, accel, decel, cap, airborne, goal, 1);
            // Every Walk ending needs acceleration headroom, including double air acceleration.
            // Opposing input remains available for native braking.
            if (motion.travelSpeed() == TravelSpeed.WALK && direction * predictedSpeed >= 0
                    && Math.abs(predictedSpeed) + (airborne ? 2 : 1) * accel > cap) return 0;
            return direction;
        }
        if (airborne) return Math.abs(xSpeed) + 2 * accel <= cap ? toward : 0;
        double drag = Math.max(1, friction) / 256.0;
        double coast = speed * speed / (2 * drag);
        double dx = goal - x;
        // No opposing input at speed during Gentle Stop: release early and let native
        // friction settle. Once stationary a small corrective approach is safe.
        if (speed * dx < 0 || Math.abs(dx) <= coast + Math.abs(speed) + 3)
            return Math.abs(speed) < 1.0 / 256 && Math.abs(dx) > 3 ? toward : 0;
        return Math.abs(predictedSpeed) + accel <= cap ? toward : 0;
    }

    /** Fixed authored charge cadence, not a replacement for native charge/speed tables.
     * Sonic_CheckSpindash / Sonic_UpdateSpindash (s2.asm:37555-37688) own crouch,
     * pressed jump edges, charge decay and release speed. Shipped fixBugs=0 is unchanged.
     */
    // One press selects the native minimum charge; repeated revs overdrive short acting routes.
    public static boolean chargeDown(int elapsed) { return elapsed > 0 && elapsed <= CHARGE_FRAMES; }
    public static boolean chargeJump(int elapsed) { return elapsed == 4; }
    public static int chargeDirection(double x, double targetX, int elapsed) {
        return elapsed == 0 ? (targetX < x ? -1 : 1) : 0;
    }
    public static boolean returning(double x, double targetX, int approach, boolean already) {
        return already || (x - targetX) * approach >= OVERSHOOT_PIXELS - 6;
    }
}
