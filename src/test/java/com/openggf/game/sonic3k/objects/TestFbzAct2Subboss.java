package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Locked-on oracle for Obj_FBZ2Subboss (sonic3k.asm:148033-148695). */
class TestFbzAct2Subboss {
    @Test void nativeBoundsTriggerAndSevenCycleCounterAreExact() {
        assertArrayEquals(new int[] {0x560, 0x660, 0x2900, 0x2C00},
                Fbz2SubbossInstance.activationBounds());
        assertEquals(0x18, Fbz2SubbossInstance.triggerDistanceExclusive());
        assertTrue(Fbz2SubbossInstance.cameraInActivationRange(0x2900,0x560));
        assertTrue(Fbz2SubbossInstance.cameraInActivationRange(0x2C00,0x660));
        assertFalse(Fbz2SubbossInstance.cameraInActivationRange(0x28FF,0x560));
        assertFalse(Fbz2SubbossInstance.cameraInActivationRange(0x2C01,0x660));
        Fbz2SubbossInstance boss = boss();
        assertEquals(7, boss.cyclesRemaining());
        for (int i = 0; i < 6; i++) {
            boss.completeLaserCycleForTest();
            assertFalse(boss.isDefeated());
        }
        boss.completeLaserCycleForTest();
        assertTrue(boss.isDefeated());
    }

    @Test void nativeRootDeleteUsesFixedCoarseBackWindowIndependentOfViewportWidth() {
        int cameraX=0x2B00;
        int coarseBack=(cameraX-0x80)&0xFF80;
        assertTrue(Fbz2SubbossInstance.nativeSpriteCheckDeleteXKeepsAlive(coarseBack+0x280,cameraX));
        assertFalse(Fbz2SubbossInstance.nativeSpriteCheckDeleteXKeepsAlive(coarseBack+0x300,cameraX));
        assertFalse(Fbz2SubbossInstance.nativeSpriteCheckDeleteXKeepsAlive(coarseBack-0x80,cameraX),
                "unsigned wrap deletes objects behind the camera");
    }

    @Test void dropChargeAndDefeatWaitWordsUseNativeUnderflowDurations() {
        assertEquals(56, Fbz2SubbossInstance.dropUpdates());
        assertEquals(64, Fbz2SubbossInstance.preLaserWaitUpdates());
        assertEquals(128, Fbz2SubbossInstance.cycleWaitUpdates());
        assertEquals(96, Fbz2SubbossInstance.defeatWaitUpdates());

        Fbz2SubbossInstance boss = boss();
        PlayableEntity p1 = mock(PlayableEntity.class);
        when(p1.getCentreX()).thenReturn((short) 0x2B40);
        boss.update(-1, p1);
        boss.update(0, p1);
        assertEquals("DROP", boss.phaseName());
        for (int i = 0; i < 55; i++) boss.update(i + 1, p1);
        assertEquals(0x5F0 + 27, boss.getY());
        boss.update(56, p1);
        assertEquals(0x5F0 + 28, boss.getY());
        assertEquals("PRE_LASER_WAIT", boss.phaseName());
        for (int i = 0; i < 63; i++) boss.update(57 + i, p1);
        assertEquals("PRE_LASER_WAIT", boss.phaseName());
        boss.update(120, p1);
        assertEquals("ACTIVE", boss.phaseName());
    }

    @Test void onlySixNonfinalCyclesMoveTheLeftAnchors() {
        Fbz2SubbossInstance boss = boss();
        Fbz2SubbossCornerChild upperLeft = Fbz2SubbossCornerChild.forTest(boss, 0);
        Fbz2SubbossCornerChild upperRight = Fbz2SubbossCornerChild.forTest(boss, 2);
        int leftStartX = upperLeft.getX();
        int startX = upperRight.getX();
        for (int cycle = 0; cycle < 6; cycle++) {
            boss.completeLaserCycleForTest();
            upperLeft.update(-1, null);
            upperRight.update(-1, null);
            assertEquals(leftStartX + cycle * 32, upperLeft.getX(),
                    "loc_6FF70 installs movement without calling MoveSprite2");
            for (int frame = 0; frame < 32; frame++) upperLeft.update(frame, null);
            for (int frame = 0; frame < 32; frame++) upperRight.update(frame, null);
        }
        assertEquals(leftStartX + 6 * 32, upperLeft.getX());
        assertEquals(startX, upperRight.getX());
        boss.completeLaserCycleForTest();
        for (int frame = 0; frame < 32; frame++) upperLeft.update(frame, null);
        for (int frame = 0; frame < 32; frame++) upperRight.update(frame, null);
        assertEquals(leftStartX + 6 * 32, upperLeft.getX(), "defeat does not publish bit 3");
        assertEquals(startX, upperRight.getX());
    }

    @Test void movingCornerInstallsThenExecutesExactlyThirtyTwoMovementPasses() {
        Fbz2SubbossInstance boss = boss();
        Fbz2SubbossCornerChild left = Fbz2SubbossCornerChild.forTest(boss, 0);
        int startX = left.getX();
        boss.completeLaserCycleForTest();

        left.update(0, null);
        assertEquals(startX, left.getX());
        assertTrue(boss.controlBit(Fbz2SubbossInstance.CONTROL_MOVE_RIGHT));
        for (int pass = 1; pass <= 31; pass++) {
            left.update(pass, null);
            assertEquals(startX + pass, left.getX());
            assertTrue(boss.controlBit(Fbz2SubbossInstance.CONTROL_MOVE_RIGHT));
        }
        left.update(32, null);
        assertEquals(startX + 32, left.getX());
        assertFalse(boss.controlBit(Fbz2SubbossInstance.CONTROL_MOVE_RIGHT));
    }

