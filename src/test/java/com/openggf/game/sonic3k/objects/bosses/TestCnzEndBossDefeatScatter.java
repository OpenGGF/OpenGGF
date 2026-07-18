package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCnzEndBossDefeatScatter {

    @Test
    void bodyHalvesUseNativeOffsetsFramesAndConstantIndexedVelocities() {
        CnzEndBossInstance boss = boss();
        CnzEndBossDefeatDebrisChild left = new CnzEndBossDefeatDebrisChild(boss, -8, -0x300);
        CnzEndBossDefeatDebrisChild right = new CnzEndBossDefeatDebrisChild(boss, 8, 0x300);
        left.setServices(servicesWithCamera(0x46C0, 0x0200));
        right.setServices(servicesWithCamera(0x46C0, 0x0200));

        assertEquals(boss.getCentreX() - 0x14, left.getCentreX());
        assertEquals(boss.getCentreX() + 0x14, right.getCentreX());
        assertEquals(0x0B, left.frameForTest());
        assertEquals(0x0C, right.frameForTest());
        assertEquals(-0x100, left.xVelocityForTest());
        assertEquals(0x100, right.xVelocityForTest());
        assertEquals(-0x100, left.yVelocityForTest());
        assertEquals(-0x100, right.yVelocityForTest());

        int leftY = left.getCentreY();
        left.update(0, null);
        assertEquals(boss.getCentreX() - 0x15, left.getCentreX());
        assertEquals(leftY - 1, left.getCentreY());
        assertEquals(-0x100, left.yVelocityForTest(),
                "CNZ body halves retain their indexed velocity without gravity");
        assertTrue(left.visibleForTest(), "Obj_FlickerMove draws on the first toggled frame");
        left.update(1, null);
        assertFalse(left.visibleForTest(), "Obj_FlickerMove alternates rendering each frame");
    }

    @Test
    void flickerMoveUsesNativeCoarseBackAndCameraYDeletionBounds() {
        int cameraX = 0x4700;
        int cameraY = 0x0200;
        int coarseBack = (cameraX - 0x80) & 0xFF80;

        assertFalse(S3kBossFlickerMove.isOutsideNativeBounds(
                coarseBack + 0x280, cameraY + 0x180, cameraX, cameraY));
        assertTrue(S3kBossFlickerMove.isOutsideNativeBounds(
                coarseBack + 0x300, cameraY, cameraX, cameraY));
        assertTrue(S3kBossFlickerMove.isOutsideNativeBounds(
                coarseBack, cameraY + 0x181, cameraX, cameraY));
        assertTrue(S3kBossFlickerMove.isOutsideNativeBounds(
                coarseBack, cameraY - 0x81, cameraX, cameraY));
    }

    @Test
    void armsScatterInPlaceWithSubtypeIndexedVelocityAndNoCollision() {
        int[][] expectedVelocities = {
                {-0x100, -0x100},
                {0x100, -0x100},
                {-0x200, -0x200},
                {0x200, -0x200}
        };
        for (int subtype = 0; subtype < expectedVelocities.length; subtype++) {
            CnzEndBossArmChild arm = new CnzEndBossArmChild(boss(), subtype << 5);
            arm.setServices(servicesWithCamera(0x46C0, 0x0200));
            arm.update(0, null);
            int startX = arm.getCentreX();
            int startY = arm.getCentreY();

            arm.beginDefeatScatter();

            assertEquals(1, arm.frameForTest());
            assertEquals(0, arm.getCollisionFlags());
            assertEquals(expectedVelocities[subtype][0], arm.xVelocityForTest());
            assertEquals(expectedVelocities[subtype][1], arm.yVelocityForTest());
            arm.update(1, null);
            assertEquals(startX + (expectedVelocities[subtype][0] >> 8), arm.getCentreX());
            assertEquals(startY + (expectedVelocities[subtype][1] >> 8), arm.getCentreY());
            assertEquals(expectedVelocities[subtype][1] + 0x38, arm.yVelocityForTest(),
                    "Obj_FlickerMove applies gravity after the indexed velocity move");
            assertTrue(arm.visibleForTest());
        }
    }

    @Test
    void magnetScatterSparksUseFrameAOpposedVelocitiesAndSecondFlip() {
        CnzEndBossMagnetChild.DefeatSpark left =
                new CnzEndBossMagnetChild.DefeatSpark(0x4740 - 8, 0x0254, 0);
        CnzEndBossMagnetChild.DefeatSpark right =
                new CnzEndBossMagnetChild.DefeatSpark(0x4740 + 8, 0x0254, 1);
        left.setServices(servicesWithCamera(0x46C0, 0x0200));
        right.setServices(servicesWithCamera(0x46C0, 0x0200));

        assertEquals(0x0A, left.frameForTest());
        assertEquals(0x0A, right.frameForTest());
        assertEquals(-0x200, left.xVelocityForTest());
        assertEquals(0x200, right.xVelocityForTest());
        assertEquals(-0x200, left.yVelocityForTest());
        assertEquals(-0x200, right.yVelocityForTest());
        assertFalse(left.horizontalFlipForTest());
        assertTrue(right.horizontalFlipForTest());

        left.update(0, null);
        right.update(0, null);
        assertEquals(0x4740 - 10, left.getCentreX());
        assertEquals(0x4740 + 10, right.getCentreX());
        assertEquals(-0x200 + 0x38, left.yVelocityForTest());
        assertEquals(-0x200 + 0x38, right.yVelocityForTest());
    }

    private static CnzEndBossInstance boss() {
        return new CnzEndBossInstance(new ObjectSpawn(
                0x4740, 0x0240, 0xA7, 0, 0, false, 0));
    }

    private static StubObjectServices servicesWithCamera(int x, int y) {
        Camera camera = new Camera() {
            @Override public short getX() { return (short) x; }
            @Override public short getY() { return (short) y; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        return new StubObjectServices() {
            @Override public Camera camera() { return camera; }
        };
    }
}
