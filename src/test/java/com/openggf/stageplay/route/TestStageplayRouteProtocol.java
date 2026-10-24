package com.openggf.stageplay.route;

import com.openggf.stageplay.StageplayDocument.Facing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.stageplay.StageplayDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayRouteProtocol {
    public static StageplayDocument source() {
        return StageplayDocument.empty("s2",0,0,0,0,320,224).withDurationFrames(120)
                .withActors(List.of(new Actor("hero","Hero","s2","sonic",List.of(new ActorKey(0,100,200,0,-1, Facing.RIGHT,true,1,Interpolation.HOLD)))))
                .withPuppets(List.of(new PuppetTrack("hero",false,0,List.of(new PuppetCommand("later",90,PuppetCommandKind.STOP,0,0,0,0)))));
    }
    public static StageplayRouteResult result(StageplayDocument source,StageplayRouteRequest request,String fingerprint) {
        var generated=List.of(new PuppetCommand("route.go",0,PuppetCommandKind.MOVE_TO,150,200,1,0),
                new PuppetCommand("route.stop",29,PuppetCommandKind.STOP,0,0,0,0));
        var all=new java.util.ArrayList<>(generated);all.addAll(source.puppets().getFirst().commands());
        var candidate=source.withPuppets(List.of(new PuppetTrack("hero",false,0,all)));
        return new StageplayRouteResult(StageplayRouteResult.Status.VERIFIED,"fixture response",fingerprint,request,candidate,generated,List.of(),
                List.of(new StageplayRoutePoint(29,150,200,true)),1,30,true);
    }
    @Test void commandOnlyPatchRoundTripsAndPreservesEveryUnrelatedField() throws Exception {
        var source=source();var request=new StageplayRouteRequest("hero",0,150,200,30);var result=result(source,request,"0".repeat(64));
        var decoded=StageplayRouteProtocol.decodeResult(StageplayRouteProtocol.encodeResult(result));
        assertEquals(result,decoded);StageplayRouteProtocol.validatePatch(source,request,result.fingerprint(),decoded);
        var changedTrack=new PuppetTrack("hero",true,0,result.candidate().puppets().getFirst().commands());
        var changedCollisionPath=new PuppetTrack("hero",false,1,result.candidate().puppets().getFirst().commands());
        for(var modified:List.of(result.candidate().withName("tampered"),result.candidate().withCamera(List.of(new CameraKey(0,42,0,1,Interpolation.HOLD))),
                result.candidate().withPuppets(List.of(changedTrack)),result.candidate().withPuppets(List.of(changedCollisionPath)))) {
            var forged=new StageplayRouteResult(result.status(),result.message(),result.fingerprint(),request,modified,result.commands(),List.of(),result.path(),1,30,true);
            assertThrows(IOException.class,()->StageplayRouteProtocol.validatePatch(source,request,result.fingerprint(),forged),
                    "Candidate must preserve scene fields and Puppet floor/collision policy: "+modified);
        }
        assertThrows(IOException.class,()->StageplayRouteProtocol.validatePatch(source,request,"1".repeat(64),result));
        assertEquals("later",result.candidate().puppets().getFirst().commands().getLast().id());
    }
    @Test void protocolRejectsUnknownDuplicateTrailingAndCoercedFields() throws Exception {
        var request=new StageplayRouteRequest("hero",0,150,200,30);
        var snapshot=new StageplayRouteInputs.Snapshot(source(),request,Path.of("/").toAbsolutePath(),"{}","{}","0".repeat(64));
        String envelope=StageplayRouteProtocol.envelope(snapshot);
        assertEquals(request,StageplayRouteProtocol.decodeEnvelope(envelope).request());
        for(String invalid:List.of(envelope+"{}",envelope.replaceFirst("\\{","{\"version\":1,"),
                envelope.replace("\"startFrame\":0","\"startFrame\":\"0\""),envelope.replaceFirst("\\{","{\"unknown\":1,")))
            assertThrows(IOException.class,()->StageplayRouteProtocol.decodeEnvelope(invalid));
        var json=new ObjectMapper();var response=json.readTree(StageplayRouteProtocol.encodeResult(result(source(),request,"0".repeat(64))));
        ((com.fasterxml.jackson.databind.node.ObjectNode)response).put("nativeVerified","true");
        assertThrows(IOException.class,()->StageplayRouteProtocol.decodeResult(response.toString()));
    }
    @Test void objectPatchAtAnInteriorPuppetKeyCannotRewriteItsNativeFreePrefix() throws Exception {
        var keys=List.of(new ObjectKey(0,100,200,0,-1, Facing.LEFT,true,Interpolation.HOLD,ObjectMode.FREE),
                new ObjectKey(30,100,200,0,-1, Facing.LEFT,true,Interpolation.HOLD,ObjectMode.PUPPET));
        var later=new ObjectCommand("later",90,ObjectCommandKind.STOP,0,0,0);
        var actor=new ObjectActor("prop","Prop","s2",0,0,65,0,-1,keys,List.of(later));
        var source=StageplayDocument.empty("s2",0,0,0,0,320,224).withDurationFrames(120).withObjectActors(List.of(actor));
        var request=new StageplayRouteRequest("prop",30,150,200,10);
        var generated=List.of(new ObjectCommand("route.go",30,ObjectCommandKind.MOVE_TO,150,200,1),
                new ObjectCommand("route.stop",39,ObjectCommandKind.STOP,0,0,0));
        var commands=new java.util.ArrayList<>(generated);commands.add(later);
        var changed=new ObjectActor(actor.id(),actor.name(),actor.gameId(),actor.zone(),actor.act(),actor.objectId(),actor.subtype(),actor.layoutIndex(),keys,commands);
        var candidate=source.withObjectActors(List.of(changed));
        var result=new StageplayRouteResult(StageplayRouteResult.Status.VISUAL_TERRAIN,"visual only","0".repeat(64),request,candidate,
                List.of(),generated,List.of(),1,10,false);
        StageplayRouteProtocol.validatePatch(source,request,result.fingerprint(),result);
        var wrongKeys=List.of(new ObjectKey(0,101,200,0,-1, Facing.LEFT,true,Interpolation.HOLD,ObjectMode.FREE),keys.getLast());
        var wrongActor=new ObjectActor(actor.id(),actor.name(),actor.gameId(),actor.zone(),actor.act(),actor.objectId(),actor.subtype(),actor.layoutIndex(),wrongKeys,commands);
        var forged=new StageplayRouteResult(result.status(),result.message(),result.fingerprint(),request,source.withObjectActors(List.of(wrongActor)),
                List.of(),generated,List.of(),1,10,false);
        assertThrows(IOException.class,()->StageplayRouteProtocol.validatePatch(source,request,result.fingerprint(),forged));
        assertEquals(keys,result.candidate().objectActors().getFirst().keys());
    }
}
