package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.objects.badniks.MantisBadnikInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMantisBadnikInstance {

    @Test
    void collisionResponseListReadsLiveMantisPosition() {
        MantisBadnikInstance mantis = new MantisBadnikInstance(
                new ObjectSpawn(0x0CA0, 0x05D0, 0x9D, 0, 0, false, 0));

        assertTrue(mantis.usesCurrentTouchResponseState(),
                "S3K retains the Mantis SST pointer, so TouchResponse reads its live x_pos/y_pos");
    }
}
