package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kDezTiltingBridgeObjectInstance {
    @Test
    void exposesTheEightRomSlabsAndFullSolidDimensions() {
        S3kDezTiltingBridgeObjectInstance bridge = bridge();

        assertEquals(8, bridge.getPieceCount());
        assertEquals(7, bridge.getReservedChildSlotCount());
        for (int piece = 0; piece < 8; piece++) {
            assertEquals(0x090 + piece * 0x20, bridge.getPieceX(piece));
            assertEquals(0x180, bridge.getPieceY(piece));
        }
        assertFalse(bridge.isTopSolidOnly());
        assertEquals(0x1B, bridge.getSolidParams().halfWidth());
        assertEquals(0x10, bridge.getSolidParams().airHalfHeight());
        assertEquals(0x11, bridge.getSolidParams().groundHalfHeight());
        assertTrue(bridge.usesPieceScopedStandingBits());
        assertTrue(bridge.resolvesEarlierPiecesBeforeRidingPiece());
        assertEquals(4, bridge.romObjectCodePointerHighWord());
    }

    @Test
    void exactForceTableMirrorsTheStandingSlabAndAddsBothPlayers() {
        S3kDezTiltingBridgeObjectInstance bridge = bridge();

        assertEquals(0xE0, bridge.forceForTest(1, 0));
        assertEquals(-0xE0, bridge.forceForTest(1, 7));
        assertEquals(-0xE0, bridge.forceForTest(8, 0));
        assertEquals(0xE0, bridge.forceForTest(8, 7));

        bridge.setStandingPieceForTest(1, 8);
        bridge.update(0, null);
        assertEquals(0, bridge.velocityForTest(0));
        assertEquals(0, bridge.velocityForTest(7));
    }

    @Test
    void collapseMultipliesVelocityOnceThenAddsGravity() {
        S3kDezTiltingBridgeObjectInstance bridge = bridge();
        bridge.setStandingPieceForTest(1, 0);
        int frame = 0;
        while (!bridge.fallingForTest() && frame < 1000) bridge.update(frame++, null);
        assertTrue(bridge.fallingForTest(), "the loaded bridge must reach the $70 collapse threshold");

        int beforeCollapse = bridge.velocityForTest(0);
        bridge.update(frame++, null);
        assertEquals(beforeCollapse * 4, bridge.velocityForTest(0));
        int afterMultiply = bridge.velocityForTest(0);
        bridge.update(frame, null);
        assertEquals(afterMultiply + 0x1000, bridge.velocityForTest(0));
    }

    private static S3kDezTiltingBridgeObjectInstance bridge() {
        return new S3kDezTiltingBridgeObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x4B, 0, 0, false, 0));
    }
}
