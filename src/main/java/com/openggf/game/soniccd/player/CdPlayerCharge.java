package com.openggf.game.soniccd.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded normal-sized R11A charge owner; scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, src/Level/_Objects/Sonic/Main.asm.
 *
 * <p>These are exact source call boundaries, NOT a complete movement/frame step.
 * The caller owns raw-to-logical input latching (Main.asm:295-300), input edges
 * (Functions (General).asm:85-106), dispatch admission and call order. Call
 * beforeRoutineDispatch exactly once per executed ObjSonic dispatch after the
 * debug bypass, before selecting its routine/mode. Never advance it for a
 * render, pause, setup or an extra helper call. It uses the PREVIOUS roll flag.
 * No wall clock, fixture input, source/JSON fallback or global service is used.
 *
 * <p>State is an immutable capture. Restore by supplying that saved value on
 * the next call, together with restored caller-owned inputs/body state. Ordered
 * effects describe every native write and sound in the admitted span; apply
 * either their writes or the final state, not both. Animation writes select
 * oAnim, not an animation tick. Sound requests are deferred service requests.
 * Continue at the returned native label before claiming any post-frame result.
 */
public final class CdPlayerCharge {
    private static final int LEFT_FACING = 1, AIR = 2, ROLL = 4;
    private static final int UP = 1, DOWN = 2, ACTION = 0x70;

    private CdPlayerCharge() { }

    /**
     * Only the actor/global fields written by these spans. Velocities are
     * signed native words; byte fields are unsigned. centreY16_16 contains the
     * exact native bit pattern: ADDQ/SUBQ.W changes only its high word and
     * preserves its low fractional word. Centre X and all other RAM are caller-owned.
     * Stored XY velocities are independent of charge ground velocity.
     */
    public record State(int charge, int groundVelocity, int storedXVelocity,
                        int storedYVelocity, int flags, int xRadius, int yRadius,
                        int centreY16_16, int animation, int lookMode, int cameraYCentre) {
        public State {
            byteValue(charge, "oPlayerCharge");
            signedWord(groundVelocity, "oPlayerGVel");
            signedWord(storedXVelocity, "oXVel");
            signedWord(storedYVelocity, "oYVel");
            byteValue(flags, "oFlags");
            byteValue(xRadius, "oXRadius");
            byteValue(yRadius, "oYRadius");
            byteValue(animation, "oAnim");
            byteValue(lookMode, "lookMode");
            unsignedWord(cameraYCentre, "camYCenter");
        }
    }

    /** Current production bytes; normal routine 2, grounded, non-mini, non-ramp only. */
    public record Body(int routine, int playerControl, int miniSonic) {
        public Body {
            byteValue(routine, "oRoutine");
            byteValue(playerControl, "oPlayerCtrl");
            byteValue(miniSonic, "miniSonic");
        }
    }

    /**
     * Already latched LOGICAL playerCtrlHold/playerCtrlTap, not a host jump
     * boolean or reconstructed edge. ctrlLocked retains the caller's previous
     * logical word; it does not necessarily mean zero input. Top speed is the
     * current signed native sonicTopSpeed word, speedShoes the native byte.
     */
    public record Input(Body body, int logicalHeld, int logicalTap, int topSpeed,
                        int speedShoes, int angle, int moveLock, int waterSlide) {
        public Input {
            Objects.requireNonNull(body, "body");
            byteValue(logicalHeld, "playerCtrlHold");
            byteValue(logicalTap, "playerCtrlTap");
            signedWord(topSpeed, "sonicTopSpeed");
            byteValue(speedShoes, "speedShoes");
            byteValue(angle, "oAngle");
            signedWord(moveLock, "oPlayerMoveLock");
            byteValue(waterSlide, "waterSlideFlag");
        }
    }

    public enum ActorField {
        CHARGE, GROUND_VELOCITY, STORED_X_VELOCITY, STORED_Y_VELOCITY,
        FLAGS, X_RADIUS, Y_RADIUS, CENTRE_Y_WORD, ANIMATION
    }
    public enum GlobalField { LOOK_MODE, CAMERA_Y_CENTRE }
    public enum Sound { FM_9C, FM_CHARGESTOP, FM_91 }
    public sealed interface Effect permits ActorWrite, GlobalWrite, SoundRequest { }
    public record ActorWrite(ActorField field, int value) implements Effect {
        public ActorWrite { Objects.requireNonNull(field, "field"); }
    }
    public record GlobalWrite(GlobalField field, int value) implements Effect {
        public GlobalWrite { Objects.requireNonNull(field, "field"); }
    }
    public record SoundRequest(Sound sound) implements Effect {
        public SoundRequest { Objects.requireNonNull(sound, "sound"); }
    }

