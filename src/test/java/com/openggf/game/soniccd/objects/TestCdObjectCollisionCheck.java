package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before the port; all cases UNRUN by the external author. Constructed terrain goes
 * through the production CdR11aTerrain/CdPlayerTerrain owners; no floor value is hard-coded here.
 * Source: Collision Check.asm:157-179 (ObjGetFloorDist) at 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 */
class TestCdObjectCollisionCheck {
    @Test
    void blankTerrainKeepsTheNearbyDefaultDistanceAndWritesNoAngle() {
        var f = new Fixture();
        var result = CdObjectCollisionCheck.objGetFloorDist(f.probes(), object(0x270, 0x735, 0x10, 0));
        // Probe at $745: blank block, blank block below: (15 - 5) + 16.
        assertEquals(26, result.distance());
        assertEquals(0, result.primaryAngle(), "MOVE.B #0,(a4) stands when FindLevelFloor writes nothing");
        assertEquals(0, result.angleResult());
    }

    @Test
    void solidFloorBelowGivesNegativeDistanceAndPublishesAnEvenAngle() {
        var f = new Fixture().floor(2, 0x22);
        var result = CdObjectCollisionCheck.objGetFloorDist(f.probes(), object(0x270, 0x778, 0x10, 0));
        // Probe at $788 is nine pixels below the floor top row $780: distance $77F - $788.
        assertEquals(-9, result.distance());
        assertEquals(0x22, result.primaryAngle());
        assertEquals(0x22, result.angleResult());
    }

    @Test
    void oddAngleStaysInTheGlobalButReturnsZeroInD3() {
        var f = new Fixture().floor(2, 0x23);
        var result = CdObjectCollisionCheck.objGetFloorDist(f.probes(), object(0x270, 0x778, 0x10, 0));
        assertEquals(-9, result.distance());
        assertEquals(0x23, result.primaryAngle());
        assertEquals(0, result.angleResult(), "BTST #0 / MOVE.B #0,d3");
    }

    @Test
    void yRadiusByteIsSignExtendedBeforeTheWordAdd() {
        var f = new Fixture().floor(2, 0x22);
        var probes = f.probes();
        // $F0 is -16: Y $798 probes $788, inside the floor.
        assertEquals(-9, CdObjectCollisionCheck.objGetFloorDist(probes, object(0x270, 0x798, 0xF0, 0)).distance());
        // An unsigned $F0 would probe $888; the positive radius $10 probes $7A8 below the floor instead.
        assertEquals(23, CdObjectCollisionCheck.objGetFloorDist(probes, object(0x270, 0x798, 0x10, 0)).distance());
    }

    @Test
    void probeCoordinatesWrapAsNativeWords() {
        var f = new Fixture().floor(2, 0x22);
        var result = CdObjectCollisionCheck.objGetFloorDist(f.probes(), object(0x270, 0xFFF8, 0x10, 0));
        // $FFF8 + $10 wraps to $0008: layout row 0 is blank, so (15 - 8) + 16.
        assertEquals(23, result.distance());
        assertEquals(0, result.primaryAngle());
    }

    @Test
    void theObjectsOwnPathLayerBitSelectsTheFlaggedChunk() {
        var f = new Fixture().flaggedCell().floor(3, 0x22);
        var probes = f.probes();
        var upper = CdObjectCollisionCheck.objGetFloorDist(probes, object(0x270, 0x778, 0x10, 0x40));
        assertEquals(-9, upper.distance(), "bit 6 swaps the flagged chunk 2 to chunk 3, which has the floor");
        assertEquals(0x22, upper.primaryAngle());
        var lower = CdObjectCollisionCheck.objGetFloorDist(probes, object(0x270, 0x778, 0x10, 0x80));
        assertEquals(23, lower.distance(), "bit 7 is not a path layer; chunk 2 stays blank");
        assertEquals(0, lower.primaryAngle());
    }

    @Test
    void nullInputsAndNonNativeWidthsAreRejected() {
        var f = new Fixture();
        var state = object(0x270, 0x735, 0x10, 0);
        assertThrows(NullPointerException.class, () -> CdObjectCollisionCheck.objGetFloorDist(null, state));
        assertThrows(NullPointerException.class, () -> CdObjectCollisionCheck.objGetFloorDist(f.probes(), null));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectCollisionCheck.FloorDistance(0x8000, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectCollisionCheck.FloorDistance(0, 0x100, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectCollisionCheck.FloorDistance(0, 0, -1));
        var kept = new CdObjectCollisionCheck.FloorDistance(-0x8000, 0xFF, 0xFE);
        assertEquals(-0x8000, kept.distance());
    }

    private static CdObjectState object(int x, int y, int yRadius, int spriteFlags) {
        return new CdObjectPool().state(32).moveWord(X, x).moveWord(Y, y)
                .moveByte(Y_RADIUS, yRadius).moveByte(SPRITE_FLAGS, spriteFlags).moveByte(ID, 0x3D);
    }

    /** World X $200..$2FF, Y $700..$7FF is layout cell (2,7); X $270 is block column 7 of that chunk. */
    private static final class Fixture {
        final byte[] chunks = new byte[61952];
        final byte[] fg = new byte[338];
        final byte[] bg = new byte[50];
        final byte[] ids = new byte[900];
        final byte[] angles = new byte[256];
        final byte[] heights = new byte[4096];
        final byte[] widths = new byte[4096];

        Fixture() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            fg[2 + 7 * 42 + 2] = 2;
        }
        Fixture flaggedCell() { fg[2 + 7 * 42 + 2] = (byte) 0x82; return this; }
        /** Full-height solid block at column 7, row 8 of the given chunk: its top is world Y $780. */
        Fixture floor(int chunk, int angle) {
            int at = (chunk - 1) * 512 + (8 * 16 + 7) * 2;
            chunks[at] = 0x20; chunks[at + 1] = 0x01;
            ids[1] = 7; angles[7] = (byte) angle;
            Arrays.fill(heights, 7 * 16, 7 * 16 + 16, (byte) 16);
            return this;
        }
        CdPlayerTerrain probes() {
            return new CdPlayerTerrain(CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg,
                    ids, angles, heights, widths));
        }
    }
}
