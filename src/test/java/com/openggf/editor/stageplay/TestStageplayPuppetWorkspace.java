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
import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.StageplayTimeline.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Shared authoring and resolver boundary tests. Native physics itself is tested by the runtime suite. */
class TestStageplayPuppetWorkspace {
    @TempDir Path root;

    private final class PreviewHost implements StageplayWorkspace.Host {
        int reads;
        boolean fail;
        public StageplayDocument initialDocument() { return StageplayDocument.empty("s2",0,0,0,0,320,224).withSet(new SetSource("s2",0,0,"","")); }
        public Path initialAssetRoot() { return root; }
        public Frame resolvePreview(StageplayDocument doc,Path assets,int frame) throws IOException {
            reads++;
            if (fail) throw new IOException("No walkable floor near puppet feet");
            var source=StageplayTimeline.sample(doc,frame);
            var actors=source.actors().stream().map(a -> doc.puppets().stream().noneMatch(p -> p.actorId().equals(a.actorId()))?a:
                    new ActorPose(a.actorId(),a.gameId(),a.character(),400+frame,300,3,frame,2,true,a.visible(),1)).toList();
            var samples=doc.puppets().stream().map(p -> new PuppetSample(p.actorId(),
                    p.commands().stream().filter(c -> c.frame()<=frame && c.kind()!=PuppetCommandKind.JUMP).map(PuppetCommand::id).reduce((a,b)->b).orElse(""),
                    PuppetStatus.MOVING,1,0,1,false,0,p.collisionPath())).toList();
            return new Frame(frame,actors,source.camera(),source.captions(),source.set(),source.transition(),source.baseCamera(),source.dialogue(),samples);
        }
    }

    private StageplayWorkspace workspace(PreviewHost host) {
        var workspace=new StageplayWorkspace(host); workspace.activate(); return workspace;
    }
    private static void add(StageplayWorkspace workspace,boolean physical) {
        workspace.invoke(ADD_ACTOR);
        if (!physical) workspace.menu().setValue("mode","POSER");
        workspace.menu().submit(); assertNull(workspace.menu().error());
    }
    private static void actionForm(StageplayWorkspace workspace,int row) {
        workspace.invoke(SET_KEY); choose(workspace.menu(),row);
    }

    @Test void newActorsDefaultToFloorPhysicsAndLegacyManualRemainsAnExplicitChoice() throws Exception {
        var workspace=workspace(new PreviewHost()); add(workspace,true);
        var physical=workspace.controller().document(); var track=physical.puppets().getFirst();
        assertTrue(track.snapToFloor()); assertEquals(0,track.collisionPath()); assertTrue(track.commands().isEmpty());
        assertTrue(workspace.selectedActorIsPuppet());
        add(workspace,false); assertFalse(workspace.selectedActorIsPuppet());
        assertEquals(1,workspace.controller().document().puppets().size());
        workspace.saveTo(root.resolve("cast.stageplay"));
        assertEquals(workspace.controller().document(),StageplayStore.read(root.resolve("cast.stageplay")));
    }

    @Test void modeConversionIsExplicitUndoableAndPreservesOtherTracks() {
        var workspace=workspace(new PreviewHost()); add(workspace,false);
        workspace.seek(20); workspace.invoke(SET_KEY);
        workspace.menu().setValue("x","800"); workspace.menu().setValue("pose","5"); workspace.menu().submit();
        var manual=workspace.controller().document();
        workspace.invoke(EDIT_ACTOR); workspace.menu().setValue("mode","PUPPET"); workspace.menu().submit();
        assertEquals(manual,workspace.controller().document()); assertEquals("Change actor performance?",workspace.menu().title());
        choose(workspace.menu(),0); var physical=workspace.controller().document();
        assertTrue(workspace.selectedActorIsPuppet());
        var keys=physical.actors().getFirst().keys();
        assertEquals(keys.getFirst().x(),keys.getLast().x()); assertEquals(-1,keys.getLast().pose());
        workspace.invoke(UNDO); assertEquals(manual,workspace.controller().document());
        workspace.invoke(REDO); assertEquals(physical,workspace.controller().document());
        workspace.invoke(EDIT_ACTOR); workspace.menu().setValue("mode","POSER"); workspace.menu().submit(); choose(workspace.menu(),0);
        assertTrue(workspace.controller().document().puppets().isEmpty());
        workspace.invoke(UNDO); assertEquals(physical,workspace.controller().document());
    }

