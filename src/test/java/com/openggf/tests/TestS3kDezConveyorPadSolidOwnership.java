package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.S3kDezConveyorPadObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.CollisionTrace;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native status(a0) belongs to the conveyor's live SST occupant, across movement. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezConveyorPadSolidOwnership {
    @AfterEach void reset() {
        GameServices.collision().setTrace(null);
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest @ValueSource(ints={0,1})
    void movingPadRetainsEachParticipantsBitUntilItsOwnAirborneCheckpointAndRecreates(int jumpingPlayer) {
        var c=SonicConfigurationService.getInstance();
        c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
        var f=HeadlessTestFixture.builder().withZoneAndAct(11,0)
                .startPosition((short)0x560,(short)0x400).startPositionIsCentre().build();
        f.camera().setX((short)0x500);f.camera().setY((short)0x480);
        var manager=GameServices.level().getObjectManager();
        var players=List.of(f.sprite(),GameServices.sprites().getRegisteredSidekicks().getFirst());
        var pad=new S3kDezConveyorPadObjectInstance(new ObjectSpawn(0x600,0x500,0x53,0xB0,0,false,0));
        manager.addDynamicObject(pad);dispatch(f);
        for(var p:players){
            p.setRenderFlagOnScreen(true);
            NativePositionOps.writeXPosResetSubpixel(p,pad.getX());
            NativePositionOps.writeYPosResetSubpixel(p,pad.getY()-0x10-p.getYRadius()-1);
            p.setAir(true);p.setOnObject(false);p.setYSpeed((short)0x30);
        }
        dispatch(f);
        for(var p:players){assertTrue(manager.isRidingObject(p,pad),"initial real contact "+p.getCode());assertTrue(manager.hasObjectStandingBit(p,pad));}
        dispatch(f);
        for(var p:players){assertTrue(manager.isRidingObject(p,pad));assertTrue(manager.hasObjectStandingBit(p,pad));}
        assertEquals(1,pad.routineForTest());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        int slot=pad.getSlotIndex();
        var first=jumpAndObserve(f,pad,jumpingPlayer);
        manager.removeDynamicObject(pad);
        assertFalse(manager.getActiveObjects().contains(pad));
        registry.restore(saved);
        var restored=manager.getActiveObjects().stream().filter(o->o instanceof S3kDezConveyorPadObjectInstance && ((S3kDezConveyorPadObjectInstance)o).getSlotIndex()==slot)
                .map(o->(S3kDezConveyorPadObjectInstance)o).findFirst().orElseThrow();
        assertNotSame(pad,restored);assertEquals(slot,restored.getSlotIndex());
        for(var p:players){assertTrue(manager.isRidingObject(p,restored));assertTrue(manager.hasObjectStandingBit(p,restored));}
        assertEquals(first,jumpAndObserve(f,restored,jumpingPlayer));
        // A replacement in the retired occupant's physical slot inherits no participant bit.
        manager.removeDynamicObject(restored);
        var replacement=new S3kDezConveyorPadObjectInstance(new ObjectSpawn(0x600,0x500,0x53,0xB0,0,false,0));
        manager.addDynamicObjectAtSlot(replacement,slot);assertEquals(slot,replacement.getSlotIndex());
        for(var p:players)assertFalse(manager.hasObjectStandingBit(p,replacement));
    }

    private List<String> jumpAndObserve(HeadlessTestFixture f,S3kDezConveyorPadObjectInstance pad,int jumpingPlayer){
        var manager=GameServices.level().getObjectManager();
        var players=List.of(f.sprite(),GameServices.sprites().getRegisteredSidekicks().getFirst());
        var jumping=players.get(jumpingPlayer);var held=players.get(1-jumpingPlayer);
        // Declared player-slot jump result, before any object body. The cold-movie
        // regression separately drives the actual jump/radius write from original input.
        int jumpY=jumping.getCentreY()+jumping.getStandYRadius()-jumping.getRollYRadius();
        jumping.setRolling(true);NativePositionOps.writeYPosPreserveSubpixel(jumping,jumpY);
        jumping.setAir(true);jumping.setYSpeed((short)-0x680);
        var checkpoints=new ArrayList<String>();var trace=mock(CollisionTrace.class);
        doAnswer(call->{
            String type=call.getArgument(0);
            if(type.equals("S3kDezConveyorPadObjectInstance") && (int)call.getArgument(1)==pad.getX()){
                assertTrue(manager.hasObjectStandingBit(jumping,pad),"moving metadata must retain this occupant's airborne standing bit");
                assertTrue(manager.hasObjectStandingBit(held,pad),"moving metadata must retain the other participant's bit");
                assertTrue(jumping.isOnObject(),"earlier solid checkpoints cannot consume this pad's native airborne return");
                checkpoints.add(pad.getY()+":"+jumping.getCentreY());
            }
            return null;
        }).when(trace).onSolidCheckpointStart(anyString(),anyInt(),anyInt());
        GameServices.collision().setTrace(trace);
        var previousSpawn=pad.getSpawn();int padY=pad.getY();dispatch(f);
        GameServices.collision().setTrace(null);
        assertEquals(padY-1,pad.getY());assertNotEquals(previousSpawn,pad.getSpawn());
        assertEquals(1,checkpoints.size());assertEquals(jumpY,jumping.getCentreY(),"loc_1DC98 returns without a new-contact Y lift");
        assertFalse(manager.hasObjectStandingBit(jumping,pad));assertFalse(manager.isRidingObject(jumping,pad));
        assertFalse(jumping.isOnObject());assertTrue(jumping.getAir());
        assertTrue(manager.hasObjectStandingBit(held,pad));assertTrue(manager.isRidingObject(held,pad));
        var rows=new ArrayList<String>();rows.addAll(checkpoints);
        NativePositionOps.writeXPosResetSubpixel(jumping,pad.getX()+0x200);
        for(int i=0;i<45;i++){
            dispatch(f);
            rows.add(pad.getX()+":"+pad.getY()+":"+pad.remainingForTest()+":"+pad.animationForTest()+":"+held.getCentreX()+":"+held.getCentreY()+":"+manager.hasObjectStandingBit(held,pad));
        }
        return rows;
    }

    private void dispatch(HeadlessTestFixture f){
        GameServices.level().getObjectManager().update(f.camera().getX(),f.sprite(),GameServices.sprites().getRegisteredSidekicks(),0,false,true,true);
    }
}
