package com.openggf.editor.stageplay;

import com.openggf.stageplay.StageplayDocument;
import com.openggf.control.InputHandler;
import static org.lwjgl.glfw.GLFW.*;
import com.openggf.stageplay.StageplayStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.editor.stageplay.StageplayWorkspace.Action.*;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayObjectWorkspace {
    @TempDir Path directory;

    private StageplayWorkspace workspace() {
        return workspace(StageplayDocument.empty("s2",0,0,0,0,320,224));
    }

    private StageplayWorkspace workspace(StageplayDocument document) {
        var workspace = new StageplayWorkspace(new StageplayWorkspace.Host() {
            public StageplayDocument initialDocument() { return document; }
            public Path initialAssetRoot() { return directory; }
        });
        workspace.activate(); return workspace;
    }

    private static void choose(StageplayWorkspace workspace,String label) {
        int row = switch (label) {
            case "Cast ROM object", "Set key at playhead", "Add move", "Add world command" -> 0;
            case "Mode / position" -> 1;
            case "Back to object", "Puppet move" -> 2;
            case "Clear badniks" -> 3;
            case "Clear objects" -> 4;
            default -> throw new IllegalArgumentException(label);
        };
        choose(workspace, row);
    }

    private static void choose(StageplayWorkspace workspace, int row) {
        var input = new InputHandler();
        for (int i = 0; i < row; i++) press(workspace, input, GLFW_KEY_DOWN);
        press(workspace, input, GLFW_KEY_ENTER);
    }

    private static void press(StageplayWorkspace workspace, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS); input.refreshLogicalSnapshot(); workspace.menu().update(input); input.update();
        input.handleKeyEvent(key, GLFW_RELEASE); input.update();
    }

    private static void cast(StageplayWorkspace workspace) {
        workspace.invoke(OBJECTS); choose(workspace,"Cast ROM object");
        workspace.menu().setValue("name","Visual role"); workspace.menu().setValue("object","86");
        workspace.menu().setValue("mode","PUPPET"); workspace.menu().submit(); assertNull(workspace.menu().error());
    }

    @Test void visualKeysChildOverridesCommandsAndSaveUseOneSharedDocument() throws Exception {
        var workspace = workspace(); cast(workspace);
        choose(workspace,"Mode / position"); choose(workspace,"Set key at playhead");
        workspace.menu().setValue("pose","3"); workspace.menu().setValue("parts","root/0:1:7; root/1:0:2");
        workspace.menu().submit(); assertNull(workspace.menu().error());
        choose(workspace,"Back to object"); choose(workspace,"Puppet move"); choose(workspace,"Add move");
        workspace.menu().setValue("x","240"); workspace.menu().setValue("speed","1.25");
        workspace.menu().submit(); assertNull(workspace.menu().error());
        var object = workspace.controller().document().objectActors().getFirst();
        assertEquals(3,object.keys().getFirst().pose());
        assertEquals(new ObjectPartPose("root/0",1,7),object.keys().getFirst().parts().getFirst());
        assertEquals(1.25,object.commands().getFirst().speed());
        workspace.invoke(ADD_ACTOR); workspace.menu().submit(); assertNull(workspace.menu().error());
        assertEquals(object,workspace.controller().document().objectActors().getFirst(),"Player edits preserve object tracks");
        var path = directory.resolve("objects.stageplay"); workspace.saveTo(path);
        assertEquals(workspace.controller().document(),StageplayStore.read(path));
        workspace.invoke(UNDO); assertEquals(object,workspace.controller().document().objectActors().getFirst());
    }

    @Test void clearsAndPortableRemovalAreUndoableAndOrderedAtEarlierPlayheads() {
        var workspace = workspace(); workspace.seek(90); workspace.invoke(OBJECTS); choose(workspace,"Clear objects");
        workspace.seek(20); workspace.invoke(OBJECTS); choose(workspace,"Clear badniks");
        assertEquals(java.util.List.of(20,90),workspace.controller().document().worldCommands().stream().map(WorldCommand::frame).toList());
        choose(workspace,"Add world command"); workspace.menu().setValue("layout","17");
        workspace.menu().setValue("object","34"); workspace.menu().setValue("subtype","2"); workspace.menu().submit();
        assertNull(workspace.menu().error());
        var remove = workspace.controller().document().worldCommands().get(1);
        assertEquals(new ObjectTarget(17,34,2,0,0),remove.target());
        assertEquals(WorldCommandKind.REMOVE_OBJECT,remove.kind());
        workspace.invoke(UNDO); assertEquals(2,workspace.controller().document().worldCommands().size());
        workspace.invoke(REDO); assertEquals(3,workspace.controller().document().worldCommands().size());
    }

    @Test void freeModeCannotRetainAuthoredPoseAndInvalidEditPreservesDocument() {
        var workspace = workspace(); cast(workspace);
        choose(workspace,"Mode / position"); choose(workspace,"Set key at playhead");
        var before = workspace.controller().document();
        workspace.menu().setValue("mode","FREE"); workspace.menu().setValue("pose","4"); workspace.menu().submit();
        assertNotNull(workspace.menu().error()); assertEquals(before,workspace.controller().document());
    }

    @Test void editingPropertiesKeysAndCommandsPreservesNativeCastAllocationOrder() {
        var first = object("first", -1, List.of(key(0, true, ObjectMode.PUPPET, List.of())));
        var second = object("second", -1, List.of(key(0, true, ObjectMode.PUPPET, List.of())));
        var workspace = workspace(StageplayDocument.empty("s2",0,0,0,0,320,224).withObjectActors(List.of(first, second)));
        workspace.invoke(OBJECTS); choose(workspace, 6); choose(workspace, 0);
        workspace.menu().setValue("name", "Renamed first"); workspace.menu().submit();
        assertNull(workspace.menu().error()); assertOrder(workspace, second);
        choose(workspace, "Mode / position"); choose(workspace, "Set key at playhead");
        workspace.menu().setValue("pose", "3"); workspace.menu().submit();
        assertNull(workspace.menu().error()); assertOrder(workspace, second);
        choose(workspace, "Back to object"); choose(workspace, "Puppet move"); choose(workspace, "Add move");
        workspace.menu().setValue("x", "240"); workspace.menu().setValue("speed", "2"); workspace.menu().submit();
        assertNull(workspace.menu().error()); assertOrder(workspace, second);
        workspace.invoke(UNDO); assertOrder(workspace, second);
        workspace.invoke(REDO); assertOrder(workspace, second);
    }

    @Test void manualPortablePlacementAndSourceEditsScopeVisibilityToMatchingShots() {
        var base = StageplayDocument.empty("s2",0,0,0,0,320,224);
        var scene = base.withCamera(List.of(base.camera().getFirst(), new CameraKey(100,0,0,1,Interpolation.HOLD),
                        new CameraKey(200,0,0,1,Interpolation.HOLD)))
                .withSceneChanges(List.of(new SceneChange("foreign",100,new SetSource("s3k",0,1,"","",WorldPolicy.LIVE),Transition.CUT,0),
                        new SceneChange("return",200,base.set(),Transition.CUT,0)));
        var workspace = workspace(scene); workspace.seek(100); workspace.invoke(OBJECTS); choose(workspace,"Cast ROM object");
        workspace.menu().setValue("layout", "7"); workspace.menu().setValue("object", "86");
        workspace.menu().setValue("subtype", "2"); workspace.menu().submit();
        assertNull(workspace.menu().error());
        var placed = workspace.controller().document().objectActors().getFirst();
        assertEquals(List.of(false,true,false), placed.keys().stream().map(ObjectKey::visible).toList());
        assertEquals("s3k", placed.gameId()); assertEquals(7, placed.layoutIndex()); assertEquals(2, placed.subtype());
        choose(workspace, 0); workspace.menu().setValue("game", "s2"); workspace.menu().setValue("act", "0");
        workspace.menu().submit(); assertNull(workspace.menu().error());
        var recast = workspace.controller().document().objectActors().getFirst();
        assertEquals(List.of(true,false,true), recast.keys().stream().map(ObjectKey::visible).toList());
        assertEquals(placed.id(),recast.id()); assertEquals(placed.sourceKind(),recast.sourceKind());
        assertEquals(placed.keys().stream().map(ObjectKey::frame).toList(),recast.keys().stream().map(ObjectKey::frame).toList());
        workspace.invoke(UNDO); assertEquals(placed,workspace.controller().document().objectActors().getFirst());
    }

    @Test void newAndChangedForeignShotsHideEveryPlacedKeyAndKeepAllOtherKeyData() {
        for (int variant = 0; variant < 3; variant++) {
            var keys = new ArrayList<>(List.of(key(0,false,ObjectMode.POSE,List.of(new ObjectPartPose("root/0",0,2))),
                    key(50,true,ObjectMode.POSE,List.of(new ObjectPartPose("root/0",0,3)))));
            if (variant != 0) keys.add(key(100,true,ObjectMode.POSE,List.of(new ObjectPartPose("root/0",0,4))));
            keys.add(key(150,true,ObjectMode.POSE,List.of(new ObjectPartPose("root/0",0,5))));
            var role = object("loaded",7,keys);
            var scene = StageplayDocument.empty("s2",0,0,0,0,320,224).withObjectActors(List.of(role));
            if (variant == 2) scene = scene.withCamera(List.of(scene.camera().getFirst(),new CameraKey(100,0,0,1,Interpolation.HOLD)))
                    .withSceneChanges(List.of(new SceneChange("existing",100,scene.set(),Transition.CUT,0)));
            var workspace = workspace(scene); workspace.seek(100); workspace.invoke(SCENE_CHANGES);
            if (variant == 2) { choose(workspace,1); choose(workspace,0); } else choose(workspace,0);
            workspace.menu().setValue("frame","100"); workspace.menu().setValue("game","s3k");
            workspace.menu().submit(); assertNull(workspace.menu().error());
            var edited = workspace.controller().document().objectActors().getFirst();
            assertTrue(edited.keys().stream().filter(k -> k.frame() >= 100).noneMatch(ObjectKey::visible));
            assertFalse(edited.keys().getFirst().visible(),"Matching-set authored hidden visibility remains hidden");
            assertTrue(edited.keys().stream().filter(k -> k.frame()==50).findFirst().orElseThrow().visible());
            for (var original : keys) {
                var result = edited.keys().stream().filter(k -> k.frame()==original.frame()).findFirst().orElseThrow();
                assertEquals(withVisible(original,original.visible() && original.frame()<100),result);
            }
            assertEquals(role.sourceKind(),edited.sourceKind()); assertEquals(role.commands(),edited.commands());
        }
    }

    @Test void all256LongPartPathsRemainEditableAndSurviveKeyEditsUndoAndSave() throws Exception {
        var parts = new ArrayList<ObjectPartPose>();
        for (int index=0; index<256; index++) parts.add(new ObjectPartPose(longPartPath(index),0,index));
        var role = object("multipart",-1,List.of(key(0,true,ObjectMode.POSE,parts)));
        var workspace = workspace(StageplayDocument.empty("s2",0,0,0,0,320,224).withObjectActors(List.of(role)));
        workspace.invoke(OBJECTS); choose(workspace,6); choose(workspace,"Mode / position"); choose(workspace,"Set key at playhead");
        assertFalse(workspace.menu().values().containsKey("parts"),"Long lists must not open an uneditable bulk field");
        workspace.menu().setValue("x","140"); workspace.menu().submit(); assertNull(workspace.menu().error());
        assertEquals(parts,workspace.controller().document().objectActors().getFirst().keys().getFirst().parts());
        choose(workspace,"Back to object"); choose(workspace,7); choose(workspace,0); choose(workspace,1);
        assertEquals(256,workspace.menu().values().get("partId").length());
        workspace.menu().setValue("partId",longPartPath(999)); workspace.menu().setValue("pose","511"); workspace.menu().submit();
        assertNull(workspace.menu().error());
        var changed = workspace.controller().document().objectActors().getFirst().keys().getFirst().parts();
        assertEquals(new ObjectPartPose(longPartPath(999),0,511),changed.getFirst());
        assertEquals(parts.subList(1,parts.size()),changed.subList(1,changed.size()));
        choose(workspace,256); workspace.menu().setValue("pose","512"); workspace.menu().submit();
        assertNull(workspace.menu().error());
        assertEquals(512,workspace.controller().document().objectActors().getFirst().keys().getFirst().parts().getLast().pose());
        choose(workspace,1); workspace.menu().setValue("delete","true"); workspace.menu().submit();
        assertNull(workspace.menu().error()); assertEquals(255,workspace.controller().document().objectActors().getFirst().keys().getFirst().parts().size());
        choose(workspace,0); workspace.menu().setValue("partId",longPartPath(999));
        workspace.menu().setValue("pose","511"); workspace.menu().submit(); assertNull(workspace.menu().error());
        assertEquals(256,workspace.controller().document().objectActors().getFirst().keys().getFirst().parts().size());
        var finalDocument = workspace.controller().document();
        workspace.invoke(UNDO); workspace.invoke(REDO); assertEquals(finalDocument,workspace.controller().document());
        var path=directory.resolve("multipart.stageplay"); workspace.saveTo(path);
        assertEquals(finalDocument,StageplayStore.read(path));
    }

    @Test void declaredNativeAppearanceExclusionsHaveAnIndividualDetailsPage() {
        var role = new ObjectActor("mask","Mask","s3k",7,0,0x8B,0,-1,List.of(key(0,true,ObjectMode.POSE,List.of())),List.of());
        var scene = StageplayDocument.empty("s3k",7,0,0,0,320,224).withObjectActors(List.of(role));
        var workspace=workspace(scene); workspace.invoke(OBJECTS); choose(workspace,6); choose(workspace,4);
        choose(workspace,0); assertEquals("Declared native appearance exclusions",workspace.menu().title());
        choose(workspace,0); assertEquals("Appearance restriction 1",workspace.menu().title());
        assertEquals(scene,workspace.controller().document(),"Viewing cited exclusions does not modify the document");
    }

    private static ObjectActor object(String id,int layout,List<ObjectKey> keys) {
        return new ObjectActor(id,id,"s2",0,0,86,0,layout,keys,List.of());
    }
    private static ObjectKey key(int frame,boolean visible,ObjectMode mode,List<ObjectPartPose> parts) {
        return new ObjectKey(frame,96,96,0,-1,false,visible,Interpolation.HOLD,mode,parts);
    }
    private static ObjectKey withVisible(ObjectKey key,boolean visible) {
        return new ObjectKey(key.frame(),key.x(),key.y(),key.animation(),key.pose(),key.flipX(),visible,key.interpolation(),key.mode(),key.parts());
    }
    private static void assertOrder(StageplayWorkspace workspace,ObjectActor second) {
        var actors=workspace.controller().document().objectActors();
        assertEquals(List.of("first","second"),actors.stream().map(ObjectActor::id).toList());
        assertEquals(second,actors.get(1));
    }
    private static String longPartPath(int id) {
        String prefix="root/"+id+"/"+"a".repeat(63)+"/"+"b".repeat(63)+"/"+"c".repeat(63)+"/";
        return prefix+"d".repeat(256-prefix.length());
    }
}