    /** Exact next source label; none implies the containing mode/frame is finished. */
    public enum Continuation {
        ROUTINE_DISPATCH,
        START_ROLL_RETURN,
        MOVE_GROUND_RETURN,
        GROUND_SETTLE,
        MOVE_ROLL_RETURN,
        ROLL_SETTLE,
        ROLL_CALC_XY_VELOCITIES,
        ROLL_MOVE_LEFT_THEN_SETTLE,
        ROLL_MOVE_RIGHT_THEN_SETTLE
    }

    public record Result(State state, List<Effect> effects, Continuation continuation) {
        public Result {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(effects);
            Objects.requireNonNull(continuation, "continuation");
        }
    }

    /** ObjSonic.NormalMode to .RunRoutines (75-94). Byte ADD precedes unsigned cap. */
    public static Result beforeRoutineDispatch(State state) {
        Cursor c = new Cursor(state);
        if (c.charge != 0) {
            int incremented = (c.charge + 1) & 0xFF;
            c.actor(ActorField.CHARGE, Math.min(incremented, (c.flags & ROLL) == 0 ? 30 : 45));
        }
        return c.finish(Continuation.ROUTINE_DISPATCH);
    }

    /**
     * ObjSonic_MoveGround.CheckCharge through .ResetScreen (939-1136).
     * Caller MUST first execute MoveGround's direction calls and resolve actual
     * balance/object/floor branches (837-937), including .Stand writes and any
     * .BalanceGround charge-stop writes. Call only if they reach .CheckCharge.
     * Guards reject earlier native bypasses; they do not implement those paths.
     * On GROUND_SETTLE the caller must execute 1140 onward, including XY
     * projection/wall checks. Even MOVE_GROUND_RETURN returns to MdGround,
     * whose remaining CheckRoll/bounds/ObjMove/collision calls still belong to it.
     */
    public static Result moveGroundAtCheckCharge(State state, Input input) {
        requireBody(state, input.body(), false);
        if (input.waterSlide() != 0 || input.moveLock() != 0
                || (((input.angle() + 0x20) & 0xC0) != 0)
                || (state.groundVelocity() != 0 && state.charge() == 0)) {
            throw new UnsupportedOperationException("Earlier MoveGround branch does not reach CheckCharge");
        }
        Cursor c = new Cursor(state);
        if ((c.look & 0xF) != 0) {
            c.global(GlobalField.LOOK_MODE, (c.look + 1) & 0xFF);
            c.global(GlobalField.LOOK_MODE, c.look & 0xCF);
        }
        if ((c.look & 0x80) != 0) {
            if ((input.logicalHeld() & UP) != 0) {
                c.actor(ActorField.ANIMATION, 7);
                if (c.camera != 0xC8) c.global(GlobalField.CAMERA_Y_CENTRE, (c.camera + 2) & 0xFFFF);
                return c.finish(Continuation.GROUND_SETTLE);
            }
            return checkForSpindash(c, input);
        }
        if ((c.look & 0x40) != 0 || (input.logicalHeld() & DOWN) != 0) {
            return checkForSpindash(c, input);
        }
        c.global(GlobalField.LOOK_MODE, c.look & 0xF);
        if ((input.logicalTap() & UP) != 0) {
            c.global(GlobalField.LOOK_MODE, c.look == 0 ? 1 : c.look | 0x80);
            return c.finish(Continuation.GROUND_SETTLE);
        }
        if ((input.logicalHeld() & UP) != 0) {
            c.actor(ActorField.ANIMATION, 7);
            if (c.charge != 0) {
                c.actor(ActorField.ANIMATION, 0);
                advanceChargedVelocity(c, input, 100);
                return c.finish(Continuation.MOVE_GROUND_RETURN);
            }
            if ((input.logicalTap() & ACTION) != 0) {
                c.actor(ActorField.CHARGE, 1);
                c.sound(Sound.FM_9C);
            }
            return c.finish(Continuation.GROUND_SETTLE);
        }
        if (c.charge == 30) {
            c.actor(ActorField.CHARGE, 0);
            c.sound(Sound.FM_91);
            return resetScreen(c);
        }
        // This stop request occurs even with charge already zero (1033-1036).
        c.sound(Sound.FM_CHARGESTOP);
        c.actor(ActorField.CHARGE, 0);
        c.actor(ActorField.GROUND_VELOCITY, 0);
        return checkForSpindash(c, input);
    }

