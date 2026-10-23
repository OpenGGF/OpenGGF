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

    private Path screenplay(String name) throws IOException {
        Path source = directory.resolve(name);
        java.nio.file.Files.writeString(source, """
                {"version":6,"name":"Imported take","width":320,"height":224,
                 "cast":[{"id":"lead","name":"Lead","gameId":"s2","character":"tails","puppet":false}],
                 "shots":[{"id":"first","durationFrames":120,"set":{"gameId":"s2","zone":0,"act":0},
                 "camera":[{"frame":0,"x":100,"y":200}],"actors":{"lead":[{"frame":0,"x":150,"y":350}]}}]}
                """);
        return source;
    }

    private static void choose(StageplayWorkspace workspace, int index) {
        var input = new com.openggf.control.InputHandler();
        for (int i = 0; i < index; i++) {
            input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, org.lwjgl.glfw.GLFW.GLFW_PRESS);
            input.refreshLogicalSnapshot(); workspace.menu().update(input);
            input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
            input.refreshLogicalSnapshot(); workspace.menu().update(input);
        }
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        input.refreshLogicalSnapshot(); workspace.menu().update(input);
    }

    @Test void markUnsavedChangesOnlySavedIdentityAndMarkSavedRestoresTheContract() {
        var initial = StageplayDocument.empty("s2",0,0,0,0,320,224);
        var controller = new com.openggf.stageplay.StageplayController(initial);
        controller.edit(d -> d.withName("Edited")); controller.seek(12); controller.play();
        controller.markUnsaved();
        assertTrue(controller.dirty()); assertTrue(controller.canUndo());
        assertEquals(12,controller.frame()); assertTrue(controller.playing());
        controller.undo(); assertEquals(initial,controller.document()); assertTrue(controller.dirty());
        controller.markSaved(); assertFalse(controller.dirty()); assertTrue(controller.canRedo());
        controller.redo(); assertTrue(controller.dirty());
    }

    @Test void screenplayImportRefineExportRecompileKeepsUnsavedStateAndUndo() throws Exception {
        Path input = screenplay("source.screenplay.json");
        byte[] original = java.nio.file.Files.readAllBytes(input);
        var workspace = workspace(); workspace.activate();
        workspace.invoke(IMPORT_SCREENPLAY); workspace.menu().setValue("path", input.toString()); workspace.menu().submit();
        assertNull(workspace.menu().error()); assertNull(workspace.path());
        assertEquals(directory, workspace.assetRoot());
        var imported = workspace.controller().document();
        assertTrue(workspace.controller().dirty(), "An import has no saved canonical counterpart");
        assertFalse(workspace.controller().canUndo(), "Replacing the scene starts a fresh history");
        workspace.selectActor("lead"); workspace.seek(30); workspace.invoke(SET_KEY);
        workspace.menu().setValue("x", "270"); workspace.menu().setValue("pose", "2"); workspace.menu().submit();
        assertNull(workspace.menu().error());
        var refined = workspace.controller().document();
        Path output = directory.resolve("edited.screenplay.json");
        workspace.invoke(EXPORT_SCREENPLAY); workspace.menu().setValue("path", output.toString()); workspace.menu().submit();
        assertNull(workspace.menu().error());
        assertEquals(refined, com.openggf.tools.StageplayScriptCompiler.compile(java.nio.file.Files.readAllBytes(output)));
        assertEquals(refined, workspace.controller().document()); assertNull(workspace.path());
        assertTrue(workspace.controller().dirty()); assertTrue(workspace.controller().canUndo());
        workspace.invoke(UNDO); assertEquals(imported, workspace.controller().document());
        assertTrue(workspace.controller().dirty(), "Undoing refinements still leaves an unsaved import");
        workspace.invoke(REDO); assertEquals(refined, workspace.controller().document());
        byte[] exported = java.nio.file.Files.readAllBytes(output);
        assertThrows(IOException.class, () -> workspace.exportScreenplay(output));
        assertArrayEquals(exported, java.nio.file.Files.readAllBytes(output));
        assertEquals(refined, workspace.controller().document()); assertTrue(workspace.controller().canUndo());
        workspace.invoke(SAVE); assertEquals("Save Stageplay", workspace.menu().title());
        Path canonical = directory.resolve("edited.stageplay");
        workspace.menu().setValue("path", canonical.toString()); workspace.menu().submit();
        assertEquals(refined, StageplayStore.read(canonical)); assertFalse(workspace.controller().dirty());
        assertArrayEquals(original, java.nio.file.Files.readAllBytes(input), "Canonical Save cannot overwrite screenplay input");
        try (var files = java.nio.file.Files.list(directory)) { assertEquals(3, files.count(), "No staging files remain"); }
    }

    @Test void failedOrCancelledImportDoesNotStopPlaybackOrTouchHostLeaseAndHistory() throws Exception {
        var stopped = new java.util.concurrent.atomic.AtomicInteger();
        var finished = new java.util.concurrent.atomic.AtomicInteger();
        var refreshed = new java.util.concurrent.atomic.AtomicInteger();
        var workspace = new StageplayWorkspace(new StageplayWorkspace.Host() {
            public StageplayDocument initialDocument() { return StageplayDocument.empty("s2", 0, 0, 100, 200, 320, 224); }
            public Path initialAssetRoot() { return directory; }
            public void stop() { stopped.incrementAndGet(); }
            public void finishPendingEdits() { finished.incrementAndGet(); }
            public void refreshResources() { refreshed.incrementAndGet(); }
        });
        workspace.activate(); addActor(workspace); workspace.controller().play();
        var before = workspace.controller().document();
        int stopCount = stopped.get(), finishCount = finished.get(), refreshCount = refreshed.get();
        Path malformed = directory.resolve("bad.json"); java.nio.file.Files.writeString(malformed, "{}");
        workspace.invoke(IMPORT_SCREENPLAY); workspace.menu().setValue("path", malformed.toString()); workspace.menu().submit();
        assertNotNull(workspace.menu().error());
        assertEquals(before, workspace.controller().document()); assertTrue(workspace.controller().canUndo());
        assertTrue(workspace.controller().playing()); assertNull(workspace.path()); assertEquals(directory, workspace.assetRoot());
        assertEquals(stopCount, stopped.get()); assertEquals(finishCount, finished.get()); assertEquals(refreshCount, refreshed.get());
        workspace.invoke(IMPORT_SCREENPLAY); workspace.menu().close();
        assertEquals(before, workspace.controller().document()); assertTrue(workspace.controller().playing());
        assertEquals(stopCount, stopped.get()); assertEquals(finishCount, finished.get());
        Path oversized = directory.resolve("too-large.json");
        try (var channel = java.nio.channels.FileChannel.open(oversized, java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(com.openggf.tools.StageplayScriptCompiler.MAX_BYTES);
            channel.write(java.nio.ByteBuffer.wrap(new byte[]{1}));
        }
        assertThrows(IOException.class, () -> workspace.importScreenplay(oversized));
        assertEquals(before, workspace.controller().document()); assertEquals(stopCount, stopped.get());
    }

    @Test void validImportProtectsDirtySceneAndCancelledReplacementPreservesItsUndo() throws Exception {
        var workspace = workspace(); workspace.activate(); addActor(workspace);
        var before = workspace.controller().document(); Path input = screenplay("incoming.json");
        workspace.importScreenplay(input); assertEquals("Unsaved Stageplay scene", workspace.menu().title());
        choose(workspace, 2); // Keep editing.
        assertEquals(before, workspace.controller().document()); assertTrue(workspace.controller().canUndo());
        workspace.importScreenplay(input); choose(workspace, 1); // Discard current scene and import.
        assertNull(workspace.path()); assertTrue(workspace.controller().dirty()); assertFalse(workspace.controller().canUndo());
        assertEquals("Imported take", workspace.controller().document().name());
    }

    @Test void screenplayExportPreservesRelativeAssetsAndDoesNotMoveUndoHistory() throws Exception {
        var workspace = workspace(); workspace.activate();
        var cue = new StageplayDocument.AudioCue("voice", 0, StageplayDocument.AudioKind.VOICE, "s2", 0, "voice.wav", 1);
        workspace.controller().edit(d -> d.withAudio(List.of(cue)));
        assertThrows(IOException.class, () -> workspace.exportScreenplay(directory.resolve("elsewhere/scene.json")));
        assertFalse(java.nio.file.Files.exists(directory.resolve("elsewhere")), "Reject asset relocation before filesystem writes");
        workspace.exportScreenplay(directory.resolve("beside.json"));
        assertEquals("voice.wav", com.openggf.tools.StageplayScriptCompiler.compile(
                java.nio.file.Files.readAllBytes(directory.resolve("beside.json"))).audio().getFirst().asset());
        workspace.controller().edit(d -> d.withAudio(List.of()));
        var before = workspace.controller().document();
        workspace.exportScreenplay(directory.resolve("elsewhere/asset-free.json"));
        assertEquals(directory, workspace.assetRoot()); assertEquals(before, workspace.controller().document());
        workspace.invoke(UNDO); assertEquals("voice.wav", workspace.controller().document().audio().getFirst().asset());
    }

    @Test void screenplayExportDoesNotCommitOrStopAnUnfinishedTake() throws Exception {
        var workspace = workspace(); workspace.activate(); addActor(workspace);
        var before = workspace.controller().document(); workspace.invoke(RECORD);
        workspace.recordStep(true, false, false, false);
        var preview = workspace.previewDocument();
        workspace.invoke(EXPORT_SCREENPLAY); workspace.menu().close();
        assertTrue(workspace.recording()); assertEquals(before, workspace.controller().document());
        Path output = directory.resolve("take.json"); workspace.exportScreenplay(output);
        assertEquals(preview, com.openggf.tools.StageplayScriptCompiler.compile(java.nio.file.Files.readAllBytes(output)));
        assertTrue(workspace.recording()); assertEquals(before, workspace.controller().document());
        workspace.stop(); workspace.invoke(UNDO); assertEquals(before, workspace.controller().document());
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

    @Test void sharedCameraFormsSaveAnimatedNativeWorldZoom() throws Exception {
        var workspace = workspace(); workspace.activate();
        workspace.controller().replace(workspace.controller().document().withSet(
                new StageplayDocument.SetSource("s2", 0, 0, "", "")));
        for (int frame : List.of(0, 60)) {
            workspace.seek(frame); workspace.invoke(CAMERA_KEY);
            var input = new com.openggf.control.InputHandler();
            input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_PRESS);
            input.refreshLogicalSnapshot(); workspace.menu().update(input);
            workspace.menu().setValue("zoom", frame == 0 ? ".75" : "1.5");
            workspace.menu().setValue("interpolation", "SMOOTH"); workspace.menu().submit();
            assertNull(workspace.menu().error());
        }
        var path = directory.resolve("native-world.stageplay"); workspace.saveTo(path);
        assertEquals(1.125, StageplayTimeline.sample(StageplayStore.read(path), 30).camera().zoom());
        workspace.invoke(UNDO);
        assertEquals(1, workspace.controller().document().camera().size(), "One key edit is one undo step");
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