    @Test void ordinaryAttacksStartFlashButNeverConsumeALaserCycle() {
        Fbz2SubbossInstance boss = boss();
        PlayableEntity player = mock(PlayableEntity.class);
        boss.update(-1, player); // native setup callback is setup-only
        boss.onPlayerAttack(player);
        assertEquals(7, boss.cyclesRemaining());
        assertEquals(0, boss.getCollisionFlags());
        boss.update(0, player);
        assertEquals(31, boss.hitFlashUpdatesRemaining());
        assertEquals(0x7E, boss.getCollisionProperty());
        assertEquals(7, boss.cyclesRemaining());
    }

    @Test void machinePilotAndMaskRemainAtTheirInitializationCoordinatesWhenRootMoves() {
        Fbz2SubbossInstance root = boss();
        Fbz2SubbossMachineChild machine = new Fbz2SubbossMachineChild(root);
        Fbz2SubbossCharacterChild pilot = new Fbz2SubbossCharacterChild(root,
                com.openggf.game.PlayerCharacter.SONIC_ALONE);
        Fbz2SubbossSpriteMaskChild mask = new Fbz2SubbossSpriteMaskChild(root);
        int machineX = machine.getX(), machineY = machine.getY();
        int pilotX = pilot.getX(), pilotY = pilot.getY();
        int maskX = mask.getX(), maskY = mask.getY();

        root.offsetNativePositionWordsPreserveSubpixel(0x40, 0x20);
        machine.update(1, null);
        pilot.update(1, null);
        mask.update(1, null);

        assertEquals(machineX, machine.getX());
        assertEquals(machineY, machine.getY());
        assertEquals(pilotX, pilot.getX());
        assertEquals(pilotY, pilot.getY());
        assertEquals(maskX, mask.getX());
        assertEquals(maskY, mask.getY());
    }

    @Test void nativeInitialChildTableUsesStableRolesAndSubtypes() {
        assertArrayEquals(new int[] {0, 2, 4, 6}, Fbz2SubbossCornerChild.nativeSubtypes());
        assertArrayEquals(new int[] {0, 2}, Fbz2SubbossSolidSideChild.nativeSubtypes());
        assertEquals(0x49, Fbz2SubbossSpriteMaskChild.nativeSubtype());
        assertEquals(4, Fbz2SubbossSpriteMaskChild.mappingFrame());
        assertEquals(0x80, Fbz2SubbossSpriteMaskChild.nativePriority());
        assertEquals(0xAC, Fbz2SubbossLaserChild.activeCollisionFlags());
    }

    @Test void laserRawScriptsMatchEveryNativeCallbackAndCollisionPass() {
        Fbz2SubbossInstance root = boss();
        Fbz2SubbossLaserChild laser = new Fbz2SubbossLaserChild(root);

        laser.update(1, null);
        assertEquals(0xA, laser.frameForTest());
        for (int call = 2; call <= 206; call++) laser.update(call, null);
        assertFalse(root.controlBit(Fbz2SubbossInstance.CONTROL_LASER_READY));
        laser.update(207, null);
        assertTrue(root.controlBit(Fbz2SubbossInstance.CONTROL_LASER_READY),
                "zero-delay wrap $20 publishes root $38 bit 1");
        for (int call = 208; call <= 270; call++) laser.update(call, null);
        assertEquals("CHARGE", laser.phaseNameForTest());
        laser.update(271, null);
        assertEquals("BEAM", laser.phaseNameForTest());
        assertEquals(6, laser.frameForTest());
        assertEquals(root.getY() + 8 + 0x3C, laser.getY());

        int collisionPasses = 0;
        for (int beam = 1; beam <= 64; beam++) {
            laser.update(271 + beam, null);
            if (laser.getCollisionFlags() == 0xAC) collisionPasses++;
            assertEquals(beam >= 6 && (beam & 1) == 0 ? 0xAC : 0,
                    laser.getCollisionFlags(), "collision is sampled before the raw frame advances");
        }
        assertEquals(30, collisionPasses);
        assertEquals("RETRACT", laser.phaseNameForTest());
        laser.update(336, null);
        assertEquals(7, laser.frameForTest());
        assertEquals("WAIT_DELETE", laser.phaseNameForTest(),
                "the already-negative Obj_Wait counter truncates byte_70412 after one frame");
        for (int wait = 1; wait < 32; wait++) laser.update(336 + wait, null);
        assertFalse(laser.isDestroyed());
        laser.update(368, null);
        assertFalse(laser.isDestroyed(), "Go_Delete_Sprite schedules deletion for the following object pass");
        laser.update(369, null);
        assertTrue(laser.isDestroyed());
    }

    private static Fbz2SubbossInstance boss() {
        return new Fbz2SubbossInstance(new ObjectSpawn(0x2B40, 0x5F0, 0xAB, 0, 0, true, 417));
    }
}
