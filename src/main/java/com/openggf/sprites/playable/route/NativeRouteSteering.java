package com.openggf.sprites.playable.route;

/** Numeric route policy expressed as native buttons, without a document or world owner. */
public final class NativeRouteSteering {
    // Authoring choice: 1.5 px/tick ($180), one quarter of normal native top speed.
    // SAnim_WalkRun (s2.asm:38449-38487) switches only at $600, also the normal
    // Sonic_top_speed initialization (s2.asm:36187); just below $600 still looks like running.
    public static final int STROLL_CAP = 0x180;
    /** Fixed authored charge window; native physics owns charge and release speed. */
    public static final int CHARGE_FRAMES = 24;
    public static final double OVERSHOOT_PIXELS = 24;

    private NativeRouteSteering() { }

    public enum TravelSpeed { WALK, RUN }
    public enum EndingAction { GENTLE_STOP, HARD_STOP, OVERSHOOT }
    public record Settings(TravelSpeed travelSpeed, EndingAction endingAction) { }
    public record Buttons(int direction, boolean down, boolean jump, boolean returning, boolean finished) { }

    /** Ordinary native-speed movement and stopping; speeds and accelerations use 8.8 units. */
    public static int movementDirection(double centreX, int xSpeed, int runAccel, int runDecel,
            int maxSpeed, boolean airborne, double targetX, double urgency) {
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

    public static int direction(double x, int xSpeed, int groundSpeed, int accel, int decel,
            int friction, int maxSpeed, int slopeImpulse, boolean airborne, double targetX, double urgency,
            Settings settings, boolean returning, int approach) {
        double goal = settings.endingAction() == EndingAction.OVERSHOOT && !returning
                ? targetX + approach * OVERSHOOT_PIXELS : targetX;
        double speed = (airborne ? xSpeed : groundSpeed) / 256.0;
        int cap = settings.travelSpeed() == TravelSpeed.WALK ? Math.min(STROLL_CAP, maxSpeed) : maxSpeed;
        cap = Math.max(1, (int) (cap * urgency));
        int toward = goal < x ? -1 : 1;
        // Native Sonic_SlopeResist (s2.asm:37709-37732) precedes acceleration.
        // Predict it from the actor's live slope factor, without changing physics.
        int drift = airborne || groundSpeed == 0 ? 0 : slopeImpulse;
        double predictedSpeed = (airborne ? xSpeed : groundSpeed) + drift;
        if (settings.travelSpeed() == TravelSpeed.WALK && (Math.abs(predictedSpeed) > cap || Math.abs(xSpeed) > cap))
            return speed < 0 ? 1 : -1;
        if (settings.endingAction() != EndingAction.GENTLE_STOP &&
                !(settings.endingAction() == EndingAction.OVERSHOOT && returning)) {
            int direction = movementDirection(x, xSpeed, accel, decel, cap, airborne, goal, 1);
            // Every Walk ending needs acceleration headroom, including double air acceleration.
            // Opposing input remains available for native braking.
            if (settings.travelSpeed() == TravelSpeed.WALK && direction * predictedSpeed >= 0
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

    /** Fixed charge window, with a native-state-gated fresh jump edge.
     * Sonic_CheckSpindash (s2.asm:37555-37581) reads Duck before ground movement.
     * Sonic_SlopeResist (s2.asm:37709-37732) skips zero inertia: settle the facing
     * tap while holding down, then wait for native Duck before pressing jump.
     * Sonic_UpdateSpindash (s2.asm:37591-37688) owns release speed; fixBugs=0 remains active.
     */
    public static Buttons charge(double x, double targetX, int elapsed, int groundSpeed,
            boolean airborne, boolean crouching, boolean spindash, int friction, int slopeImpulse) {
        if (elapsed > CHARGE_FRAMES) return new Buttons(0, false, false, false, true);
        if (elapsed == 0) return new Buttons(targetX < x ? -1 : 1, false, false, false, false);
        // Coasting removes the small facing tap without the native reverse-speed
        // floor of opposite-input braking. If downhill pull defeats friction,
        // brake once uphill, then let slope+friction settle it into Duck.
        if (!spindash && groundSpeed * slopeImpulse > 0 && Math.abs(slopeImpulse) >= friction)
            return new Buttons(groundSpeed < 0 ? 1 : -1, true, false, false, false);
        return new Buttons(0, true, !airborne && crouching && !spindash, false, false);
    }

    public static boolean returning(double x, double targetX, int approach, boolean already) {
        return already || (x - targetX) * approach >= OVERSHOOT_PIXELS - 6;
    }
}
