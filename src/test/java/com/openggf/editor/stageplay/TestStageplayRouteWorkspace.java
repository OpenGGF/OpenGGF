package com.openggf.editor.stageplay;

import com.openggf.control.InputHandler;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.editor.StageplayRouteJob;
import com.openggf.stageplay.route.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.openggf.editor.stageplay.StageplayWorkspace.Action.*;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestStageplayRouteWorkspace {
    @TempDir Path root;
    private StageplayDocument source() {
        return StageplayDocument.empty("s2",0,0,0,0,320,224).withDurationFrames(120)
                .withActors(List.of(new Actor("hero","Hero","s2","sonic",List.of(new ActorKey(0,100,200,0,-1,false,true,1,Interpolation.HOLD)))))
                .withPuppets(List.of(new PuppetTrack("hero",false,0,List.of())));
    }
    private class Host implements StageplayWorkspace.Host {
        StageplayRouteJob.Update update;StageplayDocument source;StageplayRouteRequest request;int applyRequests,cancelCalls;
        public StageplayDocument initialDocument(){return TestStageplayRouteWorkspace.this.source();}
        public Path initialAssetRoot(){return root;}
        public void startRoute(StageplayDocument document,Path root,StageplayRouteRequest request){
            this.source=document;this.request=request;update=new StageplayRouteJob.Update(1,StageplayRouteJob.Phase.PLANNING,"working",null);
        }
        public StageplayRouteJob.Update pollRoute(){return update;}
        public void requestRouteApply(StageplayDocument document,Path root){applyRequests++;update=new StageplayRouteJob.Update(1,StageplayRouteJob.Phase.CHECKING,"checking contents",result());}
        public void cancelRoute(){cancelCalls++;}
        StageplayRouteResult result(){
            var commands=List.of(new PuppetCommand("route.go",0,PuppetCommandKind.MOVE_TO,150,200,1,0),new PuppetCommand("route.stop",29,PuppetCommandKind.STOP,0,0,0,0));
            var candidate=source.withPuppets(List.of(new PuppetTrack("hero",false,0,commands)));
            return new StageplayRouteResult(StageplayRouteResult.Status.VERIFIED,"verified fixture","0".repeat(64),request,candidate,commands,List.of(),List.of(new StageplayRoutePoint(0,100,200,true),new StageplayRoutePoint(1,101,200,true)),1,30,true);
        }
    }
    private StageplayWorkspace start(Host host){
        var workspace=new StageplayWorkspace(host);workspace.activate();workspace.invoke(ROUTE_HELPER);
        workspace.menu().setValue("x","150");workspace.menu().setValue("y","200");workspace.menu().setValue("frames","30");workspace.menu().submit();
        assertNull(workspace.menu().error());assertNotNull(host.request);return workspace;
    }
    @Test void successfulNativeRouteAppliesOnlyAfterFreshnessAndIsOneUndoStep(){
        var host=new Host();var workspace=start(host);var before=workspace.controller().document();
        host.update=new StageplayRouteJob.Update(1,StageplayRouteJob.Phase.READY,"ready",host.result());
        workspace.tick(new InputHandler());workspace.invoke(ROUTE_HELPER);choose(workspace,0);
        assertEquals(1,host.applyRequests);assertEquals(before,workspace.controller().document());assertFalse(workspace.controller().canUndo());
        workspace.tick(new InputHandler());assertEquals(before,workspace.controller().document());
        var result=host.result();host.update=new StageplayRouteJob.Update(1,StageplayRouteJob.Phase.APPLY_READY,"fresh",result);
        workspace.tick(new InputHandler());assertEquals(result.candidate(),workspace.controller().document());
        assertTrue(workspace.controller().canUndo());workspace.invoke(UNDO);assertEquals(before,workspace.controller().document());
        assertFalse(workspace.controller().canUndo());workspace.invoke(REDO);assertEquals(result.candidate(),workspace.controller().document());
    }
    @Test void changedDocumentPlayheadSelectionOrAssetRootRejectsACompletedResult() throws Exception {
        for(String change:List.of("document","frame","selection","root")){
            var host=new Host();var workspace=start(host);var result=host.result();
            switch(change){
                case "document" -> workspace.controller().edit(d->d.withName("Changed"));
                case "frame" -> workspace.seek(1);
                case "selection" -> workspace.selectActor("");
                case "root" -> workspace.saveTo(root.resolve("moved/scene.stageplay"));
            }
            var expected=workspace.controller().document();host.update=new StageplayRouteJob.Update(1,StageplayRouteJob.Phase.APPLY_READY,"late",result);
            workspace.tick(new InputHandler());assertEquals(expected,workspace.controller().document());assertTrue(workspace.status().contains("Stale route"));
        }
    }
    @Test void cancelledJobCannotApplyLateResultAndNonEntryRequestsNeverStart(){
        var host=new Host();var workspace=start(host);var before=workspace.controller().document();var result=host.result();
        workspace.invoke(ROUTE_HELPER);choose(workspace,1); // PLANNING: refresh, cancel.
        host.update=new StageplayRouteJob.Update(1,StageplayRouteJob.Phase.APPLY_READY,"late",result);
        workspace.tick(new InputHandler());assertEquals(before,workspace.controller().document());assertFalse(workspace.controller().canUndo());
        host.request=null;workspace.seek(10);workspace.invoke(ROUTE_HELPER);
        assertNull(host.request);assertTrue(workspace.status().contains("shot entry"));
    }
    @Test void objectFormTransmitsExplicitVisualFootprintAndDoesNotPretendNativeInput(){
        var captured=new java.util.concurrent.atomic.AtomicReference<StageplayRouteRequest>();
        var object=new ObjectActor("prop","Walking prop","s2",0,0,65,0,-1,List.of(new ObjectKey(0,100,200,0,-1,false,true,Interpolation.HOLD,ObjectMode.PUPPET)),List.of());
        var workspace=new StageplayWorkspace(new StageplayWorkspace.Host(){
            public StageplayDocument initialDocument(){return StageplayDocument.empty("s2",0,0,0,0,320,224).withObjectActors(List.of(object));}
            public Path initialAssetRoot(){return root;}
            public void startRoute(StageplayDocument document,Path root,StageplayRouteRequest request){captured.set(request);}
        });
        workspace.activate();workspace.invoke(ROUTE_HELPER);
        workspace.menu().setValue("halfWidth","12");workspace.menu().setValue("height","24");workspace.menu().setValue("footOffset","10");workspace.menu().setValue("collisionPath","1");
        workspace.menu().submit();assertNull(workspace.menu().error());
        assertEquals("prop",captured.get().actorId());assertEquals(new StageplayRouteFootprint(12,24,10,1),captured.get().footprint());
    }
    @Test void interiorObjectKeyOpensTheFormButChangingItsIdToAPlayerStillRejectsTheRequest(){
        var captured=new java.util.concurrent.atomic.AtomicReference<StageplayRouteRequest>();
        var object=new ObjectActor("prop","Initialized prop","s2",0,0,65,0,-1,List.of(
                new ObjectKey(0,100,200,0,-1,false,true,Interpolation.HOLD,ObjectMode.FREE),
                new ObjectKey(30,100,200,0,-1,false,true,Interpolation.HOLD,ObjectMode.PUPPET)),List.of());
        var document=source().withObjectActors(List.of(object));
        var workspace=new StageplayWorkspace(new StageplayWorkspace.Host(){
            public StageplayDocument initialDocument(){return document;}
            public Path initialAssetRoot(){return root;}
            public void startRoute(StageplayDocument source,Path root,StageplayRouteRequest request){captured.set(request);}
        });
        workspace.activate();workspace.selectActor("hero");workspace.seek(30);workspace.invoke(ROUTE_HELPER);
        assertEquals("prop",workspace.menu().values().get("actor"),"An eligible object is preselected at its existing Puppet key");
        workspace.menu().setValue("actor","hero");workspace.menu().setValue("frames","30");workspace.menu().submit();
        assertNull(captured.get());assertTrue(workspace.menu().error().contains("shot-entry"));
        workspace.invoke(ROUTE_HELPER);workspace.menu().setValue("frames","30");workspace.menu().submit();
        assertNull(workspace.menu().error());assertEquals("prop",captured.get().actorId());assertEquals(30,captured.get().startFrame());
        assertEquals(document,workspace.controller().document(),"Opening/planning does not insert a boundary or reset the native Free prefix");
        assertFalse(workspace.controller().canUndo());
    }
    @Test void overlayIsProposedBeforeApplyPersistsInPlaybackAndHidesForUnrelatedEdits() {
        var host=new Host(); var workspace=start(host); var source=workspace.controller().document();
        var result=host.result(); host.update=new StageplayRouteJob.Update(1,StageplayRouteJob.Phase.READY,"ready",result);
        workspace.tick(new InputHandler());
        assertNotNull(workspace.routeOverlay()); assertFalse(workspace.routeOverlay().applied());
        assertEquals(source,workspace.controller().document());
        host.update=new StageplayRouteJob.Update(1,StageplayRouteJob.Phase.APPLY_READY,"fresh",result);
        workspace.tick(new InputHandler()); assertTrue(workspace.routeOverlay().applied());
        workspace.seek(15); assertNotNull(workspace.routeOverlay());
        workspace.invoke(PLAY); workspace.tick(new InputHandler()); assertNotNull(workspace.routeOverlay());
        workspace.stop(); workspace.controller().edit(d->d.withName("Changed")); assertNull(workspace.routeOverlay());
        workspace.invoke(UNDO); assertNotNull(workspace.routeOverlay());
        workspace.selectActor(""); assertNull(workspace.routeOverlay());
        workspace.selectActor("hero"); assertNotNull(workspace.routeOverlay());
        workspace.deactivate(); assertNull(workspace.routeOverlay());
    }

    @Test void walkingObjectUsesTheSameProposedAndAppliedOverlayModel() {
        var actor=new ObjectActor("walker","Walker","s2",0,0,65,0,-1,List.of(
                new ObjectKey(0,100,200,0,-1,false,true,Interpolation.HOLD,ObjectMode.PUPPET)),List.of());
        var document=StageplayDocument.empty("s2",0,0,0,0,320,224).withDurationFrames(120).withObjectActors(List.of(actor));
        var update=new java.util.concurrent.atomic.AtomicReference<StageplayRouteJob.Update>();
        var workspace=new StageplayWorkspace(new StageplayWorkspace.Host(){
            public StageplayDocument initialDocument(){return document;}
            public Path initialAssetRoot(){return root;}
            public void startRoute(StageplayDocument d,Path root,StageplayRouteRequest request){
                var commands=List.of(new ObjectCommand("move",0,ObjectCommandKind.MOVE_TO,150,200,1),
                        new ObjectCommand("stop",29,ObjectCommandKind.STOP,0,0,0));
                var candidate=d.withObjectActors(List.of(new ObjectActor(actor.id(),actor.name(),actor.gameId(),actor.zone(),actor.act(),
                        actor.objectId(),actor.subtype(),actor.layoutIndex(),actor.keys(),commands)));
                var result=new StageplayRouteResult(StageplayRouteResult.Status.VISUAL_TERRAIN,"Visual terrain", "0".repeat(64),request,
                        candidate,List.of(),commands,List.of(new StageplayRoutePoint(0,100,200,true),new StageplayRoutePoint(1,101,200,true)),2,30,false);
                update.set(new StageplayRouteJob.Update(1,StageplayRouteJob.Phase.READY,"ready",result));
            }
            public StageplayRouteJob.Update pollRoute(){return update.get();}
        });
        workspace.activate(); workspace.selectActor("walker"); workspace.invoke(ROUTE_HELPER);
        workspace.menu().setValue("x","150");workspace.menu().setValue("y","200");workspace.menu().setValue("frames","30");workspace.menu().submit();
        assertNull(workspace.menu().error());workspace.tick(new InputHandler());
        assertEquals("walker",workspace.controller().selectedActorId());assertNotNull(workspace.routeOverlay());
        var result=update.get().result();update.set(new StageplayRouteJob.Update(1,StageplayRouteJob.Phase.APPLY_READY,"fresh",result));
        workspace.tick(new InputHandler());workspace.seek(20);assertTrue(workspace.routeOverlay().applied());
        assertEquals(result.path(),workspace.routeOverlay().path());
        workspace.controller().edit(d->d.withObjectActors(List.of()));assertEquals("",workspace.controller().selectedActorId());
        assertNull(workspace.routeOverlay());
    }

    @Test void replanningAndActionEditingKeepTheSavedMotionPolicy() throws Exception {
        var motion=new RouteMotion(StartAction.SPINDASH,TravelSpeed.RUN,EndingAction.OVERSHOOT,EndingFacing.LEFT);
        var move=new PuppetCommand("saved-route",0,PuppetCommandKind.MOVE_TO,150,200,1,0,
                ObjectTarget.none(),WaitCondition.NONE,0,motion);
        var document=source().withPuppets(List.of(new PuppetTrack("hero",false,0,List.of(move))));
        assertEquals(document,com.openggf.tools.StageplayScriptCompiler.compile(
                com.openggf.tools.StageplayScriptDecompiler.decompile(document)));
        var request=new java.util.concurrent.atomic.AtomicReference<StageplayRouteRequest>();
        var workspace=new StageplayWorkspace(new StageplayWorkspace.Host(){
            public StageplayDocument initialDocument(){return document;}
            public Path initialAssetRoot(){return root;}
            public java.util.Set<com.openggf.game.CharacterDefinition.MovementAbility> movementAbilities(Actor actor,Path root){
                return java.util.Set.of(com.openggf.game.CharacterDefinition.MovementAbility.SPINDASH);
            }
            public void startRoute(StageplayDocument document,Path root,StageplayRouteRequest r){request.set(r);}
        });
        workspace.activate();workspace.selectActor("hero");workspace.invoke(ROUTE_HELPER);
        assertEquals("Spindash",workspace.menu().values().get("startingAction"));
        assertEquals("Run",workspace.menu().values().get("travelSpeed"));
        assertEquals("Overshoot",workspace.menu().values().get("endingAction"));
        assertEquals("Left",workspace.menu().values().get("endingFacing"));
        workspace.menu().submit();assertNull(workspace.menu().error());assertEquals(motion,request.get().motion());
        workspace.invoke(PUPPET_ACTIONS);
        // Three core actions, five native intents, placement, visibility, then the saved action.
        choose(workspace,10);choose(workspace,0);workspace.menu().setValue("x","155");workspace.menu().submit();
        assertNull(workspace.menu().error());
        assertEquals(motion,workspace.controller().document().puppets().getFirst().commands().getFirst().motion());
    }

    @Test void unavailableSpindashIsNotAmongCyclingStartChoices() {
        var workspace=new StageplayWorkspace(new Host());workspace.activate();workspace.invoke(ROUTE_HELPER);
        // Starting action follows actor, X, Y and frame budget.
        choose(workspace,4);assertEquals("Jump",workspace.menu().values().get("startingAction"));
        press(workspace,new InputHandler(),GLFW_KEY_ENTER);
        assertEquals("Walk",workspace.menu().values().get("startingAction"));
    }

    @Test void selectingSpindashForcesRunAndInvalidPairIsRejected() {
        var workspace=new StageplayWorkspace(new Host(){
            public java.util.Set<com.openggf.game.CharacterDefinition.MovementAbility> movementAbilities(Actor actor,Path root){
                return java.util.Set.of(com.openggf.game.CharacterDefinition.MovementAbility.SPINDASH);
            }
        });
        workspace.activate();workspace.invoke(ROUTE_HELPER);
        workspace.menu().setValue("startingAction","Spindash");
        assertEquals("Run",workspace.menu().values().get("travelSpeed"));
        workspace.menu().setValue("travelSpeed","Walk");workspace.menu().submit();
        assertTrue(workspace.menu().error().contains("Spindash requires Run"));
    }

    @Test void savedUnavailableSpindashCanBeRepairedInTheRouteForm() {
        var motion=new RouteMotion(StartAction.SPINDASH,TravelSpeed.RUN,EndingAction.HARD_STOP,EndingFacing.INHERIT);
        var doc=source().withPuppets(List.of(new PuppetTrack("hero",false,0,List.of(new PuppetCommand(
                "saved",0,PuppetCommandKind.MOVE_TO,150,200,1,0,ObjectTarget.none(),WaitCondition.NONE,0,motion)))));
        var workspace=new StageplayWorkspace(new Host(){public StageplayDocument initialDocument(){return doc;}});
        workspace.activate();workspace.invoke(ROUTE_HELPER);
        assertTrue(workspace.menu().isOpen());
        assertEquals("Spindash (unavailable)",workspace.menu().values().get("startingAction"));
        workspace.menu().submit();assertTrue(workspace.menu().error().contains("unavailable"));
        workspace.invoke(ROUTE_HELPER);choose(workspace,4);
        assertEquals("Walk",workspace.menu().values().get("startingAction"));
    }

    private static void choose(StageplayWorkspace workspace,int index){
        var input=new InputHandler();for(int i=0;i<index;i++)press(workspace,input,GLFW_KEY_DOWN);press(workspace,input,GLFW_KEY_ENTER);
    }
    private static void press(StageplayWorkspace workspace,InputHandler input,int key){
        input.handleKeyEvent(key,GLFW_PRESS);input.refreshLogicalSnapshot();workspace.menu().update(input);input.update();
        input.handleKeyEvent(key,GLFW_RELEASE);input.update();
    }
}
