package com.openggf.editor.stageplay;

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

class TestStageplayWorkspace {
    @TempDir Path directory;

    private StageplayWorkspace workspace() {
        return new StageplayWorkspace(new StageplayWorkspace.Host() {
            public StageplayDocument initialDocument() { return StageplayDocument.empty("s2",0,0,100,200,320,224); }
            public Path initialAssetRoot() { return directory; }
        });
    }

    private void addActor(StageplayWorkspace workspace) {
        workspace.invoke(ADD_ACTOR);
        workspace.menu().setValue("name","Tails");
        workspace.menu().setValue("character","tails");
        workspace.menu().setValue("mode","POSER");
        workspace.menu().submit();
    }

    @Test void bothHostsRoundTripAllSharedDataAndKeepUndoAcrossWorkspaceSwitch() throws Exception {
        var engine=workspace(); engine.activate(); addActor(engine);
        engine.seek(90); engine.invoke(SET_KEY);
        engine.menu().setValue("x","700"); engine.menu().setValue("pose","2"); engine.menu().submit();
        engine.invoke(SCENE); engine.menu().setValue("name","Reunion"); engine.menu().submit();
        engine.saveTo(directory.resolve("reunion.stageplay"));
        var expected=engine.controller().document();
        engine.deactivate(); engine.activate();
        assertEquals(expected,engine.controller().document()); assertTrue(engine.controller().canUndo());
        var desktop=workspace(); desktop.open(directory.resolve("reunion.stageplay"));
        assertEquals(expected,desktop.controller().document()); assertFalse(desktop.controller().dirty());
        desktop.selectActor(expected.actors().getFirst().id()); desktop.seek(90); desktop.invoke(SET_KEY);
        desktop.menu().setValue("x","720"); desktop.menu().submit(); desktop.invoke(UNDO);
        assertEquals(expected,desktop.controller().document()); assertFalse(desktop.controller().dirty());
    }

    @Test void invalidFormAndInvalidOpenPreserveDocumentAndHistory() throws Exception {
        var workspace=workspace(); workspace.activate(); addActor(workspace);
        var before=workspace.controller().document();
        workspace.invoke(SET_KEY); workspace.menu().setValue("scale","NaN"); workspace.menu().submit();
        assertEquals(before,workspace.controller().document()); assertNotNull(workspace.menu().error());
        java.nio.file.Files.writeString(directory.resolve("bad.stageplay"),"{\"version\":99}");
        assertThrows(IOException.class,()->workspace.open(directory.resolve("bad.stageplay")));
        assertEquals(before,workspace.controller().document()); assertTrue(workspace.controller().canUndo());
    }

    @Test void directedTakeIsOneUndoStepAndNeverClaimsSimulation() {
        var workspace=workspace(); workspace.activate(); addActor(workspace);
        var before=workspace.controller().document();
        workspace.invoke(RECORD);
        workspace.recordStep(true,false,false,false); workspace.recordStep(true,false,false,false);
        assertEquals(before,workspace.controller().document(),"preview capture must not mutate undo history per frame");
        workspace.stop();
        var after=workspace.controller().document();
        assertEquals(264,StageplayTimeline.sample(after,1).actors().getFirst().x());
        assertTrue(workspace.status().contains("visual take"));
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
        workspace.invoke(REDO); assertEquals(after,workspace.controller().document());
    }

    @Test void recordingAtTheSceneKeyLimitKeepsTheLastValidTakeRecoverable() {
        var workspace=workspace(); workspace.activate();
        var camera=java.util.stream.IntStream.range(0,33_333).mapToObj(frame ->
                new StageplayDocument.CameraKey(frame,0,0,1,StageplayDocument.Interpolation.HOLD)).toList();
        var denseKeys=java.util.stream.IntStream.range(0,33_333).mapToObj(frame ->
                new StageplayDocument.ActorKey(frame,100,200,0,-1,false,true,1,StageplayDocument.Interpolation.HOLD)).toList();
        var before=workspace.controller().document().withDurationFrames(36_000).withCamera(camera).withActors(List.of(
                new StageplayDocument.Actor("take","Take","s2","sonic",List.of(denseKeys.getFirst())),
                new StageplayDocument.Actor("cast1","Cast 1","s2","sonic",denseKeys),
                new StageplayDocument.Actor("cast2","Cast 2","s2","sonic",denseKeys)));
        workspace.controller().replace(before); workspace.selectActor("take"); workspace.invoke(RECORD);
        workspace.recordStep(true,false,false,false); // Replacing frame zero stays at 100,000 keys.
        var valid=workspace.previewDocument();
        assertEquals(102,valid.actors().getFirst().keys().getFirst().x());
        assertThrows(IllegalArgumentException.class,()->workspace.recordStep(true,false,false,false));
        assertEquals(1,workspace.controller().frame(),"rejected recording must not advance the playhead");
        assertEquals(valid,workspace.previewDocument(),"a rejected frame must remain safe to draw");
        assertEquals(before,workspace.controller().document()); assertTrue(workspace.recording());
        workspace.stop();
        assertFalse(workspace.recording()); assertEquals(valid,workspace.controller().document());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
        workspace.invoke(REDO); assertEquals(valid,workspace.controller().document());
    }

