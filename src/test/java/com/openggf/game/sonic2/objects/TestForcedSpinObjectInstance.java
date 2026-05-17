package com.openggf.game.sonic2.objects;

import com.openggf.game.GroundMode;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestForcedSpinObjectInstance {

    @Test
    void autorollPreservesXPosWhenTriggeredOnWallMode() throws Exception {
        ForcedSpinObjectInstance trigger = new ForcedSpinObjectInstance(
                new ObjectSpawn(0x1C60, 0x0438, Sonic2ObjectIds.FORCED_SPIN, 0, 0, false, 0),
                "ForcedSpin");
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1C6C, (short) 0x0431);
        player.setGroundMode(GroundMode.RIGHTWALL);

        short centreX = player.getCentreX();
        short centreY = player.getCentreY();

        Method enablePinballMode = ForcedSpinObjectInstance.class
                .getDeclaredMethod("enablePinballMode", AbstractPlayableSprite.class);
        enablePinballMode.setAccessible(true);
        enablePinballMode.invoke(trigger, player);

        assertEquals(centreX, player.getCentreX(),
                "Obj84 loc_212C4 sets rolling radii and adds 5 to y_pos, but never changes x_pos");
        assertEquals((short) (centreY + 5), player.getCentreY());
        assertTrue(player.getRolling());
        assertTrue(player.getPinballMode());
    }
}
