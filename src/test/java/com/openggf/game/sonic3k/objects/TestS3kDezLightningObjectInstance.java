package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kDezLightningObjectInstance {
    @Test
    void animationHoldsEachVisibleMappingForTwoPassesAndOnlyFrameThreeHurts() {
        S3kDezLightningObjectInstance lightning = lightning(2);
        List<Integer> frames = new ArrayList<>();
        List<Integer> collision = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            lightning.update(i, null);
            frames.add(lightning.mappingFrameForTest());
            collision.add(lightning.getCollisionFlags());
        }

        assertEquals(List.of(1, 1, 2, 2, 3, 3, 4, 4, 0), frames);
        assertEquals(List.of(0, 0, 0, 0, 0x9F, 0x9F, 0, 0, 0), collision);
        assertFalse(lightning.animatingForTest());
        assertEquals(1, lightning.waitCounterForTest(),
                "the animation-ending pass also performs the first ROM predecrement");
    }

    @Test
    void subtypeWaitRestartsOnlyAfterTheSignedCounterUnderflows() {
        S3kDezLightningObjectInstance lightning = lightning(2);
        for (int i = 0; i < 9; i++) {
            lightning.update(i, null);
        }
        lightning.update(9, null);
        assertEquals(0, lightning.mappingFrameForTest());
        lightning.update(10, null);
        assertTrue(lightning.animatingForTest());
        assertEquals(1, lightning.mappingFrameForTest());
    }

    @Test
    void exposesTheOwningRomRoutinePage() {
        assertEquals(4, lightning(0).romObjectCodePointerHighWord());
    }

    private static S3kDezLightningObjectInstance lightning(int subtype) {
        return new S3kDezLightningObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x52, subtype, 0, false, 0, 0));
    }
}