    @Test void moveJumpStopFormsEditAndDeleteActualInstructionsWithoutPositionKeys() {
        var workspace=workspace(new PreviewHost()); add(workspace,true);
        workspace.seek(10); actionForm(workspace,0);
        assertEquals("Move to destination",workspace.menu().title());
        assertEquals("506.0",workspace.menu().values().get("x"),"goal defaults from resolved x, not authored spawn");
        workspace.menu().setValue("x","900"); workspace.menu().setValue("y","300"); workspace.menu().setValue("urgency","0.5"); workspace.menu().submit();
        actionForm(workspace,1); workspace.menu().setValue("hold","8"); workspace.menu().submit();
        workspace.seek(40); actionForm(workspace,2); workspace.menu().submit();
        var before=workspace.controller().document(); var commands=before.puppets().getFirst().commands();
        assertEquals(List.of(PuppetCommandKind.MOVE_TO,PuppetCommandKind.JUMP,PuppetCommandKind.STOP),commands.stream().map(PuppetCommand::kind).toList());
        assertEquals(1,before.actors().getFirst().keys().size());
        workspace.invoke(PUPPET_ACTIONS); choose(workspace.menu(),5); choose(workspace.menu(),0);
        workspace.menu().setValue("urgency","0"); workspace.menu().submit();
        assertNotNull(workspace.menu().error()); assertEquals(before,workspace.controller().document());
        workspace.menu().setValue("urgency","0.75"); workspace.menu().submit();
        var edited=workspace.controller().document().puppets().getFirst().commands();
        assertEquals(.75,edited.stream().filter(c -> c.id().equals(commands.getFirst().id())).findFirst().orElseThrow().urgency());
        assertTrue(edited.contains(commands.get(1)),"Editing a movement action preserves the simultaneous jump");
        workspace.invoke(DELETE_KEY); choose(workspace.menu(),0);
        assertEquals(2,workspace.controller().document().puppets().getFirst().commands().size());
        workspace.invoke(UNDO); assertEquals(3,workspace.controller().document().puppets().getFirst().commands().size());
    }

    @Test void recordingStoresIntentsFromResolvedPositionsAndCommitsOneUndo() {
        var workspace=workspace(new PreviewHost()); add(workspace,true);
        var before=workspace.controller().document(); workspace.invoke(RECORD);
        assertTrue(workspace.recordingPuppet());
        workspace.recordStep(true,false,false,true);
        workspace.recordStep(true,false,false,true);
        workspace.recordStep(false,false,false,false);
        assertEquals(before,workspace.controller().document());
        var draft=workspace.previewDocument(); var commands=draft.puppets().getFirst().commands();
        assertEquals(before.actors(),draft.actors(),"native take does not bake position or pose keys");
        assertEquals(8592,commands.getFirst().x(),"movement goal is relative to the actual resolved x=400");
        assertEquals(List.of(PuppetCommandKind.MOVE_TO,PuppetCommandKind.JUMP,PuppetCommandKind.STOP),commands.stream().map(PuppetCommand::kind).toList());
        workspace.stop(); assertEquals(draft,workspace.controller().document());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
        workspace.invoke(REDO); assertEquals(draft,workspace.controller().document());
    }

    @Test void recordingJumpRespectsFutureJumpAndShotBoundaries() {
        var workspace=workspace(new PreviewHost()); add(workspace,true);
        workspace.seek(5); actionForm(workspace,1); workspace.menu().setValue("hold","2"); workspace.menu().submit();
        workspace.seek(0); workspace.invoke(RECORD); workspace.recordStep(false,false,false,true); workspace.stop();
        var jumps=workspace.controller().document().puppets().getFirst().commands().stream().filter(c -> c.kind()==PuppetCommandKind.JUMP).toList();
        assertEquals(4,jumps.getFirst().holdFrames()); assertEquals(5,jumps.getLast().frame());
        workspace.seek(10); workspace.invoke(SCENE_CHANGES); choose(workspace.menu(),0); workspace.menu().submit();
        workspace.seek(8); workspace.invoke(RECORD); workspace.recordStep(true,false,false,true); workspace.recordStep(true,false,false,false);
        workspace.recordStep(true,false,false,false); workspace.stop();
        var commands=workspace.controller().document().puppets().getFirst().commands();
        assertEquals(2,commands.stream().filter(c -> c.frame()==8 && c.kind()==PuppetCommandKind.JUMP).findFirst().orElseThrow().holdFrames());
        assertTrue(commands.stream().anyMatch(c -> c.frame()==10 && c.kind()==PuppetCommandKind.MOVE_TO),"new shots must restart held movement intent");
    }

    @Test void recordingRepeatedJumpPressesLeavesANativeReleasedTick() {
        var workspace=workspace(new PreviewHost()); add(workspace,true);
        workspace.invoke(RECORD);
        workspace.recordStep(false,false,false,true);
        workspace.recordStep(false,false,false,false);
        workspace.recordStep(false,false,false,true); workspace.stop();
        var jumps=workspace.controller().document().puppets().getFirst().commands().stream()
                .filter(c -> c.kind()==PuppetCommandKind.JUMP).toList();
        assertEquals(1,jumps.getFirst().holdFrames()); assertEquals(2,jumps.getLast().frame());
        assertTrue(jumps.getLast().frame()>jumps.getFirst().frame()+jumps.getFirst().holdFrames());
    }

