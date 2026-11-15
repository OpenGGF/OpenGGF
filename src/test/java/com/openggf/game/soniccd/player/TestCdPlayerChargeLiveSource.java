package com.openggf.game.soniccd.player;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.player.CdPlayerCharge.*;

/** Source-first controls for the same charge kernel. All execution belongs to Root. */
class TestCdPlayerChargeLiveSource {
    static State state(int charge,int velocity,int flags) {
        return new State(charge,velocity,495,-49,flags,(flags&4)==0?9:7,
                (flags&4)==0?19:14,0x01001234,0,0,0x60);
    }
    static Input input(boolean roll,int held,int tap) {
        return new Input(new Body(2,0,0),held,tap,0x600,0,0,0,0);
    }
    static final class Fault extends RuntimeException { Fault(String message){super(message);} }
    static final class Harness implements LivePorts {
        State value;Input expectedInput;Body expectedBody;long generation=7;
        Live live;RuntimeException refuseActor;final List<Effect> writes=new ArrayList<>();final List<Primitive> calls=new ArrayList<>();
        final List<Boolean> completed=new ArrayList<>();final List<Observation> observations=new ArrayList<>();
        Consumer<Primitive> beforeHook=c->{};Consumer<Primitive> nativeHook=c->{};Consumer<Primitive> afterHook=c->{};
        Harness(State value,Input input){this.value=value;this.expectedInput=input;this.expectedBody=input==null?new Body(2,0,0):input.body();live=bindLive(this);}
        public long generation(){return generation;}
        public void requireCurrent(LiveCall call){
            call.requireCurrent(live);
            if(call.entry()==Entry.START_ROLL){assertTrue(call.input().isEmpty());assertEquals(expectedBody,call.body().orElseThrow());}
            else {assertEquals(expectedInput,call.input().orElseThrow());assertTrue(call.body().isEmpty());}
        }
        public State readState(LiveCall call){call.requireCurrent(live);return value;}
        public void before(Primitive call){call.requireCurrent(live);calls.add(call);beforeHook.accept(call);}
        private void landed(Primitive call){call.consume(live);writes.add(call.effect());value=call.afterState();nativeHook.accept(call);}
        public void actor(ActorCall call){if(refuseActor!=null)throw refuseActor;landed(call);}
        public void global(GlobalCall call){landed(call);}
        public void sound(SoundCall call){landed(call);}
        public void after(Primitive call){completed.add(call.completed());observations.add(call.observation());afterHook.accept(call);}
    }
    @Test void groundAndRollLegacyResultsAndPrimitiveOrderAreIdenticalAcrossRawControls(){
        for(boolean roll:new boolean[]{false,true})for(int charge:new int[]{0,1,29,30,44,45,255})
            for(int facing:new int[]{0,1})for(int held:new int[]{0,1,2,3})for(int tap:new int[]{0,1,2,0x70}){
                State s=state(charge,0,(roll?4:0)|facing);Input in=input(roll,held,tap);Harness h=new Harness(s,in);
                Result expected=roll?moveRollAtNotRight(s,in):moveGroundAtCheckCharge(s,in);
                Result actual=roll?h.live.moveRollAtNotRight(s,in):h.live.moveGroundAtCheckCharge(s,in);
                assertEquals(expected,actual);assertEquals(expected.state(),h.value);assertEquals(expected.effects(),h.writes);h.live.requireIdle();
            }
    }
    @Test void startRollUsesOneKernelForNegativeZeroLimitAndFractionalValues(){
        for(int velocity:new int[]{Short.MIN_VALUE,-1,0,0x1FF,0x200,Short.MAX_VALUE})for(int flags:new int[]{0,1,4,5}){
            State s=state(0,velocity,flags);Harness h=new Harness(s,null);Result expected=startRoll(s,h.expectedBody);
            assertEquals(expected,h.live.startRoll(s,h.expectedBody));assertEquals(expected.effects(),h.writes);assertEquals(0x1234,h.value.centreY16_16()&65535);
        }
    }
    @Test void chargeStopSoundFaultPrecedesGroundChargeAndVelocityClears(){
        State s=state(5,0,0);Input in=input(false,0,0);Harness h=new Harness(s,in);Fault first=new Fault("stop");
        h.nativeHook=c->{if(c instanceof SoundCall q){assertEquals(Sound.FM_CHARGESTOP,q.sound());assertEquals(5,h.value.charge());throw first;}};
        assertSame(first,assertThrows(Fault.class,()->h.live.moveGroundAtCheckCharge(s,in)));
        assertEquals(5,h.value.charge());assertEquals(0,h.value.groundVelocity());assertEquals(1,h.writes.stream().filter(SoundRequest.class::isInstance).count());
        assertThrows(IllegalStateException.class,()->h.live.moveGroundAtCheckCharge(s,in));
    }
    @Test void spinStartPublishesChargeAndDirectionSpeedBeforeSoundThenDoesNotRollOnFault(){
        State s=state(0,0,1);Input in=input(false,2,0x70);Harness h=new Harness(s,in);Fault first=new Fault("start");
        h.nativeHook=c->{if(c instanceof SoundCall){assertEquals(1,h.value.charge());assertEquals(-0x16,h.value.groundVelocity());assertEquals(1,h.value.flags());throw first;}};
        assertSame(first,assertThrows(Fault.class,()->h.live.moveGroundAtCheckCharge(s,in)));
        assertEquals(19,h.value.yRadius());assertEquals(0x01001234,h.value.centreY16_16());assertEquals(8,h.value.animation());
    }
    @Test void matureReleaseClearsChargeBeforeSoundAndKeepsTheLaterDirectionContinuationUnexecuted(){
        State s=state(45,0,5);Input in=input(true,0,0);Harness h=new Harness(s,in);Fault first=new Fault("release");
        h.nativeHook=c->{if(c instanceof SoundCall q){assertEquals(Sound.FM_91,q.sound());assertEquals(0,h.value.charge());assertEquals(4,h.value.flags()&4);throw first;}};
        assertSame(first,assertThrows(Fault.class,()->h.live.moveRollAtNotRight(s,in)));assertEquals(-75,h.value.groundVelocity());
    }
    @Test void rollCancellationKeepsBothVelocityStoresBeforeSoundFault(){
        State s=state(3,0,4);Input in=input(true,0,0);Harness h=new Harness(s,in);Fault first=new Fault("cancel");
        h.nativeHook=c->{if(c instanceof SoundCall){assertEquals(75,h.value.groundVelocity());assertEquals(3,h.value.charge());throw first;}};
        assertSame(first,assertThrows(Fault.class,()->h.live.moveRollAtNotRight(s,in)));
        assertEquals(List.of(new ActorWrite(ActorField.GROUND_VELOCITY,75),new ActorWrite(ActorField.GROUND_VELOCITY,75)),h.writes.subList(0,2));
        assertEquals(495,h.value.storedXVelocity());assertEquals(-49,h.value.storedYVelocity());assertEquals(4,h.value.flags());
    }
    @Test void refusedBeforeHasNoPrimitiveWriteAndStillCallsAfterExactlyOnce(){
        State s=state(0,0,0);Harness h=new Harness(s,null);Fault first=new Fault("before");
        h.beforeHook=c->{throw first;};assertSame(first,assertThrows(Fault.class,()->h.live.startRoll(s,h.expectedBody)));
        assertSame(s,h.value);assertTrue(h.writes.isEmpty());assertEquals(List.of(false),h.completed);assertEquals(List.of(Observation.REFUSED),h.observations);h.live.close();h.live.close();
    }
    @Test void landedActorFaultRetainsExactPrefixAndSuppressesDistinctAfterFault(){
        State s=state(0,0,0);Harness h=new Harness(s,null);Fault first=new Fault("actor");Fault verification=new Fault("after");
        h.nativeHook=c->{if(c instanceof ActorCall a&&a.field()==ActorField.Y_RADIUS)throw first;};
        h.afterHook=c->{if(c instanceof ActorCall a&&a.field()==ActorField.Y_RADIUS){assertTrue(c.attempted());assertFalse(c.completed());assertEquals(Observation.LANDED,c.observation());throw verification;}};
        assertSame(first,assertThrows(Fault.class,()->h.live.startRoll(s,h.expectedBody)));
        assertArrayEquals(new Throwable[]{verification},first.getSuppressed());assertEquals(4,h.value.flags());assertEquals(14,h.value.yRadius());assertEquals(9,h.value.xRadius());assertEquals(s.centreY16_16(),h.value.centreY16_16());
    }
    @Test void sameFaultIsNeverSelfSuppressedAndSameValueStoreDoesNotInventLanding(){
        State s=state(1,0,4);Input in=input(true,2,0);Harness h=new Harness(s,in);Fault first=new Fault("same");
        h.nativeHook=c->{throw first;};h.afterHook=c->{throw first;};assertSame(first,assertThrows(Fault.class,()->h.live.moveRollAtNotRight(s,in)));assertEquals(0,first.getSuppressed().length);
        State t=state(1,0,4);Input secondInput=input(true,2,0);Harness q=new Harness(t,secondInput);q.afterHook=c->{if(c instanceof ActorCall a&&a.field()==ActorField.GROUND_VELOCITY&&c.ordinal()==2)assertEquals(Observation.UNCHANGED_OR_SAME_VALUE,c.observation());};q.live.moveRollAtNotRight(t,secondInput);
    }
    @Test void mutationByRejectedBeforeCannotReplaceOriginalFaultOrBecomeAnAllowedStore(){
        State s=state(0,0,0);Harness h=new Harness(s,null);Fault first=new Fault("before changed");
        h.beforeHook=c->{h.value=c.afterState();throw first;};assertSame(first,assertThrows(Fault.class,()->h.live.startRoll(s,h.expectedBody)));
        assertTrue(first.getSuppressed().length>=1);assertTrue(h.writes.isEmpty());
    }
    @Test void completedPrimitiveCannotBeConsumedTwiceOrFromAfter(){
        State s=state(0,0,0);Harness h=new Harness(s,null);
        h.nativeHook=c->assertThrows(IllegalStateException.class,()->c.consume(h.live));
        h.afterHook=c->assertThrows(IllegalStateException.class,()->c.consume(h.live));h.live.startRoll(s,h.expectedBody);
    }
    @Test void caughtReentryAndCaptureRejectionLeaveHealthyOuterCallUsable(){
        State s=state(0,0,0);Harness h=new Harness(s,null);
        h.beforeHook=c->{assertThrows(IllegalStateException.class,h.live::requireIdle);assertThrows(IllegalStateException.class,h.live::close);assertThrows(IllegalStateException.class,()->h.live.startRoll(h.value,h.expectedBody));};
        assertEquals(startRoll(s,h.expectedBody),h.live.startRoll(s,h.expectedBody));h.live.requireIdle();
    }
    @Test void foreignThreadAndRetiredOccurrencesCannotAuthorizeAnyWrite()throws Exception{
        State s=state(0,0,0);Harness h=new Harness(s,null);Harness foreign=new Harness(s,null);var error=new AtomicReference<Throwable>();
        h.beforeHook=c->{assertThrows(IllegalStateException.class,()->c.requireCurrent(foreign.live));Thread t=new Thread(()->{try{c.requireCurrent(h.live);}catch(Throwable x){error.set(x);}});t.start();try{t.join();}catch(InterruptedException x){throw new AssertionError(x);}assertInstanceOf(IllegalStateException.class,error.get());};
        h.live.startRoll(s,h.expectedBody);for(var c:h.calls)assertThrows(IllegalStateException.class,()->c.requireCurrent(h.live));
        h.live.close();h.live.close();assertThrows(IllegalStateException.class,()->h.live.startRoll(h.value,h.expectedBody));
    }
    @Test void changedGenerationAndStaleInitialPairRejectWithoutNativeEffects(){
        State s=state(0,0,0);Harness h=new Harness(s,null);h.beforeHook=c->{h.generation++;};assertThrows(IllegalStateException.class,()->h.live.startRoll(s,h.expectedBody));assertTrue(h.writes.isEmpty());
        Harness q=new Harness(state(1,0,0),null);assertThrows(IllegalArgumentException.class,()->q.live.startRoll(s,q.expectedBody));assertTrue(q.calls.isEmpty());
    }
    @Test void wrongModeAndBypassRejectBeforeAnySoundOrPrimitive(){
        State s=state(0,0,0);Input in=input(false,0,0);Harness h=new Harness(s,in);assertThrows(UnsupportedOperationException.class,()->h.live.moveRollAtNotRight(s,in));assertTrue(h.calls.isEmpty());
        Input bypass=new Input(in.body(),0,0,0x600,0,0,1,0);Harness q=new Harness(s,bypass);assertThrows(UnsupportedOperationException.class,()->q.live.moveGroundAtCheckCharge(s,bypass));assertTrue(q.calls.isEmpty());
    }
    @Test void noChargeRollDoesNotReadAnySoundOrIssueAnOccurrence(){
        State s=state(0,0,4);Input in=input(true,0,0);Harness h=new Harness(s,in);Result result=h.live.moveRollAtNotRight(s,in);assertSame(s,result.state());assertEquals(Continuation.ROLL_SETTLE,result.continuation());assertTrue(h.calls.isEmpty());assertTrue(h.writes.isEmpty());
    }
    @Test void refusedNativeCallbackRetainsBeforeAndReportsAttemptWithoutCompletion(){
        State s=state(0,0,0);Harness h=new Harness(s,null);Fault first=new Fault("native refused");h.refuseActor=first;
        h.afterHook=c->{assertTrue(c.attempted());assertFalse(c.completed());assertEquals(Observation.REFUSED,c.observation());};
        assertSame(first,assertThrows(Fault.class,()->h.live.startRoll(s,h.expectedBody)));assertSame(s,h.value);assertTrue(h.writes.isEmpty());assertEquals(1,h.calls.size());
    }
    @Test void lockedLookAndHeldMatureRollNeverInvokeASoundOwner(){
        State b=state(0,0,0);State s=new State(b.charge(),b.groundVelocity(),b.storedXVelocity(),b.storedYVelocity(),b.flags(),b.xRadius(),b.yRadius(),b.centreY16_16(),b.animation(),0x80,b.cameraYCentre());Input in=input(false,1,0x70);Harness h=new Harness(s,in);
        h.nativeHook=c->{if(c instanceof SoundCall)throw new AssertionError("unreached sound");};assertEquals(Continuation.GROUND_SETTLE,h.live.moveGroundAtCheckCharge(s,in).continuation());assertTrue(h.writes.stream().noneMatch(SoundRequest.class::isInstance));
        State r=state(45,0,4);Input roll=input(true,2,0);Harness q=new Harness(r,roll);q.nativeHook=h.nativeHook;assertEquals(Continuation.MOVE_ROLL_RETURN,q.live.moveRollAtNotRight(r,roll).continuation());assertTrue(q.writes.stream().noneMatch(SoundRequest.class::isInstance));
    }

    @Test void rawWordWrapAndAddBeforeCapFaultRetainTheActualMemoryStore(){
        State s=state(1,32760,4);Input in=input(true,2,0);Harness h=new Harness(s,in);h.live.moveRollAtNotRight(s,in);
        assertEquals(List.of(new ActorWrite(ActorField.GROUND_VELOCITY,-32701),new ActorWrite(ActorField.GROUND_VELOCITY,-32701)),h.writes);
        State c=state(1,100,4);Input cap=new Input(new Body(2,0,0),2,0,50,0,0,0,0);Harness q=new Harness(c,cap);Fault first=new Fault("ADD before cap");q.nativeHook=x->{throw first;};
        assertSame(first,assertThrows(Fault.class,()->q.live.moveRollAtNotRight(c,cap)));assertEquals(175,q.value.groundVelocity());assertEquals(1,q.writes.size());
    }

}
