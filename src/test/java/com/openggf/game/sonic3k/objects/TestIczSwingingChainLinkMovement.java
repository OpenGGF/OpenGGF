package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class TestIczSwingingChainLinkMovement {
    @Test
    void moveSpriteUsesOldVelocityThenAddsNativeGravity() throws Exception {
        var link = link(0x1000, 0x600);
        link.update(0, null);
        link.update(1, null);
        assertEquals(0x1000, link.getX());
        assertEquals(0x5FF, link.getY());
        assertEquals(0x8000, integer(link, "ySub"));
        assertEquals(-0x48, integer(link, "yVel"));
        link.update(2, null);
        assertEquals(0x1001, link.getX());
        assertEquals(0x5FF, link.getY());
        assertEquals(0x3800, integer(link, "ySub"));
        assertEquals(-0x10, integer(link, "yVel"));
    }

    @Test
    void coarseXAndUnsignedYBoundariesScheduleDeletionForNextDispatch() throws Exception {
        for (int[] position : new int[][] {
                {0x0F80, 0x0580, 1}, {0x1200, 0x0780, 1},
                {0x0F7F, 0x0600, 0}, {0x1280, 0x0600, 0},
                {0x1000, 0x057F, 0}, {0x1000, 0x0781, 0}}) {
            var link = link(position[0], position[1]);
            set(link, "xVel", 0); set(link, "yVel", 0);
            link.update(0, null);
            link.update(1, null);
            assertFalse(link.isDestroyed(), "Go_Delete_Sprite retains this dispatch's SST slot");
            assertEquals(position[2] == 0, field(link, "deleteNextFrame").getBoolean(link));
            // Freeze velocity again so inclusive boundary cases stay at the edge.
            set(link, "yVel", 0);
            link.update(2, null);
            assertEquals(position[2] == 0, link.isDestroyed());
        }
    }

    private static IczSwingingPlatformChainLinkInstance link(int x, int y) {
        AbstractObjectInstance.updateCameraBounds(0x1000, 0x600, 0x1140, 0x6E0, 0);
        var link = new IczSwingingPlatformChainLinkInstance(x, y, 0, false);
        link.setServices(new StubObjectServices());
        return link;
    }

    private static int integer(Object target, String name) throws Exception { return field(target, name).getInt(target); }
    private static void set(Object target, String name, int value) throws Exception { field(target, name).setInt(target, value); }
    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
}
