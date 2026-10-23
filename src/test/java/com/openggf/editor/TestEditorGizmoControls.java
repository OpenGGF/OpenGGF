package com.openggf.editor;

import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.HostObjectKeyRegistry;
import com.openggf.level.objects.ObjectPlacementEncoding;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.authored.AuthoredGizmoInstance;
import com.openggf.level.objects.authored.AuthoredGizmos;
import com.openggf.level.objects.authored.AuthoredObjectProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestEditorGizmoControls {
    @Test void allThreeGamesExposeSafeTypedDefaultsAndUseTheirNativeRespawnEnvelope() {
        var registries = List.of(new Sonic1ObjectRegistry(), new Sonic2ObjectRegistry(), new Sonic3kObjectRegistry());
        for (int game = 0; game < registries.size(); game++) {
            ObjectRegistry registry = registries.get(game);
            ObjectPlacementEncoding encoding = game == 0 ? new Sonic1ObjectPlacementEncoding() : new CommonObjectPlacementEncoding();
            var controller = controller(registry, encoding);
            controller.objectPalette().toggleRespawnTracked(); // Gizmos must still keep durable once/consume state.
            controller.objectPalette().toggleRenderFlag(1);
            for (String key : List.of("openggf:camera-zone", "openggf:music-zone", "openggf:stageplay-trigger")) {
                controller.objectPalette().setObjectKey(key);
                controller.setWorldCursor(new EditorCursorState(20, 20)); controller.placeObjectSpawnAtCursor();
                var spawn = controller.selectedObjectSpawn();
                assertTrue(spawn.respawnTracked());
                assertNotNull(spawn.authoredProperties());
                assertEquals(key, spawn.authoredProperties().objectKey());
                assertEquals(64, spawn.authoredProperties().width());
                assertEquals(20 | (game == 0 ? 0x4000 : 0xa000), spawn.rawYWord());
                var instance = registry.create(spawn);
                assertInstanceOf(AuthoredGizmoInstance.class, instance);
                assertTrue(HostObjectKeyRegistry.trusted(registry).ownsHostObject(spawn, instance));
            }
            assertTrue(registry.browsableObjectKeys().containsAll(AuthoredGizmos.KEYS));
        }
    }

    @Test void typedFieldChangesUseOneUndoStepAndKeepThePlacementBrush() {
        var controller = controller(new Sonic2ObjectRegistry(), new CommonObjectPlacementEncoding());
        controller.objectPalette().setObjectKey("openggf:camera-zone");
        controller.setWorldCursor(new EditorCursorState(20, 20)); controller.placeObjectSpawnAtCursor();
        controller.setSelectedGizmoProperty("height", 80);
        assertEquals(80, controller.selectedObjectSpawn().authoredProperties().height());
        controller.undo(); assertEquals(64, controller.currentLevel().getObjects().getFirst().authoredProperties().height());
        assertEquals("openggf:camera-zone", controller.objectPalette().selectedObjectKey());
        controller.undo(); assertTrue(controller.currentLevel().getObjects().isEmpty());
        controller.redo(); controller.redo(); assertEquals(80, controller.currentLevel().getObjects().getFirst().authoredProperties().height());
    }

    @Test void placingWarpSelectsSourceThenExitAndTwoWayChangesBothEndpointsAtomically() {
        var controller = controller(new Sonic2ObjectRegistry(), new CommonObjectPlacementEncoding());
        controller.objectPalette().setObjectKey("openggf:warp");
        controller.setWorldCursor(new EditorCursorState(20, 100)); controller.placeObjectSpawnAtCursor();
        var source = (AuthoredObjectProperties.Warp) controller.selectedObjectSpawn().authoredProperties();
        controller.setWorldCursor(new EditorCursorState(220, 100)); controller.placeObjectSpawnAtCursor();
        var exit = (AuthoredObjectProperties.Warp) controller.selectedObjectSpawn().authoredProperties();
        assertEquals(source.link(), exit.link());
        assertDoesNotThrow(() -> AuthoredGizmos.validatePlacements(controller.currentLevel().getObjects()));
        controller.setSelectedGizmoProperty("twoWay", false);
        assertTrue(controller.currentLevel().getObjects().stream().allMatch(spawn -> !((AuthoredObjectProperties.Warp) spawn.authoredProperties()).twoWay()));
        controller.undo();
        assertTrue(controller.currentLevel().getObjects().stream().allMatch(spawn -> ((AuthoredObjectProperties.Warp) spawn.authoredProperties()).twoWay()));
        controller.undo(); assertEquals(1, controller.currentLevel().getObjects().size());
    }

    private static LevelEditorController controller(ObjectRegistry registry, ObjectPlacementEncoding encoding) {
        var controller = new LevelEditorController(); controller.attachLevel(TestEditorInteractionSafety.level());
        controller.configureSpawnEditing(registry, encoding); controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        return controller;
    }
}
