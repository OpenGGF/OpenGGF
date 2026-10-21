package com.openggf.editor.stageplay;

import com.openggf.control.InputHandler;
import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.StageplayTimeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.openggf.editor.stageplay.StageplayWorkspace.Action.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestStageplayDirectingWorkspace {
    @TempDir Path directory;

    private StageplayWorkspace workspace() {
        var workspace = new StageplayWorkspace(new StageplayWorkspace.Host() {
            public StageplayDocument initialDocument() { return StageplayDocument.empty("s2",0,0,100,200,320,224); }
            public Path initialAssetRoot() { return directory; }
        });
        workspace.activate(); return workspace;
    }

    @Test void cameraFramingIsTransientUntilCapturedAndNeverBakesShake() {
        var workspace = workspace();
        var before = workspace.controller().document().withCameraEffects(List.of(
                new StageplayDocument.CameraEffect("quake",0,60,20,30,8,123)));
        workspace.controller().replace(before);
        workspace.invoke(CAMERA_MODE);
        workspace.moveCamera(10,-4,2);
        assertEquals(before,workspace.controller().document());
        var preview = workspace.previewFrame();
        assertEquals(190,preview.baseCamera().x()); assertEquals(252,preview.baseCamera().y());
        assertEquals(2,preview.baseCamera().zoom());
        workspace.invoke(SET_CAMERA_KEY);
        var key = workspace.controller().document().camera().getFirst();
        assertEquals(190,key.x()); assertEquals(252,key.y()); assertEquals(2,key.zoom());
        assertEquals(StageplayDocument.Interpolation.LINEAR,key.interpolation());
        assertEquals(before.cameraEffects(),workspace.controller().document().cameraEffects());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
        workspace.moveCamera(10,0,1); workspace.seek(20);
        assertEquals(StageplayTimeline.sample(before,20).baseCamera(),workspace.directorCamera());
    }

    @Test void cameraRecordingNeedsNoActorAndCommitsOneUndoStep() {
        var workspace = workspace(); var before = workspace.controller().document();
        workspace.invoke(RECORD_CAMERA);
        workspace.recordCameraStep(1,0,1); workspace.recordCameraStep(1,1,1.02);
        assertEquals(before,workspace.controller().document());
        assertTrue(workspace.recordingCamera()); assertEquals(2,workspace.controller().frame());
        var preview = workspace.previewDocument();
        assertEquals(102,preview.camera().getFirst().x());
        assertEquals(1.02,preview.camera().getLast().zoom());
        workspace.stop(); assertEquals(preview,workspace.controller().document());
        assertFalse(workspace.recording());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
        workspace.invoke(REDO); assertEquals(preview,workspace.controller().document());
    }

    @Test void cameraKeyboardMovesWithoutSeekingAndModifiersDoNotMoveIt() {
        var workspace = workspace(); workspace.invoke(CAMERA_MODE);
        var input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_RIGHT,GLFW_PRESS);
        input.refreshLogicalSnapshot();
        workspace.tick(input);
        assertEquals(0,workspace.controller().frame()); assertEquals(102,workspace.directorCamera().x());
        input.handleKeyEvent(GLFW_KEY_RIGHT,GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_LEFT_CONTROL,GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_S,GLFW_PRESS);
        input.refreshLogicalSnapshot();
        workspace.tick(input); assertEquals(200,workspace.directorCamera().y());
    }

    @Test void fullKeyBudgetAllowsFramingButRejectsCaptureAndKeepsLastValidCameraTake() {
        var workspace = workspace();
        var keys = java.util.stream.IntStream.range(0,33_333).mapToObj(frame ->
                new StageplayDocument.ActorKey(frame,100,200,0,-1,false,true,1,StageplayDocument.Interpolation.HOLD)).toList();
        var before = workspace.controller().document().withDurationFrames(36_000).withActors(List.of(
                new StageplayDocument.Actor("a","A","s2","sonic",keys),
                new StageplayDocument.Actor("b","B","s2","sonic",keys),
                new StageplayDocument.Actor("c","C","s2","sonic",keys)));
        workspace.controller().replace(before); workspace.seek(1); workspace.invoke(CAMERA_MODE);
        workspace.moveCamera(2,0,1); assertEquals(102,workspace.previewFrame().baseCamera().x());
        workspace.invoke(SET_CAMERA_KEY); assertEquals(before,workspace.controller().document());
        assertNotNull(workspace.menu().error()); assertDoesNotThrow(workspace::previewFrame);
        workspace.seek(0); workspace.invoke(RECORD_CAMERA); workspace.recordCameraStep(1,0,1);
        var lastValid = workspace.previewDocument();
        assertThrows(IllegalArgumentException.class,() -> workspace.recordCameraStep(1,0,1));
        assertEquals(lastValid,workspace.previewDocument()); assertDoesNotThrow(workspace::previewFrame);
        workspace.stop(); assertEquals(lastValid,workspace.controller().document());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
    }

    @Test void effectFormsSupportEveryPropertyAndDeleteWithUndo() {
        var workspace = workspace(); workspace.seek(10);
        workspace.invoke(CAMERA_EFFECTS); choose(workspace.menu(),0);
        var menu = workspace.menu();
        menu.setValue("start","12"); menu.setValue("end","100"); menu.setValue("x","6"); menu.setValue("y","9");
        menu.setValue("frequency","12"); menu.setValue("seed","42"); menu.submit();
        var effect = workspace.controller().document().cameraEffects().getFirst();
        assertEquals(new StageplayDocument.CameraEffect("shake1",12,100,6,9,12,42),effect);
        workspace.invoke(CAMERA_EFFECTS); choose(menu,1); choose(menu,0);
        menu.setValue("frequency","31"); menu.submit(); assertNotNull(menu.error());
        assertEquals(effect,workspace.controller().document().cameraEffects().getFirst());
        menu.setValue("frequency","10"); menu.submit();
        assertEquals(10,workspace.controller().document().cameraEffects().getFirst().frequency());
        workspace.invoke(CAMERA_EFFECTS); choose(menu,1); choose(menu,1);
        assertTrue(workspace.controller().document().cameraEffects().isEmpty());
        workspace.invoke(UNDO); assertEquals(10,workspace.controller().document().cameraEffects().getFirst().frequency());
    }

    @Test void sceneChangeInsertionMoveAndRemovalKeepBoundaryEditsAtomic() {
        var workspace = workspace(); workspace.invoke(ADD_ACTOR); workspace.menu().submit();
        var before = workspace.controller().document(); workspace.seek(120);
        workspace.invoke(SCENE_CHANGES); choose(workspace.menu(),0);
        var menu = workspace.menu(); menu.setValue("game","s3k"); menu.setValue("zone","1"); menu.setValue("act","1");
        menu.setValue("transition","FADE_WHITE"); menu.setValue("duration","30"); menu.submit();
        var changed = workspace.controller().document();
        assertEquals(120,changed.camera().getLast().frame());
        assertEquals(120,changed.actors().getFirst().keys().getLast().frame());
        assertEquals("s3k",StageplayTimeline.sample(changed,120).set().gameId());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
        workspace.invoke(REDO); assertEquals(changed,workspace.controller().document());
        workspace.invoke(SCENE_CHANGES); choose(menu,1); choose(menu,0);
        menu.setValue("frame","240"); menu.setValue("transition","WIPE_LEFT"); menu.setValue("duration","40"); menu.submit();
        var moved = workspace.controller().document();
        assertEquals(List.of(0,120,240),moved.camera().stream().map(StageplayDocument.CameraKey::frame).toList());
        assertEquals(240,moved.sceneChanges().getFirst().frame());
        workspace.invoke(ADD_ACTOR); menu.submit();
        assertEquals(List.of(0,240),workspace.controller().document().actors().getLast().keys().stream().map(StageplayDocument.ActorKey::frame).toList());
        workspace.seek(240); workspace.invoke(DELETE_KEY);
        assertTrue(menu.error().contains("scene change"));
        workspace.invoke(SCENE_CHANGES); choose(menu,1); choose(menu,1);
        assertTrue(workspace.controller().document().sceneChanges().isEmpty());
        assertEquals(moved.camera(),workspace.controller().document().camera());
    }

    @Test void newTracksSurviveOldFormsRecordingUndoAndBothHostRoundTrip() throws Exception {
        var workspace = workspace(); workspace.invoke(CAMERA_EFFECTS); choose(workspace.menu(),0); workspace.menu().submit();
        workspace.seek(100); workspace.invoke(SCENE_CHANGES); choose(workspace.menu(),0); workspace.menu().submit();
        var effects = workspace.controller().document().cameraEffects(); var changes = workspace.controller().document().sceneChanges();
        workspace.invoke(ADD_ACTOR); workspace.menu().submit();
        workspace.invoke(SCENE); workspace.menu().setValue("name","Two shots"); workspace.menu().submit();
        workspace.invoke(FILM_FORMAT); choose(workspace.menu(),0);
        workspace.invoke(RECORD); workspace.recordStep(true,false,false,false); workspace.stop();
        assertEquals(effects,workspace.controller().document().cameraEffects());
        assertEquals(changes,workspace.controller().document().sceneChanges());
        workspace.saveTo(directory.resolve("two-shots.stageplay"));
        var secondHost = workspace(); secondHost.open(directory.resolve("two-shots.stageplay"));
        assertEquals(workspace.controller().document(),secondHost.controller().document());
        assertEquals(384,secondHost.controller().document().width());
        assertEquals(StageplayDocument.VERSION,StageplayStore.read(directory.resolve("two-shots.stageplay")).version());
    }

    @Test void saveAsRejectsAuthoredSetsInSceneChangesAndTheirUndoHistory() throws Exception {
        var workspace = workspace(); workspace.seek(100);
        workspace.invoke(SCENE_CHANGES); choose(workspace.menu(),0);
        workspace.menu().setValue("project","sets/incoming.ogfproject"); workspace.menu().submit();
        assertThrows(IOException.class,() -> workspace.saveTo(directory.resolve("elsewhere/film.stageplay")));
        workspace.invoke(SCENE_CHANGES); choose(workspace.menu(),1); choose(workspace.menu(),1);
        assertThrows(IOException.class,() -> workspace.saveTo(directory.resolve("elsewhere/film.stageplay")));
        workspace.saveTo(directory.resolve("film.stageplay"));
    }

    private static void choose(EditorProjectMenu menu,int row) {
        var input = new InputHandler();
        for (int i=0;i<row;i++) press(menu,input,GLFW_KEY_DOWN);
        press(menu,input,GLFW_KEY_ENTER);
    }
    private static void press(EditorProjectMenu menu,InputHandler input,int key) {
        input.handleKeyEvent(key,GLFW_PRESS); input.refreshLogicalSnapshot(); menu.update(input); input.update();
        input.handleKeyEvent(key,GLFW_RELEASE); input.update();
    }
}
