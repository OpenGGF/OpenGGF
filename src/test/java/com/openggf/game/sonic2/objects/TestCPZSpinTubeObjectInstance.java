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

    @Test
    void captureForcesRollAnimationWithoutSettingRollingStatus() {
        ObjectSpawn spawn = new ObjectSpawn(0x0780, 0x0380, 0x1E, 0x02, 0, false, 0);
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic",
                (short) spawn.x(),
                (short) spawn.y());
        player.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);
        assertFalse(player.getRolling(), "precondition: standing player enters Obj1E");

        CPZSpinTubeObjectInstance tube = new CPZSpinTubeObjectInstance(spawn, "CPZSpinTube");
        tube.setServices(new TestObjectServices());

        tube.update(0, player);

        assertEquals(Sonic2AnimationIds.ROLL.id(), player.getAnimationId(),
                "Obj1E loc_22688 writes AniIDSonAni_Roll to anim(a1).");
        assertFalse(player.getRolling(),
                "Obj1E loc_22688 does not set status.player.rolling; it only forces the roll animation.");
        assertEquals(0x03B0, player.getCentreY(),
                "The capture waypoint y_pos must remain the ROM centre value without setRolling's 5px radius shift.");
    }

    @Test
    void releaseDefersMovementSuppressionThroughCurrentFrameAndPreservesYSubpixel() throws Exception {
        ObjectSpawn spawn = new ObjectSpawn(0x2480, 0x0500, 0x1E, 0x02, 0, false, 0);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x24E8, (short) 0x0B30);
        player.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);
        player.setSubpixelRaw(0x4000, 0xAA00);
        player.setObjectControlled(true);
        player.setObjectControlSuppressesMovement(true);

        CPZSpinTubeObjectInstance tube = new CPZSpinTubeObjectInstance(spawn, "CPZSpinTube");
        tube.setServices(new TestObjectServices());
        var characterStateConstructor = Class.forName(CPZSpinTubeObjectInstance.class.getName() + "$CharacterState")
                .getDeclaredConstructor();
        characterStateConstructor.setAccessible(true);
        Object characterState = characterStateConstructor.newInstance();
        var exitTube = CPZSpinTubeObjectInstance.class.getDeclaredMethod(
                "exitTube", AbstractPlayableSprite.class, characterState.getClass(), int.class);
        exitTube.setAccessible(true);

        exitTube.invoke(tube, player, characterState, 3722);

        assertEquals(0x0330, player.getCentreY(),
                "Obj1E loc_227A6 masks y_pos with $7FF before release.");
        assertEquals(0xAA00, player.getYSubpixelRaw(),
                "andi.w #$7FF,y_pos(a1) leaves the sibling y_sub word untouched.");
        assertTrue(player.isObjectControlled(),
                "Obj1E clears obj_control after the player slot has already run; engine release must defer to endOfTick.");
        assertTrue(player.isObjectControlSuppressesMovement(),
                "The release-side frame must still skip normal movement/gravity.");

        player.endOfTick();

        assertFalse(player.isObjectControlled(), "deferred release lands at endOfTick");
        assertFalse(player.isObjectControlSuppressesMovement(), "movement suppression clears with deferred release");
    }
}
