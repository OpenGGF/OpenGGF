package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class TestPachinkoFlipperObjectInstance {

    @Test
    public void heldJumpDoesNotLaunchImmediatelyOnContact() {
        PachinkoFlipperObjectInstance flipper = new PachinkoFlipperObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0xE7, 0, 0, false, 0));
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.isDebugMode()).thenReturn(false);
        when(player.getRolling()).thenReturn(false);
        when(player.getY()).thenReturn((short) 0x100);
        when(player.getX()).thenReturn((short) 0x100);
        when(player.getRollHeightAdjustment()).thenReturn((short) 10);
        when(player.isJumpPressed()).thenReturn(true, true, false);
        when(player.isJumpJustPressed()).thenReturn(false, false, false);

        flipper.setServices(new TestObjectServices());
        SolidContact standing = new SolidContact(true, false, false, false, false, 0, false);

        flipper.onSolidContact(player, standing, 0);
        flipper.update(0, player);
        flipper.onSolidContact(player, standing, 1);
        flipper.update(1, player);
        flipper.onSolidContact(player, standing, 2);

        verify(player, never()).setYSpeed(anyShort());
        verify(player, never()).setXSpeed(anyShort());
    }

    @Test
    public void launchDistanceUsesPlayerCentreX() {
        PachinkoFlipperObjectInstance flipper = new PachinkoFlipperObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0xE7, 0, 0, false, 0));
        flipper.setServices(new TestObjectServices());

        LaunchVelocity narrowBounds = launchWithPlayerPosition(flipper, 0x100, 0x110);

        flipper = new PachinkoFlipperObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0xE7, 0, 0, false, 0));
        flipper.setServices(new TestObjectServices());
        LaunchVelocity wideBounds = launchWithPlayerPosition(flipper, 0x108, 0x110);

        assertEquals(narrowBounds.xSpeed(), wideBounds.xSpeed(),
                "Changing top-left X without changing ROM x_pos must not change launch X velocity");
        assertEquals(narrowBounds.ySpeed(), wideBounds.ySpeed(),
                "Changing top-left X without changing ROM x_pos must not change launch Y velocity");
    }

    private static LaunchVelocity launchWithPlayerPosition(PachinkoFlipperObjectInstance flipper,
            int topLeftX, int centreX) {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.isDebugMode()).thenReturn(false);
        when(player.getRolling()).thenReturn(true);
        when(player.getAir()).thenReturn(false);
        when(player.getX()).thenReturn((short) topLeftX);
        when(player.getCentreX()).thenReturn((short) centreX);
        when(player.isJumpJustPressed()).thenReturn(true);

        SolidContact standing = new SolidContact(true, false, false, false, false, 0, false);
        flipper.onSolidContact(player, standing, 0);
        flipper.onSolidContact(player, standing, 1);

        ArgumentCaptor<Short> xSpeed = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Short> ySpeed = ArgumentCaptor.forClass(Short.class);
        verify(player).setXSpeed(xSpeed.capture());
        verify(player).setYSpeed(ySpeed.capture());
        return new LaunchVelocity(xSpeed.getValue(), ySpeed.getValue());
    }

    private record LaunchVelocity(short xSpeed, short ySpeed) {
    }
}

