package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Obj_Ending→Obj_5EA52: native init, parent flip/priority and retained child dispatch. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingReunionPresentation {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> cases(){
        var rows=new java.util.ArrayList<org.junit.jupiter.params.provider.Arguments>();
        for(int width:new int[]{320,352,400,528,800})for(String main:new String[]{"sonic","tails"})
            rows.add(org.junit.jupiter.params.provider.Arguments.of(width,main));
        return rows.stream();
    }
    @ParameterizedTest @MethodSource("cases")
    void reunionPlaneFollowsNativeAdjustedParentAndRestoresTheManagedPilot(int width,String main){
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        GameServices.camera().refreshViewportDimensions(config);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,main);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        var f=HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
        var manager=GameServices.level().getObjectManager();
        // Independent native Obj_Ending component entry; no reference row or movie state is imported.
        for(var object:java.util.List.copyOf(manager.getActiveObjects()))manager.removeDynamicObject(object);
        var owner=manager.createDynamicObject(()->new EndingRescueDirectorObjectInstance(EndingRescueDirectorObjectInstance.REUNION));
        // HeadlessTestRunner retries SETUP_ONLY; use the lower-level existing
        // driver here so the initializer's RTS and following parent read stay distinct.
        var driver=new com.openggf.tools.RecordingFrameDriver(f.sprite());
        assertEquals(com.openggf.LevelFrameResult.SETUP_ONLY,driver.stepFrame(false,false,false,false,false));
        assertEquals(1,manager.getFrameCounter());assertEquals(0,GameServices.level().getFrameCounter());
        assertTrue(f.sprite().getRenderHFlip(),"loc_5E1F0 independently sets P1 render_flags bit0");
        var plane=manager.activeObjectsOfType(EndingRescueDirectorObjectInstance.Actor.class).getFirst();
        assertEquals(owner.getX()-0x20,plane.getX(),"CreateChild1 raw offset before first adjusted dispatch");
        assertEquals(0x40,plane.nativeSstState().renderFlags());
        assertEquals(0x200,plane.nativeSstState().priority());assertEquals(0x81E3,plane.nativeSstState().artTile());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        f.stepFrame(false,false,false,false,false);
        assertEquals(owner.getX()+0x20,plane.getX(),"Refresh_ChildPositionAdjusted negates child_dx when parent flips");
        assertEquals(owner.getY()+(main.equals("tails")?0x27:0x2B),plane.getY());
        assertEquals(0x41,plane.nativeSstState().renderFlags());
        assertEquals(0,plane.nativeSstState().priority());assertEquals(0x1E3,plane.nativeSstState().artTile());
        assertTrue(plane.flightFlipX());assertFalse(plane.isHighPriority());assertEquals(0,plane.getPriorityBucket());
        var nose=plane.subsprites().getFirst();assertEquals(plane.getX()-0x34,nose.x());
        var pilot=manager.activeObjectsOfType(EndingKnucklesFlightChildObjectInstance.class).stream()
                .filter(child->child.role()==EndingKnucklesFlightChildObjectInstance.PILOT).findFirst().orElseThrow();
        assertEquals(plane.getX(),pilot.getX());assertEquals(plane.getY()-0x10,pilot.getY());
        assertEquals(1,pilot.nativeSstState().renderFlags()&1);assertEquals(0,pilot.getPriorityBucket());
        var after=registry.capture();var ownerSlot=owner.getSlotIndex();
        for(int cycle=0;cycle<2;cycle++){
            registry.restore(before);same(before,registry.capture());f.stepFrame(false,false,false,false,false);same(after,registry.capture());
            registry.restore(before);same(before,registry.capture());registry.restore(after);same(after,registry.capture());
            assertInstanceOf(EndingRescueDirectorObjectInstance.class,NativeSstSlotAccess.currentOwner(manager,ownerSlot));
        }
    }
    private static void same(com.openggf.game.rewind.CompositeSnapshot a,com.openggf.game.rewind.CompositeSnapshot b){
        for(var key:a.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key)).isEmpty(),key);
    }
}
