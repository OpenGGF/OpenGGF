package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzExitHall {
    @Test void subtypeLongwordOffsetsSelectTheTwoNativeRecords() {
        var doorScenery = new FbzExitHallInstance(new ObjectSpawn(0x3000, 0x700, 0x8A, 0, 0, false, 1));
        var hall = new FbzExitHallInstance(new ObjectSpawn(0x3020, 0x700, 0x8A, 4, 0, false, 2));
        assertEquals(0, doorScenery.mappingFrame());
        assertEquals(0, doorScenery.getPriorityBucket());
        assertEquals(1, hall.mappingFrame());
        assertEquals(5, hall.getPriorityBucket());
        assertFalse(((Object) hall) instanceof com.openggf.level.objects.SolidObjectProvider);
        assertEquals(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.FBZ_EXIT_HALL_DOOR_SCENERY,
                doorScenery.renderArtKeyForTest());
        assertEquals(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.FBZ_EXIT_HALL,
                hall.renderArtKeyForTest());
    }

    @Test void spriteOnScreenTestUsesNativeCoarse280AndRespawnableDeletion() {
        var hall = new FbzExitHallInstance(new ObjectSpawn(0x3000, 0x700, 0x8A, 4, 0, true, 2));
        com.openggf.camera.Camera camera = org.mockito.Mockito.mock(com.openggf.camera.Camera.class);
        org.mockito.Mockito.when(camera.getX()).thenReturn((short) 0x0000);
        hall.setServices(new com.openggf.level.objects.StubObjectServices() {
            @Override public com.openggf.camera.Camera camera() { return camera; }
        });
        hall.update(0, null);
        assertTrue(hall.isDestroyed());
        assertTrue(hall.wasDestroyedByOffscreen(), "Sprite_OnScreen_Test clears the placement load bit");
    }
}
