package com.openggf.stageplay.route;

import com.openggf.sprites.playable.route.NativeRouteSteering;

import static com.openggf.stageplay.StageplayDocument.*;

/** Directing policy expressed only as native buttons, shared by search and LIVE replay. */
public final class StageplayRouteSteering {
    public static final int STROLL_CAP = NativeRouteSteering.STROLL_CAP;
    public static final int CHARGE_FRAMES = NativeRouteSteering.CHARGE_FRAMES;
    public static final double OVERSHOOT_PIXELS = NativeRouteSteering.OVERSHOOT_PIXELS;
    private StageplayRouteSteering() { }

    public static int direction(double x, int xSpeed, int groundSpeed, int accel, int decel,
            int friction, int maxSpeed, int slopeImpulse, boolean airborne, double targetX, double urgency,
            RouteMotion motion, boolean returning, int approach) {
        return NativeRouteSteering.direction(x, xSpeed, groundSpeed, accel, decel, friction,
                maxSpeed, slopeImpulse, airborne, targetX, urgency, settings(motion), returning, approach);
    }

    /** Native facts copied by the LIVE adapter; this policy never reads a sprite or service. */
    public record Motion(double x, int xSpeed, int groundSpeed, int runAccel, int runDecel,
                         int friction, int maxSpeed, int slopeImpulse, boolean airborne,
                         boolean crouching, boolean spindash) { }

    public record Buttons(int direction, boolean down, boolean jump, boolean returning, boolean finished) { }

    /** Shared production command kernel. Command scheduling and waits stay with their world owner. */
    public static Buttons input(PuppetCommand command, int elapsed,
            Motion actor, boolean jump,
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

    /** Stageplay-compatible view of the shared native charge policy. */
    public static Buttons charge(double x, double targetX, int elapsed, int groundSpeed,
            boolean airborne, boolean crouching, boolean spindash, int friction, int slopeImpulse) {
        var buttons = NativeRouteSteering.charge(x, targetX, elapsed, groundSpeed,
                airborne, crouching, spindash, friction, slopeImpulse);
        return new Buttons(buttons.direction(), buttons.down(), buttons.jump(), buttons.returning(), buttons.finished());
    }
    public static boolean returning(double x, double targetX, int approach, boolean already) {
        return NativeRouteSteering.returning(x, targetX, approach, already);
    }

    private static NativeRouteSteering.Settings settings(RouteMotion motion) {
        var travel = switch (motion.travelSpeed()) {
            case WALK -> NativeRouteSteering.TravelSpeed.WALK;
            case RUN -> NativeRouteSteering.TravelSpeed.RUN;
        };
        var ending = switch (motion.endingAction()) {
            case GENTLE_STOP -> NativeRouteSteering.EndingAction.GENTLE_STOP;
            case HARD_STOP -> NativeRouteSteering.EndingAction.HARD_STOP;
            case OVERSHOOT -> NativeRouteSteering.EndingAction.OVERSHOOT;
        };
        return new NativeRouteSteering.Settings(travel, ending);
    }
}
