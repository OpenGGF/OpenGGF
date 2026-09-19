package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kDezStaircaseObjectInstance {
    @Test
    void exposesFourOrderedFullSolidPieces() {
        S3kDezStaircaseObjectInstance stairs = stairs(0, 0);
        assertEquals(4, stairs.getPieceCount());
        assertEquals(3, stairs.getReservedChildSlotCount());
        for (int i = 0; i < 4; i++) {
            assertEquals(0x100 + i * 0x20, stairs.getPieceX(i));
            assertEquals(0x180, stairs.getPieceY(i));
        }
        assertFalse(stairs.isTopSolidOnly());
        assertEquals(0x1B, stairs.getSolidParams().halfWidth());
        assertEquals(0x10, stairs.getSolidParams().airHalfHeight());
        assertEquals(0x11, stairs.getSolidParams().groundHalfHeight());
        assertTrue(stairs.usesPieceScopedStandingBits());
        assertEquals(4, stairs.romObjectCodePointerHighWord());
    }

    @Test
    void p2ContactWaitsThirtyPassesThenRaisesOnTheRomRamp() {
        S3kDezStaircaseObjectInstance stairs = stairs(0, 0);
        stairs.triggerForTest(true);
        stairs.update(0, null);
        for (int frame = 1; frame < 30; frame++) stairs.update(frame, null);
        assertEquals(0, stairs.routineForTest());
        stairs.update(30, null);
        assertEquals(1, stairs.routineForTest());
        stairs.update(31, null);
        assertArrayEquals(new int[]{1, 0, 0, 0}, offsets(stairs));
        for (int frame = 32; frame <= 158; frame++) stairs.update(frame, null);
        assertArrayEquals(new int[]{0x80, 0x60, 0x40, 0x20}, offsets(stairs));
    }

    @Test
    void subtypeFourLowersAndPlacementFlipReversesThePieceOffsets() {
        S3kDezStaircaseObjectInstance stairs = stairs(4, 1);
        stairs.triggerForTest(true);
        for (int frame = 0; frame <= 30; frame++) stairs.update(frame, null);
        stairs.update(31, null);
        assertArrayEquals(new int[]{-1, -1, -1, -1}, offsets(stairs));
        assertEquals(0x17F, stairs.getPieceY(0));
        assertEquals(0x17F, stairs.getPieceY(3));
        for (int frame = 32; frame <= 158; frame++) stairs.update(frame, null);
        assertArrayEquals(new int[]{-0x80, -0x60, -0x40, -0x20}, offsets(stairs));
        assertEquals(0x160, stairs.getPieceY(0));
        assertEquals(0x100, stairs.getPieceY(3));
    }

    private static int[] offsets(S3kDezStaircaseObjectInstance stairs) {
        return new int[]{stairs.offsetForTest(0), stairs.offsetForTest(1),
                stairs.offsetForTest(2), stairs.offsetForTest(3)};
    }

    private static S3kDezStaircaseObjectInstance stairs(int subtype, int renderFlags) {
        return new S3kDezStaircaseObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x4F, subtype, renderFlags, false, 0));
    }
}
