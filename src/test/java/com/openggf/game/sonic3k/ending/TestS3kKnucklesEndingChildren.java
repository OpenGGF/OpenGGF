package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Independent managed graph, allocation and literal native phase boundaries. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kKnucklesEndingChildren {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    private HeadlessTestFixture boot(int progress){
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);
        GameServices.camera().refreshViewportDimensions(config);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        var f=HeadlessTestFixture.builder().withZoneAndAct(10,1).withFreshLevelStartLifecycle().build();
        // Matched cold final-fight handoff: native Camera_X=$1CB. Camera zero
        // legitimately wraps the unsigned turnaround threshold to $FFC0,
        // retiring the passenger on the next dispatch instead of this stock turn.
        GameServices.camera().setX((short)0x1CB);GameServices.camera().setXCopy((short)0x1CB);
        var game=GameServices.gameState();game.configureSpecialStageProgress(7,7);
        for(int i=0;i<7&&progress>=0;i++){game.markEmeraldCollected(i);if(progress>0)game.markSuperEmeraldCollected(i);}
        return f;
    }
    private EndingKnucklesFlightObjectInstance owner(){return GameServices.level().getObjectManager()
            .activeObjectsOfType(EndingKnucklesFlightObjectInstance.class).getFirst();}
    private java.util.List<EndingKnucklesFlightChildObjectInstance> children(){return GameServices.level().getObjectManager()
            .activeObjectsOfType(EndingKnucklesFlightChildObjectInstance.class).stream().filter(c->!c.isDestroyed()).toList();}
    private void tick(HeadlessTestFixture f,int clock){
        // The local oracle walks this managed graph in ascending native SST order,
        // including children admitted after the current SST in this same pass.
        owner().update(clock,f.sprite());int last=-1;
        while(true){int after=last;var next=children().stream().filter(c->c.getSlotIndex()>after)
                .min(Comparator.comparingInt(EndingKnucklesFlightChildObjectInstance::getSlotIndex)).orElse(null);
            if(next==null)break;last=next.getSlotIndex();next.update(clock,f.sprite());}
        for(var flash:GameServices.level().getObjectManager().activeObjectsOfType(EndingKnucklesPaletteFlashObjectInstance.class))
            if(!flash.isDestroyed())flash.update(clock,f.sprite());
    }
    private void advanceTo(HeadlessTestFixture f,int routine){
        for(int i=0;i<1200&&owner().routine()!=routine;i++)tick(f,i);
        assertEquals(routine,owner().routine());
    }
    @ParameterizedTest @ValueSource(ints={-2,0,2})
    void nativePhaseListsAreManagedAndRetireOnTheirOwningFlags(int progress){
        var f=boot(progress);GameServices.level().getObjectManager().createDynamicObject(EndingKnucklesFlightObjectInstance::new);
        tick(f,0);
        assertEquals(progress>=0?3:2,children().size());
        assertEquals(1,children().stream().filter(c->c.role()==0).count());
        assertEquals(1,children().stream().filter(c->c.role()==1).count());
        assertEquals(progress>=0?1:0,children().stream().filter(c->c.role()==2).count());
        var plane=children().stream().filter(c->c.role()==0).findFirst().orElseThrow();
        assertEquals(owner().getX()-0x20,plane.getX());assertEquals(owner().getY()+0x2B,plane.getY());
        advanceTo(f,10);
        assertEquals(java.util.List.of(3,4,4,5),children().stream().map(c->c.role()).sorted().toList());
        assertEquals(java.util.List.of(0,2),children().stream().filter(c->c.role()==4).map(c->c.subtype()).sorted().toList());
        assertEquals(progress>=0?0x15:0x14,children().stream().filter(c->c.role()==4&&c.subtype()==2)
                .findFirst().orElseThrow().mappingFrame());
        tick(f,1); // loc_5EC4E/sub_6008A starts after the initializer's return.
        var pose=children().stream().filter(c->c.role()==3).findFirst().orElseThrow();
        assertEquals(owner().getX()+0xA,pose.getX());assertEquals(owner().getY()+0xC,pose.getY());
        advanceTo(f,12);
        assertEquals(java.util.List.of(6),children().stream().map(c->c.role()).toList());
        assertEquals(0,children().getFirst().isHighPriority()?1:0,"small prop inherits native low priority");
        advanceTo(f,14);
        assertEquals(progress>=0?1:0,GameServices.level().getObjectManager()
                .activeObjectsOfType(EndingKnucklesPaletteFlashObjectInstance.class).size());
        var state=(SszZoneRuntimeState)GameServices.zoneRuntimeState();state.setEndingCloudAligned(true);
        advanceTo(f,16);owner().refreshPostCameraRenderState();
        for(int i=0;i<1000&&owner().routine()!=18;i++){tick(f,i);owner().refreshPostCameraRenderState();}
        assertEquals(18,owner().routine());assertTrue(children().isEmpty());
    }
    @Test void nativeAfterCurrentAllocationDoesNotInventChildrenInFreeEarlierSlots(){
        var f=boot(0);var manager=GameServices.level().getObjectManager();
        var parent=manager.createDynamicObjectAtSlot(EndingKnucklesFlightObjectInstance::new,
                com.openggf.level.objects.ObjectSlotLayout.SONIC_3K.lastDynamicSlotExclusive()-1);
        assertNotNull(parent);parent.update(0,f.sprite());
        assertTrue(children().isEmpty(),"CreateChild1_Normal scans only SSTs after the parent");
        assertEquals(2,parent.routine());
    }
    @Test void completeRegistryRestoresParentLinksAndReplaysTheChangingGraph(){
        var f=boot(2);GameServices.level().getObjectManager().createDynamicObject(EndingKnucklesFlightObjectInstance::new);
        tick(f,0);advanceTo(f,8);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        advanceTo(f,14);var expected=registry.capture();
        registry.restore(saved);
        advanceTo(f,14);var actual=registry.capture();
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(String key:expected.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),key);
        assertEquals(java.util.List.of(6),children().stream().map(c->c.role()).toList());
    }
    @Test void allocatedPaletteFlashPreservesColorZeroAndReplaysTheNativeHold(){
        var f=boot(0);var s=TestEnvironment.objectServices();var registry=s.paletteOwnershipRegistryOrNull();
        byte[] normal=new byte[32],target=new byte[32];
        for(int i=0;i<16;i++){normal[i*2]=target[i*2]=2;normal[i*2+1]=target[i*2+1]=0x22;}
        normal[0]=0xA;normal[1]=(byte)0xCE;
        com.openggf.game.sonic3k.S3kPaletteWriteSupport.applyLine(registry,s.currentLevel(),s.graphicsManager(),
                "ending-vector",com.openggf.game.sonic3k.S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,2,normal,true);
        registry.applyTargetPatch("ending-vector",2,0,target);
        var flash=GameServices.level().getObjectManager().createDynamicObject(EndingKnucklesPaletteFlashObjectInstance::new);
        flash.update(0,f.sprite());assertEquals(0x444,colorWord(1));assertEquals(0xACE,colorWord(0));
        for(int i=1;i<=12;i++)flash.update(i,f.sprite());assertEquals(0x888,colorWord(1));
        var timeline=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=timeline.capture();
        for(int i=13;i<32;i++)flash.update(i,f.sprite());assertEquals(0x888,colorWord(1));
        flash.update(32,f.sprite());assertEquals(0x666,colorWord(1));
        for(int i=33;i<=40;i++)flash.update(i,f.sprite());assertEquals(0x222,colorWord(1));assertTrue(flash.isDestroyed());
        timeline.restore(saved);
        flash=GameServices.level().getObjectManager().activeObjectsOfType(EndingKnucklesPaletteFlashObjectInstance.class).getFirst();
        for(int i=13;i<=40;i++)flash.update(i,f.sprite());
        assertEquals(0x222,colorWord(1));assertEquals(0xACE,colorWord(0));assertTrue(flash.isDestroyed());
    }
    private int colorWord(int color){return com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
            GameServices.level().getCurrentLevel().getPalette(2).getColor(color));}
}
