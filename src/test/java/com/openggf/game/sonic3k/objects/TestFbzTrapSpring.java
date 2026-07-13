package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzTrapSpring {
    @Test
    void subtypeSelectsExactVerticalImpulseAndFlipMode() {
        assertEquals(-0x1000, spring(0).launchVelocity());
        assertEquals(-0xA00, spring(2).launchVelocity());
        assertTrue(spring(1).flipLaunch());
        assertFalse(spring(2).flipLaunch());
    }

    @Test
    void animationScriptsHoldTheirTerminalFrames() throws Exception {
        var down = spring(0);
        setField(down, "animation", 0);
        for (int i = 0; i < 12; i++) down.update(i, null);
        assertEquals(2, getInt(down, "mappingFrame"));
        var up = spring(0);
        setField(up, "animation", 1);
        for (int i = 0; i < 12; i++) up.update(i, null);
        assertEquals(0, getInt(up, "mappingFrame"));
    }

    @Test
    void priorStandingBitsLaunchThreeIdentityDistinctRidersWithExactFlipFacing() {
        AbstractPlayableSprite main = player(Direction.LEFT);
        AbstractPlayableSprite sidekickOne = player(Direction.RIGHT);
        AbstractPlayableSprite sidekickTwo = player(Direction.LEFT);
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        org.mockito.Mockito.when(services.playerQuery()).thenReturn(
                new ObjectPlayerQuery(() -> main, () -> List.of(sidekickOne, sidekickTwo)));
        FbzTrapSpringObjectInstance spring = spring(1, services);

        spring.update(0, main);
        for (AbstractPlayableSprite player : List.of(main, sidekickOne, sidekickTwo)) {
            org.mockito.Mockito.verify(player, org.mockito.Mockito.never()).setYSpeed(org.mockito.Mockito.anyShort());
            spring.onSolidContact(player,
                    new SolidContact(true, false, false, true, false), 0);
        }

        spring.update(1, main);
        org.mockito.Mockito.verify(main).setYSpeed((short) -0x1000);
        org.mockito.Mockito.verify(sidekickOne).setYSpeed((short) -0x1000);
        org.mockito.Mockito.verify(sidekickTwo).setYSpeed((short) -0x1000);
        org.mockito.Mockito.verify(main).setGSpeed((short) -1);
        org.mockito.Mockito.verify(sidekickOne).setGSpeed((short) 1);
        org.mockito.Mockito.verify(sidekickTwo).setGSpeed((short) -1);
        org.mockito.Mockito.verify(main).setFlipAngle(-1);
        org.mockito.Mockito.verify(sidekickOne).setFlipAngle(1);
        org.mockito.Mockito.verify(main).setFlipsRemaining(1);
        org.mockito.Mockito.verify(sidekickOne).setFlipsRemaining(1);
        org.mockito.Mockito.verify(sidekickTwo).setFlipsRemaining(1);
        org.mockito.Mockito.verify(services, org.mockito.Mockito.times(3))
                .playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.SPRING.id);
    }

    @Test
    void subtypeTwoUsesA00AndDisablesFlipCount() {
        AbstractPlayableSprite player = player(Direction.RIGHT);
        ObjectServices services = org.mockito.Mockito.mock(ObjectServices.class);
        FbzTrapSpringObjectInstance.launchPlayer(player, 3, services);
        org.mockito.Mockito.verify(player).setYSpeed((short) -0xA00);
        org.mockito.Mockito.verify(player).setFlipsRemaining(0);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int getInt(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static FbzTrapSpringObjectInstance spring(int subtype) {
        var services = org.mockito.Mockito.mock(com.openggf.level.objects.ObjectServices.class);
        org.mockito.Mockito.when(services.playerQuery()).thenReturn(
                new com.openggf.level.objects.ObjectPlayerQuery(() -> null, java.util.List::of));
        return spring(subtype, services);
    }

    private static FbzTrapSpringObjectInstance spring(int subtype, ObjectServices services) {
        var spring = new FbzTrapSpringObjectInstance(
                new ObjectSpawn(0x1000, 0x700, 0xE3, subtype, 0, false, 1));
        spring.setServices(services);
        return spring;
    }

    private static AbstractPlayableSprite player(Direction direction) {
        AbstractPlayableSprite player = org.mockito.Mockito.mock(AbstractPlayableSprite.class);
        org.mockito.Mockito.when(player.getDirection()).thenReturn(direction);
        org.mockito.Mockito.when(player.getCentreY()).thenReturn((short) 0x700);
        return player;
    }
}
