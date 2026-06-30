package com.openggf.game.sonic2.objects;

import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCPZSpinTubeObjectInstance {

    @Test
    void captureUsesObjectControlWithoutGlobalControlLockedLatch() {
        ObjectSpawn spawn = new ObjectSpawn(0x0780, 0x0380, 0x1E, 0x02, 0, false, 0);
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic",
                (short) spawn.x(),
                (short) spawn.y());
        player.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);
        player.setLogicalInputState(false, false, true, false, false);
        player.endOfTick();
        assertEquals(AbstractPlayableSprite.INPUT_LEFT, player.getInputHistory(0));

        CPZSpinTubeObjectInstance tube = new CPZSpinTubeObjectInstance(spawn, "CPZSpinTube");
        tube.setServices(new TestObjectServices());

        tube.update(0, player);

        assertTrue(player.isObjectControlled(),
                "Obj1E writes obj_control=$81, so tube traversal must suppress normal movement/physics.");
        assertFalse(player.isControlLocked(),
                "S2 Obj1E writes obj_control(a1), not global Control_Locked; Ctrl_1_Logical must keep refreshing "
                        + "while the tube owns movement (s2.asm:48551-48568).");
        assertEquals(Sonic2AnimationIds.ROLL.id(), player.getAnimationId());

        player.setLogicalInputState(false, false, false, false, false);
        player.endOfTick();
        assertEquals(0, player.getInputHistory(0),
                "With Control_Locked untouched, a raw zero input refreshes Ctrl_1_Logical on the next tick "
                        + "instead of keeping stale pre-capture input.");
    }
}
