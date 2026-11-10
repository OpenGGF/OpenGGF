package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static org.junit.jupiter.api.Assertions.*;

/** COMPONENT/source controls using the existing native format harness. Java UNRUN.
 * Equal values and completed callback records grant no World/Frame authority. */
class TestCdOriginalR11CheckpointBinding {
    @Test void suppressedContactDoesNotReadOrManufactureSavedHistory()throws Exception {
        // Media-backed original source factory is exercised by the real original-controller tests.
        // This pure helper verifies no new public Snapshot construction is needed for a source cut.
        var h=TestCdCheckpoint.prepared(2);var before=h.placement.capture();
        h.run();
        assertEquals(CdCheckpointSaveData.State.unknown(),h.saved);assertTrue(h.saveReads.isEmpty());assertEquals(0,h.saveCalls);assertEquals(0,h.fmCalls);
    }
    @Test void originalLazyDespawnNeverReadsSuppressedCameraDirectionOrInvalidRoutineGlobals(){
        var h=TestCdCheckpoint.prepared(3);var reads=new ArrayList<String>();
        var globals=new CdCheckpoint.DespawnReads(){
            public int cameraXWord(){reads.add("CAMERA");return 2560;}
            public int rawTimeZoneByte(){reads.add("PERIOD");return 1;}
            public int rawDirectionByte(){reads.add("DIRECTION");throw new AssertionError("Suppressed direction read");}
        };
        assertThrows(CdCheckpoint.UnsupportedCheckpoint.class,()->h.body.runWithReads(TestCdCheckpoint.WHOLE,globals));assertTrue(reads.isEmpty());
        h.keepDrawSign=true; // Constructed Draw port supplies the onscreen result required by this branch.
        h.edit(v->v.moveByte(ROUTINE,2));h.body.runWithReads(TestCdCheckpoint.WHOLE,globals);
        assertEquals(List.of("PERIOD"),reads,"Onscreen Draw suppresses camera and positive period suppresses direction");
        reads.clear();var offscreen=TestCdCheckpoint.prepared(2);
        offscreen.body.runWithReads(TestCdCheckpoint.WHOLE,globals);
        assertEquals(List.of("CAMERA","PERIOD"),reads,"The separate offscreen Draw cut reads camera before period");
    }
    @Test void originalLazyReadFaultEscapesBeforeDespawnAndRetainsDrawPrefix(){
        var h=TestCdCheckpoint.prepared(2);var original=new AssertionError("native period read");
        assertSame(original,assertThrows(AssertionError.class,()->h.body.runWithReads(TestCdCheckpoint.WHOLE,new CdCheckpoint.DespawnReads(){
            public int cameraXWord(){return 2560;}public int rawTimeZoneByte(){throw original;}public int rawDirectionByte(){throw new AssertionError("suppressed");}
        })));
        assertEquals(List.of("DRAW"),h.order);assertEquals(0x13,h.slot().id());assertTrue(h.saveReads.isEmpty());
    }
    @Test void firstAndLaterSaveReadFaultsRetainEveryCompletedStoreAndOriginalException(){
        for(var stop:CdCheckpointSaveData.Store.values()){
            var failure=new AssertionError("exact read "+stop);var state=new CdCheckpointSaveData.State[]{CdCheckpointSaveData.State.unknown()};var calls=new ArrayList<CdCheckpointSaveData.Store>();
            var port=new CdCheckpointSaveData.Port(){
                public CdCheckpointSaveData.State currentState(){return state[0];}
                public int read(CdCheckpointSaveData.Source source){if(calls.size()==stop.ordinal())throw failure;return source.width()==1?0xAB:source.width()==2?0xABCD:0xFFFFFFFF;}
                public void commit(CdCheckpointSaveData.StoreCall c){assertSame(state[0],c.expectedState());state[0]=c.finalState();calls.add(c.store());}
            };
            assertSame(failure,assertThrows(AssertionError.class,()->CdCheckpointSaveData.save(state[0],port)));
            assertEquals(List.of(CdCheckpointSaveData.Store.values()).subList(0,stop.ordinal()),calls);
            if(stop.ordinal()==0)assertEquals(CdCheckpointSaveData.State.unknown(),state[0]);
        }
    }
    @Test void savedWaterOverlapAndUnsignedTimeClampUseTheExistingValueOwner(){
        var state=new CdCheckpointSaveData.State[]{CdCheckpointSaveData.State.unknown()};var calls=new ArrayList<CdCheckpointSaveData.Store>();
        var result=CdCheckpointSaveData.save(state[0],new CdCheckpointSaveData.Port(){
            public CdCheckpointSaveData.State currentState(){return state[0];}
            public int read(CdCheckpointSaveData.Source s){return switch(s){case TIME_LONG->0xFF000001;case WATER_HEIGHT2->0xCAFE;case WATER_ROUTINE->0x7B;default->0;};}
            public void commit(CdCheckpointSaveData.StoreCall c){assertSame(state[0],c.expectedState());state[0]=c.finalState();calls.add(c.store());}
        });
        assertEquals(List.of(CdCheckpointSaveData.Store.values()),calls);assertEquals(0xCA7B,result.state().read(CdCheckpointSaveData.SavedField.WATER_HEIGHT_WORD));assertEquals(0x50000,result.state().read(CdCheckpointSaveData.SavedField.TIME_LONG));
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
