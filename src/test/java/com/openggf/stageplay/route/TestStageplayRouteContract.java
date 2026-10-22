package com.openggf.stageplay.route;

import com.openggf.stageplay.StageplayDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayRouteContract {
    private StageplayDocument source() {
        var object = new ObjectActor("prop","Prop","s2",0,0,65,0,-1,List.of(
                key(0,ObjectMode.FREE,true),key(30,ObjectMode.PUPPET,true),key(50,ObjectMode.POSE,true),
                key(60,ObjectMode.FREE,true),key(70,ObjectMode.PUPPET,false),key(80,ObjectMode.PUPPET,true)),List.of());
        var player = new Actor("hero","Hero","s2","sonic",List.of(
                new ActorKey(0,100,200,0,-1,false,true,1,Interpolation.HOLD),
                new ActorKey(80,100,200,0,-1,false,true,1,Interpolation.HOLD)));
        return StageplayDocument.empty("s2",0,0,0,0,320,224).withDurationFrames(120)
                .withCamera(List.of(new CameraKey(0,0,0,1,Interpolation.HOLD),new CameraKey(80,0,0,1,Interpolation.HOLD)))
                .withActors(List.of(player)).withPuppets(List.of(new PuppetTrack("hero",false,0,List.of())))
                .withObjectActors(List.of(object)).withSceneChanges(List.of(new SceneChange("next",80,
                        new SetSource("s2",0,1,"","",WorldPolicy.TERRAIN_ONLY),Transition.CUT,0)));
    }
    private static ObjectKey key(int frame,ObjectMode mode,boolean visible) {
        return new ObjectKey(frame,100,200,0,-1,false,visible,Interpolation.HOLD,mode);
    }
    @Test void objectPuppetEntryPreservesEarlierFreeInitializationWithinTheSameShot() {
        var document=source();
        assertEquals("",StageplayRouteContract.unavailableReason(document,new StageplayRouteRequest("prop",30,150,200,50)));
        assertTrue(StageplayRouteContract.unavailableReason(document,new StageplayRouteRequest("prop",30,150,200,51)).contains("within its shot"));
        assertEquals("",StageplayRouteContract.unavailableReason(document,new StageplayRouteRequest("prop",80,150,200,40)));
        assertEquals(ObjectMode.FREE,document.objectActors().getFirst().keys().getFirst().mode());
        assertEquals(List.of(0,30,50,60,70,80),document.objectActors().getFirst().keys().stream().map(ObjectKey::frame).toList());
    }
    @Test void objectRoutesRejectNonKeyFramesFreePoseAndHiddenKeysWhilePlayersStillRequireShotEntry() {
        var document=source();
        for(int frame:List.of(0,31,50,60,70))
            assertTrue(StageplayRouteContract.unavailableReason(document,new StageplayRouteRequest("prop",frame,150,200,10)).contains("existing visible PUPPET key"),"Object frame "+frame);
        assertTrue(StageplayRouteContract.unavailableReason(document,new StageplayRouteRequest("hero",30,150,200,10)).contains("shot-entry"));
        assertEquals("",StageplayRouteContract.unavailableReason(document,new StageplayRouteRequest("hero",0,150,200,30)));
        assertEquals("",StageplayRouteContract.unavailableReason(document,new StageplayRouteRequest("hero",80,150,200,40)));
    }
}