    /**
     * MoveRoll.NotRight through .Spindash/.NotDown (1366-1438), plus the early
     * cancellation's .StopRolling/.ResetAnim (1471-1486). Direction calls and
     * slope resistance already ran. Unlike ground, nonzero moveLock still
     * reaches NotRight; water-slide bypass does not. Zero charge requests
     * ROLL_SETTLE without work. A mature release stops BEFORE its additional
     * MoveRollLeft/Right call and Settle; caller must perform both in that order,
     * then CalcXY/wall checks. Early cancel continues at CalcXY, not Settle.
     */
    public static Result moveRollAtNotRight(State state, Input input) {
        requireBody(state, input.body(), true);
        if (input.waterSlide() != 0) {
            throw new UnsupportedOperationException("Water slide bypasses MoveRoll.NotRight");
        }
        Cursor c = new Cursor(state);
        if (c.charge == 0) return c.finish(Continuation.ROLL_SETTLE);
        advanceChargedVelocity(c, input, 75);
        if ((input.logicalHeld() & DOWN) != 0) return c.finish(Continuation.MOVE_ROLL_RETURN);
        if (c.charge == 45) {
            c.actor(ActorField.CHARGE, 0);
            c.sound(Sound.FM_91);
            return c.finish((c.flags & LEFT_FACING) != 0
                    ? Continuation.ROLL_MOVE_LEFT_THEN_SETTLE
                    : Continuation.ROLL_MOVE_RIGHT_THEN_SETTLE);
        }
        c.sound(Sound.FM_CHARGESTOP);
        c.actor(ActorField.CHARGE, 0);
        c.actor(ActorField.GROUND_VELOCITY, 0);
        c.actor(ActorField.STORED_X_VELOCITY, 0);
        c.actor(ActorField.STORED_Y_VELOCITY, 0);
        c.actor(ActorField.FLAGS, c.flags & ~ROLL);
        c.actor(ActorField.Y_RADIUS, 0x13);
        c.actor(ActorField.X_RADIUS, 9);
        c.actor(ActorField.CENTRE_Y_WORD, ((c.y >>> 16) - 5) & 0xFFFF);
        c.actor(ActorField.ANIMATION, 5);
        return c.finish(Continuation.ROLL_CALC_XY_VELOCITIES);
    }

    /** ObjSonic_StartRoll (1744-1773); normal-sized grounded boundary only. */
    public static Result startRoll(State state, Body body) {
        requireNormalBody(state, body);
        Cursor c = new Cursor(state);
        startRoll(c);
        return c.finish(Continuation.START_ROLL_RETURN);
    }

    private static Result checkForSpindash(Cursor c, Input input) {
        if ((c.look & 0x40) != 0) {
            if ((input.logicalHeld() & DOWN) == 0) return resetScreen(c);
            c.actor(ActorField.ANIMATION, 8);
            if (c.camera != 8) c.global(GlobalField.CAMERA_Y_CENTRE, (c.camera - 2) & 0xFFFF);
            return c.finish(Continuation.GROUND_SETTLE);
        }
        c.global(GlobalField.LOOK_MODE, c.look & 0xF);
        if ((input.logicalTap() & DOWN) != 0) {
            c.global(GlobalField.LOOK_MODE, c.look == 0 ? 1 : c.look | 0x40);
            return c.finish(Continuation.GROUND_SETTLE);
        }
        if ((input.logicalHeld() & DOWN) == 0) return resetScreen(c);
        c.actor(ActorField.ANIMATION, 8);
        if (c.charge == 0 && (input.logicalTap() & ACTION) != 0) {
            c.actor(ActorField.CHARGE, 1);
            c.actor(ActorField.GROUND_VELOCITY, 0x16);
            if ((c.flags & LEFT_FACING) != 0) c.actor(ActorField.GROUND_VELOCITY, -c.inertia);
            c.sound(Sound.FM_9C);
            startRoll(c);
        }
        return c.finish(Continuation.GROUND_SETTLE);
    }

    private static Result resetScreen(Cursor c) {
        if (c.camera == 0x60) {
            if ((c.look & 0xF) == 0) c.global(GlobalField.LOOK_MODE, 0);
        } else {
            // CMP.W/BCC is unsigned; retain both writes when below centre.
            if (c.camera < 0x60) c.global(GlobalField.CAMERA_Y_CENTRE, (c.camera + 4) & 0xFFFF);
            c.global(GlobalField.CAMERA_Y_CENTRE, (c.camera - 2) & 0xFFFF);
        }
        return c.finish(Continuation.GROUND_SETTLE);
    }