    @Test void resolvedReadoutIsCachedAtAFrameAndCameraDraftKeepsPhysicsMetadata() {
        var host=new PreviewHost(); var workspace=workspace(host); add(workspace,true); workspace.seek(50);
        assertEquals(450,workspace.previewFrame().actors().getFirst().x());
        assertTrue(workspace.selectedActorStatus().contains("MOVING x450.0 y300.0"));
        assertEquals(1,host.reads); workspace.previewFrame(); assertEquals(1,host.reads);
        workspace.invoke(CAMERA_MODE); workspace.moveCamera(8,0,1);
        assertEquals(8,workspace.previewFrame().baseCamera().x());
        assertEquals(PuppetStatus.MOVING,workspace.previewFrame().puppets().getFirst().status());
        assertEquals(1,host.reads);
        workspace.seek(10); assertEquals(410,workspace.previewFrame().actors().getFirst().x()); assertEquals(2,host.reads);
        workspace.invoke(EDIT_ACTOR); workspace.menu().setValue("name","Changed"); workspace.menu().submit();
        workspace.previewFrame(); assertEquals(3,host.reads);
        workspace.invoke(UNDO); workspace.previewFrame(); assertEquals(4,host.reads);
    }

    @Test void failedPreviewCannotEscapeDrawingOrPublishAnInvalidRecordedStep() {
        var host=new PreviewHost(); var workspace=workspace(host); add(workspace,true);
        workspace.invoke(RECORD); workspace.recordStep(true,false,false,false);
        var valid=workspace.previewDocument(); host.fail=true;
        assertDoesNotThrow(workspace::previewFrame);
        assertTrue(workspace.selectedActorStatus().contains("No walkable floor"));
        int reads=host.reads; assertDoesNotThrow(workspace::previewFrame); assertEquals(reads,host.reads);
        assertThrows(IllegalStateException.class,() -> workspace.recordStep(true,false,false,false));
        assertEquals(valid,workspace.previewDocument()); assertEquals(1,workspace.controller().frame());
        workspace.stop(); assertEquals(valid,workspace.controller().document());
        workspace.invoke(RECORD); assertFalse(workspace.recording());
    }

    @Test void unresolvedHostsNeverPretendToRecordPhysics() {
        var workspace=new StageplayWorkspace(() -> StageplayDocument.empty("s2",0,0,0,0,320,224)); workspace.activate(); add(workspace,true);
        var before=workspace.controller().document(); workspace.invoke(RECORD);
        assertFalse(workspace.recording()); assertEquals(before,workspace.controller().document());
        assertTrue(workspace.menu().error().contains("Puppet preview"));
    }

    @Test void shotPlacementVisibilityAndShotRemovalRemainValidAndUndoable() {
        var workspace=workspace(new PreviewHost()); add(workspace,true);
        workspace.seek(40); workspace.invoke(SCENE_CHANGES); choose(workspace.menu(),0); workspace.menu().submit();
        actionForm(workspace,3); workspace.menu().setValue("x","800"); workspace.menu().submit();
        workspace.seek(50); actionForm(workspace,4); workspace.menu().setValue("visible","false"); workspace.menu().submit();
        var before=workspace.controller().document(); var keys=before.actors().getFirst().keys();
        assertEquals(800,keys.getLast().x()); assertFalse(keys.getLast().visible());
        workspace.invoke(SCENE_CHANGES); choose(workspace.menu(),1); choose(workspace.menu(),1);
        var after=workspace.controller().document(); assertTrue(after.sceneChanges().isEmpty());
        assertEquals(after.actors().getFirst().keys().getFirst().x(),after.actors().getFirst().keys().getLast().x());
        assertFalse(after.actors().getFirst().keys().getLast().visible());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
        workspace.invoke(DELETE_ACTOR); assertTrue(workspace.controller().document().puppets().isEmpty());
        workspace.invoke(UNDO); assertEquals(before,workspace.controller().document());
    }

    private static void choose(EditorProjectMenu menu,int row) {
        var input=new InputHandler();
        for (int i=0;i<row;i++) press(menu,input,GLFW_KEY_DOWN);
        press(menu,input,GLFW_KEY_ENTER);
    }
    private static void press(EditorProjectMenu menu,InputHandler input,int key) {
        input.handleKeyEvent(key,GLFW_PRESS); input.refreshLogicalSnapshot(); menu.update(input); input.update();
        input.handleKeyEvent(key,GLFW_RELEASE); input.update();
    }
}
