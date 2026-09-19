package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestS3kDezTorpedoLauncherObjectInstance {
    @AfterEach
    void resetBounds() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    @Test
    void subtypeBecomesTheFourPassReloadWord() {
        S3kDezTorpedoLauncherObjectInstance launcher = launcher(0x18, 0);

        assertEquals(0x60, launcher.timerForTest());
        assertEquals(0, launcher.mappingFrameForTest());
        assertFalse(launcher.closingForTest());
        assertEquals(4, launcher.romObjectCodePointerHighWord());
    }

    @Test
    void projectileMovesFourPixelsLeftWhenLauncherIsNotXFlipped() {
        S3kDezTorpedoProjectileObjectInstance projectile = projectile(0);

        assertEquals(0x9B, projectile.getCollisionFlags());

        projectile.update(0, null);

        assertEquals(0xFC, projectile.getX());
        assertEquals(6, projectile.getPriorityBucket());
    }

    @Test
    void projectileMovesFourPixelsRightWhenLauncherIsXFlipped() {
        S3kDezTorpedoProjectileObjectInstance projectile = projectile(1);

        projectile.update(0, null);

        assertEquals(0x104, projectile.getX());
    }

    private static S3kDezTorpedoLauncherObjectInstance launcher(int subtype, int flags) {
        return new S3kDezTorpedoLauncherObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x4D, subtype, flags, false, 0));
    }

    private static S3kDezTorpedoProjectileObjectInstance projectile(int flags) {
        return new S3kDezTorpedoProjectileObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0, 0, flags, false, 0));
    }
}
