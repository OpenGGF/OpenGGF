package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzExitDoor {
    @Test void nativeDoorDataAndFirstCollisionMatchObjFbzExitDoor() {
        var door = new FbzExitDoorInstance(new ObjectSpawn(0x3000, 0x700, 0xCE, 0, 0, false, 1));
        var player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) 0x2FF0);

        assertEquals(0xD7, door.getCollisionFlags());
        door.onTouchResponse(player, new TouchResponseResult(0x17, 0x10, 0x20, TouchCategory.SPECIAL), 0);

        assertTrue(door.isFlying());
        assertEquals(0x800, door.xVelocity());
        verify(player).shiftX(-8);
        assertEquals(0, door.getCollisionFlags());
    }

    @Test void lightGravityMovesAtEightPixelsThenAddsThirtyEight() {
        var door = new FbzExitDoorInstance(new ObjectSpawn(0x3000, 0x700, 0xCE, 0, 0, false, 1));
        door.triggerForTest();
        door.update(0, null);
        assertEquals(0x3008, door.getX());
        assertEquals(0x700, door.getY());
        assertEquals(0x20, door.yVelocity());
    }

    @Test void unexpectedMainPlayerQueryFailureIsNotHiddenByTheDoorHit() {
        var door = new FbzExitDoorInstance(new ObjectSpawn(0x3000, 0x700, 0xCE, 0, 0, false, 1));
        ObjectServices services = mock(ObjectServices.class);
        when(services.playerQuery()).thenThrow(new IllegalStateException("broken query"));
        door.setServices(services);
        var player = mock(AbstractPlayableSprite.class);

        assertThrows(IllegalStateException.class, () -> door.onTouchResponse(
                player, new TouchResponseResult(0x17, 0x10, 0x20, TouchCategory.SPECIAL), 0));
    }
}