    private static void startRoll(Cursor c) {
        if ((c.flags & ROLL) == 0) {
            c.actor(ActorField.FLAGS, c.flags | ROLL);
            c.actor(ActorField.Y_RADIUS, 0xE);
            c.actor(ActorField.X_RADIUS, 7);
            c.actor(ActorField.CENTRE_Y_WORD, ((c.y >>> 16) + 5) & 0xFFFF);
        }
        c.actor(ActorField.ANIMATION, 2);
        // Shipped branch: negative speed bypasses the positive $200 floor.
        // This inspected R11A span has no FixBugs switch; do not symmetrize it.
        if (c.inertia >= 0 && c.inertia < 0x200) c.actor(ActorField.GROUND_VELOCITY, 0x200);
    }

    private static void advanceChargedVelocity(Cursor c, Input input, int increment) {
        int cap = (short) (input.topSpeed() << 1); // ASL.W, including overflow
        if (input.speedShoes() != 0) cap = (short) (cap - (input.topSpeed() >> 1)); // ASR.W
        boolean left = (c.flags & LEFT_FACING) != 0;
        if (left) {
            increment = (short) -increment;
            cap = (short) -cap;
        }
        int next = (short) (c.inertia + increment); // ADD.W precedes signed CMP.W/BLE
        c.actor(ActorField.GROUND_VELOCITY, next); // Native ADD.W writes memory, even before a cap.
        c.actor(ActorField.GROUND_VELOCITY, left ? Math.max(next, cap) : Math.min(next, cap));
    }

    private static void requireNormalBody(State state, Body body) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(body, "body");
        if (body.routine() != 2 || (body.playerControl() & 3) != 0
                || body.miniSonic() != 0 || (state.flags() & AIR) != 0) {
            throw new UnsupportedOperationException("Requires normal-sized grounded routine 2, no object control/3D ramp");
        }
    }

    private static void requireBody(State state, Body body, boolean rolling) {
        requireNormalBody(state, body);
        if (((state.flags() & ROLL) != 0) != rolling) {
            throw new UnsupportedOperationException("Wrong native ground/roll mode for this boundary");
        }
    }

    /** Local transition builder only; never retained or exposed as captured RAM. */
    private static final class Cursor {
        final State original;
        final List<Effect> effects = new ArrayList<>();
        int charge, inertia, xv, yv, flags, xr, yr, y, animation, look, camera;

        Cursor(State s) {
            original = Objects.requireNonNull(s, "state");
            charge = s.charge(); inertia = s.groundVelocity(); xv = s.storedXVelocity();
            yv = s.storedYVelocity(); flags = s.flags(); xr = s.xRadius(); yr = s.yRadius();
            y = s.centreY16_16(); animation = s.animation(); look = s.lookMode(); camera = s.cameraYCentre();
        }

        void actor(ActorField field, int value) {
            effects.add(new ActorWrite(field, value));
            switch (field) {
                case CHARGE -> charge = value;
                case GROUND_VELOCITY -> inertia = value;
                case STORED_X_VELOCITY -> xv = value;
                case STORED_Y_VELOCITY -> yv = value;
                case FLAGS -> flags = value;
                case X_RADIUS -> xr = value;
                case Y_RADIUS -> yr = value;
                case CENTRE_Y_WORD -> y = (value << 16) | (y & 0xFFFF);
                case ANIMATION -> animation = value;
            }
        }

        void global(GlobalField field, int value) {
            effects.add(new GlobalWrite(field, value));
            switch (field) {
                case LOOK_MODE -> look = value;
                case CAMERA_Y_CENTRE -> camera = value;
            }
        }

        void sound(Sound sound) { effects.add(new SoundRequest(sound)); }

        Result finish(Continuation next) {
            State state = effects.isEmpty() ? original : new State(charge, inertia, xv, yv,
                    flags, xr, yr, y, animation, look, camera);
            return new Result(state, effects, next);
        }
    }

    private static void byteValue(int value, String name) {
        if (value < 0 || value > 0xFF) throw new IllegalArgumentException(name + " must be an unsigned byte");
    }
    private static void signedWord(int value, String name) {
        if (value != (short) value) throw new IllegalArgumentException(name + " must be a signed word");
    }
    private static void unsignedWord(int value, String name) {
        if (value < 0 || value > 0xFFFF) throw new IllegalArgumentException(name + " must be an unsigned word");
    }
}
