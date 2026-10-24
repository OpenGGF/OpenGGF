package com.openggf.editor.project;

import com.openggf.editor.stageplay.StageplayWorkspace;
import com.openggf.control.InputHandler;
import com.openggf.stageplay.StageplayDocument;
import java.util.List;
import static com.openggf.editor.stageplay.StageplayWorkspace.Action.*;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Menu inventory for the 2026-10-24 readability review. Real shared forms, no fixture UI. */
final class StageplayMenuSurvey {
    interface Picture { void take(String name, StageplayWorkspace workspace) throws Exception; }
    static void capture(Picture picture) throws Exception {
        var scene=scene();
        show(picture,scene,"route-player",ROUTE_HELPER);
        show(picture,scene,"ground-player",PLACE_ON_FLOOR);
        show(picture,scene,"open",OPEN);
        show(picture,scene,"save",SAVE_AS);
        show(picture,scene,"screenplay-import",IMPORT_SCREENPLAY);
        show(picture,scene,"screenplay-export",EXPORT_SCREENPLAY);
        var export=workspace(scene);export.saveTo(java.nio.file.Path.of("target/menu-survey/scene.stageplay"));
        export.invoke(EXPORT);picture.take("survey-film-export",export);
        show(picture,scene,"scene",SCENE);
        show(picture,scene,"add-actor",ADD_ACTOR);
        show(picture,scene,"actor-properties",EDIT_ACTOR);
        show(picture,scene,"actor-actions",SET_KEY);
        for(int row=0;row<8;row++) show(picture,scene,"actor-action-"+row,SET_KEY,row);
        show(picture,scene,"actor-placement",SET_KEY,8);
        show(picture,scene,"actor-visibility",SET_KEY,9);
        show(picture,scene.withPuppets(List.of()),"poser-key",SET_KEY);
        show(picture,scene,"camera-menu",CAMERA_KEY);
        show(picture,scene,"camera-key",CAMERA_KEY,0);
        show(picture,scene,"film-format",FILM_FORMAT);
        show(picture,scene,"camera-shake",CAMERA_EFFECTS,0);
        show(picture,scene,"scene-changes",SCENE_CHANGES);
        show(picture,scene,"scene-change",SCENE_CHANGES,0);
        show(picture,scene,"captions",CAPTION);
        show(picture,scene,"caption-form",CAPTION,0);
        show(picture,scene,"dialogue-menu",DIALOGUE);
        show(picture,scene,"dialogue-line-menu",DIALOGUE,3);
        show(picture,scene,"dialogue-line",DIALOGUE,3,0);
        show(picture,scene,"dialogue-speaker",DIALOGUE,3,1);
        show(picture,scene,"dialogue-reveal",DIALOGUE,3,4);
        show(picture,scene,"dialogue-typer",DIALOGUE_TYPERS,0);
        show(picture,scene,"dialogue-decoration",DIALOGUE_SKINS,0);
        show(picture,scene,"speaker-voice",SPEAKER_VOICE);
        show(picture,scene,"audio-menu",AUDIO);
        show(picture,scene,"audio-form",AUDIO,0);
        show(picture,scene,"objects",OBJECTS);
        show(picture,scene,"object-properties",OBJECTS,0);
        show(picture,scene,"object-menu",OBJECTS,6);
        show(picture,scene,"object-key",OBJECTS,6,1,0);
        show(picture,scene,"object-parts",OBJECTS,6,4);
        show(picture,scene,"object-commands",OBJECTS,6,2);
        show(picture,scene,"object-command",OBJECTS,6,2,0);
        show(picture,scene,"object-part-overrides",OBJECTS,6,7,0);
        show(picture,scene,"object-part-form",OBJECTS,6,7,0,0);
        show(picture,scene,"world-commands",OBJECTS,5);
        show(picture,scene,"world-command",OBJECTS,5,0);
        var objectWorkspace=workspace(scene);objectWorkspace.selectActor("prop");objectWorkspace.invoke(ROUTE_HELPER);
        picture.take("survey-route-object",objectWorkspace);
        objectWorkspace.menu().close();objectWorkspace.invoke(PLACE_ON_FLOOR);picture.take("survey-ground-object",objectWorkspace);
        // UI fixtures exercise result layouts; production route pictures separately run the native worker.
        for(boolean complete:new boolean[]{true,false}) {
            var results=new StageplayWorkspace(new StageplayWorkspace.Host() {
                public StageplayDocument initialDocument(){return scene;}
                public com.openggf.stageplay.StageplayGround.Report ground(StageplayDocument d,java.nio.file.Path root,int frame,com.openggf.stageplay.StageplayGround.Batch batch) {
                    var q=batch.queries().getFirst();
                    return new com.openggf.stageplay.StageplayGround.Report(new com.openggf.stageplay.StageplayGround.SceneFrame(d.set(),frame),
                        List.of(new com.openggf.stageplay.StageplayGround.Answer(q.id(),complete?com.openggf.stageplay.StageplayGround.Outcome.FOUND:com.openggf.stageplay.StageplayGround.Outcome.NONE_IN_BAND,
                            complete?List.of(new com.openggf.stageplay.StageplayGround.Placement(q.x(),648,667,0,new com.openggf.stageplay.StageplayGround.PlayerTerrainProposal(com.openggf.stageplay.StageplayGround.Foot.LEFT,0,9,19))):List.of(),
                            List.of(new com.openggf.stageplay.StageplayGround.Refusal(700,com.openggf.stageplay.StageplayGround.Reason.OBSTRUCTED,"There is not enough room for the actor above this floor.")),false)),
                        new com.openggf.stageplay.StageplayGround.Work(100,0,1,com.openggf.stageplay.StageplayGround.StopReason.NONE));
                }
            });
            results.activate();results.selectActor("hero");results.invoke(PLACE_ON_FLOOR);results.menu().submit();
            assertNull(results.menu().error());picture.take(complete?"survey-ground-results":"survey-ground-empty",results);
        }
        // Explicit line breaks must stay line breaks in the menu family too.
        var lines=workspace(scene);lines.menu().showActions("Two-line hint",List.of(new com.openggf.editor.project.EditorProjectMenu.Action("Continue",()->{})));
        lines.menu().setSubtitle("Review the path.\nApply when ready.");picture.take("survey-multiline-hint",lines);
    }
    static void captureCheckpoint(Picture picture) throws Exception {
        var scene=scene();
        show(picture,scene,"route-player",ROUTE_HELPER);
        show(picture,scene,"dialogue-line",DIALOGUE,3,0);
        show(picture,scene,"ground-player",PLACE_ON_FLOOR);
    }
    private static StageplayDocument scene() {
        return StageplayDocument.empty("s2",0,0,0,500,320,224).withDurationFrames(360)
            .withActors(List.of(new Actor("hero","Sonic","s2","sonic",List.of(new ActorKey(0,128,648,0,-1,Facing.RIGHT,true,1,Interpolation.HOLD)))))
            .withPuppets(List.of(new PuppetTrack("hero",true,0,List.of())))
            .withObjectActors(List.of(new ObjectActor("prop","Walking prop","s2",0,0,65,0,-1,
                List.of(new ObjectKey(0,128,648,0,-1,Facing.LEFT,true,Interpolation.HOLD,ObjectMode.PUPPET)),List.of())))
            .withDialogue(List.of(new DialogueCue("line",0,120,"hero","","Ready?\nLet's go!","","",12,140,296,72,"",List.of())));
    }

    private static StageplayWorkspace workspace(StageplayDocument scene) {
        var workspace=new StageplayWorkspace(()->scene);workspace.activate();workspace.selectActor("hero");return workspace;
    }
    private static void show(Picture picture,StageplayDocument scene,String name,StageplayWorkspace.Action action,int... rows) throws Exception {
        var workspace=workspace(scene);workspace.invoke(action);
        for(int row:rows) choose(workspace,row);
        assertTrue(workspace.menu().isOpen(),name);assertNull(workspace.menu().error(),name);
        picture.take("survey-"+name,workspace);
    }
    private static void choose(StageplayWorkspace workspace,int row) {
        var input=new InputHandler();for(int i=0;i<row;i++)press(workspace,input,GLFW_KEY_DOWN);press(workspace,input,GLFW_KEY_ENTER);
    }
    private static void press(StageplayWorkspace workspace,InputHandler input,int key) {
        input.handleKeyEvent(key,GLFW_PRESS);input.refreshLogicalSnapshot();workspace.menu().update(input);input.update();
        input.handleKeyEvent(key,GLFW_RELEASE);input.update();
    }
}
