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
 * oAnim, not an animation tick. Sound requests are deferred service requests for the pure APIs; bindLive executes the
 * same primitive stream synchronously through actual caller-owned ports.
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

    /** Current production bytes; normal routine 2, grounded, non-mini, without external object control. */
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
        if (c.value.charge() != 0) {
            int incremented = (c.value.charge() + 1) & 0xFF;
            c.actor(ActorField.CHARGE, Math.min(incremented, (c.value.flags() & ROLL) == 0 ? 30 : 45));
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
        return groundKernel(state, input, Staged.INSTANCE);
    }

    private static Result groundKernel(State state, Input input, Output output) {
        requireBody(state, input.body(), false);
        if (input.waterSlide() != 0 || input.moveLock() != 0
                || (((input.angle() + 0x20) & 0xC0) != 0)
                || (state.groundVelocity() != 0 && state.charge() == 0)) {
            throw new UnsupportedOperationException("Earlier MoveGround branch does not reach CheckCharge");
        }
        Cursor c = new Cursor(state, output);
        if ((c.value.lookMode() & 0xF) != 0) {
            c.global(GlobalField.LOOK_MODE, (c.value.lookMode() + 1) & 0xFF);
            c.global(GlobalField.LOOK_MODE, c.value.lookMode() & 0xCF);
        }
        if ((c.value.lookMode() & 0x80) != 0) {
            if ((input.logicalHeld() & UP) != 0) {
                c.actor(ActorField.ANIMATION, 7);
                if (c.value.cameraYCentre() != 0xC8) c.global(GlobalField.CAMERA_Y_CENTRE, (c.value.cameraYCentre() + 2) & 0xFFFF);
                return c.finish(Continuation.GROUND_SETTLE);
            }
            return checkForSpindash(c, input);
        }
        if ((c.value.lookMode() & 0x40) != 0 || (input.logicalHeld() & DOWN) != 0) {
            return checkForSpindash(c, input);
        }
        c.global(GlobalField.LOOK_MODE, c.value.lookMode() & 0xF);
        if ((input.logicalTap() & UP) != 0) {
            c.global(GlobalField.LOOK_MODE, c.value.lookMode() == 0 ? 1 : c.value.lookMode() | 0x80);
            return c.finish(Continuation.GROUND_SETTLE);
        }
        if ((input.logicalHeld() & UP) != 0) {
            c.actor(ActorField.ANIMATION, 7);
            if (c.value.charge() != 0) {
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
        if (c.value.charge() == 30) {
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
        return rollKernel(state, input, Staged.INSTANCE);
    }

    private static Result rollKernel(State state, Input input, Output output) {
        requireBody(state, input.body(), true);
        if (input.waterSlide() != 0) {
            throw new UnsupportedOperationException("Water slide bypasses MoveRoll.NotRight");
        }
        Cursor c = new Cursor(state, output);
        if (c.value.charge() == 0) return c.finish(Continuation.ROLL_SETTLE);
        advanceChargedVelocity(c, input, 75);
        if ((input.logicalHeld() & DOWN) != 0) return c.finish(Continuation.MOVE_ROLL_RETURN);
        if (c.value.charge() == 45) {
            c.actor(ActorField.CHARGE, 0);
            c.sound(Sound.FM_91);
            return c.finish((c.value.flags() & LEFT_FACING) != 0
                    ? Continuation.ROLL_MOVE_LEFT_THEN_SETTLE
                    : Continuation.ROLL_MOVE_RIGHT_THEN_SETTLE);
        }
        c.sound(Sound.FM_CHARGESTOP);
        c.actor(ActorField.CHARGE, 0);
        c.actor(ActorField.GROUND_VELOCITY, 0);
        c.actor(ActorField.STORED_X_VELOCITY, 0);
        c.actor(ActorField.STORED_Y_VELOCITY, 0);
        c.actor(ActorField.FLAGS, c.value.flags() & ~ROLL);
        c.actor(ActorField.Y_RADIUS, 0x13);
        c.actor(ActorField.X_RADIUS, 9);
        c.actor(ActorField.CENTRE_Y_WORD, ((c.value.centreY16_16() >>> 16) - 5) & 0xFFFF);
        c.actor(ActorField.ANIMATION, 5);
        return c.finish(Continuation.ROLL_CALC_XY_VELOCITIES);
    }

    /** ObjSonic_StartRoll (1744-1773); normal-sized grounded boundary only. */
    public static Result startRoll(State state, Body body) {
        return startRollKernel(state, body, Staged.INSTANCE);
    }

    private static Result startRollKernel(State state, Body body, Output output) {
        requireNormalBody(state, body);
        Cursor c = new Cursor(state, output);
        startRoll(c);
        return c.finish(Continuation.START_ROLL_RETURN);
    }

    private static Result checkForSpindash(Cursor c, Input input) {
        if ((c.value.lookMode() & 0x40) != 0) {
            if ((input.logicalHeld() & DOWN) == 0) return resetScreen(c);
            c.actor(ActorField.ANIMATION, 8);
            if (c.value.cameraYCentre() != 8) c.global(GlobalField.CAMERA_Y_CENTRE, (c.value.cameraYCentre() - 2) & 0xFFFF);
            return c.finish(Continuation.GROUND_SETTLE);
        }
        c.global(GlobalField.LOOK_MODE, c.value.lookMode() & 0xF);
        if ((input.logicalTap() & DOWN) != 0) {
            c.global(GlobalField.LOOK_MODE, c.value.lookMode() == 0 ? 1 : c.value.lookMode() | 0x40);
            return c.finish(Continuation.GROUND_SETTLE);
        }
        if ((input.logicalHeld() & DOWN) == 0) return resetScreen(c);
        c.actor(ActorField.ANIMATION, 8);
        if (c.value.charge() == 0 && (input.logicalTap() & ACTION) != 0) {
            c.actor(ActorField.CHARGE, 1);
            c.actor(ActorField.GROUND_VELOCITY, 0x16);
            if ((c.value.flags() & LEFT_FACING) != 0) c.actor(ActorField.GROUND_VELOCITY, -c.value.groundVelocity());
            c.sound(Sound.FM_9C);
            startRoll(c);
        }
        return c.finish(Continuation.GROUND_SETTLE);
    }

    private static Result resetScreen(Cursor c) {
        if (c.value.cameraYCentre() == 0x60) {
            if ((c.value.lookMode() & 0xF) == 0) c.global(GlobalField.LOOK_MODE, 0);
        } else {
            // CMP.W/BCC is unsigned; retain both writes when below centre.
            if (c.value.cameraYCentre() < 0x60) c.global(GlobalField.CAMERA_Y_CENTRE, (c.value.cameraYCentre() + 4) & 0xFFFF);
            c.global(GlobalField.CAMERA_Y_CENTRE, (c.value.cameraYCentre() - 2) & 0xFFFF);
        }
        return c.finish(Continuation.GROUND_SETTLE);
    }

    private static void startRoll(Cursor c) {
        if ((c.value.flags() & ROLL) == 0) {
            c.actor(ActorField.FLAGS, c.value.flags() | ROLL);
            c.actor(ActorField.Y_RADIUS, 0xE);
            c.actor(ActorField.X_RADIUS, 7);
            c.actor(ActorField.CENTRE_Y_WORD, ((c.value.centreY16_16() >>> 16) + 5) & 0xFFFF);
        }
        c.actor(ActorField.ANIMATION, 2);
        // Shipped branch: negative speed bypasses the positive $200 floor.
        // This inspected R11A span has no FixBugs switch; do not symmetrize it.
        if (c.value.groundVelocity() >= 0 && c.value.groundVelocity() < 0x200) c.actor(ActorField.GROUND_VELOCITY, 0x200);
    }

    private static void advanceChargedVelocity(Cursor c, Input input, int increment) {
        int cap = (short) (input.topSpeed() << 1); // ASL.W, including overflow
        if (input.speedShoes() != 0) cap = (short) (cap - (input.topSpeed() >> 1)); // ASR.W
        boolean left = (c.value.flags() & LEFT_FACING) != 0;
        if (left) {
            increment = (short) -increment;
            cap = (short) -cap;
        }
        int next = (short) (c.value.groundVelocity() + increment); // ADD.W precedes signed CMP.W/BLE
        c.actor(ActorField.GROUND_VELOCITY, next); // Native ADD.W writes memory, even before a cap.
        c.actor(ActorField.GROUND_VELOCITY, left ? Math.max(next, cap) : Math.min(next, cap));
    }

    private static void requireNormalBody(State state, Body body) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(body, "body");
        // Handle3DRamp sets control bit1 before MdGround/MoveRoll/StartRoll (Sonic/Main.asm:694-704,742-755).
        // These native helpers never consume that bit; retain it and still reject external object-control bit0.
        if (body.routine() != 2 || (body.playerControl() & 1) != 0
                || body.miniSonic() != 0 || (state.flags() & AIR) != 0) {
            throw new UnsupportedOperationException("Requires normal-sized grounded routine 2 without external object control");
        }
    }

    private static void requireBody(State state, Body body, boolean rolling) {
        requireNormalBody(state, body);
        if (((state.flags() & ROLL) != 0) != rolling) {
            throw new UnsupportedOperationException("Wrong native ground/roll mode for this boundary");
        }
    }

    /** The three existing native helper boundaries; no Frame/source admission is created. */
    public enum Entry { MOVE_GROUND_CHECK_CHARGE, MOVE_ROLL_NOT_RIGHT, START_ROLL }
    /** Actual store observation. Same-value stores/sounds never prove whether a write landed. */
    public enum Observation { UNOBSERVED, REFUSED, LANDED, UNCHANGED_OR_SAME_VALUE }

    /** Actual caller-owned ports, bound under its current Frame/Body invocation. Every method is
     * required. requireCurrent must validate source/generation/thread and the complete actual
     * State+Environment/input/storage cut; readState is only a consistency projection. Actor/global
     * callbacks consume their private occurrence once and apply that original native field store;
     * sound consumes the occurrence and calls the real sound owner. No Result/effects replay.
     * before/after fence all external partitions. All ports are synchronous and nonretained. */
    public interface LivePorts {
        long generation();
        void requireCurrent(LiveCall call);
        State readState(LiveCall call);
        void before(Primitive call);
        void actor(ActorCall call);
        void global(GlobalCall call);
        void sound(SoundCall call);
        void after(Primitive call);
    }

    /** This utility scope is subordinate to the real caller. Creating it authenticates no Frame,
     * native source, capture, clock or continuation. Bind once to that owner's actual ports. */
    public static Live bindLive(LivePorts ports) { return new Live(Objects.requireNonNull(ports)); }

    public static final class Live implements AutoCloseable {
        private final LivePorts ports;
        private LiveCall current;
        private boolean busy, closed, failed;
        private Live(LivePorts ports) { this.ports=ports; }
        public Result moveGroundAtCheckCharge(State state,Input input) {
            return invoke(Entry.MOVE_GROUND_CHECK_CHARGE,Objects.requireNonNull(state),Objects.requireNonNull(input),null);
        }
        public Result moveRollAtNotRight(State state,Input input) {
            return invoke(Entry.MOVE_ROLL_NOT_RIGHT,Objects.requireNonNull(state),Objects.requireNonNull(input),null);
        }
        public Result startRoll(State state,Body body) {
            return invoke(Entry.START_ROLL,Objects.requireNonNull(state),null,Objects.requireNonNull(body));
        }
        /** The real caller's capture/restore path must reject an unfinished charge operation. */
        public void requireIdle() {
            if(busy||closed||failed)throw new IllegalStateException("Unfinished/retired charge owner");
        }
        @Override public void close() {
            if(busy)throw new IllegalStateException("Active charge owner cannot close");
            closed=true;
        }
        public boolean isCurrent(LiveCall call) {
            return identity(call)&&ports.generation()==call.generation;
        }
        public boolean isCurrent(Primitive call) {
            return call!=null&&isCurrent(call.invocation)&&call.invocation.pending==call&&call.phase!=Phase.RETIRED;
        }
        private boolean identity(LiveCall call) {
            return call!=null&&call.owner==this&&busy&&!closed&&!failed&&current==call
                    &&call.active&&call.thread==Thread.currentThread();
        }
        private void require(LiveCall call) {
            if(!identity(call)||ports.generation()!=call.generation)
                throw new IllegalStateException("Foreign/off-thread/stale charge invocation");
        }
        private void check(LiveCall call) {
            require(call);ports.requireCurrent(call);require(call);
        }
        private State read(LiveCall call) {
            check(call);var state=Objects.requireNonNull(ports.readState(call),"actual charge projection");check(call);return state;
        }
        private void expect(LiveCall call,State expected,boolean initial) {
            if(!read(call).equals(expected)){
                if(initial)throw new IllegalArgumentException("Different complete initial charge projection");
                throw new IllegalStateException("Changed complete charge primitive projection");
            }
        }
        private Result invoke(Entry entry,State state,Input input,Body body) {
            requireIdle();busy=true;LiveCall call=null;
            try {
                long generation=ports.generation();call=new LiveCall(this,entry,state,input,body,generation);current=call;
                expect(call,state,true);Output output=new NativeOutput(call);
                Result result=switch(entry){
                    case MOVE_GROUND_CHECK_CHARGE->groundKernel(state,input,output);
                    case MOVE_ROLL_NOT_RIGHT->rollKernel(state,input,output);
                    case START_ROLL->startRollKernel(state,body,output);
                };
                expect(call,result.state(),false);return result;
            }catch(RuntimeException|Error failure){failed=true;throw failure;}
            finally{if(call!=null){call.active=false;call.pending=null;}current=null;busy=false;}
        }
        private void emit(LiveCall call,Cursor cursor,Effect effect,State next) {
            check(call);if(call.pending!=null)throw new IllegalStateException("Charge primitive reentry");
            State before=cursor.value;expect(call,before,false);
            Primitive primitive=switch(effect){
                case ActorWrite actor->new ActorCall(call,++call.ordinal,before,next,actor);
                case GlobalWrite global->new GlobalCall(call,++call.ordinal,before,next,global);
                case SoundRequest sound->new SoundCall(call,++call.ordinal,before,next,sound);
            };
            call.pending=primitive;Throwable failure=null;
            try{
                primitive.phase=Phase.BEFORE;ports.before(primitive);expect(call,before,false);
                primitive.phase=Phase.NATIVE;primitive.attempted=true;
                switch(primitive){
                    case ActorCall actor->ports.actor(actor);
                    case GlobalCall global->ports.global(global);
                    case SoundCall sound->ports.sound(sound);
                }
                if(!primitive.consumed)throw new IllegalStateException("Original charge occurrence not consumed");
                expect(call,next,false);cursor.accept(effect,next);primitive.completed=true;
            }catch(RuntimeException|Error original){failure=original;}
            primitive.phase=Phase.AFTER;
            // All three fences are independent: even a failed projection check cannot skip after.
            try{observe(primitive);}catch(RuntimeException|Error verification){failure=append(failure,verification);}
            try{ports.after(primitive);}catch(RuntimeException|Error verification){failure=append(failure,verification);}
            try{observe(primitive);}catch(RuntimeException|Error verification){failure=append(failure,verification);}
            primitive.phase=Phase.RETIRED;call.pending=null;
            if(failure instanceof RuntimeException runtime)throw runtime;
            if(failure instanceof Error error)throw error;
        }
        private void observe(Primitive primitive) {
            State actual=read(primitive.invocation);boolean before=actual.equals(primitive.before),after=actual.equals(primitive.after);
            if(!primitive.attempted){
                if(!before)throw new IllegalStateException("Rejected charge before-hook changed actual state");
                primitive.observation=Observation.REFUSED;return;
            }
            if(primitive.completed&&!after||!before&&!after)
                throw new IllegalStateException("Charge callback changed beyond its one native store");
            primitive.observation=before&&after?Observation.UNCHANGED_OR_SAME_VALUE:after?Observation.LANDED:Observation.REFUSED;
        }
    }
    private static Throwable append(Throwable original,Throwable verification) {
        if(original==null)return verification;
        if(original!=verification){
            boolean found=false;for(var existing:original.getSuppressed())if(existing==verification)found=true;
            if(!found)original.addSuppressed(verification);
        }
        return original;
    }
    private enum Phase { ISSUED,BEFORE,NATIVE,AFTER,RETIRED }
    public static final class LiveCall {
        private final Live owner;private final Entry entry;private final State initial;
        private final Input input;private final Body body;private final long generation;
        private final Thread thread=Thread.currentThread();private boolean active=true;
        private int ordinal;private Primitive pending;
        private LiveCall(Live owner,Entry entry,State initial,Input input,Body body,long generation){
            this.owner=owner;this.entry=entry;this.initial=initial;this.input=input;this.body=body;this.generation=generation;
        }
        public void requireCurrent(Live expected){if(expected!=owner)throw new IllegalStateException("Foreign charge owner");owner.require(this);}
        public Entry entry(){requireCurrent(owner);return entry;}
        public State initialState(){requireCurrent(owner);return initial;}
        public java.util.Optional<Input> input(){requireCurrent(owner);return java.util.Optional.ofNullable(input);}
        public java.util.Optional<Body> body(){requireCurrent(owner);return java.util.Optional.ofNullable(body);}
        public long generation(){requireCurrent(owner);return generation;}
    }
    /** Private-issued occurrence, descriptive values only. consume is legal exactly once from the
     * actual native callback, not from observers, another thread/generation, or after retirement. */
    public abstract static sealed class Primitive permits ActorCall,GlobalCall,SoundCall {
        private final LiveCall invocation;private final int ordinal;private final State before,after;private final Effect effect;
        private Phase phase=Phase.ISSUED;private boolean consumed,attempted,completed;
        private Observation observation=Observation.UNOBSERVED;
        private Primitive(LiveCall invocation,int ordinal,State before,State after,Effect effect){this.invocation=invocation;this.ordinal=ordinal;this.before=before;this.after=after;this.effect=effect;}
        public final void requireCurrent(Live expected){invocation.requireCurrent(expected);if(invocation.pending!=this||phase==Phase.RETIRED)throw new IllegalStateException("Expired charge primitive");}
        public final void consume(Live expected){requireCurrent(expected);if(phase!=Phase.NATIVE||consumed)throw new IllegalStateException("Charge primitive already consumed or not native");consumed=true;}
        public final LiveCall invocation(){requireCurrent(invocation.owner);return invocation;}
        public final int ordinal(){requireCurrent(invocation.owner);return ordinal;}
        public final State beforeState(){requireCurrent(invocation.owner);return before;}
        public final State afterState(){requireCurrent(invocation.owner);return after;}
        public final Effect effect(){requireCurrent(invocation.owner);return effect;}
        public final boolean attempted(){requireCurrent(invocation.owner);return attempted;}
        public final boolean completed(){requireCurrent(invocation.owner);return completed;}
        public final Observation observation(){requireCurrent(invocation.owner);return observation;}
    }
    public static final class ActorCall extends Primitive {
        private ActorCall(LiveCall c,int n,State b,State a,ActorWrite e){super(c,n,b,a,e);}
        public ActorField field(){return ((ActorWrite)effect()).field();}
        public int value(){return ((ActorWrite)effect()).value();}
    }
    public static final class GlobalCall extends Primitive {
        private GlobalCall(LiveCall c,int n,State b,State a,GlobalWrite e){super(c,n,b,a,e);}
        public GlobalField field(){return ((GlobalWrite)effect()).field();}
        public int value(){return ((GlobalWrite)effect()).value();}
    }
    public static final class SoundCall extends Primitive {
        private SoundCall(LiveCall c,int n,State b,State a,SoundRequest e){super(c,n,b,a,e);}
        public Sound sound(){return ((SoundRequest)effect()).sound();}
    }

    private interface Output { void primitive(Cursor cursor,Effect effect,State next); }
    private enum Staged implements Output {
        INSTANCE;
        public void primitive(Cursor cursor,Effect effect,State next){cursor.accept(effect,next);}
    }
    private static final class NativeOutput implements Output {
        private final LiveCall call;
        private NativeOutput(LiveCall call){this.call=call;}
        public void primitive(Cursor cursor,Effect effect,State next){call.owner.emit(call,cursor,effect,next);}
    }
    /** Local transition builder; staged and original callbacks use this one native store model. */
    private static final class Cursor {
        final State original;final Output output;final List<Effect> effects=new ArrayList<>();State value;
        Cursor(State state){this(state,Staged.INSTANCE);}
        Cursor(State state,Output output){original=Objects.requireNonNull(state,"state");value=state;this.output=Objects.requireNonNull(output);}
        void actor(ActorField field,int next){
            State s=value;int charge=s.charge(),inertia=s.groundVelocity(),xv=s.storedXVelocity(),yv=s.storedYVelocity(),flags=s.flags(),xr=s.xRadius(),yr=s.yRadius(),y=s.centreY16_16(),animation=s.animation();
            switch(field){
                case CHARGE->charge=next;case GROUND_VELOCITY->inertia=next;
                case STORED_X_VELOCITY->xv=next;case STORED_Y_VELOCITY->yv=next;
                case FLAGS->flags=next;case X_RADIUS->xr=next;case Y_RADIUS->yr=next;
                case CENTRE_Y_WORD->y=(next<<16)|(y&65535);case ANIMATION->animation=next;
            }
            State ready=new State(charge,inertia,xv,yv,flags,xr,yr,y,animation,s.lookMode(),s.cameraYCentre());
            output.primitive(this,new ActorWrite(field,next),ready);
        }
        void global(GlobalField field,int next){
            State s=value;State ready=new State(s.charge(),s.groundVelocity(),s.storedXVelocity(),s.storedYVelocity(),s.flags(),s.xRadius(),s.yRadius(),s.centreY16_16(),s.animation(),field==GlobalField.LOOK_MODE?next:s.lookMode(),field==GlobalField.CAMERA_Y_CENTRE?next:s.cameraYCentre());
            output.primitive(this,new GlobalWrite(field,next),ready);
        }
        void sound(Sound sound){output.primitive(this,new SoundRequest(sound),value);}
        private void accept(Effect effect,State next){effects.add(effect);value=next;}
        Result finish(Continuation next){
            State s=value;State state=effects.isEmpty()?original:new State(s.charge(),s.groundVelocity(),s.storedXVelocity(),s.storedYVelocity(),s.flags(),s.xRadius(),s.yRadius(),s.centreY16_16(),s.animation(),s.lookMode(),s.cameraYCentre());
            return new Result(state,effects,next);
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
