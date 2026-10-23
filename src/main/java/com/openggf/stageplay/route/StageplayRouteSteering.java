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

    public record Buttons(int direction, boolean down, boolean jump, boolean returning, boolean finished) { }

    /** Shared production command kernel. Command scheduling and waits stay with their world owner. */
    public static Buttons input(PuppetCommand command, int elapsed,
            com.openggf.sprites.playable.PlayablePuppet.Observation actor, boolean jump,
            boolean returning, int approach, double targetX) {
        int direction = 0;
        boolean down = false, finished = false;
        switch (command.kind()) {
            case MOVE_TO, FLY_TO, GLIDE_TO -> {
                if (command.motion().endingAction() == EndingAction.OVERSHOOT)
                    returning = returning(Math.floor(actor.x()), targetX, approach, returning);
                direction = direction(Math.floor(actor.x()), actor.xSpeed(), actor.groundSpeed(),
                        actor.runAccel(), actor.runDecel(), actor.friction(), actor.maxSpeed(),
                        actor.slopeImpulse(), actor.airborne(), targetX, command.urgency(),
                        command.motion(), returning, approach);
            }
            case STOP -> {
                if (command.motion().endingAction() != EndingAction.GENTLE_STOP)
                    direction = StageplayMovementIntent.direction(Math.floor(actor.x()), actor.xSpeed(),
                            actor.runAccel(), actor.runDecel(), actor.maxSpeed(), actor.airborne(), actor.x(), 0);
            }
            case SPINDASH -> {
                var charge = charge(actor.x(), targetX, elapsed, actor.groundSpeed(), actor.airborne(),
                        actor.crouching(), actor.spindash(), actor.friction(), actor.slopeImpulse());
                direction = charge.direction(); down = charge.down(); jump = charge.jump();
                finished = elapsed > CHARGE_FRAMES;
            }
            case FACE_LEFT, FACE_RIGHT -> {
                direction = command.kind() == PuppetCommandKind.FACE_LEFT ? -1 : 1;
                finished = true;
            }
            case HOVER, CROUCH, LOOK_UP, ROLL, WAIT -> direction = StageplayMovementIntent.direction(
                    Math.floor(actor.x()), actor.xSpeed(), actor.runAccel(), actor.runDecel(),
                    actor.maxSpeed(), actor.airborne(), actor.x(), 0);
            default -> { }
        }
        return new Buttons(direction, down, jump, returning, finished);
    }

    /** Fixed charge window, with a native-state-gated fresh jump edge.
     * Sonic_CheckSpindash (s2.asm:37555-37581) reads Duck before ground movement.
     * Sonic_SlopeResist (s2.asm:37709-37732) skips zero inertia: brake the facing
     * tap to zero before holding down, then wait for native Duck before pressing jump.
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