    @Test void failedStopDuringTickReportsTheErrorAndRetainsTheTakeForRetry() {
        var failStop=new java.util.concurrent.atomic.AtomicBoolean();
        var workspace=new StageplayWorkspace(new StageplayWorkspace.Host() {
            public StageplayDocument initialDocument() { return StageplayDocument.empty("s2",0,0,0,0,320,224); }
            public void stop() { if(failStop.get()) throw new IllegalStateException("Audio stop failed"); }
        });
        workspace.activate(); addActor(workspace);
        var before=workspace.controller().document();
        workspace.invoke(RECORD); workspace.recordStep(true,false,false,false);
        var valid=workspace.previewDocument();
        var input=org.mockito.Mockito.mock(com.openggf.control.InputHandler.class);
        org.mockito.Mockito.when(input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE)).thenReturn(true);
        failStop.set(true);
        assertTrue(assertDoesNotThrow(()->workspace.tick(input)));
        assertEquals("Audio stop failed",workspace.menu().error());
        assertTrue(workspace.recording()); assertEquals(valid,workspace.previewDocument());
        assertEquals(before,workspace.controller().document());
        failStop.set(false); workspace.stop();
        assertFalse(workspace.recording()); assertEquals(valid,workspace.controller().document());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
    }

    @Test void transportDispatchesOnlyForwardFramesAndSeekNeverPlaysCues() {
        var frames=new java.util.ArrayList<Integer>();
        var workspace=new StageplayWorkspace(new StageplayWorkspace.Host() {
            public StageplayDocument initialDocument() { return StageplayDocument.empty("s2",0,0,0,0,320,224); }
            public void play(StageplayDocument document,Path root,int frame) { frames.add(frame); }
            public void present(StageplayDocument document,int frame) { frames.add(frame); }
        });
        workspace.activate(); workspace.seek(20); assertTrue(frames.isEmpty());
        workspace.invoke(PLAY); assertEquals(List.of(20),frames);
        workspace.seek(10); assertFalse(workspace.controller().playing()); assertEquals(List.of(20),frames);
    }

    @Test void saveCreatesChosenDirectoryAndFailurePreservesPathAndDirtyState() throws Exception {
        var workspace=workspace(); workspace.activate(); addActor(workspace);
        Path target=directory.resolve("new/scene.stageplay"); workspace.saveTo(target);
        assertEquals(workspace.controller().document(),StageplayStore.read(target));
        workspace.invoke(SCENE); workspace.menu().setValue("name","Changed"); workspace.menu().submit();
        assertThrows(IOException.class,()->workspace.saveTo(target.resolve("child.stageplay")));
        assertEquals(target,workspace.path()); assertTrue(workspace.controller().dirty());
        assertNotEquals(workspace.controller().document(),StageplayStore.read(target));
    }

    @Test void saveAsCannotReinterpretAssetsRetainedOnlyByUndoOrRedo() throws Exception {
        var workspace=workspace(); workspace.activate();
        var cue=new StageplayDocument.AudioCue("voice",0,StageplayDocument.AudioKind.VOICE,"s2",0,"voice.wav",1);
        workspace.controller().edit(d -> d.withAudio(List.of(cue)));
        workspace.controller().edit(d -> d.withAudio(List.of()));
        assertThrows(IOException.class,()->workspace.saveTo(directory.resolve("elsewhere/scene.stageplay")));
        workspace.controller().undo(); workspace.controller().undo();
        assertThrows(IOException.class,()->workspace.saveTo(directory.resolve("elsewhere/scene.stageplay")),"redo references retain the original root too");
        workspace.saveTo(directory.resolve("scene.stageplay"));
        workspace.controller().redo();
        assertEquals("voice.wav",workspace.controller().document().audio().getFirst().asset());
    }

    @Test void firstActivationFailureReportsAnErrorWithoutReplacingTheDocument() {
        var workspace=new StageplayWorkspace(() -> { throw new IllegalStateException("Disk full"); });
        var before=workspace.controller().document(); workspace.activate();
        assertFalse(workspace.active()); assertTrue(workspace.menu().isOpen());
        assertEquals("Disk full",workspace.menu().error()); assertEquals(before,workspace.controller().document());
    }
}
