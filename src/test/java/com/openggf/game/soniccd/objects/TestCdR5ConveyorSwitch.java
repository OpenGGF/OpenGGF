package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.player.CdR5ConveyorReverse;
import com.openggf.game.soniccd.player.CdR5ConveyorScope;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** All candidates UNRUN. Native switch routines0/2/4/6 and wrapper Draw/despawn. */
class TestCdR5ConveyorSwitch {
    static class Fixture implements CdR5ConveyorSwitch.Ports,CdR5ConveyorReverse.Port {
        int reverse,debug,px=100,py=100,sounds,draws,despawns;
        CdR5ConveyorSwitch.State state=new CdR5ConveyorSwitch.State(100,100,0,0x80,0,0,0,0,0,0,0,0,0);
        final List<CdR5ConveyorSwitch.State> stores=new ArrayList<>();
        RuntimeException soundFailure;
        @Override public int read(){return reverse;}
        @Override public void publish(CdR5ConveyorReverse.Change c,CdR5ConveyorScope.Operation op){assertEquals(reverse,c.before());reverse=c.after();}
        @Override public int debugMode(){return debug;}
        @Override public int playerYWord(){return py;}
        @Override public int playerXWord(){return px;}
        @Override public void publishState(CdR5ConveyorSwitch.Change c,CdR5ConveyorScope.Operation op){assertEquals(state,c.before());state=c.after();stores.add(state);}
        @Override public void sound(int command,CdR5ConveyorScope.Operation op){assertEquals(0xBF,command);sounds++;if(soundFailure!=null)throw soundFailure;}
        @Override public void draw(CdR5ConveyorSwitch.State current,CdR5ConveyorScope.Operation op){assertEquals(state,current);draws++;}
        @Override public void despawn(CdR5ConveyorSwitch.State current,CdR5ConveyorScope.Operation op){assertEquals(state,current);despawns++;}
        CdR5ConveyorSwitch create(){return new CdR5ConveyorSwitch(0x20D0D2,this,this,CdR5ConveyorScope.Guard.NONE);}
        void set(CdR5ConveyorSwitch.Field f,int n){state=state.with(f,n);}
        void step(){create().run(state);}
    }
    @Test void initPreservesFlagsSetsNativeFieldsThenFallsThroughToContact(){
        var f=new Fixture();f.reverse=0xA0;f.step();assertEquals(0xA1,f.reverse);assertEquals(4,f.state.routine());
        assertEquals(0x84,f.state.spriteFlags());assertEquals(3,f.state.layer());assertEquals(24,f.state.width());
        assertEquals(24,f.state.height());assertEquals(0x4797,f.state.tile());assertEquals(0x20D0D2,f.state.mappings());
        assertEquals(0xA0,f.state.seenReverse());assertEquals(1,f.sounds);assertEquals(1,f.draws);assertEquals(1,f.despawns);
    }
    @Test void initUsesBitZeroForTheVisualButRetainsTheFullReverseByte(){
        var f=new Fixture();f.reverse=0x81;f.debug=1;f.step();assertEquals(2,f.state.frame());assertEquals(0x81,f.state.seenReverse());assertEquals(0x81,f.reverse);
    }
    @Test void contactUsesUnsignedWordDistanceBoundaries(){
        var f=new Fixture();f.set(CdR5ConveyorSwitch.Field.ROUTINE,2);f.px=108;f.step();assertEquals(0,f.reverse);
        f.px=107;f.py=116;f.step();assertEquals(0,f.reverse);f.py=115;f.step();assertEquals(1,f.reverse);
    }
    @Test void offscreenContactStillTogglesWithoutSound(){
        var f=new Fixture();f.set(CdR5ConveyorSwitch.Field.ROUTINE,2);f.set(CdR5ConveyorSwitch.Field.SPRITE_FLAGS,0);f.step();assertEquals(1,f.reverse);assertEquals(0,f.sounds);
    }
    @Test void soundFaultRetainsToggleAndRoutineButDoesNotDrawOrDespawn(){
        var f=new Fixture();f.set(CdR5ConveyorSwitch.Field.ROUTINE,2);var fault=new IllegalStateException("sound");f.soundFailure=fault;var kernel=f.create();
        assertSame(fault,assertThrows(IllegalStateException.class,()->kernel.run(f.state)));assertEquals(1,f.reverse);assertEquals(4,f.state.routine());assertEquals(0,f.draws);assertEquals(0,f.despawns);
        assertEquals(f.state,kernel.lastState());assertFalse(kernel.lastPrefix().getLast().completed());
    }
    @Test void externalSharedByteChangeStartsRemoteTransitionAndUpdatesSeenByte(){
        var f=new Fixture();f.debug=1;f.reverse=0x80;f.set(CdR5ConveyorSwitch.Field.ROUTINE,2);f.step();
        assertEquals(4,f.state.routine());assertEquals(255,f.state.remoteMarker());assertEquals(0x80,f.state.seenReverse());assertEquals(0,f.sounds);
    }
    @Test void transitionSetsMiddleAtSevenAndFinalAtFourteenThenDisarms(){
        var f=new Fixture();f.reverse=1;f.set(CdR5ConveyorSwitch.Field.ROUTINE,4);
        for(int i=1;i<=14;i++){f.step();if(i==7)assertEquals(1,f.state.frame());}
        assertEquals(2,f.state.frame());assertEquals(0,f.state.timer());assertEquals(6,f.state.routine());assertEquals(0,f.state.remoteMarker());
    }
    @Test void transitionTimerIsAByteAndDoesNotTreatAboveFourteenAsComplete(){
        var f=new Fixture();f.set(CdR5ConveyorSwitch.Field.ROUTINE,4);f.set(CdR5ConveyorSwitch.Field.TIMER,255);f.step();assertEquals(0,f.state.timer());assertEquals(4,f.state.routine());
    }
    @Test void separationUsesLowByteOfAbsoluteWordDistance(){
        var f=new Fixture();f.set(CdR5ConveyorSwitch.Field.ROUTINE,6);f.py=100+256;f.px=100+256;f.step();assertEquals(6,f.state.routine());
        f.py=100+288;f.step();assertEquals(2,f.state.routine());
    }
    @Test void rearmReadsAndStoresCurrentSharedByteWithoutToggling(){
        var f=new Fixture();f.reverse=0xAB;f.set(CdR5ConveyorSwitch.Field.ROUTINE,6);f.px=116;f.step();assertEquals(2,f.state.routine());assertEquals(0xAB,f.state.seenReverse());assertEquals(0xAB,f.reverse);
    }
    @Test void twoSwitchesUseOneSharedPortRatherThanTwoPrivateReverseStates(){
        var f=new Fixture();f.set(CdR5ConveyorSwitch.Field.ROUTINE,2);f.step();assertEquals(1,f.reverse);
        f.set(CdR5ConveyorSwitch.Field.ROUTINE,2);f.step();assertEquals(0,f.reverse);
    }
    @Test void guardCanRejectToggleWithoutChangingGlobalOrSwitch(){
        var f=new Fixture();f.set(CdR5ConveyorSwitch.Field.ROUTINE,2);var initial=f.state;
        var k=new CdR5ConveyorSwitch(0x20D0D2,f,f,new CdR5ConveyorScope.Guard(){
            @Override public void before(CdR5ConveyorScope.Operation op){if(op.kind()==CdR5ConveyorScope.Kind.TOGGLE_REVERSE)throw new IllegalStateException("lease");}
            @Override public void after(CdR5ConveyorScope.Operation op,boolean done){}
        });assertThrows(IllegalStateException.class,()->k.run(initial));assertEquals(0,f.reverse);assertEquals(initial,f.state);
    }
    @Test void invalidRoutineRejectsBeforeSoundDrawOrDespawn(){
        var f=new Fixture();f.set(CdR5ConveyorSwitch.Field.ROUTINE,8);assertThrows(UnsupportedOperationException.class,f::step);assertEquals(0,f.draws);assertEquals(0,f.despawns);assertEquals(0,f.sounds);
    }
    @Test void identicalGuardFaultDoesNotTriggerSelfSuppression(){
        var f=new Fixture();var fault=new IllegalStateException("guard");var k=new CdR5ConveyorSwitch(1,f,f,new CdR5ConveyorScope.Guard(){
            @Override public void before(CdR5ConveyorScope.Operation op){throw fault;}
            @Override public void after(CdR5ConveyorScope.Operation op,boolean done){throw fault;}
        });assertSame(fault,assertThrows(IllegalStateException.class,()->k.run(f.state)));assertEquals(0,fault.getSuppressed().length);
    }
}
