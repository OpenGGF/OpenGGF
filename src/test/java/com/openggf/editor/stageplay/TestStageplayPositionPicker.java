package com.openggf.editor.stageplay;

import com.openggf.control.InputHandler;
import com.openggf.stageplay.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestStageplayPositionPicker {
    @Test void floorMarkerUsesYellowLineAndSeparatedVerticalDashes() {
        var view=new StageplayPositionPicker.View(0,0,1);
        var picker=new StageplayPositionPicker(160,100,320,224,view,true,
                (x,y)->List.of(new StageplayPositionPicker.Floor(x,151,170)));
        record Stroke(float x1,float y1,float x2,float y2,int rgb,float width) { }
        var lines=new java.util.ArrayList<Stroke>();
        picker.paint(new StageplayPositionPicker.Painter(){
            public void line(float x1,float y1,float x2,float y2,int rgb,float width){lines.add(new Stroke(x1,y1,x2,y2,rgb,width));}
            public void rect(float x,float y,float width,float height,int rgb) { }
            public void text(String text,float x,float y,int rgb) { }
            public float textWidth(String text){return text.length()*6;}
        },0,0,320,224,view);
        assertTrue(lines.stream().anyMatch(s->s.y1()==170&&s.y2()==170&&s.x1()<160&&s.x2()>160
                &&s.rgb()==0xFFC845&&s.width()>=2),"The queried floor has a visible yellow line");
        var dashes=lines.stream().filter(s->s.x1()==160&&s.x2()==160&&s.y1()>110&&s.y2()<170).toList();
        assertTrue(dashes.size()>3,"Connect the cursor to the floor with multiple short dashes");
        for(int i=0;i<dashes.size();i++){
            var dash=dashes.get(i);assertEquals(0xFFC845,dash.rgb());assertEquals(1,dash.width());
            assertTrue(dash.y2()-dash.y1()<=4);
            if(i>0)assertTrue(dash.y1()>dashes.get(i-1).y2(),"Dashes retain visible gaps");
        }
    }

    private static StageplayWorkspace workspace(){
        var doc=StageplayDocument.empty("s2",0,0,0,100,320,224).withActors(List.of(new Actor("hero","Sonic","s2","sonic",
                List.of(new ActorKey(0,100,200,0,-1,Facing.RIGHT,true,1,Interpolation.HOLD)))))
                .withPuppets(List.of(new PuppetTrack("hero",true,0,List.of())));
        var w=new StageplayWorkspace(new StageplayWorkspace.Host(){
            public StageplayDocument initialDocument(){return doc;}
            public StageplayGround.Report ground(StageplayDocument d,Path root,int frame,StageplayGround.Batch batch){
                var q=batch.queries().getFirst();
                return new StageplayGround.Report(new StageplayGround.SceneFrame(d.set(),frame),List.of(new StageplayGround.Answer("picker",StageplayGround.Outcome.FOUND,
                        List.of(new StageplayGround.Placement(q.x(),220,239,0,new StageplayGround.PlayerTerrainProposal(StageplayGround.Foot.LEFT,0,9,19))),List.of(),false)),
                        new StageplayGround.Work(10,0,1,StageplayGround.StopReason.NONE));
            }
        });w.activate();w.selectActor("hero");w.invoke(StageplayWorkspace.Action.ROUTE_HELPER);return w;
    }
    @Test void positionRowStartsPickerAndConfirmWritesOnlyTheFormWithCurrentFloor(){
        var w=workspace();var before=w.controller().document();var input=new InputHandler();
        press(w,input,GLFW_KEY_DOWN);press(w,input,GLFW_KEY_ENTER);
        assertTrue(w.pickingPosition());assertFalse(w.menu().isOpen());
        w.pickPoint(160,205);w.confirmPositionPick();
        assertFalse(w.pickingPosition());assertTrue(w.menu().isOpen());assertEquals("160",w.menu().values().get("x"));assertEquals("220",w.menu().values().get("y"));
        assertEquals(before,w.controller().document());assertFalse(w.controller().canUndo());
    }
    @Test void cancelPreservesNumbersAndSceneAndFineMovementIsOnePixel(){
        var w=workspace();var values=w.menu().values();var before=w.controller().document();w.menu().pick("x");
        w.tick(new InputHandler());double x=w.positionPicker().x();
        var input=new InputHandler();input.handleKeyEvent(GLFW_KEY_RIGHT,GLFW_PRESS);input.handleKeyEvent(GLFW_KEY_LEFT_SHIFT,GLFW_PRESS);input.refreshLogicalSnapshot();w.tick(input);
        assertEquals(x+1,w.positionPicker().x());w.cancelPositionPick();assertEquals(values,w.menu().values());assertEquals(before,w.controller().document());
    }
    @Test void floorColumnWritesOnlyXAndCameraPickerWritesBothCoordinates() {
        var w=workspace();w.invoke(StageplayWorkspace.Action.PLACE_ON_FLOOR);w.menu().pick("x");
        w.pickPoint(400,205);w.confirmPositionPick();assertEquals("400",w.menu().values().get("x"));assertFalse(w.menu().values().containsKey("y"));
        w.invoke(StageplayWorkspace.Action.CAMERA_KEY);w.menu().runAction(0);assertTrue(w.menu().canPick("x"));
        w.menu().pick("x");w.pickPoint(700,120);w.confirmPositionPick();assertEquals("700",w.menu().values().get("x"));assertEquals("120",w.menu().values().get("y"));
    }
    @Test void heldMovementAcceleratesPansAndChangedPlayheadCancelsPendingPick() {
        var w=workspace();var initial=w.menu().values();w.menu().pick("x");w.tick(new InputHandler());
        double x=w.positionPicker().x(),camera=w.positionPicker().camera().x();var input=new InputHandler();input.handleKeyEvent(GLFW_KEY_RIGHT,GLFW_PRESS);
        for(int tick=0;tick<100;tick++){input.refreshLogicalSnapshot();w.tick(input);input.update();}
        assertTrue(w.positionPicker().x()>x+100);assertTrue(w.positionPicker().camera().x()>camera);
        w.seek(1);w.tick(new InputHandler());assertFalse(w.pickingPosition());assertEquals(initial,w.menu().values());
    }
    private static void press(StageplayWorkspace w,InputHandler input,int key){
        input.handleKeyEvent(key,GLFW_PRESS);input.refreshLogicalSnapshot();w.menu().update(input);input.update();input.handleKeyEvent(key,GLFW_RELEASE);input.update();
    }
}
