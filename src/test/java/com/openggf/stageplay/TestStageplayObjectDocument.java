package com.openggf.stageplay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayObjectDocument {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static StageplayDocument scene() {
        return StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224);
    }

    private static ObjectKey key(int frame, double x, ObjectMode mode) {
        return new ObjectKey(frame, x, 200, 0, -1, false, true,
                mode == ObjectMode.FREE ? Interpolation.HOLD : Interpolation.LINEAR, mode);
    }

    private static ObjectActor actor(List<ObjectKey> keys, List<ObjectCommand> commands) {
        return new ObjectActor("crab", "Crab", "s2", 0, 0, 0x92, 0, -1, keys, commands);
    }

    private static StageplayDocument directed() {
        return scene().withObjectActors(List.of(actor(List.of(
                        key(0, 100, ObjectMode.FREE), key(20, 100, ObjectMode.PUPPET),
                        new ObjectKey(40, 120, 200, 2, 3, true, true, Interpolation.HOLD, ObjectMode.POSE,
                                List.of(new ObjectPartPose("root/0", 1, 4))), key(60, 100, ObjectMode.FREE)),
                List.of(new ObjectCommand("walk", 20, ObjectCommandKind.MOVE_TO, 140, 200, 2),
                        new ObjectCommand("stop", 30, ObjectCommandKind.STOP, 0, 0, 0)))))
                .withWorldCommands(List.of(new WorldCommand("remove", 80, WorldCommandKind.REMOVE_OBJECT,
                        "crab", ObjectTarget.none())));
    }

    @Test void immutableObjectTracksSurviveEveryExistingDocumentHelper() throws Exception {
        var keys = new ArrayList<>(directed().objectActors().getFirst().keys());
        var commands = new ArrayList<>(directed().objectActors().getFirst().commands());
        var roles = new ArrayList<>(List.of(actor(keys, commands)));
        var document = scene().withObjectActors(roles).withWorldCommands(directed().worldCommands());
        keys.clear(); commands.clear(); roles.clear();
        assertEquals(4, document.objectActors().getFirst().keys().size());
        var edited = document.withName("Edited").withDurationFrames(601).withSize(400, 224)
                .withSet(document.set()).withActors(List.of()).withCamera(document.camera())
                .withCaptions(List.of()).withAudio(List.of()).withCameraEffects(List.of())
                .withSceneChanges(List.of()).withDialogueTypers(List.of()).withDialogueSkins(List.of())
                .withDialogue(List.of()).withPuppets(List.of());
        assertEquals(document.objectActors(), edited.objectActors());
        assertEquals(document.worldCommands(), edited.worldCommands());
        assertEquals(document, StageplayStore.decode(StageplayStore.encode(document)));
        assertThrows(UnsupportedOperationException.class, () -> document.objectActors().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.worldCommands().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.objectActors().getFirst().keys().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.objectActors().getFirst().commands().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.objectActors().getFirst().keys().get(2).parts().clear());
        assertTrue(key(0, 100, ObjectMode.POSE).parts().isEmpty());
    }

    @Test void exactVersionsOneThroughSixMigrateWithoutNewTracksOrPolicyChanges() throws Exception {
        for (int version = 1; version <= 6; version++) {
            var old = (ObjectNode) JSON.readTree(StageplayStore.encode(scene()));
            old.put("version", version); old.remove(List.of("objectActors", "worldCommands"));
            if (version <= 4) {
                ((ObjectNode) old.get("set")).remove("worldPolicy");
                for (var camera : old.withArray("camera")) ((ObjectNode) camera).remove("follow");
            }
            if (version <= 3) old.remove("puppets");
            if (version <= 2) old.remove(List.of("dialogueTypers", "dialogueSkins", "dialogue"));
            if (version == 1) old.remove(List.of("cameraEffects", "sceneChanges"));
            var migrated = StageplayStore.decode(JSON.writeValueAsBytes(old));
            assertEquals(VERSION, migrated.version());
            assertTrue(migrated.objectActors().isEmpty()); assertTrue(migrated.worldCommands().isEmpty());
            assertEquals(version == 6 ? WorldPolicy.LIVE : version == 5 ? WorldPolicy.LEGACY_LIVE
                    : WorldPolicy.LEGACY_TERRAIN_ONLY, migrated.set().worldPolicy());
            for (String field : List.of("objectActors", "worldCommands")) {
                var disguised = old.deepCopy(); disguised.putArray(field);
                assertThrows(IOException.class, () -> StageplayStore.decode(JSON.writeValueAsBytes(disguised)));
                disguised.putNull(field);
                assertThrows(IOException.class, () -> StageplayStore.decode(JSON.writeValueAsBytes(disguised)));
            }
            assertEquals(migrated, StageplayStore.decode(StageplayStore.encode(migrated)));
        }
        var d = scene();
        var oldConstructor = new StageplayDocument(6, d.name(), d.durationFrames(), d.width(), d.height(),
                d.set(), d.actors(), d.camera(), d.captions(), d.audio(), d.cameraEffects(), d.sceneChanges(),
                d.dialogueTypers(), d.dialogueSkins(), d.dialogue(), d.puppets());
        assertEquals(d, oldConstructor);
    }

    @Test void strictObjectJsonRejectsMissingUnknownCoercedAndRuntimeIdentityFields() throws Exception {
        List<Consumer<ObjectNode>> edits = List.of(
                root -> root.remove("objectActors"), root -> root.remove("worldCommands"),
                root -> root.putNull("objectActors"), root -> role(root).put("runtimeSlot", 9),
                root -> role(root).remove("sourceKind"), root -> role(root).put("sourceKind", "CUSTOM"),
                root -> role(root).put("layoutIndex", "5"), root -> role(root).remove("zone"),
                root -> key(root, 0).put("visible", "true"), root -> key(root, 0).put("mode", 0),
                root -> key(root, 0).put("slot", 1), root -> key(root, 2).remove("parts"),
                root -> ((ObjectNode) key(root, 2).path("parts").get(0)).put("runtimeSlot", 2),
                root -> ((ObjectNode) role(root).path("commands").get(0)).put("teleport", true),
                root -> ((ObjectNode) root.path("worldCommands").get(0)).put("runtimeSlot", 1));
        for (var edit : edits) {
            var root = (ObjectNode) JSON.readTree(StageplayStore.encode(directed())); edit.accept(root);
            assertThrows(IOException.class, () -> StageplayStore.decode(JSON.writeValueAsBytes(root)), root.toString());
        }
    }

    @Test void modeChangesRejectIgnoredFreeEditsAndCommandsOutsidePuppetIntervals() {
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey(0, 0, 0, 1, -1, false, true,
                Interpolation.HOLD, ObjectMode.FREE));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey(0, 0, 0, 0, -1, false, true,
                Interpolation.LINEAR, ObjectMode.FREE));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey(0, 0, 0, 0, -1, false, true,
                Interpolation.HOLD, ObjectMode.FREE, List.of(new ObjectPartPose("root/0", 0, 1))));
        assertThrows(IllegalArgumentException.class, () -> scene().withObjectActors(List.of(actor(
                List.of(key(0, 100, ObjectMode.FREE), key(20, 110, ObjectMode.FREE)), List.of()))));
        for (var mode : List.of(ObjectMode.FREE, ObjectMode.POSE))
            assertThrows(IllegalArgumentException.class, () -> scene().withObjectActors(List.of(actor(
                    List.of(key(0, 100, mode)), List.of(new ObjectCommand("go", 1,
                            ObjectCommandKind.MOVE_TO, 200, 200, 1))))));
        assertThrows(IllegalArgumentException.class, () -> new ObjectCommand("go", 0, ObjectCommandKind.MOVE_TO, 1, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new ObjectCommand("stop", 0, ObjectCommandKind.STOP, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> directed().withDurationFrames(60));
        assertThrows(IllegalArgumentException.class, () -> new ObjectPartPose("root/../3", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ObjectKey(0, 1, 2, 0, -1, false, true,
                Interpolation.HOLD, ObjectMode.POSE, List.of(new ObjectPartPose("root/0", 0, 1), new ObjectPartPose("root/0", 0, 2))));
    }

    @Test void portablePlacementIsUniqueAndScopedToItsVisibleShot() {
        var placed = new ObjectActor("loaded", "Loaded", "s2", 0, 0, 0x92, 3, 5,
                List.of(key(0, 100, ObjectMode.PUPPET)), List.of());
        var duplicate = new ObjectActor("duplicate", "Duplicate", "s2", 0, 0, 0x92, 3, 5,
                placed.keys(), List.of());
        assertThrows(IllegalArgumentException.class, () -> scene().withObjectActors(List.of(placed, duplicate)));
        assertThrows(IllegalArgumentException.class, () -> scene().withObjectActors(List.of(placed))
                .withSet(new SetSource("s2", 1, 0, "", "", WorldPolicy.LIVE)));
        assertThrows(IllegalArgumentException.class, () -> scene().withObjectActors(List.of(placed))
                .withSet(new SetSource("s2", 0, 0, "", "", WorldPolicy.LEGACY_TERRAIN_ONLY)));
        var anchored = scene().withCamera(List.of(scene().camera().getFirst(), new CameraKey(100, 0, 0, 1, Interpolation.HOLD)))
                .withSceneChanges(List.of(new SceneChange("next", 100, scene().set(), Transition.CUT, 0)));
        assertThrows(IllegalArgumentException.class, () -> anchored.withObjectActors(List.of(placed)));
        var hidden = new ObjectKey(100, 100, 200, 0, -1, false, false, Interpolation.HOLD, ObjectMode.FREE);
        var withAnchor = new ObjectActor("loaded", "Loaded", "s2", 0, 0, 0x92, 3, 5,
                List.of(key(0, 100, ObjectMode.PUPPET), hidden), List.of());
        assertEquals(2, anchored.withObjectActors(List.of(withAnchor)).objectActors().getFirst().keys().size());
    }

    @Test void clearAndRemovalCommandsRequireExactPortableTargetsAndOrderedFrames() {
        var target = new ObjectTarget(7, 0x92, 3, 0, 0);
        var clear = new WorldCommand("clear", 0, WorldCommandKind.CLEAR_BADNIKS, "", ObjectTarget.none());
        var remove = new WorldCommand("remove", 1, WorldCommandKind.REMOVE_OBJECT, "", target);
        assertEquals(List.of(clear, remove), scene().withWorldCommands(List.of(clear, remove)).worldCommands());
        assertThrows(IllegalArgumentException.class, () -> scene().withWorldCommands(List.of(remove, clear)));
        assertThrows(IllegalArgumentException.class, () -> new WorldCommand("clear", 0, WorldCommandKind.CLEAR_OBJECTS, "crab", target));
        assertThrows(IllegalArgumentException.class, () -> new WorldCommand("remove", 0, WorldCommandKind.REMOVE_OBJECT, "crab", target));
        assertThrows(IllegalArgumentException.class, () -> new WorldCommand("remove", 0, WorldCommandKind.REMOVE_OBJECT, "", ObjectTarget.none()));
        assertThrows(IllegalArgumentException.class, () -> new WorldCommand("remove", 0, WorldCommandKind.REMOVE_OBJECT, "", new ObjectTarget(7, 0x92, 3, 1, 0)));
        assertThrows(IllegalArgumentException.class, () -> scene().withWorldCommands(List.of(new WorldCommand(
                "remove", 1, WorldCommandKind.REMOVE_OBJECT, "missing", ObjectTarget.none()))));
    }

    @Test void releasedAnimalsUseProviderIdentityAndKeepTheLegacyConstructorDefault() throws Exception {
        var keys = List.of(key(0, 100, ObjectMode.FREE));
        assertEquals(ObjectSourceKind.LAYOUT_OBJECT, actor(keys, List.of()).sourceKind());
        var animal = new ObjectActor("animal", "Animal", "s2", 0, 0, 0, 0, -1, keys, List.of(), ObjectSourceKind.RELEASED_ANIMAL);
        var document = scene().withObjectActors(List.of(animal));
        assertEquals(document, StageplayStore.decode(StageplayStore.encode(document)));
        assertThrows(IllegalArgumentException.class, () -> new ObjectActor("animal", "Animal", "s2", 0, 0,
                0x28, 0, -1, keys, List.of(), ObjectSourceKind.RELEASED_ANIMAL));
        assertThrows(IllegalArgumentException.class, () -> new ObjectActor("animal", "Animal", "s2", 0, 0,
                0, 0, 5, keys, List.of(), ObjectSourceKind.RELEASED_ANIMAL));
        assertThrows(IllegalArgumentException.class, () -> new ObjectActor("animal", "Animal", "s2", 0, 0,
                0, 1, -1, keys, List.of(), ObjectSourceKind.RELEASED_ANIMAL));
    }

    private static ObjectNode role(ObjectNode root) { return (ObjectNode) root.path("objectActors").get(0); }
    private static ObjectNode key(ObjectNode root, int index) { return (ObjectNode) role(root).path("keys").get(index); }
}
