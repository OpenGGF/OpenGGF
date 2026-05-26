package com.openggf.game.sonic3k.objects;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestS3kBossExplosionController {

    @Test
    public void controllerSpawnsExplosionsEveryThreeFrames() {
        // subtype 2: timer=$28 (40), xRange=$80, yRange=$80
        // ROM: 3-frame initial wait, then 1 explosion every 3 frames
        // Timer decrements before spawn and deletes at zero.
        var controller = new S3kBossExplosionController(160, 112, 2);
        int spawnCount = 0;
        // Run for enough frames to exhaust the timer
        for (int frame = 0; frame < 200; frame++) {
            controller.tick();
            spawnCount += controller.drainPendingExplosions().size();
            if (controller.isFinished()) break;
        }
        assertEquals(39, spawnCount,
                "CreateBossExp02 should spawn for $27..$01 and delete at $00 "
                        + "(sonic3k.asm:176775-176792)");
    }

    @Test
    public void subtypeZeroDeletesAtZeroBeforeConsumingExtraRandom() {
        var controller = new S3kBossExplosionController(160, 112, 0);
        int spawnCount = 0;
        for (int frame = 0; frame < 200 && !controller.isFinished(); frame++) {
            controller.tick();
            spawnCount += controller.drainPendingExplosions().size();
        }
        assertEquals(31, spawnCount,
                "CreateBossExp00 timer $20 should consume 31 Random_Number draws, not a zero-timer draw "
                        + "(sonic3k.asm:176706-176723,176775-176792)");
    }

    @Test
    public void initialWaitBeforeFirstExplosion() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        controller.tick();
        assertEquals(0, controller.drainPendingExplosions().size());
        controller.tick();
        assertEquals(0, controller.drainPendingExplosions().size());
        controller.tick();
        assertEquals(1, controller.drainPendingExplosions().size());
    }

    @Test
    public void threeFrameSpacingBetweenExplosions() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        // First explosion after initial wait
        for (int i = 0; i < 3; i++) {
            controller.tick();
        }
        assertEquals(1, controller.drainPendingExplosions().size());
        // Next 2 frames: no explosion (wait)
        controller.tick();
        assertEquals(0, controller.drainPendingExplosions().size());
        controller.tick();
        assertEquals(0, controller.drainPendingExplosions().size());
        // Third frame after first: second explosion
        controller.tick();
        assertEquals(1, controller.drainPendingExplosions().size());
    }

    @Test
    public void tickAndDrainNeverReturnsMoreThanOnePendingExplosionPerFrame() {
        var controller = new S3kBossExplosionController(160, 112, 2);

        for (int frame = 0; frame < 200 && !controller.isFinished(); frame++) {
            controller.tick();
            assertTrue(controller.drainPendingExplosions().size() <= 1,
                    "A single frame should never produce more than one pending explosion");
        }
    }

    @Test
    public void explosionOffsetsAreWithinRange() {
        var controller = new S3kBossExplosionController(160, 112, 2);
        // Skip to first explosion (2 wait frames + 1 spawn frame)
        for (int i = 0; i < 3; i++) {
            controller.tick();
        }
        for (var explosion : controller.drainPendingExplosions()) {
            int dx = Math.abs(explosion.x() - 160);
            int dy = Math.abs(explosion.y() - 112);
            assertTrue(dx <= 0x80, "X offset within Â±0x80");
            assertTrue(dy <= 0x80, "Y offset within Â±0x80");
        }
    }
}

