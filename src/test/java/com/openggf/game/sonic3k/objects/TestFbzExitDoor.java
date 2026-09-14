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

        assertFalse(door.isFlying(), "Touch_Special only publishes the collision property");
        verify(player, never()).shiftX(anyInt());
        door.update(0, player);
        assertTrue(door.isFlying());
        assertEquals(0x800, door.xVelocity());
        verify(player).shiftX(-8);
        assertEquals(0, door.getCollisionFlags());
        assertEquals(0x3008, door.getX(), "the recognition dispatch falls through into movement");
        assertEquals(0x20, door.yVelocity());
    }

    @Test void earlierAutoSpinSlotSeesThePositionBeforeTheDoorKnockback() {
        var player = new com.openggf.game.sonic1.objects.TestPlayableSprite();
        player.setCentreX((short) 0x3660);
        player.setCentreY((short) 0x66C);
        ObjectServices services = mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new com.openggf.level.objects.ObjectPlayerQuery(
                () -> player, java.util.List::of));
        var spin = new AutoSpinObjectInstance(new ObjectSpawn(0x3670, 0x660, 0, 0x80, 0, false, 0));
        spin.setServices(services);
        var door = new FbzExitDoorInstance(new ObjectSpawn(0x3680, 0x660, 0xCE, 0, 0, false, 0));
        door.setServices(services);
        spin.update(0, player);
        player.setCentreX((short) 0x3671);
        door.onTouchResponse(player, new TouchResponseResult(0x17, 0x10, 0x20, TouchCategory.SPECIAL), 1);
        spin.update(1, player); // native earlier SST slot observes the crossing
        assertTrue(player.getRolling());
        assertEquals(0x580, player.getGSpeed());
        door.update(1, player);
        assertEquals(0x3669, player.getCentreX());
        assertEquals(0x671, player.getCentreY());
        assertEquals(0x3688, door.getX());
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

        door.onTouchResponse(player, new TouchResponseResult(0x17, 0x10, 0x20, TouchCategory.SPECIAL), 0);
        assertThrows(IllegalStateException.class, () -> door.update(0, player));
    }
}
