package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAutomaticTunnelObjectInstance {
    @Test
    void captureUsesFullControlAndRouteReleaseClearsControlPolicy() {
        AutomaticTunnelObjectInstance tunnel = new AutomaticTunnelObjectInstance(
                new ObjectSpawn(0x0F60, 0x0578, 0x24, 0, 0, false, 0));
        tunnel.setServices(new TestObjectServices());

        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x0F60);
        player.setCentreY((short) 0x0578);
        player.setControlLocked(false);
        player.setObjectControlled(false);

        tunnel.update(0, player);

        assertTrue(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertTrue(player.isTouchResponseSuppressedByObjectControl());
        assertTrue(player.isControlLocked());

        for (int frame = 1; frame <= 80 && player.isObjectControlled(); frame++) {
            tunnel.update(frame, player);
        }

        assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement());
        assertFalse(player.isTouchResponseSuppressedByObjectControl());
        assertFalse(player.isControlLocked());
    }
}
