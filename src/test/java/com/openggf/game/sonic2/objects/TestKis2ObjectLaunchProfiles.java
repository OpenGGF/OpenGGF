package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class TestKis2ObjectLaunchProfiles {
    @Test
    void propellerClearsRollJumpAndGlideOnlyForPatchedHost() throws Exception {
        Method push = HPropellerObjectInstance.class.getDeclaredMethod("checkAndPushPlayer", AbstractPlayableSprite.class);
        push.setAccessible(true);
        for (boolean patched : new boolean[] {false, true}) {
            HPropellerObjectInstance propeller = new HPropellerObjectInstance(
                    new ObjectSpawn(0x400, 0x300, 0xB5, 0x66, 0, false, 0), patched);
            propeller = propeller.recreateForRewind(new RewindRecreateContext(propeller.getSpawn(), null, null));
            TestablePlayableSprite player = new TestablePlayableSprite("knuckles", (short) 0, (short) 0);
            player.setCentreX((short) 0x400);
            player.setCentreY((short) 0x2D0);
            player.setRollingJump(true);
            player.setDoubleJumpFlag(1);
            player.setSubpixelRaw(0x1234, 0x5678);
            push.invoke(propeller, player);
            assertTrue(player.getAir());
            assertEquals(!patched, player.getRollingJump());
            assertEquals(patched ? 0 : 1, player.getDoubleJumpFlag());
            assertEquals(0, player.getYSpeed());
            assertEquals(1, player.getGSpeed());
            assertEquals(0x5678, player.getYSubpixelRaw());
        }
    }

    @Test
    void fallingPillarChildRetainsGroundedCrushProfile() {
        ObjectSpawn spawn = new ObjectSpawn(0x400, 0x300, 0x23, 0, 0, false, 0);
        assertFalse(new FallingPillarObjectInstance(spawn, "Pillar").createChild()
                .groundedBottomContactAlwaysSquashes());
        FallingPillarObjectInstance patched = new FallingPillarObjectInstance(spawn, "Pillar", true);
        assertTrue(patched.createChild().groundedBottomContactAlwaysSquashes());
        FallingPillarObjectInstance recreated = patched.recreateForRewind(
                new RewindRecreateContext(spawn, null, null));
        assertTrue(recreated.groundedBottomContactAlwaysSquashes());
        assertTrue(recreated.createChild().groundedBottomContactAlwaysSquashes());
    }
}
