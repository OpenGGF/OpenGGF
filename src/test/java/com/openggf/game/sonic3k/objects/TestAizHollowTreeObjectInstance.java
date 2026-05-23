package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestAizHollowTreeObjectInstance {

    @Test
    void captureSetsObjectControlBitsSixAndOneWithoutSuppressingMovement() {
        AizHollowTreeObjectInstance tree = new AizHollowTreeObjectInstance(new ObjectSpawn(
                0x2D00, 0x03CC, Sonic3kObjectIds.AIZ_HOLLOW_TREE, 0, 0, false, 0));
        tree.setServices(new TestObjectServices().withCamera(new Camera()));
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 0)
                .build()
                .sprite();

        player.setCentreX((short) 0x2CF1);
        player.setCentreY((short) 0x0456);
        player.setXSpeed((short) 0x0AAC);
        player.setGSpeed((short) 0x0AE3);
        player.setAir(false);
        clearFixtureIntroControl(player);

        tree.update(0, player);

        assertTrue(player.isObjectControlled(),
                "Obj_AIZHollowTree sets object_control bits 6+1 while riding: " + tree.traceDebugDetails());
        assertTrue(player.isObjectControlAllowsCpu(),
                "Bits 6+1 are not ROM bit 7, so CPU/touch dispatch must not be suppressed: "
                        + tree.traceDebugDetails());
        assertFalse(player.isObjectControlSuppressesMovement(),
                "Obj_AIZHollowTree does not set object_control bit 0");
        assertTrue(player.isSuppressGroundWallCollision(),
                "object_control bit 6 makes Sonic_WalkSpeed skip CalcRoomInFront");
    }

    @Test
    void fallOffTreeClearsAllObjectControlState() {
        AizHollowTreeObjectInstance tree = new AizHollowTreeObjectInstance(new ObjectSpawn(
                0x2D00, 0x03CC, Sonic3kObjectIds.AIZ_HOLLOW_TREE, 0, 0, false, 0));
        tree.setServices(new TestObjectServices().withCamera(new Camera()));
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 0)
                .build()
                .sprite();

        player.setCentreX((short) 0x2CF1);
        player.setCentreY((short) 0x0456);
        player.setXSpeed((short) 0x0AAC);
        player.setGSpeed((short) 0x0AE3);
        player.setAir(false);
        clearFixtureIntroControl(player);

        tree.update(0, player);
        assertTrue(player.isObjectControlled(), "Expected hollow tree capture precondition");

        player.setGSpeed((short) 0);
        tree.update(1, player);

        assertFalse(player.isObjectControlled(),
                "Hollow-tree fall-off should clear object-control ownership");
        assertFalse(player.isObjectControlAllowsCpu(),
                "Hollow-tree fall-off should clear CPU allowance");
        assertFalse(player.isObjectControlSuppressesMovement(),
                "Hollow-tree fall-off should clear movement suppression");
        assertFalse(player.isTouchResponseSuppressedByObjectControl(),
                "Hollow-tree fall-off should clear touch suppression");
        assertFalse(player.isSuppressGroundWallCollision(),
                "Hollow-tree fall-off should clear bit-6 wall-probe suppression");
    }

    private static void clearFixtureIntroControl(AbstractPlayableSprite player) {
        ObjectControlState.none().applyTo(player);
        player.setControlLocked(false);
        player.setObjectMappingFrameControl(false);
    }
}
