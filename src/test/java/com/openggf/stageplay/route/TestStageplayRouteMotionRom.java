package com.openggf.stageplay.route;

import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.*;
import com.openggf.stageplay.runtime.*;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native EHZ route contracts. Optional sources/state feed the public motion-evidence capture.
 * Origin: 2026-10-23 route motion settings. Inputs: existing S2 ROM, optional evidence directory.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestStageplayRouteMotionRom {
    @Test void rehearsalSettingsUseNativeInputsAndSurviveColdReplayAndSeeking() throws Exception {
        com.openggf.game.GameServices.graphics().initHeadless();
        SessionManager.closeGameplaySession();
        var motions=new LinkedHashMap<String,RouteMotion>();
        motions.put("walk",RouteMotion.DEFAULT);
        motions.put("run-gentle",new RouteMotion(StartAction.WALK,TravelSpeed.RUN,EndingAction.GENTLE_STOP,EndingFacing.INHERIT));
        motions.put("run-hard",new RouteMotion(StartAction.WALK,TravelSpeed.RUN,EndingAction.HARD_STOP,EndingFacing.INHERIT));
        motions.put("run-overshoot",new RouteMotion(StartAction.WALK,TravelSpeed.RUN,EndingAction.OVERSHOOT,EndingFacing.INHERIT));
        motions.put("jump",new RouteMotion(StartAction.JUMP,TravelSpeed.RUN,EndingAction.HARD_STOP,EndingFacing.INHERIT));
        motions.put("spindash",new RouteMotion(StartAction.SPINDASH,TravelSpeed.RUN,EndingAction.HARD_STOP,EndingFacing.INHERIT));
        motions.put("face-left",new RouteMotion(StartAction.WALK,TravelSpeed.WALK,EndingAction.GENTLE_STOP,EndingFacing.LEFT));
        motions.put("face-right",new RouteMotion(StartAction.WALK,TravelSpeed.WALK,EndingAction.GENTLE_STOP,EndingFacing.RIGHT));
        try(var library=SceneRomLibrary.open(Map.of("s2",Path.of(System.getProperty("sonic2.rom.path")))) ) {
            for(var entry:motions.entrySet()) {
                var motion=entry.getValue();var source=scene(entry.getKey());
                var request=new StageplayRouteRequest("hero",0,400,624,420,1024,120000,60000,1,null,1,motion);
                StageplayRouteResult result;
                try(var resources=new StageplayResources(library,false)) {
                    result=StageplayRoutePlanner.plan(resources,library,source,request,"0".repeat(64),()->false);
                }
                assertTrue(result.success(),entry.getKey()+": "+result.message());
                assertEquals(1,result.commands().stream().filter(c->c.kind()==PuppetCommandKind.MOVE_TO).count());
                if(motion.startingAction()==StartAction.WALK)
                    assertTrue(result.commands().stream().noneMatch(c->c.kind()==PuppetCommandKind.JUMP),entry.getKey());
                if(motion.startingAction()==StartAction.JUMP)
                    assertEquals(result.commands().stream().filter(c->c.kind()==PuppetCommandKind.MOVE_TO).findFirst().orElseThrow().frame(),
                            result.commands().stream().filter(c->c.kind()==PuppetCommandKind.JUMP).findFirst().orElseThrow().frame());
                var candidate=StageplayStore.decode(StageplayStore.encode(result.candidate()));
                assertEquals(motion,candidate.puppets().getFirst().commands().stream().filter(c->c.kind()==PuppetCommandKind.MOVE_TO).findFirst().orElseThrow().motion());
                boolean charged=false,skidded=false;double farthest=0;
                var states=new StringBuilder();
                try(var resources=new StageplayResources(library,false);var world=StageplayLiveWorld.openOffline(library)) {
                    resources.setLiveWorld(world);StageplayTimeline.Frame last=null;
                    for(int frame=0;frame<420;frame++) {
                        last=resources.resolveFrame(candidate,StageplayTimeline.sample(candidate,frame));
                        var nativeState=world.routeMotionState("hero");
                        charged|=nativeState.spindash();skidded|=nativeState.skidding();
                        farthest=Math.max(farthest,last.actors().getFirst().x());
                        if(motion.travelSpeed()==TravelSpeed.WALK)
                            assertTrue(Math.max(Math.abs(nativeState.groundSpeed()),Math.abs(nativeState.xSpeed()))<=StageplayRouteSteering.STROLL_CAP,entry.getKey()+" frame "+frame);
                        states.append(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("frame",frame,"pose",last.actors().getFirst(),"puppet",last.puppets().getFirst(),"native",nativeState))).append('\n');
                    }
                    assertTrue(Math.abs(last.actors().getFirst().x()-400)<=6);
                    assertTrue(Math.abs(last.puppets().getFirst().groundSpeed())<=.25);
                    if(motion.endingFacing()!=EndingFacing.INHERIT)
                        assertEquals(motion.endingFacing()==EndingFacing.LEFT,world.routeMotionState("hero").facingLeft());
                    if(motion.endingAction()==EndingAction.GENTLE_STOP) assertFalse(skidded,entry.getKey());
                    if(motion.startingAction()==StartAction.SPINDASH) assertTrue(charged);
                    if(motion.endingAction()==EndingAction.OVERSHOOT) assertTrue(farthest>=412);
                    resources.resolveFrame(candidate,StageplayTimeline.sample(candidate,20));
                    assertEquals(last,resources.resolveFrame(candidate,StageplayTimeline.sample(candidate,419)),"Controller phase rewinds with native world");
                }
                if(System.getProperty("openggf.stageplay.motionEvidence")!=null) {
                    Path out=Path.of(System.getProperty("openggf.stageplay.motionEvidence"));Files.createDirectories(out);
                    StageplayStore.write(out.resolve(entry.getKey()+".stageplay"),candidate);
                    Files.writeString(out.resolve(entry.getKey()+"-state.jsonl"),states);
                    Files.writeString(out.resolve(entry.getKey()+"-result.json"),StageplayRouteProtocol.encodeResult(result));
                }
            }
        }
    }
    static StageplayDocument scene(String name) {
        return StageplayDocument.empty("s2",0,0,0,480,480,270).withName("Motion: "+name).withDurationFrames(420)
                .withActors(List.of(new Actor("hero","Sonic","s2","sonic",List.of(new ActorKey(0,100,656,0,-1,false,true,1,Interpolation.HOLD)))))
                .withPuppets(List.of(new PuppetTrack("hero",true,0,List.of())))
                .withWorldCommands(List.of(new WorldCommand("clear",0,WorldCommandKind.CLEAR_OBJECTS,"",ObjectTarget.none())));
    }
}
