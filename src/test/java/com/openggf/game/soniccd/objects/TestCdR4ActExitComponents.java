package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdR4ActExit;
import com.openggf.game.soniccd.CdR4ProgrammeTransitions;
import com.openggf.game.soniccd.level.CdAssetBytes;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.openggf.game.soniccd.CdR4ProgrammeTransitions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal IPX and Results vectors. Constructed scalars and real shared Results component ingress,
 * no completed Results values used as a programme call, no live World/campaign claim. */
class TestCdR4ActExitComponents {
    static OptionalInt k(int n){return OptionalInt.of(n);}static OptionalInt u(){return OptionalInt.empty();}
    static State state(int lives,int era,int good,int special,int saved,int stones){return new State(k(lives),k(era),k(good),k(special),k(saved),k(stones));}
    @Test void ordinaryReturnChecksLivesBeforeMarkedEraThenSpecial(){
        assertEquals(Cut.GAME_OVER,ordinaryReturned(new State(k(0),u(),u(),u(),u(),u())).cut());
        assertEquals(Cut.MISSING_LIVES,ordinaryReturned(new State(u(),k(128),u(),u(),u(),u())).cut());
        assertEquals(Cut.RUN_WARP,ordinaryReturned(new State(k(1),k(128),u(),u(),u(),u())).cut());
        assertEquals(Cut.MISSING_SPECIAL_STAGE,ordinaryReturned(new State(k(1),k(2),u(),u(),u(),u())).cut());
        assertEquals(Cut.RUN_SPECIAL_STAGE,ordinaryReturned(state(1,2,0,1,2,0)).cut());
    }
    @Test void warpReturnUsesOriginalActAndMaskedEraAndDoesNotReadGoodForPastOrPresent(){
        for(int act=0;act<2;act++) {
            State past=new State(u(),k(0x80),u(),u(),u(),u());State present=new State(u(),k(0x81),u(),u(),u(),u());
            assertEquals(CdNativeFile.valueOf("R4"+(act+1)+"B"),warpReturned(act,past).nextFile().orElseThrow());
            assertEquals(CdNativeFile.valueOf("R4"+(act+1)+"A"),warpReturned(act,present).nextFile().orElseThrow());
            assertEquals(CdNativeFile.valueOf("R4"+(act+1)+"C"),warpReturned(act,state(1,0x82,1,0,2,0)).nextFile().orElseThrow());
            assertEquals(CdNativeFile.valueOf("R4"+(act+1)+"D"),warpReturned(act,state(1,0xFF,0,0,2,0)).nextFile().orElseThrow());
        }
        assertEquals(Cut.MISSING_GOOD_FUTURE,warpReturned(0,new State(u(),k(2),u(),u(),u(),u())).cut());
    }
    @Test void bossProgrammeSelectionIgnoresRetainedArtAndAct(){assertEquals(CdNativeFile.R43C,bossEntry(state(1,2,1,0,2,0)).nextFile().orElseThrow());assertEquals(CdNativeFile.R43D,bossEntry(state(1,0,0,0,2,127)).nextFile().orElseThrow());}
    @Test void bossReturnCapsAfterByteIncrementAndClearsCheckpointOnlyAfterLives(){
        assertTrue(bossReturned(new State(k(0),u(),u(),u(),u(),u())).completedStores().isEmpty());
        assertEquals(Cut.MISSING_SAVED_LEVEL,bossReturned(new State(k(1),u(),u(),u(),u(),u())).cut());
        assertEquals(3,bossReturned(state(1,2,0,0,2,0)).savedLevelAfter().orElseThrow());
        assertEquals(Cut.R4_UNLOCK_TIME_ATTACK,bossReturned(state(1,2,0,0,2,0)).cut());
        assertEquals(9,CdR4ActExit.campaignUnlockLevelId());assertEquals(2,CdR4ActExit.campaignGoodFutureBit());
        assertEquals(0x300,CdR4ActExit.followingCampaignZoneAct());assertEquals(CdNativeFile.R51A,CdR4ActExit.followingCampaignProgramme());
        assertEquals(List.of(Store.SAVED_LEVEL_INCREMENT,Store.SAVED_LEVEL_CAP_DECREMENT,Store.CHECKPOINT_ZERO),bossReturned(state(1,2,0,0,6,0)).completedStores());
        assertEquals(6,bossReturned(state(1,2,0,0,6,0)).savedLevelAfter().orElseThrow());
        assertEquals(0,bossReturned(state(1,2,0,0,255,0)).savedLevelAfter().orElseThrow());
    }
    @Test void specialReturnRetainsPalettePrefixOnUnknownStones(){var r=specialReturned(new State(u(),u(),u(),u(),u(),u()));assertEquals(Cut.MISSING_TIME_STONES,r.cut());assertEquals(List.of(Store.PALETTE_CLEAR_ONE),r.completedStores());assertEquals(List.of(Store.PALETTE_CLEAR_ONE,Store.GOOD_FUTURE_ONE),specialReturned(state(1,2,0,1,2,127)).completedStores());}
    @Test void ttProfilesRetainNativeTilesAndAct3HiddenGoal(){assertEquals(0x84F2,CdR4ActExit.goalTileWord());assertEquals(0x843C,CdR4ActExit.signpostTileWord());assertTrue(CdR4ActExit.goalDraws(0));assertFalse(CdR4ActExit.goalDraws(2));assertEquals(18,CdR4ActExit.stageEndPlcId());assertEquals(4,CdR4ActExit.chaseBubblePlcId());}
    @Test void sharedResultsExecutesActualNextStageIngressForAllThreeTtActs(){
        for(int originalAct=0;originalAct<3;originalAct++){
            var h=new TestCdR4BossComponents.H();h.put(70,0x3A,8);
            var fields=new EnumMap<CdResults.ReadField,Integer>(CdResults.ReadField.class);
            for(var f:CdResults.ReadField.values())fields.put(f,0);
            fields.put(CdResults.ReadField.ZONE_ACT_WORD,0x200+originalAct);fields.put(CdResults.ReadField.ACT_BYTE,originalAct);
            fields.put(CdResults.ReadField.GOOD_FUTURE_BYTE,1);fields.put(CdResults.ReadField.GOOD_FUTURE_FLAGS_BYTE,3);
            List<String> events=new ArrayList<>();List<Integer> zones=new ArrayList<>(),eras=new ArrayList<>();
            // These empty constructed views are never read by routine8; not pretend media inputs.
            var a=new CdResults.Assets(CdAssetBytes.copyOf(new byte[0]),CdAssetBytes.copyOf(new byte[0]),0);
            var results=new CdResults(h.placement,a,c->{events.add("read:"+c.field());return fields.get(c.field());},null,null,null,null,
                c->events.add("draw"),c->{events.add("store:"+c.field()+":"+c.value());if(c.field()==CdResults.ProgrammeField.ZONE_ACT_WORD){zones.add(c.value());fields.put(CdResults.ReadField.ZONE_ACT_WORD,c.value());fields.put(CdResults.ReadField.ACT_BYTE,c.value()&255);}
                    if(c.field()==CdResults.ProgrammeField.GOOD_FUTURE_BYTE)fields.put(CdResults.ReadField.GOOD_FUTURE_BYTE,c.value());
                    if(c.field()==CdResults.ProgrammeField.GOOD_FUTURE_FLAGS_CLEAR_BYTE)fields.put(CdResults.ReadField.GOOD_FUTURE_FLAGS_BYTE,0);
                    if(c.field()==CdResults.ProgrammeField.GOOD_FUTURE_FLAGS_SET_BIT)fields.put(CdResults.ReadField.GOOD_FUTURE_FLAGS_BYTE,fields.get(CdResults.ReadField.GOOD_FUTURE_FLAGS_BYTE)|(1<<c.value()));if(c.field()==CdResults.ProgrammeField.TIME_ZONE_BYTE)eras.add(c.value());},
                c->{events.add("action:"+c.action());if(c.action()==CdResults.ProgrammeAction.RESET_SAVED_OBJECT_FLAGS)h.placement.resetSavedObjectFlags();});
            results.run(70);
            assertEquals(List.of(originalAct==2?0x300:0x201+originalAct),zones);
            assertEquals(originalAct==1?List.of(1,2):List.of(1),eras);
            assertEquals("store:LEVEL_RESTART_WORD:2",events.getFirst());
            assertTrue(events.indexOf("action:FADE_OUT_MUSIC")<events.indexOf("draw"));assertTrue(events.indexOf("draw")<events.indexOf("read:ACT_BYTE"));
            assertEquals(0x3A,h.pool.state(70).id()); // Results completion neither retires nor calls IPX
        }
    }
    @Test void sharedResultsMissingContinuationRetainsExactEarlierStores(){
        var h=new TestCdR4BossComponents.H();h.put(70,0x3A,8);List<CdResults.ProgrammeField> stored=new ArrayList<>();
        var a=new CdResults.Assets(CdAssetBytes.copyOf(new byte[0]),CdAssetBytes.copyOf(new byte[0]),0);
        RuntimeException firstMissing=new RuntimeException("real programme owner missing");
        var results=new CdResults(h.placement,a,null,null,null,null,null,null,c->{if(c.field()==CdResults.ProgrammeField.SECTION_ID_WORD)throw firstMissing;stored.add(c.field());},null);
        assertSame(firstMissing,assertThrows(RuntimeException.class,()->results.run(70)));
        assertEquals(List.of(CdResults.ProgrammeField.LEVEL_RESTART_WORD,CdResults.ProgrammeField.SPAWN_MODE_BYTE),stored);
    }
}
