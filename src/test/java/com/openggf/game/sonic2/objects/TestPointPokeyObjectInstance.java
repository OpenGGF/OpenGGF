package com.openggf.game.sonic2.objects;

import com.openggf.game.PhysicsFeatureSet;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPointPokeyObjectInstance {

    @Test
    void captureUsesObjectControlWithoutGlobalControlLockedLatch() throws Exception {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x04D3, (short) 0x043C);
        player.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);
        player.setLogicalInputState(false, false, true, false, false);
        player.endOfTick();
        assertEquals(AbstractPlayableSprite.INPUT_LEFT, player.getInputHistory(0));

        PointPokeyObjectInstance pokey = new PointPokeyObjectInstance(
                new ObjectSpawn(0x04C0, 0x0460, 0xD6, 0x00, 0, false, 0),
                "PointPokey");

        invokeCapture(pokey, player);

        assertTrue(player.isObjectControlled(),
                "ObjD6 writes obj_control=$81, so bit 7 must still suppress movement/physics.");
        assertFalse(player.isControlLocked(),
                "S2 ObjD6 writes obj_control(a1), not global Control_Locked; Obj01_Control must keep "
                        + "copying Ctrl_1 into Ctrl_1_Logical (s2.asm:36227-36235,59021).");

        player.setLogicalInputState(false, false, false, false, false);
        player.endOfTick();
        assertEquals(0, player.getInputHistory(0),
                "With Control_Locked untouched, the next raw zero input refreshes Ctrl_1_Logical before "
                        + "Sonic_RecordPos stores follower history (s2.asm:36227-36246,36346).");
    }

    private static void invokeCapture(PointPokeyObjectInstance pokey, TestablePlayableSprite player) throws Exception {
        Method capture = PointPokeyObjectInstance.class.getDeclaredMethod("capturePlayer", AbstractPlayableSprite.class);
        capture.setAccessible(true);
        capture.invoke(pokey, player);
    }
}
